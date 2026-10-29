"""
The bosses' spawn eggs, drawn whole (the user, October 23, 2026: "for the spawn egg make it so there is a wolf-like hat
on top with black under, then his blue eyes; do that for the Cinder Colossus and the Rat King; give me options I can
pick from"). A spawn egg's texture is normally two tinted layers; these are drawn as one 16x16 picture each, on
vanilla's own egg shape (SHAPE) so they sit well beside the other eggs, and their two tints are white.

Each design is pixel art written out as rows of letters (the design's palette says what each letter is; '.' is empty).
A design may reach over the egg's top (ears, a crown, volcano peaks, antlers) but nowhere else outside it, except the
winged designs (WINGS_OUT: the Emberdactyl's and its young's rounds 2 and 3), whose wings spread out past the egg on
both sides (the user, October 29, 2026, of round 1: "none of these boss egg ideas are good, maybe try to incorporate the
wings outside or something"; of round 2: "the wings are cool the A7 one now i want the bird features and Y8 for young
but again i want bird features").

  python tools/eggs/eggs.py      the option sheets (boss-previews/<biome>/<boss>/spawn_eggs/options.png) and the
                                 picked eggs' textures (src/main/resources/assets/dross/textures/item/<boss>_spawn_egg.png)

The Emberdactyl and its young (emberdactyl, emberyoung) share one folder, boss-previews/cinder_wastes/emberdactyl/
spawn_eggs/: options.png and young_options.png, and every option's texture on its own in textures/<label>.png. Round 1
(A1-A5, Y1-Y5, a picture on the egg) is kept as a record in spawn_eggs/round1/, round 2 (A6-A10, Y6-Y10, the wings out
past the egg) in spawn_eggs/round2/; round 3 (A11-A13, Y11-Y13: A7's and Y8's wings with the bird's head and face) is
drawn now.

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
EMBER_EGGS = os.path.join(ROOT, "boss-previews", "cinder_wastes", "emberdactyl", "spawn_eggs")
PREVIEWS["emberdactyl"] = EMBER_EGGS
PREVIEWS["emberyoung"] = EMBER_EGGS
TITLES = {"huntsman": "The Huntsman", "cinder_colossus": "The Cinder Colossus", "rat_king": "The Rat King",
          "emberdactyl": "The Emberdactyl", "emberyoung": "The Emberdactyl's young reborn"}
# the user's picks (October 23, 2026: "C1 is good for dormant shell"; R1 with its crown centred; "h2" for the
# Huntsman, his hood with its fangs)
PICKS = {"huntsman": "H2", "cinder_colossus": "C1", "rat_king": "R1",
         # not picked yet (October 29, 2026): round 3's first option of each stands in until the user picks
         "emberdactyl": "A11", "emberyoung": "Y11"}
# the option sheet's file name, when it isn't options.png (the young's sheet shares the adult's folder)
SHEET_FILES = {"emberyoung": "young_options.png"}
# what follows the sheet's title, when there's more to say
SUBTITLES = {"emberdactyl": ", round 3: A7's wings with its bird features",
             "emberyoung": ", round 3: Y8's molten egg with its bird features"}
# bosses whose every option is also saved as its own texture (<folder>/textures/<label>.png), so swapping the pick is
# a file copy (or PICKS changed and the tool run again)
OPTION_TEXTURES = ("emberdactyl", "emberyoung")

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

# ---------------------------------------------------------------- the Emberdactyl and its young reborn (October 29,
# 2026: spawn eggs for both, with options to pick from). The Fire Necromancer's colours only: lava orange, ember red,
# obsidian black and violet, never the Dross blue. The adult: an obsidian bill with its half moon standing along it,
# two blade horns, ember-slit eyes, scalloped wings shedding fire, a rocky obsidian hide veined with lava. The young:
# smaller and sleeker, much more molten (lava under a thin obsidian crust), plain round amber eyes, out of an
# obsidian egg that fades from near black at its foot to violet at its top.


def egg(top, body):
    """A design's rows: rows 0-2 as given (16 wide; they may reach over the egg's top), rows 3-14 from just the egg's
    inside (each its own width there, SHAPE), row 15 empty."""
    rows = list(top)
    for y, inner in zip(range(3, 15), body):
        l, r = SHAPE[y]
        if len(inner) != r - l + 1:
            raise ValueError(f"row {y} is {len(inner)} wide, the egg is {r - l + 1} there: {inner!r}")
        rows.append("." * l + inner + "." * (15 - r))
    return rows + ["." * 16]


OBSIDIAN = [(16, 10, 24), (30, 20, 44), (48, 34, 70), (72, 54, 102)]          # its hide, dark to lit
SMOKE = [(54, 34, 72), (76, 50, 100), (100, 70, 130), (128, 94, 162)]         # a violet sky of volcanic smoke
EMBER_SKY = [(66, 18, 14), (100, 30, 16), (136, 48, 20), (172, 72, 26)]       # a sky lit by fire
BASALT = [(20, 16, 20), (34, 28, 32), (50, 42, 46), (68, 58, 62)]
MAGMA = [(190, 62, 12), (232, 106, 20), (250, 160, 40), (255, 212, 104)]     # the young's molten hide
EMBER = {"k": (10, 6, 14), "o": (30, 22, 44), "O": (64, 48, 90), "V": (96, 72, 132),
         "y": (255, 232, 130), "L": (255, 150, 30), "l": (214, 78, 14), "r": (122, 34, 12), "w": (255, 250, 228)}
YOUNG = {"A": (255, 182, 44), "a": (196, 116, 22), "c": (30, 20, 38), "C": (62, 42, 78)}
# its egg: near black at its foot to violet at its top
SHELL = {"1": (12, 8, 16), "2": (26, 18, 36), "3": (44, 30, 62), "4": (68, 50, 96), "5": (98, 74, 136)}

# Round 1 (October 29, 2026), kept as a record and no longer drawn: its sheets and textures are in
# boss-previews/cinder_wastes/emberdactyl/spawn_eggs/round1/. Round 2 (the wings out past the egg) is further down.
EMBERDACTYL_ROUND1 = [
    ("A1", "Crest and slit eye", "its obsidian head side-on: the glowing half moon standing on its bill, a blade "
     "horn swept back over the top, an ember-slit eye, lava down its neck", {":": SMOKE, **EMBER}, egg(
         [".............k..",
          "............kO..",
          ".......::..kO..."], [
             ":::::k",
             ":yy:::kO",
             "yLLy::kO",
             "yLllLykkk:",
             "LlrrlLkLyk",
             ":kkkkkkkkkk:",
             "kkkkkkkkkkk:",
             ":::oooooooo:",
             ":::::oolOo",
             "::::oolooo",
             "::ooLooo",
             "olLo"])),
    ("A2", "Wings spread", "seen from above against a burning sky: its long bill with the half moon along it "
     "pointing up, its wings swept back, fire dropping from their scalloped edges", {":": EMBER_SKY, **EMBER}, egg(
         [".......yy.......",
          "......kLLk......",
          "......kLLk......"], [
             ":kLLk:",
             "::kLLk::",
             ":kkkkkk:",
             ":::kkkk:::",
             "::kokkok::",
             "::kookkook::",
             ":koookkoook:",
             "kLoLokkoLoLk",
             ":L:LkkL:L:",
             ":y::kk::y:",
             ":y:k::L:",
             ":l::"])),
    ("A3", "Half-moon crown", "its obsidian face head-on: heavy brows over two ember slits, its bill down the middle, "
     "lava in the cracks and its half moon rising over the top like a crown", {":": OBSIDIAN, **EMBER}, egg(
         ["......yyyy......",
          ".....yLrrLy.....",
          "....yLrLLrLy...."], [
             "rlLLlr",
             ":kk::kk:",
             ":Ly::yL:",
             "::::kk::::",
             ":l::kO::l:",
             "::L::kO::L::",
             ":L:l:kO:l:L:",
             ":l::LkkL::l:",
             ":l::kk::l:",
             "::r:k::r::",
             "::L::r::",
             ":r::"])),
    ("A4", "Over its volcano", "its silhouette on the wing over its volcano, lava welling in the crater and running "
     "down the cone under a sky lit by fire", {":": EMBER_SKY, "b": BASALT, **EMBER}, egg(
         ["................",
          "................",
          ".......::......."], [
             "::::::",
             "k::kk::k",
             ":k:kk:k:",
             ":::kkkk:::",
             ":y::kk::y:",
             "::::LyyL::::",
             ":::bLLLLb:::",
             "::bbLbbLbb::",
             "bbLbbbbLbb",
             "bLbbbbbbLb",
             "lbbbbbbl",
             "bbbb"])),
    ("A5", "Its eye", "its ember-slit eye up close under a heavy obsidian brow, a black slit in a glowing iris, lava "
     "cracks round it", {":": OBSIDIAN, **EMBER}, egg(
         ["................",
          "................",
          ".......::......."], [
             "::::::",
             "::l:::::",
             ":OOOOOO:",
             "kkkkkkkkkk",
             ":rlLLLLlr:",
             ":rLyykkyyLr:",
             ":rLyykkyyLr:",
             "::rlLkkLlr::",
             "::rrllrr::",
             ":::::::l::",
             ":l::::r:",
             "::::"])),
]

EMBERYOUNG_ROUND1 = [
    ("Y1", "Hatching", "its molten head bursting out of the top of its obsidian egg, round amber eyes, little soft "
     "horns, shards of shell flying", {":": MAGMA, **EMBER, **YOUNG, **SHELL}, egg(
         ["......C..C......",
          "......C..C..4...",
          "...5.::::::....."], [
             "::::::",
             ":wA::wA:",
             ":Ak::Ak:",
             "c:::yy:::c",
             "4::LyyL::4",
             "45c::LL::c54",
             "4435r::r5344",
             "334343rr4343",
             "2332rl2332",
             "2212r21222",
             "11212111",
             "1111"])),
    ("Y2", "Molten crust", "its hide: glowing magma under a thin, cracked obsidian crust, its little half moon on top",
     {":": MAGMA, **EMBER, **YOUNG}, egg(
         ["................",
          ".......yy.......",
          "......yLLy......"], [
             ":cc:::",
             "::Ccc:::",
             ":c:::y:c",
             ":Cc:::Ccc:",
             "::c:y::cC:",
             ":Cc::yw::c::",
             "::::Ccc::y::",
             ":y::cC:::Cc:",
             ":::::c::c:",
             ":Cc::::Cc:",
             "::c:y:::",
             ":::c"])),
    ("Y3", "Wings out", "the young seen from above over a smoky violet sky: its bill and little half moon "
     "pointing up, round amber eyes, molten wings dropping embers", {":": SMOKE, "m": MAGMA, **EMBER, **YOUNG}, egg(
         ["................",
          "................",
          "......cyyc......"], [
             ":cLLc:",
             "::cLLc::",
             ":cAccAc:",
             ":::cmmc:::",
             "::cmccmc::",
             "::cmmccmmc::",
             ":cmmmccmmmc:",
             "cymymccmymyc",
             ":y:yccy:y:",
             ":L::cc::L:",
             ":L:c::y:",
             ":y::"])),
    ("Y4", "Molten head", "its molten head side-on: a round amber eye, a short soft horn, the little glowing half "
     "moon on its bill, crust flaking off its neck", {":": SMOKE, "m": MAGMA, **EMBER, **YOUNG}, egg(
         ["................",
          "................",
          "....C..::......."], [
             "C:::::",
             ":C:::yy:",
             ":mm:ylly",
             "mwAmylrrly",
             "mAkmyrrrry",
             ":mmmmcccccc:",
             ":mmmmmmmmmmm",
             ":mmmmaaaa:::",
             "mcmmm:::::",
             "mmcmm:::::",
             "mmymcm::",
             "mcmm"])),
    ("Y5", "Peeking out", "its obsidian egg, near black at the foot and violet at the top, cracked and glowing, one "
     "round amber eye looking out of a hole", {"m": MAGMA, **EMBER, **YOUNG, **SHELL}, egg(
         ["................",
          "................",
          ".......55......."], [
             "4555l4",
             "4555L554",
             "45LmmL44",
             "34LmwAmL43",
             "34LmAkmL43",
             "3344LmmL4433",
             "2333L3333l32",
             "223L33333l22",
             "22l232222l",
             "2r21212222",
             "11211121",
             "1111"])),
]

# ---------------------------------------------------------------- round 2 of the Emberdactyl's eggs (October 29,
# 2026): the egg with its wings spread out past the egg's outline, bat-like pterosaur wings (a thumb claw at the wrist,
# finger bones, a scalloped trailing edge), so the item reads as "an egg with wings" at a glance. Each wing is drawn
# as the left half (8 wide) and mirrored; the egg goes in front of it. Round 2 is kept as a record and no longer drawn
# (its sheets and textures are in spawn_eggs/round2/); round 3 (further down) builds on its A7 and Y8.


def at(top, rows, width=16):
    """16 rows: `rows` from row `top` down, the others empty."""
    out = ["." * width] * top + list(rows)
    return out + ["." * width] * (16 - len(out))


def mirror(half, dark=None):
    """Left halves (8 wide) made whole: the right half mirrored, its letters through `dark` (a little darker)."""
    return [r + "".join((dark or {}).get(c, c) for c in reversed(r)) for r in half]


def winged(wing, body, dark=None):
    """A wing (left halves, mirrored) behind a body (16 wide, '.' where it isn't)."""
    w = mirror(wing, dark)
    return ["".join(b if b != "." else c for b, c in zip(body[y], w[y])) for y in range(16)]


def recolor(rows, mapping):
    """The rows with letters swapped (the same wing in other materials)."""
    return ["".join(mapping.get(c, c) for c in r) for r in rows]


# the wings (left halves): '0' its thumb claw, 'V' the arm along the leading edge, 'O' the finger bones, 'k' the
# membrane, 'L' the glowing scalloped edge. Spread: the wrists high, the tips out at the egg's sides.
WING_SPREAD = at(1, [
    "..0.....",
    "..VV....",
    ".VkOV...",
    "VkOkOV..",
    "kOkkOkV.",
    "OkkOkk..",
    "LkkOkO..",
    ".LkOLO..",
    ".LLOLL..",
    "...L....",
], 8)
# raised high in a V, the tips in the top corners
WING_RAISED = at(0, [
    "0..0....",
    "VOOV....",
    "kkOkV...",
    "LkOkV...",
    "kLOkkV..",
    "LkOkkV..",
    ".LkOkk..",
    ".LkOk...",
    "..LOL...",
    "...L....",
], 8)
# level and low, under a crest
WING_LEVEL = at(3, [
    "...0....",
    ".VVOV...",
    "VkOkOV..",
    "OkOkOkV.",
    "LkOkkO..",
    "LLkOkO..",
    ".LLOLk..",
    "...LL...",
], 8)
# burning: charred bones ('0') over membranes glowing ember red ('r') to flame ('l', 'L', 'y'), embers falling
WING_FIRE = at(1, [
    "..0.....",
    "..00....",
    ".0r00...",
    "0r0r00..",
    "l0rr0r0.",
    "0ll0ll..",
    "yLL0L0..",
    ".yL0y0..",
    ".yy0yy..",
    ".y.y....",
    "...l....",
    ".L......",
    "........",
    "..l.....",
], 8)
# the young's, bursting out level from a crack round its shell's middle
WING_BURST = at(4, [
    "..0.....",
    ".VVV....",
    "VkOkV...",
    "OkOkOV..",
    "LkOkOk..",
    "yLkOkL..",
    ".yLOLy..",
    "...yy...",
], 8)

# the adult: black obsidian wings with ember-orange edges, a glossy obsidian egg (violet where the light catches it,
# lit from the top left, near black at its foot, an ember glow under it) with lava spots or a lava crack
ADULT_WINGS = {"0": (6, 4, 10), "k": (16, 10, 24), "o": (30, 20, 44), "O": (48, 34, 70), "V": (72, 54, 102),
               "v": (100, 76, 140), "g": (150, 124, 190), "w": (255, 250, 228), "r": (122, 34, 12),
               "l": (214, 78, 14), "L": (255, 150, 30), "y": (255, 232, 130)}
ADULT_DARK = {"o": "k"}
SPOTTED_EGG = at(4, [
    ".......gv.......",
    "......gwgv......",
    ".....ggvvvV.....",
    ".....gvvLlV.....",
    "....vvvvlrVO....",
    "....vvvvVVVO....",
    "....vlLVVVOO....",
    "....VVlVOLlo....",
    "....OVOOOlOo....",
    ".....rOOOOo.....",
    "......rrrr......",
])
LOW_EGG = at(5, [
    ".......gv.......",
    "......gwgv......",
    ".....ggvLlV.....",
    ".....gvvlrV.....",
    "....vvvvVVVO....",
    "....vlLVVVOO....",
    "....VVlVOLlo....",
    "....OVOOOlOo....",
    ".....rOOOOo.....",
    "......rrrr......",
])
CRACKED_EGG = at(4, [
    ".......gv.......",
    "......gwgv......",
    ".....ggvvvV.....",
    ".....gvvVVO.....",
    "....vvvlVVOO....",
    "....vvlLlVOO....",
    "....VVVLVOOo....",
    "....VVlLOOoo....",
    "....OOOlLOoo....",
    ".....OOrLoo.....",
    "......rrlr......",
])
# its glowing half-moon crest standing on top, its ember-slit eyes on the egg
CREST_EGG = at(1, [
    "......yLLy......",
    ".....yLrrLy.....",
    ".....LrrrrL.....",
    "......OVVO......",
    ".....gvvvVO.....",
    ".....vvwvVO.....",
    "....0LyO0yL0....",
    "....vVVVVVOO....",
    "....vVVVlVOO....",
    "....VVVlLOOo....",
    "....OOlLOOoo....",
    "....OOOlOooo....",
    ".....rOOOoo.....",
    "......rrrr......",
])

EMBERDACTYL_ROUND2 = [
    ("A6", "Wings spread", "its glossy obsidian egg with lava spots, and its black wings spread out past the egg on "
     "both sides: thumb claws, finger bones, scalloped edges glowing ember orange", ADULT_WINGS,
     winged(WING_SPREAD, SPOTTED_EGG, ADULT_DARK)),
    ("A7", "Wings raised", "the egg lower down, its wings raised high in a V behind it, their tips in the top corners, "
     "their glowing edges facing in", ADULT_WINGS, winged(WING_RAISED, LOW_EGG, ADULT_DARK)),
    ("A8", "Wrapped in its wings", "its wings folded round the egg like a cloak, thumb claws up over its shoulders, the "
     "egg showing between their glowing edges", ADULT_WINGS, at(1, [
         "..0..........0..",
         "..V0........0V..",
         ".0VO........OV0.",
         ".VkO...gv...OkV.",
         ".VkkO.gwgv.OkkV.",
         ".VkOLggvvvVLOkV.",
         ".VkOkgvvLvVkOkV.",
         ".VOkLvvlVVOLkOV.",
         ".VkOkLvLlVLkOkV.",
         "..VkOkLlVLkOkV..",
         "..VkkOkLLkOkkV..",
         "...VkOkkkkOkV...",
         "....LkOkkOkL....",
         ".....LyLLyL....."])),
    ("A9", "Wings of fire", "its wings burning: charred finger bones over membranes glowing from ember red to "
     "white-hot flame, embers falling, the egg split by a lava crack", ADULT_WINGS,
     winged(WING_FIRE, CRACKED_EGG, ADULT_DARK)),
    ("A10", "Half-moon crest", "the egg wearing its glowing half-moon crest and ember-slit eyes, its wings spread low "
     "at its sides", ADULT_WINGS, winged(WING_LEVEL, CREST_EGG, ADULT_DARK)),
]

# the young: the same wings in its own materials (molten membranes 'k', dark crust bones 'O' and arms 'V', white-hot
# edges), its obsidian shell ('1' near black at the foot to '5' violet at the top, 'g' its glint), its molten hide
# ('M', 'm', 'r' under crust 'c'), round amber eyes ('w' glint, 'A' amber, 'e' pupil)
YOUNG_WINGS = {"0": (24, 12, 16), "k": (226, 96, 18), "o": (196, 66, 14), "O": (66, 34, 40), "V": (100, 56, 60),
               "r": (150, 40, 10), "l": (255, 150, 36), "L": (255, 206, 90), "y": (255, 240, 170),
               "w": (255, 252, 230), "c": (34, 18, 26), "C": (64, 38, 50), "m": (214, 84, 16), "M": (244, 128, 26),
               "1": (12, 8, 16), "2": (26, 18, 36), "3": (44, 30, 62), "4": (68, 50, 96), "5": (98, 74, 136),
               "g": (150, 124, 190), "A": (255, 184, 40), "e": (14, 8, 12)}
YOUNG_DARK = {"k": "o"}
SHELL_EGG = at(4, [
    ".......54.......",
    "......5g44......",
    ".....554443.....",
    ".....54l433.....",
    "....44lL4333....",
    "....443lL332....",
    "....3334l322....",
    "....333lL222....",
    "....2223l221....",
    ".....22l211.....",
    "......r11r......",
])
# split round its middle, shards flying off the top
SPLIT_SHELL = at(0, [
    "..5..........4..",
    "....4.......5...",
    ".......54.......",
    "......5g44......",
    ".....554443.....",
    ".....544433.....",
    "....yLlyLLyl....",
    "....4Ll43l33....",
    "....44l43332....",
    "....3334l322....",
    "....333lL222....",
    "....2223l221....",
    ".....22l211.....",
    "......r11r......",
])
MOLTEN_EGG = at(4, [
    ".......yL.......",
    "......ywLM......",
    ".....yLLcMm.....",
    ".....LLcMMm.....",
    "....LLMMMcmr....",
    "....LcMMmcmr....",
    "....MMcmmmrr....",
    "....MMmmcrrc....",
    "....mmmcrrcr....",
    ".....mcrrcr.....",
    "......rrcr......",
])
# the molten young itself, round amber eyes under the top of its shell, still on its head like a cap
SHELL_CAP = at(3, [
    ".......54.......",
    "......5g44......",
    ".....554443.....",
    ".....45443L.....",
    ".....4L3M3L.....",
    "....MwAMMwAm....",
    "....LAeMMAem....",
    "....LMMcMMmm....",
    "....MMMMcmmr....",
    "....MmcMmmrr....",
    ".....mmrrcr.....",
    "......rrcr......",
])

EMBERYOUNG_ROUND2 = [
    ("Y6", "Molten wings", "its obsidian egg, near black at the foot and violet at the top, a glowing crack down it, "
     "and the young's molten wings spread out past it, veined with dark crust", YOUNG_WINGS,
     winged(WING_SPREAD, SHELL_EGG, YOUNG_DARK)),
    ("Y7", "Bursting out", "its shell splitting round the middle, molten wings bursting out of the crack on both "
     "sides, shards of shell flying", YOUNG_WINGS, winged(WING_BURST, SPLIT_SHELL, YOUNG_DARK)),
    ("Y8", "Molten egg", "a molten egg under a cracked crust, its dark crust wings raised in a V behind it, veined "
     "with glowing lava", YOUNG_WINGS, winged(recolor(WING_RAISED, {"k": "c", "O": "l", "V": "C"}), MOLTEN_EGG)),
    ("Y9", "Wrapped in its wings", "its molten wings folded round its obsidian egg like a cloak, their hot edges "
     "glowing at the front", YOUNG_WINGS, at(1, [
         "..0..........0..",
         "..V0........0V..",
         ".0VO........OV0.",
         ".VkO...54...OkV.",
         ".VkkO.5g44.OkkV.",
         ".VkkL554443LkkV.",
         ".VkOk54l433kOkV.",
         ".VkkL4lL433LkkV.",
         ".VkOkL3lL3LkOkV.",
         "..VkOkLl2LkOkV..",
         "..VkkOkLLkOkkV..",
         "...VkOkkkkOkV...",
         "....LkOkkOkL....",
         ".....LyLLyL....."])),
    ("Y10", "Shell cap", "the molten young itself, round amber eyes peeking from under the top of its shell, still on "
     "its head like a cap, and its dark crust wings spread at its sides", YOUNG_WINGS,
     winged(recolor(WING_LEVEL, {"k": "c", "O": "r", "V": "C"}), SHELL_CAP)),
]

# ---------------------------------------------------------------- round 3 of the Emberdactyl's eggs (October 29,
# 2026; the user: "for the eggs i want the wings are cool the A7 one now i want the bird features and Y8 for young but
# again i want bird features"): A7's wings raised in a V, or Y8's molten egg and its crust wings, with the bird's own
# features on it. The adult: its head in profile (its long bill with the half moon standing along it, a glowing rim
# round a dark disc, two blade horns swept back, an ember-slit eye), glossy violet obsidian against its black wings so
# it stands out. The young: plain round amber eyes in dark crust rims (friendly, never slits), a small beak, little
# horns and a little half moon, everything molten.
YOUNG_RAISED = recolor(WING_RAISED, {"k": "c", "O": "l", "V": "C"})          # Y8's crust wings


def rows16(rows):
    """Rows from the top down, the rest empty (16 rows in all)."""
    return list(rows) + ["." * 16] * (16 - len(rows))


# the adult's head in profile, facing left, perched on the egg (rows 0-6), A7's egg under it
PERCHED_HEAD = rows16([
    "...LyL.....vg...",
    "..LrrrL...vO....",
    "..LrkrL..OVO....",
    ".kvvvvvvvVyO....",
    "0vvvvvvvVVlk....",
    ".kOrrrrOOVk.....",
    "......gwgO......",
] + LOW_EGG[7:15])
# facing right, its beak gaping in a screech, its throat glowing
SCREECHING_HEAD = rows16([
    "...gv....LyL....",
    "....Ov..LrrrL...",
    "....OVO.LrkrLvg.",
    "....OyVvvvvvvvv.",
    "....klVVlLLLyy..",
    ".....kVOOOVlLy..",
    "......OwgvOVVvg.",
] + LOW_EGG[7:15])
# bursting out of the egg: the top of the shell broken off (shards flying), lava welling at the jagged rim
HATCHING_HEAD = rows16([
    "...LyL..........",
    "..LrrrL.......g.",
    "..LrkrL..OVO.gv.",
    ".kvvvvvvvVyO....",
    "0vvvvvvvVVlk..v.",
    ".kOrrrrOOVk.....",
    ".....kOVVO......",
    "....v.LOOL.v....",
    "....LLlrrlLL....",
    "....vlrlrlVO....",
] + LOW_EGG[10:15])

EMBERDACTYL = [
    ("A11", "Perched head", "A7's wings raised in a V, and its head in profile on top of the egg: the long obsidian "
     "bill with its glowing half moon standing along it, its blade horns swept back, an ember-slit eye", ADULT_WINGS,
     winged(WING_RAISED, PERCHED_HEAD, ADULT_DARK)),
    ("A12", "Screech", "A7's raised wings, its head turned the other way and its bill gaping in a screech, the throat "
     "glowing, the half moon on its upper bill", ADULT_WINGS, winged(WING_RAISED, SCREECHING_HEAD, ADULT_DARK)),
    ("A13", "Breaking out", "A7's raised wings, its head and long bill bursting out of the top of the egg, the shell "
     "broken off in shards, lava welling at the jagged rim", ADULT_WINGS,
     winged(WING_RAISED, HATCHING_HEAD, ADULT_DARK)),
]

# the young: Y8's molten egg is its face: round amber eyes, a small dark beak, little horns and a half moon on top
MOLTEN_FACE = rows16([
    "................",
    "................",
    "................",
    ".....C.yy.C.....",
    "......cLlc......",
    "......yLMM......",
    ".....yLMMMm.....",
    ".....ccMMcc.....",
    "....cAwMMAwc....",
    "....cAACcAAc....",
    "....McmCcmcr....",
    "....MMmmcrrc....",
    "....mmmcrrcr....",
    ".....mcrrcr.....",
    "......rrcr......",
])
# its head in profile on top of the molten egg: a short glowing bill, its little half moon, a round amber eye
MOLTEN_PROFILE = rows16([
    "...yLy.....C....",
    "..yrrry...cC....",
    "..yrcry..cMc....",
    "..cyLLLLMMwAc...",
    ".yLLLLLLMMAec...",
    "..ccccccMMMc....",
    "......cmMMc.....",
] + MOLTEN_EGG[7:15])
# its head popping up out of the broken top of the molten egg, lava at the jagged rim
MOLTEN_HATCHING = rows16([
    "................",
    ".....C.yy.C.....",
    "......cLlc......",
    ".....cMMMMc.....",
    "....cAwMMAwc....",
    "....cAACcAAc....",
    ".....cmCcmc.....",
    "....LyLclyLl....",
    "....lMlMMcml....",
] + MOLTEN_EGG[9:15])

EMBERYOUNG = [
    ("Y11", "Molten face", "Y8's molten egg and crust wings, the egg its face: round amber eyes in dark crust rims, a "
     "small dark beak, little horns and its little half moon on top", YOUNG_WINGS, winged(YOUNG_RAISED, MOLTEN_FACE)),
    ("Y12", "Head in profile", "Y8's crust wings, and the young's head in profile on top of its molten egg: a short "
     "glowing bill with its little half moon, a round amber eye, a little horn", YOUNG_WINGS,
     winged(YOUNG_RAISED, MOLTEN_PROFILE)),
    ("Y13", "Popping out", "Y8's crust wings, the young's face popping up out of the broken top of its molten egg: "
     "round amber eyes, little horns and half moon, lava at the jagged rim", YOUNG_WINGS,
     winged(YOUNG_RAISED, MOLTEN_HATCHING)),
]
# the designs whose wings may (and must) reach out past the egg's outline
WINGS_OUT = {d[0] for d in EMBERDACTYL_ROUND2 + EMBERYOUNG_ROUND2 + EMBERDACTYL + EMBERYOUNG}

DESIGNS = {"huntsman": HUNTSMAN, "cinder_colossus": COLOSSUS, "rat_king": RAT_KING,
           "emberdactyl": EMBERDACTYL, "emberyoung": EMBERYOUNG}


def check(boss, design):
    """Every row 16 wide, every letter in the palette, nothing outside the egg but over its top; a winged design
    (WINGS_OUT) may reach out anywhere, and its wings must: past the egg's outline on both sides."""
    label, _name, _note, pal, rows = design
    problems = []
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        problems.append(f"{boss} {label}: not 16 x 16 ({[len(r) for r in rows]})")
        return problems
    sides = set()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in pal:
                problems.append(f"{boss} {label}: '{ch}' at {x},{y} isn't in its palette")
            elif not inside(x, y) and y > OVER_TOP:
                if label in WINGS_OUT:
                    sides.add("left" if x < 8 else "right")
                else:
                    problems.append(f"{boss} {label}: {x},{y} is outside the egg")
    if label in WINGS_OUT and sides != {"left", "right"}:
        problems.append(f"{boss} {label}: its wings don't reach out past the egg on both sides ({sorted(sides)})")
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
    title = f"{TITLES[boss]}: spawn egg options{SUBTITLES.get(boss, '')}"
    # wide enough for the title too (a sheet of only three options is narrower than a long title)
    w = max(len(designs) * cell + 40, int(ImageDraw.Draw(Image.new("RGBA", (1, 1))).textlength(title, font=font(22)))
            + 40)
    h = 16 * big + 290
    out = Image.new("RGBA", (w, h), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 14), title, font=font(22), fill=(235, 238, 245))
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
        path = os.path.join(PREVIEWS[boss], SHEET_FILES.get(boss, "options.png"))
        sheet(boss, designs).save(path)
        print("wrote", os.path.relpath(path, ROOT))
        if boss in OPTION_TEXTURES:
            folder = os.path.join(PREVIEWS[boss], "textures")
            os.makedirs(folder, exist_ok=True)
            for design in designs:
                draw(design).save(os.path.join(folder, f"{design[0]}.png"))
            print("wrote", os.path.relpath(folder, ROOT), "(each option's texture)")


if __name__ == "__main__":
    main(sys.argv[1:])
