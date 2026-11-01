package com.reg21meme.dross.boss.fire.cave;

import com.mojang.logging.LogUtils;
import java.util.Optional;
import com.reg21meme.dross.boss.cinder.CinderHermit;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.slf4j.Logger;

/**
 * Places one hermit crab cave ({@link HermitCaveDesigns}: a template each, made by tools/fire/hermit_caves.py, its way
 * in at the south) with its middle column on a ground block and its way in facing any way: the showcase uses it, and
 * the world's caves are placed the same way ({@link HermitCavePiece}). The template sets every block of the cave and of
 * its lining, so wherever it goes its lava is walled in by its own blocks; outside its lining the world is left alone.
 */
public final class HermitCaves
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private HermitCaves() {}

    /** The template, or empty (and an error in the log) if it's missing. */
    public static Optional<StructureTemplate> template(ServerLevel level, HermitCaveDesigns.Cave cave)
    {
        Optional<StructureTemplate> found = level.getStructureManager().get(cave.template());
        if (found.isEmpty())
        {
            LOGGER.error("Hermit crab cave template {} is missing", cave.template());
        }
        return found;
    }

    /**
     * Places it with its middle on {@code ground} (the ground block: the template's ground layer goes there), its way in
     * facing {@code front}, and has the game light it. Returns false if its template is missing.
     */
    public static boolean place(ServerLevel level, HermitCaveDesigns.Cave cave, BlockPos ground, Direction front)
    {
        Optional<StructureTemplate> found = template(level, cave);
        if (found.isEmpty())
        {
            return false;
        }
        BlockPos origin = origin(ground, cave);
        found.get().placeInWorld(level, origin, origin, settings(cave, front), level.getRandom(), Block.UPDATE_CLIENTS);
        relight(level, footprint(ground, front, cave));
        return true;
    }

    /** How the templates are placed: turned round their middle column to face {@code front}, no entities. */
    public static StructurePlaceSettings settings(HermitCaveDesigns.Cave cave, Direction front)
    {
        return new StructurePlaceSettings()
                .setRotation(rotationFor(front))
                .setRotationPivot(pivot(cave))
                .setIgnoreEntities(true);
    }

    /** The rotation that turns the template's south (its way in) to face {@code front}. */
    public static Rotation rotationFor(Direction front)
    {
        return switch (front)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** The box it fills when placed like {@link #place}. */
    public static BoundingBox footprint(BlockPos ground, Direction front, HermitCaveDesigns.Cave cave)
    {
        BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotationFor(front), pivot(cave));
        BlockPos b = StructureTemplate.transform(new BlockPos(cave.width() - 1, cave.layers() - 1, cave.depth() - 1),
                Mirror.NONE, rotationFor(front), pivot(cave));
        return BoundingBox.fromCorners(a.offset(origin(ground, cave)), b.offset(origin(ground, cave)));
    }

    /** Where a block of the template (its own coordinates, layer 0 at the bottom) ends up. */
    public static BlockPos toWorld(BlockPos local, BlockPos ground, Direction front, HermitCaveDesigns.Cave cave)
    {
        return StructureTemplate.transform(local, Mirror.NONE, rotationFor(front), pivot(cave)).offset(origin(ground, cave));
    }

    /** The cell you stand in to go in, in the world. */
    public static BlockPos door(BlockPos ground, Direction front, HermitCaveDesigns.Cave cave)
    {
        return toWorld(cave.door(), ground, front, cave);
    }

    /**
     * Its crab spots in the world: the cells a Cinder Hermit's feet stand in (its middle), each with a free box 7 wide,
     * 6 tall and 7 deep round it. (The hermits themselves come with their own mob: this is where to put them.)
     */
    public static java.util.List<BlockPos> crabSpots(BlockPos ground, Direction front, HermitCaveDesigns.Cave cave)
    {
        return cave.crabs().stream().map(spot -> toWorld(spot, ground, front, cave)).toList();
    }

    /**
     * Its Cinder Hermits: one at each of its crab spots ({@code spots}, in the world) inside {@code within} (the chunk
     * being made; null for all), each in a random shell (its own rule), facing any way, staying put (a cave's crabs
     * never despawn). Each one's other hermits come for whoever hurts it, as anywhere.
     */
    public static void spawnCrabs(ServerLevelAccessor level, java.util.List<BlockPos> spots, @javax.annotation.Nullable BoundingBox within)
    {
        for (BlockPos spot : spots)
        {
            if (within != null && !within.isInside(spot))
            {
                continue;
            }
            CinderHermit crab = ModEntities.CINDER_HERMIT.get().create(level.getLevel());
            if (crab == null)
            {
                continue;
            }
            crab.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
            crab.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.STRUCTURE, null, null);
            crab.setPersistenceRequired();
            level.addFreshEntityWithPassengers(crab);
        }
    }

    /** The template's corner (its first block) for a cave whose middle is on {@code ground}. */
    public static BlockPos origin(BlockPos ground, HermitCaveDesigns.Cave cave)
    {
        return ground.offset(-cave.width() / 2, -cave.groundLayer(), -cave.depth() / 2);
    }

    public static BlockPos pivot(HermitCaveDesigns.Cave cave)
    {
        return new BlockPos(cave.width() / 2, 0, cave.depth() / 2);
    }

    /** Has the game work out the light of every lamp, vent and pool of lava in the box. */
    static void relight(ServerLevel level, BoundingBox box)
    {
        LevelLightEngine light = level.getChunkSource().getLightEngine();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int y = box.minY(); y <= box.maxY(); y++)
                {
                    if (level.getBlockState(pos.set(x, y, z)).getLightEmission(level, pos) > 0)
                    {
                        light.checkBlock(pos);
                    }
                }
            }
        }
    }
}
