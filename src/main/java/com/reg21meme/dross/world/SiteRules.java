package com.reg21meme.dross.world;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The rules for <b>where</b> the portal site (the Fallen Cathedral) may go in a new world, all in one place.
 * <ul>
 *   <li>A random spot {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE} blocks from world spawn.</li>
 *   <li>In one of the {@link #ALLOWED_BIOMES} under the whole footprint and its blending ring; never an ocean, a
 *       river or a swamp, and never water under the footprint.</li>
 *   <li>Well away from every place a village could be ({@link #VILLAGE_REACH} + {@link #VILLAGE_CLEARANCE}).</li>
 *   <li>Natural ground under the footprint that already varies by {@link #MAX_VARIATION} blocks or less.</li>
 * </ul>
 * If no spot passes, the rules are relaxed one step at a time ({@link #LADDER}), with a warning in the log, so a world
 * is never left without a site.
 */
final class SiteRules
{
    /** The site is at least this many blocks (flat distance) from world spawn... */
    static final int MIN_DISTANCE = 3000;
    /** ...and at most this many. */
    static final int MAX_DISTANCE = 10000;

    /**
     * The biomes the site may be in. Every part of the footprint and its blending ring must be one of these
     * (oceans, rivers and swamps never are). Forests are fine: trees in the way are removed whole.
     */
    static final List<ResourceKey<Biome>> ALLOWED_BIOMES = List.of(
            Biomes.PLAINS,
            Biomes.SUNFLOWER_PLAINS,
            Biomes.DESERT,
            Biomes.TAIGA,
            Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA,
            Biomes.SNOWY_TAIGA,
            Biomes.FOREST,
            Biomes.FLOWER_FOREST,
            Biomes.BIRCH_FOREST,
            Biomes.OLD_GROWTH_BIRCH_FOREST,
            Biomes.DARK_FOREST,
            Biomes.SNOWY_PLAINS,
            Biomes.ICE_SPIKES);

    /** The most the natural ground under the footprint may vary (highest minus lowest), in blocks. */
    static final int MAX_VARIATION = 5;
    /**
     * How far (in blocks) outside the footprint the ground may be reshaped into a staircase that meets the natural
     * terrain. The staircase has to end inside this ring.
     */
    static final int BLEND_MARGIN = 16;
    /** No water or lava within this many blocks of the footprint (it would pour into the cleared space). */
    static final int WATER_CLEARANCE = 4;
    /**
     * Inside the footprint, dips and caves under the ground are filled down to solid ground, but never more than this
     * deep (counted from the footprint's ground level), so no big foundations.
     */
    static final int MAX_DIP_FILL = 8;
    /**
     * Small dips (a cave mouth or a little pit) don't count toward the variation: up to this share of the footprint's
     * columns may lie lower than the rest. They're filled up to the ground level, but none deeper than the level's dip fill.
     */
    static final double SMALL_DIP_SHARE = 0.01;
    /**
     * The most the staircase may raise or lower one column outside the footprint, in blocks. A spot that would need more
     * (say, a ravine right next to it being filled) isn't flat enough.
     */
    static final int MAX_RING_CHANGE = 8;
    /**
     * How far a village's buildings can reach from the middle of the chunk it starts in, in blocks: the village jigsaw
     * keeps every building within 80 blocks each way of its town centre, so up to about 125 blocks diagonally.
     */
    static final int VILLAGE_REACH = 125;
    /**
     * Extra clear blocks kept between that reach and the site (its footprint plus blending ring). Leaves room for the
     * trader's chapel and its path, which sit just outside his village.
     */
    static final int VILLAGE_CLEARANCE = 48;
    /** Other structures (outposts, ruined portals, igloos...) count if they reach this close under the ground level. */
    static final int STRUCTURE_DEPTH = 8;

    /** One step of the ladder: how strict the rules are. */
    record Level(int number, String name, @Nullable Set<ResourceKey<Biome>> biomes, int maxVariation, int blendMargin,
                 boolean staircaseRequired, int maxDipFill, int maxRingChange, int minDistance, int maxDistance)
    {
        /** True if the site may be in this biome. {@code biomes == null} means any dry land (still no ocean, river or swamp). */
        boolean allows(Holder<Biome> biome)
        {
            if (isForbidden(biome))
            {
                return false;
            }
            if (biomes == null)
            {
                return true;
            }
            for (ResourceKey<Biome> key : biomes)
            {
                if (biome.is(key))
                {
                    return true;
                }
            }
            return false;
        }

        /** The same rules with a different biome list (tests use this to ask for one biome). */
        Level withBiomes(Set<ResourceKey<Biome>> only)
        {
            return new Level(number, name + " (" + only.size() + " biome(s) only)", only, maxVariation, blendMargin,
                    staircaseRequired, maxDipFill, maxRingChange, minDistance, maxDistance);
        }
    }

    /** Oceans (frozen ones too), rivers (frozen too), beaches and swamps: never. */
    static boolean isForbidden(Holder<Biome> biome)
    {
        return biome.is(BiomeTags.IS_OCEAN)
                || biome.is(BiomeTags.IS_DEEP_OCEAN)
                || biome.is(BiomeTags.IS_RIVER)
                || biome.is(BiomeTags.IS_BEACH)
                || biome.is(Biomes.SWAMP)
                || biome.is(Biomes.MANGROVE_SWAMP);
    }

    /** The most the ground may vary at the first, second and last fallback steps of the {@link #LADDER}. */
    static final int FALLBACK_VARIATION_1 = 6;
    static final int FALLBACK_VARIATION_2 = 8;
    static final int FALLBACK_VARIATION_LAST = 12;

    /** The strict rules every new world tries first. */
    static final Level STRICT = new Level(0, "strict", Set.copyOf(ALLOWED_BIOMES), MAX_VARIATION, BLEND_MARGIN, true,
            MAX_DIP_FILL, MAX_RING_CHANGE, MIN_DISTANCE, MAX_DISTANCE);

    /**
     * If nothing passes, these are tried in order, each with a WARN in the log. The last one never fails: it builds on the
     * best spot found, on whatever ground is there (with a foundation and slopes, like the old cathedral placement).
     */
    static final List<Level> LADDER = List.of(
            STRICT,
            new Level(1, "slightly rougher ground (" + FALLBACK_VARIATION_1 + " blocks of variation)", Set.copyOf(ALLOWED_BIOMES),
                    FALLBACK_VARIATION_1, 18, true, 10, 10, MIN_DISTANCE, MAX_DISTANCE),
            new Level(2, "rougher ground (" + FALLBACK_VARIATION_2 + " blocks of variation)", Set.copyOf(ALLOWED_BIOMES),
                    FALLBACK_VARIATION_2, 20, true, 12, 12, MIN_DISTANCE, MAX_DISTANCE),
            new Level(3, "any dry land biome (" + FALLBACK_VARIATION_2 + " blocks of variation)", null,
                    FALLBACK_VARIATION_2, 20, true, 12, 12, MIN_DISTANCE, MAX_DISTANCE),
            new Level(4, "steep ground (" + FALLBACK_VARIATION_LAST + " blocks of variation, no staircase guarantee)", null,
                    FALLBACK_VARIATION_LAST, 28, false, 24, Integer.MAX_VALUE, MIN_DISTANCE, MAX_DISTANCE));

    /** A search scans at most this many tiles of the ring (512 blocks square; the whole ring is about 1,800)... */
    static final int MAX_SEARCH_REGIONS = 4000;
    /** ...for at most this long (it runs in the background, so the game never waits for it unless it must). */
    static final long MAX_SEARCH_MILLIS = 120_000L;
    /** A search stops once it has this many good spots, and ranks them. */
    static final int GOOD_SPOTS_WANTED = 8;
    /** At most this many of a search's spots are checked in the real world before moving down the ladder. */
    static final int MAX_CHECKS_PER_LEVEL = 8;
    /** Spots a search returns are at least this far apart (blocks), so one bad area doesn't fill the whole list. */
    static final int SPOT_SPACING = 300;

    private SiteRules() {}
}
