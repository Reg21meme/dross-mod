package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.registry.ModEffects;
import com.reg21meme.dross.villager.DrossTrader;
import com.reg21meme.dross.villager.Survivor;
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
import java.util.UUID;

/**
 * The Rage Wand's Rage (the user's request, a creative-only test tool): what it means for a mob to be raged, and who
 * its enemies are. Every number is in {@link RageTuning}.
 * <ul>
 *   <li><b>Raged</b>: it has the {@link RageEffect} (infinite, no cure). The wand gives it ({@link #enrage}).</li>
 *   <li><b>Sides</b>: every raged mob is on a side, saved in its persistent data as {@link #TAG_SIDE} (a UUID); a mob
 *       without one is its own side (its own UUID). A raged boss's minions join its side ({@link RageLeader},
 *       {@link #recruit}).</li>
 *   <li><b>Enemies</b>: two raged mobs on different sides. It's a free-for-all: a raged mob goes for the nearest enemy
 *       within {@link RageTuning#RAGE_RANGE} blocks, and while it has any it ignores players (it never targets one,
 *       even one that hits it). The fighting itself is each mob's own: its own attacks, moves and movement
 *       ({@link RageAi}); a mob with no attack of its own (cows, pigs, villagers...) headbutts
 *       ({@link RageHeadbuttGoal}).</li>
 *   <li><b>Calming down</b>: a raged mob with no enemy near for {@link RageTuning#RAGE_CALM_TICKS} loses its Rage and
 *       behaves normally again ({@link #calm}). So the last one standing (or the last side) calms down.</li>
 * </ul>
 * Everything here is server side (a mob's effects aren't sent to the clients).
 */
public final class Rage
{
    /** Persistent data key: the side a raged mob fights for (a UUID). Missing: its own side. */
    public static final String TAG_SIDE = "RageSide";

    private Rage() {}

    // ---------------------------------------------------------------- state

    /** True if this is a living thing with the Rage. Server side. */
    public static boolean isRaged(@Nullable Entity entity)
    {
        return entity instanceof LivingEntity living && living.hasEffect(ModEffects.RAGE.get());
    }

    /** The side a mob fights for: its saved {@link #TAG_SIDE}, or its own UUID. */
    public static UUID sideOf(Entity entity)
    {
        return entity.getPersistentData().hasUUID(TAG_SIDE) ? entity.getPersistentData().getUUID(TAG_SIDE) : entity.getUUID();
    }

    /**
     * True if the Rage has made these two enemies: both raged, on different sides. The bosses' blows and projectiles
     * use this so they can hit a raged enemy of their own kind (another boss, or golems and rats of another side),
     * which they never do otherwise.
     */
    public static boolean areEnemies(@Nullable Entity a, @Nullable Entity b)
    {
        return a != null && b != null && a != b && isRaged(a) && isRaged(b) && !sideOf(a).equals(sideOf(b));
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

    // ---------------------------------------------------------------- raging and calming

    /**
     * The wand's hit: rages a mob (it fights for itself), with a burst of red angry particles and a small growl. A mob
     * that's raged already keeps its side (the burst plays again). A raged {@link RageLeader} (a boss) also gets its
     * followers onto its side, and wakes up if it was asleep. A mob that can't be raged ({@link #canBeRaged}) only
     * gets a dull click and a puff of smoke.
     *
     * @return true if the mob is raged now
     */
    public static boolean enrage(Mob mob)
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
        if (!isRaged(mob))
        {
            mob.getPersistentData().remove(TAG_SIDE); // the wand's mob fights for itself
            if (!mob.addEffect(rageEffect()) || !isRaged(mob))
            {
                fizzle(level, mob); // something refused it (the Wither refuses every effect)
                return false;
            }
        }
        RageAi.ensureInstalled(mob);
        angryParticles(level, mob, RageTuning.RAGE_BURST_PARTICLES);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, mob.getSoundSource(),
                RageTuning.RAGE_SOUND_VOLUME, RageTuning.RAGE_SOUND_PITCH);
        if (mob instanceof RageLeader leader)
        {
            leader.onRaged();
            for (Mob follower : leader.rageFollowers())
            {
                join(follower, mob);
            }
        }
        return true;
    }

    /**
     * A boss's summon code calls this for each minion it makes (before adding it to the world): if the boss is raged,
     * the minion joins its side (see {@link RageLeader}).
     */
    public static void recruit(Mob follower, Mob leader)
    {
        if (isRaged(leader))
        {
            join(follower, leader);
        }
    }

    /** {@code follower} is raged and joins {@code leader}'s side (it never fights it or the others on that side). */
    static boolean join(Mob follower, Mob leader)
    {
        if (follower == leader || !follower.isAlive())
        {
            return false;
        }
        follower.getPersistentData().putUUID(TAG_SIDE, sideOf(leader));
        if (!isRaged(follower) && (!follower.addEffect(rageEffect()) || !isRaged(follower)))
        {
            follower.getPersistentData().remove(TAG_SIDE);
            return false;
        }
        RageAi.ensureInstalled(follower);
        return true;
    }

    /** It calms down: the Rage and its side are gone, its rage goals too, and it behaves normally again. */
    public static void calm(Mob mob)
    {
        mob.removeEffect(ModEffects.RAGE.get());
        RageAi.uninstall(mob);
    }

    /** The Rage as the wand gives it: for ever, its swirl hidden (the mob shows angry particles instead), its icon shown. */
    static MobEffectInstance rageEffect()
    {
        return new MobEffectInstance(ModEffects.RAGE.get(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    // ---------------------------------------------------------------- enemies

    /** True if {@code other} is an enemy that's in the fight: alive, with its AI (not a frozen showcase mob), raged on another side. */
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
        return isRaged(mob) && !enemiesNear(mob).isEmpty();
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

    /** Red angry particles (Minecraft's angry villager ones) over its head. */
    static void angryParticles(ServerLevel level, Mob mob, int count)
    {
        double spread = mob.getBbWidth() * RageTuning.PARTICLE_SPREAD;
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getEyeY() + RageTuning.PARTICLE_ABOVE_EYES, mob.getZ(),
                count, spread, spread * 0.5D, spread, 0.0D);
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
