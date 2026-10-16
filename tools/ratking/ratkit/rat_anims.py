"""
The rats' animations (animation.rat.idle and animation.rat.run), generated from each rat's rest pose.

Regular rats:
  idle  (loop)  sniffing about, rearing up on its hind legs to look round, grooming its face, tail swishing
  run   (loop)  a bounding gallop: front paws together, hind paws together, the spine flexing, the tail streaming
Enraged rats (a separate breed):
  idle  (loop)  crouched low with its back arched and its bristles up, trembling, hissing and snapping, tail lashing,
                a little lunge forward
  run   (loop)  a faster, head-low gallop with its jaw open and ears pinned back

The showcase plays the run on the spot.
"""
import math

import numpy as np

from kit.anims import Animation, Pose, Rig, bump, clamp01, gait_offset, ramp, smooth, wobble
from kit.moves import keyed

from . import quad as Q
from . import rig as R

STEP = 0.05


def posed_targets(m, q):
    """Where each paw actually stands in the rest pose (on the ground under its contact point)."""
    mats = m.pose_matrices()
    out = {}
    for name, limb in Q.leg_limbs(m, q).items():
        c = m.point(mats, limb.end_bone, limb.contact)
        out[name] = (np.array([c[0], 0.0, c[2]]), np.array([0.0, 0.0, -1.0]))
    return out


class RatRig(Rig):
    def __init__(self, m, q, design):
        super().__init__(m, Q.leg_limbs(m, q), posed_targets(m, q), dict(ik_smooth=0.05))
        self.q = q
        self.d = design
        self.enraged = getattr(design, "ENRAGED", False)
        self.tails = [n for n in Q.tail_names(q) if n in m.by_name]
        self.bristles = [b.name for b in m.bones if b.name.startswith("bristle_")]
        mats = m.pose_matrices()
        hind = [self.rest[f"hind_{s}"][0] for s in ("l", "r")]
        self.hind_z = float(np.mean([h[2] for h in hind]))


def plant(rig, p, offsets=None, which=("hind_l", "hind_r", "front_l", "front_r")):
    for name in which:
        tgt, heading = rig.rest[name]
        tgt = np.array(tgt, dtype=float)
        if offsets and name in offsets:
            tgt = tgt + np.asarray(offsets[name], dtype=float)
        rig.solve_limb(p, name, tgt, heading)


class Tail:
    def __init__(self, rig):
        self.rig = rig
        self.prev = None

    def apply(self, p, t, amp=8.0, freq=1.0, lag=0.5, extra=None):
        rig = self.rig
        m = rig.m
        if not rig.tails:
            return
        mats = m.pose_matrices(p.as_geo())
        n = len(rig.tails)
        new = []
        for i, name in enumerate(rig.tails):
            b = m[name]
            from kit import geo
            rest_local = geo.gecko_rotation(*b.rotation)[:3, :3] @ np.array([0.0, -1.0, 0.0])
            rigid = R.to_bedrock(mats[b.parent.name][:3, :3] @ rest_local)
            ang = math.radians(amp * (i + 1) / n * math.sin(2 * math.pi * freq * t - 0.9 * i))
            if extra is not None:
                ang += math.radians(extra(i))
            c, s_ = math.cos(ang), math.sin(ang)
            want = R.normalize(np.array([rigid[0] * c + rigid[2] * s_, rigid[1], -rigid[0] * s_ + rigid[2] * c]))
            if self.prev is not None and lag > 0:
                want = R.normalize(self.prev[i] * lag + want * (1.0 - lag))
            new.append(want)
            start = m.point(mats, name, b.pivot)
            length, thick = rig.q.tail[i]
            d = R.clamp_above(start, want, length, thick / 2.0 + 0.1)
            local = R.local_dir(mats, b.parent.name, d)
            R.set_total(p, m, name, R.aim_axis((0, -1, 0), local))
            mats = m.pose_matrices(p.as_geo())
        self.prev = new


def record(rig, name, length, frame, warm=1):
    a = Animation(name, length, True)
    rig.reset_warm()
    tail = Tail(rig)
    n = int(round(length / STEP))
    for _ in range(warm):
        for i in range(n):
            frame(i * STEP, tail)
    for i in range(n + 1):
        a.add(i * STEP, frame(i * STEP, tail))
    a.bake(0.4, 0.04, 0.01)
    return a


# ---------------------------------------------------------------- regular rats


IDLE_T = 4.0


def idle(rig):
    q = rig.q
    T = IDLE_T

    def frame(t, tail):
        p = Pose()
        w = 2 * math.pi * t / T
        breath = math.sin(4 * w)
        rear_up = bump(t, 1.0, 1.3, 2.1, 2.45)
        groom = bump(t, 2.6, 2.8, 3.3, 3.5)
        sniff = (1.0 - rear_up) * (bump(t, 0.1, 0.2, 0.8, 0.95) + bump(t, 3.5, 3.6, 3.85, 3.95))
        # Rearing up (and sitting back to groom): the whole rat tips back about its hind feet.
        up = -58.0 * rear_up - 28.0 * groom
        R.rotate_body_about(p, (up, 0.0, 0.0), (0.0, 0.0, rig.hind_z))
        p.pos("body", 0.0, 0.2 * breath, 0.0)
        p.rot("fore", 0.6 * breath + 10.0 * rear_up)
        look = keyed(t, [(0.0, 0.0), (1.35, 0.0), (1.55, -35.0), (1.8, -35.0), (1.95, 30.0), (2.15, 30.0), (2.3, 0.0)])
        p.rot("head", -6.0 * sniff * (0.5 + 0.5 * math.sin(t * 2 * math.pi * 7.0)) + 30.0 * rear_up + 26.0 * groom
              + 4.0 * groom * math.sin(t * 2 * math.pi * 4.0),
              float(look), 0.0)
        p.rot("jaw", 6.0 * sniff * (0.5 + 0.5 * math.sin(t * 2 * math.pi * 7.0)))
        p.rot("ear_l", -12.0 * bump(t, 0.5, 0.55, 0.6, 0.7))
        p.rot("ear_r", -12.0 * bump(t, 3.0, 3.05, 3.1, 3.2))
        plant(rig, p, which=("hind_l", "hind_r"))
        if rear_up + groom < 0.02:
            plant(rig, p, which=("front_l", "front_r"))
        else:
            # Front paws held up to its chest (reared), or rubbing its face (grooming).
            k = rear_up + groom
            rub = groom * math.sin(t * 2 * math.pi * 3.0)
            for side, sx in R.SIDES:
                R.set_total(p, rig.m, f"front_{side}", (-70.0 - 40.0 * groom - 18.0 * rub * sx, 0.0, -sx * 8.0), k)
                R.set_total(p, rig.m, f"paw_{side}", (-40.0, 0.0, 0.0), k)
        tail.apply(p, t, amp=10.0, freq=0.5)
        return p

    return record(rig, "idle", T, frame)


def run(rig):
    enraged = rig.enraged
    T = 0.36 if enraged else 0.42
    stride = 9.0 * (rig.q.back_y / 5.0) if not enraged else 8.0 * (rig.q.back_y / 5.0)
    lift = 2.2 * rig.q.back_y / 5.0
    stance = 0.42
    phases = {"front_l": 0.0, "front_r": 0.08, "hind_l": 0.5, "hind_r": 0.58}

    def frame(t, tail):
        ph = t / T
        w = 2 * math.pi * ph
        p = Pose()
        p.pos("body", 0.0, 0.6 * lift * (0.5 + 0.5 * math.sin(2 * w - 0.5)) - (1.0 if enraged else 0.3), 0.0)
        flex = 14.0 * math.sin(w)
        p.rot("rear", -flex, 0.0, 0.0)
        p.rot("fore", flex * 0.8 + (6.0 if enraged else 0.0), 0.0, 0.0)
        p.rot("head", -flex * 0.7 + (6.0 if enraged else -4.0), 0.0, 0.0)
        if enraged:
            p.rot("jaw", 24.0 + 6.0 * math.sin(2 * w))
            p.rot("ear_l", 30.0)
            p.rot("ear_r", 30.0)
            for i, b in enumerate(rig.bristles):
                p.rot(b, -6.0 * math.sin(w + i))
        else:
            p.rot("ear_l", 14.0)
            p.rot("ear_r", 14.0)
        offs = {}
        for name, off in phases.items():
            dz, dy, _u = gait_offset(ph - off, stance, stride * stance, lift)
            offs[name] = (0.0, dy, dz)
        plant(rig, p, offs)
        tail.apply(p, t, amp=14.0, freq=1.0 / T, lag=0.35, extra=lambda i: 0.0)
        return p

    a = record(rig, "run", T, frame, warm=2)
    a.meta["stride_blocks"] = stride / 16.0
    return a


# ---------------------------------------------------------------- enraged rats


RAGE_IDLE_T = 2.4


def enraged_idle(rig):
    T = RAGE_IDLE_T

    def frame(t, tail):
        p = Pose()
        tremble = 1.0
        lunge = bump(t, 1.1, 1.25, 1.35, 1.7)
        snap = bump(t, 1.2, 1.27, 1.3, 1.42)
        hiss = 0.5 + 0.5 * math.sin(t * 2 * math.pi * 1.25)
        p.pos("body", 0.25 * tremble * wobble(t, 1, 24), -1.2 + 0.15 * wobble(t, 2, 20), -2.5 * lunge)
        p.rot("rear", -8.0 - 2.0 * math.sin(t * 2 * math.pi / T * 2), 0.0, 0.0)
        p.rot("fore", 10.0 + 4.0 * lunge, 0.0, 1.2 * wobble(t, 3, 22))
        p.rot("head", 8.0 - 10.0 * snap + 1.5 * wobble(t, 4, 26), 6.0 * math.sin(t * 2 * math.pi / T), 0.0)
        p.rot("jaw", 14.0 + 10.0 * hiss + 18.0 * snap - 24.0 * bump(t, 1.42, 1.45, 1.5, 1.6))
        p.rot("ear_l", 34.0)
        p.rot("ear_r", 34.0)
        for i, b in enumerate(rig.bristles):
            p.rot(b, -10.0 + 5.0 * wobble(t, 10 + i, 30))
        plant(rig, p)
        tail.apply(p, t, amp=34.0, freq=3.0 / T, lag=0.2)
        return p

    return record(rig, "idle", T, frame)


def build_all(rig):
    if rig.enraged:
        return {"idle": enraged_idle(rig), "run": run(rig)}
    return {"idle": idle(rig), "run": run(rig)}
