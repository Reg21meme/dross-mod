"""The Emberdactyl's adult looks of round 5, archived (November 5, 2026). After playing 0.3.0 the user asked for its bill,
horns and tail to be its body's shade, with far fewer lava spots on them, fewer on its tail and a few less on its body,
its wings as they were; of the three shades they picked E1B ("e1b is the best"): it is now the only adult, E1A
(firedesigns/e01_emberdactyl.py: lava "fewer", purple "body"). These were the gallery's four looks (their previews:
boss-previews/cinder_wastes/emberdactyl/round5/). Nothing imports this file."""

VARIANTS = (
    # Round 4's pick, E1C3, as the game wore it from November 3 to 5.
    dict(letter="A", mood="violet and lava", density=0.85, lava="little", purple="veins",
         note="A: the user's pick of round 4 (E1C3): a little lava on its body, its bill and jaw, half moon, horns and "
              "tail violet obsidian with lava veins glowing through them; its wings as round 3's."),
    # Round 5 (the user, November 5, 2026, after playing it: "its beak and horns are way too purple compared to the
    # body, it should be the same shade; too many lava spots on its beak and horns, and a little too much on the body;
    # keep the wings as is, and remove some of the spots from the tail"): A's wings, a few less lava on its body, a
    # fifth of the lava spots on its bill, jaw, half moon and horns and two fifths on its tail, in three shades
    # (firekit/emberdactyl.py: BODY_LAVA "fewer", PURPLE "body", "hint", "dusk").
    dict(letter="B", mood="body's shade", density=0.85, lava="fewer", purple="body",
         note="B: its bill and jaw, half moon, horns and tail the same dark obsidian as its body; far fewer lava spots on "
              "them, fewer on its tail, a few less on its body; its wings as A's. (The user's pick.)"),
    dict(letter="C", mood="hint of violet", density=0.85, lava="fewer", purple="hint",
         note="C: as B, its bill and jaw, half moon, horns and tail its body's obsidian with a hint of violet."),
    dict(letter="D", mood="violet tips", density=0.85, lava="fewer", purple="dusk",
         note="D: as B, its bill and jaw, half moon, horns and tail its body's obsidian fading to a soft violet at "
              "their tips."),
)
