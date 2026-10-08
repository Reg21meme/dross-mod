package com.reg21meme.dross.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Deathforged (swords only, I-X): risen undead from a Necromancy sword come armored.
 * Does nothing without Necromancy on the same sword. The gear itself is handed out in
 * {@link RisenUndead#applyDeathforgedGear}; the level thresholds are the constants below.
 *
 * <pre>
 *  I leather, II gold, III chainmail, IV iron, V diamond, VI netherite armor
 *  VII  + netherite sword for zombie types (skeletons keep their bow)
 *  VIII + Protection II, Sharpness II / Power II
 *  IX   + Protection IV, Sharpness V / Power V
 *  X    + Strength I and Speed I for their whole life, and a gold glow outline
 * </pre>
 */
public class DeathforgedEnchantment extends Enchantment
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers.
    // ---------------------------------------------------------------------------------------------

    /** Highest level. */
    public static final int MAX_LEVEL = 10;

    /** From this level on, zombie types also get a netherite sword. */
    public static final int SWORD_LEVEL = 7;

    /** From this level on, gear is enchanted with the "low" enchantment levels below. */
    public static final int ENCHANT_LOW_LEVEL = 8;
    public static final int PROTECTION_LOW = 2;
    public static final int SHARPNESS_LOW = 2;
    public static final int POWER_LOW = 2;

    /** From this level on, gear is enchanted with the "high" enchantment levels below. */
    public static final int ENCHANT_HIGH_LEVEL = 9;
    public static final int PROTECTION_HIGH = 4;
    public static final int SHARPNESS_HIGH = 5;
    public static final int POWER_HIGH = 5;

    /** At this level they also get Strength and Speed for their whole life, and glow. */
    public static final int EFFECTS_LEVEL = 10;
    /** Effect amplifiers: 0 means level I (Strength I, Speed I). */
    public static final int STRENGTH_AMPLIFIER = 0;
    public static final int SPEED_AMPLIFIER = 0;

    /**
     * Enchantment rarity. COMMON costs 1 level per enchantment level at the anvil (from a book or a sword),
     * so even Deathforged X only costs 10 levels, well under the 40-level "Too Expensive!" limit.
     * It is treasure-only and never discoverable, so the rarity changes nothing else.
     */
    private static final Rarity RARITY = Rarity.COMMON;

    public DeathforgedEnchantment()
    {
        super(RARITY, EnchantmentCategory.WEAPON, new EquipmentSlot[] {EquipmentSlot.MAINHAND});
    }

    @Override
    public int getMaxLevel()
    {
        return MAX_LEVEL;
    }

    @Override
    public boolean isTreasureOnly()
    {
        return true;
    }

    @Override
    public boolean isDiscoverable()
    {
        return false;
    }

    @Override
    public boolean isTradeable()
    {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks()
    {
        return true;
    }
}
