package com.reg21meme.dross.world;

import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The shape of what's built at the site, seen from above, relative to the <b>site column</b> (the X/Z stored for the
 * site): the user's castle template if there's a usable one, otherwise the Fallen Cathedral. Used to search for a spot
 * where that footprint fits, and to check it.
 *
 * @param kind  what will be built ({@link PortalSiteData#KIND_TEMPLATE} or {@link PortalSiteData#KIND_CATHEDRAL})
 * @param minDX the footprint's lowest X, relative to the site column
 * @param minDZ the footprint's lowest Z, relative to the site column
 * @param maxDX the footprint's highest X, relative to the site column
 * @param maxDZ the footprint's highest Z, relative to the site column
 */
record SiteLayout(String kind, int minDX, int minDZ, int maxDX, int maxDZ)
{
    /** What this world will build: the castle template if it exists and has exactly one usable frame, else the cathedral. */
    static SiteLayout forWorld(ServerLevel level)
    {
        Optional<StructureTemplate> template = level.getStructureManager().get(PortalSite.CASTLE_TEMPLATE);
        if (template.isPresent() && PortalCastle.isUsable(template.get()))
        {
            return forTemplate(template.get().getSize());
        }
        return cathedral();
    }

    /** The Fallen Cathedral, built with its frame opening's middle column on the site column. */
    static SiteLayout cathedral()
    {
        ShrineDesign design = PortalCathedral.design().orElse(null);
        if (design == null)
        {
            // No cathedral design (shouldn't happen): reserve the small placeholder shrine's floor plus a little.
            return new SiteLayout(PortalSiteData.KIND_PLACEHOLDER, -8, -8, 8, 8);
        }
        BoundingBox box = design.footprint(design.centerForFrameAt(0, 0, 0, PortalCathedral.FACING), PortalCathedral.FACING);
        return new SiteLayout(PortalSiteData.KIND_CATHEDRAL, box.minX(), box.minZ(), box.maxX(), box.maxZ());
    }

    /** A castle template, centred on the site column (as {@link PortalCastle} places it). */
    static SiteLayout forTemplate(Vec3i size)
    {
        int minDX = -size.getX() / 2;
        int minDZ = -size.getZ() / 2;
        return new SiteLayout(PortalSiteData.KIND_TEMPLATE, minDX, minDZ, minDX + size.getX() - 1, minDZ + size.getZ() - 1);
    }

    /** The footprint with the site column at {@code x}/{@code z}, from {@code minY} to {@code maxY}. */
    BoundingBox footprintAt(int x, int z, int minY, int maxY)
    {
        return new BoundingBox(x + minDX, minY, z + minDZ, x + maxDX, maxY, z + maxDZ);
    }

    /** The footprint's middle column with the site column at {@code x}/{@code z}. */
    BlockPos middleAt(int x, int z)
    {
        return new BlockPos(x + (minDX + maxDX) / 2, 0, z + (minDZ + maxDZ) / 2);
    }

    int width()
    {
        return maxDX - minDX + 1;
    }

    int depth()
    {
        return maxDZ - minDZ + 1;
    }
}
