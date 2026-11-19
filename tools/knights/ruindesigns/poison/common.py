"""Poison's shared dressing for its areas' ruins (not an area itself: ruindesigns.AREAS doesn't list it).

Giant mushrooms, vines hung down faces, a scatter of mushrooms, cobwebs and moss, froglight lamps."""
import math

import mc
from ruinkit import AIR, scatter_on_floor

SOIL = {"dirt", "grass_block", "podzol", "coarse_dirt", "mycelium", "rooted_dirt", "moss_block", "mud",
        "muddy_mangrove_roots"}
SIDES = (("north", 0, -1), ("south", 0, 1), ("east", 1, 0), ("west", -1, 0))


def base_of(g, x, y, z):
    s = g.get(x, y, z)
    return mc.base(s) if s else ("grass_block" if y <= 0 else "air")


def giant_mushroom(g, x, z, h, r, kind="red", y0=1, lean=(0, 0)):
    """A huge mushroom: a stem from y0 up h blocks (leaning by `lean` blocks at its top: a face-connected line), then
    its cap: red a dome of red blocks, brown a wide flat plate. Returns the cap's middle (x, y, z)."""
    tx, tz = x + lean[0], z + lean[1]
    top = y0 + h - 1
    g.line((x, y0, z), (tx, top, tz), "mushroom_stem")
    if kind == "red":
        cy = top + 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                d = math.hypot(dx, dz)
                if d > r + 0.4:
                    continue
                # the dome: the rim hangs down a block or two, the middle is the highest layer
                if d <= r - 1.4:
                    g.set(tx + dx, cy + 1, tz + dz, "red_mushroom_block")
                    if d <= r - 2.6 and r >= 3:
                        g.set(tx + dx, cy + 2, tz + dz, "red_mushroom_block")
                else:
                    g.set(tx + dx, cy, tz + dz, "red_mushroom_block")
                    if d > r - 0.6 and r >= 3:
                        g.set(tx + dx, cy - 1, tz + dz, "red_mushroom_block")
        for y in range(top + 1, cy + 2):
            g.set(tx, y, tz, "mushroom_stem")
        return tx, cy + 1, tz
    cy = top + 1
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            if math.hypot(dx, dz) <= r + 0.3:
                g.set(tx + dx, cy, tz + dz, "brown_mushroom_block")
    return tx, cy, tz


def hang_vines(g, x1, z1, x2, z2, chance=0.35, y_lo=2, y_hi=16, max_len=4, block="vine"):
    """Vines on the sides of solid blocks in the box: a vine hangs in the air beside an opaque block, and on down a few
    cells (each held by the one above)."""
    for x in range(max(x1, 0), min(x2, g.W - 1) + 1):
        for z in range(max(z1, 0), min(z2, g.D - 1) + 1):
            for y in range(y_lo, y_hi + 1):
                if g.get(x, y, z) is not None:
                    continue  # only unset cells (explicit air was cleared on purpose and stays clear)
                for side, dx, dz in SIDES:
                    if g.opaque(x + dx, y, z + dz) and g.get(x + dx, y, z + dz) is not None \
                            and mc.base(g.get(x + dx, y, z + dz)) != "dross_portal_frame" and g.chance(chance):
                        st = f"vine[{side}=true]"
                        n = g.rng.randint(1, max_len)
                        for k in range(n):
                            yy = y - k
                            if yy < 1 or g.get(x, yy, z) is not None:
                                break
                            g.set(x, yy, z, st if k == 0 or g.opaque(x + dx, yy, z + dz) else st)
                        break


def poison_dress(g, count, mushrooms=0.35, webs=0.2, moss=0.3, roses=0.0, soil="podzol", y_max=2, extra=None):
    """Mushrooms (their block turned to soil), cobwebs, moss carpets and (sparingly) wither roses on the floor."""
    def place(x, y, z):
        r = g.rng.random()
        if r < mushrooms:
            # mushrooms keep only on podzol or mycelium in daylight
            if base_of(g, x, y, z) not in ("podzol", "mycelium"):
                g.set(x, y, z, "mycelium" if soil == "mycelium" else "podzol")
            g.set(x, y + 1, z, g.rng.choice(("red_mushroom", "brown_mushroom", "brown_mushroom")))
            return True
        r -= mushrooms
        if r < webs:
            g.set(x, y + 1, z, "cobweb")
            return True
        r -= webs
        if r < moss:
            g.set(x, y + 1, z, "moss_carpet")
            return True
        r -= moss
        if r < roses:
            if base_of(g, x, y, z) not in SOIL:
                g.set(x, y, z, soil)
            g.set(x, y + 1, z, "wither_rose")
            return True
        if extra:
            return extra(x, y, z)
        return False
    return scatter_on_floor(g, count, place, y_max=y_max)


def froglamp(g, x, z, y=1, post="mossy_cobblestone_wall", h=2):
    """A post with a verdant froglight on top."""
    for k in range(h):
        g.set(x, y + k, z, post)
    g.set(x, y + h, z, "verdant_froglight[axis=y]")


def hanging_froglight(g, x, y, z, chain=1):
    """A froglight hung on chains under the block at y + chain + 1."""
    for k in range(chain):
        g.set(x, y + 1 + k, z, "chain[axis=y]")
    g.set(x, y, z, "verdant_froglight[axis=y]")


def webs_in_corners(g, x1, y1, z1, x2, y2, z2, chance=0.4):
    """Cobwebs in the air cells of the box that have two or more solid neighbours (corners, under beams)."""
    for x in range(x1, x2 + 1):
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                if g.get(x, y, z) is not None or not g.empty(x, y, z):
                    continue
                n = sum(1 for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0))
                        if g.get(x + dx, y + dy, z + dz) is not None and g.solid(x + dx, y + dy, z + dz))
                if n >= 2 and g.chance(chance):
                    g.set(x, y, z, "cobweb")


def pool(g, x1, z1, x2, z2, rim, bed="mud", y=0, depth=1, lilies=0.0, region=None):
    """Water sunk into the ground at height y (its top), walled in by `rim` where the box's edge isn't already solid;
    `region(x, z)` picks which cells are water. Lily pads over some of it."""
    cells = [(x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1) if region is None or region(x, z)]
    cs = set(cells)
    for x, z in cells:
        for k in range(depth):
            g.set(x, y - k, z, "water", force=True)
        g.set(x, y - depth, z, bed)
    for x, z in cells:
        for side, dx, dz in SIDES:
            nx, nz = x + dx, z + dz
            if (nx, nz) in cs:
                continue
            for k in range(depth):
                s = g.get(nx, y - k, nz)
                if s is None or mc.is_air(s) or not mc.is_supporting(s):
                    g.set(nx, y - k, nz, rim, force=True)
            if y >= 1 and g.get(nx, y + 1, nz) is None:
                pass
    if lilies:
        for x, z in cells:
            if g.chance(lilies) and g.get(x, y + 1, z) is None:
                g.set(x, y + 1, z, "lily_pad")
    return cs
