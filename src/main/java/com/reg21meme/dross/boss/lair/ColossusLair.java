package com.reg21meme.dross.boss.lair;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * The Cinder Colossus's lair in the Cinder Wastes, for other areas (world-builder's {@code /dross goto lair colossus}).
 * <p>
 * Each world gets one, placed in the background a little while after the world first starts with it (old worlds too):
 * a volcano rising well above the land, with a jagged smoking crater at its top that opens into a round domed cavern 41
 * blocks across (the arena), entered by a big cave mouth in its flank. Worlds that got the earlier lair (a cavern with a
 * skylight and a narrow tunnel, round 2) keep it. Its Colossus rises when a player walks in, and again for the next player once
 * {@link LairTuning#LAIR_RESPAWN_TICKS} (10 minutes of game time) have passed since it was beaten.
 * <p>
 * Everything here is for the server thread (it reads the world's saved data).
 */
public final class ColossusLair
{
    private ColossusLair() {}

    /**
     * A built lair, in the Dross ({@code ModDimensions.DROSS_LEVEL}).
     *
     * @param center          the floor's middle block (the cavern's centre at floor level; players stand one block above it)
     * @param arenaMiddle     where the Colossus rises: the middle of the floor, feet position
     * @param entrance        a safe place to stand in front of the way in, open to the sky (feet position: the middle of a
     *                        block's top, two blocks of air above): in the gully just outside the volcano's cave mouth
     * @param entranceYaw     the way to face from {@code entrance}: into the mouth, towards the cavern (Minecraft's yaw:
     *                        0 south, 90 west, 180 north, -90/270 east)
     * @param tunnelDirection the way the way in runs out of the cavern, towards its mouth (north, east, south or west)
     */
    public record Lair(BlockPos center, Vec3 arenaMiddle, Vec3 entrance, float entranceYaw, Direction tunnelDirection)
    {
    }

    /** True once this world's lair is built. */
    public static boolean isPlaced(MinecraftServer server)
    {
        return ColossusLairData.get(server).isPlaced();
    }

    /** This world's lair, once it's built; empty before (in a new world, until a minute or so after it starts). */
    public static Optional<Lair> get(MinecraftServer server)
    {
        ColossusLairData data = ColossusLairData.get(server);
        if (!data.isPlaced())
        {
            return Optional.empty();
        }
        BlockPos center = new BlockPos(data.centerX(), data.floorY(), data.centerZ());
        Vec3 middle = new Vec3(data.centerX() + 0.5D, data.floorY() + 1.0D, data.centerZ() + 0.5D);
        return Optional.of(new Lair(center, middle, data.entrance(), data.entranceYaw(), data.tunnel()));
    }

    /**
     * Where the lair will be, once the search has picked its spot but before it's built (X and Z of the floor's middle;
     * Y is 0, not the ground). Empty if no spot has been picked yet. Once it's built, the same as {@code get}'s centre's X
     * and Z.
     */
    public static Optional<BlockPos> plannedSite(MinecraftServer server)
    {
        ColossusLairData data = ColossusLairData.get(server);
        if (data.isPlaced())
        {
            return Optional.of(new BlockPos(data.centerX(), 0, data.centerZ()));
        }
        return data.isPlanned() ? Optional.of(new BlockPos(data.plannedX(), 0, data.plannedZ())) : Optional.empty();
    }

    /**
     * How many ticks of game time are left before the lair's Colossus can rise again: 0 if it can (never beaten, or beaten
     * at least {@link LairTuning#LAIR_RESPAWN_TICKS} ago). It still only rises when someone walks in.
     */
    public static long ticksUntilRespawn(MinecraftServer server)
    {
        ColossusLairData data = ColossusLairData.get(server);
        if (!data.beaten())
        {
            return 0L;
        }
        long passed = server.overworld().getGameTime() - data.defeatedAt();
        return Math.max(0L, LairTuning.LAIR_RESPAWN_TICKS - passed);
    }
}
