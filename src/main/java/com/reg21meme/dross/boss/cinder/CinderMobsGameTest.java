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
import net.minecraft.world.entity.LivingEntity;
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
import java.util.Locale;
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
 *       only with open air over it and no other within 96 blocks; never a fourth hermit within 48 blocks.</li>
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
    /** The den test's wall: this far east of the den, this far either side of it north and south, this tall; the crab is
     *  put this far east of the den, behind it. */
    private static final int DEN_WALL_X = 5;
    private static final int DEN_WALL_HALF = 3;
    private static final int DEN_WALL_HEIGHT = 7;
    private static final double DEN_WALL_BEHIND = 12.0D;
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
        run.watch(helper);
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
                            || Math.abs(hermit.getBbWidth() - CinderHermit.standWidth(shell)) > 0.01F)
                    {
                        throw new GameTestAssertException("the hermit should wear one of its shells, its box that shell's standing width, but it's "
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
        run.watch(helper);
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
                    // 10 blocks from the first at their middles: well inside its call (12 edge to edge) whatever their shells
                    hermits[1] = run.spawn(ModEntities.CINDER_HERMIT.get(), 10.0D, 2.0D, 0.0F);
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
        run.watch(helper);
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
                    if (loaded.getShell() != skull || Math.abs(loaded.getBbWidth() - CinderHermit.standWidth(skull)) > 0.01F)
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

    /**
     * Its hitboxes are its pieces (the user, November 5, 2026: "a box around each leg, then their giant claw, their
     * shell"): nine of them, all shown while it's out, a blow on a leg hurts it, its own box can't be hit; in its shell
     * its legs, claws and eyes are tucked inside the shell's box.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void hermitPiecesFollowIt(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 6);
        run.watch(helper);
        CinderHermit[] crab = new CinderHermit[1];
        helper.startSequence()
                .thenWaitUntil(run::ready)
                .thenExecute(() -> run.step(() -> {
                    run.floor();
                    crab[0] = run.spawn(ModEntities.CINDER_HERMIT.get(), 0.0D, 0.0D, 0.0F);
                    crab[0].setShell(shell("M2C"));
                }))
                .thenIdle(5)
                .thenExecute(() -> run.step(() -> {
                    CinderHermit h = crab[0];
                    var parts = h.bodyParts();
                    long shown = java.util.Arrays.stream(parts).filter(com.reg21meme.dross.boss.fire.ShapedPart::shown).count();
                    if (parts.length != CinderHermit.PIECES.size() || shown != parts.length)
                    {
                        throw new GameTestAssertException("out of its shell it should have its " + CinderHermit.PIECES.size()
                                + " pieces showing, but has " + parts.length + ", " + shown + " showing");
                    }
                    if (h.isPickable())
                    {
                        throw new GameTestAssertException("its own box shouldn't be hittable (only its pieces)");
                    }
                    var leg = parts[CinderHermit.PIECES.indexOf("leg_bl")];
                    var shellBox = parts[CinderHermit.PIECES.indexOf("shell")].getBoundingBox();
                    if (leg.getBoundingBox().getXsize() > 4.0D || shellBox.getXsize() < 2.0D)
                    {
                        throw new GameTestAssertException("its pieces are the wrong size: a leg " + leg.getBoundingBox() + ", the shell " + shellBox);
                    }
                    h.invulnerableTime = 0;
                    float before = h.getHealth();
                    leg.hurt(run.level.damageSources().magic(), 4.0F);
                    if (h.getHealth() >= before)
                    {
                        throw new GameTestAssertException("a blow on its leg didn't hurt it");
                    }
                    h.hideNow();
                }))
                .thenIdle(40)
                .thenExecute(() -> run.step(() -> {
                    CinderHermit h = crab[0];
                    var parts = h.bodyParts();
                    var shellBox = parts[CinderHermit.PIECES.indexOf("shell")].getBoundingBox().inflate(0.75D);
                    StringBuilder out = new StringBuilder();
                    for (var part : parts)
                    {
                        if (part.name().equals("shell") || part.name().equals("body") || !part.shown())
                        {
                            continue;
                        }
                        var b = part.getBoundingBox();
                        if (b.minX < shellBox.minX || b.maxX > shellBox.maxX || b.minZ < shellBox.minZ || b.maxZ > shellBox.maxZ
                                || b.maxY > shellBox.maxY)
                        {
                            out.append(" ").append(part.name());
                        }
                    }
                    if (!h.isHidden() || !out.isEmpty())
                    {
                        throw new GameTestAssertException("hidden (" + h.isHidden() + "), these pieces stick out of its shell's box:" + out);
                    }
                    LOGGER.info("[cinder mobs test] the hermit's {} pieces show and can be hit; in its shell they're tucked inside it",
                            parts.length);
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
        run.watch(helper);
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
                    // Never a fourth hermit within 48 blocks (the egg's one counts).
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

    /**
     * The user, November 2: the hermit hides every time it loses a quarter of its health, a hit that kills it still kills
     * it, and magma, fire and lava never hurt the Cinder Wastes' fire mobs (they did the Pallbearer and the hermit).
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void hidesEveryQuarterAndFireNeverHurts(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 4);
        run.watch(helper);
        CinderHermit[] hermit = new CinderHermit[1];
        helper.startSequence()
                .thenWaitUntil(run::ready)
                .thenExecute(() -> run.step(() -> {
                    run.floor();
                    // Fire, lava and magma: nothing off any of the four.
                    List<LivingEntity> fireMobs = new ArrayList<>();
                    fireMobs.add(run.spawn(ModEntities.PALLBEARER.get(), -14.0D, 10.0D, 0.0F));
                    fireMobs.add(run.spawn(ModEntities.CINDER_HERMIT.get(), 0.0D, 10.0D, 0.0F));
                    for (EntityType<? extends LivingEntity> golem : List.of(ModEntities.STONE_GOLEM.get(), ModEntities.LAVA_GOLEM.get()))
                    {
                        LivingEntity g = golem.create(run.level);
                        if (g == null)
                        {
                            throw new GameTestAssertException("couldn't make a golem");
                        }
                        g.moveTo(run.at(10 + 4 * fireMobs.size(), 10).getCenter().with(net.minecraft.core.Direction.Axis.Y, FLOOR_Y + 1));
                        run.level.addFreshEntity(g);
                        run.extras.add(g);
                        fireMobs.add(g);
                    }
                    for (LivingEntity mob : fireMobs)
                    {
                        for (net.minecraft.world.damagesource.DamageSource fire : List.of(run.level.damageSources().hotFloor(),
                                run.level.damageSources().inFire(), run.level.damageSources().onFire(), run.level.damageSources().lava()))
                        {
                            mob.invulnerableTime = 0;
                            float before = mob.getHealth();
                            mob.hurt(fire, 4.0F);
                            if (mob.getHealth() < before)
                            {
                                throw new GameTestAssertException(mob.getType().getDescriptionId() + " took " + (before - mob.getHealth())
                                        + " from " + fire.getMsgId() + " (fire mobs are immune)");
                            }
                        }
                    }
                    // A hit that would kill it kills it, out of its shell or in it.
                    CinderHermit oneShot = run.spawn(ModEntities.CINDER_HERMIT.get(), 14.0D, -10.0D, 0.0F);
                    oneShot.hurt(run.level.damageSources().magic(), 1000.0F);
                    if (oneShot.isAlive() && oneShot.getHealth() > 0.0F)
                    {
                        throw new GameTestAssertException("a hit of 1000 should kill a hermit, it has " + oneShot.getHealth() + " left");
                    }
                    // 5 off (still over three quarters): it stays out; 3 more (under): it hides.
                    hermit[0] = run.spawn(ModEntities.CINDER_HERMIT.get(), -14.0D, -10.0D, 0.0F);
                    hermit[0].hurt(run.level.damageSources().magic(), hermit[0].getMaxHealth() * 0.2F);
                }))
                .thenIdle(10)
                .thenExecute(() -> run.step(() -> {
                    if (hermit[0].isHidden())
                    {
                        throw new GameTestAssertException("a fifth of its health off, it shouldn't hide yet");
                    }
                    hermit[0].invulnerableTime = 0;
                    hermit[0].hurt(run.level.damageSources().magic(), hermit[0].getMaxHealth() * 0.1F);
                }))
                .thenWaitUntil(() -> run.waitTicks("it to hide past three quarters of its health", 40, () -> hermit[0].isHidden(),
                        () -> hermit[0].shellRoutine() + ", " + hermit[0].getHealth() + " of " + hermit[0].getMaxHealth()))
                .thenExecute(() -> run.step(() -> {
                    LOGGER.info("[cinder mobs test] fire, lava and magma hurt none of the four; a big hit kills a hermit; it hides"
                            + " past three quarters of its health");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- a hermit crab cave's own crab: its den

    /**
     * A cave's own crab (its den set, as HermitCaves.spawnCrabs does; in the widest shell, about 8 blocks) never
     * wanders off its den on its own; moved 8 blocks away, it walks back; put behind a wall, it goes round it (the
     * pathfinder's route for its whole width, CinderMob's WideNavigation); it still fights back against a player who
     * hurts it from 5 blocks off (a vanilla restriction would have stopped that); its den is kept through a save. A
     * hermit with no den roams as before.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void caveCrabKeepsToItsDen(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel(), 5);
        run.watch(helper);
        CinderHermit[] crab = new CinderHermit[1];
        BlockPos[] den = new BlockPos[1];
        ServerPlayer[] player = new ServerPlayer[1];
        double[] furthest = {0.0D};
        int[] watched = {0};
        int[] walked = {0, 0};
        helper.startSequence()
                .thenWaitUntil(run::ready)
                .thenExecute(() -> run.step(() -> {
                    run.floor();
                    crab[0] = run.spawn(ModEntities.CINDER_HERMIT.get(), 0.0D, 0.0D, 0.0F);
                    // the widest (about 8 blocks; a cave's own crab never wears it, but any hermit may): the pathfinder's
                    // hardest case
                    crab[0].setShell(shell("M2C"));
                    den[0] = run.at(0, 0);
                    crab[0].setDen(den[0]);
                    CompoundTag saved = crab[0].saveWithoutId(new CompoundTag());
                    CinderHermit copy = ModEntities.CINDER_HERMIT.get().create(run.level);
                    if (copy == null)
                    {
                        throw new GameTestAssertException("couldn't make a hermit");
                    }
                    copy.load(saved);
                    if (!den[0].equals(copy.den()))
                    {
                        throw new GameTestAssertException("its den should be kept through a save: " + copy.den());
                    }
                }))
                // calm for a good while: never more than its den's reach (and a little) from it
                .thenWaitUntil(() -> run.waitTicks("600 ticks of it left alone", 700, () -> {
                    furthest[0] = Math.max(furthest[0], Math.hypot(crab[0].getX() - (den[0].getX() + 0.5D),
                            crab[0].getZ() - (den[0].getZ() + 0.5D)));
                    return ++watched[0] >= 600;
                }, () -> "watched " + watched[0]))
                .thenExecute(() -> run.step(() -> {
                    if (furthest[0] > CinderMobTuning.CAVE_CRAB_DEN + 0.5D)
                    {
                        throw new GameTestAssertException("left alone, a cave's crab wandered " + String.format(Locale.ROOT,
                                "%.1f", furthest[0]) + " blocks from its den");
                    }
                    crab[0].teleportTo(den[0].getX() + 8.5D, den[0].getY(), den[0].getZ() + 0.5D);
                }))
                .thenWaitUntil(() -> run.waitTicks("it to walk back to its den", 600, () -> home(crab[0], den[0], walked, 0),
                        () -> "at " + crab[0].position() + ": " + crab[0].denState()))
                // A wall between it and its den: it goes round it, along the pathfinder's route for its whole width.
                .thenExecute(() -> run.step(() -> {
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dz = -DEN_WALL_HALF; dz <= DEN_WALL_HALF; dz++)
                    {
                        for (int y = 1; y <= DEN_WALL_HEIGHT; y++)
                        {
                            run.level.setBlock(pos.set(den[0].getX() + DEN_WALL_X, FLOOR_Y + y, den[0].getZ() + dz),
                                    Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    crab[0].teleportTo(den[0].getX() + 0.5D + DEN_WALL_BEHIND, den[0].getY(), den[0].getZ() + 0.5D);
                }))
                .thenWaitUntil(() -> run.waitTicks("it to go round a wall back to its den", 900, () -> home(crab[0], den[0], walked, 1),
                        () -> "at " + crab[0].position() + ": " + crab[0].denState()))
                .thenExecute(() -> run.step(() -> {
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int dz = -DEN_WALL_HALF; dz <= DEN_WALL_HALF; dz++)
                    {
                        for (int y = 1; y <= DEN_WALL_HEIGHT; y++)
                        {
                            run.level.setBlock(pos.set(den[0].getX() + DEN_WALL_X, FLOOR_Y + y, den[0].getZ() + dz),
                                    Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    player[0] = run.join("cinder_den_visitor", 0.0D, -5.5D);
                    crab[0].hurt(run.level.damageSources().playerAttack(player[0]), 1.0F);
                }))
                .thenWaitUntil(() -> run.waitTicks("it to go for the player who hurt it from 5 blocks off", 80,
                        () -> crab[0].getTarget() == player[0], () -> "its target " + crab[0].getTarget()))
                .thenExecute(() -> run.step(() -> {
                    LOGGER.info("[cinder mobs test] a cave's crab (in M2C, {} wide) kept within {} of its den, walked back in {} ticks"
                            + " (round a wall in {}), and fought back", crab[0].getBbWidth(), String.format(Locale.ROOT, "%.2f", furthest[0]),
                            walked[0], walked[1]);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /** True once the crab is back at its den; counts the ticks it has walked in {@code walked[i]}. */
    private static boolean home(CinderHermit crab, BlockPos den, int[] walked, int i)
    {
        walked[i]++;
        return Math.hypot(crab.getX() - (den.getX() + 0.5D), crab.getZ() - (den.getZ() + 0.5D)) <= CinderMobTuning.CAVE_CRAB_DEN;
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
