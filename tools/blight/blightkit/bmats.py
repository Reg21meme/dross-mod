"""
The Blightwood candidates' materials: the Plague Necromancer's colours (BOSS_GUIDE.md: poison green, decaying reds and
bone beige), plus Blightwood's own moulds, bog mud, burlap and straw. Never the Dross's electric blue.

Each factory returns a painter like the Rimefall kit's (tools/rimefall/rimekit/mats.py): texels -> colour, alpha and
glow. Most reuse the fire kit's painters (tools/fire/firekit/fmats.py) with plague palettes: its lava painter with a
poison palette is glowing ooze, its crust painter rotten flesh split by glowing cracks, its glass see-through slime.
"""
from kit.paint import palette
from firekit import fmats as F
from rimekit import mats as M

# ---------------------------------------------------------------- palettes (dark to light)

TOXIC = palette("#1f4a08", "#336f0e", "#4f9a16", "#72c422", "#9ee43e", "#cdf68a", "#f0ffd0")
TOXIC_DIM = palette("#16300a", "#22460f", "#2f5f14", "#3f7a1a", "#549622")
OOZE = palette("#203e08", "#30600c", "#4a8a12", "#6cb21c", "#94d838", "#c4f07a")
ROT = palette("#1c0807", "#2b0d0b", "#3c1310", "#4e1a14", "#61221a", "#7a3022")          # rotting blightcap red
ROT_PALE = palette("#4a1c16", "#64281e", "#7e3828", "#984c36", "#ae644a", "#c48064")
FLESH = palette("#3a1a14", "#52261c", "#6c3426", "#864634", "#a05c46", "#b8765c")
BONE = palette("#6a604c", "#857a62", "#a0957a", "#bab092", "#cec6aa", "#e2dcc4")          # blightcap stem beige
BONE_DARK = palette("#3a3426", "#4e4634", "#645a44", "#7a6f56", "#90846a")
MOLD = palette("#1a1f14", "#262d1d", "#343d27", "#444f32", "#58653f", "#70804f")
MOLD_PALE = palette("#5a6646", "#707e56", "#879668", "#9eae7c", "#b6c494", "#ccd8ae")
BOG = palette("#120f08", "#1c180d", "#272112", "#332c18", "#41381f", "#524628")
SLUDGE = palette("#1c1b0f", "#252313", "#2f2c17", "#3a351b", "#453f20", "#544c28")
CAP_GREEN = palette("#14280a", "#1d3705", "#2a4f08", "#376a0b", "#4a8412", "#62a01e")      # blightcap green
SPOT = palette("#a89c78", "#c2b690", "#d6cba6", "#e0d5ad", "#ece4c4")
BURLAP = palette("#2e2414", "#3e321c", "#514226", "#655331", "#7a663d", "#90794a")
STRAW = palette("#4a3c16", "#665420", "#836c2a", "#a08636", "#bca046", "#d4ba60")
WOOD_ROT = palette("#130f0b", "#1e1811", "#2a2218", "#372d20", "#463a29", "#584934")
LEECH = palette("#120807", "#1e0d0b", "#2c1310", "#3b1a15", "#4c231b", "#5f2e23")
GATOR = palette("#3e4434", "#525a46", "#687158", "#80896c", "#99a284", "#b4bc9e")          # a pale sewer gator
GATOR_BELLY = palette("#7e7a62", "#958f74", "#aca688", "#c2bc9e", "#d6d0b4")
MANGE = palette("#1e130d", "#2b1c13", "#3a271a", "#4b3322", "#5e412c", "#735237")
SNAIL = palette("#2a2a20", "#38372a", "#474636", "#585643", "#6b6852", "#807c63")
TICK = palette("#2a0a08", "#40100c", "#581712", "#722019", "#8c2c22", "#a63c2e")
WITCH_ROBE = palette("#120d10", "#1b1418", "#251c21", "#30252b", "#3d3036", "#4c3c43")
RUST = F.RUST
IRON = F.IRON
FLY = palette("#07090a", "#0c1012", "#12181a", "#1a2224", "#232e2f", "#2e3c3b")
GLASS_SLIME = palette("#1a4a10", "#245e16", "#30741e", "#3e8c28", "#58a83a", "#86c860")
GLASS_WING = palette("#3a4a30", "#4e6240", "#647a52", "#7c9468", "#98ae84", "#b8c8a6")
EYE_TOXIC = palette("#3a7a0a", "#5aa814", "#80d024", "#aaec4a", "#d4fa94", "#f4ffe0")
EYE_RED = palette("#5a0806", "#8a120a", "#b82012", "#e03a1e", "#ff6a40", "#ffb090")
EYE_AMBER = palette("#6a4008", "#94600e", "#c08a1a", "#e2b032", "#f8d470", "#fff2c8")
FEATHER_BLACK = palette("#060606", "#0d0c0c", "#151313", "#1e1b1a", "#292523", "#36312e")


# ---------------------------------------------------------------- materials


def ooze(seed=301, crust=0.0, crust_pal=BOG, bright=1.0):
    """Glowing poison ooze running down (the fire kit's molten rock in poison green)."""
    return F.lava(pal=OOZE, seed=seed, crust=crust, crust_pal=crust_pal, bright=bright)


def rot_flesh(seed=303, cracks=0.35, cell=6.0, width=0.55, pal=ROT, glow_pal=TOXIC, hot=0.8):
    """Rotten red flesh in plates, the seams between them glowing poison green."""
    return F.crust(pal=pal, glow_pal=glow_pal, seed=seed, cell=cell, cracks=cracks, width=width, hot=hot)


def skin(pal, seed=305, mottle=1.2, scars=0.0, cracks=0.0, crack_pal=None):
    return F.skin(pal, seed=seed, mottle=mottle, scars=scars, cracks=cracks, crack_pal=crack_pal)


def bone(pal=BONE, seed=307, cracks=0.2):
    return F.bone(pal, seed=seed, cracks=cracks)


def fur(pal, seed=309, strand=3.0):
    return F.fur(pal, seed=seed, strand=strand)


def shag(pal, seed=311, strand=1.0, ragged=1.0):
    return F.shag(pal, seed=seed, strand=strand, ragged=ragged)


def cloth(pal, seed=313, folds=1.0, ragged=0.0, holes=0.0):
    return F.cloth(pal, seed=seed, folds=folds, ragged=ragged, holes=holes)


def wood(pal=WOOD_ROT, seed=315, plank=4.0, axis=0):
    return F.wood(pal, seed=seed, plank=plank, axis=axis)


def rotwood(seed=317, cracks=0.15):
    """Rotten black wood, the cracks in its grain glowing faintly green."""
    return F.charred(pal=WOOD_ROT, seed=seed, cracks=cracks, glow_pal=TOXIC_DIM, ash_top=0.0, hot=0.6)


def metal(pal=IRON, seed=319, rust=0.6):
    return F.metal(pal, seed=seed, rust=rust)


def chitin(pal, seed=321, gloss=1.4, rim=0.0, seams=0.0):
    """Glossy shell; `rim` and `seams` glow poison green."""
    return F.chitin_glow(pal=pal, seed=seed, rim_pal=TOXIC, rim=rim, gloss=gloss, seams=seams)


def beetle(pal=FLY, seed=323, seams=0.0):
    return F.beetle(pal=pal, seed=seed, seams=seams, seam_pal=TOXIC)


def slime(seed=325, alpha=(70, 150), glow_alpha=60.0, crack_glow=0.4):
    """See-through green slime with a soft glow inside."""
    return F.glass(GLASS_SLIME, alpha=alpha, seed=seed, cracks=0.12, bubbles=0.04, glow_pal=TOXIC,
                   glow_alpha=glow_alpha, crack_glow=crack_glow, rim=0.6)


def wing(seed=327, alpha=(60, 140)):
    """A thin see-through insect wing, its veins darker."""
    return F.glass(GLASS_WING, alpha=alpha, seed=seed, cracks=0.18, bubbles=0.0, rim=1.0)


def membrane(pal=ROT, seed=329, veins=1.0, hot=0.4, ragged=0.0, holes=0.0):
    return F.membrane(pal=pal, seed=seed, vein_pal=TOXIC, veins=veins, hot=hot, ragged=ragged, holes=holes)


def glowing(pal=TOXIC, seed=331, base_pal=None):
    return M.glowing(pal, seed=seed, base_pal=base_pal)


def eye(iris=EYE_TOXIC, pupil="slit", seed=333, glow=230.0, pupil_size=0.32):
    """A round eye painted on the outward face of a flat box (see rimekit's eye_disc)."""
    return M.eye_disc(iris=iris, seed=seed, pupil=pupil, pupil_size=pupil_size, glow=glow)


def eye_dot(pal=EYE_TOXIC, seed=335):
    """A small glowing eye (a box that glows all over)."""
    return M.eye(pal, seed=seed, pupil=False)


def eye_plain(seed=337):
    return M.eye_plain(seed=seed)


def mouth(seed=339):
    return M.mouth(seed)


def teeth(seed=341):
    """Yellowed teeth (bone beige, not the Rimefall kit's icicle teeth)."""
    return F.solid(BONE, idx=3.6, seed=seed, grain=0.8)


def solid(pal, seed=343, idx=2.5, grain=1.0):
    return F.solid(pal, seed=seed, idx=idx, grain=grain)


def cap(seed=345):
    """A Blightwood mushroom's cap: the Blightcap block's own texture, tiled at one texel a unit."""
    return M.tiled("blightcap_block", seed=seed)


def rotten_cap(seed=347):
    return M.tiled("rotting_blightcap", seed=seed)


def stem(seed=349):
    return M.tiled("blightcap_stem", seed=seed)


def gills(seed=351):
    return M.tiled("blightcap_inside", seed=seed)


def sporepod(seed=353):
    return M.tiled("sporepod", seed=seed)


def sludge(seed=355):
    return M.tiled("sewer_sludge", seed=seed)


def scales(pal, seed=357, cell=3.0, belly_pal=None):
    return F.scales(pal, seed=seed, cell=cell, belly_pal=belly_pal)


def feathers(pal=FEATHER_BLACK, seed=359):
    return F.feathers(pal, seed=seed)


# ---------------------------------------------------------------- painters of our own

import numpy as np  # noqa: E402

from kit import noise  # noqa: E402
from kit.paint import bevel, pick, rgba  # noqa: E402
from rimekit.mats import _centres, _factory, _out, _outline, _shade, _speck  # noqa: E402


@_factory(True)
def spotted(pal, spot_pal=SPOT, seed=371, cell=6.0, radius=0.32, density=0.6, mottle=1.0, glow_pal=None,
            glow_alpha=230.0, up_only=True, outline=0.1):
    """
    Mottled skin or a mushroom's cap with round spots (Voronoi cells, `density` of them spotted, each spot `radius` of
    a cell across), placed by world position so they run on across the boxes. `up_only`: no spots on faces looking
    down. `glow_pal`: the spots glow (pustules, boils).
    """

    def paint(tx):
        cen = _centres(tx)
        m = noise.fbm(noise.warp(tx.Pw, 1.5, 8.0, seed), 5.0, 3, seed + 1)
        k = (2.6 + (m - 0.5) * 2.0 * mottle + (_speck(tx.Pw, seed + 2) - 0.5) * 0.5
             + bevel(tx.P, tx.N, cen, 1.0) * 0.5 + _shade(tx))
        col = pick(pal, k)
        id1, _id2, _border, f1, _c = noise.voronoi(tx.Pw, cell, seed + 3)
        keep = noise.hash_ids(id1, id1, seed + 4) < density
        spot = keep & (f1 < radius * cell)
        if up_only:
            spot &= tx.Nw[:, 1] > -0.3
        sk = 3.0 + (_speck(tx.Pw, seed + 5) - 0.5) * 1.0 - f1 / (radius * cell) * 1.4
        col = np.where(spot[:, None], pick(spot_pal, sk), col)
        col = _outline(tx, col, outline)
        glow = None
        if glow_pal is not None:
            g = rgba(pick(glow_pal, sk + 0.8), glow_alpha)
            glow = np.where(spot[:, None], g, np.zeros((tx.n, 4)))
        return _out(tx, col, None, glow)

    return paint


def cap_paint(seed=373, pal=CAP_GREEN, spot_pal=SPOT, cell=5.5, density=0.55):
    """A Blightcap's cap painted (rather than tiled): deep poison green with beige spots."""
    return spotted(pal, spot_pal, seed=seed, cell=cell, radius=0.3, density=density, mottle=0.9)


def rot_cap_paint(seed=375):
    """A rotting cap: decaying red with pale sickly spots."""
    return spotted(ROT, BONE, seed=seed, cell=5.0, radius=0.28, density=0.5, mottle=1.1)


def pustules(pal, seed=377, cell=5.0, radius=0.3, density=0.45, mottle=1.2):
    """Skin with glowing green boils."""
    return spotted(pal, TOXIC, seed=seed, cell=cell, radius=radius, density=density, mottle=mottle, glow_pal=TOXIC,
                   up_only=False)
