"""Earth's meadow ruins (meadow, cherry grove): calcite and white stone, cherry trees and pink petals, wildflowers,
a petal-strewn shrine terrace, an amethyst geode cracked open in the hillside."""
import math

from ruinkit import AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, guard, layout, mound, rubble, slab, stairs

from .earthkit import boulder, cherry_tree, flowers, geode_buds

P = dict(
    floor=Mix(("calcite", 4), ("smooth_stone", 1), ("diorite", 1), ("grass_block", 2), patch=0.5),
    wall=Mix(("calcite", 5), ("diorite", 1), ("polished_diorite", 1), ("tuff", 1), patch=0.45),
    stone=Mix(("calcite", 3), ("diorite", 1), ("tuff", 1), patch=0.5),
    rubble=Mix(("calcite", 3), ("diorite", 1), ("tuff", 1)),
)

AREA = Area(element="earth", id="meadow", name="Meadow", biomes="meadow, cherry grove", setting=SURFACE,
            ground="grass_block", under="dirt", palette=P,
            blurb="White calcite shrines among wildflowers and cherry trees, pink petals over everything.")

MEADOW_FLOWERS = ("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "lily_of_the_valley")


def petals_and_flowers(g, n, avoid=None, y_max=6, petals=0.45):
    flowers(g, n, kinds=MEADOW_FLOWERS, tall=0.05, grass=0.25, petals=petals, avoid=avoid, y_max=y_max)


@layout("petal_terrace", "Petal Terrace", size=(25, 16, 25),
        blurb="A shrine's two calcite terraces climbing to the back, the frame on the high one under a cherry tree.")
def petal_terrace(g, p):
    """Two terraces of grass held up by broken calcite walls, a stair up the middle; the lower one is the fight's
    floor, the frame stands on the upper one under a leaning cherry tree, petals drifted everywhere."""
    mid = g.W // 2
    # the lower terrace: one up, its front wall ragged
    for x in range(1, g.W - 1):
        zf = 16 + int(round((g.noise3_at(3.0, x, 0, 0) - 0.5) * 2.5))
        for z in range(1, zf + 1):
            g.set(x, 1, z, p["wall"] if z == zf else "grass_block")
    # the upper terrace: four up
    for x in range(2, g.W - 2):
        zf = 6 + int(round((g.noise3_at(3.0, x, 4, 0) - 0.5) * 2.0))
        for z in range(1, zf + 1):
            for y in range(2, 5):
                g.set(x, y, z, p["wall"] if z == zf or y < 4 else "grass_block")
    # broken calcite parapet along the upper terrace's edge
    for x in range(2, g.W - 2):
        z = 6 + int(round((g.noise3_at(3.0, x, 4, 0) - 0.5) * 2.0))
        if abs(x - mid) > 2 and g.noise3_at(2.5, x, 5, z) > 0.4:
            g.set(x, 5, z, "diorite_wall" if (x % 4) else "calcite")
    # the stairs: up onto the lower terrace and up to the upper one
    for x in range(mid - 1, mid + 2):
        z = 17 + int(round((g.noise3_at(3.0, x, 0, 0) - 0.5) * 2.5))
        g.set(x, 1, z, stairs("diorite_stairs", "north"))
        for k in range(3):
            zz = 7 + int(round((g.noise3_at(3.0, x, 4, 0) - 0.5) * 2.0)) + 2 - k
            for y in range(2, 2 + k):
                g.set(x, y, zz, "calcite")
            g.set(x, 2 + k, zz, stairs("diorite_stairs", "north"))
    # the frame on the upper terrace
    cells = broken_frame(g, mid - 2, 5, 3, [(4, 6), (4, 5), (3, 6), (4, 4)])
    for x in range(mid - 2, mid + 3):
        g.set(x, 4, 3, "polished_diorite")
    rubble(g, cells, p["rubble"])
    # lanterns on calcite posts flanking the frame, pots
    for x in (mid - 4, mid + 4):
        g.set(x, 5, 4, "calcite")
        g.set(x, 6, 4, "lantern")
    g.set(mid - 6, 5, 3, "decorated_pot")
    # the cherry tree leaning over the frame from the east, and a small one at the west
    cherry_tree(g, mid + 7, 3, 7, y0=5, lean=(-1, 0), spread=4.6)
    cherry_tree(g, 3, 12, 5, y0=2, lean=(0, -1), spread=3.4)
    g.weather(0, 1, 0, g.W - 1, 6, g.D - 1, {"calcite": Mix(("calcite", 5), ("mossy_cobblestone", 1), ("tuff", 1))},
              chance=0.25)
    petals_and_flowers(g, 70, petals=0.65, avoid=lambda x, z: abs(x - mid) < 6 and 9 < z < 15)
    return [guard(mid - 4, 12, y=2), guard(mid + 4, 12, y=2)]


@layout("cherry_arbor", "Cherry Arbor", size=(25, 16, 25),
        blurb="A cherry-wood arbor walk overgrown with blossom, leading to the frame; half its beams have fallen.")
def cherry_arbor(g, p):
    """A walk of calcite paving under a pergola of cherry posts and beams, blossom grown over it, from the frame at the
    back to an open lawn in front; some posts snapped, a beam down across the walk, a bell on the last post."""
    mid = g.W // 2
    for z in range(1, 14):
        for x in range(mid - 2, mid + 3):
            g.set(x, 0, z, Mix(("calcite", 3), ("polished_diorite", 1), ("grass_block", 1), patch=0.3))
    # the posts and beams (the arbor from z 5 to 12; the frame stands clear at its far end)
    rows = (5, 8, 11)
    broken = {(mid + 3, 8), (mid - 3, 11)}
    for z in rows:
        for x in (mid - 3, mid + 3):
            h = 2 if (x, z) in broken else 5
            for y in range(1, h + 1):
                g.set(x, y, z, "cherry_log[axis=y]")
            if (x, z) not in broken:
                g.set(x, 1, z, "calcite")
    for z in rows:
        if (mid + 3, z) in broken or (mid - 3, z) in broken:
            continue
        for x in range(mid - 4, mid + 5):
            g.set(x, 6, z, "stripped_cherry_log[axis=x]")
    for z in range(4, 13):
        g.set(mid - 3, 6, z, "cherry_log[axis=z]")
        if z <= 6:
            g.set(mid + 3, 6, z, "cherry_log[axis=z]")
    # blossom in tufts along the beams, a few sprays hanging down the sides
    for x in range(mid - 4, mid + 5):
        for z in range(4, 13):
            s = g.get(x, 6, z)
            if s and "cherry" in s and g.noise3_at(2.0, x, 7, z) > 0.5:
                g.set(x, 7, z, "cherry_leaves[persistent=true]")
                if g.chance(0.3) and g.empty(x, 5, z + 1) and abs(x - mid) >= 3:
                    g.set(x, 7, z + 1, "cherry_leaves[persistent=true]")
    for x, z in ((mid - 4, 6), (mid + 4, 5), (mid - 4, 10), (mid - 2, 12)):
        g.set(x, 6, z, "cherry_leaves[persistent=true]")
        g.set(x, 5, z, "cherry_leaves[persistent=true]")
    # the fallen beam across the walk
    g.line((mid + 3, 2, 8), (mid - 1, 1, 10), "stripped_cherry_log[axis=x]")
    # the frame at the far end of the walk
    cells = broken_frame(g, mid - 2, 1, 2, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 4, mid + 4):
        g.set(x, 1, 3, "calcite")
        g.set(x, 2, 3, "lantern")
    # cherry trees at the corners: one leaning over the frame, one by the lawn
    cherry_tree(g, 3, 3, 7, lean=(1, 0), spread=4.2)
    cherry_tree(g, 2, 17, 5, lean=(1, 0), spread=3.4)
    for x, z in ((mid - 5, 9), (mid + 5, 6)):
        g.set(x, 1, z, stairs("diorite_stairs", "east" if x < mid else "west"))
        g.set(x, 1, z + 1, stairs("diorite_stairs", "east" if x < mid else "west"))
    boulder(g, g.W - 4, 1, 19, 1.6, P["stone"], top="moss_carpet")
    petals_and_flowers(g, 75, petals=0.7, avoid=lambda x, z: abs(x - mid) < 6 and 12 < z < 20)
    return [guard(mid - 4, 15), guard(mid + 4, 15)]


@layout("white_exedra", "White Exedra", size=(25, 18, 25),
        blurb="A half ring of white columns behind the frame, a curved stone bench, columns toppled into the flowers.")
def white_exedra(g, p):
    """Round 1's Fallen Ring as a half ring: calcite columns on an arc round the back, a curved bench inside it, the
    frame standing at its heart; three columns lie toppled out into the meadow."""
    mid, cz = g.W // 2, 10
    court(g, Mix(("calcite", 3), ("polished_diorite", 2), ("diorite", 1), ("grass_block", 2), patch=0.5), 7.5, cz=cz,
          under="dirt", ragged=2.0)
    # the columns: a polished foot, a calcite shaft, a capital; some knocked down to stumps
    for k, ang in enumerate(range(-10, 200, 21)):
        a = math.radians(ang)
        x = int(round(mid + math.cos(a) * 9.0))
        z = int(round(cz - math.sin(a) * 8.0))
        if not (0 < x < g.W - 1 and 0 < z < g.D - 1):
            continue
        h = (8, 3, 7, 0, 8, 2, 8, 0, 6, 4, 8)[k % 11]
        if h == 0:
            continue
        g.set(x, 1, z, "polished_diorite")
        for y in range(2, h + 1):
            g.set(x, y, z, "calcite")
        if h >= 7:
            g.set(x, h + 1, z, "diorite_slab[type=bottom]" if h == 7 else "chiseled_stone_bricks")
    # a lintel running between the three tallest at the back
    for x in range(mid - 5, mid + 3):
        if g.get(x, 9, 2) is None:
            g.set(x, 9, 2, "smooth_stone_slab[type=bottom]")
    # toppled columns lying out in the meadow
    for (x0, z0), (x1, z1) in (((2, 13), (1, 18)), ((g.W - 3, 12), (g.W - 2, 18)), ((mid + 5, 3), (g.W - 2, 1))):
        g.line((x0, 1, z0), (x1, 1, z1), "calcite")
    # the curved bench inside the arc
    for ang in range(15, 170, 9):
        a = math.radians(ang)
        x = int(round(mid + math.cos(a) * 6.5))
        z = int(round(cz - math.sin(a) * 5.8))
        if abs(x - mid) > 3:
            face = "east" if x < mid else "west"
            g.set(x, 1, z, stairs("diorite_stairs", face))
    # the frame at the heart
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (3, 6), (2, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 3, mid + 3):
        g.set(x, 1, 5, "calcite")
        g.set(x, 2, 5, "lantern")
    g.weather(0, 1, 0, g.W - 1, 10, g.D - 1, {"calcite": Mix(("calcite", 6), ("diorite", 1), ("tuff", 1))}, chance=0.3)
    cherry_tree(g, 2, 3, 6, lean=(1, 1), spread=3.8)
    petals_and_flowers(g, 60, petals=0.35, avoid=lambda x, z: abs(x - mid) < 6 and 9 < z < 17)
    return [guard(mid - 4, 12), guard(mid + 4, 12)]


@layout("cracked_geode", "Cracked Geode", size=(25, 18, 25),
        blurb="A great amethyst geode broken open in the hillside, the frame standing among its crystals.")
def cracked_geode(g, p):
    """A grassy knoll at the back, and in it a geode split open to the front: smooth basalt, a calcite layer, an
    amethyst lining bristling with buds; the frame inside, crystal shards spilled out onto the meadow."""
    mid = g.W // 2
    cx, cy, cz = mid, 2.5, 6.5
    # the knoll the geode was buried in
    mound(g, mid, 6, 11.0, 9.0, "grass_block", top="grass_block", y0=0, rz=7.5)
    # the geode's layers, then hollowed
    g.ellipsoid(cx, cy, cz, 7.0, 6.5, 6.0, "smooth_basalt")
    g.ellipsoid(cx, cy, cz, 6.0, 5.5, 5.0, "calcite")
    g.ellipsoid(cx, cy, cz, 5.0, 4.5, 4.0, Mix(("amethyst_block", 4), ("budding_amethyst", 1), patch=0.2))
    g.ellipsoid(cx, cy, cz, 4.0, 3.5, 3.0, AIR)
    # cut open to the front: a jagged break through every layer
    for x in range(g.W):
        for y in range(-2, 12):
            for z in range(g.D):
                cut = 8.6 + (g.noise3_at(2.5, x, y, z) - 0.5) * 3.0 + max(0, y - 6) * -0.4
                if z >= cut and math.hypot(x - cx, (y - cy) * 0.9) < 9.0 and y >= 1:
                    s = g.get(x, y, z)
                    if s is not None:
                        g.set(x, y, z, AIR, force=True)
    # its floor: amethyst, level for the frame
    for x in range(mid - 4, mid + 5):
        for z in range(3, 12):
            if g.get(x, 0, z) is not None or z < 10:
                g.set(x, 0, z, Mix(("amethyst_block", 3), ("calcite", 1)))
    for x in range(mid - 3, mid + 4):
        for z in range(3, 10):
            for y in range(1, 7):
                if math.hypot(x - cx, (y - cy) * 0.8) < 4.2:
                    g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 2, 1, 5, [(4, 6), (3, 6), (4, 5)])
    geode_buds(g, 1, 0, 0, g.W - 2, 12, 12, chance=0.4)
    rubble(g, cells, "amethyst_block", frame_share=0.3)
    # shards of the shell spilled out onto the meadow
    for x, z in ((mid - 6, 13), (mid + 6, 12), (mid - 8, 16), (mid + 7, 17), (mid + 2, 20)):
        g.set(x, 1, z, g.rng.choice(("smooth_basalt", "calcite", "amethyst_block")))
        if g.chance(0.6):
            g.set(x, 2, z, "amethyst_cluster[facing=up]")
    for x in (mid - 5, mid + 5):
        g.set(x, 1, 11, "calcite")
        g.set(x, 2, 11, "lantern")
    petals_and_flowers(g, 45, petals=0.2, y_max=9, avoid=lambda x, z: abs(x - mid) < 6 and 10 < z < 17)
    return [guard(mid - 4, 14), guard(mid + 4, 14)]


@layout("calcite_spring", "Calcite Spring", size=(25, 16, 23),
        blurb="A spring in a calcite basin, an old cherry leaning over it, the frame on the stone step behind.")
def calcite_spring(g, p):
    """A still pool walled in calcite on the west, lily pads and moss on its rim, a stepped stone bank behind it with
    the frame on it, an old cherry tree leaning over the water."""
    mid = g.W // 2
    # the pool: an irregular basin, water two deep, its rim calcite and moss
    px, pz = 6, 8
    cells_w = []
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - px) / 4.6, (z - pz) / 3.6) + (g.noise3_at(3.0, x, 0, z) - 0.5) * 0.35
            if d < 1.0:
                cells_w.append((x, z))
    wset = set(cells_w)
    for x, z in cells_w:
        g.set(x, 0, z, "water")
        g.set(x, -1, z, "water")
        g.set(x, -2, z, "calcite")
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            n = (x + dx, z + dz)
            if n not in wset:
                for y in (-2, -1, 0):
                    g.set(n[0], y, n[1], Mix(("calcite", 3), ("moss_block", 1), ("mossy_cobblestone", 1)))
    for x, z in cells_w:
        if g.chance(0.12):
            g.set(x, 1, z, "lily_pad")
    # the stepped bank across the back, the frame on its top step
    for x in range(mid - 6, g.W - 1):
        for z in range(1, 7):
            h = 3 if z <= 4 else (2 if z == 5 else 1)
            if x < mid - 4:
                h = min(h, 1)
            for y in range(1, h + 1):
                g.set(x, y, z, Mix(("calcite", 4), ("tuff", 1), ("diorite", 1)))
            if h == 3 and g.chance(0.5):
                g.set(x, 3, z, "grass_block")
    g.ruin_tops(mid + 6, 1, g.W - 1, 4, 1, 3, 1.0, 3.2, scale=3.0, salt=4)
    cells = broken_frame(g, mid - 1, 4, 2, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 2, mid + 5):
        g.set(x, 4, 3, "calcite")
        g.set(x, 5, 3, "lantern")
    # the cherry leaning out over the pool
    cherry_tree(g, 2, 3, 7, lean=(1, 1), spread=4.8)
    # mossy stepping stones out of the water and a fallen column by the pool
    g.line((10, 1, 14), (14, 1, 15), "calcite")
    boulder(g, g.W - 4, 1, 15, 1.7, Mix(("calcite", 2), ("mossy_cobblestone", 1)), top="moss_carpet")
    petals_and_flowers(g, 60, petals=0.5, avoid=lambda x, z: (abs(x - mid) < 6 and 9 < z < 18) or (x, z) in wset)
    return [guard(mid - 2, 12), guard(mid + 4, 12)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [petal_terrace, cherry_arbor, cracked_geode, calcite_spring]
DROPPED = [white_exedra]
