"""Three more igloos of my own (the user, October 20, 2026: "come up with 2 or 3 more unique designs"): an ice-fishing
hut, a tannery and a shrine to the Snow Angel, the Ice Necromancer the hunters serve.
"""
from .plan import Igloo, L, P, sym

FISHING = Igloo(
    "fishing_hut", "The Ice-Fishing Hut", "extra",
    "A little hut over a hole cut through the ice: a stool by the water, the day's catch drying on the rack, a barrel "
    "of fish and a lantern to fish by in the long night.",
    [(0, 0, 4.5, 5.0)],
    {0: P("""
  -1 |   ~   |
  +0 |   ~   |
"""), 1: P("""
  -3 |   O   |
  -2 | D...  |
  -1 |  ..J  |
  +0 | O...C |
  +1 |  ...  |
  +2 |  ...  |
  +3 |   .   |
"""), 2: P("""
  -1 |     t |
  +0 | L     |
""")},
    legend={"~": "water", "t": "tripwire_hook[facing={in},attached=false,powered=false]"},
)

TANNERY = Igloo(
    "tannery_igloo", "The Tannery", "extra",
    "Where the hides of the hunt are cured: soaking vats along the back, hides stretched on the walls to dry, scraping "
    "benches in the middle under the lamps, barrels of salt and furs on the drying posts.",
    [(0, 0, 6, 6.6, [], False)],
    {1: P("""
  -5 |         |
  -4 |  KOKOK  |
  -3 | ....... |
  -2 |O.......O|
  -1 |.........|
  +0 |O..J.J..C|
  +1 |.........|
  +2 |H.......H|
  +3 | .P...P. |
  +4 |  .....  |
  +5 |    .    |
"""), 2: P("""
  -3 |  u...v  |
  -1 |h.......u|
  +0 |   p.p   |
  +1 |v.......h|
  +3 |  n...w  |
""")},
    legend={"u": "brown_wall_banner[facing={in}]", "v": "light_gray_wall_banner[facing={in}]",
            "h": "white_wall_banner[facing={in}]"},
    hangs=[(0, -1, 3, "lantern[hanging=true]"), (0, 2, 3, "lantern[hanging=true]")],
)

SHRINE = Igloo(
    "snow_angel_shrine", "The Snow Angel's Shrine", "extra",
    "The hunters' shrine to the Ice Necromancer, the Snow Angel they serve: its statue of snow and ice, wings spread "
    "and a blue light for a halo, an offering on the altar before it, cushions to kneel on, candles on bones.",
    [(0, 0, 6, 8.5, [(90, 1), (-90, 1)])],
    {1: L(4, {
        -4: (-2, 2, sym({1: "V"})),
        -3: (-3, 3, sym({3: "P"})),
        -2: (-4, 4, {-1: "*", 0: "*", 1: "*"}),
        -1: (-4, 4, {0: "J"}),
        0: (-4, 4, sym({1: "W"})),
        1: (-4, 4, sym({1: "W"})),
        2: (-4, 4, sym({3: "V"})),
        3: (-3, 3, {}),
        4: (-2, 2, {}),
        5: (0, 0, {}),
    }), 2: L(4, {
        -4: (None, None, sym({1: "3"})),
        -3: (None, None, sym({3: "l"})),
        -2: (None, None, {0: "*"}),
        -1: (None, None, {0: "R"}),
        2: (None, None, sym({3: "4"})),
    }), 3: L(4, {
        -2: (None, None, {-1: "&", 0: "#", 1: "&"}),
    }), 4: L(4, {
        -2: (None, None, {-2: "&", -1: "&", 0: "*", 1: "&", 2: "&"}),
    }), 5: L(4, {
        -2: (None, None, {-2: "&", 0: "l", 2: "&"}),
    })},
    legend={"*": "snow_block", "#": "packed_ice", "&": "blue_ice"},
)

DESIGNS = [FISHING, TANNERY, SHRINE]
