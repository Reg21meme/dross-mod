"""
Clipping checks, run on every design by build.py:

1. Attachment (rest pose): every part that grows out of the body (spikes, horns, volcano levels, rim teeth...: bones
   with `attach` set) must have its foot on or in the body. Points spread over the bottom face of its first box are
   tested; the share inside (or within a hair of) another box of the same form is its support. A part with little
   support hangs off the body, like a volcano standing upright on a sloping back.
2. Pass-through (rest pose and animations): parts that mustn't go through each other (forearms and fists through
   the torso, legs, head or volcanoes; shoulder spikes through the head or volcanoes; the jaw through the chest) are
   sampled frame by frame; how much of one is inside the other, and how deep, is reported.

Run on its own:   python tools/golems/kit/clipping.py <design module> [...]
"""
import numpy as np

SUPPORT_MARGIN = 0.75   # units: a foot point this close to another box still counts as touching it
MIN_SUPPORT = 0.6       # a part with less of its foot supported than this hangs off the body


def _box_frame(mats, cube):
    """The box's matrix (GeckoLib space) and its corners in its own frame (blocks)."""
    M = mats[cube.bone.name] @ cube.cube_matrix()
    ox, oy, oz = cube.origin
    w, h, d = cube.size
    i = cube.inflate / 16.0
    lo = np.array([-(ox + w) / 16.0 - i, oy / 16.0 - i, oz / 16.0 - i])
    hi = np.array([-ox / 16.0 + i, (oy + h) / 16.0 + i, (oz + d) / 16.0 + i])
    return M, np.linalg.inv(M), lo, hi


def _inside_depth(points_g, frame, margin=0.0):
    """Depth (units) of each point inside a box (negative: outside by that much), plus `margin`."""
    _M, inv, lo, hi = frame
    q = (inv @ np.c_[points_g, np.ones(len(points_g))].T).T[:, :3]
    d = np.minimum(q - lo, hi - q)
    return np.min(d, axis=1) * 16.0 + margin


def _sample_box(frame, n, rng):
    M, _inv, lo, hi = frame
    local = lo + rng.random((n, 3)) * (hi - lo)
    return (M @ np.c_[local, np.ones(n)].T).T[:, :3]


def _foot_points(frame, grid=5):
    """Points spread over the box's bottom face (its own -y side): the foot of a spike or a volcano level."""
    M, _inv, lo, hi = frame
    xs = np.linspace(lo[0], hi[0], grid + 2)[1:-1]
    zs = np.linspace(lo[2], hi[2], grid + 2)[1:-1]
    X, Z = np.meshgrid(xs, zs)
    local = np.c_[X.ravel(), np.full(X.size, lo[1]), Z.ravel()]
    return (M @ np.c_[local, np.ones(len(local))].T).T[:, :3]


def _form(bone):
    """The form a bone is drawn in: "shell" (shell forms), "molten" (core form) or "both"."""
    kinds = {b.kind for b in bone.ancestors()}
    if "shell" in kinds:
        return "shell"
    if "molten" in kinds or "vent" in kinds:
        return "molten"
    if "burst" in kinds:
        return "burst"
    return "both"


def attachment(m):
    """[(bone name, share of its foot that touches the body)] for every part that grows out of it, worst first."""
    mats = m.pose_matrices()
    boxes = [(b, _box_frame(mats, c)) for b in m.bones for c in b.cubes]
    out = []
    for b in m.bones:
        if not getattr(b, "attach", False) or not b.cubes:
            continue
        form = _form(b)
        pts = _foot_points(_box_frame(mats, b.cubes[0]))
        supported = np.zeros(len(pts), dtype=bool)
        for ob, fr in boxes:
            if ob is b or _form(ob) not in (form, "both"):
                continue
            supported |= _inside_depth(pts, fr, SUPPORT_MARGIN) >= 0.0
        out.append((b.name, float(supported.mean())))
    return sorted(out, key=lambda r: r[1])


def group_of(bone):
    """Which body part a bone belongs to."""
    names = [b.name for b in bone.ancestors()]
    for n in names:
        if n.startswith(("forearm_", "fist_")):
            return "arm_" + n[-1]
    for n in names:
        if n.startswith("arm_"):
            return "shoulder_" + n[-1]
        if n.startswith(("thigh_", "shin_", "foot_")):
            return "leg_" + n[-1]
    if "head" in names:
        return "jaw" if ("jaw" in names or "jaw_inner" in names) else "head"
    if any(getattr(b, "is_volcano", False) for b in bone.ancestors()):
        return "volcano"
    if "torso" in names:
        return "torso"
    if "pelvis" in names:
        return "pelvis"
    return "root"


PAIRS = (
    ("arm_l", "torso"), ("arm_r", "torso"), ("arm_l", "leg_l"), ("arm_r", "leg_r"), ("arm_l", "head"),
    ("arm_r", "head"), ("arm_l", "volcano"), ("arm_r", "volcano"), ("shoulder_l", "head"), ("shoulder_r", "head"),
    ("shoulder_l", "volcano"), ("shoulder_r", "volcano"), ("jaw", "torso"), ("head", "volcano"),
)


def pass_through(m, pose, form, samples=120, seed=0):
    """
    {(part a, part b): (share of a's volume inside b, 95th percentile depth in units)} for one pose of one form
    ("shell": the shell forms, "molten": the core form).
    """
    rng = np.random.default_rng(seed)
    mats = m.pose_matrices(pose)
    groups = {}
    for b in m.bones:
        if not b.cubes or _form(b) not in (form, "both"):
            continue
        g = group_of(b)
        for c in b.cubes:
            if c.tag == "crater":
                continue
            groups.setdefault(g, []).append(_box_frame(mats, c))
    res = {}
    for ga, gb in PAIRS:
        if ga not in groups or gb not in groups:
            continue
        pts = np.concatenate([_sample_box(fr, samples, rng) for fr in groups[ga]])
        depth = np.full(len(pts), -np.inf)
        for fr in groups[gb]:
            depth = np.maximum(depth, _inside_depth(pts, fr))
        inside = depth > 0
        res[(ga, gb)] = (float(inside.mean()), float(np.percentile(depth[inside], 95)) if inside.any() else 0.0)
    return res


def animation_report(m, animations, names=("idle", "walk", "erupt", "death"), frames=10):
    """The worst pass-through per pair and form over a few frames of each animation."""
    worst = {}
    for form in ("shell", "molten"):
        for name in names:
            a = animations.get(name)
            if a is None or (name == "death" and form == "shell") or (name == "erupt" and form == "molten"):
                continue
            for t in np.linspace(0.0, a.length, frames):
                for pair, (share, d95) in pass_through(m, a.sample(float(t)), form).items():
                    key = (form,) + pair
                    if key not in worst or share * max(d95, 0.5) > worst[key][0] * max(worst[key][1], 0.5):
                        worst[key] = (share, d95, name, round(float(t), 2))
    return worst


def report(m, animations=None, quiet=False):
    """Prints and returns the problems found: hanging parts, and parts going through each other."""
    lines = []
    for n, s in attachment(m):
        if s < MIN_SUPPORT:
            lines.append(f"  hanging off the body: {n} (only {s:.0%} of its foot touches it)")
    for form in ("shell", "molten"):
        for pair, (share, d95) in sorted(pass_through(m, None, form).items()):
            if share > 0.04 and d95 > 1.0:
                lines.append(f"  rest pose ({form}): {pair[0]} through {pair[1]}: {share:.0%} of it, {d95:.1f} units deep")
    if animations:
        for (form, ga, gb), (share, d95, name, t) in sorted(animation_report(m, animations).items()):
            if share > 0.06 and d95 > 1.5:
                lines.append(f"  {name} at {t}s ({form}): {ga} through {gb}: {share:.0%} of it, {d95:.1f} units deep")
    if not quiet:
        print("\n".join(lines) if lines else "  clipping: none found")
    return lines


if __name__ == "__main__":
    import os
    import sys
    sys.dont_write_bytecode = True
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    import build  # noqa: E402
    from kit import anims  # noqa: E402
    for name in sys.argv[1:]:
        d = build.load_design(name)
        model, body, _info = build.build_model(d, verbose=False)
        print(f"{d.LABEL}. {d.NAME}")
        report(model, anims.build_all(d, model, body))
