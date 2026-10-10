package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModCreativeTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keeps the Necromancy and Deathforged enchanted books in the creative "Dross" tab only.
 * Vanilla adds a book for every enchantment to its own tabs (all levels to Ingredients and search,
 * the top level of weapon enchantments to Combat), which showed every Dross book twice in search.
 * This removes any enchanted book carrying a Dross enchantment from every category tab except the Dross tab.
 * Search then finds each book once, through the Dross tab.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DrossBooksTabFilter
{
    private DrossBooksTabFilter() {}

    @SubscribeEvent
    public static void onBuildTabContents(BuildCreativeModeTabContentsEvent event)
    {
        // Only ordinary category tabs. The search tab is built by copying every other tab's search items
        // (the Dross tab's books included), so filtering it too would hide the books from search entirely.
        if (event.getTab().getType() != CreativeModeTab.Type.CATEGORY
                || event.getTabKey().equals(ModCreativeTabs.DROSS.getKey()))
        {
            return;
        }
        // Collect first, then remove, so the entries aren't changed while they're being read.
        List<ItemStack> drossBooks = new ArrayList<>();
        for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> entry : event.getEntries())
        {
            if (isDrossBook(entry.getKey()))
            {
                drossBooks.add(entry.getKey());
            }
        }
        for (ItemStack book : drossBooks)
        {
            event.getEntries().remove(book);
        }
    }

    /** True for an enchanted book that stores at least one enchantment from this mod. */
    private static boolean isDrossBook(ItemStack stack)
    {
        if (!stack.is(Items.ENCHANTED_BOOK))
        {
            return false;
        }
        for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet())
        {
            ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
            if (id != null && Dross.MODID.equals(id.getNamespace()))
            {
                return true;
            }
        }
        return false;
    }
}
