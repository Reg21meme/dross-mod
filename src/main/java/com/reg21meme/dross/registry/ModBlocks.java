package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderFireBlock;
import com.reg21meme.dross.boss.golem.CinderLavaBlock;
import com.reg21meme.dross.boss.huntsman.village.MeatBlocks;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyBlock;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlock;
import com.reg21meme.dross.dimension.block.TerrainBlocks;
import com.reg21meme.dross.portal.DrossPortalBlock;
import com.reg21meme.dross.portal.DrossPortalFrameBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Dross.MODID);

    // Portal area
    public static final RegistryObject<Block> DROSS_PORTAL_FRAME = BLOCKS.register("dross_portal_frame", DrossPortalFrameBlock::new);
    public static final RegistryObject<Block> DROSS_PORTAL = BLOCKS.register("dross_portal", DrossPortalBlock::new);

    // Boss area: the Cinder Colossus's temporary lava pools and fire trail (they clear themselves away; no items)
    public static final RegistryObject<Block> CINDER_LAVA = BLOCKS.register("cinder_lava", CinderLavaBlock::new);
    public static final RegistryObject<Block> CINDER_FIRE = BLOCKS.register("cinder_fire", CinderFireBlock::new);

    // Boss area: the Cinder Colossus trophy (a miniature of the boss in one of five designs; the boss drops one)
    public static final RegistryObject<Block> COLOSSUS_TROPHY = BLOCKS.register("colossus_trophy", ColossusTrophyBlock::new);

    // Boss area: the Rat King trophy (a miniature of him in one of five poses; he gives one to every player who helped)
    public static final RegistryObject<Block> RAT_KING_TROPHY = BLOCKS.register("rat_king_trophy", RatKingTrophyBlock::new);

    // Dimension area: Duskstone, the Dross's base stone (properties in dimension/block/TerrainBlocks)
    public static final RegistryObject<Block> DUSKSTONE = BLOCKS.register("duskstone", TerrainBlocks::duskstone);
    // Dimension area: Duskstone Coal Ore (drops coal)
    public static final RegistryObject<Block> DUSKSTONE_COAL_ORE = BLOCKS.register("duskstone_coal_ore", TerrainBlocks::duskstoneCoalOre);
    // Dimension area: Duskstone Gold Ore (drops gold nuggets like nether gold ore)
    public static final RegistryObject<Block> DUSKSTONE_GOLD_ORE = BLOCKS.register("duskstone_gold_ore", TerrainBlocks::duskstoneGoldOre);
    // Dimension area: Moonsilver Ore (drops raw moonsilver; stone pickaxe or better)
    public static final RegistryObject<Block> MOONSILVER_ORE = BLOCKS.register("moonsilver_ore", TerrainBlocks::moonsilverOre);
    // Dimension area: Stormite Ore (glowing crystal veins, drops stormite shards; iron pickaxe or better)
    public static final RegistryObject<Block> STORMITE_ORE = BLOCKS.register("stormite_ore", TerrainBlocks::stormiteOre);
    // Dimension area: Silverwood Log (an axe strips it)
    public static final RegistryObject<Block> SILVERWOOD_LOG = BLOCKS.register("silverwood_log", TerrainBlocks::silverwoodLog);
    // Dimension area: Silverwood Wood (bark on all sides; an axe strips it)
    public static final RegistryObject<Block> SILVERWOOD_WOOD = BLOCKS.register("silverwood_wood", TerrainBlocks::silverwoodWood);
    // Dimension area: Stripped Silverwood Log
    public static final RegistryObject<Block> STRIPPED_SILVERWOOD_LOG = BLOCKS.register("stripped_silverwood_log", TerrainBlocks::strippedSilverwoodLog);
    // Dimension area: Stripped Silverwood Wood
    public static final RegistryObject<Block> STRIPPED_SILVERWOOD_WOOD = BLOCKS.register("stripped_silverwood_wood", TerrainBlocks::strippedSilverwoodWood);
    // Dimension area: Silverwood Planks
    public static final RegistryObject<Block> SILVERWOOD_PLANKS = BLOCKS.register("silverwood_planks", TerrainBlocks::silverwoodPlanks);
    // Dimension area: Silverwood Leaves (blue; decay like any leaves)
    public static final RegistryObject<Block> SILVERWOOD_LEAVES = BLOCKS.register("silverwood_leaves", TerrainBlocks::silverwoodLeaves);
    // Dimension area: Silverwood Sapling (one grows a silverwood, four in a square a great silverwood)
    public static final RegistryObject<Block> SILVERWOOD_SAPLING = BLOCKS.register("silverwood_sapling", TerrainBlocks::silverwoodSapling);
    // Dimension area: Stormvine (glowing electric blue vine on silverwood trees; never spreads)
    public static final RegistryObject<Block> STORMVINE = BLOCKS.register("stormvine", TerrainBlocks::stormvine);
    // Dimension area: Silvermoss (the Silverwood Forest floor)
    public static final RegistryObject<Block> SILVERMOSS = BLOCKS.register("silvermoss", TerrainBlocks::silvermoss);
    // Dimension area: Glimmer Fern (a silvery fern with blue tips)
    public static final RegistryObject<Block> GLIMMER_FERN = BLOCKS.register("glimmer_fern", TerrainBlocks::glimmerFern);
    // Dimension area: Wispbloom (a small flower with glowing petals)
    public static final RegistryObject<Block> WISPBLOOM = BLOCKS.register("wispbloom", TerrainBlocks::wispbloom);

    // Dimension area, Cinder Wastes (nothing here burns): Ash (soft volcanic ash; doesn't fall)
    public static final RegistryObject<Block> ASH = BLOCKS.register("ash", TerrainBlocks::ash);
    // Dimension area: Ash Layer (1-8 layers like snow; never melts)
    public static final RegistryObject<Block> ASH_LAYER = BLOCKS.register("ash_layer", TerrainBlocks::ashLayer);
    // Dimension area: Charred Log (a burnt dead trunk)
    public static final RegistryObject<Block> CHARRED_LOG = BLOCKS.register("charred_log", TerrainBlocks::charredLog);
    // Dimension area: Emberweed (dry stalks with glowing ember tips)
    public static final RegistryObject<Block> EMBERWEED = BLOCKS.register("emberweed", TerrainBlocks::emberweed);
    // Dimension area: Cinder Vent (a glowing vent that puffs smoke; harmless)
    public static final RegistryObject<Block> CINDER_VENT = BLOCKS.register("cinder_vent", TerrainBlocks::cinderVent);
    // Dimension area, Blightwood: Sporeloam (rotten soil dotted with spores)
    public static final RegistryObject<Block> SPORELOAM = BLOCKS.register("sporeloam", TerrainBlocks::sporeloam);
    // Dimension area: Blightcap Block (a giant decaying mushroom's cap; drops Gloomcaps)
    public static final RegistryObject<Block> BLIGHTCAP_BLOCK = BLOCKS.register("blightcap_block", TerrainBlocks::blightcapBlock);
    // Dimension area: Rotting Blightcap (a decayed cap block)
    public static final RegistryObject<Block> ROTTING_BLIGHTCAP = BLOCKS.register("rotting_blightcap", TerrainBlocks::rottingBlightcap);
    // Dimension area: Blightcap Stem (the giant blightcap's stem)
    public static final RegistryObject<Block> BLIGHTCAP_STEM = BLOCKS.register("blightcap_stem", TerrainBlocks::blightcapStem);
    // Dimension area: Gloomcap (a small glowing mushroom; never spreads)
    public static final RegistryObject<Block> GLOOMCAP = BLOCKS.register("gloomcap", TerrainBlocks::gloomcap);
    // Dimension area: Sporepod (bursts into harmless spores when walked into)
    public static final RegistryObject<Block> SPOREPOD = BLOCKS.register("sporepod", TerrainBlocks::sporepod);
    // Dimension area, Rimefall: Hoarfrost (frozen ground furred with frost)
    public static final RegistryObject<Block> HOARFROST = BLOCKS.register("hoarfrost", TerrainBlocks::hoarfrost);
    // Dimension area: Frostbitten Log (dead wood with rime on one side)
    public static final RegistryObject<Block> FROSTBITTEN_LOG = BLOCKS.register("frostbitten_log", TerrainBlocks::frostbittenLog);
    // Dimension area: Frostbloom (an icy flower with a glowing heart)
    public static final RegistryObject<Block> FROSTBLOOM = BLOCKS.register("frostbloom", TerrainBlocks::frostbloom);
    // Dimension area: Icicle (hangs under a solid block; breaks if that goes)
    public static final RegistryObject<Block> ICICLE = BLOCKS.register("icicle", TerrainBlocks::icicle);
    // Dimension area, the old Nether Scar's blocks (no fire; the biome was removed, the blocks kept): Scarred Netherrack (netherrack with glowing blue cracks)
    public static final RegistryObject<Block> SCARRED_NETHERRACK = BLOCKS.register("scarred_netherrack", TerrainBlocks::scarredNetherrack);
    // Dimension area: Scar Roots (crimson roots with blue tips)
    public static final RegistryObject<Block> SCAR_ROOTS = BLOCKS.register("scar_roots", TerrainBlocks::scarRoots);
    // Dimension area: Soulwick (a flower with a soul flame bloom; never burns)
    public static final RegistryObject<Block> SOULWICK = BLOCKS.register("soulwick", TerrainBlocks::soulwick);
    // Dimension area: Scar Vent (puffs red and blue embers; harmless)
    public static final RegistryObject<Block> SCAR_VENT = BLOCKS.register("scar_vent", TerrainBlocks::scarVent);
    // Dimension area, the Gravecrags: Cragstone (pale weathered mountain stone)
    public static final RegistryObject<Block> CRAGSTONE = BLOCKS.register("cragstone", TerrainBlocks::cragstone);
    // Dimension area: Storm Lichen (glowing blue lichen on cliffs)
    public static final RegistryObject<Block> STORM_LICHEN = BLOCKS.register("storm_lichen", TerrainBlocks::stormLichen);
    // Dimension area: Crag Thistle (a spiky thistle with a pale blue flower)
    public static final RegistryObject<Block> CRAG_THISTLE = BLOCKS.register("crag_thistle", TerrainBlocks::cragThistle);
    // Dimension area, the sewers under Blightwood: Sewer Sludge (murky muck on the sewer floors; slows you, less than soul sand)
    public static final RegistryObject<Block> SEWER_SLUDGE = BLOCKS.register("sewer_sludge", TerrainBlocks::sewerSludge);
    // Dimension area, the Rat King's sewer: Blight Vent (glowing poison-green crack breathing spores, at his grand entrance)
    public static final RegistryObject<Block> BLIGHT_VENT = BLOCKS.register("blight_vent", TerrainBlocks::blightVent);

    // Boss area, the hunter villages: Meat Block (a joint of raw meat: nine of any raw meat; hangs on the hunters' racks)
    public static final RegistryObject<Block> MEAT_BLOCK = BLOCKS.register("meat_block", MeatBlocks::meat);
    // Boss area, the hunter villages: Roasted Meat Block (nine of any cooked meat, or a Meat Block cooked; on the spits)
    public static final RegistryObject<Block> ROASTED_MEAT_BLOCK = BLOCKS.register("roasted_meat_block", MeatBlocks::roasted);

    private ModBlocks() {}
}
