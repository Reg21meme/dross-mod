package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderFireBlock;
import com.reg21meme.dross.boss.golem.CinderLavaBlock;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlock;
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

    // Boss area: the Cinder Colossus's temporary lava pools and fire trail (they clear themselves away; no items)
    public static final RegistryObject<Block> CINDER_LAVA = BLOCKS.register("cinder_lava", CinderLavaBlock::new);
    public static final RegistryObject<Block> CINDER_FIRE = BLOCKS.register("cinder_fire", CinderFireBlock::new);

    // Boss area: the Cinder Colossus trophy (a miniature of the boss in one of five designs; the boss drops one)
    public static final RegistryObject<Block> COLOSSUS_TROPHY = BLOCKS.register("colossus_trophy", ColossusTrophyBlock::new);

    private ModBlocks() {}
}
