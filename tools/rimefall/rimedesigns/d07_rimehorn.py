"""No. 7 Rimehorn (regular mob): an ice-age woolly rhino in layers of shaggy fur, its two horns of clear ice."""
import numpy as np

from rimekit import mats as M
from rimekit.shapes import Scaled, unit

NUMBER = 7
ID = "rimehorn"
NAME = "Rimehorn"
SECTION = "mob"
# Round 4 (CLAUDE.md step 14): a real Rimefall animal now (tools/rimefall/mobs.py), so it leaves the statue gallery.
GALLERY = False
BLURB = ("An ice-age woolly rhino: a high shoulder hump, a long low head, and a thick coat of fur in layers, stepping "
         "out from its back to a long shaggy skirt along its belly, icicles hanging off it, a ruff round its neck. "
         "Its two horns, a great sweeping one on its nose and a short one behind it, are clear "
         "ice. Plain black eyes. Round 2: a regular mob, the same size as before, the ice block on its back gone (the "
         "user's notes). Round 3: the coat rebuilt from straight layers (the hanging locks crossed through each other "
         "and the body), black eyes with no fur over them, and on the brown one a patch of snow on its back. Round 4 "
         "(a Rimefall animal now: packs of 6-8, frosted males and dark brown females): about 4.5 blocks tall and 8 "
         "long (the user's size; it was 4.9 and 9.4).")
VARIANTS = (
    dict(letter="A", mood="frosted coat", fur=M.WOOLLY_PALE, skin=M.FROST_SKIN),
    dict(letter="B", mood="dark brown coat", fur=M.WOOLLY, skin=M.STONE, snow=True),
)
# The user's picks from round 2 (October 18, 2026); the others are archived in round2/. Round 2's coat of hanging locks
# is archived in round3/.
VARIANTS = tuple(v for v in VARIANTS if v["letter"] in ('A', 'B'))

# Round 4 (the user: "make them like four and a half blocks tall and like eight blocks long"; round 3's are archived
# in round4/): the body one slice shorter (its sections pulled in to match) and the whole animal at K of the size.
K = 0.92
# The body's cross-section along its length: (z, centre height, half-width, half-height).
SECTIONS = ((-40, 50, 20, 22), (-24, 51, 25, 28), (-4, 46, 24, 26), (14, 43, 23, 24), (31, 42, 19, 20), (38, 42, 13, 14))
# The coat is built in slices this long (units), front to back, each slice from straight boxes only: nothing is turned,
# so no two pieces of fur cut through each other (the user: "a lot of clipping of the body that doesn't look good").
SLICE = 8
SLICES = tuple(range(-42, 39, SLICE))       # their middles (11; round 3 had 12)
LEGS_Z = (-26, 24)                          # the front and hind legs (round 3: -26 and 30)
LEGS = (("fl", 1, LEGS_Z[0]), ("fr", -1, LEGS_Z[0]), ("hl", 1, LEGS_Z[1]), ("hr", -1, LEGS_Z[1]))   # his left is +x


def leg_points(x, z):
    """A leg's hip, knee, ankle and the middle of its foot (design units)."""
    hip = np.array([15.0 * x, 36.0, z])
    return hip, hip + [1 * x, -18, -2], hip + [1 * x, -30, -1], hip + [1 * x, -33, -1]
SKIRT_DROP = 7                              # how far the skirt hangs below the belly
EYE = M.palette("#040405", "#08080a", "#0d0d10", "#141418")    # black


def body_at(z):
    zs = [s[0] for s in SECTIONS]
    return tuple(float(np.interp(z, zs, [s[i] for s in SECTIONS])) for i in (1, 2, 3))


def jitter(k, seed, amount):
    """A whole number from -amount to amount, the same every build."""
    return ((k * 7 + seed * 13) % (2 * amount + 1)) - amount


def slab(s, x0, x1, y0, y1, z0, z1, mat):
    """
    A straight box between two corners (either order), its corners on whole units of the finished (scaled) model, so
    neighbouring slabs still meet exactly after the scaling (no hairline gaps, no overlaps).
    """
    k = getattr(s, "k", 1.0)
    raw = getattr(s, "s", s)
    a = np.round(np.array([x0, y0, z0], dtype=float) * k)
    b = np.round(np.array([x1, y1, z1], dtype=float) * k)
    lo, hi = np.minimum(a, b), np.maximum(a, b)
    if (hi - lo).min() <= 0:
        return
    raw.box((lo + hi) / 2.0, hi - lo, mat)


def coat(s, v):
    """
    The body, slice by slice: the back on top, then layers stepping out and down (the upper flank, the widest flank),
    the belly underneath, and a long shaggy skirt hanging outside the flanks below the belly, icicles off its edge.
    Returns each slice's (z0, z1, top, back half-width) for the snow.
    """
    s = s.part("body")
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


def head(s):
    """
    The head, long and low, tilted down: the neck, the furry skull, the bare face and the muzzle, each a little smaller
    and all at the same tilt, one running into the next (round 2's head was a turned fur neck with a box of a snout
    stuck on its end: the user saw the snout come loose). Plain black eyes with nothing over them, small tufted ears;
    on top of its head a short horn of clear ice, and a great one growing from the top of its nose, behind the
    nostrils, sweeping forward and up, flattened side to side like a real woolly rhino's (the user's note). Nothing
    hangs under its chin or throat (the user saw "this weird part below his mouth").
    """
    s.part("neck").seg((0, 47, -44), (0, 40, -60), 22, 22, "fur", side=(1, 0, 0))   # the neck
    ears = {1: s.part("ear_l"), -1: s.part("ear_r")}
    s = s.part("head")
    s.seg((0, 41, -56), (0, 35, -70), 18, 19, "fur", side=(1, 0, 0))                # the skull
    # (The face and muzzle 6 units shorter than the first version of this head: the user found the snout stuck out
    # too far.)
    s.seg((0, 36, -66), (0, 30.5, -76), 15, 15, "skin", side=(1, 0, 0))             # the face
    tip = np.array([0.0, 28, -80])
    s.seg((0, 31, -73), tip, 13, 12, "skin", side=(1, 0, 0))                        # the muzzle
    d = tip - np.array([0.0, 31, -73])
    d = d / np.linalg.norm(d)
    up = np.array([0.0, -d[2], d[1]])                     # up across the muzzle's front
    for x in (-1, 1):
        s.plate(tip + up * 2.5 + [x * 3.5, 0, 0], d, up, 2, 2, 1, "mouth")              # nostrils
        s.box((9.6 * x, 40, -64), (2, 3, 3), "eye")                                      # plain black eyes
        # Ears: small, tufted, on top of the neck behind the eyes.
        ears[x].plate((8 * x, 56, -54), (x, 0.2, -0.3), (x * 0.3, 1, -0.1), 6, 9, 2, "fur")
        ears[x].plate((8.8 * x, 59.5, -54), (x, 0.2, -0.3), (x * 0.5, 1, -0.1), 4, 5, 2, "shag")
    s.plate(tip - up * 4.6, d, up, 10, 1, 1, "mouth")                                    # its mouth
    # The horns: a short one on top of its head, the great one off the top of its nose.
    s.curve([(0, 44, -65), (0, 50.5, -67), (0, 56.5, -66)], 6, [7, 5, 2], "horn", depths=[9, 6, 2])
    s.curve([(0, 34.5, -76), (0, 46, -83), (0, 60, -87), (0, 73, -86)], 12, [9, 7, 4, 2], "horn",
            depths=[11, 9, 6, 2])


def build(s, v):
    s = Scaled(s, K)
    tops = coat(s, v)
    front = SLICES[0] - SLICE / 2                  # the front of the body
    rear = SLICES[-1] + SLICE / 2
    # A ruff of fur round the neck where it leaves the body.
    slab(s.part("body"), -14, 14, 30, 56, front - 4, front + 1, "fur")
    head(s)
    if v.get("snow"):
        # A patch of snow lying on its back (the user's note for the brown one): flat drifts on the tops of the
        # slices over its hump and shoulders, ragged at the edges, a few small lumps.
        snow = s.part("snow")
        for k, (z0, z1, top, back) in enumerate(tops):
            if not -40 <= z0 <= 10:
                continue
            w = back - 2 - abs(jitter(k, 5, 2))
            dx = jitter(k, 6, 2)
            slab(snow, dx - w, dx + w, top, top + 2, z0 + abs(jitter(k, 7, 1)), z1 - abs(jitter(k, 8, 1)), "snowpatch")
            if k % 2 == 0:
                slab(snow, dx - 3, dx + 4, top + 2, top + 3, z0 + 2, z1 - 2, "snowpatch")
    # The rump and a short tufted tail.
    tail = s.part("tail")
    tail.taper([(0, 50, rear - 2), (0, 44, rear + 6), (0, 38, rear + 8)], [6, 4, 3], "fur")
    slab(tail, -3, 3, 26, 38, rear + 7, rear + 9, "shag")
    # Legs: thick pillars of fur, a shaggy cuff round each shin, three-toed feet. (Round 4: each in two parts, the
    # upper leg and the shin, so it can bend at the knee when it walks.)
    for name, x, z in LEGS:
        hip, knee, ankle, foot = leg_points(x, z)
        s.part(f"leg_{name}").taper([hip, knee], [16, 14], "fur")
        sh = s.part(f"shin_{name}")
        sh.taper([knee + unit(knee - ankle) * 2.0, knee, ankle], [14, 14, 13], "fur")
        slab(sh, foot[0] - 8.5, foot[0] + 8.5, 6, 17, foot[2] - 8.5, foot[2] + 8.5, "shag")
        hoof = s.part(f"foot_{name}")                   # (round 4: on its own bone, the ankle, so it stays flat)
        hoof.rbox(foot, (15, 6, 15), "hoof", c=2)
        for k in (-1, 0, 1):
            hoof.rbox(foot + [k * 4.5, -0.5, -7], (4, 5, 3), "hoof", c=1)


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
