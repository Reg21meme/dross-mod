package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Rage Bow ({@code dross:rage_bow}), a creative-only test tool like the Rage Wand (the user, October 31: "a rage
 * bow?" to rage many mobs at once). Since November 2 it's a hitscan gun (the user: its arrows dropped and were slow, "make
 * it a gun ... instantly and shoot fast"): right-click fires at once along where you look ({@link RageBurst#aim}, up to
 * {@link RageTuning#BOW_REACH} blocks), a beam of its team's colour out to it, and every mob within
 * {@link RageTuning#BOW_BURST_RADIUS} blocks of what it hit is raged onto the bow's team ({@link RageBurst}); a player it
 * hits goes onto it. Holding right-click keeps firing ({@link RageTuning#BOW_FIRE_TICKS} ticks apart). No damage, no
 * arrows. Sneak + right-click moves it to the next team (its string and crystal take the team's colour).
 */
public class RageBowItem extends BowItem
{
    public RageBowItem()
    {
        super(new Properties().stacksTo(1));
    }

    /** The bow's team (it keeps it like the wand: {@link RageWandItem#TAG_TEAM}). */
    public static RageTeam teamOf(ItemStack stack)
    {
        return RageWandItem.teamOf(stack);
    }

    /** Right-click fires (held, it keeps firing); sneak + right-click: the next team. */
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
        if (level instanceof ServerLevel server)
        {
            RageTeam team = teamOf(stack);
            RageBurst.Hit hit = RageBurst.aim(server, player, RageTuning.BOW_REACH);
            RageBurst.beam(server, player, hit.at(), team);
            if (hit.entity() instanceof Player other)
            {
                Rage.setPlayerTeam(other, team);
            }
            RageBurst.burst(server, hit.at(), RageTuning.BOW_BURST_RADIUS, team, player, false);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS,
                    0.8F, 1.3F + level.getRandom().nextFloat() * 0.2F);
        }
        player.getCooldowns().addCooldown(this, RageTuning.BOW_FIRE_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
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
