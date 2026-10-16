package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * creative tab after the Admin Sword): hit a mob with it and the mob gets the Rage ({@link Rage#enrage}). Raged mobs
 * fight each other, every one for itself, until one is left, which then calms down.
 * <p>
 * Its hit never hurts anything: no damage and no knockback, on a mob, a player or anything else (the attack is
 * cancelled, in creative and in survival). Players can't be raged. Like a sword, it doesn't break blocks in creative.
 */
public class RageWandItem extends Item
{
    public RageWandItem()
    {
        super(new Properties().stacksTo(1));
    }

    /**
     * Called (on both sides) when a player hits an entity with it. Returning true cancels the hit: no damage, no
     * knockback. On the server a mob it hits gets the Rage.
     */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity)
    {
        // Hitting any part of a many-part mob (the Ender Dragon) counts as hitting the mob.
        Entity target = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        if (!player.level().isClientSide && target instanceof Mob mob)
        {
            Rage.enrage(mob);
        }
        return true;
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
        tooltip.add(Component.translatable("item.dross.rage_wand.tooltip").withStyle(DrossColors.RAGE_WAND_TOOLTIP));
    }
}
