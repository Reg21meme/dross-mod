package com.reg21meme.dross.dimension.client;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.slf4j.Logger;

/**
 * The Dross's own sky and light (client only), for dimension type {@code dross:dross} (its {@code "effects"}). It's
 * always midnight in the Dross; this keeps that night blue instead of black:
 * <ul>
 *   <li><b>Fog</b>: Minecraft darkens the fog to almost black at night (to 6% in the Overworld). Here each biome's fog
 *       colour (its JSON, from {@code DrossColors}: deep blue in Silverwood Forest, red-violet in the Nether Scar...) is
 *       shown as it is, times {@link #FOG_BRIGHTNESS}, so the haze in the distance and the sky at the horizon stay blue.</li>
 *   <li><b>No clouds</b> (they'd cut through the Gravecrags' peaks) and no sunrise colours.</li>
 *   <li><b>Light</b>: light from the sky is tinted a little toward {@code DrossColors.DROSS_SKY_LIGHT_TINT} (pale blue),
 *       most where the sky light is strong and no torch is near ({@link #LIGHT_TINT_STRENGTH}).</li>
 * </ul>
 * The glow over the night sky and the thicker fog in some biomes are {@link DrossSkyRendering}. Each part has an on/off
 * switch below; if one ever throws, it's switched off for the rest of the session (with one error in the log) and
 * Minecraft's own look is used instead.
 */
public class DrossSkyEffects extends DimensionSpecialEffects
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The dimension type whose look this is ({@code data/dross/dimension_type/dross.json}'s {@code "effects"}). */
    public static final ResourceLocation EFFECTS_ID = new ResourceLocation(Dross.MODID, "dross");

    // Switches (false = Minecraft's own Overworld behaviour for that part)
    /** Use these effects at all (false: the Dross looks like the Overworld at midnight, as before). */
    public static final boolean ENABLED = true;
    /** Show each biome's fog colour at night instead of darkening it to black. */
    public static final boolean NIGHT_FOG_ENABLED = true;
    /** Tint sky light blue. */
    public static final boolean LIGHT_TINT_ENABLED = true;

    // Tuning
    /** The night fog is the biome's fog colour times this (1 = exactly the colour in DrossColors). */
    public static final float FOG_BRIGHTNESS = 1.0F;
    /** How strongly sky light is pulled toward the tint (0 = not at all, 1 = fully). */
    public static final float LIGHT_TINT_STRENGTH = 0.25F;

    private static boolean lightTintFailed;

    public DrossSkyEffects()
    {
        // No clouds (NaN cloud height), ground below the horizon, the normal sky with stars, normal lighting.
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness)
    {
        if (!NIGHT_FOG_ENABLED)
        {
            // Minecraft's Overworld formula
            return fogColor.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
        }
        return fogColor.scale(FOG_BRIGHTNESS);
    }

    @Override
    public boolean isFoggyAt(int x, int y)
    {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks)
    {
        return null;
    }

    /**
     * Tints the light from the sky toward pale blue: {@code pixelY} is the sky light level (0-15), {@code pixelX} the
     * block light level (torches...). The more sky light and the less block light, the stronger the tint.
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTicks, float skyDarken, float blockLightRedFlicker, float skyLight,
                                     int pixelX, int pixelY, Vector3f colors)
    {
        if (!LIGHT_TINT_ENABLED || lightTintFailed)
        {
            return;
        }
        try
        {
            float weight = LIGHT_TINT_STRENGTH * (pixelY / 15.0F) * (1.0F - pixelX / 30.0F);
            int tint = DrossColors.DROSS_SKY_LIGHT_TINT;
            float r = DrossColors.red(tint);
            float g = DrossColors.green(tint);
            float b = DrossColors.blue(tint);
            float top = Math.max(r, Math.max(g, b));
            if (top <= 0.0F)
            {
                return;
            }
            // Multiply by the tint scaled so its brightest channel is 1 (a tint, not a darkening), then blend by weight.
            colors.set(colors.x() * (1.0F - weight + weight * r / top), colors.y() * (1.0F - weight + weight * g / top),
                    colors.z() * (1.0F - weight + weight * b / top));
        }
        catch (RuntimeException e)
        {
            lightTintFailed = true;
            LOGGER.error("Dross sky: tinting the light failed; it's switched off for this session", e);
        }
    }

    /** Registers the effects for dimension type dross:dross (client start-up, mod bus). */
    @Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterEffects(RegisterDimensionSpecialEffectsEvent event)
        {
            if (ENABLED)
            {
                event.register(EFFECTS_ID, new DrossSkyEffects());
            }
        }
    }
}
