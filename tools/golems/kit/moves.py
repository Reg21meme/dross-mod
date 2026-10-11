"""
The Cinder Colossus's fight moves, generated like the rest of its animations (see anims.py): every frame is a pose,
fists and feet are put on their spots with the IK, and the frames are baked into keyframes.

  swipe          - a backhand sweep of its right fist across the front of it (its melee hit)
  throw          - it punches its right fist into the ground, rips out a chunk of rock (the prop_* bones), lifts it
                   high beside its head and hurls it forward
  slam           - it rears up with both fists raised high (a long, trembling wind-up), then smashes them down into
                   the ground in front of it
  charge_start   - it drops its head, paws the ground with a fist and roars before it charges
  charge         - the charge: a bounding gallop on all fours, head low and first (loops)
  lob            - it hunches over its fists while its volcanoes rumble, then its back heaves and they spit lava
  summon         - it rears up and roars, then pounds the ground: left fist, right fist, both fists (a minion rises
                   at each pound)
  volcano_lob    - the volcano controller's part of the lob: a short spurt of lava out of each crater

Each animation's meta records the moments the mod needs (when a blow lands, when a rock or blob leaves), and
`points` records where (the fist or the fists' midpoint, in the model's Bedrock space), so the mod's hits and
projectiles line up with the animation. GolemDesigns.java gets both (kit/java.py).

The moves use their own arm limits (MOVE_ARM_LIMITS): wider than the walk's, so the arms can go up overhead. The
idle, walk and the other approved animations keep the original limits.
"""
import math

import numpy as np

from . import ik
from .anims import Animation, Pose, Rig, bump, ease_out, head_track, ramp, smooth, wobble

# Shoulder limits for the moves (total degrees: x swings the arm forward (negative) and up, z out to the side).
MOVE_ARM_LIMITS = dict(lo=(-200, -60, -85), hi=(70, 60, 85))
GROUND = np.array([0.0, -1.0, 0.0])


# ---------------------------------------------------------------- helpers


def keyed(t, keys):
    """
    The value at time t from keys [(time, value), ...] or [(time, value, ease), ...], holding the ends. `ease` says
    how the segment ending at that key moves: "smooth" (the default: eases out and in), "in" (speeds up into the key:
    a blow landing), "out" (fast start, slows into the key), "linear".
    """
    def val(k):
        return np.array(k[1], dtype=float)

    if t <= keys[0][0]:
        return val(keys[0])
    for a, b in zip(keys[:-1], keys[1:]):
        if t <= b[0]:
            u = (t - a[0]) / (b[0] - a[0]) if b[0] > a[0] else 1.0
            ease = b[2] if len(b) > 2 else "smooth"
            if ease == "in":
                u = u * u
            elif ease == "out":
                u = 1.0 - (1.0 - u) ** 2
            elif ease == "smooth":
                u = smooth(u)
            return val(a) + (val(b) - val(a)) * u
    return val(keys[-1])


def unit(v):
    v = np.asarray(v, dtype=float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def move_rig(rig):
    """A copy of the rig whose arms may go up overhead (see MOVE_ARM_LIMITS); legs and style are the same."""
    limbs = dict(rig.limbs)
    for side in ("l", "r"):
        old = limbs[f"arm_{side}"]
        shoulder = old.joints[0]
        wide = ik.Joint(shoulder.bone, shoulder.axes, lo=MOVE_ARM_LIMITS["lo"], hi=MOVE_ARM_LIMITS["hi"],
                        prefer=shoulder.prefer, weight=shoulder.weight)
        limbs[f"arm_{side}"] = ik.Limb([wide] + old.joints[1:], old.end_bone, old.contact, old.down, old.forward)
    out = Rig(rig.m, limbs, rig.rest, {})
    out.style = rig.style
    return out


def shoulder(rig, pose, side):
    """Where a shoulder joint is with this body pose (Bedrock model space)."""
    m = rig.m
    mats = m.pose_matrices(pose.as_geo())
    return m.point(mats, f"arm_{side}", m[f"arm_{side}"].pivot)


def reach(rig, side):
    """How far a fist's contact point can get from its shoulder with the arm straight (units)."""
    limb = rig.limbs[f"arm_{side}"]
    return float(np.linalg.norm(np.array(limb.contact) - np.array(rig.m[f"arm_{side}"].pivot)))


def within_reach(rig, pose, side, target, share=0.94):
    """The target pulled in towards the shoulder if the arm can't reach it, so the elbow stays a little bent."""
    s = shoulder(rig, pose, side)
    d = np.asarray(target, dtype=float) - s
    n = np.linalg.norm(d)
    limit = reach(rig, side) * share
    return s + d * (limit / n) if n > limit else np.asarray(target, dtype=float)


def place_fist(rig, pose, side, target, lift=None, heading=None, along=None):
    """
    Puts a fist on a spot: flat on its knuckles near the ground, turning to point along the arm (`along`, or from the
    shoulder to the spot) as it rises, so a raised fist carries on the line of the arm instead of bending back. In
    the air the knuckles' direction matters less than the spot (it's only a guide), so the solver may bend the wrist
    a little more to get there. Returns how far it missed (units).
    """
    target = within_reach(rig, pose, side, target)
    height = float(target[1]) if lift is None else lift
    up = clamp01((height - 2.0) / 14.0)
    if along is None:
        along = unit(target - shoulder(rig, pose, side))
    down = unit(GROUND * (1.0 - up) + unit(along) * up)
    if heading is None and abs(down[1]) > 0.9:
        heading = rig.rest[f"arm_{side}"][1]
    return solve_fist(rig, pose, f"arm_{side}", target, heading, tuple(down), w_down=6.0 - 4.5 * up,
                      w_head=2.0 - 1.5 * up)


def solve_fist(rig, pose, name, target, heading, down, w_down, w_head):
    """Rig.solve_limb with the orientation weights given (warm start, smoothing and the retry are the same)."""
    from .anims import RETRY_MISS
    limb = rig.limbs[name]
    geo_pose = pose.as_geo()
    x0 = rig._warm.get(name)
    solved, err, x = ik.solve(rig.m, geo_pose, limb, target, heading, down=down, x0=x0, w_down=w_down, w_head=w_head,
                              w_smooth=rig.style["ik_smooth"])
    if err > RETRY_MISS:
        for start in (x0, None):
            s2, e2, x2 = ik.solve(rig.m, geo_pose, limb, target, heading, down=down, x0=start, w_down=w_down,
                                  w_head=w_head)
            if e2 < err - 0.1:
                solved, err, x = s2, e2, x2
    rig._warm[name] = x
    for j in limb.joints:
        pose.get3(j.bone)[0] = list(solved[j.bone][0])
    return err


def fist_keys(t, keys, s):
    """
    `keyed` for a fist's target, where a key's value may be ("S", offset): a spot relative to the shoulder `s` (its
    position in this frame), for a fist held up in the air while the body moves under it.
    """
    out = []
    for k in keys:
        v = k[1]
        if isinstance(v, tuple) and len(v) == 2 and v[0] == "S":
            v = s + np.array(v[1], dtype=float)
        out.append((k[0], v) + tuple(k[2:]))
    return keyed(t, out)


def clamp01(x):
    return max(0.0, min(1.0, x))


def plant_legs(rig, pose):
    for name in ("leg_l", "leg_r"):
        rig.solve_limb(pose, name, rig.rest[name][0])


def rest_fist(rig, side):
    return np.array(rig.rest[f"arm_{side}"][0], dtype=float)


def contact_point(rig, pose, side):
    """Where a fist's knuckles are in this pose (Bedrock model space)."""
    m = rig.m
    mats = m.pose_matrices(pose.as_geo())
    return m.point(mats, f"fist_{side}", rig.limbs[f"arm_{side}"].contact)


def bone_point(rig, pose, bone, point):
    m = rig.m
    return m.point(m.pose_matrices(pose.as_geo()), bone, point)


class Recorder:
    """Builds one animation: frames, worst IK miss, and the points the mod needs."""

    def __init__(self, rig, name, length, loop="hold_on_last_frame", step=0.05):
        self.rig = rig
        self.a = Animation(name, length, loop)
        self.step = step
        self.worst = 0.0
        rig.reset_warm()

    def times(self):
        n = int(round(self.a.length / self.step))
        return [i * self.step for i in range(n + 1)]

    def miss(self, err):
        self.worst = max(self.worst, err)

    def add(self, t, pose, settle=0.15):
        """Adds a frame. Over the last `settle` seconds the arms ease back to exactly their rest bend."""
        w = ramp(t, self.a.length - settle, self.a.length) if self.a.loop != True else 0.0
        if w > 0.0:
            for side in ("l", "r"):
                for bone in (f"arm_{side}", f"forearm_{side}", f"fist_{side}"):
                    if bone in pose:
                        r = pose[bone][0]
                        pose[bone][0] = [v * (1.0 - w) for v in r]
        self.a.add(t, self.rig.follow(pose))

    def done(self, **meta):
        self.a.bake()
        self.a.meta.update(meta)
        self.a.meta["worst_miss"] = self.worst
        return self.a


# ---------------------------------------------------------------- swipe (melee)


SWIPE_HIT = 0.6


def swipe(rig):
    """A backhand sweep of its right fist: it draws the fist back and out to its right, then sweeps it across."""
    r = Recorder(rig, "swipe", 1.45)
    hit = SWIPE_HIT
    hit_point = None
    for t in r.times():
        p = Pose()
        wind = bump(t, 0.05, 0.45, 0.48, 0.58)            # drawing back
        swing = bump(t, 0.5, hit, hit + 0.06, hit + 0.5)  # the sweep and its follow-through
        settle = ramp(t, hit + 0.2, 1.35)
        twist = 18.0 * wind - 16.0 * swing * (1.0 - settle)
        p.pos("root", 1.0 * wind - 1.2 * swing, -1.0 * wind - 0.6 * swing, 0.0)
        p.rot("pelvis", 2.0 * wind, 0.4 * twist, -2.0 * wind + 2.0 * swing)
        p.rot("torso", 3.0 * wind - 4.0 * swing, twist, -4.0 * wind + 5.0 * swing)
        head_track(p, rx=-6.0 * wind + 4.0 * swing, ry=-0.5 * twist, rz=0.0, jaw=4.0 + 18.0 * wind + 10.0 * swing,
                   inner=-3.0 * wind, inner_push=1.5 * wind)
        plant_legs(rig, p)
        # Left fist: braced on the ground, a little wider while it swings.
        brace = max(wind, swing)
        r.miss(place_fist(rig, p, "l", rest_fist(rig, "l") + np.array([5.0 * brace, 0.0, -2.0 * brace])))
        # Right fist: drawn back and out, swept across the front at about a player's chest height, back down.
        # (On the way back the fist swings out round the front, clear of its chest.)
        tgt = keyed(t, [(0.0, rest_fist(rig, "r")), (0.15, rest_fist(rig, "r") + [-6, 4, -2]),
                        (0.45, (-40.0, 22.0, -4.0)), (0.52, (-36.0, 23.0, -16.0), "linear"),
                        (hit, (-10.0, 22.0, -46.0), "in"), (hit + 0.1, (6.0, 18.0, -46.0), "out"),
                        (hit + 0.3, (-8.0, 17.0, -50.0)), (1.15, (-28.0, 8.0, -34.0)),
                        (1.35, rest_fist(rig, "r") + [-2.5, 0.5, -1.5]), (1.45, rest_fist(rig, "r"))])
        r.miss(place_fist(rig, p, "r", tgt))
        if abs(t - hit) < r.step / 2:
            hit_point = contact_point(rig, p, "r")
        r.add(t, p)
    return r.done(hit=hit, points=dict(hit=hit_point))


# ---------------------------------------------------------------- throw


THROW_GRAB = 0.6
THROW_RELEASE = 1.4


def throw(rig):
    """
    It punches its right fist into the ground, tears out a chunk of rock (the prop_* bones show from THROW_GRAB until
    THROW_RELEASE), rears up with it held high beside its head, and hurls it forward.
    """
    r = Recorder(rig, "throw", 2.3)
    grab, rel = THROW_GRAB, THROW_RELEASE
    release_point = None
    for t in r.times():
        p = Pose()
        dig = bump(t, 0.2, 0.4, 0.55, grab + 0.05)
        tear = bump(t, grab - 0.05, grab + 0.05, grab + 0.1, grab + 0.25)
        rear = bump(t, grab + 0.1, 1.15, 1.22, rel)              # rearing up with the rock high
        lunge = bump(t, rel - 0.12, rel + 0.02, rel + 0.12, rel + 0.6)
        settle = ramp(t, rel + 0.3, 2.2)
        shake = dig * 0.8 + tear
        p.pos("root", -1.0 * rear, -1.5 * dig + 3.0 * rear - 2.0 * lunge, 1.5 * rear - 2.5 * lunge)
        p.rot("pelvis", 2.0 * dig - 9.0 * rear + 4.0 * lunge, 0.0, 0.0)
        p.rot("torso", 5.0 * dig - 12.0 * rear + 8.0 * lunge + 1.2 * shake * wobble(t, 31, 14),
              6.0 * rear - 8.0 * lunge, -3.0 * dig + 4.0 * rear + 1.0 * shake * wobble(t, 32, 13))
        head_track(p, rx=6.0 * dig - 14.0 * rear + 10.0 * lunge, ry=-5.0 * rear, rz=0.0,
                   jaw=3.0 + 6.0 * tear + 14.0 * rear + 30.0 * lunge, inner=-4.0 * lunge, inner_push=2.0 * lunge)
        plant_legs(rig, p)
        # Left fist: planted, coming off the ground with the body while it rears (so the arm doesn't lock straight).
        r.miss(place_fist(rig, p, "l", rest_fist(rig, "l") + np.array([2.0 * rear + 4.0 * lunge, 7.0 * rear,
                                                                       2.0 * rear - 4.0 * lunge])))
        # Right fist: down into the ground, the rock torn out, lifted high beside its head (held above its shoulder
        # while it rears), hurled forward.
        # The arm can't bring the fist in close past its own shoulder, so the rock swings up in an arc in front of it.
        s = shoulder(rig, p, "r")
        tgt = fist_keys(t, [(0.0, rest_fist(rig, "r")), (0.22, rest_fist(rig, "r") + [-2, 7, -4]),
                            (0.4, rest_fist(rig, "r") + [0, -1.5, -7], "in"), (grab, rest_fist(rig, "r") + [0, -1, -7]),
                            (grab + 0.25, (-30.0, 20.0, -16.0), "out"), (0.98, ("S", (-8.0, -4.0, -34.0))),
                            (1.12, ("S", (-6.0, 28.0, -8.0))), (1.24, ("S", (-4.0, 27.0, 9.0))),
                            (1.32, ("S", (1.0, 24.0, -24.0)), "in"), (rel, ("S", (4.0, 4.0, -42.0)), "linear"),
                            (rel + 0.18, (-26.0, 26.0, -54.0), "out"), (1.85, (-33.0, 12.0, -36.0)),
                            (2.1, rest_fist(rig, "r") + [-3, 1, -1]), (2.3, rest_fist(rig, "r"))], s)
        r.miss(place_fist(rig, p, "r", tgt))
        if abs(t - rel) < r.step / 2:
            release_point = bone_point(rig, p, "fist_r", rig.m.prop_rock_center)
        r.add(t, p)
    return r.done(grab=grab, release=rel, points=dict(release=release_point))


# ---------------------------------------------------------------- slam


SLAM_RAISED = 0.75
SLAM_HIT = 1.5


def slam(rig):
    """
    It rears up with both fists raised high and holds them there trembling (about a second: the warning), then smashes
    them down together into the ground in front of it and shudders.
    """
    r = Recorder(rig, "slam", 2.6)
    hit = SLAM_HIT
    hit_point = None
    for t in r.times():
        p = Pose()
        brace = bump(t, 0.0, 0.2, 0.25, 0.45)
        rear = ramp(t, 0.2, SLAM_RAISED) * (1.0 - ramp(t, hit - 0.22, hit - 0.04))
        smash = bump(t, hit - 0.2, hit, hit + 0.25, hit + 0.75)
        tremble = ramp(t, 0.5, hit - 0.25) * (1.0 - ramp(t, hit - 0.22, hit - 0.1))
        shudder = bump(t, hit, hit + 0.04, hit + 0.15, hit + 0.45)
        settle = ramp(t, hit + 0.45, 2.5)
        p.pos("root", 0.0, -1.5 * brace + 7.0 * rear - 3.0 * smash, 3.5 * rear - 1.0 * smash)
        p.rot("pelvis", 2.0 * brace - 22.0 * rear + 5.0 * smash, 0.0, 0.0)
        p.rot("torso", 3.0 * brace - 20.0 * rear + 8.0 * smash + 1.5 * tremble * wobble(t, 41, 16)
              + 1.5 * shudder * wobble(t, 44, 22), 1.0 * tremble * wobble(t, 42, 13),
              1.2 * tremble * wobble(t, 43, 15))
        head_track(p, rx=-18.0 * rear - 6.0 * smash + 1.5 * tremble * wobble(t, 45, 17),
                   ry=0.0, rz=0.0, jaw=4.0 + 30.0 * rear * (1.0 - smash) + 12.0 * smash, inner=-6.0 * rear,
                   inner_push=2.5 * rear)
        for h in rig.volcano_holders:
            p.rot(h.name, 1.5 * shudder * wobble(t, 46, 20), 0.0, 1.5 * shudder * wobble(t, 47, 19))
        plant_legs(rig, p)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            rest = rest_fist(rig, side)
            # Up round the outside of its shoulders, held high either side of its head, smashed down either side of it.
            # (Held high above its shoulders, the elbows only a little bent, so the thick forearms stay clear of it.)
            s = shoulder(rig, p, side)
            tgt = fist_keys(t, [(0.0, rest), (0.2, rest + [sx * 2, 3, 0]), (0.38, (sx * 33.0, 24.0, -36.0)),
                                (SLAM_RAISED - 0.2, (sx * 33.0, 58.0, -34.0)), (SLAM_RAISED, ("S", (-sx * 4.0, 34.0, -14.0))),
                                (hit - 0.22, ("S", (-sx * 5.0, 35.0, -10.0))),
                            (hit - 0.14, (sx * 20.0, 76.0, -46.0), "in"), (hit - 0.06, (sx * 18.0, 36.0, -56.0), "linear"),
                            (hit, (sx * 16.0, -1.0, -40.0), "linear"),
                            (hit + 0.45, (sx * 16.0, -0.5, -40.0)), (2.05, (sx * 23.0, 5.0, -32.0)),
                            (2.35, rest + [0, 1, 0]), (2.6, rest)], s)
            if t < hit:
                tgt = tgt + np.array([0.6, 0.6, 0.6]) * tremble * wobble(t, 48 + sx, 18)
            r.miss(place_fist(rig, p, side, tgt))
        if abs(t - hit) < r.step / 2:
            hit_point = (contact_point(rig, p, "l") + contact_point(rig, p, "r")) / 2.0
        r.add(t, p)
    return r.done(raised=SLAM_RAISED, hit=hit, points=dict(hit=hit_point))


# ---------------------------------------------------------------- charge


def charge_start(rig):
    """It drops its head low, horns first, rakes the ground back with its right fist and roars, about to charge."""
    r = Recorder(rig, "charge_start", 1.0)
    for t in r.times():
        p = Pose()
        crouch = ramp(t, 0.0, 0.3)
        roar = bump(t, 0.25, 0.4, 0.7, 0.9)
        push = ramp(t, 0.82, 1.0)
        p.pos("root", 0.0, -2.0 * crouch + 1.0 * push, -1.0 * crouch - 2.5 * push)
        p.rot("pelvis", 4.0 * crouch, 0.0, 0.0)
        p.rot("torso", 7.0 * crouch - 2.0 * roar + 1.0 * roar * wobble(t, 51, 15), 0.0, 0.0)
        head_track(p, rx=14.0 * crouch - 4.0 * roar, ry=0.0, rz=0.0, jaw=4.0 + 28.0 * roar, inner=-5.0 * roar,
                   inner_push=2.0 * roar)
        plant_legs(rig, p)
        r.miss(place_fist(rig, p, "l", rest_fist(rig, "l") + np.array([3.0, 0.0, -5.0]) * crouch))
        # The right fist rakes the ground: up, forward, down and dragged back (out to its side, clear of its chest).
        tgt = keyed(t, [(0.0, rest_fist(rig, "r")), (0.3, rest_fist(rig, "r") + [-4, 8, -10]),
                        (0.45, rest_fist(rig, "r") + [-3, 0, -12], "in"),
                        (0.75, rest_fist(rig, "r") + [-3, 0, 1], "linear"), (1.0, rest_fist(rig, "r") + [-2, 0, -6])])
        r.miss(place_fist(rig, p, "r", tgt))
        r.add(t, p)
    return r.done()


GALLOP_CYCLE = 0.6
GALLOP_STRIDE = 40.0


def gait(phase, stance, length, lift):
    """Like anims.gait_offset: (dz, dy) of one limb at a point of its cycle (planted: sliding back; swinging: up)."""
    ph = phase % 1.0
    if ph < stance:
        u = ph / stance
        return -length / 2.0 + length * u, 0.0, 0.0
    u = (ph - stance) / (1.0 - stance)
    e = 0.5 - 0.5 * math.cos(math.pi * u)
    return length / 2.0 - length * e, lift * math.sin(math.pi * u) ** 0.8, u


def charge(rig):
    """
    The charge: a bounding gallop, both fists reaching out together, then both feet; the spine stretching and
    bunching, the head low and leading. Loops; the mod moves the golem much faster than the stride (it's a blur).
    """
    T = GALLOP_CYCLE
    a = Animation("charge", T, True)
    step = 0.025
    stance = 0.42
    phases = {"arm_l": 0.0, "arm_r": 0.07, "leg_l": 0.5, "leg_r": 0.56}

    def frame(t):
        ph = t / T
        w = 2.0 * math.pi * ph
        p = Pose()
        flex = math.sin(w)                                    # + bunched (feet forward), - stretched (fists out)
        p.pos("root", 0.0, -1.0 + 2.2 * math.cos(2 * w - 0.6), -2.0)
        p.rot("pelvis", 3.0 + 4.0 * flex, 0.0, 0.0)
        p.rot("torso", 4.0 - 3.0 * flex, 1.5 * math.sin(w + 0.5), 1.0 * math.sin(w))
        head_track(p, rx=16.0 + 4.0 * flex, ry=0.0, rz=0.0, jaw=8.0 + 4.0 * math.sin(2 * w), inner=1.0)
        for name, off in phases.items():
            is_arm = name.startswith("arm")
            length = GALLOP_STRIDE * stance
            lift = 13.0 if is_arm else 9.0
            dz, dy, u = gait(ph - off, stance, length, lift)
            dx = 0.0
            if is_arm:
                dx = 7.0 * math.sin(math.pi * u) ** 2 * (1.0 if name.endswith("_l") else -1.0) + (3.5 if name.endswith("_l") else -3.5)
                dz -= 10.0
            tgt = np.array(rig.rest[name][0], dtype=float) + np.array([dx, dy, dz])
            if is_arm:
                place_fist(rig, p, name[-1], tgt)
            else:
                rig.solve_limb(p, name, tgt)
        return rig.follow(p)

    rig.reset_warm()
    n = int(round(T / step))
    for i in range(n):
        frame(i * step)
    for i in range(n + 1):
        a.add(i * step, frame(i * step))
    a.bake()
    a.meta["stride_blocks"] = GALLOP_STRIDE / 16.0
    return a


# ---------------------------------------------------------------- lob


LOB_RELEASE = 1.05


def lob(rig):
    """It braces and hunches over its fists while the volcanoes rumble, then its back heaves and they spit lava."""
    r = Recorder(rig, "lob", 2.2)
    rel = LOB_RELEASE
    shell = rig.shell
    jitter = {b.name: (hash(b.name) % 1000) / 10.0 for b in shell}
    for t in r.times():
        p = Pose()
        hunch = ramp(t, 0.15, rel - 0.15) * (1.0 - ramp(t, rel, rel + 0.25))
        build = ramp(t, 0.1, rel) * (1.0 - ramp(t, rel, rel + 0.1))
        heave = bump(t, rel - 0.04, rel + 0.06, rel + 0.12, rel + 0.5)
        roar = bump(t, rel + 0.05, rel + 0.25, rel + 0.55, rel + 0.9)
        p.pos("root", 0.0, 1.4 * hunch + 2.0 * heave, -2.0 * hunch + 1.0 * heave)
        p.rot("pelvis", 8.0 * hunch - 4.0 * heave, 0.0, 0.0)
        p.rot("torso", 11.0 * hunch - 9.0 * heave + 1.0 * build * wobble(t, 61, 15), 0.8 * build * wobble(t, 62, 13),
              1.0 * build * wobble(t, 63, 12))
        head_track(p, rx=6.0 * hunch - 14.0 * roar, ry=4.0 * roar * math.sin(t * 9.0), rz=0.0,
                   jaw=2.0 * build + 26.0 * roar, inner=-5.0 * roar, inner_push=2.0 * roar)
        for h in rig.volcano_holders:
            p.rot(h.name, 2.0 * build * wobble(t, 64, 18) - 6.0 * heave, 0.0, 2.0 * build * wobble(t, 65, 17))
        plant_legs(rig, p)
        for side, sx, k in (("l", 1.0, 0.0), ("r", -1.0, 0.1)):
            out = ramp(t, 0.05 + k, 0.35 + k) * (1.0 - ramp(t, 1.75 + k, 2.05 + k))
            up = bump(t, 0.05 + k, 0.2 + k, 0.2 + k, 0.35 + k) + bump(t, 1.75 + k, 1.9 + k, 1.9 + k, 2.05 + k)
            tgt = rest_fist(rig, side) + np.array([sx * 4.0 * out, 3.0 * up + 3.0 * roar, -1.5 * out])
            r.miss(place_fist(rig, p, side, tgt))
        for b in shell:
            amt = build * 1.2
            if amt > 0.01:
                p.rot(b.name, amt * wobble(t, jitter[b.name], 16), amt * wobble(t, jitter[b.name] + 3, 17),
                      amt * wobble(t, jitter[b.name] + 6, 15))
        r.add(t, p)
    return r.done(release=rel)


def volcano_lob(rig):
    """The volcano controller's part of the lob: each crater spurts a short column of lava as the blobs leave."""
    from .anims import _anchored_column
    a = Animation("volcano_lob", 2.2, "hold_on_last_frame")
    columns = [b for b in rig.bursts if b.name.endswith("_column")]
    rocks = [b for b in rig.bursts if "_rock_" in b.name]
    rel = LOB_RELEASE
    step = 0.05
    for i in range(int(round(2.2 / step)) + 1):
        t = i * step
        p = Pose()
        for c in columns:
            if t < rel - 0.05:
                p.scale(c.name, 0.0)
            else:
                grow = ease_out((t - rel + 0.05) / 0.15)
                fade = 1.0 - ramp(t, rel + 0.2, rel + 0.6)
                _anchored_column(p, c, max(0.05, 1.8 * grow), max(0.0, fade) * (1.0 + 0.3 * grow))
        for b in rocks:
            p.scale(b.name, 0.0)
        a.add(t, p)
    a.bake(tol_rot=1.0, tol_pos=0.08)
    return a


# ---------------------------------------------------------------- summon


SUMMON_POUNDS = (0.95, 1.45, 2.05)


def summon(rig):
    """
    It rears up and roars, then pounds the ground: its left fist, its right fist, then both together. The mod raises a
    minion at each pound (to its left, to its right, in front of it).
    """
    r = Recorder(rig, "summon", 2.9)
    p1, p2, p3 = SUMMON_POUNDS
    for t in r.times():
        p = Pose()
        rear = bump(t, 0.0, 0.35, 0.55, 0.8)
        roar = bump(t, 0.1, 0.35, 0.55, 0.75)
        h1 = bump(t, p1 - 0.02, p1, p1 + 0.06, p1 + 0.3)
        h2 = bump(t, p2 - 0.02, p2, p2 + 0.06, p2 + 0.3)
        h3 = bump(t, p3 - 0.02, p3, p3 + 0.1, p3 + 0.45)
        shake = h1 + h2 + 1.6 * h3
        p.pos("root", 1.2 * h1 - 1.2 * h2, 3.0 * rear - 1.5 * h1 - 1.5 * h2 - 2.5 * h3, 1.2 * rear - 1.0 * h3)
        p.rot("pelvis", -8.0 * rear + 3.0 * h3, 0.0, 0.0)
        p.rot("torso", -8.0 * rear + 4.0 * (h1 + h2) + 8.0 * h3 + 1.2 * shake * wobble(t, 71, 20),
              -6.0 * h1 + 6.0 * h2, 5.0 * h1 - 5.0 * h2)
        head_track(p, rx=-16.0 * roar + 6.0 * h3, ry=0.0, rz=0.0, jaw=4.0 + 32.0 * roar + 10.0 * h3,
                   inner=-6.0 * roar, inner_push=2.5 * roar)
        plant_legs(rig, p)
        for side, sx, pound in (("l", 1.0, p1), ("r", -1.0, p2)):
            rest = rest_fist(rig, side)
            down = rest + np.array([sx * 3.0, -1.0, -9.0])
            both = (sx * 17.0, -1.0, -34.0)
            # Each fist waits on the ground until it's its turn, rises out in front of its shoulder (the elbow only a
            # little bent, so the thick forearm stays clear of its chest) and comes down well in front of it.
            s = shoulder(rig, p, side)
            wait = 0.6 if side == "l" else p1 + 0.08
            tgt = fist_keys(t, [(0.0, rest), (0.35, rest + [0, 8, 0]), (0.6, rest + [sx * 3, 4, -3]),
                                (wait, rest + [0, 1, 0]),
                                (pound - 0.24, ("S", (sx * 6.0, 2.0, -40.0)), "out"), (pound, down, "in"),
                                (pound + 0.12, down), (p3 - 0.3, ("S", (sx * 4.0, 4.0, -40.0))), (p3, both, "in"),
                                (p3 + 0.4, both), (2.62, (sx * 25.0, 4.0, -32.0)), (2.8, rest + [0, 1, 0]), (2.9, rest)],
                            s)
            if t < 0.6:
                tgt = keyed(t, [(0.0, rest), (0.35, rest + [sx * 5, 8 if side == "l" else 6, -5]),
                                (0.6, rest + [sx * 3, 4, -3])])
            r.miss(place_fist(rig, p, side, tgt))
        r.add(t, p)
    return r.done(pounds=SUMMON_POUNDS)


# ---------------------------------------------------------------- all of them


MOVE_NAMES = ("swipe", "throw", "slam", "charge_start", "charge", "lob", "summon")


def build_moves(rig):
    """Every fight move for a design, keyed by name (the volcano's lob spurt too, if it has volcanoes)."""
    mr = move_rig(rig)
    out = {"swipe": swipe(mr), "throw": throw(mr), "slam": slam(mr), "charge_start": charge_start(mr),
           "charge": charge(mr), "lob": lob(mr), "summon": summon(mr)}
    if rig.bursts:
        out["volcano_lob"] = volcano_lob(mr)
    return out
