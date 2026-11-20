package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import com.reg21meme.dross.registry.ModStructures;
import com.reg21meme.dross.world.shrine.GroundFit;
import java.util.Optional;
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
 * Builds a knight ruin ({@link KnightRuinStructure}) a chunk at a time, with the land, each column from its own ground, so
 * the chunks agree whatever order they're made in (the portal site's way, {@code world/PortalSitePiece}). Minecraft's
 * terrain adaptation ({@code beard_thin}) has already drawn the land towards its floor over its footprint (a shore ruin's
 * land side only). In each chunk, only its own part:
 * <ol>
 *   <li><b>The ground</b>: under the footprint cut or filled to the ground level (at most {@value #FOUNDATION} blocks of
 *       foundation, small caves just under it filled); round it a staircase back to the land, a column d blocks out kept
 *       within d of the ground level, out to {@value #BLEND} blocks; a pond or the sea round it is left alone. A shore
 *       ruin's sea side is only cut down to the sea's level where the land stands over it (never filled, no water made).
 *       Each column keeps its own top block and soil.</li>
 *   <li><b>Its template</b>, turned so its way in faces its front, cut to the chunk; its unset cells keep the world's.</li>
 * </ol>
 * Saved with its middle column, ground level, front and design.
 */
public final class KnightRuinPiece extends StructurePiece implements PieceBeardifierModifier
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The staircase back to the land reaches this far round the footprint. */
    static final int BLEND = 8;
    /** At most this much foundation under the footprint (a ravine isn't filled up). */
    static final int FOUNDATION = 8;
    /** Small caves and dips this far under the floor are filled. */
    static final int DIP_FILL = 5;
    private static final int SOIL_DEPTH = 3;
    private static final int ABOVE = 4;
    private static final int GROUND_SEARCH = 40;

    private final RuinSite site;

    KnightRuinPiece(RuinSite site)
    {
        super(ModStructures.KNIGHT_RUIN_PIECE.get(), 0, box(site));
        this.site = site;
    }

    public KnightRuinPiece(CompoundTag tag)
    {
        super(ModStructures.KNIGHT_RUIN_PIECE.get(), tag);
        String id = tag.getString("Design");
        KnightRuinDesigns.Design design = KnightRuinDesigns.ALL.stream().filter(d -> d.id().equals(id)).findFirst()
                .orElse(KnightRuinDesigns.ALL.get(0));
        if (!design.id().equals(id))
        {
            LOGGER.warn("Knight ruin at {} {}: its design {} is gone; {} stands in for it", tag.getInt("X"), tag.getInt("Z"), id, design.id());
        }
        this.site = new RuinSite(tag.getInt("X"), tag.getInt("Z"), tag.getInt("GroundY"), Direction.from2DDataValue(tag.getInt("Front")),
                design, tag.getBoolean("NearSpawn"));
    }

    private static BoundingBox box(RuinSite site)
    {
        BoundingBox foot = site.footprint();
        return new BoundingBox(foot.minX() - BLEND, site.groundY() - FOUNDATION - 1, foot.minZ() - BLEND, foot.maxX() + BLEND,
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
        tag.putBoolean("NearSpawn", this.site.nearSpawn());
    }

    // Minecraft's terrain adaptation: the land drawn towards the floor over the footprint (a shore ruin's land side only)
    @Override
    public BoundingBox getBeardifierBox()
    {
        BoundingBox foot = this.site.footprint();
        if (this.site.design().area().setting() == KnightRuinDesigns.Setting.SHORE)
        {
            KnightRuinDesigns.Design d = this.site.design();
            int shore = KnightRuinDesigns.shoreLine(d.depth());
            BlockPos a = this.site.toWorld(new BlockPos(0, d.ground(), shore));
            BlockPos b = this.site.toWorld(new BlockPos(d.width() - 1, d.layers() - 1, d.depth() - 1));
            return BoundingBox.fromCorners(a, b);
        }
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
    }

    private void ground(WorldGenLevel level, BoundingBox box, BoundingBox foot)
    {
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        int groundY = this.site.groundY();
        BlockState deep = GroundFit.deepFillBlock(level.getLevel());
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
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int natural = groundUnder(level, pos, x, top, z);
                boolean wet = !level.getBlockState(pos.set(x, natural + 1, z)).getFluidState().isEmpty();
                if (this.site.seaSide(x, z))
                {
                    // the sea's side: only land standing over the sea's level is cut down to it
                    if (d == 0 && natural > groundY)
                    {
                        this.column(level, box, pos, x, z, top, natural, groundY, false, deep);
                    }
                    continue;
                }
                if (d > 0 && wet)
                {
                    continue; // (a pond or the sea beside it is left as it is)
                }
                int target = d == 0 ? groundY : Mth.clamp(natural, groundY - d, groundY + d);
                if (target == natural && (d > 0 || top == natural))
                {
                    continue;
                }
                this.column(level, box, pos, x, z, top, natural, target, d == 0, deep);
            }
        }
    }

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

    private static boolean isGround(BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && state.blocksMotion()
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !state.is(Blocks.MOSSY_COBBLESTONE);
    }

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
            int bottom = footprint ? Math.max(natural + 1, target - FOUNDATION) : natural + 1;
            for (int y = bottom; y < target; y++)
            {
                level.setBlock(pos.set(x, y, z), target - y <= SOIL_DEPTH ? under : deep, FLAGS);
            }
        }
        else if (target < natural)
        {
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
            for (int y = target - 1; y >= target - DIP_FILL; y--)
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
    private void seal(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos cleared, BlockState deep)
    {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (Direction dir : Direction.Plane.HORIZONTAL)
        {
            side.setWithOffset(cleared, dir);
            // (a shore ruin's sea stays the sea)
            if (box.isInside(side) && !level.getFluidState(side).isEmpty() && !this.site.seaSide(side.getX(), side.getZ()))
            {
                level.setBlock(side, deep, FLAGS);
            }
        }
    }

    private void template(WorldGenLevel level, BoundingBox box, ChunkPos chunk)
    {
        KnightRuinDesigns.Design design = this.site.design();
        Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(design.template());
        if (template.isEmpty())
        {
            LOGGER.error("Knight ruin {}: its template {} is missing, so chunk {}, {} of it is built without it", this.site.describe(),
                    design.template(), chunk.x, chunk.z);
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
}
