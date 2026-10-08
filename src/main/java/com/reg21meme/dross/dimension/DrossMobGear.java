package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives zombies and skeletons in the Dross dimension plain netherite gear.
 *
 * <ul>
 *   <li>{@code minecraft:zombie}: full netherite armor + netherite sword.</li>
 *   <li>{@code minecraft:skeleton}: full netherite armor, keeps a (plain) bow.</li>
 * </ul>
 *
 * Nothing is enchanted, and none of it drops on death. Only those two exact mob
 * types are touched: husks, strays, drowned, zombie villagers etc. stay vanilla.
 *
 * <p>Why {@link EntityJoinLevelEvent} and not {@code MobSpawnEvent.FinalizeSpawn}:
 * in Forge 1.20.1 FinalizeSpawn fires <i>before</i> vanilla's {@code finalizeSpawn}
 * hands out random gear and enchantments, so vanilla would overwrite us. A newly
 * spawned mob only joins the level <i>after</i> {@code finalizeSpawn} has run
 * (natural spawns, spawners, spawn eggs, /summon), so this event lets us have the
 * final word and replace everything with plain, unenchanted stacks.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrossMobGear
{
    /**
     * Drop chance for our gear. In 1.20.1 vanilla rolls {@code max(random - looting * 0.01, 0) < chance},
     * so a chance of exactly 0 never drops, even with Looting III.
     */
    private static final float NO_DROP = 0.0F;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private DrossMobGear() {}

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event)
    {
        // Server only, and only mobs that are new (not ones re-loaded from a saved chunk,
        // which already have their gear stored).
        if (event.getLevel().isClientSide() || event.loadedFromDisk())
        {
            return;
        }
        if (!event.getLevel().dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }

        Entity entity = event.getEntity();
        // Exact type checks (not instanceof), so subtypes like husk/stray/drowned are left alone.
        if (entity.getType() == EntityType.ZOMBIE)
        {
            Mob mob = (Mob) entity;
            equipNetheriteArmor(mob);
            equip(mob, EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD);
        }
        else if (entity.getType() == EntityType.SKELETON)
        {
            Mob mob = (Mob) entity;
            equipNetheriteArmor(mob);
            // A brand-new bow replaces vanilla's (possibly enchanted) one.
            equip(mob, EquipmentSlot.MAINHAND, Items.BOW);
        }
    }

    private static void equipNetheriteArmor(Mob mob)
    {
        Item[] armor = { Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS };
        for (int i = 0; i < ARMOR_SLOTS.length; i++)
        {
            equip(mob, ARMOR_SLOTS[i], armor[i]);
        }
    }

    /** Puts a fresh, unenchanted stack in the slot and makes sure it never drops. */
    private static void equip(Mob mob, EquipmentSlot slot, Item item)
    {
        mob.setItemSlot(slot, new ItemStack(item));
        mob.setDropChance(slot, NO_DROP);
    }
}
