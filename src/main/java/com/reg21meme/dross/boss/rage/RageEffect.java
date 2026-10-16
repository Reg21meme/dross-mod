package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Rage ({@code dross:rage}), given by the {@link RageWandItem}: a raged mob fights the other raged mobs (see
 * {@link Rage}). It does nothing by itself each tick: the fighting is the rage's goals ({@link RageAi}).
 * <p>
 * It's given for ever (infinite duration) with its swirl of particles hidden (the mob shows red angry particles
 * instead), and nothing cures it: not even milk (the user: "this is a creative mode only staff so don't worry about
 * adding a cure"). It ends when the mob calms down ({@link RageTuning#RAGE_CALM_TICKS} without a raged enemy near), or
 * with {@code /effect clear}. Players can't have it.
 */
public class RageEffect extends MobEffect
{
    public RageEffect()
    {
        super(MobEffectCategory.HARMFUL, DrossColors.RAGE_EFFECT);
    }

    /** No cure: milk (or anything else) doesn't take it away. */
    @Override
    public List<ItemStack> getCurativeItems()
    {
        return new ArrayList<>();
    }
}
