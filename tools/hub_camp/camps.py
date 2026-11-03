"""Candidates for the survivors' camp at the foot of the Shattered Spire's front steps (the Dross hub). The Blind Oracle
(the master, who gives the quests) and the Ragged Seeker (his student) will live there instead of wandering the
spire (the user's plan, October 28, 2026). For the user to pick from; the real hub doesn't have one yet.

Round 1 was three rough drafts (No. 1 The Fireside, No. 2 The Pilgrims' Tents, No. 3 Under the Fallen Stones, below);
the user picked No. 3 (November 2, 2026) and asked for more versions of it: those are fallen_stones.py's five, 3A-3E,
the ones the game's showcase builds.

  python tools/hub_camp/camps.py               previews of 3A-3E
  python tools/hub_camp/camps.py --round1      previews of the round 1 drafts (shrine-previews/hub_camp/round1/)
  python tools/hub_camp/camps.py --templates   the game's templates and HubCampDesigns.java (3A-3E)

It builds the spire from its own design (tools/shrines/designs/d02_spire.py), sets each camp in front of its steps
and draws them with the shrine kit's renderer into shrine-previews/hub_camp/: a card per camp (close up from the
front-right, and from above), the whole spire with it, all_camps.png and index.html.
"""
import html
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from campkit import *  # noqa: E402,F401,F403  (the spire's front, the camp blocks and helpers)
from campkit import spire, render, mc, np, Image, big_blocks  # noqa: E402

# ---------------------------------------------------------------- No. 1  The Fireside

def fireside(g):
    """A lean-to of silverwood beside the road, open to a stone-ringed fire. The Oracle's white bed under its roof,
    the Seeker's bedroll by the fire with his map table and packs."""
    cx, cz = CX - 10, CZ + 23   # (15, 57), west of the road
    floor_disc(g, cx, cz, 7.2, CAMP_FLOOR)
    for x in range(cx + 5, ROAD_X[0]):          # stepping stones over to the road
        for z in range(cz - 1, cz + 2):
            put(g, x, 0, z, STONES)
    fire(g, cx, cz)
    put(g, cx + 2, 1, cz + 2, "cauldron")
    # the lean-to on the west, its open side to the fire, its roof sloping down to the back
    x0, z1, z2 = cx - 3, cz - 3, cz + 3
    for z in (z1, z2):
        for y in range(1, 5):
            put(g, x0, y, z, "dross:silverwood_log[axis=y]")
    for i, x in enumerate(range(x0, x0 - 5, -1)):
        y = 5 - i
        for z in range(z1, z2 + 1):
            put(g, x, y, z, stairs("spruce_stairs", "east"))
        if x != x0:
            for z in (z1, z2):                 # the side walls hold the roof up
                for yy in range(1, y):
                    put(g, x, yy, z, "dross:silverwood_planks")
    put(g, x0 + 1, 3, z1, "blue_wall_banner[facing=east]")
    put(g, x0 - 1, 3, cz, lantern("soul_lantern", hanging=True))
    # the Oracle's corner: his white bed, a blue rug, candles on a barrel
    bed(g, x0 - 3, cz - 1, "west", "white")
    put(g, x0 - 3, 1, cz, "light_blue_carpet")
    put(g, x0 - 2, 1, cz, "light_blue_carpet")
    put(g, x0 - 2, 1, cz + 1, "barrel[facing=up,open=false]")
    put(g, x0 - 2, 2, cz + 1, "white_candle[candles=3,lit=true]")
    # a bench of logs across the fire from the lean-to's mouth
    for x in range(cx - 2, cx + 3):
        put(g, x, 1, cz - 4, "dross:stripped_silverwood_log[axis=x]")
    # the Seeker's bedroll south of the fire, his map table and packs beside it
    bed(g, cx + 2, cz + 4, "east", "gray")
    put(g, cx + 4, 1, cz + 4, "cartography_table")
    put(g, cx + 4, 1, cz + 5, "barrel[facing=up,open=false]")
    put(g, cx + 3, 1, cz + 5, "chest[facing=north,type=single]")
    # firewood stacked behind the lean-to
    for z in range(cz - 2, cz + 2):
        for y in (1, 2):
            put(g, x0 - 6, y, z, "dross:silverwood_log[axis=z]")
    lamp_post(g, ROAD_X[0] - 2, cz - 3)
    lamp_post(g, ROAD_X[0] - 2, cz + 3)
    plants(g, [(cx - 6, cz + 4, "dross:glimmer_fern"), (cx + 6, cz - 3, "dross:wispbloom"),
               (cx + 1, cz + 7, "dross:glimmer_fern"), (cx - 2, cz - 6, "dross:wispbloom"),
               (cx + 5, cz + 6, "dross:glimmer_fern")])
    return dict(number=1, label="1", id="fireside", name="The Fireside",
                blurb="A lean-to of silverwood beside the road, open to a stone-ringed fire. The Oracle sleeps under "
                      "its roof (white bed, blue rug, candles, a blue banner); the Seeker's bedroll lies by the fire "
                      "with his map table, chest and pack. Soul lanterns on posts mark it from the road.",
                focus=(5, 46, 30, 66))


# ---------------------------------------------------------------- No. 2  The Pilgrims' Tents

def tent(g, x1, x2, zc, open_east, cloth, trim):
    """An A-frame tent, its ridge along x at zc and 4 high, closed at its back end and open at its front."""
    back = x1 if open_east else x2
    for x in range(x1, x2 + 1):
        for k in range(4):
            for z in {zc - 3 + k, zc + 3 - k}:
                put(g, x, k + 1, z, trim if k == 3 else cloth)
    for k in range(4):
        for z in range(zc - 3 + k, zc + 4 - k):
            put(g, back, k + 1, z, cloth)


def tents(g):
    """A small stone plaza at the foot of the steps with the fire in its middle, the road going round it, and two
    tents facing each other across it: the Oracle's white one, the Seeker's patched one."""
    cx, cz = CX, CZ + 22   # (25, 56)
    floor_disc(g, cx, cz, 6.2, PLAZA)
    fire(g, cx, cz, ring="mossy_cobblestone_slab[type=bottom]")
    for z in range(cz - 2, cz + 3):            # log benches either side of the fire
        put(g, cx - 4, 1, z, "dross:stripped_silverwood_log[axis=z]")
        put(g, cx + 4, 1, z, "dross:stripped_silverwood_log[axis=z]")
    # the Oracle's tent on the west, white, a blue ridge, its door to the plaza
    floor_box(g, cx - 13, cz - 4, cx - 6, cz + 4, CAMP_FLOOR)
    tent(g, cx - 13, cx - 7, cz, True, "white_wool", "light_blue_wool")
    bed(g, cx - 12, cz, "west", "white")
    for x in range(cx - 10, cx - 7):
        put(g, x, 1, cz, "light_blue_carpet")
    put(g, cx - 10, 1, cz - 2, "white_candle[candles=4,lit=true]")
    put(g, cx - 10, 1, cz + 2, "white_candle[candles=3,lit=true]")
    put(g, cx - 10, 3, cz, lantern("soul_lantern", hanging=True))
    for y in (1, 2):
        put(g, cx - 6, y, cz - 4, "spruce_fence")
    put(g, cx - 6, 3, cz - 4, "blue_banner[rotation=4]")
    # the Seeker's tent on the east, patched brown and grey, his map table and gear outside it
    floor_box(g, cx + 6, cz - 4, cx + 13, cz + 4, CAMP_FLOOR)
    tent(g, cx + 7, cx + 13, cz, False, Mix(("brown_wool", 3), ("gray_wool", 2), ("light_gray_wool", 1), patch=0.3),
         "brown_wool")
    bed(g, cx + 12, cz, "east", "gray")
    put(g, cx + 10, 1, cz - 2, "barrel[facing=up,open=false]")
    put(g, cx + 10, 3, cz, lantern("lantern", hanging=True))
    put(g, cx + 6, 1, cz - 5, "cartography_table")
    put(g, cx + 5, 1, cz - 5, "lectern[facing=south]")
    put(g, cx + 6, 1, cz + 5, "chest[facing=west,type=single]")
    put(g, cx + 7, 1, cz + 5, "barrel[facing=up,open=false]")
    put(g, cx + 6, 1, cz + 3, "dross:planted_spear")
    # a lantern arch over the road where the camp begins
    za = cz + 8
    for x in (cx - 4, cx + 4):
        for y in range(1, 5):
            put(g, x, y, za, "dross:silverwood_log[axis=y]")
    for x in range(cx - 4, cx + 5):
        put(g, x, 5, za, "dross:silverwood_log[axis=x]")
    for x in (cx - 2, cx, cx + 2):
        put(g, x, 4, za, lantern("soul_lantern", hanging=True))
    plants(g, [(cx - 14, cz + 5, "dross:glimmer_fern"), (cx + 14, cz - 5, "dross:wispbloom"),
               (cx - 8, cz + 6, "dross:wispbloom"), (cx + 9, cz + 6, "dross:glimmer_fern")])
    return dict(number=2, label="2", id="pilgrims_tents", name="The Pilgrims' Tents",
                blurb="A small stone plaza at the foot of the steps with the fire in its middle and the road going "
                      "round it. Two tents face each other across it: the Oracle's white one with a blue ridge, "
                      "candles and a blue banner, and the Seeker's patched brown one, with his map table, lectern "
                      "and a planted Moonsilver Spear outside. A lantern arch over the road marks the camp.",
                focus=(9, 46, 41, 66))


# ---------------------------------------------------------------- No. 3  Under the Fallen Stones

def ruin(g):
    """Sheltered under a slab of the spire's broken crown, laid over four broken pillars beside the steps, behind a
    windbreak of fallen masonry. The Oracle sits on a stone seat by a shard of a Dross frame, listening to its hum; a
    blue soul fire burns in front."""
    x1, z1, x2, z2 = 5, STEPS_Z + 1, 20, STEPS_Z + 14   # x 5-20, z 50-63: west of the steps
    floor_box(g, x1, z1, x2, z2, RUIN_FLOOR)
    for x in range(x2 - 1, ROAD_X[0]):         # stones over to the road
        for z in range(z1 + 6, z1 + 9):
            put(g, x, 0, z, STONES)
    # the crown's slab on its pillars
    for (x, z) in ((8, z1), (15, z1), (8, z1 + 4), (15, z1 + 4)):
        for y in range(1, 5):
            put(g, x, y, z, "polished_blackstone_bricks" if y < 4 else "chiseled_polished_blackstone")
    for x in range(7, 17):
        for z in range(z1 - 1, z1 + 6):
            edge = x in (7, 16) or z in (z1 - 1, z1 + 5)
            if edge and g.chance(0.3):
                continue                      # its broken edge
            put(g, x, 5, z, "polished_blackstone_brick_slab[type=bottom]" if edge else CROWN)
    for (x, z) in ((9, z1 + 2), (14, z1 + 2)):
        put(g, x, 4, z, lantern("soul_lantern", hanging=True))
    # the Oracle's seat facing the road, a blue rug before it, candles, the frame shard behind him
    put(g, 10, 1, z1 + 2, stairs("polished_blackstone_brick_stairs", "west"))
    put(g, 11, 1, z1 + 2, "blue_carpet")
    put(g, 11, 1, z1 + 1, "white_candle[candles=3,lit=true]")
    put(g, 11, 1, z1 + 3, "white_candle[candles=2,lit=true]")
    put(g, 9, 1, z1 + 2, FRAME)
    put(g, 9, 2, z1 + 2, FRAME)
    # the Seeker's bedroll and his salvaged crates on the east side
    bed(g, 13, z1 + 1, "north", "gray")
    put(g, 14, 1, z1 + 1, "barrel[facing=up,open=false]")
    put(g, 14, 2, z1 + 1, "barrel[facing=up,open=false]")
    put(g, 13, 1, z1 + 3, "chest[facing=east,type=single]")
    put(g, 17, 1, z1 + 1, "cartography_table")
    # the blue fire in a ring of blackstone in front
    fire(g, 12, z1 + 8, kind="dross:village_soul_fire", ring="blackstone_slab[type=bottom]")
    for z in range(z1 + 7, z1 + 10):
        put(g, 15, 1, z, "polished_blackstone_brick_slab[type=bottom]")
    # a windbreak of fallen masonry on the west and south
    for z in range(z1, z2 + 1):
        h = 1 + int(2.5 * g.noise3_at(3.0, x1, 1, z))
        for y in range(1, h + 1):
            put(g, x1, y, z, "polished_blackstone_brick_wall" if y == h else CROWN)
    for x in range(x1, x1 + 9):
        h = 1 + int(1.6 * g.noise3_at(3.0, x, 1, z2))
        for y in range(1, h + 1):
            put(g, x, y, z2, "polished_blackstone_brick_wall" if y == h else CROWN)
    for (x, z) in ((18, z2 - 1), (17, z2), (19, z1 + 4)):
        put(g, x, 1, z, "blackstone")
    lamp_post(g, ROAD_X[0] - 2, z1 + 3, post="polished_blackstone_brick_wall")
    plants(g, [(7, z2 - 3, "dross:glimmer_fern"), (18, z1 + 10, "dross:wispbloom"),
               (6, z1 + 8, "dross:glimmer_fern")])
    return dict(number=3, label="3", id="fallen_stones", name="Under the Fallen Stones",
                blurb="Beside the steps, under a slab of the spire's broken crown laid over four broken pillars, "
                      "behind a windbreak of fallen masonry. The Oracle sits on a stone seat facing the road, a "
                      "shard of a Dross frame behind him humming and leaking blue dust; the Seeker sleeps among "
                      "salvaged crates. A blue soul fire burns in front.",
                focus=(3, 46, 30, 66))


ROUND1 = (fireside, tents, ruin)

import fallen_stones  # noqa: E402

# The user's pick (November 3, 2026: "3A is best for the camps, let's implement that one"): the camp at the real hub
# (and in /dross showcase hub_camps); round 2's five versions of No. 3 stay in fallen_stones.py (--round2 draws them).
DESIGNS = (fallen_stones.PICK,)
ROUND2 = fallen_stones.VERSIONS

# ---------------------------------------------------------------- previews


class Crop:
    """A piece of the grid; with y_max, everything over that height is taken off (the roofs, to see inside)."""

    def __init__(self, g, x1, z1, x2, z2, y_max=None):
        self.a = g.a[x1:x2 + 1, :, z1:z2 + 1].copy()
        if y_max is not None:
            self.a[:, g.G + y_max + 1:, :] = 0
        self.pal = g.pal
        self.W, self.H, self.D, self.G = self.a.shape[0], g.H, self.a.shape[2], g.G


def with_sky(img):
    arr = np.zeros((img.height, img.width, 4), np.uint8)
    for r in range(img.height):
        t = r / max(img.height - 1, 1)
        arr[r, :, :3] = ((render.SKY_TOP * (1 - t) + render.SKY_BOTTOM * t) * 255).astype(np.uint8)
        arr[r, :, 3] = 255
    sky = Image.fromarray(arr, "RGBA")
    sky.alpha_composite(img)
    return sky


def side_by_side(tiles, gap=20):
    h = max(t.height for t in tiles)
    sheet = Image.new("RGBA", (sum(t.width for t in tiles) + gap * (len(tiles) - 1), h), render.BG)
    x = 0
    for t in tiles:
        sheet.paste(t, (x, (h - t.height) // 2))
        x += t.width + gap
    return sheet


_BASE = []


def bare_spire():
    """The spire with no camp, as the game builds it (made once)."""
    if not _BASE:
        base, _ = spire.build()
        _BASE.append(_as_built(base))
    return _BASE[0]


def build(design):
    """The spire with the camp on it, as the game would build it, checked: the shrine kit's check (nothing hanging in
    the air) and the camp's own (fallen_stones.check); a problem stops the build."""
    import template
    g, _info = spire.build()
    g.drop_floating()
    info = design(g)
    _as_built(g)
    for *_c, s in g.blocks():
        problems = mc.validate(s)
        if problems:
            raise ValueError(problems)
    problems, _warnings = template.check(g)
    problems += fallen_stones.check(g, bare_spire(), info)
    problems += fallen_stones.survivor_check(g, info)
    for p in problems:
        print(f"  PROBLEM ({info['label']} {info['name']}):", p)
    if problems:
        raise SystemExit(f"{info['id']}: the camp has problems")
    return g, info


# ---------------------------------------------------------------- the game's templates

NBT_DIR = os.path.join(PROJECT, "src", "main", "resources", "data", "dross", "structures", "hub_camp")
JAVA = os.path.join(PROJECT, "src", "main", "java", "com", "reg21meme", "dross", "portal", "camp", "HubCampDesigns.java")


def _as_built(g):
    """The grid as tools/shrines/build.py finishes it before writing the spire's template."""
    import build as shrine_build
    g.drop_floating()
    shrine_build._fix_plants(g)
    g.finalize()
    return g


def _states(g):
    pal = np.array(g.pal, dtype=object)
    return pal[g.a]


def write_camp(g, base, path):
    """The camp's template: only the cells where it differs from the bare spire (`base`), air included, at the spire's
    own size, so it goes on top of the built spire at the same corner and turn (the showcase builds the spire first)."""
    import nbt
    import template
    a, b = _states(g), _states(base)
    differ = np.argwhere(a != b)
    used = {}
    palette = nbt.List(nbt.COMPOUND)
    blocks = nbt.List(nbt.COMPOUND)
    for x, yi, z in differ:
        s = a[x, yi, z] or "minecraft:air"
        idx = used.get(s)
        if idx is None:
            idx = len(palette)
            used[s] = idx
            name, props = mc.parse(s)
            entry = {"Name": name}
            if props:
                entry["Properties"] = {k: str(v) for k, v in props.items()}
            palette.append(entry)
        blocks.append({"pos": nbt.List(nbt.INT, [nbt.Int(int(x)), nbt.Int(int(yi)), nbt.Int(int(z))]),
                       "state": nbt.Int(idx)})
    root = {"DataVersion": nbt.Int(template.DATA_VERSION),
            "size": nbt.List(nbt.INT, [nbt.Int(g.W), nbt.Int(g.H), nbt.Int(g.D)]),
            "palette": palette, "blocks": blocks, "entities": nbt.List(nbt.END)}
    nbt.write_file(path, root)
    return len(blocks), len(palette)


def write_java(infos, base):
    def q(s):
        return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'
    yaws = {"south": 0.0, "west": 90.0, "north": 180.0, "east": -90.0}
    G = base.G

    def spot(s):
        x, y, z, facing = s
        return f"new Spot({x}, {G + y}, {z}, {yaws[facing]:.1f}F)"

    rows = []
    for info in infos:
        x1, z1, x2, z2 = info["focus"]
        s = info.get("survivors")
        if s:
            places = ", ".join(f"new Place({x}, {G + y}, {z}, {w})" for x, y, z, w in s["places"])
            people = f"{spot(s['mentor'])}, {spot(s['student'])},\n                    List.of({places})"
        else:
            people = "null, null, List.of()"
        rows.append(f"            new Camp({info['number']}, {q(info['label'])}, {q(info['id'])}, {q(info['name'])},\n"
                    f"                    {q(info['blurb'])},\n"
                    f"                    {x1}, {z1}, {x2}, {z2},\n"
                    f"                    {people})")
    text = f"""package com.reg21meme.dross.portal.camp;

import java.util.List;
import javax.annotation.Nullable;

/**
 * GENERATED by tools/hub_camp/camps.py: don't edit by hand, change the generator and run it again.
 * <p>
 * The survivors' camp at the foot of the Shattered Spire's front steps (the Dross hub; the user's plan of October 28,
 * 2026: the Blind Oracle and the Ragged Seeker live there): the user's pick (November 3, 2026), No. 3A, Under the
 * Fallen Stones (tools/hub_camp/fallen_stones.py; its other four versions are archived there). Each camp's template,
 * {{@code data/dross/structures/hub_camp/<id>.nbt}}, holds only what it changes on the bare spire (air included), at
 * the spire's own size ({{@link #WIDTH}} x {{@link #LAYERS}} x {{@link #DEPTH}}, its ground at layer
 * {{@link #GROUND_LAYER}}, its front on its south side): it goes on top of the built spire at the same corner and turn.
 * {{@code focus}}: the camp's box (template x and z, unturned). {{@code mentor}} and {{@code student}}: where the
 * survivors rest in it, {{@code places}}: where they like to walk (template cells, unturned: x, layer, z; a
 * {{@link Spot}}'s yaw for a south-facing spire), checked by the generator to have a floor and room to stand.
 */
public final class HubCampDesigns
{{
    public static final int WIDTH = {base.W};
    public static final int DEPTH = {base.D};
    public static final int LAYERS = {base.H};
    public static final int GROUND_LAYER = {base.G};

    /** A survivor's rest spot: the cell its feet stand in (template x, layer, z) and the way it faces there. */
    public record Spot(int x, int y, int z, float yaw)
    {{
    }}

    /** A place the survivors like to walk to (template x, layer, z) and how often it's picked. */
    public record Place(int x, int y, int z, int weight)
    {{
    }}

    /** One camp: its place in the row, its label ("3A"), template id, name, a line about it, its box (template x and
     *  z), and where the survivors live in it (null and empty for a camp that doesn't say). */
    public record Camp(int number, String label, String id, String name, String blurb, int focusMinX, int focusMinZ,
                       int focusMaxX, int focusMaxZ, @Nullable Spot mentor, @Nullable Spot student, List<Place> places)
    {{
        public String template()
        {{
            return "hub_camp/" + id;
        }}
    }}

    public static final List<Camp> CAMPS = List.of(
{(","+chr(10)).join(rows)});

    private HubCampDesigns() {{}}
}}
"""
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def templates():
    """Writes each camp's template (what it changes on the bare spire) and the Java table."""
    import template
    os.makedirs(NBT_DIR, exist_ok=True)
    base = bare_spire()
    infos = []
    for design in DESIGNS:
        g, info = build(design)
        n, pal = write_camp(g, base, os.path.join(NBT_DIR, f"{info['id']}.nbt"))
        print(f"No. {info['label']} {info['name']}: {n} blocks changed on the spire ({pal} states)")
        infos.append(info)
    keep = {f"{i['id']}.nbt" for i in infos}
    for old in os.listdir(NBT_DIR):
        if old.endswith(".nbt") and old not in keep:
            os.remove(os.path.join(NBT_DIR, old))
            print("removed", old, "(not one of the camps any more)")
    write_java(infos, base)
    print("wrote", os.path.relpath(JAVA, PROJECT))


def main():
    if "--templates" in sys.argv:
        templates()
        return
    round1 = "--round1" in sys.argv
    round2 = "--round2" in sys.argv
    out = os.path.join(OUT, "round1") if round1 else os.path.join(OUT, "round2") if round2 else OUT
    designs = ROUND1 if round1 else ROUND2 if round2 else DESIGNS
    os.makedirs(out, exist_ok=True)
    cards = []
    for design in designs:
        g, info = build(design)
        x1, z1, x2, z2 = info["focus"]
        big_blocks(True)
        close = with_sky(render.render_iso(Crop(g, x1, z1, x2, z2)))
        inside = with_sky(render.render_iso(Crop(g, x1, z1, x2, z2, y_max=1)))
        big_blocks(False)
        title = f"No. {info['label']}  {info['name']}"
        card = render._label(side_by_side([close, inside]), title,
                             sub="From the front-right (the spire's steps at the back, the road coming towards you); "
                                 "on the right, everything cut off at knee height to show the beds, seats and fire.")
        card_path = os.path.join(out, f"camp_{info['label']}_{info['id']}.png")
        card.save(card_path)
        wide = with_sky(render.render_iso(g))
        wide = render._label(wide, title + "  -  with the whole spire", sub="From the front-right.")
        wide.save(os.path.join(out, f"camp_{info['label']}_{info['id']}_wide.png"))
        cards.append((info, card))
        print(f"{title}: {os.path.relpath(card_path, PROJECT)}")
    width = max(c.width for _i, c in cards)
    sheet = Image.new("RGBA", (width, sum(c.height for _i, c in cards) + 30 * (len(cards) - 1)), render.BG)
    y = 0
    for _info, c in cards:
        sheet.paste(c, (0, y))
        y += c.height + 30
    sheet.save(os.path.join(out, "all_camps.png"))
    page = ["<!doctype html><meta charset='utf-8'><title>Hub camp designs</title>",
            "<style>body{background:#1b1d24;color:#e6e8ef;font-family:Segoe UI,sans-serif;max-width:1400px;"
            "margin:24px auto;padding:0 16px}img{max-width:100%;border-radius:6px;margin:8px 0}"
            "p{max-width:900px;line-height:1.5}</style>",
            "<h1>" + ("The survivors' camp: the three drafts of round 1" if round1
                      else "The survivors' camp: No. 3, Under the Fallen Stones, in five versions" if round2
                      else "The survivors' camp: No. 3A, Under the Fallen Stones") + "</h1>",
            "<p>At the foot of the Shattered Spire's front steps (the Dross hub). The Blind Oracle (the master) and the "
            "Ragged Seeker (his student) live here." + ("" if round1 else " The user picked No. 3 of the three "
            "drafts (round 1); these are five versions of it (round 2)." if round2 else " The user's pick: No. 3A of "
            "the five versions of No. 3 (<a href='round2/index.html'>round 2</a>), built at the real hub; the three "
            "drafts are in <a href='round1/index.html'>round 1</a>.") + "</p>"]
    for info, _c in cards:
        stem = f"camp_{info['label']}_{info['id']}"
        page += [f"<h2>No. {info['label']}  {html.escape(info['name'])}</h2>", f"<p>{html.escape(info['blurb'])}</p>",
                 f"<img src='{stem}.png'>", f"<img src='{stem}_wide.png'>"]
    with open(os.path.join(out, "index.html"), "w", encoding="utf-8") as f:
        f.write("\n".join(page))


if __name__ == "__main__":
    main()
