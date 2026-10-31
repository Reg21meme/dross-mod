package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.FireAnims;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.FireDesigns;
import com.reg21meme.dross.boss.golem.LavaPools;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageLeader;
import com.reg21meme.dross.boss.rimefall.RimefallBodyPart;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

import static com.reg21meme.dross.boss.emberdactyl.EmberTuning.*;

/**
 * The Emberdactyl and its young reborn, fighting (the user's plan, October 27-29, 2026; every number in
 * {@link EmberTuning}). Built on the showcase's fire model ({@link FireCandidate}: its look, its glow and see-through
 * passes, the fire falling from its wings, its death's cracks, its animations synced and their sounds), but alive: it
 * flies (no gravity, straight at where it's going, kept over the ground), picks its moves by where its target is, and
 * dies by its own death animation.
 * <ul>
 *   <li><b>Resting</b>: the adult at its volcano perches on its crater's rim, guarding its egg; one from a spawn egg (or
 *       the young with nobody near) hovers over where it was spawned. It wakes when a player comes within
 *       {@link EmberTuning#NOTICE_RANGE} blocks of its volcano's foot (of its home, without a volcano) or anything hurts
 *       it: its screech (heard from afar) and its boss bar.</li>
 *   <li><b>Fighting</b>: mostly in the air, circling its target; it comes down to the ground for its melee moves
 *       (after its dive bomb it lies stuck a few seconds: the window to hit it up close). Moves: dive bomb, fire
 *       tornadoes, strafing run, talon grab, wing buffet, screech; on the ground: beak snap, wing buffet, screech; the
 *       young also a dash. Never further than its leash from home.</li>
 *   <li>With nobody to fight for {@link EmberTuning#GIVE_UP_TICKS} ticks it goes back to resting (but one from a spawn
 *       egg or a summon command, which fights from the moment it comes: the user's third look, "make them insta
 *       hostile as soon as the two spawn in").</li>
 *   <li>The Rage Wand ({@link RageLeader}): raged, it wakes and goes for the nearest raged mob of another team before
 *       any player (another Emberdactyl too: a fight in the air), and never hurts its own team.</li>
 * </ul>
 * Its hitboxes: its body (its own), and its head, neck, tail and wings ({@link RimefallBodyPart}s: hitting any hurts it),
 * placed for flying or for standing by its animation.
 */
public abstract class EmberBoss extends FireCandidate implements RageLeader
{
    /** Where it is in its life. */
    protected enum Phase
    {
        REST, WAKE, FIGHT, DYING
    }

    /** Its moves. */
    protected enum Move
    {
        NONE, DIVE, STUCK, TORNADO, STRAFE, GRAB, SNAP, BUFFET, SCREECH, DASH, TAKE_OFF, GLIDE
    }

    private static final String TAG_HOME = "Home";
    private static final String TAG_NEST = "Nest";
    private static final String TAG_PERCH = "Perch";
    private static final String TAG_PERCH_YAW = "PerchYaw";
    /** (on whoever it let go: when, so their fall is capped) */
    private static final String TAG_DROPPED = "DrossEmberDropped";
    private static final String TAG_VOLCANO = "Volcano";
    private static final String TAG_ENRAGED = "Enraged";
    private static final String TAG_PHASE = "Phase";
    private static final String TAG_HOSTILE = "Hostile";
    /** Parts' sizes (width, height) and spots (its own left +x, up from its feet, its front -z), flying and standing:
     *  head, neck, left wing, right wing, left wing tip, right wing tip, tail (measured from its model's poses). */
    private final double[][] flySpots;
    private final double[][] groundSpots;
    private final RimefallBodyPart<EmberBoss>[] parts;

    protected final Kind kind;
    protected Phase phase = Phase.REST;
    protected Move move = Move.NONE;
    protected int moveTick;
    @Nullable
    protected Vec3 home;
    @Nullable
    protected Vec3 nest;
    @Nullable
    protected Vec3 perch;
    protected float perchYaw;
    /** The built volcano it belongs to (its patch's id), or 0. */
    protected long volcano;
    protected boolean enraged;
    /** From a spawn egg or a summon command: it fights from the moment it comes and never goes back to resting. */
    private boolean hostile;
    /** (its tick) when its flight's loop last changed by its speed */
    private int flightLoopSince = -1000;
    @Nullable
    private LivingEntity target;
    private int noTargetTicks;
    private int pause;
    private final Map<Move, Integer> ready = new EnumMap<>(Move.class);
    private double orbitAngle;
    /** The spot a move locked (where it dives, its strafing line's end, its dash's end). */
    @Nullable
    private Vec3 locked;
    @Nullable
    private Vec3 lineFrom;
    @Nullable
    private LivingEntity carried;
    @Nullable
    private Vec3 dropSpot;
    /** The height (feet) it lets go of whoever it carries at, over the ground under the drop. */
    private double dropLevel;
    /** (its move's tick) when its grab's swoop started, and when it came to the drop's height; where it swoops to */
    private int swoopFrom;
    @Nullable
    private Vec3 swoopSpot;
    private int dropTick;
    private final Set<UUID> hitThisMove = new HashSet<>();
    /** The young's fire trail: the spot (along the ground) it last laid fire at. */
    @Nullable
    private Vec3 trailFrom;
    private boolean grounded;
    @Nullable
    private ServerBossEvent bar;
    /** (the server's tick) when it last looked after its boss bar */
    private long barTick;
    /** Dying: where it comes down from and to, and how many ticks it glides before its death animation starts. */
    @Nullable
    private Vec3 fallFrom;
    @Nullable
    private Vec3 fallTo;
    private int fallDelay;

    @SuppressWarnings("unchecked")
    protected EmberBoss(EntityType<? extends EmberBoss> type, Level level, Kind kind, int design, double[][] sizes,
                        double[][] flySpots, double[][] groundSpots)
    {
        super(type, level);
        this.kind = kind;
        this.setNoAi(false);
        this.setSilent(false);
        this.setNoGravity(true);
        // (it flew through blocks until October 30: the user saw the young "clip through walls", and both go up through
        // the bedrock arena's roof, where, inside it, they took its top for the ground; now blocks stop it like any mob)
        this.setDesign(design);
        this.flySpots = flySpots;
        this.groundSpots = groundSpots;
        this.parts = new RimefallBodyPart[sizes.length];
        for (int i = 0; i < sizes.length; i++)
        {
            this.parts[i] = new RimefallBodyPart<>(this, (float) sizes[i][0], (float) sizes[i][1]);
        }
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
        this.orbitAngle = this.random.nextDouble() * Math.PI * 2.0D;
    }

    // ---------------------------------------------------------------- what the adult and the young each say

    /** Its boss bar's name. */
    protected abstract Component barName();

    /** Its own sound event ("entity.emberdactyl.hurt"...). */
    protected abstract String soundPrefix();

    /** Called the moment it dies (before its death animation). */
    protected void onDeathStart(DamageSource source)
    {
    }

    /** Called every tick of its death; return true once it's to be removed. */
    protected abstract boolean deathTick(int ticks);

    /** True for the young (it dashes). */
    protected boolean dashes()
    {
        return false;
    }

    // ---------------------------------------------------------------- setting it up

    /** Sets where it lives: its home (it rests and fights round it), its nest's egg (or null), its perch on its crater's
     *  rim (or null: it hovers over its home), and its volcano's id (or 0). */
    public void setHome(Vec3 home, @Nullable Vec3 nest, @Nullable Vec3 perch, float perchYaw, long volcano)
    {
        this.home = home;
        this.nest = nest;
        this.perch = perch;
        this.perchYaw = perchYaw;
        this.volcano = volcano;
    }

    public long volcano()
    {
        return this.volcano;
    }

    @Nullable
    public Vec3 nest()
    {
        return this.nest;
    }

    /** True once it's awake and fighting (for tests and other areas). */
    public boolean isFighting()
    {
        return this.phase == Phase.WAKE || this.phase == Phase.FIGHT;
    }

    /** True once the adult has turned faster at half health. */
    public boolean isEnraged()
    {
        return this.enraged;
    }

    /** True if its boss bar is on this player's screen. */
    public boolean showsBarTo(ServerPlayer player)
    {
        return this.bar != null && this.bar.getPlayers().contains(player);
    }

    /** Its head, neck, wings and tail (hitboxes of their own). */
    public RimefallBodyPart<EmberBoss>[] bodyParts()
    {
        return this.parts;
    }

    /** Whom it's fighting now (or null). */
    @Nullable
    public LivingEntity fightTarget()
    {
        return this.target;
    }

    /** True while it carries someone in its talons. */
    public boolean isCarrying()
    {
        return this.carried != null;
    }

    /** (tests) Makes one of its moves at once, against {@code target}. */
    void testMove(Move m, LivingEntity target)
    {
        this.phase = Phase.FIGHT;
        this.target = target;
        this.grounded = m == Move.SNAP;
        this.start(m);
    }

    /** Starts it fighting at once (the young bursting from its egg), going for {@code target} if it's a fair one. */
    public void startFighting(@Nullable LivingEntity target)
    {
        this.phase = Phase.WAKE;
        this.target = target != null && this.canFight(target) ? target : null;
        this.playAnimation("screech");
        this.moveTick = 0;
    }

    /** Makes it fight from now on, never going back to resting (a spawn egg's, a summon command's), and wakes it. */
    public void makeHostile()
    {
        this.hostile = true;
        if (this.phase == Phase.REST)
        {
            this.startFighting(null);
        }
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        if (this.home == null)
        {
            this.home = this.position();
        }
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data, tag);
        if (reason == MobSpawnType.SPAWN_EGG)
        {
            this.makeHostile();
        }
        return out;
    }

    // ---------------------------------------------------------------- the Rage Wand

    /** It has no followers (the young comes out of its own egg, on its own team). */
    @Override
    public List<? extends net.minecraft.world.entity.Mob> rageFollowers()
    {
        return List.of();
    }

    /** Raged, it wakes up and goes for the nearest raged enemy (or whoever comes). */
    @Override
    public void onRaged()
    {
        if (this.phase == Phase.REST)
        {
            this.startFighting(Rage.nearestEnemy(this));
        }
        else if (this.target != null && Rage.areTeammates(this, this.target))
        {
            this.target = null;
        }
    }

    // ---------------------------------------------------------------- its hitboxes

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

    /** Its body's hitbox (its head, neck, wings and tail are its parts). */
    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return this.kind == null ? super.getDimensions(pose) : EntityDimensions.scalable(this.kind.width(), this.kind.height());
    }

    /** True while it's standing on the ground (its parts placed for standing). */
    private boolean standing()
    {
        String anim = this.animation();
        return anim.equals("perch") || anim.equals("walk") || anim.equals("snap") || anim.equals("death");
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        RimefallBodyPart.place(this, this.parts, this.standing() ? this.groundSpots : this.flySpots, 1.0F);
    }

    // ---------------------------------------------------------------- alive, not a statue

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        // (fire never: its own fire trail and tornadoes, lava; the user, October 30: "now the Emberdactyl takes damage
        // from its own fires": since blocks stop it, it stands in what it lays, and this skipped the game's own rule for
        // a fire-immune mob)
        if (source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypeTags.IS_FIRE))
        {
            return true;
        }
        return this.isRemoved() || this.isInvulnerable() && !source.is(DamageTypes.FELL_OUT_OF_WORLD) && !source.isCreativePlayer();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful()
    {
        return true;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source)
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        return InteractionResult.PASS;
    }

    @Override
    protected float cueVolume(FireAnims.Mob mob, String event)
    {
        return event.endsWith(".screech") ? SCREECH_VOLUME : VOLUME;
    }

    @Override
    protected float getSoundVolume()
    {
        return VOLUME;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return sound(this.soundPrefix() + ".hurt");
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound()
    {
        return null;   // (its death animation's own: FireSounds.CUES)
    }

    protected static SoundEvent sound(String event)
    {
        return SoundEvent.createVariableRangeEvent(new ResourceLocation(Dross.MODID, event));
    }

    // ---------------------------------------------------------------- moving (it flies)

    /** It moves exactly as fast as it's told, through the air, no friction and no falling; blocks stop it (never
     *  through a wall, a roof or a hill), and when a wall stops it it rises to get over it. */
    @Override
    public void travel(Vec3 input)
    {
        if (this.isDeadOrDying())
        {
            this.setDeltaMovement(Vec3.ZERO);
        }
        Vec3 want = this.getDeltaMovement();
        Vec3 go = this.isDeadOrDying() ? want : this.wingsClip(want);
        boolean wingBlocked = Math.abs(go.x - want.x) > 1.0E-7D || Math.abs(go.z - want.z) > 1.0E-7D;
        if (go != want)
        {
            this.setDeltaMovement(go);  // (a wing against a wall stops that way, like its body)
        }
        this.move(MoverType.SELF, go);
        if ((this.horizontalCollision || wingBlocked) && !this.isDeadOrDying())
        {
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x, Math.max(v.y, CLIMB_ON_BUMP), v.z);
        }
        this.calculateEntityAnimation(true);
    }

    /** Its wings' hitboxes (its parts: the two wings, then the two wing tips). */
    private static final int[] WINGS = {2, 3, 4, 5};

    /**
     * Its wings are solid too (the user, October 31: "make it so the wings count for the Emberdactyls like a physical
     * box ... those can't also clip through walls"): {@code move} cut short so none of its wings' hitboxes runs into a
     * block, as Minecraft does for its body. A wing already inside a block (it came down beside a hill) can still move
     * out of it.
     */
    private Vec3 wingsClip(Vec3 move)
    {
        if (move.lengthSqr() < 1.0E-10D)
        {
            return move;
        }
        Vec3 at = this.position();
        for (int i : WINGS)
        {
            if (i < this.parts.length)
            {
                move = Entity.collideBoundingBox(this, move, this.partBoxAt(i, at, this.yBodyRot), this.level(), List.of());
            }
        }
        return move;
    }

    /** Where one of its parts' hitboxes is with it at {@code at} facing {@code yawDeg} (as {@link RimefallBodyPart#place}
     *  puts it). */
    private AABB partBoxAt(int i, Vec3 at, float yawDeg)
    {
        double[] spot = (this.standing() ? this.groundSpots : this.flySpots)[i];
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        double cos = Mth.cos(yaw);
        double sin = Mth.sin(yaw);
        double x = at.x + spot[0] * cos + spot[2] * sin;
        double y = at.y + spot[1];
        double z = at.z + spot[0] * sin - spot[2] * cos;
        double w = this.parts[i].getBbWidth() * 0.5D;
        return new AABB(x - w, y, z - w, x + w, y + this.parts[i].getBbHeight(), z + w);
    }

    /**
     * Whether it can turn to {@code yaw} without swinging a wing into a block (one already in a block may turn on, to
     * get out). When it can't, it's pushed a little away from what's in the way, so it has room to turn next time.
     */
    private boolean wingsCanTurn(float yaw)
    {
        if (this.isDeadOrDying() || this.isPassenger())
        {
            return true;
        }
        Vec3 at = this.position();
        Vec3 away = Vec3.ZERO;
        for (int i : WINGS)
        {
            if (i >= this.parts.length)
            {
                continue;
            }
            AABB then = this.partBoxAt(i, at, yaw).deflate(0.01D);
            if (!this.level().noCollision(this, then) && this.level().noCollision(this, this.partBoxAt(i, at, this.yBodyRot).deflate(0.01D)))
            {
                Vec3 c = then.getCenter();
                away = away.add(at.x - c.x, 0.0D, at.z - c.z);
            }
        }
        if (away.lengthSqr() < 1.0E-8D)
        {
            return true;
        }
        this.setDeltaMovement(this.getDeltaMovement().add(away.normalize().scale(WING_NUDGE)));
        return false;
    }

    /** It doesn't shove what's beside it (it never did while it flew through everything). */
    @Override
    protected void pushEntities()
    {
    }

    /** The ground's height (the feet of something standing on it) at x, z, for it at its own height ({@link #groundUnder}). */
    protected double groundAt(double x, double z)
    {
        return groundUnder(this.level(), x, this.getY(), z);
    }

    /**
     * The ground's height (the feet of something standing on it) at x, z for something at height {@code y}: the top of
     * the column (Minecraft's height map: what blocks movement, leaves aside), unless there's a roof over {@code y} (the
     * bedrock test arena's: the user, October 30, "a roof tall enough to fit the Emberdactyl"; the height map is the
     * roof's top there, and it flew up through it): then the floor under {@code y}, or, inside something solid there (a
     * wall it flies through, a hill it dips into), the top of that.
     */
    public static double groundUnder(Level level, double x, double y, double z)
    {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        int from = Mth.floor(y) + 2;
        if (from >= top)
        {
            return top;                        // (open sky over it: almost always)
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(bx, from, bz);
        if (blocks(level, pos))
        {
            while (pos.getY() < top && blocks(level, pos))
            {
                pos.move(Direction.UP);
            }
            return pos.getY();
        }
        int bottom = level.getMinBuildHeight();
        while (pos.getY() > bottom && !blocks(level, pos.below()))
        {
            pos.move(Direction.DOWN);
        }
        return pos.getY();
    }

    /** What Minecraft's MOTION_BLOCKING_NO_LEAVES height map counts: a block that stops movement, or a fluid, not leaves. */
    private static boolean blocks(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return (state.blocksMotion() || !state.getFluidState().isEmpty()) && !(state.getBlock() instanceof LeavesBlock);
    }

    private double speedScale()
    {
        return this.enraged ? ENRAGED_SPEED : 1.0D;
    }

    /** Flies towards {@code to} at up to {@code speed} blocks a tick, easing its speed ({@code accel} a tick, 1: at once). */
    protected void steer(Vec3 to, double speed, double accel)
    {
        Vec3 d = to.subtract(this.position());
        double dist = d.length();
        Vec3 want = dist < 1.0E-3D ? Vec3.ZERO : d.scale(Math.min(speed * this.speedScale(), dist) / dist);
        Vec3 v = this.getDeltaMovement();
        this.setDeltaMovement(v.add(want.subtract(v).scale(accel)));
        Vec3 flat = new Vec3(want.x, 0.0D, want.z);
        if (flat.lengthSqr() > 0.01D)
        {
            this.turnTo(flat);
        }
        else if (this.target != null)
        {
            this.turnTo(this.target.position().subtract(this.position()));
        }
    }

    /** Turns its body and head towards {@code dir} (along the ground), at most {@link EmberTuning#TURN} a tick. */
    protected void turnTo(Vec3 dir)
    {
        if (dir.x * dir.x + dir.z * dir.z < 1.0E-6D)
        {
            return;
        }
        float want = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.getYRot(), want, TURN * (float) this.speedScale() * (this.dashes() ? 2.0F : 1.0F));
        if (!this.wingsCanTurn(yaw))
        {
            float half = Mth.approachDegrees(this.getYRot(), yaw, Math.abs(Mth.wrapDegrees(yaw - this.getYRot())) * 0.5F);
            if (!this.wingsCanTurn(half))
            {
                return;                 // a wing would swing into a wall: it waits (pushed a little away from it)
            }
            yaw = half;
        }
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** A spot over the ground at least {@code clearance} up. */
    private Vec3 overGround(Vec3 spot, double clearance)
    {
        return new Vec3(spot.x, Math.max(spot.y, this.groundAt(spot.x, spot.z) + clearance), spot.z);
    }

    /** {@code spot} pulled back inside its leash round its home. */
    private Vec3 leashed(Vec3 spot)
    {
        if (this.home == null)
        {
            return spot;
        }
        double dx = spot.x - this.home.x;
        double dz = spot.z - this.home.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d <= this.kind.leash())
        {
            return spot;
        }
        double k = this.kind.leash() / d;
        return new Vec3(this.home.x + dx * k, spot.y, this.home.z + dz * k);
    }

    private void loop(String anim)
    {
        if (!anim.equals(this.animation()))
        {
            this.playAnimation(anim);
        }
    }

    /** Its flight's loop by its speed: {@code fast} over {@code threshold} (blocks a tick), else {@code slow}; with a
     *  margin either side and a hold after each change, so a speed near the line doesn't flick it from one to the other
     *  (each change starts the loop again: its head snapped back; the user's third look). */
    private void flightLoop(String fast, String slow, double threshold)
    {
        String now = this.animation();
        double speed = this.getDeltaMovement().length();
        String want;
        if (now.equals(fast))
        {
            want = speed < threshold * (1.0D - FLIGHT_LOOP_MARGIN) ? slow : fast;
        }
        else if (now.equals(slow))
        {
            want = speed > threshold * (1.0D + FLIGHT_LOOP_MARGIN) ? fast : slow;
        }
        else
        {
            want = speed > threshold ? fast : slow;
        }
        if (!want.equals(now) && (this.tickCount - this.flightLoopSince >= FLIGHT_LOOP_HOLD
                || !now.equals(fast) && !now.equals(slow)))
        {
            this.flightLoopSince = this.tickCount;
            this.playAnimation(want);
        }
    }

    /** The length of one of its animations (ticks). */
    protected int length(String anim)
    {
        FireAnims.Mob mob = this.anims();
        FireAnims.Anim a = mob == null ? null : mob.get(anim);
        return a == null ? 20 : Math.max(1, Math.round(a.seconds() * 20.0F));
    }

    /** The tick of a moment {@code share} of the way into one of its animations. */
    private int at(String anim, double share)
    {
        return (int) Math.round(this.length(anim) * share);
    }

    // ---------------------------------------------------------------- its mind

    @Override
    protected void customServerAiStep()
    {
        super.customServerAiStep();
        if (this.home == null)
        {
            this.home = this.position();
        }
        if (this.phase == Phase.DYING || this.isDeadOrDying())
        {
            return;
        }
        this.updateBar();
        switch (this.phase)
        {
            case REST -> this.rest();
            case WAKE -> this.wake();
            case FIGHT -> this.fight();
            default ->
            {
            }
        }
        if (this.phase == Phase.FIGHT && this.dashes() && !this.grounded)
        {
            this.fireTrail();   // the young: a band of fire under it wherever it flies
        }
        else
        {
            this.trailFrom = null;
        }
        this.carry();
        // never in the ground, not even a little (blocks stop it, but Minecraft lets something already inside a block
        // move on through it: half a block into the bedrock arena's 1-block floor it sank through it; the user, October
        // 30, "the Emberdactyl clips through the floor, through the bedrock")
        double floor = this.groundAt(this.getX(), this.getZ());
        if (this.getY() < floor - FLOOR_EMBED)
        {
            this.setPos(this.getX(), floor, this.getZ());
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        }
    }

    /** A player it could fight: alive, not creative or a spectator, near enough its home (or a raged enemy, never a
     *  teammate or its own kind otherwise). */
    protected boolean canFight(LivingEntity e)
    {
        if (e == null || !e.isAlive() || e.level() != this.level() || e == this || Rage.areTeammates(this, e))
        {
            return false;
        }
        if ((e instanceof EmberBoss || e instanceof FireCandidate) && !Rage.areEnemies(this, e))
        {
            return false;
        }
        if (e instanceof Player p && (p.isCreative() || p.isSpectator()))
        {
            return false;
        }
        if (this.home == null)
        {
            return true;
        }
        double dx = e.getX() - this.home.x;
        double dz = e.getZ() - this.home.z;
        return Math.sqrt(dx * dx + dz * dz) <= this.kind.leash() + NOTICE_RANGE;
    }

    /** The nearest player within {@code range} of {@code from} it could fight (along the ground), or null. */
    @Nullable
    private Player nearestPlayer(Vec3 from, double range)
    {
        Player best = null;
        double bestD = range * range;
        for (Player p : this.level().players())
        {
            if (!this.canFight(p))
            {
                continue;
            }
            double dx = p.getX() - from.x;
            double dz = p.getZ() - from.z;
            double d = dx * dx + dz * dz;
            if (d <= bestD)
            {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** How near a player must come to wake it: its volcano's foot plus the notice range, or the notice range of home. */
    private double noticeRange()
    {
        return this.nest != null && this.volcano != 0L || this.perch != null ? VOLCANO_FOOT + NOTICE_RANGE : NOTICE_RANGE;
    }

    private void rest()
    {
        if (this.perch != null)
        {
            // perched on its crater's rim, guarding its egg
            if (this.position().distanceToSqr(this.perch) > 4.0D)
            {
                this.steer(this.perch, this.kind.cruise(), 0.25D);
                this.loop("fly");
            }
            else
            {
                this.setDeltaMovement(Vec3.ZERO);
                this.setPos(this.perch.x, this.perch.y, this.perch.z);
                this.setYRot(Mth.approachDegrees(this.getYRot(), this.perchYaw, TURN));
                this.yBodyRot = this.getYRot();
                this.yHeadRot = this.getYRot();
                this.loop("perch");
            }
        }
        else
        {
            // hovering over its home, slowly round
            this.orbitAngle += 0.01D;
            Vec3 spot = this.home.add(Math.cos(this.orbitAngle) * CALM_CIRCLE, 0.0D, Math.sin(this.orbitAngle) * CALM_CIRCLE);
            spot = new Vec3(spot.x, this.groundAt(this.home.x, this.home.z) + CALM_HOVER, spot.z);
            this.steer(this.overGround(spot, MIN_CLEARANCE), this.kind.cruise() * 0.4D, 0.08D);
            this.flightLoop("glide", "hover", 0.2D);
        }
        if (this.tickCount % 10 == 0)
        {
            Vec3 from = this.nest != null ? this.nest : this.home;
            Player p = this.nearestPlayer(from, this.noticeRange());
            if (p != null)
            {
                this.startFighting(p);
            }
            else if (Rage.hasEnemyNear(this))
            {
                this.startFighting(Rage.nearestEnemy(this));   // raged, a raged enemy near wakes it
            }
        }
    }

    private void wake()
    {
        // its screech (heard from afar), lifting off
        this.moveTick++;
        Vec3 up = this.overGround(this.position().add(0.0D, 0.6D, 0.0D), MIN_CLEARANCE);
        this.steer(up, this.kind.cruise() * 0.5D, 0.2D);
        if (this.target != null)
        {
            this.turnTo(this.target.position().subtract(this.position()));
        }
        if (this.moveTick >= this.length("screech"))
        {
            this.phase = Phase.FIGHT;
            this.move = Move.NONE;
            this.moveTick = 0;
            this.pause = this.kind.pause();
        }
    }

    private void fight()
    {
        // raged: a raged enemy of another team before anyone else (its own team never)
        if (Rage.isRaged(this) && !Rage.areEnemies(this, this.target))
        {
            LivingEntity enemy = Rage.nearestEnemy(this);
            if (enemy != null && this.canFight(enemy))
            {
                if (this.move != Move.NONE && this.move != Move.STUCK)
                {
                    this.endMove();
                }
                this.target = enemy;
            }
        }
        // whom it goes for: its target while it's fair, else the nearest player near its home, else whatever hurt it
        if (this.target == null || !this.canFight(this.target))
        {
            Vec3 from = this.home;
            this.target = this.nearestPlayer(from, this.kind.leash() + NOTICE_RANGE);
            LivingEntity hurtBy = this.getLastHurtByMob();
            if (this.target == null && hurtBy != null && this.canFight(hurtBy))
            {
                this.target = hurtBy;
            }
        }
        if (this.target == null)
        {
            if (++this.noTargetTicks > GIVE_UP_TICKS && this.move == Move.NONE && !this.hostile)
            {
                this.phase = Phase.REST;
                this.noTargetTicks = 0;
            }
            if (this.move == Move.NONE || this.move == Move.STUCK)
            {
                this.circle(this.home);
            }
            else
            {
                this.endMove();
            }
            return;
        }
        this.noTargetTicks = 0;
        if (this.move == Move.NONE)
        {
            // round its target, over the ground under it (a flying one too: two Emberdactyls circling each other's
            // heads would climb for ever)
            Vec3 at = this.target.position();
            double ground = this.groundAt(at.x, at.z);
            this.circle(at.y - ground > 3.0D ? new Vec3(at.x, ground, at.z) : at);
            if (--this.pause <= 0)
            {
                this.pick();
            }
            return;
        }
        this.moveTick++;
        switch (this.move)
        {
            case DIVE -> this.dive();
            case STUCK -> this.stuck();
            case TORNADO -> this.tornado();
            case STRAFE -> this.strafe();
            case GRAB -> this.grab();
            case SNAP -> this.snap();
            case BUFFET -> this.buffet();
            case SCREECH -> this.screech();
            case DASH -> this.dash();
            case TAKE_OFF -> this.takeOff();
            case GLIDE -> this.glideIn();
            default -> this.endMove();
        }
    }

    /** Circles round {@code centre}, high over it, between its moves. */
    private void circle(Vec3 centre)
    {
        this.grounded = false;
        this.orbitAngle += 0.035D * this.speedScale() * (this.dashes() ? 2.0D : 1.0D);
        Vec3 spot = centre.add(Math.cos(this.orbitAngle) * this.kind.orbit(), this.kind.orbitHeight(),
                Math.sin(this.orbitAngle) * this.kind.orbit());
        spot = this.overGround(this.leashed(spot), MIN_CLEARANCE);
        this.steer(spot, this.kind.cruise(), ACCELERATION);
        this.flightLoop("fly", "hover", 0.3D);
    }

    private boolean isReady(Move m)
    {
        return this.tickCount >= this.ready.getOrDefault(m, 0);
    }

    private int cooldown(int ticks)
    {
        return (int) Math.round(ticks * this.kind.cooldowns() * (this.enraged ? ENRAGED_COOLDOWNS : 1.0D));
    }

    /** Picks its next move by where its target is (the moves on cooldown drop out, the rest share their chances). */
    private void pick()
    {
        double d = Math.sqrt(Math.pow(this.target.getX() - this.getX(), 2) + Math.pow(this.target.getZ() - this.getZ(), 2));
        List<Move> moves = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        boolean young = this.dashes();
        if (this.target instanceof EmberBoss other)
        {
            // a fight in the air with another Emberdactyl (the Rage Wand): its talons, wings, voice and tornadoes; its
            // dive only onto one lying stuck on the ground
            add(moves, weights, Move.GRAB, DUEL_STRIKE);
            add(moves, weights, Move.BUFFET, DUEL_BUFFET);
            add(moves, weights, Move.SCREECH, DUEL_SCREECH);
            add(moves, weights, Move.TORNADO, DUEL_TORNADO);
            if (other.grounded)
            {
                add(moves, weights, Move.DIVE, DUEL_DIVE);
            }
            if (young)
            {
                add(moves, weights, Move.DASH, DUEL_DASH);
            }
        }
        else if (d < CLOSE)
        {
            add(moves, weights, Move.GRAB, young ? YOUNG_CLOSE_GRAB : CLOSE_GRAB);
            add(moves, weights, Move.BUFFET, young ? YOUNG_CLOSE_BUFFET : CLOSE_BUFFET);
            add(moves, weights, Move.DIVE, young ? YOUNG_CLOSE_DIVE : CLOSE_DIVE);
            add(moves, weights, Move.SCREECH, young ? YOUNG_CLOSE_SCREECH : CLOSE_SCREECH);
            if (young)
            {
                add(moves, weights, Move.DASH, CLOSE_DASH);
            }
            else
            {
                add(moves, weights, Move.TORNADO, CLOSE_TORNADO);
            }
        }
        else if (d < MID)
        {
            add(moves, weights, Move.DIVE, young ? YOUNG_MID_DIVE : MID_DIVE);
            add(moves, weights, Move.TORNADO, young ? YOUNG_MID_TORNADO : MID_TORNADO);
            add(moves, weights, Move.STRAFE, young ? YOUNG_MID_STRAFE : MID_STRAFE);
            add(moves, weights, Move.GRAB, young ? YOUNG_MID_GRAB : MID_GRAB);
            if (young)
            {
                add(moves, weights, Move.DASH, MID_DASH);
            }
        }
        else
        {
            add(moves, weights, Move.GLIDE, FAR_GLIDE);
            add(moves, weights, Move.STRAFE, FAR_STRAFE);
        }
        int total = 0;
        for (int i = 0; i < moves.size(); i++)
        {
            if (this.isReady(moves.get(i)))
            {
                total += weights.get(i);
            }
        }
        if (total == 0)
        {
            this.pause = 5;
            return;
        }
        int roll = this.random.nextInt(total);
        for (int i = 0; i < moves.size(); i++)
        {
            if (!this.isReady(moves.get(i)))
            {
                continue;
            }
            roll -= weights.get(i);
            if (roll < 0)
            {
                this.start(moves.get(i));
                return;
            }
        }
    }

    private static void add(List<Move> moves, List<Integer> weights, Move m, int weight)
    {
        moves.add(m);
        weights.add(weight);
    }

    /** Starts a move. */
    protected void start(Move m)
    {
        this.move = m;
        this.moveTick = 0;
        this.hitThisMove.clear();
        this.locked = null;
        this.lineFrom = null;
        switch (m)
        {
            case DIVE ->
            {
                this.locked = this.target.position();
                this.lineFrom = this.position();
                this.playAnimation("dive");
                this.ready.put(m, this.tickCount + this.cooldown(DIVE_COOLDOWN));
            }
            case TORNADO ->
            {
                this.playAnimation("gust");
                this.ready.put(m, this.tickCount + this.cooldown(TORNADO_COOLDOWN));
            }
            case STRAFE ->
            {
                // a line over the target, from where it is now, on past the target
                Vec3 across = new Vec3(this.target.getX() - this.getX(), 0.0D, this.target.getZ() - this.getZ());
                if (across.lengthSqr() < 1.0D)
                {
                    across = new Vec3(1.0D, 0.0D, 0.0D);
                }
                across = across.normalize();
                this.lineFrom = this.target.position().subtract(across.scale(STRAFE_REACH));
                this.locked = this.leashed(this.target.position().add(across.scale(STRAFE_REACH)));
                this.ready.put(m, this.tickCount + this.cooldown(STRAFE_COOLDOWN));
            }
            case GRAB ->
            {
                this.swoopFrom = 0;          // (its swoop's animation starts when it's in reach: grab())
                this.loop("fly");
                this.ready.put(m, this.tickCount + this.cooldown(GRAB_COOLDOWN));
            }
            case SNAP ->
            {
                this.playAnimation("snap");
                this.ready.put(m, this.tickCount + this.cooldown(SNAP_COOLDOWN) + this.length("snap") * SNAP_PECKS);
            }
            case BUFFET ->
            {
                this.playAnimation("gust");
                this.ready.put(m, this.tickCount + this.cooldown(BUFFET_COOLDOWN));
            }
            case SCREECH ->
            {
                this.playAnimation("screech");
                this.ready.put(m, this.tickCount + (this.dashes() ? YOUNG_SCREECH_COOLDOWN : this.cooldown(SCREECH_COOLDOWN)));
            }
            case DASH ->
            {
                Vec3 across = this.target.position().subtract(this.position());
                if (across.lengthSqr() < 1.0D)
                {
                    across = new Vec3(1.0D, 0.0D, 0.0D);
                }
                this.locked = this.target.position().add(across.normalize().scale(DASH_PAST)).add(0.0D, 1.0D, 0.0D);
                this.playAnimation("fly");
                this.ready.put(m, this.tickCount + this.cooldown(DASH_COOLDOWN));
            }
            case STUCK ->
            {
                this.grounded = true;
                this.playAnimation("perch");
            }
            case TAKE_OFF ->
            {
                this.grounded = false;
                this.playAnimation("fly");
            }
            case GLIDE ->
            {
                this.playAnimation("glide");
                this.ready.put(m, this.tickCount + this.cooldown(GLIDE_COOLDOWN));
            }
            default ->
            {
            }
        }
    }

    protected void endMove()
    {
        this.move = Move.NONE;
        this.moveTick = 0;
        this.pause = (int) Math.round(this.kind.pause() * (this.enraged ? ENRAGED_COOLDOWNS : 1.0D));
        this.locked = null;
        this.lineFrom = null;
        if (this.carried != null)
        {
            this.release();
        }
    }

    // ---------------------------------------------------------------- the moves

    /** Dive bomb: wings swept back, the plunge onto where its target stood, the crash; then stuck on the ground. */
    private void dive()
    {
        int plungeStart = this.at("dive", 0.15D);
        int impact = this.at("dive", 0.62D);
        Vec3 spot = new Vec3(this.locked.x, this.groundAt(this.locked.x, this.locked.z), this.locked.z);
        if (this.moveTick < plungeStart)
        {
            // gathering itself, rising a little
            this.steer(this.position().add(0.0D, 0.4D, 0.0D), this.kind.cruise() * 0.4D, 0.3D);
            this.turnTo(spot.subtract(this.position()));
        }
        else if (this.moveTick < impact)
        {
            double share = (double) (this.moveTick - plungeStart) / Math.max(1, impact - plungeStart);
            Vec3 pos = this.lineFrom.lerp(spot, share * share);
            this.setDeltaMovement(pos.subtract(this.position()));
            this.turnTo(spot.subtract(this.lineFrom));
        }
        else if (this.moveTick == impact)
        {
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(spot.x, spot.y, spot.z);
            this.crash(spot);
        }
        else
        {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.moveTick >= this.length("dive"))
            {
                this.start(Move.STUCK);
            }
        }
    }

    /** Its crash: a ring of blasts and fire round where it lands; everything within {@link EmberTuning#DIVE_RADIUS} hurt
     *  and thrown out and up (thrown even if the hit didn't count: a player just hurt, or burning). */
    private void crash(Vec3 spot)
    {
        double r = DIVE_RADIUS * this.kind.width() / ADULT.width() * 0.3D + DIVE_RADIUS * 0.7D;
        if (this.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, spot.x, spot.y + 1.0D, spot.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            for (int i = 0; i < DIVE_BLASTS; i++)
            {
                double a = Math.PI * 2.0D * i / DIVE_BLASTS + this.random.nextDouble() * 0.5D;
                double d = r * (0.45D + this.random.nextDouble() * 0.4D);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, spot.x + Math.cos(a) * d, spot.y + 0.8D, spot.z + Math.sin(a) * d,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            level.sendParticles(ParticleTypes.LAVA, spot.x, spot.y + 0.5D, spot.z, 90, r * 0.6D, 0.4D, r * 0.6D, 0.0D);
            level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 0.6D, spot.z, 160, r * 0.7D, 0.3D, r * 0.7D, 0.12D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 1.5D, spot.z, 90, r * 0.7D, 1.2D, r * 0.7D, 0.06D);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, spot.x, spot.y + 1.0D, spot.z, 30, r * 0.5D, 0.5D, r * 0.5D, 0.03D);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, VOLUME * 1.6F, 0.5F);
            level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, VOLUME, 0.6F);
            for (int i = 0; i < DIVE_FIRES; i++)
            {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                double d = r * (0.35D + this.random.nextDouble() * 0.6D);
                double x = spot.x + Math.cos(a) * d;
                double z = spot.z + Math.sin(a) * d;
                LavaPools.placeFire(level, BlockPos.containing(x, this.groundAt(x, z), z), true, DIVE_FIRE_MIN, DIVE_FIRE_MAX);
            }
        }
        for (LivingEntity e : this.foesWithin(new AABB(spot, spot).inflate(r, 4.0D, r)))
        {
            Vec3 out = new Vec3(e.getX() - spot.x, 0.0D, e.getZ() - spot.z);
            double d = out.length();
            if (d > r + 1.0D || Math.abs(e.getY() - spot.y) > 4.0D)
            {
                continue;
            }
            e.hurt(this.damageSources().mobAttack(this), DIVE_DAMAGE);
            out = d < 0.1D ? this.facing() : out.scale(1.0D / d);
            double k = 1.0D - (1.0D - DIVE_EDGE_SHARE) * Mth.clamp(d / r, 0.0D, 1.0D);
            this.throwOut(e, out.scale(DIVE_THROW * k), DIVE_LIFT * k);
        }
    }

    /** Lying stuck on the ground after its dive: its melee moves if anyone's near, then it takes off. */
    private void stuck()
    {
        this.setDeltaMovement(Vec3.ZERO);
        this.grounded = true;
        this.turnTo(this.target.position().subtract(this.position()));
        if (this.moveTick >= this.kind.stuck())
        {
            // its bill and wings if its target is near, then up again
            double d = this.target.distanceTo(this);
            if (d < CLOSE && this.isReady(Move.SNAP) && this.random.nextFloat() < STUCK_SNAP_CHANCE)
            {
                this.start(Move.SNAP);
            }
            else if (d < BUFFET_RANGE && this.isReady(Move.BUFFET) && this.random.nextFloat() < 0.5F)
            {
                this.start(Move.BUFFET);
            }
            else if (d < SCREECH_RANGE && this.isReady(Move.SCREECH)
                    && this.random.nextFloat() < (this.dashes() ? YOUNG_STUCK_SCREECH_CHANCE : STUCK_SCREECH_CHANCE))
            {
                this.start(Move.SCREECH);
            }
            else
            {
                this.start(Move.TAKE_OFF);
            }
        }
    }

    private void takeOff()
    {
        Vec3 up = this.overGround(this.position().add(0.0D, this.kind.orbitHeight(), 0.0D), MIN_CLEARANCE);
        this.steer(up, this.kind.cruise(), 0.25D);
        if (this.moveTick > 30 || this.getY() > up.y - 2.0D)
        {
            this.endMove();
        }
    }

    /** Fire tornadoes: the one huge wing beat, then its tornadoes spinning out towards its target. */
    private void tornado()
    {
        this.hold();
        if (this.moveTick == this.at("gust", 0.5625D) && this.level() instanceof ServerLevel level)
        {
            int count = this.kind.tornadoes() + (this.enraged ? 1 : 0);
            Vec3 at = this.target.position().subtract(this.position());
            float base = (float) (Mth.atan2(at.z, at.x) * Mth.RAD_TO_DEG);
            for (int i = 0; i < count; i++)
            {
                float a = (base + (i - (count - 1) / 2.0F) * TORNADO_SPREAD) * Mth.DEG_TO_RAD;
                Vec3 dir = new Vec3(Mth.cos(a), 0.0D, Mth.sin(a));
                Vec3 from = this.position().add(dir.scale(this.kind.width() + 2.0D));
                from = new Vec3(from.x, this.groundAt(from.x, from.z), from.z);
                FireTornado.spawn(level, this, from, dir, this.target, this.kind.tornadoSize());
            }
        }
        if (this.moveTick >= this.length("gust"))
        {
            this.endMove();
        }
    }

    /** Hovering where it is (its gust, its screech in the air). */
    private void hold()
    {
        if (this.grounded)
        {
            this.setDeltaMovement(Vec3.ZERO);
        }
        else
        {
            this.steer(this.position(), this.kind.cruise(), 0.3D);
        }
        if (this.target != null)
        {
            this.turnTo(this.target.position().subtract(this.position()));
        }
    }

    /** Strafing run: to the start of its line, then low along it, breathing fire onto the ground and anyone under it. */
    private void strafe()
    {
        if (this.locked == null || this.lineFrom == null || this.moveTick > 400)
        {
            this.endMove();
            return;
        }
        Vec3 from = this.overGround(this.lineFrom, STRAFE_HEIGHT);
        Vec3 to = this.overGround(this.locked, STRAFE_HEIGHT);
        boolean running = this.hitThisMove.contains(Marks.RUNNING);
        if (!running)
        {
            this.steer(from, this.kind.fast(), ACCELERATION * 2.0D);
            this.loop("fly");
            if (this.position().distanceToSqr(from) < 16.0D || this.moveTick > 120)
            {
                this.hitThisMove.add(Marks.RUNNING);
                this.playAnimation("glide");
            }
            return;
        }
        Vec3 next = this.overGround(this.position().add(to.subtract(this.position()).normalize().scale(1.0D)), STRAFE_HEIGHT);
        this.steer(new Vec3(to.x, next.y, to.z), STRAFE_SPEED * (this.dashes() ? 2.0D : 1.0D), 0.5D);
        this.breathe();
        if (this.position().distanceToSqr(new Vec3(to.x, this.getY(), to.z)) < 9.0D)
        {
            this.endMove();
        }
    }

    /** The glide in (from far off): it glides at its target, low, fire dropping off it onto the ground, and once it's
     *  close it dives (the user's second fight: "make him do his fire glide thing then dive at you once he is closer"). */
    private void glideIn()
    {
        double d = Math.hypot(this.target.getX() - this.getX(), this.target.getZ() - this.getZ());
        if (d <= GLIDE_DIVE_AT)
        {
            this.start(Move.DIVE);
            return;
        }
        if (this.moveTick > GLIDE_TICKS)
        {
            this.endMove();
            return;
        }
        Vec3 to = this.leashed(this.target.position());
        double low = Math.max(this.groundAt(to.x, to.z), this.groundAt(this.getX(), this.getZ())) + GLIDE_HEIGHT;
        this.steer(new Vec3(to.x, low, to.z), this.kind.fast(), ACCELERATION * 2.0D);
        this.loop("glide");
        if (this.moveTick % GLIDE_FIRE_EVERY == 0)
        {
            this.dropFire(STRAFE_TRAIL_MIN, STRAFE_TRAIL_MAX);
        }
    }

    /** The young's fire trail (the user, October 30: "much more aggressive ... not just 1 fire every couple blocks,
     *  always fire when he glides"): every block of ground it passes over gets a band of fire across its path,
     *  {@code YOUNG_TRAIL_WIDTH} blocks wide, laid along the line from where it last laid fire; hovering
     *  in place, a patch under it every {@code YOUNG_FIRE_EVERY} ticks; fire falling off it all the while. Only where
     *  the ground is near enough under it. */
    private void fireTrail()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 here = new Vec3(this.getX(), 0.0D, this.getZ());
        if (this.trailFrom == null || this.trailFrom.distanceTo(here) > YOUNG_TRAIL_JUMP)
        {
            this.trailFrom = here;
            this.fireBand(level, here, new Vec3(Mth.cos(this.getYRot() * Mth.DEG_TO_RAD), 0.0D,
                    Mth.sin(this.getYRot() * Mth.DEG_TO_RAD)));
        }
        double gone = this.trailFrom.distanceTo(here);
        if (gone >= YOUNG_TRAIL_STEP)
        {
            Vec3 along = here.subtract(this.trailFrom).normalize();
            Vec3 across = new Vec3(-along.z, 0.0D, along.x);
            int steps = (int) (gone / YOUNG_TRAIL_STEP);
            for (int i = 1; i <= steps; i++)
            {
                this.fireBand(level, this.trailFrom.add(along.scale(i * YOUNG_TRAIL_STEP)), across);
            }
            this.trailFrom = this.trailFrom.add(along.scale(steps * YOUNG_TRAIL_STEP));
        }
        else if (this.tickCount % YOUNG_FIRE_EVERY == 0)
        {
            this.dropFire(YOUNG_FIRE_MIN, YOUNG_FIRE_MAX);
        }
        double ground = this.groundAt(this.getX(), this.getZ());
        if (this.getY() - ground <= YOUNG_FIRE_REACH)
        {
            level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 0.5D, this.getZ(), 4, 0.8D,
                    (this.getY() - ground) * 0.3D, 0.8D, 0.02D);
            if (this.tickCount % 2 == 0)
            {
                level.sendParticles(ParticleTypes.FALLING_LAVA, this.getX(), this.getY() + 0.5D, this.getZ(), 2, 0.8D,
                        0.3D, 0.8D, 0.0D);
            }
        }
    }

    /** One row of the young's trail: fire on the ground {@code YOUNG_TRAIL_WIDTH} blocks wide across {@code at} (either
     *  side of it along {@code across}, where the ground is near enough under it. */
    private void fireBand(ServerLevel level, Vec3 at, Vec3 across)
    {
        for (int k = 0; k < YOUNG_TRAIL_WIDTH; k++)
        {
            double off = k - (YOUNG_TRAIL_WIDTH - 1) * 0.5D;
            double x = at.x + across.x * off;
            double z = at.z + across.z * off;
            double ground = this.groundAt(x, z);
            if (this.getY() - ground <= YOUNG_FIRE_REACH)
            {
                LavaPools.placeFire(level, BlockPos.containing(x, ground, z), true, YOUNG_FIRE_MIN, YOUNG_FIRE_MAX);
            }
        }
    }

    /** A patch of fire dropping off it onto the ground under it (a little to one side or the other), with falling
     *  embers; only where the ground is near enough under it. */
    private void dropFire(int min, int max)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        double x = this.getX() + (this.random.nextDouble() - 0.5D) * this.kind.width();
        double z = this.getZ() + (this.random.nextDouble() - 0.5D) * this.kind.width();
        double ground = this.groundAt(x, z);
        if (this.getY() - ground > YOUNG_FIRE_REACH)
        {
            return;
        }
        LavaPools.placeFire(level, BlockPos.containing(x, ground, z), true, min, max);
        level.sendParticles(ParticleTypes.FALLING_LAVA, x, this.getY() + 1.0D, z, 3, 0.6D, 0.4D, 0.6D, 0.0D);
    }

    /** Its fire breath under it: fire on the ground, and anyone in it burnt. */
    private void breathe()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        double ground = this.groundAt(this.getX(), this.getZ());
        Vec3 under = new Vec3(this.getX(), ground, this.getZ());
        if (this.tickCount % 2 == 0)
        {
            for (int k = -1; k <= 1; k++)
            {
                Vec3 side = new Vec3(Mth.cos(this.getYRot() * Mth.DEG_TO_RAD), 0.0D, Mth.sin(this.getYRot() * Mth.DEG_TO_RAD)).scale(k * 1.5D);
                LavaPools.placeFire(level, BlockPos.containing(under.add(side)), true, STRAFE_TRAIL_MIN, STRAFE_TRAIL_MAX);
            }
        }
        level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + 2.0D, this.getZ(), 12, 0.8D, (this.getY() - ground) * 0.4D, 0.8D, 0.06D);
        if (this.tickCount % 5 == 0)
        {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.6F);
        }
        AABB beam = new AABB(this.getX() - STRAFE_WIDTH, ground - 1.0D, this.getZ() - STRAFE_WIDTH,
                this.getX() + STRAFE_WIDTH, this.getY() + 2.0D, this.getZ() + STRAFE_WIDTH);
        for (LivingEntity e : this.foesWithin(beam))
        {
            if (this.hitThisMove.add(e.getUUID()) && e.hurt(this.damageSources().mobAttack(this), STRAFE_DAMAGE))
            {
                e.setSecondsOnFire(STRAFE_FIRE_SECONDS);
            }
        }
    }

    /** Talon grab: it closes in until its swoop can reach its target, then swoops and catches it; caught, it hangs from
     *  its talons (its passenger), is carried up and away and dropped near where it was caught. It never swoops where it
     *  can't catch (the user, October 30: its talons closing on nothing over their head, "remove this weird grab attack
     *  that doesn't grab me into the sky": it swooped from wherever it was, at a fixed speed, and missed when it was too
     *  far or too high): the swoop starts only within reach ({@link EmberTuning#GRAB_SWOOP_SHARE} of what it flies by
     *  the moment its talons close) and with room for it where it catches, and flies exactly onto its target over the
     *  ticks it has left; one that can't get in reach soon enough ({@link EmberTuning#GRAB_APPROACH_TICKS}) doesn't
     *  swoop at all. */
    private void grab()
    {
        int moment = this.at("grab", 0.395D);
        if (this.carried != null)
        {
            // carrying: first straight up until whoever it holds is clear over everything on its way, then across at
            // that height, then down to the drop's height over the ground there, a moment's hold, and it lets go (never
            // through the ground: they hang in open air all the way)
            double hang = this.carried.getBbHeight() + 0.5D;
            double cruiseY = this.dropSpot.y + hang;
            double across = Math.hypot(this.dropSpot.x - this.getX(), this.dropSpot.z - this.getZ());
            Vec3 to;
            boolean there = false;
            if (!this.hitThisMove.contains(Marks.CLIMBED))
            {
                to = new Vec3(this.getX(), cruiseY, this.getZ());
                // (or as high as it goes: a roof over it)
                if (this.getY() >= cruiseY - 1.0D || this.verticalCollision && !this.verticalCollisionBelow)
                {
                    this.hitThisMove.add(Marks.CLIMBED);
                }
            }
            else if (across >= 2.0D)
            {
                to = new Vec3(this.dropSpot.x, cruiseY, this.dropSpot.z);
            }
            else
            {
                to = new Vec3(this.dropSpot.x, this.dropLevel + hang, this.dropSpot.z);
                there = this.getY() <= this.dropLevel + hang + 0.75D || this.verticalCollision;
            }
            this.steer(to, this.kind.fast() * 0.5D, 0.25D);
            this.loop("fly");
            if (there && this.hitThisMove.add(Marks.AT_DROP))
            {
                this.dropTick = this.moveTick;
            }
            if (this.hitThisMove.contains(Marks.AT_DROP) && this.moveTick >= this.dropTick + GRAB_HOLD_TICKS
                    || this.moveTick > this.swoopFrom + moment + GRAB_CARRY_TICKS)
            {
                this.release();
                this.endMove();
            }
            return;
        }
        boolean carriable = carriable(this.target);
        if (!this.hitThisMove.contains(Marks.SWOOP))
        {
            // closing in, flying (no swoop yet), until its swoop reaches
            Vec3 spot = carriable ? this.grabSpot(this.target) : this.target.position().add(0.0D, 1.0D, 0.0D);
            Vec3 aim = spot != null ? spot : this.grabAim(this.target);
            double reach = this.kind.fast() * this.speedScale() * moment * GRAB_SWOOP_SHARE;
            if (spot != null && this.position().distanceTo(aim) <= reach)
            {
                this.swoopSpot = spot;
                this.hitThisMove.add(Marks.SWOOP);
                this.swoopFrom = this.moveTick;
                this.playAnimation("grab");
            }
            else if (this.moveTick > GRAB_APPROACH_TICKS)
            {
                this.endMove();              // (it couldn't get close enough, or there's no room for it: no swoop)
                return;
            }
            else
            {
                this.steer(this.overGround(aim, MIN_CLEARANCE), this.kind.fast(), 0.35D);
                this.loop("fly");
                return;
            }
        }
        int t = this.moveTick - this.swoopFrom;
        if (!carriable)
        {
            // too big to carry (another Emberdactyl): its talons rake it as it swoops past
            if (t <= moment)
            {
                this.steer(this.target.position().add(0.0D, 1.0D, 0.0D), this.kind.fast(), 0.35D);
            }
            if (t == moment)
            {
                double reach = (this.getBbWidth() + this.target.getBbWidth()) * 0.5D + DUEL_STRIKE_REACH;
                if (this.target.distanceToSqr(this) <= reach * reach && this.canFight(this.target)
                        && this.target.hurt(this.damageSources().mobAttack(this), GRAB_DAMAGE))
                {
                    Vec3 away = this.target.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
                    this.throwOut(this.target, (away.lengthSqr() < 0.01D ? this.facing() : away.normalize())
                            .scale(DUEL_STRIKE_THROW), 0.3D);
                }
            }
            if (t > moment)
            {
                this.steer(this.position().add(0.0D, 0.8D, 0.0D), this.kind.cruise(), 0.2D);
                if (t > moment + 10)
                {
                    this.endMove();
                }
            }
            return;
        }
        // the swoop: exactly onto its target over the ticks it has left (it catches it as its talons close, or, held
        // up a moment, the moment it's there)
        Vec3 now = this.grabSpot(this.target);
        if (now != null)
        {
            this.swoopSpot = now;
        }
        Vec3 aim = this.swoopSpot;
        Vec3 to = aim.subtract(this.position());
        double most = this.kind.fast() * this.speedScale() * GRAB_SWOOP_FASTEST;
        Vec3 v = to.scale(1.0D / Math.max(1, moment - t + 1));
        this.setDeltaMovement(v.length() > most ? v.normalize().scale(most) : v);
        if (to.x * to.x + to.z * to.z > 0.25D)
        {
            this.turnTo(to);
        }
        else
        {
            this.turnTo(this.target.position().subtract(this.position()));
        }
        if (t >= moment && this.position().distanceTo(this.grabAim(this.target)) <= GRAB_REACH && this.canFight(this.target))
        {
            this.target.hurt(this.damageSources().mobAttack(this), GRAB_DAMAGE);
            if (this.target.isAlive())
            {
                this.dropSpot = this.dropFor(this.target);
                if (this.target.startRiding(this, true))
                {
                    this.carried = this.target;
                    return;
                }
            }
            this.endMove();
        }
        else if (t > moment + GRAB_CATCH_GRACE)
        {
            this.endMove();                  // (it got away: a wall in the way, an ender pearl...)
        }
    }

    /** Where its feet go to catch something: just over its head (it hangs there from its talons). */
    private Vec3 grabAim(LivingEntity e)
    {
        return e.position().add(0.0D, e.getBbHeight() + 0.5D, 0.0D);
    }

    /** The nearest spot round {@link #grabAim} where its body fits (blocks stop it now: beside a wall it catches from a
     *  little off to the side or higher, within {@link EmberTuning#GRAB_REACH}), or null if there's none. */
    @Nullable
    private Vec3 grabSpot(LivingEntity e)
    {
        Vec3 aim = this.grabAim(e);
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        int r = GRAB_SPOT_SEARCH;
        for (int dy = 0; dy <= r; dy++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d >= bestD || d > Mth.square(GRAB_REACH - 0.5D))
                    {
                        continue;
                    }
                    Vec3 spot = aim.add(dx, dy, dz);
                    if (this.fitsAt(spot))
                    {
                        best = spot;
                        bestD = d;
                    }
                }
            }
        }
        return best;
    }

    /** Whether its body and its wings (solid since October 31) fit at {@code spot}, facing as it does. */
    private boolean fitsAt(Vec3 spot)
    {
        if (!this.level().noCollision(this, this.getBoundingBox().move(spot.subtract(this.position())).deflate(0.01D)))
        {
            return false;
        }
        for (int i : WINGS)
        {
            if (i < this.parts.length && !this.level().noCollision(this, this.partBoxAt(i, spot, this.yBodyRot).deflate(0.01D)))
            {
                return false;
            }
        }
        return true;
    }

    /** True if its talons can carry it off: a player, or anything no bigger than an iron golem (not another Emberdactyl). */
    private static boolean carriable(LivingEntity e)
    {
        return e instanceof Player || !(e instanceof EmberBoss) && e.getBbWidth() <= 2.0D && e.getBbHeight() <= 3.5D;
    }

    /** Whoever it carries rides it (positioned under its talons by {@link #positionRider}); one that struggled free
     *  (sneaked off), died or left is let go. Anything else riding it (a mob saved riding it) gets off. */
    private void carry()
    {
        for (Entity passenger : this.getPassengers())
        {
            if (passenger != this.carried)
            {
                passenger.stopRiding();
            }
        }
        if (this.carried == null)
        {
            return;
        }
        LivingEntity e = this.carried;
        if (!e.isAlive() || e.level() != this.level() || this.move != Move.GRAB || e.getVehicle() != this)
        {
            this.release();
            return;
        }
        e.fallDistance = 0.0F;
    }

    /** Whoever it carries hangs under its talons: worked out the same way on the server and on each screen, so they move
     *  with it smoothly, and they look round as they like. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction move)
    {
        if (this.hasPassenger(passenger))
        {
            Vec3 at = this.hangSpot(passenger);
            move.accept(passenger, at.x, at.y, at.z);
        }
    }

    /** They hang from its talons (not sitting, as on a horse). */
    @Override
    public boolean shouldRiderSit()
    {
        return false;
    }

    /** Let go, they fall from where they hang (not from on top of it). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger)
    {
        return passenger.position();
    }

    /** Nothing gets on it by itself, and nothing it carries steers it or shows its health in place of their own. */
    @Override
    protected boolean canAddPassenger(Entity passenger)
    {
        return false;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger()
    {
        return null;
    }

    @Override
    public boolean showVehicleHealth()
    {
        return false;
    }

    @Override
    protected void removePassenger(Entity passenger)
    {
        super.removePassenger(passenger);
        if (!this.level().isClientSide && passenger instanceof LivingEntity e)
        {
            markDropped(e, this.level().getGameTime());
        }
    }

    /** Marks a fall as the grab's: when it lands, see {@link #capGrabFall}. */
    static void markDropped(LivingEntity e, long gameTime)
    {
        e.getPersistentData().putLong(TAG_DROPPED, gameTime);
    }

    /**
     * The fall of whoever it let go (or who struggled free) never takes more than {@link EmberTuning#GRAB_FALL_SHARE} of
     * the health they have when they land (the user, October 29: "make it so he can't drop me from any more than where
     * it does at max half of my health"): the fall's distance is cut to match. Returns the distance to use.
     */
    static float capGrabFall(LivingEntity e, float distance, float multiplier)
    {
        CompoundTag data = e.getPersistentData();
        if (!data.contains(TAG_DROPPED))
        {
            return distance;
        }
        long since = e.level().getGameTime() - data.getLong(TAG_DROPPED);
        data.remove(TAG_DROPPED);
        if (since < 0L || since > GRAB_FALL_GUARD_TICKS)
        {
            return distance;
        }
        float most = (float) Math.floor(e.getHealth() * GRAB_FALL_SHARE);
        return Math.min(distance, 3.0F + most / Math.max(0.01F, multiplier));
    }

    /** Where whoever it carries hangs this tick: under its talons, but only ever in open air (never moved into blocks:
     *  higher up where its talons are lower than they can go, or left where they are). */
    private Vec3 hangSpot(Entity e)
    {
        Vec3 want = new Vec3(this.getX(), this.getY() - e.getBbHeight() - 0.5D, this.getZ());
        if (this.isFree(e, want))
        {
            return want;
        }
        Vec3 at = e.position();
        double base = Math.max(want.y, at.y);
        for (int up = 0; up <= 4; up++)
        {
            Vec3 spot = new Vec3(want.x, base + up, want.z);
            if (this.isFree(e, spot))
            {
                return spot;
            }
        }
        Vec3 above = new Vec3(at.x, base, at.z);
        return this.isFree(e, above) ? above : at;
    }

    private boolean isFree(Entity e, Vec3 feet)
    {
        return this.level().noCollision(e, e.getBoundingBox().move(feet.subtract(e.position())).deflate(0.01D));
    }

    /** Where it drops whoever it caught: near where it caught them, up to {@link EmberTuning#GRAB_DRIFT} blocks off
     *  (the user, October 30: "why does he bring me up to the volcano, just drop me when we are fighting on the
     *  ground"; until then one with a nest always carried them off over its crater); the height their feet are carried at (returned) never lower than {@link EmberTuning#GRAB_CLEARANCE}
     *  over the highest ground on the way (its crater's rim...) or than they are now; and the height it lets go at
     *  ({@link #dropLevel}): 3 blocks plus {@link EmberTuning#GRAB_FALL_SHARE} of their health over the ground there (a
     *  fall they live through with that share of it), {@link EmberTuning#GRAB_DROP_MIN} to
     *  {@link EmberTuning#GRAB_CARRY_HEIGHT}. */
    private Vec3 dropFor(LivingEntity e)
    {
        float a = this.random.nextFloat() * Mth.TWO_PI;
        double r = this.random.nextDouble() * GRAB_DRIFT;
        Vec3 away = this.leashed(e.position().add(Mth.cos(a) * r, 0.0D, Mth.sin(a) * r));
        double highest = e.getY();
        Vec3 from = e.position();
        double len = Math.hypot(away.x - from.x, away.z - from.z);
        int steps = Math.max(1, (int) Math.ceil(len / 2.0D));
        for (int i = 0; i <= steps; i++)
        {
            Vec3 p = from.lerp(away, (double) i / steps);
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    highest = Math.max(highest, this.groundAt(p.x + dx * 1.5D, p.z + dz * 1.5D));
                }
            }
        }
        double fall = Mth.clamp(3.0D + Math.floor(e.getHealth() * GRAB_FALL_SHARE), GRAB_DROP_MIN, GRAB_CARRY_HEIGHT)
                + (this.dashes() ? YOUNG_GRAB_DROP_EXTRA : GRAB_DROP_EXTRA);
        this.dropLevel = this.groundAt(away.x, away.z) + fall;
        double feet = Math.max(this.dropLevel, highest + GRAB_CLEARANCE);
        return new Vec3(away.x, feet, away.z);
    }

    /** Lets go of whoever it carries: they fall from where they hang (their fall capped: {@link #capGrabFall}). */
    private void release()
    {
        if (this.carried != null)
        {
            LivingEntity e = this.carried;
            this.carried = null;
            if (e.getVehicle() == this)
            {
                e.stopRiding();
            }
            e.setDeltaMovement(this.getDeltaMovement().scale(0.5D));
            e.hurtMarked = true;
        }
    }

    /** Beak snap (on the ground): {@link EmberTuning#SNAP_PECKS} pecks one after another, its bill lunging at whatever's
     *  in front of it, turning to its target between them. */
    private void snap()
    {
        this.setDeltaMovement(Vec3.ZERO);
        int peckLength = this.length("snap");
        int peck = (this.moveTick - 1) / peckLength;
        int peckTick = (this.moveTick - 1) % peckLength + 1;
        if (peckTick == 1 && peck > 0)
        {
            this.playAnimation("snap");
            this.hitThisMove.clear();
        }
        int hit = this.at("snap", SNAP_HIT_SHARE);
        if (peckTick < hit - 2)
        {
            this.turnTo(this.target.position().subtract(this.position()));
        }
        if (peckTick == hit)
        {
            double scale = this.kind.width() / ADULT.width();
            double reach = this.dashes() ? YOUNG_SNAP_STRIKE : SNAP_STRIKE;
            double splash = SNAP_SPLASH * (this.dashes() ? Math.max(0.75D, scale) : 1.0D);
            Vec3 face = this.facing();
            Vec3 strike = this.position().add(face.scale(reach));
            strike = new Vec3(strike.x, this.groundAt(strike.x, strike.z), strike.z);
            if (this.level() instanceof ServerLevel level)
            {
                // its bill striking the ground: a burst of rock and embers round the spot
                level.sendParticles(ParticleTypes.EXPLOSION, strike.x, strike.y + 0.5D, strike.z, 3, splash * 0.3D, 0.1D, splash * 0.3D, 0.0D);
                level.sendParticles(ParticleTypes.LAVA, strike.x, strike.y + 0.3D, strike.z, 10, splash * 0.4D, 0.1D, splash * 0.4D, 0.0D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, strike.x, strike.y + 0.5D, strike.z, 14, splash * 0.5D, 0.2D, splash * 0.5D, 0.02D);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BASALT.defaultBlockState()),
                        strike.x, strike.y + 0.3D, strike.z, 30, splash * 0.4D, 0.2D, splash * 0.4D, 0.15D);
            }
            AABB around = this.getBoundingBox().minmax(new AABB(strike, strike)).inflate(splash + 1.0D, SNAP_SPLASH_HEIGHT, splash + 1.0D);
            for (LivingEntity e : this.foesWithin(around))
            {
                Vec3 rel = e.position().subtract(this.position());
                double along = rel.x * face.x + rel.z * face.z;
                double side = Math.abs(rel.x * face.z - rel.z * face.x);
                // round where its bill strikes the ground (its hitbox, not only its feet: a player standing just past
                // the tip is hit), or in front of it from under its chest out to the strike
                AABB body = e.getBoundingBox();
                double dx = Math.max(0.0D, Math.max(body.minX - strike.x, strike.x - body.maxX));
                double dz = Math.max(0.0D, Math.max(body.minZ - strike.z, strike.z - body.maxZ));
                boolean inSplash = Math.hypot(dx, dz) <= splash
                        && body.maxY >= strike.y - SNAP_SPLASH_HEIGHT && body.minY <= strike.y + SNAP_SPLASH_HEIGHT;
                boolean inLine = along >= SNAP_FROM * scale && along <= reach && side <= SNAP_SIDE * scale + e.getBbWidth() * 0.5D
                        && body.minY <= this.getY() + this.getBbHeight();
                if (inLine || inSplash)
                {
                    // (each peck counts, even straight after the last: a player just hurt is no safer)
                    e.invulnerableTime = 0;
                    if (e.hurt(this.damageSources().mobAttack(this), SNAP_DAMAGE))
                    {
                        this.throwOut(e, face.scale(0.45D), 0.2D);
                    }
                }
            }
        }
        if (this.moveTick >= peckLength * SNAP_PECKS)
        {
            this.afterGroundMove();
        }
    }

    /** Wing buffet: one huge beat, everyone in front of it thrown about 25 blocks. In the air it swoops down near its
     *  target first, so the beat reaches it. */
    private void buffet()
    {
        int beat = this.at("gust", 0.5625D);
        if (!this.grounded && this.moveTick < beat && this.target != null)
        {
            Vec3 back = new Vec3(this.getX() - this.target.getX(), 0.0D, this.getZ() - this.target.getZ());
            back = back.lengthSqr() < 0.01D ? this.facing().scale(-1.0D) : back.normalize();
            Vec3 spot = this.target.position().add(back.scale(BUFFET_APPROACH)).add(0.0D, BUFFET_APPROACH_HEIGHT, 0.0D);
            this.steer(this.overGround(spot, 2.0D), this.kind.fast(), 0.3D);
            this.turnTo(this.target.position().subtract(this.position()));
        }
        else
        {
            this.hold();
        }
        if (this.moveTick == beat)
        {
            Vec3 face = this.facing();
            for (LivingEntity e : this.foesWithin(this.getBoundingBox().inflate(BUFFET_RANGE, BUFFET_REACH_DOWN, BUFFET_RANGE)))
            {
                Vec3 rel = new Vec3(e.getX() - this.getX(), 0.0D, e.getZ() - this.getZ());
                double d = rel.length();
                if (d > BUFFET_RANGE)
                {
                    continue;
                }
                double angle = d < 0.01D ? 0.0D : Math.toDegrees(Math.acos(Mth.clamp((rel.x * face.x + rel.z * face.z) / d, -1.0D, 1.0D)));
                if (angle > BUFFET_CONE && d > this.kind.width())
                {
                    continue;
                }
                e.hurt(this.damageSources().mobAttack(this), BUFFET_DAMAGE);
                this.throwOut(e, (d < 0.01D ? face : rel.scale(1.0D / d)).scale(BUFFET_THROW), THROW_20_LIFT);
            }
            if (this.level() instanceof ServerLevel level)
            {
                Vec3 c = this.position().add(face.scale(6.0D));
                level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1.0D, c.z, 40, 4.0D, 1.0D, 4.0D, 0.3D);
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1.0D, c.z, 30, 4.0D, 1.0D, 4.0D, 0.2D);
            }
        }
        if (this.moveTick >= this.length("gust"))
        {
            this.afterGroundMove();
        }
    }

    /** Screech: everyone near thrown about 20 blocks, a little hurt and slowed. */
    private void screech()
    {
        this.hold();
        if (this.moveTick == this.at("screech", 0.214D))
        {
            for (LivingEntity e : this.foesWithin(this.getBoundingBox().inflate(SCREECH_RANGE, SCREECH_RANGE, SCREECH_RANGE)))
            {
                Vec3 rel = new Vec3(e.getX() - this.getX(), 0.0D, e.getZ() - this.getZ());
                if (rel.length() > SCREECH_RANGE)
                {
                    continue;
                }
                e.hurt(this.damageSources().mobAttack(this), SCREECH_DAMAGE);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SCREECH_SLOWNESS_TICKS, 1));
                this.throwOut(e, (rel.lengthSqr() < 0.01D ? new Vec3(1.0D, 0.0D, 0.0D) : rel.normalize()).scale(THROW_20), THROW_20_LIFT);
            }
            if (this.level() instanceof ServerLevel level)
            {
                level.sendParticles(ParticleTypes.SONIC_BOOM, this.getX(), this.getY() + this.getBbHeight() * 0.8D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (this.moveTick >= this.length("screech"))
        {
            this.afterGroundMove();
        }
    }

    /** After a move it may make on the ground: back to lying stuck (its next choice), or on in the air. */
    private void afterGroundMove()
    {
        if (this.grounded)
        {
            this.start(Move.STUCK);
            this.moveTick = this.kind.stuck();
        }
        else
        {
            this.endMove();
        }
    }

    /** The young's dash: straight through its target and on past, at its swooping speed. */
    private void dash()
    {
        Vec3 to = this.overGround(this.locked, 1.0D);
        this.steer(to, this.kind.fast(), 0.6D);
        AABB reach = this.getBoundingBox().inflate(DASH_WIDTH, 1.0D, DASH_WIDTH);
        for (LivingEntity e : this.foesWithin(reach))
        {
            if (this.hitThisMove.add(e.getUUID()) && e.hurt(this.damageSources().mobAttack(this), DASH_DAMAGE))
            {
                this.throwOut(e, this.facing().scale(0.9D), 0.4D);
            }
        }
        if (this.position().distanceToSqr(to) < 4.0D || this.moveTick > 60)
        {
            this.endMove();
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The way it faces, along the ground. */
    protected Vec3 facing()
    {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /** Living things in the box it may hurt: players it could fight, anything that's hurt it or it's going for (not its
     *  kin or its egg), and any raged mob of another team (another Emberdactyl too); never its own team. */
    protected List<LivingEntity> foesWithin(AABB box)
    {
        return this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e != this && e.isAlive()
                && !Rage.areTeammates(this, e)
                && (Rage.areEnemies(this, e) || !(e instanceof EmberBoss) && !(e instanceof FireCandidate)
                && (e instanceof Player p ? !p.isCreative() && !p.isSpectator() : e == this.getLastHurtByMob() || e == this.target)));
    }

    /** Throws something: its push along the ground and up (a player's client is told). */
    protected void throwOut(LivingEntity e, Vec3 along, double up)
    {
        double resist = 1.0D - Mth.clamp(e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
        e.setDeltaMovement(along.x * resist, Math.max(e.getDeltaMovement().y, up), along.z * resist);
        e.hurtMarked = true;
        e.hasImpulse = true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.phase == Phase.DYING)
        {
            return false;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide)
        {
            if (this.phase == Phase.REST)
            {
                LivingEntity by = source.getEntity() instanceof LivingEntity e ? e : null;
                this.startFighting(by);
            }
            this.checkEnrage();
        }
        return hurt;
    }

    /** The adult turns faster at half health (the young never does). */
    protected void checkEnrage()
    {
    }

    // ---------------------------------------------------------------- its boss bar

    private void updateBar()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        if (this.bar == null)
        {
            this.bar = new ServerBossEvent(this.barName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
            LIVE_BARS.put(this.bar, this);
        }
        this.barTick = level.getGameTime();
        this.bar.setProgress(this.getHealth() / this.getMaxHealth());
        if (this.tickCount % 10 != 0)
        {
            return;
        }
        boolean showing = this.phase == Phase.WAKE || this.phase == Phase.FIGHT;
        for (ServerPlayer p : List.copyOf(this.bar.getPlayers()))
        {
            if (!showing || p.level() != level || p.distanceToSqr(this) > BAR_RANGE * BAR_RANGE || !p.isAlive())
            {
                this.bar.removePlayer(p);
            }
        }
        if (showing)
        {
            for (ServerPlayer p : level.players())
            {
                if (p.distanceToSqr(this) <= BAR_RANGE * BAR_RANGE && !p.isSpectator())
                {
                    this.bar.addPlayer(p);
                }
            }
        }
    }

    private void dropBar()
    {
        if (this.bar != null)
        {
            this.bar.removeAllPlayers();
        }
    }

    /** Its bar leaves a player's screen when it goes out of their sight too. */
    @Override
    public void stopSeenByPlayer(ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
        if (this.bar != null)
        {
            this.bar.removePlayer(player);
        }
    }

    /** Every boss bar of an Emberdactyl or its young still in the world, and whose it is. */
    private static final Map<ServerBossEvent, EmberBoss> LIVE_BARS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * (the server, every second) Takes each bar off every screen it shouldn't be on: the player's gone further than
     * {@link EmberTuning#BAR_RANGE}, to another dimension or logged out, or its boss is gone or isn't being looked after
     * (its land not ticking: the user saw "Emberdactyl: The Sky That Burns" stuck on their screen far from any; its bar
     * was only looked after while it ticked).
     */
    public static void sweepBars()
    {
        for (Map.Entry<ServerBossEvent, EmberBoss> entry : LIVE_BARS.entrySet())
        {
            ServerBossEvent bar = entry.getKey();
            EmberBoss boss = entry.getValue();
            if (boss.isRemoved())
            {
                bar.removeAllPlayers();
                LIVE_BARS.remove(bar);
                continue;
            }
            boolean ticking = boss.level().getGameTime() - boss.barTick <= 40L;
            for (ServerPlayer p : List.copyOf(bar.getPlayers()))
            {
                if (!ticking || p.hasDisconnected() || p.level() != boss.level() || p.distanceToSqr(boss) > BAR_RANGE * BAR_RANGE)
                {
                    bar.removePlayer(p);
                }
            }
        }
    }

    @Override
    public void remove(RemovalReason reason)
    {
        this.dropBar();
        if (this.bar != null)
        {
            LIVE_BARS.remove(this.bar);
        }
        this.release();
        super.remove(reason);
    }

    // ---------------------------------------------------------------- its death

    @Override
    public void die(DamageSource source)
    {
        if (this.phase == Phase.DYING)
        {
            return;
        }
        super.die(source);
        if (this.level().isClientSide)
        {
            return;
        }
        this.phase = Phase.DYING;
        this.release();
        this.dropBar();
        this.planFall();
        this.playAnimation(this.fallDelay > 0 ? "glide" : "death");
        this.onDeathStart(source);
    }

    /**
     * Where and how it comes down to die (the user's first fight: dying in the air it "seems to be falling from the
     * floor ... glitches", lying over the steep volcano half of it sank into the rock). Its death animation screams with
     * its body at its flying height, then drops it onto the ground ({@link Kind#deathFall}); so it glides down to the
     * flattest ground near ({@link #restingSpot}) and gets there just as its body falls. Too far to get there in its
     * scream, it glides down first and starts dying on the way. Dying on the ground it sinks by the difference
     * between its standing pose and its death's first pose ({@link Kind#standLift}), so its body doesn't jump up, and
     * rises back over its scream: it rears up as it screams, then falls.
     */
    private void planFall()
    {
        boolean standing = this.grounded || this.standing();
        Vec3 from = this.position();
        if (standing)
        {
            from = from.subtract(0.0D, this.kind.standLift(), 0.0D);
            this.setPos(from.x, from.y, from.z);
        }
        Vec3 to = this.restingSpot(this.position(), standing ? Math.min(8.0D, this.kind.lieSearch()) : this.kind.lieSearch());
        double needed = to.distanceTo(from) / DEATH_GLIDE;
        this.fallFrom = from;
        this.fallTo = to;
        this.fallDelay = (int) Math.max(0.0D, Math.ceil(needed - this.kind.deathFall()));
    }

    /** The flattest ground within {@code search} blocks of {@code from} for it to lie on (its feet's spot: the highest
     *  ground under it, so nothing of it sinks), a little nearer counting for a little more; never higher than
     *  {@link EmberTuning#DEATH_MAX_RISE} over the top of the ground under its body now (it comes down to die, never
     *  up), and for the
     *  adult, whose pieces fly out as it breaks apart, the ground they land on counting too (how far under the body it
     *  drops away, out to {@link EmberTuning#DEATH_BREAK_REACH}). */
    private Vec3 restingSpot(Vec3 from, double search)
    {
        double r = this.kind.lieRadius();
        double ceiling = -Double.MAX_VALUE;     // (the top of the ground under its body where it died: always allowed)
        for (int k = 0; k < 19; k++)
        {
            double rr = k == 0 ? 0.0D : k <= 6 ? r * 0.5D : r;
            double a = Math.PI * 2.0D * k / (k <= 6 ? 6.0D : 12.0D);
            ceiling = Math.max(ceiling, this.groundAt(from.x + Math.cos(a) * rr, from.z + Math.sin(a) * rr));
        }
        ceiling += DEATH_MAX_RISE;
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        int reach = (int) Math.ceil(search / 2.0D);
        for (int i = -reach; i <= reach; i++)
        {
            for (int j = -reach; j <= reach; j++)
            {
                double x = from.x + i * 2.0D;
                double z = from.z + j * 2.0D;
                double d = Math.hypot(x - from.x, z - from.z);
                if (d > search || !this.level().hasChunksAt(BlockPos.containing(x - r, 0, z - r), BlockPos.containing(x + r, 0, z + r)))
                {
                    continue;
                }
                double lo = Double.MAX_VALUE;
                double hi = -Double.MAX_VALUE;
                for (int k = 0; k < 19; k++)
                {
                    double rr = k == 0 ? 0.0D : k <= 6 ? r * 0.5D : r;
                    double a = Math.PI * 2.0D * k / (k <= 6 ? 6.0D : 12.0D);
                    double h = this.groundAt(x + Math.cos(a) * rr, z + Math.sin(a) * rr);
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
                if (hi > ceiling)
                {
                    continue;
                }
                double drop = 0.0D;
                if (!this.dashes())
                {
                    for (int k = 0; k < 16; k++)
                    {
                        double a = Math.PI * 2.0D * k / 16.0D;
                        drop = Math.max(drop, hi - this.groundAt(x + Math.cos(a) * DEATH_BREAK_REACH,
                                z + Math.sin(a) * DEATH_BREAK_REACH));
                    }
                }
                BlockPos top = BlockPos.containing(x, this.groundAt(x, z) - 1.0D, z);
                double score = hi - lo + d * 0.05D + drop * DEATH_BREAK_DROP_COST
                        + (this.level().getFluidState(top).isEmpty() ? 0.0D : DEATH_FLUID_COST);
                if (score < bestScore)
                {
                    bestScore = score;
                    best = new Vec3(x, hi, z);
                }
            }
        }
        return best != null ? best : new Vec3(from.x, this.groundAt(from.x, from.z), from.z);
    }

    /** It comes down to where it'll lie (on the server; screens follow), then lies there its whole death animation (it
     *  breaks apart, or hardens into obsidian). */
    @Override
    protected void tickDeath()
    {
        this.deathTime++;
        if (this.level().isClientSide)
        {
            return;
        }
        if (this.fallFrom != null && this.fallTo != null)
        {
            int total = this.fallDelay + this.kind.deathFall();
            double share = Mth.clamp((double) this.deathTime / Math.max(1, total), 0.0D, 1.0D);
            double eased = share * share * (3.0D - 2.0D * share);
            Vec3 at = this.fallFrom.lerp(this.fallTo, eased);
            this.setDeltaMovement(Vec3.ZERO);
            this.setPos(at.x, at.y, at.z);
            Vec3 way = this.fallTo.subtract(this.fallFrom);
            if (share < 1.0D && way.x * way.x + way.z * way.z > 4.0D)
            {
                this.turnTo(way);
            }
        }
        if (this.fallDelay > 0 && this.deathTime == this.fallDelay)
        {
            this.playAnimation("death");
        }
        int dying = this.deathTime - this.fallDelay;
        if (dying > 0 && this.deathTick(dying))
        {
            if (this.level() instanceof ServerLevel level)
            {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 2.0D, this.getZ(), 60, 3.0D, 2.0D, 3.0D, 0.05D);
            }
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public int getExperienceReward()
    {
        return this.xpReward;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (this.home != null)
        {
            tag.put(TAG_HOME, vec(this.home));
        }
        if (this.nest != null)
        {
            tag.put(TAG_NEST, vec(this.nest));
        }
        if (this.perch != null)
        {
            tag.put(TAG_PERCH, vec(this.perch));
            tag.putFloat(TAG_PERCH_YAW, this.perchYaw);
        }
        tag.putLong(TAG_VOLCANO, this.volcano);
        tag.putBoolean(TAG_ENRAGED, this.enraged);
        tag.putBoolean(TAG_HOSTILE, this.hostile);
        tag.putString(TAG_PHASE, this.phase == Phase.FIGHT || this.phase == Phase.WAKE ? "fight" : "rest");
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.home = tag.contains(TAG_HOME) ? vec(tag.getCompound(TAG_HOME)) : null;
        this.nest = tag.contains(TAG_NEST) ? vec(tag.getCompound(TAG_NEST)) : null;
        this.perch = tag.contains(TAG_PERCH) ? vec(tag.getCompound(TAG_PERCH)) : null;
        this.perchYaw = tag.getFloat(TAG_PERCH_YAW);
        this.volcano = tag.getLong(TAG_VOLCANO);
        this.enraged = tag.getBoolean(TAG_ENRAGED);
        this.hostile = tag.getBoolean(TAG_HOSTILE);
        this.phase = "fight".equals(tag.getString(TAG_PHASE)) ? Phase.FIGHT : Phase.REST;
        this.setDesign(this.designNumber());
        this.refreshDimensions();
    }

    /** Its fire design's number (the adult's E1A, the young's E2A). */
    protected abstract int designNumber();

    private static CompoundTag vec(Vec3 v)
    {
        CompoundTag t = new CompoundTag();
        t.putDouble("X", v.x);
        t.putDouble("Y", v.y);
        t.putDouble("Z", v.z);
        return t;
    }

    private static Vec3 vec(CompoundTag t)
    {
        return new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
    }

    /** The design with this label ("E1A"). */
    protected static int design(String label)
    {
        for (FireDesigns.Design d : FireDesigns.EMBERDACTYL)
        {
            if (d.label().equals(label))
            {
                return d.number();
            }
        }
        throw new IllegalArgumentException("no fire design " + label);
    }

    /** Markers kept in {@code hitThisMove}. */
    private static final class Marks
    {
        /** The strafing run has reached its line and is running along it. */
        static final UUID RUNNING = new UUID(0L, 1L);
        /** Carrying, it has climbed high enough to fly across. */
        static final UUID CLIMBED = new UUID(0L, 2L);
        /** Its grab's swoop has started (it was close enough). */
        static final UUID SWOOP = new UUID(0L, 3L);
        /** Carrying, it has come to the drop's height (it holds a moment, then lets go). */
        static final UUID AT_DROP = new UUID(0L, 4L);
    }
}
