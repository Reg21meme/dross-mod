"""
Sanity checks run on every design before anything is written:
  - z-fighting: two boxes in the same rigid group whose faces lie in the same plane, face the same way and
    overlap (GeckoLib would flicker between them);
  - every texel area inside the texture;
  - the rest pose stands on the ground (lowest point at y = 0) and fits the size the brief asks for.
"""
import numpy as np

from . import geo


def rigid_root(bone):
    """Plates hang on their parent with no rest rotation, so they share its frame in the rest pose."""
    b = bone
    while b.parent is not None and not any(abs(v) > 1e-9 for v in b.rotation):
        b = b.parent
    return b


def _extent(c):
    lo = np.array(c.origin) - c.inflate
    hi = np.array(c.origin) + np.array(c.size) + c.inflate
    return lo, hi


def coplanar_pairs(model):
    """Pairs of boxes (same rigid group, not rotated) with overlapping faces in the same plane, facing the same way."""
    pairs = []
    groups = {}
    for c in model.cubes():
        if c.rotation and any(c.rotation):
            continue
        groups.setdefault(rigid_root(c.bone).name, []).append(c)
    for cubes in groups.values():
        for i in range(len(cubes)):
            a = cubes[i]
            a0, a1 = _extent(a)
            for j in range(i + 1, len(cubes)):
                b = cubes[j]
                b0, b1 = _extent(b)
                for ax in range(3):
                    others = [k for k in range(3) if k != ax]
                    if not all(min(a1[k], b1[k]) - max(a0[k], b0[k]) > 1e-6 for k in others):
                        continue
                    for side, pa, pb in (("min", a0[ax], b0[ax]), ("max", a1[ax], b1[ax])):
                        if abs(pa - pb) < 1e-6:
                            pairs.append((a, b, ax, side, pa))
    return pairs


def coplanar_faces(model):
    return [f"{a.bone.name}{tuple(a.size)} and {b.bone.name}{tuple(b.size)}: {'xyz'[ax]}-{side} faces in the same "
            f"plane ({p:g})" for a, b, ax, side, p in coplanar_pairs(model)]


def fix_coplanar(model, step=0.1, rounds=6):
    """
    Grows one box of every z-fighting pair by `step` units on all sides (GeckoLib's cube inflate): invisible at
    this size, but the faces no longer share a plane. Returns how many boxes were grown.
    """
    grown = set()
    for _ in range(rounds):
        pairs = coplanar_pairs(model)
        if not pairs:
            break
        for a, b, _ax, _side, _p in pairs:
            # Grow the one that sits on the outside: shell over core, then the smaller (details), then the later one.
            def rank(c):
                return (c.bone.kind == "shell", -int(np.prod(c.size)), c.index)
            c = max((a, b), key=rank)
            c.inflate = round(c.inflate + step, 4)
            grown.add(id(c))
    return len(grown)


def stance(model):
    """Lowest point of the rest pose (should be 0) and the size in blocks."""
    mats = model.pose_matrices()
    lo, hi = model.bounds(mats, ("core", "shell"))
    clo, chi = model.bounds(mats, ("core", "molten"))
    return dict(shell_lo=lo, shell_hi=hi, shell_size=(hi - lo) / 16.0, core_lo=clo, core_hi=chi,
                core_size=(chi - clo) / 16.0)
