package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossConfig;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
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
 * A probe, not a check: works out the trader's home ({@link TraderVillages#plan}) for 20 fixed seeds with this Overworld's
 * generator, and logs, for each, whether his chapel is at a village (and which of the nearest), how far from the spawn
 * point, how many possible villages were tried and laid out, and how long it took. Its own GameTest namespace, so the
 * normal run leaves it out:
 * {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_trader_probe -PgameTestWorld=<a new folder> --console=plain}.
 */
@GameTestHolder(TraderVillageProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderVillageProbeGameTest
{
    static final String NAMESPACE = "dross_trader_probe";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "trader_probe_empty";
    /** The seeds probed (the portal site probe's, and four more). */
    private static final long[] SEEDS = {1L, 2L, 3L, 42L, 1234L, 99999L, 123456789L, -5L, 2024L, 77777L, 31415926L, 8675309L,
            -1752286405569615815L, 1752286405569615815L, 6L, 7L, 20261104L, 20261105L, 2943246284894409446L, 424242L};

    private TraderVillageProbeGameTest() {}

    @GameTest(template = EMPTY, timeoutTicks = 1_000_000)
    public static void traderHomesForManySeeds(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        if (!(overworld.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
        {
            throw new GameTestAssertException("the Overworld isn't a noise world");
        }
        int atVillage = 0;
        int onItsOwn = 0;
        int none = 0;
        long total = 0L;
        long worst = 0L;
        long distances = 0L;
        int tries = 0;
        for (long seed : SEEDS)
        {
            RandomState random = RandomState.create(generator.generatorSettings().value(),
                    overworld.registryAccess().lookupOrThrow(Registries.NOISE), seed);
            ChunkGeneratorStructureState structures = ChunkGeneratorStructureState.createForNormal(random, seed, generator.getBiomeSource(),
                    overworld.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET));
            ChunkPos spawn = new ChunkPos(random.sampler().findSpawnPosition());
            TraderVillages.Context ctx = new TraderVillages.Context(generator, random, structures, overworld.getServer().registryAccess(),
                    overworld.getServer().getStructureManager(), LevelHeightAccessor.create(overworld.getMinBuildHeight(), overworld.getHeight()),
                    seed, spawn.getMiddleBlockX(), spawn.getMiddleBlockZ(), true, DrossConfig.DEFAULT_TRADER_SEARCH_RADIUS);
            long start = System.nanoTime();
            List<TraderVillages.Cell> possible = TraderVillages.possibleVillages(ctx);
            Optional<RiftChapels.Home> home = TraderVillages.plan(ctx, possible);
            long millis = (System.nanoTime() - start) / 1_000_000L;
            total += millis;
            worst = Math.max(worst, millis);
            if (home.isEmpty())
            {
                none++;
            }
            else if (home.get().inVillage())
            {
                atVillage++;
                distances += home.get().distance();
                tries += home.get().looked();
            }
            else
            {
                onItsOwn++;
            }
            LOGGER.info("[trader probe] seed {}: {} possible villages; {} ({} ms)", seed, possible.size(),
                    home.map(RiftChapels.Home::describe).orElse("no chapel"), millis);
        }
        LOGGER.info(String.format(Locale.ROOT, "[trader probe] %d seeds: at a village %d (on average %d blocks from the spawn point, %.1f possible "
                        + "villages tried), on its own %d, none %d; %d ms on average, the slowest %d ms", SEEDS.length, atVillage,
                atVillage == 0 ? 0 : distances / atVillage, atVillage == 0 ? 0.0 : tries / (double) atVillage, onItsOwn, none,
                total / SEEDS.length, worst));
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
