"""Fire's basalt deltas ruins: basalt and smooth basalt, blackstone, the piglins' polished blackstone bricks with
gold in them, magma glowing in the cracks, lava held in the rock; fire on the magma that never goes out."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, guard, layout, mound, pillar, rubble,
                     scatter_on_floor, slab, stairs)

from ruindesigns.fire.netherkit import (BASALT, BLACKSTONE, fire_on, lava_pool, oval_cells)

P = dict(
    floor=Mix(("basalt[axis=y]", 4), ("blackstone", 3), ("smooth_basalt", 1), patch=0.55),
    basalt=BASALT,
    smooth="smooth_basalt",
    stone=BLACKSTONE,
    trim="chiseled_polished_blackstone",
    rubble=Mix(("blackstone", 2), ("basalt[axis=y]", 2), ("polished_blackstone_bricks", 1)),
    light="fire",
)

AREA = Area(element="fire", id="basalt_deltas", name="Basalt Deltas", biomes="basalt deltas", setting=SURFACE,
            ground="basalt", under="blackstone", palette=P,
            blurb="Basalt and blackstone, the piglins' gilded bricks, magma in the cracks and lava held in the rock.")


def dress(g, count=12):
    """Magma in the ground with fire on it, basalt nubs, blackstone pebbles, a glint of gilded blackstone."""
    def place(x, y, z):
        s = g.get(x, y, z)
        rocky = s is None or any(s.endswith(":" + b) for b in ("basalt", "blackstone", "smooth_basalt", "magma_block"))
        if s is not None and "basalt[" in s:
            rocky = True
        if not rocky:
            return False
        r = g.rng.random()
        if r < 0.25:
            g.set(x, y, z, "magma_block")
            return True
        if r < 0.45:
            return fire_on(g, x, y + 1, z, base="magma_block")
        if r < 0.68:
            g.set(x, y + 1, z, BASALT)
            if g.rng.random() < 0.4 and g.empty(x, y + 2, z):
                g.set(x, y + 2, z, BASALT)
            return True
        if r < 0.9:
            g.set(x, y + 1, z, "blackstone" if g.rng.random() < 0.6 else slab("blackstone_slab"))
            return True
        g.set(x, y + 1, z, "gilded_blackstone")
        return True
    scatter_on_floor(g, count, place)


@layout("lava_terraces", "Lava Terraces", size=(25, 22, 25), ground=4,
        blurb="Natural terraces of basalt stepping up to the frame, each holding a long pool of lava behind its lip.")
def lava_terraces(g, p):
    """Three terraces of basalt climb to the back, their lips of smooth basalt holding pools of lava like steps of a
    fountain; a stair of polished blackstone climbs through them up the middle; the frame stands on the top terrace
    with fires on magma either side; basalt columns rise at the sides."""
    mid = g.W // 2
    court(g, p["floor"], 11.0, cz=14, under="blackstone")
    terraces = ((2, 10, 14, 2.0), (4, 5, 9, 3.4), (6, 0, 4, 5.4))   # (top y, z from, z to, x inset)
    for top, za, zb, inset in terraces:
        for z in range(za, zb + 1):
            wob = (g.noise3_at(4.0, 0, top, z) - 0.5) * 3
            xa = int(round(inset + wob))
            xb = int(round(g.W - 1 - inset + wob * 0.6))
            for x in range(max(xa, 0), min(xb, g.W - 1) + 1):
                for y in range(1, top + 1):
                    g.set(x, y, z, Mix(("basalt[axis=y]", 4), ("blackstone", 1), ("gilded_blackstone", 0.08)))
                lip = z == zb or x in (xa, xb)
                g.set(x, top, z, "smooth_basalt" if lip else Mix(("basalt[axis=y]", 3), ("smooth_basalt", 1)))
    # the pools: lava in the middle of the two lower terraces, held by their lips; magma under it
    for top, za, zb, inset in terraces[:2]:
        cells = []
        for x in range(int(inset) + 4, g.W - int(inset) - 4):
            if abs(x - mid) <= 2:
                continue
            for z in range(za + 1, zb):
                cells.append((x, z))
        lava_pool(g, cells, top, "smooth_basalt", "magma_block")
    # the stair up the middle, a step of polished blackstone up each lip
    stair = "polished_blackstone_stairs"
    for x in range(mid - 1, mid + 2):
        for (top, za, zb, _inset) in terraces:
            g.set(x, top - 1, zb + 1, stairs(stair, "north"))
            for y in range(1, top - 1):
                g.set(x, y, zb + 1, "polished_blackstone")
            g.set(x, top, zb + 1, AIR, force=True)
        for (top, za, zb, _inset) in terraces[:2]:
            for z in range(za, zb + 1):
                g.set(x, top, z, "polished_blackstone_bricks")
    # the frame on the top terrace, fires on magma either side
    cells = broken_frame(g, mid - 2, 7, 1, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"], spread=2)
    for x in (mid - 4, mid + 4):
        g.set(x, 6, 3, "magma_block")
        g.set(x, 7, 3, "fire")
    # basalt columns at the sides
    for x, z, h in ((1, 6, 9), (0, 9, 6), (g.W - 2, 3, 11), (g.W - 1, 7, 7), (2, 17, 3), (g.W - 3, 18, 4)):
        pillar(g, x, z, h, BASALT)
        g.set(x, h + 1, z, "polished_basalt[axis=y]")
    dress(g, 10)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


@layout("causeway", "Causeway", size=(25, 20, 25),
        blurb="A causeway of basalt columns rising in steps to the frame, magma and lava glowing in the cracks.")
def causeway(g, p):
    """The ground breaks into columns of basalt, each a few blocks across with a flat top, low at the front and rising
    in steps to the back where the tallest flank the frame on its flat-topped group; magma and pockets of lava glow
    in the cracks between them; a way up the middle in steps of one."""
    mid = g.W // 2
    sites = []
    for gx in range(-1, 13):
        for gz in range(-1, 13):
            sx = gx * 2.3 + (g.rng.random() - 0.5) * 1.4 + (1.15 if gz % 2 else 0)
            sz = gz * 2.0 + (g.rng.random() - 0.5) * 1.2
            sites.append((sx, sz))

    def column_height(sx, sz):
        if sz > 15.5:
            return 0 if abs(sx - mid) < 9 else 1
        depth = (15.5 - sz) / 15.5
        side = abs(sx - mid) / 12.0
        h = 1 + depth * 5.5 + side * 2.0 + (g.noise3_at(3.0, int(sx) % g.W, 0, int(sz) % g.D) - 0.5) * 2.0
        if abs(sx - mid) <= 1.8:
            h = 1 + depth * 3.2
        return max(0, int(round(h)))

    heights = [column_height(sx, sz) for sx, sz in sites]
    hmap = {}
    crack = set()
    for x in range(g.W):
        for z in range(g.D):
            d = sorted((math.hypot(x - sx, z - sz), i) for i, (sx, sz) in enumerate(sites))
            (d1, i1), (d2, _i2) = d[0], d[1]
            h = heights[i1]
            group = mid - 3 <= x <= mid + 3 and 2 <= z <= 5
            if group:
                h = 3
            if d2 - d1 < 0.38 and h > 0 and not group:
                crack.add((x, z))
            hmap[(x, z)] = h
    for (x, z), h in hmap.items():
        if (x, z) in crack and h > 0:
            h = max(0, h - 1)
            hmap[(x, z)] = h
        for y in range(1, h + 1):
            g.set(x, y, z, BASALT)
        if h > 0:
            g.set(x, h, z, Mix(("polished_basalt[axis=y]", 2), ("smooth_basalt", 1), ("basalt[axis=y]", 1)))
        if (x, z) in crack:
            g.set(x, h, z, "magma_block" if g.rng.random() < 0.45 else "blackstone")
    # lava in cracks that sit lower than everything round them
    for (x, z) in sorted(crack):
        h = hmap[(x, z)]
        if h < 1 or not (2 <= x < g.W - 2 and 7 <= z <= 14) or abs(x - mid) <= 3:
            continue
        if all(hmap.get((x + dx, z + dz), 0) > h for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            lava_pool(g, [(x, z)], h, "basalt[axis=y]", "magma_block")
    # the frame on its group of columns, fires on magma by it
    cells = broken_frame(g, mid - 2, 4, 4, [(4, 6), (4, 5), (3, 6), (4, 4)])
    rubble(g, cells, p["rubble"], spread=2)
    for x in (mid - 3, mid + 3):
        g.set(x, 3, 5, "magma_block")
        g.set(x, 4, 5, "fire")
    dress(g, 8)
    return [guard(mid - 4, 19), guard(mid + 4, 19)]


@layout("black_forge", "Black Forge", size=(25, 18, 25),
        blurb="The piglins' forge in polished blackstone: a magma hearth under its chimney, a lava trough, anvils.")
def black_forge(g, p):
    """A smithy hall of polished blackstone bricks, its front fallen away: at the left the great hearth of magma
    burning under a hood and chimney, at the right a trough of lava for the quench, anvils and a smithing table
    between them, a heap of gilded blackstone; lanterns hang on chains from the roof beams; the frame stands against
    the back wall between hearth and trough."""
    mid = g.W // 2
    court(g, p["floor"], 11.0, cz=13, under="blackstone")
    x0, x1, z0, z1 = 3, g.W - 4, 1, 11
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, 0, z, Mix(("polished_blackstone", 3), ("blackstone", 2), ("polished_blackstone_bricks", 1)))
    # the walls: the back whole, the sides falling away towards the front, the front only stubs
    for x in range(x0, x1 + 1):
        for y in range(1, 10):
            g.set(x, y, z0, p["stone"])
    for z in range(z0, z1 + 1):
        top = 8 - max(0, z - 4)
        for x in (x0, x1):
            for y in range(1, max(top, 1) + 1):
                g.set(x, y, z, p["stone"])
    for x in (x0, x0 + 4, x1 - 4, x1):
        for y in range(1, 4 if x in (x0, x1) else 3):
            g.set(x, y, z1, p["trim"] if y == 1 else p["stone"])
    g.ruin_tops(x0, z0, x1, z1, 3, 9, 4.0, 8.5, scale=3.0, salt=6, region=lambda x, z: z > z0 or x > x1 - 3)
    # a lean-to roof over the back third, held by the back wall, its end broken
    for x in range(x0, x1 + 1):
        for z in (2, 3, 4):
            if x > x1 - 3 - (z - 2) * 2:
                continue
            g.set(x, 9, z, slab("polished_blackstone_brick_slab") if z == 4 else "polished_blackstone_bricks")
    # the hearth: magma under a hood, the chimney up the back wall
    for x in range(x0 + 1, x0 + 6):
        for z in range(z0 + 1, z0 + 5):
            g.set(x, 1, z, "polished_blackstone_bricks")
    for x in range(x0 + 2, x0 + 5):
        for z in range(z0 + 1, z0 + 4):
            g.set(x, 1, z, "magma_block")
            g.set(x, 2, z, "fire")
    for x in range(x0 + 1, x0 + 6):
        for z in range(z0 + 1, z0 + 5):
            if x in (x0 + 1, x0 + 5) or z == z0 + 4:
                g.set(x, 4, z, "polished_blackstone_bricks")
                g.set(x, 5, z, "polished_blackstone_brick_wall" if z == z0 + 4 else "polished_blackstone_bricks")
    for y in range(6, 13):
        for x in range(x0 + 2, x0 + 5):
            for z in range(z0 + 1, z0 + 4):
                edge = x in (x0 + 2, x0 + 4) or z in (z0 + 1, z0 + 3)
                if edge:
                    g.set(x, y, z, "polished_blackstone_bricks")
    g.set(x0 + 6, 1, z0 + 2, "blast_furnace[facing=south,lit=true]")
    g.set(x0 + 6, 1, z0 + 1, "blast_furnace[facing=south,lit=true]")
    # the quench trough of lava on the right, walled in blackstone
    trough = [(x, z) for x in range(x1 - 4, x1 - 1) for z in range(z0 + 2, z0 + 4)]
    for x in range(x1 - 5, x1):
        for z in range(z0 + 1, z0 + 5):
            g.set(x, 1, z, "polished_blackstone_bricks")
    lava_pool(g, trough, 1, "polished_blackstone_bricks", "magma_block")
    g.set(x1 - 1, 1, z0 + 5, "lava_cauldron")
    # the anvils and the smithing table, the heap of gilded blackstone
    g.set(mid - 4, 1, 7, "anvil[facing=east]")
    g.set(mid + 4, 1, 7, "chipped_anvil[facing=west]")
    g.set(mid - 4, 1, 9, "smithing_table")
    g.rubble_pile(x0 + 2, 9, 1.8, 1.6, Mix(("gilded_blackstone", 2), ("blackstone", 1)))
    # the frame against the back wall, lanterns on chains from the beams
    cells = broken_frame(g, mid - 2, 1, 2, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"], spread=3)
    for x, z, n in ((mid - 4, 3, 2), (mid + 4, 3, 2), (mid, 4, 0)):
        for k in range(n):
            g.set(x, 8 - k, z, "chain[axis=y]")
        g.set(x, 8 - n, z, "lantern[hanging=true]")
    dress(g, 9)
    return [guard(mid - 4, 16), guard(mid + 4, 16)]


@layout("bastion_ramp", "Bastion Ramp", size=(25, 22, 25),
        blurb="A great wall of a ruined bastion, a ramp climbing its face to the rampart where the frame stands.")
def bastion_ramp(g, p):
    """A massive block of bastion wall across the back, gilded here and there, buttressed; a ramp of polished
    blackstone stairs climbs its face from the left to a landing and the rampart, where the frame stands by a broken
    parapet; chains and lanterns hang down the wall, a breach torn in its right end, a trench of lava at its foot."""
    mid = g.W // 2
    court(g, p["floor"], 11.0, cz=14, under="blackstone")
    wx0, wx1, wz0, wz1, top = 2, g.W - 3, 0, 5, 9
    for x in range(wx0, wx1 + 1):
        for z in range(wz0, wz1 + 1):
            for y in range(1, top + 1):
                g.set(x, y, z, p["stone"])
            g.set(x, top, z, Mix(("polished_blackstone_bricks", 3), ("polished_blackstone", 1)))
    # the parapet along the back edge of the rampart, broken
    for x in range(wx0, wx1 + 1):
        if g.noise3_at(3.0, x, top + 1, 0) > 0.3:
            g.set(x, top + 1, wz0, "polished_blackstone_brick_wall")
    # buttresses on the face, chiseled bands
    for x in (16, 21):
        for y in range(1, top):
            for z in (wz1 + 1, wz1 + 2):
                if z == wz1 + 2 and y > top - 3:
                    continue
                g.set(x, y, z, p["stone"] if y % 4 else "chiseled_polished_blackstone")
    # the breach in its right end
    g.smash(wx1 - 2, 5, 3, 3.4)
    g.settle_rubble(0.4, 1.6, p["rubble"], y_max=1)
    # the ramp: stairs climbing east along the face from the front left, a solid body under them, a landing on top
    stair = "polished_blackstone_brick_stairs"
    for k in range(top):
        x = 1 + k
        for z in (wz1 + 1, wz1 + 2):
            for y in range(1, 1 + k):
                g.set(x, y, z, p["stone"], force=True)
            g.set(x, 1 + k, z, stairs(stair, "east"), force=True)
            for y in range(2 + k, 2 + k + 3):
                g.set(x, y, z, AIR, force=True)
    for x in range(1 + top, 1 + top + 3):
        for z in (wz1 + 1, wz1 + 2):
            for y in range(1, top + 1):
                g.set(x, y, z, p["stone"], force=True)
    # the frame on the rampart, fires on magma either side
    cells = broken_frame(g, mid - 1, top + 1, 2, [(4, 6), (4, 5), (3, 6), (4, 4)])
    rubble(g, cells, p["rubble"], spread=2)
    for x in (mid - 3, mid + 5):
        g.set(x, top, 3, "magma_block")
        g.set(x, top + 1, 3, "fire")
    # chains with lanterns hanging down the face off the rampart's edge
    for x in (14, 19):
        if g.get(x, top, wz1) is not None:
            for y in range(top - 1, top - 4, -1):
                g.set(x, y, wz1 + 1, "chain[axis=y]")
            g.set(x, top - 4, wz1 + 1, "lantern[hanging=true]")
            g.set(x, top, wz1 + 1, "polished_blackstone_bricks")
    # the trench of lava at the wall's foot on the right
    trench = [(x, z) for x in range(13, 22) for z in (wz1 + 4, wz1 + 5)]
    lava_pool(g, trench, 0, "blackstone", "magma_block")
    for x in range(12, 23):
        for z in (wz1 + 3, wz1 + 6):
            if g.noise3_at(2.0, x, 1, z) > 0.45:
                g.set(x, 1, z, "polished_blackstone_brick_wall")
    for x, z, h in ((0, 12, 5), (g.W - 1, 13, 7), (1, 20, 3)):
        pillar(g, x, z, h, BASALT)
    dress(g, 9)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# The candidates (November 2026): the user picks; any not picked go to DROPPED.
LAYOUTS = [lava_terraces, causeway, black_forge, bastion_ramp]
DROPPED = []
