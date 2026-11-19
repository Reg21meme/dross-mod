"""
A knight's body for the elemental knights, about 4 blocks to the top of the helm (like the Thawed Soldiers, 9C and 9D,
and Sir Mildew, No. 13 of the Blightwood candidates), on the Rimefall kit's Shape: Bedrock units (16 a block), y up from
the feet, the FRONT faces -z, the model's own LEFT is +x.

Every design builds its own knight from these pieces, so they share their proportions while their armour, helms and
weapons are their own:

  legs        greaves with knee cops and broad sabatons
  hips        the hip block and a skirt of plates (faulds), or a cloth skirt
  torso       the breastplate, a belt and the gorget
  pauldrons   layered shoulder plates
  arms        one of the poses below, with gauntlets
  cape        a cape down the back in two stretches
  shields and hafts

Each piece goes on its part (s.part(...)): "leg_l"/"leg_r", "waist" (hips and skirt), "body" (torso, pauldrons),
"arm_l"/"arm_r" (the arm), "hand_l"/"hand_r" (the fist; a design puts what that hand holds there too), "cape", and the
designs' helms on "head". A statue's Shape puts every part on its one bone (nothing changes); the moving knights
(tools/knights/mobs.py, November 13, 2026) route each part to a bone of its own.

The poses (where the hands are, so the weapons line up with them):

  planted     both hands on the pommel of a weapon planted point-down in front (GRIP)
  upright     the right hand holds a polearm or staff upright beside him (RIGHT_FIST)
  shoulder    the right hand holds a heavy weapon resting on his right shoulder (SHOULDER_FIST)
  shield      the left arm holds a shield across his front (SHIELD_CENTRE, SHIELD_NORMAL)
  hang        the left arm hangs, the fist by his thigh (HANG_FIST): a censer's chain, a lantern
  raise       the left hand held up in front of him (RAISE_FIST): an orb, a flame, a spell
"""
import math

import numpy as np

from firekit.parts import leg
from rimekit.shapes import unit, v3

# ---------------------------------------------------------------- the skeleton (units)

HIP_Y = 30.0
KNEE_Y = 16.0
ANKLE_Y = 4.0
LEG_X = 6.2
WAIST_Y = 33.5
CHEST = np.array([0.0, 43.0, 0.0])
SHOULDER_Y = 50.0
SHOULDER_X = 14.5
NECK_Y = 53.0
HEAD = np.array([0.0, 58.5, -0.5])          # the helm's middle; a 14-unit helm tops out about 4 blocks up

GRIP = np.array([0.0, 36.0, -16.0])         # the planted weapon's pommel
RIGHT_FIST = np.array([-23.0, 36.0, -14.0])
SHOULDER_FIST = np.array([-18.0, 42.0, -12.0])
SHOULDER_REST = np.array([-12.5, 66.0, 13.0])  # the far end of a weapon resting on the right shoulder points here
SHIELD_CENTRE = np.array([14.0, 35.0, -24.0])
SHIELD_NORMAL = unit(np.array([0.25, 0.0, -1.0]))
HANG_FIST = np.array([21.5, 29.0, -6.0])
RAISE_FIST = np.array([17.0, 47.0, -20.0])


def shoulder(sign):
    return np.array([sign * SHOULDER_X, SHOULDER_Y - 1.0, 0.0])


# ---------------------------------------------------------------- body pieces


def legs(s, armor, plate, boot=None, width=1.0, thick=1.0):
    """Greaves from the hips to the ankles, knee cops (the `plate` joints), broad sabatons."""
    for sign in (1.0, -1.0):
        g = s.part(side_part("leg", sign))
        hip = np.array([sign * LEG_X * width, HIP_Y, 0.0])
        knee = np.array([sign * (LEG_X + 0.5) * width, KNEE_Y, -1.5])
        ankle = np.array([sign * (LEG_X + 0.5) * width, ANKLE_Y, 0.0])
        leg(g, [hip, knee, ankle], [9.0 * thick, 7.5 * thick, 6.5 * thick], armor, joint_mat=plate, joint_scale=1.3)
        g.rbox((sign * (LEG_X + 0.5) * width, 2.0, -2.5), (8, 4, 13), boot or plate, c=1)


def side_part(name, sign):
    """"leg_l"/"leg_r", "arm_l"... : a knight's own left is +x."""
    return f"{name}_{'l' if sign > 0 else 'r'}"


def hips(s, mat, w=24, d=15):
    s.part("waist").rbox((0.0, HIP_Y + 1.0, 0.0), (w, 7, d), mat, c=2)


def faulds(s, mat, n=9, y=26.0, rx=13.0, rz=9.0, w=9, h=10, front_gap=False):
    """A skirt of overlapping plates round the hips (each a little longer than its neighbour, so none flicker)."""
    s = s.part("waist")
    for k in range(n):
        a = 2.0 * math.pi * (k + 0.5) / n
        if front_gap and abs(math.sin(a)) < 0.35 and math.cos(a) > 0:
            continue
        c = np.array([math.sin(a) * rx, y - (k % 2) * 0.6, -math.cos(a) * rz])
        s.plate(c, (math.sin(a), -0.25, -math.cos(a)), (0, 1, 0), w, h + (k % 3) * 0.5, 2, mat)


def cloth_skirt(s, mat, n=10, y=24.0, rx=12.5, rz=8.5, w=8, h=16):
    """A cloth skirt or coat tails hanging from the belt in strips (ragged cloth shows ragged hems)."""
    s = s.part("waist")
    for k in range(n):
        a = 2.0 * math.pi * (k + 0.5) / n
        c = np.array([math.sin(a) * rx, y - (k % 3) * 0.7, -math.cos(a) * rz])
        s.plate(c, (math.sin(a), -0.12, -math.cos(a)), (0, 1, 0), w, h + (k % 2), 2, mat)


def torso(s, armor, plate, bulk=1.0, belt=None, buckle=None):
    """The breastplate (a rounded chest over a narrower waist), the gorget at its top, a belt round the waist."""
    s = s.part("body")
    s.blob(CHEST, (12.5 * bulk, 10.0, 9.0 * bulk), armor, slices=5)
    s.blob(CHEST + np.array([0.0, -7.0, 0.0]), (10.0 * bulk, 4.0, 7.5 * bulk), armor, slices=2)
    s.seg((0.0, 48.0, 0.0), (0.0, NECK_Y + 1.5, -0.3), 10, 9, plate)
    if belt:
        s.ring((0.0, WAIST_Y, 0.0), (0, 1, 0), 9.0 * bulk, 2, 3, belt, n=12)
        if buckle:
            s.box((0.0, WAIST_Y, -9.0 * bulk - 1.0), (5, 4, 2), buckle)


def pauldrons(s, mat, under=None, size=1.0, lift=0.0):
    """Shoulder plates: a dome over each shoulder and two lames stepping down under it."""
    s = s.part("body")
    for sign in (1.0, -1.0):
        p = np.array([sign * (SHOULDER_X - 1.0), SHOULDER_Y + 1.5 + lift, 0.0])
        s.blob(p, (7.0 * size, 5.0 * size, 7.5 * size), mat, slices=3)
        for k in range(2):
            s.rbox(p + np.array([sign * 0.8 * (k + 1), -3.5 - 2.6 * k, 0.0]),
                   (round(14.0 * size) - k, 3, round(15.0 * size) - k), under or mat, c=2)


def arm(s, sign, elbow, fist, armor, plate, gauntlet=None, fist_size=5):
    """One arm from its shoulder through `elbow` to `fist`, with a couter at the elbow and a gauntlet (the fist on its
    hand's part)."""
    leg(s.part(side_part("arm", sign)), [shoulder(sign), elbow, fist], [7.0, 6.0, 5.0], armor, joint_mat=plate,
        joint_scale=1.25)
    s.part(side_part("hand", sign)).rbox(fist, (fist_size, fist_size, fist_size), gauntlet or plate, c=1)


def arms_planted(s, armor, plate, gauntlet=None, grip=GRIP):
    for sign in (1.0, -1.0):
        arm(s, sign, np.array([sign * 12.5, 41.0, -9.0]), grip + np.array([sign * 1.8, -0.5 + sign * 2.0, 0.0]),
            armor, plate, gauntlet)


def arm_upright(s, armor, plate, gauntlet=None, fist=RIGHT_FIST):
    arm(s, -1.0, np.array([-22.0, 42.0, -4.0]), fist, armor, plate, gauntlet)


def arm_shoulder(s, armor, plate, gauntlet=None, fist=SHOULDER_FIST):
    arm(s, -1.0, np.array([-23.0, 42.0, -3.0]), fist, armor, plate, gauntlet)


def arm_shield(s, armor, plate, gauntlet=None):
    arm(s, 1.0, np.array([24.0, 42.0, -8.0]), np.array([15.0, 38.0, -19.0]), armor, plate, gauntlet)


def arm_hang(s, armor, plate, gauntlet=None, fist=HANG_FIST):
    arm(s, 1.0, np.array([21.0, 40.0, -2.0]), fist, armor, plate, gauntlet)


def arm_raise(s, armor, plate, gauntlet=None, fist=RAISE_FIST):
    arm(s, 1.0, np.array([23.0, 43.0, -9.0]), fist, armor, plate, gauntlet)


def cape(s, mat, top=50.0, width=28, length=46, flare=6, back=10.0, clasp=None, lean=6.0, hem=None, hem_h=12):
    """
    A cape down the back: one stretch from the shoulders, leaning back `lean` units at its foot, and (`hem`) a strip of
    another material along its bottom `hem_h` units (a burnt or ragged hem), a little wider (`flare`).
    """
    s = s.part("cape")
    body_h = length - (hem_h if hem else 0)
    tilt = np.array([0.0, -length, lean])
    d = unit(tilt)
    n = unit(np.cross(np.array([1.0, 0.0, 0.0]), d))
    if n[2] < 0:
        n = -n
    top_pt = np.array([0.0, top, back])
    s.plate(top_pt + d * (body_h / 2.0), n, -d, width, body_h + 1, 2, mat)
    if hem:
        s.plate(top_pt + d * (body_h + hem_h / 2.0), n, -d, width + flare, hem_h + 1, 2, hem, inflate=0.05)
    # A yoke along its top edge, tying it to the shoulders (so it never floats off a narrow back).
    s.box((0.0, top - 1.5, back - 2.5), (max(8, width - 8), 3, 6), mat, inflate=0.15)
    if clasp:
        for sign in (1.0, -1.0):
            s.box((sign * 10.0, top - 1.0, back - 2.0), (4, 4, 4), clasp)


# ---------------------------------------------------------------- shields and hafts


def shield_frame(normal=SHIELD_NORMAL):
    n = unit(normal)
    side = unit(np.cross(n, [0.0, 1.0, 0.0]))
    up = np.cross(side, n)
    return n, side, up


def heater_shield(s, centre, mat, rim, w=22, h=28, normal=SHIELD_NORMAL, boss=None):
    """A heater shield (flat top, sides curving to a point at the bottom), its rim, an optional boss."""
    n, side, up = shield_frame(normal)
    c = v3(centre)
    rows = 7
    for k in range(rows):
        t = (k + 0.5) / rows                     # 0 at the top
        wk = w * (1.0 if t < 0.45 else max(0.18, math.cos((t - 0.45) / 0.55 * math.pi / 2.0)))
        y = h / 2.0 - (k + 0.5) * h / rows
        s.plate(c + up * y, n, up, round(wk), round(h / rows) + 1, 2, mat, inflate=0.05 * (k % 2))
    s.seg(c + up * (h / 2.0) - side * (w / 2.0 + 0.5), c + up * (h / 2.0) + side * (w / 2.0 + 0.5), 2, 3, rim,
          side=n)
    for sign in (1.0, -1.0):
        pts = []
        for k in range(rows + 1):
            t = k / rows
            wk = w * (1.0 if t < 0.45 else max(0.0, math.cos((t - 0.45) / 0.55 * math.pi / 2.0)))
            pts.append(c + up * (h / 2.0 - t * h) + side * sign * (wk / 2.0 + 0.4) + n * 0.4)
        for k, (a, b) in enumerate(zip(pts[:-1], pts[1:])):
            s.seg(a, b, 2, 3, rim, side=n, ext=0.5, inflate=0.06 * (k % 2))
    if boss:
        s.blob(c + n * 1.5 + up * 2.0, (3.0, 3.0, 2.0), boss, axis=n, side=side, slices=2)
    return n, side, up


def round_shield(s, centre, mat, rim, r=12.0, normal=SHIELD_NORMAL, boss=None):
    n, side, up = shield_frame(normal)
    c = v3(centre)
    s.plate(c, n, up, round(r * 2.0), round(r * 1.4), 2, mat)
    s.plate(c, n, up, round(r * 1.4), round(r * 2.0), 2, mat, inflate=0.1)
    s.plate(c, n, unit(up + side), round(r * 1.45), round(r * 1.45), 2, mat, inflate=0.2)
    s.ring(c, n, r + 0.3, 2, 3, rim, n=14)
    if boss:
        s.blob(c + n * 2.0, (3.5, 3.5, 2.0), boss, axis=n, side=side, slices=2)
    return n, side, up


def haft(s, p0, p1, w, mat, d=None, side=None):
    s.seg(p0, p1, w, d if d is not None else w, mat, side=side)
    return unit(v3(p1) - v3(p0))


def eye_slit(s, centre, half_w, mat, glow, z_out=6.6, depth=2):
    """A visor slit across the front of a helm, two glowing eyes in it."""
    c = v3(centre)
    s.seg(c + np.array([-half_w, 0.0, -z_out]), c + np.array([half_w, 0.0, -z_out]), 1, depth, mat, side=(0, 1, 0))
    for sign in (1.0, -1.0):
        s.seg(c + np.array([sign * half_w * 0.48, 0.0, -z_out + 0.1]),
              c + np.array([sign * half_w * 0.48, 0.0, -z_out - 0.5]), 2, 1, glow, side=(1, 0, 0))


def horn(s, base, control, widths, mat):
    s.curve([v3(base)] + [v3(c) for c in control], max(3, len(control) * 2), widths, mat)


def lerp(a, b, t):
    return v3(a) + (v3(b) - v3(a)) * t


def zigzag(s, p0, p1, n, amp, mat, w=1.0, normal=(0.0, 0.0, 1.0), seed=0):
    """A bolt of lightning (or a crack) from p0 to p1: n straight pieces kinking `amp` units to either side in turn."""
    a = v3(p0)
    b = v3(p1)
    d = unit(b - a)
    side = unit(np.cross(d, v3(normal)))
    pts = [a]
    for k in range(1, n):
        t = k / n
        kink = amp * (1.0 if (k + seed) % 2 else -1.0) * (0.6 + 0.4 * ((k * 7 + seed) % 3) / 2.0)
        pts.append(a + (b - a) * t + side * kink)
    pts.append(b)
    for q0, q1 in zip(pts[:-1], pts[1:]):
        s.seg(q0, q1, w, w, mat, side=v3(normal), ext=0.4)
    return pts
