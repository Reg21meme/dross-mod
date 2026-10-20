"""The Huntsman's lodge and the Alpha Moose's barn: his part of his village (CLAUDE.md step 14; the user's notes of
October 19: the lodge with its flags is liked, but "the barn for the moose could have more character, same with its
igloo, it looks basic, maybe has some meat or bones around his area").

His great snow lodge (two rims of packed ice, ice bands, a blue ice cap ringed with antlers) has a wide entrance
under a moose skull and its palm antlers, his skull banners either side and a trophy wall of skulls and antlers on
the dome round it; inside his seat of furs (its back to the wall, facing the door) under a rack of antlers, a big
fire, benches, his bed of furs, spears, trophies and chests; an armoury dome joined to it. Before it his yard: a
path between skull posts under a bone arch, the roasting fire with a spit, racks of drying meat, a butcher's block,
bone piles, a heap of skulls, hides stretched to dry. Beside it the barn: a stone footing, a timber frame and plank
walls, a steep roof hung with icicles, a moose skull and antlers over its wide door; inside the Alpha Moose's stall
(straw, a feed trough, a water trough), a tack wall (saddle, spears, lanterns), barrels of feed and a hay loft up a
ladder; outside it a corral fenced with bone posts and spruce rails.

    build_into(g, x1, z1, x2, z2) -> info     (its box in the village's grid; its way in at the south, z2)
    build()                                   (on its own, for the previews)

Every decoration of his yard and round the barn is a 1 in 2 group like the village's (villagekit.decor).
"""
import math

import mc
from voxel import AIR, Mix

from villagekit import props
from villagekit.decor import Decor
from villagekit.igloo import Massing, Style
from villagekit.interiors import Room
from villagekit.local import Frame
from villagekit.writer import banner

INFO = {"number": 0, "id": "huntsman_lodge", "name": "The Huntsman's Lodge",
        "mood": "his great lodge, an armoury, his yard and the Alpha Moose's barn"}

SIGIL = banner(("sku", "white"), ("bo", "white"), ("bts", "white"))
W, D = 55, 51                      # the box it needs

LODGE_STYLE = Style(bands=(3, 6), band="packed_ice", cap="blue_ice", rim_courses=2)
ARMOURY_STYLE = Style(bands=(3,), band="packed_ice")
STONE = Mix(("cobblestone", 3), ("stone", 2), ("mossy_cobblestone", 1), ("cobbled_deepslate", 1), patch=0.4)
STRAW = Mix(("hay_block[axis=x]", 2), ("hay_block[axis=z]", 2), ("coarse_dirt", 1), patch=0.5)


# PLACEHOLDER: made of blocks until the user's own moose-skull model replaces it (the user, October 20, 2026: "they will be replaced with a real model of the moose skull that I will create later")
def moose_head(f, y, bone="bone_block[axis=y]"):
    """A moose skull mounted over a doorway (facing f's front): the bone muzzle and brow, dark eyes, broad palm
    antlers spreading sideways and up, their tines of birch fence."""
    f.box(-1, y, 1, 1, y + 2, 1, bone)
    f.box(-1, y - 1, 2, 1, y, 2, bone)  # the long muzzle
    f.set(0, y - 2, 2, bone)
    for u in (-1, 1):
        f.set(u, y + 1, 2, "polished_blackstone_button[face=wall,facing={front},powered=false]")   # its eye holes
    for side in (-1, 1):
        for k in range(2, 5):
            f.set(side * k, y + 2, 1, "stripped_birch_wood[axis={lr}]")
        for k in range(4, 8):
            for yy in range(y + 2, y + 2 + (k - 2)):
                if yy <= y + 5:
                    f.set(side * k, yy, 1, "stripped_birch_wood[axis=y]")
        for k in (4, 5, 6, 7):
            f.set(side * k, y + 2 + (k - 2) if k < 6 else y + 6, 1, "birch_fence")
        f.set(side * 7, y + 7, 1, "birch_fence")
        f.set(side * 5, y + 6, 1, "birch_fence")


# PLACEHOLDER: made of blocks until the user's own moose-skull model replaces it (the user, October 20, 2026: "they will be replaced with a real model of the moose skull that I will create later")
def antler_crown(g, cx, cz, top, r=2):
    """A ring of antlers (birch fences branching up) round the lodge's skylight cap."""
    for k in range(8):
        a = 2 * math.pi * k / 8
        x, z = int(round(cx + math.sin(a) * r)), int(round(cz + math.cos(a) * r))
        y = top(x, z)
        if y is None:
            continue
        g.set(x, y + 1, z, "birch_fence", force=True)
        g.set(x, y + 2, z, "birch_fence", force=True)
        x2, z2 = int(round(cx + math.sin(a) * (r + 1))), int(round(cz + math.cos(a) * (r + 1)))
        if k % 2 == 0 and g.get(x2, y + 2, z2) is None:
            g.set(x2, y + 2, z2, "birch_fence", force=True)
            g.set(x2, y + 3, z2, "birch_fence", force=True)


def barn(g, x0, z0, w=13, d=17, h=8):
    """The Alpha Moose's barn (its door to the south, z0 + d - 1): a stone footing, log posts, plank walls framed in
    stripped logs, shuttered windows, a steep gable roof of spruce stairs with snow on its ridge and icicles under its
    eaves, a moose skull and antlers over the wide door. Inside: the moose's stall (straw, a feed trough, a water
    trough), a tack wall, feed barrels and a hay loft up a ladder at the back. Returns the door's middle (x, z)."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    mid = x0 + w // 2
    door = range(mid - 2, mid + 3)              # 5 wide
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            edge = x in (x0, x1) or z in (z0, z1)
            g.set(x, 0, z, "packed_mud" if (x + 2 * z) % 4 else "coarse_dirt", force=True)
            for y in range(1, h + 1):
                post = (x in (x0, x1)) and (z - z0) % 4 == 0 or (z in (z0, z1)) and (x - x0) % 4 == 0
                if not edge:
                    g.set(x, y, z, AIR, force=True)
                    continue
                if z == z1 and x in door and y <= 5:
                    g.set(x, y, z, AIR, force=True)   # the door: 5 wide and 5 tall (the Alpha Moose is 4.6 tall)
                    continue
                if y == 1:
                    g.set(x, y, z, STONE, force=True)
                elif post or y == h:
                    g.set(x, y, z, "spruce_log[axis=y]" if post else "stripped_spruce_log[axis=x]" if z in (z0, z1)
                          else "stripped_spruce_log[axis=z]", force=True)
                elif y == 4 and not post and ((x in (x0, x1) and (z - z0) % 4 == 2) or (z == z0 and (x - x0) % 4 == 2)):
                    g.set(x, y, z, "spruce_trapdoor[facing={},half=top,open=true]".format(
                        "east" if x == x0 else "west" if x == x1 else "south"), force=True)
                else:
                    g.set(x, y, z, "spruce_planks", force=True)
    # the lintel over the door, and its frame
    for x in door:
        g.set(x, 6, z1, "stripped_spruce_log[axis=x]", force=True)
    for x in (mid - 3, mid + 3):
        for y in range(1, 7):
            g.set(x, y, z1, "spruce_log[axis=y]", force=True)
    # the roof: stairs rising from both long sides to a ridge along z, gable ends of planks
    k = 0
    while x0 - 1 + k <= x1 + 1 - k:
        y = h + 1 + k
        for z in range(z0 - 1, z1 + 2):
            if x0 - 1 + k == x1 + 1 - k:
                g.set(x0 - 1 + k, y, z, "snow_block", force=True)
            else:
                g.set(x0 - 1 + k, y, z, "spruce_stairs[facing=east,half=bottom]", force=True)
                g.set(x1 + 1 - k, y, z, "spruce_stairs[facing=west,half=bottom]", force=True)
        if 0 < k:
            for z in (z0, z1):
                for x in range(x0 - 1 + k + 1, x1 + 1 - k):
                    g.set(x, y, z, "spruce_planks", force=True)
        k += 1
    ridge_y = h + k
    for z in range(z0 - 1, z1 + 2):
        if g.get(mid, ridge_y, z) is None:
            g.set(mid, ridge_y, z, "snow[layers=3]")
    # snow on the roof's lower courses, icicles under the eaves
    for z in range(z0 - 1, z1 + 2):
        for x in (x0 - 1, x1 + 1):
            if z % 2 == 0:
                g.set(x, h, z, "dross:icicle", force=True)
    # a loft over the back third, up a ladder
    loft_y = 5
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z0 + 6):
            g.set(x, loft_y, z, "spruce_slab[type=top]", force=True)
    for x in range(x0 + 1, x1):
        if x != mid:
            g.set(x, loft_y + 1, z0 + 5, "spruce_fence", force=True)
    for y in range(1, loft_y + 1):
        g.set(mid, y, z0 + 5, "spruce_planks", force=True)        # the ladder's post
        g.set(mid, y, z0 + 6, "ladder[facing=south]", force=True)
    for x, z, ax in ((x0 + 1, z0 + 1, "x"), (x0 + 2, z0 + 1, "z"), (x0 + 1, z0 + 2, "y"), (x1 - 1, z0 + 1, "y"),
                     (x1 - 2, z0 + 1, "x"), (x1 - 1, z0 + 2, "z")):
        g.set(x, loft_y + 1, z, f"hay_block[axis={ax}]", force=True)
    g.set(x0 + 1, loft_y + 2, z0 + 1, "hay_block[axis=y]", force=True)
    # under the loft: feed barrels and the feed chest
    for x in (x0 + 1, x0 + 2):
        g.set(x, 1, z0 + 1, "barrel[facing=up,open=false]", force=True)
    g.set(x1 - 1, 1, z0 + 1, "chest[facing=south,type=single]", force=True)
    g.set(x1 - 2, 1, z0 + 1, "barrel[facing=south,open=false]", force=True)
    # the stall: straw floor, a feed trough (composters full of feed) and a water trough along the west wall, fenced
    # off from the walkway down the east side
    for x in range(x0 + 1, x1 - 3):
        for z in range(z0 + 7, z1):
            g.set(x, 0, z, STRAW, force=True)
    for z in range(z0 + 7, z0 + 10):
        g.set(x0 + 1, 1, z, "composter[level=7]", force=True)
    for z in range(z0 + 11, z0 + 14):
        g.set(x0 + 1, 1, z, "water_cauldron[level=3]", force=True)
    for z in range(z0 + 7, z1 - 1):
        g.set(x1 - 4, 1, z, "spruce_fence", force=True)
    g.set(x1 - 4, 1, z0 + 7, "spruce_fence_gate[facing=east,in_wall=false,open=false,powered=false]", force=True)
    # the tack wall (east): a saddle on a rack, spears, a lead post
    g.set(x1 - 1, 1, z0 + 9, "spruce_fence", force=True)
    g.set(x1 - 1, 2, z0 + 9, "brown_carpet", force=True)
    g.set(x1 - 1, 1, z0 + 11, "spruce_fence", force=True)
    g.set(x1 - 1, 2, z0 + 11, "red_carpet", force=True)
    for z in (z0 + 13, z0 + 14):
        # PLACEHOLDER spear: a lightning rod until the real spear model and item exist (the user, October 20, 2026)
        g.set(x1 - 1, 1, z, "lightning_rod[facing=up]", force=True)
    # beams across under the roof with lanterns hanging from them
    for z in (z0 + 8, z0 + 13):
        for x in range(x0 + 1, x1):
            g.set(x, h, z, "spruce_log[axis=x]", force=True)
        g.set(mid, h - 1, z, "lantern[hanging=true]", force=True)
    # the moose skull and antlers over the door, on the gable's face
    moose_head(Frame(g, mid, z1, "south"), 7)
    return mid, z1


def drape_furs(g, m, d, n=4, seed=7):
    """Hides thrown over the lodge's dome to dry: patches of grey, brown and white fur (carpets) lying on its snow,
    on its front and sides (not over the skylight, never on ice)."""
    import random
    rng = random.Random(seed)
    colours = ("gray_carpet", "brown_carpet", "white_carpet", "light_gray_carpet")
    placed = 0
    for _t in range(80):
        if placed >= n:
            break
        a = math.radians(rng.uniform(-110, 110))           # round the front (the door faces south)
        rr = rng.uniform(0.45, 0.75) * d.r
        cx, cz = d.cx + math.sin(a) * rr, d.cz + math.cos(a) * rr
        col = rng.choice(colours)
        w, l = rng.choice((2, 3)), rng.choice((3, 4))
        cells = []
        for i in range(-w // 2, w - w // 2):
            for j in range(-l // 2, l - l // 2):
                x, z = int(round(cx + i)), int(round(cz + j))
                top = _shell_top(m, g, x, z)
                if top is None:
                    cells = None
                    break
                below = g.get(x, top, z)
                if below is None or mc.base(below) != "snow_block":
                    cells = None
                    break
                cells.append((x, top + 1, z))
            if cells is None:
                break
        if not cells:
            continue
        for x, y, z in cells:
            g.set(x, y, z, col, force=True)
        placed += 1


def build_into(g, bx1, bz1, bx2, bz2, seed=1111):
    """Builds his lodge, armoury, yard, barn and corral into the village's grid in the box (bx1..bx2, bz1..bz2), its
    way in at the middle of the box's south edge."""
    m = Massing(g)
    ox, oz = bx1, bz1
    cxm = bx1 + W // 2

    def P(x, z):
        return ox + x, oz + z

    domes = []
    lx, lz = cxm, oz + 19
    hall = m.dome(lx, lz, 9.0, 9.5, style=LODGE_STYLE, door="south", tunnel=5, tunnel_half=2, tunnel_height=5,
                  windows=[(50, 2), (-50, 2), (100, 2), (-100, 2), (140, 3), (-140, 3), (180, 4)], name="lodge")
    ax, az = P(10, 14)
    arm = m.dome(ax, az, 5.0, style=ARMOURY_STYLE, door="east", tunnel=None,
                 windows=[(90, 1), (-90, 1), (180, 1)], name="armoury")
    m.passage(ax + 3, az, lx - 6, az)
    domes += [hall, arm]
    m.build()
    m.reserved = {}
    # passages stay clear inside both domes
    for d in (hall, arm):
        res = m.reserved.setdefault(id(d), set())
        for (x, y, z) in d.room:
            if abs(z - az) <= 1 and (x < lx - 5 if d is hall else x > ax + 1):
                res.add((x, z))
    t = hall.tunnel_shape
    res = m.reserved.setdefault(id(hall), set())
    for u in range(-2, 3):
        for v in range(4, 12):
            res.add(t.f.xz(u, v))

    # -- his lodge inside: his seat at the back facing the door, a rack of antlers over it, the fire, benches, furs
    r = Room(m, hall)
    f = r.f
    back = -6
    for u in (-1, 0, 1):
        r.put(u, back, "spruce_stairs[facing={back},half=bottom]")       # the seat's back is to the wall
    for u in (-2, 2):
        r.put(u, back, "spruce_slab[type=bottom]")                       # its arms
    for u in (-1, 0, 1):
        f.set(u, 2, back - 1, "stripped_spruce_wood[axis=y]")
        f.set(u, 1, back - 1, "stripped_spruce_wood[axis=y]")
        f.set(u, 3, back - 1, "birch_fence")
    f.set(0, 4, back - 1, "skeleton_skull[rotation={skull_front}]")
    for u in (-2, -1, 1, 2):
        f.set(u, 4, back - 1, "birch_fence")
    for u in (-3, -2, 2, 3):
        f.set(u, 5, back - 1, "birch_fence")
    for u in range(-2, 3):
        for vv in range(back + 1, back + 4):
            r.put(u, vv, "brown_carpet" if (u + vv) % 2 else "white_carpet")
    r.fire("campfire", u=0, v=0)
    for u, vv in ((-3, -2), (3, -2), (-3, 2), (3, 2), (-4, 0), (4, 0)):
        r.put(u, vv, "stripped_spruce_log[axis={fb}]")
    # his bed of furs (two beds side by side) on a bearskin
    for u in (-5, -6):
        r.bed(u, -2, "back", "brown")
    for u, vv in ((-5, 0), (-6, 0), (-5, -4), (-6, -4)):
        r.put(u, vv, "gray_carpet")
    # a table of meat: a slab with a joint on it, stools
    r.put(5, -1, "spruce_slab[type=top]")
    r.put(5, -2, "spruce_slab[type=top]")
    for (u, vv) in ((5, -1), (5, -2)):
        c = r.cell(u, vv)
        if c is not None:
            g.set(c[0], c[1] + 1, c[2], props.MEAT if vv == -1 else "barrel[facing=up,open=false]", force=True)
    # PLACEHOLDER spear: a lightning rod until the real spear model and item exist (the user, October 20, 2026)
    r.against_back(["chest[facing={in},type=single]", "lightning_rod[facing=up]", "lightning_rod[facing=up]",
                    "skeleton_skull[rotation={skull_in}]", "barrel[facing=up,open=false]", "lightning_rod[facing=up]",
                    "smoker[facing={in},lit=false]", "chest[facing={in},type=single]", "fletching_table",
                    "grindstone[face=floor,facing={in}]", "skeleton_skull[rotation={skull_in}]",
                    "barrel[facing=up,open=false]"])
    # trophies on the wall: skulls on posts and antlers over them, round the room
    for ang in (60, 120, 240, 300):
        cells = [c for c in r.wall_cells() if abs(c[0] - ang) < 12 and (c[1], c[2]) not in r.used]
        if not cells:
            continue
        _a, x, z, wall = cells[0]
        y = r.y0
        g.set(x, y, z, "stripped_spruce_log[axis=y]", force=True)
        g.set(x, y + 1, z, "skeleton_skull[rotation={}]".format(
            {"north": 0, "south": 8, "east": 4, "west": 12}[wall]), force=True)
        r.used.add((x, z))

    # -- the armoury: spears and bows, a weapon rack, a chest of arrows
    ra = Room(m, arm)
    # PLACEHOLDER spear: a lightning rod until the real spear model and item exist (the user, October 20, 2026)
    ra.against_back(["lightning_rod[facing=up]", "lightning_rod[facing=up]", "fletching_table",
                     "chest[facing={in},type=single]", "lightning_rod[facing=up]", "grindstone[face=floor,facing={in}]",
                     "barrel[facing=up,open=false]", "lightning_rod[facing=up]", "target"])
    ra.rug(mats=("brown_carpet", "gray_carpet"))
    ra.lamp("soul_lantern")

    # -- the entrance: the moose skull and antlers over the tunnel's mouth, his banners either side, skulls and antlers
    # on the dome round it, and an antler crown round the skylight
    mx, mz = t.f.xz(0, t.v1)
    hx, hz = t.f.xz(0, t.v1 - 1)
    moose_head(Frame(g, hx, hz, "south"), t.ybase + t.height)
    for side in (-4, 4):
        x, z = t.f.xz(side, t.v1 + 1)
        for y in range(1, 6):
            g.set(x, y, z, "spruce_fence", force=True)
        g.set(x, 6, z, "brown_banner[rotation=0]", force=True)
        g.block_nbt[(x, 6, z)] = SIGIL
    drape_furs(g, m, hall)

    # -- the barn and its corral, east of the lodge
    bx, bz = P(39, 18)
    door = barn(g, bx, bz, w=13, d=17, h=8)
    cx1, cz1, cx2, cz2 = bx - 1, bz + 18, bx + 14, oz + D - 2
    gate_x = door[0]
    for x, z in props.rect_cells(cx1, cz1, cx2, cz2):
        if z == cz1 and abs(x - gate_x) <= 2:
            continue            # open onto the barn's door
        post = ((x - cx1) % 3 == 0 and z in (cz1, cz2)) or ((z - cz1) % 3 == 0 and x in (cx1, cx2))
        if post:
            g.set(x, 1, z, "bone_block[axis=y]", force=True)
            g.set(x, 2, z, "skeleton_skull[rotation=8]" if z == cz2 and (x - cx1) % 6 == 0 else "bone_block[axis=y]",
                  force=True)
        else:
            g.set(x, 1, z, "spruce_fence", force=True)
    gx = cx1
    gz = (cz1 + cz2) // 2
    g.set(gx, 1, gz, "spruce_fence_gate[facing=west,in_wall=false,open=false,powered=false]", force=True)
    g.set(gx, 2, gz, AIR, force=True)
    for x in range(cx1 + 2, cx1 + 5):
        g.set(x, 1, cz2 - 1, "water_cauldron[level=3]", force=True)
    g.set(cx2 - 2, 1, cz1 + 2, "hay_block[axis=y]", force=True)
    g.set(cx2 - 2, 2, cz1 + 2, "hay_block[axis=x]", force=True)
    g.set(cx2 - 3, 1, cz1 + 2, "hay_block[axis=z]", force=True)
    for x in range(cx1 + 1, cx2):
        for z in range(cz1 + 1, cz2):
            if g.get(x, 0, z) is None and (x * 3 + z * 5) % 7 < 3:
                g.set(x, 0, z, "coarse_dirt")

    # -- the path from the way in up to his door
    way_in = (cxm, oz + D - 1)
    props.path(g, [way_in, (mx, mz + 1)], width=3)

    # -- his yard's decorations (each 1 in 2)
    dec = Decor(g)
    dec.place("bone arch", lambda s: props.bone_arch(Frame(s, cxm, oz + D - 6, "south"), width=5, height=6))
    for z in range(mz + 1, oz + D):
        for x in range(cxm - 2, cxm + 3):
            dec.keepout[x, z] = True
    for x in range(cx1 - 1, cx2 + 2):
        for z in range(bz - 1, cz2 + 2):
            if 0 <= x < g.W and 0 <= z < g.D:
                dec.keepout[x, z] = True
    yard = []
    # skull posts in pairs along the path
    for k, zz in enumerate(range(mz + 3, oz + D - 7, 4)):
        for side in (-3, 3):
            yard.append(("skull post", lambda s, zz=zz, side=side: props.skull_post(
                Frame(s, cxm + side, zz, "east" if side < 0 else "west"), height=2)))
    # the roasting fire with its spit, racks of meat, the butcher's block, bones, skulls, hides, a woodpile, sleds
    yard += [
        ("roasting fire", lambda s: props.fire_pit(Frame(s, ox + 12, mz + 8, "south"), r=1, benches=True, spit=True,
                                                   bench_r=3)),
        ("meat rack", lambda s: props.meat_rack(Frame(s, ox + 3, mz - 1, "east"), length=4)),
        ("meat rack", lambda s: props.meat_rack(Frame(s, ox + 3, mz + 6, "east"), length=4)),
        ("meat rack", lambda s: props.meat_rack(Frame(s, ox + 3, mz + 13, "east"), length=4)),
        ("butcher's block", lambda s: props.butcher_block(Frame(s, ox + 8, oz + D - 2, "north"))),
        ("bone pile", lambda s: props.bone_pile(Frame(s, cxm - 8, mz + 5, "south"), big=True)),
        ("bone pile", lambda s: props.bone_pile(Frame(s, cxm - 8, mz + 12, "south"), big=False)),
        ("skull pile", lambda s: props.skull_pile(Frame(s, ox + 8, mz, "south"))),
        ("antler post", lambda s: props.antler_post(Frame(s, mx - 9, mz + 1, "south"), height=4)),
        ("hide rack", lambda s: props.hide_rack(Frame(s, ox + 2, lz + 3, "east"), colours=("gray", "brown"))),
        ("hide frame", lambda s: props.hide_frame(Frame(s, ox + 3, oz + 5, "east"), colour="light_gray")),
        ("woodpile", lambda s: props.woodpile(Frame(s, ox + 19, oz + 3, "south"), length=3)),
        ("weapon rack", lambda s: props.weapon_rack(Frame(s, ox + 11, oz + 3, "south"), n=4)),
        ("totem", lambda s: props.totem(Frame(s, lx - 13, lz + 9, "south"), height=6)),
        ("sled", lambda s: props.sled(Frame(s, cxm + 9, mz + 15, "south"))),
        ("antler post", lambda s: props.antler_post(Frame(s, cxm - 5, oz + D - 3, "south"), height=3)),
        ("meat rack", lambda s: props.meat_rack(Frame(s, bx + 2, bz - 4, "south"), length=4)),
        ("bone pile", lambda s: props.bone_pile(Frame(s, bx + 8, bz - 4, "south"), big=True)),
    ]
    for name, draw in yard:
        dec.place(name, draw)
    return {"decor": dec.groups, "box": (bx1, bz1, bx2, bz2), "domes": domes, "way_in": way_in,
            "refused": dict(dec.refused), "massing": m, "log": list(dec.log)}


def _shell_top(m, g, x, z):
    if not g.inb(x, 0, z):
        return None
    col = [yi for yi in range(g.H) if m.shell[x, yi, z]]
    if not col:
        return None
    return max(col) - g.G


def build():
    """The lodge on its own (for its previews)."""
    from villagekit.village import Village
    v = Village(W + 6, D + 6, H=24, G=5, seed=1111)
    info = build_into(v.g, 3, 3, 3 + W - 1, 3 + D - 1)
    v.g.lodge_info = info
    return v.g, dict(INFO, igloos=2)
