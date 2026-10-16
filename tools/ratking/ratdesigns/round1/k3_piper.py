"""
No. 3, the Pied Usurper, who stole the pipe.

Piebald: off-white fur with grey-green patches, one dark ear and a patch over one eye. A crown of bent forks and spoons
stuck in a tin band. A red curtain for a cape, gold tassels on its hem, split up the back (his tail comes out through
the split). His staff is the Pied Piper's long wooden pipe, with ribbons: he stole it, and now the rats follow HIM.
A bow-legged crouch, tail curled up like a question mark; he scurries furtively and sleeps curled round his pipe.
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import parts as P
from ratkit import rig as R

NUMBER = 3
LABEL = "3"
ID = "piper"
NAME = "Pied Usurper"
MOOD = "stole the pipe"
BLURB = "Piebald off-white and grey-green, a crown of forks and spoons, a tasselled curtain cape, the Pied Piper's pipe."

FUR = palette("#8a8676", "#a29e8d", "#b8b4a3", "#cbc7b6", "#dbd8c8", "#e9e6d9")
PATCH = palette("#2b3127", "#384034", "#464f41", "#555f4f", "#677260", "#7a8572")
LIGHT = palette("#a9a596", "#c0bcac", "#d3cfbf", "#e2dfd0", "#efecdf")
CAPE = palette("#4a0b0f", "#6a1116", "#8a191e", "#a82327", "#c0312f", "#d2433b")
LINING = palette("#7a5a12", "#96721a", "#b08b26", "#c7a237")

LOOK = dict(fur=FUR, xmark="#141012", ear_fur=FUR, whisker_pal=palette("#4c4a42", "#5f5d54", "#757268", "#8b887d"))

STYLE = dict(walk="scurry", sleep="curl", idle="pipe", staff_clearance=1.5, plant_staff=False)


def body():
    return R.RatBody(
        hips_pitch=14.0, belly_pitch=10.0, chest_pitch=14.0, neck_pitch=10.0, head_down=4.0, head_turn=-8.0,
        foot_target=(6.2, -3.0), foot_heading=(0.55, -1.0), hip_y=19.0,
        hand_l_target=(3.0, 33.0, -9.0),
        arm_r=((-26.0, 0.0, 20.0), (-44.0, 0.0, -4.0), (0.0, 0.0, -8.0)),
        staff_dir=(-0.18, 1.0, -0.2),
        tail_dirs=[R.yaw_pitch(0, -75), R.yaw_pitch(0, -78), R.yaw_pitch(30, -30), R.yaw_pitch(70, 30),
                   R.yaw_pitch(80, 70), R.yaw_pitch(120, 75), R.yaw_pitch(160, 20), R.yaw_pitch(170, -40)],
        tail=[(6, 3), (6, 3), (6, 2), (6, 2), (5, 2), (5, 2), (4, 1), (4, 1)],
        cape=[14, 14, 13], cape_w=16, cape_flare=(10.0, 6.0, 3.0),
    )


def build(m, B, b):
    P.torso(m, B, b, LOOK)
    P.head(m, B, b, LOOK)
    P.arms(m, B, b, LOOK)
    P.legs(m, B, b, LOOK)
    P.tail(m, B, b, LOOK)
    P.cape_panels(m, B, b)
    P.mantle(m, B, b)
    P.tassels(m, B, b, 2, n=7)
    B["chest"].add((-1.0, b.chest_h - 3.0, -b.chest_size[2] / 2.0 - 1.0), (2, 2, 1), "brass", tag="clasp")
    P.hole(m, B, b)
    P.crown_cutlery(m, B, b)
    P.staff_bone(m, B, b)


def build_staff(m, B, b, below):
    P.staff_pipe(m, B, b, below=below, above=24)


def materials():
    mats = F.standard(LOOK)
    mats.update({
        "fur": F.fur(FUR, LIGHT, seed=301, back_dark=0.35, patches=PATCH, patch_cell=8.0, patch_share=0.42,
                     dark_ear="l", eye_patch="r"),
        "ear": F.ear(FUR, dark="l", dark_pal=PATCH),
        "cape": F.cloth(CAPE, seed=302, lining=LINING, split=3.5, hem_tags=("cape_2",), folds=1.3, ragged=1.2),
        "silver": F.metal(F.SILVER, seed=303),
        "spoon": F.spoon(seed=304),
        "pipe": F.wood(F.PIPE, seed=305, knots=0.05, holes_every=4.0),
        "brass": F.metal(F.BRASS, seed=306),
        "ribbon_red": F.cloth(F.RIBBON_RED, seed=307, folds=0.6),
        "ribbon_gold": F.cloth(F.RIBBON_GOLD, seed=308, folds=0.6),
    })
    return mats
