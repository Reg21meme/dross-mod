package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.boss.BossWards;
import com.reg21meme.dross.boss.GroundsShape;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * The Cinder Colossus's arena ward ({@link BossWards}): its cavern (the floor and the dome over it, and the crater's
 * throat down into it) and its way in, the passage, out to the cave mouth's last covered row. A player the lair's
 * Colossus is locked for who walks in through the mouth is pushed back out of it (the passage's depth is how far in from
 * the mouth they are); one in the cavern itself or dropping down the throat is deep inside and is sent back to their last
 * spot outside (or the gully in front of the mouth). The gully, the volcano's flanks and its crater's rim stay open.
 */
public final class LairWard implements BossWards.Ward
{
    /** A lair's ward is looked at for players within its way in's reach of its middle plus this many blocks. */
    public static final double FIND_MARGIN = 8.0D;
    /** The cavern's ward reaches this far past its floor's edge (and the throat's past its radius). */
    public static final double CAVERN_MARGIN = 0.5D;
    /** The passage's ward reaches this far past its sides. */
    public static final double PASSAGE_SIDE_MARGIN = 1.0D;
    /** The passage's ward reaches this far over its arch's top and under its floor (blocks, from the feet). */
    public static final double PASSAGE_HEIGHT_MARGIN = 0.5D;
    /** How far in someone in the cavern or the throat counts as (always deeper than a push reaches: sent back). */
    public static final double DEEP = 99.0D;
    /** A lair with no saved way in (only the tests' made-up ones): its way out is this far out past the mouth. */
    public static final double EXIT_PAST_MOUTH = 3.0D;
    /**
     * Its blocks are protected ({@link #protects}, {@code boss.BossGrounds}) out to this far past its cone's foot (the
     * pools at its rivulets' feet, the ramp and steps of its way in)...
     */
    public static final double PROTECT_OUT = 10.0D;
    /** ...from this far under its cavern's floor (the lava under the floor, a way in that steps down to low land)... */
    public static final int PROTECT_BELOW = 16;
    /** ...up to this far over its rim (its jagged teeth). */
    public static final int PROTECT_ABOVE = 12;

    private final LairKeeper.Volume lair;
    @Nullable
    private final Vec3 entrance;
    private final float entranceYaw;

    LairWard(LairKeeper.Volume lair, @Nullable Vec3 entrance, float entranceYaw)
    {
        this.lair = lair;
        this.entrance = entrance;
        this.entranceYaw = entranceYaw;
    }

    /** The wards of the built lairs near this player (the Dross only). */
    public static void find(ServerPlayer player, ServerLevel level, List<BossWards.Ward> out)
    {
        List<LairKeeper.Volume> lairs = LairKeeper.volumes();
        if (lairs.isEmpty())
        {
            return;
        }
        for (LairKeeper.Volume lair : lairs)
        {
            if (lair.distance(player.getX(), player.getZ()) <= lair.mouth() + FIND_MARGIN)
            {
                ColossusLairData.Entry entry = ColossusLairData.get(level.getServer()).lair(lair.id());
                out.add(new LairWard(lair, entry != null ? entry.entrance() : null, entry != null ? entry.entranceYaw() : 0.0F));
            }
        }
    }

    /** True if this block belongs to a built lair: its volcano, cavern and way in (the Dross only). */
    public static boolean protects(BlockPos pos)
    {
        for (LairKeeper.Volume lair : LairKeeper.volumes())
        {
            if (shape(lair).contains(pos))
            {
                return true;
            }
        }
        return false;
    }

    /** The protected shapes of the built lairs that reach within {@code radius} blocks of this spot (for a player's game). */
    public static void shapesNear(double x, double z, double radius, List<GroundsShape> out)
    {
        for (LairKeeper.Volume lair : LairKeeper.volumes())
        {
            GroundsShape.Round shape = shape(lair);
            if (shape.near(x, z, radius))
            {
                out.add(shape);
            }
        }
    }

    /** A lair's protected blocks: round its floor's middle out past its cone's foot, from under its floor to over its rim. */
    private static GroundsShape.Round shape(LairKeeper.Volume lair)
    {
        return new GroundsShape.Round(lair.cx() + 0.5D, lair.cz() + 0.5D, CONE_BASE_RADIUS + PROTECT_OUT,
                lair.floorY() - PROTECT_BELOW, lair.rimY() + PROTECT_ABOVE, GroundsShape.Round.ROUND);
    }

    @Override
    public OracleBoss boss()
    {
        return OracleBoss.COLOSSUS;
    }

    @Override
    public double depth(Vec3 feet)
    {
        LairKeeper.Volume v = this.lair;
        double dx = feet.x - (v.cx() + 0.5D);
        double dz = feet.z - (v.cz() + 0.5D);
        double dy = feet.y - v.floorY();
        double r = Math.sqrt(dx * dx + dz * dz);
        if (dy >= 0.5D && dy <= DOME_HEIGHT + 1 && r <= LairShape.FLOOR_EDGE + CAVERN_MARGIN)
        {
            return DEEP + Math.max(0.0D, LairShape.FLOOR_EDGE - r); // in the cavern
        }
        if (dy > DOME_HEIGHT && dy <= v.rimY() - v.floorY() && r <= SKYLIGHT_RADIUS + CAVERN_MARGIN)
        {
            return DEEP; // dropping down the crater's throat into it
        }
        Direction tunnel = v.tunnel();
        Direction side = tunnel.getClockWise();
        double s = dx * tunnel.getStepX() + dz * tunnel.getStepZ();
        double l = dx * side.getStepX() + dz * side.getStepZ();
        int mouth = v.mouth();
        if (s >= FLOOR_RADIUS - 3 && s <= mouth + 0.5D && Math.abs(l) <= LairShape.passageHalfWidth(s, mouth) + PASSAGE_SIDE_MARGIN
                && dy >= 1.0D - PASSAGE_HEIGHT_MARGIN && dy <= LairShape.passageHeight(s, mouth) + PASSAGE_HEIGHT_MARGIN)
        {
            return mouth + 0.5D - s; // in the passage: how far in from the mouth
        }
        return 0.0D;
    }

    @Override
    public Vec3 outward(Vec3 feet)
    {
        Direction tunnel = this.lair.tunnel();
        return new Vec3(tunnel.getStepX(), 0.0D, tunnel.getStepZ());
    }

    @Override
    public Vec3 exit()
    {
        if (this.entrance != null)
        {
            return this.entrance;
        }
        Direction tunnel = this.lair.tunnel();
        double out = this.lair.mouth() + EXIT_PAST_MOUTH;
        return new Vec3(this.lair.cx() + 0.5D + tunnel.getStepX() * out, this.lair.floorY() + 1.0D,
                this.lair.cz() + 0.5D + tunnel.getStepZ() * out);
    }

    @Override
    public float exitYaw()
    {
        if (this.entrance != null)
        {
            return this.entranceYaw;
        }
        Direction in = this.lair.tunnel().getOpposite(); // facing into the mouth
        return Mth.wrapDegrees((float) (Mth.atan2(in.getStepZ(), in.getStepX()) * Mth.RAD_TO_DEG) - 90.0F);
    }
}
