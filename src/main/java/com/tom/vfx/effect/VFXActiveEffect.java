package com.tom.vfx.effect;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * A single running instance of a {@link VFXDefinition}. Holds a {@link VFXTimeline} that is
 * advanced every frame with the current elapsed tick time; the driving clock is maintained
 * by the client (tick counter + partial tick) but the class itself is side-agnostic.
 */
public class VFXActiveEffect {
	private final Identifier id;
	private final VFXEffectType type;
	private final float startTime;
	private final VFXTimeline timeline;
	private final int fadeTicks;
	private final boolean loop;
	private final List<BlockPos> positions;
	private final List<String> entityTargets;
	private float elapsed;
	private float age;
	private float fadeOutStart = Float.NEGATIVE_INFINITY;

	/**
	 * Creates a new effect instance starting at the given time.
	 *
	 * @param id        effect id
	 * @param type      effect kind
	 * @param startTime start time in ticks on the driving clock
	 * @param timeline  pre-built animation timeline
	 */
	public VFXActiveEffect(final Identifier id, final VFXEffectType type, final float startTime, final VFXTimeline timeline) {
		this(id, type, startTime, timeline, 0, false, List.of());
	}

	/**
	 * Creates a new effect instance with an optional fade duration and looping.
	 *
	 * @param fadeTicks ticks the effect takes to fade in on start and out when stopped
	 * @param loop      true when the timeline restarts once it reaches its duration
	 */
	public VFXActiveEffect(final Identifier id, final VFXEffectType type, final float startTime, final VFXTimeline timeline, final int fadeTicks, final boolean loop) {
		this(id, type, startTime, timeline, fadeTicks, loop, List.of());
	}

	/**
	 * Creates a new effect instance with fade, looping and world positions.
	 */
	public VFXActiveEffect(final Identifier id, final VFXEffectType type, final float startTime, final VFXTimeline timeline, final int fadeTicks, final boolean loop, final List<BlockPos> positions) {
		this(id, type, startTime, timeline, fadeTicks, loop, positions, List.of());
	}

	/**
	 * Creates a new effect instance with fade, looping, world positions and entity targets
	 * (UUID strings or player names, for the {@code entity_tint}/{@code entity_outline} overlays).
	 */
	public VFXActiveEffect(final Identifier id, final VFXEffectType type, final float startTime, final VFXTimeline timeline, final int fadeTicks, final boolean loop, final List<BlockPos> positions, final List<String> entityTargets) {
		this.id = id;
		this.type = type;
		this.startTime = startTime;
		this.timeline = timeline;
		this.fadeTicks = fadeTicks;
		this.loop = loop;
		this.positions = List.copyOf(positions);
		this.entityTargets = List.copyOf(entityTargets);
		this.elapsed = 0.0F;
		this.age = 0.0F;
	}

	/**
	 * Advances the effect to the given clock time (in ticks). When looping, the timeline time
	 * wraps around the timeline duration; {@link #getAge()} keeps the unwrapped time for fades.
	 *
	 * @param now current clock time in ticks
	 */
	public void update(final float now) {
		float raw = Math.max(0.0F, now - this.startTime);
		this.age = raw;
		if (this.loop) {
			this.elapsed = raw % Math.max(this.timeline.getDuration(), 1.0F);
		} else {
			this.elapsed = raw;
		}
		this.timeline.update(this.elapsed);
	}

	/**
	 * Marks this instance as fading out (called when it is stopped).
	 */
	public void beginFadeOut(final float now) {
		if (!this.isFadingOut()) {
			this.fadeOutStart = now;
		}
	}

	/**
	 * True after {@link #beginFadeOut(float)} was called.
	 */
	public boolean isFadingOut() {
		return this.fadeOutStart != Float.NEGATIVE_INFINITY;
	}

	/**
	 * Current fade weight in {@code [0, 1]}: ramps up over {@code fadeTicks} after the start and
	 * ramps back down once the instance is fading out.
	 */
	public float getWeight() {
		float weight = 1.0F;
		if (this.fadeTicks > 0) {
			weight = Math.min(1.0F, this.age / this.fadeTicks);
		}
		if (this.isFadingOut()) {
			if (this.fadeTicks <= 0) {
				return 0.0F;
			}
			float since = this.age - (this.fadeOutStart - this.startTime);
			weight = Math.min(weight, Math.max(0.0F, 1.0F - since / this.fadeTicks));
		}
		return weight;
	}

	/**
	 * True when the effect reached the end of its timeline, finished fading out, or never ends
	 * because it loops (looping instances only end once stopped).
	 */
	public boolean isFinished() {
		if (this.isFadingOut()) {
			return this.getWeight() <= 0.0F;
		}
		if (this.loop) {
			return false;
		}
		return this.timeline.isFinished();
	}

	/**
	 * Reads the current value of a parameter, falling back to {@code fallback} when absent.
	 */
	public float getParam(final String name, final float fallback) {
		return this.timeline.getValue(name, fallback);
	}

	/**
	 * Normalized progress in {@code [0, 1]}.
	 */
	public float getProgress() {
		return this.timeline.getProgress();
	}

	public Identifier getId() {
		return this.id;
	}

	public VFXEffectType getType() {
		return this.type;
	}

	public float getStartTime() {
		return this.startTime;
	}

	public float getElapsed() {
		return this.elapsed;
	}

	/**
	 * Unwrapped time since the effect started (does not wrap when looping).
	 */
	public float getAge() {
		return this.age;
	}

	public float getDuration() {
		return this.timeline.getDuration();
	}

	public VFXTimeline getTimeline() {
		return this.timeline;
	}

	public int getFadeTicks() {
		return this.fadeTicks;
	}

	/**
	 * World-space positions this effect applies to (for block highlighting effects).
	 */
	public List<BlockPos> getPositions() {
		return this.positions;
	}

	/**
	 * Entity targets of this effect (UUID strings or player names), resolved per frame
	 * by the world overlay renderer.
	 */
	public List<String> getEntityTargets() {
		return this.entityTargets;
	}
}
