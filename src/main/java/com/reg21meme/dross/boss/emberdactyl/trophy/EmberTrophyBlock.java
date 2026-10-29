package com.reg21meme.dross.boss.emberdactyl.trophy;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.emberdactyl.trophy.client.EmberTrophyParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * The Emberdactyl trophy ({@code dross:emberdactyl_trophy}): a 1x1x1 decorative block, a miniature of the Emberdactyl in
 * one of fifteen designs ({@link #DESIGN}, see {@link EmberdactylTrophies}). It stands on the floor, facing {@link #FACING}: the player
 * who placed it. Its figure is drawn by its block entity ({@link EmberTrophyBlockEntity}, GeckoLib) as the boss is:
 * the solid parts cut out, the lava and embers glowing, its death's magma cracks glowing. Now and then an ember drifts
 * down from a wing tip, smoke rises from its volcano's crater or the burst egg, and lava pops out of the crater (just
 * particles, client only).
 * <p>
 * Right-clicking it (with an empty hand or holding anything, main hand) turns it to the next design: 1, 2 ... 15, then
 * 1 again, still facing the same way ({@link #use}).
 * <p>
 * It never burns, explosions don't break it, and any tool (or a bare hand) breaks it: it drops itself with its design
 * (loot table {@code blocks/emberdactyl_trophy}, which copies the design onto the item). If the block under it goes, it
 * pops off as an item.
 */
public class EmberTrophyBlock extends BaseEntityBlock
{
    /** The way the figure faces. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Its design (1 to 15). */
    public static final IntegerProperty DESIGN = IntegerProperty.create("design", 1, EmberTrophyModels.ALL.size());

    /** Each design's outline (its figure's box), for each direction: [design - 1][facing's 2D index]. */
    private static final VoxelShape[][] SHAPES = new VoxelShape[EmberTrophyModels.ALL.size()][4];

    static
    {
        for (EmberTrophyModels.Model model : EmberTrophyModels.ALL)
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

    public EmberTrophyBlock()
    {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(EmberdactylTrophies.HARDNESS, EmberdactylTrophies.BLAST_RESISTANCE)
                .sound(SoundType.BASALT)
                .noOcclusion()
                .lightLevel(state -> EmberdactylTrophies.get(state.getValue(DESIGN)).model().light())
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
                .setValue(DESIGN, EmberdactylTrophies.designOf(context.getItemInHand()));
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
     * Right-click: the trophy turns to the next design (1, 2 ... 15, then 1 again), keeping the way it faces, with a
     * quiet click, a crackle of embers and a small puff of them. The new block state carries everything: the block entity
     * draws the new pose on the next frame, and the outline changes with it.
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

    /** Server: turns the trophy at {@code pos} to its next design, with the click, the crunch and the puff. */
    private static void cycle(Level level, BlockPos pos, BlockState state, @Nullable Player player)
    {
        int design = EmberdactylTrophies.next(state.getValue(DESIGN));
        level.setBlock(pos, state.setValue(DESIGN, design), Block.UPDATE_ALL);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS,
                EmberdactylTrophies.CYCLE_CLICK_VOLUME, EmberdactylTrophies.CYCLE_CLICK_PITCH);
        level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                EmberdactylTrophies.CYCLE_CRACKLE_VOLUME, EmberdactylTrophies.CYCLE_CRACKLE_PITCH);
        if (level instanceof ServerLevel server)
        {
            int rgb = DrossColors.EMBERDACTYL_TROPHY_CYCLE_DUST;
            DustParticleOptions dust = new DustParticleOptions(
                    new Vector3f(DrossColors.red(rgb), DrossColors.green(rgb), DrossColors.blue(rgb)),
                    EmberdactylTrophies.CYCLE_DUST_SIZE);
            double spread = EmberdactylTrophies.CYCLE_PUFF_SPREAD;
            server.sendParticles(dust, pos.getX() + 0.5D, pos.getY() + EmberdactylTrophies.CYCLE_PUFF_HEIGHT, pos.getZ() + 0.5D,
                    EmberdactylTrophies.CYCLE_DUST_COUNT, spread, spread, spread, 0.0D);
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
        return EmberdactylTrophies.stack(state.getValue(DESIGN));
    }

    /**
     * Client: now and then a few small particles from the design's generated spots (embers off its wing tips, smoke and
     * lava from the crater; {@link EmberTrophyParticles}). Just particles: no block changes.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (level.isClientSide)
        {
            EmberTrophyParticles.animate(state, level, pos, random);
        }
    }

    /**
     * A point in the block, as generated for a trophy facing north, for a trophy facing {@code facing}: turned about
     * the block's middle the same way the renderer turns the figure (GeckoLib's GeoBlockRenderer.rotateBlock).
     */
    public static Vec3 turn(EmberTrophyModels.Spot spot, Direction facing)
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
        return new EmberTrophyBlockEntity(pos, state);
    }
}
