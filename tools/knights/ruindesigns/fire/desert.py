"""Fire's desert ruins: sandstone and orange terracotta half swallowed by the dunes, dead bushes and cacti, braziers."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, slab, stairs)

P = dict(
    floor=Mix(("smooth_sandstone", 3), ("cut_sandstone", 3), ("sandstone", 2), ("sand", 1), patch=0.5),
    wall=Mix(("cut_sandstone", 4), ("sandstone", 3), ("chiseled_sandstone", 0.5), ("smooth_sandstone", 2), patch=0.4),
    accent=Mix(("orange_terracotta", 3), ("terracotta", 1), patch=0.3),
    trim="chiseled_sandstone",
    rubble=Mix(("sandstone", 2), ("cut_sandstone", 1), ("smooth_sandstone", 1)),
    light="campfire[lit=true]",
)

AREA = Area(element="fire", id="desert", name="Desert", biomes="desert", setting=SURFACE, ground="sand",
            under="sandstone", palette=P, blurb="Sandstone and terracotta half swallowed by the dunes.")


def dunes(g, n=4, avoid=None):
    """Drifts of sand banked up on the ground (and against what stands there)."""
    for _ in range(n):
        cx, cz = g.rng.randint(2, g.W - 3), g.rng.randint(2, g.D - 3)
        if avoid and avoid(cx, cz):
            continue
        mound(g, cx, cz, g.rng.uniform(2.5, 4.5), g.rng.uniform(1.0, 2.2), "sand", y0=0)


def dress(g, count=10):
    def place(x, y, z):
        s = g.get(x, y, z)
        r = g.rng.random()
        if r < 0.5 and (s is None or "sand" in s):
            g.set(x, y, z, "sand")
            g.set(x, y + 1, z, "dead_bush")
            return True
        if r < 0.7 and (s is None or s.endswith(":sand")):
            if all(g.empty(x + dx, y + 1, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                g.set(x, y, z, "sand")
                for k in range(1, g.rng.randint(2, 3) + 1):
                    g.set(x, y + k, z, "cactus")
                return True
            return False
        g.set(x, y + 1, z, slab("sandstone_slab"))
        return True
    scatter_on_floor(g, count, place)


def brazier(g, x, z, y=1):
    g.set(x, y, z, "chiseled_sandstone")
    g.set(x, y + 1, z, "campfire[lit=true]")


@layout("buried_stair", "Buried Stair", size=(23, 18, 25),
        blurb="A temple's great stair, half under the sand, the frame on its top step.")
def buried_stair(g, p):
    """A wide sandstone stair climbs to a broken platform; the dunes pile up its sides."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=16)
    # the platform at the back, 4 up, and the stair down from it, 7 wide
    for x in range(mid - 6, mid + 7):
        for z in range(2, 9):
            for y in range(1, 5):
                g.set(x, y, z, p["wall"] if y < 4 else p["floor"])
    for step in range(4):
        z = 9 + step
        for x in range(mid - 3, mid + 4):
            for y in range(1, 4 - step + 1):
                g.set(x, y, z, p["wall"])
            g.set(x, 4 - step, z, stairs("sandstone_stairs", "south"))
    for x in (mid - 4, mid + 4):
        for z in range(9, 13):
            for y in range(1, 13 - z + 1 + 0):
                g.set(x, y, z, p["accent"])
    cells = broken_frame(g, mid - 2, 5, 4, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    # broken pillars at the platform's corners
    pillar(g, mid - 6, 2, 4, p["wall"], y0=5)
    pillar(g, mid + 6, 2, 2, p["wall"], y0=5)
    brazier(g, mid - 5, 7, y=5)
    brazier(g, mid + 5, 7, y=5)
    dunes(g, 5, avoid=lambda x, z: abs(x - mid) < 5 and 8 < z < 20)
    mound(g, mid - 9, 6, 4.0, 4.0, "sand", y0=0)
    mound(g, mid + 9, 5, 4.0, 3.0, "sand", y0=0)
    dress(g, 8)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


@layout("sunken_oasis", "Sunken Oasis", size=(25, 16, 25),
        blurb="A court sunk into the sand round a still pool, palms leaning over the frame.")
def sunken_oasis(g, p):
    """A round court dug two deep into the sand, a pool in its back half walled in, two palms and the frame behind."""
    mid = g.W // 2
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot(x - mid, z - mid)
            if d <= 10.5:
                g.set(x, -2, z, p["floor"])
                g.set(x, -3, z, "sandstone")
                g.set(x, -1, z, AIR)
                g.set(x, 0, z, AIR)
            elif d <= 11.6:
                for y in range(-2, 1):
                    g.set(x, y, z, p["wall"])
                g.set(x, 1, z, slab("cut_sandstone_slab"))
    # the pool: water one deep in a basin, its rim of terracotta
    for x in range(mid - 4, mid + 5):
        for z in range(4, 10):
            rim = x in (mid - 4, mid + 4) or z in (4, 9)
            g.set(x, -2, z, p["accent"] if rim else "water")
            g.set(x, -3, z, "sandstone")
    # steps down at the front
    for x in range(mid - 1, mid + 2):
        g.set(x, -1, 21, stairs("sandstone_stairs", "north"))
        g.set(x, 0, 22, stairs("sandstone_stairs", "north"))
        g.set(x, -1, 22, "sandstone")
        g.set(x, 1, 22, AIR, force=True)
    # the frame on the back rim, its lower rows in the court's wall
    cells = broken_frame(g, mid - 2, -1, 2, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    # two palms leaning over the pool
    for px, lean in ((mid - 7, 1), (mid + 7, -1)):
        tx, ty = px + lean * 2, 4
        g.line((px, -1, 6), (tx, ty, 6), "jungle_log[axis=y]")
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):
            g.set(tx + dx, ty + 1, 6 + dz, "jungle_leaves[persistent=true]")
    for x, z in ((mid - 8, 14), (mid + 8, 15)):
        brazier(g, x, z, y=-1)
    dunes(g, 3, avoid=lambda x, z: math.hypot(x - mid, z - mid) < 13)
    return [guard(mid - 3, 15, y=-1), guard(mid + 3, 15, y=-1)]


@layout("obelisk_field", "Obelisk Field", size=(25, 18, 25),
        blurb="Standing and toppled obelisks round the frame, sunk to its knees in the sand.")
def obelisk_field(g, p):
    """Five obelisks, two standing, three toppled in the sand; the frame half sunk among them."""
    mid = g.W // 2
    court(g, Mix(("sand", 3), ("smooth_sandstone", 2), ("cut_sandstone", 1)), 10.0)
    # the frame sunk two rows deep
    cells = broken_frame(g, mid - 2, -2, 7, [(4, 6), (3, 6), (2, 6)])
    for x in range(mid - 3, mid + 4):
        for z in (6, 8):
            g.set(x, 0, z, "sand")
            g.set(x, -1, z, "sandstone")
    rubble(g, cells, p["rubble"])
    # standing obelisks: a terracotta foot, sandstone shaft, a chiseled tip
    for x, z, h in ((mid - 8, 6, 8), (mid + 8, 9, 6)):
        for dx in (0, 1):
            for dz in (0, 1):
                g.set(x + dx, 1, z + dz, p["accent"])
        pillar(g, x, z, h, "cut_sandstone", y0=2, cap="chiseled_sandstone")
    # toppled ones lying along the ground
    for x, z, dx, dz, n in ((mid - 9, 16, 1, 0, 6), (mid + 3, 18, 1, 0, 7), (mid + 9, 2, 0, 1, 5)):
        for k in range(n):
            g.set(x + dx * k, 1, z + dz * k, "cut_sandstone" if k < n - 1 else "chiseled_sandstone")
        g.set(x - dx, 1, z - dz, p["accent"])
    dunes(g, 4, avoid=lambda x, z: abs(x - mid) < 4 and z < 12)
    dress(g, 10)
    brazier(g, mid - 4, 11)
    brazier(g, mid + 4, 11)
    return [guard(mid - 3, 15), guard(mid + 3, 14)]


@layout("caravan_rest", "Caravan Rest", size=(25, 16, 23),
        blurb="Broken arches of an old caravan stop, torn awnings, a cold firepit, the frame between two arches.")
def caravan_rest(g, p):
    """A ring of three broken arches, awnings of wool on fence posts, the frame standing between the back arches."""
    mid = g.W // 2
    court(g, p["floor"], 9.5, cz=12)
    # the arches: two piers and a lintel, broken
    for ax, az in ((mid - 8, 6), (mid + 8, 6), (mid - 9, 15)):
        for y in range(1, 6):
            g.set(ax, y, az, p["wall"])
            g.set(ax, y, az + 3, p["wall"] if y < 4 or ax > mid else AIR)
        for z in range(az, az + 4):
            if g.get(ax, 5, z) or z == az + 1:
                g.set(ax, 6, z, "cut_sandstone")
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (4, 5), (3, 6), (4, 4)])
    rubble(g, cells, p["rubble"])
    # awnings: posts and a roof of orange and white wool, one side fallen
    for x0, z0, col in ((mid + 4, 14, "orange_wool"), (mid - 6, 18, "white_wool")):
        for x, z in ((x0, z0), (x0 + 3, z0), (x0, z0 + 2), (x0 + 3, z0 + 2)):
            if x <= g.W - 2 and z <= g.D - 2:
                for y in range(1, 4):
                    g.set(x, y, z, "spruce_fence")
        for x in range(x0, min(x0 + 4, g.W - 1)):
            for z in range(z0, min(z0 + 3, g.D - 1)):
                if (x + z) % 5 != 0:
                    g.set(x, 4, z, col)
    # the firepit
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        g.set(mid + dx, 1, 12 + dz, slab("sandstone_slab"))
    g.set(mid, 1, 12, "campfire[lit=false]")
    for x, z in ((mid - 3, 4), (mid + 3, 4)):
        g.set(x, 1, z, "decorated_pot")
    dunes(g, 3, avoid=lambda x, z: abs(x - mid) < 6)
    dress(g, 6)
    return [guard(mid - 3, 9), guard(mid + 3, 9)]


@layout("swallowed_gate", "Swallowed Gate", size=(25, 18, 23),
        blurb="A gatehouse wall buried to its shoulders under a dune; the frame stands in its gateway.")
def swallowed_gate(g, p):
    """A thick wall across the back, buried under a great dune behind and either side; the frame in its gate."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=14)
    for x in range(1, g.W - 1):
        for z in range(5, 8):
            top = 7 + (1 if x % 4 == 0 else 0) - int(abs(x - mid) / 4)
            for y in range(1, max(top, 3) + 1):
                g.set(x, y, z, p["wall"])
            if z == 7:
                g.set(x, 2, z, p["accent"])
    # the gateway through it, the frame standing in it
    for x in range(mid - 3, mid + 4):
        for z in range(5, 8):
            for y in range(1, 9):
                g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 2, 1, 6, [(0, 6), (1, 6), (0, 5), (2, 6)])
    rubble(g, cells, p["rubble"])
    # the dune behind and over the wall's ends
    mound(g, mid, 2, 13.0, 6.0, "sand", y0=0, rz=4.0)
    mound(g, 3, 9, 4.0, 4.0, "sand", y0=0)
    mound(g, g.W - 4, 10, 4.0, 3.0, "sand", y0=0)
    for x in (mid - 4, mid + 4):
        g.set(x, 1, 9, "chiseled_sandstone")
        g.set(x, 2, 9, "campfire[lit=true]")
    dress(g, 8)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [buried_stair, sunken_oasis, obelisk_field, caravan_rest]
DROPPED = [swallowed_gate]
