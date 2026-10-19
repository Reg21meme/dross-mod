"""
Z-fighting check for models made of freely turned boxes (kit/checks.py only looks at boxes that aren't turned).

Two faces flicker in the game when they lie in the same plane, face the same way and overlap. This finds every such
pair, whatever the boxes' rotations (all boxes of a candidate sit on its one root bone), and grows one box of each pair
by a hair (GeckoLib's cube inflate) until none are left.
"""
import numpy as np

from kit import geo

PLANE_TOL = 0.004      # blocks: faces closer than this (and parallel) count as one plane
STEP = 0.15            # units: how much one box of a clashing pair grows each round (more than twice PLANE_TOL)
MAX_GROWTH = 1.0       # units: the most any box is grown


def _faces(cube, bone_matrix):
    """The cube's six faces in model space (blocks): (normal, offset, corners 4x3)."""
    M = bone_matrix @ cube.cube_matrix()
    ox, oy, oz = cube.origin
    w, h, d = cube.size
    i = cube.inflate / 16.0
    x0, x1 = -(ox + w) / 16.0 - i, -ox / 16.0 + i
    y0, y1 = oy / 16.0 - i, (oy + h) / 16.0 + i
    z0, z1 = oz / 16.0 - i, (oz + d) / 16.0 + i
    quads = (
        ((-1, 0, 0), [(x0, y0, z0), (x0, y1, z0), (x0, y1, z1), (x0, y0, z1)]),
        ((1, 0, 0), [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)]),
        ((0, -1, 0), [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]),
        ((0, 1, 0), [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]),
        ((0, 0, -1), [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)]),
        ((0, 0, 1), [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]),
    )
    out = []
    for normal, verts in quads:
        pts = np.array([(M @ np.array([p[0], p[1], p[2], 1.0]))[:3] for p in verts])
        n = M[:3, :3] @ np.array(normal, dtype=float)
        n /= np.linalg.norm(n)
        out.append((n, float(np.dot(n, pts[0])), pts))
    return out


def _overlap(a, b, n):
    """True if two convex quads in the same plane (normal n) overlap with some area (separating axis test)."""
    u = np.cross(n, [0.0, 1.0, 0.0] if abs(n[1]) < 0.9 else [1.0, 0.0, 0.0])
    u /= np.linalg.norm(u)
    w = np.cross(n, u)
    A = np.stack([a @ u, a @ w], axis=1)
    B = np.stack([b @ u, b @ w], axis=1)
    for poly in (A, B):
        for i in range(4):
            e = poly[(i + 1) % 4] - poly[i]
            axis = np.array([-e[1], e[0]])
            nl = np.linalg.norm(axis)
            if nl < 1e-12:
                continue
            axis /= nl
            pa = A @ axis
            pb = B @ axis
            if min(pa.max(), pb.max()) - max(pa.min(), pb.min()) <= 1e-3:   # touching edges don't count
                return False
    return True


def clashes(model):
    """Every pair of boxes with a shared, overlapping face plane facing the same way."""
    cubes = model.cubes()
    mats = model.pose_matrices()
    keyed = {}
    for c in cubes:
        for n, off, pts in _faces(c, mats[c.bone.name]):
            key = tuple(np.round(n, 3))
            keyed.setdefault(key, []).append((off, c, pts, n))
    pairs = []
    for key, faces in keyed.items():
        faces.sort(key=lambda f: f[0])
        for i in range(len(faces)):
            oi, ci, pi, ni = faces[i]
            for j in range(i + 1, len(faces)):
                oj, cj, pj, _nj = faces[j]
                if oj - oi > PLANE_TOL:
                    break
                if ci is cj:
                    continue
                if _overlap(pi, pj, ni):
                    pairs.append((ci, cj))
    return pairs


def fix(model, rounds=20):
    """Grows one box of every clashing pair until none clash. Returns (boxes grown, clashes left)."""
    grown = set()
    pairs = []
    for _ in range(rounds):
        pairs = clashes(model)
        if not pairs:
            break
        done = set()
        for a, b in pairs:
            if id(a) in done or id(b) in done:
                continue
            # Grow the smaller one (a detail sitting on a bigger surface); never
            # past MAX_GROWTH (two nearly parallel faces can swap sides for ever: a hairline left there is better than
            # a swollen box).
            ok = [c for c in sorted((a, b), key=lambda c: int(np.prod(c.size))) if c.inflate + STEP <= MAX_GROWTH]
            if not ok:
                continue
            c = ok[0]
            c.inflate = round(c.inflate + STEP, 4)
            grown.add(id(c))
            done.add(id(c))
    return len(grown), len(pairs)
