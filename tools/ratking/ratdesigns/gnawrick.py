"""
The Rat King: Gnawrick, build 1C (broad shoulders), the user's pick from round 2 (October 14, 2026). Round 1's No. 1
in his broad build: a wide chest and broad shoulders, thicker arms, his usual height. Round 2's seven builds are
archived in ratdesigns/round2/.

The look (round 1's No. 1, the sly schemer): sickly sewer grey-green fur, darker down the spine, cream belly and
muzzle; a small three-point gold crown with a green gem, cocked to one side; a long blood-red cape with a ragged,
moth-eaten hem and a gold clasp; a twisted dark-wood staff whose green claw clutches a glowing green orb. The classic
sly hunch, his free hand drumming its fingers; he creeps on tiptoe.

The user's notes, built in:
  round 1: he sleeps curled up on the ground and gets back up with the staff pick-up; his ears and the staff never
           pass through each other, nor the staff through him or the floor (ratkit/guard.py, checked by
           ratkit/clip.py); the burrow's dirt mound is where his head goes in.
  round 2: his rage pumps his free fist in the air (it used to swing through his head) and smashes the staff's head
           into the ground; his fist holds the staff the way a hand grips a stick (the shaft across the knuckles);
           the X's of his death only on the front of his face; the staff's head all green (the claw was bone white).
"""
from kit.paint import palette
from ratkit import fur as F
from ratkit import parts as P
from ratkit import rig as R

FUR = palette("#262c23", "#333b30", "#41493c", "#50594a", "#606b59", "#737f6b")
LIGHT = palette("#5f5d4b", "#78755f", "#918d74", "#a8a389", "#bdb79c", "#d0caae")
CAPE = palette("#33070a", "#4c0b0f", "#691115", "#87191c", "#a32424", "#bb3330")
LINING = palette("#26060a", "#3a0a0e", "#4f0e13", "#651418")

CLAW_GREEN = palette("#16300c", "#224a12", "#31661a", "#438424", "#58a02f")

LOOK = dict(fur=FUR, xmark="#151112", whisker_pal=palette("#121311", "#1d1f1c", "#2b2e29", "#3a3e37"))

# How he moves (ratkit/king_anims.py): his walk, his sleep, his idle gesture.
STYLE = dict(walk="sneak", sleep="curl", idle="drum", staff_clearance=1.0, plant_staff=False)

# His posture: the same in every build (so every build is the same height).
POSTURE = dict(
    hips_pitch=12.0, belly_pitch=11.0, chest_pitch=15.0, neck_pitch=12.0, head_down=8.0, head_tilt=-4.0,
    foot_heading=(0.22, -1.0), hip_y=20.5,
    arm_l=((-40.0, 8.0, -14.0), (-100.0, 10.0, 0.0), (0.0, 0.0, 10.0)),
    tail_dirs=[R.yaw_pitch(0, -72), R.yaw_pitch(4, -76), R.yaw_pitch(10, -60), R.yaw_pitch(18, -10),
               R.yaw_pitch(30, 0), R.yaw_pitch(48, 0), R.yaw_pitch(70, 0), R.yaw_pitch(95, 0)],
    cape=[15, 15, 13], cape_flare=(8.0, 4.0, 2.0),
)

# The staff arm: the fist held out to his right and forward at his belly, the forearm level, so the fist grips the
# shaft across its knuckles (rig.pose_rest turns the hand to it); the staff leans a little out, away from his right ear,
# so his ears clear it when he glances round.
ARM_R = ((-14.0, 0.0, 24.0), (-74.0, 0.0, -4.0), (0.0, 0.0, 0.0))
STAFF_DIR = (-0.13, 1.0, -0.12)
STAFF_ABOVE = 22          # the shaft above his fist, up to the claw (units)


class Build:
    """One build of Gnawrick: his look, the posture above, and its own body widths and depths."""

    STYLE = STYLE

    def __init__(self, number, label, id_, mood, blurb, hips, belly, chest, arm_w=3, fore_w=2, thigh=(5, 7),
                 shin_w=3, neck=(6, 6, 6), belly_z=-0.5, hump=None, cape_w=None, arm_r=ARM_R, staff_dir=STAFF_DIR):
        self.NUMBER, self.LABEL, self.ID, self.MOOD, self.BLURB = number, label, id_, mood, blurb
        self.NAME = "Gnawrick"
        self.hips, self.belly, self.chest = hips, belly, chest
        self.arm_w, self.fore_w, self.thigh, self.shin_w, self.neck = arm_w, fore_w, thigh, shin_w, neck
        self.belly_z, self.hump = belly_z, hump
        self.cape_w = cape_w if cape_w is not None else chest[0] + 7
        self.arm_r, self.staff_dir = arm_r, staff_dir

    def body(self):
        hip_x = round(self.hips[0] * 0.36, 1)
        return R.RatBody(
            hips_size=self.hips, belly_size=self.belly, chest_size=self.chest, belly_z=self.belly_z, hump=self.hump,
            shoulder_x=(self.chest[0] + 2) / 2.0, arm_w=self.arm_w, fore_w=self.fore_w,
            hip_x=hip_x, foot_target=(hip_x + 0.4, -4.0), thigh_w=self.thigh[0], thigh_d=self.thigh[1],
            shin_w=self.shin_w, neck_size=self.neck, cape_w=self.cape_w,
            arm_r=self.arm_r, staff_dir=self.staff_dir, grip_across=True, **POSTURE)

    def build(self, m, B, b):
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

    def build_staff(self, m, B, b, below):
        P.staff_claw_orb(m, B, b, below=below, above=STAFF_ABOVE, claw_mat="staff_claw")

    @staticmethod
    def materials():
        mats = F.standard(LOOK)
        mats.update({
            "fur": F.fur(FUR, LIGHT, seed=101, back_dark=0.7),
            "cape": F.cloth(CAPE, seed=102, lining=LINING, ragged=3.0, holes=0.035, hem_tags=("cape_2",)),
            "wood": F.wood(F.WOOD, seed=103),
            "bone": F.bone(seed=104),
            # The claw on the staff's head: poison green like the orb it holds (the user's note: all green, no white).
            "staff_claw": F.solid(CLAW_GREEN, 2.4, seed=107, grain=0.7, bev=0.6),
            "orb": F.gem(seed=105),
            "gem": F.gem(seed=106),
        })
        return mats


# The pick: 1C, broad shoulders. (Round 2's builds are in ratdesigns/round2/gnawrick_builds.py.)
BUILDS = [
    Build(1, "1C", "gnawrick", "broad shoulders", "The pick: a wide chest and broad shoulders, thicker arms.",
          hips=(12, 8, 10), belly=(14, 10, 11), chest=(13, 10, 8), arm_w=4, fore_w=3, neck=(7, 6, 6)),
]
