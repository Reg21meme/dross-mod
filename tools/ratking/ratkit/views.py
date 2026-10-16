"""
Preview pictures for the Rat King and the rats (rat-previews/), drawn with the golem kit's renderer maths
(tools/golems/kit/render.py: GeckoLib's geometry, UVs and Minecraft's entity lighting), with one addition: anything
below the ground is not drawn, as in the game, where the ground hides it. That is what makes burrowing, popping up
and the dirt hole look right.

  ratking_<N>_<id>_<front|side|back>.png   one design standing in its idle pose
  ratking_<N>_<id>_sheet.png               one design: four views, at night (to see what glows), and a filmstrip of
                                           every animation
  ratking_all.png                          the six designs side by side
  rat_<label>_<id>_<front|side|back>.png   one rat
  rats_sheet.png                           every rat: four views, at night, and its idle and run animations
"""
import math
import os

import numpy as np
from PIL import Image, ImageDraw

from kit import geo, render
from kit.previews import font

BG = (46, 40, 38)
SHEET_BG = (22, 21, 20)
NIGHT_BG = (14, 13, 18)
NIGHT_LIGHT = 0.18
TEXT = (240, 236, 226)
ACCENT = (150, 214, 92)      # poison green
DIM = (170, 160, 150)
SCALE = 6                    # pixels per texel in the single views


def everything(_bone):
    return True


def rasterize(model, mats, visible, view, scale, bounds, floor=0.0):
    """kit.render.rasterize, but pixels whose world point lies below `floor` (blocks) are not drawn."""
    scale = scale * 16.0
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
                if max(q[1] for q in pw) < floor - 1e-6:
                    continue
                nw = rot @ np.array(normal)
                shade = render.diffuse(nw)
                gshade = render.glow_diffuse(nw)
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
                wy = pw[1][1] + s * (pw[0][1] - pw[1][1]) + t * (pw[2][1] - pw[1][1])
                inside &= wy >= floor - 1e-6
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
                # Cutout: a transparent texel doesn't hide what's behind it.
                region[closer] = z[closer]
                tex_u[iy0:iy1, ix0:ix1][closer] = tu[closer]
                tex_v[iy0:iy1, ix0:ix1][closer] = tv[closer]
                light[iy0:iy1, ix0:ix1][closer] = shade
                glight[iy0:iy1, ix0:ix1][closer] = gshade
                hit[iy0:iy1, ix0:ix1][closer] = True
    return dict(depth=depth, u=tex_u, v=tex_v, light=light, glight=glight, hit=hit, width=width, height=height)


def draw(model, textures, pose=None, yaw=0.0, elevation=10.0, scale=SCALE, bounds=None, visible=everything,
         glow_strength=1.0, rage_strength=0.0, world_light=1.0, background=BG, grid=True, figure=True, markers=()):
    """One picture. textures: {"base", "glow", "rage"} HxWx4 arrays. Cut-out texels are drawn in a second pass so
    whatever is behind a hole (a moth-eaten cape, a rounded ear) shows through."""
    mats = model.pose_matrices(pose, yaw_deg=0.0)
    view = render.camera(yaw, elevation)
    if bounds is None:
        bounds = render.view_bounds(model, mats, visible, view)
        if figure:
            bounds = (bounds[0] - 1.1, bounds[1], bounds[2], bounds[3])
    base = textures["base"]
    # Two passes: first only the opaque texels' faces matter for depth. A cheap way: draw, then redraw with the
    # transparent texels' depth removed (enough for thin capes and ears).
    buf = _raster_cutout(model, mats, visible, view, scale, bounds, base)
    h, w = buf["height"], buf["width"]
    bg = np.zeros((h, w, 3))
    bg[:] = background
    if grid:
        render._draw_grid(bg, view, bounds, scale * 16.0, background)
    if figure:
        render._draw_figure(bg, view, bounds, scale * 16.0, background)
    col, hit = render.composite(buf, base, textures.get("glow"), textures.get("rage"), glow_strength=glow_strength,
                                statue_strength=0.0, world_light=world_light)
    if rage_strength > 0 and textures.get("rage") is not None:
        r = textures["rage"][buf["v"], buf["u"]].astype(float)
        a = (r[..., 3] / 255.0) * rage_strength
        a = np.where(a < 0.1, 0.0, a)
        rc = r[..., :3] * buf["glight"][..., None]
        col = rc * a[..., None] + col * (1.0 - a[..., None])
    bg[hit] = col[hit]
    im = Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8), "RGB")
    if markers:
        _draw_markers(im, model, mats, view, bounds, scale * 16.0, markers)
    return im


def _draw_markers(im, model, mats, view, bounds, scale, markers):
    """
    The mod's particles, sketched where the game spawns them: ("zzz", phase) floats three Z's up from the `zzz`
    marker; ("steam", k) blows puffs out of both ears along their aim bones (k: 0..1 how far along the puff is).
    """
    dr = ImageDraw.Draw(im, "RGBA")
    bx0, by0, _bx1, _by1 = bounds

    def proj(bone, rel=(0.0, 0.0, 0.0)):
        b = model[bone]
        pt = (b.pivot[0] + rel[0], b.pivot[1] + rel[1], b.pivot[2] + rel[2])
        q = mats[bone] @ np.append(geo.gecko(pt), 1.0)
        v = view @ q[:3]
        return (v[0] - bx0) * scale, (v[1] - by0) * scale

    def proj_model(pt):
        """A model-space point (Bedrock units, his front -z) on the picture."""
        q = geo.world(geo.gecko(pt), 0.0)
        v = view @ q
        return (v[0] - bx0) * scale, (v[1] - by0) * scale

    for kind, k in markers:
        if kind == "trail":
            # The sweep's orb trail: a pale green swoosh, fading towards its tail.
            pts = [proj_model(q) for q in k]
            for i in range(1, len(pts)):
                u = i / (len(pts) - 1)
                w = max(1, int(round(scale * (0.04 + 0.12 * u))))
                dr.line([pts[i - 1], pts[i]], fill=(190, 250, 120, int(40 + 150 * u)), width=w)
        if kind == "ball":
            # The cast's poison ball: a clump of brownish-green lumps.
            x, y = proj_model(k)
            r = scale * 0.2             # about the real ball's size (0.6 blocks across)
            for dx, dy, f in ((0.0, 0.0, 1.0), (-0.55, -0.35, 0.7), (0.5, -0.45, 0.65), (0.45, 0.45, 0.6),
                              (-0.5, 0.4, 0.55)):
                cx, cy, rr = x + dx * r, y + dy * r, r * f
                dr.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=(98, 112, 44, 255), outline=(40, 46, 18, 255))
            dr.ellipse((x - r * 0.3, y - r * 0.3, x + r * 0.1, y + r * 0.1), fill=(170, 220, 90, 255))
        if kind == "zzz" and "zzz" in model.by_name:
            x, y = proj("zzz")
            for i in range(3):
                ph = (k + i / 3.0) % 1.0
                size = int(scale * (0.18 + 0.22 * ph))
                fx = x + scale * (0.35 * ph + 0.12 * math.sin(ph * 6.0))
                fy = y - scale * (0.15 + 0.9 * ph)
                a = int(255 * (1.0 - ph) ** 0.6)
                f = font(max(8, size))
                dr.text((fx + 1, fy + 1), "Z", font=f, fill=(20, 20, 40, a))
                dr.text((fx, fy), "Z", font=f, fill=(235, 240, 255, a))
        if kind == "steam":
            for side in ("l", "r"):
                name = f"ear_{side}_steam"
                if name not in model.by_name:
                    continue
                x0, y0 = proj(name)
                x1, y1 = proj(name + "_aim")
                dx, dy = x1 - x0, y1 - y0
                for j in range(4):
                    u = k * (0.4 + 0.25 * j)
                    r = scale * (0.05 + 0.09 * u + 0.02 * j)
                    cx, cy = x0 + dx * u * 3.0, y0 + dy * u * 3.0 - scale * 0.1 * u
                    a = int(220 * (1.0 - 0.6 * u))
                    dr.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(240, 240, 240, a))


def _raster_cutout(model, mats, visible, view, scale, bounds, base):
    """
    Rasterizes with cutout transparency: faces are drawn back to front per pixel by peeling. Simplified: rasterize
    once, find pixels whose nearest texel is transparent, and rasterize again without those faces' depth... Here we
    rasterize each bone's faces into a list and resolve per pixel with alpha.
    """
    layers = []
    buf = rasterize(model, mats, visible, view, scale, bounds)
    # Peel: up to 3 layers through transparent texels.
    for _ in range(3):
        alpha = base[buf["v"], buf["u"], 3]
        holes = buf["hit"] & (alpha < 26)
        layers.append(buf)
        if not holes.any():
            break
        buf = _rasterize_behind(model, mats, visible, view, scale, bounds, buf["depth"], holes)
    out = layers[0]
    for deeper in layers[1:]:
        alpha = base[out["v"], out["u"], 3]
        fill = out["hit"] & (alpha < 26)
        for k in ("u", "v", "light", "glight", "depth"):
            out[k] = np.where(fill, deeper[k], out[k])
        out["hit"] = np.where(fill, deeper["hit"], out["hit"])
    return out


def _rasterize_behind(model, mats, visible, view, scale, bounds, front_depth, mask, floor=0.0):
    """Like rasterize, but only keeps fragments behind front_depth at the masked pixels."""
    full = rasterize_masked(model, mats, visible, view, scale, bounds, front_depth, mask, floor)
    return full


def rasterize_masked(model, mats, visible, view, scale, bounds, front_depth, mask, floor=0.0):
    scale16 = scale * 16.0
    bx0, by0, bx1, by1 = bounds
    width = int(round((bx1 - bx0) * scale16))
    height = int(round((by1 - by0) * scale16))
    depth = np.full((height, width), np.inf)
    tex_u = np.zeros((height, width), dtype=np.int32)
    tex_v = np.zeros((height, width), dtype=np.int32)
    light = np.zeros((height, width))
    glight = np.zeros((height, width))
    hit = np.zeros((height, width), dtype=bool)
    xs = bx0 + (np.arange(width) + 0.5) / scale16
    ys = by0 + (np.arange(height) + 0.5) / scale16
    for bone in model.bones:
        if not bone.cubes or not visible(bone):
            continue
        m = mats[bone.name]
        for cube in bone.cubes:
            cm = m @ cube.cube_matrix()
            rot = cm[:3, :3]
            for face, verts, uvs, normal in cube.quads(model.tex_w, model.tex_h):
                pw = [(cm @ np.append(v, 1.0))[:3] for v in verts]
                if max(q[1] for q in pw) < floor - 1e-6:
                    continue
                nw = rot @ np.array(normal)
                p = [view @ q for q in pw]
                p0, p1, p2 = p[0], p[1], p[2]
                e_s = p0 - p1
                e_t = p2 - p1
                det = e_s[0] * e_t[1] - e_s[1] * e_t[0]
                if abs(det) < 1e-12:
                    continue
                allx = [q[0] for q in p]
                ally = [q[1] for q in p]
                ix0 = max(0, int(math.floor((min(allx) - bx0) * scale16)))
                ix1 = min(width, int(math.ceil((max(allx) - bx0) * scale16)) + 1)
                iy0 = max(0, int(math.floor((min(ally) - by0) * scale16)))
                iy1 = min(height, int(math.ceil((max(ally) - by0) * scale16)) + 1)
                if ix0 >= ix1 or iy0 >= iy1:
                    continue
                if not mask[iy0:iy1, ix0:ix1].any():
                    continue
                gx, gy = np.meshgrid(xs[ix0:ix1], ys[iy0:iy1])
                dx = gx - p1[0]
                dy = gy - p1[1]
                s = (dx * e_t[1] - dy * e_t[0]) / det
                t = (e_s[0] * dy - e_s[1] * dx) / det
                inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1) & mask[iy0:iy1, ix0:ix1]
                wy = pw[1][1] + s * (pw[0][1] - pw[1][1]) + t * (pw[2][1] - pw[1][1])
                inside &= wy >= floor - 1e-6
                z = p1[2] + s * e_s[2] + t * e_t[2]
                inside &= z > front_depth[iy0:iy1, ix0:ix1] + 1e-6
                if not inside.any():
                    continue
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
                light[iy0:iy1, ix0:ix1][closer] = render.diffuse(nw)
                glight[iy0:iy1, ix0:ix1][closer] = render.glow_diffuse(nw)
                hit[iy0:iy1, ix0:ix1][closer] = True
    return dict(depth=depth, u=tex_u, v=tex_v, light=light, glight=glight, hit=hit, width=width, height=height)


def fit(model, poses, yaw, elevation, margin=0.3, visible=everything, figure=True, floor=True):
    """A view window holding the model in every one of these poses (so a filmstrip doesn't jump)."""
    view = render.camera(yaw, elevation)
    boxes = []
    for pose in poses:
        mats = model.pose_matrices(pose, yaw_deg=0.0)
        boxes.append(render.view_bounds(model, mats, visible, view, margin))
    b = (min(x[0] for x in boxes), min(x[1] for x in boxes), max(x[2] for x in boxes), max(x[3] for x in boxes))
    if floor:
        gy = render.ground_view_y(view)
        b = (b[0], b[1], b[2], min(b[3], gy + 0.45))
    if figure:
        b = (b[0] - 1.1, b[1], b[2], b[3])
    return b


def label(im, text, size=18, color=TEXT):
    ImageDraw.Draw(im).text((8, 5), text, font=font(size), fill=color)
    return im


def grid(images, per_row, gap=8, bg=SHEET_BG):
    cw = max(i.width for i in images)
    ch = max(i.height for i in images)
    rows = (len(images) + per_row - 1) // per_row
    out = Image.new("RGB", (per_row * cw + (per_row - 1) * gap, rows * ch + (rows - 1) * gap), bg)
    for k, im in enumerate(images):
        out.paste(im, ((k % per_row) * (cw + gap), (k // per_row) * (ch + gap)))
    return out


def page(title, subtitle, parts, out_path, accent=ACCENT):
    """Stacks titled sections into one sheet."""
    width = max(p.width for _t, p in parts) + 40
    header = 92
    height = header + sum(p.height + 44 for _t, p in parts) + 16
    sheet = Image.new("RGB", (width, height), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((20, 14), title, font=font(36), fill=TEXT)
    dr.text((20, 58), subtitle, font=font(17, bold=False), fill=DIM)
    y = header
    for t, p in parts:
        dr.text((20, y + 4), t, font=font(22), fill=accent)
        y += 36
        sheet.paste(p, (20, y))
        y += p.height + 8
    sheet.save(out_path)
