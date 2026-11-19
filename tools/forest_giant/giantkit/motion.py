"""
What the giant's animations stand on (tools/forest_giant/mobs.py, giantkit/moves.py): one look's model on its rig
(rig.py) and the tools to pose it, frame by frame, the Rimefall kit's way (rimekit/mobkit.py):

  legs       planted with the Rimefall kit's IK (MobRig): each foot on its spot, its sole flat
  arm()      an arm put where it should be: its wrist on a point, its elbow bending towards a pole (two-bone IK, worked
             out exactly: the upper arm, the forearm turning only about its elbow's hinge, the fist following), the
             fist's grip turned to a direction if asked (a spear kept upright, a club swung down), the shoulder pad
             following part of the arm's turn (the Cinder Colossus's lesson)
  aim()      any bone turned so one of its rest directions points somewhere (a spear laid down, a shield lying flat)
  place()    any bone moved so one of its points is somewhere (a boulder torn out of the ground, falling off a shoulder)
  hide()     a bone scaled to nothing (G2's and G3's rock outside the throw, G1's boulder once it's thrown)
  follow()   the kilt's flaps and G3's cloak swinging clear of the legs

Because every look has its own rest pose (G1's right arm up on its boulder, G2's arms in front with spear and shield,
G3's club hanging), the moves name where the hands go (points in the chest's rest frame, or on the ground) rather than
how far each joint turns, so the same move reads the same on all three.

Units: Bedrock model units (16 a block), y up, the front -z, the giant's own left +x. Angles: Bedrock degrees (+x
bends forward, +y turns to its right, +z leans to its left).
"""
import math
import types

import numpy as np

from kit import geo
from kit.anims import Pose
from rimekit import mobkit
from rimekit.shapes import unit, v3

from . import rig

PAD_FOLLOW = 0.45            # a shoulder pad (pad_l/r, on the chest) turns this share of its arm's turn


def g(v):
    """A Bedrock vector in GeckoLib space (x flipped)."""
    return np.array([-v[0], v[1], v[2]], dtype=float)


def b_(v):
    """A GeckoLib vector in Bedrock space."""
    return np.array([-v[0], v[1], v[2]], dtype=float)


def frame_map(a0, b0, a1, b1):
    """The rotation (3x3) taking direction a0 to a1 and, about that, b0 to b1 (as near as they allow)."""
    def basis(a, b):
        a = a / np.linalg.norm(a)
        b = b - a * np.dot(a, b)
        nb = np.linalg.norm(b)
        if nb < 1e-9:
            b = np.cross(a, [1.0, 0.0, 0.0] if abs(a[0]) < 0.9 else [0.0, 1.0, 0.0])
            nb = np.linalg.norm(b)
        b = b / nb
        return np.stack([a, b, np.cross(a, b)], axis=1)
    return basis(a1, b1) @ basis(a0, b0).T


def turn_between(a, b):
    """The smallest rotation (3x3) taking direction a to direction b."""
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    ax = np.cross(a, b)
    s = np.linalg.norm(ax)
    c = float(np.clip(np.dot(a, b), -1.0, 1.0))
    if s < 1e-9:
        if c > 0:
            return np.eye(3)
        perp = np.cross(a, [1.0, 0.0, 0.0] if abs(a[0]) < 0.9 else [0.0, 1.0, 0.0])
        return geo.axis_angle(perp / np.linalg.norm(perp), math.pi)
    return geo.axis_angle(ax / s, math.atan2(s, c))


def partial(R, share):
    """`share` of rotation R (the same axis, that share of the angle)."""
    c = float(np.clip((np.trace(R) - 1.0) / 2.0, -1.0, 1.0))
    ang = math.acos(c)
    if ang < 1e-6:
        return np.eye(3)
    ax = np.array([R[2, 1] - R[1, 2], R[0, 2] - R[2, 0], R[1, 0] - R[0, 1]]) / (2.0 * math.sin(ang))
    return geo.axis_angle(ax / np.linalg.norm(ax), ang * share)


def lerp(a, b, u):
    return v3(a) + (v3(b) - v3(a)) * u


class GiantRig:
    """One look's model (build.py's build_model result `b`) and the tools to pose it."""

    def __init__(self, b):
        self.b = b
        self.m = b.m
        self.J = b.J
        self.d = b.d
        self.id = b.d.ID
        spec = types.SimpleNamespace(K=1.0, LEGS=rig.legs(b.J))
        self.legs = mobkit.MobRig(self.m, spec)
        self.prev = {}
        self.arms = {}
        for side in ("l", "r"):
            S0, E0, W0 = (v3(b.J[f"{k}_{side}"]) for k in ("shoulder", "elbow", "wrist"))
            dn0 = unit(W0 - S0)
            pole0 = E0 - (S0 + dn0 * np.dot(E0 - S0, dn0))
            if np.linalg.norm(pole0) < 1e-3:
                pole0 = v3(1.0 if side == "l" else -1.0, 0.0, 0.0)
            _w, _down, c0, g0 = b.J.hand(side)
            self.arms[side] = types.SimpleNamespace(S0=S0, E0=E0, W0=W0, pole0=unit(pole0), u0=unit(E0 - S0),
                                                    f0=unit(W0 - E0), n0=unit(np.cross(unit(pole0), dn0)),
                                                    l1=float(np.linalg.norm(E0 - S0)),
                                                    l2=float(np.linalg.norm(W0 - E0)), fist0=v3(c0), grip0=unit(g0))

    # ------------------------------------------------------------ basics

    def reset(self):
        """At the start of each animation: the IK starts afresh and the angles' continuity is forgotten."""
        self.legs.reset_warm()
        self.prev = {}

    def mats(self, p):
        return self.m.pose_matrices(p.as_geo())

    def has(self, bone):
        return bone in self.m.by_name

    def point(self, p, bone, rest_point, mats=None):
        """Where a rest-pose point carried by `bone` is now (Bedrock)."""
        return self.m.point(mats if mats is not None else self.mats(p), bone, v3(rest_point))

    def direction(self, p, bone, rest_dir, mats=None):
        return unit(self.m.direction(mats if mats is not None else self.mats(p), bone, v3(rest_dir)))

    def set_rot(self, p, bone, R_local):
        """Writes a bone's local rotation (3x3, GeckoLib space; its rest rotation is zero) as Bedrock angles, kept
        continuous with its last frame."""
        e = geo.euler_bedrock(R_local, self.prev.get(bone))
        self.prev[bone] = e
        p.get3(bone)[0] = list(e)

    def world_rot(self, mats, bone):
        M = mats[bone][:3, :3]
        return M / np.cbrt(max(abs(np.linalg.det(M)), 1e-12))

    # ------------------------------------------------------------ arms

    def rest_wrist(self, p, side, mats=None):
        """Where this look's wrist would be if the arm kept its rest pose on the chest as it is now."""
        return self.point(p, "chest", self.arms[side].W0, mats)

    def rest_pole(self, p, side, mats=None):
        return self.direction(p, "chest", self.arms[side].pole0, mats)

    def chest_point(self, p, rest_point, mats=None):
        return self.point(p, "chest", rest_point, mats)

    def chest_dir(self, p, rest_dir, mats=None):
        return self.direction(p, "chest", rest_dir, mats)

    def arm(self, p, side, wrist, pole, grip=None, mats=None):
        """
        Puts one arm so its wrist is at `wrist` (Bedrock, as near as the arm reaches), its elbow bending towards `pole`
        (a direction), and, with `grip` (a direction), the fist turned the least it can so its grip axis (what it holds
        runs along it) points that way. Returns where the wrist ended up.
        """
        A = self.arms[side]
        mats = mats if mats is not None else self.mats(p)
        S = self.point(p, "chest", A.S0, mats)
        wrist = v3(wrist)
        E = rig.two_bone(S, wrist, A.l1, A.l2, pole)
        W = E + unit(wrist - E) * A.l2
        dn = unit(W - S)
        pp = v3(pole) - dn * np.dot(v3(pole), dn)
        if np.linalg.norm(pp) < 1e-6:
            pp = A.pole0 - dn * np.dot(A.pole0, dn)
        n = unit(np.cross(unit(pp), dn))
        u = unit(E - S)
        f = unit(W - E)
        C = self.world_rot(mats, "chest")
        Rw_arm = frame_map(g(A.u0), g(A.n0), g(u), g(n))
        R_arm = C.T @ Rw_arm
        self.set_rot(p, f"arm_{side}", R_arm)
        Rw_fore = frame_map(g(A.f0), g(A.n0), g(f), g(n))
        self.set_rot(p, f"fore_{side}", Rw_arm.T @ Rw_fore)
        if grip is not None:
            now = Rw_fore @ g(A.grip0)
            Rw_hand = turn_between(now, g(unit(v3(grip)))) @ Rw_fore
            self.set_rot(p, f"hand_{side}", Rw_fore.T @ Rw_hand)
        else:
            self.set_rot(p, f"hand_{side}", np.eye(3))
        if self.has(f"pad_{side}"):
            self.set_rot(p, f"pad_{side}", partial(R_arm, PAD_FOLLOW))
        return W

    def arm_rest(self, p, side, offset=(0.0, 0.0, 0.0), grip=None, mats=None):
        """An arm kept in its look's rest pose on the chest, its wrist moved by `offset` (in the chest's frame)."""
        mats = mats if mats is not None else self.mats(p)
        A = self.arms[side]
        w = self.point(p, "chest", A.W0 + v3(offset), mats)
        return self.arm(p, side, w, self.rest_pole(p, side, mats), grip, mats)

    def fist(self, p, side, mats=None):
        """Where a fist's middle is now."""
        return self.point(p, f"hand_{side}", self.arms[side].fist0, mats)

    def grip_dir(self, p, side, mats=None):
        return self.direction(p, f"hand_{side}", self.arms[side].grip0, mats)

    # ------------------------------------------------------------ any bone

    def aim(self, p, bone, rest_dir, world_dir, mats=None):
        """Turns `bone` (on top of whatever its parent does) the least it can so its rest direction `rest_dir` points
        along `world_dir`."""
        mats = mats if mats is not None else self.mats(p)
        parent = self.m[bone].parent.name
        P = self.world_rot(mats, parent)
        now = P @ g(unit(v3(rest_dir)))
        self.set_rot(p, bone, P.T @ turn_between(now, g(unit(v3(world_dir)))) @ P)

    def orient(self, p, bone, rest_a, rest_b, world_a, world_b, mats=None):
        """Turns `bone` so its rest direction rest_a points along world_a and, about that, rest_b towards world_b."""
        mats = mats if mats is not None else self.mats(p)
        P = self.world_rot(mats, self.m[bone].parent.name)
        Rw = frame_map(g(unit(v3(rest_a))), g(unit(v3(rest_b))), g(unit(v3(world_a))), g(unit(v3(world_b))))
        self.set_rot(p, bone, P.T @ Rw)

    def place(self, p, bone, rest_point, world_point, mats=None):
        """Moves `bone` (its position offset) so the rest-pose point it carries, `rest_point`, is at `world_point`."""
        mats = mats if mats is not None else self.mats(p)
        now = self.point(p, bone, rest_point, mats)
        P = self.world_rot(mats, self.m[bone].parent.name)
        t = P.T @ g(v3(world_point) - now) / 16.0
        pos = p.get3(bone)[1]
        pos[0] += -t[0] * 16.0
        pos[1] += t[1] * 16.0
        pos[2] += t[2] * 16.0

    def hide(self, p, bone, s=0.0):
        p.get3(bone)[2] = [s, s, s]

    # ------------------------------------------------------------ legs and what hangs round them

    def plant(self, p, offsets=None, which=None):
        """Plants the feet (all, or `which`) on their rest spots moved by `offsets` {leg_l/leg_r: (dx, dy, dz)}."""
        self.legs.plant(p, offsets, which)

    def follow(self, p):
        """The kilt's flaps swing out of the thighs' way (front and back), G3's cloak hangs on behind the legs when the
        chest bends."""
        lx = [p.get3(f"leg_{s}")[0][0] for s in ("l", "r")]
        if self.has("kilt_f"):
            p.rot("kilt_f", 1.0 * min(0.0, min(lx)) - 0.2 * max(0.0, p.get3("body")[0][0]))
        if self.has("kilt_b"):
            p.rot("kilt_b", 0.8 * max(0.0, max(lx)))
        if self.has("cloak"):
            bend = p.get3("chest")[0][0] + p.get3("body")[0][0]
            p.rot("cloak", -0.75 * max(0.0, bend) + 0.6 * max(0.0, max(lx)))
        return p


Pose = Pose
