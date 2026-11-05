"""
Hitboxes that follow the animations (the user, November 5, 2026, after playing 0.3.0: "the hermit crab hitbox needs to
be more accurate ... same with the Emberdactyl: make more specific boxes around them, not just 1 giant box: a box
around each leg, then their giant claw, their shell; and the same with the Emberdactyl, its body and wings as it
swings"). For each model, its pieces (the Emberdactyl's bill, skull, horns, neck, body, tail, legs and each wing in
pieces; the
Cinder Hermit's shell, body, eyes, big claw, small claw and four legs), and for every animation (but its death) the box
round each piece every STEP seconds, posed with GeckoLib's maths from the game's own model and animation files (as
trophies.py reads them), so the boxes are where the model is drawn.

The game (boss/fire/FireHitboxes.java) reads them: each piece is a hitbox of its own that moves with the animation
(boss/fire/ShapedPart.java), and the Emberdactyl's pieces are also what blocks stop (its wings against a wall).

  models    emberdactyla (the adult, E1A; its other looks share its skeleton), emberyounga (its young, E2A),
            mobhermita ... mobhermitj (the Cinder Hermit in each of its eight shells, each with its own animations)
  frame     blocks, x its own left, y up from its feet (its position), z with its front at -z (the frame its parts
            use; the game turns them with its body)
  file      src/main/resources/assets/dross/hitboxes/<model>.bin, big-endian (Java's DataInputStream):
              "DRHB", short version (1), short pieces, each piece's name (writeUTF),
              short animations, each: its name (writeUTF), float seconds between frames, short frames, then
              frames x pieces x 6 shorts (x0, y0, z0, x1, y1, z1 in sixteenths of a block); a piece with nothing to
              show in that frame (its bones scaled to nothing) is 32767 in all six.

How a piece is made: the boxes (cubes) of its bones; a long piece (the neck, the tail, a wing's forearm, hand and
fingertip) cut into equal pieces along its length at rest, each cube going to the piece its middle is in. Each box is
the box round its cubes' corners in that frame, grown by PAD.

  python tools/fire/hitboxes.py          writes the files (and prints each model's pieces at rest)
  python tools/fire/hitboxes.py --check  only prints

Needs numpy.
"""
import math
import os
import struct
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

from trophies import FIRE_ANIM, FIRE_GEO, load_anims, load_geo  # noqa: E402

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "hitboxes")
STEP = 0.1          # seconds between frames (two ticks)
PAD = 0.1           # blocks every box is grown by
HIDDEN = 32767
SKIP = ("death",)

# (piece, its bones, how many pieces it's cut into along its length)
EMBER = [
    ("bill", ("bill", "jaw", "crest"), 2),
    ("skull", ("head", "lid_l", "lid_r"), 1),
    ("horn_l", ("horn_l",), 2),
    ("horn_r", ("horn_r",), 2),
    ("neck", ("neck",), 2),
    ("body", ("body",), 1),
    ("tail", ("tail",), 2),
    ("leg_l", ("leg_l", "shin_l", "foot_l"), 1),
    ("leg_r", ("leg_r", "shin_r", "foot_r"), 1),
]
for side in ("l", "r"):
    EMBER += [
        (f"wing_{side}_arm", (f"arm_{side}", f"mem_arm_{side}"), 1),
        (f"wing_{side}_fore", (f"fore_{side}", f"mem_fore_{side}"), 2),
        (f"wing_{side}_hand", (f"hand_{side}", f"mem_hand_{side}"), 2),
        (f"wing_{side}_tip", (f"finger_{side}", f"mem_finger_{side}", f"tip_{side}", f"mem_tip_{side}"), 2),
    ]
# The young's bill, horns and half moon are all in its head bone: its head cut in three along its length, its jaw in two.
YOUNG = [("head", ("head", "lid_l", "lid_r"), 3), ("jaw", ("jaw",), 2)] + [p for p in EMBER if p[0] not in ("bill", "skull", "horn_l", "horn_r")]
HERMIT = [
    ("shell", ("shell",), 1),
    ("body", ("body",), 1),
    ("eyes", ("eye_l", "eye_r"), 1),
    ("claw_big", ("claw_r", "fore_r", "pincer_r", "finger_r"), 1),
    ("claw_small", ("claw_l", "fore_l", "pincer_l"), 1),
    ("leg_al", ("lega_l", "kneea_l", "anklea_l"), 1),
    ("leg_bl", ("legb_l", "kneeb_l", "ankleb_l"), 1),
    ("leg_ar", ("lega_r", "kneea_r", "anklea_r"), 1),
    ("leg_br", ("legb_r", "kneeb_r", "ankleb_r"), 1),
]
# (model, its animation file, the animations' prefix's mob, its pieces)
MODELS = [("emberdactyla", "emberdactyl", "emberdactyl", EMBER), ("emberyounga", "emberyoung", "emberyoung", YOUNG)]
MODELS += [(f"mobhermit{k}", f"mobhermit{k}", "hermit", HERMIT) for k in "abcdefij"]


def corners(cube):
    """The cube's eight corners in its bone's own frame (4 x 8), as shapes.bone_points samples them."""
    ox, oy, oz = cube.origin
    w, h, d = cube.size
    i = cube.inflate / 16.0
    lo = (-(ox + w) / 16.0 - i, oy / 16.0 - i, oz / 16.0 - i)
    hi = (-ox / 16.0 + i, (oy + h) / 16.0 + i, (oz + d) / 16.0 + i)
    pts = np.array([[x, y, z, 1.0] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2])]).T
    return cube.cube_matrix() @ pts


def pieces_of(m, groups):
    """[(name, [(bone, corners 4x8)...])]: each group's cubes, a long group cut along its length at rest."""
    bones = {b.name: b for b in m.bones}
    rest = m.pose_matrices({})
    out = []
    for name, names, cut in groups:
        cubes = [(b, corners(c)) for b in names if b in bones for c in bones[b].cubes]
        if not cubes:
            raise SystemExit(f"{m.identifier if hasattr(m, 'identifier') else ''}: no cubes for the piece {name}")
        if cut <= 1:
            out.append((name, cubes))
            continue
        mids = np.array([(rest[b] @ c)[:3].mean(axis=1) for b, c in cubes])
        mid = mids.mean(axis=0)
        vals, vecs = np.linalg.eigh(np.cov((mids - mid).T))
        axis = vecs[:, np.argmax(vals)]
        t = (mids - mid) @ axis
        edges = np.linspace(t.min(), t.max() + 1e-6, cut + 1)
        k = np.clip(np.searchsorted(edges, t, side="right") - 1, 0, cut - 1)
        for i in range(cut):
            sel = [cubes[j] for j in range(len(cubes)) if k[j] == i]
            if sel:
                out.append((f"{name}_{i}", sel))
    return out


def box_at(mats, cubes):
    """The box round these cubes in one pose (blocks: x its left, y up, z its front at -z), or None if nothing shows."""
    pts = []
    for bone, c in cubes:
        mx = mats[bone]
        if abs(float(np.linalg.det(mx[:3, :3]))) < 1e-9:
            continue
        q = (mx @ c)[:3].T
        q[:, 0] = -q[:, 0]
        pts.append(q)
    if not pts:
        return None
    p = np.concatenate(pts)
    lo = p.min(axis=0) - PAD
    hi = p.max(axis=0) + PAD
    return np.concatenate([lo, hi])


def measure(model_id, anim_file, mob, groups):
    m = load_geo(os.path.join(FIRE_GEO, f"{model_id}.geo.json"), f"hitboxes_{model_id}")
    anims = load_anims(os.path.join(FIRE_ANIM, f"{anim_file}.animation.json"), f"animation.{mob}.")
    pieces = pieces_of(m, groups)
    out = {}
    for name, a in anims.items():
        if name in SKIP:
            continue
        n = max(1, int(math.ceil(a.length / STEP - 1e-9))) + 1
        frames = []
        for k in range(n):
            mats = m.pose_matrices(a.sample(min(a.length, k * STEP)))
            frames.append([box_at(mats, cubes) for _name, cubes in pieces])
        out[name] = frames
    return [name for name, _c in pieces], out


def write(model_id, names, anims):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"{model_id}.bin")

    def utf(s):
        b = s.encode("utf-8")
        return struct.pack(">H", len(b)) + b

    data = bytearray(b"DRHB" + struct.pack(">hh", 1, len(names)))
    for n in names:
        data += utf(n)
    data += struct.pack(">h", len(anims))
    for anim, frames in anims.items():
        data += utf(anim) + struct.pack(">fh", STEP, len(frames))
        for boxes in frames:
            for b in boxes:
                if b is None:
                    data += struct.pack(">6h", *([HIDDEN] * 6))
                else:
                    lo = [math.floor(v * 16.0) for v in b[:3]]
                    hi = [math.ceil(v * 16.0) for v in b[3:]]
                    data += struct.pack(">6h", *(lo + hi))
    with open(path, "wb") as f:
        f.write(bytes(data))
    return path, len(data)


def describe(model_id, names, anims):
    first = next(iter(anims))
    rest = anims[first][0]
    print(f"{model_id}: {len(names)} pieces, {len(anims)} animations ({', '.join(f'{a} {len(f)}' for a, f in anims.items())} frames)")
    for name, b in zip(names, rest):
        if b is None:
            print(f"    {name}: (nothing shows in {first})")
        else:
            print(f"    {name}: x {b[0]:.2f}..{b[3]:.2f}, y {b[1]:.2f}..{b[4]:.2f}, z {b[2]:.2f}..{b[5]:.2f} "
                  f"({b[3] - b[0]:.2f} x {b[4] - b[1]:.2f} x {b[5] - b[2]:.2f})")


def main(argv):
    only = [a for a in argv if not a.startswith("--")]
    for model_id, anim_file, mob, groups in MODELS:
        if only and not any(model_id.startswith(o) for o in only):
            continue
        names, anims = measure(model_id, anim_file, mob, groups)
        describe(model_id, names, anims)
        if "--check" not in argv:
            path, size = write(model_id, names, anims)
            print(f"    wrote {os.path.relpath(path, ROOT)} ({size // 1024} KB)")


if __name__ == "__main__":
    main(sys.argv[1:])
