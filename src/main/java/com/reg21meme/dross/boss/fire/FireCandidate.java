package com.reg21meme.dross.boss.fire;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One fire candidate on show: a model of one of the fire designs ({@link FireDesigns}, made by
 * {@code tools/fire/build.py}) for the showcases ({@code /dross showcase boss_designs cinder_wastes},
 * {@code ... emberdactyl}, {@code /dross showcase cinder_mobs}). It never walks (no AI), makes no sound and never fights;
 * a design that moves (its {@code mob}: the Emberdactyl, its young, the Pallbearer, the Cinder Hermit) plays its
 * animations on the spot ({@link FireAnims}, made by {@code tools/fire/mobs.py}): its mob's idle until the showcase's
 * controls or a right-click play another; the others are frozen statues.
 * <p>
 * Clients get the design number; the hitbox follows the design (its measured size), and the area it's drawn for reaches
 * as far as the model does, so the big ones never vanish when their feet leave the screen. It can't be hurt except by
 * {@code /kill} (damage that bypasses invulnerability), can't be pushed or leashed, never despawns (not even on
 * Peaceful) and doesn't burn. Right-clicking it shows its number, name and look on the action bar (and plays its next
 * animation, if it moves).
 */
public class FireCandidate extends PathfinderMob implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_DESIGN = SynchedEntityData.defineId(FireCandidate.class,
            EntityDataSerializers.INT);
    /** The animation it plays ("" for its mob's idle) and a count that goes up every time one is asked for, so asking
     * again for the same one plays it again. */
    private static final EntityDataAccessor<String> DATA_ANIM = SynchedEntityData.defineId(FireCandidate.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_ANIM_COUNT = SynchedEntityData.defineId(FireCandidate.class,
            EntityDataSerializers.INT);
    private static final String TAG_DESIGN = "Design";
    private static final String TAG_ANIM = "Anim";
    /** Ticks GeckoLib blends from one animation into the next. */
    private static final int BLEND_TICKS = 4;
    /** Every animation's GeckoLib form, made once: "animation.<mob>.<name>". */
    private static final Map<String, RawAnimation> RAW = new HashMap<>();
    /** Blocks added round the model's reach for the area it's drawn for. */
    private static final double CULL_MARGIN = 1.0D;
    /** A design that moves may reach this much further than its rest pose. */
    private static final double ANIMATED_REACH = 1.35D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    /** (client) the count of the animation the controller last started */
    private int playedCount = -1;
    /** (server) the tick its animation was asked for, or -1 (nothing asked for yet: its idle plays quietly) */
    private int animStart = -1;
    /** (server) which version of each sound it played last (its versions come in turn: 1, 2, 3, 1...) */
    private final Map<String, Integer> soundTurns = new HashMap<>();
    /** Each animation's sound cues ("mob/animation"), and each sound event, looked up once. */
    private static final Map<String, List<FireSounds.Cue>> CUES = new HashMap<>();
    private static final Map<String, SoundEvent> SOUNDS = new HashMap<>();
    /** How loud each mob is (a sound's range is 16 blocks times this): the giant's screech carries a long way. */
    private static final Map<String, Float> VOLUME = Map.of("emberdactyl", 4.0F, "emberyoung", 3.0F,
            "pallbearer", 2.0F, "hermit", 1.2F);

    public FireCandidate(EntityType<? extends FireCandidate> type, Level level)
    {
        this(type, level, true);
    }

    /**
     * @param exhibit true for a model on show (no AI, silent, never despawning); false for a living mob built on the fire
     *                models (the Cinder Wastes' Pallbearer and Cinder Hermit, {@code boss/cinder}): it keeps its AI and
     *                its voice and despawns like any mob. (A PathfinderMob, so the living ones can walk and use
     *                Minecraft's goals; the exhibits have no AI.)
     */
    protected FireCandidate(EntityType<? extends FireCandidate> type, Level level, boolean exhibit)
    {
        super(type, level);
        if (exhibit)
        {
            this.setNoAi(true);
            this.setSilent(true);
            this.setPersistenceRequired();
        }
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_DESIGN, 1);
        this.entityData.define(DATA_ANIM, "");
        this.entityData.define(DATA_ANIM_COUNT, 0);
    }

    /** The design it shows. Works on both sides. */
    public FireDesigns.Design getDesign()
    {
        return FireDesigns.byNumber(this.entityData.get(DATA_DESIGN));
    }

    public void setDesign(int number)
    {
        this.entityData.set(DATA_DESIGN, FireDesigns.byNumber(number).number());
        this.entityData.set(DATA_ANIM, "");
        this.refreshDimensions();
    }

    // ---------------------------------------------------------------- its animations (the designs that move)

    /** Its mob's animations, or null for a frozen statue. */
    @Nullable
    public FireAnims.Mob anims()
    {
        FireDesigns.Design design = this.getDesign();
        return design.animated() ? FireAnims.get(design.mob()) : null;
    }

    /** The animation it plays now (its mob's idle when nothing else was asked for), or "" for a frozen statue. */
    public String animation()
    {
        FireAnims.Mob mob = this.anims();
        if (mob == null)
        {
            return "";
        }
        String name = this.entityData.get(DATA_ANIM);
        return mob.get(name) != null ? name : mob.idle();
    }

    /**
     * Plays one of its animations from the start (again, if it's the one playing). A loop keeps going; a move holds
     * its last pose until another is asked for.
     *
     * @return false if it has no animation of that name (a frozen statue has none)
     */
    public boolean playAnimation(String name)
    {
        FireAnims.Mob mob = this.anims();
        if (mob == null || mob.get(name) == null)
        {
            return false;
        }
        this.entityData.set(DATA_ANIM, name);
        this.entityData.set(DATA_ANIM_COUNT, this.entityData.get(DATA_ANIM_COUNT) + 1);
        this.animStart = this.tickCount;
        return true;
    }

    /** (server) Plays the sounds its animation makes at this moment ({@link FireSounds#CUES}; a loop's every time
     * round). Its idle stays quiet until an animation has been asked for. */
    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide || this.animStart < 0)
        {
            return;
        }
        FireAnims.Mob mob = this.anims();
        if (mob == null)
        {
            return;
        }
        FireAnims.Anim anim = mob.get(this.animation());
        int length = Math.max(1, Math.round(anim.seconds() * 20.0F));
        int elapsed = this.tickCount - this.animStart;
        if (!anim.loop() && elapsed > length)
        {
            return;
        }
        int at = anim.loop() ? elapsed % length : elapsed;
        for (FireSounds.Cue cue : CUES.computeIfAbsent(mob.id() + "/" + anim.name(), key -> FireSounds.CUES.stream()
                .filter(c -> (c.mob() + "/" + c.anim()).equals(key)).toList()))
        {
            if (cue.tick() == at)
            {
                SoundEvent sound = SOUNDS.computeIfAbsent(this.versionOf(cue.event()), event ->
                        SoundEvent.createVariableRangeEvent(new ResourceLocation(Dross.MODID, event)));
                this.level().playSound(null, this.getX(), this.getY() + this.getBbHeight() / 2.0D, this.getZ(), sound,
                        SoundSource.HOSTILE, this.cueVolume(mob, cue.event()), 1.0F);
            }
        }
    }

    /** How loud a cue's sound is (a sound's range is 16 blocks times this): the mob's own loudness (the fighting
     *  Emberdactyl's screech carries further: {@code EmberBoss}). */
    protected float cueVolume(FireAnims.Mob mob, String event)
    {
        return VOLUME.getOrDefault(mob.id(), 1.0F);
    }

    /** The event a cue plays now: the sound's versions in turn (this model's own count; the user's request), or the
     * one version it always plays until the user picks ({@link FireSounds.Sound#pick}). */
    private String versionOf(String event)
    {
        FireSounds.Sound sound = FireSounds.Sound.of(event);
        if (sound == null || sound.versions() < 2)
        {
            return event;
        }
        if (sound.pick() > 0)
        {
            return sound.version(sound.pick());
        }
        return sound.version(this.soundTurns.merge(event, 1, (last, one) -> last % sound.versions() + 1));
    }

    /** Plays the animation after the one it plays now (in its mob's list, round to the first). */
    public void playNextAnimation()
    {
        FireAnims.Mob mob = this.anims();
        if (mob == null)
        {
            return;
        }
        List<FireAnims.Anim> list = mob.anims();
        int at = 0;
        for (int i = 0; i < list.size(); i++)
        {
            if (list.get(i).name().equals(this.animation()))
            {
                at = i;
            }
        }
        this.playAnimation(list.get((at + 1) % list.size()).name());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_DESIGN.equals(key))
        {
            this.refreshDimensions();
        }
        if (DATA_ANIM_COUNT.equals(key))
        {
            this.animSeenTick = this.tickCount;
        }
    }

    // ---------------------------------------------------------------- (client) its animation's time, for its death looks

    /** (client) the tick its animation count last changed (an animation's start isn't synced, so it's counted here) */
    private int animSeenTick;

    /** (client) Seconds into the animation it plays now, as GeckoLib plays it (after its blend into it). Used by the
     * Emberdactyl's death looks ({@code client/FireDeathLook}). */
    public float animationSeconds(float partialTick)
    {
        return Math.max(0.0F, (this.tickCount + partialTick - this.animSeenTick - BLEND_TICKS) / 20.0F);
    }

    /** The hitbox: the design's measured size (the very wide ones get their body's width: a hitbox is square). */
    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        FireDesigns.Design design = this.getDesign();
        return EntityDimensions.scalable(design.width(), design.height());
    }

    /** Drawn whenever any part of the model could be on screen: its whole reach round its feet, up to its top (one
     * that moves reaches further: its wings beat up past its top, it rears, it leans). */
    @Override
    public AABB getBoundingBoxForCulling()
    {
        FireDesigns.Design design = this.getDesign();
        double reach = design.reach() + CULL_MARGIN;
        double top = design.top() + CULL_MARGIN;
        if (design.animated())
        {
            top += reach * (ANIMATED_REACH - 1.0D) + CULL_MARGIN;
            reach *= ANIMATED_REACH;
        }
        return new AABB(this.getX() - reach, this.getY() - CULL_MARGIN, this.getZ() - reach,
                this.getX() + reach, this.getY() + top, this.getZ() + reach);
    }

    // ---------------------------------------------------------------- a frozen exhibit

    /** Nothing hurts it except damage that bypasses invulnerability ({@code /kill}, the void). */
    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
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

    /** Right-click: its number, name and look (and how tall it is) on the action bar. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        FireDesigns.Design design = this.getDesign();
        if (design.animated())
        {
            this.playNextAnimation();
            player.displayClientMessage(Component.translatable("dross.fire.showcase.info_anim", design.label(),
                    design.name(), design.mood(), this.animation()), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("dross.fire.showcase.info", design.label(), design.name(),
                design.mood(), String.format(Locale.ROOT, "%.1f", design.top())), true);
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------- GeckoLib (a statue: none; one that moves: its mob's)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<FireCandidate> state)
    {
        FireAnims.Mob mob = this.anims();
        if (mob == null)
        {
            return PlayState.STOP;
        }
        int count = this.entityData.get(DATA_ANIM_COUNT);
        if (count != this.playedCount)
        {
            this.playedCount = count;
            state.getController().forceAnimationReset();
        }
        FireAnims.Anim shown = mob.get(this.shownAnimation());
        FireAnims.Anim anim = shown != null ? shown : mob.get(this.animation());
        state.getController().setAnimationSpeed(this.shownAnimationSpeed());
        String key = "animation." + mob.id() + "." + anim.name();
        RawAnimation raw = RAW.computeIfAbsent(key, k -> anim.loop() ? RawAnimation.begin().thenLoop(k)
                : RawAnimation.begin().thenPlayAndHold(k));
        return state.setAndContinue(raw);
    }

    /** (client) The animation it shows now: the synced one. A living mob may show another (its walk while it walks:
     *  the Cinder Wastes' mobs, {@code boss/cinder/CinderMob}). */
    protected String shownAnimation()
    {
        return this.animation();
    }

    /** (client) How fast the animation it shows plays (a walk sped up or slowed down to its real speed, so its feet
     *  don't slide). */
    protected double shownAnimationSpeed()
    {
        return 1.0D;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    // ---------------------------------------------------------------- saving and spawning

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_DESIGN, this.getDesign().number());
        tag.putString(TAG_ANIM, this.entityData.get(DATA_ANIM));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_DESIGN))
        {
            this.setDesign(tag.getInt(TAG_DESIGN));
        }
        if (tag.contains(TAG_ANIM))
        {
            this.entityData.set(DATA_ANIM, tag.getString(TAG_ANIM));
        }
    }

    /**
     * Spawns a candidate on show.
     *
     * @param feet where its feet go (the bottom middle of the air block above the ground)
     * @param yaw  the way it faces, in degrees (0 = south, 90 = west, 180 = north, -90 = east)
     * @return the candidate, already in the world, or null if it couldn't be created
     */
    @Nullable
    public static FireCandidate spawnShowcase(ServerLevel level, Vec3 feet, float yaw, FireDesigns.Design design)
    {
        FireCandidate candidate = ModEntities.FIRE_CANDIDATE.get().create(level);
        if (candidate == null)
        {
            return null;
        }
        candidate.setDesign(design.number());
        candidate.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        candidate.setYHeadRot(yaw);
        candidate.setYBodyRot(yaw);
        candidate.yHeadRotO = yaw;
        candidate.yBodyRotO = yaw;
        level.addFreshEntity(candidate);
        return candidate;
    }
}
