package com.reg21meme.dross.boss.rage;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;

/**
 * The Rage's commands (the user, October 30: "I want to be able, using /dross, to assign @ a player name or @s etc., the
 * player's colour, so they can fight alongside or against mobs"; World site wires them into {@code /dross rage}):
 * <ul>
 *   <li>{@code /dross rage team <targets> <blue|red|green|yellow|purple>} ({@link #team}): any players and mobs a
 *       selector finds ({@code @s}, a player's name, {@code @p}, {@code @a}, {@code @e[type=zombie,distance=..10]}...)
 *       go onto that team: a mob is raged onto it as by the wand's hit ({@link Rage#enrage}), a player is put on it
 *       ({@link Rage#setPlayerTeam}: players can't have the Rage, but the other teams' raged mobs go for them and their own
 *       team's never do);</li>
 *   <li>{@code /dross rage clear <targets>} ({@link #clear}): takes them off their teams (a mob's Rage gone too: its old
 *       self again).</li>
 * </ul>
 */
public final class RageCommands
{
    private RageCommands() {}

    /** @return how many went onto the team (0: none could, a failure message sent) */
    public static int team(CommandSourceStack source, Collection<? extends Entity> targets, RageTeam team)
    {
        int players = 0;
        int mobs = 0;
        Entity one = null;
        for (Entity e : targets)
        {
            Entity target = e instanceof EnderDragonPart part ? part.parentMob : e;
            if (target instanceof Player player)
            {
                Rage.setPlayerTeam(player, team);
                players++;
                one = player;
            }
            else if (target instanceof Mob mob && Rage.enrage(mob, team))
            {
                mobs++;
                one = mob;
            }
        }
        int total = players + mobs;
        if (total == 0)
        {
            source.sendFailure(Component.translatable("dross.command.rage.none"));
            return 0;
        }
        Component who = total == 1 ? one.getDisplayName() : Component.translatable("dross.command.rage.count", players, mobs);
        source.sendSuccess(() -> Component.translatable("dross.command.rage.team", who, team.displayName()), true);
        return total;
    }

    /** @return how many were taken off a team */
    public static int clear(CommandSourceStack source, Collection<? extends Entity> targets)
    {
        int count = 0;
        for (Entity e : targets)
        {
            if (e instanceof Player player && Rage.teamOf(player) != null)
            {
                Rage.setPlayerTeam(player, null);
                count++;
            }
            else if (e instanceof Mob mob && (Rage.isRaged(mob) || Rage.teamOf(mob) != null))
            {
                Rage.calm(mob);
                count++;
            }
        }
        int n = count;
        source.sendSuccess(() -> Component.translatable("dross.command.rage.clear", n), true);
        return count;
    }
}
