package com.reg21meme.dross.boss.huntsman;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Huntsman's Alpha Moose ({@code dross:alpha_moose}; the gallery's 6A2, about 5.3 blocks to its antler tips, in
 * its tack). The Huntsman rides it in stage 1 of his fight: he sits at its own position (his saddle model's floor is
 * the moose's ground) and turns with it. Fighting ({@link HuntFight}), it keeps the Wild Hunt's bar for both and its
 * {@link WildHuntGoal} picks their moves in stage 1; in stage 2 its own {@link MooseGoal}. Calm, it walks about slowly.
 */
public class AlphaMoose extends HuntMob
{
    /** Calm, it walks at this much of its speed. */
    private static final double CALM_WALK = 0.6D;

    public AlphaMoose(EntityType<? extends AlphaMoose> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HuntsmanTuning.MOOSE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, HuntsmanTuning.MOOSE_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, HuntsmanTuning.FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WildHuntGoal(this));
        this.goalSelector.addGoal(1, new MooseGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, CALM_WALK)
        {
            @Override
            public boolean canUse()
            {
                return AlphaMoose.this.stage() == HuntStage.CALM && super.canUse();
            }

            @Override
            public boolean canContinueToUse()
            {
                return AlphaMoose.this.stage() == HuntStage.CALM && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
    }

    @Override
    public List<HuntAnims.Anim> animations()
    {
        return HuntAnims.ALPHA_MOOSE;
    }

    /** The Huntsman in its saddle, or null. */
    Huntsman rider()
    {
        return this.getFirstPassenger() instanceof Huntsman h ? h : null;
    }

    /** It's steered by its own goals, never by the one riding it. */
    @Override
    public LivingEntity getControllingPassenger()
    {
        return null;
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
