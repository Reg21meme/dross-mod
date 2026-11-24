package com.reg21meme.dross.quest;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleQuest;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import com.reg21meme.dross.quest.oracle.OracleState;
import com.reg21meme.dross.quest.trader.TraderProgress;
import com.reg21meme.dross.quest.trader.TraderState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The logic for the {@code /dross quest} test commands. The world-builder area's {@code DrossCommand}
 * (the /dross root) hooks them up, for example:
 * <pre>
 * .then(Commands.literal("quest")
 *         .requires(source -> source.hasPermission(2))
 *         .then(Commands.literal("status").executes(QuestCommands::status))
 *         .then(Commands.literal("reset").executes(QuestCommands::reset))
 *         .then(Commands.literal("complete").executes(QuestCommands::complete))
 *         .then(Commands.literal("skip").executes(QuestCommands::skip))
 *         .then(Commands.literal("set")
 *                 .then(Commands.argument(QuestCommands.QUEST_ARGUMENT, StringArgumentType.word())
 *                         .suggests(QuestCommands::suggestQuests)
 *                         .executes(QuestCommands::set))))
 * </pre>
 * {@code status}, {@code reset} and {@code complete} cover the Dross Trader's quest ({@code quest.trader});
 * {@code status}, {@code reset}, {@code skip} and {@code set} cover the Blind Oracle's quests too ({@code quest.oracle}).
 * Each works on the player running the command. The overloads that take a {@link ServerPlayer}
 * are there in case a player argument is added later.
 */
public final class QuestCommands
{
    /** The name of {@code /dross quest set}'s argument (a quest id: {@link OracleQuests#ids()}). */
    public static final String QUEST_ARGUMENT = "quest";

    private QuestCommands() {}

    /** {@code /dross quest status}: shows your quest progress. */
    public static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return status(context.getSource(), context.getSource().getPlayerOrException());
    }

    /** {@code /dross quest reset}: clears your quest progress (Guide Book flag included) and takes away the quest advancements. */
    public static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return reset(context.getSource(), context.getSource().getPlayerOrException());
    }

    /** {@code /dross quest complete}: marks the way into the Dross as done, as if the trader had just made you the Portal Orb. */
    public static int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return complete(context.getSource(), context.getSource().getPlayerOrException());
    }

    /** {@code /dross quest skip}: finishes the Oracle quest step you're on and moves on to the next quest. */
    public static int skip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return skip(context.getSource(), context.getSource().getPlayerOrException());
    }

    /** {@code /dross quest set <quest>}: jumps you to that Oracle quest (waiting to be given, nothing counted). */
    public static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return set(context.getSource(), context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, QUEST_ARGUMENT));
    }

    /** Tab completion for {@code /dross quest set}: every Oracle quest's id, in order. */
    public static CompletableFuture<Suggestions> suggestQuests(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder)
    {
        return SharedSuggestionProvider.suggest(OracleQuests.ids(), builder);
    }

    public static int status(CommandSourceStack source, ServerPlayer player)
    {
        MutableComponent text = Component.literal("Dross quest progress for ").append(player.getDisplayName()).append(":");
        line(text, "Weathered Letter found: " + yesNo(QuestProgress.hasFoundLetter(player)));
        traderStatus(text, player);
        line(text, "Dross Guide Book given: " + yesNo(QuestProgress.hasReceivedGuideBook(player)));
        oracleStatus(text, player);
        source.sendSuccess(() -> text, false);
        return 1;
    }

    public static int reset(CommandSourceStack source, ServerPlayer player)
    {
        QuestProgress.reset(player);
        source.sendSuccess(() -> Component.literal("Reset the Dross quest for ").append(player.getDisplayName())
                .append(" (the letter and Guide Book flags, the Dross Trader's and the Blind Oracle's quests and the quest "
                        + "advancements; the bosses' advancements stay). Items they hold were not taken."), true);
        return 1;
    }

    public static int complete(CommandSourceStack source, ServerPlayer player)
    {
        QuestProgress.completeAll(player);
        source.sendSuccess(() -> Component.literal("Completed the Dross quest for ").append(player.getDisplayName())
                .append(" (as if the Dross Trader had just made the Portal Orb; no items given)."), true);
        return 1;
    }

    public static int skip(CommandSourceStack source, ServerPlayer player)
    {
        OracleQuest was = OracleProgress.current(player);
        OracleState wasState = OracleProgress.state(player);
        if (!OracleProgress.skip(player))
        {
            source.sendFailure(Component.literal("Every one of the Blind Oracle's quests is already done for ")
                    .append(player.getDisplayName()).append("."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Skipped " + was.id() + " (" + stateName(wasState) + ") for ")
                .append(player.getDisplayName()).append(". Now: " + oracleLine(player)), true);
        return 1;
    }

    public static int set(CommandSourceStack source, ServerPlayer player, String questId)
    {
        OracleQuest quest = OracleQuests.byId(questId.toLowerCase(Locale.ROOT));
        if (quest == null)
        {
            source.sendFailure(Component.literal("No Oracle quest called \"" + questId + "\". The quests: "
                    + String.join(", ", OracleQuests.ids())));
            return 0;
        }
        OracleProgress.setQuest(player, quest);
        source.sendSuccess(() -> Component.literal("Set ").append(player.getDisplayName())
                .append("'s Oracle quest to " + oracleLine(player)), true);
        return 1;
    }

    private static void traderStatus(MutableComponent text, ServerPlayer player)
    {
        TraderState state = TraderProgress.state(player);
        String round = TraderProgress.round(player) > 1 ? " (round " + TraderProgress.round(player) + ", for a new orb)" : "";
        text.append("\n Dross Trader: " + switch (state)
        {
            case OFFER -> "his offer waiting";
            case RUINS -> "clearing knight ruins, " + TraderProgress.ruinsCleared(player) + " / " + TraderProgress.RUINS_NEEDED + round;
            case READY -> "ruins cleared, the orb to collect" + round;
            case ORB_GIVEN -> "the Portal Orb given";
        });
        line(text, "Marker over him: " + TraderProgress.marker(player).name().toLowerCase(Locale.ROOT));
        line(text, "Portal Orbs made: " + TraderProgress.orbsMade(player) + "; one in the inventory: " + yesNo(TraderProgress.hasOrb(player))
                + "; the cathedral's portal lit: " + yesNo(TraderProgress.isCathedralLit(player.server)));
    }

    private static void oracleStatus(MutableComponent text, ServerPlayer player)
    {
        text.append("\n Blind Oracle: " + oracleLine(player));
        line(text, "Marker over him: " + OracleProgress.marker(player).name().toLowerCase(Locale.ROOT));
    }

    /** "2 into_the_ashes (Into the Ashes), going, kills 1 / 3". */
    private static String oracleLine(ServerPlayer player)
    {
        OracleQuest quest = OracleProgress.current(player);
        OracleState state = OracleProgress.state(player);
        String line = quest.index() + " " + quest.id() + " (" + quest.displayName().getString() + "), " + stateName(state);
        if (quest.kind() == OracleQuest.Kind.KILL && state != OracleState.AVAILABLE)
        {
            line += ", kills " + OracleProgress.kills(player) + " / " + quest.count();
        }
        return line;
    }

    private static String stateName(OracleState state)
    {
        return switch (state)
        {
            case AVAILABLE -> "waiting to be given";
            case ACTIVE -> "going";
            case DONE -> "done, to hand in";
            case FINISHED -> "every quest finished";
        };
    }

    private static void line(MutableComponent text, String line)
    {
        text.append("\n " + line);
    }

    private static String yesNo(boolean value)
    {
        return value ? "yes" : "no";
    }
}
