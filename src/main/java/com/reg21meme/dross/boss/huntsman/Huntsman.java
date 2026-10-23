package com.reg21meme.dross.boss.huntsman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Huntsman ({@code dross:huntsman}; the gallery's 6A rider: a wolf's head worn as a hood, deer antlers, a wide
 * bearskin cloak, a boar spear of ice, a bow on his back). He has two models: in the Alpha Moose's saddle (while he
 * rides it, stage 1 of his fight, {@link HuntAnims#RIDING}) and on foot (stage 2, {@link HuntAnims#HUNTSMAN}).
 * <p>
 * In the saddle his hitbox sits over the moose's back ({@link HuntsmanTuning#RIDER_SEAT}: his body can be hit, not only
 * the moose under him) and his model isn't drawn: the moose wears the Wild Hunt's model then, him in its saddle
 * ({@link AlphaMoose}), playing his animation (the one he plays here, on his clock) and the saddle's motion under him. At
 * the end of his leap ({@link HuntAnims#LEAP}) he gets off and lands on foot beside the moose ({@link HuntCues#LEAP_OUT}
 * blocks to its left), playing {@code land} in his own model again; at the split he then blows his horn.
 * <p>
 * Fighting ({@link HuntFight}): a spawn egg or {@code /summon} brings him in his Alpha Moose's saddle; in stage 1 the
 * moose's {@link WildHuntGoal} picks his moves too, in stage 2 his own {@link FootGoal}.
 */
public class Huntsman extends HuntMob
{
    /** On foot his model reaches this far past his hitbox (his spear and bow to the sides, his antlers above it). */
    private static final double DRAWN_BESIDE = 1.2D;
    private static final double DRAWN_ABOVE = 1.4D;
    /** In the saddle, round the moose's back (his cloak, his spear, his legs down its sides). */
    private static final double DRAWN_BESIDE_RIDING = 1.6D;

    /** At the split: once he has landed, his horn. */
    boolean hornWhenLanded;
    /** His spear is out (thrown, on its way back): {@link HuntSpear}. */
    boolean spearOut;

    public Huntsman(EntityType<? extends Huntsman> type, Level level)
    {
        super(type, level, HuntsmanTuning.HUNTSMAN_MOVE_TURN);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HuntsmanTuning.HUNTSMAN_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, HuntsmanTuning.HUNTSMAN_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, HuntsmanTuning.FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FootGoal(this));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
    }

    /** True while he's in a saddle (he wears his riding model). Works on both sides. */
    public boolean isMounted()
    {
        return this.getVehicle() instanceof AlphaMoose;
    }

    @Override
    public List<HuntAnims.Anim> animations()
    {
        return this.isMounted() ? HuntAnims.RIDING : HuntAnims.HUNTSMAN;
    }

    @Override
    protected double gaitPace(String loop)
    {
        return switch (loop)
        {
            case "walk" -> HuntsmanTuning.HUNTSMAN_WALK_PACE;
            case "run" -> HuntsmanTuning.HUNTSMAN_RUN_PACE;
            default -> 0.0D;
        };
    }

    /** In the saddle his model stands on the moose's ground (his hitbox is over its back). */
    @Override
    public Vec3 modelOrigin()
    {
        Entity vehicle = this.getVehicle();
        return vehicle instanceof AlphaMoose ? vehicle.position() : this.position();
    }

    /** His hitbox: in the saddle over the moose's back, a little wider than the moose's; on foot his own. */
    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return this.isMounted() ? EntityDimensions.scalable(HuntsmanTuning.RIDER_WIDTH, HuntsmanTuning.RIDER_HEIGHT)
                : super.getDimensions(pose);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions)
    {
        return this.isMounted() ? HuntsmanTuning.RIDER_EYES : super.getStandingEyeHeight(pose, dimensions);
    }

    /** In the saddle his head is up among branches the moose walks under: they don't smother him. */
    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return this.isMounted() && source.is(DamageTypes.IN_WALL) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force)
    {
        boolean on = super.startRiding(vehicle, force);
        if (on)
        {
            this.refreshDimensions();
        }
        return on;
    }

    /** Off the moose: his own hitbox again. On a player's screen he lands where his leap put him at once (the server
     * moves him there in the same tick), so he's never seen jump from the moose's middle. */
    @Override
    public void stopRiding()
    {
        Entity vehicle = this.getVehicle();
        super.stopRiding();
        if (vehicle == null || this.getVehicle() != null)
        {
            return;
        }
        this.refreshDimensions();
        String anim = this.animName();
        if (this.level().isClientSide && vehicle instanceof AlphaMoose moose && (HuntAnims.LEAP.equals(anim) || "land".equals(anim)))
        {
            Vec3 spot = landing(moose);
            this.setPos(spot.x, spot.y, spot.z);
            this.setOldPosAndRot();
        }
    }

    /** Where his leap off {@code moose} lands him: {@link HuntCues#LEAP_OUT} blocks to its left, on its ground. */
    static Vec3 landing(AlphaMoose moose)
    {
        double rad = Math.toRadians(moose.yBodyRot);
        return new Vec3(moose.getX() + Math.cos(rad) * HuntCues.LEAP_OUT, moose.getY(), moose.getZ() + Math.sin(rad) * HuntCues.LEAP_OUT);
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        AABB box = this.getBoundingBox();
        return this.isMounted() ? box.expandTowards(0.0D, -HuntsmanTuning.RIDER_SEAT, 0.0D).inflate(DRAWN_BESIDE_RIDING, 0.0D, DRAWN_BESIDE_RIDING)
                : box.inflate(DRAWN_BESIDE, 0.0D, DRAWN_BESIDE).expandTowards(0.0D, DRAWN_ABOVE, 0.0D);
    }

    /** A fighting Huntsman (a spawn egg, {@code /summon}) comes in his Alpha Moose's saddle. */
    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (!this.isShowcase() && this.getVehicle() == null)
        {
            AlphaMoose moose = HuntsmanPair.newMoose(level.getLevel(), this.position(), this.getYRot());
            if (moose != null)
            {
                HuntFight.link(this, moose);
                this.startRiding(moose, true);
            }
        }
        return out;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide && this.getVehicle() instanceof AlphaMoose moose
                && this.current().name().equals(HuntAnims.LEAP) && this.playingTicks() >= this.current().ticks())
        {
            this.leapOff(moose);
        }
    }

    /** Off the moose, onto his feet where his leap put him (to its left), facing the way it faces; then he lands. */
    public void leapOff(AlphaMoose moose)
    {
        float yaw = moose.yBodyRot;
        Vec3 spot = landing(moose);
        this.stopRiding();
        this.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRotO = yaw;
        this.yHeadRotO = yaw;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.play("land");
    }

    /** At the split, his horn once he has landed. */
    @Override
    protected void onMoveEnded(HuntAnims.Anim move)
    {
        if (move.name().equals("land") && this.hornWhenLanded)
        {
            this.hornWhenLanded = false;
            this.playLater("horn_blow", HuntsmanTuning.SPLIT_HORN_DELAY);
        }
    }

    /** His thrown spear is back in his hand. */
    void spearBack()
    {
        this.spearOut = false;
    }
}
