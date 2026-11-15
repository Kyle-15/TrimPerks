package com.trimperks;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point. Effects, retrimming and the settle timer get registered here as they're built (see DESIGN.md). */
public class TrimPerks implements ModInitializer {
	public static final String MOD_ID = "trimperks";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	public static TrimPerksConfig config;

	@Override
	public void onInitialize() {
		config = TrimPerksConfig.load();
		Effects.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> TrimPerksCommands.register(dispatcher));
		// /reload re-reads config/trimperks.json too
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			config = TrimPerksConfig.load();
			LOG.info("Reloaded {}", TrimPerksConfig.file());
		});
		// Build the guide once at startup so a broken dialog shows up in the log, not when a player runs the command
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				Guide.build(server);
				Guide.buildOp(server);
				// In dev, load every mixin target now so a broken hook fails at startup, not when a player first finds it
				if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment())
					org.spongepowered.asm.mixin.MixinEnvironment.getCurrentEnvironment().audit();
				LOG.info("TrimPerks guide OK ({} effects)", Perks.ALL.size());
			} catch (RuntimeException e) {
				LOG.error("TrimPerks guide is broken", e);
			}
		});
	}
}
