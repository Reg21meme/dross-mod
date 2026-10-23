"""
The Moonsilver Spear (the user, October 22, 2026: "make the spear item using moonsilver ingot ... its the same spear as
the hunter's ... make it 3D"): the Huntsman's spear made by a player: a plain pole, a small leather collar and the
spike, here of moonsilver with an icy edge. One set of boxes is its 3D model in the hand, on the ground, thrown
(MoonsilverSpearRenderer, HuntSpearRenderer) and planted upright in the hunter villages (the Planted Moonsilver Spear
block, which stands in for their lightning rods); in the inventory and in item frames it shows a flat icon, like the
trident, drawn the way its recipe lies: two sticks on a diagonal and the moonsilver at the top right.

  python tools/huntsman/spear.py

Writes (src/main/resources/assets/dross/):
  textures/item/moonsilver_spear.png         the 3D model's texture (32 x 32)
  textures/item/moonsilver_spear_icon.png    the inventory icon (16 x 16)
  models/item/moonsilver_spear.json          the item (3D, its icon in the inventory and item frames)
  models/item/moonsilver_spear_in_hand.json  the 3D model with its hold
  models/item/moonsilver_spear_icon.json     the flat icon
  models/block/planted_spear.json            planted upright, 0.7 of its size (1.75 blocks tall)
  models/item/planted_spear.json, blockstates/planted_spear.json
and the preview boss-previews/rimefall/huntsman/moonsilver_spear.png.
"""
import json
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
PREVIEW = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "moonsilver_spear.png")

# ---------------------------------------------------------------- the shape (pixels; the spear along +y, its
# middle at x = z = 8; the butt at BUTT, the tip at BUTT + LENGTH)
LENGTH = 40.0                 # 2.5 blocks (the Huntsman's own is about 4.6, for a man 7.7 blocks tall)
BUTT = -12.0
POLE = 1.5                    # the pole's thickness
POLE_SPLITS = (10.0, 20.0)    # (it's three boxes, so its grain isn't stretched)
COLLAR = (29.5, 31.0, 2.5)    # from, to (along it from the butt), thickness
SPIKE = ((31.0, 33.5, 3.0), (33.5, 36.0, 2.2), (36.0, 38.2, 1.4), (38.2, 40.0, 0.7))   # tapering to its point
PLANTED_SCALE = 0.7           # planted: 1.75 blocks tall, standing on the block below (it can stand in a room two
                              # blocks high)

# ---------------------------------------------------------------- the colours
WOOD = ((74, 54, 38), (96, 70, 48), (118, 88, 60), (60, 43, 30))          # a dark spruce pole
LEATHER = ((92, 60, 38), (120, 82, 52), (70, 45, 28))
METAL = ((53, 61, 82), (91, 102, 131), (134, 146, 173), (174, 185, 207), (210, 218, 234), (242, 245, 252))
ICE = (168, 194, 255)         # the icy edge (the moonsilver's own blue glint)

TEX = 32                      # the 3D model's texture size (UVs run 0-16 whatever its size: 2 texels a unit)


def texture():
    im = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
    px = im.load()
    # x 0-3: the pole's sides, a vertical grain; x 4-7: its ends (end grain)
    for y in range(TEX):
        for x in range(4):
            c = WOOD[1]
            if x == 0:
                c = WOOD[3]
            elif x == 3:
                c = WOOD[2]
            if (y * 5 + x * 3) % 11 == 0:
                c = WOOD[0]
            px[x, y] = c + (255,)
    for y in range(4):
        for x in range(4, 8):
            px[x, y] = (WOOD[2] if (x + y) % 3 else WOOD[0]) + (255,)
    # x 8-13, y 0-5: the leather collar, a stitched band
    for y in range(6):
        for x in range(8, 14):
            c = LEATHER[1] if y in (2, 3) else LEATHER[0]
            if y in (0, 5):
                c = LEATHER[2]
            if y == 3 and x % 2 == 0:
                c = LEATHER[2]
            px[x, y] = c + (255,)
    # x 16-31: the spike's metal, dark at its base (y 31) to bright at its point (y 0), an icy edge down its right
    for y in range(TEX):
        t = 1.0 - y / (TEX - 1)
        for x in range(16, 32):
            k = min(len(METAL) - 1, int(t * (len(METAL) - 1) + 0.5 + (0.6 if x in (17, 18) else 0.0)))
            c = METAL[max(1, k)]
            if x >= 29:
                c = ICE if (y + x) % 3 else METAL[-1]
            if x == 16:
                c = METAL[0]
            px[x, y] = c + (255,)
    return im


def icon():
    """The inventory icon: the pole from the bottom left to the top right, the moonsilver head at the top right."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    # the pole: two pixels thick along the diagonal, from (1, 14) up to (10, 5)
    for i in range(10):
        x, y = 1 + i, 14 - i
        px[x, y] = WOOD[1] + (255,)
        if y + 1 < 16:
            px[x, y + 1] = WOOD[3] + (255,)
    px[1, 15] = WOOD[3] + (255,)
    # the collar
    for x, y in ((10, 6), (11, 5), (10, 5), (11, 6)):
        px[x, y] = LEATHER[1] + (255,)
    px[10, 6] = LEATHER[2] + (255,)
    # the spike: a leaf of moonsilver from (12, 4) to its point at (15, 0), an icy edge on its top side
    leaf = {(12, 4): 2, (12, 3): 3, (13, 4): 1, (13, 3): 3, (13, 2): 4, (14, 3): 2, (14, 2): 4, (14, 1): 5,
            (15, 1): 3, (15, 0): 5, (12, 5): 1, (13, 1): 5}
    for (x, y), k in leaf.items():
        px[x, y] = METAL[k] + (255,)
    for x, y in ((12, 2), (14, 0)):
        px[x, y] = ICE + (255,)
    # a dark outline round the head
    for x, y in ((11, 4), (12, 6), (13, 5), (14, 4), (15, 2)):
        if px[x, y][3] == 0:
            px[x, y] = METAL[0] + (255,)
    return im


def _box(lo, hi, w, uv_side, uv_end, rotation=None):
    """A box along the spear from lo to hi (y), w thick, its sides and ends mapped to uv_side / uv_end."""
    h = w / 2.0
    faces = {f: {"uv": uv_side, "texture": "#spear"} for f in ("north", "south", "east", "west")}
    faces["up"] = {"uv": uv_end, "texture": "#spear"}
    faces["down"] = {"uv": uv_end, "texture": "#spear"}
    e = {"from": [8 - h, lo, 8 - h], "to": [8 + h, hi, 8 + h], "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def elements(scale=1.0, base=BUTT):
    """Every box of the spear, its butt at y = base, made `scale` times its size (round its middle line)."""
    def y(v):
        return round(base + v * scale, 4)

    out = []
    stops = (0.0,) + POLE_SPLITS + (COLLAR[0],)
    for a, b in zip(stops, stops[1:]):
        out.append(_box(y(a), y(b), POLE * scale, [0, 0, 1.5, min(16.0, b - a)], [2, 0, 4, 2]))
    out.append(_box(y(COLLAR[0]), y(COLLAR[1]), COLLAR[2] * scale, [4, 0, 7, 3], [4, 0, 7, 3]))
    for i, (a, b, w) in enumerate(SPIKE):
        # (the metal's gradient runs over the spike alone: dark where it leaves the collar, bright at its point)
        s0, s1 = SPIKE[0][0], SPIKE[-1][1]
        v0 = 16.0 * (1.0 - (b - s0) / (s1 - s0))
        v1 = 16.0 * (1.0 - (a - s0) / (s1 - s0))
        out.append(_box(y(a), y(b), w * scale, [8, round(v0, 3), 16, round(v1, 3)], [8, 15, 10, 16]))
    for e in out:
        for k in ("from", "to"):
            e[k] = [round(v, 4) for v in e[k]]
    return out


# How it's held: Minecraft's own hold for a sword (models/item/handheld.json), but the model stands upright where a
# sword's picture lies on the diagonal, so each z turn is 45 degrees less (the rotation is applied z first: see
# ItemTransform), which lays it along the same diagonal. On the ground the same.
HANDHELD = {
    "thirdperson_righthand": ([0, -90, 55], [0, 4.0, 0.5], [0.85, 0.85, 0.85]),
    "thirdperson_lefthand": ([0, 90, -55], [0, 4.0, 0.5], [0.85, 0.85, 0.85]),
    "firstperson_righthand": ([0, -90, 25], [1.13, 3.2, 1.13], [0.68, 0.68, 0.68]),
    "firstperson_lefthand": ([0, 90, -25], [1.13, 3.2, 1.13], [0.68, 0.68, 0.68]),
    "ground": ([0, 0, 0], [0, 2, 0], [0.5, 0.5, 0.5]),
    "head": ([0, 180, 0], [0, 13, 7], [1, 1, 1]),
}
DIAGONAL = -45.0


def display():
    out = {}
    for k, (rot, tr, sc) in HANDHELD.items():
        rz = rot[2] + (DIAGONAL if k != "head" else 0.0)
        out[k] = {"rotation": [rot[0], rot[1], rz], "translation": tr, "scale": sc}
    return out


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def preview(tex, ico):
    """The spear from the side (its boxes coloured from the texture), its icon and its texture, 8x."""
    S = 8
    W, H = 64 * S // 4 + 260, 46 * S
    im = Image.new("RGBA", (W, H), (34, 40, 50, 255))
    d = ImageDraw.Draw(im)
    big = tex.resize((TEX * 4, TEX * 4), Image.NEAREST)
    for e in elements():
        (x0, y0, _z0), (x1, y1, _z1) = e["from"], e["to"]
        u = e["faces"]["north"]["uv"]
        sub = tex.crop((int(u[0] * 2), int(u[1] * 2), max(int(u[0] * 2) + 1, int(u[2] * 2)),
                        max(int(u[1] * 2) + 1, int(u[3] * 2))))
        X0, X1 = 40 + (x0 - 4) * S, 40 + (x1 - 4) * S
        Y0, Y1 = H - 10 - (y1 - BUTT) * S, H - 10 - (y0 - BUTT) * S
        im.alpha_composite(sub.resize((max(1, int(X1 - X0)), max(1, int(Y1 - Y0))), Image.NEAREST),
                           (int(X0), int(Y0)))
    im.alpha_composite(ico.resize((128, 128), Image.NEAREST), (150, 20))
    im.alpha_composite(big, (150, 170))
    d.text((150, 4), "icon", fill=(220, 230, 240, 255))
    d.text((150, 156), "texture", fill=(220, 230, 240, 255))
    d.text((8, 4), "side, 1 px = 8", fill=(220, 230, 240, 255))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    im.save(PREVIEW)


def main():
    tex, ico = texture(), icon()
    tex.save(os.path.join(ASSETS, "textures", "item", "moonsilver_spear.png"))
    ico.save(os.path.join(ASSETS, "textures", "item", "moonsilver_spear_icon.png"))
    write_json("models/item/moonsilver_spear_in_hand.json", {
        "textures": {"spear": "dross:item/moonsilver_spear", "particle": "dross:item/moonsilver_spear"},
        "gui_light": "front",
        "elements": elements(),
        "display": display(),
    })
    write_json("models/item/moonsilver_spear_icon.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "dross:item/moonsilver_spear_icon"},
    })
    write_json("models/item/moonsilver_spear.json", {
        "loader": "forge:separate_transforms",
        "gui_light": "front",
        "base": {"parent": "dross:item/moonsilver_spear_in_hand"},
        "perspectives": {
            "gui": {"parent": "dross:item/moonsilver_spear_icon"},
            "fixed": {"parent": "dross:item/moonsilver_spear_icon"},
        },
    })
    write_json("models/block/planted_spear.json", {
        "ambientocclusion": False,
        "textures": {"spear": "dross:item/moonsilver_spear", "particle": "dross:item/moonsilver_spear"},
        "elements": elements(PLANTED_SCALE, base=0.0),
    })
    write_json("models/item/planted_spear.json", {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "dross:item/moonsilver_spear_icon"},
    })
    write_json("blockstates/planted_spear.json", {"variants": {"": {"model": "dross:block/planted_spear"}}})
    preview(tex, ico)
    print("wrote the Moonsilver Spear's textures, models and", os.path.relpath(PREVIEW, ROOT))


if __name__ == "__main__":
    main()
