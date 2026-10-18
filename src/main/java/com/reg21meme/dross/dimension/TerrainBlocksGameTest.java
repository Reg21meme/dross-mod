package com.reg21meme.dross.dimension;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.worldgen.DrossTreeFeatures;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the Dross's terrain blocks and silverwood trees. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world with {@code /test runall}. They only exist
 * in the development environment: in a normal game nothing here runs.
 *
 * <ul>
 *   <li>{@link #oresDropTheRightItems}: each ore drops what it should with the right pickaxe (and nothing with a
 *       weaker one or by hand), silk touch gives the ore, and the XP is in range.</li>
 *   <li>{@link #axeStripsSilverwood}, {@link #toolsWorkOnSilvermoss}: axe stripping; hoe and shovel on Silvermoss.</li>
 *   <li>{@link #silvermossGrowsPlants}: saplings and plants grow on Silvermoss, and bone meal sprouts plants on it.</li>
 *   <li>{@link #saplingGrowsSilverwood}, {@link #fourSaplingsGrowGreatSilverwood}: saplings grow the right trees.</li>
 *   <li>{@link #treeShapesGrow}: each tree shape, grown {@link #TREES_PER_SHAPE} times on Silvermoss, has logs and
 *       leaves, a trunk of the right height, no leaf too far from a log to stay (it would decay), and some Stormvine.
 *       With the environment variable {@value #TREE_PREVIEW_ENV} (or {@code DROSS_TERRAIN_PREVIEWS}) set to {@code 1}, the
 *       grown trees are written to {@code build/tv1/trees.json} for {@code tools/terrain/preview_trees.py}.</li>
 *   <li>{@link #stormvineGlowsAndNeverSpreads}: Stormvine lights its surroundings and never grows.</li>
 *   <li>{@link #recipesExist}: the smelting, blasting and crafting recipes, and that the plants compost.</li>
 * </ul>
 * Each test has its own batch, so the trees of one never grow into another's arena. The other biomes' blocks (Cinder
 * Wastes, Blightwood, Rimefall, Nether Scar, Gravecrags) are tested in {@link BiomeBlocksGameTest}.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TerrainBlocksGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---------------------------------------------------------------------------------------------
    // Test setup
    // ---------------------------------------------------------------------------------------------

    /** The arena: a Silvermoss floor with air above, made in memory when the server starts. */
    static final String ARENA = "dross_terrain_arena";
    private static final ResourceLocation ARENA_ID = new ResourceLocation(Dross.MODID, ARENA);
    /** Width and depth (blocks): room for a great silverwood's canopy and branches all round. */
    static final int ARENA_SIZE = 31;
    /** Height (blocks, floor included): a great silverwood is up to 28 blocks tall, plus its canopy. */
    private static final int ARENA_HEIGHT = 36;
    /**
     * The floor's height in the test's own coordinates: the test framework places the arena one block above its
     * structure block (which is where the coordinates start), so the floor is at y 1 and the air above it at y 2 up.
     */
    private static final int FLOOR = 1;
    /** The highest y of the arena's air, in the test's coordinates. */
    private static final int TOP = FLOOR + ARENA_HEIGHT - 1;
    /** The middle of the arena, just above the floor (in the test's coordinates). */
    private static final BlockPos MIDDLE = new BlockPos(ARENA_SIZE / 2, FLOOR + 1, ARENA_SIZE / 2);

    /** How many times each random drop or XP amount is rolled to check its range. */
    private static final int ROLLS = 200;
    /** How many trees of each shape are grown (with fixed seeds, so the test always grows the same trees). */
    private static final int TREES_PER_SHAPE = 10;
    private static final long TREE_SEED = 20261011L;
    /** Most bone meal tries before giving up on a sapling (each try grows it with a 45% chance, two steps needed). */
    private static final int MAX_BONE_MEAL = 80;
    /** Stormvine's light level (see TerrainBlocks), and how many random ticks it gets in the "never spreads" check. */
    private static final int STORMVINE_LIGHT = 6;
    private static final int RANDOM_TICKS = 2000;

    /**
     * The longest a test waits for light to spread, in real time. The light engine works on its own thread, while the
     * test server runs its ticks as fast as it can (it doesn't wait 50 ms between them), so on a busy computer hundreds of
     * ticks can pass before the light is there (it usually takes well under a second).
     */
    static final long LIGHT_TIMEOUT_MILLIS = 60_000L;
    /** The tick limit of a test that waits for light: only a backstop behind {@link #LIGHT_TIMEOUT_MILLIS}. */
    static final int LIGHT_BACKSTOP_TICKS = 1_000_000;

    /** Expected trunk heights, and heights of the whole tree (trunk plus crown), from tools/terrain/worldgen.py. */
    private static final int[] SILVERWOOD_TRUNK = {6, 10};
    private static final int[] SILVERWOOD_HEIGHT = {9, 13};
    private static final int[] TALL_TRUNK = {10, 16};
    private static final int[] TALL_HEIGHT = {14, 20};
    private static final int[] GREAT_TRUNK = {19, 27};
    private static final int[] GREAT_HEIGHT = {20, 28};

    private TerrainBlocksGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Ores
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_ore_drops")
    public static void oresDropTheRightItems(GameTestHelper helper)
    {
        // Duskstone: any pickaxe, drops itself
        expectHarvest(helper, ModBlocks.DUSKSTONE.get(), Items.WOODEN_PICKAXE, true);
        expectHarvest(helper, ModBlocks.DUSKSTONE.get(), Items.AIR, false);
        expectDrops(helper, ModBlocks.DUSKSTONE.get(), new ItemStack(Items.WOODEN_PICKAXE), ModItems.DUSKSTONE.get(), 1, 1);

        // Coal: any pickaxe, 1 coal, 0-2 XP
        expectHarvest(helper, ModBlocks.DUSKSTONE_COAL_ORE.get(), Items.WOODEN_PICKAXE, true);
        expectDrops(helper, ModBlocks.DUSKSTONE_COAL_ORE.get(), new ItemStack(Items.WOODEN_PICKAXE), Items.COAL, 1, 1);
        expectXp(helper, ModBlocks.DUSKSTONE_COAL_ORE.get(), 0, 2);

        // Gold: any pickaxe, 2-6 gold nuggets like nether gold ore, 0-1 XP
        expectHarvest(helper, ModBlocks.DUSKSTONE_GOLD_ORE.get(), Items.WOODEN_PICKAXE, true);
        expectHarvest(helper, ModBlocks.DUSKSTONE_GOLD_ORE.get(), Items.AIR, false);
        expectDrops(helper, ModBlocks.DUSKSTONE_GOLD_ORE.get(), new ItemStack(Items.WOODEN_PICKAXE), Items.GOLD_NUGGET, 2, 6);
        expectXp(helper, ModBlocks.DUSKSTONE_GOLD_ORE.get(), 0, 1);

        // Moonsilver: a stone pickaxe or better, 1 raw moonsilver; nothing by hand or with a wooden pickaxe
        expectHarvest(helper, ModBlocks.MOONSILVER_ORE.get(), Items.STONE_PICKAXE, true);
        expectHarvest(helper, ModBlocks.MOONSILVER_ORE.get(), Items.WOODEN_PICKAXE, false);
        expectHarvest(helper, ModBlocks.MOONSILVER_ORE.get(), Items.AIR, false);
        expectDrops(helper, ModBlocks.MOONSILVER_ORE.get(), new ItemStack(Items.STONE_PICKAXE), ModItems.RAW_MOONSILVER.get(), 1, 1);

        // Stormite: an iron pickaxe or better, 1-2 shards, 2-5 XP; nothing with a stone pickaxe; silk touch: the ore
        expectHarvest(helper, ModBlocks.STORMITE_ORE.get(), Items.IRON_PICKAXE, true);
        expectHarvest(helper, ModBlocks.STORMITE_ORE.get(), Items.STONE_PICKAXE, false);
        expectDrops(helper, ModBlocks.STORMITE_ORE.get(), new ItemStack(Items.IRON_PICKAXE), ModItems.STORMITE_SHARD.get(), 1, 2);
        expectXp(helper, ModBlocks.STORMITE_ORE.get(), 2, 5);
        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        expectDrops(helper, ModBlocks.STORMITE_ORE.get(), silk, ModItems.STORMITE_ORE.get(), 1, 1);

        // Stormite glows
        int light = ModBlocks.STORMITE_ORE.get().defaultBlockState().getLightEmission(helper.getLevel(), helper.absolutePos(MIDDLE));
        if (light <= 0)
        {
            helper.fail("Stormite Ore should give off light, but its light level is " + light);
        }
        helper.succeed();
    }

    /** Fails unless a player (in survival) holding {@code tool} (AIR = bare hand) can / can't harvest the block. */
    private static void expectHarvest(GameTestHelper helper, Block block, Item tool, boolean expected)
    {
        Player player = helper.makeMockSurvivalPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool == Items.AIR ? ItemStack.EMPTY : new ItemStack(tool));
        boolean can = player.hasCorrectToolForDrops(block.defaultBlockState());
        if (can != expected)
        {
            helper.fail(name(block) + " " + (expected ? "should" : "shouldn't") + " drop anything when mined with "
                    + (tool == Items.AIR ? "a bare hand" : name(tool)));
        }
    }

    /** Rolls the block's loot {@link #ROLLS} times: every roll must be only {@code item}, from min to max of it. */
    private static void expectDrops(GameTestHelper helper, Block block, ItemStack tool, Item item, int min, int max)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MIDDLE);
        BlockState state = block.defaultBlockState();
        helper.setBlock(MIDDLE, state);
        int lowest = Integer.MAX_VALUE;
        int highest = 0;
        for (int i = 0; i < ROLLS; i++)
        {
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, tool);
            int count = 0;
            for (ItemStack drop : drops)
            {
                if (!drop.is(item))
                {
                    helper.fail(name(block) + " dropped " + drop + " (expected only " + name(item) + ")");
                }
                count += drop.getCount();
            }
            lowest = Math.min(lowest, count);
            highest = Math.max(highest, count);
        }
        if (lowest < min || highest > max)
        {
            helper.fail(name(block) + " should drop " + min + "-" + max + " " + name(item) + ", but dropped "
                    + lowest + "-" + highest + " in " + ROLLS + " tries");
        }
        helper.setBlock(MIDDLE, Blocks.AIR);
    }

    /** Rolls the XP for mining the block (no Fortune, no Silk Touch) {@link #ROLLS} times: it must stay in min-max. */
    private static void expectXp(GameTestHelper helper, Block block, int min, int max)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MIDDLE);
        BlockState state = block.defaultBlockState();
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < ROLLS; i++)
        {
            int xp = state.getExpDrop(level, level.random, pos, 0, 0);
            lowest = Math.min(lowest, xp);
            highest = Math.max(highest, xp);
        }
        if (lowest < min || highest > max)
        {
            helper.fail(name(block) + " should give " + min + "-" + max + " XP, but gave " + lowest + "-" + highest);
        }
        if (state.getExpDrop(level, level.random, pos, 0, 1) != 0)
        {
            helper.fail(name(block) + " shouldn't give XP when mined with Silk Touch");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tools
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_axe")
    public static void axeStripsSilverwood(GameTestHelper helper)
    {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        BlockPos up = MIDDLE;
        BlockPos sideways = MIDDLE.east(2);
        BlockPos wood = MIDDLE.west(2);
        helper.setBlock(up, ModBlocks.SILVERWOOD_LOG.get().defaultBlockState());
        helper.setBlock(sideways, ModBlocks.SILVERWOOD_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        helper.setBlock(wood, ModBlocks.SILVERWOOD_WOOD.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        use(helper, player, up);
        use(helper, player, sideways);
        use(helper, player, wood);
        expectState(helper, up, ModBlocks.STRIPPED_SILVERWOOD_LOG.get(), Direction.Axis.Y);
        expectState(helper, sideways, ModBlocks.STRIPPED_SILVERWOOD_LOG.get(), Direction.Axis.X);
        expectState(helper, wood, ModBlocks.STRIPPED_SILVERWOOD_WOOD.get(), Direction.Axis.Z);
        // a stripped log stays as it is
        use(helper, player, up);
        expectState(helper, up, ModBlocks.STRIPPED_SILVERWOOD_LOG.get(), Direction.Axis.Y);
        helper.succeed();
    }

    private static void expectState(GameTestHelper helper, BlockPos pos, Block block, Direction.Axis axis)
    {
        BlockState state = helper.getBlockState(pos);
        if (!state.is(block) || state.getValue(RotatedPillarBlock.AXIS) != axis)
        {
            helper.fail("expected " + name(block) + " along " + axis + " after using the axe, found " + state);
        }
    }

    @GameTest(template = ARENA, batch = "dross_terrain_silvermoss_tools")
    public static void toolsWorkOnSilvermoss(GameTestHelper helper)
    {
        Player player = helper.makeMockPlayer();
        BlockPos tilled = MIDDLE.below();              // the floor is Silvermoss
        BlockPos flattened = MIDDLE.below().east(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_HOE));
        use(helper, player, tilled);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SHOVEL));
        use(helper, player, flattened);
        if (!helper.getBlockState(tilled).is(Blocks.FARMLAND))
        {
            helper.fail("a hoe should till Silvermoss into farmland, found " + helper.getBlockState(tilled));
        }
        if (!helper.getBlockState(flattened).is(Blocks.DIRT_PATH))
        {
            helper.fail("a shovel should flatten Silvermoss into a dirt path, found " + helper.getBlockState(flattened));
        }
        helper.succeed();
    }

    /** Uses the player's main-hand item on the top of the block at pos (as a right-click would). */
    private static void use(GameTestHelper helper, Player player, BlockPos pos)
    {
        BlockPos abs = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0.0D, 0.5D, 0.0D), Direction.UP, abs, false);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    // ---------------------------------------------------------------------------------------------
    // Plants and saplings
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_silvermoss_plants")
    public static void silvermossGrowsPlants(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        for (Block plant : List.of(ModBlocks.SILVERWOOD_SAPLING.get(), ModBlocks.GLIMMER_FERN.get(), ModBlocks.WISPBLOOM.get()))
        {
            if (!plant.defaultBlockState().canSurvive(level, helper.absolutePos(MIDDLE)))
            {
                helper.fail(name(plant) + " should be able to grow on Silvermoss");
            }
        }
        // bone meal on the floor sprouts Glimmer Ferns (and now and then a Wispbloom) round about
        BoneMealItem.applyBonemeal(new ItemStack(Items.BONE_MEAL, 64), level, helper.absolutePos(MIDDLE.below()), helper.makeMockPlayer());
        int plants = 0;
        for (int x = 0; x < ARENA_SIZE; x++)
        {
            for (int z = 0; z < ARENA_SIZE; z++)
            {
                BlockState state = helper.getBlockState(new BlockPos(x, FLOOR + 1, z));
                if (state.is(ModBlocks.GLIMMER_FERN.get()) || state.is(ModBlocks.WISPBLOOM.get()))
                {
                    plants++;
                }
            }
        }
        if (plants == 0)
        {
            helper.fail("bone meal on Silvermoss should sprout some Glimmer Ferns or Wispblooms");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_terrain_sapling", timeoutTicks = 100)
    public static void saplingGrowsSilverwood(GameTestHelper helper)
    {
        helper.setBlock(MIDDLE, ModBlocks.SILVERWOOD_SAPLING.get());
        boneMealUntilGrown(helper, MIDDLE);
        int trunk = logsStraightUp(helper, MIDDLE);
        if (trunk < SILVERWOOD_TRUNK[0] || trunk > TALL_TRUNK[1])
        {
            helper.fail("the sapling grew a trunk of " + trunk + " logs; a silverwood or tall silverwood has "
                    + SILVERWOOD_TRUNK[0] + "-" + TALL_TRUNK[1]);
        }
        if (countInArena(helper, ModBlocks.SILVERWOOD_LEAVES.get()) == 0)
        {
            helper.fail("the sapling grew a tree without leaves");
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_terrain_great_sapling", timeoutTicks = 100)
    public static void fourSaplingsGrowGreatSilverwood(GameTestHelper helper)
    {
        BlockPos[] square = {MIDDLE, MIDDLE.east(), MIDDLE.south(), MIDDLE.south().east()};
        for (BlockPos pos : square)
        {
            helper.setBlock(pos, ModBlocks.SILVERWOOD_SAPLING.get());
        }
        boneMealUntilGrown(helper, MIDDLE);
        for (BlockPos pos : square)
        {
            if (!helper.getBlockState(pos).is(ModBlocks.SILVERWOOD_LOG.get()))
            {
                helper.fail("four saplings in a square should grow a 2x2 trunk, but " + pos + " is " + helper.getBlockState(pos));
            }
        }
        int trunk = logsStraightUp(helper, MIDDLE);
        if (trunk < GREAT_TRUNK[0] || trunk > GREAT_TRUNK[1])
        {
            helper.fail("the great silverwood's trunk is " + trunk + " logs tall; expected " + GREAT_TRUNK[0] + "-" + GREAT_TRUNK[1]);
        }
        helper.succeed();
    }

    /** Bone meal on a sapling until it has grown (it takes two successful tries; each try works 45% of the time). */
    private static void boneMealUntilGrown(GameTestHelper helper, BlockPos sapling)
    {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer();
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 64);
        for (int i = 0; i < MAX_BONE_MEAL && helper.getBlockState(sapling).is(ModBlocks.SILVERWOOD_SAPLING.get()); i++)
        {
            if (boneMeal.isEmpty())
            {
                boneMeal = new ItemStack(Items.BONE_MEAL, 64);
            }
            BoneMealItem.applyBonemeal(boneMeal, level, helper.absolutePos(sapling), player);
        }
        if (!helper.getBlockState(sapling).is(ModBlocks.SILVERWOOD_LOG.get()))
        {
            helper.fail("the sapling didn't grow into a tree after " + MAX_BONE_MEAL + " bone meal (found "
                    + helper.getBlockState(sapling) + ")");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tree shapes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_tree_shapes", timeoutTicks = 100)
    public static void treeShapesGrow(GameTestHelper helper)
    {
        JsonArray dump = new JsonArray();
        List<String> problems = new ArrayList<>();
        checkShape(helper, DrossTreeFeatures.SILVERWOOD, SILVERWOOD_TRUNK, SILVERWOOD_HEIGHT, false, dump, problems);
        checkShape(helper, DrossTreeFeatures.TALL_SILVERWOOD, TALL_TRUNK, TALL_HEIGHT, false, dump, problems);
        checkShape(helper, DrossTreeFeatures.GREAT_SILVERWOOD, GREAT_TRUNK, GREAT_HEIGHT, true, dump, problems);
        writeDump(dump);
        if (!problems.isEmpty())
        {
            helper.fail(String.join("; ", problems));
        }
        helper.succeed();
    }

    /** Grows {@link #TREES_PER_SHAPE} trees of one shape in the middle of the arena, one at a time, and checks them. */
    private static void checkShape(GameTestHelper helper, ResourceKey<ConfiguredFeature<?, ?>> key, int[] trunkRange, int[] heightRange,
            boolean twoByTwo, JsonArray dump, List<String> problems)
    {
        ServerLevel level = helper.getLevel();
        Optional<? extends net.minecraft.core.Holder<ConfiguredFeature<?, ?>>> holder =
                level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).getHolder(key);
        if (holder.isEmpty())
        {
            problems.add("the tree " + key.location() + " doesn't exist (its JSON didn't load)");
            return;
        }
        ConfiguredFeature<?, ?> feature = holder.get().value();
        String name = key.location().getPath();
        int vines = 0;
        RandomSource seeds = RandomSource.create(TREE_SEED + name.hashCode());
        for (int i = 0; i < TREES_PER_SHAPE; i++)
        {
            BlockPos origin = helper.absolutePos(MIDDLE);
            long seed = seeds.nextLong();               // well-mixed seeds (neighbouring seeds start out alike)
            RandomSource random = RandomSource.create(seed);
            if (!feature.place(level, level.getChunkSource().getGenerator(), random, origin))
            {
                problems.add(name + " #" + i + " didn't grow in an empty arena");
                continue;
            }
            TreeScan scan = scanAndClear(helper, origin);
            int trunk = scan.trunkHeight;
            if (scan.logs == 0 || scan.leaves == 0)
            {
                problems.add(name + " #" + i + " has " + scan.logs + " logs and " + scan.leaves + " leaves");
            }
            if (trunk < trunkRange[0] || trunk > trunkRange[1])
            {
                problems.add(name + " #" + i + " has a trunk " + trunk + " tall (expected " + trunkRange[0] + "-" + trunkRange[1] + ")");
            }
            if (scan.height < heightRange[0] || scan.height > heightRange[1])
            {
                problems.add(name + " #" + i + " is " + scan.height + " tall (expected " + heightRange[0] + "-" + heightRange[1] + ")");
            }
            if (scan.farLeaves > 0)
            {
                problems.add(name + " #" + i + " has " + scan.farLeaves + " leaves too far from its logs (they'd decay)");
            }
            if (scan.touchesEdge)
            {
                problems.add(name + " #" + i + " reached the edge of the test arena (the test arena is too small)");
            }
            if (twoByTwo && scan.trunkColumns < 4)
            {
                problems.add(name + " #" + i + " doesn't have a 2x2 trunk");
            }
            vines += scan.vines;
            JsonObject tree = new JsonObject();
            tree.addProperty("feature", key.location().toString());
            tree.addProperty("seed", seed);
            tree.addProperty("trunk", trunk);
            tree.addProperty("height", scan.height);
            tree.add("blocks", scan.blocks);
            dump.add(tree);
        }
        if (vines == 0)
        {
            problems.add("none of the " + TREES_PER_SHAPE + " " + name + " trees got any Stormvine");
        }
        LOGGER.info("Terrain test: {} x{} grew, {} Stormvine blocks in all", name, TREES_PER_SHAPE, vines);
    }

    /** What a grown tree is made of; its blocks are removed from the arena once counted. */
    private static final class TreeScan
    {
        int logs;
        int leaves;
        int vines;
        int farLeaves;
        int trunkHeight;
        /** From the ground to the top of its highest log or leaf (blocks). */
        int height;
        int trunkColumns;
        boolean touchesEdge;
        final JsonArray blocks = new JsonArray();
    }

    private static TreeScan scanAndClear(GameTestHelper helper, BlockPos origin)
    {
        ServerLevel level = helper.getLevel();
        TreeScan scan = new TreeScan();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        List<BlockPos> toClear = new ArrayList<>();
        for (int y = FLOOR + 1; y <= TOP; y++)
        {
            for (int x = 0; x < ARENA_SIZE; x++)
            {
                for (int z = 0; z < ARENA_SIZE; z++)
                {
                    BlockPos pos = corner.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir())
                    {
                        continue;
                    }
                    toClear.add(pos);
                    if (state.is(BlockTags.LOGS) || state.getBlock() instanceof LeavesBlock)
                    {
                        scan.height = Math.max(scan.height, pos.getY() - origin.getY() + 1);
                    }
                    if (state.is(BlockTags.LOGS))
                    {
                        scan.logs++;
                    }
                    else if (state.getBlock() instanceof LeavesBlock)
                    {
                        scan.leaves++;
                        if (state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE)
                        {
                            scan.farLeaves++;
                        }
                    }
                    else if (state.is(ModBlocks.STORMVINE.get()))
                    {
                        scan.vines++;
                    }
                    if (x == 0 || z == 0 || x == ARENA_SIZE - 1 || z == ARENA_SIZE - 1 || y == TOP)
                    {
                        scan.touchesEdge = true;
                    }
                    JsonArray block = new JsonArray();
                    block.add(pos.getX() - origin.getX());
                    block.add(pos.getY() - origin.getY());
                    block.add(pos.getZ() - origin.getZ());
                    block.add(BlockStateParser.serialize(state));
                    scan.blocks.add(block);
                }
            }
        }
        scan.trunkHeight = logsStraightUpAbs(level, origin);
        for (BlockPos column : new BlockPos[] {origin, origin.east(), origin.south(), origin.south().east()})
        {
            if (level.getBlockState(column).is(ModBlocks.SILVERWOOD_LOG.get()))
            {
                scan.trunkColumns++;
            }
        }
        for (BlockPos pos : toClear)
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        return scan;
    }

    private static int logsStraightUp(GameTestHelper helper, BlockPos pos)
    {
        return logsStraightUpAbs(helper.getLevel(), helper.absolutePos(pos));
    }

    private static int logsStraightUpAbs(ServerLevel level, BlockPos pos)
    {
        int n = 0;
        while (level.getBlockState(pos.above(n)).is(ModBlocks.SILVERWOOD_LOG.get()))
        {
            n++;
        }
        return n;
    }

    private static int countInArena(GameTestHelper helper, Block block)
    {
        int n = 0;
        for (int y = FLOOR + 1; y <= TOP; y++)
        {
            for (int x = 0; x < ARENA_SIZE; x++)
            {
                for (int z = 0; z < ARENA_SIZE; z++)
                {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block))
                    {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** Set to 1 (or DROSS_TERRAIN_PREVIEWS=1) to write the grown trees for tools/terrain/preview_trees.py. */
    static final String TREE_PREVIEW_ENV = "DROSS_TREE_PREVIEWS";

    /**
     * Writes the grown trees to build/tv1/trees.json (the project's build folder) for the preview renderer, only when
     * {@value #TREE_PREVIEW_ENV}=1 or {@code DROSS_TERRAIN_PREVIEWS}=1 (like the other preview exporters).
     */
    private static void writeDump(JsonArray trees)
    {
        if (!"1".equals(System.getenv(TREE_PREVIEW_ENV)) && !"1".equals(System.getenv(TerrainPreviewGameTest.ENV)))
        {
            return;
        }
        JsonObject root = new JsonObject();
        root.addProperty("note", "Written by TerrainBlocksGameTest.treeShapesGrow; rendered by tools/terrain/preview_trees.py");
        root.add("trees", trees);
        Path file = FMLPaths.GAMEDIR.get().toAbsolutePath().getParent().resolve("build").resolve("tv1").resolve("trees.json");
        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().create().toJson(root), StandardCharsets.UTF_8);
            LOGGER.info("Terrain test: wrote the grown trees to {}", file);
        }
        catch (IOException e)
        {
            LOGGER.warn("Terrain test: couldn't write {}: {}", file, e.toString());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Stormvine
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_stormvine", timeoutTicks = LIGHT_BACKSTOP_TICKS)
    public static void stormvineGlowsAndNeverSpreads(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        // a wall of duskstone to hang it on, and the vine on the wall's south side, held by its north face
        for (int y = 1; y <= 6; y++)
        {
            for (int x = -2; x <= 2; x++)
            {
                helper.setBlock(MIDDLE.offset(x, y - 1, 0), ModBlocks.DUSKSTONE.get());
            }
        }
        BlockPos vinePos = MIDDLE.offset(0, 2, 1);
        BlockState vine = ModBlocks.STORMVINE.get().defaultBlockState().setValue(VineBlock.NORTH, true);
        helper.setBlock(vinePos, vine);
        BlockPos abs = helper.absolutePos(vinePos);
        if (vine.isRandomlyTicking())
        {
            helper.fail("Stormvine shouldn't take random ticks (that's how vanilla vines spread)");
        }
        RandomSource random = RandomSource.create(1L);
        for (int i = 0; i < RANDOM_TICKS; i++)
        {
            level.getBlockState(abs).randomTick(level, abs, random);
        }
        int vines = countInArena(helper, ModBlocks.STORMVINE.get());
        if (vines != 1)
        {
            helper.fail("Stormvine spread: " + vines + " blocks after " + RANDOM_TICKS + " random ticks (should stay 1)");
        }
        if (vine.getLightEmission(level, abs) < STORMVINE_LIGHT)
        {
            helper.fail("Stormvine should give off light level " + STORMVINE_LIGHT + ", but gives " + vine.getLightEmission(level, abs));
        }
        // the light spreads out over the next moments (real time: see LIGHT_TIMEOUT_MILLIS)
        succeedWhenLit(helper, () -> {
            int lightHere = level.getBrightness(LightLayer.BLOCK, abs);
            if (lightHere < STORMVINE_LIGHT)
            {
                throw new GameTestAssertException("the block light at the Stormvine is " + lightHere + " (expected "
                        + STORMVINE_LIGHT + ")");
            }
        }, () -> {});
    }

    /**
     * Waits until {@code lit} passes (it throws {@link GameTestAssertException} while the light isn't there yet), at most
     * {@link #LIGHT_TIMEOUT_MILLIS} of real time; then checks it once more (failing the test if the light still isn't
     * there), runs {@code then} and succeeds. The test's tick limit must be {@link #LIGHT_BACKSTOP_TICKS} or more.
     */
    static void succeedWhenLit(GameTestHelper helper, Runnable lit, Runnable then)
    {
        long deadline = Util.getMillis() + LIGHT_TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (Util.getMillis() < deadline)
                    {
                        lit.run();
                    }
                })
                .thenExecute(lit)
                .thenExecute(then)
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Recipes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_terrain_recipes")
    public static void recipesExist(GameTestHelper helper)
    {
        expectCooking(helper, RecipeType.SMELTING, ModItems.RAW_MOONSILVER.get(), ModItems.MOONSILVER_INGOT.get());
        expectCooking(helper, RecipeType.BLASTING, ModItems.RAW_MOONSILVER.get(), ModItems.MOONSILVER_INGOT.get());
        expectCooking(helper, RecipeType.SMELTING, ModItems.MOONSILVER_ORE.get(), ModItems.MOONSILVER_INGOT.get());
        expectCooking(helper, RecipeType.SMELTING, ModItems.DUSKSTONE_COAL_ORE.get(), Items.COAL);
        expectCooking(helper, RecipeType.BLASTING, ModItems.DUSKSTONE_GOLD_ORE.get(), Items.GOLD_INGOT);
        expectCrafting(helper, List.of(ModItems.SILVERWOOD_LOG.get()), 1, ModItems.SILVERWOOD_PLANKS.get(), 4);
        expectCrafting(helper, List.of(ModItems.STRIPPED_SILVERWOOD_WOOD.get()), 1, ModItems.SILVERWOOD_PLANKS.get(), 4);
        expectCrafting(helper, List.of(ModItems.SILVERWOOD_LOG.get(), ModItems.SILVERWOOD_LOG.get(), ModItems.SILVERWOOD_LOG.get(),
                ModItems.SILVERWOOD_LOG.get()), 2, ModItems.SILVERWOOD_WOOD.get(), 3);
        expectCrafting(helper, List.of(ModItems.WISPBLOOM.get()), 1, Items.BLUE_DYE, 1);
        // the plants go in a composter (TerrainSetup)
        for (Item plant : List.of(ModItems.SILVERWOOD_LEAVES.get(), ModItems.SILVERWOOD_SAPLING.get(), ModItems.STORMVINE.get(),
                ModItems.GLIMMER_FERN.get(), ModItems.WISPBLOOM.get()))
        {
            if (ComposterBlock.COMPOSTABLES.getFloat(plant) <= 0.0F)
            {
                helper.fail(name(plant) + " should go in a composter");
            }
        }
        helper.succeed();
    }

    private static <T extends AbstractCookingRecipe> void expectCooking(GameTestHelper helper, RecipeType<T> type, Item input, Item output)
    {
        ServerLevel level = helper.getLevel();
        Optional<T> recipe = level.getRecipeManager().getRecipeFor(type, new SimpleContainer(new ItemStack(input)), level);
        if (recipe.isEmpty() || !recipe.get().getResultItem(level.registryAccess()).is(output))
        {
            helper.fail("no " + type + " recipe turns " + name(input) + " into " + name(output));
        }
    }

    /** A crafting grid of the given width filled row by row with the items; it must craft count x output. */
    private static void expectCrafting(GameTestHelper helper, List<Item> items, int width, Item output, int count)
    {
        ServerLevel level = helper.getLevel();
        int height = (items.size() + width - 1) / width;
        NonNullList<ItemStack> grid = NonNullList.withSize(width * height, ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++)
        {
            grid.set(i, new ItemStack(items.get(i)));
        }
        TransientCraftingContainer container = new TransientCraftingContainer(new NoMenu(), width, height, grid);
        Optional<CraftingRecipe> recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
        ItemStack result = recipe.map(r -> r.assemble(container, level.registryAccess())).orElse(ItemStack.EMPTY);
        if (!result.is(output) || result.getCount() != count)
        {
            helper.fail("crafting " + items.size() + " x " + name(items.get(0)) + " should make " + count + " " + name(output)
                    + ", made " + result);
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

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

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

    /** The arena (a Silvermoss floor with air above) is made in memory when a development server starts. */
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
        palette.add(NbtUtils.writeBlockState(ModBlocks.SILVERMOSS.get().defaultBlockState()));
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
