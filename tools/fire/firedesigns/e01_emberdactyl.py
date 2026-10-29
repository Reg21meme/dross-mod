"""No. E1 Emberdactyl: round 3, the user's pick (round 2's B, the half-moon crest) at 33 blocks across its wings,
with the eyes the user picked from its eye studies (E1A2, the ember slit)."""
from firekit import emberdactyl as E

NUMBER = 1
ID = "emberdactyl"
NAME = "Emberdactyl"
BLURB = ("The Fire Necromancer's volcanic pterosaur, round 3 (the user's look in-game, October 27, 2026): round 2's B, "
         "\"I like the 2nd design\", at 33 blocks across its wings (\"a wingspan of 33 best fits\"): its half moon "
         "standing on its bill all along it, its two blade horns, a slender body (no \"bear belly\"), rebuilt wings (a "
         "thin scalloped membrane with ribs across it, thorns swept back along the forearm, a hooked thumb claw) and "
         "less lava on its scales. It nests on its volcano guarding its egg, fights mostly in the air and lands for "
         "melee (300 health); beaten, its young bursts out of the egg (150).")
VARIANTS = (
    dict(letter="A", mood="half-moon crest", density=0.85,
         note="A: round 2's B as the user picked it, with their notes of October 27 built in."),
)
# Its ten eye studies (E1A1-E1A10, the user's second look of October 27) are archived in
# emberdactyl_round3/e01_eye_studies.py: the user picked E1A2, the ember slit ("the evil-looking ones"),
# now its own eyes (firekit/emberdactyl.py: ADULT_EYE).
FREE = ()


def build(s, v):
    E.build(s)


def materials(v):
    return E.materials()
