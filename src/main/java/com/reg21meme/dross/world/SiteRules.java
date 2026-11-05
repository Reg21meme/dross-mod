package com.reg21meme.dross.world;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The rules for <b>where</b> the portal site (the Fallen Cathedral) goes in a world, all in one place. It's a
 * world-generation structure, one per world, placed from the seed alone ({@link SiteFinder}; the user's decision of
 * November 2, 2026, "Minecraft's way", like a stronghold ring):
 * <ul>
 *   <li>The seed picks a direction and a distance, {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE} blocks from where
 *       Minecraft starts looking for the world spawn (the climate sampler's spawn point; the real spawn is within about
 *       100 blocks of it).</li>
 *   <li>From there the nearest acceptable spot is taken, searching outwards on a grid (vanilla nudges a stronghold into a
 *       good biome the same way): every sampled biome under the footprint in {@link #PREFERRED} (forests, dark
 *       forests, taigas, snowy land: biome tags, so modded forests count), none {@link #isForbidden forbidden} just
 *       round it; no possible village or pillager outpost start within {@link #VILLAGE_REACH} +
 *       {@link #VILLAGE_CLEARANCE} of it; the ground under the footprint varying by {@link #MAX_VARIATION} blocks or
 *       less; no water on it.</li>
 *   <li>If nothing fits, the rules are relaxed a rung at a time ({@link #ladder}), each with a WARN, so a world always
 *       gets a site.</li>
 * </ul>
 * Minecraft's terrain adaptation ({@code beard_thin}) shapes the land to it; the structure's piece levels its footprint
 * and steps the ring back to the land ({@link #BLEND_MARGIN}).
 */
final class SiteRules
{
    /**
     * The seed's spot is at least this many blocks (flat distance) from the spawn point... (the default; a world uses its
     * own, from config/dross-common.toml when it was made: {@link com.reg21meme.dross.DrossWorldSettings})
     */
    static final int MIN_DISTANCE = com.reg21meme.dross.DrossConfig.DEFAULT_SITE_MIN_DISTANCE;
    /** ...and at most this many. */
    static final int MAX_DISTANCE = com.reg21meme.dross.DrossConfig.DEFAULT_SITE_MAX_DISTANCE;
    /** The last rung's distances reach this much nearer the spawn point and further from it than the world's own. */
    static final int LAST_RUNG_WIDER = 500;
    /** ...but never nearer than this. */
    static final int LAST_RUNG_NEAREST = 256;
    /** Mixed into the world seed for the spot's direction and distance (any number; changing it moves every world's site). */
    static final long SPOT_SALT = 1104202602L;

    /** Biomes the site likes: forests, dark forests, taigas, snowy land ({@code data/dross/tags/worldgen/biome/}). */
    static final TagKey<Biome> PREFERRED = TagKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, "portal_site_preferred"));
    /** Dry land it may take if no preferred spot is near: the preferred ones plus plains, deserts, savannas, meadows... */
    static final TagKey<Biome> ALLOWED = TagKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, "portal_site_allowed"));

    /** The most the natural ground under the footprint may vary (highest minus lowest sample), in blocks. */
    static final int MAX_VARIATION = 5;
    /** The fallback rungs' limits. */
    static final int FALLBACK_VARIATION = 8;
    static final int FALLBACK_VARIATION_LAST = 12;

    /**
     * How far (blocks) outside the footprint the ground is stepped back to the land: a column d blocks out is kept within d
     * of the ground level, so the edge steps at most a block at a time (Minecraft's beard reaches about 12).
     */
    static final int BLEND_MARGIN = 16;
    /** No water within this many blocks of the footprint (it's sampled there too). */
    static final int WATER_CLEARANCE = 4;
    /** Dips and caves right under the footprint's ground are filled, at most this deep. */
    static final int MAX_DIP_FILL = 8;
    /** A dip under the footprint deeper than this (a ravine) isn't filled further down: its bottom stays open. */
    static final int MAX_FOUNDATION = 24;

    /**
     * How far a village's buildings can reach from the middle of the chunk it starts in, in blocks: the village jigsaw
     * keeps every building within 80 blocks each way of its town centre, so up to about 125 blocks diagonally. Pillager
     * outposts are smaller and counted the same.
     */
    static final int VILLAGE_REACH = 125;
    /** Extra clear blocks kept between that reach and the site's footprint and staircase ring. */
    static final int VILLAGE_CLEARANCE = 48;

    // How the land is read (from the world generator's own noise: no chunk is made)
    /**
     * The biomes are read at the site column and every this many blocks over the footprint (its corners, along its sides
     * and through its middle: 12 places); just round it ({@link #WATER_CLEARANCE} out) only forbidden ones count (9 places).
     */
    static final int BIOME_STEP = 30;
    /**
     * After a quick look at the footprint's corners, the middles of its sides and its middle (9 columns), the heights (and
     * water) every this many blocks over the footprint and {@link #WATER_CLEARANCE} round it.
     */
    static final int CHECK_STEP = 15;
    /** The ground level is the median of the heights every this many blocks over the footprint (63 columns). */
    static final int GROUND_STEP = 10;
    /**
     * Heights are read in a band from this far under sea level to this far over it (fewer blocks of noise per column, so
     * much quicker); ground outside it (mountains) never holds the site, except at the last resort.
     */
    static final int BAND_BELOW = 16;
    static final int BAND_ABOVE = 112;
    /** The land's smooth height must be at least this far under the band's top (its bumps stay in the band). */
    static final int BAND_TOP_ROOM = 16;

    /** Which biomes a rung of the {@link #ladder} takes. */
    enum BiomeRule
    {
        /** Every sample in {@link #PREFERRED}. */
        PREFERRED,
        /** Every sample in {@link #ALLOWED}. */
        ALLOWED,
        /** Any land that isn't {@link #isForbidden forbidden}. */
        LAND
    }

    /**
     * One rung of the ladder: how strict the rules are, and how far from the seed's spot (on a grid of {@code step} blocks,
     * nearest first) and from the spawn point a spot may be.
     */
    record Rung(int number, String name, BiomeRule biomes, int maxVariation, int radius, int step, int minDistance, int maxDistance)
    {
    }

    /**
     * The ladder for a world whose site is {@code min} to {@code max} blocks from the spawn point (3,500-4,500 by default).
     * Tried in order; every rung after the first logs a WARN when it's the one that finds the spot. The search reaches far
     * from the seed's spot because 3,500-4,500 blocks out is often sea (measured over 16 seeds: within 384 blocks of it only
     * 3 found a forest, taiga or snowy spot, the rest turned down by oceans, rivers and beaches).
     */
    static List<Rung> ladder(int min, int max)
    {
        int lastMin = Math.max(LAST_RUNG_NEAREST, min - LAST_RUNG_WIDER);
        int lastMax = max + LAST_RUNG_WIDER;
        return List.of(
                new Rung(0, "strict", BiomeRule.PREFERRED, MAX_VARIATION, 1024, 48, min, max),
                new Rung(1, "rougher ground (" + FALLBACK_VARIATION + " blocks of variation)", BiomeRule.PREFERRED, FALLBACK_VARIATION,
                        1024, 48, min, max),
                new Rung(2, "further from the seed's spot (2,048 blocks)", BiomeRule.PREFERRED, FALLBACK_VARIATION, 2048, 96, min, max),
                new Rung(3, "any dry-land biome (plains, desert, savanna...)", BiomeRule.ALLOWED, FALLBACK_VARIATION, 2048, 96, min, max),
                new Rung(4, "any land, steeper ground (" + FALLBACK_VARIATION_LAST + "), " + lastMin + " to " + lastMax + " from spawn",
                        BiomeRule.LAND, FALLBACK_VARIATION_LAST, 2048, 128, lastMin, lastMax));
    }
    /** The number given to the last resort: no rung found a spot, so the flattest dry land seen (or the seed's own spot). */
    static final int LAST_RESORT = 5;
    /** No spot is ever this much nearer the spawn point or further from it than the widest rung allows (for a quick "no"). */
    static final int REACH_SLACK = 256;

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

    /** How a biome fits: 2 preferred, 1 allowed, 0 other land, -1 forbidden. */
    static int fit(Holder<Biome> biome)
    {
        if (isForbidden(biome))
        {
            return -1;
        }
        return biome.is(PREFERRED) ? 2 : biome.is(ALLOWED) ? 1 : 0;
    }

    /** The fit a rung needs. */
    static int needs(BiomeRule biomes)
    {
        return switch (biomes)
        {
            case PREFERRED -> 2;
            case ALLOWED -> 1;
            case LAND -> 0;
        };
    }

    // The tidy-up once the cathedral's land is loaded (SiteTidy)
    /** The tidy-up waits until every chunk of the footprint and this many chunks round it are loaded. */
    static final int TIDY_RING_CHUNKS = 3;
    /** A tree or mushroom touching the footprint is followed at most this far out of it (inside the loaded ring). */
    static final int TIDY_REACH = 24;
    /** While it waits, it looks every this many ticks whether the land is loaded. */
    static final int TIDY_CHECK_TICKS = 40;

    private SiteRules() {}
}
