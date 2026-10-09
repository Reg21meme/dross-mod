package com.reg21meme.dross.quest;

import com.reg21meme.dross.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dross Guide Book ({@code dross:dross_guide_book}): readable like a written book, explaining the Dross.
 * Players get it on their first arrival ({@link QuestProgress#giveGuideBookOnFirstArrival}).
 * The text is placeholder text in {@code en_us.json}, keys {@code book.dross.dross_guide_book.page1}
 * to {@code pageN} (N = {@link #PAGE_COUNT}).
 */
public class DrossGuideBookItem extends ReadableItem
{
    /** How many pages the book has. Add a lang key for each new page when raising this. */
    public static final int PAGE_COUNT = 5;

    public DrossGuideBookItem()
    {
        super(new Properties().stacksTo(1));
    }

    /** A Dross Guide Book with its pages written in. */
    public static ItemStack create()
    {
        ItemStack stack = new ItemStack(ModItems.DROSS_GUIDE_BOOK.get());
        setPages(stack, pages());
        return stack;
    }

    /** A book without pages (for example from {@code /give}) gets them once it's in a player's inventory. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected)
    {
        if (!level.isClientSide && !hasPages(stack))
        {
            setPages(stack, pages());
        }
    }

    private static List<Component> pages()
    {
        List<Component> pages = new ArrayList<>();
        for (int i = 1; i <= PAGE_COUNT; i++)
        {
            pages.add(Component.translatable("book.dross.dross_guide_book.page" + i));
        }
        return pages;
    }
}
