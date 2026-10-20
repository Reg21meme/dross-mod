"""
The Huntsman's boss bars: ten designs for the user to pick from (October 19, 2026; CLAUDE.md step 14 part 4), each drawn
as Minecraft GUI pixel art (1 px outlines) so the previews and the game look exactly alike. Every design has the three
bars of the fight, and an angry look for the two of stage 2 (when one of the pair dies, the other's bar turns angry):

  The Wild Hunt     stage 1: him riding the Alpha Moose, one bar for both (frost white)
  The Huntsman      stage 2, on foot (glacier blue; angry: blood red)
  Alpha Moose       stage 2, riderless (antler amber; angry: blood red)

The designs (working names): 1 Antler Frame, 2 Hunting Horn, 3 Leather & Bone, 4 Cracked Ice, 5 Arrow, 6 Spear,
7 Fur Pelt, 8 Tracks in the Snow, 9 Skull Trophy, 10 Aurora Ice. The Ice Necromancer's colours (pale blues, white),
the hunt's (bone, leather, grey bearskin, spruce) and the moose's (brown, antler); never the Dross's electric blue.

Run from the project folder:  python tools/huntsman/bossbars.py   (--previews-only: only the pictures)

It writes:
  assets/dross/textures/gui/huntsman_bar/option_<n>.png      each design's texture: for each of its five looks, the
                                                             empty bar, the full bar and the decorations
  boss/client/HuntsmanBarStyles.java                         (generated) where each part is and where it's drawn
  boss-previews/rimefall/huntsman/bossbars/option_<n>.png    each design: stage 1 and stage 2 at several healths, and
                                                             the angry looks, 3x, as the game draws them
  boss-previews/rimefall/huntsman/bossbars/all_options.png   all ten side by side
  boss-previews/rimefall/huntsman/bossbars/index.html        a page with all of them

How the game draws a look (boss/client/DrossBossBars): Minecraft's bar sits at (x, y), 182 x 5, its name 9 pixels
above. Draw the empty bar; the full bar over it cut off at the health (its columns from fillLeft for
round(progress * fillWidth) pixels, at the same offset); the decorations on top; the name centred on x + 91 at nameY.
"""
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from barkit import BAR_W, CENTRE_X, FONT, NAME_ABOVE, blit, look_parts, night  # noqa: E402

ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
TEX_DIR = os.path.join(ASSETS, "textures", "gui", "huntsman_bar")
JAVA_PATH = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "client", "HuntsmanBarStyles.java")
PREVIEW_DIR = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "bossbars")

NAME_CENTRE_HALF = 40       # decorations within this many pixels of the centre keep the name above them
NAME_GAP = 1                # pixels of air between such decorations and the name's shadow
BAR_GAP = 5                 # pixels between one bar's art and the next bar's name (vanilla: 5)
MAX_ART_W, MAX_ART_H = 240, 34   # every look fits in this (checked on every build; the screen is at least 320 wide)
TEX_W = 256
PART_GAP = 2
PREVIEW_SCALE = 3

# The five looks of a design: (key, the bar, angry, its name in the game).
LOOKS = (
    ("wild_hunt", "wild", False, "The Wild Hunt"),
    ("huntsman", "hunter", False, "The Huntsman"),
    ("huntsman_angry", "hunter", True, "The Huntsman"),
    ("alpha_moose", "moose", False, "Alpha Moose"),
    ("alpha_moose_angry", "moose", True, "Alpha Moose"),
)

from bardesigns import DESIGNS, NAME_COLOURS, fill_key  # noqa: E402
from hunterbars import ROUND2  # noqa: E402

# Round 2 (the user's picks of October 19): design 9's Wild Hunt, design 1 or 3 for the Alpha Moose, and ten new bars
# for the Huntsman (11-20). Each round-2 preview shows a design as the fight would: under 9's Wild Hunt, over both
# moose bars.
WILD_PICK = 9
MOOSE_PICKS = (1, 3)


def make_design(number, title, fn):
    """Draws a design's five looks and lays their parts out in one texture."""
    looks = {}
    for key, bar, angry, _name in LOOKS:
        e, f, d = fn(bar, angry)
        looks[key] = look_parts(e, f, d)
    y = 0
    for key, *_ in LOOKS:
        for part in looks[key][:3]:
            part.u, part.v = 0, y
            y += part.h + PART_GAP
            if part.w > TEX_W:
                raise SystemExit(f"design {number}: a part is {part.w} wide (more than {TEX_W})")
    tex_h = 16
    while tex_h < y:
        tex_h *= 2
    tex = np.zeros((tex_h, TEX_W, 4), dtype=np.uint8)
    for key, *_ in LOOKS:
        for part in looks[key][:3]:
            tex[part.v:part.v + part.h, part.u:part.u + part.w] = part.img
    xs, ys, top_mid = [], [], 0
    own = getattr(fn, "own", None)          # a round-2 design: only its own looks set its size and name height
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
    return dict(number=number, name=title, tex=tex, looks=looks, name_y=name_y, height=height, art=(art_w, art_h),
                left=min(xs), right=max(xs), top=min(ys), bottom=max(ys))


# ---------------------------------------------------------------- previews


def render_stack(opt, entries, pad=10):
    """Bars one above the other as the game stacks them: entries are (look key, progress). Returns an RGB image (GUI
    pixels)."""
    width = max(BAR_W, opt["right"] - min(0, opt["left"])) + 2 * pad + 16
    first_shift = max(0, -opt["name_y"] - NAME_ABOVE)
    height = pad * 2 + len(entries) * opt["height"] + first_shift
    img = night(width, height, seed=opt["number"] * 7 + len(entries))
    bx = (width - BAR_W) // 2
    y = pad + (-opt["name_y"])
    for key, progress in entries:
        empty, full, deco, fl, fr = opt["looks"][key]
        blit(img, empty, bx + empty.x, y + empty.y)
        span = full.w - fl - fr
        n = int(round(max(0.0, min(1.0, progress)) * span))
        blit(img, full, bx + full.x, y + full.y, cols=(fl, fl + n))
        blit(img, deco, bx + deco.x, y + deco.y)
        bar, angry, name = next((b, a, nm) for k, b, a, nm in LOOKS if k == key)
        col = NAME_COLOURS[fill_key(bar, angry)]
        FONT.draw(img, name, bx + CENTRE_X - FONT.width(name) // 2, y + opt["name_y"], col)
        y += opt["height"]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def render_set(entries, pad=10):
    """Bars of different designs one above the other: entries are (design, look key, progress)."""
    width = max(max(BAR_W, o["right"] - min(0, o["left"])) for o, _k, _p in entries) + 2 * pad + 16
    first = entries[0][0]
    first_shift = max(0, -first["name_y"] - NAME_ABOVE)
    height = pad * 2 + sum(o["height"] for o, _k, _p in entries) + first_shift
    img = night(width, height, seed=len(entries) * 13 + first["number"])
    bx = (width - BAR_W) // 2
    y = pad + (-first["name_y"])
    prev = None
    for opt, key, progress in entries:
        if prev is not None:
            y += prev["height"] - (-prev["name_y"]) + (-opt["name_y"])
        empty, full, deco, fl, fr = opt["looks"][key]
        blit(img, empty, bx + empty.x, y + empty.y)
        n = int(round(max(0.0, min(1.0, progress)) * (full.w - fl - fr)))
        blit(img, full, bx + full.x, y + full.y, cols=(fl, fl + n))
        blit(img, deco, bx + deco.x, y + deco.y)
        bar, angry, name = next((b, a, nm) for k, b, a, nm in LOOKS if k == key)
        FONT.draw(img, name, bx + CENTRE_X - FONT.width(name) // 2, y + opt["name_y"], NAME_COLOURS[fill_key(bar, angry)])
        prev = opt
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def preview_round2(opt, picks, out_path):
    """A round-2 design as the fight would show it: stage 1's Wild Hunt (design 9), then stage 2 with this Huntsman bar
    over each Alpha Moose candidate (designs 1 and 3), and the angry looks."""
    wild = picks[WILD_PICK]
    m1, m3 = (picks[n] for n in MOOSE_PICKS)
    cells = [
        ("Stage 1: The Wild Hunt (design 9)", [(wild, "wild_hunt", 0.8)], ACCENT),
        (f"Stage 2: Huntsman {opt['number']}, Alpha Moose design 1", [(opt, "huntsman", 0.7), (m1, "alpha_moose", 0.45)], ACCENT),
        (f"Stage 2: Huntsman {opt['number']}, Alpha Moose design 3", [(opt, "huntsman", 0.7), (m3, "alpha_moose", 0.45)], ACCENT),
        ("Angry: the moose is dead", [(opt, "huntsman_angry", 0.55)], ANGRY_ACCENT),
        ("Angry: he is dead (moose 1, then 3)", [(m1, "alpha_moose_angry", 0.5), (m3, "alpha_moose_angry", 0.5)], ANGRY_ACCENT),
    ]
    ims = [(lab, big(render_set(entries)), col) for lab, entries, col in cells]
    gap, head, lab_h = 18, 92, 26
    colw = max(im.width for _l, im, _c in ims)
    left, right = ims[:3], ims[3:]
    H = head + max(sum(im.height + lab_h + gap for _l, im, _c in col) for col in (left, right)) + gap
    sheet = Image.new("RGB", (2 * colw + 3 * gap, H), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((gap, 12), f"Huntsman boss bar, round 2, design {opt['number']}: {opt['name']}", font=_font(32), fill=TEXT)
    dr.text((gap, 56), f"With your picks: design 9's Wild Hunt, and the Alpha Moose of design 1 or 3. Drawn "
                       f"{PREVIEW_SCALE}x as the game draws it.", font=_font(15, False), fill=DIM)
    for ci, column in enumerate((left, right)):
        x = gap + ci * (colw + gap)
        y = head
        for lab, im, col in column:
            dr.text((x, y), lab, font=_font(17), fill=col)
            sheet.paste(im, (x, y + lab_h))
            y += im.height + lab_h + gap
    sheet.save(out_path)


def overview_round2(opts, out_path):
    ims = [(opt, big(render_set([(opt, "huntsman", 0.7), (opt, "huntsman_angry", 0.4)], pad=8))) for opt in opts]
    gap, head, lab_h = 18, 70, 30
    colw = max(im.width for _o, im in ims)
    rowh = max(im.height for _o, im in ims) + lab_h + gap
    rows = (len(ims) + 1) // 2
    sheet = Image.new("RGB", (2 * colw + 3 * gap, head + rows * rowh + gap), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((gap, 14), "The Huntsman's bar, round 2: ten new designs (normal, then angry)", font=_font(30), fill=TEXT)
    for k, (opt, im) in enumerate(ims):
        x = gap + (k % 2) * (colw + gap)
        y = head + (k // 2) * rowh
        dr.text((x, y), f"{opt['number']}. {opt['name']}", font=_font(20), fill=ACCENT)
        sheet.paste(im, (x, y + lab_h))
    sheet.save(out_path)


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


def preview(opt, out_path):
    cells = [
        ("Stage 1: The Wild Hunt, full", [("wild_hunt", 1.0)], ACCENT),
        ("Stage 1, half", [("wild_hunt", 0.5)], ACCENT),
        ("Stage 1, nearly done", [("wild_hunt", 0.12)], ACCENT),
        ("Stage 2: both fresh", [("huntsman", 1.0), ("alpha_moose", 1.0)], ACCENT),
        ("Stage 2: hurt", [("huntsman", 0.55), ("alpha_moose", 0.3)], ACCENT),
        ("Angry: the moose is dead", [("huntsman_angry", 0.6)], ANGRY_ACCENT),
        ("Angry: he is dead", [("alpha_moose_angry", 0.45)], ANGRY_ACCENT),
    ]
    ims = [(lab, big(render_stack(opt, entries)), col) for lab, entries, col in cells]
    gap, head, lab_h = 18, 92, 26
    colw = max(im.width for _l, im, _c in ims)
    left = ims[:3]
    right = ims[3:]
    H1 = sum(im.height + lab_h + gap for _l, im, _c in left)
    H2 = sum(im.height + lab_h + gap for _l, im, _c in right)
    W = 2 * colw + 3 * gap
    H = head + max(H1, H2) + gap
    sheet = Image.new("RGB", (W, H), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((gap, 12), f"Huntsman boss bar, design {opt['number']}: {opt['name']}", font=_font(32), fill=TEXT)
    dr.text((gap, 56), f"Drawn {PREVIEW_SCALE}x as the game draws it (one square = one GUI pixel). Stage 1 is one bar "
                       f"for both; stage 2 shows two; the survivor's bar turns angry when the other dies.",
            font=_font(15, False), fill=DIM)
    for ci, column in enumerate((left, right)):
        x = gap + ci * (colw + gap)
        y = head
        for lab, im, col in column:
            dr.text((x, y), lab, font=_font(17), fill=col)
            sheet.paste(im, (x, y + lab_h))
            y += im.height + lab_h + gap
    sheet.save(out_path)
    return sheet


def overview(opts, out_path):
    ims = []
    for opt in opts:
        im = big(render_stack(opt, [("wild_hunt", 0.8), ("huntsman", 0.65), ("alpha_moose", 0.4)], pad=8))
        ims.append((opt, im))
    gap, head, lab_h = 18, 70, 30
    colw = max(im.width for _o, im in ims)
    rowh = max(im.height for _o, im in ims) + lab_h + gap
    cols = 2
    rows = (len(ims) + cols - 1) // cols
    W = cols * colw + (cols + 1) * gap
    H = head + rows * rowh + gap
    sheet = Image.new("RGB", (W, H), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((gap, 14), "The Huntsman's boss bars: ten designs (stage 1, then stage 2's two bars)", font=_font(30), fill=TEXT)
    for k, (opt, im) in enumerate(ims):
        x = gap + (k % cols) * (colw + gap)
        y = head + (k // cols) * rowh
        dr.text((x, y), f"{opt['number']}. {opt['name']}", font=_font(20), fill=ACCENT)
        sheet.paste(im, (x, y + lab_h))
    sheet.save(out_path)


def write_page(opts, round2=()):
    rows = ["<!doctype html><meta charset='utf-8'><title>Huntsman boss bars</title>",
            "<style>body{background:#14171c;color:#ecf2f8;font-family:Segoe UI,Arial,sans-serif;margin:24px}"
            "h1{font-size:26px}h2{color:#96d2f0;margin-top:30px}p{color:#9fb0c0;max-width:900px}"
            "img{image-rendering:pixelated;display:block;margin:8px 0;max-width:100%}</style>",
            "<h1>The Huntsman's boss bars: ten designs</h1>",
            "<p>Each design has three bars: <b>The Wild Hunt</b> (stage 1, him riding the Alpha Moose, one bar for "
            "both), then <b>The Huntsman</b> and <b>Alpha Moose</b> (stage 2, two bars); when one of them dies the "
            "other's bar turns angry (red). Drawn 3x, exactly as the game draws them. In the game: "
            "<code>/dross showcase bossbars huntsman</code>.</p>",
            "<img src='all_options.png'>"]
    if round2:
        head = ["<h1>Round 2: the Huntsman's bar</h1>",
                "<p>Your picks from round 1: design 9 for the Wild Hunt, design 1 or 3 for the Alpha Moose (both "
                "shown). Ten new bars for the Huntsman, each shown with them. In the game: "
                "<code>/dross showcase bossbars huntsman 11</code> (to 20).</p>",
                "<img src='round2_all.png'>"]
        head += [f"<h2>{o['number']}. {o['name']}</h2><img src='round2_{o['number']}.png'>" for o in round2]
        head.append("<h1>Round 1</h1>")
        rows[2:2] = head
    for opt in opts:
        rows.append(f"<h2>{opt['number']}. {opt['name']}</h2><img src='option_{opt['number']}.png'>")
    with open(os.path.join(PREVIEW_DIR, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(rows) + "\n")


# ---------------------------------------------------------------- the Java file


JAVA_HEAD = """package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * The Huntsman's boss bar designs, written by {@code tools/huntsman/bossbars.py} (don't edit by hand: change the
 * drawings there and run it again). Ten designs for the user to pick from, each with five looks: the Wild Hunt (stage 1),
 * the Huntsman and the Alpha Moose (stage 2), and those two angry (when the other has died). Each design's texture holds,
 * for every look, the empty bar, the full bar and the decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw a look: blit the
 * empty bar; blit the full bar over it, cut off at the health (its columns from {@link Look#fillLeft()} for
 * {@code round(progress * fillWidth())} pixels, at the same offset); blit the decorations on top; draw the name centred
 * on x + 91 at y + {@link Style#nameY()}; and leave {@link Style#height()} pixels before the next bar.
 */
public final class HuntsmanBarStyles
{
    /** One piece of a bar: where it is in the texture (u, v), its size, and where it's drawn from the bar's x, y. */
    public record Part(int u, int v, int width, int height, int x, int y)
    {
    }

    /** One look: the empty bar, the full bar (the same size and place), the decorations, and where the fill runs. */
    public record Look(Part empty, Part full, Part decorations, int fillLeft, int fillRight)
    {
        /** How many pixels wide the fill is at full health (182, like Minecraft's bar). */
        public int fillWidth()
        {
            return full.width() - fillLeft - fillRight;
        }
    }

    /**
     * One design.
     *
     * @param number the design's number (1 to 10; round 2's Huntsman bars 11 to 20)
     * @param name   its working name
     */
    public record Style(int number, String name, ResourceLocation texture, int textureWidth, int textureHeight, Look wildHunt,
                       Look huntsman, Look huntsmanAngry, Look alphaMoose, Look alphaMooseAngry, int nameY, int height)
    {
        /** The look of one bar: "wild_hunt", "huntsman", "huntsman_angry", "alpha_moose" or "alpha_moose_angry". */
        public Look look(String bar)
        {
            return switch (bar)
            {
                case "huntsman" -> this.huntsman;
                case "huntsman_angry" -> this.huntsmanAngry;
                case "alpha_moose" -> this.alphaMoose;
                case "alpha_moose_angry" -> this.alphaMooseAngry;
                default -> this.wildHunt;
            };
        }
    }

"""


def _part(p):
    return f"new Part({p.u}, {p.v}, {p.w}, {p.h}, {p.x}, {p.y})"


def _look(look):
    e, f, d, fl, fr = look
    return f"new Look({_part(e)}, {_part(f)}, {_part(d)}, {fl}, {fr})"


def write_java(opts):
    body = [JAVA_HEAD]
    entries = []
    for opt in opts:
        L = opt["looks"]
        entries.append(
            f"            new Style({opt['number']}, \"{opt['name']}\",\n"
            f"                    new ResourceLocation(Dross.MODID, \"textures/gui/huntsman_bar/option_{opt['number']}.png\"), "
            f"{TEX_W}, {opt['tex'].shape[0]},\n"
            f"                    {_look(L['wild_hunt'])},\n"
            f"                    {_look(L['huntsman'])},\n"
            f"                    {_look(L['huntsman_angry'])},\n"
            f"                    {_look(L['alpha_moose'])},\n"
            f"                    {_look(L['alpha_moose_angry'])},\n"
            f"                    {opt['name_y']}, {opt['height']})")
    body.append("    /** Every design, in order. */\n    public static final List<Style> ALL = List.of(\n")
    body.append(",\n".join(entries) + ");\n\n")
    body.append("""    /** The design with this number (1 to 20), or the first. */
    public static Style byNumber(int number)
    {
        for (Style style : ALL)
        {
            if (style.number() == number)
            {
                return style;
            }
        }
        return ALL.get(0);
    }

    private HuntsmanBarStyles() {}
}
""")
    os.makedirs(os.path.dirname(JAVA_PATH), exist_ok=True)
    with open(JAVA_PATH, "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(body))


# ---------------------------------------------------------------- everything


def build_all(write_files=True, only=None):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    opts = []
    for number, title, fn in DESIGNS + ROUND2:
        if only and number not in only:
            continue
        opt = make_design(number, title, fn)
        opts.append(opt)
        if number <= 10:
            preview(opt, os.path.join(PREVIEW_DIR, f"option_{number}.png"))
        print(f"  {number:2d}. {title:20s} art {opt['art'][0]} x {opt['art'][1]}, name at {opt['name_y']}, "
              f"height {opt['height']}, texture {TEX_W} x {opt['tex'].shape[0]}")
    picks = {o["number"]: o for o in opts}
    round2 = [o for o in opts if o["number"] > 10]
    for opt in round2:
        if WILD_PICK in picks and all(m in picks for m in MOOSE_PICKS):
            preview_round2(opt, picks, os.path.join(PREVIEW_DIR, f"round2_{opt['number']}.png"))
    if only:
        return opts
    overview([o for o in opts if o["number"] <= 10], os.path.join(PREVIEW_DIR, "all_options.png"))
    overview_round2(round2, os.path.join(PREVIEW_DIR, "round2_all.png"))
    write_page([o for o in opts if o["number"] <= 10], round2)
    if write_files:
        os.makedirs(TEX_DIR, exist_ok=True)
        for opt in opts:
            Image.fromarray(opt["tex"], "RGBA").save(os.path.join(TEX_DIR, f"option_{opt['number']}.png"))
        write_java(opts)
    return opts


if __name__ == "__main__":
    only = [int(a) for a in sys.argv[1:] if a.isdigit()]
    build_all(write_files="--previews-only" not in sys.argv, only=only or None)
