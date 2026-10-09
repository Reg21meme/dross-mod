package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.log;
import static com.reg21meme.dross.villager.hut.HutCanvas.snow;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;

/**
 * 6. Frost Lodge (frosty). A log cabin under a steep A-frame spruce roof with a snowy ridge, for taiga and
 * snowy villages. The front gable is one big triangle of blue glass; a porch with a railing; snow on the ground.
 * Strange: the hearth is built of blue ice that never melts, and the fire in it burns blue.
 * <pre>
 *   front view:           *            * = snow on the ridge
 *                        /#\           # = blue glass gable
 *                       /###\
 *                      /#####\
 *                     /=======\        = = log beam
 *                     |w  D  w|        D = door, w = window
 *                     |-- : --|        porch railing, : = path
 *
 *   back: the chimney, and inside the back wall an ice hearth with blue fire.
 * </pre>
 */
final class FrostLodge extends HutDesign
{
    /** The roof's ridge (local x). */
    private static final int RIDGE = 4;
    private static final int RIDGE_Y = 10;

    FrostLodge()
    {
        super(6, "Frost Lodge", "frosty", 9, 11, 12, 4, 4, 1, 5);
    }

    @Override
    void draw(HutCanvas c)
    {
        RandomSource random = c.random();

        // Body: x 1..7, z 2..8. Stone footing, spruce floor, walls of horizontal logs, stripped log corners.
        c.foundation(1, 2, 7, 8, Blocks.COBBLESTONE);
        c.walls(1, 2, 7, 8, 0, 0, Blocks.COBBLESTONE);
        c.fill(2, 0, 3, 6, 0, 7, Blocks.SPRUCE_PLANKS);
        c.fill(1, 1, 3, 1, 3, 7, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        c.fill(7, 1, 3, 7, 3, 7, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        c.fill(2, 1, 2, 6, 3, 2, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        c.fill(2, 1, 8, 6, 3, 8, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        for (int[] corner : new int[][] {{1, 2}, {7, 2}, {1, 8}, {7, 8}})
        {
            c.fill(corner[0], 1, corner[1], corner[0], 3, corner[1], log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
        }

        // Steep roof: two blocks up for every block in, ridge running front to back, overhanging front and back.
        for (int z = 1; z <= 9; z++)
        {
            c.set(0, 3, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
            c.set(8, 3, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
            for (int step = 1; step <= 3; step++)
            {
                int y = 2 + 2 * step;
                c.set(step, y, z, Blocks.SPRUCE_PLANKS);
                c.set(step, y + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
                c.set(8 - step, y, z, Blocks.SPRUCE_PLANKS);
                c.set(8 - step, y + 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
            }
            c.set(RIDGE, RIDGE_Y, z, Blocks.SPRUCE_PLANKS);
            c.set(RIDGE, RIDGE_Y + 1, z, snow(2));
        }

        // Gable ends: planks at the back; at the front a log beam and one big triangle of blue glass.
        for (int x = 2; x <= 6; x++)
        {
            int top = gableTop(x);
            c.fill(x, 4, 2, x, top, 2, Blocks.SPRUCE_PLANKS);
            c.set(x, 4, 8, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
            c.fill(x, 5, 8, x, top, 8, Blocks.BLUE_STAINED_GLASS_PANE);
        }

        // Door, windows, porch with a railing and lanterns.
        c.door(RIDGE, 1, 8, Blocks.SPRUCE_DOOR, Direction.SOUTH);
        c.set(2, 2, 8, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(6, 2, 8, Blocks.BLUE_STAINED_GLASS_PANE);
        for (int z : new int[] {4, 6})
        {
            c.set(1, 2, z, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
            c.set(7, 2, z, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        }
        c.fill(1, 0, 9, 7, 0, 10, Blocks.SPRUCE_PLANKS);
        for (int x = 1; x <= 7; x++)
        {
            if (x != RIDGE)
            {
                c.set(x, 1, 10, Blocks.SPRUCE_FENCE);
            }
        }
        c.set(1, 1, 9, Blocks.SPRUCE_FENCE);
        c.set(7, 1, 9, Blocks.SPRUCE_FENCE);
        c.set(3, 2, 10, Blocks.SOUL_LANTERN);
        c.set(5, 2, 10, Blocks.SOUL_LANTERN);

        // The ice hearth: an opening in the back wall into a nook of blue ice, with blue fire on soul soil.
        c.set(RIDGE, 1, 2, Blocks.AIR);
        c.set(3, 1, 2, Blocks.BLUE_ICE);
        c.set(5, 1, 2, Blocks.BLUE_ICE);
        c.fill(3, 2, 2, 5, 2, 2, Blocks.PACKED_ICE);
        c.set(RIDGE, 1, 1, Blocks.SOUL_FIRE);
        c.set(3, 1, 1, Blocks.BLUE_ICE);
        c.set(5, 1, 1, Blocks.BLUE_ICE);
        c.set(RIDGE, 1, 0, Blocks.BLUE_ICE);
        c.fill(3, 0, 0, 5, 0, 1, Blocks.COBBLESTONE);
        c.set(RIDGE, 0, 1, Blocks.SOUL_SOIL);

        // The chimney behind it, up past the ridge.
        c.fill(3, 2, 1, 5, 3, 1, Blocks.COBBLESTONE);
        c.fill(RIDGE, 4, 1, RIDGE, RIDGE_Y + 1, 1, Blocks.COBBLESTONE);
        c.set(RIDGE, 7, 1, Blocks.MOSSY_COBBLESTONE);
        c.set(RIDGE, RIDGE_Y + 2, 1, Blocks.COBBLESTONE_WALL);

        // Inside: two chairs facing the fire, a little table, bookshelves, a chandelier hanging from the ridge.
        c.set(3, 1, 3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        c.set(5, 1, 3, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        c.set(6, 1, 5, Blocks.SPRUCE_FENCE);
        c.set(6, 2, 5, Blocks.WHITE_CARPET);
        c.set(6, 1, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        c.fill(2, 1, 7, 2, 2, 7, Blocks.BOOKSHELF);
        c.set(6, 1, 7, Blocks.DECORATED_POT);
        c.fill(RIDGE, 6, 5, RIDGE, RIDGE_Y - 1, 5, Blocks.CHAIN);
        c.set(RIDGE, 5, 5, hanging(Blocks.SOUL_LANTERN));

        // Snow drifts on the ground around the lodge.
        for (int z = 0; z <= 10; z++)
        {
            snowDrift(c, random, 0, z);
            snowDrift(c, random, 8, z);
        }
        for (int x : new int[] {1, 2, 6, 7})
        {
            snowDrift(c, random, x, 0);
        }
    }

    /** The highest gable block in this column: just under the roof's plank block (or the ridge). */
    private static int gableTop(int x)
    {
        return 1 + 2 * Math.min(x, 8 - x);
    }

    private static void snowDrift(HutCanvas c, RandomSource random, int x, int z)
    {
        if (random.nextFloat() < 0.75F)
        {
            c.snowOnGround(x, z, 1 + random.nextInt(2));
        }
    }
}
