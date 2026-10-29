package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A probe, not a check (namespace {@code dross_volcano}: {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_volcano
 * -PgameTestWorld=<a new folder>}): builds the Emberdactyl's volcano far out in the Overworld and writes every block of it
 * you could see (a block with air, or something see-through, over it or beside it) to {@code run/probe/ember_volcano.txt},
 * one a line ("dx dy dz block", from the ground block under its middle), with its egg's and hovering adult's spots and its
 * path. {@code tools/fire/volcano_preview.py} draws the previews from it.
 */
@GameTestHolder(EmberVolcanoProbeGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = "dross", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmberVolcanoProbeGameTest
{
    static final String NAMESPACE = "dross_volcano";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EMPTY = "empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(NAMESPACE, EMPTY);
    private static final int PROBE_X = 9000;
    private static final int PROBE_Z = 9000;
    /** The flat plain reaches this far past the volcano's plan, this deep. */
    private static final int FLAT_MARGIN = 12;
    private static final int FLAT_DEPTH = 8;

    private EmberVolcanoProbeGameTest() {}

    @GameTest(template = EMPTY, batch = "dross_volcano_preview", timeoutTicks = 6000)
    public static void volcanoPreview(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        level.getChunkAt(new BlockPos(PROBE_X, 0, PROBE_Z));
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, PROBE_X, PROBE_Z) - 1;
        EmberVolcano.Plan plan = EmberVolcano.plan(new BlockPos(PROBE_X, y, PROBE_Z), Direction.NORTH,
                EmberVolcanoTuning.SHOWCASE_SEED);
        flatten(level, plan);
        long start = System.nanoTime();
        plan.buildNow(level);
        LOGGER.info("[volcano probe] built in {} ms", (System.nanoTime() - start) / 1_000_000L);
        try
        {
            dump(level, plan, Path.of("probe", "ember_volcano.txt"));
        }
        catch (IOException e)
        {
            throw new GameTestAssertException("couldn't write the probe: " + e);
        }
        helper.succeed();
    }

    /**
     * The volcano nearest the hub on the planner, built on the real Dross land where it stands (no flattening), written to
     * {@code run/probe/ember_volcano_land.txt}: {@code python tools/fire/volcano_preview.py run/probe/ember_volcano_land.txt
     * volcano_land} draws it.
     */
    @GameTest(template = EMPTY, batch = "dross_volcano_land", timeoutTicks = 20000)
    public static void volcanoOnLand(GameTestHelper helper)
    {
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = server.getLevel(com.reg21meme.dross.dimension.ModDimensions.DROSS_LEVEL);
        com.reg21meme.dross.dimension.sites.Site site = com.reg21meme.dross.dimension.sites.BossSites.forLevel(dross)
                .nearest(com.reg21meme.dross.dimension.sites.BossSites.EMBER_VOLCANO, BlockPos.ZERO, 100_000)
                .orElseThrow(() -> new GameTestAssertException("no volcano site planned"));
        EmberVolcano.Plan plan = EmberVolcanoes.replan(dross, site);
        BoundingBox box = plan.box();
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
        {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
            {
                dross.getChunk(cx, cz);
            }
        }
        long start = System.nanoTime();
        plan.buildNow(dross);
        LOGGER.info("[volcano probe] built the volcano of patch {} at {} on the land in {} ms ({} lava)", site.patch().number(),
                plan.ground().toShortString(), (System.nanoTime() - start) / 1_000_000L, plan.lavaBlocks());
        try
        {
            dump(dross, plan, Path.of("probe", "ember_volcano_land.txt"));
        }
        catch (IOException e)
        {
            throw new GameTestAssertException("couldn't write the probe: " + e);
        }
        helper.succeed();
    }

    /** Makes the probe's spot a flat plain of grass first (like the superflat world the showcase is meant for), a little
     *  past everything the volcano may change, so the previews show the volcano and not the hills it was cut into. */
    private static void flatten(ServerLevel level, EmberVolcano.Plan plan)
    {
        BoundingBox box = plan.box();
        int groundY = plan.ground().getY();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - FLAT_MARGIN; x <= box.maxX() + FLAT_MARGIN; x++)
        {
            for (int z = box.minZ() - FLAT_MARGIN; z <= box.maxZ() + FLAT_MARGIN; z++)
            {
                for (int y = groundY - FLAT_DEPTH; y <= box.maxY(); y++)
                {
                    BlockState want = y < groundY ? dirt : y == groundY ? grass : air;
                    if (level.getBlockState(pos.set(x, y, z)) != want)
                    {
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    private static void dump(ServerLevel level, EmberVolcano.Plan plan, Path file) throws IOException
    {
        BoundingBox box = plan.box();
        BlockPos ground = plan.ground();
        List<String> lines = new ArrayList<>();
        Vec3 egg = plan.eggFeet();
        Vec3 hover = egg.add(0.0D, EmberVolcanoTuning.NEST_HEIGHT * 2.0D, 0.0D);
        lines.add(String.format(java.util.Locale.ROOT, "# egg %.2f %.2f %.2f", egg.x - ground.getX(), egg.y - ground.getY(),
                egg.z - ground.getZ()));
        lines.add(String.format(java.util.Locale.ROOT, "# hover %.2f %.2f %.2f", hover.x - ground.getX(), hover.y - ground.getY(),
                hover.z - ground.getZ()));
        for (BlockPos step : plan.pathLine())
        {
            lines.add("# path " + (step.getX() - ground.getX()) + " " + (step.getY() - ground.getY()) + " " + (step.getZ() - ground.getZ()));
        }
        lines.add("# lava " + plan.lavaBlocks());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        Direction[] sides = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int yy = box.minY(); yy <= box.maxY(); yy++)
                {
                    BlockState state = level.getBlockState(pos.set(x, yy, z));
                    if (state.isAir())
                    {
                        continue;
                    }
                    boolean seen = false;
                    for (Direction side : sides)
                    {
                        BlockState beside = level.getBlockState(next.setWithOffset(pos, side));
                        if (beside.isAir() || beside.getFluidState().isEmpty() && !beside.isCollisionShapeFullBlock(level, next))
                        {
                            seen = true;
                            break;
                        }
                    }
                    if (seen)
                    {
                        lines.add((x - ground.getX()) + " " + (yy - ground.getY()) + " " + (z - ground.getZ()) + " "
                                + ForgeRegistries.BLOCKS.getKey(state.getBlock()));
                    }
                }
            }
        }
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
        LOGGER.info("[volcano probe] wrote {} lines to {}", lines.size(), file.toAbsolutePath());
    }

    /** The probe's empty template (a block of air), made when the test server starts (like the Huntsman's probes'). */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(EMPTY_ID);
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag ints(int... values)
    {
        ListTag list = new ListTag();
        for (int v : values)
        {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
