"""
Paints a golem's three textures, texel by texel, from 3D patterns:
  base   - what the golem looks like (rock shell, cooled-looking lava under the glow),
  glow   - the glowing parts only (lava cracks, the lava body, eyes); drawn full-bright on top,
  statue - the same golem cooled into obsidian (the death animation fades it in).

Every texel of the box-UV texture belongs to one face of one box. TexelMap works out, for every texel,
where on the model it sits in 3D (bind pose, Bedrock units), so the patterns (rock chunks, cracks, lava
flow) run on across the edges of a box instead of being painted face by face. Each box names a material;
a material is a function that turns those texels into colours (see `materials.py`).
"""
import numpy as np

from . import geo

ROCK_LIGHT = np.array([-0.45, 0.8, -0.4]) / np.linalg.norm([-0.45, 0.8, -0.4])  # pixel-art "light" for bevels


class TexelMap:
    """For every texel: its box, face, bind-pose position, normal and position on the face."""

    def __init__(self, model):
        H, W = model.tex_h, model.tex_w
        self.H, self.W = H, W
        self.cube = np.full((H, W), -1, dtype=np.int32)
        self.face = np.full((H, W), -1, dtype=np.int8)
        self.P = np.zeros((H, W, 3))
        self.N = np.zeros((H, W, 3))
        self.A = np.zeros((H, W))      # units along the face's u direction
        self.B = np.zeros((H, W))      # units along the face's v direction (down the face on side faces)
        self.FW = np.zeros((H, W))     # face width in units (u)
        self.FH = np.zeros((H, W))     # face height in units (v)
        self.cubes = model.cubes()
        for c in self.cubes:
            for fi, (face, verts, uvs, normal) in enumerate(c.quads(W, H)):
                (ua, va), (ub, vb), (uc, vc), _ = uvs
                us, vs = ua - ub, vc - vb
                umin, umax = int(min(ub, ua)), int(max(ub, ua))
                vmin, vmax = int(min(vb, vc)), int(max(vb, vc))
                if umax <= umin or vmax <= vmin:
                    continue
                iu, iv = np.meshgrid(np.arange(umin, umax), np.arange(vmin, vmax))
                s = (iu + 0.5 - ub) / us
                t = (iv + 0.5 - vb) / vs
                v0, v1, v2 = verts[0], verts[1], verts[2]
                pts = v1[None, None, :] + s[..., None] * (v0 - v1)[None, None, :] + t[..., None] * (v2 - v1)[None, None, :]
                bed = np.stack([-pts[..., 0] * 16.0, pts[..., 1] * 16.0, pts[..., 2] * 16.0], axis=-1)
                self.cube[iv, iu] = c.index
                self.face[iv, iu] = fi
                self.P[iv, iu] = bed
                self.N[iv, iu] = (-normal[0], normal[1], normal[2])
                self.A[iv, iu] = s * abs(us)
                self.B[iv, iu] = t * abs(vs)
                self.FW[iv, iu] = abs(us)
                self.FH[iv, iu] = abs(vs)

    def edge(self):
        """Distance (units) from each texel centre to the nearest edge of its face."""
        return np.minimum(np.minimum(self.A, self.FW - self.A), np.minimum(self.B, self.FH - self.B))


class Texels:
    """The texels one material paints, as flat arrays."""

    def __init__(self, tm, sel):
        self.sel = sel
        self.P = tm.P[sel]
        self.N = tm.N[sel]
        self.A = tm.A[sel]
        self.B = tm.B[sel]
        self.FW = tm.FW[sel]
        self.FH = tm.FH[sel]
        self.cube_index = tm.cube[sel]
        self.face = tm.face[sel]
        self.edge = np.minimum(np.minimum(self.A, self.FW - self.A), np.minimum(self.B, self.FH - self.B))
        self.cubes = tm.cubes
        self.n = len(self.P)

    def subset(self, mask):
        t = object.__new__(Texels)
        for k, v in self.__dict__.items():
            if isinstance(v, np.ndarray) and v.shape[:1] == (self.n,):
                setattr(t, k, v[mask])
            else:
                setattr(t, k, v)
        t.n = int(mask.sum())
        t.sel = tuple(s[mask] for s in self.sel)
        return t


def rgb(hexstr):
    h = hexstr.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def palette(*hexes):
    return np.array([rgb(h) for h in hexes])


def pick(pal, idx):
    idx = np.clip(np.round(idx).astype(int), 0, len(pal) - 1)
    return pal[idx]


def bevel(P, N, centre, strength=1.0):
    """
    -1..1: which side of a stone a texel is on, seen with light from the upper left/front.
    Positive towards the light (highlight), negative away (shadow).
    """
    d = P - centre
    # Project the offset and the light onto the face plane.
    dn = np.sum(d * N, axis=-1, keepdims=True)
    d = d - dn * N
    ln = np.sum(ROCK_LIGHT[None, :] * N, axis=-1, keepdims=True)
    lp = ROCK_LIGHT[None, :] - ln * N
    lpn = np.linalg.norm(lp, axis=-1, keepdims=True)
    lp = lp / np.where(lpn < 1e-9, 1.0, lpn)
    dl = np.linalg.norm(d, axis=-1)
    val = np.sum(d * lp, axis=-1) / np.where(dl < 1e-9, 1.0, dl)
    return np.clip(val * strength, -1.0, 1.0)


def paint_model(model, materials, overlays=None):
    """
    materials: {name: painter(texels) -> dict(base=(n,4), glow=(n,4), statue=(n,4)) uint8-ready arrays}
    overlays:  [callable(tm, base, glow, statue)] run afterwards (faces, eyes, runes...).
    Returns (base, glow, statue) HxWx4 uint8 arrays.
    """
    tm = TexelMap(model)
    H, W = tm.H, tm.W
    base = np.zeros((H, W, 4))
    glow = np.zeros((H, W, 4))
    statue = np.zeros((H, W, 4))
    mats = np.array([c.mat for c in tm.cubes], dtype=object)
    used = sorted(set(mats.tolist()))
    for name in used:
        if name not in materials:
            raise KeyError(f"no painter for material '{name}'")
        cube_ids = np.array([i for i, m in enumerate(mats) if m == name])
        sel = np.nonzero(np.isin(tm.cube, cube_ids))
        tx = Texels(tm, sel)
        if tx.n == 0:
            continue
        out = materials[name](tx)
        base[sel] = out["base"]
        glow[sel] = out["glow"]
        statue[sel] = out["statue"]
    for ov in overlays or ():
        ov(tm, base, glow, statue)
    return (np.clip(base, 0, 255).astype(np.uint8), np.clip(glow, 0, 255).astype(np.uint8),
            np.clip(statue, 0, 255).astype(np.uint8))


def rgba(colors, alpha=255.0):
    a = np.full((len(colors), 1), alpha, dtype=float) if np.isscalar(alpha) else np.asarray(alpha, dtype=float)[:, None]
    return np.concatenate([colors, a], axis=1)


# ---------------------------------------------------------------- lava flows (the eruption)


def _pool_shades(model, tm, sel, pool, seed):
    """
    LAVA palette indices for the always-bright boxes: the crater pools (hottest in the middle, cooler at the walls,
    crust swirls near them) and the eruption's blast column (bright, with streaks running up it).
    """
    from . import noise
    idx = np.zeros(len(pool))
    if not pool.any():
        return idx
    P = tm.P[sel]
    cubes = tm.cube[sel]
    for c in model.cubes():
        mine = pool & (cubes == c.index)
        if not mine.any():
            continue
        q = P[mine]
        swirl = noise.fbm(q, 2.2, 2, seed + 70)
        if c.tag == "crater":
            cx, _cy, cz = c.center()
            hx, hz = c.size[0] / 2.0, c.size[2] / 2.0
            rr = np.maximum(np.abs(q[:, 0] - cx) / hx, np.abs(q[:, 2] - cz) / hz)     # 0 middle .. 1 walls
            k = 6.0 - 2.6 * rr ** 1.5 + (swirl - 0.5) * 1.6
            k = np.where((swirl > 0.66) & (rr > 0.35), 1.2, k)                          # crust swirls near the walls
        else:
            streak = noise.fbm(q * np.array([1.0, 0.15, 1.0]), 1.5, 2, seed + 71)
            k = 4.9 + (streak - 0.5) * 3.0 + (swirl - 0.5) * 0.8
        idx[mine] = k
    return idx


def rest_world(model, tm):
    """Every texel's position and normal in the posed rest pose (Bedrock model space), for gravity-aware effects."""
    mats = model.pose_matrices()
    Pw = np.zeros_like(tm.P)
    Nw = np.zeros_like(tm.N)
    for c in tm.cubes:
        sel = np.nonzero(tm.cube == c.index)
        if len(sel[0]) == 0:
            continue
        M = mats[c.bone.name] @ c.cube_matrix()
        p = tm.P[sel]
        g = np.stack([-p[:, 0] / 16.0, p[:, 1] / 16.0, p[:, 2] / 16.0, np.ones(len(p))], axis=1)
        q = (M @ g.T).T[:, :3]
        Pw[sel] = np.stack([-q[:, 0] * 16.0, q[:, 1] * 16.0, q[:, 2] * 16.0], axis=1)
        n = tm.N[sel]
        gn = np.stack([-n[:, 0], n[:, 1], n[:, 2]], axis=1)
        qn = (M[:3, :3] @ gn.T).T
        Nw[sel] = np.stack([-qn[:, 0], qn[:, 1], qn[:, 2]], axis=1)
    return Pw, Nw


def paint_flows(model, sources, reach=70.0, streams=0.5, seed=1, head_bones=("head", "jaw", "jaw_inner")):
    """
    The flow texture: lava poured out of the crater(s) and running down over the shell, as it lies in the rest
    pose (gravity is world-down). Each texel's alpha says how soon the lava reaches it: alpha = 1 - 0.25 r for
    r = 0 (at the crater) .. 1 (the farthest). The mod draws it with Minecraft's "dissolve" shader (the one the ender
    dragon's death uses: a texel is dropped when its alpha is below the vertex alpha), lowering the vertex alpha from
    1 to 0.75, which pours the lava down progressively (GolemFlowLayer). That shader also blends by the alpha, so the
    alphas stay between 0.75 and 1: the lava is nearly opaque everywhere.
      sources: crater tops (Bedrock, posed rest pose)
      reach:   how far (units of travel: drop + 0.6 x sideways) the lava gets
      streams: how much of the surface the streams cover further down (0..1)
    Only shell boxes (and the crater's pool) get lava. The eruption's blast column ("burst" fountain boxes) is painted
    here too, fully opaque like the pools, so it's drawn bright and unlit whatever the glow strength.
    """
    from . import materials as M
    from . import noise
    tm = TexelMap(model)
    H, W = tm.H, tm.W
    out = np.zeros((H, W, 4))
    Pw, Nw = rest_world(model, tm)
    cubes = tm.cubes
    shell_ids = []
    pool_ids = []
    blast_ids = []
    head_ids = []
    for c in cubes:
        kinds = [b.kind for b in c.bone.ancestors()]
        names = [b.name for b in c.bone.ancestors()]
        if c.tag == "crater":
            pool_ids.append(c.index)
        elif "burst" in kinds and c.mat == "fountain":
            blast_ids.append(c.index)
        elif "shell" in kinds and "molten" not in kinds and "vent" not in kinds and "burst" not in kinds:
            shell_ids.append(c.index)
            if any(n in head_bones for n in names):
                head_ids.append(c.index)
    on_shell = np.isin(tm.cube, shell_ids)
    on_pool = np.isin(tm.cube, pool_ids) | np.isin(tm.cube, blast_ids)
    on_head = np.isin(tm.cube, head_ids)
    sel = np.nonzero(on_shell | on_pool)
    P = Pw[sel]
    N = Nw[sel]
    best = np.full(len(P), np.inf)
    for s in sources:
        s = np.asarray(s, dtype=float)
        dh = np.hypot(P[:, 0] - s[0], P[:, 2] - s[2])
        drop = s[1] - P[:, 1]
        travel = np.maximum(drop, 0.0) + 0.6 * dh + np.maximum(-drop, 0.0) * 2.5
        best = np.minimum(best, travel)
    r = np.clip(best / reach, 0.0, 1.0)
    pool = on_pool[sel]
    # Streams: vertical streaks in world space, fanning out and thinning as they run down.
    q = noise.warp(P * np.array([0.42, 0.06, 0.42]), 0.8, 3.0, seed)
    s1 = noise.fbm(q, 2.2, 2, seed + 1)
    thr = 0.56 + 0.14 * r - 0.2 * streams
    flat_up = N[:, 1] > 0.6
    pooled = flat_up & (r < 0.22)            # lava pools on surfaces facing up, right by the crater
    lava = ((s1 > thr) | pooled | (r < 0.08)) & (r < 1.0)
    head = on_head[sel]
    lava &= ~head | (s1 > thr + 0.12)        # keep the face mostly clear
    lava |= pool
    edge = lava & ~pool & ~pooled & (s1 < thr + 0.05) & (r >= 0.08)
    # Finer drips running down inside the streams, and flecks of cooling crust on the lava that has run furthest, so
    # a face the lava covers whole doesn't look flat.
    fine = noise.fbm(noise.warp(P * np.array([0.9, 0.22, 0.9]), 0.6, 2.0, seed + 7), 1.4, 2, seed + 9)
    idx = 3.6 + (s1 - 0.5) * 3.0 + (fine - 0.5) * 2.4 - r * 1.2
    idx = np.where(edge, 2.0, idx)
    fleck = (fine < 0.3) & (r > 0.3) & ~pooled
    idx = np.where(fleck, 1.0 + fine, idx)
    # The crater's pool: white-hot in the middle, orange towards the walls, with a few dark crust swirls.
    pool_idx = _pool_shades(model, tm, sel, pool, seed)
    idx = np.where(pool, pool_idx, idx)
    col = M.pick(M.LAVA, idx)
    # Alpha: the pool is fully opaque (255), so the mod's dissolve shader shows it even before any lava has poured
    # (vertex alpha 1); everything else stays under that and is revealed as the vertex alpha drops.
    alpha = np.where(pool, 255.0, np.clip(np.round(255.0 * (1.0 - 0.25 * r)), 191, 254))
    rgba_ = np.zeros((len(P), 4))
    rgba_[:, :3] = col
    rgba_[:, 3] = np.where(lava, alpha, 0.0)
    out[sel] = rgba_
    return np.clip(out, 0, 255).astype(np.uint8)
