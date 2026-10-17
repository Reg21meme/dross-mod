package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A fighting Rat King's burrow (every number is in {@link RatKingTuning}). It starts when his health reaches a burrow
 * checkpoint ({@link RatKing#beginBurrow}: the {@code burrow} animation, digging and diving in; he can't be hurt from
 * the dive on). Then:
 * <ol>
 *   <li><b>Under the ground</b> ({@link #goUnder}, {@link #tickUnder}): for {@link RatKingTuning#UNDERGROUND_TICKS}
 *       ({@link RatKingTuning#RAGE_UNDERGROUND_TICKS} in the Rage) he tunnels towards his target at
 *       {@link RatKingTuning#UNDERGROUND_SPEED} blocks a tick, following the ground (only his dirt mound shows, and a
 *       trail of dirt where he goes); nothing can hit, target or bump into him. Meanwhile his rats burst out of the
 *       ground round his target, one every {@link RatKingTuning#RAT_STAGGER_TICKS} ticks (a little faster when that many
 *       wouldn't all be out before he pops up): {@link RatKingTuning#RATS_PER_BURROW}
 *       regular ones ({@link RatKingTuning#RAGE_RATS_PER_BURROW} enraged ones in the Rage), however many of his are
 *       already alive. They're only ever called while he's under the ground.</li>
 *   <li><b>Popping up</b> ({@link #popUp}): he comes up {@link RatKingTuning#POPUP_DISTANCE} blocks from his target,
 *       facing it, on a spot where he fits (the {@code popup} animation: straight up out of a hole right under him,
 *       landing on that spot; {@link RatKing} keeps him turned to his target as he comes up). He can't be hurt until
 *       he has landed; popping up never hurts or knocks back anyone.</li>
 * </ol>
 * Before he digs in he turns to face his target ({@link RatKing}), so he dives (and his mound sets off) towards it;
 * under the ground he faces the way he tunnels.
 * While a burrow runs, his health can't go below the checkpoint that started it (one burrow at a time).
 */
final class RatKingBurrow
{
    private static final String TAG_ACTIVE = "Burrowing";
    private static final String TAG_FLOOR = "BurrowFloor";
    private static final String TAG_UNDERGROUND = "UndergroundLeft";
    private static final String TAG_RATS = "RatsLeft";
    private static final String TAG_RAT_TIMER = "RatTimer";
    private static final String TAG_CALLED = "RatsCalled";
    private static final String TAG_RING = "RatRing";

    /** A Rat King loaded under the ground without a saved time left pops up after this many ticks. */
    private static final int RESUME_TICKS = 10;
    /** Following the ground: how far up and down from where he is it looks for the surface. */
    private static final int GROUND_UP = 3;
    private static final int GROUND_DOWN = 6;
    /** Each rat's spot round the target: a few tries, each turned a little from its share of the ring. */
    private static final int RAT_SPOT_TRIES = 6;
    private static final double RAT_ANGLE_JITTER = 0.35D;
    /** Popping up: turns tried round the target from his side (degrees), and distances (blocks). */
    private static final double[] POPUP_TURNS = {0.0D, 30.0D, -30.0D, 60.0D, -60.0D, 90.0D, -90.0D, 120.0D, -120.0D,
            150.0D, -150.0D, 180.0D};
    private static final double[] POPUP_SHIFTS = {0.0D, -1.0D, 1.0D, -2.0D};
    /** How far up and down from the target's feet a pop-up spot (or a rat's) may be. */
    private static final int SPOT_UP = 3;
    private static final int SPOT_DOWN = 4;
    /** Sounds: the dig while he tunnels, and his call to his rats. */
    private static final float DIG_VOLUME = 0.7F;
    private static final float CALL_VOLUME = 1.6F;
    /** Closer than this to where he's tunnelling to, he stops. */
    private static final double ARRIVED = 0.05D;

    private final RatKing king;
    private boolean active;
    /** The checkpoint that started this burrow: his health can't go below it until he's back up. */
    private float floor;
    private int undergroundLeft;
    private int ratsLeft;
    private int ratTimer;
    private boolean called;
    /** Where round the target the first rat bursts out (radians); the rest follow round the ring. */
    private double ringStart;

    RatKingBurrow(RatKing king)
    {
        this.king = king;
    }

    boolean isActive()
    {
        return this.active;
    }

    float floor()
    {
        return this.floor;
    }

    /** A burrow starts at this checkpoint (the dig and dive play now). */
    void begin(float checkpoint)
    {
        this.active = true;
        this.floor = checkpoint;
        this.undergroundLeft = 0;
        this.ratsLeft = 0;
    }

    /** He's back on his feet (or dead): the burrow is over. */
    void finish()
    {
        this.active = false;
        this.ratsLeft = 0;
        this.undergroundLeft = 0;
    }

    /** The dive has finished: he's under the ground now, and his rats are on their way. */
    void goUnder()
    {
        this.active = true;
        this.king.startAction(RatKingAction.BURROWED);
        this.undergroundLeft = this.king.isEnraged() ? RatKingTuning.RAGE_UNDERGROUND_TICKS : RatKingTuning.UNDERGROUND_TICKS;
        this.ratsLeft = this.ratsPerBurrow();
        this.ratTimer = RatKingTuning.RAT_FIRST_DELAY;
        this.called = false;
        this.ringStart = this.king.getRandom().nextDouble() * 2.0D * Math.PI;
    }

    /** A tick under the ground: tunnel towards the target, call the rats, and pop up when the time is up. */
    void tickUnder()
    {
        if (!(this.king.level() instanceof ServerLevel level))
        {
            return;
        }
        LivingEntity target = this.liveTarget();
        if (target != null)
        {
            this.tunnelTowards(level, this.popupGoal(target));
        }
        this.king.setDeltaMovement(Vec3.ZERO);
        if (this.undergroundLeft % RatKingTuning.UNDERGROUND_DIG_EVERY == 0)
        {
            Vec3 mound = this.mound();
            level.playSound(null, mound.x, mound.y, mound.z, ModSounds.RAT_KING_DIG.get(), this.king.getSoundSource(),
                    DIG_VOLUME, 0.9F + this.king.getRandom().nextFloat() * 0.2F);
        }
        if (this.ratsLeft > 0 && --this.ratTimer <= 0)
        {
            this.ratTimer = this.ratStagger();
            int index = this.ratsPerBurrow() - this.ratsLeft;
            this.ratsLeft--;
            if (this.summonRat(level, target, index) && !this.called)
            {
                this.called = true;
                Vec3 mound = this.mound();
                level.playSound(null, mound.x, mound.y, mound.z, ModSounds.RAT_KING_ATTACK_SUMMON.get(),
                        this.king.getSoundSource(), CALL_VOLUME, 1.0F);
            }
        }
        if (--this.undergroundLeft <= 0)
        {
            this.popUp(level);
        }
    }

    /**
     * How many rats this burrow calls: regular ones in the first phase, enraged ones in the Rage, more with each burrow
     * of the phase ({@link RatKingTuning#RATS_PER_BURROW}, {@link RatKingTuning#RAGE_RATS_PER_BURROW}). Which burrow it
     * is comes from the checkpoint that started it (saved with him).
     */
    private int ratsPerBurrow()
    {
        return ratsFor(this.king.isEnraged(), this.king.getMaxHealth(), this.floor);
    }

    /**
     * Ticks between his rats: {@link RatKingTuning#RAT_STAGGER_TICKS}, or fewer when this burrow calls more rats than
     * fit in his time under the ground at that pace (the Rage's last burrow: 10 rats in 50 ticks), so every one is out
     * before he pops up.
     */
    private int ratStagger()
    {
        int under = this.king.isEnraged() ? RatKingTuning.RAGE_UNDERGROUND_TICKS : RatKingTuning.UNDERGROUND_TICKS;
        int rats = this.ratsPerBurrow();
        if (rats <= 1)
        {
            return RatKingTuning.RAT_STAGGER_TICKS;
        }
        return Mth.clamp((under - RatKingTuning.RAT_FIRST_DELAY) / (rats - 1), 1, RatKingTuning.RAT_STAGGER_TICKS);
    }

    /** The rats called by the burrow at this checkpoint (his health) in a phase, for a Rat King with this much health. */
    static int ratsFor(boolean rage, float maxHealth, float checkpoint)
    {
        List<Integer> counts = rage ? RatKingTuning.RAGE_RATS_PER_BURROW : RatKingTuning.RATS_PER_BURROW;
        float start = rage ? RatKingTuning.RAGE_AT * maxHealth : maxHealth;
        float every = rage ? RatKingTuning.RAGE_BURROW_EVERY : RatKingTuning.BURROW_EVERY;
        int burrow = Math.round((start - checkpoint) / every) - 1; // 0 for the phase's first
        return counts.get(Mth.clamp(burrow, 0, counts.size() - 1));
    }

    /** His target, if it's still alive and in the same world. */
    @Nullable
    private LivingEntity liveTarget()
    {
        LivingEntity target = this.king.getTarget();
        return target != null && target.isAlive() && target.level() == this.king.level() ? target : null;
    }

    /** Where his dirt mound is (in front of him, over his head). */
    private Vec3 mound()
    {
        return this.king.position().add(this.king.forward().scale(RatKingDesigns.HOLE_FORWARD));
    }

    /** Where he tunnels to: {@link RatKingTuning#POPUP_DISTANCE} from the target, on his side of it. */
    private Vec3 popupGoal(LivingEntity target)
    {
        Vec3 away = flat(this.king.position().subtract(target.position()));
        if (away.lengthSqr() < 1.0E-4D)
        {
            away = this.king.forward().scale(-1.0D);
        }
        return target.position().add(away.normalize().scale(RatKingTuning.POPUP_DISTANCE));
    }

    /** One tick of tunnelling: towards {@code goal} along the ground, his mound leading the way. */
    private void tunnelTowards(ServerLevel level, Vec3 goal)
    {
        Vec3 here = this.king.position();
        Vec3 to = flat(goal.subtract(here));
        double distance = to.length();
        if (distance < ARRIVED)
        {
            return;
        }
        double step = Math.min(RatKingTuning.UNDERGROUND_SPEED, distance);
        Vec3 next = here.add(to.scale(step / distance));
        double y = groundY(level, next.x, next.z, here.y);
        this.king.setPos(next.x, y, next.z);
        this.king.face((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
    }

    /**
     * The top of the ground under (x, z) nearest the height {@code fromY}: where a block with something to stand on has
     * room above it. If there's none near, he keeps his height.
     */
    static double groundY(ServerLevel level, double x, double z, double fromY)
    {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int y0 = Mth.floor(fromY + 0.01D);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int k = 0; k <= Math.max(GROUND_UP, GROUND_DOWN); k++)
        {
            for (int sign : new int[] {-1, 1})
            {
                if ((sign > 0 && k > GROUND_UP) || (sign < 0 && k > GROUND_DOWN) || (k == 0 && sign > 0))
                {
                    continue;
                }
                int y = y0 + sign * k;
                pos.set(bx, y, bz);
                if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                {
                    continue; // the feet's block must be open
                }
                pos.set(bx, y - 1, bz);
                BlockState below = level.getBlockState(pos);
                VoxelShape shape = below.getCollisionShape(level, pos);
                if (!shape.isEmpty())
                {
                    return y - 1 + shape.max(Direction.Axis.Y);
                }
            }
        }
        return fromY;
    }

    /**
     * One rat bursts out of the ground round the target (or round him, with no target): its share of the ring
     * {@link RatKingTuning#RAT_RING_MIN} to {@link RatKingTuning#RAT_RING_MAX} blocks out, facing the middle.
     *
     * @return true if it did (there was room)
     */
    private boolean summonRat(ServerLevel level, @Nullable LivingEntity target, int index)
    {
        Vec3 centre = target != null ? target.position() : this.king.position();
        EntityType<Rat> type = (this.king.isEnraged() ? ModEntities.ENRAGED_RAT : ModEntities.RAT).get();
        RandomSource random = this.king.getRandom();
        for (int tries = 0; tries < RAT_SPOT_TRIES; tries++)
        {
            double angle = this.ringStart + 2.0D * Math.PI * index / this.ratsPerBurrow()
                    + (random.nextDouble() - 0.5D) * 2.0D * RAT_ANGLE_JITTER * (tries + 1);
            double dist = RatKingTuning.RAT_RING_MIN + random.nextDouble() * (RatKingTuning.RAT_RING_MAX - RatKingTuning.RAT_RING_MIN);
            Vec3 at = centre.add(Math.cos(angle) * dist, 0.0D, Math.sin(angle) * dist);
            Vec3 feet = standingSpot(level, type, at, centre.y);
            if (feet != null)
            {
                float yaw = (float) (Mth.atan2(centre.z - feet.z, centre.x - feet.x) * Mth.RAD_TO_DEG) - 90.0F;
                return Rat.summon(type, level, this.king, feet, yaw) != null;
            }
        }
        return false;
    }

    /**
     * He pops up: {@link RatKingTuning#POPUP_DISTANCE} from his target, facing it, on the first spot round it (from his
     * side) where he fits; without a target, near where he is.
     */
    void popUp(ServerLevel level)
    {
        LivingEntity target = this.liveTarget();
        EntityType<?> type = this.king.getType();
        Vec3 spot = null;
        if (target != null)
        {
            Vec3 away = flat(this.king.position().subtract(target.position()));
            away = away.lengthSqr() < 1.0E-4D ? this.king.forward().scale(-1.0D) : away.normalize();
            for (double shift : POPUP_SHIFTS)
            {
                for (double turn : POPUP_TURNS)
                {
                    Vec3 at = target.position().add(rotate(away, turn).scale(RatKingTuning.POPUP_DISTANCE + shift));
                    spot = standingSpot(level, type, at, target.getY());
                    if (spot != null)
                    {
                        break;
                    }
                }
                if (spot != null)
                {
                    break;
                }
            }
        }
        if (spot == null)
        {
            spot = standingSpot(level, type, this.king.position(), this.king.getY());
        }
        if (spot == null)
        {
            spot = this.king.position();
        }
        float yaw = this.king.getYRot();
        if (target != null)
        {
            yaw = (float) (Mth.atan2(target.getZ() - spot.z, target.getX() - spot.x) * Mth.RAD_TO_DEG) - 90.0F;
        }
        this.king.noPhysics = false;
        this.king.setNoGravity(false);
        this.king.moveTo(spot.x, spot.y, spot.z, yaw, 0.0F);
        this.king.face(yaw);
        this.king.yBodyRotO = yaw;
        this.king.yHeadRotO = yaw;
        this.king.setDeltaMovement(Vec3.ZERO);
        this.undergroundLeft = 0;
        this.ratsLeft = 0;
        this.king.startAction(RatKingAction.POPUP);
    }

    /**
     * A spot near {@code at} (same x and z) where an entity of this type stands on solid ground with room (no blocks,
     * no liquid), {@link #SPOT_UP} above to {@link #SPOT_DOWN} below {@code nearY}; null if there's none.
     */
    @Nullable
    static Vec3 standingSpot(ServerLevel level, EntityType<?> type, Vec3 at, double nearY)
    {
        int y0 = Mth.floor(nearY + 0.01D);
        for (int k = 0; k <= Math.max(SPOT_UP, SPOT_DOWN); k++)
        {
            for (int sign : new int[] {1, -1})
            {
                if ((sign > 0 && k > SPOT_UP) || (sign < 0 && k > SPOT_DOWN) || (k == 0 && sign < 0))
                {
                    continue;
                }
                BlockPos below = BlockPos.containing(at.x, y0 + sign * k - 1, at.z);
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
                {
                    continue;
                }
                Vec3 feet = new Vec3(at.x, below.getY() + 1, at.z);
                AABB box = type.getAABB(feet.x, feet.y, feet.z);
                if (level.noCollision(box) && !level.containsAnyLiquid(box))
                {
                    return feet;
                }
            }
        }
        return null;
    }

    private static Vec3 rotate(Vec3 dir, double degrees)
    {
        double a = Math.toRadians(degrees);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        return new Vec3(dir.x * cos - dir.z * sin, 0.0D, dir.x * sin + dir.z * cos);
    }

    private static Vec3 flat(Vec3 v)
    {
        return new Vec3(v.x, 0.0D, v.z);
    }

    // ---------------------------------------------------------------- saving

    void save(CompoundTag tag)
    {
        if (!this.active)
        {
            return;
        }
        tag.putBoolean(TAG_ACTIVE, true);
        tag.putFloat(TAG_FLOOR, this.floor);
        tag.putInt(TAG_UNDERGROUND, this.undergroundLeft);
        tag.putInt(TAG_RATS, this.ratsLeft);
        tag.putInt(TAG_RAT_TIMER, this.ratTimer);
        tag.putBoolean(TAG_CALLED, this.called);
        tag.putDouble(TAG_RING, this.ringStart);
    }

    /**
     * @param underground true if he was saved under the ground (or diving in): he carries on from there and pops up;
     *                    otherwise any burrow he was in is over
     */
    void load(CompoundTag tag, boolean underground)
    {
        if (!underground)
        {
            this.finish();
            return;
        }
        this.active = true;
        this.floor = tag.contains(TAG_FLOOR) ? tag.getFloat(TAG_FLOOR) : this.king.getHealth();
        this.undergroundLeft = tag.getInt(TAG_UNDERGROUND) > 0 ? tag.getInt(TAG_UNDERGROUND) : RESUME_TICKS;
        this.ratsLeft = tag.getInt(TAG_RATS);
        this.ratTimer = Math.max(1, tag.getInt(TAG_RAT_TIMER));
        this.called = tag.getBoolean(TAG_CALLED);
        this.ringStart = tag.getDouble(TAG_RING);
    }
}
