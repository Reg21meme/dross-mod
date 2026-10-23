"""
Mounted skulls (the user, October 22, 2026: "make a 3D model like the Ice and Fire dragons for the Alpha Moose head and
also for regular male mooses, and instead of the skeleton [skulls] with the fences put those instead ... and for the
rhino horns as well with the 2 ice horns"; their answers: bare skulls, and players can get them): three blocks, each
a bare bone skull with the animal's own antlers or horns, built with the Rimefall kit from the same shapes as the
animals (tools/rimefall/rimedesigns/d06_huntsman.antlers, the Rimehorn's horns) and exported as baked 3D block models
(Forge's OBJ loader: no block entity, so a village full of them costs nothing to draw):

  alpha_moose_skull   the Alpha Moose's: old frosted bone, its great palm antlers hung with icicles, 3.3 blocks across
  moose_skull         a bull moose's: clean bone, its antlers, 2.4 blocks across
  rimehorn_skull      a Rimehorn's: a long low rhino skull, its two horns of clear ice

Each stands on the floor (on top of a post) or hangs on a wall, facing one of four ways (HuntSkullBlock).

  python tools/huntsman/skulls.py

Writes (src/main/resources/assets/dross/): models/block/hunt_skull/<id>_floor.obj and _wall.obj, their <id>.mtl, the
models <id>_floor.json and _wall.json, textures/block/hunt_skull/<id>.png, blockstates/<id>.json,
models/item/<id>.json; and the preview boss-previews/rimefall/huntsman/skulls.png.
"""
import json
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import geo  # noqa: E402
from rimekit import mats as M, shapes, texture, uvpack, zfight  # noqa: E402
from rimekit import view as V  # noqa: E402
from rimedesigns import d06_huntsman as H  # noqa: E402
from rimemobs import alpha_moose as ALPHA, moose as MOOSE, rimehorn as RIMEHORN  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
PREVIEW = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "skulls.png")
BONE = "skull"                 # the one bone every skull is built on


# ---------------------------------------------------------------- the moose skulls (in the moose's own head frame:
# d06_huntsman.moose_body builds its head there, its forehead at y 112 by z -70, the muzzle at z -100)


def moose_skull(s, old):
    """A bare moose skull, no lower jaw: the braincase, the broad forehead the antlers grow from, deep eye sockets, the
    long face with a moose's great nasal opening on top, and its antlers (the animal's own). `old`: the Alpha
    Moose's, a thicker brow and battle-cracked. (No teeth: its cheek teeth hung under its face as a row of loose
    stubs, which the user found "very bad", October 23, 2026.)"""
    s.rbox((0, 106, -66), (18, 14, 14), "bone", c=2)                                 # the braincase
    s.rbox((0, 110, -73), (17, 8, 12), "bone", c=2)                                  # the forehead
    s.plate((0, 104, -59.5), (0, 0.15, 1), (0, 1, 0), 15, 12, 2, "bone")             # the back of the skull
    s.rbox((0, 99, -62), (8, 6, 6), "bone", c=1)                                     # where the neck was
    for x in (-1, 1):
        s.seg((5 * x, 112, -70), (9 * x, 113.5, -70), 5, 5, "bone")                   # the antlers' roots
        s.box((8.6 * x, 104, -79), (2, 6, 6), "socket")                              # an eye socket
        s.box((8.0 * x, 108, -79), (3, 2 if not old else 3, 8), "bone")              # its brow
        s.seg((8.5 * x, 100, -73), (8.0 * x, 98, -87), 3, 3, "bone")                  # the cheek bone under it
    s.taper([(0, 104, -80), (0, 97, -92), (0, 91, -102)], [15, 12, 9], "bone", depths=[14, 11, 9])   # the long face
    s.box((0, 100.2, -95), (6, 2, 11), "socket")                                     # the nasal opening on top
    s.rbox((0, 89, -104), (8, 5, 6), "bone", c=1)                                    # the tip of the upper jaw
    for x in (-1, 1):
        s.box((2.5 * x, 88, -107), (2, 3, 1), "socket")                              # the nostrils' gap
    H.antlers(s, 114, -70, "moose")


def moose_materials(old):
    return {
        "bone": M.bone(seed=2101 if old else 2111, frost_amount=0.3 if old else 0.1, cracks=0.4 if old else 0.12),
        "antler": M.bone(seed=2103 if old else 2113, frost_amount=0.45 if old else 0.2, cracks=0.25 if old else 0.1),
        "icicle": M.solid(M.ICE, idx=3.6, seed=2105, grain=0.8, bev=0.9, outline=0.05),
        "socket": M.mouth(seed=2107),
    }


# ---------------------------------------------------------------- the Rimehorn's skull (in the Rimehorn's head frame:
# rimedesigns/d07_rimehorn.head, before its K)


def rimehorn_skull(s):
    """A bare woolly-rhino skull: long and low, a high crest swept back at its top, small eye sockets (no teeth, like
    the moose's), a boss of bone on its nose; the great horn of clear ice off the top of its nose and the short one on its brow,
    where the Rimehorn's own grow."""
    s.seg((0, 42, -52), (0, 37, -66), 16, 15, "bone", side=(1, 0, 0))                # the braincase
    s.plate((0, 47, -51), (0, 0.5, 1), (0, 1, -0.5), 13, 9, 2, "bone")               # the crest, swept back
    s.seg((0, 37.5, -64), (0, 32.5, -74), 13, 12, "bone", side=(1, 0, 0))            # the face
    s.seg((0, 32.5, -72), (0, 29.5, -80), 11, 10, "bone", side=(1, 0, 0))            # the nose
    s.rbox((0, 34, -76), (9, 4, 7), "bone", c=1)                                     # the boss the great horn grows from
    s.rbox((0, 42.5, -65), (8, 3, 6), "bone", c=1)                                   # and the short one's
    for x in (-1, 1):
        s.box((7.6 * x, 38, -62), (2, 4, 4), "socket")                               # an eye socket
        s.seg((7.4 * x, 35, -58), (6.8 * x, 33, -68), 3, 3, "bone")                   # the cheek bone
        s.box((3.2 * x, 29, -80.5), (2, 3, 1), "socket")                             # the nostrils
    # (the Rimehorn's own horns: d07_rimehorn.head)
    s.curve([(0, 44, -65), (0, 50.5, -67), (0, 56.5, -66)], 6, [7, 5, 2], "horn", depths=[9, 6, 2])
    s.curve([(0, 34.5, -76), (0, 46, -83), (0, 60, -87), (0, 73, -86)], 12, [9, 7, 4, 2], "horn",
            depths=[11, 9, 6, 2])


def rimehorn_materials():
    return {
        "bone": M.bone(seed=2201, frost_amount=0.15, cracks=0.18),
        "socket": M.mouth(seed=2203),
        "horn": M.ice(M.ICE, alpha=(165, 225), seed=2207, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=35),
    }


# id, name, scale (design units -> sixteenths of a block: the animal's own, so each skull is life size; the user,
# October 23, 2026: "the size of their actual skull, not a downgrade"), build, materials, has clear ice (drawn
# translucent), how big its icon is in the inventory, and the materials left off it (a bull's antlers have no icicles: only the old
# Alpha Moose's are hung with them)
SKULLS = (
    ("alpha_moose_skull", "Alpha Moose Skull", ALPHA.K, lambda s: moose_skull(s, True), lambda: moose_materials(True),
     False, 0.17, ()),
    ("moose_skull", "Moose Skull", MOOSE.K, lambda s: moose_skull(s, False), lambda: moose_materials(False), False,
     0.23, ("icicle",)),
    ("rimehorn_skull", "Rimehorn Skull", RIMEHORN.K, rimehorn_skull, rimehorn_materials, True, 0.27, ()),
)
WALL_GAP = 0.01               # blocks between a wall skull's back and the wall
WALL_HEIGHT = 0.22            # blocks: a wall skull's lowest point above its block's floor
VIEWS = ((25, 15), (90, 5), (155, 25))     # the preview's angles (yaw 0: its front at the camera)
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def build(scale, design, materials, drop=()):
    m = geo.Model("dross_skull")
    m.bone(BONE, (0.0, 0.0, 0.0))
    parts = shapes.Parts(m, {}, default=BONE)
    design(shapes.Scaled(parts.part(BONE), scale))
    for c in list(m[BONE].cubes):
        if c.mat in drop:
            m[BONE].cubes.remove(c)
    zfight.fix(m)
    mats = materials()
    uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False))
    tex = texture.bake(m, mats)
    return m, tex


def faces(m):
    """Every face of the model at rest, in GeckoLib space (blocks): [(4 corners, 4 (u, v) texels, normal)]."""
    mats = m.pose_matrices({})
    out = []
    for c in m.cubes():
        T = mats[c.bone.name] @ c.cube_matrix()
        for _face, verts, uvs, normal in c.quads(m.tex_w, m.tex_h):
            pts = [(T @ np.append(p, 1.0))[:3] for p in verts]
            n = T[:3, :3] @ np.asarray(normal, dtype=float)
            out.append((pts, uvs, n / np.linalg.norm(n)))
    return out


def place(fs, mount):
    """The faces moved into their block: the skull faces north (its front is GeckoLib -z already). On the floor it
    stands centred, its lowest point on the floor (on top of a post); on a wall its back is against the south side."""
    pts = np.array([p for f in fs for p in f[0]])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    if mount == "floor":
        # (centred on its skull's middle, not its antlers': the antlers are as wide as they are)
        shift = np.array([0.5 - (lo[0] + hi[0]) / 2.0, -lo[1], 0.5 - (lo[2] + hi[2]) / 2.0])
    else:
        shift = np.array([0.5 - (lo[0] + hi[0]) / 2.0, WALL_HEIGHT - lo[1], 1.0 - WALL_GAP - hi[2]])
    return [([p + shift for p in f[0]], f[1], f[2]) for f in fs]


def write_obj(path, fs, m, mtl_name):
    lines = [f"# made by tools/huntsman/skulls.py", f"mtllib {mtl_name}.mtl", "usemtl skull"]
    vi = 0
    body = []
    for pts, uvs, n in fs:
        for p in pts:
            lines.append(f"v {p[0]:.5f} {p[1]:.5f} {p[2]:.5f}")
        for u, v in uvs:
            lines.append(f"vt {u / m.tex_w:.6f} {v / m.tex_h:.6f}")
        lines.append(f"vn {n[0]:.5f} {n[1]:.5f} {n[2]:.5f}")
        k = vi // 4
        body.append("f " + " ".join(f"{vi + j + 1}/{vi + j + 1}/{k + 1}" for j in range(4)))
        vi += 4
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines + body) + "\n")


def write_json(rel, data):
    path = os.path.join(ASSETS, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def composite(tex):
    """One texture: the solid parts, and the clear ice (with its alpha) where there's no solid."""
    base, ice = tex["base"].copy(), tex["ice"]
    see = (base[..., 3] < 255) & (ice[..., 3] > 0)
    base[see] = ice[see]
    return Image.fromarray(base, "RGBA")


def main():
    shots = []
    for sid, name, scale, design, materials, clear, icon, drop in SKULLS:
        m, tex = build(scale, design, materials, drop)
        folder = os.path.join(ASSETS, "models", "block", "hunt_skull")
        os.makedirs(folder, exist_ok=True)
        fs = faces(m)
        for mount in ("floor", "wall"):
            write_obj(os.path.join(folder, f"{sid}_{mount}.obj"), place(fs, mount), m, sid)
            write_json(f"models/block/hunt_skull/{sid}_{mount}.json", {
                "loader": "forge:obj",
                "model": f"dross:models/block/hunt_skull/{sid}_{mount}.obj",
                "flip_v": False,
                "automatic_culling": False,
                "render_type": "minecraft:translucent" if clear else "minecraft:cutout",
                "textures": {"skull": f"dross:block/hunt_skull/{sid}", "particle": f"dross:block/hunt_skull/{sid}"},
            })
        with open(os.path.join(folder, f"{sid}.mtl"), "w", encoding="utf-8", newline="\n") as f:
            f.write("# made by tools/huntsman/skulls.py\nnewmtl skull\nmap_Kd #skull\n")
        tex_path = os.path.join(ASSETS, "textures", "block", "hunt_skull", f"{sid}.png")
        os.makedirs(os.path.dirname(tex_path), exist_ok=True)
        composite(tex).save(tex_path)
        variants = {}
        for facing, rot in FACINGS:
            for mount in ("floor", "wall"):
                v = {"model": f"dross:block/hunt_skull/{sid}_{mount}"}
                if rot:
                    v["y"] = rot
                variants[f"facing={facing},wall={'true' if mount == 'wall' else 'false'}"] = v
        write_json(f"blockstates/{sid}.json", {"variants": variants})
        write_json(f"models/item/{sid}.json", {
            "parent": f"dross:block/hunt_skull/{sid}_floor",
            "gui_light": "front",
            "display": {
                "gui": {"rotation": [20, 205, 0], "translation": [0, 1, 0], "scale": [icon] * 3},
                "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [icon * 1.6] * 3},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [icon * 0.9] * 3},
                "head": {"rotation": [0, 180, 0], "translation": [0, 10, 0], "scale": [icon * 2.4] * 3},
                "thirdperson_righthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [icon * 0.9] * 3},
                "thirdperson_lefthand": {"rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [icon * 0.9] * 3},
                "firstperson_righthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [icon] * 3},
                "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [icon] * 3},
            },
        })
        pts = np.array([p for f in fs for p in f[0]])
        size = pts.max(axis=0) - pts.min(axis=0)
        print(f"  {sid}: {len(m.cubes())} boxes, {len(fs)} faces, texture {m.tex_w}x{m.tex_h}, "
              f"{size[0]:.2f} wide x {size[1]:.2f} tall x {size[2]:.2f} long (blocks)")
        row = [V.draw(m, tex, yaw=yaw, elevation=el, scale=6.0, figure=False,
                      bounds=V.window(m, yaw, el, figure=False, ground=False))
               for yaw, el in VIEWS]
        shots.append((name, row))
    w = max(sum(im.width for im in row) + 16 * len(row) for _n, row in shots)
    h = sum(max(im.height for im in row) + 40 for _n, row in shots)
    sheet = Image.new("RGB", (w, h), (34, 40, 50))
    d = ImageDraw.Draw(sheet)
    y = 0
    for name, row in shots:
        d.text((10, y + 8), name, fill=(230, 236, 244))
        x = 0
        for im in row:
            sheet.paste(im, (x, y + 28))
            x += im.width + 16
        y += max(im.height for im in row) + 40
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    sheet.save(PREVIEW)
    print("wrote the skulls' models, textures and", os.path.relpath(PREVIEW, ROOT))


if __name__ == "__main__":
    main()
