"""Thunder's mountains (a chance on the jagged, frozen and stony peaks): the knights fight on a summit or a ledge high
in the storm. Each layout builds its own crags of stone, andesite and tuff, veins of calcite, deepslate where the
lightning has scorched it, snow on the heights; lightning rods crown the rock, and the frame stands among them."""
import math

from ruinkit import (AIR, FRAME, MOUNTAIN, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, slab, stairs)
from ruindesigns.thunder.stormkit import (CHAR, COPPER, CUT_COPPER, DEEP, GLASS, ROD, STORM, hang, rod_on, rod_post,
                                          scorch)

ROCK = Mix(("stone", 5), ("andesite", 3), ("tuff", 2), ("cobblestone", 0.6), ("calcite", 0.8), patch=0.75, scale=3.0)

P = dict(
    floor=Mix(("stone", 3), ("andesite", 2), ("snow_block", 2), ("cobblestone", 1), patch=0.6),
    wall=Mix(("deepslate_bricks", 3), ("cracked_deepslate_bricks", 2), ("deepslate_tiles", 1), patch=0.4),
    rock=ROCK,
    trim="polished_deepslate",
    rubble=Mix(("cobbled_deepslate", 2), ("andesite", 1), ("stone", 1)),
    light="sea_lantern",
)

AREA = Area(element="thunder", id="mountains", name="Storm Peaks", biomes="jagged, frozen and stony peaks",
            setting=MOUNTAIN, ground="stone", under="stone", palette=P,
            blurb="High crags in the storm: stone and calcite under snow, crowned with lightning rods.")


def heights(g, fn, snow_from=6, cap=None):
    """Fills each column from the bottom up to fn(x, z) with rock, snow on the high tops (a full block or a layer).
    Returns {(x, z): top y}."""
    tops = {}
    for x in range(g.W):
        for z in range(g.D):
            h = fn(x, z)
            if h is None:
                continue
            h = int(round(h))
            for y in range(-g.G, h + 1):
                g.set(x, y, z, ROCK)
            if h >= snow_from:
                g.set(x, h, z, "snow_block" if g.noise3_at(3.0, x, h, z) > 0.45 else ROCK)
            tops[(x, z)] = h
    return tops


def snow_layers(g, tops, share=0.4, min_y=4):
    """A dusting of snow layers on the flat-topped rock (never on a slab or a plant)."""
    for (x, z), h in tops.items():
        if h >= min_y and g.chance(share) and g.empty(x, h + 1, z):
            s = g.get(x, h, z)
            if s is not None and g.opaque(x, h, z):
                g.set(x, h + 1, z, f"snow[layers={g.rng.choice((1, 1, 2))}]")


def needle(g, x, z, y0, h, mat=None, rod=True):
    """A thin crag needle: a column of rock (or deepslate) narrowing to a point, a rod on its tip."""
    mat = DEEP if mat is None else mat
    for y in range(y0, y0 + h):
        g.set(x, y, z, mat)
        if y < y0 + h // 2:
            for dx, dz in ((1, 0), (0, 1)):
                if g.noise3_at(2.0, x + dx, y, z + dz) > 0.5:
                    g.set(x + dx, y, z + dz, mat)
    if rod:
        g.set(x, y0 + h, z, "waxed_oxidized_cut_copper")
        g.set(x, y0 + h + 1, z, ROD)


def flatten(g, x1, z1, x2, z2, y, mat):
    """A level standing place at y: the ground there made y high (filled or cut), clear air over it."""
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for yy in range(-g.G, y):
                if g.get(x, yy, z) is None or yy > -g.G:
                    g.set(x, yy, z, ROCK)
            g.set(x, y, z, mat)
            for yy in range(y + 1, y + 6):
                g.set(x, yy, z, AIR, force=True)


@layout("rod_crown", "Rod Crown", size=(25, 22, 25), ground=3,
        blurb="A summit struck a thousand times: a crown of deepslate needles and rods round a scorched top.")
def rod_crown(g, p):
    """A rounded summit with a flat, wind-scoured top; round its back rises a crown of deepslate needles, each with
    a rod on its tip, and the frame stands at the crown's highest point; the top is scorched in a great star of
    deepslate where the lightning has struck again and again."""
    mid = g.W // 2
    cz = 11

    def h(x, z):
        d = math.hypot((x - mid) / 12.5, (z - cz) / 12.5)
        if d > 1.0:
            return 0
        base = 7.0 * (1 - d ** 2.4) + (g.noise3_at(4.0, x, 0, z) - 0.5) * 1.6
        return base + (3.0 if z < 6 and abs(x - mid) < 6 else 0)
    tops = heights(g, h, snow_from=5)
    top = 7
    # the summit's flat: a ragged oval at y 7
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - mid) / 8.5, (z - 14) / 6.5)
            if d <= 1.0 + 0.15 * (g.noise3_at(3.0, x, 0, z) - 0.5):
                for y in range(-g.G, top + 1):
                    g.set(x, y, z, ROCK)
                g.set(x, top, z, Mix(("stone", 3), ("andesite", 2), ("snow_block", 1), ("calcite", 1)))
                for y in range(top + 1, top + 5):
                    g.set(x, y, z, AIR, force=True)
                tops[(x, z)] = top
    # the crag at the back where the frame stands, 3 higher
    for x in range(mid - 4, mid + 5):
        for z in range(3, 8):
            for y in range(-g.G, top + 3):
                g.set(x, y, z, ROCK)
            tops[(x, z)] = top + 2
    for x in range(mid - 1, mid + 2):
        for k in range(2):
            g.set(x, top + 1 + k, 8 + (1 - k), stairs("stone_brick_stairs", "north"))
            for y in range(-g.G, top + 1 + k):
                g.set(x, y, 8 + (1 - k), ROCK)
    cells = broken_frame(g, mid - 2, top + 3, 5, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    # the crown of needles in an arc round the back
    for k in range(9):
        a = math.pi * (1.05 + 0.9 * k / 8)
        x = int(round(mid + math.cos(a) * 9.5))
        z = int(round(12 + math.sin(a) * 8.5))
        if not (0 < x < g.W - 1 and 0 < z < g.D - 1):
            continue
        y0 = tops.get((x, z), 0) + 1
        needle(g, x, z, y0, 3 + int(g.noise3_at(2.0, x, 0, z) * 6) + (2 if k in (3, 4, 5) else 0))
    # the scorch star on the summit and its glass, two sea lanterns sunk in the rock
    scorch(g, mid, 14, 4.5, ground_y=top, rods=2)
    for x, z in ((mid - 6, 12), (mid + 6, 13)):
        g.set(x, top, z, "sea_lantern")
    snow_layers(g, tops, share=0.35)
    return [guard(mid - 4, 15, y=top + 1), guard(mid + 4, 15, y=top + 1)]


@layout("storm_ledge", "Storm Ledge", size=(25, 24, 21), ground=3,
        blurb="A ledge on a cliff face high in the storm; the frame is set into a niche in the rock wall.")
def storm_ledge(g, p):
    """A sheer face of rock rises at the back to a jagged crest crowned with rods; at its foot a broad ledge juts
    out, its lip falling away at the front; the frame stands in a niche cut into the cliff, and frozen water
    streaks the rock beside it."""
    mid = g.W // 2
    ledge = 5
    n = g.noise2(5.0, salt=41)

    def h(x, z):
        if z <= 7:
            crest = 17 + (n[x, 3] - 0.5) * 7 + (2 if abs(x - mid - 3) < 3 else 0)
            return crest - max(0, z - 4) * 2.2
        if z <= 16:
            return ledge + (g.noise3_at(3.0, x, 0, z) - 0.5) * 0.8
        return ledge - (z - 16) * 1.6
    tops = heights(g, h, snow_from=8)
    flatten(g, 2, 9, g.W - 3, 15, ledge, Mix(("stone", 3), ("andesite", 2), ("gravel", 0.01), ("snow_block", 1)))
    # the niche in the cliff, the frame in it
    for x in range(mid - 3, mid + 4):
        for z in range(4, 9):
            for y in range(ledge + 1, ledge + 9 - (1 if x in (mid - 3, mid + 3) else 0)):
                g.set(x, y, z, AIR, force=True)
            g.set(x, ledge, z, P["wall"])
    cells = broken_frame(g, mid - 2, ledge + 1, 5, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 3, mid + 3):
        for y in range(ledge + 1, ledge + 8):
            g.set(x, y, 8, P["wall"])
        g.set(x, ledge + 8, 8, "sea_lantern")
    for x in range(mid - 3, mid + 4):
        g.set(x, ledge + 9, 8, P["wall"])
    # frozen water down the rock east of the niche
    for y in range(ledge + 1, 18):
        x = mid + 6 + (1 if y % 5 < 2 else 0)
        for z in range(0, 9):
            if g.get(x, y, z) is not None and g.empty(x, y, z + 1):
                g.set(x, y, z, "packed_ice")
                break
    # rods along the crest
    for x in range(2, g.W - 2, 4):
        best = max(range(0, 7), key=lambda z: g.top_y(x, z))
        rod_on(g, x, best)
    # a scorched path where the lightning ran down the face
    for y in range(ledge + 1, 16):
        x = mid - 7 + (y % 3) - 1
        for z in range(0, 9):
            if g.get(x, y, z) is not None and g.empty(x, y, z + 1):
                g.set(x, y, z, "deepslate")
                break
    scorch(g, mid - 6, 12, 2.6, ground_y=ledge)
    rod_post(g, 3, 13, 2, y0=ledge + 1, shaft=STORM)
    snow_layers(g, {k: v for k, v in tops.items() if k[1] < 9 or k[1] > 15}, share=0.4)
    return [guard(mid - 4, 13, y=ledge + 1), guard(mid + 4, 13, y=ledge + 1)]


@layout("twin_horns", "Twin Horns", size=(25, 24, 23), ground=3,
        blurb="Two jagged horns of rock with a saddle between; a bridge of calcite spans them over the frame.")
def twin_horns(g, p):
    """Two tall horns of rock rise either side of a high saddle; a natural bridge of pale calcite joins them over
    the frame, which stands in the saddle; the fight is on the saddle's broad snowy slope below, and rods crown
    both horns."""
    mid = g.W // 2
    saddle = 6

    def h(x, z):
        out = saddle + 0.0 - max(0, z - 10) * 0.55 + (g.noise3_at(4.0, x, 0, z) - 0.5) * 1.2
        for (hx, hz, peak, r) in ((4, 7, 19.0, 5.2), (g.W - 5, 6, 17.0, 4.8)):
            d = math.hypot(x - hx, (z - hz) * 1.1)
            if d < r:
                out = max(out, saddle + (peak - saddle) * (1 - d / r) ** 0.75
                          + (g.noise3_at(2.0, x, 5, z) - 0.5) * 2.5)
        return max(out, 1)
    tops = heights(g, h, snow_from=7)
    flatten(g, mid - 5, 11, mid + 5, 17, saddle - 2, Mix(("stone", 3), ("snow_block", 2), ("andesite", 1)))
    # steps up from the slope to the saddle where the frame stands
    for x in range(mid - 4, mid + 5):
        for z in range(4, 11):
            for y in range(-g.G, saddle):
                g.set(x, y, z, ROCK)
            g.set(x, saddle, z, Mix(("snow_block", 1), ("stone", 2), ("calcite", 1)))
            for y in range(saddle + 1, saddle + 8):
                if g.get(x, y, z) is not None and abs(x - mid) < 5:
                    g.set(x, y, z, AIR, force=True)
    for x in range(mid - 1, mid + 2):
        g.set(x, saddle - 1, 11, stairs("stone_brick_stairs", "north"))
        g.set(x, saddle, 10, stairs("stone_brick_stairs", "north"))
        g.set(x, saddle - 1, 10, ROCK)
    cells = broken_frame(g, mid - 2, saddle + 1, 6, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, p["rubble"])
    # the calcite bridge between the horns, high over the frame
    for x in range(3, g.W - 3):
        t = (x - mid) / (mid - 3)
        y = int(round(saddle + 12 - 2.5 * t * t))
        for dy in (0, 1):
            for z in (6, 7):
                if not (dy == 1 and abs(t) < 0.35 and z == 7):
                    g.set(x, y - dy, z, Mix(("calcite", 4), ("diorite", 1), ("smooth_basalt", 0.4)))
        for z in (6, 7):
            for yy in range(y - 2, 0, -1):
                if g.get(x, yy, z) is not None and g.get(x, yy, z) != "minecraft:air":
                    break
                if abs(x - mid) > 5:
                    g.set(x, yy, z, ROCK)
    # rods on both horns, a scorch down the bridge's middle
    for (hx, hz) in ((4, 7), (g.W - 5, 6)):
        bx, bz = max(((x, z) for x in range(hx - 2, hx + 3) for z in range(hz - 2, hz + 3)),
                     key=lambda c: g.top_y(*c))
        y = g.top_y(bx, bz)
        g.set(bx, y + 1, bz, "waxed_oxidized_cut_copper")
        g.set(bx, y + 2, bz, "sea_lantern")
        g.set(bx, y + 3, bz, ROD)
    g.set(mid, saddle + 12, 6, "deepslate")
    g.set(mid + 1, saddle + 12, 7, "deepslate")
    scorch(g, mid + 3, 14, 2.4, ground_y=saddle - 2)
    snow_layers(g, {k: v for k, v in tops.items() if not (mid - 6 < k[0] < mid + 6 and k[1] > 10)}, share=0.4)
    return [guard(mid - 3, 14, y=saddle - 1), guard(mid + 3, 14, y=saddle - 1)]


@layout("split_monolith", "Split Monolith", size=(23, 24, 23), ground=3,
        blurb="A great deepslate monolith on the summit, split in two by lightning; the frame stands in the split.")
def split_monolith(g, p):
    """On a rocky summit stood a monolith of deepslate bands and copper; lightning split it top to bottom, and the
    two halves lean apart with the frame standing in the gap between them; molten glass ran down the split and
    froze, and rods still stand on both halves."""
    mid = g.W // 2
    top = 4

    def h(x, z):
        d = math.hypot((x - mid) / 11.5, (z - 11) / 11.5)
        if d > 1.0:
            return 0
        return 1 + 4.0 * (1 - d ** 2.0) + (g.noise3_at(4.0, x, 0, z) - 0.5) * 2.2
    tops = heights(g, h, snow_from=4)
    flatten(g, 3, 6, g.W - 4, 17, top, Mix(("stone", 3), ("andesite", 2), ("calcite", 1), ("snow_block", 1)))
    # the two halves: 3 thick, banded deepslate and copper, leaning apart as they rise
    band = lambda g_, x, y, z: ("waxed_oxidized_cut_copper" if y % 5 == 0 else  # noqa: E731
                                "polished_deepslate" if y % 5 == 1 else
                                Mix(("deepslate_tiles", 2), ("cracked_deepslate_tiles", 1)).pick(g_, x, y, z))
    for side in (-1, 1):
        for y in range(top + 1, top + 16 - (2 if side > 0 else 0)):
            lean = int((y - top) * 0.28)
            x0 = mid + side * (4 + lean)
            for dx in range(0, 3):
                for z in range(5, 9):
                    g.set(x0 + side * dx, y, z, band)
        for y in range(top + 1, top + 4):
            for dx in range(4, 8):
                g.set(mid + side * dx, y, 9, band)
    # the split's glass: what melted ran down the inner faces and pooled at their feet
    for side in (-1, 1):
        for y in range(top + 2, top + 13):
            lean = int((y - top) * 0.28)
            x = mid + side * (3 + lean)
            if g.empty(x, y, 6) and g.chance(0.55):
                g.set(x, y, 6, GLASS)
    for x in range(mid - 3, mid + 4):
        if abs(x - mid) >= 3:
            g.set(x, top + 1, 7, GLASS)
    # the frame in the gap, sunk a row into the summit
    cells = broken_frame(g, mid - 2, top, 7, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # rods on both halves' tops
    for side in (-1, 1):
        best = max(((mid + side * d, z) for d in range(4, 13) for z in range(5, 9) if 0 <= mid + side * d < g.W),
                   key=lambda c: g.top_y(*c))
        y = g.top_y(*best)
        g.set(best[0], y + 1, best[1], "sea_lantern")
        g.set(best[0], y + 2, best[1], ROD)
    # broken pieces of it scattered on the summit
    for (x, z) in ((mid - 7, 13), (mid + 6, 15), (mid + 8, 12), (mid - 4, 16)):
        y = g.top_y(x, z) + 1
        g.set(x, y, z, Mix(("polished_deepslate", 2), ("waxed_oxidized_cut_copper", 1), ("deepslate_tiles", 1)))
    scorch(g, mid, 12, 4.0, ground_y=top, rods=1)
    snow_layers(g, tops, share=0.35)
    return [guard(mid - 4, 14, y=top + 1), guard(mid + 4, 14, y=top + 1)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [rod_crown, storm_ledge, twin_horns, split_monolith]
DROPPED = []
