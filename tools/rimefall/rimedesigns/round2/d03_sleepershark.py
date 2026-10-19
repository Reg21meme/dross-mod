"""No. 3 Sleeper Shark (boss): a 400-year-old Greenland shark bursting up through the ice of a frozen lake."""
import math

import numpy as np

from rimekit import mats as M
from rimekit.shapes import catmull, unit

NUMBER = 3
ID = "sleepershark"
NAME = "Sleeper Shark"
SECTION = "boss"
BLURB = ("An ancient shark that sleeps under the ice of a frozen lake; walk over the lake and it bursts up through the "
         "ice (the user's notes for round 2). Thick, scarred and blind, glowing parasites hanging from its eyes (real "
         "Greenland sharks have these, and live for 400 years), its jaws wide: thin hooked teeth above, a saw of "
         "slanted blades below. Slabs of the lake's ice are tipped up round the hole it broke, dark water inside it, "
         "frozen spray thrown up round its body.")
VARIANTS = (
    dict(letter="A", mood="straight up", pose="up",
         note="A: frozen shooting straight up out of the hole, jaws to the sky."),
    dict(letter="B", mood="lunging at you", pose="lunge",
         note="B: frozen lunging out of the hole at you, jaws open."),
)

POSES = {
    # the body's path, tail (under the water) to the top of its head; widths along it; the hole's middle and size
    "up": dict(path=[(0, -60, 44), (0, -10, 40), (0, 46, 34), (0, 100, 20), (0, 138, -2), (0, 160, -24)],
               widths=[30, 46, 56, 58, 54, 48], hole=(0.0, 30.0, 52.0, 56.0)),
    "lunge": dict(path=[(0, -70, 124), (0, -24, 86), (0, 18, 46), (0, 56, 8), (0, 82, -26), (0, 94, -48)],
                  widths=[30, 46, 56, 58, 54, 48], hole=(0.0, 50.0, 54.0, 70.0)),
}


def frame_at(pts, i):
    """The body's frame at point i: forward (to the head), its back (dorsal side) and its own left."""
    a = np.array(pts[max(i - 1, 0)])
    b = np.array(pts[min(i + 1, len(pts) - 1)])
    fwd = unit(b - a)
    back = unit(np.array([0.0, 0.0, 1.0]) - np.dot([0.0, 0.0, 1.0], fwd) * fwd)
    if np.linalg.norm(back) < 0.5:
        back = unit(np.array([0.0, 1.0, 0.0]) - np.dot([0.0, 1.0, 0.0], fwd) * fwd)
    left = unit(np.cross(fwd, back))
    return fwd, back, left


def lake(s, hx, hz, rx, rz, seed):
    """The lake: dark water in the hole, slabs of its ice broken and tipped up round it, chunks thrown about."""
    s.box((hx, -1.5, hz), (2 * rx + 20, 4, 2 * rz + 20), "water")
    n = 11
    for k in range(n):
        a = 2 * math.pi * (k + 0.37 * (k % 3)) / n
        j = ((k * 37 + seed) % 10) / 10.0
        dist = 1.0 + 0.25 * j
        cx, cz = hx + math.sin(a) * rx * dist * 1.35, hz - math.cos(a) * rz * dist * 1.35
        outward = np.array([math.sin(a) / rx, 0.0, -math.cos(a) / rz])
        outward = unit(outward)
        tilt = 0.45 + 0.6 * j                                # how steeply it's tipped up (towards the hole)
        normal = unit(outward * tilt + np.array([0.0, 1.0, 0.0]))
        up = np.cross(normal, np.cross(outward, normal))
        w = 34 + 14 * ((k * 3) % 4)
        h = 30 + 10 * ((k * 5) % 3)
        c = np.array([cx, 4 + 8 * tilt, cz])
        s.plate(c, normal, -up, w, h, 4, "lakeice")
        if k % 3 != 1:
            s.plate(c + normal * 2.6 - up * 6, normal, -up, (w - 8) // 2 + 4, (h - 10) // 2, 1, "lakesnow")  # snow on it
    # Chunks of ice flung up.
    for k, (dx, dy, dz, r) in enumerate(((-52, 40, -10, 6), (48, 62, 14, 5), (30, 92, -40, 4), (-36, 120, 24, 3),
                                         (60, 20, -30, 7), (-60, 16, 30, 6), (12, 140, -30, 3))):
        s.box((hx + dx, dy, hz + dz), (r * 2, r * 1.6, r * 2), "lakeice", rot=(k * 23, k * 41, k * 17))


def spray(s, pts, widths, seed):
    """Frozen water thrown up round its body where it broke the surface: spikes and sheets of clear ice."""
    i = min(range(len(pts)), key=lambda k: abs(pts[k][1] - 6))
    p = np.array(pts[i])
    fwd, back, left = frame_at(pts, i)
    r = widths[i] * 0.5
    for k in range(14):
        a = 2 * math.pi * k / 14
        d = left * math.cos(a) + back * math.sin(a)
        base = p + d * (r + 2) - fwd * 4
        out = unit(d * 0.7 + fwd * 0.9 + np.array([0, 0.3, 0]))
        length = 26 + 18 * ((k * 7 + seed) % 5) / 4
        s.cone(base, base + out * length, 8, "spray", steps=3)
        if k % 2 == 0:
            s.plate(base + out * length * 0.4 + d * 4, d, out, 12, length * 0.7, 1, "spraysheet")


def shark(s, pts, ws):
    s.tube(pts, ws, "skin", depths=[w * 0.92 for w in ws], side=None)
    n = len(pts)
    # Its paler belly (sharks are dark above and pale below), as a band along its underside.
    for i in range(1, n - 1):
        fwd, back, left = frame_at(pts, i)
        a = np.array(pts[i]) - back * ws[i] * 0.44
        b = np.array(pts[i + 1]) - back * ws[i + 1] * 0.44
        s.seg(a, b, ws[i] * 0.55, 3, "belly", side=left, ext=1.0)
    # Gill slits, five a side, behind the head; a pale scar or two; the lateral line.
    gi = int(n * 0.78)
    for x in (-1, 1):
        for k in range(5):
            i = gi - k
            p = np.array(pts[i])
            fwd, back, left = frame_at(pts, i)
            q = p + left * x * ws[i] * 0.47 - back * 2
            s.seg(q - back * 7, q + back * 5, 2, 1, "gill", side=fwd)
    for k, i in enumerate((int(n * 0.35), int(n * 0.5), int(n * 0.62))):
        p = np.array(pts[i])
        fwd, back, left = frame_at(pts, i)
        x = 1 if k % 2 else -1
        s.seg(p + left * x * ws[i] * 0.48 + back * 6 - fwd * 6, p + left * x * ws[i] * 0.48 - back * 4 + fwd * 8, 2, 1,
              "scar", side=left)
    # Fins: pectorals out to the sides, two small dorsals far back, low.
    i = int(n * 0.66)
    p = np.array(pts[i])
    fwd, back, left = frame_at(pts, i)
    for x in (-1, 1):
        root = p + left * x * ws[i] * 0.45 - back * 6
        tip = root + unit(left * x * 1.0 - fwd * 0.55 - back * 0.3) * 40
        s.plate((root + tip) / 2, unit(np.cross(tip - root, fwd)), root - tip, 22, 40, 3, "fin")
    for frac, h, base in ((0.5, 34, 30), (0.3, 24, 22)):
        i = int(n * frac)
        p = np.array(pts[i])
        fwd, back, left = frame_at(pts, i)
        # A swept-back triangle: three plates, broad at the root, narrow at the tip.
        root = p + back * ws[i] * 0.4
        tip = root + unit(back - fwd * 0.75) * h
        for k, (t0, t1, w) in enumerate(((0.0, 0.45, base), (0.35, 0.8, base * 0.6), (0.7, 1.0, base * 0.3))):
            a = root + (tip - root) * t0 - fwd * base * 0.12 * k
            b = root + (tip - root) * t1 - fwd * base * 0.12 * k
            s.plate((a + b) / 2, left, a - b, w, np.linalg.norm(b - a) + 4, 3, "fin")


def head(s, neck, fwd, back, left, gape=52.0):
    """
    The head at the body's end, jaws wide: the blunt snout and upper jaw lifted, the lower jaw dropped, the dark mouth
    between them lined with gums and a U of teeth on each jaw, blind eyes with glowing parasites hanging off them.
    """
    a = math.radians(gape * 0.4)
    b = math.radians(gape * 0.6)
    up_d = unit(fwd * math.cos(a) + back * math.sin(a))          # the upper jaw's way
    up_back = unit(back * math.cos(a) - fwd * math.sin(a))       # ...and its top
    lo_d = unit(fwd * math.cos(b) - back * math.sin(b))          # the lower jaw's way
    lo_back = unit(back * math.cos(b) + fwd * math.sin(b))       # ...and its inside (facing the upper jaw)
    W = 52.0
    hinge_up = neck + back * 8
    hinge_lo = neck - back * 16
    # The upper head: the skull and its blunt, rounded snout.
    s.tube([neck - fwd * 10, hinge_up + up_d * 16, hinge_up + up_d * 36 + up_back * 2, hinge_up + up_d * 50],
           [W + 4, W, W - 8, 26], "skin", depths=[44, 34, 26, 16], side=left)
    # The lower jaw: a heavy scoop.
    s.tube([hinge_lo - fwd * 6, hinge_lo + lo_d * 22, hinge_lo + lo_d * 42], [W - 2, W - 6, W - 18], "skin",
           depths=[16, 13, 10], side=left)
    # Inside: the dark throat, gums along both jaws.
    s.plate(neck + fwd * 2 - back * 4, fwd, back, W - 12, 26, 3, "mouth")
    s.plate(hinge_up + up_d * 26 - up_back * 13, -up_back, up_d, W - 14, 40, 2, "gum")
    s.plate(hinge_lo + lo_d * 22 + lo_back * 7, lo_back, lo_d, W - 16, 36, 2, "gum")
    s.plate(hinge_lo + lo_d * 10 + lo_back * 11, lo_back, lo_d, W - 24, 14, 2, "mouth")
    # Teeth: a U of thin hooked teeth round the upper jaw, slanted blades round the lower.
    for k in range(11):
        t = (k - 5) / 5.0
        ang = t * math.pi / 2
        p = hinge_up + up_d * (8 + 38 * math.cos(ang) ** 0.6) + left * (W * 0.42 * math.sin(ang)) - up_back * 14
        s.cone(p, p - up_back * (9 - 3 * abs(t)) + up_d * 1.5, 3.0, "teeth", steps=2)
    for k in range(11):
        t = (k - 5) / 5.0
        ang = t * math.pi / 2
        p = hinge_lo + lo_d * (8 + 32 * math.cos(ang) ** 0.6) + left * (W * 0.38 * math.sin(ang)) + lo_back * 7
        s.seg(p, p + lo_back * (8 - 2 * abs(t)) + left * 2 * math.sin(ang), 4, 1, "teeth",
              side=unit(left * math.cos(ang) + lo_d * math.sin(ang) * 0.3))
    # Nostrils; blind eyes, glowing parasites dangling from them.
    for x in (-1, 1):
        s.box(hinge_up + up_d * 46 + up_back * 6 + left * x * 7, (3, 2, 3), "gill")
        eye = hinge_up + up_d * 22 + up_back * 6 + left * x * (W * 0.47)
        s.box(eye, (3, 5, 5), "eye")
        for k, length in enumerate((14, 20, 10)):
            t0 = eye + left * x * 1.2 + up_d * (k - 1) * 1.8
            hang = unit(np.array([0, -1.0, 0]) + left * x * 0.15)
            s.seg(t0, t0 + hang * length, 1, 1, "parasite")
            s.box(t0 + hang * length, (2, 3, 2), "parasite")


def build(s, v):
    P = POSES[v["pose"]]
    pts = catmull(P["path"], 14)
    ws = list(np.interp(np.linspace(0, 1, len(pts)), np.linspace(0, 1, len(P["widths"])), P["widths"]))
    shark(s, pts, ws)
    fwd, back, left = frame_at(pts, len(pts) - 1)
    head(s, np.array(pts[-1]), fwd, back, left)
    spray(s, pts, ws, 3)
    hx, hz, rx, rz = P["hole"]
    lake(s, hx, hz, rx, rz, 5)


def materials(v):
    return {
        "skin": M.skin(M.SHARK, seed=2101, mottle=1.6, frost_amount=0.3, scars=0.45),
        "fin": M.skin(M.SHARK, seed=2103, mottle=1.0, frost_amount=0.2, outline=0.2),
        "belly": M.skin(M.FISH, seed=2127, mottle=1.0, frost_amount=0.1, scars=0.3),
        "gill": M.solid(M.MOUTH, idx=1.5, seed=2113, outline=0.0),
        "scar": M.solid(M.SNOW, idx=4.2, seed=2111, outline=0.0),
        "mouth": M.mouth(),
        "gum": M.skin(M.MUZZLE, seed=2115, mottle=0.8, frost_amount=0.0),
        "teeth": M.teeth(),
        "eye": M.solid(M.PALE_SKIN, idx=3.8, seed=2107, outline=0.0),
        "parasite": M.glowing(M.GLOW_GREEN, seed=2109, idx=3.4),
        "water": M.solid(M.palette("#06121c", "#0a1a27", "#0f2333", "#152d40"), idx=1.5, seed=2117, grain=1.6,
                         outline=0.0),
        "lakeice": M.ice(M.ICE, alpha=(110, 175), seed=2119, cracks=0.5, bubbles=0.02, frost_amount=0.05),
        "lakesnow": M.snow(seed=2121),
        "spray": M.ice(M.ICE_WHITE, alpha=(130, 200), seed=2123, cracks=0.2, frost_amount=0.2),
        "spraysheet": M.ice(M.ICE, alpha=(60, 120), seed=2125, cracks=0.0, frost_amount=0.0, rim=1.4),
    }
