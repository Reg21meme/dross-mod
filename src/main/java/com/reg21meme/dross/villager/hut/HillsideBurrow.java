package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.candles;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.log;
import static com.reg21meme.dross.villager.hut.HutCanvas.upsideDown;

/**
 * 4. Hillside Burrow (hidden). A grassy mound with a round wooden door in a mossy stone front,
 * two round blue windows, and a snug 5x5 room dug inside, lined with spruce, with roots hanging from the ceiling.
 * Strange: a blue glass "eye" on top of the hill lights the room from above (and glows at night),
 * and a ring of the grass on the back of the hill has died.
 * <pre>
 *   side view:       ,-~~~-[eye]-~~~-.
 *                 ,-'     room      '-.
 *                |  o   ( D )   o   |     o = round blue window
 *   front yard:    |       :       |      | = lantern posts, : = path
 * </pre>
 */
final class HillsideBurrow extends HutDesign
{
    /** Middle of the mound (local x and z). */
    private static final int MID_X = 5;
    private static final int MID_Z = 4;
    /** Mound shape: an oval this many blocks out from the middle, this tall in the middle. */
    private static final double RADIUS_X = 5.6;
    private static final double RADIUS_Z = 4.6;
    private static final double PEAK = 6.4;
    /** The front of the mound (z = 7) is a stone wall; z = 8 is the front yard. */
    private static final int FACADE_Z = 7;

    HillsideBurrow()
    {
        super(4, "Hillside Burrow", "hidden", 11, 9, 7, 5, 5, 1, 4);
    }

    @Override
    void draw(HutCanvas c)
    {
        RandomSource random = c.random();

        // The mound: dirt, topped with grass, moss, flowers... and a dead ring at the back.
        for (int x = 0; x <= 10; x++)
        {
            for (int z = 0; z < FACADE_Z + 1; z++)
            {
                int h = height(x, z);
                if (h <= 0)
                {
                    continue;
                }
                // The front face (cut off for the yard) is mossy rather than bare dirt.
                c.fill(x, 1, z, x, h - 1, z, z == FACADE_Z ? Blocks.MOSS_BLOCK : Blocks.DIRT);
                double r = ring(x, z);
                if (z <= MID_Z && r > 0.55 && r < 0.75 && random.nextFloat() < 0.7F)
                {
                    c.set(x, h, z, Blocks.COARSE_DIRT);
                    if (random.nextFloat() < 0.4F)
                    {
                        c.set(x, h + 1, z, Blocks.DEAD_BUSH);
                    }
                }
                else if (random.nextFloat() < 0.12F)
                {
                    c.set(x, h, z, Blocks.MOSS_BLOCK);
                }
                else
                {
                    c.set(x, h, z, Blocks.GRASS_BLOCK);
                    float roll = random.nextFloat();
                    if (roll < 0.08F)
                    {
                        c.set(x, h + 1, z, Blocks.CORNFLOWER);
                    }
                    else if (roll < 0.12F)
                    {
                        c.set(x, h + 1, z, Blocks.BLUE_ORCHID);
                    }
                    else if (roll < 0.25F)
                    {
                        c.set(x, h + 1, z, Blocks.GRASS);
                    }
                }
            }
        }

        // The room dug inside: spruce floor, walls and ceiling, with a log beam.
        c.fill(3, 0, 2, 7, 0, 6, Blocks.SPRUCE_PLANKS);
        c.air(3, 1, 2, 7, 3, 6);
        c.fill(3, 4, 2, 7, 4, 6, Blocks.SPRUCE_PLANKS);
        c.fill(3, 4, MID_Z, 7, 4, MID_Z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        c.fill(2, 1, 2, 2, 3, 6, Blocks.SPRUCE_PLANKS);
        c.fill(8, 1, 2, 8, 3, 6, Blocks.SPRUCE_PLANKS);
        c.fill(3, 1, 1, 7, 3, 1, Blocks.SPRUCE_PLANKS);
        for (int x : new int[] {2, 8})
        {
            for (int z : new int[] {2, 6})
            {
                c.fill(x, 1, z, x, 3, z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
            }
        }

        // The stone front with a round door and two round blue windows.
        for (int x = 2; x <= 8; x++)
        {
            for (int y = 1; y <= 3; y++)
            {
                c.set(x, y, FACADE_Z, random.nextFloat() < 0.35F ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
            }
        }
        c.fill(4, 1, FACADE_Z, 4, 2, FACADE_Z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
        c.fill(6, 1, FACADE_Z, 6, 2, FACADE_Z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.Y));
        c.set(5, 3, FACADE_Z, log(Blocks.STRIPPED_SPRUCE_LOG, Direction.Axis.X));
        c.set(4, 3, FACADE_Z, upsideDown(Blocks.SPRUCE_STAIRS, Direction.EAST));
        c.set(6, 3, FACADE_Z, upsideDown(Blocks.SPRUCE_STAIRS, Direction.WEST));
        c.door(5, 1, FACADE_Z, Blocks.SPRUCE_DOOR, Direction.SOUTH);
        c.set(3, 2, FACADE_Z, Blocks.BLUE_STAINED_GLASS);
        c.set(7, 2, FACADE_Z, Blocks.BLUE_STAINED_GLASS);

        // Front yard: a path and two lantern posts.
        c.set(5, 0, 8, Blocks.DIRT_PATH);
        for (int x : new int[] {3, 7})
        {
            c.set(x, 1, 8, Blocks.MOSSY_COBBLESTONE_WALL);
            c.set(x, 2, 8, Blocks.SOUL_LANTERN);
        }

        // The skylight: a shaft of blue glass up through the hill, with a soul lantern under it.
        int top = height(MID_X, MID_Z);
        c.fill(MID_X, 4, MID_Z, MID_X, top, MID_Z, Blocks.BLUE_STAINED_GLASS);
        c.air(MID_X, top + 1, MID_Z, MID_X, top + 1, MID_Z);
        c.set(MID_X, 3, MID_Z, hanging(Blocks.SOUL_LANTERN));

        // Inside: bookshelves, a shelf with candles, a little table, potted blue flowers, hanging roots.
        c.fill(3, 1, 2, 3, 2, 2, Blocks.BOOKSHELF);
        c.fill(7, 1, 2, 7, 2, 2, Blocks.BOOKSHELF);
        c.set(4, 1, 2, upsideDown(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        c.set(4, 2, 2, candles(Blocks.BLUE_CANDLE, 3));
        c.set(6, 1, 2, Blocks.DECORATED_POT);
        c.set(7, 1, 4, Blocks.SPRUCE_FENCE);
        c.set(7, 2, 4, Blocks.BLUE_CARPET);
        c.set(7, 1, 3, HutCanvas.stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH));
        c.set(3, 1, 6, Blocks.POTTED_BLUE_ORCHID);
        c.set(7, 1, 6, Blocks.POTTED_CORNFLOWER);
        for (int[] root : new int[][] {{3, 4}, {7, 5}, {4, 6}, {6, 2}})
        {
            c.set(root[0], 3, root[1], Blocks.HANGING_ROOTS);
        }
    }

    /** How far out from the middle (0 = the middle, 1 = the mound's edge). */
    private static double ring(int x, int z)
    {
        double dx = (x - MID_X) / RADIUS_X;
        double dz = (z - MID_Z) / RADIUS_Z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The mound's top block height here (0 = no mound). */
    private static int height(int x, int z)
    {
        if (z > FACADE_Z)
        {
            return 0;
        }
        double r = ring(x, z);
        return r >= 1.0 ? 0 : (int) Math.round(PEAK * Math.sqrt(1.0 - r * r));
    }
}
