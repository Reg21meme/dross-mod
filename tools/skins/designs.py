"""
The ten candidate skins for the Dross trader, drawn pixel by pixel (see skinkit.py for how faces are laid out).

Every design keeps the villager's big nose and brow, and every one carries the Dross's electric blue somewhere
(palette main #2E6BFF, deep cobalt #0B2A9E, highlight #7FA8FF; '0' is a near-white glow core).

Pixel maps: one character per pixel, '.' = transparent, ' ' = leave as is, '|' separates faces.
    head / hat strips:  right | front | left | back   (8 | 8 | 8 | 8, 10 rows)
    jacket (robe) strip: right | front | left | back   (6 | 8 | 6 | 8, 20 rows)
    arm strip:           outer | front | inner | back  (4 each, 8 rows)
    forearms strip:      right | front | left | back   (4 | 8 | 4 | 8, 4 rows)
    leg strip:           outer | front | inner | back  (4 each, 12 rows)
"""
from skinkit import Palette, Skin, cut_hem, draw_brim

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


def checker(width, rows, a, b, row0=0):
    """Rows of a two-colour checker (chain mail), continuous round a strip."""
    return ["".join(a if (x + y) % 2 == 0 else b for x in range(width)) for y in range(row0, row0 + rows)]


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


def split(row, widths):
    """'abcdef...' -> 'ab|cdef|...' (so generated rows can be patched face by face)."""
    out, i = [], 0
    for w in widths:
        out.append(row[i:i + w])
        i += w
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
# 4. Rift Regent (regal): an old king of a lost realm. A gold crown set with a blue crystal, white hair and a
#    short white beard, a royal blue robe with an ermine mantle, an ermine band down the front and hem, a gold
#    chain with a crystal medallion, and a gold-framed crystal emblem on his back.
# =====================================================================================================
def rift_regent():
    s = Skin()
    p = Palette(BLUE,
                A="#3F72F0", a="#2556D6", b="#1A40A8", B="#112D7A", x="#0A1C52",
                G="#FFE79A", g="#F4C542", k="#C58F1A", K="#8A5E0C",
                W="#FFFFFF", w="#EBEBF2", v="#CDCFDC", V="#1C1C24",
                S="#E2B48E", s="#C99A74", z="#A87A58", Z="#7D5840",
                H="#FFFFFF", h="#E4E6EE", j="#C2C6D4", J="#9A9FB2",
                E="#EEF2FF", e="#2E6BFF")

    s.draw("head", "strip", [
        "jjjjjjjj|jjjjjjjj|jjjjjjjj|jjjjjjjj",
        "hhhhhhhh|jhhhhhhj|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|hssssssh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|ssssssss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sSSSSSSs|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|ssssssss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|sEesseEs|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|szsssszs|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|ssZssZss|hhhhhhhh|hhhhhhhh",
        "jjjjjjjj|ssssssss|jjjjjjjj|jjjjjjjj",
    ], p)
    s.draw("head", "top", ["jjhjjhjj"] * 8, p)
    s.draw("head", "bottom", ["hhhhhhhh"] * 8, p)
    plain_nose(s, p)

    # Crown (points at the corners and the middle of each side, a crystal in front), white hair, brows, beard.
    s.draw("hat", "strip", [
        "G..GG..G|G..01..G|G..GG..G|G..GG..G",
        "gG.gg.Gg|gG.12.Gg|gG.gg.Gg|gG.gg.Gg",
        "gggggggg|ggg23ggg|gggggggg|gggggggg",
        "kk2kk2kk|k2kkkk2k|kk2kk2kk|kk2kk2kk",
        "HHHHHHHH|H......H|HHHHHHHH|HHHHHHHH",
        "hhjhhhhh|hHH..HHh|hhhhhjhh|hHhhhhHh",
        "hhjhhjhh|h......h|hhjhhjhh|hhjhhjhh",
        "hjjhhjhh|h......h|hhjhhjjh|hhjhhjhh",
        "hjjhjjhh|hhh..hhh|hhjjhjjh|hjjhhjjh",
        "jJjjJjjh|jhhhhhhj|hjjJjjJj|jjJjjJjj",
    ], p)
    s.draw("hat", "top", EMPTY_HAT_TOP, p)

    s.draw("jacket", "strip", [
        "WVWWWV|WVWWWWVW|WWWVWW|WVWWWVWW",
        "WWWWWW|WgW01WgW|WWWWWW|WWWWWWWW",
        "wwwVww|wwg12gww|wVwwwV|wwwVwwwV",
        "vvvvvv|vvvkkvvv|vvvvvv|vvvvvvvv",
        "bbbbbb|bbAWWAbb|bbbbbb|bbbbbbbb",
        "aAabaa|aaAWVAaa|aabaAa|aaaaaaaa",
        "aAabaa|aaAWWAaa|aabaAa|aaaggaaa",
        "aAabaa|aaAVWAaa|aabaAa|aag10gaa",
        "aAabaa|aaAWWAaa|aabaAa|ag1012ga",
        "aAabaa|bbAWVAbb|aabaAa|gk1123kg",
        "aAabaa|abAWWAba|aabaAa|ag2233ga",
        "aAabaa|abAVWAba|aabaAa|aag33gaa",
        "aAabaa|abAWWAba|aabaAa|aaaggaaa",
        "aAabaa|abAWVAba|aabaAa|aAaaaaAa",
        "aAabaa|abAWWAba|aabaAa|aAabbaAa",
        "bAbbba|bbAVWAbb|abbbAb|bAbbbbAb",
        "GGGGGG|GGGGGGGG|GGGGGG|GGGGGGGG",
        "gg2ggg|2ggg2ggg|2ggg2g|gg2ggg2g",
        "WVWWWV|WWWVWWWV|WWWVWW|WVWWWVWW",
        "wwwvww|wvwwwvww|wvwwwv|wwwvwwwv",
    ], p)
    s.draw("jacket", "top", ["WVWWWVWW", "WWWWWWWW", "wwVwwwVw", "WWWWWWWW", "WVWWWVWW", "wwwwwwww"], p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    # Arms: blue velvet sleeves with ermine cuffs; hands with a gold ring; ermine on the shoulders.
    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aAaa|aaAa|aAaa|aaAa",
        "aAab|baAa|aAab|baAa",
        "aAab|baAa|aAab|baAa",
        "aaab|baaa|aaab|baaa",
        "bbbb|bbbb|bbbb|bbbb",
        "WVWW|WWVW|WVWW|WWVW",
        "wwvw|wvww|wwvw|wvww",
    ], p)
    s.draw("arm", "top", ["WVWW", "WWWW", "wwVw", "vvvv"], p)
    s.draw("arm", "bottom", ["zssz", "sSSs", "sgss", "wwww"], p)
    s.draw("forearms", "strip", [
        "aaaa|aAaaaaAa|aaaa|aaaaaaaa",
        "aaaa|aAabbaAa|aaaa|aaaaaaaa",
        "aaaa|aaabbaaa|aaaa|aaaaaaaa",
        "bbbb|bbbbbbbb|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["bzsSSszb", "bsSgSssb", "bzsssszb", "bbbbbbbb"], p)

    # Legs: velvet (hidden), gold slippers with a blue gem.
    s.draw("leg", "strip", ["BBBB|BBBB|BBBB|BBBB"] * 9 + [
        "gGGg|GGGG|gGGg|gggg",
        "kggk|g2gg|kggk|kkkk",
        "KKKK|KKKK|KKKK|KKKK",
    ], p)
    s.draw("leg", "top", ["BBBB"] * 4, p)
    s.draw("leg", "bottom", ["KKKK"] * 4, p)
    return finish(s)


# =====================================================================================================
# 5. Shard Hermit (crystal-touched): an old recluse slowly being overgrown by rift crystal. A moss-grey patched
#    hood with crystals breaking through it, a long grey beard spilling over his crossed arms, one crystal eye
#    with blue veins, crystals bursting from his back and one crystal hand. Rope belt, mossy hem, bare feet.
# =====================================================================================================
def shard_hermit():
    s = Skin()
    p = Palette(BLUE,
                A="#8A9677", a="#6D795C", b="#535E46", B="#3B4433", x="#272D22",
                m="#6F8F42", n="#4F6B2E", t="#9B8A65", T="#786A4C",
                H="#E8E8E2", h="#C4C5BD", j="#9C9D95", J="#74756E",
                S="#D9B497", s="#BF987A", z="#9D785E", Z="#725545",
                L="#B59B69", l="#8D7550", k="#665337",
                w="#EDEAE0", e="#3A3028", v="#8193BA",
                **{"5": "#F2F7FF"})

    s.draw("head", "strip", [
        "JJJJJJJJ|jjjjjjjj|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|jjjjjjjj|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|zzzzzzzz|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|zsvssssz|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|svSSSSss|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|shhzzhhs|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|s02ssews|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|sszsszss|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|szZssZzs|JJJJJJJJ|JJJJJJJJ",
        "JJJJJJJJ|ssssssss|JJJJJJJJ|JJJJJJJJ",
    ], p)
    s.draw("head", "top", ["JJJJJJJJ"] * 8, p)
    s.draw("head", "bottom", ["hhhhhhhh"] * 8, p)
    plain_nose(s, p)

    # Hood with a crystal cluster breaking out of its right side; the beard below the face opening.
    s.draw("hat", "strip", [
        "AAA5AAAA|AAAAAAAA|AAAAAAAA|AAAAAAAA",
        "aaa03a5a|aAAAAAAa|aAAAAAAa|aAAAAAAa",
        "aa01303a|ab....ba|aaaaaaaa|aaaaaaaa",
        "ab12314a|b......b|aaabaaba|abaaaaba",
        "ab43434a|b......b|aaabaaba|abaaaaba",
        "aba3baaa|b......b|ababaaba|abaaaaba",
        "ab3abaaa|b......b|ababaaba|abbaabba",
        "bbabbaab|bhH..Hhb|bbabbabb|bbabbabb",
        "bBbbBbbb|bhhHHhhb|bbbBbbBb|bBbbbbBb",
        "BBbBBbBb|Bjhhhhjb|bBbBBbBB|BBbBBbBB",
    ], p)
    s.draw("hat", "top", [
        "aaaaaaaa",
        "aAAAAAAa",
        "aAAAAAAa",
        "aAAAAAAa",
        "aAAAAAAa",
        "aAAAAAAa",
        "aAAAAAAa",
        "AAAAAAAA",
    ], p)

    s.draw("jacket", "strip", [
        "AAAAAA|AjhhhhjA|AAAAAA|AAAAAAAA",
        "aaaaaa|ajhhhhja|aAAAAa|aaaaa5aa",
        "aaaaaa|ajhhhhja|aaaaaa|abaaa025",
        "2aaaaa|ajhhhhja|abaaba|aba5a030",
        "3abaaa|ajhhhhja|abaaba|aba02131",
        "3abaaa|ajjhhjja|abaaba|aba13131",
        "4abaaa|aajhhjaa|abaaba|aba44444",
        "a3baaa|aajjjjaa|abaaba|aba3aa3a",
        "aa3aaa|aaajjaaa|abaaba|ab3aaaa3",
        "LLLLLL|LLLkkLLL|LLLLLL|LLLkLLLL",
        "llllll|lllklkll|llllll|lllkllll",
        "aaabaa|abaLaLba|abaaba|abaaaaba",
        "atTbaa|abalalba|abaaba|abatTaba",
        "aTtbaa|aAaaaaAa|abaaba|abaTtaba",
        "aaabaa|abaaaaba|abaaba|abaaaaba",
        "aaabaa|abaaaaba|abaaba|abaaaaba",
        "aanbaa|abmaanba|abamba|abnaamba",
        "mnmmnm|mnmmnmmn|mnmmnm|mnmmnmmn",
        "nBnmBn|nBnmBnBn|BnBmnB|nBnmBnBn",
        "BxBnxB|BxBnxBxB|xBxnBx|BxBnxBxB",
    ], p)
    cut_hem(s, [19, 19, 18, 19, 19, 19,
                19, 18, 19, 19, 19, 19, 18, 19,
                19, 19, 19, 18, 19, 19,
                19, 19, 18, 19, 19, 19, 18, 19], darken=0)
    s.draw("jacket", "top", ["5051AAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA", "AAAAAAAA"], p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    # Arms: frayed sleeves; the beard hangs over the crossed forearms; one crystal hand.
    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aAaa|aaAa|aAaa|aaAa",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "abab|baba|abab|baba",
        "bbbb|bbbb|bbbb|bbbb",
        "BbBb|bBbB|BbBb|bBbB",
    ], p)
    s.draw("arm", "top", ["AAAA"] * 4, p)
    s.draw("arm", "bottom", ["bzzb", "zssz", "sSSz", "BbBb"], p)
    s.draw("forearms", "strip", [
        "aaaa|aahhhhaa|aaaa|aaaaaaaa",
        "aaaa|abhHhhba|aaaa|aaaaaaaa",
        "aaaa|abjhhjba|aaaa|aaaaaaaa",
        "bbbb|bbjhhjbb|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["41bbbbsz", "20jhhjsS", "12jhhjss", "4bjjjjbz"], p)

    # Legs: bare feet under the robe.
    s.draw("leg", "strip", ["zzzz|zzzz|zzzz|zzzz"] * 9 + [
        "zssz|sSSs|zssz|zzzz",
        "zssz|ssss|zssz|zzzz",
        "ZzZz|zZzZ|ZzZz|ZZZZ",
    ], p)
    s.draw("leg", "top", ["zzzz"] * 4, p)
    s.draw("leg", "bottom", ["ZZZZ"] * 4, p)
    return finish(s)


# =====================================================================================================
# 6. Wayfarer (road-worn): a long-road pilgrim. A wide, battered felt hat with a blue crystal pilgrim's badge,
#    a short brown beard, a rust travel coat to the knees with brass buttons, a long blue scarf, a belt pouch,
#    tall boots, and a big pack with a blue blanket roll and a blue-flamed lantern.
# =====================================================================================================
def wayfarer():
    s = Skin()
    p = Palette(BLUE,
                F="#D9B985", f="#B8956A", g="#937448", G="#5E4A2E",
                A="#C2714A", a="#A2583A", b="#80432B", B="#5A2D1C", x="#3D1E12",
                P="#6E6A49", p="#555237", q="#3D3B27",
                L="#8A5E3C", l="#6A462B", k="#4C311D", K="#2E1D11",
                C="#BDB089", c="#9A8E6B", d="#776C51",
                M="#EBCB7A", m="#B8923F",
                t="#3B5CA8", T="#2A4583",
                S="#CF9468", s="#B17A52", z="#8F5E3D", Z="#66412A",
                H="#7A5538", h="#5C3F28", j="#402B1B",
                w="#EDE6DA", e="#2E2A26")

    s.draw("head", "strip", [
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|zzzzzzzz|hhhhhhhh|hhhhhhhh",
        "hhhhzsss|zjjzzjjz|ssszhhhh|hhhhhhhh",
        "jhhhzssH|swessews|Hsszhhhj|hhhhhhhh",
        "jjhhzsHh|sszsszss|hHszhhjj|jhhhhhhj",
        "zjjzzHhh|HhZssZhH|hhHzzjjz|zjjjjjjz",
        "zzzzHhhj|hHhHHhHh|jhhHzzzz|zzzzzzzz",
    ], p)
    s.draw("head", "top", ["hhhhhhhh"] * 8, p)
    s.draw("head", "bottom", ["hhhhhhhh"] * 8, p)
    plain_nose(s, p)

    # Hat: the crown (rows 0-3) with a dark leather band and the crystal badge; the brim is the hat rim.
    s.draw("hat", "strip", [
        "gfFFFFFf|GfFFFFfG|fFFFFFfg|fFFFFFFf",
        "gfffffff|gffF0ffg|fffffffg|ffffffff",
        "ggffffff|gff12ffg|ffffffgg|gfffffff",
        "GGGGGGGG|GGG23GGG|GGGGGGGG|GGGmMGGG",
    ] + ["........|........|........|........"] * 6, p)
    s.draw("hat", "top", [
        "gffffffg",
        "ffFggFff",
        "fFFggFFf",
        "fFFggFFf",
        "fFFggFFf",
        "fFFggFFf",
        "ffFggFff",
        "gffffffg",
    ], p)
    draw_brim(s, p, base="f", light="F", edge="g", radius=7.9, roundness=3.0, notch={(1, 13), (2, 14)})

    # Coat to the knees: scarf round the neck with one long end down the front, brass buttons, belt and buckle,
    # a pouch on the left hip, a lantern on the right; on the back a pack with a blanket roll.
    s.draw("jacket", "strip", [
        "222222|21222212|222222|22222222",
        "333333|32233223|333333|33333333",
        "AAAAAA|a13mBAAa|AAAAAA|tkttttkt",
        "aaaaaa|a23aBaaa|aaaaaa|TkTTTTkT",
        "aabaaa|a23aBaaa|aaabaa|aLCCCCLa",
        "aabaaa|a13mBaaa|aaabaa|aLllllLa",
        "aabaaa|a23aBaaa|aaabaa|aLlMMlLa",
        "aabaaa|a23aBaaa|aaabaa|aLkllkLa",
        "mmabaa|b13mBbbb|aaabaa|aLCCCCLa",
        "02LLLL|L23LMLLL|LLLLLL|LLCccCLL",
        "23llll|l23lmlll|llLLLl|lLccccLl",
        "mmabaa|a13mBaaa|aalMla|aLddddLa",
        "aabaaa|a23aBaAa|aakkka|aaaaaaaa",
        "aabaaa|a33mBaAa|aaabaa|abaabaab",
        "BBBBBB|B3BBBBBB|BBBBBB|BBBBBBBB",
    ] + ["......|........|......|........"] * 5, p)
    s.draw("jacket", "top", ["TttttttT", "22222222", "2AAAAAA2", "2AAAAAA2", "22222222", "AAAAAAAA"], p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aAaa|aaAa|aAaa|aaAa",
        "aaaa|aaaa|aaaa|aaaa",
        "abaa|aaba|abaa|aaba",
        "abaa|aaba|abaa|aaba",
        "bbbb|bbbb|bbbb|bbbb",
        "AAAA|AAAA|AAAA|AAAA",
        "aAaa|aaAa|aAaa|aaAa",
    ], p)
    s.draw("arm", "top", ["AAAA"] * 4, p)
    s.draw("arm", "bottom", ["kllk", "lLLl", "lLLl", "aaaa"], p)
    s.draw("forearms", "strip", [
        "aaaa|aaAllAaa|aaaa|aaaaaaaa",
        "aaaa|aaALLAaa|aaaa|aaaaaaaa",
        "aaaa|abAllAba|aaaa|aaaaaaaa",
        "bbbb|bbAkkAbb|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["bbkllkbb", "bblLLlbb", "bbllllbb", "aaAAAAaa"], p)

    s.draw("leg", "strip", [
        "PPPP|PPPP|PPPP|PPPP",
        "pPPp|PPpP|pPPp|pPPp",
        "pPpp|pPPp|ppPp|ppPp",
        "pPpp|pPPp|ppPp|ppPp",
        "pppp|pPpp|pppp|pppp",
        "qqqq|qqqq|qqqq|qqqq",
        "LLLL|LLLL|LLLL|LLLL",
        "kllk|lLLl|kllk|kllk",
        "llll|lLLl|llll|kllk",
        "llll|lLLl|llll|kllk",
        "lkkl|llll|lkkl|kkkk",
        "KKKK|KKKK|KKKK|KKKK",
    ], p)
    s.draw("leg", "top", ["PPPP"] * 4, p)
    s.draw("leg", "bottom", ["KKKK"] * 4, p)
    return finish(s)


# =====================================================================================================
# 7. Stargazer (starstruck): a dreamy astronomer. An indigo head-wrap with a brass crescent-and-star brooch, a
#    brass monocle with a blue lens, starlit eyes and a neat goatee; an indigo robe scattered with stars and
#    constellations, a brass astrolabe at his chest and a brass crescent moon on his back.
# =====================================================================================================
def stargazer():
    s = Skin()
    p = Palette(BLUE,
                A="#5B4BA6", a="#44378A", b="#31276B", B="#21194C", x="#140F30",
                W="#FFFFFF", c="#6A5DB8",
                M="#F2D58C", m="#CFA650", n="#9C7630", N="#654A1C",
                S="#A06C4C", s="#855638", z="#6A4229", Z="#4A2C1A",
                H="#3E3446", h="#2A2230",
                w="#F2EEE6")

    s.draw("head", "strip", [
        "bbbbbbbb|bbbbbbbb|bbbbbbbb|bbbbbbbb",
        "bbbbbbbb|bbbbbbbb|bbbbbbbb|bbbbbbbb",
        "bbbbbbbb|bbbbbbbb|bbbbbbbb|bbbbbbbb",
        "bbbbbbbb|bbbbbbbb|bbbbbbbb|bbbbbbbb",
        "zzzzzzzz|zzzzzzzz|zzzzzzzz|zzzzzzzz",
        "hhhzssss|shhsshhs|sssszhhh|hhhhhhhh",
        "hhhzssss|sw1ss1ws|sssszhhh|hhhhhhhh",
        "hhzzssss|sszsszss|sssszzhh|hhhhhhhh",
        "hzzzsszs|szZssZzs|szsszzzh|zhhhhhhz",
        "zzzzzzzz|sshhhhss|zzzzzzzz|zzzzzzzz",
    ], p)
    s.draw("head", "top", ["bbbbbbbb"] * 8, p)
    s.draw("head", "bottom", ["zzzzzzzz"] * 8, p)
    plain_nose(s, p)

    # Head-wrap: diagonal bands wound round the head (rows 0-4), a tail hanging down the back.
    bands = []
    for y in range(4):
        bands.append("".join("Aab"[((x + y) // 2) % 3] for x in range(32)))
    wrap = [split(r, (8, 8, 8, 8)) for r in bands]
    rows = ["|".join(r) for r in wrap]
    rows.append("BbbbbbbB|BbbbbbbB|BbbbbbbB|BbbbbbbB")
    rows += ["........|........|........|........"] * 5
    s.draw("hat", "strip", rows, p)
    # Over the wrap: the brooch, the monocle and its chain, the tail at the back.
    s.draw("hat", "front", [
        "        ",
        "   M    ",
        "  M 0   ",
        "   M    ",
        "        ",
        ".....nn.",
        ".....10n",
        ".......n",
        ".......n",
        "........",
    ], p)
    s.draw("hat", "back", [
        "        ",
        "        ",
        "        ",
        "        ",
        "        ",
        "...ab...",
        "...ab...",
        "...Ab...",
        "...ab...",
        "...mn...",
    ], p)
    s.draw("hat", "top", [
        "bbaaaabb",
        "baAAAAab",
        "aAbbbbAa",
        "aAbaabAa",
        "aAbaabAa",
        "aAbbbbAa",
        "baAAAAab",
        "bbaaaabb",
    ], p)

    s.draw("jacket", "strip", [
        "AAAAAA|aAnMMnAa|AAAAAA|AAAAAAAA",
        "aaaaaa|aaM12Maa|aaaaaa|aaaaaaaa",
        "aaWaaa|aanMMnaa|aaaaWa|aaMMmaaa",
        "aaaaaa|aaaaaaaa|aaaaaa|aMmaaaWa",
        "aabaaa|aaaaaaaa|aaabaa|aMmaaaaa",
        "aaaaa1|aaaaaaaa|1aabaa|aMmaa1aa",
        "aabaaa|aaaaaaaa|aaabaa|aMmaaaaa",
        "aaaaaa|aaaaaaaa|aaaaaa|aMmaaaaa",
        "aWbaaa|aaaaaaaa|aaabWa|aanMMaaW",
        "mmmmmm|mmmmmmmm|mmmmmm|mmmmmmmm",
        "nnnnnn|nnnMMnnn|nnnnnn|nnnnnnnn",
        "aabaaa|aWaaaaaa|aaabaa|aaaWaaaa",
        "aaba0a|aacaa1aa|a0abaa|aacacaaa",
        "aabaaa|aaacacaa|aaabaa|a0aaa0aa",
        "aabaaa|aaaa0aaa|aaabaa|aaaaaaca",
        "aWbaaa|aaaaacWa|aaabaW|aaaaaaa1",
        "aabaaa|a1aaaaaa|aaabaa|aWaaaaaa",
        "mmmmmm|mmmmmmmm|mmmmmm|mmmmmmmm",
        "B0BBB0|BB0BB0BB|BB0BBB|B0BBB0BB",
        "xxxxxx|xxxxxxxx|xxxxxx|xxxxxxxx",
    ], p)
    s.draw("jacket", "top", ["AAAAAAAA", "AAAWAAAA", "AAAAAAAA", "AAAAAA1A", "AAAAAAAA", "AAAAAAAA"], p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    s.draw("arm", "strip", [
        "AAAA|AAAA|AAAA|AAAA",
        "aaaa|aaaa|aaaa|aaaa",
        "aWaa|aaaa|aaaa|aa1a",
        "aaaa|aa0a|aaaa|aaaa",
        "aaaa|aaaa|a1aa|aaaa",
        "bbbb|bbbb|bbbb|bbbb",
        "mmmm|mmmm|mmmm|mmmm",
        "nnnn|nnnn|nnnn|nnnn",
    ], p)
    s.draw("arm", "top", ["AAAA"] * 4, p)
    s.draw("arm", "bottom", ["zssz", "sSSs", "ssss", "nnnn"], p)
    s.draw("forearms", "strip", [
        "aaaa|aaaaaaaa|aaaa|aaaaaaaa",
        "aaaa|aWaaaa0a|aaaa|aaaaaaaa",
        "aaaa|aaaaaaaa|aaaa|aaaaaaaa",
        "bbbb|bbbbbbbb|bbbb|bbbbbbbb",
    ], p)
    s.draw("forearms", "top", ["aaaaaaaa"] * 4, p)
    s.draw("forearms", "bottom", ["bzsSSszb", "bsSssSsb", "bzsssszb", "bbnnnnbb"], p)

    s.draw("leg", "strip", ["BBBB|BBBB|BBBB|BBBB"] * 9 + [
        "bbbb|bMMb|bbbb|bbbb",
        "bBBb|Bmmb|bBBb|BBBB",
        "xxxx|xxxx|xxxx|xxxx",
    ], p)
    s.draw("leg", "top", ["BBBB"] * 4, p)
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


# =====================================================================================================
# 9. Rift Warden (vigilant): the stern keeper of the rift. A steel helm with a nasal guard over a mail coif,
#    glowing blue eyes; a dark blue tabard over mail with a crystal emblem in front and a great silver key on
#    the back; steel pauldrons, leather bracers with blue rivets, a ring of keys at the belt, steel greaves.
# =====================================================================================================
def rift_warden():
    s = Skin()
    p = Palette(BLUE,
                M="#D7DCE4", m="#A9B0BC", n="#7A8190", N="#4E5462",
                A="#2E4FB0", a="#1F3C94", b="#162D73", B="#0E1F52", x="#081436",
                L="#8A6040", l="#6B4A30", k="#4D3420", K="#2F1F12",
                G="#5A5A62", g="#46464E",
                S="#D7A27C", s="#BB8762", z="#9A6A4A", Z="#6E4A33", J="#3A2A20",
                w="#EDE6DA")

    s.draw("head", "strip", ["nnnnnnnn|zzzzzzzz|nnnnnnnn|nnnnnnnn"] * 4 + [
        "nnnnnnnn|ZZZZZZZZ|nnnnnnnn|nnnnnnnn",
        "nnnnnnnn|ZJJzzJJZ|nnnnnnnn|nnnnnnnn",
        "nnnnnnnn|zw2zz2wz|nnnnnnnn|nnnnnnnn",
        "nnnnnnnn|szzsszzs|nnnnnnnn|nnnnnnnn",
        "nnnnnnnn|ssZssZss|nnnnnnnn|nnnnnnnn",
        "nnnnnnnn|szsssszs|nnnnnnnn|nnnnnnnn",
    ], p)
    s.draw("head", "top", ["nnnnnnnn"] * 8, p)
    s.draw("head", "bottom", ["zzzzzzzz"] * 8, p)
    plain_nose(s, p)

    # Helm (rows 0-3) over a mail coif (rows 4-9); the coif is a checker that runs on round the head.
    mail = checker(32, 6, "m", "n", row0=4)
    s.draw("hat", "strip", [
        "nmmMMmmn|nmMMMMmn|nmmMMmmn|nmMMMMmn",
        "mMMMMMMm|mMMMMMMm|mMMMMMMm|mMMMMMMm",
        "nmmmmmmn|nmmMMmmn|nmmmmmmn|nmmmmmmn",
        "NNNNNNNN|NNNmmNNN|NNNNNNNN|NNNNNNNN",
    ] + ["|".join(split(r, (8, 8, 8, 8))) for r in mail], p)
    s.draw("hat", "front", [
        "        ",
        "        ",
        "        ",
        "        ",
        " ..mm.. ",
        " ..mm.. ",
        " ..mn.. ",
        " ...... ",
        " ...... ",
        "  ....  ",
    ], p)
    s.draw("hat", "top", ["nmmMMmmn"] + ["mmmMMmmm"] * 6 + ["nmmMMmmn"], p)

    # Mail shirt everywhere first, then the tabard, belt and hem over it.
    s.draw("jacket", "strip", ["|".join(split(r, (6, 8, 6, 8))) for r in checker(28, 20, "m", "n")], p)
    s.draw("jacket", "strip", [
        "      |        |      |        ",
        "      | MMMMMM |      | MMMMMM ",
        "      | AaaaaA |      | AaaaaA ",
        "      | aaaaaa |      | aaMMaa ",
        "      | aaaaaa |      | aM22Ma ",
        "      | aaaaaa |      | aM22Ma ",
        "      | aaaaaa |      | aaMMaa ",
        "      | aaaaaa |      | aaaMaa ",
        "      | baaaab |      | baaMab ",
        "LLLLLL|LLLMMLLL|LLLLLL|LLLLLLLL",
        "llllll|kllmmllk|lMMlll|kllllllk",
        "      | Aa10aA | MnM  | aaaMMa ",
        "      | a1223a |  m   | aaaMaa ",
        "      | aa34aa |      | baaMMb ",
        "NNNNNN|NMMMMMMN|NNNNNN|NMMMMMMN",
        "......|........|......|........",
        "......|........|......|........",
        "......|........|......|........",
        "......|........|......|........",
        "......|........|......|........",
    ], p)
    s.draw("jacket", "top", checker(8, 6, "m", "n"), p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    arm_mail = checker(16, 2, "m", "n", row0=3)
    s.draw("arm", "strip", [
        "MMMM|MMMM|MMMM|MMMM",
        "mMMm|mMMm|mMMm|mMMm",
        "NnnN|NnnN|NnnN|NnnN",
    ] + ["|".join(split(r, (4, 4, 4, 4))) for r in arm_mail] + [
        "mMMm|mMMm|mMMm|mMMm",
        "l2ll|ll2l|l2ll|ll2l",
        "nmmn|nmmn|nmmn|nmmn",
    ], p)
    s.draw("arm", "top", ["mMMm", "MMMM", "MMMM", "mMMm"], p)
    s.draw("arm", "bottom", ["kllk", "lLLl", "lLLl", "nnnn"], p)
    s.draw("forearms", "strip", [
        "mmmm|mMMmmMMm|mmmm|mmmmmmmm",
        "llll|l2llll2l|llll|llllllll",
        "mmmm|mmmmmmmm|mmmm|mmmmmmmm",
        "nnnn|nnnnnnnn|nnnn|nnnnnnnn",
    ], p)
    s.draw("forearms", "top", ["mmmmmmmm"] * 4, p)
    s.draw("forearms", "bottom", ["kllkkllk", "lLLllLLl", "lLLllLLl", "nnnnnnnn"], p)

    s.draw("leg", "strip", [
        "GGGG|GGGG|GGGG|GGGG",
        "gGGg|GGgG|gGGg|gGGg",
        "gggg|gGgg|gggg|gggg",
        "NNNN|NNNN|NNNN|NNNN",
        "mMMm|MMMM|mMMm|mmmm",
        "nmmn|mMMm|nmmn|nnnn",
        "mMMm|MMMM|mMMm|mmmm",
        "mmmm|mMMm|mmmm|nnnn",
        "mmmm|mMMm|mmmm|nnnn",
        "nmmn|mMMm|nmmn|nnnn",
        "NnnN|nmmn|NnnN|NNNN",
        "KKKK|KKKK|KKKK|KKKK",
    ], p)
    s.draw("leg", "top", ["GGGG"] * 4, p)
    s.draw("leg", "bottom", ["KKKK"] * 4, p)
    return finish(s)


# =====================================================================================================
# 10. Alchemist (volatile): a wild-haired experimenter. Brass goggles with glowing blue lenses pushed up into
#     his orange hair, freckles, a singed eyebrow and soot; a leather apron with a row of glowing vials over a
#     cream shirt and plum waistcoat, long black gloves, a bubbling blue flask held to his chest.
# =====================================================================================================
def alchemist():
    s = Skin()
    p = Palette(BLUE,
                L="#A57A52", l="#855E3C", k="#66452A", K="#452D1A",
                C="#EEE9DE", c="#D3CCBD", d="#ADA594",
                A="#6A5578", a="#54436A", b="#3E3150", B="#2A2138", x="#1A1424",
                H="#F5A04E", h="#D97A2E", j="#AE561D", J="#763A14",
                M="#E8C26A", m="#B48A34", n="#7A5A1E",
                G="#3B3F46", g="#2A2D33", t="#C9A57A",
                S="#F0C9A8", s="#DDAE8B", z="#BE8C6C", Z="#93684F", f="#C27F5E", y="#3A3434",
                w="#F4F0E8", e="#2A5A9A")

    s.draw("head", "strip", [
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|hhhhhhhh|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|ssssssss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhh|ssssssss|hhhhhhhh|hhhhhhhh",
        "hhhhhhhs|sSSSSSSs|shhhhhhh|hhhhhhhh",
        "hhhhhhss|shhsszys|sshhhhhh|hhhhhhhh",
        "jhhhhsss|swessews|ssshhhhj|hhhhhhhh",
        "jjhhzsss|sfssssfs|ssszhhjj|jhhhhhhj",
        "zjjzzszs|syZssZss|szszzjjz|zjjjjjjz",
        "zzzzzzzz|ssssssss|zzzzzzzz|zzzzzzzz",
    ], p)
    s.draw("head", "top", ["hhhhhhhh"] * 8, p)
    s.draw("head", "bottom", ["zzzzzzzz"] * 8, p)
    nose(s, p,
         top=["SS", "Ss"],
         front=["ss", "Sf", "ss", "zz"],
         right=["sz", "sz", "zz", "zZ"],
         left=["zs", "zs", "zz", "Zz"],
         bottom=["zZ", "Zz"])

    s.draw("hat", "strip", [
        "HhHHhHHh|HhHHhHHh|hHHhHHhH|HHhHHhHH",
        "hHhhHhhH|hHjhhHjH|HhhHhhHh|hHhhHhhH",
        "hhjhhhjh|hmMhhMmh|hjhhhjhh|hhjhhjhh",
        "KKKKKKKK|K10KK10K|KKKKKKKK|KKKmmKKK",
        "hjhhjhh.|.mm..mm.|.hhjhhjh|hjhhjhhj",
        "jhjhjh..|h......h|..hjhjhj|jhjhjhjh",
        "jJjjJ...|j......j|...JjjJj|jJjjJjjJ",
        "J.J.....|........|.....J.J|J.jJ.Jj.",
        "........|........|........|........",
        "........|........|........|........",
    ], p)
    top = strands(8, 8, "H", "h", "j", seed=2)
    top[2] = top[2][:5] + "yy" + top[2][7:]
    top[3] = top[3][:5] + "y" + top[3][6:]
    s.draw("hat", "top", top, p)

    s.draw("jacket", "strip", [
        "AAAAAA|aCLLLLCa|AAAAAA|aaaaaaaa",
        "aaaaaa|aCLllLCa|aaaaaa|aLaaaaLa",
        "aaaaaa|acL02Lca|aaaaaa|aaLaaLaa",
        "aabaaa|acLkkLca|aaabaa|aaaLLaaa",
        "aabaaa|aaLllLaa|aaabaa|aaaLLaaa",
        "aabaaa|aaLllLaa|aaabaa|aaLaaLaa",
        "aabaaa|aaLllLaa|aaabaa|aLaaaaLa",
        "aabaaa|aLllllLa|aaabaa|LaaaaaaL",
        "aabaaa|aLllllLa|aaabaa|aaaaaaaa",
        "kkkkkk|kLllllLk|kkkkkk|kkkLLkkk",
        "aabaaa|aLtltlLa|aaMaaa|aakLLkaa",
        "aabaaa|aL0l0lLa|aa0aaa|aaakkaaa",
        "aabaaa|aL2l2lLa|a120aa|aakaakaa",
        "aabaaa|aL3k3kLa|a233aa|aaaaaaaa",
        "BBBBBB|BLllllLB|BBBBBB|BBBBBBBB",
        "......|.LlKllL.|......|........",
        "......|.Lll2lL.|......|........",
        "......|.kkkkkk.|......|........",
        "......|........|......|........",
        "......|........|......|........",
    ], p)
    s.draw("jacket", "top", ["AAAAAAAA"] * 6, p)
    s.draw("jacket", "bottom", [EMPTY_FACE] * 6, p)

    s.draw("arm", "strip", [
        "CCCC|CCCC|CCCC|CCCC",
        "cCcc|ccCc|cCcc|ccCc",
        "cccc|cccc|cccc|cccc",
        "cdcc|ccdc|cdcc|ccdc",
        "dddd|dddd|dddd|dddd",
        "CCCC|CCCC|CCCC|CCCC",
        "ssss|sfss|ssss|ssss",
        "GGGG|GGGG|GGGG|GGGG",
    ], p)
    s.draw("arm", "top", ["CCCC"] * 4, p)
    s.draw("arm", "bottom", ["gGGg", "GGGG", "GGGG", "gggg"], p)
    s.draw("forearms", "strip", [
        "GGGG|GGgttgGG|GGGG|GGGGGGGG",
        "GGGG|GGg00gGG|GGGG|GGGGGGGG",
        "GGGG|Gg1201gG|GGGG|GGGGGGGG",
        "gggg|gg2332gg|gggg|gggggggg",
    ], p)
    s.draw("forearms", "top", ["GGGGGGGG"] * 4, p)
    s.draw("forearms", "bottom", ["gGGggGGg", "GGGggGGG", "Gg2332gG", "gg3443gg"], p)

    s.draw("leg", "strip", [
        "aaaa|aaaa|aaaa|aaaa",
        "aAaa|aaAa|aAaa|aaaa",
        "aaba|abaa|aaba|abaa",
        "aaba|abaa|aaba|abaa",
        "aaba|abaa|aaba|abaa",
        "bbbb|bbbb|bbbb|bbbb",
        "KKKK|KKKK|KKKK|KKKK",
        "kKKk|KMmK|kKKk|KKKK",
        "kkkk|kKKk|kkkk|kkkk",
        "kkkk|kKKk|kkkk|kkkk",
        "kKKk|kkkk|kKKk|KKKK",
        "xxxx|xxxx|xxxx|xxxx",
    ], p)
    s.draw("leg", "top", ["aaaa"] * 4, p)
    s.draw("leg", "bottom", ["xxxx"] * 4, p)
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
    (4, "rift_regent", "Rift Regent", "regal",
     "Gold crown with a blue crystal, white hair and beard, royal blue robe with ermine and a crystal medallion.",
     rift_regent),
    (5, "shard_hermit", "Shard Hermit", "crystal-touched",
     "Mossy hermit overgrown by rift crystal: crystals through hood and back, long grey beard, one crystal eye.",
     shard_hermit),
    (6, "wayfarer", "Wayfarer", "road-worn",
     "Wide battered felt hat with a crystal badge, rust coat, long blue scarf, tall boots, pack with a blue lantern.",
     wayfarer),
    (7, "stargazer", "Stargazer", "starstruck",
     "Indigo head-wrap and star-strewn robe, brass monocle with a blue lens, astrolabe and a crescent moon.",
     stargazer),
    (8, "blind_oracle", "Blind Oracle", "prophetic",
     "Long white hair, a blue silk blindfold and a glowing third eye; bone-white robes with blue embroidery.",
     blind_oracle),
    (9, "rift_warden", "Rift Warden", "vigilant",
     "Steel helm and mail, glowing eyes, dark blue tabard with a crystal in front and a great silver key on the back.",
     rift_warden),
    (10, "alchemist", "Alchemist", "volatile",
     "Wild orange hair, brass goggles with glowing lenses, leather apron with glowing vials, a bubbling blue flask.",
     alchemist),
]
