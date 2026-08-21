package dev.vfxweaver.client.mixin;

import dev.vfxweaver.client.access.IVFXWeaverEntityState;
import java.util.UUID;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the {@link IVFXWeaverEntityState} UUID slot to every living entity render state. The value is
 * filled in by {@link LivingEntityRendererMixin#vfxweaver$storeEntityUuid} during
 * {@code extractRenderState} and read back during {@code submit}.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements IVFXWeaverEntityState {
	@Unique
	private UUID vfxweaver$uuid;

	@Override
	public @Nullable UUID vfxweaver$getUuid() {
		return this.vfxweaver$uuid;
	}

	@Override
	public void vfxweaver$setUuid(final @Nullable UUID uuid) {
		this.vfxweaver$uuid = uuid;
	}
}