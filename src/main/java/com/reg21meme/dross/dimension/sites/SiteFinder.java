package com.reg21meme.dross.dimension.sites;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/**
 * A use's own test: where in a patch does it fit (its lair, its sewer network...)? {@link BossSites} asks it once per
 * patch (and remembers the answer) and only ever places the use where it said.
 * <p>
 * The rules for writing one:
 * <ul>
 *   <li>Read the land only through the {@link SiteTerrain} you're given (the noise): never load, generate or read a chunk,
 *       never touch a level, never call {@link BossSites} back. It's called on any thread, world generation's included.</li>
 *   <li>Give the same answer every time for the same patch and seed (no {@code Math.random}, no clock, no other state).
 *       Seed any randomness from {@link SiteTerrain#seed()} and the patch ({@link Patch#id()}).</li>
 *   <li>Stay inside the patch: the spot returned must be in it ({@link Patch#contains}); a good start is
 *       {@link Patch#spots}, its deepest spots first.</li>
 *   <li>Be quick: a group asks up to 3 patches at once (a second or so in all is fine).</li>
 * </ul>
 * {@code relaxed} is false for the normal test. Only in a group where no patch passes it, the planner asks the roomiest
 * patch (the deepest) once more with {@code relaxed = true}: the use's smallest form, or looser rules, so the group still
 * gets its boss; return empty if even that can't fit (the group then has no boss, and a warning is logged).
 */
@FunctionalInterface
public interface SiteFinder
{
    Optional<Found> find(SiteTerrain terrain, Patch patch, boolean relaxed);

    /**
     * Where the use goes.
     *
     * @param pos    its spot (its middle; give the ground's height as y if you know it, otherwise 0)
     * @param detail anything the use wants to keep with it (for example its planned layout), or null
     */
    record Found(BlockPos pos, @Nullable Object detail)
    {
    }
}
