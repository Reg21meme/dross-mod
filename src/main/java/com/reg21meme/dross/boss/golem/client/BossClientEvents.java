package com.reg21meme.dross.boss.golem.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.LavaBlob;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the Cinder Colossus's renderers, its minions' and its projectiles' (client only). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BossClientEvents
{
    /** A lava blob's size (blocks across): a glowing lump of magma. */
    private static final float BLOB_SIZE = 0.75F;

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.CINDER_COLOSSUS.get(), CinderColossusRenderer::new);
        event.registerEntityRenderer(ModEntities.STONE_GOLEM.get(), MinionRenderer::new);
        event.registerEntityRenderer(ModEntities.LAVA_GOLEM.get(), MinionRenderer::new);
        event.registerEntityRenderer(ModEntities.COLOSSUS_ROCK.get(), TumblingBlockRenderer::rock);
        event.registerEntityRenderer(ModEntities.LAVA_BLOB.get(),
                context -> new TumblingBlockRenderer<LavaBlob>(context, BLOB_SIZE, true));
    }
}
