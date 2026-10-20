"""The meeting halls (the user, October 20, 2026: "each [village] should have this meeting hall igloo ... there
shouldn't be a bed in a meeting hall igloo ... a campfire in the center is a good idea"; "different variations of a
town hall igloo, that's fine, but it needs to be in such a way where you repeat that variation"). Two of them, each
a template like any other igloo: a round one and a long one.
"""
from villagekit.writer import banner

from .plan import Igloo, L, sym

# The Huntsman's sigil: a white skull on brown (his lodge's banners).
SIGIL = banner(("sku", "white"), ("bo", "white"), ("bts", "white"))
BANNER = ("brown_wall_banner[facing={in}]", SIGIL)

ROUND = Igloo(
    "round_hall", "The Round Hall", "hall",
    "Where the village meets: a fire in the middle under its vent, benches round it, the Huntsman's banners and the "
    "hall's trophy at the back, skulls on posts round the walls, lanterns either side of the way in.",
    [(0, 0, 8, 8.6, [(140, 3), (-140, 3), (180, 3)], False)],
    {1: L(6, {
        -6: (-2, 2, {**sym({2: "C"}), 0: "J"}),
        -5: (-4, 4, sym({3: "O"})),
        -4: (-5, 5, sym({4: "J"})),
        -3: (-5, 5, {-1: "e", 0: "e", 1: "e"}),
        -2: (-6, 6, sym({6: "O"})),
        -1: (-6, 6, sym({3: "i"})),
        0: (-6, 6, {**sym({6: "P", 3: "i"}), 0: "F"}),
        1: (-6, 6, sym({3: "i"})),
        2: (-6, 6, sym({6: "O"})),
        3: (-5, 5, sym({2: "J"})),
        4: (-5, 5, sym({4: "J"})),
        5: (-4, 4, sym({3: "O"})),
        6: (-2, 2, sym({2: "P"})),
        7: (0, 0, {}),
    }), 2: L(6, {
        -6: (None, None, {**sym({1: "%"}), 0: "s"}),
        -5: (None, None, sym({3: "4"})),
        -4: (None, None, sym({4: "s"})),
        0: (None, None, sym({6: "L"})),
        4: (None, None, sym({4: "s"})),
        5: (None, None, sym({3: "4"})),
        6: (None, None, sym({2: "L"})),
    })},
    legend={"%": BANNER},
)

LONG = Igloo(
    "long_hall", "The Long Hall", "hall",
    "The feast hall: a long table down the middle with benches either side, a fire under a vent in each end dome, the "
    "Huntsman's banners on the end walls, skulls on posts, lanterns over the table and either side of the way in.",
    [(0, 0, 6.5, 7.0, [], False), (-7, 0, 6.5, 7.0, [], False), (7, 0, 6.5, 7.0, [], False)],
    {1: L(12, {
        -5: (None, None, {**sym({8: "O", 7: "D", 6: "O"}), -1: "V", 0: "J", 1: "V"}),
        -4: (-9, 9, sym({9: "O", 3: "C"})),
        -3: (-10, 10, sym({8: "J"})),
        -2: (-11, 11, {}),
        -1: (-12, 12, {c: "e" for c in range(-3, 4)}),
        0: (-12, 12, {**sym({7: "F"}), **{c: "J" for c in range(-3, 4)}}),
        1: (-12, 12, {c: "e" for c in range(-3, 4)}),
        2: (-11, 11, {}),
        3: (-10, 10, sym({8: "J"})),
        4: (-9, 9, sym({9: "O"})),
        5: (None, None, {**sym({8: "O", 7: "H", 6: "O", 1: "L"}), 0: "."}),
        6: (0, 0, {}),
    }), 2: L(12, {
        -4: (None, None, sym({9: "4"})),
        -3: (None, None, sym({8: "s"})),
        0: (None, None, {**sym({11: "%"}), **{c: "p" for c in range(-3, 4)}}),
        3: (None, None, sym({8: "s"})),
        4: (None, None, sym({9: "4"})),
    })},
    legend={"%": BANNER},
    hangs=[(-3, 0, 4, "lantern[hanging=true]"), (3, 0, 4, "lantern[hanging=true]")],
)

DESIGNS = [ROUND, LONG]
