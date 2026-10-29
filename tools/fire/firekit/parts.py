"""
Shape helpers shared by the fire servant candidates, on top of the Rimefall kit's Shape (tools/rimefall/rimekit/
shapes.py: Bedrock units, 16 a block, y up, the feet on y = 0, the FRONT faces -z, the model's own LEFT is +x).

  Xform      a Shape seen through a turn and a shift (a body part built upright and then tilted into place: a
             reared-up head, a raised arm, a toppled bell). Points and directions handed to it are turned.
  leg        a jointed limb through points, its joints as their own boxes (glowing knuckles on a fire spider).
  flames     tongues of see-through flame, each two crossed plates like Minecraft's own fire, leaning and tapering.
  mirror     builds a part on both sides (x and -x).
"""
import math

import numpy as np

from rimekit.shapes import catmull, unit, v3


def rot_matrix(axis, deg):
    a = math.radians(deg)
    k = unit(axis)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * K @ K


class Xform:
    """
    A Shape seen through `R` (3x3, applied about `pivot`) and then a shift: every point is turned about the pivot and
    moved, every direction turned. Xforms nest (pass one as `s`). Boxes made with box()/rbox() become segs (their x
    and y axes turned), so a turned frame never needs GeckoLib's Euler angles worked out by hand.
    """

    def __init__(self, s, pivot=(0, 0, 0), axis=(1, 0, 0), deg=0.0, shift=(0, 0, 0), R=None):
        self.s = s
        self.blocks = getattr(s, "blocks", [])
        self.p = v3(pivot)
        self.R = rot_matrix(axis, deg) if R is None else np.asarray(R, dtype=float)
        self.t = v3(shift)

    def P(self, q):
        return self.p + self.R @ (v3(q) - self.p) + self.t

    def D(self, d):
        return None if d is None else self.R @ v3(d)

    def part(self, name):
        """The same frame over one named part (an animated mob routes each part to its own bone; a statue's Shape
        gives back itself)."""
        inner = self.s.part(name)
        return self if inner is self.s else Xform(inner, pivot=self.p, R=self.R, shift=self.t)

    def seg(self, p0, p1, w, d=None, mat="body", side=None, twist=0.0, ext=0.0, tag=None, inflate=0.0):
        sd = self.D(side) if side is not None else self.D(_default_side(v3(p1) - v3(p0)))
        return self.s.seg(self.P(p0), self.P(p1), w, d, mat, side=sd, twist=twist, ext=ext, tag=tag, inflate=inflate)

    def box(self, center, size, mat, rot=None, pivot=None, tag=None, inflate=0.0):
        if rot is not None and any(abs(a) > 1e-9 for a in rot):
            raise ValueError("Xform.box: turn the frame instead of passing rot")
        c = v3(center)
        h = size[1] / 2.0
        return self.s.seg(self.P(c - [0, h, 0]), self.P(c + [0, h, 0]), size[0], size[2], mat,
                          side=self.D((1.0, 0.0, 0.0)), tag=tag, inflate=inflate)

    def rbox(self, center, size, mat, c=2, rot=None, pivot=None, tag=None):
        w, h, d = (max(1, int(round(x))) for x in size)
        cx = min(c, (w - 1) // 2)
        cy = min(c, (h - 1) // 2)
        cz = min(c, (d - 1) // 2)
        out = [self.box(center, (w, h - 2 * cy, d - 2 * cz), mat, tag=tag)]
        if cx > 0 or cz > 0:
            out.append(self.box(center, (w - 2 * cx, h, d - 2 * cz), mat, tag=tag, inflate=0.1))
        if cx > 0 or cy > 0:
            out.append(self.box(center, (w - 2 * cx, h - 2 * cy, d), mat, tag=tag, inflate=0.2))
        return out

    def plate(self, center, normal, up, w, h, t, mat, tag=None, inflate=0.0):
        return self.s.plate(self.P(center), self.D(normal), self.D(up), w, h, t, mat, tag=tag, inflate=inflate)

    def taper(self, points, widths, mat, depths=None, side=None, joints=True, tag=None, ext=0.5):
        sd = side
        if side is not None and not callable(side):
            sd = self.D(side)
        return self.s.taper([self.P(p) for p in points], widths, mat, depths=depths, side=sd, joints=joints, tag=tag,
                            ext=ext)

    def cone(self, base, tip, w0, mat, w1=1.0, d0=None, d1=None, steps=None, side=None, tag=None):
        return self.s.cone(self.P(base), self.P(tip), w0, mat, w1=w1, d0=d0, d1=d1, steps=steps,
                           side=self.D(side) if side is not None else None, tag=tag)

    def tube(self, points, widths, mat, depths=None, side=None, cross=0.62, ext=1.0, tag=None):
        pts = [v3(p) for p in points]
        sd = side
        if sd is None:
            # The tube's default cross-section is level in the frame, so it turns with it.
            sd = _default_side(pts[-1] - pts[0])
        return self.s.tube([self.P(p) for p in pts], widths, mat, depths=depths, side=self.D(sd), cross=cross,
                           ext=ext, tag=tag)

    def curve(self, control, n, widths, mat, depths=None, side=None, tag=None):
        sd = side
        if side is not None and not callable(side):
            sd = self.D(side)
        return self.s.curve([self.P(c) for c in control], n, widths, mat, depths=depths, side=sd, tag=tag)

    def blob(self, center, radii, mat, axis=(0.0, 1.0, 0.0), side=None, slices=None, cross=0.62, tag=None,
             cut_below=None, single=False):
        sd = (1.0, 0.0, 0.0) if side is None else side
        return self.s.blob(self.P(center), radii, mat, axis=self.D(axis), side=self.D(sd), slices=slices,
                           cross=cross, tag=tag, cut_below=cut_below, single=single)

    def ring(self, center, axis, radius, thick, width, mat, n=8, side=None, tag=None, phase=0.0):
        return self.s.ring(self.P(center), self.D(axis), radius, thick, width, mat, n=n,
                           side=self.D(side) if side is not None else None, tag=tag, phase=phase)


def _default_side(d):
    """Shape.seg's natural side for a direction: level with the ground (x for a vertical box)."""
    sd = np.cross(v3(d), [0.0, 1.0, 0.0])
    if np.linalg.norm(sd) < 1e-6:
        return np.array([1.0, 0.0, 0.0])
    return unit(sd)


def mirror(fn):
    """Calls fn(sign) for sign = 1 (the model's own left, +x) and -1 (its right)."""
    for sign in (1.0, -1.0):
        fn(sign)


def mx(p, sign):
    """A point mirrored to one side: its x multiplied by sign."""
    q = v3(p)
    return np.array([q[0] * sign, q[1], q[2]])


def leg(s, points, widths, mat, joint_mat=None, joint_scale=1.25, depths=None, side=None, ext=0.6):
    """
    A jointed limb through `points` (hip, knee, ..., foot), one box per segment tapering through `widths`; each inner
    joint gets a knuckle box of `joint_mat` (a glowing joint on a fire spider) slightly wider than the limb there.
    """
    pts = [v3(p) for p in points]
    deps = widths if depths is None else depths
    for i in range(len(pts) - 1):
        w = (widths[i] + widths[i + 1]) / 2.0
        d = (deps[i] + deps[i + 1]) / 2.0
        s.seg(pts[i], pts[i + 1], w, d, mat, side=side, ext=ext, inflate=0.1 * (i % 2))
    for i in range(1, len(pts) - 1):
        jm = joint_mat or mat
        a = unit(pts[i] - pts[i - 1])
        b = unit(pts[i + 1] - pts[i])
        mid = unit(a + b)
        jw = widths[i] * joint_scale
        jd = deps[i] * joint_scale
        s.seg(pts[i] - mid * jw * 0.32, pts[i] + mid * jw * 0.32, jw, jd, jm, side=side, inflate=0.05)


def limb(shapes_, points, widths, mat, joint_mat=None, joint_scale=1.25, depths=None, side=None, ext=0.6):
    """
    leg() with each segment on its own Shape (an animated mob's bones: `shapes_[i]` holds the segment from points[i]
    to points[i + 1], and the knuckle at points[i] for i > 0, so a knuckle turns with the bone below it). A statue
    passes the same Shape for every segment and gets exactly leg()'s boxes.
    """
    pts = [v3(p) for p in points]
    deps = widths if depths is None else depths
    for i in range(len(pts) - 1):
        w = (widths[i] + widths[i + 1]) / 2.0
        d = (deps[i] + deps[i + 1]) / 2.0
        shapes_[i].seg(pts[i], pts[i + 1], w, d, mat, side=side, ext=ext, inflate=0.1 * (i % 2))
    for i in range(1, len(pts) - 1):
        jm = joint_mat or mat
        a = unit(pts[i] - pts[i - 1])
        b = unit(pts[i + 1] - pts[i])
        mid = unit(a + b)
        jw = widths[i] * joint_scale
        jd = deps[i] * joint_scale
        shapes_[i].seg(pts[i] - mid * jw * 0.32, pts[i] + mid * jw * 0.32, jw, jd, jm, side=side, inflate=0.05)


def flames(s, base, height, width, mat, lean=(0.0, 0.0, 0.0), layers=3, twist=0.0, thin=1):
    """
    A tongue of flame standing on `base`: `layers` pairs of crossed see-through plates (like Minecraft's fire block),
    each narrower and shorter than the one before, leaning by `lean` (a sideways offset at the top, units) and turned
    `twist` degrees about the vertical. The plates overlap their base by a unit so the flame grows out of what it's on.
    """
    b = v3(base) - np.array([0.0, 1.0, 0.0])
    lean = v3(lean)
    # (A small offset of its own, from where it stands, so neighbouring flames' plates never share a plane.)
    j = (math.sin(b[0] * 12.9898 + b[1] * 78.233 + b[2] * 37.719) * 43758.5453) % 1.0
    a = math.radians(twist + (j - 0.5) * 9.0)
    n1 = np.array([math.cos(a), 0.0, math.sin(a)])
    n2 = np.array([-math.sin(a), 0.0, math.cos(a)])
    for k in range(layers):
        f = 1.0 - k / (layers + 0.6)
        h = height * (0.55 + 0.45 * f) * (1.0 if k == 0 else 0.92) * (0.94 + 0.12 * j) + k * 0.6
        w = max(2.0, width * f)
        top = b + np.array([0.0, h, 0.0]) + lean * (h / height)
        up = unit(top - b)
        off = (k * 0.37 + j * 0.3) - 0.45
        c = (b + top) / 2.0 + n1 * off + n2 * (0.23 * k - 0.2)
        s.plate(c, n1, up, w, h, thin, mat, inflate=0.05 * k)
        s.plate(c + n2 * (0.3 + 0.17 * k) + up * 0.5, n2, up, w, h + 1.0, thin, mat, inflate=0.05 * k + 0.02)


def flame_ring(s, center, radius, count, height, width, mat, seed=0, jitter=0.35, lean_out=0.15, y_of=None):
    """`count` flame tongues round a ring (a burning rim, a brazier's mouth), heights varied by a seeded jitter."""
    c = v3(center)
    for k in range(count):
        ang = 2.0 * math.pi * (k + 0.5 * ((k * 7 + seed) % 3) / 3.0) / count
        r = np.array([math.cos(ang), 0.0, math.sin(ang)])
        j = (((k * 37 + seed * 11) % 17) / 16.0 - 0.5) * 2.0
        h = height * (1.0 + jitter * j)
        p = c + r * radius
        if y_of is not None:
            p[1] = y_of(p)
        flames(s, p, h, width * (1.0 + 0.2 * j), mat, lean=r * h * lean_out, layers=2, twist=math.degrees(ang))


def eye_pair(s, center, spread, size, mat, rim_mat=None, rim=2, forward=(0.0, 0.0, -1.0)):
    """Two eyes `spread` units apart (centre to centre) round `center`, looking along `forward`, each in a dark rim."""
    c = v3(center)
    f = unit(forward)
    side = unit(np.cross([0.0, 1.0, 0.0], f))
    for sign in (1.0, -1.0):
        p = c + side * spread / 2.0 * sign
        if rim_mat:
            s.seg(p - f * 1.0, p + f * 1.0, size + rim, size + rim, rim_mat, side=side)
        s.seg(p - f * 0.5, p + f * 1.6, size, size, mat, side=side)


def arc(p0, p1, bulge, n=8, normal=(0.0, 1.0, 0.0)):
    """n points along a bowed line from p0 to p1, bulging `bulge` units towards `normal` in the middle."""
    a = v3(p0)
    b = v3(p1)
    nn = v3(normal)
    return [a + (b - a) * t + nn * bulge * math.sin(math.pi * t) for t in np.linspace(0, 1, n)]


def spline(points, n):
    return catmull(points, n)
