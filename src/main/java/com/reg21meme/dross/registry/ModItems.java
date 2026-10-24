package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.spear.MoonsilverSpearItem;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyItem;
import com.reg21meme.dross.boss.rage.RageWandItem;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyItem;
import com.reg21meme.dross.boss.trophy.ColossusTrophyItem;
import com.reg21meme.dross.portal.RiftKeyItem;
import com.reg21meme.dross.quest.DrossGuideBookItem;
import com.reg21meme.dross.quest.WeatheredLetterItem;
import com.reg21meme.dross.villager.AdminSwordItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
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

    // Boss area: the Rat King (he wanders about: his fight isn't built yet)
    public static final RegistryObject<Item> RAT_KING_SPAWN_EGG = ITEMS.register("rat_king_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.RAT_KING, DrossColors.RAT_KING_EGG_BASE, DrossColors.RAT_KING_EGG_SPOTS,
                    new Item.Properties()));

    // Boss area: the Rat King's rats' spawn eggs, each the user's pick of its kind (the Plague Rat, the Plague Ravager)
    public static final RegistryObject<Item> RAT_SPAWN_EGG = ITEMS.register("rat_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.RAT, DrossColors.RAT_EGG_BASE, DrossColors.RAT_EGG_SPOTS,
                    new Item.Properties()));
    public static final RegistryObject<Item> ENRAGED_RAT_SPAWN_EGG = ITEMS.register("enraged_rat_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ENRAGED_RAT, DrossColors.ENRAGED_RAT_EGG_BASE,
                    DrossColors.ENRAGED_RAT_EGG_SPOTS, new Item.Properties()));

    // Boss area: Rimefall's animals (CLAUDE.md step 14). A Rimehorn egg makes a male or a female at random; used on a
    // grown one (a Rimehorn or a moose) it makes a calf.
    public static final RegistryObject<Item> RIMEHORN_SPAWN_EGG = ITEMS.register("rimehorn_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.RIMEHORN, DrossColors.RIMEHORN_EGG_BASE, DrossColors.RIMEHORN_EGG_SPOTS,
                    new Item.Properties()));
    public static final RegistryObject<Item> MOOSE_SPAWN_EGG = ITEMS.register("moose_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.MOOSE, DrossColors.MOOSE_EGG_BASE, DrossColors.MOOSE_EGG_SPOTS,
                    new Item.Properties()));
    public static final RegistryObject<Item> POND_SKATER_SPAWN_EGG = ITEMS.register("pond_skater_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.POND_SKATER, DrossColors.POND_SKATER_EGG_BASE, DrossColors.POND_SKATER_EGG_SPOTS,
                    new Item.Properties()));
    // Boss area: the Huntsman (he comes riding his Alpha Moose, calm until the first hit)
    public static final RegistryObject<Item> HUNTSMAN_SPAWN_EGG = ITEMS.register("huntsman_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.HUNTSMAN, DrossColors.HUNTSMAN_EGG_BASE, DrossColors.HUNTSMAN_EGG_SPOTS,
                    new Item.Properties()));
    // Boss area: the Huntsman's bola (how it looks flying; not in the creative tab)
    public static final RegistryObject<Item> BOLA = ITEMS.register("bola", () -> new Item(new Item.Properties()));

    // Boss area: the Cinder Colossus trophy (its design is in the item; fireproof, never despawns)
    public static final RegistryObject<Item> COLOSSUS_TROPHY = ITEMS.register("colossus_trophy",
            () -> new ColossusTrophyItem(ModBlocks.COLOSSUS_TROPHY.get()));

    // Boss area: the Rat King trophy (its design is in the item; fireproof, never despawns)
    public static final RegistryObject<Item> RAT_KING_TROPHY = ITEMS.register("rat_king_trophy",
            () -> new RatKingTrophyItem(ModBlocks.RAT_KING_TROPHY.get()));
    // Boss area: the Huntsman trophy (its design is in the item; the 3D figure in the hand)
    public static final RegistryObject<Item> HUNTSMAN_TROPHY = ITEMS.register("huntsman_trophy",
            () -> new HuntsmanTrophyItem(ModBlocks.HUNTSMAN_TROPHY.get()));

    // Boss area: the Rage Wand, a creative-only test tool (no recipe): hit mobs and they fight each other
    public static final RegistryObject<Item> RAGE_WAND = ITEMS.register("rage_wand", RageWandItem::new);

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

    // Dimension area: block items for the other biomes' blocks (Cinder Wastes, Blightwood, Rimefall, the old Nether Scar, Gravecrags)
    public static final RegistryObject<Item> ASH = ITEMS.register("ash", () -> new BlockItem(ModBlocks.ASH.get(), new Item.Properties()));
    // Dimension area: Ash Layer
    public static final RegistryObject<Item> ASH_LAYER = ITEMS.register("ash_layer", () -> new BlockItem(ModBlocks.ASH_LAYER.get(), new Item.Properties()));
    // Dimension area: Charred Log
    public static final RegistryObject<Item> CHARRED_LOG = ITEMS.register("charred_log", () -> new BlockItem(ModBlocks.CHARRED_LOG.get(), new Item.Properties()));
    // Dimension area: Emberweed
    public static final RegistryObject<Item> EMBERWEED = ITEMS.register("emberweed", () -> new BlockItem(ModBlocks.EMBERWEED.get(), new Item.Properties()));
    // Dimension area: Cinder Vent
    public static final RegistryObject<Item> CINDER_VENT = ITEMS.register("cinder_vent", () -> new BlockItem(ModBlocks.CINDER_VENT.get(), new Item.Properties()));
    // Dimension area: Sporeloam
    public static final RegistryObject<Item> SPORELOAM = ITEMS.register("sporeloam", () -> new BlockItem(ModBlocks.SPORELOAM.get(), new Item.Properties()));
    // Dimension area: Blightcap Block
    public static final RegistryObject<Item> BLIGHTCAP_BLOCK = ITEMS.register("blightcap_block", () -> new BlockItem(ModBlocks.BLIGHTCAP_BLOCK.get(), new Item.Properties()));
    // Dimension area: Rotting Blightcap
    public static final RegistryObject<Item> ROTTING_BLIGHTCAP = ITEMS.register("rotting_blightcap", () -> new BlockItem(ModBlocks.ROTTING_BLIGHTCAP.get(), new Item.Properties()));
    // Dimension area: Blightcap Stem
    public static final RegistryObject<Item> BLIGHTCAP_STEM = ITEMS.register("blightcap_stem", () -> new BlockItem(ModBlocks.BLIGHTCAP_STEM.get(), new Item.Properties()));
    // Dimension area: Gloomcap
    public static final RegistryObject<Item> GLOOMCAP = ITEMS.register("gloomcap", () -> new BlockItem(ModBlocks.GLOOMCAP.get(), new Item.Properties()));
    // Dimension area: Sporepod
    public static final RegistryObject<Item> SPOREPOD = ITEMS.register("sporepod", () -> new BlockItem(ModBlocks.SPOREPOD.get(), new Item.Properties()));
    // Dimension area: Hoarfrost
    public static final RegistryObject<Item> HOARFROST = ITEMS.register("hoarfrost", () -> new BlockItem(ModBlocks.HOARFROST.get(), new Item.Properties()));
    // Dimension area: Frostbitten Log
    public static final RegistryObject<Item> FROSTBITTEN_LOG = ITEMS.register("frostbitten_log", () -> new BlockItem(ModBlocks.FROSTBITTEN_LOG.get(), new Item.Properties()));
    // Dimension area: Frostbloom
    public static final RegistryObject<Item> FROSTBLOOM = ITEMS.register("frostbloom", () -> new BlockItem(ModBlocks.FROSTBLOOM.get(), new Item.Properties()));
    // Dimension area: Icicle
    public static final RegistryObject<Item> ICICLE = ITEMS.register("icicle", () -> new BlockItem(ModBlocks.ICICLE.get(), new Item.Properties()));
    // Dimension area: Scarred Netherrack
    public static final RegistryObject<Item> SCARRED_NETHERRACK = ITEMS.register("scarred_netherrack", () -> new BlockItem(ModBlocks.SCARRED_NETHERRACK.get(), new Item.Properties()));
    // Dimension area: Scar Roots
    public static final RegistryObject<Item> SCAR_ROOTS = ITEMS.register("scar_roots", () -> new BlockItem(ModBlocks.SCAR_ROOTS.get(), new Item.Properties()));
    // Dimension area: Soulwick
    public static final RegistryObject<Item> SOULWICK = ITEMS.register("soulwick", () -> new BlockItem(ModBlocks.SOULWICK.get(), new Item.Properties()));
    // Dimension area: Scar Vent
    public static final RegistryObject<Item> SCAR_VENT = ITEMS.register("scar_vent", () -> new BlockItem(ModBlocks.SCAR_VENT.get(), new Item.Properties()));
    // Dimension area: Cragstone
    public static final RegistryObject<Item> CRAGSTONE = ITEMS.register("cragstone", () -> new BlockItem(ModBlocks.CRAGSTONE.get(), new Item.Properties()));
    // Dimension area: Storm Lichen
    public static final RegistryObject<Item> STORM_LICHEN = ITEMS.register("storm_lichen", () -> new BlockItem(ModBlocks.STORM_LICHEN.get(), new Item.Properties()));
    // Dimension area: Crag Thistle
    public static final RegistryObject<Item> CRAG_THISTLE = ITEMS.register("crag_thistle", () -> new BlockItem(ModBlocks.CRAG_THISTLE.get(), new Item.Properties()));
    // Dimension area, the sewers: Sewer Sludge
    public static final RegistryObject<Item> SEWER_SLUDGE = ITEMS.register("sewer_sludge", () -> new BlockItem(ModBlocks.SEWER_SLUDGE.get(), new Item.Properties()));
    // Dimension area, the Rat King's sewer: Blight Vent
    public static final RegistryObject<Item> BLIGHT_VENT = ITEMS.register("blight_vent", () -> new BlockItem(ModBlocks.BLIGHT_VENT.get(), new Item.Properties()));
    // Boss area, the hunter villages: Meat Block
    public static final RegistryObject<Item> MEAT_BLOCK = ITEMS.register("meat_block", () -> new BlockItem(ModBlocks.MEAT_BLOCK.get(), new Item.Properties()));
    // Boss area, the hunter villages: Roasted Meat Block
    public static final RegistryObject<Item> ROASTED_MEAT_BLOCK = ITEMS.register("roasted_meat_block", () -> new BlockItem(ModBlocks.ROASTED_MEAT_BLOCK.get(), new Item.Properties()));
    // Boss area, the hunter villages: Frostroot Crate
    public static final RegistryObject<Item> FROSTROOT_CRATE = ITEMS.register("frostroot_crate", () -> new BlockItem(ModBlocks.FROSTROOT_CRATE.get(), new Item.Properties()));
    // Boss area, the hunter villages: Rimeberry Basket
    public static final RegistryObject<Item> RIMEBERRY_BASKET = ITEMS.register("rimeberry_basket", () -> new BlockItem(ModBlocks.RIMEBERRY_BASKET.get(), new Item.Properties()));
    // Boss area, Rimefall's crops: Frostroot (plants Frostroots; not food yet: the user, "we will do that later")
    public static final RegistryObject<Item> FROSTROOT = ITEMS.register("frostroot", () -> new ItemNameBlockItem(ModBlocks.FROSTROOTS.get(), new Item.Properties()));
    // Boss area, Rimefall's crops: Snow Lichen (plants Snow Lichen; the moose's food)
    public static final RegistryObject<Item> SNOW_LICHEN = ITEMS.register("snow_lichen", () -> new ItemNameBlockItem(ModBlocks.SNOW_LICHEN.get(), new Item.Properties()));
    // Boss area, Rimefall's crops: Rimeberries (plant a Rimeberry Bush; the Rimehorns' food)
    public static final RegistryObject<Item> RIMEBERRIES = ITEMS.register("rimeberries", () -> new ItemNameBlockItem(ModBlocks.RIMEBERRY_BUSH.get(), new Item.Properties()));

    // Dimension area: Stormite Shard (a glowing blue crystal from Stormite Ore; for special arrows and the Great Mystic Bow later)
    public static final RegistryObject<Item> STORMITE_SHARD = ITEMS.register("stormite_shard", () -> new Item(new Item.Properties()));
    // Dimension area: Raw Moonsilver (from Moonsilver Ore; smelts into a Moonsilver Ingot)
    public static final RegistryObject<Item> RAW_MOONSILVER = ITEMS.register("raw_moonsilver", () -> new Item(new Item.Properties()));
    // Dimension area: Moonsilver Ingot (a pale metal for anti-undead gear later)
    public static final RegistryObject<Item> MOONSILVER_INGOT = ITEMS.register("moonsilver_ingot", () -> new Item(new Item.Properties()));

    // Boss area: the Moonsilver Spear (the Huntsman's spear for players: stabs, throws and comes back, freezes)
    public static final RegistryObject<Item> MOONSILVER_SPEAR = ITEMS.register("moonsilver_spear", MoonsilverSpearItem::new);
    // Boss area: a Moonsilver Spear planted upright (the hunter villages' spears)
    public static final RegistryObject<Item> PLANTED_SPEAR = ITEMS.register("planted_spear", () -> new BlockItem(ModBlocks.PLANTED_SPEAR.get(), new Item.Properties()));

    // Boss area: the mounted skulls (the Alpha Moose drops its own; bull moose and Rimehorns sometimes)
    public static final RegistryObject<Item> ALPHA_MOOSE_SKULL = ITEMS.register("alpha_moose_skull", () -> new BlockItem(ModBlocks.ALPHA_MOOSE_SKULL.get(), new Item.Properties()));
    public static final RegistryObject<Item> MOOSE_SKULL = ITEMS.register("moose_skull", () -> new BlockItem(ModBlocks.MOOSE_SKULL.get(), new Item.Properties()));
    public static final RegistryObject<Item> RIMEHORN_SKULL = ITEMS.register("rimehorn_skull", () -> new BlockItem(ModBlocks.RIMEHORN_SKULL.get(), new Item.Properties()));

    private ModItems() {}
}
