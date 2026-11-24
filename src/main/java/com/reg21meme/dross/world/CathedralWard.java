package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.quest.oracle.LandFallMessage;
import com.reg21meme.dross.quest.oracle.QuestNetwork;
import com.reg21meme.dross.registry.ModParticles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * THE CATHEDRAL'S BARRIER (server side; {@link CathedralSiege}). While the siege holds, the Fallen Cathedral is warded:
 * its footprint, from {@value #BELOW} blocks under its lowest layer to {@value #ABOVE} over its top. Like the bosses'
 * arena wards in the Dross ({@code boss/BossWards}): every {@value #CHECK_EVERY_TICKS} ticks each player in the Overworld
 * near it (in survival or adventure: never creative or spectator) is looked at:
 * <ul>
 *   <li>outside, standing on something: that's their <b>last spot outside</b>;</li>
 *   <li>just inside (at most {@value #PUSH_DEPTH} blocks past its edge): a firm push back out the nearest way;</li>
 *   <li>deeper in (an ender pearl, a teleport): back to their last spot outside if it's near, else in front of the
 *       cathedral's great door.</li>
 * </ul>
 * Each bounce flashes the land wards' runes and streaks where they hit it, with a soft chime (only for them), and now and
 * then a line over their hotbar says why. The knights walk through it freely.
 *
 * <p><b>Its look:</b> the locked lands' curtain ({@code dimension/ward/client/LandWardEdge}): thin streaks of electric
 * blue rising off the ground along the footprint's edge, the Dross's runes drifting up among them, wherever a player who
 * can't pass is within {@value #CURTAIN_RADIUS} blocks of it. Sent from here, to that player only (the same
 * {@code dross:ward_streak} and {@code dross:ward_rune} particles), so their game needs to know nothing about the siege.
 * With particles on Minimal they see none.</p>
 *
 * <p><b>Its fall</b> ({@link #fall}): when the last knight dies, each player near sees the stretch of its edge nearest
 * them (and the next side round a corner) stand up, crack and shatter, the land barriers' own fall
 * ({@code LandFallMessage}'s straight stretch, played by {@code dimension/ward/client/LandWardFall}).</p>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class CathedralWard
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ------------------------------------------------------------------ Tuning

    /** The players near it are looked at every this many server ticks (20 a second). */
    public static final int CHECK_EVERY_TICKS = 4;
    /** The ward reaches this far under the cathedral's lowest layer (blocks)... */
    public static final int BELOW = 16;
    /** ...and this far over its top. */
    public static final int ABOVE = 64;
    /** A player at most this many blocks past its edge is pushed back out; deeper in, they're sent back. */
    public static final double PUSH_DEPTH = 4.0D;
    /** The push: this fast out (blocks a tick; about 5 blocks back out with the lift below)... */
    public static final double PUSH_SPEED = 0.9D;
    /** ...and this fast up (a little hop over the edge). */
    public static final double PUSH_LIFT = 0.35D;
    /** A last spot outside further than this from where they are now isn't used: the great door instead. */
    public static final double LAST_SPOT_RANGE = 64.0D;
    /** Players are only looked at (and their spots remembered) within this many blocks of the footprint. */
    public static final double WATCH_RANGE = 80.0D;
    /** Sent to the great door: this many blocks in front of the footprint's front (south) edge, facing it. */
    public static final int DOOR_OUT = 6;
    /** The line over the hotbar ({@value #KEY_HINT}) at most every this many ticks for a player. */
    public static final int HINT_EVERY_TICKS = 200;
    /** The flash: this many runes and streaks, spread this far along the edge, up and down, either side of it. */
    public static final int FLASH_RUNES = 14;
    public static final int FLASH_STREAKS = 5;
    public static final double FLASH_WIDTH = 1.2D;
    public static final double FLASH_HEIGHT = 0.7D;
    public static final double FLASH_DEPTH = 0.15D;
    /** ...centred this high over the player's feet (their chest), the streaks from their feet. */
    public static final double FLASH_UP = 1.1D;
    /** The chime: soft, high. */
    public static final float CHIME_VOLUME = 0.5F;
    public static final float CHIME_PITCH = 1.6F;

    /** The curtain is sent every this many ticks... */
    public static final int CURTAIN_EVERY_TICKS = 2;
    /** ...to a player within this many blocks of the edge, for the part of the edge within this many blocks of them (under
     * the 32 blocks Minecraft sends a particle to a player from, its runes high up included). */
    public static final double CURTAIN_RADIUS = 28.0D;
    /** Streaks a tick for every block of edge round them (as the land wards' curtain)... */
    public static final double STREAKS_PER_EDGE_BLOCK = 0.09D;
    /** ...but never more than this many a tick. */
    public static final int MAX_STREAKS_PER_TICK = 8;
    /** Runes a tick for every block of edge... */
    public static final double RUNES_PER_EDGE_BLOCK = 0.006D;
    /** ...but never more than this many a tick. */
    public static final int MAX_RUNES_PER_TICK = 1;
    /** A rune starts this far up the curtain (blocks over the ground), at least and at most. */
    public static final double RUNE_MIN_LIFT = 0.6D;
    public static final double RUNE_MAX_LIFT = 12.0D;
    /** How far either way a particle may sit off the edge (blocks), so the curtain has a little depth. */
    public static final double EDGE_JITTER = 0.3D;
    /** A player more than this many blocks under the cathedral's ground sees the streaks rise round their own feet. */
    public static final int UNDERGROUND = 6;

    /** The fall: each straight stretch is this many blocks either side of its middle (LandWardFall's own). */
    public static final int FALL_HALF_LENGTH = 16;
    /** A player sees the stretch of the side nearest them, and the next nearest's too when it's this close to them. */
    public static final double FALL_SECOND_SIDE = 24.0D;

    /** The line over the hotbar when the barrier bounces a player. */
    public static final String KEY_HINT = "dross.siege.barrier";

    /** What one look did to a player (for the tests). */
    public enum Bounce
    {
        NONE, PUSHED, SENT_BACK
    }

    private record Spot(Vec3 feet, float yaw, float pitch) {}

    /** Each player's last spot outside (server thread; forgotten when they log out). */
    private static final Map<UUID, Spot> LAST_SPOT = new HashMap<>();
    /** When each player was last told why (game time). */
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    private CathedralWard() {}

    // ------------------------------------------------------------------ Each tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        int t = event.getServer().getTickCount();
        boolean look = t % CHECK_EVERY_TICKS == 0;
        boolean curtain = t % CURTAIN_EVERY_TICKS == 0;
        if (event.phase != TickEvent.Phase.END || !look && !curtain
                || event.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
        {
            // (the test server: its other tests' stand-in players visit the cathedral; the siege's tests call check() themselves)
            return;
        }
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null || overworld.players().isEmpty())
        {
            return;
        }
        SiteSpot spot = SiteFinder.spotIfReady(overworld);
        if (spot == null)
        {
            return;
        }
        BoundingBox foot = spot.footprint();
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer p : overworld.players())
        {
            if (!exempt(p) && p.isAlive() && CathedralSiege.fromFootprint(foot, p.getX(), p.getZ()) <= WATCH_RANGE)
            {
                near.add(p);
            }
        }
        if (near.isEmpty() || CathedralSiegeData.get(overworld).broken() || !CathedralSiege.cathedralBuilt(overworld, spot))
        {
            return;
        }
        for (ServerPlayer p : near)
        {
            try
            {
                if (look)
                {
                    bounce(p, spot);
                }
                if (curtain)
                {
                    curtain(p, spot);
                }
            }
            catch (RuntimeException e)
            {
                LOGGER.error("The cathedral's barrier: looking at {} failed", p.getGameProfile().getName(), e);
            }
        }
    }

    /** Never kept out (and shown no curtain): players in creative or spectator mode. */
    static boolean exempt(ServerPlayer player)
    {
        return player.isSpectator() || player.isCreative();
    }

    /**
     * One look at a player (the tests call it): remembers their spot outside, or bounces them out of the cathedral if the
     * siege holds. Creative and spectator players are left alone.
     */
    static Bounce check(ServerPlayer player)
    {
        if (exempt(player) || !player.isAlive() || player.level().dimension() != Level.OVERWORLD)
        {
            return Bounce.NONE;
        }
        ServerLevel overworld = player.serverLevel();
        SiteSpot spot = SiteFinder.spotIfReady(overworld);
        if (spot == null || CathedralSiegeData.get(overworld).broken() || !CathedralSiege.cathedralBuilt(overworld, spot))
        {
            return Bounce.NONE;
        }
        return bounce(player, spot);
    }

    private static Bounce bounce(ServerPlayer player, SiteSpot spot)
    {
        BoundingBox box = box(spot);
        Vec3 feet = player.position();
        double depth = depth(box, feet);
        if (depth <= 0.0D)
        {
            remember(player);
            return Bounce.NONE;
        }
        if (player.isPassenger())
        {
            player.stopRiding();
        }
        hint(player);
        if (depth <= PUSH_DEPTH)
        {
            Vec3 out = outward(box, feet);
            player.setDeltaMovement(out.x * PUSH_SPEED, PUSH_LIFT, out.z * PUSH_SPEED);
            if (player.connection != null) // (a player's own game moves them: it's told at once)
            {
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
            player.resetFallDistance();
            flash(player, feet.add(out.scale(depth)), out);
            return Bounce.PUSHED;
        }
        sendBack(player, spot, box, feet);
        return Bounce.SENT_BACK;
    }

    // ------------------------------------------------------------------ Its shape

    /** The warded box: the footprint, from {@value #BELOW} under its lowest layer to {@value #ABOVE} over its top. */
    static BoundingBox box(SiteSpot spot)
    {
        BoundingBox foot = spot.footprint();
        return new BoundingBox(foot.minX(), foot.minY() - BELOW, foot.minZ(), foot.maxX(), foot.maxY() + ABOVE, foot.maxZ());
    }

    /** How far inside the ward these feet are (blocks past its nearest side); 0 or less outside it. */
    static double depth(BoundingBox box, Vec3 feet)
    {
        if (feet.y < box.minY() || feet.y > box.maxY() + 1)
        {
            return 0.0D;
        }
        double west = feet.x - box.minX();
        double east = box.maxX() + 1 - feet.x;
        double north = feet.z - box.minZ();
        double south = box.maxZ() + 1 - feet.z;
        return Math.min(Math.min(west, east), Math.min(north, south));
    }

    /** The way out from here: a flat unit vector through the nearest side. */
    static Vec3 outward(BoundingBox box, Vec3 feet)
    {
        double west = feet.x - box.minX();
        double east = box.maxX() + 1 - feet.x;
        double north = feet.z - box.minZ();
        double south = box.maxZ() + 1 - feet.z;
        double least = Math.min(Math.min(west, east), Math.min(north, south));
        if (least == west)
        {
            return new Vec3(-1.0D, 0.0D, 0.0D);
        }
        if (least == east)
        {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return least == north ? new Vec3(0.0D, 0.0D, -1.0D) : new Vec3(0.0D, 0.0D, 1.0D);
    }

    /** In front of the cathedral's great door (its front faces {@code PortalCathedral.FACING}), on the ground there. */
    static Vec3 door(ServerLevel level, SiteSpot spot)
    {
        BoundingBox foot = spot.footprint();
        double cx = (foot.minX() + foot.maxX() + 1) / 2.0D;
        double cz = (foot.minZ() + foot.maxZ() + 1) / 2.0D;
        double x = switch (PortalCathedral.FACING)
        {
            case EAST -> foot.maxX() + 1 + DOOR_OUT + 0.5D;
            case WEST -> foot.minX() - DOOR_OUT - 0.5D;
            default -> cx;
        };
        double z = switch (PortalCathedral.FACING)
        {
            case SOUTH -> foot.maxZ() + 1 + DOOR_OUT + 0.5D;
            case NORTH -> foot.minZ() - DOOR_OUT - 0.5D;
            default -> cz;
        };
        BlockPos column = BlockPos.containing(x, spot.groundY(), z);
        double y = level.hasChunkAt(column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ())
                : spot.groundY() + 1;
        return new Vec3(x, y, z);
    }

    /** The way to face at the door: towards the cathedral. */
    static float doorYaw()
    {
        // Minecraft's yaw: 0 faces south (+Z), 90 west, 180 north, -90 east
        return switch (PortalCathedral.FACING)
        {
            case SOUTH -> 180.0F;
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            default -> -90.0F;
        };
    }

    // ------------------------------------------------------------------ The bounce

    /** Their spot now, if they stand on something (not in lava). */
    private static void remember(ServerPlayer player)
    {
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1D, player.getZ());
        boolean standing = player.onGround() || !player.level().getBlockState(below).getCollisionShape(player.level(), below).isEmpty();
        if (standing && !player.isInLava())
        {
            LAST_SPOT.put(player.getUUID(), new Spot(player.position(), player.getYRot(), player.getXRot()));
        }
    }

    /** Back to their last spot outside (if it's near and still outside), else in front of the great door. */
    private static void sendBack(ServerPlayer player, SiteSpot spot, BoundingBox box, Vec3 feet)
    {
        Spot last = LAST_SPOT.get(player.getUUID());
        Vec3 to;
        float yaw;
        float pitch;
        if (last != null && last.feet().distanceTo(feet) <= LAST_SPOT_RANGE && depth(box, last.feet()) <= 0.0D)
        {
            to = last.feet();
            yaw = last.yaw();
            pitch = last.pitch();
        }
        else
        {
            to = door(player.serverLevel(), spot);
            yaw = doorYaw();
            pitch = 0.0F;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(player.serverLevel(), to.x, to.y, to.z, yaw, pitch);
        Vec3 out = outward(box, to);
        flash(player, to.subtract(out), out);
    }

    /** The land ward's runes and streaks flaring along the edge at {@code at}, and a soft chime: for this player only. */
    private static void flash(ServerPlayer player, Vec3 at, Vec3 outward)
    {
        if (player.connection == null)
        {
            return;
        }
        ServerLevel level = player.serverLevel();
        double sx = Math.abs(outward.z) * FLASH_WIDTH + FLASH_DEPTH;
        double sz = Math.abs(outward.x) * FLASH_WIDTH + FLASH_DEPTH;
        level.sendParticles(player, ModParticles.WARD_RUNE.get(), true, at.x, at.y + FLASH_UP, at.z, FLASH_RUNES, sx, FLASH_HEIGHT, sz, 0.0D);
        level.sendParticles(player, ModParticles.WARD_STREAK.get(), true, at.x, at.y, at.z, FLASH_STREAKS, sx, 0.1D, sz, 0.0D);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH);
    }

    /** Now and then a line over their hotbar: why they can't go in. */
    private static void hint(ServerPlayer player)
    {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_HINT.get(player.getUUID());
        if (last == null || now - last >= HINT_EVERY_TICKS || now < last)
        {
            LAST_HINT.put(player.getUUID(), now);
            player.displayClientMessage(Component.translatable(KEY_HINT).withStyle(s -> s.withColor(DrossColors.SIEGE_WARD_TEXT)), true);
        }
    }

    // ------------------------------------------------------------------ The curtain

    /** One side of the footprint near a player: the line it's on, the stretch of it near them, and which way it runs. */
    record Side(double fixed, double from, double to, boolean alongX)
    {
        double length()
        {
            return this.to - this.from;
        }
    }

    /**
     * The four sides of the footprint, each cut to the stretch within {@code radius} of the point (empty ones left out).
     * A side along X runs from x = from to x = to at z = fixed; along Z, the other way round.
     */
    static List<Side> sidesNear(BoundingBox foot, double px, double pz, double radius)
    {
        List<Side> sides = new ArrayList<>();
        double x0 = foot.minX();
        double x1 = foot.maxX() + 1;
        double z0 = foot.minZ();
        double z1 = foot.maxZ() + 1;
        addSide(sides, z0, x0, x1, true, pz, px, radius);   // north
        addSide(sides, z1, x0, x1, true, pz, px, radius);   // south
        addSide(sides, x0, z0, z1, false, px, pz, radius);  // west
        addSide(sides, x1, z0, z1, false, px, pz, radius);  // east
        return sides;
    }

    private static void addSide(List<Side> sides, double fixed, double from, double to, boolean alongX, double pAcross, double pAlong,
                                double radius)
    {
        double across = Math.abs(pAcross - fixed);
        if (across > radius)
        {
            return;
        }
        double half = Math.sqrt(radius * radius - across * across);
        double lo = Math.max(from, pAlong - half);
        double hi = Math.min(to, pAlong + half);
        if (hi > lo)
        {
            sides.add(new Side(fixed, lo, hi, alongX));
        }
    }

    /** The curtain along the edge near this player, for them only: a few streaks rising and now and then a rune. */
    private static void curtain(ServerPlayer player, SiteSpot spot)
    {
        if (player.connection == null)
        {
            return;
        }
        BoundingBox box = box(spot);
        if (player.getY() < box.minY() || player.getY() > box.maxY() + 1)
        {
            return;
        }
        List<Side> sides = sidesNear(spot.footprint(), player.getX(), player.getZ(), CURTAIN_RADIUS);
        double total = 0.0D;
        for (Side side : sides)
        {
            total += side.length();
        }
        if (total <= 0.0D)
        {
            return;
        }
        RandomSource random = player.getRandom();
        int streaks = count(total * STREAKS_PER_EDGE_BLOCK * CURTAIN_EVERY_TICKS, MAX_STREAKS_PER_TICK * CURTAIN_EVERY_TICKS, random);
        int runes = count(total * RUNES_PER_EDGE_BLOCK * CURTAIN_EVERY_TICKS, MAX_RUNES_PER_TICK * CURTAIN_EVERY_TICKS, random);
        double ground = spot.groundY() + 1.0D;
        for (int i = 0; i < streaks + runes; i++)
        {
            boolean rune = i >= streaks;
            double pick = random.nextDouble() * total;
            Side side = sides.get(sides.size() - 1);
            for (Side s : sides)
            {
                if (pick <= s.length())
                {
                    side = s;
                    break;
                }
                pick -= s.length();
            }
            double along = side.from() + Math.min(pick, side.length());
            double across = side.fixed() + (random.nextDouble() * 2.0D - 1.0D) * EDGE_JITTER;
            double x = side.alongX() ? along : across;
            double z = side.alongX() ? across : along;
            double y = player.getY() < ground - UNDERGROUND ? player.getY() - 1.0D + random.nextDouble() : ground;
            if (rune)
            {
                y += RUNE_MIN_LIFT + random.nextDouble() * (RUNE_MAX_LIFT - RUNE_MIN_LIFT);
            }
            SimpleParticleType type = rune ? ModParticles.WARD_RUNE.get() : ModParticles.WARD_STREAK.get();
            player.serverLevel().sendParticles(player, type, false, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** {@code expected} rounded up or down at random (so a fraction still comes now and then), at most {@code max}. */
    private static int count(double expected, int max, RandomSource random)
    {
        int whole = (int) expected;
        if (random.nextDouble() < expected - whole)
        {
            whole++;
        }
        return Math.min(whole, max);
    }

    // ------------------------------------------------------------------ The fall

    /** Each player near sees the stretch of the edge nearest them fall (and the next side's, round a corner). */
    static void fall(ServerLevel level, SiteSpot spot, Collection<ServerPlayer> players)
    {
        BoundingBox foot = spot.footprint();
        for (ServerPlayer player : players)
        {
            if (player.level() != level)
            {
                continue;
            }
            for (LandFallMessage stretch : stretches(foot, player.getX(), player.getZ()))
            {
                QuestNetwork.sendTo(player, stretch);
            }
        }
    }

    /**
     * The straight stretches of curtain that fall for a player here: the side of the footprint nearest them (its
     * {@value #FALL_HALF_LENGTH} blocks either way of the point nearest them, kept on the side), and the next nearest
     * side's too if it's within {@value #FALL_SECOND_SIDE} blocks. Half a block outside the edge.
     */
    static List<LandFallMessage> stretches(BoundingBox foot, double px, double pz)
    {
        double x0 = foot.minX();
        double x1 = foot.maxX() + 1;
        double z0 = foot.minZ();
        double z1 = foot.maxZ() + 1;
        // the four sides: {line, from, to, along X?, outside (the block row just outside it)}
        double[][] sides = {
                {z0, x0, x1, 1, foot.minZ() - 1},
                {z1, x0, x1, 1, foot.maxZ() + 1},
                {x0, z0, z1, 0, foot.minX() - 1},
                {x1, z0, z1, 0, foot.maxX() + 1}};
        double[] distances = new double[4];
        Integer[] order = {0, 1, 2, 3};
        for (int i = 0; i < 4; i++)
        {
            double[] s = sides[i];
            boolean alongX = s[3] == 1;
            double along = alongX ? px : pz;
            double across = alongX ? pz : px;
            double nearest = Math.max(s[1], Math.min(s[2], along));
            distances[i] = Math.hypot(along - nearest, across - s[0]);
        }
        java.util.Arrays.sort(order, (a, b) -> Double.compare(distances[a], distances[b]));
        List<LandFallMessage> out = new ArrayList<>();
        for (int k = 0; k < 2; k++)
        {
            int i = order[k];
            if (k == 1 && distances[i] > FALL_SECOND_SIDE)
            {
                break;
            }
            double[] s = sides[i];
            boolean alongX = s[3] == 1;
            double from = s[1] + FALL_HALF_LENGTH;
            double to = s[2] - 1 - FALL_HALF_LENGTH;
            double along = alongX ? px : pz;
            int middle = (int) Math.floor(from > to ? (s[1] + s[2]) / 2.0D : Math.max(from, Math.min(to, along)));
            int outside = (int) s[4];
            out.add(alongX ? new LandFallMessage(-1, middle, outside, 1.0F, 0.0F) : new LandFallMessage(-1, outside, middle, 0.0F, 1.0F));
        }
        return out;
    }

    // ------------------------------------------------------------------ Forgetting

    /** For the tests: forgets a player's last spot outside and when they were last told why. */
    static void forget(UUID player)
    {
        LAST_SPOT.remove(player);
        LAST_HINT.remove(player);
    }

    @SubscribeEvent
    public static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        LAST_SPOT.clear();
        LAST_HINT.clear();
    }
}
