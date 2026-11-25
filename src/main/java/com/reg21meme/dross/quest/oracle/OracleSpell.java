package com.reg21meme.dross.quest.oracle;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.dimension.sites.SiteUse;
import com.reg21meme.dross.villager.Survivor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * THE ORACLE'S SPELL (server side; QUEST_PLAN.md, "The Oracle's spell"). When a player hands in a quest that opens a
 * land or a boss's home ({@link OracleQuests#castsSpell}), the Blind Oracle breaks that barrier: he stands still
 * ({@link Survivor#holdStill}) while everyone who can see him watches the spell ({@link OracleSpellMessage}, drawn by
 * {@code quest/client/OracleSpellEffects}): his arms go up, a rune circle opens on the ground round him, a beam bursts
 * up into the sky, bends out towards the barrier and strikes it far off with a flash, a shock ring and a boom, then
 * shatters into falling sparks.
 *
 * <p>The opening itself is the quest rule's ({@link OracleRules}): it happens at the hand-in, whether anyone watches or
 * not. This only finds which way the barrier lies, for the strike: the nearest place of the land it opens (the Dross's
 * own biome search, {@link DrossBiomes#findNearest}), or the nearest home of the boss the next quest is about (the
 * boss-site planner, {@link BossSites#nearest}); anywhere else (a test, a land that isn't found), the way he faces.</p>
 */
public final class OracleSpell
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A boss's home is looked for this far out (blocks). */
    public static final int BOSS_SEARCH = 6000;

    /** The last spell cast (for the headless tests), or null. */
    @Nullable
    private static volatile Cast lastCast;

    /** One spell: the quest whose hand-in cast it, the place it strikes towards, and whether it was the big one. */
    public record Cast(String quest, BlockPos target, long gameTime, boolean big) {}

    private OracleSpell() {}

    /** The last spell cast since the server started (null if none). */
    @Nullable
    public static Cast lastCast()
    {
        return lastCast;
    }

    /** Casts it: {@code player} has just handed in {@code quest} to {@code oracle}. */
    static void cast(ServerPlayer player, Survivor oracle, OracleQuest quest)
    {
        BlockPos target = target(oracle, quest);
        boolean big = opensLand(quest);
        lastCast = new Cast(quest.id(), target, oracle.level().getGameTime(), big);
        castFrom(oracle, player, Vec3.atCenterOf(target), OracleSpellTuning.PAUSE, big);
    }

    /** True if this hand-in opens a land at the surface (the big spell); a boss's home only gets the medium one. */
    public static boolean opensLand(OracleQuest quest)
    {
        return quest.opens().stream().anyMatch(DrossBiomes.ALL::contains);
    }

    /**
     * Casts it from this Oracle towards {@code target}, after {@code delay} ticks: he stands still and says nothing until
     * it's over ({@link Survivor#castSpell}); everyone who can see him (and {@code viewer}) sees it.
     */
    static void castFrom(Survivor oracle, @Nullable ServerPlayer viewer, Vec3 target, int delay, boolean big)
    {
        oracle.castSpell(delay + OracleSpellTuning.duration(big));
        OracleSpellMessage message = message(oracle.getId(), oracle.position(), target, oracle.getYRot(), delay, big);
        QuestNetwork.sendToTracking(oracle, message);
        if (viewer != null)
        {
            QuestNetwork.sendTo(viewer, message);
        }
    }

    private static OracleSpellMessage message(int id, Vec3 from, Vec3 target, float yaw, int delay, boolean big)
    {
        double dx = target.x - from.x;
        double dz = target.z - from.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-3D)
        {
            Vec3 look = Vec3.directionFromRotation(0.0F, yaw);
            dx = look.x;
            dz = look.z;
            distance = 1.0D;
        }
        return new OracleSpellMessage(id, from.x, from.y, from.z, (float) (dx / distance), (float) (dz / distance),
                (float) distance, delay, big);
    }

    // ------------------------------------------------------------------ The showcase (/dross showcase oracle_spell)

    /** How far round the player the showcase looks for the Blind Oracle (blocks). */
    public static final double SHOWCASE_ORACLE_RANGE = 64.0D;
    /** With no Oracle near, the showcase casts it from this far in front of the player (blocks). */
    public static final double SHOWCASE_IN_FRONT = 10.0D;
    /** The showcase's strike lies this far the way the player looks (blocks). */
    public static final double SHOWCASE_STRIKE = 90.0D;

    /**
     * The showcase (the user, November 18, 2026: "a showcase ... so I can give feedback"): the nearest Blind Oracle
     * casts the spell at once towards where the player looks (no pause), or, with none near, it's cast from the ground a
     * few blocks in front of them. Changes no quest. Returns true if an Oracle cast it. {@code big}: the big spell (a
     * land) or the medium one (a boss's home).
     */
    public static boolean showcase(ServerPlayer player, boolean big)
    {
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 target = player.position().add(look.scale(SHOWCASE_STRIKE));
        Survivor oracle = null;
        double best = SHOWCASE_ORACLE_RANGE * SHOWCASE_ORACLE_RANGE;
        for (Survivor s : player.serverLevel().getEntitiesOfClass(Survivor.class, player.getBoundingBox().inflate(SHOWCASE_ORACLE_RANGE),
                s -> s.getRole() == com.reg21meme.dross.villager.SurvivorRole.MENTOR))
        {
            double d = s.distanceToSqr(player);
            if (d < best)
            {
                best = d;
                oracle = s;
            }
        }
        if (oracle != null)
        {
            castFrom(oracle, player, target, 0, big);
            return true;
        }
        Vec3 from = player.position().add(look.scale(SHOWCASE_IN_FRONT));
        BlockPos ground = player.serverLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(from));
        OracleSpellMessage message = message(-1, new Vec3(from.x, ground.getY(), from.z), target, player.getYRot(), 0, big);
        QuestNetwork.sendTo(player, message);
        QuestNetwork.sendToTrackingPlayer(player, message);
        return false;
    }

    /**
     * The barrier falling, on show: a stretch of curtain across the player's view a few blocks ahead stands, flares and
     * falls, as it does at a land's edge the first time ({@code dimension/ward/client/LandWardFall}). Changes nothing.
     */
    public static void showcaseBarrierFall(ServerPlayer player)
    {
        Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
        Vec3 at = player.position().add(look.scale(SHOWCASE_IN_FRONT));
        QuestNetwork.sendTo(player, new LandFallMessage(-1, (int) Math.floor(at.x), (int) Math.floor(at.z), (float) -look.z,
                (float) look.x));
    }

    /** Where the barrier this hand-in breaks lies, as seen from the Oracle (see the class's notes). */
    static BlockPos target(Survivor oracle, OracleQuest quest)
    {
        BlockPos from = oracle.blockPosition();
        BlockPos ahead = BlockPos.containing(oracle.position().add(Vec3.directionFromRotation(0.0F, oracle.getYRot()).scale(64.0D)));
        if (!(oracle.level() instanceof ServerLevel level) || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return ahead;
        }
        try
        {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (ResourceKey<Biome> land : quest.opens())
            {
                if (!DrossBiomes.ALL.contains(land))
                {
                    continue; // (the sewers: under Blightwood, found with it)
                }
                Optional<BlockPos> found = DrossBiomes.findNearest(level, from, land);
                if (found.isPresent() && found.get().distSqr(from) < bestDistance)
                {
                    best = found.get();
                    bestDistance = best.distSqr(from);
                }
            }
            if (best != null)
            {
                return best;
            }
            SiteUse use = quest.boss() != null ? homeOf(quest.boss()) : null;
            if (use != null)
            {
                Optional<Site> site = BossSites.forLevel(level).nearest(use, from, BOSS_SEARCH);
                if (site.isPresent())
                {
                    return site.get().pos();
                }
            }
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("The Oracle's spell couldn't find which way to strike for {}", quest.id(), e);
        }
        return ahead;
    }

    /** The boss-site planner's use for a boss's home. */
    @Nullable
    static SiteUse homeOf(OracleBoss boss)
    {
        return switch (boss)
        {
            case COLOSSUS -> BossSites.COLOSSUS_LAIR;
            case RAT_KING -> BossSites.RAT_KING_SEWER;
            case HUNTSMAN -> BossSites.HUNTSMAN_VILLAGE;
            case EMBERDACTYL -> BossSites.EMBER_VOLCANO;
        };
    }
}
