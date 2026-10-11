"""
Model geometry for the golem designs: bones, boxes, box-UV packing, the .geo.json writer, and forward
kinematics that copy GeckoLib 4 (Forge 1.20.1) exactly.

Coordinates
-----------
Everything a design writes is in **Bedrock model units** (16 units = 1 block), the same space as the
.geo.json file: y is up, the feet stand on y = 0, the golem's FRONT faces -z and its own LEFT side is +x.
All positions are given in the *bind pose* (every rotation zero); bone rotations then pose the model.

GeckoLib turns that into its own space by flipping x (cube x runs from -(origin.x + size.x) to -origin.x,
pivots become (-x, y, z), rotations (-x, -y, z)), and the entity renderer then turns the model by
(180 - body yaw) degrees about y. At yaw 0 (facing south, +z) a Bedrock point (x, y, z) therefore lands at
world (x, y, -z). `bedrock()` and `world()` below convert GeckoLib-space points back.

Units inside the maths are blocks (GeckoLib divides by 16), so a pose matrix maps GeckoLib-space block
coordinates of a bone to model (pre-yaw) block coordinates.
"""
import math

import numpy as np

# The six faces of a box, in the order GeckoLib builds them (BakedModelFactory.buildQuads).
FACES = ("west", "east", "north", "south", "up", "down")
# Outward normals of those faces in GeckoLib space (Direction.step()).
FACE_NORMALS = {
    "west": (-1.0, 0.0, 0.0), "east": (1.0, 0.0, 0.0),
    "north": (0.0, 0.0, -1.0), "south": (0.0, 0.0, 1.0),
    "up": (0.0, 1.0, 0.0), "down": (0.0, -1.0, 0.0),
}


# ---------------------------------------------------------------- small maths


def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=float)


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=float)


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=float)


def translate(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def scale(x, y, z):
    return np.diag([x, y, z, 1.0])


def gecko_rotation(rx_deg, ry_deg, rz_deg):
    """
    The rotation GeckoLib applies for Bedrock angles (degrees): it stores (-x, -y, z) in radians and
    multiplies Z, then Y, then X onto the pose stack (RenderUtils.rotateMatrixAroundBone), so M = Rz Ry Rx.
    """
    return rot_z(math.radians(rz_deg)) @ rot_y(math.radians(-ry_deg)) @ rot_x(math.radians(-rx_deg))


def bedrock(p):
    """A GeckoLib-space point (blocks) as a Bedrock model point (units)."""
    return np.array([-p[0] * 16.0, p[1] * 16.0, p[2] * 16.0])


def gecko(p):
    """A Bedrock model point (units) as a GeckoLib-space point (blocks)."""
    return np.array([-p[0] / 16.0, p[1] / 16.0, p[2] / 16.0])


def world(p, yaw_deg=0.0):
    """A GeckoLib model-space point (blocks) in world space for an entity with this body yaw."""
    m = rot_y(math.radians(180.0 - yaw_deg))
    q = m @ np.array([p[0], p[1], p[2], 1.0])
    return q[:3]


def axis_angle(axis, ang):
    """3x3 rotation by `ang` radians about a unit axis."""
    x, y, z = axis
    c, s_ = math.cos(ang), math.sin(ang)
    C = 1 - c
    return np.array([[c + x * x * C, x * y * C - z * s_, x * z * C + y * s_],
                     [y * x * C + z * s_, c + y * y * C, y * z * C - x * s_],
                     [z * x * C - y * s_, z * y * C + x * s_, c + z * z * C]])


def euler_bedrock(R, prev=None):
    """
    Bedrock (x, y, z) degrees whose GeckoLib rotation Rz(z) Ry(-y) Rx(-x) equals the 3x3 matrix R (GeckoLib space).
    Kept continuous with the previous frame's angles when `prev` is given.
    """
    sb = -R[2, 0]
    sb = max(-1.0, min(1.0, sb))
    beta = math.asin(sb)
    if abs(math.cos(beta)) > 1e-6:
        alpha = math.atan2(R[2, 1], R[2, 2])
        gamma = math.atan2(R[1, 0], R[0, 0])
    else:
        alpha = math.atan2(-R[1, 2], R[1, 1])
        gamma = 0.0
    e = [-math.degrees(alpha), -math.degrees(beta), math.degrees(gamma)]
    if prev is not None:
        for i in range(3):
            while e[i] - prev[i] > 180.0:
                e[i] -= 360.0
            while e[i] - prev[i] < -180.0:
                e[i] += 360.0
    return e


def aim(direction, twist=0.0):
    """
    Bedrock rotation (degrees) that turns a bone's (or a box's) +y towards `direction` (a Bedrock vector in its
    parent's bind frame) by the shortest turn, then twists it `twist` degrees about that direction.
    aim((0, 0, 1)) lays a part flat along +z: out of the golem's back when it hangs on the torso.
    """
    d = np.array([-direction[0], direction[1], direction[2]], dtype=float)   # GeckoLib space flips x
    d /= np.linalg.norm(d)
    y = np.array([0.0, 1.0, 0.0])
    axis = np.cross(y, d)
    s_ = np.linalg.norm(axis)
    c = float(np.dot(y, d))
    if s_ < 1e-9:
        R = np.eye(3) if c > 0 else axis_angle((1.0, 0.0, 0.0), math.pi)
    else:
        R = axis_angle(axis / s_, math.atan2(s_, c))
    if twist:
        R = axis_angle(d, math.radians(twist)) @ R
    return tuple(round(v, 4) for v in euler_bedrock(R))


# ---------------------------------------------------------------- model


class Cube:
    """One box. origin and size in Bedrock units (bind pose). size must be whole numbers (box UV)."""

    def __init__(self, origin, size, mat, rotation=None, pivot=None, inflate=0.0, tag=None):
        self.origin = tuple(float(v) for v in origin)
        if any(abs(v - round(v)) > 1e-9 or v < 1 for v in size):
            raise ValueError(f"box sizes must be whole numbers of at least 1, got {size}")
        self.size = tuple(int(round(v)) for v in size)
        self.mat = mat
        self.rotation = tuple(float(v) for v in rotation) if rotation else None
        self.pivot = tuple(float(v) for v in pivot) if pivot else None
        self.inflate = float(inflate)
        self.tag = tag
        self.uv = None
        self.bone = None
        self.index = -1

    @property
    def uv_size(self):
        w, h, d = self.size
        return 2 * (d + w), d + h

    def center(self):
        return tuple(self.origin[i] + self.size[i] / 2.0 for i in range(3))

    def quads(self, tex_w, tex_h):
        """
        The six faces exactly as GeckoLib bakes them (BakedModelFactory + GeoQuad.build, box UV, no mirror):
        [(face, [4 vertices in GeckoLib space (blocks)], [4 (u, v) in texels], normal)].
        Without the cube's own rotation (see cube_matrix).
        """
        ox, oy, oz = self.origin
        w, h, d = self.size
        i = self.inflate / 16.0
        x0, x1 = -(ox + w) / 16.0 - i, -ox / 16.0 + i
        y0, y1 = oy / 16.0 - i, (oy + h) / 16.0 + i
        z0, z1 = oz / 16.0 - i, (oz + d) / 16.0 + i
        blb = (x0, y0, z0)   # bottomLeftBack
        brb = (x0, y0, z1)   # bottomRightBack
        tlb = (x0, y1, z0)   # topLeftBack
        trb = (x0, y1, z1)   # topRightBack
        tlf = (x1, y1, z0)   # topLeftFront
        trf = (x1, y1, z1)   # topRightFront
        blf = (x1, y0, z0)   # bottomLeftFront
        brf = (x1, y0, z1)   # bottomRightFront
        verts = {
            "west": [trb, tlb, blb, brb],
            "east": [tlf, trf, brf, blf],
            "north": [tlb, tlf, blf, blb],
            "south": [trf, trb, brb, brf],
            "up": [trb, trf, tlf, tlb],
            "down": [blb, blf, brf, brb],
        }
        u, v = self.uv
        rects = {
            "west": (u + d + w, v + d, d, h),
            "east": (u, v + d, d, h),
            "north": (u + d, v + d, w, h),
            "south": (u + d + w + d, v + d, w, h),
            "up": (u + d, v, w, d),
            "down": (u + d + w, v + d, w, -d),
        }
        out = []
        for face in FACES:
            u0, v0, us, vs = rects[face]
            # GeoQuad.build without mirroring swaps the u ends: vertex 0 gets the far u.
            uvs = [(u0 + us, v0), (u0, v0), (u0, v0 + vs), (u0 + us, v0 + vs)]
            out.append((face, [np.array(p, dtype=float) for p in verts[face]], uvs, FACE_NORMALS[face]))
        return out

    def cube_matrix(self):
        """The cube's own rotation about its pivot (RenderUtils.translateToPivotPoint/rotateMatrixAroundCube)."""
        if not self.rotation or not any(self.rotation):
            return np.eye(4)
        px, py, pz = self.pivot if self.pivot else self.center()
        p = (-px / 16.0, py / 16.0, pz / 16.0)
        return translate(*p) @ gecko_rotation(*self.rotation) @ translate(-p[0], -p[1], -p[2])


class Bone:
    """
    A bone: a pivot (Bedrock units, bind pose), a rest rotation (Bedrock degrees), boxes and child bones.
    kind: "core" (the lava body, always drawn), "shell" (the rock armour, drawn in the shell form) or
    "molten" (lava extras drawn only in the core form).
    """

    def __init__(self, model, name, pivot, rotation=(0.0, 0.0, 0.0), parent=None, kind="core"):
        self.model = model
        self.name = name
        self.pivot = tuple(float(v) for v in pivot)
        self.rotation = tuple(float(v) for v in rotation)
        self.parent = parent
        self.kind = kind
        self.cubes = []
        self.children = []
        if parent is not None:
            parent.children.append(self)

    def add(self, rel_origin, size, mat, **kw):
        """A box whose origin is given relative to this bone's pivot."""
        origin = tuple(self.pivot[i] + rel_origin[i] for i in range(3))
        return self._add(Cube(origin, size, mat, **kw))

    def add_c(self, rel_center, size, mat, **kw):
        """A box centred on a point given relative to this bone's pivot."""
        origin = tuple(self.pivot[i] + rel_center[i] - size[i] / 2.0 for i in range(3))
        if "rotation" in kw and kw["rotation"] and "pivot" not in kw:
            kw["pivot"] = tuple(self.pivot[i] + rel_center[i] for i in range(3))
        return self._add(Cube(origin, size, mat, **kw))

    def add_abs(self, origin, size, mat, **kw):
        """A box at an absolute (bind pose) origin."""
        return self._add(Cube(origin, size, mat, **kw))

    def _add(self, cube):
        cube.bone = self
        self.cubes.append(cube)
        return cube

    def local_matrix(self, rot=(0.0, 0.0, 0.0), pos=(0.0, 0.0, 0.0), scl=(1.0, 1.0, 1.0)):
        """
        GeckoLib's RenderUtils.prepMatrixForBone for this bone with these animation values added
        (rotation in Bedrock degrees on top of the rest rotation, position in units, scale).
        """
        px, py, pz = self.pivot
        p = (-px / 16.0, py / 16.0, pz / 16.0)
        r = gecko_rotation(self.rotation[0] + rot[0], self.rotation[1] + rot[1], self.rotation[2] + rot[2])
        return (translate(-pos[0] / 16.0, pos[1] / 16.0, pos[2] / 16.0) @ translate(*p) @ r
                @ scale(*scl) @ translate(-p[0], -p[1], -p[2]))

    def ancestors(self):
        chain = []
        b = self
        while b is not None:
            chain.append(b)
            b = b.parent
        return chain[::-1]


class Model:
    def __init__(self, identifier):
        self.identifier = identifier
        self.bones = []
        self.by_name = {}
        self.tex_w = 0
        self.tex_h = 0

    def bone(self, name, pivot, rotation=(0.0, 0.0, 0.0), parent=None, kind=None):
        if name in self.by_name:
            raise ValueError(f"duplicate bone name {name}")
        if isinstance(parent, str):
            parent = self.by_name[parent]
        if kind is None:
            kind = parent.kind if parent is not None else "core"
        b = Bone(self, name, pivot, rotation, parent, kind)
        self.bones.append(b)
        self.by_name[name] = b
        return b

    def __getitem__(self, name):
        return self.by_name[name]

    def cubes(self):
        out = []
        for b in self.bones:
            out.extend(b.cubes)
        return out

    # ------------------------------------------------------------ box UV packing

    def pack_uv(self, width=512, pad=0):
        """
        Gives every box its own box-UV area (2(d+w) x (d+h) texels) with a skyline packer, tallest first.
        Sets tex_w / tex_h (height rounded up to a power of two).
        """
        cubes = self.cubes()
        for i, c in enumerate(cubes):
            c.index = i
        order = sorted(cubes, key=lambda c: (-c.uv_size[1], -c.uv_size[0]))
        skyline = [0] * width
        for c in order:
            w, h = c.uv_size
            w += pad
            h += pad
            if w > width:
                raise ValueError(f"box {c.size} on {c.bone.name} is too wide for a {width} texture")
            best = None
            for x in range(0, width - w + 1):
                y = max(skyline[x:x + w])
                if best is None or y < best[1] or (y == best[1] and x < best[0]):
                    best = (x, y)
            x, y = best
            c.uv = (x, y)
            for k in range(x, x + w):
                skyline[k] = y + h
        used_h = max(skyline)
        tex_h = 16
        while tex_h < used_h:
            tex_h *= 2
        self.tex_w, self.tex_h = width, tex_h
        return width, tex_h

    # ------------------------------------------------------------ forward kinematics

    def pose_matrices(self, pose=None, yaw_deg=None):
        """
        Every bone's pose matrix for a pose {bone name: (rot, pos, scale)} of animation offsets
        (missing bones stay at rest). Maps the bone's GeckoLib-space coordinates (blocks) to model space;
        with yaw_deg given, to world space for an entity with that body yaw instead.
        """
        pose = pose or {}
        base = np.eye(4) if yaw_deg is None else rot_y(math.radians(180.0 - yaw_deg))
        mats = {}
        for b in self.bones:  # parents always come before children
            parent = mats[b.parent.name] if b.parent is not None else base
            rot, pos, scl = pose.get(b.name, ((0, 0, 0), (0, 0, 0), (1, 1, 1)))
            mats[b.name] = parent @ b.local_matrix(rot, pos, scl)
        return mats

    def point(self, mats, bone_name, bedrock_point):
        """A bind-pose Bedrock point carried by a bone, after posing, as a Bedrock model point."""
        q = mats[bone_name] @ np.append(gecko(bedrock_point), 1.0)
        return bedrock(q[:3])

    def direction(self, mats, bone_name, bedrock_dir):
        """A bind-pose Bedrock direction carried by a bone, after posing, as a Bedrock direction."""
        d = np.array([-bedrock_dir[0], bedrock_dir[1], bedrock_dir[2]], dtype=float)
        q = mats[bone_name][:3, :3] @ d
        return np.array([-q[0], q[1], q[2]])

    def bounds(self, mats, kinds=("core", "shell")):
        """Bedrock bounding box (min, max) of every box of these kinds after posing."""
        pts = []
        for b in self.bones:
            if b.kind not in kinds:
                continue
            m = mats[b.name]
            for c in b.cubes:
                cm = m @ c.cube_matrix()
                ox, oy, oz = c.origin
                w, h, d = c.size
                for dx in (0, w):
                    for dy in (0, h):
                        for dz in (0, d):
                            q = cm @ np.append(gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                            pts.append(bedrock(q[:3]))
        pts = np.array(pts)
        return pts.min(axis=0), pts.max(axis=0)

    # ------------------------------------------------------------ export

    def to_json(self, visible_bounds=(6.0, 6.0, 2.5)):
        bones = []
        for b in self.bones:
            entry = {"name": b.name}
            if b.parent is not None:
                entry["parent"] = b.parent.name
            entry["pivot"] = [_num(v) for v in b.pivot]
            if any(abs(v) > 1e-9 for v in b.rotation):
                entry["rotation"] = [_num(v) for v in b.rotation]
            cubes = []
            for c in b.cubes:
                ce = {"origin": [_num(v) for v in c.origin], "size": list(c.size), "uv": list(c.uv)}
                if c.inflate:
                    ce["inflate"] = _num(c.inflate)
                if c.rotation and any(c.rotation):
                    ce["pivot"] = [_num(v) for v in (c.pivot if c.pivot else c.center())]
                    ce["rotation"] = [_num(v) for v in c.rotation]
                cubes.append(ce)
            if cubes:
                entry["cubes"] = cubes
            bones.append(entry)
        vw, vh, voff = visible_bounds
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": "geometry." + self.identifier,
                    "texture_width": self.tex_w,
                    "texture_height": self.tex_h,
                    "visible_bounds_width": vw,
                    "visible_bounds_height": vh,
                    "visible_bounds_offset": [0, voff, 0],
                },
                "bones": bones,
            }],
        }


def _num(v):
    """Whole numbers as ints, others rounded to 4 decimals (keeps the JSON small and readable)."""
    r = round(float(v), 4)
    return int(r) if abs(r - round(r)) < 1e-9 else r
