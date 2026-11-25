"""
The Silverwood Giant's animations (animation.forest_giant.<name>), worked out for each look on its own rest pose
(tools/forest_giant/mobs.py writes one animation file a look):

  idle    (loop)  heavy breathing, a slow look round, a blink
  walk    (loop)  a slow, heavy walk on bent knees, its weight rolling from foot to foot, its free arm swinging
  run     (loop)  a lumbering run for its chase, leaning in, its arms pumping
  warn    (once)  its patience running out: it stares (its eyes flare), huffs, and stamps a foot
  smash   (once)  a big overhead blow: G1 and G2 bring their left fist down, G3 its tree
  sweep   (once)  a backhand with its left fist across in front of it, out to its left
  stomp   (once)  it lifts its right knee high and stamps, its arms swinging down with it
  throw   (once)  G1 heaves the boulder off its shoulder and hurls it, then tears a new one out of the ground and
                  shoulders it; G2 and G3 tear a rock out of the ground with their left hand and hurl it
  hurt    (once)  a flinch
  death   (once)  it rears back with a last roar, drops to its knees and falls on its face, and stays down

Each one's moments (`a.meta`: when a blow lands, when the boulder leaves its hand and where, its footfalls...) go into
tools/forest_giant/moments.json for the game.
"""
import math

import numpy as np

from kit.anims import Animation, bump, ramp
from kit.moves import keyed
from rimekit import mobkit
from rimekit.shapes import unit, v3

from . import props
from .motion import Pose

STEP = mobkit.STEP

# How each look holds what it carries, and which arm does what.
LOOKS = {
    # G1: a boulder on its right shoulder (its right fist steadying it), its left arm free
    "hurler": dict(smash="l", sweep="l", throw="shoulder", hold={"l": "free", "r": "boulder"},
                   swing={"l": 1.0, "r": 0.12}, turn=(20.0, 4.0)),
    # G2: nothing in its hands at all (the user, November 18, 2026: "no armor, just hands")
    "brawler": dict(smash="l", sweep="l", throw="ground", hold={"l": "free", "r": "free"},
                    swing={"l": 1.0, "r": 1.0}, turn=(20.0, 20.0)),
    # G3: a whole uprooted tree hanging from its right fist, its left arm free
    "treebearer": dict(smash="r", sweep="l", throw="ground", hold={"l": "free", "r": "club"},
                       swing={"l": 1.0, "r": 0.12}, turn=(20.0, 20.0)),
}

FREE_OUT = 3.0            # a free arm hangs this far further out than its rest pose (clear of the thigh as it swings)
HOLD_OUT = {"free": FREE_OUT, "club": 2.0, "spear": 1.5}   # (a club's arm clear of its thigh, the spear of its chest)
CLUB_CLEAR = 0.7          # G3's club's end never lower than this over the floor (it tilts forward instead)
SPEAR_CLEAR = 2.0          # G2 never lets its spear's butt down to the ground: its wrist at least this far over its rest
WALK = dict(cycle=2.0, stance=0.6, stride=24.0, lift=6.0, crouch=3.5)    # seconds, share, units, units, units
RUN = dict(cycle=1.0, stance=0.38, stride=22.0, lift=10.0, crouch=6.0)


def k(t, keys):
    return float(keyed(t, keys))


def kv(t, keys):
    return np.asarray(keyed(t, keys), dtype=float)


def wave(t, period, phase=0.0):
    return math.sin(2.0 * math.pi * (t / period + phase))


def look(G):
    return LOOKS[G.id]


def sgn(side):
    return 1.0 if side == "l" else -1.0


def record(G, name, length, frame, loop=True, warm=1):
    """Records an animation: frame(t) -> Pose every STEP; a loop is played `warm` times first so the IK's start has
    settled and its end meets its start."""
    a = Animation(name, length, True if loop else "hold_on_last_frame")
    G.reset()
    n = int(round(length / STEP))
    if loop:
        for _ in range(warm):
            for i in range(n):
                frame(i * STEP)
    for i in range(n + 1):
        a.add(i * STEP, frame(i * STEP))
    G.smooth_kilt(a.frames, a.loop)
    a.bake(0.3, 0.04, 0.01)
    return a


# ---------------------------------------------------------------- holding what it carries


def rest_out(G, side):
    """How far a look's arm hangs from its rest pose in every animation (a free arm a little out from its thigh)."""
    return v3(sgn(side) * HOLD_OUT.get(look(G)["hold"][side], 0.0), 0.0, 0.0)


def hold(G, p, side, offset=(0.0, 0.0, 0.0), mats=None):
    """One arm as its look holds it, its wrist moved by `offset` (in the chest's frame). G2's spear stays upright (it
    leans a little with him) and its butt never comes down to the ground."""
    how = look(G)["hold"][side]
    offset = v3(offset) + rest_out(G, side)
    mats = mats if mats is not None else G.mats(p)
    if how == "spear":
        A = G.arms[side]
        w = G.chest_point(p, A.W0 + v3(offset), mats)
        w[1] = max(w[1], A.W0[1] + SPEAR_CLEAR)
        up = unit(v3(0.0, 1.0, 0.0) + 0.35 * (G.chest_dir(p, (0.0, 1.0, 0.0), mats) - v3(0.0, 1.0, 0.0)))
        return G.arm(p, side, w, G.rest_pole(p, side, mats), grip=up, mats=mats)
    grip = None if how == "free" else G.chest_dir(p, G.arms[side].grip0, mats)
    if how == "club":
        A = G.arms[side]
        w = G.chest_point(p, A.W0 + v3(offset), mats)
        return club_arm(G, p, side, w, G.rest_pole(p, side, mats), grip, mats)
    return G.arm_rest(p, side, offset, grip=grip, mats=mats)


def club_arm(G, p, side, wrist, pole, grip, mats, clear=CLUB_CLEAR):
    """G3's club arm: if the club's broken end would reach the floor, it's tilted forward (towards level) until it
    clears it; failing that the wrist comes up."""
    level = unit(G.chest_dir(p, (0.0, 0.0, -1.0), mats) * v3(1.0, 0.0, 1.0) + v3(0.0, -0.12, 0.0))
    w = v3(wrist)
    for frac in (0.0, 0.25, 0.5, 0.75, 1.0):
        g_ = unit(v3(grip) * (1.0 - frac) + level * frac)
        out = G.arm(p, side, w, pole, grip=g_, mats=mats)
        low = bone_low(G, p, "weapon")
        if low is None or low >= clear:
            return out
    w[1] += clear - low
    return G.arm(p, side, w, pole, grip=g_, mats=mats)


def hide_props(G, p, show=()):
    """The rock (G2, G3) is only seen in the throw."""
    if G.has("rock") and "rock" not in show:
        G.hide(p, "rock")


def finish(G, p, show=()):
    hide_props(G, p, show)
    return G.follow(p)


def eyes(p, flare=1.0, squint=1.0):
    """The eyes' bone: `flare` makes them bigger (glaring), `squint` (0..1) narrows them (a blink at 0)."""
    s = p.get3("eyes")[2]
    s[0] *= flare
    s[1] *= flare * squint
    s[2] *= flare


# ---------------------------------------------------------------- idle, walk, run


def idle(G):
    L = 6.0
    sw = look(G)["swing"]

    def frame(t):
        p = Pose()
        br = wave(t, L / 3.0)                        # three slow breaths a loop
        ex = max(0.0, -br)                           # breathing out
        lk = wave(t, L, 0.08)                        # one slow look each way
        p.pos("body", 0.0, -0.3 - 0.35 * br, 0.0)
        p.rot("body", 0.4 * br, 0.0, 0.0)
        p.rot("chest", -1.6 * br, 5.0 * lk, 0.6 * lk)
        tl, tr = look(G)["turn"]                     # (G1 never into its boulder, G2 never into its shield)
        turn = (tl if lk < 0.0 else tr) * lk
        p.rot("head", 1.5 * wave(t, L / 3.0, 0.2) + 3.0 * ex, turn, -2.5 * lk)
        p.rot("jaw", 2.0 + 4.0 * ex)
        eyes(p, squint=1.0 - 0.92 * bump(t, 2.3, 2.37, 2.42, 2.5))
        G.plant(p)
        mats = G.mats(p)
        for side in ("l", "r"):
            hold(G, p, side, (0.0, 0.5 * br * sw[side], 0.4 * ex * sw[side]), mats)
        return finish(G, p)

    a = record(G, "idle", L, frame)
    a.meta["breaths"] = 3
    return a


def walk(G):
    W = WALK
    cyc, share, stride, lift, crouch = W["cycle"], W["stance"], W["stride"], W["lift"], W["crouch"]
    phases = {"leg_l": 0.0, "leg_r": 0.5}
    sw = look(G)["swing"]

    def frame(t):
        p = Pose()
        w = 2.0 * math.pi * t / cyc
        # lowest as each foot takes its weight, highest over the planted foot; swaying over it; the hips turning
        # with the stride and the chest against them
        p.pos("body", 1.3 * math.sin(w), -crouch - 1.1 * math.cos(2.0 * w - 0.4), 0.0)
        p.rot("body", 2.0 + 1.0 * math.cos(2.0 * w), -5.0 * math.sin(w), -1.2 * math.sin(w))
        p.rot("chest", 2.0 - 0.8 * math.cos(2.0 * w), 4.0 * math.sin(w), 1.6 * math.sin(w))
        p.rot("head", -3.0 + 1.2 * math.cos(2.0 * w), 0.8 * math.sin(w), -0.6 * math.sin(w))
        p.rot("jaw", 2.0)
        mobkit.gait(G.legs, p, t, cyc, phases, share, stride, lift)
        mats = G.mats(p)
        for side in ("l", "r"):
            s = -math.sin(w) * sgn(side)             # the left arm forward as the right leg swings
            hold(G, p, side, (0.0, 1.5 * abs(s) * sw[side], -7.0 * s * sw[side]), mats)
        return finish(G, p)

    a = record(G, "walk", cyc, frame, warm=2)
    a.meta["blocks_per_loop"] = stride / share / 16.0
    a.meta["speed"] = mobkit.ground_speed(stride, share, cyc)
    a.meta["step_l"] = 0.0
    a.meta["step_r"] = cyc * 0.5
    return a


def run(G):
    R = RUN
    cyc, share, stride, lift, crouch = R["cycle"], R["stance"], R["stride"], R["lift"], R["crouch"]
    phases = {"leg_l": 0.0, "leg_r": 0.5}
    sw = look(G)["swing"]

    def frame(t):
        p = Pose()
        w = 2.0 * math.pi * t / cyc
        mid = 2.0 * math.pi * share / 2.0           # lowest in the middle of each stance, highest in the air
        p.pos("body", 1.6 * math.sin(w), -crouch - 2.0 * math.cos(2.0 * (w - mid)), -2.0)
        p.rot("body", 8.0, -7.0 * math.sin(w), -2.0 * math.sin(w))
        p.rot("chest", 9.0 + 1.5 * math.cos(2.0 * (w - mid)), 6.0 * math.sin(w), 2.5 * math.sin(w))
        p.rot("head", -14.0 - 1.5 * math.cos(2.0 * (w - mid)), 1.0 * math.sin(w), -1.0 * math.sin(w))
        p.rot("jaw", 6.0 + 3.0 * math.cos(2.0 * (w - mid)))
        mobkit.gait(G.legs, p, t, cyc, phases, share, stride, lift)
        mats = G.mats(p)
        for side in ("l", "r"):
            s = -math.sin(w) * sgn(side)
            a_ = sw[side]
            hold(G, p, side, (0.0, (5.0 + 3.0 * s) * a_, (-3.0 - 9.0 * s) * a_), mats)
        return finish(G, p)

    a = record(G, "run", cyc, frame, warm=2)
    a.meta["blocks_per_loop"] = stride / share / 16.0
    a.meta["speed"] = mobkit.ground_speed(stride, share, cyc)
    a.meta["step_l"] = 0.0
    a.meta["step_r"] = cyc * 0.5
    return a


def probe_point(G, frame, t, bone, rest_point):
    """Where a point carried by `bone` is at time t of an animation (blocks: its left, up, forward of its feet)."""
    G.reset()
    q = G.point(frame(t), bone, rest_point)
    G.reset()
    return [round(q[0] / 16.0, 3), round(q[1] / 16.0, 3), round(-q[2] / 16.0, 3)]


def striker(G, side):
    """What a blow with this arm lands with: (bone, its rest point): G2's shield, G3's club's far end (its tree's
    clump of roots: the look's CLUB_STRIKE units from its fist), else the fist."""
    how = look(G)["hold"][side]
    if how == "shield":
        return "shield", v3(G.J["shield"])
    if how == "club":
        A = G.arms[side]
        return "weapon", A.fist0 + A.grip0 * float(getattr(G.d, "CLUB_STRIKE", 25.0))
    return f"hand_{side}", G.arms[side].fist0


# ---------------------------------------------------------------- warning, hurt


def warn(G):
    """Its patience running out (QUEST_PLAN.md: about 4 seconds near it): it stares down at you, its eyes flare, it
    huffs (a breath in, then out hard through its nose, its jaw dropping), and stamps its right foot."""
    L = 2.4
    flare, huff, stamp = 0.35, 0.85, 1.45
    sw = look(G)["swing"]

    def frame(t):
        p = Pose()
        stare = k(t, [(0.0, 0.0), (0.3, 1.0, "out"), (2.0, 1.0), (L, 0.0)])
        inhale = k(t, [(0.0, 0.0), (0.45, 0.0), (0.75, 1.0), (huff, 1.0), (1.05, 0.0, "out")])
        exhale = k(t, [(0.0, 0.0), (0.75, 0.0), (huff + 0.03, 1.0, "in"), (1.15, 0.2), (1.5, 0.0)])
        lift = k(t, [(0.0, 0.0), (1.05, 0.0), (1.3, 1.0, "out"), (stamp, 0.0, "in"), (L, 0.0)])
        jolt = k(t, [(0.0, 0.0), (stamp, 0.0), (stamp + 0.06, 1.0, "in"), (1.85, 0.0)])
        tense = k(t, [(0.0, 0.0), (0.4, 1.0), (2.0, 1.0), (L, 0.0)])
        p.pos("body", -1.6 * lift, -0.8 * stare - 2.0 * jolt, 0.0)
        p.rot("body", 2.0 * stare + 2.0 * jolt, 0.0, 2.0 * lift)
        p.rot("chest", 3.0 * stare - 5.0 * inhale + 7.0 * exhale + 3.0 * jolt, 0.0, -1.5 * lift)
        p.rot("head", 9.0 * stare - 6.0 * inhale + 4.0 * exhale - 3.0 * jolt, 0.0, 0.0)
        p.rot("jaw", 2.0 + 12.0 * exhale + 6.0 * jolt)
        eyes(p, flare=1.0 + 0.55 * k(t, [(0.0, 0.0), (0.15, 0.0), (flare, 1.0, "out"), (0.8, 0.55), (2.0, 0.5),
                                          (L, 0.0)]))
        G.plant(p, {"leg_r": (0.0, 11.0 * lift, -3.0 * lift)})
        mats = G.mats(p)
        for side in ("l", "r"):
            s = sgn(side)
            a_ = sw[side] if look(G)["hold"][side] == "free" else 0.25 * sw[side]
            extra = v3(1.0, -2.5, -3.0) * stare if look(G)["hold"][side] == "shield" else v3(0.0, 0.0, 0.0)
            hold(G, p, side, v3(s * 3.0 * tense * a_, (1.5 * tense + 1.5 * inhale) * a_, -3.0 * tense * a_) + extra,
                 mats)
        return finish(G, p)

    a = record(G, "warn", L, frame, loop=False)
    a.meta.update(flare=flare, huff=huff, stamp=stamp)
    return a


def hurt(G):
    L = 0.5

    def frame(t):
        p = Pose()
        j = k(t, [(0.0, 0.0), (0.08, 1.0, "out"), (L, 0.0)])
        p.pos("body", 0.0, -1.0 * j, 1.0 * j)
        p.rot("chest", -7.0 * j, 6.0 * j, -2.0 * j)
        p.rot("head", -10.0 * j, -8.0 * j, 3.0 * j)
        p.rot("jaw", 2.0 + 12.0 * j)
        eyes(p, squint=1.0 - 0.6 * j)
        G.plant(p)
        mats = G.mats(p)
        for side in ("l", "r"):
            hold(G, p, side, (sgn(side) * 2.0 * j, 2.0 * j, 1.0 * j), mats)
        return finish(G, p)

    return record(G, "hurt", L, frame, loop=False)


# ---------------------------------------------------------------- blows


def arm_keys(G, p, mats, side, keys, t):
    """An arm moved along keys [(time, wrist, pole, ease)], each wrist and pole given as ("rest", offset) (its look's
    rest wrist on the chest, moved), ("chest", point) (a point in the chest's rest frame) or ("world", point), poles
    the same way as directions; between two keys it moves straight, eased as the later key says."""
    def at(spec, what):
        kind, v = spec
        if what == "wrist":
            if kind == "rest":
                return G.chest_point(p, G.arms[side].W0 + rest_out(G, side) + v3(v), mats)
            return G.chest_point(p, v, mats) if kind == "chest" else v3(v)
        if kind == "rest":
            return G.rest_pole(p, side, mats)
        return G.chest_dir(p, v, mats) if kind == "chest" else unit(v3(v))

    if t <= keys[0][0]:
        a = b = keys[0]
        u = 0.0
    elif t >= keys[-1][0]:
        a = b = keys[-1]
        u = 0.0
    else:
        for a, b in zip(keys[:-1], keys[1:]):
            if t <= b[0]:
                break
        u = (t - a[0]) / (b[0] - a[0])
        ease = b[3] if len(b) > 3 else "smooth"
        u = {"in": u * u, "out": 1.0 - (1.0 - u) ** 2, "linear": u}.get(ease, u * u * (3.0 - 2.0 * u))
    w = at(a[1], "wrist") * (1.0 - u) + at(b[1], "wrist") * u
    pole = unit(at(a[2], "pole") * (1.0 - u) + at(b[2], "pole") * u)
    return w, pole


def smash(G):
    """A big overhead blow: its arm (G1's left fist, G2's shield on its left arm, G3's club in its right fist) raised
    high behind its head while it rears back, a moment's tremble, then brought down hard in front of it as it bends
    into the blow (the hit), and back."""
    L = 2.0
    hit = 1.25
    side = look(G)["smash"]
    s = sgn(side)
    other = "r" if side == "l" else "l"
    club = look(G)["hold"][side] == "club"
    keys = [(0.0, ("rest", (0.0, 0.0, 0.0)), ("rest", None)),
            (0.35, ("chest", (s * 26.0, 52.0, -6.0)), ("chest", (s * 1.0, -0.6, 0.2)), "smooth"),
            (0.8, ("chest", (s * 17.0, 77.0, 7.0)), ("chest", (s * 1.0, 0.1, -0.7)), "out"),
            (1.0, ("chest", (s * 16.0, 78.0, 8.0)), ("chest", (s * 1.0, 0.1, -0.7))),
            (1.13, ("chest", (s * 21.0, 66.0, -24.0)), ("chest", (s * 1.0, 0.3, 0.1)), "in"),
            (hit, ("world", (s * 13.0, 20.0, -28.0) if not club else (s * 14.0, 30.0, -35.0)),
             ("world", (s * 1.0, 0.4, 0.2)), "linear"),
            (1.45, ("world", (s * 11.0, 21.0, -26.0) if not club else (s * 12.0, 31.0, -33.0)),
             ("world", (s * 1.0, 0.4, 0.2))),
            (1.72, ("chest", (s * 22.0, 30.0, -14.0) if not club else (s * 24.0, 47.0, -26.0)),
             ("chest", (s * 1.0, -0.2, 0.3))),
            (L, ("rest", (0.0, 0.0, 0.0)), ("rest", None))]

    def frame(t):
        p = Pose()
        up = k(t, [(0.0, 0.0), (0.8, 1.0, "out"), (1.0, 1.0), (hit, 0.0, "in"), (L, 0.0)])
        down = k(t, [(0.0, 0.0), (1.0, 0.0), (hit, 1.0, "in"), (1.45, 1.0), (L, 0.0)])
        shake = math.sin(t * 60.0) * bump(t, 0.75, 0.85, 0.95, 1.02)
        p.pos("body", 0.0, 1.0 * up - 5.0 * down, 1.5 * up - 2.0 * down)
        p.rot("body", -3.0 * up + 5.0 * down, 0.0, 0.0)
        p.rot("chest", -11.0 * up + 17.0 * down + 0.8 * shake, -s * 12.0 * up + s * 10.0 * down, s * 3.0 * up)
        p.rot("head", 4.0 * up + 6.0 * down, -s * 8.0 * up, -s * 5.0 * up)   # (away from its raised arm)
        p.rot("jaw", 2.0 + 14.0 * up * bump(t, 0.4, 0.75, 1.0, 1.15) + 18.0 * down)
        eyes(p, flare=1.0 + 0.3 * max(up, down))
        step = k(t, [(0.0, 0.0), (1.0, 0.0), (hit, 1.0), (1.45, 1.0), (L, 0.0)])
        G.plant(p, {f"leg_{side}": (0.0, 3.0 * bump(t, 0.95, 1.08, 1.08, hit), -6.0 * step)})
        mats = G.mats(p)
        w, pole = arm_keys(G, p, mats, side, keys, t)
        grip = None
        if club:   # the club pointing back over its shoulder, then down in front of it, its end to the ground
            g_up = G.chest_dir(p, (s * 0.15, 0.55, 0.85), mats)
            g_dn = unit(v3(0.0, -0.6, -0.8))
            g_rest = G.chest_dir(p, G.arms[side].grip0, mats)
            g_raise = unit(g_rest * (1.0 - up) + g_up * up)
            g_top = G.chest_dir(p, (s * 0.1, 1.0, -0.15), mats)    # (it swings over the top, never down through it)
            if down < 0.5:
                grip = unit(g_raise * (1.0 - 2.0 * down) + g_top * 2.0 * down)
            else:
                grip = unit(g_top * (2.0 - 2.0 * down) + g_dn * (2.0 * down - 1.0))
            club_arm(G, p, side, w, pole, grip, mats, clear=0.3)
        else:
            if look(G)["hold"][side] != "free":
                grip = G.chest_dir(p, G.arms[side].grip0, mats)
            G.arm(p, side, w, pole, grip=grip, mats=mats)
        hold(G, p, other, (-s * 2.0 * up, 1.5 * up, 2.0 * up - 2.0 * down), mats)
        return finish(G, p)

    a = record(G, "smash", L, frame, loop=False)
    a.meta.update(hit=hit, side="left" if side == "l" else "right",
                  hit_point=probe_point(G, frame, hit, *striker(G, side)))
    return a


def sweep(G):
    """A backhand with its left arm (G1's and G3's fist, G2's shield): wound up across in front of its right hip as it
    turns away, then swung out across in front of it to its left, the back of its fist (or the shield) leading (the
    hit), following through, and back."""
    L = 1.6
    hit = 0.8
    side = "l"
    wind_at = (-2.0, 27.0, -34.0) if look(G)["hold"][side] == "shield" else (12.0, 41.0, -27.0)
    keys = [(0.0, ("rest", (0.0, 0.0, 0.0)), ("rest", None)),
            (0.22, ("chest", (19.0, 38.0, -23.0)), ("chest", (1.0, -0.2, -0.3)), "smooth"),
            (0.5, ("chest", wind_at), ("chest", (1.0, -0.3, -0.6)), "out"),
            (0.66, ("chest", (16.0, 43.0, -26.0)), ("chest", (0.8, -0.2, 0.2)), "in"),
            (hit, ("chest", (27.0, 39.0, -14.0)), ("chest", (0.1, 0.6, 0.8)), "linear"),
            (0.98, ("chest", (28.0, 36.0, 3.0)), ("chest", (-0.2, 0.6, 0.8)), "out"),
            (L, ("rest", (0.0, 0.0, 0.0)), ("rest", None))]

    def frame(t):
        p = Pose()
        wind = k(t, [(0.0, 0.0), (0.5, 1.0, "out"), (0.62, 0.0, "in"), (L, 0.0)])
        thru = k(t, [(0.0, 0.0), (0.55, 0.0), (hit, 0.8, "in"), (1.0, 1.0, "out"), (1.2, 1.0), (L, 0.0)])
        low = k(t, [(0.0, 0.0), (0.5, 1.0), (1.0, 1.0), (L, 0.0)])
        p.pos("body", -1.0 * thru, -2.5 * low, 0.0)
        p.rot("body", 3.0 * low, 7.0 * wind - 9.0 * thru, 0.0)
        p.rot("chest", 5.0 * low, 24.0 * wind - 22.0 * thru, -3.0 * thru)
        p.rot("head", 4.0 * low - 12.0 * wind, -5.0 * wind - 6.0 * thru, -4.0 * thru)   # (chin up, then away from its arm)
        p.rot("jaw", 2.0 + 10.0 * bump(t, 0.6, hit, hit, 1.1))
        eyes(p, flare=1.0 + 0.25 * low)
        G.plant(p)
        mats = G.mats(p)
        w, pole = arm_keys(G, p, mats, side, keys, t)
        grip = None if look(G)["hold"][side] == "free" else G.chest_dir(p, G.arms[side].grip0, mats)
        G.arm(p, side, w, pole, grip=grip, mats=mats)
        hold(G, p, "r", (-2.0 * thru, 1.0 * low, 3.0 * thru), mats)
        return finish(G, p)

    a = record(G, "sweep", L, frame, loop=False)
    a.meta.update(hit=hit, side="left", hit_point=probe_point(G, frame, hit, *striker(G, side)))
    return a


def stomp(G):
    """It lifts its right knee high, its arms rising out for balance, and stamps its foot down with all its weight, its
    arms swinging down with it (the hit), and steps back."""
    L = 1.8
    hit = 1.05
    sw = look(G)["swing"]

    def frame(t):
        p = Pose()
        lift = k(t, [(0.0, 0.0), (0.7, 1.0, "out"), (0.85, 1.0), (hit, 0.0, "in"), (L, 0.0)])
        down = k(t, [(0.0, 0.0), (0.85, 0.0), (hit, 1.0, "in"), (1.3, 0.8), (L, 0.0)])
        fwd = k(t, [(0.0, 0.0), (0.7, 0.5), (hit, 1.0), (1.35, 1.0), (1.7, 0.0)])
        back = bump(t, 1.35, 1.5, 1.5, 1.7)
        p.pos("body", 2.6 * lift, 1.0 * lift - 6.5 * down, -1.0 * down)
        p.rot("body", -3.0 * lift + 6.0 * down, 0.0, 3.0 * lift)
        p.rot("chest", -5.0 * lift + 10.0 * down, -4.0 * lift, -2.0 * lift)
        p.rot("head", -4.0 * lift + 5.0 * down, 0.0, 0.0)
        p.rot("jaw", 2.0 + 16.0 * down)
        eyes(p, flare=1.0 + 0.3 * max(lift, down))
        G.plant(p, {"leg_r": (0.0, 16.0 * lift + 3.0 * back, -6.0 * fwd)})
        mats = G.mats(p)
        for side in ("l", "r"):
            s = sgn(side)
            if look(G)["hold"][side] == "free":
                up = (s * 9.0 * lift + s * 3.0 * down, 9.0 * lift - 4.0 * down, -2.0 * lift - 6.0 * down)
            else:
                a_ = sw[side]
                up = (s * 3.0 * lift * a_, (4.0 * lift - 2.0 * down) * a_, -3.0 * down * a_)
            hold(G, p, side, up, mats)
        return finish(G, p)

    a = record(G, "stomp", L, frame, loop=False)
    a.meta.update(hit=hit, hit_point=probe_point(G, frame, hit, "foot_r", v3(G.J["sole_r"])))
    return a


# ---------------------------------------------------------------- the throw


def path(t, keys):
    """A point along keys [(time, point, ease)] (Bedrock points, already where they are this frame), moving straight
    between two keys, eased as the later key says."""
    if t <= keys[0][0]:
        return v3(keys[0][1])
    for a, b in zip(keys[:-1], keys[1:]):
        if t <= b[0]:
            u = (t - a[0]) / (b[0] - a[0])
            ease = b[2] if len(b) > 2 else "smooth"
            u = {"in": u * u, "out": 1.0 - (1.0 - u) ** 2, "linear": u}.get(ease, u * u * (3.0 - 2.0 * u))
            return v3(a[1]) * (1.0 - u) + v3(b[1]) * u
    return v3(keys[-1][1])


def wrist_for(G, p, side, fist, mats):
    """The wrist for a fist at `fist`: a fist's length back towards its shoulder."""
    S = G.chest_point(p, G.arms[side].S0, mats)
    return v3(fist) - unit(v3(fist) - S) * 4.5


def hold_from(G, p, side, c, reach, mats):
    """Where a fist holding something at `c` goes: between it and its shoulder, `reach` from its middle (under it
    when it's held up, over it when it's on the ground)."""
    S = G.chest_point(p, G.arms[side].S0, mats)
    return v3(c) - unit(v3(c) - S) * reach


BOULDER_R = 8.0            # G1's boulder (its design's BOULDER_R)


def throw(G):
    """G1: the boulder heaved off its shoulder in both hands, lifted high behind its head as it rears back and steps
    in, hurled (it leaves its hands at "release": the game throws the real one from there, the model's is hidden), then
    a new one torn out of the ground beside its right foot and shouldered again. G2, G3: a rock torn out of the ground
    beside its left foot with its left hand, lifted back over its shoulder and hurled."""
    if look(G)["throw"] == "shoulder":
        return _throw_boulder(G)
    return _throw_rock(G)


def _throw_boulder(G):
    L = 4.6
    release = 1.55
    J = G.J
    rest = v3(J["boulder"])                         # its middle on the shoulder, at rest (the chest's frame)
    fist_rest = G.arms["r"].fist0 - rest            # where its right fist holds it (from its middle), at rest
    ground = v3(-18.0, BOULDER_R, -25.0)             # where the new one is torn out: by its right foot
    R = BOULDER_R

    def keys(p, mats):
        on_chest = lambda q: G.chest_point(p, q, mats)   # noqa: E731
        return [(0.0, on_chest(rest)),
                (0.5, on_chest(rest + v3(-1.5, 4.0, -1.5))),                       # heaved up off the shoulder
                (1.1, on_chest(v3(-3.0, 86.0, 15.0)), "out"),                       # high behind its head
                (1.2, on_chest(v3(-3.0, 87.0, 16.0))),
                (1.42, v3(-3.0, 85.0, -12.0), "in"),                               # hurled forward...
                (release, v3(-2.0, 86.0, -30.0), "linear"),                        # ...leaving its hands
                (2.4, ground + v3(0.0, 30.0, 0.0)),                               # (hidden: where the new one
                (2.6, ground),                                                     # will come from)
                (2.95, ground + v3(0.0, 1.0, 0.0)),                               # torn out
                (3.35, on_chest(v3(-22.0, 42.0, -18.0)), "out"),                   # hoisted out in front of it,
                (3.8, on_chest(v3(-27.0, 62.0, -2.0))),                            # up its right side
                (4.15, on_chest(rest + v3(-1.5, 4.0, -1.5)), "smooth"),            # and onto its shoulder
                (4.5, on_chest(rest)),
                (L, on_chest(rest))]

    def frame(t):
        p = Pose()
        lift = k(t, [(0.0, 0.0), (0.5, 0.3), (1.1, 1.0, "out"), (1.2, 1.0), (release, 0.0, "in"), (L, 0.0)])
        hurl = k(t, [(0.0, 0.0), (1.2, 0.0), (release, 1.0, "in"), (1.85, 0.7), (2.2, 0.0)])
        bend = k(t, [(0.0, 0.0), (2.0, 0.0), (2.5, 1.0), (3.0, 1.0), (3.6, 0.0, "smooth"), (L, 0.0)])
        tear = bump(t, 2.6, 2.7, 2.85, 2.95)
        step = k(t, [(0.0, 0.0), (0.9, 0.0), (1.25, 1.0), (2.0, 1.0), (2.4, 0.0), (L, 0.0)])
        p.pos("body", 0.0, 0.8 * lift - 3.0 * hurl - 9.0 * bend, 2.0 * lift - 2.0 * hurl)
        p.rot("body", -4.0 * lift + 8.0 * hurl + 8.0 * bend, -6.0 * lift + 8.0 * hurl - 6.0 * bend, 0.0)
        p.rot("chest", -12.0 * lift + 18.0 * hurl + 26.0 * bend + 1.5 * math.sin(t * 50.0) * tear,
              -8.0 * lift + 10.0 * hurl - 10.0 * bend, 0.0)
        p.rot("head", 6.0 * lift + 2.0 * hurl - 6.0 * bend, 0.0, 0.0)   # (its head down under its arms)
        p.rot("jaw", 2.0 + 20.0 * hurl + 10.0 * tear)
        eyes(p, flare=1.0 + 0.35 * max(lift, hurl))
        G.plant(p, {"leg_l": (0.0, 4.0 * bump(t, 0.9, 1.07, 1.07, 1.25) + 3.0 * bump(t, 2.0, 2.2, 2.2, 2.4),
                              -8.0 * step)})
        mats = G.mats(p)
        c = path(t, keys(p, mats))
        X = G.chest_dir(p, (1.0, 0.0, 0.0), mats)        # its left
        dn = v3(0.0, -2.0, 0.0)
        # The right fist: on the shoulder as at rest; carrying, on the boulder's right side (the left fist on its
        # left side, once it's up over its head); after the throw both fists swing down and out to its sides, the
        # right one down to the new boulder (over it on the ground), carrying it up its side and onto its shoulder.
        carry = k(t, [(0.0, 0.0), (0.25, 0.0), (0.6, 1.0), (3.85, 1.0), (4.2, 0.0)])
        both = k(t, [(0.0, 0.0), (0.6, 0.0), (0.7, 1.0), (release, 1.0), (2.1, 0.0)])
        on_shoulder = c + G.chest_dir(p, fist_rest, mats) * float(np.linalg.norm(fist_rest))
        side_r = c - X * (R + 9.0) + dn
        if t <= release:
            fist_r = on_shoulder * (1.0 - carry) + side_r * carry
        elif t < 2.6:
            c_rel = path(release, keys(p, mats))
            fist_r = path(t, [(release, c_rel - X * (R + 9.0) + dn),
                              (1.8, G.chest_point(p, v3(-32.0, 60.0, -18.0), mats)),
                              (2.05, G.chest_point(p, v3(-24.0, 36.0, -16.0), mats), "out"),
                              (2.6, hold_from(G, p, "r", ground, R + 2.5, mats))])
        else:
            over = hold_from(G, p, "r", c, R + 2.5 - 1.5 * tear, mats)
            grip = k(t, [(0.0, 0.0), (3.0, 0.0), (3.35, 1.0)])
            fist_r = over * (1.0 - grip) + on_shoulder * grip
        swing_pole = k(t, [(0.0, 0.0), (0.3, 0.0), (0.7, 1.0), (3.85, 1.0), (4.2, 0.0)])
        pole_r = unit(G.rest_pole(p, "r", mats) * (1.0 - swing_pole)
                      + G.chest_dir(p, (-1.0, -0.3, 0.3), mats) * swing_pole)
        # (its fist turned on the boulder as it rests there, until it takes its weight to heave it up)
        grip_r = G.chest_dir(p, G.arms["r"].grip0, mats) if t < 0.45 or t > 3.9 else None
        # (while it only steadies the boulder on its shoulder its arm keeps its rest pose, moved with the boulder)
        if t < 1.0:
            lifted = path(t, [(0.0, rest), (0.5, rest + v3(-1.5, 4.0, -1.5)), (1.0, rest + v3(-1.5, 4.0, -1.5))])
        elif t > 3.8:
            lifted = path(t, [(3.8, rest + v3(-1.5, 4.0, -1.5)), (4.15, rest + v3(-1.5, 4.0, -1.5), "smooth"),
                              (4.5, rest), (L, rest)])
        else:
            lifted = rest
        w_rest = G.chest_point(p, G.arms["r"].W0 + (lifted - rest), mats)
        w_r = w_rest * (1.0 - carry) + wrist_for(G, p, "r", fist_r, mats) * carry
        G.arm(p, "r", w_r, pole_r, grip=grip_r, mats=mats)
        rest_l = G.rest_wrist(p, "l", mats) + G.chest_dir(p, (1.0, 0.0, 0.0), mats) * FREE_OUT
        if t <= release:
            fist_l = path(t, [(0.6, rest_l), (0.82, G.chest_point(p, v3(28.0, 58.0, -2.0), mats), "out"),
                              (0.97, c + X * (R + 9.0) + dn)])
            if t > 0.97:
                fist_l = c + X * (R + 9.0) + dn
        else:
            c_rel = path(release, keys(p, mats))
            fist_l = path(t, [(release, c_rel + X * (R + 9.0) + dn),
                              (2.05, G.chest_point(p, v3(24.0, 36.0, -14.0), mats), "out")])
        w_l = rest_l * (1.0 - both) + wrist_for(G, p, "l", fist_l, mats) * both
        pole_l = unit(G.rest_pole(p, "l", mats) * (1.0 - both) + G.chest_dir(p, (1.0, -0.2, 0.3), mats) * both)
        G.arm(p, "l", w_l, pole_l, mats=mats)
        mats = G.mats(p)
        G.place(p, "weapon", rest, c, mats)
        grow = k(t, [(0.0, 1.0), (release, 1.0), (release + 0.001, 0.0), (2.62, 0.0), (2.95, 1.0, "out")])
        if grow < 1.0:
            G.hide(p, "weapon", grow)
        if 2.4 < t < 3.4:
            on_floor(G, p, "weapon")
        return finish(G, p)

    a = record(G, "throw", L, frame, loop=False)
    ks = _probe_path(G, frame, release)
    a.meta.update(release=release, regrab=2.6, thrown_bone="weapon", **ks)
    return a


def bone_low(G, p, bone, mats=None):
    """The lowest point (units) of one bone's boxes in this pose (None if it's hidden)."""
    from kit.clipping import _box_frame
    mats = mats if mats is not None else G.mats(p)
    if abs(float(np.linalg.det(mats[bone][:3, :3]))) < 1e-9:
        return None
    return min(float(mobkit._grid(_box_frame(mats, c))[:, 1].min() * 16.0) for c in G.m[bone].cubes)


def on_floor(G, p, bone, gap=0.1):
    """A prop on the ground lifted so its lowest corner is just on the floor, never in it (a lumpy boulder's corners
    reach past its radius)."""
    mats = G.mats(p)
    low = bone_low(G, p, bone, mats)
    if low is not None and low < gap:
        ref = v3(G.m[bone].pivot)
        G.place(p, bone, ref, G.point(p, bone, ref, mats) + v3(0.0, gap - low, 0.0), mats)


def _throw_rock(G):
    L = 3.6
    release = 2.2
    rr = props.ROCK_R
    rest = v3(G.J["rock"])
    ground = v3(20.0, rr, -22.0)                      # where it tears the rock out: out by its left foot

    def frame(t):
        p = Pose()
        bend = k(t, [(0.0, 0.0), (0.6, 1.0), (0.95, 1.0), (1.4, 0.0, "smooth"), (L, 0.0)])
        tear = bump(t, 0.6, 0.7, 0.85, 0.95)
        lift = k(t, [(0.0, 0.0), (1.3, 0.0), (1.85, 1.0, "out"), (1.95, 1.0), (release, 0.0, "in"), (L, 0.0)])
        hurl = k(t, [(0.0, 0.0), (1.95, 0.0), (release, 1.0, "in"), (2.5, 0.7), (3.0, 0.0)])
        step = k(t, [(0.0, 0.0), (1.6, 0.0), (1.95, 1.0), (2.8, 1.0), (3.3, 0.0), (L, 0.0)])
        p.pos("body", 0.0, -10.0 * bend + 0.8 * lift - 3.0 * hurl, 1.5 * lift - 2.0 * hurl)
        p.rot("body", 8.0 * bend - 4.0 * lift + 8.0 * hurl, 6.0 * bend + 6.0 * lift - 8.0 * hurl, 0.0)
        p.rot("chest", 28.0 * bend + 1.5 * math.sin(t * 50.0) * tear - 12.0 * lift + 18.0 * hurl,
              8.0 * bend + 10.0 * lift - 12.0 * hurl, -2.0 * lift)
        p.rot("head", -8.0 * bend + 5.0 * lift + 2.0 * hurl, -10.0 * lift - 6.0 * hurl, -5.0 * lift)   # (away from its arm)
        p.rot("jaw", 2.0 + 10.0 * tear + 20.0 * hurl)
        eyes(p, flare=1.0 + 0.35 * max(lift, hurl))
        G.plant(p, {"leg_r": (0.0, 4.0 * bump(t, 1.6, 1.78, 1.78, 1.95) + 3.0 * bump(t, 2.8, 3.05, 3.05, 3.3),
                              -8.0 * step)})
        mats = G.mats(p)
        on_chest = lambda q: G.chest_point(p, q, mats)   # noqa: E731
        # (lifted out to its left and up its side, high behind its left shoulder, hurled over the shoulder: never
        # across its chest or over its head)
        c = path(t, [(0.0, ground + v3(0.0, 30.0, 0.0)), (0.6, ground), (0.95, ground + v3(0.0, 1.0, 0.0)),
                     (1.3, on_chest(v3(32.0, 44.0, -19.0)), "out"),
                     (1.6, on_chest(v3(34.0, 63.0, 0.0))),
                     (1.85, on_chest(v3(20.0, 77.0, 15.0)), "out"),
                     (1.95, on_chest(v3(20.0, 78.0, 16.0))),
                     (2.08, v3(24.0, 80.0, -10.0), "in"),
                     (release, v3(23.0, 80.0, -28.0), "linear"),
                     (2.45, on_chest(v3(31.0, 44.0, -26.0)), "out"),
                     (L, on_chest(v3(31.0, 44.0, -26.0)))])
        # the left fist on the rock (over it on the ground, under it overhead) from when it grabs it to the throw,
        # then following through after it
        on = k(t, [(0.0, 0.0), (0.55, 1.0), (2.45, 1.0), (3.1, 0.0)])
        fist = hold_from(G, p, "l", c, rig_fist_reach(rr), mats)
        rest_l = G.chest_point(p, G.arms["l"].W0 + rest_out(G, "l"), mats)
        w = rest_l * (1.0 - on) + wrist_for(G, p, "l", fist, mats) * on
        pole = unit(G.rest_pole(p, "l", mats) * (1.0 - on) + G.chest_dir(p, (1.0, 0.1, 0.2), mats) * on)
        G.arm(p, "l", w, pole, mats=mats)
        # (its other arm forward and out of the way as it bends: G2's spear clear of its chest)
        hold(G, p, "r", (-3.0 * lift - 5.0 * bend, 2.0 * lift, -4.0 * lift + 3.0 * hurl - 10.0 * bend), G.mats(p))
        mats = G.mats(p)
        G.place(p, "rock", rest, c, mats)
        if t < 1.2:
            on_floor(G, p, "rock")
        grow = k(t, [(0.0, 0.0), (0.58, 0.0), (0.9, 1.0, "out"), (release, 1.0), (release + 0.001, 0.0)])
        G.hide(p, "rock", grow)
        return finish(G, p, show=("rock",))

    a = record(G, "throw", L, frame, loop=False)
    ks = _probe_path(G, frame, release, bone="rock", rest=rest)
    a.meta.update(release=release, tear=0.7, thrown_bone="rock", **ks)
    return a


def rig_fist_reach(r):
    """How far a fist's middle is from the middle of the rock it holds (props.rock_in_fist)."""
    from . import rig as _rig
    return _rig.FIST / 2.0 + r - props.ROCK_GRIP


def _probe_path(G, frame, release, bone="weapon", rest=None):
    """Where the thrown boulder's middle is at its release (blocks: its left, up, forward of its feet) and which way it's
    going (a unit vector the same way), from the frames just before it lets go."""
    rest = v3(G.J["boulder"]) if rest is None else rest
    G.reset()
    pts = []
    for t in (release - 2.0 * STEP, release - STEP, release - 1e-4):
        p = frame(t)
        # (its scale is 1 until the release: the point it carries is where it is)
        p.get3(bone)[2] = [1.0, 1.0, 1.0]
        pts.append(G.point(p, bone, rest))
    c = pts[-1]
    v = unit(pts[-1] - pts[0])
    G.reset()
    return dict(release_point=[round(c[0] / 16.0, 3), round(c[1] / 16.0, 3), round(-c[2] / 16.0, 3)],
                release_direction=[round(v[0], 3), round(v[1], 3), round(-v[2], 3)])


# ---------------------------------------------------------------- death

KNEEL = {"leg": (-12.0, 0.0, 0.0), "shin": (98.0, 0.0, 0.0), "foot": (72.0, 0.0, 0.0)}   # its legs folded under it
TOPPLE = 78.0              # degrees its body falls forward over its knees
SETTLE_BONES = ("body", "chest", "head", "jaw", "leg_l", "shin_l", "foot_l", "leg_r", "shin_r", "foot_r",
                "kilt_fl", "kilt_fr", "kilt_bl", "kilt_br", "cloak", "back_spear", "pad_l", "pad_r")
L_DEATH = 3.8


def lowest(G, p, bones=SETTLE_BONES):
    """The lowest point (units) of these bones' boxes in this pose."""
    hidden = [b.name for b in G.m.bones if b.cubes and b.name not in bones]
    from rimekit import view as _V
    pts = _V.corners(G.m, p.as_geo(), hidden)
    return float(pts[:, 1].min())


def death(G):
    """A last roar as it rears back, its arms flung out; its knees give and it drops onto them (its legs folding under
    it); then it topples forward onto its face (its head turned to one side) and lies there, its arms out in front of
    it, and its eyes dim. G1's boulder rolls off its shoulder, G2's spear falls flat beside it and its shield lies flat
    on the ground; it stays down. Each frame it rests on the floor (its lowest point on it: nothing under it, nothing
    floating)."""
    L = L_DEATH
    roar, knees, thud = 0.25, 1.35, 2.35
    stand = {}
    pivot = {}

    def frame(t):
        p = Pose()
        rear = k(t, [(0.0, 0.0), (0.35, 1.0, "out"), (0.8, 0.8), (1.2, 0.0)])
        kneel = k(t, [(0.0, 0.0), (0.7, 0.0), (knees, 1.0, "in"), (L, 1.0)])
        fall = k(t, [(0.0, 0.0), (1.55, 0.0), (thud, 1.0, "in"), (2.47, 0.95), (2.62, 1.0, "smooth"), (L, 1.0)])
        p.pos("body", 0.0, 1.0 * rear, 1.5 * rear + 4.0 * kneel)
        p.rot("body", -6.0 * rear + 4.0 * kneel, 0.0, 0.0)
        p.rot("chest", -12.0 * rear + 10.0 * kneel - 4.0 * fall, 4.0 * rear, 0.0)
        p.rot("head", -24.0 * rear + 10.0 * kneel - 30.0 * fall, 60.0 * fall, -10.0 * fall)
        p.rot("jaw", 2.0 + 26.0 * rear * (1.0 - kneel) + 8.0 * kneel * (1.0 - fall) + 4.0 * fall)
        eyes(p, flare=1.0 + 0.4 * rear * (1.0 - kneel),
             squint=1.0 - 0.75 * k(t, [(0.0, 0.0), (2.3, 0.0), (3.4, 1.0), (L, 1.0)]))
        if t < 0.7 or not stand:
            G.plant(p)
            if t >= 0.65 or not stand:
                for s_ in ("l", "r"):
                    for part in ("leg", "shin", "foot"):
                        stand[f"{part}_{s_}"] = list(p.get3(f"{part}_{s_}")[0])
        else:
            for s_ in ("l", "r"):
                for part in ("leg", "shin", "foot"):
                    b = f"{part}_{s_}"
                    p.get3(b)[0] = [a * (1.0 - kneel) + c * kneel for a, c in zip(stand[b], KNEEL[part])]
        if kneel >= 1.0 and not pivot:
            mats = G.mats(p)
            kn = (G.point(p, "shin_l", G.J["knee_l"], mats) + G.point(p, "shin_r", G.J["knee_r"], mats)) / 2.0
            pivot["k"] = v3(0.0, kn[1], kn[2])
        if fall > 0.0 and pivot:
            ang = TOPPLE * fall
            Rg = np.array(_rx(ang))
            kg = np.array([-pivot["k"][0], pivot["k"][1], pivot["k"][2]]) / 16.0
            tr = kg - Rg @ kg
            p.rot("root", ang, 0.0, 0.0)
            p.pos("root", -tr[0] * 16.0, tr[1] * 16.0, tr[2] * 16.0)
            for s_ in ("l", "r"):
                p.rot(f"shin_{s_}", -ang, 0.0, 0.0)
        if t >= 0.7:                     # resting on the floor: its lowest point on it
            p.pos("root", 0.0, -lowest(G, p), 0.0)
        mats = G.mats(p)
        for side in ("l", "r"):
            s = sgn(side)
            fling = G.chest_point(p, v3(s * 30.0, 62.0, -8.0), mats)
            hang = G.chest_point(p, v3(s * 19.0, 32.0, -12.0), mats)
            ahead = G.chest_point(p, v3(s * 22.0, 76.0, -10.0), mats)
            ahead[1] = 6.0
            wts = (rear * (1.0 - kneel), kneel * (1.0 - fall), fall)
            rest_w = max(0.0, 1.0 - sum(wts))
            w = fling * wts[0] + hang * wts[1] + ahead * wts[2] + G.rest_wrist(p, side, mats) * rest_w
            w[1] = max(w[1], 5.0)
            pole = unit(G.chest_dir(p, (s * 1.0, -0.2, 0.5), mats) * (1.0 - rest_w) + G.rest_pole(p, side, mats) * rest_w)
            hand = look(G)["hold"][side]
            grip = G.chest_dir(p, G.arms[side].grip0, mats) if hand in ("spear", "club", "boulder") else None
            if hand == "club":         # (G3's club off the ground as it kneels, laid flat beside it as it falls)
                grip = unit(grip * (1.0 - fall) + v3(s * 0.45, -0.04, -1.0) * fall)
                club_arm(G, p, side, w, pole, grip, mats, clear=0.3)
            else:
                G.arm(p, side, w, pole, grip=grip, mats=mats)
            # (a fist or forearm that would reach into the ground: the wrist lifted out of it)
            m2 = G.mats(p)
            low = min(bone_low(G, p, f"hand_{side}", m2), bone_low(G, p, f"fore_{side}", m2))
            if low < 0.2:
                w[1] += 0.2 - low
                G.arm(p, side, w, pole, grip=grip, mats=mats)
        _drop_props(G, p, t, fall)
        if look(G)["hold"]["r"] == "club" and fall > 0.0:
            on_floor(G, p, "weapon")
        return finish(G, p)

    a = record(G, "death", L, frame, loop=False)
    a.meta.update(roar=roar, knees=knees, thud=thud)
    return a


def _rx(deg):
    """The GeckoLib rotation (3x3) of a Bedrock x turn of `deg` degrees."""
    from kit import geo
    return geo.gecko_rotation(deg, 0.0, 0.0)[:3, :3]


def _drop_props(G, p, t, fall):
    """What it carries comes down with it: G1's boulder rolls off its shoulder onto the ground beside it, G2's spear
    falls flat beside it."""
    if look(G)["throw"] == "shoulder":
        rest = v3(G.J["boulder"])
        mats = G.mats(p)
        now = G.point(p, "weapon", rest, mats)
        off = k(t, [(0.0, 0.0), (1.6, 0.0), (2.3, 1.0, "in"), (L_DEATH, 1.0)])
        if off > 0.0:
            ground = v3(-34.0, BOULDER_R, -30.0)
            arc = v3(0.0, 10.0 * math.sin(math.pi * min(1.0, off)), 0.0)
            G.place(p, "weapon", rest, now * (1.0 - off) + (ground + arc) * off, mats)
            on_floor(G, p, "weapon")
    if look(G)["hold"].get("r") == "spear":
        drop = k(t, [(0.0, 0.0), (0.4, 0.0), (1.3, 1.0, "in"), (L_DEATH, 1.0)])
        if drop > 0.0:
            mats = G.mats(p)
            butt0 = v3(G.arms["r"].fist0[0], 1.0, G.arms["r"].fist0[2])
            up = v3(0.0, 1.0, 0.0)
            down = unit(v3(-0.35, 0.0, -1.0))
            d = unit(up * (1.0 - drop) + down * drop + v3(-0.2, 0.0, 0.0) * math.sin(math.pi * drop))
            G.aim(p, "weapon", up, d, mats)
            mats = G.mats(p)
            butt = v3(-22.0, 2.5, 6.0)
            G.place(p, "weapon", butt0, G.point(p, "weapon", butt0, mats) * (1.0 - drop) + butt * drop, mats)
            on_floor(G, p, "weapon")
    if G.has("shield"):
        lie = k(t, [(0.0, 0.0), (2.0, 0.0), (2.6, 1.0), (L_DEATH, 1.0)])
        if lie > 0.0:
            mats = G.mats(p)
            n0 = unit(v3(0.32, 0.06, -1.0))
            G.aim(p, "shield", n0, unit(G.direction(p, "shield", n0, mats) * (1.0 - lie) + v3(0.0, 1.0, 0.0) * lie),
                  mats)
            on_floor(G, p, "shield")



def animations(G):
    """Every animation the game plays. (No death: it dies the way Minecraft's mobs do, the user's choice of November
    18, 2026; `death` is kept here, unused.)"""
    return {"idle": idle(G), "walk": walk(G), "run": run(G), "warn": warn(G), "smash": smash(G), "sweep": sweep(G),
            "stomp": stomp(G), "throw": throw(G), "hurt": hurt(G)}
