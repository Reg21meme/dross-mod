"""
Draws the biome pictures for the mod's CurseForge page (into curseforge/), one per Dross biome: an in-game screenshot
of the biome, its creatures only far off and few (the user, November 2: "natural, don't clump them together ... maybe
you see a group of Rimehorns or a moose in the background, but you don't have to see everything"; taken by a temporary
photo booth, tools/photo_booth/, from over the land looking across it), cropped to the boss cards'
size, darkened towards its foot, with the biome's name in the page's blocky letters in its own colour and a line about
it under the name.

    biome_silverwood_forest.png, biome_cinder_wastes.png, biome_blightwood.png, biome_rimefall.png, biome_gravecrags.png

    python tools/icon/draw_biome_cards.py <screenshots folder>

The screenshots are named tour2_<biome>_<shot>.png (BIOMES picks which one each card uses). The letters and the outline
come from draw_page_art.py. Needs Pillow and numpy.
"""
import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ folders in the project

from PIL import Image, ImageDraw, ImageEnhance  # noqa: E402

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("draw_page_art", HERE / "draw_page_art.py")
art = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(art)
art.GLYPHS.setdefault("F", ["#####", "#....", "#....", "####.", "#....", "#....", "#...."])
art.GLYPHS.setdefault("Z", ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"])
art.GLYPHS.setdefault("X", ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"])
art.GLYPHS.setdefault(",", ["..", "..", "..", "..", "..", ".#", "#."])
art.GLYPHS.setdefault("'", ["#", "#", ".", ".", ".", ".", "."])

# Each biome: its card's title, the screenshot it uses, the line under the title, the title's colour, and where the
# card is cut from the screenshot top to bottom (0 its top, 0.5 its middle, 1 its foot).
BIOMES = [
    ("silverwood_forest", "SILVERWOOD FOREST", "0_090", "DARK SILVERWOOD TREES HUNG WITH GLOWING STORMVINE",
     (127, 168, 255), 0.5),
    ("cinder_wastes", "CINDER WASTES", "0_180", "ASH, BASALT AND LAVA, WHERE THE PALLBEARER WALKS",
     (255, 140, 40), 0.5),
    ("blightwood", "BLIGHTWOOD", "0h_135", "A ROTTING FOREST OVER THE RAT KING'S SEWERS",
     (150, 220, 70), 0.5),
    ("rimefall", "RIMEFALL", "0_180", "FROZEN LAKES, RIMEHORN HERDS AND THE FROSTFOLK",
     (190, 230, 255), 0.5),
    ("gravecrags", "THE GRAVECRAGS", "0h_090", "SNOWY PEAKS HIGH OVER THE DROSS",
     (205, 205, 215), 1.0),
]
TITLE_PIXEL = 11          # one pixel of the title's letters, in image pixels
LINE_PIXEL = 4            # ...of the line under it
MARGIN = 54               # from the card's left and bottom edges
SHADE_FROM = 0.45         # the card darkens from this share of its height down...
SHADE = 0.78              # ...to this much dark at its foot
BRIGHTNESS = 1.18         # the screenshot a little brighter (the Dross is always night)


def crop_to_card(shot, anchor=0.5):
    """The screenshot cut to the cards' shape (across its middle; top to bottom at `anchor`) and scaled to their
    size."""
    w, h = shot.size
    want = art.CARD_W / art.CARD_H
    if w / h > want:
        cw = int(h * want)
        box = ((w - cw) // 2, 0, (w - cw) // 2 + cw, h)
    else:
        ch = int(w / want)
        top = int((h - ch) * anchor)
        box = (0, top, w, top + ch)
    return shot.crop(box).resize((art.CARD_W, art.CARD_H), Image.LANCZOS)


def shade(card):
    """Darkens the card towards its foot (where the title goes) and a little at its sides."""
    layer = Image.new("RGBA", card.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    top = int(card.height * SHADE_FROM)
    for y in range(top, card.height):
        a = int(255 * SHADE * ((y - top) / (card.height - top)) ** 1.4)
        draw.line([(0, y), (card.width, y)], fill=art.NIGHT + (a,))
    card.alpha_composite(layer)
    return card


def biome_card(shot, title, line, colour, anchor=0.5):
    card = ImageEnhance.Brightness(crop_to_card(shot.convert("RGB"), anchor)).enhance(BRIGHTNESS).convert("RGBA")
    card = shade(card)
    sub = art.label(line, LINE_PIXEL, (225, 230, 240))
    name = art.label(title, TITLE_PIXEL, colour)
    card.alpha_composite(sub, (MARGIN - LINE_PIXEL - 4, card.height - MARGIN - sub.height + LINE_PIXEL + 4))
    card.alpha_composite(name, (MARGIN - TITLE_PIXEL - 4, card.height - MARGIN - sub.height - name.height + 18))
    return card


def main(argv):
    if len(argv) < 2:
        raise SystemExit("usage: python tools/icon/draw_biome_cards.py <screenshots folder>")
    shots = Path(argv[1])
    for biome, title, which, line, colour, anchor in BIOMES:
        path = shots / f"tour2_{biome}_{which}.png"
        card = biome_card(Image.open(path), title, line, colour, anchor)
        out = art.OUT / f"biome_{biome}.png"
        card.convert("RGB").save(out)
        print(f"draw_biome_cards: {out}")


if __name__ == "__main__":
    main(sys.argv)
