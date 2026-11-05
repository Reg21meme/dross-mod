package com.reg21meme.dross.quest;

import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.villager.RiftChapels;
import net.minecraft.core.BlockPos;
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
 *       Dross is pressing to break through again; an old villager, the writer's friend, has made his home in an old
 *       chapel at the edge of a village and is looking for someone strong enough to fight back; and roughly where that
 *       chapel is: the direction from world spawn, with coordinates rounded to {@value #LOCATION_ROUNDING} blocks.</li>
 *   <li>Which chapel: the world's one Rift Chapel, the trader's home (the villager area's {@link RiftChapels#home}, at
 *       the edge of a village the seed picks; on its own in the wilds if no village near the spawn had room, worded so).
 *       A letter comes out of its chest blank and is written the first time it's in a player's inventory (the chapel is
 *       known from the seed from the start). In a world with no chapel it says it can't tell where he is.</li>
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
    /** The location page of a letter written while the chapel wasn't known (or there was none). Written again once it's known. */
    private static final String UNKNOWN_KEY = KEY + "location_unknown";

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
        if (!hasPages(stack))
        {
            this.write(stack, player, false);
        }
        else if (player.tickCount % CHECK_INTERVAL == 0 && hasUnknownLocation(stack))
        {
            this.write(stack, player, true);
        }
        if (player.tickCount % CHECK_INTERVAL == 0)
        {
            QuestProgress.onLetterInInventory(player);
        }
    }

    /**
     * Writes the letter for the trader's chapel ({@link RiftChapels#homeIfReady}: known from the seed from the start; if
     * it isn't worked out yet, the letter stays blank a moment longer). {@code onlyIfKnown}: a letter that says it can't
     * tell where he is is only written again once there's a chapel to name.
     */
    private void write(ItemStack stack, ServerPlayer player, boolean onlyIfKnown)
    {
        ServerLevel overworld = player.server.overworld();
        Optional<RiftChapels.Home> home = RiftChapels.homeIfReady(overworld);
        if (home != null && (!onlyIfKnown || home.isPresent()))
        {
            setPages(stack, writePages(overworld, home));
        }
    }

    /**
     * True if this letter shows the "unknown location" page (written while the chapel wasn't known: it's written again
     * once it is). Checked by looking for that page's lang key in the stored pages.
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

    /**
     * The letter's pages for the trader's chapel: at the edge of a village ({@code page2_intro}, {@code location}), or on its
     * own in the wilds ({@code page2_intro_wild}, {@code location_wild}), or none ({@code location_unknown}).
     */
    static List<Component> writePages(ServerLevel overworld, Optional<RiftChapels.Home> home)
    {
        boolean village = home.map(RiftChapels.Home::inVillage).orElse(true);
        String suffix = village ? "" : "_wild";
        Component story = Component.translatable(KEY + "page1");
        Component intro = Component.translatable(KEY + "page2_intro" + suffix);
        Component friend = Component.translatable(KEY + "page3");
        if (home.isEmpty())
        {
            return List.of(story, intro, friend, Component.translatable(UNKNOWN_KEY));
        }
        BlockPos hut = home.get().chapel().traderSpot();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int roughX = roundRough(hut.getX());
        int roughZ = roundRough(hut.getZ());
        int dx = hut.getX() - spawn.getX();
        int dz = hut.getZ() - spawn.getZ();
        Component where;
        if (dx * dx + dz * dz < NEAR_SPAWN_DISTANCE * NEAR_SPAWN_DISTANCE)
        {
            where = Component.translatable(KEY + "location_near_spawn" + suffix, roughX, roughZ);
        }
        else
        {
            where = Component.translatable(KEY + "location" + suffix, Component.translatable(directionKey(dx, dz)), roughX, roughZ);
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
