package com.reg21meme.dross.boss.ratking.trophy;

import com.reg21meme.dross.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The block entity of a Rat King trophy: it only holds the figure's GeckoLib animation (its design's pose, held).
 * Everything else (design, facing) is in the block state, so it saves nothing of its own. Drawn by
 * {@code boss.ratking.trophy.client.RatKingTrophyBlockRenderer}.
 */
public class RatKingTrophyBlockEntity extends BlockEntity implements GeoBlockEntity
{
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public RatKingTrophyBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.RAT_KING_TROPHY.get(), pos, state);
    }

    /** Its design (1 to 5). */
    public int design()
    {
        BlockState state = this.getBlockState();
        return state.hasProperty(RatKingTrophyBlock.DESIGN) ? state.getValue(RatKingTrophyBlock.DESIGN) : 1;
    }

    /** The figure always stays inside its own block. */
    @Override
    public AABB getRenderBoundingBox()
    {
        return new AABB(this.getBlockPos());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "pose", 0,
                state -> state.setAndContinue(RatKingTrophies.get(this.design()).animation())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }
}
