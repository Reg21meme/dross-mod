"""
The Rat King's (and the rats') materials. Each `material(...)` returns a painter: texels -> base / glow / rage colours.
(paint.paint_model calls its third texture "statue"; here it is the rage texture: the red eyes that fade in when he
loses his temper. Everything else leaves it empty.)

Fur is painted from 3D positions in the bind pose, where the rat stands bolt upright with his arms and legs hanging:
strands run down every limb, the belly and muzzle are lighter, the back darker, and box edges get a darker outline
(the Minecraft look). Patterns (piebald patches, stripes, grizzled hairs, moss, mange, scars) are options.
"""
import numpy as np

from kit import noise
from kit.paint import bevel, palette, pick, rgb, rgba

# ---------------------------------------------------------------- shared palettes

SKIN = palette("#8c5a58", "#a86d6a", "#c08480", "#d39893", "#e3aba5", "#efc2bc")
SKIN_PALE = palette("#b47f80", "#c99496", "#dba8a9", "#e9bcbb", "#f3cfcd", "#fbe2df")
TAIL = palette("#7d5d5a", "#93706b", "#a8847e", "#bb9790", "#cba9a2", "#d9bbb4")
CLAW = palette("#8f8470", "#b2a68e", "#d1c6ad", "#e8dfca")
TOOTH = palette("#9a6a1c", "#c48a28", "#e0a83c", "#f2c45a", "#fbe08e")
NOSE = palette("#5a2a2e", "#8e4950", "#b8636a", "#d47f85", "#e8a0a4")
TONGUE = palette("#7a2333", "#a33545", "#c84b5b", "#e06a78")
GOLD = palette("#4f330a", "#7e560f", "#b07f17", "#dcae27", "#f7d24a", "#fff0a0")
GOLD_DARK = palette("#3a2507", "#5c3e0b", "#83600f", "#ab8117", "#cfa22a")
SILVER = palette("#3d4044", "#5a5e63", "#7c8086", "#a0a4a9", "#c5c8cc", "#e6e8ea")
TIN = palette("#3a4044", "#535b60", "#6f787d", "#8f989c", "#b0b8bb", "#d2d8da")
IRON = palette("#17181b", "#23252a", "#31343b", "#43474f", "#585d67", "#727884")
RUST = palette("#2c1a12", "#4a2a19", "#6d3c1f", "#8f5226", "#b0692e", "#c98a4a")
BRASS = palette("#4a3510", "#73531a", "#9c7428", "#c49a3b", "#e2bf5d", "#f5dd94")
WOOD = palette("#21150e", "#2e1d13", "#3e2718", "#51331f", "#664128", "#7d5233")
CANE = palette("#2b2117", "#3b2d1f", "#4e3c2a", "#634d37", "#7a6045", "#917455")
PIPE = palette("#3a2412", "#53341b", "#6d4524", "#88582e", "#a26c3a", "#ba824a")
BONE = palette("#6e6450", "#8d826a", "#ab9f84", "#c6bb9f", "#ddd3b8", "#eee7d2")
WAX = palette("#8a8063", "#a99e7e", "#c4ba98", "#ddd3b2", "#eee7c9", "#faf5e0")
VELVET = palette("#3a060c", "#560a14", "#73101d", "#901a28", "#ab2735")
ERMINE = palette("#9d978c", "#bdb7ab", "#d6d1c6", "#e9e5dc", "#f6f3ec")
PATCH = palette("#0e0d0e", "#181618", "#232023", "#2f2b2e")
LEATHER = palette("#24170f", "#342218", "#463022", "#5a3f2d")
DIRT = palette("#2a1c12", "#3a281a", "#4c3524", "#5f4430", "#73543c")
HOLE = palette("#050403", "#0e0a07", "#1a130d", "#291e15", "#3a2b1e")
CHAIN = palette("#1c1d21", "#2d2f35", "#43464e", "#5f636c", "#80858f")
GREEN_GLOW = palette("#1d4a12", "#2f6b1e", "#4f9a2b", "#79c93a", "#a6ec5b", "#d4ff8a", "#f0ffd0")
GREEN_FLAME = palette("#3a7a1a", "#62b02a", "#93e23f", "#c4ff6a", "#eaffb8")
RUBY = palette("#3d0408", "#66080f", "#930f19", "#c11b26", "#e5434b", "#ff8a8a")
PEARL = palette("#a9a39a", "#c6c0b6", "#ddd8cf", "#efebe4", "#fbf9f5")
RAGE = palette("#5a0000", "#8f0505", "#c41010", "#ec2a1a", "#ff6a3a", "#ffc070")
RIBBON_RED = palette("#4e0a0d", "#721217", "#971c20", "#b9292a", "#d33c37")
RIBBON_GOLD = palette("#6b4a0c", "#937014", "#bf9622", "#e2bb3c", "#f7da70")


def _zero(n):
    return np.zeros((n, 4))


def _out(tx, base, alpha=None, glow=None, rage=None):
    a = np.full(tx.n, 255.0) if alpha is None else alpha
    return dict(base=rgba(base, a), glow=glow if glow is not None else _zero(tx.n),
                statue=rage if rage is not None else _zero(tx.n))


def _centres(tx):
    return np.array([tx.cubes[i].center() for i in tx.cube_index])


def _tags(tx):
    return np.array([tx.cubes[i].tag or "" for i in tx.cube_index], dtype=object)


def _sizes(tx):
    return np.array([tx.cubes[i].size for i in tx.cube_index], dtype=float)


def _origins(tx):
    return np.array([tx.cubes[i].origin for i in tx.cube_index], dtype=float)


def _speck(P, seed):
    return noise.hash_float(np.floor(P[:, 0] * 1.0), np.floor(P[:, 1] * 1.0), np.floor(P[:, 2] * 1.0), seed)


def _face_shade(N):
    """Top faces a little lighter, bottom faces darker (on top of Minecraft's own lighting)."""
    return np.where(N[:, 1] > 0.5, 0.35, np.where(N[:, 1] < -0.5, -0.7, 0.0))


# ---------------------------------------------------------------- fur


LIGHT_TAGS = ("belly", "chest", "neck", "cheek", "muzzle", "jaw")
RAT_LIGHT_TAGS = ("muzzle", "jaw")


def fur(pal, light=None, seed=1, pattern=None, light_width=0.62, back_dark=0.6, outline=0.8, strand=3.0,
        patches=None, patch_cell=7.0, patch_share=0.45, stripes=None, grizzle=0.0, grizzle_pal=None, moss=None,
        moss_share=0.0, mange=0.0, scars=0.0, dark_ear=None, eye_patch=None, light_tags=LIGHT_TAGS, hood=False):
    """
    pal:      fur shades, dark to light (the main coat)
    light:    the belly/muzzle shades (cream, silver...), on the front of the torso, neck, cheeks and muzzle
    patches:  a second palette laid on in big irregular patches (piebald)
    stripes:  a darker palette in tabby stripes over the back and limbs
    grizzle:  share of single light hairs (old, grizzled fur), painted from grizzle_pal
    moss:     a greenish palette mixed in as mossy tinges (moss_share)
    mange:    share of bald pink patches
    scars:    share of pale scar lines
    dark_ear: "l"/"r": that ear (and its side of the skull's top) in the patch palette
    eye_patch:"l"/"r": a patch of the patch palette round that eye
    """

    def paint(tx):
        P, N = tx.P, tx.N
        tags = _tags(tx)
        cen = _centres(tx)
        size = _sizes(tx)
        s = noise.fbm(P * np.array([1.0, 0.3, 1.0]), 2.4, 2, seed)
        sp = _speck(P, seed + 3) - 0.5
        idx = 2.6 + (s - 0.5) * strand + sp * 0.9
        idx = idx + bevel(P, N, cen, 0.9) * 0.55 + _face_shade(N)
        # Darker down the back, a little lighter on the front.
        back = (N[:, 2] > 0.5)
        idx = np.where(back, idx - back_dark, idx)
        # Each box darkens towards its bottom edge (soft ambient shading).
        rel_y = (P[:, 1] - (cen[:, 1] - size[:, 1] / 2.0)) / np.maximum(size[:, 1], 1.0)
        side = np.abs(N[:, 1]) < 0.5
        idx = np.where(side, idx - (1.0 - np.clip(rel_y * 2.2, 0.0, 1.0)) * 0.6, idx)
        col = pick(pal, idx)
        # Light belly / muzzle.
        if light is not None:
            is_light_part = np.isin(tags, light_tags)
            hw = size[:, 0] / 2.0
            wob = (noise.fbm(P, 2.5, 2, seed + 9) - 0.5) * 1.6
            inside = np.abs(P[:, 0] - cen[:, 0]) < hw * light_width + wob
            front = N[:, 2] < -0.5
            bottom = N[:, 1] < -0.5
            under = np.isin(tags, ("cheek", "muzzle", "jaw")) & (bottom | (np.abs(N[:, 0]) > 0.5))
            lmask = is_light_part & ((front & inside) | under)
            lidx = 2.4 + (s - 0.5) * 2.0 + sp * 0.7 + bevel(P, N, cen, 0.9) * 0.4
            lcol = pick(light, lidx)
            col = np.where(lmask[:, None], lcol, col)
        if patches is not None:
            w = noise.warp(P, 2.5, 9.0, seed + 21)
            pn = noise.fbm(w, patch_cell, 2, seed + 22)
            pm = pn > (1.0 - patch_share)
            if dark_ear is not None:
                sx = 1 if dark_ear == "l" else -1
                pm = pm | (np.isin(tags, (f"ear_{dark_ear}",))) | ((tags == "skull") & (P[:, 0] * sx > 1.0) & (N[:, 1] > 0.5))
            if eye_patch is not None:
                sx = 1 if eye_patch == "l" else -1
                pm = pm | ((tags == "skull") & (P[:, 0] * sx > 0.5) & (np.abs(P[:, 1] - (cen[:, 1] + 1.0)) < 2.5)
                           & ((N[:, 2] < -0.5) | (N[:, 0] * sx > 0.5)))
            if hood:
                # A hooded rat: the whole head and shoulders, and a stripe down the back, in the patch colour.
                head_part = np.isin(tags, ("skull", "muzzle", "jaw", "nose_fur"))
                shoulders = (tags == "fore") & ((P[:, 2] < cen[:, 2] - 0.5) | (N[:, 1] > 0.5))
                stripe = np.isin(tags, ("rear", "rump")) & (N[:, 1] > 0.5) & (np.abs(P[:, 0]) < 1.2)
                pm = head_part | shoulders | stripe
            pidx = idx - 0.2
            col = np.where(pm[:, None], pick(patches, pidx), col)
        if stripes is not None:
            st = np.sin(P[:, 1] * 0.95 + (noise.fbm(P, 4.0, 2, seed + 31) - 0.5) * 4.0 + P[:, 0] * 0.15)
            sm = (st > 0.55) & ~np.isin(tags, ("belly", "cheek", "muzzle", "jaw")) & (N[:, 2] > -0.5)
            col = np.where(sm[:, None], pick(stripes, idx - 0.4), col)
        if moss is not None and moss_share > 0:
            mn = noise.fbm(P, 5.0, 3, seed + 41)
            mm = mn > (1.0 - moss_share)
            k = np.clip((mn - (1.0 - moss_share)) * 6.0, 0.0, 0.75)[:, None]
            col = np.where(mm[:, None], col * (1.0 - k) + pick(moss, idx) * k, col)
        if grizzle > 0:
            gm = _speck(P, seed + 51) < grizzle
            gp = grizzle_pal if grizzle_pal is not None else light if light is not None else pal
            col = np.where(gm[:, None], pick(gp, np.full(tx.n, len(gp) - 2.0) + sp), col)
        if mange > 0:
            mn = noise.fbm(P, 3.0, 2, seed + 61)
            mm = (mn > 1.0 - mange) & ~np.isin(tags, ("skull",))
            col = np.where(mm[:, None], pick(SKIN, 2.0 + (mn - 0.5) * 3.0 + sp), col)
        if scars > 0:
            id1, id2, border, _f, _c = noise.voronoi(noise.warp(P, 1.5, 6.0, seed + 71), 7.0, seed + 72)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 73) < scars
            sm = pair & (border < 0.35) & ~np.isin(tags, ("muzzle", "cheek"))
            col = np.where(sm[:, None], col * 0.55 + pick(SKIN_PALE, np.full(tx.n, 2.0)) * 0.45, col)
        # Outline: box edges a shade darker.
        out = tx.edge < 0.5
        col = np.where(out[:, None], col * (1.0 - 0.12 * outline), col)
        return _out(tx, col)

    return paint


def ermine(seed=79):
    """Ermine: white fur with the little black tail-tip spots in rows."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = 3.0 + (noise.fbm(P * np.array([1.0, 0.4, 1.0]), 1.8, 2, seed) - 0.5) * 1.6 + bevel(P, N, cen, 1.0) * 0.4
        col = pick(ERMINE, k)
        row = np.floor(P[:, 1] / 3.0)
        spot = ((np.floor(P[:, 0] + P[:, 2] + row * 2.0) % 4) == 0) & ((np.floor(P[:, 1]) % 3) == 0)
        col = np.where(spot[:, None], PATCH[1], col)
        return _out(tx, col)

    return paint


def ear(pal, inner=SKIN, seed=2, notch=None, dark=None, dark_pal=None):
    """Rounded ear discs: pink inside (the front face), fur on the back and rim. notch: 'l'/'r' bites a V out of it.
    dark: 'l'/'r' paints that ear's fur from dark_pal (a piebald's dark ear)."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        size = _sizes(tx)
        org = _origins(tx)
        tags = _tags(tx)
        u = (P[:, 0] - cen[:, 0]) / (size[:, 0] / 2.0)
        v = (P[:, 1] - cen[:, 1]) / (size[:, 1] / 2.0)
        r = u * u + v * v
        alpha = np.where(r > 1.08, 0.0, 255.0)
        if notch is not None:
            nm = (tags == f"ear_{notch}") & (np.abs(u - 0.15) < 0.22 * (1.0 + v)) & (v > 0.55)
            alpha = np.where(nm, 0.0, alpha)
        s = noise.fbm(P * np.array([1.0, 0.4, 1.0]), 2.0, 2, seed)
        front = N[:, 2] < -0.5
        rim = r > 0.74
        iidx = 3.4 - r * 1.6 + (s - 0.5) * 1.2
        fidx = 2.2 + (s - 0.5) * 2.4 + _speck(P, seed + 1) * 0.6
        furc = pick(pal, fidx)
        if dark is not None and dark_pal is not None:
            dm = tags == f"ear_{dark}"
            furc = np.where(dm[:, None], pick(dark_pal, fidx), furc)
        col = np.where((front & ~rim)[:, None], pick(inner, iidx), furc)
        return _out(tx, col, alpha)

    return paint


def eye(pal=None, glint="#f4f1ea", rage_pal=RAGE):
    """Beady eyes: dark with a white glint on the front. The rage texture paints them glowing red."""
    pal = pal if pal is not None else palette("#050404", "#0d0b0b", "#1a1616", "#2a2424")
    gl = rgb(glint)

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        idx = 1.4 + bevel(P, N, cen, 1.0) * 1.2
        col = pick(pal, idx)
        front = N[:, 2] < -0.5
        side = np.abs(N[:, 0]) > 0.5
        # One glint pixel high on the front, towards the middle of the face.
        inward = (P[:, 0] - cen[:, 0]) * -np.sign(cen[:, 0] + 1e-9)
        hi = front & (P[:, 1] > cen[:, 1]) & (inward > 0.0) & (inward < 1.0)
        col = np.where(hi[:, None], gl, col)
        # Rage: a hot core, darker red round the edge.
        d = np.max(np.abs(P - cen), axis=1)
        ridx = 4.6 - d * 2.2
        rage = rgba(pick(rage_pal, ridx), 255.0)
        return _out(tx, col, rage=rage)

    return paint


def solid(pal, idx=2.5, seed=5, grain=1.0, bev=0.6, outline=0.12, glow=None, glow_alpha=255.0, rage=None):
    """A plain material with a little noise and bevel. glow: a palette for the glow texture (same texels)."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = idx + (noise.fbm(P, 2.0, 2, seed) - 0.5) * 1.6 * grain + (_speck(P, seed) - 0.5) * 0.6 * grain
        k = k + bevel(P, N, cen, 1.0) * bev + _face_shade(N) * 0.6
        col = pick(pal, k)
        col = np.where((tx.edge < 0.5)[:, None], col * (1.0 - outline), col)
        g = None
        if glow is not None:
            g = rgba(pick(glow, k + 0.5), glow_alpha)
        r = None
        if rage is not None:
            r = rgba(pick(rage, k + 1.0), 255.0)
        return _out(tx, col, glow=g, rage=r)

    return paint


def metal(pal, seed=7, shine=1.0, sparkle=0.02):
    """Polished metal: strong bevel highlights, a bright glint here and there."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = 2.6 + bevel(P, N, cen, 1.0) * 1.6 * shine + (noise.fbm(P, 3.0, 2, seed) - 0.5) * 1.0 + _face_shade(N)
        sp = _speck(P, seed + 1) < sparkle
        k = np.where(sp, len(pal) - 1.0, k)
        col = pick(pal, k)
        col = np.where((tx.edge < 0.5)[:, None], col * 0.82, col)
        return _out(tx, col)

    return paint


def rust(seed=11):
    """Rusty iron: dark iron with orange-brown rust blooms."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = 2.4 + bevel(P, N, cen, 1.0) * 0.9 + (noise.fbm(P, 2.5, 2, seed) - 0.5) * 1.4 + _face_shade(N) * 0.6
        rn = noise.fbm(noise.warp(P, 1.5, 6.0, seed + 2), 3.5, 3, seed + 3)
        col = np.where((rn > 0.5)[:, None], pick(RUST, k + (rn - 0.5) * 4.0), pick(IRON, k))
        col = np.where((tx.edge < 0.5)[:, None], col * 0.85, col)
        return _out(tx, col)

    return paint


def gem(pal=GREEN_GLOW, seed=13, glow=True, core=True):
    """A glowing gem or orb: brighter towards its middle, a few facet lines; full-bright in the glow texture."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        size = _sizes(tx)
        d = np.linalg.norm((P - cen) / np.maximum(size / 2.0, 0.5), axis=1)
        k = 4.4 - d * 2.2 + bevel(P, N, cen, 1.0) * 0.8 + (noise.fbm(P, 1.5, 2, seed) - 0.5) * 1.0
        facet = (np.abs(P[:, 0] - cen[:, 0]) < 0.5) | (np.abs(P[:, 1] - cen[:, 1]) < 0.5)
        k = np.where(facet & (d > 0.4), k + 0.6, k)
        col = pick(pal, k - 1.0)
        g = rgba(pick(pal, k), 255.0) if glow else None
        return _out(tx, col, glow=g)

    return paint


def flame(seed=17):
    """Candle flames: green plague fire, full-bright."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = 2.5 + (P[:, 1] - cen[:, 1]) * -0.9 + (_speck(P, seed) - 0.5) * 0.8
        col = pick(GREEN_FLAME, k)
        return _out(tx, col, glow=rgba(pick(GREEN_FLAME, k + 0.6), 255.0))

    return paint


def cloth(pal, seed=19, lining=None, folds=1.0, ragged=0.0, holes=0.0, trim=None, trim_pal=None, trim_width=2.0,
          split=0.0, hem_tags=None, ermine_spots=True, pattern=None, pattern_pal=None):
    """
    Capes and cloth.
      lining:  palette for the side facing his back (inner)
      folds:   strength of the vertical folds
      ragged:  how jagged the bottom hem of the last panel is (units)
      holes:   share of moth holes
      trim:    "gold" or "ermine": a band along the panels' side edges and the bottom hem (trim_width units)
      split:   half-width (units) of a notch cut up the middle of the last panel (a split back)
      hem_tags:the tags whose bottom edge is the hem (default: the last cape panel and the mantle's none)
      pattern: "diamonds": a faint woven diamond pattern
    """

    def paint(tx):
        P, N = tx.P, tx.N
        tags = _tags(tx)
        cen = _centres(tx)
        size = _sizes(tx)
        org = _origins(tx)
        f = np.sin(P[:, 0] * 0.9 + (noise.fbm(P, 5.0, 2, seed) - 0.5) * 5.0) * folds
        weave = ((np.floor(P[:, 0]) + np.floor(P[:, 1])) % 2) * 0.25
        k = 2.4 + f * 0.9 + weave + (_speck(P, seed + 1) - 0.5) * 0.5 + _face_shade(N) * 0.5
        if pattern == "diamonds":
            dm = (np.abs((P[:, 0] % 6.0) - 3.0) + np.abs((P[:, 1] % 6.0) - 3.0)) < 0.8
            k = np.where(dm, k + 0.9, k)
        col = pick(pal, k)
        inner = N[:, 2] < -0.5
        if lining is not None:
            col = np.where((inner & np.char.startswith(tags.astype(str), "cape"))[:, None], pick(lining, k - 0.3), col)
        alpha = np.full(tx.n, 255.0)
        hem = np.zeros(tx.n, dtype=bool)
        last = hem_tags if hem_tags is not None else ()
        is_hem_box = np.isin(tags, last)
        bottom = org[:, 1]
        if trim is not None:
            tp = trim_pal if trim_pal is not None else (GOLD if trim == "gold" else ERMINE)
            cape_part = np.char.startswith(tags.astype(str), "cape")
            near_side = (np.abs(P[:, 0] - cen[:, 0]) > size[:, 0] / 2.0 - trim_width) & cape_part
            near_hem = is_hem_box & (P[:, 1] < bottom + trim_width + 0.01)
            tm = (near_side | near_hem) & (np.abs(N[:, 2]) > 0.5)
            tk = 2.8 + bevel(P, N, cen, 1.0) * 0.5 + (_speck(P, seed + 5) - 0.5) * 0.6
            tcol = pick(tp, tk)
            if trim == "ermine" and ermine_spots:
                spot = ((np.floor(P[:, 0]) % 4 == 1) & (np.floor(P[:, 1] + 0.5 * (np.floor(P[:, 0] / 4.0) % 2)) % 3 == 0))
                tcol = np.where(spot[:, None], PATCH[1], tcol)
            col = np.where(tm[:, None], tcol, col)
        if ragged > 0:
            jag = (noise.fbm(P * np.array([1.0, 0.0, 1.0]), 1.6, 2, seed + 7) - 0.3) * ragged * 1.6
            cut = is_hem_box & (P[:, 1] < bottom + jag)
            alpha = np.where(cut, 0.0, alpha)
        if holes > 0:
            id1, _i2, border, f1, _c = noise.voronoi(P, 3.0, seed + 9)
            hm = (noise.hash_ids(id1, 0, seed + 10) < holes) & (f1 < 0.9) & np.char.startswith(tags.astype(str), "cape")
            alpha = np.where(hm, 0.0, alpha)
            ring_ = (noise.hash_ids(id1, 0, seed + 10) < holes) & (f1 >= 0.9) & (f1 < 1.5) & np.char.startswith(tags.astype(str), "cape")
            col = np.where(ring_[:, None], col * 0.7, col)
        if split > 0:
            frac = np.clip((bottom + size[:, 1] - P[:, 1]) / np.maximum(size[:, 1], 1.0), 0.0, 1.0)
            sm = is_hem_box & (np.abs(P[:, 0] - cen[:, 0]) < split * frac ** 0.7)
            alpha = np.where(sm, 0.0, alpha)
        col = np.where((tx.edge < 0.5)[:, None] & ~hem[:, None], col * 0.9, col)
        return _out(tx, col, alpha)

    return paint


def wood(pal=WOOD, seed=23, knots=0.12, holes_every=0.0):
    """Wood: grain along the length (y in the bind pose), the odd knot. holes_every: finger holes (the pipe)."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        g = noise.fbm(P * np.array([1.0, 0.12, 1.0]), 1.4, 2, seed)
        k = 2.4 + (g - 0.5) * 3.0 + bevel(P, N, cen, 1.0) * 0.5 + _face_shade(N) * 0.5
        id1, _i2, _b, f1, _c = noise.voronoi(P * np.array([1.0, 0.5, 1.0]), 5.0, seed + 1)
        km = (noise.hash_ids(id1, 0, seed + 2) < knots) & (f1 < 0.9)
        k = np.where(km, 0.6, k)
        col = pick(pal, k)
        if holes_every > 0:
            front = N[:, 2] < -0.5
            hm = front & (np.abs(P[:, 0] - cen[:, 0]) < 0.6) & ((np.floor(P[:, 1]) % holes_every) == 0) & (P[:, 1] > cen[:, 1] - 6)
            col = np.where(hm[:, None], pal[0] * 0.5, col)
        col = np.where((tx.edge < 0.5)[:, None], col * 0.85, col)
        return _out(tx, col)

    return paint


def bone(pal=BONE, seed=29):
    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = 3.0 + bevel(P, N, cen, 1.0) * 0.8 + (noise.fbm(P, 2.0, 2, seed) - 0.5) * 1.4 + _face_shade(N) * 0.6
        id1, id2, border, _f, _c = noise.voronoi(P, 4.0, seed + 1)
        cr = (border < 0.25) & (noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 2) < 0.25)
        k = np.where(cr, 0.8, k)
        col = pick(pal, k)
        col = np.where((tx.edge < 0.5)[:, None], col * 0.86, col)
        return _out(tx, col)

    return paint


def wax(seed=31):
    """Candle wax: creamy, drips running down, a darker molten pool on top."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        size = _sizes(tx)
        drip = noise.fbm(P * np.array([1.0, 0.15, 1.0]), 1.2, 2, seed)
        top_rel = (cen[:, 1] + size[:, 1] / 2.0) - P[:, 1]
        k = 3.0 + (drip - 0.5) * 2.2 - np.clip(top_rel / 6.0, 0.0, 1.0) * 0.8 + bevel(P, N, cen, 1.0) * 0.4
        k = np.where(N[:, 1] > 0.5, 1.6, k)
        col = pick(WAX, k)
        return _out(tx, col)

    return paint


def tin(seed=37, label=True):
    """The tin can: dull tin with a faded red-and-cream label round its middle."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        tags = _tags(tx)
        k = 2.6 + bevel(P, N, cen, 1.0) * 1.1 + (noise.fbm(P, 2.0, 2, seed) - 0.5) * 1.0 + _face_shade(N)
        col = pick(TIN, k)
        if label:
            band = (tags == "band") & (P[:, 1] > cen[:, 1] - 1.0) & (P[:, 1] < cen[:, 1] + 1.0) & (np.abs(N[:, 1]) < 0.5)
            stripe = (np.floor(P[:, 0] + P[:, 2]) % 3) == 0
            lab = np.where(stripe[:, None], rgb("#d8cdb0"), rgb("#9a2a24"))
            fade = (noise.fbm(P, 2.0, 2, seed + 3) - 0.5) * 60.0
            col = np.where(band[:, None], np.clip(lab + fade[:, None], 0, 255), col)
        rn = noise.fbm(P, 2.0, 2, seed + 5) > 0.68
        col = np.where(rn[:, None], pick(RUST, k), col)
        col = np.where((tx.edge < 0.5)[:, None], col * 0.84, col)
        return _out(tx, col)

    return paint


def censer(seed=41):
    """The censer: black iron with slits that glow green from the fire inside."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = 2.2 + bevel(P, N, cen, 1.0) * 1.0 + (noise.fbm(P, 2.0, 2, seed) - 0.5) * 0.8
        col = pick(IRON, k)
        side = np.abs(N[:, 1]) < 0.5
        slit = side & ((np.floor(P[:, 0] + P[:, 2]) % 2) == 0) & (np.abs(P[:, 1] - cen[:, 1]) < 1.2)
        col = np.where(slit[:, None], pick(GREEN_GLOW, np.full(tx.n, 2.0)), col)
        g = np.zeros((tx.n, 4))
        g[slit] = rgba(pick(GREEN_GLOW, np.full(int(slit.sum()), 4.5)), 255.0)
        return _out(tx, col, glow=g)

    return paint


def chain(pal=CHAIN, seed=43):
    def paint(tx):
        P, N = tx.P, tx.N
        link = (np.floor(P[:, 1]) % 2) == 0
        k = np.where(link, 3.2, 1.6) + (_speck(P, seed) - 0.5) * 0.6
        return _out(tx, pick(pal, k))

    return paint


def knot(seed=47):
    """A knot of rat tails: twisted pink tails in every direction."""

    def paint(tx):
        P, N = tx.P, tx.N
        a = np.sin(P[:, 0] * 1.3 + P[:, 1] * 0.7) + np.sin(P[:, 2] * 1.1 - P[:, 1] * 0.9) * 0.8
        band = np.sin(a * 2.4 + noise.fbm(P, 2.0, 2, seed) * 3.0)
        k = 2.6 + band * 1.1 + (_speck(P, seed) - 0.5) * 0.6
        crease = np.abs(band) < 0.2
        k = np.where(crease, 0.8, k)
        return _out(tx, pick(TAIL, k))

    return paint


def tail(pal=TAIL, seed=53, rings=1.3, scabby=0.0):
    """The tail: ringed pink-grey skin (rings across the length, which runs along y in the bind pose)."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        ring_ = (np.floor(P[:, 1] * rings) % 2) == 0
        k = 2.8 + np.where(ring_, 0.45, -0.25) + (_speck(P, seed) - 0.5) * 0.7 + bevel(P, N, cen, 1.0) * 0.5
        k = k + _face_shade(N) * 0.5
        if scabby > 0:
            sm = noise.fbm(P, 2.0, 2, seed + 1) > 1.0 - scabby
            k = np.where(sm, 0.6, k)
        return _out(tx, pick(pal, k))

    return paint


def hole(seed=59):
    """The dirt hole: black in the middle, brown towards the rim."""

    def paint(tx):
        P = tx.P
        r = np.sqrt(P[:, 0] ** 2 + (P[:, 2] + 6.0) ** 2) / 6.5
        k = r * 3.6 + (noise.fbm(P, 1.5, 2, seed) - 0.5) * 1.2
        return _out(tx, pick(HOLE, k))

    return paint


def dirt(seed=61):
    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        k = 2.2 + bevel(P, N, cen, 1.0) * 0.9 + (_speck(P, seed) - 0.5) * 1.4 + _face_shade(N)
        return _out(tx, pick(DIRT, k))

    return paint


def spoon(seed=67):
    """A spoon's bowl: silver, rounded off (cut out at the corners)."""

    def paint(tx):
        P, N = tx.P, tx.N
        cen = _centres(tx)
        size = _sizes(tx)
        u = (P[:, 0] - cen[:, 0]) / (size[:, 0] / 2.0)
        v = (P[:, 1] - cen[:, 1]) / (size[:, 1] / 2.0)
        alpha = np.where(u * u + v * v > 1.25, 0.0, 255.0)
        k = 2.8 + bevel(P, N, cen, 1.0) * 1.4 + _face_shade(N)
        return _out(tx, pick(SILVER, k), alpha)

    return paint


def xmark(color="#141012"):
    c = rgb(color)

    def paint(tx):
        col = np.repeat(c[None, :], tx.n, axis=0)
        sp = (_speck(tx.P, 71) - 0.5) * 18.0
        return _out(tx, np.clip(col + sp[:, None], 0, 255))

    return paint


def strands(pal, seed=73):
    """Hair: whiskers, brows, beard; lighter at the tips."""

    def paint(tx):
        P = tx.P
        k = 2.5 + (_speck(P, seed) - 0.5) * 1.2 + (noise.fbm(P, 1.5, 1, seed) - 0.5) * 1.0
        return _out(tx, pick(pal, k))

    return paint


def standard(look):
    """The materials every design shares. `look` gives the design's palettes."""
    fp = look["fur"]
    return {
        "skin": solid(look.get("skin", SKIN), 3.0, seed=81, grain=0.7, bev=0.5),
        "claw": solid(CLAW, 2.4, seed=82, grain=0.6),
        "tooth": solid(TOOTH, 3.0, seed=83, grain=0.4, bev=0.9),
        "nose": solid(NOSE, 2.8, seed=84, grain=0.6, bev=0.8),
        "tongue": solid(TONGUE, 2.4, seed=85, grain=0.5, bev=0.6),
        "eye": eye(look.get("eye_pal")),
        "lid": fur(fp, seed=86, strand=1.2, outline=0.0),
        "xmark": xmark(look.get("xmark", "#141012")),
        "whisker": strands(look.get("whisker_pal", palette("#1a1716", "#2a2523", "#3b3431", "#4e4541"))),
        "white_hair": strands(palette("#9f9c95", "#bdbab3", "#d7d4cd", "#eceae4")),
        "tail": tail(look.get("tail_pal", TAIL), scabby=look.get("tail_scabby", 0.0)),
        "ear": ear(look.get("ear_fur", fp), inner=look.get("ear_inner", SKIN), notch=look.get("notch")),
        "hole": hole(),
        "dirt": dirt(),
        "gold": metal(GOLD, seed=91),
        "gold_dark": metal(GOLD_DARK, seed=92),
    }
