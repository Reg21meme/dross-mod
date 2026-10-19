package com.reg21meme.dross.command;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.village.HunterVillageDesigns;
import com.reg21meme.dross.boss.huntsman.village.HunterVillages;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Headless checks of the hunter villages ({@code /dross showcase hunter_villages}, the boss area's
 * {@link HunterVillageDesigns}): every template is in the mod at its table's size; the showcase's plots are one per
 * design and well apart; and every village, built in the world the way the showcase builds it (each one facing a
 * different way), stands as its template says, with whole beds, and with no clear ice or snow layer lit bright enough
 * to melt once the game has worked its light out (the Python generator softens lights for that; this checks it with
 * the game's own light engine).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HunterVillageShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Where the villages are tried: far from spawn and the other tests, in the air (so no terrain or water). */
    private static final BlockPos TRY_AT = new BlockPos(-41000, 200, 37000);
    /** Ticks given to the light engine before the light is read. */
    private static final int LIGHT_TICKS = 30;
    /** Clear ice melts over this block light, a snow layer over the next (IceBlock, SnowLayerBlock). */
    private static final int ICE_MELTS_ABOVE = 10;
    private static final int SNOW_MELTS_ABOVE = 11;
    /** At least this share of a template's blocks must be in the world where it put them. */
    private static final double MIN_MATCH = 0.98D;

    private HunterVillageShowcaseGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_villages_table")
    public static void templatesMatchTheirTable(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        if (HunterVillageDesigns.villages().size() != 10)
        {
            throw new GameTestAssertException("there should be 10 village designs, but there are "
                    + HunterVillageDesigns.villages().size());
        }
        for (HunterVillageDesigns.Design design : HunterVillageDesigns.ALL)
        {
            Optional<StructureTemplate> template = HunterVillages.template(level, design);
            if (template.isEmpty())
            {
                throw new GameTestAssertException("the template for " + design.name() + " (" + design.template() + ") is missing");
            }
            Vec3i size = template.get().getSize();
            if (size.getX() != design.width() || size.getY() != design.layers() || size.getZ() != design.depth())
            {
                throw new GameTestAssertException(design.name() + "'s template is " + size + ", its table says "
                        + design.width() + " x " + design.layers() + " x " + design.depth());
            }
        }
        // the showcase: one plot per design, none closer than 4 blocks to another
        HunterVillageShowcase.Plan plan = HunterVillageShowcase.plan(level, TRY_AT, Direction.NORTH);
        Set<String> ids = new HashSet<>();
        for (HunterVillageShowcase.Plot plot : plan.plots())
        {
            if (!ids.add(plot.design().id()))
            {
                throw new GameTestAssertException("the showcase has " + plot.design().name() + " twice");
            }
            BoundingBox a = HunterVillages.footprint(plot.ground(), plan.front(), plot.design()).inflatedBy(2);
            for (HunterVillageShowcase.Plot other : plan.plots())
            {
                if (other != plot && a.intersects(HunterVillages.footprint(other.ground(), plan.front(), other.design()).inflatedBy(2)))
                {
                    throw new GameTestAssertException(plot.design().name() + " and " + other.design().name() + " overlap");
                }
            }
        }
        if (ids.size() != HunterVillageDesigns.ALL.size())
        {
            throw new GameTestAssertException("the showcase has " + ids.size() + " plots, not " + HunterVillageDesigns.ALL.size());
        }
        helper.succeed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_villages_built",
            timeoutTicks = 20 * (LIGHT_TICKS + 20) + 400)
    public static void villagesStandAndNeverMelt(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        tryNext(helper, level, 0);
    }

    private static void tryNext(GameTestHelper helper, ServerLevel level, int index)
    {
        List<HunterVillageDesigns.Design> all = HunterVillageDesigns.ALL;
        if (index >= all.size())
        {
            LOGGER.info("[hunter village test] all {} designs stand as their templates say, beds whole, no ice or snow "
                    + "bright enough to melt", all.size());
            helper.succeed();
            return;
        }
        HunterVillageDesigns.Design design = all.get(index);
        Direction front = Direction.from2DDataValue(index % 4);
        BlockPos ground = TRY_AT.offset(index * 80, 0, 0);
        for (int dx = -48; dx <= 48; dx += 16)
        {
            for (int dz = -48; dz <= 48; dz += 16)
            {
                level.getChunkAt(ground.offset(dx, 0, dz));
            }
        }
        HunterVillageShowcase.Plot plot = new HunterVillageShowcase.Plot(design, 0, 0, ground);
        HunterVillageShowcase.Plan plan = new HunterVillageShowcase.Plan(ground, front.getOpposite(), ground.getY(), List.of(plot));
        HunterVillageShowcase.build(level, plan, plot);
        helper.runAfterDelay(LIGHT_TICKS, () ->
        {
            check(level, design, ground, front);
            tryNext(helper, level, index + 1);
        });
    }

    private static void check(ServerLevel level, HunterVillageDesigns.Design design, BlockPos ground, Direction front)
    {
        StructureTemplate template = HunterVillages.template(level, design).orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(HunterVillages.rotationFor(front))
                .setRotationPivot(new BlockPos(design.width() / 2, 0, design.depth() / 2));
        BlockPos origin = ground.offset(-design.width() / 2, -design.groundLayer(), -design.depth() / 2);
        int total = 0;
        int matched = 0;
        int ice = 0;
        int snowLayers = 0;
        int beds = 0;
        for (Block block : List.of(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.ICE, Blocks.SNOW, Blocks.WHITE_BED,
                Blocks.BROWN_BED, Blocks.GRAY_BED, Blocks.LIGHT_GRAY_BED, Blocks.SPRUCE_LOG, Blocks.BONE_BLOCK))
        {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block))
            {
                total++;
                BlockState there = level.getBlockState(info.pos());
                if (there.is(block))
                {
                    matched++;
                }
                if (block == Blocks.ICE && there.is(Blocks.ICE))
                {
                    ice++;
                    int light = level.getBrightness(LightLayer.BLOCK, info.pos());
                    if (light > ICE_MELTS_ABOVE)
                    {
                        throw new GameTestAssertException(design.name() + ": the clear ice at " + info.pos().toShortString()
                                + " has block light " + light + " and would melt");
                    }
                }
                if (block == Blocks.SNOW && there.is(Blocks.SNOW))
                {
                    snowLayers++;
                    int light = level.getBrightness(LightLayer.BLOCK, info.pos());
                    if (light > SNOW_MELTS_ABOVE)
                    {
                        throw new GameTestAssertException(design.name() + ": the snow layer at " + info.pos().toShortString()
                                + " has block light " + light + " and would melt");
                    }
                    if (!there.canSurvive(level, info.pos()))
                    {
                        throw new GameTestAssertException(design.name() + ": the snow layer at " + info.pos().toShortString()
                                + " can't lie on what's under it");
                    }
                }
                if (there.getBlock() instanceof BedBlock && there.getValue(BedBlock.PART) == BedPart.HEAD)
                {
                    beds++;
                    BlockState foot = level.getBlockState(info.pos().relative(there.getValue(BedBlock.FACING).getOpposite()));
                    if (!(foot.getBlock() instanceof BedBlock) || foot.getValue(BedBlock.PART) != BedPart.FOOT)
                    {
                        throw new GameTestAssertException(design.name() + ": the bed at " + info.pos().toShortString()
                                + " has no foot");
                    }
                }
            }
        }
        if (total == 0 || matched < total * MIN_MATCH)
        {
            throw new GameTestAssertException(design.name() + ": only " + matched + " of " + total
                    + " of its snow, ice, logs, bones and beds are where its template put them");
        }
        if (!design.isLodge() && (beds == 0 || ice == 0))
        {
            throw new GameTestAssertException(design.name() + " has " + beds + " beds and " + ice + " clear ice blocks");
        }
        LOGGER.info("[hunter village test] {} (facing {}): {} of {} checked blocks in place, {} clear ice and {} snow layers "
                + "dim enough, {} whole beds", design.name(), front.getName(), matched, total, ice, snowLayers, beds);
    }
}
