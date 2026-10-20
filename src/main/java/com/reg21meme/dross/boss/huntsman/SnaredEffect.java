package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.DrossColors;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Snared ({@code dross:snared}, in {@code registry/ModEffects}): held still by the Huntsman's bola or the frost of his
 * aimed shot. While it lasts you can't walk (no speed at all) and can't jump ({@link HuntsmanEvents} stops the jump).
 */
public class SnaredEffect extends MobEffect
{
    /** Its speed modifier's id (fixed, so the game can find and remove it). */
    private static final String SPEED_ID = "b1f6c9a2-6d3e-4f8a-9c21-7e5d4a3b2c10";

    public SnaredEffect()
    {
        super(MobEffectCategory.HARMFUL, DrossColors.HUNTSMAN_SNARED);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, SPEED_ID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
