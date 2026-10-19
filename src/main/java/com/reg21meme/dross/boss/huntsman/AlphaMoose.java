package com.reg21meme.dross.boss.huntsman;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Huntsman's Alpha Moose ({@code dross:alpha_moose}; the gallery's 6A2, about 5.3 blocks to its antler tips, in
 * its tack). The Huntsman rides it in stage 1 of his fight: he sits at its own position (his saddle model's floor is
 * the moose's ground) and turns with it. For now it's on show only ({@link HuntMob}); its fight comes later.
 */
public class AlphaMoose extends HuntMob
{
    public AlphaMoose(EntityType<? extends AlphaMoose> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public List<HuntAnims.Anim> animations()
    {
        return HuntAnims.ALPHA_MOOSE;
    }

    /** The rider sits at its feet' height: the rider's model is built standing on the moose's ground. */
    @Override
    public double getPassengersRidingOffset()
    {
        return 0.0D;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move)
    {
        if (this.hasPassenger(passenger))
        {
            move.accept(passenger, this.getX(), this.getY(), this.getZ());
            passenger.setYRot(this.getYRot());
            if (passenger instanceof LivingEntity rider)
            {
                rider.yBodyRot = this.yBodyRot;
                rider.yBodyRotO = this.yBodyRotO;
                rider.setYHeadRot(this.getYHeadRot());
            }
        }
    }
}
