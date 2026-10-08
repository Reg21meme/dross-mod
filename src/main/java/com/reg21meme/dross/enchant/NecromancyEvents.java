package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEnchantments;
import com.reg21meme.dross.villager.DrossTrader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

/**
 * What a Necromancy sword does:
 * <ul>
 *   <li><b>Hits</b> ({@link AttackEntityEvent}, which fires once per swing for the main target only, so mobs caught
 *       by the sweep never roll): a chance to raise undead next to the player, paid for with the sword's souls.</li>
 *   <li><b>Kills</b> of any zombie or skeleton kind with the sword store 1 soul, shown as an orange wisp flying
 *       from the mob into the player's hand.</li>
 *   <li>The first raise grants the "Rise!" advancement.</li>
 * </ul>
 * The numbers per level are in {@link NecromancyEnchantment}.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class NecromancyEvents
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /** How long the soul wisp takes to fly into the player's hand: 0.5 seconds. */
    private static final int WISP_TICKS = 10;
    /** Particles drawn along the wisp's path each tick. */
    private static final int WISP_PARTICLES_PER_TICK = 4;
    /** Wisp colour (orange) and particle size. */
    private static final DustParticleOptions WISP_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.0F), 1.0F);

    private static final ResourceLocation RISE_ADVANCEMENT = new ResourceLocation(Dross.MODID, "rise");
    /** Criterion name in data/dross/advancements/rise.json. */
    private static final String RISE_CRITERION = "raised";

    /** Soul wisps currently flying. */
    private static final List<Wisp> WISPS = new ArrayList<>();

    private NecromancyEvents() {}

    // ---------------------------------------------------------------------------------------------
    // Hitting: raise undead
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onAttack(AttackEntityEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator())
        {
            return;
        }
        // On Peaceful, Necromancy does nothing: no roll, no raise, no souls spent.
        if (player.level().getDifficulty() == Difficulty.PEACEFUL)
        {
            return;
        }
        ItemStack sword = player.getMainHandItem();
        int level = SoulStorage.necromancyLevel(sword);
        if (level <= 0 || !(sword.getItem() instanceof SwordItem))
        {
            return;
        }

        Entity target = event.getTarget();
        if (target instanceof PartEntity<?> part)
        {
            target = part.getParent(); // a part of the Ender Dragon counts as the dragon
        }
        // Mobs only (never players), never the trader, never your own risen undead.
        if (!(target instanceof Mob mob) || mob instanceof DrossTrader || !mob.isAlive() || mob.isInvulnerable())
        {
            return;
        }
        if (player.getUUID().equals(RisenUndead.getOwnerId(mob)))
        {
            return;
        }

        int index = NecromancyEnchantment.index(level);
        if (player.getRandom().nextFloat() >= NecromancyEnchantment.RAISE_CHANCE[index])
        {
            return;
        }
        raiseUndead(player, sword, level, mob);
    }

    private static void raiseUndead(ServerPlayer player, ItemStack sword, int necromancyLevel, LivingEntity hitMob)
    {
        int index = NecromancyEnchantment.index(necromancyLevel);
        int max = NecromancyEnchantment.MAX_ALIVE[index];
        int alive = RisenUndead.countAlive(player.getUUID());
        int count = alive == 0 ? max : NecromancyEnchantment.RAISE_WHEN_SOME_ALIVE[index];
        count = Math.min(count, max - alive); // never over the max
        if (count <= 0)
        {
            return;
        }

        boolean infinite = SoulStorage.isInfinite(sword);
        int souls = SoulStorage.getSouls(sword);
        int deathforged = EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.DEATHFORGED.get(), sword);

        int raised = 0;
        for (int i = 0; i < count; i++)
        {
            if (!infinite && souls < NecromancyEnchantment.SOUL_COST_NORMAL)
            {
                break; // out of souls
            }
            RisenType type = RisenType.random(necromancyLevel, false, player.getRandom());
            if (!infinite && type.soulCost() > souls)
            {
                // Not enough for a jockey/horseman: a normal one rises instead.
                type = RisenType.random(necromancyLevel, true, player.getRandom());
            }
            if (RisenUndead.raise(player, type, deathforged, hitMob))
            {
                raised++;
                if (!infinite)
                {
                    souls -= type.soulCost();
                }
            }
        }

        if (!infinite)
        {
            SoulStorage.setSouls(sword, souls);
        }
        if (raised > 0)
        {
            grantRiseAdvancement(player);
        }
    }

    /** Does nothing if the player already has it. Same pattern as portal/DrossArrival. */
    private static void grantRiseAdvancement(ServerPlayer player)
    {
        Advancement advancement = player.server.getAdvancements().getAdvancement(RISE_ADVANCEMENT);
        if (advancement != null)
        {
            player.getAdvancements().award(advancement, RISE_CRITERION);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Killing: gain souls
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide())
        {
            return;
        }
        // Any kind of zombie or skeleton: husk, drowned, zombie villager, zombified piglin, stray, wither skeleton...
        if (!(victim instanceof Zombie || victim instanceof AbstractSkeleton) || RisenUndead.isRisen(victim))
        {
            return;
        }
        // Killed by the player directly (melee or sweep), not by an arrow or a pet.
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player)
        {
            return;
        }
        ItemStack sword = player.getMainHandItem();
        if (SoulStorage.necromancyLevel(sword) <= 0 || !(sword.getItem() instanceof SwordItem))
        {
            return;
        }
        // Infinite swords (level IV) don't store anything, but still show the wisp.
        if (SoulStorage.addSoul(sword) || SoulStorage.isInfinite(sword))
        {
            WISPS.add(new Wisp((ServerLevel) victim.level(), victim.position().add(0.0D, victim.getBbHeight() / 2.0D, 0.0D), player.getUUID()));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Soul wisp
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || WISPS.isEmpty())
        {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Iterator<Wisp> it = WISPS.iterator();
        while (it.hasNext())
        {
            Wisp wisp = it.next();
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(wisp.player);
            if (player == null || player.level() != wisp.level)
            {
                it.remove();
                continue;
            }
            Vec3 hand = handPosition(player);
            float from = (float) wisp.age / WISP_TICKS;
            wisp.age++;
            float to = (float) wisp.age / WISP_TICKS;
            // A few particles between last tick's spot and this tick's spot, so the trail looks continuous.
            for (int i = 0; i < WISP_PARTICLES_PER_TICK; i++)
            {
                float t = from + (to - from) * (i + 1) / WISP_PARTICLES_PER_TICK;
                Vec3 pos = wisp.start.lerp(hand, t);
                wisp.level.sendParticles(WISP_DUST, pos.x, pos.y, pos.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
            if (wisp.age >= WISP_TICKS)
            {
                it.remove();
            }
        }
    }

    /** Roughly where the player's main hand is: in front of the body, on the main-arm side. */
    private static Vec3 handPosition(ServerPlayer player)
    {
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        if (player.getMainArm() == HumanoidArm.LEFT)
        {
            right = right.scale(-1.0D);
        }
        return player.position()
                .add(0.0D, player.getBbHeight() * 0.5D, 0.0D)
                .add(forward.scale(0.3D))
                .add(right.scale(0.35D));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        WISPS.clear();
    }

    private static final class Wisp
    {
        final ServerLevel level;
        final Vec3 start;
        final UUID player;
        int age;

        Wisp(ServerLevel level, Vec3 start, UUID player)
        {
            this.level = level;
            this.start = start;
            this.player = player;
        }
    }
}
