package com.reg21meme.dross.boss.huntsman.spear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.Vanishable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Moonsilver Spear ({@code dross:moonsilver_spear}): the Huntsman's spear made by a player (two sticks on a
 * diagonal and a Moonsilver Ingot at the top right). It stabs like a trident and, held back and let go, it's thrown
 * like one ({@link ThrownMoonsilverSpear}) and comes back to its thrower. What it hits, either way, is frozen
 * ({@link SpearFreeze}). Repaired with Moonsilver Ingots.
 */
public class MoonsilverSpearItem extends Item implements Vanishable
{
    private final Multimap<Attribute, AttributeModifier> modifiers;

    public MoonsilverSpearItem()
    {
        super(new Item.Properties().durability(SpearTuning.DURABILITY));
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        // (a player's own 1 of damage comes on top of the modifier)
        builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                SpearTuning.STAB_DAMAGE - 1.0D, AttributeModifier.Operation.ADDITION));
        builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                SpearTuning.ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
        this.modifiers = builder.build();
    }

    @SuppressWarnings("deprecation")
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot)
    {
        return slot == EquipmentSlot.MAINHAND ? this.modifiers : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player)
    {
        return !player.isCreative();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getDamageValue() >= stack.getMaxDamage() - 1)
        {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft)
    {
        if (!(user instanceof Player player) || this.getUseDuration(stack) - timeLeft < SpearTuning.THROW_CHARGE_TICKS)
        {
            return;
        }
        if (!level.isClientSide)
        {
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(player.getUsedItemHand()));
            ThrownMoonsilverSpear spear = new ThrownMoonsilverSpear(level, player, stack);
            spear.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, SpearTuning.THROW_POWER,
                    SpearTuning.THROW_INACCURACY);
            if (player.getAbilities().instabuild)
            {
                spear.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            level.addFreshEntity(spear);
            level.playSound(null, spear, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.1F);
            if (!player.getAbilities().instabuild)
            {
                player.getInventory().removeItem(stack);
            }
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        SpearFreeze.freeze(target, attacker);
        stack.hurtAndBreak(1, attacker, a -> a.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner)
    {
        if (state.getDestroySpeed(level, pos) != 0.0F)
        {
            stack.hurtAndBreak(2, miner, m -> m.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
        return true;
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repair)
    {
        return repair.is(ModItems.MOONSILVER_INGOT.get());
    }

    @Override
    public int getEnchantmentValue()
    {
        return SpearTuning.ENCHANTABILITY;
    }

    /**
     * Besides what anything that wears out takes (Unbreaking, Mending, Curse of Vanishing): Sharpness or Smite, Impaling,
     * Looting, Knockback and Channeling, at the enchanting table and from books (the user, October 25, 2026). Not Fire
     * Aspect (it fights the freeze), Riptide (it would throw you, not the spear), Loyalty (it comes back anyway) or
     * Sweeping Edge (a sword's swing).
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment)
    {
        return SpearTuning.EXTRA_ENCHANTMENTS.contains(enchantment) || super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    {
        lines.add(Component.translatable("item.dross.moonsilver_spear.hint").withStyle(ChatFormatting.GRAY));
    }
}
