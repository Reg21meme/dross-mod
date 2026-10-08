package com.reg21meme.dross.portal;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModParticles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lights a Dross portal when a netherite ingot item (thrown or dropped) is inside the empty
 * opening of a complete {@code dross_portal_frame} frame. Exactly one ingot is used up.
 * <p>
 * To stay cheap, this never scans the world: it remembers only the netherite-ingot item entities
 * that exist (when they join a level) and checks the block each one is in, once per tick.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossPortalActivation
{
    /** Netherite ingot item entities currently in server levels (weak, so they are forgotten when gone). */
    private static final Set<ItemEntity> INGOTS = Collections.newSetFromMap(new WeakHashMap<>());

    private DrossPortalActivation() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ItemEntity item
                && item.getItem().is(Items.NETHERITE_INGOT))
        {
            INGOTS.add(item);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || INGOTS.isEmpty())
        {
            return;
        }
        for (ItemEntity item : new ArrayList<>(INGOTS))
        {
            if (item.isRemoved() || !item.getItem().is(Items.NETHERITE_INGOT))
            {
                INGOTS.remove(item);
                continue;
            }
            if (item.level() instanceof ServerLevel level && tryActivate(level, item))
            {
                if (item.isRemoved())
                {
                    INGOTS.remove(item);
                }
            }
        }
    }

    private static boolean tryActivate(ServerLevel level, ItemEntity item)
    {
        BlockPos pos = item.blockPosition();
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() && !here.is(BlockTags.FIRE))
        {
            return false;
        }

        Optional<DrossPortalShape> shape = DrossPortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);
        if (shape.isEmpty())
        {
            return false;
        }

        shape.get().createPortalBlocks();

        // Use up exactly one ingot.
        ItemStack stack = item.getItem().copy();
        stack.shrink(1);
        if (stack.isEmpty())
        {
            item.discard();
        }
        else
        {
            item.setItem(stack);
            // The leftover ingots now sit inside the lit portal. Give them a portal cooldown so they stay
            // here for the player to pick up instead of falling straight through to Dross.
            item.setPortalCooldown();
        }

        // Lighting sound + a puff of orange portal particles.
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.2F);
        level.sendParticles(ModParticles.DROSS_PORTAL.get(), item.getX(), item.getY() + 0.5D, item.getZ(), 40, 0.4D, 0.6D, 0.4D, 0.5D);
        return true;
    }
}
