package com.reg21meme.dross;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here must match the mod_id in gradle.properties (which fills in META-INF/mods.toml)
@Mod(Dross.MODID)
public class Dross
{
    public static final String MODID = "dross";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Dross(FMLJavaModLoadingContext context)
    {
        LOGGER.info("Dross is loading");
    }
}
