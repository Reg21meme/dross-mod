"""
No. 1, Gnawrick, the sly schemer: the classic villain rat.

Sickly sewer grey-green fur, darker down the spine, cream belly and muzzle. A small three-point gold crown with a green
gem, cocked to one side. A long blood-red cape with a ragged, moth-eaten hem and a gold clasp. A twisted dark-wood
staff: a bony claw on top clutches a glowing green orb. The classic sly hunch, his free hand drumming its fingers;
he creeps on tiptoe and sleeps slumped on his staff.
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import parts as P
from ratkit import rig as R

NUMBER = 1
LABEL = "1"
ID = "gnawrick"
NAME = "Gnawrick"
MOOD = "the sly schemer"
BLURB = "Sewer grey-green, cocked crown, ragged cape, claw-and-orb staff. Sly hunch; tiptoe creep."

FUR = palette("#262c23", "#333b30", "#41493c", "#50594a", "#606b59", "#737f6b")
LIGHT = palette("#5f5d4b", "#78755f", "#918d74", "#a8a389", "#bdb79c", "#d0caae")
CAPE = palette("#33070a", "#4c0b0f", "#691115", "#87191c", "#a32424", "#bb3330")
LINING = palette("#26060a", "#3a0a0e", "#4f0e13", "#651418")

LOOK = dict(fur=FUR, xmark="#151112", whisker_pal=palette("#121311", "#1d1f1c", "#2b2e29", "#3a3e37"))

# How he moves (ratkit/king_anims.py): his walk, his sleep, his idle gesture.
STYLE = dict(walk="sneak", sleep="slump", idle="drum", staff_clearance=1.0, plant_staff=False)


def body():
    return R.RatBody(
        hips_pitch=12.0, belly_pitch=11.0, chest_pitch=15.0, neck_pitch=12.0, head_down=8.0, head_tilt=-4.0,
        foot_target=(4.4, -4.0), foot_heading=(0.22, -1.0), hip_y=20.5,
        arm_l=((-40.0, 8.0, -14.0), (-100.0, 10.0, 0.0), (0.0, 0.0, 10.0)),
        arm_r=((-20.0, 0.0, 18.0), (-46.0, 0.0, -4.0), (0.0, 0.0, -6.0)),
        staff_dir=(-0.05, 1.0, -0.12),
        tail_dirs=[R.yaw_pitch(0, -72), R.yaw_pitch(4, -76), R.yaw_pitch(10, -60), R.yaw_pitch(18, -10),
                   R.yaw_pitch(30, 0), R.yaw_pitch(48, 0), R.yaw_pitch(70, 0), R.yaw_pitch(95, 0)],
        cape=[15, 15, 13], cape_w=16, cape_flare=(8.0, 4.0, 2.0),
    )


def build(m, B, b):
    P.torso(m, B, b, LOOK)
    P.head(m, B, b, LOOK)
    P.arms(m, B, b, LOOK)
    P.legs(m, B, b, LOOK)
    P.tail(m, B, b, LOOK)
    P.cape_panels(m, B, b)
    P.mantle(m, B, b)
    B["chest"].add((-1.5, b.chest_h - 3.0, -b.chest_size[2] / 2.0 - 1.0), (3, 2, 1), "gold", tag="clasp")
    P.hole(m, B, b)
    P.crown_three(m, B, b)
    P.staff_bone(m, B, b)


def build_staff(m, B, b, below):
    P.staff_claw_orb(m, B, b, below=below, above=22)


def materials():
    mats = F.standard(LOOK)
    mats.update({
        "fur": F.fur(FUR, LIGHT, seed=101, back_dark=0.7),
        "cape": F.cloth(CAPE, seed=102, lining=LINING, ragged=3.0, holes=0.035, hem_tags=("cape_2",)),
        "wood": F.wood(F.WOOD, seed=103),
        "bone": F.bone(seed=104),
        "orb": F.gem(seed=105),
        "gem": F.gem(seed=106),
    })
    return mats
