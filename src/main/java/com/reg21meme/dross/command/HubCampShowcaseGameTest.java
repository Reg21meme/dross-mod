package com.reg21meme.dross.command;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * {@code /dross showcase hub_camps} ({@link HubCampShowcase}): the Shattered Spire built once per camp design, facing
 * east (a turned template), each camp's template on top; then every block of each camp's template (air too) is in the
 * world where it belongs, its beds whole, and the spire itself stands round it (its frame).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HubCampShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Far from spawn and the other tests. */
    private static final BlockPos TRY_AT = new BlockPos(61000, 80, -58000);
    /** At least this share of a camp's template blocks must be in the world as placed (blocks only). */
    private static final double MIN_MATCH = 0.99D;

    private HubCampShowcaseGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hub_camp_showcase", timeoutTicks = 600)
    public static void campsStandAtTheSpiresSteps(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        // facing west: the templates turned, so their corners and turns must line up with the spire's
        HubCampShowcase.Plan plan = HubCampShowcase.plan(level, TRY_AT, Direction.WEST);
        List<ChunkPos> forced = forceChunks(level, plan);
        List<String> problems = new ArrayList<>();
        try
        {
            for (HubCampShowcase.Plot plot : plan.plots())
            {
                if (!HubCampShowcase.build(level, plan, plot))
                {
                    problems.add(plot.camp().name() + " wasn't built (a template missing?)");
                    continue;
                }
                problems.addAll(check(level, plan, plot));
            }
        }
        finally
        {
            forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
        }
        if (!problems.isEmpty())
        {
            throw new GameTestAssertException("the hub camps aren't right: " + String.join("; ", problems));
        }
        helper.succeed();
    }

    private static List<String> check(ServerLevel level, HubCampShowcase.Plan plan, HubCampShowcase.Plot plot)
    {
        List<String> problems = new ArrayList<>();
        StructureTemplate template = level.getStructureManager()
                .get(new ResourceLocation(Dross.MODID, plot.camp().template())).orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(HubCampShowcase.rotation(plan.front()))
                .setRotationPivot(HubCampShowcase.pivot());
        BlockPos origin = HubCampShowcase.origin(plot);
        int total = 0;
        int same = 0;
        int beds = 0;
        String firstWrong = null;
        for (Block block : BuiltInRegistries.BLOCK)
        {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block))
            {
                total++;
                BlockState there = level.getBlockState(info.pos());
                if (there.is(block))
                {
                    same++;
                }
                else if (firstWrong == null)
                {
                    firstWrong = info.pos().toShortString() + " is " + there + ", not " + block;
                }
                if (block instanceof BedBlock)
                {
                    beds++;
                    BlockPos other = info.pos().relative(BedBlock.getConnectedDirection(there.is(block) ? there : info.state()));
                    if (!(level.getBlockState(other).getBlock() instanceof BedBlock))
                    {
                        problems.add(plot.camp().name() + "'s bed at " + info.pos().toShortString() + " is broken");
                    }
                }
            }
        }
        double share = total == 0 ? 0.0D : same / (double) total;
        if (total == 0)
        {
            problems.add(plot.camp().name() + "'s template is empty");
        }
        else if (share < MIN_MATCH)
        {
            problems.add(String.format("%s: only %.1f%% of its %d template blocks are there (%s)", plot.camp().name(),
                    100.0D * share, total, firstWrong));
        }
        if (beds == 0)
        {
            problems.add(plot.camp().name() + " has no beds (the Oracle and the Seeker sleep there)");
        }
        // the spire round it: its frame blocks
        BoundingBox box = HubCampShowcase.spire().footprint(plot.center(), plan.front());
        int frame = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
        {
            if (level.getBlockState(pos).getBlock().getDescriptionId().endsWith("dross_portal_frame"))
            {
                frame++;
            }
        }
        if (frame < 16)
        {
            problems.add(plot.camp().name() + "'s spire has " + frame + " frame blocks (its portal frame missing?)");
        }
        LOGGER.info("[hub camps] {}: {} of {} template blocks in place, {} bed halves, the spire's frame {} blocks",
                plot.camp().name(), same, total, beds, frame);
        return problems;
    }

    private static List<ChunkPos> forceChunks(ServerLevel level, HubCampShowcase.Plan plan)
    {
        List<ChunkPos> forced = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (HubCampShowcase.Plot plot : plan.plots())
        {
            BoundingBox box = HubCampShowcase.spire().footprint(plot.center(), plan.front()).inflatedBy(24);
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                {
                    if (seen.add(ChunkPos.asLong(cx, cz)))
                    {
                        level.setChunkForced(cx, cz, true);
                        level.getChunk(cx, cz);
                        forced.add(new ChunkPos(cx, cz));
                    }
                }
            }
        }
        return forced;
    }
}
