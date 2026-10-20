package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.SnaredEffect;
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
    // Boss area: Snared, from the Huntsman's bola and his aimed shot (held still: no walking or jumping)
    public static final RegistryObject<MobEffect> SNARED = MOB_EFFECTS.register("snared", SnaredEffect::new);

    private ModEffects() {}
}
