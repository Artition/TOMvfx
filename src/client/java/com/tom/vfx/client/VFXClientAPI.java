package com.tom.vfx.client;

import com.tom.vfx.api.VFXLocalDispatcher;
import com.tom.vfx.client.effect.VFXEffectManager;
import com.tom.vfx.effect.EasingType;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Client implementation of {@link VFXLocalDispatcher} that forwards playback requests onto the
 * render thread, where the effect manager is consumed each frame.
 */
public class VFXClientAPI implements VFXLocalDispatcher {
	@Override
	public void playEffect(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) {
		Minecraft.getInstance().execute(() -> VFXEffectManager.get().play(effectId, durationTicks, params, easing));
	}

	@Override
	public void stopEffect(final Identifier effectId) {
		Minecraft.getInstance().execute(() -> VFXEffectManager.get().stop(effectId));
	}

	@Override
	public void stopAllEffects() {
		Minecraft.getInstance().execute(() -> VFXEffectManager.get().stopAll());
	}
}
