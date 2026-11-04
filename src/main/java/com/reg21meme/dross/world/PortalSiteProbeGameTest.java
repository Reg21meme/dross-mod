package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
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
 * A probe, not a check: works out the portal site's spot ({@link SiteFinder#compute}) for a dozen fixed seeds with this
 * Overworld's generator, and logs which rule found each, why the others' spots were turned down, and how long each took
 * (to tune {@link SiteRules}). Its own GameTest namespace, so the normal run leaves it out:
 * {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_site_probe -PgameTestWorld=<a new folder> --console=plain}.
 */
@GameTestHolder(PortalSiteProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = com.reg21meme.dross.Dross.MODID)
public final class PortalSiteProbeGameTest
{
    static final String NAMESPACE = "dross_site_probe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "site_probe_empty";
    /** The seeds probed. */
    private static final long[] SEEDS = {1L, 2L, 3L, 42L, 1234L, 99999L, 123456789L, -5L, 2024L, 77777L, 31415926L, 8675309L,
            -1752286405569615815L, 1752286405569615815L, 6L, 7L};

    private PortalSiteProbeGameTest() {}

    @GameTest(template = EMPTY, timeoutTicks = 1_000_000)
    public static void siteSpotsForManySeeds(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        if (!(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
        {
            throw new GameTestAssertException("the Overworld isn't a noise world");
        }
        int[] rungs = new int[SiteRules.LAST_RESORT + 1];
        long total = 0L;
        long worst = 0L;
        List<String> lines = new ArrayList<>();
        for (long seed : SEEDS)
        {
            RandomState random = RandomState.create(generator.generatorSettings().value(),
                    overworld.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            ChunkGeneratorStructureState structures = ChunkGeneratorStructureState.createForNormal(random, seed, generator.getBiomeSource(),
                    overworld.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET));
            SiteFinder.Context ctx = SiteFinder.Context.of(overworld, generator, random, structures);
            long start = System.nanoTime();
            SiteSpot spot = SiteFinder.compute(ctx);
            long millis = (System.nanoTime() - start) / 1_000_000L;
            total += millis;
            worst = Math.max(worst, millis);
            rungs[Math.min(spot.rung(), SiteRules.LAST_RESORT)]++;
            String line = String.format(Locale.ROOT, "seed %d: %s", seed, spot.describe());
            lines.add(line);
            LOGGER.info("[site probe] {}", line);
        }
        StringBuilder histogram = new StringBuilder();
        for (int i = 0; i < rungs.length; i++)
        {
            histogram.append(i == 0 ? "" : ", ").append("rule ").append(i).append(": ").append(rungs[i]);
        }
        LOGGER.info("[site probe] {} seeds: {}; {} ms on average, the slowest {} ms", SEEDS.length, histogram, total / SEEDS.length, worst);
        helper.succeed();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, EMPTY));
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
