package com.reg21meme.dross.boss.huntsman;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Stage 2, the Huntsman on foot (the user's table, {@link HuntsmanTuning#FOOT_CHANCES}): his spear up close (the
 * combo, the thrust and sweep), the leaping strike and the thrown spear at middle range, his bow far off (the aimed
 * shot, the arrow rain), the backstep shot when you crowd him, the bola and the Horn of the Hunt. His spear-work waits
 * while his spear is thrown.
 */
final class FootGoal extends HuntGoal
{
    private static final double[] BANDS = {HuntsmanTuning.FOOT_CLOSE, HuntsmanTuning.FOOT_MID, HuntsmanTuning.FOOT_FAR};
    /** A thrust's narrow reach and a sweep's wide one (degrees round from his front). */
    private static final double THRUST_HALF_ANGLE = 35.0D;
    private static final double SPEAR_KNOCK = 0.6D;
    private static final double SPEAR_KNOCK_UP = 0.15D;
    /** Before each blow he steps in this far (blocks a tick), if his target is out of reach. */
    private static final double LUNGE = 0.35D;
    /** His leap lands this far short of where his target stood. */
    private static final double LEAP_SHORT = 1.2D;

    private final Huntsman h;

    FootGoal(Huntsman h)
    {
        super(h);
        this.h = h;
    }

    @Override
    protected boolean stageOk()
    {
        return this.h.stage() == HuntStage.DUEL && !this.h.isMounted();
    }

    @Override
    protected Object[][] table()
    {
        return HuntsmanTuning.FOOT_CHANCES;
    }

    @Override
    protected double[] bands()
    {
        return BANDS;
    }

    @Override
    protected int basePause()
    {
        return HuntsmanTuning.PAUSE_HUNTSMAN;
    }

    @Override
    protected String fallback()
    {
        return "run_in";
    }

    @Override
    protected boolean can(String name, LivingEntity target, double d)
    {
        boolean spear = !this.h.spearOut;
        return switch (name)
        {
            case "combo", "thrust_sweep" -> spear && d <= HuntsmanTuning.SPEAR_REACH + 1.0D;
            case "backstep" -> d <= HuntsmanTuning.FOOT_CLOSE && this.sees(target);
            case "horn" -> d <= HuntsmanTuning.HORN_LENGTH;
            case "leap" -> spear && d >= HuntsmanTuning.LEAP_MIN && d <= HuntsmanTuning.LEAP_MAX && this.sees(target);
            case "throw_spear" -> spear && d >= HuntsmanTuning.SPEAR_THROW_MIN && d <= HuntsmanTuning.SPEAR_THROW_MAX
                    && this.sees(target);
            case "bola" -> d >= HuntsmanTuning.BOLA_MIN && d <= HuntsmanTuning.BOLA_MAX && this.sees(target);
            case "aimed" -> d >= HuntsmanTuning.AIMED_MIN && d <= HuntsmanTuning.AIMED_MAX && this.sees(target);
            case "rain", "run_in" -> true;
            default -> false;
        };
    }

    @Override
    protected int cooldown(String name)
    {
        return switch (name)
        {
            case "combo" -> HuntsmanTuning.COMBO_COOLDOWN;
            case "thrust_sweep" -> HuntsmanTuning.THRUST_SWEEP_COOLDOWN;
            case "backstep" -> HuntsmanTuning.BACKSTEP_COOLDOWN;
            case "horn" -> HuntsmanTuning.HORN_COOLDOWN;
            case "leap" -> HuntsmanTuning.LEAP_COOLDOWN;
            case "throw_spear" -> HuntsmanTuning.SPEAR_THROW_COOLDOWN;
            case "bola" -> HuntsmanTuning.BOLA_COOLDOWN;
            case "aimed" -> HuntsmanTuning.AIMED_COOLDOWN;
            case "rain" -> HuntsmanTuning.RAIN_COOLDOWN_STAGE2;
            default -> 0;
        };
    }

    private static int moment(String anim, String moment)
    {
        return HuntCues.moment(HuntAnims.FOOT, anim, moment);
    }

    private static int length(String anim)
    {
        HuntAnims.Anim a = HuntAnims.named(HuntAnims.HUNTSMAN, anim);
        return a != null ? a.ticks() : 20;
    }

    /** A blow of his spear: a step in first if his target is just out of reach. */
    private Runnable blow(double halfAngle, float damage)
    {
        return () ->
        {
            LivingEntity target = this.target;
            if (target != null && HuntFight.flat(this.h, target) > HuntsmanTuning.SPEAR_REACH)
            {
                Vec3 to = target.position().subtract(this.h.position());
                double d = Math.sqrt(to.x * to.x + to.z * to.z);
                this.h.move(MoverType.SELF, new Vec3(to.x / d * LUNGE, 0.0D, to.z / d * LUNGE));
            }
            if (HuntFight.strike(this.h, this.h.position(), HuntFight.facing(this.h), HuntsmanTuning.SPEAR_REACH, halfAngle,
                    damage, SPEAR_KNOCK, SPEAR_KNOCK_UP) > 0)
            {
                this.h.level().playSound(null, this.h.getX(), this.h.getY(), this.h.getZ(), SoundEvents.TRIDENT_HIT,
                        SoundSource.HOSTILE, 1.0F, 0.9F);
            }
        };
    }

    @Nullable
    @Override
    protected Move make(String name, LivingEntity target)
    {
        return switch (name)
        {
            case "combo" -> new Timed("combo", this.h, "spear_combo", length("spear_combo"), moment("spear_combo", "hits.2"))
                    .at(moment("spear_combo", "hits.0"), this.blow(THRUST_HALF_ANGLE, HuntsmanTuning.COMBO_DAMAGE_1))
                    .at(moment("spear_combo", "hits.1"), this.blow(THRUST_HALF_ANGLE, HuntsmanTuning.COMBO_DAMAGE_2))
                    .at(moment("spear_combo", "hits.2"), this.blow(HuntsmanTuning.SWEEP_HALF_ANGLE, HuntsmanTuning.COMBO_DAMAGE_3));
            case "thrust_sweep" -> new Timed("thrust_sweep", this.h, "thrust_sweep", length("thrust_sweep"),
                    moment("thrust_sweep", "hits.1"))
                    .at(moment("thrust_sweep", "hits.0"), this.blow(THRUST_HALF_ANGLE, HuntsmanTuning.THRUST_SWEEP_DAMAGE_1))
                    .at(moment("thrust_sweep", "hits.1"), this.blow(HuntsmanTuning.SWEEP_HALF_ANGLE,
                            HuntsmanTuning.THRUST_SWEEP_DAMAGE_2));
            case "backstep" -> new Timed("backstep", this.h, "backstep_shoot", length("backstep_shoot"),
                    moment("backstep_shoot", "shots.0"))
                    .first(() -> this.aim(target, HuntsmanTuning.BACKSTEP_BASE_DAMAGE, HuntFight.Frost.NONE));
            case "aimed" -> new Timed("aimed", this.h, "aimed_shot", length("aimed_shot"), moment("aimed_shot", "release"))
                    .first(() -> this.aim(target, HuntsmanTuning.AIMED_BASE_DAMAGE, HuntFight.Frost.FREEZE))
                    .at(moment("aimed_shot", "glint"), this::glint);
            case "rain" -> new Timed("rain", this.h, "arrow_rain", moment("arrow_rain", "shots.3") + 4,
                    moment("arrow_rain", "shots.3"))
                    .first(() -> this.h.shotTarget = null)
                    .at(moment("arrow_rain", "shots.3"), () -> HuntFight.rain(this.h, target, HuntsmanTuning.RAIN_ARROWS_STAGE2));
            case "bola" -> new Timed("bola", this.h, "bola_throw", length("bola_throw"), moment("bola_throw", "release"))
                    .at(moment("bola_throw", "release"), () -> Bola.throwAt(this.h,
                            hand(this.h, HuntsmanTuning.FOOT_HAND_UP), target));
            case "horn" -> new Timed("horn", this.h, "horn_blow", moment("horn_blow", "blow") + 10, moment("horn_blow", "blow"))
                    .at(moment("horn_blow", "blow"), () -> HuntFight.blast(this.h, HuntFight.facing(this.h),
                            HuntsmanTuning.HORN_LENGTH, HuntsmanTuning.HORN_HALF_ANGLE, HuntsmanTuning.HORN_DAMAGE,
                            HuntsmanTuning.HORN_PUSH, HuntsmanTuning.HORN_PUSH_UP, HuntsmanTuning.HORN_SLOW_TICKS,
                            HuntsmanTuning.HORN_SLOW_LEVEL));
            case "leap" -> new Leap();
            case "throw_spear" -> new ThrowSpear();
            case "run_in" -> new Chase("run_in", HuntsmanTuning.HUNTSMAN_RUN, HuntsmanTuning.FOOT_CLOSE);
            default -> null;
        };
    }

    private void aim(LivingEntity target, double base, HuntFight.Frost frost)
    {
        this.h.shotTarget = target;
        this.h.shotBase = base;
        this.h.shotFrost = frost;
    }

    /** The aimed shot's warning: a frost glint at his bow, and a chime. */
    private void glint()
    {
        if (this.h.level() instanceof ServerLevel level)
        {
            Vec3 bow = hand(this.h, HuntsmanTuning.FOOT_HAND_UP + 0.4D);
            level.sendParticles(ParticleTypes.END_ROD, bow.x, bow.y, bow.z, 8, 0.15D, 0.15D, 0.15D, 0.02D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, bow.x, bow.y, bow.z, 12, 0.2D, 0.2D, 0.2D, 0.03D);
            level.playSound(null, bow.x, bow.y, bow.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2.0F, 1.6F);
        }
    }

    /** The leaping strike: a crouch, then he leaps at where his target stood and drives his spear down as he lands. */
    private final class Leap implements Move
    {
        private Vec3 landing = Vec3.ZERO;
        private Vec3 from = Vec3.ZERO;

        @Override
        public String name()
        {
            return "leap";
        }

        @Override
        public void start()
        {
            FootGoal.this.h.getNavigation().stop();
            FootGoal.this.h.play("leap_strike");
        }

        @Override
        public boolean tick(int t)
        {
            Huntsman h = FootGoal.this.h;
            LivingEntity target = FootGoal.this.target;
            int lands = moment("leap_strike", "hits.0");
            if (t < HuntsmanTuning.LEAP_TAKEOFF && target != null)
            {
                HuntFight.face(h, target);
            }
            if (t == HuntsmanTuning.LEAP_TAKEOFF)
            {
                this.from = h.position();
                Vec3 to = target != null ? target.position() : h.position();
                Vec3 d = to.subtract(this.from);
                double len = Math.sqrt(d.x * d.x + d.z * d.z);
                this.landing = len > LEAP_SHORT ? this.from.add(d.x / len * (len - LEAP_SHORT), d.y, d.z / len * (len - LEAP_SHORT))
                        : this.from;
            }
            if (t > HuntsmanTuning.LEAP_TAKEOFF && t <= lands)
            {
                double f = 1.0D / (lands - t + 1);
                Vec3 step = this.landing.subtract(h.position()).scale(f);
                h.move(MoverType.SELF, new Vec3(step.x, 0.0D, step.z));
            }
            if (t == lands)
            {
                HuntFight.burst(h, h.position(), HuntsmanTuning.LEAP_SHOCK_RADIUS, HuntsmanTuning.LEAP_DAMAGE,
                        HuntsmanTuning.LEAP_SHOCK_KNOCK, 0.3D);
                if (h.level() instanceof ServerLevel level)
                {
                    level.sendParticles(ParticleTypes.SNOWFLAKE, h.getX(), h.getY() + 0.2D, h.getZ(), 40, 1.2D, 0.2D, 1.2D, 0.1D);
                    level.sendParticles(ParticleTypes.CLOUD, h.getX(), h.getY() + 0.2D, h.getZ(), 12, 1.0D, 0.1D, 1.0D, 0.05D);
                    level.playSound(null, h.getX(), h.getY(), h.getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.HOSTILE,
                            1.5F, 0.7F);
                }
            }
            return t >= length("leap_strike");
        }
    }

    /** The thrown spear: he hurls it, and waits for it to come back to his hand on its rope (then catches it). */
    private final class ThrowSpear implements Move
    {
        private int caughtAt = -1;

        @Override
        public String name()
        {
            return "throw_spear";
        }

        @Override
        public void start()
        {
            FootGoal.this.h.getNavigation().stop();
            FootGoal.this.h.play("throw_spear");
        }

        @Override
        public boolean tick(int t)
        {
            Huntsman h = FootGoal.this.h;
            LivingEntity target = FootGoal.this.target;
            int release = moment("throw_spear", "release");
            if (t <= release && target != null)
            {
                HuntFight.face(h, target);
            }
            if (t == release && target != null)
            {
                HuntSpear.throwAt(h, hand(h, HuntsmanTuning.FOOT_HAND_UP + 0.3D), target);
            }
            if (t > release && this.caughtAt < 0 && !h.spearOut)
            {
                this.caughtAt = t;
                h.play("catch_spear");
            }
            if (t > release + HuntsmanTuning.SPEAR_RETURN_TICKS + 20)
            {
                h.spearOut = false;                    // it never came back: a spear in his hand again
                return true;
            }
            return this.caughtAt >= 0 && t >= this.caughtAt + length("catch_spear");
        }
    }
}
