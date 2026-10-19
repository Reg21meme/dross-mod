"""The camp round the igloos: fire pits, racks, sleds, inuksuks, arches, palisades, banners, paths, drifts.

Each prop takes a Frame (its own position and facing; v = its front) and puts real blocks in the grid. Props stand on
the ground (y = 1 and up); paths replace the ground layer (y = 0).
"""
import math

import mc
from voxel import AIR, Mix

from .local import Frame, ROT16

STONE_RING = Mix(("cobblestone_slab[type=bottom]", 3), ("stone_slab[type=bottom]", 2),
                 ("cobbled_deepslate_slab[type=bottom]", 1), patch=0.0)
CAIRN = Mix(("cobblestone", 3), ("stone", 2), ("andesite", 2), ("cobbled_deepslate", 1), ("tuff", 1), patch=0.0)


def frame(g, x, z, front="south"):
    return Frame(g, x, z, front)


# ---------------------------------------------------------------- fires and cooking

def fire_pit(f, r=1, benches=True, spit=False, pot=False, kind="campfire", stones=STONE_RING, bench_r=3):
    """A campfire in a ring of stone slabs, log benches round it, maybe a roasting spit or a stew pot."""
    f.set(0, 1, 0, f"{kind}[facing=north,lit=true,signal_fire=false]")
    for u in range(-r - 1, r + 2):
        for v in range(-r - 1, r + 2):
            d = math.hypot(u, v)
            if 0.5 < d <= r + 0.6 and not (u == 0 and v == 0):
                f.set(u, 1, v, stones)
            if d <= r + 0.6:
                f.set(u, 0, v, Mix(("gravel", 2), ("coarse_dirt", 1), patch=0.0) if d > 0.5 else "cobblestone")
    if spit:
        f.set(-1, 1, 0, "spruce_fence")
        f.set(1, 1, 0, "spruce_fence")
        f.set(-1, 2, 0, "spruce_fence")
        f.set(0, 2, 0, "spruce_fence")
        f.set(1, 2, 0, "spruce_fence")
    if pot:
        f.set(0, 1, 1, "cauldron")
    if benches:
        for side in range(4):
            a = side * math.pi / 2 + math.pi / 4
            cu, cv = math.sin(a) * bench_r, math.cos(a) * bench_r
            # a log bench along the tangent, 2 long
            tu, tv = math.cos(a), -math.sin(a)
            for k in (-0.5, 0.5):
                u, v = int(round(cu + tu * k)), int(round(cv + tv * k))
                ax = "{lr}" if abs(tu) > abs(tv) else "{fb}"
                f.set(u, 1, v, f"stripped_spruce_log[axis={ax}]")


def long_fire(f, length=7, kind="campfire"):
    """A fire trench: campfires in a row with stone along both sides (v along its length)."""
    for v in range(-(length // 2), length // 2 + 1):
        f.set(0, 1, v, f"{kind}[facing={{left}},lit=true,signal_fire=false]" if v % 2 == 0 else "magma_block")
        f.set(0, 0, v, "cobblestone")
        for u in (-1, 1):
            f.set(u, 1, v, STONE_RING)
            f.set(u, 0, v, "gravel")
    for u in (-1, 0, 1):
        for v in (-(length // 2) - 1, length // 2 + 1):
            f.set(u, 1, v, STONE_RING)


# ---------------------------------------------------------------- racks and frames

def drying_rack(f, length=4, height=3, hang=("dried_kelp_block", "dried_kelp_block", None)):
    """Two log posts with a fence beam on top; bundles of drying meat (dried kelp blocks) hanging on chains."""
    for u in (0, length - 1):
        for y in range(1, height + 1):
            f.set(u, y, 0, "spruce_log[axis=y]")
        f.set(u, height + 1, 0, "snow[layers=2]")
    for u in range(1, length - 1):
        f.set(u, height, 0, "spruce_fence")
        h = hang[(u - 1) % len(hang)]
        if h:
            f.set(u, height - 1, 0, "chain[axis=y]")
            f.set(u, height - 2, 0, h)


def hide_frame(f, colour="brown", w=2, h=2):
    """A stretched hide: a wool panel in a frame of fences (v = the way it faces)."""
    for u in range(-1, w + 1):
        f.set(u, 1, 0, "spruce_fence")
        f.set(u, h + 2, 0, "spruce_fence")
    for y in range(1, h + 3):
        f.set(-1, y, 0, "spruce_fence")
        f.set(w, y, 0, "spruce_fence")
    for u in range(w):
        for y in range(2, h + 2):
            f.set(u, y, 0, f"{colour}_wool")


def weapon_rack(f, n=3):
    """Spears (lightning rods) standing in a rack of fences, a target block for practice beside it."""
    for u in range(n + 1):
        f.set(u, 1, 0, "spruce_fence")
    f.set(0, 2, 0, "spruce_fence")
    f.set(n, 2, 0, "spruce_fence")
    for u in range(1, n):
        f.set(u, 2, 0, "spruce_fence")
        f.set(u, 3, 0, "lightning_rod[facing=up]")


def archery_range(f, targets=3, gap=3, distance=10):
    """A shooting line of fences and targets on hay bales `distance` blocks ahead (v)."""
    for i in range(targets):
        u = (i - (targets - 1) / 2) * gap
        u = int(round(u))
        f.set(u, 1, distance, "hay_block[axis=y]")
        f.set(u, 2, distance, "target")
        f.set(u, 3, distance, "snow[layers=1]")
    for u in range(-(targets * gap) // 2 - 1, (targets * gap) // 2 + 2):
        f.set(u, 1, 0, "spruce_fence")


def woodpile(f, length=3, height=2):
    """Spruce logs stacked on their sides against something, a slab roof with snow."""
    for u in range(length):
        for y in range(1, height + 1):
            f.set(u, y, 0, "spruce_log[axis={fb}]")
        f.set(u, height + 1, 0, "spruce_slab[type=bottom]")


def sled(f, cargo=True):
    """A sled 2 wide and 4 long, its runners curling up at the front (v), with a load."""
    for u in (0, 1):
        for v in range(0, 3):
            f.set(u, 1, v, "spruce_slab[type=bottom]")
        f.set(u, 1, 3, "spruce_stairs[facing={front},half=bottom]")
    if cargo:
        f.set(0, 2, 1, "barrel[facing=up,open=false]")
        f.set(1, 2, 0, "brown_wool")
        f.set(1, 2, 1, "white_carpet")
        f.set(0, 2, 0, "white_carpet")


# ---------------------------------------------------------------- markers

def inuksuk(f, tall=True):
    """A stone figure: two legs, a lintel of arms, a body and a head (the Arctic's way markers)."""
    f.set(-1, 1, 0, CAIRN)
    f.set(1, 1, 0, CAIRN)
    if tall:
        f.set(-1, 2, 0, CAIRN)
        f.set(1, 2, 0, CAIRN)
    y = 3 if tall else 2
    for u in (-2, -1, 0, 1, 2):
        f.set(u, y, 0, "stone_slab[type=bottom]" if abs(u) == 2 else CAIRN)
    f.set(0, y + 1, 0, CAIRN)
    f.set(0, y + 2, 0, "stone_slab[type=bottom]")


def banner_pole(f, colour="light_blue", height=4, cloth=None):
    """A spruce pole with a standing banner on top."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "spruce_fence")
    f.set(0, height + 1, 0, f"{colour}_banner[rotation={{rot_front}}]")
    f.banner_at = (f.xz(0, 0), height + 1)


def lantern_post(f, height=3, kind="lantern"):
    for y in range(1, height + 1):
        f.set(0, y, 0, "spruce_fence")
    f.set(0, height + 1, 0, f"{kind}[hanging=false]")


def ice_lantern(f, height=2):
    """A post of packed ice with a soul lantern in a cage of clear ice on top (soul light never melts the ice)."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "packed_ice")
    f.set(0, height + 1, 0, "soul_lantern[hanging=false]")
    for u, v in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        f.set(u, height + 1, v, "ice")
    f.set(0, height + 2, 0, "packed_ice")


def skull_post(f, height=2, skull="skeleton_skull"):
    for y in range(1, height + 1):
        f.set(0, y, 0, "spruce_fence")
    f.set(0, height + 1, 0, f"{skull}[rotation={{rot_front}}]")


def antler_post(f, height=3, wood="birch_fence"):
    """A log post crowned with a rack of antlers (pale birch fences branching up)."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "stripped_spruce_log[axis=y]")
    y = height + 1
    f.set(0, y, 0, "skeleton_skull[rotation={rot_front}]")
    for u in (-1, -2, -3, 1, 2, 3):
        f.set(u, y, 0, wood)
    for u in (-3, -1, 1, 3):
        f.set(u, y + 1, 0, wood)
    for u in (-3, 3):
        f.set(u, y + 2, 0, wood)
        f.set(u - (1 if u < 0 else -1) * 0, y + 2, 0, wood)


def antler_gate(f, width=3, height=4, wood="birch_fence"):
    """Two posts over a path (width blocks between them) joined by a beam of antlers."""
    for u in (-(width // 2) - 1, width // 2 + 1):
        for y in range(1, height + 1):
            f.set(u, y, 0, "stripped_spruce_log[axis=y]")
    top = height + 1
    for u in range(-(width // 2) - 2, width // 2 + 3):
        f.set(u, top, 0, wood)
    for u in range(-(width // 2) - 2, width // 2 + 3, 2):
        f.set(u, top + 1, 0, wood)
    f.set(0, top, 0, "skeleton_skull[rotation={rot_front}]")
    for u in (-(width // 2) - 2, width // 2 + 2):
        f.set(u, top + 2, 0, wood)


def bone_arch(f, width=5, height=6, mat="bone_block[axis=y]"):
    """A pair of great curved ribs of bone meeting over a path (v = along the path; the arch spans u)."""
    half = width / 2.0 + 0.5
    pts = []
    n = 24
    for i in range(n + 1):
        a = math.pi * i / n
        pts.append((-math.cos(a) * half, 1 + math.sin(a) * (height - 1)))
    curve(f, [(u, y, 0) for u, y in pts], mat)


def curve(f, pts, mat):
    """Blocks along a polyline of local (u, y, v) points, touching face to face."""
    g = f.g
    for (u1, y1, v1), (u2, y2, v2) in zip(pts, pts[1:]):
        x1, z1 = f.xz(int(round(u1)), int(round(v1)))
        x2, z2 = f.xz(int(round(u2)), int(round(v2)))
        g.line((x1, int(round(y1)), z1), (x2, int(round(y2)), z2), f.state(mat))


def horn_arch(f, width=5, height=7, mat="packed_ice", tip="ice"):
    """Two great curved horns rising from either side of a path and almost meeting overhead (a Rimehorn's horns)."""
    half = width // 2 + 1
    for side in (-1, 1):
        pts = []
        for i in range(0, 13):
            t = i / 12.0
            u = side * (half - math.sin(t * math.pi * 0.55) * (half - 0.6))
            pts.append((u, 1 + t * (height - 1), -math.sin(t * math.pi) * 1.2))
        curve(f, pts[:9], mat)
        curve(f, pts[8:], tip)
        f.set(side * half, 0, 0, "bone_block[axis=y]")


def totem(f, height=6):
    """A pole of stripped spruce with carved bands, bone and a skull, wings of trapdoors near the top."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "stripped_spruce_log[axis=y]" if y % 3 else "spruce_log[axis=y]")
    f.set(0, height + 1, 0, "skeleton_skull[rotation={rot_front}]")
    wing = height - 1
    f.set(-1, wing, 0, "spruce_trapdoor[facing={left},half=top,open=false]")
    f.set(1, wing, 0, "spruce_trapdoor[facing={right},half=top,open=false]")
    f.set(0, 3, 1, "spruce_stairs[facing={back},half=top]")


# ---------------------------------------------------------------- walls

def palisade(g, cells, height=4, gate=()):
    """A wall of spruce logs with sharpened tops (fence posts) along `cells` [(x, z)], leaving `gate` cells open."""
    for x, z in cells:
        if (x, z) in gate:
            continue
        for y in range(1, height + 1):
            g.set(x, y, z, "spruce_log[axis=y]")
        g.set(x, height + 1, z, "spruce_fence" if (x + z) % 2 == 0 else "snow[layers=2]")


def snow_wall(g, cells, height=3, cap="snow[layers=4]", mat="snow_block"):
    for x, z in cells:
        for y in range(1, height + 1):
            g.set(x, y, z, mat)
        if cap:
            g.set(x, height + 1, z, cap)


def ring_cells(cx, cz, r, a0=0.0, a1=360.0):
    """Cells on a circle (degrees: 0 = south, clockwise seen from above: 90 = west)."""
    out = []
    seen = set()
    n = int(2 * math.pi * r * 3) + 8
    for i in range(n + 1):
        a = math.radians(a0 + (a1 - a0) * i / n)
        x, z = int(round(cx - math.sin(a) * r)), int(round(cz + math.cos(a) * r))
        if (x, z) not in seen:
            seen.add((x, z))
            out.append((x, z))
    return out


def rect_cells(x1, z1, x2, z2):
    out = []
    for x in range(x1, x2 + 1):
        out.append((x, z1))
        out.append((x, z2))
    for z in range(z1 + 1, z2):
        out.append((x1, z))
        out.append((x2, z))
    return out


# ---------------------------------------------------------------- ground

def path(g, pts, width=3, mat=None):
    """A path along the polyline pts [(x, z)] (the ground layer), `width` wide."""
    mat = mat or Mix(("coarse_dirt", 2), ("gravel", 2), ("snow_block", 2), ("packed_mud", 1), patch=0.3)
    r = (width - 1) / 2.0
    for (x1, z1), (x2, z2) in zip(pts, pts[1:]):
        n = int(max(abs(x2 - x1), abs(z2 - z1)) * 2) + 1
        for i in range(n + 1):
            t = i / n
            x, z = x1 + (x2 - x1) * t, z1 + (z2 - z1) * t
            for dx in range(-int(r) - 1, int(r) + 2):
                for dz in range(-int(r) - 1, int(r) + 2):
                    if math.hypot(dx, dz) <= r + 0.35:
                        xx, zz = int(round(x + dx)), int(round(z + dz))
                        if g.inb(xx, 0, zz) and g.get(xx, 0, zz) is None:
                            g.set(xx, 0, zz, mat)


def plaza(g, cx, cz, r, mat=None):
    mat = mat or Mix(("snow_block", 5), ("gravel", 2), ("coarse_dirt", 2), ("packed_mud", 1), patch=0.5)
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            if math.hypot(x - cx, z - cz) <= r and g.inb(x, 0, z) and g.get(x, 0, z) is None:
                g.set(x, 0, z, mat)


def drifts(g, n, area=None, max_layers=4, near_solid=True, salt=0):
    """Snow drifts: snow layers piled against things (higher next to them), on plain ground."""
    W, D = g.W, g.D
    G = g.G
    noise = g.noise2(6.0, salt=salt)
    for x in range(W):
        for z in range(D):
            if area is not None and not area(x, z):
                continue
            if g.get(x, 1, z) is not None or g.get(x, 0, z) is not None and mc.base(g.get(x, 0, z)) in (
                    "packed_ice", "ice", "blue_ice", "gravel", "packed_mud", "coarse_dirt", "cobblestone"):
                continue
            # next to a wall?
            near = 0
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                s = g.get(x + dx, 1, z + dz)
                if s is not None and mc.kind(s) == "full":
                    near += 1
            p = noise[x, z]
            if near and p > 0.35:
                layers = 1 + int((p - 0.35) / 0.65 * max_layers)
                g.set(x, 1, z, f"snow[layers={max(1, min(layers, max_layers))}]")
            elif not near_solid and p > 0.8:
                g.set(x, 1, z, f"snow[layers={1 + int((p - 0.8) * 10) % 2}]")


def frostblooms(g, n, area=None):
    """A few Frostblooms (Rimefall's glowing flower) on open ground."""
    placed = 0
    tries = 0
    while placed < n and tries < n * 40:
        tries += 1
        x, z = g.rng.randrange(g.W), g.rng.randrange(g.D)
        if area is not None and not area(x, z):
            continue
        if g.get(x, 1, z) is None and g.get(x, 0, z) is None:
            g.set(x, 1, z, "dross:frostbloom")
            placed += 1
