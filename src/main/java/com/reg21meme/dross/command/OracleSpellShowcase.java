package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reg21meme.dross.quest.oracle.OracleSpell;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /dross showcase oracle_spell} and {@code /dross showcase barrier_fall} (the user, November 18, 2026: "include
 * a thing where I can see the animation from like a showcase ... so I can give feedback"). The quest area's
 * {@link OracleSpell} does the work; nothing about anyone's quest changes.
 */
public final class OracleSpellShowcase
{
    private OracleSpellShowcase() {}

    static int spell(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean byOracle = OracleSpell.showcase(player);
        context.getSource().sendSuccess(() -> Component.translatable(byOracle
                ? "commands.dross.showcase.oracle_spell.oracle" : "commands.dross.showcase.oracle_spell.ground"), false);
        return 1;
    }

    static int barrierFall(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        ServerPlayer player = context.getSource().getPlayerOrException();
        OracleSpell.showcaseBarrierFall(player);
        context.getSource().sendSuccess(() -> Component.translatable("commands.dross.showcase.barrier_fall"), false);
        return 1;
    }
}
