package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModBlocks;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
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
    /** The wall beside the grabbed cow: as tall as fits under its wings at the swoop (they're about 9 blocks over what it
     *  grabs; since October 31 its whole body is solid, so beside a wall taller than that it can't swoop and gives up). */
    private static final int GRAB_WALL_HEIGHT = 6;
    private static final int FLOOR_HALF = 40;
    /** A wait gives up (a real failure, cleaned up) after this long, real time (the test server runs ticks far faster
     *  than 20 a second, so a tick count would be over before far-off chunks are made). */
    private static final long WAIT_MILLIS = 180_000L;

    private EmberFightGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_wake", timeoutTicks = 2_000_000)
    public static void wakesAndFights(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6400, -6400);
        run.watch(helper);
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
                    if (Math.abs(adult[0].getAttribute(Attributes.MAX_HEALTH).getBaseValue() - EmberTuning.ADULT_HEALTH) > 0.01D)
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
                    adult[0].part("wing_l_arm").hurt(run.player.damageSources().playerAttack(run.player), 20.0F);
                    if (adult[0].getHealth() >= before)
                    {
                        throw new GameTestAssertException("a blow on its wing didn't hurt it (" + before + " -> " + adult[0].getHealth() + ")");
                    }
                    adult[0].setHealth(adult[0].getMaxHealth() * EmberTuning.ENRAGE_AT + 5.0F);
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
        run.watch(helper);
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
        run.watch(helper);
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
                    adult[0].setHealth(1.0F); // (a hit takes at most a fifteenth of its health: boss/BossRules)
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
                    if (Math.abs(young[0].getAttribute(Attributes.MAX_HEALTH).getBaseValue() - EmberTuning.YOUNG_HEALTH) > 0.01D)
                    {
                        throw new GameTestAssertException("the young's health is " + young[0].getMaxHealth());
                    }
                    // the adult, killed 10 blocks up, came down and lies on the floor (not in the air, not in the ground)
                    if (Math.abs(adult[0].getY() - run.centre.y) > 0.1D)
                    {
                        throw new GameTestAssertException("the dying adult lies " + String.format("%.2f", adult[0].getY() - run.centre.y)
                                + " blocks off the floor");
                    }
                    if (!egg[0].getDesign().label().equals("E3E"))
                    {
                        throw new GameTestAssertException("the hatched egg looks like " + egg[0].getDesign().label() + ", not the empty E3E");
                    }
                    if (young[0].fightTarget() != run.player)
                    {
                        throw new GameTestAssertException("the young goes for " + young[0].fightTarget() + ", not the player who beat the adult");
                    }
                    if (DrossAdvancements.has(run.player, DrossAdvancements.WINGS_CLIPPED))
                    {
                        throw new GameTestAssertException("\"Wings Clipped\" already, before the young was beaten");
                    }
                    young[0].invulnerableTime = 0;
                    young[0].setHealth(1.0F);
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

    /**
     * The user's first fight: its wing buffet threw nobody (from its circle it was too high, and a hit that didn't count
     * threw nothing): from 18 blocks up it swoops down and throws a cow that can't be hurt at all; its beak snap is five
     * pecks, each one counting, and (their second fight: "when you are directly under him his pecking attack doesn't
     * really hit you") they hit a golem standing right under its chest too.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_buffet", timeoutTicks = 2_000_000)
    public static void buffetThrowsAndBeakPecksFive(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6600, -6600);
        run.watch(helper);
        Cow[] cow = new Cow[1];
        Emberdactyl[] adult = new Emberdactyl[1];
        net.minecraft.world.entity.animal.IronGolem[] golem = new net.minecraft.world.entity.animal.IronGolem[2];
        boolean[] pecking = {false};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.centre.x + 6.0D, run.centre.y, run.centre.z, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(-6.0D, 18.0D, 0.0D));
                    adult[0].testMove(EmberBoss.Move.BUFFET, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("the buffet to throw the cow", () -> horizontal(cow[0].position(), run.centre) > 14.0D,
                        () -> "the cow " + String.format("%.1f", horizontal(cow[0].position(), run.centre)) + " blocks out, its health "
                                + cow[0].getHealth()))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] the buffet threw a cow that can't be hurt {} blocks", String.format("%.1f",
                            horizontal(cow[0].position(), run.centre)));
                    adult[0].discard();
                    cow[0].discard();
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre);
                    adult[0].setYRot(-90.0F);
                    adult[0].yBodyRot = -90.0F;
                    adult[0].yHeadRot = -90.0F;
                    // (the first golem stands just past the tip of its beak: the user's third look, "I was in front
                    // of the beak and it does no damage to me")
                    golem[0] = EntityType.IRON_GOLEM.create(run.level);
                    golem[0].moveTo(run.centre.x + EmberTuning.SNAP_STRIKE + 2.5D, run.centre.y, run.centre.z, 90.0F, 0.0F);
                    golem[0].setNoAi(true);
                    run.level.addFreshEntity(golem[0]);
                    run.extras.add(golem[0]);
                    golem[1] = EntityType.IRON_GOLEM.create(run.level);
                    golem[1].moveTo(run.centre.x + 1.5D, run.centre.y, run.centre.z, 90.0F, 0.0F);
                    golem[1].setNoAi(true);
                    run.level.addFreshEntity(golem[1]);
                    run.extras.add(golem[1]);
                    adult[0].testMove(EmberBoss.Move.SNAP, golem[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("its five pecks", () -> {
                    boolean now = adult[0].animation().equals("snap");
                    pecking[0] |= now;
                    return pecking[0] && !now;
                }, () -> "the golem's health " + golem[0].getHealth()))
                .thenExecute(() -> run.guard(() -> {
                    checkPecked(golem[0], "just past the tip of its beak");
                    // (it only pecks what it's fighting: now the golem right under its chest)
                    pecking[0] = false;
                    adult[0].testMove(EmberBoss.Move.SNAP, golem[1]);
                }))
                .thenWaitUntil(() -> run.waitFor("its five pecks under its chest", () -> {
                    boolean now = adult[0].animation().equals("snap");
                    pecking[0] |= now;
                    return pecking[0] && !now;
                }, () -> "the golem's health " + golem[1].getHealth()))
                .thenExecute(() -> run.guard(() -> {
                    checkPecked(golem[1], "under its chest");
                    LOGGER.info("[ember test] five pecks: {} lost in front of it and under its chest",
                            EmberTuning.SNAP_PECKS * EmberTuning.SNAP_DAMAGE);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * The user, November 2: "the Emberdactyl was in the bedrock arena when I was fighting him, I died, went outside the
     * arena and it somehow escaped". In a bedrock arena it dives and fights a cow standing outside its wall, and never
     * leaves the arena.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_arena", timeoutTicks = 2_000_000)
    public static void staysInItsArena(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7600, -6400);
        run.watch(helper);
        Emberdactyl[] adult = new Emberdactyl[1];
        Cow[] cow = new Cow[1];
        int[] ticks = {0};
        BlockPos floor = new BlockPos(run.x, FLOOR_Y, run.z);
        int r = com.reg21meme.dross.boss.BedrockArena.RADIUS;
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    com.reg21meme.dross.boss.BedrockArena.build(run.level, floor, 1);
                    com.reg21meme.dross.boss.BedrockArenaData.get(run.level).add(new com.reg21meme.dross.boss.BedrockArenaData.Arena(floor, 1));
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dx = r + 3; dx <= r + 9; dx++)
                    {
                        for (int dz = -3; dz <= 3; dz++)
                        {
                            run.level.setBlock(pos.set(run.x + dx, FLOOR_Y, run.z + dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.x + r + 6.5D, FLOOR_Y + 1, run.z + 0.5D, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    cow[0].setNoAi(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(25.0D, 10.0D, 0.0D));
                    adult[0].testMove(EmberBoss.Move.DIVE, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("300 ticks of it going for a cow outside its arena", () -> {
                    Emberdactyl a = adult[0];
                    double half = a.getBbWidth() * 0.5D;
                    double dx = Math.abs(a.getX() - (run.x + 0.5D));
                    double dz = Math.abs(a.getZ() - (run.z + 0.5D));
                    if (dx > r + 0.5D - half + 0.01D || dz > r + 0.5D - half + 0.01D || a.getY() < FLOOR_Y + 1 - 0.01D
                            || a.getY() + a.getBbHeight() > FLOOR_Y + com.reg21meme.dross.boss.BedrockArena.ROOF_HEIGHT + 0.01D)
                    {
                        run.giveUp("it got out of its arena: " + String.format("%.1f, %.1f, %.1f", a.getX() - run.x, a.getY() - FLOOR_Y,
                                a.getZ() - run.z) + " from the floor's middle (" + a.animation() + ")");
                    }
                    return ++ticks[0] > 300 || run.failure != null;
                }, () -> "tick " + ticks[0]))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] it went for a cow outside its arena for 300 ticks and stayed inside ({} from the middle)",
                            String.format("%.1f", Math.hypot(adult[0].getX() - run.x, adult[0].getZ() - run.z)));
                    clearArena(run, floor);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /** Takes the test's bedrock arena away again (its blocks and its record). */
    private static void clearArena(Run run, BlockPos floor)
    {
        com.reg21meme.dross.boss.BedrockArenaData.get(run.level).remove(floor);
        int r = com.reg21meme.dross.boss.BedrockArena.RADIUS + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                for (int y = FLOOR_Y; y <= FLOOR_Y + com.reg21meme.dross.boss.BedrockArena.ROOF_HEIGHT; y++)
                {
                    if (!run.level.getBlockState(pos.set(floor.getX() + dx, y, floor.getZ() + dz)).isAir())
                    {
                        run.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /**
     * The user, November 2: "mobs can spawn on the surface [where it waits], I saw it happen". An Emberdactyl with a ledge
     * (an arena's: no saved volcano, and outside the Dross) keeps natural spawns off it while it's about: a natural spawn
     * by its perch is refused, one 40 blocks off isn't, a spawn egg there isn't, and once it's gone the ledge is free.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_ledge", timeoutTicks = 2_000_000)
    public static void nothingSpawnsOnItsLedge(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 8000, -6400);
        run.watch(helper);
        Emberdactyl[] adult = new Emberdactyl[1];
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre);
                    adult[0].setHome(run.centre, null, run.centre, 0.0F, 0L);
                }))
                // (it lists its ledge once a second, from its own tick; a wait that threw a real failure here would be
                // given again on every retry until the test's tick limit: waitFor waits, then gives up properly)
                .thenWaitUntil(() -> run.waitFor("it to keep its ledge", () -> EmberKeeper.onLedge(run.level,
                        BlockPos.containing(run.centre.add(5.0D, 0.0D, 3.0D))), () -> "its ledge isn't listed"))
                .thenExecute(() -> run.guard(() -> {
                    BlockPos by = BlockPos.containing(run.centre.add(5.0D, 0.0D, 3.0D));
                    BlockPos far = BlockPos.containing(run.centre.add(40.0D, 0.0D, 0.0D));
                    if (net.minecraftforge.event.ForgeEventFactory.checkSpawnPlacements(EntityType.ZOMBIE, run.level,
                            MobSpawnType.NATURAL, by, run.level.random, true))
                    {
                        throw new GameTestAssertException("a zombie may spawn naturally on its ledge");
                    }
                    if (!net.minecraftforge.event.ForgeEventFactory.checkSpawnPlacements(EntityType.ZOMBIE, run.level,
                            MobSpawnType.NATURAL, far, run.level.random, true))
                    {
                        throw new GameTestAssertException("a natural spawn 40 blocks off its ledge was refused");
                    }
                    if (!net.minecraftforge.event.ForgeEventFactory.checkSpawnPlacements(EntityType.ZOMBIE, run.level,
                            MobSpawnType.SPAWN_EGG, by, run.level.random, true))
                    {
                        throw new GameTestAssertException("a spawn egg on its ledge was refused (only natural spawns are)");
                    }
                    adult[0].discard();
                    if (EmberKeeper.onLedge(run.level, by))
                    {
                        throw new GameTestAssertException("it's gone, but its ledge still keeps spawns off");
                    }
                    LOGGER.info("[ember test] nothing spawns by itself on an Emberdactyl's ledge while it's about");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * The user, November 2: a second Emberdactyl came to its volcano while its own was off chasing a Wither. Its volcano
     * remembers its own by its id: a new one may come only if none is recorded or its own was removed without dying
     * (Peaceful), never while its own is alive anywhere, and never because one that died is gone.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_keeper", timeoutTicks = 2_000_000)
    public static void volcanoRemembersItsOwn(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7800, -6400);
        run.watch(helper);
        long id = 7_800_640_000_123L;
        Emberdactyl[] adult = new Emberdactyl[2];
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    EmberVolcanoData data = EmberVolcanoData.get(run.level.getServer());
                    if (!data.mayBring(id))
                    {
                        throw new GameTestAssertException("with none recorded, one may come");
                    }
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(0.0D, 10.0D, 0.0D));
                    adult[0].setHome(run.centre, null, null, 0.0F, id);
                    data.setBoss(id, adult[0].getUUID());
                    if (data.mayBring(id))
                    {
                        throw new GameTestAssertException("its own is alive: no second one may come");
                    }
                    // a stray one of the same volcano removed: nothing changes
                    adult[1] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(20.0D, 10.0D, 0.0D));
                    adult[1].setHome(run.centre, null, null, 0.0F, id);
                    adult[1].discard();
                    if (data.mayBring(id))
                    {
                        throw new GameTestAssertException("a stray second one removed shouldn't let a new one come");
                    }
                    // its own removed without dying (Peaceful): a new one may come
                    adult[0].discard();
                    if (!data.mayBring(id))
                    {
                        throw new GameTestAssertException("its own removed without dying: a new one may come");
                    }
                    data.setBoss(id, null);
                    LOGGER.info("[ember test] a volcano remembers its own Emberdactyl: no second one while it's alive");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * The user, November 1: "when facing an enemy that can fly, the Emberdactyl can do its peck in the air". An iron golem
     * hanging in the air ahead of it (no gravity: a flyer to it) gets its pecks in the air, each one counting, and the
     * Emberdactyl never comes down to the ground for it.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_air_peck", timeoutTicks = 2_000_000)
    public static void airPeckHitsAFlyer(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7400, -6400);
        run.watch(helper);
        Emberdactyl[] adult = new Emberdactyl[1];
        net.minecraft.world.entity.animal.IronGolem[] golem = new net.minecraft.world.entity.animal.IronGolem[1];
        boolean[] pecking = {false};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(0.0D, 10.0D, 0.0D));
                    adult[0].setYRot(-90.0F);
                    adult[0].yBodyRot = -90.0F;
                    adult[0].yHeadRot = -90.0F;
                    golem[0] = EntityType.IRON_GOLEM.create(run.level);
                    golem[0].moveTo(run.centre.x + EmberTuning.AIR_SNAP_BILL_AHEAD, run.centre.y + 10.0D
                            + EmberTuning.AIR_SNAP_BILL_UP - 1.35D, run.centre.z, 90.0F, 0.0F);
                    golem[0].setNoAi(true);
                    golem[0].setNoGravity(true);
                    run.level.addFreshEntity(golem[0]);
                    run.extras.add(golem[0]);
                    if (!EmberBoss.flies(golem[0]))
                    {
                        throw new GameTestAssertException("a golem with no gravity off the ground should count as flying");
                    }
                    adult[0].testMove(EmberBoss.Move.AIR_SNAP, golem[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("its pecks in the air", () -> {
                    boolean now = adult[0].animation().equals("air_snap");
                    pecking[0] |= now;
                    return pecking[0] && !now;
                }, () -> "the golem's health " + golem[0].getHealth() + ", the adult " + String.format("%.1f",
                        adult[0].getY() - run.centre.y) + " blocks up"))
                .thenExecute(() -> run.guard(() -> {
                    float want = EmberTuning.AIR_SNAP_PECKS * EmberTuning.SNAP_DAMAGE;
                    float lost = golem[0].getMaxHealth() - golem[0].getHealth();
                    if (Math.abs(lost - want) > 0.01F)
                    {
                        throw new GameTestAssertException("the golem in the air lost " + lost + " to its beak, not " + want
                                + " (" + EmberTuning.AIR_SNAP_PECKS + " pecks)");
                    }
                    if (adult[0].getY() - run.centre.y < 4.0D)
                    {
                        throw new GameTestAssertException("pecking in the air it came down to the ground");
                    }
                    LOGGER.info("[ember test] its pecks in the air: the flying golem lost {}", lost);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * The user's third look: "I used the rage wand on the adult then ... the younger one, nothing seems to happen between
     * the two". The adult raged Red and the young Blue, with nobody else near: both wake and each hurts the other.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_rage", timeoutTicks = 2_000_000)
    public static void ragedTheyFightEachOther(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7200, -6400);
        run.watch(helper);
        Emberdactyl[] adult = new Emberdactyl[1];
        EmberYoung[] young = new EmberYoung[1];
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(-12.0D, 14.0D, 0.0D));
                    young[0] = run.spawn(ModEntities.EMBERYOUNG.get(), run.centre.add(12.0D, 14.0D, 0.0D));
                    if (!com.reg21meme.dross.boss.rage.Rage.enrage(adult[0], com.reg21meme.dross.boss.rage.RageTeam.RED)
                            || !com.reg21meme.dross.boss.rage.Rage.enrage(young[0], com.reg21meme.dross.boss.rage.RageTeam.BLUE))
                    {
                        throw new GameTestAssertException("the Rage Wand couldn't rage them");
                    }
                }))
                .thenIdle(3)
                .thenExecute(() -> run.guard(() -> {
                    if (!adult[0].isFighting() || !young[0].isFighting())
                    {
                        throw new GameTestAssertException("raged, they should wake (the adult fighting " + adult[0].isFighting()
                                + ", the young " + young[0].isFighting() + ")");
                    }
                }))
                .thenWaitUntil(() -> run.waitFor("each to hurt the other", () -> adult[0].getHealth() < adult[0].getMaxHealth()
                                && young[0].getHealth() < young[0].getMaxHealth(),
                        () -> "the adult " + adult[0].getHealth() + " going for " + adult[0].fightTarget() + ", the young "
                                + young[0].getHealth() + " going for " + young[0].fightTarget()))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] raged on two teams the adult ({}) and the young ({}) fought each other",
                            adult[0].getHealth(), young[0].getHealth());
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /** The user's third look: "when I use their spawn egg make them insta hostile as soon as the two spawn". With nobody
     *  near, one from a spawn egg fights at once and never goes back to resting. */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_egg", timeoutTicks = 2_000_000)
    public static void spawnEggOnesFightAtOnce(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7200, -6600);
        run.watch(helper);
        EmberBoss[] boss = new EmberBoss[2];
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    boss[0] = ModEntities.EMBERDACTYL.get().spawn(run.level, BlockPos.containing(run.centre.add(-10.0D, 8.0D, 0.0D)),
                            MobSpawnType.SPAWN_EGG);
                    boss[1] = ModEntities.EMBERYOUNG.get().spawn(run.level, BlockPos.containing(run.centre.add(10.0D, 8.0D, 0.0D)),
                            MobSpawnType.SPAWN_EGG);
                    if (boss[0] == null || boss[1] == null)
                    {
                        throw new GameTestAssertException("the spawn eggs' Emberdactyls didn't come");
                    }
                    run.extras.add(boss[0]);
                    run.extras.add(boss[1]);
                    if (!boss[0].isFighting() || !boss[1].isFighting())
                    {
                        throw new GameTestAssertException("from a spawn egg they should fight at once (the adult " + boss[0].isFighting()
                                + ", the young " + boss[1].isFighting() + ")");
                    }
                }))
                .thenIdle(EmberTuning.GIVE_UP_TICKS + 100)
                .thenExecute(() -> run.guard(() -> {
                    if (!boss[0].isFighting() || !boss[1].isFighting())
                    {
                        throw new GameTestAssertException("with nobody to fight they went back to resting (the adult "
                                + boss[0].isFighting() + ", the young " + boss[1].isFighting() + ")");
                    }
                    LOGGER.info("[ember test] from spawn eggs both fight at once and stay fighting");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    private static void checkPecked(LivingEntity golem, String where)
    {
        float want = EmberTuning.SNAP_PECKS * EmberTuning.SNAP_DAMAGE;
        float lost = golem.getMaxHealth() - golem.getHealth();
        if (Math.abs(lost - want) > 0.01F)
        {
            throw new GameTestAssertException("the golem " + where + " lost " + lost + " to its beak, not " + want + " (five pecks)");
        }
    }

    /**
     * The user's first fight: its talon grab carried them through its volcano's side ("he tried to suffocate me"); their
     * second: "make it so he can't drop me from any more than where it does at max half of my health", "I want to be
     * able to move around as he carries me ... it also looks very stuttery". Next to a stone wall (as tall as fits under
     * its wings: {@link #GRAB_WALL_HEIGHT}) it grabs a cow: the
     * cow rides it (hangs from its talons), is never inside a block on the way, is carried up over the pillar and let go
     * no higher over the ground than a fall that takes half its health. Then a cow marked as dropped by it falls 30
     * blocks and loses half its health, no more.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_grab", timeoutTicks = 2_000_000)
    public static void grabNeverCarriesYouIntoTheGround(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6800, -6400);
        run.watch(helper);
        Cow[] cow = new Cow[1];
        Cow[] faller = new Cow[1];
        Emberdactyl[] adult = new Emberdactyl[1];
        boolean[] caught = {false};
        double[] highest = {0.0D};
        double[] letGo = {Double.NaN};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dx = 2; dx <= 6; dx++)
                    {
                        for (int dz = -6; dz <= 6; dz++)
                        {
                            for (int y = 1; y <= GRAB_WALL_HEIGHT; y++)
                            {
                                run.level.setBlock(pos.set(run.x + dx, FLOOR_Y + y, run.z + dz), Blocks.STONE.defaultBlockState(),
                                        Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.centre.x, run.centre.y, run.centre.z, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    highest[0] = run.centre.y;
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(-6.0D, 2.0D, 0.0D));
                    adult[0].testMove(EmberBoss.Move.GRAB, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("it to carry the cow off and let go", () -> {
                    if (adult[0].isCarrying())
                    {
                        caught[0] = true;
                        highest[0] = Math.max(highest[0], cow[0].getY());
                        if (cow[0].getVehicle() != adult[0])
                        {
                            run.giveUp("the carried cow isn't riding it (it should hang from its talons)");
                        }
                        if (!run.level.noCollision(cow[0], cow[0].getBoundingBox().deflate(0.05D)))
                        {
                            run.giveUp("the carried cow is inside a block at " + cow[0].blockPosition().toShortString());
                        }
                    }
                    else if (caught[0] && Double.isNaN(letGo[0]))
                    {
                        letGo[0] = cow[0].getY() - run.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                Mth.floor(cow[0].getX()), Mth.floor(cow[0].getZ()));
                    }
                    else if (adult[0].fightTarget() == cow[0] && adult[0].animation().equals("grab") && !caught[0]
                            && adult[0].tickCount > 200)
                    {
                        run.giveUp("it never caught the cow");
                    }
                    return caught[0] && !adult[0].isCarrying() || run.failure != null;
                }, () -> "caught " + caught[0] + ", highest " + String.format("%.1f", highest[0] - run.centre.y)))
                .thenExecute(() -> run.guard(() -> {
                    double up = highest[0] - run.centre.y;
                    if (up < EmberTuning.GRAB_DROP_MIN - 1.0D)
                    {
                        throw new GameTestAssertException("it carried the cow only " + String.format("%.1f", up) + " blocks up");
                    }
                    // (its health 10: a fall of 3 + 5 blocks takes half of it; it lets go a little higher than that)
                    double most = 3.0D + Math.floor(cow[0].getHealth() * EmberTuning.GRAB_FALL_SHARE)
                            + EmberTuning.GRAB_DROP_EXTRA + 1.5D;
                    if (letGo[0] > most)
                    {
                        throw new GameTestAssertException("it let go of the cow " + String.format("%.1f", letGo[0])
                                + " blocks over the ground (at most " + most + ")");
                    }
                    LOGGER.info("[ember test] grabbed beside a pillar, carried in open air (up to {} blocks), let go {} blocks over "
                            + "the ground", String.format("%.1f", up), String.format("%.1f", letGo[0]));
                    faller[0] = EntityType.COW.create(run.level);
                    faller[0].moveTo(run.centre.x - 8.0D, run.centre.y + 30.0D, run.centre.z, 0.0F, 0.0F);
                    run.level.addFreshEntity(faller[0]);
                    run.extras.add(faller[0]);
                    EmberBoss.markDropped(faller[0], run.level.getGameTime());
                }))
                .thenWaitUntil(() -> run.waitFor("the dropped cow to land", () -> faller[0].onGround() && faller[0].fallDistance == 0.0F,
                        () -> "at " + String.format("%.1f", faller[0].getY() - run.centre.y)))
                .thenExecute(() -> run.guard(() -> {
                    float half = faller[0].getMaxHealth() * EmberTuning.GRAB_FALL_SHARE;
                    if (!faller[0].isAlive() || faller[0].getHealth() < faller[0].getMaxHealth() - half - 0.01F)
                    {
                        throw new GameTestAssertException("a cow it dropped from 30 blocks up has " + faller[0].getHealth()
                                + " health left (a fall after its grab takes half at most)");
                    }
                    LOGGER.info("[ember test] a cow it dropped fell 30 blocks and kept {} of {} health", faller[0].getHealth(),
                            faller[0].getMaxHealth());
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * The user's second fight: "when you're far away he shouldn't tornado ... instead make him do his fire glide thing
     * then dive at you once he is closer"; and the young "whenever he flies he drops fire onto the ground". From 40
     * blocks off its glide in drops fire on the floor under it and turns into its dive once it's close; then a young
     * screeching in the air drops fire under itself.
     */
    /**
     * Its dive lands it on the ground (the user, November 5, 2026: by a dead tree it lay "perched in the middle of the
     * air", pecking from there): diving at a cow beside a dead tree, it comes down on the floor beside the tree, its feet
     * on the stone, not on the logs; and its pieces move with it (hovering, its wingtips go up and down with each beat).
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_land", timeoutTicks = 2_000_000)
    public static void diveLandsBesideATree(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7400, -6600);
        run.watch(helper);
        Cow[] cow = new Cow[1];
        Emberdactyl[] adult = new Emberdactyl[1];
        double[] tipY = {Double.MAX_VALUE, -Double.MAX_VALUE};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    // a dead tree: a trunk 7 high with two branches, right beside where the cow stands
                    BlockPos foot = BlockPos.containing(run.centre);
                    for (int y = 0; y < 7; y++)
                    {
                        run.level.setBlock(foot.above(y), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                    run.level.setBlock(foot.above(5).east(), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
                    run.level.setBlock(foot.above(6).west(), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.centre.x + 1.5D, run.centre.y, run.centre.z, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    cow[0].setNoAi(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(-24.0D, 16.0D, 0.0D));
                }))
                .thenWaitUntil(() -> run.waitFor("its wingtips to beat", () -> {
                    var tip = adult[0].part("wing_l_tip_1");
                    if (tip != null && tip.shown())
                    {
                        tipY[0] = Math.min(tipY[0], tip.getBoundingBox().minY);
                        tipY[1] = Math.max(tipY[1], tip.getBoundingBox().minY);
                    }
                    return tipY[1] - tipY[0] > 1.5D;
                }, () -> "its wingtip went between " + tipY[0] + " and " + tipY[1] + " (" + adult[0].animation() + ")"))
                .thenExecute(() -> run.guard(() -> adult[0].testMove(EmberBoss.Move.DIVE, cow[0])))
                .thenWaitUntil(() -> run.waitFor("it to dive and lie stuck on the ground", () -> adult[0].move == EmberBoss.Move.STUCK
                        && adult[0].moveTick > 15, () -> adult[0].move + " at " + adult[0].position()))
                .thenExecute(() -> run.guard(() -> {
                    Emberdactyl a = adult[0];
                    BlockPos under = BlockPos.containing(a.getX(), a.getY() - 0.5D, a.getZ());
                    boolean held = !run.level.noCollision(a, a.getBoundingBox().move(0.0D, -0.2D, 0.0D));
                    if (a.getY() > run.centre.y + 0.6D || !held || run.level.getBlockState(under).is(net.minecraft.tags.BlockTags.LOGS))
                    {
                        throw new GameTestAssertException("after its dive it should lie on the floor (y " + run.centre.y + "), its feet on "
                                + "the stone, but it's at " + a.position() + " on " + run.level.getBlockState(under) + (held ? "" : ", in the air"));
                    }
                    LOGGER.info("[ember test] its wingtips beat {} blocks up and down; diving at a cow by a dead tree it came down on the "
                            + "floor at {} ({} from the cow)", String.format("%.1f", tipY[1] - tipY[0]), a.position(),
                            String.format("%.1f", horizontal(a.position(), cow[0].position())));
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * On a hillside (the user, November 5, 2026, after a fight on its volcano: "he stands up on one arm instead of his
     * legs being his main [support]", and "the Emberdactyl should go up a little higher before he dives down"): the adult
     * climbs about {@link EmberTuning#DIVE_CLIMB} blocks before its dive; coming in side-on to a slope that rises a block
     * every two, it lands facing up or down it, its feet and both wrists on the ground, and while it lies there it never
     * turns to stand worse.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_slope", timeoutTicks = 2_000_000)
    public static void diveLandsWellOnAHillside(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7000, -6600);
        run.watch(helper);
        Cow[] cow = new Cow[1];
        Emberdactyl[] adult = new Emberdactyl[1];
        double[] climb = {Double.NaN, -Double.MAX_VALUE};       // where it started, the highest before its dive
        double[] worst = {0.0D};
        int[] stuckTicks = {0};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    // a hillside rising along x, a block every two, 20 high
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dx = -FLOOR_HALF; dx <= FLOOR_HALF; dx++)
                    {
                        int h = Mth.clamp(Math.floorDiv(dx + 20, 2), 0, 20);
                        for (int dz = -FLOOR_HALF; dz <= FLOOR_HALF; dz++)
                        {
                            for (int y = 1; y <= h; y++)
                            {
                                run.level.setBlock(pos.set(run.x + dx, FLOOR_Y + y, run.z + dz), Blocks.STONE.defaultBlockState(),
                                        Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.centre.x, FLOOR_Y + 11, run.centre.z, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    cow[0].setNoAi(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    // coming in along z: side-on to the slope
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), new Vec3(run.centre.x, FLOOR_Y + 30, run.centre.z - 26.0D));
                }))
                .thenWaitUntil(() -> run.waitFor("it to settle in the air", () -> adult[0].tickCount > 20, () -> "tick " + adult[0].tickCount))
                .thenExecute(() -> run.guard(() -> {
                    climb[0] = adult[0].getY();
                    adult[0].testMove(EmberBoss.Move.DIVE, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("it to dive and lie stuck on the ground", () -> {
                    Emberdactyl a = adult[0];
                    if (a.move == EmberBoss.Move.DIVE && !a.animation().equals("dive"))
                    {
                        climb[1] = Math.max(climb[1], a.getY());
                    }
                    if (a.move == EmberBoss.Move.STUCK)
                    {
                        worst[0] = Math.max(worst[0], a.groundMiss(a.getX(), a.getY(), a.getZ(), a.yBodyRot));
                        stuckTicks[0]++;
                    }
                    return a.move == EmberBoss.Move.STUCK && a.moveTick > 40;
                }, () -> adult[0].move + " at " + adult[0].position()))
                .thenExecute(() -> run.guard(() -> {
                    Emberdactyl a = adult[0];
                    if (climb[1] < climb[0] + EmberTuning.DIVE_CLIMB - 2.0D)
                    {
                        throw new GameTestAssertException("before its dive the adult should climb about " + EmberTuning.DIVE_CLIMB
                                + " blocks, but it went from y " + String.format("%.1f", climb[0]) + " to " + String.format("%.1f", climb[1]));
                    }
                    boolean held = !run.level.noCollision(a, a.getBoundingBox().move(0.0D, -0.2D, 0.0D));
                    if (worst[0] > EmberTuning.GROUND_FIT + 1.0E-6D || !held)
                    {
                        throw new GameTestAssertException("on the hillside its feet and wrists should meet the ground (within "
                                + EmberTuning.GROUND_FIT + "), but they were up to " + String.format("%.2f", worst[0]) + " off it, facing "
                                + String.format("%.0f", Mth.wrapDegrees(a.yBodyRot)) + " at " + a.position() + (held ? "" : ", in the air"));
                    }
                    LOGGER.info("[ember test] it climbed {} blocks before its dive; on the hillside it landed facing {} (way in 0), its "
                            + "feet and wrists at most {} off the ground over {} ticks", String.format("%.1f", climb[1] - climb[0]),
                            String.format("%.0f", Mth.wrapDegrees(a.yBodyRot)), String.format("%.2f", worst[0]), stuckTicks[0]);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * Over a volcano (the user, November 6, 2026: "the young Emberdactyl is now stuck going into a wall forever ... it's
     * doing its glide thing and moving into the volcano and not stopping"): gliding at a cow on the far side of a cone of
     * stone as steep as a volcano's flank and 49 high, the young gets over or round it to the cow, never held against the
     * rock for 3 s.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_cone", timeoutTicks = 2_000_000)
    public static void youngGetsOverAVolcano(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6800, -6600);
        run.watch(helper);
        Cow[] cow = new Cow[1];
        EmberBoss[] young = new EmberBoss[1];
        int[] still = {0, 0};          // ticks in a row it hardly moved while it meant to fly on, and the most of those
        Vec3[] last = {null};
        int[] ticks = {0};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dx = -CONE_FOOT; dx <= CONE_FOOT; dx++)
                    {
                        for (int dz = -CONE_FOOT; dz <= CONE_FOOT; dz++)
                        {
                            int h = (int) Math.round((CONE_FOOT - Math.hypot(dx, dz)) * CONE_SLOPE);
                            for (int y = 1; y <= Math.min(h, CONE_HEIGHT); y++)
                            {
                                run.level.setBlock(pos.set(run.x + dx, FLOOR_Y + y, run.z + dz), Blocks.STONE.defaultBlockState(),
                                        Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.centre.x + CONE_FOOT + 6.0D, FLOOR_Y + 1, run.centre.z, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    cow[0].setNoAi(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    young[0] = run.spawn(ModEntities.EMBERYOUNG.get(), new Vec3(run.centre.x - CONE_FOOT - 8.0D, FLOOR_Y + 14, run.centre.z));
                    young[0].testMove(EmberBoss.Move.GLIDE, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("the young to get over the cone to the cow", () -> {
                    EmberBoss y = young[0];
                    ticks[0]++;
                    boolean flying = y.move == EmberBoss.Move.GLIDE || y.move == EmberBoss.Move.NONE;
                    if (flying && last[0] != null && y.position().distanceTo(last[0]) < 0.05D)
                    {
                        still[0]++;
                        still[1] = Math.max(still[1], still[0]);
                    }
                    else
                    {
                        still[0] = 0;
                    }
                    last[0] = y.position();
                    if (still[0] > 60)
                    {
                        run.giveUp("it was held against the cone for 3 s at " + String.format("%.1f, %.1f, %.1f", y.getX() - run.x,
                                y.getY() - FLOOR_Y, y.getZ() - run.z) + " from its foot's middle (" + y.animation() + ", " + y.move + ")");
                        return true;
                    }
                    return y.getX() > run.centre.x + 20.0D;
                }, () -> young[0].move + " at " + young[0].position()))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] the young got over a cone 49 high to the cow in {} ticks (held still at most {} ticks)",
                            ticks[0], still[1]);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * Out of a crater (the young hatches in its volcano's crater, under its jagged rim): from the bottom of a stone pit
     * narrower than its wings, under a lip of rock reaching in over it, the young gets out to the cow outside within 20 s
     * (its wings under the lip, against the walls and unable to turn, it had nowhere to go and pressed into the rock for
     * ever).
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_crater", timeoutTicks = 2_000_000)
    public static void youngClimbsOutOfACrater(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6800, -6800);
        run.watch(helper);
        Cow[] cow = new Cow[1];
        EmberBoss[] young = new EmberBoss[1];
        int[] ticks = {0};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dx = -PIT_WALL; dx <= PIT_WALL; dx++)
                    {
                        for (int dz = -PIT_WALL; dz <= PIT_WALL; dz++)
                        {
                            double r = Math.hypot(dx, dz);
                            for (int y = 1; y <= PIT_DEPTH; y++)
                            {
                                boolean wall = r >= PIT_RADIUS && r <= PIT_WALL;
                                boolean lip = r >= PIT_LIP && r <= PIT_WALL && y > PIT_DEPTH - 3;
                                if (wall || lip)
                                {
                                    run.level.setBlock(pos.set(run.x + dx, FLOOR_Y + y, run.z + dz), Blocks.STONE.defaultBlockState(),
                                            Block.UPDATE_CLIENTS);
                                }
                            }
                        }
                    }
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.centre.x + PIT_WALL + 14.0D, FLOOR_Y + 1, run.centre.z, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    cow[0].setNoAi(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    young[0] = run.spawn(ModEntities.EMBERYOUNG.get(), new Vec3(run.centre.x, FLOOR_Y + 2, run.centre.z));
                    young[0].testMove(EmberBoss.Move.GLIDE, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("the young to get out of the pit", () -> {
                    EmberBoss y = young[0];
                    if (++ticks[0] > 400)
                    {
                        run.giveUp("after 20 s the young is still in the pit, at " + String.format("%.1f, %.1f, %.1f", y.getX() - run.x,
                                y.getY() - FLOOR_Y, y.getZ() - run.z) + " from its floor's middle (" + y.animation() + ", " + y.move + ", freeing "
                                + y.freeTicks + ")");
                        return true;
                    }
                    if (ticks[0] % 40 == 0)
                    {
                        LOGGER.info("[ember test] pit: tick {} at {}, {}, {} ({}, freeing {})", ticks[0], String.format("%.1f", y.getX() - run.x),
                                String.format("%.1f", y.getY() - FLOOR_Y), String.format("%.1f", y.getZ() - run.z), y.move, y.freeTicks);
                    }
                    return y.getY() > FLOOR_Y + PIT_DEPTH + 1 || Math.hypot(y.getX() - run.centre.x, y.getZ() - run.centre.z) > PIT_WALL + 2;
                }, () -> young[0].move + " at " + young[0].position()))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] the young got out of a pit {} round under a lip in {} ticks", PIT_RADIUS, ticks[0]);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /** The pit in {@link #youngClimbsOutOfACrater}: open this far round, its wall out to this far, its lip in to this far
     *  over the top 3 blocks, this deep. */
    private static final int PIT_RADIUS = 9;
    private static final int PIT_WALL = 13;
    private static final int PIT_LIP = 5;
    private static final int PIT_DEPTH = 24;

    /** The cone in {@link #youngGetsOverAVolcano}: this far round at its foot, rising this much a block in, at most this high. */
    private static final int CONE_FOOT = 28;
    private static final double CONE_SLOPE = 1.75D;
    private static final int CONE_HEIGHT = 49;

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ember_glide", timeoutTicks = 2_000_000)
    public static void farGlideEndsInADiveAndTheYoungDropsFire(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 7000, -6200);
        run.watch(helper);
        Cow[] cow = new Cow[1];
        EmberBoss[] boss = new EmberBoss[1];
        double[] diveFrom = {Double.NaN};
        int[] fires = {0};
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    cow[0] = EntityType.COW.create(run.level);
                    cow[0].moveTo(run.centre.x + 6.0D, run.centre.y, run.centre.z, 0.0F, 0.0F);
                    cow[0].setInvulnerable(true);
                    cow[0].setNoAi(true);
                    run.level.addFreshEntity(cow[0]);
                    run.extras.add(cow[0]);
                    boss[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(-34.0D, 14.0D, 0.0D));
                    boss[0].testMove(EmberBoss.Move.GLIDE, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("its glide to turn into a dive", () -> {
                    if (boss[0].animation().equals("dive"))
                    {
                        diveFrom[0] = horizontal(boss[0].position(), cow[0].position());
                        return true;
                    }
                    return false;
                }, () -> boss[0].animation() + ", " + String.format("%.1f", horizontal(boss[0].position(), cow[0].position()))
                        + " blocks off"))
                .thenExecute(() -> run.guard(() -> {
                    fires[0] = run.fires(-40, 8, -6, 6);
                    if (fires[0] == 0)
                    {
                        throw new GameTestAssertException("its glide in dropped no fire on the floor under it");
                    }
                    if (diveFrom[0] > EmberTuning.GLIDE_DIVE_AT + 3.0D)
                    {
                        throw new GameTestAssertException("it dived from " + String.format("%.1f", diveFrom[0]) + " blocks off (at most "
                                + EmberTuning.GLIDE_DIVE_AT + ")");
                    }
                    LOGGER.info("[ember test] glided in dropping fire ({} patches) and dived from {} blocks off", fires[0],
                            String.format("%.1f", diveFrom[0]));
                    boss[0].discard();
                    boss[0] = run.spawn(ModEntities.EMBERYOUNG.get(), run.centre.add(0.0D, 10.0D, 24.0D));
                    boss[0].testMove(EmberBoss.Move.SCREECH, cow[0]);
                }))
                .thenWaitUntil(() -> run.waitFor("the young to drop fire under itself", () -> run.fires(-4, 4, 20, 28) > 0,
                        () -> boss[0].animation()))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember test] the young dropped fire under itself in the air");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    private static double horizontal(Vec3 a, Vec3 b)
    {
        return Math.hypot(a.x - b.x, a.z - b.z);
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

        /**
         * Fails the test as soon as a step has failed. A wait takes any failed check for "not yet" and tries it again
         * every tick, until the test's tick limit hours later; so a failure found in a wait is kept ({@link #failure})
         * and this second sequence (started before the test's own) fails the test with it at once.
         */
        void watch(GameTestHelper helper)
        {
            helper.startSequence()
                    .thenWaitUntil(() -> {
                        if (this.failure == null)
                        {
                            throw new GameTestAssertException("the test ran out of time");
                        }
                    })
                    .thenFail(() -> this.failure);
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

        /** Patches of its temporary fire on the floor, from x0 to x1 and z0 to z1 round the middle. */
        int fires(int x0, int x1, int z0, int z1)
        {
            int count = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = x0; dx <= x1; dx++)
            {
                for (int dz = z0; dz <= z1; dz++)
                {
                    if (this.level.getBlockState(pos.set(this.x + dx, FLOOR_Y + 1, this.z + dz)).is(ModBlocks.CINDER_FIRE.get()))
                    {
                        count++;
                    }
                }
            }
            return count;
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
