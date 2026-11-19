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
 * (QUEST_PLAN.md, "The Oracle's spell"; the server says when, {@code quest.oracle.LandFallMessage}). Along the stretch of
 * its edge round them the curtain flares up bright ({@link WardStreakParticle}), cracks with a glassy sound, and breaks
 * into shards of its runes that tumble down and fade ({@link WardShardParticle}), a cold blue dust settling where they
 * land. After that the land's curtain is gone for them ({@link OracleClientState#markFallen}).
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
    /** The flare: streaks a tick for this long. */
    public static final int FLARE_TICKS = 12;
    public static final int FLARE_STREAKS = 26;
    /** The crack's sound, the shatter's (and its rumble). */
    public static final int CRACK_AT = 9;
    public static final int SHATTER_AT = 13;
    /** The shards: from here, a tick, for this long. */
    public static final int SHARDS_FROM = 12;
    public static final int SHARDS_PER_TICK = 14;
    public static final int SHARD_TICKS = 26;
    /** Shards break off this high up the curtain (blocks over the ground)... */
    public static final double SHARD_MIN_HEIGHT = 1.0D;
    public static final double SHARD_MAX_HEIGHT = 18.0D;
    /** ...thrown out sideways this fast at most, and up at most this fast (blocks a tick). */
    public static final double SHARD_SPREAD = 0.08D;
    public static final double SHARD_LIFT = 0.12D;
    /** Dust where they land: from here, a tick, for this long. */
    public static final int DUST_FROM = 18;
    public static final int DUST_PER_TICK = 8;
    public static final int DUST_TICKS = 20;
    /** The whole thing (ticks). */
    public static final int DURATION = 46;
    /**
     * Right after a teleport the ground round the player may not be loaded on their screen yet: the edge is looked for
     * again every tick for this long before the fall plays round the spot itself.
     */
    public static final int WAIT_FOR_GROUND_TICKS = 80;
    /** The showcase's stretch of curtain runs this far either side of its middle (blocks). */
    public static final int SHOWCASE_HALF_LENGTH = 14;
    /** With particles on Decreased, this share of them. */
    public static final double DECREASED_SHARE = 0.5D;

    private static final List<Fall> FALLS = new ArrayList<>();
    /** Falls waiting for their edge's ground to load on this screen. */
    private static final List<Waiting> WAITING = new ArrayList<>();

    /** A fall whose edge wasn't found yet: its land, the column the server named, how long it has waited. */
    private static final class Waiting
    {
        final ResourceKey<Biome> land;
        final int x;
        final int z;
        int waited;

        Waiting(ResourceKey<Biome> land, int x, int z)
        {
            this.land = land;
            this.x = x;
            this.z = z;
        }
    }

    /** One barrier falling: the points of its edge and how long it has been falling. */
    private static final class Fall
    {
        final List<double[]> points;
        int age;

        Fall(List<double[]> points)
        {
            this.points = points;
        }
    }

    private LandWardFall() {}

    /** From the server: the barrier of the land with this bit falls round this column. */
    public static void start(int bit, int x, int z, float alongX, float alongZ)
    {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (bit < 0 && level != null)
        {
            // The showcase: a straight stretch of curtain across the player's view.
            List<double[]> line = new ArrayList<>();
            for (int i = -SHOWCASE_HALF_LENGTH; i <= SHOWCASE_HALF_LENGTH; i++)
            {
                line.add(new double[] {x + 0.5D + alongX * i, z + 0.5D + alongZ * i});
            }
            FALLS.add(new Fall(line));
            return;
        }
        OracleClientState.markFallen(bit);
        List<ResourceKey<Biome>> lands = OracleQuests.lockableLands();
        if (level == null || mc.player == null || bit < 0 || bit >= lands.size()
                || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        WAITING.add(new Waiting(lands.get(bit), x, z));
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
                    && level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, w.x, w.z) > level.getMinBuildHeight() + 1;
            if (!points.isEmpty() && ground)
            {
                WAITING.remove(i);
                FALLS.add(new Fall(points));
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
                FALLS.add(new Fall(ring));
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
            if (t < FLARE_TICKS)
            {
                for (int i = 0; i < count(FLARE_STREAKS * share, random); i++)
                {
                    double[] p = pick(fall, random);
                    mc.particleEngine.createParticle(ModParticles.WARD_STREAK.get(), p[0], ground(level, p), p[1], 0.0D, 0.0D, 0.0D);
                }
                if (t % 3 == 0)
                {
                    double[] p = pick(fall, random);
                    mc.particleEngine.createParticle(ModParticles.WARD_RUNE.get(), p[0], ground(level, p) + 1.0D + random.nextDouble() * 10.0D,
                            p[1], 0.0D, 0.0D, 0.0D);
                }
            }
            if (t == CRACK_AT)
            {
                play(mc, nearest(fall, player), level, ModSounds.LAND_WARD_FALL_CRACK.get(), 1.0F);
            }
            if (t == SHATTER_AT)
            {
                double[] p = nearest(fall, player);
                play(mc, p, level, ModSounds.LAND_WARD_FALL_SHATTER.get(), 1.0F);
                play(mc, p, level, ModSounds.LAND_WARD_FALL_RUMBLE.get(), 0.9F);
            }
            if (t >= SHARDS_FROM && t < SHARDS_FROM + SHARD_TICKS)
            {
                for (int i = 0; i < count(SHARDS_PER_TICK * share, random); i++)
                {
                    double[] p = pick(fall, random);
                    double y = ground(level, p) + SHARD_MIN_HEIGHT + random.nextDouble() * (SHARD_MAX_HEIGHT - SHARD_MIN_HEIGHT);
                    mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), p[0], y, p[1],
                            (random.nextDouble() * 2.0D - 1.0D) * SHARD_SPREAD, random.nextDouble() * SHARD_LIFT,
                            (random.nextDouble() * 2.0D - 1.0D) * SHARD_SPREAD);
                }
            }
            if (t >= DUST_FROM && t < DUST_FROM + DUST_TICKS)
            {
                DustParticleOptions dust = new DustParticleOptions(rgb(DrossColors.LAND_WARD_SHARD), 1.4F);
                for (int i = 0; i < count(DUST_PER_TICK * share, random); i++)
                {
                    double[] p = pick(fall, random);
                    mc.particleEngine.createParticle(dust, p[0] + random.nextGaussian() * 0.4D, ground(level, p) + 0.2D,
                            p[1] + random.nextGaussian() * 0.4D, 0.0D, 0.02D, 0.0D);
                }
            }
        }
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
        return new double[] {p[0] + (random.nextDouble() - 0.5D) * 1.5D, p[1] + (random.nextDouble() - 0.5D) * 1.5D};
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

    private static double ground(ClientLevel level, double[] p)
    {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(p[0]), (int) Math.floor(p[1]));
    }

    private static void play(Minecraft mc, double[] p, ClientLevel level, SoundEvent sound, float volume)
    {
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, 1.0F, level.random,
                p[0], ground(level, p) + 2.0D, p[1]));
    }

    static Vector3f rgb(int color)
    {
        return new Vector3f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
    }
}
