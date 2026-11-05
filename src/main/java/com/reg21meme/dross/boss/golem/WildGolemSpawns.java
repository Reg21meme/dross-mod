package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Predicate;

/**
 * Where stone and lava golems may spawn by themselves (the Cinder Wastes' spawn list, in the biome's JSON, says that
 * they spawn there and how often). Minecraft asks these rules before every natural spawn of one, so they are kept cheap.
 * <ul>
 *   <li>Both stand on the ground: on a solid block a mob may spawn on (basalt, blackstone, ash, magma...), with room
 *       for them, never in water or lava, and never on Peaceful.</li>
 *   <li>Light doesn't matter (a monster normally only spawns in the dark, but these fire golems belong in the lava-lit
 *       wastes; {@link GolemMinion#getWalkTargetValue} covers the other half of the light check).</li>
 *   <li>A lava golem also needs lava near it: within {@link ColossusTuning#WILD_LAVA_SEARCH_SIDEWAYS} blocks sideways
 *       and {@link ColossusTuning#WILD_LAVA_SEARCH_UP_DOWN} up or down. Far from lava a lava golem's spawn just fails,
 *       so away from lava every golem is a stone golem. (A monster spawner skips the lava rule.)</li>
 * </ul>
 * Runs on the mod event bus.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WildGolemSpawns
{
    /** A block that is (or holds) lava. */
    private static final Predicate<BlockState> LAVA = state -> state.getFluidState().is(FluidTags.LAVA);
    /** A magma block (counted like lava inside a hermit crab cave). */
    private static final Predicate<BlockState> MAGMA = state -> state.is(Blocks.MAGMA_BLOCK);

    private WildGolemSpawns() {}

    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event)
    {
        event.register(ModEntities.STONE_GOLEM.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WildGolemSpawns::checkStoneGolemSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntities.LAVA_GOLEM.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                WildGolemSpawns::checkLavaGolemSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /** A stone golem: on the ground, in any light, not in a fluid, not on Peaceful. */
    public static boolean checkStoneGolemSpawn(EntityType<StoneGolem> type, ServerLevelAccessor level, MobSpawnType reason,
                                               BlockPos pos, RandomSource random)
    {
        return onDryGround(type, level, reason, pos, random);
    }

    /**
     * A lava golem: like a stone golem, and only with lava near it; inside a hermit crab cave, magma counts like lava (the
     * user, November 5, 2026: "count magma as a part a lava golem can spawn in, in the hermit cave").
     */
    public static boolean checkLavaGolemSpawn(EntityType<LavaGolem> type, ServerLevelAccessor level, MobSpawnType reason,
                                              BlockPos pos, RandomSource random)
    {
        return onDryGround(type, level, reason, pos, random) && (reason == MobSpawnType.SPAWNER || isLavaNear(level, pos)
                || (inHermitCave(level, pos) && isNear(level, pos, MAGMA)));
    }

    /** True if {@code pos} is inside a hermit crab cave (a piece of the boss area's {@code dross:hermit_cave} structure). */
    public static boolean inHermitCave(ServerLevelAccessor level, BlockPos pos)
    {
        return level.getLevel().structureManager().getStructureWithPieceAt(pos, HERMIT_CAVE).isValid();
    }

    /** The hermit crab caves' structure ({@code data/dross/worldgen/structure/hermit_cave.json}). */
    private static final ResourceKey<Structure> HERMIT_CAVE = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation(Dross.MODID, "hermit_cave"));

    /**
     * Vanilla's rule for monsters that ignore light (not Peaceful, standing on a block a mob may spawn on; a spawner
     * skips the ground), and no water or lava where it stands.
     */
    private static boolean onDryGround(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason,
                                       BlockPos pos, RandomSource random)
    {
        return Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random) && level.getFluidState(pos).isEmpty();
    }

    /**
     * True if there's lava within {@link ColossusTuning#WILD_LAVA_SEARCH_SIDEWAYS} blocks sideways and
     * {@link ColossusTuning#WILD_LAVA_SEARCH_UP_DOWN} blocks up or down of {@code pos}. Cheap: each 16 x 16 x 16 piece of
     * a chunk keeps a list of the blocks in it, and a piece whose list has no lava is skipped without looking at its
     * blocks (most have none). Chunks that aren't loaded count as no lava (nothing is loaded for this).
     */
    public static boolean isLavaNear(LevelReader level, BlockPos pos)
    {
        return isNear(level, pos, LAVA);
    }

    /** Like {@link #isLavaNear}, for any kind of block. */
    public static boolean isNear(LevelReader level, BlockPos pos, Predicate<BlockState> what)
    {
        int sideways = ColossusTuning.WILD_LAVA_SEARCH_SIDEWAYS;
        int upDown = ColossusTuning.WILD_LAVA_SEARCH_UP_DOWN;
        int minX = pos.getX() - sideways;
        int maxX = pos.getX() + sideways;
        int minZ = pos.getZ() - sideways;
        int maxZ = pos.getZ() + sideways;
        int minY = Math.max(level.getMinBuildHeight(), pos.getY() - upDown);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, pos.getY() + upDown);
        if (minY > maxY)
        {
            return false;
        }
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++)
        {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++)
            {
                ChunkAccess chunk = level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null)
                {
                    continue;
                }
                for (int sectionY = SectionPos.blockToSectionCoord(minY); sectionY <= SectionPos.blockToSectionCoord(maxY); sectionY++)
                {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
                    if (section.hasOnlyAir() || !section.maybeHas(what))
                    {
                        continue;
                    }
                    if (sectionHas(section, what, Math.max(minX, SectionPos.sectionToBlockCoord(chunkX)),
                            Math.min(maxX, SectionPos.sectionToBlockCoord(chunkX, 15)),
                            Math.max(minY, SectionPos.sectionToBlockCoord(sectionY)),
                            Math.min(maxY, SectionPos.sectionToBlockCoord(sectionY, 15)),
                            Math.max(minZ, SectionPos.sectionToBlockCoord(chunkZ)),
                            Math.min(maxZ, SectionPos.sectionToBlockCoord(chunkZ, 15))))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True if any block of this chunk piece inside the box (world coordinates, all inside the piece) is one of these. */
    private static boolean sectionHas(LevelChunkSection section, Predicate<BlockState> what, int minX, int maxX, int minY, int maxY,
                                      int minZ, int maxZ)
    {
        for (int y = minY; y <= maxY; y++)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    if (what.test(section.getBlockState(x & 15, y & 15, z & 15)))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
