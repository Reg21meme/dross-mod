"""
The Huntsman in the Alpha Moose's saddle (CLAUDE.md step 14: stage 1 of his fight, "the Wild Hunt"): the gallery's 6A
rider without the moose (rimemobs/alpha_moose.py is the moose). The model's floor is the moose's ground, so the rider
sits at the moose's own position in the game. The skeleton is rimemobs/hunter_rig.py; on foot he's huntsman.py.

Animations (animation.huntsman_rider.*):

  ride_idle     (loop)  sitting easy in the saddle, breathing, looking round
  ride_gallop   (loop)  riding the moose's gallop (its run's 0.6 s stride): rising and falling with it
  ride_thrust   (once)  his arm drawn back, then the spear driven forward and down past the moose's right side, under
                        its antlers (the hit at its meta), and brought back out to his right and up, never across its
                        antlers (a check plays it against the moose's own animations, from any moment of its idle)
  ride_shoot    (once)  the circling hunt's shots: the bow off his back, his bow arm straight, two arrows from the
                        saddle's quiver drawn to his chin and loosed at a target out to his left (rimemobs/archery.py;
                        his spear stays in its cup)
  ride_shoot_front, ride_shoot_right, ride_shoot_back   (once)  the same two shots ahead of him (past the moose's
                        head, on its left), on his right (twisted round in the saddle) and behind him (over his left
                        shoulder): so the moose needn't turn side-on to him to let him shoot (SHOT_DIRECTIONS)
  ride_rain     (once)  the arrow rain: the bow raised to the sky, four loosings of three arrows each
  ride_horn     (once)  the Horn of the Hunt held to his lips for a long call, the bell up and out
  (in every one his cloak keeps lying over the moose's rump, whatever his hips and chest do: _keep_cape)
  ride_bola     (once)  a bola whirled over his head and thrown (his spear left standing in its cup)
  ride_leap     (once)  the split, played with the moose's rear: he rides the moose up as it rears (its carry_rear
                        is in this animation), rises in the stirrups as it neighs at the top of its rear, springs up
                        and off to its left and drops to the snow beside it, LEAP_OUT units out, landing in a crouch
                        with his boots on the snow (in the game he then gets off there and plays `land` on foot, which
                        starts in that crouch)
  carry_<name>  (each of the Alpha Moose's animations)  the saddle's motion: its body bone's (root and all), on the bone
                        "carry" above everything he has. In the game a second animation controller plays the one
                        matching the moose's animation, so he rides along with whatever it does (rears, kicks, kneels,
                        gallops) while his own animation plays on top; not while he leaps off (ride_leap).
"""
import math

import numpy as np

from kit.anims import bump, ramp, smooth
from kit.clipping import _box_frame
from kit.geo import euler_bedrock, gecko
from kit.moves import keyed
from rimedesigns import d06_huntsman as H
from rimekit import mobkit
from rimekit.mobkit import Pose

from . import archery, hunt_ik
from . import hunter_rig as R
from . import alpha_moose
from .huntsman import horn_moves, _shots_meta, HORN_CALL

ID = "huntsman_rider"
NAME = "The Huntsman (riding)"
POSE = "ride"
K = R.K
DESIGN = R.Design
BUILD_VARIANT = R.variant(POSE)
VARIANTS = (dict(name="huntsman_rider", design=BUILD_VARIANT, hidden=()),)
# His whole skeleton hangs from "carry", which moves like the moose's back (carry_<name>); its pivot is his seat.
CARRY = "carry"
BONES = ([(CARRY, tuple(float(v) for v in R.joints(POSE)["hips"]), None)]
         + [(n, piv, CARRY if par is None else par) for n, piv, par in R.bones(POSE)])
ROUTES = R.ROUTES
FREE = ("bow_held", "horn_held", "spear", "string_top", "string_bot", "arrow_nock", "arrow_hand", "spear_free",
        "quiver_hip", "bola_held")
LEGS = []
GIF_VIEW = (55.0, 8.0, 2.6)
LEAP_OUT = 46.0                 # he lands this far to the moose's left of its middle (units: about 2.9 blocks)
HITS = {"ride_thrust": (0.62,), "ride_shoot": (1.15, 2.05), "ride_rain": (1.0, 1.75, 2.5, 3.25), "ride_horn": (0.85,),
        "ride_bola": (0.95,), "ride_leap": (0.85,),   # (the leap: LEAP_SPRING)
        "ride_shoot_front": (1.15, 2.05), "ride_shoot_right": (1.15, 2.05), "ride_shoot_back": (1.15, 2.05)}
# The shots in the four directions (the user, October 23, 2026: "make a shot animation for each major cardinal direction
# that way the moose doesn't have to move in the direction perpendicular to you to be able to shoot you"): where the bow
# aims ((degrees to his left, degrees up): his left is ride_shoot's), how far his chest turns (positive: to his right)
# and his head (on his chest), and how far his chest turns towards the target to nock (archery's face).
SHOT_DIRECTIONS = {
    # past the moose's head on its left, the bow held high while he nocks (its lower end clear of the moose's neck)
    "ride_shoot_front": dict(aim=(24.0, 10.0), chest=40.0, head=-55.0, face=-35.0, nock_down=-30.0),
    # twisted round to his right in the saddle (the bow's in his left hand: as far round as his arms reach)
    "ride_shoot_right": dict(aim=(-60.0, 12.0), chest=80.0, head=-15.0, face=-10.0, nock_down=-25.0),
    # over his left shoulder, a Parthian shot (his chest doesn't turn back to the quiver between: its reach crossed him)
    "ride_shoot_back": dict(aim=(125.0, 10.0), chest=-40.0, head=-70.0, face=-15.0, nock_down=-20.0, reach_turn=0.0),
}
# What the spear mustn't touch on the moose (the user: the thrust went through its antlers), checked by mobs.py.
MOOSE_KEEP_CLEAR = ("antlers", "head", "neck", "ear_l", "ear_r", "body")
SPEAR_AXIS = (H.RIDE_FISTS[0] - (R.RIDE_CUP + np.array([0.0, 1.0, 0.0])))
SPEAR_AXIS = SPEAR_AXIS / np.linalg.norm(SPEAR_AXIS)          # (the spear in the saddle, butt to tip: in its rest)

clip_group = R.clip_group
CLIP_PAIRS = (("arms", "body"), ("string", "body"), ("bola", "body"),)

# Riding: the reins in his left fist, the spear's butt in its cup; so the right arm's rest already holds the spear.
GALLOP_T = alpha_moose.GALLOP[0]


def _sit(p, t, T):
    R.breathe(p, t, T, 0.8)


_RIG = None                     # (set by animations(): _keep_cape needs the model to keep the cloak in place)


def _keep_cape(p):
    """His cloak lies over the moose's rump (the user: "his cape clips through the moose while he rides it, it should
    always stay on top"): whatever his hips and chest do, it's turned back by their turn, so it keeps lying where it
    lies at rest; only its own flutter (the frame's own turn of the cape, kept small and upwards) is added. And it's
    moved back by their move too (the user, October 20: still into the moose at a gallop: as he bobbed down and leaned
    into his moves the whole cloak went down with him into its back), so its top stays where it is on the saddle; it
    still goes with him when all of him moves (the leap off)."""
    from kit.geo import gecko_rotation
    I = np.eye(3)
    rh = gecko_rotation(*p["hips"][0])[:3, :3] if "hips" in p else I
    rc = gecko_rotation(*p["chest"][0])[:3, :3] if "chest" in p else I
    rf = gecko_rotation(*p["cape"][0])[:3, :3] if "cape" in p else I
    p.get3("cape")[0] = list(euler_bedrock((rh @ rc).T @ rf))
    if _RIG is None:
        return
    m = _RIG.m
    piv = np.asarray(m["cape"].pivot, dtype=float)
    still = Pose()
    for b in (CARRY, "root"):
        if b in p:
            still[b] = [list(v) for v in p[b]]
    want = m.point(m.pose_matrices(still.as_geo()), "chest", piv)
    for _ in range(3):
        mats = m.pose_matrices(p.as_geo())
        err = want - m.point(mats, "cape", piv)
        if float(np.linalg.norm(err)) < 0.01:
            break
        R = mats["chest"][:3, :3]
        o = R.T @ np.array([-err[0], err[1], err[2]])          # (the offset is in the chest's own frame)
        p.pos("cape", -o[0], o[1], o[2])


def _show(p, props=()):
    _keep_cape(p)
    R.show(p, props)


def ride_idle(rig):
    T = 4.0

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        look = float(keyed(t, [(0.0, 0.0), (1.0, 0.0), (1.6, -18.0), (2.6, -18.0), (3.2, 0.0), (T, 0.0)]))
        p.rot("head", 0.0, look, 0.0)
        _show(p)
        return p

    return mobkit.record(rig, "ride_idle", T, frame)


def ride_gallop(rig):
    """His own part of riding the gallop (the saddle's rise and fall comes from carry_run): he absorbs it, a little
    behind the moose's stride."""
    T = GALLOP_T

    def frame(t):
        p = Pose()
        w = 2 * math.pi * t / T
        p.pos("hips", 0.0, 1.0 * math.sin(w), -0.6 * math.cos(w))
        p.rot("hips", -3.0 * math.sin(w), 0.0, 0.0)
        p.rot("chest", 10.0 + 4.0 * math.sin(w - 0.8), 0.0, 0.0)
        p.rot("head", -10.0 - 3.0 * math.sin(w - 1.2), 0.0, 0.0)
        # (the riding cloak drapes far back over the rump, so a few degrees at his shoulders move its end a lot)
        p.rot("cape", 1.5 + 1.5 * math.sin(w), 0.0, 0.0)
        R.arm(p, "l", sh=(-6.0 * math.sin(w - 0.5), 0.0, 0.0))
        R.arm(p, "r", sh=(-4.0 * math.sin(w - 0.5), 0.0, 0.0))
        _show(p)
        return p

    return mobkit.record(rig, "ride_gallop", T, frame)


def ride_thrust(rig):
    """
    The thrust from the saddle (the user's note: it "goes through the moose's antlers ... he should pull his arm back
    and thrust underneath the moose's antlers"): he draws his arm back, the spear lifted out of its cup and levelled,
    its point low; then drives it forward and down past the moose's right shoulder, under its broad right antler, his
    body leaning in behind it; and brings it back to its cup. The IK puts his fist and the spear's line where they go.
    """
    T = 1.3
    hit, = HITS["ride_thrust"]
    hands = hunt_ik.Hands(rig, R.joints(POSE))
    k = hands.k
    rest = hands.j["hand_r"]
    # (Bedrock units, the rider's frame = the moose's: his right is -x, ahead is -z)
    keys_fist = [(0.0, rest), (0.12, rest + np.array([-1.0, 2.0, 2.0])),
                 (0.42, rest + np.array([-5.0, 5.0, 12.0])),                  # drawn back, high by his shoulder
                 (hit, rest + np.array([-10.0, -5.0, -7.0]), "in"),          # driven forward and down past the moose
                 (0.8, rest + np.array([-9.0, -4.5, -5.5])),
                 (0.98, rest + np.array([-8.0, -1.0, 5.0])),                 # pulled back along its own line
                 (1.15, rest + np.array([-5.0, 2.0, 2.0])), (T, rest)]
    level = hunt_ik.unit(np.array([-0.24, -0.4, -0.88]))
    # (the way back: the spear pulled back and swung out to his right as it rises: its point never comes across the
    # antlers, which reach forward and up from the moose's head; the user saw it go through them)
    out_right = hunt_ik.unit(np.array([-0.82, 0.38, 0.42]))
    keys_dir = [(0.0, SPEAR_AXIS), (0.12, SPEAR_AXIS), (0.42, hunt_ik.unit(np.array([-0.22, -0.36, -0.9]))),
                (hit, level, "in"), (0.8, level), (0.98, hunt_ik.unit(np.array([-0.7, -0.15, -0.7]))),
                (1.12, out_right), (T, SPEAR_AXIS)]

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        wind = bump(t, 0.1, 0.42, 0.45, hit)
        drive = bump(t, 0.45, hit, 0.85, 1.15)
        p.rot("chest", -4.0 * wind + 14.0 * drive, 16.0 * wind - 14.0 * drive, 0.0)
        p.rot("head", 4.0 * wind - 8.0 * drive, -10.0 * wind + 10.0 * drive, 0.0)
        p.pos("hips", 0.0, 0.0, -2.0 * drive)
        fist = np.asarray(keyed(t, keys_fist), dtype=float)
        d = hunt_ik.unit(np.asarray(keyed(t, keys_dir), dtype=float))
        if 0.04 < t < T - 0.04:
            hands.arm(p, "r", fist, down_local=SPEAR_AXIS, down=d, key="spear", w_down=5.0)
        _show(p)
        return p

    a = mobkit.record(rig, "ride_thrust", T, frame, loop=False)
    a.meta["hits"] = [hit]
    a.meta["ik_miss"] = round(hands.miss, 2)
    return a


def _rider_shots(rig, name, T, shots, aim, chest, head, lean=0.0, lift=0.0, hold=0.12, quick=1.0, face=-65.0,
                 volley=1, nock_down=archery.NOCK_DOWN, reach_turn=25.0):
    """A shooting animation in the saddle: his body turned (chest, head: degrees, positive his right), the bow out
    of the archery code (his spear stays in its cup meanwhile; his arrows from the saddle's quiver)."""
    hands = hunt_ik.Hands(rig, R.joints(POSE))
    quiver = H.SADDLE_QUIVER * R.MOOSE_K            # (the saddle's: the moose's scale, not his)
    archer = archery.Archer(rig, hands, shots, aim, start=0.05, end=T - 0.05, hold=hold, quick=quick, drop=0.0,
                            face=face, volley=volley, quiver=lambda p, mats: quiver, reach_turn=reach_turn,
                            nock_down=nock_down)

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        kk = smooth((t - 0.06) / 0.35) * (1.0 - smooth((t - (T - 0.55)) / 0.45))
        p.rot("chest", (2.0 - lean) * kk, chest * kk, 0.0)
        p.rot("head", (2.0 - lift) * kk, head * kk, 0.0)
        props = archer.frame(p, t)
        _show(p, tuple(props))
        return p

    a = mobkit.record(rig, name, T, frame, loop=False)
    a.meta["shots"] = list(shots)
    _shots_meta(rig, hands, a, archer)
    return a


def ride_shoot(rig):
    """The circling hunt's shots: the moose galloping round you, he looses two arrows out to his left."""
    T = 2.9
    return _rider_shots(rig, "ride_shoot", T, list(HITS["ride_shoot"]), lambda t: hunt_ik.elevate(72.0, 3.0),
                        chest=4.0, head=-62.0, hold=0.1, quick=0.85)


def ride_shoot_dir(rig, name):
    """His two shots from the saddle at a target ahead of him (past the moose's head on its left), on his right
    (twisted round in the saddle) or behind him (over his left shoulder, a Parthian shot), like ride_shoot's to his
    left: so the moose needn't turn side-on to him (SHOT_DIRECTIONS)."""
    T = 2.9
    d = SHOT_DIRECTIONS[name]
    return _rider_shots(rig, name, T, list(HITS[name]), lambda t, a=d["aim"]: hunt_ik.elevate(*a), chest=d["chest"],
                        head=d["head"], hold=0.1, quick=0.85, face=d["face"],
                        nock_down=d.get("nock_down", archery.NOCK_DOWN), lean=d.get("lean", 0.0), lift=d.get("lift", 0.0),
                        reach_turn=d.get("reach_turn", 25.0))


def ride_rain(rig):
    """The arrow rain from the saddle: the bow raised to the sky ahead of him and out to his left, four loosings of
    three arrows each (the user: "he grabs 3 arrows at a time")."""
    T = 4.2
    return _rider_shots(rig, "ride_rain", T, list(HITS["ride_rain"]), lambda t: hunt_ik.elevate(25.0, 62.0),
                        chest=42.0, head=-58.0, lean=8.0, lift=22.0, hold=0.08, quick=0.65, face=-45.0, volley=3,
                        nock_down=-15.0)      # (the bow held up while he nocks: its lower end clear of the moose's neck)


def ride_horn(rig):
    """The Horn of the Hunt in the saddle: the horn from his belt to his lips, a long call, back again; his spear in
    his right hand."""
    T = 3.7
    blow0, blow1 = HORN_CALL
    hands = hunt_ik.Hands(rig, R.joints(POSE))
    horn = horn_moves(rig, hands, 0.0, lower=blow1 + 0.05, back=blow1 + 0.4)

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        breath = bump(t, 0.4, 0.75, 0.8, blow0 + 0.1)
        blowing = bump(t, blow0 - 0.05, blow0 + 0.15, blow1 - 0.2, blow1 + 0.05)
        rise = ramp(t, blow0, blow1)
        p.rot("chest", -5.0 * breath - (3.0 + 4.0 * rise) * blowing, 6.0 * blowing, 0.0)
        p.pos("chest", 0.0, 0.6 * breath, 0.0)
        p.rot("head", -8.0 * breath - (8.0 + 10.0 * rise) * blowing + 1.2 * math.sin(2 * math.pi * 7.0 * t) * blowing,
              8.0 * blowing, 0.0)
        props = horn(p, t)
        _show(p, tuple(props))
        return p

    a = mobkit.record(rig, "ride_horn", T, frame, loop=False)
    a.meta.update(blow=blow0, end=blow1, ik_miss=round(hands.miss, 2))
    return a


def bola_arm(p, t, rel, T):
    """The bola's right arm (shared with the throw on foot): raised high and out to his right, the elbow opened
    nearly straight (his arm is modelled bent round the spear, so raised as it is the hand came down over his hood),
    the bola whirled round from his fist about three times, then flung forward at `rel` as the arm comes over and
    down. Returns the props to show: the bola in his hand until he lets go (the user, October 20: "the bola throws
    nothing visible and clips him")."""
    up = ramp(t, 0.08, 0.28) * (1.0 - ramp(t, rel + 0.25, T - 0.05))        # the arm up (and the elbow open)
    spin = ramp(t, 0.1, 0.28) * (1.0 - ramp(t, rel - 0.12, rel))             # the whirling
    a = 2 * math.pi * 3.2 * t
    raise_x = float(keyed(t, [(0.0, 0.0), (0.28, -BOLA_UP), (rel - 0.14, -BOLA_UP - 8.0), (rel, -112.0, "in"),
                              (rel + 0.18, -72.0, "out"), (T - 0.05, 0.0)]))
    # (the roll that swings a hanging arm out to his right swings a raised one in, over his head: so it's turned the
    # other way, and grows with how far up the arm is)
    roll = -BOLA_OUT * min(1.0, -raise_x / BOLA_UP) ** 2
    R.arm(p, "r", sh=(raise_x, 0.0, roll + 10.0 * spin * math.sin(a)),
          el=(BOLA_OPEN * up, 0.0, 0.0), hand=(28.0 * spin * math.cos(a), 0.0, 22.0 * spin * math.sin(a)))
    return ["bola_held"] if 0.12 < t < rel else []


BOLA_UP = 150.0                 # the whirling arm raised this far (degrees; 180 is straight up)
BOLA_OUT = 36.0                 # and rolled this far out to his right: clear of his hood and antlers
BOLA_OPEN = 76.0                # its elbow opened this far from its modelled bend (about 5-7 degrees off straight:
                                # bent more, the forearm folded in over his hood)


def ride_bola(rig):
    """He lets go of the spear (it stays standing in its cup), whirls the bola round over his head and out to his
    right three times and throws it."""
    T = 1.5
    rel, = HITS["ride_bola"]

    def frame(t):
        p = Pose()
        _sit(p, t, T)
        props = bola_arm(p, t, rel, T)
        p.rot("chest", 6.0 * bump(t, rel - 0.1, rel, rel + 0.2, T), -10.0 * bump(t, rel - 0.15, rel, rel + 0.2, T), 0.0)
        _show(p, tuple(props) + (("spear_free",) if 0.06 < t < T - 0.08 else ()))
        return p

    a = mobkit.record(rig, "ride_bola", T, frame, loop=False)
    a.meta["release"] = rel
    return a


# The split's timing (the user, October 23, 2026: "the jump is too early, when the moose would neigh"): the moose's
# rear (alpha_moose.rear) is up by 0.65 s, its head tossing as it neighs from 0.6 to 1.6 s; he springs off at
# LEAP_SPRING, is in the air LEAP_AIR, and crouches on the snow LEAP_HOLD before he's on his own feet (the game swaps his
# model there). Until October 23 he sprang at 0.45 s, as the moose only began to rear.
LEAP_SPRING = 0.85
LEAP_AIR = 0.41
LEAP_HOLD = 0.14
OLD_JUMP, OLD_DOWN, OLD_T = 0.45, 0.86, 1.0      # (the leap's own timeline: its rise before the spring, its air time)


def ride_leap(rig, carry_rear=None):
    """The split: he rides the moose up as it rears, holding on (the saddle's motion, carry_rear, is in this animation:
    the game's carry controller stops while he plays it), rises in the stirrups as it neighs at the top, springs up and
    off to its left side and drops to the snow beside it, the saddle's motion fading out of him while he's in the air;
    his riding pose is a crouch already, so he lands in it, his boots on the snow, LEAP_OUT units out."""
    jump = LEAP_SPRING
    down = jump + LEAP_AIR                         # his boots touch the snow
    T = down + LEAP_HOLD
    late = jump - OLD_JUMP                         # (the leap's timeline starts this late)

    crouch = _landing_legs()

    def legs(p, k):
        # his legs from round the moose's back to under him: a crouch on the snow, boots flat
        for bone, e in crouch.items():
            p.rot(bone, *(v * k for v in e))

    landed = Pose()
    legs(landed, 1.0)
    mats = rig.m.pose_matrices(landed.as_geo())
    feet = min(float(mobkit._grid(_box_frame(mats, c))[:, 1].min() * 16.0)
               for bone in ("boot_l", "boot_r") for c in rig.m[bone].cubes)

    def frame(t):
        p = Pose()
        if carry_rear is not None:
            # riding the rear up; in the air the saddle's motion fades out (he's on his own once he lands)
            rot, pos, _scale = carry_rear.sample(min(t, jump))[CARRY]
            k = 1.0 - smooth((t - jump) / (down - 0.05 - jump)) if t > jump else 1.0
            p.rot(CARRY, *(v * k for v in rot))
            p.pos(CARRY, *(v * k for v in pos))
        t = max(0.0, t - late)                     # (the leap's own timeline from here)
        jump0, down0 = OLD_JUMP, OLD_DOWN
        rise = bump(t, 0.0, 0.25, 0.3, jump0)
        up = float(keyed(t, [(0.0, 0.0), (jump0, 0.0), (0.64, 16.0, "out"), (down0, -feet, "in"), (OLD_T, -feet)]))
        squash = bump(t, down0 - 0.02, down0 + 0.05, down0 + 0.08, OLD_T)
        out = LEAP_OUT * smooth((t - jump0) / (down0 - 0.05 - jump0))
        p.pos("root", out, up + 6.0 * rise, 0.0)
        p.rot("root", 0.0, 0.0, -16.0 * bump(t, jump0, 0.6, 0.66, down0))
        air = bump(t, jump0, 0.6, 0.7, down0)
        legs(p, ramp(t, 0.6, down0))
        for side in ("l", "r"):
            p.rot(f"thigh_{side}", 22.0 * air, 0.0, 0.0)
        p.rot("chest", 10.0 * squash, 0.0, 0.0)
        p.rot("cape", 14.0 * bump(t, jump0, 0.62, 0.75, OLD_T), 0.0, 0.0)
        R.arm(p, "l", sh=(-55.0 * air - 20.0 * squash, 0.0, 30.0 * air + 22.0 * squash))
        _show(p)
        return p

    a = mobkit.record(rig, "ride_leap", T, frame, loop=False)
    a.meta.update(jump=jump, down=down, out=LEAP_OUT)
    return a


def _arc(a, b):
    """The shortest rotation (3x3) taking direction a to direction b."""
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if np.linalg.norm(v) < 1e-9:
        return np.eye(3)
    vx = np.array([[0.0, -v[2], v[1]], [v[2], 0.0, -v[0]], [-v[1], v[0], 0.0]])
    return np.eye(3) + vx + vx @ vx / (1.0 + c)


# The landing crouch: the thigh forward and down under him, the shin angled back (design directions, his left leg).
LAND_THIGH = np.array([0.18, -0.72, -0.62])
LAND_SHIN = np.array([0.03, -0.95, 0.3])


def _landing_legs():
    """The thighs', shins' and boots' rotations (Bedrock degrees) that take his legs from round the moose's back (the
    riding pose) to the landing crouch, the boots kept as they were."""
    flip = np.array([-1.0, 1.0, 1.0])                  # (Bedrock directions to GeckoLib space and back)
    out = {}
    for x, side in ((1.0, "l"), (-1.0, "r")):
        hip, knee, ankle = (np.asarray(v, dtype=float) for v in H.ride_leg(x))
        mirror = np.array([x, 1.0, 1.0])
        r1 = _arc((knee - hip) * flip, LAND_THIGH * mirror * flip)
        shin_now = r1 @ ((ankle - knee) * flip)
        r2w = _arc(shin_now, LAND_SHIN * mirror * flip)
        out[f"thigh_{side}"] = euler_bedrock(r1)
        out[f"shin_{side}"] = euler_bedrock(r1.T @ r2w @ r1)
        out[f"boot_{side}"] = euler_bedrock((r2w @ r1).T)
    return out


def carries(rig, moose=None):
    """carry_<name> for every one of the Alpha Moose's animations: the transform that takes its body bone from rest to
    each frame (root and all), as the carry bone's rotation round his seat and a shift. Its body carries the saddle, so
    he moves exactly as the saddle does. `moose` (its model and animations) is built here if not given."""
    if moose is None:
        mm, _t = mobkit.build_model(alpha_moose, verbose=False)
        moose = (mm, alpha_moose.animations(mobkit.MobRig(mm, alpha_moose)))
    mm, manims = moose
    pc = gecko(np.asarray(rig.m[CARRY].pivot, dtype=float))
    out = {}
    for name, a in manims.items():
        prev = [None]

        def frame(t, a=a, prev=prev):
            g = mm.pose_matrices(a.sample(t))["body"]
            rot = g[:3, :3]
            shift = g[:3, 3] - pc + rot @ pc
            e = euler_bedrock(rot, prev[0])
            prev[0] = e
            p = Pose()
            p.rot(CARRY, *e)
            p.pos(CARRY, -shift[0] * 16.0, shift[1] * 16.0, shift[2] * 16.0)
            return p

        out[f"carry_{name}"] = mobkit.record(rig, f"carry_{name}", a.length, frame, loop=a.loop is True)
    return out


def with_carry(a, carry):
    """`a` played with the carry animation `carry` (or as it is if None): for previews and the checks."""
    if carry is None:
        return a

    class _Both:
        name, length, loop, meta = a.name, a.length, a.loop, a.meta

        @staticmethod
        def sample(t):
            out = dict(a.sample(t))
            out[CARRY] = carry.sample(t if carry.loop is not True else t % carry.length)[CARRY]
            return out

    return _Both


def animations(rig):
    global _RIG
    _RIG = rig
    carry = carries(rig)
    return {
        "ride_idle": ride_idle(rig),
        "ride_gallop": ride_gallop(rig),
        "ride_thrust": ride_thrust(rig),
        "ride_shoot": ride_shoot(rig),
        "ride_shoot_front": ride_shoot_dir(rig, "ride_shoot_front"),
        "ride_shoot_right": ride_shoot_dir(rig, "ride_shoot_right"),
        "ride_shoot_back": ride_shoot_dir(rig, "ride_shoot_back"),
        "ride_rain": ride_rain(rig),
        "ride_horn": ride_horn(rig),
        "ride_bola": ride_bola(rig),
        "ride_leap": ride_leap(rig, carry["carry_rear"]),
        **carry,
    }
