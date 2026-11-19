package com.reg21meme.dross.quest.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * CLIENT ONLY. How the Oracle's spell is drawn ({@link OracleSpellEffects}): glowing light added onto whatever is behind
 * it (additive), seen from both sides, never hiding anything behind it, and not dimmed by the Dross's fog (so the strike
 * far off still shows). {@link #GLOW} takes a colour per corner (the beams, the flash and the shock ring are drawn as
 * shapes fading to nothing at their edges); {@link #textured} takes a texture too (the rune circles).
 *
 * <p>(A subclass only to reach Minecraft's own building blocks for render types, which are protected.)</p>
 */
public final class SpellRenderTypes extends RenderType
{
    /** Colour only, added on. */
    public static final RenderType GLOW = create("dross_oracle_spell_glow", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    private static final Map<ResourceLocation, RenderType> TEXTURED = new HashMap<>();

    /** A texture tinted by a colour per corner, added on. */
    public static RenderType textured(ResourceLocation texture)
    {
        return TEXTURED.computeIfAbsent(texture, t -> create("dross_oracle_spell_textured", DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS, 256, false, true, CompositeState.builder()
                        .setShaderState(POSITION_COLOR_TEX_SHADER)
                        .setTextureState(new TextureStateShard(t, false, false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false)));
    }

    private SpellRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean crumbling,
                             boolean sort, Runnable setup, Runnable clear)
    {
        super(name, format, mode, bufferSize, crumbling, sort, setup, clear);
    }
}
