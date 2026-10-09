package com.reg21meme.dross.quest;

import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.villager.TraderSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Weathered Letter ({@code dross:weathered_letter}): found in Nether Fortress and Bastion chests
 * ({@link WeatheredLetterLootModifier}). Readable like a written book.
 * <ul>
 *   <li>It tells the reader that an old villager is seeking someone strong, and roughly where his hut is:
 *       the direction from world spawn, with coordinates rounded to {@value #LOCATION_ROUNDING} blocks.
 *       The wording depends on whether his hut is at the edge of a village or on its own in the wilds
 *       ({@link TraderSpawnData#isInVillage()}). If his hut isn't built yet, the wording is vaguer.</li>
 *   <li>The text is written when the letter is made (in the chest). A letter without text (for example from
 *       the creative tab) gets its text as soon as it's in a player's inventory. A letter written before his
 *       hut was built (the vague text) rewrites itself once, the next time it's in a player's inventory
 *       after he has spawned. Letters that already show a real location are never rewritten.</li>
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
    /** The page used while his hut isn't built yet. Letters with this page rewrite themselves once he has spawned. */
    private static final String UNKNOWN_KEY = KEY + "page2_unknown";

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

    /** A new letter with its text written for this world. */
    public static ItemStack create(MinecraftServer server)
    {
        ItemStack stack = new ItemStack(ModItems.WEATHERED_LETTER.get());
        setPages(stack, writePages(server));
        return stack;
    }

    /** A letter without text (the creative tab copy). It gets its text once it's in a player's inventory. */
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
        if (!hasPages(stack))
        {
            setPages(stack, writePages(player.server));
        }
        if (player.tickCount % CHECK_INTERVAL == 0)
        {
            // Written before his hut existed? Now that he has spawned, fill in the real location (once).
            if (hasUnknownLocation(stack) && TraderSpawnData.get(player.server.overworld()).hasSpawned())
            {
                setPages(stack, writePages(player.server));
            }
            QuestProgress.onLetterInInventory(player);
        }
    }

    /**
     * True if this letter was written before his hut was built (its text uses the "unknown location" page).
     * Checked by looking for that page's lang key in the stored pages, so it also works for letters
     * written by older versions of the mod.
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

    /** The letter's pages, using where the trader's hut is right now (read from the villager area's saved data). */
    private static List<Component> writePages(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        TraderSpawnData trader = TraderSpawnData.get(overworld);
        if (!trader.hasSpawned())
        {
            // His hut isn't built yet: we don't know where he is.
            return List.of(Component.translatable(KEY + "page1"), Component.translatable(UNKNOWN_KEY));
        }

        // At the edge of a village, or (no usable village was found) on its own in the wilds near spawn.
        String suffix = trader.isInVillage() ? "" : "_wild";
        Component intro = Component.translatable(KEY + "page1" + suffix);

        BlockPos hut = trader.getPos();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int roughX = roundRough(hut.getX());
        int roughZ = roundRough(hut.getZ());
        int dx = hut.getX() - spawn.getX();
        int dz = hut.getZ() - spawn.getZ();

        Component where;
        if (dx * dx + dz * dz < NEAR_SPAWN_DISTANCE * NEAR_SPAWN_DISTANCE)
        {
            where = Component.translatable(KEY + "page2_near_spawn" + suffix, roughX, roughZ);
        }
        else
        {
            where = Component.translatable(KEY + "page2" + suffix,
                    Component.translatable(directionKey(dx, dz)), roughX, roughZ);
        }
        return List.of(intro, where);
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
