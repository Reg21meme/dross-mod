"""
The staff guard: keeps the Rat King's staff out of his ears, head, crown, body and cape, and above the floor, in every
frame of every animation (the user's note on round 1: his ears slipped through the staff, and in his sleep the staff
went through him and the floor).

After an animation has posed a frame, the guard looks at the staff:
  - held in his fist: it tilts the staff about his grip and slides it through his fist (up, so its foot clears the
    floor) by as little as clears everything;
  - lying loose (on the ground while he sleeps, falling when he dies): it lifts it off the floor and pushes it clear.
The correction carries over from frame to frame and eases back to nothing once it isn't needed, so the staff never
jumps. While he burrows, the staff may go underground through the hole (`pose.hole`, the hole's middle).
"""
import math

import numpy as np

from kit import geo

from . import clip as C

CLEARANCE = 0.15          # units the staff keeps clear of everything (it may touch, not sink in)
TILTS = (2.5, 5.0, 8.0, 12.0, 17.0, 23.0, 30.0, 38.0, 47.0, 58.0, 70.0)    # degrees tried about the grip
DIRECTIONS = 16
SLIDES = (0.0, 1.0, 2.0, 3.0, 4.5, 6.0, 8.0, 10.0, 13.0, 16.0)  # units the staff may slide up through his fist
W_TILT, W_SLIDE = 1.0, 0.7        # cost of a correction (per degree, per unit)
W_TILT_CHANGE, W_SLIDE_CHANGE = 1.6, 1.0    # cost of changing it from the last frame (keeps it smooth)
HELD_GAP = 1.5            # units: the staff counts as held while its grip is this close to his fist


def _staff_bones(m):
    return [b for b in m.bones if any(a.name == "staff" for a in b.ancestors())]


class StaffGuard:
    def __init__(self, rig):
        self.rig = rig
        m = rig.m
        self.staff_cubes = [(b.name, c) for b in _staff_bones(m) for c in b.cubes]
        self.obstacles = []
        for b in m.bones:
            g = C._group(b)
            if g in (None, "staff", "grip") or not b.cubes:
                continue
            for c in b.cubes:
                if C._skip_cube(g, c):
                    continue
                self.obstacles.append((b.name, c))
        self.reset()

    def reset(self):
        self.last_cost = 0.0
        self.rot = np.zeros(3)       # held: rotation vector (radians, GeckoLib space) about the grip
        self.slide = 0.0             # held: units up the staff
        self.shift = np.zeros(3)     # loose: translation (blocks, GeckoLib space)

    # ------------------------------------------------------------ geometry

    def _frames(self, mats):
        Ms, lo, hi, tol, ctr, rad = [], [], [], [], [], []
        for bone, c in self.obstacles:
            M, inv, l, h = C._box_frame(mats, c)
            Ms.append(inv)
            lo.append(l)
            hi.append(h)
            half = float(np.min(h - l)) * 16.0 / 2.0
            tol.append(min(CLEARANCE, half * 0.3))
            mid = (l + h) / 2.0
            ctr.append(M[:3, :3] @ mid + M[:3, 3])
            rad.append(float(np.linalg.norm(h - l)) / 2.0)
        return (np.array(Ms), np.array(lo), np.array(hi), np.array(tol), np.array(ctr), np.array(rad))

    def _staff_points(self, mats):
        return np.concatenate([C._grid(C._box_frame(mats, c), spacing=1.2) for _b, c in self.staff_cubes])

    def _cull(self, obs, centre, reach):
        inv, lo, hi, tol, ctr, rad = obs
        keep = np.linalg.norm(ctr - centre, axis=1) < reach + rad + 0.1
        return inv[keep], lo[keep], hi[keep], tol[keep]

    def _cost(self, P, obs, hole):
        inv, lo, hi, tol = obs
        c = 0.0
        if hole is not None:
            # Burrowing: deep under the ground nothing shows, so only what's above it (or just under it) counts.
            P = P[P[:, 1] * 16.0 > -C.SURFACE_BAND]
            if len(P) == 0:
                return 0.0
        if len(inv):
            q = np.einsum("kij,nj->kni", inv[:, :3, :3], P) + inv[:, None, :3, 3]
            d = np.minimum(q - lo[:, None, :], hi[:, None, :] - q).min(axis=2) * 16.0
            over = d.max(axis=1) + tol
            c += float(np.clip(over, 0.0, None).sum())
        y = P[:, 1] * 16.0
        if hole is not None:
            bx = -P[:, 0] * 16.0
            bz = P[:, 2] * 16.0
            near = np.hypot(bx - hole[0], bz - hole[1]) < getattr(self.rig.m, "hole_radius", C.HOLE_RADIUS)
            y = np.where(near | (y < -C.SURFACE_BAND), 1.0, y)
        low = -float(y.min()) + 0.05
        if low > 0.0:
            c += low
        return c

    # ------------------------------------------------------------ the correction

    def apply(self, pose, place):
        """Corrects the staff in this frame's pose (in place). `place(pose, matrix)` puts the staff there."""
        rig = self.rig
        if not rig.has_staff:
            return
        hole = getattr(pose, "hole", None)
        mats = rig.m.pose_matrices(pose.as_geo())
        Ms = mats["staff"]
        piv = np.array([-rig.m["staff"].pivot[0], rig.m["staff"].pivot[1], rig.m["staff"].pivot[2]]) / 16.0
        g = Ms[:3, :3] @ piv + Ms[:3, 3]                       # the grip, GeckoLib space (blocks)
        hand = mats["hand_r"]
        hp = rig.m["hand_r"].pivot
        fist = hand[:3, :3] @ (np.array([-hp[0], hp[1] - 2.0, hp[2]]) / 16.0) + hand[:3, 3]
        held = np.linalg.norm(g - fist) * 16.0 < HELD_GAP
        P0 = self._staff_points(mats)
        reach = float(np.linalg.norm(P0 - g, axis=1).max())
        obs = self._cull(self._frames(mats), g, reach + 0.5)
        u = Ms[:3, :3] @ np.array([0.0, 1.0, 0.0])
        u = u / np.linalg.norm(u)
        if held:
            self.shift = np.zeros(3)
            M = self._held(P0, g, u, obs, hole, Ms)
        else:
            self.rot = np.zeros(3)
            self.slide = 0.0
            M = self._loose(P0, obs, hole, Ms)
        if M is not None:
            place(pose, M)
            mats = rig.m.pose_matrices(pose.as_geo())
            self.last_cost = self._cost(self._staff_points(mats), obs, hole)
        else:
            self.last_cost = self._cost(P0, obs, hole)

    def _held(self, P0, g, u, obs, hole, Ms):
        def moved(rv, s):
            R = _rotation(rv)
            return (R @ (P0 - g).T).T + g + (R @ u) * (s / 16.0)

        def total(rv, s, cost):
            tilt = math.degrees(np.linalg.norm(rv))
            change = math.degrees(np.linalg.norm(rv - self.rot))
            return 1000.0 * cost + W_TILT * tilt + W_SLIDE * s + W_TILT_CHANGE * change + W_SLIDE_CHANGE * abs(s - self.slide)

        base = self._cost(P0, obs, hole)
        prev_rot = self.rot - u * np.dot(self.rot, u)       # only tilts across the shaft
        if base <= 0.0 and np.linalg.norm(prev_rot) < 1e-4 and self.slide < 1e-3:
            self.rot, self.slide = np.zeros(3), 0.0
            return None
        best = None
        # Easing back: the last correction, or less of it, if that's already clear.
        for k in (0.0, 0.35, 0.6, 0.8, 1.0):
            rv, s = prev_rot * k, self.slide * k
            c = base if k == 0.0 else self._cost(moved(rv, s), obs, hole)
            tot = total(rv, s, c)
            if best is None or tot < best[0]:
                best = (tot, rv, s, c)
        if best[3] > 0.0:
            e1 = np.cross(u, [0.0, 1.0, 0.0] if abs(u[1]) < 0.9 else [1.0, 0.0, 0.0])
            e1 /= np.linalg.norm(e1)
            e2 = np.cross(u, e1)
            cands = []
            for i in range(DIRECTIONS):
                a = 2 * math.pi * i / DIRECTIONS
                axis = math.cos(a) * e1 + math.sin(a) * e2
                for th in TILTS:
                    cands.append(axis * math.radians(th))
            for rv0 in [prev_rot] + cands:
                for s in (self.slide, 0.0):
                    c = self._cost(moved(rv0, s), obs, hole)
                    tot = total(rv0, s, c)
                    if tot < best[0]:
                        best = (tot, rv0, s, c)
            # Slides through his fist, with the best few tilts.
            for s in SLIDES:
                c = self._cost(moved(best[1], s), obs, hole)
                tot = total(best[1], s, c)
                if tot < best[0]:
                    best = (tot, best[1], s, c)
            # Fine tune round the best.
            for _round in range(2):
                for dv in (e1, -e1, e2, -e2):
                    rv = best[1] + dv * math.radians(1.5)
                    for s in (best[2], max(0.0, best[2] - 1.0), best[2] + 1.0):
                        c = self._cost(moved(rv, s), obs, hole)
                        tot = total(rv, s, c)
                        if tot < best[0]:
                            best = (tot, rv, s, c)
        _tot, rv, s, _c = best
        self.rot, self.slide = rv, s
        if np.linalg.norm(rv) < 1e-5 and s < 1e-4:
            return None
        R = _rotation(rv)
        M = np.eye(4)
        M[:3, :3] = R @ Ms[:3, :3]
        M[:3, 3] = R @ (Ms[:3, 3] - g) + g + (R @ u) * (s / 16.0)
        return M

    def _loose(self, P0, obs, hole, Ms):
        t = self.shift * 0.8
        for _it in range(12):
            P = P0 + t
            push = self._push(P, obs, hole)
            if push is None:
                break
            t = t + push
        # Ease back as far as stays clear.
        for k in (0.0, 0.25, 0.5, 0.75):
            if self._cost(P0 + t * k, obs, hole) <= 0.0:
                t = t * k
                break
        self.shift = t
        if np.linalg.norm(t) < 1e-6:
            return None
        M = Ms.copy()
        M[:3, 3] = M[:3, 3] + t
        return M

    def _push(self, P, obs, hole):
        """How far to move loose points so they come out of everything (blocks), or None if they're clear."""
        inv, lo, hi, tol = obs
        push = np.zeros(3)
        any_ = False
        if hole is not None:
            P = P[P[:, 1] * 16.0 > -C.SURFACE_BAND]
            if len(P) == 0:
                return None
        if len(inv):
            q = np.einsum("kij,nj->kni", inv[:, :3, :3], P) + inv[:, None, :3, 3]
            dlo = q - lo[:, None, :]
            dhi = hi[:, None, :] - q
            d = np.minimum(dlo, dhi).min(axis=2) * 16.0 + tol[:, None]
            for k in range(len(inv)):
                n = int(np.argmax(d[k]))
                if d[k, n] <= 0.0:
                    continue
                any_ = True
                # Out through the nearest face of that box, in its own frame, turned into the world.
                a = np.concatenate([dlo[k, n], dhi[k, n]])
                f = int(np.argmin(a))
                axis = np.zeros(3)
                axis[f % 3] = -1.0 if f < 3 else 1.0
                Mrot = np.linalg.inv(inv[k][:3, :3])
                w = Mrot @ axis
                w /= np.linalg.norm(w)
                push += w * (d[k, n] + 0.05) / 16.0
        y = P[:, 1] * 16.0
        if hole is not None:
            bx = -P[:, 0] * 16.0
            bz = P[:, 2] * 16.0
            near = np.hypot(bx - hole[0], bz - hole[1]) < getattr(self.rig.m, "hole_radius", C.HOLE_RADIUS)
            y = np.where(near | (y < -C.SURFACE_BAND), 1.0, y)
        low = -float(y.min()) + 0.05
        if low > 0.0:
            any_ = True
            push[1] = max(push[1], low / 16.0)
        return push if any_ else None


def _rotation(rv):
    th = float(np.linalg.norm(rv))
    if th < 1e-9:
        return np.eye(3)
    return geo.axis_angle(rv / th, th)[:3, :3]
