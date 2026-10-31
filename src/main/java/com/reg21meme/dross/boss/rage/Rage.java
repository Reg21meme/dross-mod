package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.registry.ModEffects;

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
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;

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
 *       that hits it). It never targets, chases or hurts a raged mob of its own team: the team beats natural enmity
 *       (an iron golem and a zombie on one team leave each other alone), and a fight two mobs started before they were
 *       teammates ends the moment they are ({@link #settleTeam}). With no enemy near it behaves normally towards
 *       everything else (keeping its team). The fighting itself is each mob's own: its own attacks, moves and movement ({@link RageAi});
 *       a mob with no attack of its own (cows, pigs, villagers...) headbutts ({@link RageHeadbuttGoal}).</li>
 * </ul>
 * A mob given the Rage some other way (a command) has no team: it fights nobody and nobody fights it.
 * <p><b>Players on a team</b> (the user, October 30: "assign @ player name or @s ... the player's colour so they can
 * fight alongside or against mobs"): {@code /dross rage team <targets> <team>} (or the wand's hit on another player)
 * puts a player on a team ({@link #setPlayerTeam}; saved on them, kept when they die). They can't have the Rage itself,
 * but they're in the fight ({@link #inFight}): the raged mobs of other teams go for them like any enemy (in survival or
 * adventure: Minecraft's mobs never attack a player in creative), their own team's never do, and their blows don't hurt
 * their teammates (nor theirs them).
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

    /** True if this is a player on a team (not a spectator): in the fight, though players can't have the Rage. */
    public static boolean isTeamPlayer(@Nullable Entity entity)
    {
        return entity instanceof Player player && !player.isSpectator() && teamOf(player) != null;
    }

    /** True if it's in the Rage's fight: a raged mob, or a player on a team. */
    public static boolean inFight(@Nullable Entity entity)
    {
        return isRaged(entity) || isTeamPlayer(entity);
    }

    /** The team a mob (or a player) is on (saved with it), or null if none. */
    @Nullable
    public static RageTeam teamOf(@Nullable Entity entity)
    {
        return entity == null ? null : RageTeam.byId(entity.getPersistentData().getString(TAG_TEAM));
    }

    /**
     * True if the Rage has made these two enemies: both in the fight (raged, or a player on a team), on two different
     * teams. The bosses' blows and projectiles
     * use this so they can hit a raged enemy of their own kind (another boss, or golems and rats of another team), which
     * they never do otherwise. A player in creative or spectator mode is nobody's enemy, whatever their team (the user,
     * October 30: the Huntsman "even seems to attack me in creative"; a creative player on a team was his foe).
     */
    public static boolean areEnemies(@Nullable Entity a, @Nullable Entity b)
    {
        if (a == null || b == null || a == b || !inFight(a) || !inFight(b) || untouchable(a) || untouchable(b))
        {
            return false;
        }
        RageTeam ta = teamOf(a);
        RageTeam tb = teamOf(b);
        return ta != null && tb != null && ta != tb;
    }

    /** A player in creative or spectator mode: out of every fight. */
    private static boolean untouchable(Entity e)
    {
        return e instanceof Player player && (player.isCreative() || player.isSpectator());
    }

    /** True if these two are on the same team (raged mobs, or players on a team): they never fight each other. */
    public static boolean areTeammates(@Nullable Entity a, @Nullable Entity b)
    {
        if (a == null || b == null || a == b || !inFight(a) || !inFight(b))
        {
            return false;
        }
        RageTeam ta = teamOf(a);
        return ta != null && ta == teamOf(b);
    }

    /**
     * True if the wand can rage this mob. Not: a mob with no AI (the frozen showcase bosses, rats and traders) or the
     * hub's survivors (they can never die, so their fight would never end). The Wither and the Ender Dragon can (the
     * user, October 30: "can't use the rage wand on the Wither, change that, also with the Ender Dragon"; the dragon was
     * refused, and both refuse every effect, so the Rage goes on them by force ({@link #enrage}); and raged, the Wither
     * was given the headbutt in place of its skulls): the Wither fights with its skulls, the
     * dragon with its charges and fireballs ({@link RageDragon}). The Dross trader
     * can (the user, October 30: "I want to be able to affect the trader with the Rage Wand"; until then he was left out
     * with the survivors): he headbutts like a wandering trader, and beaten down to half his health he teleports home
     * and heals as always, which ends his fight.
     */
    public static boolean canBeRaged(Mob mob)
    {
        return mob.isAlive() && !mob.isNoAi() && !(mob instanceof Survivor);
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
        if (!isRaged(mob) && !mob.addEffect(rageEffect()) && (mob instanceof WitherBoss || mob instanceof EnderDragon))
        {
            mob.forceAddEffect(rageEffect(), null); // (they refuse every effect: the Rage goes on them anyway)
        }
        if (!isRaged(mob))
        {
            fizzle(level, mob); // something refused it
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

    /**
     * Puts a mob on a team (saved with it), and its new teammates and it let go of each other at once
     * ({@link #settleTeam}): a fight they started before they were teammates ends there.
     */
    private static void setTeam(Mob mob, RageTeam team)
    {
        mob.getPersistentData().putString(TAG_TEAM, team.id());
        settleTeam(mob);
    }

    /**
     * Puts a player on a team (null: off any team), saved on them: a puff of the team's colour round them, and the raged
     * mobs near that are now their teammates let go of them at once ({@link #forget}).
     */
    public static void setPlayerTeam(Player player, @Nullable RageTeam team)
    {
        if (team == null)
        {
            player.getPersistentData().remove(TAG_TEAM);
            return;
        }
        player.getPersistentData().putString(TAG_TEAM, team.id());
        if (player.level() instanceof ServerLevel level)
        {
            teamParticles(level, player, team, RageTuning.RAGE_BURST_PARTICLES);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                    RageTuning.RAGE_SOUND_VOLUME * 0.5F, RageTuning.RAGE_SOUND_PITCH);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RageTuning.SETTLE_RANGE),
                    mob -> areTeammates(mob, player)))
            {
                forget(mob, player);
            }
        }
    }

    /** Takes a mob out of the fight: its Rage and its team gone (it's its old self again). */
    public static void calm(Mob mob)
    {
        mob.removeEffect(ModEffects.RAGE.get());
        RageAi.uninstall(mob);
    }

    /** The Rage as the wand gives it: for ever, its swirl hidden (the mob shows its team's colour instead), its icon shown. */
    static MobEffectInstance rageEffect()
    {
        return new MobEffectInstance(ModEffects.RAGE.get(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    // ---------------------------------------------------------------- teammates let go of each other

    /**
     * A mob has just joined (or switched to) its team, or come back from a save: both ways, teammates let go of each
     * other. It drops anything it holds against a raged mob that's now its teammate ({@link #forgetTeammates}), and
     * every raged mob within {@link RageTuning#SETTLE_RANGE} blocks that's now its teammate drops it
     * ({@link #forget}). Team colour beats natural enmity: an iron golem and a zombie (or a pillager) on one team stop
     * fighting at once, even if they were at it a moment before. ({@link RageAi} also checks every raged mob now and
     * then, {@link RageTuning#TEAMMATE_CHECK_TICKS}, in case anything slipped through.) Server side.
     */
    static void settleTeam(Mob mob)
    {
        if (!(mob.level() instanceof ServerLevel level) || teamOf(mob) == null || !isRaged(mob))
        {
            return;
        }
        forgetTeammates(mob);
        for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(RageTuning.SETTLE_RANGE),
                other -> other != mob && areTeammates(other, mob)))
        {
            forget(other, mob);
        }
    }

    /**
     * A raged mob drops everything it holds against a teammate: its target, its anger (a neutral mob's, like an iron
     * golem's; a warden's), whoever it last hurt or was hurt by, and its brain's attack target, anger and walk target
     * (piglins, hoglins...). Server side.
     */
    static void forgetTeammates(Mob mob)
    {
        if (!(mob.level() instanceof ServerLevel level) || teamOf(mob) == null || !isRaged(mob))
        {
            return;
        }
        forgetIfTeammate(mob, mob.getTarget());
        forgetIfTeammate(mob, mob.getLastHurtByMob());
        forgetIfTeammate(mob, mob.getLastHurtMob());
        if (mob instanceof NeutralMob neutral && neutral.getPersistentAngerTarget() != null)
        {
            forgetIfTeammate(mob, level.getEntity(neutral.getPersistentAngerTarget()));
        }
        if (mob instanceof Warden warden)
        {
            forgetIfTeammate(mob, warden.getEntityAngryAt().orElse(null));
        }
        for (Entity held : RageAi.brainGrudges(mob, level))
        {
            forgetIfTeammate(mob, held);
        }
    }

    private static void forgetIfTeammate(Mob mob, @Nullable Entity other)
    {
        if (other instanceof LivingEntity mate && areTeammates(mob, mate))
        {
            forget(mob, mate);
        }
    }

    /**
     * {@code mob} lets go of its teammate {@code mate}, every way it could hold on to it: its target (and the target goal
     * that picked it, which would pick it again), its anger, who it last hurt or was hurt by (a hurt-by goal would turn
     * on it), its brain's memories of it, and its walk (it stops chasing).
     */
    static void forget(Mob mob, LivingEntity mate)
    {
        boolean chasing = mob.getTarget() == mate;
        RageAi.stopTargetGoalsOn(mob, mate, chasing);
        if (mob.getTarget() == mate)
        {
            mob.setTarget(null);
        }
        if (mob.getLastHurtByMob() == mate)
        {
            mob.setLastHurtByMob(null);
        }
        if (mob.getLastHurtMob() == mate)
        {
            mob.setLastHurtMob(null);
        }
        if (mob instanceof NeutralMob neutral && mate.getUUID().equals(neutral.getPersistentAngerTarget()))
        {
            neutral.setPersistentAngerTarget(null);
            neutral.setRemainingPersistentAngerTime(0);
        }
        if (mob instanceof Warden warden)
        {
            warden.clearAnger(mate);
        }
        chasing |= RageAi.forgetBrainMate(mob, mate);
        if (chasing)
        {
            mob.getNavigation().stop(); // (an iron golem's walk towards its target would carry on by itself)
        }
    }

    // ---------------------------------------------------------------- enemies

    /** True if {@code other} is an enemy that's in the fight: alive, with its AI (not a frozen showcase mob), raged on
     *  another team; or a player on another team (in survival or adventure: mobs never attack a player in creative). */
    static boolean isLiveEnemy(Mob mob, @Nullable Entity other)
    {
        if (other instanceof Player player)
        {
            return player.isAlive() && !player.isCreative() && !player.isSpectator() && areEnemies(mob, player);
        }
        return other instanceof Mob enemy && enemy != mob && enemy.isAlive() && !enemy.isNoAi() && areEnemies(mob, enemy);
    }

    /** True if it's close enough for a raged mob to care ({@link RageTuning#RAGE_RANGE}). */
    static boolean inRange(Mob mob, Entity other)
    {
        return other.level() == mob.level() && other.distanceToSqr(mob) <= RageTuning.RAGE_RANGE * RageTuning.RAGE_RANGE;
    }

    /** Its enemies within {@link RageTuning#RAGE_RANGE} blocks that it can see (also ones it can't target right now,
     *  like a Rat King under the ground, without seeing them), players on other teams among them. One behind a wall
     *  doesn't count until it's seen (the user, October 30: in the bedrock arena's sections, with the walls between
     *  them closed, the Huntsman "seems to be attacking nothing": he went for an enemy on the wall's far side); one it's
     *  already fighting it keeps after round a corner ({@link #isTargetable}). */
    static List<LivingEntity> enemiesNear(Mob mob)
    {
        return mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(RageTuning.RAGE_RANGE),
                other -> isLiveEnemy(mob, other) && inRange(mob, other)
                        && (!other.canBeSeenAsEnemy() || mob.hasLineOfSight(other)));
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

    /** The nearest raged enemy a raged mob can go for now (another team, in range, not under the ground), or null: for
     * a boss that picks its own target (the Huntsman and his Alpha Moose). Server side. */
    @Nullable
    public static LivingEntity nearestEnemy(Mob mob)
    {
        return isRaged(mob) && teamOf(mob) != null ? nearestTarget(mob, null) : null;
    }

    /** The nearest enemy it can go for now (never {@code skip}), or null. */
    @Nullable
    static LivingEntity nearestTarget(Mob mob, @Nullable Entity skip)
    {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity enemy : enemiesNear(mob))
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
    static void teamParticles(ServerLevel level, LivingEntity mob, RageTeam team, int count)
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
