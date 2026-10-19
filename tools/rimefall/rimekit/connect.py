"""
Finds floating pieces: boxes that touch nothing of the model's main body (the user: "make sure for all models that
there is no floating" part, after seeing a rhino's chin fur hang in the air).

Two boxes are joined when they overlap or touch (turned boxes too: the separating-axis test, 15 axes, with a small
tolerance). The joined groups are worked out; every group but the biggest is a floating piece, unless all its boxes'
materials are in `free` (things meant to float: flung ice, frozen smoke, the light inside a cabin of real blocks).
"""
import numpy as np

TOUCH = 0.35        # units: boxes this close count as touching


def _boxes(model):
    """Every box's centre, axes (3 unit vectors, rows) and half sizes, in model space (units, GeckoLib frame)."""
    mats = model.pose_matrices()
    out = []
    for c in model.cubes():
        M = mats[c.bone.name] @ c.cube_matrix()
        ox, oy, oz = c.origin
        w, h, d = c.size
        i = c.inflate
        lo = np.array([-(ox + w) - i, oy - i, oz - i]) / 16.0
        hi = np.array([-ox + i, oy + h + i, oz + d + i]) / 16.0
        centre = (M @ np.append((lo + hi) / 2.0, 1.0))[:3] * 16.0
        axes = M[:3, :3].T            # rows: the box's own x, y, z in model space
        half = (hi - lo) / 2.0 * 16.0
        out.append((centre, axes, half))
    return out


def _touch(a, b, tol):
    ca, A, ha = a
    cb, B, hb = b
    t = cb - ca
    axes = list(A) + list(B)
    for u in A:
        for v in B:
            x = np.cross(u, v)
            n = np.linalg.norm(x)
            if n > 1e-6:
                axes.append(x / n)
    for L in axes:
        ra = np.sum(ha * np.abs(A @ L))
        rb = np.sum(hb * np.abs(B @ L))
        if abs(t @ L) > ra + rb + tol:
            return False
    return True


def floating(model, free=()):
    """[(materials, centre (Bedrock units), box count)] for each group of boxes joined to nothing else."""
    cubes = model.cubes()
    boxes = _boxes(model)
    n = len(boxes)
    centres = np.array([b[0] for b in boxes])
    reach = np.array([np.linalg.norm(b[2]) for b in boxes])
    parent = list(range(n))

    def find(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for i in range(n):
        d = np.linalg.norm(centres - centres[i], axis=1)
        for j in np.nonzero(d <= reach + reach[i] + TOUCH)[0]:
            if j <= i or find(i) == find(j):
                continue
            if _touch(boxes[i], boxes[j], TOUCH):
                parent[find(i)] = find(j)
    groups = {}
    for i in range(n):
        groups.setdefault(find(i), []).append(i)
    vol = {g: sum(float(np.prod(cubes[i].size)) for i in idx) for g, idx in groups.items()}
    main = max(vol, key=vol.get)
    out = []
    for g, idx in groups.items():
        if g == main:
            continue
        mats = sorted({cubes[i].mat for i in idx})
        if all(m in free for m in mats):
            continue
        c = centres[idx].mean(axis=0)
        out.append((mats, (-c[0], c[1], c[2]), len(idx)))
    return out
