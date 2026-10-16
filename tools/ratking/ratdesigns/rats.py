"""
The Rat King's rats, two kinds, one design each: the user's picks from round 1 (the other six are archived in
ratdesigns/round1/rats_round1.py):

  regular rats (dross:rat)          about 0.65 blocks long
    R3 Plague Rat     mangy grey-green, bald patches, a notched ear, a scabby tail; sickly green pustules, mirrored
                      on both sides (the user's note) and down its back
  enraged rats (dross:enraged_rat)  a separate, scarier breed, about 1.4 times bigger: glowing red eyes, bristled fur,
                                    big fangs
    E3 Plague Ravager grey-green, glowing green boils in mirrored pairs (the user's note), drooling

Each is a class with LABEL, ID, NAME, MOOD (sign text, 15 characters at most), ENRAGED, props() (the proportions),
build(m, B, q), materials(), and its rest stance.
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import quad as Q
from ratkit import rig as R

REGULAR_TEXTURE = 64
ENRAGED_TEXTURE = 128

RAGE_EYES = palette("#5a0000", "#a00808", "#e01a12", "#ff5a2a", "#ffb070")
TOOTH_PALE = palette("#8c7a4a", "#ab9a62", "#c9b97e", "#e0d29c", "#efe5bd")


def regular(**kw):
    base = dict(back_y=6.0, rear=(5, 4, 5), fore=(4, 4, 4), skull=(4, 3, 3), snout=(3, 2, 2), ear=(3, 3), eye=1, leg=1,
                front_len=3.0, hind_len=3.0, hind_x=2.0, front_x=1.5, foot=3,
                tail=[(3, 2), (3, 1), (3, 1), (3, 1), (3, 1)], whisker=2, fang=1,
                tail_dirs=[R.yaw_pitch(0, -30), R.yaw_pitch(8, -20), R.yaw_pitch(20, -5), R.yaw_pitch(35, 0),
                           R.yaw_pitch(55, 0)])
    base.update(kw)
    return Q.RatProportions(**base)


def enraged(**kw):
    base = dict(back_y=9.0, rear=(7, 6, 7), fore=(6, 6, 6), skull=(6, 5, 4), snout=(4, 3, 3), ear=(4, 4), eye=1, leg=2,
                front_len=4.0, hind_len=4.0, hind_x=2.5, front_x=2.0, foot=4,
                tail=[(4, 2), (4, 2), (4, 1), (4, 1), (4, 1)], whisker=4, fang=2,
                tail_dirs=[R.yaw_pitch(0, -20), R.yaw_pitch(-10, -10), R.yaw_pitch(-20, 0), R.yaw_pitch(-35, 0),
                           R.yaw_pitch(-55, 0)],
                bristles=[("fore", -1.0, 2, 35.0), ("fore", -3.0, 3, 30.0), ("fore", -5.0, 2, 25.0),
                          ("rear", 1.0, 3, 35.0), ("rear", 3.0, 3, 40.0), ("rear", 5.0, 2, 45.0)])
    base.update(kw)
    return Q.RatProportions(**base)


def common(fur_pal, light=None, eyes=None, skin=F.SKIN, tail=F.TAIL, ear_inner=F.SKIN, glow_eyes=False, notch=None,
           **furkw):
    """The materials every rat shares; furkw goes to the fur painter (patterns)."""
    mats = {
        "fur": F.fur(fur_pal, light, light_tags=F.RAT_LIGHT_TAGS, strand=2.2, back_dark=0.5, **furkw),
        "skin": F.solid(skin, 3.0, seed=81, grain=0.6, bev=0.5),
        "tooth": F.solid(F.TOOTH if glow_eyes else TOOTH_PALE, 3.0, seed=83, grain=0.4, bev=0.8),
        "nose": F.solid(F.NOSE, 2.8, seed=84, grain=0.5, bev=0.6),
        "tail": F.tail(tail),
        "ear": F.ear(fur_pal, inner=ear_inner, notch=notch),
        "whisker": F.strands(palette("#8c8a84", "#a3a19a", "#b9b7b0", "#cecbc4")),
        "bristle": F.strands(fur_pal),
    }
    if glow_eyes:
        # Enraged: glowing red eyes (the glow texture lights them at night).
        mats["eye"] = F.solid(RAGE_EYES, 2.8, seed=85, grain=0.4, bev=0.8, glow=RAGE_EYES)
    else:
        mats["eye"] = F.eye(eyes)
    return mats


# ---------------------------------------------------------------- the regular rat


class PlagueRat:
    LABEL, ID, NAME, MOOD, ENRAGED = "R3", "plague", "Plague Rat", "mangy, sickly", False
    FUR = palette("#343a2c", "#424a38", "#525a45", "#626c54", "#747f64", "#879276")
    LIGHT = palette("#6e7060", "#828472", "#959784", "#a8aa96")
    STANCE = dict(arch=8.0, head_pitch=-2.0)

    @staticmethod
    def props():
        return regular(tail=[(3, 2), (3, 1), (3, 1), (3, 1)],
                       tail_dirs=[R.yaw_pitch(0, -35), R.yaw_pitch(15, -15), R.yaw_pitch(35, 0), R.yaw_pitch(60, 0)])

    # Sickly green pustules (the user's pick in round 1, asking for more of them and the same on both sides): three
    # pairs on the flanks, mirrored left and right, and two down the middle of the back. (bone, y, z) in the bone's
    # frame.
    FLANK_SPOTS = (("fore", -2.0, -2.5), ("rear", -1.5, 1.5), ("rear", -2.5, 3.5))
    BACK_SPOTS = (("fore", -1.5), ("rear", 2.5))

    @classmethod
    def build(cls, m, B, q):
        Q.body_parts(m, B, q, {})
        for bone, y, z in cls.FLANK_SPOTS:
            w = (q.fore if bone == "fore" else q.rear)[0]
            for sx in (1, -1):
                # Half in the flank, half out of it.
                x = w / 2.0 - 0.5 if sx > 0 else -w / 2.0 - 0.5
                B[bone].add((x, y, z), (1, 1, 1), "pustule", tag="boil")
        for bone, z in cls.BACK_SPOTS:
            B[bone].add((-0.5, 0.0, z), (1, 1, 1), "pustule", tag="boil")

    @classmethod
    def materials(cls):
        mats = common(cls.FUR, cls.LIGHT, mange=0.22, scars=0.05)
        mats["ear"] = F.ear(cls.FUR, notch="l")
        mats["tail"] = F.tail(F.TAIL, scabby=0.3)
        mats["pustule"] = F.solid(palette("#4f6a20", "#6b8a2a", "#8aa83a", "#a8c457"), 2.4, seed=86)
        return mats


class PlagueRavager:
    LABEL, ID, NAME, MOOD, ENRAGED = "E3", "ravager", "Plague Ravager", "glowing boils", True
    FUR = palette("#2c3224", "#384030", "#454e3b", "#545e48", "#646f57", "#768167")
    LIGHT = palette("#5d6150", "#6e7260", "#808471", "#929683")
    STANCE = dict(arch=12.0, head_pitch=4.0)

    @staticmethod
    def props():
        return enraged()

    # Glowing green boils, the same on both sides (the user's note on round 1): mirrored pairs on the cheeks, the
    # shoulders and the haunches (big 2-unit boils on the body), and a pair either side of the spine between its
    # bristles. (bone, y, z, size) of each pair, in the bone's frame.
    BOIL_PAIRS = (("head", 0.5, -2.0, 1), ("fore", -3.0, -3.5, 2), ("rear", -2.5, 3.0, 2), ("rear", -1.0, 5.5, 1))
    BACK_BOILS = (("rear", 1.5, 3.5),)       # (bone, x out from the spine, z)

    @classmethod
    def build(cls, m, B, q):
        Q.body_parts(m, B, q, {})
        for bone, y, z, size in cls.BOIL_PAIRS:
            w = {"head": q.skull[0], "fore": q.fore[0], "rear": q.rear[0]}[bone]
            out = size / 2.0          # half of each boil sticks out of the flank
            for sx in (1, -1):
                x = w / 2.0 - size + out if sx > 0 else -w / 2.0 - out
                B[bone].add((x, y, z), (size, size, size), "boil", tag="boil")
        for bone, dx, z in cls.BACK_BOILS:
            for sx in (1, -1):
                B[bone].add((sx * dx - 0.5, 0.0, z), (1, 1, 1), "boil", tag="boil")
        jaw = B["jaw"]
        jaw.add((-0.5, -3.0, -2.0), (1, 2, 1), "drool", tag="drool")

    @classmethod
    def materials(cls):
        mats = common(cls.FUR, cls.LIGHT, glow_eyes=True, mange=0.25)
        mats["boil"] = F.gem(F.GREEN_GLOW, seed=89)
        mats["drool"] = F.gem(palette("#2f5a14", "#4f8a20", "#79c93a", "#a6ec5b", "#d4ff8a"), seed=90)
        mats["tail"] = F.tail(F.TAIL, scabby=0.4)
        return mats


REGULAR = (PlagueRat,)
ENRAGED = (PlagueRavager,)
ALL = REGULAR + ENRAGED
