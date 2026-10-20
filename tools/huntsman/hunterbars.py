"""
The Huntsman's boss bar (the user's pick of October 19, 2026: round 2's design 11, Wolf & Antlers, "these are the
best"): a wolf's head worn as a hood, its snout and fangs over his face, his eyes glowing under it (red when he's angry),
a stag's antlers rising out of it, on a bar of bone and leather with the glacier blue fill; drawn to sit under design 9's
Wild Hunt bar (bardesigns.py). The other nine round-2 candidates were removed on October 20, 2026.

`huntsman_only(h_wolf)` returns the three canvases for every look (like bardesigns.py): its own Huntsman looks, design
9's Wild Hunt and design 1's Alpha Moose (so the Huntsman bar is laid out exactly as the user saw it in the showcase).
"""
from barkit import BAR_H, BAR_W, CENTRE_X, disc, shade
from bardesigns import BLOOD, BONE, FUR, OUT, both, deer_antler, outline_all, sprite, stain, start, t_antlers, t_skull


# His eyes, glowing pale blue under the wolf's hood (red when he's angry); the wolf's own eyes are dead and dark.
EYE = (178, 236, 255)
EYE_ANGRY = (255, 86, 64)
SHADOW = (20, 22, 28)


def bone_rails(c, pal=BONE):
    """Design 9's rails: a row of bone segments above and below the bar, knuckled every seventh pixel."""
    for x in range(-2, BAR_W + 2):
        seg = (x + 2) % 7
        joint = seg == 0
        c.px(x, -1, pal[4] if joint else pal[1])
        c.px(x, -2, pal[4] if joint else (pal[0] if seg in (2, 3) else pal[2]))
        c.px(x, BAR_H, pal[4] if joint else pal[3])
    for y in range(-2, BAR_H + 1):
        c.px(-1, y, pal[2])
        c.px(-2, y, pal[3])
        c.px(BAR_W, y, pal[2])
        c.px(BAR_W + 1, y, pal[3])


def knuckle_caps(d, pal=BONE):
    for cx in (-4.0, BAR_W + 4.0):
        for (x, y) in disc(cx, 0.0, 2.6) + disc(cx, 5.0, 2.6):
            d.px(x, y, shade(pal, 1.5 + (y > 2)))


# ---------------------------------------------------------------- his head: the wolf hood with its antlers

# The wolf's head worn as a hood, seen from the front: its ears up, its brow, its snout coming at you with the nose at
# its tip, fangs hanging down over his face; under it, in the shadow, his two glowing eyes. 15 wide.
WOLF_HOOD = [
    "D.............D",
    "MD...........DM",
    "MLD.........DLM",
    "MLMDDDDDDDDDMLM",
    "DMMMMMLLLMMMMMD",
    "DMMkkMLLLMkkMMD",
    "DDMMMMLWLMMMMDD",
    ".DMMMLWWWLMMMD.",
    "..DMMLWWWLMMD..",
    "...DMWWWWWMD...",
    "...DfWWnWWfD...",
    "...DfSSSSSfD...",
    "....SeSSSeS....",
    ".....SSSSS.....",
]


def wolf_hood(d, cx, top, angry, scale_antlers=0.62, antlers=True):
    """His head at (cx, top): the wolf hood (15 x 14) and, out of its brow, a stag's antlers each side."""
    eye = EYE_ANGRY if angry else EYE
    pal = {"D": FUR[4], "M": FUR[3], "L": FUR[2], "W": FUR[1], "k": (30, 28, 28), "n": (18, 16, 16),
           "f": BONE[0] if not angry else BLOOD[1], "S": SHADOW, "e": eye}
    sprite(d, WOLF_HOOD, pal, int(round(cx - 7)), top)
    if antlers:
        for dd in (-1, 1):
            deer_antler(d, cx + 0.5 + dd * 3.4, top + 3.0, dd, BONE, scale_antlers)


# ---------------------------------------------------------------- 11 Wolf & Antlers


def h_wolf(bar, angry):
    """Design 9's bone bar with his own head on it: the wolf hood, its fangs over his glowing eyes, a stag's antlers."""
    e, f, d = start(bar, angry, 111)
    both(e, f, bone_rails)
    both(e, f, outline_all)
    knuckle_caps(d)
    wolf_hood(d, CENTRE_X + 0.5, -14, angry)
    if angry:
        stain(d, 2, 5)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 12 Bone Pauldrons


# ---------------------------------------------------------------- 13 Bearskin Cloak


# ---------------------------------------------------------------- 14 Spear & Bow


# ---------------------------------------------------------------- 15 Quiver of Ice


# ---------------------------------------------------------------- 16 Horn & Antlers


# ---------------------------------------------------------------- 17 Claw Marks


# ---------------------------------------------------------------- 18 Trophy Belt


# ---------------------------------------------------------------- 19 Frost Hunter


# ---------------------------------------------------------------- 20 The Mask


# ---------------------------------------------------------------- the round


def huntsman_only(fn):
    """A round-2 design: its own Huntsman looks; design 9's Wild Hunt and design 1's Alpha Moose for the rest."""
    def design(bar, angry):
        if bar == "hunter":
            return fn(bar, angry)
        if bar == "wild":
            return t_skull(bar, angry)
        return t_antlers(bar, angry)
    design.__doc__ = fn.__doc__
    design.own = ("huntsman", "huntsman_angry")
    return design


