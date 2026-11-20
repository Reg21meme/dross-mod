"""
Icon options for the Blind Oracle's quest advancements that still show a spawn egg (the user, November 19, 2026: "give
me 5 options for the advancements that are just spawn eggs and stuff, we need unique images for those"): Giant Slayer
(three Silverwood Giants), Into the Ashes (three of the Cinder Wastes' mobs), Beneath the Rot (three of Blightwood's
rats or witches) and First Frost (three of Rimefall's grown beasts). Drawn like the bosses' icons
(advancement_icons.py: 16 x 16 pixel art, rows of letters, a dark outline, lit from the top left).

  python tools/icons/quest_icons.py   the option sheets, quest-previews/advancement_icons/<name>/options.png, and
                                      each option's texture on its own (<that folder>/textures/<label>.png)

Options only: nothing in the game changes until the user picks.
"""
import os
import sys

from PIL import Image, ImageDraw

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import advancement_icons as A  # noqa: E402

ROOT = A.ROOT
OUT = os.path.join(ROOT, "quest-previews", "advancement_icons")
TITLES = {"giant_slayer": "Giant Slayer (bring down three Silverwood Giants)",
          "into_the_ashes": "Into the Ashes (kill three of the Cinder Wastes' mobs)",
          "beneath_the_rot": "Beneath the Rot (kill three of Blightwood's rats or witches)",
          "first_frost": "First Frost (hunt three of Rimefall's grown beasts)"}

# ---------------------------------------------------------------- Giant Slayer: the Silverwood Giants' pale ashen
# skin, near-black hair and beard, electric-blue eyes; the Boulder-Hurler's mossy cragstone, the Tree-Bearer's tree
GIANT = {"k": (16, 16, 22), "h": (26, 26, 34), "H": (46, 46, 58), "1": (84, 88, 100), "2": (112, 116, 128),
         "3": (146, 150, 160), "4": (180, 182, 190), "d": (34, 42, 64), "e": (46, 107, 255), "E": (150, 190, 255),
         "x": (6, 6, 10), "m": (46, 86, 64), "M": (82, 132, 92), "c": (82, 84, 82), "C": (118, 120, 116),
         "D": (58, 58, 58), "b": (58, 46, 40), "B": (96, 80, 66), "l": (70, 92, 124), "L": (128, 150, 182),
         "i": (36, 38, 44), "I": (110, 114, 124), "j": (176, 182, 194), "J": (232, 236, 242), "g": (60, 38, 26),
         "G": (104, 66, 40), "z": (150, 120, 60), "Z": (206, 170, 90)}

# GS1: his face, felled: a crack down his brow, his blue eyes gone dark, his beard
FELLED_FACE = [
    "................",
    "...kkkkkkkkkk...",
    "..khHhHHhHHhHk..",
    "..kH434x4434Hk..",
    "..k4444x44444k..",
    "..k4hhh4xhhh4k..",
    "..k33dd33dd33k..",
    "..k3333443333k..",
    "..k3334kk4333k..",
    "..kh33333333hk..",
    "..khhhHHHHhhhk..",
    "..khHhhhhhhHhk..",
    "...khhHhhHhhk...",
    "....khhhhhhk....",
    ".....khhhhk.....",
    "......kkkk......"]

# GS2: the Boulder-Hurler's mossy boulder split in two, blue light in the crack
SPLIT_BOULDER = [
    "................",
    "....kkk..kkk....",
    "...kMMmk.kmMMk..",
    "..kMCCmk.kmCCMk.",
    "..kCCCCk.kCCCCk.",
    ".kmCCcCk.kCcCCMk",
    ".kCCCCke.eCCCcCk",
    ".kCcCCkeEekCCCCk",
    ".kCCCCk.e.kCCCck",
    ".kCCCck...kcCCCk",
    ".kcCCDk...kDCCck",
    "..kCDDk...kDDCk.",
    "..kDDk.....kDDk.",
    "...kk.......kk..",
    "................",
    "................"]

# GS3: the Tree-Bearer's uprooted silverwood felled, lying along the ground, its roots on the left, its crown on the right
FELLED_TREE = [
    "................",
    "...........lLl..",
    "..........lLLLl.",
    ".........lLlLLLl",
    "..b......lLLlLLl",
    ".b.b....klLLLLlk",
    "b.bBkkkkkBlllLl.",
    ".bBBBBBBBBBkll..",
    "b.bBbBBbBBBk....",
    ".b.bkkkkkkk.....",
    "..b.b...........",
    "................",
    ".MmmMMmMmmMMmMm.",
    "mmMmmmmMmmmmMmmm",
    "................",
    "................"]

# GS4: their glowing sign (a ring with a spear through it) on a shield of pale stone, cracked in two
BROKEN_SIGN = [
    "................",
    ".kkkkkkkkkkkkkk.",
    ".k443444x44434k.",
    ".k4444ee x4444k.".replace(" ", "4"),
    ".k444e44ex4444k.",
    ".k44e4E4xe4444k.",
    ".k44e44Ex4e444k.",
    ".k444e4x4e4444k.",
    ".k3444eexe4443k.",
    ".k33444xE44433k.",
    "..k3334x44333k..",
    "..k333x433333k..",
    "...k33x3333k....",
    "....k3x333k.....",
    ".....kxkkk......",
    "................"]

# GS5: your sword driven point down through a giant's open hand on the ground
PINNED_HAND = [
    "......Ji........",
    "......Ji........",
    ".....gGg........",
    "....gzZzg.......",
    "......Ij........",
    "......Ij........",
    "..kk..Ij.kk.....",
    ".k4kk.Ijkk4k....",
    ".k43k.Ijk34k.kk.",
    ".k43kkIjk34kk43k",
    ".k434kIjk433k4k.",
    "..k4433j3434k3k.",
    "..k3443j44433k..",
    "...k33333333k...",
    "....k22222kk....",
    ".....kkkkk......"]

GIANT_SLAYER = [
    ("GS1", "The felled giant", "a Silverwood Giant's face, felled: a crack down his brow, his blue eyes gone dark, "
     "his black beard", GIANT, FELLED_FACE),
    ("GS2", "The split boulder", "the Boulder-Hurler's mossy boulder split in two, the giants' blue light glowing in "
     "the crack", GIANT, SPLIT_BOULDER),
    ("GS3", "Timber", "the Tree-Bearer's uprooted silverwood felled, lying along the mossy ground, roots and all",
     GIANT, FELLED_TREE),
    ("GS4", "The broken sign", "their people's glowing sign (a ring with a spear through it) on a shield of pale "
     "stone, cracked in two", GIANT, BROKEN_SIGN),
    ("GS5", "Pinned", "your sword driven point down through a giant's great grey hand", GIANT, PINNED_HAND),
]

# ---------------------------------------------------------------- Into the Ashes: the stone and lava golems, the
# Cinder Hermit's bronze bell, the Pallbearer's top hat and burning coffin, ash and embers
ASH = {"k": (14, 10, 12), "1": (40, 36, 36), "2": (62, 58, 56), "3": (90, 86, 82), "4": (124, 120, 114),
       "a": (150, 146, 140), "A": (192, 188, 182), "r": (130, 36, 14), "l": (214, 80, 16), "L": (255, 150, 30),
       "y": (255, 228, 120), "o": (34, 24, 40), "O": (64, 46, 76), "u": (110, 64, 26), "U": (168, 106, 44),
       "V": (220, 160, 80), "w": (236, 230, 214), "W": (190, 184, 170), "x": (6, 4, 6), "s": (90, 90, 96)}

# IA1: a stone golem's head, crumbling: one eye's lava gone dark, chips falling off its jaw
GOLEM_HEAD = [
    "................",
    "...kkkkkkkkk....",
    "..k443444434k...",
    "..k4333333334k..",
    "..k3kkk33kkk3k..",
    "..k3kLk33kxk3k..",
    "..k3kyk33kkk3k..",
    "..k33332333x3k..",
    "..k3k2222223kk..",
    "..k32kxxxk23k...",
    "..k3322222x3k.4.",
    "...k3333333k....",
    "....kkkk3kk..3..",
    ".........k......",
    "..........2..2..",
    "................"]

# IA2: a heap of grey ash, three embers still glowing in it
EMBER_HEAP = [
    "................",
    "................",
    ".......y........",
    "......kLk.......",
    ".....k4l4k......",
    "....k4A4a4k.....",
    "...k4aA4aa4k....",
    "...kaL44a4ak....",
    "..k4ay4aA4a4k...",
    "..ka4a4Aaa4Lak..",
    ".k4aA4a4a4ayaak.",
    ".ka4aa4Aa4a4a4k.",
    "k4a4a4a4aa4a4a4k",
    "k3333333333333 3k".replace(" ", "")[:16],
    ".kkkkkkkkkkkkkk.",
    "................"]

# IA3: the Cinder Hermit's bronze bell, empty, its glowing crack gone cold
EMPTY_BELL = [
    "................",
    ".......kk.......",
    "......kUUk......",
    ".......kk.......",
    ".....kkVVkk.....",
    "....kVUUUUuk....",
    "....kVUUxUuk....",
    "....kVUUUxuk....",
    "...kVUUUUxuuk...",
    "...kVUUUxUuuk...",
    "..kVUUUUxUuuuk..",
    ".kVVUUUUUxUuuuk.",
    ".kkkkkkkkkkkkkk.",
    "..kxxxxxxxxxxk..",
    "...kkkkkkkkkk...",
    "................"]

# IA4: the Pallbearer's top hat lying in the ash, a crack of fire in its brim
TOP_HAT = [
    "................",
    "....kkkkkkk.....",
    "...koOOOOOok....",
    "...koOoooOok....",
    "...kooooooOk....",
    "...koooooook....",
    "...kooooooOk....",
    "...krrrrrrrk....",
    "...koooooook....",
    ".kkkooooooookkk.",
    "kooooooolLooooook".replace("ook", "ok", 1)[:16],
    ".kkkkkkkkkkkkkk.",
    "..aAa4aa4aAa4a..",
    ".a4aaAa4aaa4aAa.",
    "................",
    "................"]

# IA5: a lava golem's molten core, its crust closing over it, the last of its glow
DYING_CORE = [
    "................",
    ".....kkkkkk.....",
    "...kk212222kk...",
    "..k212lL21222k..",
    "..k21lyL2212k...",
    ".k21lLLl2l221k..",
    ".k2lLyLl2Ll12k..",
    ".k22lLl22lL2k...",
    ".k122l2rr2l21k..",
    ".k1222rlr2221k..",
    "..k112rr21221k..",
    "..k1112211121k..",
    "...kk111111kk...",
    ".....kkkkkk.....",
    "................",
    "................"]

INTO_THE_ASHES = [
    ("IA1", "The crumbling golem", "a stone golem's head crumbling, one lava eye gone dark, chips falling off its "
     "jaw", ASH, GOLEM_HEAD),
    ("IA2", "Three embers", "a heap of grey ash, three embers still glowing in it: the three you put out", ASH,
     EMBER_HEAP),
    ("IA3", "The empty bell", "the Cinder Hermit's bronze bell, its crab gone, its glowing crack gone cold", ASH, EMPTY_BELL),
    ("IA4", "The fallen hat", "the Pallbearer's top hat lying in the ash, a crack of fire glowing in its brim", ASH,
     TOP_HAT),
    ("IA5", "The dying core", "a lava golem's molten core, its crust closing over it, the last of its glow", ASH,
     DYING_CORE),
]

# ---------------------------------------------------------------- Beneath the Rot: the Plague Rat's grey-green fur
# and pink tail, the plague's green, a witch's purple and green, wood and iron for a trap
ROT = {"k": (16, 16, 14), "1": (40, 46, 36), "2": (60, 70, 54), "3": (86, 98, 76), "4": (116, 128, 100),
       "5": (150, 160, 132), "P": (218, 130, 146), "p": (166, 92, 106), "B": (226, 216, 186), "b": (176, 166, 140),
       "h": (130, 236, 96), "H": (70, 186, 62), "q": (196, 255, 160), "v": (60, 30, 70), "V": (98, 52, 112),
       "u": (140, 84, 150), "s": (60, 40, 26), "S": (96, 66, 40), "t": (132, 94, 58), "i": (54, 56, 60),
       "I": (110, 112, 118), "j": (170, 172, 176), "y": (236, 206, 90), "Y": (250, 236, 150),
       "g": (176, 200, 210), "G": (220, 236, 240)}

# BR1: a rat's skull, its long front teeth, the plague's green glowing in its sockets
RAT_SKULL = [
    "................",
    "................",
    "....kkkkk.......",
    "...kBBBBBkk.....",
    "..kBBbBBBBBkk...",
    "..kBkhkBBBBBBk..",
    "..kBkqkBbBBBBBk.",
    "..kBBkBBBBBbBBBk",
    "...kbBBBkBBBBBk.",
    "....kbbkBkkBBk..",
    ".....kkBkkBkkk..",
    "......kYkkYk....",
    "......kYk.Yk....",
    ".......k...k....",
    "................",
    "................"]

# BR2: a rat trap snapped shut, its iron bar down on a crumb of cheese
RAT_TRAP = [
    "................",
    "................",
    "................",
    "...........yY...",
    "..........kyyk..",
    "..........kkk...",
    "..ijjjjjjjjji...",
    "..iIIIIIIIIIi...",
    ".kSSSSSSSSSSSSk.",
    ".ktStSttSttStSk.",
    ".kSSSSSSSSSSSSk.",
    ".ksssssssssssk..",
    "..kkkkkkkkkkkk..",
    "................",
    "................",
    "................"]

# BR3: a witch's hat with a Plague Rat's pink tail curling out from under its brim
WITCH_HAT = [
    "............kk..",
    "...........kVk..",
    "..........kVuk..",
    ".........kVVk...",
    "........kVVVk...",
    ".......kVVuVk...",
    "......kVVVVVk...",
    ".....kHhHhHhHk..",
    "....kVVVVVVVVk..",
    "..kkVVVVVVVVVkk.",
    ".kvvvvvvvvvvvvvk",
    "..kkkkkkkkkkkkk.",
    "...........Pp...",
    "............P.P.",
    ".............P..",
    "................"]

# BR4: three rats' tails knotted together (the Rat King's own curse, on a small scale)
KNOTTED_TAILS = [
    "pP............Pp",
    ".pP..........Pp.",
    "..pP........Pp..",
    "...pP......Pp...",
    "....pP....Pp....",
    ".....pkkkkp.....",
    "....kPpPPpPk....",
    "....kpPkkPpk....",
    "....kPpPPpPk....",
    ".....kkPpkk.....",
    ".......Pp.......",
    "......pP........",
    ".....Pp.........",
    ".....pP.........",
    "......pP........",
    ".......pP......."]

# BR5: a witch's potion bottle cracked, its green brew leaking into a puddle
CRACKED_POTION = [
    "......kkk.......",
    "......ksk.......",
    ".....kgggk......",
    ".....kgGgk......",
    "....kgHhHgk.....",
    "...kgHhqhHHk....",
    "...kgHhhkHhk....",
    "...kgHHkHHHk....",
    "...kgHkhHhHk....",
    "....kgHHHhk.....",
    ".....kkkkhk.....",
    "........hHh.....",
    "......hHhhHh....",
    "....hHhHHhhHh...",
    "......HhhHh.....",
    "................"]

BENEATH_THE_ROT = [
    ("BR1", "Rat skull", "a rat's skull with its long front teeth, the plague's green glowing in its sockets", ROT,
     RAT_SKULL),
    ("BR2", "Snapped trap", "a rat trap snapped shut, its iron bar down, a crumb of cheese left on top", ROT, RAT_TRAP),
    ("BR3", "Witch's hat", "a witch's purple hat with a Plague Rat's pink tail curling out from under its brim", ROT,
     WITCH_HAT),
    ("BR4", "Knotted tails", "three rats' tails knotted together", ROT, KNOTTED_TAILS),
    ("BR5", "Spilt brew", "a witch's potion bottle cracked, its green brew leaking into a puddle", ROT,
     CRACKED_POTION),
]

# ---------------------------------------------------------------- First Frost: the Rimehorn's clear ice horn, a moose's
# palm antler, the Pond Skater, snow and frost, the Moonsilver Spear
FROST = {"k": (20, 26, 40), "B": (70, 120, 176), "b": (112, 166, 218), "c": (168, 210, 240), "C": (232, 246, 252),
         "w": (236, 242, 248), "W": (200, 212, 226), "n": (156, 172, 194), "x": (84, 70, 52), "T": (150, 132, 104),
         "t": (196, 180, 150), "u": (228, 216, 190), "S": (60, 43, 30), "s": (96, 70, 48), "q": (128, 96, 64),
         "d": (44, 38, 34), "D": (74, 64, 56), "e": (130, 110, 90), "m": (204, 214, 226), "M": (240, 244, 250)}

# FF1: the Rimehorn's great horn of clear ice, broken off at its root
ICE_HORN = [
    "............kC..",
    "...........kcC..",
    "..........kccb..",
    ".........kcCbk..",
    "........kcCbk...",
    ".......kcCbbk...",
    "......kcCcbk....",
    ".....kcCcbBk....",
    "....kcCcbbk.....",
    "...kccCbbBk.....",
    "...kcCcbBk......",
    "..kccbbbBk......",
    "..kWkWWkk.......",
    "..kWWnWk........",
    "...kkkk.........",
    "................"]

# FF2: a moose's palm antler lying on the snow
MOOSE_ANTLER = [
    "................",
    "..u..u..u..u....",
    "..tu.tu.tu.tu...",
    "..tTttTttTtTt...",
    "..kTTTTTTTTTTk..",
    "...kTTtTTTtTTTk.",
    "....kTTTTTTTTk..",
    ".....kxxTTTTk...",
    "........kxTk....",
    ".........kxk....",
    "..........xk....",
    "..........kxk...",
    "wWwWWwwWwWwkxkW.",
    "WwwWwWWwwWwWwWww",
    "................",
    "................"]

# FF3: a Pond Skater's long leg, bent at its knee, on cracked pond ice
SKATER_LEG = [
    "................",
    ".....kk.........",
    "....kDDk........",
    "....kDek........",
    ".....kDk........",
    "......kDk.......",
    ".......kDk......",
    "........kDk.....",
    ".........kDk....",
    "..........kDk...",
    ".........kDk....",
    "........kDk.....",
    "cbcbcbkDkbcbcbcb",
    "bcCcbkkbBcCcbcbc",
    "cbcBbcbBcbcBcbcb",
    "................"]

# FF4: the Moonsilver Spear planted in a snowdrift, frost sparkling round it
SPEAR_IN_SNOW = [
    "..........C.....",
    ".........cCc....",
    "........kcCbk...",
    "........kcbk....",
    "........kqq.....",
    ".......kSq......",
    "......kSq.......",
    ".....kSq...M....",
    "....kSq...M.M...",
    "...kSq.....M....",
    "..kSs...........",
    ".kSs............",
    "WWWWWW..........",
    "wwWWwwWW........",
    "WwwwwWwwWWW.....",
    "................"]

# FF5: a cloven hoofprint pressed in the snow, frost glinting in it
HOOFPRINT = [
    "................",
    "................",
    "..wwwwwwwwwwww..",
    ".wwWWnnwwnnWWww.",
    ".wWnBnnwwnnBnWw.",
    ".wWnbBnwwnBbnWw.",
    ".wWnbbnwwnbbnWw.",
    ".wWnbbnwwnbbnWw.",
    ".wWnBbnwwnbBnWw.",
    ".wwWnnnwwnnnWww.",
    ".wwwWWwMMwWWwww.",
    "..wwwwwMwwwwww..",
    "................",
    "................",
    "................",
    "................"]

FIRST_FROST = [
    ("FF1", "The ice horn", "a Rimehorn's great horn of clear ice, broken off at its root", FROST, ICE_HORN),
    ("FF2", "The shed antler", "a moose's palm antler lying on the snow", FROST, MOOSE_ANTLER),
    ("FF3", "The skater's leg", "a Pond Skater's long leg, bent at its knee, on cracked pond ice", FROST, SKATER_LEG),
    ("FF4", "Spear in the snow", "the Moonsilver Spear planted in a snowdrift, frost sparkling beside it", FROST,
     SPEAR_IN_SNOW),
    ("FF5", "Hoofprint", "a cloven hoofprint pressed in the snow, frost glinting in it", FROST, HOOFPRINT),
]

DESIGNS = {"giant_slayer": GIANT_SLAYER, "into_the_ashes": INTO_THE_ASHES, "beneath_the_rot": BENEATH_THE_ROT,
           "first_frost": FIRST_FROST}


def sheet(name, designs):
    big, cell = 14, 16 * 14 + 130
    w, h = len(designs) * cell + 40, 16 * big + 470
    out = Image.new("RGBA", (w, h), (30, 33, 40, 255))
    d = ImageDraw.Draw(out)
    d.text((20, 14), f"{TITLES[name]}: advancement icon options", font=A.font(22), fill=(235, 238, 245))
    got, not_got = A.frames() or (None, None)
    for i, design in enumerate(designs):
        label, title, note, _pal, _rows = design
        im = A.draw(design)
        x0 = 20 + i * cell
        back = Image.new("RGBA", (16 * big + 16, 16 * big + 16), (62, 66, 76, 255))
        back.alpha_composite(im.resize((16 * big, 16 * big), Image.NEAREST), (8, 8))
        out.alpha_composite(back, (x0, 52))
        y = 52 + 16 * big + 26
        d.text((x0, y), f"{label}  {title}", font=A.font(17), fill=(240, 240, 240))
        line, ly = "", y + 24
        for wd in note.split():
            if d.textlength(line + " " + wd, font=A.font(13)) > cell - 40:
                d.text((x0, ly), line.strip(), font=A.font(13), fill=(170, 176, 188))
                line, ly = "", ly + 17
            line += " " + wd
        d.text((x0, ly), line.strip(), font=A.font(13), fill=(170, 176, 188))
        ry = h - 34 * 3 - 18 - 18 * 3 - 30
        sx = x0
        for sc in (1, 2, 3):
            out.alpha_composite(A.in_frame(im, got, sc), (sx, ry))
            sx += 34 * sc + 10
        out.alpha_composite(A.in_frame(im, not_got, 2), (sx, ry))
        sx = x0
        for sc in (1, 2, 3):
            out.alpha_composite(A.slot(im, sc), (sx, h - 18 * 3 - 18))
            sx += 18 * sc + 12
    return out


def main(argv):
    problems = [p for name, designs in DESIGNS.items() for design in designs for p in A.check(name, design)]
    if problems:
        print("\n".join(problems))
        sys.exit(1)
    for name, designs in DESIGNS.items():
        folder = os.path.join(OUT, name)
        os.makedirs(os.path.join(folder, "textures"), exist_ok=True)
        sheet(name, designs).save(os.path.join(folder, "options.png"))
        for design in designs:
            A.draw(design).save(os.path.join(folder, "textures", f"{design[0]}.png"))
        print("wrote", os.path.relpath(os.path.join(folder, "options.png"), ROOT))


if __name__ == "__main__":
    main(sys.argv[1:])
