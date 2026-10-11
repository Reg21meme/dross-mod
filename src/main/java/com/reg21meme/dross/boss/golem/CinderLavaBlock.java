package com.reg21meme.dross.boss.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * {@code dross:cinder_lava}: the lava a Cinder Colossus's lava blobs leave on the ground (see {@link LavaPools}). It
 * looks, burns and hurts like lava (and you wade through it slowly), but it's the mod's own block, not a fluid:
 * <ul>
 *   <li>it never spreads or flows, never sets anything on fire around it, and makes no cobblestone or obsidian with
 *       water (water just washes it away);</li>
 *   <li>it can't be picked up with a bucket and drops nothing; you can place blocks into it to cover it;</li>
 *   <li>it cools away by itself: {@link #LIFE} counts down one step every {@link #STEP_TICKS} ticks (the last step it
 *       shows as a dark crust) and then it's gone. The countdown is a scheduled block tick, which Minecraft saves with
 *       the chunk, so it carries on after the chunk unloads and loads again; a random tick re-schedules it if the
 *       tick was ever lost, so it can never stay for good.</li>
 * </ul>
 */
public class CinderLavaBlock extends Block
{
    /** The highest {@link #LIFE}: 20 steps of {@link #STEP_TICKS} ticks is 50 seconds. */
    public static final int MAX_LIFE = 19;
    /** Cooling steps left: at 0 it has crusted over and goes at the next step. */
    public static final IntegerProperty LIFE = IntegerProperty.create("life", 0, MAX_LIFE);
    /** Ticks per cooling step (2.5 seconds). */
    public static final int STEP_TICKS = 50;
    /** Its light while molten, and as a crust (the last step). */
    private static final int LIGHT = 15;
    private static final int CRUST_LIGHT = 6;
    /** How much it slows you down while you're in it (like wading through lava). */
    private static final Vec3 WADING = new Vec3(0.7D, 0.85D, 0.7D);
    /** The lava's surface sits a little below the top of the block, like a lava source. */
    private static final VoxelShape SURFACE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D);

    public CinderLavaBlock()
    {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.FIRE)
                .replaceable()
                .noCollission()
                .randomTicks()
                .strength(-1.0F, 100.0F)
                .lightLevel(state -> state.getValue(LIFE) > 0 ? LIGHT : CRUST_LIGHT)
                .pushReaction(PushReaction.DESTROY)
                .noLootTable()
                .noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false)
                .sound(SoundType.EMPTY));
        this.registerDefaultState(this.stateDefinition.any().setValue(LIFE, MAX_LIFE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LIFE);
    }

    // ---------------------------------------------------------------- cooling away

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        if (!level.isClientSide)
        {
            // Placed (or one step cooler): the next step comes after STEP_TICKS.
            level.scheduleTick(pos, this, STEP_TICKS);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        int life = state.getValue(LIFE);
        if (life > 0)
        {
            level.setBlock(pos, state.setValue(LIFE, life - 1), Block.UPDATE_CLIENTS);
        }
        else
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.levelEvent(1501, pos, 0); // the hiss and smoke of lava going out
        }
    }

    /** A safety net: if its countdown was ever lost, a random tick starts it again. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!level.getBlockTicks().hasScheduledTick(pos, this))
        {
            level.scheduleTick(pos, this, STEP_TICKS);
        }
    }

    // ---------------------------------------------------------------- burning like lava

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        if (entity.fireImmune() || entity instanceof ExperienceOrb)
        {
            return; // the Colossus and its minions wade through it; its experience isn't burnt up
        }
        entity.lavaHurt();
        if (entity instanceof LivingEntity)
        {
            entity.makeStuckInBlock(state, WADING);
        }
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob)
    {
        return BlockPathTypes.LAVA;
    }

    @Override
    public boolean isBurning(BlockState state, BlockGetter level, BlockPos pos)
    {
        return true;
    }

    // ---------------------------------------------------------------- shape and looks

    /** Like lava, it can't be clicked on (blocks placed against the ground beside it go into it). */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SURFACE;
    }

    /** It lies on the ground: with nothing solid under it, it's gone. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos)
    {
        if (direction == Direction.DOWN && !this.canSurvive(state, level, pos))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /** No walls drawn between two blocks of the same pool. */
    @Override
    public boolean skipRendering(BlockState state, BlockState adjacent, Direction direction)
    {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    /** Lava's pops, bubbling and the odd lava spark; smoke as it crusts over. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        double x = pos.getX() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        double y = pos.getY() + 0.9D;
        if (state.getValue(LIFE) == 0)
        {
            if (random.nextInt(4) == 0)
            {
                level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.02D, 0.0D);
            }
            return;
        }
        if (level.getBlockState(pos.above()).isAir())
        {
            if (random.nextInt(60) == 0)
            {
                level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0D, 0.0D, 0.0D);
                level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.2F + random.nextFloat() * 0.2F,
                        0.9F + random.nextFloat() * 0.15F, false);
            }
            if (random.nextInt(200) == 0)
            {
                level.playLocalSound(pos.getX() + 0.5D, y, pos.getZ() + 0.5D, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS,
                        0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }
    }
}
