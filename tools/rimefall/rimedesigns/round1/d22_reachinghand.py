"""No. 22 Reaching Hand: a giant's frozen hand bursting out of the snow, its fingers frostbitten trees."""
import numpy as np

from rimekit import mats as M

NUMBER = 22
ID = "reachinghand"
NAME = "Reaching Hand"
MOOD = "not just trees"
BLURB = ("A giant frozen hand bursting up out of a snowdrift. Its fingers are frostbitten trees, the same dead "
         "trees that grow all over Rimefall, and the rest of the giant is still buried. After this, every dead tree "
         "in Rimefall looks suspicious.")

FINGERS = (  # x at the knuckle, lengths of its three joints, how far it curls forward
    (-25, (30, 26, 18), 0.8),
    (-8.5, (36, 30, 22), 1.0),
    (8.5, (34, 30, 20), 0.95),
    (25, (28, 24, 16), 0.85),
)


def build(s):
    # The snowdrift and the clods it threw up.
    s.blob((0, 10, 4), (66, 24, 60), "snow", cut_below=0, slices=6)
    for p, r in (((-52, 30, -30), 6), ((48, 42, 26), 5), ((-30, 24, 40), 5), ((58, 18, -24), 7)):
        s.blob(p, (r, r * 0.8, r), "snow", slices=3)
    # The forearm and the hand.
    s.tube([(0, 0, 10), (0, 40, 8), (0, 76, 4), (0, 102, 0)], [48, 46, 43, 40], "skin", depths=[42, 40, 36, 32])
    s.ring((0, 30, 8), (0, 1, 0.05), 23, 3, 6, "snow", n=12)
    s.rbox((0, 130, -2), (70, 58, 26), "skin", c=6, rot=(-10, 0, 0))
    s.blob((0, 128, 6), (32, 28, 12), "skin", slices=7)
    s.blob((-28, 114, -2), (10, 16, 13), "skin", slices=5)
    # Knuckles turning to bark, then the fingers: frostbitten trees curling forward, twigs at their tips.
    for x, lengths, curl in FINGERS:
        p = np.array([x, 160.0, -8.0])
        s.blob(p, (10, 8, 10), "bark")
        ang = 0.15
        pts = [p.copy()]
        for j, L in enumerate(lengths):
            ang += (0.32 + 0.12 * j) * curl
            p = p + np.array([x * 0.015 * L, np.cos(ang) * L, -np.sin(ang) * L])
            pts.append(p.copy())
        s.taper(pts, [17, 15, 12, 8], "bark")
        for q in pts[1:-1]:
            s.blob(q, (8.5, 7, 8.5), "bark")
        # Twigs splitting off the fingertip and along the joints.
        tip = pts[-1]
        for k, d in enumerate(((0.4, 0.6, -0.5), (-0.5, 0.8, -0.2), (0.1, 0.4, -0.9))):
            e = tip + np.array(d) * (14 + k * 3)
            s.taper([tip, e], [4, 1.5], "twig", joints=False)
            s.taper([e, e + np.array([d[0] * 0.5 - 0.3, 0.7, d[2] * 0.4]) * 7], [1.5, 1], "twig", joints=False)
        mid = pts[2]
        s.taper([mid, mid + np.array([x * 0.25, 10, 4])], [3.5, 1.5], "twig", joints=False)
        # Icicles hanging from the underside of the curled fingers.
        for q, h in ((pts[2], 12), (pts[3], 8)):
            s.cone(q + np.array([0, -5, -2]), q + np.array([0, -5 - h, -2]), 3, "icicle", steps=2)
    # The thumb, out to the side.
    s.taper([(34, 116, -6), (46, 136, -16), (52, 154, -30), (52, 166, -42)], [20, 16, 13, 9], "bark")
    s.blob((46, 136, -16), (9, 8, 9), "bark")
    s.taper([(52, 166, -42), (60, 180, -46)], [4, 1.5], "twig", joints=False)
    # A frozen silver ring with a pale stone on one finger.
    s.ring((-25, 178, -11), (0.0, 1.0, -0.25), 9.5, 3, 4, "silver", n=10)
    s.box((-25, 179, -20.5), (5, 5, 3), "gem")


def materials():
    return {
        "skin": M.skin(M.FROST_SKIN, seed=2201, mottle=1.5, frost_amount=0.35, cracks=0.07, scars=0.25),
        "bark": M.bark(M.BARK, seed=2203, rime=0.55),
        "twig": M.bark(M.BARK, seed=2205, rime=0.3),
        "icicle": M.solid(M.ICE, idx=3.6, seed=2207, grain=0.8, bev=0.9, outline=0.05),
        "snow": M.snow(seed=2209),
        "silver": M.metal(M.MARBLE, seed=2211, frost_amount=0.1),
        "gem": M.glowing(M.GLOW, seed=2213, idx=3.6),
    }
