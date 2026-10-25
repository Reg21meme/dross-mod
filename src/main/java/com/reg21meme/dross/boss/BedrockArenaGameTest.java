package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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

import java.util.List;

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.FLOOR_Y;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.waitForArena;

/**
 * The bedrock test arena ({@code /dross boss arena bedrock}): its floor is bedrock and glowstone, its walls stand all
 * round, the air over it is clear, and no mob can spawn on its floor or its walls.
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
        int middle = SIZE / 2;
        BlockPos floor = new BlockPos(middle, FLOOR_Y, middle);
        BedrockArena.build(helper.getLevel(), helper.absolutePos(floor));
        List<EntityType<?>> kinds = List.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.COW,
                ModEntities.STONE_GOLEM.get(), ModEntities.POND_SKATER.get(), ModEntities.MOOSE.get());
        int lights = 0;
        for (int dx = -BedrockArena.RADIUS - 1; dx <= BedrockArena.RADIUS + 1; dx++)
        {
            for (int dz = -BedrockArena.RADIUS - 1; dz <= BedrockArena.RADIUS + 1; dz++)
            {
                BlockPos at = floor.offset(dx, 0, dz);
                boolean wall = BedrockArena.inWall(dx, dz);
                int top = wall ? BedrockArena.WALL_HEIGHT : 0;
                for (int dy = 0; dy <= top; dy++)
                {
                    BlockState state = helper.getBlockState(at.above(dy));
                    if (!state.is(Blocks.BEDROCK) && !state.is(Blocks.GLOWSTONE))
                    {
                        helper.fail("the arena should be bedrock and glowstone, but " + at.above(dy).toShortString()
                                + " is " + state.getBlock());
                    }
                    if (dy == 0 && !wall && state.is(Blocks.GLOWSTONE))
                    {
                        lights++;
                    }
                }
                BlockPos over = helper.absolutePos(at.above(top + 1));
                for (EntityType<?> kind : kinds)
                {
                    if (NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, helper.getLevel(), over, kind))
                    {
                        helper.fail("a " + EntityType.getKey(kind) + " could spawn on the arena at " + over.toShortString());
                    }
                }
                if (!wall)
                {
                    for (int dy = 1; dy <= BedrockArena.CLEAR_HEIGHT; dy++)
                    {
                        if (!helper.getBlockState(at.above(dy)).isAir())
                        {
                            helper.fail("the air over the arena should be clear, but " + at.above(dy).toShortString()
                                    + " isn't");
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
        if (!helper.getBlockState(new BlockPos(0, FLOOR_Y, 0)).is(Blocks.SMOOTH_STONE))
        {
            helper.fail("the arena shouldn't reach past its walls");
        }
        forceChunks(helper, false);
        helper.succeed();
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
