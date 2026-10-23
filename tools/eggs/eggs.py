"""
The bosses' spawn eggs, drawn whole (the user, October 23, 2026: "for the spawn egg make it so there is a wolf-like hat
on top with black under, then his blue eyes; do that for the Cinder Colossus and the Rat King; give me options I can
pick from"). A spawn egg's texture is normally two tinted layers; these are drawn as one 16x16 picture each, on
vanilla's own egg shape (SHAPE) so they sit well beside the other eggs, and their two tints are white.

Each design is pixel art written out as rows of letters (the design's palette says what each letter is; '.' is empty).
A design may reach over the egg's top (ears, a crown, volcano peaks, antlers) but nowhere else outside it.

  python tools/eggs/eggs.py      the option sheets (boss-previews/<biome>/<boss>/spawn_eggs/options.png) and the
                                 picked eggs' textures (src/main/resources/assets/dross/textures/item/<boss>_spawn_egg.png)

A picked egg's item model is item/generated on its texture, and its two tints in DrossColors are white.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "item")
PREVIEWS = {"huntsman": os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "spawn_eggs"),
            "cinder_colossus": os.path.join(ROOT, "boss-previews", "cinder_wastes", "cinder_colossus", "spawn_eggs"),
            "rat_king": os.path.join(ROOT, "boss-previews", "blightwood", "rat_king", "spawn_eggs")}
TITLES = {"huntsman": "The Huntsman", "cinder_colossus": "The Cinder Colossus", "rat_king": "The Rat King"}
# the user's picks (October 23, 2026: "C1 is good for dormant shell"; R1 with its crown centred). The Huntsman's egg is
# still tools/huntsman/egg.py's until he's picked.
PICKS = {"cinder_colossus": "C1", "rat_king": "R1"}

# vanilla's spawn egg (textures/item/spawn_egg.png and its overlay together): each row's first and last column
SHAPE = {2: (7, 8), 3: (5, 10), 4: (4, 11), 5: (4, 11), 6: (3, 12), 7: (3, 12), 8: (2, 13), 9: (2, 13),
         10: (2, 13), 11: (3, 12), 12: (3, 12), 13: (4, 11), 14: (6, 9)}
OVER_TOP = 2                    # rows above this may hold what reaches over the egg's top


def inside(x, y):
    r = SHAPE.get(y)
    return r is not None and r[0] <= x <= r[1]


# ---------------------------------------------------------------- the Huntsman: a wolf's head worn as a hat, black
# under it, his glacier-blue eyes in the black

# his own hood (tools/rimefall/rimedesigns/d06_huntsman.py "pelt": rimekit/mats.WOLVERINE with frost tips)
WOLF_HOOD = {"1": (30, 32, 40), "2": (46, 48, 58), "3": (70, 74, 88), "4": (98, 104, 120),
             "f": (176, 188, 204), "p": (64, 54, 62), "n": (16, 16, 20)}
MANTLE = {"m": (182, 188, 196), "M": (138, 145, 155), "a": (222, 226, 232)}       # rimekit/mats.WOOLLY_PALE
FACE = {"k": (8, 8, 10), "K": (24, 24, 30), "E": (176, 236, 255), "e": (64, 152, 236)}
BONE = {"c": (214, 204, 184), "C": (176, 164, 142), "a": (232, 226, 210)}
ANTLER = {"t": (196, 180, 150), "T": (150, 132, 104)}

# the hat: pricked ears over the egg's top, the wolf's own dark eyes, its muzzle and nose pointing down over his brow,
# the hood's sides framing his face; then the black face and his eyes (one shape, swapped in by `hat`)
HAT = [
    "....1......1....",
    "....2p....p2....",
    "....2p2332p2....",
    ".....233332.....",
    "....2n3443n2....",
    "....23344332....",
    "...1234nn4321...",
    "...122k44k221...",
    "..12kkk33kkk21..",
    "..12keEkkEek21..",
    "..12kkkkkkkk21..",
    "...1kkkkkkkk1...",
    "...KkkkkkkkkK...",
    "....KkkkkkkK....",
    "......KKKK......",
    "................"]


def hat(changes=None):
    """The hat's rows with {row: {column: letter}} changes."""
    rows = [list(r) for r in HAT]
    for y, cols in (changes or {}).items():
        for x, ch in cols.items():
            rows[y][x] = ch
    return ["".join(r) for r in rows]


FANGS = {8: {6: "a", 9: "a"}}
FROST = {3: {6: "f"}, 4: {9: "f"}, 2: {7: "f"}}
ANTLERS = {0: {2: "t", 6: "t", 9: "t", 13: "t"}, 1: {2: "t", 3: "T", 6: "T", 9: "T", 12: "T", 13: "t"},
           2: {3: "t", 12: "t"}}
MANTLE_ROWS = {12: {3: "M", 4: "m", 5: "a", 6: "m", 7: "a", 8: "a", 9: "m", 10: "a", 11: "m", 12: "M"},
               13: {4: "M", 5: "m", 6: "a", 7: "m", 8: "m", 9: "a", 10: "m", 11: "M"},
               14: {6: "M", 7: "m", 8: "m", 9: "M"}}
HOOD = {**WOLF_HOOD, **FACE, **BONE, **ANTLER, **MANTLE}


def merged(*changes):
    out = {}
    for c in changes:
        for y, cols in c.items():
            out.setdefault(y, {}).update(cols)
    return out


HUNTSMAN = [
    ("H1", "His wolf hood", "a wolf's head in the colours of his own hood (dark slate fur, frost on its tips), black "
     "under it, his blue eyes", HOOD, hat(merged(FROST))),
    ("H2", "Hood and fangs", "H1 with his hood's two white fangs over the black", HOOD, hat(merged(FROST, FANGS))),
    ("H3", "Hood, fangs and antlers", "H2 with his pale deer antlers rising behind the wolf's ears", HOOD,
     hat(merged(FROST, FANGS, ANTLERS))),
    ("H4", "Hood, fangs and mantle", "H2 with his pale wolf-fur mantle round the bottom", HOOD,
     hat(merged(FROST, FANGS, MANTLE_ROWS))),
    ("H5", "All of him", "fangs, antlers and mantle together", HOOD, hat(merged(FROST, FANGS, ANTLERS, MANTLE_ROWS))),
]

# ---------------------------------------------------------------- the Cinder Colossus: scorched stone and obsidian,
# lava glowing through (fire colours only, never the Dross blue)

STONE = [(30, 26, 26), (48, 42, 40), (70, 62, 58), (96, 86, 80)]
LAVA = [(160, 44, 10), (215, 86, 14), (246, 146, 32), (255, 206, 90)]
FIRE = {"y": (255, 230, 120), "L": (255, 150, 30), "l": (214, 78, 14), "r": (122, 34, 12),
        "o": (26, 18, 38), "O": (62, 46, 82), "s": (44, 36, 34), "S": (76, 62, 54)}

COLOSSUS = [
    ("C1", "Dormant shell", "scorched stone and obsidian, lava glowing up through one great crack from its crater",
     {":": STONE, **FIRE}, [
         "................",
         "................",
         ".......Ly.......",
         ".....::L:::.....",
         "....:o::L:::....",
         "....::::L:::....",
         "...::::L::oo:...",
         "...:::lL:::::...",
         "..:::l::L::lr:..",
         "..:ol:::Lll:::..",
         "..::r::::L::::..",
         "...:::o::l:o:...",
         "...:::::l:r::...",
         "....::::r:::....",
         "......::::......",
         "................"]),
    ("C2", "Twin volcanoes", "its two volcanoes over the top, lava in their craters and running down its sides",
     {":": STONE, **FIRE}, [
         ".....y....y.....",
         "....:L:..:L:....",
         "....:l::::l:....",
         ".....l::::l.....",
         "....:l:o::l:....",
         "....l::::::l....",
         "...:l:::::o:l...",
         "...:r:::::::r...",
         "..::r:::o:::r:..",
         "..::::::::::::..",
         "..:o::::::::o:..",
         "...::::::::::...",
         "...:::o::::::...",
         "....::::::o:....",
         "......::::......",
         "................"]),
    ("C3", "Three eyes", "its cracked stone face: three burning eyes, obsidian horns, a lava jaw and a beard of shards",
     {":": STONE, **FIRE}, [
         "...o........o...",
         "...oO......Oo...",
         "....oo.::.oo....",
         ".....::::::.....",
         "....:::LL:::....",
         "....:::yy:::....",
         "...::::::::::...",
         "...:Ly::::yL:...",
         "..::::::::::::..",
         "..::::::::::::..",
         "..:rllllllllr:..",
         "...oOoOooOoO:...",
         "...:oo:oo:oo:...",
         "....o::o::o:....",
         "......o::o......",
         "................"]),
    ("C4", "Lava core", "the molten body of its last stage, a few plates of dark crust still on it",
     {":": LAVA, **FIRE}, [
         "................",
         "................",
         ".......yy.......",
         ".....:::ss:.....",
         "....::::sS::....",
         "....:ss:::::....",
         "...::Ss:::::s...",
         "...:::::::sSs...",
         "..:::::::::s::..",
         "..:ss:::::::::..",
         "..:sSs:::::ss:..",
         "...:s:::::sSs...",
         "...::::ss::s:...",
         "....:::sS:::....",
         "......::::......",
         "................"]),
    ("C5", "Cracking", "its shell breaking: obsidian and stone above, the lava core bursting out below",
     {":": STONE, ";": LAVA, **FIRE}, [
         "................",
         "................",
         ".......Ly.......",
         ".....:::L::.....",
         "....:o::L:::....",
         "....::::l:o:....",
         "...::o:::::::...",
         "...:::::l:::o...",
         "..:::;:::;;:::..",
         "..:;;;;:;;;;;:..",
         "..;;;;;;;;;;;;..",
         "...;;s;;;;;;;...",
         "...;;;;;;;s;;...",
         "....;;s;;;;;....",
         "......;;;;......",
         "................"]),
]

# ---------------------------------------------------------------- the Rat King (Gnawrick): grey-green fur, his little
# gold crown cocked to one side, beady eyes; the Plague Necromancer's colours (poison green, decaying red, bone)

FUR = [(52, 60, 46), (78, 88, 68), (106, 116, 92), (134, 144, 118)]
FUR_DARK = [(34, 40, 30), (52, 60, 46), (72, 82, 64), (96, 106, 84)]
CAPE = [(78, 12, 16), (112, 22, 26), (148, 34, 36), (180, 52, 50)]
RAT = {"G": (250, 204, 70), "g": (186, 132, 30), "h": (130, 236, 96), "b": (10, 10, 10), "w": (236, 236, 228),
       "W": (176, 178, 170), "P": (218, 130, 146), "p": (166, 92, 106), "B": (226, 216, 186), "R": (236, 46, 34),
       "q": (176, 255, 140), "Q": (70, 186, 62), "v": (128, 222, 64), "V": (76, 150, 42), "1": (40, 46, 36),
       "y": (255, 226, 120)}

CROWN = [".....G.GG.G.....",
         ".....gGhhGg.....",
         ".....gggggg....."]

RAT_KING = [
    ("R1", "Crowned rat", "his rat face filling the egg: round ears, beady eyes, pink nose, whiskers and buck teeth, "
     "his crown on top", {":": FUR, **RAT}, CROWN + [
         ".....::::::.....",
         "....1P::::P1....",
         "....1p::::p1....",
         "...::::::::::...",
         "...::bw::bw::...",
         "..::::::::::::..",
         "..:WW::PP::WW:..",
         "..::W::BB::W::..",
         "...::::::::::...",
         "...::::::::::...",
         "....::::::::....",
         "......::::......",
         "................"]),
    ("R2", "Crown and cape", "his face and crown above, his ragged red cape with a gold clasp below",
     {":": FUR, ";": CAPE, **RAT}, CROWN + [
         ".....::::::.....",
         "....1P::::P1....",
         "....:bw::bw:....",
         "...::::PP::::...",
         "...:WW:BB:WW:...",
         "..::::::::::::..",
         "..;;;;;gG;;;;;..",
         "..;;;;;;;;;;;;..",
         "...;;;;;;;;;;...",
         "...;;;;;;;;;;...",
         "....;;;;;;;;....",
         "......;;;;......",
         "................"]),
    ("R3", "Red eyes", "his face in the Rage: glowing red eyes, bared buck teeth, his crown askew",
     {":": FUR, **RAT}, [
         ".......G.G.G....",
         ".......gGhGg....",
         ".......ggggg....",
         ".....::::::.....",
         "....1P::::P1....",
         "....1p::::p1....",
         "...::::::::::...",
         "...::RR::RR::...",
         "..::::::::::::..",
         "..:WW::PP::WW:..",
         "..::W::BB::W::..",
         "...::::BB::::...",
         "...::::::::::...",
         "....::::::::....",
         "......::::......",
         "................"]),
    ("R4", "Plague orb", "dark fur, his crown, and his staff's glowing green orb in its claw",
     {":": FUR_DARK, **RAT}, CROWN + [
         ".....::::::.....",
         "....:b::::b:....",
         "....::::::::....",
         "...:::BvvB:::...",
         "...::BqqqQB::...",
         "..:::vqyqqv:::..",
         "..:::BqqqQB:::..",
         "..::::BQQB::::..",
         "...:::::B::::...",
         "...:::::B::::...",
         "....::::B:::....",
         "......::B:......",
         "................"]),
    ("R5", "Plague spots", "his crowned face, his fur spotted with glowing poison-green pustules",
     {":": FUR, **RAT}, CROWN + [
         ".....::::v:.....",
         "....1P::::P1....",
         "....1p:v::p1....",
         "...::::::::v:...",
         "...::bw::bw::...",
         "..:v::::::::::..",
         "..:WW::PP::WW:..",
         "..::W::BB::W:v..",
         "...:v::::::::...",
         "...::::::v:::...",
         "....:v::::::....",
         "......::v:......",
         "................"]),
]

DESIGNS = {"huntsman": HUNTSMAN, "cinder_colossus": COLOSSUS, "rat_king": RAT_KING}


def check(boss, design):
    """Every row 16 wide, every letter in the palette, nothing outside the egg but over its top."""
    label, _name, _note, pal, rows = design
    problems = []
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        problems.append(f"{boss} {label}: not 16 x 16 ({[len(r) for r in rows]})")
        return problems
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in pal:
                problems.append(f"{boss} {label}: '{ch}' at {x},{y} isn't in its palette")
            elif not inside(x, y) and y > OVER_TOP:
                problems.append(f"{boss} {label}: {x},{y} is outside the egg")
    return problems


def shade(x, y):
    """0 (shadow, bottom right) to 3 (lit, top left), like vanilla's egg."""
    l, r = SHAPE.get(y, SHAPE[2] if y < 2 else SHAPE[14])
    across = (x - l) / max(1, r - l)
    down = (max(y, 2) - 2) / 12.0
    return max(0, min(3, int(round(2.6 - 1.8 * across - 1.4 * down))))


def colour(pal, ch, x, y):
    """A palette letter's colour there: a list of 4 is a shaded fill (dark to lit)."""
    c = pal[ch]
    return tuple(c[shade(x, y)]) if isinstance(c, list) else c


def draw(design):
    _label, _name, _note, pal, rows = design
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = colour(pal, ch, x, y) + (255,)
    return im


def slot(im, scale):
    """The egg in an inventory slot, as the game would show it at `scale` (one slot is 18 x 18)."""
    s = Image.new("RGBA", (18 * scale, 18 * scale), (139, 139, 139, 255))
    d = ImageDraw.Draw(s)
    d.rectangle([0, 0, 18 * scale - 1, scale - 1], fill=(55, 55, 55, 255))
    d.rectangle([0, 0, scale - 1, 18 * scale - 1], fill=(55, 55, 55, 255))
    d.rectangle([0, 17 * scale, 18 * scale - 1, 18 * scale - 1], fill=(255, 255, 255, 255))
    d.rectangle([17 * scale, 0, 18 * scale - 1, 18 * scale - 1], fill=(255, 255, 255, 255))
    s.alpha_composite(im.resize((16 * scale, 16 * scale), Image.NEAREST), (scale, scale))
    return s


def font(size):
    for f in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def sheet(boss, designs):
    big, cell = 12, 16 * 12 + 40
    w = len(designs) * cell + 40
    h = 16 * big + 290
    out = Image.new("RGBA", (w, h), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 14), f"{TITLES[boss]}: spawn egg options", font=font(22), fill=(235, 238, 245))
    for i, design in enumerate(designs):
        label, name, note, _pal, _rows = design
        im = draw(design)
        x0 = 20 + i * cell
        back = Image.new("RGBA", (16 * big + 16, 16 * big + 16), (62, 66, 76, 255))
        back.alpha_composite(im.resize((16 * big, 16 * big), Image.NEAREST), (8, 8))
        out.alpha_composite(back, (x0, 52))
        y = 52 + 16 * big + 26
        d.text((x0, y), f"{label}  {name}", font=font(17), fill=(240, 240, 240))
        words, line, ly = note.split(), "", y + 24
        for wd in words:
            if d.textlength(line + " " + wd, font=font(13)) > cell - 30:
                d.text((x0, ly), line.strip(), font=font(13), fill=(170, 176, 188))
                line, ly = "", ly + 17
            line += " " + wd
        d.text((x0, ly), line.strip(), font=font(13), fill=(170, 176, 188))
        sx = x0
        for sc in (1, 2, 3):
            s = slot(im, sc)
            out.alpha_composite(s, (sx, h - 18 * 3 - 18))
            sx += 18 * sc + 12
    return out


def main(argv):
    problems = []
    for boss, designs in DESIGNS.items():
        for design in designs:
            problems += check(boss, design)
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    for boss, label in PICKS.items():
        design = next(d for d in DESIGNS[boss] if d[0] == label)
        path = os.path.join(TEXTURES, f"{boss}_spawn_egg.png")
        draw(design).save(path)
        print("wrote", os.path.relpath(path, ROOT), "from", label)
    for boss, designs in DESIGNS.items():
        os.makedirs(PREVIEWS[boss], exist_ok=True)
        path = os.path.join(PREVIEWS[boss], "options.png")
        sheet(boss, designs).save(path)
        print("wrote", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
