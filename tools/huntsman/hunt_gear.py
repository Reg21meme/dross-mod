"""
The Huntsman's gear lying in front of his door while he sleeps (the user, November 18, 2026, after seeing it in the game:
"the hunter, his bow is that small, make a 3D model just for it"; a plain bow item was a small flat picture on the
snow): his own bow (its string with it), one of his ice-fletched arrows and his bola, lifted off his in-game model
(rimemobs/huntsman.py: the bones `bow_held`, `string_top`, `string_bot`, `arrow_hand`, `bola_held`, at his own scale and
in his own materials) and laid down flat, each as a block model the game shows only as a block display
(boss/huntsman/village/HuntsmanAsleep). So they're his things at his size: the bow about 2.7 blocks tip to tip.

  python tools/huntsman/hunt_gear.py

Writes (src/main/resources/assets/dross/): models/block/hunt_<bow|arrow|bola>.obj, .mtl and .json (Forge's OBJ loader),
textures/block/hunt_<...>.png, blockstates/hunt_<...>.json; and the preview boss-previews/rimefall/huntsman/gear.png.
"""
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

import skulls as K  # noqa: E402  (its block-model writer, and the paths to the Rimefall kit)
from kit import geo  # noqa: E402
from rimekit import shapes, texture, uvpack, zfight  # noqa: E402
from rimekit import view as V  # noqa: E402
from rimemobs import huntsman as HUNTSMAN  # noqa: E402

PREVIEW = os.path.join(K.ROOT, "boss-previews", "rimefall", "huntsman", "gear.png")
# id, his bones it's made of, how it's turned to lie flat (degrees about x, about z: the bow and the bola hang
# upright in his fist, the arrow lies level already), and whether each of its boxes is dropped onto the ground on its
# own (the bola: laid down whole its cords hung in the air between its thicker ice weights, the user, November 19, 2026:
# "the bola needs to be flat on the ground, not floating"; dropped, its weights and cords all lie on the snow)
GEAR = (
    ("hunt_bow", ("bow_held", "string_top", "string_bot"), (0.0, 90.0), False),
    ("hunt_arrow", ("arrow_hand",), (0.0, 0.0), False),
    ("hunt_bola", ("bola_held",), (90.0, 0.0), True),
)


def build(bones):
    """The boxes on those bones of his in-game model (built as mobkit builds it: his bones at his scale, his design on
    foot), on one bone, and their texture."""
    mob = HUNTSMAN
    full = geo.Model("dross_huntsman")
    k = float(mob.K)
    for name, pivot, parent in mob.BONES:
        full.bone(name, tuple(float(v) * k for v in pivot), parent=parent)
    parts = shapes.Parts(full, getattr(mob, "ROUTES", {}), default="body")
    mob.DESIGN.build(parts, mob.BUILD_VARIANT)
    m = geo.Model("dross_hunt_gear")
    m.bone(K.BONE, (0.0, 0.0, 0.0))
    for b in bones:
        for c in full[b].cubes:
            m[K.BONE]._add(c)
    zfight.fix(m)
    all_mats = mob.DESIGN.materials(mob.BUILD_VARIANT)
    used = {c.mat for c in m.cubes()}
    mats = {name: all_mats[name] for name in used}
    uvpack.optimize(m, lambda c: getattr(mats[c.mat], "opaque", False))
    tex = texture.bake(m, mats)
    see_through = any(not getattr(mats[n], "opaque", True) for n in used)
    return m, tex, see_through


def turn(rx, rz):
    a, b = math.radians(rx), math.radians(rz)
    mx = np.array([[1, 0, 0], [0, math.cos(a), -math.sin(a)], [0, math.sin(a), math.cos(a)]])
    mz = np.array([[math.cos(b), -math.sin(b), 0], [math.sin(b), math.cos(b), 0], [0, 0, 1]])
    return mz @ mx


def lying_faces(m, rot, drop):
    """Every face of the model at rest, turned by {@code rot} (GeckoLib space, blocks): [(4 corners, 4 (u, v), normal,
    material)]; with {@code drop}, each box lowered on its own until its lowest corner is at 0."""
    mats = m.pose_matrices({})
    out = []
    for c in m.cubes():
        T = mats[c.bone.name] @ c.cube_matrix()
        fs = []
        for _face, verts, uvs, normal in c.quads(m.tex_w, m.tex_h):
            pts = [rot @ (T @ np.append(p, 1.0))[:3] for p in verts]
            n = rot @ (T[:3, :3] @ np.asarray(normal, dtype=float))
            fs.append((pts, uvs, n / np.linalg.norm(n), c.mat))
        if drop:
            low = min(p[1] for f in fs for p in f[0])
            fs = [([p - np.array([0.0, low, 0.0]) for p in f[0]], f[1], f[2], f[3]) for f in fs]
        out += fs
    return out


def main():
    shots = []
    for gid, bones, (rx, rz), drop in GEAR:
        m, tex, see_through = build(bones)
        rot = turn(rx, rz)
        fs = lying_faces(m, rot, drop)
        pts = np.array([p for f in fs for p in f[0]])
        lo, hi = pts.min(axis=0), pts.max(axis=0)
        mid = (lo + hi) / 2.0
        shift = np.array([0.5 - mid[0], -lo[1], 0.5 - mid[2]])   # (lying on its block's floor, its middle in the middle)
        fs = [([p + shift for p in f[0]], f[1], f[2], f[3]) for f in fs]
        folder = os.path.join(K.ASSETS, "models", "block")
        K.write_obj(os.path.join(folder, f"{gid}.obj"), fs, m, gid)
        with open(os.path.join(folder, f"{gid}.mtl"), "w", encoding="utf-8", newline="\n") as f:
            f.write("# made by tools/huntsman/hunt_gear.py\nnewmtl skull\nmap_Kd #gear\n")
        K.write_json(f"models/block/{gid}.json", {
            "loader": "forge:obj",
            "model": f"dross:models/block/{gid}.obj",
            "flip_v": False,
            "automatic_culling": False,
            "render_type": "minecraft:translucent" if see_through else "minecraft:cutout",
            "textures": {"gear": f"dross:block/{gid}", "particle": f"dross:block/{gid}"},
        })
        K.write_json(f"blockstates/{gid}.json", {"variants": {"": {"model": f"dross:block/{gid}"}}})
        K.composite(tex).save(os.path.join(K.ASSETS, "textures", "block", f"{gid}.png"))
        size = hi - lo
        print(f"  {gid}: {len(m.cubes())} boxes, {len(fs)} faces, texture {m.tex_w}x{m.tex_h}, lying "
              f"{size[0]:.2f} x {size[1]:.2f} x {size[2]:.2f} blocks{' (see-through)' if see_through else ''}")
        shots.append(V.draw(m, tex, yaw=30, elevation=25, scale=10.0, figure=False,
                            bounds=V.window(m, 30, 25, figure=False, ground=False)))
    from PIL import Image
    sheet = Image.new("RGB", (sum(im.width for im in shots) + 16 * len(shots), max(im.height for im in shots)),
                      (34, 40, 50))
    x = 0
    for im in shots:
        sheet.paste(im, (x, 0))
        x += im.width + 16
    sheet.save(PREVIEW)
    print("wrote his gear's models and", os.path.relpath(PREVIEW, K.ROOT))


if __name__ == "__main__":
    main()
