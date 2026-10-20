"""Draws the Meat Block and the Roasted Meat Block (the hunters' generic joints of meat, the user's request of
October 19, 2026: "a meat block ... just goes into a generic meat block, and they have it over a center fire").

Both are pillars like a log (a joint with a bone through it): the side is the outside of the joint (the meat's grain
running along it, a band of rind and fat at each end where it was cut), the end the cut face (a rim of rind, a ring
of fat, the muscle in a few groups split by seams of fat, the bone in the middle round its marrow). Original 16 x 16
pixel art, drawn from a fixed seed so it comes out the same every time.

    python tools/huntsman/meat_blocks.py

Writes assets/dross/textures/block/meat_block.png, meat_block_end.png, roasted_meat_block.png,
roasted_meat_block_end.png, and a preview, boss-previews/rimefall/huntsman/villages/meat_blocks.png (each texture
8 times its size, and the blocks as the villages use them: a joint hanging on a rack, one roasting on a spit).
"""
import math
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(PROJECT, "src", "main", "resources", "assets", "dross", "textures", "block")
PREVIEW = os.path.join(PROJECT, "boss-previews", "rimefall", "huntsman", "villages", "meat_blocks.png")
S = 16
SEED = 20261019

# raw: the deep reds of muscle, the cream of fat, the skin of the rind
RAW = [(104, 18, 26), (128, 24, 32), (150, 32, 38), (170, 44, 46), (192, 62, 60)]
FAT = [(218, 168, 160), (236, 204, 194), (246, 228, 218)]
RIND = [(170, 112, 92), (192, 138, 112), (214, 170, 144)]
BONE = [(172, 154, 120), (212, 198, 164), (236, 228, 202), (250, 246, 232)]
MARROW = [(116, 36, 38), (150, 58, 52)]

# roasted: a browned, glazed crust (lumpy, with glints of fat), the rind crisped golden, cooked meat inside
CRUST = [(52, 26, 16), (78, 40, 20), (104, 56, 26), (130, 72, 34), (156, 92, 44), (182, 114, 56)]
GLAZE = [(206, 136, 62), (232, 176, 96), (252, 222, 160)]
CRACKLING = [(150, 86, 36), (188, 122, 52), (216, 160, 78)]
COOKED = [(140, 86, 66), (166, 108, 84), (188, 130, 102), (206, 150, 120)]
SMOKE_RING = [(160, 64, 58), (182, 84, 72)]
ROAST_BONE = [(160, 132, 96), (208, 188, 146), (234, 220, 184)]
ROAST_MARROW = [(88, 46, 28), (118, 66, 38)]

C = (S - 1) / 2.0          # the middle (between the four middle pixels)


def _new():
    return [[None] * S for _ in range(S)]


def _save(px, name):
    img = Image.new("RGBA", (S, S))
    for x in range(S):
        for y in range(S):
            img.putpixel((x, y), px[x][y] + (255,))
    img.save(os.path.join(OUT, name + ".png"))
    return img


def _grain(rng, shades, weights, run=(3, 7)):
    """Streaks along each column (the grain runs along the joint, up the side), each a short run of one shade."""
    px = _new()
    for x in range(S):
        base = rng.choices(range(len(shades)), weights)[0]
        y = 0
        while y < S:
            n = rng.randint(*run)
            k = max(0, min(len(shades) - 1, base + rng.choice((-1, 0, 0, 0, 1))))
            for yy in range(y, min(S, y + n)):
                px[x][yy] = shades[k]
            y += n
    return px


def _vein(rng, px, colours, y0, y1, x=None):
    """A thin vein of fat wandering up the grain (it drifts sideways now and then)."""
    x = rng.randint(1, S - 2) if x is None else x
    for y in range(y0, y1):
        px[x][y] = colours[0] if rng.random() < 0.7 else colours[1]
        if rng.random() < 0.45:
            nx = max(0, min(S - 1, x + rng.choice((-1, 1))))
            px[nx][y] = colours[0]        # step sideways face to face, so the vein stays one line
            x = nx


def raw_side(rng):
    px = _grain(rng, RAW, (1, 3, 5, 3, 1))
    for _ in range(5):
        y0 = rng.randint(2, 10)
        _vein(rng, px, (FAT[0], FAT[1]), y0, min(S - 2, y0 + rng.randint(3, 6)))
    for _ in range(12):
        px[rng.randint(0, S - 1)][rng.randint(3, S - 4)] = FAT[0]
    for _ in range(10):                   # a few highlights where the meat is wet
        px[rng.randint(0, S - 1)][rng.randint(3, S - 4)] = RAW[4]
    for x in range(S):                    # the rind at both ends: a line of skin, a line of fat
        px[x][0] = RIND[rng.choice((0, 1, 1, 2))]
        px[x][S - 1] = RIND[rng.choice((0, 0, 1, 1))]
        px[x][1] = FAT[rng.choice((1, 2, 2))]
        px[x][S - 2] = FAT[rng.choice((1, 1, 2))]
        if rng.random() < 0.3:
            px[x][2] = FAT[0]
        if rng.random() < 0.3:
            px[x][S - 3] = FAT[0]
    return px


def _ring(x, y):
    """Which ring of the cut face a pixel is in, counting in from the rim (0), with the corners rounded off."""
    a, b = min(x, S - 1 - x), min(y, S - 1 - y)
    for k in range(S):
        if a <= k or b <= k or a + b <= 2 * k + 2:
            return k
    return S


def _bone(px, bone, marrow):
    """The bone in the middle: a 2 x 2 marrow in a ring of bone 4 x 4, lit from the top left."""
    for x in range(6, 10):
        for y in range(6, 10):
            corner = x in (6, 9) and y in (6, 9)
            if 7 <= x <= 8 and 7 <= y <= 8:
                px[x][y] = marrow[1] if (x, y) == (7, 7) else marrow[0]
            elif corner:
                continue                   # round it off
            else:
                lit = x + y < 15
                px[x][y] = bone[2] if lit else bone[1]
    for x, y in ((6, 6), (9, 9)):
        px[x][y] = bone[0] if (x, y) == (9, 9) else bone[1]


def _seams(rng, n=3):
    """Seams of fat between the muscle groups: rays from the bone out to the rim, a little crooked."""
    cells = set()
    a0 = rng.uniform(0, 2 * math.pi)
    for k in range(n):
        a = a0 + 2 * math.pi * k / n + rng.uniform(-0.4, 0.4)
        r = 2.2
        x, y = C + math.cos(a) * r, C + math.sin(a) * r
        while _ring(int(round(x)), int(round(y))) > 2:
            cells.add((int(round(x)), int(round(y))))
            a += rng.uniform(-0.25, 0.25)
            x += math.cos(a) * 0.7
            y += math.sin(a) * 0.7
    return cells


def raw_end(rng):
    px = _new()
    seams = _seams(rng)
    groups = [RAW[2], RAW[3], RAW[2], RAW[1]]
    for x in range(S):
        for y in range(S):
            e = _ring(x, y)
            if e == 0:
                px[x][y] = RIND[rng.choice((0, 1, 1, 2))]
            elif e == 1:
                px[x][y] = FAT[rng.choice((1, 2, 2))]
            elif (x, y) in seams:
                px[x][y] = FAT[0]
            else:
                g = int((math.atan2(y - C, x - C) + math.pi) / (2 * math.pi) * len(groups)) % len(groups)
                col = groups[g]
                r = rng.random()
                if r < 0.18:
                    col = RAW[max(0, RAW.index(col) - 1)]
                elif r < 0.26:
                    col = RAW[min(len(RAW) - 1, RAW.index(col) + 1)]
                elif r < 0.31:
                    col = FAT[0]          # marbling flecks
                px[x][y] = col
    _bone(px, BONE, MARROW)
    return px


def roasted_side(rng):
    px = [[CRUST[rng.choice((2, 3, 3, 3, 4))] for _ in range(S)] for _ in range(S)]
    # lumps of crust: lit on their top left, shadowed under their bottom right, a glint of fat on the brightest
    for _ in range(16):
        cx, cy = rng.uniform(0, S), rng.uniform(1.5, S - 2.5)
        r = rng.uniform(1.2, 2.2)
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for y in range(int(cy - r - 1), int(cy + r + 2)):
                xx = x % S
                if not 2 <= y <= S - 3:
                    continue
                d = math.hypot(x - cx, y - cy)
                if d > r:
                    continue
                side = (x - cx) + (y - cy)
                if side < -r * 0.5:
                    px[xx][y] = CRUST[5] if d > r * 0.5 else GLAZE[0]
                elif side > r * 0.6:
                    px[xx][y] = CRUST[1]
                else:
                    px[xx][y] = CRUST[4]
        gx, gy = int(round(cx - r * 0.45)) % S, int(round(cy - r * 0.45))
        if 2 <= gy <= S - 3 and rng.random() < 0.6:
            px[gx][gy] = GLAZE[2]
    for _ in range(7):                    # charred specks in the crevices
        px[rng.randint(0, S - 1)][rng.randint(2, S - 3)] = CRUST[0]
    for _ in range(5):                    # drips of glaze
        x, y = rng.randint(0, S - 1), rng.randint(3, S - 6)
        for k in range(rng.randint(2, 3)):
            px[x][y + k] = GLAZE[1] if k == 0 else GLAZE[0]
    for x in range(S):                    # the crackling at both ends, roasted golden and crisp
        for y in (0, S - 1):
            px[x][y] = CRACKLING[rng.choice((0, 0, 1, 2))]
        for y in (1, S - 2):
            px[x][y] = CRACKLING[rng.choice((1, 2, 2))] if rng.random() < 0.75 else GLAZE[1]
    return px


def roasted_end(rng):
    px = _new()
    seams = _seams(rng)
    groups = [COOKED[2], COOKED[1], COOKED[2], COOKED[3]]
    for x in range(S):
        for y in range(S):
            e = _ring(x, y)
            if e == 0:
                px[x][y] = CRUST[rng.choice((0, 1, 1))]
            elif e == 1:
                px[x][y] = CRUST[3] if rng.random() < 0.7 else GLAZE[0]
            elif e == 2:
                px[x][y] = SMOKE_RING[rng.choice((0, 1))]
            elif (x, y) in seams:
                px[x][y] = COOKED[0]
            else:
                g = int((math.atan2(y - C, x - C) + math.pi) / (2 * math.pi) * len(groups)) % len(groups)
                col = groups[g]
                r = rng.random()
                if r < 0.2:
                    col = COOKED[max(0, COOKED.index(col) - 1)]
                elif r < 0.3:
                    col = COOKED[min(len(COOKED) - 1, COOKED.index(col) + 1)]
                px[x][y] = col
    _bone(px, ROAST_BONE, ROAST_MARROW)
    return px


def _iso_cube(top, left, right, size=48):
    """A quick 3D cube of three textures (top, the left face, the right face) for the preview."""
    w = size * 2
    img = Image.new("RGBA", (w, int(size * 2.2)), (0, 0, 0, 0))
    for name, tex, shade in (("top", top, 1.0), ("left", left, 0.82), ("right", right, 0.64)):
        t = tex.convert("RGBA")
        for tx in range(S):
            for ty in range(S):
                r, g, b, a = t.getpixel((tx, ty))
                col = (int(r * shade), int(g * shade), int(b * shade), 255)
                u0, v0 = tx / S, ty / S
                u1, v1 = (tx + 1) / S, (ty + 1) / S
                if name == "top":
                    def P(u, v):
                        return (size + (u - v) * size, (u + v) * size * 0.5)
                elif name == "left":
                    def P(u, v):
                        return (u * size, size * 0.5 + u * size * 0.5 + v * size)
                else:
                    def P(u, v):
                        return (size + u * size, size + -u * size * 0.5 + v * size)
                poly = [P(u0, v0), P(u1, v0), P(u1, v1), P(u0, v1)]
                ImageDraw.Draw(img).polygon(poly, fill=col)
    return img


def preview(textures):
    scale = 8
    pad = 14
    tw = S * scale
    names = list(textures)
    cubes = [
        ("hanging joint", _iso_cube(textures["meat_block_end"], textures["meat_block"], textures["meat_block"])),
        ("on the spit", _iso_cube(textures["roasted_meat_block"], textures["roasted_meat_block_end"],
                                  textures["roasted_meat_block"].rotate(90, expand=False))),
    ]
    w = pad + len(names) * (tw + pad) + sum(c.width + pad for _n, c in cubes)
    h = tw + 2 * pad + 20
    sheet = Image.new("RGBA", (w, h), (28, 30, 38, 255))
    d = ImageDraw.Draw(sheet)
    x = pad
    for name in names:
        sheet.paste(textures[name].resize((tw, tw), Image.NEAREST), (x, pad + 18))
        d.text((x, 4), name, fill=(220, 225, 235, 255))
        x += tw + pad
    for name, cube in cubes:
        sheet.alpha_composite(cube, (x, pad + 18))
        d.text((x, 4), name, fill=(220, 225, 235, 255))
        x += cube.width + pad
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    sheet.save(PREVIEW)


def main():
    os.makedirs(OUT, exist_ok=True)
    textures = {}
    for name, fn in (("meat_block", raw_side), ("meat_block_end", raw_end), ("roasted_meat_block", roasted_side),
                     ("roasted_meat_block_end", roasted_end)):
        textures[name] = _save(fn(random.Random(f"{SEED}:{name}")), name)
    preview(textures)
    print("meat blocks: wrote", ", ".join(n + ".png" for n in textures))


if __name__ == "__main__":
    main()
