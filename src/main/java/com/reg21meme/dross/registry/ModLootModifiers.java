package com.reg21meme.dross.registry;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.WeatheredLetterLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Global loot modifier types (shared file: only add your own entries).
 * Each one also needs a JSON in {@code data/dross/loot_modifiers/} and a line in
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json}.
 */
public final class ModLootModifiers
{
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Dross.MODID);

    // Quest area: one Weathered Letter in Nether Fortress and Bastion chests
    public static final RegistryObject<Codec<WeatheredLetterLootModifier>> WEATHERED_LETTER =
            LOOT_MODIFIERS.register("weathered_letter", () -> WeatheredLetterLootModifier.CODEC);

    private ModLootModifiers() {}
}
