package com.reg21meme.dross.boss.huntsman.spear;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

/** The Moonsilver Spear's hit, stabbed or thrown: frozen for {@link SpearTuning#FREEZE_TICKS} and slowed. */
public final class SpearFreeze
{
    private SpearFreeze() {}

    public static void freeze(LivingEntity target, @Nullable Entity by)
    {
        if (!(target.level() instanceof ServerLevel level) || !target.isAlive())
        {
            return;
        }
        // Frozen like after powder snow: the frost thaws 2 ticks' worth a tick out of it, so it stays fully frozen for
        // FREEZE_TICKS and then thaws. Mobs that can't freeze (strays, polar bears, leather armour) are only slowed.
        if (target.canFreeze())
        {
            target.setTicksFrozen(Math.max(target.getTicksFrozen(),
                    target.getTicksRequiredToFreeze() + SpearTuning.FREEZE_TICKS * 2));
        }
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SpearTuning.FREEZE_TICKS,
                SpearTuning.FREEZE_SLOWNESS), by);
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                target.getZ(), SpearTuning.FREEZE_PARTICLES, target.getBbWidth() * 0.4D, target.getBbHeight() * 0.3D,
                target.getBbWidth() * 0.4D, 0.02D);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE,
                SoundSource.PLAYERS, 0.8F, 1.2F);
    }
}
