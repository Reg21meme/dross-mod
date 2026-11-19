package com.reg21meme.dross.boss.huntsman;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Making a fighting Huntsman and his Alpha Moose (a spawn egg, {@code /dross boss summon huntsman}, the fight broken
 * off with one of them dead): him in its saddle, linked as partners ({@link HuntFight}), calm until the first hit.
 */
public final class HuntsmanPair
{
    private HuntsmanPair() {}

    /** A new pair standing at {@code feet}, facing {@code yaw}: returns him (in the saddle), or null if they couldn't
     * be made. */
    @Nullable
    public static Huntsman spawn(ServerLevel level, Vec3 feet, float yaw)
    {
        AlphaMoose moose = newMoose(level, feet, yaw);
        if (moose == null)
        {
            return null;
        }
        Huntsman h = newHuntsman(level, feet, yaw);
        if (h == null)
        {
            moose.discard();
            return null;
        }
        HuntFight.link(h, moose);
        h.startRiding(moose, true);
        return h;
    }

    /**
     * A village's pair asleep at home (until the Oracle's quest of a player near reaches him: {@code village/HuntsmanAsleep}):
     * the Alpha Moose lying in its stall at {@code stall}, facing {@code stallYaw}, the Huntsman on his bed at {@code bed}
     * (his middle; his feet the way {@code bedYaw} faces), partners, not in the saddle. Null if either couldn't be made.
     */
    @Nullable
    public static Huntsman spawnAsleep(ServerLevel level, Vec3 bed, float bedYaw, Vec3 stall, float stallYaw)
    {
        AlphaMoose moose = newMoose(level, stall, stallYaw);
        if (moose == null)
        {
            return null;
        }
        Huntsman h = newHuntsman(level, bed, bedYaw);
        if (h == null)
        {
            moose.discard();
            return null;
        }
        HuntFight.link(h, moose);
        moose.setAsleep(true);
        h.setAsleep(true);
        moose.moveTo(stall.x, stall.y, stall.z, stallYaw, 0.0F);       // (its box changed: back on its spot)
        h.moveTo(bed.x, bed.y, bed.z, bedYaw, 0.0F);
        return h;
    }

    /**
     * Wakes a sleeping pair onto their patrol: both stand at {@code at} facing {@code yaw}, him in the saddle. False (and
     * nothing done) if his moose isn't there to wake with him.
     */
    public static boolean wake(Huntsman h, Vec3 at, float yaw)
    {
        if (!(HuntFight.partner(h) instanceof AlphaMoose moose) || !moose.isAlive())
        {
            return false;
        }
        moose.setAsleep(false);
        h.setAsleep(false);
        moose.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        moose.setYBodyRot(yaw);
        moose.setYHeadRot(yaw);
        h.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        h.startRiding(moose, true);
        return true;
    }

    /** A new Alpha Moose in the world (not yet anyone's partner). */
    @Nullable
    static AlphaMoose newMoose(ServerLevel level, Vec3 at, float yaw)
    {
        AlphaMoose moose = HuntFight.mooseType().create(level);
        if (moose == null)
        {
            return null;
        }
        moose.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        moose.setYBodyRot(yaw);
        moose.setYHeadRot(yaw);
        level.addFreshEntity(moose);
        return moose;
    }

    /** A new Huntsman in the world (not yet in a saddle). */
    @Nullable
    static Huntsman newHuntsman(ServerLevel level, Vec3 at, float yaw)
    {
        Huntsman h = HuntFight.huntsmanType().create(level);
        if (h == null)
        {
            return null;
        }
        h.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        h.setYBodyRot(yaw);
        h.setYHeadRot(yaw);
        level.addFreshEntity(h);
        return h;
    }

    /** A Huntsman with no moose gets a new one under him. */
    static void mountNew(Huntsman h, ServerLevel level)
    {
        AlphaMoose moose = newMoose(level, h.position(), h.getYRot());
        if (moose != null)
        {
            HuntFight.link(h, moose);
            h.startRiding(moose, true);
        }
    }
}
