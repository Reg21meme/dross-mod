package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the knight ruins in the Overworld ({@link KnightRuinSites}, {@link KnightRuinPiece},
 * {@link KnightRuinKeeper}). The test server's Overworld is a normal world with a random seed. They only exist in the
 * development environment.
 * <ul>
 *   <li>{@link #ruinsArePlanned}: the ruins near the spawn (at least one; each in its ring round the spawn, in its own
 *       area's biome, no thunder, facing the spawn, none in one or touching patches), and every ruin within 2,400 blocks
 *       in its element's biomes and keeping the rules (one patch, touching patches, 300 blocks apart); planned again from
 *       nothing, the same.</li>
 *   <li>{@link #ruinIsBuiltAndGuarded}: the nearest ruin away from the spawn made with its land: its structure start, its
 *       broken frame (6 or more frame blocks, never lit); a player near: after its tidy-up at least 97% of its template's
 *       blocks in place and its guard spots standing room, then its two knights come, on its guard spots, its element's, keeping to
 *       it; both beaten: they don't come back at once, nor 10 minutes later with a player within 64 blocks, but do with the
 *       player 80 blocks off; the player near was told it was cleared ({@link KnightRuinClearedEvent}).</li>
 * </ul>
 */
@GameTestHolder(KnightRuinWorldGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class KnightRuinWorldGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The tests' GameTest namespace: the mod's ("dross_ruins" to run only these). */
    static final String NAMESPACE = Dross.MODID;
    /** Their own batch (the ruin showcase's test is "dross_knight_ruins": together, their load slowed its land's loading). */
    private static final String BATCH = "dross_knight_ruins_world";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_knight_ruin_test", Comparator.comparingLong(ChunkPos::toLong));
    private static final long WAIT_MILLIS = 360_000L;
    private static final int TIMEOUT_TICKS = 4_000_000;
    /** The planning test looks this far round the spawn. */
    private static final int PLAN_RADIUS = 2400;
    /** At least this share of the template's blocks (not air) must be in place. */
    private static final double TEMPLATE_MIN_MATCH = 0.97D;

    private KnightRuinWorldGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The plan
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void ruinsArePlanned(GameTestHelper helper)
    {
        Steps steps = new Steps(helper);
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        KnightRuinSites.World w = KnightRuinSites.work(overworld);
        if (w == null || !w.ctx.available())
        {
            throw new GameTestAssertException("the test world has no knight ruins (it should generate them)");
        }
        KnightRuinSites.Context ctx = w.ctx;
        CompletableFuture<List<KnightRuinSites.Candidate>> planned = CompletableFuture.supplyAsync(
                () -> KnightRuinSites.standing(w, ctx.spawnX(), ctx.spawnZ(), PLAN_RADIUS));
        CompletableFuture<List<KnightRuinSites.Candidate>> again = CompletableFuture.supplyAsync(
                () -> KnightRuinSites.standing(KnightRuinSites.fresh(ctx), ctx.spawnX(), ctx.spawnZ(), PLAN_RADIUS));
        steps.watch();
        helper.startSequence()
                .thenWaitUntil(() -> steps.waitFor("the plan", () -> planned.isDone() && again.isDone(), () -> "working it out"))
                .thenExecute(() -> steps.guard(() -> {
                    List<KnightRuinSites.Candidate> list = planned.join();
                    checkPlan(w, list);
                    List<String> a = list.stream().map(c -> c.site().describe()).toList();
                    List<String> b = again.join().stream().map(c -> c.site().describe()).toList();
                    if (!a.equals(b))
                    {
                        throw new GameTestAssertException("planned again from nothing, the ruins differ: " + a + " / " + b);
                    }
                }))
                .thenSucceed();
    }

    private static void checkPlan(KnightRuinSites.World w, List<KnightRuinSites.Candidate> list)
    {
        KnightRuinSites.Context ctx = w.ctx;
        List<KnightRuinSites.Candidate> near = list.stream().filter(c -> c.site().nearSpawn()).toList();
        Map<KnightKind.Element, Integer> elements = new EnumMap<>(KnightKind.Element.class);
        list.forEach(c -> elements.merge(c.site().element(), 1, Integer::sum));
        LOGGER.info("[ruin test] {} ruins within {} blocks of the spawn {} {} ({} near it): {}", list.size(), PLAN_RADIUS, ctx.spawnX(),
                ctx.spawnZ(), near.size(), elements);
        list.forEach(c -> LOGGER.info("[ruin test]   {} ({} blocks from the spawn)", c.site().describe(),
                (int) ctx.fromSpawn(c.site().x(), c.site().z())));
        if (ctx.nearCount() > 0 && near.isEmpty())
        {
            throw new GameTestAssertException("no ruin near the spawn");
        }
        for (KnightRuinSites.Candidate c : near)
        {
            RuinSite s = c.site();
            double d = ctx.fromSpawn(s.x(), s.z());
            if (d < ctx.nearMin() - 1 || d > ctx.nearRadius() + 1)
            {
                throw new GameTestAssertException("the near-spawn ruin " + s.describe() + " is " + (int) d + " blocks from the spawn");
            }
            if (s.element() == KnightKind.Element.THUNDER)
            {
                throw new GameTestAssertException("a thunder ruin near the spawn: " + s.describe());
            }
        }
        for (KnightRuinSites.Candidate c : list)
        {
            checkBiome(w, c);
        }
        for (int i = 0; i < list.size(); i++)
        {
            for (int j = i + 1; j < list.size(); j++)
            {
                String breach = KnightRuinSites.breach(w, list.get(i), list.get(j));
                if (breach != null)
                {
                    throw new GameTestAssertException("two ruins break the rules (" + breach + "): " + list.get(i).site().describe() + " and "
                            + list.get(j).site().describe());
                }
            }
        }
    }

    /** A ruin's cell is in its area's biomes (a land ruin's own; a shore ruin's shore cell, a beach for a warm or cold shore). */
    private static void checkBiome(KnightRuinSites.World w, KnightRuinSites.Candidate c)
    {
        byte code = KnightRuinSites.cell(w, c.cellX(), c.cellZ());
        KnightRuinDesigns.Area area = c.site().design().area();
        KnightRuinDesigns.Area cellArea = RuinAreas.area(code);
        boolean ok;
        if (area == KnightRuinDesigns.THUNDER_WARM_BEACH || area == KnightRuinDesigns.THUNDER_COLD_BEACH)
        {
            ok = code == RuinAreas.BEACH;
        }
        else if (area == KnightRuinDesigns.THUNDER_MOUNTAINS)
        {
            ok = cellArea == KnightRuinDesigns.EARTH_PEAKS && RuinAreas.isStorm(code);
        }
        else
        {
            ok = cellArea == area;
        }
        if (!ok)
        {
            throw new GameTestAssertException("the ruin " + c.site().describe() + " isn't in its biomes (its cell's code " + code + ", "
                    + (cellArea == null ? "no area" : cellArea.id()) + ")");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // A ruin made with its land, and its knights
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void ruinIsBuiltAndGuarded(GameTestHelper helper)
    {
        Steps steps = new Steps(helper);
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        KnightRuinSites.World w = KnightRuinSites.work(overworld);
        if (w == null || !w.ctx.available())
        {
            throw new GameTestAssertException("the test world has no knight ruins (it should generate them)");
        }
        KnightRuinSites.Context ctx = w.ctx;
        // the nearest ruin well away from the spawn (no other test's player near it)
        CompletableFuture<Optional<RuinSite>> found = CompletableFuture.supplyAsync(() -> KnightRuinSites.standing(w, ctx.spawnX(),
                ctx.spawnZ(), 4000).stream().map(KnightRuinSites.Candidate::site)
                .filter(s -> !s.nearSpawn() && ctx.fromSpawn(s.x(), s.z()) > ctx.nearRadius() + 300).findFirst());
        Run run = new Run(overworld);
        List<KnightRuinClearedEvent> told = new ArrayList<>();
        Consumer<KnightRuinClearedEvent> listener = told::add;
        steps.watch();
        steps.onFail(() -> {
            run.release();
            MinecraftForge.EVENT_BUS.unregister(listener);
        });
        helper.startSequence()
                .thenWaitUntil(() -> steps.waitFor("a ruin to test", found::isDone, () -> "searching"))
                .thenExecute(() -> steps.guard(() -> {
                    run.site = found.join().orElseThrow(() -> new GameTestAssertException("no ruin within 4,000 blocks of the spawn"));
                    LOGGER.info("[ruin test] testing {}", run.site.describe());
                    run.hold();
                }))
                .thenWaitUntil(() -> steps.waitFor("its land", run::loaded, run::loadState))
                .thenExecute(() -> steps.guard(() -> {
                    checkBuilt(run);
                    MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, KnightRuinClearedEvent.class, listener);
                    run.player = join(overworld, run.site.doorstep(), "ruin-test-player");
                    run.player.setGameMode(GameType.CREATIVE);
                }))
                .thenWaitUntil(() -> steps.waitFor("its tidy-up", () -> {
                    KnightRuinKeeper.keep(overworld, run.site);
                    KnightRuinData.Entry e = KnightRuinData.get(overworld).find(run.site.id());
                    return e != null && e.tidied();
                }, () -> "felling"))
                .thenExecute(() -> steps.guard(() -> checkTemplate(run)))
                .thenWaitUntil(() -> steps.waitFor("its two knights (a player near)", () -> {
                    KnightRuinKeeper.keep(overworld, run.site);
                    return knights(overworld, run.site).size() >= 2;
                }, () -> knights(overworld, run.site).size() + " knights (" + KnightRuinKeeper.state(overworld, run.site) + ")"))
                .thenExecute(() -> steps.guard(() -> {
                    List<ElementalKnight> knights = knights(overworld, run.site);
                    if (knights.size() != 2)
                    {
                        throw new GameTestAssertException("a player near: 2 knights should guard it, but " + knights.size() + " do");
                    }
                    for (ElementalKnight k : knights)
                    {
                        if (k.kind().element != run.site.element())
                        {
                            throw new GameTestAssertException("a " + k.kind() + " guards a " + run.site.element() + " ruin");
                        }
                        BlockPos cell = run.site.guardCell(k.ruinSlot());
                        if (k.blockPosition().distManhattan(cell) > 2)
                        {
                            throw new GameTestAssertException("its knight " + k.kind() + " is at " + k.blockPosition().toShortString()
                                    + ", not on its guard spot " + cell.toShortString());
                        }
                    }
                    run.player.setGameMode(GameType.SURVIVAL);
                    knights.forEach(k -> k.kill());
                }))
                .thenIdle(25)
                .thenExecute(() -> steps.guard(() -> {
                    run.player.setGameMode(GameType.CREATIVE);
                    KnightRuinData.Entry e = KnightRuinData.get(overworld).find(run.site.id());
                    if (e == null || !e.bothBeaten())
                    {
                        throw new GameTestAssertException("both knights killed: the ruin should be beaten");
                    }
                    if (told.stream().noneMatch(t -> t.getPlayer() == run.player && t.getRuin().id().equals(run.site.id())))
                    {
                        throw new GameTestAssertException("the player near wasn't told the ruin was cleared");
                    }
                    if (!KnightRuins.hasCleared(overworld.getServer(), run.player.getUUID(), run.site))
                    {
                        throw new GameTestAssertException("the player near isn't remembered as clearing it");
                    }
                    KnightRuinKeeper.keep(overworld, run.site);
                    expectKnights(overworld, run.site, 0, "just beaten");
                    KnightRuinData.get(overworld).rewind(run.site.id(), KnightRuinKeeper.returnTicks() + 20);
                    KnightRuinKeeper.keep(overworld, run.site);
                    expectKnights(overworld, run.site, 0, "10 minutes on with a player within 64 blocks");
                    BlockPos away = run.site.ground().relative(run.site.front(), 80).above(2);
                    run.player.teleportTo(overworld, away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 0.0F, 0.0F);
                }))
                .thenWaitUntil(() -> steps.waitFor("its knights to come back (10 minutes on, the player 80 blocks off)", () -> {
                    KnightRuinKeeper.keep(overworld, run.site);
                    return knights(overworld, run.site).size() == 2;
                }, () -> knights(overworld, run.site).size() + " knights (" + KnightRuinKeeper.state(overworld, run.site) + ")"))
                .thenExecute(() -> steps.guard(() -> {
                    knights(overworld, run.site).forEach(k -> k.discard());
                    MinecraftForge.EVENT_BUS.unregister(listener);
                    leave(run.player);
                    run.release();
                }))
                .thenSucceed();
    }

    private static void checkBuilt(Run run)
    {
        ServerLevel level = run.level;
        RuinSite site = run.site;
        if (!KnightRuinKeeper.built(level, site))
        {
            throw new GameTestAssertException("there's no dross:knight_ruin structure at " + site.ground().toShortString());
        }
        BoundingBox foot = site.footprint();
        int frames = 0;
        int lit = 0;
        for (BlockPos pos : BlockPos.betweenClosed(foot.minX(), foot.minY(), foot.minZ(), foot.maxX(), foot.maxY(), foot.maxZ()))
        {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.DROSS_PORTAL_FRAME.get()))
            {
                frames++;
            }
            else if (state.is(ModBlocks.DROSS_PORTAL.get()))
            {
                lit++;
            }
        }
        if (frames < 6 || lit > 0)
        {
            throw new GameTestAssertException("its broken frame: " + frames + " frame blocks, " + lit + " portal blocks");
        }
        LOGGER.info("[ruin test] {}: built with the land, {} frame blocks", site.describe(), frames);
    }

    /** After the tidy-up (its template placed again): its blocks in place, its guard spots standing room. */
    private static void checkTemplate(Run run)
    {
        ServerLevel level = run.level;
        RuinSite site = run.site;
        Map<String, Integer> wrong = new java.util.HashMap<>();
        double match = templateMatch(level, site, wrong);
        String worst = wrong.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(6)
                .map(e -> e.getKey() + " " + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
        LOGGER.info("[ruin test] {}: {}% of its template in place after its tidy-up (the rest: {})", site.describe(),
                Math.round(match * 1000) / 10.0D, worst);
        if (match < TEMPLATE_MIN_MATCH)
        {
            throw new GameTestAssertException("only " + Math.round(match * 100) + "% of its template's blocks are in place (" + worst + ")");
        }
        for (int i = 0; i < 2; i++)
        {
            BlockPos cell = site.guardCell(i);
            if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                    || !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                    || level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty())
            {
                throw new GameTestAssertException("its guard spot " + cell.toShortString() + " has no room to stand");
            }
        }
    }

    /** The share of the template's blocks (not air) that are where it puts them. */
    private static double templateMatch(ServerLevel level, RuinSite site, Map<String, Integer> wrong)
    {
        var template = level.getStructureManager().get(site.design().template()).orElseThrow();
        var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(site.rotation())
                .setRotationPivot(site.pivot());
        List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo> blocks = new ArrayList<>();
        for (net.minecraft.world.level.block.Block b : net.minecraftforge.registries.ForgeRegistries.BLOCKS)
        {
            if (b != net.minecraft.world.level.block.Blocks.AIR)
            {
                blocks.addAll(template.filterBlocks(site.origin(), settings, b));
            }
        }
        if (blocks.isEmpty())
        {
            return 0.0D;
        }
        int same = 0;
        for (var info : blocks)
        {
            BlockState there = level.getBlockState(info.pos());
            // (Minecraft's patches of andesite, diorite and granite and its ores come after the structures, into any stone)
            boolean stone = info.state().is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                    && (there.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || there.is(net.minecraftforge.common.Tags.Blocks.ORES));
            if (there.getBlock() == info.state().getBlock() || stone)
            {
                same++;
            }
            else
            {
                wrong.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).getPath() + " -> "
                        + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(there.getBlock()).getPath()
                        + (info.pos().getY() < site.groundY() ? " (under)" : info.pos().getY() == site.groundY() ? " (ground)" : ""), 1, Integer::sum);
            }
        }
        return same / (double) blocks.size();
    }

    private static void expectKnights(ServerLevel level, RuinSite site, int count, String when)
    {
        int n = knights(level, site).size();
        if (n != count)
        {
            throw new GameTestAssertException(when + ": " + count + " knights should guard it, but " + n + " do ("
                    + KnightRuinKeeper.state(level, site) + ")");
        }
    }

    private static List<ElementalKnight> knights(ServerLevel level, RuinSite site)
    {
        return level.getEntitiesOfClass(ElementalKnight.class, new AABB(site.ground()).inflate(64.0D),
                k -> k.isAlive() && site.id().equals(k.ruin()));
    }

    private static ServerPlayer join(ServerLevel level, BlockPos at, String name)
    {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(at.getX() + 0.5D, at.getY() + 2, at.getZ() + 0.5D, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        if (player.serverLevel() != level)
        {
            player.teleportTo(level, at.getX() + 0.5D, at.getY() + 2, at.getZ() + 0.5D, 0.0F, 0.0F);
        }
        return player;
    }

    private static void leave(@Nullable ServerPlayer player)
    {
        if (player != null && player.server.getPlayerList().getPlayer(player.getUUID()) == player)
        {
            player.server.getPlayerList().remove(player);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The run's land, its waits and its watchdog
    // ---------------------------------------------------------------------------------------------

    private static final class Run
    {
        final ServerLevel level;
        RuinSite site;
        ServerPlayer player;
        ChunkPos middle;
        boolean held;
        static final int RADIUS = 4;

        Run(ServerLevel level)
        {
            this.level = level;
        }

        void hold()
        {
            this.middle = this.site.startChunk();
            this.level.getChunkSource().addRegionTicket(TICKET, this.middle, RADIUS, this.middle);
            this.held = true;
        }

        void release()
        {
            if (this.held)
            {
                this.level.getChunkSource().removeRegionTicket(TICKET, this.middle, RADIUS, this.middle);
                this.held = false;
            }
            leave(this.player);
        }

        boolean loaded()
        {
            return this.loadState() == null;
        }

        @Nullable
        String loadState()
        {
            int total = 0;
            int ready = 0;
            for (int dx = -2; dx <= 2; dx++)
            {
                for (int dz = -2; dz <= 2; dz++)
                {
                    total++;
                    int cx = this.middle.x + dx;
                    int cz = this.middle.z + dz;
                    if (this.level.getChunkSource().getChunkNow(cx, cz) != null && this.level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))
                            && this.level.isPositionEntityTicking(new BlockPos(cx * 16 + 8, this.site.groundY(), cz * 16 + 8)))
                    {
                        ready++;
                    }
                }
            }
            return ready == total ? null : ready + " of " + total + " chunks ready";
        }
    }

    /** "Not yet": a wait's check that isn't done. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }

    private static final class Steps
    {
        final GameTestHelper helper;
        @Nullable
        RuntimeException failure;
        long waitingSince = -1L;
        @Nullable
        Runnable onFail;

        Steps(GameTestHelper helper)
        {
            this.helper = helper;
        }

        void onFail(Runnable cleanup)
        {
            this.onFail = cleanup;
        }

        void watch()
        {
            this.helper.startSequence()
                    .thenWaitUntil(() -> {
                        if (this.failure == null)
                        {
                            throw new GameTestAssertException("the test ran out of time");
                        }
                    })
                    .thenFail(() -> this.failure);
        }

        void guard(Runnable step)
        {
            if (this.failure != null)
            {
                throw this.failure;
            }
            try
            {
                step.run();
            }
            catch (Waiting w)
            {
                throw w;
            }
            catch (RuntimeException e)
            {
                LOGGER.error("[ruin test] failed: {}", e.getMessage(), e);
                this.failure = e;
                if (this.onFail != null)
                {
                    this.onFail.run();
                }
                throw e;
            }
        }

        void waitFor(String what, BooleanSupplier done, Supplier<String> state)
        {
            this.guard(() -> {
                if (done.getAsBoolean())
                {
                    this.waitingSince = -1L;
                    return;
                }
                long now = Util.getMillis();
                if (this.waitingSince < 0L)
                {
                    this.waitingSince = now;
                }
                if (now - this.waitingSince > WAIT_MILLIS)
                {
                    throw new GameTestAssertException("gave up waiting for " + what + " after " + WAIT_MILLIS / 1000L + " s (" + state.get() + ")");
                }
                throw new Waiting("waiting for " + what);
            });
        }
    }
}
