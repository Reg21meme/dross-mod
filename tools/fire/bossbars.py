"""
The Emberdactyl's boss bars: ten candidate designs for the user to pick from (October 29, 2026), each in two looks:

  adult   "Emberdactyl: The Sky That Burns"       (lang key boss.dross.emberdactyl.bar.adult)
  young   "Emberdactyl: The Ember That Remains"   (boss.dross.emberdactyl.bar.young), its young reborn: hotter

The designs are drawn in tools/fire/emberbars.py with the Huntsman's pixel tools (tools/huntsman/barkit.py: Minecraft
GUI pixel art, 1 px outlines, Minecraft's own font in the previews), so the previews and the game look exactly alike.
The fight's two bars use design PICK until the user picks; the showcase (/dross showcase bossbars emberdactyl [page])
shows any design by its own bar names, boss.dross.emberdactyl.bar.design_<n>_<adult|young>.

Run from the project folder:  python tools/fire/bossbars.py   (--previews-only: only the pictures)

It writes:
  assets/dross/textures/gui/emberdactyl_bar.png                    every look's parts: empty bar, full bar, decorations
  boss/client/EmberdactylBarArt.java                               (generated) where each part is and where it's drawn
  boss-previews/cinder_wastes/emberdactyl/bossbars/bars.png        all ten, adult and young, drawn 3x as in the game
  boss-previews/cinder_wastes/emberdactyl/bossbars/page_<n>.png    the showcase's four pages, five bars stacked
  boss-previews/cinder_wastes/emberdactyl/bossbars/index.html      a page with them

How the game draws a look (boss/client/DrossBossBars, like the Huntsman's): Minecraft's bar sits at (x, y), 182 x 5, its
name 9 pixels above. Move it down by max(0, -nameY - 9) so its name stays on the screen; draw the empty bar; the full
bar over it cut off at the health (its columns from fillLeft for round(progress * fillWidth) pixels, at the same
offset); the decorations on top; the name centred on x + 91 at nameY; then leave height pixels before the next bar.
A design's two looks share their name's height and the room they take, so changing look never moves anything.
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
from emberbars import DESIGNS, NAME_COLOURS  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
TEX_PATH = os.path.join(ASSETS, "textures", "gui", "emberdactyl_bar.png")
JAVA_PATH = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "client",
                         "EmberdactylBarArt.java")
PREVIEW_DIR = os.path.join(ROOT, "boss-previews", "cinder_wastes", "emberdactyl", "bossbars")

PICK = 1                    # the design the fight's bars use until the user picks (EmberdactylBarArt.PICK)
NAME_GAP = 1                # pixels of air between decorations under the name and the name's shadow
BAR_GAP = 5                 # pixels between one bar's art and the next bar's name (vanilla: 5)
MAX_ART_W, MAX_ART_H = 240, 34   # every look fits in this (checked on every build; the screen is at least 320 wide)
TEX_W = 256
PART_GAP = 2
PREVIEW_SCALE = 3
VANILLA_FIRST_Y = 12        # where Minecraft draws the first boss bar
PER_PAGE = 5                # the showcase's bars a page (EmberdactylBarShowcase.PER_PAGE)

LOOKS = (("adult", False), ("young", True))
FIGHT_NAMES = {False: "Emberdactyl: The Sky That Burns", True: "Emberdactyl: The Ember That Remains"}


def showcase_name(number, title, young):
    """The showcase bar's name (en_us.json, boss.dross.emberdactyl.bar.design_<n>_<adult|young>)."""
    return f"No. {number} {title} ({'young reborn' if young else 'adult'})"


def make_design(number, title, fn):
    """Draws a design's two looks and works out where their name sits (above any decoration under the widest name
    either look may carry: the fight's or the showcase's) and how much room they take."""
    looks = {young: look_parts(*fn(young)) for _key, young in LOOKS}
    names = [FIGHT_NAMES[False], FIGHT_NAMES[True], showcase_name(number, title, False),
             showcase_name(number, title, True)]
    half = max(FONT.width(s) for s in names) // 2 + 2
    xs, ys, top_mid = [], [], 0
    for _key, young in LOOKS:
        e, f, d, fl, fr = looks[young]
        if f.w - fl - fr != BAR_W:
            raise SystemExit(f"design {number} {title}: the fill is {f.w - fl - fr} wide, not {BAR_W}")
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
        raise SystemExit(f"design {number} {title}: the art is {art_w} x {art_h} (more than {MAX_ART_W} x {MAX_ART_H})")
    name_y = min(-NAME_ABOVE, top_mid - NAME_ABOVE - (NAME_GAP if top_mid < -1 else 0))
    height = (max(ys) - name_y) + BAR_GAP
    return dict(number=number, name=title, looks=looks, name_y=int(name_y), height=int(height), art=(art_w, art_h))


def make_bars():
    """Every design's two looks, and the one texture holding their parts."""
    designs = [make_design(n, title, fn) for n, title, fn in DESIGNS]
    v = 0
    for dsg in designs:
        for _key, young in LOOKS:
            for part in dsg["looks"][young][:3]:
                if part.w > TEX_W:
                    raise SystemExit(f"design {dsg['number']}: a part is {part.w} wide (more than {TEX_W})")
                part.u, part.v = 0, v
                v += part.h + PART_GAP
    tex_h = 16
    while tex_h < v:
        tex_h *= 2
    tex = np.zeros((tex_h, TEX_W, 4), dtype=np.uint8)
    for dsg in designs:
        for _key, young in LOOKS:
            for part in dsg["looks"][young][:3]:
                tex[part.v:part.v + part.h, part.u:part.u + part.w] = part.img
    return designs, tex


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


def big(im, k=PREVIEW_SCALE):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def sheet(designs, out_path):
    """All ten, a row each: the adult look (part full) and the young look (less full), with the fight's names."""
    rows = []
    for dsg in designs:
        a = big(render([(dsg, False, FIGHT_NAMES[False], 0.72)], seed=dsg["number"]))
        y = big(render([(dsg, True, FIGHT_NAMES[True], 0.4)], seed=dsg["number"] + 50))
        rows.append((dsg, a, y))
    gap, head, lab_h = 18, 96, 28
    colw = max(max(a.width, y.width) for _d, a, y in rows)
    H = head + sum(max(a.height, y.height) + lab_h + gap for _d, a, y in rows) + gap
    out = Image.new("RGB", (2 * colw + 3 * gap, H), SHEET_BG)
    dr = ImageDraw.Draw(out)
    dr.text((gap, 12), "The Emberdactyl's boss bars: ten designs to pick from", font=_font(32), fill=TEXT)
    dr.text((gap, 58), f"Left: the adult (The Sky That Burns), part full. Right: its young reborn (The Ember That "
                       f"Remains), the same design hotter, less full. Drawn {PREVIEW_SCALE}x as the game draws them. "
                       f"In the game: /dross showcase bossbars emberdactyl [1-4].", font=_font(15, False), fill=DIM)
    yy = head
    for dsg, a, yg in rows:
        dr.text((gap, yy), f"No. {dsg['number']}  {dsg['name']}  (adult)", font=_font(18), fill=ACCENT)
        dr.text((2 * gap + colw, yy), f"No. {dsg['number']}  {dsg['name']}  (young reborn)", font=_font(18),
                fill=YOUNG_ACCENT)
        out.paste(a, (gap, yy + lab_h))
        out.paste(yg, (2 * gap + colw, yy + lab_h))
        yy += max(a.height, yg.height) + lab_h + gap
    out.save(out_path)


def pages(designs):
    """The showcase's four pages as the game stacks them: designs 1-5 and 6-10, adult then young."""
    out = []
    for page in range(1, 5):
        young = page >= 3
        first = ((page - 1) % 2) * PER_PAGE
        group = designs[first:first + PER_PAGE]
        entries = [(dsg, young, showcase_name(dsg["number"], dsg["name"], young), 0.9 - 0.15 * i)
                   for i, dsg in enumerate(group)]
        im = big(render(entries, seed=page * 31))
        path = os.path.join(PREVIEW_DIR, f"page_{page}.png")
        im.save(path)
        out.append((page, young, group, sum(dsg["height"] for dsg in group)))
    return out


def write_page(designs, page_info):
    rows = ["<!doctype html><meta charset='utf-8'><title>Emberdactyl boss bars</title>",
            "<style>body{background:#161214;color:#f8eee4;font-family:Segoe UI,Arial,sans-serif;margin:24px}"
            "h1{font-size:26px}h2{font-size:19px;color:#ffaa50;margin-top:28px}p,li{color:#c2ae9e;max-width:980px}"
            "img{image-rendering:pixelated;display:block;margin:8px 0;max-width:100%}</style>",
            "<h1>The Emberdactyl's boss bars: ten designs to pick from</h1>",
            "<p>Each design has two looks: the adult's, <b>Emberdactyl: The Sky That Burns</b> (obsidian, a lava-orange "
            "fill), and its young reborn's, <b>Emberdactyl: The Ember That Remains</b> (the same design, hotter: magma "
            "crust split by lava veins, a white-hot fill). Drawn 3x, exactly as the game draws them. In the game: "
            "<code>/dross showcase bossbars emberdactyl</code> (page 1), <code>... 2</code>, <code>... 3</code>, "
            f"<code>... 4</code>. The fight uses design {PICK} until you pick.</p>",
            "<ol>"]
    for dsg in designs:
        doc = next(fn for n, _t, fn in DESIGNS if n == dsg["number"]).__doc__ or ""
        rows.append(f"<li><b>{dsg['name']}</b>: {' '.join(doc.split())}</li>")
    rows += ["</ol>", "<h2>All ten</h2>", "<img src='bars.png'>"]
    for page, young, group, _h in page_info:
        first, last = group[0]["number"], group[-1]["number"]
        rows.append(f"<h2>Showcase page {page}: designs {first}-{last}, {'the young reborn' if young else 'the adult'}"
                    f"</h2><img src='page_{page}.png'>")
    with open(os.path.join(PREVIEW_DIR, "index.html"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(rows) + "\n")


# ---------------------------------------------------------------- the Java file


JAVA_HEAD = """package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;

/**
 * The Emberdactyl's boss bars, written by {@code tools/fire/bossbars.py} (don't edit by hand: change the drawings in
 * {@code tools/fire/emberbars.py} and run it again). Ten candidate designs for the user to pick from, each in two looks:
 * the adult's ("Emberdactyl: The Sky That Burns") and its young reborn's ("Emberdactyl: The Ember That Remains"), the
 * same design hotter. The fight's bars ({@code boss.dross.emberdactyl.bar.adult} and {@code .young}) use design
 * {@link #PICK}; the showcase's ({@code boss.dross.emberdactyl.bar.design_<n>_<adult|young>}) any design. The texture
 * holds every look's empty bar, full bar and decorations.
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


def _look(dsg, young):
    e, f, d, fl, fr = dsg["looks"][young]
    return (f"new Look({_part(e)},\n                    {_part(f)},\n                    {_part(d)}, {fl}, {fr}, "
            f"{dsg['name_y']}, {dsg['height']})")


def write_java(designs, tex_h):
    names = ", ".join(f'"{dsg["name"]}"' for dsg in designs)
    body = [JAVA_HEAD,
            "    public static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, "
            "\"textures/gui/emberdactyl_bar.png\");\n",
            f"    public static final int TEXTURE_WIDTH = {TEX_W};\n",
            f"    public static final int TEXTURE_HEIGHT = {tex_h};\n",
            "    /** How many designs there are (numbered from 1). */\n",
            f"    public static final int DESIGNS = {len(designs)};\n",
            "    /** The design the fight's bars use (the user picks later; PICK in tools/fire/bossbars.py). */\n",
            f"    public static final int PICK = {PICK};\n",
            "    /** Each design's working name, design 1 first. */\n",
            f"    private static final String[] NAMES = {{{names}}};\n\n",
            "    /** Each design's two looks, design 1 first: the adult's, then the young's. */\n",
            "    private static final Look[][] LOOKS = {\n"]
    for dsg in designs:
        body.append(f"            // {dsg['number']} {dsg['name']}\n"
                    f"            {{{_look(dsg, False)},\n"
                    f"                {_look(dsg, True)}}},\n")
    body.append("""    };

    /** The working name of design {@code design} (1 to {@link #DESIGNS}), or "" if there's no such design. */
    public static String name(int design)
    {
        return design >= 1 && design <= DESIGNS ? NAMES[design - 1] : "";
    }

    /** Design {@code design}'s adult or young look, or null if there's no such design. */
    public static Look look(int design, boolean young)
    {
        return design >= 1 && design <= DESIGNS ? LOOKS[design - 1][young ? 1 : 0] : null;
    }

    /**
     * The look a bar's key names (what follows {@code boss.dross.emberdactyl.bar.}): "adult" or "young" (the fight's,
     * design {@link #PICK}), or {@code design_<n>_adult} / {@code design_<n>_young} (the showcase's); null for anything
     * else.
     */
    public static Look look(String key)
    {
        if (key.equals("adult") || key.equals("young"))
        {
            return look(PICK, key.equals("young"));
        }
        if (key.startsWith("design_"))
        {
            int cut = key.lastIndexOf('_');
            String which = key.substring(cut + 1);
            if (cut > 7 && (which.equals("adult") || which.equals("young")))
            {
                try
                {
                    return look(Integer.parseInt(key.substring(7, cut)), which.equals("young"));
                }
                catch (NumberFormatException ignored)
                {
                    return null;
                }
            }
        }
        return null;
    }

    private EmberdactylBarArt() {}
}
""")
    with open(JAVA_PATH, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("".join(body))


def lang_lines(designs):
    """The showcase bars' names for en_us.json (printed; added there by hand)."""
    out = []
    for dsg in designs:
        for key, young in LOOKS:
            out.append(f'  "boss.dross.emberdactyl.bar.design_{dsg["number"]}_{key}": '
                       f'"{showcase_name(dsg["number"], dsg["name"], young)}",')
    return out


# ---------------------------------------------------------------- everything


def build_all(write_files=True):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    designs, tex = make_bars()
    for dsg in designs:
        print(f"  {dsg['number']:2d} {dsg['name']:16s} art {dsg['art'][0]:3d} x {dsg['art'][1]:2d}, "
              f"name at {dsg['name_y']}, height {dsg['height']}")
    sheet(designs, os.path.join(PREVIEW_DIR, "bars.png"))
    info = pages(designs)
    for page, young, group, h in info:
        print(f"  page {page}: designs {group[0]['number']}-{group[-1]['number']} {'young' if young else 'adult'}, "
              f"{h} GUI pixels tall (Minecraft draws bars only while above a third of the screen's height)")
    write_page(designs, info)
    if write_files:
        Image.fromarray(tex, "RGBA").save(TEX_PATH)
        write_java(designs, tex.shape[0])
        print(f"  texture {TEX_W} x {tex.shape[0]}")
    if "--lang" in sys.argv:
        print("\n".join(lang_lines(designs)))
    return designs


if __name__ == "__main__":
    build_all(write_files="--previews-only" not in sys.argv)
