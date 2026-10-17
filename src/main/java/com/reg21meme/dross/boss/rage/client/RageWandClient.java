package com.reg21meme.dross.boss.rage.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rage.RageWandItem;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * The Rage Wand's look on the client: its item model's {@code dross:rage_team} property is the wand's team (0 Blue up
 * to 1 Purple, {@code RageTeam.modelValue}), and {@code models/item/rage_wand.json}'s overrides pick the crystal of that
 * colour ({@code textures/item/rage_wand_<team>.png}, drawn by {@code tools/ragewand/draw.py}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RageWandClient
{
    /** The item model property the overrides read. */
    public static final ResourceLocation TEAM_PROPERTY = new ResourceLocation(Dross.MODID, "rage_team");

    private RageWandClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> ItemProperties.register(ModItems.RAGE_WAND.get(), TEAM_PROPERTY,
                (stack, level, entity, seed) -> RageWandItem.teamOf(stack).modelValue()));
    }
}
