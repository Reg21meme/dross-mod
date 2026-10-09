package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.DrossGuideBookItem;
import com.reg21meme.dross.quest.WeatheredLetterItem;
import com.reg21meme.dross.villager.DrossCompass;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs
{
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Dross.MODID);

    /** The "Dross" creative tab. Add new items to displayItems below. */
    public static final RegistryObject<CreativeModeTab> DROSS = CREATIVE_TABS.register("dross",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dross"))
                    .icon(() -> new ItemStack(ModItems.DROSS_PORTAL_FRAME.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.DROSS_PORTAL_FRAME.get());
                        // Portal area: the Rift Key (throw it into a frame to light the portal)
                        output.accept(ModItems.RIFT_KEY.get());
                        output.accept(ModItems.DROSS_TRADER_SPAWN_EGG.get());
                        // Villager area: test sword, and a Dross Compass (it gets its target once it's in your inventory)
                        output.accept(ModItems.ADMIN_SWORD.get());
                        output.accept(DrossCompass.createBlank());
                        // Enchantments area: a book for every level of Necromancy (I-IV) and Deathforged (I-X)
                        for (int level = 1; level <= ModEnchantments.NECROMANCY.get().getMaxLevel(); level++)
                        {
                            output.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ModEnchantments.NECROMANCY.get(), level)));
                        }
                        for (int level = 1; level <= ModEnchantments.DEATHFORGED.get().getMaxLevel(); level++)
                        {
                            output.accept(EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ModEnchantments.DEATHFORGED.get(), level)));
                        }
                        // Quest area: a Weathered Letter (gets its text once it's in your inventory) and the Dross Guide Book
                        output.accept(WeatheredLetterItem.createBlank());
                        output.accept(DrossGuideBookItem.create());
                    })
                    .build());

    private ModCreativeTabs() {}
}
