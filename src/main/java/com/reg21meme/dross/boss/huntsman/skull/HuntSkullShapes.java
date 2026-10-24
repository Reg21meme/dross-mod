package com.reg21meme.dross.boss.huntsman.skull;

import java.util.Map;

/**
 * Each mounted skull's hitbox, made by {@code tools/huntsman/skulls.py} (don't edit by hand): the box round its skull
 * (its bone, not its antlers or horns) as much as lies in its block, in pixels (0-16), facing north: on the floor
 * ({@code [0]}) and on a wall ({@code [1]}), each min x, y, z, max x, y, z. {@link HuntSkullBlock} turns them to its
 * facing.
 */
final class HuntSkullShapes
{
    static final Map<String, double[][]> BOXES = Map.ofEntries(
            Map.entry("alpha_moose_skull", new double[][] {{2.00D, 0.00D, 0.00D, 14.00D, 16.00D, 16.00D}, {2.00D, 3.52D, 0.00D, 14.00D, 16.00D, 13.00D}}),
            Map.entry("moose_skull", new double[][] {{3.77D, 0.00D, 0.00D, 12.23D, 12.06D, 16.00D}, {3.77D, 3.52D, 0.00D, 12.23D, 16.00D, 13.48D}}),
            Map.entry("rimehorn_skull", new double[][] {{0.00D, 0.00D, 0.00D, 16.00D, 16.00D, 16.00D}, {0.00D, 3.52D, 0.00D, 16.00D, 16.00D, 15.84D}}));

    private HuntSkullShapes() {}
}
