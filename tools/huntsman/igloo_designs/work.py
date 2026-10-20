"""The village's work igloos (the user, October 20, 2026: "give me designs for a storage igloo, a cook igloo, a barracks
igloo, a workshop igloo"). Their lights hang over where the work is done, or come from the fire.
"""
from .plan import Igloo, P

STORAGE = Igloo(
    "storage_igloo", "The Storehouse", "storage",
    "The village's winter store: barrels stacked two high down both walls, chests at the back, meat hung from the "
    "roof to keep, and blocks of packed ice to keep it all cold. Lanterns hang over the aisle.",
    [(0, 0, 6, 6.6, [], False)],
    {1: P("""
  -5 |         |
  -4 |  OC#CO  |
  -3 | H.....H |
  -2 |O.......O|
  -1 |O.......O|
  +0 |M.......M|
  +1 |O.......O|
  +2 |#.......#|
  +3 | N.....N |
  +4 |  .....  |
  +5 |    .    |
"""), 2: P("""
  -1 |O.......O|
  +0 |D.......D|
  +1 |O.......O|
""")},
    legend={"#": "packed_ice"},
    hangs=[(0, -2, 3, "lantern[hanging=true]"), (0, 2, 3, "lantern[hanging=true]"),
           (-2, 0, 3, "dross:meat_block[axis=y]"), (2, 0, 3, "dross:meat_block[axis=y]")],
)

COOK = Igloo(
    "cook_igloo", "The Cookhouse", "cook",
    "Where the village eats: a fire in the middle under its smoke vent, joints of meat hung round it to smoke, stew "
    "pots either side, smokers and chopping blocks at the back.",
    [(0, 0, 6, 6.6, [], False)],
    {1: P("""
  -5 |         |
  -4 |  OSOSO  |
  -3 | .J...J. |
  -2 |D.......K|
  -1 |.........|
  +0 |O..kFk..O|
  +1 |.........|
  +2 |M.......T|
  +3 | O.....O |
  +4 |  .....  |
  +5 |    .    |
"""), 2: P("""
  -4 |    R    |
  -3 |  p...p  |
""")},
    legend={"S": "smoker[facing={in},lit=false]"},
    hangs=[(-2, -1, 3, "dross:meat_block[axis=y]"), (2, -1, 3, "dross:meat_block[axis=y]"),
           (-2, 1, 3, "dross:meat_block[axis=y]"), (2, 1, 3, "dross:meat_block[axis=y]")],
)

BARRACKS = Igloo(
    "barracks_igloo", "The Barracks", "barracks",
    "Where the hunting party sleeps between hunts: twelve beds in two rows, a nightstand between each pair, footlockers "
    "at the ends and the spear rack in the alcove at the back. A lantern hangs in each of its three domes.",
    [(0, 0, 5, 5.6, [], False), (-6, 0, 5, 5.6, [], False), (6, 0, 5, 5.6, [], False)],
    {1: P("""
  -3 |      ODO   ICI   ODO      |
  -2 |     B3B3B.......B3B3B     |
  -1 |    Ob.b.b.......b.b.bO    |
  +0 |    C.................C    |
  +1 |    Ob.b.b.......b.b.bO    |
  +2 |     B3B3B.......B3B3B     |
  +3 |      ODO   ...   ODO      |
  +4 |             .             |
"""), 2: P("""
  -2 |      4 4         4 4      |
  +2 |      4 4         4 4      |
""")},
    legend={"3": "barrel[facing=up,open=false]"},
    beds={"B": ("brown", "b")},
    hangs=[(-6, 0, 3, "lantern[hanging=true]"), (0, 0, 3, "lantern[hanging=true]"), (6, 0, 3, "lantern[hanging=true]")],
)

WORKSHOP = Igloo(
    "workshop_igloo", "The Workshop", "workshop",
    "Where the bows, arrows and spears are made: the fletcher's, the grinder's and the loom along the back, a work "
    "table in the middle under its lamp, spears waiting for their heads along the walls.",
    [(0, 0, 6, 6.6, [(150, 1), (-150, 1)], False)],
    {1: P("""
  -5 |         |
  -4 |  XGYAO  |
  -3 | .C...C. |
  -2 |I.......I|
  -1 |I..JJJ..I|
  +0 |T.......D|
  +1 |O.......O|
  +2 |U.......U|
  +3 | .O...O. |
  +4 |  .....  |
  +5 |    .    |
"""), 2: P("""
  -1 |   ppp   |
  +0 |L........|
  +1 |        L|
""")},
    hangs=[(0, -1, 3, "lantern[hanging=true]")],
)

DESIGNS = [STORAGE, COOK, BARRACKS, WORKSHOP]
