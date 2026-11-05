"""No. E1 Emberdactyl: round 3, the user's pick (round 2's B, the half-moon crest) at 33 blocks across its wings,
with the eyes the user picked from its eye studies (E1A2, the ember slit): E1A, the one in the game. From November 3,
2026 it wore the user's pick of round 4's looks, E1C3 (violet and lava; the other seven are archived in
emberdactyl_round4/); since November 5 the user's pick of round 5's, E1B (the body's shade): its bill and jaw, half
moon, horns and tail its body's dark obsidian with few lava spots (the other three are archived in
emberdactyl_round5/)."""
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
    # The user's pick of round 5 (November 5, 2026, after playing 0.3.0: "e1b is the best"), its only look now: E1B,
    # the body's shade, as E1A. Its wings as round 3's; a few less lava on its body than round 4's pick (E1C3); its
    # bill and jaw, half moon, horns and tail its body's dark obsidian with a fifth of E1C3's lava spots on them and two
    # fifths on its tail (firekit/emberdactyl.py: BODY_LAVA "fewer", PURPLE "body"). Round 5's four looks (E1A round
    # 4's pick, E1B-E1D three shades) are archived in emberdactyl_round5/, round 4's eight in emberdactyl_round4/.
    dict(letter="A", mood="body's shade", density=0.85, lava="fewer", purple="body",
         note="A: the user's pick of round 5 (E1B): its bill and jaw, half moon, horns and tail the same dark obsidian "
              "as its body, with few lava spots on them; a little lava on its body; its wings as round 3's (the one in "
              "the game)."),
)
ONE_ROW = True          # its looks side by side in the gallery's front row (A, the one in the game, on the left)
INDEX_EXTRA = ("<p>Round 5's four looks of the adult (its bill, horns and tail its body's shade; the user picked E1B, now "
               "E1A): <a href='round5/index.html'>round5/</a>. Round 4's eight (less lava, then purple; the user picked "
               "E1C3): <a href='round4/index.html'>round4/</a>.</p>")
# Its ten eye studies (E1A1-E1A10, the user's second look of October 27) are archived in
# emberdactyl_round3/e01_eye_studies.py: the user picked E1A2, the ember slit ("the evil-looking ones"),
# now its own eyes (firekit/emberdactyl.py: ADULT_EYE).
FREE = ()


def build(s, v):
    E.build(s)


def materials(v):
    return E.materials(lava=v.get("lava"), purple=v.get("purple"))
