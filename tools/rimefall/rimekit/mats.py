"""
The Rimefall candidates' materials. Each `material(...)` returns a painter: texels -> colour, alpha and glow.

A painter gets the texels of every box that uses it (kit/paint.py Texels), with two extra arrays added by
rimekit/texture.py: Pw and Nw, each texel's position and normal in the posed model (Bedrock units), so patterns run on
across turned boxes and frost settles on whatever faces up. P and N are the same in the box's own unturned frame (y
runs along a box made by Shape.seg, which is how bark grain and fur strands follow a limb).

What it returns:
  color  (n, 3)  the colour, 0-255
  alpha  (n,)    255 solid; 0 a hole (cut out); anything between is see-through ice (drawn by the mod's ice layer)
  glow   (n, 4)  the glowing parts (eyes, crystals, a warm window), drawn full-bright on top

Palette notes: the Ice Necromancer's colours are pale blues and white (BOSS_GUIDE.md), never the Dross's electric
blue. The greys and whites match Rimefall's own blocks (frostbitten log, hoarfrost, icicle, frostbloom). The Aurora
Eel's northern-lights colours (greens, teals, violets, pinks) are the user's request for it (round 2).

Each painter also says whether it's `opaque` (solid everywhere, no holes, no see-through ice): only opaque boxes hide
the faces buried in them (rimekit/uvpack.py).
"""
import os

import numpy as np
from PIL import Image

from kit import noise
from kit.paint import bevel, palette, pick, rgb, rgba

BLOCK_TEXTURES = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "..", "src", "main", "resources",
                                              "assets", "dross", "textures", "block"))


def _factory(opaque):
    """Marks what a material factory makes as opaque (True/False, or a test on the factory's arguments)."""

    def deco(fn):
        def make(*a, **kw):
            p = fn(*a, **kw)
            if not hasattr(p, "opaque"):
                p.opaque = bool(opaque(*a, **kw) if callable(opaque) else opaque)
            return p

        make.__name__ = fn.__name__
        make.__doc__ = fn.__doc__
        return make

    return deco

# ---------------------------------------------------------------- palettes (dark to light)

SNOW = palette("#7f8ea3", "#9fb0c3", "#bccbd9", "#d3dee8", "#e7eef4", "#fafcff")
RIME = palette("#b9c9d9", "#d2deea", "#e8eff6", "#ffffff")
ICE = palette("#5a8fb9", "#6aa6d6", "#93c5ec", "#c2e2f8", "#e2f3fc", "#f6fcff")
GLACIER = palette("#1d3549", "#28465f", "#355b77", "#4a7592", "#6893ad", "#93b8cd", "#c8e0ec")
BARK = palette("#2c2d31", "#38393d", "#47494e", "#57595f", "#686b71", "#7a7d83", "#8d9096")
STONE = palette("#272b31", "#353a41", "#454b53", "#585f68", "#6e757e", "#878e96")
MARBLE = palette("#8fa2b2", "#a6b8c6", "#bccbd7", "#d1dde5", "#e3ebf0", "#f2f6f9")
BONE = palette("#77746c", "#94918a", "#b1aea4", "#cbc7bc", "#e1ded4", "#f2f0e9")
IRON = palette("#13161b", "#1d2128", "#2a2f37", "#3a404a", "#4f5662", "#6a7380")
BLACK_ICE = palette("#05080d", "#0b1118", "#131c26", "#1d2a38", "#2c3f53", "#46637f")
GLOW = palette("#5fb0d4", "#86cbe6", "#ade1f3", "#d2f2fb", "#f1fcff")
GLOW_GREEN = palette("#5fbfae", "#84d6c4", "#aae9d9", "#d0f6ec", "#f0fffa")
WARM = palette("#6e3610", "#a85c18", "#de8a28", "#ffb84c", "#ffdd92", "#fff4d2")
VERDIGRIS = palette("#22403f", "#2f5452", "#406b66", "#57867d", "#74a297", "#97beb2")
WOOD = palette("#251c15", "#33271d", "#433326", "#554131", "#69513e", "#7f634d")
CLOTH_DARK = palette("#10141b", "#171d26", "#202833", "#2b3542", "#3a4656", "#4f5d70")
CLOTH_PALE = palette("#5f6d7b", "#76838f", "#8e9aa5", "#a8b2bb", "#c3cbd2", "#dde3e8")
ROPE = palette("#3a3329", "#4d4436", "#625746", "#7a6d58", "#93856d")

# Hides, furs, shells (cold, washed-out versions of the animals' own colours)
WOLVERINE = palette("#1a1b21", "#23252d", "#2e3039", "#3a3d48", "#4a4e5b", "#5f6474")
WOLV_BAND = palette("#6d7784", "#87919c", "#a1aab4", "#bac2ca", "#d2d8dd")
MOLE = palette("#2f3640", "#3c4450", "#4b5461", "#5d6774", "#717c89", "#88929e")
STAR_FLESH = palette("#7d9fb0", "#97b8c6", "#b3d0da", "#cfe5ec", "#e9f6f9")
CHITIN = palette("#0b0f15", "#121822", "#1a222e", "#25303e", "#344353", "#4c6176")
PALE_SKIN = palette("#62737f", "#798b96", "#91a3ad", "#aabbc3", "#c4d3d8", "#dde8eb")
FROST_SKIN = palette("#4f5c69", "#63707d", "#788592", "#8f9ca8", "#a9b5bf", "#c5ced5")
LIZARD = palette("#1f2a33", "#2a3743", "#374653", "#465867", "#5a6e7d", "#738898")
TOAD = palette("#28302f", "#343e3d", "#424e4c", "#53605d", "#677571", "#808d88")
MANTIS = palette("#4d6a79", "#62808f", "#7a98a5", "#96b4bf", "#b6d0d8", "#d6e9ee")
OWL = palette("#8f9ba7", "#adb8c3", "#c8d1d9", "#dde3e8", "#eef1f4", "#fbfcfd")
OWL_BAR = palette("#222932", "#323b47", "#454f5c", "#5b6573")
MUZZLE = palette("#6d5f5d", "#857674", "#9c8d8a", "#b3a5a1", "#c7bbb7", "#dbd1cd")
WALRUS = palette("#332d2f", "#433c3e", "#554d4f", "#6a6163", "#817778", "#9b9192")
ELK = palette("#1f1c1a", "#2a2623", "#37322e", "#46403b", "#57504a", "#6b635b")
CRAB = palette("#46525d", "#596672", "#6d7b87", "#84929e", "#9eabb6", "#bcc7d0")
SHARK = palette("#232b35", "#2e3844", "#3b4653", "#4b5764", "#5e6a77", "#76828e")
FISH = palette("#525c66", "#66717a", "#7c868f", "#949da5", "#aeb6bc", "#c9cfd3")
MOUTH = palette("#0a0b10", "#141620", "#1f2230", "#2c3042")

# Round 2
MOOSE = palette("#16120f", "#1f1a16", "#2a231d", "#362d26", "#443a31", "#55493e")       # near-black brown
MOOSE_LEG = palette("#4f4a45", "#625c56", "#77706a", "#8d8680", "#a49d97", "#bcb6b0")   # pale grey legs
WOOLLY = palette("#2a1d14", "#38271b", "#493324", "#5c412e", "#71523a", "#886649")      # woolly rhino brown
WOOLLY_PALE = palette("#6f7680", "#868d97", "#9ea5ae", "#b6bcc4", "#ced3d9", "#e5e9ed")  # frosted pale coat
HORN_DARK = palette("#15161a", "#202227", "#2c2f35", "#3b3f46", "#4d525a")
LEATHER = palette("#1c1612", "#271f19", "#342922", "#43362c", "#544438", "#665446")
GOLDEN = palette("#4a3a1c", "#6b5326", "#8c6d30", "#ad893d", "#c9a752", "#e2c674")      # old brass fittings
# Coloured ices (the Fisher's crust, the Snow Angel's halo)
ICE_DEEP = palette("#0f2a52", "#173a6b", "#204c84", "#2e629e", "#4479b4", "#6493c7")
ICE_TEAL = palette("#0f4a4c", "#16605f", "#1f7774", "#2e8f89", "#46a8a0", "#6cc2b8")
ICE_WHITE = palette("#9fb4c6", "#b8cad8", "#cfdde8", "#e2ecf4", "#f1f7fb", "#ffffff")
ICE_VIOLET = palette("#2e2a5c", "#3c3774", "#4d478c", "#625ca4", "#7d77ba", "#9d98cf")
# The northern lights (Aurora Eel)
AUR_GREEN = palette("#0f5a3c", "#16774f", "#1f9764", "#33b87b", "#5cd69a", "#93ecc0", "#d0fbe6")
AUR_TEAL = palette("#0d4f5a", "#136a76", "#1c8794", "#2ea6b1", "#52c3cc", "#8ddde2", "#cdf5f7")
AUR_VIOLET = palette("#33205e", "#432a7a", "#563798", "#6c4ab3", "#8866ca", "#a98bdc", "#d3c4f0")
AUR_PINK = palette("#5a1e4c", "#782866", "#963782", "#b44c9c", "#cc6db4", "#e199cd", "#f6cfe8")


def _speck(P, seed, cell=1.0):
    return noise.hash_float(np.floor(P[:, 0] / cell), np.floor(P[:, 1] / cell), np.floor(P[:, 2] / cell), seed)


def _centres(tx):
    return np.array([tx.cubes[i].center() for i in tx.cube_index])


def _tags(tx):
    return np.array([tx.cubes[i].tag or "" for i in tx.cube_index], dtype=object)


def _sizes(tx):
    return np.array([tx.cubes[i].size for i in tx.cube_index], dtype=float)


def _shade(tx):
    """Faces looking up a little lighter, faces looking down darker (on top of Minecraft's own lighting)."""
    ny = tx.Nw[:, 1]
    return np.where(ny > 0.55, 0.35, np.where(ny < -0.55, -0.75, 0.0))


def _out(tx, col, alpha=None, glow=None):
    a = np.full(tx.n, 255.0) if alpha is None else np.asarray(alpha, dtype=float)
    g = np.zeros((tx.n, 4)) if glow is None else glow
    return dict(color=np.clip(col, 0, 255), alpha=np.clip(a, 0, 255), glow=g)


def _outline(tx, col, amount):
    return np.where((tx.edge < 0.5)[:, None], col * (1.0 - amount), col)


def frost(tx, col, amount=0.3, seed=91, pal=RIME):
    """White frost settled on the faces that look up, and a few frost specks everywhere."""
    if amount <= 0:
        return col
    up = tx.Nw[:, 1]
    n = noise.fbm(tx.Pw, 3.0, 3, seed)
    sp = _speck(tx.Pw, seed + 1)
    k = np.clip(1.6 + (0.5 - n) * 4.0 + sp * 0.8, 0, len(pal) - 1)
    # Frost lies in drifts on faces that look up (fine-grained, never whole faces), and as single glittering
    # crystals here and there.
    drift = (up > 0.45) & (n < 0.2 + amount * 0.45) & (sp < 0.45 + amount)
    col = np.where(drift[:, None], col * 0.25 + pick(pal, k) * 0.75, col)
    crystal = (_speck(tx.Pw, seed + 2) < 0.018 * amount) & (up > -0.3)
    col = np.where(crystal[:, None], pal[-1], col)
    return col


# ---------------------------------------------------------------- materials


@_factory(lambda *a, **kw: kw.get('alpha', 255.0) >= 250)
def solid(pal, idx=2.5, seed=5, grain=1.0, bev=0.6, outline=0.14, frost_amount=0.0, glow_pal=None, glow_alpha=255.0,
          alpha=255.0):
    """A plain material with a little noise and bevel; optionally glowing (glow_pal) or see-through (alpha)."""

    def paint(tx):
        cen = _centres(tx)
        k = idx + (noise.fbm(tx.Pw, 2.0, 2, seed) - 0.5) * 1.6 * grain + (_speck(tx.Pw, seed) - 0.5) * 0.6 * grain
        k = k + bevel(tx.P, tx.N, cen, 1.0) * bev + _shade(tx) * 0.6
        col = _outline(tx, pick(pal, k), outline)
        col = frost(tx, col, frost_amount, seed + 7)
        g = None if glow_pal is None else rgba(pick(glow_pal, k + 0.6), glow_alpha)
        return _out(tx, col, np.full(tx.n, float(alpha)), g)

    return paint


@_factory(True)
def fur(pal, seed=1, strand=3.0, frost_amount=0.25, outline=0.12, pattern=None, along_box=False):
    """Fur: strands running down (or along each box, along_box), lighter on top, a darker outline, frost tips."""

    def paint(tx):
        cen = _centres(tx)
        Q = tx.P if along_box else tx.Pw
        s = noise.fbm(Q * np.array([1.0, 0.25, 1.0]), 2.0, 2, seed)
        sp = _speck(Q, seed + 3) - 0.5
        k = 2.6 + (s - 0.5) * strand + sp * 0.9 + bevel(tx.P, tx.N, cen, 0.9) * 0.5 + _shade(tx)
        col = pick(pal, k)
        if pattern is not None:
            col = pattern(tx, col, k)
        col = _outline(tx, col, outline)
        col = frost(tx, col, frost_amount, seed + 11)
        return _out(tx, col)

    return paint


@_factory(True)
def skin(pal, seed=3, mottle=1.0, frost_amount=0.15, scars=0.0, cracks=0.0, outline=0.1, crack_pal=None):
    """Skin or hide: soft mottling, a little bevel, pale scars, dark frost cracks."""

    def paint(tx):
        cen = _centres(tx)
        m = noise.fbm(noise.warp(tx.Pw, 1.5, 8.0, seed), 5.0, 3, seed + 1)
        k = 2.6 + (m - 0.5) * 2.2 * mottle + (_speck(tx.Pw, seed + 2) - 0.5) * 0.5 + bevel(tx.P, tx.N, cen, 1.0) * 0.5
        k = k + _shade(tx)
        col = pick(pal, k)
        if scars > 0:
            id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 1.4, 6.0, seed + 5), 9.0, seed + 6)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 7) < scars
            sm = pair & (border < 0.45)
            col = np.where(sm[:, None], col * 0.4 + pal[-1] * 0.6, col)
        if cracks > 0:
            id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 2.0, 7.0, seed + 8), 6.0, seed + 9)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 10) < cracks
            cm = pair & (border < 0.4)
            cp = crack_pal if crack_pal is not None else BLACK_ICE
            col = np.where(cm[:, None], pick(cp, np.full(tx.n, 1.5)), col)
        col = _outline(tx, col, outline)
        col = frost(tx, col, frost_amount, seed + 13)
        return _out(tx, col)

    return paint


@_factory(True)
def scales(pal, seed=7, cell=3.0, frost_amount=0.2, belly_pal=None, outline=0.1):
    """Scales: little cells with dark seams, each cell its own shade; a paler belly underneath."""

    def paint(tx):
        id1, _id2, border, _f, c1 = noise.voronoi(tx.Pw, cell, seed, jitter=0.7)
        shade = noise.hash_ids(id1, 0, seed + 1) - 0.5
        k = 2.6 + shade * 1.6 + bevel(tx.Pw, tx.Nw, c1, 1.0) * 0.8 + _shade(tx)
        k = np.where(border < 0.35, k - 1.6, k)
        col = pick(pal, k)
        if belly_pal is not None:
            under = tx.Nw[:, 1] < -0.4
            col = np.where(under[:, None], pick(belly_pal, k - 0.3), col)
        col = _outline(tx, col, outline)
        col = frost(tx, col, frost_amount, seed + 5)
        return _out(tx, col)

    return paint


@_factory(True)
def chitin(pal, seed=9, gloss=1.4, hairs=0.0, frost_amount=0.1, outline=0.18, hair_pal=RIME):
    """Shell: smooth, with strong glossy highlights towards the light; optional white hairs."""

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 6.0, 2, seed)
        b = bevel(tx.P, tx.N, cen, 1.0)
        k = 2.2 + (n - 0.5) * 1.4 + b * gloss + _shade(tx) * 0.8
        hl = (b > 0.75) & (_speck(tx.Pw, seed + 1, 2.0) < 0.35)
        k = np.where(hl, len(pal) - 0.6, k)
        col = pick(pal, k)
        if hairs > 0:
            hm = _speck(tx.Pw, seed + 2) < hairs
            col = np.where(hm[:, None], pick(hair_pal, np.full(tx.n, 2.0)), col)
        col = _outline(tx, col, outline)
        col = frost(tx, col, frost_amount, seed + 3)
        return _out(tx, col)

    return paint


@_factory(False)
def ice(pal=ICE, alpha=(105, 175), seed=11, cracks=0.35, bubbles=0.015, frost_amount=0.15, rim=1.0, glow_pal=None,
        glow_alpha=0.0, crack_glow=0.0):
    """
    See-through ice (the mod's ice layer): a pale blue tint, whiter and more solid along cracks, round bubbles and the
    box edges (light catches them). glow_pal + glow_alpha: a soft inner glow over the whole surface; crack_glow: the
    cracks glow too.
    """

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 4.0, 2, seed)
        k = 2.2 + (n - 0.5) * 2.0 + bevel(tx.P, tx.N, cen, 1.0) * 0.8 + _shade(tx) * 0.5
        a = alpha[0] + (alpha[1] - alpha[0]) * np.clip(n * 1.4 - 0.2, 0.0, 1.0)
        id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 1.6, 7.0, seed + 1), 8.0, seed + 2)
        pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 3) < cracks
        crack = pair & (border < 0.45)
        k = np.where(crack, 4.6, k)
        a = np.where(crack, 225.0, a)
        bub = _speck(tx.Pw, seed + 4) < bubbles
        k = np.where(bub, 5.0, k)
        a = np.where(bub, 215.0, a)
        if rim > 0:
            edge = tx.edge < 0.6
            k = np.where(edge, np.maximum(k, 3.6), k)
            a = np.where(edge, np.minimum(245.0, a + 55.0 * rim), a)
        col = pick(pal, k)
        if frost_amount > 0:
            up = (tx.Nw[:, 1] > 0.45) & (noise.fbm(tx.Pw, 2.5, 2, seed + 5) < 0.2 + frost_amount)
            col = np.where(up[:, None], RIME[-2], col)
            a = np.where(up, 240.0, a)
        g = np.zeros((tx.n, 4))
        if glow_pal is not None and glow_alpha > 0:
            g = rgba(pick(glow_pal, k * 0.6 + 1.0), glow_alpha * (0.6 + 0.4 * n))
        if crack_glow > 0:
            gc = rgba(pick(glow_pal if glow_pal is not None else GLOW, np.full(tx.n, 3.2)), 255.0 * crack_glow)
            g = np.where(crack[:, None], gc, g)
        return _out(tx, col, a, g)

    return paint


@_factory(True)
def glacier(pal=GLACIER, seed=13, snow_amount=0.55, bands=1.0, cracks=0.3, outline=0.1):
    """Solid old ice: blue-white layers (strata), pale cracks, snow on everything that faces up."""

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 5.0, 3, seed)
        layer = np.sin(tx.Pw[:, 1] * 0.55 + (noise.fbm(tx.Pw, 9.0, 2, seed + 1) - 0.5) * 6.0)
        k = 3.0 + (n - 0.5) * 2.2 + layer * 0.8 * bands + bevel(tx.P, tx.N, cen, 1.0) * 0.7 + _shade(tx) * 0.6
        id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 2.0, 8.0, seed + 2), 9.0, seed + 3)
        pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 4) < cracks
        k = np.where(pair & (border < 0.4), len(pal) - 1.0, k)
        col = pick(pal, k)
        col = _outline(tx, col, outline)
        if snow_amount > 0:
            up = tx.Nw[:, 1] > 0.45
            sn = noise.fbm(tx.Pw, 3.0, 2, seed + 5)
            sm = up & (sn < 0.3 + snow_amount)
            col = np.where(sm[:, None], pick(SNOW, 3.6 + (sn - 0.5) * 2.0 + _speck(tx.Pw, seed + 6) * 0.6), col)
        return _out(tx, col)

    return paint


@_factory(True)
def snow(pal=SNOW, seed=15, sparkle=0.012, outline=0.06):
    """Snow: soft and bright, bluer in the shadows, the odd sparkle."""

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 3.5, 3, seed)
        k = 3.4 + (n - 0.5) * 1.8 + bevel(tx.P, tx.N, cen, 1.0) * 0.5 + _shade(tx) * 1.2
        k = np.where(_speck(tx.Pw, seed + 1) < sparkle, len(pal) - 1.0, k)
        col = _outline(tx, pick(pal, k), outline)
        return _out(tx, col)

    return paint


@_factory(True)
def bark(pal=BARK, seed=17, rime=0.45, knots=0.08, outline=0.12):
    """Frostbitten bark (like Rimefall's frostbitten logs): grain along each box, dark fissures, white rime on top."""

    def paint(tx):
        cen = _centres(tx)
        g = noise.fbm(tx.P * np.array([1.0, 0.1, 1.0]), 1.6, 2, seed)
        f = noise.fbm(tx.P * np.array([0.7, 0.06, 0.7]), 2.2, 2, seed + 1)
        k = 3.0 + (g - 0.5) * 3.0 + bevel(tx.P, tx.N, cen, 1.0) * 0.6 + _shade(tx) * 0.6
        k = np.where(f < 0.32, k - 2.0, k)
        if knots > 0:
            kn = _speck(tx.Pw, seed + 2, 4.0) < knots
            k = np.where(kn & (noise.fbm(tx.Pw, 1.5, 1, seed + 3) < 0.4), k - 1.5, k)
        col = _outline(tx, pick(pal, k), outline)
        col = frost(tx, col, rime, seed + 4)
        return _out(tx, col)

    return paint


@_factory(True)
def bone(pal=BONE, seed=19, frost_amount=0.1, cracks=0.15, outline=0.12):
    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.P * np.array([1.0, 0.4, 1.0]), 2.5, 2, seed)
        k = 3.0 + (n - 0.5) * 1.8 + bevel(tx.P, tx.N, cen, 1.0) * 0.7 + _shade(tx) * 0.5
        if cracks > 0:
            c = noise.fbm(tx.Pw * np.array([0.5, 2.0, 0.5]), 3.0, 2, seed + 1)
            k = np.where(np.abs(c - 0.5) < 0.02 * (1 + cracks * 3), k - 1.6, k)
        col = _outline(tx, pick(pal, k), outline)
        col = frost(tx, col, frost_amount, seed + 2)
        return _out(tx, col)

    return paint


@_factory(True)
def metal(pal=IRON, seed=21, shine=1.4, frost_amount=0.2, rust=0.0, outline=0.2):
    def paint(tx):
        cen = _centres(tx)
        k = 2.4 + bevel(tx.P, tx.N, cen, 1.0) * shine + (noise.fbm(tx.Pw, 3.0, 2, seed) - 0.5) * 1.0 + _shade(tx)
        k = np.where(_speck(tx.Pw, seed + 1) < 0.02, len(pal) - 1.0, k)
        col = pick(pal, k)
        col = _outline(tx, col, outline)
        col = frost(tx, col, frost_amount, seed + 2)
        return _out(tx, col)

    return paint


@_factory(True)
def wood(pal=WOOD, seed=23, plank=4.0, axis=0, frost_amount=0.3, outline=0.14):
    """Planks: boards `plank` units wide across the box's own `axis` (0 x, 1 y), grain along them, snow on top."""

    def paint(tx):
        cen = _centres(tx)
        q = tx.P
        board = np.floor(q[:, axis] / plank)
        bshade = noise.hash_ids(board.astype(np.int64), np.floor(q[:, 2 - axis] / 30).astype(np.int64), seed) - 0.5
        grain = noise.fbm(q * (np.array([0.15, 1.0, 1.0]) if axis == 1 else np.array([1.0, 0.15, 1.0])), 1.5, 2,
                          seed + 1)
        k = 2.6 + bshade * 1.6 + (grain - 0.5) * 1.6 + bevel(tx.P, tx.N, cen, 1.0) * 0.4 + _shade(tx) * 0.6
        seam = np.abs(q[:, axis] - (board + 0.5) * plank) > plank / 2.0 - 0.6
        k = np.where(seam, k - 1.7, k)
        col = _outline(tx, pick(pal, k), outline)
        col = frost(tx, col, frost_amount, seed + 2)
        return _out(tx, col)

    return paint


@_factory(lambda *a, **kw: kw.get('ragged', 0.0) <= 0 and kw.get('holes', 0.0) <= 0)
def cloth(pal, seed=25, folds=1.0, ragged=0.0, frost_amount=0.15, outline=0.1, holes=0.0):
    """Cloth: folds running down each box, a ragged (cut-out) bottom edge, moth holes."""

    def paint(tx):
        cen = _centres(tx)
        size = _sizes(tx)
        f = np.sin(tx.P[:, 0] * 0.9 + tx.P[:, 2] * 0.9 + (noise.fbm(tx.P, 4.0, 2, seed) - 0.5) * 5.0)
        k = 2.6 + f * 0.7 * folds + (noise.fbm(tx.Pw, 2.0, 2, seed + 1) - 0.5) * 1.2 + _shade(tx) * 0.6
        k = k + bevel(tx.P, tx.N, cen, 1.0) * 0.3
        col = _outline(tx, pick(pal, k), outline)
        col = frost(tx, col, frost_amount, seed + 2)
        a = np.full(tx.n, 255.0)
        if ragged > 0:
            # Distance from the box's bottom (its own frame); a jagged hem eats into it.
            bottom = tx.P[:, 1] - (cen[:, 1] - size[:, 1] / 2.0)
            jag = noise.fbm(tx.P * np.array([1.0, 0.0, 1.0]), 2.0, 2, seed + 3) * ragged * 8.0
            a = np.where((bottom < jag) & (tx.N[:, 1] > -0.5), 0.0, a)
            # The hem's bottom face goes too: under a ragged hem it showed as a solid strip (the user saw it under
            # the Thawed Soldiers' capes).
            a = np.where(tx.N[:, 1] < -0.5, 0.0, a)
        if holes > 0:
            hm = noise.fbm(tx.P, 2.2, 2, seed + 4) > 1.0 - holes
            a = np.where(hm & (tx.edge > 1.0), 0.0, a)
        return _out(tx, col, a)

    return paint


@_factory(True)
def feathers(pal=OWL, seed=27, bars_pal=None, bars=0.0, outline=0.1, frost_amount=0.0):
    """Feathers: barbs along each box, a darker shaft line, scalloped rows; dark bars or spots (bars_pal)."""

    def paint(tx):
        cen = _centres(tx)
        q = tx.P
        barb = np.sin(q[:, 0] * 2.4 + q[:, 1] * 0.8)
        rowv = (q[:, 1] % 5.0) / 5.0
        k = 3.2 + barb * 0.35 + (noise.fbm(tx.Pw, 2.0, 2, seed) - 0.5) * 1.0 - rowv * 0.6 + _shade(tx) * 0.5
        k = k + bevel(tx.P, tx.N, cen, 1.0) * 0.4
        shaft = np.abs(q[:, 0] - cen[:, 0]) < 0.6
        k = np.where(shaft & (np.abs(tx.N[:, 2]) > 0.5), k - 1.0, k)
        col = pick(pal, k)
        if bars_pal is not None and bars > 0:
            id1, _id2, _b, f1, _c = noise.voronoi(tx.Pw * np.array([1.0, 1.6, 1.0]), 4.0, seed + 2, jitter=0.9)
            spot = (noise.hash_ids(id1, 0, seed + 3) < bars) & (f1 < 1.5)
            col = np.where(spot[:, None], pick(bars_pal, 1.5 + (noise.hash_ids(id1, 1, seed) - 0.5) * 2.0), col)
        col = _outline(tx, col, outline)
        col = frost(tx, col, frost_amount, seed + 4)
        return _out(tx, col)

    return paint


@_factory(True)
def claw(pal=BLACK_ICE, seed=29):
    """Black ice: glassy near-black with a cold highlight."""
    return chitin(pal, seed=seed, gloss=1.8, frost_amount=0.0, outline=0.1)


@_factory(True)
def teeth(seed=31):
    """Icicle teeth: pale ice, solid."""
    return solid(ICE, idx=3.8, seed=seed, grain=0.8, bev=0.9, outline=0.08)


@_factory(True)
def glowing(pal=GLOW, seed=33, base_pal=None, idx=3.0, glow_alpha=255.0, outline=0.0):
    """Something that glows (eyes, crystals, a lit window): its colour full-bright on top of a darker base."""

    def paint(tx):
        cen = _centres(tx)
        k = idx + (noise.fbm(tx.Pw, 1.5, 2, seed) - 0.5) * 1.0 + bevel(tx.P, tx.N, cen, 1.0) * 0.5
        bp = base_pal if base_pal is not None else pal
        col = _outline(tx, pick(bp, k - 1.0), outline)
        g = rgba(pick(pal, k), glow_alpha)
        return _out(tx, col, None, g)

    return paint


@_factory(True)
def eye(pal=GLOW, seed=35, pupil=True):
    """A glowing eye: bright pale core, darker rim; a dark slit pupil on the front if `pupil`."""

    def paint(tx):
        cen = _centres(tx)
        d = np.max(np.abs(tx.P - cen) / np.maximum(_sizes(tx) / 2.0, 0.5), axis=1)
        k = 4.2 - d * 1.8
        col = pick(pal, k - 1.2)
        g = rgba(pick(pal, k), 255.0)
        if pupil:
            sl = (np.abs(tx.P[:, 0] - cen[:, 0]) < 0.5) & (np.abs(tx.N[:, 2]) > 0.5)
            col = np.where(sl[:, None], MOUTH[0], col)
            g[sl] = 0.0
        return _out(tx, col, None, g)

    return paint


@_factory(False)
def aurora(seed=37, alpha=(55, 150), glow_alpha=210.0, low=None, high=None):
    """
    The northern lights as see-through curtains: pale streaks hanging down each box, brightest along its top edge,
    fading out towards the bottom; they glow (ice glow), white-cyan at the top and pale green lower down (or `high` at
    the top and `low` at the bottom: palettes).
    """

    def paint(tx):
        cen = _centres(tx)
        size = _sizes(tx)
        h = np.clip((tx.P[:, 1] - (cen[:, 1] - size[:, 1] / 2.0)) / np.maximum(size[:, 1], 1.0), 0.0, 1.0)  # 0 bottom
        streak = noise.fbm(tx.Pw * np.array([1.0, 0.05, 1.0]), 2.5, 2, seed)
        k = 1.5 + h * 2.5 + (streak - 0.5) * 2.0
        lo_p = GLOW_GREEN if low is None else low
        hi_p = GLOW if high is None else high
        col = pick(lo_p, k * (0.5 if low is None else 1.0)) * (1.0 - h[:, None]) + pick(hi_p, k) * h[:, None]
        a = alpha[0] + (alpha[1] - alpha[0]) * np.clip(h * 1.1 + (streak - 0.5) * 0.8, 0.0, 1.0)
        a = np.where(h < 0.12 + (streak - 0.5) * 0.25, 0.0, a)          # a soft, ragged bottom edge
        g = rgba(col, glow_alpha * np.clip(0.35 + h * 0.75 + (streak - 0.5) * 0.6, 0.0, 1.0))
        g[a < 1.0] = 0.0
        return _out(tx, col, a, g)

    return paint


@_factory(True)
def mouth(seed=39):
    """The dark inside of a mouth or a hood."""
    return solid(MOUTH, idx=1.2, seed=seed, grain=0.6, bev=0.3, outline=0.0)


# ---------------------------------------------------------------- round 2


_TEXTURES = {}


def block_texture(name):
    """One of the mod's own 16x16 block textures (assets/dross/textures/block/<name>.png) as an RGBA float array."""
    if name not in _TEXTURES:
        im = Image.open(os.path.join(BLOCK_TEXTURES, name + ".png")).convert("RGBA")
        _TEXTURES[name] = np.asarray(im, dtype=float)
    return _TEXTURES[name]


def _sample(tex, u, v):
    h, w = tex.shape[:2]
    return tex[np.floor(v).astype(int) % h, np.floor(u).astype(int) % w]


def _box_frame(tx):
    """Each texel's position from its box's corner (the box's own frame, units) and the box's size."""
    size = _sizes(tx)
    return tx.P - (_centres(tx) - size / 2.0), size


@_factory(True)
def tiled(side, end=None, rime=None, rime_up=0.55, rime_front=True, darken=0.0, seed=41):
    """
    A block texture tiled over the boxes at one texel a unit, exactly as big as on the block itself: `side` on the
    faces along each box's own y (a log's grain runs along the box), `end` on its two ends (a log's rings), `rime` on
    the sides that look up or towards the model's front (the frostbitten log's rimed face points north on a standing
    log and up on a lying one). `darken` shades it down a little at random, box by box.
    """

    def paint(tx):
        q, size = _box_frame(tx)
        ny = np.abs(tx.N[:, 1])
        u = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0])
        v = size[:, 1] - q[:, 1]
        col = _sample(block_texture(side), u, v)[:, :3].copy()
        if end is not None:
            e = ny > 0.5
            col[e] = _sample(block_texture(end), q[e, 0], q[e, 2])[:, :3]
        if rime is not None:
            r = (ny < 0.5) & (tx.Nw[:, 1] > rime_up)
            if rime_front:
                r |= (ny < 0.5) & (tx.Nw[:, 2] < -0.75)
            col[r] = _sample(block_texture(rime), u[r], v[r])[:, :3]
        if darken > 0:
            k = noise.hash_ids(tx.cube_index, 0, seed)
            col = col * (1.0 - darken * k)[:, None]
        return _out(tx, col)

    return paint


@_factory(True)
def tiled_ground(top, side, seed=43, offset=8.0):
    """A ground block's textures (the hoarfrost Rimefall is covered in): `top` facing up, `side` on the sides (its
    top edge at each box's top), the side's bottom row on faces looking down. `offset`: the model's middle sits in
    the middle of a block, so the world's block edges are 8 units off its grid."""

    def paint(tx):
        q, size = _box_frame(tx)
        up = tx.Nw[:, 1] > 0.5
        u = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0])
        v = size[:, 1] - q[:, 1]
        col = _sample(block_texture(side), u, v)[:, :3].copy()
        col[up] = _sample(block_texture(top), tx.Pw[up, 0] + offset, tx.Pw[up, 2] + offset)[:, :3]
        down = tx.Nw[:, 1] < -0.5
        col[down] = _sample(block_texture(side), tx.Pw[down, 0], np.full(int(down.sum()), 15.0))[:, :3]
        return _out(tx, col)

    return paint


@_factory(False)
def shag(pal, seed=45, strand=1.0, ragged=1.0, frost_amount=0.2, outline=0.0, root=1.2, tip=3.8):
    """
    Long hanging fur (a skirt of hair, a mane, a beard): strands running down each box's own y, each its own shade,
    dark at the roots and light at the tips, frost on the tips, and a ragged bottom edge (cut out).
    """

    def paint(tx):
        q, size = _box_frame(tx)
        along = np.clip(1.0 - q[:, 1] / np.maximum(size[:, 1], 1.0), 0.0, 1.0)      # 0 at the root, 1 at the tip
        across = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0])
        col_id = np.floor(across / strand)
        side_id = np.round(tx.N[:, 0] * 2.0 + tx.N[:, 2] * 3.0)
        sh = noise.hash_float(col_id, tx.cube_index.astype(float), side_id, seed)
        wob = noise.fbm(tx.Pw * np.array([1.0, 0.2, 1.0]), 2.0, 2, seed + 1)
        k = root + (tip - root) * along + (sh - 0.5) * 1.8 + (wob - 0.5) * 1.0
        ends = np.abs(tx.N[:, 1]) > 0.5
        k = np.where(ends, root + 0.6, k)
        col = pick(pal, k)
        col = _outline(tx, col, outline)
        tips = (along > 0.72) & (_speck(tx.Pw, seed + 2) < frost_amount)
        col = np.where(tips[:, None], col * 0.35 + RIME[-2] * 0.65, col)
        a = np.full(tx.n, 255.0)
        if ragged > 0:
            jag = noise.hash_float(col_id, tx.cube_index.astype(float), side_id + 7.0, seed + 3) * 0.32 * ragged
            a = np.where((along > 1.0 - jag) & ~ends, 0.0, a)
            # The bottom end goes too: under a ragged edge it showed as a solid strip (the user saw it under the
            # Rimehorn's skirt).
            a = np.where(tx.N[:, 1] < -0.5, 0.0, a)
        return _out(tx, col, a)

    return paint


@_factory(lambda *a, **kw: kw.get('ragged', 0.0) <= 0 and kw.get('holes', 0.0) <= 0)
def worn_hide(pal, inner=None, seed=61, strand=2.4, frost_amount=0.25, ragged=0.0, holes=0.0, wear=0.0):
    """
    A fur cloak (the Huntsman's bearskin, round 4): fur on each plate's outer face, bare hide on the inner face
    (Shape.plate() turns a box's own +z towards the inside: GeckoLib's mirrored x flips the cross product). Optionally its end worn away (the user: "a worn off end"): a ragged bottom edge with deep tatters torn up into
    it (cut out), holes worn through near it (through both faces), and the fur thinning and fading towards it (`wear`).
    The end is each box's own bottom (its -y), so build the end plates with `up` pointing away from the end.
    """
    inner = LEATHER if inner is None else inner

    def paint(tx):
        cen = _centres(tx)
        q, size = _box_frame(tx)
        from_end = q[:, 1]                                       # units from the box's bottom (the cloak's end)
        s = noise.fbm(tx.Pw * np.array([1.0, 0.25, 1.0]), 2.0, 2, seed)
        sp = _speck(tx.Pw, seed + 3) - 0.5
        k = 2.6 + (s - 0.5) * strand + sp * 0.9 + bevel(tx.P, tx.N, cen, 0.9) * 0.5 + _shade(tx)
        worn = np.clip(1.0 - from_end / 16.0, 0.0, 1.0) * wear
        k = k + worn * 0.9 + (noise.fbm(tx.P, 1.6, 2, seed + 4) - 0.5) * worn * 1.6      # faded, patchy
        col = pick(pal, k)
        ki = 2.4 + (noise.fbm(noise.warp(tx.Pw, 1.5, 8.0, seed + 5), 5.0, 3, seed + 6) - 0.5) * 1.6 + _shade(tx)
        col = np.where((tx.N[:, 2] > 0.5)[:, None], pick(inner, ki + worn * 0.6), col)
        col = _outline(tx, col, 0.1)
        col = frost(tx, col, frost_amount, seed + 11)
        a = np.full(tx.n, 255.0)
        if ragged > 0:
            # A jagged edge along the cloak's width, and here and there a tatter torn much further up.
            across = tx.P * np.array([1.0, 0.0, 0.0])
            jag = noise.fbm(across, 1.3, 3, seed + 7) * ragged * 7.0
            strip = np.floor(q[:, 0] / 2.5)
            torn = noise.hash_float(strip, tx.cube_index.astype(float), 1.0, seed + 8)
            jag = jag + np.where(torn > 0.72, (torn - 0.72) / 0.28 * ragged * 11.0, 0.0)
            a = np.where((from_end < jag) & (tx.N[:, 1] > -0.5), 0.0, a)
            a = np.where(tx.N[:, 1] < -0.5, 0.0, a)              # (no solid strip under the ragged edge)
        if holes > 0:
            near = np.clip(1.0 - from_end / 26.0, 0.0, 1.0)
            hm = noise.fbm(tx.P * np.array([1.0, 1.0, 0.0]), 2.2, 2, seed + 9) > 1.0 - holes * near
            a = np.where(hm & (tx.edge > 1.0) & (np.abs(tx.N[:, 2]) > 0.5), 0.0, a)
        return _out(tx, col, a)

    return paint


@_factory(True)
def eye_plain(pal=HORN_DARK, seed=47):
    """An ordinary dark animal eye (no glow): glossy, with one bright glint near its top front corner."""

    def paint(tx):
        q, size = _box_frame(tx)
        k = 1.0 + (noise.fbm(tx.Pw, 1.0, 1, seed) - 0.5) * 0.8
        col = pick(pal, k)
        glint = (q[:, 1] > size[:, 1] - 1.0) & (np.abs(tx.N[:, 1]) < 0.5) & (_speck(tx.P, seed + 1) < 0.45)
        col = np.where(glint[:, None], np.array([214.0, 222.0, 230.0]), col)
        return _out(tx, col)

    return paint


def blend_pals(pals, t):
    """Which pair of palettes a texel's t (0..1, first to last palette) falls between, and how far along."""
    n = len(pals) - 1
    x = np.clip(t, 0.0, 1.0) * n
    i = np.clip(np.floor(x).astype(int), 0, max(0, n - 1))
    f = np.clip(x - i, 0.0, 1.0)[:, None]
    return i, f


@_factory(False)
def aurora_ice(pals, t_of, alpha=(120, 200), seed=49, cracks=0.3, rim=1.0, glow_alpha=110.0, crack_glow=1.0,
               bubbles=0.01):
    """
    See-through ice in the northern lights' colours: its colour runs through `pals` (palettes, dark to light) by
    t_of(Pw) (0..1 per texel); it glows softly all over, its cracks brightly.
    """

    def paint(tx):
        cen = _centres(tx)
        n = noise.fbm(tx.Pw, 4.0, 2, seed)
        k = 2.6 + (n - 0.5) * 2.0 + bevel(tx.P, tx.N, cen, 1.0) * 0.8 + _shade(tx) * 0.4
        a = alpha[0] + (alpha[1] - alpha[0]) * np.clip(n * 1.4 - 0.2, 0.0, 1.0)
        id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 1.6, 7.0, seed + 1), 9.0, seed + 2)
        pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 3) < cracks
        crack = pair & (border < 0.45)
        k = np.where(crack, 5.4, k)
        a = np.where(crack, 230.0, a)
        bub = _speck(tx.Pw, seed + 4) < bubbles
        k = np.where(bub, 5.8, k)
        if rim > 0:
            edge = tx.edge < 0.6
            k = np.where(edge, np.maximum(k, 4.2), k)
            a = np.where(edge, np.minimum(245.0, a + 50.0 * rim), a)
        t = np.clip(t_of(tx.Pw) + (noise.fbm(tx.Pw, 6.0, 2, seed + 5) - 0.5) * 0.12, 0.0, 1.0)
        if len(pals) == 1:
            col = pick(pals[0], k)
        else:
            i, f = blend_pals(pals, t)
            cols = np.stack([pick(p, k) for p in pals])          # (palettes, texels, 3)
            rows = np.arange(tx.n)
            col = cols[i, rows] * (1.0 - f) + cols[np.minimum(i + 1, len(pals) - 1), rows] * f
        g = rgba(np.clip(col * 1.15 + 20.0, 0, 255), glow_alpha * (0.55 + 0.45 * n))
        if crack_glow > 0:
            g[crack] = rgba(np.clip(col[crack] * 0.5 + 150.0, 0, 255), 255.0 * crack_glow)
        return _out(tx, col, a, g)

    return paint


# ---------------------------------------------------------------- round 3


@_factory(False)          # round: the box's corners are holes, so it hides nothing
def eye_disc(iris=GLOW, seed=51, pupil="round", pupil_size=0.3, pupil_pal=MOUTH, pupil_glow=None, rim=0.14,
             rim_pal=MOUTH, glow=255.0, glint=1, rings=None, stars=0.0, fibres=0.6, halo=None, swirl=None):
    """
    A round eye painted on the outward face of a flat box (a Shape.seg with `side` pointing up the head, its length
    along the head and its depth outwards, on the model's left or right side): the box's corners are holes, so the eye
    is a disc. From the outside in: a dark rim, the iris (`iris`, a palette dark to light, brightest by the pupil, with
    fibres running out from it; or `rings`, palettes from the outside in; or `swirl`, palettes it drifts between), the
    pupil ("round", "slit", "bar", "star" (a snowflake), "dot" or None) and `glint` bright glints (0, 1 or 2) up at its
    front. Its back and edges are the rim's colour. The Aurora Eel's eye studies (the user: its eyes "look not good").
      glow        how strongly the iris glows (0: not at all)
      pupil_glow  a palette: the pupil glows in it (an ember, a snowflake)
      stars       a share of the iris's texels that are glowing specks (a starry eye)
      halo        a palette: a glowing ring just inside the rim
    """

    def paint(tx):
        cen = _centres(tx)
        size = _sizes(tx)
        q = tx.P - cen
        u = -q[:, 0] / np.maximum(size[:, 0] / 2.0, 0.5)         # up the head, -1..1 (the box's x runs down)
        f = q[:, 1] / np.maximum(size[:, 1] / 2.0, 0.5)          # along it (forwards), -1..1
        px = 2.0 / np.maximum(size[:, 0], 1.0)                    # one texel, in u
        r = np.sqrt(u * u + f * f)
        flat = np.abs(tx.N[:, 2]) > 0.5
        front = flat & (tx.Nw[:, 0] * tx.Pw[:, 0] > 0.0)
        # The edges show only where the disc reaches them; the back is a dark disc.
        side_in = np.where(np.abs(tx.N[:, 0]) > 0.5, np.abs(f), np.abs(u)) < 0.55
        inside = np.where(flat, r <= 1.0, side_in)
        rim_col = pick(rim_pal, 1.0 + (_speck(tx.P, seed) - 0.5) * 0.8)
        first = (rings or swirl or [iris])[0]
        side_col = pick(first, 2.2) * 0.8           # the eyeball's side (it shows through see-through skin too)
        # The iris: brightest by the pupil, darker out to the rim, fibres running out from the middle.
        inner = 1.0 - rim
        pr = pupil_size if pupil in ("round", "dot") else 0.12
        t = np.clip((r - pr) / max(inner - pr, 0.05), 0.0, 1.0)
        ang = np.arctan2(u, f)
        fib = noise.hash_float(np.floor((ang + np.pi) / (2 * np.pi) * 30.0), 0, 0, seed + 1) - 0.5
        k = 4.7 - t * 2.6 + fib * 1.5 * fibres + (noise.fbm(tx.P, 2.0, 1, seed + 2) - 0.5) * 0.6
        if rings is not None:
            j = len(rings) - 1 - np.clip(np.floor(t * len(rings)), 0, len(rings) - 1).astype(int)
            cols = np.stack([pick(p, k) for p in rings])
            col = cols[j, np.arange(tx.n)]
        elif swirl is not None:
            w = noise.fbm(np.stack([u * 3.0, f * 3.0, r * 2.0], axis=1), 1.6, 2, seed + 3)
            i, fr = blend_pals(swirl, w)
            cols = np.stack([pick(p, k) for p in swirl])
            rows = np.arange(tx.n)
            col = cols[i, rows] * (1.0 - fr) + cols[np.minimum(i + 1, len(swirl) - 1), rows] * fr
        else:
            col = pick(iris, k)
        g = rgba(col, glow) if glow > 0 else np.zeros((tx.n, 4))
        base = col * (0.72 if glow > 0 else 1.0)
        if halo is not None:
            ring = (r > inner - 0.17) & (r <= inner)
            base = np.where(ring[:, None], pick(halo, 3.0), base)
            g[ring] = rgba(pick(halo, 4.0 + fib[ring]), 255.0)
        if stars > 0:
            st = (_speck(tx.P, seed + 4) < stars) & (r < inner - 0.1)
            base = np.where(st[:, None], np.array([235.0, 245.0, 255.0]), base)
            g[st] = np.array([235.0, 248.0, 255.0, 255.0])
        # The pupil.
        if pupil in ("round", "dot"):
            pm = r < pupil_size
        elif pupil == "slit":
            pm = (np.abs(f) < np.maximum(px * 0.55, 0.2 * np.sqrt(np.clip(1.0 - (u / 0.86) ** 2, 0.0, 1.0)))) \
                 & (np.abs(u) < 0.86)
        elif pupil == "bar":
            pm = (np.abs(u) < np.maximum(px * 0.55, 0.2 * np.sqrt(np.clip(1.0 - (f / 0.8) ** 2, 0.0, 1.0)))) \
                 & (np.abs(f) < 0.8)
        elif pupil == "star":
            arms = np.zeros(tx.n, dtype=bool)
            for a in (0.0, np.pi / 3.0, 2.0 * np.pi / 3.0):
                arms |= np.abs(u * np.cos(a) - f * np.sin(a)) < px * 0.6
            pm = (arms & (r < pupil_size)) | (r < px * 0.8)
        else:
            pm = np.zeros(tx.n, dtype=bool)
        base = np.where(pm[:, None], pick(pupil_pal, 1.0 + (_speck(tx.P, seed + 5) - 0.5) * 0.6), base)
        g[pm] = 0.0
        if pupil_glow is not None:
            base = np.where(pm[:, None], pick(pupil_glow, 3.0), base)
            g[pm] = rgba(pick(pupil_glow, 4.6 - r[pm] / max(pupil_size, 0.05) * 1.5), 255.0)
        # The rim, glints, and the back.
        rm = r > inner
        base = np.where(rm[:, None], rim_col, base)
        g[rm] = 0.0
        if glint:
            gl = (np.abs(u - 0.42) < px * 1.05) & (np.abs(f - 0.36) < px * 1.05)
            if glint > 1:
                gl |= (np.abs(u + 0.3) < px * 0.55) & (np.abs(f + 0.42) < px * 0.55)
            gl &= r <= inner
            base = np.where(gl[:, None], np.array([240.0, 250.0, 255.0]), base)
            g[gl] = np.array([245.0, 252.0, 255.0, 255.0])
        col = np.where(front[:, None], base, np.where(flat[:, None], rim_col, side_col))
        g[~front] = 0.0
        a = np.where(inside, 255.0, 0.0)
        g[~inside] = 0.0
        return _out(tx, col, a, g)

    return paint
