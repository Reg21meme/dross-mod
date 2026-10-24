package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;

/**
 * Puts each part of a hunter village on its own ground as its templates are placed in the world: every block (and
 * every animal) goes up or down to its zone's level ({@link HunterVillageZones}: a courtyard's, a lane's where it is,
 * a decoration's), and a block in no zone isn't placed at all (the world's own land stays there). A column on its own
 * ground (a path, a lantern post) goes onto the ground there as the piece levels it ({@link HunterVillagePiece.Ground}:
 * the world's own, or eased towards a zone near it; the world's ground-height map isn't updated while the village is
 * built, so it can't be read for it). {@link HunterVillagePiece} uses it for the
 * village's template ({@code decor} false) and its decorations' ({@code decor} true: each decoration whole, at its
 * zone's level).
 */
public final class HunterTerrace extends StructureProcessor
{
    /** (Only made by the village's piece: a saved processor list has none to read.) */
    public static final Codec<HunterTerrace> CODEC = Codec.unit(() -> new HunterTerrace(null, false, null));

    @Nullable
    private final RimefallVillages.VillagePlan plan;
    private final boolean decor;
    @Nullable
    private final HunterVillagePiece.Ground ground;

    public HunterTerrace(@Nullable RimefallVillages.VillagePlan plan, boolean decor, @Nullable HunterVillagePiece.Ground ground)
    {
        this.plan = plan;
        this.decor = decor;
        this.ground = ground;
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo placed,
            StructurePlaceSettings settings)
    {
        if (this.plan == null || this.plan.levels() == null)
        {
            return placed;
        }
        BoundingBox box = settings.getBoundingBox();
        BlockPos at = placed.pos();
        if (box != null && (at.getX() < box.minX() || at.getX() > box.maxX() || at.getZ() < box.minZ() || at.getZ() > box.maxZ()))
        {
            return placed;                      // (outside this chunk: it isn't placed here anyway)
        }
        int dy = this.dy(level, original.pos(), at);
        return dy == HunterVillageZones.NONE ? null
                : new StructureTemplate.StructureBlockInfo(at.above(dy), placed.state(), placed.nbt());
    }

    @Override
    public StructureTemplate.StructureEntityInfo processEntity(LevelReader level, BlockPos seed,
            StructureTemplate.StructureEntityInfo raw, StructureTemplate.StructureEntityInfo info,
            StructurePlaceSettings settings, StructureTemplate template)
    {
        if (info == null || this.plan == null || this.plan.levels() == null)
        {
            return info;
        }
        BlockPos local = BlockPos.containing(raw.pos.x, this.plan.design().groundLayer() + 1, raw.pos.z);
        int dy = this.dy(level, local, info.blockPos);
        if (dy == HunterVillageZones.NONE)
        {
            return info;                        // (an animal in no zone: none is, the tool checks it)
        }
        return new StructureTemplate.StructureEntityInfo(info.pos.add(0.0D, dy, 0.0D), info.blockPos.above(dy), info.nbt);
    }

    /** How far up (or down) a block of the template at {@code local} goes ({@code world}: where it would go
     * unshifted), or {@link HunterVillageZones#NONE} if it isn't placed. */
    private int dy(LevelReader level, BlockPos local, BlockPos world)
    {
        HunterVillageZones.Layout zones = this.plan.zones();
        if (zones == null)
        {
            return 0;
        }
        int base = this.plan.ground().getY();
        int zone;
        if (this.decor)
        {
            zone = zones.decorZone(zones.decorationAt(local.getX(), local.getZ()));
        }
        else
        {
            zone = zones.zone(local.getY() > zones.groundLayer, local.getX(), local.getZ());
        }
        if (zone < 0)
        {
            return HunterVillageZones.NONE;
        }
        if (zones.get(zone).kind == HunterVillageZones.Kind.COLUMN)
        {
            int natural = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, world.getX(), world.getZ()) - 1;
            return (this.ground != null ? this.ground.target(local.getX(), local.getZ(), natural) : natural) - base;
        }
        int l = zones.level(this.plan.levels(), zone, local.getX(), local.getZ());
        return l == HunterVillageZones.NONE ? HunterVillageZones.NONE : l - base;
    }

    @Override
    protected StructureProcessorType<?> getType()
    {
        return ModStructures.HUNTER_TERRACE.get();
    }
}
