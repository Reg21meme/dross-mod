"""
Draws the silverwood trees that the GameTest grew, so their shapes can be judged without starting the game.

    1. $env:DROSS_TREE_PREVIEWS="1"; .\gradlew.bat runGameTestServer --console=plain    (PowerShell; Git Bash:
       DROSS_TREE_PREVIEWS=1 ./gradlew.bat runGameTestServer --console=plain). TerrainBlocksGameTest.treeShapesGrow then
       writes build/tv1/trees.json (DROSS_TERRAIN_PREVIEWS=1 does too); without either it writes nothing.
    2. python tools/terrain/preview_trees.py                 (writes block-previews/trees.png)

Top: the first three trees of each shape, each on a patch of Silvermoss. Bottom: a dense stand of them (common, tall and
great silverwoods mixed, a few blocks apart) to show what the forest's canopy will be like. It's drawn at night, with
Stormvine full-bright as it is in the game. The textures are the real ones from src/main/resources.
"""
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
TREES = os.path.join(ROOT, "build", "tv1", "trees.json")
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "block")
OUT = os.path.join(ROOT, "block-previews", "trees.png")

PER_SHAPE = 3            # trees of each shape shown on their own
NIGHT = 0.72             # how much darker than daylight the blocks are drawn (Stormvine stays full-bright)
BG_TOP = np.array([7, 9, 20], float) / 255.0
BG_BOTTOM = np.array([24, 32, 60], float) / 255.0

# screen offsets per block step along x, z and y (an isometric view from the south-east, from above)
K = 7
IX = np.array([K, K / 2.0])
IZ = np.array([-K, K / 2.0])
IY = np.array([0.0, -K * 1.2])
SHADE = {"top": 1.0, "south": 0.8, "east": 0.62}


def tex(name):
    return np.asarray(Image.open(os.path.join(TEX, name + ".png")).convert("RGBA"), float)[:16, :16] / 255.0


def font(size, bold=False):
    for name in (("segoeuib.ttf" if bold else "segoeui.ttf"), ("arialbd.ttf" if bold else "arial.ttf")):
        try:
            return ImageFont.truetype(os.path.join("C:/Windows/Fonts", name), size)
        except OSError:
            continue
    return ImageFont.load_default()


TEXTURES = {}


def faces_for(state):
    """{face: texture} for a block state string, and whether it glows."""
    name = state.split("[")[0]
    if not TEXTURES:
        TEXTURES.update(log=tex("silverwood_log"), log_top=tex("silverwood_log_top"), leaves=tex("silverwood_leaves"),
                        vine=tex("stormvine"), moss_top=tex("silvermoss_top"), moss_side=tex("silvermoss_side"))
    if name == "dross:silverwood_log":
        return {"top": TEXTURES["log_top"], "south": TEXTURES["log"], "east": TEXTURES["log"]}, False
    if name == "dross:silverwood_leaves":
        return {"top": TEXTURES["leaves"], "south": TEXTURES["leaves"], "east": TEXTURES["leaves"]}, False
    if name == "dross:silvermoss":
        return {"top": TEXTURES["moss_top"], "south": TEXTURES["moss_side"], "east": TEXTURES["moss_side"]}, False
    if name == "dross:stormvine":
        return {"vine": TEXTURES["vine"]}, True
    return None, False


def project(x, y, z):
    return IX * x + IZ * z + IY * y


def draw_face(canvas, texture, origin, e1, e2, shade):
    """Draws a 16x16 texture onto the parallelogram origin + u*e1 + v*e2 (canvas: float RGB, alpha-blended)."""
    h, w = canvas.shape[:2]
    corners = np.array([origin, origin + e1, origin + e2, origin + e1 + e2])
    x0, y0 = np.floor(corners.min(axis=0)).astype(int)
    x1, y1 = np.ceil(corners.max(axis=0)).astype(int)
    x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, w), min(y1, h)
    if x1 <= x0 or y1 <= y0:
        return
    inv = np.linalg.inv(np.array([[e1[0], e2[0]], [e1[1], e2[1]]], float))
    ys, xs = np.mgrid[y0:y1, x0:x1]
    px, py = xs + 0.5 - origin[0], ys + 0.5 - origin[1]
    u = inv[0, 0] * px + inv[0, 1] * py
    v = inv[1, 0] * px + inv[1, 1] * py
    inside = (u >= 0) & (u < 1) & (v >= 0) & (v < 1)
    src = texture[np.clip((v * 16).astype(int), 0, 15), np.clip((u * 16).astype(int), 0, 15)]
    a = (src[..., 3] * inside)[..., None]
    region = canvas[y0:y1, x0:x1]
    region[:] = region * (1 - a) + src[..., :3] * shade * a


def render(blocks, size=None):
    """blocks: {(x, y, z): state}. Returns an RGBA PIL image of them (transparent background)."""
    if not blocks:
        return Image.new("RGBA", (10, 10))
    solid = {p for p, s in blocks.items() if not s.startswith("dross:stormvine")}
    pts = np.array(list(blocks.keys()), float)
    corners = [project(x, y, z) for x in (pts[:, 0].min(), pts[:, 0].max() + 1) for y in (pts[:, 1].min(), pts[:, 1].max() + 1)
               for z in (pts[:, 2].min(), pts[:, 2].max() + 1)]
    corners = np.array(corners)
    lo = corners.min(axis=0) - 4
    hi = corners.max(axis=0) + 4
    w, h = int(hi[0] - lo[0]), int(hi[1] - lo[1])
    canvas = np.zeros((h, w, 3))
    alpha = np.zeros((h, w))
    order = sorted(blocks, key=lambda p: p[0] + p[1] + p[2])
    for (x, y, z) in order:
        state = blocks[(x, y, z)]
        faces, glows = faces_for(state)
        if faces is None:
            continue
        light = 1.0 if glows else NIGHT
        base = project(x, y, z) - lo
        before = canvas.copy()
        if "vine" in faces:
            props = state[state.find("[") + 1:-1] if "[" in state else ""
            on = {kv.split("=")[0] for kv in props.split(",") if kv.endswith("=true")}
            t = faces["vine"]
            if "north" in on:   # hugging the block to its north: seen from the south, at the back of its cell
                draw_face(canvas, t, base + project(0, 1, 0.05), IX, -IY, 1.0)
            if "south" in on:
                draw_face(canvas, t, base + project(0, 1, 0.95), IX, -IY, 1.0)
            if "west" in on:
                draw_face(canvas, t, base + project(0.05, 1, 1), -IZ, -IY, 1.0)
            if "east" in on:
                draw_face(canvas, t, base + project(0.95, 1, 1), -IZ, -IY, 1.0)
            if "up" in on:
                draw_face(canvas, t, base + project(0, 0.95, 0), IX, IZ, 1.0)
        else:
            if (x, y + 1, z) not in solid:
                draw_face(canvas, faces["top"], base + project(0, 1, 0), IX, IZ, SHADE["top"] * light)
            if (x, y, z + 1) not in solid:
                draw_face(canvas, faces["south"], base + project(0, 1, 1), IX, -IY, SHADE["south"] * light)
            if (x + 1, y, z) not in solid:
                draw_face(canvas, faces["east"], base + project(1, 1, 1), -IZ, -IY, SHADE["east"] * light)
        changed = np.any(canvas != before, axis=-1)
        alpha[changed] = 1.0
    rgba = np.dstack([canvas, alpha])
    return Image.fromarray((np.clip(rgba, 0, 1) * 255).astype(np.uint8), "RGBA")


def ground(radius_x, radius_z, y=-1):
    return {(x, y, z): "dross:silvermoss" for x in range(-radius_x, radius_x + 1) for z in range(-radius_z, radius_z + 1)}


def tree_blocks(tree, dx=0, dz=0):
    return {(b[0] + dx, b[1], b[2] + dz): b[3] for b in tree["blocks"]}


def background(w, h):
    arr = np.zeros((h, w, 4), np.uint8)
    for r in range(h):
        f = r / max(h - 1, 1)
        arr[r, :, :3] = ((BG_TOP * (1 - f) + BG_BOTTOM * f) * 255).astype(np.uint8)
    arr[..., 3] = 255
    return Image.fromarray(arr, "RGBA")


def main():
    if not os.path.exists(TREES):
        raise SystemExit("build/tv1/trees.json not found: run the tests with DROSS_TREE_PREVIEWS=1 first (see the top of this file)")
    with open(TREES, encoding="utf-8") as fh:
        trees = json.load(fh)["trees"]
    by_shape = {}
    for t in trees:
        by_shape.setdefault(t["feature"], []).append(t)
    titles = {"dross:silverwood": "Silverwood (9-13 tall)", "dross:tall_silverwood": "Tall silverwood (14-20 tall)",
              "dross:great_silverwood": "Great silverwood (2x2 trunk, 20-28 tall)"}

    singles = []
    for feature in ("dross:silverwood", "dross:tall_silverwood", "dross:great_silverwood"):
        for t in by_shape.get(feature, [])[:PER_SHAPE]:
            r = 7 if feature != "dross:great_silverwood" else 10
            blocks = ground(r, r)
            blocks.update(tree_blocks(t))
            vines = sum(1 for s in t["blocks"] if s[3].startswith("dross:stormvine"))
            singles.append((titles[feature], f"{t.get('height', '?')} tall, trunk {t['trunk']}, {vines} Stormvine blocks",
                            render(blocks)))

    # a dense stand: two great ones, tall and common ones packed round them about 5-7 blocks apart
    stand = ground(18, 18)
    layout = [("dross:great_silverwood", 0, -8, -7), ("dross:great_silverwood", 1, 7, 6),
              ("dross:tall_silverwood", 0, -12, 5), ("dross:tall_silverwood", 1, 2, -11), ("dross:tall_silverwood", 2, 12, -6),
              ("dross:tall_silverwood", 3, -3, 8), ("dross:tall_silverwood", 4, 14, 12),
              ("dross:silverwood", 0, -2, -1), ("dross:silverwood", 1, -14, -2), ("dross:silverwood", 2, 3, 14),
              ("dross:silverwood", 3, -8, 13), ("dross:silverwood", 4, 13, 1), ("dross:silverwood", 5, -1, -15),
              ("dross:silverwood", 6, 7, -1), ("dross:silverwood", 7, -14, -13), ("dross:silverwood", 8, 9, -14),
              ("dross:silverwood", 9, -6, 3)]
    for feature, i, dx, dz in layout:
        shapes = by_shape.get(feature, [])
        if shapes:
            stand.update(tree_blocks(shapes[i % len(shapes)], dx, dz))
    stand_img = render(stand)

    pad = 18
    cols = PER_SHAPE
    cell_w = max(im.width for _, _, im in singles) + pad if singles else 100
    row_h = {}
    for idx, (_, _, im) in enumerate(singles):
        row_h[idx // cols] = max(row_h.get(idx // cols, 0), im.height)
    top_h = sum(h + 52 for h in row_h.values())
    W = max(cols * cell_w + pad, stand_img.width + 2 * pad)
    H = 50 + top_h + stand_img.height + 70
    out = background(W, H)
    d = ImageDraw.Draw(out)
    d.text((pad, 12), "Silverwood trees, grown by the GameTest (night; Stormvine glows)", font=font(20, True), fill=(225, 232, 245, 255))
    y = 50
    for row in range(len(row_h)):
        for c in range(cols):
            idx = row * cols + c
            if idx >= len(singles):
                continue
            title, sub, im = singles[idx]
            x = pad + c * cell_w
            d.text((x, y), title, font=font(15, True), fill=(210, 220, 238, 255))
            d.text((x, y + 18), sub, font=font(13), fill=(160, 172, 196, 255))
            out.alpha_composite(im, (x, y + 40 + row_h[row] - im.height))
        y += row_h[row] + 52
    d.text((pad, y + 6), "A dense stand of them, a few blocks apart (what the Silverwood Forest's canopy will be like)",
           font=font(15, True), fill=(210, 220, 238, 255))
    out.alpha_composite(stand_img, ((W - stand_img.width) // 2, y + 36))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print(f"preview_trees: {len(trees)} trees read, block-previews/trees.png written ({W}x{H})")


if __name__ == "__main__":
    main()
