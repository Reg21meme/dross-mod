"""No. 3 Stockade Camp: six igloos inside a palisade of sharpened spruce logs, a gatehouse with two lookout towers,
and a yard for the hunt: an archery range, spear racks, a watch fire, hides drying on the walls."""
from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village
from voxel import Mix

INFO = {"number": 3, "id": "stockade_camp", "name": "Stockade Camp",
        "mood": "six igloos inside a log palisade with a gatehouse and an archery yard"}

BOARDWALK = "spruce_slab[type=bottom]"


def tower(g, x, z, height=7):
    """A 3 x 3 log tower with a slab floor on top, a fence rail and a lantern."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            corner = abs(dx) == 1 and abs(dz) == 1
            for y in range(1, height + 1):
                if corner:
                    g.set(x + dx, y, z + dz, "spruce_log[axis=y]")
            g.set(x + dx, height, z + dz, "spruce_planks")
            if not (dx == 0 and dz == 0):
                g.set(x + dx, height + 1, z + dz, "spruce_fence")
    g.set(x, height + 1, z, "soul_lantern[hanging=false]")
    for dx in (-1, 1):
        g.set(x + dx, height + 2, z - 1 if dx < 0 else z + 1, "spruce_fence")
    g.set(x - 1, height + 3, z - 1, "spruce_slab[type=bottom]")
    g.set(x + 1, height + 3, z + 1, "spruce_slab[type=bottom]")


def build():
    v = Village(51, 51, H=20, seed=303)
    g = v.g
    style = Style()
    plan = [(12, 13, "south", "sleeping"), (25, 12, "south", "trophy"), (38, 13, "south", "sleeping"),
            (10, 30, "east", "workshop"), (40, 30, "west", "cook"), (39, 40, "north", "storage")]
    for x, z, door, kind in plan:
        r = 5.0 if kind == "trophy" else 4.5
        v.igloo(x, z, r, door=door, kind=kind, style=style, **({"beds": 3} if kind == "sleeping" else {}))
    v.build_igloos()

    # the palisade, the gate at the south, two towers either side of it
    cells = props.rect_cells(3, 3, 47, 47)
    gate = {(x, 47) for x in range(23, 28)}
    props.palisade(g, cells, height=5, gate=gate)
    tower(g, 21, 47)
    tower(g, 29, 47)
    for x in range(22, 29):
        g.set(x, 6, 47, "spruce_planks")
        g.set(x, 7, 47, "spruce_fence")
    props.banner_pole(Frame(g, 21, 45, "south"), "brown", height=4)
    props.banner_pole(Frame(g, 29, 45, "south"), "brown", height=4)
    # boardwalks: from the gate through the yard to every door
    for z in range(24, 48):
        for x in (24, 25, 26):
            g.set(x, 1, z, BOARDWALK) if g.get(x, 1, z) is None else None
    for d, _, _ in v.igloos:
        props.path(g, [v.doorstep(d, 0), (25, 25)], width=1, mat=Mix(("gravel", 1), ("coarse_dirt", 1), patch=0.0))
    for x in range(14, 37):
        g.set(x, 1, 24, BOARDWALK) if g.get(x, 1, 24) is None else None
    # the yard: a watch fire, an archery range, spear racks
    props.fire_pit(Frame(g, 25, 31, "south"), r=1, benches=True, spit=False, pot=True, bench_r=3)
    for x in (16, 19, 22):
        g.set(x, 1, 43, "hay_block[axis=y]")
        g.set(x, 2, 43, "target")
    for x in range(14, 24):
        g.set(x, 1, 36, "spruce_fence")
    props.weapon_rack(Frame(g, 30, 21, "south"), n=3)
    props.weapon_rack(Frame(g, 17, 21, "south"), n=3)
    # hides and racks along the walls, woodpiles and sleds by the gate
    props.hide_frame(Frame(g, 6, 41, "east"), colour="brown")
    props.hide_frame(Frame(g, 6, 20, "east"), colour="white")
    props.drying_rack(Frame(g, 44, 18, "west"), length=4)
    props.woodpile(Frame(g, 31, 44, "north"), length=4)
    props.sled(Frame(g, 15, 44, "north"))
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z) and (x < 4 or x > 46 or z < 4 or z > 46 or
                                                                   (x in (4, 46) or z in (4, 46))), salt=3)
    return g, dict(INFO, igloos=len(v.igloos))
