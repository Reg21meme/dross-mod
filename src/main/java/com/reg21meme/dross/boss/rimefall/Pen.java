package com.reg21meme.dross.boss.rimefall;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Mob;

import javax.annotation.Nullable;

/**
 * A hunter village animal's pen (a Rimehorn of the Huntsman's corral, a moose of a paddock): it's tame only while it's
 * inside its fence (the user, October 23, 2026: tame are only "the Rimehorns that spawn within the villages' fences or
 * the ones you bred"; a herd that had got out of its pen was still passive). The village's template saves each pen
 * animal's fence line in the animal's own frame ({@link #TAG_FRAME}: two opposite corners, each so far ahead of it and
 * so far to its side, written by tools/huntsman/village_designs/warren.py); the template turns the animal's facing with
 * the village, so on its first tick in the world the animal finds the fence line again from where it stands and the way
 * it faces ({@link #TAG_BOX} from then on). Outside it, it's a wild animal for good. A calf born in the pen has its
 * mother's pen.
 */
final class Pen
{
    /** The fence line in the animal's own frame, until it's in the world: {ahead, side, ahead, side} (blocks). */
    static final String TAG_FRAME = "PenFrame";
    /** The fence line in the world: {min x, min z, max x, max z} (its posts' middles). */
    static final String TAG_BOX = "PenBox";
    /** It looks this often (ticks) whether it's still inside. */
    private static final int CHECK_TICKS = 20;
    /** It counts as inside up to this far past the fence line's middle (blocks: the fence's outer face). */
    private static final double SLACK = 0.5D;

    @Nullable
    private float[] frame;
    @Nullable
    private double[] box;

    /** Whether it knows its pen (in the world or still in its frame). */
    boolean known()
    {
        return this.box != null || this.frame != null;
    }

    /**
     * Called every server tick while it's penned: false once it's outside its pen (it then stops being penned). An
     * animal that never knew its pen (made penned by hand, a test) stays penned.
     */
    boolean stillInside(Mob mob)
    {
        if (this.box == null && this.frame != null)
        {
            this.box = place(mob, this.frame);
            this.frame = null;
        }
        if (this.box == null || mob.tickCount % CHECK_TICKS != 0)
        {
            return true;
        }
        double x = mob.getX();
        double z = mob.getZ();
        return x >= this.box[0] - SLACK && x <= this.box[2] + SLACK && z >= this.box[1] - SLACK && z <= this.box[3] + SLACK;
    }

    /** It's out: it forgets its pen. */
    void forget()
    {
        this.frame = null;
        this.box = null;
    }

    /** A calf born in the pen: the same pen. */
    void copyTo(Pen other)
    {
        other.frame = this.frame == null ? null : this.frame.clone();
        other.box = this.box == null ? null : this.box.clone();
    }

    /** The fence line in the world from where {@code mob} stands and the way it faces. */
    private static double[] place(Mob mob, float[] frame)
    {
        double t = Math.toRadians(mob.getYRot());
        double aheadX = -Math.sin(t);
        double aheadZ = Math.cos(t);
        double sideX = Math.cos(t);
        double sideZ = Math.sin(t);
        double x1 = mob.getX() + frame[0] * aheadX + frame[1] * sideX;
        double z1 = mob.getZ() + frame[0] * aheadZ + frame[1] * sideZ;
        double x2 = mob.getX() + frame[2] * aheadX + frame[3] * sideX;
        double z2 = mob.getZ() + frame[2] * aheadZ + frame[3] * sideZ;
        return new double[] {Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)};
    }

    void save(CompoundTag tag)
    {
        if (this.box != null)
        {
            ListTag list = new ListTag();
            for (double v : this.box)
            {
                list.add(FloatTag.valueOf((float) v));
            }
            tag.put(TAG_BOX, list);
        }
        else if (this.frame != null)
        {
            ListTag list = new ListTag();
            for (float v : this.frame)
            {
                list.add(FloatTag.valueOf(v));
            }
            tag.put(TAG_FRAME, list);
        }
    }

    void load(CompoundTag tag)
    {
        this.box = null;
        this.frame = null;
        ListTag box = tag.getList(TAG_BOX, Tag.TAG_FLOAT);
        if (box.size() == 4)
        {
            this.box = new double[] {box.getFloat(0), box.getFloat(1), box.getFloat(2), box.getFloat(3)};
            return;
        }
        ListTag frame = tag.getList(TAG_FRAME, Tag.TAG_FLOAT);
        if (frame.size() == 4)
        {
            this.frame = new float[] {frame.getFloat(0), frame.getFloat(1), frame.getFloat(2), frame.getFloat(3)};
        }
    }
}
