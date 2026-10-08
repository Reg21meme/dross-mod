package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEnchantments;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Two items with the same level of Necromancy, or the same level of Deathforged, can't be combined at an anvil
 * (book + book or sword + book): the anvil shows no result. A higher level onto a lower one works normally.
 * (Necromancy + Smite is blocked by {@link NecromancyEnchantment#checkCompatibility}.)
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class AnvilRules
{
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
        if (sameLevel(left, right, ModEnchantments.NECROMANCY.get()) || sameLevel(left, right, ModEnchantments.DEATHFORGED.get()))
        {
            // Cancelling stops the anvil, but it would leave an old result in the slot, so clear that too.
            event.setCanceled(true);
            if (event.getPlayer() != null && event.getPlayer().containerMenu instanceof AnvilMenu anvil)
            {
                anvil.getSlot(AnvilMenu.RESULT_SLOT).set(ItemStack.EMPTY);
                anvil.setMaximumCost(0);
            }
        }
    }

    /** True if both items have this enchantment at the same level. Works for enchanted books too. */
    private static boolean sameLevel(ItemStack left, ItemStack right, Enchantment enchantment)
    {
        int leftLevel = EnchantmentHelper.getEnchantments(left).getOrDefault(enchantment, 0);
        int rightLevel = EnchantmentHelper.getEnchantments(right).getOrDefault(enchantment, 0);
        return leftLevel > 0 && leftLevel == rightLevel;
    }
}
