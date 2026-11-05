package com.reg21meme.dross.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.registry.ModLootModifiers;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;

/**
 * Adds one Weathered Letter to Nether Fortress and Bastion Remnant chests.
 * <p>Which chests: the conditions in {@code data/dross/loot_modifiers/weathered_letter.json}
 * ({@code minecraft:chests/nether_bridge}, {@code bastion_treasure}, {@code bastion_other},
 * {@code bastion_bridge} and {@code bastion_hoglin_stable}). It's turned on by
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json}.</p>
 * <p>This is a Forge "global loot modifier": it adds to the chest's normal loot instead of replacing
 * the loot table, so other mods' changes to these chests still work.</p>
 */
public class WeatheredLetterLootModifier extends LootModifier
{
    /** The default chance that a chest gets a letter (1.0 = every chest); the game uses config/dross-common.toml's letterChance. */
    public static final float LETTER_CHANCE = (float) com.reg21meme.dross.DrossConfig.DEFAULT_LETTER_CHANCE;

    public static final Codec<WeatheredLetterLootModifier> CODEC =
            RecordCodecBuilder.create(instance -> codecStart(instance).apply(instance, WeatheredLetterLootModifier::new));

    public WeatheredLetterLootModifier(LootItemCondition[] conditions)
    {
        super(conditions);
    }

    @NotNull
    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context)
    {
        if (context.getRandom().nextFloat() < com.reg21meme.dross.DrossConfig.letterChance())
        {
            // Blank for now: its text (the rough location of the nearest Rift Chapel to whoever takes it) is written once
            // it's in a player's inventory.
            generatedLoot.add(WeatheredLetterItem.createBlank());
        }
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return ModLootModifiers.WEATHERED_LETTER.get();
    }
}
