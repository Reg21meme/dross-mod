package com.reg21meme.dross.perf;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.frostfolk.FolkRole;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.huntsman.HuntsmanPair;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageTeam;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * The server's performance test (the user, November 19, 2026: "add a performance test"). Each scenario fills a walled
 * floor in the sky with the mod's mobs, first standing still (their AI off) and then fighting (raged on two or more
 * teams, so they use their real moves), and times every server tick: its mean, its 95th percentile and its worst. A
 * scenario fails if the fight's mean tick goes over {@value #MAX_MEAN_MS} ms or its 95th percentile over
 * {@value #MAX_P95_MS} ms (a server has 50 ms a tick before the game slows down). The scenarios run one after another
 * (each its own batch): nothing but the floor, the Silverwood Giants, the Cinder Wastes' mobs, Rimefall's animals and
 * the Frostfolk, three bosses at once (the Cinder Colossus, the Rat King, the Huntsman and his Alpha Moose), and the
 * Emberdactyl and its young fighting in the air. A tick over {@value #SLOW_MS} ms is a slow tick: a catcher thread
 * writes down what the server thread was doing in it (its stack) and how much of it was Java's memory clean-up (GC).
 *
 * <p>Its own GameTest namespace, so the normal run leaves it out:
 * {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_perf -PgameTestWorld=<a new folder>}. The numbers go
 * to the log ("[perf]") and to {@code run/perf/server_perf.txt}. The GameTest server runs its ticks back to back (no
 * 50 ms wait), so a tick's time is all work. Other programs running at the same time make the numbers worse.
 */
@GameTestHolder(ServerPerfProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class ServerPerfProbeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String NAMESPACE = "dross_perf";
    private static final String EMPTY = "empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(NAMESPACE, EMPTY);

    /** A fight's mean tick may be at most this (ms)... */
    private static final double MAX_MEAN_MS = 25.0D;
    /** ...and its 95th percentile at most this. */
    private static final double MAX_P95_MS = 45.0D;
    /** The floor's height: open sky, well over any land of the test world. */
    private static final int PIT_Y = 220;
    private static final int WALL = 6;
    /** Ticks to let everything settle, then measured standing still, then measured fighting. */
    private static final int SETTLE = 400;
    private static final int STILL = 200;
    private static final int FIGHT = 1200;
    /** Everything in the pit is given this much health, so the fight lasts the whole minute. */
    private static final double HEALTH = 3000.0D;
    private static final int TIMEOUT = 6000;

    // ---------------------------------------------------------------- the tick timer

    /** A tick longer than this (ms) is a slow tick: the catcher writes down what the server thread was doing. */
    private static final long SLOW_MS = 250L;
    /** How many slow ticks the catcher writes down in one scenario (the rest are only counted). */
    private static final int SLOW_LOGGED = 4;

    private static volatile long tickStart;
    private static volatile long tickNumber;
    private static volatile List<Long> recording;
    private static volatile String scenario = "";
    private static volatile Thread serverThread;
    private static long gcAtStart;
    /** The catcher's notes on the tick going on now (its stack while slow), handed to the tick's end. */
    private static volatile String slowStack;
    private static volatile long slowStackTick = -1L;
    private static int slowLogged;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.START)
        {
            serverThread = Thread.currentThread();
            gcAtStart = gcMillis();
            tickNumber++;
            tickStart = System.nanoTime();
            startCatcher();
        }
        else if (recording != null && tickStart != 0L)
        {
            long took = System.nanoTime() - tickStart;
            recording.add(took);
            if (took / 1_000_000L >= SLOW_MS)
            {
                long gc = gcMillis() - gcAtStart;
                String stack = slowStackTick == tickNumber ? slowStack : "(not caught)";
                if (slowLogged++ < SLOW_LOGGED)
                {
                    LOGGER.warn("[perf] slow tick in {}: {} ms, {} ms of it Java's memory clean-up (GC); the server "
                            + "thread {} ms in:{}", scenario, took / 1_000_000L, gc, SLOW_MS, stack);
                    write("slow tick in " + scenario + ": " + took / 1_000_000L + " ms, GC " + gc + " ms; " + stack
                            .replace(System.lineSeparator(), " | "));
                }
            }
        }
    }

    private static long gcMillis()
    {
        long sum = 0L;
        for (java.lang.management.GarbageCollectorMXBean bean
                : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans())
        {
            sum += Math.max(0L, bean.getCollectionTime());
        }
        return sum;
    }

    private static Thread catcher;

    /** A thread that looks at the server thread every 20 ms: when a tick has run {@value #SLOW_MS} ms, its stack. */
    private static synchronized void startCatcher()
    {
        if (catcher != null)
        {
            return;
        }
        catcher = new Thread(() ->
        {
            while (true)
            {
                try
                {
                    Thread.sleep(20L);
                }
                catch (InterruptedException e)
                {
                    return;
                }
                Thread server = serverThread;
                long start = tickStart;
                long tick = tickNumber;
                if (recording == null || server == null || start == 0L || slowStackTick == tick
                        || (System.nanoTime() - start) / 1_000_000L < SLOW_MS)
                {
                    continue;
                }
                StringBuilder b = new StringBuilder();
                StackTraceElement[] trace = server.getStackTrace();
                for (int i = 0; i < Math.min(28, trace.length); i++)
                {
                    b.append(System.lineSeparator()).append("    at ").append(trace[i]);
                }
                if (tickNumber == tick)
                {
                    slowStack = b.toString();
                    slowStackTick = tick;
                }
            }
        }, "Dross perf slow-tick catcher");
        catcher.setDaemon(true);
        catcher.start();
    }

    /** One window's tick times: how many, mean, 95th percentile and worst (ms). */
    private record Stats(int ticks, double mean, double p95, double max)
    {
        static Stats of(List<Long> nanos)
        {
            if (nanos.isEmpty())
            {
                return new Stats(0, 0.0D, 0.0D, 0.0D);
            }
            long[] sorted = nanos.stream().mapToLong(Long::longValue).toArray();
            Arrays.sort(sorted);
            double mean = Arrays.stream(sorted).average().orElse(0.0D) / 1e6;
            double p95 = sorted[Math.min(sorted.length - 1, (int) Math.floor(sorted.length * 0.95D))] / 1e6;
            return new Stats(sorted.length, mean, p95, sorted[sorted.length - 1] / 1e6);
        }

        @Override
        public String toString()
        {
            return String.format(Locale.ROOT, "mean %.2f ms, 95%% %.2f ms, worst %.2f ms (%d ticks)", mean, p95, max, ticks);
        }
    }

    // ---------------------------------------------------------------- the scenarios

    /** One fighter to put in the pit: what it is, its team, and where (blocks from the middle, round a circle). */
    private record Fighter(Supplier<? extends EntityType<? extends Mob>> type, RageTeam team) {}

    @GameTest(template = EMPTY, batch = "dross_perf_a", timeoutTicks = TIMEOUT)
    public static void a_emptyFloor(GameTestHelper helper)
    {
        run(helper, "empty floor", 22, List.of(), List.of(), false);
    }

    @GameTest(template = EMPTY, batch = "dross_perf_b", timeoutTicks = TIMEOUT)
    public static void b_silverwoodGiants(GameTestHelper helper)
    {
        List<Fighter> f = new ArrayList<>();
        for (int i = 0; i < 6; i++)
        {
            f.add(new Fighter(ModEntities.SILVERWOOD_GIANT, RageTeam.BLUE));
            f.add(new Fighter(() -> EntityType.IRON_GOLEM, RageTeam.RED));
        }
        run(helper, "6 Silverwood Giants v 6 iron golems", 26, f, List.of(), false);
    }

    @GameTest(template = EMPTY, batch = "dross_perf_c", timeoutTicks = TIMEOUT)
    public static void c_cinderWastesMobs(GameTestHelper helper)
    {
        List<Fighter> f = new ArrayList<>();
        for (int i = 0; i < 3; i++)
        {
            f.add(new Fighter(ModEntities.PALLBEARER, RageTeam.BLUE));
        }
        for (int i = 0; i < 6; i++)
        {
            f.add(new Fighter(ModEntities.CINDER_HERMIT, RageTeam.BLUE));
        }
        for (int i = 0; i < 4; i++)
        {
            f.add(new Fighter(ModEntities.STONE_GOLEM, RageTeam.BLUE));
        }
        for (int i = 0; i < 2; i++)
        {
            f.add(new Fighter(ModEntities.LAVA_GOLEM, RageTeam.BLUE));
        }
        for (int i = 0; i < 8; i++)
        {
            f.add(new Fighter(() -> EntityType.IRON_GOLEM, RageTeam.RED));
        }
        run(helper, "3 Pallbearers, 6 Cinder Hermits, 6 golems v 8 iron golems", 30, f, List.of(), false);
    }

    @GameTest(template = EMPTY, batch = "dross_perf_d", timeoutTicks = TIMEOUT)
    public static void d_rimefallAndFrostfolk(GameTestHelper helper)
    {
        List<Fighter> f = new ArrayList<>();
        for (int i = 0; i < 8; i++)
        {
            f.add(new Fighter(ModEntities.RIMEHORN, RageTeam.BLUE));
        }
        for (int i = 0; i < 4; i++)
        {
            f.add(new Fighter(ModEntities.MOOSE, RageTeam.BLUE));
            f.add(new Fighter(ModEntities.POND_SKATER, RageTeam.BLUE));
        }
        for (FolkRole role : FolkRole.values())
        {
            f.add(new Fighter(ModEntities.FROSTFOLK.get(role), RageTeam.RED));
        }
        for (int i = 0; i < 4; i++)
        {
            f.add(new Fighter(() -> EntityType.IRON_GOLEM, RageTeam.RED));
        }
        run(helper, "8 Rimehorns, 4 moose, 4 Pond Skaters v " + FolkRole.values().length + " Frostfolk and 4 iron golems",
                30, f, List.of(), false);
    }

    @GameTest(template = EMPTY, batch = "dross_perf_e", timeoutTicks = TIMEOUT)
    public static void e_threeBosses(GameTestHelper helper)
    {
        List<Fighter> f = List.of(new Fighter(ModEntities.CINDER_COLOSSUS, RageTeam.BLUE),
                new Fighter(ModEntities.RAT_KING, RageTeam.RED));
        run(helper, "the Cinder Colossus v the Rat King v the Huntsman and his Alpha Moose", 34, f,
                List.of(RageTeam.GREEN), false);
    }

    @GameTest(template = EMPTY, batch = "dross_perf_f", timeoutTicks = TIMEOUT)
    public static void f_emberdactyls(GameTestHelper helper)
    {
        List<Fighter> f = new ArrayList<>();
        f.add(new Fighter(ModEntities.EMBERDACTYL, RageTeam.BLUE));
        f.add(new Fighter(ModEntities.EMBERYOUNG, RageTeam.RED));
        for (int i = 0; i < 4; i++)
        {
            f.add(new Fighter(() -> EntityType.IRON_GOLEM, RageTeam.GREEN));
        }
        run(helper, "the Emberdactyl v its young, 4 iron golems under them", 44, f, List.of(), true);
    }

    // ---------------------------------------------------------------- one scenario

    /**
     * Builds the pit (a stone floor {@code half} blocks each way from its middle, barrier walls), puts the fighters round
     * it on a circle, the Huntsman's pair too for each of {@code huntsmanTeams}, everything with its AI off; settles,
     * measures standing still, turns the AI on and rages them on their teams, measures the fight, writes the numbers,
     * clears up.
     */
    private static void run(GameTestHelper helper, String what, int half, List<Fighter> fighters,
                            List<RageTeam> huntsmanTeams, boolean open)
    {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        BlockPos o = new BlockPos(base.getX(), PIT_Y, base.getZ());
        scenario = what;
        slowLogged = 0;
        for (int x = -half; x <= half; x++)
        {
            for (int z = -half; z <= half; z++)
            {
                level.setBlock(o.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                for (int y = 0; y < WALL + 10; y++)
                {
                    level.setBlock(o.offset(x, y, z), edge && y < WALL ? Blocks.BARRIER.defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        Vec3 middle = Vec3.atBottomCenterOf(o);
        List<Mob> mobs = new ArrayList<>();
        List<RageTeam> teams = new ArrayList<>();
        double ring = half * 0.6D;
        int n = fighters.size() + huntsmanTeams.size();
        for (int i = 0; i < fighters.size(); i++)
        {
            Fighter fighter = fighters.get(i);
            Mob mob = fighter.type().get().create(level);
            if (mob == null)
            {
                throw new GameTestAssertException("couldn't make a " + fighter.type().get().getDescriptionId());
            }
            double a = 2.0D * Math.PI * i / n;
            Vec3 at = middle.add(Math.cos(a) * ring, 0.0D, Math.sin(a) * ring);
            float yaw = (float) Math.toDegrees(Math.atan2(-Math.cos(a), Math.sin(a)));
            mob.moveTo(at.x, at.y + (open && !(mob instanceof net.minecraft.world.entity.animal.IronGolem) ? 12.0D : 0.0D),
                    at.z, yaw, 0.0F);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.SPAWN_EGG, null, null);
            if (mob instanceof com.reg21meme.dross.boss.emberdactyl.EmberBoss boss)
            {
                boss.setHome(middle, null, null, 0.0F, 0L);
                boss.makeHostile();
            }
            prepare(mob);
            level.addFreshEntity(mob);
            mobs.add(mob);
            teams.add(fighter.team());
        }
        for (int i = 0; i < huntsmanTeams.size(); i++)
        {
            double a = 2.0D * Math.PI * (fighters.size() + i) / n;
            Vec3 at = middle.add(Math.cos(a) * ring, 0.0D, Math.sin(a) * ring);
            Huntsman h = HuntsmanPair.spawn(level, at, 0.0F);
            if (h == null)
            {
                throw new GameTestAssertException("couldn't make the Huntsman's pair");
            }
            prepare(h);
            mobs.add(h);
            teams.add(huntsmanTeams.get(i));
            if (h.getVehicle() instanceof Mob moose)
            {
                prepare(moose);
                mobs.add(moose);
                teams.add(huntsmanTeams.get(i));
            }
        }
        AABB box = new AABB(o).inflate(half + 4, 40, half + 4);
        List<Long> still = new ArrayList<>();
        List<Long> fight = new ArrayList<>();
        int[] peak = {0};
        String[] busiest = {""};
        helper.onEachTick(() ->
        {
            List<Entity> in = level.getEntities((Entity) null, box, e -> true);
            if (in.size() > peak[0])
            {
                peak[0] = in.size();
                java.util.Map<String, Integer> byType = new java.util.TreeMap<>();
                for (Entity e : in)
                {
                    byType.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), 1, Integer::sum);
                }
                busiest[0] = byType.toString();
            }
        });
        helper.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> recording = still)
                .thenIdle(STILL)
                .thenExecute(() ->
                {
                    recording = null;
                    for (int i = 0; i < mobs.size(); i++)
                    {
                        Mob mob = mobs.get(i);
                        mob.setNoAi(false);
                        if (!Rage.isRaged(mob))
                        {
                            Rage.enrage(mob, teams.get(i));
                        }
                    }
                    recording = fight;
                })
                .thenIdle(FIGHT)
                .thenExecute(() ->
                {
                    recording = null;
                    Stats s = Stats.of(still);
                    Stats f = Stats.of(fight);
                    int alive = (int) mobs.stream().filter(Entity::isAlive).count();
                    String line = String.format(Locale.ROOT, "%s: standing still %s; fighting %s; %d of %d still "
                                    + "standing, at most %d entities in the pit (minions, shots, effects, body parts): %s",
                            what, s, f, alive, mobs.size(), peak[0], busiest[0]);
                    LOGGER.info("[perf] {}", line);
                    write(line);
                    // everything it put in and everything they made, wherever they flew (the Emberdactyls once went
                    // higher than the pit's box and fought on through the scenarios after theirs)
                    mobs.forEach(Entity::discard);
                    AABB column = new AABB(o.getX() - half - 120, level.getMinBuildHeight(), o.getZ() - half - 120,
                            o.getX() + half + 120, level.getMaxBuildHeight(), o.getZ() + half + 120);
                    for (Entity e : level.getEntities((Entity) null, column, e -> !(e instanceof
                            net.minecraft.world.entity.player.Player) && (box.contains(e.position()) || e instanceof Mob
                            && Rage.isRaged(e) || e instanceof com.reg21meme.dross.boss.emberdactyl.FireTornado)))
                    {
                        e.discard();
                    }
                    if (f.mean() > MAX_MEAN_MS || f.p95() > MAX_P95_MS)
                    {
                        throw new GameTestAssertException(what + ": the fight's ticks are too slow (" + f + "; at most "
                                + MAX_MEAN_MS + " ms on average and " + MAX_P95_MS + " ms at the 95th percentile)");
                    }
                })
                .thenSucceed();
    }

    /** AI off, lots of health, kept: so it waits still until the fight and lasts it. */
    private static void prepare(Mob mob)
    {
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null)
        {
            health.setBaseValue(HEALTH);
            mob.setHealth(mob.getMaxHealth());
        }
    }

    private static void write(String line)
    {
        try
        {
            Path dir = FMLPaths.GAMEDIR.get().resolve("perf");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("server_perf.txt"), java.time.LocalDateTime.now().withNano(0) + "  " + line
                    + System.lineSeparator(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException e)
        {
            LOGGER.warn("[perf] couldn't write run/perf/server_perf.txt", e);
        }
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

    private ServerPerfProbeGameTest() {}
}
