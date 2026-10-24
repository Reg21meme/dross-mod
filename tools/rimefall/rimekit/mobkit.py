"""
The Rimefall animals that move (CLAUDE.md step 14): a design's model built on a skeleton, its legs planted with IK,
its animations recorded frame by frame, checked for clipping, and drawn as previews.

A mob module (rimemobs/<mob>.py) gives:
  ID, NAME                the mob's id ("rimehorn") and name
  DESIGN                  the design module whose look it moves (rimedesigns/...): its build(s, v) and materials(v)
  K                       the scale the design builds at (its pivots are given in design units, like its shapes)
  BONES                   [(bone, pivot (design units), parent)], parents first; boxes go on the bone of their part
  ROUTES                  {part name: bone} for parts whose bone isn't called the same (usually empty)
  DENSITY                 (optional) texels a unit of its texture (default 1; the Frostfolk's 2: finer at their size)
  shown_together(a, b)    (optional) True if bones a and b are ever drawn at once (the z-fighting fix skips others)
  BUILD_VARIANT           the design variant the model is built from (it must hold every part any variant shows)
  VARIANTS                [dict(name=..., design=design variant (its materials), hidden=(bones), texture=name of
                          its texture (default: its name; two variants may share one, a bull and a cow moose))]
  LEGS                    [Leg(...)]: the limbs the IK plants
  animations(rig)         {name: Animation}
and optionally FREE (parts allowed to float) and CLIP_GROUPS / CLIP_PAIRS (what mustn't pass through what).

The model is the design's look unchanged: the same shapes, now on bones. A face hidden inside another box is only
dropped when that box is on the same bone (a moving part shows what a neighbour covered in the rest pose), and feet
keep their soles.
"""
import math
import os

import numpy as np
from PIL import Image

from kit import geo, ik
from kit.anims import Animation, Pose, Rig, gait_offset
from kit.clipping import _box_frame, _inside_depth

from . import connect, shapes, texture, uvpack, zfight
from . import view as V

STEP = 0.05                  # seconds between recorded frames (20 a second)
GIF_STEP = 0.1               # seconds between a preview GIF's frames
FLOOR_TOLERANCE = 0.6        # units a part may dip below the floor (a hoof pressing into the snow)
IK_SMOOTH = 0.01             # per degree: how strongly a frame keeps near the last (more lags behind a fast gait)
WARM_BEND = 12.0             # degrees each knee starts bent when the IK begins an animation
CLIP_TOLERANCE = 0.6         # units one part may sink into another before it counts


class Leg:
    """
    One limb the IK plants: `upper` (hip or shoulder) and `lower` (shin) bones and, if it has one, the `hoof` bone at
    the ankle (it keeps the sole flat on the ground); the sole's middle (`foot`, design units), and which way the
    lower joint may bend (`bend`: +1 the foot swings back, -1 forward).
    """

    def __init__(self, name, upper, lower, foot, bend=1, hoof=None, joints=None, forward=(0.0, 0.0, -1.0)):
        self.name = name
        self.upper = upper
        self.lower = lower
        self.hoof = hoof
        self.foot = np.asarray(foot, dtype=float)
        self.bend = bend
        self.joints = joints          # an insect's leg: its own chain of ik.Joint (the last one holds the foot)
        self.forward = tuple(forward) # which way the foot points along the ground (a skate blade's length)


# ---------------------------------------------------------------- the model


def build_model(mob, verbose=True):
    """The mob's model (bones, boxes, per-face UVs) and {variant name: textures}."""
    m = geo.Model(f"dross_{mob.ID}")
    k = float(mob.K)
    for name, pivot, parent in mob.BONES:
        m.bone(name, tuple(float(v) * k for v in pivot), parent=parent)
    parts = shapes.Parts(m, getattr(mob, "ROUTES", {}), default="body")
    mob.DESIGN.build(parts, mob.BUILD_VARIANT)
    for mats_, centre, count in connect.floating(m, getattr(mob, "FREE", ())):
        print(f"  WARNING {mob.ID}: {count} box(es) of {', '.join(mats_)} float free of the body near "
              f"({centre[0]:.0f}, {centre[1]:.0f}, {centre[2]:.0f})")
    empty = [b.name for b in m.bones if not b.cubes and not any(c.cubes for c in _descendants(b))]
    if empty:
        print(f"  note {mob.ID}: bones with nothing on them: {', '.join(empty)}")
    grown, left = zfight.fix(m, together=getattr(mob, "shown_together", None))
    first = mob.DESIGN.materials(mob.VARIANTS[0]["design"])
    boxes = len(m.cubes())
    dropped, removed = uvpack.optimize(m, lambda c: getattr(first[c.mat], "opaque", False), animated=True,
                                       density=float(getattr(mob, "DENSITY", 1.0)))
    texs = {}
    for v in mob.VARIANTS:
        name = v.get("texture", v["name"])
        if name not in texs:
            texs[name] = texture.bake(m, mob.DESIGN.materials(v["design"]))
    if verbose:
        pts = V.corners(m) / 16.0
        print(f"  {mob.ID}: {len(m.bones)} bones, {boxes} boxes ({removed} hidden ones dropped, {dropped} hidden faces), "
              f"texture {m.tex_w}x{m.tex_h}; {np.ptp(pts[:, 0]):.1f} wide x {np.ptp(pts[:, 2]):.1f} long x "
              f"{pts[:, 1].max():.1f} tall (blocks)" + (f"; {left} z-fighting pairs LEFT" if left else ""))
    return m, texs


def _descendants(b):
    out = []
    for c in b.children:
        out.append(c)
        out.extend(_descendants(c))
    return out


# ---------------------------------------------------------------- legs and IK


def limbs(m, mob):
    """{leg name: ik.Limb} for the mob's legs, and the rest targets (where each foot stands)."""
    k = float(mob.K)
    out = {}
    rest = {}
    mats = m.pose_matrices()
    for leg in mob.LEGS:
        # The contact is the sole's lowest point in the rest pose (the boxes' chamfers and hairs of inflation reach
        # a little under the foot's middle), so planting it puts nothing under the ground.
        end = leg.joints[-1].bone if leg.joints else (leg.hoof or leg.lower)
        low = min(float(_grid(_box_frame(mats, c))[:, 1].min() * 16.0) for c in m[end].cubes)
        if leg.joints:
            fwd = np.asarray(leg.forward, dtype=float)
            out[leg.name] = ik.Limb(leg.joints, end, contact=(leg.foot[0] * k, low, leg.foot[2] * k), down=(0, -1, 0),
                                    forward=tuple(fwd))
            rest[leg.name] = (np.array([leg.foot[0] * k, 0.0, leg.foot[2] * k]), fwd)
            continue
        lo2, hi2 = ((-10.0, 100.0) if leg.bend > 0 else (-100.0, 10.0))
        joints = [
            ik.Joint(leg.upper, (0, 1, 2), lo=(-80, -25, -25), hi=(80, 25, 25), prefer=(None, 0, 0), weight=0.04),
            ik.Joint(leg.lower, (0, 2), lo=(lo2, -20), hi=(hi2, 20), prefer=(None, 0), weight=0.02),
        ]
        if leg.hoof:
            joints.append(ik.Joint(leg.hoof, (0, 2), lo=(-70, -20), hi=(70, 20), prefer=(None, 0), weight=0.02))
        out[leg.name] = ik.Limb(joints, end, contact=(leg.foot[0] * k, low, leg.foot[2] * k), down=(0, -1, 0),
                                forward=(0, 0, -1))
        rest[leg.name] = (np.array([leg.foot[0] * k, 0.0, leg.foot[2] * k]), np.array([0.0, 0.0, -1.0]))
    return out, rest


class MobRig(Rig):
    def __init__(self, m, mob):
        lims, rest = limbs(m, mob)
        super().__init__(m, lims, rest, dict(ik_smooth=IK_SMOOTH))
        self.mob = mob
        self.k = float(mob.K)
        self.reset_warm()

    def reset_warm(self):
        """Every leg starts its search a little bent (a dead straight leg can't tell which way to fold)."""
        self._warm = {}
        self._fresh = set()
        for leg in getattr(self, "mob", None).LEGS if getattr(self, "mob", None) else ():
            if leg.joints:
                continue                  # (an insect's legs are bent already in the rest pose: no seed needed)
            b = WARM_BEND * leg.bend
            x = [-b / 2.0, 0.0, 0.0, b, 0.0] + ([-b / 2.0, 0.0] if leg.hoof else [])
            self._warm[leg.name] = np.array(x)
            self._fresh.add(leg.name)

    def solve_limb(self, pose, name, target, heading=None, down=(0.0, -1.0, 0.0)):
        """Rig.solve_limb, except that a limb's first frame isn't held near its starting bend (only later frames are
        kept near the frame before, so they don't flip)."""
        if name in self._fresh:
            self._fresh.discard(name)
            keep = self.style["ik_smooth"]
            self.style["ik_smooth"] = 0.0
            try:
                return super().solve_limb(pose, name, target, heading, down)
            finally:
                self.style["ik_smooth"] = keep
        return super().solve_limb(pose, name, target, heading, down)

    def plant(self, p, offsets=None, which=None):
        """Plants the feet (all, or `which`) on their rest spots moved by `offsets` {leg: (dx, dy, dz)}."""
        for name in (which or self.limbs):
            tgt, heading = self.rest[name]
            tgt = np.array(tgt, dtype=float)
            if offsets and name in offsets:
                tgt = tgt + np.asarray(offsets[name], dtype=float)
            self.solve_limb(p, name, tgt, heading)


def record(rig, name, length, frame, loop=True, warm=1):
    """
    Records an animation: frame(t) -> Pose at every STEP. A loop is played `warm` times first, so the IK's warm start
    (and anything trailing, like a tail) has settled into the loop and its end meets its start.
    """
    a = Animation(name, length, True if loop else "hold_on_last_frame")
    rig.reset_warm()
    n = int(round(length / STEP))
    if loop:
        for _ in range(warm):
            for i in range(n):
                frame(i * STEP)
    for i in range(n + 1):
        a.add(i * STEP, frame(i * STEP))
    a.bake(0.3, 0.04, 0.01)
    return a


def gait(rig, p, t, cycle, phases, stance, stride, lift, reach=0.0):
    """
    One frame of a gait: each leg's foot slides back along the ground while it carries weight and swings forward in
    the air (kit.anims.gait_offset), `phases` {leg: start of its stance, 0..1}; `reach` moves every step's middle
    forward (units). Returns {leg: swing 0..1 (0 planted)}.
    """
    offs = {}
    swing = {}
    for name, ph in phases.items():
        dz, dy, u = gait_offset(t / cycle - ph, stance, stride, lift)
        offs[name] = (0.0, dy, dz - reach)
        swing[name] = u
    rig.plant(p, offs)
    return swing


def ground_speed(stride, stance, cycle):
    """Blocks a second the body moves for a gait whose planted feet slide `stride` units in `stance` of a `cycle`."""
    return stride / (stance * cycle) / 16.0


# ---------------------------------------------------------------- clipping


def _grid(frame, spacing=1.5):
    M, _inv, lo, hi = frame
    axes = []
    for k in range(3):
        n = max(2, int(math.ceil((hi[k] - lo[k]) * 16.0 / spacing)) + 1)
        axes.append(np.linspace(lo[k], hi[k], n))
    X, Y, Z = np.meshgrid(*axes, indexing="ij")
    local = np.c_[X.ravel(), Y.ravel(), Z.ravel()]
    return (M @ np.c_[local, np.ones(len(local))].T).T[:, :3]


def clip_check(m, anims, groups, pairs, hidden=(), step=0.1):
    """
    {(animation, what): (deepest units, seconds, where)}: parts of one group going into another's boxes (`pairs`), and
    anything under the floor. `groups(bone name)` -> group name or None (not checked). Parts that already overlap in
    the rest pose (a leg growing out from under an eye) only count when they go in deeper than that.
    """
    found = {}
    rest = {}
    poses = [("rest", None, [0.0])] + [(n, a, np.arange(0.0, a.length + 1e-6, step) if a.length > 0 else [0.0])
                                       for n, a in anims.items()]
    for name, a, ts in poses:
        for t in ts:
            mats = m.pose_matrices(None if a is None else a.sample(float(t)))
            boxes = {}
            for b in m.bones:
                if not b.cubes or any(x.name in hidden for x in b.ancestors()):
                    continue
                g = groups(b.name)
                if g is None:
                    continue
                if abs(float(np.linalg.det(mats[b.name][:3, :3]))) < 1e-9:
                    continue                  # (a prop hidden at scale 0: not there)
                for c in b.cubes:
                    boxes.setdefault(g, []).append((_box_frame(mats, c), f"{b.name}/{c.mat}"))
            pts = {g: np.concatenate([_grid(fr) for fr, _l in frs]) for g, frs in boxes.items()}
            for ga, gb in pairs:
                if ga not in pts or gb not in boxes:
                    continue
                d = 0.0
                lab = ""
                for fr, lb in boxes[gb]:
                    di = float(_inside_depth(pts[ga], fr).max())
                    if di > d:
                        d, lab = di, lb
                if a is None:
                    rest[(ga, gb)] = d
                    continue
                if d > rest.get((ga, gb), 0.0) + CLIP_TOLERANCE:
                    key = (name, f"{ga}/{gb}")
                    if key not in found or d > found[key][0]:
                        found[key] = (d, float(t), lab)
            for g, frs in boxes.items():
                if a is None:
                    continue
                for fr, lab in frs:
                    low = -float(_grid(fr)[:, 1].min()) * 16.0
                    if low > FLOOR_TOLERANCE:
                        key = (name, f"{g}/floor")
                        if key not in found or low > found[key][0]:
                            found[key] = (low, float(t), lab)
    return found


def clip_report(mob_id, found):
    if not found:
        print(f"  {mob_id}: no clipping")
        return True
    for (name, what), (depth, t, where) in sorted(found.items()):
        print(f"  {mob_id}: CLIP {what} in {name} at {t:.2f} s, {depth:.1f} units deep ({where})")
    return False


# ---------------------------------------------------------------- previews


def _bounds(m, poses, yaw, el, hidden, figure=False):
    boxes = [V.window(m, yaw, el, pose=p, hidden=hidden, figure=figure) for p in poses]
    b = np.array(boxes)
    return (b[:, 0].min(), b[:, 1].min(), b[:, 2].max(), b[:, 3].max())


def gif(m, tex, a, path, yaw=55.0, el=12.0, scale=3.0, hidden=(), root_scale=None):
    """An animated GIF of one animation (played once, or one loop)."""
    times = np.arange(0.0, a.length - 1e-6 if a.loop is True else a.length + 1e-6, GIF_STEP)
    poses = [_scaled(a.sample(float(t)), root_scale) for t in times]
    bounds = _bounds(m, poses, yaw, el, hidden, figure=True)
    frames = [V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, bounds=bounds, pose=p, hidden=hidden, figure=True)
              for p in poses]
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(GIF_STEP * 1000), loop=0,
                   optimize=True)
    return frames


def strip(m, tex, a, n=8, yaw=70.0, el=10.0, scale=1.6, hidden=(), root_scale=None):
    """n pictures across one animation, side by side (one row of a filmstrip)."""
    times = np.linspace(0.0, a.length, n, endpoint=a.loop is not True)
    poses = [_scaled(a.sample(float(t)), root_scale) for t in times]
    bounds = _bounds(m, poses, yaw, el, hidden)
    ims = [V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, bounds=bounds, pose=p, hidden=hidden,
                          figure=False), f"{t:.2f} s", 13) for t, p in zip(times, poses)]
    return V.grid(ims, n)


def _scaled(pose, root_scale):
    """A baby: the whole animal scaled down from its feet (root) and its head scaled back up a little."""
    if not root_scale:
        return pose
    pose = dict(pose)
    body, head_bone, head_k = root_scale
    rot, pos, scl = pose.get("root", ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
    pose["root"] = (rot, pos, (body, body, body))
    if head_bone:
        rot, pos, scl = pose.get(head_bone, ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
        pose[head_bone] = (rot, pos, (head_k, head_k, head_k))
    return pose


def save_png(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def save_textures(tex, folder, name):
    """name.png and its _glow / _ice / _iceglow layers (only the layers it has)."""
    os.makedirs(folder, exist_ok=True)
    Image.fromarray(tex["base"], "RGBA").save(os.path.join(folder, f"{name}.png"))
    for key, suffix in (("glow", "_glow"), ("ice", "_ice"), ("iceglow", "_iceglow")):
        path = os.path.join(folder, f"{name}{suffix}.png")
        if texture.has(tex, key):
            Image.fromarray(tex[key], "RGBA").save(path)
        elif os.path.exists(path):
            os.remove(path)


Pose = Pose          # (re-exported for the mob modules)


def cross_clip(ma, anim_a, bones_a, mb, anim_b, bones_b, step=0.05, phase=0.0):
    """
    Two mobs drawn in one place (the Huntsman in the Alpha Moose's saddle: both models share the moose's ground and
    middle): how deep the boxes of `bones_a` of model A go into the boxes of `bones_b` of model B while A plays anim_a
    and B anim_b (a loop wraps). Returns (deepest units, seconds, "a bone/b bone"), or (0, None, "") if they never
    touch. Hidden bones (scaled to nothing) don't count. `phase`: B's loop started this many seconds earlier (in the
    game the moose's idle may be anywhere in its loop when he moves).
    """
    length = anim_a.length
    worst = (0.0, None, "")
    for t in np.arange(0.0, length + 1e-6, step):
        ta = float(t)
        tb = (float((t + phase) % anim_b.length) if anim_b.loop is True and anim_b.length > 0
              else float(min(t, anim_b.length)))
        mats_a = ma.pose_matrices(anim_a.sample(ta))
        mats_b = mb.pose_matrices(anim_b.sample(tb))
        pts = []
        for name in bones_a:
            b = ma[name]
            M = mats_a[name]
            if abs(np.linalg.det(M[:3, :3])) < 1e-6:
                continue
            for c in b.cubes:
                pts.append((_grid(_box_frame(mats_a, c)), name))
        if not pts:
            continue
        for name in bones_b:
            for c in mb[name].cubes:
                fr = _box_frame(mats_b, c)
                for p, an in pts:
                    d = float(_inside_depth(p, fr).max())
                    if d > worst[0]:
                        worst = (d, ta, f"{an}/{name}")
    return worst
