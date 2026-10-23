"""
Mounted skulls (the user, October 22, 2026: "make a 3D model like the Ice and Fire dragons for the Alpha Moose head and
also for regular male mooses, and instead of the skeleton [skulls] with the fences put those instead ... and for the
rhino horns as well with the 2 ice horns"; their answers: bare skulls, and players can get them): three blocks, each
exported as a baked 3D block model (Forge's OBJ loader: no block entity, so a village full of them costs nothing to
draw).

Each is a bone copy of the live animal's own head (the user, October 23, 2026: "the skull should be the exact size of
the head with the horns ... the Alpha Moose has a way bigger head than its skull, which should not be the case"; their
pick: "a bone copy of the real head"): the animal is built exactly as its in-game model is (tools/rimefall/rimemobs:
its bones, its design, its scale and variant), and its head's boxes (the head, its ears and antlers; the Rimehorn's
head with its two horns) are taken off it as they are, the same size and shape to the unit, and painted as bare bone:
its fur, face and muzzle bone, its eyes, nostrils and mouth dark holes, the antlers antler bone, the Rimehorn's horns
still clear ice. What wouldn't be a skull is left off: its ears, and the Alpha Moose's tack on its head (the bridle,
the bone plate on its face).

  alpha_moose_skull   the Alpha Moose's: old frosted, cracked bone, its great palm antlers hung with icicles
  moose_skull         a bull moose's (the wild moose's): clean bone, its antlers
  rimehorn_skull      a Rimehorn's (a male's): its long low head, its two horns of clear ice

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
from rimemobs import alpha_moose as ALPHA, moose as MOOSE, rimehorn as RIMEHORN  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
PREVIEW = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "skulls.png")
BONE = "skull"                 # the one bone every skull is built on


# ---------------------------------------------------------------- the heads, off the animals' own models

# the bones a skull's head is on, in each animal's model (rimemobs/moose.py and rimehorn.py BONES): the head and its
# antlers (its horns are on its head); not its ears, which a bare skull hasn't got (the Rimehorn's grow from its neck,
# so off it they'd float)
MOOSE_HEAD = ("head", "antlers")
RIMEHORN_HEAD = ("head",)
EARS = ("ear_l", "ear_r")
# what each of the head's materials becomes on the skull (anything else is left off: the tack, the fur tufts)
MOOSE_BONE = {"fur": "bone", "muzzle": "bone", "eye": "socket", "mouth": "socket", "antler": "antler",
              "icicle": "icicle"}
RIMEHORN_BONE = {"fur": "bone", "skin": "bone", "eye": "socket", "mouth": "socket", "horn": "horn"}


def head_of(mob, bones, bone_mats):
    """The animal built exactly as its in-game model is (rimekit/mobkit.build_model: its bones at its scale, its design
    with its build variant), and the boxes on its head's bones, their materials turned to bone. Returns (the boxes,
    {material left off: how many})."""
    m = geo.Model(f"dross_{mob.ID}")
    k = float(mob.K)
    for name, pivot, parent in mob.BONES:
        m.bone(name, tuple(float(v) * k for v in pivot), parent=parent)
    parts = shapes.Parts(m, getattr(mob, "ROUTES", {}), default="body")
    mob.DESIGN.build(parts, mob.BUILD_VARIANT)
    boxes, left = [], {}
    for b in bones:
        for c in m[b].cubes:
            if c.mat in bone_mats:
                c.mat = bone_mats[c.mat]
                boxes.append(c)
            else:
                left[c.mat] = left.get(c.mat, 0) + 1
    return boxes, left


def moose_materials(old):
    return {
        "bone": M.bone(seed=2101 if old else 2111, frost_amount=0.3 if old else 0.1, cracks=0.4 if old else 0.12),
        "antler": M.bone(seed=2103 if old else 2113, frost_amount=0.45 if old else 0.2, cracks=0.25 if old else 0.1),
        "icicle": M.solid(M.ICE, idx=3.6, seed=2105, grain=0.8, bev=0.9, outline=0.05),
        "socket": M.mouth(seed=2107),
    }


def rimehorn_materials():
    return {
        "bone": M.bone(seed=2201, frost_amount=0.15, cracks=0.18),
        "socket": M.mouth(seed=2203),
        "horn": M.ice(M.ICE, alpha=(165, 225), seed=2207, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=35),
    }


# id, name, the animal (its in-game model's module), its head's bones, what its materials become, the skull's
# materials, has clear ice (drawn translucent), and the materials left off it (a bull's antlers have no icicles: only
# the old Alpha Moose's are hung with them)
SKULLS = (
    ("alpha_moose_skull", "Alpha Moose Skull", ALPHA, MOOSE_HEAD, MOOSE_BONE, lambda: moose_materials(True), False,
     ()),
    ("moose_skull", "Moose Skull", MOOSE, MOOSE_HEAD, MOOSE_BONE, lambda: moose_materials(False), False, ("icicle",)),
    ("rimehorn_skull", "Rimehorn Skull", RIMEHORN, RIMEHORN_HEAD, RIMEHORN_BONE, rimehorn_materials, True, ()),
)
ICON_FIT = 0.9                # the inventory icon: its biggest side this share of a slot
WALL_GAP = 0.01               # blocks between a wall skull's back and the wall
WALL_HEIGHT = 0.22            # blocks: a wall skull's lowest point above its block's floor
VIEWS = ((25, 15), (90, 5), (155, 25))     # the preview's angles (yaw 0: its front at the camera)
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


def build(mob, bones, bone_mats, materials, drop=()):
    """The skull: the animal's head boxes (head_of) on one bone, as they sit on the animal (its model's units)."""
    boxes, left = head_of(mob, bones, bone_mats)
    m = geo.Model("dross_skull")
    m.bone(BONE, (0.0, 0.0, 0.0))
    for c in boxes:
        if c.mat not in drop:
            m[BONE]._add(c)
    if left:
        print(f"    (left off {mob.ID}'s head: {', '.join(f'{n} {k}' for k, n in sorted(left.items()))})")
    zfight.fix(m)
    mats = materials()
    uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False))
    tex = texture.bake(m, mats)
    return m, tex


def live_head(mob_id, bones):
    """The live animal's head in its in-game model (geo/entity/<id>/<id>.geo.json: its head's bones' boxes, turned as
    they are, at rest): wide, tall, long (blocks)."""
    path = os.path.join(ASSETS, "geo", "entity", mob_id, f"{mob_id}.geo.json")
    with open(path, encoding="utf-8") as f:
        g = json.load(f)["minecraft:geometry"][0]
    m = geo.Model("live_head")
    m.bone(BONE, (0.0, 0.0, 0.0))
    for b in g["bones"]:
        if b["name"] in bones:
            for c in b.get("cubes", []):
                m[BONE].add_abs(c["origin"], [max(1, round(v)) for v in c["size"]], "x", rotation=c.get("rotation"),
                                pivot=c.get("pivot"), inflate=c.get("inflate", 0.0))
    mats = m.pose_matrices({})
    pts = np.array([(mats[BONE] @ c.cube_matrix() @ np.append(p, 1.0))[:3]
                    for c in m.cubes() for p in _corners(c)])
    return pts.max(axis=0) - pts.min(axis=0)


def _corners(c):
    """A box's eight corners in GeckoLib space (blocks), unturned (cube_matrix turns them)."""
    if c.uv is None:
        c.uv = (0, 0)                 # (only its corners are wanted)
    out = []
    for _face, verts, _uvs, _n in c.quads(16, 16):
        out.extend(verts)
    return out


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
    for sid, name, mob, bones, bone_mats, materials, clear, drop in SKULLS:
        m, tex = build(mob, bones, bone_mats, materials, drop)
        pts = np.array([p for f in faces(m) for p in f[0]])
        icon = round(ICON_FIT / float((pts.max(axis=0) - pts.min(axis=0)).max()), 3)
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
        live = live_head(mob.ID, bones + EARS)
        print(f"  {sid}: {len(m.cubes())} boxes, {len(fs)} faces, texture {m.tex_w}x{m.tex_h}, "
              f"{size[0]:.2f} wide x {size[1]:.2f} tall x {size[2]:.2f} long (blocks); the live head with its ears "
              f"{live[0]:.2f} x {live[1]:.2f} x {live[2]:.2f}")
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
