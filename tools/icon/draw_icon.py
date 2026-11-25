"""
Draws the Dross project icon (for CurseForge and the like): curseforge/dross_icon.png, 512 x 512, and the same picture
as the mod's logo in the game's Mods list (src/main/resources/dross_logo.png, mods.toml's logoFile).

A boss's head, filling the picture: its real model and texture, drawn and framed by draw_page_art.py's boss classes
(and their generators), on a plain night background with a soft electric blue glow behind it. Since November 6, 2026 the
Emberdactyl, its head exactly as in its box on the banner (the user: "literally copy the same head from the Dross
banner"): the same angle and framing, its neck running down into its body; from October 24, 2026 the Huntsman (his wolf
hood and antlers), until then the Rat King. The colours come from the shared palette in DrossColors.java.

    python tools/icon/draw_icon.py
    python tools/icon/draw_icon.py --giant-options   the Silverwood Giant's head in its three looks, as icon options to
                                                     pick from (boss-previews/silverwood/mobs/forest_giant/icon/); the
                                                     icon and the logo are left as they are

The Silverwood Giant (November 25, 2026, the user: "a new Dross icon ... of the Silverwood Giants, 3 variations of just
the heads, don't change the banner"): its head, jaw and eyes only, from its generator's own model and textures
(tools/forest_giant/build.py), drawn the same way and on the same background; the banner (draw_page_art.py's BOSSES)
doesn't show it.

Needs numpy, scipy and Pillow (like draw_page_art.py).
"""
import copy
import importlib.util
import math
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ folders in the project
sys.path.insert(0, str(Path(__file__).resolve().parent))

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

import draw_page_art as art  # noqa: E402

OUT = art.OUT / "dross_icon.png"
LOGO = art.ROOT / "src/main/resources/dross_logo.png"

SIZE = 512
# Whose head (one of draw_page_art's boss classes).
ICON_BOSS = art.Emberdactyl
# Its head: seen from this angle ((degrees round: + turns its face to the picture's left, - to its right; degrees down),
# or None for the boss's own angle in its box on the banner), and as big as fits in this share of the picture (the
# banner's boxes' share, so it's framed exactly as there).
HEAD_ANGLE = None
HEAD_FILL = art.HEAD_FILL
# A soft light round its outline (blur radius, strength 0-1), so its dark obsidian stands out from the background.
HEAD_GLOW = (14, 0.6)


class SilverwoodGiant(art.Boss):
    """One look of the Silverwood Giant (tools/forest_giant: G1 hurler, G2 brawler, G3 treebearer), only its head."""
    glow = "SILVERWOOD_GIANT_EGG_SPOTS"
    biome = "silverwood_forest"
    LOOKS = {"hurler": "G1 the Boulder-Hurler", "brawler": "G2 the Brawler", "treebearer": "G3 the Tree-Bearer"}
    HEAD_TURN = (-32.0, -4.0)  # seen a little from its right and from a little below (degrees round, down): looking
                               # up at a giant
    BRIGHT = 1.3               # its lit (not glowing) colours times this (its grey skin on the night background)
    # Framed on its face, like the Emberdactyl's skull: the boxes within SKULL_REACH units of its head's middle (its
    # face, brow and crown) fill SKULL_FILL of what a head may fill, SKULL_Y down the picture; its beard and hair reach
    # off the edges (fitted whole, a long beard left its face small)
    SKULL_REACH = 7.0
    SKULL_FILL = 0.7
    SKULL_Y = 0.45

    def __init__(self, look):
        super().__init__()
        self.look = look
        self.name = f"SILVERWOOD GIANT {look.upper()}"

    def build(self):
        spec = importlib.util.spec_from_file_location("giant_build", art.ROOT / "tools" / "forest_giant" / "build.py")
        giants = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(giants)  # (it puts the golem, Rimefall and fire kits on the path)
        from rimekit import view
        self.view = view
        design = next(d for d in giants.designs() if d.ID == self.look)
        built = giants.build_model(design, verbose=False)
        self.tex = built.tex
        self.head = art.keeping(built.m, art.of("head"))   # (its head, jaw and eyes)
        middle = np.array(built.J["head_c"], dtype=float)
        self.skull = copy.deepcopy(self.head)
        for bone in self.skull.bones:
            bone.cubes = [c for c in bone.cubes
                          if np.abs(np.array(c.origin) + np.array(c.size) / 2.0 - middle).max() <= self.SKULL_REACH]

    def _draw(self, model, scale, yaw, elevation, bounds):
        a = np.array(self.view.draw(model, self.tex, yaw=yaw, elevation=elevation, scale=scale, bounds=bounds,
                                    background=art.KEY, grid=False, figure=False,
                                    world_light=self.BRIGHT).convert("RGBA"))
        a[(a[..., 0] == art.KEY[0]) & (a[..., 1] == art.KEY[1]) & (a[..., 2] == art.KEY[2])] = 0
        return Image.fromarray(a, "RGBA")

    def render(self, kind, scale, angle=None):
        # (its face alone is drawn in the same window as its whole head and cropped the same way, so the two line up)
        yaw, elevation = angle or self.HEAD_TURN
        bounds = self.view.window(self.head, yaw, elevation, figure=False, ground=False)
        whole = self._draw(self.head, scale, yaw, elevation, bounds)
        part = whole if kind == "head" else self._draw(self.skull, scale, yaw, elevation, bounds)
        return part.crop(whole.getbbox())

    def framed_head(self, size, fill, angle=None):
        """Its face as big as fits in SKULL_FILL of fill x fill, its beard and hair reaching off the frame."""
        probe = 4
        x0, y0, x1, y1 = self.picture("skull", probe, angle).getbbox()
        scale = max(1, int(probe * fill * self.SKULL_FILL / max(x1 - x0, y1 - y0)))
        head = self.picture("head", scale, angle)
        x0, y0, x1, y1 = self.picture("skull", scale, angle).getbbox()
        left, top = round((x0 + x1 - size) / 2), round((y0 + y1) / 2 - size * self.SKULL_Y)
        return head.crop((left, top, left + size, top + size))


GIANT_OPTIONS = art.ROOT / "boss-previews" / "silverwood" / "mobs" / "forest_giant" / "icon"


def background():
    """Night: near-black navy at the edges, a deep electric blue glow in the middle (behind its head)."""
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    c = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            t = max(0.0, 1.0 - math.hypot(x - c, y - c) / (SIZE * 0.7)) ** 1.8
            pixels[x, y] = art.mix(art.NIGHT, art.mix(art.DEEP, art.MAIN, 0.35), t) + (255,)
    return image


def icon(boss, angle=None):
    """The icon with that boss's head: the night background, its head with a soft blue light round it."""
    image = background()
    # (at the biggest whole scale that fits: crisp texture pixels)
    head = boss.framed_head(SIZE, int(SIZE * HEAD_FILL), angle)
    image.alpha_composite(art.glow(head, art.HIGHLIGHT, *HEAD_GLOW))
    image.alpha_composite(head)
    return image


def giant_options():
    """The Silverwood Giant's three looks' heads as icons, each on its own and side by side on a sheet (big, and as
    small as CurseForge and the Mods list show it)."""
    GIANT_OPTIONS.mkdir(parents=True, exist_ok=True)
    icons = []
    for n, look in enumerate(SilverwoodGiant.LOOKS, 1):
        image = icon(SilverwoodGiant(look)).convert("RGB")
        image.save(GIANT_OPTIONS / f"option_{n}_{look}.png")
        icons.append((n, look, image))
    gap, small = 24, (64, 128)
    width = len(icons) * SIZE + (len(icons) + 1) * gap
    sheet = Image.new("RGB", (width, SIZE + 2 * gap + 60 + max(small) + gap), art.NIGHT)
    try:
        font = ImageFont.truetype("arial.ttf", 28)
    except OSError:
        font = ImageFont.load_default()
    for i, (n, look, image) in enumerate(icons):
        x = gap + i * (SIZE + gap)
        sheet.paste(image, (x, gap + 40))
        ImageDraw.Draw(sheet).text((x, gap), f"Option {n}: {SilverwoodGiant.LOOKS[look]}", fill=art.WHITE, font=font)
        sx = x
        for px in small:
            sheet.paste(image.resize((px, px), Image.LANCZOS), (sx, gap + 40 + SIZE + gap + max(small) - px))
            sx += px + gap
    sheet.save(GIANT_OPTIONS / "options.png")
    print(f"draw_icon: {len(icons)} Silverwood Giant icon options in {GIANT_OPTIONS}")


def main_():
    if "--giant-options" in sys.argv[1:]:
        giant_options()
        return
    boss = ICON_BOSS()
    image = icon(boss, HEAD_ANGLE)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.convert("RGB").save(OUT)
    image.convert("RGB").save(LOGO)
    print(f"draw_icon: {OUT} and {LOGO} ({SIZE} x {SIZE}, the {boss.name.title()}'s head)")


if __name__ == "__main__":
    main_()
