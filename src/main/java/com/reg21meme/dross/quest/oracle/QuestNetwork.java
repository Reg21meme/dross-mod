package com.reg21meme.dross.quest.oracle;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.BossGroundsMessage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

/**
 * The quest area's network channel, {@code dross:quest} (the mod's first). Its messages, all server to client:
 * {@link OracleSyncMessage} (a player's Oracle marker, which locked lands are open for them and which barriers they've
 * seen fall), {@link OracleSpellMessage} (the Oracle casts his spell), {@link LandFallMessage} (a barrier falls) and the
 * boss area's {@link BossGroundsMessage} (the protected grounds near a player that they may not break).
 * Registered in common setup (this class's own mod-bus subscriber).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QuestNetwork
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Bump when a message's layout changes: client and server must match. */
    private static final String PROTOCOL = "4";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Dross.MODID, "quest"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static volatile boolean registered;

    private QuestNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(QuestNetwork::register);
    }

    private static synchronized void register()
    {
        if (registered)
        {
            return;
        }
        registered = true;
        CHANNEL.messageBuilder(OracleSyncMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OracleSyncMessage::encode)
                .decoder(OracleSyncMessage::decode)
                .consumerMainThread(OracleSyncMessage::handle)
                .add();
        CHANNEL.messageBuilder(OracleSpellMessage.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OracleSpellMessage::encode)
                .decoder(OracleSpellMessage::decode)
                .consumerMainThread(OracleSpellMessage::handle)
                .add();
        CHANNEL.messageBuilder(LandFallMessage.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LandFallMessage::encode)
                .decoder(LandFallMessage::decode)
                .consumerMainThread(LandFallMessage::handle)
                .add();
        CHANNEL.messageBuilder(BossGroundsMessage.class, 3, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BossGroundsMessage::encode)
                .decoder(BossGroundsMessage::decode)
                .consumerMainThread(BossGroundsMessage::handle)
                .add();
    }

    /** Sends a message to every player who can see this entity (not the entity itself). */
    static void sendToTracking(Entity entity, Object message)
    {
        if (!registered || entity.level().isClientSide)
        {
            return;
        }
        try
        {
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Couldn't send an Oracle message round {}", entity, e);
        }
    }

    /** Sends a message to every player who can see this player (not the player). */
    static void sendToTrackingPlayer(ServerPlayer player, Object message)
    {
        if (!registered || player.connection == null)
        {
            return;
        }
        try
        {
            CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), message);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Couldn't send an Oracle message round {}", player.getGameProfile().getName(), e);
        }
    }

    /** Sends a message to one player (nothing if they have no connection, like a test's half-made player). */
    public static void sendTo(ServerPlayer player, Object message)
    {
        if (player.connection == null || !registered)
        {
            return;
        }
        try
        {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Couldn't send an Oracle message to {}", player.getGameProfile().getName(), e);
        }
    }
}
