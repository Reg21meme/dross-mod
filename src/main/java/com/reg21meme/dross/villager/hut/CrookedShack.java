package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.facing;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.log;
import static com.reg21meme.dross.villager.hut.HutCanvas.slab;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;

/**
 * 2. Crooked Shack (eccentric). The smallest: a 3x3 room, patched together from four kinds of planks.
 * The upper floor sticks out one block to the east, propped up on fence posts, and the roof is lopsided
 * (a long slope on one side, a short one on the other). A woodpile leans on the west wall.
 * Strange: a block of lapis floats in the air above the lightning rod, and the upstairs window glows blue.
 * <pre>
 *   seen from the front:          ridge
 *                                   |   ← lapis, floating
 *                              /  rod  \
 *                         /  upstairs  |\
 *                        _|  [blue]    | |   ← sticks out east,
 *                    ww |   D  [b]  |  |     propped on posts
 * </pre>
 */
final class CrookedShack extends HutDesign
{
    private static final Block[] PLANKS = {Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.DARK_OAK_PLANKS};

    CrookedShack()
    {
        super(2, "Crooked Shack", "eccentric", 9, 7, 13, 4, 4, 1, 3);
    }

    @Override
    void draw(HutCanvas c)
    {
        RandomSource random = c.random();

        // Ground floor: x 2..6, z 1..5, a 3x3 room inside.
        c.foundation(2, 1, 6, 5, Blocks.COBBLESTONE);
        c.walls(2, 1, 6, 5, 0, 0, Blocks.MOSSY_COBBLESTONE);
        patchwork(c, random, 3, 0, 2, 5, 0, 4);
        patchworkWalls(c, random, 2, 1, 6, 5, 1, 3);
        for (int[] corner : new int[][] {{2, 1}, {6, 1}, {2, 5}, {6, 5}})
        {
            c.fill(corner[0], 1, corner[1], corner[0], 3, corner[1], log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
        }
        c.door(4, 1, 5, Blocks.BIRCH_DOOR, Direction.SOUTH);
        c.set(2, 2, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(5, 2, 5, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        c.set(6, 2, 2, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(3, 2, 6, facing(Blocks.SOUL_WALL_TORCH, Direction.SOUTH));

        // Upper floor: shifted one block east (x 3..7), sticking out over fence-post props.
        c.fill(3, 4, 1, 7, 4, 5, Blocks.SPRUCE_PLANKS);
        c.fill(2, 4, 1, 2, 4, 5, slab(Blocks.SPRUCE_SLAB));
        c.fill(7, 1, 1, 7, 3, 1, Blocks.SPRUCE_FENCE);
        c.fill(7, 1, 5, 7, 3, 5, Blocks.SPRUCE_FENCE);
        patchworkWalls(c, random, 3, 1, 7, 5, 5, 6);
        for (int[] corner : new int[][] {{3, 1}, {7, 1}, {3, 5}, {7, 5}})
        {
            c.fill(corner[0], 5, corner[1], corner[0], 6, corner[1], log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y));
        }
        c.set(7, 5, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(5, 5, 5, Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
        c.set(3, 5, 3, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(5, 5, 3, Blocks.SOUL_LANTERN);

        // Lopsided roof: ridge at x = 6, a long slope to the west and a short steep one to the east.
        // The tall east wall and the gable ends fill up to meet it.
        for (int y = 7; y <= 8; y++)
        {
            for (int z = 1; z <= 5; z++)
            {
                c.set(7, y, z, plank(random));
            }
        }
        for (int z : new int[] {1, 5})
        {
            c.set(4, 7, z, plank(random));
            c.fill(5, 7, z, 5, 8, z, plank(random));
            c.fill(6, 7, z, 6, 9, z, plank(random));
        }
        for (int z = 0; z <= 6; z++)
        {
            boolean dark = random.nextBoolean();
            Block stairs = dark ? Blocks.DARK_OAK_STAIRS : Blocks.SPRUCE_STAIRS;
            c.set(2, 6, z, stairs(stairs, Direction.EAST));
            c.set(3, 7, z, stairs(stairs, Direction.EAST));
            c.set(4, 8, z, stairs(stairs, Direction.EAST));
            c.set(5, 9, z, stairs(stairs, Direction.EAST));
            c.set(6, 10, z, slab(dark ? Blocks.DARK_OAK_SLAB : Blocks.SPRUCE_SLAB));
            c.set(7, 9, z, stairs(stairs, Direction.WEST));
            c.set(8, 8, z, stairs(stairs, Direction.WEST));
        }

        // A stovepipe, a lightning rod on the ridge... and a block of lapis floating above it.
        c.fill(4, 9, 2, 4, 10, 2, Blocks.COBBLESTONE_WALL);
        c.set(6, 11, 3, Blocks.LIGHTNING_ROD);
        c.set(6, 13, 3, Blocks.LAPIS_BLOCK);

        // Woodpile against the west wall, under a slab lean-to.
        c.fill(1, 1, 2, 1, 1, 4, log(Blocks.OAK_LOG, Direction.Axis.X));
        c.fill(1, 2, 2, 1, 2, 3, log(Blocks.OAK_LOG, Direction.Axis.X));
        c.fill(1, 3, 2, 1, 3, 4, slab(Blocks.SPRUCE_SLAB));

        // Path, and a patch of ground the Dross has drained.
        c.set(4, 0, 6, Blocks.DIRT_PATH);
        c.set(6, 0, 6, Blocks.COARSE_DIRT);
        c.set(7, 0, 6, Blocks.COARSE_DIRT);
        c.set(8, 0, 5, Blocks.COARSE_DIRT);
        c.set(6, 1, 6, Blocks.DEAD_BUSH);

        // Inside: a crafting table, a pot, a dead bush in a flower pot, a soul lantern from the ceiling.
        c.set(3, 1, 2, Blocks.CRAFTING_TABLE);
        c.set(5, 1, 2, Blocks.DECORATED_POT);
        c.set(5, 1, 4, Blocks.POTTED_DEAD_BUSH);
        c.set(4, 3, 3, hanging(Blocks.SOUL_LANTERN));
    }

    private static Block plank(RandomSource random)
    {
        return PLANKS[random.nextInt(PLANKS.length)];
    }

    /** A solid area of mixed planks. */
    private static void patchwork(HutCanvas c, RandomSource random, int x1, int y1, int z1, int x2, int y2, int z2)
    {
        for (int y = y1; y <= y2; y++)
        {
            for (int x = x1; x <= x2; x++)
            {
                for (int z = z1; z <= z2; z++)
                {
                    c.set(x, y, z, plank(random));
                }
            }
        }
    }

    /** Walls of mixed planks around a box. */
    private static void patchworkWalls(HutCanvas c, RandomSource random, int x1, int z1, int x2, int z2, int y1, int y2)
    {
        patchwork(c, random, x1, y1, z1, x2, y2, z1);
        patchwork(c, random, x1, y1, z2, x2, y2, z2);
        patchwork(c, random, x1, y1, z1 + 1, x1, y2, z2 - 1);
        patchwork(c, random, x2, y1, z1 + 1, x2, y2, z2 - 1);
    }
}
