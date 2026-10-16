package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rage.RageEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects
{
    public static final DeferredRegister<MobEffect> MOB_EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Dross.MODID);

    // Boss area: Rage, from the Rage Wand (raged mobs fight each other; infinite, no cure)
    public static final RegistryObject<MobEffect> RAGE = MOB_EFFECTS.register("rage", RageEffect::new);

    private ModEffects() {}
}
