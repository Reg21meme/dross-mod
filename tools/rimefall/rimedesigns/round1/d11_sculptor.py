"""No. 11 The Sculptor: the hulking maker of The Unfinished, a hammer and a chisel of ice for hands."""
from rimekit import mats as M

NUMBER = 11
ID = "sculptor"
NAME = "The Sculptor"
MOOD = "you're next"
BLURB = ("The one who carves the frozen dead: a hulking, hunched figure in an ice-white mask and a frozen leather "
         "apron, its hands a hammer and a chisel of ice, half-carved statues of past victims strapped to its back.")


def build(s):
    # Short thick legs, wrapped boots.
    for x in (-1, 1):
        s.taper([(10 * x, 34, 4), (12 * x, 18, -2), (12 * x, 6, 2)], [14, 12, 11], "skin")
        s.rbox((12 * x, 3.5, -1), (12, 7, 17), "leather", c=2)
    # Hips and a hunched, heavy body leaning forward.
    s.rbox((0, 36, 4), (26, 10, 18), "leather", c=2)
    s.blob((0, 52, -6), (19, 14, 13), "skin")
    s.blob((0, 62, 3), (23, 17, 17), "skin", axis=(0, 1, -0.55))
    for x in (-1, 1):
        s.blob((24 * x, 68, -5), (11, 10, 11), "skin")
    # The frozen apron, ice chips stuck to it.
    s.plate((0, 40, -16), (0, 0.15, -1), (0, 1, 0.12), 26, 42, 2, "apron")
    for x, y in ((-8, 48), (5, 44), (-3, 33), (9, 28), (-10, 24), (2, 52)):
        s.box((x, y, -17.5), (3, 2, 2), "chips", rot=(0, 0, x * 4))
    # A small head thrust forward, an ice-white mask with glowing eye holes.
    s.rbox((0, 66, -24), (13, 13, 12), "skin", c=2)
    s.plate((0, 66, -30.6), (0, 0, -1), (0, 1, 0), 12, 13, 1, "mask")
    s.box((0, 70.5, -31.6), (12, 2, 1), "mask")
    for x in (-1, 1):
        s.box((3 * x, 67, -31.4), (2, 2, 1), "eye")
    # Left arm: the forearm ends in a great hammer of clear ice.
    s.taper([(24, 66, -6), (30, 48, -14), (30, 32, -26)], [13, 12, 11], "skin")
    s.ring((30, 36, -23), (0, -0.7, -0.6), 6.5, 2, 3, "rope", n=7)
    s.rbox((30, 22, -31), (22, 18, 20), "hammer", c=3, rot=(-12, 10, 0))
    # Right arm: the forearm ends in a long chisel blade.
    s.taper([(-24, 66, -6), (-30, 50, -16), (-26, 41, -33)], [13, 12, 10], "skin")
    s.ring((-27, 43, -30), (0.15, -0.4, -0.9), 5.5, 2, 3, "rope", n=7)
    s.cone((-26, 41, -33), (-22, 24, -64), 9, "chisel", d0=4, d1=1, steps=5)
    # A frame on its back with two half-carved statues strapped to it.
    for x in (-1, 1):
        s.seg((11 * x, 54, 16), (11 * x, 100, 26), 3, 3, "wood")
    for y, z in ((64, 18.5), (86, 23.3), (98, 25.8)):
        s.seg((-14, y, z), (14, y, z), 3, 3, "wood")
    for cx, cy, h in ((-6, 82, 22), (7, 78, 18)):
        s.rbox((cx, cy, 31), (9, h * 0.62, 7), "statue", c=1)
        s.rbox((cx, cy + h * 0.47, 31), (6, 6, 6), "statue", c=1)
        s.rbox((cx, cy - h * 0.52, 31.5), (10, 8, 8), "rough", c=1)
        s.seg((cx - 6, cy + 2, 31), (cx + 6, cy + 2, 31), 2, 9, "rope")
    s.seg((-12, 62, 7), (12, 62, 7), 2, 30, "rope", side=(1, 0, 0))


def materials():
    return {
        "skin": M.skin(M.FROST_SKIN, seed=1101, mottle=1.2, frost_amount=0.3, cracks=0.15, scars=0.2),
        "leather": M.skin(M.WOOD, seed=1103, mottle=0.7, frost_amount=0.3),
        "apron": M.cloth(M.WOOD, seed=1105, folds=0.6, ragged=0.4, frost_amount=0.45),
        "chips": M.solid(M.MARBLE, idx=4.0, seed=1107, outline=0.05),
        "mask": M.solid(M.MARBLE, idx=3.6, seed=1109, grain=0.6, bev=0.5, outline=0.12),
        "eye": M.eye(M.GLOW, pupil=False),
        "hammer": M.ice(M.ICE, alpha=(165, 225), seed=1111, cracks=0.45, bubbles=0.03, glow_pal=M.GLOW,
                        glow_alpha=40),
        "chisel": M.solid(M.ICE, idx=3.4, seed=1113, grain=0.8, bev=0.9, outline=0.05, glow_pal=M.GLOW,
                          glow_alpha=70),
        "rope": M.solid(M.ROPE, seed=1115),
        "wood": M.wood(M.WOOD, seed=1117, plank=3.0, axis=1),
        "statue": M.solid(M.MARBLE, idx=3.0, seed=1119, grain=1.2, bev=0.8, outline=0.1),
        "rough": M.ice(M.ICE, alpha=(170, 220), seed=1121, cracks=0.5, frost_amount=0.3),
    }
