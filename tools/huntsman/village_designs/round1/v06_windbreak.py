"""No. 6 Windbreak Crescent: a long curved wall of snow on the north side, drifts piled high against its back,
packed-ice buttresses and wind-torn banners along its top; seven igloos in its shelter, round a fire trench."""
import math

from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village

INFO = {"number": 6, "id": "windbreak_crescent", "name": "Windbreak Crescent",
        "mood": "seven igloos sheltering behind a great curved wall of snow"}


def build():
    v = Village(61, 49, H=18, seed=606)
    g = v.g
    cx, cz = 30, 40  # the crescent's middle (south of the village)
    R = 33.0
    style = Style()
    kinds = ["sleeping", "workshop", "sleeping", "trophy", "cook", "sleeping", "storage"]
    for k in range(7):
        a = math.radians(-60 + 20 * k)  # 0 = straight north of the middle
        x = int(round(cx + math.sin(a) * (R - 12)))
        z = int(round(cz - math.cos(a) * (R - 12)))
        r = 5.0 if kinds[k] == "trophy" else 4.5
        v.igloo(x, z, r, toward=(cx, cz), kind=kinds[k], style=style,
                **({"beds": 3} if kinds[k] == "sleeping" else {}))
    v.build_igloos()

    # the wall: two blocks thick, 4 high, from west round the north to east
    wall = []
    for rr in (R, R + 1):
        wall += props.ring_cells(cx, cz, rr, 115, 245)
    props.snow_wall(g, wall, height=4, cap=None)
    for x, z in props.ring_cells(cx, cz, R, 115, 245):
        g.set(x, 5, z, "snow[layers=4]")
    # buttresses of packed ice inside it, every 12 degrees
    for k in range(11):
        a = math.radians(-60 + 12 * k)
        for i, h in ((1, 4), (2, 3), (3, 2)):
            x = int(round(cx + math.sin(a) * (R - i)))
            z = int(round(cz - math.cos(a) * (R - i)))
            for y in range(1, h + 1):
                g.set(x, y, z, "packed_ice")
    # drifts piled against its back: snow layers deepest by the wall
    for x in range(g.W):
        for z in range(g.D):
            d = math.hypot(x - cx, z - cz)
            if R + 1.5 < d < R + 9 and z < cz - 4:
                if g.get(x, 1, z) is None:
                    depth = 8 - int((d - R - 1.5) * 1.1)
                    noise = g.noise2(5.0, salt=66)[x, z]
                    depth = max(0, min(8, depth + int((noise - 0.5) * 3)))
                    if depth >= 8:
                        g.set(x, 1, z, "snow_block")
                        g.set(x, 2, z, "snow[layers=3]")
                    elif depth > 0:
                        g.set(x, 1, z, f"snow[layers={depth}]")
    # banners along the top
    for k in range(5):
        a = math.radians(-48 + 24 * k)
        x = int(round(cx + math.sin(a) * (R + 0.5)))
        z = int(round(cz - math.cos(a) * (R + 0.5)))
        for y in range(5, 8):
            g.set(x, y, z, "spruce_fence")
        g.set(x, 8, z, ("light_blue" if k % 2 else "white") + "_banner[rotation=0]")
    # the fire trench and the yard
    props.long_fire(Frame(g, cx, 26, "east"), length=9)
    props.plaza(g, cx, 26, 8)
    for x in (cx - 6, cx + 6):
        g.set(x, 1, 24, "stripped_spruce_log[axis=x]")
        g.set(x, 1, 28, "stripped_spruce_log[axis=x]")
    props.path(g, [(cx, 32), (cx, 48)], width=3)
    props.drying_rack(Frame(g, 8, 38, "south"), length=4)
    props.drying_rack(Frame(g, 49, 38, "south"), length=4)
    props.sled(Frame(g, 40, 32, "south"))
    props.woodpile(Frame(g, 18, 33, "south"))
    props.inuksuk(Frame(g, cx - 4, 45, "south"), tall=False)
    props.inuksuk(Frame(g, cx + 4, 45, "south"), tall=False)
    return g, dict(INFO, igloos=len(v.igloos))
