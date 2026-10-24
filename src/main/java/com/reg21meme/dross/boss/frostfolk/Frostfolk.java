package com.reg21meme.dross.boss.frostfolk;

import com.reg21meme.dross.boss.huntsman.HuntMob;
import com.reg21meme.dross.boss.rimefall.PondSkater;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One of the Frostfolk, Rimefall's villagers (the user, October 23, 2026: "interactable, a unique voice that is nice,
 * each its own spawn egg"; their look, October 24: an original one with a pillager's build, no nose, blue hoods of
 * different styles, the elder scarred and bearded, simple clothes, capes and wolf fur, as detailed as the Huntsman):
 * one of eleven callings ({@link FolkRole}: five warriors, five workers and the elder), each its own entity type, all
 * one model ({@code tools/rimefall/rimemobs/frostfolk.py}), each calling its own texture. They live in Rimefall's
 * villages (put in each village's template with it, 1.5 to 2 for each bed: the elder in the meeting hall, the workers
 * at their work, the rest in their homes and round the fires; each keeps within {@link FolkTuning#HOME_RADIUS} blocks
 * of where it started), and come on show ({@code /dross showcase frostfolk}) and from their spawn eggs. They die the
 * vanilla way (the user: "just the regular death animation").
 * <ul>
 *   <li><b>Calm</b> they wander about, look at you, hum at their work; right-click one and it talks to you (a line in
 *       chat; the elder has the most to say).</li>
 *   <li><b>Hit one</b> and it fights back ({@link FolkTuning#HIT_ANGER_TICKS}); the elder stays angry until you've been
 *       {@link FolkTuning#ELDER_NEAR} blocks off for {@link FolkTuning#ELDER_FORGIVE_TICKS} (10 minutes).</li>
 *   <li><b>Kill one</b> and every villager within {@link FolkTuning#VILLAGE_RANGE} blocks turns on you for
 *       {@link FolkTuning#VILLAGE_ANGER_TICKS} (10 minutes), with a rallying shout (the user: "as soon as you kill one of
 *       them, the entire village turns on you and tries to kill you"); the horn-blower blows his horn, the others get
 *       Speed and Strength from it.</li>
 *   <li>Warriors fight with their own moves (the spear's thrust and sweep, the axe's chop, the pike's longer thrust,
 *       the archer's bow); workers with their tools; the elder with his sword. They hunt Pond Skaters, and Pond Skaters
 *       hunt them (the user: "these guys are all hostile to spiders and spiders will attack these guys").</li>
 * </ul>
 */
public class Frostfolk extends PathfinderMob implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(Frostfolk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_COUNT = SynchedEntityData.defineId(Frostfolk.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(Frostfolk.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_SHOWCASE = "Showcase";
    private static final String TAG_ANGER = "Anger";
    private static final String TAG_ELDER = "ElderAnger";
    /** Written on a village's Frostfolk by its template (tools/huntsman/village_designs/warren.py): on its first tick it
     * takes the spot it stands on as its home. */
    private static final String TAG_VILLAGE = "VillageFolk";
    private static final String TAG_HOME = "Home";
    private static final int BLEND_TICKS = 4;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final FolkRole role;
    private int seenActionCount = -1;
    private int actionEnd = -1;
    private int actionStart = -1;
    /** Players it's angry at, and until when (game time). */
    private final Map<UUID, Long> angry = new HashMap<>();
    /** The elder: players he's angry at, and how long each has been away. */
    private final Map<UUID, Integer> elderAngry = new HashMap<>();
    private int talks;
    private int talkedAt = -1000;
    private int hornAt = -100_000;
    /** A village's villager: true until it has taken its home (its first tick in the world). */
    private boolean villageFolk;
    @Nullable
    private BlockPos home;

    public Frostfolk(EntityType<? extends Frostfolk> type, Level level, FolkRole role)
    {
        super(type, level);
        this.role = role;
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes(FolkRole role)
    {
        double speed = switch (role.kind)
        {
            case WARRIOR -> FolkTuning.WARRIOR_SPEED;
            case WORKER -> FolkTuning.WORKER_SPEED;
            case ELDER -> FolkTuning.ELDER_SPEED;
        };
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, role.health)
                .add(Attributes.ATTACK_DAMAGE, role.damage)
                .add(Attributes.ARMOR, role.armor)
                .add(Attributes.MOVEMENT_SPEED, speed)
                .add(Attributes.FOLLOW_RANGE, FolkTuning.FOLLOW_RANGE);
    }

    public FolkRole role()
    {
        return this.role;
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, -1);
        this.entityData.define(DATA_ACTION_COUNT, 0);
        this.entityData.define(DATA_SHOWCASE, false);
    }

    @Override
    protected void registerGoals()
    {
        // (Minecraft adds the goals while the mob is still being made, before its calling is set: the horn's and the
        // bow's goals are added for every calling and only ever start for the horn-blower and the archer.)
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HornGoal());
        this.goalSelector.addGoal(2, new BowGoal());
        this.goalSelector.addGoal(3, new FightGoal());
        this.goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, FolkTuning.HOME_SPEED));    // (a village's: home)
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, FolkTuning.STROLL_SPEED));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // (the Huntsman and his moose are theirs: their blows never hurt them, so never turn them)
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, Frostfolk.class, HuntMob.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, PondSkater.class, true));
    }

    // ---------------------------------------------------------------- on show

    /** On show: frozen (no AI), no idle chatter (it still talks when you right-click it), never pushed, can't be hurt
     * except by {@code /kill}. */
    public void setShowcase(boolean showcase)
    {
        this.entityData.set(DATA_SHOWCASE, showcase);
        this.setNoAi(showcase);
        if (showcase)
        {
            this.setPersistenceRequired();
        }
    }

    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    /**
     * Puts a frozen villager of {@code role} on show at {@code feet}, facing {@code yaw}.
     *
     * @return it, already in the world, or null if it couldn't be made
     */
    @Nullable
    public static Frostfolk spawnShowcase(ServerLevel level, FolkRole role, Vec3 feet, float yaw)
    {
        Frostfolk folk = ModEntities.FROSTFOLK.get(role).get().create(level);
        if (folk == null)
        {
            return null;
        }
        folk.setShowcase(true);
        folk.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        folk.setYHeadRot(yaw);
        folk.setYBodyRot(yaw);
        folk.yHeadRotO = yaw;
        folk.yBodyRotO = yaw;
        level.addFreshEntity(folk);
        return folk;
    }

    /** On show nothing goes for it (a Pond Skater wandering by). */
    @Override
    public boolean canBeSeenAsEnemy()
    {
        return !this.isShowcase() && super.canBeSeenAsEnemy();
    }

    /** They stand together: no friendly fire (the archer's arrows), never each other's targets. */
    @Override
    public boolean isAlliedTo(Entity other)
    {
        return other instanceof Frostfolk || super.isAlliedTo(other);
    }

    /** The showcase's controls: {@code idle}, {@code walk}, {@code run}, {@code attack} (its own: the archer's shot),
     * {@code horn} (the horn-blower's), {@code talk}. */
    public static final List<String> SHOW = List.of("idle", "walk", "run", "attack", "horn", "talk");

    /** On show: plays one of {@link #SHOW} (a control it has no animation for, the horn on any but the horn-blower,
     * does nothing). */
    public void playShowcase(String name)
    {
        RimefallAnims.Anim anim = switch (name)
        {
            case "attack" -> this.role.shoots() ? FolkAnims.QUICK_SHOT : this.role.attack.anim;
            case "horn" -> this.role.blowsHorn() ? FolkAnims.HORN_BLOW : null;
            case "idle" -> FolkAnims.IDLE;
            default -> RimefallAnims.named(FolkAnims.ALL, name);
        };
        if (anim != null)
        {
            this.startAction(anim == FolkAnims.IDLE ? null : anim);
        }
    }

    // ---------------------------------------------------------------- its animations

    void startAction(@Nullable RimefallAnims.Anim anim)
    {
        this.entityData.set(DATA_ACTION, anim == null ? -1 : FolkAnims.ALL.indexOf(anim));
        this.entityData.set(DATA_ACTION_COUNT, this.entityData.get(DATA_ACTION_COUNT) + 1);
        this.actionStart = this.tickCount;
        this.actionEnd = anim == null || anim.loop() ? -1 : this.tickCount + anim.ticks();
    }

    @Nullable
    public RimefallAnims.Anim action()
    {
        int index = this.entityData.get(DATA_ACTION);
        return index >= 0 && index < FolkAnims.ALL.size() ? FolkAnims.ALL.get(index) : null;
    }

    /** Ticks since its current action started. */
    int actionTicks()
    {
        return this.tickCount - this.actionStart;
    }

    boolean busy()
    {
        return this.actionEnd >= 0 && this.tickCount < this.actionEnd;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<Frostfolk> state)
    {
        AnimationController<Frostfolk> controller = state.getController();
        int count = this.entityData.get(DATA_ACTION_COUNT);
        if (count != this.seenActionCount)
        {
            this.seenActionCount = count;
            controller.forceAnimationReset();
        }
        RimefallAnims.Anim action = this.action();
        if (action != null)
        {
            controller.setAnimationSpeed(1.0D);
            return state.setAndContinue(action.raw());
        }
        double speed = RimefallAnims.groundSpeed(this);
        if (!this.isShowcase() && speed > RimefallAnims.MOVING)
        {
            RimefallAnims.Anim gait = speed > FolkTuning.RUN_ABOVE ? FolkAnims.RUN : FolkAnims.WALK;
            RimefallAnims.matchSpeed(controller, gait, speed);
            return state.setAndContinue(gait.raw());
        }
        controller.setAnimationSpeed(1.0D);
        return state.setAndContinue(FolkAnims.IDLE.raw());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            return;
        }
        RimefallAnims.Anim action = this.action();
        if (action != null && this.actionEnd >= 0 && this.tickCount >= this.actionEnd)
        {
            this.startAction(null);
        }
        if (this.villageFolk && this.home == null)
        {
            this.setHome(this.blockPosition());
        }
        if (this.tickCount % FolkTuning.ANGER_CHECK_TICKS == 0)
        {
            this.tickAnger(FolkTuning.ANGER_CHECK_TICKS);
        }
    }

    /** It lives at {@code pos}: it wanders within {@link FolkTuning#HOME_RADIUS} blocks of it. */
    void setHome(BlockPos pos)
    {
        this.home = pos.immutable();
        this.villageFolk = false;
        this.restrictTo(this.home, FolkTuning.HOME_RADIUS);
    }

    /** Where it lives (a village's villager), or null (a spawn egg's, or on show). */
    @Nullable
    public BlockPos home()
    {
        return this.home;
    }

    // ---------------------------------------------------------------- its anger

    /** True if it's angry at {@code entity} (a player it's been turned on, or the elder's grudge). */
    public boolean isAngryAt(LivingEntity entity)
    {
        if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator())
        {
            return false;
        }
        Long until = this.angry.get(player.getUUID());
        return (until != null && until > this.level().getGameTime()) || this.elderAngry.containsKey(player.getUUID());
    }

    /** Turns it on {@code player} for {@code ticks}. */
    public void angerAt(Player player, int ticks)
    {
        long until = this.level().getGameTime() + ticks;
        this.angry.merge(player.getUUID(), until, Math::max);
    }

    /** Lets time pass for its anger ({@code ticks}): grudges run out; the elder forgives a player away long enough
     * (near him, the time away starts again). */
    void tickAnger(int ticks)
    {
        long now = this.level().getGameTime();
        this.angry.values().removeIf(until -> until <= now);
        this.elderAngry.replaceAll((uuid, away) ->
        {
            Player player = this.level().getPlayerByUUID(uuid);
            boolean near = player != null && player.distanceToSqr(this) < FolkTuning.ELDER_NEAR * FolkTuning.ELDER_NEAR;
            return near ? 0 : away + ticks;
        });
        this.forgive();
    }

    /** (For the tests) As if {@code ticks} had passed with every player the elder holds a grudge against away. */
    void passTimeAway(int ticks)
    {
        this.elderAngry.replaceAll((uuid, away) -> away + ticks);
        this.forgive();
    }

    /** The elder forgets the grudges whose players have been away long enough; it drops a player it's no longer angry
     * at (unless that player has just hit it). */
    private void forgive()
    {
        this.elderAngry.values().removeIf(away -> away >= FolkTuning.ELDER_FORGIVE_TICKS);
        LivingEntity target = this.getTarget();
        if (target instanceof Player p && !this.isAngryAt(p) && this.getLastHurtByMob() != p)
        {
            this.setTarget(null);
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return (this.isShowcase() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable()
    {
        return !this.isShowcase() && super.isPushable();
    }

    @Override
    public void push(double x, double y, double z)
    {
        if (!this.isShowcase())
        {
            super.push(x, y, z);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (source.getEntity() instanceof Frostfolk && source.getEntity() != this)
        {
            return false;                            // a stray arrow of a friend's
        }
        if (source.getEntity() instanceof HuntMob)
        {
            return false;                            // their Huntsman's blows, arrows and ice (and his moose's)
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && source.getEntity() instanceof Player player)
        {
            if (this.role == FolkRole.ELDER)
            {
                this.elderAngry.put(player.getUUID(), 0);
            }
            else
            {
                this.angerAt(player, FolkTuning.HIT_ANGER_TICKS);
            }
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (this.level().isClientSide)
        {
            return;
        }
        Player killer = source.getEntity() instanceof Player p ? p : null;
        if (killer != null && !this.isShowcase())
        {
            turnVillage(this, killer);
        }
    }

    /** Every villager near {@code dead} turns on {@code killer} for {@link FolkTuning#VILLAGE_ANGER_TICKS}; a few shout. */
    static void turnVillage(Frostfolk dead, Player killer)
    {
        AABB area = dead.getBoundingBox().inflate(FolkTuning.VILLAGE_RANGE);
        List<Frostfolk> near = dead.level().getEntitiesOfClass(Frostfolk.class, area,
                h -> h != dead && h.isAlive() && !h.isShowcase());
        int shouted = 0;
        for (Frostfolk h : near)
        {
            h.angerAt(killer, FolkTuning.VILLAGE_ANGER_TICKS);
            h.setTarget(killer);
            if (shouted < FolkTuning.CRIERS)
            {
                h.playSound(ModSounds.FROSTFOLK_CRY.get(), 2.0F, h.getVoicePitch());
                shouted++;
            }
        }
    }

    // ---------------------------------------------------------------- talking

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND || !this.isAlive() || this.isAngryAt(player))
        {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide && this.tickCount - this.talkedAt >= FolkTuning.TALK_PAUSE)
        {
            this.talkedAt = this.tickCount;
            int line = this.talks++ % this.role.lines() + 1;
            Component said = Component.translatable("dross.frostfolk." + this.role.key + ".line." + line);
            player.sendSystemMessage(Component.literal("<").append(this.getDisplayName()).append("> ").append(said)
                    .withStyle(ChatFormatting.WHITE));
            this.getLookControl().setLookAt(player);
            if (!this.busy())
            {
                this.startAction(FolkAnims.TALK);
            }
            this.playSound(this.role == FolkRole.ELDER ? ModSounds.FROSTFOLK_ELDER_TALK.get() : ModSounds.FROSTFOLK_TALK.get(),
                    1.0F, this.getVoicePitch());
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /** (For the tests) How many times it has talked. */
    public int talks()
    {
        return this.talks;
    }

    // ---------------------------------------------------------------- its sounds

    @Nullable
    @Override
    protected SoundEvent getAmbientSound()
    {
        if (this.isShowcase())
        {
            return null;
        }
        return this.role == FolkRole.ELDER ? ModSounds.FROSTFOLK_ELDER_AMBIENT.get() : ModSounds.FROSTFOLK_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return this.role == FolkRole.ELDER ? ModSounds.FROSTFOLK_ELDER_HURT.get() : ModSounds.FROSTFOLK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return this.role == FolkRole.ELDER ? ModSounds.FROSTFOLK_ELDER_DEATH.get() : ModSounds.FROSTFOLK_DEATH.get();
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 320;
    }

    /** Each his own voice: a little higher or lower, fixed by who he is. */
    @Override
    public float getVoicePitch()
    {
        float own = ((this.getUUID().hashCode() & 0xFF) / 255.0F - 0.5F) * 0.16F;
        return 1.0F + own;
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (this.isShowcase())
        {
            tag.putBoolean(TAG_SHOWCASE, true);
        }
        ListTag anger = new ListTag();
        this.angry.forEach((uuid, until) ->
        {
            CompoundTag e = new CompoundTag();
            e.putUUID("Player", uuid);
            e.putLong("Until", until);
            anger.add(e);
        });
        tag.put(TAG_ANGER, anger);
        ListTag elder = new ListTag();
        this.elderAngry.forEach((uuid, away) ->
        {
            CompoundTag e = new CompoundTag();
            e.putUUID("Player", uuid);
            e.putInt("Away", away);
            elder.add(e);
        });
        tag.put(TAG_ELDER, elder);
        if (this.villageFolk)
        {
            tag.putBoolean(TAG_VILLAGE, true);
        }
        if (this.home != null)
        {
            tag.putLong(TAG_HOME, this.home.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setShowcase(tag.getBoolean(TAG_SHOWCASE));
        this.angry.clear();
        for (Tag t : tag.getList(TAG_ANGER, Tag.TAG_COMPOUND))
        {
            CompoundTag e = (CompoundTag) t;
            this.angry.put(e.getUUID("Player"), e.getLong("Until"));
        }
        this.elderAngry.clear();
        for (Tag t : tag.getList(TAG_ELDER, Tag.TAG_COMPOUND))
        {
            CompoundTag e = (CompoundTag) t;
            this.elderAngry.put(e.getUUID("Player"), e.getInt("Away"));
        }
        this.villageFolk = tag.getBoolean(TAG_VILLAGE);
        if (tag.contains(TAG_HOME, Tag.TAG_LONG))
        {
            this.setHome(BlockPos.of(tag.getLong(TAG_HOME)));
        }
    }

    // ---------------------------------------------------------------- fighting

    /** Hand to hand: up to its target and its own attack, the blows at their moments. */
    private final class FightGoal extends Goal
    {
        private int repath;
        private int pause;

        FightGoal()
        {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity t = Frostfolk.this.getTarget();
            if (t == null || !t.isAlive())
            {
                return false;
            }
            return !Frostfolk.this.role.shoots() || Frostfolk.this.distanceTo(t) < FolkTuning.BOW_KEEP_OFF * 0.5D;
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.canUse() || Frostfolk.this.busy();
        }

        @Override
        public void stop()
        {
            Frostfolk.this.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            Frostfolk me = Frostfolk.this;
            LivingEntity t = me.getTarget();
            FolkRole.Attack attack = me.role.attack;
            if (me.busy() && me.action() == attack.anim)
            {
                int at = me.actionTicks();
                for (int hit : attack.hits)
                {
                    if (at == hit && t != null && me.inReach(t, attack.reach + 0.5D))
                    {
                        me.doHurtTarget(t);
                        me.playSound(ModSounds.HUNTSMAN_SPEAR_SWEEP.get(), 0.7F, 1.25F);
                    }
                }
                if (t != null)
                {
                    me.getLookControl().setLookAt(t, 30.0F, 30.0F);
                }
                return;
            }
            if (t == null)
            {
                return;
            }
            me.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (this.pause > 0)
            {
                this.pause--;
            }
            if (!me.inReach(t, attack.reach))
            {
                if (--this.repath <= 0)
                {
                    this.repath = 10;
                    me.getNavigation().moveTo(t, 1.0D);
                }
                return;
            }
            me.getNavigation().stop();
            if (this.pause <= 0 && !me.busy())
            {
                me.startAction(attack.anim);
                this.pause = attack.anim.ticks() + FolkTuning.ATTACK_PAUSE;
            }
        }
    }

    /** True if {@code t} is within {@code reach} blocks of its body. */
    boolean inReach(LivingEntity t, double reach)
    {
        double gap = Math.sqrt(this.distanceToSqr(t)) - (this.getBbWidth() + t.getBbWidth()) / 2.0D;
        return gap <= reach;
    }

    /** The archer: keeps off his target and shoots it. */
    private final class BowGoal extends Goal
    {
        private int pause;
        private boolean loosed;

        BowGoal()
        {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity t = Frostfolk.this.getTarget();
            return Frostfolk.this.role.shoots() && t != null && t.isAlive()
                    && Frostfolk.this.distanceTo(t) >= FolkTuning.BOW_KEEP_OFF * 0.5D;
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.canUse() || (Frostfolk.this.busy() && Frostfolk.this.action() == FolkAnims.QUICK_SHOT);
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            Frostfolk me = Frostfolk.this;
            LivingEntity t = me.getTarget();
            if (me.busy() && me.action() == FolkAnims.QUICK_SHOT)
            {
                if (t != null)
                {
                    me.getLookControl().setLookAt(t, 30.0F, 30.0F);
                    if (!this.loosed && me.actionTicks() >= FolkAnims.SHOT_RELEASE)
                    {
                        this.loosed = true;
                        me.loose(t);
                    }
                }
                return;
            }
            if (t == null)
            {
                return;
            }
            me.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = me.distanceTo(t);
            if (d > FolkTuning.BOW_RANGE || !me.hasLineOfSight(t))
            {
                me.getNavigation().moveTo(t, 1.0D);
                return;
            }
            if (d < FolkTuning.BOW_KEEP_OFF)
            {
                Vec3 away = me.position().subtract(t.position()).normalize().scale(4.0D).add(me.position());
                me.getNavigation().moveTo(away.x, away.y, away.z, 1.1D);
            }
            else
            {
                me.getNavigation().stop();
            }
            if (--this.pause <= 0 && !me.busy())
            {
                me.startAction(FolkAnims.QUICK_SHOT);
                this.loosed = false;
                this.pause = FolkAnims.QUICK_SHOT.ticks() + FolkTuning.BOW_PAUSE;
            }
        }
    }

    /** An arrow off his bow at {@code t}. */
    void loose(LivingEntity t)
    {
        Arrow arrow = new Arrow(this.level(), this);
        arrow.setBaseDamage(FolkTuning.ARROW_DAMAGE);
        double dx = t.getX() - this.getX();
        double dz = t.getZ() - this.getZ();
        double dy = t.getY(0.4D) - arrow.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.18D, dz, FolkTuning.ARROW_SPEED, FolkTuning.ARROW_SPREAD);
        this.level().addFreshEntity(arrow);
        this.playSound(ModSounds.HUNTSMAN_BOW_SHOT.get(), 0.8F, 1.15F);
    }

    /** The horn-blower: as a fight starts, his horn; everyone near gets Speed and Strength. */
    private final class HornGoal extends Goal
    {
        private boolean called;

        HornGoal()
        {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity t = Frostfolk.this.getTarget();
            return Frostfolk.this.role.blowsHorn() && t instanceof Player
                    && Frostfolk.this.tickCount - Frostfolk.this.hornAt >= FolkTuning.HORN_PAUSE
                    && !Frostfolk.this.busy();
        }

        @Override
        public boolean canContinueToUse()
        {
            return Frostfolk.this.busy() && Frostfolk.this.action() == FolkAnims.HORN_BLOW;
        }

        @Override
        public void start()
        {
            Frostfolk me = Frostfolk.this;
            me.hornAt = me.tickCount;
            me.getNavigation().stop();
            me.startAction(FolkAnims.HORN_BLOW);
            this.called = false;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            Frostfolk me = Frostfolk.this;
            if (!this.called && me.actionTicks() >= FolkAnims.HORN_CALL)
            {
                this.called = true;
                me.level().playSound(null, me.getX(), me.getY(), me.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(1).value(),
                        SoundSource.HOSTILE, 4.0F, 1.1F);
                for (Frostfolk h : me.level().getEntitiesOfClass(Frostfolk.class, me.getBoundingBox().inflate(FolkTuning.HORN_RANGE),
                        h -> h.isAlive() && !h.isShowcase()))
                {
                    h.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, FolkTuning.HORN_EFFECT_TICKS, 0));
                    h.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, FolkTuning.HORN_EFFECT_TICKS, 0));
                }
            }
        }
    }
}
