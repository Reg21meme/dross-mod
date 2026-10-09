package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.portal.RiftKeyItem;
import com.reg21meme.dross.quest.DrossGuideBookItem;
import com.reg21meme.dross.quest.WeatheredLetterItem;
import com.reg21meme.dross.villager.AdminSwordItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems
{
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Dross.MODID);

    // Portal area: frame block item, for building test frames in creative (no recipe, so survival can't get it)
    public static final RegistryObject<Item> DROSS_PORTAL_FRAME = ITEMS.register("dross_portal_frame",
            () -> new BlockItem(ModBlocks.DROSS_PORTAL_FRAME.get(), new Item.Properties()));

    // Portal area: the Rift Key. Thrown into a Dross frame, it lights the portal (the villager area gives it out)
    public static final RegistryObject<Item> RIFT_KEY = ITEMS.register("rift_key", RiftKeyItem::new);

    // Villager area: TESTING ONLY, sold by the Dross trader (see Parked in CLAUDE.md)
    public static final RegistryObject<Item> ADMIN_SWORD = ITEMS.register("admin_sword", AdminSwordItem::new);

    // Villager area: Dross Trader spawn egg (black with electric blue spots)
    public static final RegistryObject<Item> DROSS_TRADER_SPAWN_EGG = ITEMS.register("dross_trader_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TRADER, DrossColors.TRADER_EGG_BASE, DrossColors.TRADER_EGG_SPOTS, new Item.Properties()));

    // Quest area: the Weathered Letter (Nether chest loot, readable, starts the quest)
    public static final RegistryObject<Item> WEATHERED_LETTER = ITEMS.register("weathered_letter", WeatheredLetterItem::new);

    // Quest area: the Dross Guide Book (given on a player's first arrival in the Dross, readable)
    public static final RegistryObject<Item> DROSS_GUIDE_BOOK = ITEMS.register("dross_guide_book", DrossGuideBookItem::new);

    private ModItems() {}
}
