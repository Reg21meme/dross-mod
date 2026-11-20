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
BEAR = M.palette("#1a120d", "#251a13", "#33241a", "#433022", "#563e2c", "#6e5139")   # a cave bear's grizzled brown
BEAR_MUZZLE = M.palette("#3d2c1f", "#4f3a29", "#634a35", "#7a5c43", "#917055", "#a8876a")   # its paler muzzle


def body(s, v):
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
    # (the mantle and the cloak are his beast's hide: the bear's for the axeman, the Rimehorn's for the spearman)
    s.rbox((0, 59, 5), (38, 7, 15), "mantle", c=2)
    s.part("cape").plate((0, 38, 12), (0, 0.1, 1), (0, 1, 0), 34, 40, 3, "cloak")
    # Head: heavy brow, glowing eyes, long hair, an icicle beard.
    h = s.part("head")
    h.rbox((0, 67, -2), (14, 14, 14), "skin", c=2)
    h.box((0, 71, -9.2), (15, 3, 2), "skin")
    for x in (-1, 1):
        h.box((3.6 * x, 68.2, -9.3), (3, 1, 1), "eye")
    h.box((0, 66, -9.6), (3, 4, 2), "skin")
    for i, x in enumerate((-5.5, -3, -0.5, 2, 4.5, 6)):
        top = 61.5 if abs(x) > 4 else 60.5
        h.cone((x, top, -9.5), (x * 1.1, 47 + (i % 3) * 3.0, -12.5 - (i % 2)), 4.0, "icebeard", steps=3)
    (bear_head if v["weapon"] == "axe" else rimehorn_hat)(h)
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



# Each soldier wears the head of a beast he killed (the user, November 19, 2026: "the ice people look the same ... give
# the axe dude the bear head ... the axe goes through his horns, so just remove his helmet, give him a bear head
# instead, and for the spear dude a Rimehorn hat"; both wore the same bone helmet with tusks and two short horns, and
# the axe's haft went through those horns as he hauled it up out of his smash). The hide hangs down behind his head and
# frames his face; his own face, eyes and icicle beard show under the beast's jaw.


def hide(h, mat):
    """The beast's hide down the back of his head and down both sides of his face."""
    h.box((0, 64.5, 7.5), (17, 17, 3), mat)
    for x in (-1, 1):
        h.box((8.6 * x, 68.0, -1.5), (2, 10, 13), mat)


def bear_head(h):
    """
    9C: a great bear's head worn over his own: its broad skull on top of his, its snout jutting out over his brow with
    a black nose, its fangs hanging down in front of his forehead, little black eyes, round ears; its dark grizzled
    pelt framing his face and hanging down his back. Nothing sticks up past its ears (the axe's haft passes over his
    right shoulder in his smash).
    """
    h.rbox((0, 76.5, -1), (19, 8, 18), "bear", c=2)                 # its skull over his
    h.box((0, 72.8, -10.2), (17, 3, 3), "bear")                      # its brow over his face
    h.rbox((0, 75.0, -14.5), (11, 6, 9), "bear", c=1)               # its snout
    h.rbox((0, 74.2, -20.0), (8, 4.5, 4), "bearmuzzle", c=1)        # its pale muzzle
    h.box((0, 76.0, -22.4), (4, 2.5, 1), "nose")                     # its nose
    h.box((0, 72.3, -21.9), (6, 1, 1), "bearmouth")                  # the line of its lip
    for x in (-1, 1):
        h.cone((2.6 * x, 72.4, -21.0), (2.6 * x, 68.4, -21.4), 2.0, "fang", w1=0.7, steps=2)   # its fangs
        h.cone((5.0 * x, 72.2, -15.5), (5.0 * x, 69.8, -15.8), 1.5, "fang", w1=0.5, steps=1)
        h.box((3.8 * x, 78.2, -13.6), (2, 2, 1), "beareye")          # its eyes
        h.rbox((7.6 * x, 82.2, 0.5), (6, 5, 3), "bear", c=1)        # its round ears
        h.box((7.6 * x, 82.0, -1.2), (3, 2.6, 0.6), "bearinner")
    hide(h, "bearshag")


def rimehorn_hat(h):
    """
    9D: a Rimehorn's head worn as a helm: its shaggy pale skull over his head, its face running out over his brow, the
    great horn of clear ice rising off the top of its nose and sweeping up ahead of him, the short horn behind it on
    top, plain black eyes, small tufted ears; its frosted hide framing his face and hanging down his back.
    """
    h.rbox((0, 76.5, -1), (19, 8, 18), "rhinofur", c=2)             # its skull over his
    h.box((0, 72.8, -10.2), (17, 3, 3), "rhinofur")                  # its brow over his face
    # (its face runs out over the top of his head, above his own eyes: lower, it covered them, and its nostrils looked
    # like a second pair of eyes)
    h.seg((0, 79.0, -8.0), (0, 77.0, -18.5), 12, 7, "rhinoskin", side=(1, 0, 0))    # its face, out over his brow
    for x in (-1, 1):
        h.box((2.2 * x, 76.0, -18.7), (1.5, 0.8, 1.2), "rhinomouth")  # its nostrils: slits low on its muzzle
        h.box((9.6 * x, 78.0, -5.5), (1, 2, 2), "rhinoeye")          # its eyes, on the sides of its skull
        h.plate((8.6 * x, 81.4, 1.5), (x, 0.2, -0.3), (x * 0.3, 1, -0.1), 4, 6, 2, "rhinoshag")   # its ears
    # its horns: the great one off the top of its nose, the short one behind it
    h.curve([(0, 79.5, -15.0), (0, 84.0, -17.5), (0, 89.5, -18.5), (0, 95.0, -16.5)], 9, [6, 5, 3, 1.5], "horn",
            depths=[7, 6, 4, 1.5])
    h.curve([(0, 80.0, -6.0), (0, 83.5, -7.0), (0, 86.5, -6.5)], 5, [5, 3.5, 1.5], "horn", depths=[6, 4, 1.5])
    hide(h, "rhinoshag")

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
    body(S, v)
    (axe if v["weapon"] == "axe" else spear)(S)


def materials(v):
    bear = v["weapon"] == "axe"
    return {
        "skin": M.skin(M.FROST_SKIN, seed=401, mottle=1.0, frost_amount=0.25, cracks=0.12, scars=0.2),
        "hide": M.skin(M.WALRUS, seed=403, mottle=0.8, frost_amount=0.2),
        "leather": M.skin(M.LEATHER, seed=405, mottle=0.6, frost_amount=0.1),
        "fur": M.fur(M.ELK, seed=407, strand=3.0, frost_amount=0.45),
        "furflap": M.shag(M.ELK, seed=409, ragged=0.7, frost_amount=0.3),
        # (his beast's hide: the axeman's a dark grizzled bear's, the spearman's a frosted Rimehorn's)
        "mantle": M.fur(BEAR if bear else M.WOOLLY_PALE, seed=407, strand=3.4, frost_amount=0.3 if bear else 0.4),
        "cloak": M.shag(BEAR if bear else M.WOOLLY_PALE, seed=411, strand=1.0, ragged=0.9, frost_amount=0.3),
        "bear": M.fur(BEAR, seed=441, strand=3.4, frost_amount=0.3),
        "bearshag": M.shag(BEAR, seed=443, strand=1.0, ragged=0.8, frost_amount=0.25),
        "bearinner": M.skin(M.LEATHER, seed=445, mottle=0.6, frost_amount=0.05),
        "beareye": M.solid(M.MOUTH, idx=1.0, seed=447, grain=0.3, bev=0.2, outline=0.0),
        "bearmouth": M.solid(M.MOUTH, idx=1.5, seed=448, grain=0.3, bev=0.2, outline=0.0),
        "nose": M.solid(M.MOUTH, idx=2.0, seed=451, grain=0.5, bev=0.6, outline=0.05),
        "fang": M.bone(seed=449, frost_amount=0.1),
        "bearmuzzle": M.fur(BEAR_MUZZLE, seed=452, strand=2.0, frost_amount=0.15),
        "rhinofur": M.fur(M.WOOLLY_PALE, seed=453, strand=3.6, frost_amount=0.35),
        "rhinoshag": M.shag(M.WOOLLY_PALE, seed=455, strand=1.0, ragged=1.0, frost_amount=0.3),
        "rhinoskin": M.skin(M.FROST_SKIN, seed=457, mottle=0.8, frost_amount=0.2),
        "rhinomouth": M.mouth(),
        "rhinoeye": M.solid(M.MOUTH, idx=1.0, seed=459, grain=0.3, bev=0.2, outline=0.0),
        "horn": M.ice(M.ICE, alpha=(150, 215), seed=461, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=35),
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
