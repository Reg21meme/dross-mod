package com.reg21meme.dross.dimension.rimefall;

import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * Builds a {@link RimefallLake}, a chunk at a time (each column from the lake's plan and that column's own ground, so the
 * chunks agree whatever order they're made in):
 * <ul>
 *   <li><b>In the lake</b>: the ground above the ice's level is cut away; a sheet of ice at {@link RimefallLake#iceY()}
 *       (or, in a hole, open water); water under it down to the bed (deeper in the middle), the bed gravel with patches of
 *       clay; a dip deeper than the bed is filled up to it.</li>
 *   <li><b>The shore</b> ({@link RimefallTuning#SHORE_WIDTH} round it): higher ground is lowered towards the ice's level,
 *       fully at the water's edge, not at all at the shore's outer side (a smooth blend, no cliff); lower ground next to the
 *       water is raised to the ice's level ({@link RimefallTuning#SHORE_FLAT} blocks out), then in steps of at most one
 *       block, so the water never spills out. A column keeps its own top blocks (snow, Hoarfrost, packed ice).</li>
 * </ul>
 * An open hole's top is water in an invisible light block of level 0 ({@code minecraft:light}, waterlogged): it looks and
 * swims like water, but Minecraft never freezes it over (it only freezes plain water), so the holes stay open.
 */
public final class RimefallLakePiece extends StructurePiece
{
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState ICE = Blocks.ICE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState OPEN_WATER = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 0)
            .setValue(BlockStateProperties.WATERLOGGED, true);
    private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
    private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();

    private final RimefallLake lake;

    RimefallLakePiece(RimefallLake lake)
    {
        super(ModStructures.RIMEFALL_LAKE_PIECE.get(), 0, lake.box());
        this.lake = lake;
    }

    public RimefallLakePiece(CompoundTag tag)
    {
        super(ModStructures.RIMEFALL_LAKE_PIECE.get(), tag);
        this.lake = RimefallLake.load(tag.getCompound("Lake"));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.put("Lake", this.lake.save());
    }

    public RimefallLake lake()
    {
        return this.lake;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox box,
                            ChunkPos chunk, BlockPos pivot)
    {
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                double d = this.lake.outside(x, z);
                if (d > RimefallTuning.SHORE_WIDTH)
                {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
                BlockState topState = level.getBlockState(at.set(x, top, z));
                BlockState under = level.getBlockState(at.set(x, top - 1, z));
                if (under.isAir() || !under.getFluidState().isEmpty())
                {
                    under = topState;
                }
                if (d < 0.0D)
                {
                    this.buildLakeColumn(level, at, x, z, top, under);
                }
                else
                {
                    this.buildShoreColumn(level, at, x, z, top, d, topState, under);
                }
            }
        }
    }

    private void buildLakeColumn(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int z, int top, BlockState under)
    {
        int ice = this.lake.iceY();
        int bed = this.lake.bedY(x, z);
        for (int y = ice + 1; y <= Math.max(top, ice) + 1; y++)
        {
            level.setBlock(at.set(x, y, z), AIR, Block.UPDATE_CLIENTS);
        }
        level.setBlock(at.set(x, ice, z), this.lake.isHole(x, z) ? OPEN_WATER : ICE, Block.UPDATE_CLIENTS);
        for (int y = bed + 1; y < ice; y++)
        {
            level.setBlock(at.set(x, y, z), WATER, Block.UPDATE_CLIENTS);
        }
        level.setBlock(at.set(x, bed, z), bedBlock(x, z), Block.UPDATE_CLIENTS);
        for (int y = top + 1; y < bed; y++)
        {
            level.setBlock(at.set(x, y, z), under, Block.UPDATE_CLIENTS);
        }
    }

    private void buildShoreColumn(WorldGenLevel level, BlockPos.MutableBlockPos at, int x, int z, int top, double d, BlockState topState,
                                  BlockState under)
    {
        int target = shoreTop(this.lake.iceY(), top, d);
        if (target == top)
        {
            return;
        }
        if (target < top)
        {
            for (int y = target + 1; y <= top + 1; y++)
            {
                level.setBlock(at.set(x, y, z), AIR, Block.UPDATE_CLIENTS);
            }
            level.setBlock(at.set(x, target - 1, z), under, Block.UPDATE_CLIENTS);
        }
        else
        {
            for (int y = top; y < target; y++)
            {
                level.setBlock(at.set(x, y, z), under, Block.UPDATE_CLIENTS);
            }
        }
        level.setBlock(at.set(x, target, z), topState, Block.UPDATE_CLIENTS);
    }

    /**
     * A shore column's new top block: ground above the ice is lowered towards it (all the way at the water's edge, not at
     * all {@link RimefallTuning#SHORE_WIDTH} out, smoothly), and ground below it is raised to it near the water, then in
     * steps of at most one block.
     */
    static int shoreTop(int ice, int top, double d)
    {
        int target = top;
        if (top > ice)
        {
            double t = Mth.clamp(d / RimefallTuning.SHORE_WIDTH, 0.0D, 1.0D);
            double keep = t * t * (3.0D - 2.0D * t);
            target = ice + (int) Math.round((top - ice) * keep);
        }
        int wall = ice - (int) Math.floor(Math.max(0.0D, d - RimefallTuning.SHORE_FLAT));
        return Math.max(target, wall);
    }

    /** The lake bed: gravel, with patches of clay (fixed by the column, 4 x 4 blocks a patch). */
    private static BlockState bedBlock(int x, int z)
    {
        long h = (long) (x >> 2) * 3129871L ^ (long) (z >> 2) * 116129781L;
        h = h * h * 42317861L + h * 11L;
        return ((h >>> 16) & 0xFFFF) < RimefallTuning.BED_CLAY_SHARE * 65536.0D ? CLAY : GRAVEL;
    }
}
