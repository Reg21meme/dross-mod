"""
No. 4, the Pale Prophet, the plague priest.

Albino: white fur, pink skin, pale violet eyes, and a necklace of teeth. A bone band crowned with dripping candles
that burn with green plague fire. A short deep-red mantle with its hood thrown back. A tall staff of stacked bones; an
iron censer glowing green swings from a hook at its top. Tall, thin and solemn, his free hand raised in blessing; he
glides slowly, planting his staff every other step, and sleeps slumped on his staff as if praying.
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import parts as P
from ratkit import rig as R

NUMBER = 4
LABEL = "4"
ID = "prophet"
NAME = "Pale Prophet"
MOOD = "plague priest"
BLURB = "Albino, violet eyes, a crown of green-burning candles, a hooded mantle, a bone staff with a swinging censer."

FUR = palette("#a19c90", "#b7b2a6", "#cac5b9", "#dad6cb", "#e7e4da", "#f2f0e9")
LIGHT = palette("#c4b9b4", "#d5cbc6", "#e3dbd6", "#eee8e4", "#f8f3f0")
CAPE = palette("#360509", "#4f080f", "#6b0d16", "#86141f", "#9e1f29", "#b22d34")
LINING = palette("#1f0306", "#2e050a", "#40080e", "#520c13")
EYES = palette("#3a2944", "#56405f", "#735a7d", "#90779a")

LOOK = dict(fur=FUR, xmark="#1a1214", skin=F.SKIN_PALE, ear_inner=F.SKIN_PALE, eye_pal=EYES, tail_pal=F.SKIN_PALE,
            whisker_pal=palette("#c9c3b6", "#dad5ca", "#e8e4db", "#f4f1ea"))

STYLE = dict(walk="glide", sleep="slump", idle="bless", staff_clearance=0.0, plant_staff=True)


def body():
    return R.RatBody(
        hips_pitch=3.0, belly_pitch=3.0, chest_pitch=3.0, neck_pitch=10.0, head_down=10.0,
        foot_target=(4.0, -1.0), foot_heading=(0.12, -1.0), hip_y=24.0,
        belly_size=(11, 10, 9), chest_size=(9, 11, 8), hips_size=(10, 8, 9), chest_h=11.0,
        hand_l_target=(5.0, 41.0, -9.0),
        arm_r=((-24.0, 0.0, 22.0), (-40.0, 0.0, -4.0), (0.0, 0.0, -10.0)),
        staff_dir=(-0.06, 1.0, -0.08),
        tail_dirs=[R.yaw_pitch(0, -78), R.yaw_pitch(0, -80), R.yaw_pitch(0, -40), R.yaw_pitch(-10, 0),
                   R.yaw_pitch(-30, 0), R.yaw_pitch(-60, 0), R.yaw_pitch(-100, 0), R.yaw_pitch(-140, 0)],
        cape=[12, 10], cape_w=16, cape_flare=(8.0, 4.0),
    )


def build(m, B, b):
    P.torso(m, B, b, LOOK)
    P.head(m, B, b, LOOK)
    P.arms(m, B, b, LOOK)
    P.legs(m, B, b, LOOK)
    P.tail(m, B, b, LOOK)
    P.cape_panels(m, B, b)
    P.mantle(m, B, b, height=3, extra=1)
    P.hood(m, B, b)
    P.teeth_necklace(m, B, b)
    P.hole(m, B, b)
    P.crown_candles(m, B, b)
    P.staff_bone(m, B, b)


def build_staff(m, B, b, below):
    P.staff_censer(m, B, b, below=below, above=28)


def materials():
    mats = F.standard(LOOK)
    mats.update({
        "fur": F.fur(FUR, LIGHT, seed=401, back_dark=0.35, strand=2.4),
        "cape": F.cloth(CAPE, seed=402, lining=LINING, ragged=2.0, hem_tags=("cape_1",), folds=1.1),
        "bone": F.bone(seed=403),
        "bone_knob": F.bone(F.BONE, seed=404),
        "wax": F.wax(seed=405),
        "flame": F.flame(seed=406),
        "iron": F.metal(F.IRON, seed=407, shine=0.6),
        "chain": F.chain(seed=408),
        "censer": F.censer(seed=409),
        "censer_glow": F.gem(seed=410),
    })
    return mats
