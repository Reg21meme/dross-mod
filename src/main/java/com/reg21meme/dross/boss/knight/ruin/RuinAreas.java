package com.reg21meme.dross.boss.knight.ruin;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Which knight ruin area a biome belongs to (CLAUDE.md step 18; the user, November 19, 2026: "each ruin can only go in
 * the biomes I listed ... I don't want fire to be in oak biomes or plains"). Each area's biomes are a biome tag,
 * {@code dross:knight_ruin/<area>} (a modpack may add its own biomes to an element), read through a byte code a cell
 * of the map holds: an area's number in {@link KnightRuinDesigns#AREAS} plus one, {@link #BEACH} (a beach: a warm or
 * a cold shore by the water it faces), {@link #WATER} (an ocean), or {@link #NONE} (land no ruin goes on); a river's
 * code has {@link #RIVER} added (the riverbank's and the frozen river's: the water is the cell itself).
 */
final class RuinAreas
{
    static final byte NONE = 0;
    static final byte WARM = 29;
    static final byte BEACH = 30;
    static final byte WATER = 31;
    /** Added to a peaks cell whose biome is one of the storm's (the highest peaks). Patches are told apart without it. */
    static final byte STORM = 0x20;
    static final byte RIVER = 0x40;

    private record Entry(TagKey<Biome> tag, KnightRuinDesigns.Area area)
    {
    }

    /** In order (the peaks' biomes are earth's peaks; the storm takes some of their ruins: {@link #STORM_PEAKS}). */
    private static final List<Entry> ENTRIES = List.of(
            new Entry(tag("peaks"), KnightRuinDesigns.EARTH_PEAKS),
            new Entry(tag("plains"), KnightRuinDesigns.EARTH_PLAINS),
            new Entry(tag("meadow"), KnightRuinDesigns.EARTH_MEADOW),
            new Entry(tag("windswept_hills"), KnightRuinDesigns.EARTH_WINDSWEPT_HILLS),
            new Entry(tag("snowy_plains"), KnightRuinDesigns.ICE_SNOWY_PLAINS),
            new Entry(tag("taiga"), KnightRuinDesigns.ICE_TAIGA),
            new Entry(tag("frozen_shore"), KnightRuinDesigns.ICE_FROZEN_SHORE),
            new Entry(tag("swamp"), KnightRuinDesigns.POISON_SWAMP),
            new Entry(tag("mushroom_fields"), KnightRuinDesigns.POISON_MUSHROOM_FIELDS),
            new Entry(tag("dark_forest"), KnightRuinDesigns.POISON_DARK_FOREST),
            new Entry(tag("jungle"), KnightRuinDesigns.POISON_JUNGLE),
            new Entry(tag("forest"), KnightRuinDesigns.POISON_FOREST),
            new Entry(tag("desert"), KnightRuinDesigns.FIRE_DESERT),
            new Entry(tag("savanna"), KnightRuinDesigns.FIRE_SAVANNA),
            new Entry(tag("badlands"), KnightRuinDesigns.FIRE_BADLANDS),
            new Entry(tag("stony_shore"), KnightRuinDesigns.THUNDER_STONY_SHORE),
            new Entry(tag("riverbank"), KnightRuinDesigns.THUNDER_RIVERBANK));

    static final TagKey<Biome> BEACH_TAG = tag("beach");
    static final TagKey<Biome> WATER_TAG = tag("water");
    static final TagKey<Biome> WARM_WATER = tag("warm_water");
    static final TagKey<Biome> STORM_PEAKS = tag("storm_peaks");

    private static TagKey<Biome> tag(String name)
    {
        return TagKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, "knight_ruin/" + name));
    }

    /** The cell code for a biome. */
    static byte code(Holder<Biome> biome)
    {
        byte river = biome.is(BiomeTags.IS_RIVER) ? RIVER : 0;
        for (Entry e : ENTRIES)
        {
            if (biome.is(e.tag()))
            {
                byte storm = biome.is(STORM_PEAKS) ? STORM : 0;
                return (byte) (KnightRuinDesigns.AREAS.indexOf(e.area()) + 1 + river + storm);
            }
        }
        if (biome.is(BEACH_TAG))
        {
            return BEACH;
        }
        if (biome.is(WARM_WATER))
        {
            return WARM;
        }
        if (biome.is(WATER_TAG))
        {
            return WATER;
        }
        return NONE;
    }

    /** The area of a code (null: none, a beach, water). */
    @Nullable
    static KnightRuinDesigns.Area area(byte code)
    {
        int i = (code & 0x1F) - 1;
        return i >= 0 && i < KnightRuinDesigns.AREAS.size() ? KnightRuinDesigns.AREAS.get(i) : null;
    }

    static boolean isRiver(byte code)
    {
        return (code & RIVER) != 0;
    }

    /** Land a land ruin may stand on: an area that stands on the land or a mountain. */
    static boolean isLand(byte code)
    {
        KnightRuinDesigns.Area a = area(code);
        return a != null && (a.setting() == KnightRuinDesigns.Setting.SURFACE || a.setting() == KnightRuinDesigns.Setting.MOUNTAIN);
    }

    /** A shore a shore ruin may stand on: a beach, a stony shore, a snowy beach, a river or a frozen river. */
    static boolean isShore(byte code)
    {
        if (code == BEACH)
        {
            return true;
        }
        KnightRuinDesigns.Area a = area(code);
        return a != null && a.setting() == KnightRuinDesigns.Setting.SHORE;
    }

    /** The sea a beach looks onto (warm or not). */
    static boolean isOpenWater(byte code)
    {
        return code == WATER || code == WARM;
    }

    static boolean isStorm(byte code)
    {
        return (code & STORM) != 0;
    }

    // ---------------------------------------------------------------- the Nether (PRE_DROSS_PLAN.md, stage T3)

    /** The Nether's areas, each its biome tag (the user's picks, November 25, 2026: fire in the wastes, the crimson forest
     * and the soul sand valley, poison in the warped forest, earth in the basalt deltas). Never in the Overworld. */
    private static final List<Entry> NETHER_ENTRIES = List.of(
            new Entry(tag("nether_wastes"), KnightRuinDesigns.FIRE_NETHER_WASTES),
            new Entry(tag("crimson_forest"), KnightRuinDesigns.FIRE_CRIMSON_FOREST),
            new Entry(tag("soul_sand_valley"), KnightRuinDesigns.FIRE_SOUL_SAND_VALLEY),
            new Entry(tag("warped_forest"), KnightRuinDesigns.POISON_WARPED_FOREST),
            new Entry(tag("basalt_deltas"), KnightRuinDesigns.EARTH_BASALT_DELTAS));

    /** The areas whose ruins stand in the Nether. */
    static final java.util.Set<KnightRuinDesigns.Area> NETHER = NETHER_ENTRIES.stream().map(Entry::area)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** The Nether area of a biome (its tag), or null if no Nether ruin goes there. */
    @Nullable
    static KnightRuinDesigns.Area netherArea(Holder<Biome> biome)
    {
        for (Entry e : NETHER_ENTRIES)
        {
            if (biome.is(e.tag()))
            {
                return e.area();
            }
        }
        return null;
    }

    /** The biome tag of a Nether area (for the tests). */
    @Nullable
    static TagKey<Biome> netherTag(KnightRuinDesigns.Area area)
    {
        return NETHER_ENTRIES.stream().filter(e -> e.area() == area).map(Entry::tag).findFirst().orElse(null);
    }

    /** The code patches are told apart by (the storm's peaks are one patch with the rest of their mountain). */
    static byte base(byte code)
    {
        return (byte) (code & ~STORM);
    }

    private RuinAreas() {}
}
