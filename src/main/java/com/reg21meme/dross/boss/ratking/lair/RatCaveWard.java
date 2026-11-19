package com.reg21meme.dross.boss.ratking.lair;

import com.reg21meme.dross.boss.BossWards;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.dimension.sewer.SewerTuning;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Rat King's arena ward ({@link BossWards}): his rat cave itself, its air and floor (dimension-builder's
 * {@link RatCaves.RatCave#contains}), not the rock round it or the tunnels leading in. A player the cave's king is locked
 * for who steps in from a tunnel is pushed back into it (away from the cave's middle: the cave is round, every tunnel
 * comes in from its edge); one deeper in is sent back to their last spot outside, or into the tunnel behind the cave's
 * arrival spot.
 */
public final class RatCaveWard implements BossWards.Ward
{
    /** The caves lie under Blightwood, all below y 58: a player higher than this is in none. */
    public static final int CAVE_WARD_MAX_Y = 64;
    /** How far past the cave's floor radius its wobbly edge can reach (its depth is measured from there). */
    public static final double EDGE = SewerTuning.RAT_CAVE_RADIUS + SewerTuning.RAT_CAVE_WOBBLE;
    /** A spot in the cave counts at least this deep, even right by its edge. */
    public static final double MIN_DEPTH = 0.1D;
    /**
     * The way out: the nearest spot to stand on in the tunnels joined to the arrival spot (found through open air from
     * it), outside the cave and a block clear of it, at most this many blocks from the arrival spot...
     */
    public static final int EXIT_SEARCH = 12;
    /** ...looking through at most this many open blocks. */
    public static final int EXIT_SEARCH_BLOCKS = 6000;

    private final ServerLevel level;
    private final RatCaves.RatCave cave;

    RatCaveWard(ServerLevel level, RatCaves.RatCave cave)
    {
        this.level = level;
        this.cave = cave;
    }

    /** The rat cave this player is in, if any (only low in the Dross: the caves are deep under Blightwood). */
    public static void find(ServerPlayer player, ServerLevel level, List<BossWards.Ward> out)
    {
        if (player.getY() > CAVE_WARD_MAX_Y)
        {
            return;
        }
        RatCaves.caveAt(level, player.blockPosition()).ifPresent(cave -> out.add(new RatCaveWard(level, cave)));
    }

    /** The ward of this cave (for the tests). */
    public static BossWards.Ward of(ServerLevel level, RatCaves.RatCave cave)
    {
        return new RatCaveWard(level, cave);
    }

    @Override
    public OracleBoss boss()
    {
        return OracleBoss.RAT_KING;
    }

    @Override
    public double depth(Vec3 feet)
    {
        if (!this.cave.contains(BlockPos.containing(feet)))
        {
            return 0.0D;
        }
        BlockPos centre = this.cave.centre();
        double r = Math.hypot(feet.x - (centre.getX() + 0.5D), feet.z - (centre.getZ() + 0.5D));
        return Math.max(MIN_DEPTH, EDGE - r);
    }

    @Override
    public Vec3 outward(Vec3 feet)
    {
        BlockPos centre = this.cave.centre();
        return BossWards.awayFrom(centre.getX() + 0.5D, centre.getZ() + 0.5D, feet);
    }

    /**
     * Into the tunnel behind the arrival spot (where {@code /dross goto lair ratking} lands): the nearest spot joined to it
     * through open air that's outside the cave, a block clear of it, with a floor and room to stand (no water); else its
     * sewer's grand entrance; else the arrival spot.
     */
    @Override
    public Vec3 exit()
    {
        BlockPos arrival = this.cave.arrival();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        queue.add(arrival);
        seen.add(arrival);
        while (!queue.isEmpty() && seen.size() < EXIT_SEARCH_BLOCKS)
        {
            BlockPos pos = queue.poll();
            if (this.clearOfCave(pos) && this.standable(pos))
            {
                return Vec3.atBottomCenterOf(pos);
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values())
            {
                BlockPos next = pos.relative(d);
                if (next.distManhattan(arrival) <= EXIT_SEARCH * 2 && Math.abs(next.getX() - arrival.getX()) <= EXIT_SEARCH
                        && Math.abs(next.getZ() - arrival.getZ()) <= EXIT_SEARCH && seen.add(next) && this.open(next))
                {
                    queue.add(next);
                }
            }
        }
        BlockPos entrance = this.cave.entrance();
        return Vec3.atBottomCenterOf(entrance != null ? entrance : arrival);
    }

    /** Outside the cave, and so are the blocks beside it (a step into the tunnel, not on the cave's threshold). */
    private boolean clearOfCave(BlockPos pos)
    {
        if (this.cave.contains(pos))
        {
            return false;
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL)
        {
            if (this.cave.contains(pos.relative(d)))
            {
                return false;
            }
        }
        return true;
    }

    /** A block one can move through (air, water, a plant): loaded, nothing solid in it. */
    private boolean open(BlockPos pos)
    {
        return this.level.isLoaded(pos) && this.level.getBlockState(pos).getCollisionShape(this.level, pos).isEmpty();
    }

    @Override
    public float exitYaw()
    {
        return Mth.wrapDegrees(this.cave.arrivalYaw() + 180.0F); // facing back down the tunnel, away from the cave
    }

    private boolean standable(BlockPos feet)
    {
        BlockPos below = feet.below();
        return this.level.isLoaded(feet)
                && this.level.getBlockState(feet).getCollisionShape(this.level, feet).isEmpty()
                && this.level.getBlockState(feet.above()).getCollisionShape(this.level, feet.above()).isEmpty()
                && !this.level.getBlockState(below).getCollisionShape(this.level, below).isEmpty()
                && this.level.getFluidState(feet).isEmpty();
    }
}
