"""Fire's soul sand valley ruins: soul sand and soul soil, the great bone-block fossils of the valley, basalt
columns, blackstone; soul fire burning blue on the soul sand, soul lanterns and soul campfires."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, guard, layout, mound, pillar, rubble,
                     scatter_on_floor, slab, stairs)

from ruindesigns.fire.netherkit import (BASALT, SOUL_BASE, plant, soul_fire_on)

BONE = "bone_block[axis=y]"

P = dict(
    floor=Mix(("soul_soil", 4), ("soul_sand", 3), patch=0.6),
    bone=BONE,
    basalt=BASALT,
    stone=Mix(("blackstone", 3), ("polished_blackstone_bricks", 2), ("cracked_polished_blackstone_bricks", 1),
              patch=0.45),
    trim="chiseled_polished_blackstone",
    rubble=Mix(("bone_block[axis=y]", 1), ("soul_soil", 1), ("basalt[axis=y]", 1), ("blackstone", 1)),
    light="soul_fire",
)

AREA = Area(element="fire", id="soul_sand_valley", name="Soulsand Valley", biomes="soul sand valley",
            setting=SURFACE, ground="soul_soil", under="soul_sand", palette=P,
            blurb="Giant bone fossils, basalt columns and blackstone in the soul sand, soul fire burning blue.")


def dress(g, count=12):
    """Soul fire on the soul sand and soil, a bone lying in it, a basalt nub, a patch of soul sand, a skull."""
    def place(x, y, z):
        s = g.get(x, y, z)
        soulish = s is None or any(s.endswith(":" + b) for b in SOUL_BASE)
        if not soulish:
            return False
        r = g.rng.random()
        if r < 0.36:
            return soul_fire_on(g, x, y + 1, z, base="soul_soil" if s is None else s)
        if r < 0.55:
            g.set(x, y + 1, z, f"bone_block[axis={g.rng.choice(('x', 'z'))}]")
            return True
        if r < 0.68:
            g.set(x, y + 1, z, BASALT)
            return True
        if r < 0.9:
            g.set(x, y, z, "soul_sand")
            return True
        g.set(x, y + 1, z, f"skeleton_skull[rotation={g.rng.randint(0, 15)}]")
        return True
    scatter_on_floor(g, count, place)


def soul_lamp(g, x, z, y=1, h=3, base="basalt[axis=y]"):
    """A basalt post with a soul lantern on top."""
    for k in range(h):
        g.set(x, y + k, z, base)
    g.set(x, y + h, z, "soul_lantern[hanging=false]")


def curve(g, pts, mat):
    for a, b in zip(pts, pts[1:]):
        g.line(a, b, mat)


@layout("great_ribcage", "Great Ribcage", size=(25, 20, 25),
        blurb="The fossil of some huge beast half sunk in the soul sand; the frame stands in its broken ribcage.")
def great_ribcage(g, p):
    """A spine of bone blocks arching along the back of the plot from its tail to a great skull half buried on the
    right; its ribs curve down to the soul sand front and back; the two over the middle have broken off, and in the
    gap the frame stands; soul lanterns hang from the spine, soul fire burns between the ribs."""
    mid = g.W // 2
    zs = 6
    court(g, p["floor"], 10.5, cz=11, under="soul_sand")

    def spine_y(x):
        return int(round(10.2 - (x - 11) ** 2 / 17.0))

    for x in range(1, 21):
        g.set(x, spine_y(x), zs, "bone_block[axis=x]")
        if x % 2 == 0:
            g.set(x, spine_y(x) + 1, zs, BONE)
    # the ribs: quarter arches down to the ground behind and in front
    for x in (3, 6, 9, 15, 18):
        ys = spine_y(x)
        for side, reach in ((-1, 5.6), (1, 7.0)):
            pts = []
            for k in range(9):
                t = k / 8 * math.pi / 2
                pts.append((x, int(round(1 + (ys - 1) * math.cos(t) ** 0.85)), int(round(zs + side * reach * math.sin(t)))))
            if (x, side) == (3, 1):
                pts = pts[:5]
            curve(g, pts, BONE)
    for x in (11, 13):
        ys = spine_y(x)
        for side in (-1, 1):
            g.line((x, ys, zs), (x, ys - 2, zs + side * 2), BONE)
    # the snapped rib lying in front
    g.line((1, 1, 13), (5, 1, 15), "bone_block[axis=x]")
    # the skull, half sunk on the right, its eye sockets glowing with soul fire
    g.ellipsoid(22, 3, zs, 2.8, 2.6, 3.0, BONE, shell=1.0, only_above=1)
    for x in range(20, 25):
        for z in range(zs - 3, zs + 4):
            if g.get(x, 1, z) is not None and g.empty(x, 2, z):
                g.set(x, 1, z, "soul_soil")
    for z in (zs - 1, zs + 1):
        soul_fire_on(g, 22, 2, z, base="soul_soil")
    for dz in (-1, 1):
        for y in (3, 4):
            g.set(24, y, zs + dz, AIR, force=True)
    for z in range(zs - 1, zs + 2):
        g.set(24, 1, z, "bone_block[axis=z]")
        g.set(24, 2, z, AIR if z == zs else "bone_block[axis=y]")
    # the frame in the gap of the ribs
    cells = broken_frame(g, mid - 2, 1, zs, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # soul lanterns on chains from the spine, soul fire on the sand between the ribs
    for x in (6, 18):
        ys = spine_y(x)
        g.set(x, ys - 1, zs, "chain[axis=y]")
        g.set(x, ys - 2, zs, "chain[axis=y]")
        g.set(x, ys - 3, zs, "soul_lantern[hanging=true]")
    for x, z in ((4, 8), (7, 4), (16, 9), (19, 4), (8, 10)):
        soul_fire_on(g, x, 1, z, base="soul_sand")
    for x, z, h in ((1, 16, 4), (g.W - 2, 15, 6), (2, 21, 2)):
        pillar(g, x, z, h, BASALT)
    dress(g, 10)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


@layout("spine_bridge", "Spine Bridge", size=(25, 18, 25), ground=5,
        blurb="A fossil spine arched over a trench of soul fire, the only way across to the frame on the far side.")
def spine_bridge(g, p):
    """A trench cut across the plot, its floor of soul sand burning with soul fire, basalt columns rising out of it;
    a giant's spine of bone blocks arches over it from the near bank to the far one, a few ribs hanging down into
    the fire; on the far bank the frame on a blackstone pavement, soul lantern posts at the spine's ends."""
    mid = g.W // 2
    za, zb = 7, 13
    court(g, p["floor"], 10.5, cz=12, under="soul_sand")
    for x in range(g.W):
        lo = za + int(round((g.noise3_at(3.0, x, 0, 0) - 0.5) * 2))
        hi = zb + int(round((g.noise3_at(3.0, x, 0, 9) - 0.5) * 2))
        for z in range(lo, hi + 1):
            for y in range(-5, -3):
                g.set(x, y, z, "soul_sand")
            g.set(x, -3, z, Mix(("soul_sand", 3), ("soul_soil", 1)))
            for y in range(-2, 1):
                g.set(x, y, z, AIR)
            if g.rng.random() < 0.5:
                soul_fire_on(g, x, -2, z)
        for z in (lo - 1, hi + 1):
            for y in range(-3, 1):
                g.set(x, y, z, Mix(("soul_soil", 3), ("basalt[axis=y]", 1)))
    # basalt columns standing in the trench
    for x, z, h in ((3, 10, 3), (6, 8, 1), (19, 11, 4), (22, 9, 2), (16, 12, 0)):
        pillar(g, x, z, h + 3, BASALT, y0=-2)
    # the spine: an arch of vertebrae two wide, the transverse processes sticking out its sides
    z0, z1 = za - 2, zb + 2
    for z in range(z0, z1 + 1):
        y = int(round(3.2 * math.sin(math.pi * (z - z0) / (z1 - z0))))
        for x in (mid - 1, mid):
            g.set(x, y, z, "bone_block[axis=z]", force=True)
            for yy in range(min(y, 0), y):
                if z in (z0, z1):
                    g.set(x, yy, z, "bone_block[axis=z]")
        if (z - z0) % 2 == 1:
            g.set(mid - 2, y, z, "bone_block[axis=x]")
            g.set(mid + 1, y, z, "bone_block[axis=x]")
    # ribs hanging off it into the trench
    for z, side in ((za + 1, -1), (za + 3, 1), (zb - 1, -1)):
        y = int(round(3.2 * math.sin(math.pi * (z - z0) / (z1 - z0))))
        x0 = mid - 2 if side < 0 else mid + 1
        curve(g, [(x0, y, z), (x0 + side * 2, y - 1, z), (x0 + side * 3, y - 3, z), (x0 + side * 3, -2, z)], BONE)
    # the far bank: a blackstone pavement and the frame
    for x in range(mid - 4, mid + 5):
        for z in range(1, za - 1):
            if g.noise3_at(2.5, x, 0, z) > 0.2:
                g.set(x, 0, z, p["stone"])
    cells = broken_frame(g, mid - 2, 1, 2, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"], spread=2)
    for x, z in ((mid - 3, z0), (mid + 2, z0), (mid - 3, z1), (mid + 2, z1)):
        g.set(x, 0, z, "soul_soil")
        soul_lamp(g, x, z, h=2)
    for x in (mid - 5, mid + 5):
        g.set(x, 1, 3, "soul_campfire[lit=true]")
    dress(g, 10)
    return [guard(mid - 4, 19), guard(mid + 4, 19)]


@layout("wither_altar", "Wither Altar", size=(25, 20, 25),
        blurb="A broken summoning altar: a T of soul sand with wither skulls on a blackstone dais before the frame.")
def wither_altar(g, p):
    """A round dais of blackstone in two steps at the back, the frame standing on it; before the frame an altar of
    soul sand in the shape of a T, its arm broken off and one of its three wither skulls fallen (so it can never call
    the Wither as it stands); basalt columns ring it, soul campfires smoking on some; bones about the floor."""
    mid = g.W // 2
    cz = 6
    court(g, p["floor"], 10.5, cz=12, under="soul_sand")
    g.disc(mid, cz, 6.3, 1, p["stone"])
    g.disc(mid, cz, 4.6, 2, Mix(("polished_blackstone_bricks", 3), ("chiseled_polished_blackstone", 0.4),
                               ("polished_blackstone", 1)))
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, cz + 7, stairs("polished_blackstone_brick_stairs", "north"))
        g.set(x, 2, cz + 5, stairs("polished_blackstone_brick_stairs", "north"))
    cells = broken_frame(g, mid - 2, 3, cz - 3, [(4, 6), (3, 6), (4, 5), (2, 6)])
    rubble(g, cells, p["rubble"], spread=3)
    # the altar: a T of soul sand, its right arm broken off and lying, two wither skulls left on it
    az = cz + 1
    g.set(mid, 3, az, "soul_sand")
    g.set(mid, 4, az, "soul_sand")
    g.set(mid - 1, 4, az, "soul_sand")
    g.set(mid - 1, 5, az, "wither_skeleton_skull[rotation=0]")
    g.set(mid, 5, az, "wither_skeleton_skull[rotation=0]")
    g.set(mid + 2, 3, az + 2, "soul_sand")
    g.set(mid + 2, 3, az + 1, "wither_skeleton_skull[rotation=3]")
    # soul fire in two basins at the altar's foot
    for x in (mid - 3, mid + 3):
        g.set(x, 2, az + 2, "soul_soil")
        g.set(x, 3, az + 2, "soul_fire")
    # the ring of basalt columns, some with soul campfires on them, one fallen
    for k in range(8):
        a = math.pi * (0.95 + k / 7 * 1.1)
        x = int(round(mid + math.cos(a) * 9.0))
        z = int(round(cz + 2 + math.sin(a) * 6.8))
        if k == 5:
            g.line((x, 1, z), (x + 3, 1, z + 4), "basalt[axis=z]")
            continue
        h = 3 + int(g.noise3_at(2.0, x, 0, z) * 6)
        pillar(g, x, z, h, BASALT, base="polished_basalt[axis=y]")
        if k % 2 == 0:
            g.set(x, h + 1, z, "soul_campfire[lit=true]")
    for x, z, h in ((mid - 8, 15, 4), (mid + 8, 15, 5)):
        pillar(g, x, z, h, BASALT, base="polished_basalt[axis=y]")
        g.set(x, h + 1, z, "soul_lantern[hanging=false]")
    # bones on the floor
    for x, z, axis in ((4, 16, "x"), (5, 16, "x"), (19, 18, "z"), (mid + 3, 14, "x")):
        g.set(x, 1, z, f"bone_block[axis={axis}]")
    g.set(3, 1, 18, "skeleton_skull[rotation=10]")
    dress(g, 10)
    return [guard(mid - 4, 17), guard(mid + 4, 17)]


@layout("soul_graveyard", "Soul Graveyard", size=(25, 18, 25),
        blurb="A walled graveyard in the soul sand, bone headstones and soul lanterns; the frame is its tomb's door.")
def soul_graveyard(g, p):
    """A low broken wall of blackstone round rows of graves (mounds of soul soil, bone headstones, a skull here and
    there, soul fire on two of them); basalt gallows hang soul lanterns over the path; at the back a blackstone
    tomb, its roof half fallen in, the frame for its door; a gate of basalt posts at the front."""
    mid = g.W // 2
    court(g, p["floor"], 11.0, cz=12, under="soul_sand")
    x0, x1, z0, z1 = 1, g.W - 2, 5, 22
    # the tomb at the back, its front wall round the frame
    tx0, tx1, tz0, tz1 = mid - 5, mid + 5, 0, 4
    for x in range(tx0, tx1 + 1):
        for z in range(tz0, tz1 + 1):
            for y in range(1, 8):
                wall = x in (tx0, tx1) or z in (tz0, tz1)
                g.set(x, y, z, p["stone"] if wall else AIR)
            g.set(x, 0, z, "polished_blackstone")
    for x in (tx0 + 2, tx1 - 2):
        for y in range(1, 8):
            g.set(x, y, tz1, "polished_basalt[axis=y]")
    # a gable roof along it, its ends filled in
    stair = "polished_blackstone_brick_stairs"
    for x in range(tx0 - 1, tx1 + 2):
        g.set(x, 8, tz0, stairs(stair, "south"))
        g.set(x, 8, tz1, stairs(stair, "north"))
        g.set(x, 9, tz0 + 1, stairs(stair, "south"))
        g.set(x, 9, tz1 - 1, stairs(stair, "north"))
        g.set(x, 10, tz0 + 2, slab("polished_blackstone_brick_slab"))
    for x in (tx0, tx1):
        for z in range(tz0 + 1, tz1):
            g.set(x, 8, z, p["stone"])
        g.set(x, 9, tz0 + 2, p["stone"])
    g.smash(tx1 - 1, 9, 2, 2.6)
    g.settle_rubble(0.3, 1.4, p["stone"], y_max=1)
    cells = broken_frame(g, mid - 2, 1, tz1, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"], spread=2)
    g.set(mid, 1, 2, "soul_soil")
    g.set(mid, 2, 2, "soul_fire")
    # the wall round the graveyard, broken, the gate at the front
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if not (x in (x0, x1) or z == z1):
                continue
            if z == z1 and abs(x - mid) <= 2:
                continue
            if g.noise3_at(3.0, x, 0, z) < 0.22:
                continue
            g.set(x, 1, z, Mix(("blackstone", 3), ("polished_blackstone_bricks", 2), ("basalt[axis=y]", 1)))
            if g.noise3_at(2.0, x, 2, z) > 0.6:
                g.set(x, 2, z, "blackstone_wall")
    for x in (mid - 3, mid + 3):
        pillar(g, x, z1, 4, BASALT, base="polished_basalt[axis=y]")
        g.set(x, 5, z1, "soul_lantern[hanging=false]")
    # the graves: rows either side of the path, headstones at their heads (the back)
    graves = [(x, z) for x in (3, 6, 18, 21) for z in (8, 12, 16)]
    for i, (x, z) in enumerate(graves):
        if (x, z) in ((6, 16), (21, 8)):
            continue
        for dz in (1, 2):
            g.set(x, 0, z + dz, "soul_soil")
            g.set(x, 1, z + dz, slab("blackstone_slab") if i % 3 == 0 else "soul_soil")
        if i % 4 == 1:
            g.set(x, 1, z, "polished_blackstone_brick_wall")
            g.set(x, 2, z, "skeleton_skull[rotation=0]")
        else:
            g.set(x, 1, z, BONE)
            if i % 2 == 0:
                g.set(x, 2, z, BONE)
    for x, z in ((3, 13), (18, 17)):
        g.set(x, 1, z, AIR, force=True)
        g.set(x, 0, z, "soul_soil")
        g.set(x, 1, z, "soul_fire")
    # basalt gallows hanging soul lanterns over the path
    for z in (9, 15):
        for x, arm in ((mid - 4, 1), (mid + 4, -1)):
            pillar(g, x, z, 5, BASALT, base="polished_basalt[axis=y]")
            g.set(x + arm, 5, z, slab("polished_blackstone_brick_slab", "top"))
            g.set(x + arm, 4, z, "chain[axis=y]")
            g.set(x + arm, 3, z, "soul_lantern[hanging=true]")
    dress(g, 8)
    return [guard(mid - 2, 12), guard(mid + 2, 12)]


# The candidates (November 2026): the user picks; any not picked go to DROPPED.
LAYOUTS = [great_ribcage, spine_bridge, wither_altar, soul_graveyard]
DROPPED = []
