"""No. 5 Fisher Below (boss): a huge anglerfish under a frozen lake, a real little house for bait."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import unit

NUMBER = 5
ID = "fisher"
NAME = "Fisher Below"
SECTION = "boss"
BLURB = ("A huge anglerfish under a frozen lake, its bait a cosy little cabin made of real Minecraft blocks (spruce "
         "logs and planks, glass panes, a door, a stair roof, a brick chimney), warm light in its windows, so it looks "
         "just like a house until the fish shows up (the user's notes for round 2). Its head breaks up through the "
         "ice, crusted with ice in four colours; its fangs are long needles of clear ice; a lit lantern hangs from its "
         "lure. Much bigger than round 1's.")
VARIANTS = (
    dict(letter="A", mood="house on a line", pose="lure",
         note="A: the cabin dangles from its lure on a line, right in front of its open mouth."),
    dict(letter="B", mood="in its jaws", pose="jaws",
         note="B: the cabin sits on the ice and the fish has come up under it: its jaws gape round the house."),
)


DEEP = M.palette("#141b24", "#1b2430", "#232e3c", "#2d3a4a", "#3a495b", "#4c5d70")     # deep-sea skin


class Xform:
    """A Shape seen through a turn about a pivot (the head thrown back): points and directions are turned."""

    def __init__(self, s, pivot, axis, deg):
        self.s = s
        self.blocks = s.blocks
        self.p = np.asarray(pivot, dtype=float)
        a = math.radians(deg)
        k = unit(axis)
        K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
        self.R = np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * K @ K

    def P(self, q):
        return self.p + self.R @ (np.asarray(q, dtype=float) - self.p)

    def D(self, d):
        return None if d is None else self.R @ np.asarray(d, dtype=float)

    def seg(self, p0, p1, w, d=None, mat="body", side=None, ext=0.0, inflate=0.0):
        return self.s.seg(self.P(p0), self.P(p1), w, d, mat, side=self.D(side) if side is not None else None, ext=ext,
                          inflate=inflate)

    def box(self, center, size, mat):
        c = np.asarray(center, dtype=float)
        return self.seg(c - [0, size[1] / 2.0, 0], c + [0, size[1] / 2.0, 0], size[0], size[2], mat, side=(1, 0, 0))

    def plate(self, center, normal, up, w, h, t, mat):
        return self.s.plate(self.P(center), self.D(normal), self.D(up), w, h, t, mat)

    def cone(self, base, tip, w0, mat, w1=1.0, steps=None):
        return self.s.cone(self.P(base), self.P(tip), w0, mat, w1=w1, steps=steps)

    def curve(self, control, n, widths, mat, depths=None, side=None):
        return self.s.curve([self.P(c) for c in control], n, widths, mat, depths=depths,
                            side=self.D(side) if side is not None else None)

    def tube(self, points, widths, mat, depths=None, side=None, cross=0.62):
        return self.s.tube([self.P(c) for c in points], widths, mat, depths=depths,
                           side=self.D(side) if side is not None else None, cross=cross)

    def blob(self, center, radii, mat, axis=(0.0, 1.0, 0.0), slices=None):
        return self.s.blob(self.P(center), radii, mat, axis=self.D(axis), side=self.D((1.0, 0.0, 0.0)), slices=slices)


# ---------------------------------------------------------------- the bait: a little spruce cabin of real blocks


def cabin(s, O, lit=True):
    """
    A 5 x 5 spruce cabin of real blocks, its middle bottom at O (Bedrock units), its door facing the model's front
    (north). Columns X (east, the model's right) and Z (south) run -2..2 round the middle; Y up from its floor.
    """

    def put(state, X, Y, Z, bright=False):
        s.block(state, (O[0] - (X + 0.5) * 16, O[1] + Y * 16, O[2] + (Z - 0.5) * 16), bright)

    for X in range(-2, 3):
        for Z in range(-2, 3):
            edge = abs(X) == 2 or abs(Z) == 2
            put("minecraft:cobblestone" if edge else "minecraft:spruce_planks", X, 0, Z)
    for Y in (1, 2, 3, 4):
        for X in range(-2, 3):
            for Z in range(-2, 3):
                if not (abs(X) == 2 or abs(Z) == 2):
                    continue
                corner = abs(X) == 2 and abs(Z) == 2
                if Y == 4 and abs(X) != 2 and abs(Z) == 2:
                    continue                                               # the gables are below
                if corner and Y <= 3:
                    put("minecraft:spruce_log[axis=y]", X, Y, Z)
                elif Z == -2 and X == 0 and Y in (1, 2):
                    put(f"minecraft:spruce_door[facing=north,half={'lower' if Y == 1 else 'upper'},hinge=left,"
                        f"open=false]", X, Y, Z)
                elif Y == 2 and abs(Z) == 2 and abs(X) == 1:
                    put("minecraft:glass_pane[east=true,west=true]", X, Y, Z)
                elif Y == 2 and abs(X) == 2 and Z == 0:
                    put("minecraft:glass_pane[north=true,south=true]", X, Y, Z)
                else:
                    put("minecraft:spruce_planks", X, Y, Z)
    # The gables, front and back.
    for Z in (-2, 2):
        for X in range(-2, 3):
            put("minecraft:spruce_planks", X, 4, Z)
        for X in range(-1, 2):
            put("minecraft:spruce_planks", X, 5, Z)
        put("minecraft:spruce_planks", 0, 6, Z)
    # The roof: spruce stairs down both sides, a slab ridge, overhanging front and back.
    for Z in range(-3, 4):
        for k, Y in enumerate((4, 5, 6)):
            X = 3 - k
            if (X, Y, Z) != (1, 6, 1):
                put("minecraft:spruce_stairs[facing=west,half=bottom,shape=straight]", X, Y, Z)
            put("minecraft:spruce_stairs[facing=east,half=bottom,shape=straight]", -X, Y, Z)
        put("minecraft:spruce_slab[type=bottom]", 0, 7, Z)
    # The chimney, through the roof on its right side.
    for Y in (5, 6, 7, 8):
        put("minecraft:bricks", 1, Y, 1)
    # A cobblestone step at the door, a lantern on it.
    for X in (-1, 0, 1):
        put("minecraft:cobblestone", X, 0, -3)
    put("minecraft:lantern[hanging=false]", -1, 1, -3, bright=lit)
    return O + np.array([0.0, 7.5 * 16, 0.0])        # the top of the ridge


def cabin_glow(s, O):
    """Warm light filling the cabin (it shows through the windows): the mod's own glowing box inside it."""
    s.box((O[0], O[1] + 16 + 24, O[2]), (46, 46, 46), "warm")
    # Its chimney smoke, frozen solid in the air.
    cx, cz = O[0] - 1.5 * 16 + 8, O[2] + 0.5 * 16 + 8
    for k, (dy, dx, r) in enumerate(((150, 0, 5), (160, 3, 7), (172, 0, 8), (184, -4, 7))):
        s.blob((cx + dx, O[1] + dy, cz), (r, r * 0.85, r), "smoke", slices=3)


# ---------------------------------------------------------------- the fish


def lake(s, cx, cz, rx, rz, seed):
    """The lake: dark water in the hole, slabs of its ice broken and tipped up round it."""
    s.box((cx, -1.5, cz), (2 * rx + 30, 4, 2 * rz + 30), "water")
    n = 13
    for k in range(n):
        a = 2 * math.pi * (k + 0.37 * (k % 3)) / n
        j = ((k * 37 + seed) % 10) / 10.0
        px, pz = cx + math.sin(a) * rx * (1.25 + 0.2 * j), cz - math.cos(a) * rz * (1.25 + 0.2 * j)
        outward = unit(np.array([math.sin(a) / rx, 0.0, -math.cos(a) / rz]))
        tilt = 0.35 + 0.55 * j
        normal = unit(outward * tilt + np.array([0.0, 1.0, 0.0]))
        up = np.cross(normal, np.cross(outward, normal))
        w = 48 + 16 * ((k * 3) % 4)
        h = 40 + 14 * ((k * 5) % 3)
        c = np.array([px, 4 + 10 * tilt, pz])
        s.plate(c, normal, -up, w, h, 5, "lakeice")
        if k % 3 != 1:
            s.plate(c + normal * 3.1 - up * 8, normal, -up, w // 2 + 6, h // 2, 1, "snow")


def crust(s, points, seed):
    """Clusters of ice crystals in four colours growing on its head."""
    mats = ("crust_deep", "crust_teal", "crust_white", "crust_violet")
    for k, (p, n) in enumerate(points):
        p = np.asarray(p, dtype=float)
        n = unit(n)
        side = unit(np.cross(n, [0.0, 0.0, 1.0] if abs(n[2]) < 0.9 else [1.0, 0.0, 0.0]))
        for j in range(3 + (k + seed) % 3):
            ang = (j * 2.1 + k) % (2 * math.pi)
            d = unit(n + side * math.cos(ang) * 0.5 + np.cross(n, side) * math.sin(ang) * 0.5)
            L = 14 + ((k * 7 + j * 5 + seed) % 5) * 7
            s.cone(p + side * math.cos(ang) * 6 - n * 3, p + d * L, 8 + (j % 2) * 3, mats[(k + j) % 4], steps=3)


def head(h, gape, stalk_out=True):
    """
    The head (through `h`, which may be turned): a broad, flat-topped skull, a heavy brow with small milky eyes, the
    upper jaw and its fangs; the crust; the lure's stalk. Returns the lure's tip. The model's front is -z.
    """
    # The skull: big, round-backed and lumpy, flat across the top.
    h.blob((0, 40, 50), (112, 128, 100), "skin", slices=7)
    h.seg((0, 112, -10), (0, 150, -10), 196, 110, "skin", side=(1, 0, 0))
    h.seg((0, 140, -42), (0, 164, -42), 170, 44, "skin", side=(1, 0, 0))          # the brow over the mouth
    for k, (x, y, z, r) in enumerate(((-60, 150, 40, 22), (52, 156, 60, 26), (0, 160, 96, 20), (-84, 110, 80, 18),
                                      (88, 120, 90, 20))):
        h.blob((x, y, z), (r, r * 0.7, r), "skin", slices=3)                      # lumps
    # Gill flaps; glowing spots down its sides, like a deep-sea fish's.
    for x in (-1, 1):
        h.plate((x * 108, 60, 96), (x, 0.1, 0.3), (0, 1, 0), 60, 80, 4, "fin")
        for k in range(7):
            h.box((x * (100 - k * 2), 90 + (k % 2) * 10, 60 - k * 18), (4, 4, 4), "spot")
        # Its eyes: milky and glowing, high on the brow.
        h.blob((x * 70, 170, -62), (13, 13, 11), "eye", slices=3)
        h.seg((x * 70, 170, -66), (x * 70, 170, -52), 30, 30, "rim", side=(1, 0, 0))
    # Spines along the top of its head (the lure is one of them, grown long).
    for k in range(5):
        z = 10 + k * 26
        h.cone((0, 176 - k * 6, z), (0, 206 - k * 9, z + 16), 7, "bone", steps=3)
    # The upper jaw: a broad lip across the front of the skull, lined with fangs of clear ice hanging down.
    h.seg((0, 100, -70), (0, 136, -70), 220, 64, "skin", side=(1, 0, 0))
    h.seg((0, 99, -102), (0, 104, -102), 210, 6, "lip", side=(1, 0, 0))
    for k in range(17):
        t = (k - 8) / 8.0
        x = t * 98
        z = -96 + abs(t) ** 2 * 30
        L = 34 + 34 * (1 - abs(t)) * (0.55 + 0.45 * ((k * 5) % 3) / 2) + 10 * (k % 2)
        h.cone((x, 104, z), (x * 1.02, 104 - L, z - 8), 8 + 2 * (k % 2), "fang", steps=4)
    # The crust: ice in four colours along its brow, its top and its sides.
    pts = []
    for k in range(9):
        t = (k - 4) / 4.0
        pts.append(((t * 88, 168, -40 + abs(t) * 10), (t * 0.4, 1.0, -0.3)))
    for k in range(6):
        t = (k - 2.5) / 2.5
        pts.append(((t * 70, 178, 40 + (k % 2) * 30), (t * 0.3, 1.0, 0.2)))
    for x in (-1, 1):
        for k in range(4):
            pts.append(((x * 104, 60 + k * 26, 0 + k * 20), (x, 0.4, -0.2)))
    crust(h, pts, 3)
    # The lure: a stalk from its forehead arching up and forward (or drawn by the caller, through the world).
    if not stalk_out:
        return h.P(np.array((0.0, 170.0, -10.0)))
    return lure(h, [(0, 170, -10), (0, 250, -4), (0, 316, -50), (0, 322, -110), (0, 300, -150)])


def lure(h, stalk):
    """The lure's stalk through these points, a few crystals on it; returns its tip."""
    h.curve(stalk, 10, [16, 13, 11, 9, 8], "stalk")
    for k, p in enumerate(stalk[1:4]):
        h.cone(p, np.asarray(p) + np.array([0, 10, 8]), 6, "crust_teal" if k % 2 else "crust_white", steps=2)
    tip = np.array(stalk[-1], dtype=float)
    return h.P(tip) if hasattr(h, "P") else tip


def lower_jaw(s, front_z, width, lift=0.0):
    """The lower jaw: a heavy underbite jutting past the upper, a U of fangs standing up from it."""
    s.seg((0, -24, 30), (0, 40 + lift, 30), width, 150, "skin", side=(1, 0, 0))
    s.seg((0, -10, front_z * 0.5), (0, 44 + lift, front_z * 0.5), width + 10, 110, "skin", side=(1, 0, 0),
          inflate=0.1)
    s.seg((0, -10, front_z + 24), (0, 40 + lift, front_z + 24), width - 30, 48, "skin", side=(1, 0, 0))
    s.seg((-width * 0.46, 42 + lift, front_z + 8), (width * 0.46, 42 + lift, front_z + 8), 5, 6, "lip",
          side=(0, 1, 0))
    for k in range(15):
        t = (k - 7) / 7.0
        x = t * (width * 0.44)
        z = front_z + 12 + abs(t) ** 2 * 60
        L = 30 + 30 * (1 - abs(t)) * (0.6 + 0.4 * ((k * 3) % 3) / 2) + 8 * (k % 2)
        s.cone((x, 40 + lift, z), (x * 1.02, 40 + lift + L, z + 6), 8 + 2 * (k % 2), "fang", steps=4)


def build(s, v):
    if v["pose"] == "lure":
        # The head sunk in the lake, its mouth open in front; the cabin hangs on a line in front of it.
        lake(s, 0.0, 30.0, 140.0, 130.0, 5)
        tip = head(Xform(s, (0, 0, 0), (1, 0, 0), 0.0), 0.0)
        lower_jaw(s, -112.0, 236.0)
        s.seg((0, 46, -60), (0, 92, -60), 190, 70, "mouth", side=(1, 0, 0))
        for x in (-1, 1):
            s.plate((x * 128, 4, 70), (0.25 * x, 1.0, 0.1), (0, 0, 1), 70, 90, 4, "fin")
        house = np.array([0.0, 112.0, -196.0])
        ridge = cabin(s, house)
        cabin_glow(s, house)
        # The line from the lure's tip down to the ridge, the lantern lure hanging beside it.
        s.seg(tip, ridge + np.array([0, 2, 0]), 2, 2, "line")
        s.block("minecraft:lantern[hanging=true]", (tip[0] - 8, tip[1] - 22, tip[2] + 4), bright=True)
        s.seg(tip, tip + np.array([0, -8, 12]), 2, 2, "line")
    else:
        # The cabin on a floe of lake ice; the fish has come up under it, its head thrown back, its jaws round it.
        lake(s, 0.0, -10.0, 170.0, 170.0, 9)
        s.box((0, -2, -70), (112, 12, 112), "lakeice")
        house = np.array([0.0, 4.0, -70.0])
        cabin(s, house)
        cabin_glow(s, house)
        lower_jaw(s, -172.0, 250.0, lift=-12.0)
        s.seg((0, -16, -10), (0, 6, -10), 230, 240, "mouth", side=(1, 0, 0))
        root = head(Xform(s, (0, 24, 110), (1, 0, 0), 52.0), 52.0, stalk_out=False)   # (a turn about x lifts it)
        tip = lure(s, [root, root + np.array([0, 80, -30]), (0, 336, 40), (0, 318, -60), (0, 284, -112)])
        for x in (-1, 1):
            s.plate((x * 150, 6, 40), (0.25 * x, 1.0, 0.1), (0, 0, 1), 70, 100, 4, "fin")
        s.block("minecraft:lantern[hanging=true]", (tip[0] - 8, tip[1] - 22, tip[2] + 4), bright=True)
        s.seg(tip, tip + np.array([0, -8, 12]), 2, 2, "line")


def materials(v):
    return {
        "skin": M.skin(DEEP, seed=1801, mottle=1.6, frost_amount=0.12, scars=0.35),
        "fin": M.skin(DEEP, seed=1803, mottle=1.0, frost_amount=0.25, outline=0.2),
        "rim": M.skin(M.MOUTH, seed=1805, mottle=0.6, frost_amount=0.0),
        "lip": M.skin(M.FISH, seed=1833, mottle=0.8, frost_amount=0.1),
        "bone": M.bone(seed=1835, frost_amount=0.3),
        "spot": M.glowing(M.GLOW_GREEN, seed=1837, idx=3.8),
        "eye": M.glowing(M.ICE_WHITE, seed=1807, base_pal=M.ICE, idx=4.0, glow_alpha=210),
        "mouth": M.mouth(),
        "fang": M.ice(M.ICE_WHITE, alpha=(150, 215), seed=1809, cracks=0.2, bubbles=0.02, frost_amount=0.0),
        "stalk": M.skin(M.FISH, seed=1811, mottle=1.2, frost_amount=0.4),
        "line": M.solid(M.ROPE, seed=1813, outline=0.0),
        "crust_deep": M.ice(M.ICE_DEEP, alpha=(160, 225), seed=1815, cracks=0.3, frost_amount=0.2),
        "crust_teal": M.ice(M.ICE_TEAL, alpha=(160, 225), seed=1817, cracks=0.3, frost_amount=0.2),
        "crust_white": M.ice(M.ICE_WHITE, alpha=(170, 235), seed=1819, cracks=0.3, frost_amount=0.2),
        "crust_violet": M.ice(M.ICE_VIOLET, alpha=(160, 225), seed=1821, cracks=0.3, frost_amount=0.2),
        "water": M.solid(M.palette("#06121c", "#0a1a27", "#0f2333", "#152d40"), idx=1.5, seed=1823, grain=1.6,
                         outline=0.0),
        "lakeice": M.ice(M.ICE, alpha=(110, 175), seed=1825, cracks=0.5, bubbles=0.02, frost_amount=0.05),
        "snow": M.snow(seed=1827),
        "warm": M.glowing(M.WARM, seed=1829, idx=3.4),
        "smoke": M.snow(M.SNOW, seed=1831, sparkle=0.0),
    }
