"""
The Emberdactyl's body, shared by its designs (firedesigns/e01-e06): a volcanic pterosaur hovering with its wings
spread, built in design units for the adult (Bedrock units, 16 a block; its front -z, its own left +x). A design is a
dict of choices (its crest, tail, extras) passed to build(); the young reborn form is the same animal drawn through a
smaller scale, sleeker, its wings swept back for speed, its head a little bigger, its glow brighter (the design's
materials decide the colours).

The parts:
  torso, chest keel     tipped nose-up by PITCH (it hovers, beating its wings)
  neck, head            a long neck; a long beak with a glowing seam between its jaws and serrated obsidian edges;
                        ember eyes under heavy brows; the crest (the design's)
  wings                 the arm (humerus, forearm), the wrist's three clawed fingers, the long wing finger in three
                        bones to its tip; the membrane: rows of plates between the bones and a curved trailing edge,
                        billowing a little; its last row's trailing edge burnt ragged ("edge")
  legs                  short, hanging, talons of obsidian
  tail                  short and spiked, or long with a vane (the design's)
"""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, flames, leg
from rimekit.shapes import Scaled, unit, v3

HIDE = F.palette("#120a08", "#1c100c", "#281610", "#351d15", "#45261b", "#583223")
HIDE_YOUNG = F.palette("#2a120a", "#3a1a0e", "#4c2414", "#60301a", "#783e22", "#924e2c")
OBS_MEMBRANE = F.palette("#0e0812", "#170d1d", "#211329", "#2d1a37", "#3b2448", "#4e305e")
SAIL = F.palette("#5a1406", "#7a1e08", "#9c2c0c", "#c04012", "#de5c1c", "#f27c2c")
OBS_RED = F.palette("#090505", "#130b0a", "#1f1210", "#2c1915", "#3e221c", "#583026")
ASH_HIDE = F.palette("#26221f", "#332e2a", "#423b36", "#534a43", "#665b52", "#7c7064")


def materials(theme, young=False):
    """The Emberdactyl's materials for one design's colour theme; the young's are brighter and hotter."""
    hot = 0.25 if young else 0.0
    warm = 0.08 if young else 0.0                    # (the young ones' rocky hides glow only a little more)
    hide_pal = HIDE_YOUNG if young else HIDE
    out = {
        "hide": F.crust(hide_pal, seed=1611, cracks=0.12 + warm, cell=7.0, width=0.5),
        "belly": F.skin(F.palette("#24110b", "#30170f", "#3e1e13", "#4e2618", "#60301e") if not young else
                        F.palette("#5a2a14", "#70361a", "#884222", "#a0522a", "#b86434"), seed=1613, mottle=1.0),
        "wingbone": F.crust(hide_pal, seed=1615, cracks=0.08 + warm, cell=4.0, width=0.5),
        "joint": F.glowing(F.EMBER, seed=1617, idx=2.6, base_pal=F.LAVA),
        "claw": F.obsidian(OBS_RED, seed=1619, gloss=1.9),
        "beak": F.obsidian(OBS_RED, seed=1621, gloss=1.7, veins=0.2),
        "seam": F.lava(F.EMBER, seed=1623),
        "eye": F.eye_disc(iris=F.EYE_EMBER, pupil="slit", rim=0.16, glow=240.0, seed=1625),
        "crest": F.obsidian(OBS_RED, seed=1627, gloss=1.8, veins=0.3 + hot),
        "spike": F.obsidian(OBS_RED, seed=1629, gloss=1.8),
        "membrane": F.membrane(seed=1631, hot=0.22 + hot),
        "membraneedge": F.membrane(seed=1633, hot=0.28 + hot, ragged=0.8),
        "lava": F.lava(seed=1635),
        "sail": F.membrane(SAIL, seed=1637, hot=0.6, vein_pal=F.WHITE_HOT),
        "throat": F.lava(F.EMBER, seed=1639),
        "plate": F.crust(seed=1641, cracks=0.6, cell=4.0, width=0.6),
        "vane": F.membrane(SAIL, seed=1643, hot=0.7, ragged=0.6),
        "flame": F.flame(seed=1645),
    }
    if theme == "caldera":
        out["hide"] = F.crust(F.BASALT if not young else hide_pal, seed=1647, cracks=0.3 + warm, cell=7.0,
                              ash_top=0.35)
        out["crest"] = F.crust(F.BASALT, seed=1649, cracks=0.45 + warm, cell=3.0, width=0.6)
        out["membrane"] = F.membrane(seed=1651, hot=0.4 + hot, holes=0.05)
        out["membraneedge"] = F.membrane(seed=1653, hot=0.5 + hot, ragged=1.0, holes=0.05)
    elif theme == "obsidian":
        out["hide"] = F.obsidian(seed=1655, veins=0.45 + warm, gloss=1.6)
        out["wingbone"] = F.obsidian(seed=1657, veins=0.3 + hot, cell=4.0)
        out["membrane"] = F.membrane(OBS_MEMBRANE, seed=1659, hot=0.4 + hot)
        out["membraneedge"] = F.membrane(OBS_MEMBRANE, seed=1661, hot=0.5 + hot, ragged=0.5)
        out["belly"] = F.obsidian(seed=1663, veins=0.2, gloss=1.2)
    elif theme == "sail":
        out["membrane"] = F.membrane(seed=1665, hot=0.35 + hot)
        out["membraneedge"] = F.membrane(seed=1667, hot=0.45 + hot, ragged=0.7)
    elif theme == "magma":
        out["hide"] = F.lava(seed=1669, crust=0.62 - hot * 0.6)
        out["wingbone"] = F.lava(seed=1671, crust=0.55 - hot * 0.5)
        out["membrane"] = F.membrane(seed=1673, hot=0.85, veins=1.3)
        out["membraneedge"] = F.membrane(seed=1675, hot=0.9, veins=1.3, ragged=1.1, holes=0.04)
    elif theme == "volcanic":
        # the user's "its body looks volcanic" (October 27, 2026): obsidian (their pick) crusted like the volcano's
        # rock, lava running down its body, its wing bones and its wings like the volcano's rivers
        # (mostly dark crust, the lava showing in channels between, like the volcano's flanks)
        out["hide"] = F.lava(seed=1687, crust=0.84 - hot * 0.2, flow=1.3, crust_pal=F.BASALT)
        out["wingbone"] = F.lava(seed=1689, crust=0.8 - hot * 0.2, flow=1.3, crust_pal=F.BASALT)
        out["membrane"] = F.membrane(OBS_MEMBRANE, seed=1691, hot=0.5 + hot, veins=1.5, vein_pal=F.LAVA)
        out["membraneedge"] = F.membrane(OBS_MEMBRANE, seed=1693, hot=0.6 + hot, veins=1.5, vein_pal=F.LAVA, ragged=0.6)
        out["belly"] = F.crust(F.BASALT, seed=1695, cracks=0.4 + warm, cell=5.0, width=0.6)
        out["rock"] = F.crust(F.BASALT, seed=1697, cracks=0.3, cell=4.0, width=0.5, ash_top=0.3)
    elif theme == "ash":
        out["hide"] = F.crust(ASH_HIDE if not young else F.ASH, seed=1677, cracks=0.16 + warm, cell=6.0, ash_top=0.5)
        out["wingbone"] = F.crust(ASH_HIDE, seed=1679, cracks=0.1 + warm, cell=4.0)
        out["membrane"] = F.membrane(F.ASH_DARK, seed=1681, hot=0.3 + hot)
        out["membraneedge"] = F.membrane(F.ASH_DARK, seed=1683, hot=0.4 + hot, ragged=0.9)
        out["belly"] = F.skin(F.ASH, seed=1685, mottle=1.2)
    return out

PITCH = 16.0                       # degrees, nose up
BODY = np.array([0.0, 64.0, 0.0])  # the torso's middle (the adult hovers about 2 blocks off the ground)
# Its wingspan (blocks, tip to tip) as built at scale 1, adult or young; a design asks for its own (`span`): the user,
# October 27, 2026: "at least 35 blocks, like a Rodan level", shown at 35, 45 and 55.
BASE_SPAN = 17.76


def scale_for(span):
    """The scale that gives a wingspan of `span` blocks."""
    return span / BASE_SPAN


def wing_points(sign, young, shoulder):
    """The wing's bones (leading edge: shoulder, elbow, wrist, the finger's two joints, the tip) and the trailing
    edge's points back along it to the body; the young's swept back."""
    sh = v3(shoulder)
    sweep = 14.0 if young else 0.0
    lift = 0.75 if young else 1.0                     # (the young's held a little lower, swept back)
    el = sh + np.array([sign * 22.0, 20.0 * lift, 8.0 + sweep * 0.3])
    wr = el + np.array([sign * 24.0, 26.0 * lift, -2.0 + sweep * 0.6])
    f1 = wr + np.array([sign * 30.0, 12.0 * lift, 4.0 + sweep])
    f2 = f1 + np.array([sign * 28.0, 2.0 * lift, 8.0 + sweep * 1.2])
    tip = f2 + np.array([sign * 22.0, -8.0, 14.0 + sweep * 1.4])
    lead = [sh, el, wr, f1, f2, tip]
    # The trailing edge: from the hips out under the arm and the finger to the tip, the membrane hanging below the
    # bones so it shows from the front.
    trail = [sh + np.array([sign * 2.0, -30.0, 36.0]), el + np.array([sign * 2.0, -40.0, 30.0]),
             wr + np.array([sign * 2.0, -50.0, 30.0]), f1 + np.array([sign * 6.0, -42.0, 26.0]),
             f2 + np.array([sign * 8.0, -26.0, 18.0]), tip]
    return lead, trail


def membrane(s, lead, trail, mats, rows=3, bulge=5.0, normal_hint=(0.0, 0.1, -1.0)):
    """The membrane between the bones and the trailing edge: `rows` rows of plates across each section; the last
    row's material is mats[1] (its trailing edge burnt ragged), the others mats[0]."""
    nh = unit(normal_hint)
    for i in range(len(lead) - 1):
        a0, a1 = lead[i], lead[i + 1]
        b0, b1 = trail[i], trail[i + 1]
        for r in range(rows):
            t0 = r / rows
            t1 = (r + 1) / rows

            def at(a, b, t):
                p = a + (b - a) * t
                return p + nh * bulge * math.sin(math.pi * t)

            p00, p01 = at(a0, b0, t0), at(a1, b1, t0)        # the row's leading side
            p10, p11 = at(a0, b0, t1), at(a1, b1, t1)        # its trailing side
            c = (p00 + p01 + p10 + p11) / 4.0
            span = ((p01 - p00) + (p11 - p10)) / 2.0
            chord = ((p00 - p10) + (p01 - p11)) / 2.0          # from the trailing side to the leading side
            n = np.cross(span, chord)
            if np.linalg.norm(n) < 1e-6:
                continue
            n = unit(n)
            if np.dot(n, nh) < 0:
                n = -n
            w = (np.linalg.norm(p01 - p00) + np.linalg.norm(p11 - p10)) / 2.0 * 1.12 + 2.0
            h = np.linalg.norm(chord) * 1.1 + 1.0
            if w < 1.5 or h < 1.5:
                continue
            mat = mats[1] if r == rows - 1 else mats[0]
            s.plate(c + n * (0.12 * ((i + r) % 3)), n, chord, w, h, 1, mat, inflate=0.04 * ((i + r) % 2))


def wing(s, sign, shoulder, d, young):
    lead, trail = wing_points(sign, young, shoulder)
    # The arm and the wing finger: bone covered in hide, the joints glowing.
    widths = [11.0, 9.0, 8.0, 6.0, 4.0, 2.0]
    leg(s, lead, widths, "wingbone", joint_scale=1.12)
    s.blob(lead[2], (5.0, 5.0, 5.0), "joint", slices=2)                    # the wrist, a glowing knuckle
    # The wrist's three clawed fingers, pointing forward.
    wr = lead[2]
    for k in range(3):
        f = wr + np.array([sign * (2.0 + k * 2.0), 3.0 - k * 2.0, -10.0 - k * 2.0])
        s.seg(wr, f, 3, 3, "wingbone")
        s.cone(f, f + np.array([sign * 1.0, -2.0, -5.0]), 2, "claw", steps=1)
    # Spines along the leading edge (the obsidian design), or none.
    if d.get("edge_spines"):
        for i in range(1, 5):
            a, b = lead[i], lead[i + 1]
            for t in (0.3, 0.7):
                p = a + (b - a) * t
                s.cone(p, p + np.array([0.0, 6.0, -6.0]) + unit(b - a) * 2.0, 3, "spike", steps=2)
    membrane(s, lead, trail, ("membrane", "membraneedge"))
    return lead, trail


MOON_RADIUS = 15.0                 # units: the half-moon crest on the beak (looks B and C)


def head(s, c, d):
    """The head at `c` (the skull's middle): beak, jaws, eyes, brows, the crest."""
    c = v3(c)
    s.blob(c, (8.5, 8.0, 12.0), "hide", axis=(0, 0, 1), side=(1, 0, 0), slices=4)
    # The beak: a long upper bill, a shorter lower one, a glowing seam between them, serrated edges.
    tip = c + np.array([0.0, -9.0, -50.0])
    s.taper([c + np.array([0.0, 1.0, -8.0]), c + np.array([0.0, -1.0, -26.0]), tip], [9.0, 6.0, 2.0], "beak",
            depths=[9.0, 6.0, 2.0], side=(1, 0, 0))
    ltip = c + np.array([0.0, -12.0, -44.0])
    s.taper([c + np.array([0.0, -6.0, -8.0]), c + np.array([0.0, -8.5, -24.0]), ltip], [7.0, 5.0, 2.0], "beak",
            depths=[4.0, 3.0, 2.0], side=(1, 0, 0))
    s.seg(c + np.array([0.0, -4.0, -8.0]), c + np.array([0.0, -8.5, -40.0]), 4, 1, "seam", side=(1, 0, 0))
    for k in range(6):
        t = 0.2 + k * 0.12
        p = c + np.array([0.0, -4.0, -8.0]) + (c + np.array([0.0, -8.5, -40.0]) - c - np.array([0.0, -4.0, -8.0])) * t
        for sign in (1.0, -1.0):
            s.cone(p + np.array([sign * (3.0 - t * 2.0), 0.5, 0.0]), p + np.array([sign * (3.2 - t * 2.0), -2.5, -1.0]),
                   1, "claw", steps=1)
    # The eyes and heavy brows.
    for sign in (1.0, -1.0):
        e = c + np.array([sign * 7.8, 3.0, -5.0])
        s.seg(e + np.array([0.0, 0.0, 3.0]), e + np.array([0.0, 0.0, -3.0]), 6, 2, "eye", side=(0, 1, 0))
        s.seg(c + np.array([sign * 6.0, 7.0, 0.0]), c + np.array([sign * 7.0, 6.0, -10.0]), 4, 4, "hide", side=(0, 1, 0))
    crest = d.get("crest", "swept")
    back = c + np.array([0.0, 6.0, 6.0])
    if crest == "swept":
        s.taper([back, back + np.array([0.0, 12.0, 18.0]), back + np.array([0.0, 20.0, 40.0]),
                 back + np.array([0.0, 24.0, 54.0])], [3.0, 3.0, 2.0, 1.0], "crest", depths=[14.0, 12.0, 8.0, 3.0],
                side=(1, 0, 0))
        s.seg(back + np.array([0.0, 4.0, 4.0]), back + np.array([0.0, 22.0, 46.0]), 2, 2, "seam", side=(1, 0, 0))
    elif crest == "caldera":
        # A crown of horns round a crater on its head, lava glowing in it.
        top = c + np.array([0.0, 8.0, 0.0])
        s.blob(top + np.array([0.0, 1.0, 0.0]), (6.0, 2.0, 7.0), "lava", slices=1)
        for k in range(9):
            a = 2.0 * math.pi * k / 9
            base = top + np.array([math.cos(a) * 7.0, 0.0, math.sin(a) * 8.0])
            L = 8.0 + 6.0 * (0.5 + 0.5 * math.sin(a))
            s.cone(base, base + np.array([math.cos(a) * 3.0, L, math.sin(a) * 3.0 + 3.0]), 4, "crest", steps=2)
        for sign in (1.0, -1.0):
            s.curve([c + np.array([sign * 7.0, 2.0, 6.0]), c + np.array([sign * 14.0, 6.0, 16.0]),
                     c + np.array([sign * 16.0, 14.0, 26.0])], 4, [5, 3, 1], "crest")
    elif crest == "blade":
        # two blade horns sweeping back off its head (`horn`: how much longer than the first look's)
        h = d.get("horn", 1.0)
        for sign in (1.0, -1.0):
            b = back + np.array([sign * 2.5, 0.0, 0.0])
            s.taper([b, b + np.array([sign * 2.0, 16.0 * h, 16.0 * h]), b + np.array([sign * 4.0, 26.0 * h, 42.0 * h])],
                    [2.0, 2.0, 1.0], "crest", depths=[12.0, 10.0, 2.0], side=(1, 0, 0))
        if d.get("beak_fin") == "moon":
            # the user's "little thingy on its beak, the half circle thing", made a half moon standing on the bill,
            # a glowing seam round its edge
            mid = c + np.array([0.0, 2.0, -21.0])
            r = MOON_RADIUS
            n = 7
            for i in range(n):
                z0 = -r + 2.0 * r * i / n
                z1 = -r + 2.0 * r * (i + 1) / n
                zm = (z0 + z1) / 2.0
                hgt = math.sqrt(max(0.0, r * r - zm * zm))
                s.box(mid + np.array([0.0, hgt / 2.0, zm]), (2.0, max(1.0, hgt), z1 - z0 + 0.4), "crest")
            for i in range(11):
                a = math.pi * (i + 0.5) / 11
                p = mid + np.array([0.0, math.sin(a) * (r + 0.6), -math.cos(a) * (r + 0.6)])
                s.box(p, (2.4, 2.2, 2.2), "seam")
        else:
            s.taper([c + np.array([0.0, 6.0, -14.0]), c + np.array([0.0, 14.0, -26.0])], [2.0, 1.0], "crest",
                    depths=[8.0, 2.0], side=(1, 0, 0))
    elif crest == "sail":
        # A tall sail of membrane on a bony frame, from its snout back over its head.
        frame = [c + np.array([0.0, 2.0, -24.0]), c + np.array([0.0, 26.0, -14.0]), c + np.array([0.0, 40.0, 6.0]),
                 c + np.array([0.0, 30.0, 30.0]), c + np.array([0.0, 8.0, 22.0])]
        s.curve(frame, 8, [3, 3, 3, 2, 3], "crest")
        for k in range(4):
            t = (k + 1) / 5.0
            hi = frame[1] + (frame[3] - frame[1]) * t + np.array([0.0, 6.0 * math.sin(math.pi * t), 0.0])
            lo = c + np.array([0.0, 6.0, -10.0 + 28.0 * t])
            s.plate((hi + lo) / 2.0, (1.0, 0.0, 0.0), hi - lo, 12, int(np.linalg.norm(hi - lo)) + 4, 1, "sail")
            s.seg(lo, hi, 1, 1, "seam")
    elif crest == "double":
        s.taper([back, back + np.array([0.0, 10.0, 16.0]), back + np.array([0.0, 14.0, 30.0])], [3.0, 2.0, 1.0],
                "crest", depths=[10.0, 8.0, 3.0], side=(1, 0, 0))
        s.taper([c + np.array([0.0, 5.0, -18.0]), c + np.array([0.0, 14.0, -24.0]), c + np.array([0.0, 18.0, -20.0])],
                [3.0, 2.0, 1.0], "crest", depths=[9.0, 6.0, 2.0], side=(1, 0, 0))
        # A glowing throat pouch.
        s.blob(c + np.array([0.0, -12.0, -10.0]), (5.0, 5.0, 8.0), "throat", slices=2)


def build(s, d, young=False):
    """The whole animal (through `s`); `d` the design's choices."""
    k = scale_for(d["span"]) if "span" in d else (d.get("young_scale", 0.56) if young else 1.0)
    head_k = 1.42 if young else 1.28
    slim = 0.86 if young else 1.0
    bulk = 1.25
    s = Scaled(s, k) if k != 1.0 else s
    t = Xform(s, pivot=BODY, axis=(1, 0, 0), deg=PITCH)
    # The torso: a deep, keeled chest, a short body tapering back to the hips.
    t.blob(BODY, (15.0 * slim * bulk, 26.0 * bulk, 16.0 * bulk), "hide", axis=(0, 0, 1), side=(1, 0, 0), slices=6)
    t.blob(BODY + np.array([0.0, -10.0, -13.0]), (11.0 * slim * bulk, 13.0 * bulk, 12.0 * bulk), "belly",
           axis=(0, 0, 1), side=(1, 0, 0), slices=3)
    if d.get("plates"):
        for i in range(5):
            z = -14.0 + i * 8.0
            t.blob(BODY + np.array([0.0, 14.0 - abs(i - 2) * 1.5, z]), (10.0 - abs(i - 2) * 1.5, 3.0, 5.0), "plate",
                   slices=1)
    # Spines down its back.
    for i in range(6):
        z = -18.0 + i * 8.0
        y = 15.0 - abs(z) * 0.08
        L = 8.0 - abs(i - 2) * 1.0
        t.cone(BODY + np.array([0.0, y - 2.0, z]), BODY + np.array([0.0, y + L, z + 4.0]), 4, "spike", steps=2)
    # The neck and head.
    neck = [BODY + np.array([0.0, 6.0, -24.0]), BODY + np.array([0.0, 14.0, -38.0]),
            BODY + np.array([0.0, 18.0, -52.0]), BODY + np.array([0.0, 16.0, -62.0])]
    t.tube(neck, [15.0 * slim, 13.0 * slim, 11.0 * slim, 10.0 * slim], "hide")
    hc = neck[-1] + np.array([0.0, 2.0, -12.0])
    head(Scaled(t, head_k) if head_k != 1.0 else t, hc / head_k if head_k != 1.0 else hc, d)
    # The legs, hanging, and their talons.
    for sign in (1.0, -1.0):
        hip = BODY + np.array([sign * 8.0, -6.0, 18.0])
        knee = BODY + np.array([sign * 11.0, -22.0, 12.0])
        ankle = BODY + np.array([sign * 11.0, -36.0, 20.0])
        leg(t, [hip, knee, ankle], [8.0 * slim, 6.0 * slim, 4.0], "hide", joint_scale=1.05)
        for a in (-25.0, 0.0, 25.0, 180.0):
            dd = np.array([math.sin(math.radians(a)) * 0.6, -0.5, -math.cos(math.radians(a))])
            toe = ankle + dd * (7.0 if a != 180.0 else 4.0)
            t.seg(ankle, toe, 2, 2, "hide")
            t.cone(toe, toe + unit(dd + np.array([0.0, -1.0, 0.0])) * 4.0, 2, "claw", steps=1)
    # The tail.
    tail = d.get("tail", "short")
    root = BODY + np.array([0.0, 0.0, 24.0])
    if tail == "long":
        pts = [root, root + np.array([0.0, -4.0, 18.0]), root + np.array([0.0, -6.0, 40.0]),
               root + np.array([0.0, -4.0, 62.0]), root + np.array([0.0, 0.0, 80.0])]
        t.curve(pts, 8, [7, 5, 3, 2, 2], "hide")
        tipv = pts[-1]
        t.plate(tipv + np.array([0.0, 0.0, 6.0]), (1.0, 0.0, 0.0), (0.0, 0.0, 1.0), 14, 14, 1, "vane")
        if d.get("tail_flame"):
            flames(t, tipv + np.array([0.0, 4.0, 6.0]), 14.0, 8.0, "flame", layers=2, twist=90.0)
    else:
        t.cone(root, root + np.array([0.0, -6.0, 22.0]), 8, "hide", steps=3)
        t.cone(root + np.array([0.0, -4.0, 18.0]), root + np.array([0.0, -8.0, 30.0]), 3, "spike", steps=2)
    # The wings.
    for sign in (1.0, -1.0):
        shoulder = t.P(BODY + np.array([sign * 12.0 * slim, 8.0, -14.0]))
        wing(s, sign, shoulder, d, young)
    # Volcanic (look C): two little craters on its back between its wings, lava in them; clusters of basalt columns on
    # its shoulders; lava dripping off its belly.
    if d.get("volcanic"):
        for sign in (1.0, -1.0):
            base = BODY + np.array([sign * 6.0, 14.0, 4.0])
            t.cone(base, base + np.array([sign * 2.0, 12.0, 4.0]), 11, "rock", w1=7.0, steps=3)
            t.blob(base + np.array([sign * 2.0, 12.5, 4.0]), (3.0, 1.0, 3.0), "lava", slices=1)
            flames(t, base + np.array([sign * 2.0, 13.0, 4.0]), 6.0, 3.0, "flame", layers=1)
            shoulder = BODY + np.array([sign * 12.0, 10.0, -12.0])
            for j, (ox, oz, hgt) in enumerate(((0.0, 0.0, 9.0), (3.0, 3.0, 6.0), (-2.0, 3.5, 5.0), (2.5, -3.0, 4.0))):
                p = shoulder + np.array([sign * ox, 0.0, oz])
                t.seg(p - np.array([0.0, 2.0, 0.0]), p + np.array([0.0, hgt, 0.0]), 3, 3, "rock")
        d = dict(d, drips=True)
    # Glowing magma drips hanging off its belly and wings (the magma design).
    if d.get("drips"):
        for k in range(8):
            a = k * 2.4
            p = t.P(BODY + np.array([math.cos(a) * 8.0, -14.0, -6.0 + (k % 4) * 8.0]))
            s.seg(p, p + np.array([0.0, -5.0 - (k % 3) * 2.0, 0.0]), 2, 2, "lava")


CURLED_HORN = 0.55                 # in the egg its horns are this long (soft, folded back)


def build_curled(s, d):
    """
    The young curled up in its egg (the user, October 27, 2026: "make the egg more realistic as well, like a baby thing
    has to fit in the egg"): sitting on its haunches, its back bent over, its head tucked down against its chest (its
    beak down along its belly), its wings folded tight round it like a cloak (the long wing finger folded back up along
    its side), its legs tucked under, its tail coiled round its feet. The same young as `build(young=True)` (its size
    `d["young_span"]`), built round (0, 0, 0); the egg is sized round it.
    """
    k = scale_for(d["young_span"])
    s = Scaled(s, k)
    slim = 0.86
    head_k = 1.42
    body = Xform(s, pivot=(0.0, 0.0, 0.0), axis=(1, 0, 0), deg=-20.0)   # leaning forward over its knees
    body.blob((0.0, 2.0, 0.0), (13.0 * slim, 18.0, 13.0), "hide", axis=(0, 1, 0), side=(1, 0, 0), slices=5)
    body.blob((0.0, -2.0, -9.0), (9.0 * slim, 12.0, 6.0), "belly", axis=(0, 1, 0), side=(1, 0, 0), slices=3)
    for i in range(5):
        y = -10.0 + i * 6.0
        body.cone((0.0, y, 10.0), (0.0, y + 3.0, 16.0), 3, "spike", steps=1)
    # the neck curling forward and down, the head tucked under it, its beak down along its belly
    body.tube([(0.0, 16.0, -4.0), (0.0, 22.0, -12.0), (0.0, 20.0, -20.0), (0.0, 13.0, -24.0)],
              [11.0 * slim, 10.0 * slim, 9.0 * slim, 8.0 * slim], "hide")
    hc = body.P((0.0, 8.0, -27.0))
    hx = Xform(s, pivot=hc, axis=(1, 0, 0), deg=-80.0)
    # its horns still soft in the egg, folded back flat and shorter (they harden once it's out)
    head(Scaled(hx, head_k), hc / head_k, dict(d, horn=d.get("horn", 1.0) * CURLED_HORN))
    for sign in (1.0, -1.0):
        # the wing folded: the arm back and down, the forearm forward, the long finger back up along its side
        bones = [body.P((sign * 10.0 * slim, 12.0, -2.0)), body.P((sign * 15.0, -2.0, 10.0)),
                 body.P((sign * 15.0, -16.0, -8.0)), body.P((sign * 17.0, -6.0, 10.0)),
                 body.P((sign * 16.0, 10.0, 14.0)), body.P((sign * 12.0, 22.0, 6.0))]
        leg(s, bones, [8.0, 7.0, 6.0, 5.0, 4.0, 2.0], "wingbone", joint_scale=1.1)
        s.blob(bones[2], (4.0, 4.0, 4.0), "joint", slices=2)
        for j in range(3):
            f = bones[2] + np.array([sign * (1.0 + j * 1.5), -2.0 + j, -6.0 - j * 1.5])
            s.seg(bones[2], f, 2, 2, "wingbone")
            s.cone(f, f + np.array([0.0, -2.0, -3.0]), 2, "claw", steps=1)
        # its membrane folded round its side like a cloak, and over its back
        for (y, z, w, h), mat in zip(((-6.0, 3.0, 20.0, 18.0), (9.0, 6.0, 18.0, 14.0), (-15.0, -3.0, 14.0, 10.0)),
                                     ("membrane", "membrane", "membraneedge")):
            body.plate((sign * 17.0, y, z), (sign, 0.0, 0.15), (0.0, 1.0, 0.0), w, h, 1, mat)
        body.plate((sign * 9.0, 6.0, 15.0), (sign * 0.4, 0.2, 1.0), (0.0, 1.0, 0.0), 12, 20, 1, "membraneedge")
        # its legs tucked under, talons forward
        hip = body.P((sign * 8.0, -14.0, 4.0))
        knee = body.P((sign * 11.0, -18.0, -10.0))
        ankle = body.P((sign * 9.0, -24.0, -2.0))
        leg(s, [hip, knee, ankle], [7.0 * slim, 5.0 * slim, 4.0], "hide", joint_scale=1.05)
        for a in (-20.0, 20.0):
            dd = np.array([math.sin(math.radians(a)) * 0.6, -0.3, -1.0])
            toe = ankle + dd * 5.0
            s.seg(ankle, toe, 2, 2, "hide")
            s.cone(toe, toe + unit(dd + np.array([0.0, -1.0, 0.0])) * 3.0, 2, "claw", steps=1)
    # its tail coiled round its feet
    s.curve([body.P((0.0, -14.0, 12.0)), body.P((10.0, -22.0, 10.0)), body.P((12.0, -26.0, -4.0)),
             body.P((2.0, -28.0, -14.0)), body.P((-9.0, -26.0, -8.0))], 8, [6.0, 5.0, 4.0, 3.0, 2.0], "hide")


# ---- Round 2 (October 27, 2026): the user's pick, E3 obsidian blade, at Rodan's size ----

# The three looks, each at every size: the user liked E3's "horns" (its two blade crests) and "the little thingy on its
# beak, the half circle thing", and wanted "its body ... much more like the volcano".
LOOKS = (
    dict(letter="A", look="blade", mood="obsidian blade", theme="obsidian",
         design=dict(crest="blade", tail="short", edge_spines=True),
         note="A: the first round's E3, as the user picked it: sleek and glassy, twin blade horns, the little fin on its "
              "beak, a hide of obsidian veined with lava, blades along its wings' leading edges."),
    dict(letter="B", look="moon", mood="half-moon crest", theme="obsidian",
         design=dict(crest="blade", tail="short", edge_spines=True, horn=1.6, beak_fin="moon"),
         note="B: the same, its horns half as long again and the fin on its beak made a half moon standing on the bill, a "
              "glowing seam round its edge."),
    dict(letter="C", look="volcanic", mood="volcanic", theme="volcanic",
         design=dict(crest="blade", tail="short", edge_spines=True, horn=1.6, beak_fin="moon", volcanic=True),
         note="C: B's horns and half moon, its body like its volcano: obsidian crusted like cooled lava with lava running "
              "down its body, wing bones and wings, two little craters smoking on its back, basalt columns on its "
              "shoulders, lava dripping off it."),
)
# Texture detail: a unit gets this many texels at the first round's size, fewer as it grows (so a giant's texture
# stays a sensible size: about the same texels a block as round 1's).
DETAIL = 1.6


def texture_density(span):
    """Texels a unit for a model with this wingspan."""
    return min(1.0, DETAIL / scale_for(span))


def adult_variants(span):
    """The three looks at one wingspan, as a design's VARIANTS."""
    return tuple(dict(look, span=span, density=texture_density(span),
                      note=look["note"] + f" About {span:.0f} blocks across its wings.") for look in LOOKS)
