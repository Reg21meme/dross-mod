"""
The Dross skins on the villager model, drawn pixel by pixel (see skinkit.py for how faces are laid out). Each keeps
its number from the ten candidates the user picked from: 2 The Archivist (the quest trader), 3 Ragged Seeker and
8 Blind Oracle (the hub's survivors), and 1 Hooded Watcher (kept for a possible Dross enemy later).

Every design keeps the villager's big nose and brow, and every one carries the Dross's electric blue somewhere
(palette main #2E6BFF, deep cobalt #0B2A9E, highlight #7FA8FF; '0' is a near-white glow core).

Pixel maps: one character per pixel, '.' = transparent, ' ' = leave as is, '|' separates faces.
    head / hat strips:  right | front | left | back   (8 | 8 | 8 | 8, 10 rows)
    jacket (robe) strip: right | front | left | back   (6 | 8 | 6 | 8, 20 rows)
    arm strip:           outer | front | inner | back  (4 each, 8 rows)
    forearms strip:      right | front | left | back   (4 | 8 | 4 | 8, 4 rows)
    leg strip:           outer | front | inner | back  (4 each, 12 rows)
"""
from skinkit import Palette, Skin, cut_hem

# Shared electric blues (DrossColors' palette) plus a glow core and a very deep navy.
BLUE = {
    "0": "#D6E4FF",  # glow core
    "1": "#7FA8FF",  # highlight (PALETTE_HIGHLIGHT)
    "2": "#2E6BFF",  # main (PALETTE_MAIN)
    "3": "#0B2A9E",  # deep cobalt (PALETTE_DEEP_COBALT)
    "4": "#071A66",  # deepest navy
}

EMPTY_HAT_TOP = ["........"] * 8
EMPTY_FACE = "........"


def nose(skin, pal, top, front, right, left, bottom):
    """The nose: top (2x2), front (2x4), right and left sides (2x4), bottom (2x2). Its back is inside the head."""
    skin.draw("nose", "top", top, pal)
    skin.draw("nose", "front", front, pal)
    skin.draw("nose", "right", right, pal)
    skin.draw("nose", "left", left, pal)
    skin.draw("nose", "bottom", bottom, pal)
    skin.draw("nose", "back", front, pal)


def plain_nose(skin, pal):
    """The usual nose: lit on top, a little darker down the sides and underneath."""
    nose(skin, pal,
         top=["SS", "Ss"],
         front=["ss", "Ss", "ss", "zz"],
         right=["sz", "sz", "zz", "zZ"],
         left=["zs", "zs", "zz", "Zz"],
         bottom=["zZ", "Zz"])


def strands(width, rows, light, mid, dark, seed=0):
    """Messy hair seen from above: short diagonal strands (a regular pattern, not noise)."""
    out = []
    for y in range(rows):
        line = ""
        for x in range(width):
            k = (x + 2 * y + seed) % 5
            line += light if k == 0 else dark if k == 3 else mid
        out.append(line)
    return out


def finish(skin):
    skin.copy_jacket_to_body()
    return skin


# =====================================================================================================
# 1. Hooded Watcher (mysterious): a deep navy hood with a glowing blue edge round a narrow opening, his face
#    lost in shadow except one burning blue eye; a cracked-crystal pendant; a blue band down the robe; a rift
#    crack glowing on his back.
# =====================================================================================================
def hooded_watcher():
    s = Skin()
    p = Palette(BLUE,
                A="#33406B", a="#242E52", b="#19203B", B="#10152A", x="#090C17",
                S="#8C6A57", s="#6B4E40", z="#4A3530", Z="#2C1F1E",
                L="#3E342C", l="#2E2620", k="#211B16", K="#14100C",
                M="#C8CEDC", m="#8A91A3")

    # Head: hidden under the hood except the middle of the face.
    s.draw("head", "strip", ["ZZZZZZZZ|ZZZZZZZZ|ZZZZZZZZ|ZZZZZZZZ"] * 5 + [
        "ZZZZZZZZ|ZZxZZ3ZZ|ZZZZZZZZ|ZZZZZZZZ",
        "ZZZZZZZZ|ZZ3Z40ZZ|ZZZZZZZZ|ZZZZZZZZ",
        "ZZZZZZZZ|ZZzZZ3ZZ|ZZZZZZZZ|ZZZZZZZZ",
        "ZZZZZZZZ|ZZxzzxZZ|ZZZZZZZZ|ZZZZZZZZ",
        "ZZZZZZZZ|ZZzsszZZ|ZZZZZZZZ|ZZZZZZZZ",
    ], p)
    s.draw("head", "top", ["ZZZZZZZZ"] * 8, p)
    s.draw("head", "bottom", ["zzzzzzzz"] * 8, p)
    nose(s, p,
         top=["zz", "zz"],
         front=["zz", "zz", "ss", "sz"],
         right=["ZZ", "Zz", "zz", "zz"],
         left=["ZZ", "zZ", "zz", "zz"],
         bottom=["ZZ", "ZZ"])

    # Hood: a glowing edge round a narrow opening, one fold on each side, a seam down the back.
    s.draw("hat", "strip", [
        "AAAAAAAA|AAAAAAAA|AAAAAAAA|AAAAAAAA",
        "aAAAAAAA|aAAAAAAa|AAAAAAAa|aAAAbAAa",
        "aaaaaaaa|ab2222ba|aaaaaaaa|aaaabaaa",
        "aaabaaaa|b2....2b|aaaabaaa|aaaabaaa",
        "aaabaaaa|b2....2b|aaaabaaa|aaaabaaa",
        "abaabaaa|b2....2b|aaabaaba|aaaabaaa",
        "abaabaaa|b2....2b|aaabaaba|abaababa",
        "bbabbaba|b2....2b|ababbabb|abaababa",
        "bBbbBbbb|b3....3b|bbbBbbBb|bbabbbab",
        "BBbBBbBb|B3....3B|bBbBBbBB|BbBBbBbB",
    ], p)
    s.draw("hat", "top", [
        "aaabaaaa",
        "aAabAAaa",
        "aAAbAAAa",
        "aAAbAAAa",
        "aAAbAAAa",
        "aAAbAAAa",
        "aAAbAAAa",
        "AAAbAAAA",
    ], p)

    # Robe: a mantle with a cobalt edge (a V on the back), a blue band down the front, a blue hem with faint
    # runes. On the back, a glowing rift crack.
    s.draw("jacket", "strip", [
        "AAAAAA|aAmaamAa|AAAAAA|AAAAAAAA",
        "aAAAAa|aab12baa|aAAAAa|aAAAAAAa",
        "aaaaaa|aab23baa|aaaaaa|aaaaaaaa",
        "aaaa33|33333333|33aaaa|aaaaaaaa",
        "aa33bb|bbbbbbbb|bb33aa|aaaaaaaa",
        "33bbaa|aab32baa|aabb33|3aaaaaa3",
        "bbaaaa|aab32baa|aaaabb|b3aaaa3b",
        "aaabaa|aab32baa|aabaaa|ab3aa3ba",
        "aaabaa|aab32baa|aabaaa|abb33bba",
        "aaabaa|bbb32bbb|aabaaa|ababbaba",
        "aaabaa|abb32bba|aabaaa|abaa3aba",
        "aaabaa|aab32baa|aabaaa|aba32aba",
        "aaabaa|aab32baa|aabaaa|ab3203ba",
        "aaabaa|aab32baa|aabaaa|a3202aba",
        "aaabaa|aab32baa|aabaaa|ab3002ba",
        "aaabaa|aab32baa|aabaaa|aba2023a",
        "bbbbbb|bbb32bbb|bbbbbb|bbbb32bb",
        "222222|22222222|222222|22222222",
        "B3BBB3|BBB3BBB3|BBB3BB|B3BBB3BB",
        "xxxxxx|xxxxxxxx|xxxxxx|xxxxxxxx",
    ], p)
    s.draw("jacket", "top", ["AAAAAAAA"] * 6, p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    # Arms: dark sleeves with glowing cuffs; his hands stay hidden in the dark sleeve ends.
    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aAAa|aAAa|aAAa|aAAa",
        "aaaa|aaaa|aaaa|aaaa",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "bbbb|bbbb|bbbb|bbbb",
        "2222|2222|2222|2222",
    ], p)
    s.draw("arm", "top", ["AAAA"] * 4, p)
    s.draw("arm", "bottom", ["BBBB", "BxxB", "BxxB", "BBBB"], p)
    s.draw("forearms", "strip", [
        "aaaa|aaaaaaaa|aaaa|aaaaaaaa",
        "aaaa|aaabbaaa|aaaa|aaaaaaaa",
        "aaaa|abbBBbba|aaaa|aaaaaaaa",
        "bbbb|bbbBBbbb|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["BBBBBBBB", "BBxxxxBB", "bBBxxBBb", "bbbBBbbb"], p)

    # Legs: dark trousers (hidden) and plain dark shoes.
    s.draw("leg", "strip", ["BBBB|BBBB|BBBB|BBBB"] * 9 + [
        "kllk|lLLl|kllk|kkkk",
        "kllk|llll|kllk|kkkk",
        "KKKK|KKKK|KKKK|KKKK",
    ], p)
    s.draw("leg", "top", ["BBBB"] * 4, p)
    s.draw("leg", "bottom", ["KKKK"] * 4, p)
    return finish(s)


# =====================================================================================================
# 2. The Archivist (scholarly): a bald old scholar with a white horseshoe fringe, mutton chops and bushy brows,
#    brass spectacles with glowing blue lenses, a cream stole stitched with blue glyphs, a rift-bound book held
#    to his chest, a book on a chain at his hip and a satchel of scrolls on his back. Blue ink on his fingers.
# =====================================================================================================
def archivist():
    s = Skin()
    p = Palette(BLUE,
                A="#8E9EC0", a="#6D7EA3", b="#546385", B="#3C4866", x="#262E45",
                C="#F1E8CF", c="#DCCFAC", d="#BBAC87", D="#8F8160",
                L="#9C6D45", l="#7E5336", k="#613E27", K="#432918",
                S="#EDD0B8", s="#D8B196", z="#BA8F76", Z="#8F6A55", y="#C9A086",
                H="#FFFFFF", h="#E6E7EC", j="#C3C5CF", J="#9A9DAB",
                M="#F2D27A", m="#C99A3A", n="#8C6420",
                w="#F4F4F4", e="#2B3A6B")

    # Head: a bald dome with a shine; hair, brows, moustache and spectacles are on the hat layer.
    s.draw("head", "strip", [
        "ssssssss|ssSSSSss|ssssssss|ssssssss",
        "ssssssss|ssssssss|ssssssss|ssssssss",
        "ssssssss|ssyyyyss|ssssssss|ssssssss",
        "ssssssss|ssssssss|ssssssss|ssssssss",
        "ssssssss|ssssssss|ssssssss|zzzzzzzz",
        "ssssssss|ssszzsss|ssssssss|zzzzzzzz",
        "zsssssss|swessews|sssssssz|zzzzzzzz",
        "zzssssss|sszsszss|sssssszz|zzzzzzzz",
        "zzzsssss|szZssZzs|ssssszzz|zzzzzzzz",
        "zzzzzzzz|ssszzsss|zzzzzzzz|zzzzzzzz",
    ], p)
    s.draw("head", "top", [
        "zssssssz",
        "sssSSsss",
        "ssSSSSss",
        "ssSSSSss",
        "ssSSSSss",
        "sssSSsss",
        "ssssssss",
        "ssssssss",
    ], p)
    s.draw("head", "bottom", ["zzzzzzzz"] * 8, p)
    nose(s, p,
         top=["SS", "Ss"],
         front=["ss", "Ss", "Ss", "zz"],
         right=["sz", "sz", "sz", "zz"],
         left=["zs", "zs", "zs", "zz"],
         bottom=["zZ", "Zz"])

    # Hat layer: a white horseshoe fringe and mutton chops, bushy brows, spectacles.
    s.draw("hat", "strip", [
        "........|........|........|........",
        "........|........|........|........",
        "........|........|........|........",
        "........|........|........|........",
        "HHHH...H|H......H|H...HHHH|HHHHHHHH",
        "hhhh...h|hHH..HHh|h...hhhh|hhhHhhhh",
        "hjhnnnnn|n01nn01n|nnnnnhjh|hjhhjhhj",
        "jhjh...h|h......h|h...hjhj|jhjjhjjh",
        ".jjh...h|hhh..hhh|h...hjj.|.jj..jj.",
        "..j.....|.hj..jh.|.....j..|........",
    ], p)
    s.draw("hat", "top", EMPTY_HAT_TOP, p)

    # Robe: slate blue with a cream stole (blue glyphs), a leather belt with a brass buckle, a book on a chain at
    # his right hip, a cream hem. Back: the stole round his neck, a lowered hood, a satchel with scrolls.
    s.draw("jacket", "strip", [
        "AAAAAA|aCcBBcCa|AAAAAA|cCCCCCCc",
        "aAAAaa|aC2bb2Ca|aaAAAa|dccccccd",
        "aaaaaa|aCcaacCa|aaaaaa|abbbbbba",
        "aabaaa|aCcaacCa|aaabaa|bAAAAAAb",
        "aabaaa|aC2aa2Ca|aaabaa|abaaaaba",
        "aabaaa|aCcaacCa|aaabaa|aaC2aCaa",
        "aabaaa|aCcaacCa|aaabaa|aKLLLLKa",
        "aabaaa|aC2aa2Ca|aaabaa|aKllllKa",
        "aabaaa|aCcaacCa|aaabaa|aKkkkkKa",
        "LLLLLL|LCcMMcCL|LLLLLL|LKlMMlKL",
        "kkkmkk|kdcmmcdk|kkkkkk|kKllllKk",
        "aa3m3a|aCcaacCa|aaabaa|aKllllKa",
        "a3223a|aC2aa2Ca|aaabaa|aKllllKa",
        "a3mm3a|aCcaacCa|aaabaa|aKKKKKKa",
        "a3333a|acdaadca|aaabaa|abaaaaba",
        "aabaaa|adDbbDda|aaabaa|abaaaaba",
        "bbbbbb|bbbbbbbb|bbbbbb|bbbbbbbb",
        "cccccc|cccccccc|cccccc|cccccccc",
        "bbbbbb|bbbbbbbb|bbbbbb|bbbbbbbb",
        "BBBBBB|BBBBBBBB|BBBBBB|BBBBBBBB",
    ], p)
    s.draw("jacket", "top", [
        "AACCCCAA",
        "AACCCCAA",
        "AACAACAA",
        "AACAACAA",
        "AACAACAA",
        "AACAACAA",
    ], p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    # Arms: slate sleeves with wide cream cuffs; hands with blue ink on the fingertips; a blue book held to his
    # chest (gold-edged cover, cream page edges below).
    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aAAa|aAAa|aaaa|aaaa",
        "aaaa|aaaa|aaaa|aaaa",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "CCCC|CCCC|CCCC|CCCC",
        "cccc|cccc|cccc|cccc",
        "dddd|dddd|dddd|dddd",
    ], p)
    s.draw("arm", "top", ["AAAA"] * 4, p)
    s.draw("arm", "bottom", ["dddd", "dsSd", "dz2d", "dddd"], p)
    s.draw("forearms", "strip", [
        "aaaa|aammmmaa|aaaa|aaaaaaaa",
        "aaaa|ab32m3ba|aaaa|aaaaaaaa",
        "aaaa|ab3m23ba|aaaa|aaaaaaaa",
        "bbbb|ccmmmmcc|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["bbssssbb", "bbs22sbb", "abCcCcba", "aaCCCCaa"], p)

    # Legs: hidden trousers, brown leather shoes.
    s.draw("leg", "strip", ["BBBB|BBBB|BBBB|BBBB"] * 9 + [
        "llll|lLLl|llll|kkkk",
        "lkkl|llll|lkkl|kkkk",
        "KKKK|KKKK|KKKK|KKKK",
    ], p)
    s.draw("leg", "top", ["BBBB"] * 4, p)
    s.draw("leg", "bottom", ["KKKK"] * 4, p)
    return finish(s)


# =====================================================================================================
# 3. Ragged Seeker (ragged): a gaunt wanderer in a torn, patched ash-grey cloak cut short and ragged, a blue
#    rag tied round his messy hair, stubble, and a glowing blue rift-scar across one eye. Bandaged arms and
#    shins, a blanket roll on his back.
# =====================================================================================================
def ragged_seeker():
    s = Skin()
    p = Palette(BLUE,
                A="#7E776A", a="#615B50", b="#4A453D", B="#34312B", x="#221F1B",
                r="#9A5536", R="#6E3A24", t="#B09A6A", T="#85724C",
                C="#B8AE96", c="#9C927C", d="#7E7562",
                P="#6A5A48", p="#54473A", q="#3F362C",
                W="#E2DAC2", v="#BEB59C", V="#958D76",
                S="#D19A72", s="#B5805C", z="#946446", Z="#6B4532", y="#8C6650",
                H="#5A473A", h="#3E3128", j="#2A211B", J="#1A1411",
                L="#A88B5C", l="#7F6845", k="#5A4A31",
                w="#EDE6DA", e="#2A2018")

    s.draw("head", "strip", [
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|zzzzzzzz|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sSSSSS3s|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sssss3ss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhs|sJJzs2Js|shhhhhhh|hhhhhhhh",
        "hhhhhhss|swezz01s|sshhhhhh|hhhhhhhh",
        "jhhhhsss|szzsss2s|ssshhhhj|hhhhhhhh",
        "jjhhyyys|yyZyyZ3y|syyyhhjj|jjhhhhjj",
        "jjjyyyyy|yyyyyyyy|yyyyyjjj|zzzzzzzz",
    ], p)
    s.draw("head", "top", ["hhhhhhhh"] * 8, p)
    s.draw("head", "bottom", ["yyyyyyyy"] * 8, p)
    plain_nose(s, p)

    # Messy hair, a blue rag tied round it (knot and two tails at the back).
    s.draw("hat", "strip", [
        "HhHHhHHh|HhHHhHHh|hHHhHHhH|HHhHHhHH",
        "hHhhHhhH|hHjhHHjH|HhhHhhHh|hHhhHhhH",
        "23222232|21122112|23222232|22233222",
        "hjhhhjh.|j......j|.hjhhhjh|hhj22jhh",
        "jhjhjhh.|........|.hhjhjhj|hjh23hjh",
        "hjjhjj..|........|..jjhjjh|jhj32jhj",
        "jJjjJ...|........|...JjjJj|jJj23jJj",
        "J.jJ....|........|....Jj.J|J.j32j.J",
        "...J....|........|....J...|...23...",
        "........|........|........|...3....",
    ], p)
    s.draw("hat", "top", strands(8, 8, "H", "h", "j"), p)

    # A short torn cloak, open at the front over a dirty linen shirt with a rope belt; patches; a blanket roll
    # across the back; the hem cut ragged afterwards.
    s.draw("jacket", "strip", [
        "AAAAAA|AaCssCaA|AAAAAA|AAAAAAAA",
        "aAAAAa|abCcsCba|aAAAAa|abAAAAba",
        "aaaaaa|abCccCba|aaaaaa|bAabbaAb",
        "abaaba|abCccCba|abaaba|bAbBBbAb",
        "abaaba|abCccCba|abaaba|abBBBBba",
        "abaaba|abCccCba|abaaba|tkttttkt",
        "arrrba|abCccCba|abaaba|TkTTTTkT",
        "arrrba|abCccCba|abtTta|abBBBBba",
        "aRRRba|abCccCba|abTTTa|abaaaaba",
        "abaaba|abLlLlba|abaaba|abaaaaba",
        "abaaba|abcCcdba|abaaba|aba222ba",
        "abaaba|abdcCdba|abaaba|aba212ba",
        "abaaba|aBddcdBa|abaaba|aba333ba",
        "abaaba|aBccccbB|abaaba|abaaaaba",
        "abaaba|aBccccBb|abaaba|abaabaab",
        "abaaba|abccccba|abaaba|abaabaab",
        "abaaba|abccccba|abaaba|abaabaab",
        "......|........|......|........",
        "......|........|......|........",
        "......|........|......|........",
    ], p)
    cut_hem(s, [14, 15, 13, 14, 12, 13,
                15, 14, 12, 11, 12, 12, 14, 13,
                13, 12, 14, 13, 15, 14,
                15, 13, 16, 14, 15, 13, 16, 14])
    s.draw("jacket", "top", ["bAAAAAAb"] + ["AAAAAAAA"] * 5, p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    # Arms: the cloak over the shoulders, linen sleeves, bandaged forearms and hands.
    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aAaa|aaAa|aAaa|aaAa",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "abab|baba|abab|baba",
        "bbbb|bbbb|bbbb|bbbb",
        "vvvv|vWWv|vvvv|vvvv",
        "VVVV|VVVV|VVVV|VVVV",
    ], p)
    s.draw("arm", "top", ["AAAA"] * 4, p)
    s.draw("arm", "bottom", ["VvvV", "vszv", "vsSv", "vvvv"], p)
    s.draw("forearms", "strip", [
        "aaaa|aaaVVaaa|aaaa|aaaaaaaa",
        "aaaa|abavvaba|aaaa|aaaaaaaa",
        "aaaa|abavvaba|aaaa|aaaaaaaa",
        "bbbb|bbbVVbbb|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["bbVssVbb", "bbvsSvbb", "bbvssvbb", "bbVvvVbb"], p)

    # Legs: patched trousers, wrapped shins, rag-wrapped feet.
    s.draw("leg", "strip", [
        "PPPP|PPPP|PPPP|PPPP",
        "pPPp|PPpP|pPPp|pPPp",
        "pPpp|pPPp|ppPp|ppPp",
        "prRp|pPpp|pTtp|pppp",
        "pRrp|ppPp|ptTp|pppq",
        "qppq|qppq|qppq|qqqq",
        "vvvv|vWWv|vvvv|vvvv",
        "VVVV|VVVV|VVVV|VVVV",
        "vvvv|vWWv|vvvv|vvvv",
        "VVVV|VVVV|VVVV|VVVV",
        "vvvv|vvvv|vvvv|VVVV",
        "xxxx|xxxx|xxxx|xxxx",
    ], p)
    s.draw("leg", "top", ["PPPP"] * 4, p)
    s.draw("leg", "bottom", ["xxxx"] * 4, p)
    return finish(s)


# =====================================================================================================
# 8. Blind Oracle (prophetic): pale and serene. Long white hair, a deep blue silk blindfold, a glowing third
#    eye on the brow, faint blue veins; bone-white robes with blue embroidery, a blue sash, an all-seeing eye
#    on the back below the hair.
# =====================================================================================================
def blind_oracle():
    s = Skin()
    p = Palette(BLUE,
                A="#F4F1E8", a="#DEDACB", b="#BDB8A6", B="#96907D", x="#6E6959",
                H="#FFFFFF", h="#E3E8F2", j="#C2CADB", J="#9AA4BC",
                S="#EBCFC0", s="#D2B2A2", z="#B08F80", Z="#866B60", v="#7C93CC",
                M="#E2E6EE", m="#A9B0C0")

    s.draw("head", "strip", [
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|hssssssh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sss33sss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sss01sss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sss33sss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|ssssssss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|ssssssss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sszsszss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|svZssZvs|hhhhhhhh|hhhhhhhh",
        "jjjjjjjj|svssssvs|jjjjjjjj|jjjjjjjj",
    ], p)
    s.draw("head", "top", ["hhhhhhhh"] * 8, p)
    s.draw("head", "bottom", ["ssssssss"] * 8, p)
    plain_nose(s, p)

    s.draw("hat", "strip", [
        "HHHHHHHH|HHHHHHHH|HHHHHHHH|HHHHHHHH",
        "hHhhHhhH|hHH..HHh|HhhHhhHh|hHhhhhHh",
        "hhjhhjhh|hh....hh|hhjhhjhh|hhjhhjhh",
        "hhjhhjhh|h......h|hhjhhjhh|hhjhhjhh",
        "hjjhhjjh|h......h|hjjhhjjh|hjjhhjjh",
        "31111111|11111111|11111113|11133111",
        "33222222|22222222|22222233|22233222",
        "hjjhhjjh|h......h|hjjhhjjh|hjh23hjh",
        "hjhhjjhh|h......h|hhjjhhjh|hjh32hjh",
        "jjJjjJjj|j......j|jjJjjJjj|jjJ23Jjj",
    ], p)
    s.draw("hat", "top", ["hHHjjHHh", "hHHjjHHh", "HHhjjhHH", "HHhjjhHH",
                          "HHhjjhHH", "HHhjjhHH", "hHHjjHHh", "hHHjjHHh"], p)

    s.draw("jacket", "strip", [
        "hhAAAA|hhAmmAhh|AAAAhh|hhhhhhhh",
        "hjaaaa|hjaMMajh|aaaajh|hHhhHhhH",
        "hjaaaa|jja20ajj|aaaajh|hhjhhjhh",
        "jaaaaa|jaa2Aaaj|aaaaaj|hhjhhjhh",
        "jabaaa|aa2AA2aa|aaabaj|hjjhhjjh",
        "aabaaa|aa2AA2aa|aaabaa|hjhhjjhh",
        "aabaaa|aa2AA2aa|aaabaa|jjhjjhjj",
        "aabaaa|aa2AA2aa|aaabaa|jJjjJjjJ",
        "aabaaa|ab2AA2ba|aaabaa|aJjJJjJa",
        "222222|22222222|222222|22222222",
        "333333|33311333|333333|33333333",
        "aabaaa|aa2AA2aa|aaabaa|aa3333aa",
        "aabaaa|aa2AA2aa|aaabaa|a3a11a3a",
        "aabaaa|ab2AA2ba|aaabaa|3a1001a3",
        "aabaaa|ab2AA2ba|aaabaa|a3a11a3a",
        "aabaaa|ab2AA2ba|aaabaa|aa3333aa",
        "bbbbbb|bb2AA2bb|bbbbbb|bbbbbbbb",
        "222222|22222222|222222|22222222",
        "a1aa1a|a1aa1aa1|a1aa1a|a1aa1aa1",
        "BBBBBB|BBBBBBBB|BBBBBB|BBBBBBBB",
    ], p)
    s.draw("jacket", "top", ["hhhhhhhh", "hAAAAAAh", "AAAAAAAA", "AAAAAAAA", "hAAAAAAh", "hhAAAAhh"], p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aAaa|aaAa|aAaa|aaAa",
        "aaaa|aaaa|aaaa|aaaa",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "2222|2222|2222|2222",
        "A1AA|1AA1|A1AA|1AA1",
        "3333|3333|3333|3333",
    ], p)
    s.draw("arm", "top", ["AAAA"] * 4, p)
    s.draw("arm", "bottom", ["zssz", "svSs", "ssvs", "3333"], p)
    s.draw("forearms", "strip", [
        "aaaa|aAaaaaAa|aaaa|aaaaaaaa",
        "aaaa|aaabbaaa|aaaa|aaaaaaaa",
        "aaaa|abbbbbba|aaaa|aaaaaaaa",
        "bbbb|22222222|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["bzsSSszb", "bsvssvsb", "bzsssszb", "33333333"], p)

    s.draw("leg", "strip", ["bbbb|bbbb|bbbb|bbbb"] * 9 + [
        "3333|2112|3333|3333",
        "3333|2222|3333|3333",
        "4444|4444|4444|4444",
    ], p)
    s.draw("leg", "top", ["bbbb"] * 4, p)
    s.draw("leg", "bottom", ["4444"] * 4, p)
    return finish(s)


# Number order. Each entry: (number, id, sign name, mood, one-line description, function).
SKINS = [
    (1, "hooded_watcher", "Hooded Watcher", "mysterious",
     "Deep navy hood with a glowing edge, face lost in shadow but for one burning blue eye; rift crack on his back.",
     hooded_watcher),
    (2, "archivist", "The Archivist", "scholarly",
     "Bald old scholar with white mutton chops, glowing blue spectacles, a glyph-stitched stole, a book and scrolls.",
     archivist),
    (3, "ragged_seeker", "Ragged Seeker", "ragged",
     "Torn, patched short cloak, blue rag headband, stubble and a glowing rift-scar across one eye; bandaged limbs.",
     ragged_seeker),
    (8, "blind_oracle", "Blind Oracle", "prophetic",
     "Long white hair, a blue silk blindfold and a glowing third eye; bone-white robes with blue embroidery.",
     blind_oracle),
]
