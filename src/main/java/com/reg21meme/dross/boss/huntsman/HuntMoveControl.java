package com.reg21meme.dross.boss.huntsman;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * How the Huntsman and the Alpha Moose steer as they move (the user, October 22, 2026: the walk "awkward and jittery",
 * the circling "wavy"): Minecraft's own turns a mob up to 90 degrees a tick towards wherever it's heading, so it snaps
 * from one path point to the next and zigzags. Here it turns at most {@code turn} degrees a tick (the moose a big
 * animal's slow, steady turn, him a man's), and slows down for a sharp turn rather than swinging wide
 * ({@link HuntsmanTuning#TURN_SLOW_ANGLE}): it moves on smooth curves, its body facing the way it goes.
 */
final class HuntMoveControl extends MoveControl
{
    private final float turn;

    HuntMoveControl(HuntMob mob, float turn)
    {
        super(mob);
        this.turn = turn;
    }

    @Override
    public void tick()
    {
        boolean moving = this.operation == Operation.MOVE_TO;
        float before = this.mob.getYRot();
        super.tick();
        if (!moving)
        {
            return;
        }
        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        if (dx * dx + dz * dz < 1.0E-4D)
        {
            return;
        }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = this.rotlerp(before, want, this.turn);
        this.mob.setYRot(yaw);
        float off = Math.abs(Mth.wrapDegrees(want - yaw));
        if (off > HuntsmanTuning.TURN_SLOW_ANGLE)
        {
            float f = 1.0F - (off - HuntsmanTuning.TURN_SLOW_ANGLE) / (HuntsmanTuning.TURN_SLOWEST_ANGLE - HuntsmanTuning.TURN_SLOW_ANGLE);
            this.mob.setSpeed(this.mob.getSpeed() * Math.max(HuntsmanTuning.TURN_SLOW_LEAST, f));
        }
    }
}
