package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.registry.ModEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Spawns the hub's two survivors ({@link Survivor}), the Blind Oracle and the Ragged Seeker, <b>once per world</b>, on
 * their rest spots at the hub, when the world starts:
 * <ul>
 *   <li>At {@link EventPriority#LOW low} priority, after the portal area has built the hub; {@link DrossHub#layout} builds
 *       it first anyway if it isn't built yet, so the order doesn't really matter.</li>
 *   <li>If the hub has no layout (no Dross, or no exit portal), a warning is logged and it tries again the next time the
 *       world starts. Once they've spawned ({@link SurvivorSpawnData}), they're never spawned again, like the trader.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SurvivorSpawner
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private SurvivorSpawner() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent event)
    {
        MinecraftServer server = event.getServer();
        SurvivorSpawnData data = SurvivorSpawnData.get(server.overworld());
        if (data.hasSpawned())
        {
            return;
        }
        // The hub is built in the background after a new world starts: wait for it instead of forcing it now.
        DrossHub.whenBuilt(server, built ->
        {
            if (data.hasSpawned())
            {
                return;
            }
            try
            {
                spawn(server, data);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("[Dross] Survivors: spawning them at the hub failed; trying again the next time the world starts.", e);
            }
        });
    }

    private static void spawn(MinecraftServer server, SurvivorSpawnData data)
    {
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            LOGGER.warn("[Dross] Survivors: the Dross isn't loaded, so they weren't spawned; trying again the next time the world starts.");
            return;
        }
        Optional<DrossHub.Layout> layout = DrossHub.layout(server);
        if (layout.isEmpty())
        {
            LOGGER.warn("[Dross] Survivors: the hub has no layout (no exit portal?), so they weren't spawned; trying again the next time "
                    + "the world starts.");
            return;
        }
        SurvivorGrounds grounds = SurvivorGrounds.fromLayout(dross, layout.get(), ModEntities.SURVIVOR.get().getDimensions());
        if (grounds == null)
        {
            LOGGER.warn("[Dross] Survivors: the hub has no two free spots side by side for them, so they weren't spawned; trying again the "
                    + "next time the world starts.");
            return;
        }

        Survivor mentor = Survivor.create(dross, SurvivorRole.MENTOR);
        Survivor student = Survivor.create(dross, SurvivorRole.STUDENT);
        if (mentor == null || student == null)
        {
            LOGGER.warn("[Dross] Survivors: couldn't create them; trying again the next time the world starts.");
            return;
        }
        mentor.setHome(grounds, 0);
        student.setHome(grounds, 1);
        mentor.setPartner(student.getUUID());
        student.setPartner(mentor.getUUID());
        mentor.placeAtRestSpot();
        student.placeAtRestSpot();
        if (!dross.addFreshEntity(mentor))
        {
            LOGGER.warn("[Dross] Survivors: the Blind Oracle couldn't be added to the Dross; trying again the next time the world starts.");
            return;
        }
        if (!dross.addFreshEntity(student))
        {
            mentor.discard();
            LOGGER.warn("[Dross] Survivors: the Ragged Seeker couldn't be added to the Dross; trying again the next time the world starts.");
            return;
        }
        data.markSpawned(mentor.getUUID(), student.getUUID());

        BlockPos a = grounds.restSpot(0);
        BlockPos b = grounds.restSpot(1);
        LOGGER.info("[Dross] Survivors: the Blind Oracle at {}, {}, {} and the Ragged Seeker at {}, {}, {}",
                a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
        LOGGER.info("[Dross] Survivors: they live within X {}..{}, Z {}..{} (the hub's footprint and {} blocks round it, {} hub); {} places to "
                        + "walk to: {}",
                grounds.area().minX(), grounds.area().maxX(), grounds.area().minZ(), grounds.area().maxZ(), SurvivorGrounds.AREA_MARGIN,
                layout.get().kind(), grounds.waypoints().size(),
                grounds.waypoints().stream().map(w -> w.pos().toShortString()).toList());
    }
}
