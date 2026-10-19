"""No. 7 Rimehorn (regular mob): an ice-age woolly rhino in layers of shaggy fur, its two horns of clear ice."""
import numpy as np

from rimekit import mats as M

NUMBER = 7
ID = "rimehorn"
NAME = "Rimehorn"
SECTION = "mob"
BLURB = ("An ice-age woolly rhino: a high shoulder hump, a long low head, and a thick coat of fur in layers, stepping "
         "out from its back to a long shaggy skirt along its belly, icicles hanging off it, a ruff round its neck and "
         "a beard under its throat. Its two horns, a great sweeping one on its nose and a short one behind it, are clear "
         "ice. Plain black eyes. Round 2: a regular mob, the same size as before, the ice block on its back gone (the "
         "user's notes). Round 3: the coat rebuilt from straight layers (the hanging locks crossed through each other "
         "and the body), black eyes with no fur over them, and on the brown one a patch of snow on its back.")
VARIANTS = (
    dict(letter="A", mood="frosted coat", fur=M.WOOLLY_PALE, skin=M.FROST_SKIN),
    dict(letter="B", mood="dark brown coat", fur=M.WOOLLY, skin=M.STONE, snow=True),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/. Round 2's coat of hanging locks
# is archived in round3/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('A', 'B'))

# The body's cross-section along its length: (z, centre height, half-width, half-height).
SECTIONS = ((-40, 50, 20, 22), (-22, 51, 25, 28), (0, 46, 24, 26), (20, 43, 23, 24), (38, 42, 19, 20), (46, 42, 13, 14))
# The coat is built in slices this long (units), front to back, each slice from straight boxes only: nothing is turned,
# so no two pieces of fur cut through each other (the user: "a lot of clipping of the body that doesn't look good").
SLICE = 8
SLICES = tuple(range(-42, 47, SLICE))       # their middles
SKIRT_DROP = 7                              # how far the skirt hangs below the belly
EYE = M.palette("#040405", "#08080a", "#0d0d10", "#141418")    # black


def body_at(z):
    zs = [s[0] for s in SECTIONS]
    return tuple(float(np.interp(z, zs, [s[i] for s in SECTIONS])) for i in (1, 2, 3))


def jitter(k, seed, amount):
    """A whole number from -amount to amount, the same every build."""
    return ((k * 7 + seed * 13) % (2 * amount + 1)) - amount


def slab(s, x0, x1, y0, y1, z0, z1, mat):
    """A straight box between two corners (whole units; either order)."""
    a = np.array([round(x0), round(y0), round(z0)], dtype=float)
    b = np.array([round(x1), round(y1), round(z1)], dtype=float)
    lo, hi = np.minimum(a, b), np.maximum(a, b)
    if (hi - lo).min() <= 0:
        return
    s.box((lo + hi) / 2.0, hi - lo, mat)


def coat(s, v):
    """
    The body, slice by slice: the back on top, then layers stepping out and down (the upper flank, the widest flank),
    the belly underneath, and a long shaggy skirt hanging outside the flanks below the belly, icicles off its edge.
    Returns each slice's (z0, z1, top, back half-width) for the snow.
    """
    tops = []
    for k, zc in enumerate(SLICES):
        cy, rx, ry = body_at(zc)
        z0, z1 = zc - SLICE / 2, zc + SLICE / 2
        top = round(cy + ry)
        back = round(0.62 * rx)
        upper = round(0.86 * rx) + 1
        flank = round(rx) + 2
        slab(s, -back, back, cy + 0.45 * ry, top, z0, z1, "fur")                       # the back
        slab(s, -upper, upper, cy - 0.05 * ry, cy + 0.62 * ry + jitter(k, 1, 1), z0, z1, "fur")
        slab(s, -flank, flank, cy - 0.55 * ry, cy + 0.12 * ry + jitter(k, 2, 1), z0, z1, "fur")
        slab(s, -round(0.8 * rx), round(0.8 * rx), cy - ry, cy - 0.45 * ry, z0, z1, "fur")  # the belly
        # The skirt: long hair hanging outside the flank, below the belly, a ragged edge (cut out). Icicles hang
        # down the outside of it, touching it all the way, and on below its edge (they used to hang from the
        # ragged edge itself and looked as if they floated: the user's note).
        skirt_top = cy - 0.45 * ry
        bottom = round(cy - ry - SKIRT_DROP + jitter(k, 3, 2))
        for x in (-1, 1):
            slab(s, x * flank, x * (flank + 2), bottom, skirt_top, z0, z1, "shag")
            if (k + (x > 0)) % 2 == 0:
                h = min(4 + 3 * (k % 3), bottom - 2)
                if h > 1:
                    zi = zc + jitter(k, 4, 2)
                    root = np.array([x * (flank + 3.0), round(bottom + (skirt_top - bottom) * 0.55), zi])
                    s.cone(root, np.array([x * (flank + 3.0), bottom - h, zi]), 3, "icicle", w1=1.0, steps=4)
        tops.append((z0, z1, top, back))
    return tops


def build(s, v):
    tops = coat(s, v)
    front = SLICES[0] - SLICE / 2                  # the front of the body
    rear = SLICES[-1] + SLICE / 2
    # A ruff of fur round the neck where it leaves the body, and a beard under the throat.
    slab(s, -14, 14, 30, 56, front - 4, front + 1, "fur")
    slab(s, -9, 9, 20, 34, front - 14, front - 3, "shag")
    # The neck and head: long, hanging low.
    s.taper([(0, 50, -38), (0, 42, -54), (0, 32, -70)], [27, 22, 17], "fur", depths=[26, 23, 17])
    s.rbox((0, 28, -75), (15, 13, 9), "skin", c=3)
    s.box((0, 23.5, -79.6), (10, 1, 1), "mouth")
    for x in (-1, 1):
        s.box((4 * x, 27, -80), (2, 2, 1), "mouth")                  # nostrils
        s.box((10.5 * x, 40, -58), (2, 3, 3), "eye")                 # plain black eyes, nothing over them
        # Ears: small, tufted, on top of the neck behind the eyes.
        s.plate((8 * x, 55, -55), (x, 0.2, -0.3), (x * 0.3, 1, -0.1), 6, 9, 2, "fur")
        s.plate((8.8 * x, 58.5, -55), (x, 0.2, -0.3), (x * 0.5, 1, -0.1), 4, 5, 2, "shag")
        # A tuft of fur on each cheek, below and behind the eye.
        slab(s, x * 7, x * 10, 24, 33, -68, -61, "shag")
    # (Round 2's box of a beard under its chin is gone: "this weird part below his mouth".)
    if v.get("snow"):
        # A patch of snow lying on its back (the user's note for the brown one): flat drifts on the tops of the
        # slices over its hump and shoulders, ragged at the edges, a few small lumps.
        for k, (z0, z1, top, back) in enumerate(tops):
            if not -40 <= z0 <= 10:
                continue
            w = back - 2 - abs(jitter(k, 5, 2))
            dx = jitter(k, 6, 2)
            slab(s, dx - w, dx + w, top, top + 2, z0 + abs(jitter(k, 7, 1)), z1 - abs(jitter(k, 8, 1)), "snowpatch")
            if k % 2 == 0:
                slab(s, dx - 3, dx + 4, top + 2, top + 3, z0 + 2, z1 - 2, "snowpatch")
    # The horns: a great horn of clear ice sweeping forward and up off the nose, flattened side to side like a real
    # woolly rhino's, and a short one behind it.
    s.curve([(0, 32, -79), (0, 44, -90), (0, 60, -98), (0, 74, -99)], 12, [9, 7, 4, 2], "horn", depths=[15, 12, 7, 2])
    s.curve([(0, 41, -67), (0, 49, -71), (0, 57, -71)], 6, [8, 5, 2], "horn", depths=[11, 7, 2])
    # The rump and a short tufted tail.
    s.taper([(0, 50, rear - 2), (0, 44, rear + 6), (0, 38, rear + 8)], [6, 4, 3], "fur")
    slab(s, -3, 3, 26, 38, rear + 7, rear + 9, "shag")
    # Legs: thick pillars of fur, a shaggy cuff round each shin, three-toed feet.
    for x, z in ((-1, -26), (1, -26), (-1, 30), (1, 30)):
        hip = np.array([15.0 * x, 36.0, z])
        s.taper([hip, hip + [1 * x, -18, -2], hip + [1 * x, -30, -1]], [16, 14, 13], "fur")
        foot = hip + [1 * x, -33, -1]
        slab(s, foot[0] - 8.5, foot[0] + 8.5, 6, 17, foot[2] - 8.5, foot[2] + 8.5, "shag")
        s.rbox(foot, (15, 6, 15), "hoof", c=2)
        for k in (-1, 0, 1):
            s.rbox(foot + [k * 4.5, -0.5, -7], (4, 5, 3), "hoof", c=1)


def materials(v):
    fur = v["fur"]
    return {
        "fur": M.fur(fur, seed=701, strand=3.6, frost_amount=0.35),
        "shag": M.shag(fur, seed=703, strand=1.0, ragged=1.0, frost_amount=0.3),
        "skin": M.skin(v["skin"], seed=707, mottle=0.8, frost_amount=0.2),
        "hoof": M.chitin(M.HORN_DARK, seed=709, gloss=1.0, frost_amount=0.15),
        "mouth": M.mouth(),
        "eye": M.solid(EYE, idx=1.0, seed=705, grain=0.3, bev=0.2, outline=0.0),
        "horn": M.ice(M.ICE, alpha=(150, 215), seed=711, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=35),
        "icicle": M.solid(M.ICE, idx=3.6, seed=713, grain=0.8, bev=0.9, outline=0.05),
        "snowpatch": M.snow(seed=715),
    }
