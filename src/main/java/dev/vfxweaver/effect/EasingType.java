package dev.vfxweaver.effect;

import java.util.Locale;
import java.util.function.DoubleUnaryOperator;

/**
 * Interpolation curve types used by {@link AnimatedValue} and {@link VFXTimeline}.
 * The input is a normalized progress value in {@code [0, 1]} and the output is the eased value.
 */
public enum EasingType {
	LINEAR(t -> t),
	EASE_IN_QUAD(t -> t * t),
	EASE_OUT_QUAD(t -> t * (2.0 - t)),
	EASE_IN_OUT_QUAD(t -> t < 0.5 ? 2.0 * t * t : -1.0 + (4.0 - 2.0 * t) * t),
	EASE_IN_CUBIC(t -> t * t * t),
	EASE_OUT_CUBIC(t -> 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)),
	EASE_IN_OUT_CUBIC(t -> t < 0.5 ? 4.0 * t * t * t : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0),
	EASE_IN_EXPO(t -> t == 0.0 ? 0.0 : Math.pow(2.0, 10.0 * t - 10.0)),
	EASE_OUT_EXPO(t -> t == 1.0 ? 1.0 : 1.0 - Math.pow(2.0, -10.0 * t)),
	SMOOTHSTEP(t -> t * t * (3.0 - 2.0 * t));

	private final DoubleUnaryOperator function;

	EasingType(final DoubleUnaryOperator function) {
		this.function = function;
	}

	/**
	 * Applies the easing curve to the given progress value, clamped to {@code [0, 1]}.
	 *
	 * @param progress raw progress in {@code [0, 1]}
	 * @return eased progress in {@code [0, 1]}
	 */
	public float apply(final float progress) {
		if (progress <= 0.0F) {
			return 0.0F;
		}
		if (progress >= 1.0F) {
			return 1.0F;
		}
		return (float) this.function.applyAsDouble(progress);
	}

	/**
	 * Resolves an easing type from its name (case-insensitive, ignoring separators).
	 *
	 * @param name raw name, e.g. {@code "EASE_IN_OUT_CUBIC"}, {@code "ease_in_out_cubic"} or {@code "smoothstep"}
	 * @return the matching easing type, or {@link #LINEAR} if unknown
	 */
	public static EasingType fromString(final String name) {
		if (name == null || name.isBlank()) {
			return LINEAR;
		}
		String normalized = name.trim().toUpperCase(Locale.ROOT).replace('-', '_');
		try {
			return valueOf(normalized);
		} catch (IllegalArgumentException e) {
			return LINEAR;
		}
	}
}
