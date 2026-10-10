"""No. 7  Monolith Ring: a henge of giant dark standing stones inside a ditch and bank. An outer circle of sarsens,
some still capped with lintels, others leaning or fallen and broken; inside, a horseshoe of five great trilithons
opening toward the front, their inner faces carved with glowing blue runes. An avenue of small stones leads in.
At the centre the frame stands on an altar stone, and above it a ring of rune stones hangs silently in the air."""
import math

import leak
import mc
from common import frame_with_steps
from voxel import AIR, FRAME, Grid, Mix, lantern, slab, stairs

INFO = dict(number=7, id="monolith_ring", name="Monolith Ring", mood="ancient, eerie")

W, D, H, G = 71, 79, 34, 2
CX, CZ = 35, 33
R_OUT = 24.0           # the outer circle
R_DITCH = (29.0, 31.0)
R_BANK = (31.5, 34.0)

STONE = Mix(("deepslate[axis=y]", 3), ("cobbled_deepslate", 2), ("tuff", 2), ("smooth_basalt", 1), patch=0.6, scale=4)
LINTEL = Mix(("deepslate[axis=x]", 3), ("polished_deepslate", 1), ("tuff", 1), patch=0.5)
RUNE = "blue_stained_glass"


def stone_block(g, cx, cz, along, w, d, h, lean=(0, 0), runes=None, mat=STONE):
    """An upright stone w wide (along `along`, 'x' or 'z'), d thick, h tall, optionally leaning by
    (dx, dz) at its top. runes: the side ('+' or '-' across) that gets glowing rune marks."""
    for y in range(1, h + 1):
        t = (y - 1) / max(h - 1, 1)
        ox, oz = int(round(lean[0] * t)), int(round(lean[1] * t))
        for i in range(-(w // 2), w - w // 2):
            for j in range(-(d // 2), d - d // 2):
                x = cx + ox + (i if along == "x" else j)
                z = cz + oz + (j if along == "x" else i)
                # round off the top corners a little
                if y == h and (i in (-(w // 2), w - w // 2 - 1)) and g.chance(0.6):
                    continue
                g.set(x, y, z, mat)
    if runes:
        side = 1 if runes == "+" else -1
        j = (d - d // 2 - 1) if side > 0 else -(d // 2)
        for y in range(3, h - 2):
            if (y * 7 + cx + cz) % 5 in (0, 1):
                i = ((y * 3 + cx) % w) - w // 2
                t = (y - 1) / max(h - 1, 1)
                ox, oz = int(round(lean[0] * t)), int(round(lean[1] * t))
                x = cx + ox + (i if along == "x" else j)
                z = cz + oz + (j if along == "x" else i)
                xi = cx + ox + (i if along == "x" else j - side)
                zi = cz + oz + (j - side if along == "x" else i)
                g.set(x, y, z, RUNE)
                g.set(xi, y, zi, "sea_lantern")


def fallen_stone(g, x0, z0, direction, length, w, mat=STONE, breaks=(), y0=1):
    dx, dz = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}[direction]
    for i in range(length):
        if i in breaks:
            continue
        for a in range(w):
            for y in range(y0, y0 + 2):
                x = x0 + dx * i + (a if dz else 0)
                z = z0 + dz * i + (a if dx else 0)
                g.set(x, y, z, mat)


def build():
    g = Grid(W, H, D, G, seed=707)

    # ---- the ditch and the bank (the henge), with a causeway across at the front
    for x in range(W):
        for z in range(D):
            r = math.hypot(x - CX, z - CZ)
            front = abs(x - CX) <= 3 and z > CZ
            if R_DITCH[0] <= r <= R_DITCH[1] and not front:
                g.set(x, 0, z, AIR)
                g.set(x, -1, z, Mix(("coarse_dirt", 2), ("gravel", 1), ("mud", 1)))
            elif R_BANK[0] <= r <= R_BANK[1] and not front:
                h = 2 if abs(r - sum(R_BANK) / 2) < 0.9 else 1
                for y in range(1, h + 1):
                    g.set(x, y, z, "dirt" if y < h else "grass_block")
            elif r < R_DITCH[0]:
                g.set(x, 0, z, Mix(("coarse_dirt", 3), ("podzol", 1), ("rooted_dirt", 1), ("grass_block", 2), patch=0.6))

    # ---- the outer circle: 16 sarsens, lintels on some neighbours
    n = 16
    tops = {}
    for k in range(n):
        a = k / n * 2 * math.pi + math.pi / 2 + math.pi / n
        x = int(round(CX + math.cos(a) * R_OUT))
        z = int(round(CZ + math.sin(a) * R_OUT))
        along = "x" if abs(math.sin(a)) > abs(math.cos(a)) else "z"
        if k in (3, 9):
            continue  # gone (fallen, below)
        lean = (0, 0)
        if k == 6:
            lean = (2, 1)
        if k == 12:
            lean = (-1, -2)
        h = 13 + (k * 7) % 4
        stone_block(g, x, z, along, 3, 2, h, lean=lean)
        tops[k] = (x, z, h, along, lean)
    for k in (0, 1, 13, 14, 15):
        if k in tops and (k + 1) % n in tops and tops[k][4] == (0, 0) and tops[(k + 1) % n][4] == (0, 0):
            x1, z1, h1, _, _ = tops[k]
            x2, z2, h2, _, _ = tops[(k + 1) % n]
            y = min(h1, h2) + 1
            g.line((x1, y, z1), (x2, y, z2), LINTEL, r=0.8)
    # fallen sarsens, broken in two
    a = 3 / n * 2 * math.pi + math.pi / 2 + math.pi / n
    fallen_stone(g, int(CX + math.cos(a) * R_OUT) - 2, int(CZ + math.sin(a) * R_OUT), "east", 15, 3, breaks=(6, 7))
    a = 9 / n * 2 * math.pi + math.pi / 2 + math.pi / n
    fallen_stone(g, int(CX + math.cos(a) * R_OUT), int(CZ + math.sin(a) * R_OUT) - 1, "north", 14, 3, breaks=(5,))

    # ---- the inner horseshoe: five great trilithons, opening toward the front
    tri = [(-105, 18), (-140, 20), (180, 22), (140, 20), (105, 18)]   # angle from the front (degrees), height
    for i, (deg, h) in enumerate(tri):
        a = math.radians(deg) + math.pi / 2
        r = 12.5
        mx = CX + math.cos(a) * r
        mz = CZ + math.sin(a) * r
        tx, tz = -math.sin(a), math.cos(a)            # tangent: the two uprights stand apart along it
        p1 = (int(round(mx + tx * 2.6)), int(round(mz + tz * 2.6)))
        p2 = (int(round(mx - tx * 2.6)), int(round(mz - tz * 2.6)))
        along = "x" if abs(tx) > abs(tz) else "z"
        rune_side = "+" if (CX - mx) * (1 if along == "z" else 0) + (CZ - mz) * (1 if along == "x" else 0) > 0 else "-"
        if i == 3:
            # this one broke: one upright stands, the other and the lintel lie in pieces
            stone_block(g, p1[0], p1[1], along, 2, 2, h - 3, runes=rune_side)
            fallen_stone(g, p2[0] - 1, p2[1] + 2, "south" if along == "x" else "east", 13, 2, breaks=(4, 9))
            continue
        stone_block(g, p1[0], p1[1], along, 2, 2, h, runes=rune_side)
        stone_block(g, p2[0], p2[1], along, 2, 2, h, runes=rune_side)
        g.line((p1[0] + tx * 1.2, h + 1, p1[1] + tz * 1.2), (p2[0] - tx * 1.2, h + 1, p2[1] - tz * 1.2), LINTEL, r=0.9)

    # ---- the altar: a low stepped platform, a fallen altar stone, and the frame
    for y, r in ((1, 5.5), (2, 4.2)):
        g.disc(CX, CZ, r, y, Mix(("polished_deepslate", 3), ("deepslate_tiles", 2), ("cracked_deepslate_tiles", 1)))
    frame_with_steps(g, CX - 2, 3, CZ, 3, 5, "polished_deepslate_stairs")
    for (x, z) in ((CX - 4, CZ + 2), (CX + 4, CZ + 2), (CX - 4, CZ - 2), (CX + 4, CZ - 2)):
        g.set(x, 2, z, lantern("soul_lantern"))
    fallen_stone(g, CX + 3, CZ - 8, "west", 7, 2, mat=Mix(("polished_basalt[axis=x]", 2), ("smooth_basalt", 1)), y0=1)

    # ---- the avenue: two rows of small stones leading in from the front, and the heel stone
    for i, z in enumerate(range(CZ + 37, D - 1, 5)):
        for sx in (CX - 6, CX + 6):
            if g.chance(0.8):
                stone_block(g, sx, z, "x", 2, 1, 3 + (i + sx) % 3)
    stone_block(g, CX + 9, CZ + 33, "z", 3, 3, 9, lean=(1, 0))

    # ================================================================ weathering, rubble, the leak
    for x in range(W):
        for z in range(D):
            y = g.top_y(x, z)
            s = g.get(x, y, z)
            if s is not None and y >= 4 and mc.base(s) in ("cobbled_deepslate", "tuff", "deepslate") and g.chance(0.18):
                g.set(x, y, z, "mossy_cobblestone" if g.chance(0.5) else "moss_block")
    rays = {}
    for k in range(8):
        part = leak.crack_network(g, [(CX, CZ)], branches=1, length=30, fork=0.08, wobble=0.10,
                                  seed_angle=k * math.pi / 4 + math.pi / 8, spread=0.05)
        for c, v in part.items():
            rays[c] = max(rays.get(c, 0), v)
    leak.apply_cracks(g, rays, max_surface=3, climb=3, glow_above=0.5)
    leak.dead_land(g, CX, CZ, 26, 40, bush=0.08)
    leak.shards(g, rays, 8, min_dist=7, frame_center=(CX, CZ), tall=0.5)
    for (x, z, h) in ((4, 5, 8), (66, 7, 7), (3, 62, 6), (67, 66, 9), (12, 75, 5), (60, 76, 6)):
        leak.dead_tree(g, x, z, h, wood="dark_oak_log", branches=4)

    # the strange part: a ring of rune stones hanging in the air above the frame, and a shard at its middle
    for k in range(10):
        a = k / 10 * 2 * math.pi
        x = int(round(CX + math.cos(a) * 7))
        z = int(round(CZ + math.sin(a) * 7))
        y = 24 + (k % 3)
        for (dx, dy, dz) in ((0, 0, 0), (0, 1, 0)):
            g.set(x + dx, y + dy, z + dz, STONE if dy == 1 else "lapis_block")
            g.lock(x + dx, y + dy, z + dz)
    for y in (28, 29):
        g.set(CX, y, CZ, FRAME)
        g.lock(CX, y, CZ)
    return g, INFO
