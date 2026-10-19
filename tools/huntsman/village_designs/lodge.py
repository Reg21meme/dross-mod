"""The Huntsman's lodge and stable (they join the village he lives in): a great snow lodge with a wide entrance
under a moose skull and its palm antlers, his seat of furs and antlers at the back, a fire, his spears and trophies;
an armoury dome joined to it; beside them a tall timber stable for the Alpha Moose (hay, water troughs, a saddle
rack) opening onto a fenced corral; his banners (a white skull on brown) at the door."""
import math

from villagekit import props
from villagekit.igloo import Style
from villagekit.interiors import Room
from villagekit.local import Frame
from villagekit.village import Village
from villagekit.writer import banner

INFO = {"number": 0, "id": "huntsman_lodge", "name": "The Huntsman's Lodge",
        "mood": "his great lodge, an armoury and a stable for the Alpha Moose"}

SIGIL = banner(("sku", "white"), ("bo", "white"), ("bts", "white"))


def moose_head(f, y):
    """A moose skull over a doorway (facing f's front): bone muzzle and brow, dark eyes, broad palm antlers spreading
    sideways and up, their tines of birch fence."""
    bone = "bone_block[axis=y]"
    f.box(-1, y, 1, 1, y + 2, 1, bone)
    f.box(-1, y - 1, 2, 1, y, 2, bone)  # the long muzzle
    f.set(0, y - 2, 2, bone)
    for u in (-1, 1):
        f.set(u, y + 1, 2, "black_concrete")
    for side in (-1, 1):
        # the beam out to the side, then the palm rising
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


def stable(g, x0, z0, w=11, d=15, h=8):
    """A timber stable (open to the south): log posts, plank walls on three sides, a gable roof of spruce stairs with a
    snowy ridge, hay, troughs and a saddle rack inside."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            g.set(x, 0, z, "packed_mud" if (x + z) % 3 else "coarse_dirt")
            for y in range(1, h + 1):
                post = (x in (x0, x1)) and (z - z0) % 4 == 0 or (z == z0 and (x - x0) % 5 == 0)
                wall = x in (x0, x1) or z == z0
                if post:
                    g.set(x, y, z, "spruce_log[axis=y]")
                elif wall:
                    g.set(x, y, z, "spruce_planks" if y < h - 1 else "stripped_spruce_wood[axis=y]")
                else:
                    g.set(x, y, z, "air")
    # a beam over the open front
    for x in range(x0, x1 + 1):
        g.set(x, h, z1, "spruce_log[axis=x]")
    for z in range(z0, z1 + 1):
        for y in range(h, h + 1):
            pass
    # the roof: stairs climbing from both long sides to a ridge along z
    k = 0
    while x0 - 1 + k <= x1 + 1 - k:
        y = h + 1 + k
        for z in range(z0 - 1, z1 + 2):
            if x0 - 1 + k == x1 + 1 - k:
                g.set(x0 - 1 + k, y, z, "snow_block")
            else:
                g.set(x0 - 1 + k, y, z, "spruce_stairs[facing=east,half=bottom]")
                g.set(x1 + 1 - k, y, z, "spruce_stairs[facing=west,half=bottom]")
                if k > 0:
                    for x in range(x0 + k, x1 + 1 - k):
                        if g.get(x, y - 1, z) is None or True:
                            pass
        k += 1
    # gable ends: planks under the stairs at the back, open at the front
    for kk in range(0, k):
        for x in range(x0 + kk, x1 + 1 - kk):
            g.set(x, h + 1 + kk, z0, "spruce_planks")
    # inside: hay, troughs, the saddle rack
    for x, z in ((x0 + 1, z0 + 1), (x0 + 2, z0 + 1), (x0 + 1, z0 + 2), (x1 - 1, z0 + 1)):
        g.set(x, 1, z, "hay_block[axis=y]")
    g.set(x0 + 1, 2, z0 + 1, "hay_block[axis=x]")
    for x in range(x0 + 4, x0 + 7):
        g.set(x, 1, z0 + 1, "water_cauldron[level=3]")
    g.set(x1 - 1, 1, z0 + 4, "spruce_fence")
    g.set(x1 - 1, 2, z0 + 4, "brown_carpet")
    g.set(x1 - 1, 1, z0 + 6, "chest[facing=west,type=single]")
    g.set(x1 - 1, 1, z0 + 7, "barrel[facing=up,open=false]")
    g.set(x0 + 1, 1, z0 + 8, "composter[level=7]")
    for z in (z0 + 4, z0 + 9):
        for x in range(x0 + 1, x1):
            g.set(x, h, z, "spruce_log[axis=x]")
        g.set(x0 + (w // 2), h - 1, z, "lantern[hanging=true]")
    return x1, z1


def build():
    v = Village(53, 49, H=24, seed=1111)
    g = v.g
    lodge = Style(bands=(3, 6), band="packed_ice", cap="blue_ice", rim_courses=2)
    small = Style(bands=(3,), band="packed_ice")
    hall = v.m.dome(18, 20, 9.0, 9.5, style=lodge, door="south", tunnel=5, tunnel_half=2, tunnel_height=5,
                    windows=[(50, 2), (-50, 2), (100, 2), (-100, 2), (140, 3), (-140, 3), (180, 4)])
    v.igloos.append((hall, None, {}))
    arm = v.igloo(37, 9, 5.0, door="west", kind="workshop", style=small, tunnel=None,
                  windows=[(90, 1), (-90, 1), (180, 1)])
    v.passage([(32, 9), (26, 9), (26, 13)])
    v.build_igloos()

    # inside the hall: his seat at the back, the fire, benches, trophies, spears, chests
    r = Room(v.m, hall)
    f = r.f
    back = -6
    r.put(0, back, "spruce_stairs[facing={front},half=bottom]")
    for u in (-1, 1):
        r.put(u, back, "spruce_stairs[facing={front},half=bottom]")
    for u in (-1, 0, 1):
        f.set(u, 2, back - 1, "stripped_spruce_wood[axis=y]")
        f.set(u, 1, back - 1, "stripped_spruce_wood[axis=y]")
        f.set(u, 3, back - 1, "birch_fence")
    f.set(0, 4, back - 1, "skeleton_skull[rotation={rot_front}]")
    for u in (-2, -1, 1, 2):
        f.set(u, 4, back - 1, "birch_fence")
    for u in (-2, 2):
        f.set(u, 5, back - 1, "birch_fence")
    for u in range(-2, 3):
        for vv in range(back + 1, back + 4):
            r.put(u, vv, "brown_carpet" if (u + vv) % 2 else "white_carpet")
    r.fire("campfire", u=0, v=0)
    for u, vv in ((-3, -2), (3, -2), (-3, 2), (3, 2)):
        r.put(u, vv, "stripped_spruce_log[axis={fb}]")
    r.against_back(["chest[facing={in},type=single]", "lightning_rod[facing=up]", "lightning_rod[facing=up]",
                    "skeleton_skull[rotation={rot_in}]", "barrel[facing=up,open=false]", "lightning_rod[facing=up]",
                    "smoker[facing={in},lit=false]", "chest[facing={in},type=single]", "fletching_table",
                    "grindstone[face=floor,facing={in}]"])
    r.bed(-5, -3, "back", "brown") or r.bedroll(-5, -3, "back")

    # the entrance: the moose skull and antlers over the tunnel's mouth, his banners either side
    t = hall.tunnel_shape
    mx, mz = t.f.xz(0, t.v1)
    hx, hz = t.f.xz(0, t.v1 - 1)
    moose_head(Frame(g, hx, hz, "south"), t.ybase + t.height)
    for side in (-4, 4):
        x, z = t.f.xz(side, t.v1 + 1)
        for y in range(1, 6):
            g.set(x, y, z, "spruce_fence")
        g.set(x, 6, z, "brown_banner[rotation=0]")
        g.block_nbt[(x, 6, z)] = SIGIL
    props.path(g, [t.f.xz(0, t.v1 + 1), (18, 48)], width=3)

    # the stable and its corral
    sx1, sz1 = stable(g, 36, 22, w=11, d=15, h=8)
    for x, z in props.rect_cells(33, 38, 49, 47):
        if not (40 <= x <= 42 and z == 38):
            g.set(x, 1, z, "spruce_fence")
    g.set(41, 1, 47, "spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
    for x in (40, 41, 42):
        g.set(x, 1, 38, "air")
    g.set(36, 1, 44, "hay_block[axis=y]")
    g.set(37, 1, 45, "hay_block[axis=z]")
    # trophies and racks round the lodge
    props.hide_frame(Frame(g, 4, 34, "east"), colour="light_gray")
    props.drying_rack(Frame(g, 2, 8, "east"), length=4)
    props.weapon_rack(Frame(g, 28, 32, "south"), n=4)
    props.antler_post(Frame(g, 26, 40, "south"), height=3)
    props.antler_post(Frame(g, 10, 40, "south"), height=3)
    props.woodpile(Frame(g, 30, 22, "north"), length=3)
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z, 1) and not (33 <= x <= 49 and 21 <= z <= 47), salt=11)
    return g, dict(INFO, igloos=2)
