package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.portal.DrossPortalBlock;
import com.reg21meme.dross.portal.DrossPortalFrameBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks
{
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Dross.MODID);

    // Portal area
    public static final RegistryObject<Block> DROSS_PORTAL_FRAME = BLOCKS.register("dross_portal_frame", DrossPortalFrameBlock::new);
    public static final RegistryObject<Block> DROSS_PORTAL = BLOCKS.register("dross_portal", DrossPortalBlock::new);

    private ModBlocks() {}
}
