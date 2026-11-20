"""Earth's windswept hills ruins (windswept hills, windswept gravelly hills, windswept forest): bare stone and andesite,
gravel scree, wind-bent spruces, a hillside quarry, a crag, a shepherd's fold, a broken watchtower; now and then a
glint of emerald in the rock."""
import math

from ruinkit import AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, guard, layout, mound, rubble, slab, stairs

from .earthkit import boulder, crag, flowers, spruce

P = dict(
    floor=Mix(("stone", 3), ("andesite", 3), ("gravel", 2), ("cobblestone", 1), patch=0.55),
    wall=Mix(("cobblestone", 3), ("andesite", 3), ("mossy_cobblestone", 1), ("stone", 2), patch=0.45),
    rock=Mix(("stone", 5), ("andesite", 4), ("tuff", 1), ("cobblestone", 1), patch=0.6, scale=3.0),
    rubble=Mix(("cobblestone", 2), ("andesite", 2), ("gravel", 1)),
)

AREA = Area(element="earth", id="windswept_hills", name="Windswept Hills", biomes="windswept hills, windswept "
            "gravelly hills, windswept forest", setting=SURFACE, ground="grass_block", under="stone", palette=P,
            blurb="Quarries, crags and broken towers on the bare windswept hills.")

WIND = (1, 0)   # the spruces lean east, downwind


def scree(g, cx, cz, r, avoid=None):
    """A fan of gravel and loose stone on the ground."""
    for x in range(g.W):
        for z in range(g.D):
            if avoid and avoid(x, z):
                continue
            d = math.hypot(x - cx, z - cz) / r + (g.noise3_at(3.0, x, 0, z) - 0.5) * 0.6
            if d < 1.0 and g.get(x, 0, z) is None:
                g.set(x, 0, z, Mix(("gravel", 3), ("andesite", 1), ("cobblestone", 1), patch=0.3))
                if d < 0.6 and g.chance(0.15) and g.empty(x, 1, z):
                    g.set(x, 1, z, Mix(("cobblestone", 1), ("andesite", 1)))


def emeralds(g, n, x1, y1, z1, x2, y2, z2):
    """A few emerald ores in exposed rock faces."""
    done = 0
    for _ in range(n * 40):
        if done >= n:
            break
        x, y, z = g.rng.randint(x1, x2), g.rng.randint(y1, y2), g.rng.randint(z1, z2)
        s = g.get(x, y, z)
        if s and s.split(":")[-1] in ("stone", "andesite") and any(
                g.empty(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            g.set(x, y, z, "emerald_ore")
            done += 1


@layout("quarry", "Hillside Quarry", size=(25, 18, 23),
        blurb="An old quarry cut into the hill, its face stepped in benches, a timber crane over the frame.")
def quarry(g, p):
    """A stone hill across the back with a quarry bitten out of its front: a stepped working face, a floor of chips and
    gravel, cut blocks stacked ready, a spruce derrick with its chain still hanging, an old rail line; the frame stands
    against the face; a vein of emerald glints in it."""
    mid = g.W // 2
    # the hill, highest at the back
    for x in range(g.W):
        for z in range(g.D):
            h = 10.5 - z * 0.75 - abs(x - mid) * 0.12 + (g.noise3_at(3.0, x, 0, z) - 0.5) * 2.5
            hy = int(round(h))
            if hy < 1:
                continue
            for y in range(1, hy + 1):
                g.set(x, y, z, p["rock"])
            g.set(x, hy, z, "grass_block" if g.noise3_at(2.5, x, 3, z) > 0.3 else "gravel")
    # the quarry pit: everything above the ground cut out, the back face stepped in two benches
    for x in range(mid - 7, mid + 8):
        for z in range(2, g.D):
            edge = abs(x - mid) == 7
            top = 99
            if z == 2:
                top = 6
            elif z == 3:
                top = 3 if abs(x - mid) > 3 else 0
            for y in range(max(1, top + 1 if top < 99 else 1), 14):
                if top == 99 or y > top:
                    if not (edge and y <= 2 and g.chance(0.6)):
                        g.set(x, y, z, AIR, force=True)
            g.set(x, 0, z, p["floor"])
    for x in range(mid - 7, mid + 8):
        for y in range(7, 14):
            g.set(x, y, 2, AIR, force=True)
    # the frame against the face
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (3, 6), (4, 5)])
    for x in range(mid - 2, mid + 3):
        for y in range(1, 8):
            if g.get(x, y, 3) is not None and y > 0:
                g.set(x, y, 3, p["rock"])
    rubble(g, cells, p["rubble"])
    emeralds(g, 4, mid - 7, 1, 1, mid + 7, 8, 3)
    # cut blocks stacked on the quarry floor
    for x, z, n in ((mid - 6, 6, 2), (mid - 5, 6, 2), (mid - 6, 7, 1), (mid + 5, 9, 2), (mid + 6, 9, 1), (mid + 6, 10, 1)):
        for y in range(1, n + 1):
            g.set(x, y, z, "polished_andesite" if (x + y) % 2 else "stone_bricks")
    # the derrick: a spruce mast, a boom out over the floor, a chain and a hanging block
    mx, mz = mid + 6, 5
    for y in range(1, 11):
        g.set(mx, y, mz, "spruce_log[axis=y]")
    hx, hz = mx - 5, mz + 2
    g.line((mx - 1, 10, mz), (hx, 10, hz), "spruce_planks")
    g.line((mx, 9, mz + 1), (mx - 2, 10, mz + 1), "spruce_fence")
    for y in (9, 8, 7):
        g.set(hx, y, hz, "chain[axis=y]")
    g.set(hx, 6, hz, "polished_andesite")
    for dx, dz in ((1, 0), (0, 1), (-1, 0)):
        g.set(mx + dx, 1, mz + dz, "cobblestone")
    # the rail line along the floor, a stretch of it broken
    for z in range(9, g.D - 1):
        if z not in (14, 15):
            g.set(mid - 4, 1, z, "rail[shape=north_south]")
    # the spoil heap of gravel at the front corner
    g.rubble_pile(g.W - 4, g.D - 4, 3.5, 3.0, Mix(("gravel", 3), ("cobblestone", 1)))
    g.set(mid - 6, 2, 6, "lantern")
    g.set(mid + 5, 3, 9, "lantern")
    # spruces on the hill above, bent by the wind
    spruce(g, 2, 3, 7, y0=g.top_y(2, 3) + 1, bent=WIND)
    spruce(g, g.W - 3, 2, 6, y0=g.top_y(g.W - 3, 2) + 1, bent=WIND)
    flowers(g, 10, kinds=("cornflower", "azure_bluet"), grass=0.7, y_max=12)
    return [guard(mid - 3, 12), guard(mid + 3, 12)]


@layout("crag", "The Crag", size=(25, 22, 25),
        blurb="Two jagged crags of bare rock, the frame wedged in the cleft between them, scree at their feet.")
def crag_layout(g, p):
    """A great crag on the west and a lesser one on the east, a narrow cleft between them where the frame is wedged;
    wind-bent spruces on their ledges, a fan of scree spilling out in front, a cairn by the way up."""
    mid = g.W // 2
    big = crag(g, 5.5, 6.0, 7.5, 6.5, 17.0, p["rock"], lean=(-0.15, -0.1))
    small = crag(g, g.W - 6.0, 5.5, 6.0, 5.0, 9.0, p["rock"], lean=(0.1, 0.0))
    # the cleft: cut down to the ground between them, the frame standing in it
    for x in range(mid - 3, mid + 4):
        for z in range(0, 12):
            for y in range(1, 20):
                if abs(x - mid) <= 2 or y > 3 + abs(x - mid):
                    g.set(x, y, z, AIR, force=True)
    for x in range(mid - 3, mid + 4):
        for z in range(0, 12):
            g.set(x, 0, z, Mix(("gravel", 2), ("stone", 1)))
    cells = broken_frame(g, mid - 2, 1, 5, [(0, 6), (1, 6), (0, 5), (2, 6)])
    rubble(g, cells, p["rubble"])
    # a chockstone jammed high in the cleft over it, resting on both sides
    for x in range(mid - 3, mid + 4):
        g.set(x, 11, 3, "andesite")
        g.set(x, 11, 4, "stone")
    for x in (mid - 3, mid + 3):
        for y in range(4, 11):
            g.set(x, y, 3, p["rock"])
            g.set(x, y, 4, p["rock"])
    emeralds(g, 3, 0, 1, 0, g.W - 1, 14, 12)
    # spruces on the crags' ledges
    for tx, tz, h in ((4, 11, 6), (g.W - 4, 9, 5), (8, 3, 4)):
        y0 = g.top_y(tx, tz) + 1
        if y0 < 14:
            g.set(tx, y0 - 1, tz, "grass_block")
            spruce(g, tx, tz, h, y0=y0, bent=WIND)
    scree(g, mid - 3, 13, 7.0)
    scree(g, mid + 6, 12, 5.0)
    # a cairn and a boulder in front
    cx, cz = 3, 18
    for y, w in ((1, 1), (2, 1), (3, 0), (4, 0)):
        for dx in range(-w, w + 1):
            for dz in range(-w, w + 1):
                if abs(dx) + abs(dz) <= w:
                    g.set(cx + dx, y, cz + dz, p["wall"])
    boulder(g, g.W - 5, 1, 19, 1.8, p["rock"], top="moss_carpet")
    flowers(g, 14, kinds=("cornflower", "azure_bluet", "oxeye_daisy"), grass=0.6, y_max=14,
            avoid=lambda x, z: abs(x - mid) < 6 and 12 < z < 20)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


@layout("shepherds_fold", "Shepherd's Fold", size=(25, 16, 25),
        blurb="A dry-stone sheep fold and a beehive hut fallen in, the frame standing where the gate was.")
def shepherds_fold(g, p):
    """A round fold of dry-stone walls, gapped and tumbled; a corbelled beehive hut half fallen in at its back; the
    frame stands at the back where the fold's far gate was; gravel worn bare inside, a broken fence pen, spruces
    bent over it."""
    mid, cz = g.W // 2, 12
    # gravel and grass worn inside
    court(g, Mix(("gravel", 2), ("coarse_dirt", 2), ("grass_block", 3), ("andesite", 1), patch=0.6), 8.5, cz=cz,
          under="stone")
    # the fold's wall: a ring 2 high, a gap at the front and one at the back for the frame
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot(x - mid, z - cz)
            if 9.0 <= d < 10.0:
                if abs(x - mid) <= 1 and z > cz:
                    continue
                if abs(x - mid) <= 3 and z < cz:
                    continue
                h = 2 if g.noise3_at(3.0, x, 2, z) > 0.3 else 1
                if g.noise3_at(2.5, x, 1, z) < 0.12:
                    continue
                for y in range(1, h + 1):
                    g.set(x, y, z, p["wall"])
                if h == 2 and g.chance(0.3):
                    g.set(x, 3, z, slab("cobblestone_slab"))
    # the frame in the back gap
    cells = broken_frame(g, mid - 2, 1, 2, [(4, 6), (3, 6), (2, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the beehive hut: corbelled rings of stone, its east side fallen in
    hx, hz = 6, 7
    for y in range(1, 7):
        r = 3.2 - (y - 1) * 0.5
        g.disc(hx, hz, r, y, Mix(("cobblestone", 2), ("andesite", 2), ("mossy_cobblestone", 1)), r_in=r - 1.05)
    g.set(hx, 7, hz, "andesite")
    g.smash(hx + 2.6, 5, hz + 1.5, 1.9)
    for x in range(hx - 1, hx + 2):
        for y in range(1, 3):
            g.set(x, y, hz + 3, AIR, force=True)
    g.set(hx, 1, hz, "campfire[lit=false]")
    g.rubble_pile(hx + 3, hz + 3, 2.0, 1.5, p["rubble"])
    # a broken fence pen on the east
    for z in range(13, 18):
        if z != 15:
            g.set(mid + 6, 1, z, "spruce_fence")
    for x in range(mid + 3, mid + 6):
        g.set(x, 1, 13, "spruce_fence")
    g.set(mid + 4, 1, 16, "hay_block[axis=y]")
    g.set(mid + 5, 1, 17, "hay_block[axis=x]")
    # spruces outside, leaning downwind
    spruce(g, g.W - 3, 4, 8, bent=WIND)
    spruce(g, 1, 20, 6, bent=WIND)
    spruce(g, g.W - 2, 21, 5, bent=WIND)
    boulder(g, 2, 1, 14, 1.5, p["rock"])
    g.set(mid - 3, 1, 3, "lantern")
    flowers(g, 16, kinds=("cornflower", "azure_bluet", "dandelion"), grass=0.7,
            avoid=lambda x, z: abs(x - mid) < 6 and 9 < z < 19)
    return [guard(mid - 3, 13), guard(mid + 3, 13)]


@layout("watchtower", "Old Watchtower", size=(25, 20, 25),
        blurb="A round watchtower snapped off and fallen down the hill; the frame stands in its open shell.")
def watchtower(g, p):
    """A round tower of andesite and cobble, its front torn away down to its footing so its back shell stands like a
    broken cup round the frame; the fallen top lies down the slope as a long ridge of rubble and dressed blocks."""
    mid, cx, cz = g.W // 2, g.W // 2, 6
    # a low grassy rise it stands on
    mound(g, cx, cz, 10.0, 1.6, p["rock"], top="grass_block", y0=0, rz=7.0)
    base = 1
    # the shell: walls 1 thick, radius 4.6, taller at the back
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot(x - cx, z - cz)
            if 3.6 <= d < 4.7:
                ang = math.atan2(z - cz, x - cx)            # +pi/2 is the front (south)
                front = math.sin(ang)                       # 1 at the front, -1 at the back
                h = int(round(14 - (front + 1) * 6.5 + (g.noise3_at(2.0, x, 0, z) - 0.5) * 3))
                if front > 0.55:
                    h = min(h, 1)
                for y in range(base, base + max(h, 0) + 1):
                    g.set(x, y, z, Mix(("andesite", 3), ("cobblestone", 2), ("stone_bricks", 1), patch=0.5))
    # its floor and arrow slits in the back
    g.disc(cx, cz, 3.7, 1, Mix(("cobblestone", 2), ("andesite", 1), ("gravel", 1)))
    for y, dx in ((8, -3), (11, 2), (6, 4)):
        for x in range(g.W):
            for z in range(g.D):
                if abs(x - (cx + dx)) <= 0 and z < cz and g.get(x, y, z):
                    g.set(x, y, z, AIR, force=True)
                    g.set(x, y + 1, z, AIR, force=True)
    # a stair of corbels winding up the inside of the back wall
    for k, (x, z) in enumerate(((cx - 3, cz + 1), (cx - 3, cz), (cx - 3, cz - 1), (cx - 2, cz - 2), (cx - 1, cz - 3))):
        g.set(x, 3 + k, z, slab("cobblestone_slab", "top"))
    cells = broken_frame(g, mid - 2, 2, cz, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    # the fallen top: a long ridge of rubble down the slope to the front-east, dressed blocks in it
    g.line((cx + 4, 2, cz + 4), (cx + 9, 1, cz + 15), Mix(("andesite", 2), ("cobblestone", 2), ("stone_bricks", 1)), r=1.0)
    g.rubble_pile(cx + 9, cz + 15, 2.2, 1.6, p["rubble"])
    for x, z in ((cx + 7, cz + 8), (cx + 10, cz + 12), (cx + 6, cz + 13)):
        g.set(x, 1, z, "chiseled_stone_bricks")
    g.weather(0, 1, 0, g.W - 1, 16, g.D - 1, {"stone_bricks": Mix(("stone_bricks", 2), ("cracked_stone_bricks", 2),
                                                                    ("mossy_stone_bricks", 1))})
    for x in (cx - 2, cx + 2):
        g.set(x, 2, cz + 2, "lantern")
    scree(g, 4, 16, 5.0)
    spruce(g, 2, 4, 9, bent=WIND)
    spruce(g, 3, 21, 6, bent=WIND)
    flowers(g, 14, kinds=("cornflower", "azure_bluet"), grass=0.7, y_max=3,
            avoid=lambda x, z: abs(x - mid) < 6 and 11 < z < 20)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [quarry, crag_layout, watchtower]
DROPPED = [shepherds_fold]
