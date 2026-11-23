"""Fire's Nether wastes ruins: netherrack, nether bricks and red nether bricks, magma, glowstone, quartz; fire that
burns for ever on the netherrack; a fortress's broken corner. Shown in the Nether's own blocks (the user, November
2026: the Cinder Knight and Flamehand in the Nether, about as rare as a fortress)."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, guard, layout, mound, pillar, rubble,
                     scatter_on_floor, slab, stairs)

from ruindesigns.fire.netherkit import (NETHER_BRICK, RACK, RED_BRICK, brazier, fire_on, lava_pool, oval_cells)

P = dict(
    floor=Mix(("netherrack", 5), ("nether_bricks", 2), ("cracked_nether_bricks", 2), patch=0.55),
    wall=NETHER_BRICK,
    red=RED_BRICK,
    rack=RACK,
    trim="chiseled_nether_bricks",
    rubble=Mix(("nether_bricks", 2), ("cracked_nether_bricks", 2), ("netherrack", 2)),
    light="fire",
)

AREA = Area(element="fire", id="nether_wastes", name="Nether Wastes", biomes="nether wastes", setting=SURFACE,
            ground="netherrack", under="netherrack", palette=P,
            blurb="Nether bricks and quartz in the netherrack wastes, fires burning for ever on the rock.")


def dress(g, count=10):
    """Fires on the netherrack, lumps of rock, magma in the floor, a fallen brick slab, a quartz lump."""
    def place(x, y, z):
        r = g.rng.random()
        s = g.get(x, y, z)
        rock = s is None or s.endswith(":netherrack")
        if r < 0.38:
            return rock and fire_on(g, x, y + 1, z)
        if r < 0.58:
            g.set(x, y + 1, z, P["rack"])
            return True
        if r < 0.72:
            if rock:
                g.set(x, y, z, "magma_block")
                return True
            return False
        if r < 0.88:
            g.set(x, y + 1, z, slab("nether_brick_slab"))
            return True
        g.set(x, y + 1, z, "nether_quartz_ore")
        return True
    scatter_on_floor(g, count, place)


@layout("fortress_corner", "Fortress Corner", size=(25, 20, 25),
        blurb="The broken corner of a nether fortress: walls with fence windows, a bridge pier, its fallen span.")
def fortress_corner(g, p):
    """An L of thick nether brick walls round a corner tower, fence-barred windows; on the right a bridge pier with
    its arch, the deck snapped off over it and the fallen span lying in the court; the frame on a dais in the angle."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=13, under="netherrack")
    # the walls: along the back and down the left side, two thick, stepping lower towards the front
    for x in range(1, 17):
        for z in (1, 2):
            top = 8 - (1 if x > 12 else 0)
            for y in range(1, top + 1):
                g.set(x, y, z, p["wall"])
    for z in range(3, 16):
        top = 7 - max(0, z - 8) // 2
        for x in (1, 2):
            for y in range(1, top + 1):
                g.set(x, y, z, p["wall"])
    # the corner tower, taller, its crown of fence posts
    for x in range(0, 5):
        for z in range(0, 5):
            for y in range(1, 12):
                g.set(x, y, z, p["wall"])
    for x, z in ((0, 0), (2, 0), (4, 0), (4, 2), (4, 4), (0, 4), (2, 4), (0, 2)):
        g.set(x, 12, z, "nether_brick_fence")
    g.ruin_tops(0, 0, 16, 15, 4, 12, 5.0, 11.0, scale=4.0, salt=1)
    # fence-barred windows: two high, in the back wall and the side wall
    for x in (7, 15):
        for y in (3, 4):
            for z in (1, 2):
                g.set(x, y, z, "nether_brick_fence", force=True)
    for z in (7, 11):
        for y in (3, 4):
            for x in (1, 2):
                g.set(x, y, z, "nether_brick_fence", force=True)
    # the dais the frame stands on, a stair up its front, posts with wither skulls at its corners
    for x in range(mid - 4, mid + 5):
        for z in range(3, 8):
            g.set(x, 1, z, p["wall"])
    for x in range(mid - 3, mid + 4):
        g.set(x, 1, 8, stairs("nether_brick_stairs", "north"))
    cells = broken_frame(g, mid - 2, 2, 4, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 4, mid + 4):
        g.set(x, 1, 8, p["trim"])
        g.set(x, 2, 8, "nether_brick_fence")
        g.set(x, 3, 8, "wither_skeleton_skull[rotation=0]")
    # the bridge pier on the right: two legs and an arch, the deck on top snapped off, a fence railing
    bx0, bx1 = mid + 7, mid + 11
    for z in range(4, 7):
        for x in range(bx0, bx1 + 1):
            for y in range(1, 10):
                leg = x in (bx0, bx1)
                arch = y >= 6 or (y == 5 and x in (bx0 + 1, bx1 - 1))
                if leg or arch:
                    g.set(x, y, z, p["wall"])
    for z in range(0, 10):
        for x in range(bx0, bx1 + 1):
            if z >= 8 and (x + z) % 3 == 0:
                continue
            g.set(x, 10, z, p["wall"])
        for x in (bx0, bx1):
            if z < 8:
                g.set(x, 11, z, "nether_brick_fence")
    # the fallen span: lying across the court in pieces, one end propped on rubble
    for z in range(12, 20):
        if z in (15,):
            continue
        y = 2 if z <= 13 else 1
        for x in range(bx0 - 1, bx1):
            g.set(x, y, z, p["wall"])
        if z <= 13:
            for x in range(bx0 - 1, bx1):
                g.set(x, 1, z, p["rubble"])
        g.set(bx0 - 1, y + 1, z, "nether_brick_fence")
    for x, z in ((mid - 7, 12), (mid + 3, 12)):
        brazier(g, x, z, base="nether_bricks")
    dress(g, 10)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


SCORCH = Mix(("blackstone", 3), ("netherrack", 3), ("magma_block", 1.4), ("basalt[axis=y]", 0.6), patch=0.6)
CISTERN_FLOOR = Mix(("nether_bricks", 4), ("cracked_nether_bricks", 2), ("red_nether_bricks", 1.5), patch=0.55)


@layout("lava_cistern", "Lava Cistern", size=(25, 18, 25), ground=6,
        blurb="A sunken court of nether bricks cut by channels of lava; the frame on its island in the middle.")
def lava_cistern(g, p):
    """A square court sunk three deep, walled in nether bricks with a fence rail round its rim; a ring of lava round
    an island and channels out to the walls; the frame on the island, a block bridging the ring to it; a stair down
    from the front."""
    mid = g.W // 2
    x0, x1, z0, z1 = 2, g.W - 3, 2, g.D - 4
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            rim = x in (x0, x1) or z in (z0, z1)
            for y in range(-5, 1):
                if rim:
                    g.set(x, y, z, p["wall"])
                elif y <= -4:
                    g.set(x, y, z, "netherrack")
                elif y == -3:
                    g.set(x, y, z, CISTERN_FLOOR)
                else:
                    g.set(x, y, z, AIR)
    # the island, a step up, the frame on it
    ix0, ix1, iz0, iz1 = mid - 3, mid + 3, 5, 12
    for x in range(ix0, ix1 + 1):
        for z in range(iz0, iz1 + 1):
            g.set(x, -2, z, Mix(("red_nether_bricks", 3), ("nether_bricks", 1), patch=0.5))
    cells = broken_frame(g, mid - 2, -1, 7, [(0, 6), (1, 6), (0, 5), (4, 6)])
    rubble(g, cells, p["rubble"], spread=3)
    for x in (ix0, ix1):
        brazier(g, x, iz1, y=-1, base="red_nether_bricks")
        g.set(x, -1, iz0, "nether_brick_fence")
        g.set(x, 0, iz0, "nether_brick_fence")
    # the lava: a ring round the island, channels out to the back and side walls
    ring = [(x, z) for x in range(ix0 - 1, ix1 + 2) for z in range(iz0 - 1, iz1 + 2)
            if x in (ix0 - 1, ix1 + 1) or z in (iz0 - 1, iz1 + 1)]
    arms = [(mid, z) for z in range(z0 + 1, iz0 - 1)]
    arms += [(x, 8) for x in range(x0 + 1, ix0 - 1)] + [(x, 8) for x in range(ix1 + 2, x1)]
    lava_pool(g, ring + arms, -3, p["wall"], "netherrack")
    # a block bridging the ring at the front, and stepping stones over the side channels
    for x in range(mid - 1, mid + 2):
        g.set(x, -2, iz1 + 1, "nether_bricks", force=True)
    for x in (x0 + 3, x1 - 3):
        g.set(x, -2, 8, "nether_bricks", force=True)
    # the stair down from the front rim
    for x in range(mid - 1, mid + 2):
        for k, (z, y) in enumerate(((z1, 0), (z1 - 1, -1), (z1 - 2, -2))):
            for yy in range(-3, y):
                g.set(x, yy, z, p["wall"], force=True)
            g.set(x, y, z, stairs("nether_brick_stairs", "south"), force=True)
            for yy in range(y + 1, 2):
                g.set(x, yy, z, AIR, force=True)
    # the fence rail round the rim, braziers on its corners
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and not (z == z1 and abs(x - mid) <= 1):
                if g.noise3_at(3.0, x, 1, z) > 0.3:
                    g.set(x, 1, z, "nether_brick_fence")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        brazier(g, x, z, y=1, base="nether_bricks")
    dress(g, 9)
    return [guard(mid - 5, 17, y=-2), guard(mid + 5, 17, y=-2)]


@layout("glowstone_fall", "Glowstone Fall", size=(25, 20, 25), ground=4,
        blurb="A great lump of glowstone fell from the Nether's roof: its crater, its shards, the frame it smashed.")
def glowstone_fall(g, p):
    """A crater blasted into the netherrack, its rim of thrown-up rock and splinters of netherrack sticking out of it;
    the glowstone (and the ceiling rock it fell with) lies in the crater's back half, the frame beside it, its right
    side smashed off; shards of glowstone and fires all round, magma cracks in the floor."""
    mid = g.W // 2
    cx, cz, R = mid, 10, 8.6

    def floor_y(x, z):
        r = math.hypot(x - cx, z - cz)
        if r <= 5.5:
            return -3
        return min(0, -3 + int(round((r - 5.5) / (R - 5.5) * 3)))

    for x in range(g.W):
        for z in range(g.D):
            r = math.hypot(x - cx, z - cz) + (g.noise3_at(3.0, x, 0, z) - 0.5) * 1.2
            if r > R:
                continue
            fy = floor_y(x, z)
            for y in range(-4, fy):
                g.set(x, y, z, "netherrack")
            scorch = SCORCH if r < R - 2.0 else "netherrack"
            g.set(x, fy, z, scorch)
            for y in range(fy + 1, 1):
                g.set(x, y, z, AIR)
    # the rim of thrown-up rock, open at the front where the way in comes down
    for x in range(g.W):
        for z in range(g.D):
            r = math.hypot(x - cx, z - cz)
            if not (R - 0.2 < r < R + 2.6) or (z > cz + 5 and abs(x - mid) <= 2):
                continue
            h = int(round(3.0 * (1 - abs(r - (R + 1.1)) / 1.6) + (g.noise3_at(2.0, x, 1, z) - 0.5) * 1.8))
            for y in range(1, h + 1):
                g.set(x, y, z, P["rack"])
    # splinters of netherrack thrown up out of the rim, leaning outwards
    for a in (0.3, 1.4, 2.5, 3.6, 4.9, 5.7):
        x0 = int(round(cx + math.cos(a) * (R + 0.6)))
        z0 = int(round(cz + math.sin(a) * (R + 0.6)))
        if z0 > cz + 5 and abs(x0 - mid) <= 3:
            continue
        x1 = int(round(cx + math.cos(a) * (R + 2.4)))
        z1 = int(round(cz + math.sin(a) * (R + 2.4)))
        g.line((x0, 1, z0), (x1, 4 + int(a * 7) % 3, z1), Mix(("netherrack", 3), ("blackstone", 1)))
    # the frame on a pad of rock at the crater's back left, its right side smashed off
    for x in range(mid - 6, mid + 1):
        for z in range(4, 8):
            g.set(x, -3, z, "netherrack", force=True)
            for y in (-2, -1, 0):
                g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 5, -2, 6, [(4, 6), (4, 5), (3, 6), (2, 6)])
    rubble(g, cells, Mix(("glowstone", 1), ("netherrack", 2)), frame_share=0.45, spread=3)
    # the glowstone and its ceiling rock, lying in the crater's back right
    g.ellipsoid(mid + 3, 0, 6, 3.8, 3.2, 3.2, Mix(("glowstone", 6), ("netherrack", 3), patch=0.65), only_above=-2)
    g.ellipsoid(mid + 4, 3, 5, 2.3, 1.4, 2.0, Mix(("netherrack", 3), ("glowstone", 1), patch=0.5), only_above=2)
    # glowstone shards over the crater floor
    for x, z in ((mid - 1, 9), (mid + 2, 12), (mid - 6, 11), (mid + 6, 11), (mid + 1, 3), (mid - 3, 13)):
        y = g.top_y(x, z)
        if y <= 0 and g.empty(x, y + 1, z):
            g.set(x, y + 1, z, "glowstone")
    # fires on the crater floor and round it
    for x, z in ((mid - 4, 10), (mid + 5, 9), (mid, 12), (mid - 7, 7), (mid + 1, 8)):
        y = g.top_y(x, z)
        if y <= 0:
            fire_on(g, x, y + 1, z, base="magma_block" if (x + z) % 2 else "netherrack")
    dress(g, 8)
    return [guard(mid - 3, 14, y=-2), guard(mid + 3, 14, y=-2)]


@layout("quartz_throne", "Quartz Throne", size=(25, 18, 25),
        blurb="A throne of nether quartz on a stepped dais of red nether bricks, the frame standing behind it.")
def quartz_throne(g, p):
    """Three steps of red nether brick edged in quartz rise to the back; on top an empty throne before the frame, two
    quartz columns with fires on them (one broken, its drum fallen down the steps); the netherrack has crept up over
    the dais's back corners, veined with quartz."""
    mid = g.W // 2
    court(g, Mix(("netherrack", 4), ("red_nether_bricks", 1)), 10.5, cz=14, under="netherrack")
    steps = ((1, 5, 19, 3, 13), (2, 7, 17, 3, 11), (3, 9, 15, 3, 9))
    for y, xa, xb, za, zb in steps:
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                edge = x in (xa, xb) or z == zb
                for yy in range(1, y + 1):
                    g.set(x, yy, z, p["red"])
                if edge:
                    g.set(x, y, z, Mix(("quartz_bricks", 3), ("chiseled_quartz_block", 0.4), ("smooth_quartz", 1)))
                elif y == 3:
                    g.set(x, y, z, "quartz_bricks" if (x + z) % 2 else "red_nether_bricks")
    # quartz stairs up the middle of each step's front
    for y, xa, xb, za, zb in steps:
        for x in range(mid - 1, mid + 2):
            g.set(x, y, zb + 1, stairs("quartz_stairs", "north"))
    # the frame at the back of the top step, the throne before it
    cells = broken_frame(g, mid - 2, 4, 4, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, Mix(("quartz_bricks", 1), ("red_nether_bricks", 2)))
    g.set(mid, 4, 8, stairs("quartz_stairs", "north"))
    for x in (mid - 1, mid + 1):
        g.set(x, 4, 8, "quartz_pillar[axis=y]")
        g.set(x, 5, 8, slab("smooth_quartz_slab"))
    for y in (4, 5, 6):
        g.set(mid, y, 7, "quartz_pillar[axis=y]")
    g.set(mid, 7, 7, "chiseled_quartz_block")
    g.set(mid, 8, 7, "wither_skeleton_skull[rotation=0]")
    # the columns: the left one whole with a fire on top, the right broken, its drums down the steps
    for x in (mid - 4, mid + 4):
        g.set(x, 3, 5, "chiseled_quartz_block")
    for y in range(4, 11):
        g.set(mid - 4, y, 5, "quartz_pillar[axis=y]")
    g.set(mid - 4, 11, 5, "chiseled_quartz_block")
    g.set(mid - 4, 12, 5, "magma_block")
    g.set(mid - 4, 13, 5, "fire")
    for y in range(4, 7):
        g.set(mid + 4, y, 5, "quartz_pillar[axis=y]")
    g.line((mid + 5, 3, 10), (mid + 7, 1, 15), "quartz_pillar[axis=z]")
    g.set(mid + 8, 1, 16, "chiseled_quartz_block")
    # the netherrack creeping up over the dais's back corners
    mound(g, 2, 2, 5.5, 6.0, P["rack"], y0=1, rz=5.0)
    mound(g, g.W - 3, 3, 4.5, 4.0, P["rack"], y0=1)
    for x, z in ((3, 6), (g.W - 4, 6), (1, 4)):
        y = g.top_y(x, z)
        fire_on(g, x, y + 1, z)
    # braziers at the foot of the dais
    for x in (5, 19):
        brazier(g, x, 15, base="red_nether_bricks")
    dress(g, 9)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# The candidates (November 2026): the user picks; any not picked go to DROPPED.
LAYOUTS = [fortress_corner, lava_cistern, glowstone_fall, quartz_throne]
DROPPED = []
