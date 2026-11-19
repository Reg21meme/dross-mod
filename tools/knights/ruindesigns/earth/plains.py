"""Earth's plains ruins (plains, sunflower plains): grass and cobblestone, stone bricks and tuff, hay bales, sunflowers,
standing stones, old farm walls."""
import math

from ruinkit import AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, rubble, slab, stairs

from .earthkit import FLOWERS, boulder, flowers, standing_stone

P = dict(
    floor=Mix(("cobblestone", 3), ("stone_bricks", 2), ("mossy_cobblestone", 2), ("gravel", 1), ("grass_block", 2), patch=0.5),
    wall=Mix(("cobblestone", 4), ("mossy_cobblestone", 3), ("stone_bricks", 1), ("tuff", 1), patch=0.45),
    stone=Mix(("stone", 3), ("tuff", 3), ("andesite", 2), ("mossy_cobblestone", 1), patch=0.5),
    rubble=Mix(("cobblestone", 3), ("mossy_cobblestone", 2), ("tuff", 1)),
    path=Mix(("dirt_path", 4), ("coarse_dirt", 2), ("gravel", 1), patch=0.5),
)

AREA = Area(element="earth", id="plains", name="Plains", biomes="plains, sunflower plains", setting=SURFACE,
            ground="grass_block", under="dirt", palette=P,
            blurb="Standing stones, old farm walls and grassy barrows out on the open plains.")

BRICK_WEATHER = {"stone_bricks": Mix(("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 2)),
                 "cobblestone": Mix(("cobblestone", 3), ("mossy_cobblestone", 2))}


def sunflowers(g, cells):
    for x, z in cells:
        if g.empty(x, 1, z) and g.empty(x, 2, z) and (g.get(x, 0, z) is None or "grass" in (g.get(x, 0, z) or "")):
            g.set(x, 1, z, "sunflower[half=lower]")
            g.set(x, 2, z, "sunflower[half=upper]")


def hay(g, x, y, z, axis="y"):
    g.set(x, y, z, f"hay_block[axis={axis}]")


def worn_path(g, cells):
    for x, z in cells:
        if 0 <= x < g.W and 0 <= z < g.D and g.get(x, 0, z) is None:
            g.set(x, 0, z, P["path"])


@layout("stone_circle", "Stone Circle", size=(25, 16, 25),
        blurb="A ring of standing stones on the grass, a great trilithon at its back framing the broken frame.")
def stone_circle(g, p):
    """Nine weathered stones round a worn ring path; the trilithon at the back, its lintel cracked and half fallen."""
    mid, cz = g.W // 2, 12
    # the worn ring path just inside the stones
    ring = [(x, z) for x in range(g.W) for z in range(g.D) if 7.2 <= math.hypot(x - mid, z - cz) <= 8.4
            and g.noise3_at(3.0, x, 0, z) > 0.25]
    worn_path(g, ring)
    # the trilithon: two great uprights, a lintel left on the west one, its east half fallen in front
    for ux in (mid - 4, mid + 3):
        h = 9 if ux < mid else 6
        for y in range(1, h + 1):
            for dx in (0, 1):
                for z in (2, 3):
                    g.set(ux + dx, y, z, p["stone"])
    for x in range(mid - 4, mid + 2):
        for z in (2, 3):
            g.set(x, 10, z, "tuff" if (x + z) % 3 else "stone")
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (4, 5), (3, 6)])
    for x in range(mid - 2, mid + 3):
        g.set(x, 0, 4, "tuff")
    # the fallen half of the lintel, one end on the east upright's stump, the other in the grass
    g.line((mid + 4, 7, 3), (mid + 7, 1, 6), "tuff")
    g.line((mid + 4, 7, 2), (mid + 7, 1, 5), "stone")
    rubble(g, cells, p["rubble"])
    # the ring's stones
    for k, (ang, h) in enumerate(((200, 6), (160, 4), (232, 3), (128, 5), (305, 4), (338, 5), (20, 3), (60, 0), (100, 4))):
        a = math.radians(ang)
        x = int(round(mid + math.cos(a) * 9.4))
        z = int(round(cz - math.sin(a) * 9.4))
        if h == 0:
            continue
        w, d = (2, 1) if abs(math.sin(a)) > 0.6 else (1, 2)
        standing_stone(g, x, z, h, p["stone"], w=w, d=d, lean=(1, 0) if k == 2 else None)
    # two stones toppled flat in the grass
    for x0, z0, dx, dz, n in ((mid + 6, 18, 1, -1, 4), (2, 14, 0, 1, 4)):
        g.line((x0, 1, z0), (x0 + dx * n, 1, z0 + dz * n), p["stone"])
    # a low altar stone before the frame
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, 7, "smooth_stone_slab[type=double]" if x == mid else slab("smooth_stone_slab"))
    g.set(mid, 2, 7, "lantern")
    boulder(g, 3, 1, 4, 1.6, p["stone"], top="moss_carpet")
    flowers(g, 26, kinds=FLOWERS, tall=0.08, grass=0.45, avoid=lambda x, z: math.hypot(x - mid, z - 13) < 5)
    return [guard(mid - 3, 13), guard(mid + 3, 13)]


@layout("farmstead", "Old Farmstead", size=(25, 16, 23),
        blurb="Broken field walls and a fallen stone barn; the frame stands against the barn's last gable.")
def farmstead(g, p):
    """A tumbled-down farm: dry-stone walls round a yard, the barn's broken corner and gable at the back, spilled hay,
    a well, a row of sunflowers along the wall."""
    mid = g.W // 2
    court(g, Mix(("grass_block", 4), ("coarse_dirt", 2), ("dirt_path", 2), ("cobblestone", 1), patch=0.6), 10.0, cz=12,
          under="dirt")
    # the field walls: along the west and the front-east, two blocks high with a wall-block top, gaps and stumps
    for z in range(4, g.D - 2):
        if 13 <= z <= 15:
            continue
        g.set(1, 1, z, p["wall"])
        if g.noise3_at(3.0, 1, 2, z) > 0.35:
            g.set(1, 2, z, "cobblestone_wall" if z % 3 else "mossy_cobblestone_wall")
    for x in range(mid + 4, g.W - 1):
        g.set(x, 1, g.D - 3, p["wall"])
        if g.noise3_at(3.0, x, 2, 0) > 0.45:
            g.set(x, 2, g.D - 3, "mossy_cobblestone_wall")
    # the barn: its back wall and gable with a log frame, the east wall broken down, a roof beam fallen into the yard
    for x in range(3, g.W - 3):
        for y in range(1, 6):
            g.set(x, y, 2, "stone_bricks" if y > 1 else "cobblestone")
    for y in range(6, 11):
        for x in range(3 + (y - 5) * 2, g.W - 3 - (y - 5) * 2):
            g.set(x, y, 2, "stone_bricks")
    for x in (3, g.W - 4):
        for y in range(1, 6):
            g.set(x, y, 3, "stripped_oak_log[axis=y]")
    for z in range(3, 9):
        for y in range(1, 5 - (z - 3) // 2):
            g.set(g.W - 4, y, z, "stone_bricks")
    for x in range(3, g.W - 3):
        g.set(x, 6, 3, "oak_log[axis=x]")
    g.ruin_tops(3, 2, g.W - 3, 2, 3, 10, 4.0, 10.5, scale=4.0, salt=3, region=lambda x, z: abs(x - mid) > 3)
    g.weather(0, 1, 0, g.W - 1, 12, g.D - 1, BRICK_WEATHER, chance=0.6)
    # the frame in the barn's broken doorway, against its gable
    for x in range(mid - 2, mid + 3):
        for y in range(1, 8):
            g.set(x, y, 2, AIR, force=True)
    cells = broken_frame(g, mid - 2, 1, 3, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, p["rubble"])
    g.line((g.W - 5, 5, 4), (mid + 3, 1, 9), "oak_log[axis=y]")
    # hay: a stack against the barn and bales spilled across the yard
    for x, z, n in ((4, 4, 3), (5, 4, 2), (4, 5, 1), (g.W - 6, 5, 2)):
        for y in range(1, n + 1):
            hay(g, x, y, z)
    for x, z, ax in ((7, 9, "x"), (mid + 5, 12, "z"), (3, 17, "y")):
        hay(g, x, 1, z, ax)
    # the well: a cobblestone ring round water, a little roof on two posts
    wx, wz = 4, 11
    for dx in range(-1, 2):
        for dz in range(-1, 2):
            if dx == 0 and dz == 0:
                g.set(wx, 0, wz, "water")
                g.set(wx, -1, wz, "water")
                g.set(wx, -2, wz, "cobblestone")
            else:
                for y in (-2, -1, 0):
                    g.set(wx + dx, y, wz + dz, "cobblestone")
                g.set(wx + dx, 1, wz + dz, "cobblestone_wall" if (dx + dz) % 2 else "mossy_cobblestone")
    for dz in (-1, 1):
        g.set(wx, 2, wz + dz, "oak_fence")
        g.set(wx, 3, wz + dz, "oak_fence")
    g.set(wx, 4, wz - 1, slab("oak_slab"))
    g.set(wx, 4, wz, slab("oak_slab"))
    g.set(wx, 4, wz + 1, slab("oak_slab"))
    g.set(wx, 3, wz, "lantern[hanging=true]")
    # sunflowers along the west wall and in a row at the front
    sunflowers(g, [(2, z) for z in range(15, g.D - 2, 1)] + [(x, g.D - 4) for x in range(mid + 5, g.W - 2)])
    g.set(g.W - 3, 1, 9, "composter[level=3]")
    flowers(g, 12, grass=0.6, avoid=lambda x, z: abs(x - mid) < 6 and 8 < z < 18)
    return [guard(mid - 3, 12), guard(mid + 3, 12)]


@layout("road_gate", "Old Road Gate", size=(25, 18, 25),
        blurb="A stone gatehouse where an old road crossed a boundary wall; the frame stands in the gateway.")
def road_gate(g, p):
    """Round 1's Broken Gate as a roadside gatehouse: a cobbled road runs in from the front to a raised gateway between
    two square towers (one broken low), the frame in the arch, the boundary wall running off to the sides."""
    mid = g.W // 2
    court(g, Mix(("cobblestone", 2), ("stone_bricks", 2), ("grass_block", 3), ("gravel", 1), patch=0.5), 8.0, cz=14,
          under="dirt")
    # the road: cobbles in the middle, worn dirt at its edges, through to the front
    for z in range(8, g.D):
        for x in range(mid - 3, mid + 4):
            edge = abs(x - mid) == 3
            if edge and g.noise3_at(3.0, x, 0, z) < 0.45:
                continue
            g.set(x, 0, z, P["path"] if edge else Mix(("cobblestone", 3), ("mossy_cobblestone", 2), ("gravel", 1)))
    # the gateway's dais: a step up, the frame on it
    for x in range(mid - 3, mid + 4):
        for z in range(3, 8):
            g.set(x, 1, z, "stone_bricks" if (x + z) % 3 else "chiseled_stone_bricks")
        g.set(x, 1, 8, stairs("stone_brick_stairs", "north"))
    cells = broken_frame(g, mid - 2, 2, 5, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the towers: 4 x 4 hollow, the west tall with a broken crown, the east knocked down to a stump
    for x0, h in ((mid - 8, 11), (mid + 5, 4)):
        g.walls(x0, 3, x0 + 3, 6, 1, h, "stone_bricks")
        for x in range(x0, x0 + 4):
            for z in range(3, 7):
                g.set(x, 0, z, "cobblestone")
    g.ruin_tops(mid - 8, 3, mid - 5, 6, 6, 11, 7.0, 11.0, scale=3.0, salt=5)
    g.ruin_tops(mid + 5, 3, mid + 8, 6, 2, 4, 1.5, 4.0, scale=3.0, salt=6)
    # the arch over the gate: from the west tower's top across, its east end broken off
    for x in range(mid - 4, mid + 1):
        g.set(x, 9 if x > mid - 4 else 10, 4, "stone_bricks")
    g.set(mid - 4, 8, 4, stairs("stone_brick_stairs", "east", "top"))
    g.set(mid - 4, 9, 4, "stone_bricks")
    # the boundary wall off to both sides, broken low
    for x in list(range(0, mid - 8)) + list(range(mid + 9, g.W)):
        for y in range(1, 4):
            g.set(x, y, 5, p["wall"])
    g.ruin_tops(0, 5, g.W - 1, 5, 1, 3, 1.2, 3.6, scale=4.0, salt=9,
                region=lambda x, z: x < mid - 8 or x > mid + 8)
    g.weather(0, 0, 0, g.W - 1, 12, g.D - 1, BRICK_WEATHER, chance=0.55)
    # the east tower's fallen stones and a milestone by the road
    g.rubble_pile(mid + 8, 9, 2.6, 2.0, p["rubble"], top_mix="mossy_cobblestone_slab[type=bottom]")
    g.set(mid - 5, 1, g.D - 4, "chiseled_stone_bricks")
    g.set(mid - 5, 2, g.D - 4, "stone_brick_wall")
    # a hay cart left by the road
    for x in (mid + 5, mid + 6):
        for z in (17, 18, 19):
            g.set(x, 1, z, slab("spruce_slab", "top") if z != 19 else "spruce_trapdoor[facing=south,half=top,open=false]")
    hay(g, mid + 5, 2, 18, "z")
    hay(g, mid + 6, 2, 17, "z")
    for z in (17, 19):
        g.set(mid + 4, 1, z, "spruce_fence")
    g.set(mid - 4, 2, 7, "lantern")
    g.set(mid + 4, 2, 7, "lantern")
    flowers(g, 16, tall=0.05, grass=0.6, avoid=lambda x, z: abs(x - mid) < 4)
    return [guard(mid - 4, 13), guard(mid + 4, 13)]


@layout("barrow", "Grassy Barrow", size=(25, 18, 25),
        blurb="An old burial mound ringed with kerbstones; the frame stands in its stone-lined mouth.")
def barrow(g, p):
    """A long grassy barrow across the back, flowers growing over it; a dolmen of tuff for its mouth, the frame in it,
    kerbstones round its foot and two stones guarding the way."""
    mid = g.W // 2
    hts = mound(g, mid, 4, 11.5, 7.5, "grass_block", top="grass_block", y0=0, rz=6.0)
    # the passage into the mound, lined and roofed with tuff; the frame stands in its mouth
    for z in range(1, 11):
        for x in range(mid - 3, mid + 4):
            for y in range(1, 10):
                if abs(x - mid) <= 1 and y <= 6 and z <= 9:
                    g.set(x, y, z, AIR, force=True)
                elif abs(x - mid) <= 3 and y <= 7 and (g.get(x, y, z) is not None) and hts.get((x, z), 0) > y:
                    g.set(x, y, z, "tuff" if (x + y + z) % 4 else "mossy_cobblestone")
    for x in range(mid - 3, mid + 4):
        for z in range(1, 10):
            g.set(x, 0, z, "stone_bricks")
    # the mouth: great uprights and a capstone, sticking out of the mound's face
    for x in (mid - 3, mid + 3):
        for y in range(1, 9):
            for z in (10, 11):
                g.set(x, y, z, p["stone"])
    for x in range(mid - 4, mid + 5):
        for z in (10, 11):
            if not (x == mid + 4 and z == 11):
                g.set(x, 9, z, "tuff")
    cells = broken_frame(g, mid - 2, 1, 10, [(0, 6), (1, 6), (2, 6)])
    for x in range(mid - 2, mid + 3):
        g.set(x, 0, 10, "stone_bricks")
    rubble(g, cells, p["rubble"])
    # a lantern deep in the passage
    g.set(mid, 1, 2, "chiseled_stone_bricks")
    g.set(mid, 2, 2, "lantern")
    # kerbstones round the mound's foot
    for k in range(13):
        a = math.radians(-10 + k * 15)
        x = int(round(mid + math.cos(a) * 11.5))
        z = int(round(4 + math.sin(a) * 6.8))
        if 0 <= x < g.W and 0 <= z < g.D and abs(x - mid) > 4:
            h = 1 if k % 3 else 2
            for y in range(1, h + 1):
                g.set(x, y, z, p["stone"])
    # two stones guarding the way in, one leaning
    from .earthkit import standing_stone as ss
    ss(g, mid - 6, 18, 5, p["stone"], w=1, d=1)
    ss(g, mid + 6, 19, 4, p["stone"], w=1, d=1, lean=(1, 0))
    boulder(g, 3, 17, 20, 1.5, p["stone"], top="moss_carpet")
    flowers(g, 40, tall=0.12, grass=0.35, y_max=9, avoid=lambda x, z: abs(x - mid) < 5 and z > 9)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


@layout("sunflower_field", "Sunflower Field", size=(25, 16, 25),
        blurb="The frame toppled flat in a sunflower field, a scarecrow over it and tuff boulders round the clearing.")
def sunflower_field(g, p):
    """Rows of sunflowers across the field, trampled flat in a clearing round the fallen frame; tuff boulders the
    Rockfall Knight threw lie about, a scarecrow stands guard and a broken hay cart."""
    mid = g.W // 2
    # the clearing: flattened, coarse dirt and path where the frame fell
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - mid) / 1.1, z - 11)
            if d < 7.5 + g.noise3_at(3.0, x, 0, z) * 2.0:
                g.set(x, 0, z, Mix(("grass_block", 3), ("coarse_dirt", 2), ("dirt_path", 1), ("rooted_dirt", 1), patch=0.6))
    # the frame lying flat, its top end broken, a few pieces standing up out of the dirt
    flat_frame(g, mid - 2, 1, 3, keep=lambda i, j: not (j == 0 and i >= 3) and not (i == 4 and j == 1), along="z")
    g.set(mid + 2, 1, 1, FRAME)
    g.set(mid + 3, 1, 2, FRAME)
    # a dirt bank thrown up where it hit
    for x in range(mid - 4, mid + 5):
        g.set(x, 1, 2, "coarse_dirt" if g.empty(x, 1, 2) and (x + 1) % 3 else None)
    # the sunflower rows (every other row, outside the clearing)
    rows = []
    for z in range(1, g.D - 1):
        for x in range(1, g.W - 1):
            if x % 2 == 0 and g.get(x, 0, z) is None and g.noise3_at(2.5, x, 3, z) > 0.2:
                rows.append((x, z))
    sunflowers(g, rows)
    # the scarecrow by the frame
    sx, sz = mid + 5, 6
    for y in (1, 2, 3):
        g.set(sx, y, sz, "oak_fence")
    g.set(sx, 4, sz, "hay_block[axis=y]")
    g.set(sx, 5, sz, "carved_pumpkin[facing=south]")
    g.set(sx - 1, 4, sz, "oak_fence")
    g.set(sx + 1, 4, sz, "oak_fence")
    # tuff boulders round the clearing
    for bx, bz, r in ((3, 4, 2.2), (g.W - 4, 13, 1.8), (4, 18, 1.5), (mid + 7, 20, 1.3)):
        boulder(g, bx, 1, bz, r, Mix(("tuff", 3), ("stone", 1), ("mossy_cobblestone", 1)), top="moss_carpet")
    # the broken hay cart and its spilled bales
    for x in (mid - 7, mid - 6):
        for z in (12, 13):
            g.set(x, 1, z, slab("oak_slab", "top"))
    g.set(mid - 6, 2, 12, "hay_block[axis=x]")
    g.set(mid - 5, 1, 14, "hay_block[axis=z]")
    g.set(mid - 8, 1, 12, "oak_fence")
    g.set(mid - 8, 1, 13, "oak_fence")
    flowers(g, 18, kinds=("dandelion", "poppy", "cornflower", "oxeye_daisy"), grass=0.6,
            avoid=lambda x, z: abs(x - mid) < 5 and 10 < z < 19)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [stone_circle, farmstead, road_gate, sunflower_field]
DROPPED = [barrow]
