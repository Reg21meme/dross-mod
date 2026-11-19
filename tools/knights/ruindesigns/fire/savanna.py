"""Fire's savanna ruins: acacia, coarse dirt, red sandstone and sun-baked terracotta in the long grass, a wildfire's
scars, campfire braziers; flat-topped acacias leaning over them."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, slab, stairs)

LOG = "acacia_log[axis=y]"
LEAVES = "acacia_leaves[persistent=true]"

P = dict(
    floor=Mix(("coarse_dirt", 4), ("dirt_path", 2), ("packed_mud", 1), ("grass_block", 2), patch=0.55),
    wall=Mix(("terracotta", 3), ("orange_terracotta", 2), ("red_sandstone", 2), ("cut_red_sandstone", 1), patch=0.45),
    accent=Mix(("orange_terracotta", 3), ("red_terracotta", 1), ("yellow_terracotta", 1), patch=0.4),
    stone=Mix(("red_sandstone", 3), ("cut_red_sandstone", 2), ("smooth_red_sandstone", 1), patch=0.4),
    trim="chiseled_red_sandstone",
    rubble=Mix(("red_sandstone", 2), ("terracotta", 2), ("coarse_dirt", 1)),
    scorch=Mix(("coarse_dirt", 3), ("brown_terracotta", 2), ("black_terracotta", 1), ("basalt[axis=y]", 0.5), patch=0.5),
    light="campfire[lit=true]",
)

AREA = Area(element="fire", id="savanna", name="Savanna", biomes="savanna, savanna plateau, windswept savanna",
            setting=SURFACE, ground="grass_block", under="dirt", palette=P,
            blurb="Acacia, red sandstone and sun-baked terracotta in the long grass, scorched by old wildfires.")


# ---------------------------------------------------------------- the area's own pieces

def acacia(g, x, z, top, lean=(2, 0), r=3.5, y0=1, fork=None):
    """A flat-topped acacia: a leaning trunk (face to face, so it stands), a crown of leaves two layers thick; `fork`
    adds a second branch (dx, dz, rise) with its own smaller crown."""
    tx, tz = x + lean[0], z + lean[1]
    g.line((x, y0, z), (tx, top, tz), LOG)
    crowns = [(tx, top, tz, r)]
    if fork is not None:
        fx, fz, rise = fork
        g.line((x + lean[0] // 2, y0 + (top - y0) // 2, z + lean[1] // 2), (x + fx, top - rise, z + fz), LOG)
        crowns.append((x + fx, top - rise, z + fz, r * 0.7))
    for cx, cy, cz, rr in crowns:
        g.disc(cx, cz, rr, cy + 1, LEAVES)
        g.disc(cx, cz, max(rr - 1.6, 1.0), cy + 2, LEAVES)


def tall_grass(g, x, y, z):
    g.set(x, y, z, "grass_block")
    if g.empty(x, y + 2, z) and g.rng.random() < 0.4:
        g.set(x, y + 1, z, "tall_grass[half=lower]")
        g.set(x, y + 2, z, "tall_grass[half=upper]")
    else:
        g.set(x, y + 1, z, "grass")


def dress(g, count=14, burnt=0.25):
    """The long grass, dead bushes on coarse dirt, a dropped slab or a pot."""
    def place(x, y, z):
        r = g.rng.random()
        if r < 0.62:
            tall_grass(g, x, y, z)
        elif r < 0.62 + burnt:
            g.set(x, y, z, "coarse_dirt")
            g.set(x, y + 1, z, "dead_bush")
        elif r < 0.93:
            g.set(x, y + 1, z, slab("red_sandstone_slab"))
        else:
            g.set(x, y + 1, z, "decorated_pot")
        return True
    scatter_on_floor(g, count, place)


def brazier(g, x, z, y=1, base="chiseled_red_sandstone"):
    g.set(x, y, z, base)
    g.set(x, y + 1, z, "campfire[lit=true]")


def hut(g, cx, cz, r=2.6, wall_h=3, door="south", broken=0.0):
    """A round hut of terracotta and packed mud under a cone of hay, its door gap facing `door`; `broken` knocks a
    bite out of its roof and wall on one side."""
    for y in range(1, wall_h + 1):
        g.disc(cx, cz, r, y, P["wall"] if y > 1 else "packed_mud", r_in=r - 1.0)
    for y in range(wall_h + 1, wall_h + 4):
        rr = r + 0.6 - (y - wall_h - 1) * 1.3
        if rr <= 0.4:
            g.set(cx, y, cz, "hay_block[axis=y]")
        else:
            g.disc(cx, cz, rr, y, "hay_block[axis=y]")
    g.set(cx, wall_h + 4, cz, LOG)
    dx, dz = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[door]
    for k in range(1, 4):
        for y in (1, 2):
            g.set(cx + dx * k, y, cz + dz * k, AIR)
    if broken:
        # burnt: the roof fallen in on one side, the walls charred black round the hole
        bx, bz = cx - dz * 2, cz + dx * 2
        g.smash(bx, wall_h + 2, bz, broken + 1.0)
        g.smash(cx, wall_h + 3, cz, 1.2)
        g.weather(cx - 4, 1, cz - 4, cx + 4, wall_h + 3, cz + 4,
                  {"terracotta": Mix(("black_terracotta", 2), ("terracotta", 1)),
                   "orange_terracotta": Mix(("brown_terracotta", 1), ("black_terracotta", 1)),
                   "hay_block": Mix(("hay_block[axis=y]", 1), ("coarse_dirt", 1)),
                   "red_sandstone": "black_terracotta"}, chance=0.55)


# ---------------------------------------------------------------- the layouts

@layout("hut_ring", "Hut Ring", size=(25, 18, 25),
        blurb="A burnt-out ring of round huts and a thorn fence; the frame stands where their fire was.")
def hut_ring(g, p):
    """Five round huts under hay cones round a fenced yard, two burnt open; the frame on a red sandstone hearth."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=13, under="dirt")
    # the hearth the frame stands on
    for x in range(mid - 3, mid + 4):
        for z in range(4, 9):
            g.set(x, 0, z, p["stone"])
    for x in range(mid - 3, mid + 4):
        g.set(x, 1, 8, slab("smooth_red_sandstone_slab"))
    cells = broken_frame(g, mid - 2, 1, 6, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 3, mid + 3):
        brazier(g, x, 6)
    # the huts
    hut(g, 4, 5, door="east", broken=1.8)
    hut(g, g.W - 5, 5, door="west")
    hut(g, 3, 13, r=2.2, door="east")
    hut(g, g.W - 4, 14, r=2.4, door="west", broken=2.0)
    hut(g, mid + 6, 2, r=1.8, wall_h=2, door="south")
    # the thorn fence round the yard, broken in places, the gate at the front between two posts
    for k in range(64):
        a = 2 * math.pi * k / 64
        x = int(round(mid + math.cos(a) * 11.4))
        z = int(round(13 + math.sin(a) * 10.4))
        if not (0 < x < g.W - 1 and 0 < z < g.D - 1):
            continue
        if z > 20 and abs(x - mid) <= 2:
            continue
        if g.noise3_at(3.0, x, 1, z) < 0.35 or not g.empty(x, 1, z):
            continue
        g.set(x, 1, z, "acacia_fence")
        if g.noise3_at(2.0, x, 2, z) > 0.55:
            g.set(x, 2, z, "acacia_fence")
    for x in (mid - 3, mid + 3):
        pillar(g, x, 23, 3, LOG)
        g.set(x, 4, 23, "skeleton_skull[rotation=8]")
    # an acacia over the back
    acacia(g, 2, 21, 7, lean=(1, -1), r=3.0)
    dress(g, 16, burnt=0.3)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


@layout("great_acacia", "Great Acacia", size=(25, 22, 25),
        blurb="A giant acacia grown up through the broken frame, its roots over a fallen terrace.")
def great_acacia(g, p):
    """A huge forked acacia whose trunk swallowed one side of the frame; its two crowns shade the fighting ground."""
    mid = g.W // 2
    court(g, p["floor"], 10.0, cz=14, under="dirt")
    # the old terrace round the tree, broken stepped edges
    for x in range(mid - 8, mid + 9):
        for z in range(2, 10):
            if math.hypot((x - mid) / 8.5, (z - 6) / 4.8) < 1.0:
                g.set(x, 1, z, p["wall"])
                if math.hypot((x - mid - 2) / 5.0, (z - 5) / 3.2) < 1.0:
                    g.set(x, 2, z, p["stone"])
    g.ruin_tops(mid - 9, 1, mid + 9, 10, 1, 2, 0.4, 2.6, scale=3.0, salt=3)
    # the frame on the terrace, its right side swallowed by the trunk
    cells = broken_frame(g, mid - 5, 2, 6, [(0, 6), (1, 6), (0, 5), (4, 6)])
    rubble(g, cells, p["rubble"])
    # the trunk: 2 x 2 up to the fork, against the frame's right post
    tx, tz = mid, 5
    for y in range(1, 9):
        for dx in (0, 1):
            for dz in (0, 1):
                if (dx, dz) != (0, 1) or y > 2:
                    g.set(tx + dx, y, tz + dz, LOG)
    # two great branches and their flat crowns
    g.line((tx, 8, tz), (mid - 6, 13, 9), LOG)
    g.line((tx + 1, 8, tz), (mid + 7, 12, 4), LOG)
    g.line((tx, 5, tz), (mid - 7, 8, 3), LOG)
    for cx, cy, cz, r in ((mid - 6, 13, 9, 5.0), (mid + 7, 12, 4, 4.6), (mid - 7, 8, 3, 2.8)):
        g.disc(cx, cz, r, cy + 1, LEAVES)
        g.disc(cx, cz, r - 1.8, cy + 2, LEAVES)
    # roots snaking out over the ground
    for ex, ez in ((mid - 2, 11), (mid + 6, 9), (mid + 5, 1), (mid - 1, 1), (mid + 9, 6)):
        g.line((tx, 1, tz), (ex, 1, ez), "acacia_log[axis=x]" if abs(ex - tx) > abs(ez - tz) else "acacia_log[axis=z]")
    for x, z in ((mid - 8, 13), (mid + 8, 13)):
        brazier(g, x, z)
    dress(g, 16, burnt=0.15)
    return [guard(mid - 4, 16), guard(mid + 3, 16)]


@layout("watchfire", "Watchfire", size=(23, 22, 25),
        blurb="A tall acacia lookout, one leg burnt through, its signal fire still burning on the tilted deck.")
def watchfire(g, p):
    """A stilted watchtower with a broken leg, its deck sagging to the ground on one side; the frame beside it."""
    mid = g.W // 2
    court(g, p["floor"], 9.5, cz=14, under="dirt")
    # the tower: four legs on red sandstone feet, braces, a deck at y 8
    x0, z0 = 2, 3
    legs = [(x0, z0), (x0 + 5, z0), (x0, z0 + 5), (x0 + 5, z0 + 5)]
    for i, (x, z) in enumerate(legs):
        h = 8 if i != 3 else 2
        g.set(x, 1, z, p["trim"])
        for y in range(2, h + 1):
            g.set(x, y, z, LOG)
    for y in (4,):
        for a, b in ((0, 1), (0, 2), (1, 3)):
            (ax, az), (bx, bz) = legs[a], legs[b]
            g.line((ax, y, az), (bx, y, bz), "acacia_planks")
    # the deck: level over the three good legs, sagging down to the ground at the broken corner
    for x in range(x0, x0 + 6):
        for z in range(z0, z0 + 6):
            sag = max(0, (x - x0) + (z - z0) - 5)
            y = 9 - sag
            if y >= 1:
                g.set(x, y, z, "acacia_planks" if (x + z) % 3 else "stripped_acacia_log[axis=x]")
    g.line((x0 + 5, 4, z0 + 5), (x0 + 5, 9, z0 + 1), "acacia_planks")
    g.walls(x0, z0, x0 + 5, z0 + 5, 10, 10, "acacia_fence")
    g.ruin_tops(x0, z0, x0 + 5, z0 + 5, 10, 10, 9.0, 10.8, scale=2.0, salt=5)
    g.set(x0 + 1, 10, z0 + 1, p["trim"])
    g.set(x0 + 1, 11, z0 + 1, "campfire[lit=true]")
    g.set(x0 + 3, 10, z0 + 1, "campfire[lit=true]")
    # the broken leg lies across the ground
    g.line((x0 + 6, 1, z0 + 7), (x0 + 10, 1, z0 + 9), "acacia_log[axis=x]")
    # the frame beside the tower on a stepped plinth
    for x in range(mid - 3, mid + 4):
        for z in range(4, 8):
            g.set(x, 1, z, p["stone"])
        g.set(x, 1, 8, stairs("red_sandstone_stairs", "north"))
    cells = broken_frame(g, mid - 2, 2, 5, [(4, 6), (3, 6), (2, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    for x, z in ((mid + 5, 5), (mid + 5, 9)):
        brazier(g, x, z)
    acacia(g, g.W - 3, 2, 9, lean=(-2, 1), r=3.4)
    dress(g, 14)
    return [guard(mid - 2, 14), guard(mid + 4, 14)]


@layout("scorched_ring", "Scorched Ring", size=(25, 16, 25),
        blurb="A wildfire's black scar in the grass round a ring of standing stones, the frame fallen in its middle.")
def scorched_ring(g, p):
    """Round1's Fallen Ring, burnt: a ring of red sandstone menhirs, the frame toppled flat in a blackened circle,
    one post of it still upright, charred acacia stumps and the fire's last embers."""
    mid = g.W // 2
    cz = 11
    # the scorch: black at the middle fading to dead grass at its ragged edge
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot(x - mid, z - cz) + (g.noise3_at(3.0, x, 0, z) - 0.5) * 4
            if d < 6.5:
                g.set(x, 0, z, p["scorch"])
            elif d < 10.5:
                g.set(x, 0, z, Mix(("coarse_dirt", 3), ("grass_block", 2), ("brown_terracotta", 1), patch=0.5))
            if d < 10.5:
                g.set(x, -1, z, "dirt")
    # the frame toppled flat, its head to the back, two cells snapped off, one post still standing at the foot
    flat_frame(g, mid - 2, 0, cz - 4, lambda i, j: (i, j) not in ((4, 0), (3, 0), (0, 3)))
    for y in (1, 2, 3):
        g.set(mid - 2, y, cz + 3, FRAME, force=True)
    g.set(mid + 2, 1, cz + 3, FRAME, force=True)
    # the ring of standing stones, some leaning stumps
    n = 9
    for k in range(n):
        a = 2 * math.pi * (k + 0.3) / n
        x = int(round(mid + math.cos(a) * 9.2))
        z = int(round(cz + math.sin(a) * 8.6))
        if z > cz + 6 and abs(x - mid) < 4:
            continue
        h = 2 + int(g.noise3_at(2.0, x, 0, z) * 5)
        g.set(x, 1, z, p["trim"])
        for y in range(2, h + 1):
            g.set(x, y, z, p["stone"])
        if h >= 5 and k % 2:
            g.set(x, h + 1, z, "campfire[lit=true]")
        elif h <= 3:
            g.set(x + (1 if x < mid else -1), 1, z, p["rubble"])
    # two trilithons at the back: stone posts two wide with a lintel across, one of them cracked through
    for x0, z0, h, whole in ((mid - 8, cz - 6, 5, True), (mid + 5, cz - 7, 6, False)):
        for dx in (0, 1, 3, 4):
            if not whole and dx == 4:
                continue
            g.set(x0 + dx, 1, z0, p["trim"])
            for y in range(2, h + 1):
                g.set(x0 + dx, y, z0, p["stone"])
        for dx in range(-1, 6 if whole else 4):
            g.set(x0 + dx, h + 1, z0, "cut_red_sandstone" if 0 <= dx <= 4 else slab("red_sandstone_slab"))
        if not whole:
            g.line((x0 + 4, 1, z0), (x0 + 6, 1, z0 + 3), p["stone"])
            g.set(x0 + 2, h + 2, z0, "campfire[lit=true]")
    # charred stumps of the acacias that burned
    for x, z, h in ((mid - 5, cz - 3, 3), (mid + 5, cz + 2, 2), (mid + 4, cz - 5, 4)):
        for y in range(1, h + 1):
            g.set(x, y, z, "stripped_acacia_log[axis=y]" if y < h else "basalt[axis=y]")
    g.line((mid + 5, 1, cz + 3), (mid + 8, 1, cz + 5), "stripped_acacia_log[axis=x]")
    # embers: little fires still burning in the scar
    for x, z in ((mid - 3, cz + 4), (mid + 4, cz - 1)):
        g.set(x, 0, z, "magma_block")
        g.set(x, 1, z, "campfire[lit=true]")
    acacia(g, 3, 21, 7, lean=(1, -1), r=3.0)
    dress(g, 12, burnt=0.4)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


@layout("plateau_edge", "Plateau Edge", size=(25, 20, 25),
        blurb="A red rock shelf at a plateau's foot; the frame is set into a niche cut in its face, a stair up to it.")
def plateau_edge(g, p):
    """The back of the plot rises in strata of terracotta and red sandstone, grass and an acacia on top; a stair
    climbs to a ledge where the broken frame stands in a niche; the knights wait on the flat below."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=16, under="dirt")
    strata = ["red_sandstone", "orange_terracotta", "terracotta", "red_sandstone", "yellow_terracotta",
              "orange_terracotta", "terracotta", "coarse_dirt"]
    # the cliff: its face wavy, higher towards the middle
    for x in range(g.W):
        face = 8 + int(round((g.noise3_at(4.0, x, 0, 3) - 0.5) * 4)) + (1 if abs(x - mid) < 5 else 0)
        top = 6 + int(round(g.noise3_at(3.0, x, 0, 0) * 2)) - (2 if x < 3 or x > g.W - 4 else 0)
        for z in range(0, face + 1):
            h = top - (1 if z >= face - 1 and g.noise3_at(2.0, x, 1, z) > 0.5 else 0)
            for y in range(1, h + 1):
                g.set(x, y, z, strata[y % len(strata)])
            g.set(x, h, z, "grass_block")
    # the ledge cut into the face, 3 up, and the niche behind it
    for x in range(mid - 4, mid + 5):
        for z in range(4, 12):
            for y in range(4, 14):
                g.set(x, y, z, AIR, force=True)
            if z >= 6:
                for y in range(1, 4):
                    g.set(x, y, z, p["stone"])
    for x in range(mid - 3, mid + 4):
        for z in range(4, 6):
            for y in range(4, 11):
                if abs(x - mid) <= 1 or y < 10:
                    g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 2, 4, 6, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, p["rubble"])
    # the stair down from the ledge to the flat, 3 wide
    for k in range(3):
        z = 12 + k
        for x in range(mid - 1, mid + 2):
            for y in range(1, 3 - k + 1):
                g.set(x, y, z, p["stone"])
            g.set(x, 3 - k, z, stairs("red_sandstone_stairs", "north"))
    for x in (mid - 2, mid + 2):
        for z in range(12, 15):
            for y in range(1, 15 - z + 1):
                g.set(x, y, z, p["wall"])
    for x in (mid - 4, mid + 4):
        brazier(g, x, 10, y=4)
    # a rockfall at the cliff's foot and an acacia on its top
    g.rubble_pile(4, 11, 3.0, 2.5, p["rubble"], top_mix="coarse_dirt")
    g.rubble_pile(g.W - 4, 12, 2.6, 2.0, p["rubble"])
    acacia(g, 4, 3, 13, lean=(2, 1), r=3.6, y0=g.top_y(4, 3) + 1)
    acacia(g, g.W - 4, 2, 11, lean=(-1, 2), r=2.8, y0=g.top_y(g.W - 4, 2) + 1)
    dress(g, 14, burnt=0.2)
    return [guard(mid - 3, 17), guard(mid + 3, 17)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [great_acacia, watchfire, scorched_ring, plateau_edge]
DROPPED = [hut_ring]
