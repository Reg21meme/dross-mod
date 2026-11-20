"""
The elemental knights' palettes and materials, one family per element (never the Dross's electric blue):

  fire     blackened iron and obsidian split by glowing lava, charred cloth burning at its hem, brass
  poison   Sir Mildew's: rusted plate furred with mould, Blightcaps, rotting red cloth, poison-green light (bmats)
  thunder  copper gone green with verdigris, storm-grey cloth, barnacles, pale yellow-white lightning
  earth    carved stone and deepslate split by amber light, moss, amethyst, goat horn

Each factory returns a painter like the Rimefall kit's (tools/rimefall/rimekit/mats.py); most reuse the fire kit's
(tools/fire/firekit/fmats.py) with the element's palette: its crust painter is any plated surface with glowing seams
(magma armour, cracked stone, copper crackling with lightning), its glass anything see-through (crystals, portholes).
"""
from kit.paint import palette
from firekit import fmats as F
from rimekit import mats as M

# ---------------------------------------------------------------- fire

OBSIDIAN = F.OBSIDIAN
IRON = F.IRON
IRON_BLACK = palette("#09090b", "#111114", "#19191d", "#232328", "#2e2e34", "#3c3c44")
MAGMA_CRUST = F.MAGMA_CRUST
LAVA = F.LAVA
EMBER = F.EMBER
FLAME = F.FLAME
BRASS = F.BRASS
GOLD = F.GOLD
BRONZE = F.BRONZE
SOOT_CLOTH = F.SOOT_CLOTH
CRIMSON = palette("#2a0605", "#420a08", "#5c100b", "#78180f", "#952316", "#b0321f")      # a burning-red cloth
SAND_CLOTH = palette("#4a3a24", "#62502f", "#7c663c", "#957d4a", "#ad955b", "#c4ad70")   # a desert wrap
BASALT = F.BASALT
CHAR = F.CHAR
LEATHER = F.LEATHER
EYE_EMBER = F.EYE_EMBER

# ---------------------------------------------------------------- thunder

COPPER = palette("#3a1a0e", "#572812", "#76391a", "#984d24", "#b8653a", "#d58756")
COPPER_GREEN = palette("#13261f", "#1b3a2e", "#26503e", "#336a51", "#468666", "#64a483")   # weathered copper
VERDIGRIS = F.VERDIGRIS
STORM = palette("#0e1016", "#151820", "#1e222c", "#282d3a", "#343a4a", "#444b5e")         # storm-cloud cloth
STORM_PALE = palette("#3a3e4a", "#4c5160", "#606676", "#767c8c", "#8e94a2", "#a8aebb")
LIGHTNING = palette("#7a6a1a", "#a89428", "#d4c040", "#efe070", "#fff3a8", "#fffde6")      # pale yellow-white
EYE_LIGHTNING = palette("#8a7a20", "#b8a42c", "#e0cc48", "#f6e47a", "#fff6b8", "#ffffee")
BARNACLE = palette("#4e4a42", "#67625a", "#817b72", "#9c958b", "#b6afa4", "#d0cabf")
TURTLE = palette("#16200e", "#213016", "#2e4220", "#3d562a", "#4e6c36", "#638444")
TURTLE_RIM = palette("#2a1c0c", "#3c2a12", "#523a1a", "#6a4c24", "#83602f")
ROPE = palette("#3e3220", "#54452b", "#6c5a38", "#857047", "#9e8858", "#b8a06c")
SEA_GLASS = palette("#2a3a1a", "#3e5426", "#566e34", "#728c46", "#94aa60", "#bcd08a")

# ---------------------------------------------------------------- thunder in the Dross's blue (round 2)
# The user (November 7, 2026): "why is your thunder idea just copper ... it needs to be blue to reflect the Dross". These
# follow the Dross's palette (DrossColors' Palette: main 0x2E6BFF, deep cobalt 0x0B2A9E, highlight 0x7FA8FF): dark
# blue-black steel split by electric blue light, like the Dross portal frame's cracks; silver trims; cobalt cloth.

DROSS_STEEL = palette("#090d1a", "#0f162b", "#16203e", "#1e2b54", "#28386b", "#354a86")
DROSS_COBALT = palette("#060a20", "#0a1232", "#0e1a48", "#13245f", "#1a3079", "#233d93")
ELECTRIC = palette("#0b2a9e", "#1a46d4", "#2e6bff", "#5888ff", "#7fa8ff", "#dfe9ff")
EYE_ELECTRIC = palette("#1a46d4", "#2e6bff", "#5888ff", "#7fa8ff", "#b6ceff", "#f2f6ff")
SILVER = palette("#22252d", "#323641", "#474c59", "#5f6674", "#7c8494", "#a0a8b8")
GLASS_ELECTRIC = palette("#0a1858", "#11267c", "#1a38a2", "#2c56cc", "#5680ee", "#9ab6ff")


def dross_steel(seed, cracks=0.12, cell=7.0, width=0.38):
    """The thunder knights' armour: dark blue-black steel, electric blue light in some of its seams."""
    return F.crust(pal=DROSS_STEEL, glow_pal=ELECTRIC, seed=seed, cell=cell, cracks=cracks, width=width, hot=1.0,
                   outline=0.12)


# ---------------------------------------------------------------- earth

STONE = palette("#28282a", "#363638", "#464547", "#585658", "#6c6a6a", "#84817f")
STONE_PALE = palette("#4c4a46", "#615e59", "#77736d", "#8e8982", "#a6a199", "#bcb7ae")
DEEPSLATE = palette("#151517", "#1d1d21", "#26262b", "#303036", "#3c3c43", "#4a4a52")
MOSS = palette("#18220e", "#223014", "#2e401a", "#3c5222", "#4c662a", "#5e7c34")
AMBER = palette("#6a3808", "#98560c", "#c47a18", "#e8a032", "#fcc866", "#fff0c4")
EYE_AMBER = palette("#7a4608", "#a8680e", "#d4901c", "#f2b63c", "#ffd87a", "#fff4d4")
AMETHYST = palette("#2a1440", "#3e1f5c", "#56307c", "#72469c", "#9064bc", "#b48ed8")
GOAT_HORN = palette("#3e3830", "#57503f", "#716852", "#8c8268", "#a69d82", "#c0b8a0")
EARTH_CLOTH = palette("#1e170e", "#2b2114", "#3a2d1b", "#4b3a24", "#5d4a2e", "#715a39")


# ---------------------------------------------------------------- shared painters


def plated(pal, glow_pal, seed, cell=6.0, cracks=0.35, width=0.5, hot=1.0, outline=0.12, ash_top=0.0):
    """Plates of `pal` (Voronoi cells, each its own shade), some seams glowing in `glow_pal`."""
    return F.crust(pal=pal, glow_pal=glow_pal, seed=seed, cell=cell, cracks=cracks, width=width, hot=hot,
                   outline=outline, ash_top=ash_top)


def metal(pal, seed, shine=1.4, rust=0.0):
    return F.metal(pal, seed=seed, shine=shine, rust=rust)


def sheen_metal(pal, seed, seams=0.0, seam_pal=LIGHTNING, sheen=0.5):
    """Dark metal with a faint sheen and, if asked, glowing seams (the fire kit's star iron in any palette)."""
    return F.star_iron(pal=pal, seed=seed, seams=seams, seam_pal=seam_pal, sheen=sheen)


def copper(seed, patina=0.55, shine=1.2):
    """Copper armour gone green in patches, like weathered copper blocks."""
    return F.bronze(pal=COPPER, seed=seed, patina=patina, patina_pal=COPPER_GREEN, shine=shine)


def solid(pal, seed, idx=2.5, grain=1.0, bev=0.6, outline=0.14):
    return F.solid(pal, seed=seed, idx=idx, grain=grain, bev=bev, outline=outline)


def glowing(pal, seed, base_pal=None):
    return F.glowing(pal, seed=seed, base_pal=base_pal)


def cloth(pal, seed, folds=1.0, ragged=0.0, holes=0.0):
    return F.cloth(pal, seed=seed, folds=folds, ragged=ragged, holes=holes)


def burnt_cloth(pal, seed, ragged=0.8, holes=0.0, glow_edge=1.0):
    return F.burnt_cloth(pal=pal, seed=seed, ragged=ragged, holes=holes, glow_edge=glow_edge)


def flame(seed, pal=FLAME):
    return F.flame(pal=pal, seed=seed)


def crystal(pal, seed, glow_pal=None, alpha=(130, 205), glow_alpha=110.0):
    """See-through crystal (the Rimefall kit's ice in the crystal's colours, no frost), glowing a little."""
    return M.ice(pal, alpha=alpha, seed=seed, cracks=0.25, bubbles=0.01, frost_amount=0.0,
                 glow_pal=glow_pal or pal, glow_alpha=glow_alpha)


def glass(pal, seed, alpha=(110, 190)):
    return F.glass(pal=pal, seed=seed, alpha=alpha)


def mouth(seed=39):
    return F.mouth(seed)


def bone(pal=F.BONE_CHAR, seed=19, cracks=0.15):
    return F.bone(pal, seed=seed, cracks=cracks)


def leather(seed, pal=LEATHER):
    return F.solid(pal, seed=seed, idx=2.5, grain=1.0)


def wood(pal, seed, plank=3.0, axis=1):
    return F.wood(pal, seed=seed, plank=plank, axis=axis)


def shag(pal, seed, strand=1.0, ragged=1.0):
    return F.shag(pal, seed=seed, strand=strand, ragged=ragged)


def fur(pal, seed, strand=3.0):
    return F.fur(pal, seed=seed, strand=strand)
