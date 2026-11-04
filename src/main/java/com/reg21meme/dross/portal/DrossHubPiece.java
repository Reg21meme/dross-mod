package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.portal.camp.HubCamp;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModStructures;
import com.reg21meme.dross.villager.SurvivorSpawner;
import com.reg21meme.dross.world.shrine.GroundFit;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Builds the hub ({@link DrossHubStructure}) a chunk at a time, with the land, each column from the plan and that
 * column's own ground, so the chunks agree whatever order they're made in. In each chunk, only its own part:
 * <ol>
 *   <li><b>The ground</b> ({@link DrossHubGround#target}): under the footprint cut or filled to the hub's ground level, round
 *       it a staircase back to the land, at most a block a step, {@value DrossHub#HUB_BLEND_MARGIN} blocks wide. Each
 *       column keeps its own top block and the soil under it (deep new ground is the Dross's base stone), and water or
 *       lava beside a cut is walled in (there's none at this stage: the Dross has no seas, and its ponds keep away).</li>
 *   <li><b>The Shattered Spire's</b> template, then the survivors' <b>camp</b>'s ({@link HubCamp}, at the same corner and
 *       turn), cut to the chunk; their blocks as saved (a known shape: nothing reacts to a neighbour not built yet).</li>
 *   <li><b>The exit portal</b>: portal blocks in its frame's opening. World generation sets them without block updates, so
 *       half a portal (its frame crosses a chunk border) doesn't go out before the other half is built; a chunk never goes
 *       live before its neighbours are decorated.</li>
 *   <li><b>The survivors</b>, each in the chunk that holds its rest spot ({@link SurvivorSpawner#spawnWithLand}).</li>
 * </ol>
 * Saved with its ground level alone (everything else follows from it).
 */
public final class DrossHubPiece extends StructurePiece
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** World generation's flags: tell clients, never react to neighbours (the jigsaw structures' way). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** New ground this many blocks under the top (or less) is the column's own soil; deeper, the Dross's base stone. */
    private static final int SOIL_DEPTH = 3;
    /** The piece reaches this far under the hub's ground (a dip filled up to it) and this far over the spire's top. */
    private static final int BELOW = 64;
    private static final int ABOVE = 4;

    /** How long each chunk's part took: for the log and the tests. */
    private static final AtomicInteger CHUNKS = new AtomicInteger();
    private static final AtomicLong TOTAL_NANOS = new AtomicLong();
    private static final AtomicLong SLOWEST_NANOS = new AtomicLong();

    private final int groundY;

    DrossHubPiece(int groundY)
    {
        super(ModStructures.DROSS_HUB_PIECE.get(), 0, box(groundY));
        this.groundY = groundY;
    }

    public DrossHubPiece(CompoundTag tag)
    {
        super(ModStructures.DROSS_HUB_PIECE.get(), tag);
        this.groundY = tag.getInt("GroundY");
    }

    /** The footprint and the staircase round it, from {@value #BELOW} under the ground to {@value #ABOVE} over the spire. */
    private static BoundingBox box(int groundY)
    {
        BoundingBox columns = DrossHub.columns().inflatedBy(DrossHub.HUB_BLEND_MARGIN);
        BoundingBox spire = DrossHub.layoutAt(groundY).footprint();
        return new BoundingBox(columns.minX(), groundY - BELOW, columns.minZ(), columns.maxX(), spire.maxY() + ABOVE, columns.maxZ());
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        tag.putInt("GroundY", this.groundY);
    }

    /** {chunks built, total nanoseconds, the slowest one chunk's nanoseconds} since the server started (for the tests). */
    public static long[] timing()
    {
        return new long[] {CHUNKS.get(), TOTAL_NANOS.get(), SLOWEST_NANOS.get()};
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pivot)
    {
        long start = System.nanoTime();
        DrossHub.Layout layout = DrossHub.layoutAt(this.groundY);
        this.ground(level, box);
        ShrineDesign spire = DrossHub.spire();
        BlockPos center = DrossHub.spireCenter(this.groundY);
        Rotation turn = HubCamp.rotation(DrossHub.SPIRE_FACING);
        if (box.intersects(layout.footprint())) // (a chunk of the staircase ring alone has none of their blocks)
        {
            place(level, box, chunk, spire.template(), center.offset(-spire.width() / 2, -spire.groundLayer(), -spire.depth() / 2),
                    turn, new BlockPos(spire.width() / 2, 0, spire.depth() / 2));
            place(level, box, chunk, new ResourceLocation(Dross.MODID, HubCamp.hubCamp().template()), HubCamp.origin(center), turn,
                    HubCamp.pivot());
        }
        portal(level, box, layout.exit());
        SurvivorSpawner.spawnWithLand(level, layout, box);
        long took = System.nanoTime() - start;
        int done = CHUNKS.incrementAndGet();
        TOTAL_NANOS.addAndGet(took);
        if (took > SLOWEST_NANOS.getAndAccumulate(took, Math::max))
        {
            LOGGER.info("Dross hub: built its part in chunk {}, {} in {} ms (the slowest yet; {} of its chunks built)", chunk.x, chunk.z,
                    took / 1_000_000L, done);
        }
    }

    /** The ground in the chunk's part of the piece: cut or filled to each column's target, its own top block on it. */
    private void ground(WorldGenLevel level, BoundingBox box)
    {
        BoundingBox mine = this.getBoundingBox();
        int x0 = Math.max(box.minX(), mine.minX());
        int x1 = Math.min(box.maxX(), mine.maxX());
        int z0 = Math.max(box.minZ(), mine.minZ());
        int z1 = Math.min(box.maxZ(), mine.maxZ());
        BlockState deep = GroundFit.deepFillBlock(level.getLevel());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                // the land's own top as the terrain made it (world generation's height map: nothing before this changed it)
                int natural = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                int target = DrossHubGround.target(x, z, natural, this.groundY);
                if (target == natural)
                {
                    continue;
                }
                BlockState top = level.getBlockState(pos.set(x, natural, z));
                if (top.isAir() || !top.getFluidState().isEmpty())
                {
                    continue; // (nothing to copy: never happens on the Dross's ground)
                }
                if (top.hasProperty(BlockStateProperties.SNOWY))
                {
                    top = top.setValue(BlockStateProperties.SNOWY, false);
                }
                BlockState under = level.getBlockState(pos.set(x, natural - 1, z));
                if (under.isAir() || !under.getFluidState().isEmpty())
                {
                    under = deep;
                }
                if (target < natural)
                {
                    for (int y = natural; y > target; y--)
                    {
                        level.setBlock(pos.set(x, y, z), AIR, FLAGS);
                        seal(level, box, pos, deep);
                    }
                    // a deep cut: soil under the new top, as a column has
                    for (int y = target - 1; y >= target - (SOIL_DEPTH - 1); y--)
                    {
                        if (level.getBlockState(pos.set(x, y, z)).equals(deep))
                        {
                            level.setBlock(pos, under, FLAGS);
                        }
                    }
                }
                else
                {
                    for (int y = natural; y < target; y++)
                    {
                        level.setBlock(pos.set(x, y, z), target - y <= SOIL_DEPTH ? under : deep, FLAGS);
                    }
                }
                level.setBlock(pos.set(x, target, z), top, FLAGS);
            }
        }
    }

    /** Water or lava in the chunk beside a cleared cell becomes base stone, so nothing pours into the cut. */
    private static void seal(WorldGenLevel level, BoundingBox box, BlockPos.MutableBlockPos cleared, BlockState deep)
    {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (Direction dir : Direction.Plane.HORIZONTAL)
        {
            side.setWithOffset(cleared, dir);
            if (box.isInside(side) && !level.getFluidState(side).isEmpty())
            {
                level.setBlock(side, deep, FLAGS);
            }
        }
    }

    /**
     * A template at {@code origin}, turned round {@code pivot}, cut to the chunk; its blocks exactly as saved (a known
     * shape). A missing template is an error in the log (the hub's ship in the mod).
     */
    private static void place(WorldGenLevel level, BoundingBox box, ChunkPos chunk, ResourceLocation id, BlockPos origin,
                              Rotation turn, BlockPos pivot)
    {
        Optional<StructureTemplate> template = level.getLevel().getStructureManager().get(id);
        if (template.isEmpty())
        {
            LOGGER.error("Dross hub: its template {} is missing (data/{}/structures/{}.nbt), so chunk {}, {} of the hub is built without it",
                    id, id.getNamespace(), id.getPath(), chunk.x, chunk.z);
            return;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(turn)
                .setRotationPivot(pivot)
                .setIgnoreEntities(true)
                .setKnownShape(true)
                .setBoundingBox(box);
        template.get().placeInWorld(level, origin, origin, settings, RandomSource.create(chunk.toLong()), FLAGS);
    }

    /** The exit portal's blocks in its frame's opening, the chunk's part of it (only into air). */
    private static void portal(WorldGenLevel level, BoundingBox box, PortalOpening exit)
    {
        BlockState portal = ModBlocks.DROSS_PORTAL.get().defaultBlockState().setValue(DrossPortalBlock.AXIS, exit.axis());
        boolean alongX = exit.axis() == Direction.Axis.X;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < exit.width(); i++)
        {
            for (int j = 0; j < exit.height(); j++)
            {
                pos.set(exit.minCorner().getX() + (alongX ? i : 0), exit.minCorner().getY() + j, exit.minCorner().getZ() + (alongX ? 0 : i));
                if (box.isInside(pos) && level.getBlockState(pos).isAir())
                {
                    level.setBlock(pos, portal, FLAGS);
                }
            }
        }
    }
}
