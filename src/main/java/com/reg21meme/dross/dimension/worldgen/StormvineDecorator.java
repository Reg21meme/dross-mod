package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModTreeDecorators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/**
 * Tree decorator {@code dross:stormvine}: hangs glowing Stormvine on a tree as it grows (in world generation and from
 * saplings). Used by the silverwood trees' JSON ({@code data/dross/worldgen/configured_feature/}); its numbers are
 * labeled constants in {@code tools/terrain/worldgen.py}.
 *
 * <ul>
 *   <li>{@code probability}: the chance (0 to 1) that a tree gets any Stormvine at all.</li>
 *   <li>{@code trunk_density}: on such a tree, the chance that a side of a log starts a patch of vine climbing the
 *       trunk.</li>
 *   <li>{@code trunk_continue}: the chance that a patch carries on up to the next log (so patches form runs).</li>
 *   <li>{@code hanging_density}: the chance that an open side of a leaf block starts a strand hanging from the
 *       canopy (only where there's air underneath to hang into).</li>
 *   <li>{@code max_hanging_length}: the longest a hanging strand gets (blocks).</li>
 * </ul>
 * Vanilla's own vine decorators can only place vanilla vines, so this one places Stormvine itself.
 */
public class StormvineDecorator extends TreeDecorator
{
    public static final Codec<StormvineDecorator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(decorator -> decorator.probability),
            Codec.floatRange(0.0F, 1.0F).fieldOf("trunk_density").forGetter(decorator -> decorator.trunkDensity),
            Codec.floatRange(0.0F, 1.0F).fieldOf("trunk_continue").forGetter(decorator -> decorator.trunkContinue),
            Codec.floatRange(0.0F, 1.0F).fieldOf("hanging_density").forGetter(decorator -> decorator.hangingDensity),
            Codec.intRange(1, 16).fieldOf("max_hanging_length").forGetter(decorator -> decorator.maxHangingLength)
    ).apply(instance, StormvineDecorator::new));

    private final float probability;
    private final float trunkDensity;
    private final float trunkContinue;
    private final float hangingDensity;
    private final int maxHangingLength;

    public StormvineDecorator(float probability, float trunkDensity, float trunkContinue, float hangingDensity, int maxHangingLength)
    {
        this.probability = probability;
        this.trunkDensity = trunkDensity;
        this.trunkContinue = trunkContinue;
        this.hangingDensity = hangingDensity;
        this.maxHangingLength = maxHangingLength;
    }

    @Override
    protected TreeDecoratorType<?> type()
    {
        return ModTreeDecorators.STORMVINE.get();
    }

    @Override
    public void place(TreeDecorator.Context context)
    {
        RandomSource random = context.random();
        if (random.nextFloat() >= probability)
        {
            return;
        }
        Block vine = ModBlocks.STORMVINE.get();

        // On the trunk: the logs come lowest first, so a patch can carry on upwards from the vine below it.
        for (BlockPos log : context.logs())
        {
            for (Direction side : Direction.Plane.HORIZONTAL)
            {
                BlockPos at = log.relative(side);
                BooleanProperty face = VineBlock.getPropertyForFace(side.getOpposite()); // the face touching the log
                boolean vineBelow = context.level().isStateAtPosition(at.below(),
                        state -> state.is(vine) && state.getValue(face));
                float chance = vineBelow ? trunkContinue : trunkDensity;
                if (random.nextFloat() < chance && context.isAir(at))
                {
                    context.setBlock(at, vine.defaultBlockState().setValue(face, true));
                }
            }
        }

        // From the canopy: strands hanging down beside open sides of the leaves.
        for (BlockPos leaf : context.leaves())
        {
            for (Direction side : Direction.Plane.HORIZONTAL)
            {
                BlockPos at = leaf.relative(side);
                if (random.nextFloat() < hangingDensity && context.isAir(at) && context.isAir(at.below()))
                {
                    hang(context, at, VineBlock.getPropertyForFace(side.getOpposite()), vine, 1 + random.nextInt(maxHangingLength));
                }
            }
        }
    }

    /** A strand of {@code length} vines from {@code top} downwards, each held up by the one above (like vanilla's). */
    private static void hang(TreeDecorator.Context context, BlockPos top, BooleanProperty face, Block vine, int length)
    {
        BlockState state = vine.defaultBlockState().setValue(face, true);
        BlockPos at = top;
        for (int i = 0; i < length && context.isAir(at); i++)
        {
            context.setBlock(at, state);
            at = at.below();
        }
    }
}
