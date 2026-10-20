"""No. 7 Antler Camp: a trophy hunters' camp in two clusters of three igloos, every door under a gate of antlers;
between them the trophy ground: a great rack of antlers and skulls, hides stretched on frames, skulls on posts
along the paths."""
from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village

INFO = {"number": 7, "id": "antler_camp", "name": "Antler Camp",
        "mood": "two clusters of igloos round a trophy ground of antlers, skulls and hides"}


def trophy_wall(g, x, z, width=11):
    """A long frame of logs hung with antler racks and skulls (facing south)."""
    f = Frame(g, x, z, "south")
    half = width // 2
    for u in range(-half, half + 1):
        f.set(u, 1, 0, "spruce_planks")
        f.set(u, 5, 0, "spruce_slab[type=bottom]")
    for u in (-half, -half // 2, 0, half // 2, half):
        for y in range(1, 6):
            f.set(u, y, 0, "spruce_log[axis=y]")
    for u in range(-half + 1, half):
        for y in (2, 3, 4):
            if f.get(u, y, 0) is None:
                f.set(u, y, 0, "stripped_spruce_wood[axis=y]")
    for u in (-half + 2, half - 2, -1, 1):
        f.set(u, 3, 1, "skeleton_wall_skull[facing={front}]")
    # antler racks on top: birch fence tines
    for cu in (-half // 2, half // 2, 0):
        for du in (-2, -1, 1, 2):
            f.set(cu + du, 6, 0, "birch_fence")
        for du in (-2, 2):
            f.set(cu + du, 7, 0, "birch_fence")
        f.set(cu, 6, 0, "skeleton_skull[rotation={rot_front}]")


def build():
    v = Village(57, 55, H=18, seed=707)
    g = v.g
    style = Style(bands=(2,), band="snow_block")
    west = [(10, 14, "sleeping"), (8, 31, "cook"), (16, 44, "sleeping")]
    east = [(46, 14, "workshop"), (48, 31, "trophy"), (40, 44, "sleeping")]
    for x, z, kind in west:
        v.igloo(x, z, 4.5, toward=(20, 28), kind=kind, style=style, **({"beds": 3} if kind == "sleeping" else {}))
    for x, z, kind in east:
        v.igloo(x, z, 4.5 if kind != "trophy" else 5.0, toward=(36, 28), kind=kind, style=style,
                **({"beds": 3} if kind == "sleeping" else {}))
    v.build_igloos()

    for d, _, _ in v.igloos:
        t = d.tunnel_shape
        x, z = t.f.xz(0, t.v1 + 2)
        props.antler_gate(Frame(g, x, z, t.f.front), width=1, height=3)
        props.path(g, [v.doorstep(d, 0), (28, 28)], width=1)
    props.plaza(g, 28, 28, 7)
    props.fire_pit(Frame(g, 28, 28, "south"), r=1, benches=True, bench_r=3, spit=True)
    trophy_wall(g, 28, 8)
    for i, (x, z, c) in enumerate(((22, 18, "brown"), (34, 18, "light_gray"), (22, 40, "white"), (34, 40, "brown"))):
        props.hide_frame(Frame(g, x, z, "south" if z > 28 else "north"), colour=c)
    props.path(g, [(28, 35), (28, 54)], width=3)
    for z in (40, 44, 48, 52):
        props.skull_post(Frame(g, 25, z, "east"), height=2)
        props.skull_post(Frame(g, 31, z, "west"), height=2)
    props.antler_post(Frame(g, 28, 14, "south"), height=3)
    props.drying_rack(Frame(g, 2, 22, "east"), length=4)
    props.woodpile(Frame(g, 52, 22, "west"))
    props.sled(Frame(g, 20, 50, "east"))
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z, 1), salt=7)
    return g, dict(INFO, igloos=len(v.igloos))
