package com.minersdream;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Item MINERS_DREAM = registerItem(
		"miners_dream",
		MinersDreamItem::new,
		new Item.Properties().stacksTo(16)
	);

	private static <T extends Item> T registerItem(String name, java.util.function.Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MinersDream.MOD_ID, name));
		T item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void register() {
		MinersDream.LOGGER.info("Registering items for %s".formatted(MinersDream.MOD_ID));
	}
}
