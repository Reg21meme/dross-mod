"""
The Emberdactyl's boss bar, in two looks:

  adult   "Emberdactyl: The Sky That Burns"       (lang key boss.dross.emberdactyl.bar.adult)
  young   "Emberdactyl: The Ember That Remains"   (boss.dross.emberdactyl.bar.young), its young reborn: hotter

The user's pick (October 29, 2026), of ten candidate designs: "combine the wings of 2 with the volcano of 3", the
Winged Volcano: design 3's volcano on the middle of the bar (smoking for the adult, erupting for the young) and design
2's burning wings spread out from its ends. The other designs, their textures, previews and showcase pages were removed
the same day. It's drawn in tools/fire/emberbars.py with the Huntsman's pixel tools (tools/huntsman/barkit.py: Minecraft
GUI pixel art, 1 px outlines, Minecraft's own font in the previews), so the previews and the game look exactly alike.

Run from the project folder:  python tools/fire/bossbars.py   (--previews-only: only the pictures)

It writes:
  assets/dross/textures/gui/emberdactyl_bar.png                    both looks' parts: empty bar, full bar, decorations
  boss/client/EmberdactylBarArt.java                               (generated) where each part is and where it's drawn
  boss-previews/cinder_wastes/emberdactyl/bossbars/bars.png        both looks at several healths, drawn 3x
  boss-previews/cinder_wastes/emberdactyl/bossbars/index.html      a page with it

How the game draws a look (boss/client/DrossBossBars, like the Huntsman's): Minecraft's bar sits at (x, y), 182 x 5, its
name 9 pixels above. Move it down by max(0, -nameY - 9) so its name stays on the screen; draw the empty bar; the full
bar over it cut off at the health (its columns from fillLeft for round(progress * fillWidth) pixels, at the same
offset); the decorations on top; the name centred on x + 91 at nameY; then leave height pixels before the next bar.
The two looks share their name's height and the room they take, so changing look never moves anything.
"""
import os
import random
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "huntsman"))     # barkit: the shared boss bar pixel tools
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

from barkit import BAR_W, CENTRE_X, FONT, NAME_ABOVE, blit, look_parts  # noqa: E402
from emberbars import NAME_COLOURS, TITLE, winged_volcano  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
TEX_PATH = os.path.join(ASSETS, "textures", "gui", "emberdactyl_bar.png")
JAVA_PATH = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "client",
                         "EmberdactylBarArt.java")
PREVIEW_DIR = os.path.join(ROOT, "boss-previews", "cinder_wastes", "emberdactyl", "bossbars")

NAME_GAP = 1               # pixels of air between decorations under the name and the name's shadow
BAR_GAP = 5                 # pixels between one bar's art and the next bar's name (vanilla: 5)
MAX_ART_W, MAX_ART_H = 240, 34   # every look fits in this (checked on every build; the screen is at least 320 wide)
TEX_W = 256
PART_GAP = 2
PREVIEW_SCALE = 3
VANILLA_FIRST_Y = 12        # where Minecraft draws the first boss bar

LOOKS = (("adult", False), ("young", True))
FIGHT_NAMES = {False: "Emberdactyl: The Sky That Burns", True: "Emberdactyl: The Ember That Remains"}


def make_design(title, fn):
    """Draws the bar's two looks and works out where their name sits (above any decoration under the wider of the two
    names) and how much room they take."""
    looks = {young: look_parts(*fn(young)) for _key, young in LOOKS}
    half = max(FONT.width(s) for s in FIGHT_NAMES.values()) // 2 + 2
    xs, ys, top_mid = [], [], 0
    for _key, young in LOOKS:
        e, f, d, fl, fr = looks[young]
        if f.w - fl - fr != BAR_W:
            raise SystemExit(f"{title}: the fill is {f.w - fl - fr} wide, not {BAR_W}")
        for part in (e, f, d):
            if part.img[..., 3].any():
                rows, cols = np.nonzero(part.img[..., 3])
                xs += [part.x + int(cols.min()), part.x + int(cols.max()) + 1]
                ys += [part.y + int(rows.min()), part.y + int(rows.max()) + 1]
                for r, c in zip(rows, cols):
                    if abs(part.x + c + 0.5 - CENTRE_X) <= half:
                        top_mid = min(top_mid, part.y + int(r))
    art_w, art_h = max(xs) - min(xs), max(ys) - min(ys)
    if art_w > MAX_ART_W or art_h > MAX_ART_H:
        raise SystemExit(f"{title}: the art is {art_w} x {art_h} (more than {MAX_ART_W} x {MAX_ART_H})")
    name_y = min(-NAME_ABOVE, top_mid - NAME_ABOVE - (NAME_GAP if top_mid < -1 else 0))
    height = (max(ys) - name_y) + BAR_GAP
    return dict(name=title, looks=looks, name_y=int(name_y), height=int(height), art=(art_w, art_h))


def make_bars():
    """The bar's two looks, and the one texture holding their parts."""
    dsg = make_design(TITLE, winged_volcano)
    v = 0
    for _key, young in LOOKS:
        for part in dsg["looks"][young][:3]:
            if part.w > TEX_W:
                raise SystemExit(f"{TITLE}: a part is {part.w} wide (more than {TEX_W})")
            part.u, part.v = 0, v
            v += part.h + PART_GAP
    tex_h = 16
    while tex_h < v:
        tex_h *= 2
    tex = np.zeros((tex_h, TEX_W, 4), dtype=np.uint8)
    for _key, young in LOOKS:
        for part in dsg["looks"][young][:3]:
            tex[part.v:part.v + part.h, part.u:part.u + part.w] = part.img
    return dsg, tex


# ---------------------------------------------------------------- previews


def ember_night(w, h, seed=7):
    """A dark volcanic night behind the bars (GUI pixels): a smoky sky glowing faintly red low down, ash and embers."""
    img = np.zeros((h, w, 3))
    top, bottom = np.array((22, 16, 22)), np.array((34, 16, 14))
    for y in range(h):
        img[y] = top + (bottom - top) * (y / max(1, h - 1))
    rng = random.Random(seed)
    for _ in range(w * h // 260):
        x, y = rng.randrange(w), rng.randrange(h)
        if rng.random() < 0.25:
            img[y, x] = rng.choice(((150, 60, 24), (190, 92, 30), (120, 40, 20)))
        else:
            b = rng.choice((52, 66, 80))
            img[y, x] = (b + 6, b, b - 2)
    return img


def render(entries, pad=10, seed=1):
    """Bars one above the other the way the game stacks them (DrossBossBars): entries are (design, young, name,
    progress). Returns an RGB image in GUI pixels."""
    shown = [(dsg, young) for dsg, young, _n, _p in entries]
    lefts = [min(p.x for p in dsg["looks"][young][:3]) for dsg, young in shown]
    rights = [max(p.x + p.w for p in dsg["looks"][young][:3]) for dsg, young in shown]
    width = max(BAR_W, max(rights) - min(0, min(lefts))) + 2 * pad + 16
    first_y = max(VANILLA_FIRST_Y, -shown[0][0]["name_y"] + pad)
    last, last_young = shown[-1]
    height = first_y + sum(dsg["height"] for dsg, _y in shown[:-1]) + max(0, -last["name_y"] - NAME_ABOVE) \
        + max(p.y + p.h for p in last["looks"][last_young][:3]) + pad
    img = ember_night(width, height, seed=seed)
    bx = (width - BAR_W) // 2
    bar_y = first_y
    for dsg, young, name, progress in entries:
        e, f, d, fl, fr = dsg["looks"][young]
        y = bar_y + max(0, -dsg["name_y"] - NAME_ABOVE)
        blit(img, e, bx + e.x, y + e.y)
        n = int(round(max(0.0, min(1.0, progress)) * (f.w - fl - fr)))
        blit(img, f, bx + f.x, y + f.y, cols=(fl, fl + n))
        blit(img, d, bx + d.x, y + d.y)
        FONT.draw(img, name, bx + CENTRE_X - FONT.width(name) // 2, y + dsg["name_y"], NAME_COLOURS[young])
        bar_y += dsg["height"]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def _font(size, bold=True):
    for name in (("segoeuib.ttf" if bold else "segoeui.ttf"), ("arialbd.ttf" if bold else "arial.ttf")):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


SHEET_BG = (22, 18, 20)
TEXT = (248, 238, 228)
DIM = (186, 168, 156)
ACCENT = (255, 170, 80)
YOUNG_ACCENT = (255, 220, 120)
HEALTHS = (("full", 1.0), ("half", 0.5), ("nearly gone", 0.12))      # the preview's rows


def big(im, k=PREVIEW_SCALE):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def preview(dsg, out_path):
    """The adult's look (left) and the young's (right) full, half and nearly gone, and the showcase's two bars stacked
    as the game stacks them, with the fight's names."""
    def one(young, progress, seed):
        return big(render([(dsg, young, FIGHT_NAMES[young], progress)], seed=seed))
    columns = [[(f"The adult, {label}", one(False, p, 3 + k), ACCENT) for k, (label, p) in enumerate(HEALTHS)],
               [(f"The young reborn, {label}", one(True, p, 53 + k), YOUNG_ACCENT) for k, (label, p) in
                enumerate(HEALTHS)]]
    stack = big(render([(dsg, False, FIGHT_NAMES[False], 0.8), (dsg, True, FIGHT_NAMES[True], 0.45)], seed=99))
    gap, head, lab_h = 18, 96, 28
    colw = max(im.width for col in columns for _l, im, _c in col)
    col_h = max(sum(im.height + lab_h + gap for _l, im, _c in col) for col in columns)
    W = max(2 * colw + 3 * gap, stack.width + 2 * gap)
    out = Image.new("RGB", (W, head + col_h + lab_h + stack.height + 2 * gap), SHEET_BG)
    dr = ImageDraw.Draw(out)
    dr.text((gap, 12), f"The Emberdactyl's boss bar: {dsg['name']}", font=_font(32), fill=TEXT)
    dr.text((gap, 58), f"The user's pick: the wings of design 2 with the volcano of design 3. Left: the adult (The Sky "
                       f"That Burns). Right: its young reborn (The Ember That Remains), hotter, its volcano erupting. "
                       f"Drawn {PREVIEW_SCALE}x as the game draws them.", font=_font(15, False), fill=DIM)
    for ci, column in enumerate(columns):
        x, y = gap + ci * (colw + gap), head
        for lab, im, col in column:
            dr.text((x, y), lab, font=_font(18), fill=col)
            out.paste(im, (x, y + lab_h))
            y += im.height + lab_h + gap
    y = head + col_h
    dr.text((gap, y), "/dross showcase bossbars emberdactyl: both, stacked as the game stacks them", font=_font(18),
            fill=ACCENT)
    out.paste(stack, (gap, y + lab_h))
    out.save(out_path)


def write_page(dsg):
    rows = ["<!doctype html><meta charset='utf-8'><title>Emberdactyl boss bar</title>",
            "<style>body{background:#161214;color:#f8eee4;font-family:Segoe UI,Arial,sans-serif;margin:24px}"
            "h1{font-size:26px}p{color:#c2ae9e;max-width:980px}"
            "img{image-rendering:pixelated;display:block;margin:8px 0;max-width:100%}</style>",
            f"<h1>The Emberdactyl's boss bar: {dsg['name']}</h1>",
            f"<p>{' '.join((winged_volcano.__doc__ or '').split())} Two looks: the adult's, <b>Emberdactyl: The Sky That "
            "Burns</b> (obsidian wings and rails, the volcano smoking, a lava-orange fill), and its young reborn's, "
            "<b>Emberdactyl: The Ember That Remains</b> (hotter: magma crust split by lava veins, the volcano "
            "erupting, a white-hot fill). Drawn 3x, exactly as the game draws them. In the game: "
            "<code>/dross showcase bossbars emberdactyl</code> (both, stacked, for a minute) or the fight.</p>",
            "<img src='bars.png'>"]
    with open(os.path.join(PREVIEW_DIR, "index.html"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(rows) + "\n")


# ---------------------------------------------------------------- the Java file


JAVA_HEAD = """package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

/**
 * The Emberdactyl's boss bar, written by {@code tools/fire/bossbars.py} (don't edit by hand: change the drawing in
 * {@code tools/fire/emberbars.py} and run it again). The user's pick, the Winged Volcano ("combine the wings of 2 with
 * the volcano of 3"): a volcano on the middle of the bar and burning wings spread out from its ends, in two looks: the
 * adult's ({@code boss.dross.emberdactyl.bar.adult}, "Emberdactyl: The Sky That Burns": obsidian, the volcano smoking)
 * and its young reborn's ({@code boss.dross.emberdactyl.bar.young}, "Emberdactyl: The Ember That Remains": hotter, the
 * volcano erupting). The texture holds both looks' empty bar, full bar and decorations.
 * <p>
 * Every position is relative to Minecraft's boss bar position (the x, y of its 182 x 5 bar). To draw a look: move it
 * down by {@code max(0, -nameY - 9)} so its name stays on the screen; blit the empty bar; blit the full bar over it,
 * cut off at the health (its columns from {@link Look#fillLeft()} for {@code round(progress * fillWidth())} pixels, at
 * the same offset); blit the decorations on top; draw the name centred on x + 91 at {@link Look#nameY()}; and leave
 * {@link Look#height()} pixels before the next bar.
 */
public final class EmberdactylBarArt
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


def _part(p):
    return f"new Part({p.u}, {p.v}, {p.w}, {p.h}, {p.x}, {p.y})"


DOCS = {False: "The adult's look, \"Emberdactyl: The Sky That Burns\": obsidian wings and rails, the volcano smoking.",
        True: "The young reborn's look, \"Emberdactyl: The Ember That Remains\": magma crust, the volcano erupting."}


def write_java(dsg, tex_h):
    body = [JAVA_HEAD,
            "    public static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, "
            "\"textures/gui/emberdactyl_bar.png\");\n",
            f"    public static final int TEXTURE_WIDTH = {TEX_W};\n",
            f"    public static final int TEXTURE_HEIGHT = {tex_h};\n\n"]
    for key, young in LOOKS:
        e, f, d, fl, fr = dsg["looks"][young]
        body.append(f"    /** {DOCS[young]} */\n"
                    f"    public static final Look {key.upper()} = new Look({_part(e)},\n"
                    f"            {_part(f)},\n"
                    f"            {_part(d)}, {fl}, {fr}, {dsg['name_y']}, {dsg['height']});\n")
    cases = "".join(f"            case \"{key}\" -> {key.upper()};\n" for key, _young in LOOKS)
    body.append(f"""
    /** The look a bar's key names (what follows {{@code boss.dross.emberdactyl.bar.}}: "adult" or "young"), or null. */
    public static Look look(String key)
    {{
        return switch (key)
        {{
{cases}            default -> null;
        }};
    }}

    private EmberdactylBarArt() {{}}
}}
""")
    with open(JAVA_PATH, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("".join(body))


# ---------------------------------------------------------------- everything


def build_all(write_files=True):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    dsg, tex = make_bars()
    print(f"  {dsg['name']}: art {dsg['art'][0]} x {dsg['art'][1]}, name at {dsg['name_y']}, height {dsg['height']}")
    preview(dsg, os.path.join(PREVIEW_DIR, "bars.png"))
    write_page(dsg)
    if write_files:
        Image.fromarray(tex, "RGBA").save(TEX_PATH)
        write_java(dsg, tex.shape[0])
        print(f"  texture {TEX_W} x {tex.shape[0]}")
    return dsg


if __name__ == "__main__":
    build_all(write_files="--previews-only" not in sys.argv)
