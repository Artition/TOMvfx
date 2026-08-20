package com.tom.vfx.client.mixin;

import com.tom.vfx.client.access.ITomVFXEntityState;
import java.util.UUID;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds the {@link ITomVFXEntityState} UUID slot to every living entity render state. The value is
 * filled in by {@link LivingEntityRendererMixin#tompfx$storeEntityUuid} during
 * {@code extractRenderState} and read back during {@code submit}.
 */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateMixin implements ITomVFXEntityState {
	@Unique
	private UUID tompfx$uuid;

	@Override
	public @Nullable UUID tompfx$getUuid() {
		return this.tompfx$uuid;
	}

	@Override
	public void tompfx$setUuid(final @Nullable UUID uuid) {
		this.tompfx$uuid = uuid;
	}
}