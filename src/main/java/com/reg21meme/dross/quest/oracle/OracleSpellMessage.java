package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.quest.client.OracleSpellEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the Blind Oracle casts his spell ({@link OracleSpell}); sent to everyone who can see him. The client
 * draws it ({@code quest.client.OracleSpellEffects}).
 *
 * @param oracle   the Oracle's entity id (his arms go up, and the spell follows him), or -1 for a showcase cast from
 *                 a spot with nobody there
 * @param x        where it's cast from (his feet), X...
 * @param y        ...Y
 * @param z        ...and Z (the spell uses the Oracle's own position when he's there)
 * @param dirX     which way the barrier lies from there (a unit vector along the ground, X)...
 * @param dirZ     ...and Z
 * @param distance how far off it is (blocks)
 * @param delay    ticks to wait before it starts (the pause after his line, {@link OracleSpellTuning#PAUSE})
 */
public record OracleSpellMessage(int oracle, double x, double y, double z, float dirX, float dirZ, float distance, int delay)
{
    public void encode(FriendlyByteBuf buf)
    {
        buf.writeVarInt(oracle);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(dirX);
        buf.writeFloat(dirZ);
        buf.writeFloat(distance);
        buf.writeVarInt(delay);
    }

    public static OracleSpellMessage decode(FriendlyByteBuf buf)
    {
        return new OracleSpellMessage(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readVarInt());
    }

    /** On the client's main thread. The client class is only touched on a client (never loaded on a server). */
    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> OracleSpellEffects.start(oracle, x, y, z, dirX, dirZ, distance, delay));
        context.get().setPacketHandled(true);
    }
}
