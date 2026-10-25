"""
The Frostfolk: the people of Rimefall's villages (the user, October 24, 2026: "more like regular Minecraft villagers,
no shrunken version of the Huntsman ... same build as a pillager, no nose, no grey faces ... an original look: blue
hoods, different styles of hoods, maybe a rhino horn hood for the farmer ... the elder has a scarred eye and a beard
... basic clothing, no armour plates ... as detailed as the Huntsman"; never called hunters).

One model in a pillager's build (in pixels: a head 8 x 10 x 8, a body 8 x 12 x 6, arms and legs 4 x 12 x 4; about 2.1
blocks to the top of a hood), no face under the hood but darkness and two glowing blue eyes (the user: "they should
not have human faces because of their hoods; their face should be black with blue eyes; their skin should not be
seen"), gloved hands, a tunic belted over trousers, fur-cuffed boots and sleeves, a capelet over the shoulders. Its
texture is painted at DENSITY texels a pixel (twice vanilla's), one texture for each calling (its hood's blue, its
clothes) and its glow (the eyes), and every calling's own shapes sit on bones of their own that the game hides for
the others (FolkRole.shows): its hood's style, its cape, its tools.

  warriors     spearman     a peaked hood, a wolf-pelt cape; a spear and a round hide shield
               axeman       a hood with a thick fur trim round the face, a wolf-pelt cape; an axe of ice
               pikeman      a tall pointed hood, a wolf-pelt cape; a long spear
               archer       a deep cowl with a drape down his back; a bow gripped in his left fist, carried level,
                            a brown box of a quiver on his back, four little ice fletchings out of its top
               horn-blower  a hood with a wolf's ears, a wolf-pelt cape; a spear, a horn at his belt, the village's flag
  workers      farmer       a hood with a Rimehorn's horn on its brow; a hoe, a basket of Frostroots
               butcher      a close cap-hood; a cleaver, a leather apron
               herder       a hood with fur ear flaps and a bobble; a shepherd's crook
               fisher       an oilskin hood with a wide brim; a rod and line, a fish on its hook
               tanner       a hood with a fringed capelet; a rolled hide on his back (nothing in his hands: the user,
                            October 24, 2026)
  the elder                 a long hood with its tail down his back, a long blue cape; a white scar across his right
                            eye, a long white beard (the user: "kind of like the Huntsman's face"); a sword of ice in
                            its sheath on his back, drawn to strike

Their animations are keyed by hand (no IK: they're blocky, like Minecraft's villagers). Each calling carries its tool
one of three ways (STYLE), and its idle, walk, run, talk and chop are made for that way: "pole" (a spear, pike, crook,
hoe or rod stood upright in front of its arm), "hang" (an axe or cleaver held out ahead of the fist, the way Minecraft
holds a tool) and "free" (nothing in its hands: the archer, the tanner; the elder, whose chop draws his sword); then
spear_combo, thrust_sweep, quick_shot, aimed_shot, horn_blow. A tool always leaves the fist clear of the sleeve (the
user, October 24, 2026: "the items go through the arms when they should be in their hands"; checked on every build,
CLIP_STRICT). They die the way vanilla mobs do (the user: "just the regular death animation"): no death animation of
their own.
"""
import math

import numpy as np

from kit.anims import ramp
from kit.moves import keyed
from rimekit import mats as M
from rimekit import mobkit
from rimekit.mobkit import Pose

ID = "frostfolk"
NAME = "The Frostfolk"
K = 1.0                  # designed in pixels
DENSITY = 2.0            # texels a pixel (vanilla's villagers: 1)
GIF_VIEW = (35.0, 10.0, 9.0)
LEGS = []
SHOULDER_Y = 22.0
HIP_Y = 12.0
NECK_Y = 24.0
FIST_Y = 11.5            # the middle of a hand (its arm's lower 3 pixels)

# ---------------------------------------------------------------- bones (pivot in pixels: his left +x, face -z, up +y)

GEAR = (
    # (bone, parent, pivot): every calling's own shapes
    ("hood_peak", "head", (0.0, 34.0, -4.0)),
    ("hood_furtrim", "head", (0.0, 30.0, -4.0)),
    ("hood_point", "head", (0.0, 34.0, 0.0)),
    ("hood_cowl", "head", (0.0, 34.0, 0.0)),
    ("hood_ears", "head", (0.0, 34.0, 0.0)),
    ("hood_horn", "head", (0.0, 34.0, -2.0)),
    ("hood_flaps", "head", (0.0, 27.0, 0.0)),
    ("hood_brim", "head", (0.0, 34.0, 0.0)),
    ("hood_tail", "head", (0.0, 30.0, 4.0)),
    ("beard", "head", (0.0, 26.0, -4.0)),
    ("cape_pelt", "cape", (0.0, NECK_Y, 3.0)),
    ("cape_long", "cape", (0.0, NECK_Y, 3.0)),
    ("drape", "cape", (0.0, NECK_Y, 3.0)),
    ("fringe", "body", (0.0, 21.0, 0.0)),
    ("apron", "body", (0.0, 20.0, -3.0)),
    ("quiver", "body", (0.0, 20.0, 3.0)),
    ("flag", "body", (0.0, 20.0, 3.0)),
    ("hide_roll", "body", (0.0, 23.0, 3.0)),
    ("horn_belt", "body", (4.0, 12.0, 0.0)),
    ("sword_back", "body", (0.0, 20.0, 7.0)),
    ("spear", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("long_spear", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("axe", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("sword", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("hoe", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("cleaver", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("crook", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("rod", "hand_r", (-6.0, FIST_Y, 0.0)),
    ("bow", "hand_l", (6.0, FIST_Y, 0.0)),
    ("basket", "hand_l", (6.0, FIST_Y, 0.0)),
    ("horn_held", "hand_l", (6.0, FIST_Y, 0.0)),
    ("shield", "arm_l", (8.0, 16.0, 0.0)),
)

BONES = [
    ("waist", (0.0, HIP_Y, 0.0), None),
    ("body", (0.0, HIP_Y, 0.0), "waist"),
    ("head", (0.0, NECK_Y, 0.0), "body"),
    ("cape", (0.0, NECK_Y, 3.0), "body"),
    ("arm_r", (-6.0, SHOULDER_Y, 0.0), "body"),
    ("sleeve_r", (-6.0, SHOULDER_Y, 0.0), "arm_r"),
    ("hand_r", (-6.0, FIST_Y, 0.0), "arm_r"),
    ("arm_l", (6.0, SHOULDER_Y, 0.0), "body"),
    ("sleeve_l", (6.0, SHOULDER_Y, 0.0), "arm_l"),
    ("hand_l", (6.0, FIST_Y, 0.0), "arm_l"),
    ("leg_r", (-2.0, HIP_Y, 0.0), "waist"),
    ("leg_l", (2.0, HIP_Y, 0.0), "waist"),
] + [(name, pivot, parent) for name, parent, pivot in GEAR]
ROUTES = {}
FREE = tuple(name for name, _p, _v in GEAR)


# ---------------------------------------------------------------- the model


def box(s, x0, x1, y0, y1, z0, z1, mat, rot=None):
    """A box from its corners (pixels)."""
    s.box(((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0), (x1 - x0, y1 - y0, z1 - z0), mat, rot=rot)


def body(r):
    b = r.part("body")
    box(b, -4, 4, 12, 24, -3, 3, "tunic")                       # the tunic
    box(b, -4.5, 4.5, 9, 13, -3.5, 3.5, "skirt")                # its skirt over the thighs
    box(b, -5, 5, 12, 13, -4, 4, "belt")                        # the belt and its buckle
    box(b, -1, 1, 11.5, 13.5, -4.5, -3.5, "buckle")
    box(b, -5, 5, 21, 24, -3.5, 3.5, "capelet")                 # the capelet over his shoulders
    for side in (-1, 1):                                        # the arms: sleeve, fur cuff, hand (the sleeve on
        a = r.part("arm_l" if side > 0 else "arm_r")            # a bone of its own, for the check that no tool
        x = side * 6.0                                          # passes through it: CLIP_STRICT)
        box(r.part("sleeve_l" if side > 0 else "sleeve_r"), x - 2, x + 2, 13, 22, -2, 2, "sleeve")
        box(a, x - 2.5, x + 2.5, 12.6, 13.6, -2.5, 2.5, "cuff")
        box(a, x - 2, x + 2, 10, 13, -2, 2, "glove")
        g = r.part("leg_l" if side > 0 else "leg_r")
        x = side * 2.0
        box(g, x - 2, x + 2, 5, 12, -2, 2, "trousers")          # the legs: trousers, boots with toes, a fur cuff
        box(g, x - 2, x + 2, 0, 5, -2.5, 2.5, "boots")
        dz = 0.25 if side > 0 else 0.0                          # (the two cuffs overlap between the legs: one a hair
        box(g, x - 2.5, x + 2.5, 4.5 + dz, 5.5 + dz, -3 + dz, 3 + dz, "bootfur")   # off the other)


def head(r):
    h = r.part("head")
    box(h, -4, 4, 24, 34, -4, 4, "face")                        # the head: darkness and two blue eyes
    # the hood: crown, back and sides a pixel thick, a rim over the brow (the face open below it)
    box(h, -5, 5, 34, 35, -5, 5, "hood")
    box(h, -5, 5, 25, 34, 4, 5, "hood")
    box(h, 4, 5, 25, 34, -5, 4, "hood")
    box(h, -5, -4, 25, 34, -5, 4, "hood")
    box(h, -4, 4, 32, 34, -5, -4, "hoodtrim")


def hoods(r):
    """Each calling's hood style, on a bone of its own."""
    p = r.part("hood_peak")                                    # the spearman's: a stiff peak over the brow
    box(p, -3, 3, 33, 35, -7, -5, "hoodtrim")
    box(p, -1.5, 1.5, 34, 35, -8, -7, "hoodtrim")
    f = r.part("hood_furtrim")                                 # the axeman's: a thick roll of fur round his face
    box(f, -6, 6, 32, 35, -6, -5, "fur")
    box(f, -6, -5, 24, 32, -6, -3, "fur")
    box(f, 5, 6, 24, 32, -6, -3, "fur")
    t = r.part("hood_point")                                   # the pikeman's: a tall point, leaning back
    box(t, -3, 3, 35, 38, -2, 4, "hood")
    box(t, -2, 2, 38, 41, 0, 4, "hood")
    box(t, -1, 1, 41, 44, 1, 4, "hood")
    box(t, -0.5, 0.5, 44, 46, 2, 3, "hoodtrim")
    c = r.part("hood_cowl")                                    # the archer's: a deep cowl shading his face
    box(c, -6, 6, 35, 36, -7, 5, "hood")
    box(c, -6, -5, 26, 35, -7, 5, "hood")
    box(c, 5, 6, 26, 35, -7, 5, "hood")
    box(c, -5, 5, 33, 35, -7, -6, "hoodtrim")
    e = r.part("hood_ears")                                    # the horn-blower's: a wolf's pelt and ears
    box(e, -5, 5, 35, 36, -2, 5, "wolf")
    for side in (-1, 1):
        x = side * 2.5
        box(e, x - 1, x + 1, 36, 38, -1, 1, "wolf")
        box(e, x - 0.5, x + 0.5, 38, 40, -0.5, 0.5, "wolf")
    n = r.part("hood_horn")                                    # the farmer's: a Rimehorn's hide and horns over it
    box(n, -5.5, 5.5, 34.5, 36.5, -5.5, 5.5, "woolly")
    box(n, -5.5, 5.5, 29, 35, 5, 6, "woolly")
    box(n, -5, 5, 33, 35, -6, -5, "woolly")
    n.cone((0.0, 36.0, -3.6), (0.0, 43.0, -7.4), 3.4, "horn", w1=0.6, steps=4)
    n.cone((0.0, 36.0, 0.4), (0.0, 39.0, -0.8), 2.0, "horn", w1=0.5, steps=2)
    fl = r.part("hood_flaps")                                  # the herder's: fur ear flaps, a bobble
    box(fl, -6, -5, 22, 30, -3, 3, "fur")
    box(fl, 5, 6, 22, 30, -3, 3, "fur")
    box(fl, -1.5, 1.5, 35, 38, -1.5, 1.5, "fur")
    b = r.part("hood_brim")                                    # the fisher's: an oilskin hood with a wide brim
    box(b, -7, 7, 33.5, 34.5, -7, 6, "hoodtrim")
    b2 = r.part("hood_tail")                                   # the elder's: a long tail down his back
    box(b2, -2, 2, 18, 34, 5, 6, "hood")
    box(b2, -1, 1, 14, 18, 5, 6, "hood")
    box(b2, -0.5, 0.5, 12, 14, 5.2, 6.2, "hoodtrim")
    bd = r.part("beard")                                       # the elder's long white beard, out of the dark
    box(bd, -3.5, 3.5, 22, 26, -5, -4, "beard")
    box(bd, -2.5, 2.5, 19, 22, -5, -4, "beard")
    box(bd, -1.5, 1.5, 17, 19, -5, -4, "beard")
    box(bd, -2.5, -0.5, 26, 27, -5, -4, "beard")              # his moustache either side over his mouth
    box(bd, 0.5, 2.5, 26, 27, -5, -4, "beard")


def capes(r):
    p = r.part("cape_pelt")                                    # warriors: a wolf's pelt
    box(p, -4.5, 4.5, 8, 24, 3.5, 4.5, "wolf")
    box(p, -1.5, 1.5, 6, 8, 3.5, 4.5, "wolf")
    lc = r.part("cape_long")                                   # the elder's long cape
    box(lc, -5, 5, 2, 24, 3.5, 4.5, "cape")
    d = r.part("drape")                                        # the archer's cowl's drape
    box(d, -5, 5, 13, 24, 3.5, 4.5, "hood")
    fr = r.part("fringe")                                      # the tanner's capelet's leather fringe
    for i, x in enumerate((-4.5, -3.0, -1.5, 0.0, 1.5, 3.0)):
        box(fr, x, x + 1, 19 - (i % 2), 21, -4.5, -3.5, "leather")


def gear(r):
    xr, xl = -6.0, 6.0

    def shaft(part, y0, y1, w=1.0):
        box(part, xr - w / 2.0, xr + w / 2.0, y0, y1, -w / 2.0, w / 2.0, "shaft")

    # the spearman's and horn-blower's spear, upright in his right fist
    sp = r.part("spear")
    shaft(sp, 2, 33)
    box(sp, xr - 1, xr + 1, 32, 34, -1, 1, "leather")
    sp.cone((xr, 34.0, 0.0), (xr, 39.0, 0.0), 2.2, "ice", w1=0.4, steps=3)
    # the pikeman's long spear
    ls = r.part("long_spear")
    shaft(ls, 2, 44)
    box(ls, xr - 1, xr + 1, 43, 45, -1, 1, "leather")
    ls.cone((xr, 45.0, 0.0), (xr, 51.0, 0.0), 2.4, "ice", w1=0.4, steps=3)
    # the axeman's axe: a pole, an iron cheek, a crescent of ice
    ax = r.part("axe")
    shaft(ax, 6, 26)
    box(ax, xr - 1, xr + 1, 21, 25, -2, 1, "iron")
    box(ax, xr - 0.5, xr + 0.5, 20, 27, -6, -2, "ice")
    # the elder's sword: a pommel, a grip in his fist, a crossguard, a blade of ice
    sw = r.part("sword")
    box(sw, xr - 1, xr + 1, 8, 9, -1, 1, "iron")
    box(sw, xr - 0.5, xr + 0.5, 9, 13, -0.5, 0.5, "leather")
    box(sw, xr - 2.5, xr + 2.5, 13, 14, -0.5, 0.5, "iron")             # (its guard across: held out, along it)
    box(sw, xr - 0.5, xr + 0.5, 14, 25, -1, 1, "ice")
    sw.cone((xr, 25.0, 0.0), (xr, 27.0, 0.0), 1.6, "ice", w1=0.3, d0=0.6, steps=2)
    # the farmer's hoe
    ho = r.part("hoe")
    shaft(ho, 2, 27)
    box(ho, xr - 1, xr + 1, 25, 26, -5, 1, "iron")
    # the butcher's cleaver: a broad bright blade over its handle
    cl = r.part("cleaver")
    shaft(cl, 9, 17)
    box(cl, xr - 0.5, xr + 0.5, 16, 23, -4, 1, "blade")
    # the herder's crook
    cr = r.part("crook")
    shaft(cr, 2, 33)
    box(cr, xr - 0.5, xr + 0.5, 33, 34, -3, 0.5, "shaft")
    box(cr, xr - 0.5, xr + 0.5, 30, 33, -4, -3, "shaft")
    # the fisher's rod and line, a fish on its hook (the user, October 24, 2026: "make a fish at the end of his fishing
    # pole"; he held it in his other hand)
    ro = r.part("rod")
    ro.seg((xr, 9.5, 1.0), (xr, 24.5, -12.0), 1.0, 1.0, "shaft")
    ro.seg((xr, 24.5, -12.0), (xr, 14.5, -12.0), 1.0, 1.0, "string")
    box(ro, xr - 0.5, xr + 0.5, 13.5, 14.5, -13, -12, "iron")
    box(ro, xr - 0.5, xr + 0.5, 9, 14, -13.5, -11.5, "fishscale")             # the fish, hanging by its mouth
    box(ro, xr - 0.5, xr + 0.5, 7, 9, -13, -12, "fishscale")                  # its tail
    # the archer's bow, always in his left fist, its grip through the fist's middle (the user, October 25, 2026: "make
    # the bow fit better in his hand, he should hold the handle, not the string"; it hung off the outer side of his
    # fist): modelled upright, its string behind it; he carries it level at his side and turns it upright to shoot
    # (left(), _shot())
    bw = r.part("bow")
    box(bw, xl - 0.5, xl + 0.5, FIST_Y - 2.4, FIST_Y + 2.4, -0.5, 0.5, "leather")     # its grip, wrapped in leather
    _bow(bw, np.array([xl, FIST_Y, 0.0]), np.array([0.0, 1.0, 0.0]), np.array([0.0, 0.0, 1.0]))
    # the elder's sword in its sheath across his back, its hilt over his right shoulder (in his fist only to strike:
    # the user, October 24, 2026, "the sword held by the elder is still weird")
    sb = r.part("sword_back")
    down = np.array([math.sin(math.radians(SWORD_BACK_TILT)), -math.cos(math.radians(SWORD_BACK_TILT)), 0.0])
    top = np.array([-3.8, 28.5, 7.0])
    for t0, t1, w, d, mat in ((0.0, 1.0, 2.0, 2.0, "iron"), (1.0, 5.0, 1.0, 1.0, "leather"),
                              (5.0, 6.0, 5.0, 1.5, "iron"), (6.0, 18.5, 3.0, 2.0, "leather"),
                              (18.3, 19.5, 2.0, 1.6, "iron")):            # (its tip into the sheath)
        c = top + down * ((t0 + t1) / 2.0)
        sb.box(tuple(c), (w, t1 - t0, d), mat, rot=(0.0, 0.0, -SWORD_BACK_TILT))   # (Bedrock's turn: x flipped)
    # the farmer's basket
    ba = r.part("basket")
    box(ba, xl - 0.5, xl + 0.5, 9, 10, -0.5, 0.5, "wicker")
    box(ba, xl - 2.5, xl + 2.5, 4, 9, -2.5, 2.5, "wicker")
    box(ba, xl - 2, xl + 2, 9, 10, -2, 2, "frostroot")
    # the horn-blower's horn, held up to his mouth in horn_blow
    hh = r.part("horn_held")
    hh.cone((xl, 12.6, 0.0), (xl, 18.6, -1.6), 1.0, "horn", w1=2.4, steps=3)
    # the spearman's round shield, on his left forearm's outside (its rings a hair apart, bulging out)
    sh = r.part("shield")
    for k, (h, w) in enumerate(((10, 4), (8, 7), (5, 9))):
        x0 = 8.0 + 0.2 * k
        box(sh, x0, x0 + 1, 15.5 - h / 2.0, 15.5 + h / 2.0, -w / 2.0, w / 2.0, "shieldhide")
    box(sh, 9.5, 10.5, 11, 20, -0.5, 0.5, "bone")
    box(sh, 9.6, 10.6, 15, 16, -4, 4, "bone")
    box(sh, 10.6, 11.6, 14.5, 16.5, -1, 1, "iron")
    # on his body: the butcher's apron, the archer's quiver, the horn-blower's horn and flag, the tanner's hide
    ap = r.part("apron")
    box(ap, -3.5, 3.5, 7, 21, -4.5, -3.5, "apron")
    # the archer's quiver (the user, October 25, 2026: "just make a rectangle ... make it browner ... at the top have
    # 3-4 small icy rectangles"): one straight brown box on his drape, leaning its mouth over his right shoulder for
    # his right hand, and four small ice fletchings standing out of its mouth, each sunk into it and turned with it.
    # Every size is a whole unit (the export rounds sizes, and a rounded box sat off its place) and no two faces
    # share a plane (the z-fighting fix grew the old shaft stubs and rim into a big lump beside the quiver's top: "the
    # tips where the arrows' feathers are aren't aligned with it"); a strap from it across his chest to his left hip
    qv = r.part("quiver")
    up = np.array([-math.sin(math.radians(QUIVER_TILT)), math.cos(math.radians(QUIVER_TILT)), 0.0])
    side = np.array([up[1], -up[0], 0.0])
    c = np.array(QUIVER_AT)
    rot = (0.0, 0.0, -QUIVER_TILT)     # a Bedrock turn: its x is flipped, so this leans the box's top along `up`
    half = QUIVER_LENGTH / 2.0

    def along(t, a=0.0, dz=0.0):
        return tuple(c + up * t + side * a + np.array([0.0, 0.0, dz]))

    qv.box(along(0.0), (QUIVER_WIDE, QUIVER_LENGTH, QUIVER_DEEP), "quiver", rot=rot)
    for a, dz, lift in QUIVER_FLETCHES:
        qv.box(along(half + 0.5 + lift, a, dz), (1.0, 3.0, 1.0), "fletch", rot=rot)     # 1 in, 2 out (and its lift)
    qv.box((0.0, 17.5, -3.25), (1.0, 10.8, 0.5), "belt", rot=(0.0, 0.0, 34.0))   # (under his capelet and belt)
    hb = r.part("horn_belt")
    hb.cone((4.6, 13.0, -2.0), (4.8, 10.0, 2.0), 1.8, "horn", w1=0.7, steps=3)
    fg = r.part("flag")
    box(fg, -3.5, -2.5, 8, 44, 4.5, 5.5, "shaft")
    box(fg, -2.5, 6.5, 34, 43, 4.5, 5.5, "flagcloth")
    box(fg, 0.5, 3.5, 37, 40, 5.5, 6.5, "snowflake")
    hr = r.part("hide_roll")
    hr.seg((-5.0, 23.0, 5.0), (5.0, 23.0, 5.0), 3.0, 3.0, "hide")
    box(hr, -0.5, 0.5, 21, 25, 3, 7, "leather")


QUIVER_TILT = 20.0      # the archer's quiver leans its top this far over to his right
QUIVER_AT = (-1.8, 16.8, 5.6)      # the middle of his quiver's box, on his drape (its back at 4.5)
QUIVER_LENGTH = 10.0
QUIVER_WIDE = 3.0
QUIVER_DEEP = 2.0
# its fletchings: (across its width, front to back, how much higher), two rows of two, staggered so no two of their
# faces lie in one plane with each other's or the quiver's
QUIVER_FLETCHES = ((-0.8, -0.35, 0.0), (0.6, -0.35, 0.3), (-0.6, 0.35, 0.45), (0.8, 0.35, 0.15))
SWORD_BACK_TILT = 25.0  # the elder's sheath across his back: its hilt over his right shoulder


def _bow(part, o, up, out):
    """A bow from its grip `o`: limbs bending out along `out` to their tips 10 up and down `up`, its string 3.5 out."""
    for sgn in (1.0, -1.0):
        u = up * sgn
        part.seg(tuple(o + 1.5 * u), tuple(o + 7.5 * u + 1.4 * out), 1.0, 1.0, "bowwood")
        part.seg(tuple(o + 7.5 * u + 1.4 * out), tuple(o + 10.0 * u + 3.0 * out), 1.0, 1.0, "bowwood")
    part.seg(tuple(o - 9.5 * up + 3.5 * out), tuple(o + 9.5 * up + 3.5 * out), 1.0, 1.0, "string")


class Design:
    @staticmethod
    def build(s, v):
        body(s)
        head(s)
        hoods(s)
        capes(s)
        gear(s)

    @staticmethod
    def materials(v):
        return materials(v["calling"])


# ---------------------------------------------------------------- their colours (one texture each)

def P(*hexes):
    return M.palette(*hexes)


# blues for the hoods (the user: "they have blue hoods")
NAVY = P("#0d1a33", "#132443", "#1a3055", "#233f6b", "#2f5184", "#41679f")
BLUE = P("#13284b", "#1a345f", "#224276", "#2c528e", "#3866a8", "#4c7fc1")
ROYAL = P("#14286a", "#1a3384", "#22409c", "#2d4fb3", "#3e63c7", "#5a7fd6")
STEEL = P("#1c2a3a", "#26374a", "#31465c", "#3f5770", "#516b86", "#68829d")
TEAL = P("#0f2e3a", "#153b4a", "#1c4a5c", "#255b70", "#316f86", "#46879f")
SKY = P("#244a73", "#2d5a88", "#386c9e", "#4880b3", "#5d96c6", "#7aaed6")
FROST = P("#3e5a74", "#4c6b88", "#5c7e9c", "#6e92b0", "#86a8c4", "#a4c0d6")
INDIGO = P("#141a3f", "#1b2252", "#232c66", "#2e387c", "#3c4892", "#525fac")
# clothes
WOOL_BROWN = P("#2a1e16", "#36271d", "#443226", "#543f30", "#664d3b", "#7a5e48")
WOOL_GREY = P("#2a2c30", "#36383d", "#44474d", "#55585f", "#686c73", "#7e8289")
OATMEAL = P("#4f4536", "#615543", "#746651", "#887861", "#9c8b73", "#b19f87")
MOSS = P("#26291e", "#303427", "#3c4131", "#4a503c", "#5a6149", "#6c7458")
RUST = P("#3b2117", "#4b2a1d", "#5c3424", "#6f402c", "#834d36", "#985c41")
BOOTS = P("#181310", "#211a15", "#2b221b", "#372b22", "#45362b", "#554336")
FUR_GREY = P("#4b4c50", "#5e5f64", "#727479", "#888a8f", "#9fa1a6", "#b8babe")
FUR_PALE = P("#77746c", "#8f8c84", "#a8a59d", "#c0bdb5", "#d6d3cc", "#eae8e2")
WOLF = P("#2c2e33", "#3a3c42", "#4a4d54", "#5c5f67", "#71757d", "#8a8e96")
# under every hood: darkness and two glowing blue eyes (the user, October 24, 2026), the elder's white scar
SHADOW = P("#050608", "#08090c", "#0b0d11", "#0f1217")
EYE = P("#1f5fd0", "#3c86f0", "#7fb6ff", "#c4defc")
SCAR = P("#c8ccd0", "#dcdfe2", "#eef0f2")
GLOVE = P("#1c1511", "#251c16", "#30241c", "#3c2d23", "#4a382b", "#5a4535")

# each calling: hood, its trim, capelet, tunic, skirt, sleeves, trousers (the elder: his scar)
LOOKS = {
    "spearman": dict(hood=NAVY, trim=FUR_GREY, capelet=WOLF, tunic=WOOL_BROWN, skirt=WOOL_BROWN, sleeve=WOOL_BROWN,
                     trousers=WOOL_GREY),
    "axeman": dict(hood=BLUE, trim=FUR_PALE, capelet=WOLF, tunic=RUST, skirt=RUST, sleeve=WOOL_GREY,
                   trousers=WOOL_BROWN),
    "pikeman": dict(hood=STEEL, trim=NAVY, capelet=FUR_GREY, tunic=WOOL_GREY, skirt=WOOL_GREY, sleeve=WOOL_GREY,
                    trousers=BOOTS),
    "archer": dict(hood=TEAL, trim=MOSS, capelet=TEAL, tunic=MOSS, skirt=MOSS, sleeve=MOSS, trousers=WOOL_BROWN),
    "hornblower": dict(hood=ROYAL, trim=WOLF, capelet=WOLF, tunic=OATMEAL, skirt=WOOL_BROWN, sleeve=OATMEAL,
                       trousers=WOOL_GREY),
    "farmer": dict(hood=SKY, trim=SKY, capelet=OATMEAL, tunic=OATMEAL, skirt=OATMEAL, sleeve=OATMEAL,
                   trousers=WOOL_BROWN),
    "butcher": dict(hood=NAVY, trim=NAVY, capelet=WOOL_GREY, tunic=WOOL_GREY, skirt=WOOL_GREY, sleeve=WOOL_GREY,
                    trousers=BOOTS),
    "herder": dict(hood=FROST, trim=FUR_PALE, capelet=FUR_PALE, tunic=WOOL_BROWN, skirt=WOOL_BROWN, sleeve=OATMEAL,
                   trousers=WOOL_GREY),
    "fisher": dict(hood=TEAL, trim=P("#1d2a24", "#25362e", "#2f4439", "#3b5446", "#496555", "#5a7867"),
                   capelet=TEAL, tunic=WOOL_GREY, skirt=WOOL_GREY, sleeve=WOOL_GREY, trousers=WOOL_BROWN),
    "tanner": dict(hood=STEEL, trim=RUST, capelet=RUST, tunic=RUST, skirt=WOOL_BROWN, sleeve=RUST,
                   trousers=WOOL_BROWN),
    "elder": dict(hood=INDIGO, trim=ROYAL, capelet=FUR_PALE, tunic=INDIGO, skirt=INDIGO, sleeve=INDIGO,
                  trousers=WOOL_GREY, scar=True),
}
BEARD = P("#a9adb1", "#c3c6c9", "#d8dadc", "#e9eaeb", "#f7f7f7")      # white, long
SNOWFLAKE = P("#c3d3e2", "#d8e4ee", "#ebf2f8", "#ffffff")
ICE_SOLID = P("#5f97c4", "#78add6", "#97c5e6", "#bad9f1", "#d9ebf8", "#f2f9fd")
HORN = P("#6d6a63", "#8a867e", "#a6a299", "#c1bdb3", "#d9d6cd", "#efede7")
BLADE = P("#7d868e", "#98a1a9", "#b4bcc3", "#cfd6dc", "#e6ebef")      # a bright steel edge, readable against a sleeve
QUIVER_BROWN = P("#3a2416", "#4b2f1c", "#5d3b23", "#70482b", "#845634", "#99653e")   # the archer's quiver ("browner")


def face(lk, seed):
    """The head: darkness all over (the hood's shadow), two blue eyes glowing out of it on its front (DENSITY texels
    a pixel, so half-pixel eyes); the elder's short white scar down across his right eye, which is gone: no eye behind
    it (the user, October 24, 2026: "the scar of the elder is too big, make it smaller, and it shouldn't have a blue eye
    behind the scar")."""
    base = M.skin(SHADOW, seed=seed, mottle=0.35, frost_amount=0.0, outline=0.0)
    scarred = lk.get("scar", False)

    def paint(tx):
        out = base(tx)
        col = np.array(out["color"], dtype=float)
        glow = np.array(out["glow"], dtype=float)
        x, y, z = tx.P[:, 0], tx.P[:, 1], tx.P[:, 2]
        front = (tx.N[:, 2] < -0.9) & (z < -3.9)
        ax = np.abs(x)
        eye = front & (ax >= 1.0) & (ax <= 2.5) & (y >= 28.5) & (y <= 29.5)
        if scarred:
            eye &= x > 0                                        # (his right eye, -x, gone under the scar)
        core = eye & (ax >= 1.5) & (ax <= 2.0) & (y >= 29.0)
        for mask, c in ((eye, EYE[1]), (core, EYE[3])):
            col[mask] = c
            glow[mask] = np.concatenate([np.asarray(c, dtype=float), [255.0]])
        glow[eye & ~core & (ax > 2.0)] = np.concatenate([np.asarray(EYE[0], dtype=float), [255.0]])
        if scarred:                                             # (his right: -x) from his brow down past his eye
            a0, a1 = np.array([-2.6, 30.8]), np.array([-1.0, 27.4])
            d = a1 - a0
            t = np.clip(((x - a0[0]) * d[0] + (y - a0[1]) * d[1]) / float(d @ d), 0.0, 1.0)
            dist = np.hypot(x - (a0[0] + t * d[0]), y - (a0[1] + t * d[1]))
            scar = front & (dist < 0.3)
            col[scar] = SCAR[1]
            col[scar & (dist < 0.12)] = SCAR[2]
            glow[scar] = 0.0
        out["color"] = col
        out["glow"] = glow
        return out

    paint.opaque = True
    return paint


def _furry(pal):
    """True if `pal` is one of the furs (not cloth)."""
    return any(pal is f for f in (WOLF, FUR_GREY, FUR_PALE))


def materials(calling):
    lk = LOOKS[calling]
    seed = sum(ord(c) for c in calling) * 7
    return {
        "glove": M.skin(GLOVE, seed=seed + 1, mottle=0.6, frost_amount=0.06, outline=0.08),
        "face": face(lk, seed + 2),
        "hood": M.cloth(lk["hood"], seed=seed + 6, folds=0.8, frost_amount=0.12),
        "hoodtrim": M.cloth(lk["trim"], seed=seed + 7, folds=0.5, frost_amount=0.1),
        "capelet": M.fur(lk["capelet"], seed=seed + 8, strand=2.4, frost_amount=0.2)
        if _furry(lk["capelet"]) else M.cloth(lk["capelet"], seed=seed + 8, folds=0.9),
        "tunic": M.cloth(lk["tunic"], seed=seed + 9, folds=1.0, frost_amount=0.08),
        "skirt": M.cloth(lk["skirt"], seed=seed + 10, folds=1.4, frost_amount=0.08),
        "sleeve": M.cloth(lk["sleeve"], seed=seed + 11, folds=0.9, frost_amount=0.08),
        "cuff": M.fur(FUR_GREY if lk["capelet"] is FUR_PALE else FUR_PALE, seed=seed + 12, strand=2.0,
                      frost_amount=0.2),
        "belt": M.skin(M.LEATHER, seed=seed + 13, mottle=0.6, frost_amount=0.05),
        "buckle": M.metal(M.IRON, seed=seed + 14, shine=1.0, frost_amount=0.1),
        "trousers": M.cloth(lk["trousers"], seed=seed + 15, folds=0.7, frost_amount=0.08),
        "boots": M.skin(BOOTS, seed=seed + 16, mottle=0.7, frost_amount=0.15),
        "bootfur": M.fur(FUR_PALE, seed=seed + 17, strand=2.0, frost_amount=0.25),
        "fur": M.fur(lk["trim"] if _furry(lk["trim"]) else FUR_PALE, seed=seed + 18,
                     strand=2.6, frost_amount=0.25),
        "wolf": M.fur(WOLF, seed=seed + 19, strand=3.0, frost_amount=0.3),
        "woolly": M.fur(M.WOOLLY, seed=seed + 20, strand=2.6, frost_amount=0.2),
        "horn": M.bone(HORN, seed=seed + 21, frost_amount=0.1, cracks=0.1),
        "cape": M.cloth(lk["hood"], seed=seed + 22, folds=1.6, frost_amount=0.12),
        "beard": M.fur(BEARD, seed=seed + 23, strand=2.2, frost_amount=0.2),
        "shaft": M.wood(M.WOOD, seed=seed + 25, plank=2.0, axis=1, frost_amount=0.15),
        "bowwood": M.wood(M.WOOD, seed=seed + 26, plank=2.0, axis=1, frost_amount=0.1),
        "ice": M.solid(ICE_SOLID, idx=3.2, seed=seed + 27, outline=0.08),
        "iron": M.metal(M.IRON, seed=seed + 28, shine=1.2, frost_amount=0.15),
        "blade": M.metal(BLADE, seed=seed + 41, shine=1.4, frost_amount=0.05),
        "leather": M.skin(M.LEATHER, seed=seed + 29, mottle=0.8, frost_amount=0.08),
        "quiver": M.skin(QUIVER_BROWN, seed=seed + 42, mottle=0.6, frost_amount=0.06),
        "string": M.solid(P("#bdb5a3", "#d1c9b7", "#e5ddcb"), idx=1.5, seed=seed + 30, outline=0.0),
        "bone": M.bone(M.BONE, seed=seed + 31, frost_amount=0.1),
        "shieldhide": M.worn_hide(OATMEAL, seed=seed + 32, frost_amount=0.2),
        "wicker": M.wood(OATMEAL, seed=seed + 33, plank=1.2, axis=1, frost_amount=0.15),
        "frostroot": M.solid(M.RIME, idx=3.0, seed=seed + 34, outline=0.1),
        "fishscale": M.solid(M.FISH, idx=3.0, seed=seed + 35, outline=0.1),
        "fletch": M.solid(ICE_SOLID, idx=4.0, seed=seed + 36, outline=0.05),
        "flagcloth": M.cloth(ROYAL, seed=seed + 37, folds=1.2, ragged=0.4, frost_amount=0.1),
        "snowflake": M.solid(SNOWFLAKE, idx=2.6, seed=seed + 38, outline=0.0),
        "apron": M.skin(M.LEATHER, seed=seed + 39, mottle=1.0, frost_amount=0.05),
        "hide": M.skin(OATMEAL, seed=seed + 40, mottle=1.2, frost_amount=0.15),
    }


# ---------------------------------------------------------------- the callings

# How each calling carries what it holds (its idle, walk, run, talk and chop are made for it): "pole" a long tool stood
# upright in front of its arm, "hang" a short one held out ahead of its fist, "free" nothing in its hands, "sword" the
# elder (nothing in his hands; his chop draws the sword off his back). The game's FolkAnims.Carry has the same.
STYLE = {"spearman": "pole", "axeman": "hang", "pikeman": "pole", "archer": "free", "hornblower": "pole",
         "farmer": "pole", "butcher": "hang", "herder": "pole", "fisher": "pole", "tanner": "free", "elder": "sword"}
SUFFIX = {"pole": "_pole", "hang": "_hang", "free": "", "sword": ""}


def style_anims(style):
    """The animations a way of carrying has: idle, walk, run, talk and chop (the elder's chop his own)."""
    sx = SUFFIX[style]
    return (f"idle{sx}", f"walk{sx}", f"run{sx}", f"talk{sx}", "chop_sword" if style == "sword" else f"chop{sx}")


def anim_for(key, base):
    """The name of calling `key`'s own version of `base` (idle, walk, run, talk, chop; anything else as it is)."""
    names = dict(zip(("idle", "walk", "run", "talk", "chop"), style_anims(STYLE[key])))
    return names.get(base, base)


# (key, name, kind, the gear bones it shows, the moves it plays: its own carrying's and its fighting); the game's
# FolkRole has the same
FOLK_GEAR = tuple(name for name, _p, _v in GEAR)
IDLE_ETC = ()
_CALLINGS = (
    ("spearman", "Spearman", "warrior", ("hood_peak", "cape_pelt", "spear", "shield"), ("spear_combo",)),
    ("axeman", "Axeman", "warrior", ("hood_furtrim", "cape_pelt", "axe"), ()),
    ("pikeman", "Pikeman", "warrior", ("hood_point", "cape_pelt", "long_spear"), ("thrust_sweep",)),
    ("archer", "Archer", "warrior", ("hood_cowl", "drape", "bow", "quiver"), ("quick_shot", "aimed_shot")),
    ("hornblower", "Horn-blower", "warrior", ("hood_ears", "cape_pelt", "spear", "horn_belt", "horn_held", "flag"),
     ("horn_blow", "thrust_sweep")),
    ("farmer", "Farmer", "worker", ("hood_horn", "hoe", "basket"), ()),
    ("butcher", "Butcher", "worker", ("cleaver", "apron"), ()),
    ("herder", "Herder", "worker", ("hood_flaps", "crook"), ()),
    ("fisher", "Fisher", "worker", ("hood_brim", "rod"), ()),
    ("tanner", "Tanner", "worker", ("fringe", "hide_roll"), ()),
    ("elder", "Elder", "elder", ("hood_tail", "beard", "cape_long", "sword", "sword_back"), ()),
)
CALLINGS = tuple((key, name, kind, shows, style_anims(STYLE[key]) + moves)
                 for key, name, kind, shows, moves in _CALLINGS)


def hidden_for(shows):
    return tuple(b for b in FOLK_GEAR if b not in shows)


def _callings_of(bone):
    """The callings that show a bone: every one for the shared body, the ones whose gear it is for theirs."""
    if bone not in FOLK_GEAR:
        return {key for key, _n, _k, _s, _p in CALLINGS}
    return {key for key, _n, _k, shows, _p in CALLINGS if bone in shows}


def shown_together(a, b):
    """True if some calling shows both bones (the z-fighting fix only separates boxes that are ever drawn at once)."""
    return bool(_callings_of(a) & _callings_of(b))


DESIGN = Design
BUILD_VARIANT = dict(calling="spearman")
VARIANTS = tuple(dict(name=key, design=dict(calling=key), hidden=hidden_for(shows))
                 for key, _n, _k, shows, _p in CALLINGS)


def clip_group(bone):
    if bone in ("spear", "long_spear", "axe", "sword", "hoe", "cleaver", "crook", "rod", "bow"):
        return "tool"
    if bone in ("sleeve_r", "sleeve_l"):
        return "sleeve"
    if bone in ("quiver", "sword_back"):
        return "back"
    if bone in ("body", "head", "leg_l", "leg_r", "waist"):
        return "body"
    return None


CLIP_PAIRS = (("tool", "body"), ("tool", "sleeve"), ("back", "sleeve"))
# Every tool is modelled inside the arm that holds it (its rest pose): it must be clear of the sleeve in every animation
# all the same, not just no deeper than at rest.
CLIP_STRICT = (("tool", "sleeve"),)


# ---------------------------------------------------------------- their animations (keyed by hand)

PROPS = ("horn_held", "sword")            # shown only while used
STANDS_FOR = {"horn_held": "horn_belt", "sword": "sword_back"}


def show(p, props=()):
    """Every prop hidden but `props` (and what a shown prop stands in for hidden)."""
    for prop in PROPS:
        if prop not in props:
            p.scale(prop, 0.0)
        elif prop in STANDS_FOR:
            p.scale(STANDS_FOR[prop], 0.0)


def arm(p, side, fwd=0.0, out=0.0, twist=0.0):
    """Its arm swung `fwd` degrees forward (towards its face), `out` out to its side, turned `twist` about itself."""
    s = 1.0 if side == "l" else -1.0
    p.rot(f"arm_{side}", -fwd, twist * s, -out * s)


def hand(p, side, tilt=0.0, roll=0.0):
    """What its fist holds turned `tilt` degrees the way its arm swings forward (0: upright beside it; -90: its head
    out ahead of the arm, at right angles to it; 180: upside down, pointing along the arm past the fist), its top leant
    `roll` degrees out to its side."""
    s = 1.0 if side == "l" else -1.0
    p.rot(f"hand_{side}", -tilt, 0.0, roll * s)


# Each tool is modelled through the middle of its fist, along the arm. Held at an angle to the arm, the part of it that
# rises alongside the arm would cut through the sleeve near the fist; so the right fist grips it this far towards the
# side that part leaves from: its front while its head rises up the front of the arm (tilt above -90), its back while
# its butt rises up the back (below -90: a spear levelled under a raised arm), in between at right angles.
GRIP = 1.5


def hold(p, side, fwd, out=0.0, tilt=0.0, roll=0.0):
    """Its arm swung `fwd` forward and `out` to its side, what its fist holds turned `tilt` (hand()) and gripped clear
    of the sleeve (GRIP)."""
    arm(p, side, fwd, out=out)
    hand(p, side, tilt, roll)
    if side == "r":
        p.pos("hand_r", 0.0, 0.0, -GRIP * max(-1.0, min(1.0, (tilt + 90.0) / 40.0)))


def carry(p, side, fwd, out, roll=0.0):
    """Its arm swung `fwd` forward and `out` to its side, its fist turned back by as much, so what it holds stays
    upright (or hangs straight down: a basket)."""
    hold(p, side, fwd, out, -fwd, roll)


def legs(p, swing, lift=True):
    """Its legs swung by `swing` degrees, the right one forward for positive; it rises as much as its boots' corners
    would sink (a leg swung about its hip dips its toe)."""
    p.rot("leg_r", -swing, 0.0, 0.0)
    p.rot("leg_l", swing, 0.0, 0.0)
    a = math.radians(abs(swing))
    return (HIP_Y * (1.0 - math.cos(a)) + 2.4 * math.sin(a)) if lift else 0.0


# Its right arm at rest for each way of carrying: (forward, out, tilt, roll), and how much of the walk's arm swing it
# takes. "pole": the arm brought forward and the fist turned back as much, so the tool stands straight up in front of
# the arm (the swing turns the fist with it, so it stays upright); "hang": the tool held out ahead of the fist at right
# angles to the arm, swinging with it, the way Minecraft holds a tool; "free": an empty hand.
REST = {"pole": (40.0, 6.0, -40.0, 8.0), "hang": (10.0, 4.0, -90.0, 0.0), "free": (5.0, 4.0, 0.0, 0.0),
        "sword": (5.0, 4.0, 0.0, 0.0)}
SWING_R = {"pole": 0.25, "hang": 0.6, "free": 0.6, "sword": 0.6}
# its left arm at rest (forward): a little forward holding a basket, the shield or nothing, lower with empty hands
REST_L = {"pole": 22.0, "hang": 22.0, "free": 6.0, "sword": 6.0}
REST_L_OUT = 3.0
# The archer's bow, gripped through the middle of his left fist (the only thing a free left hand holds): carried level
# at his side, turned this far from upright (its back limb a little down behind him, its front limb a little up ahead,
# its string under it; upright, its upper limb would rise up through his sleeve), whatever his arm does; upright to
# shoot, slid this far to the outer side of his fist first (turning from level to upright it lies along his arm for a
# moment: on the arm's inner side it went into his chest), so neither it nor its string comes through his sleeve.
BOW_LEVEL = 105.0
BOW_SHOT_OUT = 2.7


def right(p, style, d=0.0):
    """Its right arm at rest for `style`, swung `d` degrees further forward."""
    fwd, out, tilt, roll = REST[style]
    hold(p, "r", fwd + d, out, -(fwd + d) if style == "pole" else tilt, roll)


def left(p, style, d=0.0, out=0.0, level=1.0):
    """Its left arm at rest for `style`, swung `d` further forward and `out` further out; a free hand's bow turned
    `level` of the way from upright to level (BOW_LEVEL)."""
    fwd = REST_L[style] + d
    if style in ("free", "sword"):
        hold(p, "l", fwd, REST_L_OUT + out, BOW_LEVEL * level - fwd)
    else:
        carry(p, "l", fwd, REST_L_OUT + out)


def _breath(p, t, period=3.2):
    b = math.sin(2.0 * math.pi * t / period)
    p.pos("body", 0.0, 0.12 * b, 0.0)
    return b


def idle(rig, style):
    T = 4.0

    def frame(t):
        p = Pose()
        b = _breath(p, t, T / 2.0)
        look = math.sin(2.0 * math.pi * t / T)
        p.rot("head", 2.0 * b, 14.0 * look, 0.0)
        right(p, style, 1.5 * b)
        left(p, style, -1.5 * b)
        show(p)
        return p

    return mobkit.record(rig, "idle" + SUFFIX[style], T, frame, loop=True)


def _gait(rig, name, style, T, leg, arm_amp, lean, bob):
    def frame(t):
        p = Pose()
        w = 2.0 * math.pi * t / T
        sw = math.sin(w)
        rise = legs(p, leg * sw)
        right(p, style, -arm_amp * SWING_R[style] * sw)
        left(p, style, arm_amp * 0.6 * sw)
        p.rot("body", lean, 0.0, 0.0)
        p.pos("waist", 0.0, rise + bob * abs(math.cos(w)), 0.0)
        p.rot("head", -lean * 0.6, 0.0, 0.0)
        show(p)
        return p

    a = mobkit.record(rig, name, T, frame, loop=True)
    # how far it goes in one loop: each leg sweeps its arc twice (blocks a second for the game's matchSpeed)
    reach = 2.0 * 2.0 * 12.0 * math.sin(math.radians(leg))
    a.meta["speed"] = reach / T / 16.0
    return a


def talk(rig, style):
    T = 2.0

    def frame(t):
        p = Pose()
        _breath(p, t, 2.0)
        up = ramp(t, 0.15, 0.5) * (1.0 - ramp(t, 1.5, 1.9))
        p.rot("head", 7.0 * math.sin(2.0 * math.pi * t / 0.8) * up, 0.0, 0.0)
        right(p, style)
        wave = 8.0 * math.sin(2.0 * math.pi * t / 0.8)                  # his hand out to his side as he speaks
        left(p, style, (38.0 + wave) * up, 27.0 * up)
        p.rot("body", 0.0, 8.0 * up, 0.0)
        show(p)
        return p

    return mobkit.record(rig, "talk" + SUFFIX[style], T, frame, loop=False)


CHOP_HIT = 0.5
# the elder's sword is in his fist from as he reaches over his shoulder for it until it's on its way back down
SWORD_DRAWN = (0.34, 0.8)


def chop(rig, style):
    """Its tool raised high (at right angles to its arm, its head back over its shoulder) and brought down; the elder
    reaches over his shoulder and draws his sword for it."""
    T = 1.1
    fwd0, out0, tilt0, roll0 = REST[style]
    sword = style == "sword"

    def frame(t):
        p = Pose()
        fwd = float(keyed(t, [(0.0, fwd0), (0.38, 165.0), (CHOP_HIT, 55.0, "in"), (0.75, 50.0),
                              (T, fwd0)]))
        tilt = float(keyed(t, [(0.0, tilt0), (0.38, -90.0), (CHOP_HIT, -95.0, "in"), (0.75, -95.0), (T, tilt0)]))
        out = float(keyed(t, [(0.0, out0), (0.38, 0.0), (0.75, 0.0), (T, out0)]))
        roll = roll0 * float(keyed(t, [(0.0, 1.0), (0.3, 0.0), (0.8, 0.0), (T, 1.0)]))
        hold(p, "r", fwd, out, tilt, roll)
        left(p, style, float(keyed(t, [(0.0, 0.0), (0.38, 20.0), (CHOP_HIT, -10.0), (T, 0.0)])), 3.0)
        p.rot("body", float(keyed(t, [(0.0, 0.0), (0.38, -6.0), (CHOP_HIT, 10.0, "in"), (T, 0.0)])),
              float(keyed(t, [(0.0, 0.0), (0.38, 15.0), (CHOP_HIT, -12.0, "in"), (T, 0.0)])), 0.0)
        show(p, ("sword",) if sword and SWORD_DRAWN[0] <= t <= SWORD_DRAWN[1] else ())
        return p

    a = mobkit.record(rig, "chop_sword" if sword else "chop" + SUFFIX[style], T, frame, loop=False)
    a.meta["hits"] = [CHOP_HIT]
    return a


# A thrust: the spear levelled (this many degrees from straight up: a little below level) and driven forward from low
# at its side, underhand, its butt passing under its raised forearm (overhand, along the arm, the butt ran back up the
# sleeve); the sweep swings it out from straight ahead.
LEVEL = 95.0


def _thrust_keys(hits, T, sweep_last):
    """Keys for its right arm (fwd, out, tilt) for thrusts at `hits` (the last one a sweep if `sweep_last`)."""
    fwd0, out0, tilt0, _roll = REST["pole"]
    fwd = [(0.0, fwd0)]
    out = [(0.0, out0)]
    tilt = [(0.0, tilt0)]
    for i, h in enumerate(hits):
        last = sweep_last and i == len(hits) - 1
        keys = (((h - 0.18, 34.0, 4.0), (h, 52.0, 55.0, "in"), (h + 0.14, 46.0, 60.0)) if last else
                ((h - 0.18, 8.0, 0.0), (h, 55.0, 0.0, "in"), (h + 0.14, 48.0, 0.0)))
        for k in keys:
            fwd.append((k[0], k[1]) + k[3:])
            out.append((k[0], k[2]) + k[3:])
            tilt.append((k[0], -LEVEL - k[1]) + k[3:])
    fwd += [(T, fwd0)]
    tilt += [(T, tilt0)]
    out += [(T, out0)]
    return fwd, out, tilt


def _thrusts(rig, name, T, hits, sweep_last):
    fwd_k, out_k, tilt_k = _thrust_keys(hits, T, sweep_last)

    def frame(t):
        p = Pose()
        hold(p, "r", float(keyed(t, fwd_k)), float(keyed(t, out_k)), float(keyed(t, tilt_k)),
             REST["pole"][3] * (1.0 - ramp(t, 0.0, 0.2) * (1.0 - ramp(t, T - 0.3, T))))
        lunge = min(1.0, sum(max(0.0, 1.0 - abs(t - h) / 0.2) for h in hits))
        p.rot("body", 8.0 * lunge, -10.0 * lunge, 0.0)
        p.pos("waist", 0.0, legs(p, 10.0 * lunge), 0.0)
        left(p, "pole", 25.0 * min(1.0, lunge + ramp(t, 0.0, 0.2) * (1.0 - ramp(t, T - 0.3, T))), 5.0)
        show(p)
        return p

    a = mobkit.record(rig, name, T, frame, loop=False)
    a.meta["hits"] = list(hits)
    return a


def spear_combo(rig):
    return _thrusts(rig, "spear_combo", 1.9, (0.4, 0.8, 1.3), True)


def thrust_sweep(rig):
    return _thrusts(rig, "thrust_sweep", 1.4, (0.4, 0.9), True)



def _shot(rig, name, T, release):
    fwd0, out0, _tilt0, _roll0 = REST["free"]

    def frame(t):
        p = Pose()
        up = ramp(t, 0.1, 0.45) * (1.0 - ramp(t, T - 0.35, T))
        draw = ramp(t, release - 0.55, release - 0.05) * (1.0 - ramp(t, release, release + 0.05))
        # the bow held out ahead, upright, slid to his fist's outer side as he raises it (before it turns past
        # upright along his arm on the way)
        left(p, "free", (86.0 - REST_L["free"]) * up, -9.0 * up, level=1.0 - up)
        p.pos("hand_l", BOW_SHOT_OUT * min(1.0, up / 0.4), 0.0, 0.0)
        rf = fwd0 + ((93.0 - fwd0) - 29.75 * draw) * up
        arm(p, "r", rf, out=out0 * (1.0 - up) - 26.0 * up * (1.0 - draw * 0.3))
        p.rot("body", 0.0, 22.0 * up, 0.0)
        p.rot("head", 0.0, -20.0 * up, 0.0)
        show(p)
        return p

    a = mobkit.record(rig, name, T, frame, loop=False)
    a.meta["release"] = release
    return a


def quick_shot(rig):
    return _shot(rig, "quick_shot", 1.9, 1.15)


def aimed_shot(rig):
    return _shot(rig, "aimed_shot", 2.9, 1.7)


HORN_CALL = 0.85


def horn_blow(rig):
    T = 3.7
    l0 = REST_L["pole"]

    def frame(t):
        p = Pose()
        up = ramp(t, 0.15, HORN_CALL) * (1.0 - ramp(t, 2.9, 3.4))
        arm(p, "l", l0 + (139.0 - l0) * up, out=REST_L_OUT - 31.0 * up)
        hand(p, "l", -l0 * (1.0 - up) - 40.0 * up, 0.0)
        right(p, "pole")
        p.rot("head", -14.0 * up, 0.0, 0.0)
        p.rot("body", -4.0 * up, 0.0, 0.0)
        show(p, ("horn_held",) if t > 0.15 and t < 3.4 else ())
        return p

    a = mobkit.record(rig, "horn_blow", T, frame, loop=False)
    a.meta["blow"] = HORN_CALL
    return a


def animations(rig):
    out = {}
    for style in ("free", "pole", "hang"):
        sx = SUFFIX[style]
        out["idle" + sx] = idle(rig, style)
        out["walk" + sx] = _gait(rig, "walk" + sx, style, 1.0, 28.0, 24.0, 0.0, 0.25)
        out["run" + sx] = _gait(rig, "run" + sx, style, 0.62, 50.0, 46.0, 12.0, 0.6)
        out["talk" + sx] = talk(rig, style)
        out["chop" + sx] = chop(rig, style)
    out["chop_sword"] = chop(rig, "sword")
    out["spear_combo"] = spear_combo(rig)
    out["thrust_sweep"] = thrust_sweep(rig)
    out["quick_shot"] = quick_shot(rig)
    out["aimed_shot"] = aimed_shot(rig)
    out["horn_blow"] = horn_blow(rig)
    return out
