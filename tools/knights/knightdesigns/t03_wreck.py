"""
No. T3, the anchor knight, in the Dross's electric blue (round 2). The user kept his anchor (November 7, 2026: "for T3,
first change his design, then after make it so he throws his anchor at you and [pulls it back]") and asked for three
options; each carries the anchor on a chain, for that throw in his fight later. Round 1's copper diving knight is
archived in round1/. The user picked A (November 9: "T3A is the best"); his partner is T6 Stormblade.

  A  Wreck Knight   the diving helm, the anchor on his shoulder, its chain wound round his forearm
  B  Stormwake      heavy plate and a lightning crest, the anchor held upright at his side, its chain across his chest
  C  Stormcaptain   a captain's tricorne over a grilled helm, a long coat, the anchor planted in front, chain coiled
"""
import math

import numpy as np

from knightkit import kbody as K
from knightkit import kmats as C

NUMBER = 3
ID = "anchor"
NAME = "Wreck Knight"
MOOD = "great anchor"
BLURB = "The Storm King's anchor knight."
VARIANTS = (
    dict(letter="A", name="Wreck Knight", mood="anchor, diver", look="diver",
         blurb="He walked out of the sea after the storm that sank his ship: heavy riveted armour of blue-black steel "
               "wrapped in rope and crusted with barnacles, two air tanks on his back, and a great round diving helm "
               "whose porthole glows with the electric blue lightning trapped inside it. He carries the wreck's "
               "anchor on his shoulder, its chain wound round his forearm: he throws it at you and hauls it back."),
    dict(letter="B", name="Stormwake", mood="anchor, plate", look="plate",
         blurb="A knight in heavy blue-black plate, its seams crackling with the Dross's light, a barbute crested with "
               "a silver lightning bolt and a T of blue fire for a face. He holds a ship's anchor upright at his side, "
               "its flukes in the sand, its chain slung across his chest like a sash; he hurls it at you on its chain "
               "and drags you back with it."),
    dict(letter="C", name="Stormcaptain", mood="anchor, captain", look="captain",
         blurb="The captain of a ship the storm took, still at his post: a long cobalt coat over his armour, silver "
               "epaulettes, a captain's tricorne over a helm whose grille glows electric blue. He stands with both "
               "hands on the ring of his anchor, planted flukes-down in front of him, its chain coiled at his feet; "
               "when you come near he swings it out on the chain and reels it in."),
)
# The user's pick (November 9, 2026: "T3A is the best"); B and C are archived in round2/ (their code stays here).
VARIANTS = tuple(v for v in VARIANTS if v["letter"] == "A")

HELM = K.HEAD + np.array([0.0, 0.5, -0.5])


def build(s, v):
    look = v["look"]
    if look == "diver":
        diver(s)
    elif look == "plate":
        plate(s)
    else:
        captain(s)


# ---------------------------------------------------------------- shared pieces


def chain(s, points, w=1.5):
    """A chain along a polyline: links end to end, each turned a quarter from the last, every one touching the next."""
    pts = [np.array(p, dtype=float) for p in points]
    k = 0
    for a, b in zip(pts[:-1], pts[1:]):
        n = max(1, int(round(np.linalg.norm(b - a) / 2.2)))
        for i in range(n):
            p0 = a + (b - a) * (i / n)
            p1 = a + (b - a) * ((i + 1) / n)
            d = p1 - p0
            side = np.cross(d, [0.0, 1.0, 0.0] if abs(d[1]) < 0.9 * np.linalg.norm(d) else [1.0, 0.0, 0.0])
            if k % 2:
                side = np.cross(d, side)
            s.seg(p0, p1, w, 1, "chain", side=side, ext=0.45, inflate=0.04 * (k % 2))
            k += 1


def anchor(s, ring, crown, arm_dir, mat="anchor"):
    """
    A ship's anchor: its shank from the ring to the crown, the stock across near the ring, two arms curving out from the
    crown along `arm_dir` and back up towards the ring, a fluke on each. Returns the ring's middle (the chain ties on).
    """
    ring = np.array(ring, dtype=float)
    crown = np.array(crown, dtype=float)
    d = (crown - ring) / np.linalg.norm(crown - ring)          # ring -> crown
    out = np.array(arm_dir, dtype=float)
    out = out - d * np.dot(out, d)
    out = out / np.linalg.norm(out)
    s.seg(ring + d * 2.0, crown, 3.5, 3.5, mat)
    s.ring(ring, np.cross(d, out), 2.6, 1, 1, mat, n=8)
    stock = ring + d * 5.0
    across = np.cross(d, out)
    s.seg(stock - across * 7.0, stock + across * 7.0, 2, 2, "stock", side=d)
    for sign in (1.0, -1.0):
        pts = [crown, crown + out * sign * 5.5 - d * 1.0, crown + out * sign * 9.0 - d * 4.5,
               crown + out * sign * 9.5 - d * 8.5]
        s.curve(pts, 5, [3.5, 3.0, 2.6, 2.0], mat)
        tip = pts[-1]
        s.plate(tip - d * 0.5 + out * sign * 0.8, np.cross(out, d), -d, 5, 6, 2, mat)
    s.blob(crown, (2.5, 2.5, 2.5), mat, axis=d, slices=2)
    return ring


# ---------------------------------------------------------------- A: the diver


def diver(s):
    K.legs(s, "steel", "silver", boot="boot", width=1.08, thick=1.1)
    for sign in (1.0, -1.0):
        s.ring((sign * 6.9, 9.0, -0.5), (0, 1, 0), 4.6, 1, 2, "rope", n=8)
    K.hips(s, "steel", w=26, d=16)
    K.faulds(s, "silver", n=9, rx=13.5, rz=9.5)
    K.torso(s, "steel", "silver", bulk=1.15, belt="rope", buckle="silver")
    for k in range(5):
        for sign in (1.0, -1.0):
            s.box((sign * 5.5, 47.0 - k * 3.0, -9.9), (1, 1, 1), "rivet")
    s.seg((11.0, 50.0, -8.8), (-9.0, 33.0, -10.0), 3, 2, "rope", side=(0, 0, 1))
    for sign in (1.0, -1.0):
        p = np.array([sign * 15.0, K.SHOULDER_Y + 1.0, 0.0])
        s.rbox(p, (11, 7, 12), "silver", c=2)
        s.rbox(p + np.array([sign * 0.5, -4.5, 0.0]), (11.5, 3, 12.5), "steel", c=1)
        for k in range(4):
            s.box(p + np.array([sign * 2.0 * (k % 2), 3.6, -4.5 + k * 3.0]), (2, 1, 2), "barnacle")
    for sign in (1.0, -1.0):
        tank = np.array([sign * 5.0, 42.0, 11.0])
        s.blob(tank, (3.5, 8.5, 3.5), "tank", slices=4)
        s.seg(tank + np.array([0.0, 8.0, 0.0]), tank + np.array([0.0, 10.5, 0.0]), 2, 2, "silver")
        s.curve([tank + np.array([0.0, 10.0, 0.0]), tank + np.array([sign * 1.0, 13.0, -2.5]),
                 HELM + np.array([sign * 5.0, -2.0, 4.5])], 5, [1.5, 1.5, 1.5], "hose")
    s.seg((-6.0, 46.0, 10.5), (6.0, 46.0, 10.5), 2, 2, "rope", side=(0, 1, 0))
    elbow = np.array([-23.0, 42.0, -3.0])
    fist = K.SHOULDER_FIST
    K.arm_shoulder(s, "steel", "silver", "gauntlet")
    crown = np.array([-17.5, 63.0, 15.0])
    d = (crown - fist) / np.linalg.norm(crown - fist)
    ring = anchor(s.part("anchor"), fist - d * 8.0, crown, (1.0, 0.0, 0.0))
    # The chain: down from the ring, then wound twice round his forearm up to the elbow.
    fore = (fist - elbow) / np.linalg.norm(fist - elbow)
    for t in (0.35, 0.65):
        s.part("arm_r").ring(K.lerp(elbow, fist, t), fore, 3.4, 1, 1.5, "chain", n=8)
    chain(s.part("anchor"), [ring - d * 2.5, ring - d * 2.5 + np.array([0.0, -4.0, 0.0]),
              K.lerp(elbow, fist, 0.35) + np.array([0.0, -3.4, 0.0])])
    K.arm_hang(s, "steel", "silver", "gauntlet", fist=np.array([22.5, 29.0, -5.0]))
    # The diving helm: a big round dome on a collar of bolts, a porthole with the lightning inside, two side windows.
    hd = s.part("head")
    hd.ring(HELM + np.array([0.0, -6.5, 0.5]), (0, 1, 0), 8.0, 2, 3, "silver", n=12)
    for k in range(8):
        a = 2.0 * math.pi * k / 8.0
        hd.box(HELM + np.array([math.cos(a) * 9.6, -6.5, math.sin(a) * 9.6 + 0.5]), (2, 2, 2), "rivet")
    hd.blob(HELM, (8.5, 8.5, 8.5), "helm", slices=6)
    port = HELM + np.array([0.0, 0.5, -8.4])
    hd.blob(HELM + np.array([0.0, 0.5, -4.0]), (3.5, 3.5, 3.5), "trapped", slices=2)
    hd.seg(port + np.array([0.0, 0.0, 1.0]), port + np.array([0.0, 0.0, -0.5]), 7, 7, "glass", side=(1, 0, 0))
    hd.ring(port, (0, 0, 1), 4.2, 1, 2, "silver", n=10)
    for sign in (1.0, -1.0):
        w = HELM + np.array([sign * 8.3, 0.5, -1.0])
        hd.seg(w + np.array([-sign * 0.8, 0.0, 0.0]), w + np.array([sign * 0.5, 0.0, 0.0]), 4, 4, "glass",
              side=(0, 0, 1))
        hd.ring(w, (1, 0, 0), 2.6, 1, 1, "silver", n=8)
    hd.cone(HELM + np.array([0.0, 8.0, 0.0]), HELM + np.array([0.0, 11.0, 0.0]), 3.0, "silver", w1=2.0, steps=1)


# ---------------------------------------------------------------- B: heavy plate, the anchor at his side


def plate(s):
    K.legs(s, "steel", "silver", width=1.05, thick=1.08)
    K.hips(s, "steel", w=25)
    K.faulds(s, "silver", n=9, rx=13.5, rz=9.5)
    K.torso(s, "steel", "silver", bulk=1.12, belt="leather", buckle="silver")
    K.pauldrons(s, "silver", under="steel", size=1.15)
    for sign in (1.0, -1.0):
        K.zigzag(s, np.array([sign * 16.5, K.SHOULDER_Y + 4.5, -4.0]), np.array([sign * 19.0, K.SHOULDER_Y - 3.0,
                 -3.0]), 3, 1.0, "bolt", normal=(sign, 0, 0))
    fist = np.array([-23.0, 38.0, -14.0])
    K.arm_upright(s, "steel", "silver", "gauntlet", fist=fist)
    ring = anchor(s, fist + np.array([0.0, 8.0, 0.4]), np.array([-23.0, 5.0, -15.5]), (0.0, 0.0, 1.0))
    # The chain from the ring over his right shoulder, across his chest to his left hip.
    chain(s, [ring + np.array([0.0, 2.5, 0.0]), np.array([-14.0, 52.0, -8.0]), np.array([-6.0, 46.0, -10.6]),
              np.array([4.0, 39.0, -10.4]), np.array([10.5, 32.0, -9.6])])
    K.arm_hang(s, "steel", "silver", "gauntlet")
    K.cape(s, "cape", width=28, length=46, flare=4, clasp="silver", hem="hem", hem_h=12)
    # The barbute: a rounded helm, a T of blue fire for a face, a crest of a silver lightning bolt.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "silver")
    s.blob(HELM, (7.2, 8.0, 7.8), "helm", slices=5)
    s.seg(HELM + np.array([-4.5, 1.0, -7.4]), HELM + np.array([4.5, 1.0, -7.4]), 1, 2, "slit", side=(0, 1, 0))
    s.seg(HELM + np.array([0.0, 1.0, -7.4]), HELM + np.array([0.0, -5.5, -7.4]), 2, 2, "slit", side=(1, 0, 0))
    for sign in (1.0, -1.0):
        s.box(HELM + np.array([sign * 2.4, 1.0, -8.0]), (2, 1, 1), "spark")
    crest = [HELM + np.array([0.0, 7.0, -6.0]), HELM + np.array([0.0, 12.0, -2.0]), HELM + np.array([0.0, 9.5, 1.5]),
             HELM + np.array([0.0, 14.0, 6.0]), HELM + np.array([0.0, 8.0, 7.0])]
    for a, b in zip(crest[:-1], crest[1:]):
        s.seg(a, b, 1.5, 2.5, "silver", side=(0, 0, 1), ext=0.6)
    s.seg(HELM + np.array([0.0, 7.0, -5.0]), HELM + np.array([0.0, 7.0, 6.0]), 2, 3, "silver")


# ---------------------------------------------------------------- C: the captain, the anchor planted in front


def captain(s):
    K.legs(s, "steel", "silver", boot="boot")
    K.hips(s, "coat")
    K.cloth_skirt(s, "coattail", n=12, y=18.0, rx=12.5, rz=9.0, w=7, h=26)
    K.torso(s, "steel", "silver", bulk=1.05, belt="leather", buckle="silver")
    # The coat's front: two lapels over the breastplate, silver buttons down it.
    for sign in (1.0, -1.0):
        s.plate((sign * 6.5, 42.0, -9.8), (sign * 0.15, 0, -1), (0, 1, 0), 6, 18, 1, "coat")
        for k in range(4):
            s.box((sign * 4.4, 47.0 - k * 4.0, -10.6), (1, 1, 1), "silver")
    for sign in (1.0, -1.0):
        p = np.array([sign * 13.0, K.SHOULDER_Y + 1.5, 0.0])
        s.blob(p, (6.5, 3.0, 7.0), "coat", slices=2)
        s.rbox(p + np.array([0.0, 3.0, 0.0]), (10, 2, 10), "silver", c=1)
        for k in range(5):
            q = p + np.array([sign * 5.0, 2.0, -4.0 + k * 2.0])
            s.seg(q, q + np.array([sign * 0.3, -4.0, 0.0]), 1, 1, "fringe")
    K.arms_planted(s, "coat", "silver", "gauntlet")
    g = K.GRIP
    ring = anchor(s, g + np.array([0.0, 1.0, 0.0]), np.array([0.0, 7.0, -16.0]), (1.0, 0.0, 0.0))
    # The chain from the ring down to a coil on the ground beside the anchor.
    coil = np.array([9.0, 1.0, -17.0])
    chain(s, [ring + np.array([2.5, 0.0, 0.0]), np.array([6.0, 20.0, -17.0]), coil + np.array([0.0, 2.0, 0.0])])
    s.ring(coil, (0, 1, 0), 3.2, 1, 2, "chain", n=10)
    s.ring(coil + np.array([0.0, 1.0, 0.0]), (0, 1, 0), 2.0, 1, 2, "chain", n=8, phase=0.5)
    K.cape(s, "coattail", width=24, length=40, flare=4, back=9.5, hem="coattail", hem_h=12)
    # The helm: dark steel with a grille of electric blue for a face, a captain's tricorne on top.
    s.seg((0.0, 51.0, 0.0), (0.0, 54.0, -0.3), 9, 8, "coat")
    s.blob(HELM, (7.0, 7.5, 7.5), "helm", slices=5)
    s.box(HELM + np.array([0.0, -1.0, -6.6]), (10, 8, 2), "slit")
    for k in range(4):
        s.box(HELM + np.array([0.0, 2.0 - k * 2.0, -7.6]), (8 - (k % 2) * 2, 1, 1), "spark")
    crown = HELM + np.array([0.0, 7.0, 0.0])
    s.blob(crown, (6.5, 3.0, 6.5), "hat", slices=2)
    for k in range(3):
        a = 2.0 * math.pi * k / 3.0 + math.pi / 3.0           # the brim's sides; its points front and back corners
        out = np.array([math.sin(a), 0.0, -math.cos(a)])
        n = out * math.cos(math.radians(40)) + np.array([0.0, math.sin(math.radians(40)), 0.0])
        c = crown + out * 6.5 + np.array([0.0, 0.5, 0.0])
        s.plate(c, n, np.array([0.0, 1.0, 0.0]), 14, 5, 1, "hat", inflate=0.04 * k)
        u = np.array([0.0, 1.0, 0.0]) - n * n[1]
        u = u / np.linalg.norm(u)
        top = c + u * 2.3
        along = np.cross(n, np.array([0.0, 1.0, 0.0]))
        along = along / np.linalg.norm(along)
        s.seg(top - along * 6.5, top + along * 6.5, 1, 1, "silver", side=n)


def materials(v):
    return {
        "steel": C.dross_steel(3301),
        "silver": C.metal(C.SILVER, 3303, shine=1.8),
        "helm": C.dross_steel(3305, cracks=0.08) if v["look"] != "diver" else C.metal(C.SILVER, 3305, shine=1.5),
        "gauntlet": C.metal(C.SILVER, 3307, shine=1.8),
        "tank": C.dross_steel(3309, cracks=0.05),
        "hose": C.leather(3311),
        "boot": C.metal(C.IRON, 3313, shine=1.2),
        "rope": C.solid(C.ROPE, 3315),
        "rivet": C.metal(C.SILVER, 3317, shine=2.0),
        "barnacle": C.solid(C.BARNACLE, 3319),
        "glass": C.glass(C.GLASS_ELECTRIC, 3321, alpha=(80, 150)),
        "trapped": C.glowing(C.ELECTRIC, 3323),
        "anchor": C.plated(C.SILVER, C.ELECTRIC, seed=3325, cell=4.0, cracks=0.3, width=0.4),
        "stock": C.wood(C.DROSS_STEEL, 3327, plank=3.0, axis=0),
        "chain": C.metal(C.SILVER, 3329, shine=1.6),
        "leather": C.leather(3331),
        "cape": C.cloth(C.DROSS_COBALT, 3333, folds=1.2),
        "hem": C.cloth(C.DROSS_COBALT, 3335, folds=1.0, ragged=0.9),
        "coat": C.cloth(C.DROSS_COBALT, 3337, folds=1.0),
        "coattail": C.cloth(C.DROSS_COBALT, 3339, folds=1.1, ragged=0.6),
        "fringe": C.metal(C.SILVER, 3341, shine=1.4),
        "hat": C.leather(3343, pal=C.IRON_BLACK),
        "bolt": C.glowing(C.ELECTRIC, 3345),
        "spark": C.glowing(C.EYE_ELECTRIC, 3347),
        "slit": C.mouth(),
    }
