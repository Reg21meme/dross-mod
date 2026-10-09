package com.reg21meme.dross.villager.hut;

import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import static com.reg21meme.dross.villager.hut.HutCanvas.candles;
import static com.reg21meme.dross.villager.hut.HutCanvas.facing;
import static com.reg21meme.dross.villager.hut.HutCanvas.hanging;
import static com.reg21meme.dross.villager.hut.HutCanvas.stairs;

/**
 * 10. Rift Chapel (ominous). A small stone chapel with a dark deepslate base, buttresses, tall blue windows,
 * a steep deepslate-tile roof, a blue rose window over the door and an open belfry with a blue flame
 * where the bell should be. Inside: pews, blue banners, lanterns on long chains. Graves by the path.
 * Lapis blocks are set here and there in the stone walls, and a copper lightning rod tops the spire.
 * Strange: behind the altar stand the broken remains of a real Dross portal frame (the mod's own
 * unbreakable frame block), quietly leaking blue dust and humming now and then.
 * <pre>
 *   z1   . s s s s s s s .     back wall (small blue window high up)
 *   z2   b s F # # F a s b     F = broken Dross frame, a = altar with candles
 *   z3   . w c . . . c w .     c = candles, w = tall blue windows
 *   z4   b s B . : . B s b     B = blue banners, b = buttresses
 *   z5   . w p p : p p w .     p = pews
 *   z6   b s . . T . . s b     T = trader (in the aisle)
 *   z7   . w p p : p p w .
 *   z8   b s . . : . . s b
 *   z9   . s . . : . . s .
 *   z10  . s s F D F s s .     D = door, rose window above it, belfry above that
 *   z11  g . . . : . . . g     g = graves
 *   z12  g . | . : . | . g     | = lantern posts
 * </pre>
 */
final class RiftChapel extends HutDesign
{
    /** The roof's ridge (local x) and its height. */
    private static final int RIDGE = 4;
    private static final int RIDGE_Y = 12;
    /** The walls' top row. */
    private static final int WALL_TOP = 5;
    /** Chance that a stone block in the walls is a lapis block instead. */
    private static final float LAPIS_CHANCE = 0.1F;

    RiftChapel()
    {
        super(10, "Rift Chapel", "ominous", 9, 13, 18, 4, 4, 1, 6);
    }

    @Override
    void draw(HutCanvas c)
    {
        RandomSource random = c.random();

        // The nave: x 1..7, z 1..10. Deepslate footing and base course, stone brick walls above.
        c.foundation(1, 1, 7, 10, Blocks.COBBLED_DEEPSLATE);
        c.walls(1, 1, 7, 10, 0, 0, Blocks.POLISHED_DEEPSLATE);
        c.fill(2, 0, 2, 6, 0, 9, Blocks.STONE_BRICKS);
        c.fill(RIDGE, 0, 4, RIDGE, 0, 9, Blocks.DEEPSLATE_TILES);
        c.fill(2, 0, 2, 6, 0, 3, Blocks.POLISHED_DEEPSLATE);
        c.walls(1, 1, 7, 10, 1, 1, Blocks.DEEPSLATE_BRICKS);
        for (int y = 2; y <= WALL_TOP; y++)
        {
            for (int z = 1; z <= 10; z++)
            {
                for (int x = 1; x <= 7; x++)
                {
                    if (x == 1 || x == 7 || z == 1 || z == 10)
                    {
                        c.set(x, y, z, stone(random));
                    }
                }
            }
        }
        for (int[] corner : new int[][] {{1, 1}, {7, 1}, {1, 10}, {7, 10}})
        {
            c.fill(corner[0], 1, corner[1], corner[0], WALL_TOP, corner[1], Blocks.POLISHED_DEEPSLATE);
        }

        // Tall blue windows, with buttresses between them.
        for (int z : new int[] {3, 5, 7})
        {
            c.fill(1, 2, z, 1, 4, z, Blocks.BLUE_STAINED_GLASS_PANE);
            c.fill(7, 2, z, 7, 4, z, Blocks.BLUE_STAINED_GLASS_PANE);
        }
        for (int z : new int[] {2, 4, 6, 8})
        {
            c.fill(0, 1, z, 0, 2, z, Blocks.STONE_BRICKS);
            c.set(0, 3, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST));
            c.fill(8, 1, z, 8, 2, z, Blocks.STONE_BRICKS);
            c.set(8, 3, z, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST));
        }

        // Steep deepslate-tile roof, ridge running front to back, overhanging front and back.
        for (int z = 0; z <= 11; z++)
        {
            c.set(0, WALL_TOP, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST));
            c.set(8, WALL_TOP, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST));
            for (int step = 1; step <= 3; step++)
            {
                int y = WALL_TOP - 1 + 2 * step;
                c.set(step, y, z, Blocks.DEEPSLATE_TILES);
                c.set(step, y + 1, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST));
                c.set(8 - step, y, z, Blocks.DEEPSLATE_TILES);
                c.set(8 - step, y + 1, z, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST));
            }
            c.set(RIDGE, RIDGE_Y, z, Blocks.DEEPSLATE_TILES);
        }

        // Gable ends up under the roof: a small blue window at the back, the rose window at the front.
        for (int x = 2; x <= 6; x++)
        {
            int top = WALL_TOP - 2 + 2 * Math.min(x, 8 - x);
            for (int y = WALL_TOP + 1; y <= top; y++)
            {
                c.set(x, y, 1, stone(random));
                c.set(x, y, 10, stone(random));
            }
        }
        c.set(RIDGE, 8, 1, Blocks.BLUE_STAINED_GLASS);
        for (int[] frame : new int[][] {{3, 7}, {5, 7}, {3, 9}, {5, 9}})
        {
            c.set(frame[0], frame[1], 10, Blocks.POLISHED_DEEPSLATE);
        }
        for (int[] pane : new int[][] {{4, 7}, {3, 8}, {5, 8}, {4, 9}})
        {
            c.set(pane[0], pane[1], 10, Blocks.BLUE_STAINED_GLASS);
        }
        c.set(RIDGE, 8, 10, Blocks.LIGHT_BLUE_STAINED_GLASS);

        // The belfry on the ridge above the door: four pillars, a blue flame where the bell would be, a spire.
        int belfry = RIDGE_Y;
        for (int x : new int[] {3, 5})
        {
            for (int z : new int[] {8, 10})
            {
                c.fill(x, belfry, z, x, belfry + 2, z, Blocks.POLISHED_DEEPSLATE);
            }
            c.set(x, belfry, 9, Blocks.DEEPSLATE_TILES);
        }
        int cap = belfry + 3;
        for (int x = 3; x <= 5; x++)
        {
            c.set(x, cap, 8, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
            c.set(x, cap, 10, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.NORTH));
        }
        c.set(3, cap, 9, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.EAST));
        c.set(5, cap, 9, stairs(Blocks.DEEPSLATE_TILE_STAIRS, Direction.WEST));
        c.set(RIDGE, cap, 9, Blocks.DEEPSLATE_TILES);
        c.set(RIDGE, cap + 1, 9, Blocks.DEEPSLATE_TILE_WALL);
        c.set(RIDGE, cap + 2, 9, Blocks.END_ROD);
        c.set(RIDGE, cap + 3, 9, Blocks.LIGHTNING_ROD);
        c.set(RIDGE, cap - 1, 9, hanging(Blocks.SOUL_LANTERN));

        // The door in a dark frame, a path between lantern posts, graves on either side.
        c.door(RIDGE, 1, 10, Blocks.DARK_OAK_DOOR, Direction.SOUTH);
        c.fill(3, 1, 10, 3, 3, 10, Blocks.POLISHED_DEEPSLATE);
        c.fill(5, 1, 10, 5, 3, 10, Blocks.POLISHED_DEEPSLATE);
        c.set(RIDGE, 3, 10, Blocks.CHISELED_DEEPSLATE);
        c.fill(RIDGE, 0, 11, RIDGE, 0, 12, Blocks.DEEPSLATE_TILES);
        for (int x : new int[] {2, 6})
        {
            c.set(x, 1, 12, Blocks.DEEPSLATE_BRICK_WALL);
            c.set(x, 2, 12, Blocks.SOUL_LANTERN);
        }
        for (int x : new int[] {0, 8})
        {
            c.fill(x, 0, 11, x, 0, 12, Blocks.COARSE_DIRT);
            c.set(x, 1, 11, Blocks.MOSSY_COBBLESTONE_WALL);
            c.set(x, 1, 12, Blocks.DEAD_BUSH);
        }

        // Behind the altar: the broken remains of a Dross portal frame, one piece floating where the top was.
        Block shard = ModBlocks.DROSS_PORTAL_FRAME.get();
        c.fill(2, 1, 2, 2, 4, 2, shard);
        c.fill(3, 1, 2, 4, 1, 2, shard);
        c.fill(5, 1, 2, 5, 2, 2, shard);
        c.set(4, 5, 2, shard);
        c.set(6, 1, 2, Blocks.POLISHED_DEEPSLATE);
        c.set(6, 2, 2, candles(Blocks.BLUE_CANDLE, 4));
        c.set(2, 1, 3, candles(Blocks.BLUE_CANDLE, 3));
        c.set(6, 1, 3, candles(Blocks.BLUE_CANDLE, 2));

        // Pews either side of the aisle, blue banners between the windows, lanterns on long chains.
        for (int z : new int[] {5, 7})
        {
            for (int x : new int[] {2, 3, 5, 6})
            {
                c.set(x, 1, z, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
            }
        }
        for (int z : new int[] {4, 6})
        {
            c.set(2, 4, z, facing(Blocks.BLUE_WALL_BANNER, Direction.EAST));
            c.set(6, 4, z, facing(Blocks.BLUE_WALL_BANNER, Direction.WEST));
        }
        for (int z : new int[] {4, 8})
        {
            c.fill(RIDGE, 7, z, RIDGE, RIDGE_Y - 1, z, Blocks.CHAIN);
            c.set(RIDGE, 6, z, hanging(Blocks.SOUL_LANTERN));
        }
    }

    /** Old stone bricks: mostly plain, some mossy, some cracked, and a lapis block here and there. */
    private static Block stone(RandomSource random)
    {
        float roll = random.nextFloat();
        if (roll < LAPIS_CHANCE)
        {
            return Blocks.LAPIS_BLOCK;
        }
        return roll < 0.6F ? Blocks.STONE_BRICKS : roll < 0.85F ? Blocks.MOSSY_STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS;
    }
}
