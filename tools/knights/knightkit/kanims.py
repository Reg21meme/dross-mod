"""
The elemental knights' animations (tools/knights/mobs.py builds them and writes the files), made to move fluidly (the
user, November 18, 2026, after watching the first ones in the game: "wonky and jittery and not fluid").

What made the first ones jittery, and what's done here instead:
  * Every key was a full stop: each stretch between two keys eased out and in on its own, so a swing that went
    through three keys stopped dead at each one. Here a curve flows through its keys (a monotone cubic: it slows only
    where the motion turns round), easing out of the first key and into the last.
  * Everything moved on the same beat: arm, body, legs and head all started and stopped at the same keys. Here the
    body leads (`sh(keys, -lead)`), the off arm and the cape trail behind (`sh(keys, +lag)`), the head steadies
    against the body, and a blow carries on past its hit before it settles (follow-through), the body settling with
    a small sway back past rest at the end.
  * The walk's bob and its cape were made of |sin| and |cos|: two kinks a step. Here they're smooth (sin^2), the arms
    swing a moment behind the legs, the head stays steady, and each knight's walk is made at about the pace it really
    walks (heavier knights longer, slower strides), so the game hardly has to speed it up or slow it down.
  * The frames were sampled 20 times a second and thinned to within 0.3 degrees, so the straight lines GeckoLib draws
    between keyframes had visible corners. Here they're sampled 40 times a second and kept within 0.06 degrees.

A blow that lands ("hit" on its key) arrives at full speed: it stops there (into the ground, a recoil after it), or,
when the next key carries on the same way, sweeps on through it, slowing (follow-through). Nothing else stops dead.

Heavy knights (`kn.heavy` near 1: the Wreck, Rockfall, Cinder and Geode Knights) wind up longer, sink deeper and hold
their blow longer before they recover; light ones (the Stormblade, Flamehand, the Blight Warden) are snappier.
"""
import math

import numpy as np
from scipy.spatial.transform import Rotation, Slerp

from kit.anims import Animation
from kit.geo import gecko_rotation
from rimekit.mobkit import Pose

STEP = 0.025                     # seconds between recorded frames (40 a second)
BAKE = (0.06, 0.01, 0.004)       # how close the thinned keyframes stay to the frames: degrees, units, scale


# ---------------------------------------------------------------- curves


def curve(t, keys):
    """
    The value at time t from keys [(time, value), ...] or (time, value, "hit"): a smooth curve through them (a monotone
    cubic, Fritsch-Carlson: it never overshoots a key and only slows where it turns round), resting at its first and
    last keys. A "hit" key is arrived at at full speed (a blow landing); it stops there unless the next key carries on
    the same way, when it sweeps on through, slowing.
    """
    n = len(keys)
    ts = [float(k[0]) for k in keys]
    vs = [float(k[1]) for k in keys]
    hit = [len(k) > 2 and k[2] in ("hit", "in") for k in keys]
    if t <= ts[0]:
        return vs[0]
    if t >= ts[-1]:
        return vs[-1]
    i = 0
    while ts[i + 1] < t:
        i += 1
    d = [(vs[j + 1] - vs[j]) / (ts[j + 1] - ts[j]) for j in range(n - 1)]

    def through(j):
        a, b = d[j - 1], d[j]
        if a * b <= 0.0:
            return 0.0
        h0, h1 = ts[j] - ts[j - 1], ts[j + 1] - ts[j]
        w1, w2 = 2.0 * h1 + h0, h1 + 2.0 * h0
        return (w1 + w2) / (w1 / a + w2 / b)

    def arrive(j):
        if j == n - 1:
            return 0.0
        if hit[j]:
            return 2.0 * d[j - 1]
        return through(j)

    def leave(j):
        if j == 0:
            return 0.0
        if hit[j]:
            if j < n - 1 and d[j] * d[j - 1] > 0.0:
                return math.copysign(min(abs(0.7 * d[j - 1]), abs(2.0 * d[j])), d[j])
            return 0.0
        return through(j)

    h = ts[i + 1] - ts[i]
    u = (t - ts[i]) / h
    m0, m1, dl = leave(i), arrive(i + 1), d[i]
    if dl == 0.0:
        m0 = m1 = 0.0
    else:
        a, b = max(0.0, m0 / dl), max(0.0, m1 / dl)
        if a * a + b * b > 9.0:
            if hit[i + 1]:
                a = min(a, math.sqrt(max(0.0, 9.0 - b * b)))
            else:
                s = 3.0 / math.sqrt(a * a + b * b)
                a, b = a * s, b * s
        m0, m1 = a * dl, b * dl
    u2, u3 = u * u, u * u * u
    return ((2.0 * u3 - 3.0 * u2 + 1.0) * vs[i] + (u3 - 2.0 * u2 + u) * h * m0 + (3.0 * u2 - 2.0 * u3) * vs[i + 1]
            + (u3 - u2) * h * m1)


def sh(keys, d):
    """The keys with every one but the first and the last moved `d` seconds later (a part that trails behind) or
    earlier (negative: a part that leads), kept inside them."""
    t0, t1 = keys[0][0], keys[-1][0]
    out = [keys[0]]
    last = t0
    for k in keys[1:-1]:
        t = min(max(k[0] + d, last + 0.01), t1 - 0.01)
        out.append((t,) + tuple(k[1:]))
        last = t
    out.append(keys[-1])
    return out


def record(rig, name, length, frame, loop=True):
    """Records an animation: frame(t) -> Pose every STEP, thinned to within BAKE of the frames."""
    a = Animation(name, length, True if loop else "hold_on_last_frame")
    n = int(round(length / STEP))
    for i in range(n + 1):
        a.add(i * STEP, frame(i * STEP))
    a.bake(*BAKE)
    return a


# ---------------------------------------------------------------- posing helpers (the Frostfolk's conventions)


def arm(p, side, fwd=0.0, out=0.0, twist=0.0):
    """Its arm swung `fwd` degrees forward (towards its face), `out` out to its side, turned `twist` about itself."""
    s = 1.0 if side == "l" else -1.0
    p.rot(f"arm_{side}", -fwd, twist * s, -out * s)


def wield(p, side, fwd, head=0.0, out=0.0, roll=0.0):
    """Its arm swung `fwd` forward and `out` out; what its hand holds turned so it leans `head` degrees forward of how it
    rests (whatever the arm does: 0 keeps it as it rests, 90 lays an upright weapon level pointing ahead)."""
    arm(p, side, fwd, out)
    s = 1.0 if side == "l" else -1.0
    p.rot(f"hand_{side}", head + fwd, 0.0, roll * s)


def lift_for(swing, hip):
    """How far a leg swung `swing` degrees sinks its sole into the floor (so the waist rises that much)."""
    a = math.radians(abs(swing))
    return hip * (1.0 - math.cos(a)) + 4.0 * math.sin(a)


def lunge(p, amount, hip):
    """A lunge: the right leg forward, the left back, its body leaning in; `amount` from 0 (standing) to 1."""
    sw = 22.0 * amount
    p.rot("leg_r", -sw, 0.0, 0.0)
    p.rot("leg_l", sw * 0.8, 0.0, 0.0)
    # (the first version sank 2.5 units and lifted it back by too little, so its boots sank into the floor; the rigid
    # legs spread need their hips about hip (1 - cos) higher, so it sinks only a little now)
    p.pos("waist", 0.0, -0.8 * amount + hip * (1.0 - math.cos(math.radians(sw))) * 0.9, -3.0 * amount)


def show(p, kn, shown=()):
    """Every one of its props hidden but `shown` (a name, or (name, scale))."""
    sizes = {}
    for x in shown:
        if isinstance(x, tuple):
            sizes[x[0]] = x[1]
        else:
            sizes[x] = 1.0
    for prop in kn.props:
        if prop in ("anchor", "cap"):
            continue                     # (always there: the game hides the anchor while it's thrown)
        if prop == "boulder":
            p.scale(prop, sizes.get(prop, 1.0))
        else:
            p.scale(prop, sizes.get(prop, 0.0))


def rest_arms(p, kn, d=0.0, dl=0.0):
    """Both arms as they rest, swung `d` (right) and `dl` (left) degrees further forward (the weapon kept upright)."""
    if kn.gear == "up":                 # both hands up over its head (the boulder)
        arm(p, "r", d * 0.3)
        arm(p, "l", d * 0.3)
        return
    wield(p, "r", d, 0.0)
    wield(p, "l", dl, 0.0)


def body(p, lean, twist, roll=0.0, steady=0.6, head_twist=0.5):
    """Its body leaned `lean` forward and twisted `twist`; the head steadies against it (it keeps looking ahead)."""
    p.rot("body", lean, twist, roll)
    p.rot("head", -lean * steady, -twist * head_twist, -roll * 0.6)


def cape(p, lean_late, extra=0.0):
    """The cape swings back towards hanging straight down as the body leans (`lean_late`: the lean a moment ago, so it
    trails), only part of the way, so it stays off the legs behind it (a negative turn swings its hem forwards)."""
    p.rot("cape", -0.3 * lean_late + extra, 0.0, 0.0)


def lead(kn):
    """How far ahead of the arm the body moves (a heavy knight's hips lead its blow a little more)."""
    return 0.035 + 0.025 * kn.heavy


# ---------------------------------------------------------------- aiming a weapon (its edge leading a sweep)
#
# The user, November 18, 2026: the scythe's blade hung straight down as it swept ("not vertical, it needs to be
# horizontal"), and the same for the Thawed axe, the Geode Knight's pick and the Cinder Knight's hammer: their swings
# should be flat, the blade, spike or hammer face leading into you. These work out, every frame, the hand rotation that
# points the haft where it's keyed (in the body's frame) and turns the weapon about it so its striking side faces the
# way the sweep goes.


def _g(v):
    """A design-space vector (front -z, its own left +x) in GeckoLib space (x flipped)."""
    return np.array([-v[0], v[1], v[2]], dtype=float)


def _unit(v):
    v = np.asarray(v, dtype=float)
    n = float(np.linalg.norm(v))
    return v / n if n > 1e-9 else v


def _frame3(a, b):
    """A right-handed frame whose first axis is `a` and second the part of `b` square to it."""
    a = _unit(a)
    b = _unit(np.asarray(b, dtype=float) - a * float(np.dot(a, b)))
    return np.column_stack([a, b, np.cross(a, b)])


def _euler(R, prev):
    """Bedrock angles (rx, ry, rz) with gecko_rotation(rx, ry, rz) == R, the one of the two solutions (and the turn of
    each angle) nearest `prev`, so an animation never flips."""
    sb = max(-1.0, min(1.0, -float(R[2, 0])))
    b = math.asin(sb)
    a = math.atan2(R[1, 0], R[0, 0])
    c = math.atan2(R[2, 1], R[2, 2])
    sols = [(a, b, c), (a + math.pi, math.pi - b, c + math.pi)]
    best = None
    for a_, b_, c_ in sols:
        e = [-math.degrees(c_), -math.degrees(b_), math.degrees(a_)]
        if prev is not None:
            e = [x + 360.0 * round((p - x) / 360.0) for x, p in zip(e, prev)]
            d = sum((x - p) ** 2 for x, p in zip(e, prev))
        else:
            d = sum(x * x for x in e)
        if best is None or d < best[0]:
            best = (d, e)
    return best[1]


def aim_hand(rig, p, side, haft_rest, head_rest, haft_body, lead_w, prev, two_faced=False, lead_sign=1.0):
    """
    Sets the hand's rotation in pose p so the weapon's haft (`haft_rest`, from its fist towards its head, in the rest
    pose) points along `haft_body` (in the body's frame) and its striking side (`head_rest`) turns towards the side the
    sweep goes, by `lead_w` from 0 (as little turn about the haft as can be from how it rests) to 1 (square on to it).
    `two_faced`: a hammer, either face will do. Returns the angles set (for `prev` next frame).
    """
    m = rig.m
    p.get3(f"hand_{side}")[0][:] = [0.0, 0.0, 0.0]
    mats = m.pose_matrices(p)
    B = mats["body"][:3, :3]
    P = mats[f"arm_{side}"][:3, :3]
    hr, er = _unit(haft_rest), _unit(head_rest)
    hb = _unit(haft_body)
    # the head's way with the least turn about the haft from rest (in the body's frame), then the sweep's way
    rot_min = _min_rotation(hr, hb)
    head0 = rot_min @ er
    hw_design = _ungecko(B @ _g(hb))
    lead = _unit(np.cross(hw_design, np.array([0.0, 1.0, 0.0]))) * lead_sign     # (its left, for a sweep to the left)
    lead_b = _ungecko(B.T @ _g(lead))
    if two_faced and float(np.dot(lead_b, head0)) < 0.0:
        lead_b = -lead_b
    head_b = _slerp_dir(head0 - hb * float(np.dot(head0, hb)), lead_b - hb * float(np.dot(lead_b, hb)), lead_w)
    W = B @ _frame3(_g(hb), _g(head_b))
    Rest = _frame3(_g(hr), _g(er))
    L = P.T @ W @ Rest.T
    e = _euler(L, prev)
    p.get3(f"hand_{side}")[0][:] = e
    return e


def _ungecko(v):
    return np.array([-v[0], v[1], v[2]], dtype=float)


def _min_rotation(a, b):
    """The rotation (3x3) taking unit a to unit b by the least turn."""
    v = np.cross(a, b)
    c = float(np.dot(a, b))
    if np.linalg.norm(v) < 1e-9:
        if c > 0.0:
            return np.eye(3)
        ax = _unit(np.cross(a, [1.0, 0.0, 0.0] if abs(a[0]) < 0.9 else [0.0, 1.0, 0.0]))
        return Rotation.from_rotvec(ax * math.pi).as_matrix()
    return Rotation.from_rotvec(_unit(v) * math.atan2(float(np.linalg.norm(v)), c)).as_matrix()


def _slerp_dir(a, b, w):
    """The way `w` (0..1) of the turn from a to b."""
    a, b = _unit(a), _unit(b)
    if w <= 0.0:
        return a
    if w >= 1.0:
        return b
    R = _min_rotation(a, b)
    rv = Rotation.from_matrix(R).as_rotvec()
    return Rotation.from_rotvec(rv * w).as_matrix() @ a


def vkeys(t, keys):
    """A direction from keys [(time, (x, y, z)), ...]: each part along its own smooth curve, then made unit length."""
    return _unit([curve(t, [(k[0], k[1][i]) + tuple(k[2:]) for k in keys]) for i in range(3)])


def flat_sweep(rig, kn, name, haft_rest, head_rest, T=1.3, hit=0.62, two_faced=False, lift=None, sweep=1.0):
    """
    A flat swing (the user: "not vertical, it needs to be horizontal"): the weapon lifted off its rest and drawn back
    level at his right side as his body winds up, then swept round flat in front of him at chest height, his hips
    first, the blade (spike, hammer face) square on to the way it goes, flowing on round to his left before he brings it
    back to rest. `lift`: a way the haft points on the way up off its rest (a weapon carried on the shoulder comes up
    over it first).
    """
    hv = kn.heavy
    ld = lead(kn)
    top = hit * 0.62
    hold = hit + 0.28 + 0.06 * hv
    hr = _unit(haft_rest)
    # the haft's way in the body's frame (front -z, his left +x): from rest, back level at his right, round to his left
    k_haft = [(0.0, tuple(hr))]
    if lift is not None:
        k_haft.append((top * 0.45, tuple(_unit(lift))))
    k_haft += [(top, (-0.05 * sweep, 0.14, -0.99)), (hit, (0.08 * sweep, -0.06, -1.0)),
               (hold, (0.5 * sweep, -0.12, -0.86)), (T - 0.12, tuple(_unit(hr + np.array([0.0, 0.0, -0.4])))),
               (T, tuple(hr))]
    k_w = [(0.0, 0.0), (top * 0.8, 1.0), (hold + 0.05, 1.0), (T - 0.08, 0.0), (T, 0.0)]
    k_tw = sh([(0.0, 0.0), (top - 0.06, 48.0 * sweep), (top + 0.04, 52.0 * sweep), (hit, -8.0 * sweep, "hit"),
               (hold, -50.0 * sweep), (hold + 0.1, -47.0 * sweep), (T, 0.0)], -ld)
    k_fwd = [(0.0, 0.0), (top, 40.0), (hit, 55.0), (hold, 45.0), (T, 0.0)]
    k_out = sh([(0.0, 0.0), (top, 42.0 * sweep), (hit, 6.0 * sweep), (hold, -24.0 * sweep), (T, 0.0)], 0.02)
    k_lfwd = sh([(0.0, 0.0), (top, 18.0), (hold, 38.0), (T, 0.0)], 0.06)
    k_lout = sh([(0.0, 0.0), (hold, 24.0), (T, 0.0)], 0.06)
    k_lean = sh([(0.0, 0.0), (top, 4.0), (hit, 8.0 + 3.0 * hv, "hit"), (hold, 6.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (top, 0.15), (hit, 0.7, "hit"), (hold, 0.6), (T, 0.0)], -ld * 0.5)
    state = {"prev": None}

    def frame(t):
        if t <= 0.0:
            state["prev"] = None
        p = Pose()
        arm(p, "r", curve(t, k_fwd), curve(t, k_out))
        wield(p, "l", curve(t, k_lfwd), 0.0, out=curve(t, k_lout))
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        state["prev"] = aim_hand(rig, p, "r", hr, head_rest, vkeys(t, k_haft), curve(t, k_w), state["prev"],
                                 two_faced, lead_sign=sweep)
        return p

    a = record(rig, name, T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


# ---------------------------------------------------------------- idle and walk


def idle(rig, kn):
    """Breathing (twice a loop), its weight shifting from foot to foot (once), a slow look round, the arms and the
    cape following the chest a moment later; seamless."""
    T = 4.0
    hv = kn.heavy

    def frame(t):
        p = Pose()
        w = 2.0 * math.pi * t / T
        b = math.sin(2.0 * w)                     # the breath
        late = math.sin(2.0 * w - 0.7)            # what follows the chest
        sway = math.sin(w)
        p.pos("body", 0.0, (0.25 + 0.15 * hv) * b, 0.0)
        p.rot("body", -(0.6 + 0.5 * hv) * b, 0.0, 0.0)
        p.pos("body", (0.25 + 0.15 * hv) * sway, 0.0, 0.0)      # (a roll would push a planted staff into the floor)
        look = math.sin(w + 0.9) * 0.85 + math.sin(2.0 * w + 0.3) * 0.15
        p.rot("head", 1.5 * b, (8.0 - 3.0 * hv) * look, -0.7 * sway)
        rest_arms(p, kn, 1.6 * late, -1.6 * late)
        p.rot("cape", -1.5 * late, 0.0, 0.0)
        show(p, kn)
        return p

    return record(rig, "idle", T, frame, loop=True)


# Each knight's own way of walking (the user, November 18, 2026: "add more personality to the walk animations per
# each element, or even each knight within an element"). A knight's `walk_style` (mobs.py) sets any of these, the rest
# keep these values:
#   bob, roll, twist, arms, nod   how much the body rises, rocks over its planted foot, twists with its hips, the arms
#                                 swing and the head nods each step (1: as before)
#   sway        units its hips shift over the planted foot (a sailor's or a giant's rolling gait)
#   limp        0..1: one stride shorter and lighter than the other (a hobble)
#   stomp       0..1: the foot comes down hard: the body drops onto it and jolts (a short dip then up)
#   hunch       degrees its shoulders roll forward over its chest (on top of its lean)
#   loll        degrees its head rolls slowly side to side (the Sporebound, its mind half the fungus's)
#   look        degrees its head turns to scan round as it goes (a hunter's walk)
#   shiver      degrees of a fast tremor in its head and arms (the Thawed, cold to the bone)
#   guard       a boxer's guard: both fists up before its chest, bouncing with each step (Flamehand)
#   staff       the right arm planting its staff like a walking stick with each left step (the Blight Warden)
#   blades_back the arms held back and low, blades trailing (the Stormblade's prowl)
#   cap_pulse   the Blightcap on its back swelling a little with each step (the Sporebound)
#   heave       the boulder overhead tilting and settling with each heavy step (the Rockfall Knight)
#   carry       degrees the weapon on its shoulder bounces with each step (the Wreck and Geode Knights)
WALK_STYLE = dict(bob=1.0, roll=1.0, twist=1.0, arms=1.0, nod=1.0, sway=0.0, limp=0.0, stomp=0.0, hunch=0.0,
                  loll=0.0, look=0.0, shiver=0.0, guard=False, staff=False, blades_back=False, cap_pulse=0.0, heave=0.0,
                  carry=0.0)


def walk(rig, kn, name="walk"):
    """Its walk: legs swung like the Frostfolk's, the body bobbing smoothly (highest as the legs pass, lowest as they
    spread), twisting with the hips and rocking over the planted foot (more, the heavier it is), the arms swinging a
    moment behind the legs, the head steady, the cape trailing; then its own character (`kn.walk_style`, WALK_STYLE
    above). Made at about the pace the knight really walks (`kn.walk_t`; the stride follows from its speed), seamless
    (everything repeats once or twice a loop)."""
    T = kn.walk_t
    hv = kn.heavy
    st = dict(WALK_STYLE)
    st.update(getattr(kn, "walk_style", None) or {})
    lean = kn.walk_lean + st["hunch"] * 0.35
    # the stride that covers its pace in one loop: each leg sweeps 2 hip sin(leg) a half loop
    s = min(0.6, 16.0 * kn.pace * T / (4.0 * kn.hip * kn.K))
    leg = math.degrees(math.asin(s))
    lift = lift_for(leg, kn.hip) * 0.55
    arm_r = 3.0 if kn.gear in ("shoulder", "up") else 9.0
    arm_l = 15.0 if kn.gear != "up" else 4.0
    if kn.gear == "fists":
        arm_r = arm_l = 18.0
    arm_r *= st["arms"]
    arm_l *= st["arms"]
    limp = st["limp"]

    def frame(t):
        p = Pose()
        w = 2.0 * math.pi * t / T
        sw = math.sin(w)
        # a limp: the right leg's stride (sw > 0 half) shorter than the left's
        k_r = 1.0 - 0.35 * limp if sw > 0.0 else 1.0
        p.rot("leg_r", -leg * sw * k_r, 0.0, 0.0)
        p.rot("leg_l", leg * sw * k_r, 0.0, 0.0)
        lift_now = lift_for(leg * abs(sw) * k_r, kn.hip) * 0.55
        rise = (0.5 + 0.5 * hv) * (1.0 - sw * sw) * st["bob"]
        # a stomp: the body drops onto the foot as it lands (sw * sw small) and jolts back up
        land = math.cos(2.0 * w)                     # 1 as the feet spread (a foot lands), -1 as they pass
        jolt = st["stomp"] * 0.9 * max(0.0, land) ** 6
        bob = lift_now + max(0.0, rise - jolt) + 0.25 * limp * max(0.0, sw)
        side = st["sway"] * math.sin(w - 0.3)
        p.pos("waist", side, bob, 0.0)
        a = math.sin(w - 0.45)                         # the arms, a moment behind the legs
        if st["guard"]:
            bounce = 4.0 * math.cos(2.0 * w - 0.4)
            arm(p, "r", 62.0 + bounce - 6.0 * a, -14.0)
            arm(p, "l", 66.0 + bounce + 6.0 * a, -16.0)
        elif st["staff"]:
            plant = math.sin(w - 0.2)                  # with the left leg: the staff goes forward as it steps
            wield(p, "r", 14.0 * max(0.0, -plant) - 6.0 * max(0.0, plant), 0.0)
            wield(p, "l", arm_l * a, 0.0)
        elif st["blades_back"]:
            wield(p, "r", -24.0 - 5.0 * a, 0.0, out=10.0)
            wield(p, "l", -18.0 + 5.0 * a, 0.0, out=8.0)
        else:
            rest_arms(p, kn, -arm_r * a, arm_l * a)
        if st["carry"]:
            p.rot("hand_r", -st["carry"] * max(0.0, land) ** 2, 0.0, 0.0)
        roll = (1.0 + 2.0 * hv) * st["roll"] * math.sin(w - 0.3)    # over the planted foot
        twist = (3.0 + 1.5 * hv) * st["twist"] * math.sin(w - 0.15)
        dip = (0.8 + 0.8 * hv) * st["nod"] * math.cos(2.0 * w) + 3.0 * jolt     # a little nod with each step
        body(p, lean + dip, twist, roll, steady=0.7, head_twist=0.8)
        if st["hunch"]:
            p.rot("head", -st["hunch"] * 0.5, 0.0, 0.0)               # (it keeps looking ahead)
        if st["loll"]:
            p.rot("head", 0.0, 0.3 * st["loll"] * math.sin(w + 1.0), st["loll"] * math.sin(w + 0.6))
        if st["look"]:
            p.rot("head", 0.0, st["look"] * math.sin(w + 0.4), 0.0)
        if st["shiver"]:
            q = st["shiver"] * math.sin(8.0 * w)
            p.rot("head", 0.4 * q, 0.6 * q, 0.0)
            p.rot("arm_l", 0.0, 0.0, 0.5 * q)
        if st["cap_pulse"] and "cap" in kn.props:
            p.scale("cap", 1.0 + st["cap_pulse"] * (0.5 + 0.5 * math.cos(2.0 * w - 0.8)))
        if st["heave"] and kn.gear == "up":
            h = st["heave"]
            p.rot("arm_r", -h * 0.5 * land, 0.0, h * math.sin(w - 0.3))
            p.rot("arm_l", -h * 0.5 * land, 0.0, h * math.sin(w - 0.3))
        # the cape drags out behind it as it walks (the first walk turned it the other way, into its legs)
        p.rot("cape", 3.0 - 0.3 * lean + (1.5 + 0.5 * hv) * (1.0 + math.cos(2.0 * w - 1.2)), 0.0,
              -0.5 * roll - 0.04 * side)
        show(p, kn)
        return p

    a = record(rig, name, T, frame, loop=True)
    reach = 4.0 * kn.hip * math.sin(math.radians(leg)) * kn.K
    a.meta["speed"] = reach / T / 16.0
    return a


# ---------------------------------------------------------------- their attacks


def chop(rig, kn, name="swing", T=1.15, hit=0.5, raise_to=125.0, strike=40.0, head_back=-35.0, head_hit=105.0):
    """A one-handed blow: the weapon raised high, its head back over its shoulder (hanging there a moment), brought
    down ahead with the body turning into it first, carrying on a little past the hit, then back to rest."""
    hv = kn.heavy
    top = hit * 0.62
    ld = lead(kn)
    hold = hit + 0.22 + 0.1 * hv
    k_fwd = [(0.0, 0.0), (top - 0.08, raise_to * 0.9), (top + 0.02, raise_to), (hit, strike, "hit"),
             (hit + 0.08, strike - 6.0), (hold, strike - 3.0), (T, 0.0)]
    k_head = [(0.0, 0.0), (top + 0.04, head_back), (hit, head_hit, "hit"), (hit + 0.08, head_hit + 5.0),
              (hold, head_hit + 2.0), (T, 0.0)]
    k_out = [(0.0, 0.0), (top, 12.0), (hit, -6.0), (hold, -5.0), (T, 0.0)]
    k_left = sh([(0.0, 0.0), (top, 18.0), (hit, -12.0), (hold, -8.0), (T, 0.0)], 0.07)
    k_tw = sh([(0.0, 0.0), (top, 16.0), (hit, -14.0, "hit"), (hold, -10.0), (T - 0.16, 1.5), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (top, -6.0 - 2.0 * hv), (hit, 12.0 + 4.0 * hv, "hit"), (hold, 10.0 + 3.0 * hv),
                 (T - 0.16, -1.5), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (top, 0.2 + 0.15 * hv), (hit, 1.0, "hit"), (hold, 0.95), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        wield(p, "r", curve(t, k_fwd), curve(t, k_head), out=curve(t, k_out))
        wield(p, "l", curve(t, k_left), 0.0)
        body(p, curve(t, k_lean), curve(t, k_tw), steady=0.7)
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, name, T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


def overhead_slam(rig, kn, name="smash", T=1.85, hit=1.0, head_hit=150.0, both=False, strike=58.0):
    """Its weapon raised high over its head, hanging there a moment at the top, and slammed into the ground in front of
    it (a small recoil as it bites), held there, then hauled back up (the weapon's weight following the arm)."""
    hv = kn.heavy
    top = 0.68
    ld = lead(kn)
    hold = hit + 0.22 + 0.08 * hv
    mid = hold + 0.45 * (T - hold)
    k_fwd = [(0.0, 0.0), (0.45, 135.0), (top, 165.0), (0.79, 172.0), (hit, strike, "hit"), (hit + 0.07, strike + 2.0),
             (hold, strike), (mid, strike * 0.5), (T, 0.0)]
    k_head = [(0.0, 0.0), (0.45, -40.0), (top, -52.0), (0.8, -58.0), (hit, head_hit, "hit"),
              (hit + 0.07, head_hit - 3.0), (hold, head_hit - 2.0), (mid + 0.05, head_hit * 0.55), (T, 0.0)]
    k_lean = sh([(0.0, 0.0), (top, -10.0 - 3.0 * hv), (0.79, -12.0 - 3.0 * hv), (hit, 22.0 + 4.0 * hv, "hit"),
                 (hold, 20.0 + 3.0 * hv), (T - 0.2, -2.0), (T, 0.0)], -ld)
    k_crouch = sh([(0.0, 0.0), (0.79, 0.05), (hit, 1.0, "hit"), (hold, 1.0), (T - 0.25, 0.3), (T, 0.0)], -ld * 0.5)
    k_lfwd = sh([(0.0, 0.0), (top, 30.0), (hit, -15.0), (hold, -10.0), (T, 0.0)], 0.07)
    k_lout = sh([(0.0, 0.0), (top, 20.0), (hit, 6.0), (T, 0.0)], 0.07)
    k_both_out = [(0.0, 0.0), (top, -18.0), (hit, -12.0), (hold, -12.0), (T, 0.0)]

    def frame(t):
        p = Pose()
        fwd = curve(t, k_fwd)
        wield(p, "r", fwd, curve(t, k_head))
        if both:
            wield(p, "l", fwd * 0.92, 0.0, out=curve(t, k_both_out))
        else:
            wield(p, "l", curve(t, k_lfwd), 0.0, out=curve(t, k_lout))
        body(p, curve(t, k_lean), 0.0)
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_crouch), kn.hip)
        show(p, kn)
        return p

    a = record(rig, name, T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


def thrust(rig, kn, name="thrust", T=1.15, hits=(0.45,), level=88.0, back=18.0, out_to=70.0):
    """A spear levelled, drawn back a little further (the cock), and driven forward, the body and the front leg going
    in with it, carrying on a touch past the hit, then drawn back."""
    h = hits[0]
    ld = lead(kn)
    k_fwd = [(0.0, 0.0), (h - 0.22, back), (h - 0.1, back - 5.0), (h, out_to, "hit"), (h + 0.07, out_to + 3.0),
             (h + 0.3, back + 12.0), (T, 0.0)]
    k_head = [(0.0, 0.0), (h - 0.14, level), (h + 0.36, level), (T, 0.0)]
    k_tw = sh([(0.0, 0.0), (h - 0.1, 10.0), (h, -14.0, "hit"), (h + 0.25, -8.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (h - 0.1, -2.0), (h, 7.0, "hit"), (h + 0.3, 4.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (h - 0.1, 0.12), (h, 0.85, "hit"), (h + 0.25, 0.75), (T, 0.0)], -ld * 0.5)
    k_left = sh([(0.0, 0.0), (0.25, 22.0), (T - 0.25, 18.0), (T, 0.0)], 0.05)

    def frame(t):
        p = Pose()
        wield(p, "r", curve(t, k_fwd), curve(t, k_head))
        wield(p, "l", curve(t, k_left), 0.0)
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, name, T, frame, loop=False)
    a.meta["hits"] = list(hits)
    return a


def shield_wall(rig, kn):
    """9D: his shield raised in front of him (he takes far less from the front) while his spear jabs past it, again and
    again, his body rocking with each jab."""
    T = 2.6
    hits = (0.75, 1.1, 1.45, 1.8, 2.15)
    k_fwd = [(0.0, 0.0), (0.45, 22.0)]
    k_tw = [(0.0, 0.0), (0.45, 10.0)]
    for h in hits:
        k_fwd += [(h - 0.1, 19.0), (h, 62.0, "hit"), (h + 0.06, 64.0), (h + 0.2, 30.0)]
        k_tw += [(h - 0.1, 12.0), (h, 5.0, "hit"), (h + 0.2, 9.0)]
    k_fwd += [(T, 0.0)]
    k_tw += [(T, 0.0)]
    k_up = [(0.0, 0.0), (0.42, 1.0), (T - 0.3, 1.0), (T, 0.0)]
    k_level = [(0.0, 0.0), (0.32, 88.0), (T - 0.22, 88.0), (T, 0.0)]
    k_shield = sh(k_up, 0.04)

    def frame(t):
        p = Pose()
        up = curve(t, k_up)
        wield(p, "r", curve(t, k_fwd), curve(t, k_level), out=-6.0 * up)
        s = curve(t, k_shield)
        wield(p, "l", 38.0 * s, 0.0, out=-16.0 * s)        # the shield up before his chest, facing ahead
        body(p, 8.0 * up, curve(t, k_tw), steady=0.75, head_twist=0.8)
        cape(p, 8.0 * curve(t - 0.1, k_up))
        lunge(p, 0.45 * up, kn.hip)
        show(p, kn)
        return p

    a = record(rig, "shield_wall", T, frame, loop=False)
    a.meta["hits"] = list(hits)
    a.meta["guard"] = [0.4, T - 0.25]
    return a


def cast(rig, kn):
    """P1: his staff drawn back, then whipped forward, its globe at you, a plague ball flung from it at the snap."""
    T = 1.3
    rel = 0.75
    ld = lead(kn)
    k_fwd = [(0.0, 0.0), (0.38, -12.0), (0.5, -17.0), (rel, 62.0, "hit"), (rel + 0.08, 64.0), (rel + 0.25, 56.0),
             (T, 0.0)]
    k_head = [(0.0, 0.0), (0.4, -16.0), (0.52, -22.0), (rel, 55.0, "hit"), (rel + 0.08, 56.0), (rel + 0.25, 50.0),
              (T, 0.0)]
    k_lfwd = sh([(0.0, 0.0), (0.45, 30.0), (rel, -20.0), (rel + 0.25, -14.0), (T, 0.0)], 0.05)
    k_lout = sh([(0.0, 0.0), (0.45, 15.0), (rel, 4.0), (T, 0.0)], 0.05)
    k_tw = sh([(0.0, 0.0), (0.45, 18.0), (rel, -10.0, "hit"), (rel + 0.25, -7.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (0.45, -6.0), (rel, 10.0, "hit"), (rel + 0.3, 7.0), (T - 0.15, -1.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (0.45, 0.1), (rel, 0.5, "hit"), (rel + 0.3, 0.45), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        wield(p, "r", curve(t, k_fwd), curve(t, k_head))
        wield(p, "l", curve(t, k_lfwd), 0.0, out=curve(t, k_lout))
        body(p, curve(t, k_lean), curve(t, k_tw), steady=0.5, head_twist=0.6)
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, "cast", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def staff_raise(rig, kn, name="blight_rain", T=2.2, hit=1.25):
    """P1: his staff raised high over his head, its globe glowing, held up a moment, and driven down into the ground:
    poison bursts up and rains round him."""
    ld = lead(kn)
    k_fwd = [(0.0, 0.0), (0.5, 140.0), (0.62, 150.0), (0.98, 160.0), (hit, 25.0, "hit"), (hit + 0.06, 28.0),
             (hit + 0.5, 25.0), (T, 0.0)]
    k_head = [(0.0, 0.0), (0.5, -140.0), (0.62, -150.0), (0.98, -160.0), (hit, -25.0, "hit"), (hit + 0.06, -27.0),
              (hit + 0.5, -20.0), (T, 0.0)]
    k_lfwd = sh([(0.0, 0.0), (0.6, 70.0), (hit - 0.1, 72.0), (hit, 8.0), (hit + 0.5, 6.0), (T, 0.0)], 0.04)
    k_lout = sh([(0.0, 0.0), (0.6, 55.0), (hit - 0.1, 57.0), (hit, 12.0), (hit + 0.5, 10.0), (T, 0.0)], 0.04)
    k_lean = sh([(0.0, 0.0), (0.6, -10.0), (0.98, -12.0), (hit, 14.0, "hit"), (hit + 0.5, 12.0), (T - 0.2, -1.0),
                 (T, 0.0)], -ld)
    k_look = sh([(0.0, 0.0), (0.6, -8.0), (hit - 0.1, -9.0), (hit, 0.0), (T, 0.0)], 0.03)
    k_lunge = sh([(0.0, 0.0), (hit - 0.2, 0.0), (hit, 0.8, "hit"), (hit + 0.5, 0.8), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        wield(p, "r", curve(t, k_fwd), curve(t, k_head))
        wield(p, "l", curve(t, k_lfwd), 0.0, out=curve(t, k_lout))
        lean = curve(t, k_lean)
        p.rot("body", lean, 0.0, 0.0)
        p.rot("head", -lean * 0.9 + curve(t, k_look), 0.0, 0.0)
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, name, T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


def reap(rig, kn):
    """P3: his scythe swung flat round in front of him, its blade at chest height: a long wind-up, then one wide sweep
    that flows through and round to his other side before he draws it back."""
    T = 1.3
    hit = 0.65
    ld = lead(kn)
    k_tw = sh([(0.0, 0.0), (0.36, 52.0), (0.44, 56.0), (hit, -10.0), (0.84, -55.0), (0.96, -52.0), (T, 0.0)], -ld)
    k_fwd = [(0.0, 0.0), (0.4, 45.0), (0.85, 50.0), (T, 0.0)]
    k_head = [(0.0, 0.0), (0.4, 75.0), (hit, 80.0), (0.85, 80.0), (T, 0.0)]
    k_out = sh([(0.0, 0.0), (0.42, 40.0), (hit, 15.0), (0.86, -20.0), (0.97, -18.0), (T, 0.0)], 0.02)
    k_lfwd = sh([(0.0, 0.0), (0.4, 20.0), (0.85, 40.0), (T, 0.0)], 0.05)
    k_lout = sh([(0.0, 0.0), (0.85, 25.0), (T, 0.0)], 0.05)
    k_lean = sh([(0.0, 0.0), (0.4, 5.0), (0.7, 7.0), (0.95, 5.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (0.35, 0.1), (hit, 0.5), (0.9, 0.45), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        wield(p, "r", curve(t, k_fwd), curve(t, k_head), out=curve(t, k_out))
        wield(p, "l", curve(t, k_lfwd), 0.0, out=curve(t, k_lout))
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, "reap", T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


def spore_burst(rig, kn):
    """P3: he hunches, the Blightcap on his back throbbing as it swells, and it bursts its spores out round him with a
    jolt that runs through him."""
    T = 2.0
    hit = 1.1
    k_hunch = [(0.0, 0.0), (0.7, 0.9), (0.95, 1.0), (hit, 1.15, "hit"), (hit + 0.15, 0.95), (1.5, 0.9), (T, 0.0)]
    k_swell = [(0.0, 1.0), (0.5, 1.1), (0.6, 1.07), (0.8, 1.22), (0.88, 1.19), (hit, 1.34, "hit"), (hit + 0.1, 0.93),
               (hit + 0.25, 1.03), (1.5, 1.0), (T, 1.0)]
    k_arms = sh(k_hunch, 0.06)

    def frame(t):
        p = Pose()
        hunch = curve(t, k_hunch)
        p.rot("body", 24.0 * hunch, 0.0, 0.0)
        p.rot("head", -8.0 * hunch, 0.0, 0.0)
        p.scale("cap", curve(t, k_swell))
        a = curve(t, k_arms)
        wield(p, "r", 25.0 * a, 0.0, out=20.0 * a)
        wield(p, "l", 25.0 * a, 0.0, out=30.0 * a)
        cape(p, 24.0 * curve(t - 0.1, k_hunch))
        lunge(p, 0.6 * min(1.0, hunch), kn.hip)
        show(p, kn)
        return p

    a = record(rig, "spore_burst", T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


def hammer_swing(rig, kn):
    """F1B: his hammer hauled back, hanging there as the weight turns, then swung round flat across in front of him,
    his hips first, carried on round with its weight before he brings it back."""
    T = 1.2
    hit = 0.6
    ld = lead(kn)
    k_tw = sh([(0.0, 0.0), (0.32, 46.0), (0.4, 52.0), (hit, -15.0), (0.82, -44.0), (0.95, -42.0), (T, 0.0)], -ld)
    k_fwd = [(0.0, 0.0), (0.4, 60.0), (0.85, 70.0), (T, 0.0)]
    k_head = [(0.0, 0.0), (0.4, 60.0), (hit, 80.0), (0.85, 85.0), (T, 0.0)]
    k_out = sh([(0.0, 0.0), (0.4, 50.0), (hit, 10.0), (0.85, -25.0), (0.96, -23.0), (T, 0.0)], 0.02)
    k_left = sh([(0.0, 0.0), (0.4, 20.0), (0.85, 35.0), (T, 0.0)], 0.06)
    k_lean = sh([(0.0, 0.0), (0.4, 4.0), (hit, 9.0), (0.9, 7.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (0.4, 0.15), (hit, 0.75), (0.92, 0.7), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        wield(p, "r", curve(t, k_fwd), curve(t, k_head), out=curve(t, k_out))
        wield(p, "l", curve(t, k_left), 0.0)
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, "swing", T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a


def punch(rig, kn):
    """F5: a straight punch with his burning right fist, his shoulder turning into it, a fireball flung from it."""
    T = 0.9
    rel = 0.4
    ld = lead(kn)
    k_rfwd = [(0.0, 0.0), (0.22, -22.0), (0.27, -26.0), (rel, 28.0, "hit"), (rel + 0.06, 32.0), (0.58, 25.0),
              (T, 0.0)]
    k_rout = [(0.0, 0.0), (0.25, 10.0), (rel, -12.0), (0.6, -8.0), (T, 0.0)]
    k_lfwd = sh([(0.0, 0.0), (0.25, 12.0), (rel, -15.0), (0.6, -10.0), (T, 0.0)], 0.04)
    k_tw = sh([(0.0, 0.0), (0.25, 20.0), (rel, -18.0, "hit"), (0.6, -12.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (0.25, -3.0), (rel, 8.0, "hit"), (0.6, 6.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (0.25, 0.1), (rel, 0.8, "hit"), (0.6, 0.7), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        arm(p, "r", curve(t, k_rfwd), curve(t, k_rout))
        arm(p, "l", curve(t, k_lfwd))
        body(p, curve(t, k_lean), curve(t, k_tw), head_twist=0.6)
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, "fireball", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def great_fireball(rig, kn):
    """F5: both burning fists raised over his head, a great fireball growing and pulsing between them, drawn back a
    little further, then hurled at you."""
    T = 2.6
    rel = 1.9
    ld = lead(kn)
    k_fwd = [(0.0, 0.0), (0.5, 150.0), (0.6, 158.0), (1.3, 162.0), (1.62, 172.0), (rel, 62.0, "hit"),
             (rel + 0.08, 58.0), (2.25, 20.0), (T, 0.0)]
    k_out = [(0.0, 0.0), (0.5, -22.0), (1.62, -25.0), (rel, -2.0), (2.2, -1.0), (T, 0.0)]
    k_lean = sh([(0.0, 0.0), (0.5, -10.0), (1.62, -15.0), (rel, 18.0, "hit"), (rel + 0.3, 15.0), (T - 0.2, -1.5),
                 (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (1.62, 0.0), (rel, 0.9, "hit"), (rel + 0.3, 0.88), (T, 0.0)], -ld * 0.5)
    k_size = [(0.4, 0.0), (1.5, 1.0), (rel - 0.05, 1.1)]

    def frame(t):
        p = Pose()
        fwd, out = curve(t, k_fwd), curve(t, k_out)
        arm(p, "r", fwd, out=out)
        arm(p, "l", fwd, out=out)
        lean = curve(t, k_lean)
        p.rot("body", lean, 0.0, 0.0)
        p.rot("head", -lean * 0.8, 0.0, 0.0)
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        size = curve(t, k_size) * (1.0 + 0.05 * math.sin(2.0 * math.pi * t / 0.45))
        show(p, kn, (("orb", size),) if 0.4 < t < rel else ())
        return p

    a = record(rig, "great_fireball", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def anchor_throw(rig, kn):
    """T3A: the anchor swung back off his shoulder (his weight going back with it) and hurled at you on its chain; then
    he hauls it back in, hand over hand, leaning back into each pull (the game hides the anchor on him while it's
    out)."""
    T = 2.4
    rel = 0.7
    ld = lead(kn)
    k_fwd = [(0.0, 0.0), (0.3, -16.0), (0.42, -22.0), (rel, 115.0, "hit"), (rel + 0.08, 120.0), (rel + 0.25, 95.0),
             (1.2, 60.0), (1.45, 85.0), (1.7, 45.0), (1.95, 75.0), (T, 0.0)]
    k_head = [(0.0, 0.0), (0.42, 14.0), (rel, 30.0, "hit"), (rel + 0.1, 32.0), (1.2, 28.0), (T, 0.0)]
    k_lfwd = sh([(0.0, 0.0), (0.4, 25.0), (rel, 20.0), (rel + 0.25, 40.0), (1.2, 75.0), (1.45, 45.0), (1.7, 70.0),
                 (1.95, 40.0), (T, 0.0)], 0.03)
    k_lout = [(0.0, 0.0), (0.3, -10.0), (2.1, -10.0), (T, 0.0)]
    k_tw = sh([(0.0, 0.0), (0.3, 24.0), (0.42, 30.0), (rel, -20.0, "hit"), (1.0, -5.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (0.42, -9.0), (rel, 14.0, "hit"), (1.0, -5.0), (1.2, -3.0), (1.45, -8.0), (1.7, -3.0),
                 (1.95, -8.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (0.42, 0.25), (rel, 1.0, "hit"), (1.0, 0.5), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        wield(p, "r", curve(t, k_fwd), curve(t, k_head))
        wield(p, "l", curve(t, k_lfwd), 0.0, out=curve(t, k_lout))
        tw, lean = curve(t, k_tw), curve(t, k_lean)
        p.rot("body", lean, tw, 0.0)
        p.rot("head", -lean * 0.5, -tw * 0.4, 0.0)
        cape(p, curve(t - 0.12, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, "anchor_throw", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def slash(rig, kn):
    """T6: a quick slash with his left sword across, flowing straight into his reversed right blade back the other way,
    his body whipping from one to the other."""
    T = 0.8
    hits = (0.28, 0.52)
    ld = lead(kn)
    k_lfwd = [(0.0, 0.0), (0.14, 40.0), (hits[0], 25.0, "hit"), (0.36, 20.0), (0.45, 10.0), (T, 0.0)]
    k_lhead = [(0.0, 0.0), (0.14, -20.0), (hits[0], 30.0, "hit"), (0.36, 33.0), (T, 0.0)]
    k_lout = [(0.0, 0.0), (0.14, 35.0), (hits[0], -30.0, "hit"), (0.36, -34.0), (0.45, -20.0), (T, 0.0)]
    k_rfwd = [(0.0, 0.0), (0.32, 30.0), (hits[1], 60.0, "hit"), (0.58, 63.0), (0.65, 50.0), (T, 0.0)]
    k_rout = [(0.0, 0.0), (0.32, -25.0), (hits[1], 45.0, "hit"), (0.58, 48.0), (0.65, 40.0), (T, 0.0)]
    k_tw = sh([(0.0, 0.0), (0.14, -20.0), (hits[0], 18.0, "hit"), (0.36, 18.0), (hits[1], -24.0, "hit"), (0.62, -20.0),
               (T, 0.0)], -0.02)
    k_lean = [(0.0, 0.0), (0.15, 10.0), (0.62, 10.0), (T, 0.0)]
    k_lunge = [(0.0, 0.0), (0.2, 0.8), (0.6, 0.8), (T, 0.0)]

    def frame(t):
        p = Pose()
        wield(p, "l", curve(t, k_lfwd), curve(t, k_lhead), out=curve(t, k_lout))
        wield(p, "r", curve(t, k_rfwd), 0.0, out=curve(t, k_rout))
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.08, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        return p

    a = record(rig, "slash", T, frame, loop=False)
    a.meta["hits"] = list(hits)
    return a


def dash(rig, kn):
    """T6: the lightning dash: low and leaning far forward, both blades swept back, legs in full stride, the body
    bobbing smoothly with each stride (the game carries him along a trail of lightning; it loops while he goes)."""
    T = 0.4
    leg = 38.0
    lift = kn.hip * (1.0 - math.cos(math.radians(leg))) * 0.9

    def frame(t):
        p = Pose()
        w = 2.0 * math.pi * t / T
        sw = math.sin(w)
        p.rot("leg_r", -leg * sw, 0.0, 0.0)
        p.rot("leg_l", leg * sw, 0.0, 0.0)
        p.pos("waist", 0.0, lift * sw * sw - 1.5, 0.0)
        bob = math.cos(2.0 * w - 0.5)
        p.rot("body", 30.0 + 2.0 * bob, 3.0 * math.sin(w - 0.2), 0.0)
        p.rot("head", -26.0 - 2.0 * bob, -2.0 * math.sin(w - 0.2), 0.0)
        a = math.sin(w - 0.5)
        wield(p, "r", -40.0 + 3.0 * a, 20.0, out=15.0)
        wield(p, "l", -33.0 - 2.0 * a, 60.0, out=12.0)
        p.rot("cape", -20.0 - 3.0 * math.cos(2.0 * w - 1.2), 0.0, 0.0)
        show(p, kn)
        return p

    return record(rig, "dash", T, frame, loop=True)


def pick_strike(rig, kn):
    """E3: his war pick swung down off his shoulder onto you."""
    return chop(rig, kn, name="pick", T=1.25, hit=0.55, raise_to=110.0, strike=35.0, head_back=-20.0, head_hit=120.0)


def boulder_throw(rig, kn):
    """E4: the boulder drawn back over his head, his weight sinking back, and heaved forward; a new one grows in his
    hands, stone knitting together."""
    T = 2.0
    rel = 0.7
    ld = lead(kn)
    k_fwd = [(0.0, 0.0), (0.32, -24.0), (0.46, -32.0), (rel, 55.0, "hit"), (rel + 0.08, 60.0), (0.95, 60.0),
             (1.4, 5.0), (T, 0.0)]
    k_lean = sh([(0.0, 0.0), (0.46, -13.0), (rel, 18.0, "hit"), (1.0, 12.0), (1.5, 2.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (0.46, 0.25), (rel, 1.0, "hit"), (1.0, 0.8), (T, 0.0)], -ld * 0.5)
    k_size = [(1.15, 0.0), (1.75, 1.05), (1.85, 1.0)]

    def frame(t):
        p = Pose()
        fwd = curve(t, k_fwd)
        arm(p, "r", fwd)
        arm(p, "l", fwd)
        lean = curve(t, k_lean)
        p.rot("body", lean, 0.0, 0.0)
        p.rot("head", -lean * 0.6, 0.0, 0.0)
        cape(p, curve(t - 0.12, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        size = 1.0 if t < rel else curve(t, k_size)
        show(p, kn, (("boulder", size),))
        return p

    a = record(rig, "throw", T, frame, loop=False)
    a.meta["release"] = rel
    return a


def boulder_smash(rig, kn):
    """E4: the boulder raised higher still, held there a heartbeat, and brought down on the ground in front of him (a
    small bounce as it lands): the ground shakes and rocks fall from the sky round you."""
    T = 1.9
    hit = 1.05
    ld = lead(kn)
    k_fwd = [(0.0, 0.0), (0.45, -12.0), (0.62, -18.0), (0.8, -20.0), (hit, 125.0, "hit"), (hit + 0.06, 121.0),
             (hit + 0.4, 120.0), (T, 0.0)]
    k_lean = sh([(0.0, 0.0), (0.62, -10.0), (0.8, -12.0), (hit, 30.0, "hit"), (hit + 0.4, 28.0), (T - 0.25, -2.0),
                 (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (0.8, 0.0), (hit, 1.0, "hit"), (hit + 0.4, 1.0), (T, 0.0)], -ld * 0.5)
    k_sink = sh([(0.0, 0.0), (0.8, 0.0), (hit, -0.8, "hit"), (hit + 0.4, -0.8), (T, 0.0)], -ld * 0.5)

    def frame(t):
        p = Pose()
        fwd = curve(t, k_fwd)
        arm(p, "r", fwd)
        arm(p, "l", fwd)
        lean = curve(t, k_lean)
        p.rot("body", lean, 0.0, 0.0)
        p.rot("head", -lean * 0.7, 0.0, 0.0)
        cape(p, curve(t - 0.12, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        p.pos("waist", 0.0, curve(t, k_sink), 0.0)
        show(p, kn)
        return p

    a = record(rig, "rockfall", T, frame, loop=False)
    a.meta["hits"] = [hit]
    return a
