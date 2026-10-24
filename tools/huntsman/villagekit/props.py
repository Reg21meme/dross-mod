"""The camp round the igloos: fire pits, racks, sleds, inuksuks, arches, palisades, banners, paths, drifts.

Each prop takes a Frame (its own position and facing; v = its front) and puts real blocks in the grid. Props stand on
the ground (y = 1 and up); paths replace the ground layer (y = 0).
"""
import math

import mc
from voxel import AIR, Mix

from .local import Frame, ROT16, SKULL_ROT16

STONE_RING = Mix(("cobblestone_slab[type=bottom]", 3), ("stone_slab[type=bottom]", 2),
                 ("cobbled_deepslate_slab[type=bottom]", 1), patch=0.0)
CAIRN = Mix(("cobblestone", 3), ("stone", 2), ("andesite", 2), ("cobbled_deepslate", 1), ("tuff", 1), patch=0.0)
# the ground of the camp: snow, gravel, Hoarfrost, packed ice and cobbles (the user, October 19: "I see dirt stuff that
# shouldn't be there, it looks lame": no dirt, coarse dirt or mud anywhere)
FIRE_GROUND = Mix(("gravel", 2), ("cobblestone", 1), ("stone", 1), patch=0.0)
# (no packed ice in the paths and plazas since October 23, 2026: the user saw its patches as "weird ice squares")
PATH = Mix(("snow_block", 3), ("gravel", 2), ("dross:hoarfrost", 2), patch=0.3)
PLAZA = Mix(("snow_block", 5), ("gravel", 2), ("dross:hoarfrost", 2), patch=0.5)
FROSTBITTEN = "dross:frostbitten_log[axis=y]"
ROAST = "dross:roasted_meat_block[axis={lr}]"    # a roasted joint on its spit, the bone across (MeatBlocks)
# open water that never freezes over: a light block of level 0, waterlogged (Minecraft only freezes plain water)
OPEN_WATER = "light[level=0,waterlogged=true]"


def frame(g, x, z, front="south"):
    return Frame(g, x, z, front)


# ---------------------------------------------------------------- fires and cooking

def fire_pit(f, r=1, benches=True, spit=False, pot=False, kind="campfire", stones=STONE_RING, bench_r=3, ground=True,
             bench="stripped_spruce_log", post="spruce_fence"):
    """A campfire in a ring of stone slabs, log benches round it; spit: a joint of meat (a Roasted Meat Block, its
    bone across) roasting on a spit between two posts, a block clear of the flames; pot: a stew pot beside it.
    ground: gravel and cobbles under it (never dirt)."""
    f.set(0, 1, 0, f"{kind}[facing=north,lit=true,signal_fire=false]")
    for u in range(-r - 1, r + 2):
        for v in range(-r - 1, r + 2):
            d = math.hypot(u, v)
            if 0.5 < d <= r + 0.6 and not (u == 0 and v == 0) and not (spit and v == 0 and abs(u) == 1):
                f.set(u, 1, v, stones)
            if d <= r + 0.6 and ground:
                f.set(u, 0, v, FIRE_GROUND if d > 0.5 else "cobblestone")
    if spit:
        roasting_spit(f, post=post)
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
                f.set(u, 1, v, f"{bench}[axis={ax}]")


def roasting_spit(f, post="spruce_fence", height=3):
    """The spit over a fire at (0, 1, 0): two posts either side of it and the joint (a Roasted Meat Block lying
    across, its bone along the spit) between their tops, a block clear of the flames."""
    for u in (-1, 1):
        for y in range(1, height + 1):
            f.set(u, y, 0, post)
    f.set(0, height, 0, ROAST)


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
        # a spear planted upright (a lightning rod stood in for it until October 22, 2026)
        f.set(u, 3, 0, "dross:planted_spear")


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
    """A sled 2 wide and 4 long, its runners curling up at the front (v), with a load: True or "mixed" (a barrel,
    hides and furs), "logs" (firewood), "meat" (joints of meat under a hide), "hides" (bundles of wool), "ice" (cut
    blocks of packed ice), "harvest" (crates of Frostroot and baskets of Rimeberries) or None. Its deck is planks, full blocks, so the load sits on it (the user, October 20:
    anything on a slab floats; "redesign the sleighs so it's just a full block"), trapdoors along its sides for the
    runners, a stair for its curled-up prow."""
    for u in (0, 1):
        for v in range(0, 3):
            f.set(u, 1, v, "spruce_planks")
        f.set(u, 1, 3, "spruce_stairs[facing={front},half=bottom]")
    for v in range(0, 3):
        f.set(-1, 1, v, "spruce_trapdoor[facing={left},half=bottom,open=true]")
        f.set(2, 1, v, "spruce_trapdoor[facing={right},half=bottom,open=true]")
    if cargo is True or cargo == "mixed":
        f.set(0, 2, 1, "barrel[facing=up,open=false]")
        f.set(1, 2, 0, "brown_wool")
        f.set(1, 2, 1, "white_carpet")
        f.set(0, 2, 0, "white_carpet")
    elif cargo == "logs":
        for u in (0, 1):
            for v in (0, 1, 2):
                f.set(u, 2, v, "spruce_log[axis={fb}]")
        f.set(0, 3, 1, "spruce_log[axis={fb}]")
    elif cargo == "meat":
        f.set(0, 2, 0, "dross:meat_block[axis={fb}]")
        f.set(1, 2, 0, "dross:meat_block[axis={fb}]")
        f.set(0, 2, 1, "dross:meat_block[axis={fb}]")
        f.set(1, 2, 1, "brown_carpet")
        f.set(0, 3, 0, "light_gray_carpet")
        f.set(0, 2, 2, DRIED)
    elif cargo == "hides":
        f.set(0, 2, 0, "brown_wool")
        f.set(1, 2, 0, "light_gray_wool")
        f.set(0, 2, 1, "white_wool")
        f.set(0, 3, 0, "gray_carpet")
        f.set(1, 2, 1, "brown_carpet")
    elif cargo == "ice":
        for u, v in ((0, 0), (1, 0), (0, 1)):
            f.set(u, 2, v, "packed_ice")
        f.set(0, 3, 0, "packed_ice")
        f.set(1, 2, 1, "blue_ice")
    elif cargo == "harvest":
        f.set(0, 2, 0, "dross:frostroot_crate")
        f.set(1, 2, 0, "dross:frostroot_crate")
        f.set(0, 2, 1, "dross:rimeberry_basket")
        f.set(0, 3, 0, "dross:rimeberry_basket")
        f.set(1, 2, 1, "brown_carpet")


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
    """A spruce pole with a standing banner on top (cloth: its patterns, writer.banner's (code, dye) pairs)."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "spruce_fence")
    f.set(0, height + 1, 0, f"{colour}_banner[rotation={{rot_front}}]")
    f.banner_at = (f.xz(0, 0), height + 1)
    if cloth:
        from .writer import banner
        x, z = f.xz(0, 0)
        nbt = getattr(f.g, "block_nbt", None)
        if nbt is not None:
            nbt[(x, height + 1 + getattr(f, "dy", 0), z)] = banner(*cloth)


# Lamps inside a village (the user, October 22, 2026: "lighting inside the villages, not just the outside ... big poles
# and stuff"): always there, the village's own, not decorations.
ARCH_FOOTING = 3            # an arch's feet go this many blocks into the ground (its foot block on the surface included)


def court_lamp(f, height=5, kind="lantern"):
    """A tall pole in a courtyard: a stripped spruce post, a crossbar at its top, a lantern hung from each end."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "stripped_spruce_log[axis=y]")
    for u in (-1, 0, 1):
        f.set(u, height + 1, 0, "spruce_fence")
    for u in (-1, 1):
        f.set(u, height, 0, f"{kind}[hanging=true]")


def lane_lamp(f, height=4, kind="lantern"):
    """A pole on a lane's bank, its arm reaching out over the lane (in front of it), a lantern hung from its end."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "stripped_spruce_log[axis=y]")
    for v in (0, 1, 2):
        f.set(0, height + 1, v, "spruce_fence")
    f.set(0, height, 2, f"{kind}[hanging=true]")


def lantern_post(f, height=3, kind="lantern", arm=True):
    """A spruce post with a lantern: hanging from an arm (or standing on top)."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "spruce_fence")
    if arm:
        f.set(0, height + 1, 0, "spruce_fence")
        f.set(1, height + 1, 0, "spruce_fence")
        f.set(1, height, 0, f"{kind}[hanging=true]")
    else:
        f.set(0, height + 1, 0, f"{kind}[hanging=false]")


def ice_lantern(f, height=2):
    """A post of packed ice with a soul lantern in a cage of clear ice on top (soul light never melts the ice)."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "packed_ice")
    f.set(0, height + 1, 0, "soul_lantern[hanging=false]")
    for u, v in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        f.set(u, height + 1, v, "ice")
    f.set(0, height + 2, 0, "packed_ice")


def skull_post(f, height=2, skull="rimehorn_skull"):
    """A fence post with a mounted skull on it, facing out (a Rimehorn's, or a moose's: the user, October 22, 2026,
    "instead of the skeleton [skulls] with the fences put those instead")."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "spruce_fence")
    f.set(0, height + 1, 0, skull_on_post(skull))


def skull_on_post(skull):
    """A mounted skull standing on top of what's under it (boss/huntsman/skull/HuntSkullBlock), facing f's front."""
    return f"dross:{skull}[facing={{front}},wall=false]"


def skull_on_wall(skull):
    """A mounted skull hung on the wall behind it, facing f's front."""
    return f"dross:{skull}[facing={{front}},wall=true]"


def antler_post(f, height=3, skull="moose_skull"):
    """A log post crowned with a bull moose's skull and its antlers (until October 22, 2026 a skeleton skull with
    antlers of birch fences)."""
    for y in range(1, height + 1):
        f.set(0, y, 0, "stripped_spruce_log[axis=y]")
    f.set(0, height + 1, 0, skull_on_post(skull))


def antler_gate(f, width=3, height=4, wood="birch_fence", skull="moose_skull"):
    """Two posts over a path (width blocks between them) joined by a beam, a bull moose's skull and antlers in its
    middle (until October 22, 2026 a skeleton skull and antlers of birch fences)."""
    for u in (-(width // 2) - 1, width // 2 + 1):
        for y in range(1, height + 1):
            f.set(u, y, 0, "stripped_spruce_log[axis=y]")
    top = height + 1
    for u in range(-(width // 2) - 2, width // 2 + 3):
        f.set(u, top, 0, wood)
    f.set(0, top + 1, 0, skull_on_post(skull))


def bone_arch(f, width=5, height=6, mat="bone_block[axis=y]"):
    """A pair of great curved ribs of bone meeting over a path (v = along the path; the arch spans u). Its feet stand
    on footings of bone sunk into the ground (October 22, 2026: on uneven ground a foot could float or sink)."""
    half = width / 2.0 + 0.5
    pts = []
    n = 24
    for i in range(n + 1):
        a = math.pi * i / n
        pts.append((-math.cos(a) * half, 1 + math.sin(a) * (height - 1)))
    curve(f, [(u, y, 0) for u, y in pts], mat)
    # (as deep as the grid it's drawn in goes: a decoration's own template has only a layer or two of ground)
    deepest = max(-ARCH_FOOTING + 1, -(f.g.G + getattr(f, "dy", 0)))
    for side in (-1, 1):
        for y in range(deepest, 1):
            f.set(int(round(side * half)), y, 0, mat)


def curve(f, pts, mat):
    """Blocks along a polyline of local (u, y, v) points, touching face to face."""
    g = f.g
    dy = getattr(f, "dy", 0)
    for (u1, y1, v1), (u2, y2, v2) in zip(pts, pts[1:]):
        x1, z1 = f.xz(int(round(u1)), int(round(v1)))
        x2, z2 = f.xz(int(round(u2)), int(round(v2)))
        g.line((x1, int(round(y1)) + dy, z1), (x2, int(round(y2)) + dy, z2), f.state(mat))


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
    f.set(0, height + 1, 0, "skeleton_skull[rotation={skull_front}]")
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
    mat = mat or PATH
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
    mat = mat or PLAZA
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
                    "packed_ice", "ice", "blue_ice", "gravel", "cobblestone"):
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


# ---------------------------------------------------------------- the hunt's leavings (the Sunken Warren's)

MEAT = "dross:meat_block[axis=y]"   # a joint of raw meat hung to dry (boss/huntsman/village/MeatBlocks)
DRIED = "dried_kelp_block"          # dried meat and fish, in dark bundles


def meat_rack(f, length=4, height=3):
    """A drying rack hung with joints of red meat and bundles of dried meat."""
    drying_rack(f, length=length, height=height, hang=(MEAT, DRIED, MEAT))


def bone_pile(f, big=False):
    """A heap of bones by the camp: bone blocks lying every way, a skull on top."""
    f.set(0, 1, 0, "bone_block[axis={lr}]")
    f.set(1, 1, 0, "bone_block[axis=y]")
    f.set(0, 1, 1, "bone_block[axis={fb}]")
    f.set(0, 2, 0, "skeleton_skull[rotation={skull_front}]")
    if big:
        f.set(-1, 1, 0, "bone_block[axis={fb}]")
        f.set(1, 1, 1, "bone_block[axis={lr}]")
        f.set(1, 2, 0, "bone_block[axis={lr}]")
        f.set(1, 3, 0, "skeleton_skull[rotation={skull_right}]")


def skull_pile(f):
    """Skulls heaped on a flat stone: the hunters' count of their kills."""
    f.set(0, 1, 0, "cobblestone")
    f.set(1, 1, 0, "cobblestone_slab[type=bottom]")
    f.set(-1, 1, 0, "cobblestone_slab[type=bottom]")
    f.set(0, 2, 0, "skeleton_skull[rotation={skull_front}]")
    f.set(0, 1, 1, "skeleton_skull[rotation={skull_front}]")
    f.set(0, 1, -1, "bone_block[axis={lr}]")
    f.set(0, 2, -1, "skeleton_skull[rotation={skull_front}]")


def butcher_block(f):
    """Where the kill is cut up: a log stump to cut on, a carcass hanging from a frame beside it, a barrel, bones."""
    f.set(0, 1, 0, "stripped_spruce_log[axis=y]")
    f.set(0, 2, 0, "spruce_pressure_plate")
    for u in (2, 4):
        for y in (1, 2, 3):
            f.set(u, y, 0, "spruce_fence")
    f.set(3, 3, 0, "spruce_fence")
    f.set(3, 2, 0, "chain[axis=y]")
    f.set(3, 1, 0, MEAT)
    f.set(1, 1, 1, "barrel[facing=up,open=false]")
    f.set(-1, 1, 0, "bone_block[axis={fb}]")


def fishing_hole(f):
    """A hole cut through the ice to fish in: open water two deep (it never freezes over: a waterlogged light block
    on top, like the big lakes' holes), ringed and lined with packed ice; a rod (fence posts) leaning over it, a
    barrel for the catch with dried fish on it, a stool."""
    f.set(0, 0, 0, OPEN_WATER)
    f.set(0, -1, 0, "water")
    f.set(0, -2, 0, "gravel")
    for u in (-1, 0, 1):
        for v in (-1, 0, 1):
            if (u, v) != (0, 0):
                f.set(u, 0, v, "packed_ice")
                f.set(u, -1, v, "packed_ice")
    f.set(2, 1, 0, "barrel[facing=up,open=false]")
    f.set(2, 2, 0, DRIED)
    f.set(-2, 1, 0, "spruce_fence")
    f.set(-2, 2, 0, "spruce_fence")
    f.set(-1, 2, 0, "spruce_fence")
    f.set(0, 1, 2, "spruce_slab[type=bottom]")


def hide_rack(f, colours=("brown", "light_gray")):
    """Two hides stretched side by side in one long frame of fences."""
    hide_frame(f, colour=colours[0], w=2, h=2)
    g = f.sub(3, 0)
    for y in range(1, 5):
        g.set(2, y, 0, "spruce_fence")
    for u in range(0, 3):
        g.set(u, 1, 0, "spruce_fence")
        g.set(u, 4, 0, "spruce_fence")
    for u in range(0, 2):
        for y in (2, 3):
            g.set(u, y, 0, f"{colours[1]}_wool")


def spear_stand(f):
    """Three spears (lightning rods) upright in a stand of logs, a target behind them."""
    f.set(0, 1, 0, "spruce_log[axis={lr}]")
    f.set(1, 1, 0, "spruce_log[axis={lr}]")
    f.set(-1, 1, 0, "spruce_log[axis={lr}]")
    for u in (-1, 0, 1):
        # a spear planted upright (a lightning rod stood in for it until October 22, 2026)
        f.set(u, 2, 0, "dross:planted_spear")


def lean_to(f, icicles=True):
    """A hunters' lean-to: log posts, a roof of spruce stairs sloping down to the back with snow on it and its eave
    hung with icicles, a fur and a barrel under it."""
    for u in (-1, 1):
        f.set(u, 1, 0, "spruce_log[axis=y]")
        f.set(u, 2, 0, "spruce_log[axis=y]")
        f.set(u, 1, -1, "spruce_log[axis=y]")
    for u in (-1, 0, 1):
        f.set(u, 3, 0, "spruce_stairs[facing={front},half=bottom]")
        f.set(u, 2, -1, "spruce_stairs[facing={front},half=bottom]")
        f.set(u, 1, -2, "spruce_slab[type=bottom]")
    f.set(0, 1, 0, "brown_carpet")
    f.set(0, 1, -1, "barrel[facing=up,open=false]")
    if icicles:
        for u in (-1, 0, 1):
            f.set(u, 3, 1, "spruce_slab[type=bottom]")    # the eave, a block out over the front
            f.set(u, 2, 1, "dross:icicle")


# ---------------------------------------------------------------- more of the camp's life (round 2, October 19)

def meat_cache(f, height=2):
    """A cache on stilts, out of the animals' reach: four posts of Frostbitten Log, a floor of spruce planks on them,
    barrels, joints of meat and a hide stored on top, icicles hanging under it, a ladder up one post."""
    for u in (0, 2):
        for v in (0, 2):
            for y in range(1, height + 1):
                f.set(u, y, v, FROSTBITTEN)
    top = height + 1
    for u in range(0, 3):
        for v in range(0, 3):
            f.set(u, top, v, "spruce_planks")
    for u, v in ((1, 0), (0, 1), (2, 1), (1, 2)):
        f.set(u, height, v, "dross:icicle")
    f.set(0, top + 1, 0, "barrel[facing=up,open=false]")
    f.set(2, top + 1, 2, "barrel[facing=up,open=false]")
    f.set(1, top + 1, 1, MEAT)
    f.set(2, top + 1, 0, "dross:meat_block[axis={lr}]")
    f.set(0, top + 1, 2, DRIED)
    f.set(1, top + 1, 0, "brown_carpet")
    f.set(1, top + 2, 1, "light_gray_carpet")
    for y in range(1, top + 1):
        f.set(0, y, 3, "ladder[facing={front}]")


def crate_stack(f, lantern=False):
    """Barrels and crates (blocks of stripped spruce) stacked against each other, hides thrown over the top."""
    f.set(0, 1, 0, "barrel[facing=up,open=false]")
    f.set(1, 1, 0, "stripped_spruce_wood[axis=y]")
    f.set(2, 1, 0, "barrel[facing=up,open=false]")
    f.set(0, 1, 1, "stripped_spruce_wood[axis=y]")
    f.set(1, 1, 1, "barrel[facing=up,open=false]")
    f.set(0, 2, 0, "stripped_spruce_wood[axis=y]")
    f.set(1, 2, 0, "barrel[facing=up,open=false]")
    f.set(0, 3, 0, "brown_carpet")
    f.set(1, 3, 0, "light_gray_carpet")
    f.set(0, 2, 1, "white_carpet")
    f.set(2, 2, 0, "lantern[hanging=false]" if lantern else "gray_carpet")


def ice_sculpture(f, kind="rimehorn"):
    """A statue carved out of ice on a plinth of snow: "rimehorn" (a Rimehorn of packed ice, its great horn of clear
    ice), "angel" (the Snow Angel, the hunters' Ice Necromancer: a robe of snow, wings and a crown of clear ice),
    "crystal" (a spire of blue, packed and clear ice)."""
    if kind == "rimehorn":
        for u in (0, 1):
            for v in range(-1, 4):
                f.set(u, 1, v, "snow_block")
        for u, v in ((0, -1), (1, -1), (0, 2), (1, 2)):
            f.set(u, 2, v, "packed_ice")                    # its legs
        for u in (0, 1):
            for v in range(-1, 3):
                f.set(u, 3, v, "packed_ice")                # its body
            f.set(u, 4, -1, "blue_ice")                     # the shaggy hump
            f.set(u, 4, 0, "blue_ice")
            f.set(u, 3, 3, "packed_ice")                    # its head, low
        f.set(0, 4, 3, "ice")                               # the great horn, rising off its nose
        f.set(0, 5, 3, "ice")
        f.set(1, 4, 3, "packed_ice")                        # the small horn
    elif kind == "angel":
        for u in range(-1, 2):
            for v in range(-1, 2):
                f.set(u, 1, v, "packed_ice")
        for u, v in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
            f.set(u, 2, v, "snow_block")                    # the robe, wide at its hem
        f.set(0, 3, 0, "snow_block")
        f.set(0, 3, -1, "snow_block")
        f.set(0, 4, 0, "white_wool")                        # the hood
        f.set(0, 4, -1, "snow_block")
        f.set(0, 5, 0, "white_wool")
        for side in (-1, 1):                                # the wings, rising and spreading behind
            f.set(side, 3, -1, "ice")
            f.set(side, 4, -1, "ice")
            f.set(side * 2, 4, -1, "ice")
            f.set(side * 2, 5, -1, "ice")
            f.set(side * 3, 5, -1, "ice")
        f.set(0, 6, 0, "ice")                               # the crown of ice over the hood
    else:
        f.set(0, 1, 0, "blue_ice")
        f.set(1, 1, 0, "packed_ice")
        f.set(0, 1, 1, "packed_ice")
        f.set(0, 2, 0, "packed_ice")
        f.set(1, 2, 0, "ice")
        f.set(0, 3, 0, "ice")
        f.set(0, 4, 0, "ice")
        f.set(0, 2, 1, "ice")


def frostbloom_patch(f, spots=((0, 0), (1, 1), (-1, 1), (2, 0), (0, 2)), ground=True):
    """Frostblooms, Rimefall's glowing flower, planted in a little bed of Hoarfrost (ground=False: straight into the
    floor, which must be Hoarfrost or snow; Decor checks it)."""
    for u, v in spots:
        if ground:
            f.set(u, 0, v, "dross:hoarfrost")
        f.set(u, 1, v, "dross:frostbloom")


def kayak_rack(f):
    """A skin kayak resting on two trestles: its hull of spruce, pointed at both ends, a hide over it."""
    for v in (-1, 1):
        for u in (0, 1):
            f.set(u, 1, v, "spruce_fence")
    for u in (0, 1):
        for v in (-1, 0, 1):
            f.set(u, 2, v, "spruce_slab[type=top]")          # (a top slab: the hide lies flush on it)
        f.set(u, 2, 2, "spruce_stairs[facing={back},half=bottom]")
        f.set(u, 2, -2, "spruce_stairs[facing={front},half=bottom]")
        f.set(u, 3, 0, "brown_carpet")


def snow_block_stack(f):
    """Blocks of snow cut for building igloos, stacked in steps, snow settled on the top."""
    for u in range(3):
        for v in (0, 1):
            f.set(u, 1, v, "snow_block")
    for u in range(2):
        f.set(u, 2, 0, "snow_block")
    f.set(0, 3, 0, "snow_block")
    f.set(0, 4, 0, "snow[layers=2]")
    f.set(1, 3, 0, "snow[layers=3]")
    f.set(2, 2, 0, "snow[layers=2]")


def ice_block_stack(f):
    """Blocks of lake ice cut for walls (packed and blue ice), stacked on the ground."""
    f.set(0, 1, 0, "packed_ice")
    f.set(1, 1, 0, "blue_ice")
    f.set(2, 1, 0, "packed_ice")
    f.set(0, 2, 0, "packed_ice")
    f.set(1, 2, 0, "packed_ice")
    f.set(0, 3, 0, "blue_ice")


def lane_arch(f, width=5, height=6, mat="bone_block[axis=y]"):
    """A great rib of bone arching over a lane (the lane runs along v), icicles hanging under its crown."""
    bone_arch(f, width=width, height=height, mat=mat)
    half = int(width / 2.0 + 0.5)
    for u in range(-half + 2, half - 1):
        col = [y for y in range(1, height + 3) if f.get(u, y, 0) is not None]
        if not col:
            continue
        low = min(col)
        if low - 1 >= 4 and f.get(u, low - 1, 0) is None:
            f.set(u, low - 1, 0, "dross:icicle")


def rimehorn_skull(f):
    """A Rimehorn's skull mounted on a post, its two horns of clear ice (until October 22, 2026 built of bone blocks
    and ice)."""
    for y in (1, 2):
        f.set(0, y, 0, "stripped_spruce_log[axis=y]")
    f.set(0, 3, 0, skull_on_post("rimehorn_skull"))


def windbreak(f, length=5):
    """A low wall of snow blocks against the wind, snow lying along its top and drifted up behind it."""
    for u in range(length):
        f.set(u, 1, 0, "snow_block")
        if 0 < u < length - 1:
            f.set(u, 2, 0, "snow_block")
            f.set(u, 3, 0, "snow[layers=3]")
            f.set(u, 1, -1, "snow[layers=5]")
        else:
            f.set(u, 2, 0, "snow[layers=4]")


def stew_fire(f):
    """A fire with a stew pot hung over it from a frame of fences: the pot (a cauldron of water) on a chain, a block
    over the flames, a ring of stones."""
    f.set(0, 1, 0, "campfire[facing=north,lit=true,signal_fire=false]")
    for u in (-1, 1):
        for y in range(1, 5):
            f.set(u, y, 0, "spruce_fence")
    f.set(0, 4, 0, "spruce_fence")
    f.set(0, 3, 0, "chain[axis=y]")
    f.set(0, 2, 0, "water_cauldron[level=3]")
    for u, v in ((0, 1), (0, -1), (-1, 1), (1, 1), (-1, -1), (1, -1)):
        f.set(u, 1, v, STONE_RING)


def smoking_rack(f, length=3):
    """Joints of meat smoking over a low fire: a log frame over a trench of embers, the meat hung on chains in the
    smoke."""
    for u in (-1, length):
        for y in range(1, 5):
            f.set(u, y, 0, "spruce_log[axis=y]")
        f.set(u, 5, 0, "snow[layers=2]")
    for u in range(0, length):
        f.set(u, 4, 0, "spruce_fence")
        f.set(u, 3, 0, "chain[axis=y]")
        f.set(u, 2, 0, MEAT if u % 2 == 0 else DRIED)
        f.set(u, 1, 0, "campfire[facing=north,lit=true,signal_fire=false]" if u == length // 2 else "cobblestone")


def hide_line(f, length=5, colours=("brown", "light_gray", "white")):
    """Hides hung over a line between two posts to dry, in the hunters' colours."""
    for u in (0, length - 1):
        for y in range(1, 4):
            f.set(u, y, 0, "spruce_fence")
    for u in range(1, length - 1):
        f.set(u, 3, 0, "spruce_fence")
        if u % 2 == 1 or length <= 4:
            f.set(u, 2, 0, f"{colours[(u // 2) % len(colours)]}_wool")


def anvil_corner(f):
    """Where the hunters mend their gear: an anvil, a grindstone, spears (lightning rods) in a rack, a barrel."""
    f.set(0, 1, 0, "chipped_anvil[facing={left}]")
    f.set(2, 1, 0, "grindstone[face=floor,facing={front}]")
    f.set(1, 1, -1, "barrel[facing=up,open=false]")
    for u in (0, 1, 2):
        f.set(u, 1, -2, "spruce_fence")
    # a spear planted upright (a lightning rod stood in for it until October 22, 2026)
    f.set(0, 2, -2, "dross:planted_spear")
    f.set(2, 2, -2, "dross:planted_spear")
    f.set(1, 2, -2, "spruce_fence")
    # a spear planted upright (a lightning rod stood in for it until October 22, 2026)
    f.set(1, 3, -2, "dross:planted_spear")


def dog_sleds(f):
    """Two sleds side by side, one loaded with firewood, the other with joints of meat."""
    sled(f, cargo="logs")
    sled(f.sub(3, 0), cargo="meat")
