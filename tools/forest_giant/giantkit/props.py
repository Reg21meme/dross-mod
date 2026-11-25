"""
What the giants wear and carry, shared by every look (one people, one kind of gear): belts and kilts of hanging
strips round the pelvis, arm-rings, hanging strands (hair, moss, Stormvine), spears (a shaft of silverwood, a leaf of
dark bronze with a glowing edge, a butt-spike), a round shield with the sign of their people, a boulder, and a broken
spear-shaft for a club.

Points are Bedrock units (16 a block; front -z, the giant's own left +x).
"""
import math

import numpy as np

from rimekit.shapes import catmull, unit, v3

from . import rig


def perp(d, hint=(0.0, 1.0, 0.0)):
    """A unit direction square to `d` (as near `hint` as it can be)."""
    d = unit(d)
    h = v3(hint) - np.dot(v3(hint), d) * d
    if np.linalg.norm(h) < 1e-6:
        h = v3(1.0, 0.0, 0.0) - np.dot(v3(1.0, 0.0, 0.0), d) * d
    return unit(h)


def ellipse(centre, rx, rz, n, phase=0.0):
    c = v3(centre)
    return [c + v3(math.cos(2 * math.pi * (k + phase) / n) * rx, 0.0, math.sin(2 * math.pi * (k + phase) / n) * rz)
            for k in range(n)]


def belt(s, centre, rx, rz, width, thick, mat, n=14, phase=0.0):
    """A band round an ellipse (rx across, rz front to back) at `centre`: n flat boxes, each along its tangent."""
    pts = ellipse(centre, rx, rz, n, phase)
    for k in range(n):
        a, b = pts[k], pts[(k + 1) % n]
        mid = (a + b) / 2.0
        L = float(np.linalg.norm(b - a)) + 1.2
        t = unit(b - a)
        s.seg(mid - t * L / 2.0, mid + t * L / 2.0, width, thick, mat, side=(0.0, 1.0, 0.0), inflate=0.06 * (k % 2))
    return pts


KILT_FLAPS = ("kilt_fl", "kilt_fr", "kilt_bl", "kilt_br")   # front and back, the giant's left half and its right


def kilt_bones():
    """The kilt's four flaps, each on a bone of its own on the body (a look's EXTRA_BONES): front and back, its left
    half and its right. Each half swings with its own leg only, so a knee coming up lifts just the strips over that
    thigh (the user, November 25, 2026: one front flap rose with either knee, the half over the standing leg too)."""
    return tuple((f"kilt_{fb}{side}", v3(sign * 6.0, 34.5, -9.0 if fb == "f" else 9.0), "body")
                 for fb in ("f", "b") for side, sign in (("l", 1.0), ("r", -1.0)))


def kilt_parts(s):
    """The four flaps' parts, in KILT_FLAPS' order, for kilt()."""
    return tuple(s.part(n) for n in KILT_FLAPS)


def kilt(parts, centre, rx, rz, top, length, strips, mat, width=4.0, flare=0.18, plate_mat=None, seed=5):
    """
    A war-kilt of hanging strips (hide, leather) round an ellipse, from `top` down `length` units, each flaring out a
    little; on four parts (kilt_parts: front left, front right, back left, back right, their own bones: each swings
    with its leg) or two (the front half, the back half). Every other strip a little shorter and set a hair further
    out, so neighbours never share a face. `plate_mat`: a little bronze plate riveted near each strip's top.
    """
    rng = np.random.default_rng(seed)
    c = v3(centre)
    for k in range(strips):
        a = 2 * math.pi * (k + 0.5) / strips
        out = v3(math.cos(a), 0.0, math.sin(a))
        p = c + v3(out[0] * rx, top - c[1], out[2] * rz)
        nrm = unit(v3(out[0] * rz, 0.0, out[2] * rx))
        L = length * (0.86 if k % 2 else 1.0) * rng.uniform(0.92, 1.05)
        off = 0.35 * (k % 2)
        tip = p + nrm * (L * flare + off) + v3(0.0, -L, 0.0)
        if len(parts) == 4:
            s = parts[(0 if out[2] < 0.0 else 2) + (0 if out[0] > 0.0 else 1)]
        else:
            s = parts[0] if out[2] < 0.0 else parts[1]
        tang = v3(-nrm[2], 0.0, nrm[0])
        s.seg(tip, p + nrm * off + v3(0.0, 1.0, 0.0), width, 1, mat, side=tang, inflate=0.05 * (k % 3))
        if plate_mat:
            q = p + nrm * (off + 0.7) + v3(0.0, -3.0, 0.0)
            s.seg(q - v3(0.0, 1.5, 0.0), q + v3(0.0, 1.5, 0.0), width - 1.0, 1, plate_mat, side=tang)


def strand(s, root, tip, w, d, mat, side=None, inflate=0.0):
    """A hanging strand (hair, moss, a beard's lock): a box from its tip up to its root, so the shag painter's roots
    (the box's own top) are at the root."""
    return s.seg(v3(tip), v3(root), w, d, mat, side=side, inflate=inflate)


def ring_on(s, a, b, t, radius, thick, width, mat, n=8, phase=0.0):
    """A ring round a limb running from a to b, `t` of the way along (an arm-ring, a band)."""
    p = v3(a) + (v3(b) - v3(a)) * t
    return s.ring(p, unit(v3(b) - v3(a)), radius, thick, width, mat, n=n, phase=phase)


def hand_axis(J, side="r"):
    """(the fist's middle, its grip axis, the forearm's down, the back of the hand's direction)."""
    _w, down, c, g = J.hand(side)
    sign = 1.0 if side == "l" else -1.0
    n = unit(np.cross(down, g))
    if n[0] * sign < 0:
        n = -n
    return c, g, down, n


# ---------------------------------------------------------------- spears


def spear(s, butt, tip, shaft=3.0, head_len=13.0, head_w=5.0, socket_len=4.0, spike_len=5.0, face=None,
          shaft_mat="shaft", head_mat="spearhead", metal_mat="bronze", wrap=None, wrap_mat="grip"):
    """
    A spear from its butt to its tip: a silverwood shaft, a bronze butt-spike, and at the top a socket and a long leaf
    of a blade (flat, facing `face`, its edges glowing: head_mat). `wrap`: (from, to) shares of the shaft wrapped in
    leather where it's held.
    """
    butt = v3(butt)
    tip = v3(tip)
    d = unit(tip - butt)
    L = float(np.linalg.norm(tip - butt))
    flat = perp(d, face if face is not None else (1.0, 0.0, 0.0))
    side = unit(np.cross(d, flat))
    top = tip - d * head_len
    # the butt-spike, the shaft, its bands
    s.cone(butt + d * spike_len, butt, shaft + 1.0, metal_mat, w1=1.0, steps=2, side=flat)
    s.seg(butt + d * (spike_len - 0.5), top - d * socket_len + d * 0.5, shaft, shaft, shaft_mat, side=flat)
    if wrap:
        s.seg(butt + d * (L * wrap[0]), butt + d * (L * wrap[1]), shaft + 0.8, shaft + 0.8, wrap_mat, side=flat)
    s.seg(top - d * socket_len, top + d * 1.0, shaft + 1.0, shaft + 1.0, metal_mat, side=flat)
    # the leaf: widest a third of the way up, a ridge down its middle
    pts = [top, top + d * head_len * 0.3, top + d * head_len * 0.7, tip]
    widths = [head_w * 0.7, head_w, head_w * 0.6, 1.0]
    s.taper(pts, widths, head_mat, depths=[1.0, 1.0, 1.0, 1.0], side=side, joints=False)
    s.seg(top, top + d * head_len * 0.85, 1.6, 2.0, metal_mat, side=side, inflate=0.1)
    return d, flat


def broken_shaft(s, J, length=30.0, shaft=5.0, mat="shaft", band_mat="bronze", spike_mat="bronze", splinter="shaft",
                 wrap_mat="grip", seed=7):
    """
    The old warrior's club: the bottom of a great spear's shaft, broken off: held at its butt (a bronze cap at its
    end in his fist, a leather wrap, a band further along), the shaft thickening a little with age, and its far end a jagged
    break of long splinters.
    """
    c, g, _down, n = hand_axis(J)
    rng = np.random.default_rng(seed)
    side = unit(np.cross(g, n))
    butt = c - g * 1.6                                   # (held right at its butt, its cap inside the fist)
    end = c + g * length
    s.seg(butt - g * 0.6, butt + g * 2.0, shaft, shaft, band_mat, side=n, inflate=0.3)     # its bronze butt cap
    s.seg(butt + g * 2.0, c + g * (rig.FIST / 2.0 + 0.6), shaft + 0.4, shaft + 0.4, wrap_mat, side=n)
    s.seg(c + g * (rig.FIST / 2.0), end, shaft, shaft, mat, side=n)
    s.seg(c + g * (length * 0.55), c + g * (length * 0.55 + 2.0), shaft + 1.2, shaft + 1.2, band_mat, side=n)
    # the break: splinters of differing lengths out of the end, leaning out a little
    for k in range(6):
        a = 2 * math.pi * k / 6 + rng.uniform(-0.3, 0.3)
        off = (n * math.cos(a) + side * math.sin(a)) * (shaft / 2.0 - 1.0)
        L = rng.uniform(2.5, 6.5)
        base = end - g * 1.0 + off
        s.cone(base, base + g * L + off * 0.25, 2.2, splinter, w1=1.0, steps=2, side=n)
    return butt, end


# ---------------------------------------------------------------- a round shield


def shield(s, centre, normal, radius=11.0, thick=2.0, up=(0.0, 1.0, 0.0), face_mat="shieldface", rim_mat="bronze",
           back_mat="shieldback", band_mat="grip", arm=None):
    """
    A round shield (a hoplon): a dished face of crossed boxes (an octagon, `face_mat`: the sign of their people glows
    on it), a bronze rim round it, a wooden back with a band for the forearm across its middle (`arm`: (from, to) the
    forearm's points the band goes round).
    """
    c = v3(centre)
    nrm = unit(normal)
    upv = perp(nrm, up)
    side = unit(np.cross(upv, nrm))
    s.blob(c, (radius, thick / 2.0 + 0.5, radius), face_mat, axis=nrm, side=side, slices=1)
    s.blob(c - nrm * thick, (radius - 1.0, thick / 2.0, radius - 1.0), back_mat, axis=nrm, side=side, slices=1)
    s.ring(c + nrm * 0.2, nrm, radius - 0.2, 2.6, 2.4, rim_mat, n=12)
    s.blob(c + nrm * (thick / 2.0 + 0.6), (2.5, 0.8, 2.5), rim_mat, axis=nrm, side=side, slices=1)   # its boss
    if arm is not None:
        a, b = (v3(p) for p in arm)
        mid = (a + b) / 2.0
        s.seg(mid - nrm * 3.0, c - nrm * thick, 3, 2, band_mat, side=unit(b - a))
    return upv, side


# ---------------------------------------------------------------- a boulder


def boulder(s, centre, r=7.5, mat="boulder", moss_mat="moss", seed=11):
    """A rough boulder of the Dross's stone, lumpy (three chamfered blocks at odd turns), moss on its top."""
    c = v3(centre)
    rng = np.random.default_rng(seed)
    sizes = ((2 * r, 2 * r - 3, 2 * r - 2), (2 * r - 3, 2 * r, 2 * r - 4), (2 * r - 4, 2 * r - 2, 2 * r))
    for k, size in enumerate(sizes):
        d = unit(v3(rng.uniform(-0.4, 0.4), 1.0, rng.uniform(-0.4, 0.4)))
        side = perp(d, v3(math.cos(k * 1.1), 0.0, math.sin(k * 1.1)))
        h = size[1] / 2.0
        s.seg(c - d * h, c + d * h, size[0], size[2], mat, side=side, inflate=0.12 * k)
    s.blob(c + v3(0.6, r - 1.4, -0.4), (r * 0.62, 1.8, r * 0.55), moss_mat, slices=1)
    return c


ROCK_R = 7.0            # the rock a giant without a boulder tears out of the ground to throw (radius, units)
ROCK_GRIP = 2.5         # how far its fist sinks into the rock's top


def rock_in_fist(J, side="l", r=ROCK_R):
    """Where the middle of a rock held in a fist goes: beyond the fist along the forearm (the fingers over its top), so
    with the arm hanging it hangs under the fist, and thrown overhand it leaves from in front of the fingers. The
    looks that carry no boulder (G2, G3) have one there on its own bone (`rock`, on the hand), hidden in every
    animation but the throw (tools/forest_giant/mobs.py) and in the statue previews."""
    _w, down, c, _g = J.hand(side)
    return c + down * (rig.FIST / 2.0 + r - ROCK_GRIP)


# ---------------------------------------------------------------- the forest growing over them


def vine(s, control, n=8, width=1.4, leaf_every=2, leaf=2.0, mat="vine", leaf_mat="vineleaf", seed=13):
    """A strand of Stormvine along a curve through `control`, n pieces long, a glowing leaf every `leaf_every` pieces,
    on alternate sides, rooted in the stem."""
    rng = np.random.default_rng(seed)
    pts = catmull([v3(p) for p in control], n + 1)
    s.taper(pts, [width] * (n + 1), mat, joints=False)
    for k in range(1, n, leaf_every):
        a, b = pts[k], pts[k + 1]
        d = unit(b - a)
        side = perp(d, v3(rng.uniform(-1, 1), 0.0, rng.uniform(-1, 1)))
        sgn = 1.0 if (k // leaf_every) % 2 else -1.0
        base = a + side * sgn * (width / 2.0)
        s.seg(base - side * sgn * 0.6, base + side * sgn * leaf, leaf, 1, leaf_mat, side=d)
    return pts


def bark_plate(s, centre, normal, up, w, h, t=2.0, mat="bark", seed=17):
    """A plate of silverwood bark grown over something: a flat chamfered box facing `normal`, with a ragged second
    layer on top of it, a little smaller and askew."""
    rng = np.random.default_rng(seed)
    s.plate(centre, normal, up, w, h, t, mat)
    off = perp(normal, up) * rng.uniform(-1.0, 1.0)
    s.plate(v3(centre) + unit(normal) * (t * 0.6) + off, normal, unit(v3(up)) + perp(normal, (1.0, 0.0, 0.0)) * 0.15,
            max(2.0, w - 3.0), max(2.0, h - 4.0), t, mat, inflate=0.1)
