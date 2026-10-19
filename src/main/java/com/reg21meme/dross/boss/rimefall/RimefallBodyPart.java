package com.reg21meme.dross.boss.rimefall;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * A piece of a long animal's body as a hitbox of its own (the user, October 19: the Rimehorn's hitbox "for the front of
 * his face is off ... it needs to count his face and his snout as well"): its main hitbox is a square round its middle,
 * but a Rimehorn is 8 blocks long and a moose nearly 5, so their heads and rumps stuck out of it. Like the Ender Dragon's
 * parts and the Pond Skater's legs ({@link PondSkaterPart}): hitting one hurts the animal; it never bumps into anything
 * and is never saved (the animal moves it every tick, {@link #place}). A calf's are smaller and nearer.
 */
public class RimefallBodyPart<T extends Mob> extends PartEntity<T>
{
    private final float width;
    private final float height;
    private float scale = 1.0F;

    public RimefallBodyPart(T parent, float width, float height)
    {
        super(parent);
        this.width = width;
        this.height = height;
        this.refreshDimensions();
    }

    /** Puts each part where it belongs on the animal (blocks from its feet: its own left +x, up +y, its front -z), turned with its body, at {@code scale} (a calf's). */
    public static void place(Mob animal, RimefallBodyPart<?>[] parts, double[][] spots, float scale)
    {
        float yaw = animal.yBodyRot * ((float) Math.PI / 180.0F);
        double cos = Mth.cos(yaw);
        double sin = Mth.sin(yaw);
        for (int i = 0; i < parts.length; i++)
        {
            RimefallBodyPart<?> part = parts[i];
            part.setScale(scale);
            double lx = spots[i][0] * scale;
            double lz = spots[i][2] * scale;
            double x = animal.getX() + lx * cos + lz * sin;
            double y = animal.getY() + spots[i][1] * scale;
            double z = animal.getZ() + lx * sin - lz * cos;
            part.xo = part.getX();
            part.yo = part.getY();
            part.zo = part.getZ();
            part.xOld = part.getX();
            part.yOld = part.getY();
            part.zOld = part.getZ();
            part.setPos(x, y, z);
        }
    }

    private void setScale(float scale)
    {
        if (scale != this.scale)
        {
            this.scale = scale;
            this.refreshDimensions();
        }
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    public boolean isPickable()
    {
        return true;
    }

    @Nullable
    @Override
    public ItemStack getPickResult()
    {
        return this.getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        return !this.isInvulnerableTo(source) && this.getParent().hurt(source, amount);
    }

    @Override
    public boolean is(Entity entity)
    {
        return this == entity || this.getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return EntityDimensions.scalable(this.width * this.scale, this.height * this.scale);
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }
}
