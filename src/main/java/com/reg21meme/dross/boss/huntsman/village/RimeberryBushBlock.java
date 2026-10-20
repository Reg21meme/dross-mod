package com.reg21meme.dross.boss.huntsman.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;

import java.util.function.Supplier;

/**
 * The Rimeberry Bush ({@code dross:rimeberry_bush}, {@link HunterCrops}): Rimefall's berry bush, two blocks tall (the
 * user, October 20, 2026: "I want the rimeberries to be 2 blocks tall and have berries collectable for both blocks").
 * <ul>
 *   <li>Planted from Rimeberries on dirt (Hoarfrost too: it's in {@code #minecraft:dirt}), farmland or snow, it's a
 *       one-block sprout (age 0); its first growth needs the block over it free and makes it two blocks tall, and from
 *       then on both halves grow together (one age, kept the same in both).</li>
 *   <li>It grows like a sweet berry bush: on a random tick, one time in {@link #GROW_CHANCE}, with block light
 *       {@link #GROW_LIGHT} or more over it; bone meal works too.</li>
 *   <li>From age {@link #RIPE_AGE} both halves carry berries: right-clicking either picks the whole bush (each half 1-2
 *       berries, one more when fully ripe) and it drops back to age 1 to grow them again.</li>
 *   <li>Breaking either half breaks both, its berries dropped once (the loot table, on the lower half). It slows you
 *       like a sweet berry bush but has no thorns.</li>
 * </ul>
 */
public class RimeberryBushBlock extends BushBlock implements BonemealableBlock
{
    public static final int MAX_AGE = 3;
    /** Berries to pick from this age on. */
    public static final int RIPE_AGE = 2;
    /** It grows on one random tick in this many (with enough light), like a sweet berry bush. */
    public static final int GROW_CHANCE = 5;
    /** The block light (or sky light) it needs over it to grow. */
    public static final int GROW_LIGHT = 9;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    private static final VoxelShape SPROUT = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 8.0D, 13.0D);
    private static final VoxelShape LOWER = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 16.0D, 15.0D);
    private static final VoxelShape UPPER_YOUNG = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 9.0D, 13.0D);
    private static final VoxelShape UPPER = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 14.0D, 15.0D);

    private final Supplier<? extends ItemLike> berries;

    /** {@code berries}: what plants it and what it gives. */
    public RimeberryBushBlock(Properties properties, Supplier<? extends ItemLike> berries)
    {
        super(properties);
        this.berries = berries;
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(AGE, HALF);
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state)
    {
        return new ItemStack(this.berries.get());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        int age = state.getValue(AGE);
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER)
        {
            return age == 0 ? SPROUT : LOWER;
        }
        return age <= 1 ? UPPER_YOUNG : UPPER;
    }

    /** Dirt (Hoarfrost and Silvermoss are in its tag) and farmland like a sweet berry bush, and snow. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos)
    {
        return super.mayPlaceOn(state, level, pos) || state.is(Blocks.SNOW_BLOCK);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER)
        {
            if (state.getBlock() != this)
            {
                return super.canSurvive(state, level, pos);       // Forge's check before it's placed
            }
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return super.canSurvive(state, level, pos);
    }

    /** Either half goes when the other does (a grown lower half needs its upper one), or when its ground goes. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos)
    {
        DoubleBlockHalf half = state.getValue(HALF);
        if (half == DoubleBlockHalf.LOWER && direction == Direction.UP && state.getValue(AGE) > 0
                && !(neighbour.is(this) && neighbour.getValue(HALF) == DoubleBlockHalf.UPPER))
        {
            return Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN
                && !(neighbour.is(this) && neighbour.getValue(HALF) == DoubleBlockHalf.LOWER))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state)
    {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(AGE) < MAX_AGE;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (state.getValue(AGE) < MAX_AGE && level.getRawBrightness(pos.above(), 0) >= GROW_LIGHT
                && ForgeHooks.onCropsGrowPre(level, pos, state, random.nextInt(GROW_CHANCE) == 0))
        {
            if (this.grow(level, pos, state, null))
            {
                ForgeHooks.onCropsGrowPost(level, pos, state);
            }
        }
    }

    /** The lower half's position, from either half. */
    public static BlockPos lowerOf(BlockPos pos, BlockState state)
    {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** Sets both halves to {@code age} (the upper one put in if there isn't one yet). */
    private void setAge(Level level, BlockPos lower, BlockState lowerState, int age, Entity cause)
    {
        level.setBlock(lower.above(), this.defaultBlockState().setValue(HALF, DoubleBlockHalf.UPPER).setValue(AGE, age), 2);
        BlockState grown = lowerState.setValue(AGE, age);
        level.setBlock(lower, grown, 2);
        level.gameEvent(GameEvent.BLOCK_CHANGE, lower, GameEvent.Context.of(cause, grown));
    }

    /** One age older; its first growth only with the block over it free. Returns whether it grew. */
    private boolean grow(Level level, BlockPos lower, BlockState lowerState, Entity cause)
    {
        int age = lowerState.getValue(AGE);
        if (age >= MAX_AGE)
        {
            return false;
        }
        BlockState above = level.getBlockState(lower.above());
        boolean ownUpper = above.is(this) && above.getValue(HALF) == DoubleBlockHalf.UPPER;
        if (!ownUpper && !level.isEmptyBlock(lower.above()))
        {
            return false;
        }
        this.setAge(level, lower, lowerState, age + 1, cause);
        return true;
    }

    /** Picks the whole bush's berries from either half once it's ripe. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit)
    {
        BlockPos lower = lowerOf(pos, state);
        BlockState lowerState = level.getBlockState(lower);
        if (!lowerState.is(this))
        {
            return super.use(state, level, pos, player, hand, hit);
        }
        int age = lowerState.getValue(AGE);
        boolean full = age == MAX_AGE;
        if (!full && player.getItemInHand(hand).is(Items.BONE_MEAL))
        {
            return InteractionResult.PASS;
        }
        if (age < RIPE_AGE)
        {
            return super.use(state, level, pos, player, hand, hit);
        }
        int count = 0;
        for (int half = 0; half < 2; half++)
        {
            count += 1 + level.random.nextInt(2) + (full ? 1 : 0);
        }
        popResource(level, pos, new ItemStack(this.berries.get(), count));
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F,
                0.8F + level.random.nextFloat() * 0.4F);
        this.setAge(level, lower, lowerState, 1, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Slows whatever walks through it, like a sweet berry bush, but has no thorns. */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        if (entity instanceof LivingEntity && entity.getType() != EntityType.FOX && entity.getType() != EntityType.BEE)
        {
            entity.makeStuckInBlock(state, new Vec3(0.8F, 0.75D, 0.8F));
        }
    }

    /** Breaking the upper half in creative takes the lower one without dropping anything (like a tall flower). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player)
    {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER)
        {
            BlockPos below = pos.below();
            BlockState lowerState = level.getBlockState(below);
            if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER)
            {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lowerState));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean client)
    {
        BlockPos lower = lowerOf(pos, state);
        BlockState lowerState = level.getBlockState(lower);
        if (!lowerState.is(this))
        {
            return false;
        }
        int age = lowerState.getValue(AGE);
        return age < MAX_AGE && (age > 0 || level.isEmptyBlock(lower.above()));
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state)
    {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state)
    {
        BlockPos lower = lowerOf(pos, state);
        BlockState lowerState = level.getBlockState(lower);
        if (lowerState.is(this))
        {
            this.grow(level, lower, lowerState, null);
        }
    }
}
