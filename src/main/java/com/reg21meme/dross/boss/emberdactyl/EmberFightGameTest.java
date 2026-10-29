package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * The Emberdactyl's fight (the user's plan, October 29, 2026), on stone floors high over the Overworld (their chunks held
 * ticking by tickets; every stand-in logged out and everything removed at the end, also when a check fails):
 * <ul>
 *   <li>{@link #wakesAndFights}: one summoned the command's way, calm, wakes when a player comes near and shows its boss
 *       bar to them; a blow on its wing (a part) hurts it; at half health it turns faster (enraged).</li>
 *   <li>{@link #tornadoBurnsAndLifts}: a fire tornado hurts a cow beside it and throws it up into the sky.</li>
 *   <li>{@link #eggHatchesAndWingsClipped}: the adult beaten beside its egg: the egg cracks open and the young comes out;
 *       the young beaten: "Wings Clipped" for the player who did it.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class EmberFightGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_ember_fight_test",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int FLOOR_Y = 200;
    private static final int FLOOR_HALF = 40;
    /** A wait gives up (a real failure, cleaned up) after this long, real time (the test server runs ticks far faster
     *  than 20 a second, so a tick count would be over before far-off chunks are made). */
    private static final long WAIT_MILLIS = 180_000L;

    private EmberFightGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_wake", timeoutTicks = 2_000_000)
    public static void wakesAndFights(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6400, -6400);
        Emberdactyl[] adult = new Emberdactyl[1];
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(0.0D, 8.0D, 0.0D));
                }))
                .thenIdle(5)
                .thenExecute(() -> run.guard(() -> {
                    if (adult[0].isFighting())
                    {
                        throw new GameTestAssertException("it's fighting before anyone came near");
                    }
                    if (Math.abs(adult[0].getMaxHealth() - EmberTuning.ADULT_HEALTH) > 0.01F)
                    {
                        throw new GameTestAssertException("its health is " + adult[0].getMaxHealth());
                    }
                    run.join("ember-wake-test", run.centre.add(30.0D, 0.0D, 0.0D));
                }))
                .thenWaitUntil(() -> run.waitFor("it to wake and show its bar", () -> adult[0].isFighting() && adult[0].showsBarTo(run.player),
                        () -> "fighting " + adult[0].isFighting() + ", alive " + adult[0].isAlive()))
                .thenExecute(() -> run.guard(() -> {
                    float before = adult[0].getHealth();
                    adult[0].invulnerableTime = 0;
                    adult[0].bodyParts()[2].hurt(run.player.damageSources().playerAttack(run.player), 20.0F);
                    if (adult[0].getHealth() >= before)
                    {
                        throw new GameTestAssertException("a blow on its wing didn't hurt it (" + before + " -> " + adult[0].getHealth() + ")");
                    }
                    adult[0].setHealth(EmberTuning.ADULT_HEALTH * EmberTuning.ENRAGE_AT + 5.0F);
                    adult[0].invulnerableTime = 0;
                    adult[0].hurt(run.player.damageSources().playerAttack(run.player), 30.0F);
                    if (!adult[0].isEnraged())
                    {
                        throw new GameTestAssertException("at " + adult[0].getHealth() + " health it isn't enraged");
                    }
                    LOGGER.info("[ember test] woke, showed its bar, a wing blow hurt it, enraged at {}", adult[0].getHealth());
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_tornado", timeoutTicks = 2_000_000)
    public static void tornadoBurnsAndLifts(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6600, -6400);
        Cow[] cow = new Cow[1];
        double[] highest = {0.0D};
        float[] start = {0.0F};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    ServerLevel level = run.level;
                    cow[0] = EntityType.COW.create(level);
                    Zombie maker = EntityType.ZOMBIE.create(level);
                    cow[0].moveTo(run.centre.x + 1.0D, run.centre.y, run.centre.z, 0.0F, 0.0F);
                    maker.moveTo(run.centre.x + 30.0D, run.centre.y, run.centre.z, 0.0F, 0.0F);
                    maker.setNoAi(true);
                    maker.setPersistenceRequired();
                    level.addFreshEntity(cow[0]);
                    level.addFreshEntity(maker);
                    run.extras.add(cow[0]);
                    run.extras.add(maker);
                    highest[0] = run.centre.y;
                    start[0] = cow[0].getHealth();
                    FireTornado.spawn(level, maker, run.centre, new Vec3(1.0D, 0.0D, 0.0D), cow[0], 1.0F);
                }))
                .thenWaitUntil(() -> run.waitFor("the tornado to burn the cow and throw it up", () -> {
                    highest[0] = Math.max(highest[0], cow[0].getY());
                    return cow[0].getHealth() < start[0] && highest[0] >= run.centre.y + 3.0D;
                }, () -> "health " + cow[0].getHealth() + ", highest " + String.format("%.1f", highest[0] - run.centre.y) + " up"))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] the tornado burnt the cow ({} -> {}) and threw it {} blocks up", start[0], cow[0].getHealth(),
                            String.format("%.1f", highest[0] - run.centre.y));
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_hatch", timeoutTicks = 2_000_000)
    public static void eggHatchesAndWingsClipped(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6400, -6600);
        EmberEgg[] egg = new EmberEgg[1];
        Emberdactyl[] adult = new Emberdactyl[1];
        EmberYoung[] young = new EmberYoung[1];
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    egg[0] = EmberEgg.lay(run.level, run.centre, 0.0F, 0L);
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(10.0D, 10.0D, 0.0D));
                    adult[0].setHome(run.centre, run.centre, null, 0.0F, 0L);
                    run.join("ember-hatch-test", run.centre.add(20.0D, 0.0D, 0.0D));
                }))
                .thenIdle(5)
                .thenExecute(() -> run.guard(() -> {
                    adult[0].invulnerableTime = 0;
                    adult[0].hurt(run.player.damageSources().playerAttack(run.player), 10000.0F);
                    if (adult[0].isAlive())
                    {
                        throw new GameTestAssertException("the adult survived a blow of 10000");
                    }
                }))
                .thenWaitUntil(() -> run.waitFor("the egg to hatch and the young to come out", () -> {
                    List<EmberYoung> found = run.level.getEntitiesOfClass(EmberYoung.class, new AABB(run.centre, run.centre).inflate(60.0D));
                    if (!found.isEmpty())
                    {
                        young[0] = found.get(0);
                    }
                    return young[0] != null && egg[0].isHatched();
                }, () -> "hatched " + egg[0].isHatched() + ", young " + (young[0] != null) + ", adult dead for " + adult[0].deathTime))
                .thenExecute(() -> run.guard(() -> {
                    if (Math.abs(young[0].getMaxHealth() - EmberTuning.YOUNG_HEALTH) > 0.01F)
                    {
                        throw new GameTestAssertException("the young's health is " + young[0].getMaxHealth());
                    }
                    if (DrossAdvancements.has(run.player, DrossAdvancements.WINGS_CLIPPED))
                    {
                        throw new GameTestAssertException("\"Wings Clipped\" already, before the young was beaten");
                    }
                    young[0].invulnerableTime = 0;
                    young[0].hurt(run.player.damageSources().playerAttack(run.player), 10000.0F);
                }))
                .thenWaitUntil(() -> run.waitFor("\"Wings Clipped\"", () -> DrossAdvancements.has(run.player, DrossAdvancements.WINGS_CLIPPED),
                        () -> "the young alive " + young[0].isAlive()))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] the adult beaten, its egg hatched, the young beaten: Wings Clipped");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- the runner

    /** One test's place, stand-in and what it made; everything goes at the end or on a failure. */
    private static final class Run
    {
        final ServerLevel level;
        final int x;
        final int z;
        final Vec3 centre;
        final List<ChunkPos> held = new ArrayList<>();
        final List<net.minecraft.world.entity.Entity> extras = new ArrayList<>();
        @Nullable
        ServerPlayer player;
        @Nullable
        RuntimeException failure;
        /** When the current wait started (real time), or -1. */
        long waitingSince = -1L;
        boolean cleaned;

        Run(ServerLevel level, int x, int z)
        {
            this.level = level;
            this.x = x;
            this.z = z;
            this.centre = new Vec3(x + 0.5D, FLOOR_Y + 1, z + 0.5D);
            for (int cx = (x - FLOOR_HALF - 16) >> 4; cx <= (x + FLOOR_HALF + 16) >> 4; cx++)
            {
                for (int cz = (z - FLOOR_HALF - 16) >> 4; cz <= (z + FLOOR_HALF + 16) >> 4; cz++)
                {
                    ChunkPos chunk = new ChunkPos(cx, cz);
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
                    this.held.add(chunk);
                }
            }
        }

        /** Runs a step; a real failure is remembered, logged and cleaned up (and given again by every later step). */
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
                LOGGER.error("[ember test] failed: {}", e.getMessage());
                this.failure = e;
                this.cleanUp();
                throw e;
            }
        }

        /** A wait: still waiting (throws), or done; after {@link #WAIT_TICKS} ticks a real failure. */
        void waitFor(String what, BooleanSupplier done, Supplier<String> state)
        {
            this.guard(() -> {
                if (done.getAsBoolean())
                {
                    this.waitingSince = -1L;
                    return;
                }
                if (this.waitedTooLong())
                {
                    this.giveUp("gave up waiting for " + what + " (" + state.get() + ")");
                    return;
                }
                throw new Waiting("waiting for " + what);
            });
        }

        /** Still waiting until every held chunk is loaded with its entities ticking. */
        void ready()
        {
            for (ChunkPos chunk : this.held)
            {
                if (!this.level.isPositionEntityTicking(chunk.getMiddleBlockPosition(FLOOR_Y)))
                {
                    if (this.waitedTooLong())
                    {
                        this.giveUp("its chunks never started ticking");
                        return;
                    }
                    throw new Waiting("waiting for the chunks");
                }
            }
            this.waitingSince = -1L;
        }

        /**
         * A wait that has gone on too long: the failure is remembered and everything cleaned up, and the wait ends, so the
         * next step (a {@link #guard}ed one) fails the test at once. (A wait that threw would be retried until the test's
         * tick limit, hours away.)
         */
        void giveUp(String message)
        {
            LOGGER.error("[ember test] failed: {}", message);
            this.failure = new GameTestAssertException(message);
            this.cleanUp();
        }

        /** True once the current wait has gone on longer than {@link #WAIT_MILLIS} (starting its clock if it hasn't). */
        boolean waitedTooLong()
        {
            long now = net.minecraft.Util.getMillis();
            if (this.waitingSince < 0L)
            {
                this.waitingSince = now;
            }
            return now - this.waitingSince > WAIT_MILLIS;
        }

        /** A stone floor high over the land, cleared over it. */
        void floor()
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = -FLOOR_HALF; dx <= FLOOR_HALF; dx++)
            {
                for (int dz = -FLOOR_HALF; dz <= FLOOR_HALF; dz++)
                {
                    this.level.setBlock(pos.set(this.x + dx, FLOOR_Y, this.z + dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 60; y++)
                    {
                        if (!this.level.getBlockState(pos.set(this.x + dx, y, this.z + dz)).isAir())
                        {
                            this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }

        <T extends EmberBoss> T spawn(EntityType<T> type, Vec3 at)
        {
            T boss = type.create(this.level);
            if (boss == null)
            {
                throw new GameTestAssertException("couldn't create " + type);
            }
            boss.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            boss.setHome(this.centre, null, null, 0.0F, 0L);
            boss.finalizeSpawn(this.level, this.level.getCurrentDifficultyAt(boss.blockPosition()), MobSpawnType.COMMAND, null, null);
            this.level.addFreshEntity(boss);
            return boss;
        }

        /** A stand-in player in survival who can't be hurt (so it fights him, and he lives through it). */
        void join(String name, Vec3 feet)
        {
            ServerPlayer p = new ServerPlayer(this.level.getServer(), this.level, new GameProfile(UUID.randomUUID(), name));
            p.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            this.level.getServer().getPlayerList().placeNewPlayer(connection, p);
            p.setGameMode(GameType.SURVIVAL);
            p.getAbilities().invulnerable = true;
            if (p.serverLevel() != this.level || p.position().distanceToSqr(feet) > 0.01D)
            {
                p.teleportTo(this.level, feet.x, feet.y, feet.z, 0.0F, 0.0F);
            }
            this.player = p;
        }

        /** Everything this test made goes, the stand-in logged out, the chunks let go. */
        void cleanUp()
        {
            if (this.cleaned)
            {
                return;
            }
            this.cleaned = true;
            AABB area = new AABB(this.centre, this.centre).inflate(150.0D);
            this.level.getEntitiesOfClass(EmberBoss.class, area).forEach(e -> e.discard());
            this.level.getEntitiesOfClass(EmberEgg.class, area).forEach(e -> e.discard());
            this.level.getEntitiesOfClass(FireTornado.class, area).forEach(e -> e.discard());
            this.extras.forEach(e -> e.discard());
            if (this.player != null && this.player.server.getPlayerList().getPlayer(this.player.getUUID()) == this.player)
            {
                this.player.server.getPlayerList().remove(this.player);
            }
            for (ChunkPos chunk : this.held)
            {
                this.level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk);
            }
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
