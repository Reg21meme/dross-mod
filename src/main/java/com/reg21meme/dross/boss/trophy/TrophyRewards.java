package com.reg21meme.dross.boss.trophy;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * How a beaten boss hands out its trophy, shared by every boss (the Cinder Colossus's {@link ColossusTrophies#giveTo},
 * the Rat King's {@code RatKingTrophies.giveTo}): each player who helped gets their own trophy item.
 * <ul>
 *   <li>Straight into their inventory, with vanilla's pickup "pop" ({@code /give}'s sound), if there's room.</li>
 *   <li>If their inventory is full, dropped {@link #DROP_AHEAD} block in front of them, at their feet's height
 *       ({@link #dropInFront}; at their feet if a block is in the way).</li>
 *   <li>A dead player (waiting to respawn) has no inventory to use: it's dropped where they died ({@link #dropFor}).</li>
 * </ul>
 * A dropped trophy is theirs alone (nobody else can pick it up) and can't be destroyed: fire, lava, explosions and
 * cactus don't hurt it, and it never despawns. ({@code /kill} and the void still remove it, like anything.)
 */
public final class TrophyRewards
{
    /** Ticks before a dropped trophy can be picked up (so the player sees it drop). */
    public static final int DROP_PICKUP_DELAY = 20;
    /** How far above the player's feet it's dropped (blocks). */
    public static final double DROP_HEIGHT = 0.25D;
    /** With a full inventory, the trophy is dropped this far in front of the player (blocks, the way they face). */
    public static final double DROP_AHEAD = 1.0D;
    /** Volume of the pickup "pop" when it goes straight into the inventory (vanilla's /give uses the same). */
    public static final float GIVE_SOUND_VOLUME = 0.2F;

    private TrophyRewards()
    {
    }

    /**
     * Gives this trophy to the player: into their inventory, or dropped for them (see the class comment).
     *
     * @param stack the trophy (not changed: a copy is given)
     * @return the dropped item, or null when it all went into their inventory
     */
    @Nullable
    public static ItemEntity giveTo(ServerPlayer player, ItemStack stack)
    {
        if (!player.isAlive() || player.hasDisconnected())
        {
            return dropFor(player, stack);
        }
        ItemStack toGive = stack.copy();
        Inventory inventory = player.getInventory();
        // Checked first: in creative mode vanilla's add() would just delete a stack that doesn't fit.
        boolean fits = inventory.getSlotWithRemainingSpace(toGive) >= 0 || inventory.getFreeSlot() >= 0;
        if (fits)
        {
            inventory.add(toGive);
        }
        if (toGive.getCount() < stack.getCount())
        {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP,
                    SoundSource.PLAYERS, GIVE_SOUND_VOLUME,
                    ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F);
            player.containerMenu.broadcastChanges();
        }
        return toGive.isEmpty() ? null : dropInFront(player, toGive);
    }

    /**
     * Drops the trophy {@link #DROP_AHEAD} block in front of the player (the way they face, level), at about their
     * feet's height. If that spot is inside a block, or a wall is between them and it, it's dropped at their feet
     * instead. Theirs alone and indestructible, like {@link #dropFor}.
     *
     * @return the dropped item, already in the player's world
     */
    public static ItemEntity dropInFront(ServerPlayer player, ItemStack stack)
    {
        Level level = player.level();
        Vec3 feet = new Vec3(player.getX(), player.getY() + DROP_HEIGHT, player.getZ());
        Vec3 ahead = feet.add(Vec3.directionFromRotation(0.0F, player.getYRot()).scale(DROP_AHEAD));
        AABB box = EntityType.ITEM.getDimensions().makeBoundingBox(ahead);
        boolean clear = level.noCollision(box)
                && level.clip(new ClipContext(feet, ahead, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                        .getType() == HitResult.Type.MISS;
        return drop(player, stack, clear ? ahead : feet);
    }

    /**
     * Drops the trophy at the player's feet (where a dead player died), theirs alone and indestructible (see the class
     * comment).
     *
     * @return the dropped item, already in the player's world
     */
    public static ItemEntity dropFor(ServerPlayer player, ItemStack stack)
    {
        return drop(player, stack, new Vec3(player.getX(), player.getY() + DROP_HEIGHT, player.getZ()));
    }

    /** The trophy dropped at {@code at}, kept for the player and indestructible. */
    private static ItemEntity drop(ServerPlayer player, ItemStack stack, Vec3 at)
    {
        ItemEntity item = new ItemEntity(player.level(), at.x, at.y, at.z, stack.copy(), 0.0D, 0.0D, 0.0D);
        item.setTarget(player.getUUID());
        item.setInvulnerable(true);
        item.setUnlimitedLifetime();
        item.setPickUpDelay(DROP_PICKUP_DELAY);
        player.level().addFreshEntity(item);
        return item;
    }
}
