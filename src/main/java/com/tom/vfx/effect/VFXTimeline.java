package com.tom.vfx.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A collection of named {@link AnimatedValue}s that together drive a single visual effect.
 * The timeline has a total duration in ticks; all values are advanced together with
 * {@link #update(float)} and queried by name with {@link #getValue(String, float)}.
 *
 * <p>Live overrides (added at runtime, e.g. via {@code /vfx set}) are stored separately and
 * win over both world bindings and the definition values.
 */
public class VFXTimeline {
	/** Safety cap for runtime overrides (network/command input, see AGENTS.md). */
	private static final int MAX_OVERRIDES = 32;

	private final float duration;
	private final Map<String, AnimatedValue> values;
	private Map<String, BoundParam> bindings;
	private final Map<String, AnimatedValue> overrides = new LinkedHashMap<>();
	private float elapsed;

	/**
	 * Creates a timeline with the given duration and value set.
	 *
	 * @param duration total duration in ticks
	 * @param values   named animated values
	 */
	public VFXTimeline(final float duration, final Map<String, AnimatedValue> values) {
		this(duration, values, Map.of());
	}

	/**
	 * Creates a timeline with time-animated values and world bindings.
	 *
	 * @param duration total duration in ticks
	 * @param values   named animated values
	 * @param bindings named world bindings (evaluated per frame against the camera)
	 */
	public VFXTimeline(final float duration, final Map<String, AnimatedValue> values, final Map<String, BoundParam> bindings) {
		this.duration = duration;
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
		this.elapsed = 0.0F;
	}

	/**
	 * Advances the whole timeline to the given elapsed time (in ticks) since the effect started.
	 *
	 * @param now elapsed time in ticks
	 */
	public void update(final float now) {
		this.elapsed = Math.max(0.0F, now);
		for (AnimatedValue value : this.values.values()) {
			value.update(this.elapsed);
		}
		for (AnimatedValue value : this.overrides.values()) {
			value.update(this.elapsed);
		}
	}

	/**
	 * Reads the current value of the given parameter. Runtime overrides win over world-bound
	 * parameters, which in turn are evaluated against the camera state fed by the client.
	 *
	 * @param name     parameter name
	 * @param fallback value returned when the parameter is not present
	 */
	public float getValue(final String name, final float fallback) {
		AnimatedValue override = this.overrides.get(name);
		if (override != null) {
			return override.get();
		}
		BoundParam binding = this.bindings.get(name);
		if (binding != null) {
			return VFXWorldBindings.evaluate(binding, fallback);
		}
		AnimatedValue value = this.values.get(name);
		return value == null ? fallback : value.get();
	}

	/**
	 * True when the elapsed time reached or exceeded the timeline duration.
	 */
	public boolean isFinished() {
		return this.elapsed >= this.duration;
	}

	/**
	 * Normalized progress in {@code [0, 1]}.
	 */
	public float getProgress() {
		return this.duration <= 0.0F ? 1.0F : Math.min(1.0F, this.elapsed / this.duration);
	}

	public float getDuration() {
		return this.duration;
	}

	public float getElapsed() {
		return this.elapsed;
	}

	public Map<String, AnimatedValue> getValues() {
		return this.values;
	}

	public Map<String, BoundParam> getBindings() {
		return this.bindings;
	}

	/**
	 * Re-anchors every spatial binding ({@code screen_x}/{@code screen_y}/{@code proximity})
	 * to the given world position — used when an explicit position override arrives (e.g.
	 * {@code /vfx playat}), letting world-bound effects be re-targeted per play.
	 *
	 * @param x world X
	 * @param y world Y
	 * @param z world Z
	 */
	public void rebindPositions(final double x, final double y, final double z) {
		final Map<String, BoundParam> updated = new LinkedHashMap<>();
		for (final Map.Entry<String, BoundParam> entry : this.bindings.entrySet()) {
			final BoundParam binding = entry.getValue();
			if (binding.kind().needsPos()) {
				updated.put(entry.getKey(), new BoundParam(binding.kind(), x, y, z, binding.yaw(), binding.pitch(), binding.range(), binding.invert(), binding.scale()));
			} else {
				updated.put(entry.getKey(), binding);
			}
		}
		this.bindings = Collections.unmodifiableMap(updated);
	}

	/**
	 * Live-overrides the parameter with a constant, winning over any binding or animation
	 * from the definition (used by {@code /vfx set}).
	 *
	 * @param name  parameter name
	 * @param value the new constant value
	 */
	public void setOverride(final String name, final float value) {
		this.putOverride(name, AnimatedValue.constant(value));
	}

	/**
	 * Adds or replaces a keyframe of the parameter on a live copy of its animation (used by
	 * {@code /vfx key}). When the parameter has no animation yet, a step animation starting
	 * at the keyframe is created.
	 *
	 * @param name    parameter name
	 * @param time    keyframe time in ticks from the effect start
	 * @param value   keyframe value
	 * @param easing  easing curve towards the next keyframe
	 */
	public void setKeyframe(final String name, final float time, final float value, final EasingType easing) {
		AnimatedValue base = this.overrides.get(name);
		if (base == null) {
			base = this.values.get(name);
		}
		List<Keyframe> frames = base != null ? new ArrayList<>(base.getKeyframes()) : new ArrayList<>();
		frames.removeIf(frame -> Float.compare(frame.time(), time) == 0);
		frames.add(new Keyframe(time, value, easing));
		if (frames.size() < 2) {
			// Single keyframe: hold the value before it (step function).
			frames.add(new Keyframe(Float.MAX_VALUE, value));
		}
		this.putOverride(name, AnimatedValue.fromKeyframes(frames.toArray(new Keyframe[0])));
	}

	private void putOverride(final String name, final AnimatedValue value) {
		if (this.overrides.size() >= MAX_OVERRIDES && !this.overrides.containsKey(name)) {
			Iterator<String> it = this.overrides.keySet().iterator();
			if (it.hasNext()) {
				it.next();
				it.remove();
			}
		}
		this.overrides.put(name, value);
		value.update(this.elapsed);
	}
}
