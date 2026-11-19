package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;

/**
 * The arrows the Huntsman looses in his fight, from the saddle and on foot, and the arrow rain's ({@code dross:ice_arrow}):
 * a plain arrow in every way but its look, a head of the models' clear ice and ice-blue fletchings, like the arrows his
 * model holds (the user, November 19, 2026: "make the tip of the hunter's arrows ... that nice clear ice"; drawn by
 * {@code client/IceArrowRenderer} from {@code tools/huntsman/ice_arrow.py}'s texture). Being an {@link Arrow}, everything
 * that looks for his arrows (the rain's steering, its marks, the tests) finds it as before.
 */
public class IceArrow extends Arrow
{
    public IceArrow(EntityType<? extends IceArrow> type, Level level)
    {
        super(type, level);
    }

    /** One at {@code x, y, z}, as {@code new Arrow(level, x, y, z)} makes a plain one. */
    public static IceArrow at(Level level, double x, double y, double z)
    {
        IceArrow arrow = new IceArrow(ModEntities.ICE_ARROW.get(), level);
        arrow.setPos(x, y, z);
        return arrow;
    }
}
