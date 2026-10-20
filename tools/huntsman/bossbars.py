"""
The Huntsman's boss bars (CLAUDE.md step 14 part 4; the user's picks, October 19-20, 2026), drawn as Minecraft GUI
pixel art (1 px outlines) so the previews and the game look exactly alike:

  The Wild Hunt     stage 1, him riding the Alpha Moose, one bar for both: design 9, Skull Trophy (frost white)
  The Huntsman      stage 2, on foot: design 11, Wolf & Antlers (glacier blue; angry: blood red)
  Alpha Moose       stage 2, riderless: design 1, Antler Frame (antler amber; angry: blood red)

When one of the pair dies in stage 2, the other's bar turns to its angry look. The other candidates (round 1's 2-8 and
10, round 2's 12-20) were removed on October 20, 2026. Each bar keeps the spacing it had in the showcase the user
picked from: its name's height and the room it takes come from its own design.

Run from the project folder:  python tools/huntsman/bossbars.py   (--previews-only: only the pictures)

It writes:
  assets/dross/textures/gui/huntsman_bar.png              every look's parts: the empty bar, the full bar, the decorations
  boss/client/HuntsmanBarArt.java                         (generated) where each part is and where it's drawn
  boss-previews/rimefall/huntsman/bossbars/bars.png       stage 1, stage 2 and the angry looks at several healths, 3x
  boss-previews/rimefall/huntsman/bossbars/index.html     a page with it

How the game draws a look (boss/client/DrossBossBars): Minecraft's bar sits at (x, y), 182 x 5, its name 9 pixels
above. Move it down by max(0, -nameY - 9) so its name stays on the screen; draw the empty bar; the full bar over it cut
off at the health (its columns from fillLeft for round(progress * fillWidth) pixels, at the same offset); the
decorations on top; the name centred on x + 91 at nameY; then leave height pixels before the next bar.
"""
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from barkit import BAR_W, CENTRE_X, FONT, NAME_ABOVE, blit, look_parts, night  # noqa: E402
from bardesigns import NAME_COLOURS, fill_key, t_antlers, t_skull  # noqa: E402
from hunterbars import h_wolf, huntsman_only  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
TEX_PATH = os.path.join(ASSETS, "textures", "gui", "huntsman_bar.png")
JAVA_PATH = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "client", "HuntsmanBarArt.java")
PREVIEW_DIR = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "bossbars")

NAME_CENTRE_HALF = 40       # decorations within this many pixels of the centre keep the name above them
NAME_GAP = 1                # pixels of air between such decorations and the name's shadow
BAR_GAP = 5                 # pixels between one bar's art and the next bar's name (vanilla: 5)
MAX_ART_W, MAX_ART_H = 240, 34   # every look fits in this (checked on every build; the screen is at least 320 wide)
TEX_W = 256
PART_GAP = 2
PREVIEW_SCALE = 3
VANILLA_FIRST_Y = 12        # where Minecraft draws the first boss bar

# Every look of a design: (key, the bar, angry, its name in the game).
LOOKS = (
    ("wild_hunt", "wild", False, "The Wild Hunt"),
    ("huntsman", "hunter", False, "The Huntsman"),
    ("huntsman_angry", "hunter", True, "The Huntsman"),
    ("alpha_moose", "moose", False, "Alpha Moose"),
    ("alpha_moose_angry", "moose", True, "Alpha Moose"),
)

# The picks: which design each look comes from (its number, its working name, its drawing).
DESIGNS = {9: ("Skull Trophy", t_skull), 11: ("Wolf & Antlers", huntsman_only(h_wolf)), 1: ("Antler Frame", t_antlers)}
PICKS = {"wild_hunt": 9, "huntsman": 11, "huntsman_angry": 11, "alpha_moose": 1, "alpha_moose_angry": 1}


def make_design(number, title, fn):
    """Draws a design's looks and works out where its name sits and how much room it takes (as the showcase the user
    picked from did: over all its looks, or for round 2's Huntsman bar over its own)."""
    looks = {}
    for key, bar, angry, _name in LOOKS:
        e, f, d = fn(bar, angry)
        looks[key] = look_parts(e, f, d)
    xs, ys, top_mid = [], [], 0
    own = getattr(fn, "own", None)
    for key, *_ in LOOKS:
        if own and key not in own:
            continue
        for part in looks[key][:3]:
            if part.img[..., 3].any():
                rows, cols = np.nonzero(part.img[..., 3])
                xs += [part.x + int(cols.min()), part.x + int(cols.max()) + 1]
                ys += [part.y + int(rows.min()), part.y + int(rows.max()) + 1]
                for r, c in zip(rows, cols):
                    if abs(part.x + c - CENTRE_X) <= NAME_CENTRE_HALF:
                        top_mid = min(top_mid, part.y + r)
    for key, *_ in LOOKS:
        fl, fr = looks[key][3], looks[key][4]
        if looks[key][1].w - fl - fr != BAR_W:
            raise SystemExit(f"design {number} {key}: the fill is {looks[key][1].w - fl - fr} wide, not {BAR_W}")
    art_w, art_h = max(xs) - min(xs), max(ys) - min(ys)
    if art_w > MAX_ART_W or art_h > MAX_ART_H:
        raise SystemExit(f"design {number} {title}: the art is {art_w} x {art_h} (more than {MAX_ART_W} x {MAX_ART_H})")
    name_y = min(-NAME_ABOVE, top_mid - NAME_ABOVE - (NAME_GAP if top_mid < -1 else 0))
    height = (max(ys) - name_y) + BAR_GAP
    return dict(number=number, name=title, looks=looks, name_y=int(name_y), height=int(height))


def make_bars():
    """The five looks of the fight, each from its pick, and the one texture holding their parts."""
    designs = {n: make_design(n, title, fn) for n, (title, fn) in DESIGNS.items()}
    bars = {}
    v = 0
    for key, bar, angry, name in LOOKS:
        d = designs[PICKS[key]]
        empty, full, deco, fl, fr = d["looks"][key]
        for part in (empty, full, deco):
            if part.w > TEX_W:
                raise SystemExit(f"{key}: a part is {part.w} wide (more than {TEX_W})")
            part.u, part.v = 0, v
            v += part.h + PART_GAP
        bars[key] = dict(key=key, bar=bar, angry=angry, name=name, design=d["number"], design_name=d["name"],
                         parts=(empty, full, deco), fill_left=fl, fill_right=fr, name_y=d["name_y"], height=d["height"])
    tex_h = 16
    while tex_h < v:
        tex_h *= 2
    tex = np.zeros((tex_h, TEX_W, 4), dtype=np.uint8)
    for b in bars.values():
        for part in b["parts"]:
            tex[part.v:part.v + part.h, part.u:part.u + part.w] = part.img
    return bars, tex


# ---------------------------------------------------------------- previews


def render(bars, entries, pad=10):
    """Bars one above the other the way the game stacks them (DrossBossBars): entries are (look key, progress).
    Returns an RGB image in GUI pixels."""
    shown = [bars[k] for k, _p in entries]
    lefts = [min(p.x for p in b["parts"]) for b in shown]
    rights = [max(p.x + p.w for p in b["parts"]) for b in shown]
    width = max(BAR_W, max(rights) - min(0, min(lefts))) + 2 * pad + 16
    first_y = max(VANILLA_FIRST_Y, -shown[0]["name_y"] + pad)
    last = shown[-1]
    height = first_y + sum(b["height"] for b in shown[:-1]) + max(0, -last["name_y"] - NAME_ABOVE) \
        + max(p.y + p.h for p in last["parts"]) + pad
    img = night(width, height, seed=len(entries) * 13 + shown[0]["design"])
    bx = (width - BAR_W) // 2
    bar_y = first_y
    for b, (_k, progress) in zip(shown, entries):
        y = bar_y + max(0, -b["name_y"] - NAME_ABOVE)
        empty, full, deco = b["parts"]
        blit(img, empty, bx + empty.x, y + empty.y)
        n = int(round(max(0.0, min(1.0, progress)) * (full.w - b["fill_left"] - b["fill_right"])))
        blit(img, full, bx + full.x, y + full.y, cols=(b["fill_left"], b["fill_left"] + n))
        blit(img, deco, bx + deco.x, y + deco.y)
        FONT.draw(img, b["name"], bx + CENTRE_X - FONT.width(b["name"]) // 2, y + b["name_y"],
                  NAME_COLOURS[fill_key(b["bar"], b["angry"])])
        bar_y += b["height"]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def _font(size, bold=True):
    for name in (("segoeuib.ttf" if bold else "segoeui.ttf"), ("arialbd.ttf" if bold else "arial.ttf")):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


SHEET_BG = (20, 23, 28)
TEXT = (236, 242, 248)
DIM = (160, 172, 186)
ACCENT = (150, 210, 240)
ANGRY_ACCENT = (240, 120, 100)


def big(im):
    return im.resize((im.width * PREVIEW_SCALE, im.height * PREVIEW_SCALE), Image.NEAREST)


def preview(bars, out_path):
    cells = [
        ("Stage 1: The Wild Hunt, full", [("wild_hunt", 1.0)], ACCENT),
        ("Stage 1, half", [("wild_hunt", 0.5)], ACCENT),
        ("Stage 1, nearly done", [("wild_hunt", 0.12)], ACCENT),
        ("Stage 2: both fresh", [("huntsman", 1.0), ("alpha_moose", 1.0)], ACCENT),
        ("Stage 2: hurt", [("huntsman", 0.55), ("alpha_moose", 0.3)], ACCENT),
        ("Angry: the moose is dead", [("huntsman_angry", 0.6)], ANGRY_ACCENT),
        ("Angry: he is dead", [("alpha_moose_angry", 0.45)], ANGRY_ACCENT),
    ]
    ims = [(lab, big(render(bars, entries)), col) for lab, entries, col in cells]
    gap, head, lab_h = 18, 92, 26
    colw = max(im.width for _l, im, _c in ims)
    left, right = ims[:3], ims[3:]
    H = head + max(sum(im.height + lab_h + gap for _l, im, _c in col) for col in (left, right)) + gap
    sheet = Image.new("RGB", (2 * colw + 3 * gap, H), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((gap, 12), "The Huntsman's boss bars", font=_font(32), fill=TEXT)
    dr.text((gap, 56), f"The user's picks: the Wild Hunt design 9, the Huntsman design 11, the Alpha Moose design 1, each "
                       f"angry look blood red. Drawn {PREVIEW_SCALE}x as the game draws them.", font=_font(15, False), fill=DIM)
    for ci, column in enumerate((left, right)):
        x = gap + ci * (colw + gap)
        y = head
        for lab, im, col in column:
            dr.text((x, y), lab, font=_font(17), fill=col)
            sheet.paste(im, (x, y + lab_h))
            y += im.height + lab_h + gap
    sheet.save(out_path)


def write_page():
    rows = ["<!doctype html><meta charset='utf-8'><title>Huntsman boss bars</title>",
            "<style>body{background:#14171c;color:#ecf2f8;font-family:Segoe UI,Arial,sans-serif;margin:24px}"
            "h1{font-size:26px}p{color:#9fb0c0;max-width:900px}"
            "img{image-rendering:pixelated;display:block;margin:8px 0;max-width:100%}</style>",
            "<h1>The Huntsman's boss bars</h1>",
            "<p>Stage 1 shows one bar, <b>The Wild Hunt</b> (him riding the Alpha Moose: design 9, Skull Trophy). Stage 2 "
            "shows two, <b>The Huntsman</b> (design 11, Wolf &amp; Antlers) and <b>Alpha Moose</b> (design 1, Antler "
            "Frame); when one of them dies the other's bar turns blood red. Drawn 3x, exactly as the game draws them. "
            "In the game: <code>/dross showcase bossbars huntsman</code> (and <code>... angry</code>).</p>",
            "<img src='bars.png'>"]
    with open(os.path.join(PREVIEW_DIR, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(rows) + "\n")


# ---------------------------------------------------------------- the Java file


JAVA_HEAD = """package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

/**
 * The Huntsman's boss bars, written by {@code tools/huntsman/bossbars.py} (don't edit by hand: change the drawings there
 * and run it again). The user's picks: the Wild Hunt (stage 1) design 9, Skull Trophy; the Huntsman (stage 2) design 11,
 * Wolf &amp; Antlers; the Alpha Moose (stage 2) design 1, Antler Frame; each of the last two with its blood-red angry look
 * (when the other has died). The texture holds every look's empty bar, full bar and decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw a look: move it
 * down by {@code max(0, -nameY - 9)} so its name stays on the screen; blit the empty bar; blit the full bar over it,
 * cut off at the health (its columns from {@link Look#fillLeft()} for {@code round(progress * fillWidth())} pixels, at
 * the same offset); blit the decorations on top; draw the name centred on x + 91 at {@link Look#nameY()}; and leave
 * {@link Look#height()} pixels before the next bar.
 */
public final class HuntsmanBarArt
{
    /** One piece of a bar: where it is in the texture (u, v), its size, and where it's drawn from the bar's x, y. */
    public record Part(int u, int v, int width, int height, int x, int y)
    {
    }

    /** One look: the empty bar, the full bar (the same size and place), the decorations, where the fill runs, where its
     * name sits and how much room it takes. */
    public record Look(Part empty, Part full, Part decorations, int fillLeft, int fillRight, int nameY, int height)
    {
        /** How many pixels wide the fill is at full health (182, like Minecraft's bar). */
        public int fillWidth()
        {
            return full.width() - fillLeft - fillRight;
        }
    }

"""

DOCS = {
    "wild_hunt": "Stage 1: The Wild Hunt, him riding the Alpha Moose, one bar for both (design 9, Skull Trophy).",
    "huntsman": "Stage 2: The Huntsman on foot (design 11, Wolf &amp; Antlers).",
    "huntsman_angry": "Stage 2: The Huntsman, angry (the moose has died).",
    "alpha_moose": "Stage 2: the Alpha Moose, riderless (design 1, Antler Frame).",
    "alpha_moose_angry": "Stage 2: the Alpha Moose, angry (he has died).",
}


def _part(p):
    return f"new Part({p.u}, {p.v}, {p.w}, {p.h}, {p.x}, {p.y})"


def write_java(bars, tex_h):
    body = [JAVA_HEAD,
            "    public static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, \"textures/gui/huntsman_bar.png\");\n",
            f"    public static final int TEXTURE_WIDTH = {TEX_W};\n",
            f"    public static final int TEXTURE_HEIGHT = {tex_h};\n\n"]
    for key, *_ in LOOKS:
        b = bars[key]
        e, f, d = b["parts"]
        body.append(f"    /** {DOCS[key]} */\n"
                    f"    public static final Look {key.upper()} = new Look({_part(e)},\n"
                    f"            {_part(f)},\n"
                    f"            {_part(d)}, {b['fill_left']}, {b['fill_right']}, {b['name_y']}, {b['height']});\n")
    cases = "".join(f"            case \"{key}\" -> {key.upper()};\n" for key, *_ in LOOKS)
    body.append(f"""
    /** The look a bar's key names ("wild_hunt", "huntsman", "huntsman_angry", "alpha_moose", "alpha_moose_angry"), or
     * null. */
    public static Look look(String key)
    {{
        return switch (key)
        {{
{cases}            default -> null;
        }};
    }}

    private HuntsmanBarArt() {{}}
}}
""")
    with open(JAVA_PATH, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(body))


# ---------------------------------------------------------------- everything


def build_all(write_files=True):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    bars, tex = make_bars()
    for key, *_ in LOOKS:
        b = bars[key]
        print(f"  {key:18s} design {b['design']:2d} {b['design_name']:15s} name at {b['name_y']}, height {b['height']}")
    preview(bars, os.path.join(PREVIEW_DIR, "bars.png"))
    write_page()
    if write_files:
        Image.fromarray(tex, "RGBA").save(TEX_PATH)
        write_java(bars, tex.shape[0])
        print(f"  texture {TEX_W} x {tex.shape[0]}")
    return bars


if __name__ == "__main__":
    build_all(write_files="--previews-only" not in sys.argv)
