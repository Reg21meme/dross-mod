"""
The Huntsman's skeleton, shared by his two models (CLAUDE.md step 14, his fight): rimemobs/huntsman.py (on foot, as he
fights in stage 2) and rimemobs/huntsman_rider.py (in the Alpha Moose's saddle, stage 1). Both are the gallery's 6A
rider (rimedesigns/d06_huntsman.py) unchanged, his parts on bones of the same names, so one set of helpers moves both:

  root > hips > thigh_l/r > shin_l/r > boot_l/r
              > chest > head, cape, bow (on his back)
                      > arm_l > forearm_l > hand_l > bow_held, horn_held (shown only while he shoots or blows it)
                      > arm_r > forearm_r > hand_r > spear

His left is +x. In the saddle the model's floor (y 0) is the moose's: the rider stands on the moose's own ground, so in
the game he sits at the moose's feet' position and the saddle is where it is in the gallery.
"""
import math

import numpy as np

from rimedesigns import d06_huntsman as H
from rimekit.shapes import Scaled

K = H.K
PROPS = ("bow_held", "horn_held")   # hidden unless an animation shows them


class Design:
    """His rider parts only (the moose is its own mob), with the bow and horn he holds in some animations."""

    @staticmethod
    def build(s, v):
        H.rider(Scaled(s, K), v)

    @staticmethod
    def materials(v):
        return H.materials(v)


def variant(pose):
    return dict(letter="A" if pose == "ride" else "A1", mood="the Wild Hunt" if pose == "ride" else "on foot",
                mount="moose", parts=("rider",), pose=pose, props=True)


def joints(pose):
    """Every joint's position (design units, the model's own frame: standing, everything is STAND_DROP lower)."""
    drop = np.array([0.0, 0.0 if pose == "ride" else H.STAND_DROP, 0.0])
    fists = H.RIDE_FISTS if pose == "ride" else H.STAND_FISTS
    poles = ((-1.0, -0.2, 0.7), (1.0, -0.2, 0.7)) if pose == "ride" else ((-0.5, 0.0, 0.85), (0.3, 0.0, 1.0))
    j = {"hips": np.array([0.0, H.HIP_Y, 0.0]), "chest": np.array([0.0, 120.0, 0.0]),
         "head": np.array([0.0, 147.0, 1.0]), "cape": np.array([0.0, 148.0, 9.0])}
    for x, side, fist, pole in ((1, "l", fists[1], poles[1]), (-1, "r", fists[0], poles[0])):
        sh = H.SHOULDER * np.array([x, 1.0, 1.0])
        j[f"arm_{side}"] = sh
        j[f"forearm_{side}"] = H.elbow(sh, fist, pole)
        j[f"hand_{side}"] = np.asarray(fist, dtype=float)
        hip, knee, ankle = H.ride_leg(x) if pose == "ride" else H.stand_leg(x)
        j[f"thigh_{side}"] = hip
        j[f"shin_{side}"] = knee
        j[f"boot_{side}"] = ankle
    j["spear"] = j["hand_r"]
    j["bow_held"] = j["hand_l"]
    j["horn_held"] = j["hand_l"]
    return {k: (v + drop) for k, v in j.items()}


PARENTS = [("hips", "root"), ("chest", "hips"), ("head", "chest"), ("cape", "chest"),
           ("arm_l", "chest"), ("forearm_l", "arm_l"), ("hand_l", "forearm_l"), ("bow_held", "hand_l"),
           ("horn_held", "hand_l"), ("arm_r", "chest"), ("forearm_r", "arm_r"), ("hand_r", "forearm_r"),
           ("spear", "hand_r"), ("thigh_l", "hips"), ("shin_l", "thigh_l"), ("boot_l", "shin_l"),
           ("thigh_r", "hips"), ("shin_r", "thigh_r"), ("boot_r", "shin_r")]


def bones(pose):
    j = joints(pose)
    out = [("root", (0.0, 0.0, 0.0), None)]
    for name, parent in PARENTS:
        out.append((name, tuple(float(v) for v in j[name]), parent))
    return out


ROUTES = {"bow": "chest"}


def clip_group(bone):
    if bone in ("thigh_l", "shin_l", "boot_l", "thigh_r", "shin_r", "boot_r", "hips"):
        return "legs"
    if bone in ("chest", "head"):
        return "body"
    if bone.startswith(("arm_", "forearm_", "hand_")):
        return "arms"
    return None


def show(p, props=()):
    """Hides the props he isn't holding (scale 0)."""
    for prop in PROPS:
        if prop not in props:
            p.scale(prop, 0.0)


# ---------------------------------------------------------------- arm and body poses (FK, degrees)
# Signs (the kit's): x positive turns a part that points forward downwards (and swings a hanging one back); y turns
# him to his left; z rolls a part towards his right.

def arm(p, side, sh=(0.0, 0.0, 0.0), el=(0.0, 0.0, 0.0), hand=(0.0, 0.0, 0.0)):
    p.rot(f"arm_{side}", *sh)
    p.rot(f"forearm_{side}", *el)
    p.rot(f"hand_{side}", *hand)


def mix(a, b, t):
    return tuple(x + (y - x) * t for x, y in zip(a, b))


def breathe(p, t, T, amount=1.0):
    w = 2 * math.pi * t / T
    p.rot("chest", -1.2 * amount * math.sin(w), 0.0, 0.0)
    p.pos("chest", 0.0, 0.25 * amount * math.sin(w), 0.0)


def lowest(m, mats, bones):
    """The lowest point (units) of those bones' boxes in a posed model."""
    from kit.clipping import _box_frame
    from rimekit import mobkit
    return min(float(mobkit._grid(_box_frame(mats, c))[:, 1].min() * 16.0) for b in bones for c in m[b].cubes)


LEG_BONES = ("thigh_l", "shin_l", "boot_l", "thigh_r", "shin_r", "boot_r")


def keep_on_floor(rig, p, bones=LEG_BONES, give=0.2):
    """Lifts him (his hips) so nothing of `bones` goes under the floor in this pose."""
    mats = rig.m.pose_matrices(p.as_geo())
    low = lowest(rig.m, mats, bones)
    if low < -give:
        p.pos("hips", 0.0, -low, 0.0)
