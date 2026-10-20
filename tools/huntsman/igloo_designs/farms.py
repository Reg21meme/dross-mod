"""The hunters' farms (the user, October 20, 2026: "make it a custom plant for the biome, think of a couple ... a berry
farm outside or then another crop with its own igloo or two"): Rimefall's own crops (boss/huntsman/village/HunterCrops:
Frostroot, Snow Lichen, the two-block Rimeberry Bush), at mixed ages so the rows look grown and not planted yesterday.

Under a roof a crop grows only by its lamps' light (9 or more), and every bed of farmland lies within 4 blocks of water
(the game's rule for wet farmland); the water stays open inside, where the roof keeps it from freezing.
"""
import random

from voxel import AIR

from villagekit import props
from villagekit.local import Frame

from .plan import Custom, Igloo, L

FARMLAND = "farmland[moisture=7]"


def ages(seed, weights):
    """A crop's age chosen per cell (the same every build): `weights` {age: weight}."""
    choices = [a for a, w in weights.items() for _ in range(w)]

    def pick(dx, dz):
        return random.Random(seed * 1000003 + dx * 7919 + dz * 104729).choice(choices)
    return pick


ROOT_AGE = ages(18, {7: 6, 6: 3, 5: 2, 4: 2, 3: 1, 2: 1})
LICHEN_AGE = ages(19, {7: 5, 6: 3, 5: 2, 4: 2, 3: 1})
BERRY_AGE = ages(20, {3: 6, 2: 4, 1: 2, 0: 1})

# ---------------------------------------------------------------- the Root House: Frostroot

_ROOT_LEGEND = {"=": FARMLAND, "~": "water", "%": "dross:frostroot_crate", "@": "composter[level=5]",
                "$": lambda dx, dz: f"dross:frostroots[age={ROOT_AGE(dx, dz)}]"}

ROOT_HOUSE = Igloo(
    "root_house", "The Root House", "farm",
    "Where the village grows its Frostroot: two domes end to end, four long beds either side of a water channel down "
    "the middle, lanterns hung over the channel to grow them by, crates of the harvest, compost bins and barrels along "
    "the walls.",
    [(0, 0, 6, 6.6, [], False), (0, -8, 6, 6.6, [], False)],
    {0: L(4, {
        **{dz: (None, None, {-2: "=", -1: "=", 0: "~", 1: "=", 2: "="}) for dz in range(-10, 1)},
        -11: (None, None, {-2: "=", -1: "=", 1: "=", 2: "="}),
        1: (None, None, {-2: "=", -1: "=", 1: "=", 2: "="}),
    }), 1: L(4, {
        -12: (None, None, {-2: "O", -1: "%", 0: "C", 1: "%", 2: "O"}),
        -11: (-3, 3, {-2: "$", -1: "$", 1: "$", 2: "$"}),
        **{dz: (-4 if dz <= -6 or dz >= -2 else -3, 4 if dz <= -6 or dz >= -2 else 3,
                {-2: "$", -1: "$", 1: "$", 2: "$"}) for dz in range(-10, 2)},
        -10: (-4, 4, {-4: "O", -2: "$", -1: "$", 1: "$", 2: "$", 4: "O"}),
        -9: (-4, 4, {-4: "%", -2: "$", -1: "$", 1: "$", 2: "$", 4: "@"}),
        -8: (-4, 4, {-4: "@", -2: "$", -1: "$", 1: "$", 2: "$", 4: "%"}),
        -7: (-4, 4, {-4: "%", -2: "$", -1: "$", 1: "$", 2: "$", 4: "T"}),
        -6: (-4, 4, {-4: "O", -2: "$", -1: "$", 1: "$", 2: "$", 4: "O"}),
        -2: (-4, 4, {-4: "%", -2: "$", -1: "$", 1: "$", 2: "$", 4: "%"}),
        -1: (-4, 4, {-4: "%", -2: "$", -1: "$", 1: "$", 2: "$", 4: "O"}),
        0: (-4, 4, {-4: "O", -2: "$", -1: "$", 1: "$", 2: "$", 4: "%"}),
        1: (-4, 4, {-4: "@", -2: "$", -1: "$", 1: "$", 2: "$", 4: "@"}),
        2: (-4, 4, {}),
        3: (-3, 3, {-3: "%", 3: "%"}),
        4: (-2, 2, {}),
    })},
    legend=_ROOT_LEGEND,
    hangs=[(0, -9, 3, "lantern[hanging=true]"), (0, -5, 3, "lantern[hanging=true]"), (0, -1, 3, "lantern[hanging=true]")],
)

# ---------------------------------------------------------------- the Lichen House: Snow Lichen

_LICHEN_SPANS = {-6: 1, -5: 3, -4: 4, -3: 5, -2: 5, -1: 6, 0: 6, 1: 6, 2: 5, 3: 5, 4: 4, 5: 3, 6: 1}
_LICHEN_FURNITURE = {(-6, 0): "C", (6, 0): "@", (-5, -3): "O", (5, -3): "%", (-5, 3): "@", (5, 3): "O",
                     (0, -6): "C", (-1, -6): "O", (1, -6): "O", (-6, -1): "O", (6, 1): "O"}


def _lichen_layers():
    floor, crops = {}, {}
    for dz, half in _LICHEN_SPANS.items():
        f, c = {}, {}
        for dx in range(-half, half + 1):
            ring = max(abs(dx), abs(dz))
            if ring <= 1:
                f[dx] = "~"                      # the pool in the middle
            elif ring in (2, 4) and not (dx == 0 and dz == 4):
                f[dx] = "="
                c[dx] = "$"
            elif (dx, dz) in _LICHEN_FURNITURE:
                c[dx] = _LICHEN_FURNITURE[(dx, dz)]
        floor[dz] = (None, None, f)
        crops[dz] = (-half, half, c)
    crops[7] = (0, 0, {})
    return floor, crops


_LF, _LC = _lichen_layers()

LICHEN_HOUSE = Igloo(
    "lichen_house", "The Lichen House", "farm",
    "Where the village grows Snow Lichen, the moose's winter feed: one round dome, its beds in two rings round a pool "
    "of water, a lantern over each corner of the walk between them, the harvest's barrels, bins and chests against the "
    "wall.",
    [(0, 0, 7.5, 8.0, [], False)],
    {0: L(6, _LF), 1: L(6, _LC)},
    legend={"=": FARMLAND, "~": "water", "%": "dross:frostroot_crate", "@": "composter[level=3]",
            "$": lambda dx, dz: f"dross:snow_lichen[age={LICHEN_AGE(dx, dz)}]"},
    hangs=[(-3, -3, 3, "lantern[hanging=true]"), (3, -3, 3, "lantern[hanging=true]"),
           (-3, 3, 3, "lantern[hanging=true]"), (3, 3, 3, "lantern[hanging=true]")],
)

# ---------------------------------------------------------------- the Rimeberry Patch: outdoors

PW, PD, PH = 19, 17, 10        # its plot (odd: the grid keeps a middle); the ground at layer 1


def _patch(g):
    x0, x1, z0, z1 = 2, 16, 2, 14          # inside the windbreaks
    for x in range(PW):
        for z in range(PD):
            g.set(x, 0, z, "snow_block", force=True)
    # windbreaks on three sides (the user: "for the windbreaks I kind of like it, make sure to include those a lot"),
    # their drifts outside, the south left open as the way in
    props.windbreak(Frame(g, 17, 1, "south"), length=17)
    props.windbreak(Frame(g, 1, 2, "east"), length=13)
    props.windbreak(Frame(g, 17, 14, "west"), length=13)
    # three hedges of Rimeberry Bushes on Hoarfrost, paths between them
    hedges = (4, 7, 10)
    for z in hedges:
        for x in range(4, 15):
            g.set(x, 0, z, "dross:hoarfrost", force=True)
            age = BERRY_AGE(x, z)
            g.set(x, 1, z, f"dross:rimeberry_bush[age={age},half=lower]", force=True)
            if age > 0:
                g.set(x, 2, z, f"dross:rimeberry_bush[age={age},half=upper]", force=True)
    # the pickers' corner by the way in: barrels with baskets of berries on them, a crate, baskets on the ground
    for x, z, s in ((3, 12, "barrel[facing=up,open=false]"), (4, 12, "dross:rimeberry_basket"),
                    (3, 13, "dross:rimeberry_basket"), (15, 12, "barrel[facing=up,open=false]"),
                    (14, 12, "dross:rimeberry_basket")):
        g.set(x, 1, z, s, force=True)
    g.set(3, 2, 12, "dross:rimeberry_basket", force=True)
    g.set(15, 2, 12, "dross:rimeberry_basket", force=True)
    # lantern posts: at the ends of the paths and either side of the way in (nothing may spawn among the bushes)
    # (soul lanterns by the windbreaks: a lantern's light would melt the snow on their tops)
    for x, z, face, kind in ((3, 5, "east", "soul_lantern"), (15, 8, "west", "soul_lantern"),
                             (3, 8, "east", "soul_lantern"), (15, 5, "west", "soul_lantern"),
                             (6, 14, "north", "lantern"), (12, 14, "north", "lantern")):
        props.lantern_post(Frame(g, x, z, face), height=2, kind=kind)
    # and lamps standing on posts (over head height, the paths stay open): soul ones in the back corners by the
    # windbreaks, lanterns in the middle of each path
    for x, z, kind in ((3, 2, "soul_lantern"), (15, 2, "soul_lantern"), (9, 5, "lantern"), (9, 8, "lantern")):
        props.lantern_post(Frame(g, x, z, "south"), height=2, kind=kind, arm=False)
    rooms = set()
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            rooms.add((x, 1, z))
    mid = PW // 2
    return (mid, 1, PD - 1), (mid, z1 + 1), rooms


BERRY_PATCH = Custom("rimeberry_patch", "The Rimeberry Patch", "farm",
                     "The village's berry patch, out in the open: three hedges of two-block Rimeberry Bushes on "
                     "Hoarfrost, windbreaks of snow round three sides, lantern posts at the ends of the paths, and the "
                     "pickers' barrels and baskets by the way in.", (PW, PD, PH), _patch)

DESIGNS = [ROOT_HOUSE, LICHEN_HOUSE, BERRY_PATCH]
