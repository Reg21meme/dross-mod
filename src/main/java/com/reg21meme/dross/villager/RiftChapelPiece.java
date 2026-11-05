package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModStructures;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
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
import org.slf4j.Logger;

/**
 * Builds the Rift Chapel ({@link RiftChapelStructure}) a chunk at a time, with the land, and puts its trader in it. Before
 * that, when the land's shape is worked out, Minecraft's terrain adaptation ({@code beard_thin}, through Forge's
 * {@link PieceBeardifierModifier}) has drawn the land to the chapel's floor over its footprint and smoothed it round it.
 * In each chunk, only its own part ({@link com.reg21meme.dross.villager.hut.HutDesign#build} with the chunk as its clip):
 * <ol>
 *   <li>the ring round it ({@value #RING} blocks): each column d blocks out cut or filled to within d of the floor's level,
 *       so the ground steps at most a block at a time back to the land (Minecraft's {@code beard_thin} only pulls the land
 *       towards the floor near the floor's own level: over lower land it can leave a thin lip with a gap under it, and a
 *       drop round it);</li>
 *   <li>the chapel: everything over the floor in the footprint cleared, dips and gaps under it filled, the design drawn;</li>
 *   <li>water or lava in the chunk right beside the footprint, at the ground or a block over it, turned to dirt, so
 *       nothing pours in;</li>
 *   <li>in the chunk holding his spot in the aisle, the trader, his home the chapel (its footprint is his
 *       {@code HutBox}) and his village's key on him ({@link DrossTrader#setVillage}). A chunk is only made once, so a
 *       chapel only ever gets one; and if it were ever made again, the trader it brought would be turned away on his first
 *       tick, since a village gives the trader only once ({@link TraderVillageData}).</li>
 * </ol>
 * Trees grow after it, some into it: the trader fells those once, when the land round his chapel is loaded
 * ({@link DrossTrader}). Saved with its middle, the way its door faces and its village.
 */
public final class RiftChapelPiece extends StructurePiece implements PieceBeardifierModifier
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** World generation's flags: tell clients, never react to neighbours. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The piece reaches this far under the floor (the deepest foundation the design lays, and one more). */
    private static final int BELOW = 9;
    /** Minecraft's terrain adaptation puts the top of the ground this many blocks under the beard box's bottom + delta. */
    private static final int BEARD_GROUND_DELTA = 1;
    /** Water beside the footprint is sealed from its ground up to this many blocks over it. */
    private static final int SEAL_ABOVE = 1;
    /**
     * Round the footprint the land is stepped back to its own height: a column d blocks out is kept within d of the floor's
     * level, out to this many blocks (so the ground round the chapel never steps more than a block a block).
     */
    static final int RING = 6;
    /** A column of the ring is never cut or filled by more than this many blocks (past that, the land's own shape stays). */
    static final int MAX_RING_CHANGE = 8;
    /** New ground this many blocks under its top (or less) is the column's own soil; deeper, stone. */
    private static final int SOIL_DEPTH = 3;
    /** Looking for a column's own ground, at most this far under its highest block (past water, plants and trees). */
    private static final int GROUND_SEARCH = 32;

    /** How long each chunk's part took, and the traders put in: for the log and the tests. */
    private static final AtomicInteger CHUNKS = new AtomicInteger();
    private static final AtomicLong TOTAL_NANOS = new AtomicLong();
    private static final AtomicLong SLOWEST_NANOS = new AtomicLong();
    private static final AtomicInteger TRADERS = new AtomicInteger();

    private final BlockPos center;
    private final Direction facing;
    /** Its village's key (the village's start chunk; the chapel's own for one on its own) and whether it's at a village. */
    private final long village;
    private final boolean inVillage;

    RiftChapelPiece(RiftChapels.Home home)
    {
        super(ModStructures.RIFT_CHAPEL_PIECE.get(), 0, box(home.chapel().center(), home.chapel().facing()));
        this.center = home.chapel().center();
        this.facing = home.chapel().facing();
        this.village = home.village();
        this.inVillage = home.inVillage();
    }

    public RiftChapelPiece(CompoundTag tag)
    {
        super(ModStructures.RIFT_CHAPEL_PIECE.get(), tag);
        this.center = new BlockPos(tag.getInt("CenterX"), tag.getInt("GroundY"), tag.getInt("CenterZ"));
        this.facing = Direction.from2DDataValue(tag.getInt("Facing"));
        this.village = tag.getLong("Village");
        this.inVillage = tag.getBoolean("InVillage");
    }

    /** The footprint and its ring, from the deepest foundation (or fill) up to its top. */
    private static BoundingBox box(BlockPos center, Direction facing)
    {
        BoundingBox foot = TraderHut.DESIGN.footprint(center, facing);
        int below = Math.max(BELOW, RING + MAX_RING_CHANGE);
        return new BoundingBox(foot.minX() - RING, foot.minY() - below, foot.minZ() - RING, foot.maxX() + RING, foot.maxY(), foot.maxZ() + RING);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.putInt("CenterX", this.center.getX());
        tag.putInt("GroundY", this.center.getY());
        tag.putInt("CenterZ", this.center.getZ());
        tag.putInt("Facing", this.facing.get2DDataValue());
        tag.putLong("Village", this.village);
        tag.putBoolean("InVillage", this.inVillage);
    }

    // Minecraft's terrain adaptation: the land is drawn to the floor over the footprint
    @Override
    public BoundingBox getBeardifierBox()
    {
        return TraderHut.DESIGN.footprint(this.center, this.facing);
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment()
    {
        return TerrainAdjustment.BEARD_THIN;
    }

    /** The ground's top block ends up at the box's bottom, the floor's level (the beard fills up to the bottom + delta - 1). */
    @Override
    public int getGroundLevelDelta()
    {
        return BEARD_GROUND_DELTA;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot)
    {
        long start = System.nanoTime();
        BoundingBox foot = TraderHut.DESIGN.footprint(this.center, this.facing);
        this.ring(level, box, foot);
        if (box.intersects(foot))
        {
            TraderHut.DESIGN.build(level, box, this.center, this.facing);
            this.seal(level, box, foot);
        }
        BlockPos spot = TraderHut.DESIGN.traderSpot(this.center, this.facing);
        if (box.isInside(spot))
        {
            this.spawnTrader(level, spot, foot);
        }
        long took = System.nanoTime() - start;
        int done = CHUNKS.incrementAndGet();
        TOTAL_NANOS.addAndGet(took);
        if (took > SLOWEST_NANOS.getAndAccumulate(took, Math::max))
        {
            LOGGER.info("Rift Chapel at {}: built its part in chunk {}, {} in {} ms (the slowest yet; {} chapel chunks built)",
                    this.center.toShortString(), chunk.x, chunk.z, took / 1_000_000L, done);
        }
    }

    /**
     * The ring in the chunk's part: each column d blocks out (1 to {@value #RING}) cut or filled to within d of the floor's
     * level (by at most {@value #MAX_RING_CHANGE}), from its own ground, keeping its own top block and soil.
     */
    private void ring(WorldGenLevel level, BoundingBox box, BoundingBox foot)
    {
        int groundY = foot.minY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(box.minX(), foot.minX() - RING); x <= Math.min(box.maxX(), foot.maxX() + RING); x++)
        {
            for (int z = Math.max(box.minZ(), foot.minZ() - RING); z <= Math.min(box.maxZ(), foot.maxZ() + RING); z++)
            {
                int d = Math.max(Math.max(foot.minX() - x, x - foot.maxX()), Math.max(foot.minZ() - z, z - foot.maxZ()));
                if (d <= 0)
                {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int natural = groundUnder(level, pos, x, top, z);
                int target = Mth.clamp(Mth.clamp(natural, groundY - d, groundY + d), natural - MAX_RING_CHANGE, natural + MAX_RING_CHANGE);
                if (target != natural)
                {
                    this.column(level, box, pos, x, z, top, natural, target);
                }
            }
        }
    }

    /** The land's own top in a column: under any water, plants, trees or snow; {@code top} itself if none is found. */
    static int groundUnder(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z)
    {
        for (int y = top; y > top - GROUND_SEARCH && y > level.getMinBuildHeight(); y--)
        {
            if (isGround(level.getBlockState(pos.set(x, y, z))))
            {
                return y;
            }
        }
        return top;
    }

    static boolean isGround(BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && state.blocksMotion()
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
    }

    /** One ring column cut or filled to {@code target}, its own top block on it and its own soil under that. */
    private void column(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int x, int z, int top, int natural, int target)
    {
        BlockState topBlock = level.getBlockState(pos.set(x, natural, z));
        if (!isGround(topBlock))
        {
            topBlock = Blocks.GRASS_BLOCK.defaultBlockState();
        }
        BlockState under = level.getBlockState(pos.set(x, natural - 1, z));
        if (!isGround(under))
        {
            under = Blocks.DIRT.defaultBlockState();
        }
        BlockState deep = Blocks.STONE.defaultBlockState();
        // Over the new top: where it's cut, everything goes (land, water, plants); where it's filled, only water and loose plants.
        for (int y = Math.max(top, target + 1); y > target; y--)
        {
            BlockState there = level.getBlockState(pos.set(x, y, z));
            if (!there.isAir() && (target < natural || !there.getFluidState().isEmpty() || there.canBeReplaced()))
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            }
            sealBeside(level, box, pos);
        }
        if (target > natural)
        {
            for (int y = natural; y < target; y++)
            {
                level.setBlock(pos.set(x, y, z), target - y <= SOIL_DEPTH ? under : deep, FLAGS);
            }
        }
        else
        {
            for (int y = target - 1; y >= target - (SOIL_DEPTH - 1); y--)
            {
                if (level.getBlockState(pos.set(x, y, z)).is(Blocks.STONE))
                {
                    level.setBlock(pos, under, FLAGS);
                }
            }
        }
        level.setBlock(pos.set(x, target, z), topBlock, FLAGS);
    }

    /** Water or lava in the chunk beside a cleared cell becomes dirt, so nothing pours into the cut. */
    private static void sealBeside(WorldGenLevel level, BoundingBox box, BlockPos cleared)
    {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (Direction dir : Direction.Plane.HORIZONTAL)
        {
            side.setWithOffset(cleared, dir);
            if (box.isInside(side) && !level.getFluidState(side).isEmpty())
            {
                level.setBlock(side, Blocks.DIRT.defaultBlockState(), FLAGS);
            }
        }
    }

    /** Water or lava in the chunk right beside the footprint, from its ground to {@value #SEAL_ABOVE} over it, becomes dirt. */
    private void seal(WorldGenLevel level, BoundingBox box, BoundingBox foot)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = foot.minX() - 1; x <= foot.maxX() + 1; x++)
        {
            for (int z = foot.minZ() - 1; z <= foot.maxZ() + 1; z++)
            {
                boolean beside = x < foot.minX() || x > foot.maxX() || z < foot.minZ() || z > foot.maxZ();
                if (!beside || !box.isInside(x, box.minY(), z))
                {
                    continue;
                }
                for (int y = foot.minY() - 1; y <= foot.minY() + SEAL_ABOVE; y++)
                {
                    if (!level.getFluidState(pos.set(x, y, z)).isEmpty())
                    {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    /** The chapel's trader, on his spot facing the door, his home the chapel. */
    private void spawnTrader(WorldGenLevel level, BlockPos spot, BoundingBox foot)
    {
        DrossTrader trader = ModEntities.TRADER.get().create(level.getLevel());
        if (trader == null)
        {
            LOGGER.error("[Dross] Couldn't create the trader of the Rift Chapel at {}", this.center.toShortString());
            return;
        }
        trader.setHome(spot, foot);
        trader.setChapel(this.center, this.facing);
        trader.setVillage(this.village);
        trader.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, this.facing.toYRot(), 0.0F);
        trader.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null, null);
        trader.setPersistenceRequired();
        level.addFreshEntityWithPassengers(trader);
        TRADERS.incrementAndGet();
        LOGGER.info("[Dross] The Dross trader came with the land, in the Rift Chapel at {}, {}, {} ({})", spot.getX(), spot.getY(), spot.getZ(),
                this.inVillage ? "at the edge of the village in chunk " + ChunkPos.getX(this.village) + ", " + ChunkPos.getZ(this.village) : "on its own");
    }

    /** {chunks built, total nanoseconds, the slowest one chunk's nanoseconds, traders put in} since the server started. */
    static long[] timing()
    {
        return new long[] {CHUNKS.get(), TOTAL_NANOS.get(), SLOWEST_NANOS.get(), TRADERS.get()};
    }
}
