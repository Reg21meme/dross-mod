package com.reg21meme.dross.boss.huntsman.folk;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.rimefall.PondSkater;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless tests (GameTest) for the hunter villagers ({@link HunterFolk}; CLAUDE.md step 14), in the Cinder Colossus
 * tests' arena ({@link ColossusGameTest#ARENA}) with stand-in players:
 * <ul>
 *   <li>every calling is its own entity type with its spawn egg, its health (warriors 25-30, workers 20, the elder 50)
 *       and its damage;</li>
 *   <li>killing one turns every villager near on the killer (for {@link FolkTuning#VILLAGE_ANGER_TICKS});</li>
 *   <li>hit the elder and he stays angry while you're near, and forgives you once you've been away 10 minutes;</li>
 *   <li>a Pond Skater and a villager go for each other;</li>
 *   <li>right-click one and it talks (a line and its talk animation), but not while it's angry at you;</li>
 *   <li>{@code /dross showcase hunters} lines up one of each calling, frozen, and its controls play their animations;
 *       running it again replaces them.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HunterFolkGameTest
{
    private static final String BATCH = "dross_hunter_folk";
    private static final int TIMEOUT = ColossusGameTest.ARENA_WAIT_TICKS + 600;
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    /** Where the showcase test lines them up (far from everything else). */
    private static final BlockPos SHOWCASE_AT = new BlockPos(-47000, 0, 43500);
    private static final int SHOWCASE_CHUNKS = 2;
    private static final long LOAD_WAIT_MILLIS = 60_000L;

    private HunterFolkGameTest() {}

    private static HunterFolk spawn(GameTestHelper helper, FolkRole role, BlockPos at)
    {
        ServerLevel level = helper.getLevel();
        HunterFolk folk = ModEntities.HUNTER_FOLK.get(role).get().create(level);
        if (folk == null)
        {
            throw new GameTestAssertException("couldn't make a " + role.typeName());
        }
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        folk.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        level.addFreshEntity(folk);
        return folk;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper, String name, BlockPos at)
    {
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, name, at, 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void everyCallingIsItsOwnMob(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HunterFolkGameTest::everyCallingIsItsOwnMob))
        {
            return;
        }
        Set<EntityType<?>> types = new HashSet<>();
        for (FolkRole role : FolkRole.values())
        {
            HunterFolk folk = spawn(helper, role, MIDDLE);
            if (folk.role() != role || !types.add(folk.getType()))
            {
                throw new GameTestAssertException("the " + role.key + " should be its own entity type, " + role.typeName());
            }
            double health = folk.getAttributeValue(Attributes.MAX_HEALTH);
            boolean right = switch (role.kind)
            {
                case WARRIOR -> health >= 25.0D && health <= 30.0D;
                case WORKER -> health == 20.0D;
                case ELDER -> health == 50.0D;
            };
            if (!right || folk.getHealth() != (float) health)
            {
                throw new GameTestAssertException("the " + role.key + " has " + health + " health (warriors 25-30, workers "
                        + "20, the elder 50)");
            }
            if (folk.getAttributeValue(Attributes.ATTACK_DAMAGE) != role.damage)
            {
                throw new GameTestAssertException("the " + role.key + " hits for " + folk.getAttributeValue(Attributes.ATTACK_DAMAGE));
            }
            if (!ModItems.HUNTER_FOLK_SPAWN_EGGS.containsKey(role))
            {
                throw new GameTestAssertException("the " + role.key + " has no spawn egg");
            }
            if (role.shows.stream().anyMatch(gear -> !FolkRole.GEAR.contains(gear)))
            {
                throw new GameTestAssertException("the " + role.key + " shows gear the model hasn't got: " + role.shows);
            }
            folk.discard();
        }
        helper.succeed();
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void killingOneTurnsTheVillage(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HunterFolkGameTest::killingOneTurnsTheVillage))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivalPlayer(helper, "folk_killer", MIDDLE.offset(-6, 0, -6));
        ServerPlayer other = survivalPlayer(helper, "folk_bystander", MIDDLE.offset(6, 0, -6));
        List<ServerPlayer> players = List.of(player, other);
        HunterFolk dead = spawn(helper, FolkRole.FARMER, MIDDLE);
        List<HunterFolk> near = new ArrayList<>();
        near.add(spawn(helper, FolkRole.SPEARMAN, MIDDLE.offset(4, 0, 4)));
        near.add(spawn(helper, FolkRole.ARCHER, MIDDLE.offset(-4, 0, 5)));
        near.add(spawn(helper, FolkRole.ELDER, MIDDLE.offset(5, 0, -2)));
        try
        {
            for (HunterFolk h : near)
            {
                if (h.isAngryAt(player))
                {
                    throw new GameTestAssertException("the " + h.role().key + " is angry at a player before anything happened");
                }
            }
            dead.hurt(level.damageSources().playerAttack(player), 1000.0F);
            if (dead.isAlive())
            {
                throw new GameTestAssertException("the farmer should be dead");
            }
            for (HunterFolk h : near)
            {
                if (!h.isAngryAt(player) || h.getTarget() != player)
                {
                    throw new GameTestAssertException("killing the farmer should turn the " + h.role().key
                            + " on his killer (angry: " + h.isAngryAt(player) + ", target: " + h.getTarget() + ")");
                }
                if (h.isAngryAt(other))
                {
                    throw new GameTestAssertException("the " + h.role().key + " is angry at a player who did nothing");
                }
            }
        }
        finally
        {
            near.forEach(HunterFolk::discard);
            dead.discard();
            ColossusGameTest.leaveAll(players);
        }
        helper.succeed();
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void theElderForgives(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HunterFolkGameTest::theElderForgives))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivalPlayer(helper, "elder_hitter", MIDDLE.offset(0, 0, -4));
        HunterFolk elder = spawn(helper, FolkRole.ELDER, MIDDLE);
        try
        {
            elder.hurt(level.damageSources().playerAttack(player), 2.0F);
            if (!elder.isAngryAt(player))
            {
                throw new GameTestAssertException("hit, the elder should be angry at you");
            }
            // near him, however long, he stays angry
            elder.tickAnger(FolkTuning.ELDER_FORGIVE_TICKS * 2);
            if (!elder.isAngryAt(player))
            {
                throw new GameTestAssertException("the elder forgave a player who stayed near him");
            }
            // away, he's still angry a moment before 10 minutes, and forgives at 10
            elder.passTimeAway(FolkTuning.ELDER_FORGIVE_TICKS - FolkTuning.ANGER_CHECK_TICKS);
            if (!elder.isAngryAt(player))
            {
                throw new GameTestAssertException("the elder forgave you before " + FolkTuning.ELDER_FORGIVE_TICKS + " ticks away");
            }
            elder.passTimeAway(FolkTuning.ANGER_CHECK_TICKS);
            if (elder.isAngryAt(player))
            {
                throw new GameTestAssertException("the elder should forgive you after " + FolkTuning.ELDER_FORGIVE_TICKS
                        + " ticks away");
            }
        }
        finally
        {
            elder.discard();
            ColossusGameTest.leaveAll(List.of(player));
        }
        helper.succeed();
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void skatersAndHuntersHuntEachOther(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HunterFolkGameTest::skatersAndHuntersHuntEachOther))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        HunterFolk hunter = spawn(helper, FolkRole.SPEARMAN, MIDDLE.offset(-4, 0, 0));
        PondSkater skater = ModEntities.POND_SKATER.get().create(level);
        if (skater == null)
        {
            throw new GameTestAssertException("couldn't make a Pond Skater");
        }
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(4, 0, 0)));
        skater.moveTo(at.x, at.y, at.z, 90.0F, 0.0F);
        level.addFreshEntity(skater);
        List<Mob> both = List.of(hunter, skater);
        helper.succeedWhen(() ->
        {
            if (!(hunter.getTarget() instanceof PondSkater) || !(skater.getTarget() instanceof HunterFolk))
            {
                helper.fail("the spearman goes for " + hunter.getTarget() + ", the skater for " + skater.getTarget());
            }
            both.forEach(Mob::discard);
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void theyTalkUnlessAngry(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HunterFolkGameTest::theyTalkUnlessAngry))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivalPlayer(helper, "folk_talker", MIDDLE.offset(0, 0, -2));
        HunterFolk elder = spawn(helper, FolkRole.ELDER, MIDDLE);
        try
        {
            player.interactOn(elder, InteractionHand.MAIN_HAND);
            if (elder.talks() != 1 || elder.action() != FolkAnims.TALK)
            {
                throw new GameTestAssertException("right-clicked, the elder should talk (talked " + elder.talks()
                        + " times, playing " + elder.action() + ")");
            }
            elder.hurt(level.damageSources().playerAttack(player), 1.0F);
            elder.tickCount += FolkTuning.TALK_PAUSE;
            player.interactOn(elder, InteractionHand.MAIN_HAND);
            if (elder.talks() != 1)
            {
                throw new GameTestAssertException("angry, the elder shouldn't talk to you");
            }
        }
        finally
        {
            elder.discard();
            ColossusGameTest.leaveAll(List.of(player));
        }
        helper.succeed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_folk_showcase", timeoutTicks = 4000)
    public static void showcaseLinesUpEveryCalling(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        ChunkPos middle = new ChunkPos(SHOWCASE_AT);
        List<ChunkPos> forced = new ArrayList<>();
        for (int dx = -SHOWCASE_CHUNKS; dx <= SHOWCASE_CHUNKS; dx++)
        {
            for (int dz = -SHOWCASE_CHUNKS; dz <= SHOWCASE_CHUNKS; dz++)
            {
                ChunkPos c = new ChunkPos(middle.x + dx, middle.z + dz);
                level.setChunkForced(c.x, c.z, true);
                level.getChunk(c.x, c.z);
                forced.add(c);
            }
        }
        long deadline = net.minecraft.Util.getMillis() + LOAD_WAIT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    // every chunk the showcase stands in, not only the spot's own (the villagers stand a chunk
                    // further on, and a search skips the creatures of a chunk still loading)
                    boolean ready = forced.stream().allMatch(c -> level.areEntitiesLoaded(c.toLong())
                            && level.isPositionEntityTicking(c.getMiddleBlockPosition(SHOWCASE_AT.getY())));
                    if (!ready && net.minecraft.Util.getMillis() < deadline)
                    {
                        helper.fail("its land is still loading");
                    }
                })
                .thenExecute(() ->
                {
                    try
                    {
                        int feet = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SHOWCASE_AT.getX(), SHOWCASE_AT.getZ());
                        MinecraftServer server = level.getServer();
                        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                                .withPosition(new Vec3(SHOWCASE_AT.getX() + 0.5D, feet, SHOWCASE_AT.getZ() + 0.5D))
                                .withRotation(new Vec2(0.0F, 0.0F)).withSuppressedOutput();
                        if (server.getCommands().performPrefixedCommand(source, "dross showcase hunters") != FolkRole.values().length)
                        {
                            throw new GameTestAssertException("/dross showcase hunters didn't line up all "
                                    + FolkRole.values().length + " callings");
                        }
                        List<HunterFolk> first = lined(level);
                        server.getCommands().performPrefixedCommand(source, "dross showcase hunters attack");
                        for (HunterFolk folk : first)
                        {
                            expectPlaying(folk, folk.role().shoots() ? FolkAnims.QUICK_SHOT : folk.role().attack.anim);
                        }
                        server.getCommands().performPrefixedCommand(source, "dross showcase hunters");
                        List<HunterFolk> second = lined(level);
                        if (first.stream().anyMatch(HunterFolk::isAlive))
                        {
                            throw new GameTestAssertException("running it again should replace the first ones");
                        }
                        second.forEach(HunterFolk::discard);
                    }
                    finally
                    {
                        forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
                    }
                })
                .thenSucceed();
    }

    /** The showcase villagers near the showcase spot: exactly one of each calling, frozen. */
    private static List<HunterFolk> lined(ServerLevel level)
    {
        List<HunterFolk> found = level.getEntitiesOfClass(HunterFolk.class, new AABB(SHOWCASE_AT).inflate(32.0D, 400.0D, 32.0D),
                HunterFolk::isShowcase);
        Set<FolkRole> roles = EnumSet.noneOf(FolkRole.class);
        for (HunterFolk folk : found)
        {
            if (!roles.add(folk.role()) || !folk.isNoAi())
            {
                throw new GameTestAssertException("the showcase should have one frozen " + folk.role().key + ", it has more "
                        + "or it isn't frozen");
            }
        }
        if (roles.size() != FolkRole.values().length)
        {
            throw new GameTestAssertException("the showcase has " + roles + ", not every calling");
        }
        return found;
    }

    private static void expectPlaying(HunterFolk folk, RimefallAnims.Anim anim)
    {
        if (folk.action() != anim)
        {
            throw new GameTestAssertException("the " + folk.role().key + " should play " + anim.name() + ", it plays "
                    + (folk.action() == null ? "nothing" : folk.action().name()));
        }
    }
}
