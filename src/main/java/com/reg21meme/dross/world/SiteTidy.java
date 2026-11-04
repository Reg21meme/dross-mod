package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.shrine.TreeFelling;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The portal site's tidy-up, once per world. The cathedral is made with the land in the "surface structures" step, and the
 * trees, grass and flowers come after it in each chunk's decoration (the site is meant to be in forests), so some grow on
 * and through it. When every chunk of its footprint and {@value SiteRules#TIDY_RING_CHUNKS} chunks round it is loaded (it
 * never loads one: it waits until a player's normal loading has them, or a test's), a step a server tick (at most about
 * 40 ms each, like the cathedral's old build):
 * <ol>
 *   <li>every tree, huge mushroom and ice spike that touches the footprint is felled whole ({@link TreeFelling}, with the
 *       cathedral's own blocks kept: its beams of dark oak and spruce, its mossy cobblestone and blue ice);</li>
 *   <li>anything in the frame's opening is cleared (so the Rift Key can light it);</li>
 *   <li>the Dross leaks out round the frame ({@link SiteLeak}: dead plants, coarse dirt, cracked bricks).</li>
 * </ol>
 * Then it's saved as done ({@link PortalSiteData}). If its land unloads half way, it waits; if the server stops after it
 * has begun removing things and its land is still loaded, the rest is done at once.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SiteTidy
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The live world's own flags: tell clients, never react to neighbours. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /** The running tidy-up, or null. */
    @Nullable
    private static Job job;
    /** This server has nothing (more) to tidy. */
    private static boolean finished;
    private static int ticks;

    private SiteTidy() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || finished)
        {
            return;
        }
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null)
        {
            return;
        }
        try
        {
            tick(overworld);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross portal site: the tidy-up failed; it's left as it is", e);
            job = null;
            finished = true;
        }
    }

    private static void tick(ServerLevel overworld)
    {
        if (job == null)
        {
            if (++ticks % SiteRules.TIDY_CHECK_TICKS != 0)
            {
                return;
            }
            if (!SiteFinder.available(overworld))
            {
                finished = true; // a world with no site (a superflat world's own list of structures)
                return;
            }
            SiteSpot spot = SiteFinder.spotIfReady(overworld);
            if (spot == null)
            {
                return;
            }
            PortalSiteData data = PortalSiteData.get(overworld);
            if (data.isTidied(spot.frame().corner()))
            {
                finished = true;
                return;
            }
            if (!loaded(overworld, spot))
            {
                return;
            }
            if (!madeWithTheLand(overworld, spot))
            {
                LOGGER.warn("Dross portal site: its land at {} {} was made before the site came with the land (its chunk has no "
                        + "dross:portal_site start), so there's nothing to tidy up", spot.x(), spot.z());
                data.markTidied(spot.frame().corner(), "nothing: no cathedral was made with this land");
                finished = true;
                return;
            }
            job = new Job(overworld, spot);
            LOGGER.info("Dross portal site: its land is loaded; tidying it up (trees, the frame's opening, the leak)");
        }
        if (!loaded(overworld, job.spot))
        {
            return; // its land unloaded: wait (a step never loads a chunk)
        }
        if (job.step())
        {
            done(overworld, job);
        }
    }

    private static void done(ServerLevel overworld, Job done)
    {
        String summary = done.summary();
        PortalSiteData.get(overworld).markTidied(done.spot.frame().corner(), summary);
        LOGGER.info("Dross portal site: tidied up: {}", summary);
        job = null;
        finished = true;
    }

    /** The server is stopping: a tidy-up that has begun removing things is finished now, if its land is still loaded. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        MinecraftServer server = event.getServer();
        Job running = job;
        if (running != null && running.changedWorld() && server.overworld() != null && loaded(server.overworld(), running.spot))
        {
            while (!running.step())
            {
                // the remaining steps, one after another
            }
            done(server.overworld(), running);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        job = null;
        finished = false;
        ticks = 0;
    }

    /** True once the tidy-up for this world is done (or there's nothing to tidy): for the tests. */
    static boolean isFinished()
    {
        return finished;
    }

    /** The chunks the tidy-up needs loaded: the footprint's and {@value SiteRules#TIDY_RING_CHUNKS} round them. */
    static BoundingBox chunks(SiteSpot spot)
    {
        BoundingBox foot = spot.footprint();
        int ring = SiteRules.TIDY_RING_CHUNKS;
        return new BoundingBox(SectionPos.blockToSectionCoord(foot.minX()) - ring, 0, SectionPos.blockToSectionCoord(foot.minZ()) - ring,
                SectionPos.blockToSectionCoord(foot.maxX()) + ring, 0, SectionPos.blockToSectionCoord(foot.maxZ()) + ring);
    }

    static boolean loaded(ServerLevel overworld, SiteSpot spot)
    {
        BoundingBox chunks = chunks(spot);
        for (int cx = chunks.minX(); cx <= chunks.maxX(); cx++)
        {
            for (int cz = chunks.minZ(); cz <= chunks.maxZ(); cz++)
            {
                if (overworld.getChunkSource().getChunkNow(cx, cz) == null)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** True if the site's chunk (loaded) has the {@code dross:portal_site} structure start. */
    static boolean madeWithTheLand(ServerLevel overworld, SiteSpot spot)
    {
        Structure site = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(new ResourceLocation(Dross.MODID, "portal_site"));
        return site != null && overworld.getChunk(spot.startChunk().x, spot.startChunk().z).getStartForStructure(site) != null;
    }

    /** A block the felling takes when it's a tree's: logs, leaves, a boulder's stone, an ice spike's ice, huge mushrooms. */
    private static boolean fellable(BlockState state)
    {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.MOSSY_COBBLESTONE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.getBlock() instanceof net.minecraft.world.level.block.HugeMushroomBlock;
    }

    /** The tidy-up's work: the felling (a step at a time), then the opening and the leak (one step). */
    private static final class Job
    {
        final ServerLevel level;
        final SiteSpot spot;
        final TreeFelling.Felling felling;
        /** 0: felling; 1: the opening and the leak; 2: done. */
        int stage;
        int steps;
        long nanos;
        long longestNanos;
        int openingCleared;
        String leak = "";

        Job(ServerLevel level, SiteSpot spot)
        {
            this.level = level;
            this.spot = spot;
            this.felling = new TreeFelling.Felling(level, new CathedralArea(level, spot));
        }

        /** The next step. @return true once it's all done */
        boolean step()
        {
            long start = System.nanoTime();
            if (stage == 0)
            {
                if (felling.step())
                {
                    stage = 1;
                }
            }
            else if (stage == 1)
            {
                openingCleared = clearOpening(level, spot.frame());
                leak = SiteLeak.apply(level, spot.frame());
                stage = 2;
            }
            long took = System.nanoTime() - start;
            steps++;
            nanos += took;
            longestNanos = Math.max(longestNanos, took);
            return stage == 2;
        }

        boolean changedWorld()
        {
            return stage > 0 || felling.changedWorld();
        }

        String summary()
        {
            return felling.result().summary() + "; " + openingCleared + " blocks cleared out of the frame's opening; " + leak
                    + String.format("; %d ms of work in %d steps, the longest %d ms", nanos / 1_000_000L, steps, longestNanos / 1_000_000L);
        }
    }

    /** Anything in the frame's opening (leaves, a trunk, snow) but its own portal, should it already be lit, goes. */
    private static int clearOpening(ServerLevel level, SiteFrame frame)
    {
        BlockPos min = frame.openingMin();
        Direction along = SiteFrame.positive(frame.axis());
        int cleared = 0;
        for (int i = 0; i < frame.openingWidth(); i++)
        {
            for (int j = 0; j < frame.openingHeight(); j++)
            {
                BlockPos cell = min.relative(along, i).above(j);
                BlockState state = level.getBlockState(cell);
                if (!state.isAir() && !state.is(ModBlocks.DROSS_PORTAL.get()))
                {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), FLAGS);
                    cleared++;
                }
            }
        }
        return cleared;
    }

    /**
     * The felling's area: the footprint's columns above its ground; a tree touching it is followed at most
     * {@value SiteRules#TIDY_REACH} blocks out (inside the loaded ring); the cathedral's own blocks are kept.
     */
    private static final class CathedralArea implements TreeFelling.Area
    {
        final BoundingBox foot;
        final int groundY;
        final BoundingBox reach;
        final LongOpenHashSet kept;

        CathedralArea(ServerLevel level, SiteSpot spot)
        {
            this.foot = spot.footprint();
            this.groundY = spot.groundY();
            int r = SiteRules.TIDY_REACH;
            this.reach = new BoundingBox(foot.minX() - r, level.getMinBuildHeight(), foot.minZ() - r, foot.maxX() + r,
                    level.getMaxBuildHeight(), foot.maxZ() + r);
            this.kept = templateBlocks(level, spot);
        }

        @Override public int minX() { return foot.minX(); }
        @Override public int minZ() { return foot.minZ(); }
        @Override public int sizeX() { return foot.getXSpan(); }
        @Override public int sizeZ() { return foot.getZSpan(); }
        @Override public boolean isTouched(int x, int z) { return true; }
        @Override public int scanFrom(int x, int z) { return groundY; }
        @Override public int groundAt(int x, int z) { return groundY; }

        /** Everything over the footprint's ground counts as cleared: a leaf there held up by a trunk outside takes its tree. */
        @Override
        public boolean destroyed(BlockPos pos)
        {
            return pos.getY() > groundY && pos.getX() >= foot.minX() && pos.getX() <= foot.maxX() && pos.getZ() >= foot.minZ()
                    && pos.getZ() <= foot.maxZ();
        }

        @Override
        public boolean keeps(BlockPos pos)
        {
            return kept.contains(pos.asLong());
        }

        @Override
        public boolean reaches(BlockPos pos)
        {
            return reach.isInside(pos);
        }
    }

    /**
     * Where the cathedral's template put a block the felling would otherwise take (its own beams of dark oak and spruce,
     * mossy cobblestone, blue ice: logs, leaves, boulder stone, ice, huge mushroom blocks), as packed positions. A tree that
     * grew into one of its other cells (a dead bush's) is the world's, and goes.
     */
    static LongOpenHashSet templateBlocks(ServerLevel level, SiteSpot spot)
    {
        LongOpenHashSet out = new LongOpenHashSet();
        Optional<StructureTemplate> found = level.getStructureManager().get(PortalCathedral.design().template());
        if (found.isEmpty())
        {
            return out;
        }
        CompoundTag tag = found.get().save(new CompoundTag());
        ListTag palette = tag.contains("palette", Tag.TAG_LIST) ? tag.getList("palette", Tag.TAG_COMPOUND)
                : tag.getList("palettes", Tag.TAG_LIST).getList(0);
        boolean[] skip = new boolean[palette.size()];
        for (int i = 0; i < palette.size(); i++)
        {
            skip[i] = !fellable(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        }
        BlockPos origin = PortalCathedral.templateOrigin(spot.x(), spot.z(), spot.groundY());
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++)
        {
            CompoundTag block = blocks.getCompound(i);
            if (skip[block.getInt("state")])
            {
                continue;
            }
            ListTag p = block.getList("pos", Tag.TAG_INT);
            out.add(BlockPos.asLong(origin.getX() + p.getInt(0), origin.getY() + p.getInt(1), origin.getZ() + p.getInt(2)));
        }
        return out;
    }
}
