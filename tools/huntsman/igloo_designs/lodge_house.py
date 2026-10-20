"""The Huntsman's lodge and the Alpha Moose's barn, each one template (the user, October 20, 2026: "I like the design
of the house overall ... give his home one template as well"; "he should have his own food"; "the skeleton skulls ...
should be facing outward"; "there's holes in the house where the fences are that shouldn't be there"; "I like his
little bedroom area"; "a chest in his bedroom, and then maybe two chests in the main area, and then a couple of
barrels"; the barn: "lighting in the back ... a chest down there, and a chest up here").

The lodge: his great dome (its way in 3 wide and 4 tall, his moose skull over it, his banners either side), with his
bedroom joined on the west and the armoury on the east. Inside, his seat at the back facing the door (its arms full
blocks, his antlers and a skull mounted over it inside the room, the wall untouched), his banners either side of it,
the fire in the middle under its vent with benches round it, his table of roasted meat and his smoker, two chests and
a couple of barrels, skulls on posts facing into the room. The barn: plank walls on a stone footing, the Alpha Moose's
stall, feed and a chest at the back lit by lanterns, a chest in the hay loft, its corral beside it fenced with bone
posts, the skulls on them facing out.
"""
import mc
from voxel import AIR, Mix

from villagekit.local import SKULL_ROT16, Frame
from villagekit.writer import banner

import lodge as old_lodge

from .halls import BANNER, SIGIL
from .plan import Custom, Igloo, L, sym

LODGE_STYLE = old_lodge.LODGE_STYLE


def _outside(g, cx, cz, hall):
    """His moose skull over the way in, his banners either side of it on poles, facing out."""
    t = hall.tunnel_shape
    hx, hz = t.f.xz(0, t.v1 - 1)
    old_lodge.moose_head(Frame(g, hx, hz, "south"), t.ybase + t.height)
    for side in (-4, 4):
        x, z = t.f.xz(side, t.v1 + 1)
        for y in range(1, 6):
            g.set(x, y, z, "spruce_fence", force=True)
        g.set(x, 6, z, "brown_banner[rotation=0]", force=True)
        g.block_nbt[(x, 6, z)] = SIGIL


def _rug(cols, a="g", b="n"):
    return {c: (a if c % 2 == 0 else b) for c in cols}


LODGE = Igloo(
    "huntsman_lodge", "The Huntsman's Lodge", "lodge",
    "His great lodge: his seat at the back facing the door under his antlers, the fire and its benches, his table of "
    "roasted meat, his bedroom on the west with its triple bed, the armoury on the east, his banners outside.",
    [(0, 0, 9, 9.5, [(-60, 2), (130, 3), (-130, 3)], False),
     (-12, -2, 5, 5.6, [], False), (12, -2, 5, 5.6, [], False)],
    {1: L(15, {
        -7: (-2, 2, {-1: "J", 0: "^", 1: "J"}),
        -6: (-4, 4, sym({4: "C", 3: "O"})),
        -5: ([(-13, -11), (-5, 5), (11, 13)], {-13: "O", -12: "D", -11: "O", **_rug(range(-2, 3)),
                                              11: "I", 12: "I", 13: "I"}),
        -4: ([(-14, -10), (-6, 6), (10, 14)], {-14: "O", -13: "B", -12: "B", -11: "B", -10: "O", **sym({6: "J"}),
                                              **_rug(range(-2, 3), "n", "g"), 13: "G", 14: "X"}),
        -3: (-15, 15, {-13: "b", -12: "b", -11: "b", -1: "e", 0: "e", 1: "e", 15: "C"}),
        -2: (-15, 15, {-15: "C", **_rug(range(-13, -10)), 15: "A"}),
        -1: (-15, 15, {**_rug(range(-13, -10), "n", "g"), **sym({3: "i"}), 15: "I"}),
        0: (-14, 14, {-14: "O", **sym({3: "i"}), 0: "F", 14: "U"}),
        1: ([(-13, -11), (-7, 7), (11, 13)], {**sym({3: "i"}), 11: "O", 13: "O"}),
        2: (-7, 7, {-6: "J", 6: "S"}),
        3: (-6, 6, {-6: "J", 6: "M"}),
        4: (-6, 6, sym({6: "J"})),
        5: (-5, 5, {}),
        6: (-4, 4, sym({3: "P"})),
        7: (-2, 2, {}),
        8: (-1, 1, {}),
    }), 2: L(15, {
        -7: (None, None, {**sym({2: "%"}), -1: "J", 1: "J"}),
        -6: (None, None, sym({3: "4"})),
        -4: (None, None, {-14: "L", -10: "L", **sym({6: "s"}), 14: "L"}),
        2: (None, None, {-6: "R"}),
        3: (None, None, {-6: "R"}),
        4: (None, None, sym({6: "s"})),
        6: (None, None, sym({3: "L"})),
    }), 3: L(15, {
        -7: (None, None, {-1: "a", 0: "a", 1: "a"}),
    })},
    legend={"%": BANNER, "^": "spruce_stairs[facing=north,half=bottom]", "a": "birch_fence",
            "S": "smoker[facing={in},lit=false]"},
    beds={"B": ("brown", "b")},
    hangs=[(-5, 3, 4, "lantern[hanging=true]"), (5, -3, 4, "dross:meat_block[axis=y]"),
           (-3, 4, 4, "dross:meat_block[axis=y]")],
    style=LODGE_STYLE,
    outside=_outside,
    tunnel=3, tunnel_half=2, tunnel_height=5,
)

# ---------------------------------------------------------------- the barn

FLOOR = Mix(("spruce_planks", 3), ("stripped_spruce_wood[axis=y]", 1), patch=0.5)
STONE = old_lodge.STONE
BW, BD, BH = 13, 17, 8           # the barn's box (its door at the south) and its walls' height
CW = 11                          # the corral's width, east of the barn
SIZE = (1 + BW + 1 + CW + 3, 1 + BD + 3, BH + 10)    # (odd: the grid keeps a middle block)


def _barn(g):
    x0, z0 = 1, 1
    x1, z1 = x0 + BW - 1, z0 + BD - 1
    mid = x0 + BW // 2
    door = range(mid - 2, mid + 3)              # 5 wide, 5 tall: the Alpha Moose is 4.6 tall
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            g.set(x, 0, z, STONE if edge else FLOOR, force=True)
            for y in range(1, BH + 1):
                post = (x in (x0, x1)) and (z - z0) % 4 == 0 or (z in (z0, z1)) and (x - x0) % 4 == 0
                if not edge:
                    g.set(x, y, z, AIR, force=True)
                    continue
                if z == z1 and x in door and y <= 5:
                    g.set(x, y, z, AIR, force=True)
                    continue
                if y == 1:
                    g.set(x, y, z, STONE, force=True)
                elif post or y == BH:
                    g.set(x, y, z, "spruce_log[axis=y]" if post else "stripped_spruce_log[axis=x]" if z in (z0, z1)
                          else "stripped_spruce_log[axis=z]", force=True)
                elif y == 4 and not post and ((x in (x0, x1) and (z - z0) % 4 == 2) or (z == z0 and (x - x0) % 4 == 2)):
                    g.set(x, y, z, "spruce_trapdoor[facing={},half=top,open=true]".format(
                        "east" if x == x0 else "west" if x == x1 else "south"), force=True)
                else:
                    g.set(x, y, z, "spruce_planks", force=True)
    for x in door:
        g.set(x, 6, z1, "stripped_spruce_log[axis=x]", force=True)
    for x in (mid - 3, mid + 3):
        for y in range(1, 7):
            g.set(x, y, z1, "spruce_log[axis=y]", force=True)
    # the roof: stairs up from both long sides to a snowy ridge, gable ends of planks, icicles under the eaves
    k = 0
    while x0 - 1 + k <= x1 + 1 - k:
        y = BH + 1 + k
        for z in range(z0 - 1, z1 + 2):
            if x0 - 1 + k == x1 + 1 - k:
                g.set(x0 - 1 + k, y, z, "snow_block", force=True)
            else:
                g.set(x0 - 1 + k, y, z, "spruce_stairs[facing=east,half=bottom]", force=True)
                g.set(x1 + 1 - k, y, z, "spruce_stairs[facing=west,half=bottom]", force=True)
        if 0 < k:
            for z in (z0, z1):
                for x in range(x0 - 1 + k + 1, x1 + 1 - k):
                    g.set(x, y, z, "spruce_planks", force=True)
        k += 1
    for z in range(z0 - 1, z1 + 2, 2):
        for x in (x0 - 1, x1 + 1):
            g.set(x, BH, z, "dross:icicle", force=True)
    # the hay loft over the back third, up a ladder: hay bales, and a chest
    loft_y = 5
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z0 + 6):
            g.set(x, loft_y, z, "spruce_slab[type=top]", force=True)
    for x in range(x0 + 1, x1):
        if x != mid:
            g.set(x, loft_y + 1, z0 + 5, "spruce_fence", force=True)
    for y in range(1, loft_y + 1):
        g.set(mid, y, z0 + 5, "spruce_planks", force=True)
        g.set(mid, y, z0 + 6, "ladder[facing=south]", force=True)
    for x, z, ax in ((x0 + 1, z0 + 1, "x"), (x0 + 2, z0 + 1, "z"), (x0 + 1, z0 + 2, "y"), (x0 + 1, z0 + 3, "y")):
        g.set(x, loft_y + 1, z, f"hay_block[axis={ax}]", force=True)
    g.set(x0 + 1, loft_y + 2, z0 + 1, "hay_block[axis=y]", force=True)
    g.set(x1 - 1, loft_y + 1, z0 + 1, "chest[facing=south,type=single]", force=True)          # the loft's chest
    g.set(x1 - 2, loft_y + 1, z0 + 1, "barrel[facing=up,open=false]", force=True)
    g.set(x1 - 2, loft_y + 2, z0 + 1, "lantern[hanging=false]", force=True)                    # a lamp up there
    # under the loft, at the back: feed barrels, the chest, and lanterns on the barrels to light it
    for x in (x0 + 1, x0 + 2):
        g.set(x, 1, z0 + 1, "barrel[facing=up,open=false]", force=True)
    g.set(x0 + 2, 2, z0 + 1, "lantern[hanging=false]", force=True)
    g.set(x1 - 1, 1, z0 + 1, "chest[facing=south,type=single]", force=True)
    g.set(x1 - 2, 1, z0 + 1, "barrel[facing=up,open=false]", force=True)
    g.set(x1 - 2, 2, z0 + 1, "lantern[hanging=false]", force=True)
    g.set(mid - 2, 1, z0 + 2, "hay_block[axis=y]", force=True)
    # the stall: straw (hay), the feed trough and the water trough along the west wall, fenced off from the walkway
    for x in range(x0 + 1, x1 - 3):
        for z in range(z0 + 7, z1):
            g.set(x, 0, z, "hay_block[axis=y]", force=True)
    for z in range(z0 + 7, z0 + 10):
        g.set(x0 + 1, 1, z, "composter[level=7]", force=True)
    for z in range(z0 + 11, z0 + 14):
        g.set(x0 + 1, 1, z, "water_cauldron[level=3]", force=True)
    for z in range(z0 + 7, z1 - 1):
        g.set(x1 - 4, 1, z, "spruce_fence", force=True)
    g.set(x1 - 4, 1, z0 + 7, "spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]", force=True)
    # the tack wall (east): a saddle and a blanket over rails, spears
    g.set(x1 - 1, 1, z0 + 9, "spruce_fence", force=True)
    g.set(x1 - 1, 2, z0 + 9, "brown_carpet", force=True)
    g.set(x1 - 1, 1, z0 + 11, "spruce_fence", force=True)
    g.set(x1 - 1, 2, z0 + 11, "red_carpet", force=True)
    for z in (z0 + 13, z0 + 14):          # PLACEHOLDER spears (lightning rods) until the real spear model and item exist
        g.set(x1 - 1, 1, z, "lightning_rod[facing=up]", force=True)
    # beams across under the roof, a lantern hanging from the middle of each on one short chain (the user: "the
    # lanterns not have such long chains and be centered on the middle of the support"), and a lantern on the stall's
    # fence by the door
    for z in (z0 + 7, z0 + 10, z0 + 13):
        for x in range(x0 + 1, x1):
            g.set(x, BH, z, "spruce_log[axis=x]", force=True)
        for y in (BH - 1, BH - 2):
            g.set(mid, y, z, "chain[axis=y]", force=True)
        g.set(mid, BH - 3, z, "lantern[hanging=true]", force=True)
    g.set(x1 - 4, 2, z1 - 2, "lantern[hanging=false]", force=True)
    old_lodge.moose_head(Frame(g, mid, z1, "south"), 7)
    # the corral, east of the barn: bone posts (their skulls facing out) and spruce rails, a gate at the south, a
    # water trough and hay inside
    cx0, cx1 = x1 + 2, x1 + 1 + CW
    cz0, cz1 = z0 + 2, z1
    for x in range(cx0, cx1 + 1):
        for z in range(cz0, cz1 + 1):
            edge = x in (cx0, cx1) or z in (cz0, cz1)
            if not edge:
                continue
            if z == cz1 and abs(x - (cx0 + cx1) // 2) <= 1:
                if x == (cx0 + cx1) // 2:
                    g.set(x, 1, z, "spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]", force=True)
                else:
                    g.set(x, 1, z, "spruce_fence", force=True)
                continue
            post = ((x - cx0) % 3 == 0 and z in (cz0, cz1)) or ((z - cz0) % 3 == 0 and x in (cx0, cx1))
            if post:
                g.set(x, 1, z, "bone_block[axis=y]", force=True)
                g.set(x, 2, z, "bone_block[axis=y]", force=True)
                if (x - cx0) % 6 == 0 and z in (cz0, cz1) or (z - cz0) % 6 == 0 and x in (cx0, cx1):
                    out = "north" if z == cz0 else "south" if z == cz1 else "west" if x == cx0 else "east"
                    g.set(x, 3, z, f"skeleton_skull[rotation={SKULL_ROT16[out]}]", force=True)
            else:
                g.set(x, 1, z, "spruce_fence", force=True)
    for x in range(cx0 + 2, cx0 + 5):
        g.set(x, 1, cz1 - 2, "water_cauldron[level=3]", force=True)
    g.set(cx1 - 2, 1, cz0 + 2, "hay_block[axis=y]", force=True)
    g.set(cx1 - 3, 1, cz0 + 2, "hay_block[axis=z]", force=True)
    rooms = set()
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            rooms.add((x, 1, z))
    return (mid, 1, z1 + 1), (mid, z1), rooms


BARN = Custom("alpha_moose_barn", "The Alpha Moose's Barn", "lodge",
              "The Alpha Moose's barn: its stall of straw with feed and water troughs, the tack wall, feed and a chest at "
              "the back lit by lanterns, a hay loft with a chest up the ladder, and its corral beside it, the skulls on "
              "the posts facing out.", SIZE, _barn)

DESIGNS = [LODGE, BARN]
