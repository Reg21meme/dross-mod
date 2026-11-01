package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.emberdactyl.EmberBoss;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.FLOOR_Y;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.waitForArena;

/**
 * The bedrock test arena ({@code /dross boss arena bedrock 3}): its floor is bedrock and glowstone (and each section's
 * patch of its team's glass), its walls stand all round up to its roof, the walls between its three sections stand
 * where they should and open and close, the air inside is clear, no mob can spawn on its floor, its walls or its roof,
 * and under its roof the Emberdactyl finds the floor. For 1 to 5 teams the sections are as near the same width as
 * whole blocks allow.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BedrockArenaGameTest
{
    private static final String TEMPLATE = "bedrock_test_arena";
    private static final ResourceLocation TEMPLATE_ID = new ResourceLocation(Dross.MODID, TEMPLATE);
    /** The arena and its walls, and a block's margin. */
    private static final int SIZE = BedrockArena.SIZE + 4;

    private BedrockArenaGameTest() {}

    @GameTest(template = TEMPLATE, batch = "dross_bedrock_arena", timeoutTicks = ARENA_WAIT_TICKS + 40)
    public static void nothingSpawnsInTheBedrockArena(GameTestHelper helper)
    {
        // The test framework keeps only 5 x 5 chunks round a test loaded, and the arena reaches past them.
        forceChunks(helper, true);
        if (waitForArena(helper, BedrockArenaGameTest::nothingSpawnsInTheBedrockArena, SIZE, SIZE))
        {
            return;
        }
        checkSections(helper);
        int middle = SIZE / 2;
        int teams = 3;
        BlockPos floor = new BlockPos(middle, FLOOR_Y, middle);
        BlockPos absFloor = helper.absolutePos(floor);
        ServerLevel level = helper.getLevel();
        BedrockArena.build(level, absFloor, teams);
        List<BedrockArena.Section> sections = BedrockArena.sections(teams);
        List<Integer> dividers = BedrockArena.dividers(sections);
        List<EntityType<?>> kinds = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.COW,
                ModEntities.STONE_GOLEM.get(), ModEntities.POND_SKATER.get(), ModEntities.MOOSE.get());
        int lights = 0;
        int glass = 0;
        for (int dx = -BedrockArena.RADIUS - 1; dx <= BedrockArena.RADIUS + 1; dx++)
        {
            for (int dz = -BedrockArena.RADIUS - 1; dz <= BedrockArena.RADIUS + 1; dz++)
            {
                BlockPos at = floor.offset(dx, 0, dz);
                boolean wall = BedrockArena.inWall(dx, dz);
                boolean divider = !wall && dividers.contains(dx);
                for (int dy = 0; dy <= BedrockArena.ROOF_HEIGHT; dy++)
                {
                    BlockState state = helper.getBlockState(at.above(dy));
                    boolean solid = dy == 0 || dy == BedrockArena.ROOF_HEIGHT || wall || divider && dy <= BedrockArena.DIVIDER_HEIGHT;
                    boolean marker = dy == 0 && !wall && state.getBlock().defaultBlockState() == state
                            && sections.stream().anyMatch(s -> state.is(BedrockArena.glass(s.team()).getBlock()));
                    if (solid && !state.is(Blocks.BEDROCK) && !state.is(Blocks.GLOWSTONE) && !marker)
                    {
                        helper.fail("the arena should be bedrock and glowstone (and its teams' glass in the floor), but "
                                + at.above(dy).toShortString() + " is " + state.getBlock());
                    }
                    // (a block with a block entity is left alone by the arena on purpose, like a chest or a cave's monster
                    // spawner where the test world put the arena: twice a fresh world's dungeon was there, October 31)
                    if (!solid && !state.isAir() && !state.hasBlockEntity())
                    {
                        helper.fail("the air in the arena should be clear, but " + at.above(dy).toShortString() + " is "
                                + state.getBlock());
                    }
                    lights += dy == 0 && !wall && state.is(Blocks.GLOWSTONE) ? 1 : 0;
                    glass += marker ? 1 : 0;
                }
                // nothing spawns on its floor, on the walls between its sections, or on its roof
                List<BlockPos> tops = new ArrayList<>(List.of(at.above(BedrockArena.ROOF_HEIGHT + 1)));
                if (!wall)
                {
                    tops.add(at.above(divider ? BedrockArena.DIVIDER_HEIGHT + 1 : 1));
                }
                for (BlockPos top : tops)
                {
                    for (EntityType<?> kind : kinds)
                    {
                        if (NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, level, helper.absolutePos(top), kind))
                        {
                            helper.fail("a " + EntityType.getKey(kind) + " could spawn on the arena at " + top.toShortString());
                        }
                    }
                }
            }
        }
        if (lights < 64)
        {
            helper.fail("the floor should have glowstone set in it every " + BedrockArena.FLOOR_LIGHT_EVERY
                    + " blocks, but has only " + lights);
        }
        int patch = (2 * BedrockArena.MARKER_RADIUS + 1) * (2 * BedrockArena.MARKER_RADIUS + 1);
        if (glass != teams * patch)
        {
            helper.fail("each of the " + teams + " sections should have a " + patch + "-block patch of its team's glass, but "
                    + glass + " blocks are glass");
        }
        if (!helper.getBlockState(new BlockPos(0, FLOOR_Y, 0)).is(Blocks.SMOOTH_STONE))
        {
            helper.fail("the arena shouldn't reach past its walls");
        }
        // the Emberdactyl's ground under the roof is the floor (not the roof's top)
        int s0 = sections.get(0).middle();
        double under = EmberBoss.groundUnder(level, absFloor.getX() + s0 + 0.5D, absFloor.getY() + 20.0D, absFloor.getZ() + 0.5D);
        if (under != absFloor.getY() + 1)
        {
            helper.fail("under the roof the Emberdactyl's ground should be the floor (" + (absFloor.getY() + 1) + ", got " + under
                    + ")");
        }
        // the walls between the sections open, and close again
        BedrockArena.setDividers(level, absFloor, teams, true);
        for (int dx : dividers)
        {
            for (int dy = 1; dy <= BedrockArena.DIVIDER_HEIGHT; dy++)
            {
                if (!helper.getBlockState(floor.offset(dx, dy, 7)).isAir())
                {
                    helper.fail("opened, the wall between the sections should be gone, but " + floor.offset(dx, dy, 7).toShortString()
                            + " is " + helper.getBlockState(floor.offset(dx, dy, 7)).getBlock());
                }
            }
        }
        BedrockArena.setDividers(level, absFloor, teams, false);
        if (!helper.getBlockState(floor.offset(dividers.get(0), BedrockArena.DIVIDER_HEIGHT, 7)).is(Blocks.BEDROCK))
        {
            helper.fail("closed, the wall between the sections should stand again");
        }
        forceChunks(helper, false);
        helper.succeed();
    }

    /** For 1 to 5 teams: the sections fill the floor with a wall between each two, none more than a block wider than another. */
    private static void checkSections(GameTestHelper helper)
    {
        for (int teams = 1; teams <= BedrockArena.MAX_TEAMS; teams++)
        {
            List<BedrockArena.Section> sections = BedrockArena.sections(teams);
            int min = sections.stream().mapToInt(BedrockArena.Section::width).min().orElse(0);
            int max = sections.stream().mapToInt(BedrockArena.Section::width).max().orElse(0);
            int total = sections.stream().mapToInt(BedrockArena.Section::width).sum() + BedrockArena.dividers(sections).size();
            if (sections.size() != teams || max - min > 1 || total != BedrockArena.SIZE
                    || sections.get(0).from() != -BedrockArena.RADIUS || sections.get(teams - 1).to() != BedrockArena.RADIUS)
            {
                helper.fail(teams + " teams should make " + teams + " sections of near the same width filling the floor, but "
                        + "they're " + sections);
            }
        }
    }

    /** Keeps every chunk the test's template covers loaded (or lets them go). */
    private static void forceChunks(GameTestHelper helper, boolean forced)
    {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        BlockPos opposite = helper.absolutePos(new BlockPos(SIZE - 1, 0, SIZE - 1));
        for (int x = SectionPos.blockToSectionCoord(Math.min(corner.getX(), opposite.getX()));
             x <= SectionPos.blockToSectionCoord(Math.max(corner.getX(), opposite.getX())); x++)
        {
            for (int z = SectionPos.blockToSectionCoord(Math.min(corner.getZ(), opposite.getZ()));
                 z <= SectionPos.blockToSectionCoord(Math.max(corner.getZ(), opposite.getZ())); z++)
            {
                helper.getLevel().setChunkForced(x, z, forced);
            }
        }
    }

    /** The wide floor for the test, made in memory when a development server starts. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(TEMPLATE_ID);
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
                ColossusGameTest.floorTemplate(SIZE, SIZE));
    }
}
