"""
The Rat King's skeleton (a giant rat standing upright), his posture, and the maths the parts and animations share.

Bones (bind pose: standing bolt upright, arms and legs hanging straight down, the tail and cape hanging straight
down from where they start; every rotation zero):

    root                         the spot he stands on; never animated
     ├ hole                      the dirt hole and mound (tucked under the ground until he burrows)
     └ body                      moves the whole rat (jumps, dives, falls); pivot at the ground under him
        └ hips                   pelvis; tilts everything above it
           ├ tail_0 .. tail_N    the tail, a chain hanging from the base of the spine
           ├ thigh_l/r > shin_l/r > foot_l/r
           └ belly               lower torso
              └ chest            chest and shoulders
                 ├ neck > head   skull (+ snout boxes, jaw > tongue, ear_l/r, crown, eyelids, X eyes, markers)
                 ├ arm_l/r > forearm_l/r > hand_l/r      (hand_r > staff)
                 └ cape_0 > cape_1 > cape_2

His posture (hunch, crouch, head, arms, staff, tail, cape) is baked into the bones' rest rotations, so the model
stands in it with no animation playing; the legs are bent by IK so the feet stand flat on the ground.

Units are Bedrock model units (16 = 1 block); his front is -z and his own left side +x (the same as tools/golems).
Vectors called "world" here are Bedrock model space (before the entity's yaw), y up.
"""
import math

import numpy as np

from kit import geo, ik
from kit.clipping import _box_frame, _inside_depth

SIDES = (("l", 1), ("r", -1))
DOWN = np.array([0.0, -1.0, 0.0])
UP = np.array([0.0, 1.0, 0.0])


# ---------------------------------------------------------------- proportions and posture


class RatBody:
    """Proportions and posture. Every design passes its own values; the defaults are a lean, sly rat."""

    def __init__(self, **kw):
        # Legs (bind pose: straight down from the hip joints)
        self.hip_x = 4.0              # hip joints this far out from the middle
        self.hip_y = 23.0             # hip joint height (bind pose; the rest pose bends the knees to fit)
        self.thigh_len = 11.0
        self.shin_len = 12.0
        self.ankle_h = 3.0            # ankle joint above the sole
        self.foot_len = 11            # heel to toe tips
        self.heel = 3                 # heel behind the ankle
        self.foot_target = (4.5, -1.0)   # (x, z) under the left ankle in the rest pose; the right one mirrors
        self.foot_heading = (0.18, -1.0)  # toes turned out a little
        # Torso (bind pose: upright, stacked on the hips)
        self.belly_y = 5.0            # belly pivot above the hip joints
        self.chest_y = 9.0            # chest pivot above the belly pivot
        self.chest_h = 10.0
        self.hips_size = (11, 8, 10)
        self.belly_size = (13, 10, 11)
        self.chest_size = (9, 10, 8)
        self.belly_z = -0.5           # belly box centre (z) relative to the spine
        self.neck_size = (6, 6, 6)
        self.hump = None              # (w, h, d): a hunched upper back on the chest's back, or None
        # Shoulders and arms
        self.shoulder_x = 5.5
        self.shoulder_drop = 2.0      # shoulder joints this far below the top of the chest
        self.upper_len = 10.0
        self.fore_len = 9.0
        self.arm_w = 3
        self.fore_w = 2
        self.thigh_w = 5              # thigh box (w, d); its height follows the thigh's length
        self.thigh_d = 7
        self.shin_w = 3
        # Neck and head
        self.neck_len = 4.0
        self.neck_z = -1.0
        self.skull = (9, 8, 9)
        self.ear = (7, 8)             # ear disc (w, h)
        self.ear_tilt = 22.0          # ears lean outwards
        # Tail
        self.tail = [(6, 3), (6, 3), (6, 2), (6, 2), (6, 2), (5, 2), (5, 1), (5, 1)]   # (length, thickness)
        self.tail_y = 1.0             # tail root above the hip joints
        # Cape: hangs from the back of the shoulders
        self.cape_w = 16
        self.cape = [13, 13, 12]      # segment lengths
        # ---- posture (total rest rotations, Bedrock degrees; x > 0 leans forward / swings the bottom back)
        self.hips_pitch = 8.0
        self.belly_pitch = 8.0
        self.chest_pitch = 10.0
        self.neck_pitch = 16.0
        self.head_pitch = None        # None: look ahead (cancels the lean) then tip down by head_down
        self.head_down = 6.0
        self.head_turn = 0.0
        self.head_tilt = 0.0
        # Arms: total rotations of (arm, forearm, hand) per side, in their own (posed) frames
        self.arm_l = ((-10.0, 0.0, -8.0), (-60.0, 0.0, 0.0), (0.0, 0.0, 0.0))
        self.arm_r = ((-28.0, 0.0, 14.0), (-50.0, 0.0, 0.0), (0.0, 0.0, 0.0))
        # Staff: which way it points in the rest pose (world), and how far it slides through the hand (+ = higher)
        self.staff_dir = (0.0, 1.0, 0.0)
        self.staff_twist = 0.0
        # Tail rest shape: a world direction per segment (it lies on the ground once it gets there)
        self.tail_dirs = None
        # Cape: how far it flares away from the back (degrees) per segment
        self.cape_flare = (14.0, 6.0, 3.0)
        # Free (left) hand: a spot to put the palm on in the rest pose (world), solved by IK; None: use arm_l
        self.hand_l_target = None
        # True: the staff hand turns so the shaft runs across its knuckles, the way a fist grips a stick
        self.grip_across = False
        self.__dict__.update(kw)


# ---------------------------------------------------------------- frames and directions


def to_gecko(v):
    return np.array([-v[0], v[1], v[2]], dtype=float)


def to_bedrock(v):
    return np.array([-v[0], v[1], v[2]], dtype=float)


def local_dir(mats, parent_name, world_dir):
    """A world (Bedrock) direction expressed in a parent bone's posed frame (Bedrock)."""
    if parent_name is None:
        return np.asarray(world_dir, dtype=float)
    R = mats[parent_name][:3, :3]
    g = np.linalg.solve(R, to_gecko(world_dir))
    return to_bedrock(g)


def aim_axis(axis, direction, twist=0.0, prev=None):
    """
    Bedrock rotation that turns a bone's own axis (+y or -y) towards a direction in its parent's frame (the shortest
    turn, as geo.aim). With `prev` (last frame's angles) the angles stay continuous: GeckoLib blends rotation keyframes
    angle by angle, so a jump from 179 to -179 degrees would spin the part all the way round.
    """
    d = np.asarray(direction, dtype=float)
    d = d / np.linalg.norm(d)
    if axis[1] < 0:
        d = -d
    if prev is None:
        return geo.aim(d, twist)
    g = np.array([-d[0], d[1], d[2]])
    y = np.array([0.0, 1.0, 0.0])
    ax = np.cross(y, g)
    s_ = np.linalg.norm(ax)
    c = float(np.dot(y, g))
    if s_ < 1e-9:
        Rm = np.eye(3) if c > 0 else geo.axis_angle((1.0, 0.0, 0.0), math.pi)
    else:
        Rm = geo.axis_angle(ax / s_, math.atan2(s_, c))
    if twist:
        Rm = geo.axis_angle(g, math.radians(twist)) @ Rm
    return tuple(geo.euler_bedrock(Rm, prev))


def set_rest_world(m, bone_name, world_dir, axis=(0, 1, 0), twist=0.0):
    """Sets a bone's rest rotation so its own axis points along a world direction in the rest pose."""
    mats = m.pose_matrices()
    b = m[bone_name]
    parent = b.parent.name if b.parent is not None else None
    b.rotation = tuple(aim_axis(axis, local_dir(mats, parent, world_dir), twist))


def world_point(m, mats, bone_name, bedrock_point):
    return m.point(mats, bone_name, bedrock_point)


def world_dir(m, mats, bone_name, bedrock_dir):
    return m.direction(mats, bone_name, bedrock_dir)


def normalize(v):
    v = np.asarray(v, dtype=float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-12 else v


def yaw_pitch(yaw_deg, pitch_deg):
    """A world direction: yaw 0 = straight back (+z), 90 = his left (+x); pitch < 0 points down."""
    y, p = math.radians(yaw_deg), math.radians(pitch_deg)
    return np.array([math.sin(y) * math.cos(p), math.sin(p), math.cos(y) * math.cos(p)])


# ---------------------------------------------------------------- skeleton


def skeleton(m, b):
    """Creates the bones (no boxes yet) and returns them by name."""
    B = {}
    root = m.bone("root", (0, 0, 0))
    hole = m.bone("hole", (0, 0, 0), parent=root)
    body = m.bone("body", (0, 0, 0), parent=root)
    hips = m.bone("hips", (0, b.hip_y, 0), parent=body)
    belly = m.bone("belly", (0, b.hip_y + b.belly_y, 0), parent=hips)
    chest_y = b.hip_y + b.belly_y + b.chest_y
    chest = m.bone("chest", (0, chest_y, 0), parent=belly)
    top = chest_y + b.chest_h
    neck = m.bone("neck", (0, top - 1.0, b.neck_z), parent=chest)
    head = m.bone("head", (0, top - 1.0 + b.neck_len, b.neck_z), parent=neck)
    B.update(root=root, hole=hole, body=body, hips=hips, belly=belly, chest=chest, neck=neck, head=head)
    sy = top - b.shoulder_drop
    for side, sx in SIDES:
        arm = m.bone(f"arm_{side}", (sx * b.shoulder_x, sy, 0), parent=chest)
        fore = m.bone(f"forearm_{side}", (sx * b.shoulder_x, sy - b.upper_len, 0), parent=arm)
        hand = m.bone(f"hand_{side}", (sx * b.shoulder_x, sy - b.upper_len - b.fore_len, 0), parent=fore)
        thigh = m.bone(f"thigh_{side}", (sx * b.hip_x, b.hip_y, 0), parent=hips)
        shin = m.bone(f"shin_{side}", (sx * b.hip_x, b.hip_y - b.thigh_len, 0), parent=thigh)
        foot = m.bone(f"foot_{side}", (sx * b.hip_x, b.hip_y - b.thigh_len - b.shin_len, 0), parent=shin)
        B.update({f"arm_{side}": arm, f"forearm_{side}": fore, f"hand_{side}": hand,
                  f"thigh_{side}": thigh, f"shin_{side}": shin, f"foot_{side}": foot})
    # Tail: hangs from the back of the hips.
    ty = b.hip_y + b.tail_y
    tz = b.hips_size[2] / 2.0 - 0.5
    parent = hips
    y = ty
    for i, (length, _thick) in enumerate(b.tail):
        seg = m.bone(f"tail_{i}", (0, y, tz), parent=parent)
        B[f"tail_{i}"] = seg
        parent = seg
        y -= length
    # Cape: hangs from the back of the shoulders (behind the hump, if he has one).
    cz = b.chest_size[2] / 2.0 + 0.5 + (b.hump[2] if b.hump else 0.0)
    parent = chest
    y = top - 0.5
    for i, length in enumerate(b.cape):
        seg = m.bone(f"cape_{i}", (0, y, cz), parent=parent)
        B[f"cape_{i}"] = seg
        parent = seg
        y -= length
    return B


def tail_names(b):
    return [f"tail_{i}" for i in range(len(b.tail))]


def cape_names(b):
    return [f"cape_{i}" for i in range(len(b.cape))]


# ---------------------------------------------------------------- limbs for the solver


def leg_limb(m, b, side):
    """Hip (3 axes), knee (bends forward only: shin swings back), ankle (keeps the sole flat)."""
    return ik.Limb([
        ik.Joint(f"thigh_{side}", (0, 1, 2), lo=(-120, -40, -35), hi=(45, 40, 35), prefer=(None, 0, 0), weight=0.03),
        ik.Joint(f"shin_{side}", (0,), lo=(-2,), hi=(150,)),
        ik.Joint(f"foot_{side}", (0, 1, 2), lo=(-110, -45, -40), hi=(90, 45, 40), prefer=(None, 0, 0), weight=0.01),
    ], f"foot_{side}", contact=foot_contact(m, b, side), down=(0, -1, 0), forward=(0, 0, -1))


def foot_contact(m, b, side):
    """The middle of the sole, under the ball of the foot (bind pose)."""
    p = m[f"foot_{side}"].pivot
    return (p[0], p[1] - b.ankle_h, p[2] - (b.foot_len / 2.0 - b.heel))


def staff_limb(m, tip_name="staff"):
    """The right arm planting the staff's foot (the staff is the end bone; it can't bend)."""
    return ik.Limb([
        ik.Joint("arm_r", (0, 1, 2), lo=(-150, -60, -20), hi=(60, 60, 75), prefer=(None, 0, None), weight=0.02),
        ik.Joint("forearm_r", (0, 1), lo=(-130, -40), hi=(0, 40), prefer=(None, 0), weight=0.05),
        ik.Joint("hand_r", (0, 1, 2), lo=(-70, -60, -60), hi=(70, 60, 60), prefer=(0, 0, 0), weight=0.05),
        ik.Joint(tip_name, (), lo=(), hi=()),
    ], tip_name, contact=(0, 0, 0))


def hand_limb(m, side="l"):
    """An arm reaching its palm (2 units below the wrist) to a spot."""
    hp = m[f"hand_{side}"].pivot
    return ik.Limb([
        ik.Joint(f"arm_{side}", (0, 1, 2), lo=(-170, -80, -80), hi=(70, 80, 80), prefer=(None, None, None), weight=0.01),
        ik.Joint(f"forearm_{side}", (0, 1), lo=(-145, -60), hi=(0, 60), prefer=(None, 0), weight=0.02),
        ik.Joint(f"hand_{side}", (0, 1, 2), lo=(-60, -60, -60), hi=(60, 60, 60), prefer=(0, 0, 0), weight=0.08),
    ], f"hand_{side}", contact=(hp[0], hp[1] - 2.0, hp[2]), down=(0, -1, 0), forward=(0, 0, -1))


def foot_targets(b):
    out = {}
    for side, sx in SIDES:
        fx, fz = b.foot_target
        hx, hz = b.foot_heading
        out[f"leg_{side}"] = (np.array([sx * fx, 0.0, fz]), np.array([sx * hx, 0.0, hz]))
    return out


def legs(m, b):
    return {f"leg_{side}": leg_limb(m, b, side) for side, _sx in SIDES}


# ---------------------------------------------------------------- the rest pose


def pose_rest(m, b, staff=True):
    """
    Bakes the posture into the rest rotations: spine, head, arms, staff, legs (IK onto their footprints), tail and
    cape (hanging from where they start). Order matters: each step uses the posed rest pose of the ones before.
    """
    m["hips"].rotation = (b.hips_pitch, 0.0, 0.0)
    m["belly"].rotation = (b.belly_pitch - b.hips_pitch * 0.0, 0.0, 0.0)
    m["chest"].rotation = (b.chest_pitch, 0.0, 0.0)
    m["neck"].rotation = (b.neck_pitch, 0.0, 0.0)
    lean = b.hips_pitch + b.belly_pitch + b.chest_pitch + b.neck_pitch
    hp = b.head_pitch if b.head_pitch is not None else -lean + b.head_down
    m["head"].rotation = (hp, b.head_turn, b.head_tilt)
    for side, _sx in SIDES:
        a, f, h = getattr(b, f"arm_{side}")
        m[f"arm_{side}"].rotation = tuple(a)
        m[f"forearm_{side}"].rotation = tuple(f)
        m[f"hand_{side}"].rotation = tuple(h)
    if b.hand_l_target is not None:
        limb = hand_limb(m, "l")
        pose, err, _x = ik.solve(m, {}, limb, np.asarray(b.hand_l_target, dtype=float), None, down=(0.0, -1.0, 0.0),
                                 w_down=0.3, w_head=0.0)
        if err > 0.5:
            print(f"  warning: {m.identifier} rest left hand misses its spot by {err:.2f} units")
        ik.bake_rest(m, pose, [limb])
    if staff and "staff" in m.by_name:
        if b.grip_across:
            grip_across(m, normalize(b.staff_dir))
        set_rest_world(m, "staff", normalize(b.staff_dir), axis=(0, 1, 0), twist=b.staff_twist)
    # Legs: bend them so the feet stand flat on their footprints.
    lims = legs(m, b)
    pose = {}
    for name, (target, heading) in foot_targets(b).items():
        side = name[-1]
        pose[f"thigh_{side}"] = ((-40.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
        pose[f"shin_{side}"] = ((60.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
        pose[f"foot_{side}"] = ((-20.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
        pose, err, _x = ik.solve(m, pose, lims[name], target, heading)
        if err > 0.25:
            print(f"  warning: {m.identifier} rest {name} misses its footprint by {err:.2f} units")
    ik.bake_rest(m, pose, lims.values())
    # Tail and cape: aim each segment along its rest direction.
    if b.tail_dirs:
        for name, d, (length, t) in zip(tail_names(b), b.tail_dirs, b.tail):
            mats = m.pose_matrices()
            start = m.point(mats, name, m[name].pivot)
            dd = clamp_above(start, normalize(d), length, t / 2.0 + 0.15)
            set_rest_world(m, name, dd, axis=(0, -1, 0))
    if cape_names(b)[0] in m.by_name:
        pose = _empty_pose()
        dirs = drape(m, pose, b, cape_dirs(b))
        for name, d in zip(cape_names(b), dirs):
            set_rest_world(m, name, d, axis=(0, -1, 0))
    return lims


def grip_across(m, staff_dir, hand="hand_r"):
    """
    Turns the staff hand (by the least it can) so its knuckle line (its own x axis) runs along the staff: the shaft then
    goes across the fist, the way a hand grips a stick, instead of out of it along the arm.
    """
    mats = m.pose_matrices()
    Mh = mats[hand][:3, :3]
    s_g = to_gecko(staff_dir)
    x_now = Mh @ np.array([1.0, 0.0, 0.0])
    target = s_g if np.dot(x_now, s_g) >= 0.0 else -s_g
    axis = np.cross(x_now, target)
    sa = float(np.linalg.norm(axis))
    if sa < 1e-9:
        return
    Q = np.asarray(geo.axis_angle(axis / sa, math.atan2(sa, float(np.dot(x_now, target)))))[:3, :3]
    parent = m[hand].parent.name
    local = mats[parent][:3, :3].T @ (Q @ Mh)
    m[hand].rotation = tuple(geo.euler_bedrock(local))


def _empty_pose():
    from kit.anims import Pose
    return Pose()


def cape_dirs(b, back=(0.0, 0.0, 1.0)):
    """The directions the cape's segments would like to hang in: down, tipped back by their flare."""
    flares = list(b.cape_flare) + [b.cape_flare[-1]] * len(b.cape)
    bk = normalize(back)
    return [normalize(DOWN * math.cos(math.radians(f)) + bk * math.sin(math.radians(f))) for f in flares[:len(b.cape)]]


CAPE_COLLIDERS = ("hips", "belly", "chest", "thigh_l", "thigh_r", "shin_l", "shin_r", "tail_0", "tail_1", "tail_2",
                  "arm_l", "arm_r")


BODY_TAGS = ("hips", "belly", "chest", "thigh", "shin", "tail", "arm", "forearm", "shoulder")


def colliders(m, mats, names=CAPE_COLLIDERS):
    """The body's own boxes the cape must clear (not its accessories: the mantle, hood and collar sit on the cape)."""
    out = []
    for n in names:
        if n not in m.by_name:
            continue
        for c in m[n].cubes:
            if c.tag in BODY_TAGS:
                out.append(_box_frame(mats, c))
    return out


def drape(m, pose, b, wanted, names=None, margin=0.4, floor=0.5, step=3.0, max_out=88.0, back=None, prev=None):
    """
    Hangs the cape segment by segment for a pose: each segment points along its wanted direction, then swings out
    (backwards, about his left-right axis) just far enough that the panel clears the body (hips, belly, chest, thighs,
    shins, the tail's root, the arms) by `margin` units, and its end stays above the floor. Returns the world
    directions; writes the segments' rotations into the pose as it goes (so each one hangs from the last).
    """
    names = names or cape_names(b)
    mats = m.pose_matrices(pose.as_geo())
    lat = normalize(world_dir(m, mats, "hips", (1.0, 0.0, 0.0)))
    if back is None:
        back = normalize(world_dir(m, mats, "hips", (0.0, 0.0, 1.0)))
    out = []
    for i, name in enumerate(names):
        bone = m[name]
        length = b.cape[i]
        start = m.point(mats, name, bone.pivot)
        cols = colliders(m, mats)
        d0 = normalize(wanted[i])
        best = d0
        ang = 0.0
        while ang <= max_out:
            d = _swing(d0, lat, back, ang)
            if not _hits(start, d, length, b.cape_w, lat, cols, margin) and start[1] + d[1] * length >= floor:
                best = d
                break
            best = d
            ang += step
        out.append(best)
        local = local_dir(mats, bone.parent.name, best)
        tot = aim_axis((0, -1, 0), local, prev=None if prev is None else prev.get(name))
        if prev is not None:
            prev[name] = tot
        set_total(pose, m, name, tot)
        mats = m.pose_matrices(pose.as_geo())
    return out


def _swing(d, lat, back, ang):
    """Turns direction d by ang degrees about the lateral axis, towards `back`."""
    a = math.radians(ang)
    axis = normalize(np.cross(back, d)) if np.linalg.norm(np.cross(back, d)) > 1e-6 else lat
    # Rodrigues: rotate d about axis by -a moves it towards back.
    k = axis
    v = d
    rot = v * math.cos(a) + np.cross(k, v) * math.sin(a) + k * np.dot(k, v) * (1 - math.cos(a))
    # Make sure we moved towards back, not away.
    if np.dot(rot, back) < np.dot(d, back) - 1e-6:
        rot = v * math.cos(a) - np.cross(k, v) * math.sin(a) + k * np.dot(k, v) * (1 - math.cos(a))
    return normalize(rot)


def _hits(start, d, length, width, lat, cols, margin):
    pts = []
    for t in (0.2, 0.45, 0.7, 1.0):
        for off in (-0.5, 0.0, 0.5):
            p = start + d * length * t + lat * width * off * 0.9
            pts.append(geo.gecko(p))
    pts = np.array(pts)
    for fr in cols:
        if np.any(_inside_depth(pts, fr, margin) > 0.0):
            return True
    return False


# ---------------------------------------------------------------- pose helpers for the animations


def rest_rotation(m, bone):
    return np.array(m[bone].rotation, dtype=float)


def set_total(pose, m, bone, rot, w=1.0):
    """Sets a bone's animation rotation so its TOTAL rotation is `rot` (blended by w from its rest rotation)."""
    rest = rest_rotation(m, bone)
    off = (np.asarray(rot, dtype=float) - rest) * w
    r = pose.get3(bone)[0]
    r[0], r[1], r[2] = off[0], off[1], off[2]


def rotate_body_about(pose, rot, point):
    """
    Turns the whole rat (the `body` bone, pivot at the ground under him, no rest rotation) by `rot` (Bedrock
    degrees) about a point (Bedrock model space) instead of its pivot, on top of any position already in the pose.
    """
    R = geo.gecko_rotation(*rot)[:3, :3]
    q = geo.gecko(point)
    shift = q - R @ q                      # GeckoLib space, blocks
    p = pose.get3("body")
    r = p[0]
    r[0] += rot[0]
    r[1] += rot[1]
    r[2] += rot[2]
    p[1][0] += -shift[0] * 16.0
    p[1][1] += shift[1] * 16.0
    p[1][2] += shift[2] * 16.0


def chain_follow(m, pose, names, desired, stiffness=None):
    """
    Poses a chain (tail or cape) segment by segment. desired(i, start_world, rigid_dir) -> world direction for
    segment i, where rigid_dir is where the segment would point if it kept its rest bend relative to its parent.
    Each segment's rotation becomes the TOTAL rotation that points its -y along that direction.
    """
    mats = m.pose_matrices(pose.as_geo())
    for i, name in enumerate(names):
        b = m[name]
        parent = b.parent.name
        # Rigid direction: the rest bend, carried by the parent's current pose.
        rest_local = np.array(geo.gecko_rotation(*b.rotation)[:3, :3]) @ np.array([0.0, -1.0, 0.0])
        rigid = to_bedrock(mats[parent][:3, :3] @ rest_local)
        start = m.point(mats, name, b.pivot)
        d = normalize(desired(i, start, normalize(rigid)))
        local = local_dir(mats, parent, d)
        tot = aim_axis((0, -1, 0), local)
        set_total(pose, m, name, tot)
        mats = m.pose_matrices(pose.as_geo())
    return mats


def clamp_above(start, direction, length, floor):
    """A direction like `direction` whose end (start + length * dir) stays at or above `floor`."""
    d = normalize(direction)
    end_y = start[1] + d[1] * length
    if end_y >= floor:
        return d
    flat = np.array([d[0], 0.0, d[2]])
    if np.linalg.norm(flat) < 1e-6:
        flat = np.array([0.0, 0.0, 1.0])
    flat = normalize(flat)
    need = (floor - start[1]) / length          # the d.y that puts the end on the floor
    need = max(-1.0, min(1.0, need))
    h = math.sqrt(max(0.0, 1.0 - need * need))
    return normalize(flat * h + np.array([0.0, need, 0.0]))
