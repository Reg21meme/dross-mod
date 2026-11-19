"""
The Silverwood Giants' materials: painters (texels -> colour, alpha, glow) in the Rimefall kit's style
(tools/rimefall/rimekit/mats.py), with nothing of Rimefall's frost on them, and the Dross's own colours:

  - their skin pale and ashen with a cold blue tint, their hair near-black with blue-grey ends, and the mark of their
    people on it: bands and chevrons of deep blue woad (`woad`), the same on every one of them;
  - their war gear of the Dross's materials: dark bronze, blackstone, dark iron, hide and leather;
  - the Silverwood Forest's blocks painted on at one texel a unit, exactly as big as on the blocks (`mats.tiled`: the
    silverwood log on spear shafts and bark, duskstone and Silvermoss on a boulder), so they look made of the forest;
  - the electric blue of the Dross (DrossColors' palette: main 0x2E6BFF, deep cobalt 0x0B2A9E, highlight 0x7FA8FF) for
    everything that glows: their eyes, Stormvine, the seams of their bronze, the sign of their people.

Every painter here is opaque unless it cuts holes (a ragged hem, leaves, a crest's ends); only opaque boxes hide the
faces buried in them (rimekit/uvpack.py).
"""
import numpy as np

from kit import noise
from kit.paint import bevel, palette, pick, rgba
from rimekit import mats as M

# ---------------------------------------------------------------- palettes (dark to light)

# The Dross's electric blue (DrossColors' palette: deep cobalt, main, highlight, and the Stormvine block's own shades).
DROSS_BLUE = palette("#0b2a9e", "#1d4fe0", "#2e6bff", "#5e8fff", "#7fa8ff", "#a9c6ff", "#eef4ff")
DEEP_BLUE = palette("#06123f", "#0a1c63", "#0b2a9e", "#16399e", "#1d4fe0")

ASHEN = palette("#30323f", "#454857", "#5c5f6f", "#747888", "#8d91a0", "#a6aab7", "#bfc2cc")   # ashen, a cold tint
ASHEN_OLD = palette("#1a1d23", "#252930", "#31363e", "#3e444d", "#4d545d", "#5f666f", "#737a83")   # old, weathered dark
HAIR = palette("#0b0d13", "#13161f", "#1c202b", "#272c3a", "#363d4f", "#4c5468", "#6a7389")
FLEECE = palette("#5a5852", "#75726b", "#918d85", "#ada99f", "#c6c2b8", "#dad7ce", "#ecebe5")
FLEECE_SKIN = palette("#3b2b20", "#4e3a2b", "#634a37", "#7a5c45")                              # its hide underneath
HIDE = palette("#22170f", "#311f14", "#422b1c", "#553825", "#68462f", "#7e573b")
LEATHER = palette("#17110d", "#211913", "#2d2219", "#3a2c21", "#49382a", "#5b4636")
ROPE = palette("#2c261d", "#3e3527", "#524633", "#675940", "#7d6d4f")
TOOTH = palette("#57513f", "#736b55", "#8f876d", "#aba286", "#c5bda0", "#dcd6bc")
NAIL = palette("#2e2b29", "#423e3a", "#57524c", "#6d675f", "#837c73")
HORN = palette("#16130f", "#221d17", "#302820", "#40352a", "#524436", "#6a5845", "#857059")
MOUTH = palette("#05060c", "#0b0d17", "#131725", "#1c2234")
SOCKET = palette("#04050a", "#080b14", "#0e1220", "#151b2e")

DARK_IRON = palette("#0e1016", "#161920", "#1f232c", "#2a2f3a", "#383e4b", "#4b5262", "#646d7f")
BLACKSTONE = palette("#110e13", "#19151c", "#221d25", "#2c2630", "#38303c", "#463c4b", "#574b5d")
MAIL = palette("#0c0e13", "#171a21", "#252a33", "#363c48", "#4b5260", "#646c7c")

DARK_BRONZE = palette("#140d08", "#1f150c", "#2c1f12", "#3b2a18", "#4d3820", "#62482a", "#7b5c36")
BRONZE = palette("#2a1a0c", "#3d2812", "#55391a", "#6e4c24", "#8a6230", "#a87c40", "#c79a58")
VERDIGRIS = palette("#132a2a", "#1a3836", "#234843", "#2f5a52", "#3e6e63")
BONE = palette("#5d574b", "#7a7363", "#978f7c", "#b3ab96", "#cbc4ae", "#e0dac6")
WOAD = palette("#0d1640", "#152259", "#1c2e74", "#253b8c")                                  # the deep blue of their marks
CREST = palette("#04060f", "#080d1e", "#0d1430", "#121c45", "#18265c", "#203278")         # a helm's dark blue crest
GREY_HAIR = palette("#3a3d45", "#565a64", "#737884", "#9196a1", "#adb2bc", "#c8ccd4", "#e0e3e8")
WOOD_DARK = palette("#1b1611", "#262019", "#322a21", "#40362b", "#504436")

SILVERMOSS = palette("#0b202b", "#0e2633", "#143343", "#1a4050", "#214e5d", "#2a5e6b", "#386f7b", "#8aa3b3")
VINE_STEM = palette("#0a1430", "#0f1d44", "#14275a", "#1b336f", "#22408a")
ROOT = palette("#1f232d", "#2b303c", "#3a404d", "#4c5363", "#646c7f", "#7d879f")             # silverwood's own greys
SOIL = palette("#141210", "#1d1a17", "#27231f", "#322d27", "#3e3730")


# ---------------------------------------------------------------- the rimekit painters, without Rimefall's frost


def skin(pal=ASHEN, seed=3, mottle=1.0, scars=0.0, cracks=0.0, outline=0.1):
    return M.skin(pal, seed=seed, mottle=mottle, frost_amount=0.0, scars=scars, cracks=cracks, outline=outline)


def fur(pal, seed=1, strand=3.0, outline=0.12, along_box=False):
    return M.fur(pal, seed=seed, strand=strand, frost_amount=0.0, outline=outline, along_box=along_box)


def shag(pal=HAIR, seed=45, strand=1.0, ragged=1.0, root=1.2, tip=4.4):
    return M.shag(pal, seed=seed, strand=strand, ragged=ragged, frost_amount=0.0, root=root, tip=tip)


def cloth(pal, seed=25, folds=1.0, ragged=0.0, holes=0.0, outline=0.1):
    return M.cloth(pal, seed=seed, folds=folds, ragged=ragged, frost_amount=0.0, outline=outline, holes=holes)


def solid(pal, idx=2.5, seed=5, grain=1.0, bev=0.6, outline=0.14):
    return M.solid(pal, idx=idx, seed=seed, grain=grain, bev=bev, outline=outline)


def metal(pal=DARK_IRON, seed=21, shine=1.4, outline=0.2):
    return M.metal(pal, seed=seed, shine=shine, frost_amount=0.0, outline=outline)


def bone(pal=TOOTH, seed=19, cracks=0.15, outline=0.12):
    return M.bone(pal, seed=seed, frost_amount=0.0, cracks=cracks, outline=outline)


def wood(pal, seed=23, plank=4.0, axis=1, outline=0.14):
    return M.wood(pal, seed=seed, plank=plank, axis=axis, frost_amount=0.0, outline=outline)


def bark(pal=ROOT, seed=17, knots=0.08, outline=0.12):
    return M.bark(pal, seed=seed, rime=0.0, knots=knots, outline=outline)


def tiled(side, end=None, darken=0.0, seed=41):
    """One of the mod's block textures at one texel a unit (rimekit's `tiled`, with no rime)."""
    return M.tiled(side, end, rime=None, darken=darken, seed=seed)


def mouth(seed=39):
    return solid(MOUTH, idx=1.4, seed=seed, grain=0.6, bev=0.3, outline=0.0)


def socket(seed=37):
    return solid(SOCKET, idx=1.2, seed=seed, grain=0.5, bev=0.2, outline=0.0)


# ---------------------------------------------------------------- its own painters


def _local(tx):
    """Each texel's offset from its box's middle (the box's own frame) and the box's half sizes."""
    cen = M._centres(tx)
    size = M._sizes(tx)
    return tx.P - cen, np.maximum(size / 2.0, 0.5)


def eye(pal=DROSS_BLUE, seed=51):
    """
    One of a giant's eyes: a small box glowing electric blue, full-bright, its face that looks forward brightest in its
    middle and top (a hot core, a glint) and darker at its outer corner; its other faces the dark of the socket.
    """

    def paint(tx):
        q, half = _local(tx)
        front = tx.Nw[:, 2] < -0.5
        u = q[:, 0] / half[:, 0]
        v = q[:, 1] / half[:, 1]
        outer = np.abs(tx.Pw[:, 0]) > np.abs(M._centres(tx)[:, 0])          # towards the side of the head
        k = 3.6 + v * 0.8 - np.where(outer, 0.9, 0.0) + (noise.fbm(tx.P, 1.0, 1, seed) - 0.5) * 0.4
        k = np.where((np.abs(u) < 0.5) & (v > 0.0), 6.0, k)                 # the hot core
        col = pick(pal, k)
        g = rgba(col, 255.0)
        dark = pick(SOCKET, np.full(tx.n, 1.0))
        col = np.where(front[:, None], col, dark)
        g[~front] = rgba(pick(pal, np.full(int((~front).sum()), 1.0)), 120.0)
        return M._out(tx, col, None, g)

    paint.opaque = True
    return paint


def woad(pal=ASHEN, marks=WOAD, seed=3, mottle=0.6, scars=0.0, mode="rings", period=6.0, width=1.2, low=None,
         high=None, outline=0.08, bands=(0.3, 0.55), fade=0.78):
    """
    Their skin with the mark of their people painted on in woad: `mode` "rings" (bands round each box along its own y,
    only within `bands`, a share of the box's length from its top: an arm's, a leg's) or "chevrons" (V's down the
    chest, pointing down its middle); only between world heights `low` and `high` if given. `fade`: how much of it is
    still there. Never glowing: the blue of their eyes is the only light on them.
    """
    base = M.skin(pal, seed=seed, mottle=mottle, frost_amount=0.0, scars=scars, outline=outline)

    def paint(tx):
        out = base(tx)
        q, half = _local(tx)
        wob = (noise.fbm(tx.Pw, 3.0, 2, seed + 7) - 0.5) * 1.2
        if mode == "rings":
            from_top = (half[:, 1] - q[:, 1]) / (2.0 * half[:, 1])
            along = q[:, 1] + half[:, 1] + wob * 0.5
            m = ((along % period) < width) & (from_top >= bands[0]) & (from_top <= bands[1])
        else:
            t = np.abs(tx.Pw[:, 0]) * 0.55 + tx.Pw[:, 1] + wob
            m = ((t % period) < width) & (tx.Nw[:, 2] < -0.2)          # (on the chest's front only)
        if low is not None:
            m &= tx.Pw[:, 1] >= low
        if high is not None:
            m &= tx.Pw[:, 1] <= high
        m &= np.abs(tx.N[:, 1]) < 0.5
        m &= noise.fbm(tx.Pw, 2.0, 2, seed + 9) < fade                      # worn in places
        k = 2.2 + (noise.fbm(tx.Pw, 1.5, 2, seed + 11) - 0.5) * 1.6
        out["color"] = np.where(m[:, None], pick(marks, k) * 0.8 + out["color"] * 0.2, out["color"])
        return out

    paint.opaque = True
    return paint


def emblem(centre, normal, up=(0.0, 1.0, 0.0), radius=10.0, pal=DARK_BRONZE, glow=DROSS_BLUE, seed=91,
           ring=(0.6, 0.76), spear=0.12, shine=1.2, verdigris=0.0):
    """
    The sign of their people on a round face (a shield, a breastplate's disc, a belt's boss), whatever boxes make it:
    on the faces that look along `normal`, round `centre` (world, posed rest), a glowing ring with a spear through it,
    point up (they spear men like fish), on dark bronze; everything else plain bronze. `verdigris`: green corrosion
    (an old warrior's).
    """
    c0 = np.asarray(centre, dtype=float)
    nrm = np.asarray(normal, dtype=float)
    nrm = nrm / np.linalg.norm(nrm)
    upv = np.asarray(up, dtype=float) - np.dot(up, nrm) * nrm
    upv = upv / np.linalg.norm(upv)
    side = np.cross(upv, nrm)

    def paint(tx):
        cen = M._centres(tx)
        k = 2.6 + bevel(tx.P, tx.N, cen, 1.0) * shine + (noise.fbm(tx.Pw, 3.0, 2, seed) - 0.5) * 1.2 + M._shade(tx)
        col = M._outline(tx, pick(pal, k), 0.14)
        if verdigris > 0:
            vg = noise.fbm(tx.Pw, 3.5, 3, seed + 3) < verdigris
            col = np.where(vg[:, None], pick(VERDIGRIS, k - 0.5), col)
        g = np.zeros((tx.n, 4))
        face = tx.Nw @ nrm > 0.5
        d = tx.Pw - c0
        u = d @ side / radius
        v = d @ upv / radius
        r = np.sqrt(u * u + v * v)
        mark = face & (((r > ring[0]) & (r < ring[1]))
                       | ((np.abs(u) < spear) & (v > -0.86) & (v < 0.5))
                       | ((np.abs(u) < 0.36 - (v - 0.5) * 0.9) & (v >= 0.5) & (v < 0.88)))
        gk = 3.4 + (noise.fbm(tx.Pw, 2.0, 2, seed + 1) - 0.5) * 1.4
        col = np.where(mark[:, None], pick(glow, gk - 1.2), col)
        g[mark] = rgba(pick(glow, gk[mark]), 255.0)
        return M._out(tx, col, None, g)

    paint.opaque = True
    return paint


def blade(pal=DARK_BRONZE, glow=DROSS_BLUE, seed=95, edge=1.6, shine=1.6):
    """A blade of dark bronze (a spear's leaf): its flat faces bronze, its thin faces (the cutting edges, at most `edge`
    units across) glowing electric blue, like the seams of their armour."""

    def paint(tx):
        cen = M._centres(tx)
        k = 2.8 + bevel(tx.P, tx.N, cen, 1.0) * shine + (noise.fbm(tx.Pw, 3.0, 2, seed) - 0.5) * 1.0 + M._shade(tx)
        col = M._outline(tx, pick(pal, k), 0.12)
        thin = np.minimum(tx.FW, tx.FH) <= edge
        gk = 3.6 + (noise.fbm(tx.Pw, 1.5, 1, seed + 1) - 0.5) * 1.2
        col = np.where(thin[:, None], pick(glow, gk - 1.0), col)
        g = np.zeros((tx.n, 4))
        g[thin] = rgba(pick(glow, gk[thin]), 255.0)
        return M._out(tx, col, None, g)

    paint.opaque = True
    return paint


def mossy_stone(side="duskstone", moss_pal=SILVERMOSS, seed=93, mossy=0.45):
    """A block of the Dross's stone at one texel a unit (duskstone), Silvermoss growing over whatever faces up."""
    base = M.tiled(side, side, rime=None)

    def paint(tx):
        out = base(tx)
        n = noise.fbm(tx.Pw, 2.5, 3, seed)
        top = (tx.Nw[:, 1] > 0.35) & (n < mossy + tx.Nw[:, 1] * 0.25)
        k = 3.4 + (n - 0.5) * 3.0 + (M._speck(tx.Pw, seed + 1) - 0.5)
        out["color"] = np.where(top[:, None], pick(moss_pal, k), out["color"])
        return out

    paint.opaque = True
    return paint


def fleece(pal=FLEECE, seed=71, curl=2.2, skin_pal=FLEECE_SKIN, outline=0.06):
    """A sheep's fleece: tight curls (each a little lighter in its middle), dirty grey-white, its hide on the side
    facing in (a box's own +z, the way Shape.plate turns it) and on cut edges thinner than 2 units."""

    def paint(tx):
        cen = M._centres(tx)
        id1, _id2, border, f1, _c = noise.voronoi(tx.Pw, curl, seed, jitter=0.9)
        shade = noise.hash_ids(id1, 0, seed + 1) - 0.5
        k = 3.6 + shade * 1.4 - f1 * 0.9 + bevel(tx.P, tx.N, cen, 1.0) * 0.4 + M._shade(tx) * 0.8
        k = np.where(border < 0.25, k - 1.5, k)
        k = k + (noise.fbm(tx.Pw, 6.0, 2, seed + 2) - 0.5) * 1.6       # dirtier patches
        col = pick(pal, k)
        col = M._outline(tx, col, outline)
        return M._out(tx, col)

    paint.opaque = True
    return paint


def vine(stem=VINE_STEM, glow=DROSS_BLUE, seed=73, node_every=3.0, leafy=0.45):
    """
    Stormvine (the Silverwood Forest's glowing vine): a dark blue stem with bright electric-blue nodes and leaves along
    it (along each box's own y), glowing. `leafy`: how much of the stem is leaf.
    """

    def paint(tx):
        q, half = _local(tx)
        along = q[:, 1] + half[:, 1]
        node = np.abs((along % node_every) - node_every / 2.0) < 0.6
        k = 2.0 + (noise.fbm(tx.Pw, 2.0, 2, seed) - 0.5) * 1.6
        col = pick(stem, k)
        lf = node | (noise.hash_float(np.floor(along), M._centres(tx)[:, 0].round(), tx.cube_index.astype(float),
                                      seed + 1) < leafy * 0.5)
        gk = 2.6 + noise.hash_float(np.floor(along), tx.cube_index.astype(float), 0, seed + 2) * 3.0
        col = np.where(lf[:, None], pick(glow, gk - 1.0), col)
        g = np.zeros((tx.n, 4))
        g[lf] = rgba(pick(glow, gk[lf]), 255.0)
        g[~lf] = rgba(pick(glow, np.full(int((~lf).sum()), 0.6)), 60.0)
        return M._out(tx, col, None, g)

    paint.opaque = True
    return paint


def vine_leaf(glow=DROSS_BLUE, seed=75):
    """A Stormvine leaf or bud: electric blue, glowing, brightest at its middle."""

    def paint(tx):
        q, half = _local(tx)
        d = np.max(np.abs(q) / half, axis=1)
        k = 5.0 - d * 2.6 + (noise.fbm(tx.Pw, 1.5, 1, seed) - 0.5) * 1.0
        col = pick(glow, k - 1.0)
        return M._out(tx, col, None, rgba(pick(glow, k), 255.0))

    paint.opaque = True
    return paint


def moss(pal=SILVERMOSS, seed=77, flowers=0.0):
    """Silvermoss (the forest floor's moss): dark teal tufts, a few pale silver glints; `flowers` a share of glowing
    pale-blue Wispbloom specks."""

    def paint(tx):
        cen = M._centres(tx)
        n = noise.fbm(tx.Pw, 2.0, 3, seed)
        sp = M._speck(tx.Pw, seed + 1)
        k = 3.6 + (n - 0.5) * 3.0 + (sp - 0.5) * 1.0 + bevel(tx.P, tx.N, cen, 0.8) * 0.4 + M._shade(tx) * 0.9
        k = np.where(M._speck(tx.Pw, seed + 2) < 0.03, len(pal) - 1.0, k)
        col = M._outline(tx, pick(pal, k), 0.06)
        g = np.zeros((tx.n, 4))
        if flowers > 0:
            fl = (M._speck(tx.Pw, seed + 3) < flowers) & (tx.Nw[:, 1] > -0.2)
            col = np.where(fl[:, None], np.array([200.0, 219.0, 255.0]), col)
            g[fl] = np.array([200.0, 219.0, 255.0, 255.0])
        return M._out(tx, col, None, g)

    paint.opaque = True
    return paint


def hanging_moss(pal=SILVERMOSS, seed=79, ragged=1.0):
    """Long strands of moss and fine roots hanging down (a beard of the forest): shag in Silvermoss's teal, root-grey
    strands among them."""
    base = M.shag(pal, seed=seed, strand=1.0, ragged=ragged, frost_amount=0.0, root=1.0, tip=5.0)

    def paint(tx):
        out = base(tx)
        q, half = _local(tx)
        across = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0])
        rootish = noise.hash_float(np.floor(across), tx.cube_index.astype(float), 3.0, seed + 5) < 0.3
        along = 1.0 - (q[:, 1] + half[:, 1]) / (2.0 * half[:, 1])
        out["color"] = np.where(rootish[:, None], pick(ROOT, 1.0 + along * 3.0), out["color"])
        return out

    paint.opaque = False
    return paint


def seamed(pal=DARK_IRON, glow=DROSS_BLUE, seed=81, seam=0.6, rivets=True, cracks=0.0, shine=1.2, outline=0.16,
           broken=0.45, faces="all"):
    """
    Their war gear: dark bronze (or blackstone, `pal`) with electric-blue seams glowing along its plates' edges
    (`seam`: how wide, units), broken here and there (`broken`: the share of a seam that's dark, so it reads as light
    seeping through the joins, not a neon outline), rivets near them, and optionally glowing cracks across it.
    `faces`: "all", or "front" (only the faces that look forward) or "side" (only those looking left or right).
    """

    def paint(tx):
        cen = M._centres(tx)
        k = 2.6 + bevel(tx.P, tx.N, cen, 1.0) * shine + (noise.fbm(tx.Pw, 3.0, 2, seed) - 0.5) * 1.2 + M._shade(tx)
        k = np.where(M._speck(tx.Pw, seed + 1) < 0.02, len(pal) - 1.0, k)
        col = pick(pal, k)
        col = M._outline(tx, col, outline)
        g = np.zeros((tx.n, 4))
        edge = (tx.edge < seam) & (np.minimum(tx.FW, tx.FH) >= 3.0)
        edge &= noise.fbm(tx.Pw * np.array([1.0, 1.0, 1.0]), 2.5, 2, seed + 6) > broken
        if faces == "front":
            edge &= tx.Nw[:, 2] < -0.5
        elif faces == "side":
            edge &= np.abs(tx.Nw[:, 0]) > 0.5
        if rivets:
            riv = (tx.edge > seam) & (tx.edge < seam + 1.2) & (((tx.A + tx.B) % 4.0) < 0.9)
            col = np.where(riv[:, None], pick(pal, np.full(tx.n, len(pal) - 1.5)), col)
        if cracks > 0:
            id1, id2, border, _f, _c = noise.voronoi(noise.warp(tx.Pw, 1.6, 6.0, seed + 2), 6.0, seed + 3)
            pair = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 4) < cracks
            edge = edge | (pair & (border < 0.35) & (tx.edge > 1.0))
        gk = 3.0 + (noise.fbm(tx.Pw, 2.5, 2, seed + 5) - 0.5) * 2.0
        col = np.where(edge[:, None], pick(glow, gk - 1.0), col)
        g[edge] = rgba(pick(glow, gk[edge]), 255.0)
        return M._out(tx, col, None, g)

    paint.opaque = True
    return paint


def mail(pal=MAIL, seed=83):
    """Mail: rows of iron rings (a ring every 2 units, each row offset by one), dark between them."""

    def paint(tx):
        a = np.floor(tx.A)
        b = np.floor(tx.B)
        row = b % 2.0
        ring = ((a + row) % 2.0) < 1.0
        k = np.where(ring, 3.4, 1.2) + (noise.fbm(tx.Pw, 3.0, 2, seed) - 0.5) * 1.2 + M._shade(tx) * 0.8
        k = np.where(ring & (row < 1.0) & (M._speck(tx.Pw, seed + 1) < 0.25), k + 1.2, k)
        return M._out(tx, pick(pal, k))

    paint.opaque = True
    return paint


def rune_stone(side="duskstone", glow=DROSS_BLUE, seed=85, inset=2.0, width=1.0):
    """A block of the Dross's own stone (duskstone, at one texel a unit), a band of electric-blue rune carved round
    each of its bigger faces (`inset` units in from the edge) with a stroke down its middle, glowing."""
    base = M.tiled(side, side, rime=None)

    def paint(tx):
        out = base(tx)
        big = np.minimum(tx.FW, tx.FH) >= 2.0 * inset + 3.0
        band = big & (tx.edge >= inset) & (tx.edge < inset + width)
        stroke = big & (np.abs(tx.A - tx.FW / 2.0) < 0.5 * width) & (tx.edge >= inset)
        diag = big & (np.abs((tx.A - tx.FW / 2.0) - (tx.B - tx.FH / 2.0) * 0.6) < 0.5) & (tx.edge >= inset + 1.0)
        rune = band | stroke | diag
        gk = 3.4 + (noise.fbm(tx.Pw, 2.0, 2, seed) - 0.5) * 1.6
        out["color"] = np.where(rune[:, None], pick(glow, gk - 1.2), out["color"] * 0.92)
        g = out["glow"]
        g[rune] = rgba(pick(glow, gk[rune]), 255.0)
        return out

    paint.opaque = True
    return paint


def tiled_cut(texture, seed=87, darken=0.0):
    """A block texture with see-through holes (silverwood leaves) at one texel a unit: its clear texels are holes."""

    def paint(tx):
        q, size = M._box_frame(tx)
        u = np.where(np.abs(tx.N[:, 0]) > 0.5, q[:, 2], q[:, 0])
        v = np.where(np.abs(tx.N[:, 1]) > 0.5, q[:, 2], size[:, 1] - q[:, 1])
        t = M._sample(M.block_texture(texture), u, v)
        col = t[:, :3] * (1.0 - darken * noise.hash_ids(tx.cube_index, 0, seed))[:, None]
        a = np.where(t[:, 3] > 128, 255.0, 0.0)
        return M._out(tx, col, a)

    paint.opaque = False
    return paint


def soil(pal=SOIL, seed=89, moss_pal=SILVERMOSS, mossy=0.35):
    """Earth still clinging to the roots of a club pulled out of the ground: crumbly dark soil, Silvermoss on top."""

    def paint(tx):
        cen = M._centres(tx)
        n = noise.fbm(tx.Pw, 1.5, 3, seed)
        k = 2.2 + (n - 0.5) * 2.4 + bevel(tx.P, tx.N, cen, 1.0) * 0.6 + M._shade(tx)
        col = pick(pal, k)
        top = (tx.Nw[:, 1] > 0.3) & (noise.fbm(tx.Pw, 2.5, 2, seed + 1) < mossy + 0.2)
        col = np.where(top[:, None], pick(moss_pal, 3.5 + (n - 0.5) * 3.0), col)
        return M._out(tx, M._outline(tx, col, 0.08))

    paint.opaque = True
    return paint
