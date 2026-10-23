package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.registry.ModEffects;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Huntsman's bola ({@code dross:bola}): a weighted rope of ice he throws (the user's pick: "it holds you still for
 * 2 s, then 5 s of Slowness"). Whatever it hits takes a heart and is snared ({@code dross:snared}, held still for
 * {@link HuntsmanTuning#BOLA_SNARE_TICKS}), then slowed for {@link HuntsmanTuning#BOLA_SLOW_TICKS} more. Drawn as its
 * item flying ({@code dross:bola}).
 */
public class Bola extends ThrowableItemProjectile
{
    /** Its drop each tick (a thrown item's gravity), for aiming. */
    static final double GRAVITY = 0.03D;

    public Bola(EntityType<? extends Bola> type, Level level)
    {
        super(type, level);
    }

    public Bola(Level level, LivingEntity thrower)
    {
        super(ModEntities.BOLA.get(), thrower, level);
    }

    /** He throws one from {@code from} at {@code target}. */
    static void throwAt(HuntMob thrower, Vec3 from, LivingEntity target)
    {
        Bola bola = new Bola(thrower.level(), thrower);
        bola.setPos(from.x, from.y, from.z);
        Vec3 aim = HuntFight.aimAt(from, target, HuntsmanTuning.BOLA_SPEED, GRAVITY / 2.0D);
        bola.shoot(aim.x, aim.y, aim.z, HuntsmanTuning.BOLA_SPEED, 1.0F);
        thrower.level().addFreshEntity(bola);
        thrower.level().playSound(null, from.x, from.y, from.z, ModSounds.HUNTSMAN_BOLA_RELEASE.get(), SoundSource.HOSTILE,
                1.5F, 1.0F);
    }

    @Override
    protected Item getDefaultItem()
    {
        return ModItems.BOLA.get();
    }

    @Override
    protected float getGravity()
    {
        return (float) GRAVITY;
    }

    /** It passes through him and his moose. */
    @Override
    protected boolean canHitEntity(Entity e)
    {
        return super.canHitEntity(e) && !(this.getOwner() instanceof HuntMob h && HuntFight.isPartner(h, e));
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)
    {
        super.onHitEntity(hit);
        if (hit.getEntity() instanceof LivingEntity victim)
        {
            Entity owner = this.getOwner();
            float scale = owner instanceof HuntMob h ? HuntFight.damageScale(h) : 1.0F;
            victim.hurt(this.damageSources().thrown(this, owner), HuntsmanTuning.BOLA_DAMAGE * scale);
            victim.addEffect(new MobEffectInstance(ModEffects.SNARED.get(), HuntsmanTuning.BOLA_SNARE_TICKS, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    HuntsmanTuning.BOLA_SNARE_TICKS + HuntsmanTuning.BOLA_SLOW_TICKS, 0));
            victim.setDeltaMovement(0.0D, Math.min(0.0D, victim.getDeltaMovement().y), 0.0D);
            victim.hurtMarked = true;
            this.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), ModSounds.HUNTSMAN_BOLA_SNARE.get(),
                    SoundSource.HOSTILE, 1.5F, 1.0F);
        }
    }

    @Override
    protected void onHit(HitResult result)
    {
        super.onHit(result);
        if (this.level() instanceof ServerLevel level)
        {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(this.getDefaultItem())),
                    this.getX(), this.getY(), this.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.05D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY(), this.getZ(), 10, 0.3D, 0.3D, 0.3D, 0.02D);
            this.discard();
        }
    }
}
