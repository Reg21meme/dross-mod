"""
Shape helpers for the Blightwood candidates, on top of the Rimefall kit's Shape (Bedrock units, 16 a block, y up, the
feet on y = 0, the FRONT faces -z, the model's own LEFT is +x) and the fire kit's (Xform, leg, mirror).

  mushroom   a stem and a domed cap with gills under it, growing along any direction
  shelf      a bracket fungus sticking out of a surface
  tendril    a thin chain along a curve (mycelium threads, roots, vines)
  quad_leg   a four-legged animal's leg: thigh, shin, foot, toes
"""
import math

import numpy as np

from firekit.parts import leg
from rimekit.shapes import catmull, unit, v3


def perpendicular(d):
    d = unit(d)
    ref = np.array([0.0, 0.0, 1.0]) if abs(d[2]) < 0.9 else np.array([1.0, 0.0, 0.0])
    return unit(np.cross(d, ref))


def mushroom(s, base, height, cap_r, cap_mat="cap", stem_mat="stem", gill_mat="gills", direction=(0, 1, 0),
             stem_w=None, cap_h=None, slices=None, droop=0.0):
    """
    A mushroom growing from `base` along `direction`: a stem `height` long and a domed cap `cap_r` round (`cap_h`
    tall, default a third of its width) with a plate of gills under it. `droop`: the cap's rim hangs down that far
    (a second, wider and flatter slice under the dome). Returns the cap's top point.
    """
    b = v3(base)
    d = unit(direction)
    stem_w = stem_w if stem_w is not None else max(1.0, cap_r * 0.35)
    cap_h = cap_h if cap_h is not None else max(1.5, cap_r * 0.42)
    top = b + d * height
    s.seg(b - d * 1.0, top, stem_w, stem_w, stem_mat)
    centre = top + d * cap_h * 0.55
    sl = slices if slices is not None else max(2, int(round(cap_h * 2.0 / 2.5)))
    s.blob(centre, (cap_r, cap_h, cap_r), cap_mat, axis=d, side=perpendicular(d), slices=sl)
    if droop > 0:
        s.blob(top + d * (cap_h * 0.1 - droop * 0.5), (cap_r * 1.05, max(1.0, droop), cap_r * 1.05), cap_mat, axis=d,
               side=perpendicular(d), slices=1)
    if gill_mat and cap_r >= 2.5:
        g = max(2.0, round(cap_r * 1.7))
        s.plate(top + d * 0.4, d, perpendicular(d), g, g, 1, gill_mat)
    return centre + d * cap_h


def shelf(s, at, out, width, depth, mat="shelf", thick=2.0, tilt=8.0):
    """A bracket fungus: a flat lobe `width` across sticking `depth` out of a surface at `at`, along `out` (level),
    its outer edge tipped up `tilt` degrees."""
    o = v3(out)
    o[1] = 0.0
    o = unit(o)
    up = np.array([0.0, 1.0, 0.0])
    t = math.radians(tilt)
    along = unit(o * math.cos(t) + up * math.sin(t))
    normal = unit(np.cross(np.cross(along, up), along))
    if normal[1] < 0:
        normal = -normal
    c = v3(at) + along * (depth / 2.0 - 1.0)
    s.plate(c, normal, along, width, depth, thick, mat)
    s.plate(c + along * (depth * 0.15) + normal * (thick * 0.6), normal, along, width * 0.7, depth * 0.7, 1, mat,
            inflate=0.05)


def tendril(s, control, n, widths, mat):
    """A thin chain through the control points (a Catmull-Rom curve, n boxes long), its width from widths[0] to
    widths[-1]."""
    return s.curve([v3(p) for p in control], n, widths, mat)


def quad_leg(s, hip, knee, ankle, foot, widths, mat, toe_mat=None, toes=3, toe_len=3.0, toe_w=2.0, sign=1.0):
    """A four-legged animal's leg through hip, knee, ankle and foot, toes splayed forward from the foot."""
    leg(s, [hip, knee, ankle, foot], widths, mat, joint_scale=1.1)
    f = v3(foot)
    for k in range(toes):
        a = math.radians((k - (toes - 1) / 2.0) * 22.0)
        d = np.array([math.sin(a) * sign, -0.15, -math.cos(a)])
        s.seg(f, f + d * toe_len, toe_w, toe_w, toe_mat or mat)


def ring_points(centre, radius, n, phase=0.0, y_of=None):
    """n points round a level ring."""
    c = v3(centre)
    out = []
    for k in range(n):
        a = 2.0 * math.pi * (k + phase) / n
        p = c + np.array([math.cos(a) * radius, 0.0, math.sin(a) * radius])
        if y_of is not None:
            p[1] = y_of(p)
        out.append(p)
    return out


def spline(points, n):
    return catmull([v3(p) for p in points], n)
