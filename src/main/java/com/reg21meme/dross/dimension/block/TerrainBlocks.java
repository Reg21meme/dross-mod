package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.dimension.worldgen.SilverwoodTreeGrower;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
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
}
