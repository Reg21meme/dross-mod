package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.slf4j.Logger;

/**
 * The boss-site planner's helper threads ({@link BossSites}): when a thread has to wait for the planner (the biome map
 * read further out, a group's fit tests), these share the work so the wait is short. The calling thread always works
 * too, and only waits for shares a helper has already started, so nothing can get stuck waiting for a busy helper.
 * The work handed to them must be pure (biome samples, fit tests): it never takes the planner's lock or calls it back.
 */
final class PlannerThreads
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many helper threads: half the processors, 1 to 8 (world generation has the rest). */
    static final int HELPERS = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));

    private PlannerThreads() {}

    /**
     * Runs {@code unit.accept(0)} to {@code unit.accept(units - 1)}, shared with the helper threads (this thread works
     * too), and returns once all are done. A unit that fails is logged and skipped: the callers work out anything missing
     * again themselves (and then see the error).
     */
    static void runAll(int units, IntConsumer unit)
    {
        if (units <= 0)
        {
            return;
        }
        if (units == 1)
        {
            unit.accept(0);
            return;
        }
        AtomicInteger next = new AtomicInteger();
        CountDownLatch left = new CountDownLatch(units);
        Runnable work = () -> {
            int u;
            while ((u = next.getAndIncrement()) < units)
            {
                try
                {
                    unit.accept(u);
                }
                catch (RuntimeException e)
                {
                    LOGGER.error("[boss sites] a helper's share of the planner's work failed", e);
                }
                finally
                {
                    left.countDown();
                }
            }
        };
        int helping = Math.min(HELPERS, units - 1);
        for (int h = 0; h < helping; h++)
        {
            Pool.POOL.execute(work);
        }
        work.run();
        boolean interrupted = false;
        while (true)
        {
            try
            {
                left.await();
                break;
            }
            catch (InterruptedException e)
            {
                interrupted = true;
            }
        }
        if (interrupted)
        {
            Thread.currentThread().interrupt();
        }
    }

    /** The threads (made when first needed; daemon threads, so they never keep the game from closing). */
    private static final class Pool
    {
        static final ExecutorService POOL = Executors.newFixedThreadPool(HELPERS, new ThreadFactory()
        {
            private final AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r)
            {
                Thread t = new Thread(r, "Dross boss sites helper #" + this.count.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);   // (someone is waiting for them)
                return t;
            }
        });
    }
}
