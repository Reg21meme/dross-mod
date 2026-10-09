package com.reg21meme.dross.villager.hut;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * One design for the Dross trader's hut (see {@link HutDesigns} for all of them).
 * <p>
 * Every design is placed by its <b>center</b>: the ground block (the top solid block, which the floor
 * replaces) in the middle of its footprint. The footprint is width x depth blocks (both odd), and
 * everything the design builds above the ground fits inside it, up to {@code height} blocks up.
 * {@code facing} is the way the front door faces (out of the hut).
 * <p>
 * Every design keeps the trader's rules: a wooden door he can open, no carpet in the doorway,
 * room to stand where he teleports home ({@link #traderSpot}), a light inside, and no beds, bells
 * or job-site blocks (village villagers would come and claim them).
 */
public abstract class HutDesign
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int number;
    private final String name;
    private final String mood;
    private final int width;
    private final int depth;
    private final int height;
    private final int doorX;
    private final int standX;
    private final int standY;
    private final int standZ;

    /**
     * @param doorX the door's column (local x); the way in starts just outside the footprint in front of it
     * @param standX where the trader stands (local grid, feet position)
     */
    HutDesign(int number, String name, String mood, int width, int depth, int height,
              int doorX, int standX, int standY, int standZ)
    {
        if (width % 2 == 0 || depth % 2 == 0)
        {
            throw new IllegalArgumentException("Hut design " + name + " must be an odd number of blocks wide and deep");
        }
        this.number = number;
        this.name = name;
        this.mood = mood;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.doorX = doorX;
        this.standX = standX;
        this.standY = standY;
        this.standZ = standZ;
    }

    /** Draws the hut on the canvas (door facing south, see {@link HutCanvas} for the grid). */
    abstract void draw(HutCanvas canvas);

    /** 1 to 10. */
    public int number()
    {
        return number;
    }

    /** Short enough for one line of a sign. */
    public String name()
    {
        return name;
    }

    /** A word or two about the feel of it, short enough for one line of a sign. */
    public String mood()
    {
        return mood;
    }

    /** Blocks across the front (side to side, seen from the door). */
    public int width()
    {
        return width;
    }

    /** Blocks from the back to the front. */
    public int depth()
    {
        return depth;
    }

    /** How many blocks it rises above the ground. */
    public int height()
    {
        return height;
    }

    /**
     * Builds the hut at {@code center} (the floor level): clears everything above the floor inside the footprint
     * (up to its height), which cuts away any ground that rises above the floor, fills dips under it with dirt, then
     * draws the design.
     */
    public final void build(ServerLevel level, BlockPos center, Direction facing)
    {
        Direction door = horizontal(facing);
        HutCanvas canvas = new HutCanvas(level, center, door, width, depth, 0xD2055L * 31 + number);
        // Where the ground rises above the floor, clearing the footprint cuts it away (read before anything is placed).
        boolean[] cut = new boolean[width * depth];
        for (int x = 0; x < width; x++)
        {
            for (int z = 0; z < depth; z++)
            {
                cut[x * depth + z] = canvas.isSolid(x, 1, z);
            }
        }
        canvas.air(0, 1, 0, width - 1, height, depth - 1);
        for (int x = 0; x < width; x++)
        {
            for (int z = 0; z < depth; z++)
            {
                if (!canvas.isSolidGround(x, z))
                {
                    canvas.set(x, 0, z, Blocks.GRASS_BLOCK);
                    canvas.foundation(x, z, x, z, Blocks.DIRT);
                }
                else if (cut[x * depth + z] && canvas.isDirt(x, 0, z))
                {
                    // The grass went with the cut; put some back on the new top (bare dirt looks wrong).
                    canvas.set(x, 0, z, Blocks.GRASS_BLOCK);
                }
            }
        }
        draw(canvas);
        canvas.apply(number + " (" + name + ")");

        // The trader must have room to stand where he teleports home.
        BlockPos spot = traderSpot(center, door);
        if (!level.getBlockState(spot).getCollisionShape(level, spot).isEmpty()
                || !level.getBlockState(spot.above()).getCollisionShape(level, spot.above()).isEmpty())
        {
            LOGGER.warn("Hut design {} ({}): the trader's spot at {} isn't clear", number, name, spot.toShortString());
        }
    }

    /** Everything the hut covers: from the ground layer up to its height. */
    public final BoundingBox footprint(BlockPos center, Direction facing)
    {
        Direction door = horizontal(facing);
        return BoundingBox.fromCorners(
                HutCanvas.toWorld(center, door, width, depth, 0, 0, 0),
                HutCanvas.toWorld(center, door, width, depth, width - 1, height, depth - 1));
    }

    /** The ground block just outside the footprint, straight in front of the door: where a path to the hut should end. */
    public final BlockPos entrance(BlockPos center, Direction facing)
    {
        return HutCanvas.toWorld(center, horizontal(facing), width, depth, doorX, 0, depth);
    }

    /** Where the trader stands inside (feet position): the spot he teleports home to. */
    public final BlockPos traderSpot(BlockPos center, Direction facing)
    {
        return HutCanvas.toWorld(center, horizontal(facing), width, depth, standX, standY, standZ);
    }

    private static Direction horizontal(Direction facing)
    {
        return facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
    }
}
