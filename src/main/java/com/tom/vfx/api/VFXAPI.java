package com.tom.vfx.api;

import com.tom.vfx.effect.EasingType;
import com.tom.vfx.effect.VFXDefinition;
import com.tom.vfx.network.VFXTriggerPayload;
import com.tom.vfx.resource.VFXDefinitionManager;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Public API of the mod. Server-side code (other mods, datapack functions, commands) triggers
 * effects for players with {@link #sendEffect(ServerPlayer, Identifier, Map, EasingType)};
 * client-side code plays effects directly with {@link #playEffect(Identifier, int, Map, EasingType)}.
 */
public final class VFXAPI {
	private static final Logger LOGGER = LoggerFactory.getLogger("tompfx/api");

	private static @Nullable VFXLocalDispatcher localDispatcher;

	private VFXAPI() {
	}

	/**
	 * Called by the client entrypoint to register local playback.
	 */
	public static void setLocalDispatcher(final VFXLocalDispatcher dispatcher) {
		localDispatcher = dispatcher;
	}

	/**
	 * Plays an effect locally (client-side only). Returns {@code false} when running without a
	 * client (e.g. on a dedicated server), where the networked variant must be used instead.
	 *
	 * @param effectId      effect id (built-in or datapack-defined)
	 * @param durationTicks duration in ticks
	 * @param params        parameter overrides (empty for defaults)
	 * @param easing        easing curve (may be null for the definition default)
	 */
	public static boolean playEffect(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final @Nullable EasingType easing) {
		if (localDispatcher == null) {
			LOGGER.warn("playEffect({}) called without a client; use sendEffect() instead", effectId);
			return false;
		}
		localDispatcher.playEffect(effectId, durationTicks, params, easing);
		return true;
	}

	/**
	 * Plays an effect locally with linear easing.
	 */
	public static boolean playEffect(final Identifier effectId, final int durationTicks, final Map<String, Float> params) {
		return playEffect(effectId, durationTicks, params, EasingType.LINEAR);
	}

	/**
	 * Stops all running instances of an effect locally (client-side only).
	 */
	public static boolean stopEffect(final Identifier effectId) {
		if (localDispatcher == null) {
			return false;
		}
		localDispatcher.stopEffect(effectId);
		return true;
	}

	/**
	 * Stops all running effects locally (client-side only).
	 */
	public static boolean stopAllEffects() {
		if (localDispatcher == null) {
			return false;
		}
		localDispatcher.stopAllEffects();
		return true;
	}

	/**
	 * Triggers an effect for a player over the network. Resolves the definition to merge its
	 * default constant parameters with the given overrides and to pick the default duration and
	 * easing when those are not supplied.
	 *
	 * @param player    the receiving player
	 * @param effectId  effect id (must be registered)
	 * @param overrides parameter overrides (may be empty)
	 * @param easing    easing curve (may be null to use the definition default)
	 * @return {@code true} when the effect was known and sent
	 */
	public static boolean sendEffect(final ServerPlayer player, final Identifier effectId, final Map<String, Float> overrides, final @Nullable EasingType easing) {
		VFXDefinition definition = VFXDefinitionManager.get().get(effectId);
		if (definition == null) {
			LOGGER.warn("sendEffect({}) failed: unknown effect", effectId);
			return false;
		}
		Map<String, Float> params = new HashMap<>();
		for (Map.Entry<String, VFXDefinition.ParamSpec> entry : definition.getParams().entrySet()) {
			VFXDefinition.ParamSpec spec = entry.getValue();
			if (!spec.animated() && spec.keyframes().isEmpty() && spec.bound() == null) {
				params.put(entry.getKey(), spec.constant());
			}
		}
		params.putAll(overrides);
		int duration = definition.isPersistent() ? -1 : definition.getDefaultDuration();
		EasingType effectiveEasing = easing != null ? easing : definition.getDefaultEasing();
		ServerPlayNetworking.send(player, VFXTriggerPayload.play(effectId, duration, params, effectiveEasing));
		return true;
	}

	/**
	 * Triggers an effect for a player with the given explicit duration and easing, without
	 * consulting the definition registry.
	 */
	public static void sendEffect(
		final ServerPlayer player,
		final Identifier effectId,
		final int durationTicks,
		final Map<String, Float> params,
		final EasingType easing
	) {
		ServerPlayNetworking.send(player, VFXTriggerPayload.play(effectId, durationTicks, params, easing));
	}

	/**
	 * Tells a player's client to stop all running instances of an effect.
	 */
	public static void sendStop(final ServerPlayer player, final Identifier effectId) {
		ServerPlayNetworking.send(player, VFXTriggerPayload.stop(effectId));
	}
}
