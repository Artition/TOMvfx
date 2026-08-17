package com.tom.vfx.client.effect;

import com.tom.vfx.effect.AnimatedValue;
import com.tom.vfx.effect.EasingType;
import com.tom.vfx.effect.VFXActiveEffect;
import com.tom.vfx.effect.VFXDefinition;
import com.tom.vfx.effect.VFXEffectType;
import com.tom.vfx.effect.VFXTimeline;
import com.tom.vfx.resource.VFXDefinitionManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side store of running {@link VFXActiveEffect}s together with the shared effect clock
 * (in ticks, advanced each rendered frame). Post-processing effects are consumed by the
 * {@code VFXPostProcessingManager}; {@code camera_shake} effects are consumed by the
 * {@code CameraShakeManager}.
 */
public class VFXEffectManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("tompfx/effects");
	private static final VFXEffectManager INSTANCE = new VFXEffectManager();
	private static final int MAX_COLLECTION_DEPTH = 4;
	private static final int MAX_ACTIVE_EFFECTS = 64;
	private static final int MAX_SCHEDULED_EFFECTS = 128;

	private final List<VFXActiveEffect> active = new ArrayList<>();
	private final List<ScheduledPlay> scheduled = new ArrayList<>();
	private float clock;

	private VFXEffectManager() {
	}

	public static VFXEffectManager get() {
		return INSTANCE;
	}

	/**
	 * Advances the shared effect clock by the given number of ticks.
	 */
	public void advance(final float deltaTicks) {
		this.clock += Math.max(0.0F, deltaTicks);
	}

	/**
	 * Removes finished effects, triggers due scheduled collection children and advances the
	 * remaining effects to the current clock time.
	 */
	public void update() {
		this.active.removeIf(VFXActiveEffect::isFinished);
		if (!this.scheduled.isEmpty()) {
			List<ScheduledPlay> due = new ArrayList<>();
			this.scheduled.removeIf(play -> {
				if (play.at() <= this.clock) {
					due.add(play);
					return true;
				}
				return false;
			});
			for (ScheduledPlay play : due) {
				this.play(play.effectId(), play.durationTicks(), play.params(), play.easing(), List.of(), play.depth());
			}
		}
		for (VFXActiveEffect effect : this.active) {
			effect.update(this.clock);
		}
	}

	/**
	 * Starts an effect. When the effect id is unknown, the effect is ignored with a warning.
	 *
	 * @param effectId      effect id (built-in or datapack-defined)
	 * @param durationTicks duration in ticks (0 uses the definition default, negative = persistent)
	 * @param params        parameter overrides
	 * @param easing        easing curve (may be null for the definition default)
	 */
	public void play(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) {
		this.play(effectId, durationTicks, params, easing, List.of(), 0);
	}

	/**
	 * Starts an effect with explicit entity targets (UUID strings or player names) that
	 * override the definition's {@code targets} list.
	 */
	public void play(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing, final List<String> entityTargets) {
		this.play(effectId, durationTicks, params, easing, entityTargets, 0);
	}

	private void play(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing, final List<String> entityTargetsIn, final int depth) {
		VFXDefinition definition = VFXDefinitionManager.get().get(effectId);
		VFXEffectType type = definition != null ? definition.getType() : VFXEffectType.fromString(effectId.getPath());
		if (type == null) {
			LOGGER.warn("Ignoring unknown VFX effect '{}'", effectId);
			return;
		}
		if (type == VFXEffectType.COLLECTION) {
			if (definition == null || depth >= MAX_COLLECTION_DEPTH) {
				LOGGER.warn("Ignoring collection '{}' (unknown or nested too deeply)", effectId);
				return;
			}
		int scheduledCount = 0;
		for (VFXDefinition.ChildEffect child : definition.getChildren()) {
			if (this.scheduled.size() >= MAX_SCHEDULED_EFFECTS) {
				LOGGER.warn("Scheduled VFX effect limit ({}) reached; dropping remaining collection children", MAX_SCHEDULED_EFFECTS);
				break;
			}
			this.scheduled.add(new ScheduledPlay(this.clock + child.delay(), child.effect(), child.duration(), child.params(), child.easing(), depth + 1));			scheduledCount++;
		}
		LOGGER.info("Scheduled {} child effect(s) from collection '{}'", scheduledCount, effectId);
			if (definition.getSound() != null) {
				playSound(definition.getSound());
			}
			return;
		}

		boolean loop = definition != null && definition.isLoop();
		boolean persistent = definition != null && (definition.isPersistent() || loop || durationTicks < 0);
		int duration = persistent ? (loop ? definition.getDefaultDuration() : Integer.MAX_VALUE) : (durationTicks > 0 ? durationTicks : (definition != null ? definition.getDefaultDuration() : 40));
		EasingType effectiveEasing = easing != null ? easing : (definition != null ? definition.getDefaultEasing() : EasingType.LINEAR);
		VFXTimeline timeline = definition != null
			? definition.createTimeline(duration, params, effectiveEasing)
			: createConstantTimeline(duration, params);

		int fadeTicks = definition != null ? definition.getFadeTicks() : 0;
		List<BlockPos> positions = definition != null ? definition.getPositions() : List.of();
		BlockPos payloadPos = payloadPosition(params);
		if (payloadPos != null) {
			// Explicit position overrides (e.g. /vfx playat) win over definition positions
			// and re-anchor any spatial world bindings to that position.
			positions = List.of(payloadPos);
			timeline.rebindPositions(payloadPos.getX(), payloadPos.getY(), payloadPos.getZ());
		}
		List<String> entityTargets = !entityTargetsIn.isEmpty() ? entityTargetsIn : (definition != null ? definition.getEntityTargets() : List.of());
		VFXActiveEffect effect = new VFXActiveEffect(effectId, type, this.clock, timeline, fadeTicks, loop, positions, entityTargets);
		// Same-id replays stack as independent instances (e.g. several dents at once);
		// /vfx stop removes every instance of the id. MAX_ACTIVE_EFFECTS caps the total.
		while (this.active.size() >= MAX_ACTIVE_EFFECTS) {
			LOGGER.warn("Active VFX effect limit ({}) reached; removing oldest effect '{}'", MAX_ACTIVE_EFFECTS, this.active.get(0).getId());
			this.active.remove(0);
		}
		this.active.add(effect);
		if (definition != null && definition.getSound() != null) {
			playSound(definition.getSound());
		}
		if (LOGGER.isInfoEnabled()) {
			StringBuilder snapshot = new StringBuilder();
			for (String name : timeline.getValues().keySet()) {
				if (!snapshot.isEmpty()) {
					snapshot.append(", ");
				}
				snapshot.append(name).append('=').append(timeline.getValue(name, Float.NaN));
			}
			for (String name : timeline.getBindings().keySet()) {
				if (!snapshot.isEmpty()) {
					snapshot.append(", ");
				}
				snapshot.append(name).append("=bind(").append(timeline.getBindings().get(name).kind()).append(')');
			}
			LOGGER.info("Started VFX effect '{}' for {} ticks: {}", effectId, persistent ? "forever" : duration, snapshot);
		}
	}

	/**
	 * Stops all running instances of the given effect. Persistent instances fade out over their
	 * definition's fade duration instead of disappearing instantly.
	 */
	public void stop(final Identifier effectId) {
		this.scheduled.removeIf(play -> play.effectId().equals(effectId));
		this.active.removeIf(effect -> {
			if (!effect.getId().equals(effectId)) {
				return false;
			}
			if (effect.getFadeTicks() > 0 && !effect.isFadingOut()) {
				effect.beginFadeOut(this.clock);
				return false;
			}
			return true;
		});
	}

	/**
	 * Live-overrides a parameter of every running instance of the effect, without restarting
	 * its timeline (used by {@code /vfx set}). When no instance is running, a persistent
	 * instance is started with the override baked in, so the command works standalone.
	 *
	 * @return {@code true} when the effect is known and was applied or started
	 */
	public boolean setParam(final Identifier effectId, final String name, final float value) {
		boolean applied = false;
		for (VFXActiveEffect effect : this.active) {
			if (effect.getId().equals(effectId)) {
				effect.getTimeline().setOverride(name, value);
				applied = true;
			}
		}
		if (!applied) {
			if (VFXDefinitionManager.get().get(effectId) == null) {
				return false;
			}
			this.play(effectId, -1, Map.of(name, value), null);
		}
		return true;
	}

	/**
	 * Adds or replaces a keyframe of a parameter on every running instance of the effect
	 * (used by {@code /vfx key}).
	 *
	 * @return {@code true} when at least one running instance was found and updated
	 */
	public boolean setKeyframe(final Identifier effectId, final String name, final float time, final float value, final EasingType easing) {
		boolean applied = false;
		for (VFXActiveEffect effect : this.active) {
			if (effect.getId().equals(effectId)) {
				effect.getTimeline().setKeyframe(name, time, value, easing);
				applied = true;
			}
		}
		return applied;
	}

	/**
	 * Live-retargets every running {@code entity_tint}/{@code entity_outline} instance of the
	 * effect to another entity (UUID string or player name).
	 *
	 * @return {@code true} when at least one running instance was found and retargeted
	 */
	public boolean setEntityTargets(final Identifier effectId, final String target) {
		boolean applied = false;
		for (VFXActiveEffect effect : this.active) {
			if (effect.getId().equals(effectId)) {
				effect.setEntityTarget(target);
				applied = true;
			}
		}
		return applied;
	}

	/**
	 * Stops all running effects (persistent ones fade out).
	 */
	public void stopAll() {
		this.scheduled.clear();
		this.active.removeIf(effect -> {
			if (effect.getFadeTicks() > 0 && !effect.isFadingOut()) {
				effect.beginFadeOut(this.clock);
				return false;
			}
			return true;
		});
	}

	public List<VFXActiveEffect> getActive() {
		return List.copyOf(this.active);
	}

	public List<VFXActiveEffect> getActivePostEffects() {
		return this.active.stream().filter(e -> e.getType().isPostProcessing()).toList();
	}

	public List<VFXActiveEffect> getActiveWorldEffects() {
		return this.active.stream().filter(e -> e.getType().isWorldOverlay()).toList();
	}

	public List<VFXActiveEffect> getActiveShakes() {
		return this.active.stream().filter(e -> e.getType() == VFXEffectType.CAMERA_SHAKE).toList();
	}

	public float getActiveFovDelta() {
		float delta = 0.0F;
		for (VFXActiveEffect effect : this.active) {
			if (effect.getType() == VFXEffectType.FOV_MODIFIER) {
				delta += effect.getParam("fov_delta", 0.0F) * effect.getWeight();
			}
		}
		return delta;
	}

	public float getClock() {
		return this.clock;
	}
	/** Reads a {@code pos_x/pos_y/pos_z} override triple into a block position, or null. */
	private static BlockPos payloadPosition(final Map<String, Float> params) {
		Float x = params.get("pos_x");
		Float y = params.get("pos_y");
		Float z = params.get("pos_z");
		if (x == null || y == null || z == null) {
			return null;
		}
		return new BlockPos(x.intValue(), y.intValue(), z.intValue());
	}

	private static void playSound(final Identifier soundId) {
		try {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level != null) {
				// Effects are sent to specific players over the network; play locally at full
				// volume for the receiving client instead of at world coordinates.
				SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundId);
				minecraft.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1.0F, 1.0F));
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to play VFX sound '{}'", soundId, e);
		}
	}

	private static VFXTimeline createConstantTimeline(final float duration, final Map<String, Float> params) {
		Map<String, AnimatedValue> values = new LinkedHashMap<>();
		for (Map.Entry<String, Float> entry : params.entrySet()) {
			values.put(entry.getKey(), AnimatedValue.constant(entry.getValue()));
		}
		return new VFXTimeline(duration, values);
	}

	/**
	 * A child effect waiting for its delay to elapse.
	 */
	private record ScheduledPlay(float at, Identifier effectId, int durationTicks, Map<String, Float> params, EasingType easing, int depth) {
	}
}
