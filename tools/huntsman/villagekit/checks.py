"""Checks a village before it's written: nothing in it would melt, break or fall off when the game places it.

- Clear ice melts where the block light on it is over 10 (Minecraft's IceBlock: brightness > 11 - its light block, 1),
  and a snow layer where it's over 11 (SnowLayerBlock): every light source is flooded out through everything that
  isn't a full opaque block, losing 1 a block, and each ice block and snow layer is checked.
- A snow layer can't lie on ice, packed ice or blue ice (it pops off at the next block update), nor on anything
  that hasn't a full top.
- Beds and doors come in whole pairs; lanterns hang from or stand on something; carpets and plants have a floor.
- Nothing floats (the shrine kit's drop_floating does the rest).
"""
import numpy as np

import mc

LIGHT = {
    "campfire": 15, "soul_campfire": 10, "lantern": 15, "soul_lantern": 10, "torch": 14, "wall_torch": 14,
    "soul_torch": 10, "soul_wall_torch": 10, "sea_lantern": 15, "glowstone": 15, "shroomlight": 15,
    "jack_o_lantern": 15, "end_rod": 14, "frostbloom": 3, "glow_lichen": 7, "magma_block": 3, "smoker": 13,
    "furnace": 13, "ochre_froglight": 15, "verdant_froglight": 15, "pearlescent_froglight": 15, "redstone_lamp": 15,
}
ICE_MELT_ABOVE = 10
SNOW_MELT_ABOVE = 11


def emission(state):
    b = mc.base(state)
    p = mc.props_of(state)
    if b in ("campfire", "soul_campfire", "smoker", "furnace") and p.get("lit", "true") != "true":
        return 0
    if b.endswith("candle") and not b.endswith("candle_cake"):
        return 3 * int(p.get("candles", "1")) if p.get("lit") == "true" else 0
    return LIGHT.get(b, 0)


def block_light(grid):
    """The block light (0-15) in every cell, flooded the way the game does it (roughly: full opaque blocks stop it)."""
    W, H, D = grid.W, grid.H, grid.D
    pal = grid.pal
    emit = np.array([emission(s) if s else 0 for s in pal])
    opaque = np.array([bool(s) and mc.kind(s) == "full" and mc.base(s) not in ("ice",) for s in pal])
    src = emit[grid.a]
    stop = opaque[grid.a]
    stop[:, :grid.G + 1, :] |= grid.a[:, :grid.G + 1, :] == 0  # unset ground is the world's ground
    light = src.astype(np.int16)
    for _ in range(15):
        n = np.zeros_like(light)
        n[1:, :, :] = np.maximum(n[1:, :, :], light[:-1, :, :])
        n[:-1, :, :] = np.maximum(n[:-1, :, :], light[1:, :, :])
        n[:, 1:, :] = np.maximum(n[:, 1:, :], light[:, :-1, :])
        n[:, :-1, :] = np.maximum(n[:, :-1, :], light[:, 1:, :])
        n[:, :, 1:] = np.maximum(n[:, :, 1:], light[:, :, :-1])
        n[:, :, :-1] = np.maximum(n[:, :, :-1], light[:, :, 1:])
        n = np.where(stop, 0, n - 1)
        light = np.maximum(light, np.maximum(n, src))
    return light


def check(grid):
    """Returns (problems, notes)."""
    problems = []
    G = grid.G
    light = block_light(grid)
    melt = []
    snow_melt = []
    bad_snow = []
    beds = {}
    doors = {}
    unsupported = []
    for x, yi, z, s in grid.blocks():
        b = mc.base(s)
        y = yi - G
        if b == "ice" and light[x, yi, z] > ICE_MELT_ABOVE:
            melt.append((x, y, z, int(light[x, yi, z])))
        elif b == "snow":
            if light[x, yi, z] > SNOW_MELT_ABOVE:
                snow_melt.append((x, y, z, int(light[x, yi, z])))
            below = grid.get(x, y - 1, z)
            if below is None:
                if y - 1 > 0:
                    bad_snow.append((x, y, z, "air"))
            elif mc.base(below) in ("ice", "packed_ice", "blue_ice", "frosted_ice") or mc.kind(below) != "full":
                if not (mc.base(below) == "snow" and mc.props_of(below).get("layers") == "8"):
                    bad_snow.append((x, y, z, mc.base(below)))
        elif b.endswith("_bed"):
            beds[(x, y, z)] = mc.props_of(s)
        elif b.endswith("_door"):
            doors[(x, y, z)] = mc.props_of(s)
        elif mc.kind(s) == "carpet" or b in ("frostbloom",):
            below = grid.get(x, y - 1, z)
            if (below is None and y - 1 > 0) or (below is not None and mc.is_air(below)):
                unsupported.append((b, x, y, z))
        elif b in ("lantern", "soul_lantern"):
            hanging = mc.props_of(s).get("hanging") == "true"
            other = grid.get(x, y + 1, z) if hanging else grid.get(x, y - 1, z)
            if other is None and (hanging or y - 1 > 0) or other is not None and mc.is_air(other):
                unsupported.append((b + (" hanging" if hanging else ""), x, y, z))
    from voxel import STEP
    for (x, y, z), p in beds.items():
        dx, dz = STEP[p["facing"]]
        other = (x - dx, y, z - dz) if p["part"] == "head" else (x + dx, y, z + dz)
        want = "foot" if p["part"] == "head" else "head"
        if beds.get(other, {}).get("part") != want:
            problems.append(f"half a bed at {x},{y},{z}")
    for (x, y, z), p in doors.items():
        other = (x, y + 1, z) if p["half"] == "lower" else (x, y - 1, z)
        if other not in doors:
            problems.append(f"half a door at {x},{y},{z}")
    if melt:
        problems.append(f"{len(melt)} clear ice blocks would melt (light over {ICE_MELT_ABOVE}), e.g. {melt[:5]}")
    if snow_melt:
        problems.append(f"{len(snow_melt)} snow layers would melt (light over {SNOW_MELT_ABOVE}), e.g. {snow_melt[:5]}")
    if bad_snow:
        problems.append(f"{len(bad_snow)} snow layers on something they can't lie on, e.g. {bad_snow[:5]}")
    if unsupported:
        problems.append(f"{len(unsupported)} things with nothing holding them, e.g. {unsupported[:6]}")
    return problems, light


SOFTER = {"lantern": "soul_lantern", "campfire": "soul_campfire", "torch": "soul_torch", "wall_torch": "soul_wall_torch"}


def cool_lights(grid, rounds=6):
    """Where clear ice or a snow layer would melt, the light sources near it are swapped for their soul versions
    (soul lanterns and soul fire give light 10, which never melts anything); ice still too bright after that becomes
    packed ice. Returns (sources swapped, ice blocks given up)."""
    G = grid.G
    swapped = 0
    given_up = 0
    for _ in range(rounds):
        light = block_light(grid)
        hot = []
        for x, yi, z, s in grid.blocks():
            b = mc.base(s)
            if (b == "ice" and light[x, yi, z] > ICE_MELT_ABOVE) or (b == "snow" and light[x, yi, z] > SNOW_MELT_ABOVE):
                hot.append((x, yi, z))
        if not hot:
            return swapped, given_up
        changed = False
        for x, yi, z in hot:
            best = None
            for xx in range(max(0, x - 14), min(grid.W, x + 15)):
                for yy in range(max(0, yi - 14), min(grid.H, yi + 15)):
                    for zz in range(max(0, z - 14), min(grid.D, z + 15)):
                        s = grid.pal[grid.a[xx, yy, zz]]
                        if s is None:
                            continue
                        b = mc.base(s)
                        if b in SOFTER and emission(s) > 0:
                            d = abs(xx - x) + abs(yy - yi) + abs(zz - z)
                            if emission(s) - d >= light[x, yi, z] - 1 and (best is None or d < best[0]):
                                best = (d, xx, yy, zz, s)
            if best is not None:
                _, xx, yy, zz, s = best
                name, props = mc.parse(s)
                new = mc.fmt("minecraft:" + SOFTER[mc.base(s)], props)
                grid.a[xx, yy, zz] = grid.id(new)
                swapped += 1
                changed = True
        if not changed:
            for x, yi, z in hot:
                if mc.base(grid.pal[grid.a[x, yi, z]]) == "ice":
                    grid.a[x, yi, z] = grid.id("packed_ice")
                    given_up += 1
                else:
                    grid.a[x, yi, z] = 0
            return swapped, given_up
    return swapped, given_up
