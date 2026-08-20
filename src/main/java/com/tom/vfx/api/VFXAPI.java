package com.tom.vfx.api;

import com.tom.vfx.effect.EasingFunction;
import com.tom.vfx.effect.EasingType;
import com.tom.vfx.effect.VFXDefinition;
import com.tom.vfx.network.VFXTriggerPayload;
import com.tom.vfx.resource.VFXDefinitionManager;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
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
	 * Plays an effect locally and returns the id of the created instance, so a specific one of
	 * several concurrent instances can later be stopped with {@link #stopEffect(long)}.
	 * Returns {@code 0} when running without a client or when the effect was ignored.
	 *
	 * @param effectId      effect id (built-in or datapack-defined)
	 * @param durationTicks duration in ticks
	 * @param params        parameter overrides (empty for defaults)
	 * @param easing        easing curve (may be null for the definition default)
	 * @return the instance id, or {@code 0} on failure
	 */
	public static long playEffectId(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final @Nullable EasingType easing) {
		if (localDispatcher == null) {
			LOGGER.warn("playEffectId({}) called without a client; use sendEffect() instead", effectId);
			return 0L;
		}
		return localDispatcher.playEffect(effectId, durationTicks, params, easing);
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
	 * Stops one specific instance of an effect locally (client-side only).
	 *
	 * @param instanceId the instance id returned from {@link #playEffectId(Identifier, int, Map, EasingType)}
	 * @return {@code false} when running without a client or when no such instance exists
	 */
	public static boolean stopEffect(final long instanceId) {
		if (localDispatcher == null) {
			return false;
		}
		localDispatcher.stopEffect(instanceId);
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
		return sendEffect(player, effectId, 0L, null, overrides, easing);
	}

	/**
	 * Triggers an effect for a player over the network with an explicit world position. The
	 * client re-anchors spatial world bindings ({@code screen_x/y}, {@code proximity}) to that
	 * point and uses it for the effect's world positions — no {@code pos_x/y/z} override hacks.
	 *
	 * @param player    the receiving player
	 * @param effectId  effect id (must be registered)
	 * @param worldPos  world position to anchor the effect to
	 * @param overrides parameter overrides (may be empty)
	 * @param easing    easing curve (may be null to use the definition default)
	 * @return {@code true} when the effect was known and sent
	 */
	public static boolean sendEffect(final ServerPlayer player, final Identifier effectId, final Vec3 worldPos, final Map<String, Float> overrides, final @Nullable EasingType easing) {
		return sendEffect(player, effectId, 0L, worldPos, overrides, easing);
	}

	/**
	 * Triggers an effect for a player over the network with an explicit instance id and world
	 * position. The instance id lets a later {@link #sendStop(ServerPlayer, Identifier, long)}
	 * target this exact instance instead of every instance of the effect. When {@code instanceId}
	 * is {@code 0}, the client allocates one on play.
	 *
	 * @param player     the receiving player
	 * @param effectId   effect id (must be registered)
	 * @param instanceId instance id to assign (0 = client allocates)
	 * @param worldPos   world position to anchor the effect to (may be null)
	 * @param overrides  parameter overrides (may be empty)
	 * @param easing     easing curve (may be null to use the definition default)
	 * @return {@code true} when the effect was known and sent
	 */
	public static boolean sendEffect(
		final ServerPlayer player,
		final Identifier effectId,
		final long instanceId,
		final @Nullable Vec3 worldPos,
		final Map<String, Float> overrides,
		final @Nullable EasingType easing
	) {
		VFXDefinition definition = VFXDefinitionManager.get().get(effectId);
		if (definition == null) {
			LOGGER.warn("sendEffect({}) failed: unknown effect", effectId);
			return false;
		}
		Map<String, Float> params = new HashMap<>();
		for (Map.Entry<String, VFXDefinition.ParamSpec> entry : definition.getParams().entrySet()) {
			VFXDefinition.ParamSpec spec = entry.getValue();
			if (!spec.animated() && spec.keyframes().isEmpty() && spec.bound() == null && spec.multiply() == null) {
				params.put(entry.getKey(), spec.constant());
			}
		}
		params.putAll(overrides);
		int duration = definition.isPersistent() ? -1 : definition.getDefaultDuration();
		EasingFunction effectiveEasing = easing != null ? EasingFunction.builtIn(easing) : definition.getDefaultEasing();
		ServerPlayNetworking.send(player, VFXTriggerPayload.play(effectId, duration, instanceId, worldPos, params, effectiveEasing.name()));
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

	/**
	 * Tells a player's client to stop one specific instance of an effect. Requires the instance
	 * id that was sent with {@link #sendEffect(ServerPlayer, Identifier, long, Vec3, Map, EasingType)}
	 * when the effect was triggered.
	 *
	 * @param player     the receiving player
	 * @param effectId   effect id
	 * @param instanceId the instance id to stop
	 */
	public static void sendStop(final ServerPlayer player, final Identifier effectId, final long instanceId) {
		ServerPlayNetworking.send(player, VFXTriggerPayload.stop(effectId, instanceId));
	}

	/**
	 * Live-overrides a parameter of a running effect on the player's client, without
	 * restarting its timeline. Ignored (with a client-side log warning) when the effect is
	 * not currently running.
	 *
	 * @param player  the receiving player
	 * @param effectId effect id
	 * @param param   parameter name
	 * @param value   the new constant value
	 */
	public static void sendSetParam(final ServerPlayer player, final Identifier effectId, final String param, final float value) {
		ServerPlayNetworking.send(player, VFXTriggerPayload.setParam(effectId, param, value));
	}

	/**
	 * Adds or replaces a keyframe of a parameter on a running effect on the player's client.
	 * Ignored (with a client-side log warning) when the effect is not currently running.
	 *
	 * @param player  the receiving player
	 * @param effectId effect id
	 * @param param   parameter name
	 * @param time    keyframe time in ticks from the effect start
	 * @param value   keyframe value
	 * @param easing  easing curve towards the next keyframe
	 */
	public static void sendKeyframe(final ServerPlayer player, final Identifier effectId, final String param, final int time, final float value, final EasingType easing) {
		ServerPlayNetworking.send(player, VFXTriggerPayload.keyframe(effectId, param, time, value, easing));
	}

	/**
	 * Adds or replaces a keyframe of a parameter on a running effect on the player's client, with
	 * a custom easing curve (named datapack curve or inline name).
	 *
	 * @param player  the receiving player
	 * @param effectId effect id
	 * @param param   parameter name
	 * @param time    keyframe time in ticks from the effect start
	 * @param value   keyframe value
	 * @param easing  easing curve name (built-in or custom datapack curve)
	 */
	public static void sendKeyframe(final ServerPlayer player, final Identifier effectId, final String param, final int time, final float value, final String easing) {
		ServerPlayNetworking.send(player, VFXTriggerPayload.keyframe(effectId, param, time, value, easing));
	}
}
