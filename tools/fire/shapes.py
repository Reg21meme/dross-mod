"""
The Emberdactyl's and its young's collision shapes (the user, October 31, 2026: its head, neck and wings went through
walls, because its hitbox parts didn't cover its real wings as they beat): for each, a set of boxes that together hold
every corner of its model through every frame of its flying animations, and another set for its ground animations, so
the game can test them against the blocks round it and turn away before its wings reach a wall.

The model and the animations are the game's own files (made by tools/fire/mobs.py), read back the way trophies.py reads
them and posed with GeckoLib's maths (the golem kit's), so the boxes match what's drawn:

  adult   geo/entity/firemob/emberdactyla.geo.json, animations/entity/firemob/emberdactyl.animation.json   (E1A)
  young   geo/entity/firemob/emberyounga.geo.json,  animations/entity/firemob/emberyoung.animation.json    (E2A)

The sets: one for each animation but its death (ADULT_HOVER, YOUNG_GUST...; the fly animation's ADULT_FLY_ANIM), so
the game can use the set of the animation it's playing (EmberShapes.adult(name), young(name)), and two unions as
fallbacks: GROUND (perch, walk, snap: the animations the game counts as standing, EmberBoss.standing) and FLY (every
other animation but its death: hover, fly, glide, dive, screech, gust, grab). The frame is the one its hitbox parts use
(Emberdactyl.java's FLY and GROUND spots, placed by RimefallBodyPart.place): blocks, x its own left, y up from its feet
(the entity's position), z with its front at -z; the game turns them with its body.

How it's cut (every point of every box of the model, sampled at most SPACING blocks apart, in every frame):
  - its middle (the bones of its body, legs, neck, head, jaw, horns, crest and tail): cut along its length at its
    joints (where the head meets the neck, the neck's root, the tail's root; a part longer than MAX_PART blocks cut
    into equal pieces), each piece as wide as its points reach either side;
  - each wing (its arm, forearm, hand, finger and tip bones and the membranes on them): cut along x into equal slabs
    at most MAX_SLAB blocks wide, from its middle line (the arm's root is inside its body) out to its furthest wingtip;
    the two wings' boxes are mirror images of each other (each the union of both), so a banking glide is covered on
    both sides and the frame's handedness can't matter;
  - each box grown by PAD on every side (the sampling's give, and a little margin) and rounded outward to 0.05.
The boxes overlap where a wing's root crosses its body. Each covers its part's whole sweep through the animations
(the gust's wings closed over its back and then flung forward, the dive's swept back, the screech's head reared up),
so at any one moment it's a good deal bigger than the model there: the game turns away earlier.

  python tools/fire/shapes.py           writes src/main/java/com/reg21meme/dross/boss/emberdactyl/EmberShapes.java
  python tools/fire/shapes.py --check   only prints them

Needs numpy.
"""
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

from trophies import FIRE_ANIM, FIRE_GEO, load_anims, load_geo  # noqa: E402

JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "emberdactyl", "EmberShapes.java")
MOBS = (("ADULT", "emberdactyla", "emberdactyl", 33.0), ("YOUNG", "emberyounga", "emberyoung", 22.5))
GROUND = ("perch", "walk", "snap")      # the animations it stands in (EmberBoss.standing; its death isn't counted)
SKIP = ("death",)
STEP = 0.05                  # seconds between the frames looked at (the animations are recorded 20 a second)
SPACING = 0.125              # blocks between the points sampled through each box
PAD = 0.15                   # blocks every box is grown by
MAX_SLAB = 3.0               # blocks: a wing's boxes are at most this wide along x
MAX_PART = 4.0               # blocks: a part of the middle (head, neck, body, tail) longer than this is cut in pieces
MIDDLE = ("body", "neck", "head", "jaw", "tail", "leg_", "shin_", "foot_", "lid_", "bill", "horn_", "crest", "chunk_")


def is_middle(bone):
    """True for the bones of its middle (everything but its wings: arm, fore, hand, finger, tip and the membranes)."""
    return any(bone == p or (p.endswith("_") and bone.startswith(p)) for p in MIDDLE)


def bone_points(m):
    """{bone: (4, n) points through each of its boxes in the bone's own frame (GeckoLib space, blocks)}."""
    out = {}
    for b in m.bones:
        pts = []
        for c in b.cubes:
            ox, oy, oz = c.origin
            w, h, d = c.size
            i = c.inflate / 16.0
            lo = np.array([-(ox + w) / 16.0 - i, oy / 16.0 - i, oz / 16.0 - i])
            hi = np.array([-ox / 16.0 + i, (oy + h) / 16.0 + i, (oz + d) / 16.0 + i])
            axes = [np.linspace(lo[k], hi[k], max(2, int(math.ceil((hi[k] - lo[k]) / SPACING)) + 1)) for k in range(3)]
            X, Y, Z = np.meshgrid(*axes, indexing="ij")
            local = np.c_[X.ravel(), Y.ravel(), Z.ravel(), np.ones(X.size)].T
            pts.append(c.cube_matrix() @ local)
        if pts:
            out[b.name] = np.concatenate(pts, axis=1)
    return out


def posed(m, local, pose):
    """{bone: (n, 3) points in its own frame (blocks: x its left, y up, z its front at -z)} for one pose; bones
    scaled to nothing (a furled membrane on the ground) left out."""
    mats = m.pose_matrices(pose)
    out = {}
    for name, p in local.items():
        mx = mats[name]
        if abs(float(np.linalg.det(mx[:3, :3]))) < 1e-9:
            continue
        q = (mx @ p)[:3].T
        q[:, 0] = -q[:, 0]                          # (GeckoLib space's x is the model's right: its left is +x)
        out[name] = q
    return out


def frames(anims, names):
    for name in names:
        a = anims[name]
        n = max(1, int(round(a.length / STEP)))
        for k in range(n + 1):
            yield name, a.sample(min(a.length, k * STEP))


def cuts(lo, hi, longest):
    """Equal pieces of [lo, hi], each at most `longest` long: their edges."""
    n = max(1, int(math.ceil((hi - lo) / longest - 1e-9)))
    return [lo + (hi - lo) * i / n for i in range(n + 1)]


def scan(m, local, anims, names):
    """Each frame's points, (its animation, its middle's points, its wings' points), one frame at a time."""
    for name, pose in frames(anims, names):
        mid, wing = [], []
        for bone, pts in posed(m, local, pose).items():
            (mid if is_middle(bone) else wing).append(pts)
        yield name, np.concatenate(mid), np.concatenate(wing) if wing else np.zeros((0, 3))


def grow(lo, hi, y, z, x=None):
    """Widens the running extents lo/hi ([x, y, z]) to hold these points."""
    if not len(y):
        return
    if x is not None:
        hi[0] = max(hi[0], float(x.max()))
    lo[1] = min(lo[1], float(y.min()))
    hi[1] = max(hi[1], float(y.max()))
    lo[2] = min(lo[2], float(z.min()))
    hi[2] = max(hi[2], float(z.max()))


def load(model_id, mob):
    """The game's model and animations of one mob, and its boxes' points: (model, {name: Animation}, points)."""
    m = load_geo(os.path.join(FIRE_GEO, f"{model_id}.geo.json"), f"shapes_{model_id}")
    anims = load_anims(os.path.join(FIRE_ANIM, f"{mob}.animation.json"), f"animation.{mob}.")
    return m, anims, bone_points(m)


def sets_of(anims):
    """Which animations each set covers: {"*fly": its flying ones, "*ground": its ground ones (the unions), then each
    animation on its own, {name: [name]}}."""
    names = [n for n in anims if n not in SKIP]
    out = {"*fly": [n for n in names if n not in GROUND], "*ground": [n for n in names if n in GROUND]}
    out.update({n: [n] for n in names})
    return out


def measure(loaded, names):
    """The boxes of one model (`loaded`: load's) through these animations, and what was found."""
    m, anims, local = loaded
    joints = {b.name: float(b.pivot[2]) / 16.0 for b in m.bones}      # (Bedrock units: z as in its frame)
    # First, how far everything reaches (and each animation on its own, for the report).
    each = {}
    z_front, z_back, reach = math.inf, -math.inf, 0.0
    for name, mid, wing in scan(m, local, anims, names):
        z_front = min(z_front, float(mid[:, 2].min()))
        z_back = max(z_back, float(mid[:, 2].max()))
        reach = max(reach, float(np.abs(wing[:, 0]).max()) if len(wing) else 0.0)
        both = np.concatenate([mid, wing])
        lo, hi = each.setdefault(name, ([math.inf] * 3, [-math.inf] * 3))
        grow(lo, hi, both[:, 1], both[:, 2], np.abs(both[:, 0]))
    # Its middle cut along its length at its joints (where the head meets the neck, the neck's root, the tail's root),
    # each wing in slabs along x out to its furthest tip (the first from its middle line: the arm's root is inside its
    # body).
    head, neck, tail = joints["head"], joints["neck"], joints["tail"]
    edges = []
    for a, b in ((z_front, head), (head, neck), (neck, tail), (tail, z_back)):
        if b > a:
            edges += cuts(a, b, MAX_PART)[:-1]
    edges.append(z_back)
    slabs = cuts(0.0, reach, MAX_SLAB)
    parts = [([math.inf] * 3, [0.0, -math.inf, -math.inf]) for _ in range(len(edges) - 1)]
    wings = [([math.inf] * 3, [0.0, -math.inf, -math.inf]) for _ in range(len(slabs) - 1)]
    for _name, mid, wing in scan(m, local, anims, names):
        k = np.clip(np.searchsorted(edges, mid[:, 2], side="right") - 1, 0, len(parts) - 1)
        for i, (lo, hi) in enumerate(parts):
            sel = k == i
            grow(lo, hi, mid[sel, 1], mid[sel, 2], np.abs(mid[sel, 0]))
        ax = np.abs(wing[:, 0])
        k = np.clip(np.searchsorted(slabs, ax, side="right") - 1, 0, len(wings) - 1)
        for i, (lo, hi) in enumerate(wings):
            sel = k == i
            grow(lo, hi, wing[sel, 1], wing[sel, 2])
    boxes = []
    for (lo, hi), a, b in zip(parts, edges, edges[1:]):
        if hi[1] > -math.inf:
            boxes.append([-hi[0], lo[1], a, hi[0], hi[1], b])
    left, right = [], []
    for (lo, hi), a, b in zip(wings, slabs, slabs[1:]):
        if hi[1] > -math.inf:
            # (the two sides mirror images of each other: each slab holds both wings' points in it)
            left.append([a, lo[1], lo[2], b, hi[1], hi[2]])
            right.append([-b, lo[1], lo[2], -a, hi[1], hi[2]])
    boxes += left + right
    # Grown and rounded outward.
    out = []
    for x0, y0, z0, x1, y1, z1 in boxes:
        grown = [x0 - PAD, y0 - PAD, z0 - PAD, x1 + PAD, y1 + PAD, z1 + PAD]
        out.append([math.floor(v * 20.0) / 20.0 if k < 3 else math.ceil(v * 20.0) / 20.0 for k, v in enumerate(grown)])
    # Then the check: every point of every frame in a box.
    B = np.array(out)
    points = outside = 0
    for _name, mid, wing in scan(m, local, anims, names):
        pts = np.concatenate([mid, wing])
        inside = np.zeros(len(pts), dtype=bool)
        for bx in B:
            inside |= np.all((pts >= bx[:3]) & (pts <= bx[3:]), axis=1)
        points += len(pts)
        outside += int((~inside).sum())
    half = max(hi[0] for lo, hi in parts if hi[1] > -math.inf)
    return out, dict(names=names, half=half, reach=reach, points=points, outside=outside, each=each)


def describe(label, boxes, info):
    B = np.array(boxes)
    lo, hi = B[:, :3].min(axis=0), B[:, 3:].max(axis=0)
    print(f"{label}: {len(boxes)} boxes over {', '.join(info['names'])}; its middle reaching {info['half']:.2f} "
          f"either side, its wings out to {info['reach']:.2f}; all together x {lo[0]:.2f}..{hi[0]:.2f}, "
          f"y {lo[1]:.2f}..{hi[1]:.2f}, z {lo[2]:.2f}..{hi[2]:.2f} (blocks); {info['points']} points, "
          f"{info['outside']} outside a box")
    for name, (lo, hi) in info["each"].items():
        print(f"    {name}: reaches {hi[0]:.2f} out, y {lo[1]:.2f}..{hi[1]:.2f}, z {lo[2]:.2f}..{hi[2]:.2f}")
    for b in boxes:
        print("    {" + ", ".join(f"{v:.2f}" for v in b) + "}")


JAVA_HEAD = '''package com.reg21meme.dross.boss.emberdactyl;

/**
 * The Emberdactyl's and its young's collision shapes, so the game can test them against the blocks round it (its head,
 * neck and wings went through walls: its hitbox parts didn't cover its wings as they beat). For each animation it has
 * (but its death), the boxes that together hold every corner of its model through every frame of it
 * ({@code ADULT_HOVER}, {@code YOUNG_GUST}..., its fly animation's {@code ADULT_FLY_ANIM} and {@code YOUNG_FLY_ANIM}:
 * {@link #adult(String)} and {@link #young(String)} give the one it's playing); and, as fallbacks, the union of its
 * flying animations ({@code *_FLY}: {FLY})
 * and of its ground ones ({@code *_GROUND}: {GROUND}).
 * <p>
 * Each row is one box {@code {x0, y0, z0, x1, y1, z1}} in blocks, in the frame its hitbox parts use
 * ({@link Emberdactyl}'s FLY and GROUND spots, placed by {@code RimefallBodyPart.place}): x its own left, y up from its
 * feet (its position), z with its front at -z; turn them with its body yaw the same way ({@code x = X + lx * cos +
 * lz * sin}, {@code z = Z + lx * sin - lz * cos}). The first rows are its middle (head, neck, body, tail) from front
 * to back, then its wings in slabs from its middle line out to the tips, left then right (mirror images of each
 * other). Each box covers that part's whole sweep through its animation(s), grown by 0.15 blocks, so at any one
 * moment it's bigger than the model there.
 * <p>
 * <b>Generated by {@code tools/fire/shapes.py} from the game's own model and animation files; don't edit by hand.</b>
 */
public final class EmberShapes
{
'''


WHO = {"ADULT": "The adult (E1A, 33 blocks across its wings)",
       "YOUNG": "Its young reborn (E2A, 22.5 blocks across its wings)"}
NL = "\n"


def java_rows(name, doc, boxes):
    rows = ("," + NL).join("            {" + ", ".join(f"{v:.2f}D" for v in b) + "}" for b in boxes)
    return "".join(line + NL for line in (
        "    /**",
        f"     * {doc[0]}",
        f"     * {doc[1]}",
        "     */",
        f"    public static final double[][] {name} = {{",
        rows,
        "    };"))


def java_const(const, key):
    """The Java constant of a set: ADULT_FLY and ADULT_GROUND the unions ("*fly", "*ground"), ADULT_HOVER... each
    animation's (its "fly" animation ADULT_FLY_ANIM, so the union keeps its name)."""
    if key.startswith("*"):
        return f"{const}_{key[1:].upper()}"
    return f"{const}_{key.upper()}" + ("_ANIM" if key in ("fly", "ground") else "")


def java_switch(const, keys):
    """The method that gives the set of the animation it's playing (its flying set for any other name)."""
    who = "the adult's" if const == "ADULT" else "its young's"
    lines = ["    /**",
             f"     * The boxes of {who} animation {{@code animation}} (its name in its animation file: \"hover\",",
             f"     * \"perch\"...); {{@link #{const}_FLY}} for any other name (or null).",
             "     */",
             f"    public static double[][] {const.lower()}(String animation)",
             "    {",
             "        if (animation == null)",
             "        {",
             f"            return {const}_FLY;",
             "        }",
             "        return switch (animation)",
             "        {"]
    lines += [f"            case \"{k}\" -> {java_const(const, k)};" for k in keys]
    lines += [f"            default -> {const}_FLY;",
              "        };",
              "    }"]
    return "".join(line + NL for line in lines)


def main(argv):
    found = {}
    order = {}
    for const, model_id, mob, span in MOBS:
        loaded = load(model_id, mob)
        sets = sets_of(loaded[1])
        order[const] = list(sets)
        for key, names in sets.items():
            boxes, info = measure(loaded, names)
            found[(const, key)] = (boxes, info)
            describe(f"{java_const(const, key)} ({model_id}, {span:g} blocks across its wings)", boxes, info)
            if info["outside"]:
                raise SystemExit(f"{java_const(const, key)}: {info['outside']} points outside every box")
    if "--check" in argv:
        return
    fly = ", ".join(found[("ADULT", "*fly")][1]["names"])
    ground = ", ".join(found[("ADULT", "*ground")][1]["names"])
    parts = [JAVA_HEAD.replace("{FLY}", fly).replace("{GROUND}", ground)]
    for const, _model_id, _mob, _span in MOBS:
        for key in order[const]:
            boxes, info = found[(const, key)]
            what = {"*fly": "flying (all its flying animations)",
                    "*ground": "standing (all its ground animations)"}.get(key, f"in its {key} animation")
            B = np.array(boxes)
            lo, hi = B[:, :3].min(axis=0), B[:, 3:].max(axis=0)
            doc = (f"{WHO[const]} {what}: {len(boxes)} boxes,",
                   f"together x {lo[0]:.2f} to {hi[0]:.2f}, y {lo[1]:.2f} to {hi[1]:.2f}, "
                   f"z {lo[2]:.2f} to {hi[2]:.2f}.")
            parts.append(java_rows(java_const(const, key), doc, boxes) + NL)
    for const, _model_id, _mob, _span in MOBS:
        parts.append(java_switch(const, [k for k in order[const] if not k.startswith("*")]) + NL)
    parts.append("    private EmberShapes()" + NL + "    {" + NL + "    }" + NL + "}" + NL)
    with open(JAVA, "w", encoding="utf-8", newline=NL) as f:
        f.write("".join(parts))
    print(f"Wrote {os.path.relpath(JAVA, ROOT)}")


if __name__ == "__main__":
    main(sys.argv[1:])
