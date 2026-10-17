package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A sewer entrance: a big vertical copper pipe (green, oxidised, like a Mario pipe) standing out of the Blightwood
 * ground, with a wider rim at its top and a few stone steps up to it. Its 3 x 3 opening drops straight down the shaft into
 * the pool room below (the pool breaks your fall); a ladder runs up the inside wall on the steps' side from just over the
 * pool to the rim, so you can climb back out.
 */
public final class SewerPipePiece extends SewerPiece implements SewerEntrance
{
    private final int x;
    private final int z;
    /** The ground's top block under the pipe's body (the highest of them). */
    private final int groundY;
    /** The side the steps go down (and the ladder is on). */
    private final Direction steps;

    SewerPipePiece(int x, int z, int groundY, Direction steps, int floorY, long seed)
    {
        super(ModStructures.SEWER_PIPE.get(), box(x, z, groundY, floorY), floorY, seed);
        this.x = x;
        this.z = z;
        this.groundY = groundY;
        this.steps = steps;
    }

    public SewerPipePiece(CompoundTag tag)
    {
        super(ModStructures.SEWER_PIPE.get(), tag);
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

    /** How far the steps reach out from the pipe's middle. */
    static final int STEPS_REACH = (int) Math.floor(SewerTuning.PIPE_RIM_RADIUS) + 1 + SewerTuning.PIPE_BODY_RISE + SewerTuning.PIPE_RIM_HEIGHT;
    /** The steps' supports go at most this far down to the ground. */
    private static final int MAX_SUPPORT = 8;

    private static BoundingBox box(int x, int z, int groundY, int floorY)
    {
        int reach = STEPS_REACH + 1;
        return new BoundingBox(x - reach, Math.min(floorY + 1, groundY - MAX_SUPPORT), z - reach, x + reach, rimTop(groundY) + 4, z + reach);
    }

    /** The rim's top block (you stand on it at rimTop + 1). */
    static int rimTop(int groundY)
    {
        return groundY + SewerTuning.PIPE_BODY_RISE + SewerTuning.PIPE_RIM_HEIGHT;
    }

    @Override
    public BlockPos opening()
    {
        return new BlockPos(this.x, rimTop(this.groundY) + 1, this.z);
    }

    @Override
    public int groundY()
    {
        return this.groundY;
    }

    /** The rim's top block (you stand on the rim at this + 1; the opening's air starts there too). */
    @Override
    public int rimTopY()
    {
        return rimTop(this.groundY);
    }

    /** The network's floor height (the pool's water is level with it, down to floor - POOL_DEPTH + 1). */
    public int floorLevel()
    {
        return this.floorY;
    }

    @Override
    public BlockPos centre()
    {
        return new BlockPos(this.x, this.floorY, this.z);
    }

    @Override
    public Direction stepsSide()
    {
        return this.steps;
    }

    /** The ladder's column (inside the shaft, against the wall on the steps' side). */
    @Override
    public BlockPos ladderColumn()
    {
        return new BlockPos(this.x + this.steps.getStepX() * SewerTuning.PIPE_OPENING_HALF, 0, this.z + this.steps.getStepZ() * SewerTuning.PIPE_OPENING_HALF);
    }

    @Override
    public int openingHalf()
    {
        return SewerTuning.PIPE_OPENING_HALF;
    }

    @Override
    public boolean grand()
    {
        return false;
    }

    /** On the ground just past the foot of its steps (the block your feet are in). */
    @Override
    public BlockPos approach()
    {
        int out = (int) Math.floor(SewerTuning.PIPE_RIM_RADIUS) + 1 + SewerTuning.PIPE_BODY_RISE + SewerTuning.PIPE_RIM_HEIGHT;
        return new BlockPos(this.x + this.steps.getStepX() * out, this.groundY + 1, this.z + this.steps.getStepZ() * out);
    }

    @Override
    public BoundingBox box()
    {
        return getBoundingBox();
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot)
    {
        int f = this.floorY;
        int g = this.groundY;
        int bodyTop = g + SewerTuning.PIPE_BODY_RISE;
        int rimTop = rimTop(g);
        int open = SewerTuning.PIPE_OPENING_HALF;
        int lowest = f + 1 + SewerTuning.PIPE_END_ABOVE_WATER - 1;   // the pipe's lowest copper ring, over the pool
        double bodyR2 = SewerTuning.PIPE_BODY_RADIUS * SewerTuning.PIPE_BODY_RADIUS;
        double rimR2 = SewerTuning.PIPE_RIM_RADIUS * SewerTuning.PIPE_RIM_RADIUS;
        BlockPos ladder = ladderColumn();
        BlockPos backing = ladder.relative(this.steps);
        BlockState ladderState = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, this.steps.getOpposite());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int r = (int) Math.ceil(SewerTuning.PIPE_RIM_RADIUS);
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
                boolean body = !opening && d2 <= bodyR2;
                boolean rim = !opening && d2 <= rimR2;
                boolean isLadder = bx == ladder.getX() && bz == ladder.getZ();
                boolean isBacking = bx == backing.getX() && bz == backing.getZ();
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
                    // the pipe's wall, from over the pool up to the top of the body; the ladder's backing goes down to the water
                    int from = isBacking ? f + 1 : lowest;
                    for (int y = from; y <= bodyTop; y++)
                    {
                        boolean band = (bodyTop - y) % SewerTuning.PIPE_BAND_EVERY == SewerTuning.PIPE_BAND_EVERY - 1;
                        place(level, chunkBox, pos, bx, y, bz, pick(band ? SewerTuning.PIPE_BAND : SewerTuning.PIPE_BODY, bx, y, bz));
                    }
                }
                if (rim)
                {
                    for (int y = bodyTop + 1; y <= rimTop; y++)
                    {
                        place(level, chunkBox, pos, bx, y, bz, pick(y == rimTop ? SewerTuning.PIPE_BAND : SewerTuning.PIPE_RIM, bx, y, bz));
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
                        for (int y = g - 1; y >= g - MAX_SUPPORT; y--)
                        {
                            if (isGround(level.getBlockState(pos.set(bx, y, bz))))
                            {
                                break;
                            }
                            place(level, chunkBox, pos, bx, y, bz, pick(SewerTuning.STEP_SUPPORT, bx, y, bz));
                        }
                    }
                }
            }
        }
        buildSteps(level, chunkBox, pos, rimTop);
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

    /**
     * The steps up to the rim: a short stair of stone brick stairs going down and out from the rim on the steps' side,
     * each with a support of cobblestone under it down to the ground (where the ground is lower).
     */
    private void buildSteps(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int rimTop)
    {
        int first = (int) Math.floor(SewerTuning.PIPE_RIM_RADIUS) + 1;
        int count = SewerTuning.PIPE_BODY_RISE + SewerTuning.PIPE_RIM_HEIGHT;
        Direction across = this.steps.getClockWise();
        int halfWidth = SewerTuning.PIPE_STEPS_WIDTH / 2;
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
                BlockState here = level.getBlockState(pos.set(bx, stairY, bz));
                if (isGround(here))
                {
                    continue; // the ground is already this high here: no step needed
                }
                boolean mossy = unit(bx, stairY, bz, 31) < 0.45D;
                BlockState stair = (mossy ? Blocks.MOSSY_STONE_BRICK_STAIRS : Blocks.STONE_BRICK_STAIRS).defaultBlockState()
                        .setValue(StairBlock.FACING, this.steps.getOpposite()).setValue(StairBlock.HALF, Half.BOTTOM);
                place(level, chunkBox, pos, bx, stairY, bz, stair);
                clearAbove(level, chunkBox, pos, bx, bz, stairY + 1);
                for (int y = stairY - 1; y >= stairY - MAX_SUPPORT; y--)
                {
                    if (isGround(level.getBlockState(pos.set(bx, y, bz))))
                    {
                        break;
                    }
                    place(level, chunkBox, pos, bx, y, bz, pick(SewerTuning.STEP_SUPPORT, bx, y, bz));
                }
            }
        }
    }

    /** Solid ground (not air, a plant, water, leaves or a log). */
    private static boolean isGround(BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.canBeReplaced() && !state.is(BlockTags.LEAVES)
                && !state.is(BlockTags.LOGS) && state.isSolid();
    }
}
