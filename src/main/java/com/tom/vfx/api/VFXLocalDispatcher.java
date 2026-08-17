package com.tom.vfx.api;

import com.tom.vfx.effect.EasingType;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Bridge implemented by the client to play effects locally (no server round-trip).
 * The client registers an instance through {@link VFXAPI#setLocalDispatcher(VFXLocalDispatcher)}.
 */
public interface VFXLocalDispatcher {
	/**
	 * Plays an effect immediately on this client.
	 *
	 * @param effectId      effect id (built-in or datapack-defined)
	 * @param durationTicks duration in ticks
	 * @param params        parameter overrides (empty for defaults)
	 * @param easing        easing curve (may be null for the definition default)
	 */
	void playEffect(Identifier effectId, int durationTicks, Map<String, Float> params, EasingType easing);

	/**
	 * Stops all running instances of the given effect.
	 */
	void stopEffect(Identifier effectId);

	/**
	 * Stops all running effects.
	 */
	void stopAllEffects();
}
