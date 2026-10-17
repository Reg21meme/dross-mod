package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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
 *   <li>a raged mob's hits (blows, arrows, explosions) never hurt a raged mob of its own team;</li>
 *   <li>players can't have the Rage (not even with {@code /effect}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RageEvents
{
    private RageEvents() {}

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide)
        {
            RageAi.tick(mob);
        }
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
        if (attacker instanceof Mob && Rage.areTeammates(attacker, victim))
        {
            event.setCanceled(true); // no friendly fire within a team
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
