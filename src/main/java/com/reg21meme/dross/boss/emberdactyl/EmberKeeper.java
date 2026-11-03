package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModEntities;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.emberdactyl.EmberTuning.*;

/**
 * Brings each built volcano its egg and its Emberdactyl (the user's plan: it rests on its volcano guarding its egg), once a
 * player is within {@link EmberTuning#KEEPER_RANGE} blocks and the volcano's land has its entities loaded (never on
 * Peaceful): the egg in the nest, the adult perched on its ledge on the crater's rim opposite the lookout, facing out. Its fight's
 * state is saved with the volcano ({@link EmberVolcanoData.State}):
 * <ul>
 *   <li>{@code WAITING}: nothing there yet: both come ({@code ADULT}).</li>
 *   <li>{@code ADULT}: a missing egg comes again; a new adult only if its own (known by its id, wherever it has flown)
 *       was removed without dying (Peaceful), never while it's out of sight or unloaded somewhere.</li>
 *   <li>{@code YOUNG} (the adult died): if its egg didn't hatch (the server stopped first), it hatches now; a new young
 *       only on the same terms.</li>
 *   <li>{@code BEATEN} (the young died): never again (the user's rule for every boss: no respawn).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class EmberKeeper
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmberKeeper() {}

    /** The ledges of the Emberdactyls about, by their ids: any volcano's (an arena's too, in any dimension), kept fresh by
     *  their own ticks ({@link #keepLedge}; spawns are checked on the world generator's threads too). */
    private static final Map<UUID, Ledge> LIVE_LEDGES = new ConcurrentHashMap<>();

    private record Ledge(ResourceKey<Level> level, Vec3 perch)
    {
    }

    /** An Emberdactyl's ledge while it's about (its perch; none: it has no ledge). */
    public static void keepLedge(EmberBoss boss, @Nullable Vec3 perch)
    {
        if (perch == null)
        {
            LIVE_LEDGES.remove(boss.getUUID());
        }
        else
        {
            LIVE_LEDGES.put(boss.getUUID(), new Ledge(boss.level().dimension(), perch));
        }
    }

    /** It's gone (dead or removed). */
    public static void dropLedge(UUID boss)
    {
        LIVE_LEDGES.remove(boss);
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event)
    {
        LIVE_LEDGES.clear();
    }

    /**
     * Nothing spawns by itself on the Emberdactyl's ledge (the user, November 2: "we gave the Emberdactyl a little platform
     * to hang on ... make sure no mobs actually spawn on that platform"; then "mobs can spawn on the surface, I saw it
     * happen"): natural spawns within {@link EmberVolcanoTuning#PERCH_NO_SPAWN} blocks of a ledge (and a few blocks over
     * or under it) are refused: every built volcano's in the Dross (one saved before it had a ledge: where its Emberdactyl
     * perches on its rim), and the ledge of every Emberdactyl about, in any dimension (an arena's volcano isn't saved
     * with the world's). LOWEST priority, so its DENY wins.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onSpawnPlacementCheck(net.minecraftforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck event)
    {
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION)
        {
            return;
        }
        if (onLedge(event.getLevel().getLevel(), event.getPos()))
        {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** True if {@code pos} is on (or just over or under) an Emberdactyl's ledge, where nothing may spawn by itself. */
    public static boolean onLedge(ServerLevel level, BlockPos pos)
    {
        for (Ledge ledge : LIVE_LEDGES.values())
        {
            if (ledge.level() == level.dimension() && near(pos, ledge.perch()))
            {
                return true;
            }
        }
        if (level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            for (EmberVolcanoData.Volcano volcano : EmberVolcanoData.get(level.getServer()).all())
            {
                if (near(pos, ledgeOf(volcano)))
                {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean near(BlockPos pos, Vec3 perch)
    {
        return Math.abs(pos.getY() - perch.y) <= EmberVolcanoTuning.PERCH_NO_SPAWN_HEIGHT
                && Math.hypot(pos.getX() + 0.5D - perch.x, pos.getZ() + 0.5D - perch.z) <= EmberVolcanoTuning.PERCH_NO_SPAWN;
    }

    /** A volcano's ledge: its saved perch, or (saved before it had one) where {@link #perch} puts its Emberdactyl, at its
     *  rim's height (no height map: spawns are checked off the server thread too). */
    private static Vec3 ledgeOf(EmberVolcanoData.Volcano volcano)
    {
        if (volcano.perch() != null)
        {
            return volcano.perch();
        }
        Vec3 centre = new Vec3(volcano.ground().getX() + 0.5D, 0.0D, volcano.ground().getZ() + 0.5D);
        Vec3 away = new Vec3(centre.x - volcano.lookout().x, 0.0D, centre.z - volcano.lookout().z);
        away = away.lengthSqr() < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        Vec3 spot = centre.add(away.scale(PERCH_OUT));
        return new Vec3(spot.x, volcano.rimY(), spot.z);
    }

    /** A fall after its talon grab never takes more than half of its victim's health ({@link EmberBoss#capGrabFall}). */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event)
    {
        LivingEntity e = event.getEntity();
        if (!e.level().isClientSide)
        {
            event.setDistance(EmberBoss.capGrabFall(e, event.getDistance(), event.getDamageMultiplier()));
        }
    }

    /** A player carried off who logs out is let go first (a player's vehicle would otherwise leave with them). */
    @SubscribeEvent
    public static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity().getVehicle() instanceof EmberBoss)
        {
            event.getEntity().stopRiding();
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0)
        {
            EmberBoss.sweepBars();
        }
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % KEEPER_CHECK_TICKS != 0)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty() || dross.getDifficulty() == Difficulty.PEACEFUL)
        {
            return;
        }
        EmberVolcanoData data = EmberVolcanoData.get(server);
        for (EmberVolcanoData.Volcano volcano : data.all())
        {
            EmberVolcanoData.State state = data.state(volcano.id());
            if (state == EmberVolcanoData.State.BEATEN || !playerNear(dross, volcano))
            {
                continue;
            }
            try
            {
                keep(dross, data, volcano, state);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Emberdactyl volcanoes: looking after the volcano at {} failed.", volcano.ground().toShortString(), e);
            }
        }
    }

    private static boolean playerNear(ServerLevel dross, EmberVolcanoData.Volcano volcano)
    {
        for (ServerPlayer p : dross.players())
        {
            if (!p.isSpectator() && volcano.distanceTo(p.getX(), p.getZ()) <= KEEPER_RANGE)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean loaded(ServerLevel level, Vec3 at)
    {
        BlockPos pos = BlockPos.containing(at);
        return level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos));
    }

    private static void keep(ServerLevel dross, EmberVolcanoData data, EmberVolcanoData.Volcano volcano, EmberVolcanoData.State state)
    {
        Vec3 nest = volcano.egg();
        Perch perch = perch(dross, volcano);
        if (!loaded(dross, nest) || !loaded(dross, perch.spot()))
        {
            return;
        }
        AABB area = new AABB(nest, nest).inflate(130.0D, 140.0D, 130.0D);
        long id = volcano.id();
        EmberEgg egg = first(dross.getEntitiesOfClass(EmberEgg.class, area, e -> e.volcano() == id));
        switch (state)
        {
            case WAITING, ADULT ->
            {
                if (egg == null)
                {
                    egg = EmberEgg.lay(dross, nest, perch.yaw(), id);
                }
                Emberdactyl adult = boss(dross, data, id, Emberdactyl.class, area);
                if (adult == null && data.mayBring(id))
                {
                    adult = spawnAdult(dross, volcano, perch);
                    if (adult != null)
                    {
                        data.setBoss(id, adult.getUUID());
                    }
                }
                data.setState(id, EmberVolcanoData.State.ADULT);
            }
            case YOUNG ->
            {
                if (!dross.getEntitiesOfClass(Emberdactyl.class, area, e -> e.volcano() == id && e.isDeadOrDying()).isEmpty())
                {
                    return;   // (still breaking apart: its egg hatches as it does)
                }
                if (egg == null)
                {
                    egg = EmberEgg.lay(dross, nest, perch.yaw(), id);
                }
                if (egg != null && !egg.isHatched())
                {
                    egg.hatch();
                }
                EmberYoung young = boss(dross, data, id, EmberYoung.class, area);
                if (young == null && data.mayBring(id))
                {
                    young = spawnYoung(dross, volcano);
                    if (young != null)
                    {
                        data.setBoss(id, young.getUUID());
                    }
                }
            }
            default ->
            {
            }
        }
    }

    /** The volcano's own Emberdactyl of this kind: the recorded one wherever it is loaded, else one of this volcano near
     *  its nest (then recorded), else null. */
    @Nullable
    private static <T extends EmberBoss> T boss(ServerLevel dross, EmberVolcanoData data, long id, Class<T> kind, AABB area)
    {
        UUID known = data.boss(id);
        if (known != null && kind.isInstance(dross.getEntity(known)))
        {
            return kind.cast(dross.getEntity(known));
        }
        T near = first(dross.getEntitiesOfClass(kind, area, e -> e.volcano() == id && !e.isDeadOrDying()));
        if (near != null && !near.getUUID().equals(known))
        {
            data.setBoss(id, near.getUUID());
        }
        return near;
    }

    @Nullable
    private static <T> T first(List<T> list)
    {
        return list.isEmpty() ? null : list.get(0);
    }

    /** Where the adult perches: on its ledge on the crater's rim opposite the lookout, facing outwards. */
    record Perch(Vec3 spot, float yaw)
    {
    }

    static Perch perch(ServerLevel level, EmberVolcanoData.Volcano volcano)
    {
        if (volcano.perch() != null)
        {
            return new Perch(volcano.perch(), volcano.perchYaw());
        }
        // (a volcano saved before it had a ledge: on the rim, facing the nest)
        Vec3 centre = new Vec3(volcano.ground().getX() + 0.5D, 0.0D, volcano.ground().getZ() + 0.5D);
        Vec3 away = new Vec3(centre.x - volcano.lookout().x, 0.0D, centre.z - volcano.lookout().z);
        away = away.lengthSqr() < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        Vec3 spot = centre.add(away.scale(PERCH_OUT));
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(spot.x), Mth.floor(spot.z));
        float yaw = (float) (Mth.atan2(-away.z, -away.x) * Mth.RAD_TO_DEG) - 90.0F;
        return new Perch(new Vec3(spot.x, y, spot.z), yaw);
    }

    @Nullable
    static Emberdactyl spawnAdult(ServerLevel level, EmberVolcanoData.Volcano volcano, Perch perch)
    {
        Emberdactyl adult = ModEntities.EMBERDACTYL.get().create(level);
        if (adult == null)
        {
            return null;
        }
        adult.moveTo(perch.spot().x, perch.spot().y, perch.spot().z, perch.yaw(), 0.0F);
        adult.setYHeadRot(perch.yaw());
        adult.setYBodyRot(perch.yaw());
        adult.setHome(volcano.egg(), volcano.egg(), perch.spot(), perch.yaw(), volcano.id());
        adult.finalizeSpawn(level, level.getCurrentDifficultyAt(adult.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        level.addFreshEntity(adult);
        LOGGER.info("Emberdactyl volcanoes: the Emberdactyl perches on its volcano at {} (patch {}), guarding its egg.",
                volcano.ground().toShortString(), volcano.id());
        return adult;
    }

    @Nullable
    static EmberYoung spawnYoung(ServerLevel level, EmberVolcanoData.Volcano volcano)
    {
        EmberYoung young = ModEntities.EMBERYOUNG.get().create(level);
        if (young == null)
        {
            return null;
        }
        Vec3 over = volcano.egg().add(0.0D, 16.0D, 0.0D);
        young.moveTo(over.x, over.y, over.z, 0.0F, 0.0F);
        young.setHome(volcano.egg(), volcano.egg(), null, 0.0F, volcano.id());
        young.finalizeSpawn(level, level.getCurrentDifficultyAt(young.blockPosition()), MobSpawnType.STRUCTURE, null, null);
        level.addFreshEntity(young);
        return young;
    }
}
