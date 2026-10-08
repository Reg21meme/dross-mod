package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
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

    // Villager area: TESTING ONLY, sold by the Dross trader (see Parked in CLAUDE.md)
    public static final RegistryObject<Item> ADMIN_SWORD = ITEMS.register("admin_sword", AdminSwordItem::new);

    // Villager area: Dross Trader spawn egg (black with orange spots, like the portal frame)
    public static final RegistryObject<Item> DROSS_TRADER_SPAWN_EGG = ITEMS.register("dross_trader_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TRADER, 0x10101C, 0xDB7D1F, new Item.Properties()));

    private ModItems() {}
}
