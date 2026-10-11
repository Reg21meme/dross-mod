"""
The golems' animations, generated from the shared skeleton:

  idle         - breathing, looking around, shifting a fist; knuckles and feet stay planted (loops)
  walk         - knuckle-walking in place: a diagonal gait, fists and feet planted while they carry weight,
                 the body rolling and bobbing over them (loops)
  break_shell  - it tenses, rears up and bursts out of its shell: every plate flies off on a real
                 arc, tumbles, lands and crumbles away; then it slams its fists down (plays once, holds)
  death        - it rears back, buckles and sinks onto its knuckles with its head bowed, and stays like that
                 (plays once, holds; the mod's renderer cools it into obsidian over the last part)
  statue       - the death's last pose, held (a golem that died earlier, loaded again)

Every animation is a list of frames (poses) sampled at a fixed step. Planted limbs are solved with the IK
in ik.py on every frame, so knuckles and feet don't slide or sink. The frames are then turned into keyframe
tracks per bone and thinned out (a keyframe is dropped when straight-line interpolation between its
neighbours, which is what GeckoLib does, reproduces it closely enough).

Animation offsets are Bedrock values on top of each bone's rest rotation: rotation in degrees, position in
units, scale as a factor. Names: animation.cinder_colossus.<idle|walk|break_shell|death|statue>.
"""
import math

import numpy as np

from . import geo, ik

PREFIX = "animation.cinder_colossus."
ZERO = (0.0, 0.0, 0.0)
ONE = (1.0, 1.0, 1.0)
GRAVITY = 260.0  # units / s^2 for flying shell plates (a little under item gravity)
RETRY_MISS = 1.0  # units: a planted limb that misses its spot by more than this is solved again more freely


# ---------------------------------------------------------------- easing helpers


def clamp01(x):
    return max(0.0, min(1.0, x))


def smooth(x):
    x = clamp01(x)
    return x * x * (3.0 - 2.0 * x)


def ramp(t, t0, t1):
    """0 before t0, 1 after t1, smooth in between."""
    if t1 <= t0:
        return 1.0 if t >= t1 else 0.0
    return smooth((t - t0) / (t1 - t0))


def bump(t, t0, t1, t2, t3):
    """Rises from t0 to t1, holds, falls from t2 to t3."""
    return ramp(t, t0, t1) * (1.0 - ramp(t, t2, t3))


def ease_out(x):
    x = clamp01(x)
    return 1.0 - (1.0 - x) ** 3


def wobble(t, seed, freq=9.0):
    """Deterministic jitter in [-1, 1] (a few mixed sines)."""
    return (math.sin(t * freq * 2.1 + seed * 1.7) * 0.5 + math.sin(t * freq * 3.3 + seed * 4.1) * 0.3
            + math.sin(t * freq * 5.7 + seed * 2.9) * 0.2)


# ---------------------------------------------------------------- frames and tracks


class Pose(dict):
    """bone name -> [rot(3), pos(3), scale(3)] offsets (lists, so parts can be added up)."""

    def get3(self, bone):
        if bone not in self:
            self[bone] = [list(ZERO), list(ZERO), list(ONE)]
        return self[bone]

    def rot(self, bone, x=0.0, y=0.0, z=0.0):
        r = self.get3(bone)[0]
        r[0] += x
        r[1] += y
        r[2] += z

    def pos(self, bone, x=0.0, y=0.0, z=0.0):
        p = self.get3(bone)[1]
        p[0] += x
        p[1] += y
        p[2] += z

    def scale(self, bone, s):
        sc = self.get3(bone)[2]
        sc[0] *= s
        sc[1] *= s
        sc[2] *= s

    def as_geo(self):
        return {k: (tuple(v[0]), tuple(v[1]), tuple(v[2])) for k, v in self.items()}


class Animation:
    def __init__(self, name, length, loop):
        self.name = name
        self.length = float(length)
        self.loop = loop          # True (loop) or "hold_on_last_frame"
        self.frames = []          # (t, Pose)
        self.tracks = {}          # bone -> channel -> [(t, (x, y, z))]
        self.meta = {}

    def add(self, t, pose):
        self.frames.append((round(t, 4), pose))

    def bake(self, tol_rot=0.2, tol_pos=0.04, tol_scale=0.008):
        bones = set()
        for _t, p in self.frames:
            bones.update(p.keys())
        times = np.array([t for t, _ in self.frames])
        for bone in sorted(bones):
            chans = {}
            for ci, (cname, default, tol) in enumerate((("rotation", ZERO, tol_rot), ("position", ZERO, tol_pos),
                                                         ("scale", ONE, tol_scale))):
                vals = np.array([p.get(bone, [list(ZERO), list(ZERO), list(ONE)])[ci] for _t, p in self.frames],
                                dtype=float)
                if np.all(np.abs(vals - np.array(default)) < tol * 0.5):
                    continue
                keep = _simplify(times, vals, tol)
                chans[cname] = [(float(times[i]), tuple(float(v) for v in vals[i])) for i in keep]
            if chans:
                self.tracks[bone] = chans

    def sample(self, t):
        """The pose at time t, interpolated from the baked keyframes the way GeckoLib does (linear)."""
        if self.loop is True and self.length > 0:
            t = t % self.length
        else:
            t = min(t, self.length)
        out = {}
        for bone, chans in self.tracks.items():
            vals = []
            for cname, default in (("rotation", ZERO), ("position", ZERO), ("scale", ONE)):
                kf = chans.get(cname)
                if not kf:
                    vals.append(default)
                    continue
                vals.append(_interp(kf, t))
            out[bone] = tuple(vals)
        return out


def _interp(kf, t):
    if t <= kf[0][0]:
        return kf[0][1]
    for (t0, v0), (t1, v1) in zip(kf[:-1], kf[1:]):
        if t <= t1:
            a = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            return tuple(v0[i] + (v1[i] - v0[i]) * a for i in range(3))
    return kf[-1][1]


def _simplify(times, vals, tol):
    """Indices of keyframes to keep so linear interpolation stays within tol of every frame (RDP)."""
    n = len(times)
    if n <= 2:
        return list(range(n))
    keep = {0, n - 1}
    stack = [(0, n - 1)]
    while stack:
        a, b = stack.pop()
        if b - a < 2:
            continue
        ts = times[a + 1:b]
        f = ((ts - times[a]) / (times[b] - times[a]))[:, None]
        line = vals[a] + (vals[b] - vals[a]) * f
        err = np.max(np.abs(vals[a + 1:b] - line), axis=1)
        i = int(np.argmax(err))
        if err[i] > tol:
            k = a + 1 + i
            keep.add(k)
            stack.append((a, k))
            stack.append((k, b))
    return sorted(keep)


def to_json(animations):
    out = {"format_version": "1.8.0", "animations": {}, "geckolib_format_version": 2}
    for a in animations.values():
        bones = {}
        for bone, chans in a.tracks.items():
            entry = {}
            for cname, kf in chans.items():
                entry[cname] = {_ts(t): [_r(v) for v in vals] for t, vals in kf}
            bones[bone] = entry
        out["animations"][PREFIX + a.name] = {
            "loop": a.loop,
            "animation_length": round(a.length, 4),
            "bones": bones,
        }
    return out


def _ts(t):
    return f"{t:.4f}".rstrip("0").rstrip(".") if t else "0.0"


def _r(v):
    r = round(float(v), 3)
    return int(r) if r == int(r) else r


# ---------------------------------------------------------------- rig: the golem's limbs and contacts


class Rig:
    """What the animation code needs to know about one golem: its model, limbs, rest contacts and style."""

    def __init__(self, m, limbs, rest_targets, style):
        self.m = m
        self.limbs = limbs                  # name -> ik.Limb (arm_l, arm_r, leg_l, leg_r)
        self.rest = rest_targets            # name -> (target point, heading)
        self.style = dict(DEFAULT_STYLE)
        self.style.update(style or {})
        self.shell = [b for b in m.bones if b.kind == "shell"]
        self.molten = [b for b in m.bones if b.kind == "molten"]
        self.vents = [b for b in m.bones if b.kind == "vent"]
        self.bursts = [b for b in m.bones if b.kind == "burst"]
        self.volcano_holders = [b for b in m.bones if getattr(b, "is_volcano", False)]
        # Bones that follow only part of another bone's turn (shoulder pads on their mounts): (bone, leader, share).
        self.followers = [(b.name, b.follow[0], b.follow[1]) for b in m.bones if getattr(b, "follow", None)]
        self._warm = {}

    def solve_limb(self, pose, name, target, heading=None, down=(0.0, -1.0, 0.0)):
        """Plants a limb: IK on top of the body motion already in `pose`. Warm-starts from the last frame."""
        limb = self.limbs[name]
        geo_pose = pose.as_geo()
        x0 = self._warm.get(name)
        if heading is None:
            heading = self.rest[name][1]
        solved, err, x = ik.solve(self.m, geo_pose, limb, target, heading, down=down, x0=x0,
                                  w_smooth=self.style["ik_smooth"])
        if err > RETRY_MISS:
            # Missing the spot: the pull towards the last frame may be holding the limb in a bad bend. Try again
            # without it, and from the limb's rest bend, and keep whichever reaches clearly better.
            for start in (x0, None):
                s2, e2, x2 = ik.solve(self.m, geo_pose, limb, target, heading, down=down, x0=start)
                if e2 < err - 0.1:
                    solved, err, x = s2, e2, x2
        self._warm[name] = x
        for j in limb.joints:
            rot = solved[j.bone][0]
            p = pose.get3(j.bone)
            p[0] = list(rot)
        return err

    def reset_warm(self):
        self._warm = {}

    def follow(self, pose):
        """Turns each follower back against its leader's animated turn, so it keeps only its share of it."""
        for name, leader, share in self.followers:
            off = pose.get3(leader)[0]
            pose.rot(name, *[-(1.0 - share) * v for v in off])
        return pose


DEFAULT_STYLE = dict(
    weight=1.0,          # heavier: slower and deeper motions
    walk_cycle=1.8,      # seconds per gait cycle
    stride=30.0,         # units the body travels per cycle
    arm_lift=9.0,        # how high a fist swings
    arm_bow=6.0,         # how far out a fist swings as it passes the body (units), so it clears the body and leg
    arm_reach=3.0,       # how far forward of its rest spot a fist's steps are centred (units)
    walk_crouch=1.0,     # how much lower the body goes while walking (units), so the arms reach forward and back
                         # without locking straight
    ik_smooth=0.05,      # how strongly each frame's limb angles stay near the last frame's (per degree): no flips
    leg_lift=6.0,
    bob=1.4,             # body bob while walking (units)
    sway=1.6,            # side-to-side sway while walking (units)
    roll=3.5,            # torso roll while walking (degrees)
    lopsided=0.0,        # 0 = even gait; >0 = the right arm is weaker (crude design)
    idle_period=4.0,
    roar=1.0,            # how far it rears up in the break animation
    burst=1.3,           # seconds into break_shell when the shell bursts off
    plate_speed=1.0,     # how hard the plates fly
    erupt_blast=1.3,     # seconds into erupt when the volcano blows (it leans in over its fists before that)
    erupt_length=3.6,    # length of the erupt animation
    erupt_lean=1.0,      # how far it leans forward before the blast
    fountain_period=1.6, # the live eruption's loop
)


# ---------------------------------------------------------------- shared body motion


def head_track(p, rx=0.0, ry=0.0, rz=0.0, jaw=0.0, inner=0.0, inner_push=0.0):
    p.rot("head", rx, ry, rz)
    if jaw:
        p.rot("jaw", jaw)
    if inner or inner_push:
        p.rot("jaw_inner", inner)
        p.pos("jaw_inner", 0.0, 0.0, -inner_push)


# ---------------------------------------------------------------- idle


def idle(rig):
    s = rig.style
    T = s["idle_period"]
    a = Animation("idle", T, True)
    step = 0.1
    rig.reset_warm()
    for i in range(int(round(T / step)) + 1):
        t = i * step
        w = 2.0 * math.pi * t / T
        p = Pose()
        breath = math.sin(2 * w)                          # two breaths per loop
        p.pos("root", 0.0, -0.35 * (1 - math.cos(2 * w)) * s["weight"], 0.0)
        p.rot("pelvis", 0.6 * breath)
        p.rot("torso", -1.4 * breath, 1.2 * math.sin(w), 0.8 * math.sin(w + 1.0))
        look = math.sin(w)                                # one slow look left and right
        head_track(p, rx=1.5 * math.sin(2 * w + 0.6), ry=11.0 * look, rz=-2.0 * look,
                   jaw=2.0 + 2.0 * math.sin(2 * w - 0.5), inner=1.5 * math.sin(4 * w), inner_push=0.0)
        for name in ("leg_l", "leg_r"):
            rig.solve_limb(p, name, rig.rest[name][0])
        # The right fist shifts: lifts a little, sets down a hand-width out, and comes back.
        for name in ("arm_l", "arm_r"):
            tgt = np.array(rig.rest[name][0], dtype=float)
            if name == "arm_r":
                lift = bump(t, 1.6, 1.9, 1.9, 2.2) * 3.0
                out = ramp(t, 1.6, 2.2) * (1.0 - ramp(t, 3.3, 3.8))
                tgt = tgt + np.array([-1.5 * out, lift, -1.0 * out])
                lift2 = bump(t, 3.3, 3.55, 3.55, 3.8) * 3.0
                tgt[1] += lift2
            rig.solve_limb(p, name, tgt)
        a.add(t, rig.follow(p))
    a.bake()
    return a


# ---------------------------------------------------------------- walk


def gait_offset(phase, stance, length, lift):
    """
    Contact offset for one limb at a point of the gait cycle (0..1): while planted it slides from the front
    (-length/2 in z) to the back (+length/2); while swinging it lifts and moves forward again.
    Returns (dz, dy, swing) where swing runs 0..1 through the swing (0 while planted).
    """
    ph = phase % 1.0
    if ph < stance:
        u = ph / stance
        return -length / 2.0 + length * u, 0.0, 0.0
    u = (ph - stance) / (1.0 - stance)
    e = 0.5 - 0.5 * math.cos(math.pi * u)
    return length / 2.0 - length * e, lift * math.sin(math.pi * u) ** 0.8, u


def walk(rig):
    s = rig.style
    T = s["walk_cycle"]
    a = Animation("walk", T, True)
    step = 0.05
    stance_arm, stance_leg = 0.6, 0.64
    phases = {"leg_l": 0.0, "arm_r": 0.16, "leg_r": 0.5, "arm_l": 0.66}
    if s["lopsided"]:
        phases["arm_r"] += 0.06 * s["lopsided"]  # the weak arm hurries to catch up

    def frame(t):
        ph = t / T
        w = 2.0 * math.pi * ph
        p = Pose()
        # Weight shifts onto each arm as it lands: two dips per cycle, rolls side to side. It walks a little lower
        # than it stands, which gives its (straight, at rest) arms room to reach forward and back.
        p.pos("root", s["sway"] * math.sin(w - 0.5),
              -s["walk_crouch"] - s["bob"] * (0.5 + 0.5 * math.cos(2 * w - 2 * math.pi * 0.16 - 0.6)), 0.0)
        p.rot("pelvis", 1.5 * math.cos(2 * w), -3.0 * math.sin(w), -1.5 * math.sin(w))
        p.rot("torso", -1.2 * math.cos(2 * w + 0.5), 4.0 * math.sin(w + 0.4), s["roll"] * math.sin(w - 0.4))
        # The head keeps fairly level: it counters the torso's roll and twist.
        head_track(p, rx=1.0 * math.cos(2 * w), ry=-3.0 * math.sin(w + 0.4), rz=-0.6 * s["roll"] * math.sin(w - 0.4),
                   jaw=3.0 + 2.0 * math.sin(2 * w), inner=1.0)
        for name, off in phases.items():
            is_arm = name.startswith("arm")
            stance = stance_arm if is_arm else stance_leg
            length = s["stride"] * stance
            lift = s["arm_lift"] if is_arm else s["leg_lift"]
            if is_arm and name == "arm_r" and s["lopsided"]:
                lift *= 1.0 - 0.35 * s["lopsided"]
            dz, dy, u = gait_offset(ph - off, stance, length, lift)
            dx = 0.0
            if is_arm:
                # A lifted fist swings out a little as it passes (easing out and back in), so the arm clears the
                # body and the leg; the fists step a little ahead of where they stand.
                dx = s["arm_bow"] * math.sin(math.pi * u) ** 2 * (1.0 if name.endswith("_l") else -1.0)
                dz -= s["arm_reach"]
            tgt = np.array(rig.rest[name][0], dtype=float) + np.array([dx, dy, dz])
            rig.solve_limb(p, name, tgt)
        return rig.follow(p)

    rig.reset_warm()
    n = int(round(T / step))
    # A lap that isn't kept first: the kept lap then starts the way it ends, so the loop has no seam.
    for i in range(n):
        frame(i * step)
    for i in range(n + 1):
        a.add(i * step, frame(i * step))
    a.bake()
    a.meta["stride_blocks"] = s["stride"] / 16.0
    return a


# ---------------------------------------------------------------- break_shell


def break_shell(rig, seed=7):
    s = rig.style
    m = rig.m
    tb = s["burst"]
    length = tb + 2.3
    a = Animation("break_shell", length, "hold_on_last_frame")
    a.meta["burst"] = tb
    step = 0.05
    rng = np.random.default_rng(seed)
    rig.reset_warm()

    def body(t):
        """The golem's own motion (no plates): tense, rear up, roar, slam down, settle."""
        p = Pose()
        tense = bump(t, 0.0, 0.5, tb - 0.45, tb - 0.25)
        rise = bump(t, tb - 0.45, tb - 0.05, tb + 0.5, tb + 0.85)
        slam = bump(t, tb + 0.55, tb + 0.8, tb + 0.8, tb + 1.25)
        shake = (1.0 - ramp(t, tb + 0.6, tb + 0.8)) * ramp(t, 0.1, tb - 0.2)
        r = s["roar"]
        p.pos("root", 0.0, -2.2 * tense + 5.0 * r * rise - 2.0 * slam, 1.5 * r * rise)
        p.rot("pelvis", 3.0 * tense - 22.0 * r * rise + 6.0 * slam, 0.0, 0.0)
        p.rot("torso", 4.0 * tense - 16.0 * r * rise + 8.0 * slam + 1.6 * shake * wobble(t, 1, 14),
              1.5 * shake * wobble(t, 2, 12), 1.5 * shake * wobble(t, 3, 13))
        head_track(p, rx=6.0 * tense - 28.0 * rise + 10.0 * slam + 2.0 * shake * wobble(t, 4, 15),
                   ry=4.0 * rise * math.sin(t * 22.0), rz=0.0,
                   jaw=3.0 * tense + 38.0 * rise + 10.0 * slam, inner=-6.0 * rise, inner_push=3.5 * rise)
        return p, tense, rise, slam

    # Free-arm pose (arms thrown wide while it roars), as TOTAL angles, blended with the planted pose by `rise`.
    roar = {"arm_l": (-100.0, 10.0, -60.0), "arm_r": (-100.0, -10.0, 60.0),
            "forearm_l": (-50.0, 0.0, 0.0), "forearm_r": (-50.0, 0.0, 0.0),
            "fist_l": (-10.0, 0.0, -10.0), "fist_r": (-10.0, 0.0, 10.0)}

    frames = []
    for i in range(int(round(length / step)) + 1):
        t = i * step
        p, tense, rise, slam = body(t)
        for name in ("leg_l", "leg_r"):
            rig.solve_limb(p, name, rig.rest[name][0])
        planted = Pose()
        for k, v in p.items():
            planted[k] = [list(v[0]), list(v[1]), list(v[2])]
        for name in ("arm_l", "arm_r"):
            tgt = np.array(rig.rest[name][0], dtype=float)
            tgt[1] += 1.0 * slam + 12.0 * rise  # fists land a touch high, then press in; off the ground while it roars
            rig.solve_limb(planted, name, tgt)
        # Blend: planted arms <-> thrown-wide arms.
        for bone, free in roar.items():
            pr = planted.get3(bone)[0]
            rest = m[bone].rotation
            free_off = [free[k] - rest[k] for k in range(3)]
            p.get3(bone)[0] = [pr[k] * (1.0 - rise) + free_off[k] * rise for k in range(3)]
        frames.append((t, rig.follow(p)))

    # Shell plates: jitter while it tenses, then each flies off on its own arc.
    body_mats = [m.pose_matrices(p.as_geo()) for _t, p in frames]
    centre = np.array([0.0, 30.0, 0.0])
    for bone in rig.shell:
        delay = float(rng.uniform(0.0, 0.1))
        t_launch = tb + delay
        k_launch = int(round(t_launch / step))
        # Where the plate is at launch (world = model, Bedrock), and its orientation.
        mats = body_mats[k_launch]
        parent = bone.parent.name
        piv = np.array(bone.pivot)
        p0 = m.point(mats, parent, piv)
        out = p0 - centre
        out[1] = max(out[1], 0.0) * 0.4
        n = np.linalg.norm(out)
        out = out / n if n > 1e-6 else np.array([0.0, 0.0, -1.0])
        speed = float(rng.uniform(55.0, 85.0)) * s["plate_speed"]
        v = out * speed + np.array([0.0, float(rng.uniform(40.0, 75.0)) * s["plate_speed"], 0.0])
        v += rng.normal(0.0, 8.0, 3)
        size = np.array(bone.cubes[0].size, dtype=float) if bone.cubes else np.array([4.0, 4.0, 4.0])
        rest_y = float(np.min(size)) / 2.0
        axis = rng.normal(0.0, 1.0, 3)
        axis /= np.linalg.norm(axis)
        spin = float(rng.uniform(220.0, 520.0)) * (1.0 if rng.random() < 0.5 else -1.0)
        crumble0 = tb + 1.25 + float(rng.uniform(0.0, 0.3))
        crumble1 = crumble0 + 0.45
        jitter_seed = float(rng.uniform(0, 100))
        traj = _ballistic(p0, v, rest_y, t_launch, length, step)
        Q0 = mats[parent][:3, :3]  # parent's orientation at launch (GeckoLib space)
        prev = None
        for k, (t, p) in enumerate(frames):
            pm = body_mats[k]
            if t < t_launch:
                amt = ramp(t, 0.15, tb - 0.1)
                p.rot(bone.name, 2.5 * amt * wobble(t, jitter_seed, 16), 2.5 * amt * wobble(t, jitter_seed + 3, 17),
                      2.5 * amt * wobble(t, jitter_seed + 6, 15))
                p.pos(bone.name, 0.0, 0.0, 0.0)
                continue
            pos_w, landed_at = traj[k]
            # Orientation: tumbling while it flies, still once it has landed.
            tau = (min(t, landed_at) if landed_at is not None else t) - t_launch
            R_spin = geo.axis_angle(_to_gecko_dir(axis), math.radians(spin * tau))
            Q = R_spin @ Q0
            Rp = pm[parent][:3, :3]
            R_local = Rp.T @ Q
            eul = geo.euler_bedrock(R_local, prev)
            prev = eul
            # Position: put the pivot where the trajectory says (in the parent's frame).
            local = np.linalg.inv(pm[parent]) @ np.append(geo.gecko(pos_w), 1.0)
            off_g = local[:3] - geo.gecko(piv)
            off_b = (-off_g[0] * 16.0, off_g[1] * 16.0, off_g[2] * 16.0)  # GeckoLib translate is (-x, y, z)/16
            sink = ramp(t, crumble0, crumble1)
            p.rot(bone.name, *eul)
            p.pos(bone.name, off_b[0], off_b[1] - 0.0, off_b[2])
            sc = max(0.0, 1.0 - sink)
            p.scale(bone.name, sc if sc > 0.001 else 0.0)
    # Molten extras grow out after the burst.
    for bone in rig.molten:
        for t, p in frames:
            g = ease_out((t - tb - 0.05) / 0.4) if t > tb + 0.05 else 0.0
            p.scale(bone.name, g)
    for t, p in frames:
        a.add(t, p)
    a.bake(tol_rot=0.35, tol_pos=0.06)
    a.meta["molten_from"] = tb + 0.05
    return a


def _ballistic(p0, v0, rest_y, t0, t_end, step):
    """
    Where a thrown plate's centre is at every frame time: it flies under gravity, bounces once (if it hits
    hard enough), then slides to a stop on the ground. Also returns when it first touched the ground.
    """
    out = []
    pos = np.array(p0, dtype=float)
    vel = np.array(v0, dtype=float)
    landed_at = None
    bounced = False
    t = t0
    n = int(round(t_end / step)) + 1
    for i in range(n):
        tf = i * step
        if tf <= t0:
            out.append((pos.copy(), None))
            continue
        while t < tf - 1e-9:
            dt = min(step / 8.0, tf - t)
            on_ground = pos[1] <= rest_y + 1e-6 and abs(vel[1]) < 1e-6
            if on_ground:
                vel[0] *= max(0.0, 1.0 - 7.0 * dt)  # sliding friction
                vel[2] *= max(0.0, 1.0 - 7.0 * dt)
                pos[0] += vel[0] * dt
                pos[2] += vel[2] * dt
            else:
                vel[1] -= GRAVITY * dt
                pos += vel * dt
                if pos[1] <= rest_y:
                    pos[1] = rest_y
                    if landed_at is None:
                        landed_at = t
                    if not bounced and vel[1] < -30.0:
                        vel[1] = -vel[1] * 0.28
                        vel[0] *= 0.55
                        vel[2] *= 0.55
                        bounced = True
                    else:
                        vel[1] = 0.0
            t += dt
        out.append((pos.copy(), landed_at))
    return out


def _to_gecko_dir(d):
    return np.array([-d[0], d[1], d[2]], dtype=float)


# ---------------------------------------------------------------- death


DEATH_SCREAM = 3.4   # seconds the death's scream lasts (two waves), from rearing back, jaw wide, to coming down again
DEATH_LATE = DEATH_SCREAM - 1.0   # everything after the scream comes this much later than with a one-second scream
# The cooling (written into GolemDesigns.java through previews.py): the glow fades out, then the statue fades in,
# both slowly; and the animation lasts until the statue is in (its last pose is held).
DEATH_GLOW_FADE = (round(1.6 + DEATH_LATE, 2), round(4.8 + DEATH_LATE, 2))
DEATH_STATUE_FADE = (round(3.6 + DEATH_LATE, 2), round(7.2 + DEATH_LATE, 2))
DEATH_LENGTH = round(DEATH_STATUE_FADE[1] + 0.1, 2)


def death(rig):
    s = rig.style
    m = rig.m
    length = DEATH_LENGTH
    a = Animation("death", length, "hold_on_last_frame")
    step = 0.05
    rig.reset_warm()
    rest = rig.rest
    sc = DEATH_SCREAM
    late = DEATH_LATE
    for i in range(int(round(length / step)) + 1):
        t = i * step
        p = Pose()
        # The scream: it rears back with its jaw wide open and holds it, trembling harder and harder. It screams in
        # two waves (its jaw gapes wider and its head throws further back on each) while its head sways side to side;
        # then it comes down.
        recoil = bump(t, 0.0, 0.4, sc - 0.6, sc)
        hold = bump(t, 0.3, 0.6, sc - 0.7, sc - 0.4)
        wave = hold * (0.5 - 0.5 * math.cos(2.0 * math.pi * max(0.0, t - 0.4) / 1.3))
        sway = hold * math.sin(2.0 * math.pi * max(0.0, t - 0.4) / 2.8)
        tremble = hold * (0.6 + 0.4 * ramp(t, 0.5, sc - 0.6))
        buckle = ramp(t, 0.8 + late, 1.5 + late)
        fall = ramp(t, 1.3 + late, 2.1 + late)
        last = bump(t, 2.15 + late, 2.5 + late, 2.6 + late, 3.1 + late)          # a last heave
        settle = ramp(t, 2.6 + late, 4.2 + late)
        shudder = bump(t, 0.6 + late, 0.9 + late, 2.0 + late, 2.6 + late)
        p.pos("root", -1.5 * buckle, -4.5 * fall - 1.0 * settle + 1.0 * last, -1.0 * fall)
        p.rot("root", 0.0, 4.0 * buckle, 5.0 * buckle - 2.0 * fall)
        p.rot("pelvis", -14.0 * recoil - 2.0 * wave + 8.0 * fall + 1.0 * settle, 0.0, 0.0)
        p.rot("torso", -12.0 * recoil - 3.0 * wave + 10.0 * fall - 4.0 * last + 2.0 * settle
              + 1.2 * shudder * wobble(t, 5, 11) + 1.6 * tremble * wobble(t, 10, 13),
              -6.0 * buckle + 3.0 * sway, 8.0 * buckle - 3.0 * fall)
        head_track(p, rx=-26.0 * recoil - 7.0 * wave + 18.0 * fall - 10.0 * last + 2.5 * tremble * wobble(t, 8, 15),
                   ry=10.0 * buckle - 4.0 * fall + 12.0 * sway + 3.0 * tremble * wobble(t, 9, 12),
                   rz=-8.0 * buckle + 6.0 * fall - 5.0 * sway,
                   jaw=34.0 * recoil + 8.0 * wave + 8.0 * fall + 22.0 * last - 18.0 * settle + 4.0 * tremble * wobble(t, 7, 20),
                   inner=-8.0 * recoil - 3.0 * wave + 6.0 * last, inner_push=3.0 * recoil + 1.0 * wave + 2.0 * last)
        # Legs: stay planted, the knees fold as it sinks.
        for name in ("leg_l", "leg_r"):
            rig.solve_limb(p, name, rest[name][0])
        # Arms: as it rears back, its fists come up off the ground and in towards its chest with it (so the arms stay
        # bent instead of stretching straight and snapping bent again as it comes down); then the left one buckles
        # out to the side and the right one braces, both ending wide of the body so the arms stay clear of it as it
        # slumps between them. The fists stay flat on their knuckles.
        rear_l = recoil
        rear_r = bump(t, 0.03, 0.43, sc - 0.57, sc + 0.03)
        tl = (np.array(rest["arm_l"][0], dtype=float) + np.array([12.0, 0.0, -4.0]) * buckle
              + np.array([2.0, 0.0, -3.0]) * fall + np.array([9.0, 11.0, 8.0]) * rear_l)
        tr = (np.array(rest["arm_r"][0], dtype=float) + np.array([-8.0, 0.0, -5.0]) * fall
              + np.array([-9.0, 10.0, 8.0]) * rear_r)
        rig.solve_limb(p, "arm_l", tl)
        rig.solve_limb(p, "arm_r", tr)
        a.add(t, rig.follow(p))
    a.bake()
    return a


# ---------------------------------------------------------------- erupt (dormant -> erupted)


def erupt(rig, seed=11):
    """
    Halfway through the first stage: pressure builds - it leans forward over its fists, head down, trembling, while
    the volcano rumbles - then the volcano blows (a jolt), it rears up and roars, shakes the lava off and settles.
    The lava pouring down its body is the flow layer, drawn by the mod's renderer (and the previews) after the blast.
    """
    s = rig.style
    tb = s["erupt_blast"]
    length = s["erupt_length"]
    a = Animation("erupt", length, "hold_on_last_frame")
    a.meta["blast"] = tb
    step = 0.05
    rng = np.random.default_rng(seed)
    jitter = {b.name: float(rng.uniform(0, 100)) for b in rig.shell}
    rig.reset_warm()
    for i in range(int(round(length / step)) + 1):
        t = i * step
        p = Pose()
        # Leaning in: it lowers its shoulders over its fists and its head, and holds there, straining.
        lean = ramp(t, 0.1, tb - 0.25) * (1.0 - ramp(t, tb + 0.02, tb + 0.4)) * s["erupt_lean"]
        build = ramp(t, 0.05, tb) * (1.0 - ramp(t, tb, tb + 0.12))
        jolt = bump(t, tb - 0.02, tb + 0.05, tb + 0.1, tb + 0.35)
        roar = bump(t, tb + 0.15, tb + 0.5, tb + 1.3, tb + 1.9)
        shake = ramp(t, 0.15, tb) * (1.0 - ramp(t, tb + 0.25, tb + 0.7))
        # The lean: hips up, chest and head down over its fists (the back and volcanoes tilt ~30 degrees forward).
        p.pos("root", 0.0, 2.0 * lean - 2.5 * jolt + 1.6 * roar * s["roar"], -3.0 * lean + 0.6 * roar)
        p.rot("pelvis", 12.0 * lean - 6.0 * roar * s["roar"])
        p.rot("torso", 18.0 * lean + 2.0 * jolt - 9.0 * roar * s["roar"] + 1.2 * shake * wobble(t, 21, 15),
              1.0 * shake * wobble(t, 22, 13), 1.2 * shake * wobble(t, 23, 12))
        head_track(p, rx=8.0 * lean - 22.0 * roar + 1.5 * shake * wobble(t, 24, 16),
                   ry=5.0 * roar * math.sin(t * 9.0), jaw=2.0 * build + 34.0 * roar, inner=-6.0 * roar,
                   inner_push=3.0 * roar)
        for h in rig.volcano_holders:
            p.rot(h.name, 2.5 * shake * wobble(t, 25, 18), 0.0, 2.5 * shake * wobble(t, 26, 17))
        for name in ("leg_l", "leg_r"):
            rig.solve_limb(p, name, rig.rest[name][0])
        # Bracing: before it leans in, each fist lifts and sets down a little wider and further forward (the right a
        # beat after the left), so the arms stay clear of its sides as it sinks between them; they step back in after
        # the roar.
        for name, k in (("arm_l", 0.0), ("arm_r", 0.12)):
            sx = 1.0 if name == "arm_l" else -1.0
            out = ramp(t, 0.05 + k, 0.4 + k) * (1.0 - ramp(t, length - 0.95 + k, length - 0.55 + k))
            up = (bump(t, 0.05 + k, 0.22 + k, 0.22 + k, 0.4 + k)
                  + bump(t, length - 0.95 + k, length - 0.75 + k, length - 0.75 + k, length - 0.55 + k))
            tgt = np.array(rig.rest[name][0], dtype=float) + np.array([sx * 5.0 * out, 3.0 * up + 6.0 * roar, -2.0 * out])
            rig.solve_limb(p, name, tgt)
        for b in rig.shell:
            amt = build * 1.6
            if amt > 0.01:
                p.rot(b.name, amt * wobble(t, jitter[b.name], 16), amt * wobble(t, jitter[b.name] + 3, 17),
                      amt * wobble(t, jitter[b.name] + 6, 15))
        a.add(t, rig.follow(p))
    a.bake()
    return a


# ---------------------------------------------------------------- the volcano's own animations


def _anchored_column(p, bone, sy, sxz=1.0):
    """Scales a column plate (pivot at its middle) so its base stays where it was."""
    h = bone.cubes[0].size[1]
    sc = p.get3(bone.name)[2]
    sc[0], sc[1], sc[2] = sxz, sy, sxz
    p.pos(bone.name, 0.0, (sy - 1.0) * h / 2.0, 0.0)


def _gravity_in(rig, frame_name):
    """World gravity (units/s^2, straight down) as an animation offset in a frame bone's own axes (rest pose)."""
    R = rig.m.pose_matrices()[frame_name][:3, :3]
    g = R.T @ np.array([0.0, -1.0, 0.0])          # GeckoLib axes of the frame
    return np.array([-g[0], g[1], g[2]]) * GRAVITY  # as a Bedrock offset (GeckoLib flips x)


def volcano_erupting(rig, seed=17):
    """
    The core form's live eruption (its own controller, loops): a pulsing fountain and lava bombs. The fountain and
    the bombs' launch follow the spout's frame (the cone's axis, so they shoot out the way the volcano leans); the
    bombs then fall with world gravity.
    """
    T = rig.style["fountain_period"]
    a = Animation("volcano_erupting", T, True)
    step = 0.05
    rng = np.random.default_rng(seed)
    bombs = [b for b in rig.vents if "_bomb_" in b.name]
    fountains = [b for b in rig.vents if b.name.endswith("_fountain")]
    params = []
    for k, b in enumerate(bombs):
        ang = 2.0 * math.pi * (k + rng.uniform(-0.2, 0.2)) / max(1, len(bombs))
        params.append(dict(phase=k / max(1, len(bombs)) + float(rng.uniform(-0.05, 0.05)),
                           vx=math.cos(ang) * float(rng.uniform(10, 18)), vz=math.sin(ang) * float(rng.uniform(10, 18)),
                           vy=float(rng.uniform(55, 70)), spin=float(rng.uniform(200, 400)),
                           g=_gravity_in(rig, b.parent.name)))
    for i in range(int(round(T / step)) + 1):
        t = i * step
        w = 2.0 * math.pi * t / T
        p = Pose()
        for j, f in enumerate(fountains):
            sy = 0.85 + 0.3 * math.sin(2 * w + j) + 0.12 * math.sin(5 * w + 1.3 * j)
            _anchored_column(p, f, max(0.3, sy), 1.0 + 0.08 * math.sin(3 * w + j))
        for b, q in zip(bombs, params):
            tau = ((t / T - q["phase"]) % 1.0) * T
            fly = 1.1
            if tau < fly:
                g = q["g"]
                x = q["vx"] * tau + 0.5 * g[0] * tau * tau
                y = q["vy"] * tau + 0.5 * g[1] * tau * tau
                z = q["vz"] * tau + 0.5 * g[2] * tau * tau
                sc = 1.0 if tau < 0.85 else max(0.0, 1.0 - (tau - 0.85) / 0.25)
                p.pos(b.name, x, y, z)
                p.rot(b.name, q["spin"] * tau, q["spin"] * 0.6 * tau, 0.0)
                p.scale(b.name, sc)
            else:
                p.scale(b.name, 0.0)
        a.add(t, p)
    a.bake(tol_rot=1.0, tol_pos=0.08)
    return a


def volcano_burst(rig, erupt_anim=None, seed=19):
    """
    The eruption's blast (volcano controller, once): a column of lava shoots out of the crater along the cone (its
    frame, `*_blast`), and rocks fly out and fall with world gravity. With an upright frame (a volcano made with
    jet="upright") the frame is turned back against the body's lean in `erupt_anim`, so that lava goes straight up.
    """
    s = rig.style
    tb = s["erupt_blast"]
    length = s["erupt_length"]
    a = Animation("volcano_burst", length, "hold_on_last_frame")
    step = 0.05
    rng = np.random.default_rng(seed)
    columns = [b for b in rig.bursts if b.name.endswith("_column")]
    rocks = [b for b in rig.bursts if "_rock_" in b.name]
    frames_up = [b for b in rig.m.bones if b.name.endswith("_blast") and getattr(b.parent, "is_volcano", False)
                 and any(abs(v) > 1e-6 for v in b.rotation)]
    prev_up = {f.name: list(f.rotation) for f in frames_up}
    rp = []
    for k, b in enumerate(rocks):
        ang = 2.0 * math.pi * (k + rng.uniform(-0.25, 0.25)) / max(1, len(rocks))
        rp.append(dict(vx=math.cos(ang) * float(rng.uniform(25, 40)), vz=math.sin(ang) * float(rng.uniform(25, 40)),
                       vy=float(rng.uniform(70, 95)), spin=float(rng.uniform(300, 600)),
                       g=_gravity_in(rig, b.parent.name)))
    for i in range(int(round(length / step)) + 1):
        t = i * step
        p = Pose()
        if erupt_anim is not None and frames_up:
            mats = rig.m.pose_matrices(erupt_anim.sample(t))
            for f in frames_up:
                total = geo.euler_bedrock(mats[f.parent.name][:3, :3].T, prev_up[f.name])
                prev_up[f.name] = total
                p.rot(f.name, *[total[k] - f.rotation[k] for k in range(3)])
        for c in columns:
            if t < tb - 0.02:
                p.scale(c.name, 0.0)
            else:
                grow = ease_out((t - tb + 0.02) / 0.2)
                fade = 1.0 - ramp(t, tb + 0.35, tb + 1.1)
                _anchored_column(p, c, max(0.05, 4.0 * grow), max(0.0, fade) * (1.0 + 0.5 * grow))
        for b, q in zip(rocks, rp):
            tau = t - tb
            if tau < 0 or tau > 1.6:
                p.scale(b.name, 0.0)
                continue
            g = q["g"]
            p.pos(b.name, q["vx"] * tau + 0.5 * g[0] * tau * tau, q["vy"] * tau + 0.5 * g[1] * tau * tau,
                  q["vz"] * tau + 0.5 * g[2] * tau * tau)
            p.rot(b.name, q["spin"] * tau, q["spin"] * 0.7 * tau, 0.0)
            p.scale(b.name, 1.0 if tau < 1.3 else max(0.0, 1.0 - (tau - 1.3) / 0.3))
        a.add(t, p)
    a.bake(tol_rot=1.0, tol_pos=0.08)
    return a


def statue(death_anim):
    """The death's last pose, held: what a golem that died earlier shows when it's loaded again."""
    a = Animation("statue", 0.05, "hold_on_last_frame")
    last = death_anim.frames[-1][1]
    a.add(0.0, last)
    a.add(0.05, last)
    a.bake()
    return a


def build_all(design, m, b):
    """Every animation for a design, keyed by short name."""
    from designs import common as C
    limbs = C.limbs(m, b)
    rig = Rig(m, limbs, C.rest_targets(b), getattr(design, "ANIM", {}))
    out = {"idle": idle(rig), "walk": walk(rig), "break_shell": break_shell(rig), "death": death(rig)}
    out["statue"] = statue(out["death"])
    if rig.vents or rig.bursts:
        out["erupt"] = erupt(rig)
        out["volcano_erupting"] = volcano_erupting(rig)
        out["volcano_burst"] = volcano_burst(rig, out["erupt"])
    return out


def combine(*poses):
    """Several controllers' poses for the same moment, as one pose (their bones don't overlap)."""
    out = {}
    for p in poses:
        out.update(p)
    return out
