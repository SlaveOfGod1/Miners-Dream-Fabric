package com.minersdream;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinersDream implements ModInitializer {
	public static final String MOD_ID = "miners-dream";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.register();

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
			.register((output) -> {
				output.accept(ModItems.MINERS_DREAM);
			});

		LOGGER.info("Miners Dream loaded!");
	}
}
