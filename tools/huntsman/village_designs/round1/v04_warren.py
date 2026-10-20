"""No. 4 The Warren: a great dome (the meeting hall, a fire under its smoke hole, log benches round it) with six
smaller igloos round it, each joined to the hall by a covered snow passage, so the whole village is one warm burrow;
one long tunnel leads in from the south."""
from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village

INFO = {"number": 4, "id": "the_warren", "name": "The Warren",
        "mood": "a great hall dome and six igloos joined to it by covered passages"}


def build():
    v = Village(53, 53, H=20, seed=404)
    g = v.g
    c = 26
    hall_style = Style(bands=(3, 5), band="packed_ice", cap="blue_ice")
    small = Style()
    v.igloo(c, c, 8.0, h=8.0, door="south", kind="hall", style=hall_style, tunnel=7,
            windows=[(45, 2), (-45, 2), (135, 2), (-135, 2), (90, 3), (-90, 3), (180, 3)])
    spots = [(c, 9, "north", "sleeping"), (43, c, "east", "sleeping"), (9, c, "west", "workshop"),
             (40, 12, "east", "cook"), (12, 12, "west", "trophy"), (41, 40, "east", "storage")]
    for x, z, side, kind in spots:
        door = {"north": "south", "east": "west", "west": "east"}[side]
        d = v.igloo(x, z, 4.5, door=door, kind=kind, style=small, tunnel=None,
                    windows=[(90, 1), (-90, 1), (180, 1)], **({"beds": 3} if kind == "sleeping" else {}))
    # the passages (axis-aligned legs) from each igloo to the hall
    v.passage([(c, 13), (c, 19)])
    v.passage([(39, c), (33, c)])
    v.passage([(13, c), (19, c)])
    v.passage([(36, 12), (31, 12), (31, 20)])
    v.passage([(16, 12), (21, 12), (21, 20)])
    v.passage([(37, 40), (31, 40), (31, 32)])
    v.build_igloos()

    props.path(g, [(c, 40), (c, 52)], width=3)
    props.totem(Frame(g, c - 3, 44, "south"), height=6)
    props.totem(Frame(g, c + 3, 44, "south"), height=6)
    props.drying_rack(Frame(g, 5, 38, "south"), length=4)
    props.woodpile(Frame(g, 15, 42, "north"), length=3)
    props.sled(Frame(g, 33, 47, "east"))
    props.hide_frame(Frame(g, 45, 12, "west"), colour="brown")
    props.banner_pole(Frame(g, c, 2, "north"), "light_blue", height=5)
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z), salt=4)
    return g, dict(INFO, igloos=len(v.igloos))
