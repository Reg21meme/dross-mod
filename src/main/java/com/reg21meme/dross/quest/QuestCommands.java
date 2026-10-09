package com.reg21meme.dross.quest;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reg21meme.dross.quest.QuestRequirement.Group;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The logic for the {@code /dross quest} test commands. The world-builder area's {@code DrossCommand}
 * (the /dross root) hooks them up, for example:
 * <pre>
 * .then(Commands.literal("quest")
 *         .requires(source -> source.hasPermission(2))
 *         .then(Commands.literal("status").executes(QuestCommands::status))
 *         .then(Commands.literal("reset").executes(QuestCommands::reset))
 *         .then(Commands.literal("complete").executes(QuestCommands::complete)))
 * </pre>
 * Each works on the player running the command. The overloads that take a {@link ServerPlayer}
 * are there in case a player argument is added later.
 */
public final class QuestCommands
{
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

    /** {@code /dross quest complete}: marks your whole quest as done, as if the Rift Key had been given. */
    public static int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        return complete(context.getSource(), context.getSource().getPlayerOrException());
    }

    public static int status(CommandSourceStack source, ServerPlayer player)
    {
        QuestProgress.Stage stage = QuestProgress.getStage(player);
        MutableComponent text = Component.literal("Dross quest progress for ").append(player.getDisplayName()).append(":");
        line(text, "Stage: " + switch (stage)
        {
            case TRIBUTES -> "bringing the tributes";
            case KEY_MATERIALS -> "bringing the key materials";
            case COMPLETE -> "complete";
        });
        line(text, "Weathered Letter found: " + yesNo(QuestProgress.hasFoundLetter(player)));
        line(text, "Trader's opening line spoken: " + yesNo(QuestProgress.hasSpokenIntro(player)));
        text.append("\n Tributes:");
        for (QuestRequirement requirement : QuestRequirement.inGroup(Group.TRIBUTE))
        {
            requirementLine(text, player, requirement);
        }
        line(text, "Compass earned (Proven Worthy): " + yesNo(QuestProgress.hasEarnedCompass(player)));
        text.append(QuestProgress.isQuestComplete(player) ? "\n Key materials (towards a replacement key):" : "\n Key materials:");
        for (QuestRequirement requirement : QuestRequirement.inGroup(Group.KEY_MATERIAL))
        {
            requirementLine(text, player, requirement);
        }
        line(text, "Rift Key given (Keymaster): " + yesNo(QuestProgress.isQuestComplete(player))
                + " (keys forged: " + QuestProgress.getKeysForged(player) + ")");
        line(text, "Dross Guide Book given: " + yesNo(QuestProgress.hasReceivedGuideBook(player)));
        source.sendSuccess(() -> text, false);
        return 1;
    }

    public static int reset(CommandSourceStack source, ServerPlayer player)
    {
        QuestProgress.reset(player);
        source.sendSuccess(() -> Component.literal("Reset the Dross quest for ").append(player.getDisplayName())
                .append(" (progress, Guide Book flag and quest advancements). Items they hold were not taken."), true);
        return 1;
    }

    public static int complete(CommandSourceStack source, ServerPlayer player)
    {
        QuestProgress.completeAll(player);
        source.sendSuccess(() -> Component.literal("Completed the Dross quest for ").append(player.getDisplayName())
                .append(" (as if the Rift Key was given; no items given)."), true);
        return 1;
    }

    private static void requirementLine(MutableComponent text, ServerPlayer player, QuestRequirement requirement)
    {
        text.append("\n   [" + (QuestProgress.hasHandedIn(player, requirement) ? "x" : " ") + "] ")
                .append(requirement.describe());
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
