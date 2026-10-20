"""Draws Rimefall's crops and their blocks (the user, October 20, 2026: "make it a custom plant for the biome ... a
berry farm outside"; Snow Lichen the user's pick over kale; the Rimeberry Bush "2 blocks tall"; "a compacted root,
like a root barrel ... and use that for decorations ... alongside the meat, as well as a berry [one]").

  Frostroots: four stages of spiky dark teal leaves frosted white at their tips, the last with the pale ice-blue tops of
      its roots showing at the soil.
  Snow Lichen: four stages of a pale grey-green crust branching up into tufts like coral, frost on its tips.
  Rimeberry Bush: a sprout, then a two-block bush (a lower and an upper texture for each of three stages) of dark
      spruce-green needles dusted with frost, icy-blue berries coming in at stage 2 and heavy at stage 3.
  Items: Frostroot (a root with its leafy top), Snow Lichen (a clump), Rimeberries (a cluster).
  Frostroot Crate (spruce boards, roots heaped in its top) and Rimeberry Basket (wicker, berries heaped in its top).

Original 16 x 16 pixel art, from fixed seeds so it comes out the same every time.

    python tools/huntsman/crops.py

Writes assets/dross/textures/block/ and item/, and the preview boss-previews/rimefall/huntsman/igloos/crops.png (every
texture 8 times its size, and the stages side by side).
"""
import math
import os
import random

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(PROJECT, "src", "main", "resources", "assets", "dross", "textures")
PREVIEW = os.path.join(PROJECT, "boss-previews", "rimefall", "huntsman", "igloos", "crops.png")
S = 16

# Frostroot: dark teal leaves, frost on their tips, the roots' pale ice-blue
LEAF = [(30, 66, 62), (42, 88, 80), (58, 112, 100), (84, 140, 124)]
FROST = [(196, 222, 228), (226, 240, 244)]
ROOT = [(120, 168, 196), (160, 198, 222), (204, 228, 242), (236, 248, 252)]
# Snow Lichen: pale grey-green crust, shadowed grey, icy tips
LICHEN = [(112, 132, 120), (150, 170, 156), (186, 202, 188), (214, 226, 214), (236, 242, 236)]
LICHEN_TIP = [(214, 232, 238), (244, 250, 252)]
# Rimeberry Bush: dark spruce needles, frost dust, icy-blue berries
NEEDLE = [(22, 48, 40), (32, 64, 52), (46, 84, 68), (64, 106, 86)]
DUST = [(176, 206, 210), (214, 232, 234)]
BERRY = [(52, 84, 160), (84, 126, 204), (136, 176, 236), (210, 230, 252)]
TWIG = [(70, 52, 40), (96, 72, 54)]
# the crate's spruce and the basket's wicker
SPRUCE = [(58, 40, 24), (82, 58, 34), (104, 76, 46), (124, 92, 56)]
WICKER = [(96, 70, 40), (132, 100, 60), (168, 132, 84), (196, 162, 108)]


def _new():
    return [[None] * S for _ in range(S)]


def _put(px, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        px[x][y] = c


def _img(px):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for x in range(S):
        for y in range(S):
            if px[x][y] is not None:
                img.putpixel((x, y), tuple(px[x][y]) + (255,))
    return img


def _save(px, folder, name):
    img = _img(px)
    os.makedirs(os.path.join(ASSETS, folder), exist_ok=True)
    img.save(os.path.join(ASSETS, folder, name + ".png"))
    return name, img


# ---------------------------------------------------------------- plants


def _spike(px, rng, x0, y0, height, lean, shades, tip):
    """A leaf or a branch growing up from (x0, y0) (y down), `height` pixels, leaning `lean` pixels over its length; its
    tip frosted."""
    pts = []
    for i in range(height):
        t = i / max(1, height - 1)
        x = int(round(x0 + lean * t * t))
        y = y0 - i
        pts.append((x, y))
        shade = shades[min(len(shades) - 1, int(t * len(shades)))]
        _put(px, x, y, shade)
        if i < height * 0.45 and rng.random() < 0.5:
            _put(px, x + (1 if lean >= 0 else -1), y, shades[max(0, int(t * len(shades)) - 1)])
    tx, ty = pts[-1]
    _put(px, tx, ty, tip[-1] if height > 4 else tip[0])
    return pts


def frostroots(stage):
    rng = random.Random(1000 + stage)
    px = _new()
    heights = [3, 6, 9, 12][stage]
    plants = [3, 8, 12] if stage < 2 else [2, 5, 8, 11, 13]
    for i, x in enumerate(plants):
        n = 2 + (stage >= 1) + (stage >= 3)
        for k in range(n):
            lean = rng.choice([-3, -2, -1, 1, 2, 3]) * (0.5 + 0.5 * k / max(1, n - 1))
            h = max(2, heights - rng.randint(0, 3) - k)
            _spike(px, rng, x + rng.choice([-1, 0, 0, 1]), 15, h, lean, LEAF, FROST)
        if stage == 3:
            # the root's top at the soil: a pale ice-blue crown, two pixels high
            for dx in (-1, 0, 1):
                _put(px, x + dx, 15, ROOT[1 + (dx == 0)])
            _put(px, x, 14, ROOT[3])
            _put(px, x - 1, 14, ROOT[2])
    return px


def snow_lichen(stage):
    rng = random.Random(2000 + stage)
    px = _new()
    heights = [2, 4, 7, 10][stage]
    clumps = [2, 6, 10, 13] if stage >= 1 else [3, 7, 12]
    for x in clumps:
        # a crusty base, then branches forking up like coral
        for dx in range(-2, 3):
            if rng.random() < 0.8:
                _put(px, x + dx, 15, LICHEN[rng.randint(0, 2)])
        stems = [(x + rng.choice([-1, 0, 1]), 15, heights)]
        while stems:
            sx, sy, h = stems.pop()
            y = sy
            for i in range(h):
                y = sy - i
                sx += rng.choice([0, 0, 0, -1, 1]) if i > 0 else 0
                t = i / max(1, heights)
                _put(px, sx, y, LICHEN[min(4, 1 + int(t * 4))])
                if i >= 2 and h - i > 2 and rng.random() < 0.35 and len(stems) < 4:
                    stems.append((sx + rng.choice([-1, 1]), y, h - i - 1))
            _put(px, sx, y, LICHEN_TIP[1])
            _put(px, sx, y + 1, LICHEN_TIP[0])
    return px


def _bush(px, rng, top, bottom, density, berries, cap, dust=True):
    """Spruce-green needles filling the rows top..bottom (y down) in a rounded mass, frost dust on top, `berries` berry
    clusters; `cap`: round off the top (an upper half's crown)."""
    for y in range(top, bottom + 1):
        span = 6.6 + 0.8 * rng.random()
        if cap:
            t = (y - top) / max(1, bottom - top)
            span = 2.5 + 4.5 * math.sqrt(max(0.0, t))
        for x in range(S):
            d = abs(x - 7.5)
            if d <= span and rng.random() < density:
                shade = NEEDLE[min(3, int(rng.random() * 3 + (1 if y < (top + bottom) / 2 else 0)))]
                px[x][y] = shade
    # twigs showing through near the bottom
    for x in (5, 10):
        for y in range(max(top, bottom - 4), bottom + 1):
            if rng.random() < 0.5:
                px[x][y] = TWIG[rng.randint(0, 1)]
    # frost dust on the top edge of the needles
    for x in (range(S) if dust else ()):
        for y in range(top, bottom + 1):
            if px[x][y] is not None and (y == 0 or px[x][y - 1] is None) and rng.random() < 0.55:
                px[x][y] = DUST[rng.randint(0, 1)]
                break
    placed = 0
    tries = 0
    while placed < berries and tries < 400:
        tries += 1
        x, y = rng.randint(2, 13), rng.randint(top + 1, bottom - 1)
        if px[x][y] is None or px[x][y] in BERRY:
            continue
        # a berry: a dark rim, a bright face, a white glint
        for dx, dy, c in ((0, 0, BERRY[1]), (1, 0, BERRY[0]), (0, 1, BERRY[0]), (1, 1, BERRY[0])):
            _put(px, x + dx, y + dy, c)
        _put(px, x, y, BERRY[2])
        if rng.random() < 0.6:
            _put(px, x, y, BERRY[3])
        placed += 1


def rimeberry(part, stage):
    rng = random.Random(3000 + stage * 10 + (part == "upper"))
    px = _new()
    if part == "sprout":
        for x0, lean, h in ((7, -2, 6), (8, 2, 7), (7, 0, 8), (6, -1, 4), (9, 1, 5)):
            _spike(px, rng, x0, 15, h, lean, NEEDLE, DUST)
        return px
    berries = {1: 0, 2: 5, 3: 11}[stage]
    if part == "lower":
        _bush(px, rng, 0, 15, 0.84, berries, cap=False, dust=False)
    else:
        top = {1: 7, 2: 4, 3: 3}[stage]
        _bush(px, rng, top, 15, 0.84, berries if stage > 1 else 0, cap=True)
    return px


# ---------------------------------------------------------------- items


def item_frostroot():
    px = _new()
    # the root: from its leafy top (11, 5) down to its tail (2, 14), 3 pixels thick at the top tapering to 1
    ax, ay, bx, by = 11.0, 5.0, 2.0, 14.0
    for x in range(S):
        for y in range(S):
            dx, dy = bx - ax, by - ay
            t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)))
            cx, cy = ax + t * dx, ay + t * dy
            dist = math.hypot(x - cx, y - cy)
            w = 1.9 * (1 - t) + 0.4
            if dist <= w:
                side = (x - cx) - (y - cy)          # above the line: lit
                px[x][y] = ROOT[3] if side > 0.7 else ROOT[2] if side > -0.5 else ROOT[1]
    for x, y in ((5, 11), (8, 9), (4, 13), (7, 11)):
        _put(px, x, y, ROOT[0])                     # the rings round a root
    _put(px, 1, 15, ROOT[0])
    rng = random.Random(41)
    for x0, lean, h in ((11, -2, 5), (12, 1, 6), (13, 3, 4)):
        _spike(px, rng, x0, 5, h, lean, LEAF, FROST)
    return px


def item_lichen():
    rng = random.Random(42)
    px = _new()
    for x in range(3, 13):
        for y in range(9, 14):
            if (x - 7.5) ** 2 / 30 + (y - 11.5) ** 2 / 6 < 1 and rng.random() < 0.9:
                px[x][y] = LICHEN[rng.randint(0, 2)]
    for x in (4, 6, 8, 10, 12):
        h = rng.randint(4, 7)
        sx = x
        for i in range(h):
            y = 10 - i
            sx += rng.choice([0, 0, -1, 1]) if i else 0
            _put(px, sx, y, LICHEN[min(4, 2 + i // 2)])
        _put(px, sx, 10 - h, LICHEN_TIP[1])
    return px


def item_rimeberries():
    px = _new()
    # a sprig: a twig and needles, three berries hanging off it
    for i, (x, y) in enumerate(((3, 4), (4, 4), (5, 5), (6, 5), (7, 6), (8, 6), (9, 7))):
        _put(px, x, y, TWIG[1])
    for x, y in ((3, 3), (5, 4), (4, 5), (7, 5), (8, 5), (6, 6)):
        _put(px, x, y, NEEDLE[2])
    _put(px, 4, 3, DUST[1])
    for bx, by in ((8, 8), (11, 9), (7, 11), (10, 12)):
        for dx in range(3):
            for dy in range(3):
                if (dx, dy) in ((0, 0), (2, 0), (0, 2), (2, 2)):
                    continue
                _put(px, bx + dx, by + dy, BERRY[1] if (dx, dy) != (2, 1) and (dx, dy) != (1, 2) else BERRY[0])
        _put(px, bx + 1, by + 1, BERRY[2])
        _put(px, bx, by + 1, BERRY[3])
    return px


# ---------------------------------------------------------------- the crate and the basket


def crate(part):
    rng = random.Random(50 + ["top", "side", "bottom"].index(part))
    px = _new()
    if part == "side":
        for y in range(S):
            for x in range(S):
                board = y // 5
                edge = y % 5 == 4
                c = SPRUCE[0] if edge else SPRUCE[1 + (board + (x * 7 + y * 3) % 5 // 3) % 3]
                px[x][y] = c
        for x in (0, 15):
            for y in range(S):
                px[x][y] = SPRUCE[0] if y % 4 else SPRUCE[1]
        for y in range(S):
            px[1][y] = SPRUCE[3] if y % 3 else SPRUCE[2]
            px[14][y] = SPRUCE[2]
        # roots peeking over the rim
        for x in range(2, 14, 3):
            px[x][0] = ROOT[2]
            px[x + 1][0] = ROOT[3]
        return px
    if part == "bottom":
        for y in range(S):
            for x in range(S):
                px[x][y] = SPRUCE[0] if x % 5 == 4 else SPRUCE[1 + (x + y) % 2]
        return px
    # the top: a spruce rim round a heap of roots lying every which way, dark between them, leafy stubs
    for y in range(S):
        for x in range(S):
            if x in (0, 15) or y in (0, 15):
                px[x][y] = SPRUCE[1]
            elif x in (1, 14) or y in (1, 14):
                px[x][y] = SPRUCE[3]
            else:
                px[x][y] = LEAF[0] if rng.random() < 0.6 else SPRUCE[0]
    for _ in range(11):
        x, y = rng.randint(2, 11), rng.randint(2, 11)
        dx, dy = rng.choice([(1, 1), (1, 0), (0, 1), (1, -1)])
        for i in range(4):
            xx, yy = x + dx * i, y + dy * i
            if 2 <= xx <= 13 and 2 <= yy <= 13:
                px[xx][yy] = ROOT[3] if i == 0 else ROOT[2] if i < 3 else ROOT[1]
        ex, ey = x - dx, y - dy
        if 2 <= ex <= 13 and 2 <= ey <= 13:
            px[ex][ey] = LEAF[2]
    return px


def basket(part):
    rng = random.Random(60 + ["top", "side", "bottom"].index(part))
    px = _new()
    if part in ("side", "bottom"):
        for y in range(S):
            for x in range(S):
                over = ((x // 2) + (y // 2)) % 2 == 0
                px[x][y] = WICKER[2 + (x % 2 == 0)] if over else WICKER[1]
                if y % 2 == 1 and not over:
                    px[x][y] = WICKER[0]
        if part == "side":
            for x in range(S):
                px[x][0] = WICKER[3]
                px[x][1] = WICKER[2]
                px[x][15] = WICKER[0]
            for x in range(1, 15, 3):
                px[x][0] = BERRY[1]
                px[x + 1][0] = BERRY[2]
        return px
    for y in range(S):
        for x in range(S):
            if x in (0, 15) or y in (0, 15):
                px[x][y] = WICKER[3]
            elif x in (1, 14) or y in (1, 14):
                px[x][y] = WICKER[1]
            else:
                px[x][y] = BERRY[0]
    for _ in range(26):
        x, y = rng.randint(2, 12), rng.randint(2, 12)
        px[x][y] = BERRY[1]
        px[x + 1][y] = BERRY[1]
        px[x][y + 1] = BERRY[1]
        px[x][y] = BERRY[2] if rng.random() < 0.7 else BERRY[3]
    for _ in range(4):
        x, y = rng.randint(3, 12), rng.randint(3, 12)
        px[x][y] = NEEDLE[2]
        px[x + 1][y] = NEEDLE[3]
    return px


# ---------------------------------------------------------------- write them all, and the preview


def main():
    rows = []
    rows.append(("Frostroots", [_save(frostroots(s), "block", f"frostroots_stage{s}") for s in range(4)]))
    rows.append(("Snow Lichen", [_save(snow_lichen(s), "block", f"snow_lichen_stage{s}") for s in range(4)]))
    bush = [_save(rimeberry("sprout", 0), "block", "rimeberry_bush_sprout")]
    for s in (1, 2, 3):
        bush.append(_save(rimeberry("lower", s), "block", f"rimeberry_bush_lower_stage{s}"))
        bush.append(_save(rimeberry("upper", s), "block", f"rimeberry_bush_upper_stage{s}"))
    rows.append(("Rimeberry Bush", bush))
    rows.append(("Items", [_save(item_frostroot(), "item", "frostroot"), _save(item_lichen(), "item", "snow_lichen"),
                           _save(item_rimeberries(), "item", "rimeberries")]))
    rows.append(("Crate and basket", [_save(crate(p), "block", f"frostroot_crate_{p}") for p in ("top", "side", "bottom")]
                 + [_save(basket(p), "block", f"rimeberry_basket_{p}") for p in ("top", "side", "bottom")]))
    # the preview: each texture 8x on a frosty background, the bush's halves stacked as it stands
    scale = 8
    tile = S * scale + 16
    width = max(len(r[1]) for r in rows) * tile + 220
    sheet = Image.new("RGBA", (width, len(rows) * (tile + 30) + 60 + tile * 2), (28, 32, 40, 255))
    d = ImageDraw.Draw(sheet)
    d.text((12, 12), "Rimefall's crops (tools/huntsman/crops.py)", fill=(236, 242, 248))
    y = 44
    for label, items in rows:
        d.text((12, y + tile // 2), label, fill=(170, 210, 236))
        for k, (name, img) in enumerate(items):
            x = 220 + k * tile
            sheet.paste((70, 82, 96, 255), (x, y, x + S * scale, y + S * scale))
            big = img.resize((S * scale, S * scale), Image.NEAREST)
            sheet.alpha_composite(big, (x, y))
            d.text((x, y + S * scale + 2), name, fill=(150, 160, 170))
        y += tile + 30
    # the bush as it stands, each stage's upper half on its lower half
    d.text((12, y + tile // 2), "The bush standing", fill=(170, 210, 236))
    for k, s in enumerate((1, 2, 3)):
        x = 220 + k * tile
        up = Image.open(os.path.join(ASSETS, "block", f"rimeberry_bush_upper_stage{s}.png")).resize((S * 6, S * 6), Image.NEAREST)
        lo = Image.open(os.path.join(ASSETS, "block", f"rimeberry_bush_lower_stage{s}.png")).resize((S * 6, S * 6), Image.NEAREST)
        sheet.paste((70, 82, 96, 255), (x, y, x + S * 6, y + S * 12))
        sheet.alpha_composite(up, (x, y))
        sheet.alpha_composite(lo, (x, y + S * 6))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    sheet.save(PREVIEW)
    print("wrote", sum(len(r[1]) for r in rows), "textures and", PREVIEW)


if __name__ == "__main__":
    main()
