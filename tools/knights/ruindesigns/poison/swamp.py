"""Poison's swamp ruins (swamp, mangrove swamp): mud bricks and packed mud sinking into the bog, mangrove stilts and
roots, walled-in black pools under lily pads, vines, moss and froglights."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, slab, stairs)
from ruindesigns.poison.common import (froglamp, giant_mushroom, hang_vines, hanging_froglight, poison_dress, pool,
                                       webs_in_corners)

P = dict(
    floor=Mix(("mud_bricks", 3), ("packed_mud", 2), ("mud", 2), ("muddy_mangrove_roots", 1), patch=0.55),
    wall=Mix(("mud_bricks", 5), ("packed_mud", 2), ("mossy_cobblestone", 1), patch=0.4),
    bank=Mix(("mud", 2), ("grass_block", 5), ("muddy_mangrove_roots", 1), ("moss_block", 1.5), patch=0.6),
    rim=Mix(("muddy_mangrove_roots", 2), ("mud_bricks", 2), ("packed_mud", 1), patch=0.4),
    rubble=Mix(("mud_bricks", 3), ("packed_mud", 2), ("mossy_cobblestone", 1)),
    log="mangrove_log[axis=y]",
    light="verdant_froglight[axis=y]",
)

AREA = Area(element="poison", id="swamp", name="Swamp", biomes="swamp, mangrove swamp", setting=SURFACE,
            ground="grass_block", under="dirt", palette=P,
            blurb="Mud brick and mangrove ruins sinking into the bog.")

LOG = "mangrove_log[axis=y]"
LEAVES = "mangrove_leaves[persistent=true]"


def mangrove(g, x, z, h, lean=(0, 0), spread=3, y0=1, roots=True):
    """A mangrove: arching roots round its foot, a trunk (leaning: a face-connected line), a ragged crown with
    propagules hanging under it. Returns its top."""
    tx, tz = x + lean[0], z + lean[1]
    top = y0 + h
    g.line((x, y0, z), (tx, top, tz), LOG)
    if roots:
        for dx, dz in ((2, 0), (-2, 0), (0, 2), (0, -2), (2, 1), (-1, -2)):
            if g.chance(0.75):
                g.set(x + dx, y0, z + dz, "mangrove_roots")
                g.set(x + dx, y0 + 1, z + dz, "mangrove_roots")
                g.set(x + dx // 2 if abs(dx) == 2 else x + dx, y0 + 2, z + dz // 2 if abs(dz) == 2 else z + dz,
                      "mangrove_roots")
    for dx in range(-spread, spread + 1):
        for dz in range(-spread, spread + 1):
            for dy in (0, 1):
                d = math.hypot(dx, dz) + dy * 1.4
                if d <= spread + 0.3 - g.rng.random() * 0.9:
                    if g.get(tx + dx, top + dy, tz + dz) is None:
                        g.set(tx + dx, top + dy, tz + dz, LEAVES)
    g.set(tx, top, tz, LOG)
    # propagules and a few vines under the crown's edge
    for dx in range(-spread, spread + 1):
        for dz in range(-spread, spread + 1):
            s = g.get(tx + dx, top, tz + dz)
            if s and "mangrove_leaves" in s and g.get(tx + dx, top - 1, tz + dz) is None and g.chance(0.18):
                g.set(tx + dx, top - 1, tz + dz, "mangrove_propagule[hanging=true,age=4]")
    return tx, top, tz


def bank(g, r, cz=None, rx=None, rz=None):
    """The muddy ground of the plot: mud, grass and roots, ragged at its edge."""
    court(g, P["bank"], r, cz=cz, rx=rx, rz=rz, under="mud")


def lilies(g, cells, chance):
    for x, z in cells:
        if g.get(x, 1, z) is None and g.chance(chance):
            g.set(x, 1, z, "lily_pad")


# ---------------------------------------------------------------- 1 Stilt Shrine

@layout("stilt_shrine", "Stilt Shrine", size=(25, 20, 25),
        blurb="A mud brick shrine on mangrove stilts over a black pool, a broken stair down to the bank.")
def stilt_shrine(g, p):
    """A deck on mangrove stilts over a walled pool at the back, the frame on it, a stair down to the bank in front."""
    mid = g.W // 2
    bank(g, 11.5, cz=13)
    water = pool(g, 1, 1, g.W - 2, 15, p["rim"], bed="mud", depth=2,
                 region=lambda x, z: math.hypot((x - mid) / 10.6, (z - 7.0) / 7.2) <= 1.0 - 0.12 * g.noise3_at(3.0, x, 0, z))
    # the deck: 4 up, mud bricks on a mangrove-plank skin, its south-east corner fallen in
    DY = 4
    for x in range(mid - 5, mid + 6):
        for z in range(2, 11):
            if x >= mid + 3 and z >= 8:
                continue
            g.set(x, DY, z, p["wall"] if 3 <= z <= 9 and abs(x - mid) <= 4 else "mangrove_planks")
    # stilts: through the water to the bed, standing a little over the deck at the corners
    for x in (mid - 5, mid - 1, mid + 1, mid + 5):
        for z in (2, 6, 10):
            if x >= mid + 3 and z >= 8:
                continue
            top = DY + (2 if x in (mid - 5, mid + 5) and z in (2, 10) else 0)
            for y in range(-2, top + 1):
                g.set(x, y, z, LOG, force=True)
            if top > DY:
                g.set(x, top + 1, z, p["light"])
    # the fallen corner: its planks slanting down into the pool
    g.line((mid + 3, DY - 1, 8), (mid + 5, 0, 10), "stripped_mangrove_log[axis=y]")
    g.set(mid + 4, 1, 9, slab("mangrove_slab"))
    # a railing of mangrove fence, broken
    for x in range(mid - 5, mid + 6):
        if g.get(x, DY, 2) and g.chance(0.7):
            g.set(x, DY + 1, 2, "mangrove_fence")
    for z in range(3, 10):
        if g.chance(0.6):
            g.set(mid - 5, DY + 1, z, "mangrove_fence")
    cells = broken_frame(g, mid - 2, DY + 1, 4, [(4, 6), (4, 5), (3, 6)])
    # the stair down to the bank, 3 wide, mud brick stairs on a mud brick body
    for k in range(DY):
        z = 11 + k
        y = DY - k
        for x in range(mid - 1, mid + 2):
            g.set(x, y, z, stairs("mud_brick_stairs", "north"))
            for yy in range(-1, y):
                g.set(x, yy, z, p["wall"], force=True)
        for x in (mid - 2, mid + 2):
            for yy in range(-1, y + 1):
                g.set(x, yy, z, p["wall"], force=True)
            if k < 2:
                g.set(x, y + 1, z, "mud_brick_wall")
    # mangroves in the pool's corners, roots standing in the water
    mangrove(g, 3, 5, 9, lean=(1, -1), spread=3, y0=-1)
    mangrove(g, g.W - 4, 7, 7, lean=(-1, -1), spread=3, y0=-1)
    # the shrine's roof: two tall corner posts at the back, a broken pitch of mangrove stairs between them
    for x in (mid - 4, mid + 4):
        for y in range(DY + 1, DY + 7):
            g.set(x, y, 3, LOG)
    for i, x in enumerate(range(mid - 5, mid + 6)):
        h = DY + 7 + (5 - abs(x - mid)) // 2
        if x in (mid + 2, mid + 3):
            continue
        g.set(x, h, 3, "stripped_mangrove_log[axis=z]")
        if abs(x - mid) <= 4:
            for yy in range(DY + 7, h):
                g.set(x, yy, 3, "mangrove_planks" if abs(x - mid) == 4 or yy == h - 1 else AIR)
    g.set(mid, DY + 6, 3, "chain[axis=y]")
    g.set(mid, DY + 5, 3, "verdant_froglight[axis=y]")
    lilies(g, [(x, z) for (x, z) in water], 0.25)
    rubble(g, cells, p["rubble"], spread=3)
    hang_vines(g, 0, 0, g.W - 1, 12, chance=0.3, y_lo=2, y_hi=10)
    for x, z in ((mid - 7, 15), (mid + 7, 15)):
        froglamp(g, x, z, post="mud_brick_wall", h=2)
    poison_dress(g, 12)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]



# ---------------------------------------------------------------- 2 Sunken Causeway

@layout("causeway", "Sunken Causeway", size=(25, 18, 25),
        blurb="A causeway of mud bricks run out across the bog to the frame on its island, under a broken arch.")
def causeway(g, p):
    """Two black pools either side of a mud brick causeway; it widens into a landing where the knights stand, and runs
    on under a broken arch to a little island at the back with the frame sinking into it."""
    mid = g.W // 2
    bank(g, 12.0, cz=12)
    # the bog: both sides of the causeway, front landing and back island left dry
    def wet(x, z):
        if abs(x - mid) <= 2:
            return False
        if 3 <= z <= 13:
            return 1 <= x <= g.W - 2 and math.hypot((x - mid) / 11.5, (z - 8) / 6.5) <= 1.0
        return False
    water = pool(g, 1, 1, g.W - 2, 14, p["rim"], bed="mud", depth=2, region=wet)
    # the causeway, 5 wide, its edges broken into the water here and there
    for z in range(0, g.D):
        for x in range(mid - 2, mid + 3):
            edge = abs(x - mid) == 2
            if edge and 4 <= z <= 13 and g.chance(0.25):
                g.set(x, 0, z, "water", force=True)
                g.set(x, -1, z, "mud", force=True)
                continue
            g.set(x, 0, z, p["floor"] if not edge else p["wall"])
            g.set(x, -1, z, "packed_mud")
    # posts along it, a froglight on every other one
    for z in (5, 9, 13):
        for x in (mid - 3, mid + 3):
            if g.get(x, 0, z) and "water" in g.get(x, 0, z):
                for y in (-1, 0):
                    g.set(x, y, z, LOG, force=True)
            g.set(x, 1, z, LOG)
            g.set(x, 2, z, LOG if z != 9 else p["light"])
    # the broken arch over it: two mud brick piers, half a lintel, the other half fallen in the water
    for x in (mid - 3, mid + 3):
        for y in range(1, 8 if x < mid else 5):
            g.set(x, y, 8, p["wall"])
        g.set(x, 0, 8, p["wall"], force=True)
    for x in range(mid - 3, mid + 1):
        g.set(x, 8, 8, p["wall"])
    g.set(mid - 1, 9, 8, slab("mud_brick_slab"))
    g.set(mid - 1, 7, 8, "chain[axis=y]")
    g.set(mid - 1, 6, 8, p["light"])
    g.line((mid + 4, 1, 10), (mid + 7, 1, 7), p["rubble"])
    # the island at the back, the frame sinking into it a row deep, tilting nothing but broken
    court(g, P["bank"], 4.5, cx=mid, cz=3, rx=6.5, rz=3.2, under="mud")
    cells = broken_frame(g, mid - 2, 0, 2, [(0, 6), (0, 5), (1, 6), (4, 2)])
    rubble(g, cells, p["rubble"], spread=2)
    # the landing in front: a ring of mud bricks, broken, round where the knights stand
    for x in range(g.W):
        for z in range(14, 23):
            d = math.hypot((x - mid) / 7.5, (z - 18) / 4.5)
            if d <= 1.0:
                g.set(x, 0, z, p["floor"])
            elif d <= 1.18 and g.chance(0.6):
                g.set(x, 0, z, p["wall"])
                if g.chance(0.4):
                    g.set(x, 1, z, p["wall"])
    # mangroves out in the bog, a dead stump
    mangrove(g, 2, 4, 7, lean=(0, 0), spread=3, y0=-1)
    mangrove(g, g.W - 3, 12, 8, lean=(0, 0), spread=2, y0=-1)
    g.line((g.W - 6, -1, 4), (g.W - 6, 2, 4), LOG)
    g.set(g.W - 6, 3, 4, "mangrove_roots")
    lilies(g, water, 0.3)
    hang_vines(g, 0, 0, g.W - 1, 14, chance=0.15)
    poison_dress(g, 10)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# ---------------------------------------------------------------- 3 Root Hollow

@layout("root_hollow", "Root Hollow", size=(25, 22, 25),
        blurb="A giant mangrove has grown over the frame: its roots arch round and over it like a cage.")
def root_hollow(g, p):
    """A great mangrove behind the frame, its roots arching out over it and down into the bog all round; pools
    between the roots, the knights on the muddy roots in front."""
    mid = g.W // 2
    bank(g, 12.0, cz=12)
    # muddy roots under the tree
    for x in range(g.W):
        for z in range(g.D):
            if math.hypot((x - mid) / 9.0, (z - 6) / 6.0) <= 1.0 - 0.15 * g.noise3_at(3.0, x, 0, z):
                g.set(x, 0, z, Mix(("muddy_mangrove_roots", 3), ("mud", 2), ("moss_block", 1), patch=0.5))
    cells = broken_frame(g, mid - 2, 1, 6, [(4, 6), (3, 6), (4, 5), (0, 6)])
    # the trunk, 2 x 2, behind the frame, a crown far over it
    TOP = 15
    for x in (mid - 1, mid):
        for z in (2, 3):
            for y in range(-1, TOP + 1):
                g.set(x, y, z, LOG)
    # roots: from the trunk out and down, arching (two straight runs each, face-connected)
    roots = [((-1, 7, 2), (-5, 6, 1), (-8, 0, 0)), ((0, 7, 2), (4, 6, 1), (8, 0, 1)),
             ((-1, 5, 3), (-4, 4, 6), (-6, 0, 9)), ((0, 5, 3), (4, 4, 6), (6, 0, 10)),
             ((-1, 10, 3), (-3, 9, 8), (-4, 0, 11)), ((0, 10, 3), (3, 9, 8), (4, 0, 12)),
             ((0, 4, 2), (3, 3, -1), (6, 0, -1)), ((-1, 4, 2), (-4, 2, 0), (-7, 0, 3))]
    for a, b, c in roots:
        a2 = (mid + a[0], a[1], max(a[2], 0))
        b2 = (mid + b[0], b[1], max(b[2], 0))
        c2 = (mid + c[0], c[1], max(c[2], 0))
        g.line(a2, b2, "mangrove_log[axis=x]")
        g.line(b2, c2, "mangrove_log[axis=y]")
        for dx, dz in ((1, 0), (-1, 0), (0, 1)):
            g.set(c2[0] + dx, 1, c2[2] + dz, "mangrove_roots")
    # the crown, wide and ragged, propagules and vines under it
    mangrove(g, mid, 3, TOP - 1, spread=6, y0=1, roots=False)
    # lower clumps hanging off the crown's edge
    for dx, dz in ((-6, 1), (6, 2), (-3, 7), (4, 6), (0, -3)):
        for ddx in (-1, 0, 1):
            for ddz in (-1, 0, 1):
                if g.chance(0.7) and g.get(mid + dx + ddx, TOP - 1, 3 + dz + ddz) is None:
                    g.set(mid + dx + ddx, TOP - 1, 3 + dz + ddz, LEAVES)
    for x in range(mid - 6, mid + 7):
        for z in range(0, 10):
            for y in range(TOP - 1, TOP + 3):
                d = math.hypot(x - mid, z - 3) + (y - TOP) * 1.2
                if 3.0 < d <= 6.5 - g.rng.random() and g.get(x, y, z) is None:
                    g.set(x, y, z, LEAVES)
    # hanging roots and propagules under the crown
    for x in range(mid - 6, mid + 7):
        for z in range(0, 10):
            s = g.get(x, TOP - 1, z)
            if s and "leaves" in s and g.get(x, TOP - 2, z) is None and g.chance(0.25):
                g.set(x, TOP - 2, z, g.rng.choice(("mangrove_propagule[hanging=true,age=4]", "hanging_roots")))
    # little pools between the roots
    water = set()
    for cx, cz in ((mid - 7, 6), (mid + 7, 5)):
        water |= pool(g, cx - 2, cz - 2, cx + 2, cz + 2, p["rim"], depth=1,
                      region=lambda x, z, cx=cx, cz=cz: math.hypot(x - cx, z - cz) <= 1.9 and g.get(x, 1, z) is None)
    lilies(g, water, 0.4)
    # a hanging froglight from the arching roots over the frame
    hanging_froglight(g, mid - 3, 6, 8, chain=2)
    hanging_froglight(g, mid + 3, 6, 8, chain=2)
    # a broken path of mud bricks up to it, mud puddles either side
    for z in range(8, g.D - 1):
        for x in range(mid - 1, mid + 2):
            if g.chance(0.7 - 0.02 * (z - 8)):
                g.set(x, 0, z, p["floor"])
    for x in range(g.W):
        for z in range(10, g.D):
            if g.get(x, 0, z) is None and g.noise3_at(2.5, x, 0, z) > 0.72:
                g.set(x, 0, z, "mud")
    rubble(g, cells, p["rubble"], spread=3)
    hang_vines(g, 0, 0, g.W - 1, 12, chance=0.18, y_lo=3, y_hi=14)
    poison_dress(g, 16, moss=0.4)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


# ---------------------------------------------------------------- 4 Bog Ring (round 1's Fallen Ring, sunk in the bog)

@layout("bog_ring", "Bog Ring", size=(25, 16, 25),
        blurb="A ring of mud brick pillars leaning in a black moat; the frame lies flat on the island, full of bog water.")
def bog_ring(g, p):
    """A round island in a ring of black water; round it pillars lean out of the moat, two toppled across it as
    bridges; the frame has fallen on its back at the island's back and filled with water."""
    mid = g.W // 2
    cz = 12
    bank(g, 12.4, cz=cz)
    R_IN, R_OUT = 8.0, 10.4

    def moat(x, z):
        d = math.hypot(x - mid, z - cz) + (g.noise3_at(3.0, x, 0, z) - 0.5) * 0.9
        return R_IN <= d <= R_OUT
    water = pool(g, 0, 0, g.W - 1, g.D - 1, p["rim"], depth=2, region=moat)
    # the island's paving, mud bricks over mud, ragged
    court(g, p["floor"], 7.0, cx=mid, cz=cz, ragged=3.0, under="mud")
    # the frame on its back at the island's back (z 4-10), sunk flush, bog water in its opening
    flat_frame(g, mid - 2, 0, 3, keep=lambda i, j: (i, j) not in ((4, 0), (3, 0), (0, 6)), along="z")
    pool(g, mid - 1, 4, mid + 1, 8, p["rim"], depth=1, region=lambda x, z: True)
    g.set(mid + 2, 1, 4, FRAME)
    g.set(mid + 3, 1, 5, FRAME)
    # pillars standing in the moat all round, leaning out, broken off at different heights
    pillars = 9
    for k in range(pillars):
        a = math.pi * (0.62 + 1.76 * k / (pillars - 1))
        px = mid + math.cos(a) * 9.2
        pz = cz - math.sin(a) * 9.2
        x0, z0 = int(round(px)), int(round(pz))
        if z0 > g.D - 6:
            continue
        h = g.rng.randint(3, 8)
        lx = int(round(math.cos(a) * (h / 5.0)))
        lz = int(round(-math.sin(a) * (h / 5.0)))
        for y in (-1, 0):
            g.set(x0, y, z0, p["wall"], force=True)
        g.line((x0, 1, z0), (x0 + lx, h, z0 + lz), p["wall"])
        g.set(x0 + lx, h + 1, z0 + lz, slab("mud_brick_slab") if g.chance(0.5) else p["light"])
    # two toppled pillars lying across the moat at the front: the ways onto the island
    for x0 in (mid - 4, mid + 4):
        z0 = cz + 6 if x0 < mid else cz + 7
        g.line((x0, 0, z0), (x0 + (1 if x0 < mid else -1), 0, z0 + 6), p["wall"])
        g.line((x0 + (1 if x0 > mid else -1), 0, z0), (x0, 0, z0 + 6), p["floor"])
    lilies(g, water, 0.2)
    mangrove(g, 2, 3, 7, lean=(1, 1), spread=3, y0=1)
    froglamp(g, mid - 5, 7, post="mud_brick_wall", h=1)
    froglamp(g, mid + 5, 7, post="mud_brick_wall", h=1)
    hang_vines(g, 0, 0, g.W - 1, g.D - 1, chance=0.12)
    poison_dress(g, 10)
    return [guard(mid - 4, 14), guard(mid + 4, 14)]


# ---------------------------------------------------------------- 5 Peat Barrow

@layout("peat_barrow", "Peat Barrow", size=(25, 16, 25),
        blurb="A long grave mound of peat and moss, a mud brick doorway in its face with the frame in it.")
def peat_barrow(g, p):
    """A long mound across the back, grown over with moss, ferns and a dead mangrove; a mud brick facade cut into its
    face with the frame standing in its doorway; standing stones leading to it."""
    mid = g.W // 2
    bank(g, 12.0, cz=13)
    heights = mound(g, mid, 5, 12.5, 11.0, Mix(("dirt", 3), ("rooted_dirt", 2), ("mud", 1), patch=0.5),
                    top="moss_block", y0=0, rz=5.6)
    for (x, z), h in heights.items():
        if h >= 2 and g.chance(0.55):
            g.set(x, h, z, "grass_block")
    FZ = 10
    # the passage into the mound behind the doorway, lined with packed mud
    for x in range(mid - 3, mid + 4):
        for z in range(3, FZ):
            for y in range(0, 9):
                inside = abs(x - mid) <= 2 and 4 <= z and 1 <= y <= 7
                g.set(x, y, z, AIR if inside else ("mud_bricks" if y == 0 else "packed_mud"), force=True)
    # the facade across its face: a mud brick wall with buttresses, broken at the top
    for x in range(mid - 7, mid + 8):
        top = 9 - abs(x - mid) // 2
        for y in range(0, top + 1):
            if abs(x - mid) <= 2 and 1 <= y <= 7:
                continue
            g.set(x, y, FZ, p["wall"])
            if g.get(x, y, FZ - 1) is None:
                g.set(x, y, FZ - 1, "packed_mud")
    for x in (mid - 7, mid - 4, mid + 4, mid + 7):
        hb = 6 - (2 if abs(x - mid) == 7 else 0)
        for y in range(1, hb):
            g.set(x, y, FZ + 1, "mud_bricks")
        g.set(x, hb, FZ + 1, stairs("mud_brick_stairs", "north"))
    g.ruin_tops(mid - 7, FZ - 1, mid + 7, FZ + 1, 5, 11, 5.0, 10.0, scale=3.0, salt=5)
    cells = broken_frame(g, mid - 2, 1, FZ, [(4, 6), (3, 6), (2, 6)])
    hanging_froglight(g, mid, 6, 6, chain=1)
    # a dead mangrove on the mound's top, ferns and grass over it
    hx = heights.get((mid + 6, 5), 8)
    g.line((mid + 6, hx, 5), (mid + 7, hx + 5, 5), LOG)
    g.line((mid + 7, hx + 3, 5), (mid + 9, hx + 4, 4), "mangrove_log[axis=x]")
    g.line((mid + 7, hx + 4, 5), (mid + 5, hx + 6, 6), "mangrove_log[axis=x]")
    for (x, z), h in heights.items():
        s = g.get(x, h, z)
        if s and ("grass" in s or "moss" in s) and g.get(x, h + 1, z) is None and g.chance(0.25):
            g.set(x, h + 1, z, g.rng.choice(("fern", "grass", "moss_carpet", "grass")))
    # standing stones leading up to it
    for z, h in ((13, 3), (17, 2), (21, 3)):
        for x in (mid - 6, mid + 6):
            hh = h - (1 if (x < mid) == (z == 17) else 0)
            for y in range(1, hh + 1):
                g.set(x, y, z, p["wall"])
            g.set(x, hh + 1, z, "moss_carpet")
    rubble(g, cells, p["rubble"], spread=3)
    hang_vines(g, mid - 7, FZ, mid + 7, FZ + 2, chance=0.4, y_lo=2, y_hi=8)
    froglamp(g, mid - 8, 12, post="mangrove_fence", h=2)
    poison_dress(g, 12)
    return [guard(mid - 3, 18), guard(mid + 3, 18)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [stilt_shrine, causeway, root_hollow]
DROPPED = [bog_ring, peat_barrow]
