package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.candles;
import static com.reg21meme.dross.villager.hut.HutCanvas.facing;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.log;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;
import static com.reg21meme.dross.villager.hut.HutCanvas.topSlab;
import static com.reg21meme.dross.villager.hut.HutCanvas.upsideDown;

/**
 * 9. Copper Workshop (tinkering). A brick workshop under a weathered copper roof (green-blue patina,
 * waxed so it never changes), with a tall brick smokestack, big blue windows, a copper awning over the door
 * and a long workbench inside. Copper blocks are piled by the side wall.
 * Strange: the ridge is a strip of blue glass, and on the roof stands a contraption of copper, blue glass
 * and lightning rods pointing every way, like he's trying to catch something.
 * <pre>
 *   front view:               Y          Y = lightning rod "coil" on the ridge
 *                  ||      __/#\__       # = blue glass ridge
 *                  ||   __/       \__    || = smokestack
 *                  || /   copper roof  \
 *                   |  [bb]  D  [bb]  |  D = door, [bb] = big windows
 *                   |      =====      |  = = copper awning
 * </pre>
 */
final class CopperWorkshop extends HutDesign
{
    /** The roof's ridge (local z) and its height. */
    private static final int RIDGE_Z = 4;
    private static final int RIDGE_Y = 8;
    /** Where the contraption stands on the ridge (local x). */
    private static final int MAST_X = 8;

    CopperWorkshop()
    {
        super(9, "Copper Workshop", "tinkering", 11, 9, 12, 5, 5, 1, 4);
    }

    @Override
    void draw(HutCanvas c)
    {
        RandomSource random = c.random();

        // Body: x 1..9, z 1..7. Brick walls, dark oak corners, a band of weathered copper along the top.
        c.foundation(1, 1, 9, 7, Blocks.STONE_BRICKS);
        c.walls(1, 1, 9, 7, 0, 0, Blocks.STONE_BRICKS);
        c.fill(2, 0, 2, 8, 0, 6, Blocks.POLISHED_ANDESITE);
        c.fill(3, 0, 3, 7, 0, 5, Blocks.SMOOTH_STONE);
        c.walls(1, 1, 9, 7, 1, 3, Blocks.BRICKS);
        c.walls(1, 1, 9, 7, 4, 4, Blocks.WAXED_WEATHERED_CUT_COPPER);
        for (int[] corner : new int[][] {{1, 1}, {9, 1}, {1, 7}, {9, 7}})
        {
            c.fill(corner[0], 1, corner[1], corner[0], 3, corner[1], log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        }

        // Gable ends (brick), each with a round blue window.
        for (int x : new int[] {1, 9})
        {
            c.fill(x, 5, 2, x, 5, 6, Blocks.BRICKS);
            c.fill(x, 6, 3, x, 6, 5, Blocks.BRICKS);
            c.set(x, 7, RIDGE_Z, Blocks.BRICKS);
            c.set(x, 6, RIDGE_Z, Blocks.BLUE_STAINED_GLASS);
        }

        // Copper roof in two shades of patina, with a strip of blue glass along the ridge.
        for (int x = 0; x <= 10; x++)
        {
            for (int i = 0; i < RIDGE_Z; i++)
            {
                c.set(x, 4 + i, i, stairs(patina(random), Direction.SOUTH));
                c.set(x, 4 + i, 8 - i, stairs(patina(random), Direction.NORTH));
            }
            boolean end = x == 0 || x == 10;
            c.set(x, RIDGE_Y, RIDGE_Z, end ? Blocks.WAXED_OXIDIZED_CUT_COPPER : Blocks.BLUE_STAINED_GLASS);
        }

        // The contraption: copper, a blue glass "jar" with lightning rods sticking out every way, a rod on top.
        c.set(MAST_X, RIDGE_Y + 1, RIDGE_Z, Blocks.WAXED_COPPER_BLOCK);
        c.set(MAST_X, RIDGE_Y + 2, RIDGE_Z, Blocks.BLUE_STAINED_GLASS);
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            c.set(MAST_X + side.getStepX(), RIDGE_Y + 2, RIDGE_Z + side.getStepZ(), facing(Blocks.LIGHTNING_ROD, side));
        }
        c.set(MAST_X, RIDGE_Y + 3, RIDGE_Z, Blocks.WAXED_COPPER_BLOCK);
        c.set(MAST_X, RIDGE_Y + 4, RIDGE_Z, Blocks.LIGHTNING_ROD);

        // A brick smokestack at the back.
        c.fill(2, 0, 0, 2, 9, 0, Blocks.BRICKS);
        c.set(2, 10, 0, Blocks.BRICK_WALL);

        // Door with a copper awning, big blue windows.
        c.door(5, 1, 7, Blocks.DARK_OAK_DOOR, Direction.SOUTH);
        c.fill(4, 3, 8, 6, 3, 8, topSlab(Blocks.WAXED_OXIDIZED_CUT_COPPER_SLAB));
        c.fill(2, 2, 7, 3, 3, 7, Blocks.BLUE_STAINED_GLASS_PANE);
        c.fill(7, 2, 7, 8, 3, 7, Blocks.BLUE_STAINED_GLASS_PANE);
        c.fill(1, 2, 4, 1, 3, 4, Blocks.BLUE_STAINED_GLASS_PANE);
        c.fill(9, 2, 4, 9, 3, 4, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(4, 2, 1, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(6, 2, 1, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(5, 0, 8, Blocks.DIRT_PATH);
        for (int x : new int[] {1, 9})
        {
            c.set(x, 1, 8, Blocks.DARK_OAK_FENCE);
            c.set(x, 2, 8, Blocks.SOUL_LANTERN);
        }

        // Inside: a long workbench with odds and ends on it, a furnace, a crafting table, lamps on chains.
        for (int x = 3; x <= 7; x++)
        {
            c.set(x, 1, 2, upsideDown(Blocks.DARK_OAK_STAIRS, Direction.NORTH));
        }
        c.set(3, 2, 2, facing(Blocks.LIGHTNING_ROD, Direction.EAST));
        c.set(4, 2, 2, log(Blocks.CHAIN, Direction.Axis.X));
        c.set(5, 2, 2, candles(Blocks.BLUE_CANDLE, 2));
        c.set(7, 2, 2, Blocks.SMALL_AMETHYST_BUD);
        c.set(2, 1, 2, facing(Blocks.FURNACE, Direction.SOUTH));
        c.set(8, 1, 2, Blocks.CRAFTING_TABLE);
        c.set(2, 1, 6, Blocks.DECORATED_POT);
        c.set(8, 1, 6, Blocks.DECORATED_POT);
        for (int x : new int[] {3, 7})
        {
            c.fill(x, 6, RIDGE_Z, x, RIDGE_Y - 1, RIDGE_Z, Blocks.CHAIN);
            c.set(x, 5, RIDGE_Z, hanging(Blocks.SOUL_LANTERN));
        }

        // Copper blocks piled by the east wall.
        c.set(10, 1, 5, Blocks.WAXED_EXPOSED_COPPER);
        c.set(10, 1, 6, Blocks.WAXED_COPPER_BLOCK);
        c.set(10, 2, 6, Blocks.WAXED_EXPOSED_COPPER);
        c.set(10, 1, 7, Blocks.WAXED_WEATHERED_COPPER);
    }

    /** Copper roof stairs: mostly fully weathered (blue-green), some only partly. */
    private static Block patina(RandomSource random)
    {
        return random.nextFloat() < 0.7F ? Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS : Blocks.WAXED_WEATHERED_CUT_COPPER_STAIRS;
    }
}
