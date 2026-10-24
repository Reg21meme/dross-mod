package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophies;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyBlock;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyBlockEntity;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.MIDDLE;

/**
 * Headless tests (GameTests) for the Huntsman trophies ({@code .\gradlew.bat runGameTestServer}), in the Cinder Colossus
 * tests' arena ({@code ColossusGameTest.ARENA}, 17 x 17 blocks). Each runs in its own batch.
 * <ul>
 *   <li>{@link #rightClickCyclesTrophyDesign}: right-clicking a placed trophy turns it 1, 2 ... 5, then 1 again, still
 *       facing the same way (holding a block, which isn't placed; then with an empty hand).</li>
 *   <li>{@link #breakingDropsShownDesign}: broken, it drops itself showing the same design (and so does pick-block).</li>
 *   <li>{@link #trophyForEveryVictor}: the pair beaten, one trophy of {@link HuntsmanTuning#TROPHY_DROP_DESIGN} goes to
 *       the killer and a player near them, straight into their inventories (nothing dropped), and none to a player 60
 *       blocks away.</li>
 *   <li>{@link #noTrophyForKillCommand}: removed with {@code /kill}, they give no trophy.</li>
 *   <li>{@link #showcasePlacesFive}: {@code /dross showcase trophies huntsman} places the five in a row, facing the
 *       command, each on a pedestal with its "No. N" sign; running it again replaces the row.</li>
 * </ul>
 * (Its drop when an inventory is full or its player is dead is the shared {@code TrophyRewards}, tested with the Rat
 * King's trophies.)
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HuntsmanTrophyGameTest
{
    private static final int ARENA_SIZE = 17;
    private static final int ARENA_HEIGHT = 11;
    private static final float HIT = 10.0F;
    private static final float HUGE_HIT = 1000.0F;
    /** Far beyond the reward's 40 blocks. */
    private static final int FAR = 60;
    /** The design broken in the break test. */
    private static final int BROKEN_DESIGN = 4;

    /** Where the showcase test runs: far from spawn, the other tests and the other bosses' trophy showcase tests. */
    private static final int SHOWCASE_X = 2000;
    private static final int SHOWCASE_Z = 2000;
    /** Trophies are looked for this many blocks round the showcase spot (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 20;
    private static final String SHOWCASE_COMMAND = "dross showcase trophies huntsman";
    private static final String SIGN_NUMBER = "No. ";

    private HuntsmanTrophyGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The block
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_huntsman_trophy_cycle", timeoutTicks = 40)
    public static void rightClickCyclesTrophyDesign(GameTestHelper helper)
    {
        Direction facing = Direction.EAST;
        helper.setBlock(MIDDLE, HuntsmanTrophies.get(1).blockState(facing));
        Player player = helper.makeMockPlayer();
        // Holding a block: the trophy still turns, and the block isn't placed against it.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        int expected = 1;
        List<Integer> seen = new ArrayList<>();
        for (int click = 1; click <= HuntsmanTrophies.count(); click++)
        {
            if (click == 3)
            {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // an empty hand turns it too
            }
            helper.useBlock(MIDDLE, player);
            expected = expected % HuntsmanTrophies.count() + 1;
            BlockState state = helper.getBlockState(MIDDLE);
            if (!state.is(ModBlocks.HUNTSMAN_TROPHY.get()))
            {
                helper.fail("click " + click + ": the trophy is gone (" + state + ")");
                return;
            }
            int design = state.getValue(HuntsmanTrophyBlock.DESIGN);
            seen.add(design);
            if (design != expected)
            {
                helper.fail("click " + click + ": the trophy should show design " + expected + ", but it shows " + design);
            }
            if (state.getValue(HuntsmanTrophyBlock.FACING) != facing)
            {
                helper.fail("click " + click + ": the trophy should still face " + facing + ", but it faces "
                        + state.getValue(HuntsmanTrophyBlock.FACING));
            }
            if (!(helper.getBlockEntity(MIDDLE) instanceof HuntsmanTrophyBlockEntity trophy) || trophy.design() != expected)
            {
                helper.fail("click " + click + ": the trophy's block entity should draw design " + expected);
            }
        }
        if (!seen.equals(List.of(2, 3, 4, 5, 1)))
        {
            helper.fail("five clicks from design 1 should show 2, 3, 4, 5 and then 1 again, but showed " + seen);
        }
        for (Direction side : Direction.values())
        {
            helper.assertBlockNotPresent(Blocks.STONE, MIDDLE.relative(side));
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_huntsman_trophy_break", timeoutTicks = ARENA_WAIT_TICKS + 60)
    public static void breakingDropsShownDesign(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanTrophyGameTest::breakingDropsShownDesign))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos broken = MIDDLE.offset(-3, 0, 0);
        BlockState state = HuntsmanTrophies.get(BROKEN_DESIGN).blockState(Direction.SOUTH);
        helper.setBlock(broken, state);
        ItemStack picked = state.getBlock().getCloneItemStack(level, helper.absolutePos(broken), state);
        if (!picked.is(ModItems.HUNTSMAN_TROPHY.get()) || HuntsmanTrophies.designOf(picked) != BROKEN_DESIGN)
        {
            helper.fail("pick-block should give a design " + BROKEN_DESIGN + " trophy, but gave " + picked
                    + " (design " + HuntsmanTrophies.designOf(picked) + ")");
            return;
        }
        level.destroyBlock(helper.absolutePos(broken), true);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ItemEntity> items = trophiesNear(helper);
                    items.forEach(ItemEntity::discard);
                    helper.assertBlockNotPresent(ModBlocks.HUNTSMAN_TROPHY.get(), broken);
                    if (items.size() != 1 || HuntsmanTrophies.designOf(items.get(0).getItem()) != BROKEN_DESIGN)
                    {
                        helper.fail("the broken trophy should drop one design " + BROKEN_DESIGN + " trophy, but "
                                + items.size() + " dropped");
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The reward
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_huntsman_trophy_reward", timeoutTicks = ARENA_WAIT_TICKS + 200)
    public static void trophyForEveryVictor(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanTrophyGameTest::trophyForEveryVictor))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper);
        AlphaMoose m = HuntFight.mooseOf(h);
        ServerPlayer killer = ColossusGameTest.joinPlayer(helper, "hunt-trophy-killer", MIDDLE.offset(4, 0, 0), 0.0F);
        ServerPlayer near = ColossusGameTest.joinPlayer(helper, "hunt-trophy-near", MIDDLE.offset(-4, 0, 3), 0.0F);
        ServerPlayer far = ColossusGameTest.joinPlayer(helper, "hunt-trophy-far", MIDDLE.offset(FAR, 0, 0), 0.0F);
        List<ServerPlayer> players = List.of(killer, near, far);
        // The Wild Hunt's pool nearly gone, then the split.
        hit(m, level.damageSources().playerAttack(killer), HIT);
        m.wildHunt = 1.0F;
        hit(m, level.damageSources().playerAttack(killer), HIT);
        helper.runAfterDelay(HuntsmanTuning.SPLIT_INVULNERABLE_TICKS + 10, () ->
        {
            String problem = null;
            hit(m, level.damageSources().playerAttack(killer), HUGE_HIT);
            hit(h, level.damageSources().playerAttack(killer), HUGE_HIT);
            if (!m.isDeadOrDying() || !h.isDeadOrDying())
            {
                problem = "huge hits after the split should kill them both";
            }
            else if (!trophiesNear(helper).isEmpty())
            {
                problem = "with room in their inventories nothing should drop";
            }
            else
            {
                for (ServerPlayer player : List.of(killer, near))
                {
                    List<ItemStack> mine = trophiesIn(player);
                    if (mine.size() != 1 || mine.get(0).getCount() != 1)
                    {
                        problem = player.getScoreboardName() + " should have exactly one Huntsman trophy, but has "
                                + mine.size() + " stack(s)";
                    }
                    else if (HuntsmanTrophies.designOf(mine.get(0)) != HuntsmanTuning.TROPHY_DROP_DESIGN)
                    {
                        problem = "the trophy should be design " + HuntsmanTuning.TROPHY_DROP_DESIGN + ", but it's "
                                + HuntsmanTrophies.designOf(mine.get(0));
                    }
                }
                if (problem == null && !trophiesIn(far).isEmpty())
                {
                    problem = "a player " + FAR + " blocks away shouldn't get a trophy";
                }
            }
            trophiesNear(helper).forEach(ItemEntity::discard);
            ColossusGameTest.leaveAll(players);
            h.discard();
            m.discard();
            if (problem != null)
            {
                throw new GameTestAssertException(problem);
            }
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "dross_huntsman_trophy_kill", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void noTrophyForKillCommand(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanTrophyGameTest::noTrophyForKillCommand))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper);
        AlphaMoose m = HuntFight.mooseOf(h);
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, "hunt-trophy-bystander", MIDDLE.offset(4, 0, 0), 0.0F);
        hit(m, level.damageSources().playerAttack(player), HIT);
        m.kill();
        h.kill();
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ItemEntity> dropped = trophiesNear(helper);
                    int held = trophiesIn(player).size();
                    ColossusGameTest.leaveAll(List.of(player));
                    h.discard();
                    m.discard();
                    dropped.forEach(ItemEntity::discard);
                    if (!dropped.isEmpty() || held > 0)
                    {
                        helper.fail("a pair removed with /kill shouldn't give a trophy, but " + dropped.size()
                                + " dropped and " + held + " went into the inventory");
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The showcase
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_huntsman_trophy_showcase", timeoutTicks = 100)
    public static void showcasePlacesFive(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos spot = new BlockPos(SHOWCASE_X, 0, SHOWCASE_Z);
        List<BlockPos> placed = new ArrayList<>();
        try
        {
            int feet = surface(overworld, spot);
            int result = runShowcase(server, overworld, spot, feet);
            List<BlockPos> row = findTrophies(overworld, spot);
            placed.addAll(row);
            checkRow(overworld, row, result, "first run");
            result = runShowcase(server, overworld, spot, feet);
            row = findTrophies(overworld, spot);
            placed.addAll(row);
            checkRow(overworld, row, result, "second run (it should replace the first row)");
        }
        finally
        {
            for (BlockPos trophy : placed)
            {
                clearShowcase(overworld, trophy);
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** The Huntsman in his Alpha Moose's saddle in the middle of the arena. */
    private static Huntsman pair(GameTestHelper helper)
    {
        Huntsman h = HuntsmanPair.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (h == null || HuntFight.mooseOf(h) == null)
        {
            throw new GameTestAssertException("couldn't make the Huntsman in his Alpha Moose's saddle");
        }
        return h;
    }

    /** Hits {@code mob} (its last hit's cool-down cleared so every hit counts). */
    private static void hit(HuntMob mob, DamageSource source, float amount)
    {
        mob.invulnerableTime = 0;
        mob.hurt(source, amount);
    }

    /** The Huntsman trophy stacks in a player's inventory. */
    private static List<ItemStack> trophiesIn(ServerPlayer player)
    {
        List<ItemStack> found = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.HUNTSMAN_TROPHY.get()))
            {
                found.add(stack);
            }
        }
        return found;
    }

    /** Every Huntsman trophy item lying in the test's arena. */
    private static List<ItemEntity> trophiesNear(GameTestHelper helper)
    {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(ARENA_SIZE, ARENA_HEIGHT, ARENA_SIZE)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area.inflate(1.0D),
                item -> item.isAlive() && item.getItem().is(ModItems.HUNTSMAN_TROPHY.get()));
    }

    /** Runs the showcase at {@code column}, feet at {@code feetY}, looking south (the trophies face north). */
    private static int runShowcase(MinecraftServer server, ServerLevel level, BlockPos column, int feetY)
    {
        var source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, feetY, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, SHOWCASE_COMMAND);
    }

    /** The first free block above the ground at {@code column} (its chunk is loaded first). */
    private static int surface(ServerLevel level, BlockPos column)
    {
        level.getChunkAt(column);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
    }

    /** Every Huntsman trophy round the showcase spot, from the bottom of the world to its top. */
    private static List<BlockPos> findTrophies(ServerLevel level, BlockPos spot)
    {
        List<BlockPos> found = new ArrayList<>();
        BlockPos from = spot.offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS).atY(level.getMinBuildHeight());
        BlockPos to = spot.offset(SEARCH_RADIUS, 0, SEARCH_RADIUS).atY(level.getMaxBuildHeight() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(from, to))
        {
            if (level.getBlockState(pos).is(ModBlocks.HUNTSMAN_TROPHY.get()))
            {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /** Exactly one trophy per design, each on a pedestal, facing north (toward the command), with its sign. */
    private static void checkRow(ServerLevel level, List<BlockPos> row, int result, String when)
    {
        int count = HuntsmanTrophies.count();
        if (result != count)
        {
            throw new GameTestAssertException(when + ": the command returned " + result + ", expected " + count);
        }
        if (row.size() != count)
        {
            throw new GameTestAssertException(when + ": found " + row.size() + " Huntsman trophies, expected " + count);
        }
        Set<Integer> designs = new HashSet<>();
        for (BlockPos pos : row)
        {
            BlockState state = level.getBlockState(pos);
            int design = state.getValue(HuntsmanTrophyBlock.DESIGN);
            designs.add(design);
            if (state.getValue(HuntsmanTrophyBlock.FACING) != Direction.NORTH)
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + " faces "
                        + state.getValue(HuntsmanTrophyBlock.FACING) + ", not north (toward the command)");
            }
            if (level.getBlockState(pos.below()).isAir())
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + " has no pedestal under it");
            }
            BlockPos sign = pos.below().north();
            if (!(level.getBlockState(sign).getBlock() instanceof WallSignBlock)
                    || !(level.getBlockEntity(sign) instanceof SignBlockEntity text)
                    || !text.getFrontText().getMessage(0, false).getString().equals(SIGN_NUMBER + design))
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + " has no \"" + SIGN_NUMBER + design
                        + "\" sign on its pedestal's front");
            }
        }
        if (designs.size() != count)
        {
            throw new GameTestAssertException(when + ": the row should show each design once, but shows " + designs);
        }
    }

    /** Removes a showcase trophy with its pedestal and sign. */
    private static void clearShowcase(ServerLevel level, BlockPos trophy)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        level.setBlock(trophy.below().north(), air, Block.UPDATE_CLIENTS);
        level.setBlock(trophy, air, Block.UPDATE_CLIENTS);
        level.setBlock(trophy.below(), air, Block.UPDATE_CLIENTS);
    }
}
