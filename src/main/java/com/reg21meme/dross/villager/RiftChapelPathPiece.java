package com.reg21meme.dross.villager;

import com.reg21meme.dross.registry.ModStructures;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraftforge.common.world.PieceBeardifierModifier;

/**
 * The path from the trader's chapel's door to its village's nearest street ({@link TraderVillages}): a
 * {@value TraderVillages#PATH_WIDTH}-wide strip along a middle line that leaves the door straight out and turns at most
 * once, laid a chunk at a time with the land, right after the chapel (so on the ground its ring left). On every column of
 * it, the land's own top (under any plants, snow, leaves or logs) becomes the village's path block, if it's the kind of
 * ground a village street goes over (grass, dirt, sand, gravel, snow...); a plant or snow on it goes. Over water it's a
 * plank, like a village street's. It never shapes the land itself (no terrain adaptation): it follows it.
 */
public final class RiftChapelPathPiece extends StructurePiece implements PieceBeardifierModifier
{
    /** World generation's flags: tell clients, never react to neighbours. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The piece reaches this far under the chapel's floor and over it (the land along a path never strays further). */
    private static final int BELOW = 24;
    private static final int ABOVE = 32;
    /** Looking for a column's ground, at most this far down from where the search starts. */
    private static final int GROUND_SEARCH = 48;

    /** The middle line's corners (X and Z), from the door's front to the street. */
    private final List<BlockPos> line;
    private final Block pathBlock;
    /** The chapel's floor level: a column's ground is looked for from over the highest its ring could have raised it. */
    private final int floorY;

    RiftChapelPathPiece(RiftChapels.Home home)
    {
        this(home.path(), home.pathBlock(), home.chapel().center().getY());
    }

    private RiftChapelPathPiece(List<BlockPos> line, Block pathBlock, int floorY)
    {
        super(ModStructures.RIFT_CHAPEL_PATH_PIECE.get(), 0, box(line, floorY));
        this.line = List.copyOf(line);
        this.pathBlock = pathBlock;
        this.floorY = floorY;
    }

    public RiftChapelPathPiece(CompoundTag tag)
    {
        super(ModStructures.RIFT_CHAPEL_PATH_PIECE.get(), tag);
        List<BlockPos> read = new ArrayList<>();
        for (long packed : tag.getLongArray("Line"))
        {
            read.add(new BlockPos(ChunkPos.getX(packed), 0, ChunkPos.getZ(packed)));
        }
        this.line = List.copyOf(read);
        Block block = tag.contains("Block", Tag.TAG_STRING) ? BuiltInRegistries.BLOCK.get(new ResourceLocation(tag.getString("Block"))) : Blocks.DIRT_PATH;
        this.pathBlock = block == Blocks.AIR ? Blocks.DIRT_PATH : block;
        this.floorY = tag.getInt("FloorY");
    }

    /** Every column the strip covers. */
    private static BoundingBox box(List<BlockPos> line, int floorY)
    {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long cell : cells(line, TraderVillages.PATH_WIDTH / 2))
        {
            minX = Math.min(minX, ChunkPos.getX(cell));
            maxX = Math.max(maxX, ChunkPos.getX(cell));
            minZ = Math.min(minZ, ChunkPos.getZ(cell));
            maxZ = Math.max(maxZ, ChunkPos.getZ(cell));
        }
        return new BoundingBox(minX, floorY - BELOW, minZ, maxX, floorY + ABOVE, maxZ);
    }

    /**
     * The columns (packed like chunk positions: X and Z) of a path {@code 2 * half + 1} wide along this middle line: at
     * every block of each straight leg, the cross-section across it, and round each corner the square, so it turns
     * cleanly. In order from the door. The planner checks the same columns as are laid.
     */
    static LongLinkedOpenHashSet cells(List<BlockPos> line, int half)
    {
        LongLinkedOpenHashSet out = new LongLinkedOpenHashSet();
        if (line.size() == 1)
        {
            square(out, line.get(0), half);
            return out;
        }
        for (int i = 0; i + 1 < line.size(); i++)
        {
            BlockPos a = line.get(i);
            BlockPos b = line.get(i + 1);
            int dx = Integer.signum(b.getX() - a.getX());
            int dz = Integer.signum(b.getZ() - a.getZ());
            int steps = a.distManhattan(b);
            for (int s = 0; s <= steps; s++)
            {
                int x = a.getX() + dx * s;
                int z = a.getZ() + dz * s;
                for (int o = -half; o <= half; o++)
                {
                    out.add(ChunkPos.asLong(x - dz * o, z + dx * o));
                }
            }
            if (i > 0)
            {
                square(out, a, half);
            }
        }
        return out;
    }

    private static void square(LongLinkedOpenHashSet out, BlockPos at, int half)
    {
        for (int ox = -half; ox <= half; ox++)
        {
            for (int oz = -half; oz <= half; oz++)
            {
                out.add(ChunkPos.asLong(at.getX() + ox, at.getZ() + oz));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        long[] packed = new long[this.line.size()];
        for (int i = 0; i < packed.length; i++)
        {
            packed[i] = ChunkPos.asLong(this.line.get(i).getX(), this.line.get(i).getZ());
        }
        tag.put("Line", new LongArrayTag(packed));
        tag.putString("Block", BuiltInRegistries.BLOCK.getKey(this.pathBlock).toString());
        tag.putInt("FloorY", this.floorY);
    }

    // No terrain adaptation: the path follows the land as it is.
    @Override
    public BoundingBox getBeardifierBox()
    {
        return this.getBoundingBox();
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment()
    {
        return TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta()
    {
        return 0;
    }

    /** The middle line's corners (for the tests). */
    List<BlockPos> line()
    {
        return this.line;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int searchFrom = this.floorY + RiftChapelPiece.RING + 2;
        for (long cell : cells(this.line, TraderVillages.PATH_WIDTH / 2))
        {
            int x = ChunkPos.getX(cell);
            int z = ChunkPos.getZ(cell);
            if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ())
            {
                continue;
            }
            int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, searchFrom);
            this.lay(level, pos, x, top, z);
        }
    }

    /** One column: its ground paved (or a plank on water), anything loose on it cleared. */
    private void lay(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z)
    {
        for (int y = Math.min(top, level.getMaxBuildHeight() - 1); y > top - GROUND_SEARCH && y > level.getMinBuildHeight(); y--)
        {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.isAir())
            {
                continue;
            }
            if (state.is(Blocks.WATER))
            {
                if (level.getBlockState(pos.set(x, y + 1, z)).isAir())
                {
                    level.setBlock(pos.set(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), FLAGS);
                }
                return;
            }
            if (!state.getFluidState().isEmpty())
            {
                return;
            }
            if (!RiftChapelPiece.isGround(state))
            {
                continue;
            }
            if (paveable(state))
            {
                level.setBlock(pos.set(x, y, z), this.pathBlock.defaultBlockState(), FLAGS);
                BlockState over = level.getBlockState(pos.set(x, y + 1, z));
                if (!over.isAir() && over.canBeReplaced() && over.getFluidState().isEmpty())
                {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            return;
        }
    }

    /** Ground a village street is laid over: grass, dirt and its kinds, sand, gravel, snow. Stone and the like stay. */
    private static boolean paveable(BlockState state)
    {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.DIRT_PATH) || state.is(Blocks.CLAY);
    }
}
