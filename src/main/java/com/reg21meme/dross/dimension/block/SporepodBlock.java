package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.PlantType;

/**
 * Sporepod: a bulbous fungal pod on the Blightwood floor. Now and then it puffs a few spores; when something walks
 * into it, it bursts: a little cloud of sickly spores and a soft sound, and it sags, empty, until it has filled up
 * again a couple of seconds later ({@link #BURST}, back to false with a scheduled block tick). It's harmless: the spores
 * are only particles.
 *
 * <p>It grows on the same ground as the Gloomcap ({@code #dross:sporepod_grows_on}, see {@link PlantGround}). You walk
 * through it; it drops itself.
 */
public class SporepodBlock extends BushBlock
{
    /** True while the pod is empty after bursting (its model sags); it fills up again after {@link #REFILL_TICKS}. */
    public static final BooleanProperty BURST = BooleanProperty.create("burst");

    /** How long a burst pod stays empty (ticks; 20 = one second). It can't burst again until then. */
    public static final int REFILL_TICKS = 50;

    /** The outlines of the full pod and of the sagging, empty one. */
    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 10.0D, 13.0D);
    private static final VoxelShape SHAPE_BURST = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 6.0D, 13.0D);

    /** The burst: how many spore specks, how far they spread from the pod (blocks), and how big they are. */
    private static final int BURST_SPORES = 26;
    private static final double BURST_SPREAD = 0.35D;
    private static final float BURST_SPORE_SIZE = 0.9F;
    /** The burst sound's loudness, and its pitch (1 = as recorded) give or take {@link #BURST_PITCH_SPREAD}. */
    private static final float BURST_VOLUME = 0.7F;
    private static final float BURST_PITCH = 1.0F;
    private static final float BURST_PITCH_SPREAD = 0.15F;

    // A block within 16 blocks of the player gets an animateTick call about every 2.5 seconds.
    /** 1 in this many animateTick calls puffs a few idle spores from a full pod (about every 12 seconds). */
    private static final int IDLE_CHANCE = 5;
    private static final int IDLE_SPORES = 3;
    private static final float IDLE_SPORE_SIZE = 0.55F;
    /** Where the pod's mouth is (blocks above the ground), where the idle spores come out. */
    private static final double MOUTH_HEIGHT = 0.6D;

    private static final DustColorTransitionOptions BURST_SPORE = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.SPOREPOD_SPORE), DrossColors.vector(DrossColors.SPOREPOD_SPORE_FADE), BURST_SPORE_SIZE);
    private static final DustColorTransitionOptions IDLE_SPORE = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.SPOREPOD_SPORE), DrossColors.vector(DrossColors.SPOREPOD_SPORE_FADE), IDLE_SPORE_SIZE);

    public SporepodBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BURST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BURST);
    }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter level, BlockPos pos)
    {
        return ground.is(PlantGround.SPOREPOD);
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
        return (state.getValue(BURST) ? SHAPE_BURST : SHAPE).move(offset.x, offset.y, offset.z);
    }

    // ---------------------------------------------------------------------------------------------
    // Bursting and filling up again
    // ---------------------------------------------------------------------------------------------

    /** Something walked into the pod (Minecraft calls this for every block an entity's box is in). */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        if (!level.isClientSide && !state.getValue(BURST) && entity instanceof LivingEntity && !entity.isSpectator())
        {
            burst((ServerLevel) level, pos, state, entity);
        }
    }

    /** Bursts the pod: spores, a soft sound, and it stays empty for {@link #REFILL_TICKS}. */
    public void burst(ServerLevel level, BlockPos pos, BlockState state, Entity cause)
    {
        level.setBlock(pos, state.setValue(BURST, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, REFILL_TICKS);
        Vec3 offset = state.getOffset(level, pos);
        level.sendParticles(BURST_SPORE, pos.getX() + 0.5D + offset.x, pos.getY() + 0.4D, pos.getZ() + 0.5D + offset.z,
                BURST_SPORES, BURST_SPREAD, BURST_SPREAD * 0.8D, BURST_SPREAD, 0.0D);
        float pitch = BURST_PITCH + (level.random.nextFloat() * 2.0F - 1.0F) * BURST_PITCH_SPREAD;
        level.playSound(null, pos, ModSounds.SPOREPOD_BURST.get(), SoundSource.BLOCKS, BURST_VOLUME, pitch);
        level.gameEvent(cause, GameEvent.BLOCK_CHANGE, pos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(BURST))
        {
            level.setBlock(pos, state.setValue(BURST, false), Block.UPDATE_ALL); // filled up again
        }
    }

    // A pod left empty without its scheduled tick (copied by a structure, say) fills up again on a random tick.
    @Override
    public boolean isRandomlyTicking(BlockState state)
    {
        return state.getValue(BURST);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        tick(state, level, pos, random);
    }

    // ---------------------------------------------------------------------------------------------
    // Idle spores (client side only)
    // ---------------------------------------------------------------------------------------------

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(BURST) || random.nextInt(IDLE_CHANCE) != 0)
        {
            return;
        }
        Vec3 offset = state.getOffset(level, pos);
        for (int i = 0; i < IDLE_SPORES; i++)
        {
            double x = pos.getX() + 0.5D + offset.x + (random.nextDouble() - 0.5D) * 0.2D;
            double z = pos.getZ() + 0.5D + offset.z + (random.nextDouble() - 0.5D) * 0.2D;
            level.addParticle(IDLE_SPORE, x, pos.getY() + MOUTH_HEIGHT, z, 0.0D, 1.0D, 0.0D);
        }
    }
}
