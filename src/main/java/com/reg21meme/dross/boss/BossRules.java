package com.reg21meme.dross.boss;

import com.reg21meme.dross.villager.AdminSwordItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * The rules every boss follows (the user's boss buff, November 6-7, 2026: "the bosses should be strong and difficult";
 * CLAUDE.md, The plan, "Every boss"), with every number in one place. The bosses ({@link DrossBoss}): the Cinder
 * Colossus, the Rat King, the Huntsman and his Alpha Moose, the Emberdactyl and its young. Showcase ones don't follow
 * them (only {@code /kill} hurts those anyway).
 * <ul>
 *   <li><b>The hit cap</b>: one hit takes at most {@link #HIT_CAP_SHARE} of a boss's full health (so a modded or
 *       enchanted weapon can't melt it). {@code /kill}, the void and the Admin Sword ignore it ({@link #uncapped}).</li>
 *   <li><b>More players, a stronger boss</b>: every extra player in its fight (within its boss bar's range, alive, not in
 *       creative or spectator mode) gives it {@link #HEALTH_PER_PLAYER} more health and {@link #DAMAGE_PER_PLAYER} more
 *       damage. Someone joining mid-fight raises its health but keeps its bar's share (half full stays half full), and the
 *       bonus never goes down until it resets ({@link BossScale}).</li>
 *   <li><b>Resets</b>: with nobody within {@link #RESET_RANGE} blocks for {@link #RESET_TICKS} ticks (and no foe it's
 *       fighting that near), it goes back to full health, drops its bonus and goes back to resting: the Rat King to sleep,
 *       the Colossus dormant, the Emberdactyl to its perch, the Huntsman calm in the saddle.</li>
 * </ul>
 * Armour-piercing hits were talked over and left out for now (the user wants to test the rest first).
 */
public final class BossRules
{
    /** One hit takes at most this share of a boss's full health: 15 hits at least. */
    public static final float HIT_CAP_SHARE = 1.0F / 15.0F;
    /** Every extra player in a boss's fight adds this share of its own health (+50%)... */
    public static final double HEALTH_PER_PLAYER = 0.5D;
    /** ...and this share to every blow and shot of its (+20%). */
    public static final float DAMAGE_PER_PLAYER = 0.2F;
    /** With no player (and no foe it's fighting) within this many blocks... */
    public static final double RESET_RANGE = 64.0D;
    /** ...for this many ticks (3 minutes), it resets. */
    public static final long RESET_TICKS = 3L * 60L * 20L;
    /** It counts the players in its fight (and looks for anyone near) every this many ticks. */
    public static final int COUNT_EVERY = 10;
    /** Its health modifier for the extra players (one id for every boss). */
    static final UUID HEALTH_BONUS_ID = UUID.fromString("5c3e9a41-7d0b-4f62-8e1a-0b9d2f6c4a17");

    private BossRules() {}

    /** True for a player who's in a boss's fight: alive, not in creative or spectator mode. */
    public static boolean inFight(Player player)
    {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    /** The players in a fight (see {@link #inFight}) within {@code range} blocks of the boss. */
    public static int playersNear(LivingEntity boss, ServerLevel level, double range)
    {
        int count = 0;
        for (ServerPlayer player : level.players())
        {
            if (inFight(player) && player.distanceToSqr(boss) <= range * range)
            {
                count++;
            }
        }
        return count;
    }

    /** True if any player but a spectator (creative ones too: they're there) is within {@code range} blocks. */
    public static boolean anyoneNear(LivingEntity boss, ServerLevel level, double range)
    {
        for (ServerPlayer player : level.players())
        {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(boss) <= range * range)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * True for a hit the cap leaves alone: damage that bypasses invulnerability ({@code /kill}, falling out of the world)
     * and the Admin Sword's blow (a test tool that kills anything in one hit).
     */
    public static boolean uncapped(DamageSource source)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return true;
        }
        return source.getEntity() instanceof Player player && source.getDirectEntity() == player
                && player.getMainHandItem().getItem() instanceof AdminSwordItem;
    }
}
