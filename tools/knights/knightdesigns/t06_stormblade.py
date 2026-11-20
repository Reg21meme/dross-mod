"""
No. T6 Stormblade: the thunder knights' ninja, the anchor knight's partner. The user (November 9, 2026): "give the
double blade thing to the lightning dude, make him like a speed ninja with lightning attacks ... T3A is the best": F1C's
twin blades on a lean, light knight of the Dross's blue, lightning crackling down both swords.
"""
import numpy as np

from firekit.parts import leg
from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 6
ID = "stormblade"
NAME = "Stormblade"
MOOD = "twin blades"
BLURB = ("The fastest of the Storm King's knights and the lightest, the anchor knight's partner: no plate but a "
         "breastplate and guards of blue-black steel over a cobalt wrap, a hood and a mask with one slit of electric "
         "blue for his eyes, a scarf of the Dross's blue streaming behind him as if he never stopped running. He "
         "fights with two swords, one held out in front of him and one reversed along his forearm, lightning "
         "crackling down both: he's on you in a flash and gone again, the scabbards on his back empty.")

HOOD = K.HEAD + np.array([0.0, 1.0, 0.6])
OUT_R = np.array([-1.0, 0.0, 0.0])                 # the right arm's outside


def build(s):
    # Light legs: cloth wraps, steel at the knees, a guard down each shin, soft boots.
    K.legs(s, "wrap", "steel", boot="boot", width=0.92, thick=0.84)
    for sign in (1.0, -1.0):
        x = sign * (K.LEG_X + 0.5) * 0.92
        g = s.part(K.side_part("leg", sign))
        g.rbox((x, 9.5, -3.2), (5, 8, 2), "steel", c=1)
        for y in (5.5, 21.5):
            g.ring((x, y, -0.6), (0, 1, 0), 3.3, 1, 2, "band", n=6)
    K.hips(s, "wrap", w=21, d=13)
    K.cloth_skirt(s, "skirt", n=8, y=26.5, rx=10.0, rz=7.2, w=6, h=9)
    # The body: a cobalt wrap, a breastplate with a lightning bolt, a bright sash knotted at the front.
    K.torso(s, "gi", "wrap", bulk=0.86, belt="sash")
    s.plate((0.0, 44.0, -7.9), (0.0, 0.15, -1.0), (0, 1, 0), 15, 12, 2, "steel")
    K.zigzag(s, (2.5, 48.5, -9.1), (-2.0, 39.5, -9.1), 4, 1.3, "bolt", w=1.0, normal=(0.0, 0.0, 1.0))
    s.box((5.0, 33.0, -8.4), (3, 3, 2), "sash")
    for x, h, tilt in ((4.3, 9, 0.05), (6.2, 7, -0.1)):
        s.plate((x, 33.0 - h / 2.0 - 0.5, -9.1), (tilt, 0.0, -1.0), (0, 1, 0), 2.5, h, 1, "tail")
    # Shoulders: a steel guard on the left, cloth on the right.
    s.blob((13.5, 51.5, 0.0), (6.0, 4.2, 6.5), "steel", slices=3)
    s.rbox((15.0, 48.5, 0.0), (8, 3, 11), "steel", c=1)
    s.blob((-13.5, 51.0, 0.0), (5.5, 3.8, 6.0), "gi", slices=3)
    # The scabbards crossed on his back (empty: both swords are out).
    s.seg((-8.0, 34.0, 8.4), (8.0, 58.0, 9.6), 3, 2, "sheath")
    s.seg((8.0, 34.0, 8.9), (-8.0, 58.0, 10.1), 3, 2, "sheath")
    # Left arm: a sword held out in front of him, point up and forward.
    elbow_l = np.array([22.0, 42.0, -9.0])
    fist_l = np.array([14.0, 41.0, -19.5])
    arm(s, 1.0, elbow_l, fist_l)
    bracer(s.part("arm_l"), elbow_l, fist_l)
    sword(s.part("hand_l"), fist_l, np.array([-0.12, 0.62, -0.78]), None)
    # Right arm: a sword reversed in his fist, its blade along the outside of his forearm.
    elbow_r = np.array([-21.5, 41.0, -3.5])
    fist_r = np.array([-21.0, 31.0, -10.5])
    arm(s, -1.0, elbow_r, fist_r)
    bracer(s.part("arm_r"), elbow_r, fist_r)
    fore = (elbow_r - fist_r) / np.linalg.norm(elbow_r - fist_r)
    sword(s.part("hand_r"), fist_r, fore * 0.9 + OUT_R * 0.42, OUT_R)
    # The head: a snug hood, a mask over his face, one slit of electric blue, a silver band with a bolt on it.
    hd = s.part("head")
    hd.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "wrap")
    hd.rbox((0.0, 53.5, -0.3), (13, 4, 12), "scarf", c=2)
    hd.rbox(HOOD, (15, 17, 16), "hood", c=3)
    hd.rbox(K.HEAD + np.array([0.0, -3.2, -6.6]), (13, 7, 3.4), "mask", c=1)
    K.eye_slit(hd, K.HEAD + np.array([0.0, 1.0, 0.0]), 4.5, "visor", "eye", z_out=7.5)
    hd.box(K.HEAD + np.array([0.0, 5.2, -8.15]), (14, 2.6, 1.0), "silver")
    K.zigzag(hd, K.HEAD + np.array([0.9, 6.4, -8.75]), K.HEAD + np.array([-0.9, 4.0, -8.75]), 3, 0.7, "bolt", w=0.8,
             normal=(0.0, 0.0, 1.0))
    # The scarf's two ends and the band's two ties streaming out behind him, each bending down as it goes.
    streamer(s, (2.5, 53.0, 4.5), [((0.25, -0.2, 1.0), 12.0), ((0.35, -0.7, 1.0), 12.0)], 6)
    streamer(s, (-2.5, 52.5, 4.5), [((-0.3, -0.12, 1.0), 10.0), ((-0.4, -0.6, 1.0), 10.0)], 5)
    streamer(hd, (1.6, 63.7, 7.2), [((0.15, -0.15, 1.0), 7.0), ((0.25, -0.6, 1.0), 6.0)], 2.5)
    streamer(hd, (-1.6, 63.3, 7.2), [((-0.2, -0.2, 1.0), 6.0), ((-0.3, -0.65, 1.0), 5.0)], 2.5)


def arm(s, sign, elbow, fist):
    """A lean arm in the cobalt wrap, steel at the elbow, a dark glove."""
    leg(s.part(K.side_part("arm", sign)), [K.shoulder(sign), elbow, fist], [5.8, 5.0, 4.3], "gi", joint_mat="steel",
        joint_scale=1.2)
    s.part(K.side_part("hand", sign)).rbox(fist, (4.5, 4.5, 4.5), "glove", c=1)


def streamer(s, start, pieces, width):
    """A strip of cloth out behind him: flat pieces end to end, each along its own way (so it bends as it streams)."""
    p = np.array(start, dtype=float)
    for k, (way, length) in enumerate(pieces):
        way = np.array(way, dtype=float) / np.linalg.norm(way)
        flat = np.cross(way, np.array([1.0, 0.0, 0.0]))
        if flat[1] < 0:
            flat = -flat
        s.plate(p + way * (length / 2.0), flat, way, width - k * 0.5, length + 1.0, 1, "tail" if k else "scarf",
                inflate=0.05 * k)
        p = p + way * length


def bracer(s, elbow, fist):
    """A steel guard round the forearm."""
    s.seg(K.lerp(elbow, fist, 0.3), K.lerp(elbow, fist, 0.85), 5.8, 5.8, "steel")


def sword(s, fist, way, out):
    """
    A straight sword in `fist`, its blade along `way`: a wrapped hilt through the fist, a round guard, a silver blade
    with an electric blue edge, and lightning crackling down it and leaping off its point. `out` (a reversed grip) turns
    the blade's flat to face that way; None turns it to face sideways.
    """
    d = np.array(way, dtype=float)
    d = d / np.linalg.norm(d)
    flat = np.array(out if out is not None else (1.0, 0.0, 0.0), dtype=float)
    side = np.cross(d, flat)
    side = side / np.linalg.norm(side)
    s.seg(fist - d * 5.0, fist + d * 2.5, 2, 2, "hilt", side=side)
    s.blob(fist - d * 5.5, (1.3, 1.3, 1.3), "silver", slices=2)
    s.seg(fist + d * 2.6, fist + d * 3.6, 5, 5, "silver", side=side)
    root = fist + d * 3.6
    tip = fist + d * 26.0
    s.seg(root, tip - d * 3.0, 3.5, 1.2, "blade", side=side)
    s.cone(tip - d * 3.5, tip, 3.5, "blade", w1=0.8, d0=1.2, d1=0.8, steps=2, side=side)
    s.seg(root + side * 1.9, tip - d * 3.5 + side * 1.9, 0.8, 1.4, "edge", side=side)
    normal = np.cross(side, d)
    K.zigzag(s, root + d * 1.0 - side * 1.9, tip + d * 4.0, 7, 1.3, "bolt", w=0.8, normal=normal, seed=3)


def materials():
    return {
        "wrap": C.cloth(C.DROSS_STEEL, 3601, folds=0.8),
        "gi": C.cloth(C.DROSS_COBALT, 3603, folds=1.0),
        "skirt": C.cloth(C.DROSS_COBALT, 3605, folds=1.1, ragged=0.3),
        "hood": C.cloth(C.DROSS_STEEL, 3607, folds=0.9),
        "mask": C.cloth(C.DROSS_STEEL, 3609, folds=0.6),
        "scarf": C.cloth(C.ELECTRIC, 3611, folds=1.0),
        "tail": C.cloth(C.ELECTRIC, 3613, folds=1.3, ragged=0.5),
        "sash": C.cloth(C.ELECTRIC, 3615, folds=0.8),
        "band": C.cloth(C.DROSS_COBALT, 3617, folds=0.5),
        "steel": C.dross_steel(3619, cracks=0.1),
        "silver": C.metal(C.SILVER, 3621, shine=1.8),
        "glove": C.leather(3623, pal=C.DROSS_STEEL),
        "boot": C.leather(3625, pal=C.DROSS_STEEL),
        "sheath": C.leather(3627, pal=C.DROSS_STEEL),
        "hilt": C.leather(3629, pal=C.DROSS_COBALT),
        "blade": C.metal(C.SILVER, 3631, shine=2.2),
        "edge": C.glowing(C.ELECTRIC, 3633),
        "bolt": C.glowing(C.EYE_ELECTRIC, 3635),
        "eye": C.glowing(C.EYE_ELECTRIC, 3637),
        "visor": C.glowing(C.ELECTRIC, 3639),
    }
