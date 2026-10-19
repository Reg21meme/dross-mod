package com.reg21meme.dross.boss.huntsman;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The Huntsman ({@code dross:huntsman}; the gallery's 6A rider: a wolf's head worn as a hood, deer antlers, a wide
 * bearskin cloak, a boar spear of ice, a bow on his back). He has two models: in the Alpha Moose's saddle (while he
 * rides it, stage 1 of his fight, {@link HuntAnims#RIDING}) and on foot (stage 2, {@link HuntAnims#HUNTSMAN}). For now
 * he's on show only ({@link HuntMob}); his fight comes later.
 */
public class Huntsman extends HuntMob
{
    public Huntsman(EntityType<? extends Huntsman> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 150.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D);
    }

    /** True while he's in a saddle (he wears his riding model). Works on both sides. */
    public boolean isMounted()
    {
        return this.getVehicle() instanceof AlphaMoose;
    }

    @Override
    public List<HuntAnims.Anim> animations()
    {
        return this.isMounted() ? HuntAnims.RIDING : HuntAnims.HUNTSMAN;
    }

    /** He sits at the moose's own position (his saddle model is built standing on the moose's ground). */
    @Override
    public double getMyRidingOffset()
    {
        return 0.0D;
    }
}
