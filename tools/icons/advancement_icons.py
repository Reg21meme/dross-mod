"""
The bosses' advancement icons (the user, October 29, 2026: "change the advancement image of the Emberdactyl and the
Huntsman to something more creative than just their spawn eggs"). An advancement shows a 16x16 item, so each icon is a
small picture of the deed, drawn as pixel art (rows of letters, '.' empty; each design's palette says what a letter
is), shaded like vanilla's items (a dark outline, light from the top left). In the game they're icon-only items
(dross:wings_clipped_icon, dross:hunter_hunted_icon, dross:set_in_stone_icon, dross:down_the_drain_icon: not in any
creative tab) that the advancements' JSONs point at. Then (the same day): the Huntsman's eyes crossed out (H4, H5),
and the Cinder Colossus's "Set in Stone" (S1-S3; it was plain obsidian) and the Rat King's "Down the Drain" (D1-D3;
it was his spawn egg) drawn too.

  python tools/icons/advancement_icons.py   the option sheets (boss-previews/cinder_wastes/emberdactyl/
                                            advancement_icon/options.png, boss-previews/rimefall/huntsman/
                                            advancement_icon/options.png, boss-previews/cinder_wastes/
                                            cinder_colossus/advancement_icon/options.png, boss-previews/blightwood/
                                            rat_king/advancement_icon/options.png), every option's texture on its own
                                            (<that folder>/textures/<label>.png) and the picked icons' textures
                                            (src/main/resources/assets/dross/textures/item/<icon>.png)

To use another option, change PICKS and run it again.
"""
import io
import os
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "item")
FRAME_TEXTURE = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "block",
                             "dross_portal_frame.png")         # the Dross tab's background
# Minecraft's own advancement frames (preview only: read from the game jar in the Gradle cache, never shipped)
JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "1.20.1",
                   "client-extra.jar")

ICONS = {"wings_clipped": "wings_clipped_icon", "hunter_hunted": "hunter_hunted_icon",      # the item each one is
         "set_in_stone": "set_in_stone_icon", "down_the_drain": "down_the_drain_icon"}
PREVIEWS = {"wings_clipped": os.path.join(ROOT, "boss-previews", "cinder_wastes", "emberdactyl", "advancement_icon"),
            "hunter_hunted": os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "advancement_icon"),
            "set_in_stone": os.path.join(ROOT, "boss-previews", "cinder_wastes", "cinder_colossus", "advancement_icon"),
            "down_the_drain": os.path.join(ROOT, "boss-previews", "blightwood", "rat_king", "advancement_icon")}
TITLES = {"wings_clipped": "Wings Clipped (beat the Emberdactyl and its young reborn)",
          "hunter_hunted": "The Hunter Hunted (bring down the Huntsman and his Alpha Moose)",
          "set_in_stone": "Set in Stone (defeat the Cinder Colossus)",
          "down_the_drain": "Down the Drain (end the Rat King's reign beneath Blightwood)"}
# not picked yet (October 29, 2026): option 1 of each stands in until the user picks, but the Huntsman's H4 (the
# user: "for the huntsman achievement make it so that like his eyes are crossed out died")
PICKS = {"wings_clipped": "W1", "hunter_hunted": "H4", "set_in_stone": "S1", "down_the_drain": "D1"}

# ---------------------------------------------------------------- the Emberdactyl: its obsidian hide (black to violet,
# lit from the top left), its scalloped membrane, lava and embers (the Fire Necromancer's colours, never the Dross blue)
EMBER = {"k": (14, 8, 20), "o": (32, 22, 46), "O": (58, 42, 84), "V": (92, 70, 130), "v": (132, 104, 176),
         "m": (60, 34, 76), "M": (92, 54, 108), "n": (122, 80, 140),                # the membrane, lighter than bone
         "r": (130, 36, 14), "l": (214, 80, 16), "L": (255, 150, 30), "y": (255, 228, 120), "w": (255, 250, 228)}
# an iron sword (W3): its blade and crossguard, a dark leather grip
IRON = {"i": (36, 38, 44), "I": (110, 114, 124), "j": (176, 182, 194), "J": (232, 236, 242),
        "g": (60, 38, 26), "G": (104, 66, 40), "h": (146, 98, 60)}

# ---------------------------------------------------------------- the Huntsman: his wolf hood (tools/eggs/eggs.py's
# WOLF_HOOD, FACE, BONE, ANTLER), his ice-fletched arrows and spear (the Moonsilver Spear's ice), the Alpha Moose's
# frosted palm antlers (its skull's bone, icicles)
HOOD = {"1": (30, 32, 40), "2": (46, 48, 58), "3": (70, 74, 88), "4": (98, 104, 120), "f": (176, 188, 204),
        "p": (64, 54, 62), "n": (16, 16, 20), "k": (8, 8, 10), "K": (24, 24, 30), "E": (176, 236, 255),
        "e": (64, 152, 236), "d": (70, 86, 104), "a": (232, 226, 210)}
ANTLER = {"x": (84, 70, 52), "T": (150, 132, 104), "t": (196, 180, 150), "u": (228, 216, 190)}
SHAFT = {"S": (60, 43, 30), "s": (96, 70, 48), "q": (128, 96, 64)}
ICE = {"B": (70, 120, 176), "b": (112, 166, 218), "c": (168, 210, 240), "C": (232, 246, 252),
       "g": (53, 61, 82), "H": (134, 146, 173), "h": (210, 218, 234), "W": (242, 245, 252)}
BONE = {"z": (96, 90, 80), "Z": (150, 144, 132), "v": (196, 190, 176), "V": (228, 224, 214), "Y": (250, 250, 244),
        "k": (60, 56, 50)}                                                         # its eye sockets and nostrils
WOOD = {"w": (52, 34, 22), "W": (86, 58, 36), "m": (118, 82, 50), "M": (150, 108, 68), "o": (196, 160, 70)}
HUNT = {**HOOD, **ANTLER, **SHAFT, **ICE}


# ---------------------------------------------------------------- drawing helpers

def blank():
    return [["."] * 16 for _ in range(16)]


def bevel(mask, outline, dark, mid, lit):
    """A solid shape shaded like a vanilla item: `mask` is 16 rows with '#' where it is; its edge pixels are `outline`,
    the inside pixels next to its top or left edge `lit`, those next to its bottom or right edge `dark`, the rest
    `mid` (letters of the design's palette)."""
    def on(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and mask[y][x] == "#"

    def edge(x, y):
        return on(x, y) and not (on(x - 1, y) and on(x + 1, y) and on(x, y - 1) and on(x, y + 1))
    out = blank()
    for y in range(16):
        for x in range(16):
            if not on(x, y):
                continue
            if edge(x, y):
                out[y][x] = outline
            elif edge(x - 1, y) or edge(x, y - 1):
                out[y][x] = lit
            elif edge(x + 1, y) or edge(x, y + 1):
                out[y][x] = dark
            else:
                out[y][x] = mid
    return ["".join(r) for r in out]


def spans(ranges):
    """A mask from {row: [(first, last), ...]}."""
    out = blank()
    for y, rs in ranges.items():
        for a, b in rs:
            for x in range(a, b + 1):
                out[y][x] = "#"
    return ["".join(r) for r in out]


def stack(*layers):
    """Layers of 16 rows each, later ones over earlier ones ('.' lets the one under it show)."""
    out = blank()
    for layer in layers:
        for y, row in enumerate(layer):
            for x, ch in enumerate(row):
                if ch != ".":
                    out[y][x] = ch
    return ["".join(r) for r in out]


def put(rows, changes):
    """Rows with single pixels changed: {(x, y): letter}."""
    out = [list(r) for r in rows]
    for (x, y), ch in changes.items():
        out[y][x] = ch
    return ["".join(r) for r in out]


# ---------------------------------------------------------------- Wings Clipped: the Emberdactyl's wing

# W1: one wing spread, its long finger sheared off on a glowing cut, the tip tumbling away, embers falling
CLIPPED_WING = [
    "................",
    "...........k....",
    "...v....kkkvL...",
    "....vkkkvvvOy...",
    "....kVvvOOOnL...",
    "....kvOOonMMy...",
    "...kvOnonooML...",
    "...kvOnonMMol..L",
    "..kvOnMMonMMr...",
    "..kvOnMMoMMl.y..",
    ".kvOnMMMoMl...kk",
    ".kvOnMMMMol..kvy",
    ".kvOnMMllo..kvOL",
    "kvOnlL......kOl.",
    "kvOl...y.....kl.",
    ".kk..........L.."]

# W2: the wing's long finger snapped, its outer half hanging at a broken angle, lava welling at the break, the membrane
# torn between them
SNAPPED_WING = [
    "................",
    "................",
    "...v....kkk.....",
    "....vkkkvvvLk...",
    "....kVvvOOOyLk..",
    "....kvOOonMLvOk.",
    "...kvOnonoo.kvO.",
    "...kvOnonM.l.kvk",
    "..kvOnMMon.L.kOk",
    "..kvOnMMoMl...kl",
    ".kvOnMMMoMl....L",
    ".kvOnMMMMol.....",
    ".kvOnMMllo...y..",
    "kvOnlL..........",
    "kvOl...y........",
    ".kk............."]

# W3: the whole wing pinned to the ground by your sword, driven through its membrane, lava welling round the blade
WING = [
    "..............kk",
    "...........kkkvv",
    "...v....kkkvvvOO",
    "....vkkkvvvOOOL.",
    "....kVvvOOOnnl..",
    "....kvOOonMMMl..",
    "...kvOnonooMMl..",
    "...kvOnonMMool..",
    "..kvOnMMonMMMo..",
    "..kvOnMMoMMl....",
    ".kvOnMMMoMl.....",
    ".kvOnMMMMol.....",
    ".kvOnMMllo......",
    "kvOnlL..........",
    "kvOl............",
    ".kk............."]
PINNING_SWORD = [
    "Ji..............",
    "ihg.............",
    ".gGg.Ji.........",
    "..gGIi..........",
    "...IJji.........",
    "..IiiJji........",
    ".....iJji.......",
    "......iJji......",
    "......LiJji.....",
    ".......yiJji....",
    ".........iJji...",
    "..........iJji..",
    "...........iJji.",
    "............iji.",
    "...........lLyLl",
    "..........l..L.."]
PINNED_WING = stack(WING, PINNING_SWORD)

WINGS = [
    ("W1", "The clipped wing", "one obsidian wing spread, its long finger sheared off on a glowing cut, the tip "
     "tumbling away in embers", EMBER, CLIPPED_WING),
    ("W2", "The broken wing", "its wing's long finger snapped, the outer half hanging at a broken angle, lava "
     "welling at the break and the membrane torn", EMBER, SNAPPED_WING),
    ("W3", "Pinned down", "its whole wing pinned to the ground by your sword, driven through the membrane, lava "
     "welling round the blade", {**EMBER, **IRON}, PINNED_WING),
]


# ---------------------------------------------------------------- The Hunter Hunted: the Huntsman

# his antlers rising out of his hood, both sides
ANTLERS = [
    "u.u..........u.u",
    "t.tu........ut.t",
    ".tTt........tTt.",
    "..Tx........xT..",
] + ["." * 16] * 12

# H1: his wolf hood (eggs.py's H2, lower, under his antlers) with an ice-fletched arrow clean through it: the
# fletching out of one side, the head out of the other
PIERCED_HOOD = stack(ANTLERS, [
    "................",
    "................",
    "....1......1....",
    "....2p....p2....",
    "c...2p2f32p2....",
    "bc..2n344fn2.H..",
    "SBss23344332sHhW",
    "bc.1234nn4321H..",
    "c..122k44k221...",
    "..12kka33akk21..",
    "..12keEkkEek21..",
    "..12kkkkkkkk21..",
    "...1kkkkkkkk1...",
    "...KkkkkkkkkK...",
    "....KkkkkkkK....",
    "......KKKK......"])

# H3: his hood mounted on a wooden plaque, a hunter's trophy: the hunter's own head on your wall, his eyes gone dark
PLAQUE = bevel(spans({3: [(3, 12)], 4: [(2, 13)], **{y: [(1, 14)] for y in range(5, 12)}, 12: [(2, 13)],
                      13: [(3, 12)], 14: [(5, 10)], 15: [(7, 8)]}), "w", "W", "m", "M")
MOUNTED_HOOD = put(stack(PLAQUE, ANTLERS, [
    "................",
    "................",
    "....1......1....",
    "....2p....p2....",
    "....2p2f32p2....",
    "....2n344fn2....",
    "....23344332....",
    "....234nn432....",
    "....2k4444k2....",
    "....kka33akk....",
    "....kdkkkkdk....",
    "....kkkkkkkk....",
    ".....kkkkkk.....",
    "................",
    "................",
    "................"]), {(7, 13): "o", (8, 13): "o"})

# H2: the Alpha Moose's skull, front on, its great palm antlers hung with icicles, and his ice spear snapped in two
# under it (the head half on the left, the butt half on the right, splinters at both broken ends)
MOOSE_SKULL = [
    "V.V.V......V.V.V",
    "vVvVvV....VvVvVv",
    "zvVVVvZ..ZvVVVvz",
    ".zvVVvvZZvvVVvz.",
    "..zZZvVVVVvZZz..",
    ".c.c.zVYYVz.c.c.",
    ".b.b.zvVVvz.b.b.",
    "...b.zkvvkz.b...",
    ".....zvVVvz.....",
    "......vVVv......",
    "......vVVZ......",
    "......zvvz......",
    "......zkkz......",
    ".......zz.......",
    "................",
    "................"]
SNAPPED_SPEAR = stack(MOOSE_SKULL, [
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "W...............",
    "hW..............",
    "Hhs.............",
    ".gSss........q.q",
    "...SSsq.....qssS",
    "......q.q..qsSS.",
    "..........sSS...",
    "........ssS.....",
    ".......SS......."])

# H4, H5 (the user, October 29, 2026: "make it so that like his eyes are crossed out died"): his glowing blue eyes
# crossed out, a cartoon X of dead white for each, in his black face (rows 10-12, under the hood's fangs)
DEAD = {"X": (210, 214, 222)}
X_EYES = {(5, 10): "k", (6, 10): "k", (9, 10): "k", (10, 10): "k",
          (4, 10): "X", (6, 10): "X", (5, 11): "X", (4, 12): "X", (6, 12): "X",
          (9, 10): "X", (11, 10): "X", (10, 11): "X", (9, 12): "X", (11, 12): "X"}
# his hood and antlers alone (H1 without the arrow)
BARE_HOOD = stack(ANTLERS, [
    "................",
    "................",
    "....1......1....",
    "....2p....p2....",
    "....2p2f32p2....",
    "....2n344fn2....",
    "....23344332....",
    "...1234nn4321...",
    "...122k44k221...",
    "..12kka33akk21..",
    "..12keEkkEek21..",
    "..12kkkkkkkk21..",
    "...1kkkkkkkk1...",
    "...KkkkkkkkkK...",
    "....KkkkkkkK....",
    "......KKKK......"])

HUNTED = [
    ("H1", "Pierced hood", "his wolf hood under his antlers, an ice-fletched arrow clean through it: the fletching "
     "out of one side, the head out of the other", HUNT, PIERCED_HOOD),
    ("H2", "The Alpha's skull, the broken spear", "the Alpha Moose's bone skull front on, its great palm antlers "
     "hung with icicles, and his ice spear snapped in two under it", {**BONE, **SHAFT, **ICE}, SNAPPED_SPEAR),
    ("H3", "The hunter's trophy", "his own hood and antlers mounted on a wooden plaque like one of his trophies, its "
     "eyes gone dark", {**HOOD, **ANTLER, **WOOD}, MOUNTED_HOOD),
    ("H4", "Pierced and crossed out", "H1, the arrow clean through his wolf hood, and his glowing blue eyes crossed "
     "out: dead", {**HUNT, **DEAD}, put(PIERCED_HOOD, X_EYES)),
    ("H5", "Crossed out", "his wolf hood under his antlers with no arrow, his eyes crossed out: dead",
     {**HUNT, **DEAD}, put(BARE_HOOD, X_EYES)),
]


# ---------------------------------------------------------------- Set in Stone: the Cinder Colossus (the user, October
# 29, 2026: "make custom advancement for the colossus as well not just obsidian"). Its scorched stone, obsidian
# (violet black, glossy), the last of its lava; fire colours only, never the Dross blue
COLOSSUS = {"k": (14, 10, 16), "x": (4, 2, 6), "2": (48, 42, 40), "3": (70, 62, 58), "4": (96, 86, 80),
            "5": (126, 114, 106), "o": (24, 16, 34), "O": (46, 32, 64), "V": (76, 56, 106), "g": (140, 116, 180),
            "r": (110, 30, 12), "l": (200, 72, 14), "L": (255, 150, 30), "y": (255, 226, 120),
            "s": (150, 146, 150), "S": (104, 100, 106), "t": (196, 192, 196)}

# S1: its cracked three-eyed face cooling: the brow still scorched stone, its three eyes dimming to a last red, the
# cooling front a crack of fading lava, its jaw and beard of shards gone to obsidian, its horns
COOLING_FACE = [
    ".k............k.",
    ".kO..........Ok.",
    "..kO.kkkkkk.Ok..",
    "..kOk445544kOk..",
    "..k4554rr5545k..",
    "..k4443ll3444k..",
    "..kkkkk33kkkkk..",
    "..k3krl33lrk3k..",
    "..k33l3443l33k..",
    "..kVOOlOOlOOVk..",
    "..kOkkkkkkkkOk..",
    "..kOVrOOOOrVOk..",
    "...kOgkOOkVOk...",
    "...kOkVkkOkOk...",
    "....k.kVk.k.k...",
    ".......k........"]
# S2: its fist turned to obsidian, knuckles up, a last glow in the cracks between its fingers
OBSIDIAN_FIST = [
    "................",
    "....kkk.kkk.....",
    "...kVgVkVgVkkk..",
    "..kkOVOkOVOkVgk.",
    ".kVgkOOkOOlkOVOk",
    ".kOVkOlkOOOkOOOk",
    ".kOOkOOkOrOkOOok",
    ".kOOOkOkOOokOook",
    ".kOVgVVVVgOkook.",
    ".kOOOOOOOOOOlok.",
    "..kOOOlOOOOOook.",
    "..kOOOOrOOOook..",
    "...kOOOOOOOok...",
    "....kOOOOOok....",
    "....kVOOOOOk....",
    "....kkkkkkkk...."]
# S3: its twin volcanoes gone cold, obsidian lit from the left, an ember in one crater, a wisp of smoke from the other
COLD_VOLCANOES = [
    "............t...",
    "...........s....",
    "............S...",
    "...........s....",
    "..........kxk...",
    "..krk.....kVok..",
    "..kVok...kVgook.",
    ".kVgook..kVOook.",
    ".kVOlok.kVOOoook",
    "kVgOoookVOOOlook",
    "kOOOoooVVgOOoook",
    "kOOroooVOOOOoook",
    "kOOOooVVOOOOorok",
    "kOOOooVOOOrOoook",
    "kkkkkkkkkkkkkkkk",
    "................"]

SET_IN_STONE = [
    ("S1", "Cooling face", "its cracked three-eyed face cooling: the brow still scorched stone, its eyes dimming to a "
     "last red, a crack of fading lava, its jaw and beard of shards gone to obsidian", COLOSSUS, COOLING_FACE),
    ("S2", "Obsidian fist", "its great fist turned to obsidian, knuckles up, a last glow in the cracks", COLOSSUS,
     OBSIDIAN_FIST),
    ("S3", "Cold volcanoes", "its twin volcanoes gone cold as obsidian, an ember left in one crater, a wisp of smoke "
     "from the other", COLOSSUS, COLD_VOLCANOES),
]


# ---------------------------------------------------------------- Down the Drain: the Rat King (the user, October 29,
# 2026: "for the rat king change it up for his"). His grey-green fur, pink ears and nose, gold crown with its green gem,
# his staff's green orb, an iron sewer grate over murky water
RAT = {"k": (16, 16, 14), "1": (40, 46, 36), "2": (60, 70, 54), "3": (86, 98, 76), "4": (116, 128, 100),
       "5": (150, 160, 132), "P": (218, 130, 146), "p": (166, 92, 106), "G": (250, 204, 70), "g": (186, 132, 30),
       "d": (120, 82, 18), "h": (130, 236, 96), "H": (70, 186, 62), "q": (196, 255, 160), "W": (176, 178, 170),
       "w": (236, 236, 228), "B": (226, 216, 186), "i": (54, 56, 60), "I": (96, 98, 104), "j": (140, 142, 148),
       "x": (6, 8, 6), "m": (60, 72, 34), "s": (60, 40, 26), "S": (96, 66, 40), "t": (132, 94, 58),
       "X": (236, 236, 228)}

# D1: his gold crown, its green gem glinting, slipping down between the bars of a sewer grate
CROWN_IN_DRAIN = [
    "...k...k...k....",
    "..kGk.kGk.kGk.q.",
    "..kGGkGhGkGGk...",
    "..kGGGGGGGGGk...",
    "..kgGgGhqGgGk...",
    ".iikGGGhGGGkii..",
    ".iIjGIgIgIGjIji.",
    ".ixIgxgxgxgIxIi.",
    ".iIjIdIdIdIjIji.",
    ".imImIxIxImImIi.",
    ".iIjIjIjIjIjIji.",
    ".ixIxImIxIxImIi.",
    ".iIjIjIjIjIjIji.",
    ".imIxIxImIxIxIi.",
    ".iiiiiiiiiiiiii.",
    "................"]
# D2: his face, belly up and dead: cartoon X eyes, tongue out, whiskers drooping, his crown knocked askew
DEAD_KING = [
    "...........G....",
    ".........GgGG...",
    "..kk....GhGg.kk.",
    ".kPPk..dgggdkPPk",
    ".kPpk3333333kpPk",
    "..k3344444443k..",
    ".k33X3X44X3X33k.",
    ".k334X4444X433k.",
    ".k33X3X44X3X33k.",
    "k3444444444444k.",
    "kWW3444PP4443WWk",
    ".k3W344PP443W3k.",
    "..k33344443333k.",
    "...k333PP333k...",
    "....kk3Pp3kk....",
    "......kkkk......"]
# D3: his staff snapped in two over a sewer grate, splinters at the break, its green orb still glowing in its claw
SNAPPED_STAFF = [
    "............hH..",
    "...........HqhH.",
    "..........HhqhH.",
    "...........HhH..",
    "..........tS....",
    ".........tS.....",
    "........BtS.....",
    "....s..B.B......",
    ".itSiiiiiiiiii..",
    ".iStIjIjIjIjIji.",
    ".ixSxIxImIxIxIi.",
    ".iIjIjIjIjIjIji.",
    ".ixIxImIxIxIxIi.",
    ".iIjIjIjIjIjIji.",
    ".iiiiiiiiiiiiii.",
    "................"]

DOWN_THE_DRAIN = [
    ("D1", "Crown down the drain", "his gold crown, its green gem glinting, slipping down between the bars of a sewer "
     "grate", RAT, CROWN_IN_DRAIN),
    ("D2", "Belly up", "his face, dead: cartoon X eyes, tongue out, whiskers drooping, his crown knocked askew", RAT,
     DEAD_KING),
    ("D3", "The broken staff", "his staff snapped in two over a sewer grate, splinters at the break, its green orb still "
     "glowing in its claw", RAT, SNAPPED_STAFF),
]
DESIGNS = {"wings_clipped": WINGS, "hunter_hunted": HUNTED, "set_in_stone": SET_IN_STONE,
           "down_the_drain": DOWN_THE_DRAIN}


# ---------------------------------------------------------------- drawing, the sheets

def check(name, design):
    """Every row 16 wide, every letter in the palette."""
    label, _name, _note, pal, rows = design
    if len(rows) != 16 or any(len(r) != 16 for r in rows):
        return [f"{name} {label}: not 16 x 16 ({[len(r) for r in rows]})"]
    return [f"{name} {label}: '{ch}' at {x},{y} isn't in its palette"
            for y, row in enumerate(rows) for x, ch in enumerate(row) if ch != "." and ch not in pal]


def draw(design):
    _label, _name, _note, pal, rows = design
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = tuple(pal[ch]) + (255,)
    return im


def slot(im, scale):
    """The icon in an inventory slot at `scale` (a slot is 18 x 18)."""
    s = Image.new("RGBA", (18, 18), (139, 139, 139, 255))
    d = ImageDraw.Draw(s)
    d.line([(0, 0), (17, 0)], fill=(55, 55, 55, 255))
    d.line([(0, 0), (0, 17)], fill=(55, 55, 55, 255))
    d.line([(0, 17), (17, 17)], fill=(255, 255, 255, 255))
    d.line([(17, 0), (17, 17)], fill=(255, 255, 255, 255))
    s.alpha_composite(im, (1, 1))
    return s.resize((18 * scale, 18 * scale), Image.NEAREST)


def frames():
    """Minecraft's challenge frame, got and not got yet (26 x 26 each), or None without the game jar."""
    try:
        with zipfile.ZipFile(JAR) as z:
            w = Image.open(io.BytesIO(z.read("assets/minecraft/textures/gui/advancements/widgets.png"))).convert("RGBA")
    except (OSError, KeyError):
        return None
    return w.crop((26, 128, 52, 154)), w.crop((26, 154, 52, 180))


def in_frame(im, frame, scale):
    """The icon as the advancement screen shows it: in its frame (the icon 5 in from its corner) on the Dross tab's
    background (the portal frame's texture, tiled)."""
    back = Image.new("RGBA", (34, 34), (0, 0, 0, 255))
    try:
        tile = Image.open(FRAME_TEXTURE).convert("RGBA").crop((0, 0, 16, 16))
        for ty in range(0, 34, 16):
            for tx in range(0, 34, 16):
                back.alpha_composite(tile, (tx, ty))
    except OSError:
        pass
    if frame is not None:
        back.alpha_composite(frame, (4, 4))
    back.alpha_composite(im, (9, 9))
    return back.resize((34 * scale, 34 * scale), Image.NEAREST)


def font(size):
    for f in ("arialbd.ttf", "arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(f, size)
        except OSError:
            pass
    return ImageFont.load_default()


def sheet(name, designs):
    big, cell = 14, 16 * 14 + 130
    w, h = len(designs) * cell + 40, 16 * big + 470
    out = Image.new("RGBA", (w, h), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 14), f"{TITLES[name]}: advancement icon options", font=font(22), fill=(235, 238, 245))
    got, not_got = frames() or (None, None)
    for i, design in enumerate(designs):
        label, title, note, _pal, _rows = design
        im = draw(design)
        x0 = 20 + i * cell
        back = Image.new("RGBA", (16 * big + 16, 16 * big + 16), (62, 66, 76, 255))
        back.alpha_composite(im.resize((16 * big, 16 * big), Image.NEAREST), (8, 8))
        out.alpha_composite(back, (x0, 52))
        y = 52 + 16 * big + 26
        d.text((x0, y), f"{label}  {title}", font=font(17), fill=(240, 240, 240))
        if PICKS[name] == label:
            d.text((x0 + 16 * big + 24, 60), "in the game\nnow", font=font(14), fill=(120, 220, 130))
        line, ly = "", y + 24
        for wd in note.split():
            if d.textlength(line + " " + wd, font=font(13)) > cell - 40:
                d.text((x0, ly), line.strip(), font=font(13), fill=(170, 176, 188))
                line, ly = "", ly + 17
            line += " " + wd
        d.text((x0, ly), line.strip(), font=font(13), fill=(170, 176, 188))
        ry = h - 34 * 3 - 18 - 18 * 3 - 30
        sx = x0                                   # in its challenge frame, got and not yet, at 1x, 2x and 3x
        for sc in (1, 2, 3):
            out.alpha_composite(in_frame(im, got, sc), (sx, ry))
            sx += 34 * sc + 10
        out.alpha_composite(in_frame(im, not_got, 2), (sx, ry))
        sx = x0                                   # in an inventory slot at 1x, 2x and 3x
        for sc in (1, 2, 3):
            out.alpha_composite(slot(im, sc), (sx, h - 18 * 3 - 18))
            sx += 18 * sc + 12
    return out


def main(argv):
    problems = [p for name, designs in DESIGNS.items() for design in designs for p in check(name, design)]
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    for name, label in PICKS.items():
        design = next(d for d in DESIGNS[name] if d[0] == label)
        path = os.path.join(TEXTURES, f"{ICONS[name]}.png")
        draw(design).save(path)
        print("wrote", os.path.relpath(path, ROOT), "from", label)
    for name, designs in DESIGNS.items():
        folder = PREVIEWS[name]
        os.makedirs(os.path.join(folder, "textures"), exist_ok=True)
        sheet(name, designs).save(os.path.join(folder, "options.png"))
        for design in designs:
            draw(design).save(os.path.join(folder, "textures", f"{design[0]}.png"))
        print("wrote", os.path.relpath(os.path.join(folder, "options.png"), ROOT), "and each option's texture")


if __name__ == "__main__":
    main(sys.argv[1:])
