"""No. E2 Emberdactyl 45: the user's pick (round 1's E3, obsidian blade) with wings 45 blocks across, in three looks."""
from firekit import ptero

NUMBER = 2
ID = "ember45"
NAME = "Emberdactyl 45"
SPAN = 45.0
BLURB = ("The Emberdactyl at Rodan's size (the user, October 27, 2026: \"at least 35 blocks, like a Rodan level\"; shown at "
         "35, 45 and 55 blocks across its wings to pick from): round 1's E3, obsidian blade, which the user picked, "
         "in three looks: A as it was, B with longer horns and a half-moon crest on its beak, C the same with its body "
         "like its volcano. It nests on its volcano guarding its egg, fights mostly in the air and lands for melee "
         "(300 health); beaten, its young bursts out of the egg (150).")
VARIANTS = ptero.adult_variants(SPAN)
FREE = ()


def build(s, v):
    ptero.build(s, dict(v["design"], span=SPAN))


def materials(v):
    return ptero.materials(v["theme"])
