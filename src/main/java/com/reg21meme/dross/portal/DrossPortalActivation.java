package com.reg21meme.dross.portal;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModItems;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lighting Dross portals with the Rift Key, and the "no portals inside the Dross" rule.
 * <ul>
 *   <li><b>Lighting:</b> when a Rift Key item (thrown or dropped) is inside the empty opening of a complete
 *       {@code dross_portal_frame} frame (along X or Z), the opening fills with {@code dross:dross_portal}
 *       and exactly one key is used up.</li>
 *   <li><b>Already lit:</b> a Rift Key that lands in a lit Dross portal isn't used. It's pushed back out of the
 *       portal and gets a portal cooldown, so it doesn't fall through to the Dross.</li>
 *   <li><b>Inside the Dross:</b> the Rift Key lights nothing, and nether portals can't be lit either
 *       (otherwise they would be a second way out).</li>
 * </ul>
 * To stay cheap, this never scans the world: it remembers only the Rift Key item entities that exist
 * (when they join a level) and checks the block each one is in, once per tick.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossPortalActivation
{
    /** How fast a key that landed in a lit portal is pushed back out (blocks per tick, sideways). */
    private static final double POP_OUT_SPEED = 0.3D;
    /** And how fast upwards, so it hops out. */
    private static final double POP_OUT_UP_SPEED = 0.25D;
    /** How far from the middle of the portal block the key is moved, so it is fully outside the portal. */
    private static final double POP_OUT_DISTANCE = 0.9D;
    /** Blue particles when a portal lights: this many per opening block, but never more than the max. */
    private static final int LIGHT_PARTICLES_PER_BLOCK = 8;
    private static final int LIGHT_PARTICLES_MAX = 200;

    /** Rift Key item entities currently in server levels (weak, so they are forgotten when gone). */
    private static final Set<ItemEntity> KEYS = Collections.newSetFromMap(new WeakHashMap<>());

    private DrossPortalActivation() {}

    private static boolean isRiftKey(ItemStack stack)
    {
        return stack.is(ModItems.RIFT_KEY.get());
    }

    private static boolean isInDross(Level level)
    {
        return level.dimension() == ModDimensions.DROSS_LEVEL;
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event)
    {
        if (!event.getLevel().isClientSide()
                && event.getEntity() instanceof ItemEntity item
                && isRiftKey(item.getItem()))
        {
            KEYS.add(item);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || KEYS.isEmpty())
        {
            return;
        }
        for (ItemEntity item : new ArrayList<>(KEYS))
        {
            if (item.isRemoved() || !isRiftKey(item.getItem()))
            {
                KEYS.remove(item);
                continue;
            }
            if (item.level() instanceof ServerLevel level && !isInDross(level))
            {
                tryLight(level, item);
                if (item.isRemoved())
                {
                    KEYS.remove(item);
                }
            }
        }
    }

    /** Lights the frame the key is in, if the key is in the empty opening of a complete frame. */
    private static void tryLight(ServerLevel level, ItemEntity item)
    {
        BlockPos pos = item.blockPosition();
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() && !here.is(BlockTags.FIRE))
        {
            return; // a lit portal is handled by pushBackKey; anything else isn't an empty opening
        }

        Optional<DrossPortalShape> found = DrossPortalShape.findEmptyPortalShape(level, pos, Direction.Axis.X);
        if (found.isEmpty())
        {
            return;
        }
        DrossPortalShape shape = found.get();
        shape.createPortalBlocks();

        // Use up exactly one key.
        ItemStack stack = item.getItem().copy();
        stack.shrink(1);
        if (stack.isEmpty())
        {
            item.discard();
        }
        else
        {
            // (Keys don't stack, but just in case.) The leftovers now sit in the lit portal:
            // a portal cooldown keeps them here for the player to pick up instead of falling through.
            item.setItem(stack);
            item.setPortalCooldown();
        }

        // Lighting sound (a whoosh, plus the crystal key breaking) and a burst of blue portal particles.
        PortalOpening opening = PortalOpening.of(shape);
        double cx = opening.axis() == Direction.Axis.X ? opening.centerAlong() : opening.minCorner().getX() + 0.5D;
        double cz = opening.axis() == Direction.Axis.Z ? opening.centerAlong() : opening.minCorner().getZ() + 0.5D;
        double cy = opening.minCorner().getY() + opening.height() / 2.0D;
        level.playSound(null, cx, cy, cz, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.2F);
        level.playSound(null, cx, cy, cz, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
        int count = Math.min(LIGHT_PARTICLES_MAX, LIGHT_PARTICLES_PER_BLOCK * opening.width() * opening.height());
        double spreadAlong = opening.width() / 3.0D;
        level.sendParticles(ModParticles.DROSS_PORTAL.get(), cx, cy, cz, count,
                opening.axis() == Direction.Axis.X ? spreadAlong : 0.2D, opening.height() / 3.0D,
                opening.axis() == Direction.Axis.Z ? spreadAlong : 0.2D, 0.5D);
    }

    /**
     * Called (server side) by {@link DrossPortalTravel} for every entity inside a lit Dross portal.
     * If it's a Rift Key outside the Dross, the key is pushed back out of the portal and gets a portal
     * cooldown, so it isn't used and doesn't travel.
     *
     * @return true if it was such a key (it must not travel)
     */
    static boolean pushBackKey(Entity entity, BlockPos portalPos)
    {
        if (!(entity instanceof ItemEntity item) || !isRiftKey(item.getItem())
                || !(item.level() instanceof ServerLevel level) || isInDross(level))
        {
            return false;
        }

        BlockState portal = level.getBlockState(portalPos);
        Direction.Axis axis = portal.hasProperty(DrossPortalBlock.AXIS) ? portal.getValue(DrossPortalBlock.AXIS) : Direction.Axis.X;
        // The portal is a flat sheet; push the key out sideways (along Z for an X portal, along X for a Z portal).
        boolean pushAlongZ = axis == Direction.Axis.X;
        double offset = pushAlongZ ? item.getZ() - (portalPos.getZ() + 0.5D) : item.getX() - (portalPos.getX() + 0.5D);
        double motion = pushAlongZ ? item.getDeltaMovement().z : item.getDeltaMovement().x;
        // Back out the side it came in from: the side it's on, or (if it's dead centre) against its motion.
        int side = Math.abs(offset) > 0.05D ? (int) Math.signum(offset) : (motion > 0.0D ? -1 : 1);

        for (int trySide : new int[] {side, -side})
        {
            double nx = pushAlongZ ? item.getX() : portalPos.getX() + 0.5D + trySide * POP_OUT_DISTANCE;
            double nz = pushAlongZ ? portalPos.getZ() + 0.5D + trySide * POP_OUT_DISTANCE : item.getZ();
            AABB moved = item.getBoundingBox().move(nx - item.getX(), 0.0D, nz - item.getZ());
            if (level.noCollision(item, moved))
            {
                item.setPos(nx, item.getY(), nz);
                item.setDeltaMovement(pushAlongZ ? 0.0D : trySide * POP_OUT_SPEED, POP_OUT_UP_SPEED, pushAlongZ ? trySide * POP_OUT_SPEED : 0.0D);
                item.hasImpulse = true; // send the new motion to players right away
                level.playSound(null, nx, item.getY(), nz, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 1.0F, 1.2F);
                break;
            }
        }
        item.setPortalCooldown();
        return true;
    }

    /** No nether portals inside the Dross (flint and steel, fire, or other mods lighting obsidian frames). */
    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event)
    {
        LevelAccessor level = event.getLevel();
        if (level instanceof Level l && isInDross(l))
        {
            event.setCanceled(true);
        }
    }
}
