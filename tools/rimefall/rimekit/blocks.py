"""
Real Minecraft blocks inside a candidate (the Fisher's bait house): the mod draws them with the game's own block models
and textures (boss/rimefall/client/RimefallBlocksLayer), so the model only lists them (Shape.block). This file draws
them for the previews: a few block shapes (full cubes, logs, stairs, slabs, glass panes, doors, lanterns, snow layers)
made of boxes, painted with Minecraft's own block textures read from the game jar in the Gradle cache (only for the
pictures; none of them is copied into the mod). Without the jar the blocks come out in flat colours.

Block directions are Minecraft's in the model's frame: north is the model's front (-z), west its own left (+x). A block's
local box (x east 0..16, y up, z south) lands at Bedrock x = corner.x + 16 - x, y = corner.y + y, z = corner.z + z.
"""
import io
import os
import re
import zipfile

import numpy as np
from PIL import Image

from kit import geo, paint

JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "1.20.1",
                   "client-extra.jar")
_JAR = None
_TEX = {}
FALLBACK = {"planks": (126, 92, 56), "log": (74, 54, 34), "cobblestone": (122, 122, 122), "glass": (200, 220, 230),
            "bricks": (150, 74, 60), "snow": (240, 244, 248), "lantern": (60, 60, 70), "door": (110, 80, 50)}


def texture(name):
    """A block texture (16x16 RGBA float, the first frame of an animated one) by its name, like "spruce_planks"."""
    global _JAR
    if name in _TEX:
        return _TEX[name]
    arr = None
    try:
        if _JAR is None:
            _JAR = zipfile.ZipFile(JAR)
        data = _JAR.read(f"assets/minecraft/textures/block/{name}.png")
        im = Image.open(io.BytesIO(data)).convert("RGBA")
        arr = np.asarray(im, dtype=float)[:16, :16]
    except (OSError, KeyError):
        colour = next((c for k, c in FALLBACK.items() if k in name), (128, 128, 128))
        arr = np.zeros((16, 16, 4))
        arr[..., :3] = colour
        arr[..., 3] = 255.0
    _TEX[name] = arr
    return arr


def parse(state):
    """"minecraft:oak_stairs[facing=east,half=bottom]" -> ("oak_stairs", {"facing": "east", "half": "bottom"})."""
    m = re.fullmatch(r"(?:minecraft:)?([a-z0-9_]+)(?:\[(.*)\])?", state)
    if not m:
        raise ValueError(f"not a block state: {state}")
    props = {}
    if m.group(2):
        for kv in m.group(2).split(","):
            k, v = kv.split("=")
            props[k.strip()] = v.strip()
    return m.group(1), props


SIDES = ("north", "south", "east", "west", "up", "down")
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def _all(tex):
    return {f: tex for f in SIDES}


def _wood(name):
    """The wood type of a block name ("spruce_stairs" -> "spruce")."""
    for w in ("dark_oak", "spruce", "oak", "birch", "jungle", "acacia", "mangrove", "cherry"):
        if name.startswith(w):
            return w
    return "oak"


def parts(name, props):
    """The block as boxes: [((x0, y0, z0, x1, y1, z1), {face: texture})] in its own 0..16 frame."""
    if name.endswith("_stairs"):
        tex = {"cobblestone_stairs": "cobblestone", "brick_stairs": "bricks", "stone_brick_stairs": "stone_bricks"}.get(
            name, f"{_wood(name)}_planks")
        top = props.get("half", "bottom") == "top"
        f = props.get("facing", "north")
        low = (0, 8, 0, 16, 16, 16) if top else (0, 0, 0, 16, 8, 16)
        hy0, hy1 = (0, 8) if top else (8, 16)
        half = {"north": (0, hy0, 0, 16, hy1, 8), "south": (0, hy0, 8, 16, hy1, 16),
                "east": (8, hy0, 0, 16, hy1, 16), "west": (0, hy0, 0, 8, hy1, 16)}[f]
        return [(low, _all(tex)), (half, _all(tex))]
    if name.endswith("_slab"):
        tex = {"cobblestone_slab": "cobblestone", "brick_slab": "bricks"}.get(name, f"{_wood(name)}_planks")
        kind = props.get("type", "bottom")
        box = {"bottom": (0, 0, 0, 16, 8, 16), "top": (0, 8, 0, 16, 16, 16), "double": (0, 0, 0, 16, 16, 16)}[kind]
        return [(box, _all(tex))]
    if name.endswith("_log") or name.endswith("_wood"):
        axis = props.get("axis", "y")
        side, end = name, name + "_top"
        if name.endswith("_wood"):
            side = end = name[:-5] + "_log"
        faces = {f: side for f in SIDES}
        ends = {"x": ("east", "west"), "y": ("up", "down"), "z": ("north", "south")}[axis]
        for e in ends:
            faces[e] = end
        return [((0, 0, 0, 16, 16, 16), faces)]
    if name.endswith("glass_pane"):
        glass = name[:-5] if name != "glass_pane" else "glass"
        boxes = [((7, 0, 7, 9, 16, 9), _all(glass))]
        if props.get("north") == "true":
            boxes.append(((7, 0, 0, 9, 16, 7), _all(glass)))
        if props.get("south") == "true":
            boxes.append(((7, 0, 9, 9, 16, 16), _all(glass)))
        if props.get("west") == "true":
            boxes.append(((0, 0, 7, 7, 16, 9), _all(glass)))
        if props.get("east") == "true":
            boxes.append(((9, 0, 7, 16, 16, 9), _all(glass)))
        return boxes
    if name.endswith("_door"):
        tex = f"{name}_{'top' if props.get('half') == 'upper' else 'bottom'}"
        f = props.get("facing", "north")
        # A closed door lies against the side of its block opposite the way it faces.
        side = OPPOSITE[f]
        box = {"north": (0, 0, 0, 16, 16, 3), "south": (0, 0, 13, 16, 16, 16),
               "west": (0, 0, 0, 3, 16, 16), "east": (13, 0, 0, 16, 16, 16)}[side]
        return [(box, _all(tex))]
    if name in ("lantern", "soul_lantern"):
        hang = props.get("hanging") == "true"
        y0 = 1 if hang else 0
        return [((5, y0, 5, 11, y0 + 7, 11), _all(name)), ((6, y0 + 7, 6, 10, y0 + 9, 10), _all(name))]
    if name == "snow":
        n = int(props.get("layers", "1"))
        return [((0, 0, 0, 16, 2 * n, 16), _all("snow"))]
    if name == "campfire":
        return [((1, 0, 0, 5, 4, 16), _all("campfire_log_lit")), ((11, 0, 0, 15, 4, 16), _all("campfire_log_lit")),
                ((0, 3, 1, 16, 7, 5), _all("campfire_log_lit")), ((0, 3, 11, 16, 7, 15), _all("campfire_log_lit")),
                ((3, 4, 3, 13, 14, 13), _all("campfire_fire"))]
    tex = {"snow_block": "snow"}.get(name, name)
    return [((0, 0, 0, 16, 16, 16), _all(tex))]


def preview_model(placements):
    """
    The listed blocks as one model for the previews: (model, textures like texture.bake's: base, ice, glow, iceglow).
    Bright blocks (a lit lantern) glow.
    """
    m = geo.Model("blocks")
    bone = m.bone("root", (0.0, 0.0, 0.0))
    info = []
    for state, corner, bright in placements:
        name, props = parse(state)
        cx, cy, cz = corner
        for (x0, y0, z0, x1, y1, z1), faces in parts(name, props):
            origin = (cx + 16 - x1, cy + y0, cz + z0)
            c = bone.add_abs(origin, (x1 - x0, y1 - y0, z1 - z0), "block")
            info.append((c, corner, faces, bright))
    if not info:
        return None
    m.pack_uv(width=512)
    tm = paint.TexelMap(m)
    base = np.zeros((m.tex_h, m.tex_w, 4))
    glow = np.zeros((m.tex_h, m.tex_w, 4))
    for c, corner, faces, bright in info:
        sel = np.nonzero(tm.cube == c.index)
        P = tm.P[sel]
        fidx = tm.face[sel]
        lx = corner[0] + 16 - P[:, 0]
        ly = P[:, 1] - corner[1]
        lz = P[:, 2] - corner[2]
        out = np.zeros((len(P), 4))
        for fi, face in enumerate(geo.FACES):
            k = fidx == fi
            if not k.any():
                continue
            if face == "north":
                u, v = 16 - lx[k], 16 - ly[k]
            elif face == "south":
                u, v = lx[k], 16 - ly[k]
            elif face == "east":
                u, v = 16 - lz[k], 16 - ly[k]
            elif face == "west":
                u, v = lz[k], 16 - ly[k]
            elif face == "up":
                u, v = lx[k], lz[k]
            else:
                u, v = lx[k], 16 - lz[k]
            t = texture(faces[face])
            out[k] = t[np.clip(np.floor(v), 0, 15).astype(int), np.clip(np.floor(u), 0, 15).astype(int)]
        out[:, 3] = np.where(out[:, 3] >= 128, 255.0, 0.0)
        base[sel] = out
        if bright:
            glow[sel] = out
    to8 = lambda a: np.clip(np.round(a), 0, 255).astype(np.uint8)
    empty = np.zeros_like(base)
    return m, dict(base=to8(base), ice=to8(empty), glow=to8(glow), iceglow=to8(empty))


def corners(placements):
    """Every placed block's corners (Bedrock units), for measuring a candidate's reach."""
    pts = []
    for state, (cx, cy, cz), _bright in placements:
        for dx in (0, 16):
            for dy in (0, 16):
                for dz in (0, 16):
                    pts.append((cx + dx, cy + dy, cz + dz))
    return np.array(pts, dtype=float).reshape(-1, 3)
