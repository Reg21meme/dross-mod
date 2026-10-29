package com.reg21meme.dross.command;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcano;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcanoData;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcanoes;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Group;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * The Emberdactyl's volcanoes in the world (the user, October 29, 2026: "the spawning of the volcano"):
 * <ul>
 *   <li>the boss-site planner gives the Cinder Wastes' first groups one volcano each, never in the Colossus's patch, in
 *       slot 1, inside its patch, the same when planned again;</li>
 *   <li>{@code /dross goto lair emberdactyl} from the Overworld builds the volcano nearest the hub and lands the stand-in on
 *       the foot of its path, facing up it;</li>
 *   <li>the built volcano on the real land: every planned lava block still lava and walled in (also a few seconds later,
 *       none flowing), its rim at its height, its path walkable from its foot to the lookout, the ground from the path's
 *       foot out over the volcano's own ground a block's step at most;</li>
 *   <li>a Cinder Wastes biome teleport keeps clear of it.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class GotoVolcanoGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The nearest volcano may have to be built first: wait at most this long (real time). */
    private static final long PLACEMENT_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** The Cinder Wastes groups checked in the plan, and how many of them must have a volcano. */
    private static final int GROUPS = 4;
    private static final int GROUPS_WITH_ONE = 3;
    /** The lava is checked again this many ticks after it's built. */
    private static final int LAVA_SETTLE_TICKS = 120;
    /** The landing check starts its biome sampling at this height (any height does). */
    private static final int SAMPLE_Y = 72;

    private GotoVolcanoGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_goto_volcano", timeoutTicks = BACKSTOP_TICKS)
    public static void gotoVolcano(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel().getServer());
        helper.startSequence()
                .thenExecute(() -> run.guard(run::checkPlan))
                .thenExecute(() -> run.guard(run::ask))
                .thenWaitUntil(() -> run.guard(run::waitForLanding))
                .thenExecute(() -> run.guard(run::checkVolcano))
                .thenIdle(LAVA_SETTLE_TICKS)
                .thenExecute(() -> run.guard(run::checkLavaSettled))
                .thenExecute(() -> run.guard(run::biomeTeleportKeepsClear))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    private static final class Run
    {
        final MinecraftServer server;
        final ServerLevel dross;
        @Nullable
        ServerPlayer player;
        @Nullable
        Site site;
        @Nullable
        EmberVolcano.Plan plan;
        long askedAt;

        Run(MinecraftServer server)
        {
            this.server = server;
            this.dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            if (this.dross == null)
            {
                throw new GameTestAssertException("there's no Dross level");
            }
        }

        /** The first real failure (rethrown by every later step, so a retried step can't hide it behind "the stand-in is gone"). */
        @Nullable
        RuntimeException failure;

        /** Runs a step; on a failure it's logged and the stand-in logged out first. */
        void guard(Runnable step)
        {
            if (failure != null)
            {
                throw failure;
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
                LOGGER.error("[goto volcano test] failed: {}", e.getMessage());
                failure = e;
                cleanUp();
                throw e;
            }
        }

        /** The planner: one volcano a group, not in the Colossus's patch, in slot 1, inside its patch, the same again. */
        void checkPlan()
        {
            BossSites sites = BossSites.forLevel(dross);
            BossSites again = BossSites.forSeed(dross, dross.getSeed());
            int with = 0;
            List<String> lines = new ArrayList<>();
            for (int g = 0; g < GROUPS; g++)
            {
                Group group = sites.group(DrossBiomes.CINDER_WASTES, g)
                        .orElseThrow(() -> new GameTestAssertException("no Cinder Wastes group"));
                Optional<Site> volcano = group.secondSite();
                if (volcano.isEmpty())
                {
                    lines.add("group " + (g + 1) + ": none");
                    continue;
                }
                with++;
                Site v = volcano.get();
                if (v.use() != BossSites.EMBER_VOLCANO)
                {
                    throw new GameTestAssertException("group " + (g + 1) + "'s second boss is " + v.use() + ", not the volcano");
                }
                if (group.bossSite().isPresent() && group.bossSite().get().patch().id() == v.patch().id())
                {
                    throw new GameTestAssertException("group " + (g + 1) + "'s volcano is in the Colossus's patch " + v.patch().number());
                }
                if (group.slotOf(v.patch()) != 1)
                {
                    throw new GameTestAssertException("group " + (g + 1) + "'s volcano patch has slot " + group.slotOf(v.patch()) + ", not 1");
                }
                if (!v.patch().contains(v.pos().getX(), v.pos().getZ()))
                {
                    throw new GameTestAssertException("group " + (g + 1) + "'s volcano at " + v.pos().toShortString() + " isn't in its patch");
                }
                Optional<Site> same = again.group(DrossBiomes.CINDER_WASTES, g).flatMap(Group::secondSite);
                if (same.isEmpty() || !same.get().pos().equals(v.pos()))
                {
                    throw new GameTestAssertException("group " + (g + 1) + "'s volcano moved when planned again (" + v.pos().toShortString()
                            + ", then " + same.map(s -> s.pos().toShortString()).orElse("none") + ")");
                }
                lines.add(String.format("group %d: patch %d at %d %d (%d blocks from the hub%s); the Colossus in patch %s", g + 1,
                        v.patch().number(), v.pos().getX(), v.pos().getZ(), Math.round(v.distanceTo(0, 0)), v.relaxed() ? ", smallest form" : "",
                        group.bossSite().map(b -> String.valueOf(b.patch().number())).orElse("none")));
            }
            LOGGER.info("[goto volcano test] the planner's volcanoes:\n  {}", String.join("\n  ", lines));
            if (with < GROUPS_WITH_ONE)
            {
                throw new GameTestAssertException("only " + with + " of the first " + GROUPS + " Cinder Wastes groups have a volcano: " + lines);
            }
        }

        void ask()
        {
            this.site = BossSites.forLevel(dross).nearest(BossSites.EMBER_VOLCANO, DrossHub.CENTER, 100_000)
                    .orElseThrow(() -> new GameTestAssertException("no volcano site planned"));
            this.player = GotoBiomeGameTest.joinPlayer(server.overworld(), "goto-volcano-test", GotoBiomeGameTest.overworldSpawn(server));
            this.askedAt = Util.getMillis();
            GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
            int result = GotoBiomeGameTest.run(chat, player, "dross goto lair emberdactyl");
            if (result != 1)
            {
                throw new GameTestAssertException("/dross goto lair emberdactyl gave " + result + " (" + chat + ")");
            }
        }

        void waitForLanding()
        {
            ServerPlayer p = requirePlayer();
            Optional<EmberVolcanoData.Volcano> built = EmberVolcanoes.get(server, site);
            if (built.isEmpty() || p.level() != dross)
            {
                if (Util.getMillis() - askedAt > PLACEMENT_TIMEOUT_MILLIS)
                {
                    throw new GameTestAssertException("the volcano at " + site.pos().toShortString() + " wasn't built and landed on within "
                            + PLACEMENT_TIMEOUT_MILLIS / 1000 + " s");
                }
                throw new Waiting("waiting for the volcano");
            }
            Vec3 foot = built.get().pathFoot();
            if (p.position().distanceTo(foot) > GotoLair.ENTRANCE_FALLBACK_RADIUS + 2)
            {
                throw new GameTestAssertException("landed at " + p.blockPosition().toShortString() + ", not at the path's foot "
                        + BlockPos.containing(foot).toShortString());
            }
            LOGGER.info("[goto volcano test] landed at {} after {} s, the path's foot {}", p.blockPosition().toShortString(),
                    String.format("%.1f", (Util.getMillis() - askedAt) / 1000.0D), BlockPos.containing(foot).toShortString());
        }

        void checkVolcano()
        {
            EmberVolcanoData.Volcano volcano = EmberVolcanoes.get(server, site).orElseThrow();
            long started = Util.getMillis();
            this.plan = EmberVolcanoes.replan(dross, site);
            if (!plan.ground().equals(volcano.ground()))
            {
                throw new GameTestAssertException("planned again, the volcano stands on " + plan.ground().toShortString() + ", built on "
                        + volcano.ground().toShortString());
            }
            FireGalleriesGameTest.checkLava(dross, plan, "as built");
            FireGalleriesGameTest.checkRim(dross, plan);
            FireGalleriesGameTest.checkPerch(dross, plan);
            FireGalleriesGameTest.checkPath(dross, plan);
            checkWayOnto(volcano);
            LOGGER.info("[goto volcano test] the volcano at {} (ground y {}, rim y {}): {} lava blocks walled in, its path walkable "
                    + "(checked in {} ms)", volcano.ground().toShortString(), volcano.ground().getY(), plan.rimY(), plan.lavaBlocks(),
                    Util.getMillis() - started);
        }

        /**
         * From the path's foot straight out (away from the volcano), over the ground it lays: a block's step at most, and
         * that ground really there (solid) in the world. (Its own planned heights: a bone pile or skull on the way is
         * walked round.)
         */
        void checkWayOnto(EmberVolcanoData.Volcano volcano)
        {
            Vec3 foot = volcano.pathFoot();
            double ox = foot.x - (volcano.ground().getX() + 0.5D);
            double oz = foot.z - (volcano.ground().getZ() + 0.5D);
            double len = Math.sqrt(ox * ox + oz * oz);
            int before = plan.groundY((int) Math.floor(foot.x), (int) Math.floor(foot.z));
            int x0 = Integer.MIN_VALUE;
            int z0 = Integer.MIN_VALUE;
            for (double step = 0.5D; step <= 30.0D; step += 0.5D)
            {
                int x = (int) Math.floor(foot.x + ox / len * step);
                int z = (int) Math.floor(foot.z + oz / len * step);
                if (x == x0 && z == z0)
                {
                    continue;
                }
                x0 = x;
                z0 = z;
                if (!plan.manages(x, z))
                {
                    break;
                }
                int ground = plan.groundY(x, z);
                if (Math.abs(ground - before) > 1)
                {
                    throw new GameTestAssertException("from the volcano's path foot outwards its ground steps " + (ground - before)
                            + " blocks at " + x + " " + z + " (from y " + before + " to " + ground + ")");
                }
                if (dross.getBlockState(new BlockPos(x, ground, z)).isAir())
                {
                    throw new GameTestAssertException("the volcano's ground at " + x + " " + ground + " " + z + " is air");
                }
                before = ground;
            }
        }

        void checkLavaSettled()
        {
            FireGalleriesGameTest.checkLava(dross, plan, "after settling");
            FireGalleriesGameTest.checkNoStrayLava(dross, plan);
        }

        /** A Cinder Wastes biome landing aimed at the volcano's middle keeps clear of it. */
        void biomeTeleportKeepsClear()
        {
            EmberVolcanoData.Volcano volcano = EmberVolcanoes.get(server, site).orElseThrow();
            BlockPos aim = volcano.ground().atY(SAMPLE_Y);
            GotoBiome.Landing landing = new GotoBiome.Landing(dross, requirePlayer(), DrossBiomes.CINDER_WASTES, SAMPLE_Y);
            Optional<Vec3> spot = landing.find(DrossHub.CENTER.atY(SAMPLE_Y), aim);
            if (spot.isEmpty())
            {
                throw new GameTestAssertException("a Cinder Wastes landing aimed at the volcano found no spot (" + landing.describe() + ")");
            }
            double d = volcano.distanceTo(spot.get().x, spot.get().z);
            if (d <= GotoBiome.VOLCANO_CLEARANCE)
            {
                throw new GameTestAssertException("a Cinder Wastes landing aimed at the volcano landed " + Math.round(d) + " blocks from its "
                        + "middle (at least " + GotoBiome.VOLCANO_CLEARANCE + " expected)");
            }
            LOGGER.info("[goto volcano test] a Cinder Wastes landing aimed at the volcano landed {} blocks from its middle", Math.round(d));
        }

        ServerPlayer requirePlayer()
        {
            if (player == null)
            {
                throw new GameTestAssertException("the stand-in player is gone");
            }
            return player;
        }

        void cleanUp()
        {
            GotoBiomeGameTest.logOut(player);
            player = null;
        }
    }

    /** "Still waiting" in a waiting step: never a failure by itself. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }
}
