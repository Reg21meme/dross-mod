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
  follow()   the kilt's flaps lifted just clear of their own legs, G3's cloak swinging clear of the legs

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
# The kilt's four flaps (props.kilt_bones): each lifts only as far as it must to clear its own leg (the user, November
# 25, 2026: the front of the kilt rose as far as the knee came up in the stomp, "too high ... it looks unnatural").
KILT_CLEAR = 0.4             # units a flap's strips may sink into its leg beyond how far they sit in it at rest
KILT_SLACK = 0.3             # units over the least it can get to (a raised thigh's top meets the strips' tops by the
                             # belt, which no lift takes away): a flap lifts only until lifting more stops helping
KILT_REST_ON = 2.0           # degrees a lifted flap rides further, so its strips lie on the thigh rather than in it
KILT_SCAN = 3.0              # degrees between the lifts tried, then halved down to KILT_FINE
KILT_FINE = 0.25
KILT_OVER = 10.0             # degrees a flap may lift past its thigh's own turn (never more)
KILT_BEND = 0.2              # the front flaps also lift this share of the body's forward bend (the pelvis tipping)
KILT_SMOOTH = 0.15           # seconds round each frame a flap's lift is smoothed over (it never flickers)


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
        if self.has("kilt_fl"):
            self.lift_kilt(p)
        elif self.has("kilt_f"):
            p.rot("kilt_f", 1.0 * min(0.0, min(lx)) - 0.2 * max(0.0, p.get3("body")[0][0]))
        if self.has("kilt_b"):
            p.rot("kilt_b", 0.8 * max(0.0, max(lx)))
        if self.has("cloak"):
            bend = p.get3("chest")[0][0] + p.get3("body")[0][0]
            p.rot("cloak", -0.75 * max(0.0, bend) + 0.6 * max(0.0, max(lx)))
        return p

    # ------------------------------------------------------------ the kilt's flaps

    def _kilt_boxes(self, mats, bones):
        return [mobkit._box_frame(mats, c) for b in bones for c in self.m.by_name[b].cubes]

    def _kilt_depth(self, mats, flap, leg_frames):
        """How deep (units) the flap's strips are inside its leg's boxes."""
        pts = np.concatenate([mobkit._grid(fr) for fr in self._kilt_boxes(mats, [flap])])
        return max(float(mobkit._inside_depth(pts, fr).max()) for fr in leg_frames)

    def _kilt_rest(self):
        """Each flap's depth in its own leg in the rest pose (some strips sit against the thigh's top already)."""
        if not hasattr(self, "_kilt_rest_depth"):
            mats = self.mats(Pose())
            self._kilt_rest_depth = {}
            for flap in ("kilt_fl", "kilt_fr", "kilt_bl", "kilt_br"):
                side = flap[-1]
                legs = self._kilt_boxes(mats, [f"leg_{side}", f"shin_{side}", f"foot_{side}"])
                self._kilt_rest_depth[flap] = self._kilt_depth(mats, flap, legs)
        return self._kilt_rest_depth

    def lift_kilt(self, p):
        """Each of the kilt's four flaps lifted the least that keeps its strips out of its own leg (front flaps forward,
        back flaps back) or, if nothing takes them all out, until lifting more stops helping; never past its thigh's
        own turn and KILT_OVER; then KILT_REST_ON more, so they lie on the thigh. The front ones also lift with the
        pelvis's tip. smooth_kilt() evens the lifts out over time once an animation is recorded."""
        rest = self._kilt_rest()
        bend = max(0.0, p.get3("body")[0][0])
        for flap in ("kilt_fl", "kilt_fr"):
            p.rot(flap, -KILT_BEND * bend)
        mats = self.mats(p)
        for flap in ("kilt_fl", "kilt_fr", "kilt_bl", "kilt_br"):
            side = flap[-1]
            way = -1.0 if flap[5] == "f" else 1.0              # (forward is -x for a hanging flap)
            legs = self._kilt_boxes(mats, [f"leg_{side}", f"shin_{side}"])
            allowed = rest[flap] + KILT_CLEAR
            r = p.get3(flap)[0]
            base = r[0]

            def depth(lift):
                r[0] = base + way * lift
                return self._kilt_depth(self.mats(p), flap, legs)

            if depth(0.0) <= allowed:
                r[0] = base
                continue
            cap = max(0.0, way * p.get3(f"leg_{side}")[0][0]) + KILT_OVER
            scan = [(a, depth(a)) for a in np.arange(KILT_SCAN, cap + 1e-6, KILT_SCAN)]
            if not scan:
                r[0] = base
                continue
            target = max(allowed, min(d for _a, d in scan) + KILT_SLACK)
            lo, hi = 0.0, None
            for a, d in scan:
                if d <= target:
                    hi = float(a)
                    break
                lo = float(a)
            while hi - lo > KILT_FINE:
                mid = (lo + hi) / 2.0
                if depth(mid) <= target:
                    hi = mid
                else:
                    lo = mid
            r[0] = base + way * min(cap, hi + min(KILT_REST_ON, hi))
            mats = self.mats(p)
        return p

    def smooth_kilt(self, frames, loop):
        """Evens out each flap's lift over an animation's frames [(t, Pose)]: the highest lift within KILT_SMOOTH of
        each frame, then averaged over the same window (a loop wraps round), so a flap never flickers between frames
        and never drops below what a moment near it needs."""
        if not frames or not self.has("kilt_fl"):
            return
        n = len(frames)
        step = frames[1][0] - frames[0][0] if n > 1 else 1.0
        w = max(1, int(round(KILT_SMOOTH / step)))
        for flap in ("kilt_fl", "kilt_fr", "kilt_bl", "kilt_br"):
            way = -1.0 if flap[5] == "f" else 1.0
            vals = np.array([way * p.get3(flap)[0][0] for _t, p in frames])
            last = n - 1 if loop is True else n        # (a loop's last frame is its first again)

            def at(i):
                if loop is True:
                    return vals[i % last]
                return vals[min(max(i, 0), n - 1)]

            def span(i):          # (a one-off move's window narrows at its ends, so it starts and ends as recorded)
                return w if loop is True else min(w, i, n - 1 - i)

            peak = np.array([max(at(j) for j in range(i - span(i), i + span(i) + 1)) for i in range(n)])
            vals = peak

            def at2(i):
                if loop is True:
                    return vals[i % last]
                return vals[min(max(i, 0), n - 1)]

            even = [sum(at2(j) for j in range(i - span(i), i + span(i) + 1)) / (2 * span(i) + 1) for i in range(n)]
            if loop is True:
                even[-1] = even[0]
            for (_t, p), v in zip(frames, even):
                p.get3(flap)[0][0] = way * float(v)


Pose = Pose
