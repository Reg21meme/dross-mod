package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.registry.ModEffects;
import com.reg21meme.dross.villager.DrossTrader;
import com.reg21meme.dross.villager.Survivor;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Rage Wand's Rage (the user's request, a creative-only test tool): what it means for a mob to be raged, which team
 * it's on, and who its enemies are. Every number is in {@link RageTuning}.
 * <ul>
 *   <li><b>Raged</b>: it has the {@link RageEffect} (infinite, no cure). The wand gives it ({@link #enrage}).</li>
 *   <li><b>Teams</b>: every raged mob is on one of the wand's {@link RageTeam}s, saved in its persistent data as
 *       {@link #TAG_TEAM}: the wand's team when it hit the mob (hit again with another team, it switches). A raged
 *       boss's minions join its team ({@link RageLeader}, {@link #recruit}). The Rage and the team never wear off ("they
 *       stay their team forever: this is a creative-only test item").</li>
 *   <li><b>Enemies</b>: two raged mobs on different teams. A raged mob goes for the nearest enemy within
 *       {@link RageTuning#RAGE_RANGE} blocks, and while it has any it ignores players (it never targets one, even one
 *       that hits it). It never targets or hurts a raged mob of its own team. With no enemy near it behaves normally
 *       (keeping its team). The fighting itself is each mob's own: its own attacks, moves and movement ({@link RageAi});
 *       a mob with no attack of its own (cows, pigs, villagers...) headbutts ({@link RageHeadbuttGoal}).</li>
 * </ul>
 * A mob given the Rage some other way (a command) has no team: it fights nobody and nobody fights it.
 * Everything here is server side (a mob's effects aren't sent to the clients).
 */
public final class Rage
{
    /** Persistent data key: the team a raged mob fights for ({@link RageTeam#id()}). */
    public static final String TAG_TEAM = "RageTeam";

    private Rage() {}

    // ---------------------------------------------------------------- state

    /** True if this is a living thing with the Rage. Server side. */
    public static boolean isRaged(@Nullable Entity entity)
    {
        return entity instanceof LivingEntity living && living.hasEffect(ModEffects.RAGE.get());
    }

    /** The team a mob is on (saved with it), or null if none. */
    @Nullable
    public static RageTeam teamOf(@Nullable Entity entity)
    {
        return entity == null ? null : RageTeam.byId(entity.getPersistentData().getString(TAG_TEAM));
    }

    /**
     * True if the Rage has made these two enemies: both raged, on two different teams. The bosses' blows and projectiles
     * use this so they can hit a raged enemy of their own kind (another boss, or golems and rats of another team), which
     * they never do otherwise.
     */
    public static boolean areEnemies(@Nullable Entity a, @Nullable Entity b)
    {
        if (a == null || b == null || a == b || !isRaged(a) || !isRaged(b))
        {
            return false;
        }
        RageTeam ta = teamOf(a);
        RageTeam tb = teamOf(b);
        return ta != null && tb != null && ta != tb;
    }

    /** True if these two are raged on the same team: they never fight each other. */
    public static boolean areTeammates(@Nullable Entity a, @Nullable Entity b)
    {
        if (a == null || b == null || a == b || !isRaged(a) || !isRaged(b))
        {
            return false;
        }
        RageTeam ta = teamOf(a);
        return ta != null && ta == teamOf(b);
    }

    /**
     * True if the wand can rage this mob. Not: a mob with no AI (the frozen showcase bosses, rats and traders), the
     * Ender Dragon (it flies by its own phases and never fights mobs), the Dross trader or the hub's survivors (they
     * can never die, so their fight would never end). (The Wither can't have any effect at all, so it can't either.)
     */
    public static boolean canBeRaged(Mob mob)
    {
        return mob.isAlive() && !mob.isNoAi() && !(mob instanceof EnderDragon) && !(mob instanceof DrossTrader)
                && !(mob instanceof Survivor);
    }

    // ---------------------------------------------------------------- raging

    /**
     * The wand's hit: rages a mob onto the wand's team (a raged mob of another team switches to this one), with a burst
     * of the team's colour and a small growl. A raged {@link RageLeader} (a boss) also gets its followers onto its team,
     * and wakes up if it was asleep. A mob that can't be raged ({@link #canBeRaged}) only gets a dull click and a puff of
     * smoke.
     *
     * @return true if the mob is raged on that team now
     */
    public static boolean enrage(Mob mob, RageTeam team)
    {
        if (!(mob.level() instanceof ServerLevel level))
        {
            return false;
        }
        if (!canBeRaged(mob))
        {
            fizzle(level, mob);
            return false;
        }
        if (!isRaged(mob) && (!mob.addEffect(rageEffect()) || !isRaged(mob)))
        {
            fizzle(level, mob); // something refused it (the Wither refuses every effect)
            return false;
        }
        setTeam(mob, team);
        RageAi.ensureInstalled(mob);
        teamParticles(level, mob, team, RageTuning.RAGE_BURST_PARTICLES);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, mob.getSoundSource(),
                RageTuning.RAGE_SOUND_VOLUME, RageTuning.RAGE_SOUND_PITCH);
        if (mob instanceof RageLeader leader)
        {
            leader.onRaged();
            for (Mob follower : leader.rageFollowers())
            {
                join(follower, team);
            }
        }
        return true;
    }

    /**
     * A boss's summon code calls this for each minion it makes (before adding it to the world): if the boss is raged on a
     * team, the minion joins that team (see {@link RageLeader}).
     */
    public static void recruit(Mob follower, Mob leader)
    {
        RageTeam team = teamOf(leader);
        if (isRaged(leader) && team != null)
        {
            join(follower, team);
        }
    }

    /** {@code follower} is raged and joins this team (it never fights the others on it). */
    static boolean join(Mob follower, RageTeam team)
    {
        if (!follower.isAlive())
        {
            return false;
        }
        if (!isRaged(follower) && (!follower.addEffect(rageEffect()) || !isRaged(follower)))
        {
            return false;
        }
        setTeam(follower, team);
        RageAi.ensureInstalled(follower);
        if (follower.level() instanceof ServerLevel level)
        {
            teamParticles(level, follower, team, RageTuning.JOIN_BURST_PARTICLES);
        }
        return true;
    }

    /** Puts a mob on a team (saved with it). A target that's now its teammate is let go of (its rage goal drops it). */
    private static void setTeam(Mob mob, RageTeam team)
    {
        mob.getPersistentData().putString(TAG_TEAM, team.id());
        if (areTeammates(mob, mob.getTarget()))
        {
            mob.setTarget(null);
        }
    }

    /** The Rage as the wand gives it: for ever, its swirl hidden (the mob shows its team's colour instead), its icon shown. */
    static MobEffectInstance rageEffect()
    {
        return new MobEffectInstance(ModEffects.RAGE.get(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    // ---------------------------------------------------------------- enemies

    /** True if {@code other} is an enemy that's in the fight: alive, with its AI (not a frozen showcase mob), raged on another team. */
    static boolean isLiveEnemy(Mob mob, @Nullable Entity other)
    {
        return other instanceof Mob enemy && enemy != mob && enemy.isAlive() && !enemy.isNoAi() && areEnemies(mob, enemy);
    }

    /** True if it's close enough for a raged mob to care ({@link RageTuning#RAGE_RANGE}). */
    static boolean inRange(Mob mob, Entity other)
    {
        return other.level() == mob.level() && other.distanceToSqr(mob) <= RageTuning.RAGE_RANGE * RageTuning.RAGE_RANGE;
    }

    /** Its enemies within {@link RageTuning#RAGE_RANGE} blocks (also ones it can't target right now, like a Rat King under the ground). */
    static List<Mob> enemiesNear(Mob mob)
    {
        return mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(RageTuning.RAGE_RANGE),
                other -> isLiveEnemy(mob, other) && inRange(mob, other));
    }

    /** True if a raged mob has any enemy within {@link RageTuning#RAGE_RANGE} blocks: it ignores players then. */
    public static boolean hasEnemyNear(Mob mob)
    {
        return isRaged(mob) && teamOf(mob) != null && !enemiesNear(mob).isEmpty();
    }

    /** True if a raged mob can go for this one now: a live enemy in range that can be seen as an enemy (not under the ground, not invulnerable). */
    static boolean isTargetable(Mob mob, @Nullable Entity target)
    {
        return isLiveEnemy(mob, target) && inRange(mob, target) && ((LivingEntity) target).canBeSeenAsEnemy();
    }

    /** The nearest enemy it can go for now (never {@code skip}), or null. */
    @Nullable
    static Mob nearestTarget(Mob mob, @Nullable Entity skip)
    {
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Mob enemy : enemiesNear(mob))
        {
            if (enemy == skip || !enemy.canBeSeenAsEnemy())
            {
                continue;
            }
            double d = enemy.distanceToSqr(mob);
            if (d < bestDistance)
            {
                bestDistance = d;
                best = enemy;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- looks

    /** Dust in its team's colour over its head (so the teams can be told apart). */
    static void teamParticles(ServerLevel level, Mob mob, RageTeam team, int count)
    {
        double spread = mob.getBbWidth() * RageTuning.PARTICLE_SPREAD;
        DustParticleOptions dust = new DustParticleOptions(DrossColors.vector(team.color()), RageTuning.TEAM_PARTICLE_SIZE);
        level.sendParticles(dust, mob.getX(), mob.getEyeY() + RageTuning.PARTICLE_ABOVE_EYES, mob.getZ(), count, spread,
                spread * 0.5D, spread, 0.0D);
    }

    /** The wand can't rage this one: a dull click and a little smoke. */
    private static void fizzle(ServerLevel level, Mob mob)
    {
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS,
                RageTuning.FIZZLE_VOLUME, RageTuning.FIZZLE_PITCH);
        double spread = mob.getBbWidth() * RageTuning.PARTICLE_SPREAD;
        level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getEyeY(), mob.getZ(), RageTuning.FIZZLE_SMOKE,
                spread, spread * 0.5D, spread, 0.01D);
    }
}
