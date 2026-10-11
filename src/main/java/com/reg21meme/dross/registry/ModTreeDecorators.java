package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.worldgen.StormvineDecorator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Tree decorators: things added to a tree as it grows (used by the tree JSON in data/dross/worldgen/configured_feature/). */
public final class ModTreeDecorators
{
    public static final DeferredRegister<TreeDecoratorType<?>> TREE_DECORATORS = DeferredRegister.create(Registries.TREE_DECORATOR_TYPE, Dross.MODID);

    // Dimension area: dross:stormvine, hangs glowing Stormvine on silverwood trunks and from their canopies
    public static final RegistryObject<TreeDecoratorType<StormvineDecorator>> STORMVINE = TREE_DECORATORS.register("stormvine",
            () -> new TreeDecoratorType<>(StormvineDecorator.CODEC));

    private ModTreeDecorators() {}
}
