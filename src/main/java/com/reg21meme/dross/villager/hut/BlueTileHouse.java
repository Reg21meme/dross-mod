package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import static com.reg21meme.dross.villager.hut.HutCanvas.facing;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.slab;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;

/**
 * 5. Blue Tile House (sun-baked). A desert-village house: smooth sandstone walls under a band of blue tiles,
 * a flat roof with a crenellated parapet and a little blue-tiled cupola, and a shaded porch with a tiled floor.
 * A ladder on the side goes up to the roof terrace.
 * Strange: a jagged crack runs across the floor inside, glowing blue from somewhere underneath.
 * <pre>
 *   z0  . . . . . . . . . . .
 *   z1  . s s w s s s w s s .    s = sandstone wall, tiles along the top
 *   z2  . s / / . . b b b s .    / = glowing crack in the floor, b = bench
 *   z3  . w . / / . . . . w .
 *   z4  . s . . / T . . . s L    T = trader, L = ladder to the roof
 *   z5  . s p . . . . . c s .
 *   z6  . s w w F D F w w s .    D = door, F = carved frame
 *   z7  . t t t t : t t t t .    t = blue tiles (porch floor), | = columns
 *   z8  p | t t | : | t t | p
 * </pre>
 */
final class BlueTileHouse extends HutDesign
{
    BlueTileHouse()
    {
        super(5, "Blue Tile House", "sun-baked", 11, 9, 8, 5, 5, 1, 4);
    }

    @Override
    void draw(HutCanvas c)
    {
        // Body: x 1..9, z 1..6, walls 4 high, blue tiles along the top.
        c.foundation(1, 1, 9, 6, Blocks.SANDSTONE);
        c.walls(1, 1, 9, 6, 0, 0, Blocks.SANDSTONE);
        c.fill(2, 0, 2, 8, 0, 5, Blocks.SMOOTH_SANDSTONE);
        c.walls(1, 1, 9, 6, 1, 1, Blocks.CUT_SANDSTONE);
        c.walls(1, 1, 9, 6, 2, 3, Blocks.SMOOTH_SANDSTONE);
        for (int x = 1; x <= 9; x++)
        {
            for (int z = 1; z <= 6; z++)
            {
                if (x == 1 || x == 9 || z == 1 || z == 6)
                {
                    c.set(x, 4, z, tile(x, z));
                }
            }
        }

        // Flat roof and a crenellated parapet.
        c.fill(1, 5, 1, 9, 5, 6, Blocks.SMOOTH_SANDSTONE);
        for (int x = 1; x <= 9; x++)
        {
            for (int z = 1; z <= 6; z++)
            {
                if (x == 1 || x == 9 || z == 1 || z == 6)
                {
                    c.set(x, 6, z, (x + z) % 2 == 0 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : slab(Blocks.SMOOTH_SANDSTONE_SLAB));
                }
            }
        }

        // A small blue-tiled cupola on the roof, topped with lapis and an end rod.
        for (int x = 6; x <= 8; x++)
        {
            for (int z = 2; z <= 4; z++)
            {
                c.set(x, 6, z, tile(x, z));
            }
        }
        c.set(7, 7, 3, Blocks.LAPIS_BLOCK);
        c.set(7, 8, 3, Blocks.END_ROD);

        // Doorway: carved frame, a glowing light-blue transom above the door.
        c.door(5, 1, 6, Blocks.JUNGLE_DOOR, Direction.SOUTH);
        c.fill(4, 1, 6, 4, 3, 6, Blocks.CHISELED_SANDSTONE);
        c.fill(6, 1, 6, 6, 3, 6, Blocks.CHISELED_SANDSTONE);
        c.set(5, 3, 6, Blocks.LIGHT_BLUE_STAINED_GLASS);

        // Tall front windows, and one on each other side.
        c.fill(2, 2, 6, 3, 3, 6, Blocks.BLUE_STAINED_GLASS_PANE);
        c.fill(7, 2, 6, 8, 3, 6, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(1, 2, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(9, 2, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(3, 2, 1, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(7, 2, 1, Blocks.BLUE_STAINED_GLASS_PANE);

        // The porch: tiled floor, sandstone columns, a slab roof for shade.
        for (int x = 1; x <= 9; x++)
        {
            for (int z = 7; z <= 8; z++)
            {
                c.set(x, 0, z, (x + z) % 2 == 0 ? tile(x, z) : Blocks.SMOOTH_SANDSTONE.defaultBlockState());
            }
            c.set(x, 4, 7, slab(Blocks.SMOOTH_SANDSTONE_SLAB));
            c.set(x, 4, 8, slab(Blocks.SMOOTH_SANDSTONE_SLAB));
        }
        for (int x : new int[] {1, 4, 6, 9})
        {
            c.fill(x, 1, 8, x, 3, 8, Blocks.SANDSTONE_WALL);
        }
        c.set(0, 1, 8, Blocks.DECORATED_POT);
        c.set(10, 1, 8, Blocks.DECORATED_POT);

        // A ladder up the east wall to the roof.
        c.fill(10, 1, 4, 10, 5, 4, facing(Blocks.LADDER, Direction.EAST));

        // The crack in the floor, glowing blue from underneath.
        for (int[] crack : new int[][] {{2, 2}, {3, 2}, {3, 3}, {4, 3}, {4, 4}})
        {
            c.set(crack[0], 0, crack[1], Blocks.BLUE_STAINED_GLASS);
            c.set(crack[0], -1, crack[1], Blocks.SEA_LANTERN);
        }

        // Inside: a bench along the back wall, pots, a hanging soul lantern.
        for (int x = 6; x <= 8; x++)
        {
            c.set(x, 1, 2, stairs(Blocks.SANDSTONE_STAIRS, Direction.NORTH));
        }
        c.set(2, 1, 5, Blocks.DECORATED_POT);
        c.set(8, 1, 5, Blocks.POTTED_CACTUS);
        c.set(5, 4, 3, hanging(Blocks.SOUL_LANTERN));

        // A dry, dead patch beside the house.
        c.set(10, 0, 1, Blocks.COARSE_DIRT);
        c.set(10, 0, 2, Blocks.COARSE_DIRT);
        c.set(10, 1, 2, Blocks.DEAD_BUSH);
    }

    /** A blue glazed tile, turned a different way on each block so the pattern makes a mosaic. */
    private static BlockState tile(int x, int z)
    {
        return facing(Blocks.BLUE_GLAZED_TERRACOTTA, Direction.from2DDataValue(x + z));
    }
}
