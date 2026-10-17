"""
Draws the pictures for the mod's CurseForge page (into curseforge/), from the bosses' real models and textures:

    dross_banner.png           the banner for the top of the description: "THE DROSS" in blocky letters made of the
                               portal frame's stone, glowing electric blue, and below it a box for every boss the
                               Dross will have (BOSS_SLOTS): the bosses already in the game show their head in 3D on
                               their biome's colours, the rest are black with a question mark
    boss_cinder_colossus.png   the Cinder Colossus in its three stages: Shell, Cracking, Lava Core
    boss_rat_king.png          the Rat King asleep, awake and enraged

    python tools/icon/draw_page_art.py            (about 6 minutes the first time: it builds both bosses)
    python tools/icon/draw_page_art.py --fresh    (forget the saved renders, after a boss's look changes)

The bosses are drawn by their own generators' renderers (tools/golems, tools/ratking) and the renders are kept in
build/page_art_cache/, so changing the layout afterwards takes seconds. The colours come from DrossColors.java (the
shared palette, and each boss's own colour) and the biomes' own colours (data/dross/worldgen/biome/). A new boss: add
its class to BOSSES. Needs numpy, scipy and Pillow.
"""
import importlib.util
import json
import random
import re
import shutil
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ folders in the project

import numpy as np  # noqa: E402
from PIL import Image, ImageFilter  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "curseforge"
CACHE = ROOT / "build" / "page_art_cache"
COLOURS = ROOT / "src/main/java/com/reg21meme/dross/DrossColors.java"
BIOMES = ROOT / "src/main/resources/data/dross/worldgen/biome"
FRAME_TEXTURE = ROOT / "src/main/resources/assets/dross/textures/block/dross_portal_frame.png"

# The banner (its size follows from the boxes).
BANNER_MARGIN = 64        # room at the sides of the boxes and under the last row
TITLE = "THE DROSS"
TITLE_PIXEL = 30          # one pixel of the title's letters, in image pixels (a block of the frame's stone)
TITLE_TOP = 44
TITLE_DEPTH = 14          # how far the letters stand out (their dark side below)
BOSS_SLOTS = 14           # every boss the Dross will have (9 + 5); the ones not in the game yet are mystery boxes
BOXES_PER_ROW = 7
BOX = 212                 # each box is this many pixels square...
BOX_GAP = 22              # ...this far apart
BOX_TOP = 330             # the first row's top
BOX_BORDER = 5
BANNER_W = BOXES_PER_ROW * (BOX + 2 * BOX_BORDER) + (BOXES_PER_ROW - 1) * BOX_GAP + 2 * BANNER_MARGIN
HEAD_FILL = 0.84          # a head fills this share of its box
HEAD_YAW, HEAD_ELEVATION = -35.0, 10.0  # turned to the side (3D), like the icon
MYSTERY_PIXEL = 14        # the question mark
MYSTERY_BACK = (10, 10, 14)
MYSTERY_EDGE = (22, 22, 30)
MYSTERY_BORDER = (52, 56, 72)
MYSTERY_MARK = (92, 98, 124)
# The boss pictures.
CARD_W, CARD_H = 1600, 820
CARD_TITLE_PIXEL = 12
CARD_BOX = (470, 560)     # each pose fits in this box
CARD_LABEL_PIXEL = 5
CARD_YAW, CARD_ELEVATION = -30.0, 10.0
# Little blue dust drifting about the night background.
DUST = 90
DUST_SEED = 7

# The 5 x 7 letters (only the ones the pictures use).
GLYPHS = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
    "D": ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "G": [".####", "#....", "#....", "#..##", "#...#", "#...#", ".###."],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "I": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "K": ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
    "L": ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
    "N": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "W": ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
    "?": [".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."],
}
SPACE = 3


# ------------------------------------------------------------------------------------------------- colours

def colour(name):
    """A colour constant from DrossColors.java (following names like PORTAL_PARTICLE = PALETTE_MAIN)."""
    text = COLOURS.read_text(encoding="utf-8")
    for _ in range(4):
        value = re.search(r"\b" + name + r"\s*=\s*(0x[0-9A-Fa-f]{6}|[A-Z_]+)\s*;", text).group(1)
        if value.startswith("0x"):
            v = int(value, 16)
            return (v >> 16 & 255, v >> 8 & 255, v & 255)
        name = value
    raise ValueError(name)


def biome_colour(biome, key):
    v = json.loads((BIOMES / f"{biome}.json").read_text(encoding="utf-8"))["effects"][key]
    return (v >> 16 & 255, v >> 8 & 255, v & 255)


MAIN, DEEP, HIGHLIGHT = colour("PALETTE_MAIN"), colour("PALETTE_DEEP_COBALT"), colour("PALETTE_HIGHLIGHT")
NIGHT = (5, 7, 20)
WHITE = (236, 242, 255)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# ------------------------------------------------------------------------------------------------- the bosses

KEY = (255, 0, 255)


def cut_out(image):
    """The picture with its key-coloured background made see-through, cropped to what's left."""
    a = np.array(image.convert("RGBA"))
    a[(a[..., 0] == KEY[0]) & (a[..., 1] == KEY[1]) & (a[..., 2] == KEY[2])] = 0
    image = Image.fromarray(a, "RGBA")
    return image.crop(image.getbbox())


def under(bone_name, also=lambda bone: True):
    """A bone filter: only that bone and what hangs off it (and what `also` lets through)."""
    def visible(bone):
        if not also(bone):
            return False
        while bone is not None:
            if bone.name == bone_name:
                return True
            bone = bone.parent
        return False
    return visible


class Boss:
    """A boss in the game: built only when a picture of it isn't saved yet (building takes minutes)."""
    name = ""
    glow = ""      # its own colour, in DrossColors
    biome = ""     # its home biome (its box's colours)

    def __init__(self):
        self._built = False

    def picture(self, kind, scale):
        """One saved render (cut out), drawing it if it isn't saved yet."""
        path = CACHE / f"{self.name.lower().replace(' ', '_')}_{kind}_{scale}.png"
        if path.exists():
            return Image.open(path).convert("RGBA")
        if not self._built:
            print(f"draw_page_art: building the {self.name.title()}...", flush=True)
            self.build()
            self._built = True
        image = self.render(kind, scale)
        CACHE.mkdir(parents=True, exist_ok=True)
        image.save(path)
        return image

    def box_colours(self):
        """Its box: the biome's own fog colour at the edges, glowing towards its colour in the middle."""
        edge = biome_colour(self.biome, "fog_color")
        return mix(edge, NIGHT, 0.25), mix(edge, colour(self.glow), 0.5)


class RatKing(Boss):
    name = "RAT KING"
    glow = "RAT_KING_SLAM_SPARK"
    biome = "blightwood"
    poses = ("ASLEEP", "AWAKE", "ENRAGED")

    def build(self):
        sys.path.insert(0, str(ROOT / "tools" / "ratking"))
        import build as rat_king  # the Rat King's generator
        from ratkit import king_anims, views
        self.views = views
        design = rat_king.kings()[0]
        self.model, _bones, body = rat_king.build_king_model(design, verbose=False)
        self.textures = rat_king.paint_king(design, self.model)
        # only the animations his picture needs (all of them take minutes)
        rig = king_anims.KingRig(self.model, body, design)
        self.anims = {"sleep": king_anims.sleep(rig), "idle": king_anims.idle(rig), "rage": king_anims.rage(rig)}

    def _draw(self, scale, pose=None, yaw=0.0, elevation=6.0, visible=None, rage=0.0):
        kw = {"visible": visible} if visible else {}
        return cut_out(self.views.draw(self.model, self.textures, pose=pose, yaw=yaw, elevation=elevation, scale=scale,
                                       grid=False, figure=False, background=KEY, rage_strength=rage, **kw))

    def render(self, kind, scale):
        a = self.anims
        if kind == "head":
            return self._draw(scale, None, HEAD_YAW, HEAD_ELEVATION, visible=under("head"))
        if kind == "ASLEEP":
            return self._draw(scale, a["sleep"].sample(0.0), CARD_YAW, CARD_ELEVATION)
        if kind == "AWAKE":
            return self._draw(scale, a["idle"].sample(0.0), CARD_YAW, CARD_ELEVATION)
        return self._draw(scale, a["rage"].sample(3.7), CARD_YAW, CARD_ELEVATION, rage=1.0)


class CinderColossus(Boss):
    name = "CINDER COLOSSUS"
    glow = "COLOSSUS_RANGED_EMBER"
    biome = "cinder_wastes"
    poses = ("SHELL", "CRACKING", "LAVA CORE")

    def build(self):
        spec = importlib.util.spec_from_file_location("golem_build", ROOT / "tools" / "golems" / "build.py")
        golems = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(golems)  # (it puts tools/golems on the path, so its kit imports below)
        from designs import common
        from kit import anims, cracks, previews, render
        self.render_, self.previews, self.cracks = render, previews, cracks
        self.design = golems.load_design(golems.DESIGN_MODULES[0])
        self.model, body, info = golems.build_model(self.design, verbose=False)
        base, glow, statue, flow = golems.paint_model(self.design, self.model, info)
        self.textures = {"base": base, "glow": glow, "statue": statue, "flow": flow,
                         "cracks": golems.paint_cracks(self.design, self.model)}
        # only the animation its picture needs: the Lava Core's volcanoes erupting
        rig = anims.Rig(self.model, common.limbs(self.model, body), common.rest_targets(body), getattr(self.design, "ANIM", {}))
        self.anims = {"volcano_erupting": anims.volcano_erupting(rig)}

    def _draw(self, scale, phase, yaw, elevation, cracking=False, only=None):
        pose, flow = self.previews.still(phase, self.anims)
        visible = self.previews.visibility(phase)
        crack = 0.0
        if cracking:
            loose = self.cracks.loose_pose(getattr(self.design, "LOOSE", ()), 1.0)
            pose, visible, flow, crack = self.cracks.add_pose(pose, loose), self.previews.visibility("dormant"), 1.0, 1.0
        if only:
            visible = under(only, visible)
        glow = self.previews.DORMANT_GLOW if phase == "dormant" else 1.0
        return cut_out(self.render_.render(self.model, self.textures, pose=pose, visible=visible, yaw=yaw,
                                           elevation=elevation, scale=scale, grid=False, figure=False, background=KEY,
                                           flow_progress=flow, crack_strength=crack, glow_strength=glow))

    def render(self, kind, scale):
        if kind == "head":
            return self._draw(scale, "erupted", HEAD_YAW, HEAD_ELEVATION, only="head")
        if kind == "SHELL":
            return self._draw(scale, "dormant", CARD_YAW, CARD_ELEVATION)
        if kind == "CRACKING":
            return self._draw(scale, "erupted", CARD_YAW, CARD_ELEVATION, cracking=True)
        return self._draw(scale, "core", CARD_YAW, CARD_ELEVATION)


# The bosses in the game, in the order their boxes come on the banner (a new boss: add its class here).
BOSSES = [CinderColossus, RatKing]


def fitted(boss, kind, box, probe=4):
    """The scale (pixels a texture pixel, a whole number so the pixels stay crisp) that fits that picture in the box."""
    size = boss.picture(kind, probe).size
    return max(1, int(probe * min(box[0] / size[0], box[1] / size[1])))


# ------------------------------------------------------------------------------------------------- drawing

def radial(w, h, centre, edge, power=1.6, reach=0.75):
    """A w x h picture going from `centre` in the middle to `edge` at the sides."""
    ys, xs = np.mgrid[0:h, 0:w]
    t = np.clip(1.0 - np.hypot((xs - w / 2) / w, (ys - h * 0.45) / h) / reach, 0.0, 1.0) ** power
    c, e = np.array(centre, dtype=float), np.array(edge, dtype=float)
    rgb = e + (c - e) * t[..., None]
    return Image.fromarray(np.dstack([np.round(rgb).astype(np.uint8), np.full((h, w), 255, np.uint8)]), "RGBA")


def night(w, h, centre=None):
    """The night background: near-black at the edges, a deep glow in the middle, and blue dust."""
    image = radial(w, h, mix(NIGHT, centre or mix(DEEP, MAIN, 0.3), 0.8), NIGHT, power=1.7)
    rng = random.Random(DUST_SEED)
    for _ in range(DUST):
        x, y, size = rng.randrange(w), rng.randrange(h), rng.choice((4, 4, 6, 8))
        c = HIGHLIGHT if rng.random() < 0.35 else MAIN
        image.alpha_composite(Image.new("RGBA", (size, size), c + (rng.randint(70, 170),)), (x, y))
    return image


def glow(shape, colour_, radius, strength):
    alpha = shape.split()[3].filter(ImageFilter.GaussianBlur(radius))
    alpha = alpha.point(lambda a: int(min(255, a * strength * 1.6)))
    layer = Image.new("RGBA", shape.size, colour_ + (0,))
    layer.putalpha(alpha)
    return layer


def text_mask(text, pixel):
    """The text in the blocky letters: a mask (L), each letter pixel `pixel` image pixels."""
    cols = []
    for i, ch in enumerate(text):
        if ch == " ":
            cols.extend([None] * SPACE)
            continue
        rows = GLYPHS[ch]
        cols.extend([[rows[r][c] == "#" for r in range(7)] for c in range(5)])
        if i + 1 < len(text) and text[i + 1] != " ":
            cols.append(None)
    mask = Image.new("L", (len(cols) * pixel, 7 * pixel), 0)
    for c, col in enumerate(cols):
        if col:
            for r in range(7):
                if col[r]:
                    mask.paste(255, (c * pixel, r * pixel, (c + 1) * pixel, (r + 1) * pixel))
    return mask


def stone_title(text, pixel, depth):
    """Blocky letters made of the portal frame's stone, standing out with a dark side, a blue rim and a glow."""
    mask = text_mask(text, pixel)
    w, h = mask.size
    pad = 40
    out = Image.new("RGBA", (w + 2 * pad, h + depth + 2 * pad), (0, 0, 0, 0))
    full = Image.new("L", out.size, 0)
    for d in range(depth + 1):
        full.paste(255, (pad, pad + d), mask)
    # the glow and the bright rim round the letters and their side
    rim = full.filter(ImageFilter.MaxFilter(7))
    rim_layer = Image.new("RGBA", out.size, HIGHLIGHT + (0,))
    rim_layer.putalpha(rim)
    out.alpha_composite(glow(rim_layer, MAIN, 22, 0.9))
    out.alpha_composite(rim_layer)
    # the dark side
    for d in range(depth, 0, -1):
        side = Image.new("RGBA", out.size, mix(DEEP, NIGHT, 0.55 + 0.4 * d / depth) + (0,))
        shifted = Image.new("L", out.size, 0)
        shifted.paste(255, (pad, pad + d), mask)
        side.putalpha(shifted)
        out.alpha_composite(side)
    # the face: the frame's stone, a block of it per letter pixel, each turned its own way
    tile = Image.open(FRAME_TEXTURE).convert("RGBA").crop((0, 0, 16, 16)).resize((pixel, pixel), Image.NEAREST)
    face = Image.new("RGBA", mask.size, (0, 0, 0, 0))
    for y in range(0, h, pixel):
        for x in range(0, w, pixel):
            face.paste(tile.rotate(90 * ((x // pixel * 7 + y // pixel * 3) % 4)), (x, y))
    # a lighter top edge on each letter
    edge = max(2, pixel // 8)
    above = Image.new("L", mask.size, 0)
    above.paste(mask, (0, edge))
    top = Image.composite(Image.new("L", mask.size, 0), mask, above)
    face.alpha_composite(Image.composite(Image.new("RGBA", mask.size, HIGHLIGHT + (110,)),
                                         Image.new("RGBA", mask.size, (0, 0, 0, 0)), top))
    face.putalpha(mask)
    out.alpha_composite(face, (pad, pad))
    return out


def label(text, pixel, colour_):
    """A short label in the blocky letters, with a dark outline."""
    mask = text_mask(text, pixel)
    pad = pixel + 4
    out = Image.new("RGBA", (mask.width + 2 * pad, mask.height + 2 * pad), (0, 0, 0, 0))
    outline = Image.new("L", out.size, 0)
    outline.paste(mask, (pad, pad))
    outline = outline.filter(ImageFilter.MaxFilter(2 * (pixel // 2) + 1))
    dark = Image.new("RGBA", out.size, NIGHT + (0,))
    dark.putalpha(outline)
    out.alpha_composite(dark, (0, pixel // 2))
    out.alpha_composite(dark)
    fill = Image.new("RGBA", mask.size, colour_ + (255,))
    fill.putalpha(mask)
    out.alpha_composite(fill, (pad, pad))
    return out


def place(canvas, image, centre_x, top=None, bottom=None):
    y = top if top is not None else bottom - image.height
    canvas.alpha_composite(image, (int(centre_x - image.width / 2), int(y)))


def bordered(inside, border):
    """A box: its inside with a border round it."""
    box = Image.new("RGBA", (inside.width + 2 * BOX_BORDER, inside.height + 2 * BOX_BORDER), border + (255,))
    box.alpha_composite(inside, (BOX_BORDER, BOX_BORDER))
    return box


def boss_box(boss):
    """A boss's box: its biome's colours, its head in 3D, a border in its own colour."""
    edge, centre = boss.box_colours()
    inside = radial(BOX, BOX, centre, edge, power=1.3, reach=0.8)
    fill = int(BOX * HEAD_FILL)
    head = boss.picture("head", fitted(boss, "head", (fill, fill)))
    shadow = Image.new("RGBA", inside.size, (0, 0, 0, 0))
    place(shadow, head, BOX / 2, top=(BOX - head.height) / 2)
    inside.alpha_composite(glow(shadow, (0, 0, 0), 8, 0.6), (0, 6))
    inside.alpha_composite(shadow)
    return bordered(inside, mix(colour(boss.glow), WHITE, 0.15))


def mystery_box():
    """A boss still to come: a black box with a question mark."""
    inside = radial(BOX, BOX, MYSTERY_EDGE, MYSTERY_BACK, power=1.3, reach=0.8)
    mark = label("?", MYSTERY_PIXEL, MYSTERY_MARK)
    place(inside, mark, BOX / 2, top=(BOX - mark.height) / 2)
    return bordered(inside, MYSTERY_BORDER)


def banner(bosses):
    boxes = [boss_box(b) for b in bosses] + [mystery_box() for _ in range(BOSS_SLOTS - len(bosses))]
    side = BOX + 2 * BOX_BORDER
    rows = [boxes[i:i + BOXES_PER_ROW] for i in range(0, len(boxes), BOXES_PER_ROW)]
    image = night(BANNER_W, BOX_TOP + len(rows) * (side + BOX_GAP) - BOX_GAP + BANNER_MARGIN)
    place(image, stone_title(TITLE, TITLE_PIXEL, TITLE_DEPTH), BANNER_W / 2, top=TITLE_TOP - 40)
    for r, row in enumerate(rows):
        width = len(row) * side + (len(row) - 1) * BOX_GAP
        x = (BANNER_W - width) / 2
        y = BOX_TOP + r * (side + BOX_GAP)
        for i, box in enumerate(row):
            lit = i + r * BOXES_PER_ROW < len(bosses)
            layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
            layer.alpha_composite(box, (int(x + i * (side + BOX_GAP)), int(y)))
            if lit:
                image.alpha_composite(glow(layer, colour(bosses[i + r * BOXES_PER_ROW].glow), 18, 0.5))
            image.alpha_composite(layer)
    return image


def card(boss):
    """A boss's own picture: its name and three looks, on its biome's colours."""
    edge, centre = boss.box_colours()
    image = night(CARD_W, CARD_H, centre=centre)
    tint = colour(boss.glow)
    place(image, label(boss.name, CARD_TITLE_PIXEL, tint), CARD_W / 2, top=30)
    scale = min(fitted(boss, kind, CARD_BOX) for kind in boss.poses)
    baseline = CARD_H - 120
    slot = CARD_W / len(boss.poses)
    for i, kind in enumerate(boss.poses):
        cx = slot * (i + 0.5)
        layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
        place(layer, boss.picture(kind, scale), cx, bottom=baseline)
        image.alpha_composite(glow(layer, tint, 26, 0.55))
        image.alpha_composite(layer)
        place(image, label(kind, CARD_LABEL_PIXEL, WHITE), cx, top=baseline + 28)
    return image


def main(argv):
    if "--fresh" in argv and CACHE.exists():
        shutil.rmtree(CACHE)
    OUT.mkdir(exist_ok=True)
    bosses = [cls() for cls in BOSSES]
    banner(bosses).convert("RGB").save(OUT / "dross_banner.png")
    print(f"draw_page_art: {OUT / 'dross_banner.png'}")
    for boss in bosses:
        path = OUT / ("boss_" + boss.name.lower().replace(" ", "_") + ".png")
        card(boss).convert("RGB").save(path)
        print(f"draw_page_art: {path}")


if __name__ == "__main__":
    main(sys.argv[1:])
