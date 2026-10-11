package com.reg21meme.dross.enchant;

import javax.annotation.Nullable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/**
 * Deathforged (swords only, I-X): risen undead from a Necromancy sword come armored.
 * Does nothing without Necromancy on the same sword. The gear itself is handed out in
 * {@link RisenUndead#applyDeathforgedGear}; what each level gives is the gear table below.
 *
 * <p>Armor goes on every risen type, the sword only on zombie types. Skeleton types always hold a plain bow,
 * and mounts get nothing. Nothing is ever enchanted, and none of it drops.
 *
 * <pre>
 *  Level  Armor       Sword (zombie types)
 *  I      leather     none
 *  II     leather     wooden
 *  III    gold        gold
 *  IV     chainmail   stone
 *  V      iron        stone
 *  VI     iron        iron
 *  VII    diamond     iron
 *  VIII   diamond     diamond
 *  IX     netherite   netherite
 *  X      netherite   netherite, plus Strength I and Speed I for their whole life (no glow, no effect swirls)
 * </pre>
 */
public class DeathforgedEnchantment extends Enchantment
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers.
    // ---------------------------------------------------------------------------------------------

    /** Highest level. */
    public static final int MAX_LEVEL = 10;

    /** At this level they also get Strength and Speed for their whole life (particles hidden, no glow). */
    public static final int EFFECTS_LEVEL = 10;
    /** Effect amplifiers: 0 means level I (Strength I, Speed I). */
    public static final int STRENGTH_AMPLIFIER = 0;
    public static final int SPEED_AMPLIFIER = 0;

    // ---------------------------------------------------------------------------------------------
    // The gear table: armor set and sword per level. Change a row here to retune a level.
    // ---------------------------------------------------------------------------------------------

    /** Armor sets, each in the order helmet, chestplate, leggings, boots. */
    private static final Item[] LEATHER = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
    private static final Item[] GOLD = {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
    private static final Item[] CHAINMAIL = {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
    private static final Item[] IRON = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final Item[] DIAMOND = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
    private static final Item[] NETHERITE = {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS};

    /**
     * One level's gear.
     *
     * @param armor helmet, chestplate, leggings, boots (every risen type)
     * @param sword the zombie types' sword, or null for none
     */
    public record Gear(Item[] armor, @Nullable Item sword) {}

    /** Gear per level: the first row is level I, the last is level X. */
    private static final Gear[] GEAR_BY_LEVEL = {
            new Gear(LEATHER, null),                    // I
            new Gear(LEATHER, Items.WOODEN_SWORD),      // II
            new Gear(GOLD, Items.GOLDEN_SWORD),         // III
            new Gear(CHAINMAIL, Items.STONE_SWORD),     // IV
            new Gear(IRON, Items.STONE_SWORD),          // V
            new Gear(IRON, Items.IRON_SWORD),           // VI
            new Gear(DIAMOND, Items.IRON_SWORD),        // VII
            new Gear(DIAMOND, Items.DIAMOND_SWORD),     // VIII
            new Gear(NETHERITE, Items.NETHERITE_SWORD), // IX
            new Gear(NETHERITE, Items.NETHERITE_SWORD), // X (plus the effects above)
    };

    /** The gear for this Deathforged level, or null for level 0 (no Deathforged). Levels above X count as X. */
    @Nullable
    public static Gear gearFor(int level)
    {
        if (level < 1)
        {
            return null;
        }
        return GEAR_BY_LEVEL[Math.min(level, GEAR_BY_LEVEL.length) - 1];
    }

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
