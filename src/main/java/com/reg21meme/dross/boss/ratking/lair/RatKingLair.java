package com.reg21meme.dross.boss.ratking.lair;

import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.ratking.RatKingTuning;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Rat King's home, for other areas ({@code /dross goto lair ratking}): the rat caves at the end of about half of the
 * sewer networks under Blightwood (dimension-builder's {@link RatCaves}). In the middle of each, on a nest of piled
 * carpets ringed by his gold, the Rat King sleeps: he's there, asleep, before anyone who comes near gets close, and wakes
 * as in his fight. Once he's beaten, the cave stays empty for good (no respawn). {@link RatKingLairKeeper} has the rules;
 * {@link RatKingSnores} lets players at his sewer's grand entrance hear him snoring while he sleeps.
 * <p>
 * Everything here is for the server thread.
 */
public final class RatKingLair
{
    private RatKingLair() {}

    /**
     * The nearest rat cave to {@code from} in the Dross (Minecraft's own structure search, like {@code /locate}; it can
     * reach many thousands of blocks), or empty.
     */
    public static Optional<RatCaves.RatCave> nearest(ServerLevel dross, BlockPos from)
    {
        return RatCaves.findNearest(dross, from);
    }

    /**
     * Generates (or loads) every chunk of the cave fully, so its blocks are all there: call it before teleporting someone
     * into it. Can take a few seconds the first time (the chunks are made on the calling thread).
     */
    public static void prepare(ServerLevel dross, RatCaves.RatCave cave)
    {
        for (ChunkPos chunk : RatKingLairKeeper.chunks(cave))
        {
            dross.getChunk(chunk.x, chunk.z);
        }
    }

    /** Where the Rat King's feet are when he lies on the cave's nest. */
    public static Vec3 nestFeet(RatCaves.RatCave cave)
    {
        return RatKingLairKeeper.nestFeet(cave);
    }

    /** True for good once the cave's Rat King has been beaten (no respawn: the cave stays empty). */
    public static boolean isBeaten(MinecraftServer server, RatCaves.RatCave cave)
    {
        return RatKingLairData.get(server).beaten(cave.id());
    }

    /** True if the cave's Rat King has been woken and is still about (someone started his fight and left). */
    public static boolean isKingAwake(MinecraftServer server, RatCaves.RatCave cave)
    {
        return RatKingLairData.get(server).awake(cave.id());
    }

    /** Tests: removes every Rat King in the cave (living or lying dead) and forgets its king, as if nobody had been near. */
    public static void clearForTests(ServerLevel dross, RatCaves.RatCave cave)
    {
        AABB box = AABB.of(cave.bounds()).inflate(RatKingTuning.RAT_CAVE_HOME_MARGIN);
        for (RatKing king : dross.getEntitiesOfClass(RatKing.class, box, king -> !king.isShowcase()))
        {
            king.discard();
        }
        RatKingLairData.get(dross.getServer()).resetForTests(cave.id());
    }
}
