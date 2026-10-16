package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophies;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyBlock;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyBlockEntity;
import com.reg21meme.dross.boss.trophy.TrophyRewards;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.MIDDLE;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.joinPlayer;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.leaveAll;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.waitForArena;

/**
 * Headless tests (GameTests) for the Rat King trophies ({@code .\gradlew.bat runGameTestServer}), in the Cinder Colossus
 * tests' arena ({@code ColossusGameTest.ARENA}, 17 x 17 blocks). Each runs in its own batch.
 * <ul>
 *   <li>{@link #rightClickCyclesTrophyDesign}: right-clicking a placed trophy turns it 1, 2 ... 5, then 1 again,
 *       still facing the same way (holding a block, which isn't placed; then with an empty hand).</li>
 *   <li>{@link #breakingDropsShownDesign}: broken, it drops itself showing the same design (and so does pick-block);
 *       with the block under it gone it pops off, also as its design.</li>
 *   <li>{@link #trophyForEveryPlayerInRange}: beaten, he gives one trophy of {@link RatKingTuning#TROPHY_DROP_DESIGN}
 *       to the killer and every player within 40 blocks, straight into their inventories (nothing dropped), and none
 *       to a player further away.</li>
 *   <li>{@link #trophyDropsInFrontWhenInventoryFull}: a player with a full inventory gets it dropped a block in front
 *       (at their feet when facing a block), a dead one where they died; each is kept for its player, never despawns
 *       and shrugs off fire, lava, explosions and cactus.</li>
 *   <li>{@link #noTrophyForKillCommand}: removed with {@code /kill}, he gives no trophy.</li>
 *   <li>{@link #showcasePlacesFive}: {@code /dross showcase trophies ratking} places the five in a row, facing the
 *       command, each on a pedestal with its "No. N" sign; running it again replaces the row.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RatKingTrophyGameTest
{
    /** The arena's size (ColossusGameTest's), for finding dropped trophies. */
    private static final int ARENA_SIZE = 17;
    private static final int ARENA_HEIGHT = 11;
    /** Where the king lies when he's beaten, facing east. */
    private static final BlockPos KING_AT = MIDDLE;
    private static final float YAW_EAST = -90.0F;
    private static final float YAW_WEST = 90.0F;
    private static final float HUGE_HIT = 1000.0F;
    private static final float SMALL_HIT = 10.0F;
    /** Far beyond his reward's 40 blocks. */
    private static final int FAR = 45;
    /** A dropped trophy lies (horizontally) this close to where it should be (blocks). */
    private static final double DROP_SPOT_TOLERANCE = 0.3D;
    /** An item's age that never counts up (it never despawns). */
    private static final int NEVER_DESPAWNS_AGE = -32768;
    /** The design broken in the break test, and the one that pops off. */
    private static final int BROKEN_DESIGN = 3;
    private static final int POPPED_DESIGN = 4;

    /** Where the showcase test runs: far from spawn, the other tests and the Colossus's trophy showcase test. */
    private static final int SHOWCASE_X = 2000;
    private static final int SHOWCASE_Z = -2000;
    /** Trophies are looked for this many blocks round the showcase spot (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 20;
    private static final String SHOWCASE_COMMAND = "dross showcase trophies ratking";
    private static final String SIGN_NUMBER = "No. ";

    private RatKingTrophyGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The block
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_trophy_cycle", timeoutTicks = 40)
    public static void rightClickCyclesTrophyDesign(GameTestHelper helper)
    {
        Direction facing = Direction.EAST;
        helper.setBlock(MIDDLE, RatKingTrophies.get(1).blockState(facing));
        Player player = helper.makeMockPlayer();
        // Holding a block: the trophy still turns, and the block isn't placed against it.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        int expected = 1;
        List<Integer> seen = new ArrayList<>();
        for (int click = 1; click <= RatKingTrophies.count(); click++)
        {
            if (click == 3)
            {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // an empty hand turns it too
            }
            helper.useBlock(MIDDLE, player);
            expected = expected % RatKingTrophies.count() + 1;
            BlockState state = helper.getBlockState(MIDDLE);
            if (!state.is(ModBlocks.RAT_KING_TROPHY.get()))
            {
                helper.fail("click " + click + ": the trophy is gone (" + state + ")");
                return;
            }
            int design = state.getValue(RatKingTrophyBlock.DESIGN);
            seen.add(design);
            if (design != expected)
            {
                helper.fail("click " + click + ": the trophy should show design " + expected + ", but it shows " + design);
            }
            if (state.getValue(RatKingTrophyBlock.FACING) != facing)
            {
                helper.fail("click " + click + ": the trophy should still face " + facing + ", but it faces "
                        + state.getValue(RatKingTrophyBlock.FACING));
            }
            if (!(helper.getBlockEntity(MIDDLE) instanceof RatKingTrophyBlockEntity trophy) || trophy.design() != expected)
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

    @GameTest(template = ARENA, batch = "dross_ratking_trophy_break", timeoutTicks = ARENA_WAIT_TICKS + 60)
    public static void breakingDropsShownDesign(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingTrophyGameTest::breakingDropsShownDesign))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos broken = MIDDLE.offset(-3, 0, 0);
        BlockState state = RatKingTrophies.get(BROKEN_DESIGN).blockState(Direction.SOUTH);
        helper.setBlock(broken, state);
        // Pick-block (middle click in creative) gives the same design.
        ItemStack picked = state.getBlock().getCloneItemStack(level, helper.absolutePos(broken), state);
        if (!picked.is(ModItems.RAT_KING_TROPHY.get()) || RatKingTrophies.designOf(picked) != BROKEN_DESIGN)
        {
            helper.fail("pick-block should give a design " + BROKEN_DESIGN + " trophy, but gave " + picked
                    + " (design " + RatKingTrophies.designOf(picked) + ")");
            return;
        }
        level.destroyBlock(helper.absolutePos(broken), true);
        // On a block that goes: it pops off.
        BlockPos popped = MIDDLE.offset(3, 0, 0);
        helper.setBlock(popped, RatKingTrophies.get(POPPED_DESIGN).blockState(Direction.NORTH));
        helper.setBlock(popped.below(), Blocks.AIR);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ItemEntity> items = trophiesNear(helper);
                    items.forEach(ItemEntity::discard);
                    helper.setBlock(popped.below(), Blocks.SMOOTH_STONE);
                    helper.assertBlockNotPresent(ModBlocks.RAT_KING_TROPHY.get(), broken);
                    helper.assertBlockNotPresent(ModBlocks.RAT_KING_TROPHY.get(), popped);
                    List<Integer> designs = new ArrayList<>();
                    for (ItemEntity item : items)
                    {
                        designs.add(RatKingTrophies.designOf(item.getItem()));
                    }
                    designs.sort(Integer::compare);
                    if (!designs.equals(List.of(BROKEN_DESIGN, POPPED_DESIGN)))
                    {
                        helper.fail("the broken trophy should drop design " + BROKEN_DESIGN + " and the one that popped off "
                                + "design " + POPPED_DESIGN + ", but these dropped: " + designs);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // His reward
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_trophy_reward", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void trophyForEveryPlayerInRange(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingTrophyGameTest::trophyForEveryPlayerInRange))
        {
            return;
        }
        RatKing king = spawnKing(helper);
        if (king == null)
        {
            return;
        }
        ServerPlayer killer = joinPlayer(helper, "rat-trophy-killer", MIDDLE.offset(4, 0, 0), 0.0F);
        ServerPlayer near = joinPlayer(helper, "rat-trophy-near", MIDDLE.offset(-5, 0, 4), 0.0F);
        ServerPlayer far = joinPlayer(helper, "rat-trophy-far", MIDDLE.offset(FAR, 0, 0), 0.0F);
        List<ServerPlayer> players = List.of(killer, near, far);
        if (!beat(helper, king, killer, players))
        {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    leaveAll(players);
                    king.discard();
                    List<ItemEntity> dropped = trophiesNear(helper);
                    if (!dropped.isEmpty())
                    {
                        dropped.forEach(ItemEntity::discard);
                        helper.fail("with room in their inventories nothing should drop, but " + dropped.size()
                                + " trophy item(s) lie on the ground");
                    }
                    for (ServerPlayer player : List.of(killer, near))
                    {
                        List<ItemStack> mine = trophiesIn(player);
                        if (mine.size() != 1 || mine.get(0).getCount() != 1)
                        {
                            helper.fail(player.getScoreboardName() + " should have exactly one trophy in their inventory, "
                                    + "but has " + mine.size() + " stack(s)");
                        }
                        if (RatKingTrophies.designOf(mine.get(0)) != RatKingTuning.TROPHY_DROP_DESIGN)
                        {
                            helper.fail("the trophy should be design " + RatKingTuning.TROPHY_DROP_DESIGN + ", but it's "
                                    + RatKingTrophies.designOf(mine.get(0)));
                        }
                    }
                    if (!trophiesIn(far).isEmpty())
                    {
                        helper.fail("a player " + FAR + " blocks away shouldn't get a trophy");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_trophy_full", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void trophyDropsInFrontWhenInventoryFull(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingTrophyGameTest::trophyDropsInFrontWhenInventoryFull))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = spawnKing(helper);
        if (king == null)
        {
            return;
        }
        // A full inventory, facing east (open floor ahead): dropped a block in front.
        ServerPlayer facingOpen = joinPlayer(helper, "rat-trophy-full", MIDDLE.offset(4, 0, 0), YAW_EAST);
        // A full inventory, facing west into a block: dropped at their feet.
        ServerPlayer facingWall = joinPlayer(helper, "rat-trophy-wall", MIDDLE.offset(-5, 0, 4), YAW_WEST);
        helper.setBlock(MIDDLE.offset(-6, 0, 4), Blocks.STONE);
        // Dead (waiting to respawn): dropped where they died.
        ServerPlayer dead = joinPlayer(helper, "rat-trophy-dead", MIDDLE.offset(0, 0, -6), 0.0F);
        fillInventory(facingOpen);
        fillInventory(facingWall);
        dead.setHealth(0.0F);
        List<ServerPlayer> players = List.of(facingOpen, facingWall, dead);
        Vec3 ahead = facingOpen.position().add(TrophyRewards.DROP_AHEAD, 0.0D, 0.0D);
        if (!beat(helper, king, facingOpen, players))
        {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ItemEntity> trophies = trophiesNear(helper);
                    // Every check first (they need the dropped trophies still in the world), then the clean-up.
                    String problem = null;
                    if (trophies.size() != players.size())
                    {
                        problem = "each of the " + players.size() + " players should get a dropped trophy, but "
                                + trophies.size() + " dropped";
                    }
                    for (ServerPlayer player : players)
                    {
                        if (problem != null)
                        {
                            break;
                        }
                        ItemEntity mine = trophyOf(trophies, player);
                        Vec3 expected = player == facingOpen ? ahead : player.position();
                        if (!trophiesIn(player).isEmpty())
                        {
                            problem = player.getScoreboardName() + " has a full inventory or is dead, so the trophy "
                                    + "shouldn't be in their inventory";
                        }
                        else
                        {
                            problem = mine == null ? "no trophy is kept for " + player.getScoreboardName()
                                    : checkTrophy(level, mine, expected, player.getScoreboardName());
                        }
                    }
                    leaveAll(players);
                    king.discard();
                    trophies.forEach(ItemEntity::discard);
                    helper.setBlock(MIDDLE.offset(-6, 0, 4), Blocks.AIR);
                    if (problem != null)
                    {
                        helper.fail(problem);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_trophy_kill", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void noTrophyForKillCommand(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingTrophyGameTest::noTrophyForKillCommand))
        {
            return;
        }
        RatKing king = spawnKing(helper);
        if (king == null)
        {
            return;
        }
        ServerPlayer player = joinPlayer(helper, "rat-trophy-bystander", MIDDLE.offset(4, 0, 0), 0.0F);
        king.startFightNow();
        king.kill();
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ItemEntity> trophies = trophiesNear(helper);
                    int held = trophiesIn(player).size();
                    leaveAll(List.of(player));
                    king.discard();
                    trophies.forEach(ItemEntity::discard);
                    if (!trophies.isEmpty() || held > 0)
                    {
                        helper.fail("a Rat King removed with /kill shouldn't give a trophy, but " + trophies.size()
                                + " dropped and " + held + " went into the inventory");
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The showcase
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_trophy_showcase", timeoutTicks = 100)
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

    /** A fighting Rat King (asleep) in the middle of the arena, facing east. */
    @Nullable
    private static RatKing spawnKing(GameTestHelper helper)
    {
        RatKing king = RatKing.spawnBoss(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(KING_AT)), YAW_EAST);
        if (king == null)
        {
            helper.fail("the Rat King couldn't be spawned");
        }
        return king;
    }

    /** Wakes him, skips to the end of his Rage and has the killer finish him. False (and the test failed) if he lived. */
    private static boolean beat(GameTestHelper helper, RatKing king, ServerPlayer killer, List<ServerPlayer> players)
    {
        king.startFightNow();
        king.skipToRage();
        king.setHealth(SMALL_HIT);
        king.hurt(helper.getLevel().damageSources().playerAttack(killer), HUGE_HIT);
        if (!king.isDeadOrDying())
        {
            leaveAll(players);
            king.discard();
            helper.fail("a huge hit at the end of his Rage should kill him");
            return false;
        }
        return true;
    }

    /**
     * What's wrong with a player's dropped trophy, or null if it's right: his drop design, lying (horizontally) at
     * {@code expected}, never despawning and shrugging off fire, lava, explosions and cactus.
     */
    @Nullable
    private static String checkTrophy(ServerLevel level, ItemEntity item, Vec3 expected, String name)
    {
        if (RatKingTrophies.designOf(item.getItem()) != RatKingTuning.TROPHY_DROP_DESIGN)
        {
            return "the dropped trophy should be design " + RatKingTuning.TROPHY_DROP_DESIGN;
        }
        double off = Math.hypot(item.getX() - expected.x, item.getZ() - expected.z);
        if (off > DROP_SPOT_TOLERANCE)
        {
            return name + "'s trophy should lie at " + expected + ", but it's " + off + " blocks from there ("
                    + item.position() + ")";
        }
        if (item.getAge() != NEVER_DESPAWNS_AGE)
        {
            return "the trophy should never despawn";
        }
        for (DamageSource source : List.of(level.damageSources().lava(), level.damageSources().inFire(),
                level.damageSources().onFire(), level.damageSources().explosion(null, null), level.damageSources().cactus()))
        {
            if (item.hurt(source, HUGE_HIT) || item.isRemoved())
            {
                return "the trophy shouldn't be hurt by " + source.getMsgId();
            }
        }
        return null;
    }

    /** Fills every slot of a player's inventory with full stacks of dirt (so a trophy can't fit). */
    private static void fillInventory(ServerPlayer player)
    {
        for (int slot = 0; slot < player.getInventory().items.size(); slot++)
        {
            player.getInventory().items.set(slot, new ItemStack(Items.DIRT, Items.DIRT.getMaxStackSize()));
        }
    }

    /** The Rat King trophy stacks in a player's inventory. */
    private static List<ItemStack> trophiesIn(ServerPlayer player)
    {
        List<ItemStack> found = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.RAT_KING_TROPHY.get()))
            {
                found.add(stack);
            }
        }
        return found;
    }

    /** Every Rat King trophy item lying in the test's arena. */
    private static List<ItemEntity> trophiesNear(GameTestHelper helper)
    {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(ARENA_SIZE, ARENA_HEIGHT, ARENA_SIZE)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area.inflate(1.0D),
                item -> item.isAlive() && item.getItem().is(ModItems.RAT_KING_TROPHY.get()));
    }

    /** The dropped trophy kept for this player, or null. */
    @Nullable
    private static ItemEntity trophyOf(List<ItemEntity> trophies, ServerPlayer player)
    {
        for (ItemEntity item : trophies)
        {
            CompoundTag tag = new CompoundTag();
            item.saveWithoutId(tag);
            UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
            if (player.getUUID().equals(owner))
            {
                return item;
            }
        }
        return null;
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

    /** Every Rat King trophy round the showcase spot, from the bottom of the world to its top. */
    private static List<BlockPos> findTrophies(ServerLevel level, BlockPos spot)
    {
        List<BlockPos> found = new ArrayList<>();
        BlockPos from = spot.offset(-SEARCH_RADIUS, 0, -SEARCH_RADIUS).atY(level.getMinBuildHeight());
        BlockPos to = spot.offset(SEARCH_RADIUS, 0, SEARCH_RADIUS).atY(level.getMaxBuildHeight() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(from, to))
        {
            if (level.getBlockState(pos).is(ModBlocks.RAT_KING_TROPHY.get()))
            {
                found.add(pos.immutable());
            }
        }
        return found;
    }

    /** Exactly one trophy per design, each on a pedestal, facing north (toward the command), with its sign. */
    private static void checkRow(ServerLevel level, List<BlockPos> row, int result, String when)
    {
        int count = RatKingTrophies.count();
        if (result != count)
        {
            throw new GameTestAssertException(when + ": the command returned " + result + ", expected " + count);
        }
        if (row.size() != count)
        {
            throw new GameTestAssertException(when + ": found " + row.size() + " Rat King trophies, expected " + count);
        }
        Set<Integer> designs = new HashSet<>();
        for (BlockPos pos : row)
        {
            BlockState state = level.getBlockState(pos);
            int design = state.getValue(RatKingTrophyBlock.DESIGN);
            designs.add(design);
            if (state.getValue(RatKingTrophyBlock.FACING) != Direction.NORTH)
            {
                throw new GameTestAssertException(when + ": trophy No. " + design + " faces "
                        + state.getValue(RatKingTrophyBlock.FACING) + ", not north (toward the command)");
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
