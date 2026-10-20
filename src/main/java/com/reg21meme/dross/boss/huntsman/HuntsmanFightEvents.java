package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Huntsman's fight round the game (Forge bus, {@link HuntFight}): what his marked arrows do when they hit (the
 * frost of the arrow rain, the aimed shot's freeze), no jumping while snared, and his moose's ice spikes cleared away
 * when their time is up (and any left over from before a restart).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HuntsmanFightEvents
{
    private HuntsmanFightEvents() {}

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow
                && arrow.getPersistentData().contains(HuntFight.ARROW_TAG) && event.getAmount() > 0.0F)
        {
            HuntFight.arrowHit(event.getEntity(), arrow.getPersistentData().getString(HuntFight.ARROW_TAG));
        }
    }

    /** Snared, a jump goes nowhere. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event)
    {
        LivingEntity e = event.getEntity();
        if (e.hasEffect(ModEffects.SNARED.get()))
        {
            e.setDeltaMovement(e.getDeltaMovement().x, Math.min(0.0D, e.getDeltaMovement().y), e.getDeltaMovement().z);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            HuntFight.tickSpikes(event.getServer().overworld().getGameTime());
        }
    }

    /** A spike left over (saved with its land before its time was up) doesn't come back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Display
                && event.getEntity().getTags().contains(HuntFight.SPIKE_TAG) && !HuntFight.spikeStanding(event.getEntity())
                && event.loadedFromDisk())
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        HuntFight.clearSpikes();
    }
}
