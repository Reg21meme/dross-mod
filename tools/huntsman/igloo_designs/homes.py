"""The six home igloos, each with its own story (the user, October 20, 2026: "give me like four to six different home
igloos that have different lore behind them"). Every one is a dome 5 blocks out (a 7 x 7 floor, room to stand
everywhere) or 6 (9 x 9), its plain doorway at the south; plans are drawn north (the back) to south (the doorway).
Beds stand in pairs either side of a nightstand, or one to a wall, never off to one side of a double; every light
stands where someone would want it (on the nightstand, on the table, over the lectern).
"""
from .plan import Igloo, P


def home(windows):
    return (0, 0, 5, 5.6, windows)


def big(windows):
    return (0, 0, 6, 6.6, windows)


TRAPPER = Igloo(
    "trappers_hut", "The Trapper's Hut", "home",
    "A loner who sets snares for hares and foxes out on the ice and comes back only to sleep and skin his catch.",
    [home([(75, 1), (-75, 1)])],
    {1: P("""
  -4 |       |
  -3 |  OCO  |
  -2 | B...H |
  -1 |nb...gX|
  +0 |nn.w..G|
  +1 |...ww..|
  +2 | D...O |
  +3 |  ...  |
  +4 |   .   |
"""), 2: P("""
  -4 |       |
  -3 |  L.2  |
  -2 | ....s |
  -1 |t......|
  +0 |.......|
  +1 |......t|
  +2 | ..... |
  +3 |  ...  |
  +4 |   .   |
""")},
    legend={"t": "tripwire_hook[facing={in},attached=false,powered=false]"},
    beds={"B": ("brown", "b")},
)

FAMILY = Igloo(
    "family_igloo", "The Family Igloo", "home",
    "A hunter, his wife and their two children: the parents' beds at the back, the children's along the wall by "
    "their shared chest, and a warm kitchen corner.",
    [big([(70, 1), (110, 1)])],
    {1: P("""
  -5 |         |
  -4 |  .BOB.  |
  -3 | ..b.b.. |
  -2 |D.......K|
  -1 |Ab.www..O|
  +0 |C..wWw..S|
  +1 |Ab.www..T|
  +2 |O.......k|
  +3 | .P...P. |
  +4 |  .....  |
  +5 |    .    |
"""), 2: P("""
  -4 |    L    |
  +1 |        L|
  +3 |  n...n  |
""")},
    legend={"S": "smoker[facing={in},lit=false]"},
    beds={"B": ("white", "b"), "A": ("light_blue", "b")},
)

ELDER = Igloo(
    "elders_igloo", "The Elder's Igloo", "home",
    "The oldest of the hunters, keeper of the stories: his books and the skulls of hunts long past, and a blue lamp "
    "over the lectern he reads them from.",
    [home([(110, 1), (-110, 1)])],
    {1: P("""
  -4 |       |
  -3 |  EEE  |
  -2 | B.Q.J |
  -1 |nb....P|
  +0 |nn.w...|
  +1 |...w..O|
  +2 | C...O |
  +3 |  ...  |
  +4 |   .   |
"""), 2: P("""
  -3 |  2.3  |
  -2 |     s |
  -1 |      s|
  +2 |     L |
""")},
    hangs=[(0, -1, 3, "soul_lantern[hanging=true]")],
    beds={"B": ("gray", "b")},
)

TWINS = Igloo(
    "twin_hunters", "The Twin Hunters", "home",
    "Two brothers who hunt as one: every bed, chest and spear of theirs has its twin across the room.",
    [big([(180, 1), (35, 1), (-35, 1)])],
    {1: P("""
  -5 |         |
  -4 |  OXTXO  |
  -3 | I.....I |
  -2 |.........|
  -1 |O.......O|
  +0 |Bb.www.bB|
  +1 |C..wWw..C|
  +2 |D.......D|
  +3 | ..P.P.. |
  +4 |  U...U  |
  +5 |    .    |
"""), 2: P("""
  -1 |L.......L|
  +3 |   n.n   |
""")},
    hangs=[(0, 3, 3, "lantern[hanging=true]")],
    beds={"B": ("brown", "b")},
)

SCOUT = Igloo(
    "scouts_igloo", "The Scout's Igloo", "home",
    "The village's lookout: a wide window of clear ice at the back to watch the plain from, his maps, and his bed "
    "out of the draught.",
    [home([(180, 1), (180, 2), (155, 1), (-155, 1)])],
    {1: P("""
  -4 |       |
  -3 |  OZO  |
  -2 | B...C |
  -1 |nb.....|
  +0 |.......|
  +1 |J..w..O|
  +2 | D...O |
  +3 |  ...  |
  +4 |   .   |
"""), 2: P("""
  -3 |  1.1  |
  +1 |      L|
""")},
    beds={"B": ("light_gray", "b")},
)

APPRENTICE = Igloo(
    "apprentice_igloo", "The Apprentice's Igloo", "home",
    "A young hunter still learning the bow: targets against a bale of hay at the back to practise on, his "
    "fletching table, and a small bed.",
    [home([(-75, 1), (-150, 1)])],
    {1: P("""
  -4 |       |
  -3 |  UHU  |
  -2 | ..... |
  -1 |J.....X|
  +0 |Bb...wG|
  +1 |C.....O|
  +2 | .ww.. |
  +3 |  ...  |
  +4 |   .   |
"""), 2: P("""
  -1 |L......|
  +1 |      2|
""")},
    beds={"B": ("white", "b")},
)

HOMES = [TRAPPER, FAMILY, ELDER, TWINS, SCOUT, APPRENTICE]
