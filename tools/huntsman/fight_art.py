"""Draws the little pictures the Huntsman's fight needs (CLAUDE.md step 14):

  item/bola.png         his bola as it flies: three weights of ice on a cord knotted in the middle
  mob_effect/snared.png the Snared effect's icon: a loop of cord round a weight of ice

Original pixel art, from fixed numbers so it comes out the same every time.

    python tools/huntsman/fight_art.py
"""
import math
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.normpath(os.path.join(HERE, "..", ".."))
TEXTURES = os.path.join(PROJECT, "src", "main", "resources", "assets", "dross", "textures")

ICE = [(70, 120, 176), (112, 166, 218), (168, 210, 240), (232, 246, 252)]
CORD = [(70, 50, 34), (108, 80, 54), (146, 112, 78)]


def weight(img, cx, cy, r):
    """A round weight of ice: dark rim, lit top-left, a glint."""
    for x in range(img.width):
        for y in range(img.height):
            d = math.hypot(x - cx, y - cy)
            if d <= r:
                light = (cx - x) + (cy - y)
                shade = 0 if d > r - 0.8 else (2 if light > 0.6 else 1)
                img.putpixel((x, y), ICE[shade] + (255,))
    img.putpixel((int(round(cx - r * 0.4)), int(round(cy - r * 0.4))), ICE[3] + (255,))


def cord(img, a, b):
    """A cord from a to b, two shades twisted."""
    (x0, y0), (x1, y1) = a, b
    n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
    for i in range(n + 1):
        t = i / n
        x = int(round(x0 + (x1 - x0) * t))
        y = int(round(y0 + (y1 - y0) * t))
        if 0 <= x < img.width and 0 <= y < img.height:
            img.putpixel((x, y), CORD[1 + (i % 2)] + (255,))


def bola():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    knot = (8, 7)
    ends = [(3, 3), (13, 4), (7, 13)]
    for e in ends:
        cord(img, knot, e)
    for x, y in ((7, 6), (8, 6), (7, 7), (9, 7), (8, 8)):
        img.putpixel((x, y), CORD[0] + (255,))
    for (x, y) in ends:
        weight(img, x, y, 2.2)
    return img


def snared():
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    # a loop of cord round the middle, an ice weight hanging off it
    for k in range(48):
        a = k / 48 * math.pi * 2
        x = int(round(8.5 + math.cos(a) * 6.0))
        y = int(round(7.5 + math.sin(a) * 4.0))
        img.putpixel((x, y), CORD[1 + (k % 2)] + (255,))
    cord(img, (13, 10), (14, 14))
    weight(img, 14, 14.5, 2.4)
    weight(img, 3.5, 3.5, 1.6)
    return img


def main():
    os.makedirs(os.path.join(TEXTURES, "item"), exist_ok=True)
    os.makedirs(os.path.join(TEXTURES, "mob_effect"), exist_ok=True)
    bola().save(os.path.join(TEXTURES, "item", "bola.png"))
    snared().save(os.path.join(TEXTURES, "mob_effect", "snared.png"))
    print("wrote item/bola.png and mob_effect/snared.png")


if __name__ == "__main__":
    main()
