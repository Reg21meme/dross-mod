"""
Draws the Dross project icon (for CurseForge and the like): curseforge/dross_icon.png, 512 x 512, and the same picture
as the mod's logo in the game's Mods list (src/main/resources/dross_logo.png, mods.toml's logoFile).

A boss's face, turned to one side, filling the picture: his real model and texture (his head and everything on it), drawn
and framed by draw_page_art.py's boss classes (and their generators), on a plain night background with a soft electric
blue glow behind him. Since October 24, 2026 the Huntsman (the user's pick: his wolf hood and face as big as the Rat
King's head was, his antlers running off the top), until then the Rat King. The colours come from the shared palette in
DrossColors.java.

    python tools/icon/draw_icon.py

Needs numpy, scipy and Pillow (like draw_page_art.py).
"""
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ folders in the project
sys.path.insert(0, str(Path(__file__).resolve().parent))

from PIL import Image  # noqa: E402

import draw_page_art as art  # noqa: E402

OUT = art.OUT / "dross_icon.png"
LOGO = art.ROOT / "src/main/resources/dross_logo.png"

SIZE = 512
# Whose face (one of draw_page_art's boss classes).
ICON_BOSS = art.Huntsman
# His head: seen from this angle (degrees round: + turns his face to the picture's left, - to its right; degrees down),
# the Rat King's (the user's pick), and as big as fits in this share of the picture.
HEAD_YAW = -40.0
HEAD_ELEVATION = 8.0
HEAD_FILL = 0.9
# A soft light round his outline (blur radius, strength 0-1), so his dark fur stands out from the background.
HEAD_GLOW = (14, 0.6)


def background():
    """Night: near-black navy at the edges, a deep electric blue glow in the middle (behind his head)."""
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    c = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            t = max(0.0, 1.0 - math.hypot(x - c, y - c) / (SIZE * 0.7)) ** 1.8
            pixels[x, y] = art.mix(art.NIGHT, art.mix(art.DEEP, art.MAIN, 0.35), t) + (255,)
    return image


def main_():
    image = background()
    boss = ICON_BOSS()
    # (at the biggest whole scale that fits: crisp texture pixels)
    head = boss.framed_head(SIZE, int(SIZE * HEAD_FILL), (HEAD_YAW, HEAD_ELEVATION))
    image.alpha_composite(art.glow(head, art.HIGHLIGHT, *HEAD_GLOW))
    image.alpha_composite(head)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(OUT)
    image.convert("RGB").save(LOGO)
    print(f"draw_icon: {OUT} and {LOGO} ({SIZE} x {SIZE}, the {boss.name.title()}'s head)")


if __name__ == "__main__":
    main_()
