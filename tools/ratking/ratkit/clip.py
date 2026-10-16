"""
Clipping checks for the Rat King, run on every design by build.py (and on its own: `python tools/ratking/build.py
--clip <n>`). They sample each animation as the game plays it (the baked keyframes, blended the way GeckoLib does) and
look for:

  - the staff (and anything on it) passing through his ears, his head, his crown or his body (the right fist that
    holds it doesn't count);
  - his ears passing through his crown or his body;
  - the staff, or his body while he lies down, sinking into the floor (not while he's burrowing: then he and his staff
    go into the hole on purpose).

A point counts as inside a box when it is more than `TOLERANCE` units in; the report gives the deepest one per pair and
animation, and when it happens.
"""
import math

import numpy as np

from kit import geo
from kit.clipping import _box_frame, _inside_depth

TOLERANCE = 0.3          # units a part may sink into another before it counts
FLOOR_TOLERANCE = 0.3    # units below the floor before it counts
STEP = 0.05              # seconds between checked frames
HOLE_RADIUS = 9.0        # units round the hole's middle where things may go underground (burrow and popup)
SURFACE_BAND = 3.0       # units under the ground still counted as "crossing the ground" (deeper is out of sight)

UNDERGROUND = ("burrow", "burrowed", "popup")


def _group(bone):
    """Which group a bone's boxes belong to (None: not checked)."""
    names = [a.name for a in bone.ancestors()]
    if "hole" in names:
        return None
    if any(n == "staff" for n in names):
        return "staff"
    if any(n == "crown" for n in names):
        return "crown"
    if bone.name in ("ear_l", "ear_r"):
        return "ears"
    if bone.name in ("head", "jaw", "tongue", "neck") or bone.name.startswith(("lid_", "xeye_")):
        return "head"
    if bone.name in ("hand_r", "forearm_r"):
        return "grip"
    if bone.name.startswith(("cape_",)):
        return "cape"
    return "body"


def _skip_cube(group, cube):
    # Whiskers, eyelids, X eyes and the tongue are thin or hidden: never counted.
    return cube.tag in ("whisker", "lid", "tongue") or (cube.mat == "xmark")


def _grid(frame, spacing=1.0):
    """Points spread through a box (GeckoLib space): its corners, faces and middle, about `spacing` units apart."""
    M, _inv, lo, hi = frame
    axes = []
    for k in range(3):
        n = max(2, int(math.ceil((hi[k] - lo[k]) * 16.0 / spacing)) + 1)
        axes.append(np.linspace(lo[k], hi[k], n))
    X, Y, Z = np.meshgrid(*axes, indexing="ij")
    local = np.c_[X.ravel(), Y.ravel(), Z.ravel()]
    return (M @ np.c_[local, np.ones(len(local))].T).T[:, :3]


def _boxes(m, mats):
    """{group: [(frame, label)]}: every checked box's frame and "bone/tag"."""
    out = {}
    for bone in m.bones:
        if not bone.cubes:
            continue
        g = _group(bone)
        if g is None:
            continue
        for c in bone.cubes:
            if _skip_cube(g, c):
                continue
            out.setdefault(g, []).append((_box_frame(mats, c), f"{bone.name}/{c.tag}"))
    return out


PAIRS = (("staff", "ears"), ("staff", "head"), ("staff", "crown"), ("staff", "body"), ("staff", "cape"),
         ("ears", "crown"), ("ears", "body"))
PAIR_TOLERANCE = {"ears/crown": 0.6}     # the ears' roots sit just under the crown's band


def _tolerance(frame):
    """A thin box (an ear, a cape panel: 1 unit) counts as passed through well before the usual tolerance."""
    _M, _inv, lo, hi = frame
    half = float(np.min(hi - lo)) * 16.0 / 2.0
    return min(TOLERANCE, half * 0.5)


def _pair_depth(pts_a, boxes_b, pts_b=None, boxes_a=None):
    """The deepest any point of A goes into a box of B (and B into A, if given), past each box's tolerance."""
    best, label = 0.0, ""
    for fr, lab in boxes_b:
        d = float(_inside_depth(pts_a, fr).max()) - _tolerance(fr) + TOLERANCE
        if d > best:
            best, label = d, lab
    if pts_b is not None and boxes_a is not None:
        for fr, lab in boxes_a:
            d = float(_inside_depth(pts_b, fr).max()) - _tolerance(fr) + TOLERANCE
            if d > best:
                best, label = d, lab
    return best, label


def check(m, anims, hole_z=0.0, pairs=PAIRS, step=STEP, only=None):
    """
    Returns {(animation, what): (deepest units, at seconds, where)} for every clash deeper than the tolerance. `what`
    is "staff/ears" and so on, "staff/floor" or "body/floor"; `where` names the box. `hole_z` is where the hole's
    middle is (model z, units; x 0) unless an animation's meta says otherwise (`hole_x`, `hole_z`: the pop-up's hole is
    right under him, the burrow's in front of him).
    """
    found = {}
    for name, a in anims.items():
        if only and name not in only:
            continue
        hx = a.meta.get("hole_x", 0.0)
        hz = a.meta.get("hole_z", hole_z)
        ts = np.arange(0.0, a.length + 1e-6, step) if a.length > 0 else [0.0]
        for t in ts:
            mats = m.pose_matrices(a.sample(float(t)))       # model space: his front is -z, like the hole
            boxes = _boxes(m, mats)
            pts = {g: np.concatenate([_grid(fr) for fr, _l in frs]) for g, frs in boxes.items()}
            if name in UNDERGROUND:
                # Deep under the ground nothing shows: only what's above it (or just under it) counts.
                pts = {g: v[v[:, 1] * 16.0 > -SURFACE_BAND] for g, v in pts.items()}
                pts = {g: v for g, v in pts.items() if len(v)}
            for ga, gb in pairs:
                if ga not in pts or gb not in boxes:
                    continue
                deepest, where = _pair_depth(pts[ga], boxes[gb], pts.get(gb), boxes.get(ga))
                tol = PAIR_TOLERANCE.get(f"{ga}/{gb}", TOLERANCE)
                if deepest > tol:
                    key = (name, f"{ga}/{gb}")
                    if key not in found or deepest > found[key][0]:
                        found[key] = (deepest, float(t), where)
            # The floor (y = 0): the staff always; the body counts too (the feet and the tail lie on it, within
            # the tolerance).
            for g in ("staff", "body", "head", "ears", "crown"):
                if g not in boxes:
                    continue
                low, where = 0.0, ""
                for fr, lab in boxes[g]:
                    p = _grid(fr)
                    y = p[:, 1] * 16.0
                    if name in UNDERGROUND:
                        # Burrowing: things may go underground through the hole, and anything deep under the ground
                        # is out of sight; only parts crossing the ground away from the hole count.
                        bx = -p[:, 0] * 16.0
                        bz = p[:, 2] * 16.0
                        near = np.hypot(bx - hx, bz - hz) < getattr(m, "hole_radius", HOLE_RADIUS)
                        y = np.where(near | (y < -SURFACE_BAND), 0.0, y)
                    if -y.min() > low:
                        low, where = float(-y.min()), lab
                if low > FLOOR_TOLERANCE:
                    key = (name, f"{g}/floor")
                    if key not in found or low > found[key][0]:
                        found[key] = (low, float(t), where)
    return found


def report(design_id, found, quiet=False):
    if not found:
        if not quiet:
            print(f"  {design_id}: no clipping (staff, ears, crown, body, floor)")
        return True
    for (name, what), (depth, t, where) in sorted(found.items()):
        print(f"  {design_id}: CLIP {what} in {name} at {t:.2f} s, {depth:.1f} units deep {where}")
    return False
