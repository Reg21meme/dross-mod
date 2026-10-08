package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
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
                        output.accept(ModItems.DROSS_TRADER_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
