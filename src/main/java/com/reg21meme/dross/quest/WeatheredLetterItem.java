package com.reg21meme.dross.quest;

import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.villager.RiftChapels;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * The Weathered Letter ({@code dross:weathered_letter}): found in Nether Fortress and Bastion chests
 * ({@link WeatheredLetterLootModifier}). Readable like a written book.
 * <ul>
 *   <li>Four pages: blue portals from the Dross have been spilling into the world; "we" closed them, but the
 *       Dross is pressing to break through again; an old villager, the writer's friend, keeps watch in an old chapel in
 *       the wilds and is looking for someone strong enough to fight back; and roughly where that chapel is: the
 *       direction from world spawn, with coordinates rounded to {@value #LOCATION_ROUNDING} blocks.</li>
 *   <li>Which chapel: the nearest Rift Chapel (the villager area's {@link RiftChapels}; a world has many, each with its
 *       trader) to where the letter is first held: there in the Overworld, the Overworld's place for it in the Nether
 *       (where the letters are found: its coordinates times 8), the world spawn anywhere else. A letter comes out of its
 *       chest blank and is written then, once (the search runs off the server thread, so it stays blank for that moment),
 *       and never again. With no chapel within {@link RiftChapels#SEARCH_RADIUS} blocks (or none in the world) it says it
 *       can't tell where he is.</li>
 *   <li>Having one in your inventory grants "A Weathered Letter" (and silently unlocks the Dross advancement tab).</li>
 * </ul>
 * All wording is in {@code en_us.json} under {@code book.dross.weathered_letter.*} and {@code book.dross.direction.*}.
 */
public class WeatheredLetterItem extends ReadableItem
{
    /** Hut coordinates in the letter are rounded to this many blocks (a rough location, not an exact one). */
    public static final int LOCATION_ROUNDING = 50;
    /** If the hut is closer than this to world spawn, the letter says "near where the world began" instead of a direction. */
    public static final int NEAR_SPAWN_DISTANCE = 32;
    /** How often (in ticks) a letter in an inventory checks the advancements (20 ticks = 1 second). */
    private static final int CHECK_INTERVAL = 20;

    /** Start of every lang key for the letter's pages. */
    private static final String KEY = "book.dross.weathered_letter.";
    /** The location page an old world's letter used while his hut wasn't built yet. Letters with it are written again. */
    private static final String UNKNOWN_KEY = KEY + "location_unknown";
    /** Where the letter was first held (the Overworld's X and Z for it), so it names the nearest chapel to there. */
    private static final String FOUND_AT_TAG = "DrossFoundAt";

    /** Lang keys for the 8 directions, starting at north and going clockwise. North is -Z, east is +X. */
    private static final String[] DIRECTION_KEYS = {
            "book.dross.direction.north", "book.dross.direction.north_east",
            "book.dross.direction.east", "book.dross.direction.south_east",
            "book.dross.direction.south", "book.dross.direction.south_west",
            "book.dross.direction.west", "book.dross.direction.north_west"};

    public WeatheredLetterItem()
    {
        super(new Properties().stacksTo(16));
    }

    /** A letter without text (a chest's, the creative tab's). It gets its text once it's in a player's inventory. */
    public static ItemStack createBlank()
    {
        return new ItemStack(ModItems.WEATHERED_LETTER.get());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected)
    {
        if (level.isClientSide || !(entity instanceof ServerPlayer player))
        {
            return;
        }
        if (!hasPages(stack) || hasUnknownLocation(stack))
        {
            this.writeWhenFound(stack, player);
        }
        if (player.tickCount % CHECK_INTERVAL == 0)
        {
            QuestProgress.onLetterInInventory(player);
        }
    }

    /**
     * Writes the letter for where it was first held: the nearest Rift Chapel to that spot ({@link RiftChapels#nearestIfReady},
     * off the server thread; the letter stays blank for the moment that takes). The spot is remembered on the letter the
     * first tick, so it's written for where it was found, even if its holder walks on meanwhile.
     */
    private void writeWhenFound(ItemStack stack, ServerPlayer player)
    {
        ServerLevel overworld = player.server.overworld();
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(FOUND_AT_TAG, Tag.TAG_INT_ARRAY) || tag.getIntArray(FOUND_AT_TAG).length != 2)
        {
            BlockPos from = RiftChapels.searchOrigin(overworld, player.level(), player.blockPosition());
            tag.putIntArray(FOUND_AT_TAG, new int[] {from.getX(), from.getZ()});
        }
        int[] at = tag.getIntArray(FOUND_AT_TAG);
        Optional<RiftChapels.Chapel> nearest = RiftChapels.nearestIfReady(overworld, new BlockPos(at[0], 0, at[1]));
        if (nearest != null)
        {
            setPages(stack, writePages(overworld, nearest));
        }
    }

    /**
     * True if this letter shows the "unknown location" page (an old world's letter written before his hut was built: it
     * is written again). Checked by looking for that page's lang key in the stored pages.
     */
    private static boolean hasUnknownLocation(ItemStack stack)
    {
        if (!hasPages(stack))
        {
            return false;
        }
        ListTag pages = stack.getTag().getList(PAGES_TAG, Tag.TAG_STRING);
        for (int i = 0; i < pages.size(); i++)
        {
            if (pages.getString(i).contains(UNKNOWN_KEY))
            {
                return true;
            }
        }
        return false;
    }

    /** The letter's pages for the chapel it names (the nearest to where it was found), or for none within reach. */
    static List<Component> writePages(ServerLevel overworld, Optional<RiftChapels.Chapel> nearest)
    {
        Component story = Component.translatable(KEY + "page1");
        Component intro = Component.translatable(KEY + "page2_intro_chapel");
        Component friend = Component.translatable(KEY + "page3");
        if (nearest.isEmpty())
        {
            return List.of(story, intro, friend, Component.translatable(KEY + "location_none"));
        }
        BlockPos hut = nearest.get().traderSpot();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int roughX = roundRough(hut.getX());
        int roughZ = roundRough(hut.getZ());
        int dx = hut.getX() - spawn.getX();
        int dz = hut.getZ() - spawn.getZ();
        Component where;
        if (dx * dx + dz * dz < NEAR_SPAWN_DISTANCE * NEAR_SPAWN_DISTANCE)
        {
            where = Component.translatable(KEY + "location_near_spawn_wild", roughX, roughZ);
        }
        else
        {
            where = Component.translatable(KEY + "location_wild", Component.translatable(directionKey(dx, dz)), roughX, roughZ);
        }
        return List.of(story, intro, friend, where);
    }

    /** Rounds a coordinate to the nearest {@value #LOCATION_ROUNDING}. */
    private static int roundRough(int coordinate)
    {
        return (int) Math.round(coordinate / (double) LOCATION_ROUNDING) * LOCATION_ROUNDING;
    }

    /** One of the 8 directions (north, north-east, ...) for this offset from spawn. */
    private static String directionKey(int dx, int dz)
    {
        // 0 degrees = north (-Z), 90 = east (+X), 180 = south, -90 = west.
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int index = Math.floorMod((int) Math.round(degrees / 45.0), 8);
        return DIRECTION_KEYS[index];
    }
}
