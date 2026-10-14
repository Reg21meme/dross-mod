package com.reg21meme.dross.boss.golem;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Keeps lava golems at about 1 in 5 of the wild golems round players in the Cinder Wastes
 * ({@link ColossusTuning#WILD_LAVA_GOLEM_SHARE}), still only next to lava.
 * <p>
 * Why: the biome's spawn list picks a lava golem 1 time in 5, but its spawn only works near lava (see
 * {@link WildGolemSpawns}), and lava pools are rare, so natural spawning alone gives far fewer. Natural spawning stays
 * as it is; this adds a few more.
 * <p>
 * How: every {@link ColossusTuning#WILD_TOP_UP_INTERVAL} ticks, for each player (not a spectator) standing in the Cinder
 * Wastes in the Dross, it counts the wild stone and lava golems within {@link ColossusTuning#WILD_TOP_UP_COUNT_RANGE}
 * blocks. If there are fewer lava golems than the share asks for (stone golems x 0.2 / 0.8, rounded to the nearest whole
 * number: 1 for 2 to 5 stone golems, 2 for 6 to 9...), it adds one wild lava golem next to the nearest lava within
 * {@link ColossusTuning#WILD_TOP_UP_LAVA_RANGE} blocks: a spot that passes every rule a natural lava golem spawn does
 * (on solid ground, not in a fluid, lava near, not on Peaceful, not inside the Colossus lair or the hub's safe zone,
 * room for its body), in the Cinder Wastes, at least {@link ColossusTuning#WILD_TOP_UP_PLAYER_GAP} blocks from every
 * player. No lava near enough, or no good spot: nothing happens. It never goes over the monster mob cap, and does
 * nothing when mob spawning is off.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class WildLavaGolemTopUp
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A block that is (or holds) lava. */
    private static final Predicate<BlockState> LAVA = state -> state.getFluidState().is(FluidTags.LAVA);
    /** Minecraft's mob cap is "so many per 17 x 17 chunks" round the players (NaturalSpawner's own number). */
    private static final int MOB_CAP_CHUNKS = 17 * 17;

    private WildLavaGolemTopUp() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server.getTickCount() % ColossusTuning.WILD_TOP_UP_INTERVAL != 0)
        {
            return;
        }
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty() || !spawningAllowed(dross))
        {
            return;
        }
        for (ServerPlayer player : List.copyOf(dross.players()))
        {
            if (player.isSpectator() || !dross.getBiome(player.blockPosition()).is(DrossBiomes.CINDER_WASTES))
            {
                continue;
            }
            if (!underMobCap(dross))
            {
                return;
            }
            LavaGolem golem = topUpNear(dross, player.blockPosition(), dross.random);
            if (golem != null)
            {
                LOGGER.debug("Lava golem top-up: added one at {} for {}", golem.blockPosition().toShortString(),
                        player.getGameProfile().getName());
            }
        }
    }

    /** Monsters may spawn: not Peaceful, the server spawns monsters, and the mob spawning rule is on. */
    private static boolean spawningAllowed(ServerLevel level)
    {
        return level.getDifficulty() != Difficulty.PEACEFUL && level.getServer().isSpawningMonsters()
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    /** True if the level has room for another monster under Minecraft's mob cap (as last counted by natural spawning). */
    private static boolean underMobCap(ServerLevel level)
    {
        NaturalSpawner.SpawnState state = level.getChunkSource().getLastSpawnState();
        if (state == null)
        {
            return true;
        }
        int cap = MobCategory.MONSTER.getMaxInstancesPerChunk() * state.getSpawnableChunkCount() / MOB_CAP_CHUNKS;
        return state.getMobCategoryCounts().getInt(MobCategory.MONSTER) < cap;
    }

    /**
     * One top-up round for a player standing at {@code center}: if the wild golems round it have fewer lava golems than
     * the share asks for, adds one next to the nearest lava (see the class comment).
     *
     * @return the lava golem it added, or null if none was needed or no good spot was found
     */
    @Nullable
    public static LavaGolem topUpNear(ServerLevel level, BlockPos center, RandomSource random)
    {
        int sideways = ColossusTuning.WILD_TOP_UP_LAVA_RANGE;
        int upDown = ColossusTuning.WILD_TOP_UP_LAVA_UP_DOWN;
        return topUpNear(level, center, random, true, new BoundingBox(center.getX() - sideways, center.getY() - upDown,
                center.getZ() - sideways, center.getX() + sideways, center.getY() + upDown, center.getZ() + sideways));
    }

    /**
     * {@link #topUpNear(ServerLevel, BlockPos, RandomSource)} with its lava search limited to {@code lavaArea}, and
     * (with {@code cinderWastesOnly} false) spots outside the Cinder Wastes allowed: the headless test's arena is
     * underground in a normal world, where cave lava round it mustn't count.
     */
    @Nullable
    static LavaGolem topUpNear(ServerLevel level, BlockPos center, RandomSource random, boolean cinderWastesOnly,
                               BoundingBox lavaArea)
    {
        if (lavaGolemsWanted(level, center) <= 0)
        {
            return null;
        }
        EntityType<LavaGolem> type = ModEntities.LAVA_GOLEM.get();
        int spread = ColossusTuning.WILD_TOP_UP_SPOT_SPREAD;
        // Every spot is within about 2 x spread of its lava, so lava closer than this to the player can't give a spot far
        // enough from them: skip it rather than spend the tries on it.
        double tooClose = ColossusTuning.WILD_TOP_UP_PLAYER_GAP - 2 * spread;
        List<BlockPos> tried = new ArrayList<>();
        for (BlockPos lava : lavaIn(level, lavaArea, center))
        {
            if (tried.size() >= ColossusTuning.WILD_TOP_UP_LAVA_TRIES)
            {
                break;
            }
            if (lava.distSqr(center) < tooClose * tooClose
                    || tried.stream().anyMatch(other -> other.distManhattan(lava) <= spread))
            {
                continue; // (too close to the player, or the same pool as one already tried)
            }
            tried.add(lava);
            for (int i = 0; i < ColossusTuning.WILD_TOP_UP_SPOTS_PER_LAVA; i++)
            {
                BlockPos spot = groundSpot(level, type, lava.offset(random.nextIntBetweenInclusive(-spread, spread), 0,
                        random.nextIntBetweenInclusive(-spread, spread)));
                if (spot == null || !farFromPlayers(level, center, spot)
                        || (cinderWastesOnly && !level.getBiome(spot).is(DrossBiomes.CINDER_WASTES)))
                {
                    continue;
                }
                LavaGolem golem = spawnAt(level, type, spot, random);
                if (golem != null)
                {
                    return golem;
                }
            }
        }
        return null;
    }

    /**
     * How many more lava golems the wild golems round {@code center} need to be about 1 in 5: the stone golems x share /
     * (1 - share), rounded to the nearest whole number, minus the lava golems already there.
     */
    static int lavaGolemsWanted(ServerLevel level, BlockPos center)
    {
        double range = ColossusTuning.WILD_TOP_UP_COUNT_RANGE;
        int stone = 0;
        int lava = 0;
        for (GolemMinion golem : level.getEntitiesOfClass(GolemMinion.class, new AABB(center).inflate(range),
                golem -> golem.isAlive() && golem.isWild() && golem.distanceToSqr(center.getCenter()) <= range * range))
        {
            if (golem instanceof LavaGolem)
            {
                lava++;
            }
            else if (golem instanceof StoneGolem)
            {
                stone++;
            }
        }
        double share = ColossusTuning.WILD_LAVA_GOLEM_SHARE;
        long wanted = Math.round(stone * share / (1.0D - share));
        return (int) wanted - lava;
    }

    /**
     * Every lava block in {@code area}, nearest to {@code center} first. Cheap like {@link WildGolemSpawns#isLavaNear}:
     * a chunk piece whose block list has no lava is skipped, and chunks that aren't loaded count as no lava.
     */
    static List<BlockPos> lavaIn(ServerLevel level, BoundingBox area, BlockPos center)
    {
        int minX = area.minX();
        int maxX = area.maxX();
        int minZ = area.minZ();
        int maxZ = area.maxZ();
        int minY = Math.max(level.getMinBuildHeight(), area.minY());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, area.maxY());
        List<BlockPos> found = new ArrayList<>();
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
                    if (section.hasOnlyAir() || !section.maybeHas(LAVA))
                    {
                        continue;
                    }
                    int x0 = Math.max(minX, SectionPos.sectionToBlockCoord(chunkX));
                    int x1 = Math.min(maxX, SectionPos.sectionToBlockCoord(chunkX, 15));
                    int y0 = Math.max(minY, SectionPos.sectionToBlockCoord(sectionY));
                    int y1 = Math.min(maxY, SectionPos.sectionToBlockCoord(sectionY, 15));
                    int z0 = Math.max(minZ, SectionPos.sectionToBlockCoord(chunkZ));
                    int z1 = Math.min(maxZ, SectionPos.sectionToBlockCoord(chunkZ, 15));
                    for (int y = y0; y <= y1; y++)
                    {
                        for (int x = x0; x <= x1; x++)
                        {
                            for (int z = z0; z <= z1; z++)
                            {
                                if (section.getFluidState(x & 15, y & 15, z & 15).is(FluidTags.LAVA))
                                {
                                    found.add(new BlockPos(x, y, z));
                                }
                            }
                        }
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found;
    }

    /**
     * The highest spot in this column, at most {@link ColossusTuning#WILD_LAVA_SEARCH_UP_DOWN} blocks above or below
     * {@code near}, where a lava golem could stand by Minecraft's natural-spawning rules (its placement on the ground and
     * its spawn rules, which include lava near it, Peaceful, the lair and the hub's safe zone), or null if there's none.
     */
    @Nullable
    private static BlockPos groundSpot(ServerLevel level, EntityType<LavaGolem> type, BlockPos near)
    {
        int upDown = ColossusTuning.WILD_LAVA_SEARCH_UP_DOWN;
        SpawnPlacements.Type placement = SpawnPlacements.getPlacementType(type);
        for (int dy = upDown; dy >= -upDown; dy--)
        {
            BlockPos spot = near.above(dy);
            if (!level.isNaturalSpawningAllowed(spot) || !NaturalSpawner.isSpawnPositionOk(placement, level, spot, type))
            {
                continue;
            }
            if (SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, spot, level.random)
                    && level.noCollision(type.getAABB(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D)))
            {
                return spot;
            }
            return null; // (the highest place it could stand failed the rules: don't look under it)
        }
        return null;
    }

    /** At least {@link ColossusTuning#WILD_TOP_UP_PLAYER_GAP} blocks from {@code center} and from every player. */
    private static boolean farFromPlayers(ServerLevel level, BlockPos center, BlockPos spot)
    {
        double gap = ColossusTuning.WILD_TOP_UP_PLAYER_GAP;
        double x = spot.getX() + 0.5D;
        double y = spot.getY();
        double z = spot.getZ() + 0.5D;
        if (center.getCenter().distanceToSqr(x, y, z) < gap * gap)
        {
            return false;
        }
        Player nearest = level.getNearestPlayer(x, y, z, gap, false);
        return nearest == null;
    }

    /** Spawns a wild lava golem at {@code spot} the way natural spawning does, or returns null if it may not stand there. */
    @Nullable
    private static LavaGolem spawnAt(ServerLevel level, EntityType<LavaGolem> type, BlockPos spot, RandomSource random)
    {
        LavaGolem golem = type.create(level);
        if (golem == null)
        {
            return null;
        }
        golem.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        if (!ForgeEventFactory.checkSpawnPosition(golem, level, MobSpawnType.NATURAL))
        {
            return null;
        }
        ForgeEventFactory.onFinalizeSpawn(golem, level, level.getCurrentDifficultyAt(spot), MobSpawnType.NATURAL, null, null);
        if (golem.isSpawnCancelled())
        {
            return null;
        }
        level.addFreshEntityWithPassengers(golem);
        return golem;
    }
}
