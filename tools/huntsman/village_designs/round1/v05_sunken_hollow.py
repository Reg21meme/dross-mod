"""No. 5 Sunken Hollow: a round hollow dug three blocks into the snow, out of the wind; six igloos sunk half into its
rim with their doors on the hollow's floor, a sheltered fire in the middle, steps of snow down into it from the south."""
import math

from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village

INFO = {"number": 5, "id": "sunken_hollow", "name": "Sunken Hollow",
        "mood": "six igloos sunk into the rim of a hollow dug out of the snow"}

FLOOR_Y = -2
HOLLOW_R = 11.5


def build():
    v = Village(47, 47, H=18, G=5, seed=505)
    g = v.g
    c = 23
    m = v.m
    # the hollow: a floor 3 below the ground (y -2), a ledge at -1 round it
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot(x - c, z - c)
            if d <= HOLLOW_R:
                for y in range(FLOOR_Y, 1):
                    m.dig(x, y, z)
                g.set(x, FLOOR_Y - 1, z, "snow_block", force=True)
            elif d <= HOLLOW_R + 1.6:
                for y in range(FLOOR_Y + 1, 1):
                    m.dig(x, y, z)
                g.set(x, FLOOR_Y, z, "snow_block", force=True)
    style = Style(rim_courses=0, bands=(3,), band="packed_ice")
    kinds = ["sleeping", "cook", "sleeping", "trophy", "workshop", "sleeping"]
    for k in range(6):
        a = math.radians(30 + 60 * k + 30)  # the way in (south) between two of them
        x = int(round(c - math.sin(a) * (HOLLOW_R + 2.5)))
        z = int(round(c + math.cos(a) * (HOLLOW_R + 2.5)))
        d = v.m.dome(x, z, 4.5, 5.5, base=float(FLOOR_Y), style=style, door=_towards(x, z, c, c), tunnel=0,
                     windows=[(90, 1), (-90, 1)])
        v.igloos.append((d, kinds[k], {"beds": 3} if kinds[k] == "sleeping" else {}))
    v.build_igloos()

    # steps down from the south
    for x in range(c - 1, c + 2):
        for z in range(c + 11, c + 15):
            for y in range(FLOOR_Y, 1):
                g.set(x, y, z, "air", force=True)
        g.set(x, FLOOR_Y - 1, c + 11, "snow_block", force=True)
        g.set(x, FLOOR_Y, c + 12, "spruce_stairs[facing=south,half=bottom]", force=True)
        g.set(x, FLOOR_Y - 1, c + 12, "snow_block", force=True)
        g.set(x, FLOOR_Y + 1, c + 13, "spruce_stairs[facing=south,half=bottom]", force=True)
        g.set(x, FLOOR_Y, c + 13, "snow_block", force=True)
        g.set(x, 0, c + 14, "spruce_stairs[facing=south,half=bottom]", force=True)
        g.set(x, FLOOR_Y + 1, c + 14, "snow_block", force=True)
        g.set(x, FLOOR_Y, c + 14, "snow_block", force=True)
    # the fire on the hollow's floor
    f = Frame(g, c, c, "south")
    f.set(0, FLOOR_Y, 0, "campfire[facing=north,lit=true,signal_fire=false]")
    for u, w in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (1, 1), (-1, 1), (1, -1)):
        f.set(u, FLOOR_Y, w, "cobblestone_slab[type=bottom]")
    for u, w, ax in ((-4, 0, "z"), (4, 0, "z"), (0, -4, "x"), (-3, 3, "z"), (3, 3, "z")):
        f.set(u, FLOOR_Y, w, f"stripped_spruce_log[axis={ax}]")
    for k in range(8):
        a = math.radians(22.5 + 45 * k)
        x, z = int(round(c + math.sin(a) * 7)), int(round(c + math.cos(a) * 7))
        g.set(x, FLOOR_Y, z, "white_carpet" if k % 2 else "light_gray_carpet")
    # on the rim: racks, a sled, a banner, inuksuks either side of the steps
    props.drying_rack(Frame(g, 3, 23, "east"), length=4)
    props.sled(Frame(g, 39, 5, "south"))
    props.inuksuk(Frame(g, c - 4, 44, "south"), tall=False)
    props.inuksuk(Frame(g, c + 4, 44, "south"), tall=False)
    props.banner_pole(Frame(g, c, 3, "south"), "white", height=5)
    props.drifts(g, 0, area=lambda x, z: math.hypot(x - c, z - c) > HOLLOW_R + 2 and not v.footprint(x, z), salt=5)
    return g, dict(INFO, igloos=6)


def _towards(x, z, tx, tz):
    dx, dz = tx - x, tz - z
    if abs(dx) > abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"
