package com.reg21meme.dross.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A boss's share of {@link BossRules}: how many extra players its fight has had (its bonus: more health, harder blows;
 * it never goes down until it resets), and when a player was last near (its reset). Its owner ({@link DrossBoss}) ticks it
 * on the server every tick and saves it ({@link #save}, {@link #load}).
 */
public final class BossScale
{
    private static final String TAG = "DrossBossRules";
    private static final String TAG_EXTRA = "ExtraPlayers";
    private static final String TAG_LAST_NEAR = "LastNear";

    /** Extra players its fight has had (beyond the first). */
    private int extra;
    /** The game time a player (or a foe it was fighting) was last near (-1: not yet looked). */
    private long lastNear = -1L;

    /** Extra players its fight has had (beyond the first). */
    public int extraPlayers()
    {
        return this.extra;
    }

    /** Its health, times this ({@link BossRules#HEALTH_PER_PLAYER} a player more). */
    public double healthFactor()
    {
        return 1.0D + BossRules.HEALTH_PER_PLAYER * this.extra;
    }

    /** Its blows and shots, times this ({@link BossRules#DAMAGE_PER_PLAYER} a player more). */
    public float damageFactor()
    {
        return 1.0F + BossRules.DAMAGE_PER_PLAYER * this.extra;
    }

    /**
     * Every tick on the server: every {@link BossRules#COUNT_EVERY} ticks it counts the players in its fight (its bonus
     * goes up with them) and looks for anyone near; nobody for {@link BossRules#RESET_TICKS}, it resets.
     */
    public void tick(LivingEntity boss)
    {
        DrossBoss b = (DrossBoss) boss;
        if (!(boss.level() instanceof ServerLevel level) || !b.followsBossRules() || !boss.isAlive() || boss.isDeadOrDying())
        {
            return;
        }
        long now = level.getGameTime();
        if (this.lastNear < 0L || this.lastNear > now)
        {
            this.lastNear = now;
        }
        if (boss.tickCount % BossRules.COUNT_EVERY != 0)
        {
            return;
        }
        boolean near = BossRules.anyoneNear(boss, level, BossRules.RESET_RANGE) || foeNear(b, boss);
        if (!near)
        {
            if (now - this.lastNear >= BossRules.RESET_TICKS && b.canResetNow())
            {
                this.drop(boss);
                b.resetFight();
            }
            return;
        }
        this.lastNear = now;
        int players = BossRules.playersNear(boss, level, b.bossBarRange());
        if (players - 1 > this.extra)
        {
            this.raiseTo(boss, players - 1);
        }
    }

    /** True if whom it's fighting is alive and within {@link BossRules#RESET_RANGE}: its fight goes on. */
    private static boolean foeNear(DrossBoss b, LivingEntity boss)
    {
        LivingEntity foe = b.bossTarget();
        return foe != null && foe.isAlive() && foe.level() == boss.level()
                && foe.distanceToSqr(boss) <= BossRules.RESET_RANGE * BossRules.RESET_RANGE;
    }

    /** Raises its bonus to this many extra players (never lowers it). */
    public void raiseTo(LivingEntity boss, int extraPlayers)
    {
        if (extraPlayers > this.extra)
        {
            this.set(boss, extraPlayers);
        }
    }

    /** Its reset: the bonus is dropped (its health follows, keeping its share) and the clock starts again. */
    public void drop(LivingEntity boss)
    {
        if (this.extra != 0)
        {
            this.set(boss, 0);
        }
        if (boss.level() instanceof ServerLevel level)
        {
            this.lastNear = level.getGameTime();
        }
    }

    private void set(LivingEntity boss, int extraPlayers)
    {
        double before = this.healthFactor();
        this.extra = Math.max(0, extraPlayers);
        ((DrossBoss) boss).bonusChanged(before, this.healthFactor());
    }

    /**
     * Its full health with {@code extraPlayers} extra players ({@link BossRules#HEALTH_PER_PLAYER} of its own a
     * player), keeping its share of it (half full stays half full).
     */
    public static void applyHealth(LivingEntity boss, int extraPlayers)
    {
        AttributeInstance max = boss.getAttribute(Attributes.MAX_HEALTH);
        if (max == null)
        {
            return;
        }
        float share = boss.getMaxHealth() > 0.0F ? boss.getHealth() / boss.getMaxHealth() : 1.0F;
        max.removeModifier(BossRules.HEALTH_BONUS_ID);
        if (extraPlayers > 0)
        {
            max.addPermanentModifier(new AttributeModifier(BossRules.HEALTH_BONUS_ID, "Dross boss: more players",
                    BossRules.HEALTH_PER_PLAYER * extraPlayers, AttributeModifier.Operation.MULTIPLY_BASE));
        }
        boss.setHealth(share * boss.getMaxHealth());
    }

    /** For the tests: as if nobody had been near for this many ticks longer (its reset comes sooner). */
    public void pretendAway(long ticks)
    {
        this.lastNear -= ticks;
    }

    /** Saves it with its boss. (Its health modifier is saved with the boss's attributes.) */
    public void save(CompoundTag tag)
    {
        CompoundTag t = new CompoundTag();
        t.putInt(TAG_EXTRA, this.extra);
        t.putLong(TAG_LAST_NEAR, this.lastNear);
        tag.put(TAG, t);
    }

    public void load(CompoundTag tag)
    {
        if (!tag.contains(TAG))
        {
            return;
        }
        CompoundTag t = tag.getCompound(TAG);
        this.extra = Math.max(0, t.getInt(TAG_EXTRA));
        this.lastNear = t.contains(TAG_LAST_NEAR) ? t.getLong(TAG_LAST_NEAR) : -1L;
    }
}
