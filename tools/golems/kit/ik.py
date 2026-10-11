"""
Inverse kinematics for the golem's limbs: finds joint angles (Bedrock degrees, the same numbers that go into
the .geo.json rest rotations and the animation keyframes) that put a fist or a foot on a target spot,
lying flat, using the exact GeckoLib forward kinematics in geo.py.

A limb is a chain of bones, each with the axes that may turn and their limits:
    Joint("arm_l", axes=(0, 1, 2), lo=(-90, -40, -60), hi=(60, 40, 60))
The solver works on the TOTAL angles (rest rotation + animation offset) of those axes.
"""
import numpy as np
from scipy.optimize import least_squares

from . import geo


class Joint:
    def __init__(self, bone, axes=(0, 1, 2), lo=None, hi=None, prefer=None, weight=0.02):
        self.bone = bone
        self.axes = tuple(axes)
        self.lo = tuple(lo) if lo is not None else tuple(-179.0 for _ in self.axes)
        self.hi = tuple(hi) if hi is not None else tuple(179.0 for _ in self.axes)
        self.prefer = tuple(prefer) if prefer is not None else None  # gentle pull towards these totals
        self.weight = weight


class Limb:
    """
    A chain of joints ending in an end bone, with the contact point and contact directions on the end bone
    (bind pose, Bedrock): `contact` (point), `down` (direction that should face the ground) and
    `forward` (direction that should point along `heading`).
    """

    def __init__(self, joints, end_bone, contact, down=(0, -1, 0), forward=(0, 0, -1)):
        self.joints = joints
        self.end_bone = end_bone
        self.contact = np.asarray(contact, dtype=float)
        self.down = np.asarray(down, dtype=float)
        self.forward = np.asarray(forward, dtype=float)


def _with_angles(model, pose, limb, x):
    """A copy of the pose (animation offsets) with this limb's total angles x written in."""
    pose = dict(pose)
    k = 0
    for j in limb.joints:
        b = model[j.bone]
        rot, pos, scl = pose.get(j.bone, ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
        rot = list(rot)
        for ax in j.axes:
            rot[ax] = x[k] - b.rotation[ax]
            k += 1
        pose[j.bone] = (tuple(rot), pos, scl)
    return pose


def _current(model, pose, limb):
    x = []
    for j in limb.joints:
        b = model[j.bone]
        rot = pose.get(j.bone, ((0.0, 0.0, 0.0),))[0]
        for ax in j.axes:
            x.append(b.rotation[ax] + rot[ax])
    return np.array(x, dtype=float)


def contact_frame(model, mats, limb):
    """Where the limb's contact point is and which way its down/forward directions point (Bedrock model space)."""
    p = model.point(mats, limb.end_bone, limb.contact)
    d = model.direction(mats, limb.end_bone, limb.down)
    f = model.direction(mats, limb.end_bone, limb.forward)
    return p, d / np.linalg.norm(d), f / np.linalg.norm(f)


def solve(model, pose, limb, target, heading=None, down=(0.0, -1.0, 0.0), x0=None, w_pos=1.0, w_down=6.0,
          w_head=2.0, w_smooth=0.0):
    """
    Angles for one limb so that its contact point sits on `target` (Bedrock model space), its `down`
    direction points along `down` (default straight down: lying flat) and its forward direction points
    along `heading` (projected flat; None = don't care). Returns (pose with the solution, miss in units,
    the solved total angles).
    x0 starts the search (the previous frame's angles in an animation); w_smooth (per degree) also pulls the answer
    towards x0, so a limb with spare joints doesn't flip from one way of bending to another between frames.
    Only the limb's own bones are recomputed while solving (the rest of the body is fixed), which keeps it fast.
    """
    target = np.asarray(target, dtype=float)
    down = np.asarray(down, dtype=float)
    down /= np.linalg.norm(down)
    if heading is not None:
        heading = np.asarray(heading, dtype=float)
        heading = heading - down * np.dot(heading, down)
        heading /= np.linalg.norm(heading)
    lo = np.array([v for j in limb.joints for v in j.lo])
    hi = np.array([v for j in limb.joints for v in j.hi])
    prefer = []
    pweights = []
    for j in limb.joints:
        for i, _ax in enumerate(j.axes):
            p = j.prefer[i] if j.prefer is not None else None
            prefer.append(np.nan if p is None else float(p))
            pweights.append(j.weight)
    prefer = np.array(prefer)
    pweights = np.array(pweights)
    has_pref = ~np.isnan(prefer)
    prefer0 = np.nan_to_num(prefer)
    start = _current(model, pose, limb) if x0 is None else np.asarray(x0, dtype=float)
    start = np.clip(start, lo + 1e-6, hi - 1e-6)
    anchor = start.copy() if (x0 is not None and w_smooth > 0) else None

    chain = [model[j.bone] for j in limb.joints]
    if chain[-1].name != limb.end_bone:
        raise ValueError("a limb's last joint must be its end bone")
    base = model.pose_matrices(pose)[chain[0].parent.name] if chain[0].parent is not None else np.eye(4)
    others = []
    for b in chain:
        rot, pos, scl = pose.get(b.name, ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
        others.append((pos, scl))
    contact = np.append(geo.gecko(limb.contact), 1.0)
    d_local = np.array([-limb.down[0], limb.down[1], limb.down[2]])
    f_local = np.array([-limb.forward[0], limb.forward[1], limb.forward[2]])
    axes = [(k, ax) for k, j in enumerate(limb.joints) for ax in j.axes]

    def end_matrix(x):
        rots = [[0.0, 0.0, 0.0] for _ in chain]
        for (k, ax), v in zip(axes, x):
            rots[k][ax] = v - chain[k].rotation[ax]
        M = base
        for k, b in enumerate(chain):
            pos, scl = others[k]
            M = M @ b.local_matrix(rots[k], pos, scl)
        return M

    def frame(x):
        M = end_matrix(x)
        cp = geo.bedrock((M @ contact)[:3])
        dd = M[:3, :3] @ d_local
        ff = M[:3, :3] @ f_local
        cd = np.array([-dd[0], dd[1], dd[2]])
        cf = np.array([-ff[0], ff[1], ff[2]])
        return cp, cd / np.linalg.norm(cd), cf / np.linalg.norm(cf)

    def residuals(x):
        cp, cd, cf = frame(x)
        r = [(cp - target) * w_pos, (cd - down) * w_down]
        if heading is not None:
            fflat = cf - down * np.dot(cf, down)
            n = np.linalg.norm(fflat)
            fflat = fflat / n if n > 1e-9 else fflat
            r.append((fflat - heading) * w_head)
        r.append(np.where(has_pref, (x - prefer0) * pweights, 0.0))
        if anchor is not None:
            r.append((x - anchor) * w_smooth)
        return np.concatenate(r)

    res = least_squares(residuals, start, bounds=(lo, hi), xtol=1e-9, ftol=1e-9, gtol=1e-9, max_nfev=300)
    out = _with_angles(model, pose, limb, res.x)
    cp, _, _ = frame(res.x)
    return out, float(np.linalg.norm(cp - target)), res.x


def bake_rest(model, pose, limbs):
    """Writes the limbs' solved total angles into the bones' rest rotations (for the .geo.json)."""
    for limb in limbs:
        for j in limb.joints:
            b = model[j.bone]
            rot = pose.get(j.bone, ((0.0, 0.0, 0.0),))[0]
            b.rotation = tuple(b.rotation[i] + rot[i] for i in range(3))
            pose.pop(j.bone, None)
