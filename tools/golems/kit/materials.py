"""
The golem materials. Each `material(...)` returns a painter: texels -> base / glow / statue colours.

Palette notes (all fire, no blue):
  cobblestone - warm greys;  obsidian - purple-black;  lava - deep red to white-hot yellow;
  crust       - lava that has cooled to dark red-brown (what the lava looks like when its glow fades);
  statue      - glossy obsidian black for the cooled statue (death).
"""
import numpy as np

from . import noise
from .paint import bevel, palette, pick, rgb, rgba

COBBLE = palette("#2a2827", "#3d3a38", "#524f4c", "#68645f", "#7f7a74", "#97918a")
COBBLE_OLD = palette("#232120", "#33302d", "#433f3b", "#55504a", "#68625b", "#7c766d")   # weathered, darker
COBBLE_DARK = palette("#171515", "#221f1e", "#2e2a28", "#3b3633", "#4a4440", "#5c5550")  # scorched near black
OBSIDIAN = palette("#07050b", "#0e0a15", "#161020", "#20172c", "#2c203e", "#433158")
LAVA = palette("#6a1904", "#a52d08", "#d74f0c", "#f47a15", "#ffa328", "#ffcd4c", "#fff0a6")
CRUST = palette("#170a07", "#28110a", "#3c170c", "#55200f", "#702b11")
STATUE = palette("#0a0710", "#140e1e", "#1f172d", "#2c2140", "#3d2f57", "#58457a")
BASALT = palette("#242327", "#302f34", "#3d3c42", "#4b4a51", "#5b5a62", "#6e6d75")
GOLD = palette("#5a3c0a", "#8a5f0f", "#bd8a17", "#e7b726", "#ffd94e", "#fff2a0")
IRON = palette("#16171b", "#22242a", "#30333b", "#41454f", "#565b67", "#707684")
BLACKSTONE = palette("#141215", "#1d1a1e", "#272328", "#322d33", "#3e3840", "#4d464f")

CRACK_CRUST = rgb("#4a1606")


def _lava_cracks(P, seed, chunk, cracks, fissures, fissure_cell, warp_amt=1.3):
    """Chunk cells, which borders are lava cracks, and the long fissures. Returns a dict of arrays."""
    n = len(P)
    Pw = noise.warp(P, warp_amt, 9.0, seed)
    id1, id2, border, f1, c1 = noise.voronoi(Pw, chunk, seed)
    lo, hi = np.minimum(id1, id2), np.maximum(id1, id2)
    crack_pair = noise.hash_ids(lo, hi, seed + 5) < cracks
    if fissures > 0:
        Pf = noise.warp(P, 3.5, 14.0, seed + 3)
        fid1, fid2, fb, _, _ = noise.voronoi(Pf, fissure_cell, seed + 13, jitter=0.95)
        fpair = noise.hash_ids(np.minimum(fid1, fid2), np.maximum(fid1, fid2), seed + 17) < fissures
    else:
        fb = np.full(n, 99.0)
        fpair = np.zeros(n, dtype=bool)
    d_lava = np.minimum(np.where(crack_pair, border, 99.0), np.where(fpair, fb, 99.0))
    return dict(Pw=Pw, id1=id1, border=border, c1=c1, d_lava=d_lava)


def rock(seed=1, chunk=9.0, obsidian=0.25, cracks=0.45, fissures=0.6, fissure_cell=22.0, scorch=1.0,
         cobble=COBBLE, obsid=OBSIDIAN, stone_cell=4.2, crack_width=0.55, heat=1.0, holes=0.0, gold_border=False):
    """
    The shell: chunks of cobblestone and obsidian (each chunk one or the other), dark seams between chunks,
    and glowing lava cracks along some seams plus long fissures that run across several chunks.
      heat:        1 = yellow-hot cracks; lower = older, redder, dimmer cracks
      holes:       share of chunks broken out of the plate (see-through: only for plates over the lava body)
      gold_border: a gilded rim round every face (the ornate design)
    """

    def paint(tx):
        P, N = tx.P, tx.N
        k = _lava_cracks(P, seed, chunk, cracks, fissures, fissure_cell)
        border, d_lava = k["border"], k["d_lava"]
        is_obs = noise.hash_ids(k["id1"], 0, seed + 7) < obsidian
        cshade = noise.hash_ids(k["id1"], 0, seed + 11) - 0.5
        lava = d_lava < crack_width
        hot = d_lava < crack_width * 0.4
        seam = (border < 0.5) & ~lava
        near_edge = np.clip(1.0 - border / 2.4, 0.0, 1.0)
        bev_c = bevel(k["Pw"], N, k["c1"]) * near_edge
        # Cobblestone: little stones with mortar between them.
        sid1, _sid2, sb, _sf, sc1 = noise.voronoi(P, stone_cell, seed + 21, jitter=0.9)
        sshade = noise.hash_ids(sid1, 0, seed + 23)
        bev_s = bevel(P, N, sc1)
        cob = 2.3 + (sshade - 0.5) * 1.7 + bev_s * 1.0 + cshade * 1.1 + bev_c * 1.0
        cob = np.where(sb < 0.42, np.minimum(cob, 1.4) - 0.4, cob)
        # Obsidian: dark glass with lighter streaks and the odd sparkle.
        streak = noise.fbm(P * np.array([1.0, 0.55, 1.0]), 5.5, 3, seed + 31)
        obs = 1.3 + (streak - 0.45) * 4.0 + cshade * 1.0 + bev_c * 1.5
        sparkle = noise.hash_float(np.floor(P[:, 0]), np.floor(P[:, 1]), np.floor(P[:, 2]), seed + 33) < 0.014
        obs = np.where(sparkle, 5.0, obs)
        outline = tx.edge < 0.5
        cob = np.where(outline, cob - 0.6, cob)
        obs = np.where(outline, obs - 0.5, obs)
        base = np.where(is_obs[:, None], pick(obsid, obs), pick(cobble, cob))
        base = np.where(seam[:, None], np.where(is_obs[:, None], obsid[0], cobble[0]), base)
        # Heat: the rock right next to a crack is scorched dark red and glows faintly.
        scorched = np.clip(1.0 - (d_lava - crack_width) / 1.1, 0.0, 1.0) * scorch * (~lava)
        base = base * (1.0 - 0.45 * scorched[:, None]) + rgb("#5c2312") * (0.45 * scorched[:, None])
        base[lava] = CRACK_CRUST
        glow = np.zeros((tx.n, 4))
        hot_col = pick(LAVA, np.full(tx.n, 3.0 + 2.0 * heat))
        warm_col = pick(LAVA, np.full(tx.n, 1.0 + 2.0 * heat))
        glow[lava] = rgba(np.where(hot[lava][:, None], hot_col[lava], warm_col[lava]))
        warm = (~lava) & (d_lava < crack_width + 0.6)
        glow[warm] = rgba(np.repeat(LAVA[0:1], warm.sum(), axis=0), 60 * heat)
        # Statue: the same chunks in polished obsidian; the cracks cooled to black glass.
        st = 2.1 + cshade * 1.3 + bev_c * 1.7 + np.where(is_obs, (streak - 0.45) * 1.6, (sshade - 0.5) * 0.9 + bev_s * 0.6)
        st = np.where(seam, st - 1.3, st)
        st = np.where(outline, st - 0.6, st)
        st = np.where(sparkle, 5.0, st)
        statue = pick(STATUE, st)
        statue[lava] = STATUE[0]
        if gold_border:
            rim = tx.edge < 1.0
            gi = 3.0 + bevel(P, N, np.array([tx.cubes[i].center() for i in tx.cube_index])) * 1.2
            base[rim] = pick(GOLD, gi)[rim]
            glow[rim] = 0.0
            statue[rim] = pick(GOLD, gi - 0.8)[rim]
        out = dict(base=rgba(base), glow=glow, statue=rgba(statue))
        if holes > 0:
            gone = (noise.hash_ids(k["id1"], 0, seed + 41) < holes) & (tx.edge >= 1.0)
            for key in out:
                out[key][gone] = 0.0
        return out

    return paint


def basalt(seed=1, pal=BASALT, cracks=0.0):
    """
    Basalt columns: grey with fine vertical streaks, darker edges, ring patterns on the end faces, the odd
    lava seam. Statue: obsidian with the same streaks.
    """

    def paint(tx):
        P, N = tx.P, tx.N
        s = noise.fbm(P * np.array([1.0, 0.16, 1.0]), 2.6, 2, seed)
        idx = 2.2 + (s - 0.5) * 3.4
        centres = np.array([tx.cubes[i].center() for i in tx.cube_index])
        b = bevel(P, N, centres)
        idx = idx + b * 0.9
        top = np.abs(N[:, 1]) > 0.5
        r = np.linalg.norm((P - centres)[:, [0, 2]], axis=1)
        ring = (np.floor(r * 0.9) % 2) == 0
        idx = np.where(top, 2.6 + np.where(ring, 0.7, -0.4) + (s - 0.5) * 1.5, idx)
        idx = np.where(tx.edge < 0.5, idx - 1.1, idx)
        base = pick(pal, idx)
        glow = np.zeros((tx.n, 4))
        if cracks > 0:
            id1, id2, border, _f, _c = noise.voronoi(P * np.array([1.0, 0.5, 1.0]), 6.0, seed + 3)
            seam = (border < 0.45) & (noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 5) < cracks)
            base[seam] = CRACK_CRUST
            glow[seam] = rgba(np.repeat(LAVA[2:3], seam.sum(), axis=0))
        st = 2.0 + (s - 0.5) * 2.2 + b * 0.8 - np.where(tx.edge < 0.5, 0.9, 0.0)
        return dict(base=rgba(base), glow=glow, statue=rgba(pick(STATUE, st)))

    return paint


def lava(seed=1, crust=0.80, hot=0.0, flow_cell=10.0, edge_cool=1.0, hot_spots=(), plates=0.0, plate_cell=7.0,
         bubbles=0.0):
    """
    Molten body: flowing orange and yellow lava, cooler and darker along the edges of every box (so the
    shapes read), a few dark crust islands floating on it (they don't glow) and optional white-hot spots.
    Under the glow, the base texture is the same flow in cooled red-brown crust, so the body darkens into
    crust as the glow fades; the statue is the flow frozen in obsidian.
      crust:     higher = fewer crust islands (about 0.8 gives a few, above 1.0 none)
      hot:       brighter overall
      edge_cool: how strongly box edges darken
      hot_spots: [(x, y, z, radius)] Bedrock bind-pose points that glow white-hot (chest, joints...)
      plates:    share of the surface under black crust plates (cooling lava breaking into plates, glowing
                 seams between them); 0 = none
      bubbles:   share of the surface with bright slag bubbles (white-hot rings)
    """

    def paint(tx):
        P, N = tx.P, tx.N
        q = noise.warp(P, 4.0, 12.0, seed)
        f = noise.fbm(q, flow_cell, 3, seed + 1)
        g = noise.fbm(P * np.array([1.0, 0.6, 1.0]), 4.0, 2, seed + 2)
        band = (f * 4.0 + g * 0.8) % 1.0
        idx = 3.0 + (f - 0.5) * 3.6 + hot * 1.5
        streak = np.abs(band - 0.5) < 0.06
        dark = band < 0.06
        idx = idx + np.where(streak, 1.4, 0.0) - np.where(dark, 1.4, 0.0)
        # Edges cool first: darker rims define every box.
        e = tx.edge
        idx = idx - edge_cool * (np.where(e < 0.5, 1.6, 0.0) + np.where((e >= 0.5) & (e < 1.5), 0.6, 0.0))
        for (x, y, z, r) in hot_spots:
            d = np.linalg.norm(P - np.array([x, y, z]), axis=1)
            idx = idx + np.clip(1.0 - d / r, 0.0, 1.0) * 2.4
        c = noise.fbm(P, 7.0, 2, seed + 40)
        is_crust = (c > crust) & (e >= 0.5)
        rim = (c > crust - 0.03) & ~is_crust & (e >= 0.5)
        if plates > 0:
            # Crust plates: Voronoi cells, some of them crusted over, with glowing seams between.
            pid1, pid2, pborder, _f, pc1 = noise.voronoi(noise.warp(P, 1.0, 8.0, seed + 50), plate_cell, seed + 51)
            crusted = noise.hash_ids(pid1, 0, seed + 52) < plates
            plate = crusted & (pborder > 0.7) & (e >= 0.5)
            prim = crusted & (pborder > 0.35) & (pborder <= 0.7) & (e >= 0.5)
            is_crust = is_crust | plate
            rim = (rim | prim) & ~is_crust
            idx = np.where(crusted & (pborder <= 0.35), idx + 1.2, idx)   # the seams run hot
        if bubbles > 0:
            bid1, _b2, _bb, bf1, _bc = noise.voronoi(P, 3.0, seed + 60)
            bub = noise.hash_ids(bid1, 0, seed + 61) < bubbles
            idx = np.where(bub & (bf1 < 0.8), idx + 2.5, idx)
            idx = np.where(bub & (bf1 >= 0.8) & (bf1 < 1.3), idx - 1.3, idx)
            is_crust = is_crust & ~(bub & (bf1 < 1.3))
        glow = rgba(pick(LAVA, idx))
        glow[is_crust, 3] = 0
        glow[rim] = rgba(np.repeat(LAVA[2:3], rim.sum(), axis=0))
        cidx = 1.8 + (f - 0.5) * 3.0 + np.where(streak, 0.8, 0.0) - np.where(e < 0.5, 0.9, 0.0)
        base = pick(CRUST, cidx)
        cb = bevel(P, N, np.round(P / 3.0) * 3.0)
        base[is_crust] = pick(CRUST, 1.3 + cb[is_crust] * 0.9)
        st = 2.0 + (f - 0.5) * 2.4 + np.where(streak, 1.2, 0.0) - np.where(dark, 0.8, 0.0) - np.where(e < 0.5, 0.8, 0.0)
        st = np.where(is_crust, 1.1 + cb * 0.8, st)
        return dict(base=rgba(base), glow=glow, statue=rgba(pick(STATUE, st)))

    return paint


def crater_pool(seed=1):
    """
    The lava pool in a volcano's crater. It's always molten: the mod draws its bright lava from the flow texture
    (see paint.paint_flows), even while the golem is dormant; this glow matches it so the glow pass doesn't dull it,
    and the crust underneath is what's left when the glow fades as it dies.
    """

    def paint(tx):
        P, N = tx.P, tx.N
        v = noise.fbm(P, 2.5, 2, seed)
        base = pick(CRUST, 1.2 + (v - 0.5) * 2.5)
        glow = rgba(pick(LAVA, 4.2 + (v - 0.5) * 2.4))
        statue = pick(STATUE, 1.5 + (v - 0.5) * 1.5)
        return dict(base=rgba(base), glow=glow, statue=rgba(statue))

    return paint


def fang(seed=1, palette_=OBSIDIAN):
    """Obsidian teeth and tusks: dark glass, lighter towards the light."""

    def paint(tx):
        P, N = tx.P, tx.N
        centres = np.array([tx.cubes[i].center() for i in tx.cube_index])
        b = bevel(P, N, centres)
        idx = 2.6 + b * 1.6 + (noise.hash_float(np.floor(P[:, 0]), np.floor(P[:, 1]), np.floor(P[:, 2]), seed) - 0.5)
        base = pick(palette_, idx)
        statue = pick(STATUE, idx + 0.3)
        return dict(base=rgba(base), glow=np.zeros((tx.n, 4)), statue=rgba(statue))

    return paint


def eye(color=None):
    """A glowing eye: white-hot, full bright."""

    def paint(tx):
        col = LAVA[6] if color is None else rgb(color)
        base = np.repeat(LAVA[5:6], tx.n, axis=0)
        glow = rgba(np.repeat(col[None, :], tx.n, axis=0))
        return dict(base=rgba(base), glow=glow, statue=rgba(np.repeat(STATUE[1:2], tx.n, axis=0)))

    return paint


def solid(base_pal, idx=2.5, glow=None, statue_idx=2.0, seed=1, cell=3.0, var=1.2):
    """A simple material from one palette with pixel noise (for small details)."""

    def paint(tx):
        P, N = tx.P, tx.N
        v = noise.value_noise(P, cell, seed)
        centres = np.array([tx.cubes[i].center() for i in tx.cube_index])
        b = bevel(P, N, centres)
        i = idx + (v - 0.5) * var * 2 + b * 0.8 - np.where(tx.edge < 0.5, 0.5, 0.0)
        base = pick(base_pal, i)
        g = np.zeros((tx.n, 4))
        if glow is not None:
            g = rgba(np.repeat(rgb(glow)[None, :], tx.n, axis=0))
        return dict(base=rgba(base), glow=g, statue=rgba(pick(STATUE, statue_idx + (v - 0.5) * 1.5 + b * 0.8)))

    return paint


def glyphs(A, B, FW, FH, edge, cube_index, face, seed, share):
    """
    Rune strokes on a face: glyphs on a 5 x 7 grid of texels (1-texel lines picked from a 3 x 5 grid of points),
    kept 2 texels clear of the face's edges, on faces big enough to hold one. True where a stroke is.
    """
    inner = (edge >= 2.0) & (FW >= 7) & (FH >= 9)
    ga = np.floor((A - 2.0) / 5.0)
    gb = np.floor((B - 2.0) / 7.0)
    gx = np.floor(A - 2.0 - ga * 5.0) - 1.0       # -1..3 inside the cell; the glyph uses 0..2
    gy = np.floor(B - 2.0 - gb * 7.0) - 1.0       # -1..5; the glyph uses 0..4
    cell_id = (ga.astype(np.int64) * 131 + gb.astype(np.int64) * 7919 + np.asarray(cube_index, dtype=np.int64) * 104729
               + np.asarray(face, dtype=np.int64) * 13)
    has = noise.hash_ids(cell_id, 0, seed + 11) < share
    ink = (gx >= 0) & (gx <= 2) & (gy >= 0) & (gy <= 4)
    strokes = (gx == 1, gy == 0, gy == 4, gy == 2, (gx == 0) & (gy <= 2), (gx == 2) & (gy >= 2),
               (gx == gy - 1) | (gx == 3 - gy))
    stroke = np.zeros(np.shape(A), dtype=bool)
    for k, cond in enumerate(strokes):
        stroke |= (noise.hash_ids(cell_id, k, seed + 17) < 0.42) & cond
    return inner & has & ink & stroke


def rune_brand(material, seed=1, share=0.35):
    """Overlay: runes branded into a lava material as dark, non-glowing lines (faint glints in the statue)."""

    def run(tm, base, glow, statue):
        ids = [c.index for c in tm.cubes if c.mat == material]
        mask = np.isin(tm.cube, ids)
        sel = np.nonzero(mask)
        A, B, FW, FH = tm.A[sel], tm.B[sel], tm.FW[sel], tm.FH[sel]
        edge = np.minimum(np.minimum(A, FW - A), np.minimum(B, FH - B))
        rune = glyphs(A, B, FW, FH, edge, tm.cube[sel], tm.face[sel], seed, share)
        idx = tuple(s[rune] for s in sel)
        base[idx] = rgba(np.repeat(CRUST[0:1], rune.sum(), axis=0))
        glow[idx] = 0
        statue[idx] = rgba(np.repeat(STATUE[4:5], rune.sum(), axis=0))

    return run


def carved(seed=1, base_pal=OBSIDIAN, border=True, runes=0.55, rune_glow=True, stone=False, cobble=COBBLE,
           cracks=0.0):
    """
    Carved armour plates: smooth polished obsidian (or cut stone) with a gold border round every face and glowing
    runes cut into the middle of the larger faces. In the statue the runes are dark grooves and the gold stays.
      runes: share of glyph cells that get a rune
    """

    def paint(tx):
        P, N = tx.P, tx.N
        centres = np.array([tx.cubes[i].center() for i in tx.cube_index])
        b = bevel(P, N, centres)
        v = noise.fbm(P * np.array([1.0, 0.6, 1.0]), 4.0, 2, seed)
        if stone:
            # Cut stone blocks: a running-bond brick pattern in face space.
            row = np.floor(tx.B / 4.0)
            col = np.floor((tx.A + (row % 2) * 3.0) / 6.0)
            mortar = ((tx.B % 4.0) < 1.0) | (((tx.A + (row % 2) * 3.0) % 6.0) < 1.0)
            shade = noise.hash_ids(row.astype(np.int64) * 977 + col.astype(np.int64), tx.cube_index, seed) - 0.5
            idx = 3.0 + shade * 1.4 + (v - 0.5) * 1.2 + b * 0.8 - np.where(mortar, 1.6, 0.0)
            base = pick(cobble, idx)
        else:
            idx = 1.6 + (v - 0.5) * 2.4 + b * 1.4
            sparkle = noise.hash_float(np.floor(P[:, 0]), np.floor(P[:, 1]), np.floor(P[:, 2]), seed + 3) < 0.012
            idx = np.where(sparkle, 5.0, idx)
            base = pick(base_pal, idx)
        statue = pick(STATUE, 2.0 + (v - 0.5) * 1.6 + b * 1.2)
        glow = np.zeros((tx.n, 4))
        if cracks > 0:
            k = _lava_cracks(P, seed + 90, 9.0, cracks, 0.0, 20.0)
            crack = k["d_lava"] < 0.5
            base[crack] = CRACK_CRUST
            glow[crack] = rgba(np.repeat(LAVA[3:4], crack.sum(), axis=0))
            statue[crack] = STATUE[0]
        rune = glyphs(tx.A, tx.B, tx.FW, tx.FH, tx.edge, tx.cube_index, tx.face, seed, runes)
        base[rune] = rgb("#2a0d05")
        if rune_glow:
            glow[rune] = rgba(np.repeat(LAVA[4:5], rune.sum(), axis=0))
        statue[rune] = STATUE[0]
        if border:
            edge = tx.edge < 1.0
            gi = 3.0 + b * 1.2 + (noise.value_noise(P, 2.0, seed + 5) - 0.5) * 1.6
            gold = pick(GOLD, gi)
            base[edge] = gold[edge]
            statue[edge] = pick(GOLD, gi - 0.8)[edge]
        return dict(base=rgba(base), glow=glow, statue=rgba(statue))

    return paint


def gold(seed=1):
    """Gilded metal: warm gold with bright edges. It doesn't cool: the statue keeps it (a little darker)."""

    def paint(tx):
        P, N = tx.P, tx.N
        centres = np.array([tx.cubes[i].center() for i in tx.cube_index])
        b = bevel(P, N, centres)
        i = 2.8 + b * 1.4 + (noise.value_noise(P, 2.0, seed) - 0.5) * 1.6 - np.where(tx.edge < 0.5, 0.8, 0.0)
        return dict(base=rgba(pick(GOLD, i)), glow=np.zeros((tx.n, 4)), statue=rgba(pick(GOLD, i - 0.9)))

    return paint


def chain(seed=1):
    """Iron chain: links along the box's longest side, dark gaps between them. Stays iron in the statue."""

    def paint(tx):
        P, N = tx.P, tx.N
        cubes = [tx.cubes[i] for i in tx.cube_index]
        axis = np.array([int(np.argmax(c.size)) for c in cubes])
        along = P[np.arange(tx.n), axis]
        link = np.floor(along / 3.0)
        gap = (along % 3.0) < 0.8
        centres = np.array([c.center() for c in cubes])
        b = bevel(P, N, centres)
        i = 2.6 + b * 1.5 + (link % 2) * 0.5 - np.where(gap, 2.0, 0.0)
        base = pick(IRON, i)
        return dict(base=rgba(base), glow=np.zeros((tx.n, 4)), statue=rgba(pick(IRON, i - 0.6)))

    return paint


# ---------------------------------------------------------------- the face


def _line(x0, y0, x1, y1):
    """Texel steps of a line (Bresenham) from (x0, y0) to (x1, y1)."""
    x0, y0, x1, y1 = int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1))
    pts = []
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy
    return pts


def face_overlay(eyes=(), sockets=(), cracks=(), skull_tag="skull", core_tag="cranium", crack_glow=True,
                 extra=None, eye_px=(), socket_px=()):
    """
    Draws the face on the FRONT (-z) of the boxes tagged skull_tag (the shell's stone face) and core_tag
    (the lava head underneath). Coordinates are units from the centre of that front face: x towards the
    golem's own left (+x, the viewer's right), y up.
      eyes:    (x, y, w, h) glowing eyes;      eye_px:    single glowing texels (x, y)
      sockets: (x, y, w, h) dark hollows;      socket_px: single dark texels (x, y)
      cracks:  polylines [(x, y), ...] of 1-texel lava cracks
    On the stone face the cracks glow; on the lava face they're dark crust, and the eyes burn white.
    extra(tm, base, glow, statue, sel, fx, fy, is_core): optional hook for more face detail.
    """

    def run(tm, base, glow, statue):
        for c in tm.cubes:
            if c.tag not in (skull_tag, core_tag):
                continue
            is_core = c.tag == core_tag
            sel = np.nonzero((tm.cube == c.index) & (tm.N[..., 2] < -0.5))
            if len(sel[0]) == 0:
                continue
            cx, cy, _cz = c.center()
            P = tm.P[sel]
            fx = np.floor(P[:, 0] - cx + 0.5 * (c.size[0] % 2))  # texel coordinates from the face centre
            fy = np.floor(P[:, 1] - cy + 0.5 * (c.size[1] % 2))
            hits = {}
            for (x, y, w, h) in sockets:
                m_ = (np.abs(fx - x + 0.5) <= w / 2.0) & (np.abs(fy - y + 0.5) <= h / 2.0)
                if is_core:
                    # On the lava face a hollow reads as sunglasses: just a dark brow line over each eye.
                    m_ &= fy == np.floor(y - 0.5 + h / 2.0)
                hits.setdefault("socket", np.zeros(len(fx), bool))
                hits["socket"] |= m_
            crack_mask = np.zeros(len(fx), bool)
            for poly in cracks:
                for (a, b2) in zip(poly[:-1], poly[1:]):
                    for (px, py) in _line(a[0], a[1], b2[0], b2[1]):
                        crack_mask |= (fx == px) & (fy == py)
            eye_mask = np.zeros(len(fx), bool)
            for (x, y, w, h) in eyes:
                eye_mask |= (np.abs(fx - x + 0.5) <= w / 2.0) & (np.abs(fy - y + 0.5) <= h / 2.0)
            for (x, y) in eye_px:
                eye_mask |= (fx == x) & (fy == y)
            for (x, y) in socket_px:
                hits.setdefault("socket", np.zeros(len(fx), bool))
                if not is_core:
                    hits["socket"] |= (fx == x) & (fy == y)
            sock = hits.get("socket", np.zeros(len(fx), bool)) & ~eye_mask
            idx = tuple(s[sock] for s in sel)
            if is_core:
                base[idx] = rgba(np.repeat(CRUST[0:1], sock.sum(), axis=0))
                glow[idx] = 0
                statue[idx] = rgba(np.repeat(STATUE[0:1], sock.sum(), axis=0))
            else:
                base[idx] = rgba(np.repeat(rgb("#0c0605")[None, :], sock.sum(), axis=0))
                glow[idx] = rgba(np.repeat(LAVA[0:1], sock.sum(), axis=0), 70)
                statue[idx] = rgba(np.repeat(STATUE[0:1], sock.sum(), axis=0))
            cm = crack_mask & ~eye_mask
            idx = tuple(s[cm] for s in sel)
            if is_core:
                base[idx] = rgba(np.repeat(CRUST[1:2], cm.sum(), axis=0))
                glow[idx] = 0
            else:
                base[idx] = rgba(np.repeat(CRACK_CRUST[None, :], cm.sum(), axis=0))
                if crack_glow:
                    glow[idx] = rgba(np.repeat(LAVA[4:5], cm.sum(), axis=0))
            statue[idx] = rgba(np.repeat(STATUE[0:1], cm.sum(), axis=0))
            idx = tuple(s[eye_mask] for s in sel)
            base[idx] = rgba(np.repeat(LAVA[5:6], eye_mask.sum(), axis=0))
            glow[idx] = rgba(np.repeat(LAVA[6:7], eye_mask.sum(), axis=0))
            statue[idx] = rgba(np.repeat(STATUE[1:2], eye_mask.sum(), axis=0))
            if extra is not None:
                extra(tm, base, glow, statue, sel, fx, fy, is_core)

    return run
