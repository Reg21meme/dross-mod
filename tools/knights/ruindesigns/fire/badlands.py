"""Fire's badlands ruins: banded terracotta (orange, yellow, white, red, brown, light grey) carved by the wind into
hoodoos, slot canyons and arches; red sand, dead bushes and cacti; old mine timbers; oaks on the wooded tablelands."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, slab, stairs)

BANDS = ["terracotta", "orange_terracotta", "orange_terracotta", "yellow_terracotta", "terracotta", "white_terracotta",
         "red_terracotta", "terracotta", "brown_terracotta", "orange_terracotta", "light_gray_terracotta",
         "terracotta", "red_terracotta", "yellow_terracotta"]

P = dict(
    floor=Mix(("red_sand", 4), ("coarse_dirt", 1), ("terracotta", 1), ("orange_terracotta", 1), patch=0.55),
    wall=Mix(("terracotta", 3), ("orange_terracotta", 2), ("red_terracotta", 1), ("brown_terracotta", 1), patch=0.45),
    stone=Mix(("red_sandstone", 3), ("cut_red_sandstone", 2), ("smooth_red_sandstone", 1), patch=0.4),
    adobe=Mix(("packed_mud", 3), ("mud_bricks", 3), ("terracotta", 1), patch=0.45),
    trim="chiseled_red_sandstone",
    rubble=Mix(("terracotta", 2), ("orange_terracotta", 1), ("red_sandstone", 1)),
    light="campfire[lit=true]",
)

AREA = Area(element="fire", id="badlands", name="Badlands", biomes="badlands, eroded badlands, wooded badlands",
            setting=SURFACE, ground="red_sand", under="terracotta", palette=P,
            blurb="Banded terracotta carved into hoodoos, slot canyons and arches; red sand, dead bushes and cacti.")


# ---------------------------------------------------------------- the area's own pieces

def band(shift=0):
    """The badlands' stripes: a block by height (a callable material, so g.line and g.box can use it)."""
    return lambda g, x, y, z: BANDS[(y + shift) % len(BANDS)]


def dress(g, count=12, cactus=0.3):
    """Dead bushes on the red sand, a cactus with room round it, a fallen slab."""
    def place(x, y, z):
        r = g.rng.random()
        if r < 0.55:
            g.set(x, y, z, "red_sand")
            g.set(x, y + 1, z, "dead_bush")
            return True
        if r < 0.55 + cactus:
            if all(g.empty(x + dx, y + k, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for k in (1, 2, 3)):
                g.set(x, y, z, "red_sand")
                for k in range(1, g.rng.randint(2, 3) + 1):
                    g.set(x, y + k, z, "cactus")
                return True
            return False
        g.set(x, y + 1, z, slab("red_sandstone_slab"))
        return True
    scatter_on_floor(g, count, place)


def brazier(g, x, z, y=1):
    g.set(x, y, z, "chiseled_red_sandstone")
    g.set(x, y + 1, z, "campfire[lit=true]")


def hoodoo(g, cx, cz, h, r=1.0, cap=1.6, shift=0, waist=True):
    """A hoodoo: a banded column (narrower at its waist) with a wider cap of hard white rock on top."""
    b = band(shift)
    for y in range(1, h + 1):
        rr = r
        if waist and h * 0.45 < y < h * 0.8:
            rr = max(r - 0.6, 0.0)
        if rr < 0.5:
            g.set(cx, y, cz, b)
        else:
            g.disc(cx, cz, rr, y, b)
    g.disc(cx, cz, cap, h + 1, Mix(("white_terracotta", 3), ("light_gray_terracotta", 1)))
    g.set(cx, h + 2, cz, "white_terracotta")


def oak(g, x, z, y0, h=4):
    """A small badlands oak with a round crown."""
    for y in range(y0, y0 + h):
        g.set(x, y, z, "oak_log[axis=y]")
    g.ellipsoid(x, y0 + h, z, 2.2, 1.3, 2.2, "oak_leaves[persistent=true]")
    g.set(x, y0 + h, z, "oak_log[axis=y]")


# ---------------------------------------------------------------- the layouts

@layout("hoodoos", "Hoodoo Spires", size=(25, 20, 25),
        blurb="A forest of wind-carved hoodoos, the frame wedged between the two tallest.")
def hoodoos(g, p):
    """Eroded badlands: banded spires of every height with white caps, the broken frame standing between the
    tallest pair, one fallen spire lying across the sand."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=13, under="terracotta")
    # the two tallest flank the frame
    hoodoo(g, mid - 5, 5, 12, r=1.6, cap=2.3, shift=0)
    hoodoo(g, mid + 5, 4, 14, r=1.7, cap=2.4, shift=3)
    cells = broken_frame(g, mid - 2, 1, 5, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the others, round the edges, lower towards the front
    for x, z, h, r, s in ((2, 3, 9, 1.2, 5), (g.W - 3, 9, 8, 1.3, 2), (3, 11, 6, 1.0, 7), (mid + 1, 1, 7, 1.0, 1),
                          (g.W - 4, 17, 4, 0.9, 4), (2, 19, 3, 0.8, 6), (mid - 8, 8, 5, 0.0, 9)):
        hoodoo(g, x, z, h, r=r, cap=r + 0.9, shift=s, waist=r >= 1.0)
    # a toppled one lying in pieces, its cap snapped off beside it
    b = band(4)
    g.line((mid + 7, 1, 14), (mid + 11, 1, 19), b, r=0.8)
    g.set(mid + 7, 1, 12, "white_terracotta")
    g.set(mid + 6, 1, 12, "white_terracotta")
    for x, z in ((mid - 2, 8), (mid + 2, 8)):
        brazier(g, x, z)
    dress(g, 14, cactus=0.3)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


@layout("slot_canyon", "Slot Canyon", size=(25, 20, 25),
        blurb="Two mesas split by a narrow slot canyon; the frame stands deep in the slot, a broken rope bridge above.")
def slot_canyon(g, p):
    """The back of the plot is a mesa cut in two by a winding slot, its walls sheer and banded; the frame stands at
    the slot's far end, a plank bridge snapped in the middle hangs over it, the canyon opens onto the sand in front."""
    mid = g.W // 2
    court(g, p["floor"], 9.5, cz=16, under="terracotta")
    b = band(2)
    for z in range(0, 14):
        wob = (g.noise3_at(3.0, 0, 0, z) - 0.5) * 1.2
        left = 3.2 + max(0, z - 6) * 0.65 + wob
        right = 3.0 + max(0, z - 3) * 0.9 - wob
        bend = math.sin(z * 0.45) * 0.8
        for x in range(g.W):
            dx = x - mid - bend
            if -left <= dx <= right:
                continue
            if dx < 0:
                top = 12 + int(round((g.noise3_at(5.0, x, 0, z) - 0.5) * 4)) - max(0, z - 10) - (2 if x < 2 else 0)
            else:
                top = 8 + int(round((g.noise3_at(5.0, x, 0, z) - 0.5) * 3)) - max(0, z - 7) - (1 if x > g.W - 3 else 0)
            for y in range(1, top + 1):
                g.set(x, y, z, b)
            g.set(x, top, z, "coarse_dirt" if g.noise3_at(2.0, x, 5, z) > 0.4 else "red_sand")
    # the slot's floor: red sand with a paved path to the frame
    for z in range(1, 14):
        for x in range(mid - 3, mid + 4):
            if g.get(x, 1, z) is None:
                g.set(x, 0, z, Mix(("red_sand", 2), ("cut_red_sandstone", 1)))
    cells = broken_frame(g, mid - 2, 1, 3, [(0, 6), (1, 6), (0, 5), (0, 4)])
    rubble(g, cells, p["rubble"])
    # a plank bridge across the slot at the lower rim's height, snapped in the middle; a lantern on its stub
    span = [x for x in range(g.W) if g.get(x, 1, 7) is None and g.get(x, 1, 8) is None]
    lo, hi = min(span), max(span)
    for x in range(lo - 1, hi + 2):
        if abs(x - mid) <= 1:
            continue
        g.set(x, 7, 7, "oak_planks")
        g.set(x, 7, 8, "oak_slab[type=top]")
        g.set(x, 8, 8, "oak_fence")
    g.set(mid - 2, 6, 7, "chain[axis=y]")
    g.set(mid - 2, 5, 7, "lantern[hanging=true]")
    # boulders fallen into the slot
    g.rubble_pile(mid + 2, 9, 1.6, 1.6, b)
    g.rubble_pile(mid - 4, 13, 1.8, 1.8, b)
    for x in (mid - 6, mid + 6):
        brazier(g, x, 16)
    dress(g, 12, cactus=0.35)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


@layout("mine_mouth", "Mine Mouth", size=(25, 18, 25),
        blurb="An old gold mine's timbered mouth in a terracotta bank, its rails running out to the broken frame.")
def mine_mouth(g, p):
    """A banded bank across the back with gold ore in its seams; a dark-oak timbered adit, rails curling out of it
    past an upturned ore pile; the frame stands at the rails' end, propped with timbers."""
    mid = g.W // 2
    court(g, p["floor"], 10.0, cz=15, under="terracotta")
    b = band(5)
    for x in range(g.W):
        face = 7 + int(round((g.noise3_at(4.0, x, 0, 0) - 0.5) * 4))
        for z in range(0, face + 1):
            top = 9 - max(0, z - face + 3) + int(round((g.noise3_at(3.0, x, 2, z) - 0.5) * 2))
            for y in range(1, top + 1):
                g.set(x, y, z, b)
            if g.noise3_at(2.0, x, 7, z) > 0.83:
                g.set(x, max(top - 2, 1), z, "gold_ore")
    # the adit: 3 wide, 4 tall, its timber sets every 2 blocks
    ax = mid + 5
    for z in range(0, 10):
        for x in range(ax - 1, ax + 2):
            for y in range(1, 5):
                g.set(x, y, z, AIR, force=True)
            g.set(x, 0, z, "gravel" if x != ax else "red_sand")
    for z in range(2, 11, 2):
        for y in range(1, 5):
            g.set(ax - 2, y, z, "dark_oak_log[axis=y]")
            g.set(ax + 2, y, z, "dark_oak_log[axis=y]")
        for x in range(ax - 2, ax + 3):
            g.set(x, 5, z, "dark_oak_log[axis=x]")
    g.set(ax, 4, 6, "lantern[hanging=true]")
    g.set(ax, 4, 8, "chain[axis=y]")
    g.set(ax, 3, 8, "lantern[hanging=true]")
    # the rails out of the mine, turning towards the frame and broken off
    for z in range(1, 12):
        g.set(ax, 1, z, "rail[shape=north_south]")
    g.set(ax, 1, 12, "rail[shape=north_west]")
    for x in range(ax - 4, ax):
        g.set(x, 1, 12, "rail[shape=east_west]")
    # the frame, timber props either side, an ore heap and spilled barrels
    cells = broken_frame(g, mid - 5, 1, 10, [(0, 6), (1, 6), (2, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 6, mid):
        g.line((x, 1, 12), (x, 5, 10), "dark_oak_fence")
    g.rubble_pile(mid - 9, 13, 2.2, 2.0, Mix(("terracotta", 2), ("gold_ore", 1), ("deepslate_gold_ore", 0.3)))
    for x, z in ((ax + 3, 11), (ax + 4, 12), (ax + 3, 13)):
        g.set(x, 1, z, "barrel[facing=up]")
    g.set(ax + 4, 2, 12, "barrel[facing=north]")
    brazier(g, mid - 8, 18)
    dress(g, 12, cactus=0.25)
    return [guard(mid - 4, 17), guard(mid + 2, 17)]


@layout("cliff_dwelling", "Cliff Dwelling", size=(25, 22, 25),
        blurb="Adobe rooms stacked in tiers under a wooded mesa's cliff; the frame stands in their broken plaza.")
def cliff_dwelling(g, p):
    """Wooded badlands: a banded cliff with oaks on its top; against its foot two tiers of mud-brick rooms with
    T-shaped doors and ladders, broken open in the middle round a round plaza where the frame stands."""
    mid = g.W // 2
    court(g, p["floor"], 10.0, cz=16, under="terracotta")
    b = band(7)
    # the cliff and its top of grass and coarse dirt, oaks along it
    for x in range(g.W):
        back = 4 + int(round((g.noise3_at(3.0, x, 0, 0) - 0.5) * 2))
        for z in range(0, back + 1):
            top = 13 + int(round((g.noise3_at(4.0, x, 3, z) - 0.5) * 3))
            for y in range(1, top + 1):
                g.set(x, y, z, b)
            g.set(x, top, z, "grass_block" if g.noise3_at(2.0, x, 6, z) > 0.4 else "coarse_dirt")
    for x in (3, mid - 2, g.W - 5):
        oak(g, x, 2, g.top_y(x, 2) + 1, h=3 + (x % 2))
    # the lower tier of rooms: x 1..W-2, z 5..10, 4 tall, a packed-mud roof; the upper tier set back over it
    for x0, x1, y0, z0, z1 in ((1, g.W - 2, 1, 5, 10), (3, g.W - 4, 6, 5, 7)):
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                for y in range(y0, y0 + 4):
                    wall = x in (x0, x1) or z in (z0, z1) or (x - x0) % 5 == 0
                    g.set(x, y, z, p["adobe"] if wall else AIR)
                g.set(x, y0 + 4, z, "packed_mud" if (x + z) % 4 else "stripped_oak_log[axis=z]")
        # T-shaped doors in each room's front
        for x in range(x0 + 2, x1, 5):
            g.set(x, y0, z1, AIR, force=True)
            g.set(x, y0 + 1, z1, AIR, force=True)
            g.set(x - 1, y0 + 2, z1, AIR, force=True)
            g.set(x, y0 + 2, z1, AIR, force=True)
            g.set(x + 1, y0 + 2, z1, AIR, force=True)
    # ladders up the lower tier's front
    for x in (4, g.W - 5):
        for y in range(1, 6):
            g.set(x, y, 11, "ladder[facing=south]")
    # the plaza: the rooms' middle broken out right back to the cliff
    for x in range(mid - 4, mid + 5):
        for z in range(5, 12):
            for y in range(1, 11):
                if math.hypot((x - mid) / 4.6, (z - 8) / 4.0) < 1.0 or z >= 9:
                    g.set(x, y, z, AIR, force=True)
    g.ruin_tops(mid - 7, 5, mid + 7, 10, 1, 10, 3.0, 9.0, scale=3.0, salt=9,
                region=lambda x, z: abs(x - mid) <= 5 + (z - 5) // 3)
    g.disc(mid, 8, 3.6, 0, Mix(("cut_red_sandstone", 2), ("red_sandstone", 1)))
    g.disc(mid, 8, 4.4, 1, p["adobe"], r_in=3.6)
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, 12, AIR, force=True)
        g.set(x, 1, 13, AIR, force=True)
    cells = broken_frame(g, mid - 2, 1, 7, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    g.settle_rubble(0.25, 1.5, p["rubble"], y_max=3)
    for x in (mid - 3, mid + 3):
        brazier(g, x, 9)
    for x, z in ((2, 13), (g.W - 3, 13)):
        g.set(x, 1, z, "decorated_pot")
    dress(g, 9, cactus=0.15)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


@layout("stone_arch", "Stone Arch", size=(25, 20, 23),
        blurb="A natural banded arch spans the frame; red sand drifts against its feet, cacti all round.")
def stone_arch(g, p):
    """A great arch of banded terracotta (the wind's work) over the broken frame, its legs thick and buttressed,
    a second arch half fallen behind it, drifts of red sand and cacti round them."""
    mid = g.W // 2
    court(g, p["floor"], 10.0, cz=13, under="terracotta")
    b = band(1)
    pts = []
    for k in range(25):
        t = math.pi * k / 24
        pts.append((mid + 8.0 * math.cos(t), 1 + 11.0 * math.sin(t) ** 0.85, 6))
    for (x1, y1, z1), (x2, y2, z2) in zip(pts, pts[1:]):
        g.line((round(x1), round(y1), z1), (round(x2), round(y2), z2), b, r=1.4)
    for x in (mid - 9, mid + 9):
        g.line((x, 1, 6), (x, 4, 6), b, r=2.0)
    # the fallen arch behind: one leg and a stump of its span
    g.line((mid - 7, 1, 1), (mid - 4, 8, 1), b, r=1.1)
    g.line((mid + 6, 1, 2), (mid + 7, 4, 2), b, r=1.1)
    g.rubble_pile(mid + 3, 2, 2.2, 1.8, b)
    cells = broken_frame(g, mid - 2, 1, 6, [(4, 6), (3, 6), (4, 5), (2, 6)])
    for x in range(mid - 1, mid + 2):
        g.set(x, 0, 6, "cut_red_sandstone")
    rubble(g, cells, p["rubble"])
    # sand drifted against the legs
    for x, z, r in ((mid - 10, 9, 3.5), (mid + 10, 9, 3.2), (mid + 8, 18, 2.5)):
        mound(g, x, z, r, 2.0, "red_sand", y0=0)
    for x in (mid - 4, mid + 4):
        brazier(g, x, 9)
    dress(g, 14, cactus=0.45)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [slot_canyon, mine_mouth, cliff_dwelling, stone_arch]
DROPPED = [hoodoos]
