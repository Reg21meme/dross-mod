package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.phys.Vec3;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Carves the Colossus's lair into the real world (its chunks must be loaded; server thread). In order:
 * <ol>
 *   <li>The plan is worked out again on the spot ({@link LairShape#plan}, from {@link #planningGround}): how deep the
 *       floor goes and which way the tunnel runs.</li>
 *   <li>Trees in the way (charred trees) are removed whole, so no floating bits are left.</li>
 *   <li>Where the dome would rise above the ground, a lumpy mound of rock covers it (filling only air, fluids and
 *       plants, down to the ground).</li>
 *   <li>The lining: everything within {@link LairTuning#SHELL_THICKNESS} blocks of the cavern (and under its floor)
 *       becomes Cinder rock: basalt, blackstone, smooth basalt.</li>
 *   <li>The cavern: a round floor 41 blocks across, walls {@link LairTuning#WALL_HEIGHT} high curving into a dome
 *       {@link LairTuning#DOME_HEIGHT} high, roughened a little; the floor in the boss arena's style (red nether
 *       bricks, a chiseled middle, rings of nether bricks with shroomlights).</li>
 *   <li>The tunnel: 3 wide and 5 tall, level for {@link LairTuning#TUNNEL_STRAIGHT} blocks out of the cavern, then
 *       climbing half a block per block on nether brick slabs to its mouth (a basalt and blackstone arch with two
 *       lanterns), and a short cut approach in front of it; lined with rock (built up into a covered passage where it
 *       comes out above the ground), dim magma lights in its walls.</li>
 *   <li>The skylight: a round shaft from the top of the dome to the sky, lined with rock, with a low wall round its top.</li>
 *   <li>Safety: any fluid touching the carved space is turned to rock (no lava can flow in), and plants left without
 *       ground are removed.</li>
 *   <li>Decoration: glowing magma veins up the walls and over the dome, and lava basins at the foot of the wall behind
 *       low walls (lava that can't flow anywhere). No fire, nothing that burns.</li>
 * </ol>
 * Blocks with a block entity (chests...) are never changed. Blocks are set without poking their neighbours (nothing
 * starts flowing or falling), but players see every change.
 */
final class LairBuilder
{
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private LairBuilder() {}

    /**
     * The built lair.
     *
     * @param centerX     the floor's middle block (X)
     * @param centerZ     the floor's middle block (Z)
     * @param floorY      the floor blocks' Y (players stand at floorY + 1)
     * @param tunnel      the way the tunnel runs out of the cavern, towards its mouth
     * @param descent     how many blocks the tunnel goes down from the mouth to the floor
     * @param entrance    where a player arriving stands: on the approach in front of the mouth, feet position
     * @param entranceYaw the way to face from there: into the tunnel
     */
    record Layout(int centerX, int centerZ, int floorY, Direction tunnel, int descent, Vec3 entrance, float entranceYaw)
    {
    }

    /** What building did: the layout, the plan it followed, and counts for the log. */
    record Result(Layout layout, LairShape.Plan plan, int changed, int trees, int plants, int sealed, int basins, int veins,
                  long millis)
    {
    }

    /** Builds the lair with its floor's middle at {@code x}, {@code z}. */
    static Result build(ServerLevel level, int x, int z)
    {
        long start = Util.getMillis();
        LairShape.Plan plan = LairShape.plan(x, z, planningGround(level));
        int lowest = level.getMinBuildHeight() + UNDER_FLOOR + FILL_DEPTH + 2;
        int highest = level.getMaxBuildHeight() - MOUND_HEIGHT - (int) Math.ceil(MOUND_ROUGHNESS) - 4;
        if (plan.floorY() < lowest || plan.floorY() > highest)
        {
            int floor = Mth.clamp(plan.floorY(), lowest, highest);
            plan = new LairShape.Plan(plan.x(), plan.z(), floor, plan.tunnel(), plan.descent(), plan.exposure(), plan.highGround(),
                    plan.mouthGround(), plan.buried());
        }
        Work work = new Work(level, plan);
        work.run();
        Layout layout = new Layout(x, z, plan.floorY(), plan.tunnel(), plan.descent(), work.entrance(),
                plan.tunnel().getOpposite().toYRot());
        return new Result(layout, plan, work.changed, work.trees, work.plants, work.sealed, work.basins, work.veins,
                Util.getMillis() - start);
    }

    /**
     * The ground the plan is made from: the lower of the real ground ({@link #groundTop}) and the world generator's own
     * terrain (before trees, basalt spires and other features). So a spire or a boulder standing on the tunnel's mouth
     * doesn't lift the floor, while an old world's flat chunks (lower than the new terrain) are still followed.
     */
    static LairShape.Ground planningGround(ServerLevel level)
    {
        LairSearch.Context noise = level.getChunkSource().getGenerator() instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator
                ? LairSearch.forWorld(level) : null;
        return (x, z) -> {
            int real = groundTop(level, x, z);
            return noise == null ? real : Math.min(real, noise.ground(x, z));
        };
    }

    /** The Y of a column's top ground block: the highest full-topped block that isn't a log, leaves or fluid. */
    static int groundTop(LevelReader level, int x, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (; y > level.getMinBuildHeight(); y--)
        {
            pos.set(x, y, z);
            if (isGround(level, pos, level.getBlockState(pos)))
            {
                return y;
            }
        }
        return y;
    }

    private static boolean isGround(BlockGetter level, BlockPos pos, BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /** True for the lair's rock (the lining's blocks): veins may only replace these. */
    static boolean isLairRock(BlockState state)
    {
        return state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.SMOOTH_BASALT);
    }

    /** A steady 0-1 number for a block (the same every time). */
    static float hash(int x, int y, int z)
    {
        long h = x * 73856093L ^ y * 83492791L ^ z * 19349663L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    /** True for the floor blocks that are lights on a ring: {@code count} of them, evenly spaced round it. */
    private static boolean isLightSpot(int dx, int dz, int count)
    {
        double angle = Math.atan2(dz, dx);
        double step = 2.0D * Math.PI / count;
        double off = Math.abs(Math.IEEEremainder(angle, step));
        return off * Math.sqrt(dx * dx + dz * dz) < 0.5D;
    }

    /** The cavern floor's block at an offset from the middle (the boss arena's style, {@link com.reg21meme.dross.boss.BossArena}). */
    static BlockState floorBlock(int dx, int dz, int x, int z)
    {
        double r = Math.sqrt(dx * dx + dz * dz);
        if (r <= MEDALLION)
        {
            return Blocks.CHISELED_NETHER_BRICKS.defaultBlockState();
        }
        if (Math.abs(r - INNER_RING) < 0.5D)
        {
            return (isLightSpot(dx, dz, INNER_RING_LIGHTS) ? Blocks.SHROOMLIGHT : Blocks.NETHER_BRICKS).defaultBlockState();
        }
        if (Math.abs(r - OUTER_RING) < 0.5D)
        {
            return (isLightSpot(dx, dz, OUTER_RING_LIGHTS) ? Blocks.SHROOMLIGHT : Blocks.NETHER_BRICKS).defaultBlockState();
        }
        if (r >= LairShape.FLOOR_EDGE - 1.0D)
        {
            return Blocks.NETHER_BRICKS.defaultBlockState();
        }
        return (hash(x, 0, z) < CRACKED_SHARE ? Blocks.CRACKED_NETHER_BRICKS : Blocks.RED_NETHER_BRICKS).defaultBlockState();
    }

    // ---------------------------------------------------------------- one build

    private static final class Work
    {
        final ServerLevel level;
        final int cx;
        final int cz;
        final int floor;
        final Direction dir;
        final Direction side;
        final int descent;
        final int mouth;
        final int entranceS;
        final ImprovedNoise noise;
        final RandomSource random;
        /** Every block this build made (or found) air in the lair's space, for the safety passes. */
        final LongOpenHashSet carved = new LongOpenHashSet();
        final List<BlockPos> walls = new ArrayList<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed;
        int trees;
        int plants;
        int sealed;
        int basins;
        int veins;

        Work(ServerLevel level, LairShape.Plan plan)
        {
            this.level = level;
            this.cx = plan.x();
            this.cz = plan.z();
            this.floor = plan.floorY();
            this.dir = plan.tunnel();
            this.side = dir.getClockWise();
            this.descent = plan.descent();
            this.mouth = LairShape.mouthDistance(descent);
            this.entranceS = LairShape.entranceDistance(descent);
            long seed = level.getSeed() ^ (cx * 341873128712L + cz * 132897987541L);
            this.noise = new ImprovedNoise(RandomSource.create(seed));
            this.random = RandomSource.create(seed + 1L);
        }

        void run()
        {
            removeTrees();
            mound();
            shell();
            cavern();
            floor();
            tunnel();
            approach();
            skylight();
            sealFluids();
            dropUnsupported();
            veins();
            basins();
            updateWalls();
        }

        Vec3 entrance()
        {
            return new Vec3(tx(entranceS, 0) + 0.5D, floor + descent + 1.0D, tz(entranceS, 0) + 0.5D);
        }

        // ------------------------------------------------------------ helpers

        int tx(int s, int l)
        {
            return cx + dir.getStepX() * s + side.getStepX() * l;
        }

        int tz(int s, int l)
        {
            return cz + dir.getStepZ() * s + side.getStepZ() * l;
        }

        BlockState get(int x, int y, int z)
        {
            return level.getBlockState(pos.set(x, y, z));
        }

        /** Sets a block (never one with a block entity). */
        void set(int x, int y, int z, BlockState state)
        {
            pos.set(x, y, z);
            BlockState here = level.getBlockState(pos);
            if (here.hasBlockEntity() || here == state)
            {
                return;
            }
            level.setBlock(pos, state, FLAGS);
            changed++;
        }

        /** Makes a block part of the lair's open space. */
        void carve(int x, int y, int z)
        {
            set(x, y, z, AIR);
            carved.add(BlockPos.asLong(x, y, z));
        }

        /** True for what fills may replace: air, fluids, plants and other soft or see-through blocks. */
        boolean fillable(BlockState state)
        {
            return !state.hasBlockEntity() && (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()
                    || !state.isSolid());
        }

        /** The lair's rock at a block: basalt, blackstone or smooth basalt, mixed. */
        BlockState rock(int x, int y, int z)
        {
            float h = hash(x, y, z);
            if (h < 0.5F)
            {
                return Blocks.BASALT.defaultBlockState();
            }
            return (h < 0.88F ? Blocks.BLACKSTONE : Blocks.SMOOTH_BASALT).defaultBlockState();
        }

        /** How far the walls and ceiling are carved past their smooth shape here (0 to {@link LairTuning#ROUGHNESS}). */
        double rough(int x, int y, int z)
        {
            double n = noise.noise(x / ROUGHNESS_SCALE, y / ROUGHNESS_SCALE, z / ROUGHNESS_SCALE);
            return Mth.clamp((n + 0.6D) / 1.2D, 0.0D, 1.0D) * ROUGHNESS;
        }

        boolean inCavern(int x, int y, int z)
        {
            int dx = x - cx;
            int dz = z - cz;
            return LairShape.inCavern(Math.sqrt(dx * dx + dz * dz), y - floor, rough(x, y, z));
        }

        /** True if the column (offset from the middle) is in the band of the tunnel and its approach, {@code half} either side. */
        boolean inTunnelBand(int dx, int dz, int half)
        {
            int s = dx * dir.getStepX() + dz * dir.getStepZ();
            int l = dx * side.getStepX() + dz * side.getStepZ();
            return s >= FLOOR_RADIUS && s <= entranceS + 1 && Math.abs(l) <= half;
        }

        int surface(int x, int z)
        {
            return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        }

        /** Fills soft blocks under a column from {@code y} down, until solid ground (at most {@link LairTuning#FILL_DEPTH}). */
        void fillDown(int x, int y, int z)
        {
            for (int i = 0; i < FILL_DEPTH && y > level.getMinBuildHeight(); i++, y--)
            {
                BlockState state = get(x, y, z);
                if (!fillable(state))
                {
                    return;
                }
                set(x, y, z, rock(x, y, z));
            }
        }

        // ------------------------------------------------------------ 1. trees in the way

        void removeTrees()
        {
            int reach = Math.max((int) Math.ceil(MOUND_RADIUS) + 1, entranceS + 2);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    boolean inMound = dx * dx + dz * dz <= (MOUND_RADIUS + 1.0D) * (MOUND_RADIUS + 1.0D);
                    if (!inMound && !inTunnelBand(dx, dz, TUNNEL_HALF_WIDTH + 2))
                    {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int top = surface(x, z);
                    int ground = groundTop(level, x, z);
                    for (int y = top; y > ground; y--)
                    {
                        if (get(x, y, z).is(BlockTags.LOGS))
                        {
                            removeTree(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }

        /** Removes a whole tree (its logs and leaves, touching even corner to corner) from one of its logs. */
        void removeTree(BlockPos start)
        {
            LongOpenHashSet seen = new LongOpenHashSet();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start.asLong());
            int removed = 0;
            while (!queue.isEmpty() && removed < TREE_MAX_BLOCKS)
            {
                BlockPos at = queue.poll();
                BlockState state = level.getBlockState(at);
                if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES))
                {
                    continue;
                }
                set(at.getX(), at.getY(), at.getZ(), AIR);
                carved.add(at.asLong());
                removed++;
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        for (int dz = -1; dz <= 1; dz++)
                        {
                            BlockPos next = at.offset(dx, dy, dz);
                            if (Math.abs(next.getX() - start.getX()) > TREE_REACH || Math.abs(next.getY() - start.getY()) > TREE_REACH
                                    || Math.abs(next.getZ() - start.getZ()) > TREE_REACH || !seen.add(next.asLong()))
                            {
                                continue;
                            }
                            queue.add(next);
                        }
                    }
                }
            }
            if (removed > 0)
            {
                trees++;
            }
        }

        // ------------------------------------------------------------ 2. the mound over the dome

        void mound()
        {
            int reach = (int) Math.ceil(MOUND_RADIUS);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r >= MOUND_RADIUS)
                    {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    double lumps = noise.noise(x / 8.0D, 1000.5D, z / 8.0D) * MOUND_ROUGHNESS;
                    int top = floor + (int) Math.floor(LairShape.moundTop(r) + lumps);
                    boolean first = true;
                    int lowest = Math.max(level.getMinBuildHeight() + 1, floor - UNDER_FLOOR - FILL_DEPTH);
                    for (int y = top; y >= lowest; y--)
                    {
                        BlockState state = get(x, y, z);
                        if (fillable(state))
                        {
                            boolean ash = first && hash(x, y, z) < 0.5F && get(x, y + 1, z).isAir();
                            set(x, y, z, ash ? ModBlocks.ASH.get().defaultBlockState() : rock(x, y, z));
                            first = false;
                        }
                        else if (y <= floor)
                        {
                            break;
                        }
                        else
                        {
                            first = false;
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ 3. the rock lining

        void shell()
        {
            int reach = (int) Math.ceil(LairShape.SHELL_EDGE);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > LairShape.SHELL_EDGE)
                    {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int top = floor + (int) Math.floor(LairShape.shellTop(r));
                    for (int y = floor - UNDER_FLOOR; y <= top; y++)
                    {
                        if (!inCavern(x, y, z))
                        {
                            set(x, y, z, rock(x, y, z));
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ 4. the cavern and its floor

        void cavern()
        {
            int reach = (int) Math.ceil(LairShape.FLOOR_EDGE + ROUGHNESS);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > LairShape.FLOOR_EDGE + ROUGHNESS)
                    {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int top = floor + (int) Math.ceil(LairShape.ceiling(Math.min(r, LairShape.FLOOR_EDGE)) + ROUGHNESS);
                    for (int y = floor + 1; y <= top; y++)
                    {
                        if (inCavern(x, y, z))
                        {
                            carve(x, y, z);
                        }
                    }
                }
            }
        }

        void floor()
        {
            for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++)
            {
                for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
                {
                    if (dx * dx + dz * dz <= LairShape.FLOOR_EDGE * LairShape.FLOOR_EDGE)
                    {
                        set(cx + dx, floor, cz + dz, floorBlock(dx, dz, cx + dx, cz + dz));
                    }
                }
            }
        }

        // ------------------------------------------------------------ 5. the tunnel

        void tunnel()
        {
            BlockState bricks = Blocks.NETHER_BRICKS.defaultBlockState();
            BlockState slab = Blocks.NETHER_BRICK_SLAB.defaultBlockState();
            for (int s = FLOOR_RADIUS + 1; s <= mouth; s++)
            {
                double walk = floor + LairShape.walkTop(s, descent);
                boolean half = walk != Math.floor(walk);
                int support = half ? (int) Math.floor(walk) : (int) walk - 1;
                int airFrom = support + 1;
                int roof = airFrom + TUNNEL_HEIGHT;
                int bottom = half ? support - 2 : support - 1;
                boolean light = (s - FLOOR_RADIUS) % TUNNEL_LIGHT_SPACING == 0 && s > FLOOR_RADIUS + 2 && s < mouth;
                for (int l = -TUNNEL_HALF_WIDTH - 1; l <= TUNNEL_HALF_WIDTH + 1; l++)
                {
                    int x = tx(s, l);
                    int z = tz(s, l);
                    if (Math.abs(l) <= TUNNEL_HALF_WIDTH)
                    {
                        set(x, bottom, z, rock(x, bottom, z));
                        if (half)
                        {
                            set(x, support - 1, z, bricks);
                            set(x, support, z, slab);
                        }
                        else
                        {
                            set(x, support, z, bricks);
                        }
                        for (int y = airFrom; y < roof; y++)
                        {
                            carve(x, y, z);
                        }
                        set(x, roof, z, rock(x, roof, z));
                    }
                    else
                    {
                        for (int y = bottom; y <= roof; y++)
                        {
                            set(x, y, z, light && y == airFrom + 1 ? Blocks.MAGMA_BLOCK.defaultBlockState() : rock(x, y, z));
                        }
                    }
                    fillDown(x, bottom - 1, z);
                }
                if (s == mouth)
                {
                    arch(s, bottom, airFrom, roof);
                }
            }
        }

        /** The mouth's arch: polished basalt pillars, a blackstone lintel, two lanterns hanging under it. */
        void arch(int s, int bottom, int airFrom, int roof)
        {
            for (int l : new int[] {-TUNNEL_HALF_WIDTH - 1, TUNNEL_HALF_WIDTH + 1})
            {
                for (int y = bottom; y < roof; y++)
                {
                    set(tx(s, l), y, tz(s, l), Blocks.POLISHED_BASALT.defaultBlockState());
                }
            }
            for (int l = -TUNNEL_HALF_WIDTH - 1; l <= TUNNEL_HALF_WIDTH + 1; l++)
            {
                set(tx(s, l), roof, tz(s, l), (l == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS)
                        .defaultBlockState());
            }
            BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            for (int l : new int[] {-TUNNEL_HALF_WIDTH, TUNNEL_HALF_WIDTH})
            {
                set(tx(s, l), roof - 1, tz(s, l), lantern);
            }
        }

        /** The level approach in front of the mouth: nether bricks at the mouth's height, cut open to the sky above. */
        void approach()
        {
            int ground = floor + descent;
            for (int s = mouth + 1; s <= entranceS; s++)
            {
                for (int l = -TUNNEL_HALF_WIDTH; l <= TUNNEL_HALF_WIDTH; l++)
                {
                    int x = tx(s, l);
                    int z = tz(s, l);
                    set(x, ground, z, Blocks.NETHER_BRICKS.defaultBlockState());
                    fillDown(x, ground - 1, z);
                    int top = Math.max(surface(x, z), ground + TUNNEL_HEIGHT);
                    for (int y = ground + 1; y <= top; y++)
                    {
                        carve(x, y, z);
                    }
                }
            }
        }

        // ------------------------------------------------------------ 6. the skylight

        void skylight()
        {
            int reach = (int) Math.ceil(SKYLIGHT_RADIUS + SKYLIGHT_LINING + SKYLIGHT_RIM);
            // The shaft.
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    if (Math.sqrt(dx * dx + dz * dz) > SKYLIGHT_RADIUS)
                    {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int top = surface(x, z);
                    for (int y = floor + 1; y <= top; y++)
                    {
                        carve(x, y, z);
                    }
                }
            }
            // Its lining.
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r <= SKYLIGHT_RADIUS || r > SKYLIGHT_RADIUS + SKYLIGHT_LINING)
                    {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int top = surface(x, z);
                    for (int y = floor + (int) Math.floor(LairShape.ceiling(r)); y <= top; y++)
                    {
                        BlockState state = get(x, y, z);
                        if (!state.isAir() && !inCavern(x, y, z))
                        {
                            set(x, y, z, rock(x, y, z));
                        }
                    }
                }
            }
            // The low wall round its top.
            BlockState wall = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r <= SKYLIGHT_RADIUS || r > SKYLIGHT_RADIUS + SKYLIGHT_RIM)
                    {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    int y = surface(x, z) + 1;
                    if (y > floor + LairShape.ceiling(r) + 1 && get(x, y, z).isAir())
                    {
                        set(x, y, z, wall);
                        walls.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        // ------------------------------------------------------------ 7. safety

        /** Any fluid touching the open space becomes rock, so no lava or water can flow in. */
        void sealFluids()
        {
            LongArrayList open = new LongArrayList(carved);
            for (int i = 0; i < open.size(); i++)
            {
                BlockPos at = BlockPos.of(open.getLong(i));
                for (Direction d : Direction.values())
                {
                    BlockPos next = at.relative(d);
                    if (carved.contains(next.asLong()))
                    {
                        continue;
                    }
                    BlockState state = level.getBlockState(next);
                    if (!state.getFluidState().isEmpty() && !state.hasBlockEntity())
                    {
                        set(next.getX(), next.getY(), next.getZ(), rock(next.getX(), next.getY(), next.getZ()));
                        sealed++;
                    }
                }
            }
        }

        /** Plants and other blocks left without what holds them up (an ash layer on a cut block...) are removed. */
        void dropUnsupported()
        {
            LongArrayList open = new LongArrayList(carved);
            Direction[] around = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            for (int i = 0; i < open.size(); i++)
            {
                BlockPos at = BlockPos.of(open.getLong(i));
                for (Direction d : around)
                {
                    BlockPos next = at.relative(d);
                    while (!carved.contains(next.asLong()))
                    {
                        BlockState state = level.getBlockState(next);
                        if (state.isAir() || state.canSurvive(level, next))
                        {
                            break;
                        }
                        set(next.getX(), next.getY(), next.getZ(), AIR);
                        plants++;
                        next = next.above();
                    }
                }
            }
        }

        // ------------------------------------------------------------ 8. decoration

        /** Glowing magma veins wandering up the walls and over the dome. */
        void veins()
        {
            double tunnelAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            for (int k = 0; k < MAGMA_VEINS; k++)
            {
                double theta = random.nextDouble() * 2.0D * Math.PI;
                for (int tries = 0; tries < 8 && angleBetween(theta, tunnelAngle) < Math.toRadians(VEIN_TUNNEL_CLEARANCE_DEGREES); tries++)
                {
                    theta = random.nextDouble() * 2.0D * Math.PI;
                }
                int startY = 2 + random.nextInt(4);
                int length = VEIN_MIN_LENGTH + random.nextInt(VEIN_MAX_LENGTH - VEIN_MIN_LENGTH + 1);
                boolean any = false;
                for (int i = 0; i < length; i++)
                {
                    int dy = startY + i;
                    double radius = dy <= WALL_HEIGHT ? LairShape.FLOOR_EDGE
                            : LairShape.FLOOR_EDGE * Math.sqrt(Math.max(0.0D, 1.0D - Math.pow((dy - WALL_HEIGHT) / (double) (DOME_HEIGHT - WALL_HEIGHT), 2.0D)));
                    if (radius < SKYLIGHT_RADIUS + SKYLIGHT_LINING + 1.0D)
                    {
                        break;
                    }
                    for (double r = radius - 1.0D; r <= radius + 3.0D; r += 0.5D)
                    {
                        int x = cx + (int) Math.round(r * Math.cos(theta));
                        int z = cz + (int) Math.round(r * Math.sin(theta));
                        BlockState state = get(x, floor + dy, z);
                        if (state.isAir())
                        {
                            continue;
                        }
                        if (isLairRock(state))
                        {
                            set(x, floor + dy, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                            any = true;
                        }
                        break;
                    }
                    theta += (random.nextDouble() - 0.5D) * 2.0D * VEIN_WOBBLE;
                }
                if (any)
                {
                    veins++;
                }
            }
        }

        /**
         * Lava basins at the foot of the wall: three lava blocks just outside the floor's edge, one block up, in a niche,
         * behind a low wall. The lava has rock under it and round it, and walls in front, so it can't flow anywhere.
         */
        void basins()
        {
            double tunnelAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            BlockState lava = Blocks.LAVA.defaultBlockState();
            BlockState wall = Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState();
            for (int k = 0; k < LAVA_BASINS; k++)
            {
                double angle = tunnelAngle + Math.PI / LAVA_BASINS + k * 2.0D * Math.PI / LAVA_BASINS;
                if (angleBetween(angle, tunnelAngle) < Math.toRadians(BASIN_TUNNEL_CLEARANCE_DEGREES))
                {
                    continue;
                }
                double ux = Math.cos(angle);
                double uz = Math.sin(angle);
                LongOpenHashSet cells = new LongOpenHashSet();
                List<BlockPos> list = new ArrayList<>();
                for (int t = -1; t <= 1; t++)
                {
                    int dx = (int) Math.round((FLOOR_RADIUS + 1) * ux - t * uz);
                    int dz = (int) Math.round((FLOOR_RADIUS + 1) * uz + t * ux);
                    if (dx * dx + dz * dz <= LairShape.FLOOR_EDGE * LairShape.FLOOR_EDGE)
                    {
                        continue;
                    }
                    BlockPos cell = new BlockPos(cx + dx, floor + 1, cz + dz);
                    if (cells.add(cell.asLong()))
                    {
                        list.add(cell);
                    }
                }
                if (list.isEmpty())
                {
                    continue;
                }
                for (BlockPos cell : list)
                {
                    set(cell.getX(), floor, cell.getZ(), rock(cell.getX(), floor, cell.getZ()));
                    set(cell.getX(), cell.getY(), cell.getZ(), lava);
                    for (int y = cell.getY() + 1; y <= cell.getY() + BASIN_NICHE_HEIGHT; y++)
                    {
                        set(cell.getX(), y, cell.getZ(), AIR);
                    }
                }
                for (BlockPos cell : list)
                {
                    for (Direction d : Direction.Plane.HORIZONTAL)
                    {
                        BlockPos next = cell.relative(d);
                        if (cells.contains(next.asLong()))
                        {
                            continue;
                        }
                        BlockState state = level.getBlockState(next);
                        if (!fillable(state))
                        {
                            continue;
                        }
                        int dx = next.getX() - cx;
                        int dz = next.getZ() - cz;
                        if (dx * dx + dz * dz <= LairShape.FLOOR_EDGE * LairShape.FLOOR_EDGE)
                        {
                            set(next.getX(), next.getY(), next.getZ(), wall);
                            walls.add(next.immutable());
                        }
                        else
                        {
                            set(next.getX(), next.getY(), next.getZ(), rock(next.getX(), next.getY(), next.getZ()));
                        }
                    }
                }
                basins++;
            }
        }

        /** Walls join up with their neighbours (they were placed without updating their shapes). */
        void updateWalls()
        {
            for (BlockPos at : walls)
            {
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof WallBlock)
                {
                    BlockState shaped = Block.updateFromNeighbourShapes(state, level, at);
                    if (shaped != state)
                    {
                        level.setBlock(at, shaped, FLAGS);
                    }
                }
            }
        }

        /** The angle between two directions (radians, 0 to pi). */
        static double angleBetween(double a, double b)
        {
            return Math.abs(Math.IEEEremainder(a - b, 2.0D * Math.PI));
        }
    }
}
