package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.emberdactyl.CraterWard;
import com.reg21meme.dross.boss.lair.LairWard;
import com.reg21meme.dross.boss.ratking.lair.RatCaveWard;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import com.reg21meme.dross.quest.oracle.OracleRules;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE ARENA WARDS (QUEST_PLAN.md, "Locked bosses"; server side). A boss's arena at home is warded: a player the boss is
 * locked for ({@link BossLocks}: their Oracle quest hasn't reached it) who walks in is bounced back out, with a flash of
 * the land ward's runes where they hit it and a soft chime, and no message. The arenas (each found by its own boss's
 * code): the Colossus's cavern, its floor and passage ({@link LairWard}); the Rat King's cave ({@link RatCaveWard}); the
 * Emberdactyl's crater inside its crest, the lava lake, the nest's pillar and the lookout ({@link CraterWard}; the rest
 * of its volcano stays climbable). The Huntsman has none: he simply doesn't come home or fight for them.
 *
 * <p>Every {@value #CHECK_EVERY_TICKS} ticks each player in the Dross (not in creative or spectator mode) is looked at:
 * <ul>
 *   <li>outside every ward that's locked for them, standing on something: that's their <b>last spot outside</b>;</li>
 *   <li>just inside one (at most {@value #PUSH_DEPTH} blocks past its edge): a firm push back out the way they came;</li>
 *   <li>deeper in (an ender pearl, a teleport): back to their last spot outside if it's near, else to the ward's own way
 *       out.</li>
 * </ul>
 * Only the player bounced sees the flash and hears the chime.</p>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BossWards
{
    // ------------------------------------------------------------------ Tuning

    /** The players in the Dross are looked at every this many server ticks (20 a second). */
    public static final int CHECK_EVERY_TICKS = 4;
    /** A locked player at most this many blocks past a ward's edge is pushed back out; deeper in, they're sent back. */
    public static final double PUSH_DEPTH = 4.0D;
    /** The push: this fast away from the arena (blocks a tick; about 5 blocks back out with the lift below)... */
    public static final double PUSH_SPEED = 0.9D;
    /** ...and this fast up (a little hop over the edge). */
    public static final double PUSH_LIFT = 0.35D;
    /**
     * A last spot outside further than this from where they are now isn't used (they came from far off: a teleport, a
     * trip through a portal): the ward's own way out instead.
     */
    public static final double LAST_SPOT_RANGE = 64.0D;
    /** The flash: this many runes... */
    public static final int FLASH_RUNES = 14;
    /** ...and this many streaks of light rising from the ground... */
    public static final int FLASH_STREAKS = 5;
    /** ...spread this far along the ward's edge (blocks, either way)... */
    public static final double FLASH_WIDTH = 1.2D;
    /** ...this far up and down round the middle of the runes... */
    public static final double FLASH_HEIGHT = 0.7D;
    /** ...this far either side of the edge... */
    public static final double FLASH_DEPTH = 0.15D;
    /** ...centred this high over the player's feet (their chest), the streaks from their feet. */
    public static final double FLASH_UP = 1.1D;
    /** The flash shows from this far off (the player is never further). */
    public static final boolean FLASH_LONG_DISTANCE = true;
    /** The chime: soft, high. */
    public static final float CHIME_VOLUME = 0.5F;
    public static final float CHIME_PITCH = 1.6F;

    // ------------------------------------------------------------------ A ward

    /** One boss's warded arena, as its own code sees it. */
    public interface Ward
    {
        /** The boss it belongs to: the players it's locked for are kept out. */
        OracleBoss boss();

        /** How far inside the ward these feet are (blocks past its edge); 0 or less outside. */
        double depth(Vec3 feet);

        /** The way out from here, the way they came in: a horizontal unit vector (away from the arena's middle). */
        Vec3 outward(Vec3 feet);

        /** A safe spot outside to send someone deep inside who has no last spot near (feet position). */
        Vec3 exit();

        /** The way to face at {@link #exit} (Minecraft's yaw). */
        float exitYaw();
    }

    /** Finds the wards near a player: each boss's own (cheap: it's asked every few ticks). */
    @FunctionalInterface
    public interface Finder
    {
        void find(ServerPlayer player, ServerLevel level, List<Ward> out);
    }

    /** Every boss's ward finder. */
    private static final List<Finder> FINDERS = List.of(LairWard::find, RatCaveWard::find, CraterWard::find);

    /** What one look did to a player (for the tests). */
    public enum Bounce
    {
        NONE, PUSHED, SENT_BACK
    }

    /** Where a player last stood outside the wards locked for them. */
    private record Spot(ResourceKey<Level> level, Vec3 feet, float yaw, float pitch)
    {
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Each player's last spot outside (server thread; forgotten when they log out). */
    private static final Map<UUID, Spot> LAST_SPOT = new HashMap<>();

    private BossWards() {}

    // ------------------------------------------------------------------ The look

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_EVERY_TICKS != 0)
        {
            return;
        }
        ServerLevel dross = event.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty())
        {
            return;
        }
        List<Ward> wards = new ArrayList<>();
        for (ServerPlayer player : List.copyOf(dross.players()))
        {
            if (OracleRules.exempt(player) || !player.isAlive())
            {
                continue;
            }
            wards.clear();
            try
            {
                for (Finder finder : FINDERS)
                {
                    finder.find(player, dross, wards);
                }
                check(player, wards);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Boss wards: looking at {} failed.", player.getGameProfile().getName(), e);
            }
        }
    }

    /**
     * One look at a player and the wards near them: remembers their spot outside, or bounces them out of the deepest ward
     * they're in that's locked for them. Creative and spectator players are left alone.
     */
    public static Bounce check(ServerPlayer player, List<Ward> wards)
    {
        if (OracleRules.exempt(player) || !player.isAlive())
        {
            return Bounce.NONE;
        }
        Vec3 feet = player.position();
        Ward in = null;
        double depth = 0.0D;
        for (Ward ward : wards)
        {
            if (OracleRules.mayFightBoss(player, ward.boss()))
            {
                continue;
            }
            double d = ward.depth(feet);
            if (d > depth)
            {
                depth = d;
                in = ward;
            }
        }
        if (in == null)
        {
            remember(player);
            return Bounce.NONE;
        }
        if (player.isPassenger())
        {
            player.stopRiding();
        }
        if (depth <= PUSH_DEPTH)
        {
            push(player, in, feet, depth);
            return Bounce.PUSHED;
        }
        sendBack(player, in, feet);
        return Bounce.SENT_BACK;
    }

    /** Their spot now, if they stand on something outside (not in lava). */
    private static void remember(ServerPlayer player)
    {
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1D, player.getZ());
        boolean standing = player.onGround() || !player.level().getBlockState(below).getCollisionShape(player.level(), below).isEmpty();
        if (standing && !player.isInLava())
        {
            LAST_SPOT.put(player.getUUID(), new Spot(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        }
    }

    /** A firm push back out the way they came, and the flash where they hit the edge. */
    private static void push(ServerPlayer player, Ward ward, Vec3 feet, double depth)
    {
        Vec3 out = ward.outward(feet);
        player.setDeltaMovement(out.x * PUSH_SPEED, PUSH_LIFT, out.z * PUSH_SPEED);
        if (player.connection != null) // (a player's own game moves them: it's told at once)
        {
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        player.resetFallDistance();
        flash(player, feet.add(out.scale(depth)), out);
    }

    /** Back to their last spot outside (if it's near and still outside), else the ward's own way out. */
    private static void sendBack(ServerPlayer player, Ward ward, Vec3 feet)
    {
        Spot last = LAST_SPOT.get(player.getUUID());
        Vec3 to;
        float yaw;
        float pitch;
        if (last != null && last.level().equals(player.level().dimension()) && last.feet().distanceTo(feet) <= LAST_SPOT_RANGE
                && ward.depth(last.feet()) <= 0.0D)
        {
            to = last.feet();
            yaw = last.yaw();
            pitch = last.pitch();
        }
        else
        {
            to = ward.exit();
            yaw = ward.exitYaw();
            pitch = 0.0F;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.teleportTo(player.serverLevel(), to.x, to.y, to.z, yaw, pitch);
        Vec3 out = ward.outward(to);
        flash(player, to.subtract(out.scale(1.0D)), out);
    }

    /** The land ward's runes and streaks flaring along the edge at {@code at}, and a soft chime: for this player only. */
    static void flash(ServerPlayer player, Vec3 at, Vec3 outward)
    {
        ServerLevel level = player.serverLevel();
        double sx = Math.abs(outward.z) * FLASH_WIDTH + FLASH_DEPTH;
        double sz = Math.abs(outward.x) * FLASH_WIDTH + FLASH_DEPTH;
        level.sendParticles(player, ModParticles.WARD_RUNE.get(), FLASH_LONG_DISTANCE, at.x, at.y + FLASH_UP, at.z, FLASH_RUNES,
                sx, FLASH_HEIGHT, sz, 0.0D);
        level.sendParticles(player, ModParticles.WARD_STREAK.get(), FLASH_LONG_DISTANCE, at.x, at.y, at.z, FLASH_STREAKS,
                sx, 0.1D, sz, 0.0D);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, CHIME_VOLUME, CHIME_PITCH);
    }

    // ------------------------------------------------------------------ Shared shapes

    /** The horizontal unit vector from {@code middle} out to {@code feet} (any way if they're on the middle). */
    public static Vec3 awayFrom(double middleX, double middleZ, Vec3 feet)
    {
        Vec3 out = new Vec3(feet.x - middleX, 0.0D, feet.z - middleZ);
        return out.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : out.normalize();
    }

    /** For the tests: forgets a player's last spot outside. */
    public static void forget(UUID player)
    {
        LAST_SPOT.remove(player);
    }

    @SubscribeEvent
    public static void onLogOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        LAST_SPOT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        LAST_SPOT.clear();
    }
}
