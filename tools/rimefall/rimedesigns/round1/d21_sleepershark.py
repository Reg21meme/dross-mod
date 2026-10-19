"""No. 21 Sleeper Shark: a 400-year-old Greenland shark bursting straight up out of a snowdrift, jaws wide open."""
import numpy as np

from rimekit import mats as M
from rimekit.shapes import catmull

NUMBER = 21
ID = "sleepershark"
NAME = "Sleeper Shark"
MOOD = "400 years old"
BLURB = ("An ancient shark that swims through snow, frozen bursting straight up out of a drift with its jaws wide "
         "open: thick and scarred, blind, with glowing parasites hanging from its eyes (real Greenland sharks have "
         "these; they live for 400 years).")

# Tail (in the snow) to the top of the head, curving up and forward (-z); its back faces +z.
PATH = [(0, 4, 40), (0, 46, 30), (0, 96, 14), (0, 142, 0), (0, 176, -8)]
WIDTH = [12, 26, 34, 34, 30]


def build(s):
    # The snowdrift it bursts out of, and clods of snow thrown up.
    s.blob((0, 8, 26), (46, 18, 46), "snow", cut_below=0, slices=6)
    for p, r in (((-38, 30, -6), 5), ((40, 44, 20), 4), ((30, 70, -18), 3), ((-30, 56, 40), 4), ((46, 14, -16), 6)):
        s.blob(p, (r, r * 0.8, r), "snow", slices=3)
    pts = catmull(PATH, 12)
    ws = list(np.interp(np.linspace(0, 1, len(pts)), np.linspace(0, 1, len(WIDTH)), WIDTH))
    s.tube(pts, ws, "skin", depths=[w * 0.9 for w in ws], side=(1, 0, 0))
    # The head: the snout thrown back, the lower jaw dropped forward, the dark mouth between, rows of teeth.
    s.tube([(0, 172, -6), (0, 194, -10), (0, 210, -6), (0, 216, 2)], [30, 26, 20, 10], "skin", depths=[26, 24, 18, 9],
           side=(1, 0, 0))
    s.tube([(0, 172, -12), (0, 176, -26), (0, 178, -38)], [26, 22, 14], "skin", depths=[10, 8, 6], side=(1, 0, 0))
    s.plate((0, 189, -18), (0, 0.45, -1), (0, 1, 0.3), 22, 24, 4, "mouth")
    for k in range(7):
        x = -9 + k * 3
        s.cone((x, 198 - abs(x) * 0.2, -19 - abs(x) * 0.05), (x * 0.95, 191 - abs(x) * 0.2, -21), 2.4, "teeth", steps=2)
        s.cone((x * 0.9, 180, -32 + abs(x) * 0.3), (x * 0.85, 186, -33 + abs(x) * 0.3), 2.2, "teeth", steps=2)
    # Blind eyes, glowing parasites dangling from them.
    for x in (-1, 1):
        eye = np.array([13.5 * x, 200, -9.0])
        s.box(eye, (2, 4, 4), "eye")
        for k, length in enumerate((12, 17, 9)):
            top = eye + np.array([x * 0.8, -2, (k - 1) * 1.6])
            s.seg(top, top + np.array([0, -length, -0.5 * (k - 1)]), 1, 1, "parasite")
            s.box(top + np.array([0, -length, -0.5 * (k - 1)]), (2, 2, 2), "parasite")
    # Fins: pectorals spread out to the sides, a low dorsal on its back, the tail fin out of the snow.
    for x in (-1, 1):
        s.plate((x * 30, 132, -2), (0, 0.25, -1), (x, -0.55, 0.15), 15, 30, 3, "fin")
        s.plate((x * 42, 120, 0), (0, 0.25, -1), (x * 0.7, -0.75, 0.15), 9, 14, 3, "fin")
    s.plate((0, 110, 28), (1, 0, 0), (0, 0.45, 1), 16, 20, 3, "fin")
    s.plate((0, 120, 30), (1, 0, 0), (0, 0.6, 1), 10, 12, 3, "fin")
    s.plate((0, 34, 58), (1, 0, 0), (0, 1, 0.55), 12, 38, 3, "fin")
    s.plate((0, 24, 62), (1, 0, 0), (0, 0.3, 1), 9, 16, 3, "fin")
    # Pale scars where the snow has scoured it.
    for k, (y, z, w) in enumerate(((70, 28, 7), (104, 18, 5), (150, 4, 6), (60, 22, 4))):
        s.box((17.5 if k % 2 else -17.5, y, z - 10), (1, 2, w), "scar", rot=(30 + 15 * k, 0, 0))


def materials():
    return {
        "skin": M.skin(M.SHARK, seed=2101, mottle=1.6, frost_amount=0.3, scars=0.45),
        "fin": M.skin(M.SHARK, seed=2103, mottle=1.0, frost_amount=0.2, outline=0.2),
        "snow": M.snow(seed=2105),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        "eye": M.solid(M.PALE_SKIN, idx=3.8, seed=2107, outline=0.0),
        "parasite": M.glowing(M.GLOW_GREEN, seed=2109, idx=3.4),
        "scar": M.solid(M.SNOW, idx=4.2, seed=2111, outline=0.0),
    }
