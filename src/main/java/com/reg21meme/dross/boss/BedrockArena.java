package com.reg21meme.dross.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bedrock test arena ({@code /dross boss arena bedrock}; the user, October 25, 2026: "a boss arena made of bedrock so
 * I can test spawns ... walls lit with glowstone so no other mobs can spawn"): a square floor of bedrock
 * {@link #SIZE} blocks across with glowstone set in it every {@link #FLOOR_LIGHT_EVERY} blocks, walled round with bedrock
 * {@link #WALL_HEIGHT} blocks high with bands of glowstone in them, and the air over it cleared {@link #CLEAR_HEIGHT}
 * blocks up (blocks with a block entity, like chests, are left alone). Nothing spawns in it by itself: Minecraft never
 * spawns a mob on bedrock, nor on a block as bright as glowstone. Any boss can be summoned in it (its chat buttons).
 */
public final class BedrockArena
{
    /** Blocks from the middle to the floor's edge: SIZE = 2 x RADIUS + 1 = 61 across inside the walls (room for the
     * Colossus's charge and the Huntsman's ranged fight). */
    public static final int RADIUS = 30;
    public static final int SIZE = 2 * RADIUS + 1;
    /** The walls stand just outside the floor, this many blocks over it (no boss or mob jumps out). */
    public static final int WALL_HEIGHT = 8;
    /** Air is cleared this many blocks over the floor (the Huntsman's arrow rain appears 22 over its target). */
    public static final int CLEAR_HEIGHT = 30;
    /** A glowstone in the floor every this many blocks along x and z (so its floor is lit 8 or more everywhere). */
    public static final int FLOOR_LIGHT_EVERY = 7;
    /** Glowstone in the walls: every this many blocks along them, at these heights over the floor. */
    private static final int WALL_LIGHT_EVERY = 4;
    private static final int[] WALL_LIGHT_HEIGHTS = {2, 6};
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private BedrockArena() {}

    /** True if the column {@code dx}, {@code dz} from the middle is on the floor (inside the walls). */
    public static boolean inFloor(int dx, int dz)
    {
        return Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS;
    }

    /** True if the column is the wall's. */
    public static boolean inWall(int dx, int dz)
    {
        return Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS + 1;
    }

    /** Builds it with its floor at {@code floor} (the block under its middle). */
    public static RatKingArena.Result build(ServerLevel level, BlockPos floor)
    {
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState glowstone = Blocks.GLOWSTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        int kept = 0;
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++)
        {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++)
            {
                int x = floor.getX() + dx;
                int z = floor.getZ() + dz;
                boolean wall = inWall(dx, dz);
                int along = Math.abs(dx) == RADIUS + 1 ? dz : dx;
                for (int dy = 0; dy <= CLEAR_HEIGHT; dy++)
                {
                    pos.set(x, floor.getY() + dy, z);
                    BlockState here = level.getBlockState(pos);
                    BlockState want;
                    if (dy == 0)
                    {
                        boolean light = !wall && dx % FLOOR_LIGHT_EVERY == 0 && dz % FLOOR_LIGHT_EVERY == 0;
                        want = light ? glowstone : bedrock;
                    }
                    else if (wall && dy <= WALL_HEIGHT)
                    {
                        want = along % WALL_LIGHT_EVERY == 0 && wallLight(dy) ? glowstone : bedrock;
                    }
                    else
                    {
                        want = air;
                    }
                    if (here == want)
                    {
                        continue;
                    }
                    if (here.hasBlockEntity())
                    {
                        kept++;
                        continue;
                    }
                    level.setBlock(pos, want, FLAGS);
                    changed++;
                }
            }
        }
        return new RatKingArena.Result(changed, kept);
    }

    private static boolean wallLight(int dy)
    {
        for (int height : WALL_LIGHT_HEIGHTS)
        {
            if (height == dy)
            {
                return true;
            }
        }
        return false;
    }
}
