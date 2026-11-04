package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.world.shrine.TreeFelling;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * The one tidy-up a Rift Chapel needs after world generation: Minecraft's trees are placed after the structures (in the
 * chapel's chunks and, reaching over, their neighbours'), so some grow into it, on its yard or through its roof; this fells
 * every tree, huge mushroom and ice spike that touches its footprint, whole, with the portal site's felling (world-builder's
 * {@code world.shrine.TreeFelling}: leaves no remaining log holds up go too, other trees are left alone). The chapel's own
 * blocks are none of the kinds it removes (no logs, leaves, huge mushroom blocks, ice or mossy cobblestone); then any of them
 * a huge mushroom grew through is put back ({@link #restore}).
 * <p>
 * Run by the chapel's trader ({@link DrossTrader}) once the land round it is loaded ({@link #landLoaded}): by then every
 * chunk whose trees could reach into it has been fully made, so nothing grows into it later (but a player's sapling).
 */
final class RiftChapelTidy
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A tree touching the chapel is followed at most this far out of its footprint (blocks); this land must be loaded. */
    static final int REACH = 16;

    /** Placing the chapel's own blocks back: tell clients, never react to neighbours (the way world generation placed them). */
    private static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel level;
    private final BoundingBox hut;
    @Nullable
    private final BlockPos center;
    @Nullable
    private final Direction facing;
    private final TreeFelling.Felling felling;
    private final long startNanos = System.nanoTime();
    private int restored;

    /** {@code center} and {@code facing} say which design blocks to put back (null: none, only the felling). */
    RiftChapelTidy(ServerLevel level, BoundingBox hut, @Nullable BlockPos center, @Nullable Direction facing)
    {
        this.level = level;
        this.hut = hut;
        this.center = center;
        this.facing = facing;
        this.felling = new TreeFelling.Felling(level, new Area(hut));
    }

    /** True once every chunk the felling may read (the footprint and {@value #REACH} blocks round it) is fully loaded. */
    static boolean landLoaded(ServerLevel level, BoundingBox hut)
    {
        for (int cx = SectionPos.blockToSectionCoord(hut.minX() - REACH); cx <= SectionPos.blockToSectionCoord(hut.maxX() + REACH); cx++)
        {
            for (int cz = SectionPos.blockToSectionCoord(hut.minZ() - REACH); cz <= SectionPos.blockToSectionCoord(hut.maxZ() + REACH); cz++)
            {
                if (level.getChunkSource().getChunkNow(cx, cz) == null)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** At most about 40 ms of the felling (then the chapel's blocks put back, at once); true once it's done. Server thread. */
    boolean step()
    {
        if (!this.felling.step())
        {
            return false;
        }
        this.restore();
        return true;
    }

    /**
     * Puts back any of the chapel's own blocks something else took (Minecraft's huge mushrooms grow through anything that
     * isn't a full cube, its stairs, panes and lanterns among them, so felling one leaves a hole). A door is left as its
     * trader left it, open or shut.
     */
    private void restore()
    {
        if (this.center == null || this.facing == null)
        {
            return;
        }
        Map<BlockPos, BlockState> design = TraderHut.DESIGN.blocks(this.center, this.facing);
        for (Map.Entry<BlockPos, BlockState> entry : design.entrySet())
        {
            BlockState want = entry.getValue();
            BlockState there = this.level.getBlockState(entry.getKey());
            if (want.isAir() || there.getBlock() == want.getBlock() && (there.equals(want) || there.hasProperty(DoorBlock.OPEN)))
            {
                continue;
            }
            this.level.setBlock(entry.getKey(), want, RESTORE_FLAGS);
            this.restored++;
        }
    }

    void log()
    {
        LOGGER.info("[Dross] Rift Chapel at {}, {}, {}: felled what grew into it ({}) and put back {} of its own blocks, in {} ms",
                (this.hut.minX() + this.hut.maxX()) / 2, this.hut.minY(), (this.hut.minZ() + this.hut.maxZ()) / 2, this.felling.result().summary(),
                this.restored, (System.nanoTime() - this.startNanos) / 1_000_000L);
    }

    /** The felling's area: the footprint's columns over its ground; a tree touching it is followed {@value #REACH} blocks out. */
    private record Area(BoundingBox hut) implements TreeFelling.Area
    {
        @Override public int minX() { return this.hut.minX(); }
        @Override public int minZ() { return this.hut.minZ(); }
        @Override public int sizeX() { return this.hut.getXSpan(); }
        @Override public int sizeZ() { return this.hut.getZSpan(); }
        @Override public boolean isTouched(int x, int z) { return true; }
        @Override public int scanFrom(int x, int z) { return this.hut.minY(); }
        @Override public int groundAt(int x, int z) { return this.hut.minY(); }

        /** Everything over the footprint's ground counts as cleared: a leaf there held up by a trunk outside takes its tree. */
        @Override
        public boolean destroyed(BlockPos pos)
        {
            return pos.getY() > this.hut.minY() && pos.getX() >= this.hut.minX() && pos.getX() <= this.hut.maxX()
                    && pos.getZ() >= this.hut.minZ() && pos.getZ() <= this.hut.maxZ();
        }

        @Override
        public boolean reaches(BlockPos pos)
        {
            return pos.getX() >= this.hut.minX() - REACH && pos.getX() <= this.hut.maxX() + REACH
                    && pos.getZ() >= this.hut.minZ() - REACH && pos.getZ() <= this.hut.maxZ() + REACH;
        }
    }
}
