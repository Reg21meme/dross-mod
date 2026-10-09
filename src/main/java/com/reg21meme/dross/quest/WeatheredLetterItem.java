package com.reg21meme.dross.quest;

import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.villager.TraderSpawnData;
import net.minecraft.core.BlockPos;
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
 *   <li>It tells the reader that an old villager near a village close to spawn is seeking someone strong,
 *       and roughly where his hut is: the direction from world spawn, with coordinates rounded to
 *       {@value #LOCATION_ROUNDING} blocks. If his hut isn't built yet, the wording is vaguer.</li>
 *   <li>The text is written when the letter is made (in the chest). A letter without text (for example from
 *       the creative tab) gets its text as soon as it's in a player's inventory.</li>
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
            QuestProgress.onLetterInInventory(player);
        }
    }

    /** The letter's pages, using where the trader's hut is right now (read from the villager area's saved data). */
    private static List<Component> writePages(MinecraftServer server)
    {
        Component intro = Component.translatable("book.dross.weathered_letter.page1");
        ServerLevel overworld = server.overworld();
        TraderSpawnData trader = TraderSpawnData.get(overworld);
        if (!trader.hasSpawned())
        {
            // His hut isn't built yet: we don't know where he is.
            return List.of(intro, Component.translatable("book.dross.weathered_letter.page2_unknown"));
        }

        BlockPos hut = trader.getPos();
        BlockPos spawn = overworld.getSharedSpawnPos();
        int roughX = roundRough(hut.getX());
        int roughZ = roundRough(hut.getZ());
        int dx = hut.getX() - spawn.getX();
        int dz = hut.getZ() - spawn.getZ();

        Component where;
        if (dx * dx + dz * dz < NEAR_SPAWN_DISTANCE * NEAR_SPAWN_DISTANCE)
        {
            where = Component.translatable("book.dross.weathered_letter.page2_near_spawn", roughX, roughZ);
        }
        else
        {
            where = Component.translatable("book.dross.weathered_letter.page2",
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
