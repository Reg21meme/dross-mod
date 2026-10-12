package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.dimension.worldgen.SilverwoodTreeGrower;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Makes the Dross's terrain blocks (registered in {@code registry/ModBlocks}). Every block's properties (hardness,
 * sound, light, tool needed...) are here, as labeled constants where a number needs explaining. Which tool a block
 * needs is a block tag ({@code data/minecraft/tags/blocks/mineable/} and {@code needs_*_tool}); what it drops is its
 * loot table ({@code data/dross/loot_tables/blocks/}).
 */
public final class TerrainBlocks
{
    // ---------------------------------------------------------------------------------------------
    // Numbers (vanilla's where a vanilla block plays the same part)
    // ---------------------------------------------------------------------------------------------

    /** Duskstone: as hard as stone (time to mine 1.5, blast resistance 6). */
    private static final float DUSKSTONE_HARDNESS = 1.5F;
    private static final float DUSKSTONE_RESISTANCE = 6.0F;

    /** The ores: as hard as vanilla's stone ores (3, 3). */
    private static final float ORE_HARDNESS = 3.0F;
    private static final float ORE_RESISTANCE = 3.0F;

    /** XP from mining each ore (silk touch gives none): coal 0-2 and gold 0-1 like vanilla's coal and nether gold ores. */
    private static final UniformInt COAL_ORE_XP = UniformInt.of(0, 2);
    private static final UniformInt GOLD_ORE_XP = UniformInt.of(0, 1);
    /** Stormite gives 2-5 like lapis; moonsilver gives none when mined, like iron (its XP comes from smelting). */
    private static final UniformInt STORMITE_ORE_XP = UniformInt.of(2, 5);

    /** Light levels (0-15; a torch is 14). Stormite's crystals, Stormvine and the Wispbloom glow in the dark. */
    private static final int STORMITE_ORE_LIGHT = 4;
    private static final int STORMVINE_LIGHT = 6;
    private static final int WISPBLOOM_LIGHT = 5;

    /** Wood: logs 2 (like oak logs), planks 2 / 3 (like oak planks). */
    private static final float LOG_HARDNESS = 2.0F;
    private static final float PLANKS_HARDNESS = 2.0F;
    private static final float PLANKS_RESISTANCE = 3.0F;

    /** Leaves 0.2, vines 0.2, silvermoss 0.6 (like grass). */
    private static final float LEAVES_HARDNESS = 0.2F;
    private static final float STORMVINE_HARDNESS = 0.2F;
    private static final float SILVERMOSS_HARDNESS = 0.6F;

    /** Eating suspicious stew made with a Wispbloom gives Night Vision for this many seconds (like a poppy). */
    private static final int WISPBLOOM_STEW_SECONDS = 5;

    // --- The other biomes' blocks (Stage 2) ---

    /** Ash: soft like sand (0.5); Ash Layer as thin as snow layers (0.1). */
    private static final float ASH_HARDNESS = 0.5F;
    private static final float ASH_LAYER_HARDNESS = 0.1F;
    /** Cinder Vent: like basalt (1.25, blast resistance 4.2). */
    private static final float CINDER_VENT_HARDNESS = 1.25F;
    private static final float CINDER_VENT_RESISTANCE = 4.2F;
    /** Sporeloam and Hoarfrost: like grass and mycelium (0.6). */
    private static final float GROUND_COVER_HARDNESS = 0.6F;
    /** The giant blightcap's blocks: like vanilla mushroom blocks (0.2). */
    private static final float MUSHROOM_BLOCK_HARDNESS = 0.2F;
    /** Icicle: as fragile as glass (0.3). */
    private static final float ICICLE_HARDNESS = 0.3F;
    /** Scarred Netherrack and the Scar Vent: like netherrack (0.4). */
    private static final float NETHERRACK_HARDNESS = 0.4F;
    /** Cragstone: as hard as stone (1.5, blast resistance 6), like Duskstone. */
    private static final float CRAGSTONE_HARDNESS = 1.5F;
    private static final float CRAGSTONE_RESISTANCE = 6.0F;
    /** Storm Lichen: like glow lichen (0.2). */
    private static final float STORM_LICHEN_HARDNESS = 0.2F;

    /** Light levels of the biome blocks that glow (0-15; a torch is 14). */
    private static final int EMBERWEED_LIGHT = 4;
    private static final int CINDER_VENT_LIGHT = 3;
    private static final int GLOOMCAP_LIGHT = 6;
    private static final int FROSTBLOOM_LIGHT = 3;
    /**
     * Scarred Netherrack's cracks glow (full-bright in its model) but light nothing round them: it's the Nether Scar's main
     * ground, and monsters in the Dross only spawn in complete darkness, so light here would keep them out of most of the
     * Scar (it was 3 until Stage 6 of Terrain v1).
     */
    private static final int SCARRED_NETHERRACK_LIGHT = 0;
    private static final int SOULWICK_LIGHT = 10;
    private static final int SCAR_VENT_LIGHT = 4;
    /** Storm Lichen gives this light only where it covers at least one side (like glow lichen's 7). */
    private static final int STORM_LICHEN_LIGHT = 7;

    /** 1 in this many plants that bone meal sprouts on Sporeloam is a Sporepod; the rest are Gloomcaps. */
    private static final int SPOREPOD_ONE_IN = 6;

    /** Suspicious stew: Frostbloom gives Fire Resistance (frost against flame), like an allium, for this many seconds. */
    private static final int FROSTBLOOM_STEW_SECONDS = 4;
    /** Suspicious stew: Soulwick gives Slow Falling (the soul flame makes you light), for this many seconds. */
    private static final int SOULWICK_STEW_SECONDS = 5;
    /** Suspicious stew: Crag Thistle gives Resistance (its spines guard you), for this many seconds. */
    private static final int CRAG_THISTLE_STEW_SECONDS = 4;

    private TerrainBlocks() {}

    // ---------------------------------------------------------------------------------------------
    // Stone and ores
    // ---------------------------------------------------------------------------------------------

    /** Duskstone: the Dross's dark slate-blue base stone. Needs a pickaxe; drops itself. */
    public static Block duskstone()
    {
        return new Block(stone().strength(DUSKSTONE_HARDNESS, DUSKSTONE_RESISTANCE));
    }

    /** Duskstone Coal Ore: drops coal like coal ore (any pickaxe). */
    public static Block duskstoneCoalOre()
    {
        return new DropExperienceBlock(stone().strength(ORE_HARDNESS, ORE_RESISTANCE), COAL_ORE_XP);
    }

    /** Duskstone Gold Ore: drops 2-6 gold nuggets like nether gold ore (any pickaxe). */
    public static Block duskstoneGoldOre()
    {
        return new DropExperienceBlock(stone().strength(ORE_HARDNESS, ORE_RESISTANCE), GOLD_ORE_XP);
    }

    /** Moonsilver Ore: drops raw moonsilver like iron ore (stone pickaxe or better: tag needs_stone_tool). */
    public static Block moonsilverOre()
    {
        return new DropExperienceBlock(stone().strength(ORE_HARDNESS, ORE_RESISTANCE));
    }

    /** Stormite Ore: glowing blue crystal veins, drops 1-2 Stormite Shards (iron pickaxe or better: needs_iron_tool). */
    public static Block stormiteOre()
    {
        return new DropExperienceBlock(stone().strength(ORE_HARDNESS, ORE_RESISTANCE).lightLevel(state -> STORMITE_ORE_LIGHT),
                STORMITE_ORE_XP);
    }

    private static BlockBehaviour.Properties stone()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE);
    }

    // ---------------------------------------------------------------------------------------------
    // Silverwood
    // ---------------------------------------------------------------------------------------------

    /** Silverwood Log: pale bark; an axe strips it into a Stripped Silverwood Log. */
    public static Block silverwoodLog()
    {
        return new SilverwoodLogBlock(log(MapColor.WOOL, MapColor.CLAY), ModBlocks.STRIPPED_SILVERWOOD_LOG);
    }

    /** Silverwood Wood (bark on all six sides); an axe strips it into Stripped Silverwood Wood. */
    public static Block silverwoodWood()
    {
        return new SilverwoodLogBlock(log(MapColor.CLAY, MapColor.CLAY), ModBlocks.STRIPPED_SILVERWOOD_WOOD);
    }

    /** Stripped Silverwood Log: the luminous pale wood under the bark. */
    public static Block strippedSilverwoodLog()
    {
        return new SilverwoodLogBlock(log(MapColor.WOOL, MapColor.WOOL), null);
    }

    /** Stripped Silverwood Wood. */
    public static Block strippedSilverwoodWood()
    {
        return new SilverwoodLogBlock(log(MapColor.WOOL, MapColor.WOOL), null);
    }

    /** Silverwood Planks: pale silver-grey boards that burn like oak planks. */
    public static Block silverwoodPlanks()
    {
        return new FlammableBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOL)
                .instrument(NoteBlockInstrument.BASS)
                .strength(PLANKS_HARDNESS, PLANKS_RESISTANCE)
                .sound(SoundType.WOOD)
                .ignitedByLava(),
                Flammability.PLANKS_SPREAD, Flammability.PLANKS_BURN);
    }

    /** Silverwood Leaves: deep blue, with gaps you can see through; they decay like any leaves. */
    public static Block silverwoodLeaves()
    {
        return new SilverwoodLeavesBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .strength(LEAVES_HARDNESS)
                .randomTicks()
                .sound(SoundType.GRASS)
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> type == EntityType.OCELOT || type == EntityType.PARROT)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((state, level, pos) -> false));
    }

    /** Silverwood Sapling: grows a silverwood (or a tall one); four in a 2x2 square grow a great silverwood. */
    public static Block silverwoodSapling()
    {
        return new SaplingBlock(new SilverwoodTreeGrower(), BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLUE)
                .noCollission()
                .randomTicks()
                .instabreak()
                .sound(SoundType.GRASS)
                .pushReaction(PushReaction.DESTROY));
    }

    /** Logs and wood: {@code topColor} on the map for a log standing up, {@code sideColor} for one lying down. */
    private static BlockBehaviour.Properties log(MapColor topColor, MapColor sideColor)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? topColor : sideColor)
                .instrument(NoteBlockInstrument.BASS)
                .strength(LOG_HARDNESS)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    // ---------------------------------------------------------------------------------------------
    // Stormvine, Silvermoss and the plants
    // ---------------------------------------------------------------------------------------------

    /** Stormvine: a glowing electric blue vine on silverwood trees. Never spreads; only shears pick it up. */
    public static Block stormvine()
    {
        return new StormvineBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.LAPIS)
                .replaceable()
                .noCollission()
                .strength(STORMVINE_HARDNESS)
                .sound(SoundType.VINE)
                .lightLevel(state -> STORMVINE_LIGHT)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }

    /** Silvermoss: the Silverwood Forest floor (drops itself; shovel or hoe). */
    public static Block silvermoss()
    {
        return new SilvermossBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(SILVERMOSS_HARDNESS)
                .sound(SoundType.MOSS));
    }

    /** Glimmer Fern: a silvery fern with blue tips (like grass: replaceable, walk-through, shears only). */
    public static Block glimmerFern()
    {
        return new GlimmerFernBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                .replaceable()
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XYZ)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }

    /** Wispbloom: a small flower with glowing pale-blue petals. */
    public static Block wispbloom()
    {
        return new WispbloomBlock(() -> MobEffects.NIGHT_VISION, WISPBLOOM_STEW_SECONDS, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .lightLevel(state -> WISPBLOOM_LIGHT)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }

    // ---------------------------------------------------------------------------------------------
    // Cinder Wastes (volcanic). Nothing here burns: lava pools lie next to it.
    // ---------------------------------------------------------------------------------------------

    /** Ash: soft grey-black volcanic ash; like sand to dig (shovel), but it doesn't fall. Drops itself. */
    public static Block ash()
    {
        return new Block(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.SNARE)
                .strength(ASH_HARDNESS)
                .sound(SoundType.SAND));
    }

    /** Ash Layer: 1-8 thin layers of ash, exactly like snow layers, but it never melts. One Ash Layer per layer. */
    public static Block ashLayer()
    {
        return new AshLayerBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .replaceable()
                .forceSolidOff()
                .strength(ASH_LAYER_HARDNESS)
                .sound(SoundType.SAND)
                .isViewBlocking((state, level, pos) -> state.getValue(SnowLayerBlock.LAYERS) >= SnowLayerBlock.MAX_HEIGHT)
                .pushReaction(PushReaction.DESTROY));
    }

    /** Charred Log: the burnt trunk of a dead tree (axe). Already burnt out, so fire and lava never take it. */
    public static Block charredLog()
    {
        return new DeadLogBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASS)
                .strength(LOG_HARDNESS)
                .sound(SoundType.WOOD), 0, 0);
    }

    /** Emberweed: dry dark stalks with glowing ember tips (like dry grass: replaceable, walk-through, shears only). */
    public static Block emberweed()
    {
        return new EmberweedBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .replaceable()
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .lightLevel(state -> EMBERWEED_LIGHT)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY));
    }

    /** Cinder Vent: cracked basalt-like rock with a glowing vent mouth that puffs smoke (pickaxe). Harmless. */
    public static Block cinderVent()
    {
        return new CinderVentBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(CINDER_VENT_HARDNESS, CINDER_VENT_RESISTANCE)
                .sound(SoundType.BASALT)
                .lightLevel(state -> CINDER_VENT_LIGHT));
    }

    // ---------------------------------------------------------------------------------------------
    // Blightwood (rotten and fungal)
    // ---------------------------------------------------------------------------------------------

    /** Sporeloam: dark rotten soil dotted with pale spores (shovel). Bone meal sprouts Gloomcaps and Sporepods. */
    public static Block sporeloam()
    {
        return new SproutingSoilBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_GREEN)
                .strength(GROUND_COVER_HARDNESS)
                .sound(SoundType.NYLIUM),
                false, ModBlocks.GLOOMCAP, ModBlocks.SPOREPOD, SPOREPOD_ONE_IN);
    }

    /** Blightcap Block: the cap of a giant decaying mushroom (axe); drops 0-2 Gloomcaps like vanilla mushroom blocks. */
    public static Block blightcapBlock()
    {
        return new BlightcapBlock(mushroomBlock(MapColor.TERRACOTTA_CYAN), true);
    }

    /** Rotting Blightcap: a darker, decayed, hole-pocked cap block; drops nothing without Silk Touch. */
    public static Block rottingBlightcap()
    {
        return new BlightcapBlock(mushroomBlock(MapColor.COLOR_GRAY), true);
    }

    /** Blightcap Stem: the giant blightcap's pale fibrous stem; drops nothing without Silk Touch (like mushroom stems). */
    public static Block blightcapStem()
    {
        return new BlightcapBlock(mushroomBlock(MapColor.WOOL), false);
    }

    private static BlockBehaviour.Properties mushroomBlock(MapColor color)
    {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASS)
                .strength(MUSHROOM_BLOCK_HARDNESS)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    /** Gloomcap: a small glowing poison-green mushroom. Never spreads or grows huge. */
    public static Block gloomcap()
    {
        return new GloomcapBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.DIAMOND)
                .noCollission()
                .instabreak()
                .sound(SoundType.FUNGUS)
                .lightLevel(state -> GLOOMCAP_LIGHT)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY));
    }

    /** Sporepod: a fungal pod that bursts into harmless spores when something walks into it. */
    public static Block sporepod()
    {
        return new SporepodBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_LIGHT_GREEN)
                .noCollission()
                .instabreak()
                .sound(SoundType.FUNGUS)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY));
    }

    // ---------------------------------------------------------------------------------------------
    // Rimefall (frozen)
    // ---------------------------------------------------------------------------------------------

    /** Hoarfrost: frozen ground furred with frost (shovel); crunches like snow but isn't slippery. */
    public static Block hoarfrost()
    {
        return new SproutingSoilBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.SNOW)
                .strength(GROUND_COVER_HARDNESS)
                .sound(SoundType.SNOW),
                false, null, null, 1);
    }

    /** Frostbitten Log: grey dead wood with white rime on one side (axe); burns, but slowly. */
    public static Block frostbittenLog()
    {
        return new DeadLogBlock(BlockBehaviour.Properties.of()
                .mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? MapColor.COLOR_GRAY : MapColor.COLOR_LIGHT_GRAY)
                .instrument(NoteBlockInstrument.BASS)
                .strength(LOG_HARDNESS)
                .sound(SoundType.WOOD)
                .ignitedByLava(),
                Flammability.FROSTBITTEN_LOG_SPREAD, Flammability.FROSTBITTEN_LOG_BURN);
    }

    /** Frostbloom: a pale icy-blue flower with a softly glowing heart; crafts into light blue dye. */
    public static Block frostbloom()
    {
        return new DrossFlowerBlock(() -> MobEffects.FIRE_RESISTANCE, FROSTBLOOM_STEW_SECONDS, PlantGround.FROSTBLOOM, true,
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_LIGHT_BLUE)
                        .noCollission()
                        .instabreak()
                        .sound(SoundType.GRASS)
                        .lightLevel(state -> FROSTBLOOM_LIGHT)
                        .offsetType(BlockBehaviour.OffsetType.XZ)
                        .ignitedByLava()
                        .pushReaction(PushReaction.DESTROY));
    }

    /** Icicle: a see-through spike of ice hanging under a solid block; breaks if that goes. Silk Touch to keep it. */
    public static Block icicle()
    {
        return new IcicleBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.ICE)
                .noCollission()
                .noOcclusion()
                .strength(ICICLE_HARDNESS)
                .sound(SoundType.GLASS)
                .isValidSpawn((state, level, pos, type) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .pushReaction(PushReaction.DESTROY));
    }

    // ---------------------------------------------------------------------------------------------
    // Nether Scar (the Nether bleeding through). No fire anywhere: nothing here burns or keeps a fire going.
    // ---------------------------------------------------------------------------------------------

    /** Scarred Netherrack: netherrack split by glowing electric blue cracks (pickaxe). Not a fire source, not nylium. */
    public static Block scarredNetherrack()
    {
        return new Block(netherrack().lightLevel(state -> SCARRED_NETHERRACK_LIGHT));
    }

    /** Scar Roots: twisted crimson roots with electric blue tips (like crimson roots). */
    public static Block scarRoots()
    {
        return new ScarRootsBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.NETHER)
                .replaceable()
                .noCollission()
                .instabreak()
                .sound(SoundType.ROOTS)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY));
    }

    /** Soulwick: a flower whose bloom is a blue soul flame. A flower, not fire: it never burns or hurts. */
    public static Block soulwick()
    {
        return new SoulwickBlock(() -> MobEffects.SLOW_FALLING, SOULWICK_STEW_SECONDS, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .noCollission()
                .instabreak()
                .sound(SoundType.GRASS)
                .lightLevel(state -> SOULWICK_LIGHT)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY));
    }

    /** Scar Vent: a netherrack crack that puffs red and electric blue embers (pickaxe). Harmless. */
    public static Block scarVent()
    {
        return new ScarVentBlock(netherrack().lightLevel(state -> SCAR_VENT_LIGHT));
    }

    private static BlockBehaviour.Properties netherrack()
    {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NETHER)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops()
                .strength(NETHERRACK_HARDNESS)
                .sound(SoundType.NETHERRACK);
    }

    // ---------------------------------------------------------------------------------------------
    // The Gravecrags (mountains)
    // ---------------------------------------------------------------------------------------------

    /** Cragstone: pale grey weathered mountain stone with dark streaks. Works as stone, like Duskstone. */
    public static Block cragstone()
    {
        return new Block(stone().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(CRAGSTONE_HARDNESS, CRAGSTONE_RESISTANCE));
    }

    /** Storm Lichen: an electric blue lichen glowing on cliff faces (like glow lichen; shears only). */
    public static Block stormLichen()
    {
        return new StormLichenBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.LAPIS)
                .replaceable()
                .noCollission()
                .strength(STORM_LICHEN_HARDNESS)
                .sound(SoundType.GLOW_LICHEN)
                .lightLevel(GlowLichenBlock.emission(STORM_LICHEN_LIGHT))
                .ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }

    /** Crag Thistle: a spiky grey-violet mountain thistle with a pale blue flower head; crafts into light blue dye. */
    public static Block cragThistle()
    {
        return new DrossFlowerBlock(() -> MobEffects.DAMAGE_RESISTANCE, CRAG_THISTLE_STEW_SECONDS, PlantGround.CRAG_THISTLE, true,
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.COLOR_PURPLE)
                        .noCollission()
                        .instabreak()
                        .sound(SoundType.GRASS)
                        .offsetType(BlockBehaviour.OffsetType.XZ)
                        .ignitedByLava()
                        .pushReaction(PushReaction.DESTROY));
    }
}
