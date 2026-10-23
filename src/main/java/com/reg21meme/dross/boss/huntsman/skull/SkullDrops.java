package com.reg21meme.dross.boss.huntsman.skull;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Who drops a mounted skull (the user's answer, October 22, 2026: "drops"): the Alpha Moose always (every time it dies,
 * so a fight broken off and won again gives another); a grown bull moose or a grown Rimehorn sometimes, only when a
 * player killed it (like a wither skeleton's skull), more often with Looting.
 */
public final class SkullDrops
{
    /** A bull moose's or a Rimehorn's chance, and how much each level of Looting adds. */
    public static final float CHANCE = 0.10F;
    public static final float CHANCE_PER_LOOTING = 0.02F;

    private SkullDrops() {}

    /** The Alpha Moose's: always. */
    public static void always(LivingEntity body, Item skull)
    {
        body.spawnAtLocation(new ItemStack(skull));
    }

    /** A bull moose's or a Rimehorn's, rolled. */
    public static void sometimes(LivingEntity body, Item skull, int looting, boolean killedByPlayer)
    {
        if (killedByPlayer && body.getRandom().nextFloat() < CHANCE + CHANCE_PER_LOOTING * looting)
        {
            body.spawnAtLocation(new ItemStack(skull));
        }
    }
}
