"""No. 1 Hearthfire Ring: seven igloos in a ring, every door facing a great fire in the middle (benches, a roasting
spit, a stew pot), drying racks, sleds and woodpiles between them, and a path in from the south past two inuksuks."""
import math

from villagekit import props
from villagekit.igloo import Style
from villagekit.village import Village

INFO = {"number": 1, "id": "hearthfire_ring", "name": "Hearthfire Ring",
        "mood": "seven igloos round a great fire, every door facing it"}


def build():
    v = Village(55, 55, seed=101)
    g = v.g
    c = v.cx
    ring = 17.5
    kinds = ["sleeping", "cook", "sleeping", "trophy", "sleeping", "workshop", "storage"]
    sizes = [4.5, 4.5, 5.0, 6.0, 5.0, 4.5, 4.0]
    style = Style()
    big = Style(bands=(3,))
    domes = []
    for k in range(7):
        a = math.radians(45 * (k + 1))  # 0 = south (the path in), clockwise seen from above
        x = int(round(c - math.sin(a) * ring))
        z = int(round(c + math.cos(a) * ring))
        r = sizes[k]
        domes.append(v.igloo(x, z, r, kind=kinds[k], style=big if r >= 6 else style,
                             **({"beds": 3} if kinds[k] == "sleeping" else {})))
    v.build_igloos()

    # the plaza and the great fire
    props.plaza(g, c, c, 9)
    props.fire_pit(props.frame(g, c, c, "south"), r=1, spit=True, pot=False, bench_r=4)
    props.path(g, [(c, c + 9), (c, 54)], width=3)
    for d in domes:
        props.path(g, [v.doorstep(d, 0), (c, c)], width=1)
    # inuksuks either side of the way in
    props.inuksuk(props.frame(g, c - 4, 50, "south"))
    props.inuksuk(props.frame(g, c + 4, 50, "south"))
    # between the igloos: racks, sleds, woodpiles, hides
    between = [props.drying_rack, props.sled, props.woodpile, props.hide_frame, props.drying_rack, props.sled]
    for k in range(6):
        a = math.radians(45 * (k + 1) + 22.5)
        x = int(round(c - math.sin(a) * (ring + 1)))
        z = int(round(c + math.cos(a) * (ring + 1)))
        front = "south" if abs(math.cos(a)) > abs(math.sin(a)) else "east"
        fn = between[k]
        if fn is props.drying_rack:
            fn(props.frame(g, x - 1, z, front))
        elif fn is props.hide_frame:
            fn(props.frame(g, x, z, front), colour="brown")
        else:
            fn(props.frame(g, x, z, front))
    props.banner_pole(props.frame(g, c - 3, c + 7, "south"), "light_blue")
    props.banner_pole(props.frame(g, c + 3, c + 7, "south"), "white")
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z) and math.hypot(x - c, z - c) > 9.5, salt=1)
    return g, dict(INFO, igloos=len(domes))
