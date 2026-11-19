"""Thunder's warm shores: sandstone and dead coral bleached by the sun, prismarine washed up from drowned temples, and
the storm's marks on the sand (glass fused by lightning, rods of weathered copper, sea lanterns)."""
import math

from ruinkit import (AIR, FRAME, SHORE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, shore_line, slab, stairs)
from ruindesigns.thunder.stormkit import (COPPER, CUT_COPPER, DEEP, GLASS, ROD, STORM, fulgurite, rod_on, rod_post,
                                          scorch, sea_floor)

CORAL = Mix(("dead_brain_coral_block", 2), ("dead_tube_coral_block", 2), ("dead_horn_coral_block", 2),
            ("dead_fire_coral_block", 1), ("dead_bubble_coral_block", 1), patch=0.5, scale=3.0)
FANS = ("dead_brain_coral_fan", "dead_tube_coral_fan", "dead_horn_coral_fan", "dead_fire_coral",
        "dead_bubble_coral", "dead_horn_coral")

P = dict(
    floor=Mix(("smooth_sandstone", 3), ("cut_sandstone", 3), ("sandstone", 2), ("sand", 1), patch=0.5),
    wall=Mix(("cut_sandstone", 4), ("sandstone", 3), ("smooth_sandstone", 2), ("chiseled_sandstone", 0.4), patch=0.4),
    accent=Mix(("prismarine_bricks", 3), ("prismarine", 3), ("dark_prismarine", 1), patch=0.4),
    coral=CORAL,
    trim="chiseled_sandstone",
    rubble=Mix(("sandstone", 2), ("cut_sandstone", 1), ("prismarine", 1)),
    light="sea_lantern",
)

AREA = Area(element="thunder", id="warm_beach", name="Warm Shore", biomes="warm and lukewarm ocean shores",
            setting=SHORE, ground="sand", under="sandstone", palette=P,
            blurb="Sun-bleached sandstone and dead coral, the sand fused to glass where the lightning struck.")


def fan(g, x, y, z):
    """A dead coral (or fan) on top of the full block under (x, y, z)."""
    g.set(x, y, z, g.rng.choice(FANS) + "[waterlogged=false]")


LIVE = ("brain", "tube", "horn", "fire", "bubble")


def coral_pillar(g, x, z, h, y0=1, wide=False):
    """A sandstone drum pillar crusted with dead coral: a plinth, a lumpy shaft, a fan or two on its broken top."""
    shaft = Mix(("cut_sandstone", 3), ("smooth_sandstone", 1), ("dead_brain_coral_block", 2),
                ("dead_tube_coral_block", 1), ("dead_horn_coral_block", 1), patch=0.6, scale=2.5)
    g.set(x, y0, z, "chiseled_sandstone")
    for y in range(y0 + 1, y0 + h):
        g.set(x, y, z, shaft)
    if wide:
        for dx, dz in ((1, 0), (0, 1), (1, 1)):
            for y in range(y0, y0 + max(1, h - 2 - g.rng.randint(0, 2))):
                g.set(x + dx, y, z + dz, shaft if y > y0 else "cut_sandstone")
    else:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if g.chance(0.5) and g.empty(x + dx, y0, z + dz):
                g.set(x + dx, y0, z + dz, CORAL)
    if g.chance(0.7):
        fan(g, x, y0 + h, z)


def reef(g, n, x1, z1, x2, z2):
    """Live coral on the sea floor (in the world's water: it stays alive), a fan or a coral on some of it."""
    line = shore_line(g.D)
    k = 0
    for _ in range(n * 30):
        if k >= n:
            break
        x, z = g.rng.randint(x1, x2), g.rng.randint(z1, min(z2, line - 1))
        if g.get(x, -1, z) is not None or g.get(x, 0, z) is not None:
            continue
        kind = g.rng.choice(LIVE)
        g.set(x, -2, z, "sand")
        g.set(x, -1, z, f"{kind}_coral_block")
        if g.get(x, 0, z) is None and g.chance(0.7):
            g.set(x, 0, z, f"{g.rng.choice(LIVE)}_coral{g.rng.choice(('', '_fan'))}[waterlogged=true]")
        k += 1


def pickles(g, n, x1, z1, x2, z2):
    """Sea pickles glowing on the sea floor (in the world's own water, at y -1)."""
    line = shore_line(g.D)
    k = 0
    for _ in range(n * 20):
        if k >= n:
            break
        x, z = g.rng.randint(x1, x2), g.rng.randint(z1, min(z2, line - 1))
        if g.get(x, -1, z) is None and g.get(x, 0, z) is None and g.get(x, -2, z) is None:
            g.set(x, -1, z, f"sea_pickle[pickles={g.rng.randint(1, 4)},waterlogged=true]")
            k += 1


def dress(g, count=8):
    def place(x, y, z):
        s = g.get(x, y, z)
        r = g.rng.random()
        if r < 0.45:
            if s is None or s.endswith("sand"):
                g.set(x, y, z, "sand")
                g.set(x, y + 1, z, "dead_bush")
                return True
            return False
        if r < 0.75:
            g.set(x, y + 1, z, slab("sandstone_slab"))
            return True
        fan(g, x, y + 1, z)
        return True
    scatter_on_floor(g, count, place, y_max=1)


@layout("coral_ring", "Coral Ring", size=(25, 18, 25),
        blurb="A ring of coral-grown pillars half in the surf, the frame toppled flat in its middle.")
def coral_ring(g, p):
    """Round One's Fallen Ring, washed up: a sandstone court whose back half is in the sea, its ring of pillars grown
    over with dead coral, the frame lying on its back with one stub standing, a pool of pickles among the stones."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=12, under="sandstone")
    for x in range(g.W):
        for z in range(g.D):
            if g.get(x, 0, z) is not None and z < shore_line(g.D):
                g.set(x, -2, z, "sandstone")
            d = math.hypot(x - mid, z - 12)
            if g.get(x, 0, z) is not None and 6.0 <= d < 7.2:
                g.set(x, 0, z, Mix(("prismarine_bricks", 3), ("prismarine", 2), ("cut_sandstone", 1)))
            elif g.get(x, 0, z) is not None and d < 6.0 and (x + z) % 7 == 0:
                g.set(x, 0, z, "cut_sandstone")
    # the ring of pillars, the way in at the front left open
    n = 10
    for k in range(n):
        a = 2.0 * math.pi * (k + 0.25) / n
        x = int(round(mid + math.cos(a) * 9.0))
        z = int(round(12 + math.sin(a) * 9.0))
        if z > 16 and abs(x - mid) < 6:
            continue
        h = 2 + int(g.noise3_at(3.0, x, 0, z) * 6)
        coral_pillar(g, x, z, h, wide=(k % 3 == 0))
        if k % 4 == 1:
            g.set(x, h + 1, z, "sea_lantern")
            g.set(x, h + 2, z, ROD)
    # the frame toppled flat, its top towards the sea, a stub of its foot still standing
    flat_frame(g, mid - 2, 1, 4, lambda i, j: not (j == 0 and i in (3, 4)) and not (i == 4 and j == 1))
    for y in (1, 2, 3):
        g.set(mid - 2, y, 11, FRAME, force=True)
    g.set(mid - 1, 1, 11, FRAME, force=True)
    rubble(g, [(mid + 2, 1, 10), (mid + 1, 1, 10), (mid + 2, 1, 9)], p["rubble"])
    # a tide pool in the court, pickles glowing in it
    for x in range(mid + 3, mid + 7):
        for z in range(9, 13):
            if math.hypot(x - mid - 4.5, z - 10.5) < 2.2:
                g.set(x, 0, z, "water")
                g.set(x, -1, z, "sand")
                g.set(x, -2, z, "sandstone")
                if g.chance(0.4):
                    g.set(x, -1, z, f"sea_pickle[pickles={g.rng.randint(1, 4)},waterlogged=true]")
                    g.set(x, -2, z, "sandstone")
    for x in range(mid + 2, mid + 8):
        for z in range(8, 14):
            if g.get(x, 0, z) is not None and g.get(x, 0, z) != "minecraft:water" and math.hypot(x - mid - 4.5, z - 10.5) < 3.3:
                g.set(x, 0, z, p["accent"])
    scorch(g, mid - 5, 16, 3.0, sand=True)
    pickles(g, 10, 0, 0, g.W - 1, 9)
    reef(g, 22, 0, 0, g.W - 1, 9)
    dress(g, 8)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


@layout("drowned_pier", "Drowned Pier", size=(21, 18, 25),
        blurb="A pier of sandstone and prismarine runs out into the sea, the frame on its broken head.")
def drowned_pier(g, p):
    """A plaza on the beach and a pier from it out over the sea on prismarine piles; its middle span has fallen in,
    and the frame stands on the round pier head beyond it between two storm posts."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 8.0, cz=17, under="sandstone", rx=9.0, rz=6.5)
    # the pier deck at y 1, 5 wide, from the beach out to the head
    for z in range(4, line + 3):
        for x in range(mid - 2, mid + 3):
            g.set(x, 1, z, p["floor"] if x not in (mid - 2, mid + 2) else p["accent"])
            if z >= line:
                g.set(x, 0, z, "cut_sandstone")
    # its piles every 3 blocks, down into the sea floor
    for z in range(4, line + 1, 3):
        for x in (mid - 2, mid + 2):
            for y in range(-3, 1):
                g.set(x, y, z, p["accent"])
    # the fallen span: the deck's middle gone, its stones in the water
    for z in (6, 7):
        for x in range(mid - 1, mid + 2):
            g.set(x, 1, z, AIR)
    g.set(mid - 2, 1, 6, AIR)
    for x, z in ((mid - 1, 6), (mid + 1, 7), (mid, 6)):
        g.set(x, -1, z, p["rubble"])
    # the pier head: a round platform on a solid footing
    for x in range(g.W):
        for z in range(0, 7):
            d = math.hypot(x - mid, z - 3)
            if d <= 4.6:
                g.set(x, 1, z, "smooth_sandstone" if d < 2.6 else p["accent"])
                sea_floor(g, x, z, x, z, "sandstone", y_top=0, y_bot=-3)
                if 3.8 < d and g.chance(0.6):
                    g.set(x, 2, z, slab("prismarine_brick_slab"))
    cells = broken_frame(g, mid - 2, 2, 2, [(4, 6), (3, 6), (4, 5)])
    for x, y, z in cells:
        g.set(x - 1, -1, z + 3, p["rubble"])
    rod_post(g, mid - 4, 3, 3, y0=2, shaft=STORM)
    rod_post(g, mid + 4, 4, 2, y0=2, shaft=STORM)
    # mooring posts on the beach and the plaza's broken edge walls
    for x, z in ((mid - 6, 13), (mid + 6, 14)):
        g.set(x, 1, z, "cut_sandstone")
        g.set(x, 2, z, "sandstone_wall")
    for x in range(1, 5):
        for y in range(1, 1 + max(1, 3 - x // 2)):
            g.set(x, y, 21, p["wall"])
    for x in range(g.W - 6, g.W - 1):
        for y in range(1, 1 + (2 if x % 2 else 1)):
            g.set(x, y, 12, p["wall"])
    # the pier's gateway on the beach: two posts, the lintel broken off one side
    gz = line + 1
    for x in (mid - 3, mid + 3):
        g.set(x, 1, gz, "chiseled_sandstone")
        for y in range(2, 6):
            g.set(x, y, gz, p["wall"])
    for x in range(mid - 3, mid + 2):
        g.set(x, 6, gz, "cut_sandstone" if x > mid - 3 else "chiseled_sandstone")
    g.set(mid - 3, 7, gz, "sea_lantern")
    g.set(mid - 3, 8, gz, ROD)
    g.set(mid + 3, 6, gz, slab("cut_sandstone_slab"))
    g.set(mid + 4, 1, gz + 1, "cut_sandstone")
    g.set(mid + 5, 1, gz + 2, slab("cut_sandstone_slab"))
    # lamp posts down the pier
    for z in (9, 4):
        g.set(mid - 2, 2, z, "prismarine_wall")
        g.set(mid - 2, 3, z, "sea_lantern")
    scorch(g, mid + 5, 19, 2.5, sand=True)
    pickles(g, 12, 0, 0, g.W - 1, line - 1)
    reef(g, 18, 0, 0, g.W - 1, line - 1)
    dress(g, 6)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


@layout("temple_steps", "Temple Steps", size=(25, 20, 25),
        blurb="The stepped corner of a drowned prismarine temple, washed up on the shore, the frame on its top.")
def temple_steps(g, p):
    """A drowned temple's terraces rise out of the surf at the back in steps of prismarine, one corner torn away;
    the frame stands on the top terrace between two dark storm pillars, coral grown up its flanks."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, p["floor"], 9.0, cz=18, under="sandstone", rx=11.0, rz=6.0)
    tiers = ((11, 1), (9, 2), (7, 3), (5, 4), (2, 5))  # (front z of the tier, its top y)
    halves = (9, 8, 6, 5, 3)
    for k, (zf, top) in enumerate(tiers):
        half = halves[k]
        for x in range(mid - half, mid + half + 1):
            for z in range(0, zf + 1):
                for y in range(-3, top + 1):
                    g.set(x, y, z, p["accent"] if y < top else "prismarine_bricks")
    # the tiers' edges: dark prismarine, a sea lantern here and there, a stair up the middle
    for k, (zf, top) in enumerate(tiers):
        half = halves[k]
        for x in range(mid - half, mid + half + 1):
            g.set(x, top, zf, "sea_lantern" if (x - mid) % 6 == 3 and k % 2 == 0 else
                  Mix(("dark_prismarine", 3), ("prismarine_bricks", 1)))
        for x in range(mid - 1, mid + 2):
            g.set(x, top, zf, stairs("prismarine_brick_stairs", "north"))
            g.set(x, top + 1, zf, AIR)
    # the top terrace's floor and the frame on it
    for x in range(mid - 3, mid + 4):
        for z in range(0, 3):
            g.set(x, 5, z, "dark_prismarine" if (x + z) % 2 else "prismarine_bricks")
    cells = broken_frame(g, mid - 2, 6, 0, [(0, 6), (1, 6), (0, 5)])
    # the torn corner: a bite out of the east side, another nibble on the west
    g.smash(mid + 8, 4, 3, 4.8, jag=0.5)
    g.smash(mid - 8, 2, 8, 3.0, jag=0.5)
    g.ruin_tops(0, 0, g.W - 1, 12, 2, 6, 1.0, 6.0, scale=4.0, salt=3,
                region=lambda x, z: abs(x - mid) > 4 or z > 3)
    g.settle_rubble(0.5, 2.0, p["accent"], protect=lambda x, z: z > 13)
    # dark storm pillars either side of the frame, rods on top
    for x in (mid - 4, mid + 4):
        hi = 11 if x < mid else 8
        for y in range(4, hi):
            g.set(x, y, 1, "dark_prismarine" if y % 3 else "prismarine_bricks")
        g.set(x, hi, 1, "sea_lantern")
        g.set(x, hi + 1, 1, ROD)
    # coral grown up the flanks
    for x in range(g.W):
        for z in range(0, 13):
            for y in range(1, 4):
                t = g.get(x, y, z)
                if t is not None and "prismarine" in t and g.noise3_at(3.0, x, 1, z) > 0.62:
                    g.set(x, y, z, CORAL)
    rubble(g, cells, p["rubble"])
    reef(g, 14, 0, 0, g.W - 1, line - 1)
    scorch(g, mid + 6, 19, 2.6, sand=True, rods=1)
    dress(g, 6)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


@layout("glass_garden", "Glass Garden", size=(23, 18, 23),
        blurb="A beach struck again and again: towers of lightning-fused glass round a frame sunk to its knees.")
def glass_garden(g, p):
    """A copper lightning mast drew the storm here for a hundred years: the sand round it is scarred black and grown
    with branching fulgurite towers; the frame stands sunk in the sand, its broken corner melted to glass."""
    mid = g.W // 2
    line = shore_line(g.D)
    court(g, Mix(("sand", 4), ("smooth_sandstone", 1), patch=0.3), 9.5, cz=13, under="sandstone")
    # the frame sunk two rows into the sand, its lost corner fused to glass
    cells = broken_frame(g, mid - 2, -1, line + 1, [(4, 6), (4, 5), (3, 6)])
    for x, y, z in cells:
        g.set(x, y, z, "light_blue_stained_glass", force=True)
    for x in range(mid - 3, mid + 4):
        for z in (line, line + 2):
            g.set(x, 0, z, "sandstone")
            g.set(x, -1, z, "sandstone")
    # the mast: a copper column on a deepslate foot, its rod, a lantern hung from its arm
    mx, mz = mid + 6, line + 2
    for dx in (0, 1):
        for dz in (0, 1):
            g.set(mx + dx, 1, mz + dz, "deepslate_tiles")
    for y in range(2, 12):
        g.set(mx, y, mz, COPPER)
    g.set(mx, 12, mz, "waxed_oxidized_cut_copper")
    g.set(mx, 13, mz, ROD)
    for x in range(mx - 3, mx):
        g.set(x, 10, mz, "waxed_oxidized_cut_copper_slab[type=top]")
    g.set(mx - 3, 9, mz, "chain[axis=y]")
    g.set(mx - 3, 8, mz, "chain[axis=y]")
    g.set(mx - 3, 7, mz, "lantern[hanging=true]")
    # its strikes: scorch stars and fulgurite towers round about
    for (sx, sz, r) in ((mx, mz + 1, 4.5), (mid - 6, 15, 3.0), (mid + 2, 18, 2.2), (mid - 4, line + 2, 2.5)):
        scorch(g, sx, sz, r, sand=True)
    for (fx, fz, h) in ((mid - 7, line + 1, 6), (mid - 8, 14, 4), (mid + 4, 13, 3), (mid + 8, 16, 5),
                        (mid - 3, 19, 2), (2, 9, 3)):
        fulgurite(g, fx, fz, h, branches=2 if h > 3 else 1)
    # the old sandstone ring wall, half buried, broken low
    for k in range(28):
        a = math.pi * (0.05 + 0.9 * k / 27)
        x = int(round(mid + math.cos(a) * 9.5))
        z = int(round(20 - math.sin(a) * 6.5))
        if 0 < x < g.W - 1 and line <= z < g.D - 1 and abs(x - mid) >= 3:
            for y in range(1, 1 + int(1 + 2.4 * g.noise3_at(3.0, x, 0, z))):
                g.set(x, y, z, p["wall"])
    pickles(g, 8, 0, 0, g.W - 1, line - 1)
    reef(g, 10, 0, 0, g.W - 1, line - 1)
    dress(g, 6)
    return [guard(mid - 4, 14), guard(mid + 3, 15)]


@layout("sea_arch", "Sea Arch", size=(25, 20, 25),
        blurb="A great sandstone arch stands in the surf; under it the frame on a shelf of rock in the tide.")
def sea_arch(g, p):
    """A natural arch of layered sandstone spans the shallows behind the beach, its feet in the sea, coral at its
    knees; under it a rock shelf holds the frame, and a rod has been driven into the arch's crown."""
    mid = g.W // 2
    line = shore_line(g.D)
    bands = ("sandstone", "sandstone", "smooth_sandstone", "sandstone", "cut_sandstone", "smooth_sandstone",
             "sandstone", "terracotta", "sandstone", "smooth_sandstone")

    def layers(g, x, y, z):
        """Strata: horizontal bands of sandstone that wobble a little, a pale vein of terracotta in them."""
        k = (y + 4 + int(round((g.noise3_at(6.0, x, 0, z) - 0.5) * 2.5))) % len(bands)
        return bands[k] if g.rng.random() > 0.12 else "sandstone"
    court(g, p["floor"], 8.5, cz=18, under="sandstone", rx=10.0, rz=5.5)
    # the arch: legs at either side of the sea, an underside like a lopsided half circle, 4 deep
    za, zb = 1, 4
    for x in range(1, g.W - 1):
        t = (x - mid) / 9.5
        under = -4 if abs(t) > 0.82 else int(round(-1 + 11.5 * math.sqrt(max(0.0, 1 - t * t)) ** 0.9))
        top = 13 + int(round(1.5 * math.cos(t * 1.4) + (1 if x > mid + 4 else 0)))
        for z in range(za, zb + 1):
            zt = top - (1 if z in (za, zb) and (x + z) % 3 == 0 else 0)
            for y in range(max(under, -4), zt + 1):
                g.set(x, y, z, layers)
    # its feet broaden into the sea floor
    for cx in (2, g.W - 3):
        mound(g, cx, 3, 3.4, 4.5, layers, y0=-3, rz=4.0)
    # the shelf under it, the frame standing on it, its broken top corner
    for x in range(mid - 4, mid + 5):
        for z in range(za, line + 1):
            if math.hypot((x - mid) / 4.5, (z - 6) / 3.4) <= 1.0 or z >= line - 1:
                g.set(x, 0, z, "smooth_sandstone" if (x + z) % 3 else "prismarine_bricks")
                sea_floor(g, x, z, x, z, "sandstone", y_top=-1, y_bot=-3)
    cells = broken_frame(g, mid - 2, 1, 6, [(4, 6), (3, 6)])
    rubble(g, cells, p["rubble"])
    # the rod in the crown, its sea lantern, and the scorch it threw down the arch's face
    g.set(mid, 14, 2, "sea_lantern")
    g.set(mid, 15, 2, ROD)
    for y in range(8, 14):
        if g.get(mid + (y % 2), y, zb) is not None:
            g.set(mid + (y % 2), y, zb, "blackstone")
    # coral at the arch's knees
    for x in range(g.W):
        for z in range(0, zb + 2):
            for y in range(-2, 3):
                s = g.get(x, y, z)
                if s is not None and "sandstone" in s and g.noise3_at(2.5, x, y, z) > 0.7:
                    g.set(x, y, z, CORAL)
    pickles(g, 12, 0, 0, g.W - 1, line - 1)
    reef(g, 16, 0, 0, g.W - 1, line - 1)
    for x, z in ((mid - 5, 12), (mid + 5, 11)):
        rod_post(g, x, z, 2, shaft="cut_sandstone")
    scorch(g, mid - 6, 19, 2.4, sand=True)
    dress(g, 6)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [coral_ring, drowned_pier, temple_steps, glass_garden, sea_arch]
DROPPED = []
