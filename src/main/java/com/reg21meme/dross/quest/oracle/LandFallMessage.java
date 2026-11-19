package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.dimension.ward.client.LandWardFall;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: a land's barrier falls for this player, the first time they come to it after the Oracle broke it
 * ({@code dimension/ward/LandWardFalls}). The client plays it at that stretch of the edge
 * ({@code dimension.ward.client.LandWardFall}).
 *
 * @param land  the land, its bit in {@link OracleQuests#lockableLands()}; -1 for the showcase's straight stretch
 * @param x     a column of that land near the player, X (the showcase: the middle of its stretch)...
 * @param z     ...and Z
 * @param alongX the showcase's stretch runs this way (a unit vector along the ground, X; 0 for a real land)...
 * @param alongZ ...and Z
 */
public record LandFallMessage(int land, int x, int z, float alongX, float alongZ)
{
    public LandFallMessage(int land, int x, int z)
    {
        this(land, x, z, 0.0F, 0.0F);
    }

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeVarInt(land);
        buf.writeVarInt(x);
        buf.writeVarInt(z);
        buf.writeFloat(alongX);
        buf.writeFloat(alongZ);
    }

    public static LandFallMessage decode(FriendlyByteBuf buf)
    {
        return new LandFallMessage(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    /** On the client's main thread. The client class is only touched on a client (never loaded on a server). */
    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LandWardFall.start(land, x, z, alongX, alongZ));
        context.get().setPacketHandled(true);
    }
}
