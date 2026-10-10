"""
A tiny software renderer for Minecraft 1.20.1's VillagerModel (64x64 texture).

It builds every box of the model exactly like Minecraft does (ModelPart.Cube and ModelPart.Polygon:
the same vertices, the same UV corners, mirroring, inflation, part offsets and rotations), then draws
them with a z-buffer as an orthographic view from the front or the back. Faces are not culled, like the
game's "entity cutout no cull" render type, and fully transparent texels are skipped (cutout).

Used by draw_skins.py to make the skin previews. You can also run it on its own:

    python tools/skins/villager_render.py some_skin.png out_front.png out_back.png
"""
import math
import sys

import numpy as np
from PIL import Image

TEX_SIZE = 64


# ---------------------------------------------------------------- model geometry


class Cube:
    """One box: texOffs (u, v), origin and size in model pixels, inflation and the mirror flag."""

    def __init__(self, u, v, x, y, z, w, h, d, grow=0.0, mirror=False):
        self.u, self.v = u, v
        self.x, self.y, self.z = x, y, z
        self.w, self.h, self.d = w, h, d
        self.grow = grow
        self.mirror = mirror

    def polygons(self):
        """The six faces as (name, [(pos, (u, v)) x 4]) in part space. Same maths as ModelPart.Cube."""
        x0, y0, z0 = self.x, self.y, self.z
        x1, y1, z1 = x0 + self.w, y0 + self.h, z0 + self.d
        g = self.grow
        x0 -= g
        y0 -= g
        z0 -= g
        x1 += g
        y1 += g
        z1 += g
        if self.mirror:
            x0, x1 = x1, x0
        v7 = (x0, y0, z0)
        v0 = (x1, y0, z0)
        v1 = (x1, y1, z0)
        v2 = (x0, y1, z0)
        v3 = (x0, y0, z1)
        v4 = (x1, y0, z1)
        v5 = (x1, y1, z1)
        v6 = (x0, y1, z1)
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        f4 = u
        f5 = u + d
        f6 = u + d + w
        f7 = u + d + w + w
        f8 = u + d + w + d
        f9 = u + d + w + d + w
        f10 = v
        f11 = v + d
        f12 = v + d + h
        faces = [
            # name     vertices          u1  v1   u2  v2
            ("down", [v4, v3, v7, v0], f5, f10, f6, f11),   # the visual TOP of the box (model y is down)
            ("up", [v1, v2, v6, v5], f6, f11, f7, f10),     # the visual BOTTOM of the box
            ("west", [v7, v3, v6, v2], f4, f11, f5, f12),   # model -x: the entity's right side
            ("north", [v0, v7, v2, v1], f5, f11, f6, f12),  # model -z: the front
            ("east", [v4, v0, v1, v5], f6, f11, f8, f12),   # model +x: the entity's left side
            ("south", [v3, v4, v5, v6], f8, f11, f9, f12),  # model +z: the back
        ]
        out = []
        for name, verts, u1, vv1, u2, vv2 in faces:
            # ModelPart.Polygon: vertex 0 -> (u2, v1), 1 -> (u1, v1), 2 -> (u1, v2), 3 -> (u2, v2).
            # (Mirroring then reverses the vertex order, which only changes the winding, not the UVs.)
            uvs = [(u2, vv1), (u1, vv1), (u1, vv2), (u2, vv2)]
            out.append((name, list(zip(verts, uvs))))
        return out


class Part:
    """A model part: offset (pivot), rotation (x, y, z radians), cubes and children."""

    def __init__(self, name, cubes, offset=(0, 0, 0), rotation=(0, 0, 0), children=()):
        self.name = name
        self.cubes = cubes
        self.offset = offset
        self.rotation = rotation
        self.children = list(children)

    def matrix(self):
        """translate(offset) * rotationZYX(z, y, x), like ModelPart.translateAndRotate."""
        rx, ry, rz = self.rotation
        cx, sx = math.cos(rx), math.sin(rx)
        cy, sy = math.cos(ry), math.sin(ry)
        cz, sz = math.cos(rz), math.sin(rz)
        mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
        my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
        m = np.eye(4)
        m[:3, :3] = mz @ my @ mx
        m[:3, 3] = self.offset
        return m


def villager_model():
    """VillagerModel.createBodyModel() for 1.20.1, standing still and looking straight ahead."""
    hat_rim = Part("hat_rim", [Cube(30, 47, -8, -8, -6, 16, 16, 1)], rotation=(-math.pi / 2, 0, 0))
    hat = Part("hat", [Cube(32, 0, -4, -10, -4, 8, 10, 8, grow=0.51)], children=[hat_rim])
    nose = Part("nose", [Cube(24, 0, -1, -1, -6, 2, 4, 2)], offset=(0, -2, 0))
    head = Part("head", [Cube(0, 0, -4, -10, -4, 8, 10, 8)], children=[hat, nose])
    jacket = Part("jacket", [Cube(0, 38, -4, 0, -3, 8, 20, 6, grow=0.5)])
    body = Part("body", [Cube(16, 20, -4, 0, -3, 8, 12, 6)], children=[jacket])
    arms = Part("arms", [
        Cube(44, 22, -8, -2, -2, 4, 8, 4),
        Cube(44, 22, 4, -2, -2, 4, 8, 4, mirror=True),
        Cube(40, 38, -4, 2, -2, 8, 4, 4),
    ], offset=(0, 3, -1), rotation=(-0.75, 0, 0))
    right_leg = Part("right_leg", [Cube(0, 22, -2, 0, -2, 4, 12, 4)], offset=(-2, 12, 0))
    left_leg = Part("left_leg", [Cube(0, 22, -2, 0, -2, 4, 12, 4, mirror=True)], offset=(2, 12, 0))
    return [head, body, arms, right_leg, left_leg]


def world_quads(parts, parent=np.eye(4)):
    """
    Every face of every part, in model space: (part name, face name, [(xyz, uv) x 4], box centre).
    The box centre is used to point each face's normal outwards.
    """
    quads = []
    for part in parts:
        m = parent @ part.matrix()
        for cube in part.cubes:
            faces = []
            corners = []
            for face, verts in cube.polygons():
                pts = []
                for pos, uv in verts:
                    p = m @ np.array([pos[0], pos[1], pos[2], 1.0])
                    pts.append((p[:3], uv))
                    corners.append(p[:3])
                faces.append((part.name, face, pts))
            centre = np.mean(corners, axis=0)
            quads.extend((name, face, pts, centre) for name, face, pts in faces)
        quads.extend(world_quads(part.children, m))
    return quads


# ---------------------------------------------------------------- drawing


def _view_matrix(back, elevation_deg, yaw_deg=0.0):
    """
    Model space (y down, the entity faces -z) to view space (x right, y down, z away from the viewer).
    yaw_deg turns him round before looking (positive turns his front towards the viewer's right).
    """
    yaw = math.radians(yaw_deg + (180.0 if back else 0.0))
    m = np.array([[math.cos(yaw), 0, -math.sin(yaw)], [0, 1, 0], [math.sin(yaw), 0, math.cos(yaw)]])
    a = math.radians(elevation_deg)  # tip his top towards the viewer: we look down on him a little
    tilt = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    return tilt @ m


# Light like the game's entity lighting (two lights, ambient + diffuse; one light comes from the front and one
# from the back, so front and back faces are lit the same, as in the game). View space for lighting:
# x right, y UP, z towards the viewer.
_LIGHTS = [np.array(l) / np.linalg.norm(l) for l in ((0.2, 1.0, 0.7), (-0.2, 1.0, -0.7))]
_AMBIENT = 0.58
_POWER = 0.6


def _brightness(normal_view):
    n = np.array([normal_view[0], -normal_view[1], -normal_view[2]])  # to y up, z towards the viewer
    n = n / (np.linalg.norm(n) or 1.0)
    acc = sum(max(0.0, float(np.dot(l, n))) for l in _LIGHTS)
    return min(1.0, _AMBIENT + _POWER * acc)


def render(texture, back=False, scale=12, elevation_deg=10.0, background=(58, 63, 74), margin=2.0,
           bounds=(-9.0, -12.0, 9.0, 24.5), yaw_deg=0.0):
    """
    Renders the villager model with this 64x64 RGBA texture (PIL image or numpy array).
    Orthographic, from the front (or the back), looking down on him by elevation_deg degrees.
    Each model pixel is `scale` screen pixels. Returns a PIL RGB image.
    """
    tex = np.asarray(texture.convert("RGBA") if isinstance(texture, Image.Image) else texture)
    view = _view_matrix(back, elevation_deg, yaw_deg)
    bx0, by0, bx1, by1 = bounds
    bx0 -= margin
    by0 -= margin
    bx1 += margin
    by1 += margin
    width = int(round((bx1 - bx0) * scale))
    height = int(round((by1 - by0) * scale))
    color = np.zeros((height, width, 3), dtype=np.float64)
    color[:, :] = background
    depth = np.full((height, width), np.inf)

    # Pixel centres in view-space units.
    xs = bx0 + (np.arange(width) + 0.5) / scale
    ys = by0 + (np.arange(height) + 0.5) / scale

    for part_name, face, pts, centre in world_quads(villager_model()):
        p = [view @ q[0] for q in pts]
        c = view @ centre
        uv = [q[1] for q in pts]
        p0, p1, p2 = p[0], p[1], p[2]
        e_s = p0 - p1  # along u (u1 -> u2)
        e_t = p2 - p1  # along v (v1 -> v2)
        det = e_s[0] * e_t[1] - e_s[1] * e_t[0]
        if abs(det) < 1e-9:
            continue  # seen exactly edge-on: covers no pixels
        normal = np.cross(e_s, e_t)
        face_centre = (p0 + p[2]) / 2.0
        if np.dot(normal, face_centre - c) < 0:
            normal = -normal  # point it out of the box
        shade = _brightness(normal)
        # Bounding box of the quad on screen.
        allx = [q[0] for q in p] + [p0[0] + p2[0] - p1[0]]
        ally = [q[1] for q in p] + [p0[1] + p2[1] - p1[1]]
        ix0 = max(0, int(math.floor((min(allx) - bx0) * scale)))
        ix1 = min(width, int(math.ceil((max(allx) - bx0) * scale)) + 1)
        iy0 = max(0, int(math.floor((min(ally) - by0) * scale)))
        iy1 = min(height, int(math.ceil((max(ally) - by0) * scale)) + 1)
        if ix0 >= ix1 or iy0 >= iy1:
            continue
        gx, gy = np.meshgrid(xs[ix0:ix1], ys[iy0:iy1])
        dx = gx - p1[0]
        dy = gy - p1[1]
        s = (dx * e_t[1] - dy * e_t[0]) / det
        t = (e_s[0] * dy - e_s[1] * dx) / det
        inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
        if not inside.any():
            continue
        u1, v1 = uv[1]
        u2, v2 = uv[3][0], uv[3][1]
        uu = u1 + s * (u2 - u1)
        vv = v1 + t * (v2 - v1)
        tu = np.clip(np.floor(uu), min(u1, u2), max(u1, u2) - 1).astype(int)
        tv = np.clip(np.floor(vv), min(v1, v2), max(v1, v2) - 1).astype(int)
        tu = np.clip(tu, 0, TEX_SIZE - 1)
        tv = np.clip(tv, 0, TEX_SIZE - 1)
        z = p1[2] + s * e_s[2] + t * e_t[2]
        texel = tex[tv, tu]
        visible = inside & (texel[..., 3] >= 26)  # cutout: alpha under 0.1 is discarded
        region_depth = depth[iy0:iy1, ix0:ix1]
        closer = visible & (z < region_depth - 1e-6)
        if not closer.any():
            continue
        region_color = color[iy0:iy1, ix0:ix1]
        region_color[closer] = texel[..., :3][closer] * shade
        region_depth[closer] = z[closer]

    return Image.fromarray(np.clip(color, 0, 255).astype(np.uint8), "RGB")


def main(argv):
    if len(argv) < 4:
        print(__doc__)
        return 1
    tex = Image.open(argv[1]).convert("RGBA")
    render(tex, back=False).save(argv[2])
    render(tex, back=True).save(argv[3])
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
