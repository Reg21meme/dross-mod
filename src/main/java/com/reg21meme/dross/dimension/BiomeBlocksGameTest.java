package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.block.SporepodBlock;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless tests (GameTests) for the other biomes' blocks (Cinder Wastes, Blightwood, Rimefall, Nether Scar and the
 * Gravecrags; Stage 1's blocks are in {@link TerrainBlocksGameTest}). Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}. They only exist in the development environment.
 *
 * <ul>
 *   <li>{@link #ashLayerNeverMelts}: Ash Layers stay beside bright light where vanilla snow melts.</li>
 *   <li>{@link #ashStacksDropsAndStays}: layers stack, drop one per layer, and Ash doesn't fall.</li>
 *   <li>{@link #icicleHangsAndBreaks}: Icicles hang only under a solid face, break when it goes, drop only with Silk
 *       Touch, and you walk through them.</li>
 *   <li>{@link #plantsKeepToTheirGround}: every biome plant refuses the wrong ground and grows on the right one, and
 *       one already growing pops off when its ground is swapped for a wrong one (even dirt).</li>
 *   <li>{@link #cinderAndScarBlocksNeverBurn}: nothing of the Cinder Wastes or the Nether Scar burns or feeds fire.</li>
 *   <li>{@link #glowingBlocksGiveLight}: every glowing block's light level, and the light really spreading.</li>
 *   <li>{@link #blightcapBlocksAreHugeMushrooms}: the giant blightcap's blocks, their drops, how they join, and the
 *       vanilla huge mushroom feature building a blightcap from them.</li>
 *   <li>{@link #gloomcapNeverSpreads}: random ticks and bone meal do nothing to a Gloomcap.</li>
 *   <li>{@link #sporepodBurstsAndRefills}: a pig walking into a Sporepod bursts it (harmlessly); it fills up again.</li>
 *   <li>{@link #stormLichenIsMultiface}: Storm Lichen hangs on cliff faces like glow lichen, shears only.</li>
 *   <li>{@link #groundCoversWork}: Sporeloam and Hoarfrost: tags, shovel, no hoe, bone meal; the fit tags.</li>
 *   <li>{@link #cragstoneIsStone}: Cragstone makes stone tools and furnaces and needs a pickaxe.</li>
 *   <li>{@link #biomeRecipesAndCompost}: the dyes, Ash Layers from Ash, and what composts.</li>
 * </ul>
 * Each test has its own batch.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BiomeBlocksGameTest
{
    // ---------------------------------------------------------------------------------------------
    // Test setup
    // ---------------------------------------------------------------------------------------------

    /** The arena: a Cragstone floor with air above, made in memory when the server starts. */
    static final String ARENA = "dross_terrain_biome_arena";
    private static final ResourceLocation ARENA_ID = new ResourceLocation(Dross.MODID, ARENA);
    /** Width and depth (blocks). */
    private static final int ARENA_SIZE = 16;
    /** Height (blocks, floor included): room for a doubled-height giant blightcap (up to 12 tall). */
    private static final int ARENA_HEIGHT = 16;
    /** The floor's height in the test's own coordinates (the arena sits one block above the structure block). */
    private static final int FLOOR = 1;
    /** Just above the floor. */
    private static final int GROUND = FLOOR + 1;
    /** A spot in the middle of the arena, just above the floor. */
    private static final BlockPos MIDDLE = new BlockPos(ARENA_SIZE / 2, GROUND, ARENA_SIZE / 2);

    /** How many times each random drop is rolled, and how many random ticks the "never" checks give. */
    private static final int ROLLS = 300;
    private static final int RANDOM_TICKS = 2000;
    /** Snow melts in its random tick when the block light is above this (vanilla SnowLayerBlock). */
    private static final int SNOW_MELT_LIGHT = 11;

    /** The light levels the blocks should give (see TerrainBlocks). */
    private static final int EMBERWEED_LIGHT = 4;
    private static final int CINDER_VENT_LIGHT = 3;
    private static final int GLOOMCAP_LIGHT = 6;
    private static final int FROSTBLOOM_LIGHT = 3;
    /** Scarred Netherrack glows (full-bright cracks) but gives no light: monsters still spawn on the Nether Scar's ground. */
    private static final int SCARRED_NETHERRACK_LIGHT = 0;
    private static final int SOULWICK_LIGHT = 10;
    private static final int SCAR_VENT_LIGHT = 4;
    private static final int STORM_LICHEN_LIGHT = 7;

    private BiomeBlocksGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Cinder Wastes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_ash_layer", timeoutTicks = TerrainBlocksGameTest.LIGHT_BACKSTOP_TICKS)
    public static void ashLayerNeverMelts(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos ash = MIDDLE;
        BlockPos snow = MIDDLE.east(4);
        BlockState ashState = ModBlocks.ASH_LAYER.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3);
        helper.setBlock(ash, ashState);
        helper.setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
        helper.setBlock(ash.north(), Blocks.GLOWSTONE);              // bright light right beside both
        helper.setBlock(snow.north(), Blocks.GLOWSTONE);
        if (ashState.isRandomlyTicking())
        {
            helper.fail("Ash Layer shouldn't take random ticks (that's how snow melts)");
        }
        // wait (in real time: the light engine works on its own thread) until the glowstone's light is there
        TerrainBlocksGameTest.succeedWhenLit(helper, () -> {
                    for (BlockPos pos : List.of(ash, snow))
                    {
                        int light = level.getBrightness(LightLayer.BLOCK, helper.absolutePos(pos));
                        if (light <= SNOW_MELT_LIGHT)
                        {
                            throw new GameTestAssertException("the light at " + pos + " is only " + light + " so far");
                        }
                    }
                }, () -> {
                    RandomSource random = RandomSource.create(7L);
                    for (BlockPos pos : List.of(ash, snow))
                    {
                        BlockPos abs = helper.absolutePos(pos);
                        for (int i = 0; i < RANDOM_TICKS && !level.getBlockState(abs).isAir(); i++)
                        {
                            level.getBlockState(abs).randomTick(level, abs, random);
                        }
                    }
                    BlockState after = helper.getBlockState(ash);
                    if (!after.is(ModBlocks.ASH_LAYER.get()) || after.getValue(SnowLayerBlock.LAYERS) != 3)
                    {
                        helper.fail("the Ash Layer melted or changed beside bright light: found " + after);
                    }
                    if (!helper.getBlockState(snow).isAir())
                    {
                        helper.fail("control: vanilla snow beside the same light should have melted (is the light too dim?)");
                    }
                });
    }

    @GameTest(template = ARENA, batch = "dross_terrain_ash_stacks", timeoutTicks = 100)
    public static void ashStacksDropsAndStays(GameTestHelper helper)
    {
        // one Ash Layer per layer, with any tool or none
        for (int layers = 1; layers <= SnowLayerBlock.MAX_HEIGHT; layers++)
        {
            BlockState state = ModBlocks.ASH_LAYER.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
            expectDrops(helper, state, ItemStack.EMPTY, ModItems.ASH_LAYER.get(), layers, layers);
        }
        expectDrops(helper, ModBlocks.ASH.get().defaultBlockState(), ItemStack.EMPTY, ModItems.ASH.get(), 1, 1);
        // using an Ash Layer on an Ash Layer stacks it one higher, like snow
        BlockPos layer = MIDDLE.west(3);
        helper.setBlock(layer, ModBlocks.ASH_LAYER.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2));
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ASH_LAYER.get(), 4));
        BlockPos abs = helper.absolutePos(layer);
        BlockHitResult hit = new BlockHitResult(Vec3.atBottomCenterOf(abs).add(0.0D, 4.0D / 16.0D, 0.0D), Direction.UP, abs, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        BlockState stacked = helper.getBlockState(layer);
        if (!stacked.is(ModBlocks.ASH_LAYER.get()) || stacked.getValue(SnowLayerBlock.LAYERS) != 3)
        {
            helper.fail("placing an Ash Layer on 2 layers should make 3, found " + stacked);
        }
        // Ash doesn't fall: one placed in mid-air is still there a second later
        if (ModBlocks.ASH.get() instanceof FallingBlock)
        {
            helper.fail("Ash shouldn't be a falling block");
        }
        BlockPos floating = MIDDLE.above(5);
        helper.setBlock(floating, ModBlocks.ASH.get());
        helper.runAfterDelay(20, () -> {
            if (!helper.getBlockState(floating).is(ModBlocks.ASH.get()))
            {
                helper.fail("Ash placed in mid-air fell or vanished");
            }
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "dross_terrain_never_burn")
    public static void cinderAndScarBlocksNeverBurn(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(MIDDLE);
        List<Block> neverBurn = List.of(ModBlocks.ASH.get(), ModBlocks.ASH_LAYER.get(), ModBlocks.CHARRED_LOG.get(),
                ModBlocks.EMBERWEED.get(), ModBlocks.CINDER_VENT.get(), ModBlocks.SCARRED_NETHERRACK.get(),
                ModBlocks.SCAR_ROOTS.get(), ModBlocks.SOULWICK.get(), ModBlocks.SCAR_VENT.get());
        for (Block block : neverBurn)
        {
            BlockState state = block.defaultBlockState();
            for (Direction side : Direction.values())
            {
                if (state.isFlammable(level, abs, side) || state.getFlammability(level, abs, side) > 0
                        || state.getFireSpreadSpeed(level, abs, side) > 0)
                {
                    helper.fail(name(block) + " should never burn, but it's flammable from its " + side + " side");
                }
            }
            if (state.ignitedByLava())
            {
                helper.fail(name(block) + " shouldn't be set alight by lava");
            }
            if (state.isFireSource(level, abs, Direction.UP))
            {
                helper.fail(name(block) + " shouldn't keep a fire burning on it (like netherrack does)");
            }
        }
        for (Block block : List.of(ModBlocks.SCARRED_NETHERRACK.get(), ModBlocks.SCAR_VENT.get()))
        {
            BlockState state = block.defaultBlockState();
            if (state.is(BlockTags.INFINIBURN_OVERWORLD) || state.is(BlockTags.INFINIBURN_NETHER)
                    || state.is(BlockTags.INFINIBURN_END) || state.is(BlockTags.NYLIUM))
            {
                helper.fail(name(block) + " shouldn't be in an infiniburn tag or #minecraft:nylium");
            }
        }
        // the other biomes' wood and flowers do burn (slowly for the frostbitten log)
        for (Block block : List.of(ModBlocks.FROSTBITTEN_LOG.get(), ModBlocks.FROSTBLOOM.get(), ModBlocks.CRAG_THISTLE.get(),
                ModBlocks.STORM_LICHEN.get()))
        {
            if (!block.defaultBlockState().isFlammable(level, abs, Direction.UP))
            {
                helper.fail(name(block) + " should be able to burn");
            }
        }
        int frostbitten = ModBlocks.FROSTBITTEN_LOG.get().defaultBlockState().getFlammability(level, abs, Direction.UP);
        int oak = Blocks.OAK_LOG.defaultBlockState().getFlammability(level, abs, Direction.UP);
        if (frostbitten >= oak)
        {
            helper.fail("the Frostbitten Log should burn more slowly than oak (" + frostbitten + " vs " + oak + ")");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Plants and their ground
    // ---------------------------------------------------------------------------------------------

    /** A plant, the ground it should grow on, and ground it must refuse. */
    private record Ground(Block plant, List<Block> right, List<Block> wrong) {}

    @GameTest(template = ARENA, batch = "dross_terrain_plant_ground")
    public static void plantsKeepToTheirGround(GameTestHelper helper)
    {
        List<Ground> cases = List.of(
                new Ground(ModBlocks.EMBERWEED.get(),
                        List.of(ModBlocks.ASH.get(), Blocks.BASALT, Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, ModBlocks.DUSKSTONE.get()),
                        List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, ModBlocks.SPORELOAM.get(), Blocks.SAND, ModBlocks.CRAGSTONE.get())),
                new Ground(ModBlocks.GLOOMCAP.get(),
                        List.of(Blocks.DIRT, ModBlocks.SPORELOAM.get(), Blocks.MYCELIUM, Blocks.PODZOL, ModBlocks.SILVERWOOD_LOG.get(),
                                ModBlocks.CHARRED_LOG.get()),
                        List.of(Blocks.STONE, ModBlocks.CRAGSTONE.get(), ModBlocks.ASH.get(), Blocks.SAND)),
                new Ground(ModBlocks.SPOREPOD.get(),
                        List.of(ModBlocks.SPORELOAM.get(), Blocks.DIRT, Blocks.MYCELIUM, Blocks.OAK_LOG),
                        List.of(Blocks.STONE, ModBlocks.ASH.get(), Blocks.NETHERRACK)),
                new Ground(ModBlocks.FROSTBLOOM.get(),
                        List.of(ModBlocks.HOARFROST.get(), Blocks.SNOW_BLOCK, Blocks.DIRT, Blocks.GRASS_BLOCK),
                        List.of(Blocks.STONE, ModBlocks.ASH.get(), Blocks.SAND, Blocks.ICE)),
                new Ground(ModBlocks.SCAR_ROOTS.get(),
                        List.of(Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.NETHERRACK, ModBlocks.SCARRED_NETHERRACK.get(),
                                Blocks.SOUL_SOIL),
                        List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.STONE, Blocks.SOUL_SAND)),
                new Ground(ModBlocks.SOULWICK.get(),
                        List.of(Blocks.SOUL_SOIL, Blocks.SOUL_SAND),
                        List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.NETHERRACK, ModBlocks.SCARRED_NETHERRACK.get())),
                new Ground(ModBlocks.CRAG_THISTLE.get(),
                        List.of(ModBlocks.CRAGSTONE.get(), Blocks.GRAVEL, ModBlocks.DUSKSTONE.get(), Blocks.SNOW_BLOCK, Blocks.DIRT),
                        List.of(Blocks.STONE, Blocks.SAND, ModBlocks.ASH.get(), Blocks.NETHERRACK)));
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++)
        {
            Ground c = cases.get(i);
            int x = 1 + 2 * i;
            int z = 0;
            // can it be placed? (a row of ground blocks, the plant checked above each)
            for (Block ground : c.right)
            {
                if (!survives(helper, c.plant, ground, new BlockPos(x, GROUND, z++)))
                {
                    problems.add(name(c.plant) + " should grow on " + name(ground));
                }
            }
            for (Block ground : c.wrong)
            {
                if (survives(helper, c.plant, ground, new BlockPos(x, GROUND, z++)))
                {
                    problems.add(name(c.plant) + " shouldn't grow on " + name(ground));
                }
            }
            // one already growing on its right ground pops off when the ground is swapped for each wrong one
            for (Block wrong : c.wrong)
            {
                BlockPos groundPos = new BlockPos(x, GROUND, z++);
                helper.setBlock(groundPos, c.right.get(0));
                helper.setBlock(groundPos.above(), c.plant);
                if (!helper.getBlockState(groundPos.above()).is(c.plant))
                {
                    problems.add(name(c.plant) + " didn't stay on " + name(c.right.get(0)));
                    continue;
                }
                level.setBlock(helper.absolutePos(groundPos), wrong.defaultBlockState(), Block.UPDATE_ALL);
                if (!helper.getBlockState(groundPos.above()).isAir())
                {
                    problems.add(name(c.plant) + " stayed when its ground turned into " + name(wrong));
                }
            }
        }
        if (!problems.isEmpty())
        {
            helper.fail(String.join("; ", problems));
        }
        helper.succeed();
    }

    /** Puts {@code ground} at pos and asks whether the plant could stand on it (in the air above). */
    private static boolean survives(GameTestHelper helper, Block plant, Block ground, BlockPos pos)
    {
        helper.setBlock(pos, ground);
        return plant.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(pos.above()));
    }

    @GameTest(template = ARENA, batch = "dross_terrain_gloomcap")
    public static void gloomcapNeverSpreads(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Block gloomcap = ModBlocks.GLOOMCAP.get();
        if (gloomcap instanceof MushroomBlock || gloomcap instanceof BonemealableBlock)
        {
            helper.fail("the Gloomcap shouldn't be a vanilla mushroom or take bone meal (it would spread or grow huge)");
        }
        // a patch of Sporeloam (good ground) with one Gloomcap in the middle
        for (int x = -3; x <= 3; x++)
        {
            for (int z = -3; z <= 3; z++)
            {
                helper.setBlock(MIDDLE.offset(x, 0, z), ModBlocks.SPORELOAM.get());
            }
        }
        BlockPos cap = MIDDLE.above();
        helper.setBlock(cap, gloomcap);
        BlockPos abs = helper.absolutePos(cap);
        BlockState state = helper.getBlockState(cap);
        if (state.isRandomlyTicking())
        {
            helper.fail("the Gloomcap shouldn't take random ticks");
        }
        RandomSource random = RandomSource.create(3L);
        for (int i = 0; i < RANDOM_TICKS; i++)
        {
            level.getBlockState(abs).randomTick(level, abs, random);
        }
        if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL, 64), level, abs, helper.makeMockPlayer()))
        {
            helper.fail("bone meal shouldn't do anything to a Gloomcap");
        }
        int count = countAround(helper, MIDDLE.above(), 4, gloomcap);
        if (count != 1 || !helper.getBlockState(cap).is(gloomcap))
        {
            helper.fail("the Gloomcap spread or grew: " + count + " Gloomcaps after " + RANDOM_TICKS + " random ticks and bone meal");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_terrain_sporepod", timeoutTicks = 200)
    public static void sporepodBurstsAndRefills(GameTestHelper helper)
    {
        BlockPos pod = MIDDLE.above();
        helper.setBlock(MIDDLE, ModBlocks.SPORELOAM.get());
        helper.setBlock(pod, ModBlocks.SPOREPOD.get());
        if (helper.getBlockState(pod).getValue(SporepodBlock.BURST))
        {
            helper.fail("a new Sporepod should be full");
        }
        Pig pig = helper.spawn(EntityType.PIG, pod);
        float health = pig.getHealth();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    BlockState state = helper.getBlockState(pod);
                    if (!state.is(ModBlocks.SPOREPOD.get()) || !state.getValue(SporepodBlock.BURST))
                    {
                        throw new GameTestAssertException("the pig walked into the Sporepod but it hasn't burst: " + state);
                    }
                })
                .thenExecute(() -> {
                    if (pig.getHealth() < health || pig.isOnFire())
                    {
                        helper.fail("bursting a Sporepod hurt the pig (it should be harmless)");
                    }
                    pig.discard();
                })
                .thenWaitUntil(() -> {
                    BlockState state = helper.getBlockState(pod);
                    if (!state.is(ModBlocks.SPOREPOD.get()) || state.getValue(SporepodBlock.BURST))
                    {
                        throw new GameTestAssertException("the Sporepod hasn't filled up again: " + state);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Rimefall
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_icicle")
    public static void icicleHangsAndBreaks(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Block icicle = ModBlocks.ICICLE.get();
        BlockPos roof = MIDDLE.above(4);
        BlockPos hanging = roof.below();
        helper.setBlock(roof, ModBlocks.CRAGSTONE.get());
        if (!icicle.defaultBlockState().canSurvive(level, helper.absolutePos(hanging)))
        {
            helper.fail("an Icicle should hang under Cragstone");
        }
        helper.setBlock(hanging, icicle);
        // under air, or under a fence (its bottom face isn't solid all over), it can't hang
        BlockPos fence = MIDDLE.offset(4, 4, 0);
        helper.setBlock(fence, Blocks.OAK_FENCE);
        if (icicle.defaultBlockState().canSurvive(level, helper.absolutePos(fence.below())))
        {
            helper.fail("an Icicle shouldn't hang under a fence");
        }
        if (icicle.defaultBlockState().canSurvive(level, helper.absolutePos(MIDDLE.offset(-4, 3, 0))))
        {
            helper.fail("an Icicle shouldn't hang under nothing");
        }
        if (!helper.getBlockState(hanging).getCollisionShape(level, helper.absolutePos(hanging)).isEmpty())
        {
            helper.fail("you should be able to walk through an Icicle");
        }
        // drops: nothing, or itself with Silk Touch
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        expectNoDrops(helper, icicle.defaultBlockState(), new ItemStack(Items.IRON_PICKAXE));
        expectDrops(helper, icicle.defaultBlockState(), silk, ModItems.ICICLE.get(), 1, 1);
        // take the roof away: the icicle breaks
        helper.setBlock(roof, Blocks.AIR);
        if (!helper.getBlockState(hanging).isAir())
        {
            helper.fail("the Icicle should break when the block above it goes, found " + helper.getBlockState(hanging));
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Light
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_light", timeoutTicks = TerrainBlocksGameTest.LIGHT_BACKSTOP_TICKS)
    public static void glowingBlocksGiveLight(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(MIDDLE);
        BlockState lichenWithFace = ModBlocks.STORM_LICHEN.get().defaultBlockState().setValue(PipeBlock.NORTH, true);
        expectLight(helper, ModBlocks.EMBERWEED.get().defaultBlockState(), EMBERWEED_LIGHT);
        expectLight(helper, ModBlocks.CINDER_VENT.get().defaultBlockState(), CINDER_VENT_LIGHT);
        expectLight(helper, ModBlocks.GLOOMCAP.get().defaultBlockState(), GLOOMCAP_LIGHT);
        expectLight(helper, ModBlocks.FROSTBLOOM.get().defaultBlockState(), FROSTBLOOM_LIGHT);
        expectLight(helper, ModBlocks.SCARRED_NETHERRACK.get().defaultBlockState(), SCARRED_NETHERRACK_LIGHT);
        expectLight(helper, ModBlocks.SOULWICK.get().defaultBlockState(), SOULWICK_LIGHT);
        expectLight(helper, ModBlocks.SCAR_VENT.get().defaultBlockState(), SCAR_VENT_LIGHT);
        expectLight(helper, lichenWithFace, STORM_LICHEN_LIGHT);
        if (ModBlocks.STORM_LICHEN.get().defaultBlockState().getLightEmission(level, abs) != 0)
        {
            helper.fail("Storm Lichen covering no side at all shouldn't give light (like glow lichen)");
        }
        // and the light really spreads: Storm Lichen on a Cragstone wall, a Soulwick on soul soil, a Gloomcap on Sporeloam
        BlockPos wall = MIDDLE.offset(-4, 0, -2);
        BlockPos lichen = wall.south();
        BlockPos soulwick = MIDDLE.offset(4, 0, 0).above();
        BlockPos gloomcap = MIDDLE.offset(0, 0, 4).above();
        helper.setBlock(wall, ModBlocks.CRAGSTONE.get());
        helper.setBlock(lichen, lichenWithFace);
        helper.setBlock(soulwick.below(), Blocks.SOUL_SOIL);
        helper.setBlock(soulwick, ModBlocks.SOULWICK.get());
        helper.setBlock(gloomcap.below(), ModBlocks.SPORELOAM.get());
        helper.setBlock(gloomcap, ModBlocks.GLOOMCAP.get());
        // the light spreads out over the next moments (real time: the light engine works on its own thread)
        TerrainBlocksGameTest.succeedWhenLit(helper, () -> {
            expectBlockLight(helper, lichen, STORM_LICHEN_LIGHT);
            expectBlockLight(helper, soulwick, SOULWICK_LIGHT);
            expectBlockLight(helper, gloomcap, GLOOMCAP_LIGHT);
        }, () -> {});
    }

    private static void expectLight(GameTestHelper helper, BlockState state, int light)
    {
        int actual = state.getLightEmission(helper.getLevel(), helper.absolutePos(MIDDLE));
        if (actual != light)
        {
            helper.fail(name(state.getBlock()) + " should give light level " + light + ", but gives " + actual);
        }
    }

    private static void expectBlockLight(GameTestHelper helper, BlockPos pos, int light)
    {
        int actual = helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(pos));
        if (actual < light)
        {
            throw new GameTestAssertException("the block light at " + name(helper.getBlockState(pos).getBlock()) + " is " + actual
                    + " (expected " + light + ")");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Blightwood: the giant blightcap
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_blightcap")
    public static void blightcapBlocksAreHugeMushrooms(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Block cap = ModBlocks.BLIGHTCAP_BLOCK.get();
        Block rotting = ModBlocks.ROTTING_BLIGHTCAP.get();
        Block stem = ModBlocks.BLIGHTCAP_STEM.get();
        for (Block block : List.of(cap, rotting, stem))
        {
            if (!(block instanceof HugeMushroomBlock))
            {
                helper.fail(name(block) + " should be a HugeMushroomBlock (the vanilla huge mushroom features need one)");
            }
        }
        // drops: the cap 0-2 Gloomcaps (mostly none), the rotting cap and stem nothing; Silk Touch: the block itself
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        ItemStack silk = new ItemStack(Items.IRON_AXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        int[] range = expectDrops(helper, cap.defaultBlockState(), axe, ModItems.GLOOMCAP.get(), 0, 2);
        if (range[1] == 0)
        {
            helper.fail("a Blightcap Block never dropped a Gloomcap in " + ROLLS + " tries");
        }
        expectNoDrops(helper, rotting.defaultBlockState(), axe);
        expectNoDrops(helper, stem.defaultBlockState(), axe);
        expectDrops(helper, cap.defaultBlockState(), silk, ModItems.BLIGHTCAP_BLOCK.get(), 1, 1);
        expectDrops(helper, rotting.defaultBlockState(), silk, ModItems.ROTTING_BLIGHTCAP.get(), 1, 1);
        expectDrops(helper, stem.defaultBlockState(), silk, ModItems.BLIGHTCAP_STEM.get(), 1, 1);
        // the two cap blocks count as one cap: the side between them turns to the inside; a stem doesn't join a cap
        BlockPos a = new BlockPos(2, GROUND, 2);
        helper.setBlock(a, cap);
        helper.setBlock(a.east(), rotting);
        helper.setBlock(a.above(), stem);
        if (helper.getBlockState(a).getValue(PipeBlock.EAST))
        {
            helper.fail("a Blightcap Block beside a Rotting Blightcap should show its inside towards it");
        }
        if (!helper.getBlockState(a).getValue(PipeBlock.UP))
        {
            helper.fail("a Blightcap Block shouldn't join the stem above it");
        }
        // the vanilla huge red mushroom feature builds a giant blightcap from them, setting their faces
        BlockPos origin = new BlockPos(ARENA_SIZE / 2, GROUND, ARENA_SIZE / 2 + 2);
        helper.setBlock(origin.below(), ModBlocks.SPORELOAM.get());
        ConfiguredFeature<?, ?> feature = new ConfiguredFeature<>(Feature.HUGE_RED_MUSHROOM, new HugeMushroomFeatureConfiguration(
                BlockStateProvider.simple(cap.defaultBlockState()),
                BlockStateProvider.simple(stem.defaultBlockState().setValue(PipeBlock.UP, false).setValue(PipeBlock.DOWN, false)), 2));
        if (!feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(11L), helper.absolutePos(origin)))
        {
            helper.fail("the vanilla huge mushroom feature didn't grow a giant blightcap on Sporeloam");
        }
        int caps = 0;
        int insides = 0;
        int stems = 0;
        for (int y = GROUND; y < FLOOR + ARENA_HEIGHT; y++)
        {
            for (int x = 0; x < ARENA_SIZE; x++)
            {
                for (int z = 0; z < ARENA_SIZE; z++)
                {
                    BlockState state = helper.getBlockState(new BlockPos(x, y, z));
                    if (state.is(cap) && z >= origin.getZ() - 3)
                    {
                        caps++;
                        if (!state.getValue(PipeBlock.NORTH) || !state.getValue(PipeBlock.SOUTH) || !state.getValue(PipeBlock.WEST)
                                || !state.getValue(PipeBlock.EAST) || !state.getValue(PipeBlock.UP))
                        {
                            insides++;
                        }
                    }
                    else if (state.is(stem) && x == origin.getX() && z == origin.getZ())
                    {
                        stems++;
                    }
                }
            }
        }
        if (caps < 10 || insides == 0 || stems < 3)
        {
            helper.fail("the giant blightcap looks wrong: " + caps + " cap blocks (" + insides + " with an inside face), "
                    + stems + " stem blocks");
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // The Gravecrags: Storm Lichen and Cragstone
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_storm_lichen")
    public static void stormLichenIsMultiface(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Block lichen = ModBlocks.STORM_LICHEN.get();
        if (!(lichen instanceof MultifaceBlock) || !(lichen instanceof GlowLichenBlock))
        {
            helper.fail("Storm Lichen should be a glow lichen (a MultifaceBlock), so the multiface_growth feature takes it");
        }
        // on a cliff: two Cragstone blocks in an inside corner, the lichen covering both sides
        BlockPos spot = MIDDLE;
        helper.setBlock(spot.north(), ModBlocks.CRAGSTONE.get());
        helper.setBlock(spot.west(), ModBlocks.CRAGSTONE.get());
        BlockState twoSides = lichen.defaultBlockState().setValue(PipeBlock.NORTH, true).setValue(PipeBlock.WEST, true);
        if (!twoSides.canSurvive(level, helper.absolutePos(spot)))
        {
            helper.fail("Storm Lichen should hang on Cragstone");
        }
        helper.setBlock(spot, twoSides);
        // shears: one per side it covers; anything else: nothing
        expectDrops(helper, twoSides, new ItemStack(Items.SHEARS), ModItems.STORM_LICHEN.get(), 2, 2);
        expectNoDrops(helper, twoSides, new ItemStack(Items.IRON_AXE));
        helper.setBlock(spot, twoSides);
        // take one wall away: it keeps the other side; take both: it's gone
        helper.setBlock(spot.west(), Blocks.AIR);
        BlockState after = helper.getBlockState(spot);
        if (!after.is(lichen) || after.getValue(PipeBlock.WEST) || !after.getValue(PipeBlock.NORTH))
        {
            helper.fail("Storm Lichen should lose only the side whose wall went, found " + after);
        }
        helper.setBlock(spot.north(), Blocks.AIR);
        if (!helper.getBlockState(spot).isAir())
        {
            helper.fail("Storm Lichen with nothing left to hang on should be gone, found " + helper.getBlockState(spot));
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_terrain_cragstone")
    public static void cragstoneIsStone(GameTestHelper helper)
    {
        Block cragstone = ModBlocks.CRAGSTONE.get();
        Item cragItem = ModItems.CRAGSTONE.get();
        if (!cragstone.defaultBlockState().is(BlockTags.BASE_STONE_OVERWORLD))
        {
            helper.fail("Cragstone should be in #minecraft:base_stone_overworld");
        }
        ItemStack stack = new ItemStack(cragItem);
        if (!stack.is(ItemTags.STONE_TOOL_MATERIALS) || !stack.is(ItemTags.STONE_CRAFTING_MATERIALS))
        {
            helper.fail("Cragstone should be a stone tool and crafting material (item tags)");
        }
        // a stone pickaxe and a furnace made with it
        expectCrafting(helper, new Item[] {cragItem, cragItem, cragItem, null, Items.STICK, null, null, Items.STICK, null}, 3,
                Items.STONE_PICKAXE, 1);
        expectCrafting(helper, new Item[] {cragItem, cragItem, cragItem, cragItem, null, cragItem, cragItem, cragItem, cragItem}, 3,
                Items.FURNACE, 1);
        // any pickaxe mines it (it drops itself); a bare hand gets nothing
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
        if (!player.hasCorrectToolForDrops(cragstone.defaultBlockState()))
        {
            helper.fail("a wooden pickaxe should mine Cragstone");
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (player.hasCorrectToolForDrops(cragstone.defaultBlockState()))
        {
            helper.fail("Cragstone shouldn't drop anything when mined by hand");
        }
        expectDrops(helper, cragstone.defaultBlockState(), new ItemStack(Items.WOODEN_PICKAXE), cragItem, 1, 1);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Ground covers, tags, recipes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_ground_covers")
    public static void groundCoversWork(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Block sporeloam = ModBlocks.SPORELOAM.get();
        Block hoarfrost = ModBlocks.HOARFROST.get();
        for (Block ground : List.of(sporeloam, hoarfrost))
        {
            if (!ground.defaultBlockState().is(BlockTags.DIRT))
            {
                helper.fail(name(ground) + " should be in #minecraft:dirt");
            }
        }
        if (!sporeloam.defaultBlockState().is(BlockTags.MUSHROOM_GROW_BLOCK))
        {
            helper.fail("Sporeloam should be in #minecraft:mushroom_grow_block");
        }
        // the ground kinds the hub and site fitting will look for
        TagKey<Block> fitSurface = TagKey.create(Registries.BLOCK, new ResourceLocation(Dross.MODID, "fit_surface"));
        TagKey<Block> fitSoil = TagKey.create(Registries.BLOCK, new ResourceLocation(Dross.MODID, "fit_soil"));
        for (Block block : List.of(ModBlocks.ASH.get(), sporeloam, hoarfrost, ModBlocks.SCARRED_NETHERRACK.get(),
                ModBlocks.CRAGSTONE.get(), ModBlocks.SILVERMOSS.get()))
        {
            if (!block.defaultBlockState().is(fitSurface))
            {
                helper.fail(name(block) + " should be in #dross:fit_surface");
            }
        }
        if (!ModBlocks.CRAGSTONE.get().defaultBlockState().is(fitSoil) || !ModBlocks.DUSKSTONE.get().defaultBlockState().is(fitSoil))
        {
            helper.fail("Cragstone and Duskstone should be in #dross:fit_soil");
        }
        // a shovel flattens both into a dirt path; a hoe doesn't till them (like mycelium and podzol)
        BlockPos[] spots = {MIDDLE.offset(-4, 0, -4), MIDDLE.offset(-2, 0, -4), MIDDLE.offset(0, 0, -4), MIDDLE.offset(2, 0, -4)};
        helper.setBlock(spots[0], sporeloam);
        helper.setBlock(spots[1], hoarfrost);
        helper.setBlock(spots[2], sporeloam);
        helper.setBlock(spots[3], hoarfrost);
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        useOnTop(helper, player, spots[0]);
        useOnTop(helper, player, spots[1]);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        useOnTop(helper, player, spots[2]);
        useOnTop(helper, player, spots[3]);
        for (int i = 0; i < 2; i++)
        {
            if (!helper.getBlockState(spots[i]).is(Blocks.DIRT_PATH))
            {
                helper.fail("a shovel should flatten " + (i == 0 ? "Sporeloam" : "Hoarfrost") + " into a dirt path");
            }
        }
        if (!helper.getBlockState(spots[2]).is(sporeloam) || !helper.getBlockState(spots[3]).is(hoarfrost))
        {
            helper.fail("a hoe shouldn't till Sporeloam or Hoarfrost");
        }
        // bone meal: Sporeloam sprouts Gloomcaps (and now and then a Sporepod); Hoarfrost isn't a bone meal target
        for (int x = 0; x < ARENA_SIZE; x++)
        {
            for (int z = 6; z < ARENA_SIZE; z++)
            {
                helper.setBlock(new BlockPos(x, GROUND, z), sporeloam);
            }
        }
        BlockPos fed = new BlockPos(ARENA_SIZE / 2, GROUND, 11);
        BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL, 64), level, helper.absolutePos(fed), player);
        int sprouts = countAround(helper, fed.above(), 8, ModBlocks.GLOOMCAP.get()) + countAround(helper, fed.above(), 8, ModBlocks.SPOREPOD.get());
        if (sprouts == 0)
        {
            helper.fail("bone meal on Sporeloam should sprout Gloomcaps or Sporepods");
        }
        helper.setBlock(spots[0], hoarfrost);
        if (BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL, 64), level, helper.absolutePos(spots[0]), player))
        {
            helper.fail("bone meal shouldn't do anything on Hoarfrost");
        }
        // vanilla mushrooms grow on Sporeloam whatever the light (it's a mushroom_grow_block)
        if (!Blocks.RED_MUSHROOM.defaultBlockState().canSurvive(level, helper.absolutePos(fed.above())))
        {
            helper.fail("a red mushroom should be able to grow on Sporeloam");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_terrain_biome_recipes")
    public static void biomeRecipesAndCompost(GameTestHelper helper)
    {
        expectCrafting(helper, new Item[] {ModItems.FROSTBLOOM.get()}, 1, Items.LIGHT_BLUE_DYE, 1);
        expectCrafting(helper, new Item[] {ModItems.CRAG_THISTLE.get()}, 1, Items.LIGHT_BLUE_DYE, 1);
        expectCrafting(helper, new Item[] {ModItems.SOULWICK.get()}, 1, Items.CYAN_DYE, 1);
        Item ash = ModItems.ASH.get();
        expectCrafting(helper, new Item[] {ash, ash, ash}, 3, ModItems.ASH_LAYER.get(), 6);
        for (Item item : List.of(ModItems.EMBERWEED.get(), ModItems.GLOOMCAP.get(), ModItems.SPOREPOD.get(),
                ModItems.BLIGHTCAP_BLOCK.get(), ModItems.ROTTING_BLIGHTCAP.get(), ModItems.BLIGHTCAP_STEM.get(),
                ModItems.FROSTBLOOM.get(), ModItems.SCAR_ROOTS.get(), ModItems.SOULWICK.get(), ModItems.STORM_LICHEN.get(),
                ModItems.CRAG_THISTLE.get()))
        {
            if (ComposterBlock.COMPOSTABLES.getFloat(item) <= 0.0F)
            {
                helper.fail(name(item) + " should go in a composter");
            }
        }
        // flowers count as small flowers (bees, flower pots' kin, endermen)
        for (Block flower : List.of(ModBlocks.FROSTBLOOM.get(), ModBlocks.SOULWICK.get(), ModBlocks.CRAG_THISTLE.get()))
        {
            if (!flower.defaultBlockState().is(BlockTags.SMALL_FLOWERS))
            {
                helper.fail(name(flower) + " should be in #minecraft:small_flowers");
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Uses the player's main-hand item on the top of the block at pos (as a right-click would). */
    private static void useOnTop(GameTestHelper helper, Player player, BlockPos pos)
    {
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0.0D, 0.5D, 0.0D), Direction.UP, abs, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /**
     * Rolls the drops of a block state {@link #ROLLS} times (as if mined with {@code tool}): every roll must be only
     * {@code item}, between min and max of it. Returns the lowest and highest count seen.
     */
    private static int[] expectDrops(GameTestHelper helper, BlockState state, ItemStack tool, Item item, int min, int max)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MIDDLE.above(8));
        int lowest = Integer.MAX_VALUE;
        int highest = 0;
        for (int i = 0; i < ROLLS; i++)
        {
            int count = 0;
            for (ItemStack drop : Block.getDrops(state, level, pos, null, null, tool))
            {
                if (drop.isEmpty())
                {
                    continue;   // a loot roll of 0 (vanilla's mushroom-block formula) is an empty stack: never dropped
                }
                if (!drop.is(item))
                {
                    helper.fail(name(state.getBlock()) + " dropped " + drop + " (expected only " + name(item) + ")");
                }
                count += drop.getCount();
            }
            lowest = Math.min(lowest, count);
            highest = Math.max(highest, count);
        }
        if (lowest < min || highest > max)
        {
            helper.fail(name(state.getBlock()) + " " + state + " should drop " + min + "-" + max + " " + name(item) + ", but dropped "
                    + lowest + "-" + highest + " in " + ROLLS + " tries");
        }
        return new int[] {lowest, highest};
    }

    private static void expectNoDrops(GameTestHelper helper, BlockState state, ItemStack tool)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MIDDLE.above(8));
        for (int i = 0; i < ROLLS; i++)
        {
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, null, tool));
            drops.removeIf(ItemStack::isEmpty);
            if (!drops.isEmpty())
            {
                helper.fail(name(state.getBlock()) + " shouldn't drop anything with " + tool + ", but dropped " + drops);
            }
        }
    }

    /** A crafting grid of the given width filled row by row (null = empty); it must craft count x output. */
    private static void expectCrafting(GameTestHelper helper, Item[] items, int width, Item output, int count)
    {
        ServerLevel level = helper.getLevel();
        int height = (items.length + width - 1) / width;
        NonNullList<ItemStack> grid = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int i = 0; i < items.length; i++)
        {
            if (items[i] != null)
            {
                grid.set(i, new ItemStack(items[i]));
            }
        }
        TransientCraftingContainer container = new TransientCraftingContainer(new NoMenu(), width, height, grid);
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
        ItemStack result = recipe.map(r -> r.assemble(container, level.registryAccess())).orElse(ItemStack.EMPTY);
        if (!result.is(output) || result.getCount() != count)
        {
            helper.fail("that crafting grid should make " + count + " " + name(output) + ", made " + result);
        }
    }

    /** A crafting grid needs a menu to tell about changes; this one ignores them. */
    private static final class NoMenu extends AbstractContainerMenu
    {
        NoMenu()
        {
            super(null, -1);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int slot)
        {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player)
        {
            return true;
        }
    }

    /** How many of the block are within {@code radius} blocks (sideways) of pos, on pos's own level. */
    private static int countAround(GameTestHelper helper, BlockPos pos, int radius, Block block)
    {
        int n = 0;
        for (int x = -radius; x <= radius; x++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                BlockPos at = pos.offset(x, 0, z);
                if (at.getX() >= 0 && at.getZ() >= 0 && at.getX() < ARENA_SIZE && at.getZ() < ARENA_SIZE
                        && helper.getBlockState(at).is(block))
                {
                    n++;
                }
            }
        }
        return n;
    }

    private static String name(Block block)
    {
        return block.getName().getString();
    }

    private static String name(Item item)
    {
        return item.getDescription().getString();
    }

    // ---------------------------------------------------------------------------------------------
    // The arena template
    // ---------------------------------------------------------------------------------------------

    /** The arena (a Cragstone floor with air above) is made in memory when a development server starts. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(ARENA_ID);
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK), arenaTag());
    }

    private static CompoundTag arenaTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(ARENA_SIZE, ARENA_HEIGHT, ARENA_SIZE));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(ModBlocks.CRAGSTONE.get().defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int x = 0; x < ARENA_SIZE; x++)
        {
            for (int z = 0; z < ARENA_SIZE; z++)
            {
                CompoundTag block = new CompoundTag();
                block.put("pos", intList(x, 0, z));
                block.putInt("state", 0);
                blocks.add(block);
            }
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag intList(int... values)
    {
        ListTag list = new ListTag();
        for (int value : values)
        {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}
