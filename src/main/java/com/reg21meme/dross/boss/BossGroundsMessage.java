package com.reg21meme.dross.boss;

import com.reg21meme.dross.boss.client.BossGroundsClient;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server to client (the quest area's channel, {@code quest.oracle.QuestNetwork}): the protected grounds near this player
 * that they may not break ({@link BossGrounds}), replacing the ones sent before. Their own game then never starts to
 * break one of those blocks ({@code boss.client.BossGroundsClient}).
 */
public record BossGroundsMessage(List<GroundsShape> shapes)
{
    /** Never more than this many shapes in one message (a few dozen is usual). */
    public static final int MAX_SHAPES = 4096;

    public void encode(FriendlyByteBuf buf)
    {
        buf.writeVarInt(shapes.size());
        for (GroundsShape shape : shapes)
        {
            shape.write(buf);
        }
    }

    public static BossGroundsMessage decode(FriendlyByteBuf buf)
    {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_SHAPES)
        {
            throw new DecoderException("too many protected grounds' shapes: " + n);
        }
        List<GroundsShape> shapes = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
        {
            shapes.add(GroundsShape.read(buf));
        }
        return new BossGroundsMessage(List.copyOf(shapes));
    }

    /** The message's bytes (the server compares them with the last ones it sent: nothing is sent when they're the same). */
    static byte[] bytes(List<GroundsShape> shapes)
    {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try
        {
            new BossGroundsMessage(shapes).encode(buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        }
        finally
        {
            buf.release();
        }
    }

    /** On the client's main thread. The client class is only touched on a client (never loaded on a server). */
    public void handle(Supplier<NetworkEvent.Context> context)
    {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BossGroundsClient.set(shapes));
        context.get().setPacketHandled(true);
    }
}
