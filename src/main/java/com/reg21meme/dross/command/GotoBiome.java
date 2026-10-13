package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.lair.ColossusLair;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.portal.DrossTeleporter;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * {@code /dross goto biome <name>}: teleports a player to the nearest place of one of the Dross's six biomes
 * ({@code silverwood_forest}, {@code cinder_wastes}, {@code blightwood}, {@code rimefall}, {@code nether_scar},
 * {@code gravecrags}). World site wires it into {@link DrossCommand}; finding the biome is the dimension area's
 * {@link DrossBiomes#findNearest}, and what counts as safe ground is the portal area's {@link DrossTeleporter#findSafeSpot}.
 * <ol>
 *   <li><b>Where the search starts:</b> where the player stands, if they're in the Dross; from any other dimension, the
 *       hub's centre ({@link DrossHub#CENTER}). A player who is already standing in that biome in the Dross is told so
 *       and isn't moved.</li>
 *   <li><b>The nearest place</b> of the biome: {@link DrossBiomes#findNearest} (Minecraft's own biome search, like
 *       {@code /locate biome}: it only reads the biome noise, makes no chunks, and looks up to
 *       {@value DrossBiomes#SEARCH_RADIUS} blocks out). It gives a place a little way inside the biome's near edge.</li>
 *   <li><b>Where to land:</b> the column nearest that place (up to {@value #LANDING_RADIUS} blocks round it, along X and
 *       Z) that
 *       <ul>
 *         <li>is in that biome (the biome a player there would see on the F3 screen),</li>
 *         <li>is more than {@value #HUB_CLEARANCE} blocks from the hub's footprint (so the nearest Silverwood Forest seen
 *             from the Overworld, which is the hub itself, puts you in the forest beside the spire, never on its roof or
 *             in its hall),</li>
 *         <li>is outside the Cinder Colossus's lair: more than {@value #LAIR_CLEARANCE} blocks further from its middle
 *             than its entrance is (so never in its cavern, on its dome, in its tunnel or at its mouth; the boss area's
 *             {@link ColossusLair}; {@code /dross goto lair colossus} goes there). Before the lair is built, the spot
 *             picked for it is kept clear the same way ({@value #PLANNED_LAIR_REACH} blocks round it),</li>
 *         <li>has safe ground ({@link DrossTeleporter#findSafeSpot}: solid ground under the feet, room to stand, no
 *             lava, fire, magma, cactus, powder snow and so on; tree tops and giant mushroom caps never count as ground),</li>
 *         <li>and that ground is at most {@value #MAX_ABOVE_GROUND} blocks above the land's own height there (the height
 *             the terrain generator gives, before trees and rocks are added), so you never land on top of a huge
 *             fungus or an ice spike.</li>
 *       </ul>
 *       If no column there will do, up to {@value #FURTHER_TRIES} more places are tried, each {@value #FURTHER_STEP}
 *       blocks further on along the line from where the search started (further into the biome). The search gives up
 *       after looking at {@value #MAX_CHUNKS} chunks.</li>
 *   <li><b>The teleport:</b> like vanilla's {@code /tp} (it keeps the landing chunk loaded for a moment, gets you off a
 *       mount or out of bed), facing away from where the search started, so you look further into the biome. No arrival
 *       title or notes: those are only for travelling through the portal (the same as {@code /dross goto hub}).</li>
 * </ol>
 * Chat messages are lang keys ({@code commands.dross.goto.biome.*}) with the biome's own name
 * ({@code biome.dross.<name>}). The success line says where you landed and how far that was from the hub (from another
 * dimension) or from where you were (in the Dross).
 * <p>
 * Everything runs on the server thread when the command is used. Finding the biome takes a few milliseconds; landing
 * loads (and, the first time, makes) the chunks it looks at, usually just one or two, which takes a fraction of a second.
 */
public final class GotoBiome
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    /** "Teleported to the nearest part of BIOME at X, Y, Z (N blocks from the hub)": from another dimension. */
    static final String SUCCESS_FROM_HUB = "commands.dross.goto.biome.success_from_hub";
    /** "... (N blocks from where you were)": from inside the Dross. */
    static final String SUCCESS_FROM_YOU = "commands.dross.goto.biome.success_from_you";
    /** "You're already in BIOME, so you weren't moved." (a plain message, not a red error; the command gives 0) */
    static final String ALREADY_THERE = "commands.dross.goto.biome.already_there";
    /** "Found no part of BIOME within 6,400 blocks of the hub" */
    static final String NOT_FOUND_FROM_HUB = "commands.dross.goto.biome.not_found_from_hub";
    /** "Found no part of BIOME within 6,400 blocks of you" */
    static final String NOT_FOUND_FROM_YOU = "commands.dross.goto.biome.not_found_from_you";
    /** "Couldn't find a safe spot in BIOME near X, Z" */
    static final String NO_SAFE_SPOT = "commands.dross.goto.biome.no_safe_spot";
    /**
     * The same, when the land it looked at was (some of it) an old world's flat Dross, which has none of the new biomes:
     * "... (the land there was made before the Dross had biomes; try farther out)".
     */
    static final String NO_SAFE_SPOT_OLD_LAND = "commands.dross.goto.biome.no_safe_spot_old_land";
    /** "The Dross dimension isn't loaded, so there's nowhere to go." */
    static final String NO_DROSS = "commands.dross.goto.biome.no_dross";
    /** "Something stopped you from going to the Dross." (another mod cancelled the change of dimension) */
    static final String STOPPED = "commands.dross.goto.biome.stopped";

    // ---- Tuning ----
    /**
     * When the search starts at the hub, it starts at this height. The Dross's biomes are the same at every height, so any
     * height inside the world works; this one is about the hub's ground. (Minecraft's biome search finds nothing at all if
     * it starts outside the world's height, so a player's own height is kept inside it too.)
     */
    public static final int HUB_SEARCH_Y = 72;
    /** How far round the place the biome search found a landing spot is looked for (blocks, along X and Z). */
    public static final int LANDING_RADIUS = 48;
    /** If there's no landing spot round that place, this many more places are tried, further on into the biome... */
    public static final int FURTHER_TRIES = 3;
    /** ...each this many blocks further than the last (twice {@link #LANDING_RADIUS}, so the areas looked at just touch). */
    public static final int FURTHER_STEP = 2 * LANDING_RADIUS;
    /** Never land within this many blocks (along X and Z) of the hub's footprint: not on the spire, in it, or right by its walls. */
    public static final int HUB_CLEARANCE = 8;
    /**
     * Never land within this many blocks more than the lair's reach from its middle (its reach is how far its entrance
     * is from its middle, about 45 blocks; at least {@link #MIN_LAIR_REACH}): not in its cavern or tunnel, on its dome,
     * or at its mouth.
     */
    public static final int LAIR_CLEARANCE = 8;
    /** The lair's reach is taken to be at least this (blocks from its middle; its cavern's rock lining alone reaches 24). */
    public static final int MIN_LAIR_REACH = 32;
    /**
     * Before the lair is built (the first minute or so of a new world), never land within this many blocks of the spot
     * picked for it, so nobody is standing where it's about to be carved.
     */
    public static final int PLANNED_LAIR_REACH = 56;
    /**
     * The ground you land on may be at most this many blocks above the land's own height there (what the terrain
     * generator makes before trees, rocks and other features are added): low boulders are fine, the top of a huge fungus
     * or an ice spike isn't.
     */
    public static final int MAX_ABOVE_GROUND = 4;
    /**
     * The landing search gives up after looking at this many chunks (each one is loaded, and made if it's new, which takes
     * a moment). Normally it needs one or two.
     */
    public static final int MAX_CHUNKS = 64;
    /**
     * ...or after finding this many chunks of an old world's flat Dross (land made before the new terrain existed, which
     * has none of the new biomes). They're only read from the save, which is quicker, and skipped whole.
     */
    public static final int MAX_OLD_CHUNKS = 256;
    /** The hub's footprint is only looked up when the search comes within this many blocks (along X and Z) of its centre. */
    private static final int HUB_LOOKUP_DISTANCE = 256;
    /** If the hub has no layout (its exit portal is gone), the search keeps this far (along X and Z) from its centre instead. */
    private static final int HUB_FALLBACK_HALF_SIZE = 64;

    /** The columns round a place, nearest first: offsets along X and Z up to {@link #LANDING_RADIUS}. */
    private static final int[] AROUND_X;
    private static final int[] AROUND_Z;

    static
    {
        List<int[]> offsets = new ArrayList<>((2 * LANDING_RADIUS + 1) * (2 * LANDING_RADIUS + 1));
        for (int dx = -LANDING_RADIUS; dx <= LANDING_RADIUS; dx++)
        {
            for (int dz = -LANDING_RADIUS; dz <= LANDING_RADIUS; dz++)
            {
                offsets.add(new int[] {dx, dz});
            }
        }
        // Nearest first; equally near ones in a fixed order, so the same world always gives the same spot.
        offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1]).thenComparingInt(o -> o[1]).thenComparingInt(o -> o[0]));
        AROUND_X = new int[offsets.size()];
        AROUND_Z = new int[offsets.size()];
        for (int i = 0; i < offsets.size(); i++)
        {
            AROUND_X[i] = offsets.get(i)[0];
            AROUND_Z[i] = offsets.get(i)[1];
        }
    }

    private GotoBiome() {}

    /**
     * Runs {@code /dross goto biome <name>} for the player who used it.
     *
     * @return 1 if the player was teleported; 0 if not (the reason is said in chat)
     * @throws CommandSyntaxException if it wasn't used by a player (the server console, a command block)
     */
    static int teleport(CommandSourceStack source, ResourceKey<Biome> biome) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        Component name = biomeName(biome);
        ServerLevel dross = source.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        boolean fromHub = player.level() != dross;
        if (!fromHub && dross.getBiome(player.blockPosition()).is(biome))
        {
            source.sendSuccess(() -> Component.translatable(ALREADY_THERE, name), false);
            return 0;
        }

        BlockPos origin = searchOrigin(dross, fromHub ? DrossHub.CENTER.atY(HUB_SEARCH_Y) : player.blockPosition());
        String who = player.getName().getString();
        String from = fromHub ? "the hub" : "them at " + origin.toShortString();
        long started = Util.getNanos();
        Optional<BlockPos> found = DrossBiomes.findNearest(dross, origin, biome);
        double searchMillis = (Util.getNanos() - started) / 1.0E6D;
        if (found.isEmpty())
        {
            LOGGER.info("[goto biome] {}: no {} within {} blocks of {} ({} ms)", who, DrossBiomes.name(biome), DrossBiomes.SEARCH_RADIUS, from,
                    String.format(Locale.ROOT, "%.0f", searchMillis));
            source.sendFailure(Component.translatable(fromHub ? NOT_FOUND_FROM_HUB : NOT_FOUND_FROM_YOU, name, grouped(DrossBiomes.SEARCH_RADIUS)));
            return 0;
        }

        BlockPos place = found.get();
        started = Util.getNanos();
        Landing landing = new Landing(dross, player, biome, origin.getY());
        Optional<Vec3> spot = landing.find(origin, place);
        double landingMillis = (Util.getNanos() - started) / 1.0E6D;
        if (spot.isEmpty())
        {
            LOGGER.info("[goto biome] {}: the nearest {} from {} is at {}, {} ({} ms), but no safe spot was found there ({}, {} ms)", who,
                    DrossBiomes.name(biome), from, place.getX(), place.getZ(), String.format(Locale.ROOT, "%.0f", searchMillis),
                    landing.describe(), String.format(Locale.ROOT, "%.0f", landingMillis));
            source.sendFailure(Component.translatable(landing.sawOldLand() ? NO_SAFE_SPOT_OLD_LAND : NO_SAFE_SPOT, name, place.getX(), place.getZ()));
            return 0;
        }

        Vec3 at = spot.get();
        float yaw = facingAway(origin, at, player.getYRot());
        // Like vanilla's /tp: keeps the landing chunk loaded for a moment, gets the player off a mount or out of bed.
        player.teleportTo(dross, at.x, at.y, at.z, Set.of(), yaw, 0.0F);
        if (player.level() != dross)
        {
            LOGGER.warn("[goto biome] {}: something stopped the teleport to the Dross (another mod cancelled the change of dimension)", who);
            source.sendFailure(Component.translatable(STOPPED));
            return 0;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.resetFallDistance(); // a fall before the teleport doesn't hurt on landing

        BlockPos feet = BlockPos.containing(at);
        int distance = (int) Math.round(horizontalDistance(origin, at));
        LOGGER.info("[goto biome] {}: the nearest {} from {} is at {}, {} (found in {} ms); landed at {}, {} blocks away, facing {} ({}, {} ms)",
                who, DrossBiomes.name(biome), from, place.getX(), place.getZ(), String.format(Locale.ROOT, "%.0f", searchMillis), feet.toShortString(),
                distance, Direction.fromYRot(yaw).getName(), landing.describe(), String.format(Locale.ROOT, "%.0f", landingMillis));
        source.sendSuccess(() -> Component.translatable(fromHub ? SUCCESS_FROM_HUB : SUCCESS_FROM_YOU, name, feet.getX(), feet.getY(), feet.getZ(),
                grouped(distance)), true);
        return 1;
    }

    /** The biome's name as players read it (its {@code biome.dross.<name>} lang key). */
    static Component biomeName(ResourceKey<Biome> biome)
    {
        return Component.translatable(Util.makeDescriptionId("biome", biome.location()));
    }

    /**
     * Where the biome search starts: {@code pos}, kept inside the world's height (Minecraft's biome search finds nothing if
     * it starts above or below the world, for example a player flying very high).
     */
    static BlockPos searchOrigin(ServerLevel dross, BlockPos pos)
    {
        return pos.atY(Mth.clamp(pos.getY(), dross.getMinBuildHeight() + 1, dross.getMaxBuildHeight() - 1));
    }

    /** The way that looks from {@code origin} towards {@code spot} and on past it (Minecraft yaw: 0 south, 90 west). */
    private static float facingAway(BlockPos origin, Vec3 spot, float otherwise)
    {
        double dx = spot.x - (origin.getX() + 0.5D);
        double dz = spot.z - (origin.getZ() + 0.5D);
        if (dx * dx + dz * dz < 1.0D)
        {
            return otherwise;
        }
        return Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
    }

    /** The distance along X and Z from the middle of {@code origin}'s block to {@code spot}. */
    static double horizontalDistance(BlockPos origin, Vec3 spot)
    {
        double dx = spot.x - (origin.getX() + 0.5D);
        double dz = spot.z - (origin.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A number with thousands separators: 6,400. */
    private static String grouped(int number)
    {
        return String.format(Locale.ROOT, "%,d", number);
    }

    /** A circle (along X and Z) a landing keeps clear of: its middle and radius. */
    record LairZone(double x, double z, double radius)
    {
        /** True if the middle of the column at (blockX, blockZ) is inside the circle. */
        boolean contains(int blockX, int blockZ)
        {
            double dx = blockX + 0.5D - this.x;
            double dz = blockZ + 0.5D - this.z;
            return dx * dx + dz * dz <= this.radius * this.radius;
        }
    }

    /**
     * The circle a biome teleport keeps clear of round the Cinder Colossus's lair: round its middle, {@value #LAIR_CLEARANCE}
     * blocks wider than the distance to its entrance (taken as at least {@value #MIN_LAIR_REACH}), so it covers the
     * cavern, the dome over it, the tunnel and its mouth. Before the lair is built: {@value #PLANNED_LAIR_REACH} blocks
     * round the spot picked for it. Empty if no spot has been picked yet. Server thread.
     */
    static Optional<LairZone> lairZone(MinecraftServer server)
    {
        Optional<ColossusLair.Lair> lair = ColossusLair.get(server);
        if (lair.isPresent())
        {
            BlockPos center = lair.get().center();
            double x = center.getX() + 0.5D;
            double z = center.getZ() + 0.5D;
            Vec3 entrance = lair.get().entrance();
            double reach = Math.max(MIN_LAIR_REACH, Math.sqrt((entrance.x - x) * (entrance.x - x) + (entrance.z - z) * (entrance.z - z)));
            return Optional.of(new LairZone(x, z, reach + LAIR_CLEARANCE));
        }
        return ColossusLair.plannedSite(server).map(site -> new LairZone(site.getX() + 0.5D, site.getZ() + 0.5D, PLANNED_LAIR_REACH));
    }

    /**
     * One search for a landing spot (see the class comment): the columns it has tried, the chunks it has looked at, the
     * biome noise it has read (one read per 4x4 columns, the size of a biome cell), and why columns were turned down (for
     * the log).
     * <p>
     * Old worlds: land made before the new terrain existed stays the old flat Dross, whose only biome is
     * {@link ModDimensions#DROSS_FLATS_BIOME}, even where the noise now says another biome. A chunk like that is skipped
     * whole the first time one of its columns shows it, and doesn't count towards {@link #MAX_CHUNKS} (it was only read
     * from the save), so the search carries on further out to the new land.
     */
    static final class Landing
    {
        private final ServerLevel dross;
        @Nullable
        private final Entity entity;
        private final ResourceKey<Biome> biome;
        /** The height the biome noise is read at (the search's own; the biomes are the same at every height). */
        private final int sampleY;
        private final LongOpenHashSet tried = new LongOpenHashSet();
        /** The chunks looked at (not counting old flat land). */
        private final LongOpenHashSet chunks = new LongOpenHashSet();
        /** The chunks found to be an old world's flat Dross. */
        private final LongOpenHashSet oldChunks = new LongOpenHashSet();
        private final Long2BooleanOpenHashMap biomeByCell = new Long2BooleanOpenHashMap();
        @Nullable
        private BoundingBox hubZone;
        /** The circle kept clear round the Colossus lair (null: none), worked out the first time it's needed. */
        @Nullable
        private LairZone lairZone;
        private boolean lairZoneKnown;
        private boolean gaveUp;
        private int checked;
        private int unsafe;
        private int otherBiome;
        private int raised;
        private int byTheHub;
        private int byTheLair;

        /**
         * @param entity  who is landing (for their size; null: a standing player)
         * @param sampleY the height the biome noise is read at (inside the world's height)
         */
        Landing(ServerLevel dross, @Nullable Entity entity, ResourceKey<Biome> biome, int sampleY)
        {
            this.dross = dross;
            this.entity = entity;
            this.biome = biome;
            this.sampleY = sampleY;
        }

        /**
         * A landing spot near {@code place} (where the biome search found the biome), searching from {@code origin}: round
         * the place itself first, then further on along the line from the origin through it. Use each Landing once.
         */
        Optional<Vec3> find(BlockPos origin, BlockPos place)
        {
            double dx = place.getX() - origin.getX();
            double dz = place.getZ() - origin.getZ();
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 1.0D)
            {
                // The search started inside the biome (at the hub, or on the edge of the biome): "further on" is then away
                // from the hub's centre, or south (the way the spire's gate faces) when that's where it is.
                dx = place.getX() - DrossHub.CENTER.getX();
                dz = place.getZ() - DrossHub.CENTER.getZ();
                length = Math.sqrt(dx * dx + dz * dz);
                if (length < 1.0D)
                {
                    dx = 0.0D;
                    dz = 1.0D;
                    length = 1.0D;
                }
            }
            for (int i = 0; i <= FURTHER_TRIES && !this.gaveUp; i++)
            {
                int x = place.getX() + (int) Math.round(dx / length * FURTHER_STEP * i);
                int z = place.getZ() + (int) Math.round(dz / length * FURTHER_STEP * i);
                Optional<Vec3> spot = around(x, z);
                if (spot.isPresent())
                {
                    return spot;
                }
            }
            return Optional.empty();
        }

        /** The first good column round (x, z), nearest first, up to {@link #LANDING_RADIUS} blocks away. */
        private Optional<Vec3> around(int centerX, int centerZ)
        {
            for (int i = 0; i < AROUND_X.length; i++)
            {
                int x = centerX + AROUND_X[i];
                int z = centerZ + AROUND_Z[i];
                if (!this.tried.add(ChunkPos.asLong(x, z))) // just a way to pack the two numbers into one key
                {
                    continue; // already tried round an earlier place
                }
                if (isByTheHub(x, z))
                {
                    this.byTheHub++;
                    continue;
                }
                if (isByTheLair(x, z))
                {
                    this.byTheLair++;
                    continue;
                }
                if (!isBiomeHere(x, z))
                {
                    continue; // the biome noise says it's another biome: free to check, nothing loaded
                }
                int chunkX = SectionPos.blockToSectionCoord(x);
                int chunkZ = SectionPos.blockToSectionCoord(z);
                long chunk = ChunkPos.asLong(chunkX, chunkZ);
                if (this.oldChunks.contains(chunk))
                {
                    continue; // an old world's flat land, seen already
                }
                if (!this.chunks.contains(chunk))
                {
                    if (this.chunks.size() >= MAX_CHUNKS)
                    {
                        this.gaveUp = true;
                        return Optional.empty();
                    }
                    this.chunks.add(chunk);
                    // Loads it (makes it, if it's new). An old world's flat land is skipped whole, and isn't counted.
                    if (this.dross.getChunk(chunkX, chunkZ).getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(this.sampleY),
                            QuartPos.fromBlock(z)).is(ModDimensions.DROSS_FLATS_BIOME))
                    {
                        this.chunks.remove(chunk);
                        this.oldChunks.add(chunk);
                        if (this.oldChunks.size() >= MAX_OLD_CHUNKS)
                        {
                            this.gaveUp = true;
                            return Optional.empty();
                        }
                        continue;
                    }
                }
                this.checked++;
                Optional<Vec3> spot = DrossTeleporter.findSafeSpot(this.dross, this.entity, x, z, 0);
                if (spot.isEmpty())
                {
                    this.unsafe++;
                    continue;
                }
                // The biome a player standing there sees (Minecraft blurs biome borders by a block or two, and an old world's
                // flat land keeps its old biome).
                if (!this.dross.getBiome(BlockPos.containing(spot.get())).is(this.biome))
                {
                    this.otherBiome++;
                    continue;
                }
                if (spot.get().y > naturalGround(x, z) + MAX_ABOVE_GROUND)
                {
                    this.raised++;
                    continue;
                }
                return spot;
            }
            return Optional.empty();
        }

        /** What the biome noise says at this column (read once per 4x4 columns). */
        private boolean isBiomeHere(int x, int z)
        {
            long cell = ChunkPos.asLong(QuartPos.fromBlock(x), QuartPos.fromBlock(z));
            if (!this.biomeByCell.containsKey(cell))
            {
                this.biomeByCell.put(cell, DrossBiomes.isBiome(this.dross, new BlockPos(x, this.sampleY, z), this.biome));
            }
            return this.biomeByCell.get(cell);
        }

        /** True if (x, z) is on the hub's footprint or within {@link #HUB_CLEARANCE} blocks of it. */
        private boolean isByTheHub(int x, int z)
        {
            if (Math.abs(x - DrossHub.CENTER.getX()) > HUB_LOOKUP_DISTANCE || Math.abs(z - DrossHub.CENTER.getZ()) > HUB_LOOKUP_DISTANCE)
            {
                return false;
            }
            if (this.hubZone == null)
            {
                BoundingBox footprint = DrossHub.layout(this.dross.getServer()).map(DrossHub.Layout::footprint).orElseGet(() -> new BoundingBox(
                        DrossHub.CENTER.getX() - HUB_FALLBACK_HALF_SIZE, this.dross.getMinBuildHeight(), DrossHub.CENTER.getZ() - HUB_FALLBACK_HALF_SIZE,
                        DrossHub.CENTER.getX() + HUB_FALLBACK_HALF_SIZE, this.dross.getMaxBuildHeight(), DrossHub.CENTER.getZ() + HUB_FALLBACK_HALF_SIZE));
                this.hubZone = footprint.inflatedBy(HUB_CLEARANCE);
            }
            return x >= this.hubZone.minX() && x <= this.hubZone.maxX() && z >= this.hubZone.minZ() && z <= this.hubZone.maxZ();
        }

        /** True if (x, z) is in the circle kept clear round the Colossus lair ({@link GotoBiome#lairZone}). */
        private boolean isByTheLair(int x, int z)
        {
            if (!this.lairZoneKnown)
            {
                this.lairZone = lairZone(this.dross.getServer()).orElse(null);
                this.lairZoneKnown = true;
            }
            return this.lairZone != null && this.lairZone.contains(x, z);
        }

        /** The land's own height at (x, z): the first free block above the terrain generator's ground, before any features. */
        private int naturalGround(int x, int z)
        {
            return this.dross.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.dross,
                    this.dross.getChunkSource().randomState());
        }

        /** True if some of the land it looked at was an old world's flat Dross. */
        boolean sawOldLand()
        {
            return !this.oldChunks.isEmpty();
        }

        /** For the log: how much it looked at, and why columns were turned down. */
        String describe()
        {
            return this.checked + " column(s) checked in " + this.chunks.size() + " chunk(s)" + (this.gaveUp ? " (gave up: chunk limit)" : "")
                    + "; turned down: " + this.unsafe + " unsafe, " + this.otherBiome + " another biome where you'd stand (a border), "
                    + this.raised + " on top of something, " + this.byTheHub + " by the hub, " + this.byTheLair + " by the Colossus lair"
                    + (this.oldChunks.isEmpty() ? "" : "; skipped " + this.oldChunks.size() + " chunk(s) of this old world's flat Dross");
        }
    }
}
