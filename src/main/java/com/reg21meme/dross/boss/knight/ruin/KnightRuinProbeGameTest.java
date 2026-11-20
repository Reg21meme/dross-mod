package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.knight.KnightKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * A probe, not a check: plans the knight ruins ({@link KnightRuinSites}) for fixed seeds with this Overworld's generator
 * and logs how many there are round the spawn, their elements and areas, how far apart, the near-spawn ones, any pair
 * breaking the rules, and how long it took. For the first seeds it writes the biome map (a cell's code a byte) and the
 * ruins to {@code run/probe/knight_ruins/} for {@code tools/knights/ruin_map.py} to draw. Its own GameTest namespace:
 * {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_ruin_probe -PgameTestWorld=<a new folder> --console=plain}.
 */
@GameTestHolder(KnightRuinProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = com.reg21meme.dross.Dross.MODID)
public final class KnightRuinProbeGameTest
{
    static final String NAMESPACE = "dross_ruin_probe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "ruin_probe_empty";
    private static final long[] SEEDS = {1L, 2L, 42L, 1234L, 99999L, 123456789L, -5L, 8675309L};
    /** Ruins counted this far round the spawn. */
    private static final int RADIUS = 3200;
    /** Maps written for this many seeds. */
    private static final int MAPS = 4;

    private KnightRuinProbeGameTest() {}

    @GameTest(template = EMPTY, timeoutTicks = 4_000_000)
    public static void ruinsForManySeeds(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        if (!(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
        {
            throw new GameTestAssertException("the Overworld isn't a noise world");
        }
        Path out = overworld.getServer().getServerDirectory().toPath().resolve("probe").resolve("knight_ruins");
        int index = 0;
        int totalRuins = 0;
        int totalBreaches = 0;
        long totalMillis = 0L;
        Map<String, Integer> areas = new TreeMap<>();
        for (long seed : SEEDS)
        {
            RandomState random = RandomState.create(generator.generatorSettings().value(),
                    overworld.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            KnightRuinSites.Context ctx = KnightRuinSites.Context.forSeed(overworld, generator, random, seed);
            KnightRuinSites.World w = KnightRuinSites.fresh(ctx);
            long start = System.nanoTime();
            long tiles0 = KnightRuinSites.TILE_NANOS.get();
            long reads0 = KnightRuinSites.HEIGHT_READS.get();
            List<KnightRuinSites.Candidate> list = KnightRuinSites.standing(w, ctx.spawnX(), ctx.spawnZ(), RADIUS);
            long millis = (System.nanoTime() - start) / 1_000_000L;
            totalMillis += millis;
            LOGGER.info("[ruin probe] seed {}: {} ms reading the biome map, {} heights read, {} regions' candidates", seed,
                    (KnightRuinSites.TILE_NANOS.get() - tiles0) / 1_000_000L, KnightRuinSites.HEIGHT_READS.get() - reads0, w.candidates.size());
            Map<KnightKind.Element, Integer> elements = new EnumMap<>(KnightKind.Element.class);
            List<String> near = new ArrayList<>();
            double spacing = 0;
            int breaches = 0;
            for (KnightRuinSites.Candidate c : list)
            {
                elements.merge(c.site().element(), 1, Integer::sum);
                areas.merge(c.site().design().area().id(), 1, Integer::sum);
                if (c.site().nearSpawn())
                {
                    near.add(c.site().design().area().id() + " " + (int) ctx.fromSpawn(c.site().x(), c.site().z()));
                }
                double nearest = Double.MAX_VALUE;
                for (KnightRuinSites.Candidate o : list)
                {
                    if (o != c)
                    {
                        nearest = Math.min(nearest, KnightRuinSites.dist(c.site().x(), c.site().z(), o.site().x(), o.site().z()));
                    }
                }
                spacing += nearest == Double.MAX_VALUE ? 0 : nearest;
            }
            for (int i = 0; i < list.size(); i++)
            {
                for (int j = i + 1; j < list.size(); j++)
                {
                    String b = KnightRuinSites.breach(w, list.get(i), list.get(j));
                    if (b != null)
                    {
                        breaches++;
                        LOGGER.warn("[ruin probe] seed {}: {} and {} {}", seed, list.get(i).site().describe(), list.get(j).site().describe(), b);
                    }
                }
            }
            totalRuins += list.size();
            totalBreaches += breaches;
            double area = Math.PI * RADIUS * RADIUS;
            LOGGER.info(String.format(Locale.ROOT, "[ruin probe] seed %d: %d ruins within %d of the spawn %d %d (one per %.0f x %.0f blocks; "
                            + "nearest neighbour %.0f on average), %d near the spawn [%s], %s, %d breaches, %d ms", seed, list.size(), RADIUS,
                    ctx.spawnX(), ctx.spawnZ(), Math.sqrt(area / Math.max(1, list.size())), Math.sqrt(area / Math.max(1, list.size())),
                    spacing / Math.max(1, list.size()), near.size(), String.join(", ", near), elements, breaches, millis));
            if (index++ < MAPS)
            {
                writeMap(out, seed, w, list);
            }
        }
        LOGGER.info("[ruin probe] {} seeds: {} ruins, {} breaches, {} ms on average; by area: {}", SEEDS.length, totalRuins, totalBreaches,
                totalMillis / SEEDS.length, areas);
        helper.succeed();
    }

    /** The cells round the spawn (a byte each, x then z) and the ruins, for tools/knights/ruin_map.py. */
    private static void writeMap(Path out, long seed, KnightRuinSites.World w, List<KnightRuinSites.Candidate> list)
    {
        KnightRuinSites.Context ctx = w.ctx;
        int cell = KnightRuinTuning.CELL;
        int half = (RADIUS + 400) / cell;
        int cx0 = Math.floorDiv(ctx.spawnX(), cell) - half;
        int cz0 = Math.floorDiv(ctx.spawnZ(), cell) - half;
        int n = half * 2 + 1;
        byte[] codes = new byte[n * n];
        for (int i = 0; i < n; i++)
        {
            for (int j = 0; j < n; j++)
            {
                codes[i * n + j] = KnightRuinSites.cell(w, cx0 + i, cz0 + j);
            }
        }
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT, "seed %d spawn %d %d cells %d %d %d size %d near %d %d %d radius %d%n", seed, ctx.spawnX(), ctx.spawnZ(),
                cx0, cz0, n, cell, ctx.nearRadius(), ctx.nearMin(), ctx.spacing(), RADIUS));
        for (KnightRuinSites.Candidate c : list)
        {
            text.append(String.format(Locale.ROOT, "ruin %d %d %d %s %s %s %d %d%n", c.site().x(), c.site().z(), c.site().groundY(),
                    c.site().design().area().id(), c.site().design().id(), c.site().nearSpawn() ? "near" : "far", c.cellX(), c.cellZ()));
        }
        try
        {
            Files.createDirectories(out);
            Files.write(out.resolve("seed_" + seed + ".cells"), codes);
            Files.writeString(out.resolve("seed_" + seed + ".txt"), text.toString(), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            LOGGER.error("[ruin probe] couldn't write the map", e);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        emptyTemplate(event.getServer(), new ResourceLocation(NAMESPACE, EMPTY));
        if (!KnightRuinWorldGameTest.NAMESPACE.equals(com.reg21meme.dross.Dross.MODID))
        {
            // (the world tests' own namespace, to run only them: the empty template they're built on)
            emptyTemplate(event.getServer(), new ResourceLocation(KnightRuinWorldGameTest.NAMESPACE,
                    com.reg21meme.dross.world.PortalSiteGameTest.EMPTY));
        }
    }

    /** A 1x1x1 air template, made in memory. */
    static void emptyTemplate(net.minecraft.server.MinecraftServer server, ResourceLocation id)
    {
        StructureTemplate template = server.getStructureManager().getOrCreate(id);
        CompoundTag tag = new CompoundTag();
        ListTag size = new ListTag();
        ListTag pos = new ListTag();
        for (int i = 0; i < 3; i++)
        {
            size.add(IntTag.valueOf(1));
            pos.add(IntTag.valueOf(0));
        }
        tag.put("size", size);
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", pos);
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }
}
