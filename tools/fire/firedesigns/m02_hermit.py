"""No. M2 Cinder Hermit: a regular mob of the Cinder Wastes, a hermit crab in one of eight shells."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames, rot_matrix
from firedesigns import f13_hermit as H
from rimekit.shapes import unit

NUMBER = 2
ID = "mobhermit"
NAME = "Cinder Hermit"
ONE_ROW = True          # every shell in the gallery's front row, side by side
BLURB = ("A regular mob of the Cinder Wastes (the user's pick, October 26, 2026: the fire servant candidate 13): a "
         "hermit crab the size of a cart, a huge claw of magma rock and a small picking claw, its eyes glowing on "
         "their stalks, living in whatever it found in the ash. Each one you meet wears one of eight shells (ten were made; "
         "the user dropped G, the knight's helm, and H, the charred stump, on October 27: \"pretty lazy\").")
VARIANTS = (
    dict(letter="A", mood="bronze bell", shell="bell", note="A: a toppled bronze bell, green with patina, cracked."),
    dict(letter="B", mood="iron brazier", shell="brazier", note="B: a great iron brazier, a fire burning in it."),
    dict(letter="C", mood="horned skull", shell="skull", note="C: the charred skull of something huge and horned."),
    dict(letter="D", mood="funerary urn", shell="urn", note="D: a cracked funerary urn of painted clay, smoking."),
    dict(letter="E", mood="magma conch", shell="conch",
         note="E: a spiral conch of cooled magma, glowing in the seams between its whorls."),
    dict(letter="F", mood="crystal geode", shell="geode",
         note="F: a cracked geode: a rind of basalt, glowing amber crystals inside its broken side."),
    dict(letter="I", mood="giant lantern", shell="lantern",
         note="I: a toppled giant iron lantern, its glass cracked, its flame still burning inside."),
    dict(letter="J", mood="colossus shard", shell="shard",
         note="J: a broken-off chunk of a Cinder Colossus's volcano back, its crater still glowing."),
)
FREE = ("smoke",)

SHELL_C = np.array([0.0, 38.0, 24.0])      # where a shell sits on its back
CONCH_CHAMBER = 0.36                       # the share of the conch, from its mouth, that's its straight body whorl
SOOT_GLASS = F.palette("#0c0806", "#160f0b", "#22170f", "#332214", "#4a311a", "#664622")   # the lantern's smoked glass


# The bronze bell (October 27, the user: "that doesn't look like a bell, change that up"; "this body doesn't fit
# right"): a real church bell lying on its side, round (BELL_SIDES boxes round it), its crown end lower so it rests on
# its lip and its shoulder, its mouth behind the crab's legs so they come out of its mouth, not through its sides.
BELL_LIP = np.array([0.0, 32.5, -6.0])      # the middle of its mouth
BELL_TILT = 14.0                            # degrees its axis dips towards its crown
BELL_SIDES = 12
BELL_WALL = 4.0                             # its wall; the lip (the sound bow) is thicker
BELL_LIP_WALL = 7.0
# Its outline from the lip back: (distance along its axis, outer radius): the thick lip, the flare, the long waist,
# the shoulder rounding into the crown.
BELL_PROFILE = ((0.0, 33.0), (4.0, 31.5), (9.0, 28.0), (16.0, 25.4), (30.0, 24.2), (44.0, 22.6), (50.0, 20.4),
                (54.0, 17.6))
BELL_CRACK = ((0.0, 62.0), (7.0, 55.0), (14.0, 64.0), (22.0, 57.0), (31.0, 66.0))    # (along it, degrees round)


def bell_radius(d):
    """The bell's outer radius `d` units back from its lip."""
    ds = [a for a, _r in BELL_PROFILE]
    return float(np.interp(d, ds, [r for _a, r in BELL_PROFILE]))


def _bell_wall(b, d0, r0, d1, r1, thick, mat, n=BELL_SIDES):
    """One stretch of the bell's round wall (its axis local z): n boxes round it, each tilted along the outline from
    (d0, r0) to (d1, r1), `thick` deep from its outer face in, wide enough that they meet at their outer corners."""
    w = 2.0 * math.tan(math.pi / n) * max(r0, r1) + 0.4
    for k in range(n):
        a = 2.0 * math.pi * k / n
        out = np.array([math.cos(a), math.sin(a), 0.0])
        tan = np.array([-math.sin(a), math.cos(a), 0.0])
        b.seg(out * (r0 - thick / 2.0) + [0.0, 0.0, d0], out * (r1 - thick / 2.0) + [0.0, 0.0, d1], w, thick, mat,
              side=tan, ext=0.6, inflate=0.1 * (k % 2))


def _mouth_strips(ri, ro):
    """
    [(y0, y1, half-width)]: level strips filling a round mouth of radius ri, every corner within ro (inside the lip);
    their sizes whole units (every box edge is rounded to one), widths rounded up and heights down.
    """
    w = math.ceil(ri * 2.0) / 2.0
    h = math.floor(math.sqrt(ro * ro - w * w) * 2.0) / 4.0 * 2.0
    out = [(-h, h, w)]
    a = h
    while a < ri - 0.01:
        w = math.ceil(math.sqrt(max(0.0, ri * ri - a * a)) * 2.0) / 2.0
        top = a + max(1.0, math.floor(math.sqrt(ro * ro - w * w) - a))
        if top >= ri:
            top = a + math.ceil(ri - a)
        out += [(a, top, w), (-top, -a, w)]
        a = top
    return out


def round_mouth(s, centre, r_out, wall, back, front, mat, dark_at):
    """
    A round mouth set into a shell's front (the crab's door): a rim of `mat` (BELL_SIDES boxes round, `wall` thick, its
    outer radius `r_out`) running from `back` units behind `centre` (sunk into the shell) to `front` units before it,
    and the dark inside filling it `dark_at` units before `centre` (October 28, the user: the old flat black boxes
    "end outside what a natural shell would look like"). `centre` is on the shell's front, its axis straight ahead.
    """
    c = np.asarray(centre, dtype=float)
    nk = Xform(s, R=rot_matrix((0, 1, 0), 180.0), shift=c)
    _bell_wall(nk, -back, r_out, front, r_out, wall, mat)
    ri = (r_out - wall) / math.cos(math.pi / BELL_SIDES)
    for y0, y1, w in _mouth_strips(ri, r_out - 0.2):
        nk.seg((-w, (y0 + y1) / 2.0, dark_at), (w, (y0 + y1) / 2.0, dark_at), y1 - y0, 2.0, "dark", side=(0, 1, 0))
    return nk


def bell(s):
    """A toppled bronze church bell: its mouth forward round the crab, the dark inside, the crown at the back with its
    hanging loop, raised bands, green patina and a glowing crack down its side."""
    b = Xform(s, R=rot_matrix((1, 0, 0), BELL_TILT), shift=BELL_LIP)
    _bell_wall(b, -0.5, BELL_PROFILE[0][1], 2.5, BELL_PROFILE[0][1], BELL_LIP_WALL, "bronze")
    for (d0, r0), (d1, r1) in zip(BELL_PROFILE[:-1], BELL_PROFILE[1:]):
        _bell_wall(b, d0, r0, d1, r1, BELL_WALL, "bronze")
    # Raised bands: over the sound bow and round the shoulder.
    for d0, d1 in ((7.0, 9.0), (40.0, 42.5)):
        _bell_wall(b, d0, bell_radius(d0) + 1.2, d1, bell_radius(d1) + 1.2, 2.4, "bronze")
    # The crown, a low dome, the canon and its hanging loop.
    r = BELL_PROFILE[-1][1]
    b.blob((0.0, 0.0, 53.5), (r - 2.0, 2.5, r - 2.0), "bronze", axis=(0.0, 0.0, 1.0), slices=2)
    b.blob((0.0, 0.0, 56.5), (r * 0.62, 1.5, r * 0.62), "bronze", axis=(0.0, 0.0, 1.0), slices=2)
    b.seg((0.0, 0.0, 56.0), (0.0, 0.0, 60.0), 9, 6, "bronze", side=(1, 0, 0))
    b.ring((0.0, 0.0, 63.5), (1.0, 0.0, 0.0), 5.5, 3, 3, "bronze", n=10)
    # The crack down its side, glowing.
    pts = []
    for d, deg in BELL_CRACK:
        a = math.radians(deg)
        pts.append((np.array([math.cos(a), math.sin(a), 0.0]), np.array([0.0, 0.0, d]), bell_radius(d)))
    for (o0, z0, r0), (o1, z1, r1) in zip(pts[:-1], pts[1:]):
        p0, p1 = o0 * (r0 + 0.3) + z0, o1 * (r1 + 0.3) + z1
        b.seg(p0, p1, 2.5, 1.5, "crack", side=np.cross(unit(o0 + o1), unit(p1 - p0)))
    # The dark inside, filling its mouth just behind the lip (its corners hidden in the lip).
    ri = (BELL_PROFILE[0][1] - BELL_LIP_WALL) / math.cos(math.pi / BELL_SIDES)
    for y0, y1, w in _mouth_strips(ri, BELL_PROFILE[0][1] - 0.2):
        b.seg((-w, (y0 + y1) / 2.0, 1.5), (w, (y0 + y1) / 2.0, 1.5), y1 - y0, 2.0, "dark", side=(0, 1, 0))


def brazier(s):
    c = np.array([0.0, 30.0, 26.0])
    s.blob(c, (34.0, 26.0, 34.0), "iron", slices=6)
    s.ring(c + [0.0, 22.0, 0.0], (0.0, 1.0, 0.0), 33.0, 4, 5, "iron", n=16)
    for k in range(8):
        a = 2.0 * math.pi * (k + 0.5) / 8              # (none straight ahead, over its mouth)
        s.seg(c + [math.cos(a) * 30.0, 8.0, math.sin(a) * 30.0], c + [math.cos(a) * 35.0, 30.0, math.sin(a) * 35.0],
              4, 4, "iron")
    s.blob(c + [0.0, 22.0, 0.0], (29.0, 3.0, 29.0), "coal", slices=1)
    for k in range(7):
        a = k * 2.4
        r = 6.0 + (k % 3) * 8.0
        flames(s, c + [math.cos(a) * r, 24.0, math.sin(a) * r], 30.0 - r * 0.5, 16.0, "flame", layers=3,
               twist=k * 25.0)
    round_mouth(s, (0.0, 26.0, -8.0), 20.0, 4.0, 16.0, 4.0, "iron", 2.0)


def skull(s):
    sk = Xform(s, pivot=(0.0, 40.0, 24.0), axis=(1, 0, 0), deg=-8.0)
    sk.blob((0.0, 42.0, 26.0), (34.0, 30.0, 36.0), "bone", slices=7)
    for sign in (1.0, -1.0):
        sk.seg((sign * 16.0, 50.0, -2.0), (sign * 16.0, 50.0, -10.0), 14, 12, "dark", side=(1, 0, 0))
        sk.seg((sign * 16.0, 50.0, -8.0), (sign * 16.0, 50.0, -10.5), 5, 5, "socketglow", side=(1, 0, 0))
        sk.curve([(sign * 26.0, 64.0, 18.0), (sign * 46.0, 78.0, 22.0), (sign * 58.0, 74.0, 6.0),
                  (sign * 54.0, 60.0, -6.0)], 7, [16, 12, 8, 4], "horn")
    # Its open maw, the crab's door (October 27, the user: "the grey box in the middle of the skull's entrance", and
    # the dark looking out of place: its old muzzle stuck out through the dark from behind): the upper jaw jutting over
    # it, the lower jaw's edge under it, cheekbones either side (behind the crab's legs), and the dark of its throat set
    # back between them.
    s.rbox((0.0, 41.0, -12.0), (50, 6, 16), "bone", c=2)       # (under its eye holes: the crab peeks out of them)
    s.rbox((0.0, 8.0, -11.0), (40, 6, 14), "bone", c=2)
    for sign in (1.0, -1.0):
        s.rbox((sign * 22.0, 27.0, -6.0), (6, 38, 8), "bone", c=2)
    s.seg((0.0, 26.0, -10.0), (0.0, 26.0, -8.0), 40, 32, "dark", side=(1, 0, 0))
    # Its teeth round the crab's door (October 27, the user: "add more personality, like teeth to the skull"): fangs
    # hanging from its upper jaw, the long ones at its corners, a few broken stumps below; kept to the mouth's edges,
    # clear of the crab's body. A brow ridge over each socket, and a glowing crack across its crown.
    for x, length, w in SKULL_FANGS:
        for sign in (1.0, -1.0):
            s.cone((sign * x, 39.0, -19.5), (sign * x * 1.02, 39.0 - length, -20.0), w, "tooth", steps=2)
    for x, length, w in SKULL_LOWER_TEETH:
        for sign in (1.0, -1.0):
            s.cone((sign * x, 10.0, -16.5), (sign * x, 10.0 + length, -17.0), w, "tooth", steps=2)
    for sign in (1.0, -1.0):
        sk.seg((sign * 8.0, 59.0, -4.0), (sign * 25.0, 56.0, -2.0), 6, 5, "bone")
    for a, b in zip(SKULL_CRACK[:-1], SKULL_CRACK[1:]):
        sk.seg(a, b, 2.5, 2, "crack", side=(0, 1, 0))


SKULL_FANGS = ((5.0, 2.0, 3), (10.5, 3.0, 4), (16.0, 8.0, 5))       # (x, length, width) each side, its upper jaw
SKULL_LOWER_TEETH = ((12.0, 4.0, 3), (17.0, 6.0, 4))
SKULL_CRACK = ((-6.0, 73.5, 6.0), (-12.0, 72.5, 16.0), (-8.0, 73.5, 26.0), (-15.0, 71.5, 36.0), (-11.0, 68.5, 48.0))


URN_C = np.array([0.0, 32.0, 30.0])     # the middle of its belly (resting on the ground)
URN_NECK = 21.0                         # its neck's outer radius: a wide mouth, the crab's door
URN_LIP = 23.5                          # its flared lip's outer radius
URN_LIP_WALL = 5.0


def urn(s):
    """
    A funerary urn of painted clay lying on its side, its wide round mouth forward round the crab, a wisp of smoke from
    it (October 27, the user: its black "looks out of place": the old urn's narrow neck was up above the crab, so its
    dark was a square sticking out under it; now the mouth is round, at the crab's height, the dark filling it).
    """
    c = URN_C
    u = Xform(s, pivot=c, axis=(1, 0, 0), deg=-90.0)
    u.blob(c, (34.0, 34.0, 32.0), "urn", slices=7)
    for y in (-12.0, 4.0, 18.0):
        u.ring(c + [0.0, y, 0.0], (0.0, 1.0, 0.0), 33.5 * math.sqrt(max(0.1, 1.0 - (y / 34.0) ** 2)), 2, 4, "paint",
               n=16)
    u.seg(c + [0.0, -34.0, 0.0], c + [0.0, -38.0, 0.0], 30, 30, "urn")
    # Its neck (round, BELL_SIDES boxes round it) and flared lip, a painted band, the dark inside.
    nk = Xform(s, R=rot_matrix((0, 1, 0), 180.0), shift=(0.0, c[1], c[2] - 30.0))
    _bell_wall(nk, -2.0, URN_NECK, 8.0, URN_NECK - 1.5, 4.0, "urn")
    _bell_wall(nk, 2.0, URN_NECK + 0.9, 4.0, URN_NECK + 0.6, 1.8, "paint")
    _bell_wall(nk, 8.0, URN_LIP, 11.0, URN_LIP, URN_LIP_WALL, "urn")
    ri = (URN_LIP - URN_LIP_WALL) / math.cos(math.pi / BELL_SIDES)
    for y0, y1, w in _mouth_strips(ri, URN_LIP - 0.2):
        nk.seg((-w, (y0 + y1) / 2.0, 9.5), (w, (y0 + y1) / 2.0, 9.5), y1 - y0, 2.0, "dark", side=(0, 1, 0))
    s.blob((10.0, c[1] + URN_LIP + 1.0, -6.0), (5.0, 4.0, 5.0), "smoke", slices=2)
    s.blob((13.0, c[1] + URN_LIP + 9.0, -8.0), (6.0, 5.0, 6.0), "smoke", slices=2)


def conch(s):
    """
    A spiral shell of cooled magma: a wide body whorl behind its mouth (room for the crab to back into, October 27),
    then whorls winding round and narrowing to a point at the back, glowing seams.
    """
    n = 26
    prev = None
    for k in range(n):
        t = k / (n - 1)
        a = max(0.0, t - CONCH_CHAMBER) / (1.0 - CONCH_CHAMBER) * math.pi * 3.2
        r = 30.0 * (1.0 - t) + 4.0
        y = 36.0 + math.sin(a) * r * 0.75
        x = math.cos(a) * r * 0.75 * min(1.0, a / (math.pi * 0.5))     # (the body whorl straight behind its mouth)
        z = -8.0 + t * 70.0
        p = np.array([x * 0.4, y, z])
        w = 52.0 * (1.0 - t) ** 0.8 + 6.0
        if prev is not None:
            s.seg(prev, p, w, w * 0.9, "conch", ext=2.0, inflate=0.1 * (k % 2))
            if k % 3 == 0:
                s.ring(p, p - prev, w * 0.52, 1, 2, "seamglow", n=10, phase=k * 0.2)
        prev = p
    s.cone(prev, prev + np.array([0.0, 4.0, 14.0]), 6, "conch", steps=2)
    # Knobs along the top whorl.
    for k in range(5):
        p = np.array([0.0, 64.0 - k * 3.0, 4.0 + k * 10.0])
        s.cone(p - np.array([0.0, 4.0, 0.0]), p + np.array([0.0, 6.0, 2.0]), 6, "conch", steps=2)
    s.seg((0.0, 34.0, -12.0), (0.0, 34.0, -8.0), 46, 42, "dark", side=(1, 0, 0))


def geode(s):
    """A geode: a lumpy rind of basalt, broken open on one side, glowing crystals packed inside."""
    c = np.array([0.0, 38.0, 26.0])
    s.blob(c, (30.0, 28.0, 32.0), "rind", slices=7)
    # The broken side (its own left): a hollow of crystals.
    hole = c + np.array([24.0, 6.0, 4.0])
    s.blob(hole, (8.0, 16.0, 18.0), "dark", slices=4)
    for k in range(14):
        a = k * 2.399
        p = hole + np.array([-3.0, math.sin(a) * 12.0, math.cos(a) * 14.0])
        d = unit(np.array([1.0, math.sin(a) * 0.5, math.cos(a) * 0.5]))
        s.cone(p - d * 2.0, p + d * (8.0 + (k % 3) * 3.0), 4 + (k % 2) * 2, "crystal", steps=2)
    # A few crystals breaking through the rind elsewhere.
    for k in range(5):
        a = k * 1.3 + 0.5
        p = c + np.array([math.cos(a) * 20.0, 22.0, math.sin(a) * 22.0])
        s.cone(p - np.array([0.0, 4.0, 0.0]), p + np.array([math.cos(a) * 4.0, 10.0, math.sin(a) * 4.0]), 5, "crystal",
               steps=2)
    # Its mouth, broken through the rind and lined with crystals (October 28, the user: "remove that black basalt bar
    # thing"; the old flat dark box's middle was crossed by the rind's widest slice).
    nk = round_mouth(s, (0.0, 30.0, -5.0), 19.0, 4.0, 24.0, 4.0, "rind", 2.0)
    for k in range(10):
        a = 2.0 * math.pi * (k + 0.25) / 10
        o = np.array([math.cos(a), math.sin(a), 0.0])
        p = o * (16.5 + (k % 3)) + [0.0, 0.0, 3.0]
        nk.cone(p, p + o * (2.0 + (k % 2) * 2.0) + [0.0, 0.0, 5.0 + (k % 3) * 2.0], 3 + (k % 2), "crystal", steps=2)


def lantern(s):
    """A toppled giant iron lantern lying on its side, its open bottom facing forward round the crab: four corner
    posts, the cap at the back, cracked glass panes, its flame still burning inside."""
    c = np.array([0.0, 30.0, 24.0])
    lt = Xform(s, pivot=c, axis=(1, 0, 0), deg=90.0)
    half = 25.0
    lt.box(c + np.array([0.0, 6.0, 0.0]), (20, 30, 20), "flame_core")
    for x in (-1.0, 1.0):
        for z in (-1.0, 1.0):
            lt.box(c + np.array([x * half, 0.0, z * half]), (6, 58, 6), "iron")
    # The bottom frame (open: the crab's door) and the top plate under the cap.
    for x in (-1.0, 1.0):
        lt.box(c + np.array([x * half, -28.0, 0.0]), (6, 5, 56), "iron")
        lt.box(c + np.array([0.0, -28.0, x * half]), (56, 5, 6), "iron")
    lt.box(c + np.array([0.0, 28.0, 0.0]), (56, 5, 56), "iron")
    for nrm in ((1.0, 0.0, 0.0), (-1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, 0.0, -1.0)):
        n = np.array(nrm)
        lt.plate(c + n * (half - 0.5), n, (0.0, 1.0, 0.0), 44, 52, 1, "lanternglass")
    lt.cone(c + np.array([0.0, 30.0, 0.0]), c + np.array([0.0, 46.0, 0.0]), 40, "iron", steps=4)
    lt.ring(c + np.array([0.0, 48.0, 0.0]), (0.0, 0.0, 1.0), 6.0, 3, 3, "iron", n=8)
    # The flame's wick reaching out to the crab, which lives round it.
    s.seg((0.0, 30.0, -4.0), (0.0, 30.0, 12.0), 10, 10, "flame_core")
    s.seg((0.0, 30.0, -5.0), (0.0, 30.0, -2.0), 44, 44, "dark", side=(1, 0, 0))


def shard(s):
    """A broken-off chunk of a Cinder Colossus's volcano back: a cone of scorched stone and obsidian, its crater still
    glowing, obsidian spikes, the broken underside rough with lava."""
    c = np.array([0.0, 30.0, 26.0])
    s.blob(c, (34.0, 22.0, 34.0), "stone", slices=5)
    s.cone(c + np.array([0.0, 14.0, 2.0]), c + np.array([0.0, 42.0, 6.0]), 46, "stone", w1=24.0, steps=5)
    s.blob(c + np.array([0.0, 42.0, 6.0]), (10.0, 2.0, 10.0), "crater", slices=1)
    for k in range(6):
        a = 2.0 * math.pi * k / 6 + 0.3
        p = c + np.array([math.cos(a) * 26.0, 8.0, math.sin(a) * 26.0])
        s.cone(p, p + np.array([math.cos(a) * 10.0, 18.0, math.sin(a) * 10.0]), 7, "spike", steps=3)
    for k in range(4):
        a = k * 1.6
        p = c + np.array([math.cos(a) * 16.0, 30.0, math.sin(a) * 16.0 + 4.0])
        s.seg(p, p + np.array([math.cos(a) * 6.0, -14.0, math.sin(a) * 6.0]), 3, 2, "crater")
    round_mouth(s, (0.0, 28.0, -8.0), 19.0, 4.0, 26.0, 3.0, "spike", 1.5)


SHELLS = {"bell": bell, "brazier": brazier, "skull": skull, "urn": urn, "conch": conch, "geode": geode, "lantern": lantern,
          "shard": shard}


def build(s, v):
    H.crab(s)
    SHELLS[v["shell"]](s.part("shell"))


def materials(v):
    out = H.materials(v)
    out.update({
        "conch": F.crust(F.MAGMA_CRUST, seed=1811, cracks=0.25, cell=8.0, width=0.5),
        "seamglow": F.lava(F.EMBER, seed=1813),
        "rind": F.crust(F.BASALT, seed=1815, cracks=0.05, cell=7.0, ash_top=0.4),
        "crystal": F.glass(F.GLASS_AMBER, alpha=(150, 220), seed=1817, cracks=0.1, glow_pal=F.EMBER, glow_alpha=180.0),
        "flame_core": F.lava(F.WHITE_HOT, seed=1827),
        # (sooty smoked glass you can't see through, its flame showing only in its cracks: October 27, the user
        # found it weird to see the crab through it when it hid)
        "lanternglass": F.obsidian(SOOT_GLASS, seed=1829, gloss=1.2, veins=0.12, vein_pal=F.EMBER, outline=0.12),
        "tooth": F.bone(F.BONE_CHAR, seed=1841, cracks=0.2),
        "stone": F.crust(F.palette("#141210", "#1d1a17", "#27231f", "#322d28", "#3f3832", "#4e463e"), seed=1831,
                         cracks=0.2, cell=6.0),
        "crater": F.lava(seed=1833),
        "spike": F.obsidian(seed=1835, gloss=1.8),
    })
    return out
