package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
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
 *   <li>a raged mob with a raged enemy near never picks anything else as its target: not a player (even one that hits
 *       it), not a mob that isn't in the fight. This catches what its own goals and Minecraft's rules try to set
 *       (a zombie calling its friends on whoever hit it, a piglin's brain...);</li>
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
        if (Rage.hasEnemyNear(mob))
        {
            event.setCanceled(true); // it only has eyes for its raged enemies (players included: it ignores them)
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
