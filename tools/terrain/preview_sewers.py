"""
Draws the previews of the sewers under Blightwood (terrain-previews/sewers/), from real generated blocks:

    network_map.png           a regular sewer from above: its tunnels and rooms, the water channels, Sewer Sludge,
                              rough caves and its 3 pipe entrances (and where the land above isn't Blightwood)
    rat_cave_network_map.png  the same for the Rat King's sewer: its one grand entrance, the royal road (no carpet in the
                              game; marked here) to
                              his rat cave (the nest, the hoard, the arrival spot)
    pipe.png                  a regular pipe entrance cut in half: the pipe on the ground, the shaft and its ladder, the
                              pool room
    grand_entrance.png        the Rat King's grand entrance (lit all through): the crowned rim, the tattered banners,
                              the poisoned ground round it (rat nests, dead bushes, fences, sludge, cobwebs)
    grand_entrance_night.png  the same at night, lit only by its own glow (the shaft's and the ground's Blight Vents)
    grand_entrance_cut.png    the grand entrance cut in half: the crowned pipe, the shaft with its glowing vents and
                              ladder, the deep pool in the room below
    royal_road.png            the royal road cut just over its floor, lit by its candles: the candles, the bones and the
                              gold (the banners hang higher up; no carpet)
    rat_cave_top.png          the rat cave with its roof taken off, lit by its own light (candles, glow lichen,
                              Gloomcaps): the nest of carpets, the heaps of gold, the sludge, the drains, the pipes
    rat_cave_section.png      the rat cave's far half seen face on from its middle: its dome, the pipes on its roof and
                              wall pouring into the drains, the nest and the hoard

    1. $env:DROSS_SEWER_PREVIEWS="1"; .\\gradlew.bat runGameTestServer -PgameTestWorld=<a new folder> --console=plain
       (SewersGameTest writes the blocks to build/tv1/sewers/)
    2. python tools/terrain/preview_sewers.py

The cutaways use preview_sites.py's renderer (the real block textures): the cut-in-half ones lit all through so the
shapes read, the night ones (grand_entrance, royal_road, rat_cave_top) by the real block light the test read; the sewer
water is drawn in the sewers biome's own colour. Needs numpy and Pillow.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import preview_sites as ps  # noqa: E402

ROOT = ps.ROOT
DUMPS = os.path.join(ROOT, "build", "tv1", "sewers")
OUT = os.path.join(ROOT, "terrain-previews", "sewers")

# --- the map --------------------------------------------------------------------------------------------------------------
CELL = 5                    # pixels per block on the maps
LEGEND_W = 290
MIN_WIDTH = 900             # room for the title
MAP_COLOURS = {             # what SewersGameTest's map letters mean, and their colour
    ".": ("rock", (24, 27, 34)),
    "T": ("tunnel and room floor", (128, 132, 142)),
    "W": ("sewer water (channels, pools, drains)", (110, 128, 52)),
    "S": ("Sewer Sludge", (104, 92, 44)),
    "c": ("rough cave floor", (112, 98, 80)),
    "C": ("rat cave floor", (142, 120, 92)),
    "N": ("the Rat King's nest (carpets)", (150, 82, 40)),
    "G": ("his hoard (gold ore, gold blocks)", (242, 196, 48)),
    "p": ("copper pipe on the rat cave's wall", (70, 168, 140)),
    "R": ("the royal road (no carpet in the game)", (176, 36, 40)),
    "P": ("pipe entrance (copper)", (60, 150, 124)),
    "O": ("pipe opening (the drop)", (8, 10, 12)),
}
NOT_BLIGHTWOOD = (120, 40, 140)     # tint where the land above isn't Blightwood (there should be none)


def draw_map(dump):
    rows = dump["rows"]
    surface = dump["surface"]
    h, w = len(rows), len(rows[0])
    title = f"{dump['title']}: real generated blocks from above, at the floor (y {dump['floor']}), {w} x {h} blocks"
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    width = max(w * CELL + LEGEND_W, MIN_WIDTH, int(probe.textlength(title, font=ps.font(17, True))) + 24)
    img = Image.new("RGB", (width, max(h * CELL, 420) + 54), ps.BG_TOP)
    d = ImageDraw.Draw(img)
    oy = 48
    for z, row in enumerate(rows):
        for x, ch in enumerate(row):
            colour = MAP_COLOURS.get(ch, ("?", (255, 0, 255)))[1]
            if surface[z][x] != "b":
                colour = tuple(int(c * 0.5 + n * 0.5) for c, n in zip(colour, NOT_BLIGHTWOOD))
            d.rectangle([x * CELL, oy + z * CELL, x * CELL + CELL - 1, oy + z * CELL + CELL - 1], fill=colour)
    x0, z0 = dump["x0"], dump["z0"]
    labels = []
    for k, (px, pz, rim, ground, *rest) in enumerate(dump["pipes"]):
        grand = len(rest) >= 3 and rest[2] == 1
        cx, cz = (px - x0) * CELL + CELL // 2, oy + (pz - z0) * CELL + CELL // 2
        r = 30 if grand else 18
        d.ellipse([cx - r, cz - r, cx + r, cz + r], outline=(255, 210, 80) if grand else (160, 255, 220), width=2)
        label = (f"the grand entrance: a {rim - dump['floor']}-block drop" if grand
                 else f"pipe {k + 1}: a {rim - dump['floor']}-block drop")
        f = ps.font(13, True)
        tw = d.textlength(label, font=f)
        tx = cx + 22 if cx + 22 + tw < w * CELL else cx - 22 - tw
        ty = cz
        while any(tx < bx1 and tx + tw > bx0 and ty - 11 < by1 and ty + 7 > by0 for bx0, by0, bx1, by1 in labels):
            ty += 20   # keep labels from overlapping
        labels.append((tx - 3, ty - 11, tx + tw + 3, ty + 7))
        d.rectangle([tx - 3, ty - 11, tx + tw + 3, ty + 7], fill=(10, 12, 18))
        d.text((tx, ty - 10), label, font=f, fill=(200, 255, 235))
    cave = dump.get("cave")
    if cave:
        ax, az = (cave["arrival_x"] - x0) * CELL + CELL // 2, oy + (cave["arrival_z"] - z0) * CELL + CELL // 2
        d.polygon([(ax, az - 9), (ax + 8, az + 6), (ax - 8, az + 6)], outline=(255, 255, 255), fill=(90, 200, 255))
        d.text((ax + 10, az - 6), "arrival", font=ps.font(12, True), fill=(200, 235, 255))
    d.text((12, 8), title, font=ps.font(17, True), fill=ps.TEXT)
    d.text((12, 28), "Each pixel square is a block; north is up", font=ps.font(12), fill=ps.SUBTEXT)
    lx = w * CELL + 16
    ly = oy
    present = set("".join(rows))
    for ch, (label, colour) in MAP_COLOURS.items():
        if ch not in present:
            continue
        d.rectangle([lx, ly, lx + 16, ly + 16], fill=colour, outline=(70, 76, 90))
        d.text((lx + 24, ly), label, font=ps.font(12), fill=ps.TEXT)
        ly += 24
    d.rectangle([lx, ly, lx + 16, ly + 16], fill=NOT_BLIGHTWOOD)
    d.text((lx + 24, ly), "(tinted: not under Blightwood)", font=ps.font(12), fill=ps.TEXT)
    return img


def draw_view(view, assets, subtitle):
    img = ps.render(view, assets)
    title = f"{view['title']}: real generated blocks, {view['size']} x {view['size']}"
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    lines = wrap(probe, subtitle, ps.font(12), max(img.width, MIN_VIEW_WIDTH) - 24)
    width = max(img.width, MIN_VIEW_WIDTH, int(probe.textlength(title, font=ps.font(17, True))) + 24)
    top = 32 + 15 * len(lines)
    out = Image.new("RGB", (width, img.height + top), ps.BG_TOP)
    out.paste(img, ((width - img.width) // 2, top))
    d = ImageDraw.Draw(out)
    d.text((12, 8), title, font=ps.font(17, True), fill=ps.TEXT)
    for k, line in enumerate(lines):
        d.text((12, 28 + 15 * k), line, font=ps.font(12), fill=ps.SUBTEXT)
    return out


MIN_VIEW_WIDTH = 560


def wrap(draw, text, font, width):
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if draw.textlength(trial, font=font) > width and line:
            lines.append(line)
            line = word
        else:
            line = trial
    return lines + ([line] if line else [])


VIEWS = {
    "pipe": "Seen from above and the south-east, its south half taken away: the pipe on the ground, the shaft with its ladder, "
            "the pool room",
    "grand_entrance": "Seen from above and the south-east: the crowned rim (a bright copper band, copper points tipped with glowing Blight Vents), the "
                      "tattered red banners and the poisoned ground (coarse dirt, sludge puddles, dead bushes, rat nests of bone and "
                      "carpet, cobwebs, gnawed fences, Blight Vents)",
    "grand_entrance_night": "The same at night, lit only by its own light: the green glow of the Blight Vents on its crown, in its "
                            "shaft and in the poisoned ground",
    "grand_entrance_cut": "Seen from above and the south-east, its south half taken away: the crowned pipe, the shaft with its "
                          "glowing vents and ladder, the deep pool of green sewer water in the room below",
    "royal_road": "Seen from above and the south-east, everything over the first block above its floor taken away, lit only by "
                  "its candles: the road from the grand entrance's pool room to the rat cave (no carpet), lit green candles, bone "
                  "piles, raw gold and gold coins along its edges",
    "rat_cave_top": "Seen from above and the south-east with everything over 7 blocks above its floor taken away, lit only by its "
                    "own light: green candles round the nest and on the gold, glow lichen on the walls, Gloomcaps",
}

ELEVATION_SCALE = 16        # pixels per block on the side view
DEPTH_DIMMING = 0.025       # each block further back is this much darker (to 0.3)


def draw_elevation(dump, assets):
    """The far half of the cave seen face on from its middle line: every block's south face, dimmer the further back."""
    palette = dump["palette"]
    looks = [ps.Look(assets, state) for state in palette]
    water = dump.get("water_color")
    tint = None if water is None else (((water >> 16) & 255) / 255.0, ((water >> 8) & 255) / 255.0, (water & 255) / 255.0)
    rows = dump["rows"]
    h, w = len(rows), len(rows[0])
    s = ELEVATION_SCALE
    img = Image.new("RGB", (w * s, h * s), (4, 5, 8))
    for r, row in enumerate(rows):
        for c, (index, depth) in enumerate(row):
            if index < 0:
                continue
            look = looks[index]
            tex = next((look.faces[k] for k in ("south", "cross", "top", "vine") if k in look.faces), None)
            if tex is None:
                continue
            arr = tex.copy()
            if look.name == "minecraft:water" and tint is not None:
                arr[..., :3] = arr[..., :3] * np.array(tint)
            shade = max(0.3, 1.0 - DEPTH_DIMMING * depth) * (0.8 if "south" in look.faces else 1.0)
            arr[..., :3] *= shade
            for g in look.glow.values():
                arr[..., :3] = np.maximum(arr[..., :3], g[..., :3] * g[..., 3:4])
                break
            alpha = arr[..., 3:4]
            rgb = arr[..., :3] * alpha + np.array([4, 5, 8]) / 255.0 * (1 - alpha)
            tile = Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8), "RGB").resize((s, s), Image.NEAREST)
            img.paste(tile, (c * s, r * s))
    out = Image.new("RGB", (img.width, img.height + 60), ps.BG_TOP)
    out.paste(img, (0, 60))
    d = ImageDraw.Draw(out)
    d.text((12, 8), f"{dump['title']}: real generated blocks, {w} wide, y {dump['y0']} to {dump['y1']}", font=ps.font(17, True), fill=ps.TEXT)
    d.text((12, 28), "Looking north from the line through its middle at the far half, face on (blocks further back are darker):",
           font=ps.font(12), fill=ps.SUBTEXT)
    d.text((12, 43), "the dome and its stalactites, the copper pipes on its roof and wall, the nest and the hoard on the floor",
           font=ps.font(12), fill=ps.SUBTEXT)
    return out


def main():
    if not os.path.isdir(DUMPS):
        sys.exit("build/tv1/sewers/ not found: run the tests with DROSS_SEWER_PREVIEWS=1 first (see the top of this file)")
    os.makedirs(OUT, exist_ok=True)
    assets = ps.Assets()
    for name in ("network", "rat_cave_network"):
        path = os.path.join(DUMPS, f"map_{name}.json")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                img = draw_map(json.load(f))
            target = os.path.join(OUT, f"{name}_map.png")
            img.save(target)
            print(f"preview_sewers: {target} ({img.width}x{img.height})")
    elevation = os.path.join(DUMPS, "elevation_rat_cave_section.json")
    if os.path.exists(elevation):
        with open(elevation, encoding="utf-8") as f:
            img = draw_elevation(json.load(f), assets)
        target = os.path.join(OUT, "rat_cave_section.png")
        img.save(target)
        print(f"preview_sewers: {target} ({img.width}x{img.height})")
    for name, subtitle in VIEWS.items():
        path = os.path.join(DUMPS, f"view_{name}.json")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                img = draw_view(json.load(f), assets, subtitle)
            target = os.path.join(OUT, f"{name}.png")
            img.save(target)
            print(f"preview_sewers: {target} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()
