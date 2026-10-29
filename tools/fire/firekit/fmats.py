"""
The fire servant candidates' materials: lava orange and obsidian (the Fire Necromancer's colours, BOSS_GUIDE.md),
embers, ash, charred wood, firebrick, clay, bronze, sulfur, smoke-dark glass and see-through flame. Never the Dross's
electric blue.

Each factory returns a painter like the Rimefall kit's (tools/rimefall/rimekit/mats.py): texels -> colour, alpha and
glow. Alpha 255 is solid, 0 a hole, anything between see-through (the mod's "clear" pass: flame, glass, heat haze);
glow is drawn full-bright on top (lava, embers, eyes). The Rimefall kit's own materials are reused where they fit
(with their frost turned off: nothing here is frozen).
"""
import math

import numpy as np

from kit import noise
from kit.paint import bevel, palette, pick, rgba
from rimekit import mats as M
from rimekit.mats import _box_frame, _centres, _factory, _out, _outline, _shade, _sizes, _speck

# ---------------------------------------------------------------- palettes (dark to light)

OBSIDIAN = palette("#07050b", "#0f0b17", "#1a1326", "#271c38", "#3a2a52", "#5a4478")
CHAR = palette("#0b0a09", "#141210", "#1d1a17", "#282420", "#36312b", "#48423a")
ASH = palette("#3b3836", "#524e4c", "#6a6663", "#84807c", "#a19c97", "#c3beb8")
ASH_DARK = palette("#1d1b1a", "#292625", "#363231", "#45403e", "#57514e", "#6d6662")
LAVA = palette("#5a0f02", "#8f1d04", "#c63a07", "#ef6a10", "#ff9a2a", "#ffc75a", "#fff0b0")
EMBER = palette("#a82604", "#d8460a", "#ff7016", "#ff9a32", "#ffc66a", "#ffe8b0")
WHITE_HOT = palette("#ff8a30", "#ffad55", "#ffcf8a", "#ffe9c4", "#fff8ec", "#ffffff")
MAGMA_CRUST = palette("#100705", "#1b0c07", "#29130b", "#3a1c11", "#4f2718", "#683522")
BASALT = palette("#18181c", "#232328", "#2f2f35", "#3c3c43", "#4b4b53", "#5f5f69")
BRONZE = palette("#2c1a0c", "#4a2e14", "#6c4620", "#8e5e2a", "#b47c3a", "#d8a45a")
BRASS = palette("#3c2c0e", "#5e4716", "#82641f", "#a8822a", "#c8a23c", "#e6c766")
VERDIGRIS = palette("#1d3a33", "#28503f", "#356a51", "#4a8666", "#68a37f", "#93c2a0")
IRON = palette("#0f1013", "#18191d", "#232429", "#2f3137", "#3e4048", "#53565f")
RUST = palette("#2a1209", "#43200f", "#5e2f16", "#7b401e", "#985428", "#b46a36")
SULFUR = palette("#4f3f06", "#73600b", "#9c8410", "#c5ab1b", "#e2cd46", "#f6e98a")
SULFUR_PALE = palette("#7a6a2a", "#9a8836", "#b9a648", "#d4c262", "#e8da84", "#f6efb4")
CLAY = palette("#45200f", "#5e2f18", "#7a4123", "#965431", "#ae6a42", "#c68457")
FIREBRICK = palette("#38160e", "#4f2015", "#682b1d", "#823826", "#9c4832", "#b45d42")
MORTAR = palette("#2a2522", "#3a3430", "#4c4540", "#605853", "#776f69")
LEATHER = palette("#170f09", "#22170e", "#2f2014", "#3e2a1b", "#4f3723", "#63462e")
SOOT_CLOTH = palette("#09090b", "#101013", "#18181c", "#212126", "#2c2c33", "#3b3b44")
CRAPE = palette("#0a0809", "#120e10", "#1a1517", "#241d20", "#30272b", "#3f3438")
BONE_CHAR = palette("#3f362e", "#5a4f43", "#776a5b", "#958673", "#b2a38c", "#cdbfa6")
GREY_SKIN = palette("#24211f", "#33302c", "#45413b", "#58534c", "#6d675f", "#857e74")
CHITIN_RED = palette("#160604", "#240a06", "#36100a", "#4b170e", "#632115", "#7e2e1d")
SCARAB = palette("#0b0805", "#150f08", "#21180c", "#302312", "#433119", "#5c4423")
TOAD = palette("#16100c", "#211812", "#2e2219", "#3d2e22", "#4f3c2d", "#664e3b")
FEATHER = palette("#0a0909", "#121010", "#1b1817", "#262220", "#332e2b", "#45403b")
RED_SKIN = palette("#3e0f0b", "#58160f", "#731f15", "#8e2b1d", "#a83a27", "#c25034")
MOTH = palette("#2a221c", "#3a3027", "#4d4034", "#625244", "#7a6756", "#94806c")
MOTH_PALE = palette("#6e655c", "#887e74", "#a2988c", "#bbb1a5", "#d2c9bd", "#e7e0d6")
WICKER = palette("#1f150d", "#2d1f13", "#3d2a1a", "#4f3822", "#62472c", "#785837")
RIBBON = palette("#3a0806", "#560c09", "#73120d", "#901a12", "#ab2618", "#c43a24")
GOLD = palette("#4a3010", "#6e4a16", "#94661e", "#ba8428", "#d8a63c", "#f0cc66")
STAR_IRON = palette("#08090f", "#11131d", "#1b1e2c", "#262a3c", "#353a50", "#4b5168")
FLAME = palette("#a82a08", "#d8480c", "#ff7418", "#ff9e34", "#ffc866", "#ffeab0")
FLAME_DEEP = palette("#6a1004", "#9a1c06", "#c8300a", "#ee5010", "#ff7a24", "#ffa848")
GLASS_AMBER = palette("#5a3006", "#86500c", "#b47418", "#d89a2c", "#f0c25a", "#fde6a4")
GLASS_GREEN = palette("#123a24", "#1a5232", "#246c42", "#348a56", "#4ea870", "#80c896")
GLASS_RED = palette("#4a0a08", "#6e120c", "#941c12", "#b82c1c", "#d64a30", "#ee7a58")
GLASS_SMOKE = palette("#1c1614", "#2a221e", "#3a302a", "#4c4038", "#625448", "#7c6c5e")
HEAT = palette("#7a3a1a", "#a8552a", "#d07a40", "#eea060", "#ffc490", "#ffe6c8")
MOUTH = M.MOUTH
EYE_EMBER = palette("#b02404", "#e04a0a", "#ff7a1c", "#ffaa44", "#ffd890", "#fff4dc")
EYE_GOLD = palette("#8a5a08", "#b8800c", "#e0a818", "#f8cc40", "#ffe68a", "#fff8d8")
EYE_GOLD_DARK = palette("#2a1604", "#3e2206", "#542e08", "#6a3a0c", "#804812")


def _noglow(n):
    return np.zeros((n, 4))


# ---------------------------------------------------------------- reused Rimefall materials, unfrozen


def solid(pal, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.solid(pal, **kw)


def fur(pal, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.fur(pal, **kw)


def skin(pal, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.skin(pal, **kw)


def scales(pal, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.scales(pal, **kw)


def chitin(pal, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.chitin(pal, **kw)


def bone(pal=BONE_CHAR, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.bone(pal, **kw)


def metal(pal=IRON, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.metal(pal, **kw)


def wood(pal=CHAR, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.wood(pal, **kw)


def cloth(pal, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.cloth(pal, **kw)


def feathers(pal=FEATHER, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.feathers(pal, **kw)


def shag(pal, **kw):
    kw.setdefault("frost_amount", 0.0)
    return M.shag(pal, **kw)


def glass(pal=GLASS_AMBER, alpha=(110, 200), **kw):
    """See-through glass (the Rimefall kit's ice in glass colours, no frost)."""
    kw.setdefault("frost_amount", 0.0)
    kw.setdefault("cracks", 0.15)
    kw.setdefault("bubbles", 0.02)
    return M.ice(pal, alpha=alpha, **kw)


def eye(pal=EYE_EMBER, **kw):
    return M.eye(pal, **kw)


def eye_disc(**kw):
    return M.eye_disc(**kw)


def glowing(pal=EMBER, **kw):
    return M.glowing(pal, **kw)


def mouth(seed=39):
    return M.mouth(seed)


def tiled(side, end=None, **kw):
    return M.tiled(side, end, **kw)


# ---------------------------------------------------------------- fire materials


@_factory(True)
def lava(pal=LAVA, seed=101, flow=1.0, crust=0.0, crust_pal=MAGMA_CRUST, bright=1.0):
    """
    Molten rock: glowing all over, darker and redder in the streaks between the bright channels, which run downhill
    (along world y); `crust` (0..1) of it skinned over in dark cooled plates that don't glow.
    """

    def paint(tx):
        cen = _centres(tx)
        q = noise.warp(tx.Pw, 2.0, 9.0, seed)
        streak = noise.fbm(q * np.array([1.0, 0.18 / max(flow, 0.05), 1.0]), 3.0, 3, seed + 1)
        k = 2.6 + (streak - 0.5) * 4.0 + bevel(tx.P, tx.N, cen, 1.0) * 0.4
        k = k + (_speck(tx.Pw, seed + 2) - 0.5) * 0.6
        col = pick(pal, k)
        g = rgba(pick(pal, k + 0.4), 255.0 * bright)
        if crust > 0:
            c = noise.fbm(noise.warp(tx.Pw, 1.5, 6.0, seed + 3), 5.0, 2, seed + 4)
            cm = c < crust * 0.75
            edge = cm & (c > crust * 0.75 - 0.05)
            col = np.where(cm[:, None], pick(crust_pal, 2.4 + (c - 0.3) * 4.0 + _shade(tx)), col)
            col = np.where(edge[:, None], pick(pal, np.full(tx.n, 1.0)), col)
            g[cm & ~edge] = 0.0
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def crust(pal=MAGMA_CRUST, glow_pal=EMBER, seed=103, cell=7.0, cracks=0.55, width=0.55, hot=1.0, outline=0.1,
          ash_top=0.0):
    """
    Cooled magma or scorched rock: dark plates (Voronoi cells, each its own shade), the cracks between them glowing
    (`cracks`: the share of borders that glow, `width` how wide in units); a dusting of ash on what faces up.
    """

    def paint(tx):
        cen = _centres(tx)
        q = noise.warp(tx.Pw, 1.6, 8.0, seed)
        id1, id2, border, _f, c1 = noise.voronoi(q, cell, seed + 1, jitter=0.8)
        shade = noise.hash_ids(id1, 0, seed + 2) - 0.5
        k = 2.4 + shade * 1.6 + bevel(tx.Pw, tx.Nw, c1, 1.0) * 0.6 + bevel(tx.P, tx.N, cen, 1.0) * 0.3 + _shade(tx)
        k = k + (_speck(tx.Pw, seed + 3) - 0.5) * 0.5
        pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 4) < cracks
        seam = border < width
        k = np.where(seam & ~pair, k - 1.4, k)
        col = pick(pal, k)
        col = _outline(tx, col, outline)
        hotm = seam & pair
        g = np.zeros((tx.n, 4))
        if hot > 0:
            gk = 3.4 - border / max(width, 0.1) * 1.4 + (noise.fbm(tx.Pw, 3.0, 1, seed + 5) - 0.5)
            col = np.where(hotm[:, None], pick(glow_pal, gk - 1.0), col)
            g[hotm] = rgba(pick(glow_pal, gk[hotm]), 255.0 * hot)
        if ash_top > 0:
            up = tx.Nw[:, 1] > 0.5
            a = noise.fbm(tx.Pw, 2.5, 2, seed + 6)
            am = up & (a < ash_top) & ~hotm
            col = np.where(am[:, None], pick(ASH, 2.6 + (a - 0.4) * 3.0), col)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def obsidian(pal=OBSIDIAN, seed=105, gloss=1.6, veins=0.0, vein_pal=LAVA, outline=0.14, cell=6.0):
    """
    Obsidian: glassy purple-black with conchoidal facets (cells), sharp pale-violet highlights towards the light;
    `veins` of glowing lava running through it.
    """

    def paint(tx):
        cen = _centres(tx)
        id1, _id2, border, _f, c1 = noise.voronoi(tx.Pw, cell, seed, jitter=0.9)
        facet = noise.hash_ids(id1, 0, seed + 1) - 0.5
        b = bevel(tx.Pw, tx.Nw, c1, 1.0) * 0.7 + bevel(tx.P, tx.N, cen, 1.0) * 0.6
        k = 1.8 + facet * 1.4 + b * gloss + _shade(tx) * 0.6
        hl = (b > 0.62) & (_speck(tx.Pw, seed + 2, 1.5) < 0.4)
        k = np.where(hl, len(pal) - 0.7, k)
        k = np.where(border < 0.35, k - 0.8, k)
        col = _outline(tx, pick(pal, k), outline)
        g = np.zeros((tx.n, 4))
        if veins > 0:
            v = noise.fbm(noise.warp(tx.Pw, 2.5, 10.0, seed + 3), 9.0, 3, seed + 4)
            vm = np.abs(v - 0.5) < 0.009 * (1.0 + veins * 1.5)
            vk = 3.6 - np.abs(v - 0.5) * 120.0
            col = np.where(vm[:, None], pick(vein_pal, vk - 1.0), col)
            g[vm] = rgba(pick(vein_pal, vk[vm]), 255.0)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def magma_obsidian(pal=OBSIDIAN, seed=171, gloss=1.6, cracks=0.5, width=0.6, cell=6.5, pools=0.0, glow_pal=EMBER,
                   facet=4.0, veins=0.4):
    """
    The Emberdactyl's egg (the user's pick, October 27, 2026: "the obsidian egg with the magma aspect a little more"):
    glassy obsidian (its facets and highlights) broken into plates like cooled magma, `cracks` (0..1) of the plates'
    borders glowing, `width` units wide; `pools` of the plates molten through (glowing dark red, not glassy); thin lava
    `veins` running through the obsidian between them.
    """

    def paint(tx):
        cen = _centres(tx)
        # the obsidian: facets and highlights
        fid, _f2, fborder, _ff, fc = noise.voronoi(tx.Pw, facet, seed, jitter=0.9)
        b = bevel(tx.Pw, tx.Nw, fc, 1.0) * 0.7 + bevel(tx.P, tx.N, cen, 1.0) * 0.6
        k = 1.8 + (noise.hash_ids(fid, 0, seed + 1) - 0.5) * 1.4 + b * gloss + _shade(tx) * 0.6
        hl = (b > 0.62) & (_speck(tx.Pw, seed + 2, 1.5) < 0.4)
        k = np.where(hl, len(pal) - 0.7, k)
        k = np.where(fborder < 0.3, k - 0.8, k)
        col = _outline(tx, pick(pal, k), 0.12)
        g = np.zeros((tx.n, 4))
        # the plates and their glowing cracks
        q = noise.warp(tx.Pw, 1.4, 9.0, seed + 3)
        id1, id2, border, _f, _c1 = noise.voronoi(q, cell, seed + 4, jitter=0.85)
        pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 5) < cracks
        seam = border < width
        hot = seam & pair
        dark = seam & ~pair
        col = np.where(dark[:, None], col * 0.55, col)
        gk = 3.5 - border / max(width, 0.1) * 1.5 + (noise.fbm(tx.Pw, 3.0, 1, seed + 6) - 0.5)
        col = np.where(hot[:, None], pick(glow_pal, gk - 1.0), col)
        g[hot] = rgba(pick(glow_pal, gk[hot]), 255.0)
        if pools > 0:
            pool = (noise.hash_ids(id1, 0, seed + 7) < pools) & ~seam
            pk = 2.2 + (noise.fbm(tx.Pw, 2.0, 2, seed + 8) - 0.5) * 2.0 - border * 0.15
            col = np.where(pool[:, None], pick(MAGMA_CRUST, pk + 1.2), col)
            g[pool] = rgba(pick(glow_pal, pk[pool]), 170.0)
        if veins > 0:
            v = noise.fbm(noise.warp(tx.Pw, 2.5, 10.0, seed + 9), 9.0, 3, seed + 10)
            vm = (np.abs(v - 0.5) < 0.009 * (1.0 + veins * 1.5)) & ~seam
            vk = 3.6 - np.abs(v - 0.5) * 120.0
            col = np.where(vm[:, None], pick(LAVA, vk - 1.0), col)
            g[vm] = rgba(pick(LAVA, vk[vm]), 255.0)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def charred(pal=CHAR, seed=107, cracks=0.35, glow_pal=EMBER, check=3.0, grain=True, outline=0.1, ash_top=0.25,
            hot=1.0):
    """
    Charred wood: black, its surface broken into the blocky checks burnt wood gets (rows of little cells along each
    box), the grain running along the box; some checks' cracks still glowing with embers (`cracks`), grey ash on top.
    """

    def paint(tx):
        cen = _centres(tx)
        q, size = _box_frame(tx)
        along = q[:, 1]
        across = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0])
        cw = check * 1.6                      # a check's width across the grain and its length along it (units)
        ch = check * 2.1
        # Wandering, uneven checks: the rows wobble, and each row has its own check width.
        along = along + (noise.fbm(tx.P * np.array([1.0, 0.3, 1.0]), 6.0, 2, seed + 9) - 0.5) * ch * 0.9
        row = np.floor(along / ch)
        cw = cw * (0.7 + 0.7 * noise.hash_float(row, tx.cube_index.astype(float), 3.0, seed + 10))
        off = noise.hash_float(row, tx.cube_index.astype(float), 5.0, seed + 11) * cw
        across = across + (noise.fbm(tx.P, 4.0, 2, seed + 12) - 0.5) * 2.0
        colid = np.floor((across + off) / cw)
        cell = noise.hash_float(row, colid, tx.cube_index.astype(float), seed)
        du = (across + off) - colid * cw
        dv = along - row * ch
        # One texel of crack on each check's lower and left edge (so a crack is one texel wide).
        seam = (du < 1.0) | (dv < 1.0)
        end = np.abs(tx.N[:, 1]) > 0.5
        seam = seam & ~end
        gr = noise.fbm(tx.P * np.array([1.0, 0.12, 1.0]), 1.4, 2, seed + 1) if grain else 0.5
        # Each check bulges a little: lit on its upper side, shaded on its lower one.
        relief = np.where(dv > ch * 0.62, 0.45, np.where(dv < ch * 0.3, -0.35, 0.0))
        k = 2.7 + (cell - 0.5) * 1.3 + (gr - 0.5) * 1.2 + relief + bevel(tx.P, tx.N, cen, 1.0) * 0.4 + _shade(tx)
        k = np.where(seam, k - 1.9, k)
        # The log's end: rings.
        r = np.sqrt((q[:, 0] - size[:, 0] / 2.0) ** 2 + (q[:, 2] - size[:, 2] / 2.0) ** 2)
        k = np.where(end, 2.2 + np.sin(r * 2.2) * 0.6 + (_speck(tx.P, seed + 2) - 0.5) * 0.8, k)
        col = _outline(tx, pick(pal, k), outline)
        g = np.zeros((tx.n, 4))
        if cracks > 0 and hot > 0:
            # Glowing in patches (where the wood still burns inside), only in the cracks there.
            patch = noise.fbm(tx.Pw, 13.0, 2, seed + 3)
            limit = 0.18 + cracks * 0.32
            hm = seam & (patch < limit) & (_speck(tx.P, seed + 4) < 0.85)
            gk = 2.2 + (limit - patch) * 10.0 + (_speck(tx.Pw, seed + 5) - 0.5) * 0.8
            col = np.where(hm[:, None], pick(glow_pal, gk - 1.0), col)
            g[hm] = rgba(pick(glow_pal, gk[hm]), 255.0 * hot)
            ember_end = end & (noise.fbm(tx.Pw, 2.5, 1, seed + 6) < cracks * 0.6)
            col = np.where(ember_end[:, None], pick(glow_pal, np.full(tx.n, 1.6)), col)
            g[ember_end] = rgba(pick(glow_pal, np.full(int(ember_end.sum()), 2.4)), 255.0 * hot)
        if ash_top > 0:
            up = tx.Nw[:, 1] > 0.55
            a = noise.fbm(tx.Pw, 2.0, 2, seed + 7)
            am = up & (a < ash_top + 0.15) & (g[:, 3] < 1.0)
            col = np.where(am[:, None], pick(ASH, 2.4 + (a - 0.3) * 4.0 + _speck(tx.Pw, seed + 8) * 0.6), col)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def ash(pal=ASH, seed=109, outline=0.06, embers=0.0):
    """Ash: soft, dusty grey, the odd speck of black char, a few live embers (`embers`)."""

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 3.0, 3, seed)
        k = 2.8 + (n - 0.5) * 2.0 + bevel(tx.P, tx.N, cen, 1.0) * 0.4 + _shade(tx) * 1.0
        sp = _speck(tx.Pw, seed + 1)
        k = np.where(sp < 0.05, 0.5, k)
        col = _outline(tx, pick(pal, k), outline)
        g = np.zeros((tx.n, 4))
        if embers > 0:
            em = _speck(tx.Pw, seed + 2) < embers
            col = np.where(em[:, None], EMBER[2], col)
            g[em] = rgba(pick(EMBER, np.full(int(em.sum()), 3.0)), 255.0)
        return _out(tx, col, None, g)

    return paint


# Animated fire (the user, October 27, 2026: "make his fire animated on his coffin", "make the fire animated again for
# M2B"): tools/fire/mobs.py paints a moving design's textures again at FLAME_FRAMES moments of one loop, each with
# FLAME_TIME set to its share of the loop; the flames' tongues rise FLAME_RISE units over a loop, and the game shows the
# frames in turn. The tongues at time t are the pattern at t risen and, faded in as the loop goes on, the pattern a whole
# loop further on (Perlin's way of looping noise), so the last frame runs straight on into the first.
FLAME_TIME = 0.0
FLAME_RISE = 12.0
FLAME_FRAMES = 8


def _rising(sample, rise=FLAME_RISE):
    """`sample(dy)` (noise read from dy units lower down) at FLAME_TIME of the loop, looping without a seam."""
    t = FLAME_TIME % 1.0
    if t <= 0.0:
        return sample(0.0)
    a = sample(rise * t)
    b = sample(rise * (t - 1.0))
    mix = (1.0 - t) * a + t * b
    # (two noises averaged are flatter than one: stretch it back to one's spread)
    return 0.5 + (mix - 0.5) / math.sqrt((1.0 - t) ** 2 + t * t)


@_factory(False)
def flame(pal=FLAME, seed=111, alpha=(80, 200), glow=235.0, ragged=1.0, core=0.0, outline=0.0, cutout=True):
    """
    Flame: brightest at the bottom of each box (its own -y), tongues licking up, the top edge ragged; it glows.
    `cutout` (the default): solid flame with holes, like Minecraft's own fire block, so it's drawn with the solid parts
    and glows through glass in front of it (a lantern's flame) and never sorts wrongly; otherwise see-through (the
    mod's clear pass), fading out towards its tips. `core`: a share of each box near its middle that burns white-hot.
    """
    if cutout:
        return _flame_cut(pal, seed, glow, ragged, core)

    def paint(tx):
        q, size = _box_frame(tx)
        h = np.clip(q[:, 1] / np.maximum(size[:, 1], 1.0), 0.0, 1.0)               # 0 at the bottom, 1 at the top
        across = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2] / np.maximum(size[:, 2], 1.0),
                          q[:, 0] / np.maximum(size[:, 0], 1.0))
        tongue = _rising(lambda dy: noise.fbm((tx.Pw - np.array([0.0, dy, 0.0])) * np.array([1.0, 0.22, 1.0]), 2.4, 3,
                                              seed))
        k = 4.4 - h * 3.2 + (tongue - 0.5) * 2.4
        mid = 1.0 - np.abs(across - 0.5) * 2.0
        if core > 0:
            k = k + np.clip(mid - (1.0 - core), 0.0, 1.0) * 3.0 * (1.0 - h)
        col = pick(pal, k)
        a = alpha[0] + (alpha[1] - alpha[0]) * np.clip(1.0 - h * 1.1 + (tongue - 0.5) * 0.8, 0.0, 1.0)
        if ragged > 0:
            cut = h > 0.98 - (tongue - 0.15) * 0.75 * ragged
            a = np.where(cut, 0.0, a)
        a = np.where(tx.N[:, 1] > 0.5, np.minimum(a, alpha[0] * 0.6), a)            # (the flat top barely shows)
        g = rgba(np.clip(col * 1.05 + 10.0, 0, 255), glow * np.clip(0.45 + (1.0 - h) * 0.6, 0.0, 1.0))
        g[a < 26] = 0.0
        return _out(tx, col, a, g)

    paint.flame = True
    return paint


def _flame_cut(pal, seed, glow, ragged, core):
    """Cut-out flame (see flame()): tongues of solid fire rising up each box, holes between them near the top."""

    def paint(tx):
        q, size = _box_frame(tx)
        h = np.clip(q[:, 1] / np.maximum(size[:, 1], 1.0), 0.0, 1.0)               # 0 at the bottom, 1 at the top
        across = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2] / np.maximum(size[:, 2], 1.0),
                          q[:, 0] / np.maximum(size[:, 0], 1.0))
        tongue = _rising(lambda dy: noise.fbm(np.stack([tx.Pw[:, 0] + tx.Pw[:, 2], (tx.Pw[:, 1] - dy) * 0.3,
                                                        tx.Pw[:, 2] * 0.0], axis=1), 2.2, 3, seed))
        # The flame's outline: wide at the bottom, narrowing up into licking tongues.
        mid = 1.0 - np.abs(across - 0.5) * 2.0                                       # 1 in the middle, 0 at the edges
        reach = 0.35 + mid * 0.75 + (tongue - 0.5) * 0.9 * ragged
        solid = h < reach
        solid &= ~((h > 0.35) & (_speck(tx.P, seed + 1) < 0.05 + h * 0.15))
        k = 4.6 - h * 3.0 + (tongue - 0.5) * 1.6 + np.clip(mid - 0.6, 0.0, 1.0) * 2.0 * core
        k = np.where(h > reach - 0.12, k - 0.9, k)                                   # the edges a deeper orange
        col = pick(pal, k)
        a = np.where(solid, 255.0, 0.0)
        a = np.where(np.abs(tx.N[:, 1]) > 0.5, 0.0, a)                               # (no flat tops or bottoms)
        g = rgba(np.clip(col * 1.05 + 8.0, 0, 255), glow)
        g[a < 26] = 0.0
        return _out(tx, col, a, g)

    paint.flame = True
    return paint


HEAT_PALE = palette("#d8844c", "#f0ab74", "#ffcc9c", "#ffdfbe", "#ffeedc", "#fff8ee")
HEAT_WARM = palette("#b8501e", "#dc7430", "#f4984a", "#ffb870", "#ffd29c", "#ffe8c8")


@_factory(False)
def heat(pal=HEAT_PALE, seed=113, alpha=(18, 64), glow=150.0, bands=1.0, rim_alpha=120.0):
    """
    Heat haze: barely there, a pale glow with wavering streaks rising up it (the shimmer), the edges of each box
    catching the light brightly (a mirage's outline); it glows, most along its edges and streaks. For the Heat Haze.
    """

    def paint(tx):
        cen = _centres(tx)
        wob = (noise.fbm(tx.Pw * np.array([1.0, 0.25, 1.0]), 4.0, 2, seed) - 0.5) * 7.0
        w = np.sin(tx.Pw[:, 0] * 0.9 + tx.Pw[:, 2] * 0.9 + wob)
        n = noise.fbm(tx.Pw, 3.0, 2, seed + 1)
        streak = w > 0.55
        k = 2.4 + w * 0.6 * bands + (n - 0.5) * 1.4 + bevel(tx.P, tx.N, cen, 1.0) * 0.3
        a = alpha[0] + (alpha[1] - alpha[0]) * np.clip(0.3 + (n - 0.5) * 1.2, 0.0, 1.0)
        a = np.where(streak, a + 26.0 * bands, a)
        k = np.where(streak, k + 1.2, k)
        rim = tx.edge < 0.6
        a = np.where(rim, np.minimum(235.0, a + rim_alpha), a)
        k = np.where(rim, k + 1.6, k)
        col = pick(pal, k)
        g = rgba(np.clip(col * 1.05, 0, 255), glow * np.clip(0.25 + 0.5 * streak + 0.6 * rim + (n - 0.5) * 0.4, 0, 1))
        return _out(tx, col, a, g)

    return paint


@_factory(True)
def brick(pal=FIREBRICK, mortar=MORTAR, seed=115, bw=6.0, bh=3.0, hot_mortar=0.0, soot=0.4, glow_pal=EMBER,
          outline=0.08):
    """
    Firebrick in running bond, laid along each box's own faces (bricks `bw` x `bh` units), each brick its own shade,
    dark mortar between; soot blackening towards the top (`soot`); `hot_mortar`: a share of the mortar glowing.
    """

    def paint(tx):
        q, size = _box_frame(tx)
        top = np.abs(tx.N[:, 1]) > 0.5
        u = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0]) + np.where(np.abs(tx.N[:, 0]) > 0.5, 0.0, 0.37)
        v = np.where(top, q[:, 2], q[:, 1])
        u = np.where(top, q[:, 0], u)
        row = np.floor(v / bh)
        off = (row % 2) * bw * 0.5
        col_i = np.floor((u + off) / bw)
        du = (u + off) - col_i * bw
        dv = v - row * bh
        joint = (du < 0.8) | (dv < 0.8)
        shade = noise.hash_float(row, col_i, tx.cube_index.astype(float), seed) - 0.5
        k = 2.6 + shade * 1.8 + (noise.fbm(tx.Pw, 2.0, 2, seed + 1) - 0.5) * 1.0 + _shade(tx) * 0.7
        k = k + bevel(tx.P, tx.N, _centres(tx), 1.0) * 0.3
        col = pick(pal, k)
        col = np.where(joint[:, None], pick(mortar, 1.6 + shade * 1.4), col)
        if soot > 0:
            s = np.clip((tx.Pw[:, 1] - noise.fbm(tx.Pw, 6.0, 2, seed + 2) * 30.0) / 120.0, 0.0, 1.0) * soot
            col = col * (1.0 - s[:, None] * 0.7)
        col = _outline(tx, col, outline)
        g = np.zeros((tx.n, 4))
        if hot_mortar > 0:
            hm = joint & (noise.fbm(tx.Pw, 5.0, 2, seed + 3) < hot_mortar)
            col = np.where(hm[:, None], pick(glow_pal, np.full(tx.n, 1.6)), col)
            g[hm] = rgba(pick(glow_pal, np.full(int(hm.sum()), 2.8)), 255.0)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def clay(pal=CLAY, seed=117, cracks=0.0, glow_pal=EMBER, crackle=0.25, soot=0.0, outline=0.08):
    """
    Fired clay: smooth, softly mottled, a fine crackle glaze (`crackle`); big glowing cracks (`cracks`); soot round the
    top.
    """

    def paint(tx):
        cen = _centres(tx)
        m = noise.fbm(noise.warp(tx.Pw, 1.2, 9.0, seed), 7.0, 3, seed + 1)
        k = 2.8 + (m - 0.5) * 1.8 + bevel(tx.P, tx.N, cen, 1.0) * 0.6 + _shade(tx) * 0.8
        k = k + (_speck(tx.Pw, seed + 2) - 0.5) * 0.5
        if crackle > 0:
            _a, _b, border, _f, _c = noise.voronoi(tx.Pw, 3.0, seed + 3)
            k = np.where(border < 0.2 * crackle * 2, k - 0.9, k)
        col = pick(pal, k)
        if soot > 0:
            s = np.clip((tx.Pw[:, 1] - noise.fbm(tx.Pw, 5.0, 2, seed + 4) * 24.0) / 100.0, 0.0, 1.0) * soot
            col = col * (1.0 - s[:, None] * 0.75)
        col = _outline(tx, col, outline)
        g = np.zeros((tx.n, 4))
        if cracks > 0:
            id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 2.2, 9.0, seed + 5), 11.0, seed + 6)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 7) < cracks
            cm = pair & (border < 0.6)
            gk = 3.4 - border * 2.0
            col = np.where(cm[:, None], pick(glow_pal, gk - 1.0), col)
            g[cm] = rgba(pick(glow_pal, gk[cm]), 255.0)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def bronze(pal=BRONZE, seed=119, patina=0.3, patina_pal=VERDIGRIS, shine=1.3, outline=0.16, soot=0.0):
    """Cast bronze or brass: warm metal, bright highlights, green patina gathered in patches and on the lower edges."""

    def paint(tx):
        cen = _centres(tx)
        k = 2.6 + bevel(tx.P, tx.N, cen, 1.0) * shine + (noise.fbm(tx.Pw, 4.0, 2, seed) - 0.5) * 1.2 + _shade(tx)
        k = np.where(_speck(tx.Pw, seed + 1) < 0.02, len(pal) - 1.0, k)
        col = pick(pal, k)
        if patina > 0:
            p = noise.fbm(noise.warp(tx.Pw, 2.0, 7.0, seed + 2), 6.0, 3, seed + 3)
            pm = p < patina * (0.8 + 0.5 * (tx.Nw[:, 1] < -0.2))
            col = np.where(pm[:, None], pick(patina_pal, 2.6 + (p - 0.25) * 4.0 + _shade(tx)), col)
        if soot > 0:
            s = noise.fbm(tx.Pw, 8.0, 2, seed + 4) < soot
            col = np.where(s[:, None], col * 0.45, col)
        col = _outline(tx, col, outline)
        return _out(tx, col)

    return paint


@_factory(lambda *a, **kw: kw.get("holes", 0.0) <= 0)
def wicker(pal=WICKER, seed=121, strand=2.0, holes=0.0, scorch=0.3, glow_cracks=0.0):
    """
    Wicker: strands woven over and under (a basket weave along each face), each strand its own shade, dark gaps where
    they cross; scorched black in patches (`scorch`), some still glowing (`glow_cracks`); `holes` shows gaps through
    the weave (cut out).
    """

    def paint(tx):
        q, size = _box_frame(tx)
        u = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0])
        v = np.where(np.abs(tx.N[:, 1]) > 0.5, q[:, 2], q[:, 1])
        a_ = (u + v) / strand
        b_ = (u - v) / strand
        ia = np.floor(a_)
        ib = np.floor(b_)
        over = ((ia + ib) % 2) == 0
        fa = a_ - ia
        fb = b_ - ib
        f = np.where(over, fb, fa)
        sid = np.where(over, ia, ib + 1000)
        sh = noise.hash_float(sid, tx.cube_index.astype(float), 0.0, seed) - 0.5
        k = 2.8 + sh * 1.6 + np.sin(f * np.pi) * 0.9 - 0.4 + _shade(tx) * 0.6
        gap = (np.minimum(fa, 1.0 - fa) < 0.12) & (np.minimum(fb, 1.0 - fb) < 0.12)
        k = np.where(gap, 0.3, k)
        col = pick(pal, k)
        sc = noise.fbm(tx.Pw, 7.0, 3, seed + 1)
        sm = sc < scorch
        col = np.where(sm[:, None], pick(CHAR, k * 0.7), col)
        g = np.zeros((tx.n, 4))
        if glow_cracks > 0:
            hm = sm & (sc < scorch * glow_cracks) & ((np.minimum(fa, 1.0 - fa) < 0.2) | (np.minimum(fb, 1.0 - fb) < 0.2))
            col = np.where(hm[:, None], EMBER[2], col)
            g[hm] = rgba(pick(EMBER, np.full(int(hm.sum()), 3.0)), 255.0)
        a = np.full(tx.n, 255.0)
        if holes > 0:
            hole = gap & (noise.hash_float(ia, ib, tx.cube_index.astype(float), seed + 2) < holes)
            a = np.where(hole & (tx.edge > 0.6), 0.0, a)
        return _out(tx, col, a, g)

    return paint


@_factory(True)
def star_iron(pal=STAR_IRON, seed=123, seams=0.5, seam_pal=WHITE_HOT, sheen=1.0, outline=0.14):
    """
    Star iron (the Starborn): dark metal with a cold oily sheen (a faint shift through violet and teal on the facets
    that catch the light) and white-hot seams where it's still cooling.
    """

    def paint(tx):
        cen = _centres(tx)
        id1, id2, border, _f, c1 = noise.voronoi(noise.warp(tx.Pw, 1.0, 9.0, seed), 8.0, seed + 1, jitter=0.7)
        facet = noise.hash_ids(id1, 0, seed + 2) - 0.5
        b = bevel(tx.P, tx.N, cen, 1.0) * 0.8 + bevel(tx.Pw, tx.Nw, c1, 1.0) * 0.5
        k = 2.0 + facet * 1.4 + b * 1.4 + _shade(tx) * 0.7
        col = pick(pal, k)
        tint = np.stack([np.sin(facet * 9.0 + 0.0), np.sin(facet * 9.0 + 2.1), np.sin(facet * 9.0 + 4.2)], axis=1)
        col = col + tint * 18.0 * sheen * np.clip(b + 0.3, 0.0, 1.0)[:, None]
        col = _outline(tx, col, outline)
        g = np.zeros((tx.n, 4))
        if seams > 0:
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 3) < seams
            sm = pair & (border < 0.6)
            gk = 3.6 - border * 3.0
            col = np.where(sm[:, None], pick(seam_pal, gk - 1.0), col)
            g[sm] = rgba(pick(seam_pal, gk[sm]), 255.0)
        return _out(tx, col, None, g)

    return paint


@_factory(lambda *a, **kw: kw.get("ragged", 0.0) <= 0 and kw.get("holes", 0.0) <= 0)
def burnt_cloth(pal=SOOT_CLOTH, seed=125, folds=1.0, ragged=0.8, holes=0.0, glow_edge=1.0, outline=0.08):
    """
    Cloth burnt along its hem: folds down each box, a ragged edge eaten away (cut out) whose last texels glow like a
    smouldering edge; scorch-brown just above it; burnt holes (`holes`), their rims glowing too.
    """

    def paint(tx):
        cen = _centres(tx)
        q, size = _box_frame(tx)
        f = np.sin(tx.P[:, 0] * 0.9 + tx.P[:, 2] * 0.9 + (noise.fbm(tx.P, 4.0, 2, seed) - 0.5) * 5.0)
        k = 2.6 + f * 0.7 * folds + (noise.fbm(tx.Pw, 2.0, 2, seed + 1) - 0.5) * 1.2 + _shade(tx) * 0.6
        k = k + bevel(tx.P, tx.N, cen, 1.0) * 0.3
        col = pick(pal, k)
        a = np.full(tx.n, 255.0)
        g = np.zeros((tx.n, 4))
        bottom = q[:, 1]
        jag = noise.fbm(tx.P * np.array([1.0, 0.0, 1.0]), 2.0, 2, seed + 3) * ragged * 9.0
        side = tx.N[:, 1] > -0.5
        if ragged > 0:
            gone = (bottom < jag) & side
            a = np.where(gone, 0.0, a)
            a = np.where(tx.N[:, 1] < -0.5, 0.0, a)
            near = side & ~gone & (bottom < jag + 1.2)
            scorch = side & ~gone & (bottom < jag + 4.0)
            col = np.where(scorch[:, None], col * 0.6 + LEATHER[3] * 0.4, col)
            if glow_edge > 0:
                col = np.where(near[:, None], EMBER[2], col)
                g[near] = rgba(pick(EMBER, np.full(int(near.sum()), 3.0)), 255.0 * glow_edge)
        if holes > 0:
            hn = noise.fbm(tx.P, 2.6, 2, seed + 4)
            hm = (hn > 1.0 - holes) & (tx.edge > 1.0)
            rim = (hn > 1.0 - holes - 0.035) & ~hm & (tx.edge > 1.0)
            a = np.where(hm, 0.0, a)
            col = np.where(rim[:, None], EMBER[2], col)
            g[rim] = rgba(pick(EMBER, np.full(int(rim.sum()), 3.0)), 255.0 * glow_edge)
        col = _outline(tx, col, outline)
        return _out(tx, col, a, g)

    return paint


@_factory(True)
def ember_feathers(pal=FEATHER, seed=127, glow_pal=EMBER, tips=0.28, outline=0.1, sheen=0.0):
    """
    Charred black feathers whose lower edges still burn: barbs along each box, scalloped rows, the bottom `tips` of
    each box (its own -y) glowing ember orange, brightest at the very edge.
    """

    def paint(tx):
        cen = _centres(tx)
        q, size = _box_frame(tx)
        barb = np.sin(tx.P[:, 0] * 2.4 + tx.P[:, 1] * 0.8)
        rowv = (tx.P[:, 1] % 5.0) / 5.0
        k = 2.4 + barb * 0.35 + (noise.fbm(tx.Pw, 2.0, 2, seed) - 0.5) * 1.0 - rowv * 0.6 + _shade(tx) * 0.5
        k = k + bevel(tx.P, tx.N, cen, 1.0) * 0.5 * (1.0 + sheen)
        col = pick(pal, k)
        col = _outline(tx, col, outline)
        h = q[:, 1] / np.maximum(size[:, 1], 1.0)
        wob = (noise.fbm(tx.P * np.array([1.0, 0.0, 1.0]), 1.5, 2, seed + 1) - 0.5) * 0.12
        tm = (h < tips + wob) & (np.abs(tx.N[:, 1]) < 0.5)
        gk = 2.0 + (tips - h) / max(tips, 0.05) * 2.6
        col = np.where(tm[:, None], pick(glow_pal, gk - 0.8), col)
        g = np.zeros((tx.n, 4))
        g[tm] = rgba(pick(glow_pal, gk[tm]), 255.0)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def chitin_glow(pal=CHITIN_RED, seed=129, rim_pal=EMBER, rim=0.0, gloss=1.4, outline=0.16, seams=0.0):
    """
    Glossy shell with a hot rim: like the Rimefall kit's chitin, plus each box's edges glowing a little (`rim`, the
    joints and plate edges of a fire creature) and glowing seams between plates (`seams`).
    """

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 6.0, 2, seed)
        b = bevel(tx.P, tx.N, cen, 1.0)
        k = 2.2 + (n - 0.5) * 1.4 + b * gloss + _shade(tx) * 0.8
        hl = (b > 0.75) & (_speck(tx.Pw, seed + 1, 2.0) < 0.35)
        k = np.where(hl, len(pal) - 0.6, k)
        col = _outline(tx, pick(pal, k), outline)
        g = np.zeros((tx.n, 4))
        if rim > 0:
            rm = (tx.edge < 0.55) & (noise.fbm(tx.Pw, 3.0, 1, seed + 2) < 0.35 + rim * 0.5)
            col = np.where(rm[:, None], pick(rim_pal, np.full(tx.n, 1.4)), col)
            g[rm] = rgba(pick(rim_pal, np.full(int(rm.sum()), 2.4)), 255.0 * min(1.0, rim + 0.4))
        if seams > 0:
            id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 1.5, 8.0, seed + 3), 9.0, seed + 4)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 5) < seams
            sm = pair & (border < 0.45)
            col = np.where(sm[:, None], pick(rim_pal, np.full(tx.n, 2.0)), col)
            g[sm] = rgba(pick(rim_pal, np.full(int(sm.sum()), 3.2)), 255.0)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def beetle(pal=SCARAB, seed=137, sheen=1.0, seams=0.0, seam_pal=EMBER, outline=0.16, gloss=1.5):
    """
    A beetle's shell: glossy bronze-black, its highlights shifting smoothly from bronze-green on the faces that look up
    to violet on the ones that look out sideways (a real beetle's iridescence follows the curve of its shell, never a
    patchwork); glowing seams between plates (`seams`).
    """

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 6.0, 2, seed)
        b = bevel(tx.P, tx.N, cen, 1.0)
        k = 2.0 + (n - 0.5) * 1.2 + b * gloss + _shade(tx) * 0.7
        hl = (b > 0.7) & (_speck(tx.Pw, seed + 1, 2.0) < 0.4)
        k = np.where(hl, len(pal) - 0.8, k)
        col = pick(pal, k)
        up = np.clip(tx.Nw[:, 1], 0.0, 1.0)[:, None]
        green = np.array([10.0, 34.0, 18.0])
        violet = np.array([26.0, 6.0, 34.0])
        tint = green * up + violet * (1.0 - up)
        lit = np.clip(0.35 + b * 0.8 + (k - 2.0) * 0.15, 0.0, 1.0)[:, None]
        col = col + tint * lit * sheen
        col = _outline(tx, col, outline)
        g = np.zeros((tx.n, 4))
        if seams > 0:
            id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 1.5, 8.0, seed + 3), 9.0, seed + 4)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 5) < seams
            sm = pair & (border < 0.45)
            col = np.where(sm[:, None], pick(seam_pal, np.full(tx.n, 2.0)), col)
            g[sm] = rgba(pick(seam_pal, np.full(int(sm.sum()), 3.2)), 255.0)
        return _out(tx, col, None, g)

    return paint


MEMBRANE = palette("#2a0c08", "#3c120c", "#521a10", "#6a2414", "#842f1a", "#a03c22")


@_factory(lambda *a, **kw: kw.get("ragged", 0.0) <= 0 and kw.get("holes", 0.0) <= 0)
def membrane(pal=MEMBRANE, seed=139, vein_pal=EMBER, veins=1.0, hot=0.5, ragged=0.0, holes=0.0, spots=(),
             spot_pal=EMBER, outline=0.05, stretch=(1.0, 0.25, 1.0)):
    """
    A wing's membrane: thin leather, darker in its folds, criss-crossed by veins (branching cracks along each plate's
    own length, `stretch` squashing them along its y) that glow where the membrane is hottest (`hot`: a share of the
    veins); light shows through it a little (lighter towards each plate's middle). `ragged`: the plate's bottom edge
    (its own -y: the trailing edge) torn and burnt away, its last texels smouldering; `holes`: burnt holes with glowing
    rims; `spots`: (x, y, z, radius) round ember spots, glowing.
    """

    def paint(tx):
        q, size = _box_frame(tx)
        n = noise.fbm(tx.Pw, 5.0, 3, seed)
        mid = 1.0 - np.abs(q[:, 0] / np.maximum(size[:, 0], 1.0) - 0.5) * 2.0
        k = 2.2 + (n - 0.5) * 1.6 + mid * 0.7 + (_speck(tx.Pw, seed + 1) - 0.5) * 0.5
        fold = np.sin(tx.P[:, 0] * 0.35 + noise.fbm(tx.P, 6.0, 1, seed + 2) * 4.0)
        k = k + fold * 0.45
        col = pick(pal, k)
        g = np.zeros((tx.n, 4))
        if veins > 0:
            st = np.asarray(stretch, dtype=float)
            _a, _b, border, _f, _c = noise.voronoi(noise.warp(tx.P * st, 1.4, 6.0, seed + 3), 7.0, seed + 4)
            vm = border < 0.32 * veins
            col = np.where(vm[:, None], col * 0.55, col)
            if hot > 0:
                heat = noise.fbm(tx.Pw, 14.0, 2, seed + 5)
                hm = vm & (heat < 0.2 + hot * 0.4)
                gk = 2.2 + (0.2 + hot * 0.4 - heat) * 7.0
                col = np.where(hm[:, None], pick(vein_pal, gk - 1.0), col)
                g[hm] = rgba(pick(vein_pal, gk[hm]), 255.0)
        for (sx, sy, sz, r) in spots:
            d = np.sqrt((tx.Pw[:, 0] - sx) ** 2 + (tx.Pw[:, 1] - sy) ** 2 + (tx.Pw[:, 2] - sz) ** 2) / r
            sm = d < 1.0
            sk = 4.2 - d * 2.4
            col = np.where(sm[:, None], pick(spot_pal, sk - 1.0), col)
            g[sm] = rgba(pick(spot_pal, sk[sm]), 255.0)
        col = _outline(tx, col, outline)
        a = np.full(tx.n, 255.0)
        side = tx.N[:, 1] > -0.5
        if ragged > 0:
            jag = noise.fbm(tx.P * np.array([1.0, 0.0, 1.0]), 1.8, 3, seed + 6) * ragged * 8.0
            strip = np.floor(np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0]) / 3.0)
            torn = noise.hash_float(strip, tx.cube_index.astype(float), 2.0, seed + 7)
            jag = jag + np.where(torn > 0.78, (torn - 0.78) / 0.22 * ragged * 9.0, 0.0)
            gone = (q[:, 1] < jag) & side
            a = np.where(gone, 0.0, a)
            a = np.where(tx.N[:, 1] < -0.5, 0.0, a)
            near = side & ~gone & (q[:, 1] < jag + 1.2)
            col = np.where(near[:, None], EMBER[2], col)
            g[near] = rgba(pick(EMBER, np.full(int(near.sum()), 3.0)), 255.0)
        if holes > 0:
            hn = noise.fbm(tx.P, 3.0, 2, seed + 8)
            hm = (hn > 1.0 - holes) & (tx.edge > 1.0)
            rim = (hn > 1.0 - holes - 0.035) & ~hm & (tx.edge > 1.0)
            a = np.where(hm, 0.0, a)
            col = np.where(rim[:, None], EMBER[2], col)
            g[rim] = rgba(pick(EMBER, np.full(int(rim.sum()), 3.0)), 255.0)
        return _out(tx, col, a, g)

    return paint


@_factory(True)
def sulfur(pal=SULFUR, seed=131, crystals=1.0, outline=0.12):
    """Sulfur crust: lumpy yellow crystal growth, paler on the faces that catch the light, darker in the hollows."""

    def paint(tx):
        cen = _centres(tx)
        id1, _id2, border, _f, c1 = noise.voronoi(tx.Pw, 2.5, seed, jitter=0.9)
        shade = noise.hash_ids(id1, 0, seed + 1) - 0.5
        k = 3.0 + shade * 1.6 * crystals + bevel(tx.Pw, tx.Nw, c1, 1.0) * 1.0 + bevel(tx.P, tx.N, cen, 1.0) * 0.4
        k = np.where(border < 0.3, k - 1.2, k) + _shade(tx) * 0.6
        return _out(tx, _outline(tx, pick(pal, k), outline))

    return paint


@_factory(True)
def wings(pal=MOTH, seed=133, spots=(), spot_pal=EMBER, band_pal=None, bands=0.0, veins=0.7, outline=0.06,
          dust=0.3, spot_glow=1.0):
    """
    A moth's wing (painted on flat plates; positions in the model's own units): powdery scales with a soft noise,
    darker veins running out from the root, wavy bands across (`band_pal`), and round eyespots at `spots`
    (each (x, y, z, radius)): a dark ring, a glowing ember iris, a pale glint, the same on both faces.
    """

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 3.0, 3, seed)
        k = 2.6 + (n - 0.5) * 1.6 + (_speck(tx.Pw, seed + 1) - 0.5) * 0.9 * dust + _shade(tx) * 0.4
        k = k + bevel(tx.P, tx.N, cen, 1.0) * 0.2
        col = pick(pal, k)
        if veins > 0:
            v = noise.fbm(noise.warp(tx.Pw, 1.0, 14.0, seed + 2) * np.array([0.35, 1.0, 0.35]), 6.0, 2, seed + 3)
            vm = np.abs(v - 0.5) < 0.03 * veins
            col = np.where(vm[:, None], col * 0.55, col)
        if band_pal is not None and bands > 0:
            bnd = np.sin(np.linalg.norm(tx.Pw[:, [0, 1]], axis=1) * 0.11 + (n - 0.5) * 3.0)
            bm = bnd > 1.0 - bands
            col = np.where(bm[:, None], pick(band_pal, k), col)
        g = np.zeros((tx.n, 4))
        for (sx, sy, sz, r) in spots:
            d = np.sqrt((tx.Pw[:, 0] - sx) ** 2 + (tx.Pw[:, 1] - sy) ** 2 + (tx.Pw[:, 2] - sz) ** 2) / r
            ring = (d < 1.0) & (d >= 0.78)
            iris = (d < 0.78) & (d >= 0.22)
            pupil = d < 0.22
            col = np.where(ring[:, None], CHAR[0], col)
            ik = 4.2 - d * 2.6
            col = np.where(iris[:, None], pick(spot_pal, ik - 1.0), col)
            g[iris] = rgba(pick(spot_pal, ik[iris]), 255.0 * spot_glow)
            col = np.where(pupil[:, None], CHAR[1], col)
            gl = pupil & (_speck(tx.Pw, seed + 4) < 0.5)
            col = np.where(gl[:, None], np.array([255.0, 236.0, 200.0]), col)
        col = _outline(tx, col, outline)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def smoke(pal=ASH_DARK, seed=135):
    """Frozen smoke and soot clouds: soft dark grey puffs."""

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 4.0, 3, seed)
        k = 2.4 + (n - 0.5) * 2.4 + bevel(tx.P, tx.N, cen, 1.0) * 0.8 + _shade(tx) * 0.8
        return _out(tx, pick(pal, k))

    return paint
