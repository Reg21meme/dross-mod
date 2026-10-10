"""No. 4  Broken Colosseum: a vast oval amphitheatre of pale sandstone, three tiers of arches high. Its front-right
quarter has collapsed into slopes of rubble, opening the arena to view. Inside, rings of stepped seats fall to an
arena of dead sand, cracked by glowing fissures, where the frame stands on a platform ringed by skull-topped posts."""
import math

import leak
import mc
from common import frame_with_steps
from voxel import AIR, Grid, Mix, lantern, slab, stairs

INFO = dict(number=4, id="broken_colosseum", name="The Colosseum", mood="vast, round")

W, D, H, G = 75, 65, 32, 2
CX, CZ = 37, 32
A, B = 33.0, 28.0            # outer ellipse (x, z half axes)
R_IN = 0.84                  # inner ring wall (as a fraction of the outer ellipse)
R_ARENA = 0.46               # the arena wall
BAYS = 36                    # arches around the outside (about 5 blocks each: 3 open, 2 pier)

STONE = Mix(("smooth_sandstone", 5), ("cut_sandstone", 3), ("sandstone", 2), patch=0.55)
CUT = "cut_sandstone"
RUBBLE = Mix(("sandstone", 4), ("cut_sandstone", 2), ("smooth_sandstone", 2), ("sand", 1), ("gravel", 1), patch=0.3)
RUBBLE_TOP = Mix(("sandstone_slab[type=bottom]", 3), ("smooth_sandstone_slab[type=bottom]", 2),
                 ("cut_sandstone_slab[type=bottom]", 1), ("sandstone_stairs[facing=west]", 1))


def rho(x, z):
    return math.hypot((x - CX) / A, (z - CZ) / B)


def theta(x, z):
    return math.atan2((z - CZ) / B, (x - CX) / A)


def outward(x, z):
    dx, dz = (x - CX) / A, (z - CZ) / B
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


def build():
    g = Grid(W, H, D, G, seed=404)
    ring_out, ring_in, cav, arena_wall, arena = set(), set(), {}, set(), set()
    for x in range(W):
        for z in range(D):
            r = rho(x, z)
            step = 1.0 / min(A, B)
            if 1 - step * 2.1 < r <= 1.0:
                ring_out.add((x, z))
            elif R_IN - step * 1.05 < r <= R_IN:
                ring_in.add((x, z))
            elif R_ARENA < r <= R_IN - step * 1.05:
                cav[(x, z)] = r
            elif R_ARENA - step * 1.05 < r <= R_ARENA:
                arena_wall.add((x, z))
            elif r <= R_ARENA - step * 1.05:
                arena.add((x, z))
            if r <= 1.0:
                g.set(x, 0, z, Mix(("smooth_sandstone", 2), ("sandstone", 1)) if r > R_ARENA else
                      Mix(("sand", 4), ("coarse_dirt", 2), ("gravel", 1), patch=0.5))

    def bay_frac(x, z):
        return (theta(x, z) / (2 * math.pi) * BAYS) % 1.0

    # ---- the outer wall (two blocks thick): three tiers of round arches and an attic with small windows
    tiers = [(1, 8, 1, 6), (9, 16, 10, 14), (17, 24, 18, 22)]
    for (x, z) in ring_out:
        f = bay_frac(x, z)
        pier = f < 0.36
        for (y0, y1, o0, o1) in tiers:
            for y in range(y0, y1 + 1):
                opening = not pier and o0 <= y <= o1
                if opening and y == o1 and not (0.48 < f < 0.9):
                    opening = False  # round off the top of the arch
                if y == y1:
                    g.set(x, y, z, CUT)
                elif opening:
                    g.set(x, y, z, AIR)
                else:
                    g.set(x, y, z, "smooth_sandstone" if pier else STONE)
        for y in range(25, 29):
            window = y == 26 and 0.55 < f < 0.8 and int(theta(x, z) / (2 * math.pi) * BAYS) % 2 == 0
            g.set(x, y, z, AIR if window else STONE)
    # cornices: a ledge of slabs around the outside at the top of each tier
    for x in range(W):
        for z in range(D):
            r = rho(x, z)
            if 1.0 < r <= 1.0 + 1.1 / min(A, B):
                for y in (8, 16, 24):
                    g.set(x, y, z, slab("smooth_sandstone_slab", "top"))
    # ---- the inner ring wall and the corridor floors between the rings
    for (x, z) in ring_in:
        f = bay_frac(x, z)
        for y in range(1, 17):
            opening = f >= 0.3 and (2 <= y <= 6 or 10 <= y <= 13)
            g.set(x, y, z, AIR if opening else STONE)
    for x in range(W):
        for z in range(D):
            r = rho(x, z)
            if R_IN < r < 1 - 1.05 / min(A, B):
                g.set(x, 8, z, "smooth_sandstone")
                g.set(x, 16, z, "smooth_sandstone")

    # ---- the seats: stepped rings falling from the inner wall to the arena wall
    def seat_h(r):
        return 4 + (r - R_ARENA) / (R_IN - R_ARENA) * 11.5

    for (x, z), r in cav.items():
        h = int(seat_h(r))
        for y in range(1, h + 1):
            g.set(x, y, z, STONE if y < h else Mix(("smooth_sandstone", 3), ("cut_sandstone", 1)))
    for (x, z), r in cav.items():
        h = int(seat_h(r))
        # a step down toward the arena: make it a stair so the rows read as seats
        f = outward(x, z)
        dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[f]
        inner = (x - dx, z - dz)
        if inner in cav and int(seat_h(cav[inner])) < h:
            g.set(x, h, z, stairs("smooth_sandstone_stairs", f))
    for (x, z) in arena_wall:
        for y in range(1, 5):
            g.set(x, y, z, CUT if y < 4 else "chiseled_sandstone")
        g.set(x, 5, z, "sandstone_wall")
    # ---- gates: the main one at the front, and three more on the other sides, through every ring
    for (ang, half) in ((math.pi / 2, 2), (-math.pi / 2, 2), (0, 2), (math.pi, 2)):
        for t in range(0, 40):
            r = 0.38 + t * 0.017
            if r > 1.06:
                break
            px = CX + math.cos(ang) * A * r
            pz = CZ + math.sin(ang) * B * r
            for k in range(-half, half + 1):
                x = int(round(px - math.sin(ang) * k))
                z = int(round(pz + math.cos(ang) * k))
                for y in range(1, 8):
                    if g.get(x, y, z) is not None:
                        g.set(x, y, z, AIR)
                g.set(x, 0, z, Mix(("sandstone", 1), ("gravel", 1)))

    # ================================================================ ruin
    noise = g.noise2(6.0, 41)

    def sector(x, z, a0, a1):
        t = math.degrees(theta(x, z)) % 360
        return a0 <= t <= a1

    # the front-right quarter came down (screen: south-east of the middle)
    for x in range(W):
        for z in range(D):
            if not sector(x, z, 0, 95):
                continue
            mid = 1 - abs(math.degrees(theta(x, z)) % 360 - 47) / 50.0
            keep = int(1 + (1 - mid) * 10 + noise[x, z] * 6)
            if rho(x, z) < R_IN - 0.02:
                keep = int(keep * 0.6) + 2
            for y in range(keep + 1, H - G):
                s = g.get(x, y, z)
                if s is not None and not mc.is_air(s):
                    g.clear(x, y, z, count=True)
    # the rest is worn: the attic is mostly gone, the top tier broken
    g.ruin_tops(0, 0, W - 1, D - 1, 18, H - G - 1, 17, 29, scale=5, salt=42)
    g.ruin_tops(0, 0, W - 1, D - 1, 9, 17, 10, 20, scale=4, salt=43,
                region=lambda x, z: rho(x, z) < 1.0 and noise[x, z] > 0.62)
    g.smash(CX - 28, 14, CZ - 12, 4.0)
    g.smash(CX + 8, 12, CZ - 26, 3.5)
    g.smash(CX - 18, 9, CZ + 21, 3.0)
    g.enforce_spans(max_span=4)
    g.settle_rubble(0.6, 2.6, RUBBLE, RUBBLE_TOP,
                    protect=lambda x, z: rho(x, z) < R_ARENA * 0.55 or (abs(x - CX) <= 2 and z > CZ), max_h=6)

    # ================================================================ the arena, the frame
    px, pz = 5, 4
    for y, (hx, hz) in ((1, (px, pz)), (2, (px - 1, pz - 1))):
        for x in range(CX - hx, CX + hx + 1):
            for z in range(CZ - hz, CZ + hz + 1):
                g.set(x, y, z, Mix(("chiseled_sandstone", 1), ("cut_sandstone", 3), patch=0.3))
        for x in range(CX - hx, CX + hx + 1):
            g.set(x, y, CZ + hz + 1, stairs("cut_sandstone_slab".replace("_slab", "") + "_stairs" if False else "sandstone_stairs", "north"))
    frame_with_steps(g, CX - 2, 3, CZ, 3, 5, "sandstone_stairs")
    # posts with skulls around the platform, and chains lying on the sand
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        x = int(round(CX + math.cos(a) * 9))
        z = int(round(CZ + math.sin(a) * 7))
        for y in range(1, 4):
            g.set(x, y, z, "cut_sandstone")
        g.set(x, 4, z, f"skeleton_skull[rotation={(k * 2 + 4) % 16}]" if k % 3 else lantern("soul_lantern"))
    for k in range(4):
        a = k * math.pi / 2 + 0.4
        for t in range(3, 9):
            x = int(round(CX + math.cos(a) * t * 1.3))
            z = int(round(CZ + math.sin(a) * t))
            if g.empty(x, 1, z) and g.chance(0.7):
                g.set(x, 1, z, "chain[axis=x]" if abs(math.cos(a)) > 0.7 else "chain[axis=z]")

    # ================================================================ the Dross leaks out
    cells = leak.crack_network(g, [(CX - 3, CZ), (CX + 3, CZ), (CX, CZ - 3), (CX, CZ + 3)], branches=7, length=24,
                               fork=0.08, wobble=0.25)
    spine = leak.crack_network(g, [(CX, CZ + 3)], branches=2, length=28, fork=0.04, wobble=0.14,
                               seed_angle=math.pi / 2, spread=0.4)
    for k, v in spine.items():
        cells[k] = max(cells.get(k, 0), v)
    leak.apply_cracks(g, cells, max_surface=16, climb=5, glow_above=0.62)
    # dead bushes on the arena sand, dead land outside
    for (x, z) in arena:
        if g.chance(0.05) and g.empty(x, 1, z) and mc.base(g.get(x, 0, z) or "") in leak.BUSH_SOILS:
            g.set(x, 1, z, "dead_bush")
    leak.dead_land(g, CX, CZ, 30, 44, bush=0.07)
    leak.shards(g, cells, 8, min_dist=7, frame_center=(CX, CZ), tall=0.5, max_surface=16)
    for (x, z, h) in ((3, 3, 7), (71, 4, 8), (2, 61, 9), (72, 60, 7), (37, 1, 6), (68, 32, 8)):
        leak.dead_tree(g, x, z, h, wood="acacia_log" if h % 2 else "dark_oak_log", branches=4)
    for z in range(int(CZ + B) + 1, D):
        for x in range(CX - 2, CX + 3):
            if g.chance(0.7):
                g.set(x, 0, z, Mix(("sandstone", 2), ("gravel", 1), ("coarse_dirt", 1)))
    return g, INFO
