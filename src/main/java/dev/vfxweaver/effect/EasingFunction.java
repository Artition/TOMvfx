package dev.vfxweaver.effect;

import java.util.Locale;

/**
 * An interpolation curve: maps a normalized progress value in {@code [0, 1]} to an eased value.
 * Covers both the built-in {@link EasingType} curves and custom curves loaded from the datapack
 * ({@code data/<namespace>/vfx_curves/<name>.json} or inline {@code { "curve": [[t,v],...] }} objects).
 *
 * <p>A curve carries its canonical name so it can travel over the network and be re-resolved on
 * the client (built-in name or {@code namespace:path} curve id).
 */
public final class EasingFunction {
	private static final float EPSILON = 1.0e-6F;

	private final String name;
	private final CurveFunction function;
	private final float[] ts;
	private final float[] vs;

	@FunctionalInterface
	private interface CurveFunction {
		float apply(float t);
	}

	private EasingFunction(final String name, final CurveFunction function, final float[] ts, final float[] vs) {
		this.name = name;
		this.function = function;
		this.ts = ts;
		this.vs = vs;
	}

	/**
	 * Wraps a built-in easing type as a function.
	 */
	public static EasingFunction builtIn(final EasingType type) {
		return new EasingFunction(type.name(), type::apply, null, null);
	}

	/**
	 * Creates a custom piecewise-linear curve from control points. The points must be ordered by
	 * ascending {@code t} with the first {@code t == 0} and the last {@code t == 1}.
	 *
	 * @param name the curve's canonical name (its datapack id or "inline")
	 * @param ts   control point times in ascending order
	 * @param vs   control point values
	 * @throws IllegalArgumentException when fewer than two points are given or the times are invalid
	 */
	public static EasingFunction curve(final String name, final float[] ts, final float[] vs) {
		if (ts.length < 2 || ts.length != vs.length) {
			throw new IllegalArgumentException("A curve needs at least two [t, v] control points");
		}
		return new EasingFunction(name, t -> evaluateCurve(ts, vs, t), ts, vs);
	}

	/**
	 * Resolves an easing name to a function: a built-in {@link EasingType} first, then a custom
	 * curve from the datapack registry. Unknown names fall back to {@link EasingType#LINEAR}.
	 *
	 * @param name raw name, e.g. {@code "ease_out_cubic"}, {@code "my_curve"} or {@code "ns:my_curve"}
	 */
	public static EasingFunction fromString(final String name) {
		if (name == null || name.isBlank()) {
			return builtIn(EasingType.LINEAR);
		}
		String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		for (EasingType type : EasingType.values()) {
			if (type.name().equals(normalized)) {
				return builtIn(type);
			}
		}
		VFXCurve curve = VFXCurveManager.get().get(name.trim());
		return curve != null ? curve.function() : builtIn(EasingType.LINEAR);
	}

	/**
	 * The canonical name of this curve (built-in enum name or datapack id) used for network
	 * serialization and logging.
	 */
	public String name() {
		return this.name;
	}

	/**
	 * Applies the curve to the given progress value, clamped to {@code [0, 1]}.
	 */
	public float apply(final float progress) {
		if (progress <= EPSILON) {
			return this.ts == null ? 0.0F : this.vs[0];
		}
		if (progress >= 1.0F - EPSILON) {
			return this.ts == null ? 1.0F : this.vs[this.vs.length - 1];
		}
		return this.function.apply(progress);
	}

	private static float evaluateCurve(final float[] ts, final float[] vs, final float t) {
		for (int i = 0; i < ts.length - 1; i++) {
			if (t >= ts[i] && t <= ts[i + 1]) {
				float span = ts[i + 1] - ts[i];
				float local = span <= 0.0F ? 1.0F : (t - ts[i]) / span;
				return vs[i] + (vs[i + 1] - vs[i]) * local;
			}
		}
		return vs[vs.length - 1];
	}

	@Override
	public String toString() {
		return "EasingFunction(" + this.name + ")";
	}
}