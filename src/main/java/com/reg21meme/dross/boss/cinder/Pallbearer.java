package com.reg21meme.dross.boss.cinder;

import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;
import com.reg21meme.dross.boss.BossChase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

import static com.reg21meme.dross.boss.cinder.CinderMobTuning.*;

/**
 * The Pallbearer ({@code dross:pallbearer}; design M1A, the user's pick, at his full 9.6 blocks): a gaunt giant of
 * soot-black bone under an undertaker's top hat, bent under a burning coffin chained to his back. He walks the ash
 * roads of the Cinder Wastes in a slow funeral march, his drum beating now and then (CLAUDE.md step 16).
 * <ul>
 *   <li>Neutral ({@link CinderMob}): he leaves you alone until you hurt him. Then he <b>roars</b> (his arms flung open:
 *       Slowness II on the players round him) and fights.</li>
 *   <li>The <b>swipe</b>: a backhand of his whole arm, everything in an arc in front of him hurt and knocked away.</li>
 *   <li>The <b>slam</b>: the coffin heaved up and its weight brought down in front of him: everything round where it
 *       lands hurt and thrown up, a shockwave of ash and embers.</li>
 * </ul>
 * Every number is in {@link CinderMobTuning}.
 */
public class Pallbearer extends CinderMob
{
    /** His fire design (FireDesigns.CINDER_MOBS). */
    public static final String DESIGN = "M1A";
    /** He turns at most this many degrees a tick (a giant). */
    private static final float MAX_TURN = 8.0F;
    private static final String TAG_ROARED_AT = "RoaredAt";

    /** (server) ticks it has walked since its drum last beat */
    private int drumTicks = PALLBEARER_DRUM_TICKS - 20;
    /** (server) whom he last roared at (he roars once when he takes on a new foe) */
    @Nullable
    private UUID roaredAt;

    public Pallbearer(EntityType<? extends Pallbearer> type, Level level)
    {
        super(type, level, PALLBEARER_XP, MAX_TURN);
        this.setDesign(design(DESIGN).number());
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, PALLBEARER_HEALTH)
                .add(Attributes.ARMOR, PALLBEARER_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, PALLBEARER_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, PALLBEARER_KNOCKBACK_RESISTANCE)
                .add(Attributes.FOLLOW_RANGE, PALLBEARER_FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, SWIPE_DAMAGE);
    }

    @Override
    protected String soundPrefix()
    {
        return "entity.pallbearer";
    }

    @Override
    protected double wanderSpeed()
    {
        return PALLBEARER_WANDER_SPEED;
    }

    @Override
    protected double walkPace()
    {
        return PALLBEARER_WALK_PACE;
    }

    @Override
    protected double stepLength()
    {
        return PALLBEARER_STEP_LENGTH;
    }

    @Override
    protected float getSoundVolume()
    {
        return PALLBEARER_VOLUME;
    }

    @Override
    protected void addFightGoals()
    {
        this.goalSelector.addGoal(1, new FightGoal());
    }

    /** (server) His funeral drum now and then as he walks; and he forgets whom he roared at once he has no foe. */
    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide || !this.isAlive())
        {
            return;
        }
        if (this.getTarget() == null)
        {
            this.roaredAt = null;
        }
        if (!this.inMove() && RimefallAnims.groundSpeed(this) > MOVING)
        {
            if (++this.drumTicks >= PALLBEARER_DRUM_TICKS)
            {
                this.drumTicks = 0;
                this.level().playSound(null, this.getX(), this.getY() + 4.0D, this.getZ(), sound("entity.pallbearer.drum"),
                        SoundSource.HOSTILE, PALLBEARER_VOLUME, 1.0F);
            }
        }
    }

    /** True if he has roared at his current foe. */
    public boolean hasRoaredAt(@Nullable LivingEntity foe)
    {
        return foe != null && foe.getUUID().equals(this.roaredAt);
    }

    // ---------------------------------------------------------------- his moves

    /** The roar: Slowness II on every player within {@link CinderMobTuning#ROAR_RANGE} blocks (and on his foe, if it's
     *  a mob there): never a player in creative, never his teammates (the Rage Wand's). */
    void roar(@Nullable LivingEntity foe)
    {
        AABB round = this.getBoundingBox().inflate(ROAR_RANGE);
        for (LivingEntity e : this.hittable(round))
        {
            if ((e instanceof Player || e == foe) && e.distanceToSqr(this) <= Mth.square(ROAR_RANGE + this.getBbWidth() / 2.0D))
            {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROAR_SLOW_TICKS, ROAR_SLOW_LEVEL), this);
            }
        }
    }

    /** The swipe's blow: everything in the arc in front of him. */
    void swipe()
    {
        for (LivingEntity e : this.hittable(this.getBoundingBox().inflate(SWIPE_REACH, 1.0D, SWIPE_REACH)))
        {
            if (this.inArc(e, SWIPE_REACH, SWIPE_ARC_COS) && e.hurt(this.damageSources().mobAttack(this), SWIPE_DAMAGE))
            {
                throwFrom(e, this.position(), SWIPE_KNOCKBACK, SWIPE_KNOCKBACK_UP);
            }
        }
    }

    /** The slam's blow, where the coffin lands: everything round it hurt and thrown up; a shockwave of ash and embers. */
    void slam(Vec3 at)
    {
        AABB round = new AABB(at.x - SLAM_RADIUS, at.y - 1.0D, at.z - SLAM_RADIUS, at.x + SLAM_RADIUS, at.y + SLAM_HEIGHT,
                at.z + SLAM_RADIUS);
        for (LivingEntity e : this.hittable(round.inflate(1.0D)))
        {
            double dx = e.getX() - at.x;
            double dz = e.getZ() - at.z;
            if (Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2.0D <= SLAM_RADIUS && e.getY() <= at.y + SLAM_HEIGHT
                    && e.getY() + e.getBbHeight() >= at.y - 1.0D && e.hurt(this.damageSources().mobAttack(this), SLAM_DAMAGE))
            {
                throwFrom(e, at, SLAM_THROW_OUT, SLAM_THROW_UP);
            }
        }
        if (this.level() instanceof ServerLevel level)
        {
            BlockPos ground = BlockPos.containing(at.x, at.y - 0.5D, at.z);
            BlockState floor = level.getBlockState(ground);
            for (int i = 0; i < 28; i++)
            {
                double angle = i * Math.PI * 2.0D / 28.0D;
                double r = SLAM_RADIUS * (0.6D + this.random.nextDouble() * 0.5D);
                double x = at.x + Math.cos(angle) * r;
                double z = at.z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, at.y + 0.3D, z, 1, 0.1D, 0.1D, 0.1D, 0.02D);
                level.sendParticles(ParticleTypes.WHITE_ASH, x, at.y + 0.4D, z, 4, 0.4D, 0.3D, 0.4D, 0.02D);
                if (!floor.isAir())
                {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), x, at.y + 0.2D, z, 3, 0.2D, 0.1D, 0.2D, 0.15D);
                }
            }
            level.sendParticles(ParticleTypes.ASH, at.x, at.y + 0.5D, at.z, 60, SLAM_RADIUS * 0.6D, 0.5D, SLAM_RADIUS * 0.6D, 0.05D);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5D, at.z, 8, 1.0D, 0.3D, 1.0D, 0.0D);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.4D, at.z, 20, 1.2D, 0.3D, 1.2D, 0.03D);
        }
    }

    /** Where the coffin lands: in front of his feet. */
    Vec3 slamSpot()
    {
        Vec3 facing = this.facing();
        return this.position().add(facing.x * SLAM_FORWARD, 0.0D, facing.z * SLAM_FORWARD);
    }

    /**
     * His fight: a roar at a new foe, then he closes in on it at a walk and, in reach, swipes or slams (the slam more
     * likely with two or more round him). A move always plays to its end; he turns to his foe until just before the
     * blow lands.
     */
    private class FightGoal extends Goal
    {
        private enum Phase { CHASE, ROAR, SWIPE, SLAM }

        private Phase phase = Phase.CHASE;
        private int timer;
        private int swipeCooldown;
        private int slamCooldown;
        private int repath;
        private Vec3 slamAt = Vec3.ZERO;

        FightGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = Pallbearer.this.getTarget();
            return target != null && target.isAlive() && Pallbearer.this.canAttack(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.phase != Phase.CHASE || this.canUse();
        }

        @Override
        public void start()
        {
            LivingEntity target = Pallbearer.this.getTarget();
            this.timer = 0;
            this.repath = 0;
            if (target != null && !Pallbearer.this.hasRoaredAt(target))
            {
                Pallbearer.this.roaredAt = target.getUUID();
                this.begin(Phase.ROAR, "roar");
            }
            else
            {
                this.phase = Phase.CHASE;
            }
        }

        @Override
        public void stop()
        {
            Pallbearer.this.getNavigation().stop();
            Pallbearer.this.endMove();
            this.phase = Phase.CHASE;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        private void begin(Phase phase, String animation)
        {
            this.phase = phase;
            this.timer = 0;
            Pallbearer.this.getNavigation().stop();
            Pallbearer.this.playAnimation(animation);
        }

        private void toChase()
        {
            this.phase = Phase.CHASE;
            this.timer = 0;
            Pallbearer.this.endMove();
        }

        @Override
        public void tick()
        {
            Pallbearer self = Pallbearer.this;
            LivingEntity target = self.getTarget();
            this.swipeCooldown = Math.max(0, this.swipeCooldown - 1);
            this.slamCooldown = Math.max(0, this.slamCooldown - 1);
            this.timer++;
            switch (this.phase)
            {
                case ROAR ->
                {
                    if (target != null)
                    {
                        self.turnTowards(target.position(), MAX_TURN);
                    }
                    if (this.timer == ROAR_HIT_TICK)
                    {
                        self.roar(target);
                    }
                    if (this.timer >= ROAR_TICKS)
                    {
                        this.toChase();
                    }
                }
                case SWIPE ->
                {
                    if (target != null && this.timer < SWIPE_COMMIT_TICK)
                    {
                        self.turnTowards(target.position(), MAX_TURN * 1.5F);
                    }
                    if (this.timer == SWIPE_HIT_TICK)
                    {
                        self.swipe();
                    }
                    if (this.timer >= SWIPE_TICKS)
                    {
                        this.swipeCooldown = SWIPE_COOLDOWN;
                        this.toChase();
                    }
                }
                case SLAM ->
                {
                    if (target != null && this.timer < SLAM_COMMIT_TICK)
                    {
                        self.turnTowards(target.position(), MAX_TURN * 1.5F);
                    }
                    if (this.timer == SLAM_COMMIT_TICK)
                    {
                        this.slamAt = self.slamSpot();
                    }
                    if (this.timer == SLAM_HIT_TICK)
                    {
                        self.slam(this.slamAt);
                    }
                    if (this.timer >= SLAM_TICKS)
                    {
                        this.slamCooldown = SLAM_COOLDOWN;
                        this.toChase();
                    }
                }
                default -> this.tickChase(target);
            }
        }

        private void tickChase(@Nullable LivingEntity target)
        {
            Pallbearer self = Pallbearer.this;
            if (target == null)
            {
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dx = target.getX() - self.getX();
            double dz = target.getZ() - self.getZ();
            double reach = Math.sqrt(dx * dx + dz * dz) - target.getBbWidth() / 2.0D;
            if (this.slamCooldown == 0 && reach <= SLAM_START_REACH && self.inArc(target, SLAM_START_REACH, 0.3D)
                    && (this.foesNear() >= 2 || self.random.nextFloat() < SLAM_CHANCE))
            {
                this.begin(Phase.SLAM, "slam");
                return;
            }
            if (this.swipeCooldown == 0 && self.inArc(target, SWIPE_REACH, SWIPE_ARC_COS))
            {
                this.begin(Phase.SWIPE, "swipe");
                return;
            }
            if (reach <= SWIPE_REACH - 0.5D)
            {
                // In reach, waiting for a move: he stands and turns to it (never shoving into it).
                self.getNavigation().stop();
                self.turnTowards(target.position(), MAX_TURN);
                return;
            }
            this.repath = BossChase.chase(self, target, PALLBEARER_CHASE_SPEED, this.repath, 10);
        }

        /** Players and his foe within the slam's reach of him. */
        private int foesNear()
        {
            Pallbearer self = Pallbearer.this;
            LivingEntity target = self.getTarget();
            int count = 0;
            for (LivingEntity e : self.hittable(self.getBoundingBox().inflate(SLAM_START_REACH)))
            {
                if ((e instanceof Player || e == target || Rage.areEnemies(self, e)) && self.inArc(e, SLAM_START_REACH + 1.0D, -1.0D))
                {
                    count++;
                }
            }
            return count;
        }
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (this.roaredAt != null)
        {
            tag.putUUID(TAG_ROARED_AT, this.roaredAt);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.roaredAt = tag.hasUUID(TAG_ROARED_AT) ? tag.getUUID(TAG_ROARED_AT) : null;
        this.setDesign(design(DESIGN).number());    // (always his look, whatever number it was saved under)
    }
}
