"""
Vectorised 3D noise for painting textures: hashing, value noise, fractal noise and Voronoi cells.
Everything takes arrays of points (..., 3) in model units and is deterministic for a given seed.
"""
import numpy as np

_M1 = np.uint32(0x9E3779B1)
_M2 = np.uint32(0x85EBCA77)
_M3 = np.uint32(0xC2B2AE3D)


def hash3i(ix, iy, iz, seed=0):
    """Integer lattice coordinates -> uint32 hash."""
    with np.errstate(over="ignore"):
        h = (np.asarray(ix, dtype=np.int64).astype(np.uint32) * _M1
             ^ np.asarray(iy, dtype=np.int64).astype(np.uint32) * _M2
             ^ np.asarray(iz, dtype=np.int64).astype(np.uint32) * _M3
             ^ np.uint32(seed * 0x27D4EB2F & 0xFFFFFFFF))
        h ^= h >> np.uint32(15)
        h *= np.uint32(0x2C1B3C6D)
        h ^= h >> np.uint32(12)
        h *= np.uint32(0x297A2D39)
        h ^= h >> np.uint32(15)
    return h


def hash_float(ix, iy, iz, seed=0):
    """Integer lattice coordinates -> float in [0, 1)."""
    return hash3i(ix, iy, iz, seed).astype(np.float64) / 4294967296.0


def hash_ids(a, b=0, seed=0):
    """Hash of one or two integer ids (for example a Voronoi cell id, or a pair of them) -> [0, 1)."""
    return hash_float(a, b, 0, seed)


def value_noise(p, cell=4.0, seed=0):
    """Smooth value noise in [0, 1] with features about `cell` units across."""
    q = np.asarray(p, dtype=float) / cell
    i = np.floor(q)
    f = q - i
    u = f * f * (3.0 - 2.0 * f)
    ix, iy, iz = i[..., 0].astype(np.int64), i[..., 1].astype(np.int64), i[..., 2].astype(np.int64)
    out = 0.0
    for dx in (0, 1):
        wx = u[..., 0] if dx else 1.0 - u[..., 0]
        for dy in (0, 1):
            wy = u[..., 1] if dy else 1.0 - u[..., 1]
            for dz in (0, 1):
                wz = u[..., 2] if dz else 1.0 - u[..., 2]
                out = out + wx * wy * wz * hash_float(ix + dx, iy + dy, iz + dz, seed)
    return out


def fbm(p, cell=8.0, octaves=3, seed=0, gain=0.5):
    """Fractal (layered) value noise in [0, 1]."""
    total = 0.0
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        total = total + amp * value_noise(p, cell / (2 ** o), seed + 101 * o)
        norm += amp
        amp *= gain
    return total / norm


def warp(p, amount=2.0, cell=10.0, seed=0):
    """Pushes points around with noise (domain warping), to make straight cell borders wander."""
    p = np.asarray(p, dtype=float)
    d = np.stack([fbm(p, cell, 2, seed + k * 17) - 0.5 for k in range(3)], axis=-1)
    return p + d * (2.0 * amount)


def voronoi(p, cell=8.0, seed=0, jitter=0.85, stretch=(1.0, 1.0, 1.0)):
    """
    3D Voronoi cells about `cell` units across.
    Returns (id1, id2, border, f1, centre1):
      id1     - the id of the cell each point is in (int64),
      id2     - the id of the nearest neighbouring cell,
      border  - distance (units) from the point to the border between those two cells,
      f1      - distance to its own cell's centre,
      centre1 - that centre (units).
    `stretch` squashes space before the cells are made (for example tall columns: (1, 0.35, 1)).
    """
    st = np.asarray(stretch, dtype=float)
    q = np.asarray(p, dtype=float) * st / cell
    base = np.floor(q).astype(np.int64)
    shape = q.shape[:-1]
    best1 = np.full(shape, np.inf)
    best2 = np.full(shape, np.inf)
    id1 = np.zeros(shape, dtype=np.int64)
    id2 = np.zeros(shape, dtype=np.int64)
    c1 = np.zeros(q.shape)
    c2 = np.zeros(q.shape)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx = base[..., 0] + dx
                cy = base[..., 1] + dy
                cz = base[..., 2] + dz
                fx = cx + 0.5 + (hash_float(cx, cy, cz, seed) - 0.5) * jitter
                fy = cy + 0.5 + (hash_float(cx, cy, cz, seed + 1) - 0.5) * jitter
                fz = cz + 0.5 + (hash_float(cx, cy, cz, seed + 2) - 0.5) * jitter
                feat = np.stack([fx, fy, fz], axis=-1)
                d = np.sum((q - feat) ** 2, axis=-1)
                cid = (cx * 73856093) ^ (cy * 19349663) ^ (cz * 83492791)
                closer1 = d < best1
                closer2 = (~closer1) & (d < best2)
                # shift best1 -> best2 where a new best appears
                best2 = np.where(closer1, best1, np.where(closer2, d, best2))
                id2 = np.where(closer1, id1, np.where(closer2, cid, id2))
                c2 = np.where(closer1[..., None], c1, np.where(closer2[..., None], feat, c2))
                best1 = np.where(closer1, d, best1)
                id1 = np.where(closer1, cid, id1)
                c1 = np.where(closer1[..., None], feat, c1)
    # Distance to the bisector plane of the two nearest centres, back in units (approximate when stretched).
    diff = c2 - c1
    dl = np.linalg.norm(diff, axis=-1)
    dl = np.where(dl < 1e-9, 1e-9, dl)
    mid = (c1 + c2) * 0.5
    border = np.abs(np.sum((q - mid) * diff, axis=-1) / dl) * cell / np.min(st)
    f1 = np.sqrt(best1) * cell / np.min(st)
    centre1 = c1 * cell / st
    return id1, id2, border, f1, centre1
