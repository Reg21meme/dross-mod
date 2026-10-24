package com.reg21meme.dross.boss.huntsman.village;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;

/**
 * Where each part of a hunter village stands on the world's own land (the user, October 23, 2026: "I don't like the way
 * you just destroy the biome around it to make it flattened out ... it should be like an actual village, the same way
 * the houses spawn in the village; if there are hills or trees that don't interfere with the spawning of the village
 * then that's fine"; their pick: each courtyard on its own ground). Made with each village's templates by
 * tools/huntsman/villages.py (warren._zones), as {@code data/dross/hunter_village_zones/<id>.json}, read once.
 * <ul>
 *   <li>Every column of a village's templates belongs to a zone, or to none: the world's own land, left as it is. Two
 *       maps: one for the blocks at the ground and under it ({@link Layout#zone} with {@code upper} false), one for those
 *       over it (a lamp's arm over a lane rides with its lamp while the lane under it slopes on its own).</li>
 *   <li>{@link Kind#FLAT}: one level: from the land under it (the median of its ground) for a courtyard with its igloos,
 *       the lodge with its yard, a pen, the shrine, a Rimeberry Patch, a light of the ring, a decoration on the open
 *       ground; or a lane's level where it stands ({@link Zone#follow}: a lane igloo, a lamp on a lane's bank, a
 *       decoration in a lane).</li>
 *   <li>{@link Kind#LANE}: a lane, or a passage between two zones: its floor slopes from the level of the zone at one
 *       end to the other's, a block at a time (the village's piece puts a stair at each step). The courtyards are kept
 *       within what their lanes can climb: a block every {@link #LANE_RUN} blocks of a lane, a block a block of a
 *       passage.</li>
 *   <li>{@link Kind#COLUMN}: each column on its own ground (the paths over the snow, the shrine's lantern posts).</li>
 * </ul>
 * The levels are worked out from the noise when the village is planned ({@link #solve}, the Dimension area's
 * {@code RimefallVillages}), so every chunk of it agrees on them.
 */
public final class HunterVillageZones
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A lane climbs a block in at least this many blocks of its length; a passage between two zones a block a block. */
    public static final double LANE_RUN = 3.0D;
    public static final double PASSAGE_RUN = 1.0D;
    /** A zone's level is the median of its ground, sampled this far apart (blocks, in the template's own columns). */
    private static final int SAMPLE_STEP = 3;
    /** Two zones' levels too far apart for the lane between them are pulled together this many rounds at most. */
    private static final int RELAX_ROUNDS = 40;
    /** A level not worked out (a lane's, a column's). */
    public static final int NONE = Integer.MIN_VALUE;

    public enum Kind
    {
        FLAT, LANE, COLUMN
    }

    /** One zone, as its file has it (-1 where it has none). */
    public static final class Zone
    {
        public final int index;
        public final Kind kind;
        /** FLAT: which courtyard it is, or -1. */
        public final int court;
        /** FLAT: the lane zone it follows (its level where it stands, {@link #atX}, {@link #atZ}), or -1. */
        public final int follow;
        public final double atX;
        public final double atZ;
        /** FLAT: the decoration it's the ground of (its box), or -1. */
        public final int decor;
        /** LANE: the zones at its two ends. */
        public final int a;
        public final int b;
        /** LANE: its line (the template's own columns), and from where to where along it its floor slopes. */
        public final double fromX;
        public final double fromZ;
        public final double toX;
        public final double toZ;
        public final double start;
        public final double end;
        /** LANE: the template layer one stands in on its floor. */
        public final int floor;
        public final boolean passage;
        /** The template's columns its level is the median of ({x, z, x, z ...}), and its deviations are checked at. */
        int[] samples = new int[0];
        /** How many of the template's columns are its ground (a lamp's few, a courtyard's hundreds). */
        int area;

        private Zone(int index, JsonObject o)
        {
            this.index = index;
            this.kind = Kind.valueOf(o.get("kind").getAsString().toUpperCase());
            this.court = intOf(o, "court");
            this.follow = o.has("follow") ? o.get("follow").getAsInt() - 1 : -1;
            JsonArray at = o.has("at") ? o.getAsJsonArray("at") : null;
            this.atX = at != null ? at.get(0).getAsDouble() : 0.0D;
            this.atZ = at != null ? at.get(1).getAsDouble() : 0.0D;
            this.decor = intOf(o, "decor");
            this.a = o.has("a") ? o.get("a").getAsInt() - 1 : -1;
            this.b = o.has("b") ? o.get("b").getAsInt() - 1 : -1;
            JsonArray from = o.has("frm") ? o.getAsJsonArray("frm") : null;
            JsonArray to = o.has("to") ? o.getAsJsonArray("to") : null;
            this.fromX = from != null ? from.get(0).getAsDouble() : 0.0D;
            this.fromZ = from != null ? from.get(1).getAsDouble() : 0.0D;
            this.toX = to != null ? to.get(0).getAsDouble() : 0.0D;
            this.toZ = to != null ? to.get(1).getAsDouble() : 0.0D;
            this.start = o.has("start") ? o.get("start").getAsDouble() : 0.0D;
            this.end = o.has("end") ? o.get("end").getAsDouble() : 0.0D;
            this.floor = intOf(o, "floor");
            this.passage = o.has("passage") && o.get("passage").getAsBoolean();
        }

        private static int intOf(JsonObject o, String key)
        {
            return o.has(key) ? o.get(key).getAsInt() : -1;
        }

        /** FLAT with its own level (from its ground), not a lane's. */
        public boolean own()
        {
            return this.kind == Kind.FLAT && this.follow < 0;
        }

        /** LANE: how many blocks its floor may climb from one end to the other. */
        public int maxRise()
        {
            return (int) Math.floor(Math.max(0.0D, this.end - this.start) / (this.passage ? PASSAGE_RUN : LANE_RUN));
        }
    }

    /** A village's zones. */
    public static final class Layout
    {
        public final String id;
        public final int width;
        public final int depth;
        public final int groundLayer;
        final Zone[] zones;
        private final short[] lower;
        private final short[] upper;
        /** Each decoration's zone (in {@link HunterVillageDesigns.Design#decorations()}'s order). */
        private final int[] decor;
        /** Each column's decoration (its index + 1; 0 none), from their boxes. */
        private final short[] decorAt;
        public final int wayIn;
        public final int home;
        /** The columns kept clear of the world's trees and boulders (a zone or a decoration this near), made when first
         * asked for. */
        private volatile boolean[] clear;
        private volatile int clearReach = -1;

        private Layout(String id, JsonObject o, List<BoundingBox> decorations)
        {
            this.id = id;
            this.width = o.get("width").getAsInt();
            this.depth = o.get("depth").getAsInt();
            this.groundLayer = o.get("ground_layer").getAsInt();
            JsonArray zs = o.getAsJsonArray("zones");
            this.zones = new Zone[zs.size()];
            for (int i = 0; i < zs.size(); i++)
            {
                this.zones[i] = new Zone(i, zs.get(i).getAsJsonObject());
            }
            this.lower = map(o.getAsJsonArray("lower"), this.width, this.depth);
            for (short z : this.lower)
            {
                if (z > 0 && z <= this.zones.length)
                {
                    this.zones[z - 1].area++;
                }
            }
            this.upper = map(o.getAsJsonArray("upper"), this.width, this.depth);
            JsonArray dec = o.getAsJsonArray("decor");
            this.decor = new int[dec.size()];
            for (int i = 0; i < dec.size(); i++)
            {
                this.decor[i] = dec.get(i).getAsInt() - 1;
            }
            this.wayIn = o.get("way_in").getAsInt() - 1;
            this.home = o.get("home").getAsInt() - 1;
            this.decorAt = new short[this.width * this.depth];
            for (int i = 0; i < decorations.size(); i++)
            {
                BoundingBox box = decorations.get(i);
                for (int x = Math.max(0, box.minX()); x <= Math.min(this.width - 1, box.maxX()); x++)
                {
                    for (int z = Math.max(0, box.minZ()); z <= Math.min(this.depth - 1, box.maxZ()); z++)
                    {
                        if (this.decorAt[x * this.depth + z] == 0)
                        {
                            this.decorAt[x * this.depth + z] = (short) (i + 1);
                        }
                    }
                }
            }
            this.sample(decorations);
        }

        private static short[] map(JsonArray rows, int width, int depth)
        {
            short[] out = new short[width * depth];
            for (int x = 0; x < width && x < rows.size(); x++)
            {
                int z = 0;
                for (String run : rows.get(x).getAsString().split(" "))
                {
                    int star = run.indexOf('*');
                    short v = Short.parseShort(run.substring(0, star));
                    int n = Integer.parseInt(run.substring(star + 1));
                    for (int k = 0; k < n && z < depth; k++, z++)
                    {
                        out[x * depth + z] = v;
                    }
                }
            }
            return out;
        }

        /** Each zone's sampled columns: on a grid of {@link #SAMPLE_STEP} (at least one: a small light's own); a
         * decoration's ground at its box's corners and middle. */
        private void sample(List<BoundingBox> decorations)
        {
            List<List<Integer>> pts = new ArrayList<>();
            int[] any = new int[this.zones.length * 2];
            Arrays.fill(any, -1);
            for (int i = 0; i < this.zones.length; i++)
            {
                pts.add(new ArrayList<>());
            }
            for (int x = 0; x < this.width; x++)
            {
                for (int z = 0; z < this.depth; z++)
                {
                    int zone = this.lower[x * this.depth + z] - 1;
                    if (zone < 0)
                    {
                        continue;
                    }
                    if (any[zone * 2] < 0)
                    {
                        any[zone * 2] = x;
                        any[zone * 2 + 1] = z;
                    }
                    if (x % SAMPLE_STEP == 0 && z % SAMPLE_STEP == 0)
                    {
                        pts.get(zone).add(x);
                        pts.get(zone).add(z);
                    }
                }
            }
            for (Zone zone : this.zones)
            {
                List<Integer> p = pts.get(zone.index);
                if (zone.decor >= 0 && zone.decor < decorations.size())
                {
                    BoundingBox b = decorations.get(zone.decor);
                    p.clear();
                    int[][] at = {{b.minX(), b.minZ()}, {b.maxX(), b.minZ()}, {b.minX(), b.maxZ()}, {b.maxX(), b.maxZ()},
                            {(b.minX() + b.maxX()) / 2, (b.minZ() + b.maxZ()) / 2}};
                    for (int[] c : at)
                    {
                        p.add(c[0]);
                        p.add(c[1]);
                    }
                }
                else if (p.isEmpty() && any[zone.index * 2] >= 0)
                {
                    p.add(any[zone.index * 2]);
                    p.add(any[zone.index * 2 + 1]);
                }
                zone.samples = p.stream().mapToInt(Integer::intValue).toArray();
            }
        }

        public int zones()
        {
            return this.zones.length;
        }

        public Zone get(int zone)
        {
            return this.zones[zone];
        }

        /** The zone of a column's blocks (the template's own column; {@code upper}: over the ground), or -1. */
        public int zone(boolean upper, int x, int z)
        {
            if (x < 0 || z < 0 || x >= this.width || z >= this.depth)
            {
                return -1;
            }
            return (upper ? this.upper : this.lower)[x * this.depth + z] - 1;
        }

        /** The decoration whose box covers the column (its index), or -1. */
        public int decorationAt(int x, int z)
        {
            if (x < 0 || z < 0 || x >= this.width || z >= this.depth)
            {
                return -1;
            }
            return this.decorAt[x * this.depth + z] - 1;
        }

        /** A decoration's zone. */
        public int decorZone(int decoration)
        {
            return decoration >= 0 && decoration < this.decor.length ? this.decor[decoration] : -1;
        }

        /** The level a column's blocks stand at (its zone's; a lane's where it is), or {@link #NONE} (no zone, or a
         * column on its own ground). */
        public int level(int[] levels, boolean upper, int x, int z)
        {
            int zone = this.zone(upper, x, z);
            return zone < 0 ? NONE : this.level(levels, zone, x, z);
        }

        /** Zone {@code zone}'s level at a column (a FLAT zone's own, a lane's where the column is), or {@link #NONE}. */
        public int level(int[] levels, int zone, double x, double z)
        {
            Zone zn = this.zones[zone];
            return switch (zn.kind)
            {
                case FLAT -> levels[zone];
                case LANE -> this.laneLevel(levels, zn, x, z);
                case COLUMN -> NONE;
            };
        }

        /** A lane's level at a column: from its first end's zone's to its last's, a block at a time along it. */
        int laneLevel(int[] levels, Zone lane, double x, double z)
        {
            int la = lane.a >= 0 ? levels[lane.a] : NONE;
            int lb = lane.b >= 0 ? levels[lane.b] : NONE;
            if (la == NONE || lb == NONE)
            {
                return la == NONE ? lb : la;
            }
            double dx = lane.toX - lane.fromX;
            double dz = lane.toZ - lane.fromZ;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-6D || lane.end <= lane.start)
            {
                return (int) Math.round((la + lb) / 2.0D);
            }
            double s = ((x - lane.fromX) * dx + (z - lane.fromZ) * dz) / len;
            double t = Math.max(0.0D, Math.min(1.0D, (s - lane.start) / (lane.end - lane.start)));
            return (int) Math.round(la + (lb - la) * t);
        }

        /** True if the column is in a zone or a decoration's box, or this near one (blocks): the world's trees and
         * boulders stay off it. */
        public boolean keptClear(int x, int z, int reach)
        {
            if (x < -reach || z < -reach || x >= this.width + reach || z >= this.depth + reach)
            {
                return false;
            }
            boolean[] c = this.clear;
            if (c == null || this.clearReach != reach)
            {
                c = this.buildClear(reach);
            }
            return c[(x + reach) * (this.depth + 2 * reach) + (z + reach)];
        }

        private synchronized boolean[] buildClear(int reach)
        {
            if (this.clear != null && this.clearReach == reach)
            {
                return this.clear;
            }
            int w = this.width + 2 * reach;
            int d = this.depth + 2 * reach;
            boolean[] used = new boolean[w * d];
            for (int x = 0; x < this.width; x++)
            {
                for (int z = 0; z < this.depth; z++)
                {
                    int i = x * this.depth + z;
                    if (this.lower[i] != 0 || this.upper[i] != 0 || this.decorAt[i] != 0)
                    {
                        used[(x + reach) * d + (z + reach)] = true;
                    }
                }
            }
            // (grown by `reach` each way: along x, then along z)
            boolean[] alongX = new boolean[w * d];
            for (int z = 0; z < d; z++)
            {
                int last = -100000;
                for (int x = 0; x < w; x++)
                {
                    if (used[x * d + z])
                    {
                        last = x;
                    }
                    alongX[x * d + z] = x - last <= reach;
                }
                last = 100000;
                for (int x = w - 1; x >= 0; x--)
                {
                    if (used[x * d + z])
                    {
                        last = x;
                    }
                    alongX[x * d + z] |= last - x <= reach;
                }
            }
            boolean[] out = new boolean[w * d];
            for (int x = 0; x < w; x++)
            {
                int last = -100000;
                for (int z = 0; z < d; z++)
                {
                    if (alongX[x * d + z])
                    {
                        last = z;
                    }
                    out[x * d + z] = z - last <= reach;
                }
                last = 100000;
                for (int z = d - 1; z >= 0; z--)
                {
                    if (alongX[x * d + z])
                    {
                        last = z;
                    }
                    out[x * d + z] |= last - z <= reach;
                }
            }
            this.clearReach = reach;
            this.clear = out;
            return out;
        }
    }

    private static final Map<String, Layout> LAYOUTS = new ConcurrentHashMap<>();

    private HunterVillageZones() {}

    /** The design's zones (read the first time it's asked for), or null (and an error in the log) if its file is
     * missing. */
    @Nullable
    public static Layout of(HunterVillageDesigns.Design design)
    {
        Layout found = LAYOUTS.get(design.id());
        if (found != null)
        {
            return found;
        }
        synchronized (LAYOUTS)
        {
            found = LAYOUTS.get(design.id());
            if (found != null)
            {
                return found;
            }
            String path = "/data/dross/hunter_village_zones/" + design.id() + ".json";
            try (InputStream in = HunterVillageZones.class.getResourceAsStream(path))
            {
                if (in == null)
                {
                    LOGGER.error("Hunter village {}: its zones ({}) are missing", design.id(), path);
                    return null;
                }
                JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                found = new Layout(design.id(), json.getAsJsonObject(), design.decorations());
            }
            catch (Exception e)
            {
                LOGGER.error("Hunter village {}: its zones ({}) couldn't be read", design.id(), path, e);
                return null;
            }
            LAYOUTS.put(design.id(), found);
            return found;
        }
    }

    // ---------------------------------------------------------------- the levels, from the land

    /**
     * Every zone's level for the village with its middle at {@code middle} and its way in facing {@code front}, from
     * the ground's tops ({@code top}, the noise's: the same in every chunk): each FLAT zone of its own at the median of its
     * ground; two zones a lane joins pulled together until it can climb between them; then the zones that follow a lane.
     * Null if it doesn't fit: the ground under a zone (not a decoration's) more than {@code maxStep} from its level, or a
     * lane or passage that can't climb between its ends.
     *
     * @return one level a zone ({@link #NONE} for a lane's or a column's)
     */
    @Nullable
    public static int[] solve(Layout layout, HunterVillageDesigns.Design design, BlockPos middle, Direction front,
                              IntBinaryOperator top, int maxStep)
    {
        int n = layout.zones.length;
        int[] levels = new int[n];
        Arrays.fill(levels, NONE);
        for (Zone zone : layout.zones)
        {
            if (zone.own())
            {
                levels[zone.index] = median(tops(layout, design, middle, front, top, zone));
            }
        }
        for (int round = 0; round < RELAX_ROUNDS; round++)
        {
            boolean moved = false;
            for (Zone lane : layout.zones)
            {
                if (lane.kind != Kind.LANE || lane.a < 0 || lane.b < 0 || !layout.zones[lane.a].own()
                        || !layout.zones[lane.b].own())
                {
                    continue;
                }
                int gap = levels[lane.a] - levels[lane.b];
                int excess = Math.abs(gap) - lane.maxRise();
                if (excess > 0)
                {
                    int high = gap > 0 ? lane.a : lane.b;
                    int low = gap > 0 ? lane.b : lane.a;
                    levels[high] -= (excess + 1) / 2;
                    levels[low] += excess / 2;
                    moved = true;
                }
            }
            if (!moved)
            {
                break;
            }
        }
        // (a zone that follows a lane: once both of the lane's ends have their levels)
        for (int round = 0; round < n; round++)
        {
            boolean set = false;
            for (Zone zone : layout.zones)
            {
                if (zone.kind != Kind.FLAT || zone.follow < 0 || levels[zone.index] != NONE)
                {
                    continue;
                }
                Zone lane = layout.zones[zone.follow];
                if (lane.a >= 0 && lane.b >= 0 && levels[lane.a] != NONE && levels[lane.b] != NONE)
                {
                    levels[zone.index] = layout.laneLevel(levels, lane, zone.atX, zone.atZ);
                    set = true;
                }
            }
            if (!set)
            {
                break;
            }
        }
        for (Zone zone : layout.zones)
        {
            if (zone.kind == Kind.FLAT && levels[zone.index] == NONE)
            {
                return null;                    // (a follow chain that never closed: a broken file)
            }
            if (zone.kind == Kind.LANE && zone.a >= 0 && zone.b >= 0
                    && Math.abs(levels[zone.a] - levels[zone.b]) > zone.maxRise())
            {
                return null;
            }
        }
        for (Zone zone : layout.zones)
        {
            if (zone.kind == Kind.COLUMN || zone.decor >= 0)
            {
                continue;
            }
            for (int i = 0; i + 1 < zone.samples.length; i += 2)
            {
                int lx = zone.samples[i];
                int lz = zone.samples[i + 1];
                BlockPos w = HunterVillages.toWorld(new BlockPos(lx, 0, lz), middle, front, design);
                int want = layout.level(levels, zone.index, lx, lz);
                if (want != NONE && Math.abs(top.applyAsInt(w.getX(), w.getZ()) - want) > maxStep)
                {
                    return null;
                }
            }
        }
        return levels;
    }

    private static int[] tops(Layout layout, HunterVillageDesigns.Design design, BlockPos middle, Direction front,
                              IntBinaryOperator top, Zone zone)
    {
        int[] out = new int[zone.samples.length / 2];
        for (int i = 0; i < out.length; i++)
        {
            BlockPos w = HunterVillages.toWorld(new BlockPos(zone.samples[2 * i], 0, zone.samples[2 * i + 1]), middle,
                    front, design);
            out[i] = top.applyAsInt(w.getX(), w.getZ());
        }
        return out;
    }

    private static int median(int[] values)
    {
        if (values.length == 0)
        {
            return NONE;
        }
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
