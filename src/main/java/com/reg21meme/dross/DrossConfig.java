package com.reg21meme.dross;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The mod's settings file, {@code config/dross-common.toml} (for modpack makers and server owners). Every value has a
 * default that is the mod's own tuning, so a missing or broken file changes nothing.
 * <ul>
 *   <li><b>Where things go</b> ({@code [portal_site]}, {@code [trader]}): read once per world, the first time it loads, and
 *       saved in it ({@link DrossWorldSettings}), so changing the file later never moves the cathedral or the trader of a
 *       world that already has them: only new worlds use the new values.</li>
 *   <li><b>The rest</b> ({@code [quest]}, {@code [performance]}): read as the game goes, so a change counts at once
 *       (the planner's threads at the next start).</li>
 * </ul>
 * Each area reads its own values through the methods here; before the file is loaded (or if a value can't be read) a
 * method gives the default.
 */
public final class DrossConfig
{
    // The defaults (the mod's own tuning)
    public static final int DEFAULT_SITE_MIN_DISTANCE = 3500;
    public static final int DEFAULT_SITE_MAX_DISTANCE = 4500;
    public static final boolean DEFAULT_TRADER_AT_VILLAGES = true;
    public static final int DEFAULT_TRADER_SEARCH_RADIUS = 3000;
    public static final double DEFAULT_LETTER_CHANCE = 1.0D;
    public static final int DEFAULT_PLANNER_THREADS = 0;
    public static final int DEFAULT_BUILD_MILLIS = 40;

    public static final ForgeConfigSpec SPEC;

    private static final ForgeConfigSpec.IntValue SITE_MIN_DISTANCE;
    private static final ForgeConfigSpec.IntValue SITE_MAX_DISTANCE;
    private static final ForgeConfigSpec.BooleanValue TRADER_AT_VILLAGES;
    private static final ForgeConfigSpec.IntValue TRADER_SEARCH_RADIUS;
    private static final ForgeConfigSpec.DoubleValue LETTER_CHANCE;
    private static final ForgeConfigSpec.IntValue PLANNER_THREADS;
    private static final ForgeConfigSpec.IntValue BUILD_MILLIS;

    static
    {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("The Fallen Cathedral, the Overworld's Dross portal site (one per world).",
                "Read when a world is first made and saved in it: changing these only affects new worlds.").push("portal_site");
        SITE_MIN_DISTANCE = b.comment("The nearest it can be to the world spawn, in blocks.")
                .defineInRange("minDistance", DEFAULT_SITE_MIN_DISTANCE, 500, 25_000);
        SITE_MAX_DISTANCE = b.comment("The furthest it can be from the world spawn, in blocks (if this is less than minDistance, minDistance is used).")
                .defineInRange("maxDistance", DEFAULT_SITE_MAX_DISTANCE, 500, 25_000);
        b.pop();

        b.comment("The Dross trader and his Rift Chapel (one per world).",
                "Read when a world is first made and saved in it: changing these only affects new worlds.").push("trader");
        TRADER_AT_VILLAGES = b.comment("true: his chapel stands at the edge of a village near the world spawn.",
                        "false: it stands on its own, 384 to 1,536 blocks from spawn, and no village is laid out for it",
                        "(try this if another mod's villages cause trouble).")
                .define("atVillages", DEFAULT_TRADER_AT_VILLAGES);
        TRADER_SEARCH_RADIUS = b.comment("How far from the world spawn his village can be, in blocks.")
                .defineInRange("villageSearchRadius", DEFAULT_TRADER_SEARCH_RADIUS, 500, 8000);
        b.pop();

        b.comment("The quest.").push("quest");
        LETTER_CHANCE = b.comment("The chance that a Nether Fortress or Bastion Remnant chest holds a Weathered Letter (1.0 = every chest, 0.0 = none).")
                .defineInRange("letterChance", DEFAULT_LETTER_CHANCE, 0.0D, 1.0D);
        b.pop();

        b.comment("How much work the mod does at once.").push("performance");
        PLANNER_THREADS = b.comment("Helper threads the Dross's boss-site planner may use (0 = automatic: half the processors, 1 to 8).",
                        "Lower it on a busy server. Changes count at the next start.")
                .defineInRange("plannerThreads", DEFAULT_PLANNER_THREADS, 0, 16);
        BUILD_MILLIS = b.comment("At most this many milliseconds of a server tick go on building a boss lair or volcano near a player",
                        "(it's built a piece at a time). Lower: smoother ticks, slower building.")
                .defineInRange("buildMillisPerTick", DEFAULT_BUILD_MILLIS, 5, 200);
        b.pop();

        SPEC = b.build();
    }

    private DrossConfig() {}

    /** The portal site's nearest distance from the world spawn (for {@link DrossWorldSettings}). */
    public static int siteMinDistance()
    {
        return get(SITE_MIN_DISTANCE);
    }

    /** The portal site's furthest distance from the world spawn, never less than {@link #siteMinDistance()}. */
    public static int siteMaxDistance()
    {
        return Math.max(siteMinDistance(), get(SITE_MAX_DISTANCE));
    }

    public static boolean traderAtVillages()
    {
        return get(TRADER_AT_VILLAGES);
    }

    public static int traderSearchRadius()
    {
        return get(TRADER_SEARCH_RADIUS);
    }

    public static double letterChance()
    {
        return get(LETTER_CHANCE);
    }

    /** The planner's helper threads: the setting, or (0) half the processors, 1 to 8. */
    public static int plannerThreads()
    {
        int set = get(PLANNER_THREADS);
        return set > 0 ? set : Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
    }

    public static long buildMillisPerTick()
    {
        return get(BUILD_MILLIS);
    }

    /** The value, or its default before the file is loaded (or if it can't be read). */
    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value)
    {
        try
        {
            if (SPEC.isLoaded())
            {
                return value.get();
            }
        }
        catch (RuntimeException e)
        {
            // (falls through to the default)
        }
        return value.getDefault();
    }
}
