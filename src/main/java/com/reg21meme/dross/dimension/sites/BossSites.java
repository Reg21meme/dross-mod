package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallAreas;
import com.reg21meme.dross.dimension.sewer.SewerSites;
import com.reg21meme.dross.portal.DrossHub;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

/**
 * Where the bosses' lairs and the sewers go: one shared plan for every boss biome (the user's decision, October 16
 * 2026), worked out from the world generator's noise alone (no chunk is generated or loaded), the same every time for
 * the same seed, reaching out from the hub as far as anyone goes.
 * <h2>The rule</h2>
 * <ol>
 *   <li>A boss biome's <b>patches</b> are its separate regions on the surface ({@link Patch}: chunks of the biome that
 *       touch along a side; specks under {@link BossSitesTuning#MIN_PATCH_AREA} blocks² don't count). They're numbered
 *       by their <b>distance</b> from the hub ({@link DrossHub#CENTER}) to their nearest point, nearest first.</li>
 *   <li>The 3 nearest form <b>group 0</b>; the next 3 group 1; and so on forever (up to
 *       {@link BossSitesTuning#MAX_DISTANCE}).</li>
 *   <li>In each group, one patch, picked by the world seed among the patches where the biome's current boss's lair fits
 *       (its {@link SiteFinder}), gets that boss ({@link Group#bossSite}, slot 0). The other two are kept for the biome's
 *       two future bosses (slots 1 and 2): each biome will have 3 bosses, never two in one patch. If no patch of a group
 *       fits, the roomiest (deepest) one gets the boss's smallest form; if not even that fits, the group has no boss (a
 *       warning is logged).</li>
 *   <li>A biome may have a <b>filler</b> for the two kept patches: in Blightwood each gets a regular sewer with a 1 in 2
 *       chance ({@link BossSitesTuning#REGULAR_SEWER_CHANCE}, a roll per patch fixed by the seed), if one fits. A patch
 *       never holds more than one sewer network.</li>
 * </ol>
 * The boss biomes now: {@link DrossBiomes#CINDER_WASTES} (the Cinder Colossus, {@link #COLOSSUS_LAIR}),
 * {@link DrossBiomes#BLIGHTWOOD} (the Rat King's sewer, {@link #RAT_KING_SEWER}; filler {@link #REGULAR_SEWER}) and
 * {@link DrossBiomes#RIMEFALL} (no boss yet; every patch gets its lake and herds, {@link #RIMEFALL_AREA}: a <b>per-patch</b>
 * use, {@link SiteUse#perPatch}, placed in every patch of its biome whatever its slot).
 * <h2>Using it</h2>
 * <ul>
 *   <li>{@link #forLevel} the running world's plan (cached); {@link #forSeed} any seed's (a new one each call: tests,
 *       previews).</li>
 *   <li>{@link #patches}, {@link #patch}, {@link #patchAt}: a biome's patches; {@link #group}, {@link #groups}: its groups
 *       (each with its slots and sites); {@link #site}: a use's planned site in a patch; {@link #fitIn}: where a use would
 *       fit in a patch; {@link #nearest}: the nearest planned site of a use to a position; {@link #sitesNear}: the planned
 *       sites near a position; {@link #isSiteChunk}/{@link #siteAtChunk}: what world generation asks.</li>
 *   <li>A boss plugs in its own fit test with {@link #setFinder} (during mod setup, before any world loads: the plan
 *       depends on it, so changing it moves the lairs).</li>
 * </ul>
 * <h2>How it's worked out (and why world generation doesn't wait for it)</h2>
 * Numbering patches by distance needs every patch nearer than the one asked about, so the biome map is read outwards
 * from the hub ring by ring (rings of 16-block cells round the hub's cell), as far as the questions need; each group is
 * planned the first time it's asked about (its 3 patches' fit tests side by side, on the planner's helper threads: about
 * a third of a second for a group of 3 Blightwood patches).
 * Everything here is safe on any thread (world generation calls it from its own threads), and the answers never depend
 * on which thread asks first or when:
 * <ul>
 *   <li><b>Read in the background.</b> When the world starts, a background thread ({@link #startReader}) reads the map out
 *       to {@link BossSitesTuning#READ_AT_START} blocks, then keeps it read {@link BossSitesTuning#READ_AHEAD} blocks past
 *       the furthest player in the Dross, and plans the groups of the patches within
 *       {@link BossSitesTuning#PREPLAN_RADIUS} of each player. So world generation nearly always finds its answer ready.</li>
 *   <li><b>Answers without waiting.</b> What's been read is published after each step ({@link Published}: how far, and
 *       the numbered patches; each map cell knows its patch, {@link BiomeCells}), and a question about a place already read
 *       is answered from that, without the lock. A cell that isn't a boss biome is "no patch here" at once, however far
 *       out it is (one remembered biome sample).</li>
 *   <li><b>Reading further, quickly.</b> Only a question about a place further out than the map has been read waits: the
 *       thread reads the next rings itself, a band at a time, with the planner's sampling threads helping
 *       ({@link PlannerThreads}), publishing after each band, so other threads waiting for nearer places go as soon as
 *       theirs is read. A wait longer than {@link BossSitesTuning#SLOW_READ_LOG_MILLIS} is logged.</li>
 *   <li><b>Quicker samples.</b> Each biome sample is about twice as quick as Minecraft's own sampler, with exactly the
 *       same answers ({@link SiteTerrain}).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BossSites
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---------------------------------------------------------------------------------------------
    // The uses and the boss biomes
    // ---------------------------------------------------------------------------------------------

    /**
     * The Cinder Colossus's lair, in Cinder Wastes patches. Its fit test is a stand-in (every column within
     * {@link BossSitesTuning#COLOSSUS_FIT_RADIUS} blocks is Cinder Wastes) until the boss area plugs in its own
     * ({@link #setFinder}). The planner only says where; the boss area builds it.
     */
    public static final SiteUse COLOSSUS_LAIR = new SiteUse(id("colossus_lair"), DrossBiomes.CINDER_WASTES,
            discFinder(DrossBiomes.CINDER_WASTES, BossSitesTuning.COLOSSUS_FIT_RADIUS, BossSitesTuning.COLOSSUS_RELAXED_FIT_RADIUS));
    /** The Rat King's sewer (a network ending in his rat cave, with one grand entrance), in Blightwood patches. */
    public static final SiteUse RAT_KING_SEWER = new SiteUse(id("rat_king_sewer"), DrossBiomes.BLIGHTWOOD, SewerSites::findRatKingSewer);
    /** A regular sewer (3 pipes, rats only, no king), in the Blightwood patches kept for future bosses. */
    public static final SiteUse REGULAR_SEWER = new SiteUse(id("regular_sewer"), DrossBiomes.BLIGHTWOOD, SewerSites::findRegularSewer);
    /**
     * A Rimefall patch's own things, in every Rimefall patch whatever its slot (a per-patch use): its big frozen lake
     * and its Rimehorn herds' spots ({@link RimefallAreas.RimefallArea}; the user's requests of October 19, 2026).
     */
    public static final SiteUse RIMEFALL_AREA = new SiteUse(id("rimefall_area"), DrossBiomes.RIMEFALL, RimefallAreas::find, true);

    /** Every use, by id (the structure sets' placement JSON names one: {@code "use": "dross:rat_king_sewer"}). */
    public static final List<SiteUse> USES = List.of(COLOSSUS_LAIR, RAT_KING_SEWER, REGULAR_SEWER, RIMEFALL_AREA);

    /**
     * A boss biome: its current boss (slot 0 of every group; null while its bosses haven't come yet: then every group's
     * patches take slots 1, 2 and 3) and the filler for the two kept patches (or null).
     *
     * @param fillerChance the chance each kept patch gets the filler (if it fits)
     */
    public record BossBiome(ResourceKey<Biome> biome, @Nullable SiteUse boss, @Nullable SiteUse filler, double fillerChance)
    {
    }

    /**
     * The boss biomes. Rimefall has no boss yet (the Huntsman's home comes later), but its patches are planned for their
     * lakes and herds ({@link #RIMEFALL_AREA}).
     */
    public static final List<BossBiome> BOSS_BIOMES = List.of(
            new BossBiome(DrossBiomes.CINDER_WASTES, COLOSSUS_LAIR, null, 0.0D),
            new BossBiome(DrossBiomes.BLIGHTWOOD, RAT_KING_SEWER, REGULAR_SEWER, BossSitesTuning.REGULAR_SEWER_CHANCE),
            new BossBiome(DrossBiomes.RIMEFALL, null, null, 0.0D));

    /** The per-patch uses of a biome (every patch of it gets one, where it fits). */
    private static List<SiteUse> perPatchUses(ResourceKey<Biome> biome)
    {
        List<SiteUse> uses = new ArrayList<>();
        for (SiteUse use : USES)
        {
            if (use.perPatch() && use.biome().equals(biome))
            {
                uses.add(use);
            }
        }
        return uses;
    }

    /** The use with this id, if there is one. */
    public static Optional<SiteUse> use(ResourceLocation id)
    {
        for (SiteUse use : USES)
        {
            if (use.id().equals(id))
            {
                return Optional.of(use);
            }
        }
        return Optional.empty();
    }

    /**
     * Plugs in a use's own fit test (the boss area: the Colossus's lair, from its own volcano rules). Call it during mod
     * setup ({@code FMLCommonSetupEvent}), before any world loads: plans already made keep the old test's answers.
     */
    public static void setFinder(SiteUse use, SiteFinder finder)
    {
        use.setFinder(finder);
        synchronized (WORLDS)
        {
            if (!WORLDS.isEmpty())
            {
                LOGGER.warn("[boss sites] {}'s fit test was changed while a world's plan was already made; that world keeps the old answers.", use);
            }
        }
    }

    private static ResourceLocation id(String path)
    {
        return new ResourceLocation(Dross.MODID, path);
    }

    // ---------------------------------------------------------------------------------------------
    // One plan per world (by its RandomState: the same object world generation uses)
    // ---------------------------------------------------------------------------------------------

    private static final Map<RandomState, BossSites> WORLDS = new IdentityHashMap<>();
    /** The plan world generation asked for last (checked before taking WORLDS' lock: nearly always the one). */
    @Nullable
    private static volatile BossSites lastWorld;
    /** Where the Dross's players are (refreshed every {@link BossSitesTuning#READER_PLAYER_TICKS} on the server thread), for the background reader. */
    private static volatile List<BlockPos> drossPlayers = List.of();

    /** The running world's plan (the Dross level's). Any thread. */
    public static BossSites forLevel(ServerLevel dross)
    {
        RandomState state = dross.getChunkSource().randomState();
        BossSites last = lastWorld;
        if (last != null && last.terrain.randomState() == state)
        {
            return last;
        }
        BossSites sites;
        synchronized (WORLDS)
        {
            sites = WORLDS.computeIfAbsent(state, s -> new BossSites(SiteTerrain.forWorld(dross)));
        }
        lastWorld = sites;
        return sites;
    }

    /** The plan a world with this seed would have: a new one each call (not remembered), for the headless checks and previews. */
    public static BossSites forSeed(ServerLevel dross, long seed)
    {
        return new BossSites(SiteTerrain.forSeed(dross, seed));
    }

    /** The plan of the world whose generator uses this RandomState (world generation's view), or null if it isn't the Dross. */
    @Nullable
    public static BossSites forWorldgen(RandomState state)
    {
        BossSites last = lastWorld;
        if (last != null && last.terrain.randomState() == state)
        {
            return last;
        }
        synchronized (WORLDS)
        {
            BossSites known = WORLDS.get(state);
            if (known != null)
            {
                lastWorld = known;
                return known;
            }
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel dross = server == null ? null : server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross != null && dross.getChunkSource().randomState() == state)
        {
            return forLevel(dross);
        }
        return null;
    }

    /** When the world has started: the background reader begins reading the Dross's biome map. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel dross = event.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross != null)
        {
            forLevel(dross).startReader();
        }
    }

    /** Every second: where the Dross's players are (the background reader keeps the map read past them). */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % BossSitesTuning.READER_PLAYER_TICKS != 0)
        {
            return;
        }
        ServerLevel dross = event.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty())
        {
            if (!drossPlayers.isEmpty())
            {
                drossPlayers = List.of();
            }
            return;
        }
        List<BlockPos> where = new ArrayList<>();
        for (ServerPlayer player : dross.players())
        {
            if (!player.isSpectator())
            {
                where.add(player.blockPosition());
            }
        }
        drossPlayers = List.copyOf(where);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        synchronized (WORLDS)
        {
            WORLDS.values().forEach(BossSites::stopReader);
        }
        drossPlayers = List.of();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        synchronized (WORLDS)
        {
            WORLDS.values().forEach(BossSites::stopReader);
            WORLDS.clear();
        }
        lastWorld = null;
        drossPlayers = List.of();
    }

    // ---------------------------------------------------------------------------------------------
    // The plan's state
    // ---------------------------------------------------------------------------------------------

    private final SiteTerrain terrain;
    private final int hubX = DrossHub.CENTER.getX();
    private final int hubZ = DrossHub.CENTER.getZ();
    private final int hubCellX = Math.floorDiv(this.hubX, BossSitesTuning.CELL);
    private final int hubCellZ = Math.floorDiv(this.hubZ, BossSitesTuning.CELL);
    /** The biome map: each cell's kind (sampled once) and owner (its shape, then its patch). */
    private final BiomeCells cells;
    /**
     * Guards reading the map further (rings, patches, numbering, the cells' owners). Fair, so a thread that needs more
     * goes next. Never held while a group is planned, and never needed to answer about a place already read.
     */
    private final ReentrantLock scanLock = new ReentrantLock(true);
    /** Rings 0 to this (cells round the hub's cell, by their Chebyshev distance) have been read. (scanLock) */
    private int scannedRing = -1;
    /** Every cell of rings 0 to this has been sampled (written with scanLock held; read by the background reader without it). */
    private volatile int sampledRing = -1;
    /** Every patch nearer the hub than this has been found and numbered. (scanLock) */
    private double complete = -1.0D;
    /** (scanLock) */
    private boolean limitReached;
    /** Time spent following new patches and numbering found ones, so far (scanLock; for the log). */
    private long floodNanos;
    private long numberNanos;
    /** What's been read so far, for any thread (replaced after every step). */
    private volatile Published published;
    private final Map<ResourceKey<Biome>, BiomePlan> biomes = new LinkedHashMap<>();
    /** The boss biomes' plans, by their index in {@link #BOSS_BIOMES}. */
    private final BiomePlan[] plans;
    /** Where each use would fit in a patch (its finder's answer), by use, patch and relaxed. */
    private final Map<FitKey, Optional<Site>> fits = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Object>> caches = new ConcurrentHashMap<>();
    /** The background reader (only the running world's plan has one). */
    @Nullable
    private Thread reader;
    private volatile boolean stopReading;

    private record FitKey(SiteUse use, long patch, boolean relaxed)
    {
    }

    /**
     * What's been read so far, as one unchanging snapshot: every cell nearer the hub than {@code complete} has been read
     * and owns its final answer ({@link BiomeCells#owner}: written before this snapshot was published), and each biome's
     * first {@code counts[i]} patches are {@code numbered[i][0..counts[i] - 1]} (those entries never change).
     */
    private record Published(int ring, double complete, boolean limit, Patch[][] numbered, int[] counts)
    {
    }

    /** What a cell queued while a new patch is being followed is owned by, until the patch is recorded. */
    private static final Object QUEUED = new Object();

    /** A found patch waiting to be numbered (its cells own it until its {@link Patch} replaces it). Immutable. */
    static final class Shape
    {
        final long[] cells;
        final long nearest;
        final double distance;
        final boolean speck;

        Shape(long[] cells, long nearest, double distance, boolean speck)
        {
            this.cells = cells;
            this.nearest = nearest;
            this.distance = distance;
            this.speck = speck;
        }
    }

    private static final class BiomePlan
    {
        final BossBiome config;
        final int index;
        final byte kind;
        /** Found patches not numbered yet. (scanLock) */
        final List<Shape> pending = new ArrayList<>();
        /** The numbered patches, nearest first; only appended to (a full array is copied into a bigger one). (scanLock) */
        Patch[] numbered = new Patch[64];
        int count;
        final Map<Integer, CompletableFuture<Optional<Group>>> groups = new ConcurrentHashMap<>();

        BiomePlan(BossBiome config, int index)
        {
            this.config = config;
            this.index = index;
            this.kind = BiomeCells.bossKind(index);
        }

        void add(Patch patch)
        {
            if (this.count == this.numbered.length)
            {
                this.numbered = Arrays.copyOf(this.numbered, this.numbered.length * 2);
            }
            this.numbered[this.count++] = patch;
        }
    }

    private BossSites(SiteTerrain terrain)
    {
        this.terrain = terrain;
        this.plans = new BiomePlan[BOSS_BIOMES.size()];
        List<ResourceKey<Biome>> keys = new ArrayList<>();
        for (int i = 0; i < BOSS_BIOMES.size(); i++)
        {
            BossBiome b = BOSS_BIOMES.get(i);
            this.plans[i] = new BiomePlan(b, i);
            this.biomes.put(b.biome(), this.plans[i]);
            keys.add(b.biome());
        }
        this.cells = new BiomeCells(terrain, keys, this.hubCellX, this.hubCellZ);
        this.scanLock.lock();
        try
        {
            publish();
        }
        finally
        {
            this.scanLock.unlock();
        }
    }

    /** The land this plan reads (its noise). */
    public SiteTerrain terrain()
    {
        return this.terrain;
    }

    /** The world seed it's for. */
    public long seed()
    {
        return this.terrain.seed();
    }

    /** How far out from the hub the biome map has been read (every patch nearer than this is numbered); for logs and checks. */
    public double readDistance()
    {
        Published p = this.published;
        return p.limit() ? BossSitesTuning.MAX_DISTANCE : Math.max(0.0D, p.complete());
    }

    /**
     * A named map that lives as long as this plan, for a use's own quick lookups worked out from the plan (the sewers keep
     * which entrances are near each chunk in one). Safe on any thread; keep it small (clear it now and then).
     */
    public Map<Long, Object> cache(String name)
    {
        return this.caches.computeIfAbsent(name, n -> new ConcurrentHashMap<>());
    }

    // ---------------------------------------------------------------------------------------------
    // Patches
    // ---------------------------------------------------------------------------------------------

    /** The biome's {@code count} nearest patches (fewer if {@link BossSitesTuning#MAX_DISTANCE} is reached first), nearest first. */
    public List<Patch> patches(ResourceKey<Biome> biome, int count)
    {
        BiomePlan b = plan(biome);
        Published p = numberedUpTo(b, count);
        return List.of(Arrays.copyOf(p.numbered()[b.index], Math.min(Math.max(count, 0), p.counts()[b.index])));
    }

    /** The biome's patch with this number (1 = the nearest), if it's within {@link BossSitesTuning#MAX_DISTANCE}. */
    public Optional<Patch> patch(ResourceKey<Biome> biome, int number)
    {
        if (number < 1)
        {
            return Optional.empty();
        }
        BiomePlan b = plan(biome);
        Published p = numberedUpTo(b, number);
        return number <= p.counts()[b.index] ? Optional.of(p.numbered()[b.index][number - 1]) : Optional.empty();
    }

    /** The patch of {@code biome} the column is in (empty: not that biome there, a speck, or too far). */
    public Optional<Patch> patchAt(ResourceKey<Biome> biome, int x, int z)
    {
        BiomePlan b = plan(biome);
        int cx = Math.floorDiv(x, BossSitesTuning.CELL);
        int cz = Math.floorDiv(z, BossSitesTuning.CELL);
        double distance = cellDistance(ChunkPos.asLong(cx, cz));
        while (true)
        {
            Published p = this.published;   // (read before the owner: the owners it vouches for were written before it)
            Object owner = this.cells.owner(cx, cz);
            if (owner instanceof Patch patch)
            {
                return patch.biome().equals(biome) ? Optional.of(patch) : Optional.empty();   // (a patch never changes)
            }
            if (owner instanceof Shape shape && shape.speck)
            {
                return Optional.empty();   // (a speck is never numbered)
            }
            if (this.cells.kind(cx, cz) != b.kind)
            {
                return Optional.empty();   // (not this biome: the quick answer, however far out)
            }
            if (p.limit() || distance < p.complete())
            {
                return Optional.empty();   // (read, and not in a numbered patch: past the planner's reach)
            }
            readPast(distance);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Groups
    // ---------------------------------------------------------------------------------------------

    /** The biome's group {@code index} (0 = the 3 nearest patches), planned; empty if it lies past {@link BossSitesTuning#MAX_DISTANCE}. */
    public Optional<Group> group(ResourceKey<Biome> biome, int index)
    {
        BiomePlan b = plan(biome);
        CompletableFuture<Optional<Group>> future;
        boolean mine = false;
        synchronized (b.groups)
        {
            future = b.groups.get(index);
            if (future == null)
            {
                future = new CompletableFuture<>();
                b.groups.put(index, future);
                mine = true;
            }
        }
        if (mine)
        {
            try
            {
                future.complete(planGroup(b, index));
            }
            catch (RuntimeException | Error e)
            {
                synchronized (b.groups)
                {
                    b.groups.remove(index);
                }
                future.completeExceptionally(e);
                throw e;
            }
        }
        return future.join();
    }

    /** The biome's first {@code count} groups (fewer past {@link BossSitesTuning#MAX_DISTANCE}). */
    public List<Group> groups(ResourceKey<Biome> biome, int count)
    {
        List<Group> list = new ArrayList<>();
        for (int g = 0; g < count; g++)
        {
            Optional<Group> group = group(biome, g);
            if (group.isEmpty())
            {
                break;
            }
            list.add(group.get());
        }
        return list;
    }

    private Optional<Group> planGroup(BiomePlan b, int index)
    {
        long started = Util.getMillis();
        int first = index * BossSitesTuning.GROUP_SIZE + 1;
        List<Patch> patches = new ArrayList<>();
        for (int n = first; n < first + BossSitesTuning.GROUP_SIZE; n++)
        {
            patch(b.config.biome(), n).ifPresent(patches::add);
        }
        if (patches.isEmpty())
        {
            return Optional.empty();
        }
        BossBiome config = b.config;
        // the boss: one patch where it fits, picked by the seed (the fit tests side by side: each depends only on its patch)
        List<Integer> fitting = new ArrayList<>();
        List<Optional<Site>> bossFits = new ArrayList<>();
        if (config.boss() != null)
        {
            PlannerThreads.runAll(patches.size(), i -> fitIn(config.boss(), patches.get(i), false));
            for (int i = 0; i < patches.size(); i++)
            {
                Optional<Site> fit = fitIn(config.boss(), patches.get(i), false);
                bossFits.add(fit);
                if (fit.isPresent())
                {
                    fitting.add(i);
                }
            }
        }
        int bossIndex = -1;
        Optional<Site> bossSite = Optional.empty();
        if (config.boss() == null)
        {
            // (no boss yet: every patch is kept, slots 1 to 3)
        }
        else if (!fitting.isEmpty())
        {
            long h = mix(seed() ^ BossSitesTuning.BOSS_PICK_SALT, config.biome().location().hashCode(), index);
            bossIndex = fitting.get((int) Math.floorMod(h, (long) fitting.size()));
            bossSite = bossFits.get(bossIndex);
        }
        else
        {
            // no patch fits: the roomiest gets the boss's smallest form
            int roomiest = 0;
            for (int i = 1; i < patches.size(); i++)
            {
                if (patches.get(i).depth() > patches.get(roomiest).depth())
                {
                    roomiest = i;
                }
            }
            Optional<Site> small = fitIn(config.boss(), patches.get(roomiest), true);
            if (small.isPresent())
            {
                bossIndex = roomiest;
                bossSite = small;
                LOGGER.info("[boss sites] {} group {}: no patch has room for {} by its normal rules; the roomiest (patch {}, {} deep) gets its "
                        + "smallest form", config.biome().location().getPath(), index + 1, config.boss(), patches.get(roomiest).number(),
                        Math.round(patches.get(roomiest).depth()));
            }
            else
            {
                LOGGER.warn("[boss sites] {} group {}: {} fits in none of its {} patches, not even in its smallest form; this group has no boss",
                        config.biome().location().getPath(), index + 1, config.boss(), patches.size());
            }
        }
        // the slots: the boss's patch is 0, the others 1 and 2 (nearest first)
        List<Integer> slots = new ArrayList<>();
        int next = 1;
        for (int i = 0; i < patches.size(); i++)
        {
            slots.add(i == bossIndex ? 0 : next++);
        }
        // the fillers in the kept patches (their fit tests side by side too)
        List<Boolean> rolled = new ArrayList<>();
        List<Patch> toFit = new ArrayList<>();
        for (int i = 0; i < patches.size(); i++)
        {
            boolean roll = i != bossIndex && config.filler() != null && fillerRoll(patches.get(i)) < config.fillerChance();
            rolled.add(roll);
            if (roll)
            {
                toFit.add(patches.get(i));
            }
        }
        PlannerThreads.runAll(toFit.size(), i -> fitIn(config.filler(), toFit.get(i), false));
        List<Optional<Site>> fillers = new ArrayList<>();
        for (int i = 0; i < patches.size(); i++)
        {
            fillers.add(rolled.get(i) ? fitIn(config.filler(), patches.get(i), false) : Optional.empty());
        }
        Group group = new Group(config.biome(), index, List.copyOf(patches), List.copyOf(slots), bossSite, List.copyOf(rolled), List.copyOf(fillers));
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("[boss sites] {} group {} planned in {} ms: patches {}, boss in {}, fillers {}", config.biome().location().getPath(), index + 1,
                    Util.getMillis() - started, patches.stream().map(p -> p.number() + " (" + Math.round(p.distance()) + ")").toList(),
                    bossSite.map(s -> "patch " + s.patch().number() + " at " + s.pos().toShortString()).orElse("none"),
                    fillers.stream().map(f -> f.map(s -> s.pos().toShortString()).orElse("-")).toList());
        }
        return Optional.of(group);
    }

    /** A number from 0 to 1 fixed by the seed and the patch: its filler roll. */
    public double fillerRoll(Patch patch)
    {
        return (mix(seed() ^ BossSitesTuning.FILLER_ROLL_SALT, patch.biome().location().hashCode(), patch.id()) >>> 11) * 0x1.0p-53;
    }

    // ---------------------------------------------------------------------------------------------
    // Sites
    // ---------------------------------------------------------------------------------------------

    /**
     * Where {@code use} would fit in the patch (its finder's answer, remembered), whether or not the plan puts it there.
     *
     * @param relaxed the finder's smallest form (see {@link SiteFinder})
     */
    public Optional<Site> fitIn(SiteUse use, Patch patch, boolean relaxed)
    {
        if (!use.biome().equals(patch.biome()))
        {
            return Optional.empty();
        }
        FitKey key = new FitKey(use, patch.id(), relaxed);
        Optional<Site> known = this.fits.get(key);
        if (known != null)
        {
            return known;
        }
        Optional<Site> found;
        try
        {
            found = use.finder().find(this.terrain, patch, relaxed).map(f -> new Site(use, patch, f.pos(), relaxed, f.detail()));
        }
        catch (RuntimeException e)
        {
            LOGGER.error("[boss sites] {}'s fit test failed in {}; it counts as not fitting there.", use, patch, e);
            found = Optional.empty();
        }
        if (found.isPresent() && !patch.contains(found.get().pos().getX(), found.get().pos().getZ()))
        {
            LOGGER.error("[boss sites] {}'s fit test put it at {}, outside {}; it counts as not fitting there.", use, found.get().pos().toShortString(), patch);
            found = Optional.empty();
        }
        Optional<Site> previous = this.fits.putIfAbsent(key, found);
        return previous != null ? previous : found;
    }

    /** The planned site of {@code use} in the patch (empty if the plan doesn't put it there). */
    public Optional<Site> site(SiteUse use, Patch patch)
    {
        if (!use.biome().equals(patch.biome()))
        {
            return Optional.empty();
        }
        if (use.perPatch())
        {
            return fitIn(use, patch, false);   // (every patch, whatever its group: no need to plan the group)
        }
        return group(patch.biome(), patch.group()).map(g -> g.siteFor(use, patch));
    }

    /** The planned site of {@code use} whose spot is in this chunk (a structure's start chunk), if any. World generation calls it. */
    public Optional<Site> siteAtChunk(SiteUse use, ChunkPos chunk)
    {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        BiomePlan b = this.biomes.get(use.biome());
        if (b == null || hubDistance(x, z) > BossSitesTuning.MAX_DISTANCE || !isBiome(b, x, z))
        {
            return Optional.empty();   // (the quick answer for nearly every chunk: not even the biome)
        }
        Optional<Patch> patch = patchAt(use.biome(), x, z);
        if (patch.isEmpty())
        {
            return Optional.empty();
        }
        return site(use, patch.get()).filter(s -> s.chunk().equals(chunk));
    }

    /** True if a planned site of {@code use} starts in this chunk. */
    public boolean isSiteChunk(SiteUse use, int chunkX, int chunkZ)
    {
        return siteAtChunk(use, new ChunkPos(chunkX, chunkZ)).isPresent();
    }

    /** True if the column is the biome (a column at a cell's middle, like a chunk's middle: from the remembered biome map). */
    private boolean isBiome(BiomePlan b, int x, int z)
    {
        int cx = Math.floorDiv(x, BossSitesTuning.CELL);
        int cz = Math.floorDiv(z, BossSitesTuning.CELL);
        if (cx * BossSitesTuning.CELL + BossSitesTuning.CELL / 2 == x && cz * BossSitesTuning.CELL + BossSitesTuning.CELL / 2 == z)
        {
            return this.cells.kind(cx, cz) == b.kind;
        }
        return this.terrain.isBiome(b.config.biome(), x, z);
    }

    /**
     * The planned site of {@code use} nearest to {@code from} (by its spot, along the ground), within {@code maxDistance}
     * blocks of it. Only the groups that could hold a nearer one are planned, nearest first.
     */
    public Optional<Site> nearest(SiteUse use, BlockPos from, int maxDistance)
    {
        ResourceKey<Biome> biome = use.biome();
        if (!this.biomes.containsKey(biome))
        {
            return Optional.empty();
        }
        double fromHub = hubDistance(from.getX(), from.getZ());
        Site best = null;
        double bestDistance = maxDistance;
        // groups waiting to be planned, by the least distance any site of theirs could be from `from`
        PriorityQueue<double[]> queue = new PriorityQueue<>(Comparator.comparingDouble(e -> e[0]));
        for (int g = 0; ; g++)
        {
            Optional<Patch> first = patch(biome, g * BossSitesTuning.GROUP_SIZE + 1);
            // every site of this group and the later ones is at least this far from `from`
            double limit = first.isPresent() ? first.get().distance() - BossSitesTuning.CELL - fromHub : Double.MAX_VALUE;
            while (!queue.isEmpty() && queue.peek()[0] <= limit)
            {
                double[] e = queue.poll();
                if (e[0] < bestDistance)
                {
                    for (Site s : sitesOf(use, (int) e[1]))
                    {
                        double d = s.distanceTo(from.getX(), from.getZ());
                        if (d < bestDistance)
                        {
                            bestDistance = d;
                            best = s;
                        }
                    }
                }
            }
            if (first.isEmpty() || limit >= bestDistance)
            {
                break;
            }
            queue.add(new double[] {groupBound(biome, g, from, fromHub), g});
        }
        while (!queue.isEmpty())
        {
            double[] e = queue.poll();
            if (e[0] >= bestDistance)
            {
                break;
            }
            for (Site s : sitesOf(use, (int) e[1]))
            {
                double d = s.distanceTo(from.getX(), from.getZ());
                if (d < bestDistance)
                {
                    bestDistance = d;
                    best = s;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private List<Site> sitesOf(SiteUse use, int group)
    {
        if (use.perPatch())
        {
            return group(use.biome(), group).map(g -> g.patches().stream().flatMap(p -> site(use, p).stream()).toList()).orElse(List.of());
        }
        return group(use.biome(), group).map(g -> g.sites().stream().filter(s -> s.use() == use).toList()).orElse(List.of());
    }

    /** The least distance from `from` any spot in the group's patches could be. */
    private double groupBound(ResourceKey<Biome> biome, int group, BlockPos from, double fromHub)
    {
        double bound = Double.MAX_VALUE;
        for (int n = group * BossSitesTuning.GROUP_SIZE + 1; n <= (group + 1) * BossSitesTuning.GROUP_SIZE; n++)
        {
            Optional<Patch> p = patch(biome, n);
            if (p.isPresent())
            {
                bound = Math.min(bound, Math.max(p.get().boxDistance(from.getX(), from.getZ()), p.get().distance() - BossSitesTuning.CELL - fromHub));
            }
        }
        return bound;
    }

    /**
     * Every planned site of the biome's uses whose spot is within {@code radius} blocks of (x, z). Plans the groups of
     * the patches there.
     */
    public List<Site> sitesNear(ResourceKey<Biome> biome, int x, int z, int radius)
    {
        BiomePlan b = this.biomes.get(biome);
        if (b == null || hubDistance(x, z) - radius > BossSitesTuning.MAX_DISTANCE)
        {
            return List.of();
        }
        int reach = radius + BossSitesTuning.CELL;
        int c0x = Math.floorDiv(x - reach, BossSitesTuning.CELL);
        int c1x = Math.floorDiv(x + reach, BossSitesTuning.CELL);
        int c0z = Math.floorDiv(z - reach, BossSitesTuning.CELL);
        int c1z = Math.floorDiv(z + reach, BossSitesTuning.CELL);
        // (every cell holding a spot within the radius is nearer the hub than this, so its patch is numbered by then)
        readPast(hubDistance(x, z) + reach + BossSitesTuning.CELL);
        List<Integer> groups = new ArrayList<>();
        for (int cz = c0z; cz <= c1z; cz++)
        {
            for (int cx = c0x; cx <= c1x; cx++)
            {
                if (this.cells.owner(cx, cz) instanceof Patch patch && patch.biome().equals(biome) && !groups.contains(patch.group()))
                {
                    groups.add(patch.group());
                }
            }
        }
        List<Site> near = new ArrayList<>();
        List<SiteUse> perPatch = perPatchUses(biome);
        Collections.sort(groups);
        for (int g : groups)
        {
            group(biome, g).ifPresent(grp -> {
                List<Site> sites = new ArrayList<>(grp.sites());
                for (SiteUse use : perPatch)
                {
                    for (Patch p : grp.patches())
                    {
                        site(use, p).ifPresent(sites::add);
                    }
                }
                for (Site s : sites)
                {
                    if (s.distanceTo(x, z) <= radius)
                    {
                        near.add(s);
                    }
                }
            });
        }
        return near;
    }

    // ---------------------------------------------------------------------------------------------
    // Reading the biome map further (any thread that needs it, and the background reader)
    // ---------------------------------------------------------------------------------------------

    private BiomePlan plan(ResourceKey<Biome> biome)
    {
        BiomePlan b = this.biomes.get(biome);
        if (b == null)
        {
            throw new IllegalArgumentException(biome.location() + " isn't a boss biome (BossSites.BOSS_BIOMES)");
        }
        return b;
    }

    /** Makes sure every patch nearer the hub than {@code distance} is numbered (reading further if needed, with help). */
    private void readPast(double distance)
    {
        Published p = this.published;
        if (p.limit() || p.complete() > distance)
        {
            return;
        }
        long started = System.nanoTime();
        double before = p.complete();
        StepTimes here = new StepTimes();
        while (true)
        {
            p = this.published;
            if (p.limit() || p.complete() > distance)
            {
                break;
            }
            if (lockOrWait())
            {
                try
                {
                    while (this.complete <= distance && !this.limitReached)
                    {
                        step(here);
                    }
                }
                finally
                {
                    this.scanLock.unlock();
                }
            }
        }
        logWait(started, before, "out to " + Math.round(distance) + " blocks", here);
    }

    /** Makes sure the biome has {@code count} numbered patches (or the limit is reached), and returns what's published then. */
    private Published numberedUpTo(BiomePlan b, int count)
    {
        Published p = this.published;
        if (p.limit() || p.counts()[b.index] >= count)
        {
            return p;
        }
        long started = System.nanoTime();
        double before = p.complete();
        StepTimes here = new StepTimes();
        while (true)
        {
            p = this.published;
            if (p.limit() || p.counts()[b.index] >= count)
            {
                break;
            }
            if (lockOrWait())
            {
                try
                {
                    while (b.count < count && !this.limitReached)
                    {
                        step(here);
                    }
                }
                finally
                {
                    this.scanLock.unlock();
                }
            }
        }
        logWait(started, before, "until " + b.config.biome().location().getPath() + " has " + count + " patches", here);
        return p;
    }

    /** Takes the scan lock (true), or gives up after a short wait (false: look again at what's been published meanwhile). */
    private boolean lockOrWait()
    {
        try
        {
            return this.scanLock.tryLock(BossSitesTuning.READ_WAIT_POLL_MILLIS, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            this.scanLock.lock();
            return true;
        }
    }

    private void logWait(long startedNanos, double before, String what, StepTimes here)
    {
        long took = (System.nanoTime() - startedNanos) / 1_000_000L;
        if (took >= BossSitesTuning.SLOW_READ_LOG_MILLIS)
        {
            LOGGER.info("[boss sites] waited {} ms for the biome map to be read {} (it had been read out to {} blocks; now {}){}", took, what,
                    Math.round(Math.max(0.0D, before)), Math.round(readDistance()),
                    here.steps > 0 ? String.format(Locale.ROOT, "; this thread read %d band(s) of it: %d ms sampling (%d samples, with %d helper "
                            + "threads), %d ms joining the cells into patches (%d following new patches, %d numbering them; %d cells sampled "
                            + "while joining)", here.steps, here.sampleNanos / 1_000_000L, here.samples, PlannerThreads.HELPERS,
                            here.joinNanos / 1_000_000L, here.floodNanos / 1_000_000L, here.numberNanos / 1_000_000L, here.joinSamples) : "");
        }
    }

    /** What a thread's steps took (for the log). */
    private static final class StepTimes
    {
        int steps;
        long samples;
        long sampleNanos;
        long joinNanos;
        long floodNanos;
        long numberNanos;
        long joinSamples;
    }

    /**
     * Reads the next band of rings for a thread that's waiting for it (scanLock held): samples them (and
     * {@link BossSitesTuning#SAMPLE_AHEAD_RINGS} more, for the patches reaching out past them) with the helper threads,
     * joins and numbers them, and publishes.
     */
    private void step(StepTimes times)
    {
        long t0 = System.nanoTime();
        int from = this.scannedRing + 1;
        int to = from + bandRings(from) - 1;
        int sampleTo = Math.min(to, lastRing()) + BossSitesTuning.SAMPLE_AHEAD_RINGS;
        if (sampleTo > this.sampledRing)
        {
            long before = SiteTerrain.SAMPLES.sum();
            this.cells.sampleRings(this.hubCellX, this.hubCellZ, this.sampledRing + 1, sampleTo, true);
            this.sampledRing = sampleTo;
            times.samples += SiteTerrain.SAMPLES.sum() - before;
        }
        long t1 = System.nanoTime();
        long flood0 = this.floodNanos;
        long number0 = this.numberNanos;
        long samples0 = SiteTerrain.SAMPLES.sum();
        for (int r = from; r <= to && !this.limitReached; r++)
        {
            scanRing(r);
        }
        times.joinSamples += SiteTerrain.SAMPLES.sum() - samples0;
        publish();
        times.steps++;
        times.sampleNanos += t1 - t0;
        times.joinNanos += System.nanoTime() - t1;
        times.floodNanos += this.floodNanos - flood0;
        times.numberNanos += this.numberNanos - number0;
    }

    /**
     * One step of the background reader towards {@code target} blocks out: samples the next band of rings on this thread
     * without the lock (sampling is the slow part), then takes the lock only to join and number what's been sampled.
     */
    private void backgroundStep(double target, StepTimes times)
    {
        long t0 = System.nanoTime();
        int want = Math.min(ringFor(target), lastRing()) + BossSitesTuning.SAMPLE_AHEAD_RINGS;
        int from = this.sampledRing + 1;
        int to = Math.min(want, from + bandRings(Math.max(0, from - BossSitesTuning.SAMPLE_AHEAD_RINGS)) - 1);
        if (from <= to)
        {
            long before = SiteTerrain.SAMPLES.sum();
            this.cells.sampleRings(this.hubCellX, this.hubCellZ, from, to, false);
            times.samples += SiteTerrain.SAMPLES.sum() - before;
        }
        long t1 = System.nanoTime();
        times.sampleNanos += t1 - t0;
        this.scanLock.lock();
        try
        {
            if (to > this.sampledRing)
            {
                this.sampledRing = to;   // (it only grows: rings up to from - 1 were sampled before, so all up to `to` are now)
            }
            int joinTo = (this.sampledRing >= want ? want : this.sampledRing) - BossSitesTuning.SAMPLE_AHEAD_RINGS;
            joinTo = Math.max(joinTo, this.scannedRing + 1);   // (at least one ring, so it always gets on)
            long t2 = System.nanoTime();
            long flood0 = this.floodNanos;
            long number0 = this.numberNanos;
            long samples0 = SiteTerrain.SAMPLES.sum();
            while (this.scannedRing < joinTo && !this.limitReached && this.complete <= target)
            {
                scanRing(this.scannedRing + 1);
            }
            times.joinSamples += SiteTerrain.SAMPLES.sum() - samples0;
            publish();
            times.steps++;
            times.joinNanos += System.nanoTime() - t2;
            times.floodNanos += this.floodNanos - flood0;
            times.numberNanos += this.numberNanos - number0;
        }
        finally
        {
            this.scanLock.unlock();
        }
    }

    /** How many rings a step reads from ring {@code from} on: about {@link BossSitesTuning#BAND_CELLS} cells. */
    private static int bandRings(int from)
    {
        return Math.max(BossSitesTuning.BAND_MIN_RINGS, Math.min(BossSitesTuning.BAND_MAX_RINGS, BossSitesTuning.BAND_CELLS / (8 * Math.max(1, from))));
    }

    /** The last ring the planner reads (reading it reaches {@link BossSitesTuning#MAX_DISTANCE}). */
    private static int lastRing()
    {
        return (BossSitesTuning.MAX_DISTANCE + BossSitesTuning.CELL) / BossSitesTuning.CELL + 1;
    }

    /** The first ring whose reading reads every cell nearer the hub than {@code distance} (capped at the last ring). */
    private int ringFor(double distance)
    {
        int r = Math.max(0, (int) (distance / BossSitesTuning.CELL) - 2);
        while (r < lastRing() && completeRadius(r) <= distance)
        {
            r++;
        }
        return r;
    }

    /** Publishes what's been read so far (scanLock held). */
    private void publish()
    {
        Patch[][] numbered = new Patch[this.plans.length][];
        int[] counts = new int[this.plans.length];
        for (int i = 0; i < this.plans.length; i++)
        {
            numbered[i] = this.plans[i].numbered;
            counts[i] = this.plans[i].count;
        }
        this.published = new Published(this.scannedRing, this.complete, this.limitReached, numbered, counts);
    }

    /** Reads ring r (= scannedRing + 1; scanLock held): follows every new patch met, and numbers those now known complete. */
    private void scanRing(int r)
    {
        if ((long) r * BossSitesTuning.CELL > BossSitesTuning.MAX_DISTANCE + BossSitesTuning.CELL)
        {
            this.limitReached = true;
            this.complete = Double.MAX_VALUE;
            for (BiomePlan b : this.plans)
            {
                promote(b, BossSitesTuning.MAX_DISTANCE);
            }
            return;
        }
        if (r == 0)
        {
            visit(this.hubCellX, this.hubCellZ);
        }
        else
        {
            int cx = this.hubCellX;
            int cz = this.hubCellZ;
            for (int i = -r; i <= r; i++)
            {
                visit(cx + i, cz - r);
            }
            for (int j = -r + 1; j <= r; j++)
            {
                visit(cx + r, cz + j);
            }
            for (int i = r - 1; i >= -r; i--)
            {
                visit(cx + i, cz + r);
            }
            for (int j = r - 1; j >= -r + 1; j--)
            {
                visit(cx - r, cz + j);
            }
        }
        this.scannedRing = r;
        this.complete = completeRadius(r);
        for (BiomePlan b : this.plans)
        {
            promote(b, this.complete);
        }
    }

    /** Every cell not yet read lies at least this far from the hub (rings 0 to r read). */
    private double completeRadius(int r)
    {
        int c = BossSitesTuning.CELL;
        int half = c / 2;
        double east = (this.hubCellX + r + 1) * (double) c + half - this.hubX;
        double west = this.hubX - ((this.hubCellX - r - 1) * (double) c + half);
        double south = (this.hubCellZ + r + 1) * (double) c + half - this.hubZ;
        double north = this.hubZ - ((this.hubCellZ - r - 1) * (double) c + half);
        return Math.min(Math.min(east, west), Math.min(south, north));
    }

    /** A cell met on a ring (scanLock held): if it's a boss biome and no patch has it yet, a new patch starts there. */
    private void visit(int cx, int cz)
    {
        if (this.cells.owner(cx, cz) != null)
        {
            return;
        }
        int boss = BiomeCells.bossIndex(this.cells.kind(cx, cz));
        if (boss >= 0)
        {
            flood(this.plans[boss], ChunkPos.asLong(cx, cz));
        }
    }

    /** Follows a new patch out from one of its cells (4-way, nearest neighbours first) and records it (scanLock held). */
    private void flood(BiomePlan b, long start)
    {
        long t0 = System.nanoTime();
        ResourceKey<Biome> key = b.config.biome();
        // (a queued cell is marked as owned by QUEUED until the patch is recorded, so it's never queued twice)
        LongArrayList queue = new LongArrayList();
        queue.add(start);
        this.cells.setOwner(start, QUEUED);
        int head = 0;
        int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (head < queue.size() && head < BossSitesTuning.MAX_PATCH_CELLS)
        {
            long cell = queue.getLong(head++);
            int cx = ChunkPos.getX(cell);
            int cz = ChunkPos.getZ(cell);
            for (int[] s : steps)
            {
                // (a cell another patch owns is never this one's: patches of one biome that touch are one patch, and a
                // cell of another biome isn't this biome)
                if (this.cells.owner(cx + s[0], cz + s[1]) == null && this.cells.kind(cx + s[0], cz + s[1]) == b.kind)
                {
                    long n = ChunkPos.asLong(cx + s[0], cz + s[1]);
                    this.cells.setOwner(n, QUEUED);
                    queue.add(n);
                }
            }
        }
        // (cells queued past the size limit aren't this patch's: they're free again, for a later patch)
        for (int i = head; i < queue.size(); i++)
        {
            this.cells.setOwner(queue.getLong(i), null);
        }
        long[] sorted = Arrays.copyOf(queue.elements(), head);
        Arrays.sort(sorted);
        long nearest = sorted[0];
        double nearestDistance = Double.MAX_VALUE;
        for (long c : sorted)
        {
            double d = cellDistance(c);
            if (d < nearestDistance)   // (ties keep the smaller cell: the array is sorted)
            {
                nearestDistance = d;
                nearest = c;
            }
        }
        boolean speck = (long) sorted.length * BossSitesTuning.CELL * BossSitesTuning.CELL < BossSitesTuning.MIN_PATCH_AREA;
        Shape shape = new Shape(sorted, nearest, nearestDistance, speck);
        for (long c : sorted)
        {
            this.cells.setOwner(c, shape);
        }
        if (!speck)
        {
            b.pending.add(shape);
        }
        if (head >= BossSitesTuning.MAX_PATCH_CELLS)
        {
            LOGGER.warn("[boss sites] a {} patch near {} reached {} chunks and was cut there", key.location().getPath(),
                    Patch.middle(start).toShortString(), BossSitesTuning.MAX_PATCH_CELLS);
        }
        this.floodNanos += System.nanoTime() - t0;
    }

    /** Numbers the found patches nearer than {@code radius} (all patches that near have been found), nearest first (scanLock held). */
    private void promote(BiomePlan b, double radius)
    {
        List<Shape> ready = new ArrayList<>();
        for (Shape s : b.pending)
        {
            if (s.distance < radius)
            {
                ready.add(s);
            }
        }
        if (ready.isEmpty())
        {
            return;
        }
        long t0 = System.nanoTime();
        b.pending.removeAll(ready);
        ready.sort(Comparator.comparingDouble((Shape s) -> s.distance).thenComparingLong(s -> s.nearest));
        for (Shape s : ready)
        {
            Patch patch = makePatch(b.config.biome(), b.count + 1, s);
            b.add(patch);
            for (long c : s.cells)
            {
                this.cells.setOwner(c, patch);
            }
        }
        this.numberNanos += System.nanoTime() - t0;
    }

    /** The patch's geometry: how deep each of its cells lies (a two-pass distance transform over its box). */
    private Patch makePatch(ResourceKey<Biome> biome, int number, Shape s)
    {
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (long c : s.cells)
        {
            x0 = Math.min(x0, ChunkPos.getX(c));
            z0 = Math.min(z0, ChunkPos.getZ(c));
            x1 = Math.max(x1, ChunkPos.getX(c));
            z1 = Math.max(z1, ChunkPos.getZ(c));
        }
        // a box one cell bigger every way, so its rim is outside the patch
        int w = x1 - x0 + 3;
        int h = z1 - z0 + 3;
        int big = 1 << 20;
        int[] ox = new int[w * h];
        int[] oz = new int[w * h];
        boolean[] in = new boolean[w * h];
        for (long c : s.cells)
        {
            in[(ChunkPos.getZ(c) - z0 + 1) * w + (ChunkPos.getX(c) - x0 + 1)] = true;
        }
        for (int i = 0; i < w * h; i++)
        {
            ox[i] = in[i] ? big : 0;
            oz[i] = in[i] ? big : 0;
        }
        // offsets to the nearest cell outside the patch, carried forward then back (8SSEDT)
        for (int z = 0; z < h; z++)
        {
            for (int x = 0; x < w; x++)
            {
                relax(ox, oz, w, h, x, z, -1, 0);
                relax(ox, oz, w, h, x, z, 0, -1);
                relax(ox, oz, w, h, x, z, -1, -1);
                relax(ox, oz, w, h, x, z, 1, -1);
            }
            for (int x = w - 1; x >= 0; x--)
            {
                relax(ox, oz, w, h, x, z, 1, 0);
            }
        }
        for (int z = h - 1; z >= 0; z--)
        {
            for (int x = w - 1; x >= 0; x--)
            {
                relax(ox, oz, w, h, x, z, 1, 0);
                relax(ox, oz, w, h, x, z, 0, 1);
                relax(ox, oz, w, h, x, z, -1, 1);
                relax(ox, oz, w, h, x, z, 1, 1);
            }
            for (int x = 0; x < w; x++)
            {
                relax(ox, oz, w, h, x, z, -1, 0);
            }
        }
        float[] depths = new float[s.cells.length];
        for (int i = 0; i < s.cells.length; i++)
        {
            int k = (ChunkPos.getZ(s.cells[i]) - z0 + 1) * w + (ChunkPos.getX(s.cells[i]) - x0 + 1);
            double cellsOut = Math.sqrt((double) ox[k] * ox[k] + (double) oz[k] * oz[k]);
            depths[i] = (float) (cellsOut * BossSitesTuning.CELL - BossSitesTuning.CELL / 2.0D);
        }
        // the cells deepest first (ties: nearer the hub, then the smaller cell; every cell differs, so there's only one order)
        double[] fromHub = new double[s.cells.length];
        int[] byDepth = new int[s.cells.length];
        for (int i = 0; i < byDepth.length; i++)
        {
            fromHub[i] = cellDistance(s.cells[i]);
            byDepth[i] = i;
        }
        IntArrays.quickSort(byDepth, (a, c) -> {
            int order = Double.compare(-depths[a], -depths[c]);
            if (order == 0)
            {
                order = Double.compare(fromHub[a], fromHub[c]);
            }
            return order != 0 ? order : Long.compare(s.cells[a], s.cells[c]);
        });
        return new Patch(biome, number, s.distance, s.nearest, s.cells, depths, byDepth);
    }

    private static void relax(int[] ox, int[] oz, int w, int h, int x, int z, int dx, int dz)
    {
        int nx = x + dx;
        int nz = z + dz;
        if (nx < 0 || nz < 0 || nx >= w || nz >= h)
        {
            return;
        }
        int i = z * w + x;
        int n = nz * w + nx;
        long cx = (long) ox[n] + Math.abs(dx);
        long cz = (long) oz[n] + Math.abs(dz);
        if (cx * cx + cz * cz < (long) ox[i] * ox[i] + (long) oz[i] * oz[i])
        {
            ox[i] = (int) cx;
            oz[i] = (int) cz;
        }
    }

    /** Blocks from the hub to the middle of a cell. */
    private double cellDistance(long cell)
    {
        double dx = ChunkPos.getX(cell) * (double) BossSitesTuning.CELL + BossSitesTuning.CELL / 2.0D - this.hubX;
        double dz = ChunkPos.getZ(cell) * (double) BossSitesTuning.CELL + BossSitesTuning.CELL / 2.0D - this.hubZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Blocks from the hub to a column. */
    public double hubDistance(int x, int z)
    {
        double dx = x - this.hubX;
        double dz = z - this.hubZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A well-mixed number from a seed and two values (SplitMix64's finaliser). */
    static long mix(long seed, long a, long b)
    {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    // ---------------------------------------------------------------------------------------------
    // The background reader (the running world's plan only)
    // ---------------------------------------------------------------------------------------------

    /** Starts the background reader (once): see "How it's worked out" above. */
    synchronized void startReader()
    {
        if (this.reader != null || this.stopReading)
        {
            return;
        }
        Thread t = new Thread(this::readInBackground, "Dross boss sites reader");
        t.setDaemon(true);
        t.setPriority(BossSitesTuning.READER_THREAD_PRIORITY);
        this.reader = t;
        t.start();
    }

    /** Stops the background reader (when the server stops): it finishes what it's doing and quits. */
    synchronized void stopReader()
    {
        this.stopReading = true;
        if (this.reader != null)
        {
            this.reader.interrupt();
        }
    }

    private void readInBackground()
    {
        long started = Util.getMillis();
        StepTimes times = new StepTimes();
        boolean startRead = false;
        double lastLogged = 0.0D;
        int failures = 0;
        while (!this.stopReading)
        {
            try
            {
                double target = readerTarget();
                Published p = this.published;
                if (!p.limit() && p.complete() <= target)
                {
                    backgroundStep(target, times);
                    continue;
                }
                if (!startRead)
                {
                    startRead = true;
                    lastLogged = readDistance();
                    LOGGER.info("[boss sites] read the Dross's biome map out to {} blocks from the hub in the background in {} ms ({} ms sampling: {} "
                                    + "biome samples, quick sampler {}; {} ms joining the cells into patches: {} following new patches, {} numbering them); "
                                    + "it now keeps {} blocks past the furthest player and plans the groups within {} blocks of them",
                            Math.round(readDistance()), Util.getMillis() - started, times.sampleNanos / 1_000_000L, times.samples,
                            this.terrain.quickSampler() ? "on" : "OFF", times.joinNanos / 1_000_000L, times.floodNanos / 1_000_000L,
                            times.numberNanos / 1_000_000L, BossSitesTuning.READ_AHEAD, BossSitesTuning.PREPLAN_RADIUS);
                }
                else if (readDistance() >= lastLogged + 1000.0D)
                {
                    lastLogged = readDistance();
                    LOGGER.debug("[boss sites] the background reader has read the biome map out to {} blocks", Math.round(lastLogged));
                }
                planNearPlayers();
                Thread.sleep(BossSitesTuning.READER_PAUSE_MILLIS);
            }
            catch (InterruptedException e)
            {
                break;
            }
            catch (RuntimeException e)
            {
                failures++;
                LOGGER.error("[boss sites] the background reader failed{}", failures >= 3 ? "; it stops (world generation reads the map itself)" : "", e);
                if (failures >= 3)
                {
                    break;
                }
            }
        }
    }

    /** How far the background reader keeps the map read: past the furthest player, at least the start distance. */
    private double readerTarget()
    {
        double furthest = BossSitesTuning.READ_AT_START;
        for (BlockPos pos : drossPlayers)
        {
            furthest = Math.max(furthest, hubDistance(pos.getX(), pos.getZ()) + BossSitesTuning.READ_AHEAD);
        }
        return Math.min(furthest, BossSitesTuning.MAX_DISTANCE + 2.0D * BossSitesTuning.CELL);
    }

    /** Plans the groups of every patch near a player (each group once), so world generation finds them planned. */
    private void planNearPlayers()
    {
        for (BlockPos pos : drossPlayers)
        {
            for (BossBiome b : BOSS_BIOMES)
            {
                if (this.stopReading)
                {
                    return;
                }
                sitesNear(b.biome(), pos.getX(), pos.getZ(), BossSitesTuning.PREPLAN_RADIUS);
            }
        }
    }

    /**
     * Reads the map out past {@code distance} the way the background reader does (this thread alone, sampling without the
     * lock), for the checks that compare it with reading on demand.
     */
    void readInBackgroundTo(double distance)
    {
        StepTimes times = new StepTimes();
        while (true)
        {
            Published p = this.published;
            if (p.limit() || p.complete() > distance)
            {
                return;
            }
            backgroundStep(distance, times);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The stand-in fit test (a disc of the biome)
    // ---------------------------------------------------------------------------------------------

    /**
     * A fit test for a round site: the deepest spot of the patch where every column within {@code radius} is the biome
     * ({@code relaxedRadius} for the smallest form). The spot's y is the ground's top block + 1 there.
     */
    public static SiteFinder discFinder(ResourceKey<Biome> biome, int radius, int relaxedRadius)
    {
        return (terrain, patch, relaxed) -> {
            int r = relaxed ? relaxedRadius : radius;
            for (BlockPos spot : patch.spots(BossSitesTuning.SITE_TRIES, BossSitesTuning.SITE_TRY_SPACING))
            {
                if (patch.depthAt(spot.getX(), spot.getZ()) + BossSitesTuning.CELL < r)
                {
                    continue;   // (too near the edge to hold the disc: no need to sample it)
                }
                if (terrain.discIsBiome(biome, spot.getX(), spot.getZ(), r, BossSitesTuning.DISC_SAMPLE_STEP))
                {
                    return Optional.of(new SiteFinder.Found(spot.atY(terrain.topBlock(spot.getX(), spot.getZ()) + 1), null));
                }
            }
            return Optional.empty();
        };
    }
}
