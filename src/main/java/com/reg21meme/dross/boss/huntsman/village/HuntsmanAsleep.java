package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.huntsman.HuntsmanPair;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Huntsman and his Alpha Moose asleep at home, until the Oracle's quest of a player near reaches him (the user,
 * November 18, 2026: "before he's unlocked he should be sleeping inside his home, on his bed ... you can hear him
 * snoring ... the moose sleeps and snores too ... his spear alongside his igloo, his bow and arrow as well etc. in front
 * of his door lying on the front of the igloo, his throwing stuff on the floor, some arrows on the floor ... they're gone
 * once he wakes up"; {@link HuntsmanVillages} decides when):
 * <ul>
 *   <li>him on his back on his bed of furs, the rug that fills his lodge's bedroom ({@link #BED}: his middle; his
 *       feet to the south of the lodge, his head on the pale fur at its north end; the user: "make him sleep on the floor
 *       ... just that carpet", then "remove the beds ... the carpets are his bed": the triple bed is gone), under the clear ice window in its roof ({@code tools/huntsman/igloo_designs/
 *       lodge_house.py}), so you can look down on him from the roof;</li>
 *   <li>the Alpha Moose lying in its stall in the barn ({@link #STALL}), facing its door;</li>
 *   <li>his gear lying on the snow in front of his door ({@link #GEAR}): his spear leaning on the lodge's front, his bow,
 *       his bola and arrows scattered by the door, each his own at his size (3D models of them lifted off his model:
 *       {@code tools/huntsman/hunt_gear.py}, the blocks {@code dross:hunt_bow}, {@code hunt_arrow}, {@code hunt_bola};
 *       the user: "his bow is that small, make a 3D model just for it"). Display entities tagged {@link #GEAR_TAG},
 *       taken away when he wakes.</li>
 * </ul>
 * Where: his lodge and barn stand in both of his villages at the same place from his yard ({@code warren.py}'s
 * {@code LODGE_AT}, {@code BARN_AT}; the lodge and barn templates aren't turned in it), so every spot is fixed in the
 * village's own (template) coordinates from his yard's block ({@code homeX}, {@code homeZ}: it spans 0 to 1), turned
 * with the village. Measured from the templates on November 18, 2026 ({@code tools/huntsman}'s lodge and barn): the
 * rug x -13 to -11, z -30 (its pale head row) to -25 (since November 19; the triple bed stood on -30 and -29 until
 * then; the bedroom's wall at -24), the stall's straw x 23 to 29 (troughs at 22, its fence at
 * 29), z -17 to -8 (the barn's door at -8).
 */
public final class HuntsmanAsleep
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** A spot in the village's own coordinates from his yard's block, and the way it faces there (degrees: 0 the
     * template's south, +z). */
    record Spot(double x, double z, float yaw)
    {
    }

    /** His middle lying on his bed of furs, the rug that fills his bedroom (no bed since November 19, 2026, the user:
     * "remove the beds from the Huntsman's home ... the carpets are his bed"), his feet to the south: the sleep's model
     * lies centred on his spot, his hood 1.64 blocks back on the pale fur at the rug's head, his antler tips 2.36 back,
     * 0.24 off the north wall, and his cloak's hem 2.36 ahead, about a block short of the south wall. */
    static final Spot BED = new Spot(-11.5D, -27.4D, 0.0F);
    /** The moose's spot lying in its stall (it reaches 4.06 blocks ahead to its muzzle and 2.59 behind, 2.61 either side
     * with its antlers), facing south, the barn's door. */
    static final Spot STALL = new Spot(26.0D, -12.9D, 0.0F);

    /** One of his things lying in front of his door: what, where, which way, how big. */
    record Gear(String what, Spot at, float scale)
    {
    }

    /** His door is the lodge's front at z -5 (the doorway x -1 to 1); his gear lies on the snow before it (the bow 2.8
     * blocks tip to tip, along the lodge's front; an arrow 1.8 long; the bola about half a block). */
    static final List<Gear> GEAR = List.of(
            new Gear("spear", new Spot(2.5D, -3.6D, 180.0F), 1.0F),
            new Gear("bow", new Spot(-3.0D, -3.4D, 5.0F), 1.0F),
            new Gear("bola", new Spot(-1.9D, -2.0D, 35.0F), 1.0F),
            new Gear("arrow", new Spot(1.6D, -2.8D, 100.0F), 1.0F),
            new Gear("arrow", new Spot(2.7D, -1.7D, 150.0F), 1.0F),
            new Gear("arrow", new Spot(-2.6D, -1.4D, 20.0F), 1.0F),
            new Gear("arrow", new Spot(0.7D, -3.4D, 65.0F), 1.0F));
    /** His leaning spear's lean back against the lodge's front (degrees). */
    static final float SPEAR_LEAN = 14.0F;
    /** The tag on his gear's displays. */
    public static final String GEAR_TAG = "dross_hunt_gear";
    /** His gear is looked for this far round his door (blocks). */
    private static final double GEAR_REACH = 8.0D;

    private HuntsmanAsleep() {}

    // ---------------------------------------------------------------- where

    /** {@code spot} in the world: across the ground (y: the top of what's there to lie on, looked for round the spot's
     * own zone's level). */
    static Vec3 at(ServerLevel level, RimefallVillages.VillagePlan plan, Spot spot)
    {
        HunterVillageDesigns.Design d = plan.design();
        BlockPos home = plan.home();
        BlockPos base = local(plan, 0, 0);
        BlockPos ex = local(plan, 1, 0).subtract(base);
        BlockPos ez = local(plan, 0, 1).subtract(base);
        double ox = spot.x() - 0.5D;
        double oz = spot.z() - 0.5D;
        double x = base.getX() + 0.5D + ex.getX() * ox + ez.getX() * oz;
        double z = base.getZ() + 0.5D + ex.getZ() * ox + ez.getZ() * oz;
        int level0 = plan.levelAt(true, d.homeX() + (int) Math.floor(spot.x()), d.homeZ() + (int) Math.floor(spot.z()));
        int y0 = level0 == HunterVillageZones.NONE ? home.getY() : level0 + 1;
        return new Vec3(x, ground(level, x, z, y0), z);
    }

    /** {@code spot}'s way in the world (degrees, the game's yaw). */
    static float yaw(RimefallVillages.VillagePlan plan, Spot spot)
    {
        BlockPos base = local(plan, 0, 0);
        BlockPos ex = local(plan, 1, 0).subtract(base);
        BlockPos ez = local(plan, 0, 1).subtract(base);
        double t = Math.toRadians(spot.yaw());
        double dx = -Math.sin(t);
        double dz = Math.cos(t);
        double wx = ex.getX() * dx + ez.getX() * dz;
        double wz = ex.getZ() * dx + ez.getZ() * dz;
        return (float) Math.toDegrees(Math.atan2(-wx, wz));
    }

    /** A block of the village's templates from his yard's block, in the world (its height not used). */
    static BlockPos local(RimefallVillages.VillagePlan plan, int dx, int dz)
    {
        HunterVillageDesigns.Design d = plan.design();
        return HunterVillages.toWorld(new BlockPos(d.homeX() + dx, d.groundLayer() + 1, d.homeZ() + dz), plan.ground(),
                plan.front(), d);
    }

    /** The top of what's there to stand or lie on at (x, z), looked for from a little over {@code near} down. */
    private static double ground(ServerLevel level, double x, double z, int near)
    {
        for (int y = near + 2; y >= near - 3; y--)
        {
            BlockPos pos = BlockPos.containing(x, y, z);
            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty())
            {
                return y + shape.max(Direction.Axis.Y);
            }
        }
        return near;
    }

    // ---------------------------------------------------------------- asleep, and awake

    /** Brings the village a new pair asleep (him on his bed, the moose in its stall), his gear before his door. Null if
     * their spots aren't loaded yet or they couldn't be made. */
    @Nullable
    static Huntsman bring(ServerLevel dross, RimefallVillages.VillagePlan plan, HuntsmanVillageData data)
    {
        Vec3 bed = at(dross, plan, BED);
        Vec3 stall = at(dross, plan, STALL);
        if (!dross.isPositionEntityTicking(BlockPos.containing(bed)) || !dross.isPositionEntityTicking(BlockPos.containing(stall)))
        {
            return null;                                  // (wait until both are loaded with their entities)
        }
        Huntsman h = HuntsmanPair.spawnAsleep(dross, bed, yaw(plan, BED), stall, yaw(plan, STALL));
        if (h == null)
        {
            return null;
        }
        int generation = data.spawned(plan.id(), h.getUUID());
        List<BlockPos> loop = HuntsmanVillages.patrolLoop(plan);
        h.setHome(plan.id(), plan.home(), generation);
        h.setPatrol(loop, 0);
        if (h.partnerMob() instanceof AlphaMoose moose)
        {
            moose.setHome(plan.id(), plan.home(), generation);
            moose.setPatrol(loop, 0);
        }
        clearGear(dross, plan);
        layGear(dross, plan);
        LOGGER.info("Hunter villages: the Huntsman sleeps in his lodge at {}", plan.ground().toShortString());
        return h;
    }

    /** Wakes a sleeping pair onto their patrol (once: they never go back to sleep), and takes his gear away. False if
     * there's no room on the loop yet or his moose isn't there. */
    static boolean wake(ServerLevel dross, RimefallVillages.VillagePlan plan, Huntsman h)
    {
        HuntsmanVillages.Start start = HuntsmanVillages.patrolStart(dross, plan);
        if (start == null || !HuntsmanPair.wake(h, start.at(), start.yaw()))
        {
            return false;
        }
        h.setPatrol(start.loop(), start.next());
        if (h.getVehicle() instanceof AlphaMoose moose)
        {
            moose.setPatrol(start.loop(), start.next());
        }
        clearGear(dross, plan);
        LOGGER.info("Hunter villages: the Huntsman woke and rides his patrol at {}", plan.ground().toShortString());
        return true;
    }

    // ---------------------------------------------------------------- his gear before his door

    /** His gear's displays lying before his door. */
    static void layGear(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        for (Gear g : GEAR)
        {
            Vec3 at = at(dross, plan, g.at());
            float yaw = yaw(plan, g.at());
            CompoundTag tag = g.what().equals("spear") ? spear(g.scale()) : lying(block(g.what()), g.scale());
            ListTag tags = new ListTag();
            tags.add(StringTag.valueOf(GEAR_TAG));
            tag.put("Tags", tags);
            Entity e = EntityType.loadEntityRecursive(tag, dross, d ->
            {
                d.moveTo(at.x, at.y, at.z, yaw, 0.0F);
                return d;
            });
            if (e != null)
            {
                dross.addFreshEntity(e);
            }
        }
    }

    /** His gear's displays round his door taken away. */
    static void clearGear(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        Vec3 door = at(dross, plan, new Spot(0.5D, -3.0D, 0.0F));
        for (Display d : dross.getEntitiesOfClass(Display.class, new AABB(door, door).inflate(GEAR_REACH),
                d -> d.getTags().contains(GEAR_TAG)))
        {
            d.discard();
        }
    }

    private static BlockState block(String what)
    {
        return switch (what)
        {
            case "bow" -> ModBlocks.HUNT_BOW.get().defaultBlockState();
            case "bola" -> ModBlocks.HUNT_BOLA.get().defaultBlockState();
            default -> ModBlocks.HUNT_ARROW.get().defaultBlockState();
        };
    }

    /** One of his things (its model lies flat on its block's floor, its middle over the block's middle) lying on the
     * snow, a little over it (no flicker), turned by the display's yaw. */
    private static CompoundTag lying(BlockState state, float scale)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        tag.put("transformation", shape(new Vector3f(-0.5F * scale, 0.01F, -0.5F * scale), new Vector3f(scale, scale, scale),
                new Quaternionf()));
        return tag;
    }

    /** His spear (the planted spear's block) standing on the snow, leaning back against the lodge's front: the display
     * faces the lodge (its yaw), and the spear's top leans that way. */
    private static CompoundTag spear(float scale)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(ModBlocks.PLANTED_SPEAR.get().defaultBlockState()));
        Quaternionf lean = new Quaternionf().rotationX((float) Math.toRadians(SPEAR_LEAN));
        Vector3f foot = lean.transform(new Vector3f(0.5F * scale, 0.0F, 0.5F * scale));
        tag.put("transformation", shape(new Vector3f(-foot.x, -foot.y, -foot.z), new Vector3f(scale, scale, scale), lean));
        return tag;
    }

    private static CompoundTag shape(Vector3f translation, Vector3f scale, Quaternionf left)
    {
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(translation.x, translation.y, translation.z));
        t.put("scale", floats(scale.x, scale.y, scale.z));
        t.put("left_rotation", floats(left.x, left.y, left.z, left.w));
        t.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        return t;
    }

    private static ListTag floats(float... values)
    {
        ListTag list = new ListTag();
        for (float v : values)
        {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }
}
