package com.reg21meme.dross.boss.rage;

import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * A boss with followers (minions it summons), for the Rage Wand: when the boss is raged, its followers that are alive
 * join its side (they get the Rage too and fight its enemies, never it or each other), and so does every follower it
 * summons while it's raged (its summon code calls {@link Rage#recruit}). The Cinder Colossus (its stone and lava golems)
 * and the Rat King (his rats) are leaders.
 */
public interface RageLeader
{
    /** Its followers that are alive now (they join its side when it's raged). Server side. */
    List<? extends Mob> rageFollowers();

    /** Called when the wand rages it (the Rage is already on): a sleeping boss wakes up, for example. Server side. */
    default void onRaged()
    {
    }
}
