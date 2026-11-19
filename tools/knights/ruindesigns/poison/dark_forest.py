"""Poison's dark forest ruins: mossy cobblestone and dark oak under a heavy canopy, cobwebs in every corner, mushrooms
in the gloom and a pale green froglight here and there."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, slab, stairs)
from ruindesigns.poison.common import (froglamp, giant_mushroom, hang_vines, hanging_froglight, poison_dress, pool,
                                       webs_in_corners)

P = dict(
    floor=Mix(("mossy_cobblestone", 3), ("cobblestone", 2), ("mossy_stone_bricks", 2), ("podzol", 1), patch=0.5),
    wall=Mix(("mossy_cobblestone", 4), ("cobblestone", 2), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1),
             patch=0.4),
    ground=Mix(("grass_block", 5), ("podzol", 3), ("coarse_dirt", 1), ("moss_block", 1), patch=0.55),
    rubble=Mix(("mossy_cobblestone", 3), ("cobblestone", 2), ("dark_oak_planks", 1)),
    light="verdant_froglight[axis=y]",
)

AREA = Area(element="poison", id="dark_forest", name="Dark Forest", biomes="dark forest", setting=SURFACE,
            ground="grass_block", under="dirt", palette=P,
            blurb="Mossy stone and dark oak lost under the canopy, cobwebs in every corner.")

LOG = "dark_oak_log[axis=y]"
LEAVES = "dark_oak_leaves[persistent=true]"


def forest_floor(g, r=12.0, cz=None):
    court(g, P["ground"], r, cz=cz, under="dirt")


def dark_oak(g, x, z, h, spread=4, y0=1, lean=(0, 0), thick=2):
    """A dark oak: a 2 x 2 trunk (thick=1 for a young one), a broad flat crown two or three layers deep, a few
    branches out to it. Returns its crown's height."""
    top = y0 + h
    for ox in range(thick):
        for oz in range(thick):
            g.line((x + ox, y0, z + oz), (x + ox + lean[0], top, z + oz + lean[1]), LOG)
    cx, cz = x + lean[0] + (thick - 1) * 0.5, z + lean[1] + (thick - 1) * 0.5
    for dx in range(-spread - 1, spread + 2):
        for dz in range(-spread - 1, spread + 2):
            for dy in (0, 1, 2):
                d = math.hypot(dx - (thick - 1) * 0.5 + 0.0, dz - (thick - 1) * 0.5) + abs(dy - 0.7) * 1.0
                if d <= spread + 0.8 - g.rng.random() * 0.9:
                    xx, zz = int(round(cx + dx - (thick - 1) * 0.5)), int(round(cz + dz - (thick - 1) * 0.5))
                    if g.get(xx, top + dy, zz) is None:
                        g.set(xx, top + dy, zz, LEAVES)
    for ox in range(thick):
        for oz in range(thick):
            g.set(x + ox + lean[0], top, z + oz + lean[1], LOG)
            g.set(x + ox + lean[0], top + 1, z + oz + lean[1], LOG)
    # branches reaching into the crown
    for bx, bz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        if g.chance(0.6):
            sx, sz = x + lean[0] + (thick if bx > 0 else 0) * (1 if bx > 0 else 0) + (-1 if bx < 0 else 0), \
                z + lean[1] + (thick if bz > 0 else 0) * (1 if bz > 0 else 0) + (-1 if bz < 0 else 0)
            g.line((x + lean[0] + (thick - 1 if bx > 0 else 0), top - 2, z + lean[1] + (thick - 1 if bz > 0 else 0)),
                   (sx + bx * 2, top, sz + bz * 2), "dark_oak_log[axis=x]" if bx else "dark_oak_log[axis=z]")
    return top


def vines_under(g, chance=0.2):
    """Vines hanging off the leaves' edges and the walls (the forest's gloom)."""
    hang_vines(g, 0, 0, g.W - 1, g.D - 1, chance=chance, y_lo=3, y_hi=g.H - g.G - 2, max_len=5)


# ---------------------------------------------------------------- 1 Lost Chapel (round 1's Ruined Shrine, in the woods)

@layout("lost_chapel", "Lost Chapel", size=(23, 20, 25),
        blurb="A chapel lost in the dark forest: its walls broken down, its roof beams fallen in, the frame at its altar.")
def lost_chapel(g, p):
    """A nave of mossy cobblestone, its walls broken to jagged stumps and its door arch half fallen; dark oak roof
    beams slanted down into it; a dark oak grown up through its side; the frame on the altar step in the apse."""
    mid = g.W // 2
    forest_floor(g, cz=12)
    X1, X2, Z1, Z2 = mid - 6, mid + 6, 3, 21
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            g.set(x, 0, z, p["floor"])
    g.walls(X1, Z1, X2, Z2, 1, 9, p["wall"])
    # buttresses
    for z in (6, 11, 16):
        for x in (X1 - 1, X2 + 1):
            for y in range(1, 6):
                g.set(x, y, z, "mossy_stone_bricks")
            g.set(x, 6, z, stairs("mossy_stone_brick_stairs", "east" if x > mid else "west"))
    # the apse's back wall stands highest, a round window in it
    for x in range(X1, X2 + 1):
        for y in range(10, 14 - abs(x - mid) // 2):
            g.set(x, y, Z1, p["wall"])
    # the door: a gap in the front wall
    for x in range(mid - 2, mid + 3):
        for y in range(1, 7):
            g.set(x, y, Z2, AIR, force=True)
    # break the walls down
    g.ruin_tops(X1 - 1, Z1 + 1, X2 + 1, Z2, 2, 10, 1.5, 7.0, scale=3.5, salt=11)
    g.ruin_tops(X1, Z1, X2, Z1, 6, 14, 6.0, 13.0, scale=3.0, salt=12)
    g.smash(X2, 4, 13, 2.6)
    g.smash(X1, 3, 18, 2.2)
    # a gothic window in the back wall
    for y in range(4, 9):
        g.set(mid, y, Z1, AIR, force=True)
    for x in (mid - 1, mid + 1):
        for y in range(5, 8):
            g.set(x, y, Z1, AIR, force=True)
    # the altar step and the frame on it
    for x in range(mid - 4, mid + 5):
        for z in range(Z1 + 1, Z1 + 4):
            g.set(x, 1, z, "mossy_stone_bricks")
        g.set(x, 1, Z1 + 4, stairs("mossy_stone_brick_stairs", "north"))
    cells = broken_frame(g, mid - 2, 2, Z1 + 2, [(4, 6), (3, 6), (4, 5), (0, 6)])
    # the roof beams: fallen in, slanting from the wall tops down onto the floor
    for (x1, z1, y1), (x2, z2, y2) in (((X1 + 1, 9, 7), (X1 + 4, 12, 1)), ((X2 - 1, 17, 6), (mid + 2, 19, 1))):
        g.line((x1, y1, z1), (x2, y2, z2), "dark_oak_log[axis=x]")
        g.set(x1, y1 - 1, z1, p["wall"])
        for y in range(1, y1 - 1):
            if g.get(x1, y, z1) is None:
                g.set(x1, y, z1, p["wall"])
    # a dark oak grown up through the east wall's breach, its crown over the nave
    dark_oak(g, X1 - 3, 1, 11, spread=5)
    # pews: broken rows of dark oak stairs
    for z in (10, 13):
        for x in list(range(X1 + 2, mid - 1)) + list(range(mid + 2, X2 - 1)):
            if g.chance(0.6) and g.get(x, 1, z) is None:
                g.set(x, 1, z, stairs("dark_oak_stairs", "south"))
    for x in (mid - 4, mid + 4):
        froglamp(g, x, Z1 + 1, y=2, post="mossy_cobblestone_wall", h=1)
    webs_in_corners(g, X1, 1, Z1, X2, 9, Z2, chance=0.25)
    rubble(g, cells, p["rubble"], spread=3)
    g.settle_rubble(0.5, 1.2, p["rubble"], protect=lambda x, z: abs(x - mid) <= 4 and 12 <= z <= 20)
    vines_under(g, 0.07)
    poison_dress(g, 10, webs=0.3)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


# ---------------------------------------------------------------- 2 Fairy Ring

@layout("fairy_ring", "Fairy Ring", size=(25, 20, 25),
        blurb="A ring of old dark oaks round a clearing of podzol; the frame stands between the two tallest.")
def fairy_ring(g, p):
    """Six old dark oaks of every height in a ring round a clearing (the user, November 18, 2026: "remove the
    mushrooms, just have trees"); a dark ring of coarse dirt and ferns on the ground inside them; the frame at the
    back between the two tallest, an old stone sunk at the ring's heart."""
    mid = g.W // 2
    forest_floor(g, cz=12)
    CZ = 11
    # the clearing: podzol, with the dark fairy ring of coarse dirt and ferns
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot(x - mid, (z - CZ) * 1.05)
            if d <= 9.5:
                g.set(x, 0, z, Mix(("podzol", 3), ("grass_block", 2), ("moss_block", 1), patch=0.5))
            if 7.6 <= d <= 8.6 and g.chance(0.85):
                g.set(x, 0, z, "coarse_dirt" if g.chance(0.6) else "podzol")
                if g.get(x, 0, z) == "minecraft:podzol" and g.chance(0.5):
                    g.set(x, 1, z, g.rng.choice(("fern", "grass")))
    cells = broken_frame(g, mid - 2, 1, 3, [(0, 6), (1, 6), (2, 6), (0, 5)])
    # the ring of dark oaks (angles in half-turns from east, round the back to the front sides), the tallest
    # either side of the frame, the ring open at the front so the way in shows the frame
    ring = [(0.92, 12, 4, 2), (0.08, 11, 4, 2), (1.06, 8, 3, 1), (-0.06, 8, 3, 1), (0.68, 9, 3, 2), (0.32, 10, 3, 1)]
    for a, h, r, thick in ring:
        x = int(round(mid + math.cos(a * math.pi) * 9.4))
        z = int(round(CZ - math.sin(a * math.pi) * 9.4))
        x = min(max(x, r + 1), g.W - 2 - r)
        z = min(max(z, 2), g.D - 3)
        dark_oak(g, x, z, h, spread=r, thick=thick)
    # the old stone at its heart, sunk and mossed, a froglight in a hollow of it
    for x, z in ((mid, CZ + 1), (mid + 1, CZ + 1), (mid, CZ + 2)):
        g.set(x, 1, z, "mossy_cobblestone")
    g.set(mid + 1, 1, CZ + 2, slab("mossy_cobblestone_slab"))
    g.set(mid - 1, 0, CZ + 1, p["light"])
    for x in (mid - 4, mid + 4):
        froglamp(g, x, 5, post="dark_oak_fence", h=2)
    # dark oaks closing in behind it
    dark_oak(g, 1, 0, 12, spread=4)
    dark_oak(g, g.W - 3, 1, 10, spread=4)
    rubble(g, cells, p["rubble"], spread=3)
    vines_under(g, 0.08)
    poison_dress(g, 8, webs=0.4, mushrooms=0.0)
    return [guard(mid - 4, 15), guard(mid + 4, 15)]


# ---------------------------------------------------------------- 3 Leaning Giant

@layout("leaning_giant", "Leaning Giant", size=(25, 20, 25),
        blurb="A giant dark oak fell and caught on a broken tower: its trunk slants over the frame like a roof beam.")
def leaning_giant(g, p):
    """The stump of a round watchtower at the back right; a fallen giant dark oak slants from its root plate at the
    left up onto the tower's broken top, over the frame; its dead crown hangs past the tower."""
    mid = g.W // 2
    forest_floor(g, cz=12)
    # the tower stump: a ring of mossy cobblestone, broken off high at the back, low at the front
    TX, TZ = mid + 6, 5
    g.cylinder(TX, TZ, 3.3, 1, 12, p["wall"], r_in=1.6)
    g.cylinder(TX, TZ, 2.2, 0, 0, p["floor"])
    g.ruin_tops(TX - 4, TZ - 4, TX + 4, TZ + 4, 3, 12, 4.0, 12.0, scale=2.5, salt=21)
    for x in range(TX - 4, TX + 5):
        for z in range(TZ + 1, TZ + 5):
            for y in range(1, 4):
                if abs(x - TX) <= 1:
                    g.set(x, y, z, AIR, force=True)
    # the root plate: an upturned disc of dirt and roots at the left, a hollow before it
    for dz in range(-4, 5):
        for dy in range(0, 8):
            if math.hypot(dz, dy - 3.2) <= 3.8 + g.rng.uniform(-0.4, 0.3):
                g.set(2, dy, 6 + dz, Mix(("rooted_dirt", 3), ("dirt", 2), ("coarse_dirt", 1), patch=0.3))
                if g.chance(0.3):
                    g.set(3, dy, 6 + dz, "dark_oak_wood[axis=x]" if dy == 3 and dz == 0 else "rooted_dirt")
    # the trunk: 2 x 2, slanting from the root plate up onto the tower's top, a little past it
    for oz in (5, 6):
        for oy in (0, 1):
            g.line((3, 3 + oy, oz), (TX + 2, 11 + oy, oz), "dark_oak_log[axis=x]")
    # its dead crown past the tower: bare branches, a few leaves left
    for (dx, dy, dz) in ((3, 2, -3), (3, 4, 2), (2, -2, 3), (4, 0, -1)):
        g.line((TX + 2, 12, 5), (TX + 2 + dx, 12 + dy, 5 + dz), "dark_oak_log[axis=x]")
        for ex in (-1, 0, 1):
            for ez in (-1, 0, 1):
                if g.chance(0.5):
                    g.set(TX + 2 + dx + ex, 13 + dy, 5 + dz + ez, LEAVES)
    # the frame under the trunk, the beam crushing its top corner
    cells = broken_frame(g, mid - 4, 1, 6, [(4, 6), (4, 5), (3, 6), (2, 6)])
    for y in range(1, 4):
        g.set(mid - 6, y, 9, p["wall"])
    froglamp(g, mid - 6, 9, y=4, post="mossy_cobblestone_wall", h=0)
    # a living dark oak at the front left, its crown over the clearing's edge
    dark_oak(g, 1, 16, 9, spread=4)
    court(g, p["floor"], 4.5, cx=mid, cz=15, ragged=3.0, under="dirt")
    rubble(g, cells, p["rubble"], spread=3)
    webs_in_corners(g, TX - 2, 1, TZ - 2, TX + 2, 10, TZ + 2, chance=0.25)
    vines_under(g, 0.08)
    poison_dress(g, 12, webs=0.3)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


# ---------------------------------------------------------------- 4 Crypt Pit

@layout("crypt_pit", "Crypt Pit", size=(23, 22, 25), ground=6,
        blurb="The roof of a crypt has fallen in, leaving a pit lined with tombs; the frame stands in it, half below.")
def crypt_pit(g, p):
    """A walled pit where a crypt's roof gave way: steps down at its front, tomb niches in its walls, a stub of its
    vaulting still arching over the back; the frame stands on its floor, its top above the forest floor."""
    mid = g.W // 2
    forest_floor(g, cz=12)
    X1, X2, Z1, Z2, FL = mid - 5, mid + 5, 2, 11, -4
    # dig it and line it: mossy stone bricks, a floor of stone tiles four down
    for x in range(X1 - 1, X2 + 2):
        for z in range(Z1 - 1, Z2 + 2):
            wall = x in (X1 - 1, X2 + 1) or z in (Z1 - 1, Z2 + 1)
            for y in range(FL, 1):
                g.set(x, y, z, Mix(("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2), ("stone_bricks", 1),
                                   patch=0.4) if wall else AIR, force=True)
            g.set(x, FL - 1, z, "stone_bricks" if (x + z) % 2 else "mossy_stone_bricks", force=True)
    # the steps down in the front, 3 wide, through the front wall
    for k in range(4):
        z = Z2 + 1 - k
        for x in range(mid - 1, mid + 2):
            g.set(x, -k, z, stairs("mossy_stone_brick_stairs", "north"), force=True)
            for yy in range(FL - 1, -k):
                g.set(x, yy, z, "stone_bricks", force=True)
            for yy in range(-k + 1, 1):
                g.set(x, yy, z, AIR, force=True)
    # tomb niches in its side walls: a slab lid on a tomb
    for z in (4, 7):
        for x, f in ((X1 - 1, "east"), (X2 + 1, "west")):
            g.set(x, FL + 1, z, AIR, force=True)
            g.set(x, FL + 2, z, AIR, force=True)
            g.set(x, FL, z, "chiseled_stone_bricks", force=True)
            g.set(x, FL + 1, z, "cobweb", force=True)
    # the frame on the floor, its top three rows standing above the forest floor
    cells = broken_frame(g, mid - 2, FL, 4, [(4, 6), (3, 6), (0, 6)])
    # a broken stub of the vault arching over the back
    for x in range(X1 - 1, X2 + 2):
        h = 3 + int(round(2.5 * math.cos((x - mid) / 6.0 * math.pi / 2)))
        if x > mid + 2:
            continue
        g.set(x, h, Z1 - 1, p["wall"])
        for y in range(1, h):
            if abs(x - mid) >= 5:
                g.set(x, y, Z1 - 1, p["wall"])
    for x in range(X1 - 1, mid + 3):
        h = 3 + int(round(2.5 * math.cos((x - mid) / 6.0 * math.pi / 2)))
        for y in range(1, h):
            if g.get(x, y, Z1 - 1) is None and abs(x - mid) >= 3:
                g.set(x, y, Z1 - 1, p["wall"])
    # the crypt's ground-level rim: a low broken wall round the pit, gravestones before it
    for x in range(X1 - 1, X2 + 2):
        for z in range(Z1 - 1, Z2 + 2):
            if (x in (X1 - 1, X2 + 1) or z in (Z1 - 1,)) and g.get(x, 1, z) is None and g.chance(0.55):
                g.set(x, 1, z, p["wall"])
    for x, z in ((mid - 7, 14), (mid - 8, 18), (mid + 7, 15), (mid + 8, 19), (mid - 6, 21)):
        g.set(x, 1, z, "mossy_stone_bricks")
        g.set(x, 2, z, "mossy_stone_brick_wall" if g.chance(0.5) else slab("mossy_stone_brick_slab"))
    for x in (X1, X2):
        g.set(x, FL, 9, p["light"], force=True)
    froglamp(g, mid - 3, 13, post="mossy_cobblestone_wall", h=1)
    froglamp(g, mid + 3, 13, post="mossy_cobblestone_wall", h=1)
    dark_oak(g, X2 + 2, 1, 11, spread=4)
    dark_oak(g, 1, 9, 9, spread=3, thick=1)
    webs_in_corners(g, X1 - 1, FL + 1, Z1 - 1, X2 + 1, 0, Z2 + 1, chance=0.35)
    rubble(g, cells, p["rubble"], spread=2)
    vines_under(g, 0.08)
    poison_dress(g, 10, webs=0.3)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


# ---------------------------------------------------------------- 5 Hedge Garden

@layout("hedge_garden", "Hedge Garden", size=(25, 16, 25),
        blurb="An old walled garden gone wild: dark oak hedges on mossy footings, the frame at its overgrown heart.")
def hedge_garden(g, p):
    """A garden of dark oak hedges on low mossy walls, in broken concentric squares; their front torn open; the
    frame in the overgrown heart with a dry fountain basin before it; a gate pier with an urn."""
    mid = g.W // 2
    forest_floor(g, cz=12)
    court(g, Mix(("mossy_cobblestone", 2), ("podzol", 2), ("grass_block", 2), ("moss_block", 1), patch=0.5), 10.5,
          cz=11, under="dirt")

    def hedge(x1, z1, x2, z2, h, gaps):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if not (x in (x1, x2) or z in (z1, z2)):
                    continue
                if any(gx1 <= x <= gx2 and gz1 <= z <= gz2 for gx1, gz1, gx2, gz2 in gaps):
                    continue
                g.set(x, 1, z, p["wall"])
                top = h - (1 if g.noise3_at(2.0, x, 1, z) < 0.35 else 0)
                for y in range(2, top + 1):
                    g.set(x, y, z, LEAVES)
    # the outer square: its front wide open (fallen), its sides broken
    hedge(mid - 10, 1, mid + 10, 21, 3, [(mid - 7, 17, mid + 7, 21), (mid - 10, 12, mid - 10, 14), (mid + 10, 6, mid + 10, 7)])
    # the inner square round the heart: open at the front
    hedge(mid - 5, 2, mid + 5, 10, 4, [(mid - 2, 10, mid + 2, 10), (mid + 5, 4, mid + 5, 5)])
    # the frame in the heart, the fountain basin before it (dry, mossy, a froglight in its middle)
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (4, 5), (3, 6), (0, 6)])
    for x in range(mid - 2, mid + 3):
        for z in range(6, 10):
            rim = x in (mid - 2, mid + 2) or z in (6, 9)
            if rim:
                g.set(x, 1, z, "mossy_stone_bricks")
            else:
                g.set(x, 0, z, "moss_block")
                g.set(x, 1, z, "moss_carpet")
    g.set(mid, 1, 7, "mossy_cobblestone_wall")
    g.set(mid, 2, 7, p["light"])
    # gate piers at the outer front, an urn (a pot) on one, the other broken
    for x in (mid - 8, mid + 8):
        for y in range(1, 4 if x < mid else 2):
            g.set(x, y, 21, "mossy_stone_bricks")
    g.set(mid - 8, 4, 21, "decorated_pot")
    g.set(mid + 9, 1, 20, slab("mossy_stone_brick_slab"))
    # a dark oak shading the garden from its back corner
    dark_oak(g, 1, 0, 10, spread=4)
    for x, z in ((mid - 8, 8), (mid + 8, 13)):
        froglamp(g, x, z, post="dark_oak_fence", h=2)
    rubble(g, cells, p["rubble"], spread=2)
    vines_under(g, 0.06)
    poison_dress(g, 12, webs=0.25)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [lost_chapel, fairy_ring, hedge_garden]
DROPPED = [leaning_giant, crypt_pit]
