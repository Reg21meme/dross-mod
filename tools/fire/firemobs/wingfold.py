"""
The Emberdactyl's wing membranes folding out of its body's way (the user, October 27, 2026: "the back half of his wing
intersects with his body" when it flies, and "when he walks part of his claw or his wing part goes through his body").

Each stretch of membrane hangs on a bone of its own (firekit/emberdactyl.py: mem_<wing bone>_<side>) hinged along the
wing bone it hangs from. After each frame of an animation is posed, every stretch is turned about its hinge (its
trailing edge lifted up towards the bones, like a membrane flexing or a wing folding) by the least that keeps it out of
the body, legs, tail, neck and head and above the ground; then each stretch's fold is smoothed over time (never below
what a frame needs), so it flexes smoothly instead of flicking. Neighbouring stretches fold together a little
(COUPLE), so the membrane doesn't split open between them.
"""
import math

import numpy as np

from kit import geo
from kit.anims import Animation
from kit.clipping import _box_frame, _inside_depth
from rimekit.mobkit import STEP

CLEAR = -1.0            # units: a membrane keeps this far off the body (less than 0: a gap; the check allows 0.6 in)
FLOOR = 0.3             # units it keeps above the ground
SPACING = 1.5           # units between the points sampled on each membrane plate
STEP_DEG = 3.0          # how finely the fold is searched (first in steps of COARSE_DEG)
COARSE_DEG = 9.0
MAX_DEG = 150.0         # the most a stretch ever folds
HOLD = 3                # frames: each fold is held this long either side of the frames that need it ...
BLUR = 3                # ... and then eased over this many frames either side (so it's never less than needed)
COUPLE = 0.92                  # a stretch folds at least this share of its neighbour's fold (so the wing bends as one surface)
SQUEEZE = 0.45          # a stretch that can't fold clear even all the way is furled to this share more there


def _points(frame, spacing=SPACING):
    """Points spread through one box (GeckoLib space)."""
    M, _inv, lo, hi = frame
    axes = []
    for k in range(3):
        n = max(2, int(math.ceil((hi[k] - lo[k]) * 16.0 / spacing)) + 1)
        axes.append(np.linspace(lo[k], hi[k], n))
    X, Y, Z = np.meshgrid(*axes, indexing="ij")
    local = np.c_[X.ravel(), Y.ravel(), Z.ravel()]
    return (M @ np.c_[local, np.ones(len(local))].T).T[:, :3]


def _box_bounds(frame):
    M, _inv, lo, hi = frame
    corners = np.array([[x, y, z] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])])
    w = (M @ np.c_[corners, np.ones(8)].T).T[:, :3]
    return w.min(axis=0), w.max(axis=0)


def _gdir(d):
    """A Bedrock direction in GeckoLib space (x flipped), unit length."""
    g = np.array([-d[0], d[1], d[2]], dtype=float)
    return g / np.linalg.norm(g)


def orient(m, frames, k):
    """
    Turns each membrane bone to its own frame (`frames`: {bone: (a point on its hinge, the hinge's direction, the way
    across it)}, design units): its y along the hinge, its z across the membrane to the trailing edge; its boxes (and
    its children's pivots) turned back the other way, so it looks just as it was built. Then a scale of (s, 1, s) on
    the bone furls the membrane onto its hinge (on the ground, like a pterosaur's folded wing), and a turn about its
    y folds it about the hinge. GeckoLib turns a bone's boxes by its rest turn, as the kit does (Model.pose_matrices).
    """
    def centres(mats):
        return [mats[c.bone.name] @ c.cube_matrix() @ np.append(geo.gecko(c.center()), 1.0)
                for bone in frames for c in m[bone].cubes]

    before = centres(m.pose_matrices())
    for bone, (_pivot, hdir, across) in frames.items():
        b = m[bone]
        y = _gdir(hdir)
        z = _gdir(across)
        z = z - y * float(np.dot(z, y))
        z /= np.linalg.norm(z)
        R0 = np.column_stack([np.cross(y, z), y, z])
        b.rotation = tuple(geo.euler_bedrock(R0))
        R0 = geo.gecko_rotation(*b.rotation)[:3, :3]
        p = geo.gecko(b.pivot)
        for c in b.cubes:
            Rc = c.cube_matrix()[:3, :3]
            pc = geo.gecko(c.pivot if (c.rotation and any(c.rotation) and c.pivot) else c.center())
            pc2 = p + R0.T @ (pc - p)
            d = geo.bedrock(pc2) - geo.bedrock(pc)
            c.origin = tuple(float(c.origin[i] + d[i]) for i in range(3))
            c.rotation = tuple(geo.euler_bedrock(R0.T @ Rc))
            c.pivot = tuple(float(v) for v in geo.bedrock(pc2))
        for child in b.children:
            child.pivot = tuple(float(v) for v in geo.bedrock(p + R0.T @ (geo.gecko(child.pivot) - p)))
    # (check: every box and every marker exactly where it was)
    if not np.allclose(np.array(before), np.array(centres(m.pose_matrices())), atol=1e-4):
        raise ValueError("orient: a membrane's box moved")


class Hinge:
    """One stretch of membrane: its bone, its wing bone, its points at rest, its hinge and its frame (GeckoLib space),
    and which way round lifts its trailing edge."""

    def __init__(self, m, bone, parent):
        self.bone = bone
        self.parent = parent
        b = m[bone]
        rest = m.pose_matrices()
        self.pts = np.concatenate([_points(_box_frame(rest, c)) for c in b.cubes])
        self.piv = geo.gecko(b.pivot)
        self.rest = np.array(b.rotation, dtype=float)
        self.R0 = geo.gecko_rotation(*b.rotation)[:3, :3]
        self.axis = self.R0[:, 1]
        self.across = self.R0[:, 2]
        # +: the way that lifts the membrane's middle (up, towards the bone's top side)
        lift = (self.moved(10.0, 1.0)).mean(axis=0)[1] - self.pts.mean(axis=0)[1]
        self.sign = 1.0 if lift >= 0.0 else -1.0

    def rotation(self, deg):
        return geo.axis_angle(self.axis, math.radians(deg) * getattr(self, "sign", 1.0))

    def moved(self, deg, furl):
        """Its points folded `deg` about the hinge and furled (bind space): `furl` a share of their width, or (that
        share, a share of their length along the hinge too: drawn in to the hinge's joint)."""
        f, g = furl if isinstance(furl, tuple) else (furl, 1.0)
        q = self.pts - self.piv
        if f < 1.0 or g < 1.0:
            along = np.outer(q @ self.axis, self.axis)        # (furled: drawn in towards the hinge, rolled up on it)
            q = along * g + f * (q - along)
        return q @ self.rotation(deg).T + self.piv

    def anim(self, deg, prev=None):
        """The bone's animation turn (Bedrock degrees on top of its rest turn) for a fold of `deg`, and its full turn."""
        full = geo.euler_bedrock(self.rotation(deg) @ self.R0, prev)
        return [full[i] - self.rest[i] for i in range(3)], full


class Folder:
    """Folds a mob's membranes out of the way of `obstacles` (bone names) in each frame."""

    def __init__(self, m, hinges, obstacles, neighbours):
        self.m = m
        self.hinges = [Hinge(m, bone, parent) for bone, parent in hinges]
        self.obstacles = [b for b in m.bones if b.name in obstacles]
        self.neighbours = neighbours            # [(bone, bone)]: stretches side by side on one wing
        self.warm = {}

    def reset(self):
        self.warm = {}

    def _blocked(self, h, deg, furl, Mp, boxes):
        if _furl(furl) == (0.0, 0.0):
            return False                    # (furled away to nothing: hidden, in nothing's way)
        W = h.moved(deg, furl) @ Mp[:3, :3].T + Mp[:3, 3]
        if (W[:, 1] * 16.0 < FLOOR).any():
            return True
        lo, hi = W.min(axis=0), W.max(axis=0)
        for fr, blo, bhi in boxes:
            if (bhi < lo).any() or (blo > hi).any():
                continue
            near = np.all((W >= blo) & (W <= bhi), axis=1)
            if near.any() and (_inside_depth(W[near], fr) > CLEAR).any():
                return True
        return False

    def need(self, pose, furls=None):
        """{membrane bone: degrees it must fold at least} for one posed frame (searched from the last frame's), each
        furled as `furls` says ({bone: share of its width}; the rest 1)."""
        furls = furls or {}
        mats = self.m.pose_matrices(pose)
        boxes = []
        for b in self.obstacles:
            if abs(float(np.linalg.det(mats[b.name][:3, :3]))) < 1e-9:
                continue                    # (a part shrunk to nothing: gone)
            for c in b.cubes:
                fr = _box_frame(mats, c)
                boxes.append((fr,) + _box_bounds(fr))
        out = {}
        self.stuck = set()
        for h in self.hinges:
            Mp = mats[h.parent]
            deg = self.warm.get(h.bone, 0.0)
            furl = furls.get(h.bone, 1.0)

            def free(d):
                return not self._blocked(h, d, furl, Mp, boxes)

            if not free(deg):
                while deg < MAX_DEG and not free(deg):
                    deg += COARSE_DEG
                low = deg - COARSE_DEG                    # (blocked)
                while deg - STEP_DEG > low + 1e-6 and free(deg - STEP_DEG):
                    deg -= STEP_DEG
            else:
                while deg - COARSE_DEG >= 0.0 and free(deg - COARSE_DEG):
                    deg -= COARSE_DEG
                while deg - STEP_DEG >= 0.0 and free(deg - STEP_DEG):
                    deg -= STEP_DEG
            deg = min(deg, MAX_DEG)
            if not free(deg):
                self.stuck.add(h.bone)
            self.warm[h.bone] = deg
            out[h.bone] = deg
        return out

    def ease_furls(self, furls, loop):
        """Each bone's furl over the frames (both its shares), held and eased so it's never wider than a frame allows."""
        n = len(furls)
        out = [dict(f) for f in furls]
        for h in self.hinges:
            raw = np.array([_furl(f.get(h.bone, 1.0)) for f in furls])
            eased = np.empty_like(raw)
            for k in range(2):
                held = np.empty(n)
                for i in range(n):
                    idx = [(i + d) % n if loop else min(max(i + d, 0), n - 1) for d in range(-HOLD, HOLD + 1)]
                    held[i] = raw[idx, k].min()
                for i in range(n):
                    idx = [(i + d) % n if loop else min(max(i + d, 0), n - 1) for d in range(-BLUR, BLUR + 1)]
                    eased[i, k] = min(float(held[idx].mean()), float(raw[i, k]))
            for i in range(n):
                if eased[i, 0] < 1.0 or eased[i, 1] < 1.0:
                    out[i][h.bone] = (float(eased[i, 0]), float(eased[i, 1]))
        return out

    def smooth(self, needs, loop):
        """Each bone's folds over the frames, held and eased so they never fall below a frame's need."""
        n = len(needs)
        out = {}
        for h in self.hinges:
            raw = np.array([f[h.bone] for f in needs])
            held = np.empty(n)
            eased = np.empty(n)
            for i in range(n):
                idx = [(i + d) % n if loop else min(max(i + d, 0), n - 1) for d in range(-HOLD, HOLD + 1)]
                held[i] = raw[idx].max()
            for i in range(n):
                idx = [(i + d) % n if loop else min(max(i + d, 0), n - 1) for d in range(-BLUR, BLUR + 1)]
                eased[i] = held[idx].mean()
            out[h.bone] = np.maximum(eased, raw)
        for _ in range(2):
            for a, b in self.neighbours:
                out[a], out[b] = np.maximum(out[a], out[b] * COUPLE), np.maximum(out[b], out[a] * COUPLE)
        return out

    def settle(self, poses, folds, furls):
        """Each frame's eased fold checked again (easing and the neighbours' share can fold a stretch up into its back's
        spikes): one that's in the way is moved to the nearest fold that's clear."""
        for i, p in enumerate(poses):
            mats = self.m.pose_matrices(p.as_geo())
            boxes = []
            for b in self.obstacles:
                if abs(float(np.linalg.det(mats[b.name][:3, :3]))) < 1e-9:
                    continue
                for c in b.cubes:
                    fr = _box_frame(mats, c)
                    boxes.append((fr,) + _box_bounds(fr))
            for h in self.hinges:
                d0 = float(folds[h.bone][i])
                furl = furls[i].get(h.bone, 1.0)
                Mp = mats[h.parent]
                if not self._blocked(h, d0, furl, Mp, boxes):
                    continue
                for k in range(1, int(MAX_DEG / STEP_DEG) + 1):
                    hit = [d for d in (d0 - k * STEP_DEG, d0 + k * STEP_DEG)
                           if 0.0 <= d <= MAX_DEG and not self._blocked(h, d, furl, Mp, boxes)]
                    if hit:
                        folds[h.bone][i] = hit[0]
                        break

    def apply(self, poses, folds, furls):
        for h in self.hinges:
            prev = list(h.rest)
            for i, p in enumerate(poses):
                deg = float(folds[h.bone][i])
                f, g = _furl(furls[i].get(h.bone, 1.0))
                if deg > 1e-6:
                    turn, prev = h.anim(deg, prev)
                    p.rot(h.bone, *turn)
                if f < 1.0 or g < 1.0:
                    sc = p.get3(h.bone)[2]
                    sc[0] *= f
                    sc[1] *= g
                    sc[2] *= f


def _furl(v):
    """(share of its width, share of its length) from a furl given as one share or two."""
    return (float(v[0]), float(v[1])) if isinstance(v, tuple) else (float(v), 1.0)


def furl_of(folder, pose):
    """How furled each membrane is in a pose ({bone: share of its width}): the pose's `furl`, a share for every
    membrane or a {bone: share}; none: spread (1)."""
    f = getattr(pose, "furl", None)
    if f is None:
        return {}
    if isinstance(f, dict):
        return dict(f)
    return {h.bone: float(f) for h in folder.hinges}


def record(rig, name, length, frame, loop=True, warm=1):
    """
    mobkit.record with the membranes folded out of the way: frame(t) -> Pose at every STEP (a loop played `warm` times
    first, so the IK has settled), each stretch's fold worked out, smoothed and added, then baked.
    """
    a = Animation(name, length, True if loop else "hold_on_last_frame")
    rig.reset_warm()
    n = int(round(length / STEP))
    if loop:
        for _ in range(warm):
            for i in range(n):
                frame(i * STEP)
    poses = [frame(i * STEP) for i in range(n + 1)]
    folder = rig.folder
    folder.reset()
    furls = [furl_of(folder, p) for p in poses]
    for _ in range(3):
        folder.reset()
        if loop:
            folder.need(poses[-1].as_geo(), furls[-1])     # (the search starts from the loop's end, which runs into its start)
        needs = []
        squeezed = False
        for i, p in enumerate(poses):
            needs.append(folder.need(p.as_geo(), furls[i]))
            for bone in folder.stuck:                     # (on the ground, folded all the way and still in the way:
                f, g = _furl(furls[i].get(bone, 1.0))     # furl it more)
                if f < 1.0:
                    furls[i][bone] = (f * SQUEEZE, g * SQUEEZE)
                    squeezed = True
        if not squeezed:
            break
        furls = folder.ease_furls(furls, loop)
    if loop:
        folds = folder.smooth(needs[:-1], True)
        folds = {b: np.append(v, v[0]) for b, v in folds.items()}
    else:
        folds = folder.smooth(needs, False)
    folder.settle(poses, folds, furls)
    folder.apply(poses, folds, furls)
    for i, p in enumerate(poses):
        a.add(i * STEP, p)
    a.bake(0.3, 0.04, 0.01)
    a.meta_folds = {b: float(v.max()) for b, v in folds.items()}
    return a


# ---------------------------------------------------------------- the two wings never through each other


def _obbs(mats, cubes):
    """Each box as (centre, unit axes (3x3, columns), half sizes) in units; boxes squashed to nothing left out."""
    out = []
    for c in cubes:
        if abs(float(np.linalg.det(mats[c.bone.name][:3, :3]))) < 1e-9:
            continue                        # (a membrane folded away to nothing: hidden)
        M, _inv, lo, hi = _box_frame(mats, c)
        axes = M[:3, :3]
        lens = np.linalg.norm(axes, axis=0)
        if lens.min() < 1e-6:
            continue
        centre = (M @ np.append((lo + hi) / 2.0, 1.0))[:3] * 16.0
        out.append((centre, axes / lens, (hi - lo) / 2.0 * lens * 16.0))
    return out


def _overlap(a, b):
    """How deep two boxes overlap (units; 0 or less: apart), by the separating axes."""
    ca, A, ha = a
    cb, B, hb = b
    axes = [A[:, i] for i in range(3)] + [B[:, i] for i in range(3)]
    for i in range(3):
        for j in range(3):
            n = np.cross(A[:, i], B[:, j])
            ln = np.linalg.norm(n)
            if ln > 1e-6:
                axes.append(n / ln)
    d = cb - ca
    best = None
    for L in axes:
        ra = float(np.abs(A.T @ L) @ ha)
        rb = float(np.abs(B.T @ L) @ hb)
        o = ra + rb - abs(float(d @ L))
        if o <= 0.0:
            return 0.0
        best = o if best is None else min(best, o)
    return best


def crossings(m, anims, left, right, tolerance=0.3, step=0.1):
    """{(animation, "left/right"): (deepest units, seconds)}: boxes of the bones `left` overlapping boxes of the bones
    `right` deeper than `tolerance` (the two membranes, thin plates the clipping check's points can't see in)."""
    lb = [c for b in m.bones if b.name in left for c in b.cubes]
    rb = [c for b in m.bones if b.name in right for c in b.cubes]
    found = {}
    for name, a in anims.items():
        for t in np.arange(0.0, a.length + 1e-6, step):
            mats = m.pose_matrices(a.sample(float(t)))
            ro = _obbs(mats, rb)
            rmax = [(o[0], float(np.linalg.norm(o[2]))) for o in ro]
            for lo_ in _obbs(mats, lb):
                rl = float(np.linalg.norm(lo_[2]))
                for o, (cen, rr) in zip(ro, rmax):
                    if np.linalg.norm(cen - lo_[0]) > rl + rr:
                        continue
                    d = _overlap(lo_, o)
                    if d > tolerance and d > found.get(name, (0.0, 0.0))[0]:
                        found[name] = (d, float(t))
    return found
