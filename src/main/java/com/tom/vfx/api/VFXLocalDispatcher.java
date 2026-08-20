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
	 * Plays an effect immediately on this client and returns the id of the created instance
	 * (0 when the effect was ignored, e.g. because it is unknown).
	 *
	 * @param effectId      effect id (built-in or datapack-defined)
	 * @param durationTicks duration in ticks
	 * @param params        parameter overrides (empty for defaults)
	 * @param easing        easing curve (may be null for the definition default)
	 * @return the instance id, or {@code 0} when the effect was not started
	 */
	long playEffect(Identifier effectId, int durationTicks, Map<String, Float> params, EasingType easing);

	/**
	 * Stops all running instances of the given effect.
	 */
	void stopEffect(Identifier effectId);

	/**
	 * Stops one specific instance of an effect.
	 *
	 * @param instanceId the instance id returned from {@link #playEffect(Identifier, int, Map, EasingType)}
	 */
	void stopEffect(long instanceId);

	/**
	 * Stops all running effects.
	 */
	void stopAllEffects();
}