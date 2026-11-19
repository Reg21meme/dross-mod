package com.reg21meme.dross.quest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.quest.oracle.OracleSpellTuning;
import com.reg21meme.dross.registry.ModParticles;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static com.reg21meme.dross.quest.oracle.OracleSpellTuning.*;

/**
 * CLIENT ONLY. The Blind Oracle's spell as everyone near sees it (QUEST_PLAN.md, "The Oracle's spell";
 * {@code quest.oracle.OracleSpell} sends it, every number in {@link OracleSpellTuning}). After the pause that lets his
 * line be read:
 * <ol>
 *   <li><b>the gathering</b>: his arms go up over his head ({@link #armsRaised}, read by the villager area's model), a
 *       rune circle opens on the ground round him (a small ring inside it turning the other way, a great faint ring
 *       outside it), light rises off its rim and runes spiral in round him, the ground starting to tremble;</li>
 *   <li><b>the beam</b>: it bursts up out of his hands into the sky with a shock ring rolling out over the ground,
 *       pulsing, knots of light running up it, three thinner beams rising off the circle's rim and winding round to
 *       join it;</li>
 *   <li><b>the strike</b>: it bends out of the sky as a lance towards the barrier it breaks, and far off a flash, two
 *       shock rings and a pillar of light burst over the land; a moment later the boom, and its echo;</li>
 *   <li>the beams and the lance shatter into falling shards of light, the circle fades and his arms come down.</li>
 * </ol>
 * Its light is added onto what's behind it and the fog doesn't dim it ({@link SpellRenderTypes}). That's the <b>big</b>
 * spell, for a land opening. The <b>medium</b> one, for a boss's home (since November 19, 2026; {@code MEDIUM_*}), is
 * quick: his arms up, one circle on the ground round him, a beam shot out of his hands towards the home's shield, a
 * flash and a ring where it hits it, the beam breaking into shards; about 5 seconds.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class OracleSpellEffects
{
    private static final ResourceLocation CIRCLE = new ResourceLocation(Dross.MODID, "textures/effect/oracle_spell_circle.png");
    private static final ResourceLocation RING = new ResourceLocation(Dross.MODID, "textures/effect/oracle_spell_ring.png");
    /** The flash and the rings are drawn with this many segments round. */
    private static final int SEGMENTS = 40;
    /** Knots of light running up the beam at once. */
    private static final int KNOTS = 7;
    /** The thin beams off the circle's rim. */
    private static final int SATELLITES = 3;

    private static final List<Spell> SPELLS = new ArrayList<>();

    /** One spell being cast: by which Oracle (or -1), which way it strikes, how long until it starts and how long it has run. */
    private static final class Spell
    {
        final int oracle;
        final boolean big;
        final float dirX;
        final float dirZ;
        final float strikeDistance;
        int wait;
        int age;
        double x;
        double y;
        double z;

        Spell(int oracle, float dirX, float dirZ, float distance, Vec3 at, int wait, boolean big)
        {
            this.oracle = oracle;
            this.big = big;
            float length = Mth.sqrt(dirX * dirX + dirZ * dirZ);
            this.dirX = length > 1.0E-4F ? dirX / length : 1.0F;
            this.dirZ = length > 1.0E-4F ? dirZ / length : 0.0F;
            this.strikeDistance = big ? Mth.clamp(distance, STRIKE_MIN_DISTANCE, STRIKE_DISTANCE)
                    : Mth.clamp(distance, MEDIUM_BEAM_MIN_LENGTH, MEDIUM_BEAM_LENGTH);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.wait = wait;
        }

        boolean started()
        {
            return this.wait <= 0;
        }

        Vec3 feet()
        {
            return new Vec3(x, y, z);
        }

        Vec3 hands()
        {
            return new Vec3(x, y + HANDS_HEIGHT, z);
        }

        Vec3 top()
        {
            return new Vec3(x, y + HANDS_HEIGHT + BEAM_HEIGHT, z);
        }

        Vec3 strike()
        {
            return new Vec3(x + dirX * strikeDistance, y + (big ? STRIKE_HEIGHT : HANDS_HEIGHT + MEDIUM_BEAM_RISE),
                    z + dirZ * strikeDistance);
        }

        int duration()
        {
            return OracleSpellTuning.duration(big);
        }
    }

    private OracleSpellEffects() {}

    // ------------------------------------------------------------------ Starting one, and his arms

    /**
     * From the server: this Oracle (or nobody, -1, on show) casts his spell from x, y, z towards the barrier lying this
     * way, this far off, after {@code delay} ticks; the big spell or the medium one.
     */
    public static void start(int oracle, double x, double y, double z, float dirX, float dirZ, float distance, int delay,
                             boolean big)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
        {
            return;
        }
        Entity entity = oracle >= 0 ? mc.level.getEntity(oracle) : null;
        Vec3 at = entity != null ? entity.position() : new Vec3(x, y, z);
        if (oracle >= 0)
        {
            SPELLS.removeIf(s -> s.oracle == oracle);
        }
        SPELLS.add(new Spell(oracle, dirX, dirZ, distance, at, delay, big));
    }

    /** How far this entity's arms are raised by a spell (0 down, 1 over his head), smoothed between ticks. */
    public static float armsRaised(Entity entity, float partialTick)
    {
        for (Spell spell : SPELLS)
        {
            if (spell.oracle == entity.getId() && spell.started())
            {
                float t = spell.age + partialTick;
                int armsUp = spell.big ? ARMS_UP : MEDIUM_ARMS_UP;
                int armsDown = spell.big ? ARMS_DOWN : MEDIUM_ARMS_DOWN;
                int armsDownEnd = spell.big ? ARMS_DOWN_END : MEDIUM_ARMS_DOWN_END;
                float up = smooth(t / armsUp);
                float down = 1.0F - smooth((t - armsDown) / (armsDownEnd - armsDown));
                return Math.min(up, down);
            }
        }
        return 0.0F;
    }

    // ------------------------------------------------------------------ Each tick: time, sounds, particles

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || SPELLS.isEmpty())
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null)
        {
            SPELLS.clear();
            return;
        }
        if (mc.isPaused())
        {
            return;
        }
        ParticleStatus status = mc.options.particles().get();
        float share = status == ParticleStatus.MINIMAL ? 0.2F : status == ParticleStatus.DECREASED ? 0.5F : 1.0F;
        RandomSource random = level.random;
        for (int i = SPELLS.size() - 1; i >= 0; i--)
        {
            Spell spell = SPELLS.get(i);
            Entity oracle = spell.oracle >= 0 ? level.getEntity(spell.oracle) : null;
            if (oracle != null)
            {
                spell.x = oracle.getX();
                spell.y = oracle.getY();
                spell.z = oracle.getZ();
            }
            if (!spell.started())
            {
                spell.wait--;
                continue;
            }
            int t = spell.age++;
            if (t >= spell.duration())
            {
                SPELLS.remove(i);
                continue;
            }
            if (spell.big)
            {
                sounds(mc, spell, t);
                particles(mc, spell, t, share, random);
            }
            else
            {
                mediumSounds(mc, spell, t);
                mediumParticles(mc, spell, t, share, random);
            }
        }
    }

    private static void sounds(Minecraft mc, Spell spell, int t)
    {
        if (t == 0)
        {
            play(mc, spell.feet(), ModSounds.ORACLE_SPELL_CHARGE.get(), 1.4F, 1.0F);
        }
        if (t == GATHER_START + 16 || t == GATHER_START + 34)
        {
            play(mc, spell.feet(), ModSounds.ORACLE_SPELL_CHARGE.get(), 1.0F, t == GATHER_START + 16 ? 0.85F : 1.1F);
        }
        if (t == BEAM_START - 14)
        {
            play(mc, spell.hands(), ModSounds.ORACLE_SPELL_SURGE.get(), 1.6F, 0.9F);
        }
        if (t == BEAM_START)
        {
            play(mc, spell.hands(), ModSounds.ORACLE_SPELL_BEAM.get(), 1.8F, 1.0F);
            play(mc, spell.hands(), ModSounds.ORACLE_SPELL_STRIKE.get(), 1.4F, 1.15F);
            play(mc, spell.feet(), ModSounds.ORACLE_SPELL_RUMBLE.get(), 1.2F, 1.2F);
        }
        if (t == BEAM_START + 30 || t == BEAM_START + 60)
        {
            play(mc, spell.hands().add(0.0D, 12.0D, 0.0D), ModSounds.ORACLE_SPELL_SURGE.get(), 1.1F, 0.8F);
        }
        if (t == LANCE_START)
        {
            play(mc, spell.hands().add(0.0D, 10.0D, 0.0D), ModSounds.ORACLE_SPELL_STRIKE.get(), 1.8F, 0.9F);
        }
        if ((t == STRIKE + BOOM_DELAY || t == STRIKE + BOOM_DELAY + ECHO_DELAY) && mc.player != null)
        {
            // Far off, but always heard: put it between the listener and the strike, the way it lies.
            boolean echo = t != STRIKE + BOOM_DELAY;
            Vec3 ear = mc.player.getEyePosition();
            Vec3 way = spell.strike().subtract(ear);
            Vec3 at = ear.add(way.normalize().scale(Math.min(way.length(), echo ? 30.0D : 22.0D)));
            play(mc, at, ModSounds.ORACLE_SPELL_BOOM.get(), echo ? 1.2F : 2.4F, echo ? 0.85F : 1.0F);
            play(mc, at, ModSounds.ORACLE_SPELL_RUMBLE.get(), echo ? 1.2F : 2.0F, echo ? 0.9F : 1.0F);
        }
        if (t == SHATTER)
        {
            play(mc, spell.hands(), ModSounds.ORACLE_SPELL_SHATTER.get(), 1.6F, 1.0F);
            play(mc, spell.hands().add(0.0D, 15.0D, 0.0D), ModSounds.ORACLE_SPELL_SHATTER.get(), 1.4F, 0.8F);
        }
    }

    // ------------------------------------------------------------------ The medium spell: sounds and particles

    private static void mediumSounds(Minecraft mc, Spell spell, int t)
    {
        if (t == 0)
        {
            play(mc, spell.feet(), ModSounds.ORACLE_SPELL_CHARGE.get(), 1.0F, 1.2F);
        }
        if (t == MEDIUM_BEAM_START)
        {
            play(mc, spell.hands(), ModSounds.ORACLE_SPELL_STRIKE.get(), 1.4F, 1.25F);
            play(mc, spell.hands(), ModSounds.ORACLE_SPELL_BEAM.get(), 1.0F, 1.3F);
        }
        if (t == MEDIUM_BEAM_START + MEDIUM_BEAM_GROW && mc.player != null)
        {
            // The shield cracking where it hits: put it between the listener and the hit, so it's always heard.
            Vec3 ear = mc.player.getEyePosition();
            Vec3 way = spell.strike().subtract(ear);
            Vec3 at = ear.add(way.normalize().scale(Math.min(way.length(), 16.0D)));
            play(mc, at, ModSounds.ORACLE_SPELL_SHATTER.get(), 1.4F, 1.3F);
            play(mc, at, ModSounds.ORACLE_SPELL_BOOM.get(), 0.8F, 1.4F);
        }
        if (t == MEDIUM_BEAM_END)
        {
            play(mc, spell.hands(), ModSounds.ORACLE_SPELL_SHATTER.get(), 0.9F, 1.15F);
        }
    }

    private static void mediumParticles(Minecraft mc, Spell spell, int t, float share, RandomSource random)
    {
        float circle = smooth((float) t / MEDIUM_CIRCLE_GROW);
        if (t < MEDIUM_CIRCLE_FADE && circle > 0.2F)
        {
            for (int k = 0; k < count(MEDIUM_RIM_STREAKS * share, random); k++)
            {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double r = MEDIUM_CIRCLE_RADIUS * circle * (0.9D + random.nextDouble() * 0.1D);
                mc.particleEngine.createParticle(ModParticles.WARD_STREAK.get(), spell.x + Math.cos(a) * r, spell.y,
                        spell.z + Math.sin(a) * r, 0.0D, 0.0D, 0.0D);
            }
        }
        if (t == MEDIUM_BEAM_START)
        {
            Vec3 h = spell.hands();
            for (int k = 0; k < (int) (MEDIUM_BURST_SHARDS * share); k++)
            {
                mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), h.x, h.y, h.z, (random.nextDouble() - 0.5D) * 0.5D,
                        0.1D + random.nextDouble() * 0.3D, (random.nextDouble() - 0.5D) * 0.5D);
            }
        }
        if (t == MEDIUM_BEAM_START + MEDIUM_BEAM_GROW)
        {
            Vec3 s = spell.strike();
            for (int k = 0; k < (int) (MEDIUM_HIT_SHARDS * share); k++)
            {
                mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), s.x, s.y, s.z, random.nextGaussian() * 0.35D,
                        random.nextGaussian() * 0.3D, random.nextGaussian() * 0.35D);
            }
        }
        if (t >= MEDIUM_BEAM_END && t < MEDIUM_BEAM_END + MEDIUM_BEAM_FADE)
        {
            Vec3 h = spell.hands();
            Vec3 s = spell.strike();
            for (int k = 0; k < (int) (8 * share); k++)
            {
                Vec3 p = h.lerp(s, random.nextDouble());
                mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), p.x, p.y, p.z, random.nextGaussian() * 0.04D,
                        -0.05D - random.nextDouble() * 0.15D, random.nextGaussian() * 0.04D);
            }
        }
    }

    private static void particles(Minecraft mc, Spell spell, int t, float share, RandomSource random)
    {
        float circle = circleSize(t);
        if (t < CIRCLE_FADE && circle > 0.2F)
        {
            // Light rising off the circle's rim (thicker while it gathers), runes drifting up off it.
            float streaks = (t >= GATHER_START && t < BEAM_START ? GATHER_STREAKS : RIM_STREAKS) * share;
            for (int k = 0; k < count(streaks, random); k++)
            {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double r = CIRCLE_RADIUS * circle * (0.92D + random.nextDouble() * 0.08D);
                mc.particleEngine.createParticle(ModParticles.WARD_STREAK.get(), spell.x + Math.cos(a) * r, spell.y,
                        spell.z + Math.sin(a) * r, 0.0D, 0.0D, 0.0D);
            }
            if (random.nextFloat() < CIRCLE_RUNES * share)
            {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double r = CIRCLE_RADIUS * circle * random.nextDouble();
                mc.particleEngine.createParticle(ModParticles.WARD_RUNE.get(), spell.x + Math.cos(a) * r, spell.y + 0.4D,
                        spell.z + Math.sin(a) * r, 0.0D, 0.0D, 0.0D);
            }
        }
        if (t >= GATHER_START && t < BEAM_START)
        {
            // The gathering: shards of light spiral in to him and up.
            for (int k = 0; k < count(GATHER_RUNES * share, random); k++)
            {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double r = CIRCLE_RADIUS * OUTER_SHARE * (0.6D + random.nextDouble() * 0.4D);
                double px = spell.x + Math.cos(a) * r;
                double pz = spell.z + Math.sin(a) * r;
                double py = spell.y + 0.5D + random.nextDouble() * 2.0D;
                Vec3 to = spell.hands().subtract(px, py, pz).scale(0.06D);
                // (a little sideways too, so they swirl in)
                mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), px, py, pz, to.x - Math.sin(a) * 0.12D,
                        to.y + 0.18D, to.z + Math.cos(a) * 0.12D);
            }
        }
        if (t == BEAM_START)
        {
            // The burst as the beam leaves his hands.
            for (int k = 0; k < (int) (40 * share); k++)
            {
                mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), spell.x, spell.y + HANDS_HEIGHT, spell.z,
                        (random.nextDouble() - 0.5D) * 0.9D, 0.2D + random.nextDouble() * 0.5D, (random.nextDouble() - 0.5D) * 0.9D);
            }
        }
        if (t >= SHATTER && t < SHATTER + SHATTER_GROW + 6)
        {
            // The beams and the lance break into falling shards of light.
            Vec3 hands = spell.hands();
            Vec3 top = spell.top();
            Vec3 strike = spell.strike();
            for (int k = 0; k < (int) (SHATTER_SPARKS * share); k++)
            {
                Vec3 p = random.nextFloat() < 0.6F ? hands.lerp(top, random.nextDouble() * 0.6D) : top.lerp(strike, random.nextDouble());
                mc.particleEngine.createParticle(ModParticles.WARD_SHARD.get(), p.x + random.nextGaussian() * 0.8D, p.y,
                        p.z + random.nextGaussian() * 0.8D, random.nextGaussian() * 0.06D, -0.05D - random.nextDouble() * 0.2D,
                        random.nextGaussian() * 0.06D);
            }
        }
    }

    // ------------------------------------------------------------------ Drawing

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SPELLS.isEmpty())
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partial = event.getPartialTick();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        for (Spell spell : SPELLS)
        {
            if (spell.started())
            {
                if (spell.big)
                {
                    drawCircles(buffers, m, spell, spell.age + partial);
                }
                else
                {
                    drawMediumCircle(buffers, m, spell, spell.age + partial);
                }
            }
        }
        buffers.endBatch(SpellRenderTypes.textured(CIRCLE));
        buffers.endBatch(SpellRenderTypes.textured(RING));
        VertexConsumer glow = buffers.getBuffer(SpellRenderTypes.GLOW);
        for (Spell spell : SPELLS)
        {
            if (spell.started())
            {
                float t = spell.age + partial;
                if (spell.big)
                {
                    drawGroundRing(glow, m, spell, t);
                    drawBeams(glow, m, spell, t, cam);
                    drawStrike(glow, m, spell, t, camera, cam);
                }
                else
                {
                    drawMediumBeam(glow, m, spell, t, camera, cam);
                }
            }
        }
        buffers.endBatch(SpellRenderTypes.GLOW);
        pose.popPose();
    }

    /** The big circle, the small ring inside it and the great faint ring outside it, on the ground round his feet. */
    private static void drawCircles(MultiBufferSource buffers, Matrix4f m, Spell spell, float t)
    {
        float size = circleSize(t);
        float fade = 1.0F - smooth((t - CIRCLE_FADE) / (DURATION - CIRCLE_FADE));
        if (size <= 0.01F || fade <= 0.01F)
        {
            return;
        }
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.35F);
        float y = (float) spell.y + 0.06F;
        float r = CIRCLE_RADIUS * size;
        flatQuad(buffers.getBuffer(SpellRenderTypes.textured(CIRCLE)), m, (float) spell.x, y, (float) spell.z, r,
                t * CIRCLE_SPIN * Mth.DEG_TO_RAD, DrossColors.ORACLE_SPELL_CIRCLE, fade * pulse);
        flatQuad(buffers.getBuffer(SpellRenderTypes.textured(RING)), m, (float) spell.x, y + 0.02F, (float) spell.z,
                r * RING_SHARE, t * RING_SPIN * Mth.DEG_TO_RAD, DrossColors.ORACLE_SPELL_RING, fade);
        float outer = smooth((t - OUTER_START) / OUTER_GROW);
        if (outer > 0.01F)
        {
            flatQuad(buffers.getBuffer(SpellRenderTypes.textured(CIRCLE)), m, (float) spell.x, y - 0.02F, (float) spell.z,
                    CIRCLE_RADIUS * OUTER_SHARE * outer, -t * OUTER_SPIN * Mth.DEG_TO_RAD, DrossColors.ORACLE_SPELL_GLOW,
                    0.4F * fade * outer);
        }
        // Copies of the ring floating up over the circle while the beam stands (the circle "lifts").
        if (t > BEAM_START && t < SHATTER + SHATTER_GROW)
        {
            for (int k = 0; k < 2; k++)
            {
                float lift = Mth.frac((t - BEAM_START) / 50.0F + k * 0.5F);
                flatQuad(buffers.getBuffer(SpellRenderTypes.textured(RING)), m, (float) spell.x, y + 0.4F + lift * 4.0F,
                        (float) spell.z, r * RING_SHARE * (0.8F + lift * 0.6F), -t * RING_SPIN * Mth.DEG_TO_RAD,
                        DrossColors.ORACLE_SPELL_GLOW, 0.5F * fade * (1.0F - lift));
            }
        }
    }

    /** The medium spell's one circle on the ground round him. */
    private static void drawMediumCircle(MultiBufferSource buffers, Matrix4f m, Spell spell, float t)
    {
        float size = smooth(t / MEDIUM_CIRCLE_GROW);
        float fade = 1.0F - smooth((t - MEDIUM_CIRCLE_FADE) / (MEDIUM_DURATION - MEDIUM_CIRCLE_FADE));
        if (size <= 0.01F || fade <= 0.01F)
        {
            return;
        }
        float pulse = 0.85F + 0.15F * Mth.sin(t * 0.45F);
        flatQuad(buffers.getBuffer(SpellRenderTypes.textured(CIRCLE)), m, (float) spell.x, (float) spell.y + 0.06F,
                (float) spell.z, MEDIUM_CIRCLE_RADIUS * size, t * CIRCLE_SPIN * 1.5F * Mth.DEG_TO_RAD,
                DrossColors.ORACLE_SPELL_CIRCLE, fade * pulse);
    }

    /** The medium spell's beam out of his hands to the home's shield, and the flash and ring where it hits it. */
    private static void drawMediumBeam(VertexConsumer vc, Matrix4f m, Spell spell, float t, Camera camera, Vec3 cam)
    {
        float grow = smooth((t - MEDIUM_BEAM_START) / MEDIUM_BEAM_GROW);
        float gone = 1.0F - smooth((t - MEDIUM_BEAM_END) / MEDIUM_BEAM_FADE);
        float strength = grow * gone;
        if (strength > 0.01F)
        {
            float pulse = 1.0F + BEAM_PULSE * Mth.sin(t * BEAM_PULSE_SPEED);
            Vec3 from = spell.hands();
            Vec3 to = from.lerp(spell.strike(), grow);
            ribbon(vc, m, from, to, cam, MEDIUM_BEAM_GLOW * pulse * strength, DrossColors.ORACLE_SPELL_GLOW, 0.6F * strength, 0.5F * strength);
            ribbon(vc, m, from, to, cam, MEDIUM_BEAM_CORE * pulse * strength, DrossColors.ORACLE_SPELL_CORE, strength, strength);
        }
        float since = t - (MEDIUM_BEAM_START + MEDIUM_BEAM_GROW);
        if (since < 0.0F)
        {
            return;
        }
        Vec3 at = spell.strike();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        if (since < MEDIUM_FLASH_TICKS)
        {
            float life = since / MEDIUM_FLASH_TICKS;
            float alpha = (1.0F - life) * (1.0F - life);
            disc(vc, m, at, left, up, MEDIUM_FLASH_SIZE + since * 0.3F, DrossColors.ORACLE_SPELL_CORE, alpha);
            disc(vc, m, at, left, up, MEDIUM_FLASH_SIZE * 2.2F, DrossColors.ORACLE_SPELL_GLOW, alpha * 0.5F);
        }
        if (since < MEDIUM_RING_TICKS)
        {
            float size = 2.0F + since * MEDIUM_RING_GROWTH;
            ring(vc, m, at, left, up, size, size * 0.12F, DrossColors.ORACLE_SPELL_GLOW, 1.0F - since / MEDIUM_RING_TICKS);
        }
    }

    /** The shock ring that rolls out over the ground as the beam bursts up. */
    private static void drawGroundRing(VertexConsumer vc, Matrix4f m, Spell spell, float t)
    {
        float since = t - BEAM_START;
        if (since < 0.0F || since > GROUND_RING_TICKS)
        {
            return;
        }
        float life = since / GROUND_RING_TICKS;
        float radius = 2.0F + GROUND_RING_REACH * smoothOut(life);
        ring(vc, m, spell.feet().add(0.0D, 0.15D, 0.0D), new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F),
                radius, 1.2F + radius * 0.08F, DrossColors.ORACLE_SPELL_CORE, (1.0F - life) * 0.9F);
    }

    /** The beam up out of his hands, the thin beams off the rim winding up into it, and the lance towards the barrier. */
    private static void drawBeams(VertexConsumer vc, Matrix4f m, Spell spell, float t, Vec3 cam)
    {
        float grow = smooth((t - BEAM_START) / BEAM_GROW);
        float gone = 1.0F - smooth((t - SHATTER) / SHATTER_GROW);
        float strength = grow * gone;
        if (strength <= 0.01F)
        {
            return;
        }
        float pulse = 1.0F + BEAM_PULSE * Mth.sin(t * BEAM_PULSE_SPEED);
        Vec3 hands = spell.hands();
        Vec3 top = hands.lerp(spell.top(), grow);
        ribbon(vc, m, hands, top, cam, BEAM_GLOW * pulse * strength, DrossColors.ORACLE_SPELL_GLOW, 0.6F * strength, 0.2F * strength);
        ribbon(vc, m, hands, top, cam, BEAM_CORE * pulse * strength, DrossColors.ORACLE_SPELL_CORE, 1.0F * strength, 0.5F * strength);
        for (int k = 0; k < KNOTS; k++)
        {
            float s = Mth.frac(t * 0.03F + (float) k / KNOTS);
            Vec3 a = hands.lerp(top, s);
            Vec3 b = hands.lerp(top, Math.min(1.0F, s + 0.04F));
            ribbon(vc, m, a, b, cam, BEAM_CORE * 2.4F * strength, DrossColors.ORACLE_SPELL_CORE, 0.6F * strength, 0.6F * strength);
        }
        // The thin beams from the circle's rim, winding round as they rise into the great one.
        float sat = smooth((t - SATELLITE_START) / 10.0F) * gone;
        if (sat > 0.01F)
        {
            Vec3 join = spell.feet().add(0.0D, SATELLITE_JOIN, 0.0D);
            for (int k = 0; k < SATELLITES; k++)
            {
                float a0 = (t * SATELLITE_SPIN + k * 360.0F / SATELLITES) * Mth.DEG_TO_RAD;
                Vec3 prev = spell.feet().add(Math.cos(a0) * CIRCLE_RADIUS, 0.2D, Math.sin(a0) * CIRCLE_RADIUS);
                int pieces = 8;
                for (int p = 1; p <= pieces; p++)
                {
                    float f = (float) p / pieces;
                    float a = a0 + f * 1.4F;
                    double r = CIRCLE_RADIUS * (1.0F - f);
                    Vec3 next = new Vec3(spell.x + Math.cos(a) * r, Mth.lerp(f, (float) spell.y + 0.2F, (float) join.y),
                            spell.z + Math.sin(a) * r);
                    ribbon(vc, m, prev, next, cam, SATELLITE_WIDTH * 1.8F * sat, DrossColors.ORACLE_SPELL_GLOW, 0.4F * sat, 0.4F * sat);
                    ribbon(vc, m, prev, next, cam, SATELLITE_WIDTH * 0.6F * sat, DrossColors.ORACLE_SPELL_CORE, 0.8F * sat, 0.8F * sat);
                    prev = next;
                }
            }
        }
        float lance = smooth((t - LANCE_START) / LANCE_GROW) * gone;
        if (lance > 0.01F)
        {
            Vec3 from = spell.top();
            Vec3 to = from.lerp(spell.strike(), smooth((t - LANCE_START) / LANCE_GROW));
            ribbon(vc, m, from, to, cam, LANCE_GLOW * pulse * lance, DrossColors.ORACLE_SPELL_GLOW, 0.5F * lance, 0.6F * lance);
            ribbon(vc, m, from, to, cam, LANCE_CORE * pulse * lance, DrossColors.ORACLE_SPELL_CORE, 1.0F * lance, 1.0F * lance);
        }
    }

    /** The flash far off, the shock rings (one facing you, one spreading flat) and the pillar of light under it. */
    private static void drawStrike(VertexConsumer vc, Matrix4f m, Spell spell, float t, Camera camera, Vec3 cam)
    {
        float since = t - STRIKE;
        if (since < 0.0F)
        {
            return;
        }
        Vec3 at = spell.strike();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        if (since < FLASH_TICKS)
        {
            float life = since / FLASH_TICKS;
            float size = FLASH_SIZE + since * FLASH_GROWTH;
            float alpha = (1.0F - life) * (1.0F - life);
            disc(vc, m, at, left, up, size, DrossColors.ORACLE_SPELL_CORE, alpha);
            disc(vc, m, at, left, up, size * 2.6F, DrossColors.ORACLE_SPELL_GLOW, alpha * 0.5F);
        }
        if (since < RING_TICKS)
        {
            float life = since / RING_TICKS;
            float size = RING_START_SIZE + since * RING_GROWTH;
            float alpha = 1.0F - life;
            ring(vc, m, at, left, up, size, size * 0.12F, DrossColors.ORACLE_SPELL_GLOW, alpha);
            ring(vc, m, at, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), size * 1.3F, size * 0.08F,
                    DrossColors.ORACLE_SPELL_CORE, alpha * 0.8F);
            // A second, later ring, slower.
            if (since > 6.0F)
            {
                float s2 = RING_START_SIZE + (since - 6.0F) * RING_GROWTH * 0.6F;
                ring(vc, m, at, new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), s2, s2 * 0.1F,
                        DrossColors.ORACLE_SPELL_GLOW, alpha * 0.7F);
            }
        }
        if (since < PILLAR_TICKS)
        {
            float life = since / PILLAR_TICKS;
            float alpha = (1.0F - life) * smooth(since / 3.0F);
            Vec3 down = at.add(0.0D, -PILLAR_DEPTH, 0.0D);
            ribbon(vc, m, at.add(0.0D, 20.0D, 0.0D), down, cam, 3.5F * (1.0F - life * 0.5F), DrossColors.ORACLE_SPELL_GLOW,
                    0.5F * alpha, 0.5F * alpha);
            ribbon(vc, m, at.add(0.0D, 20.0D, 0.0D), down, cam, 1.2F * (1.0F - life * 0.5F), DrossColors.ORACLE_SPELL_CORE,
                    alpha, alpha);
        }
    }

    // ------------------------------------------------------------------ The ground shaking

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event)
    {
        if (SPELLS.isEmpty())
        {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float shake = 0.0F;
        float time = 0.0F;
        for (Spell spell : SPELLS)
        {
            if (!spell.started())
            {
                continue;
            }
            double d = cam.distanceTo(spell.feet());
            if (d > SHAKE_RANGE)
            {
                continue;
            }
            float t = spell.age + (float) event.getPartialTick();
            float a = 0.0F;
            if (!spell.big)
            {
                if (t >= MEDIUM_BEAM_START && t < MEDIUM_BEAM_START + SHAKE_STRIKE_TICKS)
                {
                    a = MEDIUM_SHAKE * (1.0F - (t - MEDIUM_BEAM_START) / SHAKE_STRIKE_TICKS);
                }
            }
            else if (t > GATHER_START && t < BEAM_START)
            {
                a += SHAKE_GATHER * (t - GATHER_START) / (BEAM_START - GATHER_START);
            }
            if (spell.big && t >= BEAM_START && t < SHATTER)
            {
                a += SHAKE_BEAM;
            }
            for (int burst : spell.big ? new int[] {BEAM_START, STRIKE} : new int[0])
            {
                if (t >= burst && t < burst + SHAKE_STRIKE_TICKS)
                {
                    a += SHAKE_STRIKE * (1.0F - (t - burst) / SHAKE_STRIKE_TICKS);
                }
            }
            a *= (float) (1.0D - d / SHAKE_RANGE);
            if (a > shake)
            {
                shake = a;
                time = t;
            }
        }
        if (shake > 0.0F)
        {
            event.setYaw(event.getYaw() + shake * Mth.sin(time * 2.1F) * Mth.cos(time * 0.9F));
            event.setPitch(event.getPitch() + shake * Mth.sin(time * 2.7F + 1.3F));
            event.setRoll(event.getRoll() + shake * 0.5F * Mth.sin(time * 1.7F + 0.4F));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        SPELLS.clear();
    }

    // ------------------------------------------------------------------ Shapes

    private static float circleSize(float t)
    {
        return smooth(t / CIRCLE_GROW);
    }

    /** A square lying flat, turned by {@code angle}, its texture across it. */
    private static void flatQuad(VertexConsumer vc, Matrix4f m, float cx, float y, float cz, float r, float angle, int color,
                                 float alpha)
    {
        float c = Mth.cos(angle) * r;
        float s = Mth.sin(angle) * r;
        float[] rgb = rgb(color);
        vertexTex(vc, m, cx - c + s, y, cz - s - c, rgb, alpha, 0.0F, 0.0F);
        vertexTex(vc, m, cx - c - s, y, cz - s + c, rgb, alpha, 0.0F, 1.0F);
        vertexTex(vc, m, cx + c - s, y, cz + s + c, rgb, alpha, 1.0F, 1.0F);
        vertexTex(vc, m, cx + c + s, y, cz + s - c, rgb, alpha, 1.0F, 0.0F);
    }

    /**
     * A strip of light from a to b turned to face the camera, {@code half} wide either side of its line, brightest along
     * it and fading to nothing at its edges ({@code alphaA} at a, {@code alphaB} at b).
     */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float half, int color, float alphaA,
                               float alphaB)
    {
        Vec3 axis = b.subtract(a);
        if (axis.lengthSqr() < 1.0E-6D || half <= 0.0F)
        {
            return;
        }
        Vec3 sideA = side(axis, cam.subtract(a)).scale(half);
        Vec3 sideB = side(axis, cam.subtract(b)).scale(half);
        float[] rgb = rgb(color);
        vertex(vc, m, a.subtract(sideA), rgb, 0.0F);
        vertex(vc, m, a, rgb, alphaA);
        vertex(vc, m, b, rgb, alphaB);
        vertex(vc, m, b.subtract(sideB), rgb, 0.0F);
        vertex(vc, m, a, rgb, alphaA);
        vertex(vc, m, a.add(sideA), rgb, 0.0F);
        vertex(vc, m, b.add(sideB), rgb, 0.0F);
        vertex(vc, m, b, rgb, alphaB);
    }

    /** A unit vector across the line {@code axis}, square to the way the camera looks at it. */
    private static Vec3 side(Vec3 axis, Vec3 toCamera)
    {
        Vec3 s = axis.cross(toCamera);
        if (s.lengthSqr() < 1.0E-8D)
        {
            s = axis.cross(new Vec3(0.0D, 1.0D, 0.0D));
            if (s.lengthSqr() < 1.0E-8D)
            {
                s = new Vec3(1.0D, 0.0D, 0.0D);
            }
        }
        return s.normalize();
    }

    /** A round glow in the plane of {@code u} and {@code v}: bright in its middle, nothing at its rim. */
    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f u, Vector3f v, float radius, int color, float alpha)
    {
        float[] rgb = rgb(color);
        for (int i = 0; i < SEGMENTS; i++)
        {
            Vec3 p0 = round(c, u, v, radius, i);
            Vec3 p1 = round(c, u, v, radius, i + 1);
            vertex(vc, m, c, rgb, alpha);
            vertex(vc, m, p0, rgb, 0.0F);
            vertex(vc, m, p1, rgb, 0.0F);
            vertex(vc, m, c, rgb, alpha);
        }
    }

    /** A ring of light in the plane of {@code u} and {@code v}: brightest at {@code radius}, fading over {@code width} either side. */
    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f u, Vector3f v, float radius, float width, int color,
                             float alpha)
    {
        float[] rgb = rgb(color);
        float inner = Math.max(0.0F, radius - width);
        float outer = radius + width;
        for (int i = 0; i < SEGMENTS; i++)
        {
            vertex(vc, m, round(c, u, v, inner, i), rgb, 0.0F);
            vertex(vc, m, round(c, u, v, radius, i), rgb, alpha);
            vertex(vc, m, round(c, u, v, radius, i + 1), rgb, alpha);
            vertex(vc, m, round(c, u, v, inner, i + 1), rgb, 0.0F);
            vertex(vc, m, round(c, u, v, radius, i), rgb, alpha);
            vertex(vc, m, round(c, u, v, outer, i), rgb, 0.0F);
            vertex(vc, m, round(c, u, v, outer, i + 1), rgb, 0.0F);
            vertex(vc, m, round(c, u, v, radius, i + 1), rgb, alpha);
        }
    }

    private static Vec3 round(Vec3 c, Vector3f u, Vector3f v, float r, int i)
    {
        float a = Mth.TWO_PI * i / SEGMENTS;
        float cu = Mth.cos(a) * r;
        float sv = Mth.sin(a) * r;
        return c.add(u.x() * cu + v.x() * sv, u.y() * cu + v.y() * sv, u.z() * cu + v.z() * sv);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, float[] rgb, float alpha)
    {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(rgb[0], rgb[1], rgb[2], Mth.clamp(alpha, 0.0F, 1.0F)).endVertex();
    }

    private static void vertexTex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float[] rgb, float alpha, float u,
                                  float v)
    {
        vc.vertex(m, x, y, z).color(rgb[0], rgb[1], rgb[2], Mth.clamp(alpha, 0.0F, 1.0F)).uv(u, v).endVertex();
    }

    private static float[] rgb(int color)
    {
        return new float[] {(color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F};
    }

    /** 0 below 0, 1 above 1, smooth between. */
    private static float smooth(float x)
    {
        float c = Mth.clamp(x, 0.0F, 1.0F);
        return c * c * (3.0F - 2.0F * c);
    }

    /** 0 to 1, fast at first and slowing (for things rolling out). */
    private static float smoothOut(float x)
    {
        float c = Mth.clamp(x, 0.0F, 1.0F);
        return 1.0F - (1.0F - c) * (1.0F - c);
    }

    private static int count(float expected, RandomSource random)
    {
        int whole = (int) expected;
        return random.nextFloat() < expected - whole ? whole + 1 : whole;
    }

    private static void play(Minecraft mc, Vec3 at, SoundEvent sound, float volume, float pitch)
    {
        if (mc.level == null)
        {
            return;
        }
        mc.getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.NEUTRAL, volume, pitch, mc.level.random, at.x, at.y, at.z));
    }
}
