"""No. 2 Whalebone Way: one long street under great arches of bone, three igloos either side with their doors on it,
lanterns between the arches, and at its far end the skull of a huge beast with a horn of ice, on a mound of snow."""
from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village
from voxel import Mix

INFO = {"number": 2, "id": "whalebone_way", "name": "Whalebone Way",
        "mood": "a street under great arches of bone, a beast's skull at its end"}

STREET = Mix(("snow_block", 4), ("gravel", 2), ("calcite", 2), ("bone_block[axis=y]", 1), patch=0.4)


def skull(g, x, z):
    """A great skull (bone blocks) on a snow mound, its jaw on the ground, a curved horn of ice off its snout,
    looking down the street (south)."""
    f = Frame(g, x, z, "south")
    for u in range(-5, 6):
        for v in range(-5, 5):
            d = (u / 5.5) ** 2 + (v / 5.0) ** 2
            if d <= 1.0:
                f.set(u, 1, v, "snow_block")
            if d <= 0.45:
                f.set(u, 2, v, "snow_block")
    bone = "bone_block[axis=y]"
    # cranium 5 wide, 4 tall, 5 deep, snout 3 wide reaching forward
    f.box(-2, 3, -3, 2, 6, 0, bone)
    f.box(-1, 3, 1, 1, 5, 3, bone)
    f.box(-1, 2, 1, 1, 2, 4, bone)  # the lower jaw
    f.box(-1, 4, 1, 1, 4, 2, "air")
    for u in (-1, 1):
        f.set(u, 5, 0, "black_concrete")  # the eye sockets
        f.set(u, 5, 1, "air")
    f.set(0, 6, 1, bone)
    # the horn: off the top of the snout, curving up and forward
    props.curve(f, [(0, 6, 3), (0, 8, 4), (0, 9, 4)], "packed_ice")
    props.curve(f, [(0, 9, 4), (0, 11, 5), (0, 12, 6)], "ice")
    # tusks from the jaw
    for side in (-2, 2):
        props.curve(f, [(side, 3, 2), (side, 3, 3), (side, 5, 4)], bone)


def build():
    v = Village(41, 63, H=20, seed=202)
    g = v.g
    sx = 20
    left, right = 9, 31
    plan = [(left, 14, "east", "sleeping"), (right, 22, "west", "cook"), (left, 31, "east", "workshop"),
            (right, 39, "west", "sleeping"), (left, 48, "east", "trophy"), (right, 54, "west", "sleeping")]
    style = Style(bands=(2,), band="snow_block", rim_courses=1)
    for x, z, door, kind in plan:
        v.igloo(x, z, 4.5 if kind != "trophy" else 5.0, door=door, kind=kind, style=style, tunnel=2,
                **({"beds": 3} if kind == "sleeping" else {}))
    v.build_igloos()

    # the street, from the way in (south) to the skull
    for z in range(10, 63):
        for x in range(sx - 2, sx + 3):
            g.set(x, 0, z, STREET)
    for d, _, _ in v.igloos:
        props.path(g, [v.doorstep(d, 0), (sx, d.cz)], width=1, mat=STREET)
    for z in (17, 27, 37, 47, 57):
        props.bone_arch(Frame(g, sx, z, "south"), width=7, height=8)
    for z in (22, 32, 42, 52):
        props.lantern_post(Frame(g, sx - 3, z, "south"), height=3, kind="soul_lantern")
        props.lantern_post(Frame(g, sx + 3, z, "south"), height=3, kind="soul_lantern")
    skull(g, sx, 6)
    # behind the igloos: drying racks, hides, woodpiles
    props.drying_rack(Frame(g, 2, 22, "east"), length=4)
    props.hide_frame(Frame(g, 2, 40, "east"), colour="light_gray")
    props.woodpile(Frame(g, 37, 30, "west"))
    props.drying_rack(Frame(g, 38, 46, "west"), length=4)
    props.sled(Frame(g, 25, 58, "north"))
    props.sled(Frame(g, 13, 58, "south"), cargo=False)
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z) and abs(x - sx) > 2, salt=2)
    return g, dict(INFO, igloos=len(v.igloos))
