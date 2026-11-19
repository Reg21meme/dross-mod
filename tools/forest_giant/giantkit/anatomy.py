"""
The giant's body, shared by every look (they're one people, so they share it): its legs and bare feet, its pelvis and
gut, its hunched chest, its thick neck, its head (a heavy scowling brow over two deep-set eyes that glow electric blue,
a broad nose, a jutting jaw), its long arms and big fists. Each part goes on its bone (rig.py) through `s.part(name)`;
a look dresses it (hair, armour, bark) and leaves out what it covers (`skip`).

Material names used here (each look's materials() paints them): "skin", "nail", "mouth", "socket", "eye"; a look may
give a part another skin material (the `skin` arguments: its painted arms, its scarred chest).

Every number is a labelled constant or an offset from the joints in rig.py, in Bedrock units (16 a block).
"""
import numpy as np

from rimekit.shapes import unit, v3

from . import rig

# Legs
THIGH_WIDTHS = (13.0, 10.0)           # at the hip, at the knee
SHIN_WIDTHS = (9.5, 7.5)              # at the knee, at the ankle
CALF = (4.2, 5.0, 3.6)                # the calf's radii (behind the upper shin)
FOOT = (10.0, 5.0, 12.0)              # heel to the ball of the foot: wide, tall at the instep, long
TOES = ((-3.4, 3.0, 4.0), (-0.6, 2.0, 3.6), (1.7, 2.0, 3.2), (3.8, 2.0, 2.8))   # (across from its middle, width,
                                      # length) from the big toe (inner side) to the little one

# Body
PELVIS = ((0.0, 31.0, 0.5), (12.5, 7.0, 10.0))
GLUTES = ((0.0, 29.5, 5.0), (11.0, 6.0, 6.5))
LOWER_BELLY = ((0.0, 34.0, -4.5), (10.5, 5.5, 6.5))
# Chest (upright, before the hunch)
RIBCAGE = ((0.0, 46.5, 1.5), (13.5, 10.5, 10.5))
GUT = ((0.0, 39.0, -3.5), (12.0, 7.0, 9.0))
PEC = ((6.4, 48.0, -7.4), (12.0, 8.0, 5.0))
HUMP = ((0.0, 54.0, 4.5), (12.0, 5.0, 8.5))
LAT = ((9.0, 45.5, 4.0), (4.5, 8.5, 7.0))

# Arms
UPPER_ARM_WIDTHS = (10.0, 8.5)
FOREARM_WIDTHS = (8.5, 9.5)           # its forearms thicken towards the wrist
DELTOID = (6.0, 6.0, 6.5)

# The face (offsets from the head's middle)
EYE_SPREAD = 3.2                      # each eye this far out from the middle
EYE_SIZE = (3, 2, 1)                  # wide, tall, deep (units): small, deep-set and fierce


def legs(s, J, skin="skin", nail="nail", feet="bare", skip=()):
    for sign, side in ((1.0, "l"), (-1.0, "r")):
        hip, knee, ankle = J[f"hip_{side}"], J[f"knee_{side}"], J[f"ankle_{side}"]
        thigh, shin, foot = s.part(f"leg_{side}"), s.part(f"shin_{side}"), s.part(f"foot_{side}")
        if "thigh" not in skip:
            thigh.tube([hip + v3(0.0, 3.0, 0.0), knee], list(THIGH_WIDTHS), skin)
            thigh.blob(hip + v3(-sign * 1.5, -4.0, -0.5), (5.5, 6.0, 6.0), skin, slices=3)   # the inner thigh's bulk
        if "shin" not in skip:
            shin.tube([knee, ankle], list(SHIN_WIDTHS), skin)
            shin.blob(knee + v3(0.0, -4.5, 3.2), CALF, skin, slices=3)
            shin.rbox(knee + v3(0.0, 0.0, -3.6), (6, 5, 3), skin, c=1)                          # the kneecap
        if "foot" not in skip:
            foot_(foot, ankle, sign, skin, nail, feet)


def foot_(f, ankle, sign, skin="skin", nail="nail", feet="bare"):
    """A giant's foot: a broad heel and instep, five stubby toes (the big toe on the inner side), toenails; "sandal":
    with a leather sole and straps."""
    x = ankle[0]
    fw, fh, fl = FOOT
    f.rbox((x, fh / 2.0, ankle[2] - 2.5), (fw, fh, fl), skin, c=1)
    f.rbox((x, fh + 0.5, ankle[2] - 0.5), (fw - 3.0, 3, 7), skin, c=1)                  # the instep, under the ankle
    f.box((x + sign * 4.6, 3.6, ankle[2] - 0.3), (2, 2, 2), skin)                       # the ankle bones
    f.box((x - sign * 4.6, 3.6, ankle[2] - 0.3), (2, 2, 2), skin)
    front = ankle[2] - 2.5 - fl / 2.0
    for across, w, length in TOES:
        cx = x - sign * across
        h = 3.0 if w >= 3.0 else 2.4
        f.box((cx, h / 2.0 + 0.4, front - length / 2.0 + 0.6), (w, h, length), skin)
        f.box((cx, h + 0.7, front - length + 1.4), (w, 1, 1.6), nail)
    if feet == "sandal":
        f.box((x, 0.5, ankle[2] - 4.0), (fw + 1, 1, fl + 6), "sandal")                  # the sole, under the toes
        for z in (-6.5, -1.5, 3.0):
            f.box((x, 2.8 if z < 0 else 3.4, ankle[2] + z), (fw + 1, 1, 2), "sandal")
        f.box((x, 6.0, ankle[2] + 0.5), (fw - 1, 1, 9), "sandal")


def pelvis(s, J, skin="skin"):
    b = s.part("body")
    for c, r in (PELVIS, GLUTES, LOWER_BELLY):
        b.blob(c, r, skin)


def chest(s, J, skin="skin", pecs=True):
    T = rig.chest_frame(s.part("chest"))
    T.blob(*RIBCAGE, skin)
    T.blob(*GUT, skin)
    T.blob(*HUMP, skin)
    for sign in (1.0, -1.0):
        c, r = LAT
        T.blob(rig.mx(c, sign), r, skin, slices=4)
        if pecs:
            c, size = PEC
            T.rbox(rig.mx(c, sign), size, skin, c=2)
    return T


def neck(s, J, skin="skin"):
    h = s.part("head")
    T = rig.chest_frame()
    h.tube([T.P((0.0, 50.0, 1.0)), J["neck"] + v3(0.0, 4.0, -3.5)], [9.5, 10.0], skin)   # (slim at its foot: a raised
                                                                                    # arm's shoulder clears it)


# ---------------------------------------------------------------- the head

def head(s, J, skin="skin", skip=()):
    """
    The skull, a heavy brow drawn down into a scowl, a band of shadow (the sockets) with the two eyes on their own bone
    deep in it, the cheekbones, a broad nose, ears, the upper lip and the mouth; on the jaw's bone the lower jaw, the
    chin and the lower lip. `skip`: "skull" (a helm covers it), "ears", "brow", "cheeks". Returns P(x, y, z): a point from the
    head's middle.
    """
    Hc = J["head_c"]
    h = s.part("head")
    jaw = s.part("jaw")
    eyes = s.part("eyes")

    def P(x, y, z):
        return Hc + v3(x, y, z)

    if "skull" not in skip:
        h.rbox(P(0.0, 2.0, 1.5), (14, 14, 12), skin, c=2)
        h.blob(P(0.0, 3.0, 4.5), (6.5, 5.5, 4.5), skin, slices=3)                         # the back of the skull
        h.box(P(0.0, 6.6, -5.2), (14, 3, 3), skin)                                       # the low forehead
    if "brow" not in skip:
        for sign in (1.0, -1.0):                                                         # the brow, a scowl's V
            h.seg(P(sign * 0.3, 4.4, -7.0), P(sign * 7.3, 5.8, -5.9), 3, 3, skin, side=(0.0, 1.0, 0.0), ext=0.7,
                  inflate=0.05 * (sign > 0))
    h.box(P(0.0, 2.0, -5.4), (12, 3, 2), "socket")                                       # the shadow round the eyes
    for sign in (1.0, -1.0):
        eyes.box(P(sign * EYE_SPREAD, 2.0, -6.55), EYE_SIZE, "eye")
        h.rbox(P(sign * 6.4, 1.8, -5.3), (2, 5, 3), skin, c=0)                           # the flesh beside the eyes
        if "cheeks" not in skip:
            h.rbox(P(sign * 4.4, -0.9, -5.7), (5, 3, 3), skin, c=1)                      # the cheekbones
        if "ears" not in skip:
            h.rbox(P(sign * 7.7, 1.0, 1.4), (2, 5, 4), skin, c=1)
            h.box(P(sign * 8.1, -1.8, 1.4), (1, 2, 2), skin)
    h.box(P(0.0, 1.8, -6.6), (2, 3, 2), skin)                                            # the bridge of the nose
    h.rbox(P(0.0, -2.4, -7.4), (4, 5, 3), skin, c=1)                                     # the broad nose
    h.rbox(P(0.0, -4.5, -7.1), (6, 2, 3), skin, c=0)                                     # its wings
    for sign in (1.0, -1.0):
        h.box(P(sign * 1.3, -5.0, -8.1), (1, 1, 1), "mouth")                             # nostrils
    h.rbox(P(0.0, -6.4, -5.8), (11, 2, 4), skin, c=1)                                    # the upper lip
    h.box(P(0.0, -7.7, -5.2), (8, 1, 2), "mouth")                                      # (set back between the lips)
    jaw.rbox(P(0.0, -9.7, -3.6), (13, 4, 8), skin, c=1)                                  # the jaw, jutting
    jaw.rbox(P(0.0, -10.5, -7.0), (8, 4, 3), skin, c=1)
    jaw.box(P(0.0, -8.5, -6.9), (7, 1, 2), skin)
    return P


# ---------------------------------------------------------------- arms and fists

def arms(s, J, skin="skin", skip=()):
    """`skin`: one material, or a pair (upper arms, forearms)."""
    up_mat, fo_mat = (skin, skin) if isinstance(skin, str) else skin
    for sign, side in ((1.0, "l"), (-1.0, "r")):
        sh, el, wr = J[f"shoulder_{side}"], J[f"elbow_{side}"], J[f"wrist_{side}"]
        up, fo = s.part(f"arm_{side}"), s.part(f"fore_{side}")
        if "upper" not in skip:
            up.tube([sh + v3(0.0, 1.5, 0.0), el], list(UPPER_ARM_WIDTHS), up_mat)
            up.blob(sh + v3(sign * 1.0, 0.5, 0.0), DELTOID, up_mat, slices=3)
        if "fore" not in skip:
            fo.blob(el, (4.6, 4.6, 4.6), fo_mat, slices=2)                                # the elbow
            fo.tube([el, wr], list(FOREARM_WIDTHS), fo_mat)
        if "fist" not in skip:
            fist(s.part(f"hand_{side}"), J, side, "skin")


def fist(h, J, side, skin="skin"):
    """A big closed fist round the grip's axis (what it holds runs through it), knuckles along its outer edge, a thumb
    over whatever it grips."""
    _w, down, c, g = J.hand(side)
    sign = 1.0 if side == "l" else -1.0
    n = unit(np.cross(down, g))
    if n[0] * sign < 0:
        n = -n                                       # (the back of the hand faces out, away from the body)
    L = rig.FIST
    h.seg(c - g * (L / 2.0), c + g * (L / 2.0), L, L, skin, side=down)
    for k in range(4):
        p = c + down * 3.3 + n * 2.6 + g * (-2.7 + 1.8 * k)
        h.seg(p - g * 0.8, p + g * 0.8, 2.2, 2.2, skin, side=down, inflate=0.05 * (k % 2))
    h.seg(c + g * 3.2 - n * 2.4 - down * 1.0, c + g * 3.2 - n * 2.4 + down * 2.8, 3, 3, skin, side=g)   # the thumb
    return c, g, down, n
