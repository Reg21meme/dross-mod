package com.reg21meme.dross.boss.huntsman;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What the Huntsman and the Alpha Moose share (CLAUDE.md step 14, his fight): their GeckoLib animations, picked by
 * name on the server and synced (with a counter, so the same one asked for again starts over).
 * <p>
 * <b>On show</b> ({@code /dross showcase boss_designs huntsman}, {@link #setShowcase}): frozen (no AI), they can't be
 * hurt except by {@code /kill}, can't be pushed or leashed and never despawn; a move plays once and then they go back
 * to their first animation (standing) after {@link #HOLD_AFTER} ticks, except a death, which stays down. His shots loose
 * real arrows from his bow ({@link HuntCues}), harmless on show, and a few moments make a placeholder sound
 * ({@link HuntShowSounds}) until the real sounds are picked.
 * <p>
 * <b>Fighting</b> (a spawn egg, {@code /dross boss summon huntsman}): {@link HuntFight} runs their fight (the stage,
 * the bars, the split, the anger, the end), their goals pick the moves ({@link WildHuntGoal}, {@link FootGoal},
 * {@link MooseGoal}), a move goes straight back to walking or standing when it ends, and his arrows fly at his target
 * and hurt.
 */
public abstract class HuntMob extends PathfinderMob implements GeoEntity
{
    /** A move's last frame is held this many ticks before it goes back to standing (on show). */
    public static final int HOLD_AFTER = 20;
    /** GeckoLib's blend between two animations (ticks). */
    protected static final int BLEND_TICKS = 4;
    /** His arrows' speed (a fully drawn bow shoots at 3), and the arrow rain's (into the sky, so they come down near). */
    public static final float ARROW_SPEED = 3.0F;
    public static final float VOLLEY_SPEED = 1.8F;
    /** Bigger moves than this (blocks) are shown at once ({@link #lerpTo}). */
    public static final double SNAP = 1.0D;
    /** A shot this steep (its direction's up part) is a volley into the sky. */
    private static final double VOLLEY_UP = 0.6D;
    /** His aimed arrows spread this much (vanilla's skeleton on Normal: about 6). */
    private static final float AIM_SPREAD = 1.0F;
    private static final String TAG_ANIM = "Anim";
    private static final String TAG_SHOWCASE = "Showcase";
    private static final String TAG_PARTNER = "Partner";
    private static final String TAG_STAGE = "Stage";
    private static final String TAG_WILD_HUNT = "WildHunt";
    private static final String TAG_ANGRY = "Angry";

    private static final EntityDataAccessor<String> DATA_ANIM = SynchedEntityData.defineId(HuntMob.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_COUNT = SynchedEntityData.defineId(HuntMob.class,
            EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int seenCount = -1;
    private int playing;
    private String later;
    private int laterTicks;

    // ---------------------------------------------------------------- the fight's state (HuntFight keeps it)
    private boolean showcase;
    @Nullable
    UUID partnerId;
    HuntStage stage = HuntStage.CALM;
    /** The Wild Hunt's bar (the moose keeps it for both). */
    float wildHunt = HuntsmanTuning.WILD_HUNT_HEALTH;
    boolean angry;
    int stageTicks;
    int lonelyTicks;
    @Nullable
    ServerBossEvent bar;
    @Nullable
    UUID creditPlayer;
    int creditTick;
    /** What his next arrow aims at, its base damage and what its hit does (his goals set them before a shot). */
    @Nullable
    LivingEntity shotTarget;
    double shotBase;
    HuntFight.Frost shotFrost = HuntFight.Frost.NONE;
    /** Things due later: the arrow rain coming down, the cracks running over the ice, volley arrows to clear away. */
    final List<HuntFight.Pending> pending = new ArrayList<>();
    /** A loop the fight wants played in place of walking or standing (the charge's gallop), or null. */
    @Nullable
    String forcedLoop;

    protected HuntMob(EntityType<? extends HuntMob> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        this.setMaxUpStep(1.0F);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIM, "");
        this.entityData.define(DATA_COUNT, 0);
    }

    /** On show: frozen and unhurtable (the showcase sets it before it adds them). */
    public void setShowcase(boolean showcase)
    {
        this.showcase = showcase;
        this.setNoAi(showcase);
    }

    public boolean isShowcase()
    {
        return this.showcase;
    }

    public HuntStage stage()
    {
        return this.stage;
    }

    public boolean isAngry()
    {
        return this.angry;
    }

    /** The animations it has right now (the Huntsman's change when he's in the saddle). */
    public abstract List<HuntAnims.Anim> animations();

    /** The animation playing (its first, standing, if none was asked for or the one asked for isn't one of its own). */
    public HuntAnims.Anim current()
    {
        HuntAnims.Anim a = HuntAnims.named(this.animations(), this.entityData.get(DATA_ANIM));
        return a != null ? a : this.animations().get(0);
    }

    /** True while it's playing a move (not a loop: standing, walking, running). */
    public boolean busy()
    {
        return !this.current().loop();
    }

    /** Goes up by one every time an animation starts (both sides): the Huntsman's saddle follows the moose's. */
    public int animCount()
    {
        return this.entityData.get(DATA_COUNT);
    }

    /** Ticks since the current animation started (server side). */
    public int playingTicks()
    {
        return this.playing;
    }

    /** Plays one of its animations (server side). Returns false if it has none of that name. */
    public boolean play(String name)
    {
        if (HuntAnims.named(this.animations(), name) == null)
        {
            return false;
        }
        this.later = null;
        this.entityData.set(DATA_ANIM, name);
        this.entityData.set(DATA_COUNT, this.entityData.get(DATA_COUNT) + 1);
        this.playing = 0;
        return true;
    }

    /** Plays a loop unless it's already playing it (a loop asked for again doesn't start over). */
    public void loop(String name)
    {
        if (!this.current().name().equals(name))
        {
            this.play(name);
        }
    }

    /** Plays one of its animations {@code delay} ticks from now (server side). Returns false if it has none of that
     * name. */
    public boolean playLater(String name, int delay)
    {
        if (HuntAnims.named(this.animations(), name) == null)
        {
            return false;
        }
        if (delay <= 0)
        {
            return this.play(name);
        }
        this.later = name;
        this.laterTicks = delay;
        return true;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide)
        {
            if (this.later != null && --this.laterTicks <= 0)
            {
                this.play(this.later);
            }
            HuntAnims.Anim a = this.current();
            if (!a.loop())
            {
                this.playing++;
                for (HuntCues.Shot shot : HuntCues.shots(a.model(), a.name()))
                {
                    if (shot.tick() == this.playing)
                    {
                        this.loose(shot);
                    }
                }
                HuntShowSounds.play(this, a, this.playing);
                int end = this.showcase ? a.ticks() + HOLD_AFTER : a.ticks();
                if (!a.stays() && this.playing > end)
                {
                    this.moveBy(HuntCues.shift(a.model(), a.name()));
                    this.entityData.set(DATA_ANIM, "");
                    this.entityData.set(DATA_COUNT, this.entityData.get(DATA_COUNT) + 1);
                    this.playing = 0;
                    this.onMoveEnded(a);
                }
            }
            if (!this.showcase)
            {
                HuntFight.tick(this);
            }
        }
    }

    /** A move just ended (server side): the Huntsman's landing is followed by his horn at the split. */
    protected void onMoveEnded(HuntAnims.Anim move)
    {
    }

    /** Moves it where an animation that carries it (the backstep shot's leap) left it: blocks to its right, up and
     * ahead (its body's facing), in the same tick as it goes back to standing, so it's never seen jump back. Fighting,
     * not into a wall: it goes as far back as there's room. */
    private void moveBy(double[] shift)
    {
        if (shift == null)
        {
            return;
        }
        double yaw = Math.toRadians(this.yBodyRot);
        for (double part = 1.0D; part > 0.0D; part -= 1.0D / 3.0D)
        {
            double x = this.getX() - (Math.cos(yaw) * shift[0] + Math.sin(yaw) * shift[2]) * part;
            double z = this.getZ() - (Math.sin(yaw) * shift[0] - Math.cos(yaw) * shift[2]) * part;
            double y = this.getY() + shift[1] * part;
            if (this.showcase || this.level().noCollision(this, this.getBoundingBox().move(x - this.getX(), y - this.getY(),
                    z - this.getZ())))
            {
                this.moveTo(x, y, z, this.getYRot(), this.getXRot());
                return;
            }
        }
    }

    /**
     * A move of more than {@link #SNAP} blocks in one go (the leap off the moose, the backstep's landing) is shown at
     * once, not slid over the next ticks the way the game smooths other moves (the user saw him slide sideways as he
     * landed).
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport)
    {
        if (this.position().distanceToSqr(x, y, z) > SNAP * SNAP)
        {
            this.moveTo(x, y, z, yRot, xRot);
            super.lerpTo(x, y, z, yRot, xRot, 1, teleport);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    /** A real arrow off his bow, where the animation's arrow leaves it. On show (and a volley into the sky) it can't
     * hurt: it has no damage, so it glances off a player. Fighting, it flies at his target and hurts. */
    private void loose(HuntCues.Shot shot)
    {
        double yaw = Math.toRadians(this.yBodyRot);
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        Vec3 from = this.position().add(right.scale(shot.right())).add(0.0D, shot.up(), 0.0D)
                .add(ahead.scale(shot.ahead()));
        Vec3 dir = right.scale(shot.dirRight()).add(0.0D, shot.dirUp(), 0.0D).add(ahead.scale(shot.dirAhead()))
                .normalize();
        boolean volley = shot.dirUp() > VOLLEY_UP;
        Arrow arrow = new Arrow(this.level(), from.x, from.y, from.z);
        arrow.setOwner(this);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        LivingEntity target = this.shotTarget;
        if (this.showcase || volley || target == null || !target.isAlive())
        {
            arrow.setBaseDamage(0.0D);
            arrow.shoot(dir.x, dir.y, dir.z, volley ? VOLLEY_SPEED : ARROW_SPEED, 0.0F);
            if (!this.showcase)
            {
                HuntFight.clearLater(this, arrow);
            }
        }
        else
        {
            Vec3 aim = HuntFight.aimAt(from, target, ARROW_SPEED);
            arrow.setBaseDamage(this.shotBase * HuntFight.damageScale(this));
            arrow.shoot(aim.x, aim.y, aim.z, ARROW_SPEED, AIM_SPREAD);
            HuntFight.mark(arrow, this.shotFrost);
        }
        this.level().addFreshEntity(arrow);
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
    }

    // ---------------------------------------------------------------- being hurt, dying

    /** On show only {@code /kill} hurts them; fighting, the split's moment can't be hurt either. */
    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return super.isInvulnerableTo(source);
        }
        return this.showcase || this.stage == HuntStage.SPLIT || this.stage == HuntStage.WON || super.isInvulnerableTo(source);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.showcase || this.level().isClientSide)
        {
            return super.hurt(source, amount);
        }
        if (HuntFight.friendly(this, source))
        {
            return false;
        }
        boolean hit = super.hurt(source, amount);
        if (hit)
        {
            HuntFight.onHurt(this, source);
        }
        return hit;
    }

    /** In stage 1 (and its first hit) what they lose goes on the one bar they share, not their own health. */
    @Override
    protected void actuallyHurt(DamageSource source, float amount)
    {
        if (this.showcase || !this.stage.sharesBar() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            super.actuallyHurt(source, amount);
            return;
        }
        float before = this.getHealth();
        super.actuallyHurt(source, amount);
        float lost = before - this.getHealth();
        this.setHealth(before);
        HuntFight.damageWildHunt(this, lost);
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (!this.level().isClientSide && this.dead && !this.showcase)
        {
            HuntFight.onDeath(this, source);
        }
    }

    /** Fighting, a body lies through its death and {@link HuntsmanTuning#CORPSE_TICKS} more, then it's gone in a puff
     * of snow (on show, the vanilla way). */
    @Override
    protected void tickDeath()
    {
        if (this.showcase)
        {
            super.tickDeath();
            return;
        }
        this.deathTime = Math.min(this.deathTime + 1, Short.MAX_VALUE);
        HuntAnims.Anim death = HuntAnims.named(this.animations(), "death");
        int end = (death != null ? death.ticks() : 20) + HuntsmanTuning.CORPSE_TICKS;
        if (!this.level().isClientSide && !this.isRemoved() && this.deathTime >= end)
        {
            HuntFight.vanish(this);
        }
    }

    @Override
    public int getExperienceReward()
    {
        return this.showcase ? 0 : HuntsmanTuning.XP_EACH;
    }

    @Override
    public void remove(RemovalReason reason)
    {
        super.remove(reason);
        if (this.bar != null)
        {
            this.bar.removeAllPlayers();
        }
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
        if (this.bar != null)
        {
            this.bar.removePlayer(player);
        }
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(double x, double y, double z)
    {
    }

    @Override
    public boolean isPushedByFluid()
    {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    /** Fighting, they're gone on Peaceful like any monster (on show they stay). */
    @Override
    protected boolean shouldDespawnInPeaceful()
    {
        return !this.showcase;
    }

    @Override
    public boolean canFreeze()
    {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source)
    {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_ANIM, this.entityData.get(DATA_ANIM));
        tag.putBoolean(TAG_SHOWCASE, this.showcase);
        if (this.partnerId != null)
        {
            tag.putUUID(TAG_PARTNER, this.partnerId);
        }
        tag.putString(TAG_STAGE, this.stage.name());
        tag.putFloat(TAG_WILD_HUNT, this.wildHunt);
        tag.putBoolean(TAG_ANGRY, this.angry);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_ANIM, tag.getString(TAG_ANIM));
        // older exhibits had no flag: they were all on show (noAI)
        this.setShowcase(tag.contains(TAG_SHOWCASE) ? tag.getBoolean(TAG_SHOWCASE) : this.isNoAi());
        this.partnerId = tag.hasUUID(TAG_PARTNER) ? tag.getUUID(TAG_PARTNER) : null;
        try
        {
            this.stage = HuntStage.valueOf(tag.getString(TAG_STAGE));
        }
        catch (IllegalArgumentException e)
        {
            this.stage = HuntStage.CALM;
        }
        // a fight left at its split goes on with the duel
        if (this.stage == HuntStage.SPLIT)
        {
            this.stage = HuntStage.DUEL;
        }
        this.wildHunt = tag.contains(TAG_WILD_HUNT) ? tag.getFloat(TAG_WILD_HUNT) : HuntsmanTuning.WILD_HUNT_HEALTH;
        this.angry = tag.getBoolean(TAG_ANGRY);
    }

    /** Where it stands (its feet's block). */
    BlockPos feet()
    {
        return this.blockPosition();
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<HuntMob> state)
    {
        int count = this.entityData.get(DATA_COUNT);
        if (count != this.seenCount)
        {
            this.seenCount = count;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(this.current().raw());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }
}
