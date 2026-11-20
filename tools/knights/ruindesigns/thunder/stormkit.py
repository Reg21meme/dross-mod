"""Thunder's storm touches, shared by its areas (not an area itself: ruindesigns/__init__.py doesn't list it).

The thunder knights wear the Dross's electric blue, so their ruins mix each shore's own blocks with a storm's marks:
lightning rods on weathered copper, deepslate and dark prismarine, lapis, sea lanterns, blue glass, and the scorch of
a lightning strike (charred blocks round it, and on sand a vein of fulgurite: sand fused to glass).
"""
import math

from ruinkit import AIR, Mix

ROD = "lightning_rod[facing=up]"
COPPER = Mix(("waxed_oxidized_copper", 3), ("waxed_weathered_copper", 2), ("waxed_oxidized_cut_copper", 2), patch=0.4)
CUT_COPPER = Mix(("waxed_oxidized_cut_copper", 3), ("waxed_weathered_cut_copper", 2), patch=0.4)
COPPER_SLAB = "waxed_oxidized_cut_copper_slab[type=bottom]"
DEEP = Mix(("deepslate_bricks", 3), ("cracked_deepslate_bricks", 2), ("deepslate_tiles", 2), ("polished_deepslate", 1),
           patch=0.4)
STORM = Mix(("dark_prismarine", 3), ("deepslate_tiles", 2), ("lapis_block", 0.6), patch=0.4)
CHAR = Mix(("blackstone", 2), ("cracked_deepslate_tiles", 2), ("tuff", 1), ("coal_block", 0.6), ("basalt[axis=y]", 1),
           patch=0.2)
GLASS = Mix(("light_blue_stained_glass", 4), ("glass", 1), ("blue_stained_glass", 2), ("white_stained_glass", 1),
            patch=0.2)


def rod_post(g, x, z, h, y0=1, shaft=None, light=True):
    """A thin storm post: a shaft (deepslate by default) with a copper collar, a sea lantern and a rod on top."""
    shaft = DEEP if shaft is None else shaft
    for y in range(y0, y0 + h):
        g.set(x, y, z, shaft)
    top = y0 + h
    g.set(x, top - 1, z, CUT_COPPER)
    if light:
        g.set(x, top, z, "sea_lantern")
        top += 1
    g.set(x, top, z, ROD)
    return top


def rod_on(g, x, z, y_max=None):
    """A rod stood on whatever is highest in this column."""
    y = g.top_y(x, z, y_max=y_max)
    if g.empty(x, y + 1, z):
        g.set(x, y + 1, z, ROD)
        return True
    return False


def scorch(g, cx, cz, r, ground_y=0, sand=False, rods=0):
    """A lightning strike's mark: the ground's top blocks charred in a ragged star round (cx, cz); on sand a few veins
    of fulgurite (glass) branch out from it. Only touches the layer ground_y, and only cells that are set or unset
    ground (never lifts anything)."""
    for x in range(int(cx - r - 1), int(cx + r + 2)):
        for z in range(int(cz - r - 1), int(cz + r + 2)):
            if not (0 <= x < g.W and 0 <= z < g.D):
                continue
            a = math.atan2(z - cz, x - cx)
            star = r * (0.55 + 0.45 * abs(math.cos(a * 2.5 + cx)))
            d = math.hypot(x - cx, z - cz)
            if d <= star * (0.7 + 0.5 * g.noise3_at(2.5, x, 0, z)):
                s = g.get(x, ground_y, z)
                if s is not None and ("water" in s or "frame" in s):
                    continue
                if g.empty(x, ground_y + 1, z) or g.get(x, ground_y + 1, z) is None:
                    g.set(x, ground_y, z, CHAR if not sand or d < r * 0.35 else GLASS if g.chance(0.35) else CHAR,
                          force=False)
    if sand:
        for k in range(4):
            a = k * 1.6 + g.rng.uniform(-0.4, 0.4)
            n = int(r + 2 + g.rng.randint(0, 3))
            x, z = cx, cz
            for _ in range(n):
                x += math.cos(a)
                z += math.sin(a)
                a += g.rng.uniform(-0.5, 0.5)
                xi, zi = int(round(x)), int(round(z))
                if 0 <= xi < g.W and 0 <= zi < g.D:
                    s = g.get(xi, ground_y, zi)
                    if (s is None or "sand" in s) and (g.empty(xi, ground_y + 1, zi)):
                        g.set(xi, ground_y, zi, GLASS)
    for _ in range(rods):
        x = int(cx + g.rng.randint(-1, 1))
        z = int(cz + g.rng.randint(-1, 1))
        if g.empty(x, ground_y + 1, z) and g.solid(x, ground_y, z):
            g.set(x, ground_y + 1, z, ROD)


def fulgurite(g, x, z, h, y0=1, branches=2):
    """A standing fulgurite: a branching tube of glass the lightning drove up out of the sand."""
    for y in range(y0, y0 + h):
        g.set(x, y, z, GLASS)
    for k in range(branches):
        bx, bz = (1, 0) if k % 2 == 0 else (0, 1)
        if g.chance(0.5):
            bx, bz = -bx, -bz
        by = y0 + g.rng.randint(1, max(1, h - 1))
        g.line((x, by, z), (x + bx * 2, by + 2, z + bz * 2), GLASS)


def sea_floor(g, x1, z1, x2, z2, mat, y_top=-1, y_bot=-3):
    """Solid footing under the sea (a pier's or a platform's foundations), from y_bot up to y_top."""
    g.box(x1, y_bot, z1, x2, y_top, z2, mat)


def hang(g, x, y, z, links=1, lamp="lantern[hanging=true]"):
    """Chain links down from under the block at (x, y + 1, z), a lantern at the end; returns the lantern's y."""
    for k in range(links):
        g.set(x, y - k, z, "chain[axis=y]")
    g.set(x, y - links, z, lamp)
    return y - links


def clear_air(g, x1, y1, z1, x2, y2, z2):
    g.box(x1, y1, z1, x2, y2, z2, AIR)
