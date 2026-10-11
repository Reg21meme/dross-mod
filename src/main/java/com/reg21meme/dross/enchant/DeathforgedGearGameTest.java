package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless test (GameTest) for the Deathforged gear scale. Run it with
 * {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world with {@code /test runall}.
 * It only exists in the development environment.
 *
 * <p>For every level from 0 (no Deathforged) to X it raises a zombie and a skeleton the way Necromancy does and
 * checks: the armor and the zombie's sword match the table below (the user's table, written out again here on
 * purpose so a mistake in {@link DeathforgedEnchantment}'s table is caught); the skeleton holds a plain bow; nothing
 * is enchanted; only level X has Strength I and Speed I (with hidden particles); nothing glows or is on
 * a scoreboard team.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class DeathforgedGearGameTest
{
    /** Expected armor material per level (index = level, 0 = none). */
    private static final Item[][] EXPECTED_ARMOR = {
            null,
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},     // I
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},     // II
            {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},         // III
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS}, // IV
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},                 // V
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},                 // VI
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},     // VII
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},     // VIII
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}, // IX
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS}, // X
    };

    /** Expected zombie sword per level (index = level; null = empty hand). */
    private static final Item[] EXPECTED_SWORD = {
            null,                   // none
            null,                   // I
            Items.WOODEN_SWORD,     // II
            Items.GOLDEN_SWORD,     // III
            Items.STONE_SWORD,      // IV
            Items.STONE_SWORD,      // V
            Items.IRON_SWORD,       // VI
            Items.IRON_SWORD,       // VII
            Items.DIAMOND_SWORD,    // VIII
            Items.NETHERITE_SWORD,  // IX
            Items.NETHERITE_SWORD,  // X
    };

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final int TIMEOUT_TICKS = 100;

    private DeathforgedGearGameTest() {}

    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_deathforged_gear", timeoutTicks = TIMEOUT_TICKS)
    public static void gearMatchesTheTable(GameTestHelper helper)
    {
        ServerPlayer owner = RisenTargetGameTest.addOwner(helper);
        try
        {
            for (int level = 0; level <= DeathforgedEnchantment.MAX_LEVEL; level++)
            {
                String zombieProblem = check(raiseOne(owner, RisenType.ZOMBIE, level), level, false);
                String skeletonProblem = check(raiseOne(owner, RisenType.SKELETON, level), level, true);
                String problem = zombieProblem != null ? "zombie: " + zombieProblem : skeletonProblem != null ? "skeleton: " + skeletonProblem : null;
                if (problem != null)
                {
                    RisenTargetGameTest.removeOwner(owner);
                    helper.fail("Deathforged level " + level + ", " + problem);
                    return;
                }
            }
        }
        finally
        {
            RisenTargetGameTest.removeOwner(owner); // his risen undead crumble
        }
        helper.succeed();
    }

    /** Raises one risen undead as Necromancy does, and returns it (or null if it didn't spawn). */
    @Nullable
    private static Mob raiseOne(ServerPlayer owner, RisenType type, int level)
    {
        Set<Mob> before = new HashSet<>(RisenUndead.active());
        if (!RisenUndead.raise(owner, type, level, null))
        {
            return null;
        }
        for (Mob mob : RisenUndead.active())
        {
            if (!before.contains(mob))
            {
                return mob;
            }
        }
        return null;
    }

    /** What's wrong with this risen mob's gear for the level, or null if it's right. */
    @Nullable
    private static String check(@Nullable Mob mob, int level, boolean skeleton)
    {
        if (mob == null)
        {
            return "it couldn't be raised";
        }
        Item[] armor = EXPECTED_ARMOR[level];
        for (int i = 0; i < ARMOR_SLOTS.length; i++)
        {
            ItemStack worn = mob.getItemBySlot(ARMOR_SLOTS[i]);
            Item expected = armor == null ? Items.AIR : armor[i];
            if (!worn.is(expected))
            {
                return "wears " + worn.getItem() + " in the " + ARMOR_SLOTS[i].getName() + " slot, expected " + expected;
            }
        }

        ItemStack hand = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        Item expectedHand = skeleton ? Items.BOW : EXPECTED_SWORD[level] == null ? Items.AIR : EXPECTED_SWORD[level];
        if (!hand.is(expectedHand))
        {
            return "holds " + hand.getItem() + ", expected " + expectedHand;
        }

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEnchanted())
            {
                return "its " + stack.getItem() + " is enchanted";
            }
        }

        boolean effects = level >= DeathforgedEnchantment.EFFECTS_LEVEL;
        String effectProblem = checkEffect(mob, MobEffects.DAMAGE_BOOST, "Strength", effects);
        if (effectProblem == null)
        {
            effectProblem = checkEffect(mob, MobEffects.MOVEMENT_SPEED, "Speed", effects);
        }
        if (effectProblem != null)
        {
            return effectProblem;
        }

        if (mob.hasGlowingTag() || mob.isCurrentlyGlowing())
        {
            return "it glows";
        }
        if (mob.getTeam() != null)
        {
            return "it's on the scoreboard team " + mob.getTeam().getName();
        }
        return null;
    }

    /** Level X must have this effect at level I with hidden particles; every other level must not have it. */
    @Nullable
    private static String checkEffect(Mob mob, MobEffect effect, String name, boolean expected)
    {
        MobEffectInstance instance = mob.getEffect(effect);
        if (!expected)
        {
            return instance == null ? null : "it has " + name + " below level X";
        }
        if (instance == null)
        {
            return "it has no " + name;
        }
        if (instance.getAmplifier() != 0 || !instance.isInfiniteDuration() || instance.isVisible())
        {
            return "its " + name + " should be level I, infinite, with hidden particles";
        }
        return null;
    }
}
