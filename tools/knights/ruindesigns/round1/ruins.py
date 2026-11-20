"""
ARCHIVED (round 1, November 18, 2026): replaced by tools/knights/ruins.py with every biome area's own layouts
(ruindesigns/<element>/<area>.py). Kept as a record; its paths are from when it lived in tools/knights/, so it no
longer runs as it is.

The elemental knights' ruins (the user, November 2026: "create some basic possible ruin spawns, give 3 variations for
each element"): where two knights of an element will guard a broken Dross portal in that element's Overworld biomes
(CLAUDE.md "Parked": the elemental soldiers). Candidates to pick from, on show only for now (no world generation yet).

Three layouts, each built in every element's stones and dressing (15 templates):
  1 Broken Gate      a raised dais with the frame standing on it, its top corner broken off and fallen; two broken
                     pillars, a paved court in front where the two knights stand.
  2 Fallen Ring      a ring of broken pillars round the frame toppled flat on the ground, a stub still standing; the
                     knights either side of it.
  3 Ruined Shrine    a walled court, its walls broken down to stumps, a way in at the front; the broken frame on a dais
                     at the back between two braziers; the knights inside the way in.
The frame (dross:dross_portal_frame) is never whole, so it can never be lit.

Elements: ice (packed ice, snow, stone bricks, soul lanterns, ice spires), poison (mossy stone and mud bricks, moss,
mushrooms on podzol, cobwebs, froglights), fire (blackstone, basalt, magma, campfire braziers), thunder (deepslate, dark
prismarine and lapis, sea lanterns, lightning rods on the pillars), earth (stone bricks, tuff, calcite, amethyst, grass
and flowers).

  python tools/knights/ruins.py            templates, previews and the Java table
  python tools/knights/ruins.py --no-nbt   previews only

Templates: src/main/resources/data/dross/structures/knight_ruin/<element>_<layout>.nbt (the way in at the south, the
ground at layer G). Previews: boss-previews/knights/ruins/ (each ruin from the front-right, all_ruins.png, index.html).
The Java table: boss/knight/KnightRuinDesigns.java (generated: sizes, the ground layer, where the knights stand).
"""
import html
import math
import os
import sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "shrines"))

from PIL import Image, ImageDraw  # noqa: E402

import mc  # noqa: E402
import render  # noqa: E402
import template  # noqa: E402
from voxel import Grid, Mix  # noqa: E402

NBT_DIR = os.path.join(ROOT, "src", "main", "resources", "data", "dross", "structures", "knight_ruin")
PREVIEWS = os.path.join(ROOT, "boss-previews", "knights", "ruins")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "knight", "KnightRuinDesigns.java")

W = D = 25          # every ruin's footprint (odd: the middle on one block)
H = 16              # layers, the ground's G included
G = 2               # layers under the ground
MID = W // 2
AIR = "minecraft:air"
FRAME = mc.FRAME

ELEMENTS = ("ice", "poison", "fire", "thunder", "earth")
ELEMENT_NAMES = {"ice": "Ice", "poison": "Poison", "fire": "Fire", "thunder": "Thunder", "earth": "Earth"}
LAYOUTS = (("gate", "Broken Gate"), ("ring", "Fallen Ring"), ("shrine", "Ruined Shrine"))


# ---------------------------------------------------------------- each element's stones and dressing

def palette(element):
    if element == "ice":
        return dict(
            floor=Mix(("snow_block", 3), ("packed_ice", 2), ("stone_bricks", 3), ("cracked_stone_bricks", 1), patch=0.5),
            wall=Mix(("stone_bricks", 3), ("packed_ice", 3), ("snow_block", 1), ("cracked_stone_bricks", 2), patch=0.4),
            pillar=Mix(("packed_ice", 3), ("blue_ice", 1), patch=0.3),
            trim="chiseled_stone_bricks", light="soul_lantern", rubble=Mix(("packed_ice", 2), ("stone_bricks", 2), ("snow_block", 1)))
    if element == "poison":
        return dict(
            floor=Mix(("mossy_cobblestone", 3), ("mossy_stone_bricks", 3), ("mud_bricks", 2), ("moss_block", 1), patch=0.5),
            wall=Mix(("mossy_stone_bricks", 3), ("mossy_cobblestone", 2), ("mud_bricks", 2), ("cracked_stone_bricks", 1), patch=0.4),
            pillar=Mix(("mossy_stone_bricks", 2), ("mud_bricks", 1), patch=0.3),
            trim="chiseled_stone_bricks", light="verdant_froglight", rubble=Mix(("mossy_cobblestone", 2), ("mud_bricks", 1)))
    if element == "fire":
        return dict(
            floor=Mix(("polished_blackstone_bricks", 3), ("cracked_polished_blackstone_bricks", 2), ("blackstone", 2),
                      ("magma_block", 1), patch=0.5),
            wall=Mix(("polished_blackstone_bricks", 3), ("cracked_polished_blackstone_bricks", 2), ("blackstone", 2),
                     ("gilded_blackstone", 0.3), patch=0.4),
            pillar="basalt[axis=y]",
            trim="chiseled_polished_blackstone", light="campfire[lit=true]",
            rubble=Mix(("blackstone", 2), ("polished_blackstone_bricks", 1), ("basalt[axis=y]", 1)))
    if element == "thunder":
        return dict(
            floor=Mix(("deepslate_tiles", 3), ("cracked_deepslate_tiles", 2), ("polished_deepslate", 2), ("dark_prismarine", 1),
                      patch=0.5),
            wall=Mix(("deepslate_bricks", 3), ("cracked_deepslate_bricks", 2), ("deepslate_tiles", 2), ("dark_prismarine", 1),
                     patch=0.4),
            pillar=Mix(("polished_deepslate", 3), ("lapis_block", 1), patch=0.2),
            trim="chiseled_deepslate", light="sea_lantern", rubble=Mix(("cobbled_deepslate", 2), ("deepslate_bricks", 1)))
    return dict(
        floor=Mix(("stone_bricks", 3), ("mossy_stone_bricks", 2), ("cobblestone", 2), ("tuff", 1), ("grass_block", 2), patch=0.5),
        wall=Mix(("stone_bricks", 3), ("cracked_stone_bricks", 2), ("tuff", 2), ("mossy_stone_bricks", 1), patch=0.4),
        pillar=Mix(("calcite", 2), ("tuff", 1), patch=0.4),
        trim="polished_andesite", light="lantern", rubble=Mix(("cobblestone", 2), ("tuff", 1), ("stone_bricks", 1)))


def dress(g, element, rng_salt=0):
    """Scatters the element's dressing over the ground of the ruin (on top of whatever stands on its floor)."""
    spots = [(x, z) for x in range(1, W - 1) for z in range(1, D - 1)]
    g.rng.shuffle(spots)
    n = 0
    for x, z in spots:
        if n >= 22:
            break
        y = g.top_y(x, z)
        if y < 0 or y > 1 or not g.empty(x, y + 1, z) or not g.empty(x, y + 2, z):
            continue
        under = g.get(x, y, z)
        if under is None or not mc.is_opaque(under):
            continue
        if element == "ice":
            g.set(x, y + 1, z, "snow[layers=%d]" % g.rng.choice((1, 2, 3)))
        elif element == "poison":
            r = g.rng.random()
            if r < 0.45:
                g.set(x, y + 1, z, "moss_carpet")
            elif r < 0.75:
                g.set(x, y, z, "podzol")
                g.set(x, y + 1, z, g.rng.choice(("red_mushroom", "brown_mushroom")))
            else:
                g.set(x, y + 1, z, "cobweb")
        elif element == "fire":
            if g.rng.random() < 0.6:
                g.set(x, y, z, "magma_block")
            else:
                g.set(x, y + 1, z, "blackstone_slab[type=bottom]")
        elif element == "thunder":
            r = g.rng.random()
            if r < 0.5:
                g.set(x, y, z, g.rng.choice(("dark_prismarine", "lapis_block")))
            else:
                g.set(x, y + 1, z, "cobbled_deepslate_slab[type=bottom]")
        else:
            r = g.rng.random()
            if r < 0.35:
                g.set(x, y, z, "grass_block")
                g.set(x, y + 1, z, g.rng.choice(("poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy")))
            elif r < 0.6:
                g.set(x, y + 1, z, "amethyst_cluster[facing=up]")
            else:
                g.set(x, y, z, "coarse_dirt")
        n += 1


def spire(g, x, z, h, element):
    """A thin element spire: ice spikes of packed ice, mushroom stalks, basalt columns, a lightning rod, amethyst."""
    if element == "ice":
        for y in range(1, h + 1):
            g.set(x, y, z, "packed_ice" if y < h else "blue_ice")
    elif element == "poison":
        for y in range(1, h):
            g.set(x, y, z, "mushroom_stem")
        g.set(x, h, z, "red_mushroom_block")
    elif element == "fire":
        for y in range(1, h + 1):
            g.set(x, y, z, "basalt[axis=y]")
    elif element == "thunder":
        for y in range(1, h):
            g.set(x, y, z, "polished_deepslate")
        g.set(x, h, z, "lightning_rod[facing=up]")
    else:
        for y in range(1, h):
            g.set(x, y, z, "amethyst_block")
        g.set(x, h, z, "amethyst_cluster[facing=up]")


def pillar(g, x, z, h, p, element, top_light=False):
    """A broken pillar of the element's stone: trimmed at the foot, its top knocked off at height h."""
    g.set(x, 1, z, p["trim"])
    for y in range(2, h + 1):
        g.set(x, y, z, p["pillar"])
    if top_light:
        put_light(g, x, h + 1, z, p, element)


def put_light(g, x, y, z, p, element):
    light = p["light"]
    if element == "thunder":
        g.set(x, y, z, "sea_lantern")
        g.set(x, y + 1, z, "lightning_rod[facing=up]")
    elif element == "poison":
        g.set(x, y, z, light)
    else:
        g.set(x, y, z, light)


def broken_frame(g, x0, y0, z0, missing):
    """The 5 x 7 frame (opening 3 x 5) along x at z0, its bottom corner at (x0, y0); the cells in `missing` (i, j: across,
    up) broken off. Returns the broken cells' world spots."""
    g.portal_frame(x0, y0, z0, open_w=3, open_h=5, axis="x")
    out = []
    for i, j in missing:
        g.set(x0 + i, y0 + j, z0, AIR, force=True)
        out.append((x0 + i, y0 + j, z0))
    return out


def court(g, p, r, z_mid=None):
    """A paved round court of the floor's stone, its edge ragged (some cells left as the world's ground)."""
    zc = MID if z_mid is None else z_mid
    for x in range(W):
        for z in range(D):
            d = math.hypot(x - MID, z - zc)
            edge = r - 1.5 + 2.5 * g.noise3_at(4.0, x, 0, z)
            if d <= edge:
                g.set(x, 0, z, p["floor"])
                g.set(x, -1, z, "stone" if g.rng.random() < 0.7 else "cobblestone")


def rubble(g, cells, p):
    """The broken-off pieces lie on the ground near where they fell."""
    for (x, y, z) in cells:
        for _ in range(4):
            rx = x + g.rng.randint(-3, 3)
            rz = z + g.rng.randint(1, 5)
            if 0 < rx < W - 1 and 0 < rz < D - 1:
                ty = g.top_y(rx, rz)
                if 0 <= ty <= 1 and g.empty(rx, ty + 1, rz):
                    g.set(rx, ty + 1, rz, FRAME if g.rng.random() < 0.35 else p["rubble"])
                    break


# ---------------------------------------------------------------- the three layouts
# Each returns the knights' spots (x, z, the template's own coordinates: their feet's cell; they stand on the floor at
# y 1) and which way they face (degrees, Minecraft's yaw in the template: 0 south, towards the way in).

def gate(g, element, p):
    court(g, p, 11.0)
    # the dais at the back
    for x in range(MID - 5, MID + 6):
        for z in range(3, 9):
            g.set(x, 1, z, p["floor"])
    for x in range(MID - 2, MID + 3):
        g.set(x, 1, 9, p["wall"])           # its front step, a full block (no stairs: a knight steps up)
    cells = broken_frame(g, MID - 2, 2, 5, [(4, 6), (3, 6), (4, 5)])
    rubble(g, cells, p)
    # two broken pillars at the dais's corners, a light on the taller one
    pillar(g, MID - 6, 4, 5, p, element, top_light=True)
    pillar(g, MID + 6, 4, 3, p, element)
    pillar(g, MID - 7, 14, 2, p, element)
    pillar(g, MID + 7, 15, 4, p, element, top_light=True)
    spire(g, MID - 9, 9, 4, element)
    spire(g, MID + 9, 7, 3, element)
    dress(g, element)
    return [(MID - 3, 13, 0.0), (MID + 3, 13, 0.0)]


def ring(g, element, p):
    court(g, p, 10.5)
    # the ring of pillars
    n = 8
    for k in range(n):
        a = 2.0 * math.pi * (k + 0.5) / n
        x = int(round(MID + math.cos(a) * 9.0))
        z = int(round(MID + math.sin(a) * 9.0))
        if z > MID + 6 and abs(x - MID) < 4:
            continue                      # (the way in)
        h = 2 + int(g.rng.random() * 5)
        pillar(g, x, z, h, p, element, top_light=(k % 3 == 0))
    # the frame toppled flat, its top towards the back; one stub of it still standing
    for i in range(5):
        for j in range(7):
            edge = i == 0 or i == 4 or j == 0 or j == 6
            if edge and not (i == 4 and j == 6) and not (i == 3 and j == 6):
                g.set(MID - 2 + i, 1, MID - j + 1, FRAME, force=True)
    g.set(MID - 2, 2, MID + 1, FRAME, force=True)
    g.set(MID - 2, 3, MID + 1, FRAME, force=True)
    rubble(g, [(MID + 2, 1, MID - 5), (MID + 1, 1, MID - 5)], p)
    spire(g, MID + 5, MID - 4, 4, element)
    spire(g, MID - 5, MID - 5, 3, element)
    dress(g, element)
    return [(MID - 4, MID + 4, 0.0), (MID + 4, MID + 4, 0.0)]


def shrine(g, element, p):
    court(g, p, 12.5)
    x1, x2, z1, z2 = MID - 9, MID + 9, 2, D - 4
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            g.set(x, 0, z, p["floor"])
    g.walls(x1, z1, x2, z2, 1, 6, p["wall"])
    # the way in: a gap 3 wide in the front wall
    for x in range(MID - 1, MID + 2):
        for y in range(1, 7):
            g.set(x, y, z2, AIR, force=True)
    g.ruin_tops(x1, z1, x2, z2, 1, 6, 1.0, 5.5, scale=5.0, salt=7)
    # the dais at the back, the broken frame on it, a brazier either side
    for x in range(MID - 4, MID + 5):
        for z in range(z1 + 1, z1 + 5):
            g.set(x, 1, z, p["trim"] if (x + z) % 4 == 0 else p["floor"])
    cells = broken_frame(g, MID - 2, 2, z1 + 2, [(0, 6), (1, 6), (0, 5)])
    rubble(g, cells, p)
    for x in (MID - 4, MID + 4):
        g.set(x, 2, z1 + 3, p["trim"])
        put_light(g, x, 3, z1 + 3, p, element)
    spire(g, x1 + 2, z2 - 2, 3, element)
    spire(g, x2 - 2, z1 + 6, 4, element)
    dress(g, element)
    return [(MID - 2, z2 - 3, 0.0), (MID + 2, z2 - 3, 0.0)]


BUILDERS = {"gate": gate, "ring": ring, "shrine": shrine}


# ---------------------------------------------------------------- building

def build(element, layout, number):
    g = Grid(W, H, D, G=G, seed=1000 + number * 17)
    p = palette(element)
    guards = BUILDERS[layout](g, element, p)
    # nothing in the knights' way where they stand: 2 wide, 5 tall
    for gx, gz, _ in guards:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for y in range(1, 6):
                    s = g.get(gx + dx, y, gz + dz)
                    if s is not None and s != FRAME:
                        g.set(gx + dx, y, gz + dz, AIR, force=True)
                if g.get(gx + dx, 0, gz + dz) is None:
                    g.set(gx + dx, 0, gz + dz, p["floor"])
    g.drop_floating()
    g.finalize()
    return g, guards


def preview(g, title, sub):
    iso = render.render_iso(g)
    out = Image.new("RGBA", (iso.width + 20, iso.height + 70), (24, 26, 32, 255))
    out.alpha_composite(iso, (10, 60))
    d = ImageDraw.Draw(out)
    d.text((14, 8), title, font=render.font(24, bold=True), fill=(226, 196, 120, 255))
    d.text((14, 38), sub, font=render.font(16), fill=(200, 204, 214, 255))
    return out


def java(entries):
    lines = []
    for e in entries:
        guards = ", ".join(f"new Guard({x}, {z}, {yaw:.1f}F)" for x, z, yaw in e["guards"])
        lines.append(f'            new Design({e["number"]}, "{e["id"]}", "{e["name"]}", KnightKind.Element.{e["element"].upper()}, '
                     f'{W}, {H}, {D}, {G}, List.of({guards}))')
    body = ",\n".join(lines)
    return f'''package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Generated by {{@code tools/knights/ruins.py}} (do not edit by hand): the elemental knights' ruin candidates, three
 * layouts in each element's stones ({{@code data/dross/structures/knight_ruin/<id>.nbt}}: the way in at the south, the
 * ground at layer {{@code ground}}), and where the knights stand in each (the template's own coordinates, their feet on
 * the floor at the ground layer + 1; their yaw, 0 facing south, out of the way in).
 */
public final class KnightRuinDesigns
{{
    /** Where one knight stands: its feet's cell (x, z) and its yaw. */
    public record Guard(int x, int z, float yaw)
    {{
    }}

    /** One ruin: its number, id, layout's name, element, size (width, layers, depth), ground layer and its two guards. */
    public record Design(int number, String id, String name, KnightKind.Element element, int width, int layers, int depth,
                         int ground, List<Guard> guards)
    {{
        public ResourceLocation template()
        {{
            return new ResourceLocation(Dross.MODID, "knight_ruin/" + this.id);
        }}
    }}

    public static final List<Design> ALL = List.of(
{body});

    private KnightRuinDesigns() {{}}
}}
'''


def main(argv):
    no_nbt = "--no-nbt" in argv
    os.makedirs(PREVIEWS, exist_ok=True)
    os.makedirs(NBT_DIR, exist_ok=True)
    entries = []
    cards = []
    number = 0
    for element in ELEMENTS:
        for layout, lname in LAYOUTS:
            number += 1
            rid = f"{element}_{layout}"
            g, guards = build(element, layout, number)
            problems, warnings = template.check(g)
            # (the shrine kit wants one whole frame; a ruin's is broken on purpose: none may be whole, or it could be lit)
            problems = [q for q in problems if "complete empty frame" not in q]
            if template.find_frames(g):
                problems.append("a whole frame: it could be lit")
            frame = sum(1 for _x, _y, _z, s in g.blocks() if s == FRAME)
            print(f"  {number:2d} {rid}: {sum(1 for _ in g.blocks())} blocks, {frame} frame blocks"
                  + (f"; PROBLEMS {problems}" if problems else "") + (f"; {len(warnings)} warnings" if warnings else ""))
            if not no_nbt:
                template.write(g, os.path.join(NBT_DIR, f"{rid}.nbt"))
            title = f"No. {number}  {ELEMENT_NAMES[element]}: {lname}"
            img = preview(g, title, f"{W} x {D}; the way in at the front; the two knights guard the frame")
            path = os.path.join(PREVIEWS, f"{number:02d}_{rid}.png")
            img.save(path)
            cards.append((title, os.path.basename(path)))
            entries.append(dict(number=number, id=rid, name=lname, element=element, guards=guards))
    # one sheet of all fifteen: a row per element
    imgs = [Image.open(os.path.join(PREVIEWS, c[1])) for c in cards]
    cw = max(i.width for i in imgs)
    ch = max(i.height for i in imgs)
    scale = 0.5
    sheet = Image.new("RGBA", (int(cw * scale) * 3, int(ch * scale) * 5), (18, 20, 24, 255))
    for k, im in enumerate(imgs):
        small = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
        sheet.alpha_composite(small, ((k % 3) * int(cw * scale), (k // 3) * int(ch * scale)))
    sheet.save(os.path.join(PREVIEWS, "all_ruins.png"))
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("<!doctype html><meta charset=utf-8><title>Knight ruins</title><body style='background:#15171c;color:#ddd;"
                "font-family:sans-serif'><h1>The elemental knights' ruins (candidates)</h1>"
                "<p>Three layouts in each element's stones. In the game: <code>/dross showcase knight_ruins</code>.</p>")
        for title, name in cards:
            f.write(f"<h3>{html.escape(title)}</h3><img src='{name}' style='max-width:100%'>")
    if not no_nbt:
        with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
            f.write(java(entries))
    print("Done.")


if __name__ == "__main__":
    main(sys.argv[1:])
