"""No. 8 Ice Lantern Way: igloos of packed ice banded with blue ice and set with clear ice windows, along a winding
path of packed ice lit by ice lanterns (soul lanterns in cages of clear ice); at the path's end a tall ice cairn."""
import math

from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village
from voxel import Mix

INFO = {"number": 8, "id": "ice_lantern_way", "name": "Ice Lantern Way",
        "mood": "glittering igloos of packed ice along a winding lantern-lit path"}

ICE_SHELL = Mix(("packed_ice", 6), ("snow_block", 2), ("blue_ice", 1), patch=0.55, scale=3.0)
PATH = Mix(("packed_ice", 3), ("snow_block", 1), patch=0.3)


def path_x(z):
    """The path's middle at each z: an S-curve from the way in (south) to the cairn (north)."""
    return 22 + 7 * math.sin((z - 6) / 52.0 * 2 * math.pi)


def build():
    v = Village(45, 63, H=18, seed=808)
    g = v.g
    style = Style(shell=ICE_SHELL, rim="blue_ice", bands=(3,), band="snow_block", window="ice", cap="blue_ice",
                  floor="packed_ice", smooth=False)
    spots = [(12, "sleeping", -1), (20, "cook", 1), (29, "sleeping", -1), (37, "trophy", 1),
             (45, "workshop", -1), (52, "sleeping", 1), (26, "storage", 1)]
    for z, kind, side in spots[:6]:
        px = path_x(z)
        x = int(round(px + side * 11))
        v.igloo(x, z, 4.5, toward=(int(round(px)), z), kind=kind, style=style,
                windows=[(60, 1), (-60, 1), (120, 1), (-120, 1), (180, 2)],
                **({"beds": 3} if kind == "sleeping" else {}))
    v.igloo(33 if path_x(56) < 22 else 10, 58, 4.0, toward=(int(round(path_x(58))), 58), kind="storage", style=style,
            windows=[(90, 1), (-90, 1)])
    v.build_igloos()

    pts = [(int(round(path_x(z))), z) for z in range(62, 5, -3)]
    props.path(g, pts, width=3, mat=PATH)
    for d, _, _ in v.igloos:
        props.path(g, [v.doorstep(d, 0), (int(round(path_x(d.cz))), d.cz)], width=1, mat=PATH)
    for z in range(58, 8, -8):
        px = path_x(z)
        for side in (-1, 1):
            x = int(round(px + side * 3))
            if not v.footprint(x, z, 1) and g.get(x, 1, z) is None:
                props.ice_lantern(Frame(g, x, z, "south"), height=2)
    # the cairn of ice at the end of the path
    cx, cz = int(round(path_x(4))), 4
    for y in range(1, 9):
        rr = max(0.5, 2.2 - y * 0.25)
        for x in range(cx - 3, cx + 4):
            for z in range(cz - 3, cz + 4):
                if math.hypot(x - cx, z - cz) <= rr:
                    g.set(x, y, z, "packed_ice" if y < 5 else ("blue_ice" if y < 8 else "ice"))
    g.set(cx, 9, cz, "ice")
    props.drying_rack(Frame(g, 34, 6, "south"), length=4)
    props.sled(Frame(g, 4, 34, "north"))
    props.frostblooms(g, 14, area=lambda x, z: not v.footprint(x, z, 1) and abs(x - path_x(z)) > 3)
    return g, dict(INFO, igloos=len(v.igloos))
