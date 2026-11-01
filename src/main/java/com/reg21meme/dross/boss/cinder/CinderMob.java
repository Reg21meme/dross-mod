package com.reg21meme.dross.boss.cinder;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.FireAnims;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.FireDesigns;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * One of the Cinder Wastes' regular mobs (CLAUDE.md, "The plan", step 16; the user's answers, October 31, 2026): the
 * {@link Pallbearer} and the {@link CinderHermit}. Built on the showcase's fire model ({@link FireCandidate}: its look,
 * its glow, its moving flames, its animations synced and the sounds they make, {@code FireSounds.CUES}), but alive: it
 * walks, wanders and fights. Every number is in {@link CinderMobTuning}.
 * <ul>
 *   <li><b>Neutral</b>: it never starts a fight. Hurt, it fights back (Minecraft's neutral mobs' way, like the
 *       Rimehorn): its anger runs out after 20-40 seconds, even while it chases you, and it gives up a chase
 *       {@link CinderMobTuning#CHASE_LEASH} blocks from where it began and walks back.</li>
 *   <li><b>Its animation</b>: a move the server started (synced: {@link #playAnimation}), or, between moves, its walk while
 *       it moves (played as fast as it really goes, so its feet don't slide) or its idle.</li>
 *   <li>It despawns like any monster (far from players, never with a name tag), never on Peaceful; fire and lava never
 *       hurt it; it can't be pushed or leashed. It dies by its own death animation (no tipping over), lies a moment,
 *       then goes in a puff of smoke.</li>
 * </ul>
 */
public abstract class CinderMob extends FireCandidate implements NeutralMob
{
    private static final UniformInt ANGER = TimeUtil.rangeOfSeconds(CinderMobTuning.ANGER_MIN_SECONDS,
            CinderMobTuning.ANGER_MAX_SECONDS);

    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;
    /** (server) where it was when it started chasing its target (it gives up past the leash) */
    @Nullable
    private Vec3 chaseFrom;

    protected CinderMob(EntityType<? extends CinderMob> type, Level level, int xp, float maxTurn)
    {
        super(type, level, false);
        this.xpReward = xp;
        this.setMaxUpStep(CinderMobTuning.STEP_HEIGHT);
        this.moveControl = new TurningMoveControl(this, maxTurn);
    }

    // ---------------------------------------------------------------- what each one says

    /** Its sounds' prefix ("entity.pallbearer"). */
    protected abstract String soundPrefix();

    /** How fast it wanders (times its speed). */
    protected abstract double wanderSpeed();

    /** Blocks a second its walk's feet cover as made. */
    protected abstract double walkPace();

    /** Blocks between two of its footfalls. */
    protected abstract double stepLength();

    /** Its fighting goals (and the hermit's hiding), added at the top of its goals. */
    protected abstract void addFightGoals();

    // ---------------------------------------------------------------- goals

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.addFightGoals();
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, this.wanderSpeed()));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (!this.level().isClientSide && this.isAlive())
        {
            // Its anger runs out even while it chases you (false: vanilla's neutral mobs stay angry for as long as they
            // keep a player as their target).
            this.updatePersistentAnger((ServerLevel) this.level(), false);
            this.keepToLeash();
        }
    }

    /** Gives up a chase {@link CinderMobTuning#CHASE_LEASH} blocks from where it began, and walks back there (not while
     *  raged: the Rage Wand's fights are its own). */
    private void keepToLeash()
    {
        LivingEntity target = this.getTarget();
        if (target == null || Rage.isRaged(this))
        {
            this.chaseFrom = null;
            return;
        }
        if (this.chaseFrom == null)
        {
            this.chaseFrom = this.position();
            return;
        }
        if (this.position().subtract(this.chaseFrom).horizontalDistance() > CinderMobTuning.CHASE_LEASH)
        {
            Vec3 back = this.chaseFrom;
            this.giveUp();
            this.getNavigation().moveTo(back.x, back.y, back.z, this.wanderSpeed());
        }
    }

    /** Stops being angry and drops its target (and the target goals that would pick it again the next tick). */
    public void giveUp()
    {
        this.stopBeingAngry();
        this.targetSelector.getRunningGoals().toList().forEach(WrappedGoal::stop);
        this.setTarget(null);
        this.chaseFrom = null;
    }

    /** For the tests: where its chase began (as if it had already chased its target from there). */
    void setChaseFromForTest(Vec3 from)
    {
        this.chaseFrom = from;
    }

    // ---------------------------------------------------------------- coming into the world

    /** Settles it where its whole body fits ({@link #settle}), then Minecraft's own. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        this.settle(level);
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /**
     * Natural spawning only makes room for a small box ({@code ModEntities}); its whole body (the hermit's legs' spread,
     * the Pallbearer's height) is moved to the nearest spot within {@link CinderMobTuning#SPAWN_SHIFT} blocks sideways
     * and {@link CinderMobTuning#SPAWN_LIFT} up where it's clear of blocks and fluids, with something under its middle
     * (it falls onto it). Where there's none, it stays (a natural spawn is called off then: {@link CinderMobEvents}; one
     * from an egg or a command walks out of the bumps, and walls never smother it).
     *
     * @return true if its whole body is clear where it ends up
     */
    public boolean settle(LevelAccessor level)
    {
        AABB box = this.getBoundingBox();
        if (this.fits(level, box))
        {
            return true;
        }
        for (int ring = 0; ring <= CinderMobTuning.SPAWN_SHIFT; ring++)
        {
            for (double dy = 0.0D; dy <= CinderMobTuning.SPAWN_LIFT + 1.0E-6D; dy += 0.5D)
            {
                for (int dx = -ring; dx <= ring; dx++)
                {
                    for (int dz = -ring; dz <= ring; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || (ring == 0 && dy == 0.0D))
                        {
                            continue;
                        }
                        AABB moved = box.move(dx, dy, dz);
                        if (this.fits(level, moved) && this.groundUnder(level, moved))
                        {
                            this.setPos(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** True if nothing solid and no fluid is in {@code box}. */
    private boolean fits(LevelAccessor level, AABB box)
    {
        return level.noCollision(this, box) && !level.containsAnyLiquid(box);
    }

    /** True if something solid is under the middle of {@code box} within {@link CinderMobTuning#SPAWN_GROUND_BELOW}. */
    private boolean groundUnder(LevelAccessor level, AABB box)
    {
        double x = (box.minX + box.maxX) / 2.0D;
        double z = (box.minZ + box.maxZ) / 2.0D;
        return !level.noCollision(this, new AABB(x - 0.3D, box.minY - CinderMobTuning.SPAWN_GROUND_BELOW, z - 0.3D,
                x + 0.3D, box.minY, z + 0.3D));
    }

    // ---------------------------------------------------------------- whom it fights

    /** Never its own kind (unless the Rage Wand has set them on each other). */
    @Override
    public boolean canAttack(LivingEntity target)
    {
        if (target.getClass() == this.getClass() && !Rage.areEnemies(this, target))
        {
            return false;
        }
        return super.canAttack(target);
    }

    /** Whether one of its blows may land on {@code e}: anything alive but itself, its own kind, a teammate (the Rage
     *  Wand's), a spectator or a player in creative. */
    protected boolean mayHit(LivingEntity e)
    {
        if (e == this || !e.isAlive() || e.isSpectator() || e instanceof Player player && player.isCreative())
        {
            return false;
        }
        if (Rage.areTeammates(this, e))
        {
            return false;
        }
        return !(e.getClass() == this.getClass() && !Rage.areEnemies(this, e));
    }

    /** Everything one of its blows may land on in {@code box}. */
    protected List<LivingEntity> hittable(AABB box)
    {
        return this.level().getEntitiesOfClass(LivingEntity.class, box, this::mayHit);
    }

    /** True if {@code e} is within {@code reach} blocks of its middle (along the ground; its body's far edge counts),
     *  in front of it (within the arc whose widest angle has cosine {@code arcCos}), and about its height. */
    protected boolean inArc(LivingEntity e, double reach, double arcCos)
    {
        double dx = e.getX() - this.getX();
        double dz = e.getZ() - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz) - e.getBbWidth() / 2.0D;
        if (distance > reach || e.getY() > this.getY() + this.getBbHeight() || e.getY() + e.getBbHeight() < this.getY() - 1.0D)
        {
            return false;
        }
        if (distance < this.getBbWidth() * 0.25D)
        {
            return true;
        }
        Vec3 facing = this.facing();
        return (dx * facing.x + dz * facing.z) / Math.max(1.0E-4D, Math.sqrt(dx * dx + dz * dz)) >= arcCos;
    }

    /** The way its body faces, along the ground. */
    protected Vec3 facing()
    {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /** Turns it (body and head) towards a spot, at most {@code maxTurn} degrees this tick. */
    protected void turnTowards(Vec3 spot, float maxTurn)
    {
        double dx = spot.x - this.getX();
        double dz = spot.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D)
        {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float turned = Mth.approachDegrees(this.getYRot(), yaw, maxTurn);
        this.setYRot(turned);
        this.setYBodyRot(turned);
        this.setYHeadRot(turned);
    }

    /** Throws {@code e} away from {@code from} (along the ground) and up, as much as its knockback resistance lets. */
    protected static void throwFrom(LivingEntity e, Vec3 from, double out, double up)
    {
        Vec3 away = e.position().subtract(from).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        double resist = 1.0D - Mth.clamp(e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
        if (resist <= 0.0D)
        {
            return;
        }
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.5D + away.x * out * resist, Math.max(v.y, up * resist), v.z * 0.5D + away.z * out * resist);
        e.hurtMarked = true;
    }

    // ---------------------------------------------------------------- being angry (a neutral mob)

    @Override
    public void startPersistentAngerTimer()
    {
        this.setRemainingPersistentAngerTime(ANGER.sample(this.random));
    }

    @Override
    public void setRemainingPersistentAngerTime(int time)
    {
        this.remainingPersistentAngerTime = time;
    }

    @Override
    public int getRemainingPersistentAngerTime()
    {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target)
    {
        this.persistentAngerTarget = target;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget()
    {
        return this.persistentAngerTarget;
    }

    // ---------------------------------------------------------------- its animation: a move, or its walk or idle

    /** (server) Back to its walk or idle (whichever fits how it moves), after a move. */
    protected void endMove()
    {
        FireAnims.Mob mob = this.anims();
        if (mob != null && !this.animation().equals(mob.idle()))
        {
            this.playAnimation(mob.idle());
        }
    }

    /** (server) True while it makes a move (its synced animation isn't its idle). */
    protected boolean inMove()
    {
        FireAnims.Mob mob = this.anims();
        return mob != null && !this.animation().equals(mob.idle());
    }

    /** The length of one of its animations, in ticks. */
    protected int ticksOf(String animation)
    {
        FireAnims.Mob mob = this.anims();
        FireAnims.Anim anim = mob == null ? null : mob.get(animation);
        return anim == null ? 20 : Math.max(1, Math.round(anim.seconds() * 20.0F));
    }

    /** (client) Its walk while it moves and no move plays; otherwise what the server says. */
    @Override
    protected String shownAnimation()
    {
        String now = this.animation();
        FireAnims.Mob mob = this.anims();
        if (mob != null && now.equals(mob.idle()) && !this.isDeadOrDying() && mob.get("walk") != null
                && RimefallAnims.groundSpeed(this) > CinderMobTuning.MOVING)
        {
            return "walk";
        }
        return now;
    }

    /** (client) Its walk as fast as it really moves. */
    @Override
    protected double shownAnimationSpeed()
    {
        if (!"walk".equals(this.shownAnimation()))
        {
            return 1.0D;
        }
        return Mth.clamp(RimefallAnims.groundSpeed(this) / this.walkPace(), CinderMobTuning.MIN_WALK_SPEEDUP,
                CinderMobTuning.MAX_WALK_SPEEDUP);
    }

    // ---------------------------------------------------------------- alive, not an exhibit

    /** Hurt like any mob (fire and lava never: its type is fire-immune), but never smothered in a wall: a giant's
     *  "in a wall" square at eye height is most of its width, and brushing a cliff would choke it. ({@link FireCandidate}'s
     *  exhibits only die to {@code /kill}: this is Minecraft's own rule again.) */
    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        if (source.is(DamageTypes.IN_WALL))
        {
            return true;
        }
        return this.isRemoved() || this.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !source.isCreativePlayer();
    }

    /** It despawns like any monster: far from every player (never with a name tag). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return true;
    }

    @Override
    public boolean shouldDespawnInPeaceful()
    {
        return true;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        return InteractionResult.PASS;
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (!this.level().isClientSide && this.dead)
        {
            this.getNavigation().stop();
        }
    }

    /** Plays its death animation (started in its own tick, so the death cry, its first sound cue, isn't missed), lies
     *  through it (vanilla removes a dead mob after a second), then goes in a puff of smoke. */
    @Override
    protected void tickDeath()
    {
        this.deathTime++;
        if (!this.level().isClientSide() && this.deathTime == 1)
        {
            this.playAnimation("death");
        }
        if (!this.level().isClientSide() && !this.isRemoved()
                && this.deathTime >= this.ticksOf("death") + CinderMobTuning.DEATH_LINGER_TICKS)
        {
            this.level().broadcastEntityEvent(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    // ---------------------------------------------------------------- sounds

    protected static SoundEvent sound(String event)
    {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(Dross.MODID, event));
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound()
    {
        return sound(this.soundPrefix() + ".ambient");
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return sound(this.soundPrefix() + ".hurt");
    }

    /** (its death animation's own: {@code FireSounds.CUES}, the user's pick) */
    @Nullable
    @Override
    protected SoundEvent getDeathSound()
    {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        this.playSound(sound(this.soundPrefix() + ".step"), this.getSoundVolume() * 0.6F, 1.0F);
    }

    /** A footfall every stride of its own (vanilla's is every 1.7 blocks). */
    @Override
    protected float nextStep()
    {
        return this.moveDist + (float) (this.stepLength() * 0.6D);
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.readPersistentAngerSaveData(this.level(), tag);
    }

    /** The cinder mob design with this label ("M1A", "M2C"). */
    protected static FireDesigns.Design design(String label)
    {
        for (FireDesigns.Design d : FireDesigns.CINDER_MOBS)
        {
            if (d.label().equals(label))
            {
                return d;
            }
        }
        throw new IllegalArgumentException("no Cinder Wastes mob design " + label);
    }

    /** Minecraft's move control, but a giant turns at most {@code maxTurn} degrees a tick as it walks (vanilla's 90 a
     *  tick swings a 6-block crab round in an instant). */
    private static final class TurningMoveControl extends MoveControl
    {
        private final float maxTurn;

        TurningMoveControl(Mob mob, float maxTurn)
        {
            super(mob);
            this.maxTurn = maxTurn;
        }

        @Override
        protected float rotlerp(float from, float to, float max)
        {
            return super.rotlerp(from, to, Math.min(max, this.maxTurn));
        }
    }
}
