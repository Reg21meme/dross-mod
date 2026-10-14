package com.reg21meme.dross.enchant;

import com.mojang.authlib.GameProfile;
import com.reg21meme.dross.Dross;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.GameType;
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
 * After the kill, each test allows {@link #LET_GO_GRACE_TICKS} of the risen undead's own ticks for them to let go
 * (checked every tick), then keeps watching them until {@link #AFTER_BODY_GONE_TICKS} of their ticks after the body
 * is gone. Each test has its own batch, so their arenas never run side by side.
 *
 * <p><b>Steady on a busy machine</b> (R6c: each failed once in another area's full run, and passed in all others).
 * Neither failure was a bug in the risen undead:
 * <ul>
 *   <li>The test server's world is a normal survival world (made from {@code run/server.properties}: monsters on,
 *       normal terrain), and the test arenas lie deep underground (about y -60), in the dark. Monsters spawn in the
 *       caves round a stand-in player and come for him: once a creeper did, and his risen skeleton rightly turned to
 *       defend him ("defend the owner"), which the test took for "still attacking". So the stand-in owner is now in
 *       creative mode (monsters ignore him), and the arena is closed in by barrier walls and a barrier roof (no mob
 *       walks or falls in, no arrow flies out).</li>
 *   <li>A test can start before its chunks have finished loading, and nothing in them moves until then. Once, in a
 *       brand-new world on a busy machine, the skeleton had no target 6 ticks after rising: most likely it hadn't
 *       ticked yet. So nothing is added until the arena has loaded (waited for in real time, see
 *       {@link RisenTestWait}), and every "within so many ticks" counts the risen undead's own ticks.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RisenTargetGameTest
{
    // ---------------------------------------------------------------------------------------------
    // Test setup (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /**
     * The arena: a flat floor inside barrier walls under a barrier roof, made in memory when the server starts (no
     * structure file needed). Also used by {@link DeathforgedGearGameTest} and {@link RisenSurvivorGameTest}, which
     * borrow the stand-in owner below too.
     */
    static final String ARENA = "risen_test_arena";
    private static final ResourceLocation ARENA_ID = new ResourceLocation(Dross.MODID, ARENA);
    /** Arena width and depth (blocks), walls included: the floor inside them is 13 x 13. Also used by {@link RisenSurvivorGameTest}. */
    static final int ARENA_SIZE = 15;
    /** Arena layers: the floor (0), three layers of air inside the walls (1 to 3), the roof (4). */
    private static final int ARENA_HEIGHT = 5;
    /**
     * The relative y the owner and the mobs stand at: the test's structure block is at relative y 0, so the
     * template's floor (its layer 0) is at relative y 1, and y 2 is on top of it. Also used by
     * {@link RisenSurvivorGameTest}.
     */
    static final int STAND_Y = 2;

    /**
     * Where the owner stands, and where the pig stands. The pig is 7 blocks away, so the risen undead (rising up to 3
     * blocks from the owner) have to walk to reach it.
     */
    private static final BlockPos OWNER_POS = new BlockPos(7, STAND_Y, 4);
    private static final BlockPos PIG_POS = new BlockPos(7, STAND_Y, 11);
    /** How far (blocks) around the owner to look for the risen skeleton's arrows. */
    private static final double ARROW_SEARCH_RADIUS = 48.0D;
    /** A failure message lists the other mobs this close (blocks) to the arena, to show what else was about. */
    private static final double NEARBY_RADIUS = 24.0D;

    /** Deathforged I: a leather helmet, so the risen undead don't burn in daylight during the test. */
    private static final int DEATHFORGED_LEVEL = 1;
    /** More than enough damage to kill the pig in one hit. */
    private static final float KILLING_DAMAGE = 1000.0F;

    // ---- Counted in the risen undead's own ticks (each is one step of their AI) ----
    /**
     * In {@link #targetDiesMidFight}: both must go after the pig within this many ticks of rising: half a second. (They
     * decide on their very first tick, then every other tick.)
     */
    private static final int GO_AFTER_GRACE_TICKS = 10;
    /** In {@link #targetDiesMidFight}: how long they fight the living pig before it's killed. */
    private static final int FIGHT_TICKS = 4;
    /** After the kill they must let go within this many ticks: half a second. (It normally takes 1 or 2.) */
    private static final int LET_GO_GRACE_TICKS = 10;
    /**
     * The "let go" check can't pass earlier than this many ticks after the kill: by then each has made at least one
     * full AI decision, so a pass means something.
     */
    private static final int AI_DECISION_TICKS = 2;
    /**
     * They're watched until this many ticks after the dead pig's body is removed (its death animation takes 20 of its
     * own ticks).
     */
    private static final int AFTER_BODY_GONE_TICKS = 10;

    private RisenTargetGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------

    /**
     * One swing raises the undead (Necromancy acts first, while the pig is still alive) and the same swing's damage
     * kills the pig. They must never keep the dead (later removed) pig as their target, and the skeleton must not
     * draw its bow at it.
     */
    @GameTest(template = ARENA, batch = "dross_risen_raising_hit_kills", timeoutTicks = RisenTestWait.TEST_TIMEOUT_TICKS)
    public static void raisingHitKillsTheTarget(GameTestHelper helper)
    {
        Scene scene = new Scene(helper);
        GameTestSequence sequence = scene.thenSetUp(helper.startSequence(), () -> {
            // Same order as a real swing: raise (target still alive), then the hit lands and kills it.
            scene.setUp();
            scene.killPig();
        });
        scene.thenLetGo(sequence).thenSucceed();
    }

    /** The pig lives for a few ticks: they must go after it, and let go of it once it's dead. */
    @GameTest(template = ARENA, batch = "dross_risen_target_dies_mid_fight", timeoutTicks = RisenTestWait.TEST_TIMEOUT_TICKS)
    public static void targetDiesMidFight(GameTestHelper helper)
    {
        Scene scene = new Scene(helper);
        RisenTestWait goAfter = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        RisenTestWait fight = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        GameTestSequence sequence = scene.thenSetUp(helper.startSequence(), scene::setUp)
                // Unless one of them already killed it, both must go after the pig the owner hit...
                .thenWaitUntil(scene.guard.poll(goAfter, scene::lookGoingAfterPig))
                .thenExecute(scene.guard.step(() -> {
                    scene.failIfWrong(goAfter, "within " + GO_AFTER_GRACE_TICKS + " of their ticks of rising: ");
                    scene.mark();
                }))
                // ...and fight it for a few ticks: the skeleton walks toward it with its bow drawn.
                .thenWaitUntil(scene.guard.poll(fight, scene::lookFightingPig))
                .thenExecute(scene.guard.step(() -> {
                    scene.failIfWrong(fight, "while they fought the pig: ");
                    scene.killPig();
                }));
        scene.thenLetGo(sequence).thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // One test's scene: its owner, pig and risen undead, and its checks
    // ---------------------------------------------------------------------------------------------

    private static final class Scene
    {
        private final GameTestHelper helper;
        @Nullable
        private ServerPlayer owner;
        @Nullable
        private Pig pig;
        /** The test's two risen undead: a skeleton and a zombie. */
        private final List<Mob> risen = new ArrayList<>();
        /** Their own tick counts when the current count began ({@link #mark}). */
        private Map<Mob, Integer> marks = Map.of();
        /** Their arrows already flying when the pig died (none may be shot after that). */
        private final Set<UUID> arrowsAtKill = new HashSet<>();
        /** In the last watch: whether the dead pig's body has been removed yet. */
        private boolean bodyGone;
        /** Every step and wait goes through it: the test's first failure is the one it reports. */
        final RisenTestWait.Guard guard = new RisenTestWait.Guard(this::cleanUp);

        Scene(GameTestHelper helper)
        {
            this.helper = helper;
        }

        /**
         * Adds the start of a test: wait until the arena has finished loading (in real time), then a step that checks
         * it did and runs {@code setUp}. Nothing is added to the world before that.
         */
        GameTestSequence thenSetUp(GameTestSequence sequence, Runnable setUp)
        {
            RisenTestWait arena = new RisenTestWait(this.helper, RisenTestWait.ARENA_MILLIS);
            return sequence
                    .thenWaitUntil(this.guard.poll(arena, RisenTestWait.arenaLoaded(this.helper, ARENA_SIZE, STAND_Y)))
                    .thenExecute(this.guard.step(() -> {
                        arena.failIfWrong("");
                        setUp.run();
                    }));
        }

        /** The stand-in owner, the pig (with no AI of its own, so it stays put), and a skeleton and a zombie sent after it. */
        void setUp()
        {
            this.owner = addOwner(this.helper);
            this.pig = this.helper.spawnWithNoFreeWill(EntityType.PIG, PIG_POS);
            for (RisenType type : List.of(RisenType.SKELETON, RisenType.ZOMBIE))
            {
                Mob mob = DeathforgedGearGameTest.raiseOne(this.owner, type, DEATHFORGED_LEVEL, this.pig);
                if (mob == null)
                {
                    throw new GameTestAssertException("the risen undead couldn't be spawned");
                }
                this.risen.add(mob);
            }
            this.mark();
        }

        /**
         * The killing hit, unless one of them already killed the pig (then nothing more may fly from now on). The
         * owner then remembers it as the mob he last hit (the pack AI's "owner's target" rule looks at that).
         */
        void killPig()
        {
            this.arrowsAtKill.addAll(arrowsOf(this.owner));
            if (this.pig.isAlive())
            {
                this.pig.hurt(this.owner.damageSources().playerAttack(this.owner), KILLING_DAMAGE);
                this.owner.setLastHurtMob(this.pig);
                if (this.pig.isAlive())
                {
                    throw new GameTestAssertException("the test pig survived the killing hit");
                }
            }
            this.mark();
        }

        /** Starts counting the risen undead's own ticks from now. */
        void mark()
        {
            this.marks = RisenTestWait.mark(this.risen);
        }

        // ---- What the waits look at ----

        /** Both must have the living pig as their target, within {@link #GO_AFTER_GRACE_TICKS} of rising. */
        RisenTestWait.Look lookGoingAfterPig()
        {
            String gone = this.risenGone();
            if (gone != null)
            {
                return RisenTestWait.Look.failed(gone);
            }
            if (!this.pig.isAlive())
            {
                return RisenTestWait.Look.DONE; // one of them already killed it
            }
            for (Mob mob : this.risen)
            {
                if (mob.getTarget() != this.pig)
                {
                    String wrong = "a risen " + name(mob) + " didn't go after the mob the owner hit (its target: "
                            + describe(mob.getTarget()) + ")";
                    return new RisenTestWait.Look(wrong, RisenTestWait.fewestTicksSince(this.marks) < GO_AFTER_GRACE_TICKS);
                }
            }
            return RisenTestWait.Look.DONE;
        }

        /** They keep the living pig for {@link #FIGHT_TICKS} (it's never given up while it's alive). */
        RisenTestWait.Look lookFightingPig()
        {
            String gone = this.risenGone();
            if (gone != null)
            {
                return RisenTestWait.Look.failed(gone);
            }
            if (!this.pig.isAlive())
            {
                return RisenTestWait.Look.DONE; // one of them killed it
            }
            for (Mob mob : this.risen)
            {
                if (mob.getTarget() != this.pig)
                {
                    return RisenTestWait.Look.failed("a risen " + name(mob) + " gave up the living mob the owner hit (its target: "
                            + describe(mob.getTarget()) + ")");
                }
            }
            int fought = RisenTestWait.fewestTicksSince(this.marks);
            return fought < FIGHT_TICKS ? RisenTestWait.Look.waiting("they've fought the pig for " + fought + " of their ticks")
                    : RisenTestWait.Look.DONE;
        }

        /** After the kill: they must have let go (see {@link #findProblem}) within {@link #LET_GO_GRACE_TICKS}. */
        RisenTestWait.Look lookLetGo()
        {
            String problem = this.findProblem();
            int ticks = RisenTestWait.fewestTicksSince(this.marks);
            if (this.risenGone() != null)
            {
                return RisenTestWait.Look.failed(problem);
            }
            if (problem == null)
            {
                return ticks < AI_DECISION_TICKS ? RisenTestWait.Look.waiting("they've only ticked " + ticks + " time(s) since the kill")
                        : RisenTestWait.Look.DONE;
            }
            return new RisenTestWait.Look(problem, ticks < LET_GO_GRACE_TICKS);
        }

        /**
         * Once they've let go: nothing may go wrong again on any tick until {@link #AFTER_BODY_GONE_TICKS} of their ticks
         * after the dead pig's body is removed.
         */
        RisenTestWait.Look lookCalmUntilBodyGone()
        {
            String problem = this.findProblem();
            if (problem != null)
            {
                return RisenTestWait.Look.failed((this.bodyGone ? "after the body was removed: " : "before the body was removed: ") + problem);
            }
            if (!this.bodyGone)
            {
                if (!this.pig.isRemoved())
                {
                    return RisenTestWait.Look.waiting("the dead pig's body hasn't been removed yet");
                }
                this.bodyGone = true;
                this.mark();
            }
            int watched = RisenTestWait.fewestTicksSince(this.marks);
            return watched < AFTER_BODY_GONE_TICKS
                    ? RisenTestWait.Look.waiting("watched for " + watched + " of their ticks after the body was removed")
                    : RisenTestWait.Look.DONE;
        }

        /** Adds the end of both tests: they let go, then stay calm until after the body is gone; then the clean-up. */
        GameTestSequence thenLetGo(GameTestSequence sequence)
        {
            RisenTestWait letGo = new RisenTestWait(this.helper, RisenTestWait.AI_MILLIS);
            RisenTestWait calm = new RisenTestWait(this.helper, RisenTestWait.AI_MILLIS);
            return sequence
                    .thenWaitUntil(this.guard.poll(letGo, this::lookLetGo))
                    .thenExecute(this.guard.step(() -> this.failIfWrong(letGo, "within " + LET_GO_GRACE_TICKS + " of their ticks after the kill: ")))
                    .thenWaitUntil(this.guard.poll(calm, this::lookCalmUntilBodyGone))
                    .thenExecute(this.guard.step(() -> {
                        this.failIfWrong(calm, "");
                        this.cleanUp();
                    }));
        }

        // ---- Checks ----

        /**
         * What's still wrong, or null if nothing: both risen undead must still be there, neither may target anything
         * dead, dying or removed, neither may still be attacking or have its bow drawn, and no arrows may have been
         * shot since the kill (there's nothing alive left to shoot at).
         */
        @Nullable
        private String findProblem()
        {
            List<Mob> alive = risenOf(this.owner);
            if (alive.size() != 2 || this.risenGone() != null)
            {
                return "expected 2 risen undead, found " + alive.size();
            }
            for (Mob mob : this.risen)
            {
                LivingEntity target = mob.getTarget();
                if (target != null && RisenUndead.isGone(target))
                {
                    return "a risen " + name(mob) + " still targets a dead or removed " + name(target);
                }
                if (mob.isAggressive() || mob.isUsingItem())
                {
                    return "a risen " + name(mob) + " is still attacking (target: " + describe(target)
                            + ", bow drawn: " + (mob.isUsingItem() ? "yes" : "no") + ")";
                }
            }
            Set<UUID> newArrows = arrowsOf(this.owner);
            newArrows.removeAll(this.arrowsAtKill);
            if (!newArrows.isEmpty())
            {
                return "the risen undead shot " + newArrows.size() + " arrow(s) after the kill";
            }
            return null;
        }

        /** Which of the test's risen undead is gone (crumbled or died), or null if both are still there. */
        @Nullable
        private String risenGone()
        {
            for (Mob mob : this.risen)
            {
                if (!mob.isAlive())
                {
                    return "the risen " + name(mob) + " is gone (crumbled or died)";
                }
            }
            return null;
        }

        /** After a wait: fails the test if something was still wrong when it ended. */
        void failIfWrong(RisenTestWait wait, String when)
        {
            if (wait.problem() != null)
            {
                throw new GameTestAssertException(when + wait.problem() + " [other mobs near the arena: " + this.othersNearby() + "]");
            }
        }

        /** The other living mobs near the arena (not the pig or the risen undead), for failure messages. */
        private String othersNearby()
        {
            Vec3 corner = this.helper.absoluteVec(Vec3.ZERO);
            Vec3 opposite = this.helper.absoluteVec(new Vec3(ARENA_SIZE, STAND_Y + ARENA_HEIGHT, ARENA_SIZE));
            AABB area = new AABB(corner, opposite).inflate(NEARBY_RADIUS);
            List<String> others = new ArrayList<>();
            for (Mob mob : this.helper.getLevel().getEntitiesOfClass(Mob.class, area, mob -> mob.isAlive() && mob != this.pig && !this.risen.contains(mob)))
            {
                others.add(name(mob) + (mob.getTarget() == null ? "" : " (after a " + name(mob.getTarget()) + ")"));
            }
            return others.isEmpty() ? "none" : String.join(", ", others);
        }

        // ---- Clean-up ----

        /** The owner leaves (his risen undead crumble) and a pig still standing is removed. Safe to call twice. */
        void cleanUp()
        {
            removeOwner(this.owner);
            if (this.pig != null && !this.pig.isRemoved())
            {
                this.pig.discard();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

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

    /** The mob's kind in lower case ("zombie"), for test messages. Also used by {@link RisenSurvivorGameTest}. */
    static String name(LivingEntity entity)
    {
        return entity.getType().getDescription().getString().toLowerCase();
    }

    /** A target for test messages: its kind and whether it's still alive, or "none". */
    private static String describe(@Nullable LivingEntity target)
    {
        if (target == null)
        {
            return "none";
        }
        return name(target) + (RisenUndead.isGone(target) ? ", dead or removed" : ", alive");
    }

    // ---------------------------------------------------------------------------------------------
    // The test owner: a stand-in player
    // ---------------------------------------------------------------------------------------------

    /**
     * A stand-in player who owns the test's risen undead, standing on the arena's floor. He must be on the server's
     * player list, or his risen undead crumble at once (their owner "logged out"). Vanilla's
     * {@code makeMockServerPlayerInLevel} crashes under Forge (its connection has no network channel), so this one gets
     * an in-memory channel.
     *
     * <p>He's in creative mode, which monsters ignore: the test server's world is a survival world, and monsters
     * spawning in the caves round the arena would otherwise come for him, and his risen undead would (rightly) fight
     * them instead of doing what the test watches.
     */
    static ServerPlayer addOwner(GameTestHelper helper)
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
        owner.setGameMode(GameType.CREATIVE);
        return owner;
    }

    /** Logs the stand-in out (his risen undead crumble, as for a real player). Safe to call twice, or with null. */
    static void removeOwner(@Nullable ServerPlayer owner)
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
     * GameTests are built on a structure template. Instead of shipping a file, the arena (a smooth stone floor inside
     * barrier walls under a barrier roof) is made in memory when a development server starts, before the tests are
     * placed.
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

    /**
     * The arena in the structure-file format: its size, a two-block palette, the floor (layer 0), the walls round the
     * edge of the layers above it, and the roof (the top layer). The air inside is left out: the test framework clears
     * the space before placing it.
     */
    private static CompoundTag arenaTag()
    {
        final int floor = 0;   // palette index of the floor block
        final int barrier = 1; // palette index of the walls and roof
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(ARENA_SIZE, ARENA_HEIGHT, ARENA_SIZE));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.SMOOTH_STONE.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.BARRIER.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        int edge = ARENA_SIZE - 1;
        int roof = ARENA_HEIGHT - 1;
        for (int y = 0; y < ARENA_HEIGHT; y++)
        {
            for (int x = 0; x < ARENA_SIZE; x++)
            {
                for (int z = 0; z < ARENA_SIZE; z++)
                {
                    int state;
                    if (y == 0)
                    {
                        state = floor;
                    }
                    else if (y == roof || x == 0 || z == 0 || x == edge || z == edge)
                    {
                        state = barrier;
                    }
                    else
                    {
                        continue; // air inside the walls
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", intList(x, y, z));
                    block.putInt("state", state);
                    blocks.add(block);
                }
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
