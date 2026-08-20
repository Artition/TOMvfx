package com.tom.vfx.network;

import com.tom.vfx.effect.EasingFunction;
import com.tom.vfx.effect.EasingType;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-to-client packet that triggers, stops or live-edits a VFX effect. Carries a protocol
 * version, the effect id, the action, the duration in ticks, an optional explicit world position,
 * an optional instance id (to target one of several concurrent instances of the same effect),
 * the (already resolved) parameter map and the easing curve name (built-in or custom datapack curve).
 */
public record VFXTriggerPayload(
	byte protocolVersion,
	Identifier effectId,
	VFXAction action,
	int durationTicks,
	long instanceId,
	@Nullable Vec3 position,
	Map<String, Float> params,
	String easing
) implements CustomPacketPayload {
	public static final byte PROTOCOL_VERSION = 4;
	/** Safety cap on the number of parameters a play packet may carry (server input, see AGENTS.md). */
	public static final int MAX_PARAMS = 32;
	public static final Type<VFXTriggerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("tompfx", "vfx_trigger"));

	public static final StreamCodec<ByteBuf, VFXAction> ACTION_CODEC = ByteBufCodecs.BYTE.map(VFXAction::fromId, VFXAction::getId);
	public static final StreamCodec<ByteBuf, Vec3> OPTIONAL_VEC3 = new StreamCodec<>() {
		public Vec3 decode(final ByteBuf input) {
			return input.readBoolean() ? Vec3.STREAM_CODEC.decode(input) : null;
		}

		public void encode(final ByteBuf output, final Vec3 value) {
			output.writeBoolean(value != null);
			if (value != null) {
				Vec3.STREAM_CODEC.encode(output, value);
			}
		}
	};

	public static final StreamCodec<RegistryFriendlyByteBuf, VFXTriggerPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BYTE,
		VFXTriggerPayload::protocolVersion,
		Identifier.STREAM_CODEC,
		VFXTriggerPayload::effectId,
		ACTION_CODEC,
		VFXTriggerPayload::action,
		ByteBufCodecs.VAR_INT,
		VFXTriggerPayload::durationTicks,
		ByteBufCodecs.VAR_LONG,
		VFXTriggerPayload::instanceId,
		OPTIONAL_VEC3,
		VFXTriggerPayload::position,
		ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT, MAX_PARAMS),
		VFXTriggerPayload::params,
		ByteBufCodecs.STRING_UTF8,
		VFXTriggerPayload::easing,
		VFXTriggerPayload::new
	);

	/**
	 * Creates a play payload with the current protocol version.
	 */
	public static VFXTriggerPayload play(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) {
		return play(effectId, durationTicks, 0L, null, params, easing.name());
	}

	/**
	 * Creates a play payload with an explicit instance id and an optional world position.
	 *
	 * @param effectId      effect id
	 * @param durationTicks duration in ticks
	 * @param instanceId    instance id (0 = let the client allocate one)
	 * @param position      world position to re-anchor spatial bindings to (may be null)
	 * @param params        parameter overrides
	 * @param easing        easing curve name (built-in or custom datapack curve)
	 */
	public static VFXTriggerPayload play(final Identifier effectId, final int durationTicks, final long instanceId, final @Nullable Vec3 position, final Map<String, Float> params, final String easing) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.PLAY, durationTicks, instanceId, position, params, easing);
	}

	/**
	 * Creates a stop payload with the current protocol version.
	 */
	public static VFXTriggerPayload stop(final Identifier effectId) {
		return stop(effectId, 0L);
	}

	/**
	 * Creates a stop payload targeting one specific instance of the effect.
	 *
	 * @param effectId   effect id
	 * @param instanceId instance id (0 = stop every instance of the effect)
	 */
	public static VFXTriggerPayload stop(final Identifier effectId, final long instanceId) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.STOP, 0, instanceId, null, Map.of(), EasingType.LINEAR.name());
	}

	/**
	 * Creates a live parameter-override payload for a running effect ({@code params} carries
	 * a single {@code name -> value} entry).
	 */
	public static VFXTriggerPayload setParam(final Identifier effectId, final String param, final float value) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.SET_PARAM, 0, 0L, null, Map.of(param, value), EasingType.LINEAR.name());
	}

	/**
	 * Creates a live keyframe payload for a running effect: {@code time} is carried in
	 * {@code durationTicks}, the value in {@code params} and the outgoing easing in {@code easing}.
	 */
	public static VFXTriggerPayload keyframe(final Identifier effectId, final String param, final int time, final float value, final EasingType easing) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.KEYFRAME, time, 0L, null, Map.of(param, value), easing.name());
	}

	/**
	 * Creates a live keyframe payload with a custom curve name.
	 */
	public static VFXTriggerPayload keyframe(final Identifier effectId, final String param, final int time, final float value, final String easing) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.KEYFRAME, time, 0L, null, Map.of(param, value), easing);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}