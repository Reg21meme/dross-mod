"""
The fight's Cracking stage (between 60% and 35% of its health): the shell is breaking up, with lava glowing through.

  loose_pose(loose, amount)  - the loose shell plates' offsets at this much cracking (0-1), as an animation pose: each
                               plate is pushed out and tilted a little, so lava glows in the gap round it. The mod
                               adds the same offsets to the bones (CinderColossusModel), from GolemDesigns.LoosePlate.
  paint_cracks(m, ...)       - the <id>_cracks texture: bright lava cracks running across the shell plates (and the
                               rims of the loose plates burning). The mod draws it full-bright over the golem, fading
                               it in as the cracking grows (GolemCrackLayer).

A design lists its loose plates as LOOSE = [(bone, push, tilt, from), ...]: push in units along the plate's parent's
axes, tilt in degrees about the plate's centre, and `from` (0-1): how cracked the shell is when that plate comes
loose (it pops out over POP more of the cracking).
"""
import numpy as np

from . import materials as M
from . import noise
from .paint import TexelMap, rest_world

POP = 0.12   # a loose plate pops out over this much of the cracking (0-1), written into GolemDesigns.java


def amount_for(frm, amount):
    """How far out a plate that comes loose at `frm` is when the shell is cracked this much (0-1, smoothed)."""
    x = max(0.0, min(1.0, (amount - frm) / POP))
    return x * x * (3.0 - 2.0 * x)


def loose_pose(loose, amount=1.0):
    """The loose plates' offsets as a pose {bone: (rot, pos, scale)} at this much cracking."""
    out = {}
    for bone, push, tilt, frm in loose:
        k = amount_for(frm, amount)
        if k > 0.0:
            out[bone] = (tuple(v * k for v in tilt), tuple(v * k for v in push), (1.0, 1.0, 1.0))
    return out


def add_pose(a, b):
    """Two poses' offsets added together (rotation and position add up, scale multiplies)."""
    out = dict(a or {})
    for bone, (rot, pos, scl) in (b or {}).items():
        if bone in out:
            r0, p0, s0 = out[bone]
            out[bone] = (tuple(r0[i] + rot[i] for i in range(3)), tuple(p0[i] + pos[i] for i in range(3)),
                         tuple(s0[i] * scl[i] for i in range(3)))
        else:
            out[bone] = (rot, pos, scl)
    return out


# Parts of the shell the cracks stay off: the face and jaw (the face has its own cracks), the spikes, horns, studs,
# rim teeth and the beard (too thin to crack nicely).
SKIP = ("skull", "brow", "jaw", "beard", "spike", "horn", "stud", "tooth", "knuckles", "cuff")


def paint_cracks(model, loose_names=(), seed=811, cell=11.0, width=0.65, share=0.55, fine_cell=5.5, fine_width=0.4,
                 fine_share=0.3, rim=0.9):
    """
    The cracks texture (HxWx4 uint8): bright lava cracks on the shell plates, transparent everywhere else.
      cell, width, share:  the big cracks (Voronoi cells about `cell` units across; `share` of their borders crack)
      fine_*:              smaller cracks branching between them
      rim:                 the loose plates' edges burn this far in (units)
    The cracks run on across neighbouring plates (they're worked out where the plates sit in the rest pose).
    """
    tm = TexelMap(model)
    out = np.zeros((tm.H, tm.W, 4))
    Pw, _Nw = rest_world(model, tm)
    shell_ids = []
    loose_ids = []
    for c in tm.cubes:
        kinds = [b.kind for b in c.bone.ancestors()]
        if "shell" not in kinds or "prop" in kinds or c.tag == "crater":
            continue
        if any(s in c.bone.name for s in SKIP):
            continue
        shell_ids.append(c.index)
        if c.bone.name in loose_names:
            loose_ids.append(c.index)
    sel = np.nonzero(np.isin(tm.cube, shell_ids))
    P = Pw[sel]
    q = noise.warp(P, 1.6, 9.0, seed)
    id1, id2, border, _f1, _c1 = noise.voronoi(q, cell, seed + 1)
    big = noise.hash_ids(np.minimum(id1, id2), np.maximum(id1, id2), seed + 2) < share
    fid1, fid2, fborder, _ff, _fc = noise.voronoi(noise.warp(P, 1.0, 5.0, seed + 3), fine_cell, seed + 4)
    fine = noise.hash_ids(np.minimum(fid1, fid2), np.maximum(fid1, fid2), seed + 5) < fine_share
    d_big = np.where(big, border, 99.0)
    d_fine = np.where(fine, fborder, 99.0)
    # The fine cracks only branch off near the big ones (they don't cover every plate evenly).
    d_fine = np.where(d_big < 4.0, d_fine, 99.0)
    crack = (d_big < width) | (d_fine < fine_width)
    core = (d_big < width * 0.45) | (d_fine < fine_width * 0.4)
    halo = ~crack & ((d_big < width + 0.7) | (d_fine < fine_width + 0.4))
    # The loose plates' rims burn where lava shows round them: in ragged stretches, not a neat outline.
    edge = np.minimum(np.minimum(tm.A[sel], tm.FW[sel] - tm.A[sel]), np.minimum(tm.B[sel], tm.FH[sel] - tm.B[sel]))
    on_loose = np.isin(tm.cube[sel], loose_ids)
    ragged = noise.fbm(P, 3.0, 2, seed + 7)
    reach = rim * (0.4 + 1.2 * ragged)
    burning = on_loose & (edge < reach) & (ragged > 0.38)
    crack |= burning
    core |= burning & (edge < reach * 0.4)
    flicker = noise.fbm(P, 2.5, 2, seed + 6)
    idx = np.where(core, 5.2 + (flicker - 0.5) * 1.6, 3.9 + (flicker - 0.5) * 1.6)
    col = M.pick(M.LAVA, idx)
    rgba = np.zeros((len(P), 4))
    rgba[:, :3] = col
    rgba[:, 3] = np.where(crack, 255.0, 0.0)
    rgba[halo, :3] = M.LAVA[2]
    rgba[halo, 3] = 95.0
    out[sel] = rgba
    return np.clip(out, 0, 255).astype(np.uint8)
