package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Huntsman's thrown spear ({@code dross:hunt_spear}; the user's pick: "he hurls it, then pulls it back on its
 * rope"): it hits for {@link HuntsmanTuning#SPEAR_THROW_DAMAGE}, then (or as soon as it hits the ground, or after
 * {@link HuntsmanTuning#SPEAR_RETURN_TICKS}) it flies back to his hand through anything in the way. Nobody can pick it
 * up. It's drawn with the Moonsilver Spear's model, made big ({@code spear/client/SpearRenderer}; until October 22,
 * 2026 a trident stood in for it).
 */
public class HuntSpear extends AbstractArrow
{
    /** Its speed flying back to him. */
    private static final double RETURN_SPEED = 1.4D;
    /** It's back in his hand this close to him. */
    private static final double CATCH_DISTANCE = 1.6D;

    private boolean returning;
    private int flying;

    public HuntSpear(EntityType<? extends HuntSpear> type, Level level)
    {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    /** He hurls it from {@code from} at {@code target}; his spear is out until it's back. */
    static void throwAt(Huntsman owner, Vec3 from, LivingEntity target)
    {
        HuntSpear spear = new HuntSpear(ModEntities.HUNT_SPEAR.get(), owner.level());
        spear.setOwner(owner);
        spear.setPos(from.x, from.y, from.z);
        Vec3 aim = HuntFight.aimAt(owner, from, target, HuntsmanTuning.SPEAR_THROW_SPEED, HuntFight.ARROW_DROP);
        spear.shoot(aim.x, aim.y, aim.z, HuntsmanTuning.SPEAR_THROW_SPEED, 1.0F);
        spear.setBaseDamage(0.0D);
        spear.getPersistentData().putUUID(HuntFight.AIM_TAG, target.getUUID());
        if (owner.level().addFreshEntity(spear))
        {
            owner.spearOut = true;
            owner.level().playSound(null, from.x, from.y, from.z, ModSounds.HUNTSMAN_SPEAR_THROW.get(), SoundSource.HOSTILE,
                    1.5F, 1.0F);
        }
    }

    @Override
    public void tick()
    {
        this.flying++;
        Entity owner = this.getOwner();
        if (!this.level().isClientSide && (owner == null || !owner.isAlive()))
        {
            this.discard();
            return;
        }
        if (owner != null && (this.returning || this.flying > HuntsmanTuning.SPEAR_RETURN_TICKS))
        {
            this.returning = true;
            this.setNoPhysics(true);
            this.inGround = false;
            Vec3 to = owner.getEyePosition().subtract(this.position());
            if (!this.level().isClientSide && to.length() < CATCH_DISTANCE)
            {
                if (owner instanceof Huntsman h)
                {
                    h.spearBack();
                }
                this.discard();
                return;
            }
            this.setDeltaMovement(to.normalize().scale(RETURN_SPEED));
        }
        super.tick();
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)
    {
        Entity e = hit.getEntity();
        Entity owner = this.getOwner();
        if (owner instanceof HuntMob h && HuntFight.isPartner(h, e))
        {
            return;
        }
        float scale = owner instanceof HuntMob h ? HuntFight.damageScale(h) : 1.0F;
        if (e.hurt(this.damageSources().trident(this, owner == null ? this : owner),
                HuntsmanTuning.SPEAR_THROW_DAMAGE * scale))
        {
            this.playSound(ModSounds.HUNTSMAN_SPEAR_HIT.get(), 1.2F, 1.0F);
        }
        this.returning = true;
        this.setDeltaMovement(this.getDeltaMovement().scale(-0.05D));
    }

    @Override
    protected void onHitBlock(BlockHitResult hit)
    {
        super.onHitBlock(hit);
        this.returning = true;
    }

    @Override
    protected boolean tryPickup(Player player)
    {
        return false;
    }

    @Override
    protected ItemStack getPickupItem()
    {
        return ItemStack.EMPTY;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent()
    {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Returning", this.returning);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.returning = tag.getBoolean("Returning");
    }
}
