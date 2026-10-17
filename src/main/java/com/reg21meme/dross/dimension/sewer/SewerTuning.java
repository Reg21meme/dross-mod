package com.reg21meme.dross.dimension.sewer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every number for the shape and look of the sewers under Blightwood (the user's request, October 2026): the maze, the
 * pipes, the tunnels, the rough caves, the rat cave with the Rat King's nest and hoard, and which blocks they're made of.
 * <p>
 * The numbers that decide <i>where</i> networks go (how common they are, how deep, how many end in a rat cave, the rats'
 * spawn lists, the sewer water's colour) are worldgen numbers: they're in {@code tools/terrain/worldgen.py} (its SEWERS
 * section), which writes them into the data files. Change a number here, rebuild, and look at a <b>new</b> world: places
 * already generated keep the sewers they got.
 */
public final class SewerTuning
{
    private SewerTuning() {}

    // ---------------------------------------------------------------------------------------------
    // The maze: a grid of junctions ("nodes") joined by straight tunnels
    // ---------------------------------------------------------------------------------------------

    /** Blocks between neighbouring junctions (centre to centre). Smaller = a tighter, busier maze. */
    public static final int CELL = 10;
    /**
     * Junctions reach at most this many cells from the network's middle (so a network is at most about 2 x 7 x 10 + 16 =
     * 156 blocks across; Minecraft lets a structure reach 128 blocks from its middle chunk).
     */
    public static final int GRID_RADIUS = 7;
    /** How many junctions a network has (a random number in this range): its size, where its patch has the room... */
    public static final int NODES_MIN = 40;
    public static final int NODES_MAX = 62;
    /**
     * ...but a network fills at most this share of the junction spots that fit in its patch (deep enough inside
     * Blightwood), so in a smaller patch the maze shrinks to fit instead of being dropped...
     */
    public static final double MAZE_FILL = 0.8D;
    /** ...down to this many junctions: a network that can't have that many isn't made (the smallest network). */
    public static final int NODES_SMALLEST = 16;
    /**
     * How the maze grows: each step either carries on from the newest junction (this chance: long winding corridors) or
     * branches off a random older one (short side branches and dead ends).
     */
    public static final double GROW_FROM_NEWEST = 0.55D;
    /** After the maze is grown, each pair of neighbouring junctions that isn't joined yet gets a tunnel with this chance: loops. */
    public static final double LOOP_CHANCE = 0.14D;
    /** ...and at least this many loops (added among the rest if the chance gave fewer). */
    public static final int MIN_LOOPS = 2;
    /** A junction where three or four tunnels meet becomes a square room with this chance (otherwise a plain crossing). */
    public static final double ROOM_CHANCE = 0.45D;
    /** A junction (not a pipe, not next to the rat cave) becomes a rough natural cave with this chance. */
    public static final double CAVE_SECTION_CHANCE = 0.10D;
    /**
     * Every junction must stand this far inside Blightwood (blocks, every way): the sewer water's colour and the sewers'
     * spawn list come from the Blightwood underground, so a network never pokes out under another biome.
     */
    public static final int BIOME_MARGIN = 8;

    // ---------------------------------------------------------------------------------------------
    // The Rat King's sewer in its smallest form: only for a group of Blightwood patches where none has room for it by the
    // rules above (the planner then puts it in the roomiest patch with these looser rules; dimension/sites/BossSites)
    // ---------------------------------------------------------------------------------------------

    /** Junctions stand at least this far inside Blightwood (instead of BIOME_MARGIN)... */
    public static final int RELAXED_BIOME_MARGIN = 4;
    /** ...the network has at least this many junctions (instead of NODES_SMALLEST)... */
    public static final int RELAXED_NODES_SMALLEST = 10;
    /** ...the rat cave needs only this many tunnels in (instead of RAT_CAVE_DOORS_MIN)... */
    public static final int RELAXED_RAT_CAVE_DOORS_MIN = 1;
    /** ...and the entrance may be this close to the cave: junctions along the tunnels, blocks from its middle. */
    public static final int RELAXED_PIPE_MIN_STEPS = 2;
    public static final int RELAXED_PIPE_MIN_DISTANCE_FROM_CAVE = 28;

    // ---------------------------------------------------------------------------------------------
    // Pipes (the entrances)
    // ---------------------------------------------------------------------------------------------

    /**
     * How many pipe entrances a regular sewer has (it's dropped if fewer than PIPES_MIN fit). The user asked for exactly 3
     * (October 16; the first version had 2-4, then 2). The Rat King's sewer has one grand entrance instead (below).
     */
    public static final int PIPES_MIN = 3;
    public static final int PIPES_MAX = 3;
    /** A pipe stands at least this many junctions (counted along the tunnels) from the middle or the rat cave... */
    public static final int PIPE_MIN_STEPS = 4;
    /** ...at least this many blocks from the rat cave's middle (straight line)... */
    public static final int PIPE_MIN_DISTANCE_FROM_CAVE = 40;
    /** ...and at least this many blocks from every other pipe. */
    public static final int PIPE_MIN_SPACING = 30;
    /** The ground under a pipe's body may differ by at most this many blocks (so it doesn't stand on a cliff). */
    public static final int PIPE_MAX_GROUND_STEP = 4;
    /** The pipe's body rises this many blocks out of the ground... */
    public static final int PIPE_BODY_RISE = 2;
    /** ...then its wider rim (the Mario-style lip) this many more: so the pipe stands 4 blocks tall. */
    public static final int PIPE_RIM_HEIGHT = 2;
    /** Its opening is 3 x 3 (half-width 1); the body is 5 across, the rim 7 (both with their corners rounded off). */
    public static final int PIPE_OPENING_HALF = 1;
    public static final double PIPE_BODY_RADIUS = 2.6D;
    public static final double PIPE_RIM_RADIUS = 3.6D;
    /** The steps up to the rim are this wide (blocks, odd). */
    public static final int PIPE_STEPS_WIDTH = 3;
    /** The pipe's lower end hangs this far into the pool room (its copper ends this many blocks above the pool's water). */
    public static final int PIPE_END_ABOVE_WATER = 3;

    /**
     * World generation keeps its bigger features (trees, giant mushrooms, fallen logs, ponds, plants) this far from every
     * pipe's middle (blocks; {@code dross:not_near_sewer_entrance}), so nothing grows over a pipe's opening or steps.
     */
    public static final int PIPE_CLEARANCE = 14;

    // ---------------------------------------------------------------------------------------------
    // The Rat King's sewer: its one grand entrance (the user's pick, October 16), a huge crowned pipe
    // ---------------------------------------------------------------------------------------------

    /** Its opening is 5 x 5 (half-width 2)... */
    public static final int GRAND_OPENING_HALF = 2;
    /** ...lined with copper this far out from its middle all the way down the shaft (7 across)... */
    public static final double GRAND_SHAFT_RADIUS = 3.6D;
    /** ...a fat body this far out above the ground (9 across)... */
    public static final double GRAND_BODY_RADIUS = 4.6D;
    /** ...and a rim this far out (11 across: the user's "about 11 wide"). */
    public static final double GRAND_RIM_RADIUS = 5.6D;
    /** The body rises this many blocks out of the ground, then the rim this many more: it stands 5 blocks tall... */
    public static final int GRAND_BODY_RISE = 3;
    public static final int GRAND_RIM_HEIGHT = 2;
    /**
     * ...with a band of bright, fresh copper ({@link #CROWN_BAND}, brighter than its green body and the regular pipes'
     * tarnished bands) every this many blocks down its body above the ground. No gold blocks anywhere on it (the user's
     * request after playing it, October 16).
     */
    public static final int GRAND_BAND_EVERY = 3;
    /**
     * Its rim is shaped like the Rat King's crown (gold with a green gem): this many copper points round it, each tipped
     * with a Blight Vent, glowing poison green like his crown's gem...
     */
    public static final int CROWN_POINTS = 8;
    /** ...every other one tall (this many copper blocks under its green tip), the rest short (this many). */
    public static final int CROWN_TALL = 2;
    public static final int CROWN_SHORT = 1;
    /** No crown point stands within this many degrees of the steps (so you can step onto the rim). */
    public static final double CROWN_STEPS_CLEARANCE_DEGREES = 30.0D;
    /** Tattered red banners (his cape's colour) hanging off the rim, on the sides away from the steps. */
    public static final int GRAND_BANNERS = 2;
    /** The steps up to its rim are this wide. */
    public static final int GRAND_STEPS_WIDTH = 5;
    /** The ground under its body may differ by at most this many blocks. */
    public static final int GRAND_MAX_GROUND_STEP = 5;
    /** The room under it: 9 x 9 inside (half-width 4), 7 tall... */
    public static final int GRAND_POOL_ROOM_HALF = 4;
    public static final int GRAND_POOL_ROOM_HEIGHT = 7;
    /** ...with a 7 x 7 pool (half-width 3) this deep, under the whole 5 x 5 opening. */
    public static final int GRAND_POOL_HALF = 3;
    public static final int GRAND_POOL_DEPTH = 5;
    /** The pipe's lower end hangs this far into the room (its copper ends this many blocks over the water). */
    public static final int GRAND_PIPE_END_ABOVE_WATER = 3;
    /**
     * The green glow: Blight Vents (dross:blight_vent, light {@code TerrainBlocks.BLIGHT_VENT_LIGHT}, breathing spores)
     * set in the shaft's wall this many blocks under the rim's top, one in the middle of each side...
     */
    public static final int SHAFT_VENT_DEPTH = 2;
    /** ...and in the 4 corners of the floor of the room under it (so the pool glows and the light climbs the shaft). */
    public static final boolean POOL_ROOM_VENTS = true;
    /** World generation keeps its bigger features this far from the grand entrance's middle (its poisoned ground and a margin). */
    public static final int GRAND_CLEARANCE = 26;

    // The poisoned ground round the grand entrance (the user's pick: like the cathedral's leak)

    /** It reaches this far from the pipe's middle (blocks), fading towards its edge (a wobbly edge, not a circle). */
    public static final int POISON_RADIUS = 16;
    /** How much the edge wobbles (0: a circle; as a share of the radius). */
    public static final double POISON_WOBBLE = 0.22D;
    /** Near the pipe this share of the ground is coarse dirt (less further out)... */
    public static final double POISON_COARSE_DIRT = 0.55D;
    /** ...this share puddles of Sewer Sludge (in small patches, this many blocks across)... */
    public static final double POISON_SLUDGE = 0.18D;
    public static final double POISON_SLUDGE_PATCH = 3.0D;
    /** ...dead bushes on this share of the rest... */
    public static final double POISON_DEAD_BUSHES = 0.12D;
    /** ...and cobwebs on this share. */
    public static final double POISON_COBWEBS = 0.02D;
    /** Rat nests: this many heaps of bone blocks with old carpets on and round them, and a cobweb or two... */
    public static final int RAT_NESTS = 5;
    /** ...each of this radius... */
    public static final double RAT_NEST_RADIUS_MIN = 1.3D;
    public static final double RAT_NEST_RADIUS_MAX = 2.1D;
    /** ...this far from the pipe's middle. */
    public static final double RAT_NEST_DISTANCE_MIN = 7.5D;
    public static final double RAT_NEST_DISTANCE_MAX = 13.5D;
    /** Gnawed fences: this many short runs of old fence posts... */
    public static final int FENCE_RUNS = 4;
    /** ...each this many posts long... */
    public static final int FENCE_LENGTH_MIN = 3;
    public static final int FENCE_LENGTH_MAX = 6;
    /** ...a post missing (gnawed away) with this chance, and two blocks tall (the rest one) with this one... */
    public static final double FENCE_GAP_CHANCE = 0.3D;
    public static final double FENCE_TALL_CHANCE = 0.35D;
    /** ...this far from the pipe's middle. */
    public static final double FENCE_DISTANCE_MIN = 9.0D;
    public static final double FENCE_DISTANCE_MAX = 15.0D;
    /** A few Blight Vents in the poisoned ground too (pools of green light on the ground at night). */
    public static final int GROUND_VENTS = 3;

    // ---------------------------------------------------------------------------------------------
    // The royal road: the main tunnel from the grand entrance to the Rat King's cave (the user's pick, October 16)
    // ---------------------------------------------------------------------------------------------

    /**
     * The grand entrance is put about this many junctions from the cave along the tunnels (a dead end if one is there):
     * the royal road is about 10 blocks a junction long.
     */
    public static final int ROYAL_ROAD_STEPS = 6;
    // (It has no carpet: a red runner from the entrance to his nest showed the way through the maze. The user asked for it
    // to go after playing it, October 16.)
    /** Red banners on each of its walls every this many blocks (the two walls half that apart, so they alternate). */
    public static final int ROYAL_BANNER_EVERY = 7;
    /** Lit green candles along each of its edges every this many blocks (alternating with the other edge), 1 to ROYAL_CANDLES_MAX each. */
    public static final int ROYAL_CANDLE_EVERY = 5;
    public static final int ROYAL_CANDLES_MAX = 3;
    /** Along its edges: bone piles on this share of the blocks, raw gold on this share, gold pressure plates (coins) on this. */
    public static final double ROYAL_BONES = 0.06D;
    public static final double ROYAL_RAW_GOLD = 0.05D;
    public static final double ROYAL_GOLD_COINS = 0.08D;

    // ---------------------------------------------------------------------------------------------
    // The glow underground (the user's pick, October 16). The boss area lets rats spawn at low block light in the sewers,
    // so these don't stop them: glow lichen gives light 7, a lit candle 3 per candle (so 3 to 12), a Gloomcap 6, a Blight
    // Vent 12 (only at the Rat King's grand entrance)
    // ---------------------------------------------------------------------------------------------

    /** Glow lichen on the walls of tunnels and rooms (both kinds of network): this share of the wall spots... */
    public static final double LICHEN_TUNNELS = 0.035D;
    /** ...and on the rat cave's walls. */
    public static final double LICHEN_RAT_CAVE = 0.05D;
    /** Lichen never grows within this many blocks of a tunnel's ends (where the junction there opens its walls). */
    public static final int LICHEN_END_SKIP = 1;
    /** Lit green candles round the Rat King's nest: this many spots on the floor just outside it... */
    public static final int NEST_CANDLE_SPOTS = 10;
    /** ...this many candles each (a random number from 2 up to this)... */
    public static final int NEST_CANDLES_MAX = 4;
    /** ...and on top of this many of his gold heaps. */
    public static final int HEAP_CANDLE_HEAPS = 5;

    // ---------------------------------------------------------------------------------------------
    // Tunnels and rooms (sizes are inside, in blocks)
    // ---------------------------------------------------------------------------------------------

    /** Main tunnels (the ones the pipes' water channels follow to the middle or the rat cave): wide, with an arched roof. */
    public static final int MAIN_WIDTH = 5;
    public static final int MAIN_HEIGHT = 5;
    /** Side tunnels (every other tunnel): narrow and low, no channel, more sludge. */
    public static final int SIDE_WIDTH = 3;
    public static final int SIDE_HEIGHT = 4;
    /** A room at a junction: 7 x 7, 6 tall (half-width 3), with pillars when three or four tunnels meet. */
    public static final int ROOM_HALF = 3;
    public static final int ROOM_HEIGHT = 6;
    /** The middle hall of a network without a rat cave: 9 x 9, 7 tall. */
    public static final int HALL_HALF = 4;
    public static final int HALL_HEIGHT = 7;
    /** The room under a pipe: 7 x 7, 6 tall, with the pool in its middle. */
    public static final int POOL_ROOM_HALF = 3;
    public static final int POOL_ROOM_HEIGHT = 6;
    /** The pool that breaks your fall: 5 x 5 (half-width 2), this many blocks deep, its water level with the floor. */
    public static final int POOL_HALF = 2;
    public static final int POOL_DEPTH = 4;
    /**
     * Rough natural cave sections: a radius (blocks) and height in these ranges. (A radius over 5 would cut into the
     * junctions next to it: they're CELL = 10 blocks apart.)
     */
    public static final int CAVE_SECTION_RADIUS_MIN = 4;
    public static final int CAVE_SECTION_RADIUS_MAX = 5;
    public static final int CAVE_SECTION_HEIGHT_MIN = 6;
    public static final int CAVE_SECTION_HEIGHT_MAX = 8;

    // ---------------------------------------------------------------------------------------------
    // Sewer Sludge on the floors: the share of each kind of floor it covers, in patches
    // ---------------------------------------------------------------------------------------------

    /** Sludge patches are about this many blocks across. */
    public static final double SLUDGE_PATCH_SIZE = 5.0D;
    public static final double SLUDGE_SIDE_TUNNELS = 0.32D;
    /** Main tunnels: only on the walkways beside the channel. */
    public static final double SLUDGE_MAIN_TUNNELS = 0.14D;
    public static final double SLUDGE_ROOMS = 0.18D;
    public static final double SLUDGE_CAVE_SECTIONS = 0.40D;
    /** The rat cave's floor (the Rat King's battlefield): patches spread over it, so you have to watch your footing... */
    public static final double SLUDGE_RAT_CAVE = 0.26D;
    /** ...smaller patches there (about this many blocks across), so there are lots of them all over the fight area. */
    public static final double RAT_CAVE_SLUDGE_PATCH_SIZE = 3.5D;

    // ---------------------------------------------------------------------------------------------
    // The rat cave (the final chamber, the Rat King's battlefield)
    // ---------------------------------------------------------------------------------------------

    /** Its floor's radius (blocks): 40 across, give or take RAT_CAVE_WOBBLE round its edge. */
    public static final int RAT_CAVE_RADIUS = 20;
    public static final double RAT_CAVE_WOBBLE = 1.5D;
    /** How tall it is in the middle (blocks of air over the floor) and at its walls (a dome between). */
    public static final int RAT_CAVE_HEIGHT = 14;
    public static final int RAT_CAVE_WALL_HEIGHT = 6;
    /** Tunnels that lead into it (a random number in this range, if that many fit): every pipe's channel ends at one. */
    public static final int RAT_CAVE_DOORS_MIN = 3;
    public static final int RAT_CAVE_DOORS_MAX = 5;
    /** The nest of piled carpets in the middle: this radius (7 across), up to 2 blocks of wool under the carpets. */
    public static final double NEST_RADIUS = 3.5D;
    /** The Rat King's hoard round the nest: this many heaps of gold... */
    public static final int GOLD_HEAPS = 7;
    /** ...each of this radius (blocks)... */
    public static final double GOLD_HEAP_RADIUS_MIN = 1.2D;
    public static final double GOLD_HEAP_RADIUS_MAX = 2.0D;
    /** ...this far from the middle... */
    public static final double GOLD_HEAP_DISTANCE_MIN = 5.5D;
    public static final double GOLD_HEAP_DISTANCE_MAX = 8.5D;
    /** ...plus this many single gold blocks scattered round them. */
    public static final int GOLD_SCATTERED = 10;
    /** The user's mix: about 70% of the hoard is Duskstone Gold Ore, the rest (30%) gold blocks. */
    public static final double GOLD_ORE_SHARE = 0.70D;
    /** The arrival spot (for the boss area's teleport) is at least this far from the nest. */
    public static final int ARRIVAL_MIN_DISTANCE = 16;
    /** Copper pipes running along its wall, about this high over the floor (2 blocks tall)... */
    public static final int WALL_PIPES = 3;
    public static final int WALL_PIPE_HEIGHT = 5;
    /** ...the last of them oozes sludge onto a heap instead of pouring water into a drain... */
    public static final int SLUDGE_WALL_PIPES = 1;
    /** ...and pipes along the ceiling, each pouring water down into a drain in the floor this far from the middle. */
    public static final int CEILING_PIPES = 2;
    public static final int CEILING_PIPE_END = 12;
    /** Pointed dripstone hanging from its roof (never over the middle, where the Rat King's poison balls fly). */
    public static final int RAT_CAVE_STALACTITES = 24;
    /** A few dim Gloomcaps at its wall's foot (light 6), so the cave isn't pitch black; 0 for none. */
    public static final int RAT_CAVE_GLOOMCAPS = 5;

    // ---------------------------------------------------------------------------------------------
    // Small details
    // ---------------------------------------------------------------------------------------------

    /** Chance a main tunnel has a thin copper pipe (lightning rods laid on their side) along its wall under the roof. */
    public static final double THIN_PIPE_CHANCE = 0.6D;
    /** Chance per upper corner block of a side tunnel of a cobweb. */
    public static final double COBWEB_CHANCE = 0.03D;
    /** Chance per wall block at floor level of an iron grate (a side drain). */
    public static final double GRATE_CHANCE = 0.03D;
    /** Chance per roof block of a rough cave section of a stalactite hanging from it. */
    public static final double CAVE_SECTION_STALACTITE_CHANCE = 0.08D;

    // ---------------------------------------------------------------------------------------------
    // What it's made of (block: weight; a block is picked by weight, fixed by its position)
    // ---------------------------------------------------------------------------------------------

    /** Tunnel and room walls. */
    public static final Mix WALLS = Mix.of(Blocks.STONE_BRICKS, 50, Blocks.MOSSY_STONE_BRICKS, 24, Blocks.CRACKED_STONE_BRICKS, 18,
            Blocks.TUFF, 4, Blocks.ANDESITE, 4);
    /** The lowest course of the walls, just above the floor: darker and grimier. */
    public static final Mix WALL_FOOT = Mix.of(Blocks.DEEPSLATE_BRICKS, 40, Blocks.CRACKED_DEEPSLATE_BRICKS, 20, Blocks.MOSSY_STONE_BRICKS, 25,
            Blocks.MOSSY_COBBLESTONE, 15);
    /** Roofs. */
    public static final Mix ROOFS = Mix.of(Blocks.STONE_BRICKS, 55, Blocks.MOSSY_STONE_BRICKS, 25, Blocks.CRACKED_STONE_BRICKS, 20);
    /** Floors (where there's no sludge). */
    public static final Mix FLOORS = Mix.of(Blocks.DEEPSLATE_TILES, 35, Blocks.DEEPSLATE_BRICKS, 25, Blocks.CRACKED_DEEPSLATE_TILES, 15,
            Blocks.POLISHED_DEEPSLATE, 10, Blocks.MOSSY_STONE_BRICKS, 15);
    /** Channel beds and pool basins. */
    public static final Mix BASINS = Mix.of(Blocks.MOSSY_COBBLESTONE, 40, Blocks.COBBLED_DEEPSLATE, 35, Blocks.MOSSY_STONE_BRICKS, 25);
    /** Room pillars. */
    public static final Mix PILLARS = Mix.of(Blocks.STONE_BRICKS, 50, Blocks.MOSSY_STONE_BRICKS, 30, Blocks.CHISELED_STONE_BRICKS, 20);
    /** The rock of rough caves (cave sections and the rat cave's walls and roof). */
    public static final Mix CAVE_ROCK = Mix.of(Blocks.TUFF, 30, Blocks.DEEPSLATE, 25, Blocks.COBBLED_DEEPSLATE, 15, Blocks.MOSSY_COBBLESTONE, 12,
            Blocks.MUD, 8, Blocks.STONE_BRICKS, 5, Blocks.MOSSY_STONE_BRICKS, 5);
    /** The floor of rough caves (where there's no sludge): muddy and dirty, so the Rat King has something to burrow into. */
    public static final Mix CAVE_FLOOR = Mix.of(Blocks.PACKED_MUD, 30, Blocks.COARSE_DIRT, 25, Blocks.MUD, 12, Blocks.TUFF, 12,
            Blocks.COBBLED_DEEPSLATE, 11, Blocks.MOSSY_COBBLESTONE, 10);
    /** The pipes: oxidised copper, green like a Mario pipe, streaked with weathered copper; waxed, so it never changes. */
    public static final Mix PIPE_BODY = Mix.of(Blocks.WAXED_OXIDIZED_COPPER, 62, Blocks.WAXED_WEATHERED_COPPER, 28, Blocks.WAXED_OXIDIZED_CUT_COPPER, 10);
    public static final Mix PIPE_RIM = Mix.of(Blocks.WAXED_OXIDIZED_CUT_COPPER, 70, Blocks.WAXED_WEATHERED_CUT_COPPER, 30);
    /** A ring of brighter copper every few blocks along a pipe (and the rim's top), where the pipe's sections join. */
    public static final Mix PIPE_BAND = Mix.of(Blocks.WAXED_EXPOSED_CUT_COPPER, 60, Blocks.WAXED_WEATHERED_CUT_COPPER, 40);
    /** Pipe bands come every this many blocks. */
    public static final int PIPE_BAND_EVERY = 4;
    /** The stone collar round a pipe's foot, and the steps up to its rim (with their support). */
    public static final Mix PIPE_COLLAR = Mix.of(Blocks.MOSSY_STONE_BRICKS, 45, Blocks.STONE_BRICKS, 35, Blocks.CRACKED_STONE_BRICKS, 20);
    public static final Mix STEP_SUPPORT = Mix.of(Blocks.COBBLESTONE, 45, Blocks.MOSSY_COBBLESTONE, 35, Blocks.STONE_BRICKS, 20);
    /** The nest's wool and carpets: sewer colours. */
    public static final Mix NEST_WOOL = Mix.of(Blocks.BROWN_WOOL, 30, Blocks.GREEN_WOOL, 20, Blocks.GRAY_WOOL, 20, Blocks.BLACK_WOOL, 10,
            Blocks.LIGHT_GRAY_WOOL, 10, Blocks.LIME_WOOL, 10);
    public static final Mix NEST_CARPETS = Mix.of(Blocks.BROWN_CARPET, 30, Blocks.GREEN_CARPET, 20, Blocks.GRAY_CARPET, 20, Blocks.BLACK_CARPET, 10,
            Blocks.LIGHT_GRAY_CARPET, 10, Blocks.LIME_CARPET, 10);
    /** The rim round a drain in the rat cave's floor. */
    public static final Mix DRAIN_RIM = Mix.of(Blocks.POLISHED_DEEPSLATE, 50, Blocks.DEEPSLATE_BRICKS, 50);
    /** The grand entrance's bands of bright, fresh copper (no gold: the user's request) and its rim's top. */
    public static final Mix CROWN_BAND = Mix.of(Blocks.WAXED_CUT_COPPER, 70, Blocks.WAXED_COPPER_BLOCK, 30);
    /** Its crown's copper points: bright copper like its bands, so the crown stands out from the green pipe. */
    public static final Mix CROWN_COPPER = Mix.of(Blocks.WAXED_COPPER_BLOCK, 50, Blocks.WAXED_CUT_COPPER, 30, Blocks.WAXED_EXPOSED_CUT_COPPER, 20);
    /** The old carpets of the rat nests round it (sewer colours). */
    public static final Mix RAT_NEST_CARPETS = Mix.of(Blocks.BROWN_CARPET, 35, Blocks.GRAY_CARPET, 25, Blocks.BLACK_CARPET, 15, Blocks.GREEN_CARPET, 15,
            Blocks.LIGHT_GRAY_CARPET, 10);
    /** Its gnawed fences. */
    public static final Mix GNAWED_FENCES = Mix.of(Blocks.DARK_OAK_FENCE, 60, Blocks.SPRUCE_FENCE, 40);
    /** The royal road's floor (Sewer Sludge on it now and then, like the other tunnels). */
    public static final Mix ROYAL_FLOOR = Mix.of(Blocks.POLISHED_DEEPSLATE, 40, Blocks.DEEPSLATE_TILES, 35, Blocks.DEEPSLATE_BRICKS, 25);

    /** A weighted mix of blocks. */
    public static final class Mix
    {
        private final List<BlockState> states = new ArrayList<>();
        private final List<Integer> cumulative = new ArrayList<>();
        private int total;

        private Mix() {}

        /** Pairs of (block, weight). */
        public static Mix of(Object... blocksAndWeights)
        {
            Mix mix = new Mix();
            for (int i = 0; i < blocksAndWeights.length; i += 2)
            {
                mix.total += (Integer) blocksAndWeights[i + 1];
                mix.states.add(((Block) blocksAndWeights[i]).defaultBlockState());
                mix.cumulative.add(mix.total);
            }
            return mix;
        }

        /** The block for a number from 0 to 1. */
        public BlockState pick(double u)
        {
            int target = (int) (u * this.total);
            for (int i = 0; i < this.states.size(); i++)
            {
                if (target < this.cumulative.get(i))
                {
                    return this.states.get(i);
                }
            }
            return this.states.get(this.states.size() - 1);
        }

        /** Every block in the mix. */
        public List<BlockState> states()
        {
            return this.states;
        }
    }
}
