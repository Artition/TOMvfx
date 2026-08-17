package com.tom.vfx.network;

import com.tom.vfx.effect.EasingType;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client packet that triggers or stops a VFX effect. Carries a protocol version,
 * the effect id, the action, the duration in ticks, the (already resolved) parameter map,
 * the easing curve and optional entity targets (UUID strings or player names).
 */
public record VFXTriggerPayload(byte protocolVersion, Identifier effectId, VFXAction action, int durationTicks, Map<String, Float> params, EasingType easing, List<String> targets)
	implements CustomPacketPayload {
	public static final byte PROTOCOL_VERSION = 4;
	public static final Type<VFXTriggerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("tompfx", "vfx_trigger"));

	/** Safety cap for the entity target list (network input, see AGENTS.md). */
	private static final int MAX_TARGETS = 16;

	public static final StreamCodec<ByteBuf, EasingType> EASING_CODEC = ByteBufCodecs.STRING_UTF8.map(EasingType::fromString, EasingType::name);
	public static final StreamCodec<ByteBuf, VFXAction> ACTION_CODEC = ByteBufCodecs.BYTE.map(VFXAction::fromId, VFXAction::getId);
	private static final StreamCodec<ByteBuf, HashMap<String, Float>> PARAMS_CODEC = ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT);

	// ponytail: hand-rolled codec instead of StreamCodec.composite (7 fields > composite's 6).
	public static final StreamCodec<RegistryFriendlyByteBuf, VFXTriggerPayload> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public void encode(final RegistryFriendlyByteBuf buf, final VFXTriggerPayload payload) {
			buf.writeByte(payload.protocolVersion);
			Identifier.STREAM_CODEC.encode(buf, payload.effectId);
			ACTION_CODEC.encode(buf, payload.action);
			buf.writeVarInt(payload.durationTicks);
			PARAMS_CODEC.encode(buf, new HashMap<>(payload.params));
			EASING_CODEC.encode(buf, payload.easing);
			buf.writeVarInt(Math.min(payload.targets.size(), MAX_TARGETS));
			for (String target : payload.targets) {
				buf.writeUtf(target, 256);
			}
		}

		@Override
		public VFXTriggerPayload decode(final RegistryFriendlyByteBuf buf) {
			byte protocolVersion = buf.readByte();
			Identifier effectId = Identifier.STREAM_CODEC.decode(buf);
			VFXAction action = ACTION_CODEC.decode(buf);
			int durationTicks = buf.readVarInt();
			Map<String, Float> params = PARAMS_CODEC.decode(buf);
			EasingType easing = EASING_CODEC.decode(buf);
			int count = Math.min(buf.readVarInt(), MAX_TARGETS);
			List<String> targets = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				targets.add(buf.readUtf(256));
			}
			return new VFXTriggerPayload(protocolVersion, effectId, action, durationTicks, params, easing, targets);
		}
	};

	/**
	 * Creates a play payload with the current protocol version.
	 */
	public static VFXTriggerPayload play(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) {
		return play(effectId, durationTicks, params, easing, List.of());
	}

	/**
	 * Creates a play payload with entity targets (UUID strings or player names).
	 */
	public static VFXTriggerPayload play(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing, final List<String> targets) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.PLAY, durationTicks, params, easing, targets);
	}

	/**
	 * Creates a stop payload with the current protocol version.
	 */
	public static VFXTriggerPayload stop(final Identifier effectId) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.STOP, 0, Map.of(), EasingType.LINEAR, List.of());
	}

	/**
	 * Creates a live parameter-override payload for a running effect ({@code params} carries
	 * a single {@code name -> value} entry).
	 */
	public static VFXTriggerPayload setParam(final Identifier effectId, final String param, final float value) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.SET_PARAM, 0, Map.of(param, value), EasingType.LINEAR, List.of());
	}

	/**
	 * Creates a live keyframe payload for a running effect: {@code time} is carried in
	 * {@code durationTicks}, the value in {@code params} and the outgoing easing in {@code easing}.
	 */
	public static VFXTriggerPayload keyframe(final Identifier effectId, final String param, final int time, final float value, final EasingType easing) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.KEYFRAME, time, Map.of(param, value), easing, List.of());
	}

	/**
	 * Creates a live entity-target payload for a running {@code entity_tint}/
	 * {@code entity_outline} effect: {@code targets} carries the single UUID/name.
	 */
	public static VFXTriggerPayload setTarget(final Identifier effectId, final String target) {
		return new VFXTriggerPayload(PROTOCOL_VERSION, effectId, VFXAction.SET_TARGET, 0, Map.of(), EasingType.LINEAR, List.of(target));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
