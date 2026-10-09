package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.growth;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.log;
import static com.reg21meme.dross.villager.hut.HutCanvas.slab;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;
import static com.reg21meme.dross.villager.hut.HutCanvas.upsideDown;
import static com.reg21meme.dross.villager.hut.HutCanvas.vines;

/**
 * 8. Bog Stilt House (eerie). A mud-brick house on mangrove stilts, three blocks up, reached by wooden steps.
 * A porch with a railing, soul lanterns hanging from the eaves, vines dangling from the platform,
 * mud and mangrove roots underneath, cobwebs in the corners.
 * Strange: sculk (from the deep dark, where the echo shards come from) is creeping over the ground under it,
 * up the stilts and onto the walls.
 * <pre>
 *   side view:        /\
 *                    /  \        dark oak roof
 *                   |[b]|__      mud-brick walls, blue windows, porch
 *                   |___|__|     platform
 *                   |  |  |\     stilts, steps down to the front
 *                  ~~~~~~~~~ \   mud, roots, sculk
 * </pre>
 */
final class BogStiltHouse extends HutDesign
{
    /** The platform (the house's floor) is this high. */
    private static final int DECK = 4;

    BogStiltHouse()
    {
        super(8, "Bog Stilt House", "eerie", 9, 11, 10, 4, 4, DECK + 1, 3);
    }

    @Override
    void draw(HutCanvas c)
    {
        RandomSource random = c.random();

        // Mud ground with sculk patches under the house.
        c.fill(1, 0, 1, 7, 0, 7, Blocks.MUD);
        for (int[] patch : new int[][] {{3, 3}, {4, 2}, {5, 4}, {2, 5}, {6, 2}, {3, 6}})
        {
            c.set(patch[0], 0, patch[1], Blocks.SCULK);
        }
        for (int[] vein : new int[][] {{3, 4}, {4, 3}, {2, 4}, {5, 5}, {5, 2}})
        {
            c.set(vein[0], 1, vein[1], growth(Blocks.SCULK_VEIN, Direction.DOWN));
        }

        // Stilts, with mangrove roots spreading round their feet.
        for (int x : new int[] {1, 7})
        {
            for (int z : new int[] {1, 4, 7})
            {
                c.fill(x, 0, z, x, DECK - 1, z, log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
                int out = x == 1 ? -1 : 1;
                c.set(x + out, 1, z, Blocks.MANGROVE_ROOTS);
                if (z != 4)
                {
                    c.set(x, 1, z + (z == 1 ? -1 : 1), Blocks.MANGROVE_ROOTS);
                }
            }
        }
        // Sculk creeping up two of the stilts.
        c.fill(2, 1, 1, 2, 2, 1, growth(Blocks.SCULK_VEIN, Direction.WEST));
        c.fill(6, 1, 7, 6, 2, 7, growth(Blocks.SCULK_VEIN, Direction.EAST));
        c.set(1, 2, 3, growth(Blocks.SCULK_VEIN, Direction.SOUTH));

        // The platform, and steps down from the porch to the ground.
        c.fill(1, DECK, 1, 7, DECK, 7, Blocks.MANGROVE_PLANKS);
        for (int i = 1; i <= 3; i++)
        {
            c.set(4, DECK - i, 7 + i, stairs(Blocks.MANGROVE_STAIRS, Direction.NORTH));
        }
        c.fill(4, 1, 8, 4, 2, 8, Blocks.MANGROVE_FENCE);
        c.set(4, 1, 9, Blocks.MANGROVE_FENCE);

        // The house: mud-brick walls between mangrove log corners, x 1..7, z 1..5.
        int floor = DECK + 1;
        c.walls(1, 1, 7, 5, floor, floor + 2, Blocks.MUD_BRICKS);
        for (int[] corner : new int[][] {{1, 1}, {7, 1}, {1, 5}, {7, 5}})
        {
            c.fill(corner[0], floor, corner[1], corner[0], floor + 2, corner[1], log(Blocks.MANGROVE_LOG, Direction.Axis.Y));
        }
        c.door(4, floor, 5, Blocks.MANGROVE_DOOR, Direction.SOUTH);
        c.set(1, floor + 1, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(7, floor + 1, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(2, floor + 1, 5, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(6, floor + 1, 5, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(4, floor + 1, 1, Blocks.BLUE_STAINED_GLASS_PANE);

        // Porch railing (open where the steps come up).
        for (int x = 1; x <= 7; x++)
        {
            if (x != 4)
            {
                c.set(x, floor, 7, Blocks.MANGROVE_FENCE);
            }
        }
        c.set(1, floor, 6, Blocks.MANGROVE_FENCE);
        c.set(7, floor, 6, Blocks.MANGROVE_FENCE);

        // Dark oak roof, ridge running side to side, its front eave sheltering the porch.
        int eave = floor + 2;
        for (int x = 0; x <= 8; x++)
        {
            c.set(x, eave, 0, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
            c.set(x, eave + 1, 1, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
            c.set(x, eave + 2, 2, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
            c.set(x, eave + 3, 3, slab(Blocks.DARK_OAK_SLAB));
            c.set(x, eave + 2, 4, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
            c.set(x, eave + 1, 5, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
            c.set(x, eave, 6, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        }
        for (int x : new int[] {1, 7})
        {
            c.fill(x, eave + 1, 2, x, eave + 1, 4, Blocks.MANGROVE_PLANKS);
            c.set(x, eave + 2, 3, Blocks.MANGROVE_PLANKS);
        }
        c.set(2, eave - 1, 6, hanging(Blocks.SOUL_LANTERN));
        c.set(6, eave - 1, 6, hanging(Blocks.SOUL_LANTERN));

        // Sculk on the walls.
        for (int[] spot : new int[][] {{0, 2}, {0, 4}})
        {
            c.set(spot[0], floor, spot[1], growth(Blocks.SCULK_VEIN, Direction.EAST));
        }
        c.set(0, floor + 1, 2, growth(Blocks.SCULK_VEIN, Direction.EAST));
        c.set(8, floor, 3, growth(Blocks.SCULK_VEIN, Direction.WEST));
        c.fill(5, floor, 0, 6, floor, 0, growth(Blocks.SCULK_VEIN, Direction.SOUTH));
        c.set(5, floor + 1, 0, growth(Blocks.SCULK_VEIN, Direction.SOUTH));

        // Vines dangling from the platform's edges.
        hangVines(c, 0, 2, Direction.EAST, 3);
        hangVines(c, 0, 6, Direction.EAST, 2);
        hangVines(c, 8, 3, Direction.WEST, 3);
        hangVines(c, 8, 5, Direction.WEST, 1 + random.nextInt(2));
        hangVines(c, 2, 8, Direction.NORTH, 2);
        hangVines(c, 6, 8, Direction.NORTH, 3);

        // Inside: a shelf with a potted blue orchid, a little table, pots, cobwebs, a soul lantern from the ridge.
        c.set(2, floor, 2, upsideDown(Blocks.MANGROVE_STAIRS, Direction.NORTH));
        c.set(2, floor + 1, 2, Blocks.POTTED_BLUE_ORCHID);
        c.set(6, floor, 2, Blocks.DECORATED_POT);
        c.set(2, floor, 4, Blocks.MANGROVE_FENCE);
        c.set(2, floor + 1, 4, Blocks.BLUE_CARPET);
        c.set(6, floor, 4, Blocks.POTTED_RED_MUSHROOM);
        c.set(2, floor + 2, 3, Blocks.COBWEB);
        c.set(6, floor + 2, 2, Blocks.COBWEB);
        c.fill(4, eave + 1, 3, 4, eave + 2, 3, Blocks.CHAIN);
        c.set(4, eave, 3, hanging(Blocks.SOUL_LANTERN));
    }

    /** A column of vines hanging down from the platform's edge, starting at deck height. */
    private static void hangVines(HutCanvas c, int x, int z, Direction towardsDeck, int length)
    {
        for (int i = 0; i < length; i++)
        {
            c.set(x, DECK - i, z, vines(towardsDeck));
        }
    }
}
