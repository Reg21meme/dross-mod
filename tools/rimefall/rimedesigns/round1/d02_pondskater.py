"""No. 2 Pond Skater: a water strider as big as a house, skating on blades of ice."""
from rimekit import mats as M

NUMBER = 2
ID = "pondskater"
NAME = "Pond Skater"
MOOD = "skims the ice"
BLURB = ("A water strider (the bug that walks on ponds) as big as a house: a small glossy body on six huge thin legs, "
         "the back four on ice-skate blades, the front two raised like hooks.")


def build(s):
    # Body: head, thorax, the wider middle where the long legs join, a tapering abdomen.
    s.blob((0, 29, -29), (8, 7, 9), "shell")
    s.blob((0, 28, -12), (10, 8, 13), "shell")
    s.blob((0, 28, 6), (12, 9, 13), "back")
    s.taper([(0, 28, 14), (0, 27, 30), (0, 25, 46)], [17, 13, 6], "back", depths=[14, 11, 5])
    # Big bulging eyes, a beak pointing down, long feelers.
    for x in (-1, 1):
        s.blob((8 * x, 31, -31), (4, 5, 5), "eye")
        s.taper([(2 * x, 31, -32), (5 * x, 36, -43), (7 * x, 35, -54), (8 * x, 31, -60)], [2, 1.5, 1.2, 1], "shell",
                joints=False)
    s.taper([(0, 25, -32), (0, 21, -35), (0, 18, -34)], [3, 2, 1.4], "shell")
    for x in (-1, 1):
        # Front legs: raised like grappling hooks.
        s.taper([(5 * x, 25, -18), (9 * x, 34, -30), (10 * x, 27, -41), (9 * x, 21, -44), (8 * x, 22, -47)],
                [3.5, 3, 2.5, 2, 1.5], "shell")
        # Middle legs: the longest, a high knee, then a long sweep down to a skate blade.
        knee = (40 * x, 47, -2)
        foot = (100 * x, 4, 2)
        s.taper([(7 * x, 26, 0), knee, (70 * x, 30, 0), foot], [4, 3.2, 2.6, 2.2], "leg")
        s.taper([foot, (112 * x, 2.5, 7)], [2.2, 1.6], "leg")
        s.seg((96 * x, 1.5, -1), (116 * x, 1.5, 8.5), 1, 3, "blade", side=(0, 1, 0))
        # Hind legs: back and out, on blades too.
        knee = (30 * x, 40, 26)
        foot = (64 * x, 4, 66)
        s.taper([(6 * x, 26, 9), knee, (48 * x, 24, 46), foot], [3.6, 3, 2.4, 2], "leg")
        s.taper([foot, (72 * x, 2.5, 80)], [2, 1.5], "leg")
        s.seg((60 * x, 1.5, 60), (76 * x, 1.5, 85), 1, 3, "blade", side=(0, 1, 0))


def materials():
    return {
        "shell": M.chitin(M.CHITIN, seed=201, gloss=1.6, hairs=0.02, frost_amount=0.15),
        "back": M.chitin(M.CHITIN, seed=202, gloss=1.4, hairs=0.04, frost_amount=0.6),
        "leg": M.chitin(M.CHITIN, seed=203, gloss=1.2, hairs=0.035, frost_amount=0.1),
        "eye": M.chitin(M.BLACK_ICE, seed=205, gloss=2.2, frost_amount=0.0, outline=0.0),
        "blade": M.solid(M.ICE, idx=4.0, seed=207, grain=0.5, bev=0.8, outline=0.0, glow_pal=M.GLOW, glow_alpha=110),
    }
