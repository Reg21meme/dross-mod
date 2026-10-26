package com.reg21meme.dross.boss.huntsman;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Room to move (the user, October 25, 2026: "he often moves into walls"): how far the Huntsman or his moose can go across
 * the ground in a direction before a wall, a drop or water stops it, and where to go when it falls back, keeps its range
 * or rides off. Their spots used to be picked by distance alone, so beside a wall they ran into it until the move's time
 * ran out.
 */
final class HuntRoom
{
    /** How far apart the steps are that a run is tried at (blocks). */
    private static final double STEP = 0.5D;
    /** How far it may step up (a block, as it walks) or drop at a step. */
    private static final double STEP_UP = 1.0D;
    private static final int MAX_DROP = 3;
    /** How far short of a wall or drop it stops. */
    private static final double MARGIN = 1.0D;
    /** The turns (degrees) a way out is tried at, nearest the one wanted first. */
    private static final double[] TURNS = {0, 25, -25, 50, -50, 75, -75, 100, -100, 130, -130, 160, -160};
    /** A way counts as clear when it reaches this share of the distance wanted. */
    private static final double CLEAR_SHARE = 0.85D;

    private HuntRoom() {}

    /** How far {@code mob} can go along {@code dir} (flat; any length) before something stops it, at most {@code max}
     * (blocks, already short of the wall by {@link #MARGIN}). */
    static double run(Mob mob, Vec3 dir, double max)
    {
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 1.0E-6D || max <= 0.0D)
        {
            return 0.0D;
        }
        double ux = dir.x / len;
        double uz = dir.z / len;
        Level level = mob.level();
        AABB box = mob.getBoundingBox();
        double dy = 0.0D;                                // how far its feet have risen or dropped along the way
        double got = 0.0D;
        for (double d = STEP; d <= max + MARGIN + 1.0E-6D; d += STEP)
        {
            Double at = standAt(level, mob, box.move(ux * d, dy, uz * d));
            if (at == null)
            {
                break;
            }
            dy += at;
            got = d;
        }
        return Math.max(0.0D, Math.min(max, got - MARGIN));
    }

    /** Where {@code mob} should head to go about {@code want} blocks along {@code dir} (flat): the nearest turn of it
     * with room, or else the way that gets furthest. With {@code awayFrom}, a way only counts if it takes it at least
     * {@code want} / 2 further from that spot, and the best of the rest is the one ending furthest from it. */
    static Vec3 spot(Mob mob, Vec3 dir, double want, @Nullable Vec3 awayFrom)
    {
        Vec3 from = mob.position();
        double now = awayFrom != null ? flat(from, awayFrom) : 0.0D;
        Vec3 best = from;
        double bestScore = -1.0D;
        for (double turn : TURNS)
        {
            Vec3 d = turned(dir, turn);
            double r = run(mob, d, want);
            Vec3 end = from.add(d.scale(r));
            double score = awayFrom != null ? flat(end, awayFrom) : r;
            if (r >= want * CLEAR_SHARE && (awayFrom == null || score >= now + want * 0.5D))
            {
                return end;
            }
            if (score > bestScore)
            {
                bestScore = score;
                best = end;
            }
        }
        return best;
    }

    /** True if {@code mob} can go straight to {@code to} (most of the way, at least). */
    static boolean clear(Mob mob, Vec3 to)
    {
        Vec3 d = new Vec3(to.x - mob.getX(), 0.0D, to.z - mob.getZ());
        double want = d.length();
        return want < 1.0D || run(mob, d, want) >= want * CLEAR_SHARE;
    }

    /** {@code dir} (flat) turned {@code degrees} about the up axis, length 1. */
    static Vec3 turned(Vec3 dir, double degrees)
    {
        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        double x = len < 1.0E-6D ? 1.0D : dir.x / len;
        double z = len < 1.0E-6D ? 0.0D : dir.z / len;
        double a = Math.toRadians(degrees);
        double c = Math.cos(a);
        double s = Math.sin(a);
        return new Vec3(x * c - z * s, 0.0D, x * s + z * c);
    }

    private static double flat(Vec3 a, Vec3 b)
    {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** How far its feet move up or down to stand with its box at {@code box} (stepping up a block, or dropping up to
     * {@link #MAX_DROP}), or null if it can't: a wall, a deeper drop, or water. */
    @Nullable
    private static Double standAt(Level level, Mob mob, AABB box)
    {
        for (double up : new double[] {0.0D, STEP_UP})
        {
            AABB b = box.move(0.0D, up, 0.0D);
            if (level.noCollision(mob, b))
            {
                for (int down = 0; down <= MAX_DROP; down++)
                {
                    AABB f = b.move(0.0D, -down, 0.0D);
                    if (down > 0 && !level.noCollision(mob, f))
                    {
                        break;                               // (it would land on what's above here)
                    }
                    if (!level.noCollision(mob, new AABB(f.minX, f.minY - 0.2D, f.minZ, f.maxX, f.minY, f.maxZ)))
                    {
                        return level.containsAnyLiquid(f) ? null : up - down;
                    }
                }
                return null;                                 // (a drop too deep)
            }
        }
        return null;
    }
}
