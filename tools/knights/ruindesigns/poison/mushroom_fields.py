"""Poison's mushroom fields ruins: giant mushrooms gone wild over the ruins, pale stems used as columns, red and brown
caps for roofs, mycelium underfoot."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, slab, stairs)
from ruindesigns.poison.common import (froglamp, giant_mushroom, hang_vines, hanging_froglight, poison_dress, pool,
                                       webs_in_corners)

P = dict(
    floor=Mix(("mycelium", 4), ("mushroom_stem", 2), ("podzol", 1), patch=0.55),
    paving=Mix(("mushroom_stem", 4), ("mossy_cobblestone", 1), ("mycelium", 1), patch=0.4),
    wall=Mix(("mushroom_stem", 4), ("mossy_cobblestone", 1), ("cobblestone", 0.6), patch=0.4),
    rubble=Mix(("mushroom_stem", 2), ("red_mushroom_block", 1), ("brown_mushroom_block", 1), ("mossy_cobblestone", 1)),
    light="verdant_froglight[axis=y]",
)

AREA = Area(element="poison", id="mushroom_fields", name="Mushroom Fields", biomes="mushroom fields", setting=SURFACE,
            ground="mycelium", under="dirt", palette=P,
            blurb="Giant mushrooms gone wild over the ruins, their stems for columns and their caps for roofs.")

RED = "red_mushroom_block"
BROWN = "brown_mushroom_block"
STEM = "mushroom_stem"
PORES_RED = "red_mushroom_block[down=false]"
PORES_BROWN = "brown_mushroom_block[down=false]"


def ground(g, r=11.5, cz=None):
    court(g, Mix(("mycelium", 6), ("podzol", 1), patch=0.5), r, cz=cz, under="dirt")


def small_mushrooms(g, count, avoid=None):
    """A few little mushrooms on the mycelium (they keep on it in daylight)."""
    def place(x, y, z):
        s = g.get(x, y, z)
        if avoid and avoid(x, z):
            return False
        if s is None or "mycelium" in s or "podzol" in s:
            g.set(x, y, z, "mycelium" if s is None else s)
            g.set(x, y + 1, z, g.rng.choice(("red_mushroom", "brown_mushroom")))
            return True
        return False
    from ruinkit import scatter_on_floor
    scatter_on_floor(g, count, place)


def cap_dome(g, cx, cy, cz, r, block=RED, pores=PORES_RED, hang=2):
    """A red cap: a dome, its rim hanging `hang` blocks below its underside, pores under it."""
    for dx in range(-r - 1, r + 2):
        for dz in range(-r - 1, r + 2):
            d = math.hypot(dx, dz)
            if d > r + 0.4:
                continue
            # height of the dome's top over cy at this distance
            t = d / (r + 0.4)
            top = cy + int(round(2.6 * (1 - t * t)))
            bottom = cy if d < r - 1.2 else cy - (hang if d > r - 0.6 else 1)
            for y in range(bottom, top + 1):
                g.set(cx + dx, y, cz + dz, block)
            if d < r - 1.2:
                g.set(cx + dx, cy, cz + dz, block)
                if g.get(cx + dx, cy - 1, cz + dz) is None:
                    pass
            if bottom < top or d < r - 1.2:
                g.set(cx + dx, min(bottom, cy), cz + dz, pores)


def hang_from_cap(g, x, z, light, low=8, chains=2):
    """A froglight on chains under the first mushroom block above (x, low, z), if there's one high enough."""
    for y in range(low, g.H - g.G):
        s = g.get(x, y, z)
        if s is None:
            continue
        if "mushroom_block" in s and y - chains - 1 >= 3:
            for k in range(1, chains + 1):
                g.set(x, y - k, z, "chain[axis=y]")
            g.set(x, y - chains - 1, z, light)
        return


def thick_stem(g, x0, z0, n, y0, y1, block=STEM):
    for x in range(x0, x0 + n):
        for z in range(z0, z0 + n):
            if n >= 3 and (x - x0, z - z0) in ((0, 0), (n - 1, 0), (0, n - 1), (n - 1, n - 1)):
                continue
            for y in range(y0, y1 + 1):
                g.set(x, y, z, block)


# ---------------------------------------------------------------- 1 Great Cap

@layout("great_cap", "Great Cap", size=(25, 22, 25),
        blurb="A colossal red mushroom has grown up beside the frame, its broken cap spread over it like a roof.")
def great_cap(g, p):
    """A huge red mushroom at the back left, its cap over the frame with a bite torn out of it, the torn pieces lying
    below; little mushrooms round it, froglights hung from the cap's rim."""
    mid = g.W // 2
    ground(g)
    # pale paving in front of the frame
    court(g, p["paving"], 6.0, cx=mid + 1, cz=12, ragged=3.0, under="dirt")
    thick_stem(g, mid - 8, 3, 4, 1, 14)
    for dx, dz in ((-1, 1), (4, 2), (1, -1), (2, 4)):
        g.set(mid - 8 + dx, 1, 3 + dz, STEM)
    cap_dome(g, mid - 6, 15, 5, 8)
    # the bite out of the cap's front-right, its pieces on the ground under it
    g.smash(mid + 1, 15, 9, 3.6, jag=0.5)
    g.smash(mid - 3, 16, 12, 2.4, jag=0.5)
    for x, z in ((mid + 1, 10), (mid + 2, 9), (mid - 1, 11), (mid + 3, 11)):
        g.set(x, 1, z, RED)
        if g.chance(0.5):
            g.set(x, 2, z, RED)
    cells = broken_frame(g, mid - 1, 1, 5, [(4, 6), (3, 6), (4, 5)])
    # froglights hung from the cap's underside
    for x, z in ((mid - 1, 3), (mid - 9, 9), (mid - 4, 10), (mid - 10, 4)):
        hang_from_cap(g, x, z, p["light"])
    # smaller mushrooms round it
    giant_mushroom(g, mid + 8, 4, 6, 3, kind="brown")
    giant_mushroom(g, g.W - 3, 13, 4, 1, kind="red")
    giant_mushroom(g, 2, 15, 3, 1, kind="red")
    giant_mushroom(g, mid + 6, 20, 2, 1, kind="brown")
    # a broken low wall of stem blocks round the court's sides
    arc_wall(g, mid, 13, 9.5, (0.15, 0.85), p["wall"], 3, salt=1)
    arc_wall(g, mid, 13, 9.5, (1.15, 1.42), p["wall"], 2, salt=2)
    rubble(g, cells, p["rubble"], spread=3)
    small_mushrooms(g, 14)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


def arc_wall(g, cx, cz, r, span, mat, hmax, salt=0):
    """A broken wall along an arc (angles in half-turns, 0 = east, 0.5 = north), its top ragged, gaps in it."""
    a0, a1 = span
    n = int(abs(a1 - a0) * math.pi * r * 1.6) + 2
    done = set()
    for k in range(n + 1):
        a = math.pi * (a0 + (a1 - a0) * k / n)
        x, z = int(round(cx + math.cos(a) * r)), int(round(cz - math.sin(a) * r))
        if (x, z) in done or not (0 < x < g.W - 1 and 0 < z < g.D - 1):
            continue
        done.add((x, z))
        h = int(round(hmax * g.noise3_at(2.0, x, salt, z) + 0.3))
        for y in range(1, h + 1):
            g.set(x, y, z, mat)


# ---------------------------------------------------------------- 2 Stem Colonnade

@layout("colonnade", "Stem Colonnade", size=(25, 18, 25),
        blurb="A temple's colonnade, its columns grown into mushroom stems capped in brown; the frame at its far end.")
def colonnade(g, p):
    """Two rows of stem columns down a pale nave, flat brown caps for capitals, two of them toppled across the floor;
    a broken lintel over the back pair; the frame on a step at the end."""
    mid = g.W // 2
    ground(g)
    for x in range(mid - 6, mid + 7):
        for z in range(2, g.D - 1):
            if g.noise3_at(3.0, x, 0, z) < 0.8 or abs(x - mid) <= 3:
                g.set(x, 0, z, p["paving"])
    # the step at the back, the frame on it
    for x in range(mid - 5, mid + 6):
        for z in range(2, 6):
            g.set(x, 1, z, p["wall"])
    for x in range(mid - 3, mid + 4):
        g.set(x, 1, 6, stairs("mossy_cobblestone_stairs", "north"))
    cells = broken_frame(g, mid - 2, 2, 3, [(0, 6), (1, 6), (0, 5)])
    # the columns: stems with a flat brown capital (a little mushroom cap), heights broken
    cols = [(z, h_l, h_r) for z, h_l, h_r in ((4, 10, 7), (9, 8, 10), (14, 0, 6), (19, 5, 0))]
    for z, hl, hr in cols:
        for x, h in ((mid - 6, hl), (mid + 6, hr)):
            if h <= 0:
                g.set(x, 1, z, STEM)
                continue
            for y in range(1, h + 1):
                g.set(x, y, z, STEM)
            if h >= 6:
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        if g.chance(0.85):
                            g.set(x + dx, h + 1, z + dz, PORES_BROWN)
    # the lintel over the back pair: a brown cap slab spanning between them, broken at its right end
    for x in range(mid - 6, mid + 4):
        g.set(x, 11, 4, BROWN)
        g.set(x, 11, 5, BROWN if x < mid else None)
    for y in range(8, 12):
        g.set(mid + 6, y, 4, STEM)
    g.set(mid + 6, 12, 4, BROWN)
    hang_from_cap(g, mid - 3, 4, p["light"], low=5, chains=2)
    hang_from_cap(g, mid + 1, 4, p["light"], low=5, chains=3)
    # the toppled columns: lying across the floor with their caps at the ends
    g.line((mid - 5, 1, 14), (mid - 11, 1, 18), STEM)
    for dx in (0, 1):
        for dz in (0, 1):
            g.set(mid - 11 - dx, 1 + dz, 18, BROWN)
    g.line((mid + 7, 1, 19), (mid + 11, 1, 13), STEM)
    g.set(mid + 11, 2, 13, BROWN)
    g.set(mid + 11, 1, 12, BROWN)
    # mushrooms grown up through the floor between the columns
    giant_mushroom(g, mid - 9, 8, 5, 2, kind="red")
    giant_mushroom(g, mid + 9, 6, 7, 3, kind="brown")
    giant_mushroom(g, mid + 9, 21, 3, 1, kind="red")
    rubble(g, cells, p["rubble"], spread=3)
    small_mushrooms(g, 12)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


# ---------------------------------------------------------------- 3 Toppled Cap

@layout("toppled_cap", "Toppled Cap", size=(25, 18, 25),
        blurb="A giant mushroom has fallen along the back, its cap standing on edge like a wall; the frame before it.")
def toppled_cap(g, p):
    """A giant red mushroom lies toppled across the back: its root ball torn up at the left, its stem along the
    ground, its great cap stood on its edge at the right; the frame leaning on the stem in front."""
    mid = g.W // 2
    ground(g)
    # the root ball torn out of the ground at the left: a clod of mycelium and dirt, a hollow where it stood
    g.ellipsoid(3, 2, 4, 2.4, 2.4, 2.6, Mix(("dirt", 3), ("mycelium", 2), ("rooted_dirt", 2), patch=0.3))
    for x in range(1, 7):
        for z in range(7, 11):
            if math.hypot(x - 3.5, z - 8.5) <= 2.2:
                g.set(x, 0, z, "rooted_dirt")
    # the stem along the back, 3 thick, from the root ball to the cap
    for x in range(4, 17):
        for z in (3, 4, 5):
            for y in (1, 2, 3):
                if (z, y) in ((3, 3), (5, 3), (3, 1), (5, 1)) and g.chance(0.6):
                    continue
                g.set(x, y, z, STEM)
    # the cap on its edge at the right: a disc in the x plane, domed outwards (east), its pores facing the stem
    cx, cy, cz, r = 18, 5, 5, 5.6
    for dz in range(-7, 8):
        for dy in range(-7, 8):
            d = math.hypot(dz, dy)
            if d > r or cy + dy < 0:
                continue
            bulge = int(round(2.4 * (1 - (d / r) ** 2)))
            for k in range(0, bulge + 1):
                g.set(cx + k, cy + dy, cz + dz, RED)
            g.set(cx - 1, cy + dy, cz + dz, "red_mushroom_block[west=false]" if d < r - 1 else RED)
    g.smash(cx - 1, cy + 6, cz - 4, 2.2, jag=0.5)
    # the frame leaning on the stem: standing just in front of it, its top corner broken off
    cells = broken_frame(g, mid - 5, 1, 7, [(4, 6), (4, 5), (3, 6), (0, 6)])
    # brown shelves grown on the stem
    for x, z in ((7, 2), (12, 2), (14, 6), (9, 6)):
        g.set(x, 3, z, BROWN)
        g.set(x + 1, 3, z, BROWN)
    giant_mushroom(g, mid + 5, 11, 4, 2, kind="brown")
    giant_mushroom(g, 2, 15, 6, 2, kind="red", lean=(1, 0))
    giant_mushroom(g, g.W - 3, 19, 3, 1, kind="red")
    hang_from_cap(g, mid + 5, 11, p["light"], low=2, chains=1)
    froglamp(g, mid - 8, 12, post=STEM, h=2)
    court(g, p["paving"], 5.5, cx=mid, cz=15, ragged=3.0, under="dirt")
    rubble(g, cells, p["rubble"], spread=3)
    small_mushrooms(g, 14)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


# ---------------------------------------------------------------- 4 Shelf Hill

@layout("shelf_hill", "Shelf Hill", size=(25, 20, 25),
        blurb="A mycelium knoll climbed by a stair of brown bracket fungi; the frame on its summit.")
def shelf_hill(g, p):
    """A steep mycelium knoll at the back; brown mushroom shelves stick out of its face in a zigzag, a stair up to the
    flat summit where the frame stands; a red mushroom on its shoulder."""
    mid = g.W // 2
    ground(g)
    PEAK = 8
    heights = mound(g, mid, 5, 9.5, PEAK + 1.5, Mix(("dirt", 3), ("rooted_dirt", 1), patch=0.3), top="mycelium",
                    y0=0, rz=6.0)
    # flatten the summit
    for x in range(mid - 3, mid + 4):
        for z in range(3, 8):
            for y in range(1, PEAK + 1):
                g.set(x, y, z, "dirt")
            g.set(x, PEAK, z, "mycelium")
            for y in range(PEAK + 1, PEAK + 4):
                g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 2, PEAK + 1, 4, [(4, 6), (3, 6), (0, 6)])
    # the stair: bracket fungi stepping straight up the knoll's face, 3 wide, dirt under each
    for k in range(PEAK):
        z, y = 15 - k, 1 + k
        for x in range(mid - 1, mid + 2):
            g.set(x, y, z, BROWN, force=True)
            for yy in range(0, y):
                g.set(x, yy, z, "dirt", force=True)
            for yy in range(y + 1, y + 4):
                if z < 8 and yy > PEAK:
                    break
                g.set(x, yy, z, AIR, force=True)
    # brackets jutting from the knoll's sides wherever its slope drops away
    for (x, z), h in sorted(heights.items()):
        if h < 3 or abs(x - mid) <= 2 and z >= 7:
            continue
        for dx, dz in ((1, 0), (-1, 0), (0, 1)):
            nh = heights.get((x + dx, z + dz), 0)
            if nh < h - 1 and g.get(x + dx, h - 1, z + dz) is None and g.chance(0.45):
                g.set(x + dx, h - 1, z + dz, BROWN)
                side = (x + dx + dz, z + dz + dx)
                if g.get(side[0], h - 1, side[1]) is None:
                    g.set(side[0], h - 1, side[1], BROWN)
    # a red mushroom on the shoulder of the knoll, a brown one on the other
    hl = heights.get((mid - 6, 4), 4)
    giant_mushroom(g, mid - 6, 4, 6, 3, kind="red", y0=hl + 1)
    hr = heights.get((mid + 6, 6), 3)
    giant_mushroom(g, mid + 6, 6, 4, 2, kind="brown", y0=hr + 1)
    froglamp(g, mid - 3, 7, y=PEAK + 1, post=STEM, h=1)
    froglamp(g, mid + 3, 7, y=PEAK + 1, post=STEM, h=1)
    # broken stem pillars at the summit's back corners
    pillar(g, mid - 3, 3, 4, STEM, y0=PEAK + 1, cap=BROWN)
    pillar(g, mid + 3, 3, 2, STEM, y0=PEAK + 1)
    for (x, z), h in heights.items():
        s = g.get(x, h, z)
        if s and "mycelium" in s and g.get(x, h + 1, z) is None and g.chance(0.22):
            g.set(x, h + 1, z, g.rng.choice(("red_mushroom", "brown_mushroom")))
    court(g, p["paving"], 5.0, cx=mid, cz=18, ragged=3.0, under="dirt")
    rubble(g, cells, p["rubble"], spread=2)
    small_mushrooms(g, 10)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# ---------------------------------------------------------------- 5 Hollow Stem

@layout("hollow_stem", "Hollow Stem", size=(25, 20, 25),
        blurb="The rotten-hollow stump of a colossal mushroom stem, split open at the front; the frame stands inside.")
def hollow_stem(g, p):
    """A ring of stem blocks, tall and hollow, its front torn open in a ragged split; inside, the frame on a floor of
    podzol under a hung froglight; brown brackets up its outside, a torn brown collar round its top."""
    mid = g.W // 2
    ground(g)
    CX, CZ, R = mid, 7, 5.2
    TOP = 13
    g.cylinder(CX, CZ, R, 1, TOP, STEM, r_in=R - 1.6)
    g.cylinder(CX, CZ, R - 1.6, 0, 0, "podzol")
    # the ragged top
    g.ruin_tops(CX - 6, CZ - 6, CX + 6, CZ + 6, 8, TOP, 8.5, TOP, scale=2.5, salt=3)
    # the split at the front: open from the ground up, narrowing as it goes up
    for x in range(CX - 4, CX + 5):
        for z in range(CZ + 1, CZ + 7):
            for y in range(1, TOP + 1):
                w = 3.4 - y * 0.18 + (g.noise3_at(2.0, x, y, z) - 0.5) * 1.2
                if abs(x - CX) <= w:
                    g.set(x, y, z, AIR, force=True)
    # the torn brown collar round the top (a ledge, part of it gone)
    for dx in range(-7, 8):
        for dz in range(-7, 8):
            d = math.hypot(dx, dz)
            if R - 0.5 <= d <= R + 1.8 and not (dz > 2 and abs(dx) < 4) and g.noise3_at(2.5, CX + dx, 5, CZ + dz) > 0.3:
                g.set(CX + dx, 10, CZ + dz, BROWN)
    # brackets up its outside
    for (dx, dz, y) in ((-5, 2, 3), (-6, -1, 5), (5, 1, 4), (6, -2, 7), (-4, -4, 8), (4, -5, 6)):
        for k in (0, 1):
            g.set(CX + dx + (1 if dx > 0 else -1), y, CZ + dz + k, BROWN)
    cells = broken_frame(g, CX - 2, 1, CZ, [(4, 6), (4, 5), (3, 6)])
    # froglights grown into the inside of the wall
    for x, y, z in ((CX - 4, 4, CZ), (CX + 4, 6, CZ - 1), (CX - 1, 8, CZ - 4), (CX + 3, 3, CZ - 3)):
        g.set(x, y, z, p["light"], force=True)
    # the split's torn pieces on the ground in front
    for x, z in ((CX - 3, CZ + 7), (CX + 4, CZ + 6), (CX + 2, CZ + 9), (CX - 5, CZ + 8)):
        g.set(x, 1, z, STEM)
    giant_mushroom(g, 3, 4, 5, 2, kind="red")
    giant_mushroom(g, g.W - 4, 10, 3, 2, kind="brown")
    giant_mushroom(g, 4, 19, 2, 1, kind="red")
    for x in (CX - 6, CX + 6):
        froglamp(g, x, 15, post=STEM, h=2)
    court(g, p["paving"], 5.0, cx=mid, cz=17, ragged=3.0, under="dirt")
    rubble(g, cells, p["rubble"], spread=2)
    small_mushrooms(g, 12)
    return [guard(mid - 3, 17), guard(mid + 3, 17)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [great_cap, hollow_stem]
DROPPED = [colonnade, toppled_cap, shelf_hill]
