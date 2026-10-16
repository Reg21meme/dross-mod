"""
No. 5, Old King Knot, the ancient elder.

Grizzled, shaggy grey fur with mossy green tinges, bushy white brows, long white whiskers and a wispy beard. An
oversized heavy crown with a broken point, slipping down over his right ear (which it squashes flat). A heavy faded
red cape with a fur collar, dragging behind him in a train. A crooked cane topped with a "rat king": a knot of tied rat
tails. Stooped, leaning on his cane with both hands; he shuffles, planting the cane with every step, and
sleeps slumped on it, nodding off.
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import parts as P
from ratkit import rig as R

NUMBER = 5
LABEL = "5"
ID = "knot"
NAME = "Old King Knot"
MOOD = "ancient elder"
BLURB = "Grizzled mossy grey, white brows and beard, a slipping oversized crown, a dragging cape, a cane topped with a rat-tail knot."

FUR = palette("#2b2d29", "#393c36", "#484b44", "#585c54", "#6b6f66", "#80847a")
LIGHT = palette("#68665b", "#807e72", "#99968a", "#afac9f", "#c3c0b3")
MOSS = palette("#3b4629", "#4a5833", "#596a3d", "#6a7d48", "#7c9154")
GRIZZLE = palette("#8e8d86", "#a9a8a1", "#c2c1ba", "#d8d7d0", "#e9e8e2")
CAPE = palette("#3a1817", "#512321", "#682f2c", "#7e3c37", "#924a43", "#a55b52")
LINING = palette("#2a1514", "#3a1d1b", "#4b2623", "#5c302c")
COLLAR = palette("#3d3b36", "#504d47", "#65625a", "#7b776e", "#918d83")

LOOK = dict(fur=FUR, xmark="#151112", whisker_len=8, whisker_mat="white_hair",
            ear_r_rot=(-10.0, 24.0, -78.0), ear_l_rot=(-8.0, -26.0, 40.0))

STYLE = dict(walk="shuffle", sleep="slump", idle="cane", staff_clearance=0.0, plant_staff=True)


def body():
    return R.RatBody(
        hips_pitch=16.0, belly_pitch=16.0, chest_pitch=18.0, neck_pitch=6.0, head_down=-2.0, head_tilt=-6.0,
        foot_target=(4.6, -2.5), foot_heading=(0.3, -1.0), hip_y=18.5,
        hand_l_target=(-3.5, 27.0, -13.0),
        arm_r=((-48.0, 0.0, 16.0), (-34.0, 0.0, -4.0), (0.0, 0.0, -10.0)),
        staff_dir=(-0.1, 1.0, -0.3),
        tail_dirs=[R.yaw_pitch(0, -60), R.yaw_pitch(0, -60), R.yaw_pitch(5, -20), R.yaw_pitch(10, 0),
                   R.yaw_pitch(18, 0), R.yaw_pitch(30, 0), R.yaw_pitch(45, 0), R.yaw_pitch(65, 0)],
        cape=[13, 13, 13, 12], cape_w=17, cape_flare=(8.0, 4.0, 2.0, 0.0),
    )


def build(m, B, b):
    P.torso(m, B, b, LOOK)
    P.head(m, B, b, LOOK)
    P.arms(m, B, b, LOOK)
    P.legs(m, B, b, LOOK)
    P.tail(m, B, b, LOOK)
    P.cape_panels(m, B, b)
    P.mantle(m, B, b, height=2, extra=1)
    P.fur_collar(m, B, b)
    P.brows_beard(m, B, b)
    P.shaggy_tufts(m, B, b)
    P.hole(m, B, b)
    P.crown_heavy(m, B, b)
    P.staff_bone(m, B, b)


def build_staff(m, B, b, below):
    P.staff_knot(m, B, b, below=below, above=5)


def materials():
    mats = F.standard(LOOK)
    mats.update({
        "fur": F.fur(FUR, LIGHT, seed=501, back_dark=0.6, grizzle=0.05, grizzle_pal=GRIZZLE, moss=MOSS,
                     moss_share=0.32, strand=2.6),
        "cape": F.cloth(CAPE, seed=502, lining=LINING, ragged=2.2, holes=0.02, hem_tags=("cape_3",), folds=1.2),
        "fur_collar": F.fur(COLLAR, None, seed=503, strand=3.0, grizzle=0.08, grizzle_pal=GRIZZLE),
        "cane": F.wood(F.CANE, seed=504, knots=0.2),
        "knot": F.knot(seed=505),
        "gem": F.gem(seed=506),
        "ruby": F.gem(F.RUBY, seed=507, glow=False),
    })
    return mats
