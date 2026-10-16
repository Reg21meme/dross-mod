"""
No. 6, the Tin-Can Tyrant, the scrappy brute.

Scruffy olive grey-green fur with dark tabby stripes and old scars, a notched ear with a gold hoop earring, and an
eyepatch. A crown cut from a tin can: jagged points, a dent, a faded label. A short red cloth knotted under his chin like
a kid's cape. A huge rusty three-tined fork, resting on his shoulder. A cocky, wide swagger, chin up, fist on his hip;
he walks with a bouncy shoulder roll and sleeps curled up with the fork beside him.
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import parts as P
from ratkit import rig as R

NUMBER = 6
LABEL = "6"
ID = "tincan"
NAME = "Tin-Can Tyrant"
MOOD = "scrappy brute"
BLURB = "Striped olive grey-green, scars, eyepatch, earring, a tin-can crown, a kid's cape, a giant rusty fork on his shoulder."

FUR = palette("#2c3023", "#393e2e", "#474d39", "#565d46", "#676f55", "#7a8267")
STRIPES = palette("#1a1d15", "#22261b", "#2b3022", "#353b2a")
LIGHT = palette("#68664f", "#817e66", "#99967c", "#adaa90", "#bfbca1")
CAPE = palette("#560e0f", "#771416", "#981c1e", "#b62827", "#ce3b33", "#de5144")

LOOK = dict(fur=FUR, xmark="#151112", notch="l", no_eye=("l",),
            whisker_pal=palette("#14150f", "#202219", "#2c2f23", "#393d2e"))

STYLE = dict(walk="swagger", sleep="curl", idle="fork", staff_clearance=1.0, plant_staff=False)


def body():
    return R.RatBody(
        hips_pitch=4.0, belly_pitch=2.0, chest_pitch=-2.0, neck_pitch=10.0, head_down=-8.0, head_tilt=5.0,
        foot_target=(6.4, -2.0), foot_heading=(0.45, -1.0), hip_y=21.0,
        hips_size=(11, 8, 10), belly_size=(13, 10, 11), chest_size=(10, 10, 8), shoulder_x=6.0, arm_w=4, fore_w=3,
        hand_l_target=(8.5, 23.0, 0.5),
        arm_r=((-58.0, 0.0, 28.0), (-104.0, 0.0, 10.0), (0.0, 0.0, 0.0)),
        staff_dir=(-0.22, 0.48, 0.85),
        tail_dirs=[R.yaw_pitch(0, -60), R.yaw_pitch(10, -55), R.yaw_pitch(25, -20), R.yaw_pitch(45, 0),
                   R.yaw_pitch(70, 0), R.yaw_pitch(90, 0), R.yaw_pitch(100, 0), R.yaw_pitch(120, 0)],
        cape=[10, 9], cape_w=12, cape_flare=(14.0, 8.0),
    )


def build(m, B, b):
    P.torso(m, B, b, LOOK)
    P.head(m, B, b, LOOK)
    P.arms(m, B, b, dict(LOOK, fist_l=True))
    P.legs(m, B, b, LOOK)
    P.tail(m, B, b, LOOK)
    P.cape_panels(m, B, b)
    P.kid_cape_knot(m, B, b)
    P.eyepatch(m, B, b, side="l")
    P.earring(m, B, b, side="r")
    P.hole(m, B, b)
    P.crown_tincan(m, B, b)
    P.staff_bone(m, B, b)


def build_staff(m, B, b, below):
    P.staff_fork(m, B, b, below=13, above=26)


def materials():
    mats = F.standard(LOOK)
    mats.update({
        "fur": F.fur(FUR, LIGHT, seed=601, back_dark=0.5, stripes=STRIPES, scars=0.05),
        "cape": F.cloth(CAPE, seed=602, ragged=1.5, hem_tags=("cape_1",), folds=1.0),
        "tin": F.tin(seed=603),
        "tin_dark": F.solid(F.TIN, 1.4, seed=604),
        "rust": F.rust(seed=605),
        "rust_tip": F.metal(F.TIN, seed=606),
        "patch": F.solid(F.PATCH, 1.8, seed=607, grain=0.5),
        "strap": F.solid(F.LEATHER, 1.8, seed=608, grain=0.6),
    })
    return mats
