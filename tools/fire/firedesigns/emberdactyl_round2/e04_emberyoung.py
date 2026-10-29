"""No. E4 Young Reborn: the Emberdactyl's young, in each of the three looks, at half the middle size's wingspan."""
from firekit import ptero

NUMBER = 4
ID = "emberyoung"
NAME = "Young Reborn"
SPAN = 22.5
BLURB = ("The Emberdactyl's young reborn form, the one that bursts out of the egg when you beat the adult: smaller, sleeker "
         "and brighter (the user's words), more agile and weaker (150 health). Here in each of the three looks at half "
         "the 45's wingspan (about 22 blocks across); it grows or shrinks with the size picked. Its egg is sized round "
         "it curled up (No. E5).")
VARIANTS = tuple(dict(look, mood=look["mood"], note=look["note"].replace(look["letter"] + ": ", look["letter"] + ": the young of ", 1),
                      density=ptero.texture_density(SPAN)) for look in ptero.LOOKS)
FREE = ()


def build(s, v):
    ptero.build(s, dict(v["design"], span=SPAN), young=True)


def materials(v):
    return ptero.materials(v["theme"], young=True)
