package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModStructures;
import com.reg21meme.dross.world.shrine.GroundFit;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.world.PieceBeardifierModifier;
import org.slf4j.Logger;

/**
 * Builds the portal site ({@link PortalSiteStructure}) a chunk at a time, with the land, each column from its own ground,
 * so the chunks agree whatever order they're made in. Before that, when the land's shape is worked out, Minecraft's
 * terrain adaptation ({@code beard_thin}, through Forge's {@link PieceBeardifierModifier}) has already drawn the land
 * towards the cathedral's floor over its footprint and up to 12 blocks round it. In each chunk, only its own part:
 * <ol>
 *   <li><b>The ground</b>: under the footprint cut or filled to the ground level (boulders, water and lava over it
 *       cleared; dips and caves just under it filled, at most {@value SiteRules#MAX_DIP_FILL} blocks down); round it a
 *       staircase back to the land, a column d blocks out kept within d of the ground level, out to
 *       {@value SiteRules#BLEND_MARGIN} blocks. Each column keeps its own top block and the soil under it; deep new ground
 *       is the Overworld's stone; water or lava beside a cut is walled in.</li>
 *   <li><b>The Fallen Cathedral's</b> template, cut to the chunk; its blocks as saved (a known shape: nothing reacts to a
 *       neighbour not built yet), its unlit frame among them.</li>
 * </ol>
 * Trees, flowers and grass come later in the chunk's decoration, and some grow on it; {@link SiteTidy} fells the ones that
 * touch it once its land is loaded. Saved with its site column and ground level.
 */
public final class PortalSitePiece extends StructurePiece implements PieceBeardifierModifier
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** World generation's flags: tell clients, never react to neighbours (the jigsaw structures' way). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** New ground this many blocks under the top (or less) is the column's own soil; deeper, the Overworld's stone. */
    private static final int SOIL_DEPTH = 3;
    /** The piece reaches this far over the cathedral's top. */
    private static final int ABOVE = 4;
    /** Looking for a column's own ground, at most this far under its highest block (past boulders, water and plants). */
    private static final int GROUND_SEARCH = 40;
    /** Minecraft's terrain adaptation puts the top of the ground this many blocks under the beard box's bottom + delta. */
    private static final int BEARD_GROUND_DELTA = 1;

    /** How long each chunk's part took: for the log and the tests. */
    private static final AtomicInteger CHUNKS = new AtomicInteger();
    private static final AtomicLong TOTAL_NANOS = new AtomicLong();
    private static final AtomicLong SLOWEST_NANOS = new AtomicLong();
    /** How much of the ground the piece itself had to change after Minecraft's beard: columns, blocks of height, the most. */
    private static final AtomicInteger COLUMNS_CHANGED = new AtomicInteger();
    private static final AtomicLong HEIGHT_CHANGED = new AtomicLong();
    private static final AtomicInteger MOST_CHANGED = new AtomicInteger();

    private final int siteX;
    private final int siteZ;
    private final int groundY;

    PortalSitePiece(int siteX, int siteZ, int groundY)
    {
        super(ModStructures.PORTAL_SITE_PIECE.get(), 0, box(siteX, siteZ, groundY));
        this.siteX = siteX;
        this.siteZ = siteZ;
        this.groundY = groundY;
    }

    public PortalSitePiece(CompoundTag tag)
    {
        super(ModStructures.PORTAL_SITE_PIECE.get(), tag);
        this.siteX = tag.getInt("SiteX");
        this.siteZ = tag.getInt("SiteZ");
        this.groundY = tag.getInt("GroundY");
    }

    /** The footprint and its staircase ring, from the deepest fill under the ground to {@value #ABOVE} over its top. */
    private static BoundingBox box(int x, int z, int groundY)
    {
        BoundingBox foot = PortalCathedral.footprint(x, z, groundY);
        int ring = SiteRules.BLEND_MARGIN;
        return new BoundingBox(foot.minX() - ring, groundY - SiteRules.MAX_FOUNDATION - 1, foot.minZ() - ring,
                foot.maxX() + ring, foot.maxY() + ABOVE, foot.maxZ() + ring);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.putInt("SiteX", this.siteX);
        tag.putInt("SiteZ", this.siteZ);
        tag.putInt("GroundY", this.groundY);
    }

    /** {chunks built, total nanoseconds, the slowest one chunk's nanoseconds} since the server started (for the tests). */
    static long[] timing()
    {
        return new long[] {CHUNKS.get(), TOTAL_NANOS.get(), SLOWEST_NANOS.get()};
    }

    /**
     * {columns whose ground the piece cut or filled, the blocks of height that took in all, the most in one column} since
     * the server started: what was left to do after Minecraft's terrain adaptation (for the tests' log).
     */
    static long[] groundWork()
    {
        return new long[] {COLUMNS_CHANGED.get(), HEIGHT_CHANGED.get(), MOST_CHANGED.get()};
    }

    // Minecraft's terrain adaptation: the land is drawn towards the floor over the footprint (not the ring: that's ours)
    @Override
    public BoundingBox getBeardifierBox()
    {
        BoundingBox foot = PortalCathedral.footprint(this.siteX, this.siteZ, this.groundY);
        return new BoundingBox(foot.minX(), this.groundY, foot.minZ(), foot.maxX(), foot.maxY(), foot.maxZ());
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment()
    {
        return TerrainAdjustment.BEARD_THIN;
    }

    /** The ground's top block ends up at the box's bottom, {@code groundY} (the beard fills up to the box's bottom + delta - 1). */
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
        BoundingBox foot = PortalCathedral.footprint(this.siteX, this.siteZ, this.groundY);
        this.ground(level, box, foot);
        if (box.intersects(foot)) // (a chunk of the staircase ring alone has none of its blocks)
        {
            this.cathedral(level, box, chunk);
        }
        long took = System.nanoTime() - start;
        int done = CHUNKS.incrementAndGet();
        TOTAL_NANOS.addAndGet(took);
        if (took > SLOWEST_NANOS.getAndAccumulate(took, Math::max))
        {
            LOGGER.info("Dross portal site: built its part in chunk {}, {} in {} ms (the slowest yet; {} of its chunks built)", chunk.x,
                    chunk.z, took / 1_000_000L, done);
        }
    }

    /** The ground in the chunk's part of the piece: the footprint levelled, the ring stepped back to the land. */
    private void ground(WorldGenLevel level, BoundingBox box, BoundingBox foot)
    {
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        BlockState deep = GroundFit.deepFillBlock(level.getLevel());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                int d = GroundFit.distanceOutside(foot, x, z);
                if (d >= SiteRules.BLEND_MARGIN)
                {
                    continue;
                }
                // (world generation's height map: the highest block that isn't air, water, a boulder or a plant included)
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int natural = groundUnder(level, pos, x, top, z);
                int target = d == 0 ? this.groundY : Mth.clamp(natural, this.groundY - d, this.groundY + d);
                if (target == natural && (d > 0 || top == natural))
                {
                    continue; // already where it should be (in the footprint, with nothing over its ground)
                }
                this.column(level, box, pos, x, z, top, natural, target, d == 0, deep);
                if (target != natural)
                {
                    COLUMNS_CHANGED.incrementAndGet();
                    HEIGHT_CHANGED.addAndGet(Math.abs(target - natural));
                    MOST_CHANGED.accumulateAndGet(Math.abs(target - natural), Math::max);
                }
            }
        }
    }

    /** The land's own top in a column: under any water, lava, boulder, plant or snow; {@code top} itself if none is found. */
    private static int groundUnder(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int top, int z)
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

    /** Solid natural ground: not air, a liquid, something simply replaced, a tree or a boulder's mossy cobblestone. */
    private static boolean isGround(BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && state.blocksMotion()
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** One column cut or filled to {@code target}, its own top block on it (and, under the footprint, small caves filled). */
    private void column(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int x, int z, int top, int natural,
                        int target, boolean footprint, BlockState deep)
    {
        BlockState topBlock = level.getBlockState(pos.set(x, natural, z));
        if (!isGround(topBlock))
        {
            topBlock = Blocks.GRASS_BLOCK.defaultBlockState();
        }
        if (topBlock.hasProperty(BlockStateProperties.SNOWY))
        {
            topBlock = topBlock.setValue(BlockStateProperties.SNOWY, false);
        }
        BlockState under = level.getBlockState(pos.set(x, natural - 1, z));
        if (!isGround(under))
        {
            under = deep;
        }
        // Everything over the target goes: land cut away, a boulder, water, plants.
        for (int y = top; y > target; y--)
        {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir())
            {
                level.setBlock(pos, AIR, FLAGS);
            }
            seal(level, box, pos, deep);
        }
        if (target > natural)
        {
            // (a deep dip under the footprint, a ravine, is filled only so far: a foundation, not a mountain)
            int bottom = footprint ? Math.max(natural + 1, target - SiteRules.MAX_FOUNDATION) : natural + 1;
            for (int y = bottom; y < target; y++)
            {
                level.setBlock(pos.set(x, y, z), target - y <= SOIL_DEPTH ? under : deep, FLAGS);
            }
        }
        else if (target < natural)
        {
            // a deep cut: soil under the new top, as a column has
            for (int y = target - 1; y >= target - (SOIL_DEPTH - 1); y--)
            {
                if (level.getBlockState(pos.set(x, y, z)).equals(deep))
                {
                    level.setBlock(pos, under, FLAGS);
                }
            }
        }
        level.setBlock(pos.set(x, target, z), topBlock, FLAGS);
        if (footprint)
        {
            for (int y = target - 1; y >= target - SiteRules.MAX_DIP_FILL; y--)
            {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir() || !state.getFluidState().isEmpty())
                {
                    level.setBlock(pos, target - y <= SOIL_DEPTH ? under : deep, FLAGS);
                }
            }
        }
    }

    /** Water or lava in the chunk beside a cleared cell becomes stone, so nothing pours into the cut. */
    private static void seal(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos cleared, BlockState deep)
    {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (Direction dir : Direction.Plane.HORIZONTAL)
        {
            side.setWithOffset(cleared, dir);
            if (box.isInside(side) && !level.getFluidState(side).isEmpty())
            {
                level.setBlock(side, deep, FLAGS);
            }
        }
    }

    /** The Fallen Cathedral's template, the chunk's part of it; its blocks exactly as saved (a known shape). */
    private void cathedral(WorldGenLevel level, BoundingBox box, ChunkPos chunk)
    {
        ShrineDesign design = PortalCathedral.design();
        Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(design.template());
        if (template.isEmpty())
        {
            LOGGER.error("Dross portal site: its template {} is missing (data/{}/structures/{}.nbt), so chunk {}, {} of it is built without "
                    + "it", design.template(), design.template().getNamespace(), design.template().getPath(), chunk.x, chunk.z);
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(true)
                .setKnownShape(true)
                .setBoundingBox(box);
        BlockPos origin = PortalCathedral.templateOrigin(this.siteX, this.siteZ, this.groundY);
        template.get().placeInWorld(level, origin, origin, settings, RandomSource.create(chunk.toLong()), FLAGS);
    }
}
