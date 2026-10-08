package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.enchant.DeathforgedEnchantment;
import com.reg21meme.dross.enchant.NecromancyEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments
{
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, Dross.MODID);

    // Enchantments area: Necromancy (I-IV) and Deathforged (I-X), swords only
    public static final RegistryObject<Enchantment> NECROMANCY = ENCHANTMENTS.register("necromancy", NecromancyEnchantment::new);
    public static final RegistryObject<Enchantment> DEATHFORGED = ENCHANTMENTS.register("deathforged", DeathforgedEnchantment::new);

    private ModEnchantments() {}
}
