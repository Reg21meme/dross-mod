package com.reg21meme.dross.boss.ratking;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests (GameTest) for the Rat King design showcase ({@code .\gradlew.bat runGameTestServer}), in the Cinder
 * Colossus tests' arena ({@link ColossusGameTest#ARENA}):
 * <ul>
 *   <li><b>Kings:</b> every design spawns as a frozen showcase Rat King; ordinary damage doesn't hurt him; his silly
 *       death leaves him lying there (he isn't removed) and ends as {@link RatKingAction#DEAD}; waking up ends standing;
 *       the rage makes him enraged (red eyes) and sleeping calms him down; burrowing ends underground.</li>
 *   <li><b>Rats:</b> every rat design spawns frozen, the enraged ones as {@code dross:enraged_rat}; ordinary damage
 *       doesn't hurt them; they switch between idle and running.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RatKingShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A hit far bigger than a Rat King's health. */
    private static final float BIG_HIT = 1000.0F;
    /** Extra ticks to wait after an animation should have ended. */
    private static final int AFTER = 8;

    private RatKingShowcaseGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_ratking_showcase",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 400)
    public static void showcaseKingsPlayTheirAnimations(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RatKingShowcaseGameTest::showcaseKingsPlayTheirAnimations))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<RatKing> kings = new ArrayList<>();
        int i = 0;
        for (RatKingDesigns.Design design : RatKingDesigns.ALL)
        {
            Vec3 feet = helper.absoluteVec(new Vec3(2.5D + i * 2.5D, ColossusGameTest.MIDDLE.getY(), 8.5D));
            RatKing king = RatKing.spawnShowcase(level, feet, 0.0F, design);
            if (king == null || !king.isShowcase() || king.getDesign().number() != design.number())
            {
                throw new GameTestAssertException("couldn't spawn showcase Rat King No. " + design.label());
            }
            kings.add(king);
            i++;
        }
        RatKingDesigns.Design first = RatKingDesigns.ALL.get(0);
        int deathWait = RatKing.ANIMATION_BLEND_TICKS + maxOf(d -> d.deathTicks()) + AFTER;
        int wakeWait = RatKing.ANIMATION_BLEND_TICKS + maxOf(d -> d.wakeTicks()) + AFTER;
        int burrowWait = RatKing.ANIMATION_BLEND_TICKS + maxOf(d -> d.burrowTicks()) + AFTER;
        helper.startSequence()
                .thenExecute(() -> {
                    for (RatKing king : kings)
                    {
                        float before = king.getHealth();
                        king.hurt(level.damageSources().generic(), BIG_HIT);
                        if (king.isRemoved() || king.getHealth() < before)
                        {
                            throw new GameTestAssertException("a showcase Rat King (No. " + king.getDesign().label()
                                    + ") was hurt by an ordinary hit");
                        }
                        king.play(RatKingAction.DEATH);
                    }
                })
                .thenExecuteAfter(deathWait, () -> {
                    for (RatKing king : kings)
                    {
                        if (king.isRemoved() || king.getAction() != RatKingAction.DEAD)
                        {
                            throw new GameTestAssertException("No. " + king.getDesign().label() + " should be lying dead "
                                    + "(still there) after his death, but is " + (king.isRemoved() ? "gone" : king.getAction()));
                        }
                        king.play(RatKingAction.WAKE);
                    }
                })
                .thenExecuteAfter(wakeWait, () -> {
                    for (RatKing king : kings)
                    {
                        if (king.getAction() != RatKingAction.IDLE)
                        {
                            throw new GameTestAssertException("No. " + king.getDesign().label()
                                    + " should be standing after waking up, but is " + king.getAction());
                        }
                        king.play(RatKingAction.RAGE);
                        if (!king.isEnraged())
                        {
                            throw new GameTestAssertException("No. " + king.getDesign().label() + " isn't enraged by his rage");
                        }
                        king.play(RatKingAction.SLEEP);
                        if (king.isEnraged())
                        {
                            throw new GameTestAssertException("No. " + king.getDesign().label() + " is still enraged asleep");
                        }
                        king.play(RatKingAction.BURROW);
                    }
                })
                .thenExecuteAfter(burrowWait, () -> {
                    for (RatKing king : kings)
                    {
                        if (king.getAction() != RatKingAction.BURROWED)
                        {
                            throw new GameTestAssertException("No. " + king.getDesign().label()
                                    + " should be underground after burrowing, but is " + king.getAction());
                        }
                        king.discard();
                    }
                    LOGGER.info("[rat king test] {} showcase Rat Kings: can't be hurt, die and lie there, wake, rage, sleep "
                            + "and burrow as they should (No. {}: death {} ticks)", kings.size(), first.label(), first.deathTicks());
                })
                .thenSucceed();
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_rat_showcase",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 100)
    public static void showcaseRatsSpawnAndRun(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RatKingShowcaseGameTest::showcaseRatsSpawnAndRun))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<RatDesigns.Design> designs = new ArrayList<>(RatDesigns.REGULAR);
        designs.addAll(RatDesigns.ENRAGED);
        List<Rat> rats = new ArrayList<>();
        int i = 0;
        for (RatDesigns.Design design : designs)
        {
            Vec3 feet = helper.absoluteVec(new Vec3(1.5D + i * 1.8D, ColossusGameTest.MIDDLE.getY(), 8.5D));
            Rat rat = Rat.spawnShowcase(level, feet, 0.0F, design);
            if (rat == null || !rat.isShowcase())
            {
                throw new GameTestAssertException("couldn't spawn showcase rat " + design.label());
            }
            boolean enragedType = rat.getType() == ModEntities.ENRAGED_RAT.get();
            if (enragedType != design.enraged() || rat.getDesign().number() != design.number())
            {
                throw new GameTestAssertException("rat " + design.label() + " spawned as the wrong kind or design");
            }
            rats.add(rat);
            i++;
        }
        helper.startSequence()
                .thenExecute(() -> {
                    for (Rat rat : rats)
                    {
                        rat.hurt(level.damageSources().generic(), BIG_HIT);
                        if (rat.isRemoved() || rat.getHealth() < rat.getMaxHealth())
                        {
                            throw new GameTestAssertException("showcase rat " + rat.getDesign().label() + " was hurt");
                        }
                        rat.setRunning(true);
                    }
                })
                .thenExecuteAfter(5, () -> {
                    for (Rat rat : rats)
                    {
                        if (!rat.isRunning())
                        {
                            throw new GameTestAssertException("showcase rat " + rat.getDesign().label() + " isn't running");
                        }
                        rat.discard();
                    }
                    LOGGER.info("[rat king test] {} showcase rats spawned as the right kinds, can't be hurt, and run", rats.size());
                })
                .thenSucceed();
    }

    /** A rat from its spawn egg (or /summon) wears the user's pick of its kind: the Plague Rat, the Plague Ravager. */
    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_rat_showcase",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 40)
    public static void eggRatsWearThePicks(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RatKingShowcaseGameTest::eggRatsWearThePicks))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        for (boolean enraged : new boolean[] {false, true})
        {
            Rat rat = (enraged ? ModEntities.ENRAGED_RAT : ModEntities.RAT).get().create(level);
            if (rat == null)
            {
                throw new GameTestAssertException("couldn't create a " + (enraged ? "enraged " : "") + "rat");
            }
            String want = enraged ? "Plague Ravager" : "Plague Rat";
            if (rat.isShowcase() || !rat.getDesign().name().equals(want))
            {
                throw new GameTestAssertException("a new " + (enraged ? "enraged " : "") + "rat wears " + rat.getDesign().name()
                        + ", not the " + want);
            }
            rat.discard();
        }
        LOGGER.info("[rat king test] new rats wear the Plague Rat and the Plague Ravager");
        helper.succeed();
    }

    private static int maxOf(java.util.function.ToIntFunction<RatKingDesigns.Design> f)
    {
        int best = 0;
        for (RatKingDesigns.Design d : RatKingDesigns.ALL)
        {
            best = Math.max(best, f.applyAsInt(d));
        }
        return best;
    }
}
