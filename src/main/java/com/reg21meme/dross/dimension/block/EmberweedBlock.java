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
 * Emberweed: dry dark stalks of the Cinder Wastes whose tips glow like embers (its light level is in its block
 * properties; the tips are drawn full-bright by its model). Now and then a tiny ember drifts up off a tip.
 *
 * <ul>
 *   <li>It grows only on the full blocks in {@code #dross:emberweed_grows_on} (Ash, basalt, blackstone, magma and
 *       Duskstone; see {@link PlantGround}).</li>
 *   <li>It's like dry grass: you walk through it, placing a block replaces it, and only shears pick it up.</li>
 *   <li>Its embers are only particles: it never sets anything on fire, and it never burns itself.</li>
 * </ul>
 */
public class EmberweedBlock extends BushBlock
{
    /** The outline: the same size as vanilla grass and dead bushes (12 pixels wide, 13 tall). */
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    // Minecraft calls animateTick on random blocks around the player (a block within 16 blocks gets a call about
    // every 2.5 seconds), so 1 call in EMBER_CHANCE sheds an ember: about one every half minute per plant.
    /** 1 in this many animateTick calls sheds an ember. */
    private static final int EMBER_CHANCE = 12;
    /** Size of the ember (1.0 = redstone dust size; smaller ones also fade sooner). */
    private static final float EMBER_SIZE = 0.55F;
    /** Where the glowing tips are: from this height (blocks above the ground)... */
    private static final double TIPS_LOW = 0.45D;
    /** ...up to this one. */
    private static final double TIPS_HIGH = 0.8D;
    /** How far from the middle of the block (sideways, blocks) the tips spread. */
    private static final double TIPS_SPREAD = 0.3D;

    /** The ember starts bright orange and cools to a dark red as it drifts up. */
    private static final DustColorTransitionOptions EMBER = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.EMBERWEED_EMBER), DrossColors.vector(DrossColors.EMBERWEED_EMBER_FADE), EMBER_SIZE);

    public EmberweedBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter level, BlockPos pos)
    {
        return ground.is(PlantGround.EMBERWEED);
    }

    @Override
    public PlantType getPlantType(BlockGetter level, BlockPos pos)
    {
        return PlantGround.PLANT_TYPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        Vec3 offset = state.getOffset(level, pos); // it's nudged randomly, like grass: the outline follows
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(EMBER_CHANCE) != 0)
        {
            return;
        }
        Vec3 offset = state.getOffset(level, pos);
        double x = pos.getX() + 0.5D + offset.x + (random.nextDouble() * 2.0D - 1.0D) * TIPS_SPREAD;
        double y = pos.getY() + TIPS_LOW + random.nextDouble() * (TIPS_HIGH - TIPS_LOW);
        double z = pos.getZ() + 0.5D + offset.z + (random.nextDouble() * 2.0D - 1.0D) * TIPS_SPREAD;
        level.addParticle(EMBER, x, y, z, 0.0D, 1.0D, 0.0D); // dust drifts gently whatever the speed; this tips it upwards
    }
}
