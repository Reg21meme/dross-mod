"""
The Silverwood Giant's skeleton: where its joints are in its standing rest pose, its bones, and its legs for the
Rimefall kit's IK (rimekit/mobkit.py: Leg, MobRig), so the next stage can animate it the way the Rimefall animals and
the Pallbearer are animated.

Units are Bedrock model units (16 a block): y up, the feet on y = 0, its FRONT faces -z, its own LEFT is +x.

The rest pose (every look's bind pose: the boxes are built where they sit in it, and every bone's rest rotation is
zero): it stands with its weight on both feet, the knees a little bent, its chest hunched forward (HUNCH) and its head
pushed out in front of its shoulders, its long arms hanging. A look moves an arm with `arm_to` (a boulder on its
shoulder, a spear held upright, a shield on its forearm).

Bones (parents first):
  root
    body              the pelvis and belly (pivot at the hips); the legs hang from it
      chest           the ribcage, back and shoulders (pivot in the small of its back)
        head          the skull, brow, nose, ears, hair, a helm (pivot at the base of its neck)
          jaw         the lower jaw and anything on it (a beard), pivot at the jaw's hinge
          eyes        its two glowing eyes, pivot between them (they can flare, narrow, blink)
        arm_l/r       the upper arm and shoulder cap, pivot at the shoulder
          fore_l/r    the forearm, pivot at the elbow
            hand_l/r  the fist, pivot at the wrist
              weapon  (right hand) what it fights with, pivot at the grip in the fist
        pad_l/r       shoulder armour, bark plates, moss: pivot at the shoulder, parent chest, so an animation can let
                      it follow only part of the arm's swing (the Cinder Colossus's lesson: pads on the arm itself
                      slide through the chest when it swings; kit/anims.py Rig.follow)
      leg_l/r         the thigh, pivot at the hip
        shin_l/r      pivot at the knee
          foot_l/r    pivot at the ankle (the IK keeps the sole flat)
  and each look's own extra bones (`EXTRA_BONES`: a kilt's flaps, a shield, a spear on its back, a cloak).
"""
import math

import numpy as np

from firekit.parts import Xform
from rimekit.mobkit import Leg
from rimekit.shapes import unit, v3

HUNCH = -14.0                         # degrees its chest leans forward about CHEST_PIVOT (negative: forward)
BODY_PIVOT = v3(0.0, 27.0, 1.0)       # the hips: the body bone (pelvis, belly) and the legs turn about here
CHEST_PIVOT = v3(0.0, 37.0, 2.0)      # the small of its back: the chest bone turns about here

# Legs (its left; the right mirrors x): hip, knee, ankle, and the middle of the sole on the ground.
HIP = v3(7.5, 27.0, 1.5)
KNEE = v3(8.0, 14.5, -1.0)            # the knees a little bent and forward: a heavy stance
ANKLE = v3(8.5, 4.5, 1.5)
SOLE = v3(8.5, 0.0, -2.0)

# The chest's points, given upright (before the hunch) and turned into place by chest_frame().
NECK_UP = v3(0.0, 54.0, -0.5)
SHOULDER_UP = v3(14.0, 52.0, 1.5)
HEAD_FROM_NECK = v3(0.0, 8.0, -6.5)   # the head's middle from the base of the neck: pushed out in front
JAW_FROM_HEAD = v3(0.0, -4.0, 2.0)    # the jaw's hinge from the head's middle
EYES_FROM_HEAD = v3(0.0, 1.6, -6.2)   # the middle between its eyes, from the head's middle

# Arms (its left): the elbow and the wrist from the shoulder in the rest pose (hanging, a little out, fists forward).
ELBOW_FROM_SHOULDER = v3(3.2, -14.5, 1.5)
WRIST_FROM_ELBOW = v3(0.4, -13.5, -3.0)
FIST = 8.0                            # a fist's size (units, along the grip), its depth and height a unit less
WRIST_TILT = 35.0                     # degrees a fist's grip may tilt off square to its forearm
UPPER_ARM = float(np.linalg.norm(ELBOW_FROM_SHOULDER))
FOREARM = float(np.linalg.norm(WRIST_FROM_ELBOW))


def chest_frame(s=None):
    """The chest's frame: a Shape (or None) seen through the hunch, so the chest is built upright and leans."""
    return Xform(s, pivot=CHEST_PIVOT, axis=(1.0, 0.0, 0.0), deg=HUNCH)


def mx(p, sign):
    q = v3(p)
    return np.array([q[0] * sign, q[1], q[2]])


def two_bone(root, target, l1, l2, pole):
    """Where the middle joint of a two-bone limb goes so its end reaches `target` (as near as it can), bending
    towards `pole` (a direction)."""
    root = v3(root)
    target = v3(target)
    d = target - root
    dist = float(np.linalg.norm(d))
    dist = min(max(dist, abs(l1 - l2) + 1e-3), l1 + l2 - 1e-3)
    dn = unit(d)
    a = (l1 * l1 - l2 * l2 + dist * dist) / (2.0 * dist)
    h = math.sqrt(max(0.0, l1 * l1 - a * a))
    p = v3(pole) - np.dot(v3(pole), dn) * dn
    p = unit(p)
    return root + dn * a + p * h


class Joints(dict):
    """The rest pose's joints (name -> point), and the hands' frames (where each fist is and which way it grips)."""

    def hand(self, side):
        """(wrist, down along the forearm, the fist's middle, the grip's axis) of one hand."""
        w = self[f"wrist_{side}"]
        down = unit(w - self[f"elbow_{side}"])
        g = self.get(f"grip_axis_{side}")
        if g is None:
            fwd = v3(0.0, 0.0, -1.0)
            g = unit(fwd - np.dot(fwd, down) * down)
        # the fist can tilt its grip at most WRIST_TILT degrees off square to the forearm (a wrist's bend)
        g = v3(g)
        along = float(np.clip(np.dot(g, down), -1.0, 1.0))
        flat = unit(g - along * down)
        tilt = math.radians(float(np.clip(math.degrees(math.asin(along)), -WRIST_TILT, WRIST_TILT)))
        g = unit(flat * math.cos(tilt) + down * math.sin(tilt))
        centre = w + down * (FIST / 2.0)
        return w, down, centre, g


def joints():
    """The default rest pose."""
    T = chest_frame()
    J = Joints()
    J["body"] = BODY_PIVOT
    J["chest"] = CHEST_PIVOT
    J["neck"] = T.P(NECK_UP)
    J["head_c"] = J["neck"] + HEAD_FROM_NECK
    J["jaw"] = J["head_c"] + JAW_FROM_HEAD
    J["eyes"] = J["head_c"] + EYES_FROM_HEAD
    for sign, side in ((1.0, "l"), (-1.0, "r")):
        sh = T.P(mx(SHOULDER_UP, sign))
        el = sh + mx(ELBOW_FROM_SHOULDER, sign)
        J[f"shoulder_{side}"] = sh
        J[f"elbow_{side}"] = el
        J[f"wrist_{side}"] = el + mx(WRIST_FROM_ELBOW, sign)
        J[f"hip_{side}"] = mx(HIP, sign)
        J[f"knee_{side}"] = mx(KNEE, sign)
        J[f"ankle_{side}"] = mx(ANKLE, sign)
        J[f"sole_{side}"] = mx(SOLE, sign)
    return J


def arm_to(J, side, wrist, pole, grip_axis=None):
    """Moves one arm so its wrist is at `wrist` (the same bone lengths), its elbow bending towards `pole`."""
    sh = J[f"shoulder_{side}"]
    el = two_bone(sh, wrist, UPPER_ARM, FOREARM, pole)
    J[f"elbow_{side}"] = el
    J[f"wrist_{side}"] = el + unit(v3(wrist) - el) * FOREARM
    if grip_axis is not None:
        J[f"grip_axis_{side}"] = unit(grip_axis)
    return J


def bones(J, extra=()):
    """[(bone, pivot, parent)], parents first (rimekit/mobkit.py's BONES)."""
    _w, _d, grip, _g = J.hand("r")
    out = [("root", (0.0, 0.0, 0.0), None),
           ("body", tuple(J["body"]), "root"),
           ("chest", tuple(J["chest"]), "body"),
           ("head", tuple(J["neck"]), "chest"),
           ("jaw", tuple(J["jaw"]), "head"),
           ("eyes", tuple(J["eyes"]), "head")]
    for side in ("l", "r"):
        out += [(f"arm_{side}", tuple(J[f"shoulder_{side}"]), "chest"),
                (f"fore_{side}", tuple(J[f"elbow_{side}"]), f"arm_{side}"),
                (f"hand_{side}", tuple(J[f"wrist_{side}"]), f"fore_{side}"),
                (f"pad_{side}", tuple(J[f"shoulder_{side}"]), "chest")]
    out.append(("weapon", tuple(J.get("weapon_pivot", grip)), "hand_r"))
    for side in ("l", "r"):
        out += [(f"leg_{side}", tuple(J[f"hip_{side}"]), "body"),
                (f"shin_{side}", tuple(J[f"knee_{side}"]), f"leg_{side}"),
                (f"foot_{side}", tuple(J[f"ankle_{side}"]), f"shin_{side}")]
    out += [(name, tuple(v3(pivot)), parent) for name, pivot, parent in extra]
    return out


def legs(J):
    """The Rimefall kit's IK legs (rimekit/mobkit.py Leg): thigh, shin and foot (its sole kept flat)."""
    return [Leg(f"leg_{side}", f"leg_{side}", f"shin_{side}", tuple(J[f"sole_{side}"]), bend=1, hoof=f"foot_{side}")
            for side in ("l", "r")]
