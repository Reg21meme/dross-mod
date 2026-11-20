package com.reg21meme.dross.dimension.ward.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.ward.LandWard;
import com.reg21meme.dross.quest.client.OracleClientState;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import com.reg21meme.dross.registry.ModParticles;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * CLIENT ONLY. A land's barrier falling in front of the player, the first time they come to it after the Oracle broke it
 * (QUEST_PLAN.md, "The Oracle's spell"; the server says when, {@code quest.oracle.LandFallMessage}). About 5.5 s along
 * the stretch of its edge round them (the user, November 19, 2026, of the first version, 2 s of a few streaks: "I don't
 * see the animation"): the curtain stands up thick and bright with its runes ({@link WardStreakParticle},
 * {@link WardRuneParticle}), a rumble; glowing cracks run jagged across it ({@link WardCrackParticle}) with two glassy
 * cracks; then it shatters with a flash, a crash and a rumble, hundreds of shards of its runes bursting out and tumbling
 * down ({@link WardShardParticle}), and a cold blue dust settles where they land. From the shatter the land's curtain is
 * gone for them ({@link OracleClientState#markFallen}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class LandWardFall
{
    // ------------------------------------------------------------------ Tuning

    /** The stretch of edge that falls: this far round the spot the server named (blocks). */
    public static final int RADIUS = 30;
    /** ...read on a grid of columns this many blocks apart. */
    public static final int CELL = 2;
    /** At most this many points of the edge are kept. */
    public static final int MAX_POINTS = 160;
    /** First the curtain stands up thick and bright: streaks a tick, runes a tick, until the shatter. */
    public static final int GATHER_STREAKS = 70;
    public static final int GATHER_RUNES = 6;
    /** The rumble as it starts. */
    public static final int RUMBLE_AT = 2;
    /** Cracks run across it: from here (half of them, the rest 8 ticks later), each a step a tick for this many steps. */
    public static final int CRACKS_FROM = 16;
    public static final int CRACKS = 12;
    public static final int CRACK_STEPS = 26;
    public static final double CRACK_STEP = 0.8D;
    /** ...starting this high up the curtain (blocks over the ground), at least and at most. */
    public static final double CRACK_MIN_HEIGHT = 2.0D;
    public static final double CRACK_MAX_HEIGHT = 16.0D;
    /** The cracks' sounds. */
    public static final int CRACK_AT = 16;
    public static final int CRACK_AGAIN_AT = 30;
    /** The shatter: a flash, the crash and the rumble, and this many shards at once, thrown out this fast at most. */
    public static final int SHATTER_AT = 44;
    public static final int BURST_SHARDS = 320;
    public static final double BURST_SPEED = 0.32D;
    /** ...then more shards a tick, for this long. */
    public static final int SHARDS_PER_TICK = 12;
    public static final int SHARD_TICKS = 30;
    /** Shards break off this high up the curtain (blocks over the ground)... */
    public static final double SHARD_MIN_HEIGHT = 0.5D;
    public static final double SHARD_MAX_HEIGHT = 22.0D;
    /** ...thrown out sideways this fast at most, and up at most this fast (blocks a tick). */
    public static final double SHARD_SPREAD = 0.1D;
    public static final double SHARD_LIFT = 0.15D;
    /** Dust where they land: from here, a tick, for this long. */
    public static final int DUST_FROM = 52;
    public static final int DUST_PER_TICK = 10;
    public static final int DUST_TICKS = 40;
    /** The whole thing (ticks). */
    public static final int DURATION = 110;
    /**
     * Right after a teleport the ground round the player may not be loaded on their screen yet: the edge is looked for
     * again every tick for this long before the fall plays round the spot itself.
     */
    public static final int WAIT_FOR_GROUND_TICKS = 80;
    /** The showcase's stretch of curtain runs this far either side of its middle (blocks). */
    public static final int SHOWCASE_HALF_LENGTH = 16;
    /** With particles on Decreased, this share of them. */
    public static final double DECREASED_SHARE = 0.5D;

    private static final List<Fall> FALLS = new ArrayList<>();
    /** Falls waiting for their edge's ground to load on this screen. */
    private static final List<Waiting> WAITING = new ArrayList<>();

    /** A fall whose edge wasn't found yet: its land (and its bit), the column the server named, how long it has waited. */
    private static final class Waiting
    {
        final ResourceKey<Biome> land;
        final int bit;
        final int x;
        final int z;
        int waited;

        Waiting(ResourceKey<Biome> land, int bit, int x, int z)
        {
            this.land = land;
            this.bit = bit;
            this.x = x;
            this.z = z;
        }
    }

    /** A crack running across the curtain: where it has got to, the way it's going, the steps it has left. */
    private static final class Crack
    {
        final double[] at;
        final double[] way;
        int left;

        Crack(double[] at, double[] way, int left)
        {
            this.at = at;
            this.way = way;
            this.left = left;
        }
    }

    /** One barrier falling: the points of its edge, its land's bit (-1: the showcase), its cracks, its age. */
    private static final class Fall
    {
        final List<double[]> points;
        final int bit;
        final List<Crack> cracks = new ArrayList<>();
        int age;

        Fall(List<double[]> points, int bit)
        {
            this.points = points;
            this.bit = bit;
        }
    }

    private LandWardFall() {}

    /**
     * A ring of curtain this far round x, z, standing at y, that stands up at once and falls like a land's (the Blind
     * Oracle's spells: the barrier they break, seen breaking; the user, November 19, 2026: "I still don't see the spell
     * destroying it"). It shatters {@link #SHATTER_AT} ticks after this. Changes nobody's barriers.
     */
    public static void ring(double x, double y, double z, double radius)
    {
        int n = Math.max(24, (int) Math.round(radius * Math.PI * 2.0D / RING_SPACING));
        List<double[]> points = new ArrayList<>();
        for (int k = 0; k < n; k++)
        {
            double a = Math.PI * 2.0D * k / n;
            points.add(new double[] {x + Math.cos(a) * radius, z + Math.sin(a) * radius, y});
        }
        FALLS.add(new Fall(points, -1));
    }

    /** A spell's ring has a point of curtain every this many blocks round it. */
    public static final double RING_SPACING = 1.2D;

    /** From the server: the barrier of the land with this bit falls round this column. */
    public static void start(int bit, int x, int z, float alongX, float alongZ)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if ((bit < 0 || alongX != 0.0F || alongZ != 0.0F) && level != null)
        {
            // The showcase, or a land's barrier for a player who arrived deep inside it: a straight stretch of curtain across the player's view, standing at their feet's height (the
            // ground's top there may be a tree's crown, and under a forest's leaves you never saw it).
            double feet = mc.player != null ? mc.player.getY() : level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            List<double[]> line = new ArrayList<>();
            for (int i = -SHOWCASE_HALF_LENGTH; i <= SHOWCASE_HALF_LENGTH; i++)
            {
                line.add(new double[] {x + 0.5D + alongX * i, z + 0.5D + alongZ * i, feet});
            }
            FALLS.add(new Fall(line, bit));
            return;
        }
        List<ResourceKey<Biome>> lands = OracleQuests.lockableLands();
        if (level == null || mc.player == null || bit < 0 || bit >= lands.size()
                || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            OracleClientState.markFallen(bit);
            return;
        }
        WAITING.add(new Waiting(lands.get(bit), bit, x, z));
        tryStart(level);
    }

    /** Starts every waiting fall whose edge is now loaded (or that has waited long enough: round its spot then). */
    private static void tryStart(ClientLevel level)
    {
        for (int i = WAITING.size() - 1; i >= 0; i--)
        {
            Waiting w = WAITING.get(i);
            List<double[]> points = edge(level, w.land, w.x, w.z);
            boolean ground = level.getChunkSource().hasChunk(w.x >> 4, w.z >> 4)
                    && surface(level, w.x, w.z) > level.getMinBuildHeight() + 1;
            if (!points.isEmpty() && ground)
            {
                WAITING.remove(i);
                FALLS.add(new Fall(points, w.bit));
            }
            else if (++w.waited >= WAIT_FOR_GROUND_TICKS)
            {
                WAITING.remove(i);
                List<double[]> ring = new ArrayList<>();
                for (int k = 0; k < 24; k++)
                {
                    double a = Math.PI * 2.0D * k / 24;
                    ring.add(new double[] {w.x + Math.cos(a) * 6.0D, w.z + Math.sin(a) * 6.0D});
                }
                FALLS.add(new Fall(ring, w.bit));
            }
        }
    }

    /** The points along the land's edge round x, z: on each pair of neighbouring cells where it starts or ends. */
    private static List<double[]> edge(ClientLevel level, ResourceKey<Biome> land, int x, int z)
    {
        int half = RADIUS / CELL;
        int size = half * 2 + 1;
        byte[] grid = new byte[size * size]; // 0 unknown, 1 the land, 2 not
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                int cx = x + (i - half) * CELL;
                int cz = z + (j - half) * CELL;
                if (level.getChunkSource().hasChunk(cx >> 4, cz >> 4))
                {
                    grid[i * size + j] = (byte) (land.equals(LandWard.landAt(level, cx, cz)) ? 1 : 2);
                }
            }
        }
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                byte here = grid[i * size + j];
                if (here == 0)
                {
                    continue;
                }
                double cx = x + (i - half) * CELL;
                double cz = z + (j - half) * CELL;
                if (i + 1 < size && grid[(i + 1) * size + j] != 0 && grid[(i + 1) * size + j] != here)
                {
                    points.add(new double[] {cx + CELL / 2.0D, cz});
                }
                if (j + 1 < size && grid[i * size + j + 1] != 0 && grid[i * size + j + 1] != here)
                {
                    points.add(new double[] {cx, cz + CELL / 2.0D});
                }
            }
        }
        RandomSource random = level.random;
        while (points.size() > MAX_POINTS)
        {
            points.remove(random.nextInt(points.size()));
        }
        return points;
    }

    // ------------------------------------------------------------------ Each tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || FALLS.isEmpty() && WAITING.isEmpty())
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null)
        {
            FALLS.clear();
            WAITING.clear();
            return;
        }
        if (!WAITING.isEmpty() && !mc.isPaused())
        {
            tryStart(level);
        }
        if (mc.isPaused())
        {
            return;
        }
        ParticleStatus status = mc.options.particles().get();
        double share = status == ParticleStatus.MINIMAL ? 0.15D : status == ParticleStatus.DECREASED ? DECREASED_SHARE : 1.0D;
        RandomSource random = level.random;
        for (int f = FALLS.size() - 1; f >= 0; f--)
        {
            Fall fall = FALLS.get(f);
            int t = fall.age++;
            if (t >= DURATION)
            {
                FALLS.remove(f);
                continue;
            }
            if (t < SHATTER_AT)
            {
                stand(mc, level, fall, t, share, random);
            }
            if (t == RUMBLE_AT)
            {
                play(mc, nearest(fall, player), level, ModSounds.LAND_WARD_FALL_RUMBLE.get(), 0.6F, 0.8F);
            }
            if (t >= CRACKS_FROM && t < SHATTER_AT)
            {
                crack(mc, level, fall, t, random);
            }
            if (t == CRACK_AT || t == CRACK_AGAIN_AT)
            {
                play(mc, nearest(fall, player), level, ModSounds.LAND_WARD_FALL_CRACK.get(), 1.0F, t == CRACK_AT ? 1.0F : 0.85F);
            }
            if (t == SHATTER_AT)
            {
                shatter(mc, level, fall, player, share, random);
            }
            if (t > SHATTER_AT && t < SHATTER_AT + SHARD_TICKS)
            {
                for (int i = 0; i < count(SHARDS_PER_TICK * share, random); i++)
                {
                    shard(mc, level, pick(fall, random), random, 1.0D);
                }
            }
            if (t >= DUST_FROM && t < DUST_FROM + DUST_TICKS)
            {
                DustParticleOptions dust = new DustParticleOptions(rgb(DrossColors.LAND_WARD_SHARD), 1.6F);
                for (int i = 0; i < count(DUST_PER_TICK * share, random); i++)
                {
                    double[] p = pick(fall, random);
                    mc.particleEngine.createParticle(dust, p[0] + random.nextGaussian() * 0.6D, ground(level, p) + 0.2D,
                            p[1] + random.nextGaussian() * 0.6D, 0.0D, 0.02D, 0.0D);
                }
            }
        }
    }

    /** The curtain standing, thick and bright, its runes drifting up it (before it shatters). */
    private static void stand(Minecraft mc, ClientLevel level, Fall fall, int t, double share, RandomSource random)
    {
        double grow = Math.min(1.0D, (t + 1) / 10.0D);
        for (int i = 0; i < count(GATHER_STREAKS * share * grow, random); i++)
        {
            double[] p = pick(fall, random);
            mc.particleEngine.createParticle(ModParticles.WARD_STREAK.get(), p[0], ground(level, p) + random.nextDouble() * 6.0D,
                    p[1], 0.0D, 0.0D, 0.0D);
        }
        for (int i = 0; i < count(GATHER_RUNES * share, random); i++)
        {
            double[] p = pick(fall, random);
            mc.particleEngine.createParticle(ModParticles.WARD_RUNE.get(), p[0], ground(level, p) + 1.0D + random.nextDouble() * 14.0D,
                    p[1], 0.0D, 0.0D, 0.0D);
        }
    }

    /** The cracks: a few start at once, each runs on a step a tick, leaving a glowing jagged line until the shatter. */
    private static void crack(Minecraft mc, ClientLevel level, Fall fall, int t, RandomSource random)
    {
        if (t == CRACKS_FROM || t == CRACKS_FROM + 8)
        {
            for (int i = 0; i < CRACKS / 2; i++)
            {
                double[] p = pick(fall, random);
                double y = ground(level, p) + CRACK_MIN_HEIGHT + random.nextDouble() * (CRACK_MAX_HEIGHT - CRACK_MIN_HEIGHT);
                double a = random.nextDouble() * Math.PI * 2.0D;
                double up = (random.nextDouble() * 2.0D - 1.0D) * 0.9D;
                fall.cracks.add(new Crack(new double[] {p[0], y, p[1]}, new double[] {Math.cos(a) * 0.5D, up, Math.sin(a) * 0.5D},
                        CRACK_STEPS));
            }
        }
        int life = SHATTER_AT - t + 1;
        for (int c = fall.cracks.size() - 1; c >= 0; c--)
        {
            Crack crack = fall.cracks.get(c);
            if (crack.left-- <= 0)
            {
                continue;
            }
            // Mostly on its way, but jagged; now and then a branch.
            double[] w = crack.way;
            w[0] += random.nextGaussian() * 0.35D;
            w[1] += random.nextGaussian() * 0.45D;
            w[2] += random.nextGaussian() * 0.35D;
            double len = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
            if (len < 1.0E-3D)
            {
                continue;
            }
            double[] from = crack.at.clone();
            for (int k = 0; k < 3; k++)
            {
                crack.at[k] += w[k] / len * CRACK_STEP;
            }
            for (int k = 1; k <= 3; k++)
            {
                double q = k / 3.0D;
                mc.particleEngine.createParticle(ModParticles.WARD_CRACK.get(), from[0] + (crack.at[0] - from[0]) * q,
                        from[1] + (crack.at[1] - from[1]) * q, from[2] + (crack.at[2] - from[2]) * q, life, 0.0D, 0.0D);
            }
            if (random.nextFloat() < 0.08F && fall.cracks.size() < CRACKS * 3)
            {
                fall.cracks.add(new Crack(crack.at.clone(), new double[] {-w[2], w[1] * 0.5D, w[0]}, crack.left / 2));
            }
        }
    }

    /** The shatter: a flash, the crash, and the curtain bursting into shards. */
    private static void shatter(Minecraft mc, ClientLevel level, Fall fall, LocalPlayer player, double share, RandomSource random)
    {
        OracleClientState.markFallen(fall.bit);
        double[] near = nearest(fall, player);
        play(mc, near, level, ModSounds.LAND_WARD_FALL_SHATTER.get(), 1.0F, 1.0F);
        play(mc, near, level, ModSounds.LAND_WARD_FALL_RUMBLE.get(), 1.0F, 1.0F);
        for (int i = 0; i < 5; i++)
        {
            double[] p = pick(fall, random);
            mc.particleEngine.createParticle(ParticleTypes.FLASH, p[0], ground(level, p) + 3.0D + random.nextDouble() * 10.0D,
                    p[1], 0.0D, 0.0D, 0.0D);
        }
        for (int i = 0; i < count(BURST_SHARDS * share, random); i++)
        {
            shard(mc, level, pick(fall, random), random, BURST_SPEED / SHARD_SPREAD);
        }
        fall.cracks.clear();
    }

    /** A shard breaking off somewhere up the curtain over {@code p}, thrown out {@code speed} times as fast as usual. */
    private static void shard(Minecraft mc, ClientLevel level, double[] p, RandomSource random, double speed)
    {
        double y = ground(level, p) + SHARD_MIN_HEIGHT + random.nextDouble() * (SHARD_MAX_HEIGHT - SHARD_MIN_HEIGHT);
        mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), p[0], y, p[1],
                (random.nextDouble() * 2.0D - 1.0D) * SHARD_SPREAD * speed, random.nextDouble() * SHARD_LIFT * Math.sqrt(speed),
                (random.nextDouble() * 2.0D - 1.0D) * SHARD_SPREAD * speed);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        FALLS.clear();
        WAITING.clear();
    }

    private static int count(double expected, RandomSource random)
    {
        int whole = (int) expected;
        return random.nextDouble() < expected - whole ? whole + 1 : whole;
    }

    private static double[] pick(Fall fall, RandomSource random)
    {
        double[] p = fall.points.get(random.nextInt(fall.points.size()));
        double dx = (random.nextDouble() - 0.5D) * 1.5D;
        double dz = (random.nextDouble() - 0.5D) * 1.5D;
        return p.length > 2 ? new double[] {p[0] + dx, p[1] + dz, p[2]} : new double[] {p[0] + dx, p[1] + dz};
    }

    private static double[] nearest(Fall fall, LocalPlayer player)
    {
        double[] best = fall.points.get(0);
        double bestD = Double.MAX_VALUE;
        for (double[] p : fall.points)
        {
            double d = (p[0] - player.getX()) * (p[0] - player.getX()) + (p[1] - player.getZ()) * (p[1] - player.getZ());
            if (d < bestD)
            {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** The ground under a point of the edge: its own height if it has one (the showcase's), else the land's top. */
    private static double ground(ClientLevel level, double[] p)
    {
        if (p.length > 2)
        {
            return p[2];
        }
        return surface(level, (int) Math.floor(p[0]), (int) Math.floor(p[1]));
    }

    /**
     * The ground's top at x, z on this screen: under the trees' leaves and logs, on top of water. (A player's game only
     * has the server's MOTION_BLOCKING and WORLD_SURFACE heights; MOTION_BLOCKING_NO_LEAVES is always the world's
     * bottom there, so until November 19, 2026 every land's barrier fell out of sight under the ground: the user, "this
     * doesn't happen ... I am waiting at the border".)
     */
    static int surface(ClientLevel level, int x, int z)
    {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int lowest = Math.max(level.getMinBuildHeight(), y - 32);
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        while (y > lowest)
        {
            pos.set(x, y - 1, z);
            net.minecraft.world.level.block.state.BlockState below = level.getBlockState(pos);
            boolean through = below.is(net.minecraft.tags.BlockTags.LEAVES) || below.is(net.minecraft.tags.BlockTags.LOGS)
                    || below.getFluidState().isEmpty() && below.getCollisionShape(level, pos).isEmpty();
            if (!through)
            {
                break;
            }
            y--;
        }
        return y;
    }

    private static void play(Minecraft mc, double[] p, ClientLevel level, SoundEvent sound, float volume, float pitch)
    {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, level.random,
                p[0], ground(level, p) + 2.0D, p[1]));
    }

    static Vector3f rgb(int color)
    {
        return new Vector3f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
    }
}
