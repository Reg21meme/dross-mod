package com.reg21meme.dross.villager;

import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModParticles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One of the two <b>survivors</b> who live at the hub in the Dross (the Shattered Spire at Dross 0,0): the
 * <b>Blind Oracle</b>, the mentor, and the <b>Ragged Seeker</b>, her student ({@link SurvivorRole}). They have no trades;
 * right-clicking one makes it say its next line in chat. They spawn once per world ({@link SurvivorSpawner}).
 * <ul>
 *   <li><b>Together:</b> the Oracle wanders the hub slowly, in and around the spire and out of its front gate, to the
 *       places in her {@link SurvivorGrounds} and short strolls between them, resting at her spot now and then; she waits
 *       whenever her student falls more than {@value #WAIT_DISTANCE} blocks behind. The Seeker follows her (walking once
 *       she's more than {@value #FOLLOW_START} blocks away, stopping at {@value #FOLLOW_STOP}). Both look at players and
 *       look around. A survivor whose partner isn't there wanders on its own.</li>
 *   <li><b>Their area:</b> the hub's footprint and {@value SurvivorGrounds#AREA_MARGIN} blocks round it ({@link #getArea}).
 *       They never pick a place outside it, never walk into a portal or onto the steps by the exit portal, and keep
 *       clear of the arrival spot ({@link SurvivorGrounds.Clearance}).</li>
 *   <li><b>Popping back:</b> outside the area, after a fall of more than {@value #MAX_FALL} blocks, or when the student
 *       has been out of reach for {@value #LOST_TICKS} ticks, both pop back to their rest spots (the trader's enderman
 *       sound and blue particles). They leave any vehicle first and never ride anything.</li>
 *   <li><b>They can't die</b>, exactly like the trader: they take damage and knockback, but a hit can never take the last
 *       of their health, and at half health or less they heal fully and pop back (with their partner). Not by the
 *       invulnerable flag, which creative players ignore. {@code /kill} doesn't kill them either; only removing the
 *       entity does ({@code discard()}).</li>
 *   <li><b>Hostile mobs ignore them</b> ({@link SurvivorEvents}; and once a second any hostile mob near one that still
 *       targets it, by some other route, is made to drop it).</li>
 *   <li>They never use portals, can't be leashed and never despawn.</li>
 * </ul>
 * A survivor made any other way (for example {@code /summon dross:survivor}) is a mentor unless its {@code Role} says
 * otherwise, has no partner, and takes the spot where it first appears as its rest spot, with an area of
 * {@value SurvivorGrounds#AREA_MARGIN} blocks round it.
 */
public class Survivor extends PathfinderMob
{
    private static final EntityDataAccessor<Byte> DATA_ROLE = SynchedEntityData.defineId(Survivor.class, EntityDataSerializers.BYTE);

    // ---- Health and speed (like the trader) ----
    public static final double MAX_HEALTH = 20.0D;
    public static final double MOVEMENT_SPEED = 0.5D;
    /** How far a path may reach (blocks), far enough to cross the hub's grounds in one go (vanilla villagers use 48 too). */
    public static final double FOLLOW_RANGE = 48.0D;

    // ---- Walking. Speeds are multipliers of MOVEMENT_SPEED; Minecraft moves a walking mob by about the square of its speed. ----
    /** The Oracle's pace: slow (about 1.3 blocks a second). */
    static final double MENTOR_WALK_SPEED = 0.4D;
    /** The Seeker's pace following her (about 2 blocks a second, so it keeps up)... */
    static final double STUDENT_FOLLOW_SPEED = 0.5D;
    /** ...and when it's more than {@link #CATCH_UP_DISTANCE} blocks behind (about 3.3 blocks a second). */
    static final double STUDENT_CATCH_UP_SPEED = 0.65D;
    static final double CATCH_UP_DISTANCE = 8.0D;
    /** The pace of a survivor walking on its own (its partner isn't there). */
    static final double ALONE_WALK_SPEED = 0.45D;
    /** The Seeker starts following when the Oracle is further than this (blocks)... */
    static final double FOLLOW_START = 3.0D;
    /** ...and stops this close to her. */
    static final double FOLLOW_STOP = 2.0D;
    /** The Oracle waits while the Seeker is further than this (blocks)... */
    static final double WAIT_DISTANCE = 6.0D;
    /** ...until it's this close again. */
    static final double WAIT_RESUME = 4.0D;

    // ---- Popping back ----
    /** Falling more than this many blocks pops them back. */
    static final float MAX_FALL = 4.0F;
    /** If the Seeker stays more than WAIT_DISTANCE from the Oracle this long (ticks; it can't reach her), both pop back. */
    static final int LOST_TICKS = 30 * 20;
    /** If something blocks a rest spot, they pop back to the nearest free spot this close to it (blocks). */
    private static final int FREE_SPOT_REACH = 3;
    /** Hostile mobs this close (blocks) are checked once a second for still targeting a survivor. */
    private static final double HOSTILE_CHECK_RADIUS = 24.0D;

    // ---- Talking ----
    /** After a right-click it stands still and looks at the player this long (ticks). */
    static final int LISTEN_TICKS = 60;
    /** ...unless the player walks further away than this (blocks). */
    private static final double LISTEN_RANGE = 8.0D;
    /** About how often (ticks) it makes an idle sound: half as often as a villager. */
    private static final int AMBIENT_SOUND_INTERVAL = 160;
    /** Where the eyes are (a villager's), so it looks at you with the villager model's head. */
    private static final float EYE_HEIGHT = 1.62F;

    // ---- Saved ----
    private static final String TAG_ROLE = "Role";
    private static final String TAG_REST = "RestPos";
    private static final String TAG_REST_YAW = "RestYaw";
    /** The area, saved as six ints: min X, Y, Z, then max X, Y, Z. */
    private static final String TAG_AREA = "Area";
    private static final String TAG_WAYPOINTS = "Waypoints";
    private static final String TAG_WAYPOINT_WEIGHTS = "WaypointWeights";
    private static final String TAG_CLEARANCE = "Clearance";
    private static final String TAG_PARTNER = "Partner";

    /** Its spot at the hub (feet), where it rests and pops back to. Null until set (or its first tick). */
    @Nullable
    private BlockPos restSpot;
    private float restYaw;
    /** Where it may be (see the class comment). Null until set. */
    @Nullable
    private BoundingBox area;
    /** Covers the whole area from the rest spot, so vanilla's "is it restricted here" checks always ask {@link #isWithinRestriction(BlockPos)}. */
    private float restrictRadius = -1.0F;
    private List<SurvivorGrounds.Waypoint> waypoints = List.of();
    @Nullable
    private SurvivorGrounds.Clearance clearance;
    @Nullable
    private UUID partnerId;

    // ---- Not saved ----
    @Nullable
    private Survivor partnerCache;
    private int nextPartnerLookup;
    /** Which line each player hears next (index from 0); starts again from the first after a restart. */
    private final Map<UUID, Integer> nextLine = new HashMap<>();
    @Nullable
    private Player listener;
    private int listenUntil;
    private int lostTicks;
    /** How many times it has popped back since it was loaded (for the headless tests). */
    private int timesPoppedBack;

    public Survivor(EntityType<? extends Survivor> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    /**
     * A new survivor with this role, not yet in the world: give it a home ({@link #setHome}) and a partner, move it to
     * its rest spot ({@link #placeAtRestSpot}) and add it to the level.
     */
    @Nullable
    public static Survivor create(ServerLevel level, SurvivorRole role)
    {
        Survivor survivor = ModEntities.SURVIVOR.get().create(level);
        if (survivor != null)
        {
            survivor.setRole(role);
        }
        return survivor;
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ROLE, (byte) SurvivorRole.MENTOR.ordinal());
    }

    @Override
    protected PathNavigation createNavigation(Level level)
    {
        return new SurvivorNavigation(this, level);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SurvivorGoals.ListenGoal(this));
        this.goalSelector.addGoal(2, new SurvivorGoals.WaitForStudentGoal(this));
        this.goalSelector.addGoal(2, new SurvivorGoals.FollowMentorGoal(this));
        this.goalSelector.addGoal(3, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(4, new SurvivorGoals.WanderGoal(this));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Survivor.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Role, home and partner
    // ------------------------------------------------------------------------------------------------------------

    public SurvivorRole getRole()
    {
        return SurvivorRole.byIndex(this.entityData.get(DATA_ROLE));
    }

    public void setRole(SurvivorRole role)
    {
        this.entityData.set(DATA_ROLE, (byte) role.ordinal());
    }

    /**
     * Sets its home: its rest spot (feet) and the way it faces there, the area it may be in, the places it likes to walk
     * to, and the cells by the exit portal it keeps clear (null if there's no portal to keep clear of).
     */
    public void setHome(BlockPos rest, float yaw, BoundingBox area, List<SurvivorGrounds.Waypoint> waypoints,
                        @Nullable SurvivorGrounds.Clearance clearance)
    {
        this.restSpot = rest.immutable();
        this.restYaw = yaw;
        this.area = copy(area);
        this.waypoints = List.copyOf(waypoints);
        this.clearance = clearance;
        double far = 0.0D;
        for (int x : new int[] {area.minX(), area.maxX()})
        {
            for (int y : new int[] {area.minY(), area.maxY()})
            {
                for (int z : new int[] {area.minZ(), area.maxZ()})
                {
                    far = Math.max(far, Math.sqrt(this.restSpot.distSqr(new Vec3i(x, y, z))));
                }
            }
        }
        this.restrictRadius = (float) Math.ceil(far) + 1.0F;
        this.restrictTo(this.restSpot, (int) this.restrictRadius);
    }

    /** Sets its home from the hub grounds: survivor number {@code index} (0 = the mentor, 1 = the student). */
    public void setHome(SurvivorGrounds grounds, int index)
    {
        this.setHome(grounds.restSpot(index), grounds.restYaw(index), grounds.area(), grounds.waypoints(), grounds.clearance());
    }

    /** The other survivor of the pair (the student for the mentor, and the other way round), or null for none. */
    public void setPartner(@Nullable UUID partner)
    {
        this.partnerId = partner;
        this.partnerCache = null;
        this.nextPartnerLookup = 0;
    }

    @Nullable
    public UUID getPartnerId()
    {
        return this.partnerId;
    }

    /** Moves it onto its rest spot, facing its way (for a new survivor, before it's added to the level). */
    public void placeAtRestSpot()
    {
        if (this.restSpot != null)
        {
            this.moveTo(this.restSpot.getX() + 0.5D, this.restSpot.getY(), this.restSpot.getZ() + 0.5D, this.restYaw, 0.0F);
            this.setYHeadRot(this.restYaw);
            this.setYBodyRot(this.restYaw);
        }
    }

    @Nullable
    public BlockPos getRestSpot()
    {
        return this.restSpot;
    }

    /** A copy of its area, or null if it has none yet. */
    @Nullable
    public BoundingBox getArea()
    {
        return this.area == null ? null : copy(this.area);
    }

    public List<SurvivorGrounds.Waypoint> getWaypoints()
    {
        return this.waypoints;
    }

    @Nullable
    public SurvivorGrounds.Clearance getClearance()
    {
        return this.clearance;
    }

    /** How many times it has popped back since it was loaded. */
    public int timesPoppedBack()
    {
        return this.timesPoppedBack;
    }

    /** Its partner, if that's loaded in the same level (looked up by its UUID at most once a second). */
    @Nullable
    Survivor partner()
    {
        if (this.partnerId == null || !(this.level() instanceof ServerLevel level))
        {
            return null;
        }
        Survivor cached = this.partnerCache;
        if (cached != null && !cached.isRemoved() && cached.level() == level)
        {
            return cached;
        }
        this.partnerCache = null;
        if (this.tickCount < this.nextPartnerLookup)
        {
            return null;
        }
        this.nextPartnerLookup = this.tickCount + 20;
        Entity found = level.getEntity(this.partnerId);
        if (found instanceof Survivor survivor && survivor != this && survivor.isAlive())
        {
            this.partnerCache = survivor;
        }
        return this.partnerCache;
    }

    /** True if it picks its own walks: the mentor, or a student whose mentor isn't there. */
    boolean wandersOnItsOwn()
    {
        return this.getRole() == SurvivorRole.MENTOR || this.partner() == null;
    }

    /** Its walking pace for its own walks. */
    double walkSpeed()
    {
        return this.getRole() == SurvivorRole.MENTOR ? MENTOR_WALK_SPEED : ALONE_WALK_SPEED;
    }

    /** True if it may pick this cell to walk to or stand on: in its area and not one of the cells kept clear. */
    boolean isGoodPlaceToStand(BlockPos cell)
    {
        return this.isWithinRestriction(cell) && (this.clearance == null || !this.clearance.keepsRestingOff(cell));
    }

    /**
     * True for cells it never walks through (its path finding treats them as walls): any portal, and the cells kept
     * clear by the exit portal. The cells it stands in now never count, so it can always walk out of one.
     */
    boolean keepsOutOf(BlockGetter level, int x, int y, int z)
    {
        BlockPos feet = this.blockPosition();
        if (x == feet.getX() && z == feet.getZ() && (y == feet.getY() || y == feet.getY() + 1))
        {
            return false;
        }
        if (this.clearance != null && this.clearance.keepsWalkingOff(x, y, z))
        {
            return true;
        }
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        return state.is(BlockTags.PORTALS) || state.is(ModBlocks.DROSS_PORTAL.get());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Its area (vanilla's "restriction", as a box)
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public boolean hasRestriction()
    {
        return this.area != null || super.hasRestriction();
    }

    @Override
    public boolean isWithinRestriction(BlockPos pos)
    {
        return this.area != null ? this.area.isInside(pos) : super.isWithinRestriction(pos);
    }

    @Override
    public BlockPos getRestrictCenter()
    {
        return this.restSpot != null ? this.restSpot : super.getRestrictCenter();
    }

    @Override
    public float getRestrictRadius()
    {
        return this.area != null ? this.restrictRadius : super.getRestrictRadius();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Every tick
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        if (this.restSpot == null)
        {
            // Summoned some other way: wherever it first appears is its rest spot.
            BlockPos here = this.blockPosition();
            this.setHome(here, this.getYRot(), new BoundingBox(here).inflatedBy(SurvivorGrounds.AREA_MARGIN), List.of(), null);
        }
        if (this.isPassenger())
        {
            this.stopRiding(); // never rides anything (a boat, a minecart, /ride...)
        }
        if (this.isVehicle())
        {
            this.ejectPassengers();
        }
        if (!this.area.isInside(this.blockPosition()) || this.fallDistance > MAX_FALL)
        {
            this.popBack(level, true);
            return;
        }
        if (this.getRole() == SurvivorRole.MENTOR)
        {
            this.watchForLostStudent(level);
        }
        if (this.tickCount % 20 == 0)
        {
            this.shakeOffHostileMobs(level);
        }
    }

    /** If the student has been out of reach too long (it can't get to her), both pop back to their spots. */
    private void watchForLostStudent(ServerLevel level)
    {
        Survivor student = this.partner();
        if (student != null && this.distanceToSqr(student) > WAIT_DISTANCE * WAIT_DISTANCE)
        {
            if (++this.lostTicks > LOST_TICKS)
            {
                this.popBack(level, true);
            }
        }
        else
        {
            this.lostTicks = 0;
        }
    }

    /**
     * Hostile mobs never pick a survivor as a target ({@link SurvivorEvents}). This catches the rare ones that set a target
     * some other way (the warden, or another mod's mob): any hostile mob near it that still targets it drops it.
     */
    private void shakeOffHostileMobs(ServerLevel level)
    {
        AABB near = this.getBoundingBox().inflate(HOSTILE_CHECK_RADIUS);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, near, mob -> mob instanceof Enemy))
        {
            if (mob.getTarget() == this)
            {
                mob.setTarget(null);
            }
            Brain<?> brain = mob.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == this)
            {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Can't die, pops back
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Exactly like the trader: it takes damage and knockback normally, but a hit can never take its last bit of health,
     * and at half health or less it heals fully and pops back to its rest spot (its partner too).
     */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return super.hurt(source, amount);
        }
        boolean wasHurt = super.hurt(source, Math.min(amount, Math.max(this.getHealth() - 1.0F, 0.0F)));
        if (this.isAlive() && this.getHealth() <= this.getMaxHealth() / 2.0F)
        {
            this.setHealth(this.getMaxHealth());
            if (this.restSpot != null)
            {
                this.popBack(level, true);
            }
        }
        return wasHurt;
    }

    /**
     * A safety net under {@link #hurt}: its health never drops below 1 while it's in the world, even if another mod adds
     * damage after the cap (so it can never reach 0 and die). Removing the entity still works.
     */
    @Override
    public void setHealth(float health)
    {
        super.setHealth(this.isRemoved() ? health : Math.max(health, 1.0F));
    }

    /**
     * Pops back to its rest spot, enderman-style, with the portal's blue particles at both ends: out of any vehicle,
     * fire put out, any effects cleared. If {@code partnerToo}, its partner (if it's loaded) pops back to its own spot too.
     */
    public void popBack(ServerLevel level, boolean partnerToo)
    {
        if (this.restSpot == null)
        {
            return;
        }
        this.stopRiding();
        this.ejectPassengers();
        this.teleportEffects(level);
        this.getNavigation().stop();
        this.clearFire();
        this.removeAllEffects();
        BlockPos spot = this.freeSpotNear(level, this.restSpot);
        this.setYRot(this.restYaw);
        this.setYHeadRot(this.restYaw);
        this.setYBodyRot(this.restYaw);
        this.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        this.lostTicks = 0;
        this.timesPoppedBack++;
        this.teleportEffects(level);
        if (partnerToo)
        {
            Survivor partner = this.partner();
            if (partner != null && partner.isAlive())
            {
                partner.popBack(level, false);
            }
        }
    }

    /** The rest spot, or (if a block or something solid is in the way) the nearest free spot to stand on near it. */
    private BlockPos freeSpotNear(ServerLevel level, BlockPos rest)
    {
        EntityDimensions size = this.getDimensions(Pose.STANDING);
        if (SurvivorGrounds.canStandAt(level, rest, size))
        {
            return rest;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : BlockPos.betweenClosed(rest.offset(-FREE_SPOT_REACH, -2, -FREE_SPOT_REACH), rest.offset(FREE_SPOT_REACH, 2, FREE_SPOT_REACH)))
        {
            double distance = cell.distSqr(rest);
            if (distance < bestDistance && this.isGoodPlaceToStand(cell) && SurvivorGrounds.canStandAt(level, cell, size))
            {
                best = cell.immutable();
                bestDistance = distance;
            }
        }
        return best != null ? best : rest;
    }

    private void teleportEffects(ServerLevel level)
    {
        level.sendParticles(ModParticles.DROSS_PORTAL.get(),
                this.getX(), this.getY() + this.getBbHeight() / 2.0D, this.getZ(),
                64, this.getBbWidth(), this.getBbHeight() / 2.0D, this.getBbWidth(), 0.5D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.0F, 1.0F);
    }

    /** The exit portal is right beside them: they never go through any portal. */
    @Override
    public boolean canChangeDimensions()
    {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    /** It never gets into a boat, a minecart or onto anything (and leaves at once if put there by a command). */
    @Override
    protected boolean canRide(Entity vehicle)
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Talking
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Right-click (main hand only; sneaking falls through to vanilla, which does nothing here): it says its next line
     * in chat, with its name as the speaker ({@code <Blind Oracle> ...}), then stands still and looks at the player for
     * a moment. Each player hears its lines in order, then from the first again. There's never a trading screen.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (player.isSecondaryUseActive())
        {
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && this.isAlive())
        {
            this.speakTo(serverPlayer);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private void speakTo(ServerPlayer player)
    {
        SurvivorRole role = this.getRole();
        int index = Math.floorMod(this.nextLine.getOrDefault(player.getUUID(), 0), role.lineCount());
        this.nextLine.put(player.getUUID(), (index + 1) % role.lineCount());
        Component line = Component.translatable(role.lineKey(index + 1));
        player.sendSystemMessage(Component.translatable("chat.type.text", this.getDisplayName(), line));
        this.playSound(SoundEvents.VILLAGER_AMBIENT, 1.0F, this.getVoicePitch());
        this.listener = player;
        this.listenUntil = this.tickCount + LISTEN_TICKS;
    }

    /** The player it's listening to (for a moment after a right-click), or null. */
    @Nullable
    Player listener()
    {
        Player player = this.listener;
        if (player == null)
        {
            return null;
        }
        if (this.tickCount > this.listenUntil || !player.isAlive() || player.level() != this.level()
                || this.distanceToSqr(player) > LISTEN_RANGE * LISTEN_RANGE)
        {
            this.listener = null;
            return null;
        }
        return player;
    }

    /** Its name, "Blind Oracle" or "Ragged Seeker" (a name tag still overrides it, as for any mob). */
    @Override
    protected Component getTypeName()
    {
        return this.getRole().displayName();
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions)
    {
        return EYE_HEIGHT;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Sounds: a villager's, lower for the Oracle and higher for the Seeker
    // ------------------------------------------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.VILLAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return SoundEvents.VILLAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.VILLAGER_DEATH;
    }

    @Override
    public float getVoicePitch()
    {
        return this.getRole().voicePitch() + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return AMBIENT_SOUND_INTERVAL;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_ROLE, this.getRole().id());
        if (this.restSpot != null && this.area != null)
        {
            tag.put(TAG_REST, NbtUtils.writeBlockPos(this.restSpot));
            tag.putFloat(TAG_REST_YAW, this.restYaw);
            tag.putIntArray(TAG_AREA, new int[] {
                    this.area.minX(), this.area.minY(), this.area.minZ(), this.area.maxX(), this.area.maxY(), this.area.maxZ()});
            long[] places = new long[this.waypoints.size()];
            int[] weights = new int[this.waypoints.size()];
            for (int i = 0; i < places.length; i++)
            {
                places[i] = this.waypoints.get(i).pos().asLong();
                weights[i] = this.waypoints.get(i).weight();
            }
            tag.putLongArray(TAG_WAYPOINTS, places);
            tag.putIntArray(TAG_WAYPOINT_WEIGHTS, weights);
            if (this.clearance != null)
            {
                tag.put(TAG_CLEARANCE, this.clearance.save());
            }
        }
        if (this.partnerId != null)
        {
            tag.putUUID(TAG_PARTNER, this.partnerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setRole(SurvivorRole.byId(tag.getString(TAG_ROLE)));
        this.setPartner(tag.hasUUID(TAG_PARTNER) ? tag.getUUID(TAG_PARTNER) : null);
        int[] box = tag.getIntArray(TAG_AREA);
        if (tag.contains(TAG_REST) && box.length == 6)
        {
            long[] places = tag.getLongArray(TAG_WAYPOINTS);
            int[] weights = tag.getIntArray(TAG_WAYPOINT_WEIGHTS);
            List<SurvivorGrounds.Waypoint> list = new ArrayList<>();
            for (int i = 0; i < places.length; i++)
            {
                list.add(new SurvivorGrounds.Waypoint(BlockPos.of(places[i]), i < weights.length ? Math.max(1, weights[i]) : 1));
            }
            SurvivorGrounds.Clearance kept = tag.contains(TAG_CLEARANCE) ? SurvivorGrounds.Clearance.load(tag.getCompound(TAG_CLEARANCE)) : null;
            // fromCorners: a box saved the wrong way round can't throw.
            BoundingBox saved = BoundingBox.fromCorners(new Vec3i(box[0], box[1], box[2]), new Vec3i(box[3], box[4], box[5]));
            this.setHome(NbtUtils.readBlockPos(tag.getCompound(TAG_REST)), tag.getFloat(TAG_REST_YAW), saved, list, kept);
        }
    }

    private static BoundingBox copy(BoundingBox box)
    {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
