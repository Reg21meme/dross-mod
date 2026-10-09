package com.reg21meme.dross.villager.hut;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.candles;
import static com.reg21meme.dross.villager.hut.HutCanvas.facing;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.slab;
import static com.reg21meme.dross.villager.hut.HutCanvas.upsideDown;

/**
 * 3. Stargazer Tower (watchful). A round, three-storey stone tower, the tallest design: a study on the
 * ground floor, a library above, and a glass observatory on top under a blue glass dome with a glowing core.
 * Ladders inside go all the way up. The observatory floor is a blue "star chart".
 * Strange: the dome glows blue at night, and three glass crystals float in the air around it.
 * <pre>
 *   floor plan (round, 7 across):        side view:
 *        . . o o o . .                       |        end rod
 *        . o . L . o .     L = ladder       /^\       glass dome, glowing core
 *        o . . . . . o                     |###|      observatory (blue glass walls)
 *        o . . T . . o     T = trader      |   |      library
 *        o . . . . . o                     |   |      study
 *        . o . . . o .                     | D |
 *        . . o D o . .     D = door
 * </pre>
 */
final class StargazerTower extends HutDesign
{
    /** The tower's middle column (local x and z). */
    private static final int MID = 4;
    /** Floors: ground, library, observatory. */
    private static final int LIBRARY_FLOOR = 5;
    private static final int OBSERVATORY_FLOOR = 9;
    /** The stone walls stop here; the dome starts above. */
    private static final int WALL_TOP = 12;

    StargazerTower()
    {
        super(3, "Stargazer Tower", "watchful", 9, 9, 16, 4, 4, 1, 4);
    }

    @Override
    void draw(HutCanvas c)
    {
        RandomSource random = c.random();
        c.foundation(1, 1, 7, 7, Blocks.STONE_BRICKS);

        for (int x = 1; x <= 7; x++)
        {
            for (int z = 1; z <= 7; z++)
            {
                if (!inTower(x, z))
                {
                    continue;
                }
                if (isWall(x, z))
                {
                    c.set(x, 0, z, Blocks.STONE_BRICKS);
                    boolean side = Math.abs(x - MID) == 3 || Math.abs(z - MID) == 3;
                    for (int y = 1; y <= WALL_TOP; y++)
                    {
                        // The observatory's walls are blue glass between stone pillars.
                        boolean glass = side && y > OBSERVATORY_FLOOR;
                        c.set(x, y, z, glass ? Blocks.BLUE_STAINED_GLASS : stone(random));
                    }
                    // The rim around the dome.
                    c.set(x, WALL_TOP + 1, z, side ? slab(Blocks.STONE_BRICK_SLAB) : Blocks.BLUE_STAINED_GLASS.defaultBlockState());
                }
                else
                {
                    c.set(x, 0, z, Blocks.SPRUCE_PLANKS);
                    c.set(x, LIBRARY_FLOOR, z, Blocks.SPRUCE_PLANKS);
                    // The observatory floor: a star chart.
                    c.set(x, OBSERVATORY_FLOOR, z, random.nextFloat() < 0.2F ? Blocks.LIGHT_BLUE_CONCRETE : Blocks.BLUE_CONCRETE);
                }
            }
        }
        c.set(MID, OBSERVATORY_FLOOR, MID, facing(Blocks.BLUE_GLAZED_TERRACOTTA, Direction.SOUTH));

        // The dome: a ring of blue glass, then a smaller ring around a glowing core, then a light-blue tip and an end rod.
        int dome = WALL_TOP + 1;
        for (int i = 2; i <= 6; i++)
        {
            c.set(i, dome, 2, Blocks.BLUE_STAINED_GLASS);
            c.set(i, dome, 6, Blocks.BLUE_STAINED_GLASS);
            c.set(2, dome, i, Blocks.BLUE_STAINED_GLASS);
            c.set(6, dome, i, Blocks.BLUE_STAINED_GLASS);
        }
        c.fill(3, dome + 1, 3, 5, dome + 1, 5, Blocks.BLUE_STAINED_GLASS);
        c.set(MID, dome + 1, MID, Blocks.SEA_LANTERN);
        c.set(MID, dome + 2, MID, Blocks.LIGHT_BLUE_STAINED_GLASS);
        c.set(MID, dome + 3, MID, Blocks.END_ROD);

        // Crystals floating in the air around the top.
        c.set(1, 15, 2, Blocks.LIGHT_BLUE_STAINED_GLASS);
        c.set(8, 14, 7, Blocks.BLUE_STAINED_GLASS);
        c.set(6, 16, 0, Blocks.LIGHT_BLUE_STAINED_GLASS);

        // A ladder up the back wall, through holes in both floors.
        for (int y = 1; y <= OBSERVATORY_FLOOR; y++)
        {
            c.set(MID, y, 2, facing(Blocks.LADDER, Direction.SOUTH));
        }

        // Door with a little stone hood, lantern posts, path.
        c.door(MID, 1, 7, Blocks.SPRUCE_DOOR, Direction.SOUTH);
        c.set(MID, 3, 8, upsideDown(Blocks.STONE_BRICK_STAIRS, Direction.NORTH));
        c.set(MID, 0, 8, Blocks.DIRT_PATH);
        for (int x : new int[] {3, 5})
        {
            c.set(x, 1, 8, Blocks.STONE_BRICK_WALL);
            c.set(x, 2, 8, Blocks.SOUL_LANTERN);
        }

        // Windows: the study, the library, and one above the door.
        c.set(1, 2, 4, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(7, 2, 4, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(3, 2, 1, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(1, 7, 4, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(7, 7, 4, Blocks.BLUE_STAINED_GLASS_PANE);
        c.set(MID, 7, 7, Blocks.BLUE_STAINED_GLASS_PANE);

        // The study: a blue "globe" on a stand, books, a desk with candles, a lantern.
        c.set(2, 1, 3, Blocks.SPRUCE_FENCE);
        c.set(2, 2, 3, facing(Blocks.BLUE_GLAZED_TERRACOTTA, Direction.EAST));
        c.fill(6, 1, 3, 6, 2, 3, Blocks.BOOKSHELF);
        c.set(2, 1, 5, upsideDown(Blocks.SPRUCE_STAIRS, Direction.WEST));
        c.set(2, 2, 5, candles(Blocks.BLUE_CANDLE, 2));
        c.set(6, 1, 5, Blocks.POTTED_CORNFLOWER);
        c.set(MID, 4, MID, hanging(Blocks.SOUL_LANTERN));

        // The library: shelves between the windows, a lantern.
        for (int x : new int[] {2, 6})
        {
            for (int z : new int[] {3, 5})
            {
                c.fill(x, LIBRARY_FLOOR + 1, z, x, LIBRARY_FLOOR + 2, z, Blocks.BOOKSHELF);
            }
        }
        c.set(MID, OBSERVATORY_FLOOR - 1, MID, hanging(Blocks.SOUL_LANTERN));

        // The observatory: candles on the star chart.
        c.set(3, OBSERVATORY_FLOOR + 1, 3, candles(Blocks.BLUE_CANDLE, 3));
        c.set(5, OBSERVATORY_FLOOR + 1, 5, candles(Blocks.LIGHT_BLUE_CANDLE, 2));
    }

    /** Inside the tower's round (octagon) outline, 7 across. */
    private static boolean inTower(int x, int z)
    {
        int dx = Math.abs(x - MID);
        int dz = Math.abs(z - MID);
        return Math.max(dx, dz) <= 3 && dx + dz <= 4;
    }

    /** On the outline: next to a spot outside the tower. */
    private static boolean isWall(int x, int z)
    {
        return inTower(x, z) && (!inTower(x - 1, z) || !inTower(x + 1, z) || !inTower(x, z - 1) || !inTower(x, z + 1));
    }

    /** Old stone bricks: mostly plain, some mossy, some cracked. */
    private static Block stone(RandomSource random)
    {
        float roll = random.nextFloat();
        return roll < 0.6F ? Blocks.STONE_BRICKS : roll < 0.85F ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS;
    }
}
