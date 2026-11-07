package com.reg21meme.dross.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;

/**
 * A boss that follows {@link BossRules}: the hit cap, the extra players' bonus and the reset. Each keeps a
 * {@link BossScale}, ticks it on the server and saves it with itself.
 */
public interface DrossBoss
{
    /** Its extra players' bonus and when a player was last near. */
    BossScale bossScale();

    /** How far its boss bar reaches: the players in its fight within this many blocks count for its bonus. */
    double bossBarRange();

    /** False for one that doesn't fight (a showcase one, on show only): the rules leave it alone. */
    boolean followsBossRules();

    /** The health one hit is capped against ({@link BossRules#HIT_CAP_SHARE} of it): its full health. */
    default float hitCapHealth()
    {
        return ((LivingEntity) this).getMaxHealth();
    }

    /**
     * Its bonus has changed (more players, or dropped at a reset): its health follows, keeping its share. By default its
     * full health ({@link BossScale#applyHealth}); a boss with health of its own (the Wild Hunt's shared bar) does that
     * too.
     */
    default void bonusChanged(double oldFactor, double newFactor)
    {
        BossScale.applyHealth((LivingEntity) this, this.bossScale().extraPlayers());
    }

    /** Whom it's fighting: a foe this near (a raged mob of another team, say) keeps it from resetting. */
    @Nullable
    default LivingEntity bossTarget()
    {
        return this instanceof Mob mob ? mob.getTarget() : null;
    }

    /** False while it can't reset just now (in the middle of a stage change, under the ground...): it tries again. */
    default boolean canResetNow()
    {
        return true;
    }

    /**
     * Nobody has been near for {@link BossRules#RESET_TICKS}: its bonus is already dropped; it goes back to full health
     * and its resting state.
     */
    void resetFight();
}
