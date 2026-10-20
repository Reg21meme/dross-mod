"""
The Huntsman's hands (rimemobs/huntsman.py on foot, huntsman_rider.py in the saddle), worked out with the golem kit's
IK (kit/ik.py) on his real arms, frame by frame, instead of guessed angles (the user's notes of October 19: the bow
and the horn "look unnatural", the spear thrust from the saddle went through the moose's antlers):

  arm(...)      puts a point of a hand (its fist, a horn's mouthpiece) on a spot, a direction of it along another
  bow(...)      an archer's two arms: the bow arm straight out along the aim, the bow upright and square to it; the
                drawing fist on the string, `draw` units back; the string's two halves turned and stretched to meet at
                the fingers, the arrow on the string slid to match
  spear_at(...) the free spear (spear_free) exactly where a bone's spear is now (a world matrix), so it can be let go of
                and fall (his death)

Everything is in the kit's Bedrock model units (design units times K, the model's own frame: his left is +x, his
face -z, up +y).
"""
import math

import numpy as np

from kit import geo, ik
from rimedesigns import d06_huntsman as H

ARM_REACH = 0.94             # how straight the bow arm is (of its full length)
# other starting folds of an arm for the IK when it's stuck (shoulder x, y, z; elbow x, y, z; wrist x, y, z)
STARTS = ((0.0, 0.0, 0.0, -20.0, 0.0, 0.0, 0.0, 0.0, 0.0),
          (-90.0, 0.0, 0.0, -20.0, 0.0, 0.0, 0.0, 0.0, 0.0),
          (-80.0, 0.0, 70.0, -20.0, 0.0, 0.0, 0.0, 60.0, 0.0),
          (-80.0, 0.0, -70.0, -20.0, 0.0, 0.0, 0.0, -60.0, 0.0),
          (-10.0, 0.0, 85.0, -15.0, 0.0, 0.0, 0.0, 80.0, 0.0),
          (-10.0, 0.0, -85.0, -15.0, 0.0, 0.0, 0.0, -80.0, 0.0),
          (-150.0, 0.0, 0.0, -30.0, 0.0, 0.0, 0.0, 0.0, 0.0))


class Hands:
    """The IK state of one animation (each arm's last answer, so the next frame starts from it and doesn't flip)."""

    def __init__(self, rig, joints):
        self.rig = rig
        self.m = rig.m
        self.k = rig.k
        self.j = {n: np.asarray(v, dtype=float) * rig.k for n, v in joints.items()}   # Bedrock units
        self.prev = {}
        self.miss = 0.0

    # ------------------------------------------------------------------ basics
    def mats(self, p):
        return self.m.pose_matrices(p.as_geo())

    def at(self, p, bone, joint=None, mats=None):
        """Where a joint (default: the bone's own pivot) is now."""
        mats = mats or self.mats(p)
        return self.m.point(mats, bone, self.j[joint or bone])

    def point(self, p, bone, rest_point, mats=None):
        mats = mats or self.mats(p)
        return self.m.point(mats, bone, rest_point)

    def direction(self, p, bone, rest_dir, mats=None):
        mats = mats or self.mats(p)
        d = self.m.direction(mats, bone, rest_dir)
        return d / np.linalg.norm(d)

    # ------------------------------------------------------------------ an arm
    def limb(self, side, contact, down_local, forward_local=(0.0, 0.0, -1.0)):
        joints = [
            ik.Joint(f"arm_{side}", (0, 1, 2), lo=(-185.0, -130.0, -130.0), hi=(75.0, 130.0, 130.0), weight=0.004),
            ik.Joint(f"forearm_{side}", (0, 1, 2), lo=(-150.0, -40.0, -40.0), hi=(4.0, 40.0, 40.0),
                     prefer=(None, 0.0, 0.0), weight=0.03),
            ik.Joint(f"hand_{side}", (0, 1, 2), lo=(-120.0, -120.0, -120.0), hi=(120.0, 120.0, 120.0),
                     prefer=(0.0, 0.0, 0.0), weight=0.006),
        ]
        return ik.Limb(joints, f"hand_{side}", contact=np.asarray(contact, dtype=float),
                       down=np.asarray(down_local, dtype=float), forward=np.asarray(forward_local, dtype=float))

    def arm(self, p, side, target, contact=None, down_local=(0.0, -1.0, 0.0), down=(0.0, -1.0, 0.0), heading=None,
            key=None, w_down=6.0, w_head=2.0):
        """Solves one arm so the hand's `contact` (its fist by default, a rest point) sits on `target`, its
        `down_local` direction points along `down` and its forward (-z) along `heading`; writes the angles into p."""
        contact = self.j[f"hand_{side}"] if contact is None else np.asarray(contact, dtype=float)
        limb = self.limb(side, contact, down_local)
        key = key or side
        x0 = self.prev.get(key)
        pose, miss, x = ik.solve(self.m, p.as_geo(), limb, np.asarray(target, dtype=float), heading=heading,
                                 down=np.asarray(down, dtype=float), x0=x0, w_down=w_down, w_head=w_head,
                                 w_smooth=0.004 if x0 is not None else 0.0)
        if miss > 0.8:
            # stuck in a poor fold of the arm: try again from a few other starts and keep the best
            for start in STARTS:
                p2, m2, x2 = ik.solve(self.m, p.as_geo(), limb, np.asarray(target, dtype=float), heading=heading,
                                      down=np.asarray(down, dtype=float), x0=np.array(start, dtype=float),
                                      w_down=w_down, w_head=w_head)
                if m2 < miss - 0.3:
                    pose, miss, x = p2, m2, x2
                if miss < 0.5:
                    break
        self.prev[key] = x
        for jnt in limb.joints:
            p.get3(jnt.bone)[0] = list(pose[jnt.bone][0])
        self.miss = max(self.miss, miss)
        return miss

    # ------------------------------------------------------------------ the bow
    def bow(self, p, aim, draw, nocked=True, cant=8.0, reach=ARM_REACH, draw_hand=True, extend=1.0, fist=None,
            nock_prop="arrow_nock"):
        """
        The bow arm out along `aim` (a unit Bedrock direction), the bow upright (canted `cant` degrees) and square to
        it, held where a full draw brings the string to the right of his chin (the anchor; or, if his arm can't reach
        that far, as far out as `reach` of it); the drawing fist `draw` design units back from where the string rests,
        on the string. With `nocked`, the arrow on the string (slid to the draw); returns the nock's world point.
        `extend` (0..1): how far out the bow arm is (an archer nocks with it bent and close, then pushes the bow out
        as he draws: 1 is the full draw's reach). `fist`: where the bow fist goes instead (archery.py: a straight
        arm); `nock_prop`: the arrow (or the volley's three) slid on the string.
        """
        aim = np.asarray(aim, dtype=float)
        aim = aim / np.linalg.norm(aim)
        up = np.array([0.0, 1.0, 0.0])
        up_p = up - aim * np.dot(up, aim)
        up_p /= np.linalg.norm(up_p)
        # cant: the top limb tipped a little towards his right (the bow hand's thumb side out)
        side = np.cross(aim, up_p)
        c = math.radians(cant)
        bow_up = up_p * math.cos(c) + side * math.sin(c)
        shoulder = self.at(p, "arm_l")
        length = (H.UPPER_ARM + H.FOREARM) * self.k
        anchor = self.point(p, "head", self.j["anchor"])
        if fist is None:
            fist = bow_fist(anchor, aim, up_p, extend, self.k)
            off = fist - shoulder
            if np.linalg.norm(off) > length * reach:
                fist = shoulder + off / np.linalg.norm(off) * length * reach
        self.arm(p, "l", fist, down_local=(0.0, -1.0, 0.0), down=-bow_up, heading=aim, key="bow")
        mats = self.mats(p)
        rest_mid = self.j["hand_l"] + np.array([0.0, 0.0, H.BOW_BEND * self.k])
        mid = self.m.point(mats, "bow_held", rest_mid)
        fwd = self.direction(p, "bow_held", (0.0, 0.0, -1.0), mats)
        nock = mid - fwd * draw * self.k
        if draw_hand:
            self.arm(p, "r", nock, down_local=(0.0, -1.0, 0.0), down=-bow_up, heading=fwd, key="draw")
        self.strings(p, draw)
        if nocked:
            p.pos(nock_prop, 0.0, 0.0, -(H.DRAW - draw) * self.k)
        return nock

    def strings(self, p, draw):
        """The string's halves from each tip to the nock `draw` units behind where the string rests (the bow's own
        frame), turned and stretched."""
        k = self.k
        rest_mid = self.j["hand_l"] + np.array([0.0, 0.0, H.BOW_BEND * k])
        nock = rest_mid + np.array([0.0, 0.0, draw * k])
        for bone in ("string_top", "string_bot"):
            tip = self.j[bone]
            d0 = rest_mid - tip
            d1 = nock - tip
            # the bone's pivot is the bow's tip line (the joint): the string's segment runs from the tip to the middle
            rot = turn(d0, d1)
            p.rot(bone, *rot)
            s = float(np.linalg.norm(d1) / np.linalg.norm(d0))
            p.get3(bone)[2] = [1.0, s, 1.0]

    # ------------------------------------------------------------------ the free spear
    def spear_at(self, p, mats_now, bone="spear"):
        """Puts spear_free (a child of the root, its pivot the spear's) where `bone`'s spear is in mats_now."""
        M = mats_now[bone]
        root = mats_now["root"]
        rel = np.linalg.inv(root) @ M
        R = rel[:3, :3]
        piv = geo.gecko(self.j["spear_free"])
        moved = (rel @ np.append(piv, 1.0))[:3]
        e = geo.euler_bedrock(R)
        d = moved - piv
        p.get3("spear_free")[0] = list(e)
        p.get3("spear_free")[1] = [-d[0] * 16.0, d[1] * 16.0, d[2] * 16.0]
        return e, (-d[0] * 16.0, d[1] * 16.0, d[2] * 16.0)


def bow_fist(anchor, aim, up_p, extend, k):
    """Where the bow fist goes: out along the aim from the anchor (his chin) as far as a full draw needs, less (and a
    little lower) as `extend` drops."""
    return anchor + aim * (H.BOW_BEND + H.DRAW) * k * extend - up_p * (1.0 - extend) * 12.0 * k


def side_on(rig, p, angle):
    """Turns his body `angle` degrees at the hips (positive: his left shoulder towards the front, an archer's stance)
    with his feet planted where they go turned with him (so his legs don't twist). (The root stays put, so a spear
    standing in the snow beside him stays where it is.)"""
    p.rot("hips", 0.0, angle, 0.0)
    mats = rig.m.pose_matrices(p.as_geo())
    for name in rig.limbs:
        tgt, heading = rig.rest[name]
        world = rig.m.point(mats, "hips", np.asarray(tgt, dtype=float))
        world[1] = float(tgt[1])
        head = rig.m.direction(mats, "hips", np.asarray(heading, dtype=float))
        head[1] = 0.0
        rig.solve_limb(p, name, world, head / np.linalg.norm(head))


def turn(d0, d1):
    """Bedrock degrees of the shortest turn taking direction d0 to d1 (both Bedrock, in the bone's parent frame)."""
    a = np.array([-d0[0], d0[1], d0[2]], dtype=float)
    b = np.array([-d1[0], d1[1], d1[2]], dtype=float)
    a /= np.linalg.norm(a)
    b /= np.linalg.norm(b)
    axis = np.cross(a, b)
    s = float(np.linalg.norm(axis))
    c = float(np.dot(a, b))
    if s < 1e-9:
        return (0.0, 0.0, 0.0)
    R = geo.axis_angle(axis / s, math.atan2(s, c))
    return tuple(geo.euler_bedrock(R))


def unit(v):
    v = np.asarray(v, dtype=float)
    return v / np.linalg.norm(v)


def elevate(yaw_deg, pitch_deg):
    """A Bedrock direction: forward (-z) turned `yaw_deg` to his left and tipped `pitch_deg` up."""
    y, pch = math.radians(yaw_deg), math.radians(pitch_deg)
    return np.array([math.sin(y) * math.cos(pch), math.sin(pch), -math.cos(y) * math.cos(pch)])
