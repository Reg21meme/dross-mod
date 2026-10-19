"""No. 18 Fisher Below: a huge pale head breaking up through a frozen lake, an ice-fishing hut dangling as bait."""
import math

from rimekit import mats as M

NUMBER = 18
ID = "fisher"
NAME = "Fisher Below"
MOOD = "come get warm"
BLURB = ("A huge pale head breaking up through a frozen lake, its jaws lined with icicle teeth. Dangling from a "
         "stalk over its brow is its bait: a cosy ice-fishing hut with a warm, glowing window, its chimney smoke frozen "
         "solid in the air.")


def build(s):
    # Slabs of the lake's ice, broken and tipped up round the head.
    for k in range(9):
        a = 2 * math.pi * (k + 0.3) / 9
        r = 62 + (k % 3) * 6
        c = (math.sin(a) * r, 5 + (k % 2) * 2, -math.cos(a) * r * 0.9 + 6)
        n = (math.sin(a) * 0.5, 1.0, -math.cos(a) * 0.5)
        s.plate(c, n, (math.cos(a), 0, math.sin(a)), 26 + (k % 3) * 6, 22 + (k % 2) * 8, 3, "lakeice")
    # The skull: broad and flat-topped, sunk in the lake; a gaping mouth facing forward.
    s.blob((0, 30, 10), (50, 48, 46), "skin", cut_below=0, slices=20)
    s.rbox((0, 58, -32), (88, 20, 40), "skin", c=4)
    s.rbox((0, 13, -38), (92, 26, 44), "skin", c=4)
    s.box((0, 37, -40), (78, 20, 34), "mouth")
    # Icicle teeth, upper and lower.
    for i in range(10):
        x = -38 + i * 8.4
        h = 12 + (i * 7 % 5) * 2
        s.cone((x, 48.5, -55), (x * 1.02, 48.5 - h, -57), 5, "teeth", steps=3)
        h2 = 10 + (i * 3 % 4) * 2
        s.cone((x + 4, 26.5, -58), (x * 1.02 + 4, 26.5 + h2, -59.5), 4.5, "teeth", steps=3)
    # Tiny milky eyes high on the sides, old scars, little side fins on the ice.
    for x in (-1, 1):
        s.blob((44 * x, 66, -12), (4, 4, 4), "eye")
        s.plate((56 * x, 8, 18), (0.3 * x, 1, 0.2), (0, 0, 1), 18, 26, 2, "skin")
    # The stalk arching up over the brow, the line, and the bait: a little ice-fishing hut.
    s.curve([(0, 76, -6), (0, 124, 4), (0, 168, -14), (0, 184, -48), (0, 176, -80)], 10, [10, 8, 7, 6, 5], "skin")
    s.seg((0, 176, -80), (0, 158, -80), 1, 1, "rope")
    hut = (0.0, 140.0, -80.0)
    s.box(hut, (30, 26, 26), "planks")
    s.box((0, 126.5, -80), (32, 2, 28), "planks")
    s.plate((-8.5, 157, -80), (-0.6, 0.8, 0), (0, 0, 1), 32, 21, 3, "roof")
    s.plate((8.5, 157, -80), (0.6, 0.8, 0), (0, 0, 1), 32, 21, 3, "roof")
    s.seg((0, 162.5, -96), (0, 162.5, -64), 3, 3, "roof")
    s.box((-6, 140, -93.5), (9, 9, 1), "window")
    s.box((-6, 140, -93.8), (11, 1, 1), "planks")
    s.box((-6, 140, -93.8), (1, 11, 1), "planks")
    s.box((8, 135, -93.4), (8, 16, 1), "door")
    s.box((9, 160, -74), (5, 12, 5), "iron")
    # Its chimney smoke, frozen solid in the air.
    for (y, x, r) in ((169, 9.5, 3.5), (175, 11, 4.5), (182, 9, 5.5), (189, 6, 5)):
        s.blob((x, y, -74), (r, r * 0.85, r), "smoke")
    # A warm lantern glow under the eaves.
    s.box((14, 149, -94), (3, 4, 3), "window")


def materials():
    return {
        "skin": M.skin(M.FISH, seed=1801, mottle=1.5, frost_amount=0.35, scars=0.4),
        "mouth": M.mouth(),
        "teeth": M.ice(M.ICE, alpha=(190, 240), seed=1803, cracks=0.3, frost_amount=0.0),
        "eye": M.glowing(M.GLOW, seed=1805, base_pal=M.ICE, idx=3.6, glow_alpha=150),
        "lakeice": M.ice(M.ICE, alpha=(110, 175), seed=1807, cracks=0.5, bubbles=0.02, frost_amount=0.45),
        "rope": M.solid(M.ROPE, seed=1809),
        "planks": M.wood(M.WOOD, seed=1811, plank=3.0, axis=1, frost_amount=0.25),
        "roof": M.snow(seed=1813),
        "door": M.wood(M.WOOD, seed=1815, plank=2.5, axis=0, frost_amount=0.0),
        "iron": M.metal(M.IRON, seed=1817, frost_amount=0.4),
        "window": M.glowing(M.WARM, seed=1819, idx=3.6),
        "smoke": M.snow(M.SNOW, seed=1821, sparkle=0.0),
    }
