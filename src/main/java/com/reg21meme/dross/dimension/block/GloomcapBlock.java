package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.PlantType;

/**
 * Gloomcap: a small poison-green mushroom of the Blightwood whose cap glows (its light level is in its block properties;
 * the cap is drawn full-bright by its model). Now and then it lets go a faint glowing spore.
 *
 * <p>It grows on the ground in {@code #dross:gloomcap_grows_on} ({@code #minecraft:dirt},
 * {@code #minecraft:mushroom_grow_block}, mycelium and logs; see {@link PlantGround}), at any light level. Unlike
 * vanilla mushrooms it <b>never spreads and never grows huge</b>: it isn't a {@code MushroomBlock}, takes no random
 * ticks and ignores bone meal. It drops itself. (Giant blightcaps drop Gloomcaps when their cap is broken.)
 */
public class GloomcapBlock extends BushBlock
{
    /** The outline round the two little mushrooms of its sprite. */
    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 11.0D, 12.0D);

    // A block within 16 blocks of the player gets an animateTick call about every 2.5 seconds.
    /** 1 in this many animateTick calls lets go a spore (about every 20 seconds per mushroom). */
    private static final int SPORE_CHANCE = 8;
    /** Size of the spore (1.0 = redstone dust size): small and faint. */
    private static final float SPORE_SIZE = 0.45F;
    /** The height of its cap (blocks above the ground), where the spores come from. */
    private static final double CAP_HEIGHT = 0.6D;
    /** How far from the middle of the block (sideways, blocks) the cap reaches. */
    private static final double CAP_RADIUS = 0.2D;

    /** A spore starts pale glowing poison green and fades to deep green. */
    private static final DustColorTransitionOptions SPORE = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.GLOOMCAP_SPORE), DrossColors.vector(DrossColors.GLOOMCAP_SPORE_FADE), SPORE_SIZE);

    public GloomcapBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter level, BlockPos pos)
    {
        return ground.is(PlantGround.GLOOMCAP);
    }

    @Override
    public PlantType getPlantType(BlockGetter level, BlockPos pos)
    {
        return PlantGround.PLANT_TYPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(SPORE_CHANCE) != 0)
        {
            return;
        }
        Vec3 offset = state.getOffset(level, pos);
        double x = pos.getX() + 0.5D + offset.x + (random.nextDouble() * 2.0D - 1.0D) * CAP_RADIUS;
        double z = pos.getZ() + 0.5D + offset.z + (random.nextDouble() * 2.0D - 1.0D) * CAP_RADIUS;
        level.addParticle(SPORE, x, pos.getY() + CAP_HEIGHT, z, 0.0D, 1.0D, 0.0D);
    }
}
