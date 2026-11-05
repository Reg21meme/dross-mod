"""
The Emberdactyl, round 3 (the user's look in-game, October 27, 2026): round 2's B (the half-moon crest, the longer
horns, obsidian) with wings 33 blocks across ("I think a wingspan of 33 best fits"), its young at round 2's size
("the current baby sizes also work"), and their notes:

  - "not all of the semicircle thing is on his beak": the half moon now stands on the bill all along it, its foot
    following the bill's top line down to the tip;
  - "his wing is not good": rebuilt: a smoother, thinner membrane (each stretch between two bones cut in two, four
    rows across it), its trailing edge scalloped between the bones, ribs running across it from the bones to the edge,
    small thorns swept back along the forearm in place of round 2's stubby spines, a big thumb claw at the wrist;
  - "he seems so chubby with the bear belly, slim that down": a slender, deep-keeled chest and no pot belly;
  - "a little less lava on the scales" (the adult), "a little more lava on the kid" (the young).

Then the user's second look the same day:

  - the screech: "his teeth stay with his body ... remove that lava part near his mouth, only keep his tongue": the
    teeth are rooted in the jaw they belong to (a row hanging from the upper bill, a row standing in the lower jaw, so
    each row opens with its own jaw), the glowing lump at the back of the mouth is gone, and the tongue lies inside
    the lower jaw (`teeth`, `tongue`);
  - "the back half of his wing intersects with his body" (flying) and "part of his claw or his wing part goes through
    his body" (walking): the membrane no longer starts inside the body (it leaves the arm where the arm leaves the
    body, `MEM_FROM`, and its root stands just off the flank, `ROOT_*`), and each stretch of it hangs on a bone of its
    own (`mem_<bone>_<side>`) hinged along its bone, so the animations fold it up out of the body's way
    (firemobs/wingfold.py);
  - the young: "much more magma, not enough": lava running all through its hide, horns, wing bones and wings;
  - "give me variations on his eyes, it should be similar to Rodan": ten eye studies (`EYE_STYLES`, `build_eye_study`):
    the adult's head and neck with ten kinds of small, fierce, glowing eye deep under a heavy brow;
  - "as he flies fire needs to fall from his wings": marker bones along each wing's trailing edge and at its tip
    (`ember_<side><n>`), for the game's falling fire.

Every part goes on its own bone (`s.part`), so the same code builds the statue (one bone) and the animated mob
(firemobs/emberdactyl.py): body, neck, head, jaw, tail, each wing's arm, forearm, hand, finger and tip and the
membrane on each, each leg's thigh, shin and foot. Design units are Bedrock units at scale 1 (16 a block), its front
-z, its own left +x; the wingspan sets the scale (`scale_for`).
"""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, leg, rot_matrix
from firekit.ptero import BODY, OBS_MEMBRANE, OBS_RED, PITCH, wing_points
from kit import noise
from kit.paint import pick, rgba
from rimekit.mats import _factory, _out, _speck
from rimekit.shapes import Scaled, unit, v3

ADULT_SPAN = 33.0                  # blocks, tip to tip (the user's pick)
YOUNG_SPAN = 22.5                  # its young (the user: "the current baby sizes also work")
SLENDER = 0.8                      # the torso and limbs this much of round 2's girth
SCALLOP = 0.16                     # the trailing edge sags this far back towards the bones between each pair
MOON_RADIUS = 12.0                 # units: the half moon on the bill
MOON_FROM, MOON_TO = -10.0, -34.0  # it stands on the bill from here to here (units in front of the head's middle)
CURLED_HORN = 0.4                  # in the egg its horns are this long (soft, folded back)
SIDES = (("l", 1.0), ("r", -1.0))
WING_BONES = ("arm", "fore", "hand", "finger", "tip")
# The membrane (the user: "the back half of his wing intersects with his body"): it starts this far out along the arm
# (where the arm leaves the body), and its root on the flank stands ROOT_GAP units off the body's side, ROOT_UP above
# the torso's middle and ROOT_BACK behind it (the torso's own frame).
MEM_FROM = 0.28
ROOT_GAP = 3.0
ROOT_UP = 4.0
ROOT_BACK = 14.0
INNER_CHORD = 0.6                  # the inner wing's membrane this share as deep (its root and the elbow's trailing point)
# Where the game's falling fire comes from: points on each wing's trailing edge ((stretch, share along it)), then the
# wingtip (the tip bone's end); each a marker bone ember_<side><n> (n from 0) on that stretch's membrane bone.
EMBER_SPOTS = ((0, 0.55), (1, 0.0), (1, 0.5), (2, 0.0), (2, 0.5), (3, 0.0), (3, 0.5), (4, 0.0), (4, 0.5))
# The bills (head units: z, the middle's height, width, depth), the teeth they hold and the tongue.
UPPER_BILL = ((-8.0, 1.0, 9.0, 9.0), (-26.0, -1.0, 6.0, 6.0), (-50.0, -9.0, 2.0, 2.0))
LOWER_BILL = ((-8.0, -6.0, 7.0, 4.0), (-24.0, -8.5, 5.0, 3.0), (-44.0, -12.0, 2.0, 2.0))
UPPER_TEETH = (-12.0, -17.0, -22.0, -27.0, -32.0, -37.0)
LOWER_TEETH = (-14.5, -19.5, -24.5, -29.5, -34.5)
TONGUE = ((-9.0, 3.2), (-20.0, 2.6), (-31.0, 1.4))       # (z, width)
LID_X = 5.4                        # head units: an eyelid's middle out from the head's middle at rest (behind the eye)
LID_SLIDE = 3.0                    # ... and how far a death slides it out, over the eye
# What its body breaks into as it dies (death_break, death_crumble: firemobs/emberdactyl.py; the adult only): plates
# and lumps of its crust hidden inside its body, each on a bone of its own (chunk_<n>), slid out and dropped as it
# breaks apart. (Offset from the body's middle in its own frame, size, material), design units.
CHUNKS = (((-2.0, 5.0, -6.0), (6.0, 2.5, 7.0), "hide"), ((3.0, 4.0, 2.0), (8.0, 2.5, 7.0), "spike"),
          ((-3.0, 5.0, 9.0), (7.0, 2.5, 7.0), "hide"), ((3.0, 3.0, 16.0), (6.0, 2.5, 6.0), "spike"),
          ((-4.0, -3.0, -2.0), (6.0, 5.0, 6.0), "spike"), ((5.0, -2.0, 7.0), (6.0, 5.0, 5.0), "hide"),
          ((0.0, -5.0, 20.0), (5.0, 4.0, 5.0), "hide"), ((-2.0, -4.0, 14.0), (5.0, 4.0, 5.0), "spike"))
# (all behind its shoulders: its arms' roots turn inside its body there, and the pieces fly out past its neck)
# The eye studies (the user: "give me variations on his eyes, it should be similar to Rodan"): the head stands this
# many blocks up (its eyes a little higher), turned this many degrees to its left so its right eye faces you.
STUDY_HEAD_UP = 2.6
STUDY_TURN = 48.0
STUDY_DENSITY = 1.0                # texels a unit: the eye a little sharper than the whole animal's

EYE_AMBER = F.palette("#6a3204", "#a05206", "#d8840e", "#f6b030", "#ffd878", "#fff2c8")
EYE_BLOOD = F.palette("#3a0402", "#660804", "#9a1206", "#d02a0a", "#ff5a22", "#ffa070")
EYE_DARK = F.palette("#0a0303", "#140605", "#220a07", "#320f09", "#46160c", "#5e2010")
EYE_SOOT = F.palette("#1a0604", "#2e0a05", "#4a1206", "#6c1c08", "#94300c", "#c04a14")
SOCKET = F.palette("#040202", "#080404", "#0e0706", "#160b09", "#1f100c", "#2a1610")

# Each eye study: its eye (`eye`: F.eye_disc's choices), how big the eye is (head units across; the adult's is 6),
# how far a dark socket rim stands out round it (`socket`, units; 0: none), its brow ("ridge", "shelf", "hood",
# "spikes", "double") and whether lava runs down from it (`tears`).
EYE_STYLES = {
    "amber": dict(mood="amber beads", size=4.4, socket=1.6, brow="shelf",
                  eye=dict(iris=EYE_AMBER, pupil="round", pupil_size=0.26, rim=0.2, glow=230.0, glint=1),
                  text="small round amber eyes with a black pupil, sunk in dark sockets under a jutting bony shelf"),
    "slit": dict(mood="ember slit", size=5.0, socket=1.0, brow="hood",
                 eye=dict(iris=F.EYE_EMBER, pupil="slit", rim=0.14, glow=240.0, glint=1),
                 text="glowing orange eyes with a thin slit pupil, a heavy lid hooding their top, lower at the front"),
    "coal": dict(mood="white-hot coal", size=4.6, socket=1.8, brow="shelf",
                 eye=dict(iris=F.WHITE_HOT, pupil=None, rim=0.24, glow=255.0, glint=0, fibres=0.2),
                 text="no pupil at all: a white-hot coal in each socket, cooling to orange at its rim"),
    "blood": dict(mood="blood ember", size=4.8, socket=1.4, brow="ridge",
                  eye=dict(iris=EYE_BLOOD, pupil="round", pupil_size=0.3, rim=0.16, glow=220.0, glint=1,
                           halo=F.EMBER),
                  text="deep red eyes with a black pupil and a ring of fire round each, under a heavy ridge"),
    "raptor": dict(mood="raptor gold", size=5.6, socket=0.8, brow="shelf",
                   eye=dict(iris=F.EYE_GOLD, pupil="round", pupil_size=0.4, rim=0.12, glow=170.0, glint=2),
                   text="a hawk's stare: gold eyes with a big black pupil and two glints, under a bony shelf"),
    "pinpoint": dict(mood="pinpoints", size=4.0, socket=2.2, brow="shelf",
                     eye=dict(iris=EYE_DARK, pupil="dot", pupil_size=0.3, rim=0.2, glow=60.0, glint=0,
                              pupil_glow=F.WHITE_HOT, fibres=0.3),
                     text="dark pits, a tiny white-hot point of light burning in the middle of each"),
    "rings": dict(mood="fire rings", size=5.2, socket=1.2, brow="ridge",
                  eye=dict(iris=F.EYE_EMBER, rings=[EYE_BLOOD, F.EYE_EMBER, F.WHITE_HOT], pupil="round",
                           pupil_size=0.22, rim=0.14, glow=240.0, glint=1),
                  text="rings of fire round a black pupil: red at the rim, orange, then white-hot by the pupil"),
    "spark": dict(mood="spark eyes", size=4.8, socket=1.4, brow="double",
                  eye=dict(iris=EYE_SOOT, pupil="slit", rim=0.16, glow=120.0, glint=0, stars=0.14, halo=F.EMBER),
                  text="smouldering dark red eyes full of sparks, a slit pupil and a glowing ring, two ridges above"),
    "spiked": dict(mood="spiked brow", size=5.0, socket=1.2, brow="spikes",
                   eye=dict(iris=EYE_AMBER, pupil="bar", rim=0.14, glow=230.0, glint=1),
                   text="amber eyes with a goat's bar pupil under a brow of short spikes swept back"),
    "tears": dict(mood="lava tears", size=4.6, socket=1.2, brow="ridge",
                  eye=dict(iris=F.EYE_EMBER, pupil="round", pupil_size=0.28, rim=0.16, glow=240.0, glint=1),
                  tears=True, text="fierce orange eyes, lava running from their back corners down its cheeks"),
}


# Less lava on the adult's body (the user, October 31, 2026: "I am considering less lava spots on the adult version on
# the body; the wings are fine, but on the body and horns less lava; give me a showcase with those versions"): the
# gallery's E1B-E1D (firedesigns/e01_emberdactyl.py) keep only `keep` of E1A's lava veins (the same veins, fewer of
# them) on its hide, belly, bills, brows, horns and half moon, and the half moon's glowing rim cooled over this much
# (`rim`: 0 bright as E1A's, towards 1 a crust of obsidian with a little lava showing); the wings stay as they are.
BODY_LAVA = {
    "less": dict(keep=0.5, rim=0.0),
    "little": dict(keep=0.25, rim=0.62),
    # (the user, November 5, 2026, of E1A: "a little too much on the body": a few less than "little")
    "fewer": dict(keep=0.18, rim=0.62),
    "none": dict(keep=0.07, rim=0.92),
}

# Purple looks of E1C (the user, November 2, 2026, of the less-lava looks: "I like E1C. Maybe can you give me
# variations of the bird but with the beak and the horns with purple, and tail, just a variation of what we have
# here"): E1C as it is (its body, wings, eyes and lava, its half moon's glowing rim), only its bill and jaw, its half
# moon, its two blade horns and its tail (and the spike at its end) purple: the violet of glossy and crying obsidian,
# the family of its egg (e03_emberegg.EGG_OBSIDIAN) and its spawn egg A11. Those parts have materials of their own
# (PURPLE_PARTS), painted as E1A-E1D's were (the same painters) unless a look colours them. The gallery's E1C1-E1C4
# (firedesigns/e01_emberdactyl.py):
#   "glossy"  glossy violet obsidian all over them, no lava on them (bright lilac glints where the light catches it);
#   "fade"    deep purple at their tips fading to E1C's near-black obsidian at their roots (its few lava veins kept);
#   "veins"   violet obsidian with lava veins glowing through it (as many as E1A has there: orange in purple);
#   "tips"    only their ends purple (the last third of each horn, the front of the bill and jaw, the top of the half
#             moon under its rim, the tail's end and its spike), with a faint purple glow and crying obsidian's sparks.
VIOLET = F.palette("#1c0a32", "#2e1154", "#431a76", "#5c2698", "#7838ba", "#a066dc")       # glossy violet obsidian
VIOLET_DEEP = F.palette("#120622", "#1e0a38", "#2e1052", "#40186e", "#56248e", "#7238b0")  # its deep purple
VIOLET_GLOW = F.palette("#5a1c9a", "#7a2ec6", "#9a48e6", "#b874fa", "#d6a8ff")             # crying obsidian's glow
PURPLE_PARTS = ("beak", "horn", "moon", "tail", "tailspike")
# Which way each part grows out of the body in the rest pose (the painters' frame: +y up, -z its front), and which of
# its texels' spreads that is (0 its longest way; the half moon's is along the bill, so its height is its second):
# root (0) to tip (1), worked out from the part's own texels.
PART_GROWS = {"beak": ((0.0, 0.0, -1.0), 0), "horn": ((0.0, 1.0, 1.0), 0), "moon": ((0.0, 1.0, 0.0), 1),
              "tail": ((0.0, 0.0, 1.0), 0), "tailspike": ((0.0, 0.0, 1.0), 0)}
# E1C's painter each part keeps unless a look colours it, and its seed and lava veins (E1A's amount: E1C keeps a
# quarter of them, BODY_LAVA["little"]).
PART_BASE = {"beak": ("beak", 1811, 1.7, 0.08), "horn": ("crest", 1819, 1.8, 0.12), "moon": ("crest", 1819, 1.8, 0.12),
             "tail": ("hide", 1801, 1.6, 0.14), "tailspike": ("spike", 1821, 1.8, 0.0)}
# The user, November 5, 2026, of E1A (E1C3) in the game: "its beak and horns are way too purple compared to the body, it
# should be the same shade; too many lava spots on its beak and horns, and a little too much on the body; keep the wings
# as is, and remove some of the spots from the tail". Three looks of it (veins "cut": E1A's own amount of veins on each
# part, only `keep` of them kept; the body "fewer"): its bill and jaw, half moon and horns keep a fifth of their veins,
# its tail two fifths, and they are
#   "body"   the body's own obsidian (fmats.OBSIDIAN, a purple-black), no other colour;
#   "hint"   the body's obsidian with a hint of violet (DUSK: a third of the way to VIOLET);
#   "dusk"   the body's obsidian at their roots fading to a soft violet (DUSK_TIP, half way) at their tips.
DUSK = F.OBSIDIAN * (2.0 / 3.0) + VIOLET * (1.0 / 3.0)
DUSK_TIP = F.OBSIDIAN * 0.5 + VIOLET * 0.5
CUT_KEEP = {"beak": 0.2, "horn": 0.2, "moon": 0.2, "tail": 0.4, "tailspike": 1.0}
PURPLE = {
    "body": dict(pal=F.OBSIDIAN, gloss=1.6, veins="cut", keep=CUT_KEEP),
    "hint": dict(pal=DUSK, gloss=1.7, veins="cut", keep=CUT_KEEP),
    "dusk": dict(pal=DUSK_TIP, gloss=1.8, veins="cut", keep=CUT_KEEP, ramp=(0.35, 0.95), spike_ramp=None),
    # (fade, tips: w = how purple a texel is, 0 at the part's root .. 1 at its tip, from `ramp` (where it starts and
    # where it's all purple, as shares of the way out); the tail's spike is all purple, being its tip)
    "glossy": dict(pal=VIOLET, gloss=2.1, veins=None),
    "fade": dict(pal=VIOLET_DEEP, gloss=1.8, veins="kept", ramp=(0.05, 0.85)),
    "veins": dict(pal=VIOLET, gloss=1.8, veins="all", vein_floor=0.12),
    "tips": dict(pal=VIOLET, gloss=2.0, veins="kept", ramp=(0.6, 0.72), spike_ramp=None, glow=52.0, sparks=0.05),
}


def along(tx, grows, rank=0):
    """Each texel's place along its part, 0 at its root to 1 at its tip: the part's texels' `rank`-th longest spread
    (rest pose), pointed the way the part grows (`grows`)."""
    p = tx.Pw
    mid = p.mean(axis=0)
    if tx.n < 3:
        return np.zeros(tx.n)
    vals, vecs = np.linalg.eigh(np.cov((p - mid).T))
    axis = vecs[:, np.argsort(vals)[::-1][rank]]
    if float(axis @ np.asarray(grows, dtype=float)) < 0.0:
        axis = -axis
    d = (p - mid) @ axis
    return (d - d.min()) / max(float(d.max() - d.min()), 1e-6)


@_factory(True)
def toward_tip(root, tip, grows, rank=0, ramp=None, glow=0.0, sparks=0.0, seed=1851):
    """
    A part painted `root` at its root and `tip` towards its tip (two painters of the same seed and veins, so the
    lava veins run on through), blended smoothly over `ramp` (shares of the way out; None: all `tip`); `glow`: a faint
    purple glow (its alpha) over what's purple, `sparks` (a share) of crying obsidian's brighter specks in it.
    """

    def paint(tx):
        b = tip(tx)
        if ramp is None:
            w = np.ones(tx.n)
        else:
            a = root(tx)
            s = np.clip((along(tx, grows, rank) - ramp[0]) / max(ramp[1] - ramp[0], 1e-6), 0.0, 1.0)
            w = s * s * (3.0 - 2.0 * s)
        col = b["color"] * w[:, None] if ramp is None else a["color"] * (1.0 - w[:, None]) + b["color"] * w[:, None]
        g = b["glow"] * w[:, None] if ramp is None else a["glow"] * (1.0 - w[:, None]) + b["glow"] * w[:, None]
        if glow > 0:
            lit = (g[:, 3] < 1.0) & (w > 0.05)
            k = 1.2 + (noise.fbm(tx.Pw, 5.0, 2, seed) - 0.5) * 2.0
            spark = lit & (_speck(tx.Pw, seed + 1, 1.2) < sparks)
            g[lit] = rgba(pick(VIOLET_GLOW, k[lit]), glow * w[lit])
            g[spark] = rgba(pick(VIOLET_GLOW, np.full(int(spark.sum()), 4.0)), 190.0 * w[spark])
            col = np.where(spark[:, None], pick(VIOLET_GLOW, np.full(tx.n, 3.0)), col)
        return _out(tx, col, None, g)

    return paint


def purple_parts(look, base):
    """The painters of a purple look (PURPLE) for the parts it colours, over E1C's painters `base`."""
    spec = PURPLE[look]
    out = {}
    for part in PURPLE_PARTS:
        mat, seed, gloss, veins = PART_BASE[part]
        if spec["veins"] == "all":
            v, keep = max(veins, spec.get("vein_floor", 0.0)), 1.0
        elif spec["veins"] == "kept":
            v, keep = veins, BODY_LAVA["little"]["keep"]
        elif spec["veins"] == "cut":
            v, keep = veins, spec["keep"][part]
        else:
            v, keep = 0.0, 1.0
        tip = F.obsidian(spec["pal"], seed=seed, gloss=spec["gloss"], veins=v, vein_keep=keep)
        ramp = spec.get("ramp")
        if part == "tailspike":
            ramp = spec.get("spike_ramp")
        grows, rank = PART_GROWS[part]
        out[part] = toward_tip(base[mat], tip, grows, rank, ramp=ramp, glow=spec.get("glow", 0.0),
                               sparks=spec.get("sparks", 0.0), seed=1851 + PURPLE_PARTS.index(part) * 7)
    return out


def materials(young=False, eye=None, lava=None, purple=None):
    """Obsidian with lava in its veins: a little on the adult's scales; the young's hide all run through with lava
    (the user: "much more magma"). `eye`: an eye study's key in EYE_STYLES (None: the adult's own eyes). `lava`: a key
    of BODY_LAVA (the adult with less lava on its body; None: E1A's). `purple`: a key of PURPLE (its bill, jaw, half
    moon, horns and tail purple; None: as the rest of it)."""
    cut = BODY_LAVA[lava] if lava is not None and not young else dict(keep=1.0, rim=0.0)
    keep = cut["keep"]
    out = {
        "hide": F.obsidian(seed=1801, veins=0.14, gloss=1.6, vein_keep=keep),
        "belly": F.obsidian(seed=1803, veins=0.08, gloss=1.2, vein_keep=keep),
        "wingbone": F.obsidian(seed=1805, veins=0.11, cell=4.0),
        "joint": F.glowing(F.EMBER, seed=1807, idx=2.0, base_pal=F.LAVA),
        "claw": F.obsidian(OBS_RED, seed=1809, gloss=1.9),
        "tooth": F.obsidian(OBS_RED, seed=1810, gloss=2.0),
        "beak": F.obsidian(OBS_RED, seed=1811, gloss=1.7, veins=0.08, vein_keep=keep),
        "seam": F.lava(F.EMBER, seed=1813),
        "tongue": F.lava(F.EMBER, seed=1815, crust=0.3),
        "eye": F.eye_disc(iris=F.EYE_EMBER, pupil="slit", rim=0.16, glow=240.0, seed=1817),
        "socket": F.obsidian(SOCKET, seed=1818, gloss=0.8),
        "brow": F.obsidian(OBS_RED, seed=1820, gloss=1.5, veins=0.1, vein_keep=keep),
        "tear": F.lava(F.EMBER, seed=1822, flow=2.0),
        "crest": F.obsidian(OBS_RED, seed=1819, gloss=1.8, veins=0.12, vein_keep=keep),
        "spike": F.obsidian(OBS_RED, seed=1821, gloss=1.8),
        "rib": F.obsidian(OBS_RED, seed=1823, gloss=1.4),
        "membrane": F.membrane(OBS_MEMBRANE, seed=1825, hot=0.26, veins=0.9),
        "membraneedge": F.membrane(OBS_MEMBRANE, seed=1827, hot=0.34, veins=0.9, ragged=0.6),
        "lava": F.lava(seed=1829),
        "lid": F.obsidian(OBS_RED, seed=1843, gloss=1.3),
    }
    if young:
        # Lava running all through it (the user, of round 3's young: "much more magma, not enough"): its hide, horns
        # and wing bones dark obsidian crust broken by wide glowing channels, its wings veined with fire.
        out.update({
            "hide": F.lava(seed=1831, crust=0.76, flow=1.2, crust_pal=OBS_RED),
            "belly": F.lava(seed=1833, crust=0.6, flow=1.0, crust_pal=OBS_RED),
            "wingbone": F.lava(seed=1835, crust=0.72, flow=1.4, crust_pal=OBS_RED),
            "joint": F.glowing(F.EMBER, seed=1807, idx=3.0, base_pal=F.LAVA),
            "beak": F.obsidian(OBS_RED, seed=1811, gloss=1.7, veins=0.9),
            "crest": F.lava(seed=1837, crust=0.78, flow=1.6, crust_pal=OBS_RED),
            "brow": F.lava(seed=1839, crust=0.75, crust_pal=OBS_RED),
            "spike": F.obsidian(OBS_RED, seed=1821, gloss=1.8, veins=1.2),
            "rib": F.lava(seed=1841, crust=0.35, flow=2.0, crust_pal=OBS_RED),
            "membrane": F.membrane(OBS_MEMBRANE, seed=1825, hot=1.0, veins=2.0, vein_pal=F.LAVA),
            "membraneedge": F.membrane(OBS_MEMBRANE, seed=1827, hot=1.0, veins=2.0, vein_pal=F.LAVA, ragged=0.8),
        })
    if cut["rim"] > 0:
        out["seam"] = F.lava(F.EMBER, seed=1813, crust=cut["rim"], crust_pal=OBS_RED)
    # (its horns, half moon and tail have materials of their own: the same painters as the rest of it, so the looks
    # without purple are exactly as they were)
    out.update(horn=out["crest"], moon=out["crest"], tail=out["hide"], tailspike=out["spike"])
    if purple is not None and not young:
        out.update(purple_parts(purple, out))
    eye = eye or (YOUNG_EYE if young else ADULT_EYE)
    out["eye"] = F.eye_disc(seed=1817, **EYES[eye]["eye"])
    return out


# ---------------------------------------------------------------- where everything goes


def frame(young=False):
    """The torso's frame (turned nose-up by PITCH), the girth, the head's size, the neck's line and the head's middle."""
    slim = (0.86 if young else 1.0) * SLENDER
    head_k = 1.42 if young else 1.28
    t = Xform(None, pivot=BODY, axis=(1, 0, 0), deg=PITCH)
    neck = [BODY + np.array([0.0, 6.0, -24.0]), BODY + np.array([0.0, 14.0, -38.0]),
            BODY + np.array([0.0, 18.0, -52.0]), BODY + np.array([0.0, 16.0, -62.0])]
    hc = neck[-1] + np.array([0.0, 2.0, -12.0])
    return t, slim, head_k, neck, hc


def leg_points(sign, slim):
    return (BODY + np.array([sign * 7.0, -6.0, 16.0]), BODY + np.array([sign * 9.0, -20.0, 10.0]),
            BODY + np.array([sign * 9.0, -33.0, 18.0]))


def shoulder(t, sign, slim):
    return t.P(BODY + np.array([sign * 11.0 * slim, 8.0, -14.0]))


def flank(slim, z):
    """How far the body's side is from its middle line (units) at z along the torso (its own frame), as the body's
    blob is built (seven slices, each as wide as the ellipse at the slice's middle)."""
    rx, slices = 12.0 * slim * 1.25, 7
    t = float(max(1, int(round(2.0 * 29.0 / slices))))
    ry = slices * t / 2.0
    k = min(slices - 1, max(0, int((z + ry) // t)))
    yc = -ry + (k + 0.5) * t
    return rx * max(0.32, math.sqrt(max(0.0, 1.0 - (yc / (ry + t * 0.35)) ** 2)))


def wing_edges(sign, young, t, slim):
    """
    One wing's lines (design units): its bones' line (`lead`: shoulder, elbow, wrist, the finger's joints, the tip),
    the membrane's leading line (`mlead`: the same, but starting where the arm leaves the body) and its trailing edge
    (`trail`: from its root on the flank out to the tip).
    """
    lead, trail = wing_points(sign, young, shoulder(t, sign, slim))
    root = t.P(BODY + np.array([sign * (flank(slim, ROOT_BACK) + ROOT_GAP), ROOT_UP, ROOT_BACK]))
    mlead = [lead[0] + (lead[1] - lead[0]) * MEM_FROM] + list(lead[1:])
    # the inner wing narrower (the user: the two parts nearest its body "spike up ... make those two parts smaller"):
    # its root and the elbow's trailing point drawn in towards the bones
    trail = [mlead[0] + (root - mlead[0]) * INNER_CHORD, lead[1] + (trail[1] - lead[1]) * INNER_CHORD] + list(trail[2:])
    return lead, mlead, trail


def edge_at(mlead, trail, i, u):
    """The trailing edge's point u (0..1) of the way along stretch i, sagging back towards the bones between them."""
    base = trail[i] + (trail[i + 1] - trail[i]) * u
    la = mlead[i] + (mlead[i + 1] - mlead[i]) * u
    return base + (la - base) * SCALLOP * math.sin(math.pi * u)


def bones(young=False):
    """[(bone, pivot in design units, parent)], parents first (the animated mob's skeleton)."""
    t, slim, head_k, neck, hc = frame(young)
    out = [("root", (0.0, 0.0, 0.0), None), ("body", tuple(BODY), "root"),
           ("neck", tuple(t.P(neck[0])), "body"), ("head", tuple(t.P(neck[-1])), "neck"),
           ("jaw", tuple(t.P(hc + head_k * np.array([0.0, -5.0, -4.0]))), "head"),
           ("tail", tuple(t.P(BODY + np.array([0.0, 0.0, 24.0]))), "body")]
    for side, sign in SIDES:
        out.append((f"lid_{side}", tuple(t.P(hc + head_k * np.array([sign * 7.6, 3.0, -5.0]))), "head"))
    if not young:
        for n, (off, _size, _mat) in enumerate(CHUNKS):
            out.append((f"chunk_{n}", tuple(t.P(BODY + np.array(off))), "body"))
        # the pieces its death breaks off its head (head units from the head's middle): the upper bill, each horn's
        # root, the half moon's middle
        zc = (MOON_FROM + MOON_TO) / 2.0
        for name, off in (("bill", (0.0, -1.0, -10.0)), ("horn_l", (2.5, 6.0, 6.0)), ("horn_r", (-2.5, 6.0, 6.0)),
                          ("crest", (0.0, bill_top((0.0, 0.0, 0.0), zc), zc))):
            out.append((name, tuple(t.P(hc + head_k * np.array(off))), "head"))
    for side, sign in SIDES:
        lead, mlead, trail = wing_edges(sign, young, t, slim)
        parent = "body"
        for name, p in zip(WING_BONES, lead[:5]):
            out.append((f"{name}_{side}", tuple(p), parent))
            parent = f"{name}_{side}"
        hip, knee, ankle = leg_points(sign, slim)
        out += [(f"leg_{side}", tuple(t.P(hip)), "body"), (f"shin_{side}", tuple(t.P(knee)), f"leg_{side}"),
                (f"foot_{side}", tuple(t.P(ankle)), f"shin_{side}")]
        # each stretch of membrane on its own bone, hinged at its wing bone's joint (it folds along the bone)
        for i, name in enumerate(WING_BONES):
            out.append((f"mem_{name}_{side}", tuple(lead[i]), f"{name}_{side}"))
        # the falling fire's spots along the trailing edge, and the wingtip
        for n, (i, u) in enumerate(EMBER_SPOTS):
            out.append((f"ember_{side}{n}", tuple(edge_at(mlead, trail, i, u)), f"mem_{WING_BONES[i]}_{side}"))
        out.append((f"ember_{side}{len(EMBER_SPOTS)}", tuple(lead[5]), f"tip_{side}"))
    return out


def hinges(young=False):
    """Each stretch of membrane's hinge: [(its bone, its wing bone, a point on the hinge, the hinge's direction)]
    (design units): the line of the wing bone it hangs from."""
    t, slim, _hk, _n, _hc = frame(young)
    out = []
    for side, sign in SIDES:
        lead, _mlead, _trail = wing_edges(sign, young, t, slim)
        for i, name in enumerate(WING_BONES):
            out.append((f"mem_{name}_{side}", f"{name}_{side}", np.array(lead[i]), unit(lead[i + 1] - lead[i])))
    return out


def membrane_frames(young=False):
    """Each stretch of membrane's own frame: {its bone: (a point on its hinge, the hinge's direction, the way across it
    from the hinge to its trailing edge)} (design units). The animated mob turns each membrane bone to it, so the
    membrane can be furled (squashed towards its hinge) as well as folded (firemobs/wingfold.py)."""
    t, slim, _hk, _n, _hc = frame(young)
    out = {}
    for side, sign in SIDES:
        lead, mlead, trail = wing_edges(sign, young, t, slim)
        for i, name in enumerate(WING_BONES):
            h = unit(lead[i + 1] - lead[i])
            across = (edge_at(mlead, trail, i, 0.5) - (mlead[i] + mlead[i + 1]) / 2.0)
            across = unit(across - h * float(np.dot(across, h)))
            out[f"mem_{name}_{side}"] = (np.array(lead[i]), h, across)
    return out


def half_span(young=False):
    """Units from its middle to a wingtip at scale 1 (the tip bone's end and the membrane round it)."""
    t, slim, _hk, _n, _hc = frame(young)
    lead, _trail = wing_points(1.0, young, shoulder(t, 1.0, slim))
    return float(lead[-1][0]) + 4.0


def scale_for(span, young=False):
    return span * 16.0 / 2.0 / half_span(young)


# ---------------------------------------------------------------- the head


def _profile(pts, z):
    """(middle's height, width, depth) of a bill at z, from its (z, y, width, depth) points."""
    if z >= pts[0][0]:
        return pts[0][1:]
    if z <= pts[-1][0]:
        return pts[-1][1:]
    for a, b in zip(pts, pts[1:]):
        if b[0] <= z <= a[0]:
            u = (z - a[0]) / (b[0] - a[0])
            return tuple(a[k] + (b[k] - a[k]) * u for k in (1, 2, 3))
    return pts[-1][1:]


def bill_top(c, z):
    """The upper bill's top at z units in front of the head's middle."""
    y, _w, d = _profile(UPPER_BILL, z)
    return c[1] + y + d / 2.0


def teeth(sh, sj, c):
    """
    The teeth, each rooted in the jaw it belongs to (the user: "his teeth stay with his body"): a row hanging from the
    upper bill's edge (on the head) and a row standing in the lower jaw between them (on the jaw), so each row opens
    with its own jaw; shut, the upper ones hang outside the narrower lower jaw and the lower ones go up inside the bill.
    """
    for z in UPPER_TEETH:
        y, w, d = _profile(UPPER_BILL, z)
        bottom = y - d / 2.0
        for sign in (1.0, -1.0):
            x = sign * (w / 2.0 - 0.9)
            sh.cone(c + np.array([x, bottom + 1.2, z]), c + np.array([x * 0.96, bottom - 2.4, z - 0.8]), 1.6, "tooth",
                    w1=0.6, steps=2)
    for z in LOWER_TEETH:
        y, w, d = _profile(LOWER_BILL, z)
        top = y + d / 2.0
        for sign in (1.0, -1.0):
            x = sign * (w / 2.0 - 0.8)
            sj.cone(c + np.array([x, top - 1.0, z]), c + np.array([x * 0.96, top + 2.0, z - 0.6]), 1.4, "tooth",
                    w1=0.6, steps=2)


def tongue(sj, c):
    """Its tongue, lying in the lower jaw (narrower than the jaw, so nothing of it shows outside it)."""
    pts, widths = [], []
    for z, w in TONGUE:
        y, _jw, d = _profile(LOWER_BILL, z)
        pts.append(c + np.array([0.0, y + d / 2.0, z]))
        widths.append(w)
    sj.taper(pts, widths, "tongue", depths=[1.4, 1.2, 1.0], side=(1, 0, 0))


DEFAULT_EYE = dict(size=6.0, socket=0.0, brow="adult")
# The user's picks (October 27): the adult the evil ember slit of its eye studies (E1A2: "the best eyes, the evil-looking
# ones"), its young plain, friendly eyes ("make the smaller eyes not evil and just normal again"): a round amber eye
# with a soft round pupil and two glints, no dark socket, the round-2 ridge for a brow.
ADULT_EYE = "slit"
YOUNG_EYE = "plain"
EYES = dict(EYE_STYLES, plain=dict(mood="plain eyes", size=5.6, socket=0.0, brow="adult",
                                   eye=dict(iris=EYE_AMBER, pupil="round", pupil_size=0.34, rim=0.14, glow=200.0,
                                            glint=2), text="round amber eyes with a soft round pupil"))


def eyes(sh, c, style):
    """Its eyes and brows (`style`: an eye study's key in EYE_STYLES; None: the adult's own)."""
    st = EYES[style] if style else DEFAULT_EYE
    size, socket, brow = st["size"], st["socket"], st["brow"]
    h = size / 2.0
    for sign in (1.0, -1.0):
        e = c + np.array([sign * 7.6, 3.0, -5.0])
        sh.seg(e + np.array([0.0, 0.0, h]), e + np.array([0.0, 0.0, -h]), size, 2, "eye", side=(0, 1, 0))
        out = 8.6                                   # (the eye's face, x)
        if socket > 0:
            # a dark rim round it, standing out from its face: the eye looks sunk deep in its socket
            ft = 1.4
            depth = socket + 1.5
            x = sign * (out + socket / 2.0 - 0.75)
            for dy, dz, sy, sz in ((h + ft / 2.0, 0.0, ft, size + 2.0 * ft), (-h - ft / 2.0, 0.0, ft, size + 2.0 * ft),
                                   (0.0, -h - ft / 2.0, size, ft), (0.0, h + ft / 2.0, size, ft)):
                sh.box(np.array([c[0] + x, e[1] + dy, e[2] + dz]), (depth, sy, sz), "socket")
        top = e[1] + h + (1.4 if socket > 0 else 0.0)
        reach = out + socket                        # (how far out the socket comes: the brow overhangs it)
        X = np.array([c[0], 0.0, 0.0])
        if brow == "adult":
            sh.seg(c + np.array([sign * 6.0, 7.0, 0.0]), c + np.array([sign * 7.0, 6.0, -10.0]), 4, 4, "hide",
                   side=(0, 1, 0))
        elif brow in ("ridge", "spikes", "double"):
            sh.seg(X + np.array([sign * (reach - 2.6), top + 1.3, e[2] + 6.0]),
                   X + np.array([sign * (reach - 2.0), top + 0.8, e[2] - 6.5]), 3.0, 5.0, "brow", side=(0, 1, 0))
            if brow == "spikes":
                for k, dz in enumerate((4.0, 0.0, -4.0)):
                    p = X + np.array([sign * (reach - 1.5), top + 2.0, e[2] + dz])
                    sh.cone(p - np.array([sign * 0.5, 1.0, 0.0]),
                            p + np.array([sign * 2.0, 3.2 - k * 0.4, 4.5]), 1.8, "brow", w1=0.6, steps=2)
            if brow == "double":
                sh.seg(X + np.array([sign * (reach - 3.4), top + 3.6, e[2] + 5.0]),
                       X + np.array([sign * (reach - 3.0), top + 3.0, e[2] - 4.0]), 2.4, 4.0, "brow", side=(0, 1, 0))
        elif brow == "shelf":
            # a heavy bony shelf jutting out over the eye
            sh.seg(X + np.array([sign * (reach - 2.8), top + 1.4, e[2] + 7.0]),
                   X + np.array([sign * (reach - 1.6), top + 0.8, e[2] - 7.5]), 3.4, 6.0 + socket * 0.6, "brow",
                   side=(0, 1, 0))
        elif brow == "hood":
            # a heavy lid over the eye's top, lower at its front
            sh.seg(X + np.array([sign * (reach - 2.4), top + 0.6, e[2] + 6.0]),
                   X + np.array([sign * (reach - 1.8), e[1] + h * 0.15, e[2] - 6.0]), 3.4, 4.6 + socket, "brow",
                   side=(0, 1, 0))
        if st.get("tears"):
            # lava running from the eye's back corner down its cheek
            a = np.array([c[0] + sign * 8.2, e[1] - h * 0.4, e[2] + h * 0.6])
            sh.seg(a, a + np.array([-sign * 0.4, -3.5, 2.5]), 1.2, 1.2, "tear", side=(1, 0, 0))
            sh.seg(a + np.array([-sign * 0.4, -3.5, 2.5]), a + np.array([-sign * 1.2, -6.5, 3.0]), 1.0, 1.0, "tear",
                   side=(1, 0, 0))


def lids(sl, c, style):
    """Its eyelids (the user: it closes its eyes as it dies, and only then), one shape a side: hidden in the head behind
    each eye; a death slides each out over its eye (LID_SLIDE units: firemobs/emberdactyl.py)."""
    st = EYES[style] if style else DEFAULT_EYE
    for (side, sign), s in zip(SIDES, sl):
        e = c + np.array([sign * 7.6, 3.0, -5.0])
        s.box(e + np.array([sign * (LID_X - 7.6), 0.0, 0.0]), (2.4, st["size"] + 0.4, st["size"] + 0.4), "lid")


def head(sh, sj, c, d, sl=None, pieces=None, horn_mat="crest", moon_mat="crest"):
    """The head round `c`: the upper bill, its teeth, eyes, brows, horns and the half moon on `sh`; the lower jaw, its
    teeth and the tongue on `sj` (it opens to screech). `pieces` ({"bill", "horn_l", "horn_r", "crest": shape}, the
    adult's): the upper bill with its teeth, each horn and the half moon each on a shape of its own instead (its
    death breaks them off and throws them: firemobs/emberdactyl.py). `horn_mat`, `moon_mat`: the horns' and the half
    moon's materials (the living animal's have names of their own, so its purple looks can colour them: PURPLE)."""
    c = v3(c)
    pieces = pieces or {}
    sh.blob(c, (8.0, 7.5, 12.0), "hide", axis=(0, 0, 1), side=(1, 0, 0), slices=4)
    up = UPPER_BILL
    pieces.get("bill", sh).taper([c + np.array([0.0, p[1], p[0]]) for p in up], [p[2] for p in up], "beak", depths=[p[3] for p in up],
             side=(1, 0, 0))
    lo = LOWER_BILL
    sj.taper([c + np.array([0.0, p[1], p[0]]) for p in lo], [p[2] for p in lo], "beak", depths=[p[3] for p in lo],
             side=(1, 0, 0))
    teeth(pieces.get("bill", sh), sj, c)
    tongue(sj, c)
    eyes(sh, c, d.get("eye"))
    if sl is not None:
        lids(sl, c, d.get("eye"))
    # two blade horns sweeping back off its head
    back = c + np.array([0.0, 6.0, 6.0])
    h = d.get("horn", 1.6)
    for sign in (1.0, -1.0):
        b = back + np.array([sign * 2.5, 0.0, 0.0])
        pieces.get("horn_l" if sign > 0 else "horn_r", sh).taper([b, b + np.array([sign * 2.0, 16.0 * h, 16.0 * h]), b + np.array([sign * 4.0, 26.0 * h, 42.0 * h])],
                 [2.0, 2.0, 1.0], horn_mat, depths=[12.0, 10.0, 2.0], side=(1, 0, 0))
    # the half moon standing on the bill all along it, its foot sunk into the bill's top, a glowing rim round its arc
    zc = (MOON_FROM + MOON_TO) / 2.0
    r = (MOON_FROM - MOON_TO) / 2.0
    n = 9
    for i in range(n):
        z0 = MOON_TO + (MOON_FROM - MOON_TO) * i / n
        z1 = MOON_TO + (MOON_FROM - MOON_TO) * (i + 1) / n
        zm = (z0 + z1) / 2.0
        hgt = math.sqrt(max(0.0, r * r - (zm - zc) ** 2)) * (MOON_RADIUS / r)
        base = bill_top(c, zm) - 1.5
        top = bill_top(c, zm) + hgt
        pieces.get("crest", sh).box(np.array([0.0, (base + top) / 2.0, c[2] + zm]), (2.0, max(1.0, top - base),
                                                                                    z1 - z0 + 0.4), moon_mat)
    for i in range(12):
        a = math.pi * (i + 0.5) / 12
        zm = zc - math.cos(a) * r
        y = bill_top(c, zm) + math.sin(a) * MOON_RADIUS + 0.6
        pieces.get("crest", sh).box(np.array([0.0, y, c[2] + zm]), (2.4, 2.0, 2.2), "seam")


# ---------------------------------------------------------------- the wings


def membrane(shapes_, lead, trail, thin, rows=4, sub=2, bulge=4.0, nh=(0.0, 0.1, -1.0)):
    """
    The wing's membrane: each stretch between two bones (on that stretch's own shape) cut into `sub` pieces, each
    `rows` rows of thin plates from the bones to the trailing edge, which sags back towards the bones between each pair
    (scalloped); a rib across the middle of each piece, from the bone to the edge; the last row burnt ragged.
    """
    nh = unit(nh)

    def lead_at(i, u):
        return lead[i] + (lead[i + 1] - lead[i]) * u

    def at(a, b, t):
        return a + (b - a) * t + nh * bulge * math.sin(math.pi * t)

    for i in range(len(lead) - 1):
        s = shapes_[min(i, len(shapes_) - 1)]
        for j in range(sub):
            u0, u1 = j / sub, (j + 1) / sub
            a0, a1 = lead_at(i, u0), lead_at(i, u1)
            b0, b1 = edge_at(lead, trail, i, u0), edge_at(lead, trail, i, u1)
            for r in range(rows):
                t0, t1 = r / rows, (r + 1) / rows
                p00, p01 = at(a0, b0, t0), at(a1, b1, t0)
                p10, p11 = at(a0, b0, t1), at(a1, b1, t1)
                c = (p00 + p01 + p10 + p11) / 4.0
                span = ((p01 - p00) + (p11 - p10)) / 2.0
                chord = ((p00 - p10) + (p01 - p11)) / 2.0
                nrm = np.cross(span, chord)
                if np.linalg.norm(nrm) < 1e-6:
                    continue
                nrm = unit(nrm)
                if np.dot(nrm, nh) < 0:
                    nrm = -nrm
                w = (np.linalg.norm(p01 - p00) + np.linalg.norm(p11 - p10)) / 2.0 * 1.1 + 1.5
                h = np.linalg.norm(chord) * 1.1 + 1.0
                if w < 1.5 or h < 1.5:
                    continue
                s.plate(c + nrm * (0.1 * ((j + r) % 3)), nrm, chord, w, h, thin, "membraneedge" if r == rows - 1
                        else "membrane", inflate=0.03 * ((i + j + r) % 2))
            # the rib across this piece's middle, lying on the membrane
            um = (u0 + u1) / 2.0
            a, b = lead_at(i, um), edge_at(lead, trail, i, um)
            pts = [at(a, b, t) + nh * 0.9 for t in (0.0, 0.33, 0.66, 0.95)]
            s.curve(pts, 4, [thin * 1.6, thin * 1.4, thin * 1.2, thin], "rib")


def wing(sw, sm, sign, lead, mlead, trail, thin, young):
    """One wing: its bones (each on its own bone, `sw`), the wrist's claws, thorns on the forearm, and the membrane
    (each stretch on its own bone, `sm`)."""
    widths = [10.0, 8.0, 7.0, 5.0, 3.5, 1.5]
    for i in range(5):
        leg(sw[i], [lead[i], lead[i + 1]], [widths[i], widths[i + 1]], "wingbone", joint_scale=1.08)
    sw[2].blob(lead[2], (3.0, 3.0, 3.0), "joint", slices=2)
    wr = lead[2]
    # the thumb claw, big and hooked, and two smaller fingers
    for k, (size, length) in enumerate(((4.0, 12.0), (2.5, 8.0), (2.5, 7.0))):
        f = wr + np.array([sign * (2.0 + k * 2.5), 4.0 - k * 3.0, -length])
        sw[2].seg(wr, f, size, size, "wingbone")
        sw[2].cone(f, f + np.array([sign * 1.0, -3.0 - k, -5.0 + k]), size * 0.8, "claw", steps=2)
    # small thorns swept back along the forearm and the hand
    for i, count in ((1, 3), (2, 2)):
        a, b = lead[i], lead[i + 1]
        along = unit(b - a)
        for q in range(count):
            p = a + (b - a) * ((q + 0.6) / (count + 0.4))
            sw[i].cone(p, p + np.array([0.0, 4.0, 5.0]) + along * 2.0, 2.5, "spike", steps=2)
    membrane(sm, mlead, trail, thin)


def build(s, young=False, span=None):
    """The whole animal through `s` (a Shape for a statue, or a model's Parts for the animated mob)."""
    span = span or (YOUNG_SPAN if young else ADULT_SPAN)
    k = scale_for(span, young)
    s = Scaled(s, k)
    thin = 1.0 / k
    t0, slim, head_k, neck, hc = frame(young)

    def part(name):
        return Xform(s.part(name), pivot=t0.p, R=t0.R, shift=t0.t)

    body = part("body")
    # (long enough that the neck's root and the tail's sit inside it)
    body.blob(BODY, (12.0 * slim * 1.25, 29.0, 13.0), "hide", axis=(0, 0, 1), side=(1, 0, 0), slices=7)
    body.blob(BODY + np.array([0.0, -9.0, -11.0]), (8.5 * slim * 1.25, 9.0, 8.0), "belly", axis=(0, 0, 1),
              side=(1, 0, 0), slices=3)
    if not young:
        for n, (off, size, mat) in enumerate(CHUNKS):
            part(f"chunk_{n}").box(BODY + np.array(off), size, mat)
    for i in range(6):
        z = -18.0 + i * 8.0
        y = 13.0 - abs(z) * 0.08
        L = 7.0 - abs(i - 2) * 0.9
        body.cone(BODY + np.array([0.0, y - 2.0, z]), BODY + np.array([0.0, y + L, z + 4.0]), 3, "spike", steps=2)
    # (the neck runs on into the back of the head, so the slender neck always meets it)
    part("neck").tube(neck + [hc + np.array([0.0, -1.0, 6.0])], [12.0 * slim, 10.5 * slim, 9.5 * slim, 9.0 * slim,
                                                               8.5 * slim], "hide")
    head(Scaled(part("head"), head_k), Scaled(part("jaw"), head_k), hc / head_k,
         {"horn": 1.6, "eye": YOUNG_EYE if young else ADULT_EYE},
         [Scaled(part(f"lid_{side}"), head_k) for side, _s in SIDES],
         None if young else {name: Scaled(part(name), head_k) for name in ("bill", "horn_l", "horn_r", "crest")},
         horn_mat="horn", moon_mat="moon")
    # the legs, hanging, and their talons
    for side, sign in SIDES:
        hip, knee, ankle = leg_points(sign, slim)
        part(f"leg_{side}").seg(hip, knee, 7.0 * slim * 1.2, 7.0 * slim * 1.2, "hide", ext=0.6)
        part(f"shin_{side}").seg(knee, ankle, 5.0 * slim * 1.2, 5.0 * slim * 1.2, "hide", ext=0.6)
        foot = part(f"foot_{side}")
        for a in (-25.0, 0.0, 25.0, 180.0):
            dd = np.array([math.sin(math.radians(a)) * 0.6, -0.5, -math.cos(math.radians(a))])
            toe = ankle + dd * (7.0 if a != 180.0 else 4.0)
            foot.seg(ankle, toe, 2, 2, "hide")
            foot.cone(toe, toe + unit(dd + np.array([0.0, -1.0, 0.0])) * 4.0, 2, "claw", steps=1)
    # the tail: short, spiked
    tail = part("tail")
    root = BODY + np.array([0.0, 0.0, 24.0])
    # (its own materials, painted as its hide and spikes unless a purple look colours them: PURPLE)
    tail.cone(root, root + np.array([0.0, -6.0, 22.0]), 7, "tail", steps=3)
    tail.cone(root + np.array([0.0, -4.0, 18.0]), root + np.array([0.0, -8.0, 30.0]), 3, "tailspike", steps=2)
    # the wings
    for side, sign in SIDES:
        lead, mlead, trail = wing_edges(sign, young, t0, slim)
        wing([s.part(f"{b}_{side}") for b in WING_BONES], [s.part(f"mem_{b}_{side}") for b in WING_BONES], sign, lead,
             mlead, trail, thin, young)


def build_eye_study(s, style):
    """
    One eye study (the user: "give me variations on his eyes, it should be similar to Rodan"): the adult's head and
    the end of its neck, as big as on the adult, its eyes in `style` (a key of EYE_STYLES); its head STUDY_HEAD_UP
    blocks up, turned STUDY_TURN degrees to its left so its right eye faces whoever stands in front of it.
    """
    k = scale_for(ADULT_SPAN)
    s = Scaled(s, k)
    t0, slim, head_k, neck, hc = frame(False)
    centre = t0.P(hc)
    target = np.array([0.0, STUDY_HEAD_UP * 16.0 / k, 0.0])
    st = Xform(s, pivot=centre, R=rot_matrix((0.0, 1.0, 0.0), STUDY_TURN), shift=target - centre)
    tt = Xform(st, pivot=t0.p, R=t0.R, shift=t0.t)
    # the neck's last stretch, tapering off where it's cut
    stub = neck[1] + (neck[2] - neck[1]) * 0.4
    tt.tube([stub, neck[2], neck[3], hc + np.array([0.0, -1.0, 6.0])],
            [4.0 * slim, 9.5 * slim, 9.0 * slim, 8.5 * slim], "hide")
    head(Scaled(tt, head_k), Scaled(tt, head_k), hc / head_k, {"horn": 1.6, "eye": style})


def build_curled(s, young_span=YOUNG_SPAN):
    """
    The young curled up in its egg (the user: "a baby thing has to fit in the egg"): sitting on its haunches, its
    back bent over, its head tucked down under its chest (its beak back along its belly), its wings folded tight
    round it like a cloak, its legs tucked under, its tail coiled round its feet, its horns still soft and folded back.
    Built round (0, 0, 0), as big as the young (`young_span`); the egg is sized round it.
    """
    k = scale_for(young_span, True)
    s = Scaled(s, k)
    slim = 0.86 * SLENDER
    head_k = 1.42
    body = Xform(s, pivot=(0.0, 0.0, 0.0), axis=(1, 0, 0), deg=-20.0)
    body.blob((0.0, 2.0, 0.0), (13.0 * slim, 18.0, 12.0), "hide", axis=(0, 1, 0), side=(1, 0, 0), slices=5)
    body.blob((0.0, -2.0, -8.0), (9.0 * slim, 11.0, 5.0), "belly", axis=(0, 1, 0), side=(1, 0, 0), slices=3)
    for i in range(5):
        y = -10.0 + i * 6.0
        body.cone((0.0, y, 9.0), (0.0, y + 3.0, 14.0), 3, "spike", steps=1)
    # (its neck curled down in front of its chest, its head tucked under, its beak back along its belly: the way a
    # bird lies in its egg, and the roundest bundle: the smallest egg)
    body.tube([(0.0, 16.0, -4.0), (0.0, 13.0, -14.0), (0.0, 4.0, -18.0), (0.0, -2.0, -16.0)],
              [10.0 * slim, 9.0 * slim, 8.0 * slim, 7.5 * slim], "hide")
    hc = body.P((0.0, -4.0, -18.0))
    hx = Xform(s, pivot=hc, axis=(1, 0, 0), deg=-140.0)
    head(Scaled(hx, head_k), Scaled(hx, head_k), hc / head_k, {"horn": 1.6 * CURLED_HORN})
    for sign in (1.0, -1.0):
        bones_ = [body.P((sign * 10.0 * slim, 12.0, -2.0)), body.P((sign * 14.0, -2.0, 9.0)),
                  body.P((sign * 14.0, -15.0, -7.0)), body.P((sign * 15.0, -6.0, 9.0)),
                  body.P((sign * 14.0, 9.0, 13.0)), body.P((sign * 11.0, 20.0, 5.0))]
        leg(s, bones_, [7.0, 6.0, 5.0, 4.0, 3.0, 2.0], "wingbone", joint_scale=1.1)
        s.blob(bones_[2], (3.5, 3.5, 3.5), "joint", slices=2)
        for j in range(3):
            f = bones_[2] + np.array([sign * (1.0 + j * 1.5), -2.0 + j, -5.0 - j * 1.5])
            s.seg(bones_[2], f, 2, 2, "wingbone")
            s.cone(f, f + np.array([0.0, -2.0, -3.0]), 2, "claw", steps=1)
        for (y, z, w, h), mat in zip(((-6.0, 3.0, 18.0, 17.0), (8.0, 5.0, 16.0, 13.0), (-14.0, -3.0, 13.0, 9.0)),
                                     ("membrane", "membrane", "membraneedge")):
            body.plate((sign * 15.5, y, z), (sign, 0.0, 0.15), (0.0, 1.0, 0.0), w, h, 1, mat)
        body.plate((sign * 8.0, 6.0, 13.5), (sign * 0.4, 0.2, 1.0), (0.0, 1.0, 0.0), 11, 18, 1, "membraneedge")
        hip = body.P((sign * 7.0, -14.0, 4.0))
        knee = body.P((sign * 10.0, -18.0, -9.0))
        ankle = body.P((sign * 8.0, -23.0, -2.0))
        leg(s, [hip, knee, ankle], [6.0 * slim, 4.5 * slim, 3.5], "hide", joint_scale=1.05)
        for a in (-20.0, 20.0):
            dd = np.array([math.sin(math.radians(a)) * 0.6, -0.3, -1.0])
            toe = ankle + dd * 4.5
            s.seg(ankle, toe, 2, 2, "hide")
            s.cone(toe, toe + unit(dd + np.array([0.0, -1.0, 0.0])) * 3.0, 2, "claw", steps=1)
    s.curve([body.P((0.0, -14.0, 11.0)), body.P((9.0, -21.0, 9.0)), body.P((11.0, -24.0, -4.0)),
             body.P((2.0, -26.0, -12.0)), body.P((-8.0, -24.0, -7.0))], 8, [5.0, 4.5, 3.5, 2.5, 2.0], "hide")
