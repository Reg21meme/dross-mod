"""
Round 2 of the Huntsman's boss bars (October 19, 2026; the user's picks from round 1: design 9, Skull Trophy, for the Wild
Hunt; design 1 or 3 for the Alpha Moose, with its red angry look; "none of these really satisfy for the Huntsman").
Ten new bars for the Huntsman alone, numbered 11 to 20, drawn to sit under design 9's Wild Hunt bar (bone, leather,
his grey bearskin, the glacier blue fill) and after his own look: a wolf's head worn as a hood (its snout and fangs over
his face, his eyes glowing under it), a stag's antlers rising out of it, bone pauldrons, a bearskin cloak, an ice spear,
a bow, a hunting horn and a quiver of ice-fletched arrows.

Each design function returns the three canvases for every look (like bardesigns.py), but only its Huntsman looks are
new: its Wild Hunt look is design 9's and its Alpha Moose looks design 1's (the showcase mixes bars of different designs,
so those copies only keep every design complete).
"""
import math
import random

from barkit import BAR_H, BAR_W, CENTRE_X, Canvas, curve_points, disc, ellipse, lit_tube, mix, paint, polygon, shade, thick_line
from bardesigns import (BLOOD, BONE, FUR, ICE, IRON, LEATHER, OUT, SPRUCE, both, deer_antler, outline_all, sprite, stain,
                        start, t_antlers, t_skull)


# His eyes, glowing pale blue under the wolf's hood (red when he's angry); the wolf's own eyes are dead and dark.
EYE = (178, 236, 255)
EYE_ANGRY = (255, 86, 64)
SHADOW = (20, 22, 28)
HIDE = [mix(LEATHER[k], FUR[3], 0.25) for k in range(len(LEATHER))]


def _blood(pal, t=0.5):
    return [mix(p, BLOOD[2], t) for p in pal]


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


def deer_skull(d, cx, top, angry):
    """A wolf's skull (the hood's bone), front on, 11 wide: brow, eye holes, the long snout, fangs."""
    rows = [
        "..bbbbbbb..",
        ".bbbbbbbbb.",
        "bbbbbbbbbbb",
        "bEEbbbbbEEb",
        "bEEbbbbbEEb",
        ".bbbbBbbbb.",
        "..bbbBbbb..",
        "..fbbbbbf..",
        "..f.bnb.f..",
    ]
    pal = {"b": BONE[1], "B": BONE[3], "E": EYE_ANGRY if angry else (24, 20, 18), "n": BONE[4], "f": BONE[0]}
    sprite(d, rows, pal, int(round(cx - 5)), top)


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


def pauldron(d, x0, side, pal, fur):
    """A bone shoulder plate at a bar end, seen from the front: a fan of ribbed bone plates spreading round the end (each
    rib lit along one side, a dark lip round the rim), a boss at its root, the grey bearskin hanging under it.
    side = -1 at the left end, +1 at the right."""
    cx, cy = x0, 2.5
    rng = random.Random(int(x0) * 7 + 3)
    for x in range(int(cx) - 12, int(cx) + 13):
        if (x - cx) * side < -1:
            continue
        n = rng.choice((3, 4, 5, 6))
        for j in range(n):
            d.px(x, 8 + j, fur[1 + (x + j) % 3] if j < n - 1 else fur[4])
    for y in range(-10, 13):
        for x in range(int(cx) - 12, int(cx) + 13):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy * 1.15)
            if r > 9.5 or dx * side < -0.5:
                continue
            ang = math.degrees(math.atan2(dy, dx * side))
            rib = (ang + 90.0) % 22.5
            if r > 8.4:
                col = pal[4]
            elif rib < 2.2:
                col = pal[3]
            else:
                col = shade(pal, 0.5 + 1.6 * (rib / 22.5) + 0.12 * (dy + 8))
            d.px(x, y, col)
    for (x, y) in disc(cx + 0.5 * side, cy, 2.3):
        d.px(x, y, pal[0])


def h_pauldrons(bar, angry):
    """His bone pauldrons cap the bar's ends over his grey bearskin; the bar a stitched hide strap between them; a
    small wolf skull at its middle."""
    e, f, d = start(bar, angry, 121)

    def strap(c):
        for x in range(-1, BAR_W + 1):
            c.px(x, -1, HIDE[1] if x % 4 else HIDE[0])
            c.px(x, BAR_H, HIDE[3])
        for y in range(0, BAR_H):
            c.px(-1, y, HIDE[2])
            c.px(BAR_W, y, HIDE[2])
    both(e, f, strap)
    both(e, f, outline_all)
    pal = BONE if not angry else _blood(BONE, 0.4)
    pauldron(d, -2, -1, pal, FUR)
    pauldron(d, BAR_W + 2, 1, pal, FUR)
    for x in range(14, BAR_W - 12, 9):                 # stitches along the strap
        d.px(x, -1, BONE[2])
    deer_skull(d, CENTRE_X + 0.5, -6, angry)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 13 Bearskin Cloak


def h_cloak(bar, angry):
    """The bar wrapped in his wide grey bearskin: shaggy fur over its top, the hide's pale underside showing at its
    ends, its worn end hanging in tatters under the right; a bone clasp with his antlers at the middle."""
    e, f, d = start(bar, angry, 131)
    rng = random.Random(132)
    tops = [rng.choice((1, 2, 2, 3)) for _ in range(BAR_W + 8)]

    def pelt(c):
        for x in range(-3, BAR_W + 3):
            for k in range(1, tops[x + 3] + 1):
                c.px(x, -k, shade(FUR, 1.0 + 0.7 * (k - 1) + (0.6 if (x * 5) % 7 == 0 else 0)))
            c.px(x, BAR_H, FUR[3])
        for y in range(0, BAR_H):
            for x in (-3, -2, -1, BAR_W, BAR_W + 1, BAR_W + 2):
                c.px(x, y, HIDE[1] if y % 2 else HIDE[2])
    both(e, f, pelt)
    both(e, f, outline_all)
    # the worn end: strips of fur hanging under the bar's right third, each 2-3 wide and pointed, longer towards the
    # end, a gap between them, the hide's pale edge showing at their roots
    x = BAR_W - 66
    while x < BAR_W + 2:
        w = rng.choice((2, 3, 3))
        t = (x - (BAR_W - 66)) / 68.0
        n = int(round(2 + 9 * t * rng.uniform(0.55, 1.0)))
        for k in range(n):
            for j in range(w):
                if k == n - 1 and j != w // 2:
                    continue
                d.px(x + j, BAR_H + 1 + k, HIDE[1] if k == 0 else shade(FUR, 1.6 + 0.35 * k + (0.6 if j == w - 1 else 0)))
        x += w + rng.choice((1, 1, 2))
    # the clasp: a bone ring holding the cloak, his antlers rising from it
    for dd in (-1, 1):
        deer_antler(d, CENTRE_X + 0.5 + dd * 3.0, -2.5, dd, BONE, 0.62)
    for (x, y) in disc(CENTRE_X + 0.5, 1.5, 4.0):
        d.px(x, y, shade(BONE if not angry else _blood(BONE), 1.0 + 0.4 * (y + 2)))
    for (x, y) in disc(CENTRE_X + 0.5, 1.5, 1.8):
        d.px(x, y, EYE_ANGRY if angry else ICE[2])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 14 Spear & Bow


def ice_spearhead(d, x, y, d_x, blade):
    """A boar spear's leaf of ice pointing along d_x (+1 right, -1 left) from (x, y), its iron socket and lugs."""
    for (px, py) in polygon([(x, y), (x + d_x * 5, y - 5), (x + d_x * 12, y - 4), (x + d_x * 20, y),
                             (x + d_x * 12, y + 4), (x + d_x * 5, y + 5)]):
        d.px(px, py, shade(blade, 1 + 3.2 * abs(py + 0.5 - y) / 5.0))
    for k in range(1, 19):
        d.px(int(x + d_x * k), int(y), blade[0])


def h_spear_bow(bar, angry):
    """Design 9's bones with his weapons laid across them: the ice spear along the top, its blade past the right end and
    its tassel at the left, the bow drawn under, an arrow nocked; the wolf skull at the middle."""
    e, f, d = start(bar, angry, 141)
    both(e, f, bone_rails)
    both(e, f, outline_all)
    blade = ICE if not angry else _blood(ICE, 0.5)
    # the spear: a shaft over the bar's top, the blade past the right end
    for x in range(-14, BAR_W + 4):
        if abs(x - CENTRE_X) > 9:
            d.px(x, -4, SPRUCE[1] if x % 9 else LEATHER[2])
            d.px(x, -3, SPRUCE[3])
    d.rect(BAR_W + 4, -6, BAR_W + 6, -1, IRON[2])
    ice_spearhead(d, BAR_W + 7, -3.5, 1, blade)
    for k, x in enumerate(range(-19, -13)):              # the fur tassel at its butt
        for y in range(-4, 2 + (k % 3)):
            d.px(x, y, FUR[1 + (x + y) % 3])
    # the bow under the bar: a long curve, its string, an ice-tipped arrow on it pointing left
    bow = curve_points([(18, 7), (60, 12.5), (122, 12.5), (164, 7)], 60)
    for (x, y) in bow:
        d.px(int(x), int(y), SPRUCE[2])
        d.px(int(x), int(y) + 1, SPRUCE[4])
    for x in range(18, 165):
        d.px(x, 7, BONE[0])
    for x in range(8, 100):
        d.px(x, 8, SPRUCE[1])
    for (x, y) in polygon([(2, 8.5), (8, 6), (8, 11)]):
        d.px(x, y, blade[1])
    deer_skull(d, CENTRE_X + 0.5, -7, angry)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 15 Quiver of Ice


def ice_arrow(d, x, y, ang, length, blade, feather):
    """An arrow from its nock at (x, y) pointing at ang degrees (0: right, -90: up): spruce shaft, ice head, fletching."""
    a = math.radians(ang)
    ux, uy = math.cos(a), math.sin(a)
    for k in range(length):
        d.px(int(round(x + ux * k)), int(round(y + uy * k)), SPRUCE[1] if k % 5 else SPRUCE[2])
    hx, hy = x + ux * length, y + uy * length
    for (px, py) in polygon([(hx + ux * 4, hy + uy * 4), (hx - uy * 2, hy + ux * 2), (hx + uy * 2, hy - ux * 2)]):
        d.px(px, py, blade[1])
    d.px(int(round(hx + ux * 3)), int(round(hy + uy * 3)), blade[0])
    for k in range(1, 5):
        for s in (-1, 1):
            d.px(int(round(x + ux * k - uy * s * 1.3)), int(round(y + uy * k + ux * s * 1.3)), feather[k % 2])


def h_quiver(bar, angry):
    """A quiver's leather strap for the bar, studded with bone; at each end a fan of ice-fletched arrows sticking out of
    a quiver's mouth; his hood's wolf skull in the middle."""
    e, f, d = start(bar, angry, 151)

    def strap(c):
        for x in range(-1, BAR_W + 1):
            c.px(x, -1, LEATHER[1])
            c.px(x, -2, LEATHER[2] if x % 3 else LEATHER[0])
            c.px(x, BAR_H, LEATHER[3])
            c.px(x, BAR_H + 1, LEATHER[4])
        for y in range(-2, BAR_H + 2):
            c.px(-1, y, LEATHER[2])
            c.px(BAR_W, y, LEATHER[2])
    both(e, f, strap)
    both(e, f, outline_all)
    blade = ICE if not angry else _blood(ICE, 0.55)
    feather = (FUR[0], ICE[3]) if not angry else (FUR[0], BLOOD[2])
    for side, x0 in ((-1, -4), (1, BAR_W + 3)):
        # the quiver's mouth: a hide cup with a bone rim
        d.rect(x0 - 3, -3, x0 + 3, BAR_H + 2, HIDE[2])
        d.rect(x0 - 3, -3, x0 + 3, -3, BONE[1])
        d.rect(x0 - 2, 1, x0 + 2, 1, HIDE[4])
        for k, ang in enumerate((-150, -125, -100, -75) if side < 0 else (-30, -55, -80, -105)):
            ice_arrow(d, x0 - 1 + k * 0.7 * -side, -3, ang, 9 + (k % 2) * 3, blade, feather)
    for x in range(10, BAR_W - 8, 12):
        d.px(x, -2, BONE[0])
        d.px(x + 6, BAR_H + 1, BONE[1])
    deer_skull(d, CENTRE_X + 0.5, -7, angry)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 16 Horn & Antlers


def h_horn(bar, angry):
    """The bar hung on its carrying cord like his hunting horn, the horn itself curving under its middle (a silver
    mouthpiece, bone bands, the bell turned up), his stag's antlers spread above."""
    e, f, d = start(bar, angry, 161)
    both(e, f, bone_rails)
    both(e, f, outline_all)
    knuckle_caps(d)
    horn = [mix(h, (90, 74, 58), 0.3) for h in BONE]
    if angry:
        horn = _blood(horn, 0.35)
    # the horn hangs under the bar's middle: a silver mouthpiece at the left, the horn curving down and round, its bell
    # turned up at the right (dark inside, a pale rim); bone bands round it
    path = curve_points([(CENTRE_X - 24, 9.5), (CENTRE_X - 8, 14.5), (CENTRE_X + 10, 14.5), (CENTRE_X + 22, 11.0)], 20)
    paint(d, lit_tube(path, lambda t: 1.3 + 2.6 * t ** 1.5), horn, 2.4, 1.6, -0.8)
    for k in (0.28, 0.52, 0.74):
        x, y = path[int(k * (len(path) - 1))]
        r = 1.3 + 2.6 * k ** 1.5 + 0.6
        for (px, py) in thick_line(x, y - r, x, y + r, 0.55):
            if d.on(px, py):
                d.px(px, py, BONE[4] if not angry else BLOOD[2])
    for (x, y) in ellipse(CENTRE_X + 24.5, 10.5, 2.4, 4.0):
        d.px(x, y, horn[1])
    for (x, y) in ellipse(CENTRE_X + 24.5, 10.5, 1.4, 3.0):
        d.px(x, y, SHADOW)
    d.rect(CENTRE_X - 29, 8, CENTRE_X - 25, 11, IRON[2])
    d.rect(CENTRE_X - 29, 8, CENTRE_X - 25, 8, IRON[0])
    d.rect(CENTRE_X - 30, 9, CENTRE_X - 30, 10, IRON[4])
    for (x0, x1) in ((CENTRE_X - 18, CENTRE_X - 21), (CENTRE_X + 16, CENTRE_X + 19)):   # its carrying cords
        for (x, y) in thick_line(x0, 11, x1, BAR_H + 0.5, 0.45):
            if not d.on(x, y):
                d.px(x, y, LEATHER[1])
    for dd in (-1, 1):
        deer_antler(d, CENTRE_X + 0.5 + dd * 2.0, -2.0, dd, BONE, 0.72)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 17 Claw Marks


def h_claws(bar, angry):
    """Bone rails scored by three great claw marks at each end (his bear's), bear claws threaded on a cord hanging under
    the bar, a single wolf fang at the middle; the claw marks bleed when he's angry."""
    e, f, d = start(bar, angry, 171)
    both(e, f, bone_rails)
    both(e, f, outline_all)
    knuckle_caps(d)
    gash = BLOOD[2] if angry else BONE[5]
    for x0 in (8, BAR_W - 26):
        for k in range(3):
            for (x, y) in thick_line(x0 + k * 6, -4, x0 + k * 6 + 8, BAR_H + 3, 0.5):
                d.px(x, y, gash)
    cord = curve_points([(20, BAR_H + 1), (60, BAR_H + 4), (122, BAR_H + 4), (162, BAR_H + 1)], 16)
    for (x, y) in cord:
        d.px(int(x), int(y), LEATHER[3])
    for k, x in enumerate(range(30, 160, 12)):
        if abs(x - CENTRE_X) < 8:
            continue
        y = next(int(py) for (px, py) in cord if px >= x) + 1
        for j in range(4):
            d.px(x + (j > 1), y + j, BONE[1] if j < 2 else BONE[3])
        d.px(x, y, (50, 44, 38))
    fang = [(CENTRE_X - 2.5, -9.0), (CENTRE_X + 3.5, -9.0), (CENTRE_X + 2.5, -3.0), (CENTRE_X + 0.5, 2.5),
            (CENTRE_X - 1.0, -2.0)]
    for (x, y) in polygon(fang):
        d.px(x, y, shade(BONE if not angry else _blood(BONE, 0.45), 0.4 + 0.3 * (y + 9)))
    d.rect(CENTRE_X - 2, -11, CENTRE_X + 3, -10, LEATHER[2])
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 18 Trophy Belt


def h_belt(bar, angry):
    """His belt for the bar: thick leather with a bone buckle, little skulls and fangs threaded along it (his kills),
    the wolf hood's face at the middle."""
    e, f, d = start(bar, angry, 181)

    def belt(c):
        for x in range(-2, BAR_W + 2):
            for y, k in ((-3, 1), (-2, 2), (-1, 3), (BAR_H, 3), (BAR_H + 1, 4)):
                c.px(x, y, LEATHER[k])
            if x % 2 == 0:
                c.px(x, -3, LEATHER[0])
        for y in range(-3, BAR_H + 2):
            c.px(-2, y, LEATHER[2])
            c.px(-1, y, LEATHER[3])
            c.px(BAR_W, y, LEATHER[3])
            c.px(BAR_W + 1, y, LEATHER[2])
    both(e, f, belt)
    both(e, f, outline_all)
    # the buckle: a bone frame at the left end
    for y in range(-6, BAR_H + 4):
        for x in range(-12, -3):
            if x in (-12, -11, -5, -4) or y in (-6, -5, BAR_H + 2, BAR_H + 3):
                d.px(x, y, shade(BONE, 1.0 + 0.1 * (y + 6)))
    d.rect(-10, 1, -3, 2, IRON[2])
    skull = ["bbbbb", "bEbEb", "bbbbb", ".bnb.", ".b.b."]
    pal = {"b": BONE[1], "E": (36, 30, 26), "n": BONE[4]}
    for k, x in enumerate(range(16, BAR_W - 6, 19)):
        if abs(x + 2 - CENTRE_X) < 14:
            continue
        if k % 2:
            sprite(d, skull, pal, x, -9)
        else:
            for j in range(5):
                d.px(x + 2, -8 + j, BONE[0] if j < 3 else BONE[2])
                if j < 3:
                    d.px(x + 3, -8 + j, BONE[2])
        d.px(x + 2, -4, LEATHER[4])
    wolf_hood(d, CENTRE_X + 0.5, -15, angry, antlers=False)
    if angry:
        stain(d, 2, 9)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 19 Frost Hunter


def ice_antler(d, bx, by, dd, pal, size):
    deer_antler(d, bx, by, dd, pal, size)


def h_frost(bar, angry):
    """The Ice Necromancer's servant: a rail of clear frost, antlers of ice growing out of both ends, the wolf hood in
    pale ice at the middle with his eyes burning in it."""
    e, f, d = start(bar, angry, 191)
    frost = ICE if not angry else _blood(ICE, 0.4)

    def rails(c):
        for x in range(-2, BAR_W + 2):
            c.px(x, -1, frost[2] if x % 6 else frost[0])
            c.px(x, -2, frost[1] if (x * 7) % 11 else frost[3])
            c.px(x, BAR_H, frost[3])
        for y in range(-2, BAR_H + 1):
            for x in (-2, -1, BAR_W, BAR_W + 1):
                c.px(x, y, frost[2])
    both(e, f, rails)
    both(e, f, outline_all)
    for dd, bx in ((-1, -3), (1, BAR_W + 2)):
        deer_antler(d, bx, 2.5, dd, frost, 0.95)
    rng = random.Random(192)
    x = 6
    while x < BAR_W - 4:                                  # small icicles under it
        if abs(x - CENTRE_X) > 9:
            n = rng.choice((1, 2, 2, 3, 4))
            for k in range(n):
                d.px(x, BAR_H + 1 + k, frost[2] if k < n - 1 else frost[0])
        x += rng.randint(4, 9)
    ice_pal = {"D": frost[5], "M": frost[4], "L": frost[3], "W": frost[1], "k": frost[6], "n": frost[6],
               "f": frost[0], "S": (22, 34, 48), "e": EYE_ANGRY if angry else (255, 255, 255)}
    sprite(d, WOLF_HOOD, ice_pal, int(round(CENTRE_X + 0.5 - 7)), -14)
    d.outline(OUT)
    return e, f, d


# ---------------------------------------------------------------- 20 The Mask


WOLF_MASK = [
    "DD.................DD",
    "MLD...............DLM",
    "MLLD.............DLLM",
    "MLLMD...........DMLLM",
    "DMLMMDDDDDDDDDDDMMLMD",
    "DMMMMMMMMLLLMMMMMMMMD",
    "DMMMkkkMMLLLMMkkkMMMD",
    "DDMMMkkMMLLLMMkkMMMDD",
    ".DDMMMMMLLWLLMMMMMDD.",
    "..DMMMMMLWWWLMMMMMD..",
    "...DMMMLWWWWWLMMMD...",
    "....DMMLWWWWWLMMD....",
    ".....DMWWWWWWWMD.....",
    ".....DfWWWnWWWfD.....",
    ".....DffWnnnWffD.....",
    "......fSSSSSSSf......",
    "......SeeSSSeeS......",
    ".......SSSSSSS.......",
    "........SSSSS........",
]


def h_mask(bar, angry):
    """His face large at the middle, the hood and antlers rising over the bar's name-free middle: the wolf's head over
    the shadow of his face, two burning eyes; a stitched hide bar studded with bone."""
    e, f, d = start(bar, angry, 201)

    def hide(c):
        for x in range(-2, BAR_W + 2):
            c.px(x, -1, HIDE[1])
            c.px(x, -2, HIDE[2] if x % 5 else BONE[1])
            c.px(x, BAR_H, HIDE[3])
            c.px(x, BAR_H + 1, HIDE[4] if x % 5 else BONE[2])
        for y in range(-2, BAR_H + 2):
            c.px(-2, y, HIDE[2])
            c.px(-1, y, HIDE[3])
            c.px(BAR_W, y, HIDE[3])
            c.px(BAR_W + 1, y, HIDE[2])
    both(e, f, hide)
    both(e, f, outline_all)
    eye = EYE_ANGRY if angry else EYE
    pal = {"D": FUR[4], "M": FUR[3], "L": FUR[2], "W": FUR[1], "k": (30, 28, 28), "n": (18, 16, 16),
           "f": BONE[0] if not angry else BLOOD[1], "S": SHADOW, "e": eye}
    sprite(d, WOLF_MASK, pal, int(round(CENTRE_X + 0.5 - 10.5)), -19)
    for dd in (-1, 1):
        deer_antler(d, CENTRE_X + 0.5 + dd * 4.5, -16.0, dd, BONE, 0.48)
    d.outline(OUT)
    return e, f, d


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


ROUND2 = (
    (11, "Wolf & Antlers", huntsman_only(h_wolf)),
    (12, "Bone Pauldrons", huntsman_only(h_pauldrons)),
    (13, "Bearskin Cloak", huntsman_only(h_cloak)),
    (14, "Spear & Bow", huntsman_only(h_spear_bow)),
    (15, "Quiver of Ice", huntsman_only(h_quiver)),
    (16, "Horn & Antlers", huntsman_only(h_horn)),
    (17, "Claw Marks", huntsman_only(h_claws)),
    (18, "Trophy Belt", huntsman_only(h_belt)),
    (19, "Frost Hunter", huntsman_only(h_frost)),
    (20, "The Mask", huntsman_only(h_mask)),
)
