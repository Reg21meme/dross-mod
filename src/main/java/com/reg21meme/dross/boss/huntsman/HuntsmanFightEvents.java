package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The Huntsman's fight round the game (Forge bus, {@link HuntFight}): what his marked arrows do when they hit (the
 * frost of the arrow rain, the aimed shot's freeze), no jumping while snared, and his moose's ice spikes cleared away
 * when their time is up (and any left over from before a restart).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HuntsmanFightEvents
{
    private HuntsmanFightEvents() {}

    /** The one his rain arrow is hurting right now (its knockback is skipped). */
    @Nullable
    private static LivingEntity rainHit;

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event)
    {
        if (event.getSource().getDirectEntity() instanceof AbstractArrow arrow)
        {
            if (arrow.getPersistentData().contains(HuntFight.RAIN_TAG) && event.getAmount() > 0.0F)
            {                                        // (not when a shield took it)
                event.setAmount(arrow.getPersistentData().getFloat(HuntFight.RAIN_TAG));
                rainHit = event.getEntity();
            }
            if (arrow.getPersistentData().contains(HuntFight.ARROW_TAG) && event.getAmount() > 0.0F)
            {
                HuntFight.arrowHit(event.getEntity(), arrow.getPersistentData().getString(HuntFight.ARROW_TAG));
            }
        }
    }

    /** A rain arrow's hit doesn't push its target about (many of them hit in a moment). */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event)
    {
        if (rainHit != null && event.getEntity() == rainHit)
        {
            event.setCanceled(true);
            rainHit = null;
        }
    }

    /** What one of them shoots or throws flies through the other and the moose's hitboxes (his arrows from the
     * saddle passed its head; in the duel they'd glance off each other harmlessly). */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent event)
    {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit))
        {
            return;
        }
        if (event.getProjectile().getOwner() instanceof HuntMob mob && HuntFight.isPartner(mob, hit.getEntity()))
        {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            return;
        }
        CompoundTag data = event.getProjectile().getPersistentData();
        if (!(hit.getEntity() instanceof Player) && data.hasUUID(HuntFight.AIM_TAG))
        {
            // what he aims at one (an arrow, the rain, his spear, the bola) flies on through any other but a player: at
            // a golem, past its Colossus (the user, October 25, 2026: "he doesn't switch his attack to them")
            Entity e = hit.getEntity() instanceof PartEntity<?> part ? part.getParent() : hit.getEntity();
            if (!e.getUUID().equals(data.getUUID(HuntFight.AIM_TAG)))
            {
                event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
                return;
            }
        }
        if (hit.getEntity() instanceof LivingEntity victim && data.contains(HuntFight.RAIN_TAG))
        {
            victim.invulnerableTime = 0;             // every arrow of the rain counts, however close together
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
        rainHit = null;
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
