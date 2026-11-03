package com.reg21meme.dross.portal.camp;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * The survivors' camp at the Dross hub: the user's pick (November 3, 2026: "3A is best for the camps, let's implement
 * that one"), No. 3A, Under the Fallen Stones ({@link HubCampDesigns}). When the hub is built, its template goes onto the
 * Shattered Spire at the foot of its front steps ({@link #place}, after the spire and before its frame is lit), and the
 * Blind Oracle and the Ragged Seeker live in it ({@link #spots}) instead of wandering the spire. {@code /dross showcase
 * hub_camps} places it the same way.
 * <p>
 * A camp's template is the spire's size, so it goes on at the spire's own corner and turn: {@link #origin} and
 * {@link #rotation} are the spire's ({@code ShrineDesign.build}).
 */
public final class HubCamp
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The camp built at the real hub. */
    public static final String HUB_CAMP = "fallen_stones_a";
    /** How high the camp's box reaches over the spire's ground (blocks; its shelter's slab is 5 up). */
    static final int BOX_UP = 8;
    /** ...and under it. */
    static final int BOX_DOWN = 2;

    /** Where the survivors live in a camp placed in the world. */
    public record Spots(BlockPos mentor, float mentorYaw, BlockPos student, float studentYaw, List<Place> places,
                        BoundingBox box)
    {
    }

    /** A place they like to walk to (the cell their feet stand in) and how often it's picked. */
    public record Place(BlockPos pos, int weight)
    {
    }

    private HubCamp() {}

    /** The camp built at the real hub (the user's pick). */
    public static HubCampDesigns.Camp hubCamp()
    {
        for (HubCampDesigns.Camp camp : HubCampDesigns.CAMPS)
        {
            if (camp.id().equals(HUB_CAMP))
            {
                return camp;
            }
        }
        return HubCampDesigns.CAMPS.get(0);
    }

    /** The template's corner (its ground at the spire's center's height) for a spire centred on {@code spireCenter}. */
    public static BlockPos origin(BlockPos spireCenter)
    {
        return spireCenter.offset(-HubCampDesigns.WIDTH / 2, -HubCampDesigns.GROUND_LAYER, -HubCampDesigns.DEPTH / 2);
    }

    /** The template turns round its middle column, like the spire's (so both land on the same blocks). */
    public static BlockPos pivot()
    {
        return new BlockPos(HubCampDesigns.WIDTH / 2, 0, HubCampDesigns.DEPTH / 2);
    }

    /** The turn that takes "front faces south" (how the templates are made) to "front faces {@code front}". */
    public static Rotation rotation(Direction front)
    {
        return switch (front)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** A template cell (unturned: x, layer, z) in the world, for a spire centred on {@code spireCenter}. */
    public static BlockPos toWorld(BlockPos spireCenter, Direction front, BlockPos local)
    {
        return StructureTemplate.transform(local, Mirror.NONE, rotation(front), pivot()).offset(origin(spireCenter));
    }

    /** A yaw for a south-facing spire, turned with the spire (Minecraft's yaw: 0 = south, 90 = west). */
    static float turnYaw(float yaw, Direction front)
    {
        return Mth.wrapDegrees(yaw + 90.0F * rotation(front).ordinal());
    }

    /**
     * Places a camp's template on the spire built centred on {@code spireCenter}, its front facing {@code front}.
     *
     * @return false (with an error in the log) if its template is missing
     */
    public static boolean place(ServerLevel level, HubCampDesigns.Camp camp, BlockPos spireCenter, Direction front, int flags)
    {
        ResourceLocation id = new ResourceLocation(Dross.MODID, camp.template());
        Optional<StructureTemplate> found = level.getStructureManager().get(id);
        if (found.isEmpty())
        {
            LOGGER.error("Hub camp: the template {} is missing (data/dross/structures/{}.nbt)", id, camp.template());
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation(front))
                .setRotationPivot(pivot())
                .setIgnoreEntities(true);
        BlockPos origin = origin(spireCenter);
        found.get().placeInWorld(level, origin, origin, settings, level.getRandom(), flags);
        return true;
    }

    /**
     * Where the survivors live in a camp placed on the spire centred on {@code spireCenter}: their rest spots (mentor's
     * first) and the ways they face there, the places they walk to, and the camp's box (its {@code focus}, from
     * {@value #BOX_DOWN} under the spire's ground to {@value #BOX_UP} over it).
     *
     * @return null if the camp doesn't say where they live
     */
    @Nullable
    public static Spots spots(HubCampDesigns.Camp camp, BlockPos spireCenter, Direction front)
    {
        if (camp.mentor() == null || camp.student() == null)
        {
            return null;
        }
        List<Place> places = new ArrayList<>();
        for (HubCampDesigns.Place p : camp.places())
        {
            places.add(new Place(toWorld(spireCenter, front, new BlockPos(p.x(), p.y(), p.z())), p.weight()));
        }
        int g = HubCampDesigns.GROUND_LAYER;
        BlockPos a = toWorld(spireCenter, front, new BlockPos(camp.focusMinX(), g - BOX_DOWN, camp.focusMinZ()));
        BlockPos b = toWorld(spireCenter, front, new BlockPos(camp.focusMaxX(), g + BOX_UP, camp.focusMaxZ()));
        return new Spots(toWorld(spireCenter, front, at(camp.mentor())), turnYaw(camp.mentor().yaw(), front),
                toWorld(spireCenter, front, at(camp.student())), turnYaw(camp.student().yaw(), front),
                List.copyOf(places), BoundingBox.fromCorners(a, b));
    }

    private static BlockPos at(HubCampDesigns.Spot spot)
    {
        return new BlockPos(spot.x(), spot.y(), spot.z());
    }
}
