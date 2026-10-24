package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
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
 *       counts, so testers see him) comes within {@link #APPEAR_RANGE} blocks of his yard in front of his lodge
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

    /** One look: every Huntsman's village with a player near its yard gets its pair if it should. */
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
        if (entry != null && entry.huntsman() != null)
        {
            Entity there = dross.getEntity(entry.huntsman());
            if (there != null && there.isAlive())
            {
                return;                                   // he's there (or out chasing someone, loaded)
            }
            if (there == null && !nobodyFar(dross, plan))
            {
                return;                                   // he may be out of loaded land with someone: not yet
            }
        }
        bring(dross, plan, data);
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

    /** Brings the village a new pair, calm, him in the saddle: on his patrol, at its loaded point furthest from the
     * nearest player (a spot there the moose fits), riding on to the next; else round his yard. */
    @Nullable
    static Huntsman bring(ServerLevel dross, RimefallVillages.VillagePlan plan, HuntsmanVillageData data)
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
        Huntsman h = HuntsmanPair.spawn(dross, at, yaw);
        if (h == null)
        {
            return null;
        }
        int generation = data.spawned(plan.id(), h.getUUID());
        h.setHome(plan.id(), plan.home(), generation);
        h.setPatrol(loop, startIndex + 1);                // (his too: it's the chase's leash, whichever is left)
        if (h.getVehicle() instanceof AlphaMoose moose)
        {
            moose.setHome(plan.id(), plan.home(), generation);
            moose.setPatrol(loop, startIndex + 1);
        }
        LOGGER.info("Hunter villages: the Huntsman came home to his village at {}", plan.ground().toShortString());
        return h;
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
