package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rimefall.Moose;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Headless checks of Rimefall's crops ({@link HunterCrops}): Frostroot and Snow Lichen stay in a sealed dark box where a
 * carrot pops off (they're hardy: the farm igloos' templates are put down before their lanterns are lit); a crop gives
 * what plants it, more when grown; the two-block Rimeberry Bush grows up into its second block, ripens, is picked from
 * either half (both halves' berries, then back to age 1), breaks as one, can't grow up into a block, and stands on snow
 * but not stone; Rimeberries breed Rimehorns, Snow Lichen breeds moose (a bull and a cow).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HunterCropsGameTest
{
    /** Where the tests build: far from spawn and the other tests, in the air. */
    private static final BlockPos AT = new BlockPos(-47000, 200, 43000);
    private static final int DARK_SETTLE_TICKS = 40;
    /** Under this a crop can't stay put unless it's hardy (CropBlock.canSurvive). */
    private static final int CROP_SURVIVES_LIGHT = 8;
    /** The longest a test waits for its far-off land's creatures to load (ticks: the test server can run several times
     * faster than the game, and the loading goes at its own pace, so this is generous; once 400 ticks proved too few
     * and the berries, dropped before the land was ready, were gone). */
    private static final int READY_TICKS = 4000;

    private HunterCropsGameTest() {}

    private static ServerLevel world(GameTestHelper helper, BlockPos at)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        ChunkPos c = new ChunkPos(at);
        level.setChunkForced(c.x, c.z, true);
        level.getChunk(c.x, c.z);
        return level;
    }

    private static void release(ServerLevel level, BlockPos at)
    {
        ChunkPos c = new ChunkPos(at);
        level.setChunkForced(c.x, c.z, false);
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_crops_dark", timeoutTicks = DARK_SETTLE_TICKS + 100)
    public static void hardyCropsStayInTheDark(GameTestHelper helper)
    {
        BlockPos base = AT;
        ServerLevel level = world(helper, base);
        // a box of stone sealed all round: inside, three farmland cells, a carrot, a Frostroot and a Snow Lichen on them
        for (int x = -1; x <= 3; x++)
        {
            for (int y = -1; y <= 2; y++)
            {
                for (int z = -1; z <= 1; z++)
                {
                    level.setBlock(base.offset(x, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        BlockState[] crops = {Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7),
                ModBlocks.FROSTROOTS.get().defaultBlockState().setValue(CropBlock.AGE, 7),
                ModBlocks.SNOW_LICHEN.get().defaultBlockState().setValue(CropBlock.AGE, 7)};
        for (int x = 0; x < 3; x++)
        {
            level.setBlock(base.offset(x, 0, 0), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
            level.setBlock(base.offset(x, 1, 0), crops[x], Block.UPDATE_CLIENTS);
        }
        helper.runAfterDelay(DARK_SETTLE_TICKS, () ->
        {
            for (int x = 0; x < 3; x++)
            {
                int light = level.getRawBrightness(base.offset(x, 1, 0), 0);
                if (light >= CROP_SURVIVES_LIGHT)
                {
                    throw new GameTestAssertException("the sealed box should be dark, but it has light " + light);
                }
            }
            // poke each crop: a block beside it changes, so the game asks whether it can stay
            for (int x = 0; x < 3; x++)
            {
                level.setBlock(base.offset(x, 1, -1), Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
            boolean carrotGone = !level.getBlockState(base.offset(0, 1, 0)).is(Blocks.CARROTS);
            boolean rootStays = level.getBlockState(base.offset(1, 1, 0)).is(ModBlocks.FROSTROOTS.get());
            boolean lichenStays = level.getBlockState(base.offset(2, 1, 0)).is(ModBlocks.SNOW_LICHEN.get());
            release(level, base);
            if (!carrotGone)
            {
                throw new GameTestAssertException("a carrot in the dark box should have popped off (the box isn't dark)");
            }
            if (!rootStays || !lichenStays)
            {
                throw new GameTestAssertException("Frostroot and Snow Lichen should stay in the dark: Frostroot "
                        + (rootStays ? "stayed" : "popped off") + ", Snow Lichen " + (lichenStays ? "stayed" : "popped off"));
            }
            helper.succeed();
        });
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_crops_drops", timeoutTicks = 40)
    public static void cropsGiveWhatPlantsThem(GameTestHelper helper)
    {
        BlockPos at = AT.offset(20, 0, 0);
        ServerLevel level = world(helper, at);
        level.setBlock(at.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_CLIENTS);
        Object[][] crops = {{ModBlocks.FROSTROOTS.get(), ModItems.FROSTROOT.get()},
                {ModBlocks.SNOW_LICHEN.get(), ModItems.SNOW_LICHEN.get()}};
        for (Object[] crop : crops)
        {
            Block block = (Block) crop[0];
            Item item = (Item) crop[1];
            int young = count(Block.getDrops(block.defaultBlockState().setValue(CropBlock.AGE, 0), level, at, null), item);
            int grown = count(Block.getDrops(block.defaultBlockState().setValue(CropBlock.AGE, 7), level, at, null), item);
            if (young != 1 || grown < 1)
            {
                throw new GameTestAssertException(block.getName().getString() + " should give back 1 when young and at "
                        + "least 1 when grown, but gave " + young + " and " + grown);
            }
            if (block.asItem() != item)
            {
                throw new GameTestAssertException(block.getName().getString() + " should be planted with " + item
                        + ", not " + block.asItem());
            }
        }
        release(level, at);
        helper.succeed();
    }

    private static int count(List<ItemStack> drops, Item item)
    {
        int n = 0;
        for (ItemStack stack : drops)
        {
            if (!stack.is(item))
            {
                throw new GameTestAssertException("a crop dropped " + stack + ", not " + item);
            }
            n += stack.getCount();
        }
        return n;
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_crops_bush", timeoutTicks = READY_TICKS + 40)
    public static void rimeberryBushGrowsTallAndIsPickedFromEitherHalf(GameTestHelper helper)
    {
        BlockPos lower = AT.offset(40, 0, 0);
        ServerLevel level = world(helper, lower);
        // the berries it drops are items: they're only kept once the land's creatures are loaded
        whenReady(helper, level, lower, () -> pickBush(helper, level, lower));
    }

    /** Waits until the land at {@code at} has its creatures loaded and ticking, then runs {@code then} (never before:
     * if it isn't ready by the test's timeout, the test fails saying so). */
    private static void whenReady(GameTestHelper helper, ServerLevel level, BlockPos at, Runnable then)
    {
        ChunkPos c = new ChunkPos(at);
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    if (!level.areEntitiesLoaded(c.toLong()) || !level.isPositionEntityTicking(at))
                    {
                        helper.fail("the land at " + at.toShortString() + " isn't ready yet"); // keeps waiting
                    }
                })
                .thenExecute(then);
    }

    private static void pickBush(GameTestHelper helper, ServerLevel level, BlockPos lower)
    {
        BlockPos upper = lower.above();
        RimeberryBushBlock bush = (RimeberryBushBlock) ModBlocks.RIMEBERRY_BUSH.get();
        level.setBlock(lower.below(), Blocks.SNOW_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(lower, bush.defaultBlockState(), Block.UPDATE_ALL);
        if (!level.getBlockState(lower).canSurvive(level, lower))
        {
            throw new GameTestAssertException("a Rimeberry Bush should stand on snow");
        }
        // it grows up into its second block, then both halves grow together
        for (int age = 1; age <= RimeberryBushBlock.MAX_AGE; age++)
        {
            if (!bush.isValidBonemealTarget(level, lower, level.getBlockState(lower), false))
            {
                throw new GameTestAssertException("the bush at age " + (age - 1) + " should take bone meal");
            }
            bush.performBonemeal(level, level.getRandom(), lower, level.getBlockState(lower));
            expectBush(level, lower, upper, age);
        }
        // picked from its upper half: both halves' berries, back to age 1
        Player player = helper.makeMockPlayer();
        BlockState top = level.getBlockState(upper);
        top.use(level, player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(upper), Direction.NORTH,
                upper, false));
        int picked = berriesAround(level, lower);
        if (picked < 4 || picked > 6)
        {
            throw new GameTestAssertException("picking a ripe bush should give 4-6 Rimeberries (both halves), but gave " + picked);
        }
        expectBush(level, lower, upper, 1);
        // breaking either half breaks both
        level.destroyBlock(upper, true);
        if (!level.getBlockState(lower).isAir())
        {
            throw new GameTestAssertException("breaking the upper half should take the lower one too, but it's "
                    + level.getBlockState(lower));
        }
        // a sprout with a block over it can't grow up; on stone a bush can't stand
        BlockPos blocked = lower.offset(3, 0, 0);
        level.setBlock(blocked.below(), Blocks.SNOW_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(blocked, bush.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(blocked.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (bush.isValidBonemealTarget(level, blocked, level.getBlockState(blocked), false))
        {
            throw new GameTestAssertException("a sprout with a block over it shouldn't be able to grow up");
        }
        BlockPos onStone = lower.offset(6, 0, 0);
        level.setBlock(onStone.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (bush.defaultBlockState().canSurvive(level, onStone))
        {
            throw new GameTestAssertException("a Rimeberry Bush shouldn't stand on stone");
        }
        release(level, lower);
        helper.succeed();
    }

    private static void expectBush(ServerLevel level, BlockPos lower, BlockPos upper, int age)
    {
        BlockState lo = level.getBlockState(lower);
        BlockState up = level.getBlockState(upper);
        Block bush = ModBlocks.RIMEBERRY_BUSH.get();
        if (!lo.is(bush) || lo.getValue(RimeberryBushBlock.HALF) != DoubleBlockHalf.LOWER
                || lo.getValue(RimeberryBushBlock.AGE) != age)
        {
            throw new GameTestAssertException("the bush's lower half should be at age " + age + ", but it's " + lo);
        }
        if (!up.is(bush) || up.getValue(RimeberryBushBlock.HALF) != DoubleBlockHalf.UPPER
                || up.getValue(RimeberryBushBlock.AGE) != age)
        {
            throw new GameTestAssertException("the bush's upper half should be at age " + age + ", but it's " + up);
        }
    }

    private static int berriesAround(ServerLevel level, BlockPos at)
    {
        int n = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3)))
        {
            if (item.getItem().is(ModItems.RIMEBERRIES.get()))
            {
                n += item.getItem().getCount();
            }
            item.discard();
        }
        return n;
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_crops_food", timeoutTicks = 20)
    public static void rimeberriesBreedRimehornsAndLichenBreedsMoose(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Rimehorn rimehorn = ModEntities.RIMEHORN.get().create(level);
        if (rimehorn == null || !rimehorn.isFood(new ItemStack(ModItems.RIMEBERRIES.get()))
                || !rimehorn.isFood(new ItemStack(ModItems.FROSTBLOOM.get())))
        {
            throw new GameTestAssertException("Rimeberries and Frostbloom should both breed Rimehorns");
        }
        Moose bull = ModEntities.MOOSE.get().create(level);
        Moose cow = ModEntities.MOOSE.get().create(level);
        Moose otherBull = ModEntities.MOOSE.get().create(level);
        if (bull == null || cow == null || otherBull == null)
        {
            throw new GameTestAssertException("couldn't make the moose");
        }
        if (!bull.isFood(new ItemStack(ModItems.SNOW_LICHEN.get())) || !bull.isFood(new ItemStack(ModItems.FROSTBLOOM.get())))
        {
            throw new GameTestAssertException("Snow Lichen and Frostbloom should both breed moose");
        }
        bull.setBull(true);
        otherBull.setBull(true);
        cow.setBull(false);
        for (Moose m : List.of(bull, cow, otherBull))
        {
            m.setInLoveTime(600);
        }
        if (!bull.canMate(cow) || bull.canMate(otherBull))
        {
            throw new GameTestAssertException("a bull should mate with a cow (" + bull.canMate(cow) + ") but not with "
                    + "another bull (" + bull.canMate(otherBull) + ")");
        }
        helper.succeed();
    }
}
