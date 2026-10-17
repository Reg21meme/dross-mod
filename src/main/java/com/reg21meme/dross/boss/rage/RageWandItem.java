package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Rage Wand ({@code dross:rage_wand}), a creative-only test tool (the user's request; no recipe, in the Dross
 * creative tab after the Admin Sword). It's always on one of five {@link RageTeam}s (saved on the item as
 * {@link #TAG_TEAM}; a new wand is Blue):
 * <ul>
 *   <li><b>right-click</b> (in the air, or on a block) moves it to the next team (Blue, Red, Green, Yellow, Purple,
 *       Blue...): a small click, the team's name on the action bar in its colour, and its crystal turns that colour;</li>
 *   <li><b>hitting a mob</b> rages it onto the wand's team ({@link Rage#enrage}); hit again with another team, it
 *       switches. Raged mobs fight the raged mobs of the other teams, never their own, for ever.</li>
 * </ul>
 * Its hit never hurts anything: no damage and no knockback, on a mob, a player or anything else (the attack is
 * cancelled, in creative and in survival). Players can't be raged. Like a sword, it doesn't break blocks in creative.
 */
public class RageWandItem extends Item
{
    /** The item's tag: the team it's on ({@link RageTeam#id()}). */
    public static final String TAG_TEAM = "RageTeam";

    public RageWandItem()
    {
        super(new Properties().stacksTo(1));
    }

    /** The team a wand is on (Blue if it has never been switched). */
    public static RageTeam teamOf(ItemStack stack)
    {
        RageTeam team = stack.hasTag() ? RageTeam.byId(stack.getTag().getString(TAG_TEAM)) : null;
        return team != null ? team : RageTeam.DEFAULT;
    }

    /** Puts a wand on a team. */
    public static void setTeam(ItemStack stack, RageTeam team)
    {
        stack.getOrCreateTag().putString(TAG_TEAM, team.id());
    }

    /**
     * Called (on both sides) when a player hits an entity with it. Returning true cancels the hit: no damage, no
     * knockback. On the server a mob it hits is raged onto the wand's team.
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity)
    {
        // Hitting any part of a many-part mob (the Ender Dragon) counts as hitting the mob.
        Entity target = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        if (!player.level().isClientSide && target instanceof Mob mob)
        {
            Rage.enrage(mob, teamOf(stack));
        }
        return true;
    }

    /** Right-click: the next team (a click, and its name on the action bar). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide)
        {
            RageTeam next = teamOf(stack).next();
            setTeam(stack, next);
            player.displayClientMessage(Component.translatable("item.dross.rage_wand.team_changed", next.displayName()), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON,
                    SoundSource.PLAYERS, RageTuning.TEAM_CLICK_VOLUME, RageTuning.TEAM_CLICK_PITCH);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Like a sword: it doesn't break blocks in creative. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player)
    {
        return !player.isCreative();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("item.dross.rage_wand.tooltip.team", teamOf(stack).displayName())
                .withStyle(DrossColors.RAGE_WAND_TOOLTIP));
        tooltip.add(Component.translatable("item.dross.rage_wand.tooltip").withStyle(DrossColors.RAGE_WAND_TOOLTIP));
        tooltip.add(Component.translatable("item.dross.rage_wand.tooltip.hint").withStyle(DrossColors.RAGE_WAND_HINT));
    }
}
