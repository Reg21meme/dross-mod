"""No. E1 Emberdactyl: round 3, the user's pick (round 2's B, the half-moon crest) at 33 blocks across its wings,
with the eyes the user picked from its eye studies (E1A2, the ember slit): E1A, the one in the game. E1B-E1D (October
31, 2026) are E1A with less lava on its body and horns, its wings unchanged, for the user to compare and pick from;
the user liked E1C, and E1C1-E1C4 (November 2, 2026) are E1C with its bill and jaw, half moon, horns and tail purple."""
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
         note="A: round 2's B as the user picked it, with their notes of October 27 built in (the one in the game)."),
    # The adult with less lava on its body (the user, October 31: "less lava spots on the adult version on the body;
    # the wings are fine, but on the body and horns less lava; give me a showcase with those versions"): A's wings,
    # fewer lava veins on its hide, bills, horns and half moon (firekit/emberdactyl.py: BODY_LAVA).
    dict(letter="B", mood="less lava", density=0.85, lava="less",
         note="B: A with half the lava spots on its body, neck, head, legs, tail, horns and half moon; its half "
              "moon's rim still bright; its wings as A's."),
    dict(letter="C", mood="a little lava", density=0.85, lava="little",
         note="C: A with a quarter of the lava spots on its body and horns, its half moon's rim half cooled over; its "
              "wings as A's."),
    dict(letter="D", mood="almost no lava", density=0.85, lava="none",
         note="D: a body of dark obsidian, only a few glowing cracks left on it and its horns, its half moon's rim "
              "cooled to a glint; its wings as A's."),
    # E1C with purple (the user, November 2, 2026: "I like E1C. Maybe can you give me variations of the bird but with
    # the beak and the horns with purple, and tail, just a variation of what we have here"): C as it is (its body,
    # wings, lava and its half moon's rim), its bill and jaw, half moon, horns and tail purple in four ways
    # (firekit/emberdactyl.py: PURPLE).
    dict(letter="C1", mood="glossy violet", density=0.85, lava="little", purple="glossy",
         note="C1: C with its bill and jaw, half moon, horns and tail glossy violet obsidian (like its egg, A11's "
              "violet), no lava on them; the rest of it as C."),
    dict(letter="C2", mood="violet fade", density=0.85, lava="little", purple="fade",
         note="C2: C with its bill and jaw, half moon, horns and tail deep purple at their tips, fading to C's "
              "near-black obsidian at their roots (C's few lava veins kept); the rest of it as C."),
    dict(letter="C3", mood="violet and lava", density=0.85, lava="little", purple="veins",
         note="C3: C with its bill and jaw, half moon, horns and tail violet obsidian with lava veins glowing through "
              "them (as many as A has there); the rest of it as C."),
    dict(letter="C4", mood="violet tips", density=0.85, lava="little", purple="tips",
         note="C4: C with only the ends purple: the last third of each horn, the front of its bill and jaw, the top "
              "of its half moon under the rim, its tail's end and spike, glowing faintly purple with crying "
              "obsidian's sparks; the rest of it as C."),
)
ONE_ROW = True          # its looks side by side in the gallery's front row (A, the one in the game, on the left)
# (the previews' index page links the side-by-side sheet of the four, drawn by tools/fire/lava_looks.py)
INDEX_EXTRA = ("<p><b><a href='lava_looks/lava_looks.png'>E1A to E1D side by side</a></b>: the adult with less and "
               "less lava on its body and horns, its wings the same (close-ups of its head and body, by day and at "
               "night; drawn by <code>tools/fire/lava_looks.py</code>).</p>"
               "<p><b><a href='purple_looks/purple_looks.png'>E1C and E1C1 to E1C4 side by side</a></b>: E1C with its "
               "bill and jaw, half moon, horns and tail purple in four ways (close-ups of its head and tail, by day "
               "and at night; drawn by <code>tools/fire/lava_looks.py</code>).</p>")
# Its ten eye studies (E1A1-E1A10, the user's second look of October 27) are archived in
# emberdactyl_round3/e01_eye_studies.py: the user picked E1A2, the ember slit ("the evil-looking ones"),
# now its own eyes (firekit/emberdactyl.py: ADULT_EYE).
FREE = ()


def build(s, v):
    E.build(s)


def materials(v):
    return E.materials(lava=v.get("lava"), purple=v.get("purple"))
