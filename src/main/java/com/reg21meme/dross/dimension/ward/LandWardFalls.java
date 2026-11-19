package com.reg21meme.dross.dimension.ward;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import com.reg21meme.dross.quest.oracle.OracleRules;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * THE BARRIERS FALLING (server side; QUEST_PLAN.md, "The Oracle's spell"; the user, November 18, 2026: "when you first
 * arrive at that barrier ... it does an animation where the barrier falls off"). Once the Oracle has broken a land's
 * barrier ({@link OracleRules#isLandOpen}), its curtain still stands for that player until they first come within
 * {@value #RANGE} blocks of its edge (a column of the land and one of another land both that near: arriving deep inside
 * it by a teleport, it waits until they come near its edge); then it falls in front of them, once ({@link OracleProgress#barrierFalls}, which
 * saves it and has their game play it, {@code client/LandWardFall}). Only the lands at the surface
 * ({@link DrossBiomes#ALL}); the sewers fall with Blightwood. Creative players see it too (since November 19, 2026, the
 * user: "I still don't see the barrier breaking when I go to the biome": they test in creative, where the barrier never
 * stood in their way); spectators never do. Arriving inside the land (a teleport) with no edge within
 * {@value #RANGE} blocks, its nearest edge out to {@value #FAR_RANGE} blocks falls (the user, November 19, 2026: "it
 * happened in front of me instead of at the border next to me"); only with none that near does it fall as a straight
 * stretch across their view {@value #AHEAD} blocks ahead.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LandWardFalls
{
    /** The players in the Dross are checked every this many ticks (once a second). */
    public static final int CHECK_EVERY_TICKS = 20;
    /** A land's barrier falls when a player first comes this near the land (blocks). */
    public static final int RANGE = 24;
    /** The columns looked at: rings this far apart out to {@link #RANGE}... */
    public static final int RING_STEP = 4;
    /** ...with this many columns round each ring per {@value #RING_STEP} blocks of its radius (at least 8). */
    public static final int COLUMNS_PER_STEP = 6;
    /** Inside with no edge within RANGE: its edge is looked for out to this far, on rings this far apart... */
    public static final int FAR_RANGE = 64;
    public static final int FAR_STEP = 2;
    /** ...with a column every this many blocks round each ring. */
    public static final double FAR_SPACING = 2.0D;
    /** Deep inside with no edge even that near: the stretch falls this far in front of them (blocks). */
    public static final int AHEAD = 12;

    private LandWardFalls() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % CHECK_EVERY_TICKS != 0)
        {
            return;
        }
        ServerLevel dross = event.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty())
        {
            return;
        }
        for (ServerPlayer player : List.copyOf(dross.players()))
        {
            check(player);
        }
    }

    /** Lets the barrier of one land fall for this player if they've come to it (public for the headless tests). */
    public static void check(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator() || !player.level().dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        for (ResourceKey<Biome> land : OracleQuests.lockableLands())
        {
            if (!DrossBiomes.ALL.contains(land) || !OracleRules.isLandOpenByQuest(player, land) || OracleProgress.hasSeenFall(player, land))
            {
                continue;
            }
            int[] near = nearestColumn(player, land, true);
            if (near == null)
            {
                continue;
            }
            if (nearestColumn(player, land, false) != null)
            {
                OracleProgress.barrierFalls(player, land, near[0], near[1]);
                return; // one a check: two barriers never fall at once
            }
            if (near[0] == player.getBlockX() && near[1] == player.getBlockZ())
            {
                int[] edge = farEdge(player, land);
                if (edge != null)
                {
                    OracleProgress.barrierFalls(player, land, edge[0], edge[1]);
                    return;
                }
                // Inside it, its edge far off: a stretch across their view ahead.
                Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
                Vec3 at = player.position().add(look.scale(AHEAD));
                OracleProgress.barrierFalls(player, land, Mth.floor(at.x), Mth.floor(at.z), (float) -look.z, (float) look.x);
                return;
            }
        }
    }

    /**
     * Standing in {@code land}: a column of it right by its nearest edge within {@value #FAR_RANGE} blocks (the first
     * column of another land on the nearest ring, then back towards the player to the last of this land), or null.
     */
    @Nullable
    static int[] farEdge(ServerPlayer player, ResourceKey<Biome> land)
    {
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        for (int r = FAR_STEP; r <= FAR_RANGE; r += FAR_STEP)
        {
            int count = Math.max(16, (int) Math.ceil(Mth.TWO_PI * r / FAR_SPACING));
            for (int i = 0; i < count; i++)
            {
                float a = Mth.TWO_PI * i / count;
                int x = px + Math.round(Mth.cos(a) * r);
                int z = pz + Math.round(Mth.sin(a) * r);
                if (land.equals(LandWard.landAt(level, x, z)))
                {
                    continue;
                }
                for (int back = r - 1; back >= 0; back--)
                {
                    int bx = px + Math.round(Mth.cos(a) * back);
                    int bz = pz + Math.round(Mth.sin(a) * back);
                    if (land.equals(LandWard.landAt(level, bx, bz)))
                    {
                        return new int[] {bx, bz};
                    }
                }
                return new int[] {px, pz};
            }
        }
        return null;
    }

    /**
     * A column within {@value #RANGE} blocks of the player (the nearest ring first) whose land is {@code land}
     * ({@code inside}) or isn't, or null.
     */
    @Nullable
    static int[] nearestColumn(ServerPlayer player, ResourceKey<Biome> land, boolean inside)
    {
        ServerLevel level = player.serverLevel();
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        if (land.equals(LandWard.landAt(level, px, pz)) == inside)
        {
            return new int[] {px, pz};
        }
        for (int r = RING_STEP; r <= RANGE; r += RING_STEP)
        {
            int count = Math.max(8, COLUMNS_PER_STEP * r / RING_STEP);
            for (int i = 0; i < count; i++)
            {
                float a = Mth.TWO_PI * i / count;
                int x = px + Math.round(Mth.cos(a) * r);
                int z = pz + Math.round(Mth.sin(a) * r);
                if (land.equals(LandWard.landAt(level, x, z)) == inside)
                {
                    return new int[] {x, z};
                }
            }
        }
        return null;
    }
}
