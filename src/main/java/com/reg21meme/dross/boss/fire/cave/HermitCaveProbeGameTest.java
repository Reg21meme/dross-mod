package com.reg21meme.dross.boss.fire.cave;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Patch;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * A probe, not a check (its own GameTest namespace, so the normal run leaves it out:
 * {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_hermit_probe -PgameTestWorld=<a new folder>}): plans
 * the hermit crab caves of the Cinder Wastes' first {@link #PATCHES} patches for {@link #SEEDS} seeds and logs how many
 * are hillside and underground ones, how much of a hillside one's shell its hill covers (from the noise), and how long a
 * patch takes to plan.
 */
@GameTestHolder(HermitCaveProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HermitCaveProbeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String NAMESPACE = "dross_hermit_probe";
    private static final String EMPTY = "empty";
    private static final int PATCHES = 12;
    private static final int SEEDS = 6;
    private static final long TIMEOUT_MILLIS = 20 * 60 * 1000L;

    private HermitCaveProbeGameTest() {}

    @GameTest(template = EMPTY, batch = "dross_hermit_probe", timeoutTicks = 2_000_000)
    public static void hermitCavePlans(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        CompletableFuture<Void> run = CompletableFuture.runAsync(() -> probe(dross), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.succeedWhen(() -> {
            if (!run.isDone() && Util.getMillis() < deadline)
            {
                throw new GameTestAssertException("probing");
            }
            run.join();
        });
    }

    /** The nearest real cave of each kind, generated with the land and checked as the main test does (with a slice). */
    @GameTest(template = EMPTY, batch = "dross_hermit_probe_hill", timeoutTicks = 2_000_000)
    public static void hillsideCaveInTheWorld(GameTestHelper helper)
    {
        HermitCaveGameTest.caveIsBuiltInTheWorld(helper, HermitCaveDesigns.Kind.HILLSIDE);
    }

    @GameTest(template = EMPTY, batch = "dross_hermit_probe_flat", timeoutTicks = 2_000_000)
    public static void undergroundCaveInTheWorld(GameTestHelper helper)
    {
        HermitCaveGameTest.caveIsBuiltInTheWorld(helper, HermitCaveDesigns.Kind.UNDERGROUND);
    }

    private static void probe(ServerLevel dross)
    {
        int hill = 0;
        int flat = 0;
        int patches = 0;
        int none = 0;
        double coverSum = 0.0D;
        long nanos = 0L;
        for (int s = 0; s < SEEDS; s++)
        {
            long seed = dross.getSeed() + 7919L * s;
            BossSites sites = BossSites.forSeed(dross, seed);
            for (Patch patch : sites.patches(DrossBiomes.CINDER_WASTES, PATCHES))
            {
                sites.site(BossSites.COLOSSUS_LAIR, patch);         // (planned first, so only the caves are timed)
                sites.site(BossSites.EMBER_VOLCANO, patch);
                long started = System.nanoTime();
                List<HermitCaveSites.CavePlan> plan = HermitCaveSites.plan(sites, patch);
                nanos += System.nanoTime() - started;
                patches++;
                none += plan.isEmpty() ? 1 : 0;
                StringBuilder line = new StringBuilder();
                for (HermitCaveSites.CavePlan cave : plan)
                {
                    BlockPos m = cave.middle();
                    if (cave.cave().hillside())
                    {
                        hill++;
                        double cover = HermitCaveSites.hillCover(sites.terrain(), cave.cave(), m.getX(), m.getZ(), cave.front());
                        coverSum += cover;
                        line.append(String.format(" %s at %d %d facing %s (hill over it %.0f%%)", cave.cave().id(), m.getX(),
                                m.getZ(), cave.front().getName(), 100.0D * cover));
                    }
                    else
                    {
                        flat++;
                        line.append(String.format(" %s at %d %d", cave.cave().id(), m.getX(), m.getZ()));
                    }
                }
                LOGGER.info("[hermit probe] seed {} patch {} ({} deep):{}", seed, patch.number(), Math.round(patch.depth()), line);
            }
        }
        LOGGER.info("[hermit probe] {} patches of {} seeds: {} hillside caves (the hill over them {}% on average), {} underground, "
                        + "{} patches with none; {} ms a patch to plan", patches, SEEDS, hill,
                hill == 0 ? 0 : Math.round(100.0D * coverSum / hill), flat, none, nanos / 1_000_000L / Math.max(1, patches));
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
        tag.put("size", intList(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", intList(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag intList(int... values)
    {
        ListTag list = new ListTag();
        for (int v : values)
        {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
