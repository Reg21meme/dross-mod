"""Earth's peaks ruins (grove, snowy slopes, frozen peaks, jagged peaks, stony peaks): high on the mountain, each ruin
builds its own crags and summit; bare stone banded with calcite, snow on every ledge, veins of amethyst in the rock,
a little powder snow in the hollows, snowy spruces of the groves."""
import math

from ruinkit import AIR, FRAME, MOUNTAIN, Area, Mix, broken_frame, flat_frame, guard, layout, rubble, slab, stairs

from .earthkit import boulder, crag, spruce

P = dict(
    floor=Mix(("snow_block", 4), ("stone", 2), ("packed_ice", 0.5), ("gravel", 0.5), patch=0.6),
    wall=Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2), ("mossy_stone_bricks", 0.5), ("cobblestone", 1), patch=0.45),
    rock=Mix(("stone", 6), ("andesite", 2), ("calcite", 1), ("tuff", 1), patch=0.7, scale=3.0),
    rubble=Mix(("cobblestone", 2), ("stone", 2), ("snow_block", 1)),
)

AREA = Area(element="earth", id="peaks", name="Peaks", biomes="grove, snowy slopes, frozen peaks, jagged peaks, "
            "stony peaks", setting=MOUNTAIN, ground="stone", under="stone", palette=P,
            blurb="Summit ledges, passes and jagged spires high on the snowy peaks.")


def snow_over(g, chance=0.7, y_min=1):
    """Thin snow on the open tops of full blocks (not on the frame), now and then a drift two layers deep."""
    for x in range(g.W):
        for z in range(g.D):
            y = g.top_y(x, z)
            if y < y_min - 1 or not g.empty(x, y + 1, z) or not g.inb(x, y + 1, z):
                continue
            s = g.get(x, y, z)
            if s is None:
                if y == 0 and g.chance(chance):
                    g.set(x, 1, z, "snow[layers=1]")
                continue
            if "portal_frame" in s or "lantern" in s or "snow" == s.split(":")[-1].split("[")[0]:
                continue
            from ruinkit import mc
            if mc.kind(s) == "full" and g.chance(chance):
                g.set(x, y + 1, z, "snow[layers=%d]" % g.rng.choice((1, 1, 2, 3)))


def strata(g, x1, y1, z1, x2, y2, z2):
    """Bands of calcite and tuff running level through the stone (the peaks' layered rock)."""
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                s = g.get(x, y, z)
                if s and s.split(":")[-1] in ("stone", "andesite"):
                    band = (y + int(g.noise3_at(4.0, x, 0, z) * 2)) % 6
                    if band == 0:
                        g.set(x, y, z, "calcite")
                    elif band == 3 and g.chance(0.5):
                        g.set(x, y, z, "tuff")


def amethyst_vein(g, p1, p2):
    """A vein of amethyst through the rock along a line; buds on its open faces."""
    pts = []
    x1, y1, z1 = p1
    x2, y2, z2 = p2
    n = int(max(abs(x2 - x1), abs(y2 - y1), abs(z2 - z1))) + 1
    for i in range(n + 1):
        t = i / n
        pts.append((round(x1 + (x2 - x1) * t), round(y1 + (y2 - y1) * t), round(z1 + (z2 - z1) * t)))
    for x, y, z in pts:
        for dx, dy, dz in ((0, 0, 0), (1, 0, 0), (0, 1, 0), (0, 0, 1)):
            s = g.get(x + dx, y + dy, z + dz)
            if s and "frame" not in s and "lantern" not in s and s.split(":")[-1] not in ("air",):
                g.set(x + dx, y + dy, z + dz, "budding_amethyst" if g.chance(0.2) else "amethyst_block")
    faces = (("up", 0, 1, 0), ("south", 0, 0, 1), ("east", 1, 0, 0), ("west", -1, 0, 0), ("north", 0, 0, -1))
    for x, y, z in pts:
        for dx0, dy0, dz0 in ((0, 0, 0), (1, 0, 0), (0, 1, 0), (0, 0, 1)):
            bx, by, bz = x + dx0, y + dy0, z + dz0
            s = g.get(bx, by, bz)
            if not s or "amethyst" not in s:
                continue
            for face, dx, dy, dz in faces:
                if g.empty(bx + dx, by + dy, bz + dz) and g.inb(bx + dx, by + dy, bz + dz) and g.chance(0.45):
                    kind = g.rng.choice(("amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud"))
                    g.set(bx + dx, by + dy, bz + dz, f"{kind}[facing={face}]")
                    break


def needle(g, x, z, h, w, mat, lean=(0, 0), cap="snow_block"):
    """A jagged needle of rock: w x w at its foot, narrowing in steps to a single block at its tip, leaning a little."""
    lx, lz = lean
    for y in range(1, h + 1):
        t = (y - 1) / max(h - 1, 1)
        r = (w / 2.0) * (1 - t) ** 0.5 + 0.4
        ox = lx * t * 2.0
        oz = lz * t * 2.0
        for dx in range(-w, w + 1):
            for dz in range(-w, w + 1):
                if math.hypot(dx - ox, dz - oz) <= r + (g.noise3_at(1.5, x + dx, y, z + dz) - 0.5) * 0.9:
                    g.set(x + dx, y, z + dz, mat)
    # snow caps on its ledges and tip
    for dx in range(-w, w + 1):
        for dz in range(-w, w + 1):
            ty = g.top_y(x + dx, z + dz)
            s = g.get(x + dx, ty, z + dz)
            if ty >= 3 and s and "stone" in s and g.chance(0.55):
                g.set(x + dx, ty, z + dz, cap)


def snowy_spruce(g, x, z, h, y0=1):
    spruce(g, x, z, h, y0=y0)
    for dx in range(-3, 4):
        for dz in range(-3, 4):
            ty = g.top_y(x + dx, z + dz)
            s = g.get(x + dx, ty, z + dz)
            if s and "spruce_leaves" in s and g.empty(x + dx, ty + 1, z + dz) and g.chance(0.6):
                g.set(x + dx, ty + 1, z + dz, "snow[layers=1]")


@layout("summit_ledge", "Summit Ledge", size=(25, 24, 25),
        blurb="A broad ledge below the summit, cut steps up to it, the frame against the peak and a cairn on top.")
def summit_ledge(g, p):
    """The mountain's last ledge, a broad shelf six blocks up with steps cut up its front; behind it the summit crag
    rises banded with calcite and veined with amethyst, a cairn on its top; the frame stands against its foot."""
    mid = g.W // 2
    # the summit crag at the back
    hts = crag(g, mid + 1, 2.5, 12.0, 6.5, 18.0, p["rock"], top="snow_block", jag=5.0, top_chance=0.45)
    # the ledge: six up, its front edge ragged
    for x in range(g.W):
        zf = 18 + int(round((g.noise3_at(3.0, x, 0, 0) - 0.5) * 3.0))
        xs = 1 + int(round(g.noise3_at(3.0, 0, 0, x) * 2))
        if x < xs - 1 or x > g.W - xs:
            continue
        for z in range(2, zf + 1):
            for y in range(1, 7):
                g.set(x, y, z, p["rock"])
            g.set(x, 6, z, p["floor"])
    # steps cut up its front
    for k in range(6):
        z = 24 - k
        for x in range(mid - 1, mid + 2):
            for y in range(1, k + 1):
                g.set(x, y, z, p["rock"])
            g.set(x, k + 1, z, stairs("stone_brick_stairs", "north"))
        for x in (mid - 2, mid + 2):
            for y in range(1, k + 2):
                g.set(x, y, z, p["rock"])
    strata(g, 0, 1, 0, g.W - 1, 20, g.D - 1)
    # the frame against the crag's foot, on the ledge
    for x in range(mid - 3, mid + 4):
        for z in range(6, 9):
            for y in range(7, 15):
                g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 2, 7, 7, [(4, 6), (3, 6), (4, 5), (0, 6)])
    for x in range(mid - 2, mid + 3):
        g.set(x, 6, 7, "stone_bricks")
    rubble(g, cells, p["rubble"])
    for x in range(mid - 3, mid + 4):
        if g.get(x, 6, 6) is not None:
            g.set(x, 6, 6, "stone_bricks")
    # the amethyst vein slanting up the crag's face
    amethyst_vein(g, (mid - 7, 7, 6), (mid - 3, 15, 3))
    # the cairn on the summit
    (sx, sz), sy = max(hts.items(), key=lambda kv: kv[1])
    for y, w in ((1, 1), (2, 0), (3, 0)):
        for dx in range(-w, w + 1):
            for dz in range(-w, w + 1):
                if abs(dx) + abs(dz) <= w:
                    g.set(sx + dx, sy + y, sz + dz, "cobblestone")
    g.set(sx, sy + 4, sz, "lantern")
    # broken posts of an old rail along the ledge's edge
    for x in range(2, g.W - 2, 3):
        z = 18 + int(round((g.noise3_at(3.0, x, 0, 0) - 0.5) * 3.0))
        if abs(x - mid) > 2 and g.chance(0.6):
            g.set(x, 7, z, "cobblestone_wall")
    for x in (mid - 5, mid + 5):
        g.set(x, 7, 8, "stone_bricks")
        g.set(x, 8, 8, "lantern")
    boulder(g, 3, 7, 13, 1.4, p["rock"])
    snowy_spruce(g, g.W - 3, 14, 6, y0=7)
    snow_over(g, 0.4)
    return [guard(mid - 3, 12, y=7), guard(mid + 3, 12, y=7)]


@layout("saddle_pass", "Saddle Pass", size=(25, 22, 25),
        blurb="A snowy pass between two peaks, an old border wall across it; the frame stands in its gate.")
def saddle_pass(g, p):
    """Two peaks either side, a wide saddle of snow between them; across it a broken border wall climbs both slopes,
    the frame standing in its gateway; packed ice in the ruts, powder snow drifted in a hollow."""
    mid = g.W // 2
    crag(g, 0.5, 7.0, 6.5, 10.0, 16.0, p["rock"], top="snow_block", top_chance=0.65, lean=(-0.25, 0.1))
    crag(g, g.W - 1.5, 9.0, 6.0, 9.0, 12.0, p["rock"], top="snow_block", top_chance=0.65, lean=(0.25, 0.1))
    strata(g, 0, 1, 0, g.W - 1, 18, g.D - 1)
    # the pass's floor
    for x in range(g.W):
        for z in range(g.D):
            if g.get(x, 1, z) is None:
                g.set(x, 0, z, p["floor"])
    # the border wall across the pass and up the slopes, 4 high, broken, its gate in the middle
    for x in range(g.W):
        base = g.top_y(x, 6)
        base = 0 if g.get(x, 1, 6) is None else base
        for z in (5, 6):
            for y in range(1, 5):
                g.set(x, base + y, z, p["wall"])
            if g.get(x, base + 4, z):
                g.set(x, base + 5, z, slab("stone_brick_slab") if (x + z) % 2 else None)
    g.ruin_tops(0, 5, g.W - 1, 6, 1, 20, 1.5, 16.0, scale=4.0, salt=11, region=lambda x, z: abs(x - mid) > 3)
    for x in range(mid - 3, mid + 4):
        for z in (5, 6):
            for y in range(1, 9):
                g.set(x, y, z, AIR, force=True)
    # the gate's two towers' stumps
    for x0 in (mid - 5, mid + 4):
        for x in (x0, x0 + 1):
            for z in (4, 5, 6, 7):
                for y in range(1, 7 if x0 < mid else 4):
                    g.set(x, y, z, p["wall"])
    cells = broken_frame(g, mid - 2, 1, 6, [(4, 6), (3, 6), (2, 6), (4, 5)])
    rubble(g, cells, p["rubble"])
    # ruts of packed ice down the pass, a hollow of powder snow by the west slope
    for z in range(8, g.D - 1):
        for x in (mid - 2, mid + 2):
            if g.noise3_at(3.0, x, 0, z) > 0.4:
                g.set(x, 0, z, "packed_ice")
    for x, z in ((6, 17), (7, 17), (6, 18)):
        if g.get(x, 1, z) is None:
            g.set(x, 0, z, "powder_snow")
            g.set(x, -1, z, "stone")
    amethyst_vein(g, (g.W - 3, 2, 8), (g.W - 5, 8, 12))
    for x in (mid - 4, mid + 4):
        g.set(x, 1, 8, "cobblestone")
        g.set(x, 2, 8, "lantern")
    snowy_spruce(g, 6, 1, 6, y0=g.top_y(6, 1) + 1)
    boulder(g, mid + 6, 1, 19, 1.5, p["rock"], top="snow[layers=2]")
    snow_over(g, 0.75)
    return [guard(mid - 3, 13), guard(mid + 3, 13)]


@layout("jagged_spires", "Jagged Spires", size=(25, 24, 25),
        blurb="A snowfield ringed by jagged needles of banded rock, one split by a vein of amethyst; the frame between.")
def jagged_spires(g, p):
    """Needles of rock round the back and sides of a snowfield, leaning, banded with calcite and capped with snow; the
    tallest split by a vein of amethyst; the frame stands between the two at the back, a fallen needle across the snow."""
    mid = g.W // 2
    for x in range(g.W):
        for z in range(g.D):
            g.set(x, 0, z, Mix(("snow_block", 5), ("stone", 1), ("packed_ice", 1), patch=0.6))
    spires = ((mid - 6, 3, 20, 4, (0, -1)), (mid + 6, 3, 16, 3, (1, 0)), (3, 10, 12, 3, (-1, 0)),
              (g.W - 3, 9, 18, 3, (1, -1)), (2, 18, 8, 2, (-1, 1)), (g.W - 3, 19, 10, 2, (1, 1)),
              (mid, 1, 10, 2, (0, -1)), (7, 2, 8, 2, (0, 0)))
    for x, z, h, w, lean in spires:
        needle(g, x, z, h, w, p["rock"], lean=lean)
    strata(g, 0, 1, 0, g.W - 1, 21, g.D - 1)
    amethyst_vein(g, (mid - 4, 2, 5), (mid - 6, 17, 3))
    amethyst_vein(g, (g.W - 3, 3, 9), (g.W - 2, 12, 7))
    # the frame between the two great needles
    for x in range(mid - 3, mid + 4):
        for z in range(5, 8):
            for y in range(1, 9):
                if g.get(x, y, z) and "amethyst" not in (g.get(x, y, z) or ""):
                    g.set(x, y, z, AIR, force=True)
    cells = broken_frame(g, mid - 2, 1, 6, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p["rubble"])
    # a needle fallen across the snow, broken in two
    g.line((5, 1, 14), (9, 1, 16), p["rock"], r=0.8)
    g.line((11, 1, 17), (13, 1, 18), p["rock"], r=0.6)
    for x, z in ((mid - 4, 9), (mid + 4, 9)):
        g.set(x, 1, z, "stone_bricks")
        g.set(x, 2, z, "lantern")
    for x, z in ((6, 19), (7, 20), (6, 20)):
        g.set(x, 0, z, "powder_snow")
        g.set(x, -1, z, "stone")
    snow_over(g, 0.5)
    return [guard(mid - 3, 12), guard(mid + 3, 12)]


@layout("avalanche", "Avalanche Field", size=(25, 22, 25),
        blurb="Where a rockslide came down the slope: tumbled boulders and snow, the frame thrown down among them.")
def avalanche(g, p):
    """The mountainside rising at the back, scoured by a slide: boulders of stone and packed snow tumbled down to the
    front, the frame thrown flat and half buried, a mountain hut's roof sticking out of the snow, snapped spruces; a
    clear bowl in front where the knights wait."""
    mid = g.W // 2
    # the slope at the back
    for x in range(g.W):
        for z in range(g.D):
            h = 11.0 - z * 1.0 + (g.noise3_at(3.0, x, 0, z) - 0.5) * 3.0 + abs(x - mid) * 0.1
            hy = int(round(h))
            if hy < 1:
                continue
            for y in range(1, hy + 1):
                g.set(x, y, z, p["rock"])
            g.set(x, hy, z, "snow_block" if g.noise3_at(2.0, x, 4, z) > 0.35 else "stone")
    # the slide's track: a scoured gully down the middle-west
    for x in range(mid - 7, mid):
        for z in range(0, 10):
            cut = 3 + int(abs(x - (mid - 4)) * 0.8)
            for y in range(cut, 14):
                g.set(x, y, z, AIR, force=True)
    strata(g, 0, 1, 0, g.W - 1, 14, g.D - 1)
    # the tumbled boulders: stone and packed snow, big at the foot of the slope, smaller further out
    for bx, bz, r, mat in ((2, 7, 2.2, p["rock"]), (mid - 1, 12, 1.6, "snow_block"), (2, 19, 1.6, p["rock"]),
                           (g.W - 3, 22, 1.6, Mix(("stone", 2), ("snow_block", 1))), (g.W - 3, 17, 1.3, p["rock"]),
                           (mid - 9, 20, 1.2, "snow_block"), (mid + 3, 9, 1.4, p["rock"])):
        boulder(g, bx, 1, bz, r, mat, top="snow[layers=2]")
    # the frame thrown flat out of the gully onto the snow, its far end still under the slide
    for x in range(mid - 9, mid - 1):
        for z in range(8, 15):
            for y in range(1, 6):
                if not (x < mid - 7 and z < 10):
                    g.set(x, y, z, AIR, force=True)
            g.set(x, 0, z, Mix(("snow_block", 2), ("stone", 1)))
    flat_frame(g, mid - 9, 1, 9, keep=lambda i, j: not (j == 0 and i < 3) and not (i == 4 and j == 3), along="x")
    g.set(mid - 6, 2, 9, FRAME)
    g.set(mid - 6, 2, 10, "snow_block")
    # the hut's roof sticking out of the snow on the east, its chimney
    for k in range(5):
        for z in range(11, 16):
            x = mid + 5 + k
            y = 1 + min(k, 4 - k)
            g.set(x, y, z, stairs("spruce_stairs", "west" if k < 2 else ("east" if k > 2 else "south")) if k != 2
                  else "spruce_planks")
            for yy in range(1, y):
                g.set(x, yy, z, "snow_block")
    g.ruin_tops(mid + 5, 11, mid + 9, 15, 1, 4, 0.0, 3.5, scale=2.5, salt=4)
    for y in range(1, 5):
        g.set(mid + 8, y, 11, "cobblestone")
    # snapped spruces: a stump and a trunk lying in the snow
    for y in (1, 2):
        g.set(2, y, 5, "spruce_log[axis=y]")
    g.line((3, 1, 7), (8, 1, 9), "spruce_log[axis=x]")
    snowy_spruce(g, g.W - 3, 3, 7, y0=g.top_y(g.W - 3, 3) + 1)
    for x in (mid - 5, mid + 5):
        g.set(x, 1, 15, "cobblestone")
        g.set(x, 2, 15, "lantern")
    snow_over(g, 0.75)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [summit_ledge, saddle_pass, jagged_spires, avalanche]
DROPPED = []
