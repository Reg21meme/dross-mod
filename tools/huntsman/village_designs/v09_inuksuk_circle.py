"""No. 9 Snow Angel Circle: seven igloos round a ring of eight inuksuks (stone figures) all facing a statue of the
Snow Angel, the Ice Necromancer the hunters serve: a hooded robe of snow, wings and a halo of clear ice."""
import math

from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village
from voxel import Mix

INFO = {"number": 9, "id": "snow_angel_circle", "name": "Snow Angel Circle",
        "mood": "igloos round a ring of stone figures and a statue of the Snow Angel"}


def angel(g, cx, cz):
    """The statue (facing south): a robe of snow widening to the ground, a hood, wings of clear ice spread to either
    side, a halo of ice over the hood, on a round plinth of packed ice."""
    f = Frame(g, cx, cz, "south")
    for u in range(-3, 4):
        for v in range(-3, 4):
            if math.hypot(u, v) <= 3.3:
                f.set(u, 1, v, "packed_ice")
            if math.hypot(u, v) <= 2.2:
                f.set(u, 2, v, "snow_block")
    for y in range(3, 9):
        rr = 1.9 - (y - 3) * 0.28
        for u in range(-2, 3):
            for v in range(-2, 3):
                if math.hypot(u, v * 1.2) <= rr:
                    f.set(u, y, v, "snow_block")
    # the hood and its dark opening
    f.box(-1, 9, -1, 1, 10, 1, "white_wool")
    f.set(0, 11, 0, "white_wool")
    f.set(0, 9, 1, "black_wool")
    f.set(0, 10, 1, "black_wool")
    # wings: feathers of clear ice, rising and spreading behind its shoulders
    for side in (-1, 1):
        for i in range(6):
            u = side * (2 + i)
            top = 9 + (2 if i < 3 else 3 - (i - 3))
            bottom = 6 + i // 2
            for y in range(bottom, top + 1):
                f.set(u, y, -1, "ice")
        f.set(side * 2, 7, -1, "packed_ice")
    # the halo
    for u, y in ((-1, 12), (0, 12), (1, 12), (-2, 13), (2, 13), (-1, 14), (0, 14), (1, 14)):
        f.set(u, y, -1, "ice")
    for u, y in ((-2, 12), (2, 12), (-2, 14), (2, 14)):
        f.set(u, y, -1, "ice")


def build():
    v = Village(57, 57, H=20, seed=909)
    g = v.g
    c = 28
    style = Style(bands=(3,), band="packed_ice")
    kinds = ["sleeping", "trophy", "sleeping", "cook", "workshop", "sleeping", "storage"]
    for k in range(7):
        a = math.radians(45 * (k + 1))
        x = int(round(c - math.sin(a) * 20))
        z = int(round(c + math.cos(a) * 18))
        v.igloo(x, z, 4.5 if kinds[k] != "trophy" else 5.0, kind=kinds[k], style=style,
                **({"beds": 3} if kinds[k] == "sleeping" else {}))
    v.build_igloos()

    props.plaza(g, c, c, 11, mat=Mix(("snow_block", 5), ("calcite", 2), ("gravel", 1), patch=0.5))
    angel(g, c, c - 1)
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        x = int(round(c - math.sin(a) * 8))
        z = int(round(c + math.cos(a) * 8))
        front = {0: "north", 1: "north", 2: "east", 3: "east", 4: "south", 5: "south", 6: "west", 7: "west"}[k]
        props.inuksuk(Frame(g, x, z, front), tall=k % 2 == 0)
    for d, _, _ in v.igloos:
        props.path(g, [v.doorstep(d, 0), (c, c)], width=1)
    props.path(g, [(c, c + 11), (c, 56)], width=3)
    props.frostblooms(g, 20, area=lambda x, z: 4 < math.hypot(x - c, z - c) < 10.5)
    props.drying_rack(Frame(g, 3, 28, "east"), length=4)
    props.woodpile(Frame(g, 54, 34, "west"))
    props.sled(Frame(g, 40, 50, "west"))
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z) and math.hypot(x - c, z - c) > 11.5, salt=9)
    return g, dict(INFO, igloos=len(v.igloos))
