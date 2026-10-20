package com.reg21meme.dross.boss.huntsman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Huntsman ({@code dross:huntsman}; the gallery's 6A rider: a wolf's head worn as a hood, deer antlers, a wide
 * bearskin cloak, a boar spear of ice, a bow on his back). He has two models: in the Alpha Moose's saddle (while he
 * rides it, stage 1 of his fight, {@link HuntAnims#RIDING}) and on foot (stage 2, {@link HuntAnims#HUNTSMAN}).
 * <p>
 * In the saddle a second animation controller moves him with the saddle: it plays his {@code carry_<name>} for the
 * moose's animation (started with it), so he rides along when the moose rears, kicks or kneels; not while he leaps off.
 * At the end of his leap ({@link HuntAnims#LEAP}) he gets off and lands on foot beside the moose ({@link HuntCues#LEAP_OUT}
 * blocks to its left), playing {@code land}; at the split he then blows his horn.
 * <p>
 * Fighting ({@link HuntFight}): a spawn egg or {@code /summon} brings him in his Alpha Moose's saddle; in stage 1 the
 * moose's {@link WildHuntGoal} picks his moves too, in stage 2 his own {@link FootGoal}.
 */
public class Huntsman extends HuntMob
{
    private int carrySeen = -1;
    /** At the split: once he has landed, his horn. */
    boolean hornWhenLanded;
    /** His spear is out (thrown, on its way back): {@link HuntSpear}. */
    boolean spearOut;

    public Huntsman(EntityType<? extends Huntsman> type, Level level)
    {
        super(type, level);
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

    /** He sits at the moose's own position (his saddle model is built standing on the moose's ground). */
    @Override
    public double getMyRidingOffset()
    {
        return 0.0D;
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
        double rad = Math.toRadians(yaw);
        double x = moose.getX() + Math.cos(rad) * HuntCues.LEAP_OUT;
        double z = moose.getZ() + Math.sin(rad) * HuntCues.LEAP_OUT;
        this.stopRiding();
        this.moveTo(x, moose.getY(), z, yaw, 0.0F);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRotO = yaw;
        this.yHeadRotO = yaw;
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

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        super.registerControllers(controllers);
        controllers.add(new AnimationController<>(this, "carry", BLEND_TICKS, this::carry));
    }

    private PlayState carry(AnimationState<Huntsman> state)
    {
        Entity vehicle = this.getVehicle();
        if (!(vehicle instanceof AlphaMoose moose) || this.current().name().equals(HuntAnims.LEAP))
        {
            this.carrySeen = -1;
            return PlayState.STOP;
        }
        int count = moose.animCount();
        if (count != this.carrySeen)
        {
            this.carrySeen = count;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(HuntAnims.carry(moose.current()));
    }
}
