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

from .kanims import (STEP, _euler, _nearest, _other, _frame3, _g, _min_rotation, _slerp_dir, _ungecko, _unit, body, cape, curve, lead,
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
    Where the right fist goes for a two-handed grip: as near `want` as it can be with both fists in reach (the left one
    `c` back down the haft from it, along -D) and (unless `zmax` is None) both in front of the chest (z <= zmax), every
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
        if zmax is not None:
            lim = min(zmax, zmax + float(D[2]) * c)
            R[2] = soft_cap(float(R[2]), lim, 0.1 * rr)
    return R


# ---------------------------------------------------------------- a blow with the weapon held in one or both hands
#
# The user, November 19, 2026, after the first two-handed swings: the Cinder Knight "does a weird flick with his hands
# that's really fast at the end", the Thawed axe's "flick back" and the Sporebound's ("the scythe is going through the
# body"). What it was: each blow carried the weapon on round past its rest and then, in its last tenth of a second,
# turned every angle straight back to rest; the hand had turned on round 300-470 degrees by then, so it spun that far
# in a tenth of a second. Now every blow comes back the way it went (the weapon turned back as it turned out), slowly,
# and only a few degrees are left to tidy away at its very end (a warning if more).


def two_handed(rig, kn, name, T, hit, haft_rest, head_rest, k_hand, k_haft, k_lead, k_lw, k_grip, c, k_tw, k_lean,
               k_lunge, pole_r=(-0.55, -0.8, 0.25), pole_l=(0.55, -0.8, 0.25), two_faced=False, hits=None,
               world=False, k_slide=None, rest_grip=False, k_left=None, k_lpole=None, k_gap=None):
    """
    A blow with the weapon in the right hand, the left on its haft as `k_grip` says (0 its own way, 1 holding on).
    `k_hand`: spots for the middle of the grip (the right fist, when the left lets go; see the top for spots); the
    first and last are taken as rest. `k_haft`: the haft's way (from the fist towards its head). With `world`, spots and
    ways are in the model's own frame (fixed to the ground: a blow into the ground), else in the body's (they turn with
    it: a swing round it). `k_lead`: "sweep" (its striking side leading a swing round him), "chop" (leading a blow down
    in front of him) or keys of ways; `k_lw` how much (0 as little turn about the haft as can be). `k_slide`: units the
    weapon slides along the haft through the fists towards its head (the hands going down it to the butt; mobs.Knight
    `slide`). `rest_grip`: both hands hold it at rest (the Thawed axe; then `c` is the gap between the fists at rest).
    `k_left`: spots for the left fist while it isn't holding on. `k_gap`: units between the fists along the haft
    (the left hand sliding up it to the right; the first and last are taken as at rest).
    """
    Sr, Er, Fr, rr = rest_arm(rig, "r")
    Sl, El, Fl, rl = rest_arm(rig, "l")
    C, r = frame_of(rig)
    hr = _unit(haft_rest)
    if rest_grip:
        c = float(np.linalg.norm(Fr - Fl)) / r
    gap = c * r
    rest_spot = (Fr - hr * (0.5 * gap)) if rest_grip else Fr
    # (a key of None, and the first and last keys, are as it rests)
    def rest_or(i, k, n, rest, f):
        return tuple(rest) if (i in (0, n - 1) or k[1] is None) else f(k[1])

    def spot(v):
        return tuple(C + r * np.asarray(v, dtype=float))

    hand = [(k[0], rest_or(i, k, len(k_hand), rest_spot, spot)) + tuple(k[2:]) for i, k in enumerate(k_hand)]
    haft = [(k[0], rest_or(i, k, len(k_haft), hr, lambda v: tuple(_unit(v)))) + tuple(k[2:])
            for i, k in enumerate(k_haft)]
    gaps = None if k_gap is None else [(k[0], gap if (i in (0, len(k_gap) - 1) or k[1] is None) else float(k[1]))
                                       + tuple(k[2:]) for i, k in enumerate(k_gap)]
    left = None if k_left is None else [(k[0], rest_or(i, k, len(k_left), Fl, spot)) for i, k in enumerate(k_left)]
    rest_pr, rest_pl = rest_pole(rig, "r"), rest_pole(rig, "l")
    lpoles = None if k_lpole is None else [(k[0], rest_or(i, k, len(k_lpole), rest_pl, lambda v: tuple(_unit(v))))
                                           for i, k in enumerate(k_lpole)]
    # (a fist's middle well out in front of the breastplate, but never further out than where the fists rest)
    zmax = None if world else max(C[2] - 0.6 * r, max(Fr[2], Fl[2]) + 3.0)
    # (how far each fist may go from its shoulder: never short of where it rests, which a nearly straight arm at rest
    # was, so it never quite got back to rest)
    rr_cap = max(rr * REACH, float(np.linalg.norm(Fr - Sr)) / 0.8)
    rl_cap = max(rl * REACH, float(np.linalg.norm(Fl - Sl)) / 0.8)
    tidy = T - 0.08
    state = {}
    bones = ("arm_r", "fore_r", "hand_r", "arm_l", "fore_l")
    m = rig.m

    def to_body(B, v):
        return _ungecko(B.T @ _g(v))

    def frame(t):
        if t <= 0.0:
            state.clear()
        p = Pose()
        body(p, curve(t, k_lean), curve(t, k_tw))
        cape(p, curve(t - 0.1, k_lean))
        lunge(p, curve(t, k_lunge), kn.hip)
        show(p, kn)
        if k_slide is not None:
            p.pos("weapon_r", *(hr * curve(t, k_slide)))
        g = 1.0 if k_grip is None else min(max(curve(t, k_grip), 0.0), 1.0)
        gp = gap if gaps is None else curve(t, gaps)
        mats = m.pose_matrices(p)
        B = mats["body"][:3, :3]
        Dk = vkeys(t, haft)
        Db = to_body(B, Dk) if world else Dk
        want = pkeys(t, hand) + Dk * (0.5 * gp * g)
        if world:
            Srn = np.asarray(m.point(mats, "arm_r", tuple(Sr)), dtype=float)
            Sln = np.asarray(m.point(mats, "arm_l", tuple(Sl)), dtype=float)
            R = want + (two_hand_spot(Srn, rr_cap, Sln, rl_cap, want, Dk, gp, None) - want) * g
            target = R
            far = float(np.linalg.norm(R - np.asarray(m.point(mats, "body", tuple(Fr)), dtype=float)))
        else:
            R = want + (two_hand_spot(Sr, rr_cap, Sl, rl_cap, want, Dk, gp, zmax) - want) * g
            target = body_point(rig, mats, R)
            far = float(np.linalg.norm(R - Fr))
        # (the elbow's way eases from how it rests to the blow's over the fist's first reach out of rest: over its first
        # third it snapped round, and the forearm unwound 57 degrees in the blow's last tenth of a second)
        bend = _smootherstep(far / r)
        state["r"] = arm_ik(rig, p, "r", target, _unit(rest_pr * (1.0 - bend) + np.asarray(pole_r) * bend),
                            state.get("r"))
        w = curve(t, k_lw)
        if k_lead in ("sweep", "chop"):
            axis = to_body(B, (0.0, 1.0, 0.0)) if k_lead == "sweep" else np.array([1.0, 0.0, 0.0])
            side = np.cross(Db, axis)
            # (with the haft along the axis its way is undefined: the turn towards it fades out there)
            w *= _smootherstep((float(np.linalg.norm(side)) - 0.25) / 0.45)
            lead_way = _unit(side) if np.linalg.norm(side) > 1e-6 else state.get("lead", np.array([1.0, 0.0, 0.0]))
        else:
            lead_way = vkeys(t, k_lead)
        state["lead"] = lead_way
        if two_faced:
            # (either face will do, but the one it took stays taken: picked fresh every frame, it flipped over; and it's
            # picked only once the blow has a way to lead, as the face nearer how the head is turned then: picked at
            # rest, with the haft upright and no way to speak of, it was as often the far one, and the hammer turned on
            # round through half a turn and back the other side as it came back to rest)
            if "face" not in state and w > 0.05:
                head0 = _min_rotation(hr, Db) @ _unit(head_rest)
                state["face"] = lead_way if float(np.dot(lead_way, head0)) >= 0.0 else -lead_way
            if "face" in state:
                if float(np.dot(lead_way, state["face"])) < 0.0:
                    lead_way = -lead_way
                state["face"] = lead_way
        state["h"] = aim_weapon(rig, p, "r", hr, head_rest, Db, lead_way, w, state.get("h"), False)
        mats = m.pose_matrices(p)
        haft_now = _ungecko(mats["hand_r"][:3, :3] @ _g(hr))
        grip = np.asarray(m.point(mats, "hand_r", tuple(Fr)), dtype=float) - haft_now * gp
        if left is not None:
            lp = pkeys(t, left)
            own = lp if world else body_point(rig, mats, lp)
        else:
            own = body_point(rig, mats, Fl)
        lpole = rest_pl * (1.0 - g) + np.asarray(pole_l) * g
        if lpoles is not None and g < 1.0:
            lpole = lpole * g + vkeys(t, lpoles) * (1.0 - g)
        state["l"] = arm_ik(rig, p, "l", own + (grip - own) * g, _unit(lpole), state.get("l"))
        if t > tidy:
            # the last moment: what's left of every arm angle tidied away (a few degrees: the game's blend back to
            # standing never spins a part)
            if "resid" not in state:
                state["resid"] = max(abs(x) for b in bones for x in tidy_angles(p.get3(b)[0]))
                if state["resid"] > 8.0:
                    print(f"  WARNING {kn.ID} {name}: {state['resid']:.0f} degrees left to tidy at its end")
            k = 1.0 - _smootherstep((t - tidy) / (T - tidy))
            for b in bones:
                ang = p.get3(b)[0]
                ang[:] = [x * k for x in tidy_angles(ang)]
        return p

    a = record(rig, name, T, frame, loop=False)
    a.meta["hits"] = list(hits) if hits else [hit]
    return a


def tidy_angles(e):
    """The same turn written as near 0 as it can be (of its two sets of angles, a turn more or less of each)."""
    best = None
    for v in (list(e), _other(e)):
        v = _nearest(v, (0.0, 0.0, 0.0))
        if best is None or sum(x * x for x in v) < sum(x * x for x in best):
            best = v
    return best


def swing_round(rig, kn, name, haft_rest, head_rest, T=2.0, hit=0.66, c=0.42, low=0.0, two_faced=False,
                rest_grip=False, slide=0.0, gap=None):
    """
    A flat two-handed swing (the Cinder Knight's hammer, the Thawed axe, the Sporebound's scythe): both hands on the
    haft, the weapon drawn back past his right shoulder as his whole body winds round, swung flat across in front (`low`
    lower than chest height: the scythe at the waist), his hips first and his arms reaching out at the hit, on round to
    his left; then, slowly, back the way it came to the front and up to rest (the left hand letting go as it rises,
    unless both hold it at rest). `slide`: units the weapon slides through his fists as he winds up (his hands going
    down the haft towards its butt, as for any big swing of a long haft), `gap` the units between his fists then.
    """
    hv = kn.heavy
    ld = lead(kn)
    top = hit * 0.62
    mid = 0.5 * (top + hit)
    hold = hit + 0.26 + 0.06 * hv
    back = hold + 0.4 * (T - hold)
    rest = T - 0.15                    # (everything back at rest by here, held to the end)
    rise = back + 0.55 * (rest - back)
    _s, _e, Fr, _r = rest_arm(rig, "r")
    _s, _e, Fl, _r = rest_arm(rig, "l")
    C, r = frame_of(rig)
    hr = _unit(haft_rest)
    g0 = float(np.linalg.norm(Fr - Fl)) if rest_grip else c * r
    rest_mid = ((Fr - hr * (0.5 * g0)) if rest_grip else Fr) - C
    rest_mid = rest_mid / r
    b_spot = np.array((-0.14, -0.4 - low * 0.8, -0.84))
    b_haft = _unit((0.25, -0.02 - low * 0.4, -0.97))
    # (a weapon held at rest by one hand goes up to rest at his right side, its haft leaning in a little and its butt out
    # past his hip: lifted upright before him, its long butt end swung down through his belly and his right leg)
    rise_haft = _unit(b_haft + (hr - b_haft) * 0.6) if rest_grip else _unit(b_haft * 0.4 + hr * 0.6 + np.array([0.3, 0.0, 0.0]))
    k_hand = [(0.0, None), (top * 0.5, (-0.3, -0.3, -0.62)), (top, (-0.26, -0.3 - low * 0.5, -0.66)),
              (mid, (-0.24, -0.38 - low * 0.8, -0.8)), (hit, (-0.2, -0.42 - low, -0.86), "hit"),
              (hold, (0.1, -0.42 - low, -0.86)), (back, tuple(b_spot)),
              (rise, tuple(b_spot + (rest_mid - b_spot) * (0.6 if rest_grip else 0.78))), (rest, None), (T, None)]
    # (on the way up to rest the haft turns through the way half way between level ahead and how it rests, its head
    # kept well out in front of him: the first way back carried it up past his face and round, and its butt end through
    # his body)
    k_haft = [(0.0, None), (top * 0.5, (-0.62, 0.75, 0.2)), (top, (-0.85, 0.3 - low * 0.6, 0.45)),
              (mid, (-0.92, 0.05 - low * 0.55, -0.38)), (hit, (0.3, -0.05 - low * 0.5, -0.95)),
              (hold, (0.85, -0.15 - low * 0.4, -0.5)), (back, tuple(b_haft)),
              (rise, tuple(rise_haft)), (rest, None), (T, None)]
    k_lw = [(0.0, 0.0), (top * 0.8, 1.0), (back, 1.0), (rise, 0.5 if rest_grip else 0.25), (rest, 0.0), (T, 0.0)]
    k_grip = None if rest_grip else [(0.0, 0.0), (top * 0.55, 1.0), (back, 1.0), (rise, 0.3), (rest, 0.0), (T, 0.0)]
    k_slide = None
    k_gap = None
    if slide:
        k_slide = [(0.0, 0.0), (top * 0.6, slide * 0.4), (top, slide), (back, slide), (rise, slide * 0.7), (rest, 0.0), (T, 0.0)]
    if gap is not None:
        k_gap = [(0.0, None), (top * 0.6, g0 + (gap - g0) * 0.8), (top, gap), (back, gap),
                 (rise, g0 + (gap - g0) * 0.6), (rest, None), (T, None)]
    k_tw = sh([(0.0, 0.0), (top - 0.05, 52.0), (top + 0.05, 55.0), (hit, -6.0, "hit"), (hold, -48.0),
               (hold + 0.1, -45.0), (back, -8.0), (rise, -3.0), (rest, 0.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (top, 3.0 + 4.0 * low), (hit, 9.0 + 3.0 * hv + 10.0 * low, "hit"),
                 (hold, 7.0 + 8.0 * low), (back, 4.0 + 4.0 * low), (rest, 0.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (top, 0.2), (hit, 0.75, "hit"), (hold, 0.65), (back, 0.35), (rest, 0.0), (T, 0.0)], -ld * 0.5)
    # (letting go, the left hand goes back to his side out round his hip, not straight through it)
    k_left = None if rest_grip else [(0.0, None), (back, (0.6, -0.55, -0.75)), (rise, (0.9, -0.72, -0.55)),
                                     (rest, None), (T, None)]
    # (the elbows out to the sides: the left arm reaching across his chest would otherwise lie on it)
    return two_handed(rig, kn, name, T, hit, haft_rest, head_rest, k_hand, k_haft, "sweep", k_lw, k_grip, c,
                      k_tw, k_lean, k_lunge, pole_r=(-0.8, -0.5, 0.3), pole_l=(0.85, -0.45, 0.25), two_faced=two_faced,
                      rest_grip=rest_grip, k_slide=k_slide, k_gap=k_gap, k_left=k_left)


def chop_down(rig, kn, name, haft_rest, head_rest, T=2.0, hit=0.95, low=-0.55, ahead=1.05, angle=50.0, side=-0.12,
              rest_grip=False, c=0.3, slide=0.0, gap=None, two_faced=False, lean_hit=18.0, crouch=1.0, pull_x=None,
              pull_slide=None):
    """
    A two-handed blow over the top into the ground in front of him (the Thawed axe's smash, the Cinder Knight's slam):
    the left hand comes onto the haft, both raise it up past his right shoulder and over his head, its head hanging
    behind him a beat as he rears back, then brought over the top and down, his weight following it, its blade (or
    hammer face) leading and striking the ground: at the blow his hands are `ahead` reaches out and `low` below his
    shoulders, the haft `angle` degrees below level. Held a moment, pulled out and carried back to rest. The ground,
    not his body, sets where things go (`world`): his lean and crouch carry his shoulders down after his hands.
    `slide`, `gap`: the weapon sliding through his fists as he raises it (his hands going down to the butt).
    """
    hv = kn.heavy
    ld = lead(kn)
    lift = hit * 0.3
    top = hit * 0.62
    over = hit * 0.84
    hold = hit + 0.22 + 0.08 * hv
    rest = T - 0.15
    free = hold + 0.25 * (rest - hold)
    pull = hold + 0.65 * (rest - hold)
    a = math.radians(angle)
    a2 = math.radians(angle * 0.4)
    # (at the top his hands are up before his right shoulder, the head of the weapon back over it, up behind his
    # head, as a woodsman's: his short arms can't both reach over his head, and tried, they went round his helm)
    k_hand = [(0.0, None), (lift, (-0.3, 0.0, -0.5)), (top, (-0.38, 0.14, -0.4)), (top + 0.08, (-0.36, 0.17, -0.44)),
              (over, (side, 0.32, -0.95)), (hit, (side, low, -ahead), "hit"), (hold, (side, low - 0.03, -ahead + 0.03)),
              (free, (side, low + 0.12, -ahead + 0.02)), (pull, (side * 0.6 if pull_x is None else pull_x, -0.15, -0.95)), (rest, None), (T, None)]
    k_haft = [(0.0, None), (lift, (-0.25, 0.9, 0.35)), (top, (-0.15, 0.58, 0.8)), (top + 0.08, (-0.14, 0.68, 0.72)),
              (over, (-0.04, 0.92, -0.4)), (hit, (-0.02, -math.sin(a), -math.cos(a)), "hit"),
              (hold, (-0.02, -math.sin(a) - 0.04, -math.cos(a))), (free, (-0.04, -math.sin(a2), -math.cos(a2))),
              (pull, (-0.1, 0.95, -0.3)), (rest, None), (T, None)]
    k_lw = [(0.0, 0.0), (lift, 1.0), (pull, 1.0), (rest, 0.0), (T, 0.0)]
    # (the left hand lets go as he lifts it back upright, and drifts back to his side: let go at the end, it swung)
    k_grip = None if rest_grip else [(0.0, 0.0), (lift * 0.8, 1.0), (free, 1.0), (pull - 0.08, 0.25), (pull, 0.0),
                                     (rest, 0.0), (T, 0.0)]
    # (`pull_slide`: lifting it back upright he holds it by the very end of its haft, so its butt doesn't swing
    # through him, then lets it slide down through his fist till the butt rests on the ground)
    k_slide = None if not slide else [(0.0, 0.0), (lift, slide * 0.5), (top, slide), (free, slide),
                                      (pull, slide if pull_slide is None else pull_slide), (rest, 0.0), (T, 0.0)]
    k_gap = None
    if gap is not None:
        _s, _e, Fr, _r = rest_arm(rig, "r")
        _s, _e, Fl, _r = rest_arm(rig, "l")
        C, r = frame_of(rig)
        g0 = float(np.linalg.norm(Fr - Fl)) if rest_grip else c * r
        k_gap = [(0.0, None), (lift, g0 + (gap - g0) * 0.5), (top, gap), (pull, gap), (rest, None), (T, None)]
    k_tw = sh([(0.0, 0.0), (top, 10.0), (hit, -4.0, "hit"), (hold, -4.0), (rest, 0.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (top, -9.0 - 3.0 * hv), (top + 0.08, -10.0 - 3.0 * hv), (hit, lean_hit + 4.0 * hv, "hit"),
                 (hold, lean_hit - 2.0 + 3.0 * hv), (pull, 8.0), (rest, 0.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (top, 0.1), (hit, crouch, "hit"), (hold, crouch * 0.95), (pull, 0.4), (rest, 0.0),
                  (T, 0.0)], -ld * 0.5)
    return two_handed(rig, kn, name, T, hit, haft_rest, head_rest, k_hand, k_haft, "chop", k_lw, k_grip, c,
                      k_tw, k_lean, k_lunge, pole_r=(-0.85, -0.45, 0.25), pole_l=(0.85, -0.45, 0.25),
                      two_faced=two_faced, world=True, k_slide=k_slide, rest_grip=rest_grip, k_gap=k_gap)


def one_hand_chop(rig, kn, name, haft_rest, head_rest, T=2.4, hit=0.85, low=-1.15, ahead=0.78, angle=18.0,
                  side=-0.5, lean_hit=28.0, crouch=1.2):
    """
    A one-handed blow over the top into the ground (the Geode Knight's pick; the user, November 19, 2026: "his
    pickaxe needs to hit the ground ... just do a one-hand thing"): the weapon swung up off his shoulder, back over
    it, its head hanging behind him a beat, then over the top and down, his weight going in after it, the spike (or
    blade) leading and driving into the ground before him, the haft near level as it bites; held, pulled free and
    shouldered again. His free arm swings out ahead for balance as he winds up and back as he strikes. `world`, like
    chop_down: the ground sets where the blow lands.
    """
    hv = kn.heavy
    ld = lead(kn)
    lift = hit * 0.32
    top = hit * 0.62
    over = hit * 0.84
    hold = hit + 0.2 + 0.08 * hv
    rest = T - 0.15
    free = hold + 0.22 * (rest - hold)
    up = hold + 0.55 * (rest - hold)
    pull = hold + 0.8 * (rest - hold)
    a = math.radians(angle)
    k_hand = [(0.0, None), (lift, (-0.58, 0.62, -0.4)), (top, (-0.52, 0.78, -0.2)), (top + 0.07, (-0.5, 0.78, -0.26)),
              (over, (side, 0.45, -0.72)), (hit, (side, low, -ahead), "hit"), (hold, (side, low - 0.02, -ahead + 0.02)),
              (free, (side, low + 0.25, -ahead + 0.05)), (up, (-0.52, 0.2, -0.62)), (pull, (-0.6, 0.28, -0.55)),
              (rest, None), (T, None)]
    k_haft = [(0.0, None), (lift, (-0.05, 0.8, 0.6)), (top, (-0.06, 0.25, 0.97)), (top + 0.07, (-0.06, 0.4, 0.92)),
              (over, (-0.03, 0.9, -0.43)), (hit, (-0.02, -math.sin(a), -math.cos(a)), "hit"),
              (hold, (-0.02, -math.sin(a) - 0.03, -math.cos(a))), (free, (-0.03, 0.2, -0.98)),
              (up, (-0.02, 0.96, -0.28)), (pull, (0.08, 0.85, 0.52)), (rest, None), (T, None)]
    k_lw = [(0.0, 0.0), (lift, 1.0), (up, 1.0), (pull, 0.5), (rest, 0.0), (T, 0.0)]
    k_grip = [(0.0, 0.0), (T, 0.0)]
    k_left = [(0.0, None), (top, (0.75, -0.25, -0.75)), (hit, (1.0, -0.7, 0.15)), (hold, (1.0, -0.72, 0.12)),
              (pull, (0.95, -0.8, -0.2)), (rest, None), (T, None)]
    k_tw = sh([(0.0, 0.0), (top, 14.0), (hit, -6.0, "hit"), (hold, -5.0), (rest, 0.0), (T, 0.0)], -ld)
    k_lean = sh([(0.0, 0.0), (top, -8.0 - 3.0 * hv), (hit, lean_hit + 4.0 * hv, "hit"), (hold, lean_hit + 2.0 * hv),
                 (pull, 6.0), (rest, 0.0), (T, 0.0)], -ld)
    k_lunge = sh([(0.0, 0.0), (top, 0.15), (hit, crouch, "hit"), (hold, crouch * 0.95), (pull, 0.3), (rest, 0.0),
                  (T, 0.0)], -ld * 0.5)
    return two_handed(rig, kn, name, T, hit, haft_rest, head_rest, k_hand, k_haft, "chop", k_lw, k_grip, 0.3,
                      k_tw, k_lean, k_lunge, pole_r=(-0.85, -0.45, 0.25), pole_l=(0.85, -0.45, 0.25), world=True,
                      k_left=k_left, k_lpole=[(0.0, None), (top, (0.9, -0.4, 0.2)), (pull, (0.9, -0.4, 0.2)), (rest, None),
                                              (T, None)])


def two_hand_chop(rig, kn, name, haft_rest, head_rest, T=1.35, hit=0.7, c=0.38, down=0.45, reach=0.95, ground=False,
                  two_faced=False):
    """
    (Not used since November 19, 2026, evening: the Geode pick is one_hand_chop now, the two-handed blows into the
    ground chop_down. Kept for reference.)
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
