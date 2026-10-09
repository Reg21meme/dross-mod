package com.reg21meme.dross.enchant;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEnchantments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Moves Necromancy items from the old four-level numbering (I-IV) to the five-level one (I-V), exactly once:
 * old I becomes II, old II becomes III, old III becomes IV, and the old admin level IV becomes V.
 * Souls stored on a moved sword stay (cut down to the new capacity if needed, which never actually happens).
 *
 * <h2>How old and new items are told apart</h2>
 * Every Necromancy item made or moved under the new numbering carries a marker in its item tag
 * ({@link #ITEM_TAG}). An item <b>with</b> the marker is never touched again. An item <b>without</b> it is
 * treated as old <i>only</i> in places that can only hold things from before the update:
 * <ul>
 *   <li>the inventory and ender chest of a player the first time they log in after the update
 *       (a per-player flag, kept through death, makes this happen once per player);</li>
 *   <li>a container (chest, barrel, shulker box, ender chest...) when a player opens it.</li>
 * </ul>
 * Everywhere else an unmarked Necromancy item is new: once a player has been moved over, any unmarked Necromancy
 * item in their inventory or on their mouse cursor came from {@code /give}, {@code /enchant}, an anvil, the trader
 * or the vanilla creative tabs after the update, so it just gets the marker (its level is left alone). That happens
 * every tick, so a new item is marked before it can be put in a chest.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class NecromancyScheme
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The current numbering: 2 = five levels (I-V). Items without a marker are from numbering 1 (I-IV). */
    public static final int CURRENT_SCHEME = 2;

    /** Item tag key holding the numbering an item's Necromancy level is in. */
    public static final String ITEM_TAG = "dross_necromancy_scheme";

    /** Key in the player's persisted data (kept through death): the numbering their inventory was last moved to. */
    private static final String PLAYER_TAG = "dross_necromancy_scheme";

    /**
     * Old level to new level (index 0 = old I ... index 3 = old IV): I to II, II to III, III to IV, IV (admin) to V.
     * Old levels above IV (only possible with commands) become V.
     */
    private static final int[] OLD_TO_NEW_LEVEL = {2, 3, 4, 5};

    /** The item tag lists enchantments are kept in: on items, and on enchanted books. */
    private static final String[] ENCHANTMENT_LISTS = {ItemStack.TAG_ENCH, EnchantedBookItem.TAG_STORED_ENCHANTMENTS};

    private NecromancyScheme() {}

    // ---------------------------------------------------------------------------------------------
    // Helpers for code that makes Necromancy items (the creative tab, the trader's shop)
    // ---------------------------------------------------------------------------------------------

    /** A Necromancy enchanted book at this level (in the five-level numbering), already marked as new. */
    public static ItemStack createBook(int level)
    {
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ModEnchantments.NECROMANCY.get(), level));
        mark(book);
        return book;
    }

    /** Marks an item as being in the current (five-level) numbering, so it is never moved up a level. */
    public static void mark(ItemStack stack)
    {
        stack.getOrCreateTag().putInt(ITEM_TAG, CURRENT_SCHEME);
    }

    public static boolean isMarked(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getInt(ITEM_TAG) >= CURRENT_SCHEME;
    }

    // ---------------------------------------------------------------------------------------------
    // Moving and marking single items
    // ---------------------------------------------------------------------------------------------

    /** The Necromancy entries in this item's enchantment lists (the real tags, so they can be edited). */
    private static List<CompoundTag> necromancyEntries(ItemStack stack)
    {
        List<CompoundTag> found = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag == null)
        {
            return found;
        }
        ResourceLocation necromancy = EnchantmentHelper.getEnchantmentId(ModEnchantments.NECROMANCY.get());
        for (String key : ENCHANTMENT_LISTS)
        {
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++)
            {
                CompoundTag entry = list.getCompound(i);
                if (necromancy != null && necromancy.equals(EnchantmentHelper.getEnchantmentId(entry)))
                {
                    found.add(entry);
                }
            }
        }
        return found;
    }

    /**
     * If this is an unmarked (old) Necromancy item, moves it up one level, keeps its souls (cut to the new
     * capacity) and marks it. Returns true if it changed.
     */
    public static boolean moveIfOld(ItemStack stack)
    {
        if (stack.isEmpty() || isMarked(stack))
        {
            return false;
        }
        List<CompoundTag> entries = necromancyEntries(stack);
        if (entries.isEmpty())
        {
            return false;
        }
        for (CompoundTag entry : entries)
        {
            int oldLevel = EnchantmentHelper.getEnchantmentLevel(entry);
            if (oldLevel >= 1)
            {
                EnchantmentHelper.setEnchantmentLevel(entry, OLD_TO_NEW_LEVEL[Math.min(oldLevel, OLD_TO_NEW_LEVEL.length) - 1]);
            }
        }
        mark(stack);
        // Souls stay. Every new capacity is at least the old one, but cut them down just in case.
        int capacity = SoulStorage.capacity(stack);
        if (capacity > 0 && SoulStorage.getSouls(stack) > capacity)
        {
            SoulStorage.setSouls(stack, capacity);
        }
        return true;
    }

    /** If this is an unmarked Necromancy item, marks it as new without changing its level. Returns true if it changed. */
    private static boolean markIfNew(ItemStack stack)
    {
        if (stack.isEmpty() || !stack.hasTag() || isMarked(stack) || necromancyEntries(stack).isEmpty())
        {
            return false;
        }
        mark(stack);
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Player inventories
    // ---------------------------------------------------------------------------------------------

    private static CompoundTag persisted(Player player)
    {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }

    private static boolean isMovedOver(Player player)
    {
        return persisted(player).getInt(PLAYER_TAG) >= CURRENT_SCHEME;
    }

    /**
     * The first time a player logs in after the update, move the old Necromancy items in their inventory and
     * ender chest up a level. Then remember (in data kept through death) that it's done.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || isMovedOver(player))
        {
            return;
        }
        int moved = moveAll(player.getInventory()) + moveAll(player.getEnderChestInventory());
        if (moved > 0)
        {
            LOGGER.info("Moved {} Necromancy item(s) of {} to the five-level numbering", moved, player.getGameProfile().getName());
        }
        CompoundTag persisted = persisted(player);
        persisted.putInt(PLAYER_TAG, CURRENT_SCHEME);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }

    private static int moveAll(Container container)
    {
        int moved = 0;
        for (int i = 0; i < container.getContainerSize(); i++)
        {
            if (moveIfOld(container.getItem(i)))
            {
                moved++;
            }
        }
        if (moved > 0)
        {
            container.setChanged();
        }
        return moved;
    }

    /** Every tick, new (unmarked) Necromancy items in a moved-over player's inventory or on their cursor get the marker. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !isMovedOver(player))
        {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++)
        {
            markIfNew(inventory.getItem(i));
        }
        markIfNew(player.containerMenu.getCarried());
    }

    // ---------------------------------------------------------------------------------------------
    // Containers
    // ---------------------------------------------------------------------------------------------

    /**
     * When a player opens a container, old Necromancy items in it move up a level. The player's own inventory
     * slots in the screen are skipped (those are handled above).
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        for (Slot slot : event.getContainer().slots)
        {
            if (slot.container instanceof Inventory || !slot.hasItem())
            {
                continue;
            }
            if (moveIfOld(slot.getItem()))
            {
                slot.setChanged(); // so the chest saves it; the screen picks up the change on the next tick
            }
        }
    }
}
