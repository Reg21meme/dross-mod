package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Smoke and embers rising from the volcano's crater (the lair's version 2), for players within
 * {@link LairTuning#SMOKE_RANGE} blocks of it: tall plumes of campfire signal smoke (the kind that rises about 20 blocks
 * and can be seen from far away), a dark puff now and then, lava pops and small flames.
 * <p>
 * Only particles: nothing is placed, nothing catches fire, and nothing changes in the world. They're sent to each nearby
 * player as "long distance" particles, so they show from as far as the player can see the volcano (a block's own
 * {@code animateTick} only runs within about 32 blocks of a player, too close to make a landmark).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
final class LairSmoke
{
    private LairSmoke() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        LairKeeper.Volume lair = LairKeeper.volume();
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || lair == null || lair.version() < 2
                || server.getTickCount() % SMOKE_INTERVAL_TICKS != 0)
        {
            return;
        }
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty())
        {
            return;
        }
        Vec3 crater = lair.crater();
        for (ServerPlayer player : dross.players())
        {
            if (player.distanceToSqr(crater) <= SMOKE_RANGE * SMOKE_RANGE)
            {
                puff(dross, player, crater, dross.random);
            }
        }
    }

    /** One round of smoke and embers for one player. */
    static void puff(ServerLevel dross, ServerPlayer player, Vec3 crater, RandomSource random)
    {
        for (int i = 0; i < SMOKE_PLUMES; i++)
        {
            Vec3 at = inCrater(crater, random, -3.0D);
            dross.sendParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, at.x, at.y, at.z, 0,
                    (random.nextDouble() - 0.5D) * 0.012D, SMOKE_RISE * (0.8D + 0.4D * random.nextDouble()),
                    (random.nextDouble() - 0.5D) * 0.012D, 1.0D);
        }
        if (random.nextInt(SMOKE_PUFF_ONE_IN) == 0)
        {
            Vec3 at = inCrater(crater, random, -2.0D);
            dross.sendParticles(player, ParticleTypes.LARGE_SMOKE, true, at.x, at.y, at.z, 0, 0.0D, 0.12D, 0.0D, 1.0D);
        }
        if (random.nextInt(EMBER_ONE_IN) == 0)
        {
            Vec3 at = inCrater(crater, random, -5.0D);
            dross.sendParticles(player, ParticleTypes.LAVA, true, at.x, at.y, at.z, EMBERS, 2.0D, 0.5D, 2.0D, 0.0D);
            for (int i = 0; i < EMBERS; i++)
            {
                Vec3 flame = inCrater(crater, random, -4.0D);
                dross.sendParticles(player, ParticleTypes.SMALL_FLAME, true, flame.x, flame.y, flame.z, 0,
                        (random.nextDouble() - 0.5D) * 0.03D, 0.08D + 0.06D * random.nextDouble(), (random.nextDouble() - 0.5D) * 0.03D, 1.0D);
            }
        }
    }

    /** A random spot inside the crater, {@code below} blocks under its rim (give or take a block). */
    private static Vec3 inCrater(Vec3 crater, RandomSource random, double below)
    {
        double a = random.nextDouble() * 2.0D * Math.PI;
        double r = Math.sqrt(random.nextDouble()) * (CRATER_RADIUS - 3.0D);
        return new Vec3(crater.x + r * Math.cos(a), crater.y + below + random.nextDouble() * 2.0D, crater.z + r * Math.sin(a));
    }
}
