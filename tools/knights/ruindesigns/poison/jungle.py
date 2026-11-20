"""Poison's jungle ruins (jungle, sparse jungle, bamboo jungle): a lost temple's mossy stone bricks and carved stone
under jungle trees, vines down every face, bamboo groves, cocoa on the trunks and froglights in the gloom."""
import math

from ruinkit import (AIR, FRAME, SURFACE, Area, Mix, broken_frame, court, flat_frame, guard, layout, mound, pillar,
                     rubble, slab, stairs)
from ruindesigns.poison.common import (froglamp, giant_mushroom, hang_vines, hanging_froglight, poison_dress, pool,
                                       webs_in_corners)

P = dict(
    floor=Mix(("mossy_stone_bricks", 3), ("stone_bricks", 2), ("cracked_stone_bricks", 1), ("mossy_cobblestone", 2),
              patch=0.5),
    wall=Mix(("mossy_stone_bricks", 4), ("stone_bricks", 2), ("cracked_stone_bricks", 1), ("mossy_cobblestone", 1),
             patch=0.4),
    carved="chiseled_stone_bricks",
    ground=Mix(("grass_block", 6), ("podzol", 1), ("moss_block", 1), ("coarse_dirt", 0.5), patch=0.55),
    rubble=Mix(("mossy_cobblestone", 2), ("mossy_stone_bricks", 2), ("cobblestone", 1)),
    light="verdant_froglight[axis=y]",
)

AREA = Area(element="poison", id="jungle", name="Jungle", biomes="jungle, sparse jungle, bamboo jungle",
            setting=SURFACE, ground="grass_block", under="dirt", palette=P,
            blurb="A lost temple's mossy stone under jungle trees, vines and bamboo.")

LOG = "jungle_log[axis=y]"
LEAVES = "jungle_leaves[persistent=true]"


def jungle_floor(g, r=12.0, cz=None):
    court(g, P["ground"], r, cz=cz, under="dirt")


def jungle_tree(g, x, z, h, spread=3, y0=1, thick=1, cocoa=2):
    """A jungle tree: a tall trunk (2 x 2 if thick), a leaf blob at the top and a smaller one part way up, cocoa
    pods on the trunk. Returns its top."""
    top = y0 + h
    for ox in range(thick):
        for oz in range(thick):
            for y in range(y0, top):
                g.set(x + ox, y, z + oz, LOG)
    for cy, r in ((top, spread), (top - max(3, h // 2), max(1, spread - 2))):
        for dx in range(-r - 1, r + 2 + thick - 1):
            for dz in range(-r - 1, r + 2 + thick - 1):
                for dy in (0, 1):
                    d = math.hypot(dx - (thick - 1) * 0.5, dz - (thick - 1) * 0.5) + dy * 1.2
                    if d <= r + 0.5 - g.rng.random() * 0.9 and g.get(x + dx, cy + dy, z + dz) is None:
                        g.set(x + dx, cy + dy, z + dz, LEAVES)
        if cy != top:
            # a branch out to the lower blob
            pass
    # cocoa on the trunk
    faces = [((-1, 0), "east"), ((thick, 0), "west"), ((0, -1), "south"), ((0, thick), "north")]
    for k in range(cocoa):
        (dx, dz), f = g.rng.choice(faces)
        y = g.rng.randint(y0 + 2, max(y0 + 3, top - 4))
        if g.get(x + dx, y, z + dz) is None:
            g.set(x + dx, y, z + dz, f"cocoa[age=2,facing={f}]")
    return top


def bamboo_clump(g, cx, cz, r, hmin=5, hmax=11, density=0.6, avoid=None):
    """A clump of bamboo on the grass: stalks of mixed heights, leafy at their tops."""
    for x in range(int(cx - r) - 1, int(cx + r) + 2):
        for z in range(int(cz - r) - 1, int(cz + r) + 2):
            if not (0 < x < g.W - 1 and 0 < z < g.D - 1) or math.hypot(x - cx, z - cz) > r or not g.chance(density):
                continue
            if avoid and avoid(x, z):
                continue
            if g.get(x, 1, z) is not None:
                continue
            g.set(x, 0, z, "grass_block")
            h = g.rng.randint(hmin, hmax)
            for y in range(1, h + 1):
                if g.get(x, y, z) is not None:
                    break
                leaves = "none" if y < h - 2 else ("small" if y < h else "large")
                g.set(x, y, z, f"bamboo[age=1,leaves={leaves},stage=0]")


def jungle_vines(g, chance=0.25):
    hang_vines(g, 0, 0, g.W - 1, g.D - 1, chance=chance, y_lo=2, y_hi=g.H - g.G - 2, max_len=5)


# ---------------------------------------------------------------- 1 Step Pyramid

@layout("step_pyramid", "Step Pyramid", size=(25, 20, 25),
        blurb="A three-stepped temple mound, a steep stair up its face, the frame in the broken shrine on its top.")
def step_pyramid(g, p):
    """Three tiers of mossy stone bricks stepping up at the back, a steep stair up the middle of their face, carved
    stone at the corners; on the top tier the shrine's two broken walls either side of the frame."""
    mid = g.W // 2
    jungle_floor(g, cz=12)
    tiers = [(10, 7.0, 1, 3), (7, 5.0, 4, 6), (5, 3.5, 7, 8)]   # half-width, half-depth, from y, to y
    CZ = 6
    for hw, hd, y1, y2 in tiers:
        for x in range(mid - hw, mid + hw + 1):
            for z in range(int(CZ - hd), int(CZ + hd) + 1):
                if z < 0 or z >= g.D:
                    continue
                for y in range(y1, y2 + 1):
                    g.set(x, y, z, p["wall"])
        for x, z in ((mid - hw, int(CZ + hd)), (mid + hw, int(CZ + hd))):
            g.set(x, y2, z, p["carved"])
        # a lip of slabs round each tier's top edge
        for x in range(mid - hw, mid + hw + 1):
            if g.chance(0.5):
                g.set(x, y2 + 1, int(CZ + hd), slab("mossy_stone_brick_slab"))
    TOP = 8
    # the stair up the front, 3 wide, carved cheeks
    for k in range(TOP):
        z = int(CZ + 7) + 1 - (k * 6) // TOP
        z = 14 - k
        y = 1 + k
        if z < CZ:
            break
        for x in range(mid - 1, mid + 2):
            g.set(x, y, z, stairs("mossy_stone_brick_stairs", "north"), force=True)
            for yy in range(1, y):
                g.set(x, yy, z, p["wall"], force=True)
            for yy in range(y + 1, y + 4):
                if yy > TOP:
                    break
                g.set(x, yy, z, AIR, force=True)
        for x in (mid - 2, mid + 2):
            for yy in range(1, y + 1):
                g.set(x, yy, z, p["wall"])
    # break the tiers' edges
    g.ruin_tops(mid - 11, 0, mid + 11, 14, 2, 8, 0.5, 8.0, scale=3.0, salt=31,
                region=lambda x, z: (abs(x - mid) >= 7 or z >= 12) and abs(x - mid) > 2)
    # the shrine's broken side walls on top, the frame between them
    for x in (mid - 4, mid + 4):
        for z in range(3, 7):
            for y in range(TOP + 1, TOP + (5 if x < mid else 3) + 1):
                g.set(x, y, z, p["wall"])
    for x in range(mid - 4, mid + 5):
        g.set(x, TOP + 6, 3, p["carved"] if abs(x - mid) == 4 else (p["wall"] if x < mid - 1 else None))
    cells = broken_frame(g, mid - 2, TOP + 1, 4, [(4, 6), (3, 6), (4, 5)])
    for x in (mid - 3, mid + 3):
        g.set(x, TOP + 1, 7, p["light"])
    # jungle trees crowding it, bamboo at the front corners
    jungle_tree(g, 1, 2, 15, spread=4, thick=2)
    jungle_tree(g, g.W - 3, 9, 12, spread=3)
    bamboo_clump(g, 2, 20, 2.5)
    bamboo_clump(g, g.W - 3, 21, 2.0)
    rubble(g, cells, p["rubble"], spread=3)
    g.settle_rubble(0.6, 1.0, p["rubble"], protect=lambda x, z: abs(x - mid) <= 6 and z >= 15)
    jungle_vines(g, 0.18)
    poison_dress(g, 8, webs=0.15)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# ---------------------------------------------------------------- 2 Serpent Maw

@layout("serpent_maw", "Serpent Maw", size=(25, 20, 25),
        blurb="A colossal stone serpent's head, its jaws gaping: the frame stands in its mouth.")
def serpent_maw(g, p):
    """A great carved serpent's head at the back, its jaws open wide to the south, fangs hanging, froglight eyes; its
    body arches away over the ground behind and beside it; the frame stands on its lower jaw, inside the mouth."""
    mid = g.W // 2
    jungle_floor(g, cz=13)
    stone = p["wall"]

    def half_w(z, y):
        """The head's half-width at (z, y): broad at the back, a rounded wedge towards the snout."""
        w = 5.6 - max(0, z - 7) * 0.45
        if y >= 11:
            w -= (y - 10) * 0.9
        return w

    # the lower jaw (y 1-2, z 2-13) and the skull and upper jaw (y 3-13 at the back, 9-12 forward)
    for z in range(1, 14):
        for y in range(1, 14):
            for x in range(mid - 6, mid + 7):
                if abs(x - mid) > half_w(z, y):
                    continue
                lower = y <= 2
                skull = z <= 4
                upper = 9 <= y <= (12 if z <= 9 else 11 if z <= 11 else 10)
                if lower or skull or upper:
                    g.set(x, y, z, stone)
    # the mouth's hollow and its paving (the cheeks stay at the back half)
    for z in range(5, 14):
        for y in range(3, 9):
            for x in range(mid - 4, mid + 5):
                g.set(x, y, z, AIR, force=True)
        if z <= 8:
            for x in (mid - 5, mid + 5):
                for y in range(3, 9):
                    g.set(x, y, z, stone)
    for x in range(mid - 3, mid + 4):
        for z in range(5, 13):
            g.set(x, 2, z, Mix(("stone_bricks", 2), ("mossy_stone_bricks", 1), patch=0.4))
    # fangs: two long ones down from the upper jaw's front corners, two short up from the lower
    for x in (mid - 3, mid + 3):
        for y in range(5, 9):
            g.set(x, y, 12, "mossy_stone_brick_wall")
        g.set(x, 3, 12, "mossy_stone_brick_wall")
    for x in (mid - 1, mid + 1):
        g.set(x, 8, 12, "mossy_stone_brick_wall")
    # eyes high on the head's sides, brows over them, nostrils on the snout
    for x, f in ((mid - 4, "east"), (mid + 4, "west")):
        g.set(x, 12, 8, p["light"], force=True)
        g.set(x, 13, 8, p["carved"])
        g.set(x, 13, 7, p["carved"])
    for x in (mid - 1, mid + 1):
        g.set(x, 11, 12, p["carved"])
    # a crest of carved ridges down the back of the skull
    for z in range(1, 6):
        g.set(mid, 14, z, p["carved"] if z % 2 else stone)
    g.set(mid, 15, 2, p["carved"])
    # the body: from the back of the head it arches up and over to the right and dives into the ground in front;
    # the tail coils out of the ground at the left
    g.line((mid + 3, 6, 1), (mid + 8, 8, 2), stone, r=1.3)
    g.line((mid + 8, 8, 2), (mid + 10, 6, 7), stone, r=1.3)
    g.line((mid + 10, 6, 7), (mid + 10, 1, 12), stone, r=1.3)
    g.line((mid + 10, 1, 12), (mid + 10, -1, 14), stone, r=1.2)
    g.line((2, 0, 9), (2, 3, 6), stone, r=0.8)
    g.line((2, 3, 6), (3, 4, 3), stone, r=0.6)
    g.set(3, 5, 3, p["carved"])
    # the frame on the lower jaw, inside the mouth
    cells = broken_frame(g, mid - 2, 3, 6, [(4, 6), (3, 6), (0, 6), (4, 5)])
    g.ruin_tops(mid - 7, 0, mid + 7, 6, 12, 16, 11.0, 16.0, scale=2.5, salt=41, region=lambda x, z: x < mid - 2)
    # steps up onto the jaw's tip
    for x in range(mid - 1, mid + 2):
        g.set(x, 1, 14, stairs("mossy_stone_brick_stairs", "north"))
    jungle_tree(g, 1, 1, 14, spread=4)
    jungle_tree(g, g.W - 2, 2, 11, spread=3)
    bamboo_clump(g, 2, 17, 2.2)
    rubble(g, cells, p["rubble"], spread=3)
    jungle_vines(g, 0.1)
    poison_dress(g, 8, webs=0.15)
    return [guard(mid - 4, 18), guard(mid + 4, 18)]


# ---------------------------------------------------------------- 3 Canopy Bridge

@layout("canopy_bridge", "Canopy Bridge", size=(25, 22, 25),
        blurb="Two giant jungle trees hold up a broken plank platform between them; the frame stands up on it.")
def canopy_bridge(g, p):
    """Two great 2 x 2 jungle trees at the back left and right; between them a platform of jungle planks on log
    beams, 7 up, its middle sagging and holed, the frame on it; a ladder up one trunk, vines and a stone stair base."""
    mid = g.W // 2
    jungle_floor(g, cz=12)
    DY = 7
    jungle_tree(g, 2, 3, 17, spread=4, thick=2, cocoa=4)
    jungle_tree(g, g.W - 4, 4, 15, spread=4, thick=2, cocoa=4)
    # beams from trunk to trunk, and the platform's planks on them
    for z in (3, 7):
        g.line((4, DY - 1, z), (g.W - 5, DY - 1, z), "jungle_log[axis=x]")
    for x in range(4, g.W - 4):
        for z in range(3, 8):
            if (x + z) % 7 == 0 or (abs(x - mid) <= 1 and z == 7) or (x in (mid + 5, mid + 6) and z >= 6):
                continue
            g.set(x, DY, z, "jungle_planks")
    # posts down to the ground under the beams, two of them
    for x in (mid - 5, mid + 5):
        for z in (3, 7):
            for y in range(1, DY - 1):
                g.set(x, y, z, "stripped_jungle_log[axis=y]")
    # a railing of jungle fence, broken
    for x in range(4, g.W - 4):
        if g.chance(0.55):
            g.set(x, DY + 1, 3, "jungle_fence")
    cells = broken_frame(g, mid - 2, DY + 1, 4, [(0, 6), (1, 6), (0, 5)])
    # a ladder up the left tree's front, and a stone stair-base up to a landing at its foot
    for y in range(1, DY + 1):
        g.set(3, y, 5, "ladder[facing=south]")
    g.set(3, DY, 5, AIR, force=True)
    g.set(3, DY, 6, "jungle_planks")
    # froglights hung under the platform and in lanterns on its posts
    for x in (mid - 3, mid + 3):
        g.set(x, DY - 1, 5, "jungle_planks")
        g.set(x, DY - 2, 5, "chain[axis=y]")
        g.set(x, DY - 3, 5, p["light"])
    # the old temple's stones under it: a broken floor and a fallen carved block
    court(g, p["floor"], 6.0, cx=mid, cz=8, ragged=3.5, under="dirt")
    for x, z in ((mid - 2, 10), (mid + 3, 11)):
        g.set(x, 1, z, p["carved"])
    bamboo_clump(g, g.W - 2, 19, 2.0)
    bamboo_clump(g, 2, 15, 2.0)
    rubble(g, cells, p["rubble"], spread=4)
    jungle_vines(g, 0.22)
    poison_dress(g, 10, webs=0.15)
    return [guard(mid - 3, 16), guard(mid + 3, 16)]


# ---------------------------------------------------------------- 4 Bamboo Shrine

@layout("bamboo_shrine", "Bamboo Shrine", size=(25, 18, 25),
        blurb="A little stone shrine lost in a bamboo grove; a path cut through the stalks leads to it.")
def bamboo_shrine(g, p):
    """A walled court in a dense bamboo grove, a little roofless shrine house at its back with the frame in its
    doorway, stone lanterns by its steps; the bamboo cut back for the court and the path in."""
    mid = g.W // 2
    jungle_floor(g, cz=12)
    court(g, p["floor"], 6.5, cx=mid, cz=13, ragged=2.0, under="dirt", rx=7.5, rz=6.5)
    # the shrine house at the back: thick walls on a plinth, its roof gone but a corner of it
    for x in range(mid - 5, mid + 6):
        for z in range(2, 8):
            g.set(x, 1, z, p["wall"])
    g.walls(mid - 5, 2, mid + 5, 7, 2, 7, p["wall"])
    for x in range(mid - 2, mid + 3):
        for y in range(2, 9):
            g.set(x, y, 7, AIR, force=True)
    g.ruin_tops(mid - 5, 2, mid + 5, 7, 4, 9, 3.5, 7.5, scale=2.5, salt=51)
    for x in range(mid - 6, mid - 1):
        g.set(x, 8, 2, stairs("mossy_stone_brick_stairs", "north", "bottom"))
        for z in range(3, 5):
            if g.chance(0.7):
                g.set(x, 8, z, slab("mossy_stone_brick_slab"))
    for y in range(2, 8):
        g.set(mid - 5, y, 2, p["carved"] if y in (2, 7) else p["wall"])
    cells = broken_frame(g, mid - 2, 2, 7, [(4, 6), (4, 5), (3, 6), (0, 6)])
    # steps down from the plinth and stone lanterns either side
    for x in range(mid - 3, mid + 4):
        g.set(x, 1, 8, stairs("mossy_stone_brick_stairs", "north"))
    for x in (mid - 4, mid + 4):
        g.set(x, 1, 10, p["carved"])
        g.set(x, 2, 10, "mossy_stone_brick_wall")
        g.set(x, 3, 10, p["light"])
        g.set(x, 4, 10, slab("mossy_stone_brick_slab"))
    # the grove: dense bamboo all round, cut back from the court, the shrine and the path in
    def cleared(x, z):
        return (math.hypot((x - mid) / 9.0, (z - 13) / 8.0) <= 1.0 or (1 <= z <= 9 and abs(x - mid) <= 7)
                or z >= 18 or (abs(x - mid) <= 3 and z >= 12))
    bamboo_clump(g, mid, 12, 13.0, hmin=5, hmax=12, density=0.35, avoid=cleared)
    # a low broken wall at the court's edge, half eaten by the bamboo
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot((x - mid) / 8.0, (z - 13) / 7.0)
            if 0.95 <= d <= 1.08 and z > 8 and abs(x - mid) > 2 and g.get(x, 1, z) is None and g.chance(0.6):
                g.set(x, 1, z, p["wall"])
    jungle_tree(g, g.W - 3, 2, 13, spread=3)
    rubble(g, cells, p["rubble"], spread=3)
    jungle_vines(g, 0.15)
    poison_dress(g, 6, webs=0.2)
    return [guard(mid - 3, 14), guard(mid + 3, 14)]


# ---------------------------------------------------------------- 5 Toppled Idol

@layout("toppled_idol", "Toppled Idol", size=(25, 18, 25),
        blurb="A giant stone idol has fallen face up across the clearing; the frame stands on its empty plinth.")
def toppled_idol(g, p):
    """A tall plinth at the back, the idol's feet still on it, the frame standing between them; the idol lies
    toppled down the clearing's left side, face up, its head broken off and rolled aside."""
    mid = g.W // 2
    jungle_floor(g, cz=12)
    # the plinth: two steps up, its front carved
    for x in range(mid - 5, mid + 6):
        for z in range(2, 9):
            g.set(x, 1, z, p["wall"])
            if 3 <= z <= 7 and abs(x - mid) <= 4:
                g.set(x, 2, z, p["wall"])
        g.set(x, 1, 9, stairs("mossy_stone_brick_stairs", "north"))
    for x in range(mid - 4, mid + 5, 2):
        g.set(x, 2, 7, p["carved"])
    # the idol's feet still on it, broken off at the ankle, either side of the frame
    for fx in (mid - 4, mid + 3):
        for x in (fx, fx + 1):
            for z in (3, 4, 5):
                g.set(x, 3, z, p["wall"])
                if z < 5:
                    g.set(x, 4, z, p["wall"])
        g.set(fx, 5, 3, p["wall"])
    cells = broken_frame(g, mid - 2, 3, 5, [(4, 6), (3, 6), (2, 6), (4, 5)])
    # the toppled body lying face up down the left side: legs, hips, chest, arms at its sides
    BX = 4
    for z in range(9, 21):
        w = 1 if z < 13 else 2
        for x in range(BX - w, BX + w + 1):
            for y in range(1, 3 if z < 13 else 4):
                g.set(x, y, z, p["wall"])
    for z in range(15, 21):
        for x in (BX - 3, BX + 3):
            g.set(x, 1, z, p["wall"])
            g.set(x, 2, z, p["wall"] if z < 20 else p["carved"])
    for x in range(BX - 2, BX + 3):
        g.set(x, 4, 17, p["carved"])
    g.set(BX, 4, 15, p["light"])
    # the head, broken off, rolled to the right, its face to the sky
    HX, HZ = mid + 6, 19
    for x in range(HX - 2, HX + 2):
        for z in range(HZ - 2, HZ + 2):
            for y in range(1, 5):
                g.set(x, y, z, p["wall"])
    for x, z in ((HX - 1, HZ - 1), (HX, HZ - 1)):
        g.set(x, 4, z, p["light"], force=True)
    g.set(HX - 1, 4, HZ + 1, p["carved"])
    g.set(HX, 4, HZ + 1, p["carved"])
    g.set(HX - 2, 5, HZ - 2, slab("mossy_stone_brick_slab"))
    g.set(HX + 1, 5, HZ - 2, slab("mossy_stone_brick_slab"))
    jungle_tree(g, g.W - 3, 4, 13, spread=4, thick=2, cocoa=3)
    jungle_tree(g, 1, 3, 11, spread=3)
    bamboo_clump(g, g.W - 3, 13, 2.0)
    rubble(g, cells, p["rubble"], spread=3)
    jungle_vines(g, 0.2)
    poison_dress(g, 10, webs=0.15)
    return [guard(mid - 2, 14), guard(mid + 3, 14)]


# The user's picks (November 18, 2026); the others are kept here for the record, not built.
LAYOUTS = [step_pyramid, serpent_maw, bamboo_shrine]
DROPPED = [canopy_bridge, toppled_idol]
