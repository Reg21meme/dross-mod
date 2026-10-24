"""
The Alpha Moose's ice spikes (its cracking stamp; the user, October 23, 2026: "for the slam attack for the moose when it
summons ice ... I want to use that clear ice we created for some of the models: smaller spikes on the ground clumped up,
then the main [one] a giant [spike] around 1.5 blocks tall that slows you down and knocks you into the air"): one spike
of the models' see-through ice (the Rimehorn's horns' material, rimekit's mats.ice), a column tapering to its point with
two shards leaning off its foot, one block tall. The fight shows it as block displays (HuntFight.spikes): small ones
clumped round you, scaled down and leaning, then the big one, 1.5 blocks tall, growing up under you.

  python tools/huntsman/ice_spike.py

Writes (src/main/resources/assets/dross/): models/block/hunt_ice_spike.obj, .mtl and .json (Forge's OBJ loader, drawn
see-through), textures/block/hunt_ice_spike.png, blockstates/hunt_ice_spike.json; and the preview
boss-previews/rimefall/huntsman/ice_spike.png.
"""
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

import skulls as K  # noqa: E402  (its block-model writer, and the paths to the Rimefall kit)
from kit import geo  # noqa: E402
from rimekit import mats as M, texture, uvpack, zfight  # noqa: E402
from rimekit import view as V  # noqa: E402

ID = "hunt_ice_spike"
PREVIEW = os.path.join(K.ROOT, "boss-previews", "rimefall", "huntsman", "ice_spike.png")
# the column: (width, height) in model units from its foot up, 16 a block
COLUMN = ((5, 4), (4, 4), (3, 3), (2, 3), (1, 2))
# the shards off its foot: (x, z, width, height, lean in degrees about x, about z), leaning out from it
SHARDS = ((-3.0, 0.5, 2, 9, 0.0, -30.0), (2.5, -2.0, 2, 7, 26.0, 24.0), (1.0, 3.0, 2, 6, -28.0, 8.0))


def build():
    m = geo.Model("dross_ice_spike")
    m.bone(K.BONE, (0.0, 0.0, 0.0))
    y = 0
    for w, h in COLUMN:
        m[K.BONE].add_abs((-w / 2.0, y, -w / 2.0), (w, h, w), "ice")
        y += h
    for x, z, w, h, rx, rz in SHARDS:
        m[K.BONE].add_abs((x - w / 2.0, 0.0, z - w / 2.0), (w, h, w), "ice", rotation=(rx, 0.0, rz), pivot=(x, 0.0, z))
    zfight.fix(m)
    mats = {"ice": M.ice(M.ICE, alpha=(165, 225), seed=2307, cracks=0.4, bubbles=0.03, glow_pal=M.GLOW, glow_alpha=35)}
    uvpack.optimize(m, lambda c: False)
    tex = texture.bake(m, mats)
    return m, tex


def main():
    m, tex = build()
    fs = K.faces(m)
    pts = np.array([p for f in fs for p in f[0]])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    shift = np.array([0.5, -lo[1], 0.5])          # (its foot's middle on the block's floor middle)
    fs = [([p + shift for p in f[0]], f[1], f[2], f[3]) for f in fs]
    folder = os.path.join(K.ASSETS, "models", "block")
    K.write_obj(os.path.join(folder, f"{ID}.obj"), fs, m, ID)
    with open(os.path.join(folder, f"{ID}.mtl"), "w", encoding="utf-8", newline="\n") as f:
        f.write("# made by tools/huntsman/ice_spike.py\nnewmtl skull\nmap_Kd #spike\n")
    K.write_json(f"models/block/{ID}.json", {
        "loader": "forge:obj",
        "model": f"dross:models/block/{ID}.obj",
        "flip_v": False,
        "automatic_culling": False,
        "render_type": "minecraft:translucent",
        "textures": {"spike": f"dross:block/{ID}", "particle": f"dross:block/{ID}"},
    })
    K.write_json(f"blockstates/{ID}.json", {"variants": {"": {"model": f"dross:block/{ID}"}}})
    tex_path = os.path.join(K.ASSETS, "textures", "block", f"{ID}.png")
    K.composite(tex).save(tex_path)
    size = hi - lo
    print(f"  {ID}: {len(m.cubes())} boxes, {len(fs)} faces, texture {m.tex_w}x{m.tex_h}, "
          f"{size[0]:.2f} x {size[1]:.2f} x {size[2]:.2f} blocks")
    row = [V.draw(m, tex, yaw=yaw, elevation=el, scale=12.0, figure=False,
                  bounds=V.window(m, yaw, el, figure=False, ground=False))
           for yaw, el in ((25, 15), (115, 10))]
    from PIL import Image
    sheet = Image.new("RGB", (sum(im.width for im in row) + 16 * len(row), max(im.height for im in row)), (34, 40, 50))
    x = 0
    for im in row:
        sheet.paste(im, (x, 0))
        x += im.width + 16
    sheet.save(PREVIEW)
    print("wrote the ice spike's model, texture and", os.path.relpath(PREVIEW, K.ROOT))


if __name__ == "__main__":
    main()
