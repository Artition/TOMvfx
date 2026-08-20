package com.tom.vfx.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers the {@link VFXTriggerPayload} and {@link VFXSyncPayload} packet types on the
 * clientbound play channel.
 */
public final class VFXPayloads {
	private VFXPayloads() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(VFXTriggerPayload.TYPE, VFXTriggerPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VFXSyncPayload.TYPE, VFXSyncPayload.STREAM_CODEC);
	}
}
