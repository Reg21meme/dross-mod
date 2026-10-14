package com.reg21meme.dross.enchant;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;

/**
 * Waits for the risen undead GameTests ({@link RisenTargetGameTest}, {@link RisenSurvivorGameTest}) that hold up on a
 * busy machine. Only those tests use it (development only: in a normal game nothing here runs).
 *
 * <p>The test server runs its ticks as fast as it can (it doesn't wait 50 ms between them), while loading chunks is
 * background work. On a busy computer hundreds of ticks can pass before a test's arena is ready, and a count of the
 * server's ticks says nothing about how much real time has passed. So:
 * <ul>
 *   <li>a wait is checked every tick until what it waits for holds, for at most a real-time limit;</li>
 *   <li>anything that must happen "within so many ticks" counts the mobs' own ticks ({@code tickCount}: their AI runs
 *       once per tick of their own), not the server's, so a mob that isn't ticking yet can't use up its allowance;</li>
 *   <li>every wait ends by itself (done, out of allowance, out of time, or at {@link #BACKSTOP_TICKS}), well before the
 *       test's own tick limit, which would end the test on the spot. So the step after each wait always runs: it fails
 *       the test if something was still wrong, and the test cleans up either way.</li>
 * </ul>
 */
final class RisenTestWait
{
    // ---------------------------------------------------------------------------------------------
    // Limits (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /**
     * The longest a test waits for its arena to finish loading, in real time. Usually it's ready at once; a busy
     * machine making a brand-new world can take many seconds.
     */
    static final long ARENA_MILLIS = 120_000L;
    /**
     * The longest a wait counted in the mobs' own ticks may take, in real time. Only reached if they stop ticking: a
     * whole wait is normally a few dozen ticks.
     */
    static final long AI_MILLIS = 60_000L;
    /** No wait goes past this tick of its test. A backstop only: the server can run several hundred ticks a second. */
    static final int BACKSTOP_TICKS = 1_000_000;
    /** The tests' own tick limit: only a backstop behind the waits, which always end first. */
    static final int TEST_TIMEOUT_TICKS = BACKSTOP_TICKS + 1_000;

    // ---------------------------------------------------------------------------------------------
    // One wait
    // ---------------------------------------------------------------------------------------------

    /** What a wait sees on one tick: what's still wrong (null: nothing) and whether it may keep waiting for it. */
    record Look(@Nullable String wrong, boolean mayWait)
    {
        /** Nothing wrong: the wait is over. */
        static final Look DONE = new Look(null, false);

        /** Not there yet: keep waiting (while time allows). */
        static Look waiting(String why)
        {
            return new Look(why, true);
        }

        /** Something went wrong that waiting can't fix: stop now. */
        static Look failed(String problem)
        {
            return new Look(problem, false);
        }
    }

    private final GameTestHelper helper;
    private final long limitMillis;
    /** When the wait began (its first check): -1 until then. A sequence is built before it runs. */
    private long startMillis = -1L;
    private long startTick;
    @Nullable
    private String problem;

    /**
     * @param limitMillis the longest it waits, in real time ({@link #ARENA_MILLIS} or {@link #AI_MILLIS})
     */
    RisenTestWait(GameTestHelper helper, long limitMillis)
    {
        this.helper = helper;
        this.limitMillis = limitMillis;
    }

    /**
     * For a sequence's {@code thenWaitUntil}: looks once. Throwing keeps the sequence waiting (it looks again next
     * tick); returning lets it go on to the next step, with what was still wrong kept in {@link #problem()}.
     */
    void poll(Supplier<Look> look)
    {
        if (this.startMillis < 0L)
        {
            this.startMillis = Util.getMillis();
            this.startTick = this.helper.getTick();
        }
        Look now;
        try
        {
            now = look.get();
        }
        catch (RuntimeException e)
        {
            // An unexpected error inside a sequence would stop the whole test server: the next step fails the test.
            this.problem = "the test threw " + e;
            return;
        }
        this.problem = now.wrong();
        if (now.wrong() == null || !now.mayWait())
        {
            return;
        }
        if (Util.getMillis() - this.startMillis >= this.limitMillis || this.helper.getTick() >= BACKSTOP_TICKS)
        {
            this.problem = now.wrong() + " (gave up after " + this.took() + ")";
            return;
        }
        throw new GameTestAssertException(now.wrong()); // not yet: looked at again next tick
    }

    /** What was still wrong when the wait ended, or null if nothing. */
    @Nullable
    String problem()
    {
        return this.problem;
    }

    /** For the step after the wait: fails the test if something was still wrong when it ended. */
    void failIfWrong(String when)
    {
        if (this.problem != null)
        {
            throw new GameTestAssertException(when + this.problem);
        }
    }

    /** How long it has waited so far, in the server's ticks and in seconds. */
    String took()
    {
        return (this.helper.getTick() - this.startTick) + " ticks ("
                + String.format("%.1f", (Util.getMillis() - this.startMillis) / 1000.0D) + " s)";
    }

    // ---------------------------------------------------------------------------------------------
    // Waiting for the arena
    // ---------------------------------------------------------------------------------------------

    /**
     * What a wait for the arena looks at (give that wait {@link #ARENA_MILLIS}): done once the arena has finished
     * loading. A test can start before its chunks are ready (far from spawn, or in a brand-new world), and until then
     * nothing in them moves. A test adds nothing until it's done, and its first step checks the wait first
     * ({@link #failIfWrong}), so if it gave up there's nothing to clean up.
     *
     * @param size the arena's width and depth (blocks)
     * @param standY the relative y the test's mobs stand at
     */
    static Supplier<Look> arenaLoaded(GameTestHelper helper, int size, int standY)
    {
        return () -> {
            String loading = arenaStillLoading(helper, size, standY);
            return loading == null ? Look.DONE : Look.waiting(loading);
        };
    }

    /**
     * Null once every chunk under the arena has finished loading (the mobs in it tick and can be found, and its block
     * ticks run); otherwise which chunk is still loading.
     */
    @Nullable
    private static String arenaStillLoading(GameTestHelper helper, int size, int standY)
    {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(0, standY, 0));
        BlockPos opposite = helper.absolutePos(new BlockPos(size - 1, standY, size - 1));
        int minX = SectionPos.blockToSectionCoord(Math.min(corner.getX(), opposite.getX()));
        int maxX = SectionPos.blockToSectionCoord(Math.max(corner.getX(), opposite.getX()));
        int minZ = SectionPos.blockToSectionCoord(Math.min(corner.getZ(), opposite.getZ()));
        int maxZ = SectionPos.blockToSectionCoord(Math.max(corner.getZ(), opposite.getZ()));
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.isPositionEntityTicking(chunk.getWorldPosition()) || !level.areEntitiesLoaded(chunk.toLong())
                        || !level.getChunkSource().isPositionTicking(chunk.toLong()))
                {
                    return "the arena's chunk " + x + ", " + z + " hasn't finished loading";
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Counting the mobs' own ticks
    // ---------------------------------------------------------------------------------------------

    /** Each mob's own tick count right now, to count from ({@link #fewestTicksSince}). */
    static Map<Mob, Integer> mark(Collection<Mob> mobs)
    {
        Map<Mob, Integer> marks = new IdentityHashMap<>();
        for (Mob mob : mobs)
        {
            marks.put(mob, mob.tickCount);
        }
        return marks;
    }

    /** The fewest of their own ticks any of these mobs has lived since they were marked (0 if there are none). */
    static int fewestTicksSince(Map<Mob, Integer> marks)
    {
        int fewest = Integer.MAX_VALUE;
        for (Map.Entry<Mob, Integer> entry : marks.entrySet())
        {
            fewest = Math.min(fewest, entry.getKey().tickCount - entry.getValue());
        }
        return fewest == Integer.MAX_VALUE ? 0 : fewest;
    }

    // ---------------------------------------------------------------------------------------------
    // Keeping a test's first failure
    // ---------------------------------------------------------------------------------------------

    /**
     * Keeps a test's first failure as its result. A failed step fails the test at once, but the sequence still runs its
     * next steps on that same tick: they'd find the scene already cleaned up, fail again, and the test would report
     * that later, misleading failure instead. So every step and wait of a test goes through its guard, and after the
     * first failure they only repeat it.
     */
    static final class Guard
    {
        private final Runnable cleanUp;
        @Nullable
        private GameTestAssertException failure;

        /** @param cleanUp the test's clean-up (safe to run twice), run when a step fails */
        Guard(Runnable cleanUp)
        {
            this.cleanUp = cleanUp;
        }

        /**
         * One step of the test (for {@code thenExecute}). A failed check fails the test; any other error becomes a
         * failure too, instead of stopping the test server. Either way the clean-up runs first, so no stand-in player
         * is left behind.
         */
        Runnable step(Runnable body)
        {
            return () -> {
                if (this.failure != null)
                {
                    throw this.failure;
                }
                try
                {
                    body.run();
                }
                catch (GameTestAssertException failure)
                {
                    this.failed(failure);
                }
                catch (RuntimeException e)
                {
                    GameTestAssertException failure = new GameTestAssertException("the test threw " + e);
                    failure.initCause(e);
                    this.failed(failure);
                }
            };
        }

        /** One look of a wait (for {@code thenWaitUntil}). After a failure it just stops the sequence: the test is over. */
        Runnable poll(RisenTestWait wait, Supplier<Look> look)
        {
            return () -> {
                if (this.failure != null)
                {
                    throw this.failure;
                }
                wait.poll(look);
            };
        }

        private void failed(GameTestAssertException failure)
        {
            this.failure = failure;
            this.cleanUp.run();
            throw failure;
        }
    }
}
