package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.portal.DrossPortalShape;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import com.reg21meme.dross.world.shrine.ShrineDesigns;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code /dross showcase shrines} (test tool, cheats only): builds the candidate designs for a grander portal shrine
 * ({@code world.shrine.ShrineDesigns}) side by side, so they can be compared and one picked. The row is about 1,000
 * blocks long, so use a superflat world.
 * <p>
 * The layout, seen from where the command was run:
 * <ul>
 *   <li>one row: No. 1 straight ahead, the others running off to the right in number order;</li>
 *   <li>every shrine's front faces back toward you, and every front edge is {@link #FRONT_DISTANCE} blocks ahead;</li>
 *   <li>{@link #GAP} clear blocks between neighbouring shrines;</li>
 *   <li>beside each entrance, a short stone brick pillar with a waxed sign on its front: number, name, mood and size
 *       (width x depth x height).</li>
 * </ul>
 * The whole row uses one ground level: the block you're standing on. Each design prepares its own ground (it clears
 * its footprint above the ground, fills dips under it down to solid ground, and slopes the ground around it back to
 * the natural terrain; on a superflat world only the footprint is cleared).
 * <p>
 * Each shrine is tens of thousands of blocks, so they are built <b>one at a time</b> from the server tick, never all
 * at once: first the next shrine's chunks are loaded in the background (a chunk ticket, so the server doesn't stop to
 * generate them), then it's built, at most one every {@link #TICKS_BETWEEN_BUILDS} ticks. A chat line appears as each
 * one is finished; click it to teleport in front of that shrine. A shrine that fails to build is logged and reported,
 * and the others are still built. Only one shrine showcase runs at a time.
 * <p>
 * It uses the command's position, rotation and dimension, so it also works from the server console and command
 * blocks, for example {@code execute in minecraft:overworld positioned 0 -60 0 rotated 0 0 run dross showcase shrines}.
 * It builds wherever it's run and never cleans up.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class ShrineShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from where you stand to the front edge of every shrine. */
    private static final int FRONT_DISTANCE = 24;
    /** Clear blocks between neighbouring shrines. */
    private static final int GAP = 32;
    /** At most one shrine is built every this many server ticks (20 ticks = 1 second), so the server keeps up. */
    private static final int TICKS_BETWEEN_BUILDS = 10;
    /** Clicking a shrine's chat line puts you this many blocks in front of its entrance, facing it... */
    private static final int VIEW_DISTANCE = 20;
    /** ...looking this many degrees up (negative is up), so more of the shrine is in view. */
    private static final float VIEW_PITCH = -10.0F;
    /** Each sign's pillar stands just outside the shrine's front edge, this many blocks left of the entrance (as you face the shrine). */
    private static final int SIGN_SIDE_OFFSET = 4;
    /** The pillar is this many stone bricks tall; the sign hangs on the front of the top one, at about eye level. */
    private static final int PILLAR_HEIGHT = 2;
    /**
     * Chunks are loaded this many blocks past each shrine's footprint (the band where its ground may be sloped, plus a
     * little), so building it never waits for a chunk.
     */
    private static final int LOAD_MARGIN = ShrineDesign.GROUND_MARGIN + 2;
    /** If a shrine's chunks still aren't loaded after this many ticks (1 minute), it's built anyway (the server then loads the rest itself). */
    private static final int LOAD_TIMEOUT_TICKS = 60 * 20;
    /** A built shrine's chunks stay loaded this many more ticks (5 seconds), so its lighting is finished before they're saved and unloaded. */
    private static final int HOLD_AFTER_BUILD_TICKS = 5 * 20;

    /**
     * Keeps a shrine's chunks loaded while it's built. Every shrine gets its own key ({@link #nextTicketKey}), so two
     * shrines that share a chunk never remove each other's ticket.
     */
    private static final TicketType<Long> LOAD_TICKET = TicketType.create("dross_shrine_showcase", Long::compare);

    /** The showcase being built, or null. Only touched on the server thread (commands and server ticks run there). */
    @Nullable
    private static Job current;
    /** Chunk tickets of shrines that are already built, removed a little later (see {@link #HOLD_AFTER_BUILD_TICKS}). */
    private static final List<Hold> HOLDS = new ArrayList<>();
    private static long nextTicketKey;

    /** Runs {@code /dross showcase shrines}: lays out the row, then the shrines are built one by one from the server tick. */
    public static int buildShrines(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        List<ShrineDesign> designs = ShrineDesigns.ALL;
        if (designs.isEmpty())
        {
            source.sendFailure(Component.literal("There are no shrine designs to show."));
            return 0;
        }
        Job running = current;
        if (running != null)
        {
            source.sendFailure(Component.literal("A shrine showcase is already being built (" + running.next + " of "
                    + running.placements.size() + " done). Wait for it to finish, then try again."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y); // where the command looks: north, south, east or west
        Direction facing = look.getOpposite();  // every shrine's front faces back toward the start
        Direction right = look.getClockWise();  // the row runs off to the right
        BlockPos origin = new BlockPos(Mth.floor(position.x), groundY(level, position, designs), Mth.floor(position.z));
        List<Placement> placements = layOut(designs, origin, look, facing, right);

        current = new Job(source, level, facing, placements, source.getServer().getTickCount());
        Placement first = placements.get(0);
        Placement last = placements.get(placements.size() - 1);
        LOGGER.info("Shrine showcase: building {} designs one at a time for {} in {}: ground y {}, No. {} centred at {}, "
                        + "the row running {} to No. {} centred at {}, fronts facing {}",
                placements.size(), source.getTextName(), level.dimension().location(), origin.getY(),
                first.design().number(), first.center().toShortString(), right.getName(),
                last.design().number(), last.center().toShortString(), facing.getName());
        int count = placements.size();
        source.sendSuccess(() -> Component.literal("Building " + count + " shrine designs in a row to your right, one at a"
                + " time (No. " + first.design().number() + " straight ahead). Click a line below to go to one."), true);
        return count;
    }

    // ---------------------------------------------------------------- the row

    /**
     * Where each shrine goes: No. 1 straight ahead, the others side by side to its right, {@link #GAP} blocks apart,
     * every front edge {@link #FRONT_DISTANCE} blocks ahead. Sizes come from each design's own footprint, so turning
     * the shrines never has to be worked out here.
     */
    private static List<Placement> layOut(List<ShrineDesign> designs, BlockPos origin, Direction look, Direction facing, Direction right)
    {
        Direction left = right.getOpposite();
        List<Placement> placements = new ArrayList<>();
        int leftEdge = 0; // where the next shrine's left edge goes, in blocks to the right of the start
        for (int i = 0; i < designs.size(); i++)
        {
            ShrineDesign design = designs.get(i);
            // Measure the shrine as if its center were on the start, then slide it into its place in the row.
            BoundingBox measured = design.footprint(origin, facing);
            Span across = Span.of(measured, origin, right);
            Span ahead = Span.of(measured, origin, look);
            int sideways = i == 0 ? 0 : leftEdge - across.min(); // the first one is straight ahead
            BlockPos center = origin.relative(right, sideways).relative(look, FRONT_DISTANCE - ahead.min());
            leftEdge = sideways + across.max() + 1 + GAP;

            BoundingBox footprint = design.footprint(center, facing);
            BlockPos entrance = design.entrance(center, facing); // a ground block, just outside the front edge
            BlockPos pillar = entrance.relative(left, SIGN_SIDE_OFFSET).above(); // the pillar's bottom block
            BlockPos sign = pillar.above(PILLAR_HEIGHT - 1).relative(facing); // on the front of its top block
            BlockPos view = entrance.relative(facing, VIEW_DISTANCE).above(); // where you stand (height checked later)
            ChunkArea chunks = ChunkArea.covering(footprint.inflatedBy(LOAD_MARGIN), pillar, sign, view);
            placements.add(new Placement(design, center, footprint, pillar, sign, view, chunks));
        }
        return placements;
    }

    /** The row's ground level (see {@link Showcases#groundY}), kept high enough for every design's underground layers. */
    private static int groundY(ServerLevel level, Vec3 position, List<ShrineDesign> designs)
    {
        int deepest = 0;
        for (ShrineDesign design : designs)
        {
            deepest = Math.max(deepest, design.groundLayer());
        }
        return Math.max(Showcases.groundY(level, position), level.getMinBuildHeight() + deepest);
    }

    // ---------------------------------------------------------------- building, one shrine at a time

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || (current == null && HOLDS.isEmpty()))
        {
            return;
        }
        long now = event.getServer().getTickCount();
        releaseHolds(now);
        Job job = current;
        if (job == null)
        {
            return;
        }
        try
        {
            step(job, now);
        }
        catch (RuntimeException e)
        {
            // An error here must never crash the server: give up on this showcase instead.
            current = null;
            if (job.ticketArea != null)
            {
                job.ticketArea.removeTickets(job.level, job.ticketKey);
                job.ticketArea = null;
            }
            LOGGER.error("Shrine showcase: stopped by an error after {} of {} designs", job.next, job.placements.size(), e);
            failure(job, "The shrine showcase stopped because of an error after " + job.next + " of "
                    + job.placements.size() + " designs; see the game log.");
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        Job job = current;
        if (job != null)
        {
            LOGGER.info("Shrine showcase: the server stopped after {} of {} designs; the rest weren't built",
                    job.next, job.placements.size());
        }
        // The chunk tickets go away with the server.
        current = null;
        HOLDS.clear();
    }

    /**
     * One server tick of a showcase: asks for the next shrine's chunks if that hasn't happened yet, and builds the
     * shrine once they're loaded and its turn has come.
     */
    private static void step(Job job, long now)
    {
        Placement placement = job.placements.get(job.next);
        if (job.ticketArea == null)
        {
            requestChunks(job, placement, now);
        }
        if (now < job.notBefore)
        {
            return;
        }
        boolean ready = placement.chunks().allLoaded(job.level);
        if (!ready && now - job.requestedAt < LOAD_TIMEOUT_TICKS)
        {
            return;
        }
        if (!ready)
        {
            LOGGER.warn("Shrine showcase: the chunks for No. {} ({}) still weren't loaded after {} seconds; building it anyway",
                    placement.design().number(), placement.design().name(), LOAD_TIMEOUT_TICKS / 20);
        }

        build(job, placement);
        // Its chunks stay loaded a little longer (lighting), then unload normally.
        HOLDS.add(new Hold(job.level, job.ticketArea, job.ticketKey, now + HOLD_AFTER_BUILD_TICKS));
        job.ticketArea = null;
        job.next++;
        if (job.next >= job.placements.size())
        {
            finish(job, now);
            return;
        }
        job.notBefore = now + TICKS_BETWEEN_BUILDS;
        // Start loading the next shrine's chunks straight away, so they're ready when its turn comes.
        requestChunks(job, job.placements.get(job.next), now);
    }

    private static void requestChunks(Job job, Placement placement, long now)
    {
        long key = nextTicketKey++;
        placement.chunks().addTickets(job.level, key);
        job.ticketArea = placement.chunks();
        job.ticketKey = key;
        job.requestedAt = now;
    }

    private static void releaseHolds(long now)
    {
        Iterator<Hold> it = HOLDS.iterator();
        while (it.hasNext())
        {
            Hold hold = it.next();
            if (now >= hold.releaseAt())
            {
                hold.chunks().removeTickets(hold.level(), hold.key());
                it.remove();
            }
        }
    }

    /** Builds one shrine and its sign, checks its frame, and sends its chat line (clickable once it's built). */
    private static void build(Job job, Placement p)
    {
        ShrineDesign design = p.design();
        ServerLevel level = job.level;
        String line = design.number() + ". " + design.name() + " (" + design.mood() + "), " + size(design);
        warnIfTemplateSizeDiffers(level, design, p.footprint());
        boolean built;
        try
        {
            built = design.build(level, p.center(), job.facing);
        }
        catch (RuntimeException e)
        {
            built = false;
            LOGGER.error("Shrine showcase: No. {} ({}) failed to build at {}", design.number(), design.name(),
                    p.center().toShortString(), e);
        }
        if (!built)
        {
            job.failed.add(design);
            failure(job, line + ": failed to build, see the game log");
            return;
        }

        try
        {
            placeSign(level, p, job.facing);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Shrine showcase: couldn't put up the sign for No. {} at {}", design.number(), p.sign().toShortString(), e);
        }
        String frame = checkFrame(level, design, p.center(), job.facing);
        BlockPos view = standingSpot(level, p.view());
        LOGGER.info("Shrine showcase: No. {} {} done ({} of {}): centre {}, {}, sign {}, viewpoint {}",
                design.number(), design.name(), job.next + 1, job.placements.size(), p.center().toShortString(), frame,
                p.sign().toShortString(), view.toShortString());
        success(job, link(line, design, level, view, job.facing.getOpposite().toYRot()), false);
    }

    /** A short stone brick pillar just outside the front edge, beside the entrance, with the waxed sign on its front. */
    private static void placeSign(ServerLevel level, Placement p, Direction facing)
    {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
        for (int i = 0; i < PILLAR_HEIGHT; i++)
        {
            level.setBlock(p.pillar().above(i), stone, Block.UPDATE_ALL);
        }
        ShrineDesign design = p.design();
        if (!Showcases.placeWallSign(level, p.sign(), facing, "No. " + design.number(), design.name(), design.mood(), size(design)))
        {
            LOGGER.warn("Shrine showcase: couldn't put the sign for No. {} at {}", design.number(), p.sign().toShortString());
        }
    }

    /**
     * Checks the built shrine with the portal area's own frame test (the one the Rift Key uses): there should be a
     * complete, empty Dross frame around the opening {@link ShrineDesign#frameOpeningCenter} points at.
     *
     * @return a short note for the log
     */
    private static String checkFrame(ServerLevel level, ShrineDesign design, BlockPos center, Direction facing)
    {
        BlockPos opening = design.frameOpeningCenter(center, facing);
        // Templates are made facing south; turning one to face east or west swaps its X and Z.
        Direction.Axis expected = facing.getAxis() == Direction.Axis.X
                ? (design.frameAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X)
                : design.frameAxis();
        Optional<DrossPortalShape> found = DrossPortalShape.findEmptyPortalShape(level, opening, expected);
        if (found.isEmpty())
        {
            LOGGER.warn("Shrine showcase: No. {} ({}) has no complete, empty Dross portal frame around {} after building",
                    design.number(), design.name(), opening.toShortString());
            return "NO valid frame around " + opening.toShortString();
        }
        DrossPortalShape shape = found.get();
        if (shape.getAxis() != expected || shape.getWidth() != design.openingWidth() || shape.getHeight() != design.openingHeight())
        {
            LOGGER.warn("Shrine showcase: No. {} ({}): its frame opening is {}x{} along {}, but ShrineDesigns says {}x{} along {}",
                    design.number(), design.name(), shape.getWidth(), shape.getHeight(), shape.getAxis().getName(),
                    design.openingWidth(), design.openingHeight(), expected.getName());
        }
        return "frame OK (opening " + shape.getWidth() + "x" + shape.getHeight() + " along "
                + shape.getAxis().getName().toUpperCase(Locale.ROOT) + ", lowest corner " + shape.getMinCorner().toShortString() + ")";
    }

    /**
     * Warns in the log if a template's real size doesn't match the numbers in {@code ShrineDesigns} (which the
     * generator writes): then the shrine would sit off-centre, and its frame and entrance wouldn't be where expected.
     */
    private static void warnIfTemplateSizeDiffers(ServerLevel level, ShrineDesign design, BoundingBox footprint)
    {
        Optional<StructureTemplate> template = level.getStructureManager().get(design.template());
        if (template.isEmpty())
        {
            return; // build() reports a missing template itself
        }
        Vec3i size = template.get().getSize();
        if (size.getX() != design.width() || size.getY() != footprint.getYSpan() || size.getZ() != design.depth())
        {
            LOGGER.warn("Shrine showcase: No. {} ({}): the template {} is {} x {} x {} (width x layers x depth), but "
                            + "ShrineDesigns says {} x {} x {}, so it won't line up; re-run the generator in tools/shrines",
                    design.number(), design.name(), design.template(), size.getX(), size.getY(), size.getZ(),
                    design.width(), footprint.getYSpan(), design.depth());
        }
    }

    /** Where you stand at a viewpoint: on top of the ground there (on a flat world, the row's own ground). */
    private static BlockPos standingSpot(ServerLevel level, BlockPos view)
    {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, view.getX(), view.getZ());
        return top > level.getMinBuildHeight() ? new BlockPos(view.getX(), top, view.getZ()) : view;
    }

    /** A built shrine's chat line. Clicking it teleports you to its viewpoint (in its dimension), facing the shrine. */
    private static Component link(String line, ShrineDesign design, ServerLevel level, BlockPos view, float yaw)
    {
        String command = String.format(Locale.ROOT, "/execute in %s run tp @s %.1f %d %.1f %.0f %.0f",
                level.dimension().location(), view.getX() + 0.5D, view.getY(), view.getZ() + 0.5D, yaw, VIEW_PITCH);
        Component hover = Component.literal("Click to teleport in front of No. " + design.number() + ", " + design.name()
                + " (" + view.getX() + ", " + view.getY() + ", " + view.getZ() + ")");
        return Component.literal(line).withStyle(style -> style
                .withColor(DrossColors.SHOWCASE_LINK)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static void finish(Job job, long now)
    {
        current = null;
        int total = job.placements.size();
        int built = total - job.failed.size();
        long seconds = Math.max(1L, Math.round((now - job.startTick) / 20.0D));
        String failed = job.failed.stream().map(d -> "No. " + d.number()).collect(Collectors.joining(", "));
        LOGGER.info("Shrine showcase: finished, built {} of {} designs in {} s{}", built, total, seconds,
                job.failed.isEmpty() ? "" : " (failed: " + failed + ")");
        if (job.failed.isEmpty())
        {
            success(job, Component.literal("Shrine showcase finished: built all " + total + " designs in about " + seconds
                    + " s. Click a numbered line above to go to one."), true);
        }
        else
        {
            failure(job, "Shrine showcase finished: built " + built + " of " + total + " designs in about " + seconds
                    + " s (" + failed + " failed, see the game log). Click a numbered line above to go to one.");
        }
    }

    // ---------------------------------------------------------------- messages

    private static void success(Job job, Component message, boolean tellAdmins)
    {
        if (canHear(job.source))
        {
            job.source.sendSuccess(() -> message, tellAdmins);
        }
    }

    private static void failure(Job job, String message)
    {
        if (canHear(job.source))
        {
            job.source.sendFailure(Component.literal(message));
        }
    }

    /** False once the player who ran the command has left (the log still gets every line). */
    private static boolean canHear(CommandSourceStack source)
    {
        return !(source.getEntity() instanceof ServerPlayer player) || !player.hasDisconnected();
    }

    /** "width x depth x height", for example "63 x 73 x 31". */
    private static String size(ShrineDesign design)
    {
        return design.width() + " x " + design.depth() + " x " + design.height();
    }

    // ---------------------------------------------------------------- small types

    /** One run of the command: where everything goes, and how far it has got. */
    private static final class Job
    {
        final CommandSourceStack source;
        final ServerLevel level;
        final Direction facing;
        final List<Placement> placements;
        final long startTick;
        final List<ShrineDesign> failed = new ArrayList<>();
        /** The next shrine to build (an index into {@link #placements}). */
        int next;
        /** The chunks currently held for it, or null while they haven't been asked for. */
        @Nullable
        ChunkArea ticketArea;
        /** The key of those chunk tickets. */
        long ticketKey;
        /** When they were asked for. */
        long requestedAt;
        /** It isn't built before this tick. */
        long notBefore;

        Job(CommandSourceStack source, ServerLevel level, Direction facing, List<Placement> placements, long startTick)
        {
            this.source = source;
            this.level = level;
            this.facing = facing;
            this.placements = placements;
            this.startTick = startTick;
            this.notBefore = startTick;
        }
    }

    /**
     * Where one shrine goes, worked out before anything is built.
     *
     * @param pillar the bottom block of its sign's pillar
     * @param sign   where the sign hangs
     * @param view   where the chat line's teleport stands you (its height is checked once the shrine is built)
     * @param chunks every chunk the shrine, its sign and its viewpoint touch
     */
    private record Placement(ShrineDesign design, BlockPos center, BoundingBox footprint, BlockPos pillar, BlockPos sign,
                             BlockPos view, ChunkArea chunks) {}

    /** Chunk tickets to remove at {@code releaseAt}. */
    private record Hold(ServerLevel level, ChunkArea chunks, long key, long releaseAt) {}

    /** A rectangle of chunks, inclusive. */
    private record ChunkArea(int minX, int minZ, int maxX, int maxZ)
    {
        /** Every chunk under the box and the extra positions. */
        static ChunkArea covering(BoundingBox box, BlockPos... more)
        {
            int minX = box.minX();
            int minZ = box.minZ();
            int maxX = box.maxX();
            int maxZ = box.maxZ();
            for (BlockPos pos : more)
            {
                minX = Math.min(minX, pos.getX());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new ChunkArea(SectionPos.blockToSectionCoord(minX), SectionPos.blockToSectionCoord(minZ),
                    SectionPos.blockToSectionCoord(maxX), SectionPos.blockToSectionCoord(maxZ));
        }

        /** Asks for every chunk to be loaded (generated if it's new) in the background, and kept loaded. */
        void addTickets(ServerLevel level, long key)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    level.getChunkSource().addRegionTicket(LOAD_TICKET, new ChunkPos(x, z), 0, key);
                }
            }
        }

        /** Lets the chunks unload normally again (they're saved first). */
        void removeTickets(ServerLevel level, long key)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    level.getChunkSource().removeRegionTicket(LOAD_TICKET, new ChunkPos(x, z), 0, key);
                }
            }
        }

        /**
         * True once every chunk is fully loaded. ({@code getChunkNow}, not {@code hasChunk}: that only says a chunk
         * has a ticket, not that it's ready.) Server thread only.
         */
        boolean allLoaded(ServerLevel level)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    if (level.getChunkSource().getChunkNow(x, z) == null)
                    {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /** Where a box starts and ends along a horizontal direction, in blocks from a point (negative = the other way). */
    private record Span(int min, int max)
    {
        static Span of(BoundingBox box, BlockPos from, Direction direction)
        {
            boolean alongX = direction.getAxis() == Direction.Axis.X;
            int step = alongX ? direction.getStepX() : direction.getStepZ();
            int start = alongX ? from.getX() : from.getZ();
            int a = ((alongX ? box.minX() : box.minZ()) - start) * step;
            int b = ((alongX ? box.maxX() : box.maxZ()) - start) * step;
            return new Span(Math.min(a, b), Math.max(a, b));
        }
    }

    private ShrineShowcase() {}
}
