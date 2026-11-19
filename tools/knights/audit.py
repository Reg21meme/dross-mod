"""
Checks the elemental knights' animations (the user, November 19, 2026: "collision tests ... make sure stuff looks
fluid, nice, so interactions between body parts that shouldn't collide ..."). Builds each knight in memory (writes
nothing in the project) and reports:

  clip     every part of it going into another it shouldn't (the weapon into its body, head or legs, an arm into its
           head, a leg into the other, the cape into the legs...) deeper than they overlap as it stands, and anything
           under the floor
  smooth   snaps: a frame where a part's tip changes speed far more than in the frames round it (a kink in its path),
           outside the moments a blow lands (meant to stop dead there); the loops' seams (the last frame meeting the
           first, speed and all)
  joins    the blends the game makes into each animation from the ones before it (GeckoLib blends the angles straight:
           an angle a turn or more away from where the last animation left it spins the part round on the way)

  python tools/knights/audit.py                  every knight, every check
  python tools/knights/audit.py geode wreck      just these
  python tools/knights/audit.py --clip           just one check (--clip, --smooth, --joins)

Exits 1 when it finds something past the tolerances below.
"""
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

import mobs  # noqa: E402
from rimekit import mobkit  # noqa: E402

# ---------------------------------------------------------------- tolerances

CLIP_TOL = 1.6          # units deeper than at rest a part may go into another (a tenth of a block for a moment:
                        # rigid boxes bending at a joint graze a little; 1.2 found only such grazes, 1.4-1.6)
FLOOR_TOL = 0.8         # units under the floor
SNAP_RATIO = 3.5        # a frame's change of speed this many times its neighbours' ...
SNAP_MIN = 90.0         # ... and at least this many units a second (a slow part's kinks don't show)
SNAP_HIT_WINDOW = 0.07  # seconds round a blow's moment where a sudden stop is meant
SEAM_POS = 0.6          # units a loop's last frame may be off its first
SEAM_VEL = 25.0         # units a second its speed may change across the seam
SPIN = 150.0            # degrees an angle may differ between two animations the game blends between

# what each bone is, for the clipping check (None: not checked)
GROUPS = {
    "waist": "torso", "body": "torso", "head": "head", "cape": "cape",
    "arm_r": "arm_r", "arm_l": "arm_l", "fore_r": "arm_r", "fore_l": "arm_l", "hand_r": "held_r",
    "hand_l": "held_l",
    "leg_r": "leg_r", "leg_l": "leg_l", "anchor": "held_r", "weapon_r": "held_r", "boulder": "boulder", "cap": "cap",
    "orb": None,
}
# (a, b): a's points mustn't go into b's boxes (deeper than at rest)
PAIRS = [
    ("held_r", "torso"), ("held_r", "head"), ("held_r", "leg_r"), ("held_r", "leg_l"), ("held_r", "arm_l"),
    ("held_r", "cape"), ("held_r", "held_l"),
    ("held_l", "torso"), ("held_l", "head"), ("held_l", "leg_r"), ("held_l", "leg_l"), ("held_l", "arm_r"),
    ("held_l", "cape"),
    ("arm_r", "head"), ("arm_l", "head"), ("arm_r", "leg_r"), ("arm_l", "leg_l"), ("arm_r", "arm_l"),
    ("arm_r", "torso"), ("arm_l", "torso"),
    ("leg_r", "leg_l"), ("leg_l", "leg_r"), ("leg_r", "cape"), ("leg_l", "cape"), ("cape", "leg_r"),
    ("cape", "leg_l"),
    ("boulder", "head"), ("boulder", "torso"), ("cap", "head"), ("head", "cap"), ("held_r", "cap"),
    ("held_l", "cap"),
]


def animations(kn):
    m, texs = mobkit.build_model(kn, verbose=False)
    rig = mobkit.MobRig(m, kn)
    anims = {"idle": mobs.idle(rig, kn), "walk": mobs.walk(rig, kn)}
    for name, f in kn.moves.items():
        anims[name] = f(rig, kn)
    return m, anims


# ---------------------------------------------------------------- clipping


# A two-handed blow's haft runs through its left fist, and the fists meet on it: those pairs aren't checked in them
# (knightkit/kreach.py; the user, November 19, 2026: "use both arms").
# (and the arms lie against the chest and each other as both hands hold on in front of it: the Thawed Soldiers' thick
# arms press about 4.6 units into their chest there, the others' 3)
GRIP_PAIRS = {("held_r", "held_l"), ("held_r", "arm_l"), ("held_l", "arm_r"), ("arm_r", "arm_l"), ("arm_l", "torso"),
              ("arm_r", "torso")}
# The Stormblade's X-cut crosses his forearms in front of him (their steel bracers touch).
CROSSED = {("stormblade", "slash"): {("arm_r", "arm_l")}}
TWO_HANDED = {("thawedaxe", "swing"), ("thawedaxe", "smash"), ("thawedaxe", "idle"), ("thawedaxe", "walk"),
              ("sporebound", "reap"), ("cinder", "swing"), ("cinder", "slam")}


def check_clip(m, anims, kid=None):
    old = (mobkit.CLIP_TOLERANCE, mobkit.FLOOR_TOLERANCE)
    mobkit.CLIP_TOLERANCE, mobkit.FLOOR_TOLERANCE = CLIP_TOL, FLOOR_TOL
    found = {}
    try:
        for name, a in anims.items():
            pairs = [q for q in PAIRS if ((kid, name) not in TWO_HANDED or q not in GRIP_PAIRS)
                     and q not in CROSSED.get((kid, name), ())]
            found.update(mobkit.clip_check(m, {name: a}, lambda b: GROUPS.get(b, "torso"), pairs, step=0.05))
    finally:
        mobkit.CLIP_TOLERANCE, mobkit.FLOOR_TOLERANCE = old
    return [f"CLIP {what} in {name} at {t:.2f} s, {d:.1f} units ({where})"
            for (name, what), (d, t, where) in sorted(found.items(), key=lambda kv: -kv[1][0])]


# ---------------------------------------------------------------- smoothness


def _tips(m):
    """Each bone with cubes: (its cube, the corner in that cube's frame) furthest from its pivot (where it moves
    most), in the rest pose."""
    mats = m.pose_matrices(None)
    out = {}
    for b in m.bones:
        if not b.cubes:
            continue
        piv = np.array([-b.pivot[0], b.pivot[1], b.pivot[2]]) / 16.0
        best, far = None, -1.0
        for c in b.cubes:
            M, _inv, lo, hi = mobkit._box_frame(mats, c)
            for x in (lo[0], hi[0]):
                for y in (lo[1], hi[1]):
                    for z in (lo[2], hi[2]):
                        q = (M @ np.array([x, y, z, 1.0]))[:3]
                        d = float(np.sum((q - piv) ** 2))
                        if d > far:
                            far, best = d, (c, np.array([x, y, z, 1.0]))
        out[b.name] = best
    return out


def _track(m, a, tips, dt):
    """{bone: positions (n, 3) in units} over the animation (a loop: two extra frames round the seam each side)."""
    loop = a.loop is True
    ts = np.arange(-2 * dt if loop else 0.0, a.length + (2 * dt if loop else 0.0) + 1e-9, dt)
    out = {b: [] for b in tips}
    for t in ts:
        tt = t % a.length if loop else min(max(t, 0.0), a.length)
        mats = m.pose_matrices(a.sample(float(tt)))
        for b, (c, corner) in tips.items():
            if abs(float(np.linalg.det(mats[b][:3, :3]))) < 1e-9:
                out[b].append(np.full(3, np.nan))
                continue
            M = mobkit._box_frame(mats, c)[0]
            out[b].append((M @ corner)[:3] * 16.0)
    return ts, {b: np.array(v) for b, v in out.items()}


def check_smooth(m, a, name, dt=0.025):
    tips = _tips(m)
    ts, tr = _track(m, a, tips, dt)
    hits = []
    for key in ("hits", "release"):
        v = a.meta.get(key)
        if v is not None:
            hits += list(v) if isinstance(v, (list, tuple)) else [v]
    worst = {}
    for b, P in tr.items():
        if np.isnan(P).any():
            continue
        V = np.diff(P, axis=0) / dt                    # speed between frames
        dV = np.linalg.norm(np.diff(V, axis=0), axis=1)   # change of speed at each inner frame (units/s)
        for i in range(2, len(dV) - 2):
            t = float(ts[i + 1])
            if any(abs(t - h) < SNAP_HIT_WINDOW for h in hits):
                continue
            around = np.median(np.r_[dV[i - 2:i], dV[i + 1:i + 3]])
            if dV[i] > SNAP_MIN and dV[i] > SNAP_RATIO * max(around, 1.0):
                if b not in worst or dV[i] > worst[b][0]:
                    worst[b] = (float(dV[i]), t % a.length if a.loop is True else t, float(around))
    out = [f"SNAP {name}: {b} at {t:.3f} s, its speed changes {d:.0f} u/s in a frame ({ar:.0f} round it)"
           for b, (d, t, ar) in sorted(worst.items(), key=lambda kv: -kv[1][0])]
    if a.loop is True:
        p0, p1 = a.sample(0.0), a.sample(a.length - 1e-6)
        for bone in p0:
            for ci, cname in enumerate(("rotation", "position", "scale")):
                d = max(abs(x - y) for x, y in zip(p0[bone][ci], p1.get(bone, p0[bone])[ci]))
                if d > (2.0 if ci == 0 else SEAM_POS if ci == 1 else 0.02):
                    out.append(f"SEAM {name}: {bone} {cname} jumps {d:.2f} at the loop")
    return out


# ---------------------------------------------------------------- the blends between animations


def check_joins(anims):
    """The game blends from idle or walk (anywhere in the loop) into each move, and from each move's end back. GeckoLib
    turns each angle straight from one value to the other: angles that differ by a turn or more spin the part round."""
    out = []
    gaits = [anims["idle"], anims["walk"]]
    for name, a in anims.items():
        if a.loop is True:
            continue
        first, last = a.sample(0.0), a.sample(a.length)
        for g in gaits:
            for t in np.arange(0.0, g.length, g.length / 8.0):
                gp = g.sample(float(t))
                for bone in set(first) | set(gp):
                    for end, pose in (("start", first), ("end", last)):
                        r1 = pose.get(bone, ((0, 0, 0),))[0]
                        r0 = gp.get(bone, ((0, 0, 0),))[0]
                        d = max(abs(x - y) for x, y in zip(r1, r0))
                        if d > SPIN:
                            out.append(f"SPIN {name} {end}: {bone} turns {d:.0f} degrees in the blend from {g.name}")
    return sorted(set(out))


def main(argv):
    which = [x for x in argv if not x.startswith("--")]
    flags = [x for x in argv if x.startswith("--")] or ["--clip", "--smooth", "--joins"]
    bad = 0
    for kn in mobs.KNIGHTS:
        if which and kn.ID not in which and kn.label not in which:
            continue
        m, anims = animations(kn)
        lines = []
        if "--clip" in flags:
            lines += check_clip(m, anims, kn.ID)
        if "--smooth" in flags:
            for name, a in anims.items():
                lines += check_smooth(m, a, name)
        if "--joins" in flags:
            lines += check_joins(anims)
        print(f"{kn.label} {kn.NAME}: {'clear' if not lines else str(len(lines)) + ' found'}")
        for ln in lines:
            print("   ", ln)
        bad += len(lines)
        sys.stdout.flush()
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
