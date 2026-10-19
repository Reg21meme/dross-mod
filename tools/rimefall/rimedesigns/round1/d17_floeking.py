"""No. 17 Floe King: a giant king crab carrying an iceberg for a shell."""
import math

from rimekit import mats as M

NUMBER = 17
ID = "floeking"
NAME = "Floe King"
MOOD = "the crusher"
BLURB = ("A giant king crab that carries an iceberg for a shell: spiny legs, eyes on stalks, and two huge claws, "
         "the right one a crusher.")


def build(s):
    # The carapace, spiny round its edge.
    s.blob((0, 40, 0), (40, 16, 34), "shell")
    for k in range(18):
        a = 2 * math.pi * (k + 0.5) / 18
        p = (math.sin(a) * 38, 44, -math.cos(a) * 32)
        s.cone(p, (math.sin(a) * 47, 49, -math.cos(a) * 40), 5, "spine", steps=2)
    # The iceberg on its back.
    s.box((0, 66, 6), (70, 30, 58), "berg", rot=(0, 8, 3))
    s.box((-4, 92, 10), (52, 28, 44), "berg", rot=(4, -12, 6))
    s.box((8, 116, 8), (32, 26, 30), "berg", rot=(10, 22, -8))
    s.box((-26, 84, 18), (22, 34, 26), "berg", rot=(-8, 10, 14))
    s.box((28, 80, 0), (20, 30, 28), "berg", rot=(6, -18, -16))
    s.cone((-6, 126, 6), (-12, 162, 4), 20, "berg", steps=6)
    s.cone((18, 118, 16), (26, 146, 22), 15, "berg", steps=5)
    s.cone((-22, 100, 22), (-36, 124, 30), 12, "berg", steps=4)
    for p, q, w in (((30, 70, -16), (44, 86, -26), 8), ((-30, 72, -12), (-44, 90, -18), 7), ((6, 100, -14), (8, 114, -28), 6)):
        s.cone(p, q, w, "shard", steps=3)
    # Eyes on stalks.
    for x in (-1, 1):
        s.taper([(8 * x, 46, -30), (10 * x, 60, -38)], [4, 3], "shell")
        s.blob((10 * x, 62, -39), (3, 3, 3), "eye")
    for x in (-4, 0, 4):
        s.box((x, 32, -33), (3, 6, 3), "shell")
    # The claws: a great crusher on the right, a smaller nipper on the left.
    for x, k in ((-1, 1.3), (1, 0.85)):
        s.taper([(24 * x, 36, -26), (36 * x * k + 6 * x, 46, -40 * k - 6), (40 * x * k + 6 * x, 42, -58 * k - 6)],
                [14 * k + 2, 13 * k + 2, 12 * k + 2], "shell")
        palm = (42 * x * k + 6 * x, 44, -68 * k - 8)
        s.blob(palm, (14 * k, 12 * k, 16 * k), "shell")
        s.curve([(palm[0], palm[1] + 6 * k, palm[2] - 12 * k), (palm[0] + 2 * x, palm[1] + 5 * k, palm[2] - 24 * k),
                 (palm[0] + 3 * x, palm[1] + 1 * k, palm[2] - 34 * k)], 4, [11 * k, 8 * k, 3], "shell")
        s.curve([(palm[0], palm[1] - 7 * k, palm[2] - 10 * k), (palm[0] + 2 * x, palm[1] - 6 * k, palm[2] - 22 * k),
                 (palm[0] + 3 * x, palm[1] - 2 * k, palm[2] - 30 * k)], 4, [10 * k, 7 * k, 3], "shell")
        for j in range(4):
            s.cone((palm[0] + 6 * x * k, palm[1] + 9 * k, palm[2] + (j - 1.5) * 6 * k),
                   (palm[0] + 9 * x * k, palm[1] + 16 * k, palm[2] + (j - 1.5) * 7 * k), 4, "spine", steps=2)
    # Three pairs of long spiny legs.
    for x in (-1, 1):
        for z in (-12, 6, 24):
            hip = (34 * x, 38, z)
            knee = (68 * x, 62, z * 1.3)
            tip = (100 * x, 0.5, z * 1.6 + 4)
            mid = (86 * x, 34, z * 1.45 + 2)
            s.taper([hip, knee, mid, tip], [12, 10, 7, 3], "shell")
            for t in (0.3, 0.6):
                p = (hip[0] + (knee[0] - hip[0]) * t, hip[1] + (knee[1] - hip[1]) * t + 5, hip[2] + (knee[2] - hip[2]) * t)
                s.cone(p, (p[0], p[1] + 6, p[2]), 3, "spine", steps=2)


def materials():
    return {
        "shell": M.chitin(M.CRAB, seed=1701, gloss=1.2, frost_amount=0.35, outline=0.16),
        "spine": M.chitin(M.CHITIN, seed=1703, gloss=1.4, frost_amount=0.1),
        "eye": M.glowing(M.GLOW, seed=1705, base_pal=M.CHITIN, idx=3.2),
        "berg": M.glacier(M.GLACIER, seed=1707, snow_amount=0.7, bands=1.0, cracks=0.35),
        "shard": M.ice(M.ICE, alpha=(150, 215), seed=1709, cracks=0.4, glow_pal=M.GLOW, glow_alpha=40),
    }
