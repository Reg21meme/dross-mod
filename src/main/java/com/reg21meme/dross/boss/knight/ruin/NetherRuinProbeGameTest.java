package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.knight.KnightKind;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * A probe, not a check: plans the Nether's knight ruins ({@link NetherRuinSites}) for fixed seeds with this world's Nether
 * generator and logs how many of the squares round 0, 0 got one (about one each is the aim: the user, "maybe make the
 * Nether one every 1,000 blocks"), their elements and grounds, how far apart, the tries, floors and columns read, and how
 * long it took. For tuning {@link KnightRuinTuning}'s Nether numbers. Its own GameTest namespace:
 * {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_nether_ruin_probe -PgameTestWorld=<a new folder> --console=plain}.
 */
@GameTestHolder(NetherRuinProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = com.reg21meme.dross.Dross.MODID)
public final class NetherRuinProbeGameTest
{
    static final String NAMESPACE = "dross_nether_ruin_probe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "nether_ruin_probe_empty";
    private static final long[] SEEDS = {1L, 2L, 42L, 1234L, 99999L, 123456789L, -5L, 8675309L};
    /** The squares from -HALF to HALF - 1 each way round 0, 0 (8 x 8 = 64 squares of 1,000 blocks). */
    private static final int HALF = 4;

    private NetherRuinProbeGameTest() {}

    @GameTest(template = EMPTY, timeoutTicks = 4_000_000)
    public static void netherRuinsForManySeeds(GameTestHelper helper)
    {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null || !(nether.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
        {
            throw new GameTestAssertException("no Nether, or it isn't a noise world");
        }
        int total = 0;
        int squaresAll = 0;
        long millisAll = 0L;
        Map<String, Integer> areas = new TreeMap<>();
        for (long seed : SEEDS)
        {
            RandomState random = RandomState.create(generator.generatorSettings().value(), nether.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            NetherRuinSites.Context ctx = NetherRuinSites.Context.forSeed(nether, generator, random, seed);
            long tries0 = NetherRuinSites.TRIES_MADE.get();
            long floors0 = NetherRuinSites.FLOORS_TRIED.get();
            long columns0 = NetherRuinSites.COLUMNS_READ.get();
            long start = System.nanoTime();
            int s = ctx.spacing();
            int held = 0;
            int squares = 0;
            Map<KnightKind.Element, Integer> elements = new EnumMap<>(KnightKind.Element.class);
            List<RuinSite> sites = new java.util.ArrayList<>();
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int rx = -HALF; rx < HALF; rx++)
            {
                for (int rz = -HALF; rz < HALF; rz++)
                {
                    squares++;
                    var site = NetherRuinSites.plan(ctx, rx, rz);
                    if (site.isPresent())
                    {
                        held++;
                        sites.add(site.get());
                        elements.merge(site.get().element(), 1, Integer::sum);
                        areas.merge(site.get().design().area().id(), 1, Integer::sum);
                        lowest = Math.min(lowest, site.get().groundY());
                        highest = Math.max(highest, site.get().groundY());
                    }
                }
            }
            long millis = (System.nanoTime() - start) / 1_000_000L;
            double spacing = 0;
            for (RuinSite a : sites)
            {
                double nearest = Double.MAX_VALUE;
                for (RuinSite b : sites)
                {
                    if (a != b)
                    {
                        nearest = Math.min(nearest, KnightRuinSites.dist(a.x(), a.z(), b.x(), b.z()));
                    }
                }
                spacing += nearest == Double.MAX_VALUE ? 0 : nearest;
            }
            total += held;
            squaresAll += squares;
            millisAll += millis;
            LOGGER.info(String.format(Locale.ROOT, "[nether ruin probe] seed %d: %d of %d squares (%d blocks) have a ruin, %s; grounds y %d-%d; "
                            + "nearest neighbour %.0f on average; %d tries, %d floors tried, %d columns read; %d ms (%d ms a square)", seed, held,
                    squares, s, elements, lowest, highest, spacing / Math.max(1, sites.size()), NetherRuinSites.TRIES_MADE.get() - tries0,
                    NetherRuinSites.FLOORS_TRIED.get() - floors0, NetherRuinSites.COLUMNS_READ.get() - columns0, millis, millis / squares));
        }
        LOGGER.info("[nether ruin probe] {} seeds: {} ruins in {} squares ({}%), {} ms a square on average; by area: {}", SEEDS.length, total,
                squaresAll, Math.round(100.0D * total / Math.max(1, squaresAll)), millisAll / Math.max(1, squaresAll), areas);
        helper.succeed();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (ForgeGameTestHooks.isGametestEnabled())
        {
            KnightRuinProbeGameTest.emptyTemplate(event.getServer(), new ResourceLocation(NAMESPACE, EMPTY));
        }
    }
}
