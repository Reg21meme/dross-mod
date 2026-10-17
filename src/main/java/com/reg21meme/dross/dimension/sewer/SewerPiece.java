package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.registry.ModBlocks;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.FluidState;

/**
 * What every piece of a sewer network shares: the network's floor height and its seed, and the three ways a piece puts
 * down a block.
 * <ul>
 *   <li>{@link #place}: always (the piece's own air, floor, water and fittings).</li>
 *   <li>{@link #shell}: only into the natural rock round it (walls and roofs). So where two pieces meet, one's wall never
 *       blocks the other's tunnel or overwrites the other's wall: the opening between them stays open.</li>
 *   <li>Every block's "random" look is fixed by its position and the network's seed ({@link SewerNoise}), so a piece
 *       built across several chunks (each placing its own part) matches at every chunk border.</li>
 * </ul>
 */
abstract class SewerPiece extends StructurePiece
{
    /** The floor's height: the floor blocks are at this y, you walk at floorY + 1. The whole network shares it. */
    protected final int floorY;
    /** The network's own seed (each piece adds its own salt). */
    protected final long seed;

    private static Set<Block> built;

    protected SewerPiece(StructurePieceType type, BoundingBox box, int floorY, long seed)
    {
        super(type, 0, box);
        this.floorY = floorY;
        this.seed = seed;
    }

    protected SewerPiece(StructurePieceType type, CompoundTag tag)
    {
        super(type, tag);
        this.floorY = tag.getInt("Floor");
        this.seed = tag.getLong("Seed");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.putInt("Floor", this.floorY);
        tag.putLong("Seed", this.seed);
    }

    /** Puts the block down if it's inside this chunk's part of the piece (and lets placed water settle once the chunk is live). */
    protected static void place(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state)
    {
        if (!chunkBox.isInside(x, y, z))
        {
            return;
        }
        pos.set(x, y, z);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty())
        {
            level.scheduleTick(pos, fluid.getType(), 0);
        }
    }

    /** Puts the block down only into natural rock (never into air, water or anything a sewer piece already built). */
    protected static void shell(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int y, int z, BlockState state)
    {
        if (!chunkBox.isInside(x, y, z))
        {
            return;
        }
        BlockState here = level.getBlockState(pos.set(x, y, z));
        if (isNatural(here))
        {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    /** True for the natural ground round the sewers: not air, not a fluid, and nothing the sewers build. */
    static boolean isNatural(BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !builtBlocks().contains(state.getBlock());
    }

    /** Every block the sewers put down. */
    static synchronized Set<Block> builtBlocks()
    {
        if (built == null)
        {
            Set<Block> blocks = new HashSet<>();
            for (SewerTuning.Mix mix : new SewerTuning.Mix[] {SewerTuning.WALLS, SewerTuning.WALL_FOOT, SewerTuning.ROOFS, SewerTuning.FLOORS,
                    SewerTuning.BASINS, SewerTuning.PILLARS, SewerTuning.CAVE_ROCK, SewerTuning.CAVE_FLOOR, SewerTuning.PIPE_BODY,
                    SewerTuning.PIPE_RIM, SewerTuning.PIPE_BAND, SewerTuning.PIPE_COLLAR, SewerTuning.STEP_SUPPORT, SewerTuning.NEST_WOOL,
                    SewerTuning.NEST_CARPETS, SewerTuning.DRAIN_RIM, SewerTuning.CROWN_BAND, SewerTuning.CROWN_COPPER, SewerTuning.RAT_NEST_CARPETS,
                    SewerTuning.GNAWED_FENCES, SewerTuning.ROYAL_FLOOR})
            {
                mix.states().forEach(s -> blocks.add(s.getBlock()));
            }
            blocks.add(ModBlocks.SEWER_SLUDGE.get());
            blocks.add(ModBlocks.DUSKSTONE_GOLD_ORE.get());
            blocks.add(ModBlocks.GLOOMCAP.get());
            blocks.add(Blocks.GOLD_BLOCK);
            blocks.add(Blocks.LADDER);
            blocks.add(Blocks.IRON_BARS);
            blocks.add(Blocks.LIGHTNING_ROD);
            blocks.add(Blocks.COBWEB);
            blocks.add(Blocks.POINTED_DRIPSTONE);
            blocks.add(Blocks.STONE_BRICK_STAIRS);
            blocks.add(Blocks.MOSSY_STONE_BRICK_STAIRS);
            blocks.add(ModBlocks.BLIGHT_VENT.get());
            blocks.add(Blocks.GLOW_LICHEN);
            blocks.add(Blocks.GREEN_CANDLE);
            blocks.add(Blocks.RED_WALL_BANNER);
            blocks.add(Blocks.BONE_BLOCK);
            blocks.add(Blocks.RAW_GOLD_BLOCK);
            blocks.add(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);
            built = blocks;
        }
        return built;
    }

    /** A number from 0 to 1 fixed by the position (and a salt, so different choices at the same block don't agree). */
    protected double unit(int x, int y, int z, int salt)
    {
        return SewerNoise.unit(this.seed + salt * 0x51ED27L, x, y, z);
    }

    /** The block of a mix for this position. */
    protected BlockState pick(SewerTuning.Mix mix, int x, int y, int z)
    {
        return mix.pick(unit(x, y, z, 7));
    }

    /** True where the floor here is Sewer Sludge: patches covering about {@code share} of this kind of floor. */
    protected boolean sludgeAt(int x, int z, double share)
    {
        return sludgeAt(x, z, share, SewerTuning.SLUDGE_PATCH_SIZE);
    }

    /** As {@link #sludgeAt(int, int, double)}, with patches about {@code patchSize} blocks across. */
    protected boolean sludgeAt(int x, int z, double share, double patchSize)
    {
        return SewerNoise.patches(this.seed ^ 0x5EB5EB5EL, x, z, patchSize) > SewerNoise.thresholdFor(share);
    }

    /** A floor block: Sewer Sludge in its patches, otherwise from the mix. */
    protected BlockState floor(int x, int z, double sludgeShare, SewerTuning.Mix mix)
    {
        return sludgeAt(x, z, sludgeShare) ? ModBlocks.SEWER_SLUDGE.get().defaultBlockState() : pick(mix, x, this.floorY, z);
    }

    protected static BlockState water()
    {
        return Blocks.WATER.defaultBlockState();
    }

    /** A lit green candle (or 2 to 4 together). */
    protected static BlockState candles(int count)
    {
        return Blocks.GREEN_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, Math.max(1, Math.min(4, count))).setValue(CandleBlock.LIT, true);
    }

    /**
     * Glow lichen on the {@code toWall} side of the air block at (x, y, z): only if that block is air (in this chunk) and
     * the wall there is solid now. Its light is 7.
     */
    protected static void lichen(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int y, int z, Direction toWall)
    {
        if (!chunkBox.isInside(x, y, z) || !level.getBlockState(pos.set(x, y, z)).isAir())
        {
            return;
        }
        BlockPos wall = new BlockPos(x, y, z).relative(toWall);
        if (!level.getBlockState(wall).isFaceSturdy(level, wall, toWall.getOpposite()))
        {
            return;
        }
        level.setBlock(pos.set(x, y, z), Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(toWall), true), Block.UPDATE_CLIENTS);
    }

    /**
     * A red banner (the Rat King's cape's colour) hanging on the {@code toWall} side of the air block at (x, y, z),
     * tattered: a black jagged hem and a frayed black border. Only into air, against a solid block.
     */
    protected static void banner(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int y, int z, Direction toWall)
    {
        banner(level, chunkBox, pos, x, y, z, toWall, false);
    }

    /**
     * As {@link #banner(WorldGenLevel, BoundingBox, BlockPos.MutableBlockPos, int, int, int, Direction)}; with
     * {@code ownWall} the wall is this piece's own (a pipe's rim), so it isn't checked: it may lie in the next chunk,
     * not built yet (the tidy-up, {@code TidyUpFeature.isBuiltFitting}, leaves the banner alone until it is).
     */
    protected static void banner(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int x, int y, int z, Direction toWall,
                                 boolean ownWall)
    {
        if (!chunkBox.isInside(x, y, z) || !level.getBlockState(pos.set(x, y, z)).isAir())
        {
            return;
        }
        BlockPos at = new BlockPos(x, y, z);
        if (!ownWall && !level.getBlockState(at.relative(toWall)).isSolid())
        {
            return;
        }
        level.setBlock(at, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, toWall.getOpposite()), Block.UPDATE_CLIENTS);
        tatter(level, at);
    }

    /** Gives the (red) banner at pos its tattered look: the block entity's patterns, set while the chunk is still being made. */
    private static void tatter(WorldGenLevel level, BlockPos pos)
    {
        ChunkAccess chunk = level.getChunk(pos);
        if (chunk.getStatus().getChunkType() != ChunkStatus.ChunkType.PROTOCHUNK)
        {
            return;   // (never: structures are built while the chunk is being made; then it just stays plain red)
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:banner");
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        ListTag patterns = new ListTag();
        for (String pattern : TATTERED_PATTERNS)
        {
            CompoundTag p = new CompoundTag();
            p.putString("Pattern", pattern);
            p.putInt("Color", DyeColor.BLACK.getId());
            patterns.add(p);
        }
        tag.put("Patterns", patterns);
        chunk.setBlockEntityNbt(tag);
    }

    /** The banner patterns that make a red banner look tattered: a jagged bottom edge ("bts") and a frayed border ("cbo"). */
    private static final String[] TATTERED_PATTERNS = {"bts", "cbo"};

    protected static BlockState air()
    {
        return Blocks.AIR.defaultBlockState();
    }
}
