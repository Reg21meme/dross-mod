"""Thunder's cold shores: grey gravel and stone, mossy cobble walls, dark ships' timbers thrown up by the storms,
dried kelp, and the storm's own marks (lightning rods on weathered copper, deepslate, sea lanterns)."""
import math

from ruinkit import (AIR, FRAME, SHORE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, shore_line, slab, stairs)
from ruindesigns.thunder.stormkit import (COPPER, CUT_COPPER, DEEP, ROD, STORM, hang, rod_on, rod_post, scorch,
                                          sea_floor)

TIMBER = Mix(("spruce_planks", 3), ("dark_oak_planks", 3), ("stripped_spruce_log[axis=y]", 1), patch=0.5)

P = dict(
    floor=Mix(("gravel", 3), ("cobblestone", 2), ("stone", 2), ("mossy_cobblestone", 1), ("andesite", 1), patch=0.5),
    wall=Mix(("cobblestone", 3), ("mossy_cobblestone", 2), ("stone", 2), ("stone_bricks", 1),
             ("cracked_stone_bricks", 1), patch=0.4),
    accent=Mix(("prismarine", 2), ("dark_prismarine", 1), ("mossy_cobblestone", 1), patch=0.4),
    timber=TIMBER,
    trim="stone_bricks",
    rubble=Mix(("cobblestone", 2), ("mossy_cobblestone", 2), ("stone", 1)),
    light="sea_lantern",
)

AREA = Area(element="thunder", id="cold_beach", name="Cold Shore", biomes="cold and deep cold ocean shores",
            setting=SHORE, ground="gravel", under="stone", palette=P,
            blurb="Grey gravel and mossy stone, ships' timbers and dried kelp thrown up by the storms.")


def rocks(g, n, x1, z1, x2, z2, y=-1):
    """Mossy boulders on the sea floor in the shallows (in the world's own water)."""
    line = shore_line(g.D)
    for _ in range(n):
        x, z = g.rng.randint(x1, x2), g.rng.randint(z1, min(z2, line - 1))
        if g.get(x, y, z) is None and g.get(x, 0, z) is None:
            g.set(x, y, z, Mix(("mossy_cobblestone", 2), ("stone", 1), ("andesite", 1), ("prismarine", 0.5)))
            g.set(x, y - 1, z, "stone")
            if g.chance(0.3) and g.get(x, 0, z) is None:
                g.set(x, 0, z, "mossy_cobblestone")


def dress(g, count=8):
    def place(x, y, z):
        r = g.rng.random()
        if r < 0.3:
            g.set(x, y + 1, z, slab("cobblestone_slab"))
        elif r < 0.55:
            g.set(x, y + 1, z, "dried_kelp_block")
        elif r < 0.75:
            g.set(x, y + 1, z, slab("spruce_slab"))
        else:
            g.set(x, y + 1, z, "mossy_cobblestone")
        return True
    scatter_on_floor(g, count, place, y_max=1)


def bollard(g, x, z, y=1):
    g.set(x, y, z, "stone_bricks")
    g.set(x, y + 1, z, "cobblestone_wall")


@layout("wreck_ribs", "Wreck Ribs", size=(25, 18, 25),
        blurb="A ship's broken hull beached in the gravel, the frame it carried standing between its ribs.")
def wreck_ribs(g, p):
    """A ship heeled over on the shore, its bow in the surf: the keel, ribs of dark timber curving up the high side
    and snapped short on the low, a few strakes of planking, its deck torn open where the frame stands in its belly;
    its mast lies across the beach, a rod still on its top."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.0, cz=18, under="stone", rx=11.0, rz=6.5)
    kx = mid
    z0, z1 = 1, 15

    def half_width(z):
        t = (z - z0) / (z1 - z0)
        return 2.0 + 3.6 * math.sin(math.pi * min(1.0, t * 1.25)) ** 0.7

    # the keel, sunk half into the gravel
    for z in range(z0, z1 + 1):
        g.set(kx, 0, z, "stripped_dark_oak_log[axis=z]")
        g.set(kx, -1, z, "stone")
        if z < line:
            sea_floor(g, kx, z, kx, z, "stone", y_top=-1, y_bot=-3)
    # the ribs every 2 blocks: high on the west (the ship heels east), snapped short on the east
    for z in range(z0 + 1, z1, 2):
        w = half_width(z)
        hw = int(round(w))
        g.line((kx, 0, z), (kx - hw, 2, z), "dark_oak_planks")
        hi = 6 - (1 if z in (z0 + 1, z1 - 2) else 0) - (z % 3 == 0)
        g.line((kx - hw, 2, z), (kx - hw - 1, hi, z), "stripped_spruce_log[axis=y]")
        short = 1 + int(2.5 * g.noise3_at(3.0, kx, 0, z))
        g.line((kx, 0, z), (kx + hw, min(2, short), z), "dark_oak_planks")
        if short > 2:
            g.set(kx + hw, 3, z, "stripped_spruce_log[axis=y]")
        if z < line:
            for x in (kx - hw, kx + hw):
                sea_floor(g, x, z, x, z, "stone", y_top=-1, y_bot=-3)
    # strakes of planking along the high side, broken through in places; one strake left on the low side
    for z in range(z0 + 1, z1):
        hw = int(round(half_width(z)))
        for y in range(1, 6):
            x = kx - hw - (1 if y >= 3 else 0) + (1 if y == 1 else 0)
            if g.noise3_at(3.0, x, y, z) > 0.3 + 0.08 * y and g.get(x, y, z) is None:
                g.set(x, y, z, "dark_oak_planks" if y % 2 else "spruce_planks")
        if g.noise3_at(3.0, kx + hw, 1, z) > 0.4:
            g.set(kx + hw - 1, 1, z, "spruce_planks")
        if z < line:
            sea_floor(g, kx - hw, z, kx + hw, z, "stone", y_top=-2, y_bot=-3)
    # the gunwale along the high side's rib tops, snapped in two places
    for z in range(z0 + 2, z1 - 1):
        hw = int(round(half_width(z)))
        if z not in (6, 7, 12):
            g.set(kx - hw - 1, 6 if z % 3 else 5, z, "stripped_dark_oak_log[axis=z]")
    # the bow's stem post rising out of the surf, and the stern's broken transom
    g.line((kx, 0, z0), (kx, 4, z0 - 1), "stripped_dark_oak_log[axis=y]")
    g.line((kx, 4, z0 - 1), (kx, 8, z0 - 1), "stripped_dark_oak_log[axis=y]")
    g.set(kx, 9, z0 - 1, "waxed_oxidized_cut_copper")
    g.set(kx, 10, z0 - 1, ROD)
    sea_floor(g, kx, z0 - 1, kx, z0 - 1, "stone", y_top=-1, y_bot=-3)
    for x in range(kx - 2, kx + 2):
        for y in range(1, 4 - abs(x - kx + 1)):
            g.set(x, y, z1, "dark_oak_planks")
    # the hold's floor and the frame standing in it, broken at its top corner
    for x in range(kx - 3, kx + 4):
        for z in range(5, 11):
            g.set(x, 0, z, Mix(("spruce_planks", 3), ("dark_oak_planks", 2), ("gravel", 1)) if z >= line else
                  "spruce_planks")
            g.set(x, -1, z, "stone")
    cells = broken_frame(g, kx - 2, 1, 8, [(4, 6), (3, 6), (4, 5), (0, 6)])
    for x, y, z in cells:
        g.set(x + 2, 1, z + 4, FRAME)
    # the ship's lamps hanging from the high ribs
    for z in (5, 11):
        hw = int(round(half_width(z)))
        hang(g, kx - hw - 1 + 1, 5, z, links=1)
    # the mast across the beach: its foot by the hull, its top with a rod and a copper band
    g.line((kx + 4, 1, 12), (kx + 11, 1, 20), "stripped_spruce_log[axis=x]")
    g.set(kx + 11, 1, 21, "waxed_oxidized_cut_copper")
    g.set(kx + 11, 1, 22, "lightning_rod[facing=south]")
    g.set(kx + 10, 2, 20, "light_gray_carpet")
    for x, z in ((kx + 7, 15), (kx + 6, 16), (kx + 8, 17), (kx + 5, 15)):
        if g.empty(x, 1, z):
            g.set(x, 1, z, "white_carpet")
    scorch(g, kx + 9, 19, 2.5)
    rocks(g, 10, 0, 0, g.W - 1, line - 1)
    rod_post(g, kx - 8, 13, 3, shaft=Mix(("mossy_cobblestone", 1), ("cobblestone", 1)))
    dress(g, 8)
    return [guard(mid - 4, 19), guard(mid + 2, 19)]


@layout("anchor_yard", "Anchor Yard", size=(23, 20, 25),
        blurb="A giant's anchor driven fluke-down into the beach, its chain running into the sea behind the frame.")
def anchor_yard(g, p):
    """A ship's anchor three times a man's height stands where the storm drove it into the gravel, its stock across
    its top, its chain hanging from the ring and running back into the sea; the frame on a stone plinth at the
    water's edge, mooring stones round the yard."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.5, cz=15, under="stone")
    # the plinth at the waterline, its back in the sea, the frame on it
    for x in range(mid - 4, mid + 5):
        for z in range(line - 3, line + 3):
            for y in range(-3, 2):
                g.set(x, y, z, p["wall"] if y < 1 else Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2)))
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, line + 3, stairs("stone_brick_stairs", "north"))
    cells = broken_frame(g, mid - 2, 2, line - 1, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, p["rubble"])
    # the anchor, standing at the west of the yard: crown and flukes in the gravel, shank up, stock, ring
    ax, az = 5, line + 4
    # (no iron blocks: the user, November 18, 2026, "too OP"; dark stone that reads as old iron, a little rust)
    iron = Mix(("polished_deepslate", 4), ("smooth_basalt", 2), ("waxed_weathered_cut_copper", 1),
               ("mossy_cobblestone", 0.6), patch=0.6)
    for y in range(-1, 12):
        g.set(ax, y, az, iron)
    # the flukes: arms out east and west from the crown, curling up, half buried
    for side in (-1, 1):
        g.line((ax, 0, az), (ax + side * 2, 1, az), iron)
        g.line((ax + side * 2, 1, az), (ax + side * 3, 3, az), iron)
        g.set(ax + side * 3, 4, az, iron)
        g.set(ax + side * 4, 4, az, iron)
        g.set(ax + side * 4, 5, az, "iron_bars")
    # the stock across the shank near its top (wood, along z), the ring over it
    for z in range(az - 3, az + 4):
        g.set(ax, 10, z, "stripped_dark_oak_log[axis=z]")
    g.set(ax, 12, az - 1, "polished_deepslate")
    g.set(ax, 12, az + 1, "polished_deepslate")
    g.set(ax, 13, az - 1, "polished_deepslate")
    g.set(ax, 13, az + 1, "polished_deepslate")
    g.set(ax, 14, az, "polished_deepslate")
    # its chain: from the ring down its side and away along the gravel into the sea
    for y in range(4, 12):
        g.set(ax + 1, y, az - 1, "chain[axis=y]")
    g.set(ax + 1, 3, az - 1, "polished_deepslate")
    for z in range(az - 2, -1, -1):
        y = 1 if z >= line else -1
        g.set(ax + 1, y, z, "chain[axis=z]")
        if z < line:
            g.set(ax + 1, y - 1, z, "stone")
    g.set(ax + 1, 1, az - 1, "chain[axis=z]")
    g.set(ax + 1, 2, az - 1, "chain[axis=y]")
    g.set(ax + 1, 0, line - 1, "chain[axis=y]")
    # the strike that drove it in: scorch at its foot, a rod on its stock's end
    scorch(g, ax, az, 3.2)
    g.set(ax, 11, az + 3, "waxed_oxidized_cut_copper")
    g.set(ax, 12, az + 3, ROD)
    # the old quay along the waterline, its edge broken, crates and barrels left on it
    for x in range(1, g.W - 1):
        for z in (line, line + 1):
            if g.noise3_at(4.0, x, 0, z) > 0.28:
                g.set(x, 0, z, Mix(("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1)))
                g.set(x, -1, z, "stone")
        if g.noise3_at(4.0, x, 0, line) > 0.4:
            g.set(x, -1, line - 1, "mossy_stone_bricks")
            g.set(x, -2, line - 1, "stone")
    for x, z, b in ((mid + 6, line + 1, "barrel[facing=up]"), (mid + 7, line + 1, "barrel[facing=north]"),
                    (mid + 6, line + 2, "spruce_planks"), (mid + 6, line + 1 - 0, None)):
        if b:
            g.set(x, 1, z, b)
    g.set(mid + 6, 2, line + 1, "barrel[facing=up]")
    # mooring stones and a broken low wall along the east
    for x, z in ((mid + 3, 19), (mid - 2, 21), (g.W - 3, 13)):
        bollard(g, x, z)
    for z in range(line + 1, g.D - 4):
        for y in range(1, 1 + int(1 + 2.5 * g.noise3_at(3.0, g.W - 2, 0, z))):
            g.set(g.W - 2, y, z, p["wall"])
    rod_post(g, mid + 4, line + 1, 3, y0=2, shaft=STORM)
    rocks(g, 10, 0, 0, g.W - 1, line - 1)
    dress(g, 7)
    return [guard(mid - 2, 17), guard(mid + 4, 17)]


@layout("sea_chapel", "Sea Chapel", size=(23, 20, 25),
        blurb="A fishermen's chapel the sea is taking back: its apse in the surf, the frame where its altar was.")
def sea_chapel(g, p):
    """A small stone chapel, its nave on the beach and its round apse standing in the shallows, its walls broken down
    to jagged stumps, the gable at the front still standing with a bell under a broken beam; the frame stands where
    the altar was, the sea come in round it; broken pews of spruce inside."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 8.5, cz=20, under="stone", rx=10.0, rz=5.0)
    x1, x2, z1, z2 = mid - 5, mid + 5, 5, 14
    # the nave's floor and the apse's: stone bricks, the apse's floor awash
    for x in range(x1, x2 + 1):
        for z in range(1, z2 + 1):
            inside = z >= z1 or math.hypot(x - mid, z - z1) <= 5.0
            if inside:
                g.set(x, 0, z, Mix(("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1)))
                for y in range(-3, 0):
                    g.set(x, y, z, "stone")
    # its walls: the nave's sides, the apse's half circle, the front gable wall with its door
    for z in range(z1, z2 + 1):
        for y in range(1, 9):
            g.set(x1, y, z, p["wall"])
            g.set(x2, y, z, p["wall"])
    for k in range(40):
        a = math.pi * k / 39
        x = int(round(mid + math.cos(a) * 5.0))
        z = int(round(z1 - math.sin(a) * 4.5))
        for y in range(-3, 9):
            g.set(x, y, z, p["wall"] if y >= 0 else "stone")
    for x in range(x1, x2 + 1):
        top = 9 + (5 - abs(x - mid))
        for y in range(1, top):
            g.set(x, y, z2, p["wall"])
    for y in range(1, 5):
        for x in range(mid - 1, mid + 2):
            g.set(x, y, z2, AIR, force=True)
    g.set(mid, 5, z2, AIR, force=True)
    # the walls knocked down to jagged stumps, the gable left standing
    g.ruin_tops(x1, 0, x2, z2 - 1, 1, 9, 0.5, 7.5, scale=4.0, salt=11)
    g.smash(x2, 5, 9, 2.8, jag=0.5)
    g.smash(mid + 3, 12, z2, 2.4, jag=0.5)
    g.settle_rubble(0.6, 1.6, p["rubble"], protect=lambda x, z: z > z2)
    # the bell under a broken beam at the gable's top
    for y in range(1, 4):
        g.set(mid, 13 - y + 1, z2 - 1, AIR, force=True)
    g.set(mid, 13, z2 - 1, "spruce_planks")
    g.set(mid, 12, z2 - 1, "bell[attachment=ceiling,facing=south]")
    # the sea come into the apse: water over its floor, the frame standing in it where the altar was
    for x in range(mid - 4, mid + 5):
        for z in range(1, z1 + 1):
            if math.hypot(x - mid, z - z1) <= 3.9 and g.get(x, 0, z) is not None and "wall" not in str(g.get(x, 0, z)):
                g.set(x, 0, z, "water")
                g.set(x, -1, z, "gravel")
    for x in range(mid - 2, mid + 3):
        g.set(x, 0, z1 - 1, "stone_bricks")
    cells = broken_frame(g, mid - 2, 1, z1 - 1, [(4, 6), (3, 6), (4, 5), (2, 6)])
    rubble(g, cells, p["rubble"])
    # the altar's step and two sea lanterns on its ends, a rod on the apse's tallest stump
    for x in range(mid - 3, mid + 4):
        g.set(x, 0, z1, "polished_andesite")
    for x in (mid - 3, mid + 3):
        g.set(x, 1, z1, "chiseled_stone_bricks")
        g.set(x, 2, z1, "sea_lantern")
    # broken pews of spruce down the nave
    for z in range(z1 + 3, z2 - 1, 2):
        for x in list(range(x1 + 1, mid - 1)) + list(range(mid + 2, x2)):
            if g.noise3_at(3.0, x, 1, z) > 0.35 and g.empty(x, 1, z):
                g.set(x, 1, z, stairs("spruce_stairs", "north"))
    # a rod on the tallest stump of the apse
    best = max(((g.top_y(x, z), x, z) for x in range(x1, x2 + 1) for z in range(0, z1)
                if g.get(x, g.top_y(x, z), z) and "water" not in str(g.get(x, g.top_y(x, z), z))))
    g.set(best[1], best[0] + 1, best[2], ROD)
    rocks(g, 10, 0, 0, g.W - 1, line - 1)
    scorch(g, mid + 6, 20, 2.4)
    dress(g, 6)
    return [guard(mid - 4, 19), guard(mid + 4, 19)]


@layout("breakwater", "Breakwater", size=(25, 18, 25),
        blurb="A curving breakwater of mossy stone runs out into the sea; kelp dries on racks on the beach.")
def breakwater(g, p):
    """A breakwater of great mossy blocks curves out from the beach into the sea, a broken storm beacon on its end;
    the frame lies toppled on the shingle at its root, one side still upright; racks of dried kelp on spruce frames
    stand along the beach."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.0, cz=17, under="stone", rx=11.0, rz=6.5)
    # the breakwater: a thick curve from the beach's east out round to the north-west, 3 wide, 2 high
    pts = []
    for k in range(60):
        t = k / 59
        a = math.pi * (0.02 + 0.74 * t)
        pts.append((mid + 1 + math.cos(a) * 9.5, line + 3 - math.sin(a) * 9.0))
    for cx, cz in pts:
        for x in range(int(cx) - 1, int(cx) + 2):
            for z in range(int(cz) - 1, int(cz) + 2):
                if 0 <= x < g.W and 0 <= z < g.D and math.hypot(x - cx, z - cz) <= 1.6:
                    top = 1 + (1 if g.noise3_at(3.0, x, 0, z) > 0.4 else 0) + (1 if z < line - 3 else 0)
                    for y in range(-3, top + 1):
                        g.set(x, y, z, Mix(("mossy_cobblestone", 2), ("cobblestone", 1), ("stone", 2),
                                           ("mossy_stone_bricks", 1), patch=0.6) if y >= -1 else "stone")
    # a gap the sea broke through
    for x in range(g.W):
        for z in range(g.D):
            if math.hypot(x - pts[33][0], z - pts[33][1]) < 1.6:
                for y in range(0, 4):
                    g.set(x, y, z, AIR, force=True) if y > 0 else g.clear(x, y, z)
                g.clear(x, -1, z)
    # the beacon on its end: a stump of deepslate, its copper cage, a sea lantern and rods
    bx, bz = int(round(pts[-1][0])), int(round(pts[-1][1]))
    for dx in (0, 1):
        for dz in (0, 1):
            for y in range(-3, 4 + (dx + dz)):
                g.set(bx + dx, y, bz + dz, DEEP if y >= 0 else "stone")
    g.set(bx, 5, bz, "sea_lantern")
    g.set(bx, 6, bz, ROD)
    g.set(bx + 1, 6, bz + 1, "waxed_oxidized_cut_copper")
    g.set(bx + 1, 7, bz + 1, ROD)
    # the frame toppled on the shingle at the breakwater's root, its west side still standing
    flat_frame(g, mid - 4, 1, line + 1, lambda i, j: not (i == 4 and j in (0, 1, 2)) and not (j == 0 and i == 3),
               along="x")
    for y in (2, 3, 4):
        g.set(mid - 4, y, line + 1, FRAME, force=True)
    g.set(mid - 4, 5, line + 1, "waxed_oxidized_cut_copper")
    # racks of dried kelp: spruce posts, a beam, kelp blocks hung over it, along the west of the beach
    for rz in (14, 18):
        for x in (1, 4):
            for y in (1, 2):
                g.set(x, y, rz, "spruce_fence")
        for x in range(1, 5):
            g.set(x, 3, rz, "stripped_spruce_log[axis=x]")
        for x in (2, 3):
            if g.chance(0.8):
                g.set(x, 2, rz, "dried_kelp_block")
    for x, z in ((2, 21), (3, 21), (2, 22)):
        g.set(x, 1, z, "dried_kelp_block")
    g.set(2, 2, 21, "dried_kelp_block")
    for x, z in ((mid + 5, 20), (mid + 8, 13)):
        bollard(g, x, z)
    rod_post(g, mid + 2, line + 1, 2, shaft="mossy_cobblestone")
    rocks(g, 12, 0, 0, g.W - 1, line - 1)
    scorch(g, mid + 4, 18, 2.6)
    dress(g, 6)
    return [guard(mid - 3, 17), guard(mid + 3, 18)]


@layout("capsized_hull", "Capsized Hull", size=(25, 18, 25),
        blurb="A ship thrown keel-up across the waterline; the frame stands in the dark under its broken side.")
def capsized_hull(g, p):
    """A ship the storm rolled over and threw up the shore, lying keel-up across the waterline like a great dark
    roof; its landward side is stove in, and in the shadow under the hull stands the frame, lanterns still hanging
    from the deck above it; rods stand along the copper-sheathed keel."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.0, cz=19, under="stone", rx=11.5, rz=6.0)
    c = line - 1
    x0, x1 = 2, g.W - 3

    def half_width(x):
        t = (x - x0) / (x1 - x0)
        return 2.5 + 3.8 * math.sin(math.pi * min(1.0, 0.25 + t * 0.9)) ** 0.6

    def height(x):
        t = (x - x0) / (x1 - x0)
        return 6.5 + 2.5 * math.sin(math.pi * min(1.0, 0.25 + t * 0.9)) ** 0.5

    keel = {}
    for x in range(x0, x1 + 1):
        w, hh = half_width(x), height(x)
        prof = []
        for k in range(-12, 13):
            d = w * k / 12
            prof.append((int(round(hh * (1 - (abs(d) / w) ** 2.2))), int(round(c + d))))
        for (ya, za), (yb, zb) in zip(prof, prof[1:]):
            g.line((x, ya, za), (x, yb, zb), Mix(("dark_oak_planks", 3), ("spruce_planks", 2),
                                                 ("stripped_dark_oak_log[axis=x]", 0.5)))
        for (y, z) in (prof[0], prof[-1]):
            if z < line:
                for yy in range(-3, y):
                    g.set(x, yy, z, "stone")
        keel[x] = int(round(hh)) + 1
        g.set(x, keel[x], c, "stripped_dark_oak_log[axis=x]")
    # the stern's flat transom at the west end, the bow's stem at the east
    w0 = half_width(x0)
    for z in range(int(c - w0), int(c + w0) + 1):
        for y in range(0, int(height(x0) * (1 - (abs(z - c) / w0) ** 2.2)) + 1):
            g.set(x0, y, z, "dark_oak_planks")
    g.line((x1, keel[x1], c), (x1 + 1, 2, c), "stripped_dark_oak_log[axis=y]")
    for z in range(int(c - w0), int(c + w0) + 1):
        if z < line:
            for yy in range(-3, 0):
                g.set(x0, yy, z, "stone")
    # the landward side stove in: a ragged hole, its planks thrown out onto the shingle
    for x in range(mid - 4, mid + 5):
        for z in range(c + 1, c + 8):
            for y in range(0, 8):
                if abs(x - mid) + max(0, y - 4) * 1.4 < 4.6 + g.noise3_at(2.0, x, y, z) * 1.6:
                    s = g.get(x, y, z)
                    if s is not None and y > 0:
                        g.set(x, y, z, AIR, force=True)
    for x, z in ((mid - 3, c + 8), (mid + 2, c + 9), (mid + 4, c + 8), (mid - 1, c + 10), (mid + 5, c + 10)):
        g.set(x, 1, z, g.rng.choice(("dark_oak_slab[type=bottom]", "spruce_planks", "stripped_spruce_log[axis=x]")))
    # under the hull: a floor of shingle and the frame, lanterns hanging from the deck over it
    for x in range(x0 + 1, x1):
        for z in range(int(c - half_width(x)) + 1, int(c + half_width(x))):
            g.set(x, 0, z, Mix(("gravel", 2), ("stone", 2), ("mossy_cobblestone", 1)))
            g.set(x, -1, z, "stone")
            if z < line:
                g.set(x, -2, z, "stone")
    cells = broken_frame(g, mid - 2, 1, c, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 5, mid + 5):
        y = keel[x] - 2
        while g.get(x, y, c) is not None and y > 3:
            y -= 1
        g.set(x, y + 1, c, "dark_oak_planks")
        hang(g, x, y, c, links=1)
    # copper sheathing and rods along the keel
    for x in range(x0 + 1, x1, 4):
        g.set(x, keel[x], c, "waxed_oxidized_cut_copper")
        g.set(x, keel[x] + 1, c, ROD)
    rocks(g, 10, 0, 0, g.W - 1, line - 1)
    scorch(g, mid + 6, 20, 2.4)
    dress(g, 6)
    return [guard(mid - 4, 19), guard(mid + 4, 19)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [wreck_ribs, anchor_yard, sea_chapel]
DROPPED = [breakwater, capsized_hull]
