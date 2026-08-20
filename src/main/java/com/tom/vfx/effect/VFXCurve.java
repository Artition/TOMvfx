package com.tom.vfx.effect;

import net.minecraft.resources.Identifier;

/**
 * A custom easing curve loaded from the datapack ({@code data/<namespace>/vfx_curves/<name>.json}).
 * The curve is a piecewise-linear function through control points and is referenced from effect
 * definitions by its id ({@code "easing": "<namespace>:<name>"} or a bare name in the same
 * namespace as the effect, falling back to the global namespace).
 *
 * @param id       the curve's datapack id
 * @param function the ready-to-use easing function
 */
public record VFXCurve(Identifier id, EasingFunction function) {
}