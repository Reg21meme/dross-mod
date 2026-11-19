package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.BossLocks;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.HuntMob;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.huntsman.HuntsmanPair;
import com.reg21meme.dross.boss.huntsman.HuntsmanTuning;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Huntsman at home in his villages (the user, October 23, 2026: "make sure the hunter spawns in his village"; their
 * pick, "home, simple"):
 * <ul>
 *   <li><b>He comes</b> with his Alpha Moose, him in the saddle, calm, when a player (not a spectator; creative
 *       counts, so testers see him) whose Oracle quest has reached him ({@link #bringsHim}: anyone else counts as
 *       nobody, and the pair never fights them) comes within {@link #APPEAR_RANGE} blocks of his yard in front of his lodge
 *       ({@link RimefallVillages.VillagePlan#home}) and its ground is loaded and ticking: on his patrol round the
 *       village ({@link #patrolLoop}), at its loaded point furthest from the nearest player, riding on to the next (the
 *       user, October 23, 2026: "the Huntsman should be patrolling the village ... when he comes into view he should
 *       already be moving around"); in his yard if no point of it is loaded. Never on Peaceful. Calm, they ride the
 *       loop ({@code PatrolGoal}); the fight starts when he spots a foe in Rimefall, as anywhere. They chase up to
 *       {@link HuntsmanTuning#CHASE_PAST_LOOP} blocks past the loop; a foe that gets away (out of range out there, or
 *       out of Rimefall) is let go and they head home, still hurt; with nobody near for
 *       {@link HuntsmanTuning#BREAK_OFF_TICKS} they're back at full health ({@code HuntFight}) and take the loop up
 *       again.</li>
 *   <li><b>One pair per village</b>: his UUID is kept for the village ({@link HuntsmanVillageData}) and his village on
 *       both of them, with the pair's number; an older pair that loads after a newer one came is removed as it
 *       loads.</li>
 *   <li><b>Asleep at home</b> (the user, November 18, 2026: "before he's unlocked he should be sleeping inside his home
 *       ... the moose sleeps ... once [the] quest [is] complete all Huntsmen across the map are now in patrol mode"):
 *       with no player near whose Oracle quest has reached him ({@link #wakesHim}; creative doesn't count), the pair
 *       comes asleep instead: him on his bed in his lodge, the Alpha Moose lying in its stall, his gear lying in front of
 *       his door ({@link HuntsmanAsleep}). The first time a player near has reached his quest they wake onto their
 *       patrol, and they never go back to sleep.</li>
 *   <li><b>Beaten</b>: both of them dead, any way ({@code /kill} too): the village stays empty for good (no respawn,
 *       like the other bosses). Removed without dying (Peaceful) isn't beaten: the next player near finds a new pair.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HuntsmanVillages
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A player this near his yard brings him (about the game's usual simulation distance). */
    public static final double APPEAR_RANGE = 160.0D;
    /** The keeper looks this often (ticks). */
    private static final int CHECK_TICKS = 40;
    /** Looking for a spot the moose fits at, round his yard: this far out at most. */
    private static final int SPOT_SEARCH = 12;

    private HuntsmanVillages() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_TICKS != 0)
        {
            return;
        }
        ServerLevel dross = event.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty() || dross.getDifficulty() == Difficulty.PEACEFUL)
        {
            return;
        }
        try
        {
            check(dross);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Hunter villages: looking after the Huntsman's villages failed.", e);
        }
    }

    /** One look: every Huntsman's village with a player near its yard gets its pair if it should (asleep, or on his
     * patrol once a player near has reached his quest). */
    public static void check(ServerLevel dross)
    {
        BossSites sites = BossSites.forLevel(dross);
        Set<Long> done = new HashSet<>();
        for (ServerPlayer player : dross.players())
        {
            if (player.isSpectator() || !player.isAlive())
            {
                continue;
            }
            for (Site site : sites.sitesNear(DrossBiomes.RIMEFALL, player.getBlockX(), player.getBlockZ(),
                    (int) APPEAR_RANGE + 160))
            {
                if (site.use() != BossSites.HUNTSMAN_VILLAGE || !(site.detail() instanceof RimefallVillages.VillagePlan plan)
                        || !done.add(plan.id()))
                {
                    continue;
                }
                BlockPos home = plan.home();
                if (player.position().distanceToSqr(Vec3.atBottomCenterOf(home)) <= APPEAR_RANGE * APPEAR_RANGE)
                {
                    keep(dross, plan);
                }
            }
        }
    }

    /**
     * True if this player is one he may meet (and fight) at home: alive, not a spectator, and their Oracle quest has
     * reached him, or they're in creative ({@code boss.BossLocks}). A player he's locked for counts as nobody in his
     * fight.
     */
    public static boolean bringsHim(ServerPlayer player)
    {
        return !player.isSpectator() && player.isAlive() && BossLocks.mayMeet(player, OracleBoss.HUNTSMAN);
    }

    /**
     * True if this player near wakes him (and his moose) onto their patrol: alive, not a spectator, their Oracle quest at
     * or past his ("The Hunter Hunted": First Frost handed in). Creative alone doesn't (testers see him asleep until their
     * quest gets there: {@code /dross quest set the_hunter_hunted}).
     */
    public static boolean wakesHim(ServerPlayer player)
    {
        return !player.isSpectator() && player.isAlive() && OracleProgress.isAtOrPast(player, OracleBoss.HUNTSMAN.quest());
    }

    /** True if a player within {@link #APPEAR_RANGE} of the village's yard wakes him ({@link #wakesHim}). */
    static boolean anyWaker(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        Vec3 home = Vec3.atBottomCenterOf(plan.home());
        for (ServerPlayer p : dross.players())
        {
            if (wakesHim(p) && p.position().distanceToSqr(home) <= APPEAR_RANGE * APPEAR_RANGE)
            {
                return true;
            }
        }
        return false;
    }

    /** The village's pair, if it should have one now: there already, beaten, or brought now. */
    static void keep(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        HuntsmanVillageData data = HuntsmanVillageData.get(dross);
        HuntsmanVillageData.Entry entry = data.find(plan.id());
        if (entry != null && entry.beaten())
        {
            return;
        }
        BlockPos home = plan.home();
        if (!dross.isPositionEntityTicking(home))
        {
            return;                                       // (its ground isn't loaded with its entities yet)
        }
        boolean awake = anyWaker(dross, plan);
        if (entry != null && entry.huntsman() != null)
        {
            Entity there = dross.getEntity(entry.huntsman());
            if (there instanceof Huntsman h && h.isAlive() && h.isAsleep())
            {
                if (awake)
                {
                    HuntsmanAsleep.wake(dross, plan, h);    // (once: they never go back to sleep)
                }
                return;
            }
            if (there != null && there.isAlive())
            {
                return;                                   // he's there (or out chasing someone, loaded)
            }
            if (there == null && !nobodyFar(dross, plan))
            {
                return;                                   // he may be out of loaded land with someone: not yet
            }
        }
        if (awake)
        {
            bring(dross, plan, data);
        }
        else
        {
            HuntsmanAsleep.bring(dross, plan, data);
        }
    }

    /** True if no player is far enough from the village that the pair could be with them in land not loaded. */
    private static boolean nobodyFar(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        Vec3 home = Vec3.atBottomCenterOf(plan.home());
        for (ServerPlayer p : dross.players())
        {
            if (p.position().distanceToSqr(home) > APPEAR_RANGE * APPEAR_RANGE * 4.0D)
            {
                return false;
            }
        }
        return true;
    }

    /** His patrol round {@code plan}'s village: {@code HuntsmanTuning.PATROL_POINTS} points on a rounded ring
     * {@code PATROL_OUT} blocks outside its footprint (a superellipse: nearly its rectangle, its corners rounded), in
     * order round it (their heights left at 0: the ground's is found as he rides). */
    public static List<BlockPos> patrolLoop(RimefallVillages.VillagePlan plan)
    {
        BoundingBox b = plan.footprint();
        double cx = (b.minX() + b.maxX()) / 2.0D;
        double cz = (b.minZ() + b.maxZ()) / 2.0D;
        double rx = (b.maxX() - b.minX()) / 2.0D + HuntsmanTuning.PATROL_OUT;
        double rz = (b.maxZ() - b.minZ()) / 2.0D + HuntsmanTuning.PATROL_OUT;
        double power = 2.0D / HuntsmanTuning.PATROL_ROUNDNESS;
        List<BlockPos> out = new ArrayList<>();
        for (int i = 0; i < HuntsmanTuning.PATROL_POINTS; i++)
        {
            double a = 2.0D * Math.PI * i / HuntsmanTuning.PATROL_POINTS;
            double c = Math.cos(a);
            double s = Math.sin(a);
            double x = cx + rx * Math.signum(c) * Math.pow(Math.abs(c), power);
            double z = cz + rz * Math.signum(s) * Math.pow(Math.abs(s), power);
            out.add(new BlockPos((int) Math.floor(x), 0, (int) Math.floor(z)));
        }
        return out;
    }

    /** Where a pair starts on its patrol: the spot, the way it faces, the point it rides to next, the loop. */
    record Start(Vec3 at, float yaw, int next, List<BlockPos> loop)
    {
    }

    /** Brings the village a new pair, calm, him in the saddle: on his patrol ({@link #patrolStart}). */
    @Nullable
    static Huntsman bring(ServerLevel dross, RimefallVillages.VillagePlan plan, HuntsmanVillageData data)
    {
        Start start = patrolStart(dross, plan);
        if (start == null)
        {
            return null;
        }
        Huntsman h = HuntsmanPair.spawn(dross, start.at(), start.yaw());
        if (h == null)
        {
            return null;
        }
        int generation = data.spawned(plan.id(), h.getUUID());
        h.setHome(plan.id(), plan.home(), generation);
        h.setPatrol(start.loop(), start.next());           // (his too: it's the chase's leash, whichever is left)
        if (h.getVehicle() instanceof AlphaMoose moose)
        {
            moose.setHome(plan.id(), plan.home(), generation);
            moose.setPatrol(start.loop(), start.next());
        }
        LOGGER.info("Hunter villages: the Huntsman came home to his village at {}", plan.ground().toShortString());
        return h;
    }

    /** Where a pair starts its patrol: at the loop's loaded point furthest from the nearest player (a spot there the
     * moose fits), riding on to the next; else round his yard. Null if there's no room. */
    @Nullable
    static Start patrolStart(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        List<BlockPos> loop = patrolLoop(plan);
        Vec3 homeAt = Vec3.atBottomCenterOf(plan.home());
        ServerPlayer near = null;
        for (ServerPlayer p : dross.players())
        {
            if (!p.isSpectator() && (near == null || p.distanceToSqr(homeAt) < near.distanceToSqr(homeAt)))
            {
                near = p;
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < loop.size(); i++)
        {
            order.add(i);
        }
        ServerPlayer watcher = near;
        if (watcher != null)
        {
            order.sort((a, b) -> Double.compare(watcher.distanceToSqr(Vec3.atCenterOf(loop.get(b))),
                    watcher.distanceToSqr(Vec3.atCenterOf(loop.get(a)))));
        }
        Optional<BlockPos> spot = Optional.empty();
        int startIndex = -1;
        for (int i : order)
        {
            BlockPos p = loop.get(i);
            BlockPos ground = new BlockPos(p.getX(), dross.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()),
                    p.getZ());
            if (!dross.isPositionEntityTicking(ground))
            {
                continue;
            }
            spot = spot(dross, ground);
            if (spot.isPresent())
            {
                startIndex = i;
                break;
            }
        }
        if (spot.isEmpty())
        {
            spot = spot(dross, plan.home());
        }
        if (spot.isEmpty())
        {
            return null;
        }
        Vec3 at = Vec3.atBottomCenterOf(spot.get());
        BlockPos toward = startIndex >= 0 ? loop.get((startIndex + 1) % loop.size()) : plan.wayIn();
        float yaw = (float) (Math.atan2(toward.getZ() - at.z, toward.getX() - at.x) * 180.0D / Math.PI) - 90.0F;
        return new Start(at, yaw, startIndex + 1, loop);
    }

    /** The nearest spot round {@code home} where the Alpha Moose stands on the ground with room round it. */
    public static Optional<BlockPos> spot(ServerLevel dross, BlockPos home)
    {
        var type = ModEntities.ALPHA_MOOSE.get();
        for (int r = 0; r <= SPOT_SEARCH; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                    {
                        continue;
                    }
                    int x = home.getX() + dx;
                    int z = home.getZ() + dz;
                    int y = dross.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    if (Math.abs(y - home.getY()) > 2)
                    {
                        continue;                         // (not down in a courtyard or up on a roof)
                    }
                    BlockPos feet = new BlockPos(x, y, z);
                    if (dross.getBlockState(feet.below()).isFaceSturdy(dross, feet.below(), net.minecraft.core.Direction.UP)
                            && dross.noCollision(type.getAABB(x + 0.5D, y, z + 0.5D).inflate(0.2D, 0.0D, 0.2D)))
                    {
                        return Optional.of(feet);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** True if the Huntsman of this village has been beaten there (it stays empty for good). For the commands. */
    public static boolean isBeaten(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        HuntsmanVillageData.Entry e = HuntsmanVillageData.get(dross).find(plan.id());
        return e != null && e.beaten();
    }

    // ---------------------------------------------------------------- what happens to them

    /** Both of them are dead ({@code HuntFight}, any death): beaten at his village, for good. */
    public static void beaten(HuntMob mob)
    {
        if (mob.homeVillage() != 0 && mob.level() instanceof ServerLevel level && level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            HuntsmanVillageData.get(level).beaten(mob.homeVillage());
        }
    }

    /** A broken-off fight brought back a Huntsman who had died: he's the village's Huntsman now. */
    public static void replaced(Huntsman h)
    {
        if (h.homeVillage() != 0 && h.level() instanceof ServerLevel level && level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            HuntsmanVillageData.get(level).replaced(h.homeVillage(), h.getUUID());
        }
    }

    /** The Huntsman of a village removed without dying (Peaceful): not beaten, a new pair next time. */
    public static void removed(Huntsman h)
    {
        if (h.homeVillage() != 0 && h.level() instanceof ServerLevel level && level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            HuntsmanVillageData data = HuntsmanVillageData.get(level);
            HuntsmanVillageData.Entry e = data.find(h.homeVillage());
            if (e != null && !e.beaten() && h.getUUID().equals(e.huntsman()))
            {
                data.gone(h.homeVillage());
            }
        }
    }

    /** An older pair of a village that has a newer one (or is beaten) doesn't come back as it loads. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof HuntMob mob) || mob.homeVillage() == 0
                || !(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        HuntsmanVillageData.Entry e = HuntsmanVillageData.get(level).find(mob.homeVillage());
        if (e != null && (e.beaten() && mob.isAlive() && event.loadedFromDisk() || mob.homeGeneration() < e.generation()))
        {
            event.setCanceled(true);
        }
    }
}
