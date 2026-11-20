"""Earth's cavern ruins (big caves underground, now and then): the showcase walls each in with rock and a roof two
over its top, open at the front; the ruin is the cave's floor and what stands on it. Deepslate and tuff, dripstone
columns and pointed dripstone, amethyst, moss and glow lichen, lit by lanterns so it reads in the dark."""
import math

from ruinkit import AIR, CAVERN, FRAME, Area, Mix, broken_frame, court, flat_frame, guard, layout, rubble, slab, stairs

from .earthkit import boulder, crystal_spike, geode_buds, lichen_on_walls, stalactite, stalagmite

P = dict(
    floor=Mix(("deepslate", 3), ("cobbled_deepslate", 2), ("tuff", 2), ("stone", 1), patch=0.6),
    wall=Mix(("deepslate_bricks", 3), ("cracked_deepslate_bricks", 2), ("deepslate_tiles", 1), ("cobbled_deepslate", 1),
             patch=0.45),
    rock=Mix(("deepslate", 4), ("tuff", 2), ("cobbled_deepslate", 1), ("stone", 1), patch=0.6, scale=3.0),
    rubble=Mix(("cobbled_deepslate", 2), ("tuff", 1), ("deepslate_bricks", 1)),
)

AREA = Area(element="earth", id="caverns", name="Caverns", biomes="big caves underground (a chance there)",
            setting=CAVERN, ground="deepslate", under="deepslate", palette=P,
            blurb="Dripstone halls, crystal grottoes and carved pillar halls deep underground.")


def cave_floor(g, mat=None, rough=True):
    """The cave's own floor over the whole plot, a little uneven (a step up here and there)."""
    mat = mat or P["floor"]
    for x in range(g.W):
        for z in range(g.D):
            g.set(x, 0, z, mat)
            g.set(x, -1, z, "deepslate")
            if rough and g.noise3_at(3.0, x, 2, z) > 0.82 and 0 < x < g.W - 1 and z < g.D - 3:
                g.set(x, 1, z, P["rock"])


def column(g, cx, cz, top, r_foot=2.2, r_mid=1.1, r_top=2.0, mat="dripstone_block", y0=1):
    """A great column from the floor to the top of the ruin, flared at its foot and its head like an hourglass
    (where a stalagmite met a stalactite); pointed dripstone hanging under its head."""
    for y in range(y0, top + 1):
        t = (y - y0) / max(top - y0, 1)
        r = r_mid + (r_foot - r_mid) * (1 - t) ** 3 + (r_top - r_mid) * t ** 3
        g.disc(cx, cz, r + (g.noise3_at(2.0, int(cx), y, int(cz)) - 0.5) * 0.6, y, mat)
    # stalactites under the head's rim
    for x in range(int(cx - r_top - 1), int(cx + r_top + 2)):
        for z in range(int(cz - r_top - 1), int(cz + r_top + 2)):
            for y in range(top - 3, top + 1):
                if g.get(x, y, z) and "dripstone_block" in g.get(x, y, z) and g.empty(x, y - 1, z) and g.chance(0.4):
                    stalactite(g, x, y - 1, z, g.rng.choice((1, 2, 2, 3)))
                    break


def floor_lantern(g, x, z, y=1, base="cobbled_deepslate"):
    g.set(x, y, z, base)
    g.set(x, y + 1, z, "lantern")


@layout("dripstone_hall", "Dripstone Hall", size=(25, 18, 25),
        blurb="A forest of dripstone: great columns floor to roof, stalagmites, a still pool; the frame among them.")
def dripstone_hall(g, p):
    """Four great dripstone columns hold up the cave, stalagmites of every height between them, a still pool walled in
    by dripstone at the side; the frame stands among the columns at the back, its top corner snapped off by a fallen
    stalactite."""
    mid = g.W // 2
    top = g.H - g.G - 1
    cave_floor(g, Mix(("deepslate", 2), ("dripstone_block", 2), ("tuff", 1), ("cobbled_deepslate", 1), patch=0.6))
    for cx, cz, rf, rm, rt in ((3.5, 4.5, 2.6, 1.3, 2.4), (g.W - 4.5, 3.5, 2.2, 1.0, 2.6), (2.5, 17.0, 2.0, 1.0, 2.0)):
        column(g, cx, cz, top, rf, rm, rt)
    # a fourth column's broken stump at the front-east, its fallen top lying beside it
    column(g, g.W - 3.0, 18.0, 4, 2.2, 1.4, 1.4)
    g.line((g.W - 4, 1, 15), (g.W - 9, 1, 12), "dripstone_block", r=0.8)
    # the pool on the east side, its rim of dripstone
    px, pz = g.W - 6, 10
    for x in range(px - 3, px + 3):
        for z in range(pz - 2, pz + 3):
            inner = px - 3 < x < px + 2 and pz - 2 < z < pz + 2
            if inner:
                g.set(x, 0, z, "water")
                g.set(x, -1, z, "dripstone_block")
            else:
                g.set(x, 0, z, "dripstone_block")
                g.set(x, 1, z, "dripstone_block" if (x + z) % 3 else AIR)
    # stalagmites between the columns
    for x, z, h in ((7, 9, 5), (5, 12, 3), (mid - 6, 3, 4), (mid + 5, 7, 6), (g.W - 8, 17, 4), (7, 20, 3),
                    (g.W - 6, 21, 3), (mid + 2, 2, 3), (9, 6, 2)):
        stalagmite(g, x, z, h, fat=1 if h >= 5 else 0)
    # the frame among the columns, a stalactite fallen across its broken corner
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, Mix(("dripstone_block", 2), ("cobbled_deepslate", 1)))
    g.line((mid + 3, 1, 5), (mid + 6, 3, 2), "dripstone_block")
    # lanterns standing on rocks, glow lichen on the columns
    for x, z in ((mid - 4, 6), (mid + 4, 7), (6, 15), (g.W - 7, 14)):
        floor_lantern(g, x, z)
    lichen_on_walls(g, 0.12, y_min=1, y_max=top)
    return [guard(mid - 3, 12), guard(mid + 3, 12)]


@layout("crystal_grotto", "Crystal Grotto", size=(25, 18, 25),
        blurb="Amethyst grown wild through the cave: crystal spires, budding floors, the frame half swallowed by it.")
def crystal_grotto(g, p):
    """Spires of amethyst rising out of the floor in clumps, budding patches ringed with calcite and smooth basalt
    across the cave floor; the frame at the back half swallowed by crystal growing over its broken corner."""
    mid = g.W // 2
    top = g.H - g.G - 1
    cave_floor(g)
    # budding patches ringed with calcite and smooth basalt (the geode's layers, flat on the floor)
    for cx, cz, r in ((5, 6, 3.2), (g.W - 5, 9, 2.8), (4, 19, 2.4), (g.W - 4, 20, 2.0), (mid, 3, 2.4)):
        for x in range(int(cx - r - 2), int(cx + r + 3)):
            for z in range(int(cz - r - 2), int(cz + r + 3)):
                d = math.hypot(x - cx, z - cz)
                if d <= r - 1:
                    g.set(x, 0, z, Mix(("amethyst_block", 3), ("budding_amethyst", 1)))
                elif d <= r:
                    g.set(x, 0, z, "calcite")
                elif d <= r + 1:
                    g.set(x, 0, z, "smooth_basalt")
    # crystal spires in clumps
    for x, z, h in ((5, 5, 9), (4, 7, 6), (6, 7, 4), (7, 4, 5), (g.W - 5, 9, 7), (g.W - 4, 10, 4), (g.W - 6, 8, 3),
                    (4, 19, 5), (3, 20, 3), (g.W - 4, 20, 4), (mid + 2, 2, 6), (mid - 2, 2, 4), (mid, 3, top - 2)):
        crystal_spike(g, x, z, h)
        for k in range(1, h // 3 + 1):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                if g.chance(0.75 if k == 1 else 0.4):
                    g.set(x + dx, k, z + dz, "amethyst_block")
    # a great crystal column floor to roof on the west, calcite at its foot
    for y in range(1, top + 1):
        r = 1.6 if y < 3 or y > top - 2 else 0.9
        g.disc(2, 12, r, y, Mix(("amethyst_block", 3), ("calcite", 1)) if y > 2 else "calcite")
    # the frame, crystal grown over its broken top corner
    cells = broken_frame(g, mid - 2, 1, 5, [(4, 6), (3, 6), (4, 5), (4, 4)])
    for x, y in ((mid + 2, 4), (mid + 3, 4), (mid + 3, 5), (mid + 2, 5), (mid + 3, 6), (mid + 3, 3), (mid + 3, 2),
                 (mid + 3, 1)):
        if g.get(x, y, 5) is None or g.get(x, y, 5) == "minecraft:air":
            g.set(x, y, 5, "amethyst_block", force=True)
    g.set(mid + 3, 1, 6, "amethyst_block")
    rubble(g, cells, Mix(("amethyst_block", 1), ("cobbled_deepslate", 2)), frame_share=0.3)
    geode_buds(g, 0, 0, 0, g.W - 1, top, g.D - 1, chance=0.3)
    # tuff boulders and lanterns
    boulder(g, mid - 7, 1, 13, 1.4, "tuff")
    boulder(g, mid + 8, 1, 15, 1.3, "tuff")
    for x, z in ((mid - 4, 8), (mid + 5, 7), (mid - 8, 16), (mid + 7, 18)):
        floor_lantern(g, x, z, base="calcite")
    lichen_on_walls(g, 0.06, y_min=1, y_max=6)
    return [guard(mid - 3, 13), guard(mid + 3, 13)]


@layout("pillar_hall", "Pillar Hall", size=(25, 18, 25),
        blurb="A hall carved out of the deepslate long ago: two rows of pillars, lintels, the frame on its dais.")
def pillar_hall(g, p):
    """A carved hall: a tiled floor, two rows of square deepslate pillars floor to roof with chiseled capitals and
    lintels across, lanterns hanging from them on chains; two pillars broken, one fallen across the floor; the frame on
    a stepped dais at the far end, dripstone grown down from the lintels with age."""
    mid = g.W // 2
    top = g.H - g.G - 1
    cave_floor(g, rough=False)
    for x in range(mid - 6, mid + 7):
        for z in range(1, g.D - 1):
            g.set(x, 0, z, Mix(("deepslate_tiles", 3), ("cracked_deepslate_tiles", 2), ("polished_deepslate", 1),
                               patch=0.4))
    rows = (3, 8, 13, 18)
    broken = {(mid + 6, 13): 3, (mid - 6, 18): 0}
    for z in rows:
        for x in (mid - 6, mid + 6):
            h = broken.get((x, z), top)
            for y in range(1, h + 1):
                for dx in (0, 1) if x > mid else (-1, 0):
                    for dz in (0, 1):
                        cap = y >= top - 1
                        g.set(x + dx, y, z + dz, "chiseled_deepslate" if cap or y == 1 else
                              ("polished_deepslate" if (y // 3) % 2 else "deepslate_bricks"))
    # the lintels across the hall, between each pair (but where a pillar broke)
    for z in rows:
        if any(broken.get((x, z), top) < top for x in (mid - 6, mid + 6)):
            continue
        for x in range(mid - 6, mid + 7):
            g.set(x, top, z, "polished_deepslate")
            g.set(x, top, z + 1, "deepslate_tile_slab[type=top]" if 0 < abs(x - mid) < 6 else "polished_deepslate")
        g.set(mid, top - 1, z, "chain[axis=y]")
        g.set(mid, top - 2, z, "lantern[hanging=true]")
        for x in (mid - 3, mid + 3):
            stalactite(g, x, top - 1, z, g.rng.choice((1, 2)))
    # the fallen pillar across the floor and the stump's rubble
    g.line((mid - 7, 1, 17), (mid - 2, 1, 21), "polished_deepslate", r=0.6)
    g.rubble_pile(mid + 7, 13, 2.0, 1.4, p["rubble"])
    # the dais and the frame at the far end
    for x in range(mid - 4, mid + 5):
        for z in range(1, 6):
            g.set(x, 1, z, "polished_deepslate")
        g.set(x, 1, 6, stairs("polished_deepslate_stairs", "north"))
    for x in range(mid - 3, mid + 4):
        for z in range(1, 4):
            g.set(x, 2, z, "deepslate_tiles")
        g.set(x, 2, 4, stairs("deepslate_tile_stairs", "north"))
    cells = broken_frame(g, mid - 2, 3, 2, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 4, mid + 4):
        g.set(x, 2, 4, "chiseled_deepslate")
        g.set(x, 3, 4, "lantern")
    # wall stubs along the sides, carved out of the rock
    for z in range(1, g.D - 2):
        for x in (1, g.W - 2):
            h = 2 + int(g.noise3_at(3.0, x, 0, z) * 4)
            for y in range(1, h + 1):
                g.set(x, y, z, p["wall"])
    lichen_on_walls(g, 0.06, y_min=1, y_max=top)
    return [guard(mid - 3, 11), guard(mid + 3, 11)]


@layout("mossy_grotto", "Mossy Grotto", size=(25, 18, 25),
        blurb="A grotto where moss has taken the cave: a rock arch over the frame hung with glow berries, a pool.")
def mossy_grotto(g, p):
    """Moss over everything, a natural arch of tuff and deepslate across the back with glow berry vines hanging from
    it and spore blossoms under it; the frame standing under the arch; azaleas, a dripleaf pool, mossy boulders."""
    mid = g.W // 2
    top = g.H - g.G - 1
    cave_floor(g, Mix(("moss_block", 4), ("deepslate", 1), ("tuff", 1), ("rooted_dirt", 1), patch=0.65), rough=False)
    # the arch: two legs and a span, rough rock
    rock = Mix(("tuff", 3), ("deepslate", 2), ("mossy_cobblestone", 1), patch=0.5)
    for x in range(g.W):
        for y in range(1, top + 1):
            for z in range(1, 8):
                n = (g.noise3_at(2.0, x, y, z) - 0.5)
                zr = 1.4 + n * 1.6 + (0.8 if y < 4 else 0.0)
                if abs(z - 4) > zr:
                    continue
                outer = ((x - mid) / (9.0 + n * 1.5)) ** 2 + (y / 13.0) ** 2
                inner = ((x - mid) / 4.2) ** 2 + (y / 9.6) ** 2
                if outer <= 1.0 and inner > 1.0:
                    g.set(x, y, z, rock)
    # moss on top of the arch, glow berries and spore blossoms under it
    for x in range(mid - 9, mid + 10):
        for z in range(1, 8):
            ty = g.top_y(x, z)
            if g.get(x, ty, z) and g.empty(x, ty + 1, z):
                g.set(x, ty, z, "moss_block")
    for x in range(mid - 4, mid + 5):
        for z in range(2, 7):
            # the lowest rock cell over the opening
            for y in range(7, 14):
                if g.get(x, y, z) and g.empty(x, y - 1, z):
                    if abs(x - mid) <= 2 and z == 4:
                        break
                    if g.chance(0.3):
                        n = g.rng.randint(1, 3)
                        for k in range(n):
                            last = k == n - 1
                            berries = "true" if g.chance(0.5) else "false"
                            g.set(x, y - 1 - k, z, f"cave_vines[age=10,berries={berries}]" if last
                                  else f"cave_vines_plant[berries={berries}]")
                    elif g.chance(0.12):
                        g.set(x, y - 1, z, "spore_blossom")
                    break
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the dripleaf pool on the west, walled in
    for x in range(2, 8):
        for z in range(11, 16):
            inner = 2 < x < 7 and 11 < z < 15
            g.set(x, 0, z, "water" if inner else "moss_block")
            g.set(x, -1, z, "clay" if inner else "deepslate")
            if inner and (x + z) % 3 == 0:
                g.set(x, 1, z, "lily_pad")
    # big dripleaves on the moss at the pool's edge
    for x, z in ((2, 10), (7, 16), (8, 12)):
        g.set(x, 1, z, "big_dripleaf_stem[facing=east]")
        g.set(x, 2, z, "big_dripleaf[facing=east,tilt=none]")
    # azaleas, moss carpet, mossy boulders, lanterns
    for x, z in ((g.W - 4, 10), (g.W - 3, 18), (4, 20), (g.W - 6, 21)):
        g.set(x, 1, z, "flowering_azalea" if (x + z) % 2 else "azalea")
    boulder(g, g.W - 5, 1, 14, 1.8, rock, top="moss_carpet")
    boulder(g, 3, 1, 4, 1.6, rock, top="moss_carpet")
    for x in range(g.W):
        for z in range(g.D):
            if g.get(x, 1, z) is None and g.chance(0.18) and "moss_block" in (g.get(x, 0, z) or ""):
                g.set(x, 1, z, "moss_carpet")
    for x, z in ((mid - 4, 8), (mid + 4, 8), (g.W - 7, 17)):
        floor_lantern(g, x, z, base="mossy_cobblestone")
    lichen_on_walls(g, 0.12, y_min=1, y_max=top)
    return [guard(mid - 3, 13), guard(mid + 3, 13)]


@layout("rockfall_heap", "Rockfall Heap", size=(25, 18, 25),
        blurb="Where the cave roof came down: a great heap of boulders, the frame thrust up crooked out of its top.")
def rockfall_heap(g, p):
    """A cone of fallen rock piled up at the back where the roof collapsed, boulders of deepslate and tuff rolled out
    round it, stalactites snapped off and lying on the floor; the frame stands out of the heap's shoulder, its foot
    buried; a ramp of scree up to it, lanterns of old miners left in the rubble."""
    mid = g.W // 2
    top = g.H - g.G - 1
    cave_floor(g)
    # the heap: a broad cone of rubble at the back
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - mid) / 10.0, (z - 4) / 7.0)
            if d > 1.0:
                continue
            h = int(round(11.0 * (1 - d) ** 1.1 + (g.noise3_at(2.0, x, 0, z) - 0.5) * 3.0))
            for y in range(1, h + 1):
                g.set(x, y, z, Mix(("cobbled_deepslate", 3), ("tuff", 2), ("deepslate", 2), ("gravel", 1), patch=0.4))
    # big boulders sitting in and round the heap
    for bx, by, bz, r in ((mid - 6, 3, 5, 2.0), (mid + 6, 2, 7, 2.3), (mid + 2, 6, 2, 1.8), (3, 1, 13, 1.7),
                          (g.W - 4, 1, 15, 1.6), (mid - 8, 1, 18, 1.3)):
        boulder(g, bx, by, bz, r, Mix(("deepslate", 3), ("tuff", 2), patch=0.5), sink=0.6)
    # the frame on the heap's shoulder, its foot buried in the rubble, the rubble cleared from its opening
    fy = 5
    for x in range(mid - 3, mid + 4):
        for z in range(5, 8):
            for y in range(fy, fy + 9):
                g.set(x, y, z, AIR, force=True)
    for x in range(mid - 2, mid + 3):
        for y in range(1, fy):
            g.set(x, y, 6, P["rubble"])
    cells = broken_frame(g, mid - 2, fy - 1, 6, [(0, 6), (1, 6), (0, 5), (4, 0)])
    for x in range(mid - 3, mid + 4):
        for z in (5, 7):
            g.set(x, fy - 1, z, Mix(("cobbled_deepslate", 2), ("gravel", 1)))
    rubble(g, cells, P["rubble"])
    # a scree ramp from the floor up to it
    for k in range(fy):
        z = 8 + (fy - 1 - k)
        for x in range(mid - 2, mid + 3):
            for y in range(1, k + 1):
                g.set(x, y, z, "cobbled_deepslate")
            if k > 0:
                g.set(x, k, z, "cobbled_deepslate")
    # snapped stalactites lying on the floor and standing stalagmites at the edges
    g.line((4, 1, 18), (8, 1, 21), "dripstone_block")
    g.line((g.W - 6, 1, 20), (g.W - 3, 1, 22), "dripstone_block")
    for x, z, h in ((2, 8, 4), (g.W - 2, 10, 5), (g.W - 3, 4, 3), (1, 21, 3)):
        stalagmite(g, x, z, h, fat=0)
    # old miners' lanterns and a broken cart rail
    for x, z, y in ((mid - 5, 11, 1), (mid + 5, 11, 1), (mid + 4, 3, 7)):
        ty = g.top_y(x, z)
        floor_lantern(g, x, z, y=ty + 1)
    for z in range(13, g.D - 1):
        if z not in (17, 18):
            g.set(2, 1, z, "rail[shape=north_south]")
    lichen_on_walls(g, 0.08, y_min=1, y_max=top)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [dripstone_hall, crystal_grotto, pillar_hall, mossy_grotto, rockfall_heap]
DROPPED = []
