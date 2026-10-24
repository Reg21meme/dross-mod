package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * A probe, not a check (namespace {@code dross_probe}: {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_probe
 * -PgameTestWorld=<a new folder>}): the nearest Huntsman's village and the nearest regular one generated, then a map of
 * each from above (its top blocks, shaded by height: run/probe/village_<id>.png) and a line of numbers in the log (its
 * zones' levels, the stairs on its lanes, the world's trees left standing in it). For looking at how each part of a
 * village sits on its own ground.
 */
@GameTestHolder("dross_probe")
@PrefixGameTestTemplate(false)
public final class VillageMapProbeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_MILLIS = 600_000L;
    private static final int PIXELS = 3;

    private VillageMapProbeGameTest() {}

    @GameTest(template = "empty", batch = "dross_probe_village_map", timeoutTicks = 72_000)
    public static void villageMaps(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("no Dross");
        }
        CompletableFuture<Optional<Site>> huntsman = CompletableFuture.supplyAsync(
                () -> BossSites.forLevel(dross).nearest(BossSites.HUNTSMAN_VILLAGE, BlockPos.ZERO, 12_000), Util.backgroundExecutor());
        CompletableFuture<Optional<Site>> hunter = CompletableFuture.supplyAsync(
                () -> BossSites.forLevel(dross).nearest(BossSites.HUNTER_VILLAGE, BlockPos.ZERO, 12_000), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!(huntsman.isDone() && hunter.isDone()) && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("waiting for the villages to be planned");
                    }
                })
                .thenExecute(() -> {
                    for (Optional<Site> site : new Optional[] {huntsman.join(), hunter.join()})
                    {
                        if (site.isPresent() && site.get().detail() instanceof RimefallVillages.VillagePlan plan)
                        {
                            map(dross, plan);
                        }
                    }
                })
                .thenSucceed();
    }

    private static void map(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        BoundingBox box = plan.coverBox(0);
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
        {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
            {
                dross.getChunk(cx, cz);
            }
        }
        int w = box.maxX() - box.minX() + 1;
        int d = box.maxZ() - box.minZ() + 1;
        int[][] top = new int[w][d];
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int x = 0; x < w; x++)
        {
            for (int z = 0; z < d; z++)
            {
                top[x][z] = dross.getHeight(Heightmap.Types.MOTION_BLOCKING, box.minX() + x, box.minZ() + z) - 1;
                low = Math.min(low, top[x][z]);
                high = Math.max(high, top[x][z]);
            }
        }
        BufferedImage img = new BufferedImage(w * PIXELS, d * PIXELS, BufferedImage.TYPE_INT_RGB);
        int stairs = 0;
        int logs = 0;
        HunterVillageZones.Layout zones = plan.zones();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < w; x++)
        {
            for (int z = 0; z < d; z++)
            {
                int wx = box.minX() + x;
                int wz = box.minZ() + z;
                BlockState st = dross.getBlockState(pos.set(wx, top[x][z], wz));
                int rgb = colour(st);
                double shade = 0.55D + 0.45D * (top[x][z] - low) / Math.max(1.0D, high - low);
                int r = (int) (((rgb >> 16) & 255) * shade);
                int g = (int) (((rgb >> 8) & 255) * shade);
                int b = (int) ((rgb & 255) * shade);
                BlockPos local = plan.toLocal(wx, wz);
                int zone = zones == null ? -1 : zones.zone(false, local.getX(), local.getZ());
                boolean edge = false;
                if (zones != null)
                {
                    for (int[] o : new int[][] {{1, 0}, {0, 1}})
                    {
                        if (zones.zone(false, local.getX() + o[0], local.getZ() + o[1]) != zone)
                        {
                            edge = true;
                        }
                    }
                }
                if (edge)
                {
                    r = r * 2 / 3;
                    g = g * 2 / 3;
                    b = b * 2 / 3;
                }
                for (int px = 0; px < PIXELS; px++)
                {
                    for (int pz = 0; pz < PIXELS; pz++)
                    {
                        img.setRGB(x * PIXELS + px, z * PIXELS + pz, (r << 16) | (g << 8) | b);
                    }
                }
                if (zone >= 0 && zones.get(zone).kind == HunterVillageZones.Kind.LANE)
                {
                    int level = zones.level(plan.levels(), zone, local.getX(), local.getZ());
                    if (dross.getBlockState(pos.set(wx, level - 2, wz)).getBlock() instanceof StairBlock)
                    {
                        stairs++;
                    }
                }
                if (zone < 0 && plan.footprint().isInside(wx, plan.ground().getY(), wz) && st.is(BlockTags.LOGS))
                {
                    logs++;
                }
            }
        }
        TreeSet<Integer> courts = new TreeSet<>();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        if (zones != null && plan.levels() != null)
        {
            for (int i = 0; i < zones.zones(); i++)
            {
                int l = plan.levels()[i];
                if (l == HunterVillageZones.NONE)
                {
                    continue;
                }
                lowest = Math.min(lowest, l);
                highest = Math.max(highest, l);
                if (zones.get(i).court >= 0)
                {
                    courts.add(l);
                }
            }
        }
        File out = new File("probe", "village_" + plan.design().id() + ".png");
        out.getParentFile().mkdirs();
        try
        {
            ImageIO.write(img, "png", out);
        }
        catch (Exception e)
        {
            LOGGER.error("[village map] couldn't write {}", out, e);
        }
        LOGGER.info("[village map] {} at {} facing {}: courtyards at {}, zones from {} to {} (middle {}), {} lane stairs, "
                        + "{} columns of the world's (fallen) trees inside its footprint; land {} to {}; map {}",
                plan.design().id(), plan.ground().toShortString(), plan.front().getName(), courts, lowest, highest,
                plan.ground().getY(), stairs, logs, low, high, out.getAbsolutePath());
    }

    private static int colour(BlockState st)
    {
        if (st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.SNOW) || st.is(Blocks.POWDER_SNOW))
        {
            return 0xF2F6FA;
        }
        if (st.is(BlockTags.ICE))
        {
            return 0x9CC8F0;
        }
        if (st.is(BlockTags.LEAVES))
        {
            return 0x3E7A3A;
        }
        if (st.is(BlockTags.LOGS))
        {
            return 0x6B4A2E;
        }
        if (st.is(BlockTags.PLANKS) || st.is(BlockTags.WOODEN_STAIRS) || st.is(BlockTags.WOODEN_FENCES)
                || st.is(BlockTags.WOODEN_SLABS))
        {
            return 0xB08A58;
        }
        if (st.is(Blocks.WATER))
        {
            return 0x2F5FA8;
        }
        if (st.is(Blocks.GRAVEL))
        {
            return 0x9A9590;
        }
        if (st.is(Blocks.LANTERN) || st.is(Blocks.SOUL_LANTERN) || st.is(Blocks.CAMPFIRE))
        {
            return 0xFFD040;
        }
        if (st.is(Blocks.BONE_BLOCK))
        {
            return 0xE8E0C8;
        }
        String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock()));
        if (id.contains("hoarfrost"))
        {
            return 0xC8D8E0;
        }
        if (id.contains("stone") || id.contains("crag"))
        {
            return 0x7A7E84;
        }
        return 0xC060C0;
    }
}
