package com.reg21meme.dross.boss.cinder;

import com.reg21meme.dross.boss.BossChase;
import com.reg21meme.dross.boss.fire.FireDesigns;
import com.reg21meme.dross.boss.fire.ShapedPart;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

import static com.reg21meme.dross.boss.cinder.CinderMobTuning.*;

/**
 * The Cinder Hermit ({@code dross:cinder_hermit}): a hermit crab the size of a cart, a huge claw of magma rock and a
 * small picking claw, its eyes on stalks, living in whatever it found in the ash (CLAUDE.md step 16). <b>One mob that
 * wears one of eight shells</b> ({@link #SHELLS}: the bronze bell, the iron brazier, the horned skull, the funerary urn,
 * the magma conch, the crystal geode, the giant lantern, the colossus shard), each as likely, picked when it's made
 * (natural spawns, the spawn egg, {@code /summon}), saved ({@code Shell}, its label: {@code {Shell:"M2C"}} picks one)
 * and synced; each shell's model has its own animations (it hides in its own shell). Its hitboxes are its pieces (its
 * shell, body, eyes, claws and legs, following its animations: {@link #PIECES}); its own box, narrower than its shell, is
 * only for standing and walking.
 * <ul>
 *   <li>Neutral ({@link CinderMob}); hurt, it fights back, and so do the other hermits within
 *       {@link CinderMobTuning#HERMIT_CALL_RANGE} blocks.</li>
 *   <li>The <b>snap</b> (its big claw slammed shut on what's in front of it) and the <b>pinch</b> (two quick nips of its
 *       small claw).</li>
 *   <li><b>Hiding</b>: every quarter of its health it loses, it pulls into its shell. In there it takes half the damage
 *       and can't be knocked back; after 2-3 s it peeks out (its eyes on their stalks, looking left and right), then comes
 *       out and fights on (angry, it strikes at once).</li>
 * </ul>
 */
public class CinderHermit extends CinderMob
{
    /** Its eight shells: every Cinder Wastes mob design that wears the hermit's skeleton (FireDesigns.CINDER_MOBS). */
    public static final List<FireDesigns.Design> SHELLS = FireDesigns.CINDER_MOBS.stream()
            .filter(d -> "hermit".equals(d.mob())).toList();
    private static final EntityDataAccessor<Boolean> DATA_HIDDEN = SynchedEntityData.defineId(CinderHermit.class,
            EntityDataSerializers.BOOLEAN);
    private static final String TAG_SHELL = "Shell";
    private static final String TAG_HIDE_MARK = "HideMark";
    private static final String TAG_DEN = "Den";
    /** It turns at most this many degrees a tick (a 6-block crab). */
    private static final float MAX_TURN = 10.0F;

    /** (server) where it is in its shell routine */
    enum Shell { OUT, HIDING, HIDDEN, PEEKING, COMING_OUT }

    private final ShapedPart<CinderHermit>[] parts;
    private Shell shell = Shell.OUT;
    /** (server) set when a hurt makes it want to hide (its hiding goal takes it from there) */
    private boolean wantsToHide;
    /** (server) it hides when its health falls to this share of its most or under (then the next quarter down) */
    private float hideMark = 1.0F - HIDE_EVERY;
    /** (server) out of its shell angry: it strikes at once */
    private boolean strikeNow;

    @SuppressWarnings("unchecked")
    public CinderHermit(EntityType<? extends CinderHermit> type, Level level)
    {
        super(type, level, HERMIT_XP, MAX_TURN);
        this.setDesign(SHELLS.get(this.random.nextInt(SHELLS.size())).number());
        this.parts = new ShapedPart[PIECES.size()];
        for (int i = 0; i < this.parts.length; i++)
        {
            this.parts[i] = new ShapedPart<>(this, PIECES.get(i));
        }
        // Its hitboxes' ids follow its own (like the Ender Dragon's parts).
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, HERMIT_HEALTH)
                .add(Attributes.ARMOR, HERMIT_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, HERMIT_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, HERMIT_KNOCKBACK_RESISTANCE)
                .add(Attributes.FOLLOW_RANGE, HERMIT_FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, SNAP_DAMAGE);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_HIDDEN, false);
    }

    // ---------------------------------------------------------------- its shell

    /** The shell it wears. Works on both sides. */
    public FireDesigns.Design getShell()
    {
        return this.getDesign();
    }

    /** Puts it in one of its shells ({@link #SHELLS}); anything else is ignored. */
    public void setShell(FireDesigns.Design shell)
    {
        if (SHELLS.contains(shell))
        {
            this.setDesign(shell.number());
        }
    }

    /** True while it's in its shell (pulling in, hidden, peeking, until it's out again). Works on both sides. */
    public boolean isHidden()
    {
        return this.entityData.get(DATA_HIDDEN);
    }

    private void setHidden(boolean hidden)
    {
        this.entityData.set(DATA_HIDDEN, hidden);
    }

    /** (server) Where it is in its shell routine (for the tests). */
    Shell shellRoutine()
    {
        return this.shell;
    }

    // ---------------------------------------------------------------- its hitboxes

    /** Its pieces, each a hitbox (in the order of its hitbox files, tools/fire/hitboxes.py: the same in every shell). */
    public static final java.util.List<String> PIECES = java.util.List.of("shell", "body", "eyes", "claw_big", "claw_small",
            "leg_al", "leg_bl", "leg_ar", "leg_br");

    /** Its own box: only for standing and walking, narrower than its shell (CinderMobTuning.HERMIT_STAND_SHARE). */
    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        FireDesigns.Design design = this.getDesign();
        return EntityDimensions.scalable(standWidth(design), design.height());
    }

    /** How wide its own box is in this shell. */
    public static float standWidth(FireDesigns.Design shell)
    {
        return Math.max(HERMIT_STAND_MIN, shell.width() * HERMIT_STAND_SHARE);
    }

    /** Only its pieces can be hit (like the Ender Dragon: its own box isn't where it is). */
    @Override
    public boolean isPickable()
    {
        return false;
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

    /** Its hitboxes ({@link #PIECES}). */
    public ShapedPart<CinderHermit>[] bodyParts()
    {
        return this.parts;
    }

    @Override
    public void tick()
    {
        super.tick();
        ShapedPart.place(this, this.parts, this.isDeadOrDying() ? null : this.pieceBoxes());
    }

    // ---------------------------------------------------------------- what it says

    @Override
    protected String soundPrefix()
    {
        return "entity.hermit";
    }

    @Override
    protected double wanderSpeed()
    {
        return HERMIT_WANDER_SPEED;
    }

    @Override
    protected double walkPace()
    {
        return HERMIT_WALK_PACE;
    }

    @Override
    protected double stepLength()
    {
        return HERMIT_STEP_LENGTH;
    }

    @Override
    protected float getSoundVolume()
    {
        return HERMIT_VOLUME;
    }

    @Override
    protected void addFightGoals()
    {
        this.goalSelector.addGoal(1, new HideGoal());
        this.goalSelector.addGoal(2, new FightGoal());
        this.goalSelector.addGoal(4, new BackToDenGoal());
    }

    // ---------------------------------------------------------------- a cave's own crab: its den

    /**
     * (server) Where a hermit crab cave's own crab lives (its crab spot; null: it roams, a natural or an egg's hermit).
     * It never wanders off from there, and walks back once it's more than {@link CinderMobTuning#CAVE_CRAB_DEN} blocks
     * away with nothing to fight (the user, November 3; a headless test saw one leave its den). It isn't a vanilla
     * restriction: Minecraft's target goals refuse anything outside one, so it wouldn't fight back from its den.
     */
    @Nullable
    private BlockPos den;

    /** Makes {@code spot} (a hermit crab cave's crab spot) its den, saved with it. */
    public void setDen(BlockPos spot)
    {
        this.den = spot.immutable();
    }

    @Nullable
    public BlockPos den()
    {
        return this.den;
    }

    @Override
    protected boolean mayWander()
    {
        return this.den == null;
    }

    private double fromDen()
    {
        return this.den == null ? 0.0D : Math.hypot(this.getX() - (this.den.getX() + 0.5D), this.getZ() - (this.den.getZ() + 0.5D));
    }

    /** For the tests' messages: how its way back to its den stands (its path, its running goals). */
    String denState()
    {
        net.minecraft.world.level.pathfinder.Path path = this.getNavigation().getPath();
        StringBuilder goals = new StringBuilder();
        this.goalSelector.getRunningGoals().forEach(g -> goals.append(g.getGoal().getClass().getSimpleName()).append(' '));
        return String.format(java.util.Locale.ROOT, "%.1f from its den, on the ground %s, its path %s, done %s, running %s, its animation %s"
                        + " | yaw %.1f, wants %s %.1f %.1f, speed %.3f, zza %.3f, moving %.3f %.3f",
                this.fromDen(), this.onGround(), path == null ? "none" : path.getNodeCount() + " nodes to " + path.getTarget().toShortString()
                        + " next " + (path.isDone() ? "-" : path.getNextNodeIndex() + " " + path.getNextNodePos().toShortString())
                        + (path.canReach() ? "" : " (can't reach)"), this.getNavigation().isDone(), goals.toString().trim(), this.animation(),
                this.getYRot(), this.getMoveControl().hasWanted(), this.getMoveControl().getWantedX(), this.getMoveControl().getWantedZ(),
                this.getSpeed(), this.zza, this.getDeltaMovement().x, this.getDeltaMovement().z);
    }

    /** Back to its den after a fight: straight there when the way is clear (as it chases: {@link BossChase}), else
     *  along a route of up to {@link CinderMobTuning#CAVE_CRAB_PATH_RANGE} blocks, until it's
     *  {@link CinderMobTuning#CAVE_CRAB_HOME} from the middle of its den. */
    private final class BackToDenGoal extends Goal
    {
        private int repath;

        BackToDenGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse()
        {
            return CinderHermit.this.den != null && CinderHermit.this.getTarget() == null && !CinderHermit.this.inMove()
                    && CinderHermit.this.fromDen() > CAVE_CRAB_DEN;
        }

        @Override
        public boolean canContinueToUse()
        {
            return CinderHermit.this.den != null && CinderHermit.this.getTarget() == null && !CinderHermit.this.inMove()
                    && CinderHermit.this.fromDen() > CAVE_CRAB_HOME;
        }

        @Override
        public void start()
        {
            this.repath = 0;
        }

        @Override
        public void tick()
        {
            CinderHermit self = CinderHermit.this;
            BlockPos d = self.den;
            Vec3 home = new Vec3(d.getX() + 0.5D, d.getY(), d.getZ() + 0.5D);
            if (BossChase.straightWay(self, home))
            {
                self.getNavigation().stop();
                self.getMoveControl().setWantedPosition(home.x, home.y, home.z, CAVE_CRAB_HOME_SPEED);
                this.repath = 0;
            }
            else if (--this.repath <= 0 || self.getNavigation().isDone())
            {
                self.walkFar(home, CAVE_CRAB_HOME_SPEED, CAVE_CRAB_PATH_RANGE);
                this.repath = CAVE_CRAB_REPATH_TICKS;
            }
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void stop()
        {
            CinderHermit.this.getNavigation().stop();
        }
    }

    // ---------------------------------------------------------------- hurt: hiding, and its kin

    /**
     * In its shell it takes {@link CinderMobTuning#HIDDEN_DAMAGE_TAKEN} of the damage (never less against
     * {@code /kill}), with a clang. Every quarter of its health it loses ({@link CinderMobTuning#HIDE_EVERY}), it hides
     * (the user, November 2: "if it loses like 25% of its health, it goes in the shell to hide"; a hit that kills it
     * still kills it: it's never shielded before it hides), and the hermits round it come for whoever hurt it.
     */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.level().isClientSide)
        {
            return super.hurt(source, amount);
        }
        boolean shelled = this.isHidden() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        boolean hurt = super.hurt(source, shelled ? amount * HIDDEN_DAMAGE_TAKEN : amount);
        if (hurt && shelled)
        {
            boolean bell = "M2A".equals(this.getShell().label());
            this.playSound(bell ? sound("entity.hermit.clang") : SoundEvents.SHIELD_BLOCK, 1.0F, 0.6F + this.random.nextFloat() * 0.2F);
        }
        if (hurt && this.isAlive())
        {
            if (source.getEntity() instanceof LivingEntity attacker && attacker != this)
            {
                this.callKin(attacker);
            }
            float left = this.getHealth() / this.getMaxHealth();
            if (left <= this.hideMark + 1.0E-4F)
            {
                // (the next quarter under where its health is now: a big hit can take it past two at once)
                this.hideMark = (float) Math.floor((left - 1.0E-4F) / HIDE_EVERY) * HIDE_EVERY;
                if (this.shell == Shell.OUT)
                {
                    this.wantsToHide = true;
                }
            }
        }
        return hurt;
    }

    /** For the tests: it will hide at its next goal tick. */
    void hideNow()
    {
        this.wantsToHide = true;
    }

    /** The other hermits within {@link CinderMobTuning#HERMIT_CALL_RANGE} blocks (edge to edge) that aren't fighting
     *  come for {@code attacker}. */
    private void callKin(LivingEntity attacker)
    {
        if (!this.canAttack(attacker))
        {
            return;
        }
        double reach = HERMIT_CALL_RANGE + this.getBbWidth();
        for (CinderHermit kin : this.level().getEntitiesOfClass(CinderHermit.class, this.getBoundingBox().inflate(reach),
                k -> k != this && k.isAlive() && k.getTarget() == null))
        {
            double edges = kin.distanceTo(this) - (kin.getBbWidth() + this.getBbWidth()) / 2.0D;
            if (edges <= HERMIT_CALL_RANGE && kin.canAttack(attacker))
            {
                kin.setTarget(attacker);
            }
        }
    }

    /** In its shell it can't be knocked back. */
    @Override
    public void knockback(double strength, double x, double z)
    {
        if (!this.isHidden())
        {
            super.knockback(strength, x, z);
        }
    }

    // ---------------------------------------------------------------- its moves

    /** The snap's blow: everything in its claw's arc. */
    void snap()
    {
        for (LivingEntity e : this.hittable(this.getBoundingBox().inflate(CLAW_REACH, 1.0D, CLAW_REACH)))
        {
            if (this.inArc(e, CLAW_REACH, CLAW_ARC_COS) && e.hurt(this.damageSources().mobAttack(this), SNAP_DAMAGE))
            {
                throwFrom(e, this.position(), SNAP_KNOCKBACK, 0.2D);
            }
        }
    }

    /** One of the pinch's two nips, on its foe in reach (each counts: the second isn't lost to the first's moment of
     *  not being hurt again). */
    void nip(@Nullable LivingEntity target)
    {
        if (target != null && target.isAlive() && this.mayHit(target) && this.inArc(target, CLAW_REACH, CLAW_ARC_COS))
        {
            target.invulnerableTime = 0;
            target.hurt(this.damageSources().mobAttack(this), PINCH_DAMAGE / 2.0F);
        }
    }

    /** Pulls into its shell, waits, peeks out, comes out. It holds still the whole time. */
    private class HideGoal extends Goal
    {
        private int timer;
        private int hiddenFor;

        HideGoal()
        {
            // (not JUMP: the float goal's, so a hermit in its shell in water still floats)
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            return CinderHermit.this.wantsToHide && CinderHermit.this.isAlive();
        }

        @Override
        public boolean canContinueToUse()
        {
            return CinderHermit.this.shell != Shell.OUT && CinderHermit.this.isAlive();
        }

        @Override
        public void start()
        {
            CinderHermit self = CinderHermit.this;
            self.wantsToHide = false;
            self.shell = Shell.HIDING;
            self.setHidden(true);
            self.getNavigation().stop();
            self.playAnimation("hide");
            this.timer = 0;
        }

        @Override
        public void stop()
        {
            CinderHermit self = CinderHermit.this;
            if (self.shell != Shell.OUT)
            {
                self.shell = Shell.OUT;
                self.setHidden(false);
                if (self.isAlive())
                {
                    self.endMove();
                }
            }
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            CinderHermit self = CinderHermit.this;
            self.getNavigation().stop();
            this.timer++;
            switch (self.shell)
            {
                case HIDING ->
                {
                    if (this.timer >= HIDE_TICKS)
                    {
                        self.shell = Shell.HIDDEN;
                        this.timer = 0;
                        this.hiddenFor = Mth.nextInt(self.random, HIDDEN_MIN_TICKS, HIDDEN_MAX_TICKS);
                    }
                }
                case HIDDEN ->
                {
                    if (this.timer >= this.hiddenFor)
                    {
                        self.shell = Shell.PEEKING;
                        this.timer = 0;
                        self.playAnimation("peek");
                    }
                }
                case PEEKING ->
                {
                    // It always has its whole look round (eyes out, left, right) first; angry, it strikes as it comes out.
                    if (this.timer >= PEEK_TICKS)
                    {
                        self.shell = Shell.COMING_OUT;
                        this.timer = 0;
                        self.playAnimation("come_out");
                    }
                }
                case COMING_OUT ->
                {
                    if (this.timer == COME_OUT_FREE_TICK)
                    {
                        self.setHidden(false);
                    }
                    if (this.timer >= COME_OUT_TICKS)
                    {
                        self.shell = Shell.OUT;
                        self.setHidden(false);
                        self.endMove();
                        self.strikeNow = self.getTarget() != null;
                    }
                }
                default ->
                {
                }
            }
        }
    }

    /** Its fight: it closes in, and in reach of its claws snaps or pinches. A move plays to its end; it turns to its foe
     *  until just before the blow. */
    private class FightGoal extends Goal
    {
        private enum Phase { CHASE, SNAP, PINCH }

        private Phase phase = Phase.CHASE;
        private int timer;
        private int snapCooldown;
        private int pinchCooldown;
        private int repath;

        FightGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = CinderHermit.this.getTarget();
            return target != null && target.isAlive() && CinderHermit.this.canAttack(target) && !CinderHermit.this.isHidden();
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.phase != Phase.CHASE || this.canUse();
        }

        @Override
        public void start()
        {
            this.phase = Phase.CHASE;
            this.timer = 0;
            this.repath = 0;
            if (CinderHermit.this.strikeNow)
            {
                CinderHermit.this.strikeNow = false;
                this.snapCooldown = 0;
                this.pinchCooldown = 0;
            }
        }

        @Override
        public void stop()
        {
            CinderHermit.this.getNavigation().stop();
            if (CinderHermit.this.shell == Shell.OUT)
            {
                CinderHermit.this.endMove();
            }
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
            CinderHermit.this.getNavigation().stop();
            CinderHermit.this.playAnimation(animation);
        }

        private void toChase()
        {
            this.phase = Phase.CHASE;
            this.timer = 0;
            CinderHermit.this.endMove();
        }

        @Override
        public void tick()
        {
            CinderHermit self = CinderHermit.this;
            LivingEntity target = self.getTarget();
            this.snapCooldown = Math.max(0, this.snapCooldown - 1);
            this.pinchCooldown = Math.max(0, this.pinchCooldown - 1);
            this.timer++;
            switch (this.phase)
            {
                case SNAP ->
                {
                    if (target != null && this.timer < SNAP_COMMIT_TICK)
                    {
                        self.turnTowards(target.position(), MAX_TURN * 1.5F);
                    }
                    if (this.timer == SNAP_HIT_TICK)
                    {
                        self.snap();
                    }
                    if (this.timer >= SNAP_TICKS)
                    {
                        this.snapCooldown = SNAP_COOLDOWN;
                        this.toChase();
                    }
                }
                case PINCH ->
                {
                    if (target != null && this.timer < PINCH_HIT_TICK)
                    {
                        self.turnTowards(target.position(), MAX_TURN * 1.5F);
                    }
                    if (this.timer == PINCH_HIT_TICK || this.timer == PINCH_HIT2_TICK)
                    {
                        self.nip(target);
                    }
                    if (this.timer >= PINCH_TICKS)
                    {
                        this.pinchCooldown = PINCH_COOLDOWN;
                        this.toChase();
                    }
                }
                default -> this.tickChase(target);
            }
        }

        private void tickChase(@Nullable LivingEntity target)
        {
            CinderHermit self = CinderHermit.this;
            if (target == null)
            {
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (self.inArc(target, CLAW_REACH, CLAW_ARC_COS) && (this.snapCooldown == 0 || this.pinchCooldown == 0))
            {
                boolean snap = this.snapCooldown == 0 && (this.pinchCooldown > 0 || self.random.nextFloat() < SNAP_CHANCE);
                if (snap)
                {
                    this.begin(Phase.SNAP, "snap");
                }
                else
                {
                    this.begin(Phase.PINCH, "pinch");
                }
                return;
            }
            double dx = target.getX() - self.getX();
            double dz = target.getZ() - self.getZ();
            double reach = Math.sqrt(dx * dx + dz * dz) - target.getBbWidth() / 2.0D;
            if (reach <= CLAW_REACH - 0.5D)
            {
                // In reach, waiting for a claw: it stands and turns to it (never shoving into it).
                self.getNavigation().stop();
                self.turnTowards(target.position(), MAX_TURN);
                return;
            }
            this.repath = BossChase.chase(self, target, HERMIT_CHASE_SPEED, this.repath, 10);
        }
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_SHELL, this.getShell().label());
        tag.putFloat(TAG_HIDE_MARK, this.hideMark);
        if (this.den != null)
        {
            tag.put(TAG_DEN, NbtUtils.writeBlockPos(this.den));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        FireDesigns.Design kept = this.getShell();
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_HIDE_MARK))
        {
            this.hideMark = tag.getFloat(TAG_HIDE_MARK);
        }
        if (tag.contains(TAG_DEN))
        {
            this.setDen(NbtUtils.readBlockPos(tag.getCompound(TAG_DEN)));
        }
        FireDesigns.Design shell = kept;
        if (tag.contains(TAG_SHELL))
        {
            String label = tag.getString(TAG_SHELL);
            for (FireDesigns.Design d : SHELLS)
            {
                if (d.label().equals(label))
                {
                    shell = d;
                }
            }
        }
        else if (SHELLS.contains(this.getDesign()))
        {
            shell = this.getDesign();
        }
        this.setDesign(shell.number());
    }

    /** Its shell's label (for the tests' log). */
    @Override
    public String toString()
    {
        return super.toString() + " in " + this.getShell().label();
    }
}
