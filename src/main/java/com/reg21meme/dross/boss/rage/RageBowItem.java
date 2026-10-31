package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Rage Bow ({@code dross:rage_bow}), a creative-only test tool like the Rage Wand (the user, October 31: "a rage
 * bow?" to rage many mobs at once). Its arrows need no ammunition and do no damage: where one lands every mob within
 * {@link RageTuning#BOW_BURST_RADIUS} blocks is raged onto the bow's team ({@link RageBurst}), and a player it hits goes
 * onto it. Sneak + right-click moves it to the next team (its string and crystal take the team's colour); its arrows
 * trail the colour as they fly. The landing is handled in {@link RageEvents}.
 */
public class RageBowItem extends BowItem
{
    /** The arrow's tag: the team it rages onto. */
    public static final String TAG_ARROW_TEAM = "DrossRageArrow";

    /** Its arrows in flight, for their trail ({@link #trail}). */
    private static final Set<AbstractArrow> FLYING = Collections.newSetFromMap(new WeakHashMap<>());

    public RageBowItem()
    {
        super(new Properties().stacksTo(1));
    }

    /** The bow's team (it keeps it like the wand: {@link RageWandItem#TAG_TEAM}). */
    public static RageTeam teamOf(ItemStack stack)
    {
        return RageWandItem.teamOf(stack);
    }

    /** The team a Rage Bow's arrow rages onto, or null for any other projectile. */
    @Nullable
    public static RageTeam arrowTeam(Entity projectile)
    {
        return projectile.getPersistentData().contains(TAG_ARROW_TEAM)
                ? RageTeam.byId(projectile.getPersistentData().getString(TAG_ARROW_TEAM)) : null;
    }

    /** Right-click draws it (no arrows needed); sneak + right-click: the next team. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown())
        {
            if (!level.isClientSide)
            {
                RageTeam next = teamOf(stack).next();
                RageWandItem.setTeam(stack, next);
                player.displayClientMessage(Component.translatable("item.dross.rage_bow.team_changed", next.displayName()), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON,
                        SoundSource.PLAYERS, RageTuning.TEAM_CLICK_VOLUME, RageTuning.TEAM_CLICK_PITCH);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** Let go: an arrow of the bow's team, no damage, never picked up (except in creative). */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity shooter, int timeLeft)
    {
        float power = getPowerForTime(this.getUseDuration(stack) - timeLeft);
        if (power < 0.1F || level.isClientSide)
        {
            return;
        }
        Arrow arrow = new Arrow(level, shooter);
        arrow.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot(), 0.0F, power * 3.0F, 1.0F);
        arrow.setBaseDamage(0.0D);
        arrow.setCritArrow(power >= 1.0F);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.getPersistentData().putString(TAG_ARROW_TEAM, teamOf(stack).id());
        level.addFreshEntity(arrow);
        FLYING.add(arrow);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
    }

    /** Every server tick: its arrows in flight trail their team's colour. */
    static void trail()
    {
        FLYING.removeIf(arrow -> !arrow.isAlive());
        for (AbstractArrow arrow : FLYING)
        {
            RageTeam team = arrowTeam(arrow);
            if (team != null && arrow.level() instanceof ServerLevel level)
            {
                level.sendParticles(new DustParticleOptions(DrossColors.vector(team.color()), RageTuning.ARROW_TRAIL_SIZE),
                        arrow.getX(), arrow.getY(), arrow.getZ(), 2, 0.05D, 0.05D, 0.05D, 0.0D);
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.dross.rage_wand.tooltip.team", teamOf(stack).displayName())
                .withStyle(DrossColors.RAGE_WAND_TOOLTIP));
        tooltip.add(Component.translatable("item.dross.rage_bow.tooltip").withStyle(DrossColors.RAGE_WAND_TOOLTIP));
        tooltip.add(Component.translatable("item.dross.rage_bow.tooltip.hint").withStyle(DrossColors.RAGE_WAND_HINT));
    }
}
