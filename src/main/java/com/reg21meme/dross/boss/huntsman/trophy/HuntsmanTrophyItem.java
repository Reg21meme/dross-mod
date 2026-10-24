package com.reg21meme.dross.boss.huntsman.trophy;

import com.reg21meme.dross.DrossColors;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Huntsman trophy as an item ({@code dross:huntsman_trophy}): place it on the floor and the figure faces you. Its
 * design is in the item (the {@code BlockStateTag} vanilla uses for placed blocks), shown as a tooltip line. In the hand,
 * the inventory and on the ground it shows the 3D figure (GeckoLib, {@code HuntsmanTrophyItemRenderer}).
 * <ul>
 *   <li>Fireproof: a dropped trophy survives fire and lava, like netherite.</li>
 *   <li>A dropped trophy never despawns.</li>
 * </ul>
 * (When the fight's reward has to be dropped, it's also made unbreakable and kept for its player:
 * {@link HuntsmanTrophies#giveTo}.)
 */
public class HuntsmanTrophyItem extends BlockItem implements GeoItem
{
    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public HuntsmanTrophyItem(Block block)
    {
        super(block, new Item.Properties().fireResistant());
    }

    /**
     * How many ticks a dropped trophy lasts before it despawns. Normal items last 6000 ticks (5 minutes); this is so
     * long (over 3 years of play) that it effectively never despawns.
     */
    @Override
    public int getEntityLifespan(ItemStack stack, Level level)
    {
        return Integer.MAX_VALUE;
    }

    /** The name is shown in glacier blue (his boss bar's). */
    @Override
    public Component getName(ItemStack stack)
    {
        return super.getName(stack).copy()
                .withStyle(style -> style.withColor(TextColor.fromRgb(DrossColors.HUNTSMAN_TROPHY_NAME)));
    }

    /** "No. 1: Riding Down", then the hint "Right-click to change its pose". */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        HuntsmanTrophies.Design design = HuntsmanTrophies.get(HuntsmanTrophies.designOf(stack));
        tooltip.add(Component.translatable(HuntsmanTrophies.TOOLTIP_KEY, design.number(), design.displayName())
                .withStyle(DrossColors.HUNTSMAN_TROPHY_TOOLTIP));
        tooltip.add(Component.translatable(HuntsmanTrophies.HINT_KEY).withStyle(DrossColors.HUNTSMAN_TROPHY_HINT));
    }

    // ---------------------------------------------------------------- the 3D figure (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "pose", 0, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            int design = stack == null ? 1 : HuntsmanTrophies.designOf(stack);
            return state.setAndContinue(HuntsmanTrophies.get(design).animation());
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    /** The client draws it with {@code HuntsmanTrophyItemRenderer} (only made on the client, the first time it's needed). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer)
    {
        consumer.accept(new IClientItemExtensions()
        {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer()
            {
                if (this.renderer == null)
                {
                    this.renderer = new com.reg21meme.dross.boss.huntsman.trophy.client.HuntsmanTrophyItemRenderer();
                }
                return this.renderer;
            }
        });
    }
}
