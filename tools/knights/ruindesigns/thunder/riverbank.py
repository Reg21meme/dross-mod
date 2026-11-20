"""Thunder's rivers: sand and clay banks, gravel bars, reeds of sugar cane, mossy stone bricks of old bridges and
mills, and the storm's marks (lightning rods on weathered copper, deepslate, sea lanterns). The river runs along the
back of the plot (the shore setting's water)."""
import math

from ruinkit import (AIR, FRAME, SHORE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, shore_line, slab, stairs)
from ruindesigns.thunder.stormkit import (COPPER, CUT_COPPER, DEEP, ROD, STORM, hang, rod_on, rod_post, scorch,
                                          sea_floor)

BRICK = Mix(("mossy_stone_bricks", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 2), ("mossy_cobblestone", 1),
            patch=0.5)

P = dict(
    floor=Mix(("sand", 3), ("gravel", 1), ("clay", 1), ("coarse_dirt", 1), patch=0.6),
    wall=BRICK,
    bank=Mix(("grass_block", 4), ("coarse_dirt", 1), ("sand", 1), patch=0.6),
    trim="chiseled_stone_bricks",
    rubble=Mix(("mossy_cobblestone", 2), ("mossy_stone_bricks", 1), ("cobblestone", 1)),
    light="lantern",
)

AREA = Area(element="thunder", id="riverbank", name="Riverbank", biomes="river",
            setting=SHORE, ground="sand", under="dirt", palette=P,
            blurb="Sand and clay banks, reeds, the mossy stones of an old bridge or mill by the water.")


def is_water_next(g, x, z, y=0):
    """True if the cell beside (x, y, z) is water: placed water, or the river's own (an unset cell at the back)."""
    line = shore_line(g.D)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, nz = x + dx, z + dz
        if not (0 <= nx < g.W and 0 <= nz < g.D):
            continue
        s = g.get(nx, y, nz)
        if s == "minecraft:water" or (s is None and nz < line):
            return True
    return False


def reeds(g, n, x1, z1, x2, z2):
    """Sugar cane on sand beside the water, 1-3 tall."""
    k = 0
    for _ in range(n * 30):
        if k >= n:
            break
        x, z = g.rng.randint(max(x1, 0), min(x2, g.W - 1)), g.rng.randint(max(z1, 0), min(z2, g.D - 1))
        s = g.get(x, 0, z)
        if s is None or not g.empty(x, 1, z) or not is_water_next(g, x, z):
            continue
        if not any(t in s for t in ("sand", "grass_block", "dirt", "clay", "gravel")):
            continue
        g.set(x, 0, z, "sand")
        g.set(x, -1, z, "dirt")
        for y in range(1, 1 + g.rng.randint(1, 3)):
            g.set(x, y, z, "sugar_cane")
        k += 1


def lilies(g, n, x1, z1, x2, z2):
    """Lily pads on the river (over the world's own water at the back)."""
    line = shore_line(g.D)
    for _ in range(n):
        x, z = g.rng.randint(x1, x2), g.rng.randint(z1, min(z2, line - 1))
        if g.get(x, 0, z) is None and g.get(x, 1, z) is None:
            g.set(x, 1, z, "lily_pad")


def grassy(g, z_from):
    """Grass and flowers on the bank's front (a soil block under each plant)."""
    def place(x, y, z):
        if z < z_from or y != 0:
            return False
        r = g.rng.random()
        g.set(x, 0, z, "grass_block")
        g.set(x, -1, z, "dirt")
        g.set(x, 1, z, "grass" if r < 0.5 else "fern" if r < 0.7 else
              g.rng.choice(("blue_orchid", "cornflower", "azure_bluet", "oxeye_daisy")))
        return True
    scatter_on_floor(g, 14, place, y_max=0)


def dress(g, count=6):
    def place(x, y, z):
        r = g.rng.random()
        if r < 0.4:
            g.set(x, y + 1, z, slab("mossy_stone_brick_slab"))
        elif r < 0.7:
            g.set(x, y + 1, z, "mossy_cobblestone")
        else:
            g.set(x, y + 1, z, "clay")
        return True
    scatter_on_floor(g, count, place, y_max=1)


def bank(g, p, cz=17, rx=11.5, rz=6.5):
    """The bank in front: sand down by the water, grass further up."""
    court(g, p["floor"], 9.0, cz=cz, under="dirt", rx=rx, rz=rz)
    for x in range(g.W):
        for z in range(g.D):
            s = g.get(x, 0, z)
            if s is not None and z > cz - 1 and g.noise3_at(5.0, x, 0, z) > 0.45:
                g.set(x, 0, z, p["bank"])


@layout("broken_bridge", "Broken Bridge", size=(23, 20, 23),
        blurb="An old stone bridge's first arch over the river, broken off mid-stream; the frame stands on its end.")
def broken_bridge(g, p):
    """A mossy stone bridge rises from the bank on a ramp and leaps the river on an arch; the next span has fallen,
    and on the broken end of the deck, over the water, stands the frame, a lamp post leaning beside it."""
    mid = g.W // 2
    line = shore_line(g.D)
    bank(g, p)
    deck = 6
    # the abutment on the bank, the ramp up to it (stairs going north)
    for x in range(mid - 3, mid + 4):
        for z in range(line, line + 4):
            for y in range(-2, deck + 1):
                g.set(x, y, z, BRICK)
        for k in range(deck):
            z = line + 4 + k
            for y in range(1, deck - k):
                g.set(x, y, z, BRICK)
            g.set(x, deck - k, z, stairs("mossy_stone_brick_stairs", "north")
                  if x not in (mid - 3, mid + 3) else BRICK)
    # the arch over the river: its pier in the water at z 0-1, its underside an arc
    for x in range(mid - 3, mid + 4):
        for z in range(0, line):
            arc = int(round(-1 + 6.0 * math.sin(math.pi * (z + 0.5) / (line + 1.6))))
            for y in range(max(arc, -3), deck + 1):
                g.set(x, y, z, BRICK)
        for z in (0, 1):
            for y in range(-3, deck + 1):
                g.set(x, y, z, BRICK)
    # parapets along both sides, broken
    for z in range(0, line + 4):
        for x in (mid - 3, mid + 3):
            if g.noise3_at(3.0, x, 0, z) > 0.35:
                g.set(x, deck + 1, z, "mossy_stone_brick_wall")
    # the broken end: the deck torn off ragged at the back of the plot
    for x in range(mid - 4, mid + 5):
        for y in range(deck - 3, deck + 2):
            if g.noise3_at(2.0, x, y, 0) > 0.4:
                g.clear(x, y, 0)
    # the frame on the deck, over the pier, its top corner gone
    cells = broken_frame(g, mid - 2, deck + 1, 2, [(4, 6), (3, 6), (4, 5), (2, 6)])
    for x, y, z in cells:
        g.set(x, -1, z - 1, BRICK)
    # the lamp post leaning out over the water, its lantern hanging; a rod on the ramp's corner post
    g.line((mid + 3, deck + 1, 5), (mid + 5, deck + 4, 5), "spruce_fence")
    g.set(mid + 5, deck + 5, 5, "spruce_planks")
    hang(g, mid + 6, deck + 4, 5, links=1)
    g.set(mid + 6, deck + 5, 5, "spruce_slab[type=top]")
    rod_post(g, mid - 3, line + 3, 2, y0=deck + 1, shaft=STORM)
    # the fallen span's stones in the river
    for x, z in ((mid - 2, 0), (mid + 1, 1), (mid + 4, 2), (mid - 5, 3)):
        if g.get(x, -1, z) is None:
            g.set(x, -1, z, BRICK)
            g.set(x, -2, z, "dirt")
    reeds(g, 10, 0, line - 1, g.W - 1, line + 2)
    lilies(g, 6, 0, 0, g.W - 1, line - 1)
    scorch(g, mid - 6, 17, 2.4)
    grassy(g, 17)
    dress(g, 5)
    return [guard(mid - 6, 16), guard(mid + 6, 16)]


@layout("the_ford", "The Ford", size=(25, 18, 23),
        blurb="Stepping stones cross the ford to a gravel bar in the river, where the frame stands in the reeds.")
def the_ford(g, p):
    """An old ford: a gravel bar in the river, reached by a line of flat stepping stones from the bank, the frame
    standing on the bar among reeds; on the bank, a mossy waymark and two storm posts mark the crossing."""
    mid = g.W // 2
    line = shore_line(g.D)
    bank(g, p, cz=17)
    # the gravel bar in the river, its foot built up from the bed
    for x in range(g.W):
        for z in range(0, line):
            d = math.hypot((x - mid - 1) / 6.5, (z - 3.5) / 3.2)
            if d <= 1.0 + 0.2 * (g.noise3_at(3.0, x, 0, z) - 0.5):
                g.set(x, 0, z, Mix(("gravel", 3), ("sand", 2), ("clay", 1), ("mossy_cobblestone", 0.4)))
                g.set(x, -1, z, "dirt")
                g.set(x, -2, z, "dirt")
    # the stepping stones from the bank to the bar (level with the water, in a gentle curve)
    for k, z in enumerate(range(line - 1, 6, -1)):
        x = mid - 2 + int(round(math.sin(k * 0.9) * 1.2))
        for dx in (0, 1):
            g.set(x + dx, 0, z, Mix(("smooth_stone", 2), ("stone", 1), ("mossy_cobblestone", 1)))
            g.set(x + dx, -1, z, "stone")
    cells = broken_frame(g, mid - 1, 1, 3, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, p["rubble"])
    # driftwood on the bar, reeds round its edge
    g.set(mid + 5, 1, 3, "stripped_oak_log[axis=x]")
    g.set(mid + 6, 1, 3, "stripped_oak_log[axis=x]")
    g.set(mid + 7, 1, 4, "oak_log[axis=x]")
    reeds(g, 14, mid - 7, 0, mid + 8, line - 1)
    reeds(g, 8, 0, line - 1, g.W - 1, line + 1)
    # the waymark: a mossy stone with a sea lantern, and two storm posts either side of the crossing
    for y in range(1, 4):
        g.set(mid - 6, y, line + 2, BRICK if y < 3 else "chiseled_stone_bricks")
    g.set(mid - 6, 4, line + 2, "sea_lantern")
    g.set(mid - 6, 5, line + 2, ROD)
    rod_post(g, mid + 2, line + 1, 2, shaft=Mix(("mossy_cobblestone", 1), ("cobblestone", 1)))
    rod_post(g, mid - 4, line, 1, shaft="mossy_cobblestone", light=False)
    lilies(g, 8, 0, 0, g.W - 1, line - 1)
    scorch(g, mid + 6, 18, 2.6)
    grassy(g, 16)
    dress(g, 5)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


@layout("old_mill", "Old Mill", size=(25, 18, 23),
        blurb="A watermill's broken walls by the river, its race still running past a wrecked wheel; the frame in its wall.")
def old_mill(g, p):
    """The stone shell of a watermill on the bank: a race of water led off the river runs along its east side,
    walled in, past the ruin of its great wheel; the frame is built into the mill's broken back wall, a millstone
    lies in the doorway and copper rods stand on the gable's stump."""
    mid = g.W // 2
    line = shore_line(g.D)
    bank(g, p, cz=18, rz=5.5)
    x1, x2, z1, z2 = mid - 7, mid + 2, line - 2, line + 6
    # the mill's floor and walls (stone bricks on a clay footing, the back wall standing in the river)
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            g.set(x, 0, z, Mix(("spruce_planks", 2), ("mossy_cobblestone", 1), ("clay", 1)))
            for y in range(-3, 0):
                g.set(x, y, z, "clay" if y == -1 else "dirt")
    g.walls(x1, z1, x2, z2, 1, 9, BRICK)
    for x in range(x1, x2 + 1):
        top = 9 + max(0, 4 - abs(x - (x1 + x2) // 2))
        for y in range(9, top):
            g.set(x, y, z1, BRICK)
    for x in range(x1, x2 + 1):
        for y in range(-3, 1):
            g.set(x, y, z1, BRICK)
    # its door at the front and a window over it, then broken down
    for y in range(1, 4):
        for x in (x1 + 3, x1 + 4, x1 + 5):
            g.set(x, y, z2, AIR, force=True)
    g.ruin_tops(x1, z1 + 1, x2, z2, 1, 10, 0.5, 6.0, scale=4.0, salt=31)
    g.ruin_tops(x1, z1, x2, z1, 6, 14, 6.0, 13.0, scale=3.0, salt=32)
    g.settle_rubble(0.5, 1.5, p["rubble"], protect=lambda x, z: z > z2 + 1)
    # the frame in the back wall, inside facing the door
    cells = broken_frame(g, x1 + 2, 1, z1, [(0, 6), (1, 6), (0, 5), (1, 5)])
    rubble(g, cells, p["rubble"])
    # the race: a channel 2 wide east of the mill, from the river to a sluice, walled and floored
    rx = x2 + 2
    for z in range(0, z2 + 2):
        for x in range(rx - 1, rx + 3):
            wall = x in (rx - 1, rx + 2) or z == z2 + 1
            for y in range(-2, 1):
                g.set(x, y, z, BRICK if wall or y < 0 else "water")
            if wall and z >= line:
                g.set(x, 1, z, BRICK if g.noise3_at(3.0, x, 0, z) > 0.3 else AIR)
    for x in (rx, rx + 1):
        for z in range(0, line):
            g.clear(x, 0, z)
            g.clear(x, -1, z)
    # the wheel: a broken ring of spruce on its axle, half in the race
    wz = line + 3
    ring = []
    for k in range(17):
        a = 2 * math.pi * k / 16
        ring.append((int(round(3 + math.sin(a) * 3.4)), int(round(wz + math.cos(a) * 3.4))))
    for k in range(16):
        if k in (3, 4, 5):
            continue  # a gap where the rim broke away
        (ya, za), (yb, zb) = ring[k], ring[k + 1]
        g.line((rx, ya, za), (rx, yb, zb), "stripped_spruce_log[axis=x]" if k % 2 else "spruce_planks")
        if k % 4 == 0:
            g.set(rx + 1, ya, za, "spruce_trapdoor[facing=east,half=bottom,open=true]")
    for k in (0, 8, 12):
        g.line((rx, 3, wz), (rx, ring[k][0], ring[k][1]), "spruce_fence")
    for x in range(x2, rx + 2):
        g.set(x, 3, wz, "stripped_dark_oak_log[axis=x]")
    # the millstone in the doorway, sacks of clay and a lantern on a post
    g.set(x1 + 4, 1, z2 + 1, "smooth_stone_slab[type=bottom]")
    g.set(x1 + 5, 1, z2 + 2, "smooth_stone")
    for x, z in ((x1 - 1, z2), (x1 - 1, z2 - 1)):
        g.set(x, 1, z, "clay")
    g.set(x1 - 2, 1, z2 + 2, "spruce_fence")
    g.set(x1 - 2, 2, z2 + 2, "spruce_fence")
    g.set(x1 - 2, 3, z2 + 2, "lantern")
    # rods on the gable's stump
    top = max((g.top_y(x, z1), x) for x in range(x1, x2 + 1))
    g.set(top[1], top[0] + 1, z1, "waxed_oxidized_cut_copper")
    g.set(top[1], top[0] + 2, z1, ROD)
    reeds(g, 10, 0, line - 1, x1 - 1, line + 2)
    lilies(g, 5, 0, 0, x1, line - 1)
    scorch(g, mid + 5, 19, 2.2)
    grassy(g, 17)
    dress(g, 4)
    return [guard(mid - 6, 18), guard(mid + 1, 18)]


@layout("reed_shrine", "Reed Shrine", size=(23, 18, 23),
        blurb="A little shrine on a spit of clay in the reeds, the frame on its plinth among offerings and lanterns.")
def reed_shrine(g, p):
    """A spit of clay and mud runs out into the river through a bed of reeds; at its end a round plinth of mossy
    stone holds the frame, ringed by little lantern posts and decorated pots left as offerings, a copper rod
    standing over it; a boardwalk of planks crosses the reeds to it from the bank."""
    mid = g.W // 2
    line = shore_line(g.D)
    bank(g, p, cz=17)
    # the spit: mud and clay from the bank out to a round end in the river
    for x in range(g.W):
        for z in range(0, line + 2):
            d = math.hypot((x - mid) / 5.2, (z - 4) / 4.2)
            spit = abs(x - mid) <= 2 + (1 if g.noise3_at(3.0, x, 0, z) > 0.6 else 0) and z >= 4
            if d <= 1.0 or spit:
                g.set(x, 0, z, Mix(("mud", 2), ("clay", 2), ("packed_mud", 1), ("grass_block", 1), patch=0.6))
                g.set(x, -1, z, "dirt")
                g.set(x, -2, z, "dirt")
    # the backwaters either side of the spit's root: still pools for the reeds
    for (px, pz) in ((mid - 6, line + 1), (mid + 6, line + 2)):
        for x in range(px - 3, px + 4):
            for z in range(pz - 2, pz + 3):
                if math.hypot((x - px) / 3.0, (z - pz) / 2.2) <= 1.0 and z >= line:
                    g.set(x, 0, z, "water")
                    g.set(x, -1, z, "clay")
    # the plinth: two rings of mossy stone, its top at y 1
    for x in range(mid - 4, mid + 5):
        for z in range(0, 9):
            d = math.hypot(x - mid, z - 4)
            if d <= 3.6:
                g.set(x, 1, z, "chiseled_stone_bricks" if d < 0.6 else BRICK)
                g.set(x, 0, z, BRICK)
            elif d <= 4.6 and g.get(x, 0, z) is not None and "water" not in str(g.get(x, 0, z)):
                g.set(x, 1, z, slab("mossy_stone_brick_slab"))
    cells = broken_frame(g, mid - 2, 2, 3, [(4, 6), (3, 6), (2, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the copper rod over it on a tall post behind, and lantern posts and offerings round the plinth
    for y in range(2, 11):
        g.set(mid, y, 1, "waxed_oxidized_copper" if y % 3 else "waxed_oxidized_cut_copper")
    g.set(mid, 11, 1, "sea_lantern")
    g.set(mid, 12, 1, ROD)
    for (x, z) in ((mid - 3, 6), (mid + 3, 6), (mid - 3, 2), (mid + 3, 2)):
        g.set(x, 2, z, "mossy_stone_brick_wall")
        g.set(x, 3, z, "lantern")
    for (x, z) in ((mid - 2, 7), (mid + 2, 7), (mid + 1, 7)):
        g.set(x, 2, z, "decorated_pot")
    # the boardwalk from the bank over the spit's root
    for z in range(8, line + 5):
        for x in (mid - 1, mid, mid + 1):
            g.set(x, 1, z, slab("spruce_slab") if z > 8 else "spruce_planks")
    for z in (9, 12, 14):
        for x in (mid - 2, mid + 2):
            g.set(x, 1, z, "spruce_fence")
            g.set(x, 2, z, "spruce_fence")
    reeds(g, 22, 0, 0, g.W - 1, line + 4)
    lilies(g, 10, 0, 0, g.W - 1, line - 1)
    scorch(g, mid - 5, 18, 2.2)
    grassy(g, 16)
    dress(g, 4)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [the_ford, reed_shrine]
DROPPED = [broken_bridge, old_mill]
