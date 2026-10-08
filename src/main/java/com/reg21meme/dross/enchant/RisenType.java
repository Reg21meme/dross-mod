package com.reg21meme.dross.enchant;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * The kinds of undead Necromancy can raise, the Necromancy level that unlocks each, and their soul cost.
 * <ul>
 *   <li>I: zombie, skeleton</li>
 *   <li>II: + husk, stray, drowned</li>
 *   <li>III (and IV): + chicken jockey, spider jockey, skeleton horseman</li>
 * </ul>
 * A jockey/horseman takes 1 "alive" slot (the rider) but costs {@link NecromancyEnchantment#SOUL_COST_MOUNTED} souls.
 */
public enum RisenType
{
    ZOMBIE(1, false),
    SKELETON(1, false),
    HUSK(2, false),
    STRAY(2, false),
    DROWNED(2, false),
    /** A baby zombie riding a chicken. */
    CHICKEN_JOCKEY(3, true),
    /** A skeleton riding a spider. */
    SPIDER_JOCKEY(3, true),
    /** A skeleton riding a skeleton horse. */
    SKELETON_HORSEMAN(3, true);

    /** Necromancy level that unlocks this type. */
    public final int unlockLevel;
    /** True for jockeys and horsemen (rider + mount). */
    public final boolean mounted;

    RisenType(int unlockLevel, boolean mounted)
    {
        this.unlockLevel = unlockLevel;
        this.mounted = mounted;
    }

    public int soulCost()
    {
        return this.mounted ? NecromancyEnchantment.SOUL_COST_MOUNTED : NecromancyEnchantment.SOUL_COST_NORMAL;
    }

    /** A random type unlocked at this Necromancy level. With {@code normalOnly}, never a jockey/horseman. */
    public static RisenType random(int necromancyLevel, boolean normalOnly, RandomSource random)
    {
        List<RisenType> choices = new ArrayList<>();
        for (RisenType type : values())
        {
            if (type.unlockLevel <= necromancyLevel && !(normalOnly && type.mounted))
            {
                choices.add(type);
            }
        }
        return choices.get(random.nextInt(choices.size()));
    }
}
