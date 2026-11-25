package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.world.shrine.GroundFit;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Once per Nether ruin, as it comes into view and before its knights first come (like {@link KnightRuinTidy} in the
 * Overworld): the Nether's features come after the structures, so its huge fungi, basalt columns, glowstone, vines, fire
 * and lava (a basalt delta's pools, a spring in a wall) may have grown into the ruin. In two server ticks:
 * <ol>
 *   <li>every huge fungus (its stems, wart, shroomlights and vines, joined to each other) with a block in the space over
 *       the ruin's ground is removed whole (followed at most {@link KnightRuinTidy#REACH} blocks out of the footprint);</li>
 *   <li>the space over its ground is cleared again (up to the piece's {@link NetherRuinPiece#ABOVE} over its top); lava in
 *       its footprint at or under its ground becomes netherrack, and round it ({@value #LAVA_MARGIN} blocks out) a lava
 *       source becomes netherrack and flowing lava air; then its template is placed again, so exactly its own blocks stand
 *       there (its own lava, fungi and fire among them), and its own lava is walled in again.</li>
 * </ol>
 */
final class NetherRuinTidy implements RuinTidy
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Lava round the footprint this far out is stopped. */
    static final int LAVA_MARGIN = 2;
    /** A huge fungus is followed at most this far over the space cleared over the ruin (a ceiling's wart and vines). */
    private static final int FUNGUS_UP = 24;
    /** Safety limit on the fungi removed (blocks); past it the rest stays (logged). */
    private static final int MAX_FUNGUS_BLOCKS = 4000;

    private final ServerLevel level;
    private final RuinSite site;
    private final BoundingBox foot;
    private final int clearTop;
    private final long started = System.nanoTime();
    private boolean fungiDone;
    private int fungi;
    private boolean truncated;

    NetherRuinTidy(ServerLevel level, RuinSite site)
    {
        this.level = level;
        this.site = site;
        this.foot = site.footprint();
        this.clearTop = this.foot.maxY() + NetherRuinPiece.ABOVE;
    }

    @Override
    public boolean step()
    {
        if (!this.fungiDone)
        {
            this.fungi();
            this.fungiDone = true;
            return false;
        }
        int cleared = this.clear();
        int lava = this.lava();
        this.template();
        LOGGER.info("Knight ruin {}: tidied ({} fungus blocks removed{}, {} other blocks cleared over its ground, {} lava blocks stopped) "
                        + "and put its own blocks back, in {} ms", this.site.describe(), this.fungi, this.truncated ? " (hit the limit)" : "",
                cleared, lava, (System.nanoTime() - this.started) / 1_000_000L);
        return true;
    }

    // ---------------------------------------------------------------- the fungi

    private void fungi()
    {
        int ground = this.site.groundY();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet seen = new LongOpenHashSet();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = this.foot.minX(); x <= this.foot.maxX(); x++)
        {
            for (int z = this.foot.minZ(); z <= this.foot.maxZ(); z++)
            {
                for (int y = ground + 1; y <= this.clearTop; y++)
                {
                    if (isFungus(this.level.getBlockState(pos.set(x, y, z))) && seen.add(pos.asLong()))
                    {
                        queue.add(pos.immutable());
                    }
                }
            }
        }
        List<BlockPos> found = new ArrayList<>();
        while (!queue.isEmpty())
        {
            BlockPos at = queue.poll();
            found.add(at);
            if (found.size() >= MAX_FUNGUS_BLOCKS)
            {
                this.truncated = true;
                break;
            }
            for (Direction dir : Direction.values())
            {
                BlockPos next = at.relative(dir);
                if (this.reaches(next) && !seen.contains(next.asLong()) && isFungus(this.level.getBlockState(next)))
                {
                    seen.add(next.asLong());
                    queue.add(next);
                }
            }
        }
        for (BlockPos at : found)
        {
            this.level.setBlock(at, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        this.fungi = found.size();
    }

    private boolean reaches(BlockPos pos)
    {
        int r = KnightRuinTidy.REACH;
        return pos.getX() >= this.foot.minX() - r && pos.getX() <= this.foot.maxX() + r && pos.getZ() >= this.foot.minZ() - r
                && pos.getZ() <= this.foot.maxZ() + r && pos.getY() > this.site.groundY() - 2 && pos.getY() <= this.clearTop + FUNGUS_UP;
    }

    /** A huge fungus's block: its stem, wart, shroomlight, or the vines hanging or growing off them. */
    static boolean isFungus(BlockState state)
    {
        return state.is(BlockTags.CRIMSON_STEMS) || state.is(BlockTags.WARPED_STEMS) || state.is(BlockTags.WART_BLOCKS)
                || state.is(Blocks.SHROOMLIGHT) || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT);
    }

    // ---------------------------------------------------------------- the space over it, the lava, its template

    /** Everything over its ground in the footprint cleared (its template's own come back with it). */
    private int clear()
    {
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = this.foot.minX(); x <= this.foot.maxX(); x++)
        {
            for (int z = this.foot.minZ(); z <= this.foot.maxZ(); z++)
            {
                for (int y = this.site.groundY() + 1; y <= this.clearTop; y++)
                {
                    if (!this.level.getBlockState(pos.set(x, y, z)).isAir())
                    {
                        this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Lava in the footprint at or under its ground becomes netherrack (its template's own comes back with it); round it a
     * source becomes netherrack and flowing lava air. */
    private int lava()
    {
        int n = 0;
        BlockState deep = GroundFit.deepFillBlock(this.level);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bottom = this.site.groundY() - NetherRuinPiece.FOUNDATION;
        for (int x = this.foot.minX() - LAVA_MARGIN; x <= this.foot.maxX() + LAVA_MARGIN; x++)
        {
            for (int z = this.foot.minZ() - LAVA_MARGIN; z <= this.foot.maxZ() + LAVA_MARGIN; z++)
            {
                boolean inside = x >= this.foot.minX() && x <= this.foot.maxX() && z >= this.foot.minZ() && z <= this.foot.maxZ();
                for (int y = bottom; y <= this.clearTop; y++)
                {
                    FluidState fluid = this.level.getFluidState(pos.set(x, y, z));
                    if (!fluid.is(FluidTags.LAVA))
                    {
                        continue;
                    }
                    boolean solid = inside || fluid.isSource();
                    this.level.setBlock(pos, solid ? deep : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    n++;
                }
            }
        }
        return n;
    }

    private void template()
    {
        Optional<StructureTemplate> template = this.level.getStructureManager().get(this.site.design().template());
        if (template.isEmpty())
        {
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(this.site.rotation())
                .setRotationPivot(this.site.pivot()).setIgnoreEntities(true).setKnownShape(true);
        BlockPos origin = this.site.origin();
        template.get().placeInWorld(this.level, origin, origin, settings, RandomSource.create(origin.asLong()), FLAGS);
        // its own lava walled in again (as the piece did)
        List<StructureTemplate.StructureBlockInfo> lava = template.get().filterBlocks(origin,
                new StructurePlaceSettings().setRotation(this.site.rotation()).setRotationPivot(this.site.pivot()), Blocks.LAVA);
        Set<BlockPos> cells = new HashSet<>();
        lava.forEach(info -> cells.add(info.pos()));
        BlockState deep = GroundFit.deepFillBlock(this.level);
        for (BlockPos cell : cells)
        {
            for (Direction dir : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN})
            {
                BlockPos next = cell.relative(dir);
                if (!cells.contains(next) && !NetherRuinPiece.holds(this.level.getBlockState(next)))
                {
                    this.level.setBlock(next, deep, FLAGS);
                }
            }
        }
    }
}
