"""
Per-face UVs for the Rimefall candidates: every face gets its own little rectangle of texture, and the faces nobody can
see get none at all.

The golem kit packs whole boxes (box UV: each box takes 2(d+w) x (d+h) texels, its six faces in a fixed layout). The
round-2 giants (the 25-block hand, the fish under the lake) are mostly big stacked boxes whose tops and bottoms are
buried in their neighbours, so box UV spends most of a huge texture on faces that are never drawn. GeckoLib also reads
Bedrock's per-face UV ("uv": {"north": {"uv": [u, v], "uv_size": [w, h]}, ...}; a face that isn't listed isn't built at
all: BakedModelFactory.buildQuad returns null and GeoRenderer.renderCube skips it), and builds those quads with exactly
the box-UV vertices (VertexSet.verticesForQuad, no mirror), so the kit's painting (TexelMap) and previews work
unchanged once Cube.quads() hands them the per-face rectangles.

A face is dropped when nobody can see it:
  - it lies inside one opaque box (all four corners inside it, or on its surface facing into it); see-through boxes
    (ice, curtains, cloth with holes) never hide anything;
  - it's under the ground (the showcase's floor): facing down at the floor, or entirely below it.
A box left with no faces is removed.
"""
import numpy as np

from kit import geo

from . import zfight

EPS = 0.002                 # blocks: a corner this close to an occluder's surface counts as on it
GROUND_EPS = 0.03           # blocks: a downward face this close to the floor (or under it) can't be seen
TEXTURE_WIDTHS = (128, 256, 512, 1024, 2048)
MAX_TEXELS = 2048 * 2048    # the most a texture may hold before faces drop to a lower texel density


def _face_dims(cube):
    """Each face's size in units (u across, v down), in GeckoLib's box-UV orientation."""
    w, h, d = cube.size
    return {"west": (d, h), "east": (d, h), "north": (w, h), "south": (w, h), "up": (w, d), "down": (w, d)}


class FaceCube(geo.Cube):
    """A cube with per-face UVs: `face_uv` = {face: (u, v, us, vs)}; faces not in it aren't built."""

    face_uv = None

    def quads(self, tex_w, tex_h):
        ox, oy, oz = self.origin
        w, h, d = self.size
        i = self.inflate / 16.0
        x0, x1 = -(ox + w) / 16.0 - i, -ox / 16.0 + i
        y0, y1 = oy / 16.0 - i, (oy + h) / 16.0 + i
        z0, z1 = oz / 16.0 - i, (oz + d) / 16.0 + i
        blb, brb, tlb, trb = (x0, y0, z0), (x0, y0, z1), (x0, y1, z0), (x0, y1, z1)
        tlf, trf, blf, brf = (x1, y1, z0), (x1, y1, z1), (x1, y0, z0), (x1, y0, z1)
        verts = {
            "west": [trb, tlb, blb, brb], "east": [tlf, trf, brf, blf],
            "north": [tlb, tlf, blf, blb], "south": [trf, trb, brb, brf],
            "up": [trb, trf, tlf, tlb], "down": [blb, blf, brf, brb],
        }
        out = []
        for face in geo.FACES:
            if face not in self.face_uv:
                continue
            u0, v0, us, vs = self.face_uv[face]
            uvs = [(u0 + us, v0), (u0, v0), (u0, v0 + vs), (u0 + us, v0 + vs)]
            out.append((face, [np.array(p, dtype=float) for p in verts[face]], uvs, geo.FACE_NORMALS[face]))
        return out


# ---------------------------------------------------------------- which faces can be seen


def _local_frames(cubes, mats):
    """Each cube's inverse matrix (model -> its own unturned GeckoLib frame) and its bounds there (blocks)."""
    inv = []
    lo = []
    hi = []
    for c in cubes:
        M = mats[c.bone.name] @ c.cube_matrix()
        inv.append(np.linalg.inv(M))
        ox, oy, oz = c.origin
        w, h, d = c.size
        i = c.inflate / 16.0
        lo.append((-(ox + w) / 16.0 - i, oy / 16.0 - i, oz / 16.0 - i))
        hi.append((-ox / 16.0 + i, (oy + h) / 16.0 + i, (oz + d) / 16.0 + i))
    return np.array(inv), np.array(lo), np.array(hi)


def hidden_faces(model, opaque):
    """
    {(cube index, face name)} of faces nobody can see. `opaque(cube)` says whether a cube hides what's inside it.
    """
    cubes = model.cubes()
    mats = model.pose_matrices()
    # Every face's corners (model space, blocks) and normal, in the kit's face order.
    corners = []
    normals = []
    owner = []
    names = []
    for ci, c in enumerate(cubes):
        for (n, _off, pts), face in zip(zfight._faces(c, mats[c.bone.name]), ("west", "east", "down", "up", "north",
                                                                              "south")):
            corners.append(pts)
            normals.append(n)
            owner.append(ci)
            names.append(face)
    corners = np.array(corners)            # (F, 4, 3)
    normals = np.array(normals)            # (F, 3)
    owner = np.array(owner)
    F = len(owner)
    hidden = np.zeros(F, dtype=bool)
    # Under the floor.
    ys = corners[:, :, 1]
    hidden |= ys.max(axis=1) < -GROUND_EPS
    hidden |= (normals[:, 1] < -0.9) & (ys.max(axis=1) < GROUND_EPS)
    # Inside an opaque box.
    inv, lo, hi = _local_frames(cubes, mats)
    flat = np.concatenate([corners.reshape(-1, 3), np.ones((F * 4, 1))], axis=1)       # (4F, 4)
    vol = np.array([float(np.prod(c.size)) for c in cubes])
    for bi, b in enumerate(cubes):
        if not opaque(b):
            continue
        q = (flat @ inv[bi].T)[:, :3].reshape(F, 4, 3)
        inside = np.all((q >= lo[bi] - EPS) & (q <= hi[bi] + EPS), axis=(1, 2))
        inside &= owner != bi
        if not inside.any():
            continue
        cand = np.nonzero(inside & ~hidden)[0]
        if not len(cand):
            continue
        # A face lying on b's surface, facing out of b the same way as b's own face there, is only hidden by a
        # bigger box (two equal boxes mustn't hide each other's faces).
        qc = q[cand]
        nl = normals[cand] @ inv[bi][:3, :3].T
        on = np.zeros(len(cand), dtype=bool)
        for axis in range(3):
            at_lo = np.all(np.abs(qc[:, :, axis] - lo[bi][axis]) < EPS, axis=1) & (nl[:, axis] < -0.5)
            at_hi = np.all(np.abs(qc[:, :, axis] - hi[bi][axis]) < EPS, axis=1) & (nl[:, axis] > 0.5)
            on |= at_lo | at_hi
        a_vol = vol[owner[cand]]
        wins = (vol[bi] > a_vol) | ((vol[bi] == a_vol) & (bi > owner[cand]))
        hidden[cand[~on | wins]] = True
    return {(int(owner[f]), names[f]) for f in np.nonzero(hidden)[0]}


# ---------------------------------------------------------------- packing


def _skyline_pack(rects, width):
    """
    Packs rectangles (w, h) into a texture `width` wide, tallest first, each at the lowest spot (leftmost on a tie).
    Returns their (u, v) and the height used.
    """
    order = sorted(range(len(rects)), key=lambda k: (-rects[k][1], -rects[k][0]))
    sky = np.zeros(width, dtype=np.int64)
    pos = [None] * len(rects)
    for k in order:
        w, h = rects[k]
        if w > width:
            return None, None
        win = np.lib.stride_tricks.sliding_window_view(sky, w).max(axis=1)
        x = int(np.argmin(win))
        y = int(win[x])
        pos[k] = (x, y)
        sky[x:x + w] = y + h
    return pos, int(sky.max())


def optimize(model, opaque, density=1.0):
    """
    Drops the faces nobody sees (and boxes left with none), then gives every remaining face its own rectangle of
    texture, `density` texels per unit (lowered automatically if the texture would hold more than MAX_TEXELS).
    Sets model.tex_w / tex_h. Returns (faces dropped, boxes removed).
    """
    cubes = model.cubes()
    gone = hidden_faces(model, opaque)
    removed = 0
    keep = {}
    for ci, c in enumerate(cubes):
        faces = [f for f in geo.FACES if (ci, f) not in gone]
        if not faces:
            c.bone.cubes.remove(c)
            removed += 1
            continue
        c.__class__ = FaceCube
        keep[id(c)] = faces
    cubes = model.cubes()
    for i, c in enumerate(cubes):
        c.index = i
    dens = density
    while True:
        rects = []
        owners = []
        for c in cubes:
            dims = _face_dims(c)
            for f in keep[id(c)]:
                us, vs = dims[f]
                rects.append((max(1, int(round(us * dens))), max(1, int(round(vs * dens)))))
                owners.append((c, f))
        total = sum(w * h for w, h in rects)
        if total <= MAX_TEXELS * 0.8 or dens <= 0.25:
            break
        dens *= 0.75
    best = None
    for width in TEXTURE_WIDTHS:
        if width * width * 2 < total:
            continue
        pos, used = _skyline_pack(rects, width)
        if pos is None:
            continue
        tex_h = 16
        while tex_h < used:
            tex_h *= 2
        if tex_h > 2 * width:
            continue
        if best is None or width * tex_h < best[0] * best[1]:
            best = (width, tex_h, pos)
    if best is None:
        width = TEXTURE_WIDTHS[-1]
        pos, used = _skyline_pack(rects, width)
        tex_h = 16
        while tex_h < used:
            tex_h *= 2
        best = (width, tex_h, pos)
    width, tex_h, pos = best
    for c in cubes:
        c.face_uv = {}
    for (c, f), (u, v), (us, vs) in zip(owners, pos, rects):
        c.face_uv[f] = (u, v, us, vs)
    model.tex_w, model.tex_h = width, tex_h
    model.texel_density = dens
    return len(gone), removed


# ---------------------------------------------------------------- the .geo.json


def to_json(model, visible_bounds):
    """The kit's Model.to_json with per-face UVs."""
    bones = []
    for b in model.bones:
        entry = {"name": b.name}
        if b.parent is not None:
            entry["parent"] = b.parent.name
        entry["pivot"] = [geo._num(v) for v in b.pivot]
        if any(abs(v) > 1e-9 for v in b.rotation):
            entry["rotation"] = [geo._num(v) for v in b.rotation]
        cubes = []
        for c in b.cubes:
            uv = {f: {"uv": [int(r[0]), int(r[1])], "uv_size": [int(r[2]), int(r[3])]} for f, r in c.face_uv.items()}
            ce = {"origin": [geo._num(v) for v in c.origin], "size": list(c.size), "uv": uv}
            if c.inflate:
                ce["inflate"] = geo._num(c.inflate)
            if c.rotation and any(c.rotation):
                ce["pivot"] = [geo._num(v) for v in (c.pivot if c.pivot else c.center())]
                ce["rotation"] = [geo._num(v) for v in c.rotation]
            cubes.append(ce)
        if cubes:
            entry["cubes"] = cubes
        bones.append(entry)
    vw, vh, voff = visible_bounds
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + model.identifier,
                "texture_width": model.tex_w,
                "texture_height": model.tex_h,
                "visible_bounds_width": vw,
                "visible_bounds_height": vh,
                "visible_bounds_offset": [0, voff, 0],
            },
            "bones": bones,
        }],
    }
