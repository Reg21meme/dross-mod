"""
No. 2, Lord Squeakley, the pompous royal.

Sleek dark slate-grey fur with a silver muzzle and chest, and a gold monocle. A tall royal crown: eight points with
pearls, a red velvet cap and a green orb. A floor-length royal red cape with spotted ermine trim and a tall stiff
collar. A gold scepter topped with a big green gem. Upright, chest puffed, nose in the air, fist on his hip; he struts
stiffly and sleeps curled up in his cape.
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import parts as P
from ratkit import rig as R

NUMBER = 2
LABEL = "2"
ID = "squeakley"
NAME = "Lord Squeakley"
MOOD = "pompous royal"
BLURB = "Dark slate grey, silver muzzle, monocle, tall royal crown, ermine-trimmed cape, jewelled scepter. Puffed up; struts."

FUR = palette("#1d2025", "#272b31", "#32373e", "#3e444c", "#4c535c", "#5e666f")
LIGHT = palette("#646a71", "#7e858c", "#999fa5", "#b2b7bc", "#c8ccd0", "#dcdfe1")
CAPE = palette("#430610", "#600a16", "#7f101f", "#9e182a", "#ba2536", "#cf3846")
LINING = palette("#2e050b", "#430812", "#5a0c18", "#70121f")

LOOK = dict(fur=FUR, xmark="#e8e6e0", whisker_pal=palette("#9fa3a8", "#b7bbbf", "#cdd0d3", "#e1e3e5"))

STYLE = dict(walk="strut", sleep="curl", idle="monocle", staff_clearance=7.0, plant_staff=False)


def body():
    return R.RatBody(
        hips_pitch=3.0, belly_pitch=0.0, chest_pitch=-7.0, neck_pitch=8.0, head_down=-12.0,
        foot_target=(5.2, -1.5), foot_heading=(0.38, -1.0), hip_y=21.0,
        hand_l_target=(8.5, 23.0, 0.5),
        arm_r=((-34.0, 0.0, 10.0), (-62.0, 0.0, -6.0), (0.0, 0.0, -4.0)),
        staff_dir=(-0.12, 1.0, -0.05),
        tail_dirs=[R.yaw_pitch(0, -70), R.yaw_pitch(-4, -72), R.yaw_pitch(-10, -40), R.yaw_pitch(-20, -5),
                   R.yaw_pitch(-35, 0), R.yaw_pitch(-55, 0), R.yaw_pitch(-80, 0), R.yaw_pitch(-110, 0)],
        cape=[15, 15, 15], cape_w=18, cape_flare=(10.0, 5.0, 2.0),
    )


def build(m, B, b):
    P.torso(m, B, b, LOOK)
    P.head(m, B, b, LOOK)
    P.arms(m, B, b, dict(LOOK, fist_l=True))
    P.legs(m, B, b, LOOK)
    P.tail(m, B, b, LOOK)
    P.cape_panels(m, B, b)
    P.mantle(m, B, b, height=2, extra=1)
    P.ermine_collar(m, B, b)
    B["chest"].add((-2.0, b.chest_h - 3.0, -b.chest_size[2] / 2.0 - 1.0), (4, 2, 1), "gold", tag="clasp")
    P.monocle(m, B, b, side="l")
    P.hole(m, B, b)
    P.crown_royal(m, B, b)
    P.staff_bone(m, B, b)


def build_staff(m, B, b, below):
    P.staff_scepter(m, B, b, below=below, above=24)


def materials():
    mats = F.standard(LOOK)
    mats.update({
        "fur": F.fur(FUR, LIGHT, seed=201, back_dark=0.5, light_width=0.7),
        "cape": F.cloth(CAPE, seed=202, lining=LINING, trim="ermine", trim_width=2.0, hem_tags=("cape_2",),
                        pattern="diamonds", folds=0.8),
        "ermine": F.ermine(seed=203),
        "velvet": F.solid(F.VELVET, 2.6, seed=204, grain=0.8),
        "gem": F.gem(seed=205),
        "orb": F.gem(seed=206),
        "pearl": F.solid(F.PEARL, 3.0, seed=207, bev=1.0),
        "ruby": F.gem(F.RUBY, seed=208, glow=False),
        "chain_gold": F.chain(F.GOLD, seed=209),
    })
    return mats
