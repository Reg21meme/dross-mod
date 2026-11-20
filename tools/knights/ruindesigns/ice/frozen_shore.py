"""Ice's frozen shore ruins: snow on sand and gravel, frost-bitten stone, packed ice; ice floes over the dark sea
behind. On a beach: the back of the plot (z < shore_line) is the sea, its surface at y 0."""
import math

from ruinkit import (AIR, FRAME, SHORE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, shore_line, slab, stairs)

FLOE = Mix(("ice", 3), ("packed_ice", 2), patch=0.6)
SHARD = Mix(("packed_ice", 4), ("blue_ice", 2), ("ice", 1), patch=0.55)

P = dict(
    floor=Mix(("snow_block", 4), ("sand", 3), ("gravel", 1), patch=0.85, scale=6.0),
    wall=Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2), ("cobblestone", 1), ("mossy_stone_bricks", 0.5),
             patch=0.45),
    stone=Mix(("stone", 3), ("andesite", 2), ("cobblestone", 1), ("diorite", 0.4), patch=0.5),
    trim="chiseled_stone_bricks",
    rubble=Mix(("cobblestone", 2), ("stone_bricks", 1), ("packed_ice", 1)),
    light="soul_lantern",
)

AREA = Area(element="ice", id="frozen_shore", name="Frozen Shore", biomes="snowy beach, frozen river, frozen ocean",
            setting=SHORE, ground="sand", under="sandstone", palette=P,
            blurb="Snow on sand and gravel, frost-bitten stone and ice floes on the frozen sea.")


# ---------------------------------------------------------------- the area's own pieces

def sea_ice(g, cover=0.75, avoid=None):
    """The sea frozen over: floes of ice at the surface (y 0), open water between them left as the sea."""
    line = shore_line(g.D)
    for x in range(g.W):
        for z in range(line):
            if avoid and avoid(x, z):
                continue
            if g.get(x, 0, z) is not None:
                continue
            if g.noise3_at(3.0, x, 0, z) < cover or z >= line - 2:
                g.set(x, 0, z, FLOE)


def beach(g):
    """The beach's own ground: sand with gravel and snow, a rim of gravel at the water."""
    line = shore_line(g.D)
    for x in range(g.W):
        for z in range(line, g.D):
            if g.get(x, 0, z) is None:
                rim = z < line + 2 and g.noise3_at(2.0, x, 0, z) < 0.6
                g.set(x, 0, z, "gravel" if rim else P["floor"])
                g.set(x, -1, z, "sandstone")


def footing(g, x, z, top, mat):
    """A post down to the sea floor (y -2) up to `top`."""
    for y in range(-2, top + 1):
        g.set(x, y, z, mat)


def dress(g, count=16):
    """Snow on the beach, broken stones and ice lumps, a bone or two from the sea."""
    def place(x, y, z):
        if z < shore_line(g.D):
            return False
        r = g.rng.random()
        if r < 0.65:
            g.set(x, y + 1, z, "snow[layers=%d]" % g.rng.choice((1, 1, 2, 3)))
        elif r < 0.85:
            g.set(x, y + 1, z, Mix(("packed_ice", 1), ("cobblestone", 1)))
        else:
            g.set(x, y + 1, z, "bone_block[axis=x]")
        return True
    scatter_on_floor(g, count, place)


def lamp(g, x, z, h=3, y0=1):
    g.set(x, y0, z, "chiseled_stone_bricks")
    for y in range(y0 + 1, y0 + h):
        g.set(x, y, z, "stone_brick_wall")
    g.set(x, y0 + h, z, "soul_lantern")


# ---------------------------------------------------------------- the layouts

@layout("ice_pier", "Ice Pier", size=(25, 18, 25),
        blurb="A broken pier runs out over the frozen sea to a stone landing where the frame stands.")
def ice_pier(g, p):
    """A plank pier on log piles from the beach out onto the ice, a span of it fallen through; at its end a stone
    landing with the broken frame, soul lanterns on posts, a bollard and chain; floes all round."""
    mid = g.W // 2
    beach(g)
    court(g, p["floor"], 8.5, cz=17, under="sandstone")
    # the landing out in the sea
    for x in range(mid - 4, mid + 5):
        for z in range(1, 6):
            for y in range(-2, 1):
                g.set(x, y, z, p["wall"] if y < 0 else Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2)))
    for x in range(mid - 4, mid + 5):
        g.set(x, 1, 1, "stone_brick_wall")
    cells = broken_frame(g, mid - 2, 1, 3, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the pier: 3 wide, planks at y 0 on piles every 3, a span fallen through near the landing
    for z in range(6, 12):
        for x in range(mid - 1, mid + 2):
            if z in (7, 8) and x != mid - 1:
                continue
            g.set(x, 0, z, "spruce_planks" if x != mid else "spruce_slab[type=double]")
        if z % 3 == 0:
            for x in (mid - 2, mid + 2):
                footing(g, x, z, 2, "spruce_log[axis=y]")
    g.set(mid + 1, -1, 7, "spruce_planks")
    g.set(mid + 1, -2, 8, "spruce_planks")
    # lanterns, a bollard and its chain
    for x, z in ((mid - 4, 4), (mid + 4, 4)):
        lamp(g, x, z, h=3)
    g.set(mid + 3, 1, 2, "stone_brick_wall")
    g.set(mid + 3, 2, 2, "chain[axis=x]")
    g.set(mid - 2, 1, 12, "barrel[facing=up]")
    g.set(mid + 2, 1, 13, "barrel[facing=up]")
    g.set(mid + 2, 2, 13, "snow[layers=2]")
    # old pilings of a second pier, poking through the ice
    for x, z, h in ((3, 3, 1), (3, 6, 2), (6, 4, 0), (g.W - 4, 5, 1), (g.W - 4, 8, 2)):
        footing(g, x, z, h, "spruce_log[axis=y]")
    sea_ice(g, cover=0.55)
    for x, z in ((2, 14), (g.W - 3, 15)):
        mound(g, x, z, 2.5, 1.6, "snow_block", y0=0)
    dress(g, 14)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


@layout("frozen_wreck", "Frozen Wreck", size=(25, 18, 25),
        blurb="A longship frozen into the shore ice, its side stove in; the frame stands in its hold.")
def frozen_wreck(g, p):
    """A wrecked ship lying along the shore, half on the sand and half locked in the sea ice, its planks stove in on
    the beach side so its ribs show; the frame stands in its hold; its mast lies across the beach, a torn sail."""
    mid = g.W // 2
    beach(g)
    court(g, p["floor"], 8.5, cz=18, under="sandstone")
    cx, cz = mid, 7
    # the hull: half an ellipsoid shell, its keel at -2, open at the top; prow and stern posts rising
    hull = Mix(("dark_oak_planks", 3), ("spruce_planks", 2), patch=0.5)
    g.ellipsoid(cx, 3, cz, 10.0, 5.0, 4.0, hull, shell=1.0, only_above=-2)
    for x in range(g.W):
        for z in range(g.D):
            for y in range(5 if z < 7 else 3, 10):
                if g.get(x, y, z) is not None:
                    g.set(x, y, z, AIR)
    for y in range(-1, 1):
        for x in range(cx - 8, cx + 9):
            for z in range(cz - 3, cz + 4):
                if math.hypot((x - cx) / 9.0, (z - cz) / 3.2) < 1.0:
                    g.set(x, y, z, "spruce_planks" if y == 0 else "dark_oak_planks")
    for k, x in enumerate((cx - 10, cx + 10)):
        s = -1 if k == 0 else 1
        g.line((x, 1, cz), (x + s, 7, cz), "dark_oak_log[axis=y]")
        g.line((x + s, 8, cz), (x + 2 * s, 9, cz), "dark_oak_fence")
    # the beach side stove in: planks gone, the ribs left standing
    for x in range(cx - 5, cx + 7):
        for y in range(1, 3):
            g.set(x, y, cz + 4, AIR, force=True)
            g.set(x, y, cz + 3, AIR, force=True)
    for x in range(cx - 5, cx + 7, 2):
        g.line((x, 0, cz + 4), (x, 3, cz + 3), "dark_oak_fence")
    # the frame in the hold
    cells = broken_frame(g, cx - 2, 1, cz, [(0, 6), (1, 6), (0, 5), (1, 5)])
    rubble(g, cells, p["rubble"])
    # its cargo: barrels and a chest-less crate of planks, frozen in
    for x, z in ((cx - 6, cz - 1), (cx - 5, cz - 1), (cx + 5, cz), (cx - 6, cz + 1)):
        g.set(x, 1, z, "barrel[facing=up]")
    g.set(cx + 6, 1, cz - 1, SHARD)
    g.set(cx + 6, 2, cz - 1, "snow[layers=3]")
    # the mast fallen across the beach, its sail torn on the sand
    g.line((cx - 2, 2, cz + 2), (cx - 9, 1, cz + 12), "spruce_log[axis=z]")
    for x, z in ((cx - 8, cz + 11), (cx - 9, cz + 10), (cx - 7, cz + 12), (cx - 10, cz + 12), (cx - 8, cz + 13)):
        g.set(x, 1, z, "white_carpet")
    # the sea ice round the hull, packed up against it
    sea_ice(g, cover=0.8)
    for x in range(g.W):
        for z in range(shore_line(g.D)):
            if g.get(x, 0, z) is None:
                continue
            d = math.hypot((x - cx) / 11.0, (z - cz) / 5.0)
            if 1.0 < d < 1.2 and g.empty(x, 1, z):
                g.set(x, 1, z, SHARD)
    lamp(g, cx + 9, 14, h=2)
    dress(g, 14)
    return [guard(mid - 3, 17), guard(mid + 3, 17)]


@layout("sea_stack", "Sea Stack", size=(25, 22, 25),
        blurb="A rock stack in the frozen sea, the frame on a ledge cut into it, stepping stones across the ice.")
def sea_stack(g, p):
    """A tall stack of grey rock, snow on its top, standing out in the frozen sea; a ledge cut into its beach side
    holds the frame; a causeway of stepping stones crosses the ice to it; a smaller stack and a fallen arch beside."""
    mid = g.W // 2
    beach(g)
    court(g, p["floor"], 8.5, cz=18, under="sandstone")
    sx, sz = mid + 1, 4
    for y in range(-2, 14):
        r = 4.4 - max(0, y - 8) * 0.45 + (g.noise3_at(3.0, 0, y + 2, 0) - 0.5) * 1.2
        g.disc(sx, sz, max(r, 1.2), y, p["stone"])
    for x in range(g.W):
        for z in range(10):
            y = g.top_y(x, z)
            s = g.get(x, y, z)
            if y >= 6 and s is not None and g.empty(x, y + 1, z):
                g.set(x, y, z, "snow_block")
                if g.rng.random() < 0.5:
                    g.set(x, y + 1, z, "snow[layers=2]")
    # the ledge: cut back into the stack's front, 1 up, the frame on it
    for z in range(sz - 1, sz + 7):
        hw = 3 + max(0, z - sz) * 0.8
        for x in range(int(sx - hw), int(sx + hw) + 1):
            for y in range(1, 10 + max(0, z - sz)):
                g.set(x, y, z, AIR, force=True)
            g.set(x, 0, z, Mix(("stone_bricks", 2), ("cracked_stone_bricks", 1)))
            for y in range(-2, 0):
                g.set(x, y, z, p["stone"])
    cells = broken_frame(g, sx - 2, 1, sz, [(4, 6), (3, 6), (4, 5), (2, 6)])
    rubble(g, cells, p["rubble"])
    for x in (sx - 3, sx + 3):
        g.set(x, 1, sz - 1, "soul_lantern")
    # the causeway: stepping stones across the ice
    for x, z in ((mid, 9), (mid - 1, 8), (mid, 7), (mid + 1, 10)):
        footing(g, x, z, 0, Mix(("stone", 2), ("cobblestone", 1)))
        g.set(x, 1, z, slab("cobblestone_slab") if (x + z) % 2 else "snow[layers=1]")
    # a smaller stack on the left and the stump of a fallen arch between them
    for y in range(-2, 7):
        g.disc(4, 3, 2.2 - max(0, y - 3) * 0.4, y, p["stone"])
    g.set(4, 7, 3, "snow_block")
    g.line((6, 3, 3), (8, 5, 4), p["stone"], r=0.7)
    sea_ice(g, cover=0.6)
    lamp(g, mid - 6, 13, h=3)
    lamp(g, mid + 6, 13, h=2)
    dress(g, 14)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


@layout("beached_berg", "Beached Berg", size=(25, 22, 25),
        blurb="An iceberg run aground on the beach, the frame frozen into its face; shards of it strewn on the sand.")
def beached_berg(g, p):
    """A great jagged iceberg, blue at its heart, driven up over the shore line; the broken frame is frozen into a
    hollow in its beach face, its posts locked in blue ice; slabs that broke off it lie tilted on the sand."""
    mid = g.W // 2
    beach(g)
    court(g, p["floor"], 8.0, cz=18, under="sandstone")
    # the berg: an ellipsoid with a jagged top, packed ice outside and blue ice inside
    cx, cz = mid, 5
    g.ellipsoid(cx, 2, cz, 11.0, 12.0, 5.5, SHARD, only_above=-2)
    g.ellipsoid(cx, 3, cz, 7.0, 8.0, 3.0, "blue_ice", only_above=1)
    g.ruin_tops(0, 0, g.W - 1, 11, 4, 16, 5.0, 15.0, scale=3.0, salt=11)
    # the hollow in its face and the frame frozen into it
    for x in range(cx - 4, cx + 5):
        for z in range(cz + 1, 12):
            for y in range(1, 10):
                if math.hypot((x - cx) / 4.6, (y - 1) / 8.8) < 1.0:
                    g.set(x, y, z, AIR, force=True)
            g.set(x, 0, z, Mix(("packed_ice", 2), ("snow_block", 1)))
    cells = broken_frame(g, cx - 2, 1, cz + 3, [(4, 6), (4, 5), (3, 6)])
    for y in range(1, 5):
        g.set(cx - 3, y, cz + 3, "blue_ice")
    rubble(g, cells, p["rubble"])
    for x in (cx - 3, cx + 3):
        g.set(x, 1, cz + 5, "soul_lantern")
    # slabs broken off it, tilted on the sand, and lumps at the water's edge
    for (x1, z1), (x2, z2) in (((2, 12), (5, 16)), ((g.W - 3, 11), (g.W - 6, 15))):
        g.line((x1, 3, z1), (x2, 1, z2), SHARD, r=0.8)
    g.rubble_pile(mid - 8, 18, 1.6, 1.2, SHARD)
    sea_ice(g, cover=0.6)
    dress(g, 14)
    return [guard(mid - 3, 17), guard(mid + 3, 17)]


@layout("sea_steps", "Sea Steps", size=(25, 18, 25),
        blurb="A broad stair runs down from the beach into the frozen sea; the frame stands on its drowned landing.")
def sea_steps(g, p):
    """Old harbour steps: a wide stair between stone cheeks runs down from the beach to a landing at the sea's
    surface, out on the ice; the frame stands on the landing, its foot in the ice; broken lamp posts line the
    cheeks; a toppled statue's head lies on the beach."""
    mid = g.W // 2
    beach(g)
    court(g, p["floor"], 8.5, cz=18, under="sandstone")
    line = shore_line(g.D)
    # the beach raised a step into a stone terrace along the shore, its edge the top of the stair
    for x in range(1, g.W - 1):
        for z in range(line, line + 4):
            g.set(x, 1, z, Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2), ("snow_block", 1)))
    g.ruin_tops(1, line, g.W - 2, line + 3, 1, 1, 0.0, 1.6, scale=3.0, salt=13,
                region=lambda x, z: abs(x - mid) > 6)
    # the stair cut down through the terrace's edge into the sea, 7 wide, to a landing under the water
    for k, z in enumerate((line + 1, line, line - 1, line - 2)):
        y = 1 - k
        for x in range(mid - 3, mid + 4):
            for yy in range(-2, y):
                g.set(x, yy, z, p["wall"])
            g.set(x, y, z, stairs("stone_brick_stairs", "south"))
            for yy in range(y + 1, 3):
                g.set(x, yy, z, AIR, force=True)
    # the drowned landing: its top at y -1, the sea standing over it (left unset), the frame's foot in the water
    for x in range(mid - 5, mid + 6):
        for z in range(1, line - 2):
            for y in range(-2, 0):
                g.set(x, y, z, p["wall"] if y < -1 else Mix(("stone_bricks", 2), ("cracked_stone_bricks", 1)))
    # the cheeks either side, from the landing's back up onto the terrace
    for x in (mid - 4, mid + 4):
        for z in range(1, line + 4):
            for y in range(-2, 3 if z >= line - 2 else 1):
                g.set(x, y, z, p["wall"])
    g.ruin_tops(mid - 4, 1, mid + 4, line - 3, 0, 1, -1.0, 1.0, scale=2.0, salt=17)
    cells = broken_frame(g, mid - 2, 0, 3, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    # broken lamp posts along the cheeks and the landing
    for x, z, h in ((mid - 4, line + 3, 3), (mid + 4, line + 3, 2), (mid - 4, 2, 3), (mid + 4, 2, 2)):
        base = 3 if z >= line else 1
        g.set(x, base, z, "chiseled_stone_bricks")
        for y in range(base + 1, base + h):
            g.set(x, y, z, "stone_brick_wall")
        if h >= 3:
            g.set(x, base + h, z, "soul_lantern")
    # two harbour light columns out on the landing's corners, one snapped off
    for x, h in ((mid - 5, 8), (mid + 5, 4)):
        for y in range(-1, h):
            g.set(x, y, line - 3, "stone_bricks" if y % 3 else "chiseled_stone_bricks")
        if h >= 6:
            g.set(x, h, line - 3, "chiseled_stone_bricks")
            g.set(x, h + 1, line - 3, "soul_campfire[lit=true]")
    g.line((mid + 6, 1, line - 2), (mid + 9, 1, line + 1), "stone_bricks", r=0.6)
    # a toppled statue of a soldier on the beach: its body, its head rolled off, its stone spear
    g.box(2, 1, line + 6, 7, 2, line + 7, Mix(("stone_bricks", 2), ("mossy_stone_bricks", 1)))
    g.box(9, 1, line + 5, 11, 3, line + 7, "chiseled_stone_bricks")
    g.set(10, 2, line + 8, "stone_button[face=wall,facing=south]")
    g.line((1, 1, line + 9), (8, 1, line + 10), "stone_brick_wall")
    for x, l in ((3, 2), (5, 1), (10, 3)):
        g.set(x, 4 if x == 10 else 3, line + 6, "snow[layers=%d]" % l)
    sea_ice(g, cover=0.65, avoid=lambda x, z: abs(x - mid) <= 4 and z < line - 2)
    dress(g, 14)
    return [guard(mid - 3, 17), guard(mid + 3, 17)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [ice_pier, beached_berg, sea_steps]
DROPPED = [frozen_wreck, sea_stack]
