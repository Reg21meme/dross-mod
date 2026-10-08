package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.villager.DrossTrader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

/**
 * The risen undead: real vanilla mobs (zombies, skeletons, ...) raised by Necromancy, marked with their owner.
 *
 * <ul>
 *   <li>The owner's UUID and the spawn time go into the mob's persistent data before it joins the world,
 *       so other code can recognise it with {@link #isRisen(Entity)} (the Dross netherite rule skips them).</li>
 *   <li>Their hostile targeting is replaced: they attack what the owner hits, mobs that hurt the owner and mobs
 *       that hurt them, and follow the owner when idle. Never players, villagers, wandering traders, the Dross
 *       trader or the owner's other risen undead (damage from them, arrows included, to those is cancelled too).
 *       The owner's own hits never damage them. Risen drowned fight on land, day or night.</li>
 *   <li>They crumble (orange puff) after {@link #DESPAWN_TICKS}, or early when the owner logs out, dies or changes
 *       dimension, or when their chunk is loaded back from a save.</li>
 *   <li>No drops, no XP, no item pickup, no portals, no conversions. Vanilla sunlight burning still applies.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RisenUndead
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /** Lifetime before they crumble: 60 seconds. */
    public static final int DESPAWN_TICKS = 1200;

    /** They rise on a random free spot up to this many blocks (X and Z) from the owner. */
    public static final int SPAWN_RADIUS = 3;
    /** Tries to find a free spot before giving up and rising right at the owner's feet. */
    public static final int SPAWN_ATTEMPTS = 12;

    /** They start walking after the owner when further away than this (blocks). Same as a tamed wolf. */
    public static final double FOLLOW_START_DISTANCE = 10.0D;
    /** ...and stop once this close (blocks). */
    public static final double FOLLOW_STOP_DISTANCE = 2.0D;
    /** Further than this (blocks) and they teleport next to the owner. Same as a tamed wolf. */
    public static final double TELEPORT_DISTANCE = 12.0D;
    /** Walking speed multiplier while following the owner (1.0 = their normal speed). */
    public static final double FOLLOW_SPEED = 1.2D;

    /** Goal priorities. Lower runs first. Attack goals are 2 (zombies) and 4 (skeletons), so following waits. */
    private static final int FOLLOW_GOAL_PRIORITY = 5;
    /** How long (ticks) a brain-based mob (piglin, hoglin...) stays angry at a risen undead that hit it: 30 s. */
    private static final long BRAIN_ANGER_TICKS = 600L;

    /** Same priority as the zombie attack goal vanilla drowned use. */
    private static final int DROWNED_ATTACK_GOAL_PRIORITY = 2;

    /** Orange of the rise/crumble puff (red, green, blue from 0 to 1), and its size. */
    private static final DustParticleOptions ORANGE_DUST = new DustParticleOptions(new Vector3f(1.0F, 0.5F, 0.0F), 1.5F);
    /** Number of particles in a puff. */
    private static final int PUFF_PARTICLES = 25;

    /** Scoreboard team for Deathforged X glow. Its colour (gold, Minecraft's closest to orange) colours the outline. */
    private static final String GLOW_TEAM = "dross_risen_glow";
    private static final ChatFormatting GLOW_COLOR = ChatFormatting.GOLD;

    // ---------------------------------------------------------------------------------------------
    // Persistent-data keys.
    // ---------------------------------------------------------------------------------------------

    /** Owner's UUID. Present on every risen mob and mount. */
    public static final String OWNER_KEY = "dross_risen_owner";
    /** Game time (level ticks) they rose at. */
    public static final String SPAWN_TIME_KEY = "dross_risen_spawn_time";
    /** True on mounts (chickens, spiders, skeleton horses): they don't count toward the "alive" limit. */
    public static final String MOUNT_KEY = "dross_risen_mount";
    /** Owner's UUID on arrows shot by risen skeletons, so they stay harmless even after the skeleton crumbles. */
    public static final String PROJECTILE_OWNER_KEY = "dross_risen_projectile_owner";

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Armor per Deathforged level I-VI (VII and up stay netherite). Order: helmet, chestplate, leggings, boots. */
    private static final Item[][] ARMOR_BY_LEVEL = {
            {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS},
            {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS},
            {Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS},
    };

    /** Every risen mob (riders and mounts) currently in a loaded world, on the server. */
    private static final Set<Mob> ACTIVE = new LinkedHashSet<>();

    private RisenUndead() {}

    // ---------------------------------------------------------------------------------------------
    // Public helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * True if this entity is a risen undead (or a risen jockey's mount) raised by Necromancy.
     * Works on the server only (persistent data isn't sent to clients). Safe to call with null.
     */
    public static boolean isRisen(@Nullable Entity entity)
    {
        return entity != null && entity.getPersistentData().hasUUID(OWNER_KEY);
    }

    /** The owner's UUID, or null if the entity isn't risen. */
    @Nullable
    public static UUID getOwnerId(@Nullable Entity entity)
    {
        return isRisen(entity) ? entity.getPersistentData().getUUID(OWNER_KEY) : null;
    }

    /** The owner if they're in the same world as the mob, otherwise null. */
    @Nullable
    public static Player getOwner(Mob mob)
    {
        UUID owner = getOwnerId(mob);
        return owner == null ? null : mob.level().getPlayerByUUID(owner);
    }

    /** How many of this player's risen undead are alive. A jockey counts once (its mount isn't counted). */
    public static int countAlive(UUID owner)
    {
        int count = 0;
        for (Mob mob : ACTIVE)
        {
            if (mob.isAlive() && !mob.getPersistentData().getBoolean(MOUNT_KEY) && owner.equals(getOwnerId(mob)))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Never players, the Dross trader, villagers or wandering traders, themselves, or their owner's other risen
     * undead (even if the owner hits one of those).
     */
    public static boolean isValidTarget(Mob risen, @Nullable LivingEntity target)
    {
        if (target == null || target == risen || !target.isAlive())
        {
            return false;
        }
        if (isProtected(target))
        {
            return false;
        }
        UUID owner = getOwnerId(risen);
        return owner == null || !owner.equals(getOwnerId(target));
    }

    /**
     * Entities risen undead never attack or hurt: players, villagers and wandering traders
     * ({@link AbstractVillager} covers both), and the Dross trader.
     */
    private static boolean isProtected(Entity entity)
    {
        return entity instanceof Player || entity instanceof AbstractVillager || entity instanceof DrossTrader;
    }

    // ---------------------------------------------------------------------------------------------
    // Raising
    // ---------------------------------------------------------------------------------------------

    /**
     * Raises one undead of the given type next to the owner, with Deathforged gear for the given level (0 = none),
     * and sends it after {@code firstTarget} (the mob the owner just hit) if that's a valid target.
     * Returns true if it was spawned.
     */
    static boolean raise(ServerPlayer owner, RisenType type, int deathforgedLevel, @Nullable LivingEntity firstTarget)
    {
        ServerLevel level = owner.serverLevel();
        Mob rider;
        Mob mount = null;
        switch (type)
        {
            case ZOMBIE -> rider = EntityType.ZOMBIE.create(level);
            case SKELETON -> rider = EntityType.SKELETON.create(level);
            case HUSK -> rider = EntityType.HUSK.create(level);
            case STRAY -> rider = EntityType.STRAY.create(level);
            case DROWNED -> rider = EntityType.DROWNED.create(level);
            case CHICKEN_JOCKEY -> {
                Zombie baby = EntityType.ZOMBIE.create(level);
                if (baby != null)
                {
                    baby.setBaby(true);
                }
                rider = baby;
                Chicken chicken = EntityType.CHICKEN.create(level);
                if (chicken != null)
                {
                    chicken.setChickenJockey(true); // also stops it laying eggs
                }
                mount = chicken;
            }
            case SPIDER_JOCKEY -> {
                rider = EntityType.SKELETON.create(level);
                mount = EntityType.SPIDER.create(level);
            }
            case SKELETON_HORSEMAN -> {
                rider = EntityType.SKELETON.create(level);
                SkeletonHorse horse = EntityType.SKELETON_HORSE.create(level);
                if (horse != null)
                {
                    // Same as vanilla's skeleton trap horsemen.
                    horse.setTamed(true);
                    horse.setAge(0);
                }
                mount = horse;
            }
            default -> rider = null;
        }
        if (rider == null || (type.mounted && mount == null))
        {
            return false;
        }

        Mob body = mount != null ? mount : rider;
        BlockPos pos = findSpawnPos(owner, body);
        float yaw = owner.getRandom().nextFloat() * 360.0F;
        long now = level.getGameTime();

        // Mark and set up BEFORE adding to the world, so EntityJoinLevelEvent handlers (the Dross netherite rule)
        // already see them as risen. finalizeSpawn is deliberately not called: no random vanilla gear.
        rider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        setUp(rider, owner, now, false);
        applyDeathforgedGear(rider, deathforgedLevel);
        if (mount != null)
        {
            mount.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
            setUp(mount, owner, now, true);
            rider.startRiding(mount, true);
            level.addFreshEntityWithPassengers(mount);
            if (mount.isAddedToWorld())
            {
                ACTIVE.add(mount);
            }
        }
        else
        {
            level.addFreshEntity(rider);
        }
        if (!rider.isAddedToWorld())
        {
            // Another mod (or the game) refused the spawn. A mount left without its rider crumbles.
            if (mount != null && mount.isAddedToWorld())
            {
                crumble(mount);
            }
            return false;
        }
        ACTIVE.add(rider);

        if (firstTarget != null && isValidTarget(rider, firstTarget))
        {
            rider.setTarget(firstTarget);
        }
        puff(level, body);
        return true;
    }

    /** A random free spot near the owner that the mob (or jockey mount) fits in, or the owner's own spot. */
    private static BlockPos findSpawnPos(ServerPlayer owner, Mob body)
    {
        BlockPos center = owner.blockPosition();
        RandomSource random = owner.getRandom();
        for (int i = 0; i < SPAWN_ATTEMPTS; i++)
        {
            BlockPos pos = center.offset(
                    random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS,
                    random.nextInt(3) - 1,
                    random.nextInt(SPAWN_RADIUS * 2 + 1) - SPAWN_RADIUS);
            if (RisenFollowOwnerGoal.canStandAt(body, pos))
            {
                return pos;
            }
        }
        return center;
    }

    /** Marks the mob as risen and swaps its hostile AI for the risen behavior. */
    private static void setUp(Mob mob, ServerPlayer owner, long now, boolean isMount)
    {
        CompoundTag data = mob.getPersistentData();
        data.putUUID(OWNER_KEY, owner.getUUID());
        data.putLong(SPAWN_TIME_KEY, now);
        if (isMount)
        {
            data.putBoolean(MOUNT_KEY, true);
        }

        mob.setPersistenceRequired(); // vanilla never despawns them early; our own timer removes them
        mob.setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            mob.setDropChance(slot, 0.0F);
        }
        if (mob instanceof Zombie)
        {
            // No "reinforcement" zombies (they'd be normal hostile zombies).
            AttributeInstance reinforcements = mob.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
            if (reinforcements != null)
            {
                reinforcements.setBaseValue(0.0D);
            }
        }

        // Targeting: drop every vanilla target goal (players, villagers, golems, turtles...) and use ours.
        mob.targetSelector.removeAllGoals(goal -> true);
        mob.targetSelector.addGoal(1, new RisenTargetGoal(mob, RisenTargetGoal.Source.OWNER_ATTACKER));
        mob.targetSelector.addGoal(2, new RisenTargetGoal(mob, RisenTargetGoal.Source.OWNER_TARGET));
        mob.targetSelector.addGoal(3, new RisenTargetGoal(mob, RisenTargetGoal.Source.SELF_ATTACKER));

        // Movement: no wandering off on their own; follow the owner instead.
        mob.goalSelector.removeAllGoals(goal -> goal instanceof RandomStrollGoal || goal instanceof MoveThroughVillageGoal);
        if (mob instanceof PathfinderMob pathfinder)
        {
            mob.goalSelector.addGoal(FOLLOW_GOAL_PRIORITY, new RisenFollowOwnerGoal(pathfinder));
        }

        if (mob instanceof Drowned drowned)
        {
            // Vanilla drowned only fight targets standing in water (or at night) and head for water in daylight.
            // Remove their own moving goals (water-only attack, trident attack, go to water, go to beach; all of
            // them use the MOVE flag; the harmless night-time "swim up" goal doesn't) and give them a normal
            // zombie attack, so risen drowned fight on land, day or night.
            mob.goalSelector.removeAllGoals(goal -> goal.getClass().getEnclosingClass() == Drowned.class
                    && goal.getFlags().contains(Goal.Flag.MOVE));
            mob.goalSelector.addGoal(DROWNED_ATTACK_GOAL_PRIORITY, new ZombieAttackGoal(drowned, 1.0D, false));
        }
    }

    /**
     * Gear from Deathforged (see {@link DeathforgedEnchantment}). Skeletons always get a plain bow, because they
     * can't attack without one. Mounts never call this.
     */
    private static void applyDeathforgedGear(Mob mob, int level)
    {
        boolean skeletonType = mob instanceof AbstractSkeleton;
        boolean zombieType = mob instanceof Zombie;
        level = Math.max(0, Math.min(level, DeathforgedEnchantment.MAX_LEVEL));

        int protection = level >= DeathforgedEnchantment.ENCHANT_HIGH_LEVEL ? DeathforgedEnchantment.PROTECTION_HIGH
                : level >= DeathforgedEnchantment.ENCHANT_LOW_LEVEL ? DeathforgedEnchantment.PROTECTION_LOW : 0;
        int sharpness = level >= DeathforgedEnchantment.ENCHANT_HIGH_LEVEL ? DeathforgedEnchantment.SHARPNESS_HIGH
                : level >= DeathforgedEnchantment.ENCHANT_LOW_LEVEL ? DeathforgedEnchantment.SHARPNESS_LOW : 0;
        int power = level >= DeathforgedEnchantment.ENCHANT_HIGH_LEVEL ? DeathforgedEnchantment.POWER_HIGH
                : level >= DeathforgedEnchantment.ENCHANT_LOW_LEVEL ? DeathforgedEnchantment.POWER_LOW : 0;

        if (level > 0)
        {
            Item[] armor = ARMOR_BY_LEVEL[Math.min(level, ARMOR_BY_LEVEL.length) - 1];
            for (int i = 0; i < ARMOR_SLOTS.length; i++)
            {
                ItemStack piece = new ItemStack(armor[i]);
                if (protection > 0)
                {
                    piece.enchant(Enchantments.ALL_DAMAGE_PROTECTION, protection);
                }
                mob.setItemSlot(ARMOR_SLOTS[i], piece);
            }
        }

        if (skeletonType)
        {
            ItemStack bow = new ItemStack(Items.BOW);
            if (power > 0)
            {
                bow.enchant(Enchantments.POWER_ARROWS, power);
            }
            mob.setItemSlot(EquipmentSlot.MAINHAND, bow);
            ((AbstractSkeleton) mob).reassessWeaponGoal(); // switch to the bow attack
        }
        else if (zombieType && level >= DeathforgedEnchantment.SWORD_LEVEL)
        {
            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            if (sharpness > 0)
            {
                sword.enchant(Enchantments.SHARPNESS, sharpness);
            }
            mob.setItemSlot(EquipmentSlot.MAINHAND, sword);
        }

        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            mob.setDropChance(slot, 0.0F);
        }

        if (level >= DeathforgedEnchantment.EFFECTS_LEVEL)
        {
            // Whole life (they crumble long before "infinite" matters). No swirl particles: the glow shows it.
            mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION,
                    DeathforgedEnchantment.STRENGTH_AMPLIFIER, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION,
                    DeathforgedEnchantment.SPEED_AMPLIFIER, false, false));
            Scoreboard scoreboard = mob.level().getScoreboard();
            PlayerTeam team = scoreboard.getPlayerTeam(GLOW_TEAM);
            if (team == null)
            {
                team = scoreboard.addPlayerTeam(GLOW_TEAM);
            }
            team.setColor(GLOW_COLOR);
            scoreboard.addPlayerToTeam(mob.getScoreboardName(), team);
            mob.setGlowingTag(true);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Crumbling
    // ---------------------------------------------------------------------------------------------

    /** Orange puff, then the mob is removed (no death, so no drops or XP). */
    private static void crumble(Mob mob)
    {
        if (mob.level() instanceof ServerLevel level)
        {
            puff(level, mob);
        }
        mob.discard();
    }

    private static void puff(ServerLevel level, Entity entity)
    {
        level.sendParticles(ORANGE_DUST, entity.getX(), entity.getY() + entity.getBbHeight() / 2.0D, entity.getZ(),
                PUFF_PARTICLES, entity.getBbWidth() / 2.0D, entity.getBbHeight() / 3.0D, entity.getBbWidth() / 2.0D, 0.0D);
    }

    /** Crumbles every risen undead (and mount) of this owner, wherever it is. */
    private static void crumbleAllOwnedBy(UUID owner)
    {
        for (Mob mob : new ArrayList<>(ACTIVE))
        {
            if (owner.equals(getOwnerId(mob)))
            {
                crumble(mob);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty())
        {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // Copy: crumbling removes mobs from ACTIVE (EntityLeaveLevelEvent) while we loop.
        List<Mob> mobs = new ArrayList<>(ACTIVE);
        for (Mob mob : mobs)
        {
            if (mob.isRemoved() || !mob.isAlive())
            {
                ACTIVE.remove(mob);
                continue;
            }
            UUID ownerId = getOwnerId(mob);
            ServerPlayer owner = server == null || ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
            long age = mob.level().getGameTime() - mob.getPersistentData().getLong(SPAWN_TIME_KEY);
            if (owner == null || !owner.isAlive() || owner.level() != mob.level() || age >= DESPAWN_TICKS)
            {
                crumble(mob);
                continue;
            }
            // Skeletons in powder snow would turn into strays; vanilla has no Forge event for that one.
            if (mob instanceof Skeleton skeleton && skeleton.isFreezeConverting())
            {
                skeleton.setFreezeConverting(false);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        crumbleAllOwnedBy(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        crumbleAllOwnedBy(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            crumbleAllOwnedBy(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        ACTIVE.clear();
    }

    // ---------------------------------------------------------------------------------------------
    // Joining / leaving the world
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onJoinLevel(EntityJoinLevelEvent event)
    {
        Level level = event.getLevel();
        if (level.isClientSide())
        {
            return;
        }
        Entity entity = event.getEntity();
        if (event.loadedFromDisk() && isRisen(entity))
        {
            // Loaded back from a save: they're temporary, so they crumble instead of coming back.
            Scoreboard scoreboard = level.getScoreboard();
            if (scoreboard.getPlayersTeam(entity.getScoreboardName()) != null)
            {
                scoreboard.removePlayerFromTeam(entity.getScoreboardName());
            }
            if (level instanceof ServerLevel serverLevel)
            {
                puff(serverLevel, entity);
            }
            event.setCanceled(true);
            return;
        }
        // Tag arrows (and other projectiles) shot by risen undead with their owner.
        if (entity instanceof Projectile projectile && !event.loadedFromDisk())
        {
            UUID owner = getOwnerId(projectile.getOwner());
            if (owner != null)
            {
                projectile.getPersistentData().putUUID(PROJECTILE_OWNER_KEY, owner);
            }
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event)
    {
        if (event.getEntity() instanceof Mob mob)
        {
            ACTIVE.remove(mob);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Safety rules
    // ---------------------------------------------------------------------------------------------

    /** Owner of whatever caused this damage, if it was a risen undead or one of their arrows. */
    @Nullable
    private static UUID risenOwnerOf(DamageSource source)
    {
        UUID owner = getOwnerId(source.getEntity());
        if (owner == null)
        {
            owner = getOwnerId(source.getDirectEntity());
        }
        Entity direct = source.getDirectEntity();
        if (owner == null && direct != null && direct.getPersistentData().hasUUID(PROJECTILE_OWNER_KEY))
        {
            owner = direct.getPersistentData().getUUID(PROJECTILE_OWNER_KEY);
        }
        return owner;
    }

    /**
     * Risen undead (and their arrows) never hurt players, villagers, wandering traders, the Dross trader, or their
     * owner's other risen undead. And the owner's own hits (melee, sweep, arrows...) never hurt their risen undead.
     */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide())
        {
            return;
        }
        DamageSource source = event.getSource();

        // The owner hitting their own risen undead: source.getEntity() is the player for melee, sweep and arrows.
        UUID victimOwner = getOwnerId(victim);
        if (victimOwner != null && source.getEntity() instanceof Player player && victimOwner.equals(player.getUUID()))
        {
            event.setCanceled(true);
            return;
        }

        UUID attackerOwner = risenOwnerOf(source);
        if (attackerOwner == null)
        {
            return;
        }
        if (isProtected(victim) || attackerOwner.equals(victimOwner))
        {
            event.setCanceled(true);
        }
    }

    /**
     * When a risen undead (or its arrow) damages a mob, that mob turns on the risen undead that hit it, instead of
     * staying fixed on the player. Vanilla would often keep the old target (a target goal that's already running
     * wins, and undead mostly ignore each other), so we set it directly and make it stick:
     * <ul>
     *   <li>goal-based mobs (zombies, skeletons, spiders, creepers...): {@code setTarget} + last attacker. A running
     *       target goal keeps whatever {@code getTarget()} is, so it keeps chasing the risen undead until it dies;</li>
     *   <li>"neutral" mobs (zombified piglins, endermen, iron golems...): their anger is pointed at it too;</li>
     *   <li>brain-based mobs (piglins, hoglins...): their attack-target and anger memories are set.</li>
     * </ul>
     * Latest hit wins: if the player hits the mob afterwards, normal vanilla retaliation takes over.
     * Runs at LOWEST priority and only if no one cancelled the damage, so it only happens when the hit landed.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event)
    {
        if (!(event.getEntity() instanceof Mob victim) || victim.level().isClientSide() || !victim.isAlive())
        {
            return;
        }
        // source.getEntity() is the shooter for arrows, so those count too.
        if (!(event.getSource().getEntity() instanceof Mob risen) || !isRisen(risen) || !risen.isAlive())
        {
            return;
        }
        if (isProtected(victim) || victim.isAlliedTo(risen))
        {
            return; // villagers/traders (never damaged anyway) and the shared Deathforged X glow team
        }
        UUID attackerOwner = getOwnerId(risen);
        if (attackerOwner != null && attackerOwner.equals(getOwnerId(victim)))
        {
            return; // same owner's risen undead can't hurt each other anyway
        }

        victim.setLastHurtByMob(risen);
        victim.setTarget(risen);
        if (victim instanceof NeutralMob neutral)
        {
            neutral.setPersistentAngerTarget(risen.getUUID());
            neutral.startPersistentAngerTimer();
        }
        Brain<?> brain = victim.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED))
        {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, risen);
            if (brain.checkMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED))
            {
                brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            }
        }
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED))
        {
            // Piglins drop any attack target that isn't their "angry at" mob or nearest valid target.
            brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, risen.getUUID(), BRAIN_ANGER_TICKS);
        }
    }

    /** Last line of defence: blocks any target change (from any source) to a forbidden target. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event)
    {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide() && isRisen(mob))
        {
            LivingEntity newTarget = event.getNewTarget();
            if (newTarget != null && !isValidTarget(mob, newTarget))
            {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event)
    {
        if (isRisen(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExperience(LivingExperienceDropEvent event)
    {
        if (isRisen(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event)
    {
        if (isRisen(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    /** No zombie -> drowned, husk -> zombie (skeleton -> stray is handled in the tick above). */
    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Pre event)
    {
        if (isRisen(event.getEntity()))
        {
            event.setCanceled(true);
            event.setConversionTimer(DESPAWN_TICKS); // don't re-check every tick
        }
    }
}
