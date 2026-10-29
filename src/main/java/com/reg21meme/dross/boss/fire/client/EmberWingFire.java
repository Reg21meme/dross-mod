package com.reg21meme.dross.boss.fire.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.fire.FireCandidate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Fire falling from the Emberdactyl's wings as it flies (the user, October 27, 2026: "as he flies fire needs to fall
 * from his wings like particles, and later in the battle actual fire"). Client only, just particles: nothing catches
 * fire (the fight's real fire comes later).
 * <p>
 * Its model has marker bones along each wing's trailing edge and at its tip ({@code ember_l0} ... {@code ember_l9},
 * {@code ember_r0} ... {@code ember_r9}, made by {@code tools/fire/firekit/emberdactyl.py}; they follow the wing and its
 * membrane as they move). The renderer has GeckoLib track them ({@link #prepare}) and reads where they are right after
 * drawing each Emberdactyl ({@link #capture}; GeckoLib shares the bones between everything drawn with one model); then
 * each client tick, while it plays a flying animation ({@link #FLYING}), flames, glowing embers that cool as they
 * fall, now and then a drop of lava and a wisp of smoke leave random spots along each wing's edge. Its young sheds
 * {@link #YOUNG_MORE} times as many (the user: "much more magma"). Only while it's drawn (on screen) and within
 * {@link #MAX_DISTANCE} blocks; the video settings' particle level is respected (none on Minimal, half on Decreased).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class EmberWingFire
{
    /** Markers on each wing, from the root of its trailing edge out to its tip. */
    private static final int MARKERS = 10;
    private static final String[] LEFT = names("ember_l");
    private static final String[] RIGHT = names("ember_r");
    /** The animations it flies in (the ground ones and its death shed nothing). */
    private static final Set<String> FLYING = Set.of("hover", "fly", "glide", "dive", "gust", "grab", "screech");
    /** Per wing, each tick (on average): flames, embers, drops of lava, wisps of smoke (the adult). */
    private static final double FLAMES = 1.4D;
    private static final double EMBERS = 0.9D;
    private static final double DRIPS = 0.12D;
    private static final double SMOKE = 0.1D;
    /** Its young (the mob "emberyoung") sheds this many times as much. */
    private static final double YOUNG_MORE = 1.7D;
    /** How fast what falls leaves the wing (blocks a tick, downwards), and how much it drifts sideways. */
    private static final double FALL_MIN = 0.04D;
    private static final double FALL_MAX = 0.13D;
    private static final double DRIFT = 0.025D;
    /** A share of the wing's own movement each piece takes with it (a downstroke flings them down). */
    private static final double CARRY = 0.35D;
    /** Embers: their size, and how far a spot is scattered round the edge (blocks). */
    private static final float EMBER_MIN = 1.2F;
    private static final float EMBER_MAX = 2.4F;
    private static final double SCATTER = 0.35D;
    /** No fire from one further than this from the camera (blocks), or not drawn for this many ticks (off screen). */
    private static final double MAX_DISTANCE = 160.0D;
    private static final long STALE_TICKS = 3L;
    private static final String YOUNG = "emberyoung";

    /** Where each Emberdactyl's wing markers were when last drawn, and the time before that (for their movement). */
    private static final Map<FireCandidate, Wings> WINGS = new WeakHashMap<>();

    private record Wings(Vec3[] left, Vec3[] right, Vec3[] leftBefore, Vec3[] rightBefore, long tick)
    {
    }

    private EmberWingFire() {}

    private static String[] names(String prefix)
    {
        String[] out = new String[MARKERS];
        for (int i = 0; i < MARKERS; i++)
        {
            out[i] = prefix + i;
        }
        return out;
    }

    /** Before drawing: GeckoLib works out the markers' places (a model without them is left alone). */
    public static void prepare(BakedGeoModel model, boolean isReRender)
    {
        if (isReRender)
        {
            return;
        }
        for (String[] side : new String[][] {LEFT, RIGHT})
        {
            for (String name : side)
            {
                model.getBone(name).ifPresent(bone -> bone.setTrackingMatrices(true));
            }
        }
    }

    /** Right after drawing one: where its markers are now (world positions). */
    public static void capture(FireCandidate candidate, BakedGeoModel model, boolean isReRender)
    {
        if (isReRender || candidate.level() == null)
        {
            return;
        }
        Vec3[] left = read(model, LEFT);
        Vec3[] right = read(model, RIGHT);
        if (left == null || right == null)
        {
            return;
        }
        long tick = candidate.level().getGameTime();
        Wings before = WINGS.get(candidate);
        if (before != null && before.tick() == tick)
        {
            WINGS.put(candidate, new Wings(left, right, before.leftBefore(), before.rightBefore(), tick));
        }
        else
        {
            WINGS.put(candidate, new Wings(left, right, before == null ? left : before.left(),
                    before == null ? right : before.right(), tick));
        }
    }

    private static Vec3[] read(BakedGeoModel model, String[] names)
    {
        Vec3[] out = new Vec3[names.length];
        for (int i = 0; i < names.length; i++)
        {
            GeoBone bone = model.getBone(names[i]).orElse(null);
            if (bone == null)
            {
                return null;
            }
            Vector3d p = bone.getWorldPosition();
            out[i] = new Vec3(p.x, p.y, p.z);
        }
        return out;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || WINGS.isEmpty())
        {
            if (level == null)
            {
                WINGS.clear();
            }
            return;
        }
        ParticleStatus status = mc.options.particles().get();
        if (status == ParticleStatus.MINIMAL)
        {
            return;
        }
        double share = status == ParticleStatus.DECREASED ? 0.5D : 1.0D;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        long now = level.getGameTime();
        Iterator<Map.Entry<FireCandidate, Wings>> it = WINGS.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<FireCandidate, Wings> entry = it.next();
            FireCandidate candidate = entry.getKey();
            Wings wings = entry.getValue();
            if (candidate.isRemoved() || candidate.level() != level)
            {
                it.remove();
                continue;
            }
            if (now - wings.tick() > STALE_TICKS || !FLYING.contains(candidate.animation())
                    || candidate.position().distanceToSqr(camera) > MAX_DISTANCE * MAX_DISTANCE)
            {
                continue;
            }
            double more = (YOUNG.equals(candidate.getDesign().mob()) ? YOUNG_MORE : 1.0D) * share;
            shed(level, wings.left(), wings.leftBefore(), more);
            shed(level, wings.right(), wings.rightBefore(), more);
        }
    }

    /** One wing's fire for one tick. */
    private static void shed(ClientLevel level, Vec3[] edge, Vec3[] before, double more)
    {
        RandomSource random = level.random;
        for (int kind = 0; kind < 4; kind++)
        {
            double rate = more * (kind == 0 ? FLAMES : kind == 1 ? EMBERS : kind == 2 ? DRIPS : SMOKE);
            int n = (int) rate + (random.nextDouble() < rate - (int) rate ? 1 : 0);
            for (int j = 0; j < n; j++)
            {
                int i = random.nextInt(edge.length - 1);
                double u = random.nextDouble();
                Vec3 at = edge[i].lerp(edge[i + 1], u).add((random.nextDouble() - 0.5D) * SCATTER,
                        (random.nextDouble() - 0.5D) * SCATTER, (random.nextDouble() - 0.5D) * SCATTER);
                Vec3 moved = edge[i].lerp(edge[i + 1], u).subtract(before[i].lerp(before[i + 1], u)).scale(CARRY);
                double fall = FALL_MIN + random.nextDouble() * (FALL_MAX - FALL_MIN);
                double dx = moved.x + (random.nextDouble() - 0.5D) * 2.0D * DRIFT;
                double dz = moved.z + (random.nextDouble() - 0.5D) * 2.0D * DRIFT;
                double dy = Math.min(moved.y, 0.0D) - fall;
                ParticleOptions type;
                switch (kind)
                {
                    case 0 -> type = ParticleTypes.FLAME;
                    case 1 ->
                    {
                        float size = EMBER_MIN + random.nextFloat() * (EMBER_MAX - EMBER_MIN);
                        type = new DustColorTransitionOptions(DrossColors.vector(DrossColors.EMBERDACTYL_WING_EMBER),
                                DrossColors.vector(DrossColors.EMBERDACTYL_WING_EMBER_COOL), size);
                    }
                    case 2 ->
                    {
                        type = ParticleTypes.FALLING_LAVA;
                        dx = 0.0D;
                        dy = 0.0D;
                        dz = 0.0D;
                    }
                    default ->
                    {
                        type = ParticleTypes.LARGE_SMOKE;
                        dy = 0.02D;
                    }
                }
                level.addAlwaysVisibleParticle(type, true, at.x, at.y, at.z, dx, dy, dz);
            }
        }
    }
}
