"""
The Cinder Hermit on its skeleton (M2A to M2J, no G or H: one model per shell, the crab the same in all), and its
animations (animation.hermit.*; its hide and peek fitted to each shell, so each model has its own animation file):

  idle   (loop)  its eyes twitching on their stalks, its claws flexing, its shell rocking a little
  walk   (loop)  scuttling ahead on its four walking legs, the shell swaying on its back
  snap   (once)  the big magma claw raised high, then slammed forward and snapped shut ("hit"); its antennae swept
                 back out of the claw's way
  pinch  (once)  two quick nips with the small picking claw ("hit", "hit2")
  hide   (once)  it folds its legs and claws in, curls up and backs into its shell through its mouth, and the shell
                 settles onto the ground (its last pose stays: nothing of it shows from any side)
  peek   (once)  from hiding: it stays folded in its shell; its big claw drops a little like a door, its eyes rise
                 over it, point out ahead and slide out through the mouth, pop up past the rim, then look left and
                 right; it stays there peeking (its last pose stays)
  come_out (once) from peeking: its eyes go back in the way they came, its claw closes, it climbs out ("out"), its
                 shell lifted back up, and unfolds
  death  (once)  its legs buckle, it slumps out of its shell and goes still

Its walking legs are planted with the golem kit's IK.
"""
import math

import numpy as np

from firedesigns import f13_hermit as H, m02_hermit as DESIGN
from firemobs import common as C
from firemobs.common import Pose, k, wave
from kit import geo, ik
from rimekit import mobkit
from rimekit.mobkit import Leg
from rimekit.shapes import unit

ID = "hermit"
NAME = "Cinder Hermit"
PREVIEW_SET = "mobs"
IDLE = "idle"
K = 1.0
VIEW = (35.0, 16.0)
MODELS = [(f"{DESIGN.ID}{v['letter'].lower()}", v) for v in DESIGN.VARIANTS]
FREE = DESIGN.FREE

_J = H.joints()
BONES = [("root", (0.0, 0.0, 0.0), None), ("shell", tuple(_J["shell"]), "root"), ("body", tuple(_J["body"]), "root"),
         ("eye_l", tuple(_J["eye_l"]), "body"), ("eye_r", tuple(_J["eye_r"]), "body"),
         ("antenna_l", tuple(_J["antenna_l"]), "body"), ("antenna_r", tuple(_J["antenna_r"]), "body"),
         ("claw_r", tuple(_J["claw_r"]), "body"), ("fore_r", tuple(_J["fore_r"]), "claw_r"),
         ("pincer_r", tuple(_J["pincer_r"]), "fore_r"), ("finger_r", tuple(_J["finger_r"]), "pincer_r"),
         ("claw_l", tuple(_J["claw_l"]), "body"), ("fore_l", tuple(_J["fore_l"]), "claw_l"),
         ("pincer_l", tuple(_J["pincer_l"]), "fore_l")]
LEGS = []
for _sign, _side in ((1.0, "l"), (-1.0, "r")):
    for _name, _hz, _fz, _fx in H.LEGS:
        _hip, _knee, _ankle, _foot = H.leg_points(_sign, _hz, _fz, _fx)
        _n = [f"leg{_name}_{_side}", f"knee{_name}_{_side}", f"ankle{_name}_{_side}"]
        BONES += [(_n[0], tuple(_hip), "body"), (_n[1], tuple(_knee), _n[0]), (_n[2], tuple(_ankle), _n[1])]
        _fwd = unit(np.array([_foot[0] - _ankle[0], 0.0, _foot[2] - _ankle[2]]))
        LEGS.append(Leg(f"leg{_name}_{_side}", None, None, (_foot[0], 0.0, _foot[2]), forward=tuple(_fwd), joints=[
            ik.Joint(_n[0], (0, 1, 2), lo=(-60, -60, -60), hi=(60, 60, 60), prefer=(0, 0, 0), weight=0.01),
            ik.Joint(_n[1], (0, 2), lo=(-90, -90), hi=(90, 90), prefer=(0, 0), weight=0.005),
            ik.Joint(_n[2], (0, 2), lo=(-90, -90), hi=(90, 90), prefer=(0, 0), weight=0.005),
        ]))


def clip_group(bone):
    if bone.startswith(("claw_r", "fore_r", "pincer_r", "finger_r")):
        return "bigclaw"
    if bone.startswith(("claw_l", "fore_l", "pincer_l")):
        return "smallclaw"
    if bone.startswith("eye"):
        return "eyes"
    if bone.startswith("antenna"):
        return "antennae"
    return None


# (each pair both ways: a thin antenna's few points can go right through a claw whose points never land in it; the
# user saw its snap go through its antennae, October 28)
CLIP_PAIRS = tuple(pair for a, b in (("bigclaw", "eyes"), ("smallclaw", "eyes"), ("bigclaw", "smallclaw"),
                                     ("bigclaw", "antennae"), ("smallclaw", "antennae"), ("antennae", "eyes"))
                   for pair in ((a, b), (b, a)))


# Its hide, peek and come_out (each model's own): its eyes against its claws and antennae are model_check's, which
# counts only what can be seen (the skull's eyes pass its folded claw deep inside the bone, out of sight).
MODEL_CLIP_PAIRS = tuple(pair for pair in CLIP_PAIRS if "eyes" not in pair)


def make_rig(m):
    spec = type("Spec", (), dict(K=K, LEGS=LEGS))
    return mobkit.MobRig(m, spec)


def eyes(p, t, amp=1.0):
    for side, sx in (("l", 1.0), ("r", -1.0)):
        p.rot(f"eye_{side}", amp * 8.0 * wave(t, 1.7, 0.3 * sx), amp * 14.0 * wave(t, 2.3, 0.2 * sx), 0.0)


def idle(rig):
    L = 4.0

    def frame(t):
        p = Pose()
        p.pos("body", 0.0, 0.5 * wave(t, L / 2.0), 0.0)
        p.rot("shell", 0.0, 0.0, 1.2 * wave(t, L))
        eyes(p, t)
        flex = k(t, [(0.0, 0.0), (1.2, 0.0), (1.45, 1.0), (1.7, 0.0), (1.95, 1.0), (2.2, 0.0), (L, 0.0)])
        p.rot("finger_r", 22.0 * flex, 0.0, 0.0)
        p.rot("claw_r", -3.0 * wave(t, L), 0.0, 0.0)
        p.rot("pincer_l", 10.0 * wave(t, 0.9) * k(t, [(0.0, 0.0), (2.6, 0.0), (2.8, 1.0), (3.6, 1.0), (3.8, 0.0)]),
              0.0, 0.0)
        rig.plant(p)
        return p

    return C.record(rig, "idle", L, frame)


WALK = (1.0, 0.55, 16.0, 6.0)     # cycle, stance share, stride (units), lift


def walk(rig):
    cyc, share, stride, lift = WALK
    phases = {"lega_l": 0.0, "legb_r": 0.0, "lega_r": 0.5, "legb_l": 0.5}

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / cyc
        p.pos("body", 0.0, 0.8 * math.cos(2 * w), 0.0)
        p.pos("shell", 0.0, 0.8 * math.cos(2 * w + 0.4), 0.0)
        p.rot("shell", 0.0, 2.0 * math.sin(w), 2.5 * math.sin(w))
        eyes(p, t, 0.5)
        p.rot("claw_r", -4.0 * math.sin(w), 0.0, 0.0)
        p.rot("claw_l", 4.0 * math.sin(w), 0.0, 0.0)
        mobkit.gait(rig, p, t, cyc, phases, share, stride, lift)
        return p

    a = C.record(rig, "walk", cyc, frame, warm=2)
    a.meta["speed"] = mobkit.ground_speed(stride, share, cyc)
    return a


SNAP_ANTENNAE = (-70.0, 30.0)     # its snap: its antennae swept back (x) and out (z), clear of its rising claw (the
                                  # user, October 28: "his snaps still go through his tentacles")


def snap(rig):
    L = 1.3
    hit = 0.62

    def frame(t):
        p = Pose()
        lift = k(t, [(0.0, 0.0), (0.4, 1.0), (hit, 0.0, "in"), (L, 0.0)])
        smash = k(t, [(0.0, 0.0), (0.4, 0.0), (hit, 1.0, "in"), (0.85, 1.0), (L, 0.0)])
        gape = k(t, [(0.0, 0.0), (0.35, 1.0), (hit - 0.04, 1.0), (hit, 0.0, "in"), (L, 0.0)])
        p.rot("claw_r", -36.0 * lift + 2.0 * smash, 10.0 * lift - 8.0 * smash, 0.0)
        p.rot("fore_r", -24.0 * lift + 2.0 * smash, 0.0, 0.0)
        p.rot("pincer_r", -10.0 * lift - 6.0 * smash, 0.0, 0.0)
        p.rot("finger_r", 34.0 * gape, 0.0, 0.0)
        p.pos("body", 0.0, 1.5 * lift - 1.0 * smash, -2.0 * smash)
        p.rot("body", -4.0 * lift + 4.0 * smash, 0.0, 0.0)
        p.rot("shell", 0.0, 0.0, 2.0 * smash)
        sweep = k(t, [(0.0, 0.0), (0.25, 1.0, "out"), (0.9, 1.0), (L, 0.0)])
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"antenna_{side}", SNAP_ANTENNAE[0] * sweep, 0.0, -sx * SNAP_ANTENNAE[1] * sweep)
        eyes(p, t, 0.3)
        rig.plant(p)
        return p

    a = C.record(rig, "snap", L, frame, loop=False)
    a.meta["hit"] = hit
    return a


def pinch(rig):
    L = 1.1
    hit, hit2 = 0.35, 0.7

    def frame(t):
        p = Pose()
        out = k(t, [(0.0, 0.0), (0.2, 1.0), (0.9, 1.0), (L, 0.0)])
        nip = k(t, [(0.0, 0.0), (0.2, -1.0), (hit, 1.0, "in"), (0.5, -1.0), (hit2, 1.0, "in"), (0.9, 0.0), (L, 0.0)])
        p.rot("claw_l", -12.0 * out + 8.0 * max(0.0, nip), -10.0 * out, 0.0)
        p.rot("fore_l", -14.0 * out + 10.0 * max(0.0, nip), 0.0, 0.0)
        p.rot("pincer_l", -14.0 * nip, 0.0, 0.0)
        eyes(p, t, 0.4)
        rig.plant(p)
        return p

    a = C.record(rig, "pinch", L, frame, loop=False)
    a.meta.update(hit=hit, hit2=hit2)
    return a


# ---------------------------------------------------------------- hiding in its shell
#
# The user (October 27, 2026): "the sides of the hide animation have to be fixed, where the entire crab hides; peek as
# well, for each one". The crab is as wide as most of its shells, so it can't just back in: it folds its legs back
# along its body and its claws in against its face (the tightest folds, LEG_TUCK, BIG_TUCK, SMALL_TUCK, found by a
# search: legs within about 16 units of its middle line, the big claw within 41), draws itself in (curls up, a little
# smaller) and backs into the shell, which settles onto the ground. How far it backs in, how high, and how much it
# curls up are worked out for each shell (HIDE, by tools/fire/hermit_fit.py) so that nothing of it shows from any side
# once it's in (rays from every part of it, every way but down, all stopped by the shell), and nothing of it pokes out
# of the shell's sides or back on the way in; mobs.py checks it again on every build.

LEG_TUCK = {"a": (15.0, 110.0, -40.0, 90.0, 15.0), "b": (15.0, 110.0, -40.0, 90.0, 30.0)}  # hip x y z, knee z, ankle z
BIG_TUCK = (-20.0, -40.0, 40.0, 50.0, -60.0)     # claw_r x y z, fore_r x, pincer_r x: drawn in before its face
SMALL_TUCK = (-20.0, 0.0, -40.0, 25.0, -60.0)    # claw_l x y z, fore_l x, pincer_l x (clear of the big claw)
EYES_IN = (-70.0, 0.0, 0.6)           # the eyes folded back on their stalks (clear of the claws), drawn in by this share
ANTENNAE_IN = (-120.0, 60.0, 0.65)    # its antennae laid back over its head (x, z) and curled in by this share as it
                                      # folds up: when it peeks only its eyes show (the user, October 27: "I just want
                                      # to see his eyes")
# The peek (October 28, 2026, the user: "I need the eyes to pop out in a way where they don't clip through the top of
# the shell ... not just to peep out but also look left and right"). Like a real hermit crab's, its big claw is the
# shell's door: it drops a little (D degrees at its shoulder, D2 at its pincer, and the folded crab sinks Y units in its
# shell) to open a gap over it, its eyes unfold and point straight out ahead on
# their stalks (PEEK_AIM) at the gap's height (H units over where they fold, before S), slide out through the mouth (P
# units, before S; the stalks' roots stay inside), pop up once they're past the rim (leaning out R degrees, splayed
# apart, stretched to O: its eyes their full size again, though the rest of it is curled up small), then look one way
# and the other: both stalks swing LX units to a side (before S) and turn LK degrees that way,
# standing up and turning about the point F along them (0 their roots, 1 the eyes) where they leave the mouth, so they
# never sweep into its rim. The skull's eyes slide to its eye holes instead (E: the left eye's slide, the right mirrors it).
# Everything per shell is found by tools/fire/hermit_fit.py: the crab hidden but for its eyes, its eyes never into the
# shell anywhere you can see (only through its mouth's dark), never into its claws or antennae.
PEEK_AIM = 85.0                        # its eyes pointed out ahead on their stalks as they slide out (degrees)
PEEK_AIM_SCALE = 0.5                   # their size then (drawn in small to slip through the gap; they pop out to full
                                       # size once they're out)
PEEK_OUT_SIZE = 1.0                    # and once they're out, their size against the crab's (O, if the shell has no room
                                       # for that: the eyes keep at least PEEK_OUT_MIN of the curled-up crab's)
PEEK_OUT_MIN = 1.3
PEEK_SPREAD = 4.0                      # each eye slides this far out to its own side (units, before S): apart, never
                                       # together (the user, October 28: "separate the eyes")
PEEK_SPLAY = 30.0                      # once out, its stalks splayed apart (degrees)
PEEK_LOOK_LEAN = -0.5                  # looking to a side, its stalks lean by this share of LK (so the splayed eyes stay
                                       # about level as they turn)

# Per shell: B units it backs in, U units it rises (before the shell settles), S its size curled up; its peek's D, H,
# P (or the skull's E), R, F and LK (see above).
HIDE = {
    "bell": dict(B=39.0, U=-6.0, S=0.55, D=0.0, D2=0.0, Y=0.0, H=20.0, P=36.0, R=40.0, F=0.50, LK=40.0, LX=10.0, O=1.00),
    "brazier": dict(B=45.0, U=0.0, S=0.65, D=16.0, D2=0.0, Y=0.0, H=16.0, P=44.0, R=60.0, F=0.65, LK=40.0, LX=4.0, O=1.00),
    "skull": dict(B=42.0, U=6.0, S=0.60, D=0.0, D2=0.0, Y=0.0, H=0.0, E=(21.0, 21.3, -45.1), R=85.0, F=0.20, LK=30.0, LX=3.0, O=0.50),
    "urn": dict(B=45.0, U=0.0, S=0.65, D=0.0, D2=0.0, Y=0.0, H=20.0, P=42.0, R=50.0, F=0.65, LK=40.0, LX=10.0, O=1.00),
    "conch": dict(B=33.0, U=12.0, S=0.50, D=8.0, D2=0.0, Y=0.0, H=18.0, P=42.0, R=40.0, F=0.35, LK=40.0, LX=10.0, O=1.00),
    "geode": dict(B=39.0, U=0.0, S=0.55, D=0.0, D2=0.0, Y=0.0, H=20.0, P=42.0, R=60.0, F=0.65, LK=30.0, LX=7.0, O=1.00),
    "lantern": dict(B=48.0, U=0.0, S=0.60, D=0.0, D2=0.0, Y=0.0, H=20.0, P=46.0, R=60.0, F=0.20, LK=40.0, LX=10.0, O=1.00),
    "shard": dict(B=42.0, U=0.0, S=0.60, D=0.0, D2=0.0, Y=0.0, H=20.0, P=44.0, R=50.0, F=0.65, LK=30.0, LX=4.0, O=1.00),
}
HIDE_DEFAULT = dict(B=30.0, U=6.0, S=0.8, D=0.0, H=0.0, P=8.0, R=40.0, F=0.3, LK=20.0, LX=0.0)

EYE_PIVOT = {side: np.array(_J[f"eye_{side}"]) for side in ("l", "r")}      # (design units: the model's own frame)
EYE_TOP = {side: EYE_PIVOT[side] + np.array([sx * 3.0, 14.0, -5.0]) for side, sx in (("l", 1.0), ("r", -1.0))}


def shell_drop(m):
    """How far its shell settles once it's let down: from its lowest point at rest down to the ground (units)."""
    mats = m.pose_matrices(Pose().as_geo())
    low = min(float(mobkit._grid(mobkit._box_frame(mats, c))[:, 1].min()) for c in m["shell"].cubes)
    return max(0.0, low * 16.0 - 0.5)


def tucked(p, fold):
    """Its legs and claws folded in, its antennae laid back, by `fold` (0..1); its eyes are peek_eye's."""
    for side, sx in (("l", 1.0), ("r", -1.0)):
        p.rot(f"antenna_{side}", ANTENNAE_IN[0] * fold, 0.0, -sx * ANTENNAE_IN[1] * fold)
        p.scale(f"antenna_{side}", 1.0 - ANTENNAE_IN[2] * fold)
        for name in ("a", "b"):
            hx, hy, hz, kz, az = LEG_TUCK[name]
            p.rot(f"leg{name}_{side}", hx * fold, sx * hy * fold, sx * hz * fold)
            p.rot(f"knee{name}_{side}", 0.0, 0.0, sx * kz * fold)
            p.rot(f"ankle{name}_{side}", 0.0, 0.0, sx * az * fold)
    for claw, tuck in (("r", BIG_TUCK), ("l", SMALL_TUCK)):
        p.rot(f"claw_{claw}", tuck[0] * fold, tuck[1] * fold, tuck[2] * fold)
        p.rot(f"fore_{claw}", tuck[3] * fold, 0.0, 0.0)
        p.rot(f"pincer_{claw}", tuck[4] * fold, 0.0, 0.0)


def peek_slide(prm):
    """How far its left eye slides out for its peek (x out to its own side, y up, z ahead: units before S; the right
    eye mirrors it): the shell's own `E` (the skull's eye holes), or P straight ahead and a little apart."""
    if "E" in prm:
        return tuple(prm["E"])
    return PEEK_SPREAD, 0.0, -prm["P"]


def out_scale(prm):
    """Its eyes' scale once they're out of the shell: O (of the crab's own size: the skull's eye holes are small), or
    by default their full size."""
    if "O" in prm:
        return prm["O"] / prm["S"]
    return max(PEEK_OUT_MIN, PEEK_OUT_SIZE / prm["S"])


def _bone_rotation(rx, ry, rz):
    """The rotation a bone turned by these Bedrock angles gets, in the model's own (design) frame."""
    flip = np.diag([-1.0, 1.0, 1.0])
    return flip @ geo.gecko_rotation(rx, ry, rz)[:3, :3] @ flip


def peek_eye(p, prm, side, sx, eyes_in=0.0, aim=0.0, slide=0.0, height=0.0, stand=0.0, look=0.0, twitch=0.0):
    """
    One eye's peek: folded back on its stalk by `eyes_in` (EYES_IN), aimed out ahead by `aim`, slid out of the shell
    by `slide` at the height H by `height`, stood up and splayed by `stand`, turned to look by `look` (1 one way, -1
    the other) about the point F along its stalk, and a `twitch` (degrees).
    """
    rx0 = EYES_IN[0] * eyes_in + PEEK_AIM * aim + twitch
    rz0 = -sx * EYES_IN[1] * eyes_in
    rx = rx0 + (prm["R"] - PEEK_AIM) * stand
    rz = rz0 + sx * PEEK_SPLAY * stand - prm["LK"] * PEEK_LOOK_LEAN * look
    ry = prm["LK"] * look
    size = (1.0 - EYES_IN[2] * eyes_in) * (1.0 + (PEEK_AIM_SCALE - 1.0) * aim
                                           + (out_scale(prm) - PEEK_AIM_SCALE) * stand)
    E = peek_slide(prm)
    pos = np.array([sx * E[0] * slide - prm.get("LX", 0.0) * look, E[1] * slide + prm["H"] * height, E[2] * slide])
    if stand or look:
        # (stood up and turned about the point F along its stalk, where it leaves the mouth, not its root: that point
        # stays put, so the stalk never swings up into the rim)
        arm = size * (EYE_TOP[side] - EYE_PIVOT[side]) * prm["F"]
        pos += _bone_rotation(rx0, 0.0, rz0) @ arm - _bone_rotation(rx, ry, rz) @ arm
    p.rot(f"eye_{side}", rx, ry, rz)
    p.scale(f"eye_{side}", size)
    p.pos(f"eye_{side}", *pos)


def hidden(p, prm, drop, fold, back, eyes_in, settle=None, door=0.0, **peek):
    """
    The crab folded by `fold`, curled up with it, backed into its shell by `back` (0..1 of prm's B); the shell
    settles onto the ground by `settle` (by default with `back`), the crab inside it going down with it; its eyes
    folded back by `eyes_in`, its big claw dropped by `door` (0..1 of D) and its eyes posed by `peek` (peek_eye's).
    """
    settle = back if settle is None else settle
    tucked(p, fold)
    p.rot("claw_r", prm["D"] * door, 0.0, 0.0)
    p.rot("pincer_r", prm.get("D2", 0.0) * door, 0.0, 0.0)
    for side, sx in (("l", 1.0), ("r", -1.0)):
        peek_eye(p, prm, side, sx, eyes_in, **peek)
    p.scale("body", 1.0 - (1.0 - prm["S"]) * fold)
    p.pos("body", 0.0, prm["U"] * back - drop * settle - prm.get("Y", 0.0) * door, prm["B"] * back)
    p.pos("shell", 0.0, -drop * settle, 0.0)


def hide_with(rig, prm, drop):
    L = 1.2

    def frame(t):
        p = Pose()
        e = k(t, [(0.0, 0.0), (0.25, 1.0)])
        fold = k(t, [(0.0, 0.0), (0.1, 0.0), (0.5, 1.0, "out"), (L, 1.0)])
        back = k(t, [(0.0, 0.0), (0.4, 0.0), (0.95, 1.0, "out"), (L, 1.0)])
        hidden(p, prm, drop, fold, back, e)
        p.rot("shell", 0.0, 0.0, 3.0 * k(t, [(0.0, 0.0), (0.9, 0.0), (1.0, 1.0), (L, 0.0)]))
        return p

    a = C.record(rig, "hide", L, frame, loop=False)
    a.meta["shut"] = 0.9
    return a


PEEK_LENGTH = 4.1


def peek_keys(t):
    """The peek's moments at t (each 0..1; look -1..1, twitch degrees): the door opens, its eyes (still folded back)
    rise over its claw, unfold to point out ahead, slide out, stand up, and look round."""
    L = PEEK_LENGTH
    return dict(
        door=k(t, [(0.0, 0.0), (0.2, 0.0), (0.6, 1.0, "out"), (L, 1.0)]),
        height=k(t, [(0.0, 0.0), (0.35, 0.0), (0.75, 1.0, "out"), (L, 1.0)]),
        eyes_in=k(t, [(0.0, 1.0), (0.7, 1.0), (1.05, 0.0, "out"), (L, 0.0)]),
        aim=k(t, [(0.0, 0.0), (0.7, 0.0), (1.05, 1.0, "out"), (L, 1.0)]),
        slide=k(t, [(0.0, 0.0), (1.05, 0.0), (1.5, 1.0, "out"), (L, 1.0)]),
        stand=k(t, [(0.0, 0.0), (1.45, 0.0), (1.85, 1.0, "out"), (L, 1.0)]),
        look=k(t, [(0.0, 0.0), (2.0, 0.0), (2.3, 1.0, "out"), (2.7, 1.0), (3.1, -1.0, "out"), (3.5, -1.0),
                   (3.8, 0.0, "out"), (L, 0.0)]),
        twitch=6.0 * wave(t, 0.18) * k(t, [(0.0, 0.0), (3.8, 0.0), (3.9, 1.0), (L, 0.0)]))


def peek_pose(prm, drop, t):
    """The peek's pose at t."""
    p = Pose()
    keys = peek_keys(t)
    door, eyes_in = keys.pop("door"), keys.pop("eyes_in")
    hidden(p, prm, drop, 1.0, 1.0, eyes_in, 1.0, door, **keys)
    return p


def peek_with(rig, prm, drop):
    """
    From hiding: it stays in its shell; its big claw drops a little, opening a gap over it; its eyes unfold, pointed
    straight out ahead, and slide out through the gap, pop up once they're past the rim and splay apart, then look
    one way, then the other, and back, with a twitch; it stays there peeking (its last pose stays; come_out climbs out
    from it). The user, October 27-28: "make their eyes come out, look around, then make a separate come-out
    animation"; "out more, like he is physically looking outside"; "separate the eyes"; "pop out in a way where they
    don't clip through the top of the shell ... also look left and right".
    """
    a = C.record(rig, "peek", PEEK_LENGTH, lambda t: peek_pose(prm, drop, t), loop=False)
    a.meta["eyes"] = 1.0
    return a


COME_OUT_LENGTH = 2.45


def come_out_keys(t):
    """come_out's moments at t: its eyes back in the way they came, its claw closing the door, then it climbs out."""
    L = COME_OUT_LENGTH
    return dict(
        stand=k(t, [(0.0, 1.0), (0.3, 0.0, "out"), (L, 0.0)]),
        slide=k(t, [(0.0, 1.0), (0.25, 1.0), (0.65, 0.0, "in"), (L, 0.0)]),
        aim=k(t, [(0.0, 1.0), (0.6, 1.0), (0.95, 0.0), (L, 0.0)]),
        height=k(t, [(0.0, 1.0), (0.9, 1.0), (1.2, 0.0), (L, 0.0)]),
        eyes_in=k(t, [(0.0, 0.0), (0.6, 0.0), (0.95, 1.0), (1.9, 1.0), (2.3, 0.0, "out"), (L, 0.0)]),
        door=k(t, [(0.0, 1.0), (1.1, 1.0), (1.35, 0.0), (L, 0.0)]),
        back=k(t, [(0.0, 1.0), (1.4, 1.0), (1.9, 0.0, "out"), (L, 0.0)]),
        fold=k(t, [(0.0, 1.0), (1.55, 1.0), (2.25, 0.0, "out"), (L, 0.0)]))


def come_out_pose(prm, drop, t):
    """come_out's pose at t."""
    p = Pose()
    keys = come_out_keys(t)
    fold, back, door, eyes_in = keys.pop("fold"), keys.pop("back"), keys.pop("door"), keys.pop("eyes_in")
    hidden(p, prm, drop, fold, back, eyes_in, back, door, **keys)
    return p


def come_out_with(rig, prm, drop):
    """From peeking (peek's last pose): its eyes come back in the way they went out, its claw closes the door, then it
    climbs out of its shell, the shell lifted back onto its back, and unfolds to stand as it does at rest."""
    a = C.record(rig, "come_out", COME_OUT_LENGTH, lambda t: come_out_pose(prm, drop, t), loop=False)
    a.meta["out"] = 1.4
    return a


def hide(rig):
    return hide_with(rig, HIDE["bell"], 0.0)


def peek(rig):
    return peek_with(rig, HIDE["bell"], 0.0)


def come_out(rig):
    return come_out_with(rig, HIDE["bell"], 0.0)


def model_animations(rig, m, v):
    """Its hide, peek and come_out for the shell this model wears (mobs.py writes them into the model's own animation
    file)."""
    prm = HIDE.get(v["shell"], HIDE_DEFAULT)
    drop = shell_drop(m)
    return {"hide": hide_with(rig, prm, drop), "peek": peek_with(rig, prm, drop),
            "come_out": come_out_with(rig, prm, drop)}


def model_check(m, v, rig):
    """
    {(animation, "shell" or "sides"): what's wrong}: this model's hide, peek and come_out against its shell
    (tools/fire/hermit_fit.py): its eyes never seen outside the shell's openings or into its rim, never into its claws
    or antennae, the rest of it hidden while it's in, and nothing of it going in or out through the shell's sides.
    """
    import hermit_fit                      # (it imports this module: loaded only when it's needed)
    chk = hermit_fit.PeekCheck(m, v, shell_drop(m))
    out = {}
    for name, (bad, t) in hermit_fit.check_peek(m, v, rig, chk).items():
        out[(name, "shell")] = f"{bad} at {t:.2f} s"
    for name, t, part, n in hermit_fit.check_motion(m, v, rig, chk.hmap):
        # (only while it's folded up, backing in or still in: unfolded, its legs stand beside the shell, as at rest)
        if (name == "hide" and t < 0.5) or (name == "come_out" and t > 1.55):
            continue
        out.setdefault((name, "sides"), f"{part} {n} points at {t:.2f} s")
    return out


DEATH_ANTENNAE = (10.0, 60.0)     # dying: its antennae flopping limp out to its sides (x, z), clear of its claws (they
                                  # drooped forward into its big claw)


def death(rig):
    L = 2.4

    def frame(t):
        p = Pose()
        buckle = k(t, [(0.0, 0.0), (0.3, 0.3), (1.2, 1.0, "in"), (L, 1.0)])
        twitch = k(t, [(0.0, 0.0), (1.6, 0.0), (1.75, 1.0), (2.0, 0.0)])
        p.pos("body", 0.0, -6.0 * buckle, -2.0 * buckle)
        p.rot("body", 10.0 * buckle, 0.0, 8.0 * buckle)
        p.pos("shell", 0.0, -6.0 * buckle, 0.0)
        p.rot("shell", 4.0 * buckle, 0.0, 6.0 * buckle)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            p.rot(f"eye_{side}", 60.0 * buckle, 0.0, -sx * 30.0 * buckle)
            p.rot(f"antenna_{side}", DEATH_ANTENNAE[0] * buckle, 0.0, -sx * DEATH_ANTENNAE[1] * buckle)  # (limp)
        p.rot("claw_r", -8.0 * buckle, 0.0, 0.0)
        p.rot("pincer_r", -6.0 * buckle, 0.0, 0.0)
        p.rot("finger_r", 8.0 * buckle + 10.0 * twitch, 0.0, 0.0)
        p.rot("claw_l", -16.0 * buckle, 0.0, 0.0)
        p.rot("pincer_l", -24.0 * buckle, 0.0, 0.0)
        p.rot("fore_l", -10.0 * buckle, 0.0, 0.0)
        for side, sx in (("l", 1.0), ("r", -1.0)):
            for name in ("a", "b"):
                p.rot(f"leg{name}_{side}", -8.0 * buckle, 0.0, sx * (-30.0 * buckle + 6.0 * twitch))
                p.rot(f"knee{name}_{side}", 0.0, 0.0, sx * 30.0 * buckle)
                p.rot(f"ankle{name}_{side}", 0.0, 0.0, sx * 40.0 * buckle)
        return p

    a = C.record(rig, "death", L, frame, loop=False)
    a.meta["fall"] = 1.2
    return a


def animations(rig):
    return {"idle": idle(rig), "walk": walk(rig), "snap": snap(rig), "pinch": pinch(rig), "hide": hide(rig),
            "peek": peek(rig), "come_out": come_out(rig), "death": death(rig)}
