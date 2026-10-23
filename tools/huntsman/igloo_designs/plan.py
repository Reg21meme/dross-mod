"""Hand-designed igloo templates (the user's plan of October 20, 2026: "create a general template for each house ... then
copy and paste them"; every light with a purpose, beds centred, chests that open, nothing on slabs).

An igloo is a snow shell (one or more domes hollowed out together by villagekit.igloo, its one way in a plain doorway
1 block wide and 2 tall at the south, no door) and a floor plan drawn as text, layer by layer, so every bed, chest,
seat and light has its place:

    plans = {1: ["  ...  ",        # rows run north (the back) to south (the doorway); the middle row and column are
                 " .B.B. ",        # the first dome's middle; ' ' is outside the plan, '.' floor left clear, any
                 ...], 2: [...]}   # other character a block from LEGEND (or the design's own legend)

Placeholders in a legend's states: {in} faces into the room (towards the middle of its dome), {wall} the other way,
{rot_in} a standing banner's rotation facing in, {skull_in} a standing skull's. A bed is two characters, its head ("B") and its foot ("b")
side by side: the head's facing is worked out from where its foot is. A campfire ("F") gets a smoke vent through the
roof over it, a collar of packed ice round the hole outside.

    build(design) -> Built (its grid, where its way in is, its problems)
"""
import math

import mc
import numpy as np
from voxel import AIR, STEP, Grid

from villagekit import checks, walk, writer
from villagekit.igloo import DEFAULT, Massing
from villagekit.local import ROT16, SKULL_ROT16

# ---------------------------------------------------------------- the common legend

LEGEND = {
    "F": "campfire[facing=north,lit=true,signal_fire=false]",
    "f": "soul_campfire[facing=north,lit=true,signal_fire=false]",
    "C": "chest[facing={in},type=single]",
    "O": "barrel[facing=up,open=false]",
    "o": "barrel[facing={in},open=false]",
    "L": "lantern[hanging=false]",
    "l": "soul_lantern[hanging=false]",
    "T": "crafting_table",
    "S": "smoker[facing={in},lit=true]",
    "K": "water_cauldron[level=3]",
    "k": "cauldron",
    "H": "hay_block[axis=y]",
    "M": "dross:meat_block[axis=y]",
    "R": "dross:roasted_meat_block[axis=y]",
    "D": "dried_kelp_block",
    "J": "stripped_spruce_log[axis=y]",
    "j": "spruce_log[axis=y]",
    "e": "stripped_spruce_log[axis=x]",
    "i": "stripped_spruce_log[axis=z]",
    "P": "spruce_fence",
    "p": "spruce_pressure_plate",
    "X": "fletching_table",
    "G": "grindstone[face=floor,facing={in}]",
    "A": "chipped_anvil[facing={wall}]",
    "Y": "loom[facing={in}]",
    "Q": "lectern[facing={in},has_book=false]",
    "Z": "cartography_table",
    "U": "target",
    "E": "bookshelf",
    # a spear planted upright (the Planted Moonsilver Spear: lightning rods stood in for them until October 22, 2026)
    "I": "dross:planted_spear",
    "V": "bone_block[axis=y]",
    "s": "skeleton_skull[rotation={skull_in}]",
    "W": "white_carpet",
    "w": "light_gray_carpet",
    "n": "brown_carpet",
    "g": "gray_carpet",
    "r": "red_carpet",
    "N": "white_wool",
    "m": "brown_wool",
    "1": "candle[candles=1,lit=true]",
    "2": "candle[candles=2,lit=true]",
    "3": "candle[candles=3,lit=true]",
    "4": "candle[candles=4,lit=true]",
    "q": "spruce_trapdoor[facing={in},half=top,open=false]",
}

# The hunters' guards: armor stands in a skeleton's skull, blue leather and a stone sword (the user, October 20, 2026:
# the Long Hall's "skulls face inwards with leather armor that is blue maybe with stone sword"; the barracks' stands in
# place of its spears)
GUARD_BLUE = 0x3A6FB0             # their leather's dye: a glacier blue
GUARD = {"head": writer.item("minecraft:skeleton_skull"),
         "chest": writer.item("minecraft:leather_chestplate", GUARD_BLUE),
         "legs": writer.item("minecraft:leather_leggings", GUARD_BLUE),
         "feet": writer.item("minecraft:leather_boots", GUARD_BLUE),
         "mainhand": writer.item("minecraft:stone_sword")}
YAW = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}


def P(text):
    """A plan layer written with its row numbers: lines like "  -3 |  OCO  |" (the number is the row's distance south
    of the first dome's middle; what's between the bars is the row, its middle character the middle column). Rows
    can be left out (a layer with only a lantern or two). Returns {row: text}."""
    rows = {}
    for line in text.strip("\n").splitlines():
        if "|" not in line:
            continue
        a = line.index("|")
        b = line.rindex("|")
        rows[int(line[:a].strip())] = line[a + 1:b]
    return rows


def L(half, rows):
    """A plan layer for a wide room, built from its rows' items instead of typed out: rows {row: (lo, hi, {column:
    char})}: the clear floor runs from column lo to hi ('.'; None: none, only the items), the items go at their
    columns. Every row is 2 * half + 1 wide (columns -half..half). Returns {row: text}."""
    out = {}
    for dz, spec in rows.items():
        if len(spec) == 2:                       # (spans, items): several stretches of floor [(lo, hi), ...]
            spans, items = spec
        else:
            lo, hi, items = spec
            spans = [] if lo is None else [(lo, hi)]
        cells = [" "] * (2 * half + 1)
        for lo, hi in spans:
            for c in range(lo, hi + 1):
                cells[c + half] = "."
        for c, ch in items.items():
            cells[c + half] = ch
        out[dz] = "".join(cells)
    return out


def sym(items):
    """{column: char} for items standing in mirrored pairs: {3: 'O'} gives {3: 'O', -3: 'O'}."""
    out = {}
    for c, ch in items.items():
        out[c] = ch
        out[-c] = ch
    return out


ICE_BLOCKS = ("ice", "packed_ice", "blue_ice")
FULL_TOP_SPECIAL = ("barrel", "chest", "crafting_table", "fletching_table", "cartography_table", "smithing_table",
                    "loom", "smoker", "furnace", "bookshelf", "target", "hay_block", "dried_kelp_block")
MIN_FLOOR_LIGHT = 5          # every clear floor cell at least this lit (monsters spawn in the Dross only at 0)
CROP_LIGHT = 9               # every crop and bush under a roof lit this much by its lamps, or it never grows
CROPS = ("frostroots", "snow_lichen", "rimeberry_bush", "wheat", "carrots", "potatoes", "beetroots")


class Igloo:
    """One design: its id and name, what it is (home, storage, cook, barracks, workshop, hall, extra, lodge), its story,
    its domes [(dx, dz, r, h[, windows[, skylight]])] (the first one holds the way in, facing south; windows are (angle
    from the doorway, clockwise; height over the floor)), its plans and legend, beds {head char: (colour, foot char)},
    things hung on chains from the ceiling [(dx, dz, y, state)], armor stands on the floor [(dx, dz, facing or None for
    "into the room", outfit)], and a function for anything else (outside it). The doorway is 1 wide (tunnel_half 1),
    2 wide (tunnel_half 1, tunnel_extra 1: the second column to its west) or 3 wide (tunnel_half 2)."""

    def __init__(self, id, name, kind, lore, domes, plans, legend=None, beds=None, hangs=(), style=DEFAULT,
                 outside=None, tunnel=1, tunnel_half=1, tunnel_height=3, tunnel_extra=0, stands=()):
        self.id, self.name, self.kind, self.lore = id, name, kind, lore
        self.domes = [tuple(d) for d in domes]
        self.plans = plans
        self.legend = dict(LEGEND)
        self.legend.update(legend or {})
        self.beds = {"B": ("white", "b")}
        self.beds.update(beds or {})
        self.hangs = list(hangs)
        self.style = style
        self.outside = outside
        self.tunnel = tunnel
        self.tunnel_half = tunnel_half          # 1: a doorway 1 block wide; 2: 3 wide (the lodge)
        self.tunnel_height = tunnel_height      # 3: 2 tall; 5: 4 tall
        self.tunnel_extra = tunnel_extra        # 1: one more column (a doorway 2 wide)
        self.stands = [tuple(s) for s in stands]


class Custom:
    """A design that isn't an igloo (the Alpha Moose's barn): draw(g) builds it into a grid of `size` (W, D, H; the
    ground at layer 1) and returns (where you stand just outside its way in (x, y, z), the way in's outer cell (x, z),
    its rooms {(x, y, z)}). It's checked like an igloo."""

    def __init__(self, id, name, kind, lore, size, draw):
        self.id, self.name, self.kind, self.lore = id, name, kind, lore
        self.size = size
        self.draw = draw


def build_custom(design):
    W, D, H = design.size
    g = Grid(W, H, D, G=1, seed=1)
    g.block_nbt = {}
    g.entities = []
    entrance, mouth, rooms = design.draw(g)
    problems = check(g, None, rooms, entrance, centre=(W // 2, D // 2))
    return Built(design, g, None, entrance, mouth, rooms, problems)


class Built:
    def __init__(self, design, g, m, entrance, mouth, rooms, problems):
        self.design = design
        self.g, self.m = g, m
        self.entrance = entrance          # (x, y, z): where you stand just outside the doorway
        self.mouth = mouth                # (x, z): the doorway's outer cell
        self.rooms = rooms                # every room cell (x, y, z)
        self.problems = problems


def _size(design):
    ext_x = max(abs(d[0]) + d[2] for d in design.domes) + 2
    ext_z = max(abs(d[1]) + d[2] for d in design.domes) + 2 + design.tunnel
    top = max(d[3] for d in design.domes) + 3
    W = 2 * int(math.ceil(ext_x)) + 1
    D = 2 * int(math.ceil(ext_z)) + 1
    return W, D, int(math.ceil(top)) + 2


def _dir_in(x, z, cx, cz):
    """The compass direction from (x, z) towards (cx, cz) (the bigger of the two steps)."""
    dx, dz = cx - x, cz - z
    if dx == 0 and dz == 0:
        return "south"
    if abs(dx) >= abs(dz):
        return "east" if dx > 0 else "west"
    return "south" if dz > 0 else "north"


OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}


def build(design, seed=1):
    W, D, H = _size(design)
    g = Grid(W, H, D, G=1, seed=seed)
    g.block_nbt = {}
    g.entities = []
    m = Massing(g)
    cx, cz = W // 2, D // 2
    domes = []
    for i, d in enumerate(design.domes):
        dx, dz, r, h = d[:4]
        windows = d[4] if len(d) > 4 else ()
        skylight = d[5] if len(d) > 5 else True
        domes.append(m.dome(cx + dx, cz + dz, r, h, style=design.style, door="south" if i == 0 else None,
                            tunnel=design.tunnel if i == 0 else None, windows=windows, skylight=skylight,
                            name=design.id, tunnel_half=design.tunnel_half, tunnel_height=design.tunnel_height,
                            tunnel_extra=design.tunnel_extra))
    m.build()
    problems = []
    centres = [(dm.cx, dm.cz) for dm in domes]

    def centre_of(x, z):
        return min(centres, key=lambda c: (c[0] - x) ** 2 + (c[1] - z) ** 2)

    # the plan, layer by layer
    beds = []
    fires = []
    for y, rows in sorted(design.plans.items()):
        if isinstance(rows, list):
            rows = {i - len(rows) // 2: row for i, row in enumerate(rows)}
        widths = {len(row) for row in rows.values()}
        if len(widths) != 1 or min(widths) % 2 == 0:
            raise SystemExit(f"{design.id}: layer {y}'s rows must all be one odd width")
        for dz, row in rows.items():
            half_x = len(row) // 2
            for j, ch in enumerate(row):
                if ch == " ":
                    continue
                x, z = cx - half_x + j, cz + dz
                inside = m.is_room(x, 1, z) if y == 0 else m.is_room(x, y, z)     # layer 0: the floor itself
                if not inside:
                    problems.append(f"'{ch}' at {x - cx},{y},{z - cz} (layer {y}) isn't inside the room")
                    continue
                if ch == ".":
                    continue
                if ch in design.beds or any(ch == foot for _c, foot in design.beds.values()):
                    beds.append((ch, x, y, z))
                    continue
                state = design.legend.get(ch)
                if state is None:
                    raise SystemExit(f"{design.id}: no block for '{ch}'")
                if callable(state):                   # chosen per cell (a crop's age), from its place in the plan
                    state = state(x - cx, z - cz)
                data = None
                if isinstance(state, tuple):          # (its state, its block entity data: a banner's pattern)
                    state, data = state
                ccx, ccz = centre_of(x, z)
                facing_in = _dir_in(x, z, ccx, ccz)
                state = (state.replace("{in}", facing_in).replace("{wall}", OPP[facing_in])
                         .replace("{rot_in}", str(ROT16[facing_in])).replace("{skull_in}", str(SKULL_ROT16[facing_in])))
                g.set(x, y, z, state, force=True)
                if data is not None:
                    g.block_nbt[(x, y, z)] = data
                if mc.base(state) in ("campfire", "soul_campfire"):
                    fires.append((x, y, z))
    # beds: each head beside its foot
    heads = {(x, y, z): ch for ch, x, y, z in beds if ch in design.beds}
    feet = {(x, y, z): ch for ch, x, y, z in beds if ch not in design.beds}
    for (x, y, z), ch in heads.items():
        colour, foot_ch = design.beds[ch]
        found = [(fx, fz) for fx, fz in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1))
                 if feet.get((fx, y, fz)) == foot_ch]
        if len(found) != 1:
            problems.append(f"the bed at {x - cx},{y},{z - cz} has {len(found)} feet next to its head")
            continue
        fx, fz = found[0]
        facing = {(1, 0): "east", (-1, 0): "west", (0, 1): "south", (0, -1): "north"}[(x - fx, z - fz)]
        g.set(x, y, z, f"{colour}_bed[facing={facing},part=head,occupied=false]", force=True)
        g.set(fx, y, fz, f"{colour}_bed[facing={facing},part=foot,occupied=false]", force=True)
        del feet[(fx, y, fz)]
    for (x, y, z), _ch in feet.items():
        problems.append(f"a bed's foot at {x - cx},{y},{z - cz} with no head")
    # things hung from the ceiling on chains
    for dx, dz, y, state in design.hangs:
        x, z = cx + dx, cz + dz
        if not m.is_room(x, y, z):
            problems.append(f"the hung {mc.base(state)} at {dx},{y},{dz} isn't inside the room")
            continue
        yy = y + 1
        while m.is_room(x, yy, z):
            g.set(x, yy, z, "chain[axis=y]", force=True)
            yy += 1
        above = g.get(x, yy, z)
        if above is None or mc.kind(above) != "full":
            problems.append(f"the hung {mc.base(state)} at {dx},{y},{dz} has no ceiling to hang from")
        g.set(x, y, z, state, force=True)
    # armor stands on the floor, facing into the room (or the way given)
    for dx, dz, facing, outfit in design.stands:
        x, z = cx + dx, cz + dz
        if not (m.is_room(x, 1, z) and m.is_room(x, 2, z)) or not mc.is_air(g.get(x, 1, z) or AIR)                 or not mc.is_air(g.get(x, 2, z) or AIR):
            problems.append(f"the armor stand at {dx},{dz} has no room to stand (2 clear blocks inside)")
            continue
        if facing is None:
            ccx, ccz = centre_of(x, z)
            facing = _dir_in(x, z, ccx, ccz)
        g.entities.append(writer.armor_stand(x + 0.5, 1, z + 0.5, YAW[facing], **outfit))
    # a smoke vent over every fire: open up through the roof, a collar of packed ice round the hole outside
    for x, y, z in fires:
        yy = y + 1
        while m.is_room(x, yy, z):
            yy += 1
        top = yy
        while True:
            s = g.get(x, yy, z)
            if s is None or mc.is_air(s):
                break
            g.set(x, yy, z, AIR, force=True)
            yy += 1
        hole_top = yy - 1
        if hole_top < top:
            problems.append(f"the fire at {x - cx},{y},{z - cz} has no roof over it for its vent")
        for dx, dz in STEP.values():
            nx, nz = x + dx, z + dz
            col = [yi for yi in range(g.H) if m.shell[nx, yi, nz]] if g.inb(nx, 0, nz) else []
            if not col:
                continue
            ty = max(col) - g.G
            s = g.get(nx, ty + 1, nz)
            if s is not None and mc.base(s) == "snow":
                g.set(nx, ty + 1, nz, AIR, force=True)
            g.set(nx, ty, nz, "packed_ice", force=True)
    if design.outside is not None:
        design.outside(g, cx, cz, domes[0])

    t = domes[0].tunnel_shape
    mouth = t.f.xz(0, t.v1)
    out = t.f.xz(0, t.v1 + 1)
    entrance = (out[0], 1, out[1])
    rooms = set()
    for dm in domes:
        rooms.update(dm.room)
    problems += check(g, m, rooms, entrance, centre=(cx, cz))
    return Built(design, g, m, entrance, mouth, rooms, problems)


# ---------------------------------------------------------------- checks


def full_top(state):
    """Does it have a full flat top (something can stand or lie on it)?"""
    b = mc.base(state)
    p = mc.props_of(state)
    if b.endswith("_slab"):
        return p.get("type") in ("top", "double")
    if b.endswith("_stairs"):
        return p.get("half") == "top"
    if b.endswith("_trapdoor"):
        return p.get("half") == "top" and p.get("open") != "true"
    return mc.kind(state) == "full" or b in FULL_TOP_SPECIAL


def holds_up(below, above):
    """Can `above` stand on `below`? (the user: "stuff on slabs ... random carpet floating, because it's on slabs")"""
    a = mc.base(above)
    if a.endswith("rimeberry_bush") and below is not None and mc.base(below).endswith("rimeberry_bush"):
        return True                 # a two-block bush's upper half on its lower one
    if a in ("lantern", "soul_lantern") and mc.props_of(above).get("hanging") == "true":
        return True
    needs = (mc.kind(above) in ("carpet", "cross") or a.endswith("_pressure_plate") or a.endswith("candle")
             or a.endswith("_skull") or a in ("lantern", "soul_lantern", "dross:frostbloom", "frostbloom")
             or a.endswith("_bed"))
    if not needs:
        return True                 # a block of the shell or of the furniture holds itself up
    if below is None:
        return True
    if mc.is_air(below):
        return False
    b = mc.base(below)
    if b.endswith("_slab") and mc.props_of(below).get("type") == "bottom":
        return False
    if mc.kind(above) == "carpet" or a.endswith("_pressure_plate") or a.endswith("candle") or a.endswith("_skull"):
        return full_top(below) or b.endswith("_fence") or b.endswith("_wall") or (a.endswith("candle") and b == "barrel")
    if a in ("lantern", "soul_lantern"):
        return full_top(below) or b.endswith("_fence") or b.endswith("_wall") or b == "chain"
    return True


def check(g, m, rooms, entrance, centre):
    problems, light = check_blocks(g)
    G = g.G
    # every clear floor cell reachable from the doorway, and lit
    reached = walk.reach(g, entrance)
    cols = {(x, z) for x, _y, z in reached}
    floor = [(x, y, z) for (x, y, z) in rooms if y == 1 and walk.standable(g, x, y, z)]
    lost = [(x - centre[0], z - centre[1]) for x, y, z in floor if (x, z) not in cols]
    if lost:
        problems.append(f"{len(lost)} clear floor cell(s) can't be walked to from the doorway, e.g. {lost[:5]}")
    dark = [(int(light[x, y + G, z]), x - centre[0], z - centre[1]) for x, y, z in floor
            if light[x, y + G, z] < MIN_FLOOR_LIGHT]
    if dark:
        dark.sort()
        problems.append(f"{len(dark)} floor cell(s) darker than {MIN_FLOOR_LIGHT}, the darkest {dark[:3]}")
    problems += walk.facing_problems(g, rooms)
    return problems


def check_blocks(g):
    """Nothing on a bottom slab, nothing that needs a floor without one, chests that open, and the kit's checks
    (nothing melts, beds and doors whole, lanterns held). Returns (problems, the block light)."""
    problems = []
    G = g.G
    floating = []
    on_slab = []
    blocked_chests = []
    for x, yi, z, s in g.blocks():
        y = yi - G
        if y <= 0 or mc.is_air(s):
            continue
        below = g.get(x, y - 1, z)
        b = mc.base(s)
        if below is not None and mc.base(below).endswith("_slab") and mc.props_of(below).get("type") == "bottom":
            on_slab.append((b, x, y, z))
        elif not holds_up(below, s):
            floating.append((b, x, y, z))
        if b in ("chest", "trapped_chest"):
            above = g.get(x, y + 1, z)
            if above is not None and mc.kind(above) == "full":
                blocked_chests.append((x, y, z))
    if on_slab:
        problems.append(f"{len(on_slab)} block(s) on a bottom slab (they'd float), e.g. {on_slab[:4]}")
    if floating:
        problems.append(f"{len(floating)} block(s) with nothing under them to stand on, e.g. {floating[:4]}")
    if blocked_chests:
        problems.append(f"{len(blocked_chests)} chest(s) with a block over them (they can't open), e.g. {blocked_chests[:4]}")
    found, light = checks.check(g)
    problems += found
    # crops under a roof need their lamps' light to grow (out in the open the sky does it)
    dim = []
    for x, yi, z, s in g.blocks():
        b = mc.base(s).split(":")[-1]
        if b not in CROPS or light[x, yi, z] >= CROP_LIGHT:
            continue
        above = [g.get(x, y, z) for y in range(yi - G + 1, g.H - G)]
        roofed = any(a is not None and not mc.is_air(a) and mc.base(a).split(":")[-1] not in CROPS for a in above)
        if roofed:
            dim.append((int(light[x, yi, z]), x, yi - G, z))
    if dim:
        dim.sort()
        problems.append(f"{len(dim)} crop(s) under a roof lit under {CROP_LIGHT} (they'd never grow), e.g. {dim[:4]}")
    return problems, light


def darkest_floor(built):
    """The darkest clear floor cell (template coordinates, layer 0 at the bottom), for the game's light check."""
    g = built.g
    light = checks.block_light(g)
    cells = [(int(light[x, y + g.G, z]), x, y + g.G, z) for x, y, z in built.rooms
             if y == 1 and walk.standable(g, x, y, z)]
    cells.sort()
    return cells[0][1:] if cells else None


def blank(design):
    """Prints the room at each layer of a design's domes (to draw its plan on)."""
    d = Igloo(design.id, design.name, design.kind, design.lore, design.domes, {}, tunnel=design.tunnel)
    W, D, H = _size(d)
    g = Grid(W, H, D, G=1, seed=1)
    m = Massing(g)
    cx, cz = W // 2, D // 2
    for i, dm in enumerate(d.domes):
        dx, dz, r, h = dm[:4]
        m.dome(cx + dx, cz + dz, r, h, door="south" if i == 0 else None, tunnel=d.tunnel if i == 0 else None)
    m.build()
    for y in range(1, H):
        rows = ["".join("." if m.is_room(x, y, z) else " " for x in range(W)) for z in range(D)]
        if not any("." in r for r in rows):
            break
        print(f"layer {y} (middle column {cx}, middle row {cz}):")
        for z, row in enumerate(rows):
            print(f"  {z - cz:+3d} |{row}|")
