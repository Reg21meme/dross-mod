"""
A small software renderer for the golem models: the same geometry, UVs and bone maths as GeckoLib
(see geo.py), drawn with a z-buffer as an orthographic view, lit like Minecraft lights entities.

Layers, like the mod's renderer (boss/golem/client):
  1. base    - the main texture, cutout (alpha under 0.1 is a hole), lit by the face direction
               (Minecraft's two entity lights: ambient 0.4 + 0.6 x light) and by the world light;
  2. glow    - the *_glow texture on the same visible surfaces: lit by the face direction only (no world
               light: it glows in the dark), blended by its alpha times the glow strength;
  3. statue  - the *_statue texture blended on top by the statue strength (the death "cooling").
"""
import math

import numpy as np
from PIL import Image

from . import geo

# Minecraft's entity lights (Lighting.DIFFUSE_LIGHT_0 / _1), world space, and the shader's mix.
_LIGHTS = [np.array(v, dtype=float) / np.linalg.norm(v) for v in ((0.2, 1.0, -0.7), (-0.2, 1.0, 0.7))]
_AMBIENT = 0.4
_POWER = 0.6
# The glow layer bends every normal halfway towards "up" before lighting (the mod's GolemGlowLayer does the
# same), so glowing lava stays bright from the sides instead of dropping to half brightness.
GLOW_NORMAL_LIFT = 0.5


def diffuse(normal_world):
    n = np.asarray(normal_world, dtype=float)
    n = n / (np.linalg.norm(n) or 1.0)
    acc = sum(max(0.0, float(np.dot(l, n))) for l in _LIGHTS)
    return min(1.0, _AMBIENT + _POWER * acc)


def glow_diffuse(normal_world):
    n = np.asarray(normal_world, dtype=float)
    n = n / (np.linalg.norm(n) or 1.0)
    return diffuse(n + np.array([0.0, GLOW_NORMAL_LIFT, 0.0]))


def camera(yaw_deg, elevation_deg):
    """
    View rotation for a camera circling the model: yaw 0 looks at the golem's face (it faces +z world),
    90 at its left side (+x), 180 at its back. Elevation tips the view down onto it.
    Returns a 3x3 matrix world -> view (x right, y DOWN on screen, z away from the viewer).
    """
    a = math.radians(yaw_deg)
    # Camera position direction (from the model towards the camera).
    cam = np.array([math.sin(a), 0.0, math.cos(a)])
    forward = -cam                      # looking at the model
    up = np.array([0.0, 1.0, 0.0])
    right = np.cross(forward, up)
    right /= np.linalg.norm(right)
    e = math.radians(elevation_deg)
    # Tilt: rotate forward/up about the right axis so we look down by e.
    fwd = forward * math.cos(e) - up * math.sin(e)
    upv = up * math.cos(e) + forward * math.sin(e)
    return np.array([right, -upv, fwd])


class Visibility:
    """
    Which bones are drawn, by kind: shell plates (shell forms), molten extras (core form), vent bones (the core
    form's live eruption) and burst bones (only while it erupts). Same rules as CinderColossus.showsBone.
    """

    def __init__(self, shell=True, molten=False, vent=None, burst=False, hide=()):
        self.shell = shell
        self.molten = molten
        self.vent = molten if vent is None else vent
        self.burst = burst
        self.hide = set(hide)

    def __call__(self, bone):
        if bone.name in self.hide:
            return False
        for b in bone.ancestors():
            if b.kind == "shell" and not self.shell:
                return False
            if b.kind == "molten" and not self.molten:
                return False
            if b.kind == "vent" and not self.vent:
                return False
            if b.kind == "burst" and not self.burst:
                return False
        return True


def rasterize(model, mats, visible, view, scale, bounds):
    """
    Draws every visible face into buffers: depth, texel u/v and the face's light factor.
    bounds: (x0, y0, x1, y1) of the view window in blocks (view space, y down); scale: pixels per texel.
    """
    scale = scale * 16.0  # pixels per block
    bx0, by0, bx1, by1 = bounds
    width = int(round((bx1 - bx0) * scale))
    height = int(round((by1 - by0) * scale))
    depth = np.full((height, width), np.inf)
    tex_u = np.zeros((height, width), dtype=np.int32)
    tex_v = np.zeros((height, width), dtype=np.int32)
    light = np.zeros((height, width))
    glight = np.zeros((height, width))
    hit = np.zeros((height, width), dtype=bool)
    xs = bx0 + (np.arange(width) + 0.5) / scale
    ys = by0 + (np.arange(height) + 0.5) / scale
    for bone in model.bones:
        if not bone.cubes or not visible(bone):
            continue
        m = mats[bone.name]
        for cube in bone.cubes:
            cm = m @ cube.cube_matrix()
            rot = cm[:3, :3]
            for face, verts, uvs, normal in cube.quads(model.tex_w, model.tex_h):
                pw = [(cm @ np.append(v, 1.0))[:3] for v in verts]
                nw = rot @ np.array(normal)
                shade = diffuse(nw)
                gshade = glow_diffuse(nw)
                p = [view @ q for q in pw]
                p0, p1, p2 = p[0], p[1], p[2]
                e_s = p0 - p1
                e_t = p2 - p1
                det = e_s[0] * e_t[1] - e_s[1] * e_t[0]
                if abs(det) < 1e-12:
                    continue
                allx = [q[0] for q in p]
                ally = [q[1] for q in p]
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
                z = p1[2] + s * e_s[2] + t * e_t[2]
                region = depth[iy0:iy1, ix0:ix1]
                closer = inside & (z < region - 1e-7)
                if not closer.any():
                    continue
                (ua, va), (ub, vb), (uc, vc) = uvs[0], uvs[1], uvs[2]
                uu = ub + s * (ua - ub) + t * (uc - ub)
                vv = vb + s * (va - vb) + t * (vc - vb)
                umin, umax = min(ua, ub, uc), max(ua, ub, uc)
                vmin, vmax = min(va, vb, vc), max(va, vb, vc)
                tu = np.clip(np.floor(uu), umin, max(umin, umax - 1)).astype(np.int32)
                tv = np.clip(np.floor(vv), vmin, max(vmin, vmax - 1)).astype(np.int32)
                region[closer] = z[closer]
                tex_u[iy0:iy1, ix0:ix1][closer] = tu[closer]
                tex_v[iy0:iy1, ix0:ix1][closer] = tv[closer]
                light[iy0:iy1, ix0:ix1][closer] = shade
                glight[iy0:iy1, ix0:ix1][closer] = gshade
                hit[iy0:iy1, ix0:ix1][closer] = True
    return dict(depth=depth, u=tex_u, v=tex_v, light=light, glight=glight, hit=hit, width=width, height=height)


def composite(buf, base, glow=None, statue=None, glow_strength=1.0, statue_strength=0.0, world_light=1.0,
              background=None, flow=None, flow_progress=0.0, flow_dim=1.0):
    """
    Colours the rasterized buffers. base/glow/statue: HxWx4 uint8 texture arrays. Returns float RGB (0-255)
    and the coverage mask.
    """
    h, w = buf["height"], buf["width"]
    out = np.zeros((h, w, 3))
    if background is not None:
        out[:] = background
    hit = buf["hit"].copy()
    u, v, shade = buf["u"], buf["v"], buf["light"]
    texel = base[v, u].astype(float)
    hit &= texel[..., 3] >= 26  # cutout
    lit = texel[..., :3] * (shade * world_light)[..., None]
    col = lit
    if flow is not None and (flow_progress > 0 or flow_dim >= 1.0):
        # The lava flows, as the mod draws them with Minecraft's dissolve shader (rendertype_entity_alpha): a texel is
        # dropped when its alpha is below the vertex alpha (1 - 0.25 p), the rest is unlit and blended by its alpha.
        # At p = 0 only the fully opaque texels show: the crater pools, always molten.
        # While cooling (flow_dim < 1) the mod draws them with the emissive layer instead, fading out.
        f = flow[v, u].astype(float)
        fa = f[..., 3] / 255.0
        if flow_dim >= 1.0:
            va = round(255.0 * (1.0 - 0.25 * min(1.0, flow_progress))) / 255.0
            show = (fa >= va - 1e-6) & (fa > 0)
            a = np.where(show, fa, 0.0)
            col = f[..., :3] * a[..., None] + col * (1.0 - a[..., None])
        else:
            a = np.where(fa >= 0.1, fa * flow_dim, 0.0)
            col = f[..., :3] * buf["glight"][..., None] * a[..., None] + col * (1.0 - a[..., None])
    if glow is not None and glow_strength > 0:
        g = glow[v, u].astype(float)
        a = (g[..., 3] / 255.0) * glow_strength
        a = np.where(a < 0.1, 0.0, a)
        gc = g[..., :3] * buf["glight"][..., None]
        col = gc * a[..., None] + col * (1.0 - a[..., None])
    if statue is not None and statue_strength > 0:
        st = statue[v, u].astype(float)
        a = (st[..., 3] / 255.0) * statue_strength
        a = np.where(a < 0.1, 0.0, a)
        sc = st[..., :3] * (shade * world_light)[..., None]
        col = sc * a[..., None] + col * (1.0 - a[..., None])
    out[hit] = col[hit]
    return out, hit


def view_bounds(model, mats, visible, view, margin=0.4):
    """The view-space rectangle (blocks) that holds every visible box, plus a margin."""
    pts = []
    for bone in model.bones:
        if not bone.cubes or not visible(bone):
            continue
        m = mats[bone.name]
        for cube in bone.cubes:
            cm = m @ cube.cube_matrix()
            ox, oy, oz = cube.origin
            sx, sy, sz = cube.size
            for dx in (0, sx):
                for dy in (0, sy):
                    for dz in (0, sz):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        pts.append(view @ q[:3])
    pts = np.array(pts)
    return (pts[:, 0].min() - margin, pts[:, 1].min() - margin, pts[:, 0].max() + margin, pts[:, 1].max() + margin)


def ground_view_y(view, y_world=0.0):
    """Screen y (view space, blocks) of the ground level directly under the model's origin."""
    return (view @ np.array([0.0, y_world, 0.0]))[1]


def render(model, textures, pose=None, visible=None, yaw=0.0, elevation=12.0, scale=8, bounds=None,
           glow_strength=1.0, statue_strength=0.0, world_light=1.0, background=(46, 40, 38), grid=True,
           figure=True, flow_progress=0.0, flow_dim=1.0):
    """
    One picture of the model. textures: dict with "base", "glow", "statue" HxWx4 arrays (glow/statue optional).
    yaw: 0 front, 90 left side, 180 back. scale: pixels per texel. bounds: view window in blocks (fits the
    model when None).
    figure: draw a player-sized silhouette (1.8 blocks) beside the golem for scale.
    """
    visible = visible or Visibility()
    mats = model.pose_matrices(pose, yaw_deg=0.0)
    view = camera(yaw, elevation)
    if bounds is None:
        bounds = view_bounds(model, mats, visible, view)
        if figure:
            # Room on the left for the player silhouette.
            bounds = (bounds[0] - 1.1, bounds[1], bounds[2], bounds[3])
    buf = rasterize(model, mats, visible, view, scale, bounds)
    h, w = buf["height"], buf["width"]
    bg = np.zeros((h, w, 3))
    bg[:] = background
    if grid:
        _draw_grid(bg, view, bounds, scale * 16.0, background)
    if figure:
        _draw_figure(bg, view, bounds, scale * 16.0, background)
    col, hit = composite(buf, textures["base"], textures.get("glow"), textures.get("statue"),
                         glow_strength=glow_strength, statue_strength=statue_strength, world_light=world_light,
                         flow=textures.get("flow"), flow_progress=flow_progress, flow_dim=flow_dim)
    bg[hit] = col[hit]
    return Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB")


def _draw_grid(img, view, bounds, scale, background):
    """Faint one-block grid on the vertical plane through the model, and a stronger ground line."""
    bx0, by0, bx1, by1 = bounds
    h, w = img.shape[:2]
    line = np.array(background, dtype=float) * 1.18 + 6
    ground = np.array(background, dtype=float) * 1.6 + 10
    # Screen-space height of one block on the vertical axis (view tilts it a little).
    up = view @ np.array([0.0, 1.0, 0.0])
    gy0 = ground_view_y(view)
    for k in range(-2, 12):
        y = gy0 + up[1] * k
        py = int(round((y - by0) * scale))
        if 0 <= py < h:
            img[py, :] = ground if k == 0 else line
    # Vertical lines every block across the screen.
    x = math.floor(bx0)
    while x <= bx1:
        px = int(round((x - bx0) * scale))
        if 0 <= px < w:
            col = img[:, px]
            mask = np.abs(col - np.array(background, dtype=float)).sum(axis=1) < 1
            col[mask] = line
        x += 1


def _draw_figure(img, view, bounds, scale, background):
    """A dark player silhouette (Steve's proportions, 2 blocks tall incl. head) to the left of the golem."""
    bx0, by0, bx1, by1 = bounds
    h, w = img.shape[:2]
    gy = ground_view_y(view)
    up = (view @ np.array([0.0, 1.0, 0.0]))[1]  # screen y per block upwards (negative)
    # Place it a little inside the left edge.
    cx = bx0 + 0.6
    parts = [  # (x0, x1, y0, y1) in model pixels: legs, body, arms, head
        (-4, 0, 0, 12), (0, 4, 0, 12), (-4, 4, 12, 24), (-8, -4, 12, 24), (4, 8, 12, 24), (-4, 4, 24, 32)]
    color = np.array(background, dtype=float) * 0.55
    for x0, x1, y0, y1 in parts:
        sx0 = int(round((cx + x0 / 16.0 - bx0) * scale))
        sx1 = int(round((cx + x1 / 16.0 - bx0) * scale))
        sy0 = int(round((gy + up * (y1 / 16.0) - by0) * scale))
        sy1 = int(round((gy + up * (y0 / 16.0) - by0) * scale))
        sx0, sx1 = max(0, sx0), min(w, sx1)
        sy0, sy1 = max(0, sy0), min(h, sy1)
        if sx0 < sx1 and sy0 < sy1:
            img[sy0:sy1, sx0:sx1] = color
