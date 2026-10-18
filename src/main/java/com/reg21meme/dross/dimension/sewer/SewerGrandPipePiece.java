package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModStructures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The Rat King's sewer's one way in (the user's picks, October 16): a huge copper pipe standing out of the Blightwood
 * ground, its rim (11 across) shaped like his crown, and the ground round it poisoned.
 * <ul>
 *   <li><b>The pipe</b>: green oxidised copper with bands of bright, fresh copper, a rim whose top is such a band with
 *       copper points round it, each tipped with a Blight Vent glowing green like his crown's gem (alternately tall and
 *       short, none in front of the steps; no gold blocks, the user's request), and tattered red banners (his cape's colour) hanging off it on the sides away from the steps. Wide stone
 *       steps lead up to it.</li>
 *   <li><b>The drop</b>: its 5 x 5 opening drops down the copper-lined shaft into a deep pool of green sewer water in the
 *       room below ({@link SewerNodePiece.Kind#GRAND_POOL}); a ladder climbs the wall on the steps' side.</li>
 *   <li><b>The glow</b>: Blight Vents in the shaft's wall just under the rim (and in the room's corners) light it poison
 *       green, visible at night, and breathe spores that drift up out of the opening.</li>
 *   <li><b>The poisoned ground</b> round it (like the cathedral's leak), fading out by {@value SewerTuning#POISON_RADIUS}
 *       blocks: coarse dirt, puddles of Sewer Sludge, dead bushes, rat nests (heaps of bone blocks and old carpets), cobwebs,
 *       gnawed fences and a few more vents in the ground. World generation keeps its trees, giant mushrooms and plants out
 *       ({@link SewerTuning#GRAND_CLEARANCE}).</li>
 * </ul>
 */
public final class SewerGrandPipePiece extends SewerPiece implements SewerEntrance
{
    private final int x;
    private final int z;
    /** The ground's top block under the pipe's body (the highest of them). */
    private final int groundY;
    /** The side the steps go down (and the ladder is on). */
    private final Direction steps;

    private volatile Layout layout;

    /** How far the steps reach out from the pipe's middle. */
    static final int STEPS_REACH = (int) Math.floor(SewerTuning.GRAND_RIM_RADIUS) + 1 + SewerTuning.GRAND_BODY_RISE + SewerTuning.GRAND_RIM_HEIGHT;
    /** The steps' and the collar's supports go at most this far down to the ground. */
    private static final int MAX_SUPPORT = 8;
    /** The poisoned ground's top block is looked for this far above and below the pipe's ground. */
    private static final int GROUND_SEARCH_UP = 8;
    private static final int GROUND_SEARCH_DOWN = 10;

    SewerGrandPipePiece(int x, int z, int groundY, Direction steps, int floorY, long seed)
    {
        super(ModStructures.SEWER_GRAND_PIPE.get(), box(x, z, groundY, floorY), floorY, seed);
        this.x = x;
        this.z = z;
        this.groundY = groundY;
        this.steps = steps;
    }

    public SewerGrandPipePiece(CompoundTag tag)
    {
        super(ModStructures.SEWER_GRAND_PIPE.get(), tag);
        this.x = tag.getInt("X");
        this.z = tag.getInt("Z");
        this.groundY = tag.getInt("Ground");
        this.steps = Direction.from2DDataValue(tag.getInt("Steps"));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        super.addAdditionalSaveData(context, tag);
        tag.putInt("X", this.x);
        tag.putInt("Z", this.z);
        tag.putInt("Ground", this.groundY);
        tag.putInt("Steps", this.steps.get2DDataValue());
    }

    private static int reach()
    {
        return Math.max(SewerTuning.POISON_RADIUS + (int) Math.ceil(SewerTuning.POISON_RADIUS * SewerTuning.POISON_WOBBLE), STEPS_REACH) + 1;
    }

    private static BoundingBox box(int x, int z, int groundY, int floorY)
    {
        int r = reach();
        int top = Math.max(rimTop(groundY) + SewerTuning.CROWN_TALL + 4, groundY + GROUND_SEARCH_UP + 3);
        return new BoundingBox(x - r, Math.min(floorY + 1, groundY - GROUND_SEARCH_DOWN - MAX_SUPPORT), z - r, x + r, top, z + r);
    }

    /** The rim's top block (you stand on the rim at this + 1). */
    static int rimTop(int groundY)
    {
        return groundY + SewerTuning.GRAND_BODY_RISE + SewerTuning.GRAND_RIM_HEIGHT;
    }

    // ---------------------------------------------------------------------------------------------
    // SewerEntrance
    // ---------------------------------------------------------------------------------------------

    @Override
    public BlockPos opening()
    {
        return new BlockPos(this.x, rimTop(this.groundY) + 1, this.z);
    }

    @Override
    public BlockPos centre()
    {
        return new BlockPos(this.x, this.floorY, this.z);
    }

    @Override
    public int groundY()
    {
        return this.groundY;
    }

    @Override
    public int rimTopY()
    {
        return rimTop(this.groundY);
    }

    @Override
    public Direction stepsSide()
    {
        return this.steps;
    }

    @Override
    public BlockPos ladderColumn()
    {
        return new BlockPos(this.x + this.steps.getStepX() * SewerTuning.GRAND_OPENING_HALF, 0, this.z + this.steps.getStepZ() * SewerTuning.GRAND_OPENING_HALF);
    }

    @Override
    public int openingHalf()
    {
        return SewerTuning.GRAND_OPENING_HALF;
    }

    @Override
    public boolean grand()
    {
        return true;
    }

    @Override
    public BlockPos approach()
    {
        int out = (int) Math.floor(SewerTuning.GRAND_RIM_RADIUS) + 1 + SewerTuning.GRAND_BODY_RISE + SewerTuning.GRAND_RIM_HEIGHT;
        return new BlockPos(this.x + this.steps.getStepX() * out, this.groundY + 1, this.z + this.steps.getStepZ() * out);
    }

    @Override
    public BoundingBox box()
    {
        return getBoundingBox();
    }

    /** The middles of the shaft's Blight Vents (for checks). */
    public List<BlockPos> shaftVents()
    {
        int y = rimTop(this.groundY) - SewerTuning.SHAFT_VENT_DEPTH;
        int r = SewerTuning.GRAND_OPENING_HALF + 1;
        return List.of(new BlockPos(this.x + r, y, this.z), new BlockPos(this.x - r, y, this.z), new BlockPos(this.x, y, this.z + r),
                new BlockPos(this.x, y, this.z - r));
    }

    // ---------------------------------------------------------------------------------------------
    // The poisoned ground's layout (worked out once from the seed, the same in every chunk)
    // ---------------------------------------------------------------------------------------------

    /** What stands on the poisoned ground, by column (relative to the pipe's middle). */
    private static final class Layout
    {
        /** Rat nests: blocks of bone heaped on the column (1 or 2), with carpet on top; 0 = carpet only, round a heap. */
        final Map<Long, Integer> nests = new HashMap<>();
        /** Cobwebs by the nests. */
        final List<Long> webs = new ArrayList<>();
        /** Fence posts: their height (1 or 2). */
        final Map<Long, Integer> fences = new HashMap<>();
        /** Vents in the ground. */
        final List<Long> vents = new ArrayList<>();
        /** The sludge puddles' noise threshold (lowered from POISON_SLUDGE's if that gave too few: POISON_SLUDGE_MIN)... */
        double sludgeThreshold;
        /** ...and how many columns it makes sludge, before the ground is looked at. */
        int sludgePlanned;
    }

    /** The sludge threshold is lowered by this much at a time... */
    private static final double SLUDGE_RELAX_STEP = 0.02D;
    /** ...at most this many times. */
    private static final int SLUDGE_RELAX_STEPS = 50;

    private static long column(int dx, int dz)
    {
        return ChunkPos.asLong(dx, dz);
    }

    private Layout layout()
    {
        Layout l = this.layout;
        if (l == null)
        {
            synchronized (this)
            {
                l = this.layout;
                if (l == null)
                {
                    l = makeLayout();
                    this.layout = l;
                }
            }
        }
        return l;
    }

    private Layout makeLayout()
    {
        Layout l = new Layout();
        Random random = new Random(this.seed ^ 0x6A0DE7C0B5L ^ ((long) this.x << 32) ^ this.z);
        double stepsAngle = Math.atan2(this.steps.getStepZ(), this.steps.getStepX());
        List<double[]> taken = new ArrayList<>();
        // rat nests
        for (int i = 0; i < SewerTuning.RAT_NESTS; i++)
        {
            for (int attempt = 0; attempt < 20; attempt++)
            {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double dist = Mth.lerp(random.nextDouble(), SewerTuning.RAT_NEST_DISTANCE_MIN, SewerTuning.RAT_NEST_DISTANCE_MAX);
                double r = Mth.lerp(random.nextDouble(), SewerTuning.RAT_NEST_RADIUS_MIN, SewerTuning.RAT_NEST_RADIUS_MAX);
                double cx = Math.cos(angle) * dist;
                double cz = Math.sin(angle) * dist;
                if (angleGap(angle, stepsAngle) < Math.toRadians(35.0D) || tooClose(taken, cx, cz, r + 3.0D))
                {
                    continue;
                }
                taken.add(new double[] {cx, cz, r});
                int span = (int) Math.ceil(r + 1.0D);
                for (int dx = (int) Math.floor(cx) - span; dx <= (int) Math.ceil(cx) + span; dx++)
                {
                    for (int dz = (int) Math.floor(cz) - span; dz <= (int) Math.ceil(cz) + span; dz++)
                    {
                        double d = Math.hypot(dx - cx, dz - cz);
                        if (d <= r)
                        {
                            l.nests.put(column(dx, dz), d <= r * 0.45D ? 2 : 1);
                        }
                        else if (d <= r + 1.0D && !l.nests.containsKey(column(dx, dz)) && random.nextDouble() < 0.7D)
                        {
                            l.nests.put(column(dx, dz), 0);
                        }
                    }
                }
                int webs = 1 + random.nextInt(2);
                for (int w = 0; w < webs; w++)
                {
                    double a = random.nextDouble() * Math.PI * 2.0D;
                    l.webs.add(column((int) Math.round(cx + Math.cos(a) * (r + 1.4D)), (int) Math.round(cz + Math.sin(a) * (r + 1.4D))));
                }
                break;
            }
        }
        // gnawed fences: short runs across the line to the pipe
        for (int i = 0; i < SewerTuning.FENCE_RUNS; i++)
        {
            for (int attempt = 0; attempt < 20; attempt++)
            {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double dist = Mth.lerp(random.nextDouble(), SewerTuning.FENCE_DISTANCE_MIN, SewerTuning.FENCE_DISTANCE_MAX);
                int length = SewerTuning.FENCE_LENGTH_MIN + random.nextInt(SewerTuning.FENCE_LENGTH_MAX - SewerTuning.FENCE_LENGTH_MIN + 1);
                double sx = Math.cos(angle) * dist;
                double sz = Math.sin(angle) * dist;
                if (angleGap(angle, stepsAngle) < Math.toRadians(30.0D) || tooClose(taken, sx, sz, length / 2.0D + 2.0D))
                {
                    continue;
                }
                taken.add(new double[] {sx, sz, length / 2.0D});
                // the run goes across the line from the pipe (along X if that line is mostly along Z, and the other way round)
                boolean alongX = Math.abs(Math.sin(angle)) > Math.abs(Math.cos(angle));
                int bx = (int) Math.round(sx) - (alongX ? length / 2 : 0);
                int bz = (int) Math.round(sz) - (alongX ? 0 : length / 2);
                for (int k = 0; k < length; k++)
                {
                    if (random.nextDouble() < SewerTuning.FENCE_GAP_CHANCE)
                    {
                        continue;   // gnawed away
                    }
                    l.fences.put(column(bx + (alongX ? k : 0), bz + (alongX ? 0 : k)), random.nextDouble() < SewerTuning.FENCE_TALL_CHANCE ? 2 : 1);
                }
                break;
            }
        }
        // vents in the ground
        for (int i = 0; i < SewerTuning.GROUND_VENTS; i++)
        {
            for (int attempt = 0; attempt < 20; attempt++)
            {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double dist = Mth.lerp(random.nextDouble(), SewerTuning.GRAND_RIM_RADIUS + 2.5D, SewerTuning.POISON_RADIUS * 0.7D);
                int dx = (int) Math.round(Math.cos(angle) * dist);
                int dz = (int) Math.round(Math.sin(angle) * dist);
                if (angleGap(angle, stepsAngle) < Math.toRadians(30.0D) || l.nests.containsKey(column(dx, dz)) || l.fences.containsKey(column(dx, dz)))
                {
                    continue;
                }
                l.vents.add(column(dx, dz));
                break;
            }
        }
        // sludge puddles: where the patch noise is high enough (POISON_SLUDGE), but at least POISON_SLUDGE_MIN blocks: if
        // the noise runs low round this pipe, its puddles grow (the threshold drops) until there are that many
        double threshold = SewerNoise.thresholdFor(SewerTuning.POISON_SLUDGE);
        int planned = plannedSludge(l, threshold);
        for (int step = 0; step < SLUDGE_RELAX_STEPS && planned < SewerTuning.POISON_SLUDGE_MIN; step++)
        {
            threshold -= SLUDGE_RELAX_STEP;
            planned = plannedSludge(l, threshold);
        }
        l.sludgeThreshold = threshold;
        l.sludgePlanned = planned;
        return l;
    }

    /** How many columns of the poisoned ground get sludge with this threshold (not counting nests, fences and vents). */
    private int plannedSludge(Layout l, double threshold)
    {
        int r = reach();
        int n = 0;
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                double strength = poisonStrength(dx, dz);
                long key = column(dx, dz);
                if (strength >= 0.0D && !l.vents.contains(key) && !l.nests.containsKey(key) && !l.fences.containsKey(key)
                        && isSludge(dx, dz, strength, threshold))
                {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * How strong the poison is at (dx, dz) from the pipe's middle: 1 at the pipe, fading to 0 at the poisoned ground's
     * wobbly edge; -1 where there's no poisoned ground (under the rim, on the steps' lane, beyond the edge).
     */
    private double poisonStrength(int dx, int dz)
    {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= SewerTuning.GRAND_RIM_RADIUS + 0.7D || onSteps(dx, dz))
        {
            return -1.0D;
        }
        double angle = Math.atan2(dz, dx);
        double wobble = SewerNoise.value2(this.seed ^ 0x9015E2L, 3.0D + 1.5D * Math.cos(angle), 3.0D + 1.5D * Math.sin(angle));
        double edge = SewerTuning.POISON_RADIUS * (1.0D + SewerTuning.POISON_WOBBLE * wobble);
        if (d > edge)
        {
            return -1.0D;
        }
        double e = d / edge;
        return 1.0D - e * e;
    }

    /** True if the column (dx, dz), with this poison strength, is a sludge puddle (the weaker the poison, the rarer). */
    private boolean isSludge(int dx, int dz, double strength, double threshold)
    {
        return SewerNoise.patches(this.seed ^ 0x51D9EL, this.x + dx, this.z + dz, SewerTuning.POISON_SLUDGE_PATCH) > threshold + (1.0D - strength) * 0.6D;
    }

    /**
     * How many columns of the poisoned ground its layout gives a sludge puddle (at least
     * {@link SewerTuning#POISON_SLUDGE_MIN}), before the ground is looked at: a pond, a plant or a bump can take a few.
     */
    public int plannedSludge()
    {
        return layout().sludgePlanned;
    }

    private static double angleGap(double a, double b)
    {
        double d = Math.abs(Mth.wrapDegrees(Math.toDegrees(a - b)));
        return Math.toRadians(d);
    }

    private static boolean tooClose(List<double[]> taken, double x, double z, double distance)
    {
        for (double[] t : taken)
        {
            if (Math.hypot(t[0] - x, t[1] - z) < distance + t[2])
            {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Building it
    // ---------------------------------------------------------------------------------------------

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        buildPipe(level, chunkBox, pos);
        buildSteps(level, chunkBox, pos);
        buildCrownAndBanners(level, chunkBox, pos);
        poisonGround(level, chunkBox, pos);
    }

    private void buildPipe(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos)
    {
        int f = this.floorY;
        int g = this.groundY;
        int bodyTop = g + SewerTuning.GRAND_BODY_RISE;
        int rimTop = rimTop(g);
        int open = SewerTuning.GRAND_OPENING_HALF;
        int lowest = f + 1 + SewerTuning.GRAND_PIPE_END_ABOVE_WATER - 1;   // the shaft's lowest copper ring, over the pool
        double shaftR2 = SewerTuning.GRAND_SHAFT_RADIUS * SewerTuning.GRAND_SHAFT_RADIUS;
        double bodyR2 = SewerTuning.GRAND_BODY_RADIUS * SewerTuning.GRAND_BODY_RADIUS;
        double rimR2 = SewerTuning.GRAND_RIM_RADIUS * SewerTuning.GRAND_RIM_RADIUS;
        BlockPos ladder = ladderColumn();
        BlockPos backing = ladder.relative(this.steps);
        BlockState ladderState = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, this.steps.getOpposite());
        int ventY = rimTop - SewerTuning.SHAFT_VENT_DEPTH;
        int r = (int) Math.ceil(SewerTuning.GRAND_RIM_RADIUS);
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                int bx = this.x + dx;
                int bz = this.z + dz;
                if (!chunkBox.isInside(bx, g, bz))
                {
                    continue;
                }
                int d2 = dx * dx + dz * dz;
                boolean opening = Math.abs(dx) <= open && Math.abs(dz) <= open;
                boolean lining = !opening && d2 <= shaftR2;
                boolean body = !opening && d2 <= bodyR2;
                boolean rim = !opening && d2 <= rimR2;
                boolean isLadder = bx == ladder.getX() && bz == ladder.getZ();
                boolean isBacking = bx == backing.getX() && bz == backing.getZ();
                boolean vent = (dx == 0 || dz == 0) && Math.abs(dx) + Math.abs(dz) == open + 1;
                if (opening)
                {
                    // the shaft: open all the way down to the pool room, the ladder up one side (its backing, the wall
                    // behind it, may be in the next chunk and built later: the tidy-up leaves the ladder alone till then)
                    for (int y = f + 1; y <= rimTop + 1; y++)
                    {
                        place(level, chunkBox, pos, bx, y, bz, isLadder && y <= rimTop ? ladderState : air());
                    }
                    clearAbove(level, chunkBox, pos, bx, bz, rimTop + 1);
                    continue;
                }
                if (body)
                {
                    // the lining runs down the shaft (the ladder's backing down to the water); the fat body only above the ground
                    int from = lining ? (isBacking ? f + 1 : lowest) : g - 1;
                    for (int y = from; y <= bodyTop; y++)
                    {
                        BlockState state;
                        if (vent && y == ventY)
                        {
                            state = ModBlocks.BLIGHT_VENT.get().defaultBlockState();
                        }
                        else if (y >= g - 1)
                        {
                            boolean band = (bodyTop - y) % SewerTuning.GRAND_BAND_EVERY == SewerTuning.GRAND_BAND_EVERY - 1;
                            state = band ? pick(SewerTuning.CROWN_BAND, bx, y, bz) : pick(SewerTuning.PIPE_BODY, bx, y, bz);
                        }
                        else
                        {
                            boolean band = (bodyTop - y) % SewerTuning.PIPE_BAND_EVERY == SewerTuning.PIPE_BAND_EVERY - 1;
                            state = pick(band ? SewerTuning.PIPE_BAND : SewerTuning.PIPE_BODY, bx, y, bz);
                        }
                        place(level, chunkBox, pos, bx, y, bz, state);
                    }
                }
                if (rim)
                {
                    for (int y = bodyTop + 1; y <= rimTop; y++)
                    {
                        BlockState state = vent && y == ventY ? ModBlocks.BLIGHT_VENT.get().defaultBlockState()
                                : y == rimTop ? pick(SewerTuning.CROWN_BAND, bx, y, bz) : pick(SewerTuning.PIPE_RIM, bx, y, bz);
                        place(level, chunkBox, pos, bx, y, bz, state);
                    }
                    clearAbove(level, chunkBox, pos, bx, bz, rimTop + 1);
                    if (!body)
                    {
                        // under the rim's overhang: air down to the ground, a stone collar on the ground round the pipe's foot
                        for (int y = g + 1; y <= bodyTop; y++)
                        {
                            place(level, chunkBox, pos, bx, y, bz, air());
                        }
                        place(level, chunkBox, pos, bx, g, bz, pick(SewerTuning.PIPE_COLLAR, bx, g, bz));
                        support(level, chunkBox, pos, bx, g - 1, bz);
                    }
                }
            }
        }
    }

    /** Fills down from y to the ground (at most MAX_SUPPORT blocks) with rubble. */
    private void support(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int bx, int fromY, int bz)
    {
        for (int y = fromY; y > fromY - MAX_SUPPORT; y--)
        {
            if (isGround(level.getBlockState(pos.set(bx, y, bz))))
            {
                break;
            }
            place(level, chunkBox, pos, bx, y, bz, pick(SewerTuning.STEP_SUPPORT, bx, y, bz));
        }
    }

    /** Clears plants and the like off the top of the pipe (anything that isn't air, up to a few blocks over it). */
    private static void clearAbove(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int z, int from)
    {
        for (int y = from; y < from + 4; y++)
        {
            if (chunkBox.isInside(x, y, z) && !level.getBlockState(pos.set(x, y, z)).isAir())
            {
                place(level, chunkBox, pos, x, y, z, air());
            }
        }
    }

    /** Wide stone brick steps going down and out from the rim on the steps' side, each with rubble under it to the ground. */
    private void buildSteps(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos)
    {
        int rimTop = rimTop(this.groundY);
        int first = (int) Math.floor(SewerTuning.GRAND_RIM_RADIUS) + 1;
        int count = SewerTuning.GRAND_BODY_RISE + SewerTuning.GRAND_RIM_HEIGHT;
        Direction across = this.steps.getClockWise();
        int halfWidth = SewerTuning.GRAND_STEPS_WIDTH / 2;
        for (int k = 0; k < count; k++)
        {
            int out = first + k;
            int stairY = rimTop - k;
            for (int w = -halfWidth; w <= halfWidth; w++)
            {
                int bx = this.x + this.steps.getStepX() * out + across.getStepX() * w;
                int bz = this.z + this.steps.getStepZ() * out + across.getStepZ() * w;
                if (!chunkBox.isInside(bx, stairY, bz))
                {
                    continue;
                }
                if (isGround(level.getBlockState(pos.set(bx, stairY, bz))))
                {
                    continue;   // the ground is already this high here: no step needed
                }
                boolean mossy = unit(bx, stairY, bz, 31) < 0.45D;
                BlockState stair = (mossy ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS).defaultBlockState()
                        .setValue(StairBlock.FACING, this.steps.getOpposite()).setValue(StairBlock.HALF, Half.BOTTOM);
                place(level, chunkBox, pos, bx, stairY, bz, stair);
                clearAbove(level, chunkBox, pos, bx, bz, stairY + 1);
                support(level, chunkBox, pos, bx, stairY - 1, bz);
            }
        }
    }

    /** True if (dx, dz) is on the steps' lane (where the poisoned ground leaves the way up clear). */
    private boolean onSteps(int dx, int dz)
    {
        int along = dx * this.steps.getStepX() + dz * this.steps.getStepZ();
        int across = Math.abs(dx * this.steps.getStepZ() + dz * this.steps.getStepX());
        return along > 0 && along <= STEPS_REACH + 2 && across <= SewerTuning.GRAND_STEPS_WIDTH / 2 + 1;
    }

    /** The crown's copper points with their green tips (Blight Vents), and the tattered banners hanging off the rim. */
    private void buildCrownAndBanners(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos)
    {
        int rimTop = rimTop(this.groundY);
        double stepsAngle = Math.atan2(this.steps.getStepZ(), this.steps.getStepX());
        double rimR2 = SewerTuning.GRAND_RIM_RADIUS * SewerTuning.GRAND_RIM_RADIUS;
        double bodyR2 = SewerTuning.GRAND_BODY_RADIUS * SewerTuning.GRAND_BODY_RADIUS;
        for (int k = 0; k < SewerTuning.CROWN_POINTS; k++)
        {
            double angle = stepsAngle + Math.PI / SewerTuning.CROWN_POINTS + k * 2.0D * Math.PI / SewerTuning.CROWN_POINTS;
            if (Math.toDegrees(angleGap(angle, stepsAngle)) < SewerTuning.CROWN_STEPS_CLEARANCE_DEGREES)
            {
                continue;
            }
            int dx = (int) Math.round(Math.cos(angle) * (SewerTuning.GRAND_RIM_RADIUS - 0.5D));
            int dz = (int) Math.round(Math.sin(angle) * (SewerTuning.GRAND_RIM_RADIUS - 0.5D));
            int d2 = dx * dx + dz * dz;
            if (d2 > rimR2 || d2 <= bodyR2 - 4.0D)
            {
                continue;
            }
            int bx = this.x + dx;
            int bz = this.z + dz;
            int copper = k % 2 == 0 ? SewerTuning.CROWN_TALL : SewerTuning.CROWN_SHORT;
            for (int y = rimTop + 1; y <= rimTop + copper; y++)
            {
                place(level, chunkBox, pos, bx, y, bz, pick(SewerTuning.CROWN_COPPER, bx, y, bz));
            }
            place(level, chunkBox, pos, bx, rimTop + copper + 1, bz, ModBlocks.BLIGHT_VENT.get().defaultBlockState());
        }
        // the banners: off the rim's outer face on the two sides away from the steps (one each, up to GRAND_BANNERS)
        Direction[] sides = {this.steps.getClockWise(), this.steps.getCounterClockWise(), this.steps.getOpposite()};
        int out = (int) Math.floor(SewerTuning.GRAND_RIM_RADIUS) + 1;
        for (int i = 0; i < Math.min(SewerTuning.GRAND_BANNERS, sides.length); i++)
        {
            Direction side = sides[i];
            // (its wall is the rim's outer face, which may be in the next chunk and not built yet: placed anyway)
            banner(level, chunkBox, pos, this.x + side.getStepX() * out, rimTop, this.z + side.getStepZ() * out, side.getOpposite(), true);
        }
    }

    /** The poisoned ground round the pipe. */
    private void poisonGround(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos)
    {
        Layout l = layout();
        int r = reach();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                int bx = this.x + dx;
                int bz = this.z + dz;
                if (!chunkBox.isInside(bx, this.groundY, bz))
                {
                    continue;
                }
                // how strong the poison is here: full near the pipe, fading to nothing at the edge (none off the ground)
                double strength = poisonStrength(dx, dz);
                if (strength < 0.0D)
                {
                    continue;
                }
                int top = groundTop(level, pos, bx, bz);
                if (top == Integer.MIN_VALUE)
                {
                    continue;
                }
                long key = column(dx, dz);
                Integer nest = l.nests.get(key);
                Integer fence = l.fences.get(key);
                if (l.vents.contains(key))
                {
                    place(level, chunkBox, pos, bx, top, bz, ModBlocks.BLIGHT_VENT.get().defaultBlockState());
                    continue;
                }
                if (nest != null)
                {
                    place(level, chunkBox, pos, bx, top, bz, Blocks.COARSE_DIRT.defaultBlockState());
                    for (int k = 1; k <= nest; k++)
                    {
                        place(level, chunkBox, pos, bx, top + k, bz, Blocks.BONE_BLOCK.defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, unit(bx, top + k, bz, 51) < 0.5D ? Direction.Axis.X : Direction.Axis.Z));
                    }
                    place(level, chunkBox, pos, bx, top + nest + 1, bz, pick(SewerTuning.RAT_NEST_CARPETS, bx, top + nest + 1, bz));
                    continue;
                }
                if (fence != null)
                {
                    place(level, chunkBox, pos, bx, top, bz, Blocks.COARSE_DIRT.defaultBlockState());
                    BlockState post = pick(SewerTuning.GNAWED_FENCES, bx, top, bz);
                    for (int k = 1; k <= fence; k++)
                    {
                        place(level, chunkBox, pos, bx, top + k, bz, connect(post, l, dx, dz, k));
                    }
                    continue;
                }
                if (isSludge(dx, dz, strength, l.sludgeThreshold))
                {
                    place(level, chunkBox, pos, bx, top, bz, ModBlocks.SEWER_SLUDGE.get().defaultBlockState());
                    continue;
                }
                if (unit(bx, top, bz, 52) < SewerTuning.POISON_COARSE_DIRT * strength)
                {
                    place(level, chunkBox, pos, bx, top, bz, Blocks.COARSE_DIRT.defaultBlockState());
                }
                if (!level.getBlockState(pos.set(bx, top + 1, bz)).isAir())
                {
                    continue;
                }
                if (l.webs.contains(key) || unit(bx, top, bz, 53) < SewerTuning.POISON_COBWEBS * strength)
                {
                    place(level, chunkBox, pos, bx, top + 1, bz, Blocks.COBWEB.defaultBlockState());
                }
                else if (unit(bx, top, bz, 54) < SewerTuning.POISON_DEAD_BUSHES * strength
                        && Blocks.DEAD_BUSH.defaultBlockState().canSurvive(level, pos.set(bx, top + 1, bz)))
                {
                    place(level, chunkBox, pos, bx, top + 1, bz, Blocks.DEAD_BUSH.defaultBlockState());
                }
            }
        }
    }

    /** A fence post joined to the posts next to it in its run (at the same height or higher). */
    private static BlockState connect(BlockState post, Layout l, int dx, int dz, int level)
    {
        BlockState state = post;
        Direction[] sides = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        BooleanProperty[] props = {CrossCollisionBlock.NORTH, CrossCollisionBlock.EAST, CrossCollisionBlock.SOUTH, CrossCollisionBlock.WEST};
        for (int i = 0; i < 4; i++)
        {
            Integer other = l.fences.get(column(dx + sides[i].getStepX(), dz + sides[i].getStepZ()));
            state = state.setValue(props[i], other != null && other >= level);
        }
        return state;
    }

    /** The column's top ground block (natural, solid; not water), near the pipe's ground height, or MIN_VALUE if none. */
    private int groundTop(WorldGenLevel level, BlockPos.MutableBlockPos pos, int bx, int bz)
    {
        for (int y = this.groundY + GROUND_SEARCH_UP; y >= this.groundY - GROUND_SEARCH_DOWN; y--)
        {
            BlockState state = level.getBlockState(pos.set(bx, y, bz));
            if (state.isAir())
            {
                continue;
            }
            if (!state.getFluidState().isEmpty() || !isGround(state) || y == this.groundY + GROUND_SEARCH_UP)
            {
                return Integer.MIN_VALUE;   // a pond or a plant, or a hill rising above the search: leave it
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Solid ground (not air, a plant, water, leaves or a log). */
    private static boolean isGround(BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)
                && !state.is(BlockTags.LOGS) && state.isSolid();
    }
}
