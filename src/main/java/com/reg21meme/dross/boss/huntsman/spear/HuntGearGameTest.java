package com.reg21meme.dross.boss.huntsman.spear;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.skull.HuntSkullBlock;
import com.reg21meme.dross.boss.huntsman.skull.SkullDrops;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * The Huntsman's gear for players and the villages (CLAUDE.md step 14, October 22, 2026): the Moonsilver Spear (its
 * recipe, a stab that freezes, a throw that hits, freezes and comes back), the Planted Moonsilver Spear and the three
 * mounted skulls as blocks, and who drops a skull.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HuntGearGameTest
{
    private static final String ARENA = ColossusGameTest.ARENA;
    private static final int TIMEOUT = 20_000;
    private static final int SKULL_ROLLS = 600;

    private HuntGearGameTest() {}

    private static Zombie zombie(GameTestHelper helper, BlockPos at)
    {
        Zombie z = helper.spawn(EntityType.ZOMBIE, at);
        z.setNoAi(true);
        z.setPersistenceRequired();
        return z;
    }

    private static boolean frozen(Zombie z)
    {
        return z.getTicksFrozen() >= z.getTicksRequiredToFreeze() && z.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
    }

    /** The recipe the user asked for: two sticks on a diagonal, the Moonsilver Ingot at the top right. */
    @GameTest(template = ARENA, batch = "dross_hunt_gear_recipe", timeoutTicks = TIMEOUT)
    public static void spearRecipe(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntGearGameTest::spearRecipe))
        {
            return;
        }
        Optional<? extends Recipe<?>> recipe = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(Dross.MODID, "moonsilver_spear"));
        if (recipe.isEmpty() || !(recipe.get() instanceof ShapedRecipe shaped)
                || !shaped.getResultItem(helper.getLevel().registryAccess()).is(ModItems.MOONSILVER_SPEAR.get()))
        {
            throw new GameTestAssertException("no shaped recipe dross:moonsilver_spear making the Moonsilver Spear");
        }
        // (3 x 3, row by row from the top: the ingot top right, sticks in the middle and bottom left)
        var at = shaped.getIngredients();
        boolean ok = shaped.getWidth() == 3 && shaped.getHeight() == 3
                && at.get(2).test(new ItemStack(ModItems.MOONSILVER_INGOT.get()))
                && at.get(4).test(new ItemStack(net.minecraft.world.item.Items.STICK))
                && at.get(6).test(new ItemStack(net.minecraft.world.item.Items.STICK))
                && at.get(0).isEmpty() && at.get(8).isEmpty();
        if (!ok || !(recipe.get() instanceof CraftingRecipe))
        {
            throw new GameTestAssertException("the spear's recipe should be the ingot at the top right over two sticks on a diagonal");
        }
        helper.succeed();
    }

    /** A stab does the spear's damage (its modifier) and freezes what it hits. */
    @GameTest(template = ARENA, batch = "dross_hunt_gear_stab", timeoutTicks = TIMEOUT)
    public static void spearStabFreezes(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntGearGameTest::spearStabFreezes))
        {
            return;
        }
        ItemStack spear = new ItemStack(ModItems.MOONSILVER_SPEAR.get());
        double damage = spear.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).stream()
                .mapToDouble(AttributeModifier::getAmount).sum() + 1.0D;
        Zombie target = zombie(helper, ColossusGameTest.MIDDLE.offset(2, 0, 0));
        Zombie attacker = zombie(helper, ColossusGameTest.MIDDLE.offset(-2, 0, 0));
        spear.getItem().hurtEnemy(spear, target, attacker);
        boolean cold = frozen(target);
        int used = spear.getDamageValue();
        target.discard();
        attacker.discard();
        if (Math.abs(damage - SpearTuning.STAB_DAMAGE) > 1.0E-6D)
        {
            throw new GameTestAssertException("a stab should do " + SpearTuning.STAB_DAMAGE + ", it does " + damage);
        }
        if (!cold)
        {
            throw new GameTestAssertException("a stab should freeze what it hits (frozen and slowed)");
        }
        if (used != 1)
        {
            throw new GameTestAssertException("a stab should wear the spear by 1, it wore it by " + used);
        }
        helper.succeed();
    }

    /** Thrown, it hits, freezes what it hit, and flies back to its thrower. */
    @GameTest(template = ARENA, batch = "dross_hunt_gear_throw", timeoutTicks = TIMEOUT)
    public static void thrownSpearComesBack(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntGearGameTest::thrownSpearComesBack))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Skeleton thrower = helper.spawn(EntityType.SKELETON, ColossusGameTest.MIDDLE.offset(-5, 0, 0));
        thrower.setNoAi(true);
        thrower.setPersistenceRequired();
        Zombie target = zombie(helper, ColossusGameTest.MIDDLE.offset(4, 0, 0));
        float before = target.getHealth();
        ThrownMoonsilverSpear spear = new ThrownMoonsilverSpear(level, thrower, new ItemStack(ModItems.MOONSILVER_SPEAR.get()));
        Vec3 from = thrower.getEyePosition();
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        spear.setPos(from.x, from.y, from.z);
        Vec3 d = to.subtract(from);
        spear.shoot(d.x, d.y, d.z, SpearTuning.THROW_POWER, 0.0F);
        level.addFreshEntity(spear);
        double[] closest = {Double.MAX_VALUE};
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    if (target.getHealth() >= before)
                    {
                        throw new GameTestAssertException("waiting for the spear to hit");
                    }
                })
                .thenExecute(() ->
                {
                    if (before - target.getHealth() < SpearTuning.THROW_DAMAGE * 0.7F || !frozen(target))
                    {
                        clear(thrower, target, spear);
                        throw new GameTestAssertException("a thrown spear should hit for about " + SpearTuning.THROW_DAMAGE
                                + " and freeze; it took " + (before - target.getHealth()) + ", frozen " + frozen(target));
                    }
                })
                .thenWaitUntil(() ->
                {
                    double gap = spear.distanceTo(thrower);
                    closest[0] = Math.min(closest[0], gap);
                    if (closest[0] > 2.5D)
                    {
                        throw new GameTestAssertException("waiting for the spear to come back (" + gap + " blocks off)");
                    }
                })
                .thenExecute(() -> clear(thrower, target, spear))
                .thenSucceed();
    }

    /** Every skull stands on the floor and hangs on a wall facing each way; the planted spear stands. */
    @GameTest(template = ARENA, batch = "dross_hunt_gear_blocks", timeoutTicks = TIMEOUT)
    public static void skullsAndSpearsPlace(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntGearGameTest::skullsAndSpearsPlace))
        {
            return;
        }
        List<Block> skulls = List.of(ModBlocks.ALPHA_MOOSE_SKULL.get(), ModBlocks.MOOSE_SKULL.get(), ModBlocks.RIMEHORN_SKULL.get());
        BlockPos at = ColossusGameTest.MIDDLE.offset(0, 1, 0);
        for (Block skull : skulls)
        {
            for (Direction facing : Direction.Plane.HORIZONTAL)
            {
                for (boolean wall : new boolean[] {false, true})
                {
                    BlockState state = skull.defaultBlockState().setValue(HuntSkullBlock.FACING, facing).setValue(HuntSkullBlock.WALL, wall);
                    helper.setBlock(at, state);
                    if (helper.getBlockState(at) != state)
                    {
                        throw new GameTestAssertException("couldn't place " + state);
                    }
                }
            }
        }
        helper.setBlock(at, ModBlocks.PLANTED_SPEAR.get());
        if (!helper.getBlockState(at).is(ModBlocks.PLANTED_SPEAR.get()))
        {
            throw new GameTestAssertException("couldn't place the Planted Moonsilver Spear");
        }
        helper.setBlock(at, net.minecraft.world.level.block.Blocks.AIR);
        helper.succeed();
    }

    /** The Alpha Moose drops its skull every time it dies; a bull moose's or a Rimehorn's comes now and then, only
     * for a player's kill. */
    @GameTest(template = ARENA, batch = "dross_hunt_gear_skull_drops", timeoutTicks = TIMEOUT)
    public static void whoDropsASkull(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntGearGameTest::whoDropsASkull))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        AlphaMoose moose = helper.spawn(ModEntities.ALPHA_MOOSE.get(), ColossusGameTest.MIDDLE);
        moose.setNoAi(true);
        moose.setHealth(0.0F);
        moose.die(level.damageSources().generic());
        AABB around = new AABB(helper.absolutePos(ColossusGameTest.MIDDLE)).inflate(8.0D);
        int alpha = count(level, around, ModItems.ALPHA_MOOSE_SKULL.get());
        // the bull moose's and the Rimehorn's roll, on a stand-in body
        Cow body = helper.spawn(EntityType.COW, ColossusGameTest.MIDDLE.offset(3, 0, 3));
        body.setNoAi(true);
        for (int i = 0; i < SKULL_ROLLS; i++)
        {
            SkullDrops.sometimes(body, ModItems.MOOSE_SKULL.get(), 0, false);
        }
        int unearned = count(level, around, ModItems.MOOSE_SKULL.get());
        for (int i = 0; i < SKULL_ROLLS; i++)
        {
            SkullDrops.sometimes(body, ModItems.MOOSE_SKULL.get(), 0, true);
        }
        int earned = count(level, around, ModItems.MOOSE_SKULL.get());
        level.getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
        moose.discard();
        body.discard();
        if (alpha != 1)
        {
            throw new GameTestAssertException("the Alpha Moose should drop its skull when it dies (found " + alpha + ")");
        }
        if (unearned != 0)
        {
            throw new GameTestAssertException("a moose's skull should only come from a player's kill");
        }
        double expect = SKULL_ROLLS * SkullDrops.CHANCE;
        double sd = Math.sqrt(SKULL_ROLLS * SkullDrops.CHANCE * (1.0D - SkullDrops.CHANCE));
        if (Math.abs(earned - expect) > 4.0D * sd)
        {
            throw new GameTestAssertException("a player's kills should give a skull about " + (int) (SkullDrops.CHANCE * 100)
                    + "% of the time: " + earned + " of " + SKULL_ROLLS);
        }
        helper.succeed();
    }

    private static int count(ServerLevel level, AABB box, net.minecraft.world.item.Item item)
    {
        return level.getEntitiesOfClass(ItemEntity.class, box, e -> e.getItem().is(item)).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void clear(net.minecraft.world.entity.Entity... entities)
    {
        for (net.minecraft.world.entity.Entity e : entities)
        {
            e.discard();
        }
    }
}
