package com.reg21meme.dross.dimension.ward.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.DrossColors;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * CLIENT ONLY. One streak of a land ward's curtain ({@code dross:ward_streak}; QUEST_PLAN.md, "Locked lands"): a thin,
 * upright line of light that rises off the border from the ground and fades out high up, its colour going from
 * {@link DrossColors#LAND_WARD_STREAK} as it leaves the ground through {@link DrossColors#LAND_WARD_STREAK_MID} to
 * {@link DrossColors#LAND_WARD_STREAK_FADE}. Its sprites ({@code textures/particle/ward_streak_<n>.png}, 8 x 64, white)
 * are drawn by {@code tools/terrain/ward_particles.py}, its head at the top.
 *
 * <p>Unlike most particles it isn't a square turned to face you: it's a tall strip that stays upright and turns only
 * round its own upright line, so the curtain reads as streaks of light from any side. Made by {@link LandWardEdge}.</p>
 */
public class WardStreakParticle extends TextureSheetParticle
{
    /** How long a streak lasts (ticks), at least and at most. */
    public static final int MIN_LIFETIME = 40;
    public static final int MAX_LIFETIME = 70;
    /** How far it rises over its life (blocks), at least and at most. */
    public static final float MIN_RISE = 14.0F;
    public static final float MAX_RISE = 24.0F;
    /** Its length (blocks), at least and at most. */
    public static final float MIN_LENGTH = 1.2F;
    public static final float MAX_LENGTH = 2.4F;
    /** Its width (blocks), at least and at most: thin. */
    public static final float MIN_WIDTH = 0.12F;
    public static final float MAX_WIDTH = 0.22F;
    /** Its brightest (0 to 1). */
    public static final float MAX_ALPHA = 0.9F;
    /** It brightens to full over this share of its life as it leaves the ground... */
    public static final float FADE_IN = 0.12F;
    /** ...then fades: alpha = (1 - life) to this power. */
    public static final float FADE_OUT_POWER = 1.2F;
    /** The shimmer: its brightness wavers by this share, this fast (radians a tick). */
    public static final float SHIMMER = 0.18F;
    public static final float SHIMMER_SPEED = 0.85F;

    private final float length;
    private final float width;
    private final float shimmerPhase;

    protected WardStreakParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites)
    {
        super(level, x, y, z);
        this.lifetime = Mth.nextInt(this.random, MIN_LIFETIME, MAX_LIFETIME);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.length = Mth.nextFloat(this.random, MIN_LENGTH, MAX_LENGTH);
        this.width = Mth.nextFloat(this.random, MIN_WIDTH, MAX_WIDTH);
        this.shimmerPhase = this.random.nextFloat() * Mth.TWO_PI;
        this.setSize(this.width, this.length); // its box (for the game's view culling) is the whole strip, foot to head
        this.xd = 0.0D;
        this.yd = Mth.nextFloat(this.random, MIN_RISE, MAX_RISE) / this.lifetime;
        this.zd = 0.0D;
        this.pickSprite(sprites);
        this.shade(0.0F);
    }

    @Override
    public void tick()
    {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime)
        {
            this.remove();
            return;
        }
        this.move(this.xd, this.yd, this.zd);
        this.shade((float) this.age / this.lifetime);
    }

    /** Its colour and brightness this far through its life (0 to 1). */
    private void shade(float life)
    {
        int color = life < 0.5F
                ? lerpColor(DrossColors.LAND_WARD_STREAK, DrossColors.LAND_WARD_STREAK_MID, life * 2.0F)
                : lerpColor(DrossColors.LAND_WARD_STREAK_MID, DrossColors.LAND_WARD_STREAK_FADE, (life - 0.5F) * 2.0F);
        this.rCol = (color >> 16 & 0xFF) / 255.0F;
        this.gCol = (color >> 8 & 0xFF) / 255.0F;
        this.bCol = (color & 0xFF) / 255.0F;
        float in = Math.min(1.0F, life / FADE_IN);
        float out = (float) Math.pow(1.0F - life, FADE_OUT_POWER);
        float shimmer = 1.0F - SHIMMER * 0.5F * (1.0F + Mth.sin(this.age * SHIMMER_SPEED + this.shimmerPhase));
        this.alpha = MAX_ALPHA * in * out * shimmer;
    }

    static int lerpColor(int from, int to, float t)
    {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int b = Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
        return r << 16 | g << 8 | b;
    }

    /**
     * Draws it as an upright strip: its foot at the particle's place, its head {@code length} above, turned round its
     * upright line to face the camera (vanilla's corner order, so it's the front that shows).
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks)
    {
        Vec3 cam = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cam.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cam.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cam.z());
        // The camera's left, flat: up x (the way from the camera to the streak).
        float fx = x;
        float fz = z;
        if (fx * fx + fz * fz < 1.0E-4F)
        {
            Vector3f look = camera.getLookVector();
            fx = look.x();
            fz = look.z();
        }
        float flat = Mth.sqrt(fx * fx + fz * fz);
        if (flat < 1.0E-4F)
        {
            fx = 0.0F;
            fz = 1.0F;
            flat = 1.0F;
        }
        float half = this.width * 0.5F;
        float lx = fz / flat * half;
        float lz = -fx / flat * half;
        float y0 = y;
        float y1 = y + this.length;
        float u0 = this.getU0();
        float u1 = this.getU1();
        float v0 = this.getV0();
        float v1 = this.getV1();
        int light = this.getLightColor(partialTicks);
        buffer.vertex(x - lx, y0, z - lz).uv(u1, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(x - lx, y1, z - lz).uv(u1, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(x + lx, y1, z + lz).uv(u0, v0).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(x + lx, y0, z + lz).uv(u0, v1).color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    /** Light of its own: it glows at full brightness whatever's round it. */
    @Override
    protected int getLightColor(float partialTicks)
    {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Makes a streak from its sprites. */
    public static class Provider implements ParticleProvider<SimpleParticleType>
    {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites)
        {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd)
        {
            return new WardStreakParticle(level, x, y, z, this.sprites);
        }
    }
}
