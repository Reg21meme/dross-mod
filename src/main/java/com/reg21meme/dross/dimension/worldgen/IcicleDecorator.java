package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModTreeDecorators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

/**
 * Tree decorator {@code dross:icicles}: hangs Icicles under the branches of Rimefall's frost-covered dead trees as they
 * grow in world generation. Every log with open air right under it (a branch or a leaning trunk, never the trunk's foot)
 * gets an Icicle under it with the chance {@code probability}. An Icicle only hangs under a block whose bottom face is
 * solid, and a log is a full block, so it can always stay (it breaks if the log is ever taken away).
 *
 * <p>Config (written by {@code tools/terrain/worldgen.py}, FEATURES): <pre>{"type": "dross:icicles", "probability": 0.4}</pre>
 */
public class IcicleDecorator extends TreeDecorator
{
    public static final Codec<IcicleDecorator> CODEC = Codec.floatRange(0.0F, 1.0F).fieldOf("probability")
            .xmap(IcicleDecorator::new, decorator -> decorator.probability).codec();

    private final float probability;

    public IcicleDecorator(float probability)
    {
        this.probability = probability;
    }

    @Override
    protected TreeDecoratorType<?> type()
    {
        return ModTreeDecorators.ICICLES.get();
    }

    @Override
    public void place(TreeDecorator.Context context)
    {
        RandomSource random = context.random();
        BlockState icicle = ModBlocks.ICICLE.get().defaultBlockState();
        for (BlockPos log : context.logs())
        {
            BlockPos below = log.relative(Direction.DOWN);
            if (context.isAir(below) && random.nextFloat() < probability)
            {
                context.setBlock(below, icicle);
            }
        }
    }
}
