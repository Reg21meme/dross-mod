"""
Draws night views of real generated Dross land, seen from above and the south-east: each biome's sample site and the
hub, with their trees, plants, ponds, boulders and everything else, in the real block textures.

    1. $env:DROSS_TERRAIN_PREVIEWS="1"; .\\gradlew.bat runGameTestServer -PgameTestWorld=<a new folder> --console=plain
       (TerrainPreviewGameTest writes the blocks to build/tv1/views/<name>.json)
    2. python tools/terrain/preview_sites.py          (writes terrain-previews/view_<name>.png)

It's drawn at night: blocks are dark unless block light reaches them (glowing plants, Stormvine, lava, vents...), and the
full-bright parts of the Dross's blocks (Stormvine, Storm Lichen, the glowing overlays) glow as they do in the game.
Textures: the mod's own from src/main/resources, vanilla ones from the game jar in the Gradle cache. Needs numpy and Pillow.
"""
import io
import json
import os
import sys
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
VIEWS = os.path.join(ROOT, "build", "tv1", "views")
OUT = os.path.join(ROOT, "terrain-previews")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets")
MC_JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions", "1.20.1",
                      "client.jar")

# --- the look -----------------------------------------------------------------------------------------------------------
K = 8                       # half a block's width on screen (pixels): the top of a block is 2K wide and K tall
H = 10                      # a block's height on screen (pixels)
NIGHT = 0.36                # how bright an unlit block is (night); block light 15 makes it fully bright
LIGHT_CURVE = 0.85          # how block light brightens (below 1: a little light shows more)
FACE_SHADE = {"top": 1.0, "south": 0.78, "east": 0.6}
WATER_ALPHA = 0.78
BG_TOP = (6, 8, 18)
BG_BOTTOM = (20, 26, 52)
TEXT = (225, 232, 245)
SUBTEXT = (160, 172, 196)
# Blocks drawn full-bright whatever the light (they glow in the game: full-bright models, or light 15 themselves)
FULL_BRIGHT = {"dross:stormvine", "dross:storm_lichen", "minecraft:lava", "minecraft:shroomlight", "minecraft:glowstone",
               "minecraft:sea_lantern", "minecraft:magma_block"}
# Huge-mushroom blocks: the texture shown on a face whose property is false (the inside)
MUSHROOM_INSIDE = {"dross:blightcap_block": "dross:block/blightcap_inside", "dross:rotting_blightcap": "dross:block/blightcap_inside",
                   "dross:blightcap_stem": "dross:block/blightcap_inside",
                   "minecraft:red_mushroom_block": "minecraft:block/mushroom_block_inside",
                   "minecraft:brown_mushroom_block": "minecraft:block/mushroom_block_inside",
                   "minecraft:mushroom_stem": "minecraft:block/mushroom_block_inside"}
FACE_PROPS = ("north", "south", "east", "west", "up", "down")


# --- reading the game's assets ------------------------------------------------------------------------------------------
class Assets:
    def __init__(self):
        self.jar = zipfile.ZipFile(MC_JAR) if os.path.exists(MC_JAR) else None
        if self.jar is None:
            print("preview_sites: the game jar wasn't found, so vanilla blocks are drawn as plain grey")
        self.cache = {}

    def read(self, ns, path):
        local = os.path.join(ASSETS, ns, *path.split("/"))
        if os.path.exists(local):
            with open(local, "rb") as f:
                return f.read()
        if self.jar is not None:
            try:
                return self.jar.read(f"assets/{ns}/{path}")
            except KeyError:
                return None
        return None

    def json(self, ns, path):
        data = self.read(ns, path)
        return json.loads(data) if data else None

    def texture(self, ref):
        """A texture id ('dross:block/ash', 'block/snow') as a 16x16 RGBA float array (first frame of animated ones)."""
        if ref in self.cache:
            return self.cache[ref]
        ns, path = split_id(ref)
        data = self.read(ns, f"textures/{path}.png")
        if data is None:
            img = np.ones((16, 16, 4)) * np.array([0.5, 0.5, 0.5, 1.0])
        else:
            arr = np.asarray(Image.open(io.BytesIO(data)).convert("RGBA"), float) / 255.0
            w = arr.shape[1]
            arr = arr[:w, :w]
            if w != 16:
                arr = np.asarray(Image.fromarray((arr * 255).astype(np.uint8), "RGBA").resize((16, 16), Image.NEAREST), float) / 255.0
            img = arr
        self.cache[ref] = img
        return img

    def has_texture(self, ref):
        ns, path = split_id(ref)
        return self.read(ns, f"textures/{path}.png") is not None


def split_id(ref, default_ns="minecraft"):
    if ":" in ref:
        ns, path = ref.split(":", 1)
    else:
        ns, path = default_ns, ref
    return ns, path


def parse_state(state):
    """'minecraft:snow[layers=2]' -> ('minecraft:snow', {'layers': '2'})."""
    if "[" in state:
        name, rest = state.split("[", 1)
        props = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if "=" in kv)
    else:
        name, props = state, {}
    if ":" not in name:
        name = "minecraft:" + name
    return name, props


def model_textures(assets, model_ref):
    """The textures of a block model, following its parents, with #references resolved."""
    textures = {}
    parents = []
    ref = model_ref
    for _ in range(12):
        if ref is None:
            break
        ns, path = split_id(ref)
        model = assets.json(ns, f"models/{path}.json")
        if model is None:
            break
        parents.append(f"{ns}:{path}")
        for k, v in model.get("textures", {}).items():
            textures.setdefault(k, v)
        ref = model.get("parent")
    for _ in range(6):
        for k, v in list(textures.items()):
            if isinstance(v, str) and v.startswith("#"):
                textures[k] = textures.get(v[1:], v)
    return textures, parents


def first_model(assets, name, props):
    ns, path = split_id(name)
    bs = assets.json(ns, f"blockstates/{path}.json")
    if bs is None:
        return None
    if "variants" in bs:
        best, best_score = None, -1
        for key, value in bs["variants"].items():
            want = dict(kv.split("=", 1) for kv in key.split(",") if "=" in kv)
            if all(props.get(k) == v for k, v in want.items()):
                score = len(want)
                if score > best_score:
                    best, best_score = value, score
        if best is None:
            best = next(iter(bs["variants"].values()))
        if isinstance(best, list):
            best = best[0]
        return best.get("model")
    if "multipart" in bs:
        for part in bs["multipart"]:
            apply = part.get("apply")
            if isinstance(apply, list):
                apply = apply[0]
            if apply and apply.get("model"):
                return apply["model"]
    return None


class Look:
    """How one block state is drawn: kind (cube, cross, vine, layer, fluid, none), its face textures and glow overlays."""

    def __init__(self, assets, state):
        self.state = state
        name, props = parse_state(state)
        self.name, self.props = name, props
        self.kind = "cube"
        self.height = 1.0
        self.full_bright = name in FULL_BRIGHT
        self.tint = None
        self.alpha = 1.0
        self.faces = {}
        self.glow = {}
        model = first_model(assets, name, props)
        textures, parents = model_textures(assets, model) if model else ({}, [])

        def tex(*keys):
            for k in keys:
                if k in textures and isinstance(textures[k], str) and not textures[k].startswith("#"):
                    return textures[k]
            return None

        def glow_of(ref):
            if ref is None:
                return None
            for key in ("glow",):
                if key in textures and isinstance(textures[key], str):
                    return assets.texture(textures[key])
            g = ref + "_glow"
            return assets.texture(g) if assets.has_texture(g) else None

        if name in ("minecraft:water", "minecraft:lava"):
            self.kind = "fluid"
            ref = "minecraft:block/water_still" if name == "minecraft:water" else "minecraft:block/lava_still"
            self.faces = {"top": assets.texture(ref)}
            self.height = 14 / 16
            if name == "minecraft:water":
                self.alpha = WATER_ALPHA
            return
        if name.endswith(("_vine", "stormvine")) or name in ("minecraft:vine", "minecraft:glow_lichen", "dross:storm_lichen"):
            if not any(k in parents for k in ("minecraft:block/cross",)) and tex("cross") is None:
                self.kind = "vine"
                self.faces = {"vine": assets.texture(tex("vine", "lichen", "texture", "particle") or "minecraft:block/vine")}
                return
        if "layers" in props:
            self.kind = "layer"
            self.height = int(props["layers"]) * 2 / 16
            t = assets.texture(tex("texture", "all", "particle") or "minecraft:block/snow")
            self.faces = {"top": t, "south": t, "east": t}
            return
        if "wool" in textures:
            self.kind = "layer"
            self.height = 1 / 16
            t = assets.texture(tex("wool"))
            self.faces = {"top": t, "south": t, "east": t}
            return
        cross = tex("cross")
        if cross is not None:
            self.kind = "cross"
            self.faces = {"cross": assets.texture(cross)}
            g = glow_of(cross)
            if g is not None:
                self.glow = {"cross": g}
            return
        if model is None or not textures:
            self.kind = "none" if name in ("minecraft:air", "minecraft:cave_air") else "cube"
            if self.kind == "cube":
                self.faces = {f: assets.texture("minecraft:block/stone") for f in ("top", "south", "east")}
            return
        top = tex("top", "end", "up", "all", "texture", "particle")
        side = tex("side", "north", "south", "all", "texture", "particle")
        axis = props.get("axis", "y")
        if axis == "x":
            refs = {"top": side, "south": side, "east": top}
        elif axis == "z":
            refs = {"top": side, "south": top, "east": side}
        else:
            refs = {"top": top, "south": side, "east": side}
        if name == "dross:frostbitten_log" and axis != "y":
            refs["top"] = "dross:block/frostbitten_log_rime"           # rime settles on top of a lying log
        if name in MUSHROOM_INSIDE:
            outside = tex("texture", "all", "particle") or name.replace(":", ":block/")
            for face, prop in (("top", "up"), ("south", "south"), ("east", "east")):
                refs[face] = outside if props.get(prop, "true") == "true" else MUSHROOM_INSIDE[name]
        self.faces = {f: assets.texture(r) for f, r in refs.items()}
        for f, r in refs.items():
            g = glow_of(r)
            if g is not None:
                self.glow[f] = g
        if "leaves" in name or name in ("minecraft:ice",):
            self.alpha = 1.0
        self.opaque = False

    @property
    def is_opaque(self):
        if self.kind != "cube" or "leaves" in self.name or self.name in ("minecraft:ice", "minecraft:glass"):
            return False
        return all(t[..., 3].min() > 0.99 for t in self.faces.values())


# --- drawing ------------------------------------------------------------------------------------------------------------
# A block's sprite covers 2K x (K + H) pixels; its origin is the block's top-north-west corner, at sprite x = K, y = 0.
SW, SH = 2 * K, K + H


def quad(sprite, texture, p0, e1, e2, shade, alpha=1.0, tint=None):
    """Paints texture onto the parallelogram p0 + u*e1 + v*e2 (u, v in 0..1) of the sprite (RGBA float), alpha-blended."""
    hgt, wid = sprite.shape[:2]
    ys, xs = np.mgrid[0:hgt, 0:wid]
    px, py = xs + 0.5 - p0[0], ys + 0.5 - p0[1]
    m = np.array([[e1[0], e2[0]], [e1[1], e2[1]]], float)
    if abs(np.linalg.det(m)) < 1e-9:
        return
    inv = np.linalg.inv(m)
    u = inv[0, 0] * px + inv[0, 1] * py
    v = inv[1, 0] * px + inv[1, 1] * py
    inside = (u >= 0) & (u < 1) & (v >= 0) & (v < 1)
    src = texture[np.clip((v * 16).astype(int), 0, 15), np.clip((u * 16).astype(int), 0, 15)]
    rgb = src[..., :3]
    if tint is not None:
        rgb = rgb * np.array(tint)
    a = (src[..., 3] * inside * alpha)[..., None]
    sprite[..., :3] = sprite[..., :3] * (1 - a) + rgb * shade * a
    sprite[..., 3:] = sprite[..., 3:] * (1 - a) + a


def brightness(light, full_bright):
    if full_bright:
        return 1.0
    return NIGHT + (1.0 - NIGHT) * (light / 15.0) ** LIGHT_CURVE


# screen offsets (pixels) of the unit steps, from the block's top-north-west corner
EX = np.array([K, K / 2.0])      # +x (east): right and down
EZ = np.array([-K, K / 2.0])     # +z (south): left and down
EY = np.array([0.0, -float(H)])  # +y (up)
O = np.array([float(K), 0.0])    # the corner's place in the sprite


def make_sprite(look, light, faces):
    """The block's sprite, lit by `light`, with only the faces in `faces` (top, south, east) drawn."""
    sprite = np.zeros((SH, SW, 4))
    b = brightness(light, look.full_bright)
    if look.kind == "cube" or look.kind == "layer":
        h = look.height
        drop = -EY * (1.0 - h)              # a layer's top sits lower
        if "top" in faces:
            quad(sprite, look.faces["top"], O + drop, EX, EZ, FACE_SHADE["top"] * b, look.alpha, look.tint)
            if "top" in look.glow:
                quad(sprite, look.glow["top"], O + drop, EX, EZ, 1.0)
        if "south" in faces:
            quad(sprite, look.faces["south"], O + EZ + drop, EX, -EY * h, FACE_SHADE["south"] * b, look.alpha, look.tint)
            if "south" in look.glow:
                quad(sprite, look.glow["south"], O + EZ + drop, EX, -EY * h, 1.0)
        if "east" in faces:
            quad(sprite, look.faces["east"], O + EX + drop, EZ, -EY * h, FACE_SHADE["east"] * b, look.alpha, look.tint)
            if "east" in look.glow:
                quad(sprite, look.glow["east"], O + EX + drop, EZ, -EY * h, 1.0)
    elif look.kind == "fluid":
        drop = -EY * (1.0 - look.height)
        quad(sprite, look.faces["top"], O + drop, EX, EZ, b, look.alpha, look.tint)
    elif look.kind == "cross":
        # one upright quad across the block, facing the viewer (from the north-east corner to the south-west)
        quad(sprite, look.faces["cross"], O + EX, EZ - EX, -EY, FACE_SHADE["south"] * b)
        if "cross" in look.glow:
            quad(sprite, look.glow["cross"], O + EX, EZ - EX, -EY, 1.0)
    elif look.kind == "vine":
        t = look.faces["vine"]
        on = {f for f in FACE_PROPS if look.props.get(f) == "true"}
        inset = 0.06
        if "north" in on:
            quad(sprite, t, O + EZ * inset, EX, -EY, b)
        if "west" in on:
            quad(sprite, t, O + EX * inset, EZ, -EY, b)
        if "south" in on:
            quad(sprite, t, O + EZ * (1 - inset), EX, -EY, b)
        if "east" in on:
            quad(sprite, t, O + EX * (1 - inset), EZ, -EY, b)
        if "up" in on:
            quad(sprite, t, O - EY * inset, EX, EZ, b)
        if "down" in on:
            quad(sprite, t, O - EY * (1 - inset), EX, EZ, b)
    return sprite


def render(view, assets):
    palette = view["palette"]
    looks = [Look(assets, s) for s in palette]
    water = view.get("water_color")
    if water is not None:
        tint = (((water >> 16) & 255) / 255.0, ((water >> 8) & 255) / 255.0, (water & 255) / 255.0)
        for look in looks:
            if look.name == "minecraft:water":
                look.tint = tint
    opaque = [look.is_opaque for look in looks]
    blocks = view["blocks"]
    occupied = {}
    for x, y, z, idx, light in blocks:
        occupied[(x, y, z)] = idx
    n = view["size"]
    ymax = max(b[1] for b in blocks) if blocks else 0
    # canvas: x from the west corner of the front-left column to the east corner; y from the highest top to the bottom
    width = int(2 * K * n + 2 * K) + 8
    height = int(K * n + H * (ymax + 2)) + 8
    ox = K * n + 4                      # screen x of block (0, *, 0)'s corner
    oy = H * (ymax + 1) + 4
    canvas = np.zeros((height, width, 3))
    for r in range(height):
        f = r / max(height - 1, 1)
        canvas[r] = (np.array(BG_TOP) * (1 - f) + np.array(BG_BOTTOM) * f) / 255.0
    cache = {}
    order = sorted(blocks, key=lambda b: (b[0] + b[1] + b[2], b[1]))
    for x, y, z, idx, light in order:
        look = looks[idx]
        if look.kind == "none":
            continue
        faces = []
        if not (opaque[occupied.get((x, y + 1, z), -1)] if (x, y + 1, z) in occupied else False) or look.kind == "layer":
            faces.append("top")
        if not (opaque[occupied[(x, y, z + 1)]] if (x, y, z + 1) in occupied else False):
            faces.append("south")
        if not (opaque[occupied[(x + 1, y, z)]] if (x + 1, y, z) in occupied else False):
            faces.append("east")
        if look.kind in ("cube",) and not faces:
            continue
        key = (idx, light, tuple(faces))
        sprite = cache.get(key)
        if sprite is None:
            sprite = make_sprite(look, light, faces)
            cache[key] = sprite
        sx = int(round(ox + EX[0] * x + EZ[0] * z - K))
        sy = int(round(oy + EX[1] * x + EZ[1] * z + EY[1] * (y + 1)))
        if sx < 0 or sy < 0 or sx + SW > width or sy + SH > height:
            continue
        region = canvas[sy:sy + SH, sx:sx + SW]
        a = sprite[..., 3:]
        region[:] = region * (1 - a) + sprite[..., :3] * a
    return Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8), "RGB")


def font(size, bold=False):
    for name in (("segoeuib.ttf" if bold else "segoeui.ttf"), ("arialbd.ttf" if bold else "arial.ttf")):
        try:
            return ImageFont.truetype(os.path.join("C:/Windows/Fonts", name), size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    if not os.path.isdir(VIEWS):
        sys.exit("build/tv1/views/ not found: run the preview test first (see the top of this file)")
    assets = Assets()
    names = sorted(f[:-5] for f in os.listdir(VIEWS) if f.endswith(".json"))
    os.makedirs(OUT, exist_ok=True)
    for name in names:
        with open(os.path.join(VIEWS, name + ".json"), encoding="utf-8") as f:
            view = json.load(f)
        img = render(view, assets)
        title = view.get("title", name)
        out = Image.new("RGB", (img.width, img.height + 44), BG_TOP)
        out.paste(img, (0, 44))
        d = ImageDraw.Draw(out)
        d.text((12, 8), f"{title}: real generated blocks, {view['size']} x {view['size']}, at night", font=font(17, True), fill=TEXT)
        d.text((12, 28), "Seen from above and the south-east; lit only where block light reaches (glowing plants, Stormvine, lava...)",
               font=font(12), fill=SUBTEXT)
        path = os.path.join(OUT, f"view_{name}.png")
        out.save(path)
        print(f"preview_sites: {path} ({out.width}x{out.height}, {len(view['blocks'])} blocks, {len(view['palette'])} kinds)")


if __name__ == "__main__":
    main()
