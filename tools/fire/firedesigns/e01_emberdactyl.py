"""No. E1 Emberdactyl: round 3, the user's pick (round 2's B, the half-moon crest) at 33 blocks across its wings,
with the eyes the user picked from its eye studies (E1A2, the ember slit): E1A, the one in the game. Since November 3,
2026 it wears the user's pick of round 4's looks, E1C3 (violet and lava): less lava on its body, its bill and jaw, half
moon, horns and tail violet with lava veins (the other seven looks are archived in emberdactyl_round4/)."""
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
    # The user's pick of round 4 (November 3, 2026: "E1 C3 is the best"), its only look now: E1C3, violet and lava, as
    # E1A. Its body and wings as E1C (a quarter of round 3's lava on its body and horns, its half moon's rim half cooled
    # over, the wings unchanged), its bill and jaw, half moon, horns and tail violet obsidian with lava veins glowing
    # through them (firekit/emberdactyl.py: BODY_LAVA "little", PURPLE "veins"). Round 4's eight looks (E1A round 3's,
    # E1B-E1D less lava, E1C1-E1C4 purple) are archived in emberdactyl_round4/.
    dict(letter="A", mood="violet and lava", density=0.85, lava="little", purple="veins",
         note="A: the user's pick of round 4 (E1C3): a little lava on its body, its bill and jaw, half moon, horns and "
              "tail violet obsidian with lava veins glowing through them; its wings as round 3's (the one in the game)."),
    # Round 5 (the user, November 5, 2026, after playing it: "its beak and horns are way too purple compared to the
    # body, it should be the same shade; too many lava spots on its beak and horns, and a little too much on the body;
    # keep the wings as is, and remove some of the spots from the tail"): A's wings, a few less lava on its body, a
    # fifth of the lava spots on its bill, jaw, half moon and horns and two fifths on its tail, in three shades
    # (firekit/emberdactyl.py: BODY_LAVA "fewer", PURPLE "body", "hint", "dusk").
    dict(letter="B", mood="body's shade", density=0.85, lava="fewer", purple="body",
         note="B: its bill and jaw, half moon, horns and tail the same dark obsidian as its body; far fewer lava spots on "
              "them, fewer on its tail, a few less on its body; its wings as A's."),
    dict(letter="C", mood="hint of violet", density=0.85, lava="fewer", purple="hint",
         note="C: as B, its bill and jaw, half moon, horns and tail its body's obsidian with a hint of violet."),
    dict(letter="D", mood="violet tips", density=0.85, lava="fewer", purple="dusk",
         note="D: as B, its bill and jaw, half moon, horns and tail its body's obsidian fading to a soft violet at "
              "their tips."),
)
ONE_ROW = True          # its looks side by side in the gallery's front row (A, the one in the game, on the left)
INDEX_EXTRA = ("<p>Round 4's eight looks of the adult (less lava, then purple; the user picked E1C3, now E1A): "
               "<a href='round4/index.html'>round4/</a>.</p>")
# Its ten eye studies (E1A1-E1A10, the user's second look of October 27) are archived in
# emberdactyl_round3/e01_eye_studies.py: the user picked E1A2, the ember slit ("the evil-looking ones"),
# now its own eyes (firekit/emberdactyl.py: ADULT_EYE).
FREE = ()


def build(s, v):
    E.build(s)


def materials(v):
    return E.materials(lava=v.get("lava"), purple=v.get("purple"))
