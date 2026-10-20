package com.reg21meme.dross.command;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.village.HunterIglooDesigns;
import com.reg21meme.dross.boss.huntsman.village.HardyCropBlock;
import com.reg21meme.dross.boss.huntsman.village.HunterIgloos;
import com.reg21meme.dross.boss.huntsman.village.RimeberryBushBlock;
import com.reg21meme.dross.boss.huntsman.village.VillageAnimals;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Headless checks of the hunter igloo showcase ({@code /dross showcase hunter_igloos}, the boss area's
 * {@link HunterIglooDesigns}): every igloo's and decoration's template is in the mod at its table's size; the whole
 * showcase, built in the world, stands with every bed whole, every chest able to open (nothing solid over its lid),
 * nothing standing on a bottom slab (the user: "anything over a slab ... floats"), no clear ice or snow layer lit
 * enough to melt, and every igloo's darkest floor cell lit (nothing can spawn in it); every igloo has its guards (armor
 * stands in a skull, dyed leather and a stone sword, as many as its table says) and all its crops (none popped off when
 * the template went down).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HunterIglooShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Where the showcase is built: far from spawn and the other tests, in the air (no terrain or water). */
    private static final BlockPos TRY_AT = new BlockPos(-46000, 200, 41000);
    private static final int LIGHT_TICKS = 100;
    private static final int LIGHT_RETRY_TICKS = 200;
    private static final int LIGHT_RETRIES = 3;
    private static final int ICE_MELTS_ABOVE = 10;
    private static final int GUARD_RETRY_TICKS = 20;
    private static final int GUARD_RETRIES = 30;
    private static final int SNOW_MELTS_ABOVE = 11;

    private HunterIglooShowcaseGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_igloos_table")
    public static void iglooTemplatesMatchTheirTable(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        if (HunterIglooDesigns.IGLOOS.size() < 15 || HunterIglooDesigns.DECORATIONS.size() < 40)
        {
            throw new GameTestAssertException("there should be at least 15 igloos and 40 decorations, but there are "
                    + HunterIglooDesigns.IGLOOS.size() + " and " + HunterIglooDesigns.DECORATIONS.size());
        }
        List<HunterIgloos.Piece> pieces = new ArrayList<>();
        HunterIglooDesigns.IGLOOS.forEach(i -> pieces.add(HunterIgloos.Piece.of(i)));
        HunterIglooDesigns.DECORATIONS.forEach(d -> pieces.add(HunterIgloos.Piece.of(d)));
        for (HunterIgloos.Piece piece : pieces)
        {
            Optional<StructureTemplate> template = HunterIgloos.template(level, piece);
            if (template.isEmpty())
            {
                throw new GameTestAssertException("the template " + piece.template() + " is missing");
            }
            Vec3i size = template.get().getSize();
            if (size.getX() != piece.width() || size.getY() != piece.layers() || size.getZ() != piece.depth())
            {
                throw new GameTestAssertException(piece.template() + " is " + size + ", its table says " + piece.width()
                        + " x " + piece.layers() + " x " + piece.depth());
            }
        }
        helper.succeed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_igloos_built",
            timeoutTicks = LIGHT_TICKS + (LIGHT_RETRIES + 1) * LIGHT_RETRY_TICKS + GUARD_RETRIES * GUARD_RETRY_TICKS + 400)
    public static void igloosStandAndNeverMelt(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        HunterIglooShowcase.Plan plan = HunterIglooShowcase.plan(level, TRY_AT, Direction.NORTH);
        List<ChunkPos> forced = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (HunterIglooShowcase.Plot plot : plan.plots())
        {
            BoundingBox box = HunterIgloos.footprint(plot.ground(), plan.front(), plot.piece()).inflatedBy(8);
            for (int x = box.minX(); x <= box.maxX() + 15; x += 16)
            {
                for (int z = box.minZ(); z <= box.maxZ() + 15; z += 16)
                {
                    ChunkPos c = new ChunkPos(new BlockPos(Math.min(x, box.maxX()), 0, Math.min(z, box.maxZ())));
                    if (seen.add(c.toLong()))
                    {
                        level.setChunkForced(c.x, c.z, true);
                        level.getChunk(c.x, c.z);
                        forced.add(c);
                    }
                }
            }
        }
        for (HunterIglooShowcase.Plot plot : plan.plots())
        {
            HunterIglooShowcase.build(level, plan, plot);
        }
        helper.runAfterDelay(LIGHT_TICKS, () ->
        {
            int checked = 0;
            for (HunterIglooShowcase.Plot plot : plan.plots())
            {
                checked += check(level, plan, plot);
            }
            int blocks = checked;
            whenLit(helper, level, plan, LIGHT_RETRIES, () -> whenGuardsIn(helper, level, GUARD_RETRIES, () ->
            {
                int guards = 0;
                int crops = 0;
                for (HunterIglooShowcase.Plot plot : plan.plots())
                {
                    int[] found = guardsAndCrops(level, plan, plot);
                    guards += found[0];
                    crops += found[1];
                }
                forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
                LOGGER.info("[hunter igloo test] {} igloos and {} decorations stand: {} blocks looked at, beds whole, "
                                + "chests open, nothing on a slab, nothing melting, every igloo's floor lit, {} guards, "
                                + "{} crops", HunterIglooDesigns.IGLOOS.size(), HunterIglooDesigns.DECORATIONS.size(),
                        blocks, guards, crops);
                helper.succeed();
            }));
        });
    }

    /** Runs {@code then} once every igloo's darkest floor cell reads lit, looking again {@code tries} more times if not. */
    private static void whenLit(GameTestHelper helper, ServerLevel level, HunterIglooShowcase.Plan plan, int tries, Runnable then)
    {
        String dark = null;
        for (HunterIglooShowcase.Plot plot : plan.plots())
        {
            if (plot.igloo() == null)
            {
                continue;
            }
            BlockPos at = HunterIgloos.toWorld(plot.igloo().probe(), plot.ground(), plan.front(), plot.piece());
            int light = level.getBrightness(LightLayer.BLOCK, at);
            if (light < 1)
            {
                dark = plot.igloo().id() + ": its floor at " + at.toShortString() + " has block light " + light
                        + ": something could spawn there";
                break;
            }
        }
        if (dark == null)
        {
            then.run();
            return;
        }
        if (tries <= 0)
        {
            throw new GameTestAssertException(dark);
        }
        helper.runAfterDelay(LIGHT_RETRY_TICKS, () -> whenLit(helper, level, plan, tries - 1, then));
    }

    /** Runs {@code then} once every template's entities (the guards) are in, looking again {@code tries} more times. */
    private static void whenGuardsIn(GameTestHelper helper, ServerLevel level, int tries, Runnable then)
    {
        if (VillageAnimals.waiting(level) == 0)
        {
            then.run();
            return;
        }
        if (tries <= 0)
        {
            throw new GameTestAssertException("the igloos' armor stands never went in: " + VillageAnimals.describeWaiting(level));
        }
        helper.runAfterDelay(GUARD_RETRY_TICKS, () -> whenGuardsIn(helper, level, tries - 1, then));
    }

    /** One igloo's guards (armor stands) and crops, as many as its table says, the guards in their outfit. */
    private static int[] guardsAndCrops(ServerLevel level, HunterIglooShowcase.Plan plan, HunterIglooShowcase.Plot plot)
    {
        if (plot.igloo() == null)
        {
            return new int[] {0, 0};
        }
        HunterIglooDesigns.Igloo igloo = plot.igloo();
        BoundingBox box = HunterIgloos.footprint(plot.ground(), plan.front(), plot.piece());
        AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        List<ArmorStand> stands = level.getEntitiesOfClass(ArmorStand.class, area);
        if (stands.size() != igloo.stands())
        {
            throw new GameTestAssertException(igloo.id() + " should have " + igloo.stands() + " armor stand(s), but has "
                    + stands.size());
        }
        for (ArmorStand stand : stands)
        {
            ItemStack chest = stand.getItemBySlot(EquipmentSlot.CHEST);
            if (!stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.SKELETON_SKULL) || !chest.is(Items.LEATHER_CHESTPLATE)
                    || !(chest.getItem() instanceof DyeableLeatherItem dyed && dyed.hasCustomColor(chest))
                    || !stand.getItemBySlot(EquipmentSlot.LEGS).is(Items.LEATHER_LEGGINGS)
                    || !stand.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS)
                    || !stand.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.STONE_SWORD))
            {
                throw new GameTestAssertException(igloo.id() + ": the guard at " + stand.blockPosition().toShortString()
                        + " isn't in its skull, dyed leather and stone sword");
            }
        }
        int crops = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int y = box.minY(); y <= box.maxY(); y++)
                {
                    Block block = level.getBlockState(pos.set(x, y, z)).getBlock();
                    if (block instanceof HardyCropBlock || block instanceof RimeberryBushBlock)
                    {
                        crops++;
                    }
                }
            }
        }
        if (crops != igloo.crops())
        {
            throw new GameTestAssertException(igloo.id() + " should have " + igloo.crops() + " crop blocks, but has "
                    + crops + " (some popped off?)");
        }
        return new int[] {stands.size(), crops};
    }

    /** Looks at one plot's blocks; returns how many it looked at. */
    private static int check(ServerLevel level, HunterIglooShowcase.Plan plan, HunterIglooShowcase.Plot plot)
    {
        BoundingBox box = HunterIgloos.footprint(plot.ground(), plan.front(), plot.piece());
        String what = plot.igloo() != null ? plot.igloo().id() : "decoration " + plot.number() + " (" + plot.name() + ")";
        int n = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int y = plan.floorY() + 1; y <= box.maxY(); y++)
                {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir())
                    {
                        continue;
                    }
                    n++;
                    BlockState below = level.getBlockState(pos.below());
                    if (below.getBlock() instanceof SlabBlock && below.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                            && !state.is(Blocks.LIGHT))
                    {
                        throw new GameTestAssertException(what + ": the " + state.getBlock().getName().getString() + " at "
                                + pos.toShortString() + " stands on a bottom slab (it floats)");
                    }
                    if (state.getBlock() instanceof ChestBlock)
                    {
                        BlockPos above = pos.above();
                        if (level.getBlockState(above).isRedstoneConductor(level, above))
                        {
                            throw new GameTestAssertException(what + ": the chest at " + pos.toShortString()
                                    + " has a block over it and can't open");
                        }
                    }
                    if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD)
                    {
                        BlockState foot = level.getBlockState(pos.relative(state.getValue(BedBlock.FACING).getOpposite()));
                        if (!(foot.getBlock() instanceof BedBlock) || foot.getValue(BedBlock.PART) != BedPart.FOOT)
                        {
                            throw new GameTestAssertException(what + ": the bed at " + pos.toShortString() + " has no foot");
                        }
                    }
                    if (state.is(Blocks.ICE))
                    {
                        int light = level.getBrightness(LightLayer.BLOCK, pos);
                        if (light > ICE_MELTS_ABOVE)
                        {
                            throw new GameTestAssertException(what + ": the clear ice at " + pos.toShortString()
                                    + " has block light " + light + " and would melt");
                        }
                    }
                    if (state.is(Blocks.SNOW))
                    {
                        int light = level.getBrightness(LightLayer.BLOCK, pos);
                        if (light > SNOW_MELTS_ABOVE)
                        {
                            throw new GameTestAssertException(what + ": the snow layer at " + pos.toShortString()
                                    + " has block light " + light + " and would melt");
                        }
                    }
                }
            }
        }
        if (plot.igloo() != null && n < 100)
        {
            throw new GameTestAssertException(what + " has only " + n + " blocks: it wasn't built");
        }
        if (plot.igloo() == null && n == 0)
        {
            throw new GameTestAssertException(what + " wasn't built");
        }
        return n;
    }
}
