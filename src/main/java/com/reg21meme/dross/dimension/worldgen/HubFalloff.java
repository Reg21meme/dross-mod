package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * Density function type {@code dross:hub_falloff}: how close a column is to the hub, as a number from 1 to 0.
 * <ul>
 *   <li>1 within {@code inner_radius} blocks of ({@code center_x}, {@code center_z}),</li>
 *   <li>0 beyond {@code outer_radius} blocks,</li>
 *   <li>a smooth S-curve (smoothstep) in between.</li>
 * </ul>
 * Vanilla has no density function of the horizontal position, so this one exists to shape the land round the hub at
 * Dross 0, 0: the terrain JSON (written by {@code tools/terrain/worldgen.py}) uses it to keep Silverwood Forest round
 * the hub, the mountains away from it, and its ground gentle. It only depends on X and Z, so it's safe inside
 * {@code flat_cache} (which computes at y 0).
 *
 * <pre>{"type": "dross:hub_falloff", "center_x": 0.0, "center_z": 0.0, "inner_radius": 200.0, "outer_radius": 450.0}</pre>
 *
 * @param centerX     the hub's X (optional, default 0: {@code portal.DrossHub.CENTER})
 * @param centerZ     the hub's Z (optional, default 0)
 * @param innerRadius within this many blocks the value is 1
 * @param outerRadius beyond this many blocks the value is 0 (must be more than {@code innerRadius})
 */
public record HubFalloff(double centerX, double centerZ, double innerRadius, double outerRadius)
        implements DensityFunction.SimpleFunction
{
    /** The largest radius the JSON may give (blocks): far beyond any world border. */
    private static final double MAX_RADIUS = 3.0E7D;

    private static final MapCodec<HubFalloff> DATA_CODEC = RecordCodecBuilder.<HubFalloff>mapCodec(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("center_x", 0.0D).forGetter(HubFalloff::centerX),
            Codec.DOUBLE.optionalFieldOf("center_z", 0.0D).forGetter(HubFalloff::centerZ),
            Codec.doubleRange(0.0D, MAX_RADIUS).fieldOf("inner_radius").forGetter(HubFalloff::innerRadius),
            Codec.doubleRange(0.0D, MAX_RADIUS).fieldOf("outer_radius").forGetter(HubFalloff::outerRadius)
    ).apply(instance, HubFalloff::new)).flatXmap(HubFalloff::check, HubFalloff::check);

    /** Registered as {@code dross:hub_falloff} in {@code registry/ModDensityFunctions}. */
    public static final KeyDispatchDataCodec<HubFalloff> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);

    private static DataResult<HubFalloff> check(HubFalloff falloff)
    {
        return falloff.outerRadius > falloff.innerRadius
                ? DataResult.success(falloff)
                : DataResult.error(() -> "dross:hub_falloff needs outer_radius (" + falloff.outerRadius + ") > inner_radius ("
                        + falloff.innerRadius + ")");
    }

    @Override
    public double compute(DensityFunction.FunctionContext context)
    {
        return valueAt(context.blockX(), context.blockZ());
    }

    /** The value at a column (for the GameTests and anything else that wants it without a function context). */
    public double valueAt(double x, double z)
    {
        double dx = x - centerX;
        double dz = z - centerZ;
        double t = Mth.clamp((Math.sqrt(dx * dx + dz * dz) - innerRadius) / (outerRadius - innerRadius), 0.0D, 1.0D);
        return 1.0D - t * t * (3.0D - 2.0D * t);
    }

    @Override
    public double minValue()
    {
        return 0.0D;
    }

    @Override
    public double maxValue()
    {
        return 1.0D;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec()
    {
        return CODEC;
    }
}
