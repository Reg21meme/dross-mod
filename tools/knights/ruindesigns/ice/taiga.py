"""Ice's taiga ruins: spruce logs and planks, cobblestone and mossy cobblestone, podzol, ferns and sweet berry bushes,
great mossy boulders and a dusting of snow; old northern halls and shrines the forest has taken back."""
import math

import mc

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, slab, stairs)

LOG = "spruce_log[axis=y]"
LEAVES = "spruce_leaves[persistent=true]"
MOSSY = Mix(("mossy_cobblestone", 3), ("cobblestone", 2), ("mossy_stone_bricks", 1), patch=0.5)
BOULDER = Mix(("mossy_cobblestone", 4), ("cobblestone", 2), ("stone", 1), patch=0.6)

P = dict(
    floor=Mix(("podzol", 4), ("coarse_dirt", 2), ("mossy_cobblestone", 1), ("grass_block", 2), ("cobblestone", 1),
              patch=0.55),
    wall=Mix(("spruce_planks", 4), ("stripped_spruce_log[axis=y]", 1), patch=0.4),
    stone=MOSSY,
    trim="chiseled_stone_bricks",
    rubble=Mix(("mossy_cobblestone", 2), ("cobblestone", 1), ("spruce_planks", 1)),
    light="lantern",
)

AREA = Area(element="ice", id="taiga", name="Taiga",
            biomes="snowy taiga, taiga, old growth pine and spruce taiga", setting=SURFACE, ground="podzol",
            under="dirt", palette=P,
            blurb="Spruce halls and mossy stone among great boulders, ferns and berry bushes, a dusting of snow.")


# ---------------------------------------------------------------- the area's own pieces

def spruce(g, x, z, h, y0=1, r=2.6):
    """A spruce: a straight trunk under rings of leaves narrowing to a point."""
    for y in range(y0, y0 + h):
        g.set(x, y, z, LOG)
    top = y0 + h
    k = 0
    for y in range(y0 + 2, top + 1):
        frac = (y - y0 - 2) / max(h - 1, 1)
        rr = r * (1 - frac) * (0.75 if k % 2 else 1.0)
        if rr >= 0.7:
            g.disc(x, z, rr, y, LEAVES)
        k += 1
    g.set(x, top, z, LEAVES)
    g.set(x, top + 1, z, LEAVES)


def boulder(g, cx, cz, rx, ry, rz, cy=0):
    """A great mossy boulder sunk into the ground, moss on its top."""
    g.ellipsoid(cx, cy, cz, rx, ry, rz, BOULDER, only_above=0)
    for x in range(int(cx - rx - 1), int(cx + rx + 2)):
        for z in range(int(cz - rz - 1), int(cz + rz + 2)):
            if not (0 <= x < g.W and 0 <= z < g.D):
                continue
            y = g.top_y(x, z)
            if y >= 2 and g.get(x, y, z) and "cobble" in g.get(x, y, z) and g.empty(x, y + 1, z):
                if g.rng.random() < 0.5:
                    g.set(x, y, z, "moss_block")
                    if g.rng.random() < 0.4:
                        g.set(x, y + 1, z, "moss_carpet")


def dress(g, count=18, snow=0.2):
    """Ferns, berry bushes and moss on the forest floor, a little snow."""
    def place(x, y, z):
        r = g.rng.random()
        if r < 0.4:
            g.set(x, y, z, "podzol")
            if g.empty(x, y + 2, z) and g.rng.random() < 0.3:
                g.set(x, y + 1, z, "large_fern[half=lower]")
                g.set(x, y + 2, z, "large_fern[half=upper]")
            else:
                g.set(x, y + 1, z, "fern")
        elif r < 0.6:
            g.set(x, y, z, "grass_block")
            g.set(x, y + 1, z, "sweet_berry_bush[age=%d]" % g.rng.choice((2, 3)))
        elif r < 0.75:
            g.set(x, y + 1, z, "moss_carpet")
        elif r < 0.75 + snow:
            g.set(x, y + 1, z, "snow[layers=%d]" % g.rng.choice((1, 1, 2)))
        else:
            g.set(x, y, z, "podzol")
            g.set(x, y + 1, z, "brown_mushroom")
        return True
    scatter_on_floor(g, count, place)


def mc_air(s):
    return mc.is_air(s)


def hanging_lantern(g, x, y, z, links=1):
    """A lantern hanging from the block at (x, y + links + 1, z) on a chain."""
    for k in range(1, links + 1):
        g.set(x, y + k, z, "chain[axis=y]")
    g.set(x, y, z, "lantern[hanging=true]")


# ---------------------------------------------------------------- the layouts

@layout("longhouse", "Longhouse", size=(25, 18, 23),
        blurb="A northern longhouse across the back, its roof fallen in on one end; the frame stands at its hearth.")
def longhouse(g, p):
    """A long spruce hall on a cobblestone footing, its steep roof of spruce stairs whole on the left and caved in on
    the right, its front wall broken open on the frame that stands by the long hearth; crossed gable posts."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=15, under="dirt")
    x0, x1, z0, z1 = 1, g.W - 2, 2, 10
    cz = (z0 + z1) // 2
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, 0, z, Mix(("spruce_planks", 3), ("podzol", 1)) if x0 < x < x1 and z0 < z < z1 else MOSSY)
    # the footing and the walls: posts every 3, planks between
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            g.set(x, 1, z, MOSSY)
            for y in range(2, 5):
                g.set(x, y, z, "spruce_log[axis=y]" if (x - x0) % 3 == 0 else p["wall"])
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            g.set(x, 1, z, MOSSY)
            for y in range(2, 5):
                g.set(x, y, z, "spruce_log[axis=y]" if z in (z0, z1, cz) else p["wall"])
    # the roof: stairs up both slopes to a ridge of logs, the gable ends filled
    rows = cz - z0 + 1
    for k in range(rows):
        y = 5 + k
        for x in range(x0 - 1, x1 + 2):
            g.set(x, y, z0 - 1 + k, stairs("spruce_stairs", "south"))
            g.set(x, y, z1 + 1 - k, stairs("spruce_stairs", "north"))
            if k > 0:
                g.set(x, y - 1, z0 - 1 + k, "spruce_planks")
                g.set(x, y - 1, z1 + 1 - k, "spruce_planks")
        for x in (x0, x1):
            for z in range(z0 + k, z1 - k + 1):
                g.set(x, y, z, p["wall"])
    for x in range(x0 - 1, x1 + 2):
        g.set(x, 5 + rows, cz, "spruce_log[axis=x]")
    # crossed gable posts at the left end
    g.line((x0 - 1, 8, cz - 1), (x0 - 1, 11, cz + 2), "spruce_fence")
    g.line((x0 - 1, 8, cz + 1), (x0 - 1, 11, cz - 2), "spruce_fence")
    # the right end caved in, and the front wall broken out before the frame
    g.smash(mid + 4, 9, cz + 1, 5.0)
    g.smash(x1 - 2, 7, cz, 4.0)
    g.ruin_tops(x1 - 9, z0, x1, z1, 2, 10, 2.0, 6.0, scale=3.0, salt=6)
    for x in range(mid - 5, mid + 6):
        for y in range(1, 9):
            if math.hypot((x - mid) / 5.5, (y - 1) / 7.5) < 1.0:
                for z in (z1, z1 + 1):
                    g.set(x, y, z, AIR, force=True)
    g.settle_rubble(0.3, 1.5, p["rubble"], protect=lambda x, z: z > 11, y_max=4)
    cells = broken_frame(g, mid - 2, 1, cz, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    # the long hearth before it, and lanterns from the beams
    for x in range(mid - 3, mid + 4):
        if abs(x - mid) > 1:
            g.set(x, 0, cz + 2, "cobblestone")
            g.set(x, 1, cz + 2, "campfire[lit=false]" if x % 2 else slab("cobblestone_slab"))
    for x in (x0 + 3, x0 + 6):
        hanging_lantern(g, x, 6, cz, links=2)
    spruce(g, 2, 19, 9)
    spruce(g, g.W - 3, 0, 10)
    boulder(g, g.W - 3, 14, 2.0, 2.0, 2.0)
    dress(g, 16)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


@layout("boulder_gate", "Boulder Gate", size=(25, 20, 25),
        blurb="A great boulder wedged between two others makes a gate in the woods; the frame stands under it.")
def boulder_gate(g, p):
    """Two huge mossy boulders at the back with a third jammed across their tops, an arch of rock; the frame stands
    in the gap beneath; smaller boulders tumbled round, spruces crowding behind, a cairn of mossy stones."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=15, under="dirt")
    boulder(g, mid - 6, 6, 3.6, 5.0, 3.6)
    boulder(g, mid + 6, 5, 4.0, 6.0, 4.0)
    boulder(g, mid, 6, 6.5, 2.2, 3.2, cy=9)
    # clear the gap under the capstone for the frame
    for x in range(mid - 3, mid + 4):
        for z in range(4, 9):
            for y in range(1, 8):
                g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 2, 1, 6, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    for x in range(mid - 3, mid + 4):
        for z in range(4, 9):
            g.set(x, 0, z, Mix(("mossy_stone_bricks", 2), ("cobblestone", 1)))
    # tumbled boulders and a cairn
    for x, z, r in ((2, 13, 1.8), (g.W - 3, 14, 2.2), (4, 2, 2.4), (mid + 9, 19, 1.4)):
        boulder(g, x, z, r, r, r)
    for y, r in ((1, 1.2), (2, 0.6), (3, 0.3)):
        g.disc(mid - 8, 18, r, y, MOSSY)
    g.set(mid - 8, 4, 18, "lantern")
    for x, z, h in ((1, 6, 10), (g.W - 2, 9, 9), (mid + 2, 0, 12), (mid - 3, 0, 11), (1, 22, 7)):
        spruce(g, x, z, h)
    g.set(mid + 4, 1, 10, "lantern")
    g.set(mid - 4, 1, 10, "lantern")
    dress(g, 18)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


@layout("giant_stump", "Giant Stump", size=(25, 20, 25),
        blurb="An old-growth spruce's hollow stump and its fallen trunk; the frame stands in the crook between them.")
def giant_stump(g, p):
    """A spruce of the old growth, broken off: its hollow stump, roots like walls, and the trunk lying where it fell
    across the back; the frame stands in the angle between them on an old stone floor the roots have heaved up."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=15, under="dirt")
    sx, sz = 5, 6
    wood = Mix(("spruce_wood[axis=y]", 3), ("spruce_log[axis=y]", 2), ("stripped_spruce_wood[axis=y]", 0.5), patch=0.4)
    g.cylinder(sx, sz, 3.6, 1, 11, wood, r_in=2.2)
    g.ruin_tops(sx - 5, sz - 5, sx + 5, sz + 5, 3, 11, 4.0, 11.0, scale=2.0, salt=8)
    g.disc(sx, sz, 2.4, 0, "podzol")
    # its roots, out over the ground and down into it
    for ex, ez in ((sx - 4, sz + 6), (sx + 5, sz + 5), (sx - 5, sz - 3), (sx + 2, sz - 5), (sx + 7, sz + 1)):
        g.line((sx, 2, sz), (ex, 1, ez), "spruce_wood[axis=y]")
    # the fallen trunk across the back, 3 thick, its broken end splintered
    for x in range(sx + 3, g.W):
        r = 1.5 if x < g.W - 4 else 1.1
        for y in range(1, 5):
            for z in range(0, 6):
                if math.hypot(y - 2.3, z - 2.5) <= r + 0.3:
                    g.set(x, y, z, "spruce_log[axis=x]")
    for x, z in ((sx + 3, 1), (sx + 3, 3), (sx + 4, 4)):
        g.set(x, 5, z, "spruce_fence")
    for x in range(sx + 6, g.W - 1, 2):
        if g.empty(x, 5, 2):
            g.set(x, 5, 2, "moss_carpet")
    # the frame and its heaved floor
    for x in range(mid - 2, mid + 6):
        for z in range(6, 11):
            g.set(x, 0, z, Mix(("mossy_stone_bricks", 2), ("cracked_stone_bricks", 1), ("podzol", 1)))
    cells = broken_frame(g, mid - 1, 1, 7, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    for x, z in ((mid - 2, 9), (mid + 5, 10)):
        g.set(x, 1, z, "mossy_stone_bricks")
        g.set(x, 2, z, slab("mossy_stone_brick_slab"))
    g.set(mid + 5, 3, 10, "lantern")
    for x, z, h in ((1, 14, 9), (g.W - 2, 22, 7)):
        spruce(g, x, z, h)
    boulder(g, 3, 20, 2.0, 1.8, 2.0)
    dress(g, 18, snow=0.25)
    return [guard(mid - 2, 16), guard(mid + 4, 16)]


@layout("wayshrine", "Wayshrine", size=(23, 22, 25),
        blurb="A steep-roofed spruce shrine on a stone plinth, one corner post gone so its roof slumps; the frame inside.")
def wayshrine(g, p):
    """A northern shrine: a mossy stone plinth with steps, four great posts, a steep two-tiered roof of spruce, its
    front-right post broken so that corner of the roof has slumped to the plinth; the frame stands within, lanterns
    hanging, prayer stones either side of the way up."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=16, under="dirt")
    x0, x1, z0, z1 = mid - 5, mid + 5, 2, 10
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, 1, z, MOSSY if x in (x0, x1) or z in (z0, z1) else Mix(("mossy_stone_bricks", 2), ("stone_bricks", 1)))
    for x in range(mid - 2, mid + 3):
        g.set(x, 1, z1 + 1, stairs("mossy_stone_brick_stairs", "north"))
    # the posts (the front right one snapped short) and the beams on them
    posts = [(x0 + 1, z0 + 1, 8), (x1 - 1, z0 + 1, 8), (x0 + 1, z1 - 1, 8), (x1 - 1, z1 - 1, 3)]
    for x, z, h in posts:
        for y in range(2, 2 + h):
            g.set(x, y, z, "stripped_spruce_log[axis=y]" if y < 2 + h - 1 or h < 8 else LOG)
    for x in range(x0 + 1, x1):
        g.set(x, 10, z0 + 1, "spruce_log[axis=x]")
        g.set(x, 10, z1 - 1, "spruce_log[axis=x]")
    for z in range(z0 + 1, z1):
        g.set(x0 + 1, 10, z, "spruce_log[axis=z]")
        g.set(x1 - 1, 10, z, "spruce_log[axis=z]")
    # the roof: two steep tiers stepping in, a ridge pole with a carved finial
    for t, (y, inset) in enumerate(((11, -1), (12, 0), (13, 1), (14, 2), (15, 3), (16, 4))):
        for x in range(x0 + inset, x1 - inset + 1):
            for z in range(z0 + inset, z1 - inset + 1):
                edge = x in (x0 + inset, x1 - inset) or z in (z0 + inset, z1 - inset)
                if edge:
                    face = "south" if z == z1 - inset else "north" if z == z0 + inset else \
                        "east" if x == x1 - inset else "west"
                    g.set(x, y, z, stairs("spruce_stairs", {"south": "north", "north": "south", "east": "west",
                                                            "west": "east"}[face]))
                else:
                    g.set(x, y, z, "spruce_planks")
    g.set(mid, 17, z0 + 4, LOG)
    g.set(mid, 18, z0 + 4, "spruce_fence")
    # the front-right corner slumped: the roof there knocked out, its planks slid down to the plinth
    g.smash(x1, 12, z1, 4.0)
    g.line((x1 - 1, 9, z1 - 3), (x1 + 1, 2, z1 + 1), "spruce_planks")
    g.line((x1 - 2, 9, z1 - 2), (x1, 2, z1 + 2), stairs("spruce_stairs", "west"))
    # the frame inside, lanterns hanging from the beams
    cells = broken_frame(g, mid - 2, 2, 5, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    hanging_lantern(g, x0 + 2, 8, z0 + 1, links=1)
    hanging_lantern(g, x0 + 2, 8, z1 - 1, links=1)
    # prayer stones either side of the way up
    for x in (mid - 5, mid + 5):
        for z in (14, 18):
            g.set(x, 1, z, "mossy_stone_bricks")
            g.set(x, 2, z, "mossy_stone_brick_wall")
            g.set(x, 3, z, "lantern" if z == 14 else "moss_carpet")
    for x, z, h in ((1, 3, 12), (g.W - 2, 2, 11), (1, 13, 8)):
        spruce(g, x, z, h)
    dress(g, 16)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


@layout("berry_hollow", "Berry Hollow", size=(25, 18, 25),
        blurb="A sunken hollow full of berry bushes; fallen spruces bridge it, the frame half buried in its back.")
def berry_hollow(g, p):
    """A hollow dug into the forest floor, its banks of podzol and mossy stone, thick with berry bushes and ferns;
    two fallen spruces lie across it; the frame is half sunk into its back bank, an old stair down at the front."""
    mid = g.W // 2
    cz = 12
    # the hollow, 3 deep, its edge wobbly; its floor podzol and coarse dirt
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - mid) / 10.5, (z - cz) / 9.5) + (g.noise3_at(3.0, x, 0, z) - 0.5) * 0.25
            if d < 1.0:
                depth = 3 if d < 0.8 else 2 if d < 0.9 else 1
                for y in range(1 - depth, 1):
                    g.set(x, y, z, AIR)
                g.set(x, -depth, z, Mix(("podzol", 3), ("coarse_dirt", 2), ("moss_block", 2), ("rooted_dirt", 1), patch=0.6))
                g.set(x, -depth - 1, z, "dirt")
            elif d < 1.15:
                g.set(x, 0, z, Mix(("mossy_cobblestone", 2), ("podzol", 2), patch=0.5))
    # the hollow's banks faced with old mossy stone where they show
    for x in range(g.W):
        for z in range(g.D):
            if g.get(x, 0, z) is not None and mc_air(g.get(x, 0, z)):
                continue
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = g.get(x + dx, 0, z + dz)
                if n is not None and mc_air(n):
                    for y in range(-3, 1):
                        g.set(x, y, z, Mix(("mossy_cobblestone", 3), ("cobblestone", 1), ("dirt", 2), patch=0.5))
                    break
    # the frame half sunk into the back bank: its foot two below the bank's top
    for x in range(mid - 4, mid + 5):
        for z in range(1, 5):
            for y in range(-3, 1):
                g.set(x, y, z, Mix(("dirt", 2), ("mossy_cobblestone", 1)))
            g.set(x, 1, z, "podzol" if z < 3 else AIR)
    cells = broken_frame(g, mid - 2, -2, 4, [(4, 6), (4, 5), (3, 6), (2, 6)])
    rubble(g, cells, p["rubble"])
    g.set(mid - 4, 2, 2, "lantern")
    g.set(mid + 4, 2, 2, "lantern")
    # the fallen spruces bridging the hollow, root plates up on the banks
    g.line((1, 1, 6), (g.W - 3, 1, 12), "spruce_log[axis=x]")
    g.line((4, 1, 21), (g.W - 2, 1, 16), "spruce_log[axis=x]")
    for y in range(1, 4):
        for z in range(5, 8):
            g.set(0, y, z, Mix(("dirt", 1), ("spruce_wood[axis=y]", 1)))
    # the old stair down at the front, mossy and broken
    for k, z in enumerate((21, 20, 19)):
        for x in range(mid - 1, mid + 2):
            g.set(x, -k, z, stairs("mossy_cobblestone_stairs", "north"))
            for y in range(-3, -k):
                g.set(x, y, z, "cobblestone")
    # thick with berry bushes and ferns on its floor
    for _ in range(60):
        x, z = g.rng.randint(1, g.W - 2), g.rng.randint(1, g.D - 2)
        y = g.top_y(x, z)
        if math.hypot((x - mid) / 10.5, (z - cz) / 9.5) < 0.6:
            continue
        if y in (-3, -2) and g.empty(x, y + 1, z) and g.empty(x, y + 2, z):
            if g.rng.random() < 0.55:
                g.set(x, y, z, "grass_block")
                g.set(x, y + 1, z, "sweet_berry_bush[age=3]")
            else:
                g.set(x, y, z, "podzol")
                g.set(x, y + 1, z, "fern")
    for x, z, h in ((2, 2, 11), (g.W - 3, 3, 12), (1, 18, 8)):
        spruce(g, x, z, h, y0=g.top_y(x, z) + 1)
    boulder(g, 2, 22, 1.8, 1.6, 1.8)
    dress(g, 10)
    return [guard(mid - 3, 14, y=-2), guard(mid + 3, 14, y=-2)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [giant_stump, wayshrine, berry_hollow]
DROPPED = [longhouse, boulder_gate]
