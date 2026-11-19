"""
The Alpha Moose (CLAUDE.md step 14, the Huntsman's fight): the Huntsman's giant moose at its gallery size (6A2, about
5.3 blocks to its antler tips), in its tack (saddle, blanket, girth, breast strap, bridle, the bone plate on its face,
skulls on the saddle, the spear's cup and the quiver; nobody on it: the stirrups hang and the reins loop back to the
saddle). The Huntsman rides it as a separate mob (rimemobs/huntsman.py), so the two can split in his fight.

Animations (animation.alpha_moose.*):

  idle          (loop)  breathing, looking round, ears turning, a shake of its head
  walk          (loop)  its long-legged four-beat walk
  run           (loop)  a gallop, head up (stage 1, with him in the saddle, and its chases)
  charge        (loop)  the gallop with its head low and its antlers forward (the antler charge)
  ram           (once)  a lunging ram with its antlers (the hit at RAM_HIT seconds)
  back_kick     (once)  its front sinks and both hind legs kick out behind it (the hit at KICK_HIT)
  antler_toss   (once)  its head down and under you, then flung up hard (the hit at TOSS_HIT)
  stomp         (once)  it rears a little and brings both front hooves down (the hit at STOMP_HIT)
  bellow        (once)  neck stretched out and up, a long roar shaking its head (the cone of the roar BELLOW_START-END)
  crack_stamp   (once)  it paws the ground three times, then stamps (the cracks start at CRACK_HIT)
  rear          (once)  the split: it rears up on its hind legs, front hooves pawing the air, and comes down (2.4 s)
  eat           (once)  it kneels on its front knees to graze (like the wild moose)
  sleep         (loop)  asleep in its stall (before the Oracle's quest reaches the Huntsman): lying down, its front legs
                        folded under it, its hind legs tucked under its belly, its neck low and its chin resting on the
                        straw, its sides rising and falling with each slow snore
  death         (once)  its own death (the user's request): it rears up high with a last great bellow, front hooves
                        thrashing, crashes down onto its front knees, its hind legs give way and it rolls over onto its
                        side, legs out; they kick feebly, it lifts its head once and lays it down, and it's still
"""
import math

import numpy as np

from kit.anims import bump, ramp, smooth
from kit.moves import keyed
from rimedesigns import d06_huntsman as H
from rimekit import mobkit
from rimekit.mobkit import Leg, Pose
from rimekit.shapes import Scaled

from . import moose as M

ID = "alpha_moose"
NAME = "Alpha Moose"
K = H.K                         # the gallery's 6A2: rider and moose at the user's 7.7 blocks
GROW = K / M.K                  # how much bigger than the wild moose (its gaits' lengths scale with it)


class _Design:
    """The Alpha Moose and its tack, nobody on it."""

    @staticmethod
    def build(s, v):
        H.mount(Scaled(s, K), v)

    @staticmethod
    def materials(v):
        return H.materials(v)


ALPHA = dict(letter="A2", mood="alpha moose", mount="moose", parts=("moose",), pose="ride", lids=True)
DESIGN = _Design
BUILD_VARIANT = ALPHA
VARIANTS = (dict(name="alpha_moose", design=ALPHA, hidden=()),)
# (its eyelids on bones of their own, on its head, at its eyes; d06_huntsman's LID_IN)
BONES = M.BONES + [("lid_l", (8.5, 104, -76), "head"), ("lid_r", (-8.5, 104, -76), "head")]
LEGS = M.LEGS
ROUTES = {}
FREE = ()

RAM_HIT = 0.45
KICK_HIT = 0.5
TOSS_HIT = 0.55
STOMP_HIT = 0.62
BELLOW_START, BELLOW_END = 0.55, 1.75
CRACK_HIT = 1.55
DEATH_CRY = 0.5                 # its last bellow starts (the game's loud roar)
DEATH_DOWN = 2.85               # it's on its side
GIF_VIEW = (55.0, 12.0, 2.6)

# The gaits, as the wild moose's scaled up: (cycle seconds, stance share, stride units, lift units, sink units).
WALK = (1.7, 0.68, 18.0 * GROW, 5.0 * GROW, 0.6 * GROW)
GALLOP = (0.6, 0.42, 24.0 * GROW, 8.0 * GROW, 1.6 * GROW)
CHARGE = (0.56, 0.4, 26.0 * GROW, 8.0 * GROW, 2.4 * GROW)


def clip_group(bone):
    return M.clip_group(bone)


CLIP_PAIRS = M.CLIP_PAIRS


# ---------------------------------------------------------------- helpers

def _lift_keeping(rig, pitch, point_bone, point):
    """The body's (dy, dz) that keeps `point` (design units, on `point_bone`) where it is at rest when the body pitches
    by `pitch` degrees (rearing round its hind hips, or tipping onto its front)."""
    m, k = rig.m, rig.k
    rest = m.point(m.pose_matrices({}), point_bone, np.asarray(point) * k)
    pose = {"body": ((pitch, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0))}
    moved = m.point(m.pose_matrices(pose), point_bone, np.asarray(point) * k)
    return float(rest[1] - moved[1]), float(rest[2] - moved[2])


HIND_HIP = H.leg_points(1)[1][0]
FRONT_SHOULDER = H.leg_points(1)[0][0]


def _ears_back(p, amount):
    for side in ("l", "r"):
        p.rot(f"ear_{side}", -24.0 * amount, 0.0, 0.0)


def _front_legs(p, upper, shin, foot=0.0, which=("l", "r"), offset=0.0):
    for i, side in enumerate(which):
        p.rot(f"leg_f{side}", upper + (offset if i else 0.0), 0.0, 0.0)
        p.rot(f"shin_f{side}", shin, 0.0, 0.0)
        p.rot(f"foot_f{side}", foot, 0.0, 0.0)


# ---------------------------------------------------------------- the gaits

def walk(rig):
    T, stance, stride, lift, sink = WALK
    phases = {"hl": 0.0, "fl": 0.25, "hr": 0.5, "fr": 0.75}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, -sink - 0.3 + 0.4 * math.cos(2 * w), 0.0)
        p.rot("body", 0.0, 0.0, 1.0 * math.sin(w))
        p.rot("neck", 4.0 * math.sin(2 * w - 0.6), 2.5 * math.sin(w), 0.0)
        p.rot("head", -2.5 * math.sin(2 * w - 1.0), 0.0, 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        return p

    a = mobkit.record(rig, "walk", T, frame)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


def fast(rig, name, gait, neck, head, pitch, bob):
    T, stance, stride, lift, sink = gait
    phases = {"hl": 0.0, "hr": 0.1, "fl": 0.5, "fr": 0.6}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("body", 0.0, -sink + bob * (0.5 * math.sin(2 * w + 0.6) - 0.5), 0.0)
        p.rot("body", pitch * math.sin(w), 0.0, 0.0)
        p.rot("neck", neck - pitch * 0.8 * math.sin(w - 0.4), 0.0, 0.0)
        p.rot("head", head - pitch * 0.4 * math.sin(w - 0.8), 0.0, 0.0)
        _ears_back(p, 1.0 if neck > 10 else 0.4)
        p.rot("tail", 15.0 + 8.0 * math.sin(w), 0.0, 0.0)
        mobkit.gait(rig, p, t, T, phases, stance, stride, lift)
        return p

    a = mobkit.record(rig, name, T, frame, warm=2)
    a.meta["speed"] = mobkit.ground_speed(stride, stance, T)
    return a


# ---------------------------------------------------------------- the moves

def ram(rig):
    T = 1.1

    def frame(t):
        p = Pose()
        neck = float(keyed(t, [(0.0, 0.0), (0.3, 24.0), (RAM_HIT, 16.0, "in"), (0.7, 12.0), (T, 0.0)]))
        head = float(keyed(t, [(0.0, 0.0), (0.3, 30.0), (RAM_HIT, 14.0, "in"), (0.7, 18.0), (T, 0.0)]))
        lunge = float(keyed(t, [(0.0, 0.0), (0.3, 2.0), (RAM_HIT, -6.0, "in"), (0.7, -3.0), (T, 0.0)]))
        drop = float(keyed(t, [(0.0, 0.0), (0.3, -2.0), (RAM_HIT, -1.5), (T, 0.0)]))
        p.pos("body", 0.0, -0.8 + drop, lunge)
        p.rot("body", float(keyed(t, [(0.0, 0.0), (0.3, 3.0), (RAM_HIT, 1.0), (T, 0.0)])), 0.0, 0.0)
        p.rot("neck", neck, 0.0, 0.0)
        p.rot("head", head, 0.0, 0.0)
        _ears_back(p, float(keyed(t, [(0.0, 0.0), (0.25, 1.0), (0.9, 1.0), (T, 0.0)])))
        rig.plant(p)
        return p

    a = mobkit.record(rig, "ram", T, frame, loop=False)
    a.meta["hit"] = RAM_HIT
    return a


def back_kick(rig):
    """Its weight goes onto its front legs (the body tips forward round its shoulders), its head drops, and both hind
    legs kick straight out behind it."""
    T = 1.2

    def frame(t):
        p = Pose()
        tip = float(keyed(t, [(0.0, 0.0), (0.3, 6.0), (KICK_HIT, 16.0, "in"), (0.75, 12.0), (T, 0.0)]))
        dy, dz = _lift_keeping(rig, tip, "body", FRONT_SHOULDER)
        p.pos("body", 0.0, dy - 0.5, dz)
        p.rot("body", tip, 0.0, 0.0)
        p.rot("neck", 1.2 * tip, 0.0, 0.0)
        p.rot("head", 0.6 * tip, 0.0, 0.0)
        _ears_back(p, ramp(t, 0.1, 0.3) * (1.0 - ramp(t, 0.9, T)))
        kick = float(keyed(t, [(0.0, 0.0), (0.32, -0.25), (KICK_HIT, 1.0, "in"), (0.72, 0.8), (1.0, 0.1), (T, 0.0)]))
        for side in ("l", "r"):
            p.rot(f"leg_h{side}", 70.0 * kick, 0.0, 0.0)
            p.rot(f"shin_h{side}", -35.0 * max(kick, 0.0) + 40.0 * max(-kick, 0.0) * 2.0, 0.0, 0.0)
            p.rot(f"foot_h{side}", 30.0 * max(kick, 0.0), 0.0, 0.0)
        p.rot("tail", 30.0 * max(kick, 0.0), 0.0, 0.0)
        rig.plant(p, which=("fl", "fr"))
        if kick <= 0.02 and (t > 1.0 or t < 0.1):
            rig.plant(p, which=("hl", "hr"))
        return p

    a = mobkit.record(rig, "back_kick", T, frame, loop=False)
    a.meta["hit"] = KICK_HIT
    return a


def antler_toss(rig):
    """Its head swings low and forward under you, then flings up and back, its forequarters lifting with it."""
    T = 1.3

    def frame(t):
        p = Pose()
        neck = float(keyed(t, [(0.0, 0.0), (0.38, 26.0), (TOSS_HIT, -26.0, "in"), (0.8, -18.0), (T, 0.0)]))
        head = float(keyed(t, [(0.0, 0.0), (0.38, 30.0), (TOSS_HIT, -24.0, "in"), (0.8, -14.0), (T, 0.0)]))
        rise = float(keyed(t, [(0.0, 0.0), (0.38, 4.0), (TOSS_HIT + 0.05, -10.0, "in"), (0.85, -6.0), (T, 0.0)]))
        lunge = float(keyed(t, [(0.0, 0.0), (0.38, -4.0), (TOSS_HIT, -2.0), (T, 0.0)]))
        dy, dz = _lift_keeping(rig, rise, "body", HIND_HIP)
        p.pos("body", 0.0, dy - 0.6, dz + lunge)
        p.rot("body", rise, 0.0, 0.0)
        p.rot("neck", neck, 0.0, 0.0)
        p.rot("head", head, 0.0, 0.0)
        _ears_back(p, bump(t, 0.1, 0.3, 0.9, 1.2))
        rig.plant(p, which=("hl", "hr"))
        lifted = max(0.0, -rise) / 10.0
        if lifted < 0.05:
            rig.plant(p, which=("fl", "fr"))
        else:
            _front_legs(p, -10.0 * lifted, 30.0 * lifted, -10.0 * lifted)
            rig.plant(p, offsets={n: (0.0, 2.0 * GROW * lifted, -3.0 * lifted) for n in ("fl", "fr")}, which=("fl", "fr"))
        return p

    a = mobkit.record(rig, "antler_toss", T, frame, loop=False)
    a.meta["hit"] = TOSS_HIT
    return a


def _rearing(rig, t, pitch, paw, p):
    """Body pitched up `pitch` (negative: front up) round its hind hips, hind feet planted; the front legs folded and
    pawing (`paw` 0..1 how much)."""
    dy, dz = _lift_keeping(rig, pitch, "body", HIND_HIP)
    p.pos("body", 0.0, dy, dz)
    p.rot("body", pitch, 0.0, 0.0)
    rig.plant(p, which=("hl", "hr"))
    up = min(1.0, -pitch / 40.0) if pitch < 0 else 0.0
    if up > 0.02:
        s = math.sin(2 * math.pi * 2.2 * t)
        p.rot("leg_fl", -40.0 * up - 25.0 * paw * s, 0.0, 0.0)
        p.rot("leg_fr", -40.0 * up + 25.0 * paw * s, 0.0, 0.0)
        for side in ("l", "r"):
            p.rot(f"shin_f{side}", 75.0 * up, 0.0, 0.0)
            p.rot(f"foot_f{side}", 30.0 * up, 0.0, 0.0)
    else:
        rig.plant(p, which=("fl", "fr"))


REAR_NEIGH = 0.7            # (the split) its neigh at the top of its rear: the Huntsman springs off just after it


def rear(rig):
    """The split: it rears up high on its hind legs, neck up, front hooves pawing the air, then comes down."""
    T = 2.4

    def frame(t):
        p = Pose()
        pitch = float(keyed(t, [(0.0, 0.0), (0.2, 4.0), (0.65, -52.0, "out"), (1.55, -48.0), (2.05, 0.0, "in"),
                                (T, 0.0)]))
        _rearing(rig, t, pitch, bump(t, 0.55, 0.75, 1.4, 1.6), p)
        p.rot("neck", 0.45 * pitch, 0.0, 0.0)
        p.rot("head", 0.25 * pitch + 6.0 * math.sin(2 * math.pi * 3.0 * t) * bump(t, 0.6, 0.8, 1.4, 1.6), 0.0, 0.0)
        _ears_back(p, bump(t, 0.2, 0.5, 1.8, 2.2))
        p.rot("tail", 25.0 * bump(t, 0.3, 0.6, 1.6, 2.0), 0.0, 0.0)
        return p

    a = mobkit.record(rig, "rear", T, frame, loop=False)
    a.meta["neigh"] = REAR_NEIGH
    return a


def stomp(rig):
    """It rears a little and brings both front hooves down hard."""
    T = 1.2

    def frame(t):
        p = Pose()
        pitch = float(keyed(t, [(0.0, 0.0), (0.42, -24.0, "out"), (STOMP_HIT, 3.0, "in"), (0.8, 1.0), (T, 0.0)]))
        _rearing(rig, t, pitch, 0.0, p)
        p.rot("neck", 0.5 * pitch + 10.0 * bump(t, STOMP_HIT - 0.05, STOMP_HIT, 0.75, 0.95), 0.0, 0.0)
        p.rot("head", 0.3 * pitch, 0.0, 0.0)
        _ears_back(p, bump(t, 0.1, 0.3, 0.9, 1.1))
        return p

    a = mobkit.record(rig, "stomp", T, frame, loop=False)
    a.meta["hit"] = STOMP_HIT
    return a


def crack_stamp(rig):
    """It paws the ground three times with its right fore hoof (the warning), then rears a little and stamps with both;
    cracks run out from there towards you."""
    T = 2.2

    def frame(t):
        p = Pose()
        paws = (0.15, 0.5, 0.85)
        paw = sum(bump(t, t0, t0 + 0.1, t0 + 0.2, t0 + 0.32) for t0 in paws)
        pitch = float(keyed(t, [(0.0, 0.0), (1.1, 0.0), (1.4, -22.0, "out"), (CRACK_HIT, 3.0, "in"), (1.8, 1.0),
                                (T, 0.0)]))
        if pitch < -0.5 or t > 1.1:
            _rearing(rig, t, pitch, 0.0, p)
        else:
            rig.plant(p, which=("hl", "hr", "fl"))
            # the right fore hoof lifts and scrapes back along the snow
            rig.plant(p, offsets={"fr": (0.0, 9.0 * GROW * paw, 6.0 * GROW * paw - 3.0 * GROW * paw * paw)},
                      which=("fr",))
        p.rot("neck", 14.0 * ramp(t, 0.0, 0.3) * (1.0 - ramp(t, 1.1, 1.3)) + 0.5 * pitch, 0.0, 0.0)
        p.rot("head", 10.0 * ramp(t, 0.0, 0.3) * (1.0 - ramp(t, 1.1, 1.3)) + 0.3 * pitch, 0.0, 0.0)
        _ears_back(p, bump(t, 0.0, 0.2, 1.9, T))
        return p

    a = mobkit.record(rig, "crack_stamp", T, frame, loop=False)
    a.meta["hit"] = CRACK_HIT
    return a


def bellow(rig):
    """A long roar: it braces, stretches its neck out and up, and its head shakes as it bellows."""
    T = 2.2

    def frame(t):
        p = Pose()
        out = ramp(t, 0.1, BELLOW_START) * (1.0 - ramp(t, BELLOW_END, 2.1))
        shake = math.sin(2 * math.pi * 7.0 * t) * bump(t, BELLOW_START, BELLOW_START + 0.15, BELLOW_END - 0.2, BELLOW_END)
        p.pos("body", 0.0, -1.2 * out, 2.0 * out)
        p.rot("body", 2.0 * out, 0.0, 0.0)
        p.rot("neck", -22.0 * out, 0.0, 0.0)
        p.rot("head", -18.0 * out + 3.0 * shake, 4.0 * shake, 0.0)
        _ears_back(p, out)
        p.rot("tail", 20.0 * out, 0.0, 0.0)
        rig.plant(p)
        return p

    a = mobkit.record(rig, "bellow", T, frame, loop=False)
    a.meta.update(start=BELLOW_START, end=BELLOW_END)
    return a


def death(rig):
    """Its death: a last great rear and bellow, down onto its knees, over onto its side; a few kicks, its head lifted
    once and laid down, a last breath."""
    T = 4.6
    rot, kpitch, kdrop = M.kneel_pose(rig)
    # (it rests on its own body: its fur, legs and muzzle; the tack (stirrups, the skulls on the saddle, straps) and
    # its antlers may sink into the snow, so a stirrup under it doesn't hold it up off the ground: the user saw it float)
    body_cubes = [c for b in rig.m.bones if b.name not in ("antlers", "ear_l", "ear_r") for c in b.cubes
                  if c.mat in RESTS_ON]

    def frame(t):
        p = Pose()
        pitch = float(keyed(t, [(0.0, 0.0), (0.15, 4.0), (0.62, -56.0, "out"), (1.15, -50.0), (1.48, 4.0, "in"),
                                (1.6, 0.0), (T, 0.0)]))
        cry = bump(t, DEATH_CRY - 0.05, DEATH_CRY + 0.15, 1.2, 1.4)
        shake = math.sin(2 * math.pi * 4.0 * t) * cry
        if t < 1.55:
            _rearing(rig, t, pitch, bump(t, 0.55, 0.75, 1.05, 1.25), p)
            p.rot("neck", 0.45 * pitch - 18.0 * cry, 4.0 * shake, 0.0)
            p.rot("head", 0.25 * pitch - 22.0 * cry + 5.0 * shake, 3.0 * shake, 0.0)
        else:
            # down on its front knees, then over onto its side
            down = smooth((t - 1.5) / 0.45)
            fold = smooth((t - 1.45) / 0.25)
            over = smooth((t - 2.05) / 0.8)
            p.pos("body", 0.0, -kdrop * down, 0.0)
            p.rot("body", kpitch * down * (1.0 - 0.6 * over), 0.0, 0.0)
            for bone, r in rot.items():
                if bone.startswith("leg_f"):
                    p.rot(bone, *(v * down * (1.0 - 0.7 * over) for v in r))
                elif bone.startswith(("shin_f", "foot_f")):
                    p.rot(bone, *(v * fold * (1.0 - 0.6 * over) for v in r))
            if over < 0.02:
                rig.plant(p, which=("hl", "hr"))
            else:
                # its hind legs give way: out straight as it goes over
                for side in ("l", "r"):
                    p.rot(f"leg_h{side}", -12.0 * over, 0.0, 0.0)
                    p.rot(f"shin_h{side}", 18.0 * over, 0.0, 0.0)
                p.rot("root", 0.0, 0.0, 84.0 * over + 4.0 * bump(t, 2.8, 2.88, 2.92, 3.05))
            # the head: low as it kneels, then lifted once on its side and laid down
            lift_head = bump(t, 3.1, 3.35, 3.5, 3.85)
            p.rot("neck", rot["neck"][0] * 0.4 * down * (1.0 - over) - 14.0 * lift_head, 0.0, -38.0 * over)
            p.rot("head", rot["head"][0] * 0.4 * down * (1.0 - over) - 10.0 * lift_head, 0.0, -18.0 * over)
            # kicks, weaker each time
            kick = math.sin(2 * math.pi * 3.0 * (t - 2.9)) * max(0.0, 1.0 - (t - 2.9) / 0.9) if t > 2.9 else 0.0
            for side, ph in (("l", 0.0), ("r", 1.3)):
                p.rot(f"shin_h{side}", 14.0 * kick * math.cos(ph), 0.0, 0.0)
                p.rot(f"shin_f{side}", 10.0 * kick * math.sin(ph + 0.7), 0.0, 0.0)
            p.pos("body", 0.0, 0.0, 0.0)
            p.rot("body", 1.0 * bump(t, 3.95, 4.15, 4.2, 4.45), 0.0, 0.0)     # a last breath
        _ears_back(p, bump(t, 0.2, 0.5, 4.0, 4.5))
        p.rot("tail", 25.0 * bump(t, 0.3, 0.6, 1.6, 2.0), 0.0, 0.0)
        # nothing of it under the snow (but an antler may dig in)
        mats = rig.m.pose_matrices(p.as_geo())
        low = _lowest_of(mats, body_cubes)
        if low < -0.3:
            p.pos("root", 0.0, -low, 0.0)
        return p

    a = mobkit.record(rig, "death", T, frame, loop=False)
    a.meta.update(cry=DEATH_CRY, down=DEATH_DOWN)
    return a


RESTS_ON = ("fur", "mane", "leg", "leg2", "muzzle", "hoof")

SNORE = 5.0                     # one slow breath in and out while it sleeps (the game's snore comes with each)
# Lying down: its body this far lower (units), its legs folded under it (degrees: upper leg, shin, hoof), found by a
# search for the fold whose lowest point rests on the floor inside the body's length, its belly a few units over the
# straw (November 18, 2026; four candidates were drawn and this one looked most like a resting moose)
# (its front and hind legs touch by about 3 units under its belly, out of sight; less folded, they held it up)
SLEEP_DROP = 28.0
FRONT_TUCK = (50.0, 40.0, -90.0)
HIND_TUCK = (-70.0, 0.0, 30.0)
SLEEP_NECK = 0.55               # its neck and head lowered this share of the way they go to graze (its chin on the straw)


# How far its eyelids slide out over its eyes as it sleeps (design units): from inside its head (the lid 1.6 wide, out
# to 6.9) to 8.3-9.9, over each eye (out to 9.5) and a block of fur bigger all round (d06_huntsman's LID_SIZE), its
# lashes and brow just proud of it
LID_OUT = 3.0


def sleep(rig):
    """
    Asleep in its stall (the user, November 18, 2026: "the moose sleeps ... and snores"): lying down the way a moose
    does, its front legs folded under it (as it kneels to graze) and its hind legs tucked under its belly, its belly on
    the straw, its neck lowered and its chin resting on the straw before it; its sides rise and fall with each slow
    breath (SNORE), its ears drooping back, its eyes shut (its lids slid out over them).
    """
    T = SNORE
    rot, _kpitch, _kdrop = M.kneel_pose(rig)          # (for the neck and head: how far down they go to graze)
    body_cubes = [c for b in rig.m.bones if b.name not in ("antlers", "ear_l", "ear_r") for c in b.cubes
                  if c.mat in RESTS_ON]

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        breath = 0.5 - 0.5 * math.cos(w)
        for side in ("l", "r"):
            for i, part in enumerate(("leg", "shin", "foot")):
                p.rot(f"{part}_f{side}", FRONT_TUCK[i], 0.0, 0.0)
                p.rot(f"{part}_h{side}", HIND_TUCK[i], 0.0, 0.0)
        p.rot("body", 0.8 * breath, 0.0, 0.0)
        p.pos("body", 0.0, -SLEEP_DROP + 0.5 * breath, 0.0)
        p.rot("neck", rot["neck"][0] * SLEEP_NECK, 0.0, 0.0)
        p.rot("head", rot["head"][0] * SLEEP_NECK + 2.0 * breath, 0.0, 0.0)
        _ears_back(p, 0.6)
        for side, x in (("l", 1.0), ("r", -1.0)):       # its eyes shut (each lid slid out its own way)
            p.pos(f"lid_{side}", x * LID_OUT * K, 0.0, 0.0)
        # down onto the straw: its lowest point (body, legs, muzzle; an antler may dig in) on the floor
        mats = rig.m.pose_matrices(p.as_geo())
        low = _lowest_of(mats, body_cubes)
        p.pos("root", 0.0, -low, 0.0)
        return p

    return mobkit.record(rig, "sleep", T, frame)


def _lowest_of(mats, cubes):
    from kit.clipping import _box_frame
    return min(float(mobkit._grid(_box_frame(mats, c))[:, 1].min() * 16.0) for c in cubes)


def animations(rig):
    return {
        "idle": M.idle(rig),
        "walk": walk(rig),
        "run": fast(rig, "run", GALLOP, -2.0, 2.0, 3.0, 1.6 * GROW),
        "charge": fast(rig, "charge", CHARGE, 17.0, 24.0, 4.0, 1.8 * GROW),
        "ram": ram(rig),
        "back_kick": back_kick(rig),
        "antler_toss": antler_toss(rig),
        "stomp": stomp(rig),
        "bellow": bellow(rig),
        "crack_stamp": crack_stamp(rig),
        "rear": rear(rig),
        "eat": M.eat(rig),
        "death": death(rig),
        "sleep": sleep(rig),
    }
