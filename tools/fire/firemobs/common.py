"""
What the fire mobs share: a rig whose limbs are any chain of bones planted with the golem kit's IK (a pterosaur's
wing wrist on the ground as well as its feet), and small helpers for keyed motion.
"""
import math

import numpy as np

from kit import ik
from kit.anims import Rig
from kit.clipping import _box_frame
from kit.moves import keyed
from rimekit import mobkit
from rimekit.mobkit import Pose, _grid

IK_SMOOTH = 0.01


class FireRig(Rig):
    """
    A Rig with named limbs (ik.Limb) and where each one's contact stands at rest. `solve(p, name, target)` plants one
    on top of the body motion already in the pose; each limb starts its search from its last frame.
    """

    def __init__(self, m, limbs, rest, w_down=None):
        super().__init__(m, limbs, rest, dict(ik_smooth=IK_SMOOTH))
        self.w_down = dict(w_down or {})        # per limb: how hard its contact keeps facing down (default 6)
        self._warm = {}

    def reset_warm(self):
        self._warm = {}

    def solve(self, p, name, target, heading=None):
        limb = self.limbs[name]
        if heading is None:
            heading = self.rest[name][1]
        x0 = self._warm.get(name)
        wd = self.w_down.get(name, 6.0)
        solved, err, x = ik.solve(self.m, p.as_geo(), limb, target, heading, x0=x0, w_down=wd,
                                  w_head=0.0 if heading is None else 2.0, w_smooth=0.0 if x0 is None else IK_SMOOTH)
        if err > 1.0 and x0 is not None:
            s2, e2, x2 = ik.solve(self.m, p.as_geo(), limb, target, heading, x0=None, w_down=wd,
                                  w_head=0.0 if heading is None else 2.0)
            if e2 < err - 0.1:
                solved, err, x = s2, e2, x2
        self._warm[name] = x
        for j in limb.joints:
            p.get3(j.bone)[0] = list(solved[j.bone][0])
        return err

    def plant(self, p, offsets=None, which=None):
        for name in (which or self.limbs):
            tgt, heading = self.rest[name]
            tgt = np.array(tgt, dtype=float)
            if offsets and name in offsets:
                tgt = tgt + np.asarray(offsets[name], dtype=float)
            self.solve(p, name, tgt, heading)


def lowest(m, bone):
    """The lowest point (Bedrock y) of a bone's boxes at rest."""
    mats = m.pose_matrices()
    return min(float(_grid(_box_frame(mats, c))[:, 1].min() * 16.0) for c in m[bone].cubes)


def k(t, keys):
    """keyed() as a float."""
    return float(keyed(t, keys))


def wave(t, period, phase=0.0):
    return math.sin(2.0 * math.pi * (t / period + phase))


record = mobkit.record
Pose = Pose
