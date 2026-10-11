package com.reg21meme.dross.dimension.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.PlantType;

/**
 * A small flower of one of the Dross's biomes (the Frostbloom, the Crag Thistle, and the Soulwick through
 * {@link SoulwickBlock}). A normal small flower (bees visit it, it drops itself, it goes into suspicious stew), except
 * that it grows only on its own ground: the block tag {@code ground} (see {@link PlantGround}). Whether it burns is
 * given when it's made: flowers of the Nether Scar never do.
 */
public class DrossFlowerBlock extends FlowerBlock
{
    private final TagKey<Block> ground;
    private final boolean flammable;

    /**
     * @param stewEffect  what suspicious stew made with it gives, for {@code stewSeconds} seconds
     * @param ground      the blocks it grows on
     * @param flammable   true: burns like a vanilla flower ({@link Flammability#PLANT_SPREAD}, {@link Flammability#PLANT_BURN})
     */
    public DrossFlowerBlock(Supplier<MobEffect> stewEffect, int stewSeconds, TagKey<Block> ground, boolean flammable, Properties properties)
    {
        super(stewEffect, stewSeconds, properties);
        this.ground = ground;
        this.flammable = flammable;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos)
    {
        return state.is(ground);
    }

    @Override
    public PlantType getPlantType(BlockGetter level, BlockPos pos)
    {
        return PlantGround.PLANT_TYPE;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return flammable;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return flammable ? Flammability.PLANT_BURN : 0;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return flammable ? Flammability.PLANT_SPREAD : 0;
    }
}
