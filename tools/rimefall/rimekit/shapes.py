"""
Shape helpers for the Rimefall candidates.

Every box goes on one bone (the model's root, pivot 0, 0, 0) at an absolute bind-pose position, in Bedrock model units
(16 = 1 block): y is up, the feet stand on y = 0, the FRONT faces -z and the model's own LEFT is +x (kit/geo.py).
The models never move (no animations), so the rest pose is the pose: boxes are turned freely with GeckoLib's cube
rotations instead of bones. The helpers place a box between two points, taper chains of boxes into limbs, horns,
spikes and tails, stack boxes into round bodies (ellipsoids) and chamfer boxes into rounded blocks.

Box sizes must be whole numbers of at least 1 (box UV); positions and angles can be anything.
"""
import math

import numpy as np

from kit import geo


def v3(*a):
    if len(a) == 1:
        a = a[0]
    return np.array(a, dtype=float)


def unit(d):
    d = np.asarray(d, dtype=float)
    n = np.linalg.norm(d)
    return d / n if n > 1e-9 else np.array([0.0, 1.0, 0.0])


def lerp(a, b, t):
    return np.asarray(a, dtype=float) * (1.0 - t) + np.asarray(b, dtype=float) * t


def size_of(x):
    """A box edge: a whole number of at least 1."""
    return max(1, int(round(float(x))))


def orient(direction, side=None, twist=0.0):
    """
    Bedrock cube rotation (degrees) that turns a box's +y along `direction` (Bedrock vector) and, if `side` is
    given, its x axis as close as possible to `side` (so flat plates face where you want); otherwise the shortest turn
    followed by `twist` degrees about the direction.
    """
    d = np.array([-direction[0], direction[1], direction[2]], dtype=float)   # GeckoLib space flips x
    d /= np.linalg.norm(d)
    if side is None:
        return geo.aim(direction, twist)
    s = np.array([-side[0], side[1], side[2]], dtype=float)
    s = s - np.dot(s, d) * d
    if np.linalg.norm(s) < 1e-6:
        return geo.aim(direction, twist)
    s /= np.linalg.norm(s)
    z = np.cross(s, d)
    R = np.column_stack([s, d, z])
    if twist:
        R = geo.axis_angle(d, math.radians(twist)) @ R
    return tuple(round(x, 4) for x in geo.euler_bedrock(R))


def rotate_point(p, pivot, rotation):
    """Where a bind-pose point ends up after a cube rotation about `pivot` (all Bedrock)."""
    M = geo.translate(*geo.gecko(pivot)) @ geo.gecko_rotation(*rotation) @ geo.translate(*(-geo.gecko(pivot)))
    q = M @ np.append(geo.gecko(p), 1.0)
    return geo.bedrock(q[:3])


class Shape:
    """Adds boxes to one bone of a model at absolute positions."""

    def __init__(self, model, bone="root"):
        self.m = model
        self.bone = model[bone] if bone in model.by_name else model.bone(bone, (0.0, 0.0, 0.0))
        self.count = 0
        self.blocks = []

    # ------------------------------------------------------------ real Minecraft blocks

    def block(self, state, corner, bright=False):
        """
        A real Minecraft block drawn with the model (the mod renders the block itself, with the game's own textures):
        `state` like "minecraft:oak_stairs[facing=east,half=bottom]", `corner` its lowest corner in Bedrock units
        (x at the block's +x side is corner.x + 16). Directions in the state are the model's: north is its front
        (-z), south its back, west its own left (+x), east its own right (-x). `bright`: drawn full-bright (a lit
        lantern, a fire).
        """
        self.blocks.append((str(state), tuple(float(v) for v in corner), bool(bright)))

    # ------------------------------------------------------------ single boxes

    def box(self, center, size, mat, rot=None, pivot=None, tag=None, inflate=0.0):
        """A box centred on `center`, turned by `rot` (Bedrock degrees) about `pivot` (default: its centre)."""
        c = v3(center)
        s = tuple(size_of(x) for x in size)
        origin = tuple(c[i] - s[i] / 2.0 for i in range(3))
        kw = dict(tag=tag, inflate=inflate)
        if rot is not None and any(abs(a) > 1e-9 for a in rot):
            kw["rotation"] = tuple(rot)
            kw["pivot"] = tuple(v3(pivot)) if pivot is not None else tuple(c)
        self.count += 1
        return self.bone.add_abs(origin, s, mat, **kw)

    def seg(self, p0, p1, w, d=None, mat="body", side=None, twist=0.0, ext=0.0, tag=None, inflate=0.0):
        """
        A box running from p0 to p1 (its length rounded to whole units), w wide and d deep across it. `side` points
        its width (x) axis; `ext` lengthens it past both ends (to close joints).
        """
        p0 = v3(p0)
        p1 = v3(p1)
        d = w if d is None else d
        dv = p1 - p0
        L = np.linalg.norm(dv)
        if L < 1e-6:
            return self.box(p0, (w, w, d), mat, tag=tag, inflate=inflate)
        center = (p0 + p1) / 2.0
        rot = orient(dv, side, twist)
        return self.box(center, (w, L + 2.0 * ext, d), mat, rot=rot, tag=tag, inflate=inflate)

    def plate(self, center, normal, up, w, h, t, mat, tag=None, inflate=0.0):
        """A flat plate t thick facing `normal`, w across and h along `up` (which is made square to the normal)."""
        n = unit(normal)
        u = v3(up) - np.dot(v3(up), n) * n
        u = unit(u)
        # The box's y runs along `up`, its z along the normal, its x across.
        side = np.cross(u, n)
        rot = orient(u, side)
        return self.box(center, (w, h, t), mat, rot=rot, tag=tag, inflate=inflate)

    def rbox(self, center, size, mat, c=2, rot=None, pivot=None, tag=None):
        """A box with its edges chamfered by about `c` units: three overlapping boxes (one per axis)."""
        w, h, d = (size_of(x) for x in size)
        cx = min(c, (w - 1) // 2)
        cy = min(c, (h - 1) // 2)
        cz = min(c, (d - 1) // 2)
        pv = pivot if pivot is not None else center
        # The three boxes grow by different hairs (inflate), so their hidden inner faces never share a plane.
        out = [self.box(center, (w, h - 2 * cy, d - 2 * cz), mat, rot=rot, pivot=pv, tag=tag)]
        if cx > 0 or cz > 0:
            out.append(self.box(center, (w - 2 * cx, h, d - 2 * cz), mat, rot=rot, pivot=pv, tag=tag, inflate=0.1))
        if cx > 0 or cy > 0:
            out.append(self.box(center, (w - 2 * cx, h - 2 * cy, d), mat, rot=rot, pivot=pv, tag=tag, inflate=0.2))
        return out

    # ------------------------------------------------------------ chains

    def taper(self, points, widths, mat, depths=None, side=None, joints=True, tag=None, ext=0.5):
        """
        A chain of boxes through `points`, its thickness going from widths[0] to widths[-1] (one width per point).
        Joint blocks round off the bends. Neighbouring boxes alternate a tiny inflate so their faces never z-fight.
        """
        pts = [v3(p) for p in points]
        n = len(pts)
        if depths is None:
            depths = widths
        out = []
        for i in range(n - 1):
            w = (widths[i] + widths[i + 1]) / 2.0
            d = (depths[i] + depths[i + 1]) / 2.0
            sd = side(i) if callable(side) else side
            out.append(self.seg(pts[i], pts[i + 1], w, d, mat, side=sd, ext=ext, tag=tag, inflate=0.1 * (i % 2)))
            if joints and 0 < i + 1 < n - 1:
                jw = widths[i + 1] * 0.92
                jd = depths[i + 1] * 0.92
                a = unit(pts[i + 1] - pts[i])
                b = unit(pts[i + 2] - pts[i + 1])
                bend = math.degrees(math.acos(max(-1.0, min(1.0, float(np.dot(a, b))))))
                if bend > 8.0:
                    mid = unit(a + b)
                    out.append(self.seg(pts[i + 1] - mid * jw * 0.35, pts[i + 1] + mid * jw * 0.35, jw, jd, mat,
                                        side=sd, tag=tag, inflate=0.03))
        return out

    def cone(self, base, tip, w0, mat, w1=1.0, d0=None, d1=None, steps=None, side=None, tag=None):
        """A spike from `base` to `tip`, tapering from w0 to w1 in `steps` boxes (overlapping a little)."""
        base = v3(base)
        tip = v3(tip)
        L = np.linalg.norm(tip - base)
        d0 = w0 if d0 is None else d0
        d1 = w1 if d1 is None else d1
        if steps is None:
            steps = max(1, min(8, int(round(max(w0, d0) - min(w1, d1))) // 2 + 1, int(L // 2) or 1))
        out = []
        for k in range(steps):
            t0 = k / steps
            t1 = (k + 1) / steps
            w = w0 + (w1 - w0) * (t0 + t1) / 2.0
            d = d0 + (d1 - d0) * (t0 + t1) / 2.0
            a = lerp(base, tip, t0)
            b = lerp(base, tip, t1)
            ext = 0.0 if k == steps - 1 else min(1.0, L / steps * 0.25)
            out.append(self.seg(a, b + unit(tip - base) * ext, w, d, mat, side=side, tag=tag,
                                inflate=0.1 * (k % 2)))
        return out

    def tube(self, points, widths, mat, depths=None, side=None, cross=0.62, ext=1.0, tag=None):
        """
        A round(ish) body along a chain of points: each piece is two crossed boxes (an octagon-like cross-section, like
        a blob's slices), overlapping a little; much rounder than taper()'s square chain. `side` fixes the cross-section's
        turn (default: level with the ground).
        """
        pts = [v3(p) for p in points]
        if depths is None:
            depths = widths
        out = []
        for i in range(len(pts) - 1):
            w = (widths[i] + widths[i + 1]) / 2.0
            d = (depths[i] + depths[i + 1]) / 2.0
            dv = pts[i + 1] - pts[i]
            sd = side
            if sd is None:
                sd = np.cross(dv, [0.0, 1.0, 0.0])
                if np.linalg.norm(sd) < 1e-6:
                    sd = np.array([1.0, 0.0, 0.0])
            k = 0.1 * (i % 2)
            out.append(self.seg(pts[i], pts[i + 1], w, d * cross, mat, side=sd, ext=ext, tag=tag, inflate=k))
            out.append(self.seg(pts[i], pts[i + 1], w * cross, d, mat, side=sd, ext=ext, tag=tag, inflate=k + 0.2))
        return out

    def curve(self, control, n, widths, mat, depths=None, side=None, tag=None):
        """A smooth chain along a Catmull-Rom curve through the control points, n boxes long."""
        pts = catmull(control, n + 1)
        ws = np.interp(np.linspace(0, 1, n + 1), np.linspace(0, 1, len(widths)), widths)
        ds = None if depths is None else np.interp(np.linspace(0, 1, n + 1), np.linspace(0, 1, len(depths)), depths)
        return self.taper(pts, list(ws), mat, depths=None if ds is None else list(ds), side=side, tag=tag)

    # ------------------------------------------------------------ round bodies

    def blob(self, center, radii, mat, axis=(0.0, 1.0, 0.0), side=None, slices=None, cross=0.62, tag=None,
             cut_below=None, single=False):
        """
        An ellipsoid with radii (rx, ry, rz) along its own axes (ry along `axis`), stacked from slices; each slice is
        two crossed boxes, so the outline is an octagon rather than a square (`single`: one box per slice, for see-through
        bodies, where every face a ray passes through tints it). cut_below drops the slices whose top is below that
        world height (bodies sunk into the ground or a snowdrift).
        """
        c = v3(center)
        rx, ry, rz = (float(r) for r in radii)
        if slices is None:
            slices = int(max(2, min(16, round(2.0 * ry / 3.2))))
        # Whole-unit slices that touch exactly (no hairline gaps); the stack is as tall as 2 ry, give or take.
        t = float(max(1, int(round(2.0 * ry / slices))))
        slices = max(2, int(round(2.0 * ry / t)))
        ry = slices * t / 2.0
        rot = None
        ax = unit(axis)
        if abs(ax[1] - 1.0) > 1e-6 or side is not None:
            rot = orient(ax, side)
        out = []
        for k in range(slices):
            y0 = -ry + k * t
            y1 = y0 + t
            yc = (y0 + y1) / 2.0
            # The slice's width: the ellipse at the slice's middle, a little fuller (the slice covers its whole band).
            f = math.sqrt(max(0.0, 1.0 - (yc / (ry + t * 0.35)) ** 2))
            # Widen the end slices a little so the caps aren't pin-points.
            f = max(f, 0.32)
            a = rx * f
            b = rz * f
            p = c + np.array([0.0, yc, 0.0])
            if rot is not None:
                p_world_y = rotate_point(p, c, rot)[1]
            else:
                p_world_y = p[1]
            if cut_below is not None and p_world_y + t / 2.0 < cut_below:
                continue
            h = max(1.0, t)
            if single or a < 2.6 or b < 2.6:
                out.append(self.box(p, (2 * a, h, 2 * b), mat, rot=rot, pivot=c, tag=tag))
                continue
            out.append(self.box(p, (2 * a, h, 2 * b * cross), mat, rot=rot, pivot=c, tag=tag))
            out.append(self.box(p, (2 * a * cross, h, 2 * b), mat, rot=rot, pivot=c, tag=tag, inflate=0.1))
        return out

    def ring(self, center, axis, radius, thick, width, mat, n=8, side=None, tag=None, phase=0.0):
        """A ring of n boxes round `axis` (a collar, a band, a hoop): each box `width` long along the ring."""
        c = v3(center)
        a = unit(axis)
        u = unit(np.cross(a, [0.0, 1.0, 0.0] if abs(a[1]) < 0.9 else [1.0, 0.0, 0.0]))
        w = np.cross(a, u)
        seg_len = 2.0 * math.pi * radius / n * 1.08
        out = []
        for i in range(n):
            ang = 2.0 * math.pi * (i + phase) / n
            r = u * math.cos(ang) + w * math.sin(ang)
            t = -u * math.sin(ang) + w * math.cos(ang)
            p = c + r * radius
            out.append(self.seg(p - t * seg_len / 2.0, p + t * seg_len / 2.0, width, thick, mat, side=a, tag=tag,
                                inflate=0.1 * (i % 2)))
        return out


class Scaled:
    """
    A Shape seen through a scale factor: every position, size, width, radius and length handed to it is multiplied by
    `k` (box sizes are still rounded to whole units, at least 1), so one design can be built at two sizes with the
    same texel size (The Thawed at about 2.5 and 4 blocks).
    """

    def __init__(self, shape, k):
        self.s = shape
        self.k = float(k)
        self.blocks = shape.blocks

    def _p(self, p):
        return None if p is None else v3(p) * self.k

    def _l(self, xs):
        return None if xs is None else [x * self.k for x in xs]

    def box(self, center, size, mat, rot=None, pivot=None, tag=None, inflate=0.0):
        return self.s.box(self._p(center), [x * self.k for x in size], mat, rot=rot, pivot=self._p(pivot), tag=tag,
                          inflate=inflate)

    def seg(self, p0, p1, w, d=None, mat="body", side=None, twist=0.0, ext=0.0, tag=None, inflate=0.0):
        return self.s.seg(self._p(p0), self._p(p1), w * self.k, None if d is None else d * self.k, mat, side=side,
                          twist=twist, ext=ext * self.k, tag=tag, inflate=inflate)

    def plate(self, center, normal, up, w, h, t, mat, tag=None, inflate=0.0):
        return self.s.plate(self._p(center), normal, up, w * self.k, h * self.k, t * self.k, mat, tag=tag,
                            inflate=inflate)

    def rbox(self, center, size, mat, c=2, rot=None, pivot=None, tag=None):
        return self.s.rbox(self._p(center), [x * self.k for x in size], mat, c=max(1, int(round(c * self.k))),
                           rot=rot, pivot=self._p(pivot), tag=tag)

    def taper(self, points, widths, mat, depths=None, side=None, joints=True, tag=None, ext=0.5):
        return self.s.taper([self._p(p) for p in points], self._l(widths), mat, depths=self._l(depths), side=side,
                            joints=joints, tag=tag, ext=ext * self.k)

    def cone(self, base, tip, w0, mat, w1=1.0, d0=None, d1=None, steps=None, side=None, tag=None):
        return self.s.cone(self._p(base), self._p(tip), w0 * self.k, mat, w1=max(1.0, w1 * self.k),
                           d0=None if d0 is None else d0 * self.k, d1=None if d1 is None else max(1.0, d1 * self.k),
                           steps=steps, side=side, tag=tag)

    def tube(self, points, widths, mat, depths=None, side=None, cross=0.62, ext=1.0, tag=None):
        return self.s.tube([self._p(p) for p in points], self._l(widths), mat, depths=self._l(depths), side=side,
                           cross=cross, ext=ext * self.k, tag=tag)

    def curve(self, control, n, widths, mat, depths=None, side=None, tag=None):
        return self.s.curve([self._p(p) for p in control], n, self._l(widths), mat, depths=self._l(depths), side=side,
                            tag=tag)

    def blob(self, center, radii, mat, axis=(0.0, 1.0, 0.0), side=None, slices=None, cross=0.62, tag=None,
             cut_below=None, single=False):
        return self.s.blob(self._p(center), [r * self.k for r in radii], mat, axis=axis, side=side, slices=slices,
                           cross=cross, tag=tag, cut_below=None if cut_below is None else cut_below * self.k,
                           single=single)

    def ring(self, center, axis, radius, thick, width, mat, n=8, side=None, tag=None, phase=0.0):
        return self.s.ring(self._p(center), axis, radius * self.k, thick * self.k, width * self.k, mat, n=n, side=side,
                           tag=tag, phase=phase)


def catmull(control, n):
    """n points along a Catmull-Rom spline through the control points (ends included)."""
    P = [v3(p) for p in control]
    if len(P) == 2:
        return [lerp(P[0], P[1], t) for t in np.linspace(0, 1, n)]
    P = [P[0] + (P[0] - P[1])] + P + [P[-1] + (P[-1] - P[-2])]
    # Sample by arc length, roughly: dense samples first.
    dense = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for t in np.linspace(0, 1, 24, endpoint=False):
            t2 = t * t
            t3 = t2 * t
            dense.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2
                                + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    dense.append(P[-2])
    dense = np.array(dense)
    seglen = np.linalg.norm(np.diff(dense, axis=0), axis=1)
    s = np.concatenate([[0.0], np.cumsum(seglen)])
    targets = np.linspace(0, s[-1], n)
    return [np.array([np.interp(tt, s, dense[:, k]) for k in range(3)]) for tt in targets]


def check_seg():
    """Self-test: a seg's two ends land on its points after GeckoLib's cube rotation."""
    m = geo.Model("t")
    s = Shape(m)
    for p0, p1 in (((0, 0, 0), (0, 10, 0)), ((3, 4, -2), (10, 1, 7)), ((0, 5, 0), (0, 5, -12)), ((1, 1, 1), (-9, -3, 4))):
        c = s.seg(p0, p1, 2, 2, "x")
        L = c.size[1]
        mid = v3(c.origin) + v3(c.size) / 2.0
        a = rotate_point(mid - np.array([0, L / 2.0, 0]), c.pivot or c.center(), c.rotation or (0, 0, 0))
        b = rotate_point(mid + np.array([0, L / 2.0, 0]), c.pivot or c.center(), c.rotation or (0, 0, 0))
        direction = unit(v3(p1) - v3(p0))
        if np.dot(unit(b - a), direction) < 0.999:
            raise AssertionError(f"seg {p0}->{p1} points the wrong way: {a} -> {b}")
    return True
