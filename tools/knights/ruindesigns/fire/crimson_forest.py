"""Fire's crimson forest ruins: crimson nylium underfoot, crimson stems and planks, nether wart blocks and
shroomlights, weeping vines hanging from the caps, crimson fungi and roots on the nylium; the piglins' old haunts."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, guard, layout, mound, pillar, rubble,
                     scatter_on_floor, slab, stairs)

from ruindesigns.fire.netherkit import (RED_BRICK, brazier, fire_on, huge_fungus, plant, weeping)

STEM = "crimson_stem[axis=y]"

P = dict(
    floor=Mix(("crimson_nylium", 5), ("netherrack", 2), patch=0.6),
    plank="crimson_planks",
    wart=Mix(("nether_wart_block", 7), ("shroomlight", 0.35), patch=0.4),
    red=RED_BRICK,
    trim="chiseled_nether_bricks",
    rubble=Mix(("netherrack", 2), ("crimson_planks", 1), ("nether_wart_block", 1)),
    light="shroomlight",
)

AREA = Area(element="fire", id="crimson_forest", name="Crimson Forest", biomes="crimson forest", setting=SURFACE,
            ground="crimson_nylium", under="netherrack", palette=P,
            blurb="Crimson stems and wart under the huge fungi, weeping vines and shroomlights, the piglins' old haunts.")


def dress(g, count=14):
    """Crimson roots and fungi on the nylium, a fallen shroomlight, a lump of wart."""
    def place(x, y, z):
        s = g.get(x, y, z)
        if not (s is None or s.endswith(":crimson_nylium") or s.endswith(":netherrack")):
            return False
        r = g.rng.random()
        if r < 0.58:
            return plant(g, x, y + 1, z, "crimson_roots", "crimson_nylium")
        if r < 0.82:
            return plant(g, x, y + 1, z, "crimson_fungus", "crimson_nylium")
        if r < 0.92:
            g.set(x, y + 1, z, "nether_wart_block")
            return True
        g.set(x, y + 1, z, "shroomlight")
        return True
    scatter_on_floor(g, count, place)


def lamp_post(g, x, z, y=1, h=2):
    """A crimson fence post with a shroomlight on top."""
    for k in range(h):
        g.set(x, y + k, z, "crimson_fence")
    g.set(x, y + h, z, "shroomlight")


@layout("wart_garden", "Wart Garden", size=(25, 18, 25),
        blurb="An old nether wart garden: raised beds of soul sand, a broken pergola dripping vines over the frame.")
def wart_garden(g, p):
    """Raised beds of soul sand in crimson plank borders, nether wart growing in them at every age; at the back a
    pergola of crimson stems over the frame on its red brick step, half fallen, weeping vines hanging from its slats;
    shroomlight lamps on fence posts, a huge fungus leaning over the front right corner."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=13, under="netherrack")
    # the path up the middle to the step
    for z in range(7, g.D - 1):
        for x in range(mid - 1, mid + 2):
            if g.noise3_at(3.0, x, 0, z) > 0.18:
                g.set(x, 0, z, Mix(("red_nether_bricks", 2), ("nether_bricks", 1)))
    beds = ((2, 6, 9, 19), (18, 22, 9, 19), (3, 7, 2, 6), (17, 21, 2, 6))
    for xa, xb, za, zb in beds:
        for x in range(xa, xb + 1):
            for z in range(za, zb + 1):
                if x in (xa, xb) or z in (za, zb):
                    if g.noise3_at(2.0, x, 1, z) > 0.12:
                        g.set(x, 1, z, "stripped_crimson_stem[axis=z]" if x in (xa, xb) else
                              "stripped_crimson_stem[axis=x]")
                else:
                    g.set(x, 1, z, "soul_sand")
                    g.set(x, 0, z, "soul_sand")
                    if g.rng.random() < 0.8:
                        g.set(x, 2, z, f"nether_wart[age={g.rng.choice((1, 2, 3, 3))}]")
        inner = xb if xb < mid else xa
        for z in (za, zb):
            g.set(inner, 1, z, "stripped_crimson_stem[axis=y]")
            lamp_post(g, inner, z, y=2, h=1)
    # the step and the frame
    for x in range(mid - 3, mid + 4):
        for z in range(3, 7):
            g.set(x, 1, z, p["red"])
    cells = broken_frame(g, mid - 2, 2, 4, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    # the pergola: four posts, two beams along x, slats along z; its right half has come down
    for x, z, h in ((mid - 4, 2, 8), (mid + 4, 2, 8), (mid - 4, 8, 8), (mid + 4, 8, 5)):
        pillar(g, x, z, h, STEM, base="red_nether_bricks")
    for z in (2, 8):
        for x in range(mid - 5, mid + 6):
            if not (z == 8 and x > mid + 1):
                g.set(x, 9, z, "stripped_crimson_stem[axis=x]")
    for x in range(mid - 5, mid + 4, 2):
        for z in range(1, 10):
            if x > mid and z > 4:
                continue
            g.set(x, 10, z, "crimson_planks")
    for x in range(mid - 5, mid + 4, 2):
        for z in (1, 2, 6, 7, 8):
            if g.get(x, 10, z) is not None and g.rng.random() < 0.6:
                weeping(g, x, 9 if z in (1, 6, 7) else 8, z, g.rng.randint(2, 5))
    # the fallen beam and slats lying across the right bed
    g.line((mid + 4, 6, 8), (mid + 8, 2, 11), "stripped_crimson_stem[axis=x]")
    for x, z in ((mid + 2, 10), (mid + 3, 11), (mid + 5, 7)):
        g.set(x, 1, z, slab("crimson_slab"))
    huge_fungus(g, 1, 8, 1, 8, r=3.0, vines=0.45)
    dress(g, 12)
    return [guard(mid - 4, 16), guard(mid + 4, 16)]


@layout("stem_lodge", "Stem Lodge", size=(25, 20, 25),
        blurb="A piglin hunters' lodge of crimson logs, half its roof fallen in; the frame stands in its hall.")
def stem_lodge(g, p):
    """A long log hall of crimson stems under a pitched roof of crimson stairs, its east half caved in and its front
    wall broken open; the frame stands inside against the back wall, a lantern hanging from the ridge; piglin heads
    on posts at the door, a fire pit in the yard, a fungus peeping over the roof."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=14, under="netherrack")
    x0, x1, z0, z1 = 4, g.W - 5, 2, 11
    # the floor and the log walls
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, 0, z, "crimson_planks")
    for y in range(1, 6):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                g.set(x, y, z, "crimson_stem[axis=x]")
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                g.set(x, y, z, "crimson_stem[axis=z]")
        for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
            g.set(x, y, z, "stripped_crimson_stem[axis=y]")
    # the roof: stairs up both sides to a ridge, gable ends of planks
    for x in range(x0 - 1, x1 + 2):
        for k, z in enumerate(range(z0 - 1, z0 + 5)):
            g.set(x, 5 + k, z, stairs("crimson_stairs", "south"))
        for k, z in enumerate(range(z1 + 1, z1 - 5, -1)):
            g.set(x, 5 + k, z, stairs("crimson_stairs", "north"))
    for x in (x0, x1):
        for z in range(z0 + 1, z1):
            top = 5 + min(z - z0, z1 - z)
            for y in range(6, top + 1):
                g.set(x, y, z, "crimson_planks")
    # the east half caved in, the east wall and the front broken down
    for x in range(mid + 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            for y in range(5, 12):
                if x > mid + 2 + int(g.noise3_at(3.0, x, y, z) * 3):
                    g.clear(x, y, z, count=True)
    g.ruin_tops(mid, z0, x1, z1, 2, 11, 2.0, 5.0, scale=3.0, salt=2,
                region=lambda x, z: x > mid + 2 and (x == x1 or z in (z0, z1)))
    for x in range(mid - 3, mid + 4):
        for y in range(1, 5):
            g.set(x, y, z1, AIR, force=True)
    g.ruin_tops(x0, z1, x1, z1, 1, 5, 1.5, 4.5, scale=2.5, salt=4, region=lambda x, z: abs(x - mid) <= 6)
    g.settle_rubble(0.35, 1.5, Mix(("crimson_planks", 2), ("crimson_stem[axis=x]", 1)), y_max=1)
    # the frame against the back wall, a lantern on a chain from the ridge
    cells = broken_frame(g, mid - 2, 1, 5, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"], spread=3)
    g.set(mid - 5, 9, 7, "chain[axis=y]")
    g.set(mid - 5, 8, 7, "lantern[hanging=true]")
    g.set(mid - 6, 1, 3, "barrel[facing=up]")
    g.set(mid - 6, 1, 4, "barrel[facing=up]")
    g.set(mid - 6, 2, 3, "crimson_trapdoor[facing=north,half=bottom,open=false]")
    # the door posts with piglin heads, the fire pit in the yard
    for x in (mid - 4, mid + 4):
        for y in (1, 2):
            g.set(x, y, z1 + 2, "crimson_fence")
        g.set(x, 3, z1 + 2, "piglin_head[rotation=0]")
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        g.set(mid + dx, 1, 17 + dz, slab("nether_brick_slab"))
    g.set(mid, 0, 17, "netherrack")
    g.set(mid, 1, 17, "campfire[lit=true]")
    huge_fungus(g, 1, 1, 1, 10, r=2.8, vines=0.5)
    dress(g, 12)
    return [guard(mid - 4, 16), guard(mid + 4, 16)]


@layout("weeping_arch", "Weeping Arch", size=(25, 22, 25),
        blurb="Two huge crimson fungi whose caps grew into one arch over the frame, curtained with weeping vines.")
def weeping_arch(g, p):
    """Two great crimson fungi on thick stems either side of the frame, their caps grown together into one red arch
    over it, shroomlights glowing underneath; curtains of weeping vines hang round the sides and the back; an old
    path of red nether bricks leads in under it."""
    mid = g.W // 2
    court(g, p["floor"], 10.5, cz=12, under="netherrack")
    for z in range(6, g.D - 1):
        for x in range(mid - 1, mid + 2):
            if g.noise3_at(2.5, x, 0, z) > 0.25 - (0.2 if z < 12 else 0):
                g.set(x, 0, z, Mix(("red_nether_bricks", 3), ("nether_bricks", 1), ("cracked_nether_bricks", 1)))
    cells = broken_frame(g, mid - 2, 1, 4, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 5, mid + 4):
        top = huge_fungus(g, x, 4, 1, 13, r=5.0, vines=0.0, lights=0.22, droop=2, wide_stem=True)
    # the arch's crown: wart joining the two caps over the frame
    for x in range(mid - 3, mid + 4):
        for z in range(1, 9):
            if math.hypot((x - mid) / 3.4, (z - 4.5) / 3.8) <= 1.0:
                g.set(x, top, z, p["wart"])
                g.set(x, top + 1, z, "nether_wart_block")
    # curtains of weeping vines round the caps' rims: sides and back, never over the frame or the way in
    for x in range(1, g.W - 1):
        for z in range(0, 12):
            if abs(x - mid) <= 3 and z >= 2:
                continue
            for y in range(top + 2, top - 4, -1):
                s = g.get(x, y, z)
                if s is not None and g.empty(x, y - 1, z):
                    if g.rng.random() < 0.42:
                        weeping(g, x, y - 1, z, g.rng.randint(2, 6))
                    break
    # shroomlights fallen from the caps, fungi and roots thick round the stems
    for x, z in ((mid - 8, 11), (mid + 8, 10), (mid - 3, 12)):
        g.set(x, 1, z, "shroomlight")
    dress(g, 20)
    return [guard(mid - 4, 15), guard(mid + 4, 15)]


@layout("wart_heart", "Wart Heart", size=(25, 20, 25),
        blurb="A great growth of nether wart like a heart, split open round the frame, its veins running out.")
def wart_heart(g, p):
    """A lumpy, hollow mass of nether wart grown over the frame, veined with shroomlights and split open at the
    front so the frame shows inside it; crimson hyphae run out from it over the ground like veins; weeping vines
    hang in its mouth; clots of wart and a thicket of fungi round it."""
    mid = g.W // 2
    cz = 6
    court(g, p["floor"], 10.5, cz=12, under="netherrack")
    # the veins first, so the heart sits on them
    for ex, ez in ((1, 2), (2, 12), (g.W - 2, 1), (g.W - 3, 13), (5, 21), (g.W - 6, 21), (mid - 8, 0)):
        axis = "x" if abs(ex - mid) >= abs(ez - cz) else "z"
        g.line((mid, 1, cz), (ex, 1, ez), f"crimson_hyphae[axis={axis}]")
        g.line((mid, 0, cz), (ex, 0, ez), "crimson_nylium")
    # the heart: a hollow lumpy shell, shroomlight veins in it
    heart = Mix(("nether_wart_block", 8), ("crimson_hyphae[axis=y]", 0.8), patch=0.5)
    g.ellipsoid(mid, 3, cz, 7.4, 5.8, 5.2, heart, shell=1.6, only_above=1)
    g.ellipsoid(mid + 3, 7, cz - 1, 3.6, 2.8, 3.2, heart, shell=1.2, only_above=1)
    g.ellipsoid(mid - 3, 3, cz + 1, 3.6, 3.0, 3.4, heart, shell=1.2, only_above=1)
    for x in range(g.W):
        for y in range(1, 13):
            for z in range(g.D):
                s = g.get(x, y, z)
                if s is not None and s.endswith(":nether_wart_block") and g.noise3_at(2.2, x, y, z) > 0.83:
                    g.set(x, y, z, "shroomlight")
    # hollow it right out and split it open at the front
    g.ellipsoid(mid, 3, cz, 5.4, 4.6, 3.4, AIR, only_above=1)
    for x in range(mid - 6, mid + 7):
        for y in range(1, 11):
            for z in range(cz - 1, g.D):
                half = 4.4 - max(0, y - 7) * 1.3 + (g.noise3_at(2.0, x, y, z) - 0.5) * 1.4
                if abs(x - mid) <= half and (z >= cz + 1 or y >= 8):
                    g.set(x, y, z, AIR)
    for x in range(mid - 5, mid + 6):
        for z in range(cz - 4, cz + 5):
            g.set(x, 0, z, Mix(("crimson_nylium", 2), ("nether_wart_block", 1), patch=0.5))
    cells = broken_frame(g, mid - 2, 1, cz - 1, [(0, 6), (0, 5), (1, 6)])
    rubble(g, cells, Mix(("nether_wart_block", 2), ("netherrack", 1)), spread=3)
    # weeping vines from the mouth's lip and the hollow's roof, clear of the frame's opening
    for x in range(mid - 5, mid + 6):
        for z in range(cz - 3, cz + 6):
            for y in range(11, 2, -1):
                s = g.get(x, y, z)
                if s is not None and s.endswith(":nether_wart_block") and g.empty(x, y - 1, z):
                    if g.rng.random() < 0.38:
                        weeping(g, x, y - 1, z, g.rng.randint(1, 3))
                    break
    # clots of wart round it
    for x, z, r in ((3, 15, 1.6), (g.W - 4, 16, 1.3), (mid + 8, 7, 1.4)):
        g.ellipsoid(x, 1, z, r, 1.1, r, "nether_wart_block", only_above=1)
    dress(g, 22)
    return [guard(mid - 4, 16), guard(mid + 4, 16)]


# The candidates (November 2026): the user picks; any not picked go to DROPPED.
LAYOUTS = [wart_garden, weeping_arch]
DROPPED = [stem_lodge, wart_heart]  # the user's picks, November 25, 2026: 1, 3
