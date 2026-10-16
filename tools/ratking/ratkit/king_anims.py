"""
The Rat King's animations, generated frame by frame from each design's rest pose (every design shares this code; its
STYLE picks the walk, the sleep and the idle gesture):

  sleep     (loop)    slumped on his staff, or curled up on the ground with the staff beside him; eyelids shut, slow
                      breathing (the mod adds the floating Zzz)
  wake      (once)    startles awake, looks round, stretches and yawns, grabs his staff, stands into his idle pose
  idle      (loop)    breathing, shifty glances, a sniff, a blink, tail sway, and his own gesture
  walk      (loop)    on the spot (the showcase): feet planted by IK, the cape and tail swinging
  burrow    (holds)   glances round, digs at the ground, dives in head first; the dirt mound stays
  burrowed  (holds)   the burrow's last frame (a Rat King loaded while underground)
  popup     (once)    a hole opens right under him and trembles, his nose pokes out and sniffs, he springs straight
                      up out of it and lands on the spot, his crown bouncing
  rage      (once)    trembling fists, a stomping tantrum, a screech, a staff slam (the mod turns his eyes red and
                      blows steam out of his ears at the times in meta)
  death     (holds)   clutches his chest, wobbles, falls flat on his back like a plank: tongue out, X eyes, one foot
                      twitching
  dead      (holds)   the death's last frame
  run       (loop)    the fight: a fast, low scurry, the staff carried in his fist, cape streaming, tail out behind
                      (in place; the mod plays it as fast as he moves: runBlocks per runTicks)
  sweep     (once)    the fight's close blow: winds the staff back to his right and sweeps it flat round his front at
                      chest height (the hit as it passes his front), then back to his idle hold
  cast      (once)    the fight's poison ball: holds the staff out, aims, draws back and flicks; the ball leaves the
                      orb at the release (where, in meta), then back to his idle hold

The cape, the tail, the censer and the ribbons are secondary motion: each frame they ease towards where gravity, the
body's motion and their rest shape want them (with a little lag), and the cape is swung clear of the body.
Times the mod needs go into Animation.meta (seconds; build.py turns them into ticks for RatKingDesigns.java).
"""
import math

import numpy as np
from scipy.spatial.transform import Rotation, Slerp

from kit import geo
from kit.anims import Animation, Pose, Rig, bump, clamp01, ease_out, gait_offset, ramp, smooth, wobble
from kit.moves import keyed

from . import parts as PT
from . import rig as R

PREFIX = "animation.rat_king."
STEP = 0.05

WALKS = {
    #          seconds per two steps, stride (units), foot lift, how much lower he walks, body bob, sway, lean (deg),
    #          free arm swing (deg), head bob (deg), shoulder roll (deg)
    "sneak": dict(T=1.6, stride=13.0, lift=7.0, crouch=2.6, bob=1.1, sway=0.7, lean=7.0, arm=16.0, head=5.0, roll=3.0,
                  tiptoe=22.0),
    "strut": dict(T=1.3, stride=15.0, lift=5.0, crouch=0.2, bob=0.9, sway=0.9, lean=-7.0, arm=26.0, head=-6.0, roll=2.0,
                  tiptoe=0.0),
    "scurry": dict(T=0.8, stride=9.0, lift=4.5, crouch=2.8, bob=1.0, sway=0.6, lean=9.0, arm=12.0, head=4.0, roll=4.0,
                   tiptoe=10.0),
    "glide": dict(T=1.9, stride=12.0, lift=3.0, crouch=0.6, bob=0.35, sway=0.4, lean=0.0, arm=6.0, head=1.0, roll=1.0,
                  tiptoe=0.0),
    "shuffle": dict(T=2.0, stride=7.0, lift=2.2, crouch=1.2, bob=0.5, sway=1.0, lean=4.0, arm=0.0, head=2.0, roll=2.5,
                    tiptoe=0.0),
    "swagger": dict(T=1.15, stride=15.0, lift=5.0, crouch=0.8, bob=1.3, sway=2.0, lean=-3.0, arm=22.0, head=-3.0,
                    roll=7.0, tiptoe=0.0),
}


# ---------------------------------------------------------------- the rig


class KingRig(Rig):
    """The kit's Rig (IK with warm starts) plus what the Rat King's animations need about one design."""

    def __init__(self, m, b, design):
        limbs = R.legs(m, b)
        self.has_staff = "staff" in m.by_name
        if self.has_staff:
            s = m["staff"]
            limbs["staff"] = _staff_limb(m, s)
            sp = s.pivot[1]
            tops = [c.origin[1] + c.size[1] - sp for bn in m.bones if "staff" in [a.name for a in bn.ancestors()]
                    for c in bn.cubes]
            head_up = (max(tops) if tops else 30.0) - 1.5
            limbs["staff_head"] = _staff_limb(m, s, contact=(s.pivot[0], s.pivot[1] + head_up, s.pivot[2]))
        targets = {k: (np.array(v[0]), np.array(v[1])) for k, v in R.foot_targets(b).items()}
        super().__init__(m, limbs, targets, dict(ik_smooth=0.05))
        self.b = b
        self.d = design
        self.st = dict(getattr(design, "STYLE", {}))
        self.hidden = getattr(m, "hidden", {})
        mats = m.pose_matrices()
        self.rest_mats = mats
        self.tails = [n for n in R.tail_names(b) if n in m.by_name]
        self.capes = [n for n in R.cape_names(b) if n in m.by_name]
        if self.has_staff:
            self.staff_foot_rest = m.point(mats, "staff", PT.staff_foot(m["staff"]))
            self.staff_up_rest = R.normalize(m.direction(mats, "staff", (0.0, 1.0, 0.0)))
            self.rest["staff"] = (self.staff_foot_rest, None)
            self.rest["staff_head"] = (None, None)
            # How far the staff (and anything hanging on it) reaches above its grip, in its own frame.
            sp = m["staff"].pivot[1]
            tops = [c.origin[1] + c.size[1] - sp for bn in m.bones if "staff" in [a.name for a in bn.ancestors()]
                    for c in bn.cubes]
            self.staff_above = max(tops) if tops else 30.0
            self.staff_local_rest = m["staff"].local_matrix()
            # Points through the staff's boxes, in the staff's own frame (to find its lowest point anywhere).
            from kit.clipping import _box_frame
            from .clip import _grid
            inv = np.linalg.inv(mats["staff"])
            pts = np.concatenate([_grid(_box_frame(mats, c), spacing=1.0) for bn in m.bones
                                  if "staff" in [a.name for a in bn.ancestors()] for c in bn.cubes])
            self.staff_pts = (inv @ np.c_[pts, np.ones(len(pts))].T).T
        from .guard import StaffGuard
        self.guard = StaffGuard(self) if self.has_staff else None
        self.flames = [bn.name for bn in m.bones if bn.name.startswith("flame_")]
        self.ribbons = [bn.name for bn in m.bones if bn.name.startswith("ribbon_")]
        self.has_censer = "censer_chain" in m.by_name
        self.head_top = m.point(mats, "head", (m["head"].pivot[0], m["head"].pivot[1] + b.skull[1], m["head"].pivot[2]))
        self.leg_reach = b.thigh_len + b.shin_len + b.ankle_h

    def bone(self, name):
        return name in self.m.by_name


def _staff_limb(m, s, contact=None):
    """The right arm moving the staff so a spot on it reaches a target: its foot (planting it), or another spot."""
    from kit import ik
    foot = contact if contact is not None else PT.staff_foot(s)
    return ik.Limb([
        ik.Joint("arm_r", (0, 1, 2), lo=(-160, -70, -40), hi=(70, 70, 80), prefer=(None, None, None), weight=0.02),
        ik.Joint("forearm_r", (0, 1), lo=(-140, -45), hi=(0, 45), prefer=(None, 0), weight=0.03),
        ik.Joint("hand_r", (0, 1, 2), lo=(-60, -50, -50), hi=(60, 50, 50), prefer=(0, 0, 0), weight=0.06),
        ik.Joint("staff", (), lo=(), hi=()),
    ], "staff", contact=foot, down=(0, -1, 0), forward=(0, 0, -1))


# ---------------------------------------------------------------- small helpers


def slide(pose, rig, name, k):
    """Brings a hidden part (eyelids, X eyes, tongue, the dirt hole) out by k (0 hidden .. 1 shown)."""
    if name not in rig.hidden or k == 0.0:
        return
    off = rig.hidden[name]
    pose.pos(name, off[0] * k, off[1] * k, off[2] * k)


def lids(pose, rig, k):
    for side in ("l", "r"):
        slide(pose, rig, f"lid_{side}", k)


def xeyes(pose, rig, k, pop=1.0):
    for side in ("l", "r"):
        slide(pose, rig, f"xeye_{side}", k)
        if k > 0 and pop != 1.0:
            pose.scale(f"xeye_{side}", pop)


def head(pose, rx=0.0, ry=0.0, rz=0.0, jaw=0.0):
    pose.rot("head", rx, ry, rz)
    if jaw:
        pose.rot("jaw", jaw)


def ears(pose, rx=0.0, ry=0.0, rz=0.0, l=1.0, r=1.0):
    pose.rot("ear_l", rx * l, ry * l, rz * l)
    pose.rot("ear_r", rx * r, -ry * r, -rz * r)


def plant_feet(rig, pose, offsets=None, down=None):
    """IK both feet onto their rest footprints (plus offsets {leg: (dx, dy, dz)})."""
    worst = 0.0
    for name in ("leg_l", "leg_r"):
        tgt, heading = rig.rest[name]
        tgt = np.array(tgt, dtype=float)
        if offsets and name in offsets:
            tgt = tgt + np.asarray(offsets[name], dtype=float)
        dn = down.get(name, R.DOWN) if isinstance(down, dict) else (down if down is not None else R.DOWN)
        worst = max(worst, rig.solve_limb(pose, name, tgt, heading, down=tuple(dn)))
    return worst


FOOT_FLOOR_GAP = 0.05     # units a planted foot's lowest point keeps above the floor (plant_feet_clear)


def _foot_low(rig, mats, side):
    """The lowest point (y, units) of one foot's boxes."""
    low = 1e9
    bone = rig.m[f"foot_{side}"]
    for c in bone.cubes:
        cm = mats[bone.name] @ c.cube_matrix()
        ox, oy, oz = c.origin
        w, h, d = c.size
        for dx in (0, w):
            for dy in (0, h):
                for dz in (0, d):
                    q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                    low = min(low, q[1] * 16.0)
    return low


def plant_feet_clear(rig, pose, offsets=None, down=None):
    """plant_feet, then once more for any foot whose sole or toes ended up in the floor (the solver's warm start lags
    a little when his hips turn fast), lifted by just that much."""
    worst = plant_feet(rig, pose, offsets, down)
    mats = mats_of(rig, pose)
    lift = {}
    for side in ("l", "r"):
        low = _foot_low(rig, mats, side)
        if low < FOOT_FLOOR_GAP - 0.02:
            lift[f"leg_{side}"] = FOOT_FLOOR_GAP - low
    if lift:
        offs = {k: list(v) for k, v in (offsets or {}).items()}
        for leg, dy in lift.items():
            o = offs.setdefault(leg, [0.0, 0.0, 0.0])
            o[1] += dy
        worst = plant_feet(rig, pose, offs, down)
    return worst


def plant_staff(rig, pose, target, up=None):
    """IK the right arm so the staff's foot stands on a spot, the shaft leaning along `up`."""
    if not rig.has_staff:
        return 0.0
    up = R.normalize(up if up is not None else rig.staff_up_rest)
    return rig.solve_limb(pose, "staff", np.asarray(target, dtype=float), None, down=tuple(-up))


def reach_hand(rig, pose, target, side="l", warm_key=None):
    """IK one hand's palm onto a spot (orientation barely matters). Returns the miss in units."""
    from kit import ik
    key = warm_key or f"hand_{side}"
    limb = R.hand_limb(rig.m, side)
    x0 = rig._warm.get(key)
    solved, err, x = ik.solve(rig.m, pose.as_geo(), limb, np.asarray(target, dtype=float), None, down=(0.0, -1.0, 0.0),
                              x0=x0, w_down=0.3, w_head=0.0, w_smooth=0.03 if x0 is not None else 0.0)
    rig._warm[key] = x
    for j in limb.joints:
        pose.get3(j.bone)[0] = list(solved[j.bone][0])
    return err


def slide_staff(rig, pose, amount):
    """Slides the staff down through his fist by `amount` units (so he holds it higher up the shaft)."""
    if not rig.has_staff:
        return
    b = rig.m["staff"]
    axis = geo.gecko_rotation(*b.rotation)[:3, :3] @ np.array([0.0, 1.0, 0.0])
    ax = R.to_bedrock(axis)
    pose.pos("staff", -ax[0] * amount, -ax[1] * amount, -ax[2] * amount)


def mats_of(rig, pose):
    return rig.m.pose_matrices(pose.as_geo())


PREV = {}      # the last frame's angles of parts placed by place() (reset at the start of every animation)


def place(rig, pose, bone, M, prev=None):
    """Poses `bone` so its matrix (GeckoLib space) becomes M: lets a staff or a crown leave the hand or head.
    Its angles stay continuous with the last frame's (see rig.aim_axis)."""
    m = rig.m
    b = m[bone]
    mats = mats_of(rig, pose)
    L = np.linalg.inv(mats[b.parent.name]) @ M
    Rl = L[:3, :3]
    e = geo.euler_bedrock(Rl, prev if prev is not None else PREV.get(bone))
    PREV[bone] = e
    p = np.array([-b.pivot[0] / 16.0, b.pivot[1] / 16.0, b.pivot[2] / 16.0])
    pos_g = L[:3, 3] - p + Rl @ p
    entry = pose.get3(bone)
    entry[0] = [e[0] - b.rotation[0], e[1] - b.rotation[1], e[2] - b.rotation[2]]
    entry[1] = [-pos_g[0] * 16.0, pos_g[1] * 16.0, pos_g[2] * 16.0]
    return e


def frame_matrix(rig, bone, origin_world, y_axis, z_hint):
    """A GeckoLib matrix that puts a bone's pivot at origin_world (Bedrock) with its +y along y_axis (world)."""
    b = rig.m[bone]
    y = R.normalize(y_axis)
    z = R.normalize(np.asarray(z_hint, dtype=float) - y * np.dot(z_hint, y))
    x = np.cross(y, z)
    Rb = np.stack([x, y, z], axis=1)          # Bedrock: columns are where the bone's axes go
    F = np.diag([-1.0, 1.0, 1.0])
    Rg = F @ Rb @ F
    M = np.eye(4)
    M[:3, :3] = Rg
    pg = np.array([-b.pivot[0] / 16.0, b.pivot[1] / 16.0, b.pivot[2] / 16.0])
    wg = geo.gecko(origin_world)
    M[:3, 3] = wg - Rg @ pg
    return M


def blend_matrix(M0, M1, u, spin=None):
    """Between two placements: position straight, rotation by slerp."""
    u = clamp01(u)
    rots = Rotation.from_matrix(np.stack([M0[:3, :3], M1[:3, :3]]))
    r = Slerp([0.0, 1.0], rots)([u]).as_matrix()[0]
    M = np.eye(4)
    M[:3, :3] = r
    M[:3, 3] = M0[:3, 3] * (1.0 - u) + M1[:3, 3] * u
    return M


def straighten(rig, pose, w):
    """Takes w of his hunch out of the spine (towards standing bolt upright)."""
    b = rig.b
    pose.rot("hips", -b.hips_pitch * w)
    pose.rot("belly", -b.belly_pitch * w)
    pose.rot("chest", -b.chest_pitch * w)
    pose.rot("neck", -b.neck_pitch * w)
    lean = b.hips_pitch + b.belly_pitch + b.chest_pitch + b.neck_pitch
    hp = b.head_pitch if b.head_pitch is not None else -lean + b.head_down
    pose.rot("head", -hp * w)


def set_limb_totals(rig, pose, totals, w=1.0):
    """Blends bones from their current pose towards these TOTAL rotations by w (0: unchanged, 1: exactly these)."""
    if w <= 0.0:
        return
    for bone, rot in totals.items():
        if not rig.bone(bone):
            continue
        cur = list(pose.get3(bone)[0])
        R.set_total(pose, rig.m, bone, rot, 1.0)
        tgt = pose.get3(bone)[0]
        pose.get3(bone)[0] = [cur[i] * (1.0 - w) + tgt[i] * w for i in range(3)]


# ---------------------------------------------------------------- secondary motion


TAIL_FLOOR_GAP = 0.15     # units each tail segment's end keeps above the floor (Secondary, when the floor is on)


class Secondary:
    """Keeps the cape, tail, censer and ribbons' last directions between frames (for their lag)."""

    def __init__(self, rig):
        self.rig = rig
        self.cape = None
        self.tail = None
        self.hang = {}
        self.last_attach = None
        self.euler = {}

    def apply(self, pose, t, wave=1.0, tail_floor=True, cape_mode="hang", lag=0.45, tail_lag=0.55, cape_floor=0.5,
              tail_wave=(6.0, 1.5, 0.9), extra_tail=None, tail_mode="follow", tail_along=None, tail_gap=TAIL_FLOOR_GAP):
        rig = self.rig
        m = rig.m
        self.t = t
        self._tail(pose, t, wave, tail_floor, tail_lag, tail_wave, extra_tail, tail_mode, tail_along, tail_gap)
        self._cape(pose, cape_mode, lag, cape_floor)
        self._hangers(pose)
        self._flames(pose, t)

    def _tail(self, pose, t, wave, floor_on, lag, tw, extra, mode="follow", along=None, gap=None):
        gap = TAIL_FLOOR_GAP if gap is None else gap
        rig = self.rig
        m = rig.m
        if not rig.tails:
            return
        amp, freq, phase = tw
        mats = m.pose_matrices(pose.as_geo())
        n = len(rig.tails)
        new = []
        for i, name in enumerate(rig.tails):
            b = m[name]
            rest_local = geo.gecko_rotation(*b.rotation)[:3, :3] @ np.array([0.0, -1.0, 0.0])
            rigid = R.to_bedrock(mats[b.parent.name][:3, :3] @ rest_local)
            k = (i + 1) / n
            ang = math.radians(amp * wave * k * math.sin(2 * math.pi * freq * t - phase * i))
            if extra is not None:
                ang += math.radians(extra(i, t))
            if mode == "curl":
                # Lying on the ground, curling round him: flatten the direction and turn it a little more each
                # segment.
                flat = np.array([rigid[0], 0.0, rigid[2]])
                if np.linalg.norm(flat) < 1e-3:
                    flat = np.array([0.0, 0.0, 1.0])
                rigid = R.normalize(flat)
                ang += math.radians(28.0)
            c, s_ = math.cos(ang), math.sin(ang)
            want = np.array([rigid[0] * c + rigid[2] * s_, rigid[1], -rigid[0] * s_ + rigid[2] * c])
            want = R.normalize(want)
            if along is not None:
                # Streaming one way (diving down a hole: straight back up it), by along's weight.
                d_, k_ = along
                want = R.normalize(want * (1.0 - k_) + R.normalize(np.asarray(d_, dtype=float)) * k_)
            if self.tail is not None and lag > 0:
                want = R.normalize(self.tail[i] * lag + want * (1.0 - lag))
            new.append(want)
            start = m.point(mats, name, b.pivot)
            length, thick = rig.b.tail[i]
            d = R.clamp_above(start, want, length, thick / 2.0 + gap) if floor_on else want
            local = R.local_dir(mats, b.parent.name, d)
            tot = R.aim_axis((0, -1, 0), local, prev=self.euler.get(name))
            self.euler[name] = tot
            R.set_total(pose, m, name, tot)
            mats = m.pose_matrices(pose.as_geo())
        self.tail = new

    def _cape(self, pose, mode, lag, floor):
        rig = self.rig
        m = rig.m
        b = rig.b
        if not rig.capes:
            return
        mats = m.pose_matrices(pose.as_geo())
        back = R.normalize(R.world_dir(m, mats, "chest", (0.0, 0.0, 1.0)))
        spine_down = R.normalize(R.world_dir(m, mats, "chest", (0.0, -1.0, 0.0)))
        attach = m.point(mats, rig.capes[0], m[rig.capes[0]].pivot)
        vel = np.zeros(3) if self.last_attach is None else (attach - self.last_attach) / STEP
        self.last_attach = attach
        self.vel = vel if getattr(self, "vel", None) is None else self.vel * 0.65 + vel * 0.35
        vel = self.vel
        if mode == "body":
            # Along his back (falling): follows the body with a little flutter.
            want = [R.normalize(spine_down * 1.0 + back * 0.12 * (i + 1)) for i in range(len(rig.capes))]
        elif mode == "gravity":
            # Just hangs (diving into the hole: it falls in after him).
            want = [R.DOWN.copy() for _ in rig.capes]
        elif mode == "floor":
            # Lying down: spread out on the ground behind his shoulders, away from his middle.
            mid = m.point(mats, "belly", m["belly"].pivot)
            away = np.array([attach[0] - mid[0], 0.0, attach[2] - mid[2]])
            if np.linalg.norm(away) < 1e-3:
                away = np.array([0.0, 0.0, 1.0])
            away = R.normalize(away)
            want = [R.normalize(away + R.DOWN * (0.8 if i == 0 else 0.1)) for i in range(len(rig.capes))]
        elif mode == "trail":
            # Along his back towards his tail (diving head first: it follows him down the hole), swung just clear.
            want = [spine_down.copy() for _ in rig.capes]
        elif mode == "blanket":
            side = R.normalize(R.world_dir(m, mats, "chest", (1.0, 0.0, 0.0)))
            want = [R.normalize(spine_down + side * (0.9 - 0.4 * i) + back * 0.3) for i in range(len(rig.capes))]
        elif mode == "stream":
            # Running: the air streams it out behind him, each panel drooping a little more, rippling in waves that
            # run down it (CAPE_STREAM_*; whole cycles per loop, so a running loop stays seamless).
            t = getattr(self, "t", 0.0)
            want = []
            for i in range(len(rig.capes)):
                ripple = CAPE_STREAM_RIPPLE * (0.5 + 0.5 * i) * math.sin(
                    2 * math.pi * CAPE_STREAM_FREQ * t - CAPE_STREAM_PHASE * i)
                want.append(R.normalize(np.array([0.0, -(CAPE_STREAM_DROOP[min(i, len(CAPE_STREAM_DROOP) - 1)])
                                                  + ripple, 1.0])))
        else:
            drag = -vel * 0.02
            n = np.linalg.norm(drag)
            if n > 0.55:
                drag = drag * (0.55 / n)
            drag[1] = min(max(drag[1], -0.3), 0.35)
            want = [R.normalize(d + drag * (0.6 + 0.4 * i)) for i, d in enumerate(R.cape_dirs(b, back=np.array([back[0], 0.0, back[2]])))]
            # Bent far over, the top panel lies along his back instead of hanging off it.
            up = R.normalize(R.world_dir(m, mats, "chest", (0.0, 1.0, 0.0)))
            tilt = math.degrees(math.acos(max(-1.0, min(1.0, up[1]))))
            hug = clamp01((tilt - 35.0) / 30.0)
            if hug > 0:
                want[0] = R.normalize(want[0] * (1.0 - hug) + R.normalize(spine_down + back * 0.25) * hug)
        if self.cape is not None and lag > 0:
            want = [R.normalize(p * lag + w * (1.0 - lag)) for p, w in zip(self.cape, want)]
        self.cape = want
        if mode in ("hang", "trail", "stream"):
            R.drape(m, pose, b, want, names=rig.capes, floor=floor, prev=self.euler)
        else:
            mats = m.pose_matrices(pose.as_geo())
            for i, name in enumerate(rig.capes):
                bone = m[name]
                start = m.point(mats, name, bone.pivot)
                d = R.clamp_above(start, want[i], b.cape[i], floor) if floor > -50 else want[i]
                local = R.local_dir(mats, bone.parent.name, d)
                tot = R.aim_axis((0, -1, 0), local, prev=self.euler.get(name))
                self.euler[name] = tot
                R.set_total(pose, m, name, tot)
                mats = m.pose_matrices(pose.as_geo())

    def _hangers(self, pose):
        """The censer's chain and the ribbons hang down (with lag)."""
        rig = self.rig
        m = rig.m
        names = (["censer_chain"] if rig.has_censer else []) + rig.ribbons
        if not names:
            return
        mats = m.pose_matrices(pose.as_geo())
        for name in names:
            bone = m[name]
            start = m.point(mats, name, bone.pivot)
            prev = self.hang.get(name)
            last_start = self.hang.get(name + "@")
            vel = np.zeros(3) if last_start is None else (start - last_start) / STEP
            want = R.normalize(R.DOWN - vel * 0.03)
            if prev is not None:
                want = R.normalize(prev * 0.6 + want * 0.4)
            self.hang[name] = want
            self.hang[name + "@"] = start
            local = R.local_dir(mats, bone.parent.name, want)
            tot = R.aim_axis((0, -1, 0), local, prev=self.euler.get(name))
            self.euler[name] = tot
            R.set_total(pose, m, name, tot)
            mats = m.pose_matrices(pose.as_geo())

    def _flames(self, pose, t):
        for i, name in enumerate(self.rig.flames):
            f = 1.0 + 0.18 * wobble(t, i * 3.1 + 1.0, 7.0)
            pose.scale(name, f)


# ---------------------------------------------------------------- recording


def record(rig, name, length, loop, frame, warm=0, step=STEP, bake=(0.3, 0.05, 0.01)):
    """
    Runs frame(t, sec) over the animation (after `warm` throwaway laps, for seamless loops) and bakes it. Every frame
    then goes through the staff guard (ratkit/guard.py), which keeps the staff out of him and the floor.
    """
    a = Animation(name, length, loop)
    rig.reset_warm()
    PREV.clear()
    sec = Secondary(rig)
    guard = rig.guard
    if guard is not None:
        guard.reset()

    def run(t, report=False):
        p = frame(t, sec)
        if guard is not None:
            guard.apply(p, lambda pose, M: place(rig, pose, "staff", M))
            if report and guard.last_cost > GUARD_REPORT:
                print(f"    staff guard: {rig.m.identifier} {name} {t:.2f} s still {guard.last_cost:.1f} in something")
        return p

    n = int(round(length / step))
    for _ in range(warm):
        for i in range(n):
            run(i * step)
    for i in range(n + 1):
        t = i * step
        a.add(t, run(t, report=True))
    a.bake(*bake)
    return a


GUARD_REPORT = 0.6      # units: a frame the staff guard couldn't clear by this much is printed by the build


def hold(src, name):
    a = Animation(name, 0.05, "hold_on_last_frame")
    last = src.frames[-1][1]
    a.add(0.0, last)
    a.add(0.05, last)
    a.bake()
    return a


# ---------------------------------------------------------------- idle


IDLE_T = 4.0


def idle_pose(rig, t, p, k=1.0, T=IDLE_T):
    """His idle motion at time t (looping over T), added into pose p at strength k."""
    st = rig.st
    w = 2 * math.pi * t / T
    breath = math.sin(2 * w)
    p.pos("body", 0.0, -0.3 * (1 - math.cos(2 * w)) * k, 0.0)
    p.rot("belly", 0.8 * breath * k)
    p.rot("chest", -1.6 * breath * k, 0.0, 0.0)
    # Shifty glances: right, back, left, back; a quick double sniff.
    look = keyed(t % T, [(0.0, 0.0), (0.5, 0.0), (0.75, -24.0), (1.4, -24.0), (1.7, 0.0), (2.3, 0.0), (2.55, 26.0),
                         (3.1, 26.0), (3.45, 0.0), (T, 0.0)])
    sniff = bump(t % T, 1.75, 1.8, 1.85, 1.95) + bump(t % T, 1.95, 2.0, 2.05, 2.15)
    head(p, rx=(1.0 * math.sin(2 * w + 0.6) - 3.0 * sniff) * k, ry=float(look) * k, rz=-0.12 * float(look) * k,
         jaw=1.0 * sniff * k)
    p.rot("neck", 0.0, 0.25 * float(look) * k, 0.0)
    # Ears twitch now and then.
    ears(p, rx=-10.0 * bump(t % T, 1.0, 1.05, 1.1, 1.2) * k, rz=8.0 * bump(t % T, 3.3, 3.35, 3.4, 3.5) * k,
         l=1.0, r=0.6)
    # A blink.
    lids(p, rig, max(bump(t % T, 2.2, 2.25, 2.3, 2.38), bump(t % T, 0.15, 0.2, 0.25, 0.33)) * k)
    gesture = st.get("idle", "drum")
    if gesture == "drum":
        # Free hand: fingers drumming twice (a quick little bob of the hand).
        drum = bump(t % T, 0.4, 0.5, 1.2, 1.3) + bump(t % T, 2.6, 2.7, 3.3, 3.4)
        p.rot("hand_l", 14.0 * drum * math.sin(t * 2 * math.pi * 7.0) * k, 0.0, 0.0)
        p.rot("forearm_l", 4.0 * drum * k)
    elif gesture == "monocle":
        adj = bump(t % T, 0.9, 1.3, 1.7, 2.1)
        p.rot("arm_l", -45.0 * adj * k, -20.0 * adj * k, -10.0 * adj * k)
        p.rot("forearm_l", -40.0 * adj * k, 0.0, 0.0)
        puff = bump(t % T, 2.4, 2.9, 3.3, 3.7)
        p.rot("chest", -4.0 * puff * k)
        p.rot("head", -6.0 * puff * k)
    elif gesture == "pipe":
        tap = bump(t % T, 0.5, 0.6, 1.4, 1.5) + bump(t % T, 2.4, 2.5, 3.0, 3.1)
        p.rot("hand_l", 10.0 * tap * math.sin(t * 2 * math.pi * 5.0) * k)
        p.rot("arm_l", -6.0 * tap * k)
    elif gesture == "bless":
        sway = math.sin(w)
        p.rot("arm_l", -6.0 * sway * k, 0.0, 3.0 * sway * k)
        p.rot("arm_r", 3.0 * math.sin(w + 1.0) * k, 0.0, 2.0 * math.sin(w) * k)
    elif gesture == "cane":
        trem = 0.6 * math.sin(t * 2 * math.pi * 6.0)
        p.rot("head", trem * k, 0.0, 0.0)
        cough = bump(t % T, 2.9, 2.95, 3.0, 3.15) + bump(t % T, 3.15, 3.2, 3.25, 3.4)
        p.pos("body", 0.0, -0.5 * cough * k, 0.0)
        p.rot("chest", 6.0 * cough * k)
        p.rot("head", 8.0 * cough * k)
        p.rot("jaw", 10.0 * cough * k)
    elif gesture == "fork":
        tap = bump(t % T, 0.6, 0.7, 0.75, 0.85) + bump(t % T, 0.9, 1.0, 1.05, 1.15) + bump(t % T, 2.8, 2.9, 2.95, 3.05)
        p.rot("arm_r", -6.0 * tap * k, 0.0, 0.0)
        p.rot("forearm_r", -8.0 * tap * k)
    return p


def idle(rig):
    def frame(t, sec):
        p = Pose()
        idle_pose(rig, t, p)
        plant_feet(rig, p)
        sec.apply(p, t, wave=1.0, tail_wave=(7.0, 0.5, 0.9))
        return p

    return record(rig, "idle", IDLE_T, True, frame, warm=1)


# ---------------------------------------------------------------- walk


def walk(rig):
    ws = WALKS[rig.st.get("walk", "sneak")]
    T = ws["T"]
    plant = rig.st.get("plant_staff", False) and rig.has_staff
    stance = 0.62

    def frame(t, sec):
        ph = t / T
        w = 2 * math.pi * ph
        p = Pose()
        p.pos("body", ws["sway"] * math.sin(w), -ws["crouch"] - ws["bob"] * (0.5 + 0.5 * math.cos(2 * w)), 0.0)
        p.rot("hips", ws["lean"] * 0.4 + 1.5 * math.cos(2 * w), -4.0 * math.sin(w), -1.2 * math.sin(w))
        p.rot("belly", ws["lean"] * 0.3, 2.0 * math.sin(w), 0.0)
        p.rot("chest", ws["lean"] * 0.3 - 1.2 * math.cos(2 * w + 0.5), 5.0 * math.sin(w + 0.3), ws["roll"] * math.sin(w))
        head(p, rx=-ws["lean"] * 0.8 + ws["head"] * 0.3 * math.cos(2 * w), ry=-4.0 * math.sin(w + 0.3),
             rz=-0.6 * ws["roll"] * math.sin(w))
        offs = {}
        down = {}
        for name, off in (("leg_l", 0.0), ("leg_r", 0.5)):
            dz, dy, u = gait_offset(ph - off, stance, ws["stride"] * stance, ws["lift"])
            offs[name] = (0.0, dy, dz)
            if ws["tiptoe"]:
                # Up on his toes the whole way (heel up, toes down): the ball of the foot lifts so the toe tips, not
                # the middle of the sole, touch the floor.
                tilt = math.radians(ws["tiptoe"])
                down[name] = np.array([0.0, -math.cos(tilt), math.sin(tilt)])
                toe = rig.b.foot_len / 2.0 + 1.5
                offs[name] = (0.0, dy + toe * math.sin(tilt) + 0.5, dz)
            else:
                down[name] = R.DOWN
        plant_feet(rig, p, offs, down)
        # Free arm swings with the right leg; the staff arm swings a little, or plants the staff.
        p.rot("arm_l", ws["arm"] * math.sin(w), 0.0, 0.0)
        p.rot("forearm_l", -0.3 * ws["arm"] * (0.5 + 0.5 * math.sin(w)), 0.0, 0.0)
        if plant:
            dz, dy, u = gait_offset(ph - 0.05, 0.55, ws["stride"] * 0.55, 3.0)
            tgt = rig.staff_foot_rest + np.array([0.0, dy, dz])
            plant_staff(rig, p, tgt)
        elif rig.st.get("walk") == "strut" and rig.has_staff:
            p.rot("arm_r", -10.0 * math.sin(w), 0.0, 0.0)
            p.rot("staff", 8.0 * math.sin(w), 0.0, 0.0)
        elif rig.st.get("walk") == "swagger":
            p.rot("arm_r", -4.0 * math.cos(2 * w), 0.0, 0.0)
            p.rot("staff", 3.0 * math.cos(2 * w), 0.0, 0.0)
        else:
            p.rot("arm_r", -0.4 * ws["arm"] * math.sin(w), 0.0, 0.0)
        ears(p, rx=-4.0 * math.cos(2 * w))
        sec.apply(p, t, wave=1.0, tail_wave=(10.0, 1.0 / T, 0.9))
        return p

    a = record(rig, "walk", T, True, frame, warm=2)
    a.meta["stride_blocks"] = ws["stride"] / 16.0
    return a


# ---------------------------------------------------------------- sleep (and the start of wake)


SLEEP_T = 4.0


def slump_pose(rig, p, breathe=0.0, k=1.0):
    """Slumped on his staff: knees sagging, leaning on the staff planted in front of him, his chin on his hands."""
    p.pos("body", 0.0, -4.5 * k - 0.6 * breathe, 0.0)
    p.rot("hips", 4.0 * k)
    p.rot("belly", 5.0 * k + 1.2 * breathe)
    p.rot("chest", 8.0 * k - 2.0 * breathe)
    p.rot("neck", 16.0 * k)
    head(p, rx=24.0 * k + 1.5 * breathe, ry=0.0, rz=-14.0 * k, jaw=3.0 * k + 4.0 * max(0.0, breathe))
    ears(p, rx=18.0 * k, rz=-24.0 * k)
    return p


SLUMP_SLIDE = 13.0


def slump_arms(rig, p, k=1.0):
    """The staff planted in front of him, both hands high on it under his chin."""
    if not rig.has_staff:
        return
    slide_staff(rig, p, SLUMP_SLIDE * k)
    tgt = rig.staff_foot_rest + (np.array([-0.5, 0.0, -13.0]) - rig.staff_foot_rest) * k
    tgt[1] = 0.0 - SLUMP_SLIDE * 0.0
    up = R.normalize(rig.staff_up_rest * (1.0 - k) + np.array([0.04, 1.0, 0.30]) * k)
    plant_staff(rig, p, tgt, up)
    if k > 0.01:
        mats = mats_of(rig, p)
        grip = rig.m.point(mats, "staff", rig.m["staff"].pivot)
        sup = R.normalize(rig.m.direction(mats, "staff", (0.0, 1.0, 0.0)))
        # The free hand just above the staff hand.
        want = grip + sup * 3.5 + np.array([0.6, 0.0, 0.0])
        free = Pose()
        for kk, v in p.items():
            free[kk] = [list(v[0]), list(v[1]), list(v[2])]
        reach_hand(rig, free, want, "l")
        for bone in ("arm_l", "forearm_l", "hand_l"):
            cur = p.get3(bone)[0]
            fv = free.get3(bone)[0]
            p.get3(bone)[0] = [cur[i] * (1.0 - k) + fv[i] * k for i in range(3)]


CURL = {
    # total rotations while curled up on his side (his right side down)
    "hips": (40.0, 0.0, 0.0), "belly": (30.0, 0.0, 0.0), "chest": (28.0, 0.0, 0.0), "neck": (25.0, 0.0, 0.0),
    "head": (10.0, 0.0, 10.0),
    "thigh_l": (-95.0, 0.0, 6.0), "shin_l": (120.0, 0.0, 0.0), "foot_l": (-30.0, 0.0, 0.0),
    "thigh_r": (-85.0, 0.0, -4.0), "shin_r": (125.0, 0.0, 0.0), "foot_r": (-35.0, 0.0, 0.0),
    "arm_l": (-70.0, 10.0, -6.0), "forearm_l": (-110.0, 0.0, 0.0), "hand_l": (0.0, 0.0, 0.0),
    "arm_r": (-80.0, -10.0, 8.0), "forearm_r": (-100.0, 0.0, 0.0), "hand_r": (0.0, 0.0, 0.0),
}
CURL_ROLL = 82.0        # lies on his right side
CURL_LIFT = 7.5         # his side's height off the ground


def curl_pose(rig, p, breathe=0.0, k=1.0):
    set_limb_totals(rig, p, CURL, k)
    p.rot("chest", -1.5 * breathe * k)
    p.rot("belly", 1.0 * breathe * k)
    R.rotate_body_about(p, (0.0, 0.0, -CURL_ROLL * k), (0.0, rig.b.hip_y * 0.5, 0.0))
    p.pos("body", 0.0, 0.0, 2.0 * k)
    head(p, jaw=2.0 * max(0.0, breathe) * k)
    ears(p, rx=20.0 * k, rz=-25.0 * k)
    return p


def settle_on_ground(rig, p, k=1.0, margin=0.2):
    """Moves the whole body up or down so its lowest box just touches the ground (for lying poses)."""
    m = rig.m
    mats = mats_of(rig, p)
    lo = _lowest(m, mats)
    p.pos("body", 0.0, (-lo + margin) * k, 0.0)


def _lowest(m, mats):
    low = 1e9
    for bone in m.bones:
        names = [a.name for a in bone.ancestors()]
        if not bone.cubes or "hole" in names or bone.name in ("staff",) or any(n.startswith(("tail_", "cape_", "staff", "censer", "ribbon")) for n in names):
            continue
        for c in bone.cubes:
            cm = mats[bone.name] @ c.cube_matrix()
            ox, oy, oz = c.origin
            w, h, d = c.size
            for dx in (0, w):
                for dy in (0, h):
                    for dz in (0, d):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        low = min(low, q[1] * 16.0)
    return low


def _highest(m, mats, root):
    """The highest point (y, units) of a bone's boxes and everything hanging from it."""
    high = -1e9
    for bone in m.bones:
        if not bone.cubes or root not in [a.name for a in bone.ancestors()]:
            continue
        for c in bone.cubes:
            cm = mats[bone.name] @ c.cube_matrix()
            ox, oy, oz = c.origin
            w, h, d = c.size
            for dx in (0, w):
                for dy in (0, h):
                    for dz in (0, d):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        high = max(high, q[1] * 16.0)
    return high


def staff_down(rig, p, hole=None):
    """The staff standing upright in the hole (`hole`: its middle (x, z), model units; the burrow's hole in front of
    him unless given) but wholly under the ground, so it doesn't poke out before him."""
    hx, hz = (0.0, -HOLE_FORWARD) if hole is None else hole
    grip = np.array([hx - 0.6 * PT.hole_radius(rig.b), -rig.staff_above - 2.0, hz])
    return frame_matrix(rig, "staff", grip, np.array([0.0, 1.0, 0.0]), np.array([0.0, 0.0, -1.0]))


def staff_on_ground(rig, where, along):
    """The staff lying flat on the ground: its grip at `where` (x, z), its shaft along `along` (x, z), resting on its
    lowest point (the claw and the orb are wider than the shaft)."""
    a = R.normalize(np.array([along[0], 0.0, along[1]]))
    grip = np.array([where[0], 1.2, where[1]])
    M = frame_matrix(rig, "staff", grip, a, R.UP)
    return rest_on_floor(rig, M)


def rest_on_floor(rig, M, gap=0.1):
    """Moves a staff placement (GeckoLib matrix) up or down so its lowest point is just on the floor."""
    low = float((M @ rig.staff_pts.T)[1].min()) * 16.0
    M = M.copy()
    M[1, 3] += (gap - low) / 16.0
    return M


def curled(rig, p, t, sec):
    """The curled-up sleep at time t (without the staff)."""
    w = 2 * math.pi * t / SLEEP_T
    breathe = math.sin(2 * w)
    curl_pose(rig, p, breathe)
    settle_on_ground(rig, p)
    ears(p, rx=-10.0 * bump(t, 2.6, 2.65, 2.7, 2.85))
    sec.apply(p, t, wave=0.3, cape_mode="floor", cape_floor=0.5, tail_floor=True, tail_wave=(3.0, 0.25, 0.9),
              tail_mode="curl")
    return p


BED_CLEARANCE = 0.6       # units the staff lying beside him keeps clear of him, his tail and his cape
BED_SEARCH = 22.0         # units round STAFF_BED searched for a clear spot (the hulking build is wide)


def staff_bed(rig):
    """
    Where the staff lies while he sleeps curled up, as (grip x z, shaft x z): the spot nearest STAFF_BED (and its
    direction nearest STAFF_BED_ALONG) where it lies on the floor clear of him, his tail and his cape all through the
    sleep and while he rolls up and stands when he wakes (the user's note on round 1: the staff mustn't go through him
    or the floor). Worked out once per design: the builds are different widths and depths.
    """
    if getattr(rig, "bed", None) is not None:
        return rig.bed
    from .clip import _box_frame, _grid, _group, _skip_cube
    sec = Secondary(rig)
    frames = []
    n = int(round(SLEEP_T / STEP))
    for lap in range(2):
        for i in range(n):
            p = curled(rig, Pose(), i * STEP, sec)
            if lap == 1 and i % 8 == 0:
                frames.append(mats_of(rig, p))
    frames += wake(rig, dry=True)
    obstacles = []
    for mats in frames:
        for bone in rig.m.bones:
            g = _group(bone)
            if g in (None, "staff") or not bone.cubes:
                continue
            for c in bone.cubes:
                if not _skip_cube(g, c):
                    obstacles.append(_box_frame(mats, c))
    inv = np.array([o[1] for o in obstacles])
    lo = np.array([o[2] for o in obstacles])
    hi = np.array([o[3] for o in obstacles])

    def clear(where, along):
        M = staff_on_ground(rig, where, along)
        P = (M @ rig.staff_pts.T).T[:, :3]
        q = np.einsum("kij,nj->kni", inv[:, :3, :3], P) + inv[:, None, :3, 3]
        d = np.minimum(q - lo[:, None, :], hi[:, None, :] - q).min(axis=2) * 16.0
        return float(d.max()) < -BED_CLEARANCE

    a0 = math.degrees(math.atan2(STAFF_BED_ALONG[0], STAFF_BED_ALONG[1]))
    best = None
    for dx in np.arange(-BED_SEARCH, BED_SEARCH + 0.01, 2.0):
        for dz in np.arange(-BED_SEARCH, BED_SEARCH + 0.01, 2.0):
            for da in (0.0, 15.0, -15.0, 30.0, -30.0, 50.0, -50.0, 75.0, -75.0, 100.0, -100.0, 130.0, -130.0, 160.0,
                       -160.0, 180.0):
                cost = math.hypot(dx, dz) + 0.06 * abs(da)
                if best is not None and cost >= best[0]:
                    continue
                where = (STAFF_BED[0] + dx, STAFF_BED[1] + dz)
                a = math.radians(a0 + da)
                along = (math.sin(a), math.cos(a))
                if clear(where, along):
                    best = (cost, where, along)
    if best is None:
        print(f"  warning: {rig.m.identifier}: no clear spot for the staff beside him while he sleeps")
        best = (0.0, STAFF_BED, STAFF_BED_ALONG)
    rig.bed = (best[1], best[2])
    return rig.bed


def sleep(rig):
    style = rig.st.get("sleep", "slump")
    bed = staff_bed(rig) if style != "slump" and rig.has_staff else None

    def frame(t, sec):
        p = Pose()
        w = 2 * math.pi * t / SLEEP_T
        breathe = math.sin(2 * w)
        if style == "slump":
            slump_pose(rig, p, breathe)
            plant_feet(rig, p)
            slump_arms(rig, p)
            ears(p, rx=-8.0 * bump(t, 2.6, 2.65, 2.7, 2.85))
            sec.apply(p, t, wave=0.4, tail_wave=(4.0, 0.25, 0.9))
        else:
            curled(rig, p, t, sec)
            if bed is not None:
                place(rig, p, "staff", staff_on_ground(rig, *bed))
        lids(p, rig, 1.0)
        return p

    a = record(rig, "sleep", SLEEP_T, True, frame, warm=1)
    a.meta["zzz"] = True
    return a


# ---------------------------------------------------------------- wake


WAKE_T = 3.6
WAKE_STARTLE = 0.45
GRAB = 2.22
STAFF_BED = (-9.0, -7.0)          # where the staff lies while he sleeps curled up (its grip, x and z)
STAFF_BED_ALONG = (0.35, 1.0)     # which way it lies


def _copy(p):
    out = Pose()
    for k_, v in p.items():
        out[k_] = [list(v[0]), list(v[1]), list(v[2])]
    return out


def _blend_bones(p, other, bones, k):
    for bone in bones:
        cur = p.get3(bone)[0]
        ov = other.get3(bone)[0]
        p.get3(bone)[0] = [cur[i] * (1.0 - k) + ov[i] * k for i in range(3)]


LEGS = ("thigh_l", "shin_l", "foot_l", "thigh_r", "shin_r", "foot_r")
ARM_R = ("arm_r", "forearm_r", "hand_r")


WAKE_DRY_UNTIL = 1.95     # seconds: up to here the wake doesn't depend on where the staff lies (he reaches at 2.0)


def wake(rig, dry=False):
    """The wake. dry=True: no staff and nothing recorded; returns his poses (matrices) up to WAKE_DRY_UNTIL, so
    staff_bed can keep the staff clear of him while he rolls up too."""
    style = rig.st.get("sleep", "slump")
    held = {}

    def frame(t, sec):
        p = Pose()
        startle = ramp(t, WAKE_STARTLE, WAKE_STARTLE + 0.08)
        jolt = bump(t, WAKE_STARTLE, WAKE_STARTLE + 0.08, WAKE_STARTLE + 0.12, WAKE_STARTLE + 0.45)
        if style == "slump":
            look = keyed(t, [(0.0, 0.0), (0.7, 0.0), (0.9, -30.0), (1.15, -30.0), (1.35, 28.0), (1.6, 28.0), (1.8, 0.0)])
            stretch = bump(t, 1.8, 2.25, 2.55, 2.85)
            yawn = bump(t, 1.95, 2.25, 2.45, 2.7)
            stand = ramp(t, 2.6, 3.4)
            k = 1.0 - ramp(t, WAKE_STARTLE, WAKE_STARTLE + 0.3) * 0.75 - 0.25 * ramp(t, 1.7, 2.2)
            slump_pose(rig, p, 0.0, k)
            p.pos("body", 0.0, 3.0 * jolt, 0.0)
            plant_feet(rig, p)
            # Hands on the staff; it wobbles as he jerks awake, then he grips it properly.
            slump_arms(rig, p, 1.0 - ramp(t, 2.6, 3.3))
            p.rot("staff", 18.0 * jolt * math.sin(t * 40.0), 0.0, 10.0 * jolt)
            cape = "hang"
        else:
            # Curled up: jolts awake, rolls up into a crouch, looks round, stretches and yawns, bends down to pick his
            # staff up off the ground, and stands.
            look = keyed(t, [(0.0, 0.0), (0.9, 0.0), (1.05, -30.0), (1.25, -30.0), (1.4, 28.0), (1.55, 28.0), (1.7, 0.0)])
            stretch = bump(t, 1.55, 1.8, 1.9, 2.05)
            yawn = bump(t, 1.6, 1.8, 1.88, 2.0)
            stand = ramp(t, 2.7, 3.45)
            roll = 1.0 - ramp(t, WAKE_STARTLE + 0.05, 1.2)
            bend = bump(t, 2.0, 2.2, 2.3, 2.65)
            curl_pose(rig, p, 0.0, roll)
            crouch = (1.0 - roll) * (1.0 - stand) * 4.0 + 4.0 * bend
            p.pos("body", 0.0, 4.0 * jolt * roll - crouch, 0.0)
            p.rot("hips", 22.0 * bend)
            p.rot("chest", 30.0 * bend, -14.0 * bend, 0.0)
            p.rot("belly", 20.0 * bend)
            if roll < 0.999:
                feet = _copy(p)
                plant_feet(rig, feet)
                _blend_bones(p, feet, LEGS, 1.0 - roll)
                # Rolling up off his side: his feet and toes never dip into the floor on the way.
                lo = _lowest(rig.m, mats_of(rig, p))
                if lo < 0.05:
                    p.pos("body", 0.0, 0.05 - lo, 0.0)
            if rig.has_staff and not dry:
                M_ground = staff_on_ground(rig, *staff_bed(rig))
                if t < GRAB:
                    if bend > 0.0:
                        g = M_ground @ np.append(geo.gecko(rig.m["staff"].pivot), 1.0)
                        target = geo.bedrock(g[:3]) + np.array([0.0, 1.5, 0.0])
                        arm = _copy(p)
                        reach_hand(rig, arm, target, "r", warm_key="grab")
                        _blend_bones(p, arm, ARM_R, bend)
                    held["now"] = ("world", M_ground)
                else:
                    # In his hand now: it turns from the way it lay on the ground to the way he holds it.
                    if "local" not in held:
                        arm = _copy(p)
                        g = M_ground @ np.append(geo.gecko(rig.m["staff"].pivot), 1.0)
                        reach_hand(rig, arm, geo.bedrock(g[:3]) + np.array([0.0, 1.5, 0.0]), "r", warm_key="grab")
                        _blend_bones(p, arm, ARM_R, bend)
                        held["local"] = np.linalg.inv(mats_of(rig, p)["hand_r"]) @ M_ground
                        held["arm"] = {b_: list(p.get3(b_)[0]) for b_ in ARM_R}
                    u = smooth(clamp01((t - GRAB) / 0.4))
                    # The arm comes back from the reach to its usual hold.
                    for b_ in ARM_R:
                        cur = p.get3(b_)[0]
                        p.get3(b_)[0] = [held["arm"][b_][i] * (1.0 - u) + cur[i] * u for i in range(3)]
                    held["now"] = ("hand", blend_matrix(held["local"], rig.staff_local_rest, u))
            cape = "floor" if roll > 0.97 else ("gravity" if roll > 0.3 else "hang")
        # Both: eyes open at the startle, look round, stretch and yawn, settle into idle.
        lids(p, rig, 1.0 - startle + yawn)
        head(p, rx=-16.0 * jolt - 14.0 * yawn, ry=float(look), rz=0.0, jaw=34.0 * yawn + 10.0 * jolt)
        ears(p, rx=-22.0 * jolt, rz=10.0 * jolt)
        p.rot("chest", -16.0 * stretch)
        p.rot("belly", -8.0 * stretch)
        set_limb_totals(rig, p, {"arm_l": (-165.0, 0.0, -28.0), "forearm_l": (-25.0, 0.0, 0.0)}, stretch)
        if style == "slump":
            p.rot("arm_r", 0.0, 0.0, 10.0 * stretch)
        else:
            set_limb_totals(rig, p, {"arm_r": (-160.0, 0.0, 28.0), "forearm_r": (-25.0, 0.0, 0.0)}, stretch)
        idle_pose(rig, 0.0, p, stand)
        # The staff last, once every arm move of this frame is in.
        if style != "slump" and rig.has_staff and "now" in held and not dry:
            kind, M = held["now"]
            place(rig, p, "staff", M if kind == "world" else mats_of(rig, p)["hand_r"] @ M)
        sec.apply(p, t, wave=0.5 + 0.5 * stand, cape_mode=cape, extra_tail=lambda i, tt: -40.0 * jolt,
                  tail_mode="curl" if style != "slump" and t < WAKE_STARTLE else "follow")
        return p

    if dry:
        rig.reset_warm()
        PREV.clear()
        sec = Secondary(rig)
        out = []
        for k in range(int(round(WAKE_DRY_UNTIL / STEP)) + 1):
            p = frame(k * STEP, sec)
            if k % 2 == 0:
                out.append(mats_of(rig, p))
        rig.reset_warm()
        PREV.clear()
        return out
    a = record(rig, "wake", WAKE_T, "hold_on_last_frame", frame)
    a.meta["startle"] = WAKE_STARTLE
    # When the mod plays his yawn and the tap of his staff as he takes hold of it.
    a.meta["yawn"] = 1.6 if style != "slump" else 1.95
    a.meta["grab"] = GRAB if style != "slump" else 2.6
    return a


# ---------------------------------------------------------------- burrow and popup


BURROW_T = 2.8
DIG_FROM, DIG_TO, DIVE = 0.55, 1.35, 1.45
HOLE_FORWARD = PT.HOLE_FORWARD   # units in front of his feet: the hole he digs, dives into and bursts out of
DIG_LEAN = 55.0           # degrees he tips forward (about his hips) to dig at it
DIG_DROP = 6.0            # units he crouches to dig
DIVE_ANGLE = 70.0         # degrees below level that he goes into the hole at, head first
HOP = 0.3                 # share of the dive spent hopping up and over, head down, onto the hole
HOP_LIFT = 7.0            # units his head rises in the hop
DIVE_DEPTH = 96.0         # units he slides down the hole (his whole body and tail end up out of sight)


def hole_centre():
    return np.array([0.0, 0.0, -HOLE_FORWARD])


def head_point(rig, mats):
    """The middle of his skull (Bedrock model space)."""
    h = rig.m["head"]
    return rig.m.point(mats, "head", (h.pivot[0], h.pivot[1] + rig.b.skull[1] / 2.0, h.pivot[2]))


def dig_pose(rig, p, t, crouch, dig):
    """Crouched low over the hole spot, tipped forward: the free hand claws at it, the staff planted beside it."""
    b = rig.b
    p.pos("body", 0.0, -DIG_DROP * crouch, 0.0)
    R.rotate_body_about(p, (DIG_LEAN * crouch, 0.0, 0.0), (0.0, b.hip_y, 0.0))
    plant_feet(rig, p)
    # Crouched this low his legs can't fold any further: lift him so his feet stay on the ground, not in it.
    lo = _lowest(rig.m, mats_of(rig, p))
    if lo < 0.05:
        p.pos("body", 0.0, 0.05 - lo, 0.0)
        plant_feet(rig, p)
    if crouch > 0.0:
        ph = t * 2 * math.pi * 4.0
        scrape = 4.0 * math.sin(ph) * dig
        lift = 2.5 * max(0.0, math.cos(ph)) * dig
        hc = hole_centre()
        want = hc + np.array([2.5, 1.5 + lift, 2.0 + scrape])
        arm = _copy(p)
        reach_hand(rig, arm, want, "l", warm_key="dig")
        _blend_bones(p, arm, ("arm_l", "forearm_l", "hand_l"), crouch)
        if rig.has_staff:
            st = _copy(p)
            plant_staff(rig, st, hc + np.array([-11.0, 0.0, 3.0]), np.array([-0.25, 1.0, -0.15]))
            _blend_bones(p, st, ARM_R, crouch)


def dive_shape(rig, p, w):
    """His shape in the dive (w 0: as he digs .. 1: stretched out like a diver, arms ahead, legs straight)."""
    straighten(rig, p, 0.75 * w)
    set_limb_totals(rig, p, {"arm_l": (-168.0, 0.0, -8.0), "forearm_l": (-8.0, 0.0, 0.0), "hand_l": (0.0, 0.0, 0.0),
                             "arm_r": (-150.0, 0.0, 34.0), "forearm_r": (-10.0, 0.0, 0.0), "hand_r": (0.0, 0.0, 0.0),
                             "thigh_l": (8.0, 0.0, 4.0), "thigh_r": (8.0, 0.0, -4.0),
                             "shin_l": (14.0, 0.0, 0.0), "shin_r": (14.0, 0.0, 0.0),
                             "foot_l": (40.0, 0.0, 0.0), "foot_r": (40.0, 0.0, 0.0)}, w)


def _axis_lean(rig, mats):
    """How far his hips-to-head axis leans forward from upright (degrees), and the axis."""
    ax = R.normalize(head_point(rig, mats) - rig.m.point(mats, "hips", rig.m["hips"].pivot))
    return math.degrees(math.atan2(-ax[2], ax[1])), ax


def burrow(rig):
    state = {}

    def frame(t, sec):
        p = Pose()
        p.hole = (0.0, -HOLE_FORWARD)
        glance = keyed(t, [(0.0, 0.0), (0.15, -32.0), (0.32, -32.0), (0.42, 30.0), (0.52, 30.0), (0.62, 0.0)])
        crouch = smooth(ramp(t, 0.45, 0.75))
        dig = bump(t, DIG_FROM, DIG_FROM + 0.08, DIG_TO - 0.08, DIG_TO)
        dv = clamp01((t - DIVE) / (BURROW_T - 0.15 - DIVE))
        if dv <= 0.0:
            dig_pose(rig, p, t, crouch, dig)
            head(p, rx=12.0 * crouch, ry=float(glance))
            ears(p, rx=-8.0 * crouch)
            sec.apply(p, t, wave=1.0)
        else:
            # The dive: the dig pose turns into the diver's shape; he hops up and over, head down, onto the hole and
            # slides down it head first: his whole body goes down through the hole.
            hop = smooth(clamp01(dv / HOP))
            sl = clamp01((dv - HOP) / (1.0 - HOP))
            if "start" not in state:
                # Where his head was as the dive began (the dig pose's last frame), and how far he leaned.
                q = Pose()
                dig_pose(rig, q, DIVE, 1.0, 0.0)
                head(q, rx=12.0)
                mq = mats_of(rig, q)
                state["start"] = head_point(rig, mq)
                state["lean0"] = _axis_lean(rig, mq)[0]
            dig_pose(rig, p, DIVE, 1.0, 0.0)
            head(p, rx=12.0 * (1.0 - hop) - 20.0 * hop)
            # The dive places the body itself: undo the dig's tilt and crouch.
            p.get3("body")[0] = [0.0, 0.0, 0.0]
            p.get3("body")[1] = [0.0, 0.0, 0.0]
            dive_shape(rig, p, hop)
            lean_now, _ax = _axis_lean(rig, mats_of(rig, p))
            lean = state["lean0"] + (90.0 + DIVE_ANGLE - state["lean0"]) * hop
            d = np.array([0.0, -math.sin(math.radians(DIVE_ANGLE)), -math.cos(math.radians(DIVE_ANGLE))])
            entry = hole_centre() - d * 3.0
            if sl <= 0.0:
                h = state["start"] * (1.0 - hop) + entry * hop + np.array([0.0, HOP_LIFT * math.sin(math.pi * hop), 0.0])
            else:
                h = entry + d * (DIVE_DEPTH * sl ** 1.5)
            p.rot("body", lean - lean_now, 0.0, 0.0)
            A1 = head_point(rig, mats_of(rig, p))
            p.pos("body", *(h - A1))
            # Legs kick as he goes in; ears flat back; the tail flicks and follows him down.
            kick = math.sin(t * 2 * math.pi * 6.0) * smooth(sl) * (1.0 - smooth(clamp01((sl - 0.6) * 3.0)))
            p.rot("thigh_l", 12.0 * kick)
            p.rot("thigh_r", -12.0 * kick)
            ears(p, rx=60.0 * hop, rz=-26.0 * hop)
            if rig.has_staff:
                # The staff goes in with him: along his right arm, its foot first down the hole.
                mats = mats_of(rig, p)
                hr = rig.m["hand_r"].pivot
                fist = rig.m.point(mats, "hand_r", (hr[0], hr[1] - 2.0, hr[2]))
                _l, axis_w = _axis_lean(rig, mats)
                M_dive = frame_matrix(rig, "staff", fist, -axis_w, np.array([1.0, 0.0, 0.0]))
                place(rig, p, "staff", blend_matrix(mats["staff"], M_dive, hop))
            sec.apply(p, t, wave=1.0 - 0.8 * smooth(clamp01((sl - 0.4) * 3)), tail_floor=False, cape_mode="trail",
                      cape_floor=-99.0, lag=0.5, tail_lag=0.55 - 0.45 * smooth(clamp01((sl - 0.4) * 3)),
                      tail_along=(-d, smooth(clamp01((sl - 0.15) * 2.5))),
                      extra_tail=lambda i, tt: 25.0 * math.sin(tt * 18.0) * (i + 1) / len(rig.tails) * smooth(clamp01((sl - 0.3) * 3)) * (1.0 - smooth(clamp01((sl - 0.55) * 4))))
        slide(p, rig, "hole", ramp(t, DIG_FROM, DIG_FROM + 0.35))
        return p

    a = record(rig, "burrow", BURROW_T, "hold_on_last_frame", frame)
    a.meta.update(dig_from=DIG_FROM, dig_to=DIG_TO, dive=DIVE)
    return a


POPUP_T = 2.6
POP_PEEK, POP_BURST, POP_LAND = 0.55, 1.25, 1.62
# The hole he pops up out of is right under him, not the burrow's hole in front of him: its middle is under his head
# as he stands straight up under the ground (popup_hole: on the pick, a sixteenth of a block in front of his feet), and
# he comes straight up out of it and lands on the spot (the user saw the old spring out of a hole in front and back to
# his spot as "going back"). The hole bone is slid back under him for this animation; the Java list gets the spot
# (RatKingDesigns.Design.popupHoleForward) for the dirt particles.
POP_HOLE_RISE = 0.2       # seconds the hole takes to open (rise out of the ground) at the start, with the rumble
POP_HOP = 12.0            # units he springs up above the ground as he bursts out (straight up)
POP_ARC_LEAN = -5.0       # degrees he leans back (chest out) at the top of the spring
POP_LAND_DIP = 1.5        # units he sinks as he lands...
POP_LAND_LEAN = 10.0      # ...and degrees he tips forward into the landing crouch
POP_TAIL_FLOOR = 0.4      # share of the spring after which his tail (trailing out of the hole) stays above the ground
DOWN_THE_HOLE = np.array([0.0, -1.0, 0.0])   # under the ground his tail hangs straight down the hole (tail_along)
POP_TAIL_GAP = 0.6        # units his tail keeps above the floor as it drops from his spring (its steep ends overhang)


def _popup_stand(rig, p):
    """His pose under the ground before he pops up: standing straight up, arms and legs tucked in."""
    straighten(rig, p, 1.0)
    set_limb_totals(rig, p, {"arm_l": (-20.0, 0.0, -10.0), "arm_r": (-20.0, 0.0, 10.0),
                             "thigh_l": (8.0, 0.0, 4.0), "thigh_r": (8.0, 0.0, -4.0),
                             "shin_l": (14.0, 0.0, 0.0), "shin_r": (14.0, 0.0, 0.0)}, 1.0)


def popup_hole(rig):
    """The middle (x, z; model units, his front is -z) of the hole he pops up out of: right under his head as he
    stands straight up under the ground, so his nose and ears come up through its middle."""
    p = Pose()
    _popup_stand(rig, p)
    A = head_point(rig, mats_of(rig, p))
    return float(A[0]), float(A[2])


def popup(rig):
    state = {}
    hx, hz = popup_hole(rig)

    def frame(t, sec):
        p = Pose()
        p.hole = (hx, hz)
        tremble = bump(t, 0.0, 0.1, 0.45, 0.55)
        peek = ramp(t, POP_PEEK, POP_PEEK + 0.25)
        burst = clamp01((t - POP_BURST) / (POP_LAND - POP_BURST))
        land = bump(t, POP_LAND, POP_LAND + 0.06, POP_LAND + 0.12, POP_LAND + 0.4)
        if t < POP_BURST:
            # Underground, standing straight up right under his spot; then his nose and ears poke up out of the hole
            # and sniff about.
            _popup_stand(rig, p)
            if "top" not in state:
                # Keep everything (his crown and ear tips too) under the ground until he peeks.
                mats = mats_of(rig, p)
                h = rig.m["head"]
                state["skull_top"] = rig.m.point(mats, "head", (h.pivot[0], h.pivot[1] + rig.b.skull[1], h.pivot[2]))[1]
                state["top"] = _highest(rig.m, mats, "head")
            under = -state["top"] - 4.0
            rise = -state["skull_top"] + 5.0 + 1.5 * math.sin(t * 9.0) * peek
            p.pos("body", 0.0, under * (1.0 - peek) + rise * peek, 0.0)
            head(p, rx=-12.0 * peek + 6.0 * math.sin(t * 22.0) * peek, ry=18.0 * math.sin(t * 4.0) * peek)
            ears(p, rx=-20.0 * peek, rz=-24.0 * peek)
            if rig.has_staff:
                place(rig, p, "staff", staff_down(rig, p, (hx, hz)))
            # His tail hangs straight down the hole under him, so it follows him up through it.
            sec.apply(p, t, tail_floor=False, cape_mode="body", cape_floor=-99.0, tail_along=(DOWN_THE_HOLE, 1.0))
        else:
            # Springs straight up out of the hole and lands on the spot in a crouch.
            u = burst
            arc = 4.0 * u * (1.0 - u)
            start_y = -state.get("skull_top", rig.head_top[1]) + 5.0
            y = start_y * (1.0 - ease_out(u)) + POP_HOP * arc - POP_LAND_DIP * land
            p.pos("body", 0.0, y, 0.0)
            R.rotate_body_about(p, (POP_ARC_LEAN * arc + POP_LAND_LEAN * land, 0.0, 0.0), (0.0, rig.b.hip_y, 0.0))
            straighten(rig, p, 1.0 - smooth(u))
            if u >= 1.0:
                plant_feet(rig, p)
            else:
                set_limb_totals(rig, p, {"thigh_l": (-60.0, 0.0, 4.0), "thigh_r": (-50.0, 0.0, -4.0),
                                         "shin_l": (90.0, 0.0, 0.0), "shin_r": (80.0, 0.0, 0.0)}, 1.0 - u)
                if u > 0.6:
                    feet = Pose()
                    for k_, v in p.items():
                        feet[k_] = [list(v[0]), list(v[1]), list(v[2])]
                    plant_feet(rig, feet)
                    kk = smooth((u - 0.6) / 0.4)
                    for leg in ("thigh_l", "shin_l", "foot_l", "thigh_r", "shin_r", "foot_r"):
                        cur = p.get3(leg)[0]
                        fv = feet.get3(leg)[0]
                        p.get3(leg)[0] = [cur[i] * (1 - kk) + fv[i] * kk for i in range(3)]
            p.rot("arm_l", -70.0 * arc, 0.0, -30.0 * arc)
            p.rot("arm_r", -40.0 * arc, 0.0, 20.0 * arc)
            # The crown bounces up off his head as he lands, and drops back on.
            pop = bump(t, POP_LAND, POP_LAND + 0.12, POP_LAND + 0.18, POP_LAND + 0.38)
            if rig.bone("crown"):
                p.pos("crown", 0.0, 6.0 * pop, 0.0)
                p.rot("crown", 10.0 * pop, 30.0 * pop, -8.0 * pop)
            flourish = bump(t, 1.95, 2.15, 2.25, 2.5)
            if rig.has_staff:
                back = clamp01((t - POP_BURST) / 0.45)
                if back < 1.0:
                    M0 = staff_down(rig, p, (hx, hz))
                    # Straight up through the hole first (until its foot clears the ground), then to his hand.
                    M_up = M0.copy()
                    M_up[1, 3] += (rig.staff_above + 2.0 + float(rig.m["staff"].below) + 1.0) / 16.0
                    M1 = mats_of(rig, p)["staff"]
                    k = smooth(back)
                    M = blend_matrix(M0, M_up, clamp01(k / 0.5)) if k < 0.5 else blend_matrix(M_up, M1, (k - 0.5) / 0.5)
                    place(rig, p, "staff", M)
                else:
                    p.rot("staff", 0.0, 360.0 * smooth(clamp01((t - 1.9) / 0.45)), 0.0)
            p.rot("arm_r", -20.0 * flourish, 0.0, 10.0 * flourish)
            idle_pose(rig, 0.0, p, ramp(t, 2.0, 2.6))
            # His tail trails out of the hole behind him, then (out of it) lies on the ground again.
            sec.apply(p, t, wave=1.0, cape_mode="hang" if u > 0.5 else "body", cape_floor=0.5 if u > 0.5 else -99.0,
                      tail_floor=u > POP_TAIL_FLOOR, tail_gap=POP_TAIL_GAP,
                      tail_along=(DOWN_THE_HOLE, 1.0 - smooth(clamp01(u / POP_TAIL_FLOOR))))
        # The hole (slid back from in front of him to right under him) opens with the rumble and closes after he lands.
        p.pos("hole", hx, 0.0, hz + HOLE_FORWARD)
        hole_k = smooth(ramp(t, 0.0, POP_HOLE_RISE)) * (1.0 - ramp(t, POP_LAND + 0.1, POP_LAND + 0.6))
        slide(p, rig, "hole", hole_k)
        if hole_k > 0 and t < POP_BURST:
            p.pos("hole", 0.0, 0.4 * tremble * math.sin(t * 40.0), 0.0)
        return p

    a = record(rig, "popup", POPUP_T, "hold_on_last_frame", frame)
    a.meta.update(burst=POP_BURST, land=POP_LAND, peek=POP_PEEK, hole_x=hx, hole_z=hz, hole_forward=-hz)
    return a


# ---------------------------------------------------------------- rage


RAGE_T = 3.7
RAGE_EYES = 0.9
STEAM = (1.15, 1.55, 1.95, 2.35)
STOMPS = (0.55, 0.85, 1.1, 1.33, 1.53, 1.71)
SLAM = 2.85                     # the staff's foot smashes into the ground
SLAM_RAISE = (2.3, 2.6)         # he lifts the staff up beside him...
SMASH_FROM = 2.62               # ...and brings it down, hard
SLAM_LIFT = 15.0                # units he lifts it
SLAM_SPOT = (-1.0, -5.0)        # where its foot hits, from where it stands (x, z): a little in front of him
SLAM_HOLD = 0.2                 # seconds it stays down before he eases off
SLAM_LEAN = 14.0                # degrees he leans into the smash
FIST_UP = (-155.0, 0.0, 38.0)   # his free arm raised up and out to his left, clear of his head...
FIST_BEND = -55.0               # ...elbow bent, a fist at the top
FIST_CURL = 95.0                # degrees his fingers curl into a fist
PUMP_UP = 16.0                  # each pump thrusts the fist up this much more (and straightens the elbow)


def rage(rig):
    def frame(t, sec):
        p = Pose()
        clench = ramp(t, 0.0, 0.3)
        tremble = clench * (1.0 - ramp(t, 3.0, 3.4))
        screech = bump(t, 2.0, 2.15, 2.5, 2.7)
        settle = ramp(t, 3.0, 3.6)
        raise_ = smooth(ramp(t, *SLAM_RAISE))
        smash = clamp01((t - SMASH_FROM) / (SLAM - SMASH_FROM)) ** 2       # speeds up as it comes down
        down = smash * (1.0 - smooth(ramp(t, SLAM + SLAM_HOLD, SLAM + SLAM_HOLD + 0.5)))  # leaning into it
        hit = bump(t, SLAM, SLAM + 0.03, SLAM + 0.06, SLAM + 0.2)
        # Stomps: each foot in turn lifts high and slams down.
        offs = {"leg_l": [0.0, 0.0, 0.0], "leg_r": [0.0, 0.0, 0.0]}
        bounce = 0.0
        for i, ts in enumerate(STOMPS):
            leg = "leg_l" if i % 2 == 0 else "leg_r"
            up = bump(t, ts - 0.27, ts - 0.1, ts - 0.07, ts)
            offs[leg][1] += 10.0 * up
            offs[leg][2] += -2.5 * up
            bounce += bump(t, ts, ts + 0.03, ts + 0.04, ts + 0.12)
        p.pos("body", 0.4 * tremble * wobble(t, 1, 26), -1.6 * clench - 1.4 * bounce + 1.5 * screech - 2.0 * down, 0.0)
        p.rot("hips", 3.0 * clench + 6.0 * down, 0.0, 2.0 * tremble * wobble(t, 2, 20))
        p.rot("chest", 6.0 * clench - 22.0 * screech + SLAM_LEAN * down + 2.5 * tremble * wobble(t, 3, 24), 0.0,
              3.0 * tremble * wobble(t, 4, 22))
        head(p, rx=8.0 * clench - 34.0 * screech + 6.0 * hit + 3.0 * tremble * wobble(t, 5, 28),
             ry=4.0 * tremble * wobble(t, 6, 18), jaw=8.0 * clench + 40.0 * screech)
        plant_feet(rig, p, offs)
        # His free fist goes up out to his left and pumps in the air with every stomp, then shakes as he screeches.
        up_k = smooth(ramp(t, 0.12, 0.42)) * (1.0 - settle)
        pump = sum(bump(t, ts - 0.12, ts - 0.03, ts + 0.02, ts + 0.14) for ts in STOMPS)
        shake = screech * math.sin(t * 2.0 * math.pi * 9.0)
        set_limb_totals(rig, p, {"arm_l": (FIST_UP[0] - PUMP_UP * pump + 6.0 * shake, FIST_UP[1], FIST_UP[2]),
                                 "forearm_l": (FIST_BEND + 30.0 * pump + 8.0 * shake, 0.0, 0.0),
                                 "hand_l": (0.0, 0.0, 0.0)}, up_k)
        if rig.bone("fingers_l"):
            p.rot("fingers_l", FIST_CURL * smooth(ramp(t, 0.05, 0.3)) * (1.0 - settle))
        # Ears flatten, then flick up with each puff of steam.
        puff = sum(bump(t, s_, s_ + 0.04, s_ + 0.08, s_ + 0.25) for s_ in STEAM)
        ears(p, rx=24.0 * clench * (1.0 - screech) - 30.0 * puff, rz=-20.0 * clench + 14.0 * puff)
        # The staff: shaken with the stomps; lifted up beside him, then smashed down so its foot strikes the ground just
        # in front of him (the mod puts dust and green sparks there); held a moment with a little bounce, then eased off.
        if rig.has_staff:
            p.rot("arm_r", -25.0 * clench * (1.0 - raise_) + 6.0 * pump * (1.0 - raise_))
            k = raise_ * (1.0 - smooth(ramp(t, SLAM + SLAM_HOLD, SLAM + SLAM_HOLD + 0.5)))
            if k > 0.0:
                height = SLAM_LIFT * (1.0 - smash) + 1.2 * hit
                foot = rig.staff_foot_rest + np.array([SLAM_SPOT[0] * raise_, 0.0, SLAM_SPOT[1] * raise_])
                foot[1] = height * raise_
                arm = _copy(p)
                plant_staff(rig, arm, foot, R.normalize(rig.staff_up_rest + np.array([0.0, 0.0, -0.12])))
                _blend_bones(p, arm, ARM_R, k)
                p.get3("staff")[1] = list(arm.get3("staff")[1])
        idle_pose(rig, 0.0, p, settle)
        sec.apply(p, t, wave=1.0 + 2.0 * tremble, tail_wave=(14.0, 3.0, 0.9),
                  extra_tail=lambda i, tt: -30.0 * screech * (i + 1) / len(rig.tails))
        return p

    a = record(rig, "rage", RAGE_T, "hold_on_last_frame", frame)
    a.meta.update(eyes=RAGE_EYES, steam=list(STEAM), slam=SLAM, stomps=list(STOMPS), screech=2.0)
    # Where the staff's foot strikes the ground (blocks in front of him and to his right), for the mod's dust and sparks.
    if rig.has_staff:
        a.meta["slam_forward"] = -(rig.staff_foot_rest[2] + SLAM_SPOT[1]) / 16.0
        a.meta["slam_right"] = -(rig.staff_foot_rest[0] + SLAM_SPOT[0]) / 16.0
    return a


# ---------------------------------------------------------------- the silly death


DEATH_T = 3.6
FALL_FROM, FALL_TO = 1.05, 1.62
TONGUE_AT, X_AT = 1.85, 2.0


def death(rig):
    state = {}

    def frame(t, sec):
        p = Pose()
        clutch = ramp(t, 0.0, 0.25)
        stagger = bump(t, 0.05, 0.3, 0.6, 0.9)
        wob = bump(t, 0.5, 0.65, 0.95, 1.1)
        stiff = ramp(t, 0.9, 1.15)
        u = clamp01((t - FALL_FROM) / (FALL_TO - FALL_FROM))
        fall = u * u                                   # speeds up like a falling plank
        bounce = bump(t, FALL_TO, FALL_TO + 0.07, FALL_TO + 0.09, FALL_TO + 0.25)
        legs_up = ramp(t, 2.05, 2.45)
        twitch = bump(t, 3.0, 3.05, 3.1, 3.18)
        # Clutching his chest, staggering, wobbling...
        p.pos("body", 0.0, -1.0 * clutch * (1.0 - stiff), 3.0 * stagger)
        p.rot("chest", 8.0 * clutch * (1.0 - stiff), 0.0, 4.0 * wob * math.sin(t * 16.0))
        head(p, rx=-10.0 * stagger, ry=0.0, rz=12.0 * wob * math.sin(t * 13.0), jaw=14.0 * clutch)
        p.rot("arm_l", -40.0 * clutch * (1.0 - stiff), 30.0 * clutch * (1.0 - stiff), 0.0)
        p.rot("forearm_l", -50.0 * clutch * (1.0 - stiff))
        # ...then he goes stiff as a plank (rising onto straight legs) and topples straight back about his heels.
        # He bounces once when he hits the ground.
        straighten(rig, p, stiff)
        legs = {"thigh_l": (0.0, 0.0, 3.0), "thigh_r": (0.0, 0.0, -3.0), "shin_l": (0.0, 0.0, 0.0),
                "shin_r": (0.0, 0.0, 0.0), "foot_l": (0.0, 0.0, 0.0), "foot_r": (0.0, 0.0, 0.0)}
        if stiff < 1e-6:
            plant_feet(rig, p)
        else:
            planted = Pose()
            for k_, v in p.items():
                planted[k_] = [list(v[0]), list(v[1]), list(v[2])]
            plant_feet(rig, planted)
            for leg, tot in legs.items():
                R.set_total(p, rig.m, leg, tot)
                cur = p.get3(leg)[0]
                pv = planted.get3(leg)[0]
                p.get3(leg)[0] = [pv[i] * (1.0 - stiff) + cur[i] * stiff for i in range(3)]
            p.pos("body", 0.0, (rig.leg_reach - rig.b.hip_y) * stiff, 0.0)
            if u < 0.3:
                # Rising onto straight legs and starting to tip: his toes never dip into the floor on the way.
                lo = _lowest(rig.m, mats_of(rig, p))
                if lo < 0.05:
                    p.pos("body", 0.0, 0.05 - lo, 0.0)
        ang = -90.0 * fall + 7.0 * bounce
        R.rotate_body_about(p, (ang, 0.0, 0.0), (0.0, 0.0, float(rig.b.heel)))
        if u > 0.0:
            # Cartoon legs: stiff, sticking up off the ground once he's down; one foot twitches at the end.
            p.rot("thigh_l", -55.0 * legs_up, 0.0, 6.0 * legs_up)
            p.rot("thigh_r", -48.0 * legs_up, 0.0, -6.0 * legs_up)
            p.rot("foot_r", 30.0 * twitch)
            settle_on_ground(rig, p, k=smooth(clamp01((u - 0.5) * 2.0)))
        arms_out = ramp(t, 0.95, 1.3)
        set_limb_totals(rig, p, {"arm_l": (-30.0, 0.0, -55.0), "arm_r": (-30.0, 0.0, 55.0),
                                 "forearm_l": (-10.0, 0.0, 0.0), "forearm_r": (-10.0, 0.0, 0.0)}, arms_out)
        # Face: tongue lolls out of the side of his mouth, then the X's pop over his eyes.
        tongue = ramp(t, TONGUE_AT, TONGUE_AT + 0.15)
        head(p, jaw=10.0 * tongue)
        slide(p, rig, "tongue", tongue)
        p.rot("tongue", 25.0 * tongue, -35.0 * tongue, 0.0)
        xk = ramp(t, X_AT, X_AT + 0.06)
        pop = 1.0 + 0.45 * bump(t, X_AT + 0.04, X_AT + 0.09, X_AT + 0.11, X_AT + 0.22)
        xeyes(p, rig, xk, pop if xk > 0 else 1.0)
        ears(p, rx=10.0 * ramp(t, FALL_TO, FALL_TO + 0.3), rz=-30.0 * ramp(t, FALL_TO, FALL_TO + 0.3))
        # The staff: he lets go as he tips, it falls and clatters down beside him.
        if rig.has_staff:
            drop = clamp01((t - 1.15) / 0.5)
            if t >= 1.15:
                mats = mats_of(rig, p)
                M_rest_now = mats["staff"]
                if state.get("staff_start") is None:
                    state["staff_start"] = M_rest_now
                M0 = state["staff_start"]
                M1 = staff_on_ground(rig, (-16.0, 6.0), (-0.25, 1.0))
                k = drop * drop
                bb = bump(t, 1.65, 1.7, 1.72, 1.85)
                M = blend_matrix(M0, M1, k)
                M[1, 3] += bb * 2.5 / 16.0
                place(rig, p, "staff", M)
        # The crown flies off as he lands and drops onto his belly.
        if rig.bone("crown"):
            cf = clamp01((t - FALL_TO) / 0.5)
            if t >= FALL_TO:
                mats = mats_of(rig, p)
                if state.get("crown_start") is None:
                    state["crown_start"] = mats["crown"]
                belly = rig.m.point(mats, "belly", (0.0, rig.m["belly"].pivot[1] + 5.0, rig.m["belly"].pivot[2] - rig.b.belly_size[2] / 2.0 - 0.5))
                M1 = frame_matrix(rig, "crown", belly + np.array([0.0, 0.3, 0.0]), R.normalize(np.array([0.25, 1.0, 0.1])),
                                  np.array([0.0, 0.0, -1.0]))
                M = blend_matrix(state["crown_start"], M1, smooth(cf))
                M[1, 3] += 10.0 / 16.0 * 4.0 * cf * (1.0 - cf)
                place(rig, p, "crown", M)
        mode = "hang" if u <= 0.0 else "body"
        sec.apply(p, t, wave=1.0 - stiff * 0.8, cape_mode=mode, cape_floor=0.5 if u <= 0 else 0.3,
                  extra_tail=lambda i, tt: 30.0 * bump(tt, 3.2, 3.25, 3.3, 3.4) * (i + 1) / len(rig.tails))
        return p

    a = record(rig, "death", DEATH_T, "hold_on_last_frame", frame)
    # The staff lands 0.5 s after he lets go (1.15 s); the crown lands on his belly 0.5 s after he hits the ground.
    a.meta.update(fall=FALL_TO, tongue=TONGUE_AT, xeyes=X_AT, clatter=1.65, crown=FALL_TO + 0.5)
    return a


# ---------------------------------------------------------------- the fight: run, sweep, cast


def staff_hold(rig, pose, grip, direction, slide=0.0, key="staff"):
    """
    IK the staff arm so his fist sits at `grip` (Bedrock model space) and the shaft points along `direction` (towards
    the orb). slide: how far the staff has been slid up through his fist (units; slide_staff(rig, pose, -slide) must
    already be in the pose), so the foot is that much nearer the fist. Returns the miss in units.
    """
    if not rig.has_staff:
        return 0.0
    u = R.normalize(direction)
    below = float(rig.m["staff"].below)
    foot = np.asarray(grip, dtype=float) - u * (below - slide)
    return rig.solve_limb(pose, key, foot, None, down=tuple(-u))


ORB_FALLBACK = 30.0             # units above the grip taken as the staff's head if it has no orb


def orb_point(m, mats):
    """The middle of the orb on his staff (Bedrock model space, his front -z: use pose_matrices without a yaw)."""
    for c in m["staff"].cubes:
        if c.tag == "orb":
            return m.point(mats, "staff", c.center())
    s = m["staff"]
    return m.point(mats, "staff", (s.pivot[0], s.pivot[1] + ORB_FALLBACK, s.pivot[2]))


def blend_arm(p, other, k, bones=ARM_R):
    """Blends these bones' rotations AND the staff's slide (its position) from `other` into p by k."""
    _blend_bones(p, other, bones, k)
    if "staff" in other:
        cur = p.get3("staff")[1]
        ov = other.get3("staff")[1]
        p.get3("staff")[1] = [cur[i] * (1.0 - k) + ov[i] * k for i in range(3)]


def direction_at(yaw_deg, pitch_deg):
    """A Bedrock model-space direction: yaw 0 = straight ahead (-z), +90 = his left (+x); pitch > 0 points up."""
    y, pt = math.radians(yaw_deg), math.radians(pitch_deg)
    return np.array([math.sin(y) * math.cos(pt), math.sin(pt), -math.cos(y) * math.cos(pt)])


# The run (loop): a fast, low scurry, the staff carried in his fist, slanted forward with the orb leading, his cape
# streaming out behind and his tail out straight. In place: the mod plays it faster or slower to match how fast he
# really moves (RatKingDesigns runBlocks per runTicks).
RUN_T = 0.4                     # seconds per stride cycle (two steps)
RUN_STRIDE = 42.0               # units he travels per cycle: 42 / 16 / 0.4 s = 6.6 blocks a second as made
RUN_STANCE = 0.36               # share of the cycle each foot is on the ground (both are off it in between)
RUN_LIFT = 7.0                  # how high a foot lifts as it swings through (units)
RUN_KICK = 5.0                  # how much higher the heel kicks up behind, early in the swing (units)
RUN_CROUCH = 6.0                # how much lower than standing he runs (units)
RUN_BOB = 1.4                   # up and down with each step: lowest mid-stance, highest in the air (units)
RUN_LEAN = 26.0                 # how much further forward he leans (degrees, spread over his spine)
RUN_FEET_IN = 1.2               # feet this much nearer his middle than when he stands (units)
RUN_TIPTOE = 26.0               # degrees up on his toes on the ground (more while a foot swings)
RUN_TOE_CLEAR = 0.9             # units his toe tips stay above the floor (they touch, never sink)
RUN_ARM = 34.0                  # how far the free arm pumps forward and back (degrees)
RUN_STAFF_GRIP = (-12.5, 20.5, -12.5)   # where his fist carries the staff (units, his rest grip is (-12, 25, -15))
RUN_STAFF_DIR = (-18.0, 30.0)   # which way it points (yaw, pitch degrees): forward, a little out, orb up
RUN_STAFF_SWING = 2.5           # units his staff fist swings forward and back with each stride
RUN_STEP = 0.025                # seconds between recorded frames (the cycle is short)
CAPE_STREAM_DROOP = (0.25, 0.45, 0.6)   # how far each cape panel droops below level, streaming back (y per z)
CAPE_STREAM_RIPPLE = 0.22       # how far the ripples lift and drop a panel
CAPE_STREAM_FREQ = 2.0 / RUN_T  # ripples a second (two per stride cycle)
CAPE_STREAM_PHASE = 1.3         # radians the ripple lags from one panel to the next


def run(rig):
    T = RUN_T
    L = RUN_STRIDE * RUN_STANCE          # how far a planted foot slides back under him (units)
    mid = RUN_STANCE / 2.0               # the left foot's mid-stance (the right's is half a cycle later)

    def foot(ph):
        """(dz, dy, tilt) of one foot at this point of its own cycle (0 = it lands in front)."""
        ph %= 1.0
        if ph < RUN_STANCE:
            u = ph / RUN_STANCE
            return -L / 2.0 + L * u, 0.0, RUN_TIPTOE + 10.0 * u      # rolls onto its toes as it pushes off
        u = (ph - RUN_STANCE) / (1.0 - RUN_STANCE)
        e = 0.5 - 0.5 * math.cos(math.pi * u ** 1.25)               # stays back a moment (the kick), then reaches
        dz = L / 2.0 - L * e
        dy = RUN_LIFT * math.sin(math.pi * u) ** 0.8 + RUN_KICK * math.sin(math.pi * min(1.0, u * 1.6)) * (1.0 - u)
        tilt = RUN_TIPTOE + 10.0 + 22.0 * math.sin(math.pi * u) - 12.0 * smooth((u - 0.7) / 0.3)
        return dz, dy, tilt

    def frame(t, sec):
        ph = t / T
        w = 2 * math.pi * ph
        p = Pose()
        bob = math.cos(2 * (w - 2 * math.pi * mid))      # 1 mid-stance (low), -1 in the air
        p.pos("body", 0.35 * math.sin(w), -RUN_CROUCH - RUN_BOB * (0.5 + 0.5 * bob), 0.0)
        # Leaning into it; the hips turn with the legs and the shoulders against them.
        p.rot("hips", RUN_LEAN * 0.45 + 2.0 * bob, 7.0 * math.cos(w), -1.5 * math.sin(w))
        p.rot("belly", RUN_LEAN * 0.3, -3.0 * math.cos(w), 0.0)
        p.rot("chest", RUN_LEAN * 0.25 - 1.5 * bob, -6.0 * math.cos(w), 2.5 * math.sin(w))
        # His head stays level, looking where he's going; ears pressed back, a pant.
        head(p, rx=-RUN_LEAN * 0.9 + 1.5 * bob, ry=3.0 * math.cos(w), rz=-1.5 * math.sin(w),
             jaw=6.0 + 3.0 * math.sin(2 * w))
        p.rot("neck", -2.0, 0.0, 0.0)
        ears(p, rx=38.0 + 6.0 * math.sin(2 * w + 0.8), rz=-14.0)
        offs, down = {}, {}
        for name, off, sx in (("leg_l", 0.0, -1.0), ("leg_r", 0.5, 1.0)):
            dz, dy, tilt = foot(ph - off)
            tl = math.radians(tilt)
            down[name] = np.array([0.0, -math.cos(tl), math.sin(tl)])
            toe = rig.b.foot_len / 2.0 + 1.5
            offs[name] = (sx * RUN_FEET_IN, dy + toe * math.sin(tl) + RUN_TOE_CLEAR, dz)
        plant_feet_clear(rig, p, offs, down)
        # The free arm pumps against the left leg, elbow bent.
        p.rot("arm_l", RUN_ARM * math.cos(w) - 8.0, 0.0, -4.0)
        p.rot("forearm_l", -18.0 - 14.0 * (0.5 - 0.5 * math.cos(w)), 0.0, 0.0)
        p.rot("hand_l", 10.0 * math.cos(w + 0.6))
        if rig.bone("fingers_l"):
            p.rot("fingers_l", 40.0)
        # The staff carried forward in his fist, swinging a little with his stride.
        if rig.has_staff:
            grip = np.array(RUN_STAFF_GRIP) + np.array([0.0, -0.8 * bob, -RUN_STAFF_SWING * math.cos(w)])
            yaw, pitch = RUN_STAFF_DIR
            staff_hold(rig, p, grip, direction_at(yaw + 3.0 * math.cos(w), pitch + 4.0 * math.cos(w)))
        sec.apply(p, t, wave=1.0, cape_mode="stream", lag=0.3, cape_floor=1.0, tail_lag=0.3,
                  tail_wave=(12.0, 1.0 / T, 0.9), tail_along=(np.array([0.0, 0.12, 1.0]), 0.8))
        return p

    a = record(rig, "run", T, True, frame, warm=2, step=RUN_STEP)
    a.meta["stride_blocks"] = RUN_STRIDE / 16.0
    return a


# The sweep (once): his close-range blow. He winds the staff back over his right side, holding it near its foot like a
# club, then sweeps it round in a flat arc in front of him at chest height and follows through to his left; then he
# eases back to his idle stance. The hit is the moment the staff passes his front.
SWEEP_T = 0.9                   # seconds, wind-up to recovery
SWEEP_KEYS = (                  # (seconds, the staff's (yaw, pitch), his fist's yaw round SWEEP_CENTRE); yaw 0 is
    (0.0, None, None),          # straight ahead, + his left; pitch + up. 0 s: his idle hold
    (0.28, (-130.0, 18.0), -78.0),   # wound back to his right, orb raised a little
    (0.33, (-127.0, 14.0), -76.0),   # (a held beat at the top)
    (0.53, (72.0, -4.0), 44.0),      # swept round through his front, level, to his left
    (0.6, (90.0, -8.0), 52.0),       # the follow-through
    (0.8, (-40.0, 72.0), -44.0),     # brought back round and up to his side (his idle hold is about (-47, 80), -44)
)
SWEEP_FIST_LEAD = 0.03          # seconds his fist leads the staff's head through the swing (it whips through)
SWEEP_RECOVER = (0.66, 0.9)     # easing back into his idle hold
SWEEP_SLIDE = 21.0              # units he slides his fist down the shaft (holding it by its foot, like a club)
SWEEP_GRIP_RADIUS = 15.0        # units his fist sweeps round from...
SWEEP_CENTRE = (-2.0, 33.0, -4.0)   # ...this spot (a little to his right, at chest height)
SWEEP_TWIST = 30.0              # degrees his body turns into the wind-up (then the other way through the swing)


def _sweep_key(t, which):
    """One of the sweep's tracks at time t (which: 1 the staff's (yaw, pitch), 2 the fist's yaw), eased."""
    keys = SWEEP_KEYS
    t = max(t, keys[1][0])
    for k0, k1 in zip(keys[1:-1], keys[2:]):
        if t <= k1[0]:
            u = (t - k0[0]) / (k1[0] - k0[0])
            a0, a1 = np.atleast_1d(np.array(k0[which], dtype=float)), np.atleast_1d(np.array(k1[which], dtype=float))
            if k1 is keys[3]:
                u = smooth(u) ** 1.35               # the swing: slow out of the wind-up, fastest through his front
            else:
                u = smooth(u)
            return a0 + (a1 - a0) * u
    return np.atleast_1d(np.array(keys[-1][which], dtype=float))


def _sweep_dir(t):
    """(staff yaw, pitch), the fist's yaw and how far into the move he is (0 idle .. 1 fully in it)."""
    k_in = smooth(clamp01(t / SWEEP_KEYS[1][0]))
    k_out = 1.0 - smooth(clamp01((t - SWEEP_RECOVER[0]) / (SWEEP_RECOVER[1] - SWEEP_RECOVER[0])))
    staff = _sweep_key(t, 1)
    fist = float(_sweep_key(t + SWEEP_FIST_LEAD, 2)[0])
    return (float(staff[0]), float(staff[1])), fist, k_in * k_out


def sweep_hit():
    """When the staff passes his front (yaw 0), from SWEEP_KEYS."""
    ts = np.arange(0.0, SWEEP_T, 0.001)
    ys = [_sweep_dir(float(t))[0][0] for t in ts]
    for a, b_, t in zip(ys[:-1], ys[1:], ts[1:]):
        if a < 0.0 <= b_:
            return float(t)
    return 0.45


def sweep(rig):
    hit_t = sweep_hit()
    swing = (SWEEP_KEYS[2][0], SWEEP_KEYS[3][0])
    wind = SWEEP_KEYS[1][1][0]
    span = SWEEP_KEYS[3][1][0] - wind

    def frame(t, sec):
        p = Pose()
        (yaw, pitch), fist_yaw, k = _sweep_dir(t)
        # The body turns into the wind-up, then whips round the other way through the swing.
        turn = SWEEP_TWIST * (1.0 - 2.0 * smooth(clamp01((yaw - wind) / span)))
        lunge = bump(t, swing[0], hit_t, hit_t + 0.08, SWEEP_RECOVER[1])
        p.rot("hips", 4.0 * lunge * k, 0.3 * turn * k, 0.0)
        p.rot("belly", 3.0 * lunge * k, 0.3 * turn * k, 0.0)
        p.rot("chest", 6.0 * lunge * k, 0.4 * turn * k, 0.0)
        p.pos("body", 0.0, -1.5 * k - 1.0 * lunge * k, -1.5 * lunge * k)
        head(p, rx=-6.0 * k, ry=-0.9 * turn * k, jaw=4.0 * k + 22.0 * bump(t, swing[0], hit_t, hit_t + 0.05, 0.75))
        ears(p, rx=26.0 * k, rz=-10.0 * k)
        plant_feet_clear(rig, p, {"leg_l": (0.0, 0.0, -1.5 * k), "leg_r": (0.0, 0.0, 1.5 * k)})
        # The free arm swings out the other way for balance.
        p.rot("arm_l", -20.0 * k * lunge + 10.0 * k, 0.0, -35.0 * k * (0.4 + 0.6 * lunge))
        if rig.bone("fingers_l"):
            p.rot("fingers_l", 50.0 * k)
        if rig.has_staff and k > 0.0:
            arm = _copy(p)
            slide_staff(rig, arm, -SWEEP_SLIDE * k)
            g = np.array(SWEEP_CENTRE) + direction_at(fist_yaw, 0.0) * SWEEP_GRIP_RADIUS
            staff_hold(rig, arm, g, direction_at(yaw, pitch), slide=SWEEP_SLIDE * k)
            blend_arm(p, arm, k)
        idle_pose(rig, 0.0, p, 1.0 - k)
        sec.apply(p, t, wave=1.0)
        return p

    a = record(rig, "sweep", SWEEP_T, "hold_on_last_frame", frame)
    a.meta["hit"] = hit_t
    # How far the orb reaches from his middle as it passes his front (blocks): the blow's reach.
    o = orb_point(rig.m, rig.m.pose_matrices(a.sample(hit_t)))
    a.meta["reach"] = float(math.hypot(o[0], o[2])) / 16.0
    a.meta["hit_height"] = o[1] / 16.0
    return a


# The cast (once): he holds the staff out in front of him, aims it, draws it back a touch and flicks it forward: the
# poison ball leaves the orb at the release (the mod spawns it there, from castForward/castUp/castRight). Then he
# eases back to his idle stance.
CAST_T = 1.1                    # seconds, raising the staff to recovery
CAST_KEYS = (                   # (seconds, his fist (units), the staff's (yaw, pitch))
    (0.0, None, None),
    (0.3, (-9.0, 31.0, -22.0), (-4.0, 34.0)),    # held out in front, the orb up at his target
    (0.55, (-9.0, 31.5, -23.0), (-2.0, 36.0)),   # aiming (a slow tremble)
    (0.68, (-10.0, 34.0, -17.0), (-6.0, 62.0)),  # drawn back
    (0.76, (-9.0, 30.0, -25.0), (0.0, 12.0)),    # the flick
    (0.84, (-9.0, 28.5, -24.0), (2.0, 4.0)),     # the follow-through
)
CAST_RELEASE = 0.74             # seconds: the ball leaves the orb
CAST_RECOVER = (0.84, 1.1)      # back to his idle hold


def _cast_at(t):
    keys = CAST_KEYS
    k_in = smooth(clamp01(t / keys[1][0]))
    k_out = 1.0 - smooth(clamp01((t - CAST_RECOVER[0]) / (CAST_RECOVER[1] - CAST_RECOVER[0])))
    if t <= keys[1][0]:
        return np.array(keys[1][1]), keys[1][2], k_in * k_out
    for (t0, g0, a0), (t1, g1, a1) in zip(keys[1:-1], keys[2:]):
        if t <= t1:
            u = smooth((t - t0) / (t1 - t0))
            g = np.array(g0) + (np.array(g1) - np.array(g0)) * u
            return g, (a0[0] + (a1[0] - a0[0]) * u, a0[1] + (a1[1] - a0[1]) * u), k_in * k_out
    return np.array(keys[-1][1]), keys[-1][2], k_in * k_out


def cast(rig):
    state = {}

    def frame(t, sec):
        p = Pose()
        g, (yaw, pitch), k = _cast_at(t)
        aim = bump(t, 0.25, 0.4, 0.62, 0.7)
        draw = bump(t, 0.58, 0.68, 0.7, 0.76)
        flick = bump(t, 0.7, CAST_RELEASE, 0.8, 1.0)
        # Leans in to aim, rocks back as he draws, snaps forward with the flick.
        p.rot("hips", (3.0 * aim - 4.0 * draw + 8.0 * flick) * k)
        p.rot("chest", (5.0 * aim - 8.0 * draw + 14.0 * flick) * k, -6.0 * k, 0.0)
        p.pos("body", 0.0, -1.5 * k, (-1.0 * aim + 1.5 * draw - 3.0 * flick) * k)
        tremble = aim * 0.6 * math.sin(t * 2 * math.pi * 11.0)
        head(p, rx=(-8.0 * aim + 6.0 * draw - 4.0 * flick) * k, ry=4.0 * k,
             jaw=(3.0 * aim + 24.0 * flick) * k)
        # Ears prick forward as he aims, flatten with the flick.
        ears(p, rx=(-14.0 * aim + 22.0 * flick) * k, rz=(6.0 * aim - 10.0 * flick) * k)
        lids(p, rig, 0.35 * aim * k)                                # a squint along the staff
        plant_feet_clear(rig, p)
        # The free hand: claws spread beside the staff as he aims, then flung back with the flick.
        p.rot("arm_l", (-35.0 * aim + 20.0 * flick) * k, 0.0, (-10.0 * aim - 25.0 * flick) * k)
        p.rot("forearm_l", (-20.0 * aim) * k)
        if rig.has_staff and k > 0.0:
            arm = _copy(p)
            staff_hold(rig, arm, g + np.array([0.0, tremble, 0.0]), direction_at(yaw, pitch))
            blend_arm(p, arm, k)
        idle_pose(rig, 0.0, p, 1.0 - k)
        sec.apply(p, t, wave=1.0)
        return p

    a = record(rig, "cast", CAST_T, "hold_on_last_frame", frame)
    a.meta["release"] = CAST_RELEASE
    # Where the ball leaves from: the orb at the release (blocks in front of him, above his feet, to his right).
    o = orb_point(rig.m, rig.m.pose_matrices(a.sample(CAST_RELEASE)))
    a.meta["cast_forward"] = -o[2] / 16.0
    a.meta["cast_up"] = o[1] / 16.0
    a.meta["cast_right"] = -o[0] / 16.0
    return a


# ---------------------------------------------------------------- everything


def build_all(rig):
    out = {}
    out["idle"] = idle(rig)
    out["walk"] = walk(rig)
    out["sleep"] = sleep(rig)
    out["wake"] = wake(rig)
    out["burrow"] = burrow(rig)
    out["burrowed"] = hold(out["burrow"], "burrowed")
    out["popup"] = popup(rig)
    out["rage"] = rage(rig)
    out["death"] = death(rig)
    out["dead"] = hold(out["death"], "dead")
    out["run"] = run(rig)
    out["sweep"] = sweep(rig)
    out["cast"] = cast(rig)
    return out
