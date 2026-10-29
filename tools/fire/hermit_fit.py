"""
Fits the Cinder Hermit's peek to each of its shells, and checks its hide, peek and come_out against them (the user,
October 27, 2026: "the sides of the hide animation have to be fixed, where the entire crab hides; peek as well, for
each one"; October 28: "I need the eyes to pop out in a way where they don't clip through the top of the shell ...
also look left and right"):

    python tools/fire/hermit_fit.py           finds the peek for every shell (prints the table for firemobs/hermit.py)
    python tools/fire/hermit_fit.py --check   checks the table firemobs/hermit.py has now
    python tools/fire/hermit_fit.py urn conch  fits only these shells (the others' rows printed from the table)

Its hide (B, U, S: how far it backs in, how high, how much it curls up) keeps nothing of it showing from any side once
it's in: a ray from any point on it, any way but down (the ground), meets the shell (its flames and smoke don't count,
nor anything see-through). Its peek (D, H, P or the skull's E, R, F, LK: see firemobs/hermit.py) is searched so that
its eyes come out only through the shell's mouth (or the skull's eye holes), never into its rim or its claws, the rest
of it staying hidden, and they look round as far as they can. mobs.py runs the same checks on every build
(hermit.model_check).

The rays are worked out once per shell on a grid of points round it (the shell's "hidden map"), so trying a pose is
quick. Needs numpy (and the Rimefall and golem kits, like mobs.py).
"""
import itertools
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path[:0] = [HERE, os.path.join(ROOT, "tools", "golems"), os.path.join(ROOT, "tools", "rimefall")]

import numpy as np  # noqa: E402

import mobs  # noqa: E402
from firemobs import hermit as Hm  # noqa: E402
from firedesigns import m02_hermit as DESIGN  # noqa: E402
from firemobs.common import Pose  # noqa: E402
from firekit.parts import Xform  # noqa: E402
from rimekit import mobkit  # noqa: E402

STEP = 2.0                  # the hidden map's spacing (units)
PEEK_HIDDEN = ("body", "legs", "claws", "antennae")   # what stays in the shell when it peeks: only its eyes show
SEE_THROUGH = ("flame", "smoke")     # a shell's materials that don't hide what's behind them (and any not opaque:
                                     # glass, see see_through())


def directions():
    """Every way but down: rings at 0, 25, 50 and 75 degrees up, and straight up."""
    out = []
    for el in (0.0, 25.0, 50.0, 75.0):
        n = 20 if el < 60 else 10
        for i in range(n):
            az = 2.0 * math.pi * (i + 0.5 * (el > 0)) / n
            out.append((math.cos(math.radians(el)) * math.cos(az), math.sin(math.radians(el)),
                        math.cos(math.radians(el)) * math.sin(az)))
    out.append((0.0, 1.0, 0.0))
    return np.array(out)


DIRS = directions()


def see_through(v):
    """The names of the materials of this model's shell you can see through: its flames and smoke, and any painted
    see-through (glass, ice: not opaque), so a crab behind clear glass counts as showing."""
    return set(SEE_THROUGH) | {n for n, p in DESIGN.materials(v).items() if not getattr(p, "opaque", True)}


def shell_boxes(m, see=SEE_THROUGH):
    """The shell's boxes that hide what's behind them, at rest: [(M, inv, lo, hi)] in blocks."""
    mats = m.pose_matrices(Pose().as_geo())
    return [mobkit._box_frame(mats, c) for c in m["shell"].cubes
            if not c.mat.startswith(SEE_THROUGH) and c.mat not in see]


def blocked(pts, frames):
    """[points x DIRS]: whether a ray from each point (blocks) meets one of the boxes."""
    hit = np.zeros((len(pts), len(DIRS)), dtype=bool)
    for M, inv, lo, hi in frames:
        o = (inv @ np.c_[pts, np.ones(len(pts))].T).T[:, :3]
        d = (inv[:3, :3] @ DIRS.T).T
        d = np.where(np.abs(d) < 1e-12, 1e-12, d)
        t1 = (lo[None, None, :] - o[:, None, :]) / d[None, :, :]
        t2 = (hi[None, None, :] - o[:, None, :]) / d[None, :, :]
        tmin = np.minimum(t1, t2).max(axis=2)
        tmax = np.maximum(t1, t2).min(axis=2)
        hit |= tmax >= np.maximum(tmin, 0.0)
    return hit


class HiddenMap:
    """Which points round a shell (at rest) can't be seen from outside it."""

    def __init__(self, m, see=SEE_THROUGH):
        frames = shell_boxes(m, see)
        corners = np.concatenate([(M @ np.c_[np.array(list(itertools.product(*zip(lo, hi)))), np.ones(8)].T).T[:, :3]
                                  for M, _i, lo, hi in frames]) * 16.0
        self.lo = corners.min(axis=0) - STEP
        hi = corners.max(axis=0) + STEP
        self.n = np.ceil((hi - self.lo) / STEP).astype(int) + 1
        axes = [self.lo[i] + np.arange(self.n[i]) * STEP for i in range(3)]
        X, Y, Z = np.meshgrid(*axes, indexing="ij")
        pts = np.c_[X.ravel(), Y.ravel(), Z.ravel()]
        hid = np.zeros(len(pts), dtype=bool)
        for i in range(0, len(pts), 4000):
            hid[i:i + 4000] = blocked(pts[i:i + 4000] / 16.0, frames).all(axis=1)
        self.hid = hid.reshape(tuple(self.n))
        self.front = float(corners[:, 2].min())          # the shell's mouth (its front: -z)

    def hidden(self, pts):
        """Whether each point (units) is hidden: every corner of the map's cell round it is."""
        g = (pts - self.lo) / STEP
        i0 = np.floor(g).astype(int)
        ok = np.all((i0 >= 0) & (i0 + 1 < self.n), axis=1)
        out = ok.copy()
        for dx, dy, dz in itertools.product((0, 1), repeat=3):
            idx = np.clip(i0 + [dx, dy, dz], 0, self.n - 1)
            out &= self.hid[idx[:, 0], idx[:, 1], idx[:, 2]]
        return out


def crab_points(m, pose, drop):
    """{part: points (units, in the shell's rest frame)} of the crab's boxes posed: corners, edges' and faces' middles."""
    mats = m.pose_matrices(pose)
    out = {}
    for b in m.bones:
        if b.name in ("root", "shell") or not b.cubes:
            continue
        if abs(float(np.linalg.det(mats[b.name][:3, :3]))) < 1e-9:
            continue
        part = "eyes" if b.name.startswith("eye") else ("claws" if b.name.startswith(
            ("claw", "fore", "pincer", "finger")) else ("legs" if b.name.startswith(("leg", "knee", "ankle"))
                                                        else "body"))
        for c in b.cubes:
            M, _i, lo, hi = mobkit._box_frame(mats, c)
            loc = np.array(list(itertools.product(*[(a, (a + z) / 2.0, z) for a, z in zip(lo, hi)])))
            # (its antennae: their own part; when it peeks only its eyes show, October 27)
            out.setdefault("antennae" if c.mat == "antenna" else part, []).append(
                (M @ np.c_[loc, np.ones(len(loc))].T).T[:, :3] * 16.0)
    return {k: np.concatenate(v) + np.array([0.0, drop, 0.0]) for k, v in out.items()}


def final_pose(prm, drop, eyes_in=1.0, back=1.0):
    p = Pose()
    Hm.hidden(p, prm, drop, 1.0, back, eyes_in, settle=1.0)
    return p.as_geo()


def showing(hmap, parts, only=None):
    """{part: how many of its points show}."""
    return {k: int((~hmap.hidden(v)).sum()) for k, v in parts.items() if only is None or k in only}


# ---------------------------------------------------------------- its peek
#
# October 28, 2026, the user: "I need the eyes to pop out in a way where they don't clip through the top of the shell
# ... also look left and right". The peek (firemobs/hermit.py: the big claw dropping a little to open a gap, the eyes
# pointed out ahead sliding out over it, popping up past the rim, looking one way and the other about the point where
# they leave the mouth) is tried with each shell's D, H, P (the skull: its E scaled), R, F and LK; it must keep:
#   its eyes never seen outside the shell's openings (poking out through its top or sides),
#   its eyes never into the shell where you can see it (its rim, in front of its mouth's dark: CLIP units at most),
#   its eyes never into its claws or antennae, nor those into its eyes,
#   the rest of it all hidden, its claw above the ground, its stalks' roots inside,
#   and its eyes out at the end (most of the eye balls seen), looking round as far as it can (LK).

ALLOWED = ("dark", "socketglow")      # the shell's materials its eyes may go into: its mouth's dark (they come out of
                                      # it) and the skull's glowing eye holes (they come out of those)
CLIP = 0.6                            # units a part may sink into another before it counts (mobkit's CLIP_TOLERANCE)
EYE_GRID = 0.75                       # spacing of the points tried on its eyes (units)
FOOT = 3.0                            # a stalk's root: its points within this of the stalk's turning point (units)
OUT_SEEN = 0.9                        # out: this share of its eye balls' points seen
DOORS = tuple((d, d2, y) for y in (0.0, 4.0, 8.0) for d in (0.0, 8.0, 16.0, -8.0) for d2 in (0.0, 20.0, -20.0))
HEIGHTS = tuple(float(h) for h in range(6, 29, 2))
LEANS = (40.0, 50.0, 30.0, 60.0, 70.0, 85.0)     # (85: still pointing out ahead, as they slid)
TURN_POINTS = (0.35, 0.5, 0.2, 0.65)
LOOKS = tuple((lk, lx) for lk, lx in sorted(((lk, lx) for lk in (40.0, 30.0, 20.0, 10.0) for lx in (10.0, 7.0, 4.0, 0.0)),
                                              key=lambda q: -(q[1] + q[0] / 5.0)))   # (LK degrees, LX units): swing first
SIZES = (1.0, 0.85, 0.7)              # O: its eyes once out, against the crab's size (as big as there's room for)
SKULL_SIZES = (0.85, 0.7, 0.6, 0.5)   # (the skull's eye holes are small: its eyes stay smaller, and part of each one
SKULL_SEEN = 0.6                      # may stay in its hole)
SLIDE_TRY = 72.0                      # the furthest slide tried (units, before S)
SKULL_CLIP = 1.0                      # the skull: its eye holes' dark and the bone round them overlap a little, so its
                                      # eyes may brush the hole's edge by this much (units)
SKULL_FRONT = (1.0, 4.0, 2.0)         # the skull: how far in front of its eye holes' front the eye balls end (units)


def openings(m):
    """(z of the mouth's front, [(x low, x high, y low, y high)] (units)): the shell's mouth (round its biggest dark
    box and every other dark box in its plane) and any other dark hole facing forward (the skull's eye holes)."""
    mats = m.pose_matrices(Pose().as_geo())
    boxes = []
    for c in m["shell"].cubes:
        if c.mat != "dark":
            continue
        M, _i, lo, hi = mobkit._box_frame(mats, c)
        pts = (M @ np.c_[np.array(list(itertools.product(*zip(lo, hi)))), np.ones(8)].T).T[:, :3] * 16.0
        size = hi - lo
        normal = M[:3, int(np.argmin(size))]
        normal = normal / np.linalg.norm(normal)
        centre = (M @ np.append((lo + hi) / 2.0, 1.0))[:3] * 16.0
        boxes.append((float(np.prod(np.sort(size)[1:])), pts, centre, normal))
    _a, _p, c0, n0 = max(boxes, key=lambda b: b[0])
    plane = [b for b in boxes if abs(float(np.dot(b[2] - c0, n0))) <= 2.5]
    pts = np.concatenate([b[1] for b in plane])
    hw = float(np.abs(pts[:, 0]).max())
    rects = [(-hw, hw, float(pts[:, 1].min()), float(pts[:, 1].max()))]
    for b in boxes:
        if all(b is not q for q in plane) and abs(float(b[3][2])) > 0.7:
            q = b[1]
            rects.append((float(q[:, 0].min()), float(q[:, 0].max()), float(q[:, 1].min()), float(q[:, 1].max())))
    return float(pts[:, 2].min()), rects


class PeekCheck:
    """Tries poses of its peek against one shell (see above)."""

    def __init__(self, m, v, drop):
        self.m, self.drop = m, drop
        self.hmap = HiddenMap(m, see_through(v))
        self.zf, self.rects = openings(m)
        self.clip = SKULL_CLIP if v["shell"] == "skull" else CLIP
        self._solid = {}
        self.pivots = {s: np.array([-Hm.EYE_PIVOT[s][0], Hm.EYE_PIVOT[s][1], Hm.EYE_PIVOT[s][2]]) / 16.0
                       for s in ("l", "r")}
        self.base_claw, self.base_shows = 0.0, 0
        base = self.trouble(final_pose(Hm.HIDE.get(v["shell"], Hm.HIDE_DEFAULT), drop))
        self.base_claw, self.base_shows = base["claw"], base["shows"]     # (as it hides: no worse than that)

    def solid(self, mats, sy):
        key = round(sy, 3)
        if key not in self._solid:
            self._solid[key] = [mobkit._box_frame(mats, c) for c in self.m["shell"].cubes
                                if c.mat not in ALLOWED and not c.mat.startswith(SEE_THROUGH)]
        return self._solid[key]

    def trouble(self, pose, body=True):
        """{what: how bad} for one pose (geo); see ok()."""
        m = self.m
        mats = m.pose_matrices(pose)
        sy = pose.get("shell", ((0, 0, 0), (0, 0, 0), (1, 1, 1)))[1][1]
        lift = np.array([0.0, -sy, 0.0])
        groups = {"eyes": [], "claws": [], "antennae": [], "rest": []}
        balls = []
        for b in m.bones:
            if not b.cubes or b.name in ("root", "shell"):
                continue
            if abs(float(np.linalg.det(mats[b.name][:3, :3]))) < 1e-9:
                continue
            g = ("eyes" if b.name.startswith("eye") else "antennae" if b.name.startswith("antenna") else
                 "claws" if b.name.startswith(("claw", "fore", "pincer", "finger")) else "rest")
            for c in b.cubes:
                fr = mobkit._box_frame(mats, c)
                groups[g].append(fr)
                if g == "eyes" and c.mat == "eye":
                    balls.append(mobkit._grid(fr, EYE_GRID))
        eye_pts = np.concatenate([mobkit._grid(f, EYE_GRID) for f in groups["eyes"]])
        eu = eye_pts * 16.0 + lift
        out = {}
        hid = self.hmap.hidden(eu)
        behind = eu[:, 2] > self.zf + 2.0
        inwin = np.zeros(len(eu), dtype=bool)
        for x0, x1, y0, y1 in self.rects:
            inwin |= (eu[:, 0] >= x0 - 1.0) & (eu[:, 0] <= x1 + 1.0) & (eu[:, 1] >= y0 - 1.0) & (eu[:, 1] <= y1 + 1.0)
        out["poke"] = int((~hid & behind & ~inwin).sum())
        front = ~behind
        d = 0.0
        if front.any():
            fp = eye_pts[front]
            for fr in self.solid(mats, sy):
                d = max(d, float(mobkit._inside_depth(fp, fr).max()))
        out["rim"] = d
        # (its eyes and its claws or antennae in each other: only where it can be seen; deep in the shell, out of
        # sight, they may pass each other: the skull's eyes go up past its folded claw inside the bone)
        d = 0.0
        seen_eyes = eye_pts[~hid]
        for g in ("claws", "antennae"):
            gp = np.concatenate([mobkit._grid(f) for f in groups[g]])
            gp = gp[~self.hmap.hidden(gp * 16.0 + lift)]
            for fr in groups[g]:
                if len(seen_eyes):
                    d = max(d, float(mobkit._inside_depth(seen_eyes, fr).max()))
            for fr in groups["eyes"]:
                if len(gp):
                    d = max(d, float(mobkit._inside_depth(gp, fr).max()))
        out["claw"] = d
        feet = []
        for s in ("l", "r"):
            piv = (mats[f"eye_{s}"] @ np.append(self.pivots[s], 1.0))[:3]
            feet.append(eye_pts[np.linalg.norm(eye_pts - piv, axis=1) * 16.0 <= FOOT])
        out["feet"] = int((~self.hmap.hidden(np.concatenate(feet) * 16.0 + lift)).sum())
        bu = np.concatenate(balls) * 16.0 + lift
        out["seen"] = float((~self.hmap.hidden(bu)).mean())
        out["out"] = float(bu[:, 2].max())          # (its eye balls' back: in front of the mouth when it's < zf)
        if body:
            rest = groups["rest"] + groups["claws"] + groups["antennae"]
            pts = np.concatenate([mobkit._grid(f, 3.0) for f in rest])
            out["shows"] = int((~self.hmap.hidden(pts * 16.0 + lift)).sum())
            out["floor"] = max(0.0, -float(np.concatenate([mobkit._grid(f, 3.0) for f in groups["claws"]])[:, 1].min()
                                           * 16.0))
        return out

    def ok(self, tr):
        return (tr["poke"] == 0 and tr["rim"] <= self.clip and tr["claw"] <= self.base_claw + CLIP and tr["feet"] == 0
                and tr.get("shows", 0) <= self.base_shows and tr.get("floor", 0.0) <= CLIP)


def _pose(prm, drop, door, **eye):
    p = Pose()
    Hm.hidden(p, prm, drop, 1.0, 1.0, eye.pop("eyes_in", 0.0), 1.0, door, **eye)
    return p.as_geo()


def door_ok(chk, prm, drop):
    """Whether its claw can drop (D, D2) and the folded crab sink (Y) with everything but its eyes still hidden."""
    return all(chk.ok(chk.trouble(_pose(prm, drop, f, eyes_in=1.0))) for f in np.linspace(0.25, 1.0, 4))


def slide_reach(chk, prm, drop):
    """How far (0..1 of its slide) its eyes can slide out before something goes wrong, the eyes rising (still folded
    back) and unfolding first, the door open (0 if that already fails). The rest of it doesn't move meanwhile: only
    its eyes are tried."""
    for f in np.linspace(0.25, 1.0, 4):
        if not chk.ok(chk.trouble(_pose(prm, drop, 1.0, eyes_in=1.0, height=f), body=False)):
            return 0.0
    for f in np.linspace(0.2, 1.0, 5):
        if not chk.ok(chk.trouble(_pose(prm, drop, 1.0, eyes_in=1.0 - f, aim=f, height=1.0), body=False)):
            return 0.0
    best = 0.0
    for s in np.linspace(0.0, 1.0, 37):
        if not chk.ok(chk.trouble(_pose(prm, drop, 1.0, aim=1.0, height=1.0, slide=s), body=False)):
            break
        best = float(s)
    return best


def stand_ok(chk, prm, drop, seen=OUT_SEEN):
    """Whether its eyes stand up out of the mouth cleanly and end up out (`seen` of them) with these settings."""
    for f in np.linspace(0.0, 1.0, 6):
        tr = chk.trouble(_pose(prm, drop, 1.0, aim=1.0, height=1.0, slide=1.0, stand=f), body=False)
        if not chk.ok(tr):
            return False
    return tr["seen"] >= seen


def look_ok(chk, prm, drop):
    """Whether its eyes can look round (LK each way) and twitch, once out, with these settings."""
    for look in (1.0, -1.0, 0.5, -0.5, 0.75, -0.75, 0.25, -0.25):
        if not chk.ok(chk.trouble(_pose(prm, drop, 1.0, aim=1.0, height=1.0, slide=1.0, stand=1.0, look=look),
                                  body=False)):
            return False
    return all(chk.ok(chk.trouble(_pose(prm, drop, 1.0, aim=1.0, height=1.0, slide=1.0, stand=1.0, twitch=tw),
                                  body=False)) for tw in (-6.0, 6.0))


def eye_hole(m, drop):
    """The middle of the skull's left eye hole (units, the shell settled) and the z of its front."""
    p = Pose()
    p.pos("shell", 0.0, -drop, 0.0)
    mats = m.pose_matrices(p.as_geo())
    for c in m["shell"].cubes:
        if c.mat != "dark":
            continue
        M, _i, lo, hi = mobkit._box_frame(mats, c)
        q = (M @ np.c_[np.array(list(itertools.product(*zip(lo, hi)))), np.ones(8)].T).T[:, :3] * 16.0
        if q[:, 0].max() < -5.0:                       # (its left one: geo x is the design's flipped)
            return q.mean(axis=0), float(q[:, 2].min())
    raise ValueError("no eye hole")


def fit_skull(m, v, drop, base, chk):
    """
    The skull's peek (October 28: its eyes out through its eye holes, not through the bone above them): its eyes
    slide straight to the middle of the eye holes, the slide E worked out so the eye balls end just in front of them
    (SKULL_FRONT), no lift (H 0: they go past its folded claw inside the bone, where nobody sees), no door; then the
    smallest eyes, lean and turning point that let them look round furthest.
    """
    mid, front = eye_hole(m, drop)

    def ball(prm):
        pose = _pose(prm, drop, 1.0, aim=1.0, height=1.0, slide=1.0, stand=1.0)
        mats = m.pose_matrices(pose)
        u = np.concatenate([mobkit._grid(mobkit._box_frame(mats, c), EYE_GRID) for c in m["eye_l"].cubes
                            if c.mat == "eye"])
        return u.mean(axis=0) * 16.0

    best = None
    for dz in SKULL_FRONT:
        target = np.array([mid[0], mid[1], front - dz])
        for O in SKULL_SIZES:
            for R in LEANS[::-1]:
                for F in TURN_POINTS:
                    prm = dict(base, D=0.0, D2=0.0, Y=0.0, H=0.0, E=(0.0, 0.0, 0.0), R=R, F=F, LK=0.0, LX=0.0, O=O)
                    d = (target - ball(prm)) / base["S"]
                    prm["E"] = (round(float(-d[0]), 1), round(float(d[1]), 1), round(float(d[2]), 1))
                    if slide_reach(chk, prm, drop) < 0.999 or not stand_ok(chk, prm, drop, SKULL_SEEN):
                        continue
                    for LK, LX in SKULL_LOOKS:
                        if best is not None and LK + 3.0 * LX <= best["LK"] + 3.0 * best["LX"]:
                            break
                        if look_ok(chk, dict(prm, LK=LK, LX=LX), drop):
                            best = dict(prm, LK=LK, LX=LX)
                            print(f"  skull: eyes {O}, lean {R}, turning at {F}, {dz} out: looks round {LK:.0f} and "
                                  f"{LX:.0f} to the side", flush=True)
                            break
        if best is not None:
            return best, chk
    return best, chk


SKULL_LOOKS = tuple((float(a), float(b)) for a, b in ((30, 3), (25, 3), (20, 2), (20, 0), (15, 2), (15, 0), (10, 0),
                                                      (5, 0)))


def fit_peek(m, v, drop, base):
    """Its peek's settings for this shell (merged into `base`, its hide's B, U and S), or None: of the doors and
    heights its eyes slide out furthest at, the furthest slide, lean (R) and turning point (F) that let them look
    round furthest (LK). The skull: fit_skull."""
    chk = PeekCheck(m, v, drop)
    skull = v["shell"] == "skull"
    if skull:
        return fit_skull(m, v, drop, base, chk)

    def with_slide(prm, frac):
        prm["P"] = round(SLIDE_TRY * frac, 1)
        return prm

    best = None
    for D, D2, Y in sorted(DOORS, key=lambda d: abs(d[0]) + abs(d[1]) + 4.0 * d[2]):
        door = dict(D=D, D2=D2, Y=Y)
        if not door_ok(chk, with_slide(dict(base, R=40.0, F=0.35, LK=0.0, H=0.0, **door), 1.0), drop):
            print(f"  {v['shell']}: {door}: the claw shows", flush=True)
            continue
        for H in HEIGHTS:
            r = slide_reach(chk, with_slide(dict(base, R=40.0, F=0.35, LK=0.0, H=H, **door), 1.0), drop)
            if r <= 0.0:
                continue
            found = None
            for O in SIZES:
                for frac in (r, r - 2.0 / 36.0):
                    for R in LEANS:
                        for F in TURN_POINTS:
                            prm = with_slide(dict(base, R=R, F=F, LK=0.0, LX=0.0, O=O, H=H, **door), frac)
                            if frac <= 0.0 or not stand_ok(chk, prm, drop):
                                continue
                            for LK, LX in LOOKS:
                                if found is not None and score(LK, LX, O) <= score(found["LK"], found["LX"], found["O"]):
                                    break
                                if look_ok(chk, dict(prm, LK=LK, LX=LX), drop):
                                    found = dict(prm, LK=LK, LX=LX)
                                    break
                if found is not None:
                    break                         # (the biggest eyes that fit)
            print(f"  {v['shell']}: {door} H {H:.0f}: slides out {r:.2f}; "
                  + (f"eyes {found['O']}, looks round {found['LK']:.0f} and {found['LX']:.0f} to the side (R "
                     f"{found['R']:.0f}, F {found['F']})" if found else "can't stand up or look round"), flush=True)
            if found is not None and (best is None or score(found["LK"], found["LX"], found["O"])
                                      > score(best["LK"], best["LX"], best["O"])):
                best = found
            if best is not None and score(best["LK"], best["LX"], best["O"]) >= score(*LOOKS[0], SIZES[0]):
                return best, chk
        if best is not None:
            return best, chk                  # (the simplest door that works: the claw moving as little as it can)
    return best, chk


def score(LK, LX, O):
    """How well a peek shows: its eyes' size first, then how far they swing to look round."""
    return O * 100.0 + LX + LK / 5.0


def mouth(m):
    """(x half-width, y low, y high, z of its front) of the shell's mouth (units), from openings()."""
    zf, rects = openings(m)
    x0, x1, y0, y1 = rects[0]
    return x1, y0, y1, zf


def check_motion(m, v, rig, hmap=None):
    """
    [(animation, seconds, part, points)]: moments of its hide and peek when part of it is behind the shell's mouth but
    not in line with it (going in through the shell's side, or sticking out beside it) rather than through its mouth;
    with a hidden map, only the points of it that can be seen (folded up inside a solid shell, out of line is fine).
    """
    hw, ylo, yhi, zf = mouth(m)
    out = []
    anims = Hm.model_animations(rig, m, v)
    for name, a in anims.items():
        for t in np.arange(0.0, a.length + 1e-6, 0.05):
            pose = a.sample(float(t))
            sy = pose.get("shell", ((0, 0, 0), (0, 0, 0), (1, 1, 1)))[1][1]
            for part, pts in crab_points(m, pose, -sy).items():
                if part == "eyes":
                    continue                     # (its eyes: check_peek's)
                behind = pts[:, 2] > zf + 2.0
                outside = (np.abs(pts[:, 0]) > hw + 1.0) | (pts[:, 1] < ylo - 1.0) | (pts[:, 1] > yhi + 1.0)
                bad = behind & outside
                if hmap is not None and bad.any():
                    bad &= ~hmap.hidden(pts)
                if bad.any():
                    out.append((name, float(t), part, int(bad.sum())))
    return out


def check_peek(m, v, rig, chk=None):
    """{animation: (worst problem, seconds)}: its baked hide, peek and come_out tried like the search's poses (the
    rest of it only while it's all in its shell)."""
    chk = chk or PeekCheck(m, v, Hm.shell_drop(m))
    out = {}
    for name, a in Hm.model_animations(rig, m, v).items():
        for t in np.arange(0.0, a.length + 1e-6, 0.05):
            pose = a.sample(float(t))
            if (name == "hide" and t < 0.95) or (name == "come_out" and t > 1.4):
                continue                         # (going in or out: check_motion's)
            tr = chk.trouble(pose, body=name != "hide")       # (the hide's own settling: its fit's business)
            if not chk.ok(tr):
                bad = {k: v2 for k, v2 in tr.items() if k in ("poke", "rim", "claw", "feet", "shows", "floor")}
                out.setdefault(name, (bad, float(t)))
    return out


def hide_row(shell, prm):
    """Its line in firemobs/hermit.py's HIDE."""
    slide = (f"E=({prm['E'][0]:.1f}, {prm['E'][1]:.1f}, {prm['E'][2]:.1f})" if "E" in prm else f"P={prm['P']:.1f}")
    return (f'    "{shell}": dict(B={prm["B"]:.1f}, U={prm["U"]:.1f}, S={prm["S"]:.2f}, D={prm["D"]:.1f}, '
            f'D2={prm.get("D2", 0.0):.1f}, Y={prm.get("Y", 0.0):.1f}, H={prm["H"]:.1f}, {slide}, R={prm["R"]:.1f}, '
            f'F={prm["F"]:.2f}, LK={prm["LK"]:.1f}, LX={prm.get("LX", 0.0):.1f}, O={prm.get("O", 1.0):.2f}),')


def main(argv):
    check = "--check" in argv
    only = [a for a in argv if not a.startswith("--")]
    rig = None
    rows = []
    for model_id, v in Hm.MODELS:
        if only and v["shell"] not in only:
            rows.append(hide_row(v["shell"], Hm.HIDE[v["shell"]]))
            continue
        m, _t = mobkit.build_model(mobs.model_spec(Hm, model_id, v), verbose=False)
        if rig is None:
            rig = Hm.make_rig(m)
        drop = Hm.shell_drop(m)
        chk = None
        if not check:
            base = {k: Hm.HIDE[v["shell"]][k] for k in ("B", "U", "S")}
            prm, chk = fit_peek(m, v, drop, base)
            if prm is None:
                print(f"{model_id} ({v['shell']}): NO PEEK FITS", flush=True)
                rows.append(hide_row(v["shell"], Hm.HIDE[v["shell"]]))
                continue
            Hm.HIDE[v["shell"]] = prm
        prm = Hm.HIDE[v["shell"]]
        motion = check_motion(m, v, rig, (chk or PeekCheck(m, v, drop)).hmap)
        worst = {}
        for name, t, part, n in motion:
            if n > worst.get((name, part), (0, 0))[0]:
                worst[(name, part)] = (n, t)
        peek = check_peek(m, v, rig, chk)
        print(f"{model_id} ({v['shell']}): {prm}, drop {drop:.1f}; out of the sides on the way: "
              + (", ".join(f"{a} {p} {n} at {t:.2f} s" for (a, p), (n, t) in sorted(worst.items())) or "none")
              + "; its eyes: " + (", ".join(f"{a} {bad} at {t:.2f} s" for a, (bad, t) in peek.items()) or "clean"),
              flush=True)
        rows.append(hide_row(v["shell"], prm))
    print("\nHIDE = {\n" + "\n".join(rows) + "\n}")


if __name__ == "__main__":
    main(sys.argv[1:])
