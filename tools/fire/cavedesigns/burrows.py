"""The parts every round-2 hermit crab cave shares, from round 1's No. 7, the Steam Vent Burrows (the user's pick: "I like
number 7, where it's like 3 different things"): a Cinder Vent puffing on a little mound of basalt and magma, burrows
off the den, each a different thing (the crab's nest, a lava burrow, a midden of its spare shells), vents and glowing
cracks in the floors, walled-in lava. Fire colours only.
"""
import math

import cavekit as K


def vent_mound(cave, x, z, stand):
    """A little mound of basalt and magma (3 x 3, one block high, on the floor) with a Cinder Vent on top."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                cave.fill(x + dx, stand, z + dz, "magma_block" if (x + dx + z + dz) % 2 else K.BASALT)
    cave.fill(x, stand, z, K.BASALT)
    cave.fill(x, stand + 1, z, "dross:cinder_vent")


def floor_vents(cave, cx, cz, stand, offsets):
    """Cinder Vents set into the floor (where it's open floor) at these offsets from (cx, cz)."""
    for dx, dz in offsets:
        x, z = cx + dx, cz + dz
        if cave.is_open(x, stand, z) and not cave.is_open(x, stand - 1, z) and not cave.near_crab(x, stand, z):
            cave.g.set(x, stand - 1, z, "dross:cinder_vent", force=True)


def ring_cells(cave, cx, cz, stand, r_in, r_out):
    """Floor cells at standing height `stand` between r_in and r_out of (cx, cz), by angle round it."""
    cells = []
    for x in range(int(cx - r_out - 1), int(cx + r_out + 2)):
        for z in range(int(cz - r_out - 1), int(cz + r_out + 2)):
            d = math.hypot(x - cx, z - cz)
            if r_in <= d <= r_out and cave.is_open(x, stand, z) and cave.is_open(x, stand + 1, z) \
                    and not cave.is_open(x, stand - 1, z) and cave.get(x, stand, z) == "minecraft:air":
                cells.append((math.atan2(z - cz, x - cx), x, z))
    cells.sort()
    return [(x, z) for _a, x, z in cells]


def lava_burrow(cave, cx, cz, stand, toward, size=(2, 3)):
    """A burrow's far end turned into a walled-in pool of lava (`size` cells, its long side across `toward`, the way
    out from the den), magma round its lip and vents beside it."""
    tx, tz = toward
    n = math.hypot(tx, tz) or 1.0
    tx, tz = tx / n, tz / n
    ax, az = -tz, tx                      # across the way in
    cells = []
    for i in range(size[1]):
        for j in range(size[0]):
            k = i - (size[1] - 1) / 2.0
            x = int(round(cx + tx * (1.0 + j) + ax * k))
            z = int(round(cz + tz * (1.0 + j) + az * k))
            cells.append((x, z))
    cave.pool(cells, stand - 1, depth=1)
    # glowing magma round its lip, where it's floor
    for x, z in cells:
        for dx, dz in K.FOUR:
            nx, nz = x + dx, z + dz
            if (nx, nz) not in cells and cave.is_open(nx, stand, nz) and not cave.is_open(nx, stand - 1, nz):
                cave.g.set(nx, stand - 1, nz, "magma_block", force=True)
    return cells


# A hermit crab's spare shells (the Cinder Hermit wears a bell, a brazier, a skull, an urn, a conch, a geode, a lantern
# or a shard of the Colossus): what it keeps heaped round the walls of its midden.
MIDDEN = ("bell[attachment=floor,facing=south]", "skeleton_skull[rotation=6]", "lava_cauldron",
          "lantern[hanging=false]", "wither_skeleton_skull[rotation=10]", "gilded_blackstone", "decorated_pot",
          "obsidian", "skeleton_skull[rotation=14]", "magma_block", "bell[attachment=floor,facing=east]",
          "decorated_pot")


def midden(cave, cx, cz, stand, r, salt=0, every=2):
    """The crab's spare shells heaped round the wall of a burrow: every `every`-th floor cell round its edge gets one
    (bells, skulls, a lava brazier, lanterns, urns, obsidian), an amethyst crystal on a block of blackstone among
    them (a geode's)."""
    cells = ring_cells(cave, cx, cz, stand, max(0.0, r - 1.6), r + 0.6)
    placed = 0
    for i, (x, z) in enumerate(cells):
        if i % every or cave.near_crab(x, stand, z):
            continue
        what = MIDDEN[(placed + salt) % len(MIDDEN)]
        if what == "decorated_pot":
            cave.pot(x, stand, z, ("brick", "brick", "brick", "brick"), loot=False)   # an urn shell: plain
        else:
            cave.put(x, stand, z, what)
        placed += 1
    return placed


def amethyst(cave, x, z, stand):
    """A geode shell's crystal: blackstone with an amethyst cluster on top."""
    cave.fill(x, stand, z, "blackstone")
    cave.put(x, stand + 1, z, "amethyst_cluster[facing=up,waterlogged=false]")


def nest(cave, cx, cz, stand, r, salt=0):
    """The crab's nest: its burrow's floor heaped with soft ash (ash underfoot, drifts one layer deep in the crab's own
    room, two outside it), a ring of magma glowing at its edge."""
    rnd = cave.radius_noise(40 + salt, 1.0)
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            if not (cave.is_open(x, stand, z) and not cave.is_open(x, stand - 1, z)):
                continue
            d = math.hypot(x - cx, z - cz)
            if d > r + 0.4 or cave.get(x, stand, z) != "minecraft:air":
                continue
            edge = d > r - 1.0
            cave.g.set(x, stand - 1, z, "magma_block" if edge and (x * 7 + z * 3 + salt) % 3 == 0 else "dross:ash",
                       force=True)
            # two layers only outside the crab's own room: in the game a drift of two has a collision box
            layers = 2 if d < r * 0.55 + 0.6 * rnd(x * 0.7 + z) and not cave.near_crab(x, stand, z) else 1
            if not edge or (x + z + salt) % 2:
                cave.put(x, stand, z, f"dross:ash_layer[layers={layers}]")


def dress_floor(cave, stand_ok, salt=0, ash=14, weed=7):
    """Ash drifts and emberweed on the floors (`stand_ok(x, y, z)` says which), never in the crab's room."""
    cave.scatter("dross:ash_layer[layers=1]", ash, where=stand_ok, on=("ash",), salt=salt)
    cave.scatter("dross:emberweed", weed, where=stand_ok, on=("ash", "basalt", "blackstone"), salt=salt + 1)


def mouth_frame(cave, x1, x2, top, depth=2):
    """Frames a hillside mouth (open from x1 to x2, up to `top` over the ground at the south edge): pillars of polished
    basalt either side, a lintel of polished blackstone over it with a magma keystone, `depth` rows deep. Only cells
    the lining already holds are changed. Returns the cells it set (keep lights off them)."""
    cells = []
    for z in range(cave.D - depth, cave.D):
        for y in range(1, top + 1):
            for x in (x1 - 1, x2 + 1):
                if cave.get(x, y, z) not in (None, "minecraft:air") and not cave.is_open(x, y, z):
                    cave.g.set(x, y, z, K.POLISHED_BASALT, force=True)
                    cells.append((x, y, z))
        for x in range(x1, x2 + 1):
            y = 1
            while y <= cave.ABOVE and cave.is_open(x, y, z):
                y += 1
            if cave.get(x, y, z) not in (None, "minecraft:air") and not cave.is_open(x, y, z):
                mid = x == (x1 + x2) // 2
                cave.g.set(x, y, z, "magma_block" if mid else "polished_blackstone", force=True)
                cells.append((x, y, z))
    return cells
