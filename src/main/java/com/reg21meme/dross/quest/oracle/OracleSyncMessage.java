package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.quest.client.OracleClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: a player's quest markers (the Blind Oracle's and the Dross Trader's) and which locked lands are open
 * for them (bit {@code i} of
 * {@code openLands} is {@link OracleQuests#lockableLands()}{@code .get(i)}). Sent on login, respawn, a change of
 * dimension and every change to their progress ({@link OracleProgress#sync}). The client keeps it in
 * {@code quest.client.OracleClientState}.
 *
 * @param marker    the marker over the Oracle for this player
 * @param traderMarker the marker over the Dross Trader for this player ({@code quest.trader.TraderProgress#marker})
 * @param openLands the open lockable lands, a bit each (by their progress only: the client adds creative and spectator)
 * @param fallenLands the open lands whose barrier they've seen fall, a bit each (an open land whose barrier hasn't
 *                    fallen for them yet still shows its curtain, until they come to it)
 */
public record OracleSyncMessage(OracleMarker marker, OracleMarker traderMarker, int openLands, int fallenLands)
{
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeEnum(marker);
        buf.writeEnum(traderMarker);
        buf.writeVarInt(openLands);
        buf.writeVarInt(fallenLands);
    }

    public static OracleSyncMessage decode(FriendlyByteBuf buf)
    {
        return new OracleSyncMessage(buf.readEnum(OracleMarker.class), buf.readEnum(OracleMarker.class), buf.readVarInt(),
                buf.readVarInt());
    }

    /** On the client's main thread. The client class is only touched on a client (never loaded on a server). */
    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> OracleClientState.set(marker, traderMarker, openLands, fallenLands));
        context.get().setPacketHandled(true);
    }
}
