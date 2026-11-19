"""
The elbows: arms that reach (the user, November 19, 2026, after watching the reworked moves).

What the user saw: the Geode Knight's pick strike "just doesn't look right ... his arm needs to pull and extend itself",
the Cinder Knight should "use both arms when swinging", the Sporebound "both hands and hold the scythe better", the
Thawed axeman's "arms are so weird when he swings", the Stormblade's cuts "clunky".

Every arm was one stiff piece from the shoulder to the fist (its elbow fixed as the design bends it), swung from the
shoulder. Now each has an elbow (mobs.split_arms: the forearm on a bone of its own, fore_r/fore_l), and these put a fist
where it's wanted: the upper arm and the forearm turned (two-bone IK) so the fist lands on the spot, the elbow bending
towards a given way. A two-handed weapon's left fist goes on its haft, below the right, wherever the right hand takes it.

Spots are given as offsets from between its shoulders in arm's reaches (x its left, y up, z back: front is -z), so one
set of keys fits every knight's size.
"""
import math

import numpy as np

from rimekit.mobkit import Pose

from .kanims import (STEP, _euler, _frame3, _g, _min_rotation, _slerp_dir, _ungecko, _unit, body, cape, curve, lead,
                     lunge, record, sh, show, vkeys)


# ---------------------------------------------------------------- the arm's geometry


def rest_arm(rig, side):
    """Its shoulder, elbow and fist (model units, at rest), and its reach (upper arm + forearm)."""
    m = rig.m
    S, E, F = (np.array(m[b].pivot, dtype=float) for b in (f"arm_{side}", f"fore_{side}", f"hand_{side}"))
    return S, E, F, float(np.linalg.norm(E - S) + np.linalg.norm(F - E))


def rest_pole(rig, side):
    """The way its elbow points at rest, square to its shoulder-to-fist line (the body's frame)."""
    S, E, F, _r = rest_arm(rig, side)
    u = _unit(F - S)
    return _unit(_perp(E - S, u))


def frame_of(rig):
    """Between its shoulders (model units, at rest) and its arms' mean reach: the origin and unit of the spots."""
    Sr, _e, _f, rr = rest_arm(rig, "r")
    Sl, _e, _f, rl = rest_arm(rig, "l")
    return 0.5 * (Sr + Sl), 0.5 * (rr + rl)


def body_point(rig, mats, q):
    """A point fixed to the body (given where it is at rest, model units) where the body is now."""
    return np.asarray(rig.m.point(mats, "body", tuple(float(x) for x in q)), dtype=float)


def _perp(v, axis):
    v = np.asarray(v, dtype=float)
    return v - axis * float(np.dot(v, axis))


def _smootherstep(u):
    u = min(max(u, 0.0), 1.0)
    return u * u * u * (u * (6.0 * u - 15.0) + 10.0)


def pkeys(t, keys):
    """A point from keys [(time, (x, y, z)), ...], each part along its own smooth curve."""
    return np.array([curve(t, [(k[0], k[1][i]) + tuple(k[2:]) for k in keys]) for i in range(3)])


# ---------------------------------------------------------------- reaching


def arm_ik(rig, p, side, target, pole, prev=None):
    """
    Turns arm_{side} and fore_{side} in pose p so the fist reaches `target` (model units, where the body is now; as near
    as the arm reaches), its elbow bending towards `pole` (a way in the body's frame). Returns the angles set (pass them
    back as `prev` next frame, so nothing flips).
    """
    m = rig.m
    ab, fb, hb = f"arm_{side}", f"fore_{side}", f"hand_{side}"
    p.get3(ab)[0][:] = [0.0, 0.0, 0.0]
    p.get3(fb)[0][:] = [0.0, 0.0, 0.0]
    mats = m.pose_matrices(p)
    S0, E0, F0, _r = rest_arm(rig, side)
    S = np.asarray(m.point(mats, ab, tuple(S0)), dtype=float)
    E = np.asarray(m.point(mats, fb, tuple(E0)), dtype=float)
    F = np.asarray(m.point(mats, hb, tuple(F0)), dtype=float)
    la, lb = float(np.linalg.norm(E - S)), float(np.linalg.norm(F - E))
    B = mats["body"][:3, :3]
    T = np.asarray(target, dtype=float)
    u = _unit(T - S)
    # (a target past its reach is eased in, not cut off: a fist dragged straight to the end of a dead straight arm, then
    # let go, jolts)
    d = soft_cap(max(float(np.linalg.norm(T - S)), abs(la - lb) + 0.05), la + lb - 0.05, 0.05 * (la + lb))
    v = _perp(_ungecko(B @ _g(_unit(pole))), u)
    if np.linalg.norm(v) < 1e-6:
        v = _perp(E - S, u)
    v = _unit(v)
    x = (la * la - lb * lb + d * d) / (2.0 * d)
    h = math.sqrt(max(la * la - x * x, 0.0))
    E1 = S + u * x + v * h
    T1 = S + u * d
    R = _frame3(_g(E1 - S), _g(T1 - S)) @ _frame3(_g(E - S), _g(F - S)).T
    ea = _euler(B.T @ R @ B, prev[0] if prev else None)
    p.get3(ab)[0][:] = ea
    mats = m.pose_matrices(p)
    A = mats[ab][:3, :3]
    E2 = np.asarray(m.point(mats, fb, tuple(E0)), dtype=float)
    F2 = np.asarray(m.point(mats, hb, tuple(F0)), dtype=float)
    Rf = _frame3(_g(T1 - E2), _g(S - E2)) @ _frame3(_g(F2 - E2), _g(S - E2)).T
    ef = _euler(A.T @ Rf @ A, prev[1] if prev else None)
    p.get3(fb)[0][:] = ef
    return (ea, ef)


def aim_weapon(rig, p, side, haft_rest, head_rest, haft_body, lead_body, w, prev, two_faced=False):
    """
    Turns the hand so what it holds has its haft (`haft_rest`, from the fist towards its head, at rest) along `haft_body`
    (the body's frame) and its striking side (`head_rest`) turned `w` (0..1) of the way from the least turn about the
    haft towards `lead_body`. `two_faced`: a hammer, either face will do. Returns the angles set.
    """
    m = rig.m
    p.get3(f"hand_{side}")[0][:] = [0.0, 0.0, 0.0]
    mats = m.pose_matrices(p)
    B = mats["body"][:3, :3]
    P = mats[f"fore_{side}"][:3, :3]
    hr, er = _unit(haft_rest), _unit(head_rest)
    hb = _unit(haft_body)
    head0 = _min_rotation(hr, hb) @ er
    lb = np.asarray(lead_body, dtype=float)
    if two_faced and float(np.dot(lb, head0)) < 0.0:
        lb = -lb
    head_b = _slerp_dir(_perp(head0, hb), _perp(lb, hb), w)
    W = B @ _frame3(_g(hb), _g(head_b))
    Rest = _frame3(_g(hr), _g(er))
    e = _euler(P.T @ W @ Rest.T, prev)
    p.get3(f"hand_{side}")[0][:] = e
    return e


# How much of its length an arm stretches to at most (a dead straight arm makes the elbow whip round as it straightens
# and bends again: the IK's elbow moves fastest there).
REACH = 0.93


def soft_cap(x, cap, width):
    """x, eased off as it nears `cap` over its last `width` (never past it; the same as x well under it, and smooth:
    a hard limit would jolt a moving fist the moment it was reached)."""
    knee = cap - width
    return x if x <= knee else knee + width * math.tanh((x - knee) / width)


def two_hand_spot(Sr, rr, Sl, rl, want, D, c, zmax):
    """
    Where the right fist goes (at rest's coordinates) for a two-handed grip: as near `want` as it can be with both fists
    in reach (the left one `c` back down the haft from it, along -D) and both in front of the chest (z <= zmax), every
    limit eased in (soft_cap) so the fists glide along it.
    """
    R = np.array(want, dtype=float)
    for _ in range(40):
        v = R - Sr
        n = float(np.linalg.norm(v))
        if n > 1e-6:
            R = Sr + v * soft_cap(n, rr, 0.18 * rr) / n
        L = R - D * c
        v = L - Sl
        n = float(np.linalg.norm(v))
        if n > 1e-6:
            R = Sl + v * soft_cap(n, rl, 0.18 * rl) / n + D * c
        lim = min(zmax, zmax + float(D[2]) * c)
        R[2] = soft_cap(float(R[2]), lim, 0.1 * rr)
    return R


# ---------------------------------------------------------------- a two-handed blow


def two_handed(rig, kn, name, T, hit, haft_rest, head_rest, k_hand, k_haft, k_lead, k_lw, k_grip, c, k_tw, k_lean,
               k_lunge, pole_r=(-0.55, -0.8, 0.25), pole_l=(0.55, -0.8, 0.25), two_faced=False, hits=None):
    """
    A two-handed blow: the right fist along `k_hand` (spots: see the top), the haft along `k_haft` (ways, the body's
    frame), its striking side turned `k_lw` of the way to `k_lead` (keys of ways, or "sweep": the way a swing to his
    left goes), the left fist on the haft `c` reaches below the right as much as `k_grip` says (0 its own rest, 1
    holding on). Both start and end at rest (the first and last keys of the hand and haft are taken as rest, whatever
    they say), and the last moment eases every arm angle the rest of the way back (the game's blend never spins one).
    """
    Sr, Er, Fr, rr = rest_arm(rig, "r")
    Sl, El, Fl, rl = rest_arm(rig, "l")
    C, r = frame_of(rig)
    hr = _unit(haft_rest)
    hand = [(k[0], tuple(Fr) if i in (0, len(k_hand) - 1) else tuple(C + r * np.asarray(k[1], dtype=float)))
            + tuple(k[2:]) for i, k in enumerate(k_hand)]
    haft = [(k_haft[0][0], tuple(hr))] + [(k[0], tuple(_unit(k[1]))) + tuple(k[2:]) for k in k_haft[1:-1]] \
        + [(k_haft[-1][0], tuple(hr))]
    rest_pr, rest_pl = rest_pole(rig, "r"), rest_pole(rig, "l")
    zmax = C[2] - 0.6 * r          # (a fist's middle well out in front of the breastplate)
    rec0 = T - 0.12
    state = {}
    bones = ("arm_r", "fore_r", "hand_r", "arm_l", "fore_l")

    def frame(t):
        if t <= 0.0:
            state.clear()
        p = Pose()
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        g = min(max(curve(t, k_grip), 0.0), 1.0)
        D = vkeys(t, haft)
        # (the keys give the middle of the grip: both fists move round it as the haft turns, as a swing does; keyed
        # on the right fist, the left one whipped round it)
        want = pkeys(t, hand) + D * (0.5 * c * r * g)
        R = want + (two_hand_spot(Sr, rr * REACH, Sl, rl * REACH, want, D, c * r, zmax) - want) * g
        mats = rig.m.pose_matrices(p)
        bend = _smootherstep(3.0 * float(np.linalg.norm(R - Fr)) / r)
        state["r"] = arm_ik(rig, p, "r", body_point(rig, mats, R),
                            _unit(rest_pr * (1.0 - bend) + np.asarray(pole_r) * bend), state.get("r"))
        w = curve(t, k_lw)
        if k_lead == "sweep":
            # (a swing's way is level and square to the haft: with the haft upright it has none, so the turn towards
            # it fades out as the haft stands up)
            side = np.cross(D, [0.0, 1.0, 0.0])
            w *= _smootherstep((float(np.linalg.norm(side)) - 0.25) / 0.45)
            lead_way = _unit(side) if np.linalg.norm(side) > 1e-6 else np.array([1.0, 0.0, 0.0])
        else:
            lead_way = vkeys(t, k_lead)
        if two_faced:
            # (either face will do, but the one it took stays taken: picked fresh every frame, it flipped over)
            if "face" not in state:
                head0 = _min_rotation(hr, D) @ _unit(head_rest)
                state["face"] = lead_way if float(np.dot(lead_way, head0)) >= 0.0 else -lead_way
            if float(np.dot(lead_way, state["face"])) < 0.0:
                lead_way = -lead_way
            state["face"] = lead_way
        state["h"] = aim_weapon(rig, p, "r", hr, head_rest, D, lead_way, w, state.get("h"), False)
        mats = rig.m.pose_matrices(p)
        haft_now = _ungecko(mats["hand_r"][:3, :3] @ _g(hr))
        grip = np.asarray(rig.m.point(mats, "hand_r", tuple(Fr)), dtype=float) - haft_now * c * r
        rest_l = body_point(rig, mats, Fl)
        state["l"] = arm_ik(rig, p, "l", rest_l + (grip - rest_l) * g,
                            _unit(rest_pl * (1.0 - g) + np.asarray(pole_l) * g), state.get("l"))
        if t > rec0:
            # the last moment: every arm angle eased the rest of the way to rest (the game's blend never spins one)
            k = 1.0 - _smootherstep((t - rec0) / (T - rec0))
            for b in bones:
                ang = p.get3(b)[0]
                ang[:] = [x * k for x in ang]
        return p

    a = record(rig, name, T, frame, loop=False)
    a.meta["hits"] = list(hits) if hits else [hit]
    return a


def two_hand_sweep(rig, kn, name, haft_rest, head_rest, T=1.3, hit=0.62, c=0.4, low=0.0, two_faced=False):
    """
    A flat two-handed swing (the Cinder Knight's hammer, the Thawed axe, the Sporebound's scythe): both hands come
    together on the haft, the weapon drawn back past his right shoulder as his whole body winds round, then swung flat
    across in front (`low` lower than chest height: the scythe at the waist), his hips first and his arms reaching out
    straight at the hit, flowing on round to his left, the left hand letting go as it comes back to rest.
    """
    hv = kn.heavy
    ld = lead(kn)
    top = hit * 0.6
    hold = hit + 0.26 + 0.06 * hv
    rel = hold + 0.4 * (T - hold)
    # (on the way back the weapon comes up upright in front of him, its butt clear of his legs, its head well over his
    # helm, and is carried across to his right side like that: a long haft carried any other way swept through him)
    mid = 0.5 * (top + hit)
    after = 0.5 * (hold + rel)
    k_hand = [(0.0, None), (top * 0.5, (-0.34, -0.3, -0.62)), (top, (-0.26, -0.3 - low * 0.5, -0.66)),
              (mid, (-0.24, -0.38 - low * 0.8, -0.8)), (hit, (-0.2, -0.42 - low, -0.86), "hit"), (hold, (0.1, -0.42 - low, -0.86)),
              (after, (0.0, -0.3, -0.82)), (rel, (-0.12, -0.2, -0.82)), (T, None)]
    k_haft = [(0.0, None), (top * 0.5, (-0.62, 0.75, 0.2)), (top, (-0.85, 0.3 - low * 0.6, 0.45)),
              (mid, (-0.92, 0.05 - low * 0.55, -0.38)), (hit, (0.3, -0.05 - low * 0.5, -0.95)),
              (hold, (0.85, -0.15 - low * 0.4, -0.5)), (after, (0.55, 0.62, -0.55)), (rel, (0.05, 0.97, 0.15)),
              (T, None)]
    k_lw = [(0.0, 0.0), (top * 0.8, 1.0), (hold + 0.05, 1.0), (rel, 0.0), (T, 0.0)]
    k_grip = [(0.0, 0.0), (top * 0.55, 1.0), (rel - 0.05, 1.0), (rel + 0.18, 0.0), (T, 0.0)]
    k_tw = sh([(0.0, 0.0), (top - 0.05, 52.0), (top + 0.05, 55.0), (hit, -6.0, "hit"), (hold, -48.0),
               (hold + 0.1, -45.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (top, 3.0 + 4.0 * low), (hit, 9.0 + 3.0 * hv + 10.0 * low, "hit"),
                 (hold, 7.0 + 8.0 * low), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (top, 0.2), (hit, 0.75, "hit"), (hold, 0.65), (T, 0.0)], -ld * 0.5)
    # (the elbows out to the sides: the left arm reaching across his chest would otherwise lie on it)
    return two_handed(rig, kn, name, T, hit, haft_rest, head_rest, k_hand, k_haft, "sweep", k_lw, k_grip, c,
                      k_tw, k_lean, k_lunge, pole_r=(-0.8, -0.5, 0.3), pole_l=(0.85, -0.45, 0.25), two_faced=two_faced)


def two_hand_chop(rig, kn, name, haft_rest, head_rest, T=1.35, hit=0.7, c=0.38, down=0.45, reach=0.95, ground=False,
                  two_faced=False):
    """
    A two-handed overhead blow (the Geode Knight's pick; with `ground`, a slam into the ground): the left hand comes up
    onto the haft, both raise it high behind his head as he rears back, then pull it over and down in front, his arms
    reaching out straight (`reach` of their length ahead) as it strikes, its head driven down (`down`: the haft's droop
    at the blow), held a moment, then carried back to rest.
    """
    hv = kn.heavy
    ld = lead(kn)
    top = hit * 0.68
    hold = hit + 0.2 + 0.08 * hv
    rel = hold + 0.35 * (T - hold)
    yh = -0.48 if ground else -0.12
    k_hand = [(0.0, None), (top * 0.5, (-0.42, 0.1, -0.45)), (top, (-0.32, 0.4, -0.38)),
              (hit - 0.12, (-0.12, 0.22, -0.78)), (hit - 0.06, (-0.06, 0.05 + 0.5 * yh, -0.88)),
              (hit, (0.0, yh, -reach), "hit"),
              (hold, (0.0, yh - 0.06, -reach + 0.06)), (rel, (-0.1, -0.3, -0.74)), (T, None)]
    dz = math.sqrt(max(1.0 - down * down, 0.0))
    k_haft = [(0.0, None), (top * 0.5, (-0.45, 0.7, 0.55)), (top, (-0.15, 0.45, 0.88)),
              (hit - 0.12, (0.0, 0.75, -0.6)), (hit - 0.06, (0.0, 0.22 - 0.4 * down, -0.97)),
              (hit, (0.0, -down, -dz)), (hold, (0.0, -down - 0.12, -dz + 0.05)),
              (rel, (0.05, 0.97, 0.15)), (T, None)]
    k_lead = [(0.0, (0.0, -1.0, 0.0)), (T, (0.0, -1.0, 0.0))]
    k_lw = [(0.0, 0.0), (top, 0.6), (hit - 0.1, 1.0), (hold, 1.0), (rel, 0.0), (T, 0.0)]
    k_grip = [(0.0, 0.0), (top * 0.6, 1.0), (rel - 0.05, 1.0), (rel + 0.18, 0.0), (T, 0.0)]
    k_tw = sh([(0.0, 0.0), (top, 18.0), (hit, -4.0, "hit"), (hold, -6.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (top, -9.0 - 3.0 * hv), (hit, (24.0 if ground else 16.0) + 4.0 * hv, "hit"),
                 (hold, (22.0 if ground else 14.0) + 3.0 * hv), (T - 0.2, -2.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (top, 0.1), (hit, 0.95 if ground else 0.8, "hit"), (hold, 0.9 if ground else 0.7),
                  (T - 0.25, 0.25), (T, 0.0)], -ld * 0.5)
    return two_handed(rig, kn, name, T, hit, haft_rest, head_rest, k_hand, k_haft, k_lead, k_lw, k_grip, c,
                      k_tw, k_lean, k_lunge, two_faced=two_faced)


# ---------------------------------------------------------------- the Stormblade's cuts


def blade_cuts(rig, kn, T=0.95, hits=(0.3, 0.62)):
    """
    T6 (the user: his cuts looked "clunky and weird ... make him do cooler stuff with his daggers"): an X-cut, then a
    scissor cut. Both blades raised high and wide as he rears, then whipped down crossing in front of him (the left one
    over the right) as he drops into a lunge; a beat crossed, then both flung out wide to his sides as he springs up
    out of it, arms at full stretch, and back to his stance. The left sword is aimed along each cut; the right one, held
    reversed, runs along his forearm and cuts with it.
    """
    Sr, Er, Fr, rr = rest_arm(rig, "r")
    Sl, El, Fl, rl = rest_arm(rig, "l")
    C, r = frame_of(rig)
    h1, h2 = hits
    wind, hold1, hold2 = 0.16, h1 + 0.1, h2 + 0.1

    def spot(v):
        return tuple(C + r * np.asarray(v, dtype=float))

    k_r = [(0.0, tuple(Fr)), (wind, spot((-0.62, 0.42, -0.4))), (h1, spot((0.18, -0.38, -0.62)), "hit"),
           (hold1, spot((0.22, -0.42, -0.6))), (h2 - 0.1, spot((-0.1, -0.18, -0.8))), (h2, spot((-0.9, -0.05, -0.36)), "hit"),
           (hold2, spot((-0.88, -0.1, -0.34))), (T, tuple(Fr))]
    k_l = [(0.0, tuple(Fl)), (wind, spot((0.62, 0.42, -0.4))), (h1, spot((-0.2, -0.12, -0.92)), "hit"),
           (hold1, spot((-0.24, -0.16, -0.9))), (h2 - 0.1, spot((0.1, -0.06, -0.9))), (h2, spot((0.9, -0.05, -0.36)), "hit"),
           (hold2, spot((0.88, -0.1, -0.34))), (T, tuple(Fl))]
    # the left blade (from its fist to its point, the body's frame): up and out as it rears, down across, out wide
    bl = _unit((-0.12, 0.62, -0.78))
    k_bl = [(0.0, tuple(bl)), (wind, (0.45, 0.8, -0.35)), (h1, (-0.75, -0.35, -0.55)), (hold1, (-0.75, -0.4, -0.5)),
            (h2 - 0.1, (-0.2, 0.2, -0.95)), (h2, (0.85, 0.05, -0.52)), (hold2, (0.85, 0.0, -0.5)), (T, tuple(bl))]
    k_lean = [(0.0, 0.0), (wind, -7.0), (h1, 17.0, "hit"), (hold1, 15.0), (h2, 4.0, "hit"), (hold2, 5.0), (T, 0.0)]
    k_tw = [(0.0, 0.0), (wind, 4.0), (h1, -8.0, "hit"), (hold1, -7.0), (h2, 6.0, "hit"), (T, 0.0)]
    k_lunge = [(0.0, 0.0), (wind, 0.15), (h1, 0.85, "hit"), (hold1, 0.85), (h2, 0.55, "hit"), (hold2, 0.5), (T, 0.0)]
    pr, pl = (-0.85, -0.45, 0.25), (0.85, -0.45, 0.25)
    rest_pr, rest_pl = rest_pole(rig, "r"), rest_pole(rig, "l")
    rec0 = T - 0.12
    state = {}
    bones = ("arm_r", "fore_r", "arm_l", "fore_l", "hand_l")

    def frame(t):
        if t <= 0.0:
            state.clear()
        p = Pose()
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.08, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        mats = rig.m.pose_matrices(p)
        R, L = pkeys(t, k_r), pkeys(t, k_l)
        br = _smootherstep(3.0 * float(np.linalg.norm(R - Fr)) / r)
        bll = _smootherstep(3.0 * float(np.linalg.norm(L - Fl)) / r)
        state["r"] = arm_ik(rig, p, "r", body_point(rig, mats, R), _unit(rest_pr * (1 - br) + np.asarray(pr) * br),
                            state.get("r"))
        state["l"] = arm_ik(rig, p, "l", body_point(rig, mats, L), _unit(rest_pl * (1 - bll) + np.asarray(pl) * bll),
                            state.get("l"))
        state["h"] = aim_weapon(rig, p, "l", bl, _unit(np.cross(bl, [1.0, 0.0, 0.0])), vkeys(t, k_bl), (1.0, 0.0, 0.0),
                                0.0, state.get("h"))
        if t > rec0:
            k = 1.0 - _smootherstep((t - rec0) / (T - rec0))
            for b in bones:
                ang = p.get3(b)[0]
                ang[:] = [x * k for x in ang]
        return p

    a = record(rig, "slash", T, frame, loop=False)
    a.meta["hits"] = list(hits)
    return a
