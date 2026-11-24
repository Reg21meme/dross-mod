package com.reg21meme.dross.portal;

import com.reg21meme.dross.DrossColors;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Portal Orb ({@code dross:portal_orb}): a glass ball with a little Dross portal swirling in it (the user's pick of
 * the portal item's designs, O3; PRE_DROSS_PLAN.md). The Dross Trader makes it from the souls of three knight ruins.
 * <ul>
 *   <li>Throw it (Q) into the empty middle of a complete Dross portal frame to light the portal.
 *       The orb is used up (see {@link DrossPortalActivation}).</li>
 *   <li>Doesn't stack (stack size 1).</li>
 *   <li>Fireproof: a dropped orb survives fire and lava, like netherite.</li>
 *   <li>A dropped orb never despawns.</li>
 * </ul>
 * The trader's quest (the quest area's {@code quest.trader}) gives it out, through {@code ModItems.PORTAL_ORB}.
 */
public class PortalOrbItem extends Item
{
    public PortalOrbItem()
    {
        super(new Item.Properties().stacksTo(1).fireResistant());
    }

    /**
     * How many ticks a dropped orb lasts before it despawns. Normal items last 6000 ticks (5 minutes);
     * this is so long (over 3 years of play) that the orb effectively never despawns.
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
        return super.getName(stack).copy().withStyle(style -> style.withColor(TextColor.fromRgb(DrossColors.PORTAL_ORB_NAME)));
    }
}
