package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEnchantments;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Extra anvil rules for the Dross enchantments (book + book, book onto sword, or sword + sword).
 * When a rule says "no result", the anvil shows nothing.
 *
 * <ul>
 *   <li><b>Necromancy never levels up at the anvil.</b>
 *     <ul>
 *       <li>Books: two items with the same level, where at least one is a book (book + book, or a book onto a sword),
 *           give no result. A higher level onto a lower one works.</li>
 *       <li>Sword + sword: allowed even with the same level, but the result keeps that level (III + III = III).
 *           Different levels keep the higher one, as vanilla does. The souls of both swords are added together,
 *           up to the result's capacity ({@link NecromancyEnchantment#SOUL_CAPACITY}). If nothing would change at all
 *           (same enchantments, no repair, no rename), there is no result.</li>
 *       <li>Necromancy + Smite is blocked by {@link NecromancyEnchantment#checkCompatibility}.</li>
 *     </ul></li>
 *   <li><b>Deathforged</b>, only when <i>both</i> items carry it:
 *     <ul>
 *       <li>If either one is {@link #DEATHFORGED_NO_COMBINE_FROM} (VI) or higher: no result.</li>
 *       <li>Two equal levels give level + 1 (I+I=II ... IV+IV=V), but never above {@link #DEATHFORGED_COMBINE_CAP} (V):
 *           V+V gives no result.</li>
 *       <li>Different levels (all below VI) keep the higher one, as vanilla does.</li>
 *     </ul>
 *     A single Deathforged book of any level (VI-X too) onto a sword without Deathforged still works.</li>
 * </ul>
 *
 * XP costs are vanilla's. For sword + sword with Necromancy this class works out the result itself (a copy of
 * vanilla's anvil math with the Necromancy and soul changes above), because Forge has no way to adjust vanilla's
 * result afterwards.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class AnvilRules
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers.
    // ---------------------------------------------------------------------------------------------

    /** Highest Deathforged level the anvil can make by combining two equal levels (V). V+V gives no result. */
    public static final int DEATHFORGED_COMBINE_CAP = 5;

    /** From this Deathforged level on (VI), an item can't be combined with another Deathforged item at all. */
    public static final int DEATHFORGED_NO_COMBINE_FROM = 6;

    /**
     * Sword + sword: whether adding the souls together is, on its own, enough to give a result.
     * false = if the enchantments, durability and name would all stay the same, there is no result,
     * even when both swords hold souls.
     */
    public static final boolean SOULS_ALONE_ALLOW_COMBINE = false;

    /** Vanilla: the anvil refuses (shows "Too Expensive!") at this many levels or more, except in creative. */
    private static final int TOO_EXPENSIVE = 40;

    private AnvilRules() {}

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event)
    {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty())
        {
            return;
        }
        if (blockDeathforged(left, right))
        {
            noResult(event, 0);
            return;
        }
        if (isItemCombine(left, right) && (necromancyLevel(left) > 0 || necromancyLevel(right) > 0))
        {
            combineNecromancyItems(event, left, right);
            return;
        }
        if (blockNecromancyBooks(left, right))
        {
            noResult(event, 0);
        }
    }

    /**
     * Shows no result. Cancelling stops the anvil, but it would leave an old result in the slot, so clear that too.
     * {@code cost} is the number shown above the slot (40 or more shows "Too Expensive!"; 0 shows nothing).
     */
    private static void noResult(AnvilUpdateEvent event, int cost)
    {
        event.setCanceled(true);
        if (event.getPlayer() != null && event.getPlayer().containerMenu instanceof AnvilMenu anvil)
        {
            anvil.getSlot(AnvilMenu.RESULT_SLOT).set(ItemStack.EMPTY);
            anvil.setMaximumCost(cost);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Necromancy
    // ---------------------------------------------------------------------------------------------

    /** Book + book or a book onto a sword: the same Necromancy level on both gives no result. */
    private static boolean blockNecromancyBooks(ItemStack left, ItemStack right)
    {
        int leftLevel = necromancyLevel(left);
        return leftLevel > 0 && leftLevel == necromancyLevel(right);
    }

    /**
     * Vanilla's "two of the same item" combine (sword + sword): not books, the same damageable item, and the right
     * item isn't a repair material for the left one (that's a plain repair, left to vanilla).
     */
    private static boolean isItemCombine(ItemStack left, ItemStack right)
    {
        return !left.is(Items.ENCHANTED_BOOK) && !right.is(Items.ENCHANTED_BOOK)
                && left.isDamageableItem() && left.is(right.getItem())
                && !left.getItem().isValidRepairItem(left, right);
    }

    /**
     * Sword + sword where at least one has Necromancy. Follows vanilla's anvil math step by step, except:
     * the same Necromancy level stays the same, the souls are added together, and nothing changing means no result.
     * (Deathforged's limits were already checked by {@link #blockDeathforged}.)
     */
    private static void combineNecromancyItems(AnvilUpdateEvent event, ItemStack left, ItemStack right)
    {
        Enchantment necromancy = ModEnchantments.NECROMANCY.get();
        Player player = event.getPlayer();
        boolean creative = player != null && player.getAbilities().instabuild;

        ItemStack result = left.copy();
        Map<Enchantment, Integer> leftEnchantments = EnchantmentHelper.getEnchantments(left);
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(result);
        int baseCost = event.getCost(); // both items' "prior work" penalty, filled in by vanilla
        int cost = 0;                   // vanilla's "i": levels for repair, enchantments and renaming
        int renameCost = 0;             // vanilla's "k"

        // Durability: the right sword's durability plus a 12% bonus, as vanilla.
        int leftLeft = left.getMaxDamage() - left.getDamageValue();
        int rightLeft = right.getMaxDamage() - right.getDamageValue();
        int newDamage = Math.max(0, result.getMaxDamage() - (leftLeft + rightLeft + result.getMaxDamage() * 12 / 100));
        if (newDamage < result.getDamageValue())
        {
            result.setDamageValue(newDamage);
            cost += 2;
        }

        // Enchantments from the right sword.
        boolean anyApplied = false;
        boolean anyRefused = false;
        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(right).entrySet())
        {
            Enchantment enchantment = entry.getKey();
            if (enchantment == null)
            {
                continue;
            }
            int leftLevel = enchantments.getOrDefault(enchantment, 0);
            int rightLevel = entry.getValue();
            int level;
            if (enchantment == necromancy)
            {
                level = Math.max(leftLevel, rightLevel); // never levels up: III + III = III
            }
            else
            {
                level = leftLevel == rightLevel ? rightLevel + 1 : Math.max(rightLevel, leftLevel);
            }

            boolean fits = enchantment.canEnchant(left) || creative;
            for (Enchantment other : enchantments.keySet())
            {
                if (other != enchantment && !enchantment.isCompatibleWith(other))
                {
                    fits = false;
                    cost++;
                }
            }
            if (!fits)
            {
                anyRefused = true;
                continue;
            }
            anyApplied = true;
            level = Math.min(level, enchantment.getMaxLevel());
            enchantments.put(enchantment, level);
            cost += costPerLevel(enchantment) * level;
            if (left.getCount() > 1)
            {
                cost = TOO_EXPENSIVE;
            }
        }
        if (anyRefused && !anyApplied)
        {
            noResult(event, 0);
            return;
        }

        // Renaming, as vanilla.
        String name = event.getName();
        if (name != null && !Util.isBlank(name))
        {
            if (!name.equals(left.getHoverName().getString()))
            {
                renameCost = 1;
                cost += renameCost;
                result.setHoverName(Component.literal(name));
            }
        }
        else if (left.hasCustomHoverName())
        {
            renameCost = 1;
            cost += renameCost;
            result.resetHoverName();
        }

        // Souls: both swords' souls together, up to the result's capacity.
        EnchantmentHelper.setEnchantments(enchantments, result);
        boolean soulsChanged = mergeSouls(result, left, right);

        // Nothing would change: no result (the user's rule; vanilla would still let you pay for nothing).
        boolean changed = !enchantments.equals(leftEnchantments)
                || result.getDamageValue() != left.getDamageValue()
                || renameCost > 0
                || (SOULS_ALONE_ALLOW_COMBINE && soulsChanged);
        if (!changed || cost <= 0)
        {
            noResult(event, 0);
            return;
        }

        int total = baseCost + cost;
        if (renameCost == cost && renameCost > 0 && total >= TOO_EXPENSIVE)
        {
            total = TOO_EXPENSIVE - 1; // vanilla: a rename alone is never too expensive
        }
        if (total >= TOO_EXPENSIVE && !creative)
        {
            noResult(event, total); // shows "Too Expensive!"
            return;
        }

        // Prior work penalty for next time, as vanilla.
        int repairCost = Math.max(result.getBaseRepairCost(), right.getBaseRepairCost());
        if (renameCost != cost || renameCost == 0)
        {
            repairCost = AnvilMenu.calculateIncreasedRepairCost(repairCost);
        }
        result.setRepairCost(repairCost);
        if (necromancyLevel(result) > 0)
        {
            NecromancyScheme.mark(result); // it's in the five-level numbering, never move it up
        }

        event.setOutput(result);
        event.setCost(total);
        event.setMaterialCost(0); // the whole right sword is used up
    }

    /**
     * Sets the result's souls to the souls of both swords together (only swords with Necromancy count), capped at
     * the result's capacity. An infinite (level V) result stores no souls. Returns true if the result's count changed.
     */
    private static boolean mergeSouls(ItemStack result, ItemStack left, ItemStack right)
    {
        if (necromancyLevel(result) <= 0)
        {
            return false;
        }
        int before = SoulStorage.getSouls(result);
        if (SoulStorage.isInfinite(result))
        {
            result.removeTagKey(SoulStorage.SOULS_TAG);
            return false;
        }
        int souls = soulsIfNecromancy(left) + soulsIfNecromancy(right);
        int after = Math.min(souls, SoulStorage.capacity(result));
        if (after > 0 || result.getTag() != null && result.getTag().contains(SoulStorage.SOULS_TAG))
        {
            SoulStorage.setSouls(result, after);
        }
        return after != before;
    }

    private static int soulsIfNecromancy(ItemStack stack)
    {
        return necromancyLevel(stack) > 0 ? SoulStorage.getSouls(stack) : 0;
    }

    /** Vanilla's anvil cost per enchantment level, by rarity (from another item, not a book). */
    private static int costPerLevel(Enchantment enchantment)
    {
        return switch (enchantment.getRarity())
        {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case VERY_RARE -> 8;
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Deathforged
    // ---------------------------------------------------------------------------------------------

    /** Deathforged: the VI+ lock and the V cap (only when both items carry Deathforged). */
    private static boolean blockDeathforged(ItemStack left, ItemStack right)
    {
        Enchantment deathforged = ModEnchantments.DEATHFORGED.get();
        int leftLevel = levelOf(left, deathforged);
        int rightLevel = levelOf(right, deathforged);
        if (leftLevel <= 0 || rightLevel <= 0)
        {
            // Only one side (or neither) has Deathforged: a plain apply, which always works.
            return false;
        }
        if (leftLevel >= DEATHFORGED_NO_COMBINE_FROM || rightLevel >= DEATHFORGED_NO_COMBINE_FROM)
        {
            return true;
        }
        // Equal levels would go up by one; stop that from passing the cap.
        return leftLevel == rightLevel && leftLevel + 1 > DEATHFORGED_COMBINE_CAP;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static int necromancyLevel(ItemStack stack)
    {
        return levelOf(stack, ModEnchantments.NECROMANCY.get());
    }

    /** This enchantment's level on the item (0 if it doesn't have it). Works for enchanted books too. */
    private static int levelOf(ItemStack stack, Enchantment enchantment)
    {
        return EnchantmentHelper.getEnchantments(stack).getOrDefault(enchantment, 0);
    }
}
