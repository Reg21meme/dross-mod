package com.reg21meme.dross.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.BossCommands;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlock;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless test (GameTest) for the {@code /dross} command names and {@code /dross showcase trophies colossus}. Run it
 * with {@code .\gradlew.bat runGameTestServer --console=plain}. It only exists in the development environment.
 * <ul>
 *   <li><b>Names:</b> every command in the naming scheme ({@code goto <place>}, {@code goto biome <name>} and
 *       {@code goto lair <boss>}, {@code showcase <kind> [<boss>]}, {@code boss <action> <boss>}, {@code trader <action>},
 *       {@code quest <action>}) parses to a runnable command, including the chat buttons' commands (the golem showcase's
 *       controls and the arena's summon button), and {@code goto biome} or {@code goto lair} on its own, or with a
 *       name that isn't a Dross biome or a boss with a lair, doesn't. Running
 *       {@code goto biome} is checked by {@link GotoBiomeGameTest}, {@code goto lair colossus} by
 *       {@link GotoLairGameTest}.</li>
 *   <li><b>Trophy showcase:</b> run far from everything else ({@link #SHOWCASE_X}, {@link #SHOWCASE_Z}), looking
 *       south, it places the 5 trophies in a row, each on a showcase pedestal, facing back north (toward the command),
 *       with a sign "No. N" and the design's name in front. Run again 10 blocks further north, the old row is gone
 *       and there are still exactly 5. Run a third time from the same spot but standing on a block high in the air
 *       (at least {@link #THIRD_RUN_UP} blocks above the old row: steep ground, made sure of), the old row far below
 *       is gone too, while a trophy a "player" put on display nearby (on the same kind of block, without a showcase
 *       sign) stays. Trophies are counted at every height, so uneven ground never fools the test. The test removes
 *       its trophies, pedestals, signs and the floor built in the air afterwards.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class DrossCommandGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Every command that must exist (without the slash). The controls and buttons are added from their own code. */
    private static final String[] NEW_COMMANDS = {
            "dross goto cathedral", "dross goto hub", "dross goto trader",
            "dross goto biome silverwood_forest", "dross goto biome cinder_wastes", "dross goto biome blightwood",
            "dross goto biome rimefall", "dross goto biome gravecrags",
            "dross goto lair colossus", "dross goto lair ratking", "dross goto sewer", "dross goto lake",
            "dross showcase bossbars huntsman", "dross showcase bossbars huntsman angry",
            "dross showcase hunter_villages", "dross showcase hunter_igloos", "dross showcase boss_designs huntsman",
            "dross showcase boss_designs huntsman ride_gallop", "dross showcase boss_designs huntsman spear_combo",
            "dross showcase boss_designs huntsman rear",
            "dross trader sendhome",
            "dross quest status", "dross quest reset", "dross quest complete",
            "dross showcase boss_designs colossus", "dross showcase trophies colossus", "dross showcase trophies ratking",
            "dross showcase boss_designs ratking", "dross showcase rats", "dross showcase boss_sounds ratking",
            "dross showcase boss_designs rimefall", "dross showcase rimefall_mobs", "dross showcase mob_sounds rimefall",
            "dross boss summon ratking", "dross boss arena ratking", "dross boss summon huntsman", "dross boss arena huntsman",
            "dross boss summon colossus", "dross boss arena colossus",
    };
    /** Unfinished or unknown commands, which must not run (the Nether Scar biome was removed on October 17, 2026). */
    private static final String[] NOT_COMMANDS = {
            "dross goto biome", "dross goto biome not_a_biome", "dross goto biome dross:gravecrags", "dross goto biome nether_scar",
            "dross goto lair", "dross goto lair cinder_colossus", "dross goto colossus",
    };

    /** Where the trophy showcase is tried: far from spawn, the site and the other tests. */
    private static final int SHOWCASE_X = -2000;
    private static final int SHOWCASE_Z = -2000;
    /** The second run is this many blocks further north, so its cleared floor doesn't reach the first row. */
    private static final int SECOND_RUN_BACK = 10;
    /**
     * The third run stands on a block at least this many blocks above the second row (the showcase once looked only 8
     * blocks up and down for an old row, so this failed)...
     */
    private static final int THIRD_RUN_UP = 20;
    /** ...and at least this many above every block under its floor, so its floor (built in the air) never touches the land. */
    private static final int THIRD_RUN_CLEARANCE = 12;
    /**
     * The third run's floor, built in the air, lies within this box round its spot, which the test clears afterwards:
     * this many blocks to each side (the floor reaches 8)...
     */
    private static final int AIR_FLOOR_SIDE = 10;
    /** ...from this many blocks behind the spot to this many ahead of it (the floor reaches 6 ahead)... */
    private static final int AIR_FLOOR_BEHIND = 2;
    private static final int AIR_FLOOR_AHEAD = 9;
    /** ...and this many blocks below and above the spot's block (grass and up to 4 dirt under it; the trophies at +2). */
    private static final int AIR_FLOOR_HEIGHT = 6;
    /** A "player's" own trophy on display (no showcase sign) stands this many blocks to the side of the third run's spot. */
    private static final int DISPLAY_SIDE = 9;
    /** Trophies are looked for this many blocks round the showcase spots (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 20;

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_commands")
    public static void commandNames(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        CommandSourceStack source = server.createCommandSourceStack().withPermission(2).withSuppressedOutput();

        List<String> expected = new ArrayList<>(List.of(NEW_COMMANDS));
        for (GolemShowcase.Control control : GolemShowcase.Control.values())
        {
            expected.add(control.command().substring(1));
        }
        for (RatKingShowcase.Control control : RatKingShowcase.Control.values())
        {
            expected.add(control.command().substring(1));
        }
        for (RatShowcase.Control control : RatShowcase.Control.values())
        {
            expected.add(control.command().substring(1));
        }
        for (RimefallMobShowcase.Control control : RimefallMobShowcase.Control.values())
        {
            expected.add(control.command().substring(1));
        }
        expected.add(BossCommands.SUMMON_COMMAND.substring(1));
        expected.add(BossCommands.SUMMON_RAT_KING_COMMAND.substring(1));
        for (String biome : DrossBiomes.names()) // every Dross biome has its goto biome literal, a future one too
        {
            expected.add("dross goto biome " + biome);
        }
        for (String command : expected)
        {
            if (!runnable(dispatcher, source, command))
            {
                throw new GameTestAssertException("/" + command + " should be a command, but isn't");
            }
        }
        for (String command : NOT_COMMANDS)
        {
            if (runnable(dispatcher, source, command))
            {
                throw new GameTestAssertException("/" + command + " should not run (unfinished or unknown), but it does");
            }
        }
        LOGGER.info("[command test] {} commands parse, {} unfinished or unknown ones don't", expected.size(), NOT_COMMANDS.length);
        helper.succeed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_commands")
    public static void trophyShowcase(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos first = new BlockPos(SHOWCASE_X, 0, SHOWCASE_Z);
        BlockPos second = first.north(SECOND_RUN_BACK);
        List<BlockPos> placed = new ArrayList<>();
        BoundingBox airFloor = null;
        try
        {
            int result = runShowcase(server, overworld, first, surface(overworld, first));
            List<BlockPos> row = findTrophies(overworld, first);
            placed.addAll(row);
            checkRow(overworld, row, result, "first run");

            result = runShowcase(server, overworld, second, surface(overworld, second));
            row = findTrophies(overworld, first);
            placed.addAll(row);
            checkRow(overworld, row, result, "second run, 10 blocks further north");
            for (BlockPos trophy : row)
            {
                if (trophy.getZ() > second.getZ() + SECOND_RUN_BACK / 2)
                {
                    throw new GameTestAssertException("second run: a trophy of the old row is still at " + trophy.toShortString());
                }
            }

            // The steep-ground case, made sure of: the same spot, but standing on a block high above the old row.
            BoundingBox columns = BoundingBox.fromCorners(second.offset(-AIR_FLOOR_SIDE, 0, -AIR_FLOOR_BEHIND),
                    second.offset(AIR_FLOOR_SIDE, 0, AIR_FLOOR_AHEAD));
            int oldRowY = row.get(0).getY();
            int high = Math.max(oldRowY + THIRD_RUN_UP, highestBlock(overworld, columns) + THIRD_RUN_CLEARANCE);
            if (high + AIR_FLOOR_HEIGHT >= overworld.getMaxBuildHeight())
            {
                throw new GameTestAssertException("third run: no room in the sky above " + second.toShortString()
                        + " (the land there reaches y " + (high - THIRD_RUN_CLEARANCE) + ")");
            }
            airFloor = new BoundingBox(columns.minX(), high - AIR_FLOOR_HEIGHT, columns.minZ(), columns.maxX(),
                    high + AIR_FLOOR_HEIGHT, columns.maxZ());
            overworld.setBlock(second.atY(high), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            // A player's own trophy on display nearby: on the same kind of block, but without a showcase sign.
            BlockPos display = second.offset(DISPLAY_SIDE, 0, 4).atY(high + 2);
            overworld.setBlock(display.below(), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            overworld.setBlock(display, ColossusTrophies.get(1).blockState(Direction.NORTH), Block.UPDATE_CLIENTS);
            placed.add(display);

            result = runShowcase(server, overworld, second, high + 1);
            row = findTrophies(overworld, first);
            if (!row.remove(display))
            {
                throw new GameTestAssertException("third run: it removed a trophy on display that isn't a showcase trophy (no sign)");
            }
            placed.addAll(row);
            String when = "third run, standing " + (high - oldRowY) + " blocks above the old row";
            checkRow(overworld, row, result, when);
            for (BlockPos trophy : row)
            {
                if (trophy.getY() != high + 2)
                {
                    throw new GameTestAssertException(when + ": a trophy is at " + trophy.toShortString()
                            + ", not in the new row at y " + (high + 2) + " (the old row wasn't removed)");
                }
            }
            LOGGER.info("[command test] The trophy showcase placed 5 trophies with signs, and running it again replaced the row, "
                    + "also from {} blocks above it; a trophy on display without a showcase sign stayed", high - oldRowY);
        }
        finally
        {
            cleanUp(overworld, placed);
            if (airFloor != null)
            {
                clear(overworld, airFloor);
            }
        }
        helper.succeed();
    }

    /** True if {@code command} parses completely to a node that runs something. */
    private static boolean runnable(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String command)
    {
        ParseResults<CommandSourceStack> parse = dispatcher.parse(command, source);
        return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && parse.getContext().getCommand() != null;
    }

    /** Runs {@code /dross showcase trophies colossus} at {@code column}, feet at {@code feetY}, looking south. */
    private static int runShowcase(MinecraftServer server, ServerLevel level, BlockPos column, int feetY)
    {
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, feetY, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, "dross showcase trophies colossus");
    }

    /** The first free block above the ground at {@code column} (its chunk is loaded first: unloaded, it says the bottom of the world). */
    private static int surface(ServerLevel level, BlockPos column)
    {
        level.getChunkAt(column);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
    }

    /** Every Colossus trophy round the showcase spots, from the bottom of the world to its top. */
    private static List<BlockPos> findTrophies(ServerLevel level, BlockPos first)
    {
        List<BlockPos> found = new ArrayList<>();
        BlockPos from = first.offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS - SECOND_RUN_BACK).atY(level.getMinBuildHeight());
        BlockPos to = first.offset(SEARCH_RADIUS, 0, SEARCH_RADIUS).atY(level.getMaxBuildHeight() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(from, to))
        {
            if (level.getBlockState(pos).is(ModBlocks.COLOSSUS_TROPHY.get()))
            {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /** The highest block (anything but air: land, water, leaves, an earlier showcase) in the box's columns. */
    private static int highestBlock(ServerLevel level, BoundingBox area)
    {
        int highest = level.getMinBuildHeight();
        for (int x = area.minX(); x <= area.maxX(); x++)
        {
            for (int z = area.minZ(); z <= area.maxZ(); z++)
            {
                level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)); // loaded first
                highest = Math.max(highest, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
            }
        }
        return highest;
    }

    /** Sets every block in the box to air (the third run's floor, built in the air). */
    private static void clear(ServerLevel level, BoundingBox box)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
        {
            if (!level.getBlockState(pos).isAir())
            {
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Exactly one trophy per design, each on a pedestal, facing north, with its sign. */
    private static void checkRow(ServerLevel level, List<BlockPos> row, int result, String when)
    {
        int count = ColossusTrophies.count();
        if (result != count)
        {
            throw new GameTestAssertException(when + ": the command returned " + result + ", expected " + count);
        }
        if (row.size() != count)
        {
            throw new GameTestAssertException(when + ": found " + row.size() + " trophies, expected " + count);
        }
        Set<Integer> designs = new HashSet<>();
        for (BlockPos pos : row)
        {
            BlockState state = level.getBlockState(pos);
            int design = state.getValue(ColossusTrophyBlock.DESIGN);
            designs.add(design);
            if (state.getValue(ColossusTrophyBlock.FACING) != Direction.NORTH)
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + " faces "
                        + state.getValue(ColossusTrophyBlock.FACING) + ", not north (toward the command)");
            }
            if (!level.getBlockState(pos.below()).is(Blocks.CHISELED_POLISHED_BLACKSTONE))
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + " isn't on its pedestal");
            }
            BlockPos signPos = pos.below().north();
            if (!(level.getBlockState(signPos).getBlock() instanceof WallSignBlock)
                    || !(level.getBlockEntity(signPos) instanceof SignBlockEntity sign))
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + " has no sign in front");
            }
            String number = sign.getFrontText().getMessage(0, false).getString();
            Component name = sign.getFrontText().getMessage(1, false);
            if (!number.equals("No. " + design) || !(name.getContents() instanceof TranslatableContents translatable)
                    || !translatable.getKey().equals(ColossusTrophies.get(design).nameKey()))
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + "'s sign says \"" + number + "\" / \""
                        + name.getString() + "\"");
            }
        }
        if (designs.size() != count)
        {
            throw new GameTestAssertException(when + ": the designs aren't all different: " + designs);
        }
    }

    /** Removes what the test placed: each trophy's sign, the trophy and its pedestal. */
    private static void cleanUp(ServerLevel level, List<BlockPos> trophies)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos trophy : trophies)
        {
            if (level.getBlockState(trophy.below().north()).getBlock() instanceof WallSignBlock)
            {
                level.setBlock(trophy.below().north(), air, 3);
            }
            if (level.getBlockState(trophy).is(ModBlocks.COLOSSUS_TROPHY.get()))
            {
                level.setBlock(trophy, air, 3);
            }
            if (level.getBlockState(trophy.below()).is(Blocks.CHISELED_POLISHED_BLACKSTONE))
            {
                level.setBlock(trophy.below(), air, 3);
            }
        }
    }

    private DrossCommandGameTest() {}
}
