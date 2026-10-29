"""Previews of the Emberdactyl's volcano, drawn from the blocks the game really placed.

Run the probe first (it builds the volcano in a test world and writes every block you could see):

    .\\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_volcano -PgameTestWorld=<a new folder> --console=plain

then:

    python tools/fire/volcano_preview.py [path to run/probe/ember_volcano.txt]

It writes boss-previews/cinder_wastes/emberdactyl/volcano_front.png (by day, from its front, where the path starts),
volcano_back.png (from behind, the lookout's side), volcano_night.png (from its front at night: only what glows),
volcano_map.png (from above: the lava, the path, the egg) and volcano_crater.png (the crater close up: the lake, the
nest's pillar, the lookout). Each block is a flat-coloured cube (its colour picked to look
like its texture), so it's a check of the shapes and the lava, not of the textures.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_DUMP = ROOT / "run" / "probe" / "ember_volcano.txt"
OUT = ROOT / "boss-previews" / "cinder_wastes" / "emberdactyl"

# Block colours (the look of each texture's average) and whether it glows.
COLOURS = {
    "minecraft:lava": ((232, 112, 24), True),
    "minecraft:magma_block": ((142, 58, 22), True),
    "dross:cinder_vent": ((70, 40, 32), True),
    "minecraft:campfire": ((200, 120, 40), True),
    "minecraft:lantern": ((236, 180, 90), True),
    "minecraft:basalt": ((80, 79, 84), False),
    "minecraft:polished_basalt": ((98, 97, 102), False),
    "minecraft:smooth_basalt": ((72, 72, 78), False),
    "minecraft:blackstone": ((42, 36, 42), False),
    "minecraft:polished_blackstone": ((54, 48, 58), False),
    "minecraft:obsidian": ((20, 16, 30), False),
    "dross:ash": ((58, 55, 56), False),
    "dross:ash_layer": ((70, 66, 66), False),
    "minecraft:bone_block": ((222, 214, 186), False),
    "dross:charred_log": ((34, 28, 26), False),
    "minecraft:hay_block": ((166, 136, 38), False),
    "minecraft:skeleton_skull": ((200, 200, 196), False),
    "minecraft:wither_skeleton_skull": ((40, 40, 40), False),
    "minecraft:grass_block": ((96, 140, 60), False),
    "minecraft:dirt": ((122, 86, 60), False),
    "minecraft:stone": ((120, 120, 120), False),
    "minecraft:water": ((50, 80, 180), False),
    "minecraft:sand": ((214, 204, 150), False),
    # the paved path up and the hoard at the lookout (October 27)
    "minecraft:polished_blackstone_bricks": ((50, 44, 54), False),
    "minecraft:cracked_polished_blackstone_bricks": ((46, 40, 48), False),
    "minecraft:chiseled_polished_blackstone": ((56, 50, 60), False),
    "minecraft:shroomlight": ((240, 146, 70), True),
    "minecraft:chest": ((160, 112, 50), False),
    "minecraft:gold_block": ((246, 208, 61), True),
    "minecraft:raw_gold_block": ((222, 170, 58), False),
    "minecraft:gilded_blackstone": ((88, 66, 40), False),
    "dross:duskstone_gold_ore": ((120, 104, 70), False),
    "dross:moonsilver_ore": ((150, 156, 170), False),
    "dross:stormite_ore": ((60, 90, 200), True),
}
LEAFY = ("leaves", "log", "grass", "fern", "flower", "bush", "vine", "tulip", "poppy", "dandelion", "azure", "orchid",
         "allium", "oxeye", "cornflower", "lily")
SKY = (156, 178, 214)
CRATER_VIEW = 26
NIGHT_SKY = (10, 12, 22)


def colour_of(name):
    if name in COLOURS:
        return COLOURS[name]
    if any(word in name for word in LEAFY):
        return (70, 110, 50), False
    return (128, 124, 120), False


def load(path):
    blocks = {}
    marks = {"path": []}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.startswith("#"):
            parts = line[2:].split()
            if parts[0] in ("egg", "hover"):
                marks[parts[0]] = tuple(float(v) for v in parts[1:4])
            elif parts[0] == "path":
                marks["path"].append(tuple(int(v) for v in parts[1:4]))
            elif parts[0] == "lava":
                marks["lava"] = int(parts[1])
            continue
        x, y, z, name = line.split()
        blocks[(int(x), int(y), int(z))] = name
    return blocks, marks


def jitter(x, y, z):
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791)
    return 0.94 + ((h & 0xFFFF) / 0xFFFF) * 0.12


def iso(blocks, turn, night=False, k=3.0):
    """An isometric view: the camera off the corner `turn` quarter turns round from the north-west, looking down."""
    def rot(x, z):
        for _ in range(turn % 4):
            x, z = -z, x
        return x, z

    cubes = []
    for (x, y, z), name in blocks.items():
        rx, rz = rot(x, z)
        cubes.append((rx + rz + y, rx, y, rz, name))
    cubes.sort()
    us = [(c[1] - c[3]) * k for c in cubes]
    vs = [(c[1] + c[3]) * k * 0.5 - c[2] * k * 0.82 for c in cubes]
    pad = 20
    u0, u1 = min(us) - 2 * k - pad, max(us) + 2 * k + pad
    v0, v1 = min(vs) - 2 * k - pad, max(vs) + 2 * k + pad
    img = Image.new("RGB", (int(u1 - u0), int(v1 - v0)), NIGHT_SKY if night else SKY)
    draw = ImageDraw.Draw(img)

    def p(px, py, pz):
        return ((px - pz) * k - u0, (px + pz) * k * 0.5 - py * k * 0.82 - v0)

    for _, x, y, z, name in cubes:
        rgb, glow = colour_of(name)
        j = jitter(x, y, z)
        small = name.endswith("skull") or name.endswith("lantern") or name.endswith("campfire") or name.endswith("ash_layer")
        h = 0.5 if small else 1.0
        x0 = x + (0.25 if small and not name.endswith("ash_layer") else 0.0)
        z0 = z + (0.25 if small and not name.endswith("ash_layer") else 0.0)
        x1 = x + (0.75 if small and not name.endswith("ash_layer") else 1.0)
        z1 = z + (0.75 if small and not name.endswith("ash_layer") else 1.0)
        if name.endswith("ash_layer"):
            h = 0.25
        faces = (
            ([p(x0, y + h, z0), p(x1, y + h, z0), p(x1, y + h, z1), p(x0, y + h, z1)], 1.0),
            ([p(x1, y, z0), p(x1, y, z1), p(x1, y + h, z1), p(x1, y + h, z0)], 0.78),
            ([p(x0, y, z1), p(x1, y, z1), p(x1, y + h, z1), p(x0, y + h, z1)], 0.62),
        )
        for corners, shade in faces:
            if night:
                if glow:
                    f = (1.15 if shade == 1.0 else 0.95) * j
                    col = tuple(min(255, int(c * f)) for c in rgb)
                else:
                    f = 0.16 * shade * j
                    col = (int(rgb[0] * f + 4), int(rgb[1] * f + 5), int(rgb[2] * f + 12))
            else:
                f = shade * j
                col = tuple(min(255, int(c * f)) for c in rgb)
            draw.polygon(corners, fill=col)
    return img


def map_view(blocks, marks, scale=4):
    tops = {}
    for (x, y, z), name in blocks.items():
        if (x, z) not in tops or y > tops[(x, z)][0]:
            tops[(x, z)] = (y, name)
    xs = [x for x, _ in tops]
    zs = [z for _, z in tops]
    x0, z0 = min(xs), min(zs)
    w, h = (max(xs) - x0 + 1) * scale, (max(zs) - z0 + 1) * scale
    img = Image.new("RGB", (w, h), (0, 0, 0))
    draw = ImageDraw.Draw(img)
    hmax = max(y for y, _ in tops.values()) or 1
    for (x, z), (y, name) in tops.items():
        rgb, glow = colour_of(name)
        west = tops.get((x - 1, z), (y,))[0]
        north = tops.get((x, z - 1), (y,))[0]
        shade = 1.0 + 0.06 * ((y - west) + (y - north))
        lift = 0.75 + 0.35 * max(0, y) / hmax
        f = shade * lift if not glow else 1.0
        col = tuple(max(0, min(255, int(c * f))) for c in rgb)
        draw.rectangle([(x - x0) * scale, (z - z0) * scale, (x - x0 + 1) * scale - 1, (z - z0 + 1) * scale - 1], fill=col)
    for (x, _, z) in marks["path"][::3]:
        cx, cz = (x - x0 + 0.5) * scale, (z - z0 + 0.5) * scale
        draw.ellipse([cx - 1.5, cz - 1.5, cx + 1.5, cz + 1.5], fill=(250, 250, 250))
    if "egg" in marks:
        ex, _, ez = marks["egg"]
        cx, cz = (ex - x0) * scale, (ez - z0) * scale
        draw.ellipse([cx - 6, cz - 6, cx + 6, cz + 6], outline=(255, 240, 120), width=2)
    return img


def main():
    dump = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_DUMP
    blocks, marks = load(dump)
    OUT.mkdir(parents=True, exist_ok=True)
    # The probe builds it with its front (the path's foot) to the north: two quarter turns put the camera off its
    # north-west, none off its south-east.
    iso(blocks, 2).save(OUT / "volcano_front.png")
    iso(blocks, 0).save(OUT / "volcano_back.png")
    iso(blocks, 2, night=True).save(OUT / "volcano_night.png")
    map_view(blocks, marks).save(OUT / "volcano_map.png")
    # the crater close up: everything within CRATER_VIEW blocks of the middle and near the rim's height
    egg_y = marks.get("egg", (0, 60, 0))[1]
    crater = {pos: name for pos, name in blocks.items()
              if pos[0] * pos[0] + pos[2] * pos[2] <= CRATER_VIEW * CRATER_VIEW and pos[1] >= egg_y - 16}
    iso(crater, 2, k=9.0).save(OUT / "volcano_crater.png")
    lava = sum(1 for name in blocks.values() if name == "minecraft:lava")
    print(f"{len(blocks)} blocks drawn ({lava} lava on show, {marks.get('lava', '?')} lava in all); "
          f"path of {len(marks['path'])} steps; previews in {OUT}")


if __name__ == "__main__":
    main()
