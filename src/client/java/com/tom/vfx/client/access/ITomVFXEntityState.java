package com.tom.vfx.client.access;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Extension attached to {@link net.minecraft.client.renderer.entity.state.EntityRenderState}
 * instances so the entity render pipeline can carry the entity's UUID from
 * {@code extractRenderState} to {@code submit}. Vanilla render states have no UUID field, and
 * {@code getRenderType}/{@code submit} only receive the state — the UUID is the key that maps a
 * running {@code entity_tint}/{@code entity_outline} effect onto its targeted entities.
 *
 * <p>Deliberately placed outside the mixin package: Mixin forbids referencing classes that live
 * in a registered mixin package from normal (non-mixin) class loading, and this interface is
 * loaded by other mods' class loaders too (e.g. ElytraTrails' {@code TrailSystem} during client
 * init), which crashed with {@code IllegalClassLoadError} when the interface sat in
 * {@code com.tom.vfx.client.mixin}.</p>
 */
public interface ITomVFXEntityState {
	@Nullable
	UUID tompfx$getUuid();

	void tompfx$setUuid(@Nullable UUID uuid);
}