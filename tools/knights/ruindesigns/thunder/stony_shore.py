"""Thunder's stony shores: grey stone and andesite cliffs, sea stacks standing in the surf, gravel between the rocks,
an old lighthouse's stump, and the storm's marks (lightning rods on weathered copper, deepslate, sea lanterns)."""
import math

from ruinkit import (AIR, FRAME, SHORE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, shore_line, slab, stairs)
from ruindesigns.thunder.stormkit import (COPPER, CUT_COPPER, DEEP, ROD, STORM, hang, rod_on, rod_post, scorch,
                                          sea_floor)

ROCK = Mix(("stone", 5), ("andesite", 3), ("cobblestone", 1), ("tuff", 1), ("diorite", 0.4), patch=0.7, scale=3.5)

P = dict(
    floor=Mix(("stone", 3), ("andesite", 2), ("gravel", 2), ("cobblestone", 1), patch=0.6),
    wall=Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2), ("mossy_stone_bricks", 1), ("andesite", 1), patch=0.4),
    rock=ROCK,
    trim="polished_andesite",
    rubble=Mix(("cobblestone", 2), ("andesite", 1), ("stone", 1)),
    light="sea_lantern",
)

AREA = Area(element="thunder", id="stony_shore", name="Stony Shore", biomes="stony shore",
            setting=SHORE, ground="stone", under="stone", palette=P,
            blurb="Grey stone cliffs and sea stacks, an old lighthouse's stump on the rocks.")


def stack(g, cx, cz, r0, h, y0=-3, lean=(0.0, 0.0), salt=0):
    """A sea stack: a rough column of rock narrowing as it rises (leaning a little), its top broken."""
    for y in range(y0, y0 + h):
        t = (y - y0) / max(h - 1, 1)
        r = r0 * (1.0 - 0.45 * t) + (g.noise3_at(2.0, int(cx) + salt, y, int(cz)) - 0.5) * 1.4
        ox, oz = cx + lean[0] * t * h, cz + lean[1] * t * h
        for x in range(int(ox - r - 1), int(ox + r + 2)):
            for z in range(int(oz - r - 1), int(oz + r + 2)):
                if math.hypot(x - ox, z - oz) <= r:
                    g.set(x, y, z, ROCK)
    return (int(round(cx + lean[0] * h)), int(round(cz + lean[1] * h)))


def gravel_beds(g, n, avoid=None):
    """Patches of gravel and stone on the ground layer (with stone under, so nothing falls)."""
    for _ in range(n):
        cx, cz = g.rng.randint(1, g.W - 2), g.rng.randint(shore_line(g.D), g.D - 2)
        if avoid and avoid(cx, cz):
            continue
        r = g.rng.uniform(1.5, 3.0)
        for x in range(int(cx - r), int(cx + r) + 1):
            for z in range(int(cz - r), int(cz + r) + 1):
                if 0 <= x < g.W and 0 <= z < g.D and math.hypot(x - cx, z - cz) <= r and g.empty(x, 1, z):
                    s = g.get(x, 0, z)
                    if s is None or "stone" in s or "andesite" in s or "gravel" in s:
                        g.set(x, 0, z, "gravel")
                        g.set(x, -1, z, "stone")


def dress(g, count=8):
    def place(x, y, z):
        r = g.rng.random()
        if r < 0.35:
            g.set(x, y + 1, z, slab("cobblestone_slab"))
        elif r < 0.6:
            g.set(x, y + 1, z, Mix(("andesite", 1), ("cobblestone", 1), ("mossy_cobblestone", 1)))
        elif r < 0.8:
            g.set(x, y + 1, z, slab("andesite_slab"))
        else:
            g.set(x, y + 1, z, "stone_button[face=floor,facing=north]")
        return True
    scatter_on_floor(g, count, place, y_max=1)


@layout("lighthouse", "Lighthouse", size=(23, 22, 25),
        blurb="An old lighthouse's stump on the rocks, its side torn open; the frame stands in the breach.")
def lighthouse(g, p):
    """A round lighthouse of banded stone and calcite on an outcrop at the water's edge, its top broken off and its
    seaward side torn open, the frame standing inside the breach on the landward side; its lantern lies smashed on
    the rocks, but a copper rod still stands on the tallest piece of wall."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.0, cz=17, under="stone", rx=11.0, rz=7.0)
    # the outcrop under it, its back in the sea
    mound(g, mid, line - 1, 7.5, 2.5, ROCK, y0=-3, rz=6.0)
    for x in range(g.W):
        for z in range(0, line + 6):
            if math.hypot(x - mid, (z - line + 1) * 1.2) <= 7.0:
                for y in range(-3, 1):
                    g.set(x, y, z, ROCK)
    # the tower: radius 4.5, its wall a ring 1 thick, bands of calcite and stone bricks
    cx, cz = mid, line - 1

    def band(g_, x, y, z):
        return "calcite" if (y // 3) % 2 == 0 else Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2)).pick(g_, x, y, z)
    for y in range(1, 17):
        g.disc(cx, cz, 4.5, y, band, r_in=3.4)
    g.disc(cx, cz, 4.4, 0, "polished_andesite")
    g.disc(cx, cz, 5.4, 1, "polished_andesite", r_in=4.4)
    # broken off at a slant, and the breach facing the way in
    g.ruin_tops(cx - 6, cz - 6, cx + 6, cz + 6, 3, 17, 6.0, 16.5, scale=3.0, salt=5)
    for x in range(cx - 2, cx + 3):
        for y in range(1, 10 - abs(x - cx)):
            for z in range(cz + 2, cz + 6):
                g.set(x, y, z, AIR, force=True)
    g.smash(cx + 3, 6, cz + 3, 2.2, jag=0.5)
    g.settle_rubble(0.6, 2.0, p["rubble"], protect=lambda x, z: z > line + 7 and abs(x - mid) < 6)
    # the frame inside, on the tower's floor, its top corner broken
    cells = broken_frame(g, cx - 2, 1, cz, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the rod on the tallest piece of wall
    top = max((g.top_y(x, z), x, z) for x in range(cx - 5, cx + 6) for z in range(cz - 5, cz + 6)
              if math.hypot(x - cx, z - cz) > 3.3)
    g.set(top[1], top[0] + 1, top[2], "waxed_oxidized_cut_copper")
    g.set(top[1], top[0] + 2, top[2], ROD)
    # the lantern room fallen: its sea lanterns and copper on the rocks to the east
    for x, z in ((cx + 6, cz + 4), (cx + 7, cz + 3), (cx + 6, cz + 5), (cx + 8, cz + 6)):
        y = g.top_y(x, z) + 1
        g.set(x, y, z, "sea_lantern" if (x + z) % 2 else CUT_COPPER)
    # a stair of stones up from the beach to the breach
    for k, z in enumerate(range(cz + 6, cz + 3, -1)):
        for x in range(cx - 1, cx + 2):
            g.set(x, 1 if k < 2 else 1, z, "polished_andesite" if k else stairs("polished_andesite_stairs", "north"))
    gravel_beds(g, 4, avoid=lambda x, z: abs(x - mid) < 4 and z > 13)
    scorch(g, mid + 5, 18, 2.5)
    dress(g, 6)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


@layout("sea_stacks", "Sea Stacks", size=(25, 22, 25),
        blurb="Three sea stacks stand in the surf; the frame on a shelf of rock between the two tallest.")
def sea_stacks(g, p):
    """Three stacks of grey rock rise out of the sea at the back, the tallest two joined near their tops by a bridge
    the sea has not yet broken; under the bridge a shelf of rock holds the frame, and rods crown the stacks."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.5, cz=18, under="stone", rx=11.5, rz=7.0)
    # the shelf at the waterline, under the bridge
    for x in range(mid - 5, mid + 6):
        for z in range(2, line + 3):
            if math.hypot((x - mid) / 5.5, (z - 6) / 4.5) <= 1.0 or z >= line:
                for y in range(-3, 1):
                    g.set(x, y, z, ROCK)
    t1 = stack(g, mid - 7, 4, 3.4, 18, lean=(0.04, 0.0), salt=1)
    t2 = stack(g, mid + 7, 3, 3.0, 16, lean=(-0.05, 0.02), salt=2)
    t3 = stack(g, g.W - 3, line + 1, 2.2, 9, salt=3)
    # the bridge between the two tallest, about 10 up, 2 thick
    for y in (10, 11, 12):
        for k in range(0, 21):
            x = mid - 7 + k * 0.7
            z = 4 - k * 0.05
            dy = 1 if abs(k - 10) > 6 else 0
            g.set(int(round(x)), y - dy, int(round(z)), ROCK)
            if y == 10:
                g.set(int(round(x)), y - dy, int(round(z)) + 1, ROCK)
    g.smash(mid + 1, 9, 4, 1.4, jag=0.3)
    cells = broken_frame(g, mid - 2, 1, 6, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    for (x, z) in (t1, t2, t3):
        y = g.top_y(x, z)
        g.set(x, y + 1, z, "sea_lantern")
        g.set(x, y + 2, z, ROD)
    # deepslate scorched down the tallest stack's face where the lightning ran
    for y in range(4, 16):
        x = t1[0] + (1 if y % 4 < 2 else 2)
        for z in range(t1[1], t1[1] + 4):
            if g.get(x, y, z) is not None and g.empty(x, y, z + 1):
                g.set(x, y, z, "deepslate")
                break
    # rocks round their feet
    for _ in range(14):
        x, z = g.rng.randint(0, g.W - 1), g.rng.randint(0, line - 1)
        if g.get(x, 0, z) is None:
            g.set(x, -1, z, ROCK)
            if g.chance(0.4):
                g.set(x, 0, z, ROCK)
    gravel_beds(g, 4, avoid=lambda x, z: abs(x - mid) < 5 and z > 14)
    scorch(g, mid - 6, 19, 2.4)
    dress(g, 7)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


@layout("cliff_cleft", "Cliff Cleft", size=(25, 20, 23),
        blurb="A sea cliff split by a cleft down to the waves; the frame stands in the cleft, the sea behind it.")
def cliff_cleft(g, p):
    """A ridge of grey cliff across the back of the shore, its seaward face dropping into the water; a cleft splits
    it to the ground, and in the cleft stands the frame with the sea showing through behind it. Steps cut into the
    rock climb to a ledge where storm posts stand."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.0, cz=17, under="stone", rx=11.5, rz=5.5)
    n = g.noise2(5.0, salt=21)
    for x in range(g.W):
        crest = 9 + int(round((n[x, 8] - 0.5) * 6)) - (2 if x in (0, g.W - 1) else 0)
        front = 12 + int(round((n[x, 14] - 0.5) * 3))
        back = 3 + int(round((n[x, 2] - 0.5) * 3))
        for z in range(back, front + 1):
            # the faces fall away: steep at the front, a little stepped at the back
            h = crest
            if z >= front - 1:
                h = crest - 2 * (z - (front - 2))
            if z <= back + 1:
                h = crest - 3 * ((back + 2) - z)
            h += int(round((g.noise3_at(2.0, x, 0, z) - 0.5) * 2))
            for y in range(-3, max(h, 0) + 1):
                g.set(x, y, z, ROCK)
    # the cleft: 5 wide at the ground, widening as it rises, open through to the sea
    for z in range(0, 15):
        for y in range(1, 16):
            w = 3 + y // 4
            for x in range(mid - w, mid + w + 1):
                if g.get(x, y, z) is not None:
                    g.set(x, y, z, AIR, force=True)
    for x in range(mid - 3, mid + 4):
        for z in range(line - 1, 14):
            g.set(x, 0, z, Mix(("gravel", 1), ("stone", 2), ("andesite", 1)))
            g.set(x, -1, z, "stone")
    cells = broken_frame(g, mid - 2, 1, line + 1, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    # steps cut up the cliff's east face to a ledge, and the ledge's storm posts
    ex = mid + 5
    for k in range(7):
        z = 13 - (k // 2)
        x = ex + k
        if x >= g.W - 1:
            break
        y = 1 + k
        g.set(x, y, z, stairs("stone_brick_stairs", "east"))
        for yy in range(-1, y):
            g.set(x, yy, z, ROCK)
        g.set(x, y + 1, z, AIR, force=True)
        g.set(x, y + 2, z, AIR, force=True)
    ledge_y = g.top_y(g.W - 4, 9)
    for x in range(g.W - 6, g.W - 1):
        for z in range(7, 11):
            if g.top_y(x, z) >= ledge_y - 1:
                g.set(x, ledge_y, z, Mix(("stone_bricks", 2), ("cracked_stone_bricks", 1)))
                for y in range(ledge_y + 1, ledge_y + 4):
                    g.set(x, y, z, AIR, force=True)
    rod_post(g, g.W - 3, 8, 2, y0=ledge_y + 1, shaft=STORM)
    rod_post(g, g.W - 5, 10, 1, y0=ledge_y + 1, shaft=STORM, light=False)
    # the lightning's path: deepslate scorched down the cleft's west wall
    for y in range(2, 12):
        x = mid - 4 - y // 4
        z = 6 + (y % 3)
        if g.get(x, y, z) is not None:
            g.set(x, y, z, "deepslate")
    gravel_beds(g, 3, avoid=lambda x, z: abs(x - mid) < 5 and z > 14)
    scorch(g, mid - 6, 18, 2.4)
    dress(g, 6)
    return [guard(mid - 4, 17), guard(mid + 3, 18)]


@layout("tide_pools", "Tide Pools", size=(25, 16, 25),
        blurb="A shelf of rock pitted with tide pools; the frame stands in the deepest one, cairns of rods round it.")
def tide_pools(g, p):
    """A low shelf of rock runs from the beach out into the sea, pitted with pools the tide fills; the frame stands
    with its foot in the deepest pool, and round the shelf stand cairns of stacked stones, each crowned with a rod."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.0, cz=19, under="stone", rx=11.0, rz=6.0)
    # the shelf: one block over the sea, ragged at its seaward edge
    for x in range(g.W):
        for z in range(1, 15):
            edge = 2.0 + 3.0 * g.noise3_at(4.0, x, 0, 0)
            if z >= edge and math.hypot((x - mid) / 12.5, (z - 9) / 9.0) <= 1.0:
                for y in range(-3, 2):
                    g.set(x, y, z, ROCK)
    # the pools: hollows in the shelf, water level with its top (walled in all round)
    pools = ((mid, 6, 3.2), (mid - 7, 9, 2.0), (mid + 7, 8, 2.4), (mid - 4, 3, 1.4), (mid + 4, 12, 1.5))
    for (px, pz, r) in pools:
        for x in range(int(px - r) - 1, int(px + r) + 2):
            for z in range(int(pz - r) - 1, int(pz + r) + 2):
                if math.hypot(x - px, z - pz) <= r and g.get(x, 1, z) is not None:
                    ok = all(g.get(x + dx, 1, z + dz) is not None
                             for dx in (-1, 0, 1) for dz in (-1, 0, 1))
                    if ok:
                        g.set(x, 1, z, "water")
                        if r > 3 and math.hypot(x - px, z - pz) < r - 1.2:
                            g.set(x, 0, z, "water")
                            g.set(x, -1, z, "gravel")
    # the frame standing in the deepest pool, its feet in the water, broken at its top
    cells = broken_frame(g, mid - 2, 0, 6, [(0, 6), (1, 6), (2, 6), (0, 5)])
    for x, y, z in cells:
        g.set(x + 1, 2, z + 4, FRAME)
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, 6, "water", force=True)
    # cairns of stacked stones round the shelf, a rod on each
    for (cx, cz, h) in ((mid - 9, 5, 4), (mid + 9, 4, 5), (mid - 5, 12, 3), (mid + 10, 12, 3), (2, 13, 2)):
        if g.get(cx, 1, cz) is not None and "water" in g.get(cx, 1, cz):
            continue
        y0 = g.top_y(cx, cz) + 1
        for k in range(h):
            g.set(cx, y0 + k, cz, Mix(("andesite", 2), ("cobblestone", 1), ("stone", 1), ("polished_andesite", 1)))
            if k == 0:
                for dx, dz in ((1, 0), (0, 1)):
                    g.set(cx + dx, y0, cz + dz, "cobblestone")
        g.set(cx, y0 + h, cz, "waxed_oxidized_cut_copper")
        g.set(cx, y0 + h + 1, cz, ROD)
    # a sea lantern glowing at the bottom of the big pool, steps from the beach onto the shelf
    g.set(mid + 2, -1, 4, "sea_lantern")
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, 15, slab("stone_slab"))
    gravel_beds(g, 3, avoid=lambda x, z: abs(x - mid) < 5 and z > 15)
    scorch(g, mid + 6, 19, 2.4)
    dress(g, 7)
    return [guard(mid - 4, 19), guard(mid + 4, 19)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [lighthouse, sea_stacks, cliff_cleft, tide_pools]
DROPPED = []
