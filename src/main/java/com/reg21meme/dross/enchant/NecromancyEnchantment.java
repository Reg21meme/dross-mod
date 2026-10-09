package com.reg21meme.dross.enchant;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Necromancy (swords only, I-V): hits have a chance to raise undead that fight for you.
 * The effect itself lives in {@link NecromancyEvents}; this class holds the rules and the tuning numbers.
 *
 * <p>Level V is for admin testing only (see "Parked for later" in CLAUDE.md).
 *
 * <p>Before the five-level restructure there were only four levels (I-IV). Items from that time are moved up one
 * level, exactly once, by {@link NecromancyScheme}.
 */
public class NecromancyEnchantment extends Enchantment
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers. Index 0 = level I ... index 4 = level V.
    // ---------------------------------------------------------------------------------------------

    /** Highest level (V, the admin level). */
    public static final int MAX_LEVEL = 5;

    /** Chance (0.0 - 1.0) that a hit raises undead: I 15%, II 20%, III 25%, IV 30%, V 100%. */
    public static final float[] RAISE_CHANCE = {0.15F, 0.20F, 0.25F, 0.30F, 1.00F};

    /**
     * How many of a player's risen undead can be alive at once: I 1, II 2, III 4, IV 6, V 10.
     * When none are alive, a success raises this many. When some are alive, it raises half of it, rounded up
     * (I 1, II 1, III 2, IV 3, V 5; see {@link #raiseWhenSomeAlive}), never going over this number.
     */
    public static final int[] MAX_ALIVE = {1, 2, 4, 6, 10};

    /** Most souls a sword can store: I 25, II 50, III 100, IV 250, V infinite (never spends). */
    public static final int[] SOUL_CAPACITY = {25, 50, 100, 250, SoulStorage.INFINITE};

    /** Souls each normal risen undead costs. */
    public static final int SOUL_COST_NORMAL = 1;
    /** Souls a jockey or skeleton horseman costs (it still only takes 1 "alive" slot). */
    public static final int SOUL_COST_MOUNTED = 2;

    /**
     * Enchantment rarity. In 1.20.1 this only changes the anvil cost here (the enchantment is treasure-only and
     * never discoverable). UNCOMMON costs 1 level per enchantment level from a book (2 from another sword),
     * so Necromancy V from a book costs just 5 levels.
     */
    private static final Rarity RARITY = Rarity.UNCOMMON;

    public NecromancyEnchantment()
    {
        super(RARITY, EnchantmentCategory.WEAPON, new EquipmentSlot[] {EquipmentSlot.MAINHAND});
    }

    /** Clamps a level read from an item to 1..MAX_LEVEL and turns it into an array index (0..4). */
    public static int index(int level)
    {
        return Math.max(1, Math.min(level, MAX_LEVEL)) - 1;
    }

    /** How many rise when the player already has some alive: half of {@link #MAX_ALIVE}, rounded up. */
    public static int raiseWhenSomeAlive(int index)
    {
        return (MAX_ALIVE[index] + 1) / 2;
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
