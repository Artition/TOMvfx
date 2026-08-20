package com.tom.vfx.network;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client packet that synchronizes the datapack-defined VFX effect definitions and
 * easing curves to a connecting (or just-reloaded) client. The client keeps its built-in
 * {@code tompfx:*} effects and merges the received datapack definitions on top, so custom
 * effects declared in {@code data/<namespace>/vfx/*.json} work on dedicated servers just like
 * in single player.
 *
 * <p>Carries the raw JSON source of each definition/curve, which the client re-parses through
 * the normal {@code VFXDefinitionManager}/{@code VFXCurveManager} loading path (so a malformed
 * entry from the server is logged and skipped without breaking the rest).
 */
public record VFXSyncPayload(Map<Identifier, String> definitions, Map<Identifier, String> curves)
	implements CustomPacketPayload {
	public static final Type<VFXSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("tompfx", "vfx_sync"));

	private static final StreamCodec<ByteBuf, Map<Identifier, String>> MAP_CODEC =
		ByteBufCodecs.map(HashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.STRING_UTF8);

	public static final StreamCodec<ByteBuf, VFXSyncPayload> STREAM_CODEC = StreamCodec.composite(
		MAP_CODEC, VFXSyncPayload::definitions,
		MAP_CODEC, VFXSyncPayload::curves,
		VFXSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}