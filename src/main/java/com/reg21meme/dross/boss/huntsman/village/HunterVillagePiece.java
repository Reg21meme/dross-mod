package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.dimension.rimefall.RimefallTuning;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * column's own ground, so the chunks agree whatever order they're made in:
 * <ul>
 *   <li><b>The ground</b>: under the village it's cut and filled to the plan's level (the median of the ground there);
 *       round it, over {@link RimefallTuning#VILLAGE_BLEND} blocks, a column d blocks out is never more than d above or
 *       below that level (a staircase back to the land's own, at most a block a step, like the Overworld's cathedral).
 *       A column keeps its own top block (snow, Hoarfrost, packed ice) and what's under it.</li>
 *   <li><b>The village</b>: its template ({@link HunterVillages}: the courtyards dug three down, the igloos, the pens and
 *       their animals), then its decorations, each kept with the village's own 1 in 2 roll (the same for every chunk),
 *       both cut to the chunk.</li>
 * </ul>
 * The Huntsman isn't built in: he comes when a player is near ({@code HuntsmanVillages}).
 */
public final class HunterVillagePiece extends StructurePiece
{
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** The piece reaches this far under and over the village's level (its ground cut or filled at most
     * {@link RimefallTuning#VILLAGE_MAX_STEP_RELAXED}, its blend ring no more than its width). */
    private static final int BELOW = RimefallTuning.VILLAGE_MAX_STEP_RELAXED + RimefallTuning.VILLAGE_BLEND + 8;
    private static final int ABOVE = 48;

    private final RimefallVillages.VillagePlan plan;

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
        int y = plan.ground().getY();
        return new BoundingBox(cover.minX(), Math.min(cover.minY(), y - BELOW), cover.minZ(), cover.maxX(),
                Math.max(cover.maxY(), y + ABOVE), cover.maxZ());
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
    }

    /** The ground under the village and round it, in the chunk's part of it. */
    private void level(WorldGenLevel level, BoundingBox box)
    {
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        int lv = this.plan.ground().getY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                double out = this.plan.outside(x, z);
                if (out > RimefallTuning.VILLAGE_BLEND)
                {
                    continue;
                }
                int natural = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int reach = Mth.ceil(out);
                int target = out <= 0.0D ? lv : Mth.clamp(natural, lv - reach, lv + reach);
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

    /** The village's templates, cut to the chunk. */
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
        StructurePlaceSettings settings = HunterVillages.settings(d, this.plan.front(), true).setBoundingBox(box);
        village.get().placeInWorld(level, origin, origin, settings, random, flags);
        boolean[] keep = HunterVillages.roll(d, RandomSource.create(this.plan.seed()), HunterVillages.DECOR_CHANCE);
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
                .addProcessor(new HunterDecorFilter(dropped));
        decor.get().placeInWorld(level, origin, origin, withDecor, random, flags);
    }
}
