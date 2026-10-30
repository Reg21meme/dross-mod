package com.reg21meme.dross.boss;

import com.reg21meme.dross.boss.rage.RageTeam;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The bedrock test arena ({@code /dross boss arena bedrock [teams]}; the user, October 25, 2026: "a boss arena made of
 * bedrock so I can test spawns ... walls lit with glowstone so no other mobs can spawn"): a square floor of bedrock
 * {@link #SIZE} blocks across with glowstone set in it every {@link #FLOOR_LIGHT_EVERY} blocks, walled round with bedrock
 * up to a bedrock roof {@link #ROOF_HEIGHT} blocks over the floor (glowstone in its walls and roof), and the air inside
 * cleared (blocks with a block entity, like chests, are left alone). Nothing spawns in it by itself: Minecraft never
 * spawns a mob on bedrock, glass or glowstone. Any boss can be summoned in it (its chat buttons).
 * <p>
 * The user, October 30: "a number after bedrock divides the arena into the number of teams there are ... the arena
 * comes with a roof tall enough to fit the Emberdactyl and other flying mobs ... walls dividing the bedrock in equal
 * groups depending on the number, so 2 means 1 wall, 3 2 walls". So it's 89 across (the Emberdactyl circles 26 blocks
 * round its target, its wings reaching 16.5 past that; it was 61) and roofed 48 up (it circles 18 over its target, 11
 * tall), and with {@code teams} of 2 to {@link #MAX_TEAMS} it's divided along x into that many equal sections
 * ({@link #sections}: as equal as whole blocks allow, never more than a block apart) by bedrock walls
 * {@link #DIVIDER_HEIGHT} high: no walking mob gets over them, a flying one can (they stop well under the roof). Each
 * section has a patch of its team's stained glass in the middle of its floor (the Rage Wand's teams in order: Blue, Red,
 * Green, Yellow, Purple). The walls between the sections can be opened and closed ({@link #setDividers}).
 */
public final class BedrockArena
{
    /** Blocks from the middle to the floor's edge: SIZE = 2 x RADIUS + 1 = 89 across inside the walls. */
    public static final int RADIUS = 44;
    public static final int SIZE = 2 * RADIUS + 1;
    /** The roof is this many blocks over the floor; the outer walls reach it. */
    public static final int ROOF_HEIGHT = 48;
    /** At most this many teams (sections): the Rage Wand's five. */
    public static final int MAX_TEAMS = RageTeam.values().length;
    /** The walls between the sections stand this many blocks over the floor. */
    public static final int DIVIDER_HEIGHT = 12;
    /** A glowstone in the floor (and the roof) every this many blocks along x and z (so its floor is lit 8 or more everywhere). */
    public static final int FLOOR_LIGHT_EVERY = 7;
    /** Each section's patch of its team's glass reaches this far from its middle (5 x 5). */
    public static final int MARKER_RADIUS = 2;
    /** Glowstone in the walls: every this many blocks along them, at these heights over the floor. */
    private static final int WALL_LIGHT_EVERY = 4;
    private static final int[] WALL_LIGHT_HEIGHTS = {2, 6, 14, 22, 30, 38, 46};
    private static final int[] DIVIDER_LIGHT_HEIGHTS = {2, 6, 10};
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** One team's section: its columns from {@code from} to {@code to} (x from the middle, both in), for {@code team}. */
    public record Section(int from, int to, RageTeam team)
    {
        /** The column in its middle (x from the arena's middle). */
        public int middle()
        {
            return (this.from + this.to) / 2;
        }

        public int width()
        {
            return this.to - this.from + 1;
        }
    }

    private BedrockArena() {}

    /** True if the column {@code dx}, {@code dz} from the middle is on the floor (inside the walls). */
    public static boolean inFloor(int dx, int dz)
    {
        return Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS;
    }

    /** True if the column is the outer wall's. */
    public static boolean inWall(int dx, int dz)
    {
        return Math.max(Math.abs(dx), Math.abs(dz)) == RADIUS + 1;
    }

    /**
     * The sections for this many teams (1 to {@link #MAX_TEAMS}), west to east: the floor's {@link #SIZE} columns less
     * the walls between them, shared out as evenly as whole blocks allow (any block left over goes to the outer ones
     * first, both ends alike).
     */
    public static List<Section> sections(int teams)
    {
        int n = Math.max(1, Math.min(MAX_TEAMS, teams));
        int free = SIZE - (n - 1);
        int[] widths = new int[n];
        for (int k = 0; k < n; k++)
        {
            widths[k] = free / n;
        }
        int extra = free % n;
        for (int i = 0; extra > 0; i++, extra--)
        {
            widths[i % 2 == 0 ? i / 2 : n - 1 - i / 2]++;      // (0, the last, 1, the last but one...)
        }
        List<Section> out = new ArrayList<>();
        int at = -RADIUS;
        for (int k = 0; k < n; k++)
        {
            out.add(new Section(at, at + widths[k] - 1, RageTeam.values()[k]));
            at += widths[k] + 1;                               // (and the wall after it)
        }
        return out;
    }

    /** The columns (x from the middle) of the walls between these sections. */
    public static List<Integer> dividers(List<Section> sections)
    {
        List<Integer> out = new ArrayList<>();
        for (int k = 0; k + 1 < sections.size(); k++)
        {
            out.add(sections.get(k).to() + 1);
        }
        return out;
    }

    /** Builds it with its floor at {@code floor} (the block under its middle), divided for {@code teams} teams. */
    public static RatKingArena.Result build(ServerLevel level, BlockPos floor, int teams)
    {
        List<Section> sections = sections(teams);
        List<Integer> dividers = dividers(sections);
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState glowstone = Blocks.GLOWSTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        int kept = 0;
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++)
        {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++)
            {
                int x = floor.getX() + dx;
                int z = floor.getZ() + dz;
                boolean wall = inWall(dx, dz);
                boolean divider = !wall && dividers.contains(dx);
                int along = Math.abs(dx) == RADIUS + 1 ? dz : dx;
                boolean grid = !wall && dx % FLOOR_LIGHT_EVERY == 0 && dz % FLOOR_LIGHT_EVERY == 0;
                BlockState marker = sections.size() > 1 && !wall ? marker(sections, dx, dz) : null;
                for (int dy = 0; dy <= ROOF_HEIGHT; dy++)
                {
                    BlockState want;
                    if (dy == 0)
                    {
                        want = marker != null ? marker : grid ? glowstone : bedrock;
                    }
                    else if (dy == ROOF_HEIGHT)
                    {
                        want = grid ? glowstone : bedrock;
                    }
                    else if (wall)
                    {
                        want = along % WALL_LIGHT_EVERY == 0 && lightAt(WALL_LIGHT_HEIGHTS, dy) ? glowstone : bedrock;
                    }
                    else if (divider && dy <= DIVIDER_HEIGHT)
                    {
                        want = dividerBlock(dz, dy);
                    }
                    else
                    {
                        want = air;
                    }
                    pos.set(x, floor.getY() + dy, z);
                    int result = set(level, pos, want);
                    changed += result > 0 ? 1 : 0;
                    kept += result < 0 ? 1 : 0;
                }
            }
        }
        return new RatKingArena.Result(changed, kept);
    }

    /**
     * Opens ({@code open}: air) or closes (bedrock again) the walls between an arena's sections. Its floor and its outer
     * walls stay as they are.
     *
     * @return how many blocks changed
     */
    public static int setDividers(ServerLevel level, BlockPos floor, int teams, boolean open)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        for (int dx : dividers(sections(teams)))
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                for (int dy = 1; dy <= DIVIDER_HEIGHT; dy++)
                {
                    pos.set(floor.getX() + dx, floor.getY() + dy, floor.getZ() + dz);
                    changed += set(level, pos, open ? Blocks.AIR.defaultBlockState() : dividerBlock(dz, dy)) > 0 ? 1 : 0;
                }
            }
        }
        return changed;
    }

    /** The section a column is in (x from the middle), or null (a wall between them, or outside). */
    public static Section sectionAt(List<Section> sections, int dx)
    {
        for (Section s : sections)
        {
            if (dx >= s.from() && dx <= s.to())
            {
                return s;
            }
        }
        return null;
    }

    /** A wall between sections: bedrock with glowstone bands. */
    private static BlockState dividerBlock(int dz, int dy)
    {
        return dz % WALL_LIGHT_EVERY == 0 && lightAt(DIVIDER_LIGHT_HEIGHTS, dy) ? Blocks.GLOWSTONE.defaultBlockState()
                : Blocks.BEDROCK.defaultBlockState();
    }

    /** The team's glass at the middle of its section's floor, or null. */
    private static BlockState marker(List<Section> sections, int dx, int dz)
    {
        Section s = sectionAt(sections, dx);
        if (s == null || Math.abs(dx - s.middle()) > MARKER_RADIUS || Math.abs(dz) > MARKER_RADIUS)
        {
            return null;
        }
        return glass(s.team());
    }

    /** A team's stained glass. */
    public static BlockState glass(RageTeam team)
    {
        return (switch (team)
        {
            case BLUE -> Blocks.BLUE_STAINED_GLASS;
            case RED -> Blocks.RED_STAINED_GLASS;
            case GREEN -> Blocks.LIME_STAINED_GLASS;
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS;
            case PURPLE -> Blocks.PURPLE_STAINED_GLASS;
        }).defaultBlockState();
    }

    /** Sets a block unless it's already that or has a block entity: 1 if it changed, -1 if it was kept, 0 if it was already right. */
    private static int set(ServerLevel level, BlockPos pos, BlockState want)
    {
        BlockState here = level.getBlockState(pos);
        if (here == want)
        {
            return 0;
        }
        if (here.hasBlockEntity())
        {
            return -1;
        }
        level.setBlock(pos, want, FLAGS);
        return 1;
    }

    private static boolean lightAt(int[] heights, int dy)
    {
        for (int height : heights)
        {
            if (height == dy)
            {
                return true;
            }
        }
        return false;
    }
}
