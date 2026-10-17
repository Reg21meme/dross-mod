package com.reg21meme.dross.dimension.sewer;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.sites.SiteTerrain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.slf4j.Logger;

/**
 * Lays out one sewer network round a chunk, from the terrain's noise alone (no chunks are generated): where its
 * junctions, tunnels, entrances and rat cave go, and how deep it lies. Every number is in {@link SewerTuning} (shape) or
 * the structure's JSON (depth; {@code tools/terrain/worldgen.py}).
 * <p>
 * It's run twice for every network, with the same answer: first by the boss-site planner ({@link SewerSites}: does a
 * network fit in this patch, and where are its entrances?), then by the structure's own generation at the chunk the
 * planner picked ({@link SewerStructure}). Both read the same noise and seed their randomness from the world seed and
 * the chunk alone, so the second run lays out exactly the network the first one checked.
 * <ol>
 *   <li>A grid of possible junctions round the middle, {@value SewerTuning#CELL} blocks apart; only those standing deep
 *       inside Blightwood (with {@link SewerTuning#BIOME_MARGIN} to spare) can be used, so the maze takes the patch's
 *       shape and shrinks to fit a smaller patch ({@link SewerTuning#MAZE_FILL}, down to
 *       {@link SewerTuning#NODES_SMALLEST} junctions).</li>
 *   <li>The Rat King's network puts his rat cave near the middle, where the ground is highest (the most rock over its
 *       dome); its doors are the junctions next to it.</li>
 *   <li>The floor height: deep enough that every tunnel has the structure's {@code min_cover} of rock over it (so it lies
 *       in the sewers biome, whose water is green). Junctions with too little rock over them are left out.</li>
 *   <li>The maze grows out from the cave's doors (or the middle hall) one junction at a time, now carrying on from the
 *       newest (long winding corridors), now branching off an older one (side branches, dead ends): a tree, so every
 *       junction leads to the cave.</li>
 *   <li>Entrances: a regular network gets {@value SewerTuning#PIPES_MIN} pipes far out along the tree (dead ends first),
 *       well apart, on fairly level ground; the Rat King's gets one grand entrance (a crowned pipe) about
 *       {@value SewerTuning#ROYAL_ROAD_STEPS} junctions from his cave.</li>
 *   <li>The tunnels from each entrance back to the cave are main tunnels: a water channel in a regular network, the
 *       royal road (a red carpet, banners, candles, bones and gold) in the Rat King's. Then a few extra tunnels make
 *       loops; junctions become rooms, rough caves or plain crossings.</li>
 * </ol>
 * If it can't fit, it says why and makes no pieces.
 */
final class SewerPlanner
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The tallest junction there can be (the hall, or the tallest rough cave): the floor is put deep enough for it. */
    private static final int TALLEST_JUNCTION = Math.max(SewerTuning.HALL_HEIGHT, SewerTuning.CAVE_SECTION_HEIGHT_MAX);
    /** Mixed into the world seed before the network's own randomness is seeded from it and the chunk. */
    private static final long PLAN_SALT = 0x5E3E2F1A9B7C4D11L;

    private final SiteTerrain terrain;
    private final SewerStructure structure;
    private final boolean ratCave;
    private final boolean relaxed;
    private final int margin;
    private final int smallest;
    private final RandomSource random;
    private final int middleX;
    private final int middleZ;
    private final ChunkPos chunk;
    private final int size = 2 * SewerTuning.GRID_RADIUS + 1;
    private final Node[] grid = new Node[this.size * this.size];
    private final List<Edge> edges = new ArrayList<>();
    private final Map<Long, Edge> edgeAt = new HashMap<>();
    private long seed;

    /** The result: the pieces and the layout, or why it didn't fit. */
    record Plan(@Nullable List<StructurePiece> pieces, @Nullable SewerSites.Layout layout, @Nullable String failure)
    {
        boolean ok()
        {
            return this.failure == null;
        }
    }

    /**
     * Lays out the network {@code structure} (dross:rat_cave or dross:sewers) round {@code chunk}.
     *
     * @param relaxed the Rat King's sewer's smallest form (looser rules: {@link SewerTuning#RELAXED_BIOME_MARGIN} and the
     *                rest); only for a group where no patch has room for the normal one
     */
    static Plan plan(SiteTerrain terrain, SewerStructure structure, ChunkPos chunk, boolean relaxed)
    {
        try
        {
            return new SewerPlanner(terrain, structure, chunk, relaxed).tryPlan();
        }
        catch (RuntimeException e)
        {
            LOGGER.error("[sewers] laying out a network at {} failed", chunk, e);
            return new Plan(null, null, "failed: " + e);
        }
    }

    private SewerPlanner(SiteTerrain terrain, SewerStructure structure, ChunkPos chunk, boolean relaxed)
    {
        this.terrain = terrain;
        this.structure = structure;
        this.ratCave = structure.hasRatCave();
        this.relaxed = relaxed && this.ratCave;
        this.margin = this.relaxed ? SewerTuning.RELAXED_BIOME_MARGIN : SewerTuning.BIOME_MARGIN;
        this.smallest = this.relaxed ? SewerTuning.RELAXED_NODES_SMALLEST : SewerTuning.NODES_SMALLEST;
        this.chunk = chunk;
        this.middleX = chunk.getMiddleBlockX();
        this.middleZ = chunk.getMiddleBlockZ();
        long salt = (this.ratCave ? 2L : 0L) + (this.relaxed ? 1L : 0L);
        this.random = RandomSource.create(SewerNoise.hash(terrain.seed() ^ PLAN_SALT, chunk.x, (int) salt, chunk.z));
    }

    /** A possible junction. */
    private static final class Node
    {
        final int i;
        final int j;
        final int x;
        final int z;
        boolean blightwood;
        /** The lowest ground round it (the terrain's height at its middle and corners). */
        double lowGround;
        boolean inCave;
        /** Blocks of air it may have over its floor (with min_cover of rock over that). */
        int headroom;
        boolean usable;
        boolean used;
        @Nullable Node parent;
        int steps;
        boolean root;
        boolean pipe;
        /** For a rat cave door: the side the cave is on, and whether its tunnel into the cave is a main one. */
        int caveSide = -1;
        boolean mainDoor;
        int links;
        int mainLinks;
        SewerNodePiece.Kind kind = SewerNodePiece.Kind.CROSSING;
        int half;
        int height;

        Node(int i, int j, int x, int z)
        {
            this.i = i;
            this.j = j;
            this.x = x;
            this.z = z;
        }
    }

    private static final class Edge
    {
        final Node a;
        final Node b;
        boolean main;

        Edge(Node a, Node b)
        {
            this.a = a;
            this.b = b;
        }
    }

    private record Cave(int x, int z, double lowGround) {}

    private record Pipe(Node node, int groundY, Direction steps) {}

    // ---------------------------------------------------------------------------------------------

    private Plan fail(String why)
    {
        return new Plan(null, null, why);
    }

    private Plan tryPlan()
    {
        this.seed = this.random.nextLong();
        survey();
        Cave cave = null;
        if (this.ratCave)
        {
            cave = placeCave();
            if (cave == null)
            {
                return fail("no room for the rat cave");
            }
        }
        Integer floor = chooseFloor(cave);
        if (floor == null)
        {
            return fail("too little Blightwood or rock over it");
        }
        for (Node n : this.grid)
        {
            n.headroom = Math.min((int) Math.floor(n.lowGround) - 1 - this.structure.minCover(), this.structure.airMaxY()) - floor;
            n.usable = n.blightwood && !n.inCave && n.headroom >= SewerTuning.MAIN_HEIGHT;
        }
        List<Node> roots = cave != null ? caveDoors(cave) : hall();
        if (roots.isEmpty())
        {
            return fail(cave != null ? "no way into the rat cave" : "no room for the middle hall");
        }
        int junctions = grow(roots);
        if (junctions < this.smallest)
        {
            return fail("only " + junctions + " junctions fit (" + this.smallest + " needed)");
        }
        List<Pipe> pipes = cave != null ? grandEntrance(cave) : choosePipes(roots);
        if (cave != null ? pipes.isEmpty() : pipes.size() < SewerTuning.PIPES_MIN)
        {
            return fail(cave != null ? "no room for the grand entrance" : "only " + pipes.size() + " pipe(s) fit");
        }
        for (Pipe pipe : pipes)
        {
            markMain(pipe.node());
        }
        addLoops();
        chooseKinds();

        RatCavePiece cavePiece = null;
        if (cave != null)
        {
            int[] doorList = doors(cave);
            int royal = -1;
            for (int k = 0; k < doorList.length; k++)
            {
                if (royalDoorKey(cave) == doorList[k])
                {
                    royal = k;
                }
            }
            cavePiece = new RatCavePiece(cave.x(), cave.z(), doorList, royal, floor, this.seed);
            if (!cavePiece.hasArrival())
            {
                return fail("no arrival spot in the rat cave");
            }
        }
        List<StructurePiece> pieces = new ArrayList<>();
        for (Edge e : this.edges)
        {
            pieces.add(tunnel(e, floor));
        }
        for (Node n : this.grid)
        {
            if (n.caveSide >= 0 && cave != null)
            {
                pieces.add(doorTunnel(n, cave, floor));
            }
        }
        for (Node n : this.grid)
        {
            if (n.used)
            {
                boolean royal = this.ratCave && n.mainLinks != 0;
                pieces.add(new SewerNodePiece(n.kind, n.x, n.z, n.half, n.height, n.links, n.mainLinks, royal, floor, this.seed));
            }
        }
        if (cavePiece != null)
        {
            pieces.add(cavePiece);
        }
        List<SewerSites.Entrance> entrances = new ArrayList<>();
        for (Pipe pipe : pipes)
        {
            Node n = pipe.node();
            if (cave != null)
            {
                pieces.add(new SewerGrandPipePiece(n.x, n.z, pipe.groundY(), pipe.steps(), floor, this.seed));
                entrances.add(new SewerSites.Entrance(new BlockPos(n.x, SewerGrandPipePiece.rimTop(pipe.groundY()) + 1, n.z), pipe.groundY(), pipe.steps(),
                        true, SewerTuning.GRAND_CLEARANCE));
            }
            else
            {
                pieces.add(new SewerPipePiece(n.x, n.z, pipe.groundY(), pipe.steps(), floor, this.seed));
                entrances.add(new SewerSites.Entrance(new BlockPos(n.x, SewerPipePiece.rimTop(pipe.groundY()) + 1, n.z), pipe.groundY(), pipe.steps(),
                        false, SewerTuning.PIPE_CLEARANCE));
            }
        }
        long used = Arrays.stream(this.grid).filter(n -> n.used).count();
        SewerSites.Layout layout = new SewerSites.Layout(this.ratCave, this.relaxed, this.chunk, floor, List.copyOf(entrances),
                cave != null ? new BlockPos(cave.x(), floor + 1, cave.z()) : null, (int) used);
        return new Plan(pieces, layout, null);
    }

    // ---------------------------------------------------------------------------------------------
    // The ground and the biome
    // ---------------------------------------------------------------------------------------------

    /** The terrain's height at a column (the first air above the ground, before the little roughness on rocky land). */
    private double groundAt(int x, int z)
    {
        return this.terrain.height(x, z);
    }

    /** The real top block of a column, as the generator makes it (exact, slower). */
    private int topBlock(int x, int z)
    {
        return this.terrain.topBlock(x, z);
    }

    private boolean blightwood(int x, int z)
    {
        return this.terrain.isBiome(DrossBiomes.BLIGHTWOOD, x, z);
    }

    private void survey()
    {
        int r = SewerTuning.GRID_RADIUS;
        int m = this.margin;
        int c = SewerTuning.CELL / 2;
        for (int j = -r; j <= r; j++)
        {
            for (int i = -r; i <= r; i++)
            {
                Node n = new Node(i, j, this.middleX + i * SewerTuning.CELL, this.middleZ + j * SewerTuning.CELL);
                this.grid[(j + r) * this.size + (i + r)] = n;
                n.blightwood = blightwood(n.x, n.z) && blightwood(n.x - m, n.z - m) && blightwood(n.x + m, n.z - m)
                        && blightwood(n.x - m, n.z + m) && blightwood(n.x + m, n.z + m);
                if (!n.blightwood)
                {
                    continue;
                }
                n.lowGround = Math.min(groundAt(n.x, n.z), Math.min(Math.min(groundAt(n.x - c, n.z - c), groundAt(n.x + c, n.z - c)),
                        Math.min(groundAt(n.x - c, n.z + c), groundAt(n.x + c, n.z + c))));
            }
        }
    }

    @Nullable
    private Node node(int i, int j)
    {
        int r = SewerTuning.GRID_RADIUS;
        if (Math.abs(i) > r || Math.abs(j) > r)
        {
            return null;
        }
        return this.grid[(j + r) * this.size + (i + r)];
    }

    @Nullable
    private Node neighbour(Node n, int side)
    {
        Direction d = SewerNodePiece.SIDES[side];
        return node(n.i + d.getStepX(), n.j + d.getStepZ());
    }

    private static int sideFrom(Node a, Node b)
    {
        for (int s = 0; s < 4; s++)
        {
            Direction d = SewerNodePiece.SIDES[s];
            if (b.i - a.i == d.getStepX() && b.j - a.j == d.getStepZ())
            {
                return s;
            }
        }
        throw new IllegalArgumentException("not neighbours");
    }

    // ---------------------------------------------------------------------------------------------
    // The rat cave
    // ---------------------------------------------------------------------------------------------

    /** Where the rat cave goes: near the middle, all of it (and round it) Blightwood, under the highest ground. */
    @Nullable
    private Cave placeCave()
    {
        int radius = SewerTuning.RAT_CAVE_RADIUS;
        Cave best = null;
        for (int cj = -2; cj <= 2; cj++)
        {
            for (int ci = -2; ci <= 2; ci++)
            {
                int x = this.middleX + ci * SewerTuning.CELL;
                int z = this.middleZ + cj * SewerTuning.CELL;
                boolean fits = blightwood(x, z);
                for (int k = 0; k < 16 && fits; k++)
                {
                    double a = k * Math.PI / 8.0D;
                    fits = blightwood(x + (int) Math.round(Math.cos(a) * (radius + this.margin + 4)),
                            z + (int) Math.round(Math.sin(a) * (radius + this.margin + 4)));
                }
                if (!fits)
                {
                    continue;
                }
                double low = Double.MAX_VALUE;
                for (int dx = -radius - 2; dx <= radius + 2; dx += 5)
                {
                    for (int dz = -radius - 2; dz <= radius + 2; dz += 5)
                    {
                        if (dx * dx + dz * dz <= (radius + 3) * (radius + 3))
                        {
                            low = Math.min(low, groundAt(x + dx, z + dz));
                        }
                    }
                }
                if (best == null || low > best.lowGround())
                {
                    best = new Cave(x, z, low);
                }
            }
        }
        if (best != null)
        {
            double reach = (radius + 4.0D) / SewerTuning.CELL;
            for (Node n : this.grid)
            {
                double di = (n.x - best.x()) / (double) SewerTuning.CELL;
                double dj = (n.z - best.z()) / (double) SewerTuning.CELL;
                n.inCave = di * di + dj * dj <= reach * reach;
            }
        }
        return best;
    }

    /** The floor's height, or null if too few junctions fit or it would have to lie below the structure's floor_min_y. */
    @Nullable
    private Integer chooseFloor(@Nullable Cave cave)
    {
        List<Double> lows = new ArrayList<>();
        for (Node n : this.grid)
        {
            if (n.blightwood && !n.inCave)
            {
                lows.add(n.lowGround);
            }
        }
        if (lows.size() < this.smallest)
        {
            return null;
        }
        lows.sort(Comparator.naturalOrder());
        double quarter = lows.get(lows.size() / 4);
        int cover = this.structure.minCover();
        int floor = (int) Math.floor(quarter) - 1 - cover - TALLEST_JUNCTION;
        if (cave != null)
        {
            // the dome's top (with its roughness) and its roof, under min_cover of rock
            floor = Math.min(floor, (int) Math.floor(cave.lowGround()) - 1 - cover - (SewerTuning.RAT_CAVE_HEIGHT + 2));
        }
        floor = Math.min(floor, Math.min(this.structure.floorMaxY(), this.structure.airMaxY() - TALLEST_JUNCTION));
        if (cave != null)
        {
            floor = Math.min(floor, this.structure.airMaxY() - (SewerTuning.RAT_CAVE_HEIGHT + 2));
        }
        if (floor < this.structure.floorMinY())
        {
            if (cave != null)
            {
                return null;
            }
            floor = this.structure.floorMinY();
        }
        return floor;
    }

    /** The rat cave's doors: junctions next to the cave whose tunnel straight towards it runs well inside it. */
    private List<Node> caveDoors(Cave cave)
    {
        List<Node> options = new ArrayList<>();
        for (Node n : this.grid)
        {
            if (!n.usable)
            {
                continue;
            }
            for (int s = 0; s < 4; s++)
            {
                Node next = neighbour(n, s);
                if (next == null || !next.inCave)
                {
                    continue;
                }
                Direction d = SewerNodePiece.SIDES[s];
                // the door's line runs from n towards the cave; how far to the side of the cave's middle is it?
                int along = (cave.x() - n.x) * d.getStepX() + (cave.z() - n.z) * d.getStepZ();
                int across = Math.abs((n.x - cave.x()) * d.getStepZ() + (n.z - cave.z()) * d.getStepX());
                if (along > 0 && across <= SewerTuning.RAT_CAVE_RADIUS - 6)
                {
                    n.caveSide = s;
                    options.add(n);
                    break;
                }
            }
        }
        shuffle(options);
        int want = SewerTuning.RAT_CAVE_DOORS_MIN + this.random.nextInt(SewerTuning.RAT_CAVE_DOORS_MAX - SewerTuning.RAT_CAVE_DOORS_MIN + 1);
        List<Node> doors = new ArrayList<>();
        for (Node n : options)
        {
            boolean apart = true;
            for (Node d : doors)
            {
                if (Math.abs(d.x - n.x) + Math.abs(d.z - n.z) < 2 * SewerTuning.CELL)
                {
                    apart = false;
                }
            }
            if (apart && doors.size() < want)
            {
                doors.add(n);
            }
        }
        for (Node n : options)
        {
            if (!doors.contains(n))
            {
                n.caveSide = -1;
            }
        }
        int fewest = this.relaxed ? SewerTuning.RELAXED_RAT_CAVE_DOORS_MIN : Math.min(2, SewerTuning.RAT_CAVE_DOORS_MIN);
        if (doors.size() < fewest)
        {
            doors.forEach(n -> n.caveSide = -1);
            return List.of();
        }
        for (Node n : doors)
        {
            n.used = true;
            n.root = true;
            n.links |= 1 << n.caveSide;
        }
        return doors;
    }

    /** A network without a rat cave grows from a hall at (or near) its middle. */
    private List<Node> hall()
    {
        Node best = null;
        for (Node n : this.grid)
        {
            if (n.usable && n.headroom >= SewerTuning.HALL_HEIGHT && (best == null || n.i * n.i + n.j * n.j < best.i * best.i + best.j * best.j))
            {
                best = n;
            }
        }
        if (best == null || best.i * best.i + best.j * best.j > 4)
        {
            return List.of();
        }
        best.used = true;
        best.root = true;
        best.kind = SewerNodePiece.Kind.HALL;
        return List.of(best);
    }

    /**
     * The cave's doors as the cave piece wants them: the side of the cave each is on and its line's offset; the doors
     * whose tunnels aren't main ones first (the arrival spot is on the first), the royal road's last.
     */
    private int[] doors(Cave cave)
    {
        List<Integer> side = new ArrayList<>();
        List<Integer> main = new ArrayList<>();
        for (Node n : this.grid)
        {
            if (n.caveSide < 0)
            {
                continue;
            }
            (n.mainDoor ? main : side).add(doorKey(n, cave));
        }
        side.addAll(main);
        return side.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int doorKey(Node n, Cave cave)
    {
        int s = (n.caveSide + 2) % 4;   // seen from the cave, the door is on the other side
        Direction d = SewerNodePiece.SIDES[s];
        int offset = (n.x - cave.x()) * d.getStepZ() + (n.z - cave.z()) * d.getStepX();
        return RatCavePiece.door(s, offset);
    }

    /** The royal road's door (the main one), as the cave piece knows it, or -1. */
    private int royalDoorKey(Cave cave)
    {
        if (!this.ratCave)
        {
            return -1;
        }
        for (Node n : this.grid)
        {
            if (n.caveSide >= 0 && n.mainDoor)
            {
                return doorKey(n, cave);
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------------------------------------
    // The maze
    // ---------------------------------------------------------------------------------------------

    /** Grows the maze; returns how many junctions it has. */
    private int grow(List<Node> roots)
    {
        List<Node> active = new ArrayList<>(roots);
        shuffle(active);
        int usable = roots.size();
        for (Node n : this.grid)
        {
            if (n.usable && !n.used)
            {
                usable++;
            }
        }
        int target = SewerTuning.NODES_MIN + this.random.nextInt(SewerTuning.NODES_MAX - SewerTuning.NODES_MIN + 1);
        target = Math.min(target, Math.max(this.smallest, (int) Math.floor(usable * SewerTuning.MAZE_FILL)));
        int count = roots.size();
        while (count < target && !active.isEmpty())
        {
            int index = this.random.nextDouble() < SewerTuning.GROW_FROM_NEWEST ? active.size() - 1 : this.random.nextInt(active.size());
            Node from = active.get(index);
            List<Node> options = new ArrayList<>();
            for (int s = 0; s < 4; s++)
            {
                Node next = neighbour(from, s);
                if (next != null && next.usable && !next.used)
                {
                    options.add(next);
                }
            }
            if (options.isEmpty())
            {
                active.remove(index);
                continue;
            }
            Node to = options.get(this.random.nextInt(options.size()));
            connect(from, to);
            to.used = true;
            to.parent = from;
            to.steps = from.steps + 1;
            active.add(to);
            count++;
        }
        return count;
    }

    private Edge connect(Node a, Node b)
    {
        Edge e = new Edge(a, b);
        this.edges.add(e);
        this.edgeAt.put(key(a, b), e);
        a.links |= 1 << sideFrom(a, b);
        b.links |= 1 << sideFrom(b, a);
        return e;
    }

    private static long key(Node a, Node b)
    {
        long ka = (long) (a.i + 64) * 128 + (a.j + 64);
        long kb = (long) (b.i + 64) * 128 + (b.j + 64);
        return Math.min(ka, kb) * 100000L + Math.max(ka, kb);
    }

    /** A regular network's pipes. */
    private List<Pipe> choosePipes(List<Node> roots)
    {
        List<Node> options = new ArrayList<>();
        int fromX = roots.get(0).x;
        int fromZ = roots.get(0).z;
        for (Node n : this.grid)
        {
            if (n.used && !n.root && n.steps >= SewerTuning.PIPE_MIN_STEPS && n.headroom >= SewerTuning.POOL_ROOM_HEIGHT
                    && distance(n.x, n.z, fromX, fromZ) >= SewerTuning.PIPE_MIN_SPACING)
            {
                options.add(n);
            }
        }
        shuffle(options);
        // dead ends first (you land at the end of a tunnel), then the furthest along the tunnels
        options.sort(Comparator.comparingInt((Node n) -> Integer.bitCount(n.links) == 1 ? 0 : 1).thenComparingInt(n -> -n.steps));
        int want = SewerTuning.PIPES_MIN + this.random.nextInt(SewerTuning.PIPES_MAX - SewerTuning.PIPES_MIN + 1);
        List<Pipe> pipes = new ArrayList<>();
        for (Node n : options)
        {
            if (pipes.size() >= want)
            {
                break;
            }
            boolean apart = true;
            for (Pipe p : pipes)
            {
                if (distance(p.node().x, p.node().z, n.x, n.z) < SewerTuning.PIPE_MIN_SPACING)
                {
                    apart = false;
                    break;
                }
            }
            if (!apart)
            {
                continue;
            }
            Pipe pipe = pipeAt(n, SewerTuning.PIPE_BODY_RADIUS, SewerTuning.PIPE_RIM_RADIUS, SewerPipePiece.STEPS_REACH + 2,
                    SewerTuning.PIPE_MAX_GROUND_STEP);
            if (pipe != null)
            {
                pipes.add(pipe);
                n.pipe = true;
            }
        }
        return pipes;
    }

    /** The Rat King's network's one grand entrance: about ROYAL_ROAD_STEPS junctions from his cave, a dead end if it can be. */
    private List<Pipe> grandEntrance(Cave cave)
    {
        int minSteps = this.relaxed ? SewerTuning.RELAXED_PIPE_MIN_STEPS : SewerTuning.PIPE_MIN_STEPS;
        int minDistance = this.relaxed ? SewerTuning.RELAXED_PIPE_MIN_DISTANCE_FROM_CAVE : SewerTuning.PIPE_MIN_DISTANCE_FROM_CAVE;
        List<Node> options = new ArrayList<>();
        for (Node n : this.grid)
        {
            if (n.used && !n.root && n.steps >= minSteps && n.headroom >= SewerTuning.GRAND_POOL_ROOM_HEIGHT
                    && distance(n.x, n.z, cave.x(), cave.z()) >= minDistance)
            {
                options.add(n);
            }
        }
        shuffle(options);
        options.sort(Comparator.comparingInt((Node n) -> Integer.bitCount(n.links) == 1 ? 0 : 1)
                .thenComparingInt(n -> Math.abs(n.steps - SewerTuning.ROYAL_ROAD_STEPS)));
        for (Node n : options)
        {
            Pipe pipe = pipeAt(n, SewerTuning.GRAND_BODY_RADIUS, SewerTuning.GRAND_RIM_RADIUS, SewerTuning.GRAND_CLEARANCE - 6,
                    SewerTuning.GRAND_MAX_GROUND_STEP);
            if (pipe != null)
            {
                n.pipe = true;
                return List.of(pipe);
            }
        }
        return List.of();
    }

    /**
     * A pipe at this junction, if the land round it is Blightwood ({@code reach} blocks every way), the ground under its
     * body is level enough and its steps have somewhere to go.
     */
    @Nullable
    private Pipe pipeAt(Node n, double bodyRadius, double rimRadius, int reach, int maxStep)
    {
        for (int k = 0; k < 8; k++)
        {
            double a = k * Math.PI / 4.0D;
            if (!blightwood(n.x + (int) Math.round(Math.cos(a) * reach), n.z + (int) Math.round(Math.sin(a) * reach)))
            {
                return null;
            }
        }
        int highest = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        int body = (int) Math.ceil(bodyRadius);
        double r2 = bodyRadius * bodyRadius;
        for (int dx = -body; dx <= body; dx++)
        {
            for (int dz = -body; dz <= body; dz++)
            {
                if (dx * dx + dz * dz > r2)
                {
                    continue;
                }
                int top = topBlock(n.x + dx, n.z + dz);
                highest = Math.max(highest, top);
                lowest = Math.min(lowest, top);
            }
        }
        if (highest - lowest > maxStep)
        {
            return null;
        }
        // the steps go down the side where the ground is nearest the pipe's foot (and not above it)
        Direction best = null;
        int bestScore = Integer.MAX_VALUE;
        int first = (int) Math.floor(rimRadius) + 2;
        for (Direction d : SewerNodePiece.SIDES)
        {
            int score = 0;
            for (int out = first; out < first + 3; out++)
            {
                int top = topBlock(n.x + d.getStepX() * out, n.z + d.getStepZ() * out);
                score += top > highest ? 3 * (top - highest) : highest - top;
            }
            if (score < bestScore)
            {
                bestScore = score;
                best = d;
            }
        }
        return new Pipe(n, highest, best);
    }

    /** The tunnels from a pipe back along the tree to the cave (or the hall) are main tunnels: a water channel, or the royal road. */
    private void markMain(Node pipe)
    {
        Node n = pipe;
        while (n.parent != null)
        {
            Edge e = this.edgeAt.get(key(n, n.parent));
            if (e != null)
            {
                e.main = true;
            }
            n = n.parent;
        }
        if (n.caveSide >= 0)
        {
            n.mainDoor = true;
        }
    }

    private void addLoops()
    {
        List<Node[]> unused = new ArrayList<>();
        int loops = 0;
        for (Node a : this.grid)
        {
            if (!a.used)
            {
                continue;
            }
            for (int s = 1; s <= 2; s++)   // east and south: each pair once
            {
                Node b = neighbour(a, s);
                if (b == null || !b.used || this.edgeAt.containsKey(key(a, b)))
                {
                    continue;
                }
                if (this.random.nextDouble() < SewerTuning.LOOP_CHANCE)
                {
                    connect(a, b);
                    loops++;
                }
                else
                {
                    unused.add(new Node[] {a, b});
                }
            }
        }
        // a maze has loops: top them up to the least there may be
        shuffle(unused);
        for (int k = 0; k < unused.size() && loops < SewerTuning.MIN_LOOPS; k++)
        {
            connect(unused.get(k)[0], unused.get(k)[1]);
            loops++;
        }
    }

    private void chooseKinds()
    {
        for (Edge e : this.edges)
        {
            if (e.main)
            {
                e.a.mainLinks |= 1 << sideFrom(e.a, e.b);
                e.b.mainLinks |= 1 << sideFrom(e.b, e.a);
            }
        }
        for (Node n : this.grid)
        {
            if (!n.used)
            {
                continue;
            }
            if (n.caveSide >= 0 && n.mainDoor)
            {
                n.mainLinks |= 1 << n.caveSide;
            }
            int degree = Integer.bitCount(n.links);
            boolean royal = this.ratCave && n.mainLinks != 0;
            if (n.pipe)
            {
                n.kind = this.ratCave ? SewerNodePiece.Kind.GRAND_POOL : SewerNodePiece.Kind.POOL;
                n.half = this.ratCave ? SewerTuning.GRAND_POOL_ROOM_HALF : SewerTuning.POOL_ROOM_HALF;
                n.height = this.ratCave ? SewerTuning.GRAND_POOL_ROOM_HEIGHT : SewerTuning.POOL_ROOM_HEIGHT;
            }
            else if (n.kind == SewerNodePiece.Kind.HALL)
            {
                n.half = SewerTuning.HALL_HALF;
                n.height = SewerTuning.HALL_HEIGHT;
            }
            else if (degree >= 3 && n.headroom >= SewerTuning.ROOM_HEIGHT && this.random.nextDouble() < SewerTuning.ROOM_CHANCE)
            {
                n.kind = SewerNodePiece.Kind.ROOM;
                n.half = SewerTuning.ROOM_HALF;
                n.height = SewerTuning.ROOM_HEIGHT;
            }
            else if (n.caveSide < 0 && !royal && n.headroom >= SewerTuning.CAVE_SECTION_HEIGHT_MIN
                    && this.random.nextDouble() < SewerTuning.CAVE_SECTION_CHANCE)
            {
                n.kind = SewerNodePiece.Kind.CAVE;
                n.half = SewerTuning.CAVE_SECTION_RADIUS_MIN
                        + this.random.nextInt(SewerTuning.CAVE_SECTION_RADIUS_MAX - SewerTuning.CAVE_SECTION_RADIUS_MIN + 1);
                n.height = Math.min(n.headroom, SewerTuning.CAVE_SECTION_HEIGHT_MIN
                        + this.random.nextInt(SewerTuning.CAVE_SECTION_HEIGHT_MAX - SewerTuning.CAVE_SECTION_HEIGHT_MIN + 1));
            }
            else
            {
                n.kind = SewerNodePiece.Kind.CROSSING;
                boolean anyMain = n.mainLinks != 0;
                n.half = SewerTunnelPiece.half(anyMain);
                n.height = SewerTunnelPiece.height(anyMain);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The pieces
    // ---------------------------------------------------------------------------------------------

    /** How far a tunnel stops short of a junction's middle (its inside half-width: the tunnel opens its wall). */
    private static int stopShort(Node n)
    {
        return n.kind == SewerNodePiece.Kind.CAVE ? n.half - 2 : n.half;
    }

    private SewerTunnelPiece tunnel(Edge e, int floor)
    {
        boolean alongX = e.a.j == e.b.j;
        Node first = alongX ? (e.a.i < e.b.i ? e.a : e.b) : (e.a.j < e.b.j ? e.a : e.b);
        Node second = first == e.a ? e.b : e.a;
        int from = (alongX ? first.x : first.z) + stopShort(first) + 1;
        int to = (alongX ? second.x : second.z) - stopShort(second) - 1;
        return new SewerTunnelPiece(alongX, alongX ? first.z : first.x, from, Math.max(from, to), e.main, e.main && this.ratCave, false, floor, this.seed);
    }

    /** The tunnel from a door junction straight into the rat cave (it ends well inside the cave's floor). */
    private SewerTunnelPiece doorTunnel(Node n, Cave cave, int floor)
    {
        Direction d = SewerNodePiece.SIDES[n.caveSide];
        boolean alongX = d.getAxis() == Direction.Axis.X;
        int across = Math.abs(alongX ? n.z - cave.z() : n.x - cave.x());
        double inner = SewerTuning.RAT_CAVE_RADIUS - SewerTuning.RAT_CAVE_WOBBLE;
        int reachIn = (int) Math.floor(Math.sqrt(Math.max(0.0D, inner * inner - across * across))) - 3;
        int nodeCoord = alongX ? n.x : n.z;
        int caveCoord = alongX ? cave.x() : cave.z();
        int step = alongX ? d.getStepX() : d.getStepZ();      // +1 if the cave is further along the axis
        int nodeEnd = nodeCoord + step * (stopShort(n) + 1);
        int caveEnd = caveCoord - step * reachIn;
        return new SewerTunnelPiece(alongX, alongX ? n.z : n.x, Math.min(nodeEnd, caveEnd), Math.max(nodeEnd, caveEnd), n.mainDoor,
                n.mainDoor && this.ratCave, true, floor, this.seed);
    }

    private static double distance(int x1, int z1, int x2, int z2)
    {
        return Math.sqrt((double) (x1 - x2) * (x1 - x2) + (double) (z1 - z2) * (z1 - z2));
    }

    private <T> void shuffle(List<T> list)
    {
        for (int k = list.size() - 1; k > 0; k--)
        {
            int swap = this.random.nextInt(k + 1);
            T t = list.get(k);
            list.set(k, list.get(swap));
            list.set(swap, t);
        }
    }
}
