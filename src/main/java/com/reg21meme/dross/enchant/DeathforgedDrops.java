package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossMobGear;
import com.reg21meme.dross.registry.ModEnchantments;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Deathforged books drop only from the netherite-wearing zombies and skeletons of the Dross
 * ({@link DrossMobGear#hasDrossGear}), and only when a player hit them recently. Risen undead never drop them.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DeathforgedDrops
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers.
    // ---------------------------------------------------------------------------------------------

    /** Chance per kill (0.0 - 1.0): 5%. */
    private static final float BASE_DROP_CHANCE = 0.05F;
    /** Extra chance per level of Looting: +1%. */
    private static final float DROP_CHANCE_PER_LOOTING = 0.01F;

    /**
     * Weight of each book level when one drops (index 0 = level I ... index 9 = level X), out of 100.
     * So Deathforged X is 1 in 100 books, about 1 in 2,000 kills without Looting.
     */
    private static final int[] LEVEL_WEIGHTS = {25, 20, 15, 12, 9, 7, 5, 4, 2, 1};

    private DeathforgedDrops() {}

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event)
    {
        LivingEntity mob = event.getEntity();
        if (mob.level().isClientSide() || !event.isRecentlyHit())
        {
            return;
        }
        if (RisenUndead.isRisen(mob) || !DrossMobGear.hasDrossGear(mob))
        {
            return;
        }
        RandomSource random = mob.getRandom();
        float chance = BASE_DROP_CHANCE + DROP_CHANCE_PER_LOOTING * event.getLootingLevel();
        if (random.nextFloat() >= chance)
        {
            return;
        }
        int level = rollLevel(random);
        ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(ModEnchantments.DEATHFORGED.get(), level));
        ItemEntity drop = new ItemEntity(mob.level(), mob.getX(), mob.getY(), mob.getZ(), book);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    /** Picks a level 1-10 using {@link #LEVEL_WEIGHTS}. */
    private static int rollLevel(RandomSource random)
    {
        int total = 0;
        for (int weight : LEVEL_WEIGHTS)
        {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < LEVEL_WEIGHTS.length; i++)
        {
            roll -= LEVEL_WEIGHTS[i];
            if (roll < 0)
            {
                return i + 1;
            }
        }
        return 1;
    }
}
