package com.reg21meme.dross.dimension.ward.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.ward.LandWard;
import com.reg21meme.dross.quest.client.OracleClientState;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * CLIENT ONLY. The edge of a land ward (QUEST_PLAN.md, "Locked lands"): where a land that's open for this player meets
 * one their Oracle quest hasn't opened yet (or one opened whose barrier they haven't yet seen fall: {@link LandWardFall}),
 * a shimmering curtain stands on the border, thin electric-blue streaks of
 * light rising off the ground and fading high up ({@link WardStreakParticle}) with the Dross's glowing runes drifting
 * up among them ({@link WardRuneParticle}). Only a player it's locked for sees it (each player's game knows only their
 * own open lands, {@link OracleClientState}); in creative or spectator mode, or with particles on Minimal, nothing.
 *
 * <p>How: every {@value #RESCAN_TICKS} ticks (and when the player crosses into another cell) the land of a grid of
 * columns round them, {@value #CELL} blocks apart out to {@value #RADIUS} blocks, is read once ({@link LandWard#landAt},
 * the server's own rule) and the pairs of neighbouring cells where one is locked and the other isn't are kept. Each tick
 * a few particles go up on those, each on the exact block where the land changes along a line across the pair, at most
 * {@value #MAX_STREAKS_PER_TICK} streaks and {@value #MAX_RUNES_PER_TICK} rune a tick.</p>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class LandWardEdge
{
    // ------------------------------------------------------------------ Tuning

    /** The edge is looked for this far round the player (blocks, along X and Z). */
    public static final int RADIUS = 32;
    /** ...on a grid of columns this many blocks apart (a biome cell's size: a border can't be finer). */
    public static final int CELL = 4;
    /** The grid is read again every this many ticks (a quest opening a land shows within a second). */
    public static final int RESCAN_TICKS = 20;
    /** Streaks a tick for every block of edge round the player (a curtain about 60 blocks long keeps about 300 up)... */
    public static final double STREAKS_PER_EDGE_BLOCK = 0.09D;
    /** ...but never more than this many a tick. */
    public static final int MAX_STREAKS_PER_TICK = 10;
    /** Runes a tick for every block of edge (about 30 up along 60 blocks)... */
    public static final double RUNES_PER_EDGE_BLOCK = 0.005D;
    /** ...but never more than this many a tick. */
    public static final int MAX_RUNES_PER_TICK = 1;
    /** With particles on Decreased, this share of them. */
    public static final double DECREASED_SHARE = 0.5D;
    /** A rune starts this far up the curtain (blocks over the ground), at least and at most. */
    public static final double RUNE_MIN_LIFT = 0.6D;
    public static final double RUNE_MAX_LIFT = 12.0D;
    /** A streak's foot: from the top of the land down through leaves and logs to the ground, at most this many blocks. */
    public static final int GROUND_SEARCH = 32;
    /**
     * A player more than this many blocks under the ground there (digging, or in the sewers) sees the streaks rise from
     * round their own feet instead, so the curtain runs through their tunnel too.
     */
    public static final int UNDERGROUND = 6;
    /** How far either way a particle may sit off the border (blocks), so the curtain has a little depth. */
    public static final double BORDER_JITTER = 0.3D;
    /** Lines across a pair tried before giving up on a particle this tick (the border can bend away from one). */
    private static final int TRIES = 3;

    /** The pairs of cells on the edge, as of the last read. */
    private static final List<Edge> EDGES = new ArrayList<>();
    private static ClientLevel scannedLevel;
    private static long lastScan;
    private static int scanCellX;
    private static int scanCellZ;

    /** Two neighbouring cells, one locked and one not: the first's middle column, and whether the second is along X. */
    private record Edge(int x, int z, boolean alongX) {}

    private LandWardEdge() {}

    // ------------------------------------------------------------------ Each tick

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || !level.dimension().equals(ModDimensions.DROSS_LEVEL)
                || player.isCreative() || player.isSpectator() || !OracleClientState.anyCurtain())
        {
            forget();
            return;
        }
        ParticleStatus status = mc.options.particles().get();
        if (mc.isPaused() || status == ParticleStatus.MINIMAL)
        {
            return;
        }
        long now = level.getGameTime();
        int cellX = Math.floorDiv(player.getBlockX(), CELL);
        int cellZ = Math.floorDiv(player.getBlockZ(), CELL);
        if (level != scannedLevel || now - lastScan >= RESCAN_TICKS || now < lastScan || cellX != scanCellX || cellZ != scanCellZ)
        {
            scan(level, cellX, cellZ);
            scannedLevel = level;
            lastScan = now;
            scanCellX = cellX;
            scanCellZ = cellZ;
        }
        if (EDGES.isEmpty())
        {
            return;
        }
        RandomSource random = level.random;
        double share = status == ParticleStatus.DECREASED ? DECREASED_SHARE : 1.0D;
        double edgeBlocks = (double) EDGES.size() * CELL;
        int streaks = count(edgeBlocks * STREAKS_PER_EDGE_BLOCK * share, MAX_STREAKS_PER_TICK, random);
        int runes = count(edgeBlocks * RUNES_PER_EDGE_BLOCK * share, MAX_RUNES_PER_TICK, random);
        for (int i = 0; i < streaks; i++)
        {
            spawn(mc, level, player, ModParticles.WARD_STREAK.get(), false, random);
        }
        for (int i = 0; i < runes; i++)
        {
            spawn(mc, level, player, ModParticles.WARD_RUNE.get(), true, random);
        }
    }

    private static void forget()
    {
        EDGES.clear();
        scannedLevel = null;
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

    // ------------------------------------------------------------------ Finding the edge

    /** Reads the grid round the cell the player is in and keeps the pairs of cells on the edge. */
    private static void scan(ClientLevel level, int cellX, int cellZ)
    {
        EDGES.clear();
        int half = RADIUS / CELL;
        int size = half * 2 + 1;
        byte[] grid = new byte[size * size];
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                grid[i * size + j] = cellState(level, middle(cellX + i - half), middle(cellZ + j - half));
            }
        }
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                byte here = grid[i * size + j];
                if (here == UNKNOWN)
                {
                    continue;
                }
                int x = middle(cellX + i - half);
                int z = middle(cellZ + j - half);
                if (i + 1 < size && differs(here, grid[(i + 1) * size + j]))
                {
                    EDGES.add(new Edge(x, z, true));
                }
                if (j + 1 < size && differs(here, grid[i * size + j + 1]))
                {
                    EDGES.add(new Edge(x, z, false));
                }
            }
        }
    }

    private static final byte UNKNOWN = 0;
    private static final byte OPEN = 1;
    private static final byte LOCKED = 2;

    private static int middle(int cell)
    {
        return cell * CELL + CELL / 2;
    }

    private static boolean differs(byte a, byte b)
    {
        return b != UNKNOWN && a != b;
    }

    /** A column's state: UNKNOWN when its chunk isn't loaded here, else whether its land is locked for this player. */
    private static byte cellState(ClientLevel level, int x, int z)
    {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4))
        {
            return UNKNOWN;
        }
        return lockedAt(level, x, z) ? LOCKED : OPEN;
    }

    /** True if the land of this column shows its barrier to this player (locked, or open but not yet seen falling). */
    private static boolean lockedAt(ClientLevel level, int x, int z)
    {
        ResourceKey<Biome> land = LandWard.landAt(level, x, z);
        return land != null && OracleClientState.showsCurtain(land);
    }

    // ------------------------------------------------------------------ The particles

    /**
     * Puts one particle on the edge: a random pair, a random line across it within its cell's width, the block on that
     * line where the land changes; a streak at the ground there, a rune somewhere up the curtain.
     */
    private static void spawn(Minecraft mc, ClientLevel level, LocalPlayer player, SimpleParticleType type, boolean rune,
                              RandomSource random)
    {
        for (int attempt = 0; attempt < TRIES; attempt++)
        {
            Edge edge = EDGES.get(random.nextInt(EDGES.size()));
            int start = edge.alongX() ? edge.x() : edge.z();
            int line = (edge.alongX() ? edge.z() : edge.x()) - CELL / 2 + random.nextInt(CELL);
            int border = Integer.MIN_VALUE;
            boolean before = lockedAt(level, edge.alongX() ? start : line, edge.alongX() ? line : start);
            for (int step = 1; step <= CELL; step++)
            {
                int c = start + step;
                boolean now = lockedAt(level, edge.alongX() ? c : line, edge.alongX() ? line : c);
                if (now != before)
                {
                    border = c;
                    break;
                }
            }
            if (border == Integer.MIN_VALUE)
            {
                continue;
            }
            double across = border + (random.nextDouble() - 0.5D) * 2.0D * BORDER_JITTER;
            double along = line + random.nextDouble();
            double x = edge.alongX() ? across : along;
            double z = edge.alongX() ? along : across;
            double y = footY(level, player, (int) Math.floor(x), (int) Math.floor(z), random);
            if (rune)
            {
                y += RUNE_MIN_LIFT + random.nextDouble() * (RUNE_MAX_LIFT - RUNE_MIN_LIFT);
            }
            mc.particleEngine.createParticle(type, x, y, z, 0.0D, 0.0D, 0.0D);
            return;
        }
    }

    /**
     * Where a streak starts at this column: the ground (under any leaves and logs, on top of water), or round the
     * player's feet if they're well under the ground there.
     */
    private static double footY(ClientLevel level, LocalPlayer player, int x, int z, RandomSource random)
    {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int lowest = Math.max(level.getMinBuildHeight(), y - GROUND_SEARCH);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (y > lowest)
        {
            pos.set(x, y - 1, z);
            BlockState below = level.getBlockState(pos);
            boolean through = below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS)
                    || below.getFluidState().isEmpty() && below.getCollisionShape(level, pos).isEmpty();
            if (!through)
            {
                break;
            }
            y--;
        }
        if (player.getY() < y - UNDERGROUND)
        {
            return player.getY() - 1.0D + random.nextDouble();
        }
        return y;
    }
}
