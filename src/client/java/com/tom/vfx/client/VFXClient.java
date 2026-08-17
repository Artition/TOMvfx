package com.tom.vfx.client;

import com.tom.vfx.api.VFXAPI;
import com.tom.vfx.client.effect.VFXEffectManager;
import com.tom.vfx.client.postprocessing.VFXShaderPrograms;
import com.tom.vfx.client.render.VFXWorldOverlayRenderer;
import com.tom.vfx.network.VFXAction;
import com.tom.vfx.network.VFXTriggerPayload;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint: registers the post-processing pipelines, the local dispatcher and the
 * network receiver that turns {@link VFXTriggerPayload}s into running effects.
 */
public class VFXClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("tompfx/client");

	@Override
	public void onInitializeClient() {
		VFXShaderPrograms.register();
		VFXWorldOverlayRenderer.register();
		VFXAPI.setLocalDispatcher(new VFXClientAPI());
		ClientPlayNetworking.registerGlobalReceiver(VFXTriggerPayload.TYPE, this::handleTrigger);
		LOGGER.info("TOMPFX client initialized");
	}

	private void handleTrigger(final VFXTriggerPayload payload, final ClientPlayNetworking.Context context) {
		context.client().execute(() -> {
			if (payload.protocolVersion() != VFXTriggerPayload.PROTOCOL_VERSION) {
				LOGGER.warn("Ignoring VFX packet from server: protocol version mismatch (server={}, client={})", payload.protocolVersion(), VFXTriggerPayload.PROTOCOL_VERSION);
				return;
			}
			LOGGER.info("Received VFX packet: action={}, effect={}, duration={}, params={}", payload.action(), payload.effectId(), payload.durationTicks(), payload.params().keySet());
			if (payload.action() == VFXAction.STOP) {
				VFXEffectManager.get().stop(payload.effectId());
			} else if (payload.action() == VFXAction.SET_PARAM || payload.action() == VFXAction.KEYFRAME) {
				if (payload.params().size() != 1) {
					LOGGER.warn("Ignoring VFX packet: {} expects exactly one parameter, got {}", payload.action(), payload.params().size());
					return;
				}
				Map.Entry<String, Float> entry = payload.params().entrySet().iterator().next();
				if (payload.action() == VFXAction.SET_PARAM) {
					if (!VFXEffectManager.get().setParam(payload.effectId(), entry.getKey(), entry.getValue())) {
						LOGGER.warn("VFX set_param: effect '{}' is not running", payload.effectId());
					}
				} else if (!VFXEffectManager.get().setKeyframe(payload.effectId(), entry.getKey(), payload.durationTicks(), entry.getValue(), payload.easing())) {
					LOGGER.warn("VFX keyframe: effect '{}' is not running", payload.effectId());
				}
			} else {
				VFXEffectManager.get().play(payload.effectId(), payload.durationTicks(), payload.params(), payload.easing());
			}
		});
	}
}
