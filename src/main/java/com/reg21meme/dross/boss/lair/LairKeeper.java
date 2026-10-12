package com.reg21meme.dross.boss.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModSounds;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Looks after the built lair's Colossus (server side):
 * <ul>
 *   <li><b>It rises</b> in the middle of the floor, facing them, when a player (not a spectator; creative counts, so
 *       testers see it) walks into the cavern and the lair has no living Colossus, unless the last one was beaten less
 *       than {@link LairTuning#LAIR_RESPAWN_TICKS} (10 minutes of game time) ago. Never on Peaceful. It's the same fighting
 *       Colossus as {@code /dross boss summon colossus} makes ({@link CinderColossus#spawnBoss}), with a rumble, a roar and
 *       a burst of rock dust.</li>
 *   <li><b>Beaten</b>: any death counts, its normal death (statue, trophies, "Set in Stone") and {@code /kill} too (which
 *       gives no trophy, as always); the 10 minutes start then. One that's removed without dying (Peaceful) isn't
 *       beaten: the next player who walks in wakes a new one at once.</li>
 *   <li><b>Kept home</b>: it can't fit through the 3-wide tunnel, but a living lair Colossus found outside its cavern
 *       (teleported out with a command, say) is put back in the middle of the floor. If its saved UUID can't be found
 *       once the cavern's entities are loaded, it's gone (its statue crumbled, or it was removed) and is forgotten.</li>
 *   <li><b>No natural spawning</b> inside the cavern and the tunnel (wild golems included), so the fight stays clean.
 *       Spawn eggs, spawners and commands still work there.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class LairKeeper
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A lair Colossus carries this mark in its own saved data (besides the UUID in the lair's data). */
    static final String LAIR_TAG = "DrossLairColossus";
    /** The rock dust it rises in. */
    private static final int RISE_DUST = 160;
    private static final int RISE_SMOKE = 40;
    private static final float RISE_VOLUME = 3.0F;

    /**
     * The built lair's space, for the spawn check (which can run on world-generation threads, so it never reads the
     * saved data itself). Null when there's no lair (yet) or no server.
     */
    @Nullable
    private static volatile Volume volume;

    private LairKeeper() {}

    /** The lair's space: where its cavern and tunnel are. */
    record Volume(int cx, int cz, int floorY, Direction tunnel, int descent)
    {
        /** Horizontal distance from the floor's middle (block middles: the middle block is 0). */
        double distance(double x, double z)
        {
            double dx = x - (cx + 0.5D);
            double dz = z - (cz + 0.5D);
            return Math.sqrt(dx * dx + dz * dz);
        }

        /** True for a player standing in the cavern: on its floor, inside the dome. */
        boolean inCavern(Vec3 feet)
        {
            return distance(feet.x, feet.z) <= LairShape.FLOOR_EDGE && feet.y >= floorY + 0.9D && feet.y <= floorY + DOME_HEIGHT;
        }

        /** True if a Colossus standing here is still in its cavern. */
        boolean isHome(Vec3 feet)
        {
            return distance(feet.x, feet.z) <= LairShape.FLOOR_EDGE + HOME_MARGIN && feet.y >= floorY && feet.y <= floorY + DOME_HEIGHT + 1;
        }

        /** True if natural spawning is barred here: the cavern (a little past its floor's edge) and the tunnel. */
        boolean noSpawn(BlockPos pos)
        {
            int dx = pos.getX() - cx;
            int dz = pos.getZ() - cz;
            int dy = pos.getY() - floorY;
            if (dy < 0)
            {
                return false;
            }
            if (Math.sqrt(dx * dx + dz * dz) <= LairShape.FLOOR_EDGE + NO_SPAWN_MARGIN && dy <= DOME_HEIGHT + 1)
            {
                return true;
            }
            Direction side = tunnel.getClockWise();
            int s = dx * tunnel.getStepX() + dz * tunnel.getStepZ();
            int l = dx * side.getStepX() + dz * side.getStepZ();
            return s >= FLOOR_RADIUS && s <= LairShape.entranceDistance(descent) && Math.abs(l) <= TUNNEL_HALF_WIDTH + 1
                    && dy <= descent + TUNNEL_HEIGHT + 1;
        }

        /** Where the Colossus rises: the middle of the floor (feet position). */
        Vec3 middle()
        {
            return new Vec3(cx + 0.5D, floorY + 1.0D, cz + 0.5D);
        }

        /** True once the entities of every chunk the cavern touches are loaded (so "not found" really means gone). */
        boolean entitiesLoaded(ServerLevel level)
        {
            int reach = (int) Math.ceil(LairShape.SHELL_EDGE);
            for (int x = SectionPos.blockToSectionCoord(cx - reach); x <= SectionPos.blockToSectionCoord(cx + reach); x++)
            {
                for (int z = SectionPos.blockToSectionCoord(cz - reach); z <= SectionPos.blockToSectionCoord(cz + reach); z++)
                {
                    if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z)))
                    {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /** Remembers the lair's space from its data (after loading, and once it's built). Server thread. */
    static void refresh(ColossusLairData data)
    {
        volume = data.isPlaced() ? new Volume(data.centerX(), data.centerZ(), data.floorY(), data.tunnel(), data.descent()) : null;
    }

    static void clear()
    {
        volume = null;
    }

    @Nullable
    static Volume volume()
    {
        return volume;
    }

    // ---------------------------------------------------------------- the boss

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        Volume lair = volume;
        if (event.phase != TickEvent.Phase.END || lair == null || event.getServer().getTickCount() % CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return;
        }
        try
        {
            check(server, dross, lair);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Colossus lair: looking after the lair's Colossus failed.", e);
        }
    }

    /** One look at the lair: its Colossus (kept home, or forgotten if gone), then whether a player has walked in. */
    static void check(MinecraftServer server, ServerLevel dross, Volume lair)
    {
        ColossusLairData data = ColossusLairData.get(server);
        long now = server.overworld().getGameTime();
        UUID id = data.boss();
        if (id != null)
        {
            Entity entity = dross.getEntity(id);
            if (entity instanceof CinderColossus boss && !boss.isRemoved())
            {
                if (boss.isAlive())
                {
                    if (!lair.isHome(boss.position()))
                    {
                        bringHome(boss, lair);
                    }
                    return; // it's awake: nothing more rises
                }
                if (!data.beaten())
                {
                    data.recordDefeat(now); // dead (cooling, or a statue), but its death wasn't seen
                }
            }
            else
            {
                if (!lair.entitiesLoaded(dross))
                {
                    return; // can't tell yet whether it's there
                }
                LOGGER.info("Colossus lair: its Colossus ({}) is gone{}.", id, !data.beaten()
                        ? " without being beaten (removed), so the next player who walks in wakes a new one at once"
                        : " (beaten; its statue has crumbled)");
                data.forgetBoss();
            }
        }
        if (dross.getDifficulty() == Difficulty.PEACEFUL || !data.respawnReady(now))
        {
            return;
        }
        ServerPlayer walker = null;
        for (ServerPlayer player : dross.players())
        {
            if (!player.isSpectator() && player.isAlive() && lair.inCavern(player.position()))
            {
                walker = player;
                break;
            }
        }
        if (walker == null || !lair.entitiesLoaded(dross) || (data.boss() != null && isAliveColossus(dross, data.boss())))
        {
            return;
        }
        rise(dross, data, lair, walker, now);
    }

    private static boolean isAliveColossus(ServerLevel dross, UUID id)
    {
        return dross.getEntity(id) instanceof CinderColossus boss && boss.isAlive();
    }

    /** The lair's Colossus rises in the middle of the floor, facing the player who walked in. */
    private static void rise(ServerLevel dross, ColossusLairData data, Volume lair, ServerPlayer walker, long now)
    {
        Vec3 feet = lair.middle();
        float yaw = (float) (Mth.atan2(walker.getZ() - feet.z, walker.getX() - feet.x) * Mth.RAD_TO_DEG) - 90.0F;
        CinderColossus boss = CinderColossus.spawnBoss(dross, feet, yaw);
        if (boss == null)
        {
            LOGGER.warn("Colossus lair: the Cinder Colossus couldn't be created.");
            return;
        }
        boss.getPersistentData().putBoolean(LAIR_TAG, true);
        data.setRisen(boss.getUUID());
        dross.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()), feet.x, feet.y + 0.5D, feet.z,
                RISE_DUST, 2.0D, 0.6D, 2.0D, 0.15D);
        dross.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 1.0D, feet.z, RISE_SMOKE, 2.0D, 1.0D, 2.0D, 0.02D);
        dross.playSound(null, feet.x, feet.y, feet.z, ModSounds.COLOSSUS_RUMBLE.get(), SoundSource.HOSTILE, RISE_VOLUME, 0.8F);
        dross.playSound(null, feet.x, feet.y, feet.z, ModSounds.COLOSSUS_ROAR.get(), SoundSource.HOSTILE, RISE_VOLUME, 0.9F);
        LOGGER.info("Colossus lair: the Cinder Colossus rises at {} for {} (rise {} in this world, game time {}).",
                BlockPos.containing(feet).toShortString(), walker.getGameProfile().getName(), data.rises(), now);
    }

    /** Puts a living lair Colossus that has left its cavern back in the middle of the floor. */
    private static void bringHome(CinderColossus boss, Volume lair)
    {
        Vec3 home = lair.middle();
        LOGGER.info("Colossus lair: its Colossus was found outside the cavern at {}; it's put back in the middle.",
                boss.blockPosition().toShortString());
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.teleportTo(home.x, home.y, home.z);
    }

    /** True if this Colossus is the lair's (by the saved UUID, or its own mark). */
    static boolean isLairColossus(CinderColossus boss, ColossusLairData data)
    {
        return boss.getUUID().equals(data.boss()) || boss.getPersistentData().getBoolean(LAIR_TAG);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof CinderColossus boss) || boss.level().isClientSide || boss.isShowcase()
                || !(boss.level() instanceof ServerLevel level) || volume == null)
        {
            return;
        }
        ColossusLairData data = ColossusLairData.get(level.getServer());
        if (!isLairColossus(boss, data))
        {
            return;
        }
        long now = level.getServer().overworld().getGameTime();
        data.recordDefeat(now);
        LOGGER.info("Colossus lair: its Colossus was beaten ({}); a new one can rise after {} ticks ({} minutes of game time).",
                event.getSource().getMsgId(), LAIR_RESPAWN_TICKS, LAIR_RESPAWN_TICKS / 1200);
    }

    // ---------------------------------------------------------------- no natural spawning inside

    // LOWEST priority so this runs last and its DENY wins.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event)
    {
        Volume lair = volume;
        if (lair == null)
        {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION)
        {
            return;
        }
        if (!event.getLevel().getLevel().dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        if (lair.noSpawn(event.getPos()))
        {
            event.setResult(Event.Result.DENY);
        }
    }
}
