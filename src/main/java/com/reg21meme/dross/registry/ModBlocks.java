package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderFireBlock;
import com.reg21meme.dross.boss.golem.CinderLavaBlock;
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

    private ModBlocks() {}
}
