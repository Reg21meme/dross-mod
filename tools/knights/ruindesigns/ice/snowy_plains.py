"""Ice's snowy plains ruins: snow, packed and blue ice, ice spike spires, frost-cracked stone bricks and soul
lanterns; where the Thawed Soldiers stood frozen for an age before the Dross woke them."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, scatter_on_floor, slab, stairs)

ICE = Mix(("packed_ice", 5), ("blue_ice", 1), patch=0.5)
SHARD = Mix(("packed_ice", 4), ("blue_ice", 2), ("ice", 1), patch=0.55)

P = dict(
    floor=Mix(("snow_block", 4), ("stone_bricks", 2), ("cracked_stone_bricks", 1), ("packed_ice", 1), patch=0.55),
    wall=Mix(("stone_bricks", 4), ("cracked_stone_bricks", 3), ("cobblestone", 1), ("packed_ice", 0.5), patch=0.45),
    stone=Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2), patch=0.4),
    trim="chiseled_stone_bricks",
    rubble=Mix(("stone_bricks", 2), ("cobblestone", 1), ("packed_ice", 1), ("snow_block", 1)),
    light="soul_lantern",
)

AREA = Area(element="ice", id="snowy_plains", name="Snowy Plains", biomes="snowy plains, ice spikes",
            setting=SURFACE, ground="snow_block", under="dirt", palette=P,
            blurb="Frost-split stone and ice spikes on the open snow, lit by soul lanterns.")


# ---------------------------------------------------------------- the area's own pieces

def spike(g, cx, cz, h, r0, lean=(0.0, 0.0), y0=1, mat=ICE, tip="blue_ice"):
    """An ice spike: a column tapering from r0 to a point, leaning by `lean` blocks per layer; each layer keeps the
    one below's middle so it always stands face to face."""
    prev = None
    for k in range(h):
        y = y0 + k
        t = k / max(h - 1, 1)
        r = r0 * (1 - t) ** 0.9
        x = int(round(cx + lean[0] * k))
        z = int(round(cz + lean[1] * k))
        if r >= 0.6:
            g.disc(x, z, r, y, mat)
        g.set(x, y, z, mat if k < h - 1 else tip)
        if prev is not None and prev != (x, z):
            g.set(prev[0], y, prev[1], mat)
        prev = (x, z)


def lantern_post(g, x, z, h=2, y0=1):
    g.set(x, y0, z, "chiseled_stone_bricks")
    for y in range(y0 + 1, y0 + h):
        g.set(x, y, z, "stone_brick_wall")
    g.set(x, y0 + h, z, "soul_lantern")


def drift(g, cx, cz, r, peak, rz=None):
    """A snowdrift banked up on the ground (solid snow, its top a soft layer)."""
    hs = mound(g, cx, cz, r, peak, "snow_block", y0=0, rz=rz)
    for (x, z), h in hs.items():
        if g.empty(x, h + 1, z) and g.rng.random() < 0.5:
            g.set(x, h + 1, z, "snow[layers=%d]" % g.rng.randint(1, 4))


def dress(g, count=18):
    """Thin snow over the stone, a few broken bricks and ice chunks lying about."""
    def place(x, y, z):
        r = g.rng.random()
        if r < 0.7:
            g.set(x, y + 1, z, "snow[layers=%d]" % g.rng.choice((1, 1, 2, 3)))
        elif r < 0.85:
            g.set(x, y + 1, z, P["rubble"])
        else:
            g.set(x, y + 1, z, slab("stone_brick_slab"))
        return True
    scatter_on_floor(g, count, place)


def snow_tops(g):
    """A layer of snow on everything that stands up (wall tops, pillars), where the sky can reach it."""
    for x in range(g.W):
        for z in range(g.D):
            y = g.top_y(x, z)
            s = g.get(x, y, z)
            if y >= 2 and s is not None and s.endswith(("stone_bricks", "cobblestone", "snow_block")) \
                    and g.empty(x, y + 1, z) and g.rng.random() < 0.6:
                g.set(x, y + 1, z, "snow[layers=%d]" % g.rng.randint(1, 3))


# ---------------------------------------------------------------- the layouts

@layout("spike_grove", "Spike Grove", size=(25, 22, 25),
        blurb="Great ice spikes round the frame, the biggest grown up over it and half swallowing it.")
def spike_grove(g, p):
    """A grove of ice spikes, leaning every way; the tallest has grown over the frame's left side, encasing it in
    blue ice; old stone flags in the snow before it, soul lanterns on broken posts."""
    mid = g.W // 2
    court(g, p["floor"], 9.0, cz=15, under="dirt")
    cells = broken_frame(g, mid - 1, 1, 6, [(4, 6), (4, 5), (3, 6)])
    rubble(g, cells, p["rubble"])
    # the great spike behind and over the frame's left post
    spike(g, mid - 3, 5, 19, 3.2, lean=(0.08, 0.04))
    for y in range(1, 7):
        for x in range(mid - 3, mid - 1):
            if g.get(x, y, 6) is None:
                g.set(x, y, 6, "blue_ice")
    g.ellipsoid(mid - 3, 2, 7, 1.6, 2.0, 1.0, "packed_ice", only_above=1)
    # the others round the back and sides
    for x, z, h, r, lean in ((3, 3, 13, 2.2, (0.15, 0.0)), (g.W - 4, 4, 15, 2.4, (-0.12, 0.05)),
                             (mid + 6, 1, 9, 1.6, (0.0, 0.0)), (2, 12, 8, 1.6, (0.12, 0.1)),
                             (g.W - 3, 17, 4, 1.1, (-0.2, 0.0)), (mid - 7, 6, 5, 1.0, (0.0, 0.0))):
        spike(g, x, z, h, r, lean=lean)
    # a snapped spike lying in the snow, its stump beside it
    spike(g, mid - 7, 11, 3, 1.4)
    g.line((mid - 8, 1, 12), (mid - 11, 1, 17), "packed_ice", r=0.7)
    for x, z in ((mid - 6, 13), (mid + 5, 13)):
        lantern_post(g, x, z, h=2)
    drift(g, 4, 20, 3.5, 2.0)
    drift(g, g.W - 3, 21, 3.0, 1.5)
    dress(g, 16)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


@layout("buried_tower", "Buried Tower", size=(25, 20, 25),
        blurb="A round watchtower's stump drowned in snow; its front has burst open on the frame inside.")
def buried_tower(g, p):
    """A round stone tower, broken off at every height, a great drift banked up its back; its front wall has fallen
    outwards, opening the floor where the frame stands between soul lanterns."""
    mid = g.W // 2
    cz = 7
    court(g, p["floor"], 9.5, cz=15, under="dirt")
    g.disc(mid, cz, 5.5, 0, p["floor"])
    g.cylinder(mid, cz, 6.0, 1, 13, p["wall"], r_in=4.8)
    # arrow slits and a ring of corbels near the top
    for y in (5, 9):
        for a in range(0, 360, 60):
            x = int(round(mid + math.cos(math.radians(a)) * 5.6))
            z = int(round(cz + math.sin(math.radians(a)) * 5.6))
            g.set(x, y, z, AIR, force=True)
    g.ruin_tops(mid - 7, cz - 7, mid + 7, cz + 7, 1, 13, 4.0, 13.0, scale=4.0, salt=2)
    # the front fallen out: a wide breach, its stones lying in a fan
    for x in range(mid - 4, mid + 5):
        for z in range(cz + 2, cz + 8):
            for y in range(1, 14):
                if math.hypot((x - mid) / 4.4, (y - 1) / 9.0) < 1.0:
                    g.set(x, y, z, AIR, force=True)
    g.settle_rubble(0.35, 2.0, p["rubble"], protect=lambda x, z: abs(x - mid) <= 3 and z > cz + 6)
    cells = broken_frame(g, mid - 2, 1, cz, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    for x in (mid - 3, mid + 3):
        g.set(x, 1, cz - 1, p["trim"])
        g.set(x, 2, cz - 1, "soul_lantern")
    # the drift up its back and left side
    drift(g, mid - 2, 1, 9.0, 7.0, rz=3.5)
    drift(g, 2, cz + 2, 3.5, 4.0)
    for x, z in ((mid - 7, 16), (mid + 7, 16)):
        lantern_post(g, x, z, h=3)
    snow_tops(g)
    dress(g, 14)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


@layout("ice_barrow", "Ice Barrow", size=(25, 18, 25),
        blurb="A long snow barrow; the frame is its doorway, an avenue of frost-split stones leading up to it.")
def ice_barrow(g, p):
    """A burial mound of snow over stone, long across the back; a stone-lined passage cut into its front with the
    broken frame standing in its mouth (the tomb the Thawed Soldiers came out of); short standing stones in pairs
    down the way in."""
    mid = g.W // 2
    court(g, Mix(("snow_block", 3), ("stone_bricks", 1), ("gravel", 1), patch=0.6), 8.0, cz=16, rx=6.0, under="dirt")
    # the barrow: stone inside, snow over it
    hs = mound(g, mid, 3, 12.0, 11.0, "snow_block", y0=0, rz=7.5)
    mound(g, mid, 3, 10.0, 8.0, Mix(("stone", 3), ("cobblestone", 1)), y0=0, rz=6.0)
    for (x, z), h in hs.items():
        if g.rng.random() < 0.3 and g.empty(x, h + 1, z):
            g.set(x, h + 1, z, "snow[layers=2]")
    # the passage into it: stone-brick walls, a roof of great lintel stones, soul lanterns down it
    for z in range(1, 11):
        for x in range(mid - 3, mid + 4):
            for y in range(1, 9):
                g.set(x, y, z, AIR, force=True)
            g.set(x, 0, z, p["stone"])
        for x in (mid - 4, mid + 4):
            for y in range(1, 9):
                g.set(x, y, z, p["wall"])
        for x in range(mid - 4, mid + 5):
            g.set(x, 9, z, p["wall"] if z % 3 else "chiseled_stone_bricks")
    for z in (2, 5):
        g.set(mid, 8, z, "chain[axis=y]")
        g.set(mid, 7, z, "soul_lantern[hanging=true]")
    # the forecourt: a crescent of great kerbstones either side of the mouth, tallest beside it
    for x in range(mid - 10, mid + 11):
        d = abs(x - mid)
        if d <= 3:
            continue
        z = 11 + int((d - 4) * 0.4)
        h = max(2, 10 - (d - 4) * 1.2 - (1 if x % 3 == 0 else 0))
        for y in range(0, int(h) + 1):
            g.set(x, y, z, p["stone"] if y else "stone")
        g.set(x, int(h) + 1, z, "snow[layers=3]")
    for x in range(mid - 4, mid + 5):
        g.set(x, 9, 11, "chiseled_stone_bricks" if x in (mid - 4, mid + 4) else "stone_bricks")
        g.set(x, 10, 11, "stone_bricks")
    g.smash(mid + 3, 10, 11, 1.5)
    cells = broken_frame(g, mid - 2, 1, 9, [(4, 6), (3, 6), (4, 5), (4, 4)])
    rubble(g, cells, p["rubble"])
    # the avenue: standing stones in pairs, shorter towards the front
    for z, h in ((13, 4), (17, 3), (21, 2)):
        for x in (mid - 7, mid + 7):
            hh = h - (1 if (x + z) % 3 == 0 else 0)
            g.set(x, 1, z, p["trim"])
            for y in range(2, hh + 1):
                g.set(x, y, z, p["stone"])
            g.set(x, hh + 1, z, "soul_lantern" if z == 13 else "snow[layers=2]")
    snow_tops(g)
    dress(g, 12)
    return [guard(mid - 3, 15), guard(mid + 3, 15)]


@layout("colonnade", "Colonnade", size=(23, 20, 25),
        blurb="Two rows of frost-split columns lead to the frame on its dais; half of them have fallen.")
def colonnade(g, p):
    """A processional way: two rows of stone columns with cores of packed ice, a lintel across the first pair, the
    rest broken or toppled into the snow; at its head the frame on a three-step dais under ice spikes."""
    mid = g.W // 2
    for x in range(mid - 5, mid + 6):
        for z in range(1, g.D - 1):
            g.set(x, 0, z, p["floor"] if abs(x - mid) < 5 else p["trim"])
            g.set(x, -1, z, "stone")
    # the dais
    for step, (z0, z1) in enumerate(((1, 7), (1, 6), (1, 5))):
        for x in range(mid - 5 + step, mid + 6 - step):
            for z in range(z0, z1 + 1):
                g.set(x, 1 + step, z, p["stone"])
    cells = broken_frame(g, mid - 2, 4, 3, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    # the columns: base, shaft (an ice core in its middle third), capital
    heights = {(-6, 9): 9, (6, 9): 9, (-6, 13): 6, (6, 13): 3, (-6, 17): 2, (6, 17): 7, (-6, 21): 4, (6, 21): 1}
    for (dx, z), h in heights.items():
        x = mid + dx
        g.set(x, 1, z, "chiseled_stone_bricks")
        for y in range(2, h + 1):
            g.set(x, y, z, "packed_ice" if h * 0.35 < y < h * 0.7 else p["stone"])
        if h >= 6:
            g.set(x, h + 1, z, "chiseled_stone_bricks")
    for x in range(mid - 7, mid + 8):
        g.set(x, 11, 9, "stone_bricks" if x not in (mid - 1, mid) else "cracked_stone_bricks")
    g.smash(mid + 2, 11, 9, 1.2)
    # the fallen ones lie outwards across the snow
    g.line((mid + 7, 1, 13), (g.W - 1, 1, 15), "stone_bricks", r=0.6)
    g.line((mid - 7, 1, 17), (0, 1, 19), "stone_bricks", r=0.6)
    g.line((mid + 7, 1, 21), (g.W - 1, 1, 23), "stone_bricks", r=0.6)
    for x, z, h in ((2, 2, 11), (g.W - 3, 3, 13), (1, 8, 6)):
        spike(g, x, z, h, 1.6)
    for x in (mid - 4, mid + 4):
        g.set(x, 3, 6, "soul_lantern")
    drift(g, 2, 13, 3.0, 2.5)
    drift(g, g.W - 2, 18, 2.6, 2.0)
    snow_tops(g)
    dress(g, 16)
    return [guard(mid - 3, 13), guard(mid + 3, 13)]


@layout("glacier_shard", "Glacier Shard", size=(25, 22, 25),
        blurb="A tilted slab of glacier ice crashed down beside the frame, overhanging it; the ground heaved up.")
def glacier_shard(g, p):
    """A slab of blue and packed ice fifteen blocks long, fallen from somewhere, planted in the ground at the left
    and leaning out over the frame; the snow and old flagstones round its foot heaved up in broken plates."""
    mid = g.W // 2
    court(g, p["floor"], 9.5, cz=15, under="dirt")
    cells = broken_frame(g, mid - 1, 1, 6, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # the slab: each layer shifted right, 3 thick, its ends ragged, its top a jagged edge
    for y in range(-1, 16):
        shift = max(y, 0) * 0.62
        x0 = 2 + shift
        zlo = 1 + int(round(g.noise3_at(3.0, 0, y + 3, 0) * 1.5)) + max(0, y - 11)
        zhi = 11 - int(round(g.noise3_at(3.0, 5, y + 3, 0) * 1.5)) - max(0, y - 11)
        for x in range(int(x0), int(x0) + 3):
            for z in range(zlo, zhi + 1):
                if (x, y, z) in g.frame_cells():
                    continue
                g.set(x, y, z, SHARD)
    # a smaller shard planted on the right, and the ground heaved up round the big one's foot
    spike(g, g.W - 4, 5, 9, 1.8, lean=(-0.15, 0.1), mat=SHARD, tip="ice")
    for x, z, n in ((1, 12, 3), (5, 13, 2), (6, 2, 2), (2, 0, 2)):
        for k in range(n):
            g.set(x + k, 1, z, "packed_ice" if k % 2 else "stone_bricks")
            if k == 0:
                g.set(x, 2, z, slab("stone_brick_slab"))
    g.rubble_pile(4, 15, 2.2, 1.5, Mix(("packed_ice", 2), ("snow_block", 2), ("stone_bricks", 1)))
    for x, z in ((mid + 4, 11), (mid - 2, 12)):
        lantern_post(g, x, z, h=2)
    drift(g, g.W - 3, 18, 3.5, 2.0)
    snow_tops(g)
    dress(g, 14)
    return [guard(mid - 2, 16), guard(mid + 4, 16)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [spike_grove, buried_tower, colonnade, glacier_shard]
DROPPED = [ice_barrow]
