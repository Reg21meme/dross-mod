package com.reg21meme.dross.villager;

import com.reg21meme.dross.enchant.RisenUndead;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.entity.PartEntity;

/**
 * TESTING ONLY (see "Parked for later" in CLAUDE.md): the Admin Sword, sold by the Dross trader.
 * One hit kills any living thing, bosses included, except the Dross trader himself.
 * It never breaks and survives fire and lava. Its texture is Minecraft's netherite sword, recoloured
 * black with orange veins to match the Dross Portal Frame.
 */
public class AdminSwordItem extends SwordItem
{
    public AdminSwordItem()
    {
        super(Tiers.NETHERITE, 3, -2.4F, new Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    /** Called (on both sides) when a player hits an entity with this sword. Returning true skips the normal hit. */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity)
    {
        // Hitting any part of the Ender Dragon counts as hitting the dragon.
        Entity target = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        if (!(target instanceof LivingEntity living) || living instanceof DrossTrader)
        {
            return false; // boats, item frames, the trader...: a normal hit
        }
        if (player.getUUID().equals(RisenUndead.getOwnerId(living)))
        {
            return true; // your own risen undead: cancel the hit entirely, no damage
        }
        if (!player.level().isClientSide && living.isAlive())
        {
            // A real player attack first, so the kill counts as the player's (loot, XP, advancements).
            living.hurt(player.damageSources().playerAttack(player), Float.MAX_VALUE);
            if (living instanceof EnderDragon dragon)
            {
                // Vanilla keeps a dying dragon at 1 health while it flies back to the exit portal.
                // At 0 health its death animation (rising, light beams, XP, the egg) starts right away instead.
                dragon.setHealth(0.0F);
            }
            else if (living.isAlive())
            {
                // Still alive (the Wither while it's spawning, invulnerable mobs, a totem of undying...): /kill it.
                living.kill();
            }
        }
        return true;
    }

    @Override
    public boolean isDamageable(ItemStack stack)
    {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return true;
    }
}
