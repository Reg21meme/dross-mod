"""Poison's forest ruins (forest, birch forest, old growth birch forest, flower forest): old stone bricks and oak
beams among birches and oaks in the flowers, and round the frame the poison spreading: dead trees, bare earth,
wither roses."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, slab, stairs)
from ruindesigns.poison.common import (froglamp, giant_mushroom, hang_vines, hanging_froglight, poison_dress, pool,
                                       webs_in_corners)

P = dict(
    floor=Mix(("stone_bricks", 3), ("mossy_stone_bricks", 3), ("cracked_stone_bricks", 1), ("cobblestone", 1),
              patch=0.5),
    wall=Mix(("stone_bricks", 4), ("mossy_stone_bricks", 3), ("cracked_stone_bricks", 2), patch=0.4),
    ground=Mix(("grass_block", 7), ("moss_block", 1), ("coarse_dirt", 0.5), patch=0.55),
    blight=Mix(("coarse_dirt", 3), ("podzol", 3), ("rooted_dirt", 1), ("moss_block", 1), patch=0.5),
    rubble=Mix(("cobblestone", 2), ("mossy_cobblestone", 2), ("stone_bricks", 1)),
    light="verdant_froglight[axis=y]",
)

AREA = Area(element="poison", id="forest", name="Forest",
            biomes="forest, birch forest, old growth birch forest, flower forest", setting=SURFACE,
            ground="grass_block", under="dirt", palette=P,
            blurb="Old stone among birches and flowers, the poison spreading out from the frame.")

FLOWERS = ("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "lily_of_the_valley",
           "red_tulip", "white_tulip", "orange_tulip", "pink_tulip")


def meadow(g, r=12.0, cz=None):
    court(g, P["ground"], r, cz=cz, under="dirt")


def blight_stain(g, cx, cz, r, rz=None):
    """The poison spreading from the frame: bare coarse dirt and podzol in a ragged patch. Returns its test."""
    rz = r if rz is None else rz

    def inside(x, z):
        return math.hypot((x - cx) / r, (z - cz) / rz) <= 1.0 - 0.25 * g.noise3_at(2.5, x, 0, z)
    for x in range(g.W):
        for z in range(g.D):
            if inside(x, z):
                s = g.get(x, 0, z)
                if s is None or "grass" in s or "moss" in s:
                    g.set(x, 0, z, P["blight"])
    return inside


def flowers(g, count, avoid=None, roses_in=None):
    """Flowers on the grass (wither roses, sparingly, where the poison is), grass tufts among them."""
    def place(x, y, z):
        s = g.get(x, y, z)
        base = "grass_block" if s is None else s.split(":")[-1].split("[")[0]
        if base not in ("grass_block", "podzol", "coarse_dirt", "moss_block"):
            return False
        if avoid and avoid(x, z):
            return False
        if roses_in and roses_in(x, z):
            if g.chance(0.35):
                g.set(x, y + 1, z, "wither_rose" if base != "moss_block" else "moss_carpet")
                return True
            if base == "coarse_dirt" and g.chance(0.4):
                g.set(x, y + 1, z, "dead_bush")
                return True
            return False
        if base != "grass_block":
            return False
        g.set(x, y + 1, z, g.rng.choice(FLOWERS) if g.chance(0.7) else "grass")
        return True
    scatter_on_floor(g, count, place)


def birch(g, x, z, h, y0=1, dead=False, crown=2):
    """A birch: a straight white trunk and a narrow crown (dead: stripped, bare, a broken top and a couple of
    bare branches)."""
    if dead:
        top = y0 + h - 2
        for y in range(y0, top + 1):
            g.set(x, y, z, "stripped_birch_log[axis=y]" if g.chance(0.6) else "birch_log[axis=y]")
        for dx, dz in g.rng.sample([(1, 0), (-1, 0), (0, 1), (0, -1)], 2):
            by = g.rng.randint(y0 + 3, top)
            g.line((x, by, z), (x + dx * 2, by + 2, z + dz * 2), "birch_log[axis=x]" if dx else "birch_log[axis=z]")
        return top
    top = y0 + h
    for y in range(y0, top):
        g.set(x, y, z, "birch_log[axis=y]")
    for dy in range(-3, 2):
        r = crown if dy < 0 else crown - 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) == r and abs(dz) == r and g.chance(0.7):
                    continue
                if g.get(x + dx, top + dy, z + dz) is None:
                    g.set(x + dx, top + dy, z + dz, "birch_leaves[persistent=true]")
    return top


def oak(g, x, z, h, spread=3, y0=1):
    """A broad oak: a trunk with a couple of branches and a round crown."""
    top = y0 + h
    for y in range(y0, top):
        g.set(x, y, z, "oak_log[axis=y]")
    for dx, dz in ((1, 0), (-1, 1)):
        g.line((x, top - 3, z), (x + dx * 2, top - 1, z + dz * 2), "oak_log[axis=x]")
    for dx in range(-spread - 1, spread + 2):
        for dz in range(-spread - 1, spread + 2):
            for dy in range(-2, 2):
                d = math.hypot(dx, dz) + abs(dy + 0.5) * 0.9
                if d <= spread + 0.6 - g.rng.random() * 0.8 and g.get(x + dx, top + dy, z + dz) is None:
                    g.set(x + dx, top + dy, z + dz, "oak_leaves[persistent=true]")
    g.set(x, top, z, "oak_log[axis=y]")
    return top


# ---------------------------------------------------------------- 1 Poisoned Grove

@layout("poisoned_grove", "Poisoned Grove", size=(25, 18, 25),
        blurb="A birch grove dying round the frame: bare earth, wither roses and dead birches near it, green further out.")
def poisoned_grove(g, p):
    """The frame on a low cracked plinth at the back; the poison stains the ground in a long tongue out from it; the
    birches standing in it are dead and bare, those at its edge half dead, those beyond green; flowers outside it,
    wither roses inside."""
    mid = g.W // 2
    meadow(g, cz=12)
    stain = blight_stain(g, mid, 9, 7.5, rz=10.0)
    for x in range(mid - 4, mid + 5):
        for z in range(2, 7):
            g.set(x, 1, z, p["floor"] if abs(x - mid) <= 3 and 3 <= z <= 6 else None)
    g.set(mid - 4, 1, 4, p["wall"])
    cells = broken_frame(g, mid - 2, 2, 4, [(4, 6), (3, 6), (4, 5)])
    # the trees: dead in the stain, living outside it
    spots = [(mid - 6, 3, 10), (mid + 6, 2, 11), (mid - 6, 10, 9), (mid + 7, 9, 10), (mid - 8, 6, 8),
             (2, 4, 12), (1, 13, 10), (g.W - 2, 14, 12), (mid - 10, 1, 13)]
    for x, z, h in spots:
        if not (0 <= x < g.W and 0 <= z < g.D):
            continue
        d = math.hypot((x - mid) / 7.5, (z - 9) / 10.0)
        if d < 0.9:
            birch(g, x, z, h, dead=True)
        elif d < 1.25:
            birch(g, x, z, h, crown=1)
        else:
            birch(g, x, z, h)
    oak(g, g.W - 3, 1, 9, spread=3)
    # a fallen dead birch across the stain
    g.line((mid - 7, 1, 14), (mid - 1, 1, 12), "birch_log[axis=x]")
    # froglights sunk in the plinth's front corners, cobwebs in the dead branches
    for x in (mid - 3, mid + 3):
        g.set(x, 1, 7, p["light"])
    for x, z in ((mid - 6, 3), (mid + 6, 2), (mid - 6, 10), (mid + 7, 9)):
        for y in range(4, 9):
            if g.get(x + 1, y, z) is None and g.chance(0.15):
                g.set(x + 1, y, z, "cobweb")
    rubble(g, cells, p["rubble"], spread=3)
    flowers(g, 40, roses_in=stain)
    return [guard(mid - 4, 15), guard(mid + 4, 15)]


# ---------------------------------------------------------------- 2 Manor Corner

@layout("manor_corner", "Manor Corner", size=(25, 20, 25),
        blurb="The last corner of a stone manor: two walls, a fireplace and a sagging upper floor; the frame inside.")
def manor_corner(g, p):
    """Two tall walls of a manor meeting at the back left, their windows gaping, a great fireplace and chimney in the
    back wall; oak joists of the fallen upper floor still caught in the walls, some slanted down to the floor; the
    frame standing in the corner's room, the rest of the house only a low line of stones."""
    mid = g.W // 2
    meadow(g, cz=12)
    X1, X2, Z1, Z2 = 3, g.W - 4, 2, 15
    # the floor: oak planks over stone, broken through to the ground in places
    for x in range(X1, X2 + 1):
        for z in range(Z1, Z2 + 1):
            if g.noise3_at(3.0, x, 0, z) < 0.78:
                g.set(x, 0, z, Mix(("oak_planks", 3), ("stone_bricks", 1), ("mossy_stone_bricks", 1), patch=0.6))
    # the two standing walls: back (z Z1) and left (x X1), tall, broken off in a slope away from the corner
    for x in range(X1, X2 + 1):
        top = 13 - (x - X1) * 0.75
        for y in range(1, int(top) + 1):
            g.set(x, y, Z1, p["wall"])
    for z in range(Z1, Z2 + 1):
        top = 13 - (z - Z1) * 0.8
        for y in range(1, int(top) + 1):
            g.set(X1, y, z, p["wall"])
    g.ruin_tops(X1, Z1, X2, Z2, 3, 14, 2.0, 13.5, scale=2.0, salt=61,
                region=lambda x, z: x > X1 + 2 or z > Z1 + 2)
    # the rest of the house: low broken stumps of its other walls
    for x in range(X1, X2 + 1):
        if g.chance(0.6):
            g.set(x, 1, Z2, p["wall"])
    for z in range(Z1, Z2 + 1):
        if g.chance(0.55):
            g.set(X2, 1, z, p["wall"])
            if g.chance(0.3):
                g.set(X2, 2, z, p["wall"])
    # windows in the walls: tall gaps with a stone sill
    for wx in (X1 + 6, X1 + 11):
        for y in range(3, 7):
            g.set(wx, y, Z1, AIR, force=True)
        g.set(wx, 2, Z1, "chiseled_stone_bricks")
    for wz in (Z1 + 6, Z1 + 10):
        for y in range(3, 6):
            g.set(X1, y, wz, AIR, force=True)
    # the fireplace in the back wall, a chimney breast over it
    FX = X1 + 3
    for x in range(FX - 1, FX + 3):
        for y in range(1, 5):
            g.set(x, y, Z1 + 1, "bricks" if (x in (FX - 1, FX + 2) or y == 4) else AIR)
    g.set(FX, 1, Z1 + 1, "campfire[lit=false]")
    g.set(FX + 1, 1, Z1 + 1, "campfire[lit=false]")
    for x in range(FX - 1, FX + 3):
        g.set(x, 5, Z1 + 1, stairs("brick_stairs", "north"))
    for y in range(1, 15):
        g.set(FX, y, Z1 - 1 if Z1 > 0 else Z1, "bricks")
        g.set(FX + 1, y, Z1 - 1 if Z1 > 0 else Z1, "bricks" if y < 13 else None)
    # the upper floor's joists: oak logs from the back wall forward at y 7, broken short, two slanted down
    for jx in range(X1 + 1, X1 + 10, 2):
        n = g.rng.randint(2, 6)
        for z in range(Z1 + 1, Z1 + 1 + n):
            g.set(jx, 7, z, "oak_log[axis=z]")
    for jz in (Z1 + 3, Z1 + 6):
        for x in range(X1 + 1, X1 + 1 + g.rng.randint(3, 6)):
            g.set(x, 7, jz, "stripped_oak_log[axis=x]")
    g.line((X1 + 11, 7, Z1 + 1), (X1 + 13, 1, Z1 + 5), "oak_log[axis=z]")
    g.line((X1 + 1, 7, Z1 + 9), (X1 + 4, 1, Z1 + 11), "oak_log[axis=x]")
    # the frame in the room, near the corner, facing out
    cells = broken_frame(g, mid - 1, 1, 6, [(4, 6), (3, 6), (4, 5), (0, 6)])
    # birches and an oak round the outside, flowers in the meadow, moss in the house
    birch(g, g.W - 2, 3, 11)
    birch(g, g.W - 2, 9, 9)
    oak(g, 1, 20, 7, spread=3)
    froglamp(g, X1 + 2, Z2 - 1, post="stone_brick_wall", h=1)
    rubble(g, cells, p["rubble"], spread=3)
    g.settle_rubble(0.5, 1.0, p["rubble"], protect=lambda x, z: z >= 12 and abs(x - mid) <= 6)
    hang_vines(g, X1 - 1, Z1 - 1, X2, Z2, chance=0.1, y_lo=3, y_hi=13)
    flowers(g, 25)
    poison_dress(g, 6, webs=0.4, mushrooms=0.2)
    return [guard(mid - 3, 17), guard(mid + 4, 17)]


# ---------------------------------------------------------------- 3 Broken Bridge

@layout("broken_bridge", "Broken Bridge", size=(25, 18, 25),
        blurb="An old humpbacked bridge over a still brook; its middle has fallen in, the frame stands at its far end.")
def broken_bridge(g, p):
    """A brook across the plot, walled in, lily pads and flowers on its banks; a humpbacked stone bridge over it,
    its middle fallen into the water; the frame on a little stone landing at the far end, birches behind."""
    mid = g.W // 2
    meadow(g, cz=12)
    # the brook: across the plot at z 8-10, walled in, closed at its ends
    water = pool(g, 2, 7, g.W - 3, 11, p["wall"], bed="gravel", depth=2,
                 region=lambda x, z: abs(z - 9 - 0.8 * math.sin(x / 3.5)) <= 1.9)
    # the bridge: a hump of stone bricks from z 5 to z 13, 5 wide, its top following an arch
    for z in range(4, 15):
        rise = 5 - abs(z - 9) * 0.85
        top = max(1, int(round(rise)))
        for x in range(mid - 2, mid + 3):
            for y in range(-1, top + 1):
                if (x, z) in water and y <= 0 and abs(x - mid) <= 1:
                    continue  # the arch's opening over the water
                g.set(x, y, z, p["wall"], force=(y <= 0))
            g.set(x, top, z, stairs("stone_brick_stairs", "north" if z > 9 else "south") if abs(z - 9) >= 2
                  else p["floor"])
        for x in (mid - 3, mid + 3):
            g.set(x, top + 1, z, "stone_brick_wall" if g.chance(0.75) else AIR)
            for y in range(0, top + 1):
                g.set(x, y, z, p["wall"], force=(y <= 0))
    # its middle fallen in: knocked out, the pieces in the water under it
    for x in range(mid - 3, mid + 4):
        for y in range(1, 7):
            for z in (8, 9, 10):
                if abs(x - mid) <= 1 or g.chance(0.5) or z == 9:
                    g.set(x, y, z, AIR, force=True)
    for x in (mid - 1, mid + 1):
        g.set(x, -1, 9, "mossy_cobblestone", force=True)
    # the landing at the far end and the frame on it, the ground round it gone bad
    stain = blight_stain(g, mid, 3, 7.5, rz=3.5)
    for x in range(mid - 4, mid + 5):
        for z in range(1, 5):
            g.set(x, 1, z, p["floor"])
    cells = broken_frame(g, mid - 2, 2, 2, [(4, 6), (3, 6), (0, 6), (1, 6)])
    for x in (mid - 4, mid + 4):
        froglamp(g, x, 4, y=2, post="stone_brick_wall", h=1)
    # willows of the brook: birches and an oak leaning over it, lily pads, flowers
    birch(g, 2, 3, 11)
    birch(g, g.W - 3, 2, 12)
    oak(g, 3, 14, 7, spread=3)
    birch(g, g.W - 2, 7, 9)
    for x, z in water:
        if g.chance(0.18) and g.get(x, 1, z) is None:
            g.set(x, 1, z, "lily_pad")
    rubble(g, cells, p["rubble"], spread=2)
    flowers(g, 35, avoid=lambda x, z: abs(x - mid) <= 3 and 5 <= z <= 13, roses_in=stain)
    poison_dress(g, 4, webs=0.0, mushrooms=0.5)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# ---------------------------------------------------------------- 4 Gazebo

@layout("gazebo", "Gazebo", size=(23, 20, 23),
        blurb="A round garden pavilion in a flower meadow: a ring of pillars under a broken dome, the frame inside.")
def gazebo(g, p):
    """An eight-pillared round pavilion on a stepped stone floor, its dome of stone fallen in on one side, the
    frame standing under it; wild flowers round it, a birch at its back."""
    mid = g.W // 2
    meadow(g, cz=11)
    CX, CZ = mid, 9
    # the floor: two steps up, round
    g.disc(CX, CZ, 6.5, 1, p["floor"])
    g.disc(CX, CZ, 5.4, 2, Mix(("smooth_stone", 2), ("stone_bricks", 2), ("mossy_stone_bricks", 1), patch=0.4))
    # eight pillars round it, carved bases and capitals, some broken
    heights = [8, 8, 5, 8, 3, 8, 8, 6]
    for k in range(8):
        a = math.pi * 2 * k / 8 + math.pi / 8
        x = int(round(CX + math.cos(a) * 4.6))
        z = int(round(CZ - math.sin(a) * 4.6))
        h = heights[k]
        g.set(x, 3, z, "chiseled_stone_bricks")
        for y in range(4, 3 + h):
            g.set(x, y, z, "stone_brick_wall" if y < 2 + h else "chiseled_stone_bricks")
        if h == 8:
            g.set(x, 3 + h, z, "chiseled_stone_bricks")
    # the ring beam and the dome over it, the south-west part of both fallen
    for x in range(CX - 6, CX + 7):
        for z in range(CZ - 6, CZ + 7):
            d = math.hypot(x - CX, z - CZ)
            fallen = (x - CX) + (z - CZ) * 0.7 > 2.2
            if fallen:
                continue
            if 3.9 <= d <= 5.4:
                g.set(x, 11, z, p["wall"])
            if d <= 4.6:
                dome = 12 + int(round(2.8 * (1 - (d / 4.8) ** 2)))
                g.set(x, dome, z, Mix(("stone_bricks", 2), ("mossy_stone_bricks", 2), ("moss_block", 1), patch=0.5))
                if d >= 3.6:
                    g.set(x, 12, z, p["wall"])
    # hold every dome block up: fill the dome's shell between the beam and its top where it would float
    for x in range(CX - 5, CX + 6):
        for z in range(CZ - 5, CZ + 6):
            col = [y for y in range(11, 17) if g.get(x, y, z)]
            if col:
                for y in range(min(col), max(col) + 1):
                    if g.get(x, y, z) is None and math.hypot(x - CX, z - CZ) >= 2.2:
                        g.set(x, y, z, p["wall"])
    cells = broken_frame(g, CX - 2, 3, CZ, [(4, 6), (3, 6), (4, 5)])
    # steps up from the front
    for x in range(CX - 1, CX + 2):
        g.set(x, 1, CZ + 7, stairs("stone_brick_stairs", "north"))
    # the fallen part of the dome on the floor and the meadow
    for x, z in ((CX + 4, CZ + 6), (CX + 6, CZ + 4), (CX + 3, CZ + 7), (CX + 7, CZ + 6)):
        g.set(x, 1, z, p["rubble"])
        if g.chance(0.5):
            g.set(x, 2, z, slab("stone_brick_slab"))
    birch(g, 2, 2, 12)
    birch(g, g.W - 3, 1, 10)
    oak(g, g.W - 3, 4, 7, spread=3)
    for x in (CX - 6, CX + 6):
        froglamp(g, x, CZ + 7, post="stone_brick_wall", h=1)
    rubble(g, cells, p["rubble"], spread=2)
    hang_vines(g, 0, 0, g.W - 1, g.D - 1, chance=0.12, y_lo=4, y_hi=14)
    flowers(g, 45)
    return [guard(mid - 3, 17), guard(mid + 3, 17)]


# ---------------------------------------------------------------- 5 Fallen Arch

@layout("fallen_arch", "Fallen Arch", size=(25, 20, 25),
        blurb="Two great trees, one dead and one dying, have fallen against each other over the frame.")
def fallen_arch(g, p):
    """A dead white birch from the left and a great oak from the right have fallen against each other, their trunks
    meeting high over the frame like the roof of a tent; their roots torn up either side; the poison spreading from
    under them."""
    mid = g.W // 2
    meadow(g, cz=12)
    stain = blight_stain(g, mid, 7, 6.5, rz=6.0)
    TOPY, TOPZ = 12, 6
    # the birch: from its torn roots at the left, slanting up to the meeting point (two logs thick)
    for dz in (0, 1):
        g.line((2, 1, 5 + dz), (mid + 1, TOPY, TOPZ + dz), "stripped_birch_log[axis=x]")
        g.line((2, 2, 5 + dz), (mid + 1, TOPY + 1, TOPZ + dz), "birch_log[axis=x]")
    # the oak: from the right, its trunk thicker, its dying crown hanging past the meeting point
    for dz in (0, 1):
        for dy in (0, 1):
            g.line((g.W - 3, 1 + dy, 6 + dz), (mid, TOPY - 1 + dy, TOPZ + dz), "oak_log[axis=x]")
    for dx in range(-4, 2):
        for dz in range(-3, 5):
            for dy in range(0, 3):
                if math.hypot(dx + 1.5, dz - 0.5) + dy * 0.8 <= 3.5 - g.rng.random() and g.chance(0.6):
                    if g.get(mid + dx - 1, TOPY + 1 + dy, TOPZ + dz) is None:
                        g.set(mid + dx - 1, TOPY + 1 + dy, TOPZ + dz, "oak_leaves[persistent=true]")
    # their root plates, torn up out of the ground
    for rx in (1, g.W - 2):
        for dz in range(-3, 5):
            for dy in range(0, 6):
                if math.hypot(dz - 0.5, dy - 2.2) <= 3.0 + g.rng.uniform(-0.4, 0.3):
                    g.set(rx, dy, 5 + dz, Mix(("rooted_dirt", 3), ("dirt", 2), ("coarse_dirt", 1), patch=0.3))
    # the frame under them
    cells = broken_frame(g, mid - 2, 1, 6, [(4, 6), (0, 6), (1, 6)])
    for x in (mid - 3, mid + 3):
        g.set(x, 0, 8, p["light"])
    # old standing stones beside it, half buried
    for x, z, h in ((mid - 5, 9, 3), (mid + 5, 10, 2), (mid - 6, 4, 2)):
        for y in range(1, h + 1):
            g.set(x, y, z, p["wall"])
    birch(g, 3, 15, 10)
    birch(g, g.W - 4, 13, 11)
    birch(g, 2, 21, 8)
    webs_in_corners(g, mid - 6, 2, 3, mid + 6, 12, 9, chance=0.15)
    rubble(g, cells, p["rubble"], spread=3)
    flowers(g, 35, roses_in=stain)
    return [guard(mid - 4, 16), guard(mid + 4, 16)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [poisoned_grove, manor_corner, broken_bridge, gazebo, fallen_arch]
DROPPED = []
