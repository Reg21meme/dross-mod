package com.reg21meme.dross.enchant;

import com.mojang.authlib.GameProfile;
import com.reg21meme.dross.Dross;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless tests (GameTests) for the risen undead's targeting. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world with {@code /test runall}.
 * They only exist in the development environment: in a normal game nothing here runs.
 *
 * <ul>
 *   <li>{@link #raisingHitKillsTheTarget}: the bug where one swing both raised the undead and killed the mob they were
 *       sent after, and the skeletons kept shooting at the spot where it died.</li>
 *   <li>{@link #targetDiesMidFight}: the normal case (the mob dies a while later) still works: they go after it, then
 *       let go once it's dead. It dies while the skeleton is still walking toward it with its bow drawn (a skeleton
 *       walks toward its target for its first second of seeing it), which once left the skeleton walking to the empty
 *       spot, bow drawn, for up to a few seconds.</li>
 * </ul>
 * After the kill, each test allows {@link #LET_GO_GRACE_TICKS} for them to let go (checked every tick), then checks
 * again once the body is gone. Each test has its own batch, so their arenas never run side by side (stray arrows
 * can't reach the other test).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RisenTargetGameTest
{
    // ---------------------------------------------------------------------------------------------
    // Test setup (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /** The arena: a flat floor, made in memory when the server starts (no structure file needed). */
    private static final String ARENA = "risen_test_arena";
    private static final ResourceLocation ARENA_ID = new ResourceLocation(Dross.MODID, ARENA);
    /** Arena width and depth (blocks). */
    private static final int ARENA_SIZE = 15;
    /** Arena height (blocks, floor included). */
    private static final int ARENA_HEIGHT = 5;

    /**
     * Where the owner stands, and where the pig stands (relative to the arena; y 1 is just above the floor). The pig
     * is 7 blocks away, so the risen undead (rising up to 3 blocks from the owner) have to walk to reach it.
     */
    private static final BlockPos OWNER_POS = new BlockPos(7, 1, 4);
    private static final BlockPos PIG_POS = new BlockPos(7, 1, 11);
    /** How far (blocks) around the owner to look for the risen skeleton's arrows. */
    private static final double ARROW_SEARCH_RADIUS = 48.0D;

    /** Deathforged I: a leather helmet, so the risen undead don't burn in daylight during the test. */
    private static final int DEATHFORGED_LEVEL = 1;
    /** More than enough damage to kill the pig in one hit. */
    private static final float KILLING_DAMAGE = 1000.0F;

    /**
     * After the kill they must let go within this many ticks: half a second. (They make their decisions every other
     * tick, so it normally takes 1 or 2.)
     */
    private static final int LET_GO_GRACE_TICKS = 10;
    /**
     * The "let go" check can't pass earlier than this many ticks after it starts: by then every mob has made at least
     * one full AI decision, so a pass means something.
     */
    private static final int AI_DECISION_TICKS = 2;
    /** Ticks to wait after letting go: more than the 20-tick death animation, so the body is removed by then. */
    private static final int BODY_GONE_TICKS = 30;
    /** In {@link #targetDiesMidFight}: how long they fight the living pig before it's killed. */
    private static final int FIGHT_TICKS = 6;
    private static final int TIMEOUT_TICKS = 200;

    private RisenTargetGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------

    /**
     * One swing raises the undead (Necromancy acts first, while the pig is still alive) and the same swing's damage
     * kills the pig. They must never keep the dead (later removed) pig as their target, and the skeleton must not
     * draw its bow at it.
     */
    @GameTest(template = ARENA, batch = "dross_risen_raising_hit_kills", timeoutTicks = TIMEOUT_TICKS)
    public static void raisingHitKillsTheTarget(GameTestHelper helper)
    {
        ServerPlayer owner = addOwner(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, PIG_POS);

        // Same order as a real swing: raise (target still alive), then the hit lands and kills it, and the owner
        // remembers it as the mob they last hit (the pack AI's "owner's target" rule looks at that).
        raiseTwo(helper, owner, pig);
        Set<UUID> arrowsAtKill = arrowsOf(owner);
        pig.hurt(owner.damageSources().playerAttack(owner), KILLING_DAMAGE);
        owner.setLastHurtMob(pig);
        if (pig.isAlive())
        {
            fail(helper, owner, "the test pig survived the killing hit");
        }

        GameTestSequence sequence = thenLetGo(helper.startSequence(), helper, owner, arrowsAtKill);
        sequence.thenExecuteAfter(BODY_GONE_TICKS, () -> failIfProblem(helper, owner, arrowsAtKill, "after the body was removed"))
                .thenExecute(() -> removeOwner(owner))
                .thenSucceed();
    }

    /** The pig lives for a few ticks: they must go after it, and let go of it once it's dead. */
    @GameTest(template = ARENA, batch = "dross_risen_target_dies_mid_fight", timeoutTicks = TIMEOUT_TICKS)
    public static void targetDiesMidFight(GameTestHelper helper)
    {
        ServerPlayer owner = addOwner(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, PIG_POS);
        raiseTwo(helper, owner, pig);
        Set<UUID> arrowsAtKill = new HashSet<>();

        GameTestSequence sequence = helper.startSequence()
                .thenExecuteAfter(FIGHT_TICKS, () -> {
                    // Unless one of them already killed it, both must be after the pig the owner hit.
                    if (pig.isAlive())
                    {
                        for (Mob mob : risenOf(owner))
                        {
                            if (mob.getTarget() != pig)
                            {
                                fail(helper, owner, "a risen " + name(mob) + " didn't go after the mob the owner hit");
                            }
                        }
                        arrowsAtKill.addAll(arrowsOf(owner));
                        pig.hurt(owner.damageSources().playerAttack(owner), KILLING_DAMAGE);
                    }
                    else
                    {
                        arrowsAtKill.addAll(arrowsOf(owner)); // already killed by them: nothing more should fly
                    }
                });
        thenLetGo(sequence, helper, owner, arrowsAtKill)
                .thenExecuteAfter(BODY_GONE_TICKS, () -> failIfProblem(helper, owner, arrowsAtKill, "after the body was removed"))
                .thenExecute(() -> removeOwner(owner))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Checks
    // ---------------------------------------------------------------------------------------------

    /** Raises a skeleton and a zombie sent after {@code target}, as the Necromancy swing does. */
    private static void raiseTwo(GameTestHelper helper, ServerPlayer owner, LivingEntity target)
    {
        boolean skeleton = RisenUndead.raise(owner, RisenType.SKELETON, DEATHFORGED_LEVEL, target);
        boolean zombie = RisenUndead.raise(owner, RisenType.ZOMBIE, DEATHFORGED_LEVEL, target);
        if (!skeleton || !zombie)
        {
            fail(helper, owner, "the risen undead couldn't be spawned");
        }
    }

    /**
     * Adds "wait until they've let go of the dead pig" to the sequence: checked every tick from
     * {@link #AI_DECISION_TICKS} after it starts, passing as soon as {@link #findProblem} finds nothing, and failing if
     * it still finds something {@link #LET_GO_GRACE_TICKS} after it started.
     */
    private static GameTestSequence thenLetGo(GameTestSequence sequence, GameTestHelper helper, ServerPlayer owner, Set<UUID> arrowsAtKill)
    {
        long[] start = {-1L};
        String[] problem = {null};
        return sequence
                .thenWaitUntil(() -> {
                    if (start[0] < 0L)
                    {
                        start[0] = helper.getTick();
                    }
                    long waited = helper.getTick() - start[0];
                    if (waited < AI_DECISION_TICKS)
                    {
                        throw new GameTestAssertException("waiting for the AI"); // checked again next tick
                    }
                    problem[0] = findProblem(owner, arrowsAtKill);
                    if (problem[0] != null && waited < LET_GO_GRACE_TICKS)
                    {
                        throw new GameTestAssertException(problem[0]); // not yet: checked again next tick
                    }
                })
                .thenExecute(() -> {
                    if (problem[0] != null)
                    {
                        fail(helper, owner, "half a second after the kill: " + problem[0]);
                    }
                });
    }

    private static void failIfProblem(GameTestHelper helper, ServerPlayer owner, Set<UUID> arrowsAtKill, String when)
    {
        String problem = findProblem(owner, arrowsAtKill);
        if (problem != null)
        {
            fail(helper, owner, when + ": " + problem);
        }
    }

    /**
     * What's still wrong, or null if nothing: both risen undead must still be there, neither may target anything
     * dead, dying or removed, neither may still be attacking or have its bow drawn, and no arrows may have been shot
     * since the kill (there's nothing alive left to shoot at).
     */
    @Nullable
    private static String findProblem(ServerPlayer owner, Set<UUID> arrowsAtKill)
    {
        List<Mob> risen = risenOf(owner);
        if (risen.size() != 2)
        {
            return "expected 2 risen undead, found " + risen.size();
        }
        for (Mob mob : risen)
        {
            LivingEntity target = mob.getTarget();
            if (target != null && RisenUndead.isGone(target))
            {
                return "a risen " + name(mob) + " still targets a dead or removed " + name(target);
            }
            if (mob.isAggressive() || mob.isUsingItem())
            {
                return "a risen " + name(mob) + " is still attacking (target: " + (target == null ? "none" : name(target))
                        + ", bow drawn: " + (mob.isUsingItem() ? "yes" : "no") + ")";
            }
        }
        Set<UUID> newArrows = arrowsOf(owner);
        newArrows.removeAll(arrowsAtKill);
        if (!newArrows.isEmpty())
        {
            return "the risen undead shot " + newArrows.size() + " arrow(s) after the kill";
        }
        return null;
    }

    /** Arrows shot by this owner's risen undead (they carry the owner's id), near the owner. */
    private static Set<UUID> arrowsOf(ServerPlayer owner)
    {
        Set<UUID> arrows = new HashSet<>();
        AABB area = owner.getBoundingBox().inflate(ARROW_SEARCH_RADIUS);
        for (AbstractArrow arrow : owner.level().getEntitiesOfClass(AbstractArrow.class, area,
                arrow -> arrow.getPersistentData().hasUUID(RisenUndead.PROJECTILE_OWNER_KEY)
                        && owner.getUUID().equals(arrow.getPersistentData().getUUID(RisenUndead.PROJECTILE_OWNER_KEY))))
        {
            arrows.add(arrow.getUUID());
        }
        return arrows;
    }

    /** This owner's risen undead that are still alive. */
    private static List<Mob> risenOf(ServerPlayer owner)
    {
        List<Mob> list = new ArrayList<>();
        for (Mob mob : RisenUndead.active())
        {
            if (mob.isAlive() && owner.getUUID().equals(RisenUndead.getOwnerId(mob)))
            {
                list.add(mob);
            }
        }
        return list;
    }

    private static String name(LivingEntity entity)
    {
        return entity.getType().getDescription().getString().toLowerCase();
    }

    /** Removes the test owner first (so no fake player is left behind), then fails the test. */
    private static void fail(GameTestHelper helper, ServerPlayer owner, String message)
    {
        removeOwner(owner);
        helper.fail(message);
    }

    // ---------------------------------------------------------------------------------------------
    // The test owner: a stand-in player
    // ---------------------------------------------------------------------------------------------

    /**
     * A stand-in player who owns the test's risen undead. He must be on the server's player list, or his risen undead
     * crumble at once (their owner "logged out"). Vanilla's {@code makeMockServerPlayerInLevel} crashes under Forge
     * (its connection has no network channel), so this one gets an in-memory channel.
     */
    private static ServerPlayer addOwner(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "risen-test-owner"));
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(OWNER_POS));
        owner.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
        level.getServer().getPlayerList().placeNewPlayer(connection, owner);
        if (owner.serverLevel() != level)
        {
            owner.teleportTo(level, pos.x, pos.y, pos.z, 0.0F, 0.0F); // tests run in another dimension
        }
        return owner;
    }

    /** Logs the stand-in out (his risen undead crumble, as for a real player). Safe to call twice. */
    private static void removeOwner(@Nullable ServerPlayer owner)
    {
        if (owner != null && owner.server.getPlayerList().getPlayer(owner.getUUID()) == owner)
        {
            owner.server.getPlayerList().remove(owner);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The arena template
    // ---------------------------------------------------------------------------------------------

    /**
     * GameTests are built on a structure template. Instead of shipping a file, the arena (a smooth stone floor with
     * air above) is made in memory when a development server starts, before the tests are placed.
     */
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

    /** The arena in the structure-file format: its size, a one-block palette, and the floor blocks. */
    private static CompoundTag arenaTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(ARENA_SIZE, ARENA_HEIGHT, ARENA_SIZE));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.SMOOTH_STONE.defaultBlockState()));
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
