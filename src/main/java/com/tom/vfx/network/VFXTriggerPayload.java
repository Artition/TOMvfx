package com.tom.vfx.network;

import com.tom.vfx.effect.EasingType;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client packet that triggers or stops a VFX effect. Carries a protocol version,
 * the effect id, the action, the duration in ticks, the (already resolved) parameter map and
 * the easing curve.
 */
public record VFXTriggerPayload(byte protocolVersion, Identifier effectId, VFXAction action, int durationTicks, Map<String, Float> params, EasingType easing)
	implements CustomPacketPayload {
	public static final byte PROTOCOL_VERSION = 1;
	public static final Type<VFXTriggerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("tompfx", "vfx_trigger"));

	public static final StreamCodec<ByteBuf, EasingType> EASING_CODEC = ByteBufCodecs.STRING_UTF8.map(EasingType::fromString, EasingType::name);
	public static final StreamCodec<ByteBuf, VFXAction> ACTION_CODEC = ByteBufCodecs.BYTE.map(VFXAction::fromId, VFXAction::getId);

	public static final StreamCodec<RegistryFriendlyByteBuf, VFXTriggerPayload> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.BYTE,
		VFXTriggerPayload::protocolVersion,
		Identifier.STREAM_CODEC,
		VFXTriggerPayload::effectId,
		ACTION_CODEC,
		VFXTriggerPayload::action,
		ByteBufCodecs.VAR_INT,
		VFXTriggerPayload::durationTicks,
		ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT),
		VFXTriggerPayload::params,
		EASING_CODEC,
		VFXTriggerPayload::easing,
		VFXTriggerPayload::new
	);

	/**
	 * Creates a play payload with the current protocol version.
	 */
	public static VFXTriggerPayload play(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.PLAY, durationTicks, params, easing);
	}

	/**
	 * Creates a stop payload with the current protocol version.
	 */
	public static VFXTriggerPayload stop(final Identifier effectId) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.STOP, 0, Map.of(), EasingType.LINEAR);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
