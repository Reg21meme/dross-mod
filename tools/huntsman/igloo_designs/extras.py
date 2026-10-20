"""Three more igloos of my own (the user, October 20, 2026: "come up with 2 or 3 more unique designs"): an ice-fishing
hut, a tannery and a shrine to the Snow Angel, the Ice Necromancer the hunters serve. The user liked all three ("I like
all 3, you can add them as well") and asked for the shrine to be three igloos joined, with the most in it, standing on
its own in a village "to show their importance".
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
    "snow_angel_shrine", "The Snow Angel's Shrine", "shrine",
    "The hunters' shrine to the Ice Necromancer, the Snow Angel they serve, three domes joined: in the great middle "
    "one its statue of snow and ice, wings spread and a blue light for a halo, the altar with its offering and "
    "candles before it, cushions to kneel on either side of the aisle; on the west the Hall of the Fallen, the books "
    "and skulls of the hunters who never came back; on the east the Offerings Chapel, barrels and chests of what the "
    "hunt gave, meat laid out, candles burning on bones. It stands alone in a village, joined to no other igloo.",
    [(0, 0, 8, 10.5, [(150, 3), (-150, 3)]), (-11, -1, 5.5, 6.2, [(180, 1)], False),
     (11, -1, 5.5, 6.2, [(180, 1)], False)],
    {1: L(15, {
        -6: (-2, 2, {}),
        -5: ([(-11, -11), (-4, 4), (11, 11)], {-11: "E", -3: "P", -1: "*", 0: "*", 1: "*", 3: "P", 11: "O"}),
        -4: ([(-13, -9), (-5, 5), (9, 13)], {-13: "E", -12: "E", -11: "E", -10: "E", -9: "E", -4: "J", 4: "J",
                                              9: "O", 10: "R", 11: "C", 12: "M", 13: "O"}),
        -3: (-14, 14, {-14: "E", -11: "Q", -4: "V", -1: "e", 0: "e", 1: "e", 4: "V", 11: "k", 14: "O"}),
        -2: (-14, 14, {-14: "E", 14: "H"}),
        -1: (-15, 15, {-15: "E", 1: "W", -2: "W", 0: "c", -1: "c", 15: "O"}),
        0: (-14, 14, {-14: "E", 1: "W", -2: "W", 0: "c", -1: "c", 14: "D"}),
        1: (-14, 14, {-14: "C", 0: "c", -1: "c", 14: "O"}),
        2: (-13, 13, {-13: "J", 0: "c", -1: "c", 13: "M"}),
        3: ([(-11, -11), (-5, 5), (11, 11)], {-11: "V", -4: "V", 0: "c", -1: "c", 4: "V", 11: "V"}),
        4: (-5, 5, {0: "c", -1: "c"}),
        5: (-4, 4, {0: "c", -1: "c"}),
        6: (-2, 2, {1: "P", -2: "P", 0: "c", -1: "c"}),
        7: (-1, 0, {}),
    }), 2: L(15, {
        -5: (None, None, {-3: "l", 0: "*", 3: "l"}),
        -4: (None, None, {-13: "E", -12: "1", -11: "E", -10: "2", -9: "E", -4: "s", 4: "s", 9: "3", 13: "3"}),
        -3: (None, None, {-14: "E", -4: "4", -1: "3", 0: "R", 1: "3", 4: "4", 14: "2"}),
        -2: (None, None, {-14: "E"}),
        0: (None, None, {-14: "E"}),
        1: (None, None, {14: "3"}),
        2: (None, None, {-13: "s"}),
        3: (None, None, {-4: "4", 4: "4"}),
        6: (None, None, {1: "l", -2: "l"}),
    }), 3: L(15, {
        -5: (None, None, {-1: "&", 0: "#", 1: "&"}),
    }), 4: L(15, {
        -5: (None, None, {-2: "&", -1: "&", 0: "*", 1: "&", 2: "&"}),
    }), 5: L(15, {
        -5: (None, None, {-2: "&", 0: "l", 2: "&"}),
    })},
    legend={"*": "snow_block", "#": "packed_ice", "&": "blue_ice", "c": "light_blue_carpet"},
    hangs=[(-11, -1, 4, "lantern[hanging=true]"), (11, -1, 4, "lantern[hanging=true]"),
           (-5, -1, 5, "lantern[hanging=true]"), (5, -1, 5, "lantern[hanging=true]")],
    tunnel_extra=1,                 # the user: "shrine doorway make it 2x2"
)

DESIGNS = [FISHING, TANNERY, SHRINE]
