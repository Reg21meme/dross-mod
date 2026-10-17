package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One straight sewer tunnel between two junctions, along X or Z.
 * <ul>
 *   <li>A <b>main</b> tunnel is {@value SewerTuning#MAIN_WIDTH} wide and {@value SewerTuning#MAIN_HEIGHT} tall with an
 *       arched roof and a channel of sewer water down its middle (the pipes' water running to the middle hall).</li>
 *   <li>In the Rat King's sewer the main tunnel is the <b>royal road</b> instead (the user's pick, October 16): no
 *       channel, a deepslate floor, red banners (his cape's colour) on its walls, lit green candles along its edges, bone
 *       piles, raw gold and gold coins (light weighted pressure plates). No carpet: a runner to his nest showed the way
 *       through the maze (the user's request after playing it).</li>
 *   <li>A <b>side</b> tunnel is {@value SewerTuning#SIDE_WIDTH} x {@value SewerTuning#SIDE_HEIGHT}, dry, with more sludge
 *       and the odd cobweb.</li>
 * </ul>
 * Every tunnel has a little glow lichen on its walls ({@link SewerTuning#LICHEN_TUNNELS}). It runs from one junction's
 * wall to the other's (its ends open the junctions' walls), so the junctions themselves are other pieces.
 */
public final class SewerTunnelPiece extends SewerPiece
{
    /** True: along X (the fixed coordinate is z); false: along Z. */
    private final boolean alongX;
    /** The tunnel's middle line (z for an X tunnel, x for a Z tunnel). */
    private final int line;
    /** First and last block along the tunnel (inclusive). */
    private final int from;
    private final int to;
    private final boolean main;
    /** The Rat King's royal road (a main tunnel of his sewer). */
    private final boolean royal;
    /** A tunnel into the rat cave (the cave carves its end, so no lichen on it). */
    private final boolean door;

    SewerTunnelPiece(boolean alongX, int line, int from, int to, boolean main, boolean royal, boolean door, int floorY, long seed)
    {
        super(ModStructures.SEWER_TUNNEL.get(), box(alongX, line, from, to, main, floorY), floorY, seed);
        this.alongX = alongX;
        this.line = line;
        this.from = from;
        this.to = to;
        this.main = main;
        this.royal = royal && main;
        this.door = door;
    }

    public SewerTunnelPiece(CompoundTag tag)
    {
        super(ModStructures.SEWER_TUNNEL.get(), tag);
        this.alongX = tag.getBoolean("AlongX");
        this.line = tag.getInt("Line");
        this.from = tag.getInt("From");
        this.to = tag.getInt("To");
        this.main = tag.getBoolean("Main");
        this.royal = tag.getBoolean("Royal");
        this.door = tag.getBoolean("Door");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        super.addAdditionalSaveData(context, tag);
        tag.putBoolean("AlongX", this.alongX);
        tag.putInt("Line", this.line);
        tag.putInt("From", this.from);
        tag.putInt("To", this.to);
        tag.putBoolean("Main", this.main);
        tag.putBoolean("Royal", this.royal);
        tag.putBoolean("Door", this.door);
    }

    static int half(boolean main)
    {
        return (main ? SewerTuning.MAIN_WIDTH : SewerTuning.SIDE_WIDTH) / 2;
    }

    static int height(boolean main)
    {
        return main ? SewerTuning.MAIN_HEIGHT : SewerTuning.SIDE_HEIGHT;
    }

    private static BoundingBox box(boolean alongX, int line, int from, int to, boolean main, int floorY)
    {
        int reach = half(main) + 1;
        int top = floorY + height(main) + 1;
        int bottom = floorY - 1;
        return alongX ? new BoundingBox(from, bottom, line - reach, to, top, line + reach)
                : new BoundingBox(line - reach, bottom, from, line + reach, top, to);
    }

    public boolean isMain()
    {
        return this.main;
    }

    /** True for the Rat King's royal road. */
    public boolean isRoyal()
    {
        return this.royal;
    }

    /** The tunnel's middle line's two ends at floor + 1 (for checks). */
    public BlockPos start()
    {
        return this.alongX ? new BlockPos(this.from, this.floorY + 1, this.line) : new BlockPos(this.line, this.floorY + 1, this.from);
    }

    public BlockPos end()
    {
        return this.alongX ? new BlockPos(this.to, this.floorY + 1, this.line) : new BlockPos(this.line, this.floorY + 1, this.to);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot)
    {
        int half = half(this.main);
        int height = height(this.main);
        int f = this.floorY;
        boolean thinPipe = this.main && !this.royal && unit(this.from, f, this.line, 11) < SewerTuning.THIN_PIPE_CHANCE;
        int thinPipeSide = unit(this.from, f, this.line, 12) < 0.5D ? -half : half;
        double sludge = this.main ? SewerTuning.SLUDGE_MAIN_TUNNELS : SewerTuning.SLUDGE_SIDE_TUNNELS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int u = this.from; u <= this.to; u++)
        {
            for (int v = -half - 1; v <= half + 1; v++)
            {
                int x = this.alongX ? u : this.line + v;
                int z = this.alongX ? this.line + v : u;
                if (!chunkBox.isInside(x, f, z))
                {
                    continue;
                }
                int side = Math.abs(v);
                if (side <= half)
                {
                    // the floor: a channel of water down the middle of a main tunnel, its bed below (the royal road has none)
                    if (this.royal)
                    {
                        place(level, chunkBox, pos, x, f, z, floor(x, z, sludge, SewerTuning.ROYAL_FLOOR));
                    }
                    else if (this.main && v == 0)
                    {
                        place(level, chunkBox, pos, x, f, z, water());
                        place(level, chunkBox, pos, x, f - 1, z, pick(SewerTuning.BASINS, x, f - 1, z));
                    }
                    else
                    {
                        place(level, chunkBox, pos, x, f, z, floor(x, z, sludge, SewerTuning.FLOORS));
                    }
                    for (int y = f + 1; y <= f + height; y++)
                    {
                        boolean archCorner = this.main && y == f + height && side == half;
                        if (archCorner)
                        {
                            place(level, chunkBox, pos, x, y, z, pick(SewerTuning.ROOFS, x, y, z));
                        }
                        else if (this.royal && y == f + 1)
                        {
                            place(level, chunkBox, pos, x, y, z, royalFitting(x, z, u, v, half));
                        }
                        else
                        {
                            place(level, chunkBox, pos, x, y, z, fitting(x, y, z, v, half, height, thinPipe, thinPipeSide));
                        }
                    }
                    shell(level, chunkBox, pos, x, f + height + 1, z, pick(SewerTuning.ROOFS, x, f + height + 1, z));
                }
                else
                {
                    // the walls (and the floor under them)
                    shell(level, chunkBox, pos, x, f, z, pick(SewerTuning.FLOORS, x, f, z));
                    for (int y = f + 1; y <= f + height; y++)
                    {
                        BlockState wall;
                        if (y == f + 1 && unit(x, y, z, 13) < SewerTuning.GRATE_CHANCE)
                        {
                            wall = grate();
                        }
                        else
                        {
                            wall = y == f + 1 ? pick(SewerTuning.WALL_FOOT, x, y, z) : pick(SewerTuning.WALLS, x, y, z);
                        }
                        shell(level, chunkBox, pos, x, y, z, wall);
                    }
                    shell(level, chunkBox, pos, x, f + height + 1, z, pick(SewerTuning.ROOFS, x, f + height + 1, z));
                }
            }
        }
        // Then what hangs on the walls (now they're there): banners on the royal road, glow lichen.
        for (int u = this.from; u <= this.to; u++)
        {
            for (int sign = -1; sign <= 1; sign += 2)
            {
                int v = sign * half;
                int x = this.alongX ? u : this.line + v;
                int z = this.alongX ? this.line + v : u;
                if (!chunkBox.isInside(x, f, z))
                {
                    continue;
                }
                Direction toWall = this.alongX ? (sign > 0 ? Direction.SOUTH : Direction.NORTH) : (sign > 0 ? Direction.EAST : Direction.WEST);
                // (each wall every ROYAL_BANNER_EVERY blocks, the two walls half a period apart: they alternate)
                if (this.royal && Math.floorMod(u + (sign > 0 ? 0 : SewerTuning.ROYAL_BANNER_EVERY / 2), SewerTuning.ROYAL_BANNER_EVERY) == 0
                        && u > this.from && u < this.to)
                {
                    banner(level, chunkBox, pos, x, f + SewerTuning.MAIN_HEIGHT - 1, z, toWall);
                }
                if (this.door || u < this.from + SewerTuning.LICHEN_END_SKIP || u > this.to - SewerTuning.LICHEN_END_SKIP)
                {
                    continue;
                }
                for (int y = f + 2; y <= f + height - 1; y++)
                {
                    if (unit(x, y, z, 17) < SewerTuning.LICHEN_TUNNELS)
                    {
                        lichen(level, chunkBox, pos, x, y, z, toWall);
                    }
                }
            }
        }
    }

    /** What fills the tunnel's air: air, or now and then a cobweb or a stretch of thin pipe under the roof. */
    private BlockState fitting(int x, int y, int z, int v, int half, int height, boolean thinPipe, int thinPipeSide)
    {
        int f = this.floorY;
        if (thinPipe && v == thinPipeSide && y == f + height - 1)
        {
            // a thin copper pipe laid along the wall, just under the roof's arch
            return Blocks.LIGHTNING_ROD.defaultBlockState().setValue(LightningRodBlock.FACING, this.alongX ? Direction.EAST : Direction.SOUTH);
        }
        if (!this.main && y == f + height && Math.abs(v) == half && unit(x, y, z, 14) < SewerTuning.COBWEB_CHANCE)
        {
            return Blocks.COBWEB.defaultBlockState();
        }
        return air();
    }

    /**
     * The royal road at the floor: along its edges lit green candles every few blocks, bone piles, raw gold and gold coins;
     * its middle stays clear.
     */
    private BlockState royalFitting(int x, int z, int u, int v, int half)
    {
        int f = this.floorY;
        if (Math.abs(v) != half || u <= this.from || u >= this.to)
        {
            return air();
        }
        // (each edge every ROYAL_CANDLE_EVERY blocks, the two edges half a period apart: they alternate)
        if (Math.floorMod(u + (v > 0 ? 0 : SewerTuning.ROYAL_CANDLE_EVERY / 2), SewerTuning.ROYAL_CANDLE_EVERY) == 0)
        {
            return candles(1 + (int) (unit(x, f + 1, z, 24) * SewerTuning.ROYAL_CANDLES_MAX));
        }
        double roll = unit(x, f + 1, z, 25);
        if (roll < SewerTuning.ROYAL_BONES)
        {
            return Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, unit(x, f + 1, z, 26) < 0.5D ? Direction.Axis.X : Direction.Axis.Z);
        }
        if (roll < SewerTuning.ROYAL_BONES + SewerTuning.ROYAL_RAW_GOLD)
        {
            return Blocks.RAW_GOLD_BLOCK.defaultBlockState();
        }
        // gold coins, only on the stone floor (the edge's sludge patches sink them out of sight)
        if (roll < SewerTuning.ROYAL_BONES + SewerTuning.ROYAL_RAW_GOLD + SewerTuning.ROYAL_GOLD_COINS && !sludgeAt(x, z, SewerTuning.SLUDGE_MAIN_TUNNELS))
        {
            return Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState();
        }
        return air();
    }

    /** An iron grate set in the wall (a side drain), joined to the wall on both sides. */
    private BlockState grate()
    {
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        return this.alongX ? bars.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true)
                : bars.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
    }
}
