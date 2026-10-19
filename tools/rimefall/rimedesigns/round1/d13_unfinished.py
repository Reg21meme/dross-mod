"""No. 13 The Unfinished: an ice statue of a knight, its top half carved, its bottom half still a raw block."""
from rimekit import mats as M

NUMBER = 13
ID = "unfinished"
NAME = "The Unfinished"
MOOD = "half carved"
BLURB = ("An ice statue whose sculptor never finished it: the top half a beautifully carved knight, the bottom half "
         "still a rough block of ice with chisels and a mallet stuck in it. It drags itself along on its arms.")


def build(s):
    # The raw block it drags along.
    s.box((0, 22, 10), (56, 44, 48), "raw", rot=(0, 6, 2))
    s.box((-21, 28, 16), (18, 34, 34), "raw", rot=(0, -14, 9))
    s.box((20, 16, 22), (22, 30, 34), "raw", rot=(5, 15, -7))
    s.box((4, 41, 20), (40, 14, 32), "raw", rot=(-9, 5, 4))
    s.box((-4, 10, 36), (40, 20, 12), "raw", rot=(8, -6, 3))
    # Chisels and a mallet stuck in the block.
    for p, q in (((-16, 32, -6), (-23, 42, -15)), ((22, 24, 2), (30, 30, -6)), ((-6, 46, 30), (-8, 56, 38))):
        s.seg(p, q, 2, 2, "iron")
        s.seg(q, (q[0] + (q[0] - p[0]) * 0.8, q[1] + (q[1] - p[1]) * 0.8, q[2] + (q[2] - p[2]) * 0.8), 3, 3, "wood")
    s.seg((12, 46, 24), (17, 60, 30), 3, 3, "wood")
    s.rbox((18, 62, 31), (11, 8, 8), "wood", c=1, rot=(0, 30, 70))
    # The finished half: a knight's body rising out of the block, leaning forward.
    s.taper([(0, 44, -2), (0, 62, -12), (0, 78, -22)], [26, 28, 30], "marble", depths=[18, 18, 18])
    s.rbox((0, 70, -28), (26, 16, 6), "marble", c=2, rot=(-24, 0, 0))
    s.box((0, 53, -18), (25, 3, 18), "marble", rot=(-30, 0, 0))
    for x in (-1, 1):
        s.blob((18 * x, 82, -22), (10, 7, 10), "marble")
        s.box((18 * x, 76, -22), (19, 3, 19), "marble")
    s.rbox((0, 86, -26), (12, 5, 12), "marble", c=1)
    # The helmet: a visor slit with the glow of eyes behind it, a carved plume sweeping back.
    s.rbox((0, 95, -30), (15, 16, 15), "marble", c=2)
    s.box((0, 95, -37.6), (11, 2, 1), "visor")
    for x in (-1, 1):
        s.box((3 * x, 95, -37.4), (2, 1, 1), "eye")
    s.curve([(0, 103, -32), (0, 107, -22), (0, 104, -10), (0, 94, 0)], 5, [4, 4, 4, 3], "marble",
            depths=[10, 9, 8, 6])
    # A carved cape flowing back and melting into the raw block.
    s.plate((0, 66, -4), (0, 0.6, 0.8), (0, 0.8, -0.6), 30, 34, 3, "marble")
    s.plate((0, 56, 6), (0, 0.75, 0.66), (0, 0.66, -0.75), 32, 18, 3, "rawcape")
    # Arms reaching forward, gauntlets planted on the ground.
    for x in (-1, 1):
        s.taper([(19 * x, 80, -24), (23 * x, 52, -42), (21 * x, 10, -52)], [11, 10, 9], "marble")
        s.rbox((21 * x, 5, -54), (11, 10, 13), "marble", c=2)
        s.ring((22.5 * x, 36, -46), (0.05 * x, -1, -0.25), 6.5, 2, 3, "marble", n=8)


def materials():
    return {
        "raw": M.ice(M.ICE, alpha=(120, 185), seed=1301, cracks=0.55, bubbles=0.02, frost_amount=0.35, rim=1.2),
        "rawcape": M.ice(M.ICE, alpha=(150, 205), seed=1303, cracks=0.4, frost_amount=0.3),
        "marble": M.solid(M.MARBLE, idx=3.0, seed=1305, grain=0.8, bev=0.9, outline=0.16, frost_amount=0.2),
        "visor": M.mouth(),
        "eye": M.eye(M.GLOW, pupil=False),
        "iron": M.metal(M.IRON, seed=1307),
        "wood": M.wood(M.WOOD, seed=1309, plank=3.0, axis=1, frost_amount=0.3),
    }
