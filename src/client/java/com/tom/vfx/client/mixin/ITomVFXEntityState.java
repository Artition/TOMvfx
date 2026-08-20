package com.tom.vfx.client.mixin;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Extension attached to {@link net.minecraft.client.renderer.entity.state.EntityRenderState}
 * instances so the entity render pipeline can carry the entity's UUID from
 * {@code extractRenderState} to {@code submit}. Vanilla render states have no UUID field, and
 * {@code getRenderType}/{@code submit} only receive the state — the UUID is the key that maps a
 * running {@code entity_tint}/{@code entity_outline} effect onto its targeted entities.
 */
public interface ITomVFXEntityState {
	@Nullable
	UUID tompfx$getUuid();

	void tompfx$setUuid(@Nullable UUID uuid);
}