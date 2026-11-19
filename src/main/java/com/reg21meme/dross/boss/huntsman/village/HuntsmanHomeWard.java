package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.boss.BossWards;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Huntsman's home ward ({@link BossWards}; the user, November 19, 2026: "you shouldn't be able to go into the
 * Huntsman's house or the moose's barn, or from the top at all, make barriers around that"): while he sleeps for a
 * player (their Oracle quest hasn't reached him), his lodge and the Alpha Moose's barn (its corral too) are each a
 * warded box, from {@value #BELOW} blocks under his yard up to {@value #ABOVE} over it, so walking in at a door, climbing
 * onto a roof or dropping onto one from above bounces them out like the other bosses' wards. His yard and his gear in
 * front of his door stay open; his roof's clear ice window is out of reach now (you hear him snoring from his door).
 *
 * <p>The boxes are the lodge's and the barn's templates' footprints, in the village's own coordinates from his yard's
 * block (as {@link HuntsmanAsleep}'s spots: his lodge and barn stand at the same place from his yard in both of his
 * villages, {@code tools/huntsman/village_designs/warren.py} {@code LODGE_AT}, {@code BARN_AT}), turned with the village.
 * Measured from the templates (the lodge 39 x 47, the barn 29 x 21; November 19, 2026).</p>
 */
public final class HuntsmanHomeWard implements BossWards.Ward
{
    /** The lodge's box (the village's x and z from his yard's block, edges between blocks): its front's face at z -4,
     * a block short of the template's own edge so his gear on the snow before his door stays outside. */
    static final double[] LODGE = {-19.0D, 20.0D, -49.0D, -4.0D};
    /** The barn's box (the barn and its corral), beside the lodge on the east. */
    static final double[] BARN = {20.0D, 49.0D, -25.0D, -4.0D};
    /** The boxes reach from this far under his yard's ground... */
    public static final int BELOW = 10;
    /** ...up to this far over it (well over the lodge's roof: nobody drops in from above). */
    public static final int ABOVE = 48;
    /** A village's ward is looked at for players within this many blocks of his yard. */
    public static final double FIND_RANGE = 120.0D;
    /** Sent back, they land in his yard this far south of his door (the village's z), facing it. */
    static final HuntsmanAsleep.Spot EXIT = new HuntsmanAsleep.Spot(0.5D, 2.5D, 180.0F);

    private final ServerLevel level;
    private final RimefallVillages.VillagePlan plan;
    private final double[] box;
    private final BlockPos base;
    private final BlockPos ex;
    private final BlockPos ez;

    HuntsmanHomeWard(ServerLevel level, RimefallVillages.VillagePlan plan, double[] box)
    {
        this.level = level;
        this.plan = plan;
        this.box = box;
        this.base = HuntsmanAsleep.local(plan, 0, 0);
        this.ex = HuntsmanAsleep.local(plan, 1, 0).subtract(this.base);
        this.ez = HuntsmanAsleep.local(plan, 0, 1).subtract(this.base);
    }

    /** The wards of the Huntsman's villages near this player: his lodge's and his barn's. */
    public static void find(ServerPlayer player, ServerLevel level, List<BossWards.Ward> out)
    {
        for (Site site : BossSites.forLevel(level).sitesNear(DrossBiomes.RIMEFALL, player.getBlockX(), player.getBlockZ(),
                (int) FIND_RANGE + 160))
        {
            if (site.use() != BossSites.HUNTSMAN_VILLAGE || !(site.detail() instanceof RimefallVillages.VillagePlan plan))
            {
                continue;
            }
            if (player.position().distanceToSqr(Vec3.atBottomCenterOf(plan.home())) <= FIND_RANGE * FIND_RANGE)
            {
                out.add(new HuntsmanHomeWard(level, plan, LODGE));
                out.add(new HuntsmanHomeWard(level, plan, BARN));
            }
        }
    }

    @Override
    public OracleBoss boss()
    {
        return OracleBoss.HUNTSMAN;
    }

    /** These feet in the village's own x and z (continuous: a block's cell from its value to the next). */
    private double[] toLocal(Vec3 feet)
    {
        double dx = feet.x - this.base.getX() - 0.5D;
        double dz = feet.z - this.base.getZ() - 0.5D;
        return new double[] {this.ex.getX() * dx + this.ex.getZ() * dz + 0.5D, this.ez.getX() * dx + this.ez.getZ() * dz + 0.5D};
    }

    @Override
    public double depth(Vec3 feet)
    {
        int ground = this.plan.home().getY();
        if (feet.y < ground - BELOW || feet.y > ground + ABOVE)
        {
            return 0.0D;
        }
        double[] l = toLocal(feet);
        return Math.min(Math.min(l[0] - this.box[0], this.box[1] - l[0]), Math.min(l[1] - this.box[2], this.box[3] - l[1]));
    }

    /** Out through the nearest side of its box. */
    @Override
    public Vec3 outward(Vec3 feet)
    {
        double[] l = toLocal(feet);
        double west = l[0] - this.box[0];
        double east = this.box[1] - l[0];
        double north = l[1] - this.box[2];
        double south = this.box[3] - l[1];
        double least = Math.min(Math.min(west, east), Math.min(north, south));
        double ux = least == west ? -1.0D : least == east ? 1.0D : 0.0D;
        double uz = ux != 0.0D ? 0.0D : least == north ? -1.0D : 1.0D;
        return new Vec3(this.ex.getX() * ux + this.ez.getX() * uz, 0.0D, this.ex.getZ() * ux + this.ez.getZ() * uz).normalize();
    }

    /** His yard before his door. */
    @Override
    public Vec3 exit()
    {
        return HuntsmanAsleep.at(this.level, this.plan, EXIT);
    }

    @Override
    public float exitYaw()
    {
        return HuntsmanAsleep.yaw(this.plan, EXIT);
    }
}
