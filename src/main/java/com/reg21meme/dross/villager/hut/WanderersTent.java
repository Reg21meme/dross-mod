package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.candles;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.log;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;

/**
 * 7. Wanderer's Tent (nomadic). A round pavilion tent of blue and white wool stripes with a pointed roof,
 * a blue banner flying from its top. Inside, a round rug and a bench; outside, hay bales and a fire with
 * log seats. He looks like he only stopped here for a while.
 * Strange: a circle of lit blue candles around the middle, and the campfire burns blue.
 * <pre>
 *   h h . . . . . . . . .      h = hay bales
 *   h . . s s w w b . . .      s/w = blue and white wool stripes
 *   . . s . . . . . b . .
 *   . s . c . . . c . w .      c = blue candles
 *   . w . . . . . . . b .
 *   . w . . . T . . b w .      T = trader, b = bench
 *   . s . . . . . . . s .
 *   . s . c . . . c . w .
 *   . . w . . . . . s . .
 *   . . . s w D w s . F l      D = door, F = soul campfire, l = log seats
 *   . . . . . : . . l . .
 * </pre>
 */
final class WanderersTent extends HutDesign
{
    private static final int MID = 5;
    /** The tent wall's radius. */
    private static final double RADIUS = 4.5;
    /** The roof steps up a block at each of these distances from the middle (y = 4 out to the wall, then 5, 6, 7). */
    private static final double[] ROOF_STEPS = {2.6, 1.6, 0.6};
    /** How many stripes (half blue, half white) go round the tent. */
    private static final int STRIPES = 12;

    WanderersTent()
    {
        super(7, "Wanderer's Tent", "nomadic", 11, 11, 10, 5, 5, 1, 5);
    }

    @Override
    void draw(HutCanvas c)
    {
        c.foundation(1, 1, 9, 9, Blocks.DIRT);

        for (int x = 0; x <= 10; x++)
        {
            for (int z = 0; z <= 10; z++)
            {
                if (!inside(x, z, RADIUS))
                {
                    continue;
                }
                // Floor: spruce boards with a round blue and white rug in the middle.
                double d = distance(x, z);
                Block floor = d < 1.0 ? Blocks.BLUE_WOOL : d < 2.0 ? Blocks.WHITE_WOOL : d < 2.7 ? Blocks.BLUE_WOOL : Blocks.SPRUCE_PLANKS;
                c.set(x, 0, z, floor);
                if (onEdge(x, z, RADIUS))
                {
                    // The wall: three blocks of striped wool.
                    c.fill(x, 1, z, x, 3, z, stripe(x, z));
                }
                else
                {
                    // The pointed roof: one block higher for each step in towards the middle.
                    int y = 4;
                    for (double step : ROOF_STEPS)
                    {
                        if (d <= step)
                        {
                            y++;
                        }
                    }
                    c.set(x, y, z, stripe(x, z));
                }
            }
        }
        c.set(MID, 7, MID, Blocks.BLUE_WOOL);
        c.set(MID, 8, MID, Blocks.SPRUCE_FENCE);
        c.set(MID, 9, MID, Blocks.BLUE_BANNER);

        // Door between two posts.
        c.door(MID, 1, 9, Blocks.SPRUCE_DOOR, Direction.SOUTH);
        c.fill(4, 1, 9, 4, 2, 9, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        c.fill(6, 1, 9, 6, 2, 9, log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        c.set(MID, 0, 10, Blocks.DIRT_PATH);

        // Inside: the candle circle, a bench along the east side, pots, a soul lantern from the roof.
        for (int[] spot : new int[][] {{3, 3}, {7, 3}, {3, 7}, {7, 7}})
        {
            c.set(spot[0], 1, spot[1], candles(Blocks.BLUE_CANDLE, 3));
        }
        for (int z = 4; z <= 6; z++)
        {
            c.set(8, 1, z, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST));
        }
        c.set(2, 1, 4, Blocks.DECORATED_POT);
        c.set(2, 1, 6, Blocks.DECORATED_POT);
        c.set(MID, 6, MID, hanging(Blocks.SOUL_LANTERN));

        // Outside: a blue campfire with log seats, and a stack of hay bales.
        c.set(9, 1, 9, Blocks.SOUL_CAMPFIRE);
        c.set(8, 1, 10, log(Blocks.SPRUCE_LOG, Direction.Axis.X));
        c.set(10, 1, 8, log(Blocks.SPRUCE_LOG, Direction.Axis.Z));
        for (int[] hay : new int[][] {{1, 1, 1}, {0, 1, 1}, {1, 1, 0}, {1, 2, 1}})
        {
            c.set(hay[0], hay[1], hay[2], Blocks.HAY_BLOCK);
        }
    }

    private static double distance(int x, int z)
    {
        return Math.sqrt((x - MID) * (x - MID) + (z - MID) * (z - MID));
    }

    private static boolean inside(int x, int z, double radius)
    {
        return (x - MID) * (x - MID) + (z - MID) * (z - MID) <= radius * radius;
    }

    /** On the circle's outline: inside it, next to a spot outside it. */
    private static boolean onEdge(int x, int z, double radius)
    {
        return inside(x, z, radius)
                && (!inside(x - 1, z, radius) || !inside(x + 1, z, radius) || !inside(x, z - 1, radius) || !inside(x, z + 1, radius));
    }

    /** Blue or white, in stripes running round the tent (and up the roof). */
    private static Block stripe(int x, int z)
    {
        double angle = Math.atan2(z - MID, x - MID) + Math.PI;
        int sector = (int) (angle / (2 * Math.PI) * STRIPES) % STRIPES;
        return sector % 2 == 0 ? Blocks.BLUE_WOOL : Blocks.WHITE_WOOL;
    }
}
