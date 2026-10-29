"""No. 15 Lanternwing: a giant moth drawn to light, its wings of ember scales with glowing eyespots."""
import math

import numpy as np

from firekit import fmats as F
from firekit.parts import Xform, leg
from rimekit.shapes import unit

NUMBER = 15
ID = "moth"
NAME = "Lanternwing"
BLURB = ("A giant moth that hunts light: in its patch every torch, lantern or glowstone you put down fills a hidden "
         "meter, and when there's too much light it comes out of the dark. Its wings are ember scales, ash grey and "
         "burnt orange, with two great glowing eyespots that look like a face; its body is thick with ash fur, its "
         "antennae feathery. It dives at the brightest light near you (your own torches become bait), sheds burning "
         "scales and throws dust that blinds. Put your lights out to lose it. About 10 blocks across its wings.")
VARIANTS = (
    dict(letter="A", mood="wings spread", look="spread", note="A: the original: its wings raised, the eyespots glaring."),
    dict(letter="B", mood="wings folded", look="folded",
         note="B: resting, its wings folded over its back like a roof, the eyespots looking out from their sides."),
    dict(letter="C", mood="glass-winged", look="glass",
         note="C: a clearwing: see-through wings of smoky glass with glowing veins and dark rims; a banded body."),
    dict(letter="D", mood="lantern belly", look="lantern",
         note="D: its abdomen a lantern, glowing through a cage of chitin; darker wings, smaller eyespots."),
)
FREE = ()

THORAX = np.array([0.0, 38.0, 4.0])


def wing(s, root, span_dir, chord_dir, segments, mat):
    """
    A wing of flat plates laid edge to edge from its root: `segments` = [(length, width, chord_shift)], each plate
    `width` across (along chord_dir) and `length` along the span, its middle shifted along the chord. Returns the
    points along the wing's middle (for placing eyespots).
    """
    span = unit(span_dir)
    chord = unit(chord_dir - np.dot(chord_dir, span) * span)
    normal = unit(np.cross(span, chord))
    p = np.asarray(root, dtype=float)
    mids = []
    for k, (L, w, shift) in enumerate(segments):
        c = p + span * L / 2.0 + chord * shift
        s.plate(c + normal * (0.15 * (k % 2)), normal, chord, L + 1.0, w, 1, mat)
        mids.append(c)
        p = p + span * L
    return mids, normal


def body(s, look):
    """Thorax, head, eyes, antennae, legs, the abdomen; returns the wings' roots."""
    s.blob(THORAX, (12.0, 13.0, 13.0), "fur", slices=4)
    s.blob(THORAX + [0.0, 7.0, -6.0], (12.0, 5.0, 8.0), "furpale", slices=2)            # the collar of pale fur
    head = THORAX + np.array([0.0, 2.0, -16.0])
    s.blob(head, (8.0, 8.0, 7.0), "fur", slices=3)
    for sign in (1.0, -1.0):
        s.blob(head + [sign * 6.5, 1.0, -2.0], (4.5, 5.5, 5.0), "eye", slices=2)
        # A feathery antenna: a shaft curving up and forward, combs of barbs along it.
        a0 = head + [sign * 3.0, 6.0, -4.0]
        pts = [a0, a0 + [sign * 4.0, 10.0, -6.0], a0 + [sign * 10.0, 18.0, -6.0], a0 + [sign * 16.0, 22.0, -2.0]]
        s.curve(pts, 5, [2, 2, 1, 1], "antenna")
        for k in range(6):
            t = 0.15 + k * 0.14
            i = min(2, int(t * 3))
            f = t * 3 - i
            q = pts[i] + (pts[i + 1] - pts[i]) * f
            for side in (1.0, -1.0):
                s.seg(q, q + np.array([sign * 2.0 * side, 1.5, -2.5 * side]) * (1.2 - t * 0.7), 1, 1, "antenna")
    # The proboscis, coiled under its head.
    s.ring(head + [0.0, -7.0, -3.0], (1.0, 0.0, 0.0), 2.5, 1, 1, "antenna", n=6)
    # The abdomen: segmented, tapering back and down, furry bands (or a lantern).
    ab = [THORAX + [0.0, -4.0, 10.0], THORAX + [0.0, -10.0, 24.0], THORAX + [0.0, -18.0, 38.0],
          THORAX + [0.0, -26.0, 50.0]]
    if look == "lantern":
        s.blob(ab[1] + [0.0, -2.0, 4.0], (9.0, 9.0, 13.0), "lamp", slices=4)
        for k in range(6):
            a = 2.0 * math.pi * k / 6
            pts = [ab[0] + [math.cos(a) * 8.0, math.sin(a) * 8.0, 0.0],
                   ab[1] + [math.cos(a) * 12.0, math.sin(a) * 12.0 - 2.0, 4.0],
                   ab[2] + [math.cos(a) * 9.0, math.sin(a) * 9.0, 0.0]]
            s.curve(pts, 4, [2, 2, 2], "fur")
        s.tube([ab[2], ab[3]], [12.0, 5.0], "fur")
    else:
        widths = [20.0, 22.0, 16.0, 6.0]
        for k in range(3):
            s.tube([ab[k], ab[k + 1]], [widths[k], widths[k + 1]], "fur" if look != "glass" else
                   ("band" if k % 2 == 0 else "fur"))
            s.ring(ab[k + 1], ab[k + 1] - ab[k], widths[k + 1] * 0.48, 1, 2, "furpale", n=10)
    # Six legs: thin, jointed, the front pair reaching forward.
    for sign in (1.0, -1.0):
        for k, (hz, fx, fz) in enumerate(((-6.0, 22.0, -34.0), (2.0, 30.0, -4.0), (10.0, 28.0, 26.0))):
            hip = THORAX + [sign * 8.0, -9.0, hz]
            knee = np.array([sign * fx * 0.7, 30.0, (hz + fz) / 2.0])
            foot = np.array([sign * fx, 1.0, fz])
            leg(s, [hip, knee, foot, foot + unit(foot - knee) * 4.0], [3.0, 2.5, 2.0, 1.0], "leg", joint_scale=1.1)
    return THORAX + np.array([8.0, 8.0, 0.0]), THORAX + np.array([8.0, 2.0, 6.0])


def build(s, v):
    look = v["look"]
    froot, hroot = body(s, look)
    wm = {"spread": "wing", "folded": "wing", "glass": "glasswing", "lantern": "darkwing"}[look]
    for sign in (1.0, -1.0):
        fr = froot * np.array([sign, 1.0, 1.0])
        hr = hroot * np.array([sign, 1.0, 1.0])
        if look == "folded":
            # Folded over its back like a roof: each wing sloping down from the ridge over its back.
            span = np.array([sign * 0.55, -0.35, 1.0])
            chord = np.array([sign * 1.0, -0.9, 0.0])
            wing(s, fr + [0.0, 6.0, -6.0], span, chord, [(18, 30, 4), (18, 34, 6), (18, 30, 6), (14, 22, 4)], wm)
            continue
        # Forewing: raised and out in a broad V, its face towards the front.
        span = np.array([sign * 1.0, 0.75, -0.08])
        chord = np.array([sign * 0.35, -1.0, 0.25])
        wing(s, fr, span, chord, [(20, 30, 4), (22, 38, 8), (22, 36, 9), (18, 26, 6), (10, 14, 3)], wm)
        # Hindwing: lower and rounder, out to the side.
        span = np.array([sign * 1.0, -0.15, 0.3])
        chord = np.array([0.0, -1.0, 0.35])
        wing(s, hr, span, chord, [(16, 24, 6), (18, 30, 9), (16, 26, 8), (10, 14, 4)], wm + "hind")


def spots(look):
    """Where the eyespots sit (world units, both wings): the forewings' middles (found by building the wings)."""
    from rimekit import shapes
    from kit import geo
    out = []
    for sign in (1.0, -1.0):
        fr = np.array([8.0 * sign, 46.0, 4.0])
        if look == "folded":
            span = np.array([sign * 0.55, -0.35, 1.0])
            chord = np.array([sign * 1.0, -0.9, 0.0])
            m, n = wing(_Null(), fr + [0.0, 6.0, -6.0], span, chord, [(18, 30, 4), (18, 34, 6), (18, 30, 6),
                                                                       (14, 22, 4)], "x")
            out.append((*m[1], 9.0))
        else:
            span = np.array([sign * 1.0, 0.75, -0.08])
            chord = np.array([sign * 0.35, -1.0, 0.25])
            m, n = wing(_Null(), fr, span, chord, [(20, 30, 4), (22, 38, 8), (22, 36, 9), (18, 26, 6), (10, 14, 3)],
                        "x")
            r = 8.0 if look == "lantern" else 13.0
            out.append((*(m[1] * 0.55 + m[2] * 0.45), r))
    return out


class _Null:
    def plate(self, *a, **k):
        pass


def materials(v):
    look = v["look"]
    sp = spots(look)
    band = F.palette("#5a2a10", "#7a3a16", "#9c4c1c", "#c06424", "#de8430")
    return {
        "fur": F.fur(F.MOTH, seed=1511, strand=2.0, outline=0.08),
        "furpale": F.fur(F.palette("#4e463e", "#635a50", "#786d62", "#8e8276", "#a3968a"), seed=1513, strand=2.0, outline=0.06),
        "band": F.fur(band, seed=1515, strand=2.0),
        "eye": F.chitin(F.CHAR, seed=1517, gloss=2.0, hairs=0.04, hair_pal=F.EMBER),
        "antenna": F.solid(F.MOTH, seed=1519, outline=0.0),
        "leg": F.chitin(F.MOTH, seed=1521, gloss=1.0),
        "wing": F.wings(F.MOTH, seed=1523, spots=sp, band_pal=F.palette("#5a2408", "#7c330c", "#a04612", "#c45c1a",
                                                                         "#e07a26"), bands=0.35),
        "winghind": F.wings(F.palette("#5a2408", "#7c330c", "#a04612", "#c45c1a", "#e07a26"), seed=1525,
                            band_pal=F.MOTH, bands=0.3),
        "darkwing": F.wings(F.CHAR, seed=1527, spots=sp, band_pal=F.MOTH, bands=0.25),
        "darkwinghind": F.wings(F.CHAR, seed=1529, band_pal=F.palette("#5a2408", "#7c330c", "#a04612", "#c45c1a"),
                                bands=0.2),
        "glasswing": F.glass(F.GLASS_SMOKE, alpha=(50, 110), seed=1531, cracks=0.45, glow_pal=F.EMBER, glow_alpha=30.0,
                             crack_glow=0.9, rim=1.4),
        "glasswinghind": F.glass(F.GLASS_SMOKE, alpha=(50, 110), seed=1533, cracks=0.45, glow_pal=F.EMBER,
                                 glow_alpha=30.0, crack_glow=0.9, rim=1.4),
        "lamp": F.lava(F.palette("#a8400a", "#d8661a", "#f49030", "#ffb450", "#ffd27c", "#ffeab8"), seed=1535),
    }
