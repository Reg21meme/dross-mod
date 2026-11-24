package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModParticles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * THE CATHEDRAL'S SIEGE (PRE_DROSS_PLAN.md, stage T2; the user: "a lot of knights around the area, and there's some
 * spell protecting it ... you have to defeat all 5 to enter the cathedral", and "make it down for everyone in the
 * world"). Owned by world-builder; the knights themselves are the boss area's {@link ElementalKnight}.
 * <ul>
 *   <li><b>Its guards:</b> {@value #GUARDS} elemental knights, once per world, each a different one of the ten kinds,
 *       never more than {@value #MAX_PER_ELEMENT} of one element, picked from the world's seed ({@link #plan}). They come
 *       when a player (not a spectator) is within {@value #COME_RANGE} blocks of the cathedral, its land is loaded and its
 *       tidy-up is done ({@link SiteTidy}; this never loads a chunk), each on its own spot round it, spread round it by
 *       angle, {@value #GUARD_OUT} blocks or so outside its footprint on solid dry ground ({@link #findHome}, picked once
 *       and saved). Each keeps near its own spot with the ruins' guard behaviour ({@link ElementalKnight#guard}): it
 *       wanders there, fights players who come near it and walks back. They never despawn. Not on Peaceful. One that
 *       leaves without dying (Peaceful, taken away) comes back the next time; a dead one never does.</li>
 *   <li><b>Its barrier:</b> while the siege holds, the cathedral is warded ({@link CathedralWard}): players in survival or
 *       adventure who walk in are pushed back out, and see the locked lands' curtain along its edge.</li>
 *   <li><b>The fall:</b> when the last of the five dies the siege is broken, for good and for everyone in the world
 *       (saved: {@link CathedralSiegeData}). Every player within {@value #CREDIT_RANGE} blocks of the cathedral sees its
 *       barrier fall and is told so, and they and the player credited with that last kill get "Siege Broken"
 *       ({@code dross:siege_broken}; not when it's removed with {@code /kill}, as with the bosses). Each knight's fall
 *       before it is counted over their hotbar ("The cathedral's knights fallen: 2 / 5").</li>
 *   <li><b>Its frame:</b> while the siege holds, a Portal Orb thrown into the cathedral's own frame doesn't light it: it
 *       pops back out with a line ({@link #refusesOrb}, called by the portal area's {@code DrossPortalActivation}).</li>
 * </ul>
 * Like the knight ruins' keeper ({@code boss/knight/ruin/KnightRuinKeeper}), the keeper and the ward stand down on the
 * GameTest server (whose other tests' stand-in players visit the cathedral); its tests drive them themselves.
 * Test tools: {@code /dross siege status|reset|break} ({@code command/SiegeCommands}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class CathedralSiege
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ------------------------------------------------------------------ Tuning

    /** How many knights guard the cathedral. */
    public static final int GUARDS = 5;
    /** Never more than this many of one element among them. */
    public static final int MAX_PER_ELEMENT = 2;
    /** They come when a player (not a spectator) is within this many blocks of the cathedral's footprint. */
    public static final int COME_RANGE = 160;
    /** The keeper looks every this many ticks (2 s). */
    public static final int KEEPER_EVERY = 40;
    /** Each knight's spot: about this far outside the footprint's edge (blocks), along its own direction from the middle. */
    public static final int GUARD_OUT = 12;
    /** ...or, where that's no good (water, a cliff, a tree), these distances out (blocks past the edge), best first... */
    private static final int[] GUARD_OUTS = {GUARD_OUT, 16, 9, 20, 24, 7, 30};
    /** ...and these turns either way off its direction (degrees), best first. */
    private static final int[] GUARD_TURNS = {0, 8, -8, 16, -16, 24, -24, 32, -32};
    /** A spot at least this far from the footprint's edge (blocks): never on the cathedral itself. */
    public static final int GUARD_MIN_GAP = 5;
    /** Two knights' spots at least this far apart (blocks): scattered, never together. */
    public static final int GUARD_APART = 24;
    /** A spot's ground at most this far above or below the cathedral's ground at first (blocks; then anywhere). */
    public static final int GUARD_MAX_RISE = 12;
    /** Their directions round the cathedral are evenly spaced from a start the seed picks, each turned by up to this. */
    public static final double ANGLE_JITTER_DEGREES = 10.0D;
    /** Mixed into the world's seed for their plan, so it isn't the same as anything else picked from it. */
    private static final long PLAN_SALT = 0x5EE6E_CA7EDL;
    /** The players within this many blocks of the cathedral's footprint see its barrier fall and get "Siege Broken"
     * (with the player credited with the last kill, wherever they are), and are told each knight's fall. */
    public static final int CREDIT_RANGE = 64;
    /** A refused Portal Orb: pushed this far out of the frame's plane (blocks)... */
    private static final double ORB_POP_DISTANCE = 1.1D;
    /** ...this fast out (blocks a tick)... */
    private static final double ORB_POP_SPEED = 0.3D;
    /** ...and this fast up. */
    private static final double ORB_POP_UP = 0.3D;
    /** Its thrower (or, with none, the players this near) is told why at most every this many ticks for one orb. */
    private static final int ORB_LINE_EVERY = 40;
    private static final double ORB_LINE_RANGE = 8.0D;
    /** The runes flaring across the frame's opening as it refuses an orb. */
    private static final int ORB_RUNES = 18;
    private static final int ORB_STREAKS = 6;

    /** A siege knight's "ruin" (its {@link ElementalKnight#guard} id): this and the siege's generation. */
    public static final String KNIGHT_ID_PREFIX = "dross:cathedral_siege#";

    // ------------------------------------------------------------------ Lang keys

    /** To the players near when the last knight falls (and on {@code /dross siege break}). */
    public static final String KEY_BROKEN = "dross.siege.broken";
    /** A knight fell: how many of the five have (over the hotbar). */
    public static final String KEY_PROGRESS = "dross.siege.progress";
    /** The frame refusing a Portal Orb. */
    public static final String KEY_FRAME_SHUT = "dross.siege.frame_shut";
    /** The advancement (Quest's helper; its JSON {@code data/dross/advancements/siege_broken.json}). */
    public static final String ADVANCEMENT = DrossAdvancements.SIEGE_BROKEN;

    /** The cathedral's structure start is in its chunk (null: not known yet). Server thread; forgotten when it stops. */
    @Nullable
    private static Boolean built;
    /** When each refused orb last made its line. */
    private static final Map<ItemEntity, Long> ORB_LINES = new WeakHashMap<>();

    private CathedralSiege() {}

    // ------------------------------------------------------------------ The plan (from the seed)

    /** One of the cathedral's guards: its kind, and its direction from the cathedral's middle (radians, from +X towards +Z). */
    public record Guard(KnightKind kind, double angle) {}

    /**
     * The five guards from the world's seed: different kinds (a seeded shuffle of the ten), never more than
     * {@value #MAX_PER_ELEMENT} of one element (with two kinds an element, five different ones never break it; the check
     * keeps it so if kinds are added), their directions evenly spread from a seeded start, each turned a little.
     */
    public static List<Guard> plan(long seed)
    {
        Random random = new Random(seed ^ PLAN_SALT);
        List<KnightKind> kinds = new ArrayList<>(List.of(KnightKind.values()));
        Collections.shuffle(kinds, random);
        List<KnightKind> picked = new ArrayList<>();
        Map<KnightKind.Element, Integer> perElement = new EnumMap<>(KnightKind.Element.class);
        for (KnightKind kind : kinds)
        {
            if (picked.size() < GUARDS && perElement.getOrDefault(kind.element, 0) < MAX_PER_ELEMENT)
            {
                picked.add(kind);
                perElement.merge(kind.element, 1, Integer::sum);
            }
        }
        for (int i = 0; picked.size() < GUARDS; i++)
        {
            picked.add(kinds.get(i % kinds.size())); // (never: ten kinds, two of each element)
        }
        double start = random.nextDouble() * Math.PI * 2.0D;
        List<Guard> guards = new ArrayList<>();
        for (int slot = 0; slot < GUARDS; slot++)
        {
            double jitter = Math.toRadians((random.nextDouble() * 2.0D - 1.0D) * ANGLE_JITTER_DEGREES);
            guards.add(new Guard(picked.get(slot), start + slot * Math.PI * 2.0D / GUARDS + jitter));
        }
        return List.copyOf(guards);
    }

    // ------------------------------------------------------------------ Its state

    /** True while the siege holds: the world has a cathedral and its knights haven't all fallen. Server thread. */
    public static boolean holds(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        return overworld != null && SiteFinder.available(overworld) && !CathedralSiegeData.get(overworld).broken();
    }

    /** True once the siege is broken (for good, for everyone). Server thread. */
    public static boolean isBroken(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        return overworld != null && CathedralSiegeData.get(overworld).broken();
    }

    /**
     * True if the cathedral was really made with the land: its chunk (loaded: this never loads one) holds the
     * {@code dross:portal_site} start. False while its chunk isn't loaded (no player can be at it then). Remembered once
     * known.
     */
    static boolean cathedralBuilt(ServerLevel overworld, SiteSpot spot)
    {
        if (built != null)
        {
            return built;
        }
        LevelChunk chunk = overworld.getChunkSource().getChunkNow(spot.startChunk().x, spot.startChunk().z);
        if (chunk == null)
        {
            return false;
        }
        Structure site = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(new ResourceLocation(Dross.MODID, "portal_site"));
        built = site != null && chunk.getStartForStructure(site) != null;
        if (!built)
        {
            LOGGER.warn("Dross portal site: its chunk {} has no dross:portal_site start (its land was made before the site came "
                    + "with the land), so there's no siege", spot.startChunk());
        }
        return built;
    }

    /** How far (flat blocks) this point is from the footprint (0 inside it). */
    static double fromFootprint(BoundingBox foot, double x, double z)
    {
        double dx = Math.max(0.0D, Math.max(foot.minX() - x, x - (foot.maxX() + 1)));
        double dz = Math.max(0.0D, Math.max(foot.minZ() - z, z - (foot.maxZ() + 1)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The nearest player (not a spectator) to the footprint, flat blocks; {@link Double#MAX_VALUE} with none. */
    static double nearestPlayer(ServerLevel overworld, BoundingBox foot)
    {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : overworld.players())
        {
            if (!p.isSpectator())
            {
                best = Math.min(best, fromFootprint(foot, p.getX(), p.getZ()));
            }
        }
        return best;
    }

    /** The players (not spectators) within {@value #CREDIT_RANGE} blocks of the footprint. */
    static Set<ServerPlayer> playersNear(ServerLevel overworld, BoundingBox foot)
    {
        Set<ServerPlayer> near = new LinkedHashSet<>();
        for (ServerPlayer p : overworld.players())
        {
            if (!p.isSpectator() && fromFootprint(foot, p.getX(), p.getZ()) <= CREDIT_RANGE)
            {
                near.add(p);
            }
        }
        return near;
    }

    /** True if this knight is one of the cathedral's (of any generation). */
    public static boolean isSiegeKnight(ElementalKnight knight)
    {
        return knight.ruin() != null && knight.ruin().startsWith(KNIGHT_ID_PREFIX);
    }

    /** This generation's id for its knights. */
    static String knightId(CathedralSiegeData data)
    {
        return KNIGHT_ID_PREFIX + data.generation();
    }

    // ------------------------------------------------------------------ The keeper

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % KEEPER_EVERY != 0 || level.players().isEmpty())
        {
            return;
        }
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
        {
            // (the test server: its other tests' stand-in players visit the cathedral; the siege's own tests call keep()
            // themselves. Not ForgeGameTestHooks.isGametestEnabled(): that's true in the dev game too.)
            return;
        }
        try
        {
            keep(level);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("The cathedral's siege: looking after its knights failed", e);
        }
    }

    /**
     * Looks after the siege: its knights come (or come again) if they should. Never loads a chunk. Server thread, the
     * Overworld.
     */
    static void keep(ServerLevel overworld)
    {
        SiteSpot spot = SiteFinder.spotIfReady(overworld);
        if (spot == null)
        {
            return;
        }
        CathedralSiegeData data = CathedralSiegeData.get(overworld);
        BoundingBox foot = spot.footprint();
        if (data.broken() || nearestPlayer(overworld, foot) > COME_RANGE || !cathedralBuilt(overworld, spot)
                || !PortalSiteData.get(overworld).isTidied(spot.frame().corner()) || overworld.getDifficulty() == Difficulty.PEACEFUL)
        {
            return;
        }
        List<Guard> plan = plan(overworld.getSeed());
        for (int slot = 0; slot < GUARDS; slot++)
        {
            if (data.fallen(slot))
            {
                continue;
            }
            Guard guard = plan.get(slot);
            BlockPos home = data.home(slot);
            UUID known = data.knight(slot);
            if (known != null)
            {
                Entity there = overworld.getEntity(known);
                // (still there; or perhaps in a chunk that isn't loaded: only if all of its ground is loaded is it gone)
                if (there != null && there.isAlive() || home != null && !groundLoaded(overworld, home))
                {
                    continue;
                }
            }
            if (home == null)
            {
                List<BlockPos> others = new ArrayList<>();
                for (int other = 0; other < GUARDS; other++)
                {
                    if (other != slot && data.home(other) != null)
                    {
                        others.add(data.home(other));
                    }
                }
                home = findHome(overworld, spot, guard, others);
                if (home == null)
                {
                    continue;
                }
                data.home(slot, home);
            }
            if (overworld.isPositionEntityTicking(home))
            {
                spawn(overworld, data, spot, slot, guard.kind(), home);
            }
        }
    }

    /** True if all the ground a knight keeps to round its spot is loaded with its entities (so a missing one is gone). */
    private static boolean groundLoaded(ServerLevel overworld, BlockPos home)
    {
        int r = (int) com.reg21meme.dross.boss.knight.ruin.KnightRuinTuning.LEASH;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (!overworld.isPositionEntityTicking(home.offset(dx * r, 0, dz * r)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A knight's spot round the cathedral, once all the ground it might be on is loaded (so it's the same whichever way a
     * player comes; null until then): along its direction from the footprint's middle, {@link #GUARD_OUTS} blocks past the
     * footprint's edge, turned by {@link #GUARD_TURNS}, the first column at least {@value #GUARD_APART} blocks from the
     * other knights' spots whose ground is solid (not a log, leaves or a fluid), with room for a knight standing there and
     * no liquid, and near the cathedral's ground level ({@value #GUARD_MAX_RISE} blocks; then anywhere); with none, its
     * own column as it is.
     */
    @Nullable
    static BlockPos findHome(ServerLevel overworld, SiteSpot spot, Guard guard, List<BlockPos> others)
    {
        BoundingBox foot = spot.footprint();
        for (int turn : GUARD_TURNS)
        {
            for (int out : GUARD_OUTS)
            {
                if (!overworld.isPositionEntityTicking(column(foot, guard.angle() + Math.toRadians(turn), out).atY(spot.groundY())))
                {
                    return null;
                }
            }
        }
        EntityType<ElementalKnight> type = ModEntities.KNIGHTS.get(guard.kind()).get();
        for (boolean strict : new boolean[] {true, false})
        {
            for (int turn : GUARD_TURNS)
            {
                for (int out : GUARD_OUTS)
                {
                    BlockPos column = column(foot, guard.angle() + Math.toRadians(turn), out);
                    if (others.stream().anyMatch(o -> Math.hypot(o.getX() - column.getX(), o.getZ() - column.getZ()) < GUARD_APART))
                    {
                        continue;
                    }
                    BlockPos feet = standingAt(overworld, spot, foot, type, column, strict);
                    if (feet != null)
                    {
                        return feet;
                    }
                }
            }
        }
        BlockPos column = column(foot, guard.angle(), GUARD_OUT);
        return column.atY(overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()));
    }

    /** The column {@code out} blocks past the footprint's edge along this direction from its middle (Y 0). */
    static BlockPos column(BoundingBox foot, double angle, int out)
    {
        double cx = (foot.minX() + foot.maxX() + 1) / 2.0D;
        double cz = (foot.minZ() + foot.maxZ() + 1) / 2.0D;
        double dx = Math.cos(angle);
        double dz = Math.sin(angle);
        double hx = foot.getXSpan() / 2.0D;
        double hz = foot.getZSpan() / 2.0D;
        double edge = Math.min(Math.abs(dx) < 1.0E-6D ? Double.MAX_VALUE : hx / Math.abs(dx),
                Math.abs(dz) < 1.0E-6D ? Double.MAX_VALUE : hz / Math.abs(dz));
        double t = edge + out;
        return BlockPos.containing(cx + dx * t, 0.0D, cz + dz * t);
    }

    /** Where a knight would stand on this column (its feet), or null if it's no good (see {@link #findHome}). */
    @Nullable
    private static BlockPos standingAt(ServerLevel overworld, SiteSpot spot, BoundingBox foot, EntityType<ElementalKnight> type,
                                       BlockPos column, boolean strict)
    {
        if (!overworld.isPositionEntityTicking(column.atY(spot.groundY())) || fromFootprint(foot, column.getX() + 0.5D,
                column.getZ() + 0.5D) < GUARD_MIN_GAP)
        {
            return null;
        }
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        if (strict && Math.abs(y - (spot.groundY() + 1)) > GUARD_MAX_RISE)
        {
            return null;
        }
        BlockPos feet = column.atY(y);
        BlockPos ground = feet.below();
        BlockState state = overworld.getBlockState(ground);
        if (!state.getFluidState().isEmpty() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
                || !state.isFaceSturdy(overworld, ground, Direction.UP))
        {
            return null;
        }
        AABB box = type.getAABB(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        return overworld.noCollision(box) && !overworld.containsAnyLiquid(box) ? feet : null;
    }

    /** A knight comes to its spot, facing out from the cathedral, guarding it. */
    @Nullable
    private static ElementalKnight spawn(ServerLevel overworld, CathedralSiegeData data, SiteSpot spot, int slot, KnightKind kind,
                                         BlockPos home)
    {
        ElementalKnight knight = ModEntities.KNIGHTS.get(kind).get().create(overworld);
        if (knight == null)
        {
            return null;
        }
        BoundingBox foot = spot.footprint();
        double cx = (foot.minX() + foot.maxX() + 1) / 2.0D;
        double cz = (foot.minZ() + foot.maxZ() + 1) / 2.0D;
        // Minecraft's yaw: 0 faces +Z (south), 90 faces -X (west)
        float yaw = (float) Math.toDegrees(Math.atan2(-(home.getX() + 0.5D - cx), home.getZ() + 0.5D - cz));
        knight.moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, yaw, 0.0F);
        knight.setYBodyRot(yaw);
        knight.setYHeadRot(yaw);
        knight.guard(knightId(data), home, slot);
        knight.finalizeSpawn(overworld, overworld.getCurrentDifficultyAt(home), MobSpawnType.STRUCTURE, null, null);
        overworld.addFreshEntity(knight);
        data.spawned(slot, knight.getUUID());
        LOGGER.info("The cathedral's siege: its knight {} ({}) comes to {}", slot + 1, kind, home.toShortString());
        return knight;
    }

    // ------------------------------------------------------------------ Its knights' deaths, comings and goings

    /** The slot this knight holds now, or -1 if it isn't one of this generation's (a stray copy, an older one). */
    private static int slotOf(ElementalKnight knight, CathedralSiegeData data)
    {
        int slot = knight.ruinSlot();
        if (!knightId(data).equals(knight.ruin()) || slot < 0 || slot >= GUARDS)
        {
            return -1;
        }
        UUID known = data.knight(slot);
        return known == null || known.equals(knight.getUUID()) ? slot : -1;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ElementalKnight knight) || !isSiegeKnight(knight)
                || !(knight.level() instanceof ServerLevel overworld) || overworld.dimension() != Level.OVERWORLD)
        {
            return;
        }
        CathedralSiegeData data = CathedralSiegeData.get(overworld);
        int slot = slotOf(knight, data);
        if (slot < 0 || data.broken() || data.fallen(slot))
        {
            return;
        }
        data.fell(slot);
        SiteSpot spot = SiteFinder.spotIfReady(overworld);
        if (spot == null)
        {
            return;
        }
        // (/kill credits nobody and gives no advancement, as with the bosses; the siege still breaks)
        boolean killCommand = event.getSource().is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        ServerPlayer credited = killCommand ? null : credited(event.getSource().getEntity(), knight);
        Set<ServerPlayer> told = new LinkedHashSet<>();
        if (credited != null)
        {
            told.add(credited);
        }
        told.addAll(playersNear(overworld, spot.footprint()));
        LOGGER.info("The cathedral's siege: its knight {} ({}) falls ({} of {}){}", slot + 1, knight.kind(), data.fallenCount(), GUARDS,
                credited != null ? " to " + credited.getGameProfile().getName() : "");
        if (data.allFallen())
        {
            breakSiege(overworld, spot, credited, told, !killCommand, credited != null ? credited.getGameProfile().getName()
                    : killCommand ? "/kill" : "its last knight's death");
            return;
        }
        Component progress = Component.translatable(KEY_PROGRESS, data.fallenCount(), GUARDS).withStyle(s -> s.withColor(DrossColors.SIEGE_PROGRESS_TEXT));
        told.forEach(p -> p.displayClientMessage(progress, true));
    }

    /** The player credited with a kill: the one who dealt it, the owner of the pet that did, or the last player to hurt it. */
    @Nullable
    private static ServerPlayer credited(@Nullable Entity killer, LivingEntity victim)
    {
        if (killer instanceof ServerPlayer player)
        {
            return player;
        }
        if (killer instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner)
        {
            return owner;
        }
        return victim.getKillCredit() instanceof ServerPlayer last ? last : null;
    }

    /**
     * A copy of a siege knight that isn't its knight now (an older one, after it came again or a reset) never joins; one
     * whose place was empty takes it again (so none comes in its stead).
     */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (event.loadedFromDisk() && event.getEntity() instanceof ElementalKnight knight && isSiegeKnight(knight)
                && event.getLevel() instanceof ServerLevel overworld && overworld.dimension() == Level.OVERWORLD)
        {
            CathedralSiegeData data = CathedralSiegeData.get(overworld);
            int slot = slotOf(knight, data);
            if (slot < 0 || data.broken() || data.fallen(slot))
            {
                event.setCanceled(true);
            }
            else if (data.knight(slot) == null)
            {
                data.spawned(slot, knight.getUUID());
            }
        }
    }

    /** A knight that left without dying (Peaceful, taken away) leaves its place empty: it comes again. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event)
    {
        if (!(event.getEntity() instanceof ElementalKnight knight) || !isSiegeKnight(knight)
                || !(event.getLevel() instanceof ServerLevel overworld) || overworld.dimension() != Level.OVERWORLD)
        {
            return;
        }
        Entity.RemovalReason reason = knight.getRemovalReason();
        if (reason == null || reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER)
        {
            return;
        }
        CathedralSiegeData data = CathedralSiegeData.get(overworld);
        int slot = slotOf(knight, data);
        if (slot >= 0 && knight.getUUID().equals(data.knight(slot)) && !data.fallen(slot))
        {
            data.lost(slot);
        }
    }

    // ------------------------------------------------------------------ The fall

    /**
     * The siege breaks, for good and for everyone: saved; the knights of this generation still about go; every player
     * {@code told} is told and sees the barrier fall ({@link CathedralWard#fall}), and gets "Siege Broken" if
     * {@code grant}.
     */
    static void breakSiege(ServerLevel overworld, SiteSpot spot, @Nullable ServerPlayer credited, Collection<ServerPlayer> told,
                           boolean grant, String by)
    {
        CathedralSiegeData data = CathedralSiegeData.get(overworld);
        data.markBroken(overworld.getGameTime(), by);
        int removed = removeKnights(overworld, k -> true);
        LOGGER.info("The cathedral's siege is broken ({}): its barrier is down for everyone{}; told {}", by,
                removed > 0 ? " (" + removed + " of its knights still about went)" : "",
                told.stream().map(p -> p.getGameProfile().getName()).toList());
        Component line = Component.translatable(KEY_BROKEN).withStyle(s -> s.withColor(DrossColors.SIEGE_BROKEN_TEXT));
        for (ServerPlayer p : told)
        {
            p.sendSystemMessage(line);
            if (grant)
            {
                DrossAdvancements.grant(p, ADVANCEMENT);
            }
        }
        CathedralWard.fall(overworld, spot, told);
    }

    /** Removes the siege's knights that are about now (loaded) and match: returns how many. */
    private static int removeKnights(ServerLevel overworld, java.util.function.Predicate<ElementalKnight> which)
    {
        List<? extends ElementalKnight> about = overworld.getEntities(EntityTypeTest.forClass(ElementalKnight.class),
                k -> isSiegeKnight(k) && k.isAlive() && which.test(k));
        for (ElementalKnight k : List.copyOf(about))
        {
            overworld.sendParticles(ModParticles.WARD_RUNE.get(), k.getX(), k.getY() + 2.0D, k.getZ(), 12, 0.6D, 1.2D, 0.6D, 0.0D);
            k.discard();
        }
        return about.size();
    }

    // ------------------------------------------------------------------ The frame, shut while it holds

    /**
     * Called by the portal area's {@code DrossPortalActivation} when a Portal Orb lands in the empty opening of a whole
     * frame, before lighting it. If that frame is the cathedral's own and the siege holds, the orb isn't used: it pops
     * back out the way it came, runes flare across the opening with a chime, and its thrower (or the players near) is told
     * {@value #KEY_FRAME_SHUT}. Returns true then (the frame mustn't light).
     */
    public static boolean refusesOrb(ServerLevel level, ItemEntity orb, BlockPos openingMin, Direction.Axis axis, int width, int height)
    {
        if (level.dimension() != Level.OVERWORLD)
        {
            return false;
        }
        SiteSpot spot = SiteFinder.spotIfReady(level);
        if (spot == null || CathedralSiegeData.get(level).broken() || !isCathedralOpening(spot, openingMin, axis, width, height)
                || !cathedralBuilt(level, spot))
        {
            return false;
        }
        popOut(level, orb, openingMin, axis);
        double cx = axis == Direction.Axis.X ? openingMin.getX() + width / 2.0D : openingMin.getX() + 0.5D;
        double cz = axis == Direction.Axis.Z ? openingMin.getZ() + width / 2.0D : openingMin.getZ() + 0.5D;
        double cy = openingMin.getY() + height / 2.0D;
        double spreadX = axis == Direction.Axis.X ? width / 3.0D : 0.15D;
        double spreadZ = axis == Direction.Axis.Z ? width / 3.0D : 0.15D;
        level.sendParticles(ModParticles.WARD_RUNE.get(), cx, cy, cz, ORB_RUNES, spreadX, height / 3.0D, spreadZ, 0.0D);
        level.sendParticles(ModParticles.WARD_STREAK.get(), cx, openingMin.getY(), cz, ORB_STREAKS, spreadX, 0.05D, spreadZ, 0.0D);
        long now = level.getGameTime();
        Long last = ORB_LINES.get(orb);
        if (last == null || now - last >= ORB_LINE_EVERY)
        {
            ORB_LINES.put(orb, now);
            level.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 1.2F);
            Component line = Component.translatable(KEY_FRAME_SHUT).withStyle(s -> s.withColor(DrossColors.SIEGE_WARD_TEXT));
            if (orb.getOwner() instanceof ServerPlayer thrower)
            {
                thrower.sendSystemMessage(line);
            }
            else
            {
                for (ServerPlayer p : level.players())
                {
                    if (p.distanceToSqr(cx, cy, cz) <= ORB_LINE_RANGE * ORB_LINE_RANGE)
                    {
                        p.sendSystemMessage(line);
                    }
                }
            }
        }
        return true;
    }

    /** True if this opening holds the cathedral's opening's middle (it's the cathedral's own frame). */
    static boolean isCathedralOpening(SiteSpot spot, BlockPos openingMin, Direction.Axis axis, int width, int height)
    {
        BlockPos center = spot.frame().openingCenter();
        boolean alongX = axis == Direction.Axis.X;
        int along = alongX ? center.getX() - openingMin.getX() : center.getZ() - openingMin.getZ();
        int across = alongX ? center.getZ() - openingMin.getZ() : center.getX() - openingMin.getX();
        int up = center.getY() - openingMin.getY();
        return across == 0 && along >= 0 && along < width && up >= 0 && up < height;
    }

    /** Pushes the orb out of the frame's plane, back the way it came (the side it's on, else out the front), and up. */
    private static void popOut(ServerLevel level, ItemEntity orb, BlockPos openingMin, Direction.Axis axis)
    {
        boolean acrossZ = axis == Direction.Axis.X; // (an X frame is walked through along Z)
        double plane = (acrossZ ? openingMin.getZ() : openingMin.getX()) + 0.5D;
        double offset = (acrossZ ? orb.getZ() : orb.getX()) - plane;
        double motion = acrossZ ? orb.getDeltaMovement().z : orb.getDeltaMovement().x;
        int side = Math.abs(motion) > 0.01D ? (motion > 0.0D ? -1 : 1) : Math.abs(offset) > 0.05D ? (offset > 0.0D ? 1 : -1) : 1;
        for (int trySide : new int[] {side, -side})
        {
            double nx = acrossZ ? orb.getX() : plane + trySide * ORB_POP_DISTANCE;
            double nz = acrossZ ? plane + trySide * ORB_POP_DISTANCE : orb.getZ();
            AABB moved = orb.getBoundingBox().move(nx - orb.getX(), 0.0D, nz - orb.getZ());
            if (level.noCollision(orb, moved))
            {
                orb.setPos(nx, orb.getY(), nz);
                orb.setDeltaMovement(acrossZ ? 0.0D : trySide * ORB_POP_SPEED, ORB_POP_UP, acrossZ ? trySide * ORB_POP_SPEED : 0.0D);
                orb.hasImpulse = true;
                return;
            }
        }
        orb.setDeltaMovement(orb.getDeltaMovement().x, ORB_POP_UP, orb.getDeltaMovement().z);
        orb.hasImpulse = true;
    }

    // ------------------------------------------------------------------ The test tools (command/SiegeCommands)

    /** What {@code /dross siege status} says: its state, and each knight's. */
    public static List<Component> status(MinecraftServer server)
    {
        List<Component> lines = new ArrayList<>();
        ServerLevel overworld = server.overworld();
        SiteSpot spot = overworld == null || !SiteFinder.available(overworld) ? null : SiteFinder.spotIfReady(overworld);
        if (spot == null)
        {
            lines.add(Component.literal("This world has no Fallen Cathedral (no portal site), so no siege."));
            return lines;
        }
        CathedralSiegeData data = CathedralSiegeData.get(overworld);
        BoundingBox foot = spot.footprint();
        if (data.broken())
        {
            long ago = overworld.getGameTime() - data.brokenAt();
            lines.add(Component.literal("The Fallen Cathedral's siege is broken (" + data.brokenBy() + ", " + ago / 20L
                    + " s ago): its barrier is down for everyone."));
        }
        else
        {
            lines.add(Component.literal("The Fallen Cathedral's siege holds: " + data.fallenCount() + " of " + GUARDS
                    + " knights fallen; its barrier keeps players out (not in creative or spectator mode)."));
        }
        lines.add(Component.literal("Its footprint: X " + foot.minX() + ".." + foot.maxX() + ", Z " + foot.minZ() + ".." + foot.maxZ()
                + ", ground level y " + spot.groundY() + ". Its knights come with a player within " + COME_RANGE
                + " blocks once its land is loaded and tidied (never on Peaceful)" + (cathedralBuilt(overworld, spot) ? "."
                : "; its chunk isn't loaded now, or it has no cathedral.")));
        List<Guard> plan = plan(overworld.getSeed());
        for (int slot = 0; slot < GUARDS; slot++)
        {
            Guard guard = plan.get(slot);
            String state;
            UUID known = data.knight(slot);
            Entity there = known == null ? null : overworld.getEntity(known);
            BlockPos home = data.home(slot);
            if (data.fallen(slot))
            {
                state = "fallen";
            }
            else if (there != null && there.isAlive())
            {
                state = "on guard at " + there.blockPosition().toShortString() + " (its spot " + (home == null ? "?" : home.toShortString()) + ")";
            }
            else if (known != null)
            {
                state = "came, not loaded now (its spot " + (home == null ? "?" : home.toShortString()) + ")";
            }
            else
            {
                state = "not come yet" + (home == null ? "" : " (its spot " + home.toShortString() + ")");
            }
            final String s = state;
            lines.add(Component.literal("  " + (slot + 1) + ". ").append(ModEntities.KNIGHTS.get(guard.kind()).get().getDescription())
                    .append(Component.literal(" (" + guard.kind().element.name().toLowerCase(java.util.Locale.ROOT) + "): " + s)));
        }
        return lines;
    }

    /**
     * {@code /dross siege reset}: the siege starts again (a new generation): unbroken, its knights about now go, and they
     * come back the next time a player is near (their spots picked again). Returns how many knights went; -1 with no site.
     */
    public static int reset(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        if (overworld == null || !SiteFinder.available(overworld))
        {
            return -1;
        }
        CathedralSiegeData.get(overworld).reset();
        int removed = removeKnights(overworld, k -> true);
        LOGGER.info("The cathedral's siege is reset ({} of its knights about went); they come back with a player near", removed);
        return removed;
    }

    /**
     * {@code /dross siege break}: breaks it at once (no advancement: nobody brought its knights down), its knights about
     * now going; the players near see the barrier fall. False if there's no site or it's already broken.
     */
    public static boolean breakNow(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        SiteSpot spot = overworld == null || !SiteFinder.available(overworld) ? null : SiteFinder.spotIfReady(overworld);
        if (spot == null || CathedralSiegeData.get(overworld).broken())
        {
            return false;
        }
        breakSiege(overworld, spot, null, playersNear(overworld, spot.footprint()), false, "/dross siege break");
        return true;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        built = null;
        ORB_LINES.clear();
    }
}
