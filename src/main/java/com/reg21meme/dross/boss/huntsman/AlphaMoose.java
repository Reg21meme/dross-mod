package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.boss.huntsman.skull.SkullDrops;
import com.reg21meme.dross.boss.rimefall.RimefallBodyPart;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Huntsman's Alpha Moose ({@code dross:alpha_moose}; the gallery's 6A2, about 5.3 blocks to its antler tips, in
 * its tack). The Huntsman rides it in stage 1 of his fight: his hitbox sits over its back
 * ({@link HuntsmanTuning#RIDER_SEAT}) and he turns with it, and while he rides it the moose wears the Wild Hunt's model
 * (itself with him in its saddle, {@link HuntAnims#WILD}; his own isn't drawn then): its own animation, his and the
 * saddle's motion under him all play on it, on its own clock, so the two are one on the screen (the user, October 23,
 * 2026: "make them one model, then separate the models after the split"; drawn apart they wobbled against each other as
 * he aimed and it moved). Fighting ({@link HuntFight}), it keeps the Wild Hunt's bar for both and its
 * {@link MooseGoal} picks its moves in both stages (in stage 1 he makes his own from its back: {@link SaddleGoal}).
 * Calm, it walks about slowly.
 */
public class AlphaMoose extends HuntMob
{
    /** Calm, it walks at this much of its speed. */
    private static final double CALM_WALK = 0.6D;
    /** Its model reaches this far past its hitbox (its head and antlers ahead, its rump behind; its antler tips over
     * it), so it's drawn whenever any of that is on screen. */
    private static final double DRAWN_BEYOND = 2.4D;
    private static final double DRAWN_ABOVE = 1.0D;
    /** Ridden, the Huntsman in its saddle reaches this much higher (his antlers, his spear). */
    private static final double DRAWN_ABOVE_RIDDEN = 4.4D;

    /**
     * Its body's hitboxes past its main one (a square 2.8 across round its middle: it's 6.7 blocks long, so its legs,
     * chest, rump, head and neck stuck out of it; the user, October 23, 2026: "increase his hitbox near his legs"), in
     * blocks from its feet (its own left +x, up +y, front -z), measured from its model (alpha_moose.geo.json): its chest
     * and front legs, its rump and hind legs (both from the ground to its back), its head and neck.
     */
    private static final double[][] PART_SPOTS = {{0.0D, 0.0D, -1.9D}, {0.0D, 0.0D, 1.9D}, {0.0D, 2.7D, -3.4D}};
    private static final float[][] PART_SIZES = {{2.0F, 4.6F}, {2.0F, 4.6F}, {1.6F, 2.3F}};

    private final RimefallBodyPart<AlphaMoose>[] parts;
    private int riderSeen = -1;
    private int carrySeen = -1;
    /** The tick its gallop's hooves last sounded (one stride's sound at a time). */
    private int lastGallop = -1000;

    @SuppressWarnings("unchecked")
    public AlphaMoose(EntityType<? extends AlphaMoose> type, Level level)
    {
        super(type, level, HuntsmanTuning.MOOSE_MOVE_TURN);
        this.parts = new RimefallBodyPart[PART_SPOTS.length];
        for (int i = 0; i < this.parts.length; i++)
        {
            this.parts[i] = new RimefallBodyPart<>(this, PART_SIZES[i][0], PART_SIZES[i][1]);
        }
        // its parts' ids follow its own (like the Ender Dragon's parts)
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    @Override
    public void setId(int id)
    {
        super.setId(id);
        if (this.parts != null)
        {
            for (int i = 0; i < this.parts.length; i++)
            {
                this.parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity()
    {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts()
    {
        return this.parts;
    }

    /** Its chest, rump and head hitboxes (in {@link #PART_SPOTS}' order). */
    public RimefallBodyPart<AlphaMoose>[] getBodyParts()
    {
        return this.parts;
    }

    @Override
    public void tick()
    {
        super.tick();
        RimefallBodyPart.place(this, this.parts, PART_SPOTS, 1.0F);
    }

    @Override
    protected int idleSoundInterval()
    {
        return HuntsmanTuning.MOOSE_IDLE_SOUND_TICKS;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.ALPHA_MOOSE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.ALPHA_MOOSE_HURT.get();
    }

    /** None here: its death call comes at its moment of its death animation ({@link HuntSoundCues}). */
    @Override
    @Nullable
    protected SoundEvent getDeathSound()
    {
        return null;
    }

    /** Its hooves: a stride of its gallop while it runs or charges (one at a time), a heavy clop when it walks. */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        String gait = this.current().name();
        if (gait.equals("run") || gait.equals("charge"))
        {
            if (this.tickCount - this.lastGallop >= HuntsmanTuning.GALLOP_SOUND_TICKS)
            {
                this.lastGallop = this.tickCount;
                this.playSound(ModSounds.ALPHA_MOOSE_GALLOP.get(), 1.4F, 1.0F);
            }
        }
        else
        {
            this.playSound(ModSounds.ALPHA_MOOSE_STEP.get(), 1.0F, 1.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HuntsmanTuning.MOOSE_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, HuntsmanTuning.MOOSE_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, HuntsmanTuning.FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MooseGoal(this));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, CALM_WALK)
        {
            @Override
            public boolean canUse()
            {
                return AlphaMoose.this.stage() == HuntStage.CALM && super.canUse();
            }

            @Override
            public boolean canContinueToUse()
            {
                return AlphaMoose.this.stage() == HuntStage.CALM && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
    }

    @Override
    public List<HuntAnims.Anim> animations()
    {
        return HuntAnims.ALPHA_MOOSE;
    }

    @Override
    protected double gaitPace(String loop)
    {
        return switch (loop)
        {
            case "walk" -> HuntsmanTuning.MOOSE_WALK_PACE;
            case "run" -> HuntsmanTuning.MOOSE_GALLOP_PACE;
            case "charge" -> HuntsmanTuning.MOOSE_CHARGE_PACE;
            default -> 0.0D;
        };
    }

    /** The Huntsman in its saddle, or null. */
    Huntsman rider()
    {
        return this.getFirstPassenger() instanceof Huntsman h ? h : null;
    }

    /** True while the Huntsman rides it: it wears the Wild Hunt's model. Both sides. */
    public boolean isRidden()
    {
        return this.rider() != null;
    }

    /** It's steered by its own goals, never by the one riding it. */
    @Override
    public LivingEntity getControllingPassenger()
    {
        return null;
    }

    /** The rider's hitbox sits over its back ({@link HuntsmanTuning#RIDER_SEAT}); his model is drawn on its ground. */
    @Override
    public double getPassengersRidingOffset()
    {
        return HuntsmanTuning.RIDER_SEAT;
    }

    /** The rider sits over its back, facing exactly the way it faces (his body, his head). */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move)
    {
        if (this.hasPassenger(passenger))
        {
            move.accept(passenger, this.getX(), this.getY() + HuntsmanTuning.RIDER_SEAT, this.getZ());
            passenger.setYRot(this.getYRot());
            if (passenger instanceof LivingEntity rider)
            {
                rider.yBodyRot = this.yBodyRot;
                rider.yBodyRotO = this.yBodyRotO;
                rider.setYHeadRot(this.yBodyRot);
                rider.yHeadRotO = this.yBodyRotO;
            }
        }
    }

    /** Its skull, every time it dies fighting ({@link SkullDrops}: a fight broken off and won again gives another). */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit)
    {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!this.isShowcase())
        {
            SkullDrops.always(this, ModItems.ALPHA_MOOSE_SKULL.get());
        }
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(DRAWN_BEYOND, 0.0D, DRAWN_BEYOND)
                .expandTowards(0.0D, this.isRidden() ? DRAWN_ABOVE_RIDDEN : DRAWN_ABOVE, 0.0D);
    }

    // ---------------------------------------------------------------- GeckoLib: the Wild Hunt's model

    @Override
    protected RawAnimation raw(HuntAnims.Anim a)
    {
        return this.isRidden() ? HuntAnims.raw(HuntAnims.WILD, a) : a.raw();
    }

    /** Ridden, two more controllers: his animation, and the saddle's motion under him (not while he leaps off: his leap
     * has its own). */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        super.registerControllers(controllers);
        controllers.add(new HuntController<>(this, "rider", BLEND_TICKS, this::riderAnim, AlphaMoose::riderAnchor,
                AlphaMoose::riderSpeed));
        controllers.add(new HuntController<>(this, "carry", BLEND_TICKS, this::carryAnim, AlphaMoose::carryAnchor,
                AlphaMoose::carrySpeed));
    }

    private PlayState riderAnim(AnimationState<AlphaMoose> state)
    {
        Huntsman h = this.rider();
        if (h == null)
        {
            this.riderSeen = -1;
            return PlayState.STOP;
        }
        int count = h.animCount();
        if (count != this.riderSeen)
        {
            this.riderSeen = count;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(HuntAnims.raw(HuntAnims.WILD, h.current()));
    }

    /** His animation goes on his own beat; his part of the gallop on its strides (its beat, as long a loop as its
     * gallop's or dash's). */
    private double riderAnchor(String animation)
    {
        Huntsman h = this.rider();
        if (h == null || !animation.equals(h.current().name()))
        {
            return Double.NaN;
        }
        return this.galloping(animation) ? this.anchor() : h.anchor();
    }

    private double riderSpeed(String animation)
    {
        Huntsman h = this.rider();
        if (h == null || !animation.equals(h.current().name()))
        {
            return 1.0D;
        }
        if (!this.galloping(animation))
        {
            return h.pace();
        }
        HuntAnims.Anim gallop = HuntAnims.named(HuntAnims.RIDING, HuntAnims.GALLOP);
        return this.pace() * gallop.length() / this.current().length();
    }

    private boolean galloping(String animation)
    {
        String gait = this.current().name();
        return HuntAnims.GALLOP.equals(animation) && (gait.equals("run") || gait.equals("charge"));
    }

    private PlayState carryAnim(AnimationState<AlphaMoose> state)
    {
        Huntsman h = this.rider();
        if (h == null || h.current().name().equals(HuntAnims.LEAP))
        {
            this.carrySeen = -1;
            return PlayState.STOP;
        }
        int count = this.animCount();
        if (count != this.carrySeen)
        {
            this.carrySeen = count;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(HuntAnims.carry(this.current()));
    }

    /** The saddle's motion goes on the moose's own beat. */
    private double carryAnchor(String carry)
    {
        return carry.equals("carry_" + this.current().name()) ? this.anchor() : Double.NaN;
    }

    private double carrySpeed(String carry)
    {
        return carry.equals("carry_" + this.current().name()) ? this.pace() : 1.0D;
    }
}
