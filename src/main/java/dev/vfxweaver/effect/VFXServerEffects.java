package dev.vfxweaver.effect;

import dev.vfxweaver.network.VFXTriggerPayload;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side memory of effects sent to each player, so an effect is re-applied when the player
 * reconnects (or joins) while it is still running. {@code VFXAPI.sendEffect} records every play;
 * on player join the still-active ones are re-sent with their remaining duration.
 *
 * <p>Keyed per {@code player -> effectId}, keeping the latest play of each effect (a repeat
 * replaces the previous entry — matching how {@code /vfx stop} stops every instance of an id).
 * Persistent (negative duration) effects are always re-applied; finite ones only while their
 * duration has not elapsed. The map is bounded per player (see {@link #MAX_EFFECTS_PER_PLAYER}).
 *
 * <p>Everything is disabled during Flashback replay playback: the replay already carries the
 * effects (as packets or custom actions) and re-injecting them from the live registry would
 * double them. The guard is reflective so this mod stays free of a Flashback dependency.
 */
public final class VFXServerEffects {
	private static final Logger LOGGER = LoggerFactory.getLogger("vfxweaver/server-effects");
	/** Safety cap on tracked effects per player (external input, see AGENTS.md). */
	private static final int MAX_EFFECTS_PER_PLAYER = 32;
	private static final VFXServerEffects INSTANCE = new VFXServerEffects();

	/**
	 * Reflective handle to {@code Flashback.isInReplay()}, resolved once on first use so the
	 * per-call {@code record}/{@code stop}/{@code applyTo} path skips the costly lookup.
	 */
	private static final Method FLASHBACK_IS_IN_REPLAY = resolveIsInReplay();

	private final Map<UUID, Map<Identifier, ActiveEffect>> byPlayer = new HashMap<>();

	private VFXServerEffects() {
	}

	public static VFXServerEffects get() {
		return INSTANCE;
	}

	private static @Nullable Method resolveIsInReplay() {
		if (!FabricLoader.getInstance().isModLoaded("flashback")) {
			return null;
		}
		try {
			Class<?> flashback = Class.forName("com.moulberry.flashback.Flashback");
			return flashback.getMethod("isInReplay");
		} catch (Throwable t) {
			return null;
		}
	}

	/**
	 * True while a Flashback replay is being played back (the effects are already being replayed
	 * by Flashback itself). {@code false} when Flashback is not installed.
	 */
	private static boolean flashbackIsReplaying() {
		Method isInReplay = FLASHBACK_IS_IN_REPLAY;
		if (isInReplay == null) {
			return false;
		}
		try {
			return (Boolean) isInReplay.invoke(null);
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * A recorded effect play: everything needed to re-send it later, plus the server tick it
	 * started on so the remaining duration can be computed.
	 */
	private record ActiveEffect(
		Identifier effectId,
		int durationTicks,
		long instanceId,
		@Nullable Vec3 worldPos,
		List<UUID> entityUuids,
		Map<String, Float> params,
		String easing,
		long startTick
	) {
	}

	/**
	 * Records an effect play sent to the player. Replaces any previous entry of the same effect id.
	 */
	public void record(
		final ServerPlayer player,
		final Identifier effectId,
		final int durationTicks,
		final long instanceId,
		final @Nullable Vec3 worldPos,
		final List<UUID> entityUuids,
		final Map<String, Float> params,
		final String easing
	) {
		if (flashbackIsReplaying()) {
			return;
		}
		Map<Identifier, ActiveEffect> effects = this.byPlayer.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>());
		if (!effects.containsKey(effectId) && effects.size() >= MAX_EFFECTS_PER_PLAYER) {
			// Oldest entries get evicted so a misbehaving caller cannot pin unbounded memory.
			Iterator<ActiveEffect> it = effects.values().iterator();
			if (it.hasNext()) {
				it.next();
				it.remove();
			}
		}
		effects.put(effectId, new ActiveEffect(effectId, durationTicks, instanceId, worldPos, List.copyOf(entityUuids), Map.copyOf(params), easing, player.level().getServer().getTickCount()));
	}

	/**
	 * Drops every recorded instance of the effect for the player (mirrors {@code sendStop}).
	 */
	public void stop(final ServerPlayer player, final Identifier effectId) {
		if (flashbackIsReplaying()) {
			return;
		}
		Map<Identifier, ActiveEffect> effects = this.byPlayer.get(player.getUUID());
		if (effects != null) {
			effects.remove(effectId);
		}
	}

	/**
	 * Drops the recorded instance with the given id for the player (mirrors the instance-targeted
	 * {@code sendStop}).
	 */
	public void stop(final ServerPlayer player, final Identifier effectId, final long instanceId) {
		if (flashbackIsReplaying()) {
			return;
		}
		Map<Identifier, ActiveEffect> effects = this.byPlayer.get(player.getUUID());
		if (effects == null) {
			return;
		}
		ActiveEffect active = effects.get(effectId);
		if (active != null && active.instanceId() == instanceId) {
			effects.remove(effectId);
		}
	}

	/**
	 * Re-sends the still-active effects to a (re)joining player, each with its remaining duration.
	 * Called after the datapack definitions have been synced so the client can resolve the ids.
	 * Expired entries are pruned on the way.
	 */
	public void applyTo(final ServerPlayer player) {
		if (flashbackIsReplaying()) {
			return;
		}
		Map<Identifier, ActiveEffect> effects = this.byPlayer.get(player.getUUID());
		if (effects == null || effects.isEmpty()) {
			return;
		}
		long now = player.level().getServer().getTickCount();
		Iterator<Map.Entry<Identifier, ActiveEffect>> it = effects.entrySet().iterator();
		while (it.hasNext()) {
			ActiveEffect active = it.next().getValue();
			int remaining = remainingTicks(active, now);
			if (remaining < 0) {
				it.remove();
				continue;
			}
			ServerPlayNetworking.send(player, VFXTriggerPayload.play(
				active.effectId(), remaining, active.instanceId(), active.worldPos(), active.entityUuids(), active.params(), active.easing()
			));
		}
		if (effects.isEmpty()) {
			this.byPlayer.remove(player.getUUID());
		}
	}

	/**
	 * The number of ticks the effect still has left, or {@code -1} when it has expired. Persistent
	 * effects (negative duration) never expire.
	 */
	private static int remainingTicks(final ActiveEffect active, final long now) {
		if (active.durationTicks() < 0) {
			return active.durationTicks();
		}
		long elapsed = Math.max(0L, now - active.startTick());
		long remaining = active.durationTicks() - elapsed;
		if (remaining <= 0L) {
			return -1;
		}
		return (int) remaining;
	}
}