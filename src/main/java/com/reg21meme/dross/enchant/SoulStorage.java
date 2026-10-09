package com.reg21meme.dross.enchant;

import com.reg21meme.dross.registry.ModEnchantments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Souls live in the sword's own item tag, so every sword has its own count and it moves with the sword
 * (dropped, traded, put in a chest...).
 */
public final class SoulStorage
{
    /** Item tag key that holds the soul count. */
    public static final String SOULS_TAG = "dross_souls";

    /** Marker for "infinite" capacity (Necromancy V, the admin level). Such a sword never spends souls. */
    public static final int INFINITE = -1;

    private SoulStorage() {}

    /** Necromancy level on this item (0 if none). */
    public static int necromancyLevel(ItemStack stack)
    {
        return stack.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.NECROMANCY.get(), stack);
    }

    /** Soul capacity for this sword, or {@link #INFINITE}. 0 if the item has no Necromancy. */
    public static int capacity(ItemStack stack)
    {
        int level = necromancyLevel(stack);
        return level <= 0 ? 0 : NecromancyEnchantment.SOUL_CAPACITY[NecromancyEnchantment.index(level)];
    }

    public static boolean isInfinite(ItemStack stack)
    {
        return capacity(stack) == INFINITE;
    }

    /** Stored souls (0 if none yet). */
    public static int getSouls(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getInt(SOULS_TAG));
    }

    public static void setSouls(ItemStack stack, int souls)
    {
        stack.getOrCreateTag().putInt(SOULS_TAG, Math.max(0, souls));
    }

    /** Adds one soul if there's room. Returns true if one was added. Infinite swords never change. */
    public static boolean addSoul(ItemStack stack)
    {
        int capacity = capacity(stack);
        if (capacity == INFINITE || capacity <= 0)
        {
            return false;
        }
        int souls = getSouls(stack);
        if (souls >= capacity)
        {
            return false;
        }
        setSouls(stack, souls + 1);
        return true;
    }
}
