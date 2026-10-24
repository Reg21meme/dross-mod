package com.reg21meme.dross.boss.huntsman.trophy;

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
 * The block entity of a Huntsman trophy: it only holds the figure's GeckoLib animation (its design's pose, held).
 * Everything else (design, facing) is in the block state, so it saves nothing of its own. Drawn by
 * {@code boss.huntsman.trophy.client.HuntsmanTrophyBlockRenderer}.
 */
public class HuntsmanTrophyBlockEntity extends BlockEntity implements GeoBlockEntity
{
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public HuntsmanTrophyBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.HUNTSMAN_TROPHY.get(), pos, state);
    }

    /** Its design (1 to 5). */
    public int design()
    {
        BlockState state = this.getBlockState();
        return state.hasProperty(HuntsmanTrophyBlock.DESIGN) ? state.getValue(HuntsmanTrophyBlock.DESIGN) : 1;
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
                state -> state.setAndContinue(HuntsmanTrophies.get(this.design()).animation())));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }
}
