package com.reg21meme.dross.boss.rimefall;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;

/**
 * One of a Pond Skater's legs as a small hitbox of its own (the user: "make its leg also part of the hitbox, but a small
 * hitbox for the legs"), like the Ender Dragon's body parts: hitting it hurts the Pond Skater. It's only there to be
 * hit: it never bumps into anything and is never saved (the Pond Skater moves it every tick).
 */
public class PondSkaterPart extends PartEntity<PondSkater>
{
    private final EntityDimensions size;

    public PondSkaterPart(PondSkater parent, float width, float height)
    {
        super(parent);
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
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
        return this.size;
    }

    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }
}
