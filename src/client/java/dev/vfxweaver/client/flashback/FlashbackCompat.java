package dev.vfxweaver.client.flashback;

import dev.vfxweaver.client.effect.VFXEffectManager;
import dev.vfxweaver.effect.EasingType;
import dev.vfxweaver.effect.VFXActiveEffect;
import dev.vfxweaver.effect.VFXEffectType;
import dev.vfxweaver.effect.VFXTimeline;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft-dependency bridge to Flashback (https://modrinth.com/mod/flashback): locally played VFX
 * effects are written into the replay stream as a custom {@code Action} and re-triggered during
 * playback. The mod works fully without Flashback — nothing here runs when it is absent, and all
 * Flashback classes are reached through reflection so the mod has no compile-time dependency on
 * it (only {@code suggests: flashback} in {@code fabric.mod.json}).
 *
 * <p>Recording: {@link #recordPlay} queues a {@code Recorder.submitCustomTask} that writes the
 * effect id, duration, easing and resolved params into the current replay. Playback: the
 * registered action's {@code handle} decodes that payload and re-triggers the effect through
 * {@link VFXEffectManager} on the render thread (the handler runs on the replay server thread).
 *
 * <p>Only client-local plays ({@code playEffect} on the client) are recorded. Effects triggered
 * from the server travel as {@code vfxweaver:vfx_trigger} packets which Flashback captures and
 * replays on its own, so recording them here too would double them.
 */
public final class FlashbackCompat {
	private static final Logger LOGGER = LoggerFactory.getLogger("vfxweaver/flashback");
	private static final Identifier ACTION_NAME = Identifier.fromNamespaceAndPath("vfxweaver", "effect_trigger");
	/** Safety cap on the number of params decoded from a replay file. */
	private static final int MAX_PARAMS = 32;

	private static boolean enabled;
	private static @Nullable Class<?> actionClass;
	private static @Nullable Class<?> registryClass;
	private static @Nullable Class<?> recorderClass;
	private static @Nullable Class<?> replayWriterClass;
	private static @Nullable Class<?> flashbackClass;
	private static @Nullable Object action;
	// Reflective handles resolved once during init to avoid per-call getMethod/getField lookups.
	private static @Nullable Field recorderField;
	private static @Nullable Method readyToWriteMethod;
	private static @Nullable Method submitCustomTaskMethod;
	private static @Nullable Method startActionMethod;
	private static @Nullable Method finishActionMethod;
	private static @Nullable Method friendlyByteBufMethod;
	/** The last {@code Flashback.RECORDER} instance seen, to detect a new recording start. */
	private static @Nullable Object lastRecorder;
	/** True once the snapshot of already-active effects has been written for the current recording. */
	private static boolean snapshotWritten;

	private FlashbackCompat() {
	}

	/**
	 * Looks up the Flashback classes and registers the replay action. Safe to call multiple times;
	 * a no-op when Flashback is not installed. Must run after Flashback itself is on the classpath.
	 */
	public static void init() {
		if (enabled || !FabricLoader.getInstance().isModLoaded("flashback")) {
			return;
		}
		try {
			actionClass = Class.forName("com.moulberry.flashback.action.Action");
			registryClass = Class.forName("com.moulberry.flashback.action.ActionRegistry");
			recorderClass = Class.forName("com.moulberry.flashback.record.Recorder");
			replayWriterClass = Class.forName("com.moulberry.flashback.io.ReplayWriter");
			flashbackClass = Class.forName("com.moulberry.flashback.Flashback");
			action = Proxy.newProxyInstance(actionClass.getClassLoader(), new Class<?>[]{actionClass}, new ActionHandler());
			Method register = registryClass.getMethod("register", actionClass);
			register.invoke(null, action);
			recorderField = flashbackClass.getField("RECORDER");
			readyToWriteMethod = recorderClass.getMethod("readyToWrite");
			submitCustomTaskMethod = recorderClass.getMethod("submitCustomTask", Consumer.class);
			startActionMethod = replayWriterClass.getMethod("startAction", actionClass);
			finishActionMethod = replayWriterClass.getMethod("finishAction", actionClass);
			friendlyByteBufMethod = replayWriterClass.getMethod("friendlyByteBuf");
			ClientTickEvents.END_CLIENT_TICK.register(tick -> detectRecordingStart());
			enabled = true;
			LOGGER.info("Flashback compatibility enabled: client-local VFX effects are recorded into replays");
		} catch (Throwable t) {
			enabled = false;
			LOGGER.warn("Failed to initialize Flashback compatibility; effects won't be recorded into replays", t);
		}
	}

	/**
	 * Watches {@code Flashback.RECORDER} each tick. When a recording just started and became ready
	 * (its initial world snapshot has been written), snapshots every effect that is already running
	 * so it appears in the replay from the first tick instead of being lost.
	 */
	private static void detectRecordingStart() {
		if (!enabled) {
			return;
		}
		try {
			Object recorder = recorderField.get(null);
			if (recorder == null) {
				lastRecorder = null;
				snapshotWritten = false;
				return;
			}
			if (recorder != lastRecorder) {
				lastRecorder = recorder;
				snapshotWritten = false;
			}
			if (snapshotWritten) {
				return;
			}
			if ((Boolean) readyToWriteMethod.invoke(recorder)) {
				snapshotWritten = true;
				writeActiveEffectsSnapshot(recorder);
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to detect Flashback recording start", t);
		}
	}

	/**
	 * Writes one replay action per already-running effect into the given recording, using each
	 * effect's current parameter values so it replays in the same state. Persistent and looping
	 * effects are skipped — with no recorded stop event they would loop forever during playback.
	 */
	private static void writeActiveEffectsSnapshot(final Object recorder) {
		try {
			for (VFXActiveEffect effect : VFXEffectManager.get().getActive()) {
				Identifier id = effect.getId();
				VFXTimeline timeline = effect.getTimeline();
				// Without a recorded stop event a looping/persistent effect would loop forever
				// during playback, so only finite-duration effects are snapshotted.
				if (effect.getType() == VFXEffectType.COLLECTION || effect.getType() == VFXEffectType.CAMERA_SHAKE
					|| effect.isLooping() || timeline.getDuration() >= Integer.MAX_VALUE - 1) {
					continue;
				}
				int duration = Math.max(1, (int) Math.ceil(timeline.getDuration() - timeline.getElapsed()));
				Map<String, Float> params = snapshotParams(timeline);
				submitCustomTaskMethod.invoke(recorder, (Consumer<Object>) writer -> {
					try {
						writeAction(writer, id, duration, params, EasingType.LINEAR);
					} catch (Throwable t) {
						LOGGER.warn("Failed to write snapshot of running VFX effect '{}' into Flashback replay", id, t);
					}
				});
			}
		} catch (Throwable t) {
			LOGGER.warn("Failed to snapshot running VFX effects into Flashback replay", t);
		}
	}

	/**
	 * Collects the current value of every timeline parameter (values, bindings, multipliers,
	 * expressions and live overrides) into a constant map, preserving the effect's on-screen state.
	 */
	private static Map<String, Float> snapshotParams(final VFXTimeline timeline) {
		Map<String, Float> params = new LinkedHashMap<>();
		Map<String, Float> deferred = new LinkedHashMap<>();
		timeline.getValues().keySet().forEach(name -> deferred.put(name, timeline.getValue(name, Float.NaN)));
		timeline.getBindings().keySet().forEach(name -> deferred.put(name, timeline.getValue(name, Float.NaN)));
		timeline.getMultipliers().keySet().forEach(name -> deferred.put(name, timeline.getValue(name, Float.NaN)));
		timeline.getExpressions().keySet().forEach(name -> deferred.put(name, timeline.getValue(name, Float.NaN)));
		timeline.getOverrideNames().forEach(name -> deferred.put(name, timeline.getValue(name, Float.NaN)));
		for (Map.Entry<String, Float> entry : deferred.entrySet()) {
			if (!Float.isNaN(entry.getValue())) {
				params.put(entry.getKey(), entry.getValue());
			}
		}
		return params;
	}

	/**
	 * Records a client-local effect play into the active Flashback replay, if one is running.
	 * Persistent (negative duration) effects are skipped: without a recorded stop event they would
	 * loop forever during playback. The payload is written on the render thread, mirroring the
	 * {@code effectId, durationTicks, easing, params} order of the network trigger.
	 */
	public static void recordPlay(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) {
		if (!enabled || durationTicks < 0) {
			return;
		}
		try {
			Minecraft.getInstance().execute(() -> {
				try {
					Object recorder = recorderField.get(null);
					if (recorder == null) {
						return;
					}
					if (!((Boolean) readyToWriteMethod.invoke(recorder))) {
						return;
					}
					submitCustomTaskMethod.invoke(recorder, (Consumer<Object>) writer -> {
						try {
							writeAction(writer, effectId, durationTicks, params, easing);
						} catch (Throwable t) {
							LOGGER.warn("Failed to write VFX effect '{}' into Flashback replay", effectId, t);
						}
					});
				} catch (Throwable t) {
					LOGGER.warn("Failed to record VFX effect '{}' into Flashback replay", effectId, t);
				}
			});
		} catch (Throwable t) {
			LOGGER.warn("Failed to queue VFX effect '{}' for Flashback replay recording", effectId, t);
		}
	}

	/**
	 * Writes one replay action via the {@code ReplayWriter} handed to us by Flashback's recorder.
	 */
	private static void writeAction(final Object writer, final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) throws Exception {
		boolean started = false;
		try {
			startActionMethod.invoke(writer, action);
			started = true;
			RegistryFriendlyByteBuf buf = (RegistryFriendlyByteBuf) friendlyByteBufMethod.invoke(writer);
			buf.writeIdentifier(effectId);
			buf.writeVarInt(durationTicks);
			buf.writeUtf(easing.name());
			buf.writeVarInt(params.size());
			for (Map.Entry<String, Float> entry : params.entrySet()) {
				buf.writeUtf(entry.getKey());
				buf.writeFloat(entry.getValue());
			}
		} finally {
			if (started) {
				finishActionMethod.invoke(writer, action);
			}
		}
	}

	/**
	 * Decodes a recorded play action and re-triggers the effect on the render thread. Called by
	 * Flashback on the replay server thread, hence the {@code execute} hop.
	 */
	private static void handlePlayback(final RegistryFriendlyByteBuf buf) {
		Identifier effectId = buf.readIdentifier();
		int durationTicks = buf.readVarInt();
		String easingName = buf.readUtf();
		int paramCount = buf.readVarInt();
		if (paramCount < 0 || paramCount > MAX_PARAMS) {
			// Corrupt or foreign payload: refuse to allocate an unbounded map.
			throw new IllegalStateException("Invalid VFX action param count: " + paramCount);
		}
		Map<String, Float> params = new HashMap<>(paramCount);
		for (int i = 0; i < paramCount; i++) {
			params.put(buf.readUtf(), buf.readFloat());
		}
		Minecraft.getInstance().execute(() ->
			VFXEffectManager.get().play(effectId, durationTicks, params, EasingType.fromString(easingName))
		);
	}

	/**
	 * {@link InvocationHandler} for the {@code com.moulberry.flashback.action.Action} proxy:
	 * dispatches {@code name()} and {@code handle(ReplayServer, RegistryFriendlyByteBuf)}.
	 */
	private static final class ActionHandler implements InvocationHandler {
		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
			String name = method.getName();
			if (method.getDeclaringClass() == Object.class) {
				return switch (name) {
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					case "toString" -> "vfxweaver Flashback action " + ACTION_NAME;
					default -> throw new UnsupportedOperationException("Unsupported Object method: " + method);
				};
			}
			if ("name".equals(name)) {
				return ACTION_NAME;
			}
			if ("handle".equals(name)) {
				handlePlayback((RegistryFriendlyByteBuf) args[1]);
				return null;
			}
			throw new UnsupportedOperationException("Unsupported Action method: " + method);
		}
	}
}