package dev.vfxweaver.client;

import dev.vfxweaver.api.VFXLocalDispatcher;
import dev.vfxweaver.client.effect.VFXEffectManager;
import dev.vfxweaver.effect.EasingFunction;
import dev.vfxweaver.effect.EasingType;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Client implementation of {@link VFXLocalDispatcher} that forwards playback requests onto the
 * render thread, where the effect manager is consumed each frame. The instance id is allocated
 * synchronously so it can be returned to the caller; the play itself is scheduled.
 */
public class VFXClientAPI implements VFXLocalDispatcher {
	@Override
	public long playEffect(final Identifier effectId, final int durationTicks, final Map<String, Float> params, final EasingType easing) {
		long instanceId = VFXEffectManager.get().allocateInstanceId();
		Minecraft.getInstance().execute(() -> VFXEffectManager.get().play(effectId, durationTicks, instanceId, null, params, EasingFunction.builtIn(easing)));
		return instanceId;
	}

	@Override
	public void stopEffect(final Identifier effectId) {
		Minecraft.getInstance().execute(() -> VFXEffectManager.get().stop(effectId));
	}

	@Override
	public void stopEffect(final long instanceId) {
		Minecraft.getInstance().execute(() -> VFXEffectManager.get().stop(instanceId));
	}

	@Override
	public void stopAllEffects() {
		Minecraft.getInstance().execute(() -> VFXEffectManager.get().stopAll());
	}
}