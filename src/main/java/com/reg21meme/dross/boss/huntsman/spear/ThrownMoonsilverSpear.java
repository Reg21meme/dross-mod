package com.reg21meme.dross.boss.huntsman.spear;

import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A thrown Moonsilver Spear ({@code dross:moonsilver_spear}): it flies like a trident, hits for
 * {@link SpearTuning#THROW_DAMAGE} and freezes what it hits, then (or once it has stuck in the ground a moment) it
 * flies back to its thrower like a trident with Loyalty; only its thrower can catch it. If its thrower is gone it drops
 * as an item.
 */
public class ThrownMoonsilverSpear extends AbstractArrow
{
    private static final String TAG_SPEAR = "Spear";
    private static final String TAG_DEALT = "DealtDamage";

    private ItemStack spear = new ItemStack(ModItems.MOONSILVER_SPEAR.get());
    private boolean dealtDamage;
    private int returnTicks;

    public ThrownMoonsilverSpear(EntityType<? extends ThrownMoonsilverSpear> type, Level level)
    {
        super(type, level);
    }

    public ThrownMoonsilverSpear(Level level, LivingEntity owner, ItemStack stack)
    {
        super(ModEntities.MOONSILVER_SPEAR.get(), owner, level);
        this.spear = stack.copy();
    }

    @Override
    public void tick()
    {
        if (this.inGroundTime > 4)
        {
            this.dealtDamage = true;
        }
        Entity owner = this.getOwner();
        if ((this.dealtDamage || this.isNoPhysics()) && owner != null)
        {
            if (!this.canReturnTo(owner))
            {
                if (!this.level().isClientSide && this.pickup == Pickup.ALLOWED)
                {
                    this.spawnAtLocation(this.getPickupItem(), 0.1F);
                }
                this.discard();
            }
            else
            {
                // (a trident's Loyalty, at RETURN_PULL)
                int pull = SpearTuning.RETURN_PULL;
                this.setNoPhysics(true);
                Vec3 to = owner.getEyePosition().subtract(this.position());
                this.setPosRaw(this.getX(), this.getY() + to.y * 0.015D * pull, this.getZ());
                if (this.level().isClientSide)
                {
                    this.yOld = this.getY();
                }
                this.setDeltaMovement(this.getDeltaMovement().scale(0.95D).add(to.normalize().scale(0.05D * pull)));
                if (this.returnTicks == 0)
                {
                    this.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
                }
                this.returnTicks++;
            }
        }
        super.tick();
    }

    private boolean canReturnTo(Entity owner)
    {
        return owner.isAlive() && !(owner instanceof ServerPlayer player && player.isSpectator());
    }

    @Override
    protected ItemStack getPickupItem()
    {
        return this.spear.copy();
    }

    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 from, Vec3 to)
    {
        return this.dealtDamage ? null : super.findHitEntity(from, to);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)
    {
        Entity target = hit.getEntity();
        float damage = SpearTuning.THROW_DAMAGE;
        if (target instanceof LivingEntity living)
        {
            damage += EnchantmentHelper.getDamageBonus(this.spear, living.getMobType());
        }
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().trident(this, owner == null ? this : owner);
        this.dealtDamage = true;
        if (target.hurt(source, damage) && target.getType() != EntityType.ENDERMAN && target instanceof LivingEntity living)
        {
            if (owner instanceof LivingEntity thrower)
            {
                EnchantmentHelper.doPostHurtEffects(living, thrower);
                EnchantmentHelper.doPostDamageEffects(thrower, living);
            }
            SpearFreeze.freeze(living, owner);
            this.doPostHurtEffects(living);
        }
        this.setDeltaMovement(this.getDeltaMovement().multiply(-0.01D, -0.1D, -0.01D));
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
    }

    @Override
    protected boolean tryPickup(Player player)
    {
        return super.tryPickup(player) || this.isNoPhysics() && this.ownedBy(player) && player.getInventory().add(this.getPickupItem());
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent()
    {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void playerTouch(Player player)
    {
        if (this.ownedBy(player) || this.getOwner() == null)
        {
            super.playerTouch(player);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_SPEAR, 10))
        {
            this.spear = ItemStack.of(tag.getCompound(TAG_SPEAR));
        }
        this.dealtDamage = tag.getBoolean(TAG_DEALT);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.put(TAG_SPEAR, this.spear.save(new CompoundTag()));
        tag.putBoolean(TAG_DEALT, this.dealtDamage);
    }

    /** (it never despawns while it can still come back) */
    @Override
    public void tickDespawn()
    {
        if (this.pickup != Pickup.ALLOWED)
        {
            super.tickDespawn();
        }
    }

    @Override
    protected float getWaterInertia()
    {
        return 0.99F;
    }

    @Override
    public boolean shouldRender(double x, double y, double z)
    {
        return true;
    }
}
