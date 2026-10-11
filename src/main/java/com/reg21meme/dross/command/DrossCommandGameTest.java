package com.reg21meme.dross.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.BossCommands;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlock;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
 *   <li><b>Names:</b> every command in the naming scheme ({@code goto <place>}, {@code showcase <kind> [<boss>]},
 *       {@code boss <action> <boss>}, {@code trader <action>}, {@code quest <action>}) parses to a runnable command,
 *       including the chat buttons' commands (the golem showcase's controls and the arena's summon button), and none
 *       of the old names do.</li>
 *   <li><b>Trophy showcase:</b> run far from everything else ({@link #SHOWCASE_X}, {@link #SHOWCASE_Z}), looking
 *       south, it places the 5 trophies in a row, each on a showcase pedestal, facing back north (toward the command),
 *       with a sign "No. N" and the design's name in front. Run again 10 blocks further north, the old row is gone
 *       and there are still exactly 5. The test removes its trophies, pedestals and signs afterwards.</li>
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
            "dross trader sendhome",
            "dross quest status", "dross quest reset", "dross quest complete",
            "dross showcase trader_huts", "dross showcase trader_skins", "dross showcase portal_shrines",
            "dross showcase boss_designs colossus", "dross showcase trophies colossus",
            "dross boss summon colossus", "dross boss arena colossus",
    };
    /** The old names (and one unfinished command), which must not run. */
    private static final String[] OLD_COMMANDS = {
            "dross site", "dross hub", "dross trader", "dross trader home", "dross boss arena",
            "dross showcase huts", "dross showcase skins", "dross showcase shrines", "dross showcase golems",
            "dross showcase golems erupt", "dross showcase trophies",
    };

    /** Where the trophy showcase is tried: far from spawn, the site and the other tests. */
    private static final int SHOWCASE_X = -2000;
    private static final int SHOWCASE_Z = -2000;
    /** The second run is this many blocks further north, so its cleared floor doesn't reach the first row. */
    private static final int SECOND_RUN_BACK = 10;
    /** Trophies are looked for this many blocks round the showcase spots (X and Z)... */
    private static final int SEARCH_RADIUS = 20;
    /** ...from this many blocks below the surface (the showcase goes down to the bottom of water)... */
    private static final int SEARCH_BELOW = 48;
    /** ...to this many above it. */
    private static final int SEARCH_ABOVE = 12;

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
        expected.add(BossCommands.SUMMON_COMMAND.substring(1));
        for (String command : expected)
        {
            if (!runnable(dispatcher, source, command))
            {
                throw new GameTestAssertException("/" + command + " should be a command, but isn't");
            }
        }
        for (String command : OLD_COMMANDS)
        {
            if (runnable(dispatcher, source, command))
            {
                throw new GameTestAssertException("/" + command + " should not run (an old name), but it does");
            }
        }
        LOGGER.info("[command test] {} commands parse, {} old names are gone", expected.size(), OLD_COMMANDS.length);
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
        try
        {
            int result = runShowcase(server, overworld, first);
            List<BlockPos> row = findTrophies(overworld, first);
            placed.addAll(row);
            checkRow(overworld, row, result, "first run");

            result = runShowcase(server, overworld, second);
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
            LOGGER.info("[command test] The trophy showcase placed 5 trophies with signs, and running it again replaced the row");
        }
        finally
        {
            cleanUp(overworld, placed);
        }
        helper.succeed();
    }

    /** True if {@code command} parses completely to a node that runs something. */
    private static boolean runnable(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String command)
    {
        ParseResults<CommandSourceStack> parse = dispatcher.parse(command, source);
        return !parse.getReader().canRead() && parse.getExceptions().isEmpty() && parse.getContext().getCommand() != null;
    }

    /** Runs {@code /dross showcase trophies colossus} standing on the ground at {@code column}, looking south. */
    private static int runShowcase(MinecraftServer server, ServerLevel level, BlockPos column)
    {
        int y = surface(level, column);
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, y, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, "dross showcase trophies colossus");
    }

    /** The first free block above the ground at {@code column} (its chunk is loaded first: unloaded, it says the bottom of the world). */
    private static int surface(ServerLevel level, BlockPos column)
    {
        level.getChunkAt(column);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
    }

    /** Every Colossus trophy round the showcase spots. */
    private static List<BlockPos> findTrophies(ServerLevel level, BlockPos first)
    {
        List<BlockPos> found = new ArrayList<>();
        int y = surface(level, first);
        BlockPos from = first.offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS - SECOND_RUN_BACK).atY(y - SEARCH_BELOW);
        BlockPos to = first.offset(SEARCH_RADIUS, 0, SEARCH_RADIUS).atY(y + SEARCH_ABOVE);
        for (BlockPos pos : BlockPos.betweenClosed(from, to))
        {
            if (level.getBlockState(pos).is(ModBlocks.COLOSSUS_TROPHY.get()))
            {
                found.add(pos.immutable());
            }
        }
        return found;
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
