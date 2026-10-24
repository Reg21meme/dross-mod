"""
The hunter villages' people (the user, October 23, 2026: "all of them should again have the same design of the hunter,
just 2 blocks tall"): the Huntsman on foot (rimemobs/huntsman.py, his skeleton rimemobs/hunter_rig.py), the same model
drawn at half his size in the game (boss/huntsman/folk/: about 2 blocks to the top of the hood), with the gear of
eleven callings on bones of their own; the game shows each calling only its own gear (HunterFolk's FolkRole):

  warriors (25-30 health)   spearman     his spear and a round shield of hide on a bone cross (shield)
                            axeman       an axe, its blade of ice (axe)
                            pikeman      a long spear (long_spear)
                            archer       his bow (his own bow, on his back and drawn; no spear)
                            horn-blower  his horn and spear, a flag on a pole on his back (flag)
  workers                   farmer       a hoe (hoe), a basket of Frostroots (basket)
                            butcher      a cleaver (cleaver), a leather apron (apron)
                            herder       a shepherd's crook (crook)
                            fisher       a rod and line (rod), a fish (fish)
                            tanner       a scraper (scraper), a rolled hide on his back (hide_roll)
  the elder (50 health)     his hood's wolf face pushed back off his own (snout hidden, face shown): his face
                            scarred across both eyes, a grey beard; a sword (sword)

The workers' hoods have no antlers (antlers hidden). The wolf's face, the antlers and the elder's face are their own
bones under the head (head(): the Huntsman's, split).

Animations: the Huntsman's own (idle, walk, run, spear_combo, thrust_sweep, quick_shot, aimed_shot, horn_blow, death)
and two of theirs: chop (a blow raised overhead and brought down: the axe, the sword, the workers' tools; the hit at
CHOP_HIT) and talk (a nod and a hand raised as they speak).
"""
import math

import numpy as np

from kit.anims import ramp
from kit.moves import keyed
from rimekit import mats as M
from rimekit import mobkit
from rimekit.mobkit import Pose
from rimekit.shapes import Moved, Scaled

from . import hunter_rig as R
from . import huntsman as HU
from rimedesigns import d06_huntsman as H

ID = "hunter_folk"
NAME = "The hunter villagers"
POSE = "stand"
K = R.K

# ---------------------------------------------------------------- their gear (design units, the Huntsman's frame
# before he's stood on the ground: his left +x, his face -z, up +y; his right fist at H.STAND_FISTS[0])

UP = np.array([0.0, 1.0, 0.0])
FWD = np.array([0.0, 0.0, -1.0])
LONG_SPEAR = 165.0               # the pikeman's spear, butt to tip (the Huntsman's is H.SPEAR, 119)
SHIELD_R = 19.0                  # the round shield's radius (about as wide as his chest)
CROOK = 130.0                    # the herder's crook, from the ground
# the callings' own bones: (bone, its parent, the joint it turns about)
GEAR = (("shield", "forearm_l", "forearm_l"), ("axe", "hand_r", "hand_r"), ("long_spear", "hand_r", "hand_r"),
        ("sword", "hand_r", "hand_r"), ("hoe", "hand_r", "hand_r"), ("cleaver", "hand_r", "hand_r"),
        ("crook", "hand_r", "hand_r"), ("rod", "hand_r", "hand_r"), ("scraper", "hand_r", "hand_r"),
        ("basket", "hand_l", "hand_l"), ("fish", "hand_l", "hand_l"), ("flag", "chest", "chest"),
        ("apron", "chest", "chest"), ("hide_roll", "chest", "chest"), ("antlers", "head", "head"),
        ("snout", "head", "head"), ("face", "head", "head"))


def head(r):
    """The Huntsman's head (H.head), split: the hood on the head bone, the wolf's face (snout), the antlers, and the
    elder's own face (face: shown only for him, with the wolf's face pushed back off it)."""
    s = r.part("head")
    s.seg((0, 145, 1), (0, 156, 1), 12, 12, "cloth")
    s.rbox((0, 158, 0), (16, 15, 16), "cloth", c=3)
    s.rbox((0, 166, 1), (20, 8, 20), "pelt", c=3)
    s.plate((0, 161, 10.5), (0, 0.15, 1), (0, 1, 0), 16, 11, 2, "pelt")
    w = r.part("snout")
    w.rbox((0, 164, -13), (10, 7, 12), "pelt", c=2)
    w.box((0, 166, -19.4), (4, 3, 1), "mouth")
    w.box((0, 155.5, -8.2), (11, 8, 1), "mouth")
    for x in (-1, 1):
        w.box((x * 4, 167.5, -9.6), (2, 1, 1), "eyesocket")
        w.cone((x * 3.5, 161.5, -17), (x * 3.5, 156.5, -17.5), 2, "teeth", steps=1)
        w.cone((x * 4.5, 161.5, -12), (x * 4.5, 157.5, -12), 1.5, "teeth", steps=1)
        w.box((x * 2.6, 156.5, -8.9), (3, 2, 1), "eyesocket")
    a = r.part("antlers")
    for x in (-1, 1):
        a.curve([(x * 4, 165, -6), (x * 10, 175, -4), (x * 13, 186, -8), (x * 12, 196, -6)], 5, [3, 2.6, 2.2, 1.6],
                "antler")
        for (y, dz, dx) in ((176, -6, 4), (184, -2, 6), (191, -10, 3)):
            a.cone((x * (10 + dx * 0.2), y, -5), (x * (12 + dx), y + 7, -5 + dz), 1.6, "antler", steps=1)
    f = r.part("face")
    f.box((0, 156, -9.2), (11, 9, 1), "skin")                        # his own face, under the pushed-back hood
    f.box((0, 163.5, -8.6), (14, 2, 3), "pelt")                      # the wolf's face, pushed back, its edge
    for x in (-1, 1):
        f.box((x * 2.6, 157.5, -9.9), (2, 1, 1), "eyepale")          # his old eyes
        for k in range(4):                                           # the scars across them
            f.box((x * (1.2 + k * 0.9), 160 - k * 1.4, -10.0), (1, 1, 1), "scar")
    f.box((0, 154.0, -10.0), (4, 1, 1), "mouth")
    f.rbox((0, 149.5, -8.5), (11, 9, 5), "beard", c=2)               # a grey beard, long
    f.rbox((0, 143.0, -8.0), (7, 6, 4), "beard", c=2)


def gear(r):
    """Every calling's gear, each on its own bone (the game shows only a calling's own)."""
    fr = H.STAND_FISTS[0]
    fl = H.STAND_FISTS[1]
    ground = -H.STAND_DROP + 1.0                                     # (the snow, in this frame)
    # the spearman's round shield, strapped on his left forearm's outside: rows of hide, a bone cross, an iron boss
    sh = r.part("shield")
    c = np.array([fl[0] + 6.0, fl[1] + 8.0, fl[2] - 1.0])
    for y in range(-int(SHIELD_R), int(SHIELD_R) + 1, 2):
        half = math.sqrt(max(0.0, SHIELD_R * SHIELD_R - (y + 1.0) ** 2))
        if half >= 2.0:
            sh.box((c[0] + 1.5, c[1] + y + 1.0, c[2]), (2, 2, int(round(half * 2))), "shieldhide")
    sh.box((c[0] + 2.8, c[1], c[2]), (1, int(SHIELD_R * 2 - 2), 2), "bone")
    sh.box((c[0] + 2.8, c[1], c[2]), (1, 2, int(SHIELD_R * 2 - 2)), "bone")
    sh.rbox((c[0] + 3.5, c[1], c[2]), (2, 5, 5), "iron", c=1)
    sh.box((c[0] - 1.0, c[1], c[2]), (3, 3, 3), "leather")           # its strap round the forearm
    # the axeman's axe, carried upright: a pole, an iron head, a crescent blade of ice
    ax = r.part("axe")
    ax.seg(fr - UP * 18, fr + UP * 42, 4, 4, "shaft")
    ax.box(fr + UP * 37 + FWD * 3.0, (4, 9, 6), "iron")
    ax.rbox(fr + UP * 37 + FWD * 10.0, (3, 24, 9), "lance", c=3)
    # the pikeman's long spear (as the Huntsman's, longer)
    ls = r.part("long_spear")
    butt = np.array([fr[0], ground, fr[2]])
    tip = butt + UP * LONG_SPEAR
    ls.seg(butt, tip - UP * 20, 3, 3, "shaft")
    ls.cone(tip - UP * 21, tip, 6, "lance", d0=3, steps=5)
    ls.ring(tip - UP * 22, UP, 2.2, 1, 2, "leather", n=6)
    # the elder's sword: a leather grip, an iron guard and pommel, a long blade of ice
    sw = r.part("sword")
    sw.seg(fr - UP * 5, fr + UP * 4, 3, 3, "leather")
    sw.rbox(fr - UP * 6.5, (4, 3, 4), "iron", c=1)
    sw.box(fr + UP * 5.0, (12, 2, 3), "iron")
    sw.seg(fr + UP * 6, fr + UP * 42, 5, 2, "lance")
    sw.cone(fr + UP * 42, fr + UP * 47, 2.5, "lance", steps=2)
    # the farmer's hoe: a long handle, an iron blade forward at its top
    ho = r.part("hoe")
    ho.seg(butt, fr + UP * 30, 3, 3, "shaft")
    ho.box(fr + UP * 29 + FWD * 6.5, (6, 4, 12), "iron")
    # the butcher's cleaver
    cl = r.part("cleaver")
    cl.seg(fr - UP * 5, fr + UP * 8, 3, 3, "shaft")
    cl.box(fr + UP * 18 + FWD * 4.0, (3, 20, 13), "iron")
    # the herder's crook: a long staff, its top curled round
    cr = r.part("crook")
    top = np.array([fr[0], ground + CROOK, fr[2]])
    cr.seg(butt, top, 3, 3, "shaft")
    cr.curve([top, top + UP * 6 + FWD * 2, top + UP * 8 + FWD * 8, top + UP * 4 + FWD * 12, top - UP * 2 + FWD * 11],
             5, [1.6, 1.6, 1.5, 1.4, 1.3], "shaft")
    # the fisher's rod and its line
    ro = r.part("rod")
    rtip = fr + UP * 40 + FWD * 34
    ro.seg(fr - UP * 6 - FWD * 4, rtip, 2, 2, "shaft")
    ro.seg(rtip, rtip - UP * 34, 1, 1, "string")
    ro.box(rtip - UP * 35, (2, 2, 2), "iron")
    # the tanner's scraper: a short handle, a curved blade
    sc = r.part("scraper")
    sc.seg(fr - UP * 4, fr + UP * 9, 3, 3, "shaft")
    sc.box(fr + UP * 13.5 + FWD * 1.0, (3, 6, 18), "iron")
    # the farmer's basket of Frostroots, from his left fist
    ba = r.part("basket")
    ba.box(fl - UP * 5.5, (2, 9, 2), "cord")
    ba.rbox(fl - UP * 17.0, (17, 14, 15), "wicker", c=1)
    ba.box(fl - UP * 9.5, (15, 2, 13), "frostroot")
    # the fisher's fish, hung from his left fist by its tail
    fi = r.part("fish")
    fi.box(fl - UP * 4.5, (2, 3, 2), "cord")
    fi.rbox(fl - UP * 17.0, (5, 22, 9), "fishscale", c=1)
    fi.box(fl - UP * 6.5, (2, 3, 9), "fishscale")
    # the horn-blower's flag on his back: a pole above his antlers, a banner with the Huntsman's skull
    fl_ = r.part("flag")
    pole0 = np.array([-7.0, 104.0, 13.0])
    pole1 = np.array([-7.0, 232.0, 13.0])
    fl_.seg(pole0, pole1, 3, 3, "shaft")
    fl_.box((2.0, 229.0, 13.0), (20, 2, 2), "shaft")
    fl_.box((2.0, 210.0, 13.5), (18, 36, 1), "flagcloth")
    fl_.box((2.0, 214.0, 12.8), (8, 8, 1), "bone")
    fl_.box((2.0, 209.0, 12.8), (4, 3, 1), "bone")
    # the butcher's apron, from his chest down over his thighs
    ap = r.part("apron")
    ap.box((0.0, 104.0, -12.5), (20, 40, 1), "apronleather")
    ap.box((0.0, 126.0, -11.5), (22, 2, 2), "cord")
    # the tanner's rolled hide across his shoulders
    hr = r.part("hide_roll")
    hr.seg((-14.0, 142.0, 13.0), (14.0, 142.0, 13.0), 9, 9, "hide")
    hr.box((0.0, 142.0, 13.0), (4, 11, 11), "cord")


class Design:
    """The Huntsman on foot, his head split (head()) and every calling's gear (gear())."""

    @staticmethod
    def build(s, v):
        s = Moved(Scaled(s, K), (0.0, 0.0, 0.0))
        r = Moved(s, (0.0, H.STAND_DROP, 0.0))
        H.legs_rider(r, POSE)
        H.torso(r.part("chest"), r)
        H.mantle(r.part("chest"))
        H.cloak(r.part("cape"), POSE, v.get("shrink"))
        H.arms(r, H.STAND_FISTS, POSE)
        H.spear(r.part("spear"), H.STAND_FISTS[0], POSE)
        H.bow(r.part("bow_back"))
        H.props_in_hand(r, H.STAND_FISTS, POSE)
        head(r)
        gear(r)

    @staticmethod
    def materials(v):
        out = dict(H.materials(v))
        out.update({
            "skin": M.skin(SKIN, seed=1701, mottle=0.6, frost_amount=0.05),
            "eyepale": M.solid(M.RIME, idx=4.6, seed=1703, outline=0.0),
            "scar": M.solid(SCAR, idx=3.0, seed=1705, outline=0.0),
            "beard": M.fur(BEARD, seed=1707, strand=2.2, frost_amount=0.25),
            "shieldhide": M.worn_hide(H.BEARSKIN, seed=1709, frost_amount=0.2),
            "flagcloth": M.cloth(M.CLOTH_DARK, seed=1711, folds=1.4, ragged=0.6, frost_amount=0.2),
            "apronleather": M.skin(M.LEATHER, seed=1713, mottle=0.8, frost_amount=0.1),
            "hide": M.worn_hide(H.BEARSKIN, seed=1715, frost_amount=0.25),
            "wicker": M.wood(M.WOOD, seed=1717, plank=1.5, axis=1, frost_amount=0.2),
            "frostroot": M.solid(M.RIME, idx=3.4, seed=1719, outline=0.1),
            "fishscale": M.solid(FISH, idx=3.0, seed=1721, outline=0.1),
        })
        return out


SKIN = M.palette("#5b3f31", "#6e4c3b", "#835b47", "#986b54", "#ab7c63", "#bd8e74")      # weathered, wind-burnt
SCAR = M.palette("#5a2f2a", "#6e3b34", "#83483f", "#97564b", "#a86558", "#b87567")
BEARD = M.palette("#5f5d59", "#76736e", "#8d8a84", "#a4a19a", "#bab7b0", "#d0cdc6")      # grey, frosted
FISH = M.palette("#2f3f4a", "#3e5260", "#4f6676", "#617b8c", "#7590a2", "#8aa6b8")


def variant(pose):
    v = R.variant(pose)
    v["mood"] = "the hunter villagers"
    return v


DESIGN = Design
BUILD_VARIANT = variant(POSE)
VARIANTS = (dict(name="hunter_folk", design=BUILD_VARIANT, hidden=()),)


def _bones():
    out = R.bones(POSE)
    j = R.joints(POSE)
    for name, parent, at in GEAR:
        out.append((name, tuple(float(v) for v in j[at]), parent))
    return out


BONES = _bones()
ROUTES = R.ROUTES
FREE = HU.FREE + tuple(name for name, _p, _a in GEAR)
LEGS = HU.LEGS
GIF_VIEW = HU.GIF_VIEW
CHOP_HIT = 0.5
HITS = dict(HU.HITS, chop=(CHOP_HIT,))


def clip_group(bone):
    if bone in ("axe", "sword", "hoe", "cleaver", "crook", "scraper", "long_spear"):
        return "spear"
    return R.clip_group(bone)


CLIP_PAIRS = HU.CLIP_PAIRS


# ---------------------------------------------------------------- their own two animations

RAISE = HU.grip((-160.0, 0.0, -10.0), (-35.0, 0.0, 0.0), -15.0)      # the tool raised overhead, its head back
STRIKE = HU.grip((-65.0, 0.0, 0.0), (-10.0, 0.0, 0.0), 120.0)        # brought down in front, its head forward and low


def chop(rig):
    T = 1.1

    def frame(t):
        p = Pose()
        HU._base(p, t)
        keys = [(0.0, HU.REST), (0.38, RAISE), (CHOP_HIT, STRIKE, "in"), (0.75, STRIKE), (T, HU.REST)]
        R.arm(p, "r", **HU._armkeys(keys, t))
        HU._lunge(p, t, [(0.0, 0.0), (0.38, -0.2), (CHOP_HIT, 1.0, "in"), (0.8, 0.6), (T, 0.0)])
        twist = float(keyed(t, [(0.0, 0.0), (0.38, 14.0), (CHOP_HIT, -10.0, "in"), (T, 0.0)]))
        p.rot("chest", 0.0, twist, 0.0)
        rig.plant(p)
        R.show(p)
        return p

    a = mobkit.record(rig, "chop", T, frame, loop=False)
    a.meta["hits"] = [CHOP_HIT]
    return a


def talk(rig):
    T = 2.0

    def frame(t):
        p = Pose()
        HU._base(p, t, T)
        up = ramp(t, 0.15, 0.5) * (1.0 - ramp(t, 1.5, 1.9))
        p.rot("head", 8.0 * math.sin(2 * math.pi * t / 0.9) * up, 6.0 * up, 0.0)          # nodding as he speaks
        R.arm(p, "l", sh=(-38.0 * up, 0.0, -12.0 * up), el=(-55.0 * up, 0.0, 0.0), hand=(0.0, 0.0, -20.0 * up))
        p.rot("chest", 3.0 * up, 0.0, 0.0)
        rig.plant(p)
        R.show(p)
        return p

    return mobkit.record(rig, "talk", T, frame, loop=False)


HAND_GEAR = ("axe", "long_spear", "sword", "hoe", "cleaver", "crook", "rod", "scraper", "basket", "fish")
DROP_GEAR = (0.04, 0.3)          # in the death the tools slip out of their hands with the spear (gone by its second)


def _drops_gear(a):
    """Their tools let go in the death as the Huntsman's spear does (his falls on its own; theirs are just gone)."""
    start, end = DROP_GEAR
    for bone in HAND_GEAR:
        a.tracks.setdefault(bone, {})["scale"] = [(0.0, (1.0, 1.0, 1.0)), (start, (1.0, 1.0, 1.0)), (end, (0.0, 0.0, 0.0)),
                                                  (a.length, (0.0, 0.0, 0.0))]
    return a


# ---------------------------------------------------------------- the callings

# Every gear bone a calling may show (the Huntsman's own props and the villagers' gear), and what each calling shows
# and plays besides IDLE_ETC (the game's FolkRole has the same lists; folk_preview.py draws them). The clipping check
# looks at each calling with only its own gear, in only its own moves (mobs.py, CALLINGS): all the gear at once (a hoe,
# an axe and a shield in one pair of hands) would clip where no calling does.
FOLK_GEAR = ("spear", "spear_free", "bow_back", "horn_belt", "quiver_hip", "shield", "axe", "long_spear", "sword",
             "hoe", "cleaver", "crook", "rod", "scraper", "basket", "fish", "flag", "apron", "hide_roll", "antlers",
             "snout", "face")
IDLE_ETC = ("idle", "walk", "run", "talk", "death")
CALLINGS = (
    ("spearman", "Spearman", "warrior", ("spear", "spear_free", "shield", "antlers", "snout"), ("spear_combo",)),
    ("axeman", "Axeman", "warrior", ("axe", "antlers", "snout"), ("chop",)),
    ("pikeman", "Pikeman", "warrior", ("long_spear", "antlers", "snout"), ("thrust_sweep",)),
    ("archer", "Archer", "warrior", ("bow_back", "quiver_hip", "antlers", "snout"), ("quick_shot", "chop")),
    ("hornblower", "Horn-blower", "warrior", ("spear", "spear_free", "horn_belt", "flag", "antlers", "snout"),
     ("horn_blow", "thrust_sweep")),
    ("farmer", "Farmer", "worker", ("hoe", "basket", "snout"), ("chop",)),
    ("butcher", "Butcher", "worker", ("cleaver", "apron", "snout"), ("chop",)),
    ("herder", "Herder", "worker", ("crook", "snout"), ("chop",)),
    ("fisher", "Fisher", "worker", ("rod", "fish", "snout"), ("chop",)),
    ("tanner", "Tanner", "worker", ("scraper", "hide_roll", "snout"), ("chop",)),
    ("elder", "The Elder", "elder", ("sword", "antlers", "face"), ("chop",)),
)


def hidden_for(shows):
    """The gear bones a calling that shows `shows` hides."""
    return tuple(b for b in FOLK_GEAR if b not in shows)


def animations(rig):
    return {
        "idle": HU.idle(rig),
        "walk": HU._stride(rig, "walk", HU.WALK, 4.0, False),
        "run": HU._stride(rig, "run", HU.RUN, 14.0, True),
        "spear_combo": HU.spear_combo(rig),
        "thrust_sweep": HU.thrust_sweep(rig),
        "chop": chop(rig),
        "quick_shot": HU.quick_shot(rig),
        "aimed_shot": HU.aimed_shot(rig),
        "horn_blow": HU.horn_blow(rig),
        "talk": talk(rig),
        "death": _drops_gear(HU.death(rig)),
    }
