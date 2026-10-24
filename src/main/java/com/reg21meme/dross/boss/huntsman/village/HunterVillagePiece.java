package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.dimension.rimefall.RimefallTuning;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a hunter village ({@link RimefallVillages.VillagePlan}) a chunk at a time, each column from the plan and that
 * column's own ground, so the chunks agree whatever order they're made in. Each part of it stands on its own ground
 * (the user, October 23, 2026: "don't destroy everything around: if there are hills or trees that don't interfere ...
 * then that's fine"; until then the whole village stood on one level with a 16-block ring of land blended round it):
 * <ul>
 *   <li><b>The ground</b>: under each zone ({@link HunterVillageZones}: a courtyard with its igloos, a lane, the lodge, a
 *       pen, a decoration kept on the open ground...) it's cut and filled to the zone's level; round it, a column d
 *       blocks out is never more than d above or below that level, out to {@link RimefallTuning#VILLAGE_SOFT_EDGE} (a
 *       staircase back to the land, at most a block a step, only where the land is that far off). Everywhere else the
 *       world's own land is left as it is. A column keeps its own top block (snow, Hoarfrost, packed ice) and what's
 *       under it.</li>
 *   <li><b>The village</b>: its template (the courtyards dug three down, the igloos, the pens and their animals), then
 *       its decorations, each kept with the village's own 1 in 2 roll (the same for every chunk), both cut to the chunk
 *       and put on their zones' levels by {@link HunterTerrace}.</li>
 *   <li><b>The lanes' steps</b>: where a lane's (or a passage's) floor steps up a block on its way between two zones,
 *       a stair.</li>
 * </ul>
 * The Huntsman isn't built in: he comes when a player is near ({@code HuntsmanVillages}).
 */
public final class HunterVillagePiece extends StructurePiece
{
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STEP = Blocks.SPRUCE_STAIRS.defaultBlockState();
    /** The piece reaches this far under its lowest level and over its highest (a zone cut or filled at most
     * {@link RimefallTuning#VILLAGE_ZONE_MAX_STEP_RELAXED}, the template's own depth and height). */
    private static final int BELOW = RimefallTuning.VILLAGE_ZONE_MAX_STEP_RELAXED + RimefallTuning.VILLAGE_SOFT_EDGE + 8;
    private static final int ABOVE = 48;
    /** The columns round a column, nearest first, out to the soft edge: {dx, dz, ceil(distance)}. */
    private static final int[][] AROUND = around(RimefallTuning.VILLAGE_SOFT_EDGE);

    private final RimefallVillages.VillagePlan plan;
    /** Which decorations it keeps (its own roll), worked out the first time. */
    private boolean[] keep;

    HunterVillagePiece(RimefallVillages.VillagePlan plan)
    {
        super(ModStructures.HUNTER_VILLAGE_PIECE.get(), 0, box(plan));
        this.plan = plan;
    }

    public HunterVillagePiece(CompoundTag tag)
    {
        super(ModStructures.HUNTER_VILLAGE_PIECE.get(), tag);
        this.plan = RimefallVillages.VillagePlan.load(tag.getCompound("Plan"));
    }

    private static BoundingBox box(RimefallVillages.VillagePlan plan)
    {
        BoundingBox cover = plan.coverBox(0);
        int low = plan.ground().getY();
        int high = low;
        if (plan.levels() != null)
        {
            for (int l : plan.levels())
            {
                if (l != HunterVillageZones.NONE)
                {
                    low = Math.min(low, l);
                    high = Math.max(high, l);
                }
            }
        }
        return new BoundingBox(cover.minX(), Math.min(cover.minY(), low - BELOW), cover.minZ(), cover.maxX(),
                Math.max(cover.maxY(), high + ABOVE), cover.maxZ());
    }

    private static int[][] around(int reach)
    {
        List<int[]> out = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                double d = Math.sqrt(dx * dx + dz * dz);
                if ((dx != 0 || dz != 0) && d <= reach)
                {
                    out.add(new int[] {dx, dz, Mth.ceil(d)});
                }
            }
        }
        out.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        return out.toArray(new int[0][]);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.put("Plan", this.plan.save());
    }

    public RimefallVillages.VillagePlan plan()
    {
        return this.plan;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot)
    {
        this.level(level, box);
        this.placeVillage(level, box, chunk);
        this.steps(level, box);
    }

    /** Which of its decorations the village keeps (the same in every chunk: from its own seed). */
    private boolean[] keep()
    {
        if (this.keep == null)
        {
            this.keep = HunterVillages.roll(this.plan.design(), RandomSource.create(this.plan.seed()), HunterVillages.DECOR_CHANCE);
        }
        return this.keep;
    }

    /** The level a template column's ground goes to (its zone's, or a kept decoration's box's on the open ground), or
     * {@link HunterVillageZones#NONE}: the world's own. */
    private int groundLevel(HunterVillageZones.Layout zones, int lx, int lz)
    {
        int zone = zones.zone(false, lx, lz);
        if (zone >= 0)
        {
            return zones.get(zone).kind == HunterVillageZones.Kind.COLUMN ? HunterVillageZones.NONE
                    : zones.level(this.plan.levels(), zone, lx, lz);
        }
        int decoration = zones.decorationAt(lx, lz);
        boolean[] keep = this.keep();
        if (decoration >= 0 && decoration < keep.length && keep[decoration])
        {
            int dz = zones.decorZone(decoration);
            if (dz >= 0 && zones.get(dz).decor == decoration)
            {
                return this.plan.levels()[dz];
            }
        }
        return HunterVillageZones.NONE;
    }

    /** The ground under each zone and round it, in the chunk's part of it. */
    private void level(WorldGenLevel level, BoundingBox box)
    {
        HunterVillageZones.Layout zones = this.plan.zones();
        if (zones == null || this.plan.levels() == null)
        {
            return;
        }
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                BlockPos local = this.plan.toLocal(x, z);
                int natural = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int target = this.groundLevel(zones, local.getX(), local.getZ());
                if (target == HunterVillageZones.NONE)
                {
                    target = natural;
                    for (int[] o : AROUND)
                    {
                        int near = this.groundLevel(zones, local.getX() + o[0], local.getZ() + o[1]);
                        if (near != HunterVillageZones.NONE)
                        {
                            target = Mth.clamp(natural, near - o[2], near + o[2]);
                            break;
                        }
                    }
                }
                if (target == natural)
                {
                    continue;
                }
                BlockState top = level.getBlockState(pos.set(x, natural, z));
                BlockState under = level.getBlockState(pos.set(x, natural - 1, z));
                if (top.isAir() || !top.getFluidState().isEmpty())
                {
                    continue;                   // (nothing to copy: never happens on Rimefall's ground)
                }
                if (under.isAir() || !under.getFluidState().isEmpty())
                {
                    under = top;
                }
                if (target < natural)
                {
                    for (int y = natural; y > target; y--)
                    {
                        level.setBlock(pos.set(x, y, z), AIR, Block.UPDATE_CLIENTS);
                    }
                }
                else
                {
                    for (int y = natural; y < target; y++)
                    {
                        level.setBlock(pos.set(x, y, z), under, Block.UPDATE_CLIENTS);
                    }
                }
                level.setBlock(pos.set(x, target, z), top, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The village's templates, cut to the chunk, each part on its zone's level. */
    private void placeVillage(WorldGenLevel level, BoundingBox box, ChunkPos chunk)
    {
        HunterVillageDesigns.Design d = this.plan.design();
        var manager = level.getLevel().getStructureManager();
        Optional<StructureTemplate> village = manager.get(d.template());
        Optional<StructureTemplate> decor = manager.get(d.decorTemplate());
        if (village.isEmpty() || decor.isEmpty())
        {
            return;
        }
        BlockPos origin = HunterVillages.origin(this.plan.ground(), d);
        RandomSource random = RandomSource.create(this.plan.seed() ^ chunk.toLong());
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        StructurePlaceSettings settings = HunterVillages.settings(d, this.plan.front(), true).setBoundingBox(box)
                .addProcessor(new HunterTerrace(this.plan, false));
        village.get().placeInWorld(level, origin, origin, settings, random, flags);
        boolean[] keep = this.keep();
        List<BoundingBox> boxes = d.decorations();
        List<BoundingBox> dropped = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++)
        {
            if (i >= keep.length || !keep[i])
            {
                dropped.add(boxes.get(i));
            }
        }
        StructurePlaceSettings withDecor = HunterVillages.settings(d, this.plan.front()).setBoundingBox(box)
                .addProcessor(new HunterDecorFilter(dropped)).addProcessor(new HunterTerrace(this.plan, true));
        decor.get().placeInWorld(level, origin, origin, withDecor, random, flags);
    }

    /** A stair wherever a lane's (or a passage's) floor steps up a block, in the chunk's part of the village. */
    private void steps(WorldGenLevel level, BoundingBox box)
    {
        HunterVillageZones.Layout zones = this.plan.zones();
        int[] levels = this.plan.levels();
        if (zones == null || levels == null)
        {
            return;
        }
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        Direction[] around = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        var rotation = HunterVillages.rotationFor(this.plan.front());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                BlockPos local = this.plan.toLocal(x, z);
                int zone = zones.zone(false, local.getX(), local.getZ());
                if (zone < 0 || zones.get(zone).kind != HunterVillageZones.Kind.LANE)
                {
                    continue;
                }
                HunterVillageZones.Zone lane = zones.get(zone);
                int here = zones.level(levels, zone, local.getX(), local.getZ());
                double ux = lane.toX - lane.fromX;
                double uz = lane.toZ - lane.fromZ;
                Direction up = null;
                double best = -1.0D;
                for (Direction dir : around)
                {
                    int nx = local.getX() + dir.getStepX();
                    int nz = local.getZ() + dir.getStepZ();
                    if (zones.zone(false, nx, nz) != zone || zones.level(levels, zone, nx, nz) != here + 1)
                    {
                        continue;
                    }
                    double along = Math.abs(dir.getStepX() * ux + dir.getStepZ() * uz);
                    if (along > best)
                    {
                        best = along;
                        up = dir;
                    }
                }
                if (up == null)
                {
                    continue;
                }
                int y = here + lane.floor - zones.groundLayer;
                if (!level.getBlockState(pos.set(x, y, z)).isAir() || !level.getBlockState(pos.set(x, y + 1, z)).isAir()
                        || !level.getBlockState(pos.set(x, y - 1, z)).isFaceSturdy(level, pos, Direction.UP))
                {
                    continue;                   // (not its open floor: a wall, a bank, a lamp's foot)
                }
                level.setBlock(pos.set(x, y, z), STEP.setValue(StairBlock.FACING, rotation.rotate(up)), Block.UPDATE_CLIENTS);
            }
        }
    }
}
