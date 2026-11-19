"""Earth's own helpers, shared by its areas (plains, meadow, windswept hills, peaks, caverns): boulders, standing stones,
trees, flowers, amethyst and dripstone."""
import math

from ruinkit import AIR, Mix, mc

FLOWERS = ("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "allium", "lily_of_the_valley",
           "red_tulip", "orange_tulip", "white_tulip", "pink_tulip")
TALL = ("rose_bush", "lilac", "peony")
SOIL = ("grass_block", "dirt", "moss_block", "podzol", "coarse_dirt", "rooted_dirt")


def soil_under(g, x, y, z):
    s = g.get(x, y, z)
    if s is None:
        return y <= 0
    return mc.base(s) in SOIL


def flowers(g, count, kinds=FLOWERS, tall=0.0, grass=0.35, avoid=None, y_max=3, petals=0.0):
    """Flowers (and grass tufts, a few tall flowers, pink petals) on the open soil."""
    spots = [(x, z) for x in range(1, g.W - 1) for z in range(1, g.D - 1)]
    g.rng.shuffle(spots)
    n = 0
    for x, z in spots:
        if n >= count:
            break
        if avoid and avoid(x, z):
            continue
        y = g.top_y(x, z)
        if y > y_max or not soil_under(g, x, y, z) or not g.empty(x, y + 1, z) or not g.empty(x, y + 2, z):
            continue
        r = g.rng.random()
        if r < petals:
            g.set(x, y + 1, z, "pink_petals[flower_amount=%d,facing=%s]"
                  % (g.rng.randint(1, 4), g.rng.choice(("north", "south", "east", "west"))))
        elif r < petals + tall:
            t = g.rng.choice(TALL)
            g.set(x, y + 1, z, f"{t}[half=lower]")
            g.set(x, y + 2, z, f"{t}[half=upper]")
        elif r < petals + tall + grass:
            g.set(x, y + 1, z, "grass")
        else:
            g.set(x, y + 1, z, g.rng.choice(kinds))
        n += 1
    return n


def boulder(g, cx, cy, cz, r, mat, top=None, squash=0.75, sink=0.4):
    """A rough rounded boulder sitting on (and a little sunk into) the ground at cy."""
    ry = r * squash
    yc = cy + ry * (1 - sink)
    for x in range(int(cx - r - 2), int(cx + r + 3)):
        for z in range(int(cz - r - 2), int(cz + r + 3)):
            for y in range(int(cy) - 1, int(yc + ry + 2)):
                d = ((x - cx) / (r + 0.3)) ** 2 + ((y - yc) / (ry + 0.3)) ** 2 + ((z - cz) / (r + 0.3)) ** 2
                d += (g.noise3_at(2.5, x, y, z) - 0.5) * 0.55
                if d <= 1.0 and y >= cy:
                    g.set(x, y, z, mat)
    if top is not None:
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                ty = g.top_y(x, z)
                if ty >= cy and g.empty(x, ty + 1, z) and g.chance(0.6):
                    s = g.get(x, ty, z)
                    if s is not None and mc.kind(s) == "full" and mc.base(s) != "dross_portal_frame":
                        g.set(x, ty + 1, z, top)


def standing_stone(g, x, z, h, mat, w=1, d=1, y0=1, lean=None):
    """A rough standing stone w x d, h tall; its top cells knocked about. lean: (dx, dz) shift of its top half."""
    for y in range(y0, y0 + h):
        sx, sz = (0, 0)
        if lean and y >= y0 + h // 2:
            sx, sz = lean
        for i in range(w):
            for j in range(d):
                if y == y0 + h - 1 and (i + j) % 2 == 1 and g.chance(0.6):
                    continue
                g.set(x + i + sx, y, z + j + sz, mat)
                if sx or sz:
                    g.set(x + i, y, z + j, mat)


def amethyst_cluster(g, x, y, z, size=None):
    """An amethyst bud or cluster standing on the block under (x, y, z)."""
    kind = size or g.rng.choice(("amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "amethyst_cluster"))
    g.set(x, y, z, f"{kind}[facing=up]")


def crystal_spike(g, x, z, h, y0=1):
    """A spike of amethyst growing up out of the ground: amethyst blocks, budding at its foot, a cluster on top."""
    for y in range(y0, y0 + h):
        g.set(x, y, z, "budding_amethyst" if y == y0 else "amethyst_block")
    g.set(x, y0 + h, z, "amethyst_cluster[facing=up]")


def geode_buds(g, x1, y1, z1, x2, y2, z2, chance=0.35):
    """Buds and clusters on the amethyst faces in a box: on top (facing up) and on its sides."""
    dirs = (("up", 0, 1, 0), ("north", 0, 0, -1), ("south", 0, 0, 1), ("east", 1, 0, 0), ("west", -1, 0, 0))
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                s = g.get(x, y, z)
                if s is None or mc.base(s) not in ("amethyst_block", "budding_amethyst"):
                    continue
                for face, dx, dy, dz in dirs:
                    if g.empty(x + dx, y + dy, z + dz) and g.inb(x + dx, y + dy, z + dz):
                        if g.chance(chance):
                            kind = g.rng.choice(("amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud",
                                                 "small_amethyst_bud"))
                            g.set(x + dx, y + dy, z + dz, f"{kind}[facing={face}]")


def cherry_tree(g, x, z, h, y0=1, lean=(1, 0), spread=4.5, seed_off=0):
    """A cherry tree: a trunk curving one way, a broad flat crown of pink leaves, a few hanging down."""
    lx, lz = lean
    tx, tz = x + lx * 2, z + lz * 2
    g.line((x, y0, z), (x, y0 + h // 2, z), "cherry_log[axis=y]")
    g.line((x, y0 + h // 2, z), (tx, y0 + h, tz), "cherry_wood[axis=y]")
    top = y0 + h
    g.line((tx, top, tz), (tx - lx * 3 - lz, top + 1, tz - lz * 3 + lx), "cherry_log[axis=y]")
    for dx in range(-int(spread) - 1, int(spread) + 2):
        for dz in range(-int(spread) - 1, int(spread) + 2):
            for dy in range(-1, 3):
                d = math.hypot(dx, dz) / spread + abs(dy - 0.6) / 2.2
                d += (g.noise3_at(2.0, tx + dx, top + dy, tz + dz) - 0.5) * 0.5
                if d <= 1.0:
                    g.set(tx + dx, top + dy, tz + dz, "cherry_leaves[persistent=true]")
    # leaves hanging from the crown's underside
    for dx in range(-int(spread), int(spread) + 1):
        for dz in range(-int(spread), int(spread) + 1):
            if g.get(tx + dx, top - 1, tz + dz) and mc.base(g.get(tx + dx, top - 1, tz + dz)) == "cherry_leaves" \
                    and g.chance(0.18):
                for k in range(1, g.rng.randint(1, 3) + 1):
                    if g.empty(tx + dx, top - 1 - k, tz + dz) and top - 1 - k > y0 + 1:
                        g.set(tx + dx, top - 1 - k, tz + dz, "cherry_leaves[persistent=true]")


def spruce(g, x, z, h, y0=1, bent=(0, 0)):
    """A wind-bent spruce: a trunk and full tiers of needles, widest low down, the crown pushed downwind."""
    bx, bz = bent
    top = y0 + h
    for y in range(y0, top):
        sx = int(round(bx * (y - y0) / h))
        sz = int(round(bz * (y - y0) / h))
        if y > y0 and sx != int(round(bx * (y - 1 - y0) / h)) or y > y0 and sz != int(round(bz * (y - 1 - y0) / h)):
            g.set(x + sx - (1 if bx > 0 else 0), y, z + sz - (1 if bz > 0 else 0), "spruce_log[axis=y]")
        g.set(x + sx, y, z + sz, "spruce_log[axis=y]")
        k = y - y0
        if k < 2:
            continue
        r = (top - y) * 0.5 + (0.9 if (top - y) % 2 == 0 else 0.0)
        r = min(r, 3.0)
        ri = int(r) + 1
        for dx in range(-ri, ri + 1):
            for dz in range(-ri, ri + 1):
                if (dx or dz) and math.hypot(dx - bx * 0.6, dz - bz * 0.6) <= r + 0.2:
                    if g.empty(x + sx + dx, y, z + sz + dz):
                        g.set(x + sx + dx, y, z + sz + dz, "spruce_leaves[persistent=true]")
    tx, tz = x + bx, z + bz
    g.set(tx, top, tz, "spruce_leaves[persistent=true]")
    g.set(tx, top + 1, tz, "spruce_leaves[persistent=true]")


def stalagmite(g, x, z, h, y0=1, base_mat="dripstone_block", fat=1):
    """A stalagmite from the floor: a foot of dripstone blocks (fat 1: a plus shape at the foot), then pointed
    dripstone growing up to its tip."""
    solid_h = max(0, h - 3)
    for y in range(y0, y0 + solid_h):
        g.set(x, y, z, base_mat)
        if fat and y < y0 + max(1, solid_h // 2):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                g.set(x + dx, y, z + dz, base_mat)
    tip_y = y0 + solid_h
    parts = ["base", "frustum", "tip"] if h - solid_h >= 3 else (["frustum", "tip"] if h - solid_h == 2 else ["tip"])
    for k, part in enumerate(parts):
        g.set(x, tip_y + k, z, f"pointed_dripstone[thickness={part},vertical_direction=up]")


def stalactite(g, x, y_top, z, n):
    """Pointed dripstone hanging down from the block at y_top + 1."""
    parts = ["base", "frustum", "tip"][-n:] if n <= 3 else ["base"] + ["middle"] * (n - 3) + ["frustum", "tip"]
    for k, part in enumerate(parts):
        g.set(x, y_top - k, z, f"pointed_dripstone[thickness={part},vertical_direction=down]")


def lichen_on_walls(g, chance=0.15, y_min=1, y_max=30):
    """Glow lichen on the open faces of the stone and deepslate standing in the ruin."""
    dirs = (("north", 0, -1), ("south", 0, 1), ("east", 1, 0), ("west", -1, 0))
    for x in range(1, g.W - 1):
        for z in range(1, g.D - 1):
            for y in range(y_min, min(y_max, g.H - g.G - 1)):
                if not g.empty(x, y, z) or g.get(x, y, z) is None and y <= 0:
                    continue
                if g.get(x, y, z) is not None and not mc.is_air(g.get(x, y, z)):
                    continue
                for face, dx, dz in dirs:
                    n = g.get(x + dx, y, z + dz)
                    if n is not None and mc.kind(n) == "full" and mc.base(n) != "dross_portal_frame" \
                            and g.chance(chance):
                        g.set(x, y, z, f"glow_lichen[{face}=true]")
                        break


def crag(g, cx, cz, rx, rz, peak, mat, lean=(0.0, 0.0), y0=1, top="grass_block", jag=4.0, top_chance=0.38, power=0.7):
    """A jagged rock outcrop: columns up to a peaked, broken height; its top cells grassed now and then.
    Returns its height map."""
    hts = {}
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - cx) / rx, (z - cz) / rz)
            if d > 1.0:
                continue
            h = peak * (1 - d) ** power + (g.noise3_at(1.6, x, 5, z) - 0.5) * jag
            h += lean[0] * (x - cx) + lean[1] * (z - cz)
            hy = y0 + int(round(h))
            if hy < y0:
                continue
            for y in range(y0, hy + 1):
                g.set(x, y, z, mat)
            if top and g.noise3_at(2.0, x, 9, z) > 1.0 - top_chance:
                g.set(x, hy, z, top)
            hts[(x, z)] = hy
    return hts
