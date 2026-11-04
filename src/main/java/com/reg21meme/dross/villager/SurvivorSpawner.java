package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.portal.DrossHub;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * The hub's two survivors ({@link Survivor}), the Blind Oracle and the Ragged Seeker, come <b>with the land</b>: the hub's
 * structure (portal area, {@code portal.DrossHubPiece}) puts each one on his rest spot in the camp when it builds the
 * chunk that holds that spot ({@link #spawnWithLand}), the way a village's villagers come with it. So they're made
 * exactly once per world (a chunk is only generated once), and only when someone first goes to the Dross.
 * <p>
 * Their UUIDs come from the world's seed ({@link #mentorId}, {@link #studentId}), so each knows his partner even when
 * the two rest spots are in different chunks, made at different times.
 */
public final class SurvivorSpawner
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private SurvivorSpawner() {}

    /** The Blind Oracle's UUID in the world with this seed. */
    public static UUID mentorId(long seed)
    {
        return idFor(seed, SurvivorRole.MENTOR);
    }

    /** The Ragged Seeker's UUID in the world with this seed. */
    public static UUID studentId(long seed)
    {
        return idFor(seed, SurvivorRole.STUDENT);
    }

    private static UUID idFor(long seed, SurvivorRole role)
    {
        return UUID.nameUUIDFromBytes(("dross:survivor/" + role.id() + "/" + seed).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Puts the survivors whose rest spots are in {@code chunk} (the chunk being made: only X and Z count) there, with their
     * home in the hub's camp ({@link SurvivorGrounds#fromCamp}) and each other as partners. World generation only.
     */
    public static void spawnWithLand(WorldGenLevel level, DrossHub.Layout layout, BoundingBox chunk)
    {
        SurvivorGrounds grounds = SurvivorGrounds.fromCamp(layout);
        if (grounds == null)
        {
            LOGGER.error("[Dross] Survivors: the hub's camp says nothing of where they live, so they weren't spawned.");
            return;
        }
        long seed = level.getSeed();
        SurvivorRole[] roles = {SurvivorRole.MENTOR, SurvivorRole.STUDENT};
        for (int i = 0; i < roles.length; i++)
        {
            BlockPos rest = grounds.restSpot(i);
            if (rest.getX() < chunk.minX() || rest.getX() > chunk.maxX() || rest.getZ() < chunk.minZ() || rest.getZ() > chunk.maxZ())
            {
                continue;
            }
            Survivor survivor = Survivor.create(level.getLevel(), roles[i]);
            if (survivor == null)
            {
                LOGGER.error("[Dross] Survivors: couldn't create the {}.", roles[i].id());
                continue;
            }
            survivor.setUUID(idFor(seed, roles[i]));
            survivor.setHome(grounds, i);
            survivor.setPartner(idFor(seed, roles[1 - i]));
            survivor.placeAtRestSpot();
            level.addFreshEntity(survivor);
            LOGGER.info("[Dross] Survivors: the {} came with the hub's land, at {}; he lives within X {}..{}, Z {}..{} (his camp and {} "
                            + "blocks round it), {} places to walk to", roles[i] == SurvivorRole.MENTOR ? "Blind Oracle" : "Ragged Seeker",
                    rest.toShortString(), grounds.area().minX(), grounds.area().maxX(), grounds.area().minZ(), grounds.area().maxZ(),
                    SurvivorGrounds.CAMP_AREA_MARGIN, grounds.waypoints().size());
        }
    }
}
