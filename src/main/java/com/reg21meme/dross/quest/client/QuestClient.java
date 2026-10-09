package com.reg21meme.dross.quest.client;

import com.reg21meme.dross.quest.ReadableItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Client-only quest code: opening the book screen for the Weathered Letter and the Dross Guide Book. */
public final class QuestClient
{
    private QuestClient() {}

    /** Opens the vanilla book screen showing the pages stored in this item. */
    public static void openBook(ItemStack stack)
    {
        Minecraft.getInstance().setScreen(new BookViewScreen(new ItemPages(stack)));
    }

    /** Reads the pages (JSON text components) from the item's tag. */
    private static final class ItemPages implements BookViewScreen.BookAccess
    {
        private final List<FormattedText> pages = new ArrayList<>();

        ItemPages(ItemStack stack)
        {
            CompoundTag tag = stack.getTag();
            if (tag != null)
            {
                ListTag list = tag.getList(ReadableItem.PAGES_TAG, Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++)
                {
                    pages.add(parse(list.getString(i)));
                }
            }
            if (pages.isEmpty())
            {
                // Not written yet (it gets its text a moment after it lands in an inventory).
                pages.add(Component.translatable("book.dross.blank"));
            }
        }

        private static FormattedText parse(String json)
        {
            try
            {
                Component component = Component.Serializer.fromJson(json);
                if (component != null)
                {
                    return component;
                }
            }
            catch (Exception ignored)
            {
                // Not JSON: show it as plain text below.
            }
            return FormattedText.of(json);
        }

        @Override
        public int getPageCount()
        {
            return pages.size();
        }

        @Override
        public FormattedText getPageRaw(int index)
        {
            return pages.get(index);
        }
    }
}
