package com.reg21meme.dross.boss.cinder;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.FireAnims;
import com.reg21meme.dross.boss.fire.FireDesigns;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Headless checks (GameTests) of the Cinder Wastes' Pallbearer and Cinder Hermit (CLAUDE.md step 16), each on a stone
 * floor high over the land (its chunks held by tickets; every mob made persistent, so another test's far-off stand-in
 * player can't make it despawn; everything removed at the end, also when a check fails):
 * <ul>
 *   <li>{@link #sizesHealthAndShells}: each has its design's size, its health, armour and experience, and is
 *       fire-immune; a hermit wears one of its eight shells, each about as often; the tuning's animation lengths match
 *       the animations.</li>
 *   <li>{@link #neutralUntilHurtThenFightsBack}: survival players standing beside them aren't attacked; hurt, each goes
 *       for the player who did it (the other hermit near too), the Pallbearer roars (Slowness) and both hurt their
 *       player; past its leash the Pallbearer gives up.</li>
 *   <li>{@link #hermitShellSavesAndHidingCutsDamage}: a hermit's shell survives a save and load ({@code Shell}); hidden,
 *       it takes a fifth of the damage and can't be knocked back, and it peeks and comes out again.</li>
 *   <li>{@link #eggsAndSpawnRules}: the spawn eggs make them (the hermit's in one of its shells); a Pallbearer may spawn
 *       only with open air over it and no other within 64 blocks; never a fifth hermit within 48 blocks.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class CinderMobsGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_cinder_mobs_test",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final String BATCH = "dross_cinder_mobs";
    private static final int FLOOR_Y = 200;
    private static final int FLOOR_HALF = 24;
    private static final int CLEAR_UP = 16;
    /** Each test's floor: this far out, and this far apart. */
    private static final int X0 = -9000;
    private static final int Z0 = 9000;
    private static final int APART = 200;
    /** A wait for the chunks gives up after this long, real time (the test server runs ticks far faster than 20 a
     *  second, so a tick count would be over before far-off chunks are made). */
    private static final long CHUNK_WAIT_MILLIS = 180_000L;
    private static final float HEALTH_TOLERANCE = 0.01F;
    /** The stand-in players' health (they're hit again and again). */
    private static final double STAND_IN_HEALTH = 100.0D;

    private CinderMobsGameTest() {}

    // ---------------------------------------------------------------- sizes, health and shells

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void sizesHealthAndShells(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 0);
        helper.startSequence()
                .thenWaitUntil(run::ready)
                .thenExecute(() -> run.step(() -> {
                    run.floor();
                    Pallbearer pallbearer = run.spawn(ModEntities.PALLBEARER.get(), -10.0D, 0.0D, 0.0F);
                    CinderHermit hermit = run.spawn(ModEntities.CINDER_HERMIT.get(), 10.0D, 0.0D, 0.0F);
                    FireDesigns.Design look = pallbearer.getDesign();
                    if (!look.label().equals(Pallbearer.DESIGN) || Math.abs(pallbearer.getBbHeight() - look.height()) > 0.01F
                            || Math.abs(pallbearer.getBbWidth() - look.width()) > 0.01F)
                    {
                        throw new GameTestAssertException("the Pallbearer should be M1A at " + look.width() + " x " + look.height()
                                + ", but it's " + look.label() + " at " + pallbearer.getBbWidth() + " x " + pallbearer.getBbHeight());
                    }
                    FireDesigns.Design shell = hermit.getShell();
                    if (!CinderHermit.SHELLS.contains(shell) || Math.abs(hermit.getBbHeight() - shell.height()) > 0.01F
                            || Math.abs(hermit.getBbWidth() - shell.width()) > 0.01F)
                    {
                        throw new GameTestAssertException("the hermit should wear one of its shells at that shell's size, but it's "
                                + shell.label() + " at " + hermit.getBbWidth() + " x " + hermit.getBbHeight());
                    }
                    expect(pallbearer.getMaxHealth(), (float) CinderMobTuning.PALLBEARER_HEALTH, "the Pallbearer's health");
                    expect(hermit.getMaxHealth(), (float) CinderMobTuning.HERMIT_HEALTH, "the hermit's health");
                    expect(pallbearer.getArmorValue(), (float) CinderMobTuning.PALLBEARER_ARMOR, "the Pallbearer's armour");
                    expect(hermit.getArmorValue(), (float) CinderMobTuning.HERMIT_ARMOR, "the hermit's armour");
                    expect(pallbearer.getExperienceReward(), CinderMobTuning.PALLBEARER_XP, "the Pallbearer's experience");
                    expect(hermit.getExperienceReward(), CinderMobTuning.HERMIT_XP, "the hermit's experience");
                    if (!ModEntities.PALLBEARER.get().fireImmune() || !ModEntities.CINDER_HERMIT.get().fireImmune())
                    {
                        throw new GameTestAssertException("both should be fire-immune");
                    }
                    if (CinderHermit.SHELLS.size() != 8)
                    {
                        throw new GameTestAssertException("the hermit should have 8 shells, but it has " + CinderHermit.SHELLS.size());
                    }
                    // Each shell about as often (made, never added to the world).
                    int made = 1600;
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    CinderHermit.SHELLS.forEach(d -> counts.put(d.label(), 0));
                    for (int i = 0; i < made; i++)
                    {
                        CinderHermit h = ModEntities.CINDER_HERMIT.get().create(run.level);
                        if (h == null)
                        {
                            throw new GameTestAssertException("couldn't make a hermit");
                        }
                        counts.merge(h.getShell().label(), 1, Integer::sum);
                    }
                    double each = made / 8.0D;
                    double spread = 4.0D * Math.sqrt(made * (1.0D / 8.0D) * (7.0D / 8.0D));
                    for (Map.Entry<String, Integer> count : counts.entrySet())
                    {
                        if (Math.abs(count.getValue() - each) > spread)
                        {
                            throw new GameTestAssertException("each shell should come up about " + (int) each + " times in " + made
                                    + ", but they came up " + counts);
                        }
                    }
                    // The tuning's animation lengths are the animations'.
                    checkLength("pallbearer", "swipe", CinderMobTuning.SWIPE_TICKS);
                    checkLength("pallbearer", "slam", CinderMobTuning.SLAM_TICKS);
                    checkLength("pallbearer", "roar", CinderMobTuning.ROAR_TICKS);
                    checkLength("hermit", "snap", CinderMobTuning.SNAP_TICKS);
                    checkLength("hermit", "pinch", CinderMobTuning.PINCH_TICKS);
                    checkLength("hermit", "hide", CinderMobTuning.HIDE_TICKS);
                    checkLength("hermit", "peek", CinderMobTuning.PEEK_TICKS);
                    checkLength("hermit", "come_out", CinderMobTuning.COME_OUT_TICKS);
                    LOGGER.info("[cinder mobs test] Pallbearer {} x {}, hermit in {} at {} x {}; {} hermits' shells: {}",
                            pallbearer.getBbWidth(), pallbearer.getBbHeight(), shell.label(), hermit.getBbWidth(), hermit.getBbHeight(),
                            made, counts);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    private static void checkLength(String mob, String animation, int ticks)
    {
        FireAnims.Anim anim = FireAnims.get(mob).get(animation);
        if (anim == null || Math.round(anim.seconds() * 20.0F) != ticks)
        {
            throw new GameTestAssertException(mob + "'s " + animation + " is " + (anim == null ? "missing" : anim.seconds() + " s")
                    + ", but CinderMobTuning says " + ticks + " ticks");
        }
    }

    private static void expect(float got, float wanted, String what)
    {
        if (Math.abs(got - wanted) > HEALTH_TOLERANCE)
        {
            throw new GameTestAssertException(what + " should be " + wanted + ", but it's " + got);
        }
    }

    // ---------------------------------------------------------------- neutral, until hurt

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void neutralUntilHurtThenFightsBack(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 1);
        Pallbearer[] pallbearer = new Pallbearer[1];
        CinderHermit[] hermits = new CinderHermit[2];
        ServerPlayer[] players = new ServerPlayer[2];
        helper.startSequence()
                .thenWaitUntil(run::ready)
                .thenExecute(() -> run.step(() -> {
                    run.floor();
                    // Facing south (+z), each player in front of it, in reach.
                    pallbearer[0] = run.spawn(ModEntities.PALLBEARER.get(), -12.0D, -2.0D, 0.0F);
                    hermits[0] = run.spawn(ModEntities.CINDER_HERMIT.get(), 10.0D, -8.0D, 0.0F);
                    hermits[1] = run.spawn(ModEntities.CINDER_HERMIT.get(), 10.0D, 7.0D, 0.0F);
                    players[0] = run.join("cinder_mourner", -12.0D, 2.0D);
                    players[1] = run.join("cinder_crabber", 10.0D, -3.4D);
                }))
                .thenIdle(100)
                .thenExecute(() -> run.step(() -> {
                    // Left alone, nobody is a foe.
                    for (Mob mob : List.of(pallbearer[0], hermits[0], hermits[1]))
                    {
                        if (mob.getTarget() != null)
                        {
                            throw new GameTestAssertException(mob + " went for " + mob.getTarget() + " though nobody hurt it");
                        }
                    }
                    for (ServerPlayer p : players)
                    {
                        if (p.getHealth() < p.getMaxHealth())
                        {
                            throw new GameTestAssertException(p.getName().getString() + " was hurt though they hurt nothing");
                        }
                    }
                    pallbearer[0].hurt(run.level.damageSources().playerAttack(players[0]), 2.0F);
                    hermits[0].hurt(run.level.damageSources().playerAttack(players[1]), 2.0F);
                }))
                .thenWaitUntil(() -> run.waitTicks("each to go for the player who hurt it", 80,
                        () -> pallbearer[0].getTarget() == players[0] && hermits[0].getTarget() == players[1]
                                && hermits[1].getTarget() == players[1],
                        () -> "the Pallbearer's target " + pallbearer[0].getTarget() + ", the hermits' " + hermits[0].getTarget()
                                + " and " + hermits[1].getTarget()))
                .thenWaitUntil(() -> run.waitTicks("the Pallbearer to roar (Slowness)", 120,
                        () -> players[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN), () -> "no Slowness on its player"))
                .thenWaitUntil(() -> run.waitTicks("both to hurt their players", 600,
                        () -> players[0].getHealth() < players[0].getMaxHealth() && players[1].getHealth() < players[1].getMaxHealth(),
                        () -> "the Pallbearer's player " + players[0].getHealth() + ", the hermit's " + players[1].getHealth()))
                .thenExecute(() -> run.step(() -> {
                    // Far past where its chase began, it gives up.
                    pallbearer[0].setChaseFromForTest(pallbearer[0].position().add(CinderMobTuning.CHASE_LEASH + 6.0D, 0.0D, 0.0D));
                }))
                .thenIdle(3)
                .thenExecute(() -> run.step(() -> {
                    if (pallbearer[0].getTarget() != null || pallbearer[0].isAngryAt(players[0]))
                    {
                        throw new GameTestAssertException("past its leash the Pallbearer should give up, but it goes for "
                                + pallbearer[0].getTarget());
                    }
                    LOGGER.info("[cinder mobs test] calm beside players; hurt, they fought back (players at {} and {}); the leash held",
                            players[0].getHealth(), players[1].getHealth());
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- the hermit's shell and its hiding

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void hermitShellSavesAndHidingCutsDamage(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 2);
        CinderHermit[] hidden = new CinderHermit[1];
        CinderHermit[] open = new CinderHermit[1];
        boolean[] peeked = new boolean[1];
        helper.startSequence()
                .thenWaitUntil(run::ready)
                .thenExecute(() -> run.step(() -> {
                    run.floor();
                    // Its shell saved and loaded.
                    CinderHermit saved = run.spawn(ModEntities.CINDER_HERMIT.get(), -12.0D, 0.0D, 0.0F);
                    FireDesigns.Design skull = shell("M2C");
                    saved.setShell(skull);
                    CompoundTag tag = saved.saveWithoutId(new CompoundTag());
                    saved.discard();
                    CinderHermit loaded = ModEntities.CINDER_HERMIT.get().create(run.level);
                    if (loaded == null)
                    {
                        throw new GameTestAssertException("couldn't make a hermit");
                    }
                    loaded.load(tag);
                    if (loaded.getShell() != skull || Math.abs(loaded.getBbWidth() - skull.width()) > 0.01F)
                    {
                        throw new GameTestAssertException("saved in the horned skull, it came back in " + loaded.getShell().label()
                                + " (" + loaded.getBbWidth() + " wide)");
                    }
                    tag.putString("Shell", "M2E");
                    loaded.load(tag);
                    if (loaded.getShell() != shell("M2E"))
                    {
                        throw new GameTestAssertException("{Shell:\"M2E\"} should put it in the magma conch, not " + loaded.getShell().label());
                    }
                    hidden[0] = run.spawn(ModEntities.CINDER_HERMIT.get(), 0.0D, 0.0D, 0.0F);
                    open[0] = run.spawn(ModEntities.CINDER_HERMIT.get(), 12.0D, 0.0D, 0.0F);
                    hidden[0].hideNow();
                }))
                .thenIdle(4)
                .thenExecute(() -> run.step(() -> {
                    CinderHermit h = hidden[0];
                    if (!h.isHidden())
                    {
                        throw new GameTestAssertException("it should be in its shell (" + h.shellRoutine() + ")");
                    }
                    h.invulnerableTime = 0;
                    open[0].invulnerableTime = 0;
                    float before = h.getHealth();
                    float beforeOpen = open[0].getHealth();
                    h.hurt(run.level.damageSources().magic(), 10.0F);
                    open[0].hurt(run.level.damageSources().magic(), 10.0F);
                    float lost = before - h.getHealth();
                    float lostOpen = beforeOpen - open[0].getHealth();
                    if (Math.abs(lost - 10.0F * CinderMobTuning.HIDDEN_DAMAGE_TAKEN) > 0.01F || Math.abs(lostOpen - 10.0F) > 0.01F)
                    {
                        throw new GameTestAssertException("10 damage should take " + 10.0F * CinderMobTuning.HIDDEN_DAMAGE_TAKEN
                                + " from a hidden hermit and 10 from one out of its shell, but they lost " + lost + " and " + lostOpen);
                    }
                    h.setDeltaMovement(Vec3.ZERO);
                    h.knockback(1.5D, 1.0D, 0.0D);
                    if (h.getDeltaMovement().horizontalDistanceSqr() > 1.0E-6D)
                    {
                        throw new GameTestAssertException("in its shell it was knocked back: " + h.getDeltaMovement());
                    }
                }))
                .thenWaitUntil(() -> run.waitTicks("it to peek and come out of its shell", 400,
                        () -> {
                            peeked[0] |= hidden[0].shellRoutine() == CinderHermit.Shell.PEEKING;
                            return peeked[0] && hidden[0].shellRoutine() == CinderHermit.Shell.OUT && !hidden[0].isHidden();
                        },
                        () -> "it's " + hidden[0].shellRoutine() + (peeked[0] ? " (it peeked)" : " (it never peeked)")))
                .thenExecute(() -> run.step(() -> {
                    LOGGER.info("[cinder mobs test] the shell kept through a save; hidden it took a fifth of the damage, peeked and came out");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    private static FireDesigns.Design shell(String label)
    {
        return CinderHermit.SHELLS.stream().filter(d -> d.label().equals(label)).findFirst()
                .orElseThrow(() -> new GameTestAssertException("no hermit shell " + label));
    }

    // ---------------------------------------------------------------- spawn eggs and spawn rules

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void eggsAndSpawnRules(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 3);
        helper.startSequence()
                .thenWaitUntil(run::ready)
                .thenExecute(() -> run.step(() -> {
                    run.floor();
                    ServerLevel level = run.level;
                    // The eggs.
                    if (!(ModItems.PALLBEARER_SPAWN_EGG.get() instanceof SpawnEggItem pallbearerEgg)
                            || pallbearerEgg.getType(null) != ModEntities.PALLBEARER.get())
                    {
                        throw new GameTestAssertException("the Pallbearer Spawn Egg should make a Pallbearer");
                    }
                    if (!(ModItems.CINDER_HERMIT_SPAWN_EGG.get() instanceof SpawnEggItem hermitEgg)
                            || hermitEgg.getType(null) != ModEntities.CINDER_HERMIT.get())
                    {
                        throw new GameTestAssertException("the Cinder Hermit Spawn Egg should make a Cinder Hermit");
                    }
                    Entity hatched = hermitEgg.getType(null).spawn(level, run.at(-14, 14), MobSpawnType.SPAWN_EGG);
                    if (!(hatched instanceof CinderHermit eggHermit) || !CinderHermit.SHELLS.contains(eggHermit.getShell()))
                    {
                        throw new GameTestAssertException("the hermit's egg should make a hermit in one of its shells, not " + hatched);
                    }
                    run.extras.add(eggHermit);
                    eggHermit.setPersistenceRequired();

                    // A Pallbearer: open air over it, none other near.
                    BlockPos spot = run.at(0, 0);
                    if (!CinderMobs.checkPallbearerSpawn(ModEntities.PALLBEARER.get(), level, MobSpawnType.NATURAL, spot, level.random))
                    {
                        throw new GameTestAssertException("a Pallbearer should be able to spawn on the open floor");
                    }
                    BlockPos roof = spot.above(CinderMobTuning.PALLBEARER_HEADROOM - 3);
                    level.setBlock(roof, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    boolean underRoof = CinderMobs.checkPallbearerSpawn(ModEntities.PALLBEARER.get(), level, MobSpawnType.NATURAL, spot,
                            level.random);
                    level.setBlock(roof, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (underRoof)
                    {
                        throw new GameTestAssertException("a Pallbearer spawned with a block " + (CinderMobTuning.PALLBEARER_HEADROOM - 3)
                                + " over it");
                    }
                    Pallbearer other = run.spawn(ModEntities.PALLBEARER.get(), 20.0D, 0.0D, 0.0F);
                    if (CinderMobs.checkPallbearerSpawn(ModEntities.PALLBEARER.get(), level, MobSpawnType.NATURAL, spot, level.random))
                    {
                        throw new GameTestAssertException("a Pallbearer spawned 20 blocks from another");
                    }
                    other.discard();
                    if (!CinderMobs.checkHermitSpawn(ModEntities.CINDER_HERMIT.get(), level, MobSpawnType.NATURAL, spot, level.random))
                    {
                        throw new GameTestAssertException("a hermit should be able to spawn with one other near");
                    }
                    // Never a fifth hermit within 48 blocks (the egg's one counts).
                    List<CinderHermit> crowd = new ArrayList<>();
                    for (int i = 0; i < CinderMobTuning.HERMIT_CROWD - 1; i++)
                    {
                        crowd.add(run.spawn(ModEntities.CINDER_HERMIT.get(), -14.0D + 14.0D * i, -14.0D, 0.0F));
                    }
                    if (CinderMobs.checkHermitSpawn(ModEntities.CINDER_HERMIT.get(), level, MobSpawnType.NATURAL, spot, level.random))
                    {
                        throw new GameTestAssertException("a hermit spawned with " + CinderMobTuning.HERMIT_CROWD + " others near");
                    }
                    if (!CinderMobs.checkHermitSpawn(ModEntities.CINDER_HERMIT.get(), level, MobSpawnType.SPAWN_EGG, spot, level.random))
                    {
                        throw new GameTestAssertException("the crowding rule should be for natural spawns only");
                    }
                    crowd.get(0).discard();
                    if (!CinderMobs.checkHermitSpawn(ModEntities.CINDER_HERMIT.get(), level, MobSpawnType.NATURAL, spot, level.random))
                    {
                        throw new GameTestAssertException("with " + (CinderMobTuning.HERMIT_CROWD - 1) + " hermits near, another may come");
                    }
                    // Coming into the world beside a bump inside its legs' spread, it settles where its whole body fits.
                    BlockPos bumpAt = run.at(-16, 0).offset(2, 0, 0);
                    level.setBlock(bumpAt, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    CinderHermit settler = ModEntities.CINDER_HERMIT.get().create(level);
                    if (settler == null)
                    {
                        throw new GameTestAssertException("couldn't make a hermit");
                    }
                    settler.moveTo(run.at(-16, 0).getX() + 0.5D, FLOOR_Y + 1, run.at(-16, 0).getZ() + 0.5D, 0.0F, 0.0F);
                    boolean blockedAtFirst = !level.noCollision(settler, settler.getBoundingBox());
                    boolean settled = settler.settle(level);
                    boolean clear = level.noCollision(settler, settler.getBoundingBox());
                    level.setBlock(bumpAt, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    if (!blockedAtFirst || !settled || !clear)
                    {
                        throw new GameTestAssertException("beside a bump inside its legs' spread it should settle where it fits (in the"
                                + " bump at first " + blockedAtFirst + ", settled " + settled + ", clear " + clear + ")");
                    }
                    LOGGER.info("[cinder mobs test] the eggs make them; the spawn rules hold (headroom, spacing, the hermits' crowd);"
                            + " beside a bump a hermit settled {} blocks over", String.format(java.util.Locale.ROOT, "%.1f",
                            settler.position().subtract(run.at(-16, 0).getCenter().with(net.minecraft.core.Direction.Axis.Y, FLOOR_Y + 1)).length()));
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- one test's floor

    private static final class Run
    {
        final ServerLevel level;
        final int x;
        final int z;
        final List<ChunkPos> held = new ArrayList<>();
        final List<Entity> extras = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
        /** (a waiting step) when it started: the real time, the ticks waited */
        long waitingSince = -1L;
        int waitedTicks;
        RuntimeException failure;
        boolean cleaned;

        Run(ServerLevel level, int index)
        {
            this.level = level;
            this.x = X0;
            this.z = Z0 + index * APART;
            for (int cx = (this.x - FLOOR_HALF - 16) >> 4; cx <= (this.x + FLOOR_HALF + 16) >> 4; cx++)
            {
                for (int cz = (this.z - FLOOR_HALF - 16) >> 4; cz <= (this.z + FLOOR_HALF + 16) >> 4; cz++)
                {
                    ChunkPos chunk = new ChunkPos(cx, cz);
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
                    this.held.add(chunk);
                }
            }
        }

        /** Still waiting (throws) until every held chunk ticks its entities; a real failure after a few minutes. */
        void ready()
        {
            this.check();
            for (ChunkPos chunk : this.held)
            {
                if (!this.level.isPositionEntityTicking(chunk.getMiddleBlockPosition(FLOOR_Y)))
                {
                    long now = net.minecraft.Util.getMillis();
                    if (this.waitingSince < 0L)
                    {
                        this.waitingSince = now;
                    }
                    if (now - this.waitingSince > CHUNK_WAIT_MILLIS)
                    {
                        this.giveUp("its chunks never started ticking");
                        return;
                    }
                    throw new GameTestAssertException("waiting for the chunks");
                }
            }
            this.waitingSince = -1L;
        }

        /** A wait of at most {@code ticks} ticks (the mobs' own time) for something to happen. */
        void waitTicks(String what, int ticks, BooleanSupplier done, Supplier<String> state)
        {
            this.check();
            if (done.getAsBoolean())
            {
                this.waitedTicks = 0;
                return;
            }
            if (++this.waitedTicks > ticks)
            {
                this.waitedTicks = 0;
                this.giveUp("gave up waiting for " + what + " (" + state.get() + ")");
                return;
            }
            throw new GameTestAssertException("waiting for " + what);
        }

        /** A step; a failure cleans up and fails the test. */
        void step(Runnable step)
        {
            this.check();
            try
            {
                step.run();
            }
            catch (RuntimeException e)
            {
                LOGGER.error("[cinder mobs test] failed: {}", e.getMessage());
                this.failure = e;
                this.cleanUp();
                throw e;
            }
        }

        /** A failure found by an earlier wait: the test fails now. */
        void check()
        {
            if (this.failure != null)
            {
                throw this.failure;
            }
        }

        /** A wait that went on too long: remembered and cleaned up; the wait ends, so the next step fails the test. */
        void giveUp(String message)
        {
            LOGGER.error("[cinder mobs test] failed: {}", message);
            this.failure = new GameTestAssertException(message);
            this.cleanUp();
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
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + CLEAR_UP; y++)
                    {
                        if (!this.level.getBlockState(pos.set(this.x + dx, y, this.z + dz)).isAir())
                        {
                            this.level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }

        /** The block just over the floor, this far from the middle. */
        BlockPos at(int dx, int dz)
        {
            return new BlockPos(this.x + dx, FLOOR_Y + 1, this.z + dz);
        }

        /** One of them on the floor, facing {@code yaw}, persistent (so a far-off stand-in of another test can't make it
         *  despawn). */
        <T extends CinderMob> T spawn(EntityType<T> type, double dx, double dz, float yaw)
        {
            T mob = type.create(this.level);
            if (mob == null)
            {
                throw new GameTestAssertException("couldn't make " + EntityType.getKey(type));
            }
            mob.moveTo(this.x + 0.5D + dx, FLOOR_Y + 1, this.z + 0.5D + dz, yaw, 0.0F);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            mob.setPersistenceRequired();
            this.level.addFreshEntity(mob);
            this.extras.add(mob);
            return mob;
        }

        /** A stand-in player in survival, standing there. */
        ServerPlayer join(String name, double dx, double dz)
        {
            Vec3 feet = new Vec3(this.x + 0.5D + dx, FLOOR_Y + 1, this.z + 0.5D + dz);
            ServerPlayer p = new ServerPlayer(this.level.getServer(), this.level, new GameProfile(UUID.randomUUID(), name));
            p.moveTo(feet.x, feet.y, feet.z, 180.0F, 0.0F);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            this.level.getServer().getPlayerList().placeNewPlayer(connection, p);
            p.setGameMode(GameType.SURVIVAL);
            // (plenty of health: they're hit again and again, and must live through it)
            AttributeInstance health = p.getAttribute(Attributes.MAX_HEALTH);
            if (health != null)
            {
                health.setBaseValue(STAND_IN_HEALTH);
            }
            p.setHealth((float) STAND_IN_HEALTH);
            if (p.serverLevel() != this.level || p.position().distanceToSqr(feet) > 0.01D)
            {
                p.teleportTo(this.level, feet.x, feet.y, feet.z, 180.0F, 0.0F);
            }
            this.players.add(p);
            return p;
        }

        /** Everything this test made goes, the stand-ins logged out, the chunks let go. */
        void cleanUp()
        {
            if (this.cleaned)
            {
                return;
            }
            this.cleaned = true;
            this.extras.forEach(Entity::discard);
            for (ServerPlayer p : this.players)
            {
                if (p.server.getPlayerList().getPlayer(p.getUUID()) == p)
                {
                    p.server.getPlayerList().remove(p);
                }
            }
            for (ChunkPos chunk : this.held)
            {
                this.level.getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk);
            }
        }
    }
}
