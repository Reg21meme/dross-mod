package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.registry.ModEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.entity.PartEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Rage's hooks into the game (server side):
 * <ul>
 *   <li>every mob, every tick: its rage goals come and go with its Rage, and a raged mob's fight ({@link RageAi});</li>
 *   <li>a raged mob never targets a raged mob of its own team (a zombie calling its friends on whoever hit it, a
 *       skeleton's stray arrow...), and with a raged enemy near it never picks anything else as its target: not a
 *       player (even one that hits it), not a mob that isn't in the fight. This catches what its own goals and
 *       Minecraft's rules try to set (a target it already had before the two became teammates is dropped when they
 *       do: {@link Rage#settleTeam}, and {@link RageAi}'s safety net);</li>
 *   <li>a raged mob's hits (blows, arrows, explosions) never hurt a raged mob of its own team, nor a player on it, and
 *       a player on a team never hurts their own team's;</li>
 *   <li>players can't have the Rage (not even with {@code /effect}), but can be on a team ({@link Rage#setPlayerTeam}),
 *       which they keep when they die;</li>
 *   <li>a player on a team gives off a few specks of its colour now and then like a raged mob, and a player holding the
 *       wand sees every team round them clearly ({@link #showTeams});</li>
 *   <li>a raged Ender Dragon or warden shows a plain boss bar ({@link RageBars}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RageEvents
{
    private RageEvents() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        if (event.getEntity() instanceof Mob mob && mob.level() instanceof ServerLevel level)
        {
            RageAi.tick(mob);
            if (RageBars.wants(mob) && mob.isAlive() && Rage.isRaged(mob))
            {
                RageBars.tick(mob, level); // a raged dragon's or warden's plain boss bar
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            RageBars.sweep();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        RageBars.clear();
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event)
    {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide)
        {
            return;
        }
        LivingEntity next = event.getNewTarget();
        if (next == null || !Rage.isRaged(mob) || Rage.areEnemies(mob, next))
        {
            return; // letting go of a target, not raged, or one of its raged enemies: fine
        }
        if (Rage.areTeammates(mob, next) || Rage.hasEnemyNear(mob))
        {
            event.setCanceled(true); // never its own team; with an enemy near, only its enemies (players included: it ignores them)
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide)
        {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (Rage.areTeammates(attacker, victim))
        {
            event.setCanceled(true); // no friendly fire within a team (players on it too)
        }
    }

    /** A player keeps their team when they die (the copy that respawns is a new player). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        RageTeam team = Rage.teamOf(event.getOriginal());
        if (team != null)
        {
            event.getEntity().getPersistentData().putString(Rage.TAG_TEAM, team.id());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level))
        {
            return;
        }
        RageTeam team = Rage.teamOf(player);
        if (team != null && !player.isSpectator() && (player.tickCount + player.getId()) % RageTuning.RAGE_PARTICLE_EVERY == 0)
        {
            Rage.teamParticles(level, player, team, RageTuning.RAGE_PARTICLES);
        }
        if (player.tickCount % RageTuning.WAND_SHOW_EVERY == 0 && (player.getMainHandItem().getItem() instanceof RageWandItem
                || player.getOffhandItem().getItem() instanceof RageWandItem))
        {
            showTeams(level, player);
        }
    }

    /** Holding the wand: every raged mob and every other player on a team near gives off its team's colour all over
     *  its body, which only this player sees ({@link RageTuning#WAND_SHOW_RANGE}). */
    private static void showTeams(ServerLevel level, ServerPlayer viewer)
    {
        double r = RageTuning.WAND_SHOW_RANGE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, viewer.getBoundingBox().inflate(r),
                e -> e != viewer && e.isAlive() && Rage.inFight(e) && e.distanceToSqr(viewer) <= r * r))
        {
            RageTeam team = Rage.teamOf(e);
            if (team == null)
            {
                continue;
            }
            DustParticleOptions dust = new DustParticleOptions(DrossColors.vector(team.color()), RageTuning.WAND_SHOW_SIZE);
            double w = e.getBbWidth() * 0.5D;
            double h = e.getBbHeight() * 0.5D;
            level.sendParticles(viewer, dust, true, e.getX(), e.getY() + h, e.getZ(), RageTuning.WAND_SHOW_PARTICLES,
                    w, h, w, 0.0D);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (event.getEntity() instanceof Player && event.getEffectInstance().getEffect() == ModEffects.RAGE.get())
        {
            event.setResult(Event.Result.DENY); // players can't be raged
        }
    }
}
