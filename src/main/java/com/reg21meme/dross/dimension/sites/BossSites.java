package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.SewerSites;
import com.reg21meme.dross.portal.DrossHub;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.server.ServerStoppedEvent;
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
 * The boss biomes now: {@link DrossBiomes#CINDER_WASTES} (the Cinder Colossus, {@link #COLOSSUS_LAIR}) and
 * {@link DrossBiomes#BLIGHTWOOD} (the Rat King's sewer, {@link #RAT_KING_SEWER}; filler {@link #REGULAR_SEWER}). Rimefall
 * joins when its first boss arrives (add it to {@link #BOSS_BIOMES}).
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
 * Everything here is safe on any thread (world generation calls it from its own threads). The work is done lazily and
 * remembered: the biome map is read outwards from the hub ring by ring, as far as the questions need, and each group is
 * planned the first time it's asked about (about a second for a group of 3 Blightwood patches).
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

    /** Every use, by id (the structure sets' placement JSON names one: {@code "use": "dross:rat_king_sewer"}). */
    public static final List<SiteUse> USES = List.of(COLOSSUS_LAIR, RAT_KING_SEWER, REGULAR_SEWER);

    /**
     * A boss biome: its current boss (slot 0 of every group) and the filler for the two kept patches (or null).
     *
     * @param fillerChance the chance each kept patch gets the filler (if it fits)
     */
    public record BossBiome(ResourceKey<Biome> biome, SiteUse boss, @Nullable SiteUse filler, double fillerChance)
    {
    }

    /** The boss biomes. */
    public static final List<BossBiome> BOSS_BIOMES = List.of(
            new BossBiome(DrossBiomes.CINDER_WASTES, COLOSSUS_LAIR, null, 0.0D),
            new BossBiome(DrossBiomes.BLIGHTWOOD, RAT_KING_SEWER, REGULAR_SEWER, BossSitesTuning.REGULAR_SEWER_CHANCE));

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

    /** The boss biome entry for a biome, if it's one. */
    public static Optional<BossBiome> bossBiome(ResourceKey<Biome> biome)
    {
        return BOSS_BIOMES.stream().filter(b -> b.biome().equals(biome)).findFirst();
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

    /** The running world's plan (the Dross level's). Any thread. */
    public static BossSites forLevel(ServerLevel dross)
    {
        RandomState state = dross.getChunkSource().randomState();
        synchronized (WORLDS)
        {
            return WORLDS.computeIfAbsent(state, s -> new BossSites(SiteTerrain.forWorld(dross)));
        }
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
        synchronized (WORLDS)
        {
            BossSites known = WORLDS.get(state);
            if (known != null)
            {
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

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        synchronized (WORLDS)
        {
            WORLDS.clear();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The plan's state
    // ---------------------------------------------------------------------------------------------

    private final SiteTerrain terrain;
    private final int hubX = DrossHub.CENTER.getX();
    private final int hubZ = DrossHub.CENTER.getZ();
    private final int hubCellX = Math.floorDiv(this.hubX, BossSitesTuning.CELL);
    private final int hubCellZ = Math.floorDiv(this.hubZ, BossSitesTuning.CELL);
    /** Guards the biome map's reading (rings, patches, numbering). Never held while a group is planned. */
    private final Object scanLock = new Object();
    /** Rings 0 to this (cells round the hub's chunk, by their Chebyshev distance) have been read. */
    private int scannedRing = -1;
    /** Every patch nearer the hub than this has been found and numbered. */
    private double complete = -1.0D;
    private boolean limitReached;
    private final Map<ResourceKey<Biome>, BiomePlan> biomes = new LinkedHashMap<>();
    /** Where each use would fit in a patch (its finder's answer), by use, patch and relaxed. */
    private final Map<FitKey, Optional<Site>> fits = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Object>> caches = new ConcurrentHashMap<>();

    private record FitKey(SiteUse use, long patch, boolean relaxed)
    {
    }

    /** A found patch, before and after it's numbered. */
    private static final class Shape
    {
        final long[] cells;
        final long nearest;
        final double distance;
        final boolean speck;
        @Nullable Patch patch;

        Shape(long[] cells, long nearest, double distance, boolean speck)
        {
            this.cells = cells;
            this.nearest = nearest;
            this.distance = distance;
            this.speck = speck;
        }
    }

    private final class BiomePlan
    {
        final BossBiome config;
        final Long2ObjectOpenHashMap<Shape> cells = new Long2ObjectOpenHashMap<>();
        final List<Shape> pending = new ArrayList<>();
        final List<Patch> numbered = new ArrayList<>();
        final Map<Integer, CompletableFuture<Optional<Group>>> groups = new ConcurrentHashMap<>();

        BiomePlan(BossBiome config)
        {
            this.config = config;
        }
    }

    private BossSites(SiteTerrain terrain)
    {
        this.terrain = terrain;
        for (BossBiome b : BOSS_BIOMES)
        {
            this.biomes.put(b.biome(), new BiomePlan(b));
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
        synchronized (this.scanLock)
        {
            numberUpTo(b, count);
            return List.copyOf(b.numbered.subList(0, Math.min(count, b.numbered.size())));
        }
    }

    /** The biome's patch with this number (1 = the nearest), if it's within {@link BossSitesTuning#MAX_DISTANCE}. */
    public Optional<Patch> patch(ResourceKey<Biome> biome, int number)
    {
        if (number < 1)
        {
            return Optional.empty();
        }
        BiomePlan b = plan(biome);
        synchronized (this.scanLock)
        {
            numberUpTo(b, number);
            return number <= b.numbered.size() ? Optional.of(b.numbered.get(number - 1)) : Optional.empty();
        }
    }

    /** The patch of {@code biome} the column is in (empty: not that biome there, a speck, or too far). */
    public Optional<Patch> patchAt(ResourceKey<Biome> biome, int x, int z)
    {
        BiomePlan b = plan(biome);
        long cell = ChunkPos.asLong(Math.floorDiv(x, BossSitesTuning.CELL), Math.floorDiv(z, BossSitesTuning.CELL));
        synchronized (this.scanLock)
        {
            scanPast(cellDistance(cell));
            Shape shape = b.cells.get(cell);
            return shape == null || shape.patch == null ? Optional.empty() : Optional.of(shape.patch);
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
        // the boss: one patch where it fits, picked by the seed
        List<Integer> fitting = new ArrayList<>();
        List<Optional<Site>> bossFits = new ArrayList<>();
        for (int i = 0; i < patches.size(); i++)
        {
            Optional<Site> fit = fitIn(config.boss(), patches.get(i), false);
            bossFits.add(fit);
            if (fit.isPresent())
            {
                fitting.add(i);
            }
        }
        int bossIndex = -1;
        Optional<Site> bossSite = Optional.empty();
        if (!fitting.isEmpty())
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
        // the fillers in the kept patches
        List<Boolean> rolled = new ArrayList<>();
        List<Optional<Site>> fillers = new ArrayList<>();
        for (int i = 0; i < patches.size(); i++)
        {
            boolean roll = i != bossIndex && config.filler() != null && fillerRoll(patches.get(i)) < config.fillerChance();
            rolled.add(roll);
            fillers.add(roll ? fitIn(config.filler(), patches.get(i), false) : Optional.empty());
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
        return group(patch.biome(), patch.group()).map(g -> g.siteFor(use, patch));
    }

    /** The planned site of {@code use} whose spot is in this chunk (a structure's start chunk), if any. World generation calls it. */
    public Optional<Site> siteAtChunk(SiteUse use, ChunkPos chunk)
    {
        int x = chunk.getMiddleBlockX();
        int z = chunk.getMiddleBlockZ();
        if (hubDistance(x, z) > BossSitesTuning.MAX_DISTANCE || !this.terrain.isBiome(use.biome(), x, z))
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
        List<Integer> groups = new ArrayList<>();
        synchronized (this.scanLock)
        {
            scanPast(hubDistance(x, z) + reach + BossSitesTuning.CELL);
            for (int cz = c0z; cz <= c1z; cz++)
            {
                for (int cx = c0x; cx <= c1x; cx++)
                {
                    Shape shape = b.cells.get(ChunkPos.asLong(cx, cz));
                    if (shape != null && shape.patch != null && !groups.contains(shape.patch.group()))
                    {
                        groups.add(shape.patch.group());
                    }
                }
            }
        }
        List<Site> near = new ArrayList<>();
        Collections.sort(groups);
        for (int g : groups)
        {
            group(biome, g).ifPresent(grp -> {
                for (Site s : grp.sites())
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
    // Reading the biome map, ring by ring out from the hub
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

    /** Reads rings until the biome has {@code count} numbered patches (or the limit). Hold scanLock. */
    private void numberUpTo(BiomePlan b, int count)
    {
        while (b.numbered.size() < count && !this.limitReached)
        {
            scanNextRing();
        }
    }

    /** Reads rings until every patch nearer than {@code distance} is numbered (or the limit). Hold scanLock. */
    private void scanPast(double distance)
    {
        long started = Util.getMillis();
        int rings = 0;
        while (this.complete <= distance && !this.limitReached)
        {
            scanNextRing();
            rings++;
        }
        long took = Util.getMillis() - started;
        if (took > 2000L)
        {
            LOGGER.info("[boss sites] read the biome map out to {} blocks from the hub ({} more rings) in {} ms", Math.round(this.complete), rings, took);
        }
    }

    private void scanNextRing()
    {
        int r = this.scannedRing + 1;
        if ((long) r * BossSitesTuning.CELL > BossSitesTuning.MAX_DISTANCE + BossSitesTuning.CELL)
        {
            this.limitReached = true;
            this.complete = Double.MAX_VALUE;
            for (BiomePlan b : this.biomes.values())
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
        for (BiomePlan b : this.biomes.values())
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

    private void visit(int cx, int cz)
    {
        long cell = ChunkPos.asLong(cx, cz);
        for (BiomePlan b : this.biomes.values())
        {
            if (b.cells.containsKey(cell))
            {
                return;
            }
        }
        Holder<Biome> biome = this.terrain.biome(cx * BossSitesTuning.CELL + BossSitesTuning.CELL / 2, cz * BossSitesTuning.CELL + BossSitesTuning.CELL / 2);
        for (BiomePlan b : this.biomes.values())
        {
            if (biome.is(b.config.biome()))
            {
                flood(b, cell);
                return;
            }
        }
    }

    /** Follows a new patch out from one of its cells (4-way, nearest neighbours first) and records it. */
    private void flood(BiomePlan b, long start)
    {
        ResourceKey<Biome> key = b.config.biome();
        LongArrayList cells = new LongArrayList();
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList queue = new LongArrayList();
        queue.add(start);
        seen.add(start);
        int head = 0;
        int[][] steps = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        while (head < queue.size() && cells.size() < BossSitesTuning.MAX_PATCH_CELLS)
        {
            long cell = queue.getLong(head++);
            cells.add(cell);
            int cx = ChunkPos.getX(cell);
            int cz = ChunkPos.getZ(cell);
            for (int[] s : steps)
            {
                long n = ChunkPos.asLong(cx + s[0], cz + s[1]);
                if (!seen.add(n) || b.cells.containsKey(n))
                {
                    continue;
                }
                if (this.terrain.isBiome(key, (cx + s[0]) * BossSitesTuning.CELL + BossSitesTuning.CELL / 2,
                        (cz + s[1]) * BossSitesTuning.CELL + BossSitesTuning.CELL / 2))
                {
                    queue.add(n);
                }
            }
        }
        long[] sorted = cells.toLongArray();
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
            b.cells.put(c, shape);
        }
        if (!speck)
        {
            b.pending.add(shape);
        }
        if (cells.size() >= BossSitesTuning.MAX_PATCH_CELLS)
        {
            LOGGER.warn("[boss sites] a {} patch near {} reached {} chunks and was cut there", key.location().getPath(),
                    Patch.middle(start).toShortString(), BossSitesTuning.MAX_PATCH_CELLS);
        }
    }

    /** Numbers the found patches nearer than {@code radius} (all patches that near have been found), nearest first. */
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
        b.pending.removeAll(ready);
        ready.sort(Comparator.comparingDouble((Shape s) -> s.distance).thenComparingLong(s -> s.nearest));
        for (Shape s : ready)
        {
            s.patch = makePatch(b.config.biome(), b.numbered.size() + 1, s);
            b.numbered.add(s.patch);
        }
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
            double cells = Math.sqrt((double) ox[k] * ox[k] + (double) oz[k] * oz[k]);
            depths[i] = (float) (cells * BossSitesTuning.CELL - BossSitesTuning.CELL / 2.0D);
        }
        Integer[] order = new Integer[s.cells.length];
        for (int i = 0; i < order.length; i++)
        {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer i) -> -depths[i]).thenComparingDouble(i -> cellDistance(s.cells[i]))
                .thenComparingLong(i -> s.cells[i]));
        int[] byDepth = new int[order.length];
        for (int i = 0; i < order.length; i++)
        {
            byDepth[i] = order[i];
        }
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
