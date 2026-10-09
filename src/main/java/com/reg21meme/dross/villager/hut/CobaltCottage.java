package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.facing;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.log;
import static com.reg21meme.dross.villager.hut.HutCanvas.slab;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;

/**
 * 1. Cobalt Cottage (cozy). A classic plains cottage: cobblestone base, oak walls with log corners,
 * a dark spruce roof. Blue glass windows, a blue tablecloth and banner, beds of cornflowers and blue orchids.
 * Strange: the chimney burns with blue soul fire, and one corner of the garden has died off.
 * <pre>
 *   z0  . . . . . . C . .    C = chimney (soul campfire on top)
 *   z1  . L o o o o o L .    L = oak log corner, o = cobblestone + oak plank walls
 *   z2  . o B . . . P o .
 *   z3  . w . . T c F w .    T = trader, c/F = chair and table, w = window
 *   z4  . o f . . . c o .
 *   z5  . L w o D o w L .    D = door
 *   z6  . * * * : * * * .    * = flowers under the eaves
 *   z7  x . * . : . * . .
 *   z8  x x . | : | . . .    | = lantern posts, : = path, x = dead patch
 * </pre>
 */
final class CobaltCottage extends HutDesign
{
    CobaltCottage()
    {
        super(1, "Cobalt Cottage", "cozy", 9, 9, 8, 4, 4, 1, 3);
    }

    @Override
    void draw(HutCanvas c)
    {
        // Foundation, floor and walls.
        c.foundation(1, 1, 7, 5, Blocks.COBBLESTONE);
        c.walls(1, 1, 7, 5, 0, 1, Blocks.COBBLESTONE);
        c.fill(2, 0, 2, 6, 0, 4, Blocks.SPRUCE_PLANKS);
        c.walls(1, 1, 7, 5, 2, 3, Blocks.OAK_PLANKS);
        for (int[] corner : new int[][] {{1, 1}, {7, 1}, {1, 5}, {7, 5}})
        {
            c.fill(corner[0], 1, corner[1], corner[0], 3, corner[1], log(Blocks.OAK_LOG, Direction.Axis.Y));
        }

        // Gable ends under the roof, each with a round light-blue window.
        for (int x : new int[] {1, 7})
        {
            c.fill(x, 4, 2, x, 4, 4, Blocks.OAK_PLANKS);
            c.set(x, 5, 3, Blocks.OAK_PLANKS);
            c.set(x, 4, 3, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }

        // Spruce roof, ridge running side to side, overhanging by one block all round.
        for (int x = 0; x <= 8; x++)
        {
            c.set(x, 3, 0, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            c.set(x, 4, 1, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            c.set(x, 5, 2, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            c.set(x, 6, 3, slab(Blocks.SPRUCE_SLAB));
            c.set(x, 5, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            c.set(x, 4, 5, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            c.set(x, 3, 6, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        }

        // The chimney at the back, burning blue.
        c.fill(6, 0, 0, 6, 7, 0, Blocks.COBBLESTONE);
        c.set(6, 2, 0, Blocks.MOSSY_COBBLESTONE);
        c.set(6, 5, 0, Blocks.MOSSY_COBBLESTONE);
        c.set(6, 8, 0, Blocks.SOUL_CAMPFIRE);

        // Door, windows and lanterns under the front eaves.
        c.door(4, 1, 5, Blocks.SPRUCE_DOOR, Direction.SOUTH);
        c.set(2, 2, 5, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(6, 2, 5, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(1, 2, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(7, 2, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(3, 2, 1, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(3, 2, 6, hanging(Blocks.SOUL_LANTERN));
        c.set(5, 2, 6, hanging(Blocks.SOUL_LANTERN));

        // Blue flower beds under the eaves, a path, lantern posts at the garden gate.
        Block[] bed = {Blocks.CORNFLOWER, Blocks.BLUE_ORCHID, Blocks.CORNFLOWER};
        for (int i = 0; i < 3; i++)
        {
            flower(c, 1 + i, 6, bed[i]);
            flower(c, 5 + i, 6, bed[i]);
        }
        flower(c, 2, 7, Blocks.CORNFLOWER);
        flower(c, 6, 7, Blocks.BLUE_ORCHID);
        for (int z = 6; z <= 8; z++)
        {
            c.set(4, 0, z, Blocks.DIRT_PATH);
        }
        for (int x : new int[] {3, 5})
        {
            c.set(x, 1, 8, Blocks.OAK_FENCE);
            c.set(x, 2, 8, Blocks.SOUL_LANTERN);
        }

        // A corner of the garden where everything has died: the Dross leaks a little, even here.
        c.set(0, 0, 7, Blocks.COARSE_DIRT);
        c.set(0, 0, 8, Blocks.COARSE_DIRT);
        c.set(1, 0, 8, Blocks.COARSE_DIRT);
        c.set(1, 1, 8, Blocks.DEAD_BUSH);

        // Inside: bookshelves, a table with a blue cloth and two chairs, a hanging lantern, a blue banner.
        c.fill(2, 1, 2, 2, 2, 2, Blocks.BOOKSHELF);
        c.set(3, 1, 2, Blocks.CRAFTING_TABLE);
        c.set(6, 1, 2, Blocks.DECORATED_POT);
        c.set(6, 1, 3, Blocks.OAK_FENCE);
        c.set(6, 2, 3, Blocks.BLUE_CARPET);
        c.set(5, 1, 3, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST));
        c.set(6, 1, 4, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        c.set(2, 1, 4, Blocks.POTTED_BLUE_ORCHID);
        c.set(4, 5, 3, Blocks.CHAIN);
        c.set(4, 4, 3, hanging(Blocks.LANTERN));
        c.set(4, 3, 2, facing(Blocks.BLUE_WALL_BANNER, Direction.SOUTH));
    }

    /** A flower on a grass block. */
    private static void flower(HutCanvas c, int x, int z, Block flower)
    {
        c.set(x, 0, z, Blocks.GRASS_BLOCK);
        c.set(x, 1, z, flower);
    }
}
