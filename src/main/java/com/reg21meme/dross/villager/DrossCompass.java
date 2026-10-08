package com.reg21meme.dross.villager;

import com.reg21meme.dross.world.PortalSite;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Helpers for the Dross Compass: a vanilla compass with lodestone-style data pointing at the portal site
 * in the Overworld. Lodestone tracking is off, so it keeps pointing there without a lodestone.
 * A hidden marker tag lets {@link DrossCompassTracker} find these compasses in a player's inventory.
 */
public final class DrossCompass
{
    /** Hidden boolean tag that marks a compass as a Dross Compass. */
    public static final String TAG_MARKER = "DrossCompass";

    private DrossCompass() {}

    /** A Dross Compass with no target yet (the tracker gives it one once it's in a player's inventory). */
    public static ItemStack createBlank()
    {
        ItemStack compass = new ItemStack(Items.COMPASS);
        compass.getOrCreateTag().putBoolean(TAG_MARKER, true);
        compass.setHoverName(Component.translatable("item.dross.dross_compass").withStyle(style -> style.withItalic(false)));
        return compass;
    }

    /** A Dross Compass already pointing at the portal site (what the trader sells). */
    public static ItemStack create(ServerLevel level)
    {
        ItemStack compass = createBlank();
        applyTarget(compass, level);
        return compass;
    }

    public static boolean isDrossCompass(ItemStack stack)
    {
        return stack.is(Items.COMPASS) && stack.hasTag() && stack.getTag().getBoolean(TAG_MARKER);
    }

    /** Aims at the frame's opening (the frame's bottom corner + 1 in X and Y). */
    private static BlockPos target(ServerLevel level)
    {
        return PortalSite.getFramePos(level.getServer().overworld()).offset(1, 1, 0);
    }

    /**
     * Writes the portal site target into the compass. Only changes the NBT if something is
     * missing or different. Returns true if it changed anything.
     */
    public static boolean applyTarget(ItemStack compass, ServerLevel level)
    {
        BlockPos target = target(level);
        CompoundTag tag = compass.getOrCreateTag();
        boolean upToDate = tag.contains(CompassItem.TAG_LODESTONE_POS, 10)
                && NbtUtils.readBlockPos(tag.getCompound(CompassItem.TAG_LODESTONE_POS)).equals(target)
                && tag.contains(CompassItem.TAG_LODESTONE_DIMENSION)
                && Level.RESOURCE_KEY_CODEC.parse(NbtOps.INSTANCE, tag.get(CompassItem.TAG_LODESTONE_DIMENSION)).result()
                        .filter(Level.OVERWORLD::equals).isPresent()
                && tag.contains(CompassItem.TAG_LODESTONE_TRACKED)
                && !tag.getBoolean(CompassItem.TAG_LODESTONE_TRACKED);
        if (upToDate)
        {
            return false;
        }
        tag.put(CompassItem.TAG_LODESTONE_POS, NbtUtils.writeBlockPos(target));
        Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, Level.OVERWORLD).result()
                .ifPresent(dimension -> tag.put(CompassItem.TAG_LODESTONE_DIMENSION, dimension));
        tag.putBoolean(CompassItem.TAG_LODESTONE_TRACKED, false);
        return true;
    }
}
