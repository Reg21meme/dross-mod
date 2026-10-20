package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Leaves some of a hunter village's decorations out when its decorations' template is placed: every block that lies
 * in one of the {@code dropped} boxes (the template's own coordinates, as {@link HunterVillageDesigns.Design#decorations()}
 * gives them) isn't placed. {@link HunterVillages} rolls each decoration (1 in 2) and drops the ones that lost.
 */
public final class HunterDecorFilter extends StructureProcessor
{
    public static final Codec<HunterDecorFilter> CODEC = BoundingBox.CODEC.listOf().fieldOf("dropped")
            .xmap(HunterDecorFilter::new, filter -> filter.dropped).codec();

    private final List<BoundingBox> dropped;

    public HunterDecorFilter(List<BoundingBox> dropped)
    {
        this.dropped = List.copyOf(dropped);
    }

    @Nullable
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos,
            StructureTemplate.StructureBlockInfo original, StructureTemplate.StructureBlockInfo placed,
            StructurePlaceSettings settings)
    {
        BlockPos local = original.pos();
        for (BoundingBox box : this.dropped)
        {
            if (box.isInside(local))
            {
                return null;
            }
        }
        return placed;
    }

    @Override
    protected StructureProcessorType<?> getType()
    {
        return ModStructures.HUNTER_DECOR.get();
    }
}
