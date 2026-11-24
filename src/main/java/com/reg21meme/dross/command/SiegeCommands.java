package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.world.CathedralSiege;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /dross siege status|reset|break}: test tools for the Fallen Cathedral's siege (world/CathedralSiege; its five
 * knights and the barrier that keeps players out until they're dead). Cheats on (level 2).
 * <ul>
 *   <li>{@code status}: whether it holds (and how many knights have fallen) or is broken (when, by whom); each knight:
 *       its kind and element, on guard where, not come yet, or fallen.</li>
 *   <li>{@code reset}: it holds again: its knights about now go, and they come back (their spots picked again) the next
 *       time a player is near; "Siege Broken" is taken from you, so you can earn it again.</li>
 *   <li>{@code break}: breaks it at once, its knights about now going; the players near see the barrier fall (nobody gets
 *       "Siege Broken": nobody brought the knights down).</li>
 * </ul>
 */
public final class SiegeCommands
{
    private SiegeCommands() {}

    static int status(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        List<Component> lines = CathedralSiege.status(source.getServer());
        for (Component line : lines)
        {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    static int reset(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        int removed = CathedralSiege.reset(source.getServer());
        if (removed < 0)
        {
            source.sendFailure(Component.literal(DrossCommand.SITE_NOT_PLACED));
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (player != null)
        {
            DrossAdvancements.revoke(player, DrossAdvancements.SIEGE_BROKEN);
        }
        source.sendSuccess(() -> Component.literal("The cathedral's siege holds again" + (removed > 0 ? " (" + removed
                + " of its knights went)" : "") + ": its knights come back when a player is within " + CathedralSiege.COME_RANGE
                + " blocks" + (player != null ? "; \"Siege Broken\" taken from you" : "") + "."), true);
        return 1;
    }

    static int breakSiege(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        if (!CathedralSiege.breakNow(source.getServer()))
        {
            source.sendFailure(Component.literal(CathedralSiege.isBroken(source.getServer()) ? "The cathedral's siege is already broken."
                    : DrossCommand.SITE_NOT_PLACED));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Broke the cathedral's siege: its barrier is down for everyone."), true);
        return 1;
    }
}
