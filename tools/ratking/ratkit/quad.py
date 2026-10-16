"""
The rats: a small four-legged rat skeleton and its parts. Regular rats and enraged rats (a separate, bigger, scarier
breed) share it; each design sets its proportions and extras.

Bones (bind pose: standing on all fours, every leg hanging straight down; front is -z, his left +x):

    root
     └ body                    moves the whole rat (bobs, rears up, lunges); pivot at the ground under his middle
        ├ rear                 hindquarters (pivot mid-back): the rump, the hind legs and the tail
        │  ├ hind_l/r > hfoot_l/r
        │  └ tail_0 .. tail_N
        └ fore                 chest and shoulders (pivot mid-back): the front legs and the head
           ├ front_l/r > paw_l/r
           ├ head > jaw, ear_l/r
           └ bristle_0 .. bristle_N  (enraged rats: tufts of fur standing up along the spine; more on `rear`)

The two halves pivot at the middle of the back, so the spine can flex in the gallop.
"""
import numpy as np

from kit import geo, ik

from . import rig as R

SIDES = R.SIDES


class RatProportions:
    def __init__(self, **kw):
        self.back_y = 5.0          # spine height at the middle of the back
        self.rear = (5, 4, 5)      # hindquarters box (w, h, d), behind the middle
        self.fore = (4, 4, 4)      # chest box, in front of the middle
        self.skull = (4, 3, 3)
        self.snout = (3, 2, 2)
        self.ear = (2, 2)
        self.ear_tilt = 25.0
        self.eye = 1
        self.leg = 1               # leg thickness
        self.front_len = 3.0       # shoulder to wrist
        self.hind_len = 3.0        # hip to ankle
        self.hind_x = 2.0
        self.front_x = 1.5
        self.foot = 3              # hind foot length
        self.tail = [(3, 2), (3, 1), (3, 1), (3, 1), (3, 1)]
        self.tail_dirs = None
        self.whisker = 3
        self.fang = 1              # tooth length
        self.bristles = []         # [(bone, z, height, rake)]: tufts along the spine
        self.__dict__.update(kw)


def skeleton(m, q):
    B = {}
    root = m.bone("root", (0, 0, 0))
    body = m.bone("body", (0, 0, 0), parent=root)
    y = q.back_y
    rear = m.bone("rear", (0, y, 0), parent=body)
    fore = m.bone("fore", (0, y, 0), parent=body)
    B.update(root=root, body=body, rear=rear, fore=fore)
    rw, rh, rd = q.rear
    fw, fh, fd = q.fore
    for side, sx in SIDES:
        hy = y - rh + 1.0
        hz = rd - 2.0
        hind = m.bone(f"hind_{side}", (sx * q.hind_x, hy, hz), parent=rear)
        hfoot = m.bone(f"hfoot_{side}", (sx * q.hind_x, hy - q.hind_len, hz), parent=hind)
        fy = y - fh + 1.0
        fz = -fd + 1.5
        front = m.bone(f"front_{side}", (sx * q.front_x, fy, fz), parent=fore)
        paw = m.bone(f"paw_{side}", (sx * q.front_x, fy - q.front_len, fz), parent=front)
        B.update({f"hind_{side}": hind, f"hfoot_{side}": hfoot, f"front_{side}": front, f"paw_{side}": paw})
    head = m.bone("head", (0, y - 1.0, -fd), parent=fore)
    B["head"] = head
    ty = y - 1.0
    parent = rear
    yy = ty
    for i, (length, _t) in enumerate(q.tail):
        seg = m.bone(f"tail_{i}", (0, yy, rd), parent=parent)
        B[f"tail_{i}"] = seg
        parent = seg
        yy -= length
    return B


def tail_names(q):
    return [f"tail_{i}" for i in range(len(q.tail))]


def body_parts(m, B, q, look):
    rw, rh, rd = q.rear
    fw, fh, fd = q.fore
    y = q.back_y
    B["rear"].add((-rw / 2.0, -rh + 0.5, 0.0), (rw, rh, rd), "fur", tag="rear")
    B["rear"].add((-rw / 2.0 + 0.5, -rh + 1.0, rd - 0.5), (rw - 1, rh - 1, 1), "fur", tag="rump")
    B["fore"].add((-fw / 2.0, -fh + 0.5, -fd), (fw, fh, fd), "fur", tag="fore")
    lg = q.leg
    for side, sx in SIDES:
        B[f"hind_{side}"].add((-lg / 2.0 - 0.5, -q.hind_len, -1.5), (lg + 1, int(q.hind_len) + 1, 3), "fur", tag="haunch")
        B[f"hfoot_{side}"].add((-lg / 2.0, -1.0, -q.foot + 1.0), (lg, 1, q.foot), "skin", tag="foot")
        B[f"front_{side}"].add((-lg / 2.0, -q.front_len, -lg / 2.0), (lg, int(q.front_len), lg), "fur", tag="leg")
        B[f"paw_{side}"].add((-lg / 2.0, -1.0, -lg / 2.0 - 1.0), (lg, 1, lg + 1), "skin", tag="paw")
    h = B["head"]
    sw, sh, sd = q.skull
    h.add((-sw / 2.0, -1.0, -sd), (sw, sh, sd), "fur", tag="skull")
    nw, nh, nd = q.snout
    h.add((-nw / 2.0, -1.0, -sd - nd), (nw, nh, nd), "fur", tag="muzzle")
    h.add((-0.5 if nw % 2 else -1.0, -1.0 + nh - 1.0, -sd - nd - 1.0), (1 if nw % 2 else 2, 1, 1), "nose", tag="nose")
    # Incisors just under the tip of the nose (enraged rats: two fangs with a gap), the jaw tucked in behind them.
    xs = (-0.5,) if (nw % 2 and q.fang < 2) else ((-1.5, 0.5) if q.fang >= 2 else (-1.0, 0.0))
    for x in xs:
        h.add((x, -1.0 - q.fang, -sd - nd + 0.25), (1, q.fang, 1), "tooth", tag="tooth")
    jaw = m.bone("jaw", (h.pivot[0], h.pivot[1] - 1.0, h.pivot[2] - sd + 0.25), parent=h)
    jw = max(1, nw - 1)
    jd = max(1, nd - 1)
    jaw.add((-jw / 2.0, -1.0, -jd), (jw, 1, jd), "fur", tag="jaw")
    B["jaw"] = jaw
    e = q.eye
    for side, sx in SIDES:
        ex0 = sw / 2.0 - e + 0.5 if sx > 0 else -sw / 2.0 - 0.5
        h.add((ex0, -1.0 + sh - 1.0 - e + 0.5, -sd - 0.5), (e, e, e), "eye", tag=f"eye_{side}")
    ew, eh = q.ear
    for side, sx in SIDES:
        piv = (h.pivot[0] + sx * (sw / 2.0 - 0.5), h.pivot[1] - 1.0 + sh - 0.5, h.pivot[2] - sd / 2.0)
        ear = m.bone(f"ear_{side}", piv, (-10.0, -sx * 20.0, sx * q.ear_tilt), parent=h)
        x0 = -0.5 if sx > 0 else -(ew - 0.5)
        ear.add((x0, 0.0, -0.5), (ew, eh, 1), "ear", tag=f"ear_{side}")
        B[f"ear_{side}"] = ear
    wl = q.whisker
    for side, sx in SIDES:
        for k, (ay, az) in enumerate(((-12.0, 10.0), (14.0, -10.0))):
            base = (sx * nw / 2.0, -0.5, -sd - nd + 0.8 + 0.8 * k)
            org = (base[0] if sx > 0 else base[0] - wl, base[1] - 0.5, base[2] - 0.5)
            h.add(org, (wl, 1, 1), look.get("whisker_mat", "whisker"), rotation=(0.0, sx * ay, sx * az),
                  pivot=tuple(h.pivot[i] + base[i] for i in range(3)), tag="whisker")
    for i, (length, t) in enumerate(q.tail):
        last = i == len(q.tail) - 1
        B[f"tail_{i}"].add((-t / 2.0, -length - (0.0 if last else 0.5), -t / 2.0), (t, length + (0 if last else 1), t),
                            "tail", tag="tail", inflate=0.06 * (i % 2))
    for i, (bone, z, hgt, rake) in enumerate(q.bristles):
        parent = B[bone]
        top = parent.pivot[1] + 0.5
        br = m.bone(f"bristle_{i}", (0.0, top - 0.5, z), (-rake, 0.0, 0.0), parent=parent)
        br.add((-0.5, -0.5, -0.5), (1, int(hgt) + 1, 1), "bristle", tag="bristle")
        B[f"bristle_{i}"] = br


def leg_limbs(m, q):
    out = {}
    for side, _sx in SIDES:
        hf = m[f"hfoot_{side}"].pivot
        out[f"hind_{side}"] = ik.Limb([
            ik.Joint(f"hind_{side}", (0, 1, 2), lo=(-100, -30, -30), hi=(70, 30, 30), prefer=(None, 0, 0), weight=0.03),
            ik.Joint(f"hfoot_{side}", (0, 2), lo=(-80, -20), hi=(100, 20), prefer=(None, 0), weight=0.01),
        ], f"hfoot_{side}", contact=(hf[0], hf[1] - 1.0, hf[2] - (q.foot - 1) / 2.0), down=(0, -1, 0), forward=(0, 0, -1))
        pw = m[f"paw_{side}"].pivot
        out[f"front_{side}"] = ik.Limb([
            ik.Joint(f"front_{side}", (0, 1, 2), lo=(-110, -30, -30), hi=(80, 30, 30), prefer=(None, 0, 0), weight=0.03),
            ik.Joint(f"paw_{side}", (0, 2), lo=(-90, -20), hi=(90, 20), prefer=(None, 0), weight=0.01),
        ], f"paw_{side}", contact=(pw[0], pw[1] - 1.0, pw[2] - 0.5), down=(0, -1, 0), forward=(0, 0, -1))
    return out


def rest_targets(m, q):
    """Where each paw stands in the rest pose: under its shoulder or hip."""
    out = {}
    for name, limb in leg_limbs(m, q).items():
        c = limb.contact
        out[name] = (np.array([c[0], 0.0, c[2]]), np.array([0.0, 0.0, -1.0]))
    return out


def pose_rest(m, q, crouch=0.0, arch=0.0, head_pitch=0.0):
    """Bakes the stance: a little crouch (legs bent), the back's arch, the head; tail aimed along tail_dirs."""
    m["rear"].rotation = (-arch, 0.0, 0.0)
    m["fore"].rotation = (arch, 0.0, 0.0)
    m["head"].rotation = (head_pitch - arch, 0.0, 0.0)
    lims = leg_limbs(m, q)
    pose = {}
    for name, (target, heading) in rest_targets(m, q).items():
        side = name[-1]
        upper, lower = (f"hind_{side}", f"hfoot_{side}") if name.startswith("hind") else (f"front_{side}", f"paw_{side}")
        # Start from a few sensible bends (hind legs fold forward at the knee, front legs back at the wrist) and keep
        # the best: a straight start gets stuck.
        # The leg has two parts (upper leg, foot), so not every spot under it can be reached with the foot flat: try
        # spots a little forward and back along the ground and keep the one it reaches best (nearest the middle).
        best = None
        for dz in np.arange(-3.0, 3.01, 0.5):
            tgt = target + np.array([0.0, 0.0, dz])
            for a in ((-35.0, 35.0), (35.0, -35.0)):
                if name.startswith("front"):
                    a = (-a[0] * 0.5, -a[1] * 0.5)
                trial = dict(pose)
                trial[upper] = ((a[0], 0.0, 0.0), (0, 0, 0), (1, 1, 1))
                trial[lower] = ((a[1], 0.0, 0.0), (0, 0, 0), (1, 1, 1))
                solved, err, _x = ik.solve(m, trial, lims[name], tgt, heading)
                score = err + 0.08 * abs(dz)
                if best is None or score < best[2] - 1e-3:
                    best = (solved, err, score)
        pose, err, _s = best
        if err > 0.3:
            print(f"  warning: {m.identifier} rest {name} misses by {err:.2f}")
    ik.bake_rest(m, pose, lims.values())
    if q.tail_dirs:
        for name, d, (length, t) in zip(tail_names(q), q.tail_dirs, q.tail):
            mats = m.pose_matrices()
            start = m.point(mats, name, m[name].pivot)
            dd = R.clamp_above(start, R.normalize(d), length, t / 2.0 + 0.1)
            R.set_rest_world(m, name, dd, axis=(0, -1, 0))
    return lims
