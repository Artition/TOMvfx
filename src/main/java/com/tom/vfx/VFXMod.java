package com.tom.vfx;

import com.tom.vfx.command.VFXCommand;
import com.tom.vfx.network.VFXPayloads;
import com.tom.vfx.resource.VFXDefinitionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VFXMod implements ModInitializer {
	public static final String MOD_ID = "tompfx";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		VFXPayloads.register();
		CommandRegistrationCallback.EVENT.register(VFXCommand::register);

		// Datapack VFX definitions (data/<namespace>/vfx/<effect>.json). Also registered from the
		// client entrypoint so single player rendering can resolve datapack-defined effects.
		registerVfxDefinitionReloadListener();
	}

	public static void registerVfxDefinitionReloadListener() {
		try {
			ResourceLoader.get(PackType.SERVER_DATA)
				.registerReloadListener(id("vfx_definitions"), VFXDefinitionManager.get());
		} catch (RuntimeException e) {
			LOGGER.warn("Could not register VFX definition reload listener", e);
		}
	}

	public static Identifier id(final String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
