package com.reg21meme.dross.enchant.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.enchant.SoulStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shows "Souls: X / Y" (or "Souls: ∞" at Necromancy IV) on any item with Necromancy.
 * The count is read from the item's own tag, which the client already has, so no packets are needed.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class SoulTooltip
{
    private SoulTooltip() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event)
    {
        ItemStack stack = event.getItemStack();
        if (SoulStorage.necromancyLevel(stack) <= 0)
        {
            return; // also skips enchanted books, which store their enchantments differently
        }
        Component line = SoulStorage.isInfinite(stack)
                ? Component.translatable("tooltip.dross.souls_infinite")
                : Component.translatable("tooltip.dross.souls", SoulStorage.getSouls(stack), SoulStorage.capacity(stack));
        event.getToolTip().add(line.copy().withStyle(ChatFormatting.GOLD));
    }
}
