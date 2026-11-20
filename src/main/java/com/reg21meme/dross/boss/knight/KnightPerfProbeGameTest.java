package com.reg21meme.dross.boss.knight;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * A timing probe, not a check (the user, November 19, 2026: "performance"): all ten elemental knights fighting iron
 * golems at once in a walled floor, and how long the server's ticks take, first with everything standing still (their
 * AI off), then fighting (each knight's moves, effects, spikes, rain and rocks), and the most block displays (its
 * spikes and crystals) standing at once. Its own GameTest namespace, so the normal run leaves it out:
 * {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_knight_probe -PgameTestWorld=<a new folder>}. The
 * numbers go to the log ("[knight perf probe]").
 */
@GameTestHolder(KnightPerfProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class KnightPerfProbeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String NAMESPACE = "dross_knight_probe";
    private static final String EMPTY = "empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(NAMESPACE, EMPTY);
    /** The floor's half-width (blocks) and its walls' height. */
    private static final int HALF = 22;
    private static final int WALL = 5;
    private static final int SETTLE = 100;
    private static final int STILL = 300;
    private static final int FIGHT = 1200;

    private KnightPerfProbeGameTest() {}

    @GameTest(template = EMPTY, batch = "dross_knight_probe", timeoutTicks = 20_000)
    public static void knightsFighting(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(BlockPos.ZERO).above(30);
        for (int x = -HALF; x <= HALF; x++)
        {
            for (int z = -HALF; z <= HALF; z++)
            {
                level.setBlock(o.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                for (int y = 0; y < WALL + 8; y++)
                {
                    level.setBlock(o.offset(x, y, z), edge && y < WALL ? Blocks.BARRIER.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        List<ElementalKnight> knights = new ArrayList<>();
        List<IronGolem> golems = new ArrayList<>();
        KnightKind[] kinds = KnightKind.values();
        for (int i = 0; i < kinds.length; i++)
        {
            double a = 2.0D * Math.PI * i / kinds.length;
            ElementalKnight k = ModEntities.KNIGHTS.get(kinds[i]).get().create(level);
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (k == null || g == null)
            {
                throw new GameTestAssertException("couldn't make the " + kinds[i] + " or its golem");
            }
            k.moveTo(o.getX() + 0.5D + Math.cos(a) * 14.0D, o.getY(), o.getZ() + 0.5D + Math.sin(a) * 14.0D, 0.0F, 0.0F);
            k.setInvulnerable(true);
            k.setNoAi(true);
            k.setPersistenceRequired();
            level.addFreshEntity(k);
            g.moveTo(o.getX() + 0.5D + Math.cos(a) * 7.0D, o.getY(), o.getZ() + 0.5D + Math.sin(a) * 7.0D, 0.0F, 0.0F);
            g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
            g.setHealth(1000.0F);
            g.setNoAi(true);
            g.setPersistenceRequired();
            level.addFreshEntity(g);
            knights.add(k);
            golems.add(g);
        }
        AABB box = new AABB(o).inflate(HALF + 2, 20, HALF + 2);
        int[] peakDisplays = {0};
        float[] still = {0.0F};
        helper.onEachTick(() ->
        {
            int displays = level.getEntitiesOfClass(Display.class, box).size();
            peakDisplays[0] = Math.max(peakDisplays[0], displays);
        });
        helper.startSequence()
                .thenIdle(SETTLE + STILL)
                .thenExecute(() ->
                {
                    still[0] = level.getServer().getAverageTickTime();
                    for (int i = 0; i < knights.size(); i++)
                    {
                        knights.get(i).setNoAi(false);
                        golems.get(i).setNoAi(false);
                        knights.get(i).setTarget(golems.get(i));
                        golems.get(i).setTarget(knights.get(i));
                    }
                })
                .thenIdle(FIGHT)
                .thenExecute(() ->
                {
                    float fighting = level.getServer().getAverageTickTime();
                    int alive = (int) golems.stream().filter(IronGolem::isAlive).count();
                    LOGGER.info("[knight perf probe] the server's average tick (last 100 ticks): {} ms with the ten "
                                    + "knights and ten golems standing still, {} ms with them fighting; at most {} block "
                                    + "displays (spikes, crystals) at once; {} of the golems still standing",
                            String.format("%.2f", still[0]), String.format("%.2f", fighting), peakDisplays[0], alive);
                    knights.forEach(ElementalKnight::discard);
                    golems.forEach(IronGolem::discard);
                    level.getEntitiesOfClass(Display.class, box).forEach(d -> d.discard());
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- the probe's empty template (made in memory)

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(EMPTY_ID);
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag ints(int... v)
    {
        ListTag out = new ListTag();
        for (int x : v)
        {
            out.add(IntTag.valueOf(x));
        }
        return out;
    }
}
