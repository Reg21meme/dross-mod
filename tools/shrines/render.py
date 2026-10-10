"""Preview renders of a shrine Grid: top-down map, front and side elevations, and a textured 3D (isometric) view."""
import math
import os
from functools import lru_cache

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import mc

SKY_TOP = np.array([0.42, 0.60, 0.92])
SKY_BOTTOM = np.array([0.74, 0.84, 0.98])
BG = (28, 30, 38, 255)
GROUND_STATE = "minecraft:grass_block"   # what unset ground looks like (a superflat world)
UNDER_STATE = "minecraft:dirt"


def font(size, bold=False):
    for name in (("segoeuib.ttf" if bold else "segoeui.ttf"), "arialbd.ttf" if bold else "arial.ttf"):
        try:
            return ImageFont.truetype(os.path.join("C:/Windows/Fonts", name), size)
        except OSError:
            continue
    return ImageFont.load_default()


# ---------------------------------------------------------------- shared helpers

def dense(grid):
    """The grid as an array of palette ids plus the palette, with unset ground filled in as grass/dirt."""
    a = grid.a.copy()
    pal = list(grid.pal)
    g_id = len(pal)
    pal.append(GROUND_STATE)
    d_id = len(pal)
    pal.append(UNDER_STATE)
    G = grid.G
    ground = a[:, G, :] == 0
    a[:, G, :][ground] = g_id
    for yi in range(G):
        layer = a[:, yi, :]
        layer[layer == 0] = d_id
    return a, pal


@lru_cache(maxsize=None)
def scaled(state, face, size, shade):
    f = mc.faces(state)
    tex = f.get(face) if face in f else f.get("side")
    if tex is None:
        tex = np.zeros((16, 16, 4), np.float32)
        tex[..., 0] = 1.0
        tex[..., 2] = 1.0
        tex[..., 3] = 1.0
    img = Image.fromarray((np.clip(tex, 0, 1) * 255).astype(np.uint8), "RGBA").resize((size, size), Image.NEAREST)
    arr = np.asarray(img).astype(np.float32) / 255.0
    arr = arr.copy()
    arr[..., :3] *= shade
    return arr


def _alpha_for(state):
    k = mc.kind(state)
    if k == "glass":
        return 0.62
    if k == "water":
        return 0.72
    return 1.0


# ---------------------------------------------------------------- top-down

def render_top(grid, px=10):
    a, pal = dense(grid)
    W, H, D = a.shape
    kinds = [mc.kind(s) if s else "air" for s in pal]
    # top surface height per column (first non-air from above), for shading/shadows
    top_h = np.zeros((W, D), int)
    for x in range(W):
        for z in range(D):
            col = a[x, :, z]
            for yi in range(H - 1, -1, -1):
                if kinds[col[yi]] not in ("air",):
                    top_h[x, z] = yi
                    break
    hmin, hmax = top_h.min(), max(top_h.max(), top_h.min() + 1)
    img = np.zeros((D * px, W * px, 3), np.float32)
    for x in range(W):
        for z in range(D):
            col = a[x, :, z]
            color = np.zeros((px, px, 3))
            remaining = np.ones((px, px, 1))
            for yi in range(H - 1, -1, -1):
                s = pal[col[yi]]
                k = kinds[col[yi]]
                if k == "air":
                    continue
                face = "top"
                tex = scaled(s, face, px, 1.0)
                alpha = tex[..., 3:4] * _alpha_for(s)
                if k in ("cross", "thin", "wall", "fence", "pane", "carpet"):
                    alpha = alpha * (0.85 if k != "pane" else 0.5)
                h_shade = 0.62 + 0.38 * (yi - hmin) / (hmax - hmin)
                # cast shadow from the north-west
                shadow = 1.0
                for k2 in range(1, 16):
                    xx, zz = x - k2, z - k2
                    if xx < 0 or zz < 0:
                        break
                    if top_h[xx, zz] > yi + k2 * 0.9:
                        shadow = 0.68
                        break
                contrib = tex[..., :3] * h_shade * shadow
                color = color + remaining * alpha * contrib
                remaining = remaining * (1 - alpha)
                if remaining.max() < 0.03:
                    break
            img[z * px:(z + 1) * px, x * px:(x + 1) * px] = color + remaining * 0.1
    out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB")
    return out


# ---------------------------------------------------------------- elevations

def render_elevation(grid, view="front", px=10):
    """view 'front': from the south looking north (x left->right). 'side': from the east looking west
    (z right->left as seen... drawn with the front of the shrine on the left)."""
    a, pal = dense(grid)
    W, H, D = a.shape
    kinds = [mc.kind(s) if s else "air" for s in pal]
    if view == "front":
        cols, depth = W, D
    else:
        cols, depth = D, W
    img = np.zeros((H * px, cols * px, 3), np.float32)
    # sky
    for r in range(H * px):
        t = r / max(H * px - 1, 1)
        img[r, :, :] = SKY_TOP * (1 - t) + SKY_BOTTOM * t
    depth_ref = max(40, depth * 0.8)
    for c in range(cols):
        for yi in range(H):
            color = np.zeros((px, px, 3))
            remaining = np.ones((px, px, 1))
            for d in range(depth):
                if view == "front":
                    x, z = c, D - 1 - d
                    face = "side"
                else:
                    x, z = W - 1 - d, D - 1 - c
                    face = "side"
                s_id = a[x, yi, z]
                k = kinds[s_id]
                if k == "air":
                    continue
                s = pal[s_id]
                f = mc.faces(s)
                if "axis" in f:
                    # a log lying along the view direction shows its end
                    along = f["axis"]
                    if (view == "front" and along == "z") or (view == "side" and along == "x"):
                        face = "end"
                shade = 1.0 - 0.5 * min(d / depth_ref, 1.0)
                tex = scaled(s, face, px, 1.0)
                alpha = tex[..., 3:4] * _alpha_for(s)
                if k == "pane":
                    alpha = alpha * 0.6
                contrib = tex[..., :3] * shade + (1 - shade) * 0.18 * SKY_BOTTOM
                color = color + remaining * alpha * contrib
                remaining = remaining * (1 - alpha)
                if remaining.max() < 0.03:
                    break
            r0 = (H - 1 - yi) * px
            sky = img[r0:r0 + px, c * px:(c + 1) * px]
            img[r0:r0 + px, c * px:(c + 1) * px] = color + remaining * sky
    out = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGB")
    return out


# ---------------------------------------------------------------- isometric 3D

# screen offsets per unit step along x, z and y (integers, so blocks tile without gaps)
SS = 2  # rendered at 2x, then scaled down smoothly
IX = (8 * SS, 4 * SS)
IZ = (-8 * SS, 4 * SS)
IY = (0, -10 * SS)


def _proj(x, y, z):
    return (IX[0] * x + IZ[0] * z + IY[0] * y, IX[1] * x + IZ[1] * z + IY[1] * y)


@lru_cache(maxsize=None)
def _face_sprite(state, face, box, shade, alpha_mul):
    """An RGBA sprite of one face of a box inside the block cell, and its top-left offset relative to the
    projection of the cell's (0,0,0) corner. box = (x0, y0, z0, x1, y1, z1) in 0..1."""
    x0, y0, z0, x1, y1, z1 = box
    if face == "top":
        o = (x0, y1, z0)
        e1 = (x1 - x0, 0, 0)
        e2 = (0, 0, z1 - z0)
        tex_key = "top"
    elif face == "south":
        o = (x0, y1, z1)
        e1 = (x1 - x0, 0, 0)
        e2 = (0, -(y1 - y0), 0)
        tex_key = "side"
    elif face == "east":
        o = (x1, y1, z1)
        e1 = (0, 0, -(z1 - z0))
        e2 = (0, -(y1 - y0), 0)
        tex_key = "side"
    elif face == "billboard":
        # an upright plane facing the viewer through the middle of the cell
        o = (0.0, y1, 1.0)
        e1 = (1.0, 0, -1.0)
        e2 = (0, -(y1 - y0), 0)
        tex_key = "side"
    else:
        raise ValueError(face)
    f = mc.faces(state)
    if "axis" in f:
        axis = f["axis"]
        if (face == "south" and axis == "z") or (face == "east" and axis == "x"):
            tex_key = "end"
    tex = f.get(tex_key)
    if tex is None:
        tex = f.get("side")
    if tex is None:
        return None, (0, 0)
    tex = tex.copy()
    tex[..., :3] *= shade
    tex[..., 3] *= alpha_mul
    timg = Image.fromarray((np.clip(tex, 0, 1) * 255).astype(np.uint8), "RGBA")
    po = np.array(_proj(*o), float)
    p1 = np.array(_proj(*e1), float)
    p2 = np.array(_proj(*e2), float)
    corners = [po, po + p1, po + p2, po + p1 + p2]
    xs = [c[0] for c in corners]
    ys = [c[1] for c in corners]
    left, top = math.floor(min(xs)), math.floor(min(ys))
    w = math.ceil(max(xs)) - left
    h = math.ceil(max(ys)) - top
    if w <= 0 or h <= 0:
        return None, (0, 0)
    m = np.array([[p1[0], p2[0]], [p1[1], p2[1]]])
    if abs(np.linalg.det(m)) < 1e-9:
        return None, (0, 0)
    inv = np.linalg.inv(m)
    # output pixel (X, Y) (relative to left/top) -> s, t = inv @ (X + left - po)
    ox, oy = left - po[0], top - po[1]
    a_, b_ = inv[0, 0] * 16, inv[0, 1] * 16
    d_, e_ = inv[1, 0] * 16, inv[1, 1] * 16
    c_ = (inv[0, 0] * ox + inv[0, 1] * oy) * 16
    f_ = (inv[1, 0] * ox + inv[1, 1] * oy) * 16
    # sample pixel centres
    c_ += (a_ + b_) * 0.5
    f_ += (d_ + e_) * 0.5
    sprite = timg.transform((w, h), Image.AFFINE, (a_, b_, c_, d_, e_, f_), resample=Image.NEAREST,
                            fillcolor=(0, 0, 0, 0))
    return sprite, (left, top)


SHADE_TOP, SHADE_SOUTH, SHADE_EAST = 1.0, 0.80, 0.62
SHADOW = 0.58


def _boxes_for(state, k):
    """Approximate shapes for the preview."""
    if k == "slab_bottom":
        return [(0, 0, 0, 1, 0.5, 1)]
    if k == "slab_top":
        return [(0, 0.5, 0, 1, 1, 1)]
    if k == "carpet":
        return [(0, 0, 0, 1, 0.07, 1)]
    if k == "wall":
        return [(0.25, 0, 0.25, 0.75, 1, 0.75)]
    if k == "fence":
        return [(0.375, 0, 0.375, 0.625, 1, 0.625)]
    if k == "pane":
        return [(0.42, 0, 0.42, 0.58, 1, 0.58)]
    if k == "thin":
        b = mc.base(state)
        if b in ("chain",):
            return [(0.43, 0, 0.43, 0.57, 1, 0.57)]
        if "lantern" in b:
            return [(0.3, 0.0, 0.3, 0.7, 0.55, 0.7)]
        if "candle" in b or "torch" in b or "rod" in b:
            return [(0.4, 0, 0.4, 0.6, 0.6, 0.6)]
        if b == "snow":
            return [(0, 0, 0, 1, 0.13, 1)]
        if b == "pointed_dripstone":
            return "billboard"
        return [(0.2, 0, 0.2, 0.8, 0.7, 0.8)]
    if k == "cross":
        return "billboard"
    if k == "stairs":
        p = mc.props_of(state)
        half = p.get("half", "bottom")
        f = p.get("facing", "north")
        lower = (0, 0, 0, 1, 0.5, 1) if half == "bottom" else (0, 0.5, 0, 1, 1, 1)
        yb = (0.5, 1) if half == "bottom" else (0, 0.5)
        if f == "north":
            step = (0, yb[0], 0, 1, yb[1], 0.5)
        elif f == "south":
            step = (0, yb[0], 0.5, 1, yb[1], 1)
        elif f == "east":
            step = (0.5, yb[0], 0, 1, yb[1], 1)
        else:
            step = (0, yb[0], 0, 0.5, yb[1], 1)
        return [lower, step]
    return [(0, 0, 0, 1, 1, 1)]


def render_iso(grid, crop=True):
    a, pal = dense(grid)
    W, H, D = a.shape
    kinds = [mc.kind(s) if s else "air" for s in pal]
    opaque = np.array([k == "full" for k in kinds])
    # image bounds
    corners = [_proj(x, y, z) for x in (0, W) for y in (0, H) for z in (0, D)]
    minx = min(c[0] for c in corners) - 4
    miny = min(c[1] for c in corners) - 4
    maxx = max(c[0] for c in corners) + 4
    maxy = max(c[1] for c in corners) + 4
    img = Image.new("RGBA", (maxx - minx, maxy - miny), (0, 0, 0, 0))
    occ = opaque[a]
    # sky shadow: a spot is in shade if anything solid is somewhere above it
    above_any = np.zeros_like(occ)
    above_any[:, :-1, :] = np.flip(np.cumsum(np.flip(occ[:, 1:, :], axis=1), axis=1), axis=1) > 0

    def shaded(x, y, z):
        if 0 <= x < W and 0 <= y < H and 0 <= z < D:
            return bool(above_any[x, y, z])
        return False

    cells = []
    xs, ys, zs = np.nonzero(a)
    order = np.argsort(xs + ys + zs, kind="stable")
    for i in order:
        x, y, z = int(xs[i]), int(ys[i]), int(zs[i])
        s_id = a[x, y, z]
        k = kinds[s_id]
        if k == "air":
            continue
        state = pal[s_id]
        # hidden entirely?
        up = y + 1 < H and occ[x, y + 1, z]
        south = z + 1 < D and occ[x, y, z + 1]
        east = x + 1 < W and occ[x + 1, y, z]
        if k == "full" and up and south and east:
            continue
        base = _proj(x, y, z)
        alpha_mul = 0.62 if k == "glass" else (0.75 if k == "water" else 1.0)
        boxes = _boxes_for(state, k)
        if boxes == "billboard":
            spr, off = _face_sprite(state, "billboard", (0, 0, 0, 1, 1, 1), 0.95, 1.0)
            if spr is not None:
                img.alpha_composite(spr, (base[0] + off[0] - minx, base[1] + off[1] - miny))
            continue
        for box in boxes:
            full_box = box == (0, 0, 0, 1, 1, 1)
            if k == "water":
                faces = [("top", SHADE_TOP)] if not (y + 1 < H and kinds[a[x, y + 1, z]] == "water") else []
                if not (z + 1 < D and kinds[a[x, y, z + 1]] in ("water", "full")):
                    faces.append(("south", SHADE_SOUTH))
                if not (x + 1 < W and kinds[a[x + 1, y, z]] in ("water", "full")):
                    faces.append(("east", SHADE_EAST))
            else:
                faces = []
                if not (full_box and up) or box[4] < 1:
                    faces.append(("top", SHADE_TOP))
                if not (full_box and south) or box[5] < 1:
                    faces.append(("south", SHADE_SOUTH))
                if not (full_box and east) or box[3] < 1:
                    faces.append(("east", SHADE_EAST))
            for face, shade in faces:
                if face == "top":
                    dark = shaded(x, y, z)
                elif face == "south":
                    dark = shaded(x, y, z + 1)
                else:
                    dark = shaded(x + 1, y, z)
                if dark:
                    shade = round(shade * SHADOW, 3)
                spr, off = _face_sprite(state, face, box, shade, alpha_mul)
                if spr is not None:
                    img.alpha_composite(spr, (base[0] + off[0] - minx, base[1] + off[1] - miny))
    if crop:
        bbox = img.getbbox()
        if bbox:
            img = img.crop((max(bbox[0] - 20, 0), max(bbox[1] - 20, 0),
                            min(bbox[2] + 20, img.width), min(bbox[3] + 20, img.height)))
    if SS > 1:
        img = img.resize((img.width // SS, img.height // SS), Image.LANCZOS)
    return img


# ---------------------------------------------------------------- sheets

def _label(img, text, size=22, pad=10, bg=BG, color=(235, 238, 245, 255), sub=None):
    f = font(size, bold=True)
    fs = font(max(size - 6, 12))
    extra = size + 16 + (size if sub else 0)
    out = Image.new("RGBA", (img.width + 2 * pad, img.height + extra + pad), bg)
    d = ImageDraw.Draw(out)
    d.text((pad, 6), text, font=f, fill=color)
    if sub:
        d.text((pad, 8 + size + 2), sub, font=fs, fill=(170, 178, 195, 255))
    out.paste(img.convert("RGBA"), (pad, extra))
    return out


def save_previews(grid, info, out_dir, stem):
    """Writes <stem>_top.png, <stem>_side.png (front + side elevations) and <stem>_3d.png. Returns the paths."""
    os.makedirs(out_dir, exist_ok=True)
    title = f"No. {info['number']}  {info['name']}"
    size = f"{grid.W} wide x {grid.D} deep x {grid.H - grid.G} tall ({info['mood']})"
    W, D = grid.W, grid.D
    px = max(6, min(16, int(1200 / max(W, D))))
    top = render_top(grid, px=px)
    # mark the front edge
    top_l = _label(top, title + "  -  top view", sub=size + ".  The way in is at the BOTTOM edge.")
    top_path = os.path.join(out_dir, f"{stem}_top.png")
    top_l.save(top_path)

    epx = max(5, min(12, int(900 / max(W, D, grid.H))))
    front = render_elevation(grid, "front", px=epx)
    side = render_elevation(grid, "side", px=epx)
    gap = 24
    both = Image.new("RGBA", (front.width + side.width + gap, max(front.height, side.height) + 30), BG)
    d = ImageDraw.Draw(both)
    both.paste(front, (0, 30))
    both.paste(side, (front.width + gap, 30))
    f = font(18, bold=True)
    d.text((4, 4), "Front (from the way in)", font=f, fill=(220, 225, 235, 255))
    d.text((front.width + gap + 4, 4), "Right side (front on the left)", font=f, fill=(220, 225, 235, 255))
    side_l = _label(both, title + "  -  side views", sub=size)
    side_path = os.path.join(out_dir, f"{stem}_side.png")
    side_l.save(side_path)

    iso = render_iso(grid)
    sky = Image.new("RGBA", iso.size, (0, 0, 0, 0))
    arr = np.zeros((iso.height, iso.width, 4), np.uint8)
    for r in range(iso.height):
        t = r / max(iso.height - 1, 1)
        c = SKY_TOP * (1 - t) + SKY_BOTTOM * t
        arr[r, :, :3] = (c * 255).astype(np.uint8)
        arr[r, :, 3] = 255
    sky = Image.fromarray(arr, "RGBA")
    sky.alpha_composite(iso)
    iso_l = _label(sky, title + "  -  3D view from the front-right", sub=size)
    iso_path = os.path.join(out_dir, f"{stem}_3d.png")
    iso_l.save(iso_path)
    return top_path, side_path, iso_path
