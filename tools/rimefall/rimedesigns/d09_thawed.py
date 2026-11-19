"""No. 9 Thawed Soldier (regular mob): an ancient warrior thawed out of a glacier; axe or spear and shield, two sizes."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import Scaled

NUMBER = 9
ID = "thawed"
NAME = "Thawed Soldier"
SECTION = "mob"
BLURB = ("One of an ancient war band frozen in a glacier for ten thousand years and awake again: hide armour strapped "
         "with whale bone, a fur mantle, a bone helmet with tusks, an icicle beard, pale glowing eyes. Round 2: a "
         "regular mob (the user: 'the thawed soldier'), in two sizes and with two weapons; the user kept the 4-block "
         "ones and had the slabs of glacier on his back and shoulder and the ice round the axe-bearer's fist taken off.")
# k: the scale against the build below (its helmet's horns reach about 5.3 blocks at k = 1). The big ones were 4 blocks
# to their horns' tips until November 9, 2026, when the user had them made as tall as the other elemental knights
# ("make the ice dudes the same heights as everyone else"): at 4.6 their head sits where the knights' do (about 58 units
# up, tools/knights/knightkit/kbody.HEAD) and their shoulders about where the knights' are.
BIG = 4.6 / 5.31
SMALL = 2.5 / 5.31
VARIANTS = (
    dict(letter="A", mood="axe, 2.5 tall", weapon="axe", k=SMALL),
    dict(letter="B", mood="spear, 2.5 tall", weapon="spear", k=SMALL),
    dict(letter="C", mood="axe, 4.6 tall", weapon="axe", k=BIG),
    dict(letter="D", mood="spear, 4.6 tall", weapon="spear", k=BIG),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('C', 'D'))


def body(s):
    # Legs in hide leggings bound with rope, fur-wrapped boots.
    # (each piece on its part: a statue keeps them all on its one bone; the moving knight, tools/knights/mobs.py,
    # puts each part on a bone of its own)
    for x in (-1, 1):
        g = s.part("leg_l" if x > 0 else "leg_r")
        g.taper([(7.5 * x, 30, 1), (8.5 * x, 17, -2), (8.5 * x, 6, 0)], [11, 10, 9], "hide")
        g.rbox((8.5 * x, 3.5, -1.5), (11, 7, 15), "fur", c=2)
        for y in (12, 20):
            g.ring((8.5 * x, y, -1), (0, 1, 0), 5.6, 2, 3, "rope", n=6)
    w = s.part("waist")
    # Hips, a fur kilt, the belt with a bone buckle and a pouch.
    w.rbox((0, 30, 0), (26, 9, 16), "hide", c=2)
    # (Each flap a little longer than the last, so no two share a top or bottom: overlapping, they'd flicker.)
    for i in range(7):
        a = math.pi * (0.1 + 0.8 * i / 6)
        x, z = math.cos(a) * 13.5, -math.sin(a) * 9
        w.plate((x, 25 - (i % 3) * 0.8, z), (x, 0, z), (0, 1, 0), 7, 13 + (i % 3), 2, "furflap")
    for i in range(5):
        a = math.pi * (1.15 + 0.7 * i / 4)
        x, z = math.cos(a) * 13.5, -math.sin(a) * 9
        w.plate((x, 25 - (i % 3) * 0.8, z), (x, 0, z), (0, 1, 0), 7, 12 + (i % 3), 2, "furflap")
    w.rbox((0, 34, 0), (28, 4, 18), "leather", c=1)
    w.box((0, 34, -9.5), (6, 5, 2), "bone")
    w.rbox((-12, 31, -6), (5, 6, 4), "leather", c=1)
    # A barrel chest in hide armour, ribs of whale bone strapped across it, heavy shoulders.
    s.rbox((0, 44, 0), (28, 16, 17), "hide", c=3)
    s.rbox((0, 52, -0.5), (32, 10, 18), "hide", c=3)
    for y in (39, 44, 49):
        s.seg((-12, y, -9), (12, y + 1.5, -9), 2, 2, "bone")
    s.seg((0, 37, -9.5), (0, 52, -9.5), 3, 2, "leather")
    for x in (-1, 1):
        s.blob((17 * x, 56, 0), (8, 7, 8), "hide")
        s.box((17 * x, 61, 0), (12, 3, 12), "bone", rot=(0, 0, -14 * x))          # shoulder plates of bone
    # Fur mantle over the shoulders and a ragged cloak down the back.
    s.rbox((0, 59, 5), (38, 7, 15), "fur", c=2)
    s.part("cape").plate((0, 38, 12), (0, 0.1, 1), (0, 1, 0), 34, 40, 3, "cloak")
    # Head: heavy brow, glowing eyes, long hair, an icicle beard.
    h = s.part("head")
    h.rbox((0, 67, -2), (14, 14, 14), "skin", c=2)
    h.box((0, 71, -9.2), (15, 3, 2), "skin")
    for x in (-1, 1):
        h.box((3.6 * x, 68.2, -9.3), (3, 1, 1), "eye")
    h.box((0, 66, -9.6), (3, 4, 2), "skin")
    h.rbox((0, 62, 6), (15, 16, 5), "hair", c=1)
    for i, x in enumerate((-5.5, -3, -0.5, 2, 4.5, 6)):
        top = 61.5 if abs(x) > 4 else 60.5
        h.cone((x, top, -9.5), (x * 1.1, 47 + (i % 3) * 3.0, -12.5 - (i % 2)), 4.0, "icebeard", steps=3)
    # The helmet: a cap of whale bone over the head, a nose guard, tusks down the cheeks, two short horns.
    h.rbox((0, 75, -2), (17, 7, 17), "helmet", c=2)
    h.box((0, 72.5, -10.5), (16, 3, 2), "helmet")
    h.box((0, 69, -10.8), (2, 6, 2), "helmet")
    for x in (-1, 1):
        h.cone((7.5 * x, 72, -8), (9 * x, 61, -13), 3, "bone", steps=2)
        h.curve([(7 * x, 78, 0), (11 * x, 82, 1), (13 * x, 85, -2)], 3, [4, 3, 2], "bone")
    # (The slabs of glacier that were on his shoulder and his back are gone: the user's note after round 2.)


# The axe held at the ready in both hands (the user, November 19, 2026: "instead of holding it with one hand, in a
# neutral state, make him hold it with an angle with two hands ... the sharp part needs to be facing toward where he's
# facing ... make the axe a little thicker"; it was held upright in his right fist, its blade out to his side): the haft
# across him at 45 degrees, his left fist low on it before his left hip, his right fist up before his right breast, the
# head up past his right shoulder with its blade pointing ahead.
AXE_L = np.array([10.0, 36.0, -16.0])       # (unscaled, the design's units before BIG)
AXE_R = np.array([-6.0, 52.0, -17.0])
AXE_BUTT = 10.0                             # the haft runs this far below the left fist ...
AXE_TOP = 65.0                              # ... and this far above it
AXE_BLADE = (35.0, 62.0)                    # the blade along it, from the left fist
SHOULDER_R = np.array([-18.0, 55.0, 0.0])
SHOULDER_L = np.array([18.0, 55.0, 0.0])


def elbow(S, W, la, lb, pole):
    """Where an elbow goes for the arm from shoulder S to wrist W (upper arm la, forearm lb), bending towards `pole`."""
    S, W = np.asarray(S, dtype=float), np.asarray(W, dtype=float)
    d = float(np.linalg.norm(W - S))
    u = (W - S) / d
    d = min(d, la + lb - 0.05)
    x = (la * la - lb * lb + d * d) / (2.0 * d)
    h = math.sqrt(max(la * la - x * x, 0.0))
    v = np.asarray(pole, dtype=float)
    v = v - u * float(np.dot(v, u))
    v = v / np.linalg.norm(v)
    return S + u * x + v * h


AXE_ELBOW_R = elbow(SHOULDER_R, AXE_R, 14.5, 13.0, (-1.0, -0.5, 0.3))
AXE_ELBOW_L = elbow(SHOULDER_L, AXE_L, 14.5, 13.0, (1.0, -0.4, 0.4))


def axe(s):
    body, h = s, s.part("hand_r")
    L = AXE_L
    u = (AXE_R - AXE_L) / np.linalg.norm(AXE_R - AXE_L)          # up the haft
    f = np.array([0.0, 0.0, -1.0])
    f = f - u * float(np.dot(f, u))
    f = f / np.linalg.norm(f)                                      # the way the blade points: ahead
    for side, S, E, F in ((-1, SHOULDER_R, AXE_ELBOW_R, AXE_R), (1, SHOULDER_L, AXE_ELBOW_L, AXE_L)):
        wrist = F + (E - F) / np.linalg.norm(E - F) * 1.0
        body.part("arm_r" if side < 0 else "arm_l").taper([S, E, wrist], [10, 9, 8], "skin")
        body.part("arm_r" if side < 0 else "arm_l").ring(E, (wrist - E) / np.linalg.norm(wrist - E), 4.6, 2, 3,
                                                          "leather", n=6)
        body.part("hand_r" if side < 0 else "hand_l").rbox(F, (8, 8, 8), "skin", c=2)
    h.seg(L - u * AXE_BUTT, L + u * AXE_TOP, 4, 4, "bone")
    for t in (-6.5, 30.5):
        h.ring(L + u * t, u, 2.8, 1, 2, "leather", n=6)
    # The blade: a crescent of clear ice pointing ahead, set in a whale's jawbone along the haft, its edge glowing (a
    # third thicker than it was).
    edge = []
    for k in range(9):
        t = k / 8.0
        sk = AXE_BLADE[0] + k * 3.0 + 1.5                          # strips 3 along the haft, touching (never overlapping)
        reach = round(9 + 9 * math.sin(math.pi * t) + 3 * t)       # how far the blade sticks out here
        p0 = L + u * sk + f * 1.5
        h.seg(p0, p0 + f * reach, 3, 3, "blade", side=u)
        edge.append(p0 + f * reach)
    h.curve([edge[0] - f * 2.0 - u * 3.0] + edge[::2] + [edge[-1] - f * 3.0 + u * 3.0], 8, [2, 3, 3, 3, 3, 3, 2],
            "edge", depths=[4] * 7, side=f)
    h.curve([L + u * 31.0, L + u * 43.0 + f * 2.5, L + u * 55.0 + f * 2.5, L + u * 65.0], 5, [4, 5, 5, 3], "bone",
            depths=[5, 5, 5, 4], side=f)
    for sk in (39.0, 45.0, 51.0, 57.0):                            # the jaw's teeth, gripping the ice
        h.seg(L + u * sk + f * 3.5, L + u * sk + f * 5.5, 2, 4, "bone", side=u)


def spear(s):
    # Right arm: the spear held upright beside him, its butt on the ground.
    s.part("arm_r").taper([(-18, 55, 0), (-25, 43, -4), (-21, 38, -14)], [10, 9, 8], "skin")
    hand = s.part("hand_r")
    hand.rbox((-21, 38, -15), (8, 8, 8), "skin", c=2)
    s, body = hand, s
    shaft0 = np.array([-21.0, 1.0, -17.0])
    shaft1 = np.array([-21.0, 92.0, -13.0])
    s.seg(shaft0, shaft1, 3, 3, "wood")
    d = (shaft1 - shaft0) / np.linalg.norm(shaft1 - shaft0)
    for t in (0.38, 0.47, 0.9):
        s.ring(shaft0 + (shaft1 - shaft0) * t, d, 2.2, 1, 2, "leather", n=6)
    # The head: a long leaf of clear ice bound on with bone, a fur tuft under it.
    s.cone(shaft1, shaft1 + d * 22, 7, "spearhead", w1=1, d0=3, d1=1, steps=5, side=(1, 0, 0))
    s.cone(shaft1 + d * 1, shaft1 - d * 4, 5, "bone", w1=4, steps=1)
    for k in range(5):
        a = 2 * math.pi * k / 5
        n = np.array([math.cos(a), 0.0, math.sin(a)])
        s.plate(shaft1 - d * 9 + n * 2.0, n, d, 4, 9, 1, "furflap")
    # Left arm: a round shield on the forearm, held across the front: a disc of the same clear ice as the axe's blade
    # and his spearhead, its rim of glowing edge-ice, bound across with two leather straps (the user, November 19, 2026:
    # "make the spearman's shield the same icy material the spear and the axeman's axe is made out of instead of this
    # white stone": it was pale hide under a bone rim and cross).
    body.part("arm_l").taper([(18, 55, 0), (25, 44, -8), (15, 42, -17)], [10, 9, 8], "skin")
    s = body.part("hand_l")
    s.rbox((14, 42, -18), (8, 8, 8), "skin", c=2)
    centre = np.array([13.0, 40.0, -24.0])
    n = np.array([0.25, 0.0, -1.0])
    n = n / np.linalg.norm(n)
    side = np.cross(n, [0.0, 1.0, 0.0])
    side = side / np.linalg.norm(side)
    up = np.cross(side, n)
    rad = 12.0
    for k in range(8):                            # the disc in strips 3 tall, touching (clear ice never overlapping)
        yk = -rad + 1.5 + 3.0 * k
        w = 2.0 * math.sqrt(max(rad * rad - (abs(yk) + 0.8) ** 2, 4.0))
        s.plate(centre + up * yk, n, up, round(w), 3, 2, "blade")
    s.ring(centre + n * 0.4, n, rad + 0.6, 2, 3, "edge", n=12)
    s.rbox(centre + n * 1.8, (5, 5, 2), "edge", c=1, rot=None)
    for a in (0.35, 0.35 + math.pi / 2):          # two leather straps bound across it
        dirv = side * math.cos(a) + up * math.sin(a)
        s.seg(centre + n * 1.4 - dirv * 10.5, centre + n * 1.4 + dirv * 10.5, 2, 1, "leather", side=np.cross(dirv, n))
    # (The chunk of glacier ice stuck on the shield is gone too: no stray ice on the soldiers.)


def build(s, v):
    S = Scaled(s, v["k"])
    body(S)
    (axe if v["weapon"] == "axe" else spear)(S)


def materials(v):
    return {
        "skin": M.skin(M.FROST_SKIN, seed=401, mottle=1.0, frost_amount=0.25, cracks=0.12, scars=0.2),
        "hide": M.skin(M.WALRUS, seed=403, mottle=0.8, frost_amount=0.2),
        "leather": M.skin(M.LEATHER, seed=405, mottle=0.6, frost_amount=0.1),
        "fur": M.fur(M.ELK, seed=407, strand=3.0, frost_amount=0.45),
        "furflap": M.shag(M.ELK, seed=409, ragged=0.7, frost_amount=0.3),
        "cloak": M.cloth(M.ELK, seed=411, folds=1.2, ragged=0.7, frost_amount=0.3),
        "hair": M.fur(M.CLOTH_PALE, seed=413, strand=3.0, frost_amount=0.3),
        "icebeard": M.solid(M.ICE, idx=3.5, seed=415, grain=1.0, bev=0.9, outline=0.06),
        "bone": M.bone(seed=417, frost_amount=0.15),
        "helmet": M.bone(M.BONE, seed=418, frost_amount=0.3, cracks=0.3),
        "rope": M.solid(M.ROPE, seed=419),
        "eye": M.eye(M.GLOW, pupil=False),
        "glacier": M.ice(M.ICE, alpha=(120, 185), seed=421, cracks=0.45, bubbles=0.02),
        "edge": M.ice(M.ICE, alpha=(170, 225), seed=423, cracks=0.2, glow_pal=M.GLOW, glow_alpha=150),
        "blade": M.ice(M.ICE, alpha=(120, 180), seed=425, cracks=0.5, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=40),
        "spearhead": M.ice(M.ICE, alpha=(160, 220), seed=427, cracks=0.35, glow_pal=M.GLOW, glow_alpha=70,
                           crack_glow=0.8),
        "wood": M.wood(M.WOOD, seed=429, plank=2.0, axis=0, frost_amount=0.2),
        "shield": M.skin(M.MUZZLE, seed=431, mottle=1.2, frost_amount=0.35, scars=0.3),
    }
