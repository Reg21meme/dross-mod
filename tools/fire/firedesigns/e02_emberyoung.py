"""No. E2 Young Reborn: round 3, the Emberdactyl's young (round 2's size), with more lava on it."""
from firekit import emberdactyl as E

NUMBER = 2
ID = "emberyoung"
NAME = "Young Reborn"
BLURB = ("The Emberdactyl's young reborn form, the one that bursts out of the egg when you beat the adult: round 2's "
         "size (\"the current baby sizes also work\", about 22 blocks across its wings), the adult's look, sleeker, its "
         "head bigger, \"a little more lava\" on it: its hide, horns and wings veined with glowing lava. Weaker (150 "
         "health), but in its fight it hits as hard as the adult and never lets up.")
VARIANTS = (
    dict(letter="A", mood="young reborn", note="A: the adult's look, young: sleeker, hotter, more lava on it."),
)
FREE = ()


def build(s, v):
    E.build(s, young=True)


def materials(v):
    return E.materials(young=True)
