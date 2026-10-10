"""No. 6  Fallen Colossus: a giant stone statue of a robed villager once stood astride the frame. It broke at the
waist. Its legs and the flared hem of its tunic still stand on the pedestal like a gate around the frame; its upper
body lies on its back in front of it, one arm flung out with its hand open, and its great hooded head (that
unmistakable nose) lies face up, the blue light of the rift still glowing in its eyes. Cracks climb its legs and
spill over the pedestal."""
import math

import leak
import mc
from common import frame_with_steps
from voxel import AIR, Grid, Mix, lantern, slab, stairs

INFO = dict(number=6, id="fallen_colossus", name="Fallen Colossus", mood="statue, toppled")

W, D, H, G = 67, 85, 40, 2
CX = 33
PZ = 22                      # the pedestal's middle (z); the legs stand here

STATUE = Mix(("smooth_stone", 3), ("stone", 3), ("andesite", 2), ("tuff", 1), patch=0.6, scale=5)
FACE = Mix(("calcite", 3), ("smooth_stone", 2), ("diorite", 1), patch=0.5)
HOOD = Mix(("tuff", 3), ("andesite", 2), ("cobbled_deepslate", 1), patch=0.5)
PEDESTAL = Mix(("deepslate_bricks", 4), ("cracked_deepslate_bricks", 2), ("deepslate_tiles", 1), patch=0.5)
RUBBLE = Mix(("cobblestone", 3), ("stone", 2), ("andesite", 2), ("tuff", 1), ("gravel", 1), patch=0.3)
RUBBLE_TOP = Mix(("stone_slab[type=bottom]", 2), ("cobblestone_slab[type=bottom]", 2),
                 ("andesite_slab[type=bottom]", 1), ("stone_stairs[facing=east]", 1))


def build():
    g = Grid(W, H, D, G, seed=606)

    # ---- the pedestal: three dark tiers with steps up the front
    tiers = [((CX - 15, PZ - 12, CX + 15, PZ + 12), 1, 2), ((CX - 13, PZ - 10, CX + 13, PZ + 10), 3, 4),
             ((CX - 11, PZ - 8, CX + 11, PZ + 8), 5, 6)]
    for (x1, z1, x2, z2), y1, y2 in tiers:
        g.box(x1, y1, z1, x2, y2, z2, PEDESTAL)
        for x in range(x1, x2 + 1):
            g.set(x, y2, z2, "polished_deepslate")
            g.set(x, y2, z1, "polished_deepslate")
    g.box(CX - 16, 0, PZ - 13, CX + 16, 0, PZ + 13, "deepslate_tiles")
    for (y, z) in ((1, PZ + 13), (2, PZ + 12), (3, PZ + 11), (4, PZ + 10), (5, PZ + 9), (6, PZ + 8)):
        for x in range(CX - 4, CX + 5):
            g.set(x, y, z, stairs("deepslate_brick_stairs", "north"))
            for yy in range(1, y):
                g.set(x, yy, z, PEDESTAL)

    # ---- the legs: feet, round shins, knees, thighs
    for side in (-1, 1):
        lx = CX + side * 7
        g.box(lx - 3, 7, PZ - 3, lx + 3, 8, PZ + 5, STATUE)                    # foot
        for x in range(lx - 3, lx + 4):
            g.set(x, 7, PZ + 6, stairs("stone_stairs", "north"))      # rounded toes
            g.set(x, 9, PZ + 3, stairs("stone_stairs", "north"))
        g.box(lx - 2, 9, PZ - 3, lx + 2, 9, PZ + 2, STATUE)
        g.line((lx, 9, PZ - 0.5), (lx, 18, PZ - 0.5), STATUE, r=2.6)          # shin
        g.line((lx, 18, PZ - 0.3), (lx, 19, PZ + 0.2), STATUE, r=3.0)         # knee
        g.line((lx, 19, PZ), (lx - side * 1.5, 29, PZ - 0.5), STATUE, r=3.1)  # thigh, leaning in to the hips
    # ---- the tunic: a flared skirt from the waist down to just above the knees, broken at the waist
    for y in range(20, 37):
        rx = 11.0 - (y - 20) * 0.16
        rz = 5.6 - (y - 20) * 0.05
        for x in range(int(CX - rx) - 1, int(CX + rx) + 2):
            for z in range(int(PZ - rz) - 1, int(PZ + rz) + 2):
                d = ((x - CX) / rx) ** 2 + ((z - PZ) / rz) ** 2
                if d <= 1.0:
                    hem = y in (20, 21)
                    fold = (x - CX) % 3 == 0 and d > 0.6
                    g.set(x, y, z, "polished_andesite" if hem else ("andesite" if fold else STATUE))
    for x in range(CX - 10, CX + 11):          # the belt
        for z in range(PZ - 6, PZ + 7):
            if g.get(x, 33, z) is not None and ((x - CX) / 8.5) ** 2 + ((z - PZ) / 4.9) ** 2 > 0.75:
                g.set(x, 33, z, "chiseled_stone_bricks")
    # the gap under the hem between the legs, where the frame stands
    for y in range(7, 20):
        for x in range(CX - 4, CX + 5):
            for z in range(PZ - 6, PZ + 7):
                if g.get(x, y, z) is not None and abs(x - CX) <= 3:
                    g.set(x, y, z, AIR)
    fz = PZ
    frame_with_steps(g, CX - 2, 7, fz, 3, 5, "polished_deepslate_stairs")
    for x in (CX - 3, CX + 3):
        g.set(x, 7, fz + 3, lantern("soul_lantern"))
        g.set(x, 7, fz - 3, lantern("soul_lantern"))

    # ================================================================ the break at the waist
    g.ruin_tops(CX - 12, PZ - 7, CX + 12, PZ + 7, 26, H - G - 1, 27, 37, scale=3, salt=61)
    g.smash(CX + 7, 33, PZ - 2, 3.0)
    g.enforce_spans(max_span=5)

    # ---- the fallen upper body: on its back in front of the pedestal (to the right), waist toward it, head toward us
    bx = 44
    g.ellipsoid(bx, 5, 51, 8.5, 4.6, 7.5, STATUE)                 # chest
    g.ellipsoid(bx, 5, 57, 10.5, 4.2, 3.6, STATUE)                # shoulders
    g.ellipsoid(bx, 4, 43, 7.0, 4.0, 3.5, STATUE)                 # belly, broken off at the waist
    for z in range(38, 43):
        for x in range(bx - 8, bx + 9):
            for y in range(1, 10):
                if g.chance(0.25 + (42 - z) * 0.17):
                    g.clear(x, y, z, count=True)
    for z in range(46, 58):                                        # the robe's neckline on the chest
        for xx in (bx + (z - 46) // 3 - 2, bx - (z - 46) // 3 + 2):
            g.set(xx, g.top_y(xx, z), z, "andesite")
    g.line((bx, 4, 59), (bx + 1, 4, 62), STATUE, r=2.6)           # neck
    # its right arm along its side; its left arm flung out, broken at the elbow, hand open
    g.line((bx + 10, 4, 56), (bx + 11, 3, 48), STATUE, r=2.3)
    g.line((bx + 11, 3, 48), (bx + 10, 3, 41), STATUE, r=2.1)
    g.box(bx + 8, 1, 37, bx + 13, 4, 40, STATUE)                   # a closed fist
    g.line((bx - 10, 4, 57), (bx - 17, 3, 61), STATUE, r=2.3)
    g.line((bx - 20, 3, 62), (bx - 28, 3, 65), STATUE, r=2.1)
    hx, hz = bx - 32, 66                                           # the open hand, palm up
    g.box(hx - 2, 1, hz - 3, hx + 2, 2, hz + 2, FACE)
    for k, dz in enumerate((-3, -1, 1, 3)):                         # fingers, curling up at the tips
        g.line((hx - 3, 1.5, hz + dz * 0.8), (hx - 7, 1.5, hz + dz * 1.0), FACE, r=0.6)
        g.set(hx - 7, 3, int(round(hz + dz * 1.0)), FACE)
    g.line((hx + 1, 1.5, hz + 3), (hx - 1, 2, hz + 6), FACE, r=0.6)  # thumb

    # ---- the head (big, like every villager's): lying face up, crown toward us, hood around it
    hcx, hcy, hcz = bx + 2, 7, 72
    g.ellipsoid(hcx, hcy, hcz, 7.0, 6.4, 7.6, FACE)
    for x in range(hcx - 10, hcx + 11):
        for y in range(1, 16):
            for z in range(hcz - 3, hcz + 11):
                d = ((x - hcx) / 8.3) ** 2 + ((y - hcy) / 7.6) ** 2 + ((z - hcz) / 8.8) ** 2
                inner = ((x - hcx) / 7.1) ** 2 + ((y - hcy) / 6.5) ** 2 + ((z - hcz) / 7.7) ** 2
                if d <= 1.0 and inner > 1.0 and (z >= hcz + 2 or y <= hcy):
                    g.set(x, y, z, HOOD)

    def surface(x, z):
        return g.top_y(x, z, y_max=16)

    # the face, on top: the long nose pointing up, the unibrow, glowing eyes, a stern mouth
    for x in range(hcx - 1, hcx + 2):
        for z in range(hcz - 5, hcz + 1):
            top = surface(x, z)
            for y in range(top + 1, top + 4):
                g.set(x, y, z, "smooth_stone" if x != hcx else "stone")
    for x in range(hcx - 5, hcx + 6):
        top = surface(x, hcz + 3)
        g.set(x, top + 1, hcz + 3, "smooth_stone")
        g.set(x, top + 1, hcz + 4, "smooth_stone")
    for ex in (hcx - 3, hcx + 3):
        for ez in (hcz + 1, hcz + 2):
            top = surface(ex, ez)
            g.set(ex, top, ez, "blue_stained_glass")
            g.set(ex, top - 1, ez, "sea_lantern")
    for x in range(hcx - 3, hcx + 4):
        g.set(x, surface(x, hcz - 6), hcz - 6, "andesite")             # the mouth

    # ================================================================ debris and the leak
    g.settle_rubble(0.7, 2.6, RUBBLE, RUBBLE_TOP,
                    protect=lambda x, z: abs(x - CX) <= 5 and PZ - 4 <= z <= PZ + 16, max_h=5)
    for (x, z, r, h) in ((CX - 17, 40, 3.5, 3), (CX - 18, 26, 3.0, 2), (CX + 19, 12, 3.0, 2), (CX - 4, 45, 2.5, 2),
                         (CX - 22, 8, 2.5, 2)):
        g.rubble_pile(x, z, r, h, RUBBLE, RUBBLE_TOP)
    for (x, z) in ((CX - 12, 49), (CX - 17, 16), (CX + 20, 6), (CX - 6, 78), (CX - 26, 40)):
        y = g.top_y(x, z) + 1
        g.box(x, y, z, x + 2, y + 1, z + 2, STATUE)
        g.set(x + 1, y + 2, z + 1, STATUE)
    # moss on the fallen pieces (they've lain there a long time)
    for x in range(W):
        for z in range(37, D):
            y = g.top_y(x, z)
            s = g.get(x, y, z)
            if s is not None and mc.base(s) in ("stone", "smooth_stone", "andesite", "cobblestone", "tuff") \
                    and y >= 3 and g.chance(0.22):
                g.set(x, y, z, "mossy_cobblestone" if g.chance(0.6) else "moss_block")

    spine = leak.crack_network(g, [(CX, fz + 2)], branches=2, length=44, fork=0.06, wobble=0.13,
                               seed_angle=math.pi / 2, spread=0.5)
    rest = leak.crack_network(g, [(CX - 3, fz), (CX + 3, fz), (CX, fz - 2)], branches=7, length=26,
                              fork=0.1, wobble=0.25)
    cells = dict(rest)
    for k, v in spine.items():
        cells[k] = max(cells.get(k, 0), v)
    leak.apply_cracks(g, cells, max_surface=7, climb=4)
    # glowing veins climb the inside of each leg
    for side in (-1, 1):
        lx = CX + side * 7
        ang = math.pi if side > 0 else 0.0
        for y in range(9, 20):
            x = int(round(lx + math.cos(ang) * 2.4))
            z = int(round(PZ - 0.5 + math.sin(ang) * 2.4))
            if g.get(x, y, z) is not None and not mc.is_air(g.get(x, y, z)):
                g.set(x, y, z, "blue_stained_glass")
                xi = int(round(lx + math.cos(ang) * 1.3))
                zi = int(round(PZ - 0.5 + math.sin(ang) * 1.3))
                g.set(xi, y, zi, "sea_lantern" if y % 2 else "lapis_block")
            ang += g.rng.uniform(-0.35, 0.35)
    leak.dead_land(g, CX, 34, 26, 42, bush=0.07)
    leak.shards(g, cells, 8, min_dist=7, frame_center=(CX, fz), tall=0.5, max_surface=7)
    for (x, z, h) in ((4, 6, 8), (61, 5, 7), (5, 76, 9), (22, 80, 6), (4, 32, 7), (63, 34, 6)):
        leak.dead_tree(g, x, z, h, wood="spruce_log" if h % 2 else "dark_oak_log", branches=4)
    for z in range(PZ + 14, D):
        for x in range(CX - 2, CX + 3):
            if g.chance(0.65) and g.get(x, 0, z) is None:
                g.set(x, 0, z, Mix(("gravel", 2), ("coarse_dirt", 1), ("cobblestone", 1)))
    return g, INFO
