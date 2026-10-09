package com.reg21meme.dross.quest;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.quest.client.QuestClient;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An item you can read like a written book: right-click opens the vanilla book screen.
 * Its pages are stored in the item's tag under {@value #PAGES_TAG}, as a list of JSON text components
 * (the same format as a written book's pages). The pages use lang keys, so the text can be edited in
 * {@code en_us.json} without touching code.
 */
public abstract class ReadableItem extends Item
{
    /** The item tag that holds the pages. */
    public static final String PAGES_TAG = "pages";

    protected ReadableItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide)
        {
            // The book screen only exists on the client; this keeps it out of the server.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestClient.openBook(stack));
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.dross.readable").withStyle(DrossColors.READABLE_TOOLTIP));
    }

    /** True if the stack has pages written in it. */
    public static boolean hasPages(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(PAGES_TAG, Tag.TAG_LIST) && !tag.getList(PAGES_TAG, Tag.TAG_STRING).isEmpty();
    }

    /** Writes these pages into the stack (replacing any old ones). */
    public static void setPages(ItemStack stack, List<Component> pages)
    {
        ListTag list = new ListTag();
        for (Component page : pages)
        {
            list.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        }
        stack.getOrCreateTag().put(PAGES_TAG, list);
    }
}
