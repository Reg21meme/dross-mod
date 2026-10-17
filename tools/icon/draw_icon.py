"""
Draws the Dross project icon (for CurseForge and the like): curseforge/dross_icon.png, 512 x 512, and the same picture
as the mod's logo in the game's Mods list (src/main/resources/dross_logo.png, mods.toml's logoFile).

The Rat King's face, turned to one side, filling the picture: his real model and texture (his head and everything on
it: ears, crown, whiskers), drawn by the Rat King's generator (tools/ratking), on a plain night background with a soft
electric blue glow behind him. The colours come from the shared palette in DrossColors.java.

    python tools/icon/draw_icon.py

Needs numpy, scipy and Pillow (like tools/ratking).
"""
import math
import re
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ folders in the project

from PIL import Image, ImageFilter  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "curseforge" / "dross_icon.png"
LOGO = ROOT / "src/main/resources/dross_logo.png"

SIZE = 512
# His head: seen from this angle (degrees round: + turns his snout to the picture's left, - to its right; degrees down), and as big as
# fits in this share of the picture.
HEAD_YAW = -40.0
HEAD_ELEVATION = 8.0
HEAD_FILL = 0.9
# His look: 0 his normal eyes, 1 the Rage's glowing red eyes.
HEAD_RAGE = 0.0
# A soft light round his outline (blur radius, strength 0-1), so his dark fur stands out from the background.
HEAD_GLOW = (14, 0.6)


def palette():
    """The shared palette from DrossColors.java (main, deep cobalt, highlight)."""
    text = (ROOT / "src/main/java/com/reg21meme/dross/DrossColors.java").read_text(encoding="utf-8")

    def colour(name):
        value = int(re.search(name + r"\s*=\s*0x([0-9A-Fa-f]{6})", text).group(1), 16)
        return (value >> 16 & 255, value >> 8 & 255, value & 255)

    return colour("PALETTE_MAIN"), colour("PALETTE_DEEP_COBALT"), colour("PALETTE_HIGHLIGHT")


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def background(main, deep):
    """Night: near-black navy at the edges, a deep electric blue glow in the middle (behind his head)."""
    night = (5, 7, 20)
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    c = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            t = max(0.0, 1.0 - math.hypot(x - c, y - c) / (SIZE * 0.7)) ** 1.8
            pixels[x, y] = mix(night, mix(deep, main, 0.35), t) + (255,)
    return image


def rat_king_head(scale):
    """The Rat King's head (his head bone and everything on it) at this many pixels a texture pixel, cut out."""
    sys.path.insert(0, str(ROOT / "tools" / "ratking"))
    import build as rat_king  # the Rat King's generator (it puts the golem kit on the path)
    from ratkit import views

    design = rat_king.kings()[0]
    model, _bones, _body = rat_king.build_king_model(design, verbose=False)
    textures = rat_king.paint_king(design, model)

    def on_head(bone):
        while bone is not None:
            if bone.name == "head":
                return True
            bone = bone.parent
        return False

    key = (255, 0, 255)
    drawn = views.draw(model, textures, yaw=HEAD_YAW, elevation=HEAD_ELEVATION, scale=scale, visible=on_head,
                       grid=False, figure=False, background=key, rage_strength=HEAD_RAGE).convert("RGBA")
    pixels = drawn.load()
    for y in range(drawn.height):
        for x in range(drawn.width):
            if pixels[x, y][:3] == key:
                pixels[x, y] = (0, 0, 0, 0)
    return drawn.crop(drawn.getbbox())


def glow(shape, colour, radius, strength):
    """A soft glow of the given colour round the shape's opaque pixels."""
    alpha = shape.split()[3].filter(ImageFilter.GaussianBlur(radius))
    alpha = alpha.point(lambda a: int(min(255, a * strength * 1.6)))
    layer = Image.new("RGBA", shape.size, colour + (0,))
    layer.putalpha(alpha)
    return layer


def main_():
    main, deep, highlight = palette()
    image = background(main, deep)

    # drawn once small to measure him, then again at the biggest whole scale that fits (crisp texture pixels)
    probe_scale = 8
    probe = rat_king_head(probe_scale)
    scale = max(1, int(probe_scale * SIZE * HEAD_FILL / max(probe.size)))
    face = rat_king_head(scale)
    head = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    head.alpha_composite(face, ((SIZE - face.width) // 2, (SIZE - face.height) // 2))
    image.alpha_composite(glow(head, highlight, *HEAD_GLOW))
    image.alpha_composite(head)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(OUT)
    image.convert("RGB").save(LOGO)
    print(f"draw_icon: {OUT} and {LOGO} ({SIZE} x {SIZE}, his head at {scale} pixels a texture pixel)")


if __name__ == "__main__":
    main_()
