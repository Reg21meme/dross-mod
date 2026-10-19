"""
Preview pictures for the Rimefall candidates, drawn the way the mod draws them (kit/render.py's GeckoLib maths and
Minecraft's entity lighting), with the see-through ice added:

  1. the solid texture, cut-out (a z-buffer);
  2. its glow, full-bright, on the solid surfaces you see;
  3. the ice: every see-through texel in front of the solid surface, blended back to front;
  4. the ice's own glow on the nearest ice surface (the mod draws it with the ice's depth).

A dark figure the size of a player stands to the left for scale.
"""
import math

import numpy as np
from PIL import Image, ImageDraw

from kit import geo, render
from kit.previews import font

BG = (34, 40, 50)
SHEET_BG = (18, 22, 28)
NIGHT_BG = (10, 12, 20)
NIGHT_LIGHT = 0.2
TEXT = (236, 242, 248)
ACCENT = (150, 214, 236)     # pale ice blue
DIM = (150, 162, 176)
HOLE = 26


def _fragments(layers, view, scale, bounds):
    """
    Rasterizes every face of every (model, textures) layer into one picture. Returns the solid buffers (with the layer
    each pixel came from) and the list of ice fragments.
    """
    s16 = scale * 16.0
    bx0, by0, bx1, by1 = bounds
    width = int(round((bx1 - bx0) * s16))
    height = int(round((by1 - by0) * s16))
    depth = np.full((height, width), np.inf)
    tu_b = np.zeros((height, width), dtype=np.int32)
    tv_b = np.zeros((height, width), dtype=np.int32)
    layer_b = np.zeros((height, width), dtype=np.int32)
    light = np.zeros((height, width))
    glight = np.zeros((height, width))
    hit = np.zeros((height, width), dtype=bool)
    xs = bx0 + (np.arange(width) + 0.5) / s16
    ys = by0 + (np.arange(height) + 0.5) / s16
    ice_frags = []
    for li, (model, tex) in enumerate(layers):
        _raster(li, model, tex, view, s16, bounds, xs, ys, width, height, depth, tu_b, tv_b, layer_b, light, glight,
                hit, ice_frags)
    return dict(depth=depth, u=tu_b, v=tv_b, layer=layer_b, light=light, glight=glight, hit=hit, width=width,
                height=height, ice=ice_frags)


def _raster(li, model, tex, view, s16, bounds, xs, ys, width, height, depth, tu_b, tv_b, layer_b, light, glight, hit,
            ice_frags):
    bx0, by0, bx1, by1 = bounds
    mats = model.pose_matrices(None, yaw_deg=0.0)
    a_base = tex["base"][..., 3]
    a_ice = tex["ice"][..., 3]
    for bone in model.bones:
        if not bone.cubes:
            continue
        m = mats[bone.name]
        for cube in bone.cubes:
            cm = m @ cube.cube_matrix()
            rot = cm[:3, :3]
            for face, verts, uvs, normal in cube.quads(model.tex_w, model.tex_h):
                pw = [(cm @ np.append(v, 1.0))[:3] for v in verts]
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
                ix0 = max(0, int(math.floor((min(allx) - bx0) * s16)))
                ix1 = min(width, int(math.ceil((max(allx) - bx0) * s16)) + 1)
                iy0 = max(0, int(math.floor((min(ally) - by0) * s16)))
                iy1 = min(height, int(math.ceil((max(ally) - by0) * s16)) + 1)
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
                (ua, va), (ub, vb), (uc, vc) = uvs[0], uvs[1], uvs[2]
                uu = ub + s * (ua - ub) + t * (uc - ub)
                vv = vb + s * (va - vb) + t * (vc - vb)
                umin, umax = min(ua, ub, uc), max(ua, ub, uc)
                vmin, vmax = min(va, vb, vc), max(va, vb, vc)
                tu = np.clip(np.floor(uu), umin, max(umin, umax - 1)).astype(np.int32)
                tv = np.clip(np.floor(vv), vmin, max(vmin, vmax - 1)).astype(np.int32)
                solid = inside & (a_base[tv, tu] >= HOLE)
                region = depth[iy0:iy1, ix0:ix1]
                closer = solid & (z < region - 1e-7)
                if closer.any():
                    region[closer] = z[closer]
                    tu_b[iy0:iy1, ix0:ix1][closer] = tu[closer]
                    tv_b[iy0:iy1, ix0:ix1][closer] = tv[closer]
                    layer_b[iy0:iy1, ix0:ix1][closer] = li
                    light[iy0:iy1, ix0:ix1][closer] = shade
                    glight[iy0:iy1, ix0:ix1][closer] = gshade
                    hit[iy0:iy1, ix0:ix1][closer] = True
                icy = inside & (a_ice[tv, tu] >= HOLE)
                if icy.any():
                    yy, xx = np.nonzero(icy)
                    ice_frags.append(((yy + iy0) * width + (xx + ix0), z[icy], tu[icy], tv[icy],
                                      np.full(len(yy), shade), np.full(len(yy), gshade), np.full(len(yy), li)))


def draw(model, tex, yaw=30.0, elevation=12.0, scale=4.0, bounds=None, world_light=1.0, glow_strength=1.0,
         background=BG, grid=True, figure=True, extra=()):
    """
    One picture of a candidate (front faces the camera at yaw 0). `extra`: more (model, textures) drawn with it (its
    real Minecraft blocks).
    """
    layers = [(model, tex)] + [e for e in extra if e is not None]
    view = render.camera(yaw, elevation)
    if bounds is None:
        bounds = window(model, yaw, elevation, figure=figure, extra=[m for m, _t in layers[1:]])
    buf = _fragments(layers, view, scale, bounds)
    h, w = buf["height"], buf["width"]
    img = np.zeros((h, w, 3))
    img[:] = background
    if grid:
        render._draw_grid(img, view, bounds, scale * 16.0, background)
    if figure:
        render._draw_figure(img, view, bounds, scale * 16.0, background)
    flat = img.reshape(-1, 3)
    # 1-2: the solid parts and their glow.
    hit = buf["hit"]
    u, v, lay = buf["u"], buf["v"], buf["layer"]
    col = np.zeros((h, w, 3))
    for li, (_m, t) in enumerate(layers):
        sel = hit & (lay == li)
        if not sel.any():
            continue
        texel = t["base"][v[sel], u[sel]].astype(float)
        c = texel[:, :3] * (buf["light"][sel] * world_light)[:, None]
        g = t["glow"][v[sel], u[sel]].astype(float)
        ga = (g[:, 3] / 255.0) * glow_strength
        ga = np.where(ga < 0.1, 0.0, ga)
        col[sel] = g[:, :3] * buf["glight"][sel][:, None] * ga[:, None] + c * (1.0 - ga[:, None])
    img[hit] = col[hit]
    # 3-4: the ice, back to front, then the ice's glow on the nearest ice surface.
    if buf["ice"]:
        pix = np.concatenate([f[0] for f in buf["ice"]])
        z = np.concatenate([f[1] for f in buf["ice"]])
        tu = np.concatenate([f[2] for f in buf["ice"]])
        tv = np.concatenate([f[3] for f in buf["ice"]])
        sh = np.concatenate([f[4] for f in buf["ice"]])
        gsh = np.concatenate([f[5] for f in buf["ice"]])
        fl = np.concatenate([f[6] for f in buf["ice"]])
        front = buf["depth"].reshape(-1)[pix]
        keep = z < front - 1e-6
        pix, z, tu, tv, sh, gsh, fl = pix[keep], z[keep], tu[keep], tv[keep], sh[keep], gsh[keep], fl[keep]
        if len(pix):
            order = np.lexsort((-z, pix))             # by pixel, farthest first
            pix, z, tu, tv, sh, gsh, fl = pix[order], z[order], tu[order], tv[order], sh[order], gsh[order], fl[order]
            first = np.r_[True, pix[1:] != pix[:-1]]
            start = np.maximum.accumulate(np.where(first, np.arange(len(pix)), 0))
            rank = np.arange(len(pix)) - start

            def look(key, rows):
                out = np.zeros((int(rows.sum()), 4))
                for li, (_m, t) in enumerate(layers):
                    k = fl[rows] == li
                    if k.any():
                        out[k] = t[key][tv[rows][k], tu[rows][k]]
                return out

            for r in range(int(rank.max()) + 1):
                sel = rank == r
                p = pix[sel]
                c = look("ice", sel)
                a = (c[:, 3] / 255.0)[:, None]
                flat[p] = c[:, :3] * (sh[sel] * world_light)[:, None] * a + flat[p] * (1.0 - a)
            last = np.r_[pix[1:] != pix[:-1], True]   # the nearest fragment of each pixel
            p = pix[last]
            c = look("iceglow", last)
            a = (c[:, 3] / 255.0) * glow_strength
            a = np.where(a < 0.1, 0.0, a)[:, None]
            flat[p] = c[:, :3] * gsh[last][:, None] * a + flat[p] * (1.0 - a)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")


def corners(model):
    """Every box corner of the model (Bedrock units, posed)."""
    mats = model.pose_matrices()
    pts = []
    for b in model.bones:
        m = mats[b.name]
        for c in b.cubes:
            cm = m @ c.cube_matrix()
            ox, oy, oz = c.origin
            sx, sy, sz = c.size
            i = c.inflate
            for dx in (-i, sx + i):
                for dy in (-i, sy + i):
                    for dz in (-i, sz + i):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        pts.append(geo.bedrock(q[:3]))
    return np.array(pts)


def window(model, yaw, elevation, margin=0.35, figure=True, ground=True, extra=()):
    """The view window (blocks, view space) that holds the model (and the `extra` models), the ground under it and a
    player figure."""
    view = render.camera(yaw, elevation)
    pts = np.concatenate([corners(model)] + [corners(e) for e in extra])
    g = np.stack([-pts[:, 0] / 16.0, pts[:, 1] / 16.0, pts[:, 2] / 16.0], axis=1)
    # GeckoLib model space -> world at yaw 0 (the camera maths uses world space).
    wpts = np.stack([-g[:, 0], g[:, 1], -g[:, 2]], axis=1)
    v = wpts @ view.T
    x0, y0, x1, y1 = v[:, 0].min(), v[:, 1].min(), v[:, 0].max(), v[:, 1].max()
    if ground:
        gy = render.ground_view_y(view)
        y1 = max(y1, gy + 0.15)
    if figure:
        x0 -= 1.1
        y0 = min(y0, render.ground_view_y(view) + (view @ np.array([0.0, 2.1, 0.0]))[1])
    return (x0 - margin, y0 - margin, x1 + margin, y1 + margin)


def label(im, text, size=18, color=TEXT, y=5):
    ImageDraw.Draw(im).text((8, y), text, font=font(size), fill=color)
    return im


def grid(images, per_row, gap=8, bg=SHEET_BG, align_bottom=True):
    cw = max(i.width for i in images)
    ch = max(i.height for i in images)
    rows = (len(images) + per_row - 1) // per_row
    out = Image.new("RGB", (per_row * cw + (per_row - 1) * gap, rows * ch + (rows - 1) * gap), bg)
    for k, im in enumerate(images):
        x = (k % per_row) * (cw + gap) + (cw - im.width) // 2
        y = (k // per_row) * (ch + gap) + ((ch - im.height) if align_bottom else 0)
        out.paste(im, (x, y))
    return out


def page(title, subtitle, parts, out_path, accent=ACCENT):
    """Stacks titled sections into one sheet."""
    width = max(max(p.width for _t, p in parts) + 40, 900)
    # The subtitle wrapped to the page's width.
    f = font(17, bold=False)
    probe = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    lines = []
    line = ""
    for word in subtitle.split(" "):
        test = (line + " " + word) if line else word
        if line and probe.textlength(test, font=f) > width - 40:
            lines.append(line)
            line = word
        else:
            line = test
    if line:
        lines.append(line)
    header = 70 + 22 * len(lines)
    height = header + sum(p.height + 44 for _t, p in parts) + 16
    sheet = Image.new("RGB", (width, height), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((20, 14), title, font=font(34), fill=TEXT)
    for k, text in enumerate(lines):
        dr.text((20, 58 + 22 * k), text, font=f, fill=DIM)
    y = header
    for t, p in parts:
        dr.text((20, y + 4), t, font=font(22), fill=accent)
        y += 36
        sheet.paste(p, (20, y))
        y += p.height + 8
    sheet.save(out_path)
    return sheet
