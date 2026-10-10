"""No. 10  Old Observatory: a white quartz tower with colonnaded wings, crowned by a great dome of green copper
ribs, now shattered, its glass long gone. A giant copper telescope still juts from the dome toward the sky, its
end snapped off and lying in the forecourt. In the round forecourt stands a broken armillary sphere, rings of copper
the size of houses, and at its very centre, where the astronomers' rings once circled the heavens, the frame."""
import math

import arch
import leak
import mc
from common import frame_with_steps
from voxel import AIR, Grid, Mix, lantern, slab, stairs

INFO = dict(number=10, id="old_observatory", name="Old Observatory", mood="dome, copper")

W, D, H, G = 65, 77, 56, 2
CX = 32
TZ = 20                # the tower's middle (z)
TR = 8.5               # tower radius
TOWER_TOP = 30
PZ = 52                # the forecourt / armillary sphere middle (z)

QUARTZ = Mix(("quartz_bricks", 4), ("smooth_quartz", 3), ("polished_diorite", 1), patch=0.5)
COPPER = Mix(("oxidized_cut_copper", 3), ("weathered_cut_copper", 2), ("oxidized_copper", 1), patch=0.5)
RUBBLE = Mix(("quartz_bricks", 3), ("smooth_quartz", 2), ("diorite", 2), ("gravel", 1), patch=0.3)
RUBBLE_TOP = Mix(("smooth_quartz_slab[type=bottom]", 2), ("quartz_slab[type=bottom]", 1),
                 ("polished_diorite_slab[type=bottom]", 1))


def ring(g, center, normal_axis, r, mat, arc=None, thick=0.6):
    """A circle of blocks of radius r around `center`, lying in the plane perpendicular to normal_axis
    ('x', 'y' or 'z', or a (ax, ay, az) unit vector for a tilted ring). arc = (from, to) in degrees keeps part."""
    cx, cy, cz = center
    if isinstance(normal_axis, str):
        n = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}[normal_axis]
    else:
        n = normal_axis
    # two unit vectors spanning the ring's plane
    ux = (n[1], -n[0], 0) if abs(n[2]) < 0.9 else (1, 0, 0)
    ln = math.sqrt(sum(c * c for c in ux))
    u = tuple(c / ln for c in ux)
    v = (n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0])
    steps = int(2 * math.pi * r * 3)
    prev = None
    for i in range(steps + 1):
        t = i / steps * 360.0
        if arc is not None and not (arc[0] <= t <= arc[1]):
            prev = None
            continue
        a = math.radians(t)
        p = (cx + r * (math.cos(a) * u[0] + math.sin(a) * v[0]),
             cy + r * (math.cos(a) * u[1] + math.sin(a) * v[1]),
             cz + r * (math.cos(a) * u[2] + math.sin(a) * v[2]))
        if prev is not None:
            g.line(prev, p, mat)
        prev = p


def build():
    g = Grid(W, H, D, G, seed=1010)

    # ---- the tower: round, white, three floors, tall windows
    for y in range(1, TOWER_TOP + 1):
        g.disc(CX, TZ, TR, y, QUARTZ if y not in (10, 20, TOWER_TOP) else "chiseled_quartz_block", r_in=TR - 2.0)
    for y in (10, 20):
        g.disc(CX, TZ, TR - 1.8, y, slab("smooth_quartz_slab", "top"))
    for x in range(int(CX - TR) - 1, int(CX + TR) + 2):
        for z in range(int(TZ - TR) - 1, int(TZ + TR) + 2):
            if math.hypot(x - CX, z - TZ) <= TR + 0.4:
                g.set(x, 0, z, "polished_diorite")
    for k in range(10):
        a = k / 10 * 2 * math.pi + math.pi / 2
        for (y0, h) in ((3, 6), (12, 6), (22, 6)):
            for rr in (TR - 1.4, TR - 0.4):
                wx = int(round(CX + math.cos(a) * rr))
                wz = int(round(TZ + math.sin(a) * rr))
                for y in range(y0, y0 + h):
                    g.set(wx, y, wz, "glass_pane" if rr > TR - 1 and g.chance(0.35) else AIR)
    arch.cut_arch(g, "x", CX - 2, 1, int(TZ + TR) - 2, 5, 7, depth=3, kind="round")    # the door
    # colonnaded wings on both sides
    for side in (-1, 1):
        x1 = CX + side * 10
        x2 = CX + side * 23
        lo, hi = min(x1, x2), max(x1, x2)
        g.box(lo, 0, TZ - 6, hi, 0, TZ + 6, "polished_diorite")
        g.walls(lo, TZ - 6, hi, TZ - 5, 1, 9, QUARTZ)
        for x in range(lo, hi + 1, 3):
            for z in (TZ + 2, TZ + 6):
                g.column(x, z, 1, 9, "quartz_pillar[axis=y]", base="chiseled_quartz_block", capital="chiseled_quartz_block")
        g.box(lo, 10, TZ - 6, hi, 10, TZ + 6, slab("smooth_quartz_slab", "bottom"))
        for x in range(lo, hi + 1):
            g.set(x, 10, TZ + 7, stairs("quartz_stairs", "north"))

    # ---- the forecourt and the armillary sphere around the frame
    for x in range(W):
        for z in range(D):
            d = math.hypot(x - CX, z - PZ)
            if d <= 14.5:
                g.set(x, 0, z, "polished_diorite" if d > 13.5 else Mix(("smooth_quartz", 2), ("quartz_bricks", 1),
                                                                        ("calcite", 1), patch=0.4))
    for z in range(int(TZ + TR), int(PZ - 12)):
        for x in range(CX - 2, CX + 3):
            g.set(x, 0, z, Mix(("polished_diorite", 2), ("smooth_quartz", 1)))
    g.disc(CX, PZ, 4.5, 1, Mix(("polished_deepslate", 2), ("chiseled_quartz_block", 1)))
    frame_with_steps(g, CX - 2, 2, PZ, 3, 5, "polished_deepslate_stairs")
    center = (CX, 6, PZ)
    for off in (0.0, 0.7):
        ring(g, (CX, 6, PZ + off), "z", 9.5, COPPER)                 # the meridian ring, around the frame
        ring(g, (CX, 6 + off, PZ), "y", 9.5, COPPER)                 # the horizon ring
    tilt = (0, math.cos(math.radians(40)), math.sin(math.radians(40)))
    ring(g, center, tilt, 9.0, COPPER, arc=(150, 330))              # the ecliptic ring: half of it is left
    ring(g, (CX, 6.6, PZ), tilt, 9.0, COPPER, arc=(150, 330))
    ring(g, (CX - 10, 1, PZ + 10), "y", 8.0, COPPER, arc=(200, 330))  # its other half lies on the floor
    for (x, z) in ((CX - 10, PZ), (CX + 10, PZ), (CX, PZ - 10), (CX, PZ + 10)):
        for y in range(1, 7):
            g.set(x, y, z, "oxidized_copper" if y < 6 else "chiseled_quartz_block")
    g.set(CX, 17, PZ, "lightning_rod[facing=up]")
    for (x, z) in ((CX - 3, PZ - 3), (CX + 3, PZ - 3)):
        g.set(x, 2, z, lantern("soul_lantern"))

    # ================================================================ ruin (the tower walls and the wings)
    g.ruin_tops(CX - 12, TZ - 12, CX + 12, TZ + 12, 20, H - G - 1, 24, TOWER_TOP + 1, scale=3, salt=101)
    g.ruin_tops(0, TZ - 7, CX - 10, TZ + 8, 1, 12, 3, 11, scale=3, salt=102)
    g.ruin_tops(CX + 10, TZ - 7, W - 1, TZ + 8, 1, 12, 5, 11, scale=3, salt=103)
    g.smash(CX + 7, 15, TZ + 5, 3.2)
    g.smash(CX - 18, 6, TZ + 2, 3.0)
    g.enforce_spans(max_span=6)

    # ---- the dome, built on what's left: green copper ribs and rings, most of its panels gone,
    #      an observation slot facing the front
    dy = TOWER_TOP + 1
    dc = (CX, dy, TZ)
    R = TR + 0.6
    for k in range(12):
        ang = k * 30
        if ang == 90:
            continue  # the slot
        a = math.radians(ang)
        n = (math.sin(a), 0, -math.cos(a))
        arc = (0, 180)
        if ang in (150, 300):
            arc = (0, 70)       # broken ribs
        if ang == 30:
            arc = (110, 180)
        ring(g, dc, n, R, COPPER, arc=arc)
    ring(g, dc, "y", R, "oxidized_cut_copper")
    ring(g, (CX, dy + 4, TZ), "y", R * 0.9, COPPER, arc=(150, 400))
    ring(g, (CX, dy + 7, TZ), "y", R * 0.62, COPPER, arc=(0, 250))
    for x in range(int(CX - R) - 1, int(CX + R) + 2):
        for y in range(dy, int(dy + R) + 2):
            for z in range(int(TZ - R) - 1, TZ + 1):
                d = math.sqrt((x - CX) ** 2 + (y - dy) ** 2 + (z - TZ) ** 2)
                if R - 0.6 <= d <= R + 0.4 and g.get(x, y, z) is None and g.chance(0.22):
                    g.set(x, y, z, "weathered_copper" if g.chance(0.6) else "light_blue_stained_glass")
    # ---- the telescope: a great copper tube out of the slot toward the sky, its end snapped off
    elev = math.radians(30)
    start = (CX, dy + 1, TZ - 1)
    length = 19
    end = (CX, dy + 1 + math.sin(elev) * length, TZ - 1 + math.cos(elev) * length)
    g.line(start, end, Mix(("weathered_copper", 2), ("oxidized_copper", 2), ("weathered_cut_copper", 1), patch=0.4), r=1.9)
    for k in (6, 15):
        p = (CX, dy + 1 + math.sin(elev) * k, TZ - 1 + math.cos(elev) * k)
        g.line(p, (p[0], p[1] + 0.01, p[2] + 0.3), "oxidized_cut_copper", r=2.2)
    g.line(end, (end[0], end[1] + 0.01, end[2] + 0.3), "exposed_cut_copper", r=2.2)   # the brass rim
    for y in range(dy - 2, dy + 2):
        g.set(CX, y, TZ + 2, "oxidized_copper")         # its cradle
    g.set(CX, dy - 2, TZ + 2, "oxidized_copper")
    # the snapped-off end lies in the forecourt
    g.line((CX + 7, 2, PZ - 17), (CX + 15, 2, PZ - 10), Mix(("weathered_copper", 2), ("oxidized_copper", 2)), r=1.6)
    g.line((CX + 15, 2, PZ - 10), (CX + 15.5, 2, PZ - 9.5), "exposed_cut_copper", r=1.9)
    for x in range(W):
        for y in range(dy - 2, H - G):
            for z in range(D):
                if g.get(x, y, z) is not None:
                    g.lock(x, y, z)

    g.settle_rubble(0.5, 2.4, RUBBLE, RUBBLE_TOP,
                    protect=lambda x, z: math.hypot(x - CX, z - PZ) < 11 or (abs(x - CX) <= 2 and z > TZ), max_h=4)

    # ================================================================ the Dross leaks out
    for x in range(W):
        for z in range(D):
            y = g.top_y(x, z)
            s = g.get(x, y, z)
            if s is not None and mc.base(s) in ("quartz_bricks",) and math.hypot(x - CX, z - PZ) < 30 and g.chance(0.2):
                g.set(x, y, z, "diorite")
    cells = leak.crack_network(g, [(CX - 3, PZ), (CX + 3, PZ), (CX, PZ + 3), (CX, PZ - 3)], branches=8, length=26,
                               fork=0.1, wobble=0.25)
    leak.apply_cracks(g, cells, max_surface=3, climb=4)
    leak.dead_land(g, CX, PZ - 6, 26, 40, bush=0.08)
    leak.shards(g, cells, 8, min_dist=6, frame_center=(CX, PZ), tall=0.5)
    for (x, z, h) in ((4, 4, 7), (60, 6, 8), (4, 70, 6), (60, 70, 7), (6, 40, 6), (58, 42, 6)):
        leak.dead_tree(g, x, z, h, wood="birch_log" if h % 2 else "dark_oak_log", branches=4)
    leak.cobwebs(g, CX - 7, 2, TZ - 7, CX + 7, 22, TZ + 7, 10)
    for z in range(PZ + 14, D):
        for x in range(CX - 2, CX + 3):
            if g.chance(0.7):
                g.set(x, 0, z, Mix(("polished_diorite", 1), ("gravel", 2), ("coarse_dirt", 1)))
    return g, INFO
