"""
The Huntsman's ice arrows, the ones he looses in his fight (the user, November 19, 2026: "make the tip of the hunter's
arrows ... the icy blue used by other stuff in the mod we made, that nice clear ice; can you use that same thing for the
tip of the arrow"): a dark spruce shaft, a head of the models' see-through ice (rimekit's ICE palette, the colours of the
Rimehorn's horns, the ice spikes and his own arrows' heads in his model) and ice-blue fletchings, like the arrows his
model holds and carries in its quiver. Drawn pixel by pixel on the arrow texture's layout (32 x 32: the side view in the
top 16 x 5, the end seen from behind under it), so `IceArrowRenderer` draws it like any arrow, only see-through, with a
faint glow on the head.

  python tools/huntsman/ice_arrow.py

Writes src/main/resources/assets/dross/textures/entity/huntsman/ice_arrow.png and ice_arrow_glow.png, and the preview
boss-previews/rimefall/huntsman/ice_arrow.png.
"""
import os
import sys

from PIL import Image

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))

from rimekit import mats as M  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "entity", "huntsman")
PREVIEW = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "ice_arrow.png")

ICE = [tuple(int(c) for c in M.ICE[i]) for i in range(len(M.ICE))]     # dark to light, as the models' ice
GLOW = [tuple(int(c) for c in M.GLOW[i]) for i in range(len(M.GLOW))]
SHAFT = [(0x4a, 0x33, 0x22), (0x5c, 0x41, 0x2b), (0x70, 0x52, 0x37)]  # dark spruce, as the model's shaft
NOCK = (0x2a, 0x1f, 0x18)

HEAD_ALPHA = 205             # the head's ice: see-through like the models' clear ice (180-235 there)
EDGE_ALPHA = 235             # its rim a little thicker
FLETCH_ALPHA = 230           # the fletchings' ice, a little see-through too

# The side view, 16 x 5 (the arrow points right, +x, as the game draws it). Letters:
#   h head (ice), d its darker underside, H head's bright edge, t its point, s shaft, n nock, f fletching, F fletching's light edge
SIDE = [
    "FF.........H....",
    ".ff.......hhHH..",
    "nssssssssshhhhHt",
    ".ff.......dddh..",
    "FF.........d....",
]
# The end seen from behind, 5 x 5 (under the side view, rows 5-9): the four fletchings round the nock.
BACK = [
    "..F..",
    "..f..",
    "FfnfF",
    "..f..",
    "..F..",
]


def colour(ch, x, y):
    if ch == "s":
        return SHAFT[(x + y) % 3 if x % 4 else 0] + (255,)
    if ch == "n":
        return NOCK + (255,)
    if ch == "f":
        return ICE[2] + (FLETCH_ALPHA,)
    if ch == "F":
        return ICE[3] + (FLETCH_ALPHA,)
    if ch == "h":
        return (ICE[1] if y > 2 else ICE[2] if y == 2 else ICE[3]) + (HEAD_ALPHA,)
    if ch == "d":
        return ICE[0] + (EDGE_ALPHA,)
    if ch == "H":
        return ICE[4] + (EDGE_ALPHA,)
    if ch == "t":
        return ICE[5] + (EDGE_ALPHA,)
    return None


def glow(ch):
    """Its glow pass (drawn added on, so a darker colour is a fainter glow): the head, and a little on the fletchings."""
    k = {"h": 0.45, "H": 0.7, "d": 0.3, "t": 0.85, "f": 0.18, "F": 0.25}.get(ch)
    if k is None:
        return None
    g = GLOW[3]
    return tuple(int(c * k) for c in g) + (255,)


def draw():
    tex = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    lit = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for rows, top in ((SIDE, 0), (BACK, 5)):
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                c = colour(ch, x, y)
                if c:
                    tex.putpixel((x, top + y), c)
                g = glow(ch)
                if g:
                    lit.putpixel((x, top + y), g)
    return tex, lit


def main():
    tex, lit = draw()
    os.makedirs(OUT, exist_ok=True)
    tex.save(os.path.join(OUT, "ice_arrow.png"))
    lit.save(os.path.join(OUT, "ice_arrow_glow.png"))
    # the preview: the side view big, on the night snow and on a light background
    side = tex.crop((0, 0, 16, 10))
    big = side.resize((16 * 24, 10 * 24), Image.NEAREST)
    sheet = Image.new("RGBA", (big.width * 2 + 48, big.height + 32), (24, 30, 44, 255))
    sheet.alpha_composite(big, (16, 16))
    light = Image.new("RGBA", big.size, (226, 236, 244, 255))
    light.alpha_composite(big)
    sheet.alpha_composite(light, (big.width + 32, 16))
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    sheet.convert("RGB").save(PREVIEW)
    print("wrote", os.path.join(OUT, "ice_arrow.png"), "and its glow;", PREVIEW)


if __name__ == "__main__":
    main()
