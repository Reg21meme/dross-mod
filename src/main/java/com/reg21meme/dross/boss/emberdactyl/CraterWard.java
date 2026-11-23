package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.boss.BossWards;
import com.reg21meme.dross.boss.GroundsShape;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.reg21meme.dross.boss.emberdactyl.EmberVolcanoTuning.*;

/**
 * The Emberdactyl's arena ward ({@link BossWards}): the whole of a world volcano, everything inside its foot (its flanks,
 * its path, the crest, the crater and the nest), from {@value #BELOW_GROUND} blocks under its ground up to
 * {@value #ABOVE_RIM} blocks over its rim (since the user's "make the barrier for the Emberdactyl further out", November
 * 9, 2026; it was only the crater inside the crest, so a locked player could climb the path to the lookout, right beside
 * its perch). The foot isn't round: it's pushed in and out in lobes, worked out from the volcano's seed
 * ({@link EmberVolcano#lobePhases}), and the ward follows it. A player the volcano's Emberdactyl is locked for who walks
 * onto its foot is pushed back out, away from its middle; one deeper in is sent back to their last spot outside, or the
 * path's foot (just outside the ward). The land round it, its foot's lava pools and its apron, stay open (the Cinder
 * Wastes open earlier than the Emberdactyl), so they can look up at it on its perch. A test arena's volcano isn't the
 * world's, so has no ward. Its blocks are protected too ({@link #protects}, {@code boss.BossGrounds}).
 */
public final class CraterWard implements BossWards.Ward
{
    /** The ward reaches this far over the rim's height (a player flying over the volcano)... */
    public static final int ABOVE_RIM = 48;
    /** ...and down to this far under its ground (no tunnelling in under it). */
    public static final int BELOW_GROUND = 16;
    /**
     * The ward's edge is this far past the cone's foot ({@code BASE_RADIUS}, lobed): 0, the foot itself. The path's foot,
     * the way out, lies {@code PATH_START_OUT} (2) past it, outside the ward.
     */
    public static final double WARD_OUT = 0.0D;
    /**
     * Its blocks are protected out to this far past the cone's foot (lobed): the lava pools at its rivers' feet and the
     * cooled crust round them ({@code FOOT_POOL_OUT} + the pool + {@code CRUST_REACH}), and the bones at its foot...
     */
    public static final double PROTECT_OUT = 16.0D;
    /** ...from this far under its ground (its re-laid foundation, the lava's walls) up to this far over its rim. */
    public static final int PROTECT_BELOW = 12;
    public static final int PROTECT_ABOVE = 24;
    /** A volcano's ward is looked at for players within its widest foot plus this many blocks of its middle. */
    public static final double FIND_MARGIN = 6.0D;
    /** The widest a foot is pushed out (its lobes at their most). */
    private static final double WIDEST = (BASE_RADIUS + Math.max(WARD_OUT, PROTECT_OUT)) * (1.0D + LOBE_3 + LOBE_5 + LOBE_2);

    /** Each volcano's lobes, by the seed it was shaped from (worked out once). */
    private static final Map<Long, double[]> PHASES = new ConcurrentHashMap<>();

    private final EmberVolcanoData.Volcano volcano;
    private final double[] phases;

    CraterWard(EmberVolcanoData.Volcano volcano, double[] phases)
    {
        this.volcano = volcano;
        this.phases = phases;
    }

    /** The wards of the built volcanoes near this player (the Dross only; a test arena's volcano isn't among them). */
    public static void find(ServerPlayer player, ServerLevel level, List<BossWards.Ward> out)
    {
        List<EmberVolcanoData.Volcano> all = EmberVolcanoData.get(level.getServer()).all();
        for (EmberVolcanoData.Volcano v : all)
        {
            if (v.distanceTo(player.getX(), player.getZ()) <= WIDEST + FIND_MARGIN)
            {
                out.add(of(level, v));
            }
        }
    }

    /** The ward of this built volcano. */
    public static CraterWard of(ServerLevel level, EmberVolcanoData.Volcano volcano)
    {
        long seed = VolcanoPlacer.shapeSeed(level, volcano.id());
        return new CraterWard(volcano, PHASES.computeIfAbsent(seed, EmberVolcano::lobePhases));
    }

    /** The ward of a volcano shaped from {@code seed} (for the tests). */
    public static CraterWard of(EmberVolcanoData.Volcano volcano, long seed)
    {
        return new CraterWard(volcano, EmberVolcano.lobePhases(seed));
    }

    @Override
    public OracleBoss boss()
    {
        return OracleBoss.EMBERDACTYL;
    }

    /** How far inside its foot (blocks), at the feet's height between under its ground and the sky over it. */
    @Override
    public double depth(Vec3 feet)
    {
        if (feet.y < this.volcano.ground().getY() - BELOW_GROUND || feet.y > this.volcano.rimY() + ABOVE_RIM)
        {
            return 0.0D;
        }
        double dx = feet.x - (this.volcano.ground().getX() + 0.5D);
        double dz = feet.z - (this.volcano.ground().getZ() + 0.5D);
        double lobe = EmberVolcano.lobes(this.phases, Math.atan2(dz, dx));
        double r = Math.sqrt(dx * dx + dz * dz);
        return (BASE_RADIUS + WARD_OUT) * lobe - r;
    }

    /** True if this block is part of the volcano (its cone, its foot's pools and crust, the ground it re-laid). */
    public boolean covers(BlockPos pos)
    {
        return shape().contains(pos);
    }

    /**
     * The volcano's protected blocks as one shape (its lobed foot pushed out {@value #PROTECT_OUT} blocks, from under its
     * ground to over its rim): what {@link #covers} tests, and what a player's game is sent.
     */
    public GroundsShape.Round shape()
    {
        return new GroundsShape.Round(this.volcano.ground().getX() + 0.5D, this.volcano.ground().getZ() + 0.5D,
                BASE_RADIUS + PROTECT_OUT, this.volcano.ground().getY() - PROTECT_BELOW, this.volcano.rimY() + PROTECT_ABOVE,
                this.phases);
    }

    /** The protected shapes of the built world volcanoes that reach within {@code radius} blocks of this spot. */
    public static void shapesNear(ServerLevel level, double x, double z, double radius, List<GroundsShape> out)
    {
        for (EmberVolcanoData.Volcano v : EmberVolcanoData.get(level.getServer()).all())
        {
            if (v.distanceTo(x, z) <= WIDEST + radius)
            {
                GroundsShape.Round shape = of(level, v).shape();
                if (shape.near(x, z, radius))
                {
                    out.add(shape);
                }
            }
        }
    }

    /** True if this block belongs to a built world volcano (the Dross only; a test arena's volcano isn't one). */
    public static boolean protects(ServerLevel level, BlockPos pos)
    {
        for (EmberVolcanoData.Volcano v : EmberVolcanoData.get(level.getServer()).all())
        {
            if (v.distanceTo(pos.getX() + 0.5D, pos.getZ() + 0.5D) <= WIDEST && of(level, v).covers(pos))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public Vec3 outward(Vec3 feet)
    {
        return BossWards.awayFrom(this.volcano.ground().getX() + 0.5D, this.volcano.ground().getZ() + 0.5D, feet);
    }

    /** The path's foot (facing up it). */
    @Override
    public Vec3 exit()
    {
        return this.volcano.pathFoot();
    }

    @Override
    public float exitYaw()
    {
        return this.volcano.pathFootYaw();
    }
}
