package com.reg21meme.dross.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Necromancy (swords only, I-IV): hits have a chance to raise undead that fight for you.
 * The effect itself lives in {@link NecromancyEvents}; this class holds the rules and the tuning numbers.
 *
 * <p>Level IV is for admin testing only (see "Parked for later" in CLAUDE.md).
 */
public class NecromancyEnchantment extends Enchantment
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers. Index 0 = level I, index 3 = level IV.
    // ---------------------------------------------------------------------------------------------

    /** Highest level. */
    public static final int MAX_LEVEL = 4;

    /** Chance (0.0 - 1.0) that a hit raises undead: I 5%, II 10%, III 15%, IV 100%. */
    public static final float[] RAISE_CHANCE = {0.05F, 0.10F, 0.15F, 1.00F};

    /** How many of a player's risen undead can be alive at once: I 2, II 4, III 6, IV 10. */
    public static final int[] MAX_ALIVE = {2, 4, 6, 10};

    /**
     * How many rise when the player already has some alive (never going over MAX_ALIVE).
     * When none are alive, a success raises the full MAX_ALIVE instead. I 1, II 2, III 3, IV 5.
     */
    public static final int[] RAISE_WHEN_SOME_ALIVE = {1, 2, 3, 5};

    /** Most souls a sword can store: I 25, II 100, III 250, IV infinite (never spends). */
    public static final int[] SOUL_CAPACITY = {25, 100, 250, SoulStorage.INFINITE};

    /** Souls each normal risen undead costs. */
    public static final int SOUL_COST_NORMAL = 1;
    /** Souls a jockey or skeleton horseman costs (it still only takes 1 "alive" slot). */
    public static final int SOUL_COST_MOUNTED = 2;

    /**
     * Enchantment rarity. In 1.20.1 this only changes the anvil cost here (the enchantment is treasure-only and
     * never discoverable). UNCOMMON costs 1 level per enchantment level from a book (2 from another sword),
     * so Necromancy IV from a book costs just 4 levels.
     */
    private static final Rarity RARITY = Rarity.UNCOMMON;

    public NecromancyEnchantment()
    {
        super(RARITY, EnchantmentCategory.WEAPON, new EquipmentSlot[] {EquipmentSlot.MAINHAND});
    }

    /** Clamps a level read from an item to 1..MAX_LEVEL and turns it into an array index (0..3). */
    public static int index(int level)
    {
        return Math.max(1, Math.min(level, MAX_LEVEL)) - 1;
    }

    @Override
    public int getMaxLevel()
    {
        return MAX_LEVEL;
    }

    /** Only from the trader and the creative tab, never from the enchanting table or loot. */
    @Override
    public boolean isTreasureOnly()
    {
        return true;
    }

    /** False keeps it out of the enchanting table, random loot enchanting and fishing. */
    @Override
    public boolean isDiscoverable()
    {
        return false;
    }

    /** False keeps it out of normal librarian trades. */
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

    /** Can't be on the same sword as Smite. */
    @Override
    protected boolean checkCompatibility(Enchantment other)
    {
        return super.checkCompatibility(other) && other != Enchantments.SMITE;
    }
}
