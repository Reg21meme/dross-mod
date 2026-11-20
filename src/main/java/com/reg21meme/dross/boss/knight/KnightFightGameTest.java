package com.reg21meme.dross.boss.knight;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTest) for the real elemental knights' fights ({@code .\gradlew.bat runGameTestServer}), one for each
 * knight, in the Cinder Colossus tests' arena: the knight (its own AI off, so only the moves the test starts happen)
 * faces an iron golem standing still (its AI off too) at a distance its move is used from, makes its first move, and
 * the golem must be hurt (or poisoned) by it; then the golem is healed and moved for the second move, and the same.
 * Also the knight's health and armour are its kind's.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class KnightFightGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_knight_fights";
    private static final int TIMEOUT = ColossusGameTest.ARENA_WAIT_TICKS + 600;
    /** The knight stands here (relative), facing south (+z), at the arena's north side. */
    private static final int KNIGHT_Z = 3;
    /** The golem is put at least this far, at most this far, south of it. */
    private static final double NEAREST = 3.0D;
    private static final double FURTHEST = 10.0D;
    /** How long after a move's animation ends its effects have to have hurt the golem (thrown things fly a while). */
    private static final int AFTER = 50;
    /** The golem's health the moves are measured from (more than any move takes). */
    private static final float GOLEM_HEALTH = 100.0F;

    private KnightFightGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void thawedAxeman(GameTestHelper helper)
    {
        fight(helper, KnightKind.THAWED_AXE);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void thawedSpearman(GameTestHelper helper)
    {
        fight(helper, KnightKind.THAWED_SPEAR);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void blightWarden(GameTestHelper helper)
    {
        fight(helper, KnightKind.BLIGHT_WARDEN);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void sporebound(GameTestHelper helper)
    {
        fight(helper, KnightKind.SPOREBOUND);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void cinderKnight(GameTestHelper helper)
    {
        fight(helper, KnightKind.CINDER);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void flamehand(GameTestHelper helper)
    {
        fight(helper, KnightKind.FLAMEHAND);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void wreckKnight(GameTestHelper helper)
    {
        fight(helper, KnightKind.WRECK);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void stormblade(GameTestHelper helper)
    {
        fight(helper, KnightKind.STORMBLADE);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void geodeKnight(GameTestHelper helper)
    {
        fight(helper, KnightKind.GEODE);
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rockfallKnight(GameTestHelper helper)
    {
        fight(helper, KnightKind.ROCKFALL);
    }

    private static void fight(GameTestHelper helper, KnightKind kind)
    {
        if (ColossusGameTest.waitForArena(helper, h -> fight(h, kind)))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos middle = ColossusGameTest.MIDDLE;
        Vec3 feet = helper.absoluteVec(new Vec3(middle.getX() + 0.5D, middle.getY(), KNIGHT_Z + 0.5D));
        ElementalKnight knight = ModEntities.KNIGHTS.get(kind).get().create(level);
        IronGolem golem = EntityType.IRON_GOLEM.create(level);
        if (knight == null || golem == null)
        {
            throw new GameTestAssertException("couldn't make the " + kind + " or its golem");
        }
        knight.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        knight.setYBodyRot(0.0F);
        knight.setYHeadRot(0.0F);
        knight.setNoAi(true);
        level.addFreshEntity(knight);
        if (Math.abs(knight.getMaxHealth() - kind.health) > 0.01D || Math.abs(knight.getArmorValue() - kind.armor) > 0.01D)
        {
            throw new GameTestAssertException(kind + " has " + knight.getMaxHealth() + " health and " + knight.getArmorValue()
                    + " armour, not " + kind.health + " and " + kind.armor);
        }
        golem.setNoAi(true);
        golem.setPersistenceRequired();
        golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(GOLEM_HEALTH);
        golem.setHealth(GOLEM_HEALTH);
        level.addFreshEntity(golem);
        knight.setTarget(golem);
        StringBuilder log = new StringBuilder();
        try
        {
            tryMove(helper, knight, golem, feet, 0, () -> tryMove(helper, knight, golem, feet, 1, () ->
            {
                knight.discard();
                golem.discard();
                LOGGER.info("[knight fight test] {}: {}", kind, log.append("both moves hurt their foe"));
                helper.succeed();
            }));
        }
        catch (RuntimeException e)
        {
            knight.discard();
            golem.discard();
            throw e;
        }
    }

    /** Puts the golem where move {@code index} is used from, makes it, and checks the golem was hurt, then {@code next}. */
    private static void tryMove(GameTestHelper helper, ElementalKnight knight, IronGolem golem, Vec3 feet, int index,
                                Runnable next)
    {
        KnightKind kind = knight.kind();
        KnightKind.Move move = kind.move(index);
        double d = Math.min(FURTHEST, Math.max(NEAREST, Math.min(move.maxRange() - 1.0D, move.minRange() + 1.5D)));
        golem.setHealth(GOLEM_HEALTH);
        golem.removeAllEffects();
        // (fire can't hurt it: a move must do harm of its own, not only set it burning or leave fire under it)
        golem.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
        golem.clearFire();
        golem.setDeltaMovement(Vec3.ZERO);
        golem.moveTo(feet.x, feet.y, feet.z + d, 180.0F, 0.0F);
        knight.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        knight.setYBodyRot(0.0F);
        knight.setYHeadRot(0.0F);
        knight.startMove(index);
        int length = KnightAnims.anim(kind.model, move.anim()).ticks();
        int wait = (length > 0 ? length : KnightTuning.DASH_MAX_TICKS + KnightTuning.DASH_RECOVER) + AFTER;
        helper.runAfterDelay(wait, () ->
        {
            boolean hurt = golem.getHealth() < GOLEM_HEALTH - 0.5F || golem.hasEffect(MobEffects.POISON);
            if (!hurt)
            {
                knight.discard();
                golem.discard();
                throw new GameTestAssertException(kind + "'s " + move.anim() + " from " + d + " blocks didn't hurt the golem "
                        + "(its health " + golem.getHealth() + "; the knight at " + knight.position() + ", the golem at "
                        + golem.position() + ")");
            }
            LOGGER.info("[knight fight test] {}'s {} from {} blocks took {} of the golem's health", kind, move.anim(), d,
                    GOLEM_HEALTH - golem.getHealth());
            next.run();
        });
    }
}
