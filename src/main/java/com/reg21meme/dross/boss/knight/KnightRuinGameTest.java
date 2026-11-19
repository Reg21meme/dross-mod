package com.reg21meme.dross.boss.knight;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.command.KnightRuinShowcase;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless test (GameTest) for the knight ruin candidates ({@code /dross showcase knight_ruins <element>}, each element
 * run far from everything else, looking south): every ruin of every area is built, its broken frame there (Dross frame
 * blocks in its footprint), its element's two knights on guard in it, on show, each standing on solid ground with room
 * for its body; running an element again replaces them (still two a ruin).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class KnightRuinGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int AT_X = 2600;
    private static final int AT_Z = -2600;
    /** The elements' galleries side by side, this far apart. */
    private static final int ELEMENT_SPACING = 260;

    private KnightRuinGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_knight_ruins", timeoutTicks = 2400)
    public static void ruinsStandWithTheirKnights(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        KnightKind.Element[] elements = KnightKind.Element.values();
        List<BlockPos> origins = new ArrayList<>();
        Set<ChunkPos> forced = new HashSet<>();
        // keep every gallery's land loaded (with its entities) while the test runs: the far rows lie beyond the
        // distance the game keeps mobs loaded round the test, where a knight put in can't be found
        for (int e = 0; e < elements.length; e++)
        {
            int x = AT_X + e * ELEMENT_SPACING;
            level.getChunkAt(new BlockPos(x, 0, AT_Z));
            int y = Math.max(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, AT_Z) - 1, 70);
            BlockPos origin = new BlockPos(x, y, AT_Z);
            origins.add(origin);
            for (KnightRuinShowcase.Plot plot : KnightRuinShowcase.plan(origin, Direction.SOUTH, elements[e]).plots())
            {
                BoundingBox box = KnightRuinShowcase.footprint(plot.ground(), Direction.NORTH, plot.ruin());
                for (int cx = (box.minX() - 8) >> 4; cx <= (box.maxX() + 8) >> 4; cx++)
                {
                    for (int cz = (box.minZ() - 8) >> 4; cz <= (box.maxZ() + 8) >> 4; cz++)
                    {
                        if (forced.add(new ChunkPos(cx, cz)))
                        {
                            level.setChunkForced(cx, cz, true);
                        }
                    }
                }
            }
        }
        helper.runAfterDelay(40, () ->
        {
            List<KnightRuinShowcase.Plan> plans = new ArrayList<>();
            int total = 0;
            try
            {
                for (int e = 0; e < elements.length; e++)
                {
                    for (int run = 1; run <= 2; run++)
                    {
                        KnightRuinShowcase.Plan plan = KnightRuinShowcase.buildNow(level, origins.get(e), Direction.SOUTH,
                                elements[e]);
                        if (run == 1)
                        {
                            plans.add(plan);
                        }
                        check(level, plan, run);
                        if (run == 2)
                        {
                            total += plan.plots().size();
                        }
                    }
                }
                if (total != KnightRuinDesigns.ALL.size())
                {
                    throw new GameTestAssertException("built " + total + " ruins, the table has "
                            + KnightRuinDesigns.ALL.size());
                }
                LOGGER.info("[knight ruin test] All {} ruins built with their broken frames and their two knights each;"
                        + " running each element again replaced them", total);
            }
            finally
            {
                for (KnightRuinShowcase.Plan plan : plans)
                {
                    for (KnightRuinShowcase.Plot plot : plan.plots())
                    {
                        level.getEntitiesOfClass(ElementalKnight.class, AABB.of(KnightRuinShowcase.footprint(plot.ground(),
                                plan.front(), plot.ruin())).inflate(4)).forEach(Entity::discard);
                    }
                }
                forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
            }
            helper.succeed();
        });
    }

    private static void check(ServerLevel level, KnightRuinShowcase.Plan plan, int run)
    {
        long expected = KnightRuinDesigns.ALL.stream().filter(d -> d.element() == plan.element()).count();
        if (plan.plots().size() != expected)
        {
            throw new GameTestAssertException("expected " + expected + " " + plan.element() + " ruins, planned "
                    + plan.plots().size());
        }
        for (KnightRuinShowcase.Plot plot : plan.plots())
        {
            KnightRuinDesigns.Design ruin = plot.ruin();
            BoundingBox box = KnightRuinShowcase.footprint(plot.ground(), plan.front(), ruin);
            int frame = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = box.minX(); x <= box.maxX(); x++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    for (int yy = box.minY(); yy <= box.maxY(); yy++)
                    {
                        if (level.getBlockState(pos.set(x, yy, z)).is(ModBlocks.DROSS_PORTAL_FRAME.get()))
                        {
                            frame++;
                        }
                    }
                }
            }
            if (frame < 6)
            {
                throw new GameTestAssertException("run " + run + ", ruin " + ruin.id() + " has only " + frame
                        + " frame blocks");
            }
            List<ElementalKnight> knights = level.getEntitiesOfClass(ElementalKnight.class, AABB.of(box).inflate(1));
            if (knights.size() != 2)
            {
                throw new GameTestAssertException("run " + run + ", ruin " + ruin.id() + " has " + knights.size()
                        + " knights, not 2");
            }
            for (ElementalKnight knight : knights)
            {
                if (knight.kind().element != ruin.element() || !knight.isShowcase())
                {
                    throw new GameTestAssertException("ruin " + ruin.id() + " has a " + knight.kind() + " (on show "
                            + knight.isShowcase() + ")");
                }
                BlockPos feet = knight.blockPosition();
                if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty())
                {
                    throw new GameTestAssertException("ruin " + ruin.id() + "'s " + knight.kind() + " stands on nothing at "
                            + feet.toShortString());
                }
                if (!level.noCollision(knight, knight.getBoundingBox().deflate(0.05D)))
                {
                    throw new GameTestAssertException("ruin " + ruin.id() + "'s " + knight.kind()
                            + " is inside a block at " + feet.toShortString());
                }
            }
        }
    }
}
