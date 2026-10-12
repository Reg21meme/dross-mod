package com.reg21meme.dross.dimension;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Top-down pictures of the Dross's terrain (the user asked for them), written to the project's {@code terrain-previews/}
 * folder. They take a minute or two, so they're only made when the environment variable {@value #ENV} is {@code 1}:
 * <pre>
 *   PowerShell:  $env:DROSS_TERRAIN_PREVIEWS="1"; .\gradlew.bat runGameTestServer --console=plain
 *   Git Bash:    DROSS_TERRAIN_PREVIEWS=1 ./gradlew.bat runGameTestServer --console=plain
 * </pre>
 * Otherwise the test just succeeds. What it writes:
 * <ul>
 *   <li>{@code biomes_<seed>.png} for each fixed test seed, and {@code biomes_world.png} for this world (its seed changes
 *       every run, so its file keeps one name and shows the seed in its title): the biome map, {@value #MAP_HALF} blocks
 *       every way from the hub, {@value #MAP_CELL} blocks a pixel; the hub marked, rings at 200, 450, 600, 850 and 1500
 *       blocks (the hub's falloff radii); a legend.</li>
 *   <li>{@code relief_<seed>.png} and {@code relief_world.png}: the same area's land, lit from the north-west and coloured
 *       by biome, with a contour line every 16 blocks; higher land is lighter, snow above y 200 white.</li>
 *   <li>{@code overview.png}: the seeds' biome maps side by side.</li>
 *   <li>{@code hub_area.png}: the real generated blocks {@value #HUB_HALF} blocks round the hub (this world), drawn like a
 *       Minecraft map (with a darker line every {@value #BLOCK_CONTOUR} blocks of height); the hub's footprint outlined.</li>
 *   <li>{@code site_<biome>.png}: the real generated blocks of each biome's sample site ({@link TerrainChunksGameTest}).</li>
 *   <li>{@code stats.json} and {@code stats.txt}: the numbers ({@link TerrainSurvey} for every test seed and this world;
 *       chunk generation times; the hub).</li>
 *   <li>For {@code tools/terrain/preview_sites.py}: the real blocks of the middle of each site ({@value #VIEW_HALF} blocks
 *       every way) and round the hub ({@value #HUB_VIEW_HALF}), with their block light, in {@code build/tv1/views/};
 *       the script draws them as night views from above ({@code terrain-previews/view_<name>.png}).</li>
 * </ul>
 * The seeds drawn are the first {@value #PREVIEW_FIXED_SEEDS} of {@link TerrainSamplingGameTest#TEST_SEEDS} and this world's.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class TerrainPreviewGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String ENV = "DROSS_TERRAIN_PREVIEWS";
    private static final int PREVIEW_FIXED_SEEDS = 3;
    /** The maps reach this far from the hub every way (blocks), and each pixel is this many blocks. */
    private static final int MAP_HALF = 4096;
    private static final int MAP_CELL = 8;
    /** Rings drawn round the hub (blocks): the Silverwood core, the mountain-free zone, the mountain ring. */
    private static final int[] RINGS = {200, 450, 600, 850, 1500};
    /** The real-blocks picture of the hub reaches this far (blocks), each block this many pixels. */
    private static final int HUB_HALF = 128;
    private static final int HUB_SCALE = 3;
    private static final int SITE_SCALE = 6;
    /** Relief: a contour line every this many blocks; snow above this height. */
    private static final int CONTOUR = 16;
    private static final int SNOW_Y = 200;
    /** Real-block pictures: a darker line every this many blocks of height. */
    private static final int BLOCK_CONTOUR = 8;
    private static final int LEGEND_WIDTH = 260;
    /** The night views (tools/terrain/preview_sites.py): this far every way round a site's middle... */
    private static final int VIEW_HALF = 24;
    /** ...and round the middle of the hub's footprint (the Shattered Spire's: X -25..49, Z -34..38). */
    private static final int HUB_VIEW_HALF = 64;
    private static final int HUB_VIEW_X = 12;
    private static final int HUB_VIEW_Z = 2;
    /** A view's blocks reach this far below its lowest ground. */
    private static final int VIEW_BELOW_GROUND = 3;

    private TerrainPreviewGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_terrain_previews", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void terrainPreviews(GameTestHelper helper)
    {
        if (!"1".equals(System.getenv(ENV)))
        {
            LOGGER.info("[terrain previews] skipped (set the environment variable {}=1 to draw them into terrain-previews/)", ENV);
            helper.succeed();
            return;
        }
        System.setProperty("java.awt.headless", "true");
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        Path out = FMLPaths.GAMEDIR.get().toAbsolutePath().getParent().resolve("terrain-previews");
        long started = Util.getMillis();
        CompletableFuture<JsonObject> maps = CompletableFuture.supplyAsync(() -> drawMaps(dross, out));
        CompletableFuture<Map<ResourceKey<Biome>, BlockPos>> sites = CompletableFuture.supplyAsync(() -> TerrainChunksGameTest.findSites(TerrainProbe.forWorld(dross)));
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        JsonObject chunkStats = new JsonObject();
        GameTestSequence sequence = helper.startSequence()
                .thenWaitUntil(() -> {
                    if ((!maps.isDone() || !sites.isDone()) && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still drawing the maps");
                    }
                })
                .thenExecute(() -> {
                    if (!maps.isDone() || !sites.isDone())
                    {
                        throw new GameTestAssertException("drawing the maps took too long");
                    }
                    maps.join();
                    drawHubArea(dross, out, chunkStats);
                    dumpView(dross, HUB_VIEW_X, HUB_VIEW_Z, HUB_VIEW_HALF, "hub", "The hub (the Shattered Spire) in the Silverwood Forest");
                });
        for (ResourceKey<Biome> biome : DrossBiomes.ALL)
        {
            sequence = sequence.thenExecute(() -> {
                BlockPos site = sites.join().get(biome);
                if (site != null)
                {
                    long t = Util.getNanos();
                    List<LevelChunk> chunks = TerrainChunksGameTest.generate(dross, site);
                    double ms = (Util.getNanos() - t) / 1.0E6D / chunks.size();
                    chunkStats.addProperty("site_" + DrossBiomes.name(biome) + "_ms_per_chunk", round(ms));
                    chunkStats.addProperty("site_" + DrossBiomes.name(biome), site.getX() + ", " + site.getZ());
                    // centred on the generated chunks (the site's own chunk is the middle one)
                    drawBlocks(dross, ((site.getX() >> 4) << 4) + 8, ((site.getZ() >> 4) << 4) + 8, TerrainChunksGameTest.SITE_CHUNKS * 8, SITE_SCALE, null,
                            out.resolve("site_" + DrossBiomes.name(biome) + ".png"), DrossBiomes.name(biome) + " (" + site.getX() + ", " + site.getZ() + ")");
                    dumpView(dross, ((site.getX() >> 4) << 4) + 8, ((site.getZ() >> 4) << 4) + 8, VIEW_HALF, DrossBiomes.name(biome),
                            DrossBiomes.name(biome) + " (" + site.getX() + ", " + site.getZ() + ")");
                }
            });
        }
        sequence.thenExecute(() -> {
            JsonObject stats = maps.join();
            stats.add("chunks", chunkStats);
            stats.addProperty("preview_seconds", round((Util.getMillis() - started) / 1000.0D));
            write(out.resolve("stats.json"), new GsonBuilder().setPrettyPrinting().create().toJson(stats));
            LOGGER.info("[terrain previews] written to {} in {} s", out, String.format(Locale.ROOT, "%.1f", (Util.getMillis() - started) / 1000.0D));
        }).thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Maps from the noise (off the server thread)
    // ---------------------------------------------------------------------------------------------

    private static JsonObject drawMaps(ServerLevel dross, Path out)
    {
        try
        {
            Files.createDirectories(out);
            // Clear the last run's pictures (and any older run's that named this world's maps by its seed).
            try (var old = Files.list(out))
            {
                for (Path file : old.toList())
                {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".png") && (name.startsWith("biomes_") || name.startsWith("relief_") || name.startsWith("site_")
                            || name.equals("overview.png") || name.equals("hub_area.png")))
                    {
                        Files.delete(file);
                    }
                }
            }
        }
        catch (IOException e)
        {
            throw new IllegalStateException("can't make or clear " + out, e);
        }
        List<Long> seeds = new ArrayList<>();
        for (int i = 0; i < PREVIEW_FIXED_SEEDS; i++)
        {
            seeds.add(TerrainSamplingGameTest.TEST_SEEDS[i]);
        }
        seeds.add(dross.getSeed());
        List<BufferedImage> biomeMaps = new ArrayList<>();
        for (long seed : seeds)
        {
            TerrainProbe probe = seed == dross.getSeed() ? TerrainProbe.forWorld(dross) : TerrainProbe.forSeed(dross, seed);
            int n = MAP_HALF * 2 / MAP_CELL;
            int x0 = DrossHub.CENTER.getX() - MAP_HALF;
            int z0 = DrossHub.CENTER.getZ() - MAP_HALF;
            int[] biomes = TerrainSurvey.biomeGrid(probe, x0, z0, MAP_CELL, n, n);
            double[] heights = new double[n * n];
            IntStream.range(0, n).parallel().forEach(j -> {
                for (int i = 0; i < n; i++)
                {
                    heights[j * n + i] = probe.height(x0 + i * MAP_CELL, z0 + j * MAP_CELL);
                }
            });
            String label = "seed " + seed + (seed == dross.getSeed() ? " (this world)" : "");
            BufferedImage biomeMap = biomeImage(biomes, n);
            biomeMaps.add(biomeMap);
            // this world's seed changes every run: a fixed name, so a refresh replaces its maps instead of adding new files
            String fileName = seed == dross.getSeed() ? "world" : Long.toString(seed);
            saveWithLegend(biomeMap, out.resolve("biomes_" + fileName + ".png"), "Dross biomes, " + label, true);
            saveWithLegend(reliefImage(biomes, heights, n), out.resolve("relief_" + fileName + ".png"), "Dross relief, " + label, false);
        }
        saveOverview(biomeMaps, seeds, dross.getSeed(), out.resolve("overview.png"));

        // The numbers: every test seed and this world
        JsonObject stats = new JsonObject();
        JsonArray perSeed = new JsonArray();
        StringBuilder text = new StringBuilder("Dross terrain statistics (from the noise; see dimension/TerrainSurvey)\n\n");
        long[] all = Arrays.copyOf(TerrainSamplingGameTest.TEST_SEEDS, TerrainSamplingGameTest.TEST_SEEDS.length + 1);
        all[all.length - 1] = dross.getSeed();
        List<TerrainSurvey.SeedStats> surveys = new ArrayList<>();
        for (long seed : all)
        {
            long t = Util.getMillis();
            TerrainSurvey.SeedStats s = TerrainSurvey.survey(seed == dross.getSeed() ? TerrainProbe.forWorld(dross) : TerrainProbe.forSeed(dross, seed));
            surveys.add(s);
            perSeed.add(toJson(s, Util.getMillis() - t));
            text.append(s.summary()).append("\n\n");
        }
        stats.add("seeds", perSeed);
        JsonObject averages = new JsonObject();
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            int biome = b;
            JsonObject o = new JsonObject();
            o.addProperty("share", round(surveys.stream().mapToDouble(s -> s.shares()[biome]).average().orElse(0)));
            o.addProperty("region_size_area_weighted_median", round(surveys.stream().mapToDouble(s -> s.regions()[biome].weightedSize()).average().orElse(0)));
            o.addProperty("nearest_to_hub_max", round(surveys.stream().mapToDouble(s -> s.nearest()[biome]).max().orElse(0)));
            averages.add(TerrainSurvey.name(b), o);
        }
        List<Double> peaks = new ArrayList<>();
        surveys.forEach(s -> peaks.addAll(s.peaks()));
        averages.addProperty("mountain_ranges", peaks.size());
        averages.addProperty("mountain_peak_median", round(TerrainSurvey.percentile(peaks, 50)));
        stats.add("averages", averages);
        text.append("Averages over ").append(surveys.size()).append(" seeds: ").append(averages).append('\n');
        write(out.resolve("stats.txt"), text.toString());
        return stats;
    }

    private static JsonObject toJson(TerrainSurvey.SeedStats s, long millis)
    {
        JsonObject o = new JsonObject();
        o.addProperty("seed", s.seed());
        o.addProperty("all_silverwood_within", round(s.allSilverwoodRadius()));
        JsonObject biomes = new JsonObject();
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            JsonObject bo = new JsonObject();
            bo.addProperty("share", round(s.shares()[b]));
            TerrainSurvey.Regions r = s.regions()[b];
            bo.addProperty("regions", r.count());
            bo.addProperty("region_size_median", round(r.medianSize()));
            bo.addProperty("region_size_area_weighted_median", round(r.weightedSize()));
            bo.addProperty("region_size_largest", round(r.largest()));
            bo.addProperty("nearest_to_hub", s.nearest()[b] == Double.MAX_VALUE ? -1 : round(s.nearest()[b]));
            TerrainSurvey.Heights h = s.heights()[b];
            if (h != null)
            {
                bo.addProperty("height_p5", round(h.p5()));
                bo.addProperty("height_p50", round(h.p50()));
                bo.addProperty("height_p95", round(h.p95()));
                bo.addProperty("slope_p50", round(h.slope50()));
                bo.addProperty("slope_p95", round(h.slope95()));
            }
            biomes.add(TerrainSurvey.name(b), bo);
        }
        o.add("biomes", biomes);
        JsonArray peaks = new JsonArray();
        s.peaks().forEach(p -> peaks.add(round(p)));
        o.add("mountain_peaks", peaks);
        o.addProperty("height_min", round(s.minHeight()));
        o.addProperty("height_max", round(s.maxHeight()));
        o.addProperty("hub_ground_range", s.hubRange());
        o.addProperty("hub_ground_std", round(s.hubStd()));
        o.addProperty("hub_ground_steepest_step", s.hubSlope());
        o.addProperty("survey_ms", millis);
        return o;
    }

    private static BufferedImage biomeImage(int[] biomes, int n)
    {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                img.setRGB(i, j, biomeColor(biomes[j * n + i]));
            }
        }
        decorate(img, n);
        return img;
    }

    private static BufferedImage reliefImage(int[] biomes, double[] h, int n)
    {
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                int k = j * n + i;
                double here = h[k];
                double gx = (h[j * n + Math.min(n - 1, i + 1)] - h[j * n + Math.max(0, i - 1)]) / (2.0D * MAP_CELL);
                double gz = (h[Math.min(n - 1, j + 1) * n + i] - h[Math.max(0, j - 1) * n + i]) / (2.0D * MAP_CELL);
                double shade = clamp(0.85D + (-gx - gz) * 0.55D, 0.25D, 1.45D);
                double lift = 0.55D + 0.45D * clamp((here - 40.0D) / 180.0D, 0.0D, 1.0D);
                int base = biomeColor(biomes[k]);
                if (here >= SNOW_Y)
                {
                    base = mix(base, DrossColors.PREVIEW_SNOW, 0.75D);
                }
                double r = ((base >> 16) & 255) * shade * lift;
                double g = ((base >> 8) & 255) * shade * lift;
                double b = (base & 255) * shade * lift;
                boolean contour = (i + 1 < n && Math.floor(here / CONTOUR) != Math.floor(h[k + 1] / CONTOUR))
                        || (j + 1 < n && Math.floor(here / CONTOUR) != Math.floor(h[k + n] / CONTOUR));
                if (contour)
                {
                    r *= 0.62D;
                    g *= 0.62D;
                    b *= 0.62D;
                }
                img.setRGB(i, j, rgb(r, g, b));
            }
        }
        decorate(img, n);
        return img;
    }

    /** The hub's rings and its mark. */
    private static void decorate(BufferedImage img, int n)
    {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(DrossColors.PREVIEW_MARK));
        g.setStroke(new BasicStroke(1.2F));
        double c = n / 2.0D;
        for (int r : RINGS)
        {
            double pr = r / (double) MAP_CELL;
            g.drawOval((int) Math.round(c - pr), (int) Math.round(c - pr), (int) Math.round(2 * pr), (int) Math.round(2 * pr));
        }
        g.fillOval((int) c - 4, (int) c - 4, 8, 8);
        g.dispose();
    }

    private static void saveWithLegend(BufferedImage map, Path file, String title, boolean biomes)
    {
        int w = map.getWidth() + LEGEND_WIDTH;
        BufferedImage img = new BufferedImage(w, map.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(DrossColors.PREVIEW_BACKGROUND));
        g.fillRect(0, 0, w, map.getHeight());
        g.drawImage(map, 0, 0, null);
        int x = map.getWidth() + 14;
        int y = 28;
        g.setColor(new Color(DrossColors.PREVIEW_TEXT));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        for (String part : wrap(title, 26))
        {
            g.drawString(part, x, y);
            y += 20;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        y += 6;
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            g.setColor(new Color(biomeColor(b)));
            g.fillRect(x, y - 11, 16, 14);
            g.setColor(new Color(DrossColors.PREVIEW_TEXT));
            g.drawString(prettyName(b), x + 24, y);
            y += 22;
        }
        y += 10;
        String[] notes = biomes
                ? new String[] {MAP_HALF * 2 + " x " + MAP_HALF * 2 + " blocks,", MAP_CELL + " blocks a pixel; north is up.",
                        "Dot: the hub at 0, 0. Rings: 200,", "450, 600, 850 and 1500 blocks", "(Silverwood core, no mountains", "within ~650, mountain ring)."}
                : new String[] {"Lit from the north-west; higher", "is lighter; a line every " + CONTOUR + " blocks", "of height; white: snow above",
                        "y " + SNOW_Y + ". Rings as on the biome map."};
        for (String note : notes)
        {
            g.drawString(note, x, y);
            y += 18;
        }
        // Scale bar: 1000 blocks
        y += 16;
        int bar = 1000 / MAP_CELL;
        g.setColor(new Color(DrossColors.PREVIEW_MARK));
        g.fillRect(x, y, bar, 4);
        g.setColor(new Color(DrossColors.PREVIEW_TEXT));
        g.drawString("1000 blocks", x, y + 20);
        g.dispose();
        save(img, file);
    }

    private static void saveOverview(List<BufferedImage> maps, List<Long> seeds, long worldSeed, Path file)
    {
        int tile = 512;
        int cols = 2;
        int rows = (maps.size() + 1) / 2;
        BufferedImage img = new BufferedImage(cols * tile + (cols + 1) * 8, rows * (tile + 26) + 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(DrossColors.PREVIEW_BACKGROUND));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (int i = 0; i < maps.size(); i++)
        {
            int x = 8 + (i % cols) * (tile + 8);
            int y = 8 + (i / cols) * (tile + 26);
            g.setColor(new Color(DrossColors.PREVIEW_TEXT));
            g.drawString("seed " + seeds.get(i) + (seeds.get(i) == worldSeed ? " (this world)" : ""), x, y + 14);
            g.drawImage(maps.get(i), x, y + 20, tile, tile, null);
        }
        g.dispose();
        save(img, file);
    }

    // ---------------------------------------------------------------------------------------------
    // Real blocks (server thread)
    // ---------------------------------------------------------------------------------------------

    private static void drawHubArea(ServerLevel dross, Path out, JsonObject stats)
    {
        int cx = DrossHub.CENTER.getX();
        int cz = DrossHub.CENTER.getZ();
        long t = Util.getNanos();
        int chunks = 0;
        for (int x = (cx - HUB_HALF) >> 4; x <= (cx + HUB_HALF) >> 4; x++)
        {
            for (int z = (cz - HUB_HALF) >> 4; z <= (cz + HUB_HALF) >> 4; z++)
            {
                dross.getChunk(x, z);
                chunks++;
            }
        }
        stats.addProperty("hub_area_chunks", chunks);
        stats.addProperty("hub_area_ms_per_chunk", round((Util.getNanos() - t) / 1.0E6D / chunks));
        BoundingBox footprint = null;
        Optional<DrossHub.Layout> layout = DrossHub.layout(dross.getServer());
        if (layout.isPresent())
        {
            footprint = layout.get().footprint();
            DrossHub.Layout l = layout.get();
            stats.addProperty("hub", l.kind() + (l.fitted() ? " on fitted ground" : "") + ", ground level " + l.groundY() + ", footprint X "
                    + footprint.minX() + ".." + footprint.maxX() + ", Z " + footprint.minZ() + ".." + footprint.maxZ());
        }
        drawBlocks(dross, cx, cz, HUB_HALF, HUB_SCALE, footprint, out.resolve("hub_area.png"), "The hub, " + 2 * HUB_HALF + " blocks square (real blocks)");
    }

    /** The real blocks round x, z (half blocks every way), drawn like a Minecraft map, scaled up. */
    private static void drawBlocks(ServerLevel dross, int cx, int cz, int half, int scale, BoundingBox outline, Path file, String title)
    {
        int n = 2 * half;
        BufferedImage img = new BufferedImage(n * scale, n * scale + 24, BufferedImage.TYPE_INT_RGB);
        int[] top = new int[n * n];
        int[] color = new int[n * n];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                int x = cx - half + i;
                int z = cz - half + j;
                int y = dross.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                BlockState state = dross.getBlockState(pos.set(x, y, z));
                while (y > dross.getMinBuildHeight() && state.getMapColor(dross, pos).col == 0)
                {
                    state = dross.getBlockState(pos.set(x, --y, z));
                }
                top[j * n + i] = y;
                color[j * n + i] = state.getMapColor(dross, pos).col;
            }
        }
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(DrossColors.PREVIEW_BACKGROUND));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                int k = j * n + i;
                int north = j > 0 ? top[k - n] : top[k];
                double shade = top[k] > north ? 255.0D / 255.0D : top[k] < north ? 180.0D / 255.0D : 220.0D / 255.0D;
                boolean contour = (i + 1 < n && Math.floorDiv(top[k], BLOCK_CONTOUR) != Math.floorDiv(top[k + 1], BLOCK_CONTOUR))
                        || (j + 1 < n && Math.floorDiv(top[k], BLOCK_CONTOUR) != Math.floorDiv(top[k + n], BLOCK_CONTOUR));
                if (contour)
                {
                    shade *= 0.72D;
                }
                int c = color[k];
                g.setColor(new Color(rgb(((c >> 16) & 255) * shade, ((c >> 8) & 255) * shade, (c & 255) * shade)));
                g.fillRect(i * scale, 24 + j * scale, scale, scale);
            }
        }
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(DrossColors.PREVIEW_TEXT));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.drawString(title + "; ground y " + Arrays.stream(top).min().orElse(0) + ".." + Arrays.stream(top).max().orElse(0) + "; a line every "
                + BLOCK_CONTOUR + " up", 6, 17);
        if (outline != null)
        {
            g.setColor(new Color(DrossColors.PREVIEW_MARK));
            g.setStroke(new BasicStroke(2.0F));
            g.drawRect((outline.minX() - cx + half) * scale, 24 + (outline.minZ() - cz + half) * scale, outline.getXSpan() * scale,
                    outline.getZSpan() * scale);
        }
        g.dispose();
        save(img, file);
    }

    /**
     * Writes the real blocks of a square round x, z ({@code half} blocks every way) to build/tv1/views/{@code name}.json
     * for tools/terrain/preview_sites.py: from a few blocks under the lowest ground up to the highest block, only the
     * blocks that can be seen from above and the south-east (the rest are hidden behind others), each with the block
     * light next to it. Server thread.
     */
    private static void dumpView(ServerLevel dross, int cx, int cz, int half, String name, String title)
    {
        int n = 2 * half;
        int x0 = cx - half;
        int z0 = cz - half;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int[] ground = new int[n * n];
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                LevelChunk chunk = dross.getChunk((x0 + i) >> 4, (z0 + j) >> 4);
                int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, (x0 + i) & 15, (z0 + j) & 15);
                ground[j * n + i] = TerrainChunksGameTest.groundTop(chunk, x0 + i, z0 + j, surface, dross.getMinBuildHeight());
                lowest = Math.min(lowest, ground[j * n + i]);
                highest = Math.max(highest, surface);
            }
        }
        int y0 = lowest - VIEW_BELOW_GROUND;
        Map<String, Integer> palette = new java.util.LinkedHashMap<>();
        JsonArray blocks = new JsonArray();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                for (int y = y0; y <= highest; y++)
                {
                    BlockState state = dross.getBlockState(pos.set(x0 + i, y, z0 + j));
                    if (state.isAir() || hidden(dross, pos, next))
                    {
                        continue;
                    }
                    String key = net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state);
                    int index = palette.computeIfAbsent(key, k -> palette.size());
                    int light = Math.max(dross.getBrightness(LightLayer.BLOCK, pos),
                            Math.max(dross.getBrightness(LightLayer.BLOCK, next.setWithOffset(pos, 0, 1, 0)),
                                    Math.max(dross.getBrightness(LightLayer.BLOCK, next.setWithOffset(pos, 0, 0, 1)),
                                            dross.getBrightness(LightLayer.BLOCK, next.setWithOffset(pos, 1, 0, 0)))));
                    JsonArray b = new JsonArray();
                    b.add(i);
                    b.add(y - y0);
                    b.add(j);
                    b.add(index);
                    b.add(light);
                    blocks.add(b);
                }
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("note", "Written by TerrainPreviewGameTest; drawn by tools/terrain/preview_sites.py");
        root.addProperty("name", name);
        root.addProperty("title", title);
        root.addProperty("x0", x0);
        root.addProperty("y0", y0);
        root.addProperty("z0", z0);
        root.addProperty("size", n);
        root.addProperty("water_color", dross.getBiome(new BlockPos(cx, 80, cz)).value().getWaterColor());
        JsonArray names = new JsonArray();
        palette.keySet().forEach(names::add);
        root.add("palette", names);
        root.add("blocks", blocks);
        write(FMLPaths.GAMEDIR.get().toAbsolutePath().getParent().resolve("build").resolve("tv1").resolve("views").resolve(name + ".json"),
                new GsonBuilder().create().toJson(root));
    }

    /** True if the block can't be seen from above and the south-east: full opaque blocks above it, south of it and east of it. */
    private static boolean hidden(ServerLevel dross, BlockPos pos, BlockPos.MutableBlockPos next)
    {
        return opaque(dross, next.setWithOffset(pos, 0, 1, 0)) && opaque(dross, next.setWithOffset(pos, 0, 0, 1))
                && opaque(dross, next.setWithOffset(pos, 1, 0, 0));
    }

    private static boolean opaque(ServerLevel dross, BlockPos pos)
    {
        return dross.getBlockState(pos).isSolidRender(dross, pos);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static int biomeColor(int biome)
    {
        return switch (biome)
        {
            case 0 -> DrossColors.PREVIEW_SILVERWOOD;
            case 1 -> DrossColors.PREVIEW_CINDER;
            case 2 -> DrossColors.PREVIEW_BLIGHTWOOD;
            case 3 -> DrossColors.PREVIEW_RIMEFALL;
            case 4 -> DrossColors.PREVIEW_NETHER_SCAR;
            case 5 -> DrossColors.PREVIEW_GRAVECRAGS;
            default -> DrossColors.PREVIEW_OTHER;
        };
    }

    private static String prettyName(int biome)
    {
        String name = TerrainSurvey.name(biome).replace('_', ' ');
        StringBuilder s = new StringBuilder();
        for (String word : name.split(" "))
        {
            s.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return s.toString().trim();
    }

    private static List<String> wrap(String text, int width)
    {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" "))
        {
            if (line.length() + word.length() + 1 > width && line.length() > 0)
            {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0)
            {
                line.append(' ');
            }
            line.append(word);
        }
        lines.add(line.toString());
        return lines;
    }

    private static int mix(int a, int b, double t)
    {
        return rgb(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t, ((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t,
                (a & 255) * (1 - t) + (b & 255) * t);
    }

    private static int rgb(double r, double g, double b)
    {
        return ((int) clamp(r, 0, 255) << 16) | ((int) clamp(g, 0, 255) << 8) | (int) clamp(b, 0, 255);
    }

    private static double clamp(double v, double lo, double hi)
    {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round(double v)
    {
        return Math.round(v * 1000.0D) / 1000.0D;
    }

    private static void save(BufferedImage img, Path file)
    {
        try
        {
            Files.createDirectories(file.getParent());
            ImageIO.write(img, "png", file.toFile());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("can't write " + file, e);
        }
    }

    private static void write(Path file, String text)
    {
        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            throw new IllegalStateException("can't write " + file, e);
        }
    }
}
