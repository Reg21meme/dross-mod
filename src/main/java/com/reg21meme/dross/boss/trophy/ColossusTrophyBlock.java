package com.reg21meme.dross.boss.trophy;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The Cinder Colossus trophy ({@code dross:colossus_trophy}): a 1x1x1 decorative block, a miniature of the boss in one
 * of five designs ({@link #DESIGN}, see {@link ColossusTrophies}). It stands on the floor, facing {@link #FACING}: the
 * player who placed it. Its figure is drawn by its block entity ({@link ColossusTrophyBlockEntity}, GeckoLib), with
 * glowing lava that keeps flowing down it; now and then lava drips off it (just particles: nothing catches fire).
 * <p>
 * Right-clicking it (with an empty hand or holding anything, main hand) turns it to the next design: 1, 2 ... 5, then
 * 1 again, still facing the same way ({@link #use}).
 * <p>
 * It never burns, explosions don't break it, and any tool (or a bare hand) breaks it: it drops itself with its design
 * (loot table {@code blocks/colossus_trophy}, which copies the design onto the item). If the block under it goes, it
 * pops off as an item.
 */
public class ColossusTrophyBlock extends BaseEntityBlock
{
    /** The way the figure faces. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Its design (1 to 5). */
    public static final IntegerProperty DESIGN = IntegerProperty.create("design", 1, TrophyModels.ALL.size());

    /** Each design's outline (its figure's box), for each direction: [design - 1][facing's 2D index]. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[TrophyModels.ALL.size()][4];

    static
    {
        for (TrophyModels.Model model : TrophyModels.ALL)
        {
            for (Direction facing : Direction.Plane.HORIZONTAL)
            {
                Vec3 a = turn(model.min(), facing);
                Vec3 b = turn(model.max(), facing);
                SHAPES[model.number() - 1][facing.get2DDataValue()] = Shapes.box(Math.min(a.x, b.x), Math.min(a.y, b.y),
                        Math.min(a.z, b.z), Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
            }
        }
    }

    public ColossusTrophyBlock()
    {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(ColossusTrophies.HARDNESS, ColossusTrophies.BLAST_RESISTANCE)
                .sound(SoundType.BASALT)
                .noOcclusion()
                .lightLevel(state -> ColossusTrophies.get(state.getValue(DESIGN)).style().light)
                .pushReaction(PushReaction.DESTROY));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DESIGN, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, DESIGN);
    }

    /** Placed facing the player, with the design of the item it was placed from. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(DESIGN, ColossusTrophies.designOf(context.getItemInHand()));
    }

    // ---------------------------------------------------------------- standing on the floor

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos)
    {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    // ---------------------------------------------------------------- right-click: the next pose

    /**
     * Right-click: the trophy turns to the next design (1, 2 ... 5, then 1 again), keeping the way it faces, with a
     * quiet stone click and a little puff of ember dust. The new block state carries everything: the block entity
     * draws the new pose, look and lava streams on the next frame, and the light level and outline change with it.
     * <ul>
     *   <li>Main hand only, empty or holding anything: the hand swings and nothing else happens (holding a block, it
     *       isn't placed against the trophy).</li>
     *   <li>Sneaking with an item in either hand, vanilla never calls this, so the item is used as normal (a block is
     *       placed). Sneaking with empty hands, it turns too.</li>
     *   <li>Players who can't build (adventure mode) can't turn it.</li>
     * </ul>
     */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit)
    {
        if (hand != InteractionHand.MAIN_HAND || !player.mayBuild())
        {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide)
        {
            cycle(level, pos, state, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Server: turns the trophy at {@code pos} to its next design, with the click and the puff. */
    private static void cycle(Level level, BlockPos pos, BlockState state, @Nullable Player player)
    {
        int design = ColossusTrophies.next(state.getValue(DESIGN));
        level.setBlock(pos, state.setValue(DESIGN, design), Block.UPDATE_ALL);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS,
                ColossusTrophies.CYCLE_CLICK_VOLUME, ColossusTrophies.CYCLE_CLICK_PITCH);
        level.playSound(null, pos, SoundType.BASALT.getHitSound(), SoundSource.BLOCKS,
                ColossusTrophies.CYCLE_GRIND_VOLUME, ColossusTrophies.CYCLE_GRIND_PITCH);
        if (level instanceof ServerLevel server)
        {
            int rgb = DrossColors.TROPHY_CYCLE_DUST;
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(DrossColors.red(rgb), DrossColors.green(rgb), DrossColors.blue(rgb)),
                    ColossusTrophies.CYCLE_DUST_SIZE);
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.4D;
            double z = pos.getZ() + 0.5D;
            double spread = ColossusTrophies.CYCLE_PUFF_SPREAD;
            server.sendParticles(dust, x, y, z, ColossusTrophies.CYCLE_DUST_COUNT, spread, spread, spread, 0.0D);
            server.sendParticles(ParticleTypes.SMOKE, x, y, z, ColossusTrophies.CYCLE_SMOKE_COUNT, spread, spread, spread,
                    0.01D);
        }
    }

    // ---------------------------------------------------------------- shape and looks

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPES[state.getValue(DESIGN) - 1][state.getValue(FACING).get2DDataValue()];
    }

    /** Only its block entity draws it (the GeckoLib figure); the block has no model of its own. */
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Pick-block (middle click in creative) gives a trophy of the same design. */
    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state)
    {
        return ColossusTrophies.stack(state.getValue(DESIGN));
    }

    /**
     * Client: now and then lava drips off the figure (from the low ends of its lava streams) and its craters smoke or
     * pop. Just particles: nothing catches fire and no block changes.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        TrophyModels.Model model = ColossusTrophies.get(state.getValue(DESIGN)).model();
        TrophyStyle style = model.style();
        Direction facing = state.getValue(FACING);
        if (!model.drips().isEmpty() && random.nextFloat() < style.dripChance)
        {
            Vec3 at = turn(model.drips().get(random.nextInt(model.drips().size())), facing);
            level.addParticle(ParticleTypes.DRIPPING_LAVA, pos.getX() + at.x, pos.getY() + at.y, pos.getZ() + at.z,
                    0.0D, 0.0D, 0.0D);
        }
        if (model.craters().isEmpty())
        {
            return;
        }
        Vec3 crater = turn(model.craters().get(random.nextInt(model.craters().size())), facing);
        if (random.nextFloat() < style.smokeChance)
        {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + crater.x, pos.getY() + crater.y, pos.getZ() + crater.z,
                    0.0D, 0.02D, 0.0D);
        }
        if (random.nextFloat() < style.popChance)
        {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + crater.x, pos.getY() + crater.y, pos.getZ() + crater.z,
                    0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * A point in the block, as generated for a trophy facing north, for a trophy facing {@code facing}: turned about
     * the block's middle the same way the renderer turns the figure (GeckoLib's GeoBlockRenderer.rotateBlock).
     */
    public static Vec3 turn(TrophyModels.Spot spot, Direction facing)
    {
        double x = spot.x() - 0.5D;
        double z = spot.z() - 0.5D;
        double angle = switch (facing)
        {
            case SOUTH -> Math.PI;
            case WEST -> Math.PI / 2.0D;
            case EAST -> Math.PI * 1.5D;
            default -> 0.0D;
        };
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(0.5D + x * cos + z * sin, spot.y(), 0.5D - x * sin + z * cos);
    }

    // ---------------------------------------------------------------- its block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ColossusTrophyBlockEntity(pos, state);
    }
}
