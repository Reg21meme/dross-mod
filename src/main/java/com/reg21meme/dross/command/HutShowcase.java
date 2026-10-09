package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.villager.hut.HutDesign;
import com.reg21meme.dross.villager.hut.HutDesigns;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dross showcase huts} (test tool, cheats only): builds every candidate design for the Dross trader's hut
 * (the villager area's {@code villager.hut.HutDesigns}) side by side in front of the player, so they can be
 * compared and one picked.
 * <p>
 * The layout, as the player sees it:
 * <ul>
 *   <li>one row across your view, centered on you, numbered 1 to 10 from left to right;</li>
 *   <li>every door faces you, and every hut's front edge is {@link #FRONT_DISTANCE} blocks in front of you;</li>
 *   <li>{@link #GAP} clear blocks between neighbouring huts;</li>
 *   <li>a waxed oak sign in front of each hut, beside its door: number, name, mood and size
 *       (width x depth x height).</li>
 * </ul>
 * The whole row uses one ground level: the block you're standing on. Each hut first gets a flat pad (grass on top,
 * dirt filling small dips, air above), so the row stays tidy on hills; on a superflat world the pads change almost
 * nothing. It builds wherever you stand and never cleans up, so use a spare flat area.
 */
public final class HutShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from the player to the front edge of every hut. */
    private static final int FRONT_DISTANCE = 10;
    /** Clear blocks between neighbouring huts. */
    private static final int GAP = 6;
    /** How far each hut's flat pad reaches past its footprint, all round. */
    private static final int PAD_MARGIN = 2;
    /** Blocks of air the pad clears above the hut's own height. */
    private static final int PAD_HEADROOM = 2;
    /** The pad fills a dip with dirt at most this many blocks deep. */
    private static final int PAD_MAX_FILL = 4;
    /** The sign stands this many blocks in front of the hut's footprint... */
    private static final int SIGN_DISTANCE = 2;
    /** ...and this many blocks to the left of the door (as you face the hut), so it doesn't block the way in. */
    private static final int SIGN_SIDE_OFFSET = 2;
    /** Pad blocks don't poke their neighbours (no redstone going off), but players still see every change. */
    private static final int PAD_FLAGS = Block.UPDATE_CLIENTS;

    /** Runs {@code /dross showcase huts}. */
    public static int buildHuts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        List<HutDesign> designs = HutDesigns.ALL;
        if (designs.isEmpty())
        {
            source.sendFailure(Component.literal("There are no hut designs to show."));
            return 0;
        }

        Direction look = player.getDirection(); // where the player looks: north, south, east or west
        Direction facing = look.getOpposite();  // every door faces back at the player
        Direction right = look.getClockWise();  // the row runs from the player's left to their right
        Direction left = right.getOpposite();
        BlockPos origin = new BlockPos(player.getBlockX(), groundY(level, player), player.getBlockZ());

        List<BlockPos> centers = layOut(designs, origin, look, facing, right);
        List<BlockPos> signs = new ArrayList<>();
        for (int i = 0; i < designs.size(); i++)
        {
            // The sign's ground block: in front of the footprint, beside the door.
            signs.add(designs.get(i).entrance(centers.get(i), facing)
                    .relative(facing, SIGN_DISTANCE - 1)
                    .relative(left, SIGN_SIDE_OFFSET));
        }

        // 1. Every pad first, so a pad can never cut into a hut that's already built.
        for (int i = 0; i < designs.size(); i++)
        {
            flattenPad(level, designs.get(i).footprint(centers.get(i), facing), signs.get(i));
        }
        // 2. The huts. A broken design is logged and skipped, so the others still get built.
        List<HutDesign> failed = new ArrayList<>();
        for (int i = 0; i < designs.size(); i++)
        {
            HutDesign design = designs.get(i);
            try
            {
                design.build(level, centers.get(i), facing);
            }
            catch (RuntimeException e)
            {
                failed.add(design);
                LOGGER.error("Hut showcase: design {} ({}) failed to build at {}",
                        design.number(), design.name(), centers.get(i).toShortString(), e);
            }
        }
        // 3. The signs.
        for (int i = 0; i < designs.size(); i++)
        {
            placeSign(level, designs.get(i), signs.get(i).above(), facing);
        }

        int built = designs.size() - failed.size();
        LOGGER.info("Hut showcase: built {} of {} hut designs in a row in front of {} (ground y {}, doors facing {})",
                built, designs.size(), player.getGameProfile().getName(), origin.getY(), facing.getName());
        String summary = (failed.isEmpty() ? "Built " + built : "Built " + built + " of " + designs.size())
                + " hut designs in a row in front of you, numbered " + designs.get(0).number()
                + " to " + designs.get(designs.size() - 1).number() + " from left to right:";
        source.sendSuccess(() -> Component.literal(summary), true);
        for (HutDesign design : designs)
        {
            String line = design.number() + ". " + design.name() + " (" + design.mood() + "), " + size(design);
            if (failed.contains(design))
            {
                source.sendFailure(Component.literal(line + ": failed to build, see the game log"));
            }
            else
            {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    /**
     * Where each hut's center goes: side by side from the player's left to their right, {@link #GAP} blocks apart,
     * the whole row centered on the player, and every front edge {@link #FRONT_DISTANCE} blocks ahead.
     * Sizes come from each design's own footprint, so turning the huts never has to be worked out here.
     */
    private static List<BlockPos> layOut(List<HutDesign> designs, BlockPos origin, Direction look, Direction facing, Direction right)
    {
        // How long the whole row is, so it can be centered on the player.
        int rowLength = -GAP;
        for (HutDesign design : designs)
        {
            rowLength += Span.of(design.footprint(origin, facing), origin, right).length() + GAP;
        }

        List<BlockPos> centers = new ArrayList<>();
        int leftEdge = -rowLength / 2; // where the next hut's left edge goes, in blocks to the player's right
        for (HutDesign design : designs)
        {
            // Measure the hut as if it stood on the player's spot, then slide it into its place in the row.
            BoundingBox measured = design.footprint(origin, facing);
            Span across = Span.of(measured, origin, right);
            Span ahead = Span.of(measured, origin, look);
            centers.add(origin.relative(right, leftEdge - across.min()).relative(look, FRONT_DISTANCE - ahead.min()));
            leftEdge += across.length() + GAP;
        }
        return centers;
    }

    /**
     * The ground level for the whole row: the block you're standing on (the one whose top is at, or just under,
     * your feet, so slabs, paths and carpets work too). If you're flying, the first solid ground below you instead.
     */
    private static int groundY(ServerLevel level, ServerPlayer player)
    {
        BlockPos underFeet = BlockPos.containing(player.getX(), player.getY() - 0.5D, player.getZ());
        BlockPos.MutableBlockPos pos = underFeet.mutable();
        while (!isSolidGround(level, pos) && pos.getY() > level.getMinBuildHeight())
        {
            pos.move(Direction.DOWN);
        }
        return isSolidGround(level, pos) ? pos.getY() : underFeet.getY();
    }

    /**
     * A flat pad under and around a hut, out to its sign: grass at the ground level, dirt below it down to solid
     * ground (filling small dips), and air above, up to a little over the hut's height.
     */
    private static void flattenPad(ServerLevel level, BoundingBox footprint, BlockPos signGround)
    {
        int groundY = footprint.minY();
        int topY = footprint.maxY() + PAD_HEADROOM;
        int minX = Math.min(footprint.minX() - PAD_MARGIN, signGround.getX());
        int maxX = Math.max(footprint.maxX() + PAD_MARGIN, signGround.getX());
        int minZ = Math.min(footprint.minZ() - PAD_MARGIN, signGround.getZ());
        int maxZ = Math.max(footprint.maxZ() + PAD_MARGIN, signGround.getZ());

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                for (int y = topY; y > groundY; y--)
                {
                    level.setBlock(pos.set(x, y, z), air, PAD_FLAGS);
                }
                level.setBlock(pos.set(x, groundY, z), grass, PAD_FLAGS);
                for (int y = groundY - 1; y >= groundY - PAD_MAX_FILL; y--)
                {
                    if (isSolidGround(level, pos.set(x, y, z)))
                    {
                        break;
                    }
                    level.setBlock(pos, dirt, PAD_FLAGS);
                }
            }
        }
    }

    /** A waxed oak sign whose front faces the player: number, name, mood and size. */
    private static void placeSign(ServerLevel level, HutDesign design, BlockPos pos, Direction facing)
    {
        BlockState state = Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(facing));
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign))
        {
            LOGGER.warn("Hut showcase: couldn't put the sign for design {} at {}", design.number(), pos.toShortString());
            return;
        }
        SignText text = sign.getFrontText()
                .setMessage(0, Component.literal("No. " + design.number()))
                .setMessage(1, Component.literal(design.name()))
                .setMessage(2, Component.literal(design.mood()))
                .setMessage(3, Component.literal(size(design)));
        sign.setText(text, true);
        sign.setWaxed(true); // so a click doesn't open the sign editor
        // Make sure players see the text straight away.
        sign.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /** "width x depth x height", for example "9 x 9 x 8". */
    private static String size(HutDesign design)
    {
        return design.width() + " x " + design.depth() + " x " + design.height();
    }

    /** Something you can stand on: not air, not water or lava, and has a collision box. */
    private static boolean isSolidGround(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty();
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

        int length()
        {
            return max - min + 1;
        }
    }

    private HutShowcase() {}
}
