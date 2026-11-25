package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import com.reg21meme.dross.registry.ModStructures;
import com.reg21meme.dross.world.shrine.GroundFit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.common.world.PieceBeardifierModifier;
import org.slf4j.Logger;

/**
 * Builds a knight ruin in the Nether ({@link NetherRuinStructure}) a chunk at a time, with the land, each column from its
 * own blocks, so the chunks agree whatever order they're made in. Minecraft's terrain adaptation ({@code beard_thin}) has
 * already drawn the Nether's rock towards its floor and away from the space over it. The Nether has a roof, so nothing
 * here reads a heightmap: every column is read up and down from the ruin's ground. In each chunk, only its own part:
 * <ol>
 *   <li><b>The footprint</b>: the space over its ground cleared up to {@value #ABOVE} over its top (rock cut away: the
 *       planner keeps that to a little, {@link NetherRuinSites}), its ground block the column's own top block, and under
 *       it a foundation of the column's own soil and netherrack: every gap or lava pocket in the {@value #FOUNDATION}
 *       blocks under it filled, and past that on down to the column's ground (at most {@value #FILL_MAX}), so it never
 *       floats.</li>
 *   <li><b>Round it</b>: a staircase back to the land, a column d blocks out kept within d of the ground level, out to
 *       {@value #BLEND} blocks, from that column's own floor near the ground (rock walls and drops round it are left as
 *       they are).</li>
 *   <li><b>Its template</b>, turned so its way in faces its front, cut to the chunk; its unset cells keep the world's
 *       (the footprint's air over the ground, its foundation under it).</li>
 *   <li><b>Its lava kept in</b>: any open cell beside or under one of its template's lava blocks becomes netherrack.</li>
 * </ol>
 * Lava beside a cleared cell is sealed with netherrack. Saved with its middle column, ground level, front and design.
 */
public final class NetherRuinPiece extends StructurePiece implements PieceBeardifierModifier
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The staircase back to the land reaches this far round the footprint. */
    static final int BLEND = 8;
    /** Every gap under the ground this far down is filled (the foundation)... */
    static final int FOUNDATION = 8;
    /** ... and past that, on down to the column's own ground, at most this far under the ground. */
    static final int FILL_MAX = 16;
    /** The column's own top block's soil this deep under a filled top (then netherrack). */
    private static final int SOIL_DEPTH = 2;
    /** The air over the footprint is cleared this far over the ruin's top. */
    static final int ABOVE = 4;
    /** Round it, a column's floor is looked for this far past the staircase's reach, above and below. */
    private static final int LOOK = 4;
    private static final int NONE = Integer.MIN_VALUE;
    private static final Direction[] SEAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP};
    private static final Direction[] HOLD = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};

    private final RuinSite site;

    NetherRuinPiece(RuinSite site)
    {
        super(ModStructures.NETHER_KNIGHT_RUIN_PIECE.get(), 0, box(site));
        this.site = site;
    }

    public NetherRuinPiece(CompoundTag tag)
    {
        super(ModStructures.NETHER_KNIGHT_RUIN_PIECE.get(), tag);
        String id = tag.getString("Design");
        KnightRuinDesigns.Design fallback = KnightRuinDesigns.ALL.stream().filter(d -> RuinAreas.NETHER.contains(d.area())).findFirst()
                .orElse(KnightRuinDesigns.ALL.get(0));
        KnightRuinDesigns.Design design = KnightRuinDesigns.ALL.stream().filter(d -> d.id().equals(id)).findFirst().orElse(fallback);
        if (!design.id().equals(id))
        {
            LOGGER.warn("Knight ruin in the Nether at {} {}: its design {} is gone; {} stands in for it", tag.getInt("X"), tag.getInt("Z"),
                    id, design.id());
        }
        this.site = new RuinSite(tag.getInt("X"), tag.getInt("Z"), tag.getInt("GroundY"), Direction.from2DDataValue(tag.getInt("Front")),
                design, false);
    }

    static BoundingBox box(RuinSite site)
    {
        BoundingBox foot = site.footprint();
        return new BoundingBox(foot.minX() - BLEND, site.groundY() - FILL_MAX - 1, foot.minZ() - BLEND, foot.maxX() + BLEND,
                foot.maxY() + ABOVE, foot.maxZ() + BLEND);
    }

    public RuinSite site()
    {
        return this.site;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.putInt("X", this.site.x());
        tag.putInt("Z", this.site.z());
        tag.putInt("GroundY", this.site.groundY());
        tag.putInt("Front", this.site.front().get2DDataValue());
        tag.putString("Design", this.site.design().id());
    }

    // Minecraft's terrain adaptation: the rock drawn towards the floor under the footprint and away over it
    @Override
    public BoundingBox getBeardifierBox()
    {
        BoundingBox foot = this.site.footprint();
        return new BoundingBox(foot.minX(), this.site.groundY(), foot.minZ(), foot.maxX(), foot.maxY(), foot.maxZ());
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment()
    {
        return TerrainAdjustment.BEARD_THIN;
    }

    @Override
    public int getGroundLevelDelta()
    {
        return 1;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot)
    {
        BoundingBox foot = this.site.footprint();
        this.ground(level, box, foot);
        if (box.intersects(foot))
        {
            this.template(level, box, chunk);
        }
        if (box.intersects(foot.inflatedBy(1)))
        {
            this.holdLava(level, box);
        }
    }

    private void ground(WorldGenLevel level, BoundingBox box, BoundingBox foot)
    {
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        int groundY = this.site.groundY();
        int clearTop = foot.maxY() + ABOVE;
        BlockState deep = GroundFit.deepFillBlock(level.getLevel());
        BlockState areaTop = block(this.site.design().area().ground(), deep);
        BlockState areaUnder = block(this.site.design().area().under(), deep);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                int d = GroundFit.distanceOutside(foot, x, z);
                if (d >= BLEND)
                {
                    continue;
                }
                if (d == 0)
                {
                    this.footColumn(level, box, pos, x, z, groundY, clearTop, areaTop, areaUnder, deep);
                }
                else
                {
                    this.ringColumn(level, box, pos, x, z, groundY, d, deep);
                }
            }
        }
    }

    /** A footprint column: cleared over its ground, its ground its own top block, a foundation under it down to real ground. */
    private void footColumn(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int x, int z, int groundY, int clearTop,
                            BlockState areaTop, BlockState areaUnder, BlockState deep)
    {
        int natural = floorNear(level, pos, x, z, groundY + LOOK + BLEND, groundY - FILL_MAX, groundY);
        BlockState top = areaTop;
        BlockState under = areaUnder;
        if (natural != NONE)
        {
            top = level.getBlockState(pos.set(x, natural, z));
            BlockState below = level.getBlockState(pos.set(x, natural - 1, z));
            under = isGround(below) ? below : deep;
        }
        for (int y = clearTop; y > groundY; y--)
        {
            pos.set(x, y, z);
            if (!level.getBlockState(pos).isAir())
            {
                level.setBlock(pos, AIR, FLAGS);
            }
            this.seal(level, box, pos, deep);
        }
        level.setBlock(pos.set(x, groundY, z), top, FLAGS);
        for (int y = groundY - 1; y >= groundY - FILL_MAX; y--)
        {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            boolean ground = isGround(state);
            if (ground && y < groundY - FOUNDATION)
            {
                break; // (past the foundation: down to the first real ground, no further)
            }
            if (!ground)
            {
                level.setBlock(pos, groundY - y <= SOIL_DEPTH ? under : deep, FLAGS);
            }
        }
    }

    /** A column round it: its own floor near the ground stepped to within d of it (a wall or a drop there is left alone). */
    private void ringColumn(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos pos, int x, int z, int groundY, int d,
                            BlockState deep)
    {
        int natural = floorNear(level, pos, x, z, groundY + d + LOOK, groundY - d - LOOK, groundY);
        if (natural == NONE)
        {
            return;
        }
        int target = Mth.clamp(natural, groundY - d, groundY + d);
        if (target == natural)
        {
            return;
        }
        BlockState top = level.getBlockState(pos.set(x, natural, z));
        BlockState below = level.getBlockState(pos.set(x, natural - 1, z));
        BlockState under = isGround(below) ? below : deep;
        if (natural > target)
        {
            for (int y = natural; y > target; y--)
            {
                pos.set(x, y, z);
                level.setBlock(pos, AIR, FLAGS);
                this.seal(level, box, pos, deep);
            }
        }
        else
        {
            for (int y = natural; y < target; y++)
            {
                level.setBlock(pos.set(x, y, z), target - y <= SOIL_DEPTH ? under : deep, FLAGS);
            }
        }
        level.setBlock(pos.set(x, target, z), top, FLAGS);
    }

    /** The floor (ground with open air over it) nearest {@code level} between {@code from} and {@code to}, or {@link #NONE}. */
    private static int floorNear(WorldGenLevel world, BlockPos.MutableBlockPos pos, int x, int z, int from, int to, int level)
    {
        int best = NONE;
        int bestDist = Integer.MAX_VALUE;
        boolean openAbove = isOpen(world.getBlockState(pos.set(x, from + 1, z)));
        for (int y = from; y >= to && y > world.getMinBuildHeight(); y--)
        {
            BlockState state = world.getBlockState(pos.set(x, y, z));
            boolean ground = isGround(state);
            if (ground && openAbove)
            {
                int dist = Math.abs(y - level);
                if (dist < bestDist)
                {
                    best = y;
                    bestDist = dist;
                }
            }
            openAbove = isOpen(state);
        }
        return best;
    }

    /** Natural ground to stand on: solid, dry, not a plant, a fungus's stem, wart or light. */
    static boolean isGround(BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && state.blocksMotion()
                && !state.is(BlockTags.LOGS) && !state.is(BlockTags.WART_BLOCKS) && !state.is(Blocks.SHROOMLIGHT);
    }

    /** Open over a floor: air, or something a step goes through (a plant, fire), and no lava. */
    private static boolean isOpen(BlockState state)
    {
        return !isGround(state) && state.getFluidState().isEmpty() && !state.blocksMotion();
    }

    private static BlockState block(String id, BlockState fallback)
    {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id));
        return block == Blocks.AIR ? fallback : block.defaultBlockState();
    }

    /** Lava (or anything flowing) in the chunk beside or over a cleared cell becomes netherrack, so nothing pours in. */
    private void seal(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos cleared, BlockState deep)
    {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (Direction dir : SEAL)
        {
            side.setWithOffset(cleared, dir);
            if (box.isInside(side) && !level.getFluidState(side).isEmpty())
            {
                level.setBlock(side, deep, FLAGS);
            }
        }
    }

    private Optional<StructureTemplate> templateOf(WorldGenLevel level)
    {
        return level.getLevel().getStructureManager().get(this.site.design().template());
    }

    private void template(WorldGenLevel level, BoundingBox box, ChunkPos chunk)
    {
        Optional<StructureTemplate> template = this.templateOf(level);
        if (template.isEmpty())
        {
            LOGGER.error("Knight ruin {}: its template {} is missing, so chunk {}, {} of it is built without it", this.site.describe(),
                    this.site.design().template(), chunk.x, chunk.z);
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(this.site.rotation())
                .setRotationPivot(this.site.pivot())
                .setIgnoreEntities(true)
                .setKnownShape(true)
                .setBoundingBox(box);
        BlockPos origin = this.site.origin();
        template.get().placeInWorld(level, origin, origin, settings, RandomSource.create(chunk.toLong()), FLAGS);
    }

    /**
     * Its template's lava kept in: in this chunk, every cell beside (or under) one of its lava blocks that lava could flow
     * into becomes netherrack. Worked out from the template, not from what's built in the chunk next door, so the chunks
     * agree whatever order they're made in.
     */
    private void holdLava(WorldGenLevel level, BoundingBox box)
    {
        Optional<StructureTemplate> template = this.templateOf(level);
        if (template.isEmpty())
        {
            return;
        }
        List<StructureTemplate.StructureBlockInfo> lava = template.get().filterBlocks(this.site.origin(),
                new StructurePlaceSettings().setRotation(this.site.rotation()).setRotationPivot(this.site.pivot()), Blocks.LAVA);
        if (lava.isEmpty())
        {
            return;
        }
        Set<BlockPos> cells = new HashSet<>();
        lava.forEach(info -> cells.add(info.pos()));
        BlockState deep = GroundFit.deepFillBlock(level.getLevel());
        for (BlockPos cell : cells)
        {
            for (Direction dir : HOLD)
            {
                BlockPos next = cell.relative(dir);
                if (box.isInside(next) && !cells.contains(next) && !holds(level.getBlockState(next)))
                {
                    level.setBlock(next, deep, FLAGS);
                }
            }
        }
    }

    /** Lava can't flow into it. */
    static boolean holds(BlockState state)
    {
        return state.blocksMotion() && state.getFluidState().isEmpty() || state.is(Blocks.LAVA);
    }
}
