package com.reg21meme.dross.portal;

import com.reg21meme.dross.DrossColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Rift Key ({@code dross:rift_key}): a crystal key with a blue heart.
 * <ul>
 *   <li>Throw it (Q) into the empty middle of a complete Dross portal frame to light the portal.
 *       The key is used up (see {@link DrossPortalActivation}).</li>
 *   <li>Doesn't stack (stack size 1).</li>
 *   <li>Fireproof: a dropped key survives fire and lava, like netherite.</li>
 *   <li>A dropped key never despawns.</li>
 * </ul>
 * The trader (villager area) forges it and gives it out, through {@code ModItems.RIFT_KEY}.
 */
public class RiftKeyItem extends Item
{
    public RiftKeyItem()
    {
        super(new Item.Properties().stacksTo(1).fireResistant());
    }

    /**
     * How many ticks a dropped key lasts before it despawns. Normal items last 6000 ticks (5 minutes);
     * this is so long (over 3 years of play) that the key effectively never despawns.
     */
    @Override
    public int getEntityLifespan(ItemStack stack, Level level)
    {
        return Integer.MAX_VALUE;
    }

    /** The item name is shown in electric blue. */
    @Override
    public Component getName(ItemStack stack)
    {
        return super.getName(stack).copy().withStyle(style -> style.withColor(TextColor.fromRgb(DrossColors.RIFT_KEY_NAME)));
    }
}
