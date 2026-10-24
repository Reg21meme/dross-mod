package com.reg21meme.dross.boss.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * An extra pass of a GeckoLib model (its glow, its see-through ice) drawn only for the parts that show anything in that
 * pass's texture: the eyes, the Rimehorn's horns, the Huntsman's ice. Drawing the whole model again for each pass was
 * most of what a village of the Frostfolk and a pen of Rimehorns cost (measured in the game, October 24, 2026: the
 * Rimehorn's 222 boxes drawn three times over for two horns). Which parts show is worked out once per model and texture,
 * from the texture's own pixels under each face.
 */
public final class PartLayer<T extends GeoAnimatable> extends GeoRenderLayer<T>
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Per baked model (a resource reload makes new ones), per texture: the names of the bones that show in it. */
    private static final Map<BakedGeoModel, Map<ResourceLocation, Set<String>>> SHOWING =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private final Function<T, ResourceLocation> texture;
    private final Function<ResourceLocation, RenderType> type;
    private final boolean fullBright;

    /**
     * @param texture    the pass's texture for one being drawn (null: no pass for it)
     * @param type       the pass's render type for a texture
     * @param fullBright drawn at full brightness (a glow), else lit by the world
     */
    public PartLayer(GeoRenderer<T> renderer, Function<T, ResourceLocation> texture,
                     Function<ResourceLocation, RenderType> type, boolean fullBright)
    {
        super(renderer);
        this.texture = texture;
        this.type = type;
        this.fullBright = fullBright;
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay)
    {
        if (bone.isHidden() || bone.getCubes().isEmpty())
        {
            return;
        }
        ResourceLocation tex = this.texture.apply(animatable);
        if (tex == null)
        {
            return;
        }
        BakedGeoModel model = this.getGeoModel().getBakedModel(this.getGeoModel().getModelResource(animatable));
        if (!showing(model, tex).contains(bone.getName()))
        {
            return;
        }
        VertexConsumer pass = bufferSource.getBuffer(this.type.apply(tex));
        this.getRenderer().renderCubesOfBone(poseStack, bone, pass, this.fullBright ? LightTexture.FULL_BRIGHT : packedLight,
                packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        bufferSource.getBuffer(renderType);     // (back to the main pass: it shares the buffer the model goes on drawing in)
    }

    /** The bones of {@code model} with a face over any pixel of {@code texture} that isn't see-through. */
    static Set<String> showing(BakedGeoModel model, ResourceLocation texture)
    {
        Map<ResourceLocation, Set<String>> byTexture = SHOWING.computeIfAbsent(model, m -> new HashMap<>());
        synchronized (byTexture)
        {
            return byTexture.computeIfAbsent(texture, t -> find(model, t));
        }
    }

    private static Set<String> find(BakedGeoModel model, ResourceLocation texture)
    {
        Set<String> out = new HashSet<>();
        NativeImage image = read(texture);
        if (image == null)
        {
            return out;
        }
        try (image)
        {
            for (GeoBone bone : model.topLevelBones())
            {
                collect(bone, image, out);
            }
        }
        return out;
    }

    private static void collect(GeoBone bone, NativeImage image, Set<String> out)
    {
        if (shows(bone, image))
        {
            out.add(bone.getName());
        }
        for (GeoBone child : bone.getChildBones())
        {
            collect(child, image, out);
        }
    }

    private static boolean shows(GeoBone bone, NativeImage image)
    {
        int w = image.getWidth();
        int h = image.getHeight();
        for (GeoCube cube : bone.getCubes())
        {
            for (GeoQuad quad : cube.quads())
            {
                if (quad == null)
                {
                    continue;
                }
                float u0 = 1.0F, v0 = 1.0F, u1 = 0.0F, v1 = 0.0F;
                for (GeoVertex v : quad.vertices())
                {
                    u0 = Math.min(u0, v.texU());
                    u1 = Math.max(u1, v.texU());
                    v0 = Math.min(v0, v.texV());
                    v1 = Math.max(v1, v.texV());
                }
                int x0 = Math.max(0, (int) Math.floor(u0 * w));
                int x1 = Math.min(w, (int) Math.ceil(u1 * w));
                int y0 = Math.max(0, (int) Math.floor(v0 * h));
                int y1 = Math.min(h, (int) Math.ceil(v1 * h));
                for (int y = y0; y < y1; y++)
                {
                    for (int x = x0; x < x1; x++)
                    {
                        if ((image.getPixelRGBA(x, y) >>> 24) != 0)
                        {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    @Nullable
    private static NativeImage read(ResourceLocation texture)
    {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty())
        {
            return null;
        }
        try (InputStream in = resource.get().open())
        {
            return NativeImage.read(in);
        }
        catch (Exception e)
        {
            LOGGER.warn("Couldn't read {} to see which parts show in it", texture, e);
            return null;
        }
    }
}
