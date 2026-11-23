"""Fire's warped forest ruins: warped nylium underfoot, warped stems and planks, warped wart blocks with
shroomlights glowing orange in them, twisting vines climbing up off the ground, warped fungi, roots and nether
sprouts; polished blackstone the piglins left."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, guard, layout, mound, pillar, rubble,
                     scatter_on_floor, slab, stairs)

from ruindesigns.fire.netherkit import (RED_BRICK, huge_fungus, lava_pool, oval_cells, plant, twisting)

STEM = "warped_stem[axis=y]"

P = dict(
    floor=Mix(("warped_nylium", 5), ("netherrack", 2), patch=0.6),
    plank="warped_planks",
    wart=Mix(("warped_wart_block", 7), ("shroomlight", 0.35), patch=0.4),
    stone=Mix(("polished_blackstone_bricks", 3), ("blackstone", 2), ("cracked_polished_blackstone_bricks", 1),
              patch=0.45),
    red=RED_BRICK,
    trim="chiseled_polished_blackstone",
    rubble=Mix(("netherrack", 2), ("warped_planks", 1), ("warped_wart_block", 1)),
    light="shroomlight",
)

AREA = Area(element="fire", id="warped_forest", name="Warped Forest", biomes="warped forest", setting=SURFACE,
            ground="warped_nylium", under="netherrack", palette=P,
            blurb="Warped stems and planks among the teal fungi, shroomlights glowing orange, twisting vines climbing.")


def dress(g, count=14):
    """Warped roots, nether sprouts, warped fungi and twisting vines on the nylium; now and then a shroomlight."""
    def place(x, y, z):
        s = g.get(x, y, z)
        if not (s is None or s.endswith(":warped_nylium") or s.endswith(":netherrack")):
            return False
        r = g.rng.random()
        if r < 0.42:
            return plant(g, x, y + 1, z, "warped_roots", "warped_nylium")
        if r < 0.66:
            return plant(g, x, y + 1, z, "nether_sprouts", "warped_nylium")
        if r < 0.82:
            return plant(g, x, y + 1, z, "warped_fungus", "warped_nylium")
        if r < 0.95:
            g.set(x, y, z, "warped_nylium")
            return twisting(g, x, y + 1, z, g.rng.randint(1, 4)) > 0
        g.set(x, y + 1, z, "shroomlight")
        return True
    scatter_on_floor(g, count, place)


def lamp(g, x, z, y=1, h=2, fence="warped_fence"):
    """A fence post with a shroomlight on top."""
    for k in range(h):
        g.set(x, y + k, z, fence)
    g.set(x, y + h, z, "shroomlight")


@layout("twisted_tower", "Twisted Tower", size=(23, 24, 25),
        blurb="A round watchtower of warped planks, its top broken off and its side torn open, vines climbing it.")
def twisted_tower(g, p):
    """A round tower of warped planks on stem ribs at the back left, its crown broken jaggedly, a great bite torn out
    of its side showing its upper floor; twisting vines climb up round it; a shroomlight still glows in its last
    turret; the frame stands beside it, a huge warped fungus behind, the fallen planks heaped at its foot."""
    mid = g.W // 2
    court(g, p["floor"], 10.0, cz=14, under="netherrack")
    tx, tz, r = 5, 6, 3.4
    for y in range(1, 16):
        g.disc(tx, tz, r, y, p["plank"] if y > 1 else p["stone"], r_in=r - 1.0)
    for dx, dz in ((0, -3), (0, 3), (-3, 0), (3, 0)):
        for y in range(1, 16):
            g.set(tx + dx, y, tz + dz, STEM)
    g.disc(tx, tz, r - 1.0, 0, "warped_planks")
    g.disc(tx, tz, r - 1.0, 8, "warped_planks")
    for y in (1, 2, 3):
        g.set(tx + 3, y, tz, AIR, force=True)
    g.ruin_tops(tx - 4, tz - 4, tx + 4, tz + 4, 7, 16, 9.0, 15.0, scale=3.0, salt=3,
                region=lambda x, z: (x - tx) + (z - tz) > -2)
    g.smash(tx + 3, 10, tz + 2, 2.8)
    g.settle_rubble(0.3, 1.6, Mix(("warped_planks", 3), ("warped_stem[axis=x]", 1)), y_max=1)
    # the last turret's lamp: a shroomlight in a cage of fences on the highest stub
    hx, hz = tx - 2, tz - 2
    top = g.top_y(hx, hz)
    if top >= 10:
        g.set(hx, top + 1, hz, "shroomlight")
        for dx, dz in ((1, 0), (0, 1)):
            if g.empty(hx + dx, top + 1, hz + dz) and g.solid(hx + dx, top, hz + dz):
                g.set(hx + dx, top + 1, hz + dz, "warped_fence")
    # vines climbing up round its foot
    for k in range(10):
        a = 2 * math.pi * k / 10 + 0.3
        x = int(round(tx + math.cos(a) * (r + 1.0)))
        z = int(round(tz + math.sin(a) * (r + 1.0)))
        if 0 < x < g.W - 1 and 0 < z < g.D - 1:
            g.set(x, 0, z, "warped_nylium")
            twisting(g, x, 1, z, g.rng.randint(4, 10))
    # the frame beside it, lamps either side
    cells = broken_frame(g, mid + 1, 1, 4, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid, mid + 6):
        g.set(x, 0, 7, p["stone"])
        lamp(g, x, 7, h=2)
    huge_fungus(g, g.W - 3, 1, 1, 9, kind="warped", r=3.0)
    g.rubble_pile(4, 13, 2.4, 1.8, Mix(("warped_planks", 3), ("warped_stem[axis=z]", 1), ("netherrack", 1)))
    dress(g, 14)
    return [guard(mid - 3, 17), guard(mid + 4, 17)]


@layout("sprout_hollow", "Sprout Hollow", size=(25, 18, 25), ground=5,
        blurb="A hollow terraced down into the nylium, its floor thick with sprouts; the frame half sunk at its back.")
def sprout_hollow(g, p):
    """A round hollow dug three deep in terraces held up by warped logs, its floor a carpet of sprouts, roots and
    fungi; the frame stands sunk in its back terrace; two huge warped fungi lean over its rim and twisting vines rise
    round it; shroomlights set in the terraces' faces."""
    mid = g.W // 2
    cx, cz = mid, 11
    levels = ((5.6, -3), (7.2, -2), (8.7, -1))

    def level(r):
        for edge, y in levels:
            if r <= edge:
                return y
        return 0

    for x in range(g.W):
        for z in range(g.D):
            r = math.hypot(x - cx, z - cz) + (g.noise3_at(3.0, x, 0, z) - 0.5) * 0.8
            y = level(r)
            if y == 0:
                continue
            for yy in range(-5, y):
                g.set(x, yy, z, "netherrack")
            g.set(x, y, z, "warped_nylium")
            for yy in range(y + 1, 1):
                g.set(x, yy, z, AIR)
    # the terraces' faces: a log along each riser, a shroomlight set in now and then
    for x in range(1, g.W - 1):
        for z in range(1, g.D - 1):
            y = g.top_y(x, z)
            if y > 0 or y < -3:
                continue
            lower = [g.top_y(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))]
            if min(lower) < y:
                dx, dz = x - cx, z - cz
                axis = "x" if abs(dz) > abs(dx) else "z"
                g.set(x, y, z, "shroomlight" if g.rng.random() < 0.08 else f"stripped_warped_stem[axis={axis}]")
    # the frame sunk at the back
    cells = broken_frame(g, mid - 2, -2, 5, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"], spread=3)
    # the floor's carpet of sprouts, roots and fungi
    for x in range(g.W):
        for z in range(g.D):
            y = g.top_y(x, z)
            if y == -3 and g.empty(x, y + 1, z) and g.rng.random() < 0.55:
                kind = g.rng.choice(("nether_sprouts", "nether_sprouts", "warped_roots", "warped_roots",
                                     "warped_fungus", "crimson_roots"))
                plant(g, x, y + 1, z, kind, "warped_nylium")
    # the fungi over the rim, vines round it
    huge_fungus(g, 3, 3, 1, 9, kind="warped", r=3.4)
    huge_fungus(g, g.W - 3, 7, 1, 6, kind="warped", r=2.8)
    for k in range(14):
        a = 2 * math.pi * k / 14
        x = int(round(cx + math.cos(a) * 10.0))
        z = int(round(cz + math.sin(a) * 10.0))
        if 0 < x < g.W - 1 and 0 < z < g.D - 1 and g.top_y(x, z) == 0 and not (z > cz + 6 and abs(x - mid) < 4):
            g.set(x, 0, z, "warped_nylium")
            twisting(g, x, 1, z, g.rng.randint(2, 7))
    dress(g, 10)
    return [guard(mid - 3, 14, y=-2), guard(mid + 3, 14, y=-2)]


@layout("strider_dock", "Strider Dock", size=(25, 16, 25), ground=4,
        blurb="A warped plank dock run out over a pond of lava to the frame on its island; striders' fungi on the shore.")
def strider_dock(g, p):
    """A pond of lava with a quay of blackstone round its near shore; a dock of warped planks on stem pilings runs out
    over it to a little island where the frame stands; mooring posts with lanterns at its end; warped fungi grown on
    the shore for the striders, twisting vines along it."""
    mid = g.W // 2
    island = set(oval_cells(mid, 5.5, 3.6, 2.6))
    pond = [c for c in oval_cells(mid, 8.5, 9.6, 6.2, g, wobble=0.5) if c not in island and 2 <= c[0] <= g.W - 3
            and 2 <= c[1] <= g.D - 3]
    lava_pool(g, pond, -1, Mix(("netherrack", 3), ("blackstone", 2), patch=0.5), "blackstone", depth=2)
    for x, z in pond:
        g.set(x, 0, z, AIR, force=True)
    # the quay: the shore's edge round the pond faced with polished blackstone, walls along its front
    ponds = set(pond)
    for x in range(g.W):
        for z in range(g.D):
            if (x, z) in ponds or (x, z) in island:
                continue
            if any((x + dx, z + dz) in ponds for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                g.set(x, 0, z, p["stone"])
                g.set(x, -1, z, "blackstone")
                if z > 12 and g.noise3_at(2.5, x, 1, z) > 0.55 and abs(x - mid) > 2:
                    g.set(x, 1, z, "polished_blackstone_brick_wall")
    # the island and the frame on it
    for x, z in island:
        for y in range(-3, 0):
            g.set(x, y, z, "blackstone")
        g.set(x, 0, z, Mix(("warped_nylium", 3), ("netherrack", 1)))
    cells = broken_frame(g, mid - 2, 1, 5, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"], spread=2)
    # the dock on its pilings, a rail along one side, mooring posts with lanterns at its end
    z_end = max(z for x, z in island) + 1
    shore = max(z for x, z in pond if abs(x - mid) <= 1)
    for z in range(z_end, shore + 1):
        for x in range(mid - 1, mid + 2):
            g.set(x, 0, z, "warped_planks", force=True)
        if (z - z_end) % 3 == 0:
            for x in (mid - 1, mid + 1):
                g.set(x, -1, z, STEM, force=True)
                g.set(x, -2, z, STEM, force=True)
        if (z + 1) % 4 and z > z_end + 1:
            g.set(mid + 1, 1, z, "warped_fence")
    for x in (mid - 2, mid + 2):
        for y in (-2, -1, 0, 1):
            g.set(x, y, z_end, STEM, force=True)
        g.set(x, 2, z_end, "lantern[hanging=false]")
    # a broken boat-stage: a few planks left of the dock half over the lava
    for x, z in ((mid - 4, 12), (mid - 5, 12), (mid - 4, 13)):
        if (x, z) in ponds:
            g.set(x, 0, z, slab("warped_slab"), force=True)
    g.set(mid - 4, -1, 12, STEM, force=True)
    g.set(mid - 4, -2, 12, STEM, force=True)
    # the striders' fungi on the shore, vines along it
    for x, z in ((3, 19), (5, 21), (g.W - 4, 19), (g.W - 6, 21), (2, 15), (g.W - 3, 14)):
        if g.top_y(x, z) == 0:
            plant(g, x, 1, z, "warped_fungus", "warped_nylium")
    for x, z in ((1, 10), (2, 5), (g.W - 2, 9), (g.W - 3, 3), (mid + 6, 1)):
        if g.top_y(x, z) == 0 and g.get(x, 0, z) is None:
            g.set(x, 0, z, "warped_nylium")
            twisting(g, x, 1, z, g.rng.randint(3, 7))
    huge_fungus(g, 1, 1, 1, 7, kind="warped", r=2.6)
    dress(g, 10)
    return [guard(mid - 4, 20), guard(mid + 4, 20)]


@layout("lamp_avenue", "Lamp Avenue", size=(25, 18, 25),
        blurb="An avenue of tall shroomlight lamps between wart hedges, some fallen, leading to the frame on its dais.")
def lamp_avenue(g, p):
    """A paved avenue runs from the way in to a blackstone dais at the back where the frame stands; lamp posts of
    warped stem with shroomlights under little roofs line it in pairs, two fallen across the nylium, one broken
    short; hedges of warped wart behind them; twisting vines climb beside the posts; huge fungi at the back corners."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=13, under="netherrack")
    for z in range(6, g.D - 1):
        for x in range(mid - 1, mid + 2):
            if g.noise3_at(2.5, x, 0, z) > 0.16:
                g.set(x, 0, z, Mix(("polished_blackstone_bricks", 3), ("warped_planks", 2), ("blackstone", 1)))
    # the dais and the frame
    for x in range(mid - 4, mid + 5):
        for z in range(2, 7):
            g.set(x, 1, z, p["stone"])
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, 7, stairs("polished_blackstone_brick_stairs", "north"))
    cells = broken_frame(g, mid - 2, 2, 3, [(0, 6), (1, 6), (0, 5), (2, 6)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 4, mid + 4):
        lamp(g, x, 6, y=2, h=1)
    # the lamp posts: (x, z, what became of it)
    posts = [(mid - 3, 9, "lamp"), (mid + 3, 9, "lamp"), (mid - 3, 13, "fallen_w"), (mid + 3, 13, "lamp"),
             (mid - 3, 17, "lamp"), (mid + 3, 17, "short"), (mid - 3, 21, "lamp"), (mid + 3, 21, "fallen_e")]
    for x, z, what in posts:
        g.set(x, 0, z, p["stone"])
        g.set(x, 1, z, p["trim"])
        if what == "lamp":
            for y in range(2, 7):
                g.set(x, y, z, STEM)
            g.set(x, 7, z, "shroomlight")
            g.set(x, 8, z, slab("warped_slab"))
            side = -1 if x < mid else 1
            g.set(x + side, 0, z, "warped_nylium")
            twisting(g, x + side, 1, z, g.rng.randint(3, 6))
        elif what == "short":
            for y in range(2, 4):
                g.set(x, y, z, STEM)
        else:
            side = -1 if what == "fallen_w" else 1
            g.set(x, 2, z, STEM)
            for k in range(1, 6):
                g.set(x + side * k, 1, z, "warped_stem[axis=x]")
            g.set(x + side * 6, 1, z, "shroomlight")
    # the wart hedges behind the posts, broken here and there
    for x in (mid - 6, mid + 6):
        for z in range(7, 23):
            if g.noise3_at(3.0, x, 0, z) < 0.25:
                continue
            h = 1 + (1 if g.noise3_at(2.0, x, 2, z) > 0.45 else 0)
            for y in range(1, h + 1):
                if g.empty(x, y, z):
                    g.set(x, y, z, P["wart"])
    huge_fungus(g, 2, 2, 1, 9, kind="warped", r=3.2)
    huge_fungus(g, g.W - 3, 3, 1, 7, kind="warped", r=2.8)
    dress(g, 12)
    return [guard(mid - 2, 15), guard(mid + 2, 15)]


# The candidates (November 2026): the user picks; any not picked go to DROPPED.
LAYOUTS = [twisted_tower, sprout_hollow, strider_dock, lamp_avenue]
DROPPED = []
