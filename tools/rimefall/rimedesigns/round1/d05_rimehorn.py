"""No. 5 Rimehorn: an ice-age woolly rhino, its back half still locked in a block of ice it drags along."""
import math

from rimekit import mats as M

NUMBER = 5
ID = "rimehorn"
NAME = "Rimehorn"
MOOD = "never stops"
BLURB = ("An ice-age woolly rhino dug out of the frozen ground: its back half is still locked in a block of ice it "
         "drags along, and its horn is clear ice as long as you are tall.")


def build(s):
    # The front half: shoulders, a shaggy hump, a long low head.
    s.blob((0, 44, -16), (25, 27, 28), "fur")
    s.blob((0, 62, -22), (19, 12, 18), "mane")
    s.blob((0, 38, -6), (22, 20, 22), "fur")
    s.taper([(0, 46, -40), (0, 38, -56), (0, 29, -71)], [26, 21, 15], "fur", depths=[24, 22, 16])
    s.rbox((0, 27, -76), (14, 12, 8), "skin", c=3)
    for x in (-1, 1):
        s.rbox((8.5 * x, 47.5, -48), (5, 7, 3), "fur", c=1)
        s.box((11 * x, 39, -61), (2, 2, 1), "eye")
        s.box((4 * x, 24, -80.2), (2, 2, 1), "mouth")
    # The horns: a great curved horn of clear ice, a short one behind it.
    s.curve([(0, 32, -77), (0, 44, -88), (0, 60, -96), (0, 72, -97)], 7, [12, 9, 5, 2], "horn")
    s.curve([(0, 40, -66), (0, 48, -70), (0, 54, -70)], 3, [9, 6, 2], "horn")
    # Front legs: pillars with dark hooves.
    for x in (-1, 1):
        s.taper([(15 * x, 34, -24), (16 * x, 16, -27), (16 * x, 6, -28)], [16, 14, 13], "fur")
        s.rbox((16 * x, 3, -28.5), (15, 6, 15), "hoof", c=2)
    # A long woolly fringe hanging under the belly and chest.
    for i in range(18):
        a = math.pi * (0.08 + 0.84 * (i % 9) / 8)
        side = 1 if i < 9 else -1
        z = -40 + (i % 9) * 6.5
        x = side * (19 + 3 * math.sin(a))
        top = 34 - (i % 3) * 2
        s.seg((x, top, z), (x * 1.04, top - 15 - (i % 4) * 2, z + 1), 5, 3, "hair", side=(0, 0, 1))
    # The back half, frozen inside a block of ice that drags on the ground.
    s.blob((0, 40, 24), (23, 25, 24), "fur")
    for x in (-1, 1):
        s.taper([(14 * x, 32, 32), (15 * x, 16, 38), (15 * x, 6, 36)], [15, 13, 12], "fur")
        s.rbox((15 * x, 3, 36), (14, 6, 14), "hoof", c=2)
    s.taper([(0, 52, 46), (0, 44, 52), (0, 36, 53)], [6, 5, 4], "fur")
    s.box((0, 34, 31), (54, 64, 44), "ice", rot=(0, 4, 0))
    s.box((-2, 64, 30), (40, 18, 34), "ice", rot=(7, -11, 9))
    s.box((24, 54, 26), (18, 26, 30), "ice", rot=(14, 20, -24))
    s.box((-25, 50, 36), (18, 30, 30), "ice", rot=(-10, -16, 22))
    s.box((28, 22, 36), (12, 40, 34), "ice", rot=(4, 8, -10))
    s.box((-28, 20, 26), (12, 36, 30), "ice", rot=(-6, -6, 9))
    s.box((4, 24, 55), (36, 40, 10), "ice", rot=(10, 4, 3))
    s.box((-6, 8, 8), (34, 16, 12), "ice", rot=(-14, 6, 0))
    for p, q in (((-12, 72, 26), (-17, 86, 22)), ((8, 74, 36), (12, 90, 40)), ((20, 66, 22), (31, 79, 18)),
                 ((-20, 64, 44), (-30, 76, 52))):
        s.cone(p, q, 9, "ice", steps=3)
    for x, h in ((-18, 9), (-8, 13), (2, 7), (12, 11), (20, 6)):
        s.cone((x, 66, 9.5), (x, 66 - h, 8.5), 3.5, "icicle", steps=2)
    # Snow ploughed up in front of the dragging block.
    for x, z, w in ((-24, 6, 10), (-12, 3, 8), (14, 3, 9), (25, 6, 10)):
        s.box((x, 1.5, z), (w, 3, 5), "snow", rot=(0, x * 0.8, 0))
        s.box((x * 0.95, 3.5, z - 0.5), (w - 4, 2, 3), "snow", rot=(0, x * 0.8, 0))


def materials():
    return {
        "fur": M.fur(M.ELK, seed=501, strand=3.6, frost_amount=0.4),
        "mane": M.fur(M.CLOTH_PALE, seed=503, strand=3.6, frost_amount=0.45),
        "hair": M.fur(M.ELK, seed=505, strand=3.0, frost_amount=0.2, along_box=True),
        "skin": M.skin(M.STONE, seed=507, mottle=0.8, frost_amount=0.2),
        "hoof": M.chitin(M.BLACK_ICE, seed=509, gloss=1.0),
        "mouth": M.mouth(),
        "eye": M.eye(M.GLOW, pupil=False),
        "horn": M.ice(M.ICE, alpha=(150, 215), seed=511, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=70,
                      crack_glow=0.9),
        "ice": M.ice(M.ICE, alpha=(55, 115), seed=513, cracks=0.22, bubbles=0.01, rim=0.8),
        "snow": M.snow(seed=515),
        "icicle": M.solid(M.ICE, idx=3.6, seed=517, grain=0.8, bev=0.8, outline=0.05),
    }
