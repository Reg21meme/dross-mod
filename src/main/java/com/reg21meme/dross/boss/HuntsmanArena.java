package com.reg21meme.dross.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Huntsman's basic arena ({@code /dross boss arena huntsman}; the user, October 20, 2026: "let him just be a spawn
 * egg with a basic arena"): a flat round floor of snow, {@link #RADIUS} blocks out from the middle (41 across: room for
 * the moose's charge and its circling hunt), with patches of packed ice (the shapes of {@link RatKingArena#noise}, about
 * {@link #ICE_SHARE} of it), and lantern posts round its edge so nothing spawns on it at night. The air over it is
 * cleared {@link #CLEAR_HEIGHT} blocks up (blocks with a block entity, like chests, are left alone) and gaps under it are
 * filled with snow up to {@link #FILL_DEPTH} blocks down. His real home is a hunter village in Rimefall (later).
 */
public final class HuntsmanArena
{
    /** Blocks from the middle to the edge of the floor (2 x RADIUS + 1 = 41 across). */
    public static final int RADIUS = 20;
    /** Air is cleared this many blocks above the floor (his antlers in the saddle are about 7.7 blocks up; his arrow
     * rain comes down from 18 over his target). */
    public static final int CLEAR_HEIGHT = 22;
    public static final int FILL_DEPTH = 6;
    /** About this share of the floor is packed ice. */
    public static final double ICE_SHARE = 0.3D;
    /** Lantern posts round the edge: how many, how far out, how tall. */
    private static final int POSTS = 12;
    private static final int POST_RADIUS = 19;
    private static final int POST_HEIGHT = 2;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private HuntsmanArena() {}

    public static boolean inFloor(int dx, int dz)
    {
        return dx * dx + dz * dz <= (RADIUS + 0.5D) * (RADIUS + 0.5D);
    }

    /** Builds it with its floor at {@code floor} (the block under its middle). */
    public static RatKingArena.Result build(ServerLevel level, BlockPos floor)
    {
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        double threshold = threshold(floor);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        int kept = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                if (!inFloor(dx, dz))
                {
                    continue;
                }
                int x = floor.getX() + dx;
                int z = floor.getZ() + dz;
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++)
                {
                    pos.set(x, floor.getY() + dy, z);
                    BlockState here = level.getBlockState(pos);
                    if (here.isAir())
                    {
                        continue;
                    }
                    if (here.hasBlockEntity())
                    {
                        kept++;
                        continue;
                    }
                    level.setBlock(pos, air, FLAGS);
                    changed++;
                }
                pos.set(x, floor.getY(), z);
                if (!level.getBlockState(pos).hasBlockEntity())
                {
                    level.setBlock(pos, RatKingArena.noise(x, z) > threshold ? ice : snow, FLAGS);
                    changed++;
                }
                else
                {
                    kept++;
                }
                for (int dy = 1; dy <= FILL_DEPTH; dy++)
                {
                    pos.set(x, floor.getY() - dy, z);
                    BlockState below = level.getBlockState(pos);
                    if (below.hasBlockEntity() || (!below.canBeReplaced() && below.getFluidState().isEmpty()))
                    {
                        break;
                    }
                    level.setBlock(pos, snow, FLAGS);
                    changed++;
                }
            }
        }
        for (int i = 0; i < POSTS; i++)
        {
            double a = i * Math.PI * 2.0D / POSTS;
            int x = floor.getX() + Mth.floor(Math.cos(a) * POST_RADIUS + 0.5D);
            int z = floor.getZ() + Mth.floor(Math.sin(a) * POST_RADIUS + 0.5D);
            for (int y = 1; y <= POST_HEIGHT; y++)
            {
                level.setBlock(pos.set(x, floor.getY() + y, z), Blocks.SPRUCE_FENCE.defaultBlockState(), FLAGS);
                changed++;
            }
            level.setBlock(pos.set(x, floor.getY() + POST_HEIGHT + 1, z), Blocks.LANTERN.defaultBlockState(), FLAGS);
            changed++;
        }
        return new RatKingArena.Result(changed, kept);
    }

    /** The noise level above which a column is packed ice ({@link #ICE_SHARE} of the floor's columns are above it). */
    private static double threshold(BlockPos floor)
    {
        java.util.List<Double> values = new java.util.ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                if (inFloor(dx, dz))
                {
                    values.add(RatKingArena.noise(floor.getX() + dx, floor.getZ() + dz));
                }
            }
        }
        values.sort(null);
        int index = Mth.clamp((int) Math.round(values.size() * (1.0D - ICE_SHARE)) - 1, 0, values.size() - 1);
        return values.get(index);
    }
}
