"""Shared pieces of fire's Nether ruins (nether_wastes, crimson_forest, warped_forest, soul_sand_valley,
basalt_deltas): the Nether's plants put only where the game keeps them, huge fungi, lava walled in, fire that burns
for ever (on netherrack or magma), soul fire (on soul sand or soul soil).

ruins.py checks the plants (nether_plant_problems): fungi, roots and sprouts on nylium or soul soil, nether wart on
soul sand, fire on netherrack or magma, soul fire on soul sand or soul soil, weeping vines hanging from a block or
from more vine, twisting vines standing on a block or on more vine."""
import math

from ruinkit import AIR, Mix, mc

# Fungi, nether wart and the two fires are plants: drawn as crossed sprites in the previews, never a floor, a wall
# a fence joins or a block that hides its neighbours (the shrine kit counts unknown blocks as full cubes).
mc.CROSS_PLANTS.update({"crimson_fungus", "warped_fungus", "fire", "soul_fire", "nether_wart"})
mc.kind.cache_clear()

LAVA = "lava"
NYLIUM = ("crimson_nylium", "warped_nylium")
FIRE_BASE = ("netherrack", "magma_block")
SOUL_BASE = ("soul_sand", "soul_soil")

# ---------------------------------------------------------------- the Nether's stones

NETHER_BRICK = Mix(("nether_bricks", 6), ("cracked_nether_bricks", 3), ("chiseled_nether_bricks", 0.3), patch=0.45)
RED_BRICK = Mix(("red_nether_bricks", 6), ("nether_bricks", 1), patch=0.5)
RACK = Mix(("netherrack", 10), ("nether_quartz_ore", 0.5), ("nether_gold_ore", 0.4), ("magma_block", 0.4), patch=0.3)
BLACKSTONE = Mix(("polished_blackstone_bricks", 5), ("cracked_polished_blackstone_bricks", 3), ("blackstone", 2),
                 ("gilded_blackstone", 0.3), patch=0.45)
BASALT = "basalt[axis=y]"


def base_at(g, x, y, z, ground):
    """The block id at a cell (the world's `ground` for unset ground, 'air' for unset air)."""
    s = g.get(x, y, z)
    if s is None:
        return ground if y <= 0 else "air"
    return mc.base(s)


# ---------------------------------------------------------------- plants and fire

# the ground a plant may turn into its own soil under it (never a ruin's bricks or planks)
GROUNDS = {"netherrack", "crimson_nylium", "warped_nylium", "soul_soil", "soul_sand", "magma_block", "basalt",
           "blackstone", "gravel"}


def free(g, x, y, z):
    """Air or unset above the ground, and not a locked cell (the frame and its opening)."""
    return g.inb(x, y, z) and g.empty(x, y, z) and not g.locked[x, y + g.G, z]


def plant(g, x, y, z, state, soil):
    """A plant at (x, y, z) on `soil` (put under it): only if the cell is free and the cell under it is the world's
    ground or a ground block (netherrack, nylium, soul soil...). Returns True if it was placed."""
    if not free(g, x, y, z):
        return False
    under = g.get(x, y - 1, z)
    if under is None and y - 1 > 0:
        return False
    if under is not None and mc.base(under) != mc.base(soil) and mc.base(under) not in GROUNDS:
        return False
    g.set(x, y - 1, z, soil)
    if mc.base(g.get(x, y - 1, z) or "") != mc.base(soil):
        return False
    g.set(x, y, z, state)
    return True


def fire_on(g, x, y, z, base="netherrack"):
    """Fire that burns for ever: `base` (netherrack or magma) put under it."""
    return plant(g, x, y, z, "fire", base)


def soul_fire_on(g, x, y, z, base="soul_sand"):
    return plant(g, x, y, z, "soul_fire", base)


def brazier(g, x, z, y=1, base="nether_bricks", top="magma_block", soul=False):
    """A post with a fire burning on top: base block, magma (or soul soil) and the fire."""
    g.set(x, y, z, base)
    if soul:
        g.set(x, y + 1, z, "soul_soil")
        g.set(x, y + 2, z, "soul_fire")
    else:
        g.set(x, y + 1, z, top)
        g.set(x, y + 2, z, "fire")


def weeping(g, x, y_top, z, n):
    """Weeping vines hanging down from under the block at y_top + 1: n long at most, stopping at anything in the way.
    Their tip is the full-grown head (it won't grow into what's below)."""
    if g.get(x, y_top + 1, z) is None or mc.kind(g.get(x, y_top + 1, z)) != "full":
        return 0
    cells = []
    for k in range(n):
        y = y_top - k
        if y < 1 or not free(g, x, y, z):
            break
        cells.append(y)
        if not free(g, x, y - 1, z):
            break
    for i, y in enumerate(cells):
        g.set(x, y, z, "weeping_vines[age=25]" if i == len(cells) - 1 else "weeping_vines_plant")
    return len(cells)


def twisting(g, x, y_base, z, n):
    """Twisting vines standing up from the block at y_base - 1 (it must be a full block), n tall at most."""
    under = g.get(x, y_base - 1, z)
    if not ((under is None and y_base - 1 <= 0) or (under is not None and mc.kind(under) == "full")):
        return 0
    cells = []
    for k in range(n):
        y = y_base + k
        if not free(g, x, y, z):
            break
        cells.append(y)
    for i, y in enumerate(cells):
        g.set(x, y, z, "twisting_vines[age=25]" if i == len(cells) - 1 else "twisting_vines_plant")
    return len(cells)


# ---------------------------------------------------------------- huge fungi

def huge_fungus(g, x, z, y0, h, kind="crimson", r=3.2, vines=0.35, lights=0.16, droop=1, wide_stem=False):
    """A huge fungus like the game's: a stem h tall from y0, a domed cap of wart blocks over it (its rim hanging
    `droop` below the dome), shroomlights in the cap's underside; weeping vines under a crimson cap. Returns the cap
    underside's height (the dome's lowest full layer)."""
    stem = f"{kind}_stem[axis=y]"
    wart = "nether_wart_block" if kind == "crimson" else "warped_wart_block"
    top = y0 + h - 1
    for y in range(y0, top + 1):
        g.set(x, y, z, stem)
        if wide_stem:
            for dx, dz in ((1, 0), (0, 1), (1, 1)):
                g.set(x + dx, y, z + dz, stem)
    cx, cz = (x + 0.5, z + 0.5) if wide_stem else (x, z)
    # the dome: two full layers on top, then the rim hanging down as a ring
    g.disc(cx, cz, r * 0.55, top + 2, wart)
    g.disc(cx, cz, r * 0.9, top + 1, wart)
    g.disc(cx, cz, r, top, wart, r_in=r - 1.2)
    for k in range(1, droop + 1):
        g.disc(cx, cz, r + 0.2, top - k, Mix((wart, 3), (AIR, 1), patch=0.3), r_in=r - 0.9)
    # the underside: shroomlights glowing in it
    for xx in range(int(cx - r) - 1, int(cx + r) + 2):
        for zz in range(int(cz - r) - 1, int(cz + r) + 2):
            s = g.get(xx, top + 1, zz)
            if s is not None and mc.base(s) == mc.base(wart) and g.empty(xx, top, zz) and g.rng.random() < lights:
                g.set(xx, top + 1, zz, "shroomlight")
    if kind == "crimson" and vines > 0:
        for xx in range(int(cx - r) - 1, int(cx + r) + 2):
            for zz in range(int(cz - r) - 1, int(cz + r) + 2):
                if math.hypot(xx - cx, zz - cz) > r + 0.4 or g.rng.random() > vines:
                    continue
                for y in range(top + 1, top - droop - 1, -1):
                    s = g.get(xx, y, zz)
                    if s is not None and mc.kind(s) == "full" and g.empty(xx, y - 1, zz):
                        weeping(g, xx, y - 1, zz, g.rng.randint(1, 4))
                        break
    return top + 1


# ---------------------------------------------------------------- lava walled in

def lava_pool(g, cells, y, rim, bed, depth=1):
    """Lava in every (x, z) of `cells`, from y down `depth` blocks; every neighbour that isn't lava (its four sides at
    each level and under its bottom) is set to `rim` (or `bed` under it), so it never spills."""
    cells = set(cells)
    for (x, z) in cells:
        for d in range(depth):
            g.set(x, y - d, z, LAVA, force=True)
        g.set(x, y - depth, z, bed, force=True)
    for (x, z) in cells:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) in cells:
                continue
            for d in range(depth):
                s = g.get(x + dx, y - d, z + dz)
                if s is None or not mc.is_supporting(s):
                    g.set(x + dx, y - d, z + dz, rim, force=True)


def oval_cells(cx, cz, rx, rz, g=None, wobble=0.0):
    """The (x, z) cells of an oval (its edge wobbling by `wobble` with the grid's noise)."""
    out = []
    for x in range(int(cx - rx) - 2, int(cx + rx) + 3):
        for z in range(int(cz - rz) - 2, int(cz + rz) + 3):
            d = math.hypot((x - cx) / max(rx, 0.1), (z - cz) / max(rz, 0.1))
            if wobble and g is not None:
                d += (g.noise3_at(3.0, x, 0, z) - 0.5) * wobble
            if d <= 1.0:
                out.append((x, z))
    return out
