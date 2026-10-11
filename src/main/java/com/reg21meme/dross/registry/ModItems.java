package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.trophy.ColossusTrophyItem;
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

    // Boss area: Cinder Colossus spawn egg (scorched stone with lava spots); it spawns a fighting Colossus
    public static final RegistryObject<Item> CINDER_COLOSSUS_SPAWN_EGG = ITEMS.register("cinder_colossus_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CINDER_COLOSSUS, DrossColors.COLOSSUS_EGG_BASE, DrossColors.COLOSSUS_EGG_SPOTS,
                    new Item.Properties()));

    // Boss area: the Cinder Colossus's minions' spawn eggs (stone grey with dim cracks; dark crust with bright lava)
    public static final RegistryObject<Item> STONE_GOLEM_SPAWN_EGG = ITEMS.register("stone_golem_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.STONE_GOLEM, DrossColors.STONE_GOLEM_EGG_BASE, DrossColors.STONE_GOLEM_EGG_SPOTS,
                    new Item.Properties()));
    public static final RegistryObject<Item> LAVA_GOLEM_SPAWN_EGG = ITEMS.register("lava_golem_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.LAVA_GOLEM, DrossColors.LAVA_GOLEM_EGG_BASE, DrossColors.LAVA_GOLEM_EGG_SPOTS,
                    new Item.Properties()));

    // Boss area: the Cinder Colossus trophy (its design is in the item; fireproof, never despawns)
    public static final RegistryObject<Item> COLOSSUS_TROPHY = ITEMS.register("colossus_trophy",
            () -> new ColossusTrophyItem(ModBlocks.COLOSSUS_TROPHY.get()));

    // Dimension area: block items for the Dross's terrain blocks (stone, ores, silverwood, Stormvine, Silvermoss, plants)
    public static final RegistryObject<Item> DUSKSTONE = ITEMS.register("duskstone", () -> new BlockItem(ModBlocks.DUSKSTONE.get(), new Item.Properties()));
    // Dimension area: Duskstone Coal Ore
    public static final RegistryObject<Item> DUSKSTONE_COAL_ORE = ITEMS.register("duskstone_coal_ore", () -> new BlockItem(ModBlocks.DUSKSTONE_COAL_ORE.get(), new Item.Properties()));
    // Dimension area: Duskstone Gold Ore
    public static final RegistryObject<Item> DUSKSTONE_GOLD_ORE = ITEMS.register("duskstone_gold_ore", () -> new BlockItem(ModBlocks.DUSKSTONE_GOLD_ORE.get(), new Item.Properties()));
    // Dimension area: Moonsilver Ore
    public static final RegistryObject<Item> MOONSILVER_ORE = ITEMS.register("moonsilver_ore", () -> new BlockItem(ModBlocks.MOONSILVER_ORE.get(), new Item.Properties()));
    // Dimension area: Stormite Ore
    public static final RegistryObject<Item> STORMITE_ORE = ITEMS.register("stormite_ore", () -> new BlockItem(ModBlocks.STORMITE_ORE.get(), new Item.Properties()));
    // Dimension area: Silverwood Log
    public static final RegistryObject<Item> SILVERWOOD_LOG = ITEMS.register("silverwood_log", () -> new BlockItem(ModBlocks.SILVERWOOD_LOG.get(), new Item.Properties()));
    // Dimension area: Silverwood Wood
    public static final RegistryObject<Item> SILVERWOOD_WOOD = ITEMS.register("silverwood_wood", () -> new BlockItem(ModBlocks.SILVERWOOD_WOOD.get(), new Item.Properties()));
    // Dimension area: Stripped Silverwood Log
    public static final RegistryObject<Item> STRIPPED_SILVERWOOD_LOG = ITEMS.register("stripped_silverwood_log", () -> new BlockItem(ModBlocks.STRIPPED_SILVERWOOD_LOG.get(), new Item.Properties()));
    // Dimension area: Stripped Silverwood Wood
    public static final RegistryObject<Item> STRIPPED_SILVERWOOD_WOOD = ITEMS.register("stripped_silverwood_wood", () -> new BlockItem(ModBlocks.STRIPPED_SILVERWOOD_WOOD.get(), new Item.Properties()));
    // Dimension area: Silverwood Planks
    public static final RegistryObject<Item> SILVERWOOD_PLANKS = ITEMS.register("silverwood_planks", () -> new BlockItem(ModBlocks.SILVERWOOD_PLANKS.get(), new Item.Properties()));
    // Dimension area: Silverwood Leaves
    public static final RegistryObject<Item> SILVERWOOD_LEAVES = ITEMS.register("silverwood_leaves", () -> new BlockItem(ModBlocks.SILVERWOOD_LEAVES.get(), new Item.Properties()));
    // Dimension area: Silverwood Sapling
    public static final RegistryObject<Item> SILVERWOOD_SAPLING = ITEMS.register("silverwood_sapling", () -> new BlockItem(ModBlocks.SILVERWOOD_SAPLING.get(), new Item.Properties()));
    // Dimension area: Stormvine
    public static final RegistryObject<Item> STORMVINE = ITEMS.register("stormvine", () -> new BlockItem(ModBlocks.STORMVINE.get(), new Item.Properties()));
    // Dimension area: Silvermoss
    public static final RegistryObject<Item> SILVERMOSS = ITEMS.register("silvermoss", () -> new BlockItem(ModBlocks.SILVERMOSS.get(), new Item.Properties()));
    // Dimension area: Glimmer Fern
    public static final RegistryObject<Item> GLIMMER_FERN = ITEMS.register("glimmer_fern", () -> new BlockItem(ModBlocks.GLIMMER_FERN.get(), new Item.Properties()));
    // Dimension area: Wispbloom
    public static final RegistryObject<Item> WISPBLOOM = ITEMS.register("wispbloom", () -> new BlockItem(ModBlocks.WISPBLOOM.get(), new Item.Properties()));

    // Dimension area: Stormite Shard (a glowing blue crystal from Stormite Ore; for special arrows and the Great Mystic Bow later)
    public static final RegistryObject<Item> STORMITE_SHARD = ITEMS.register("stormite_shard", () -> new Item(new Item.Properties()));
    // Dimension area: Raw Moonsilver (from Moonsilver Ore; smelts into a Moonsilver Ingot)
    public static final RegistryObject<Item> RAW_MOONSILVER = ITEMS.register("raw_moonsilver", () -> new Item(new Item.Properties()));
    // Dimension area: Moonsilver Ingot (a pale metal for anti-undead gear later)
    public static final RegistryObject<Item> MOONSILVER_INGOT = ITEMS.register("moonsilver_ingot", () -> new Item(new Item.Properties()));

    private ModItems() {}
}
