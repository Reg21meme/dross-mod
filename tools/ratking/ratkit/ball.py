"""
The Rat King's poison ball: the projectile his cast launches. A clumpy, brownish-green ball of poison, six lumps of
different sizes stuck together (not a single block), with a couple of darker sickly spots, about half a block across,
slowly tumbling, and faintly glowing.

It writes (build.py runs it; its own folders, so build.py's stale-file cleanup of rat_king/ and rat/ never touches it):
  assets/dross/geo/entity/plague_ball/plague_ball.geo.json             the model
  assets/dross/animations/entity/plague_ball/plague_ball.animation.json  animation.plague_ball.spin (loop)
  assets/dross/textures/entity/plague_ball/plague_ball.png, _glow.png   the texture; the faint glow (alpha = strength)
  boss-previews/blightwood/rat_king/plague_ball.png                                 a few angles, at night, and the tumble

The model: bone `root` (pivot at the origin, never animated) > `ball` (the tumble; pivot at the ball's middle,
BALL_CENTRE_Y units above the origin, so a 0.5-block entity box holds it) > `lump_0` .. `lump_5` (each pulses a
little about its own middle). Units are Bedrock model units (16 = 1 block).
"""
import math
import os

import numpy as np
from PIL import Image

from kit import checks, geo, noise
from kit.anims import Animation, Pose, to_json
from kit.paint import bevel, paint_model, palette, pick, rgba

ID = "plague_ball"
PREFIX = "animation.plague_ball."
TEXTURE_WIDTH = 64
BALL_CENTRE_Y = 4.0             # units: the ball's middle above the model's origin (half of a 0.5-block box)

# The lumps: (middle relative to the ball's middle (units), size, tilt (degrees)). The core and five smaller lumps
# stuck round it, so the outline is knobbly from every side; about 9 units (0.55 blocks) across.
LUMPS = (
    ((0.0, 0.0, 0.0), (5, 5, 5), (0.0, 0.0, 0.0)),
    ((2.4, 1.3, 0.6), (4, 4, 4), (12.0, 20.0, -8.0)),
    ((-2.5, 0.9, -1.0), (3, 4, 3), (-10.0, 15.0, 18.0)),
    ((0.6, -2.4, 1.4), (4, 3, 4), (8.0, -25.0, 10.0)),
    ((-1.4, -1.6, 2.4), (3, 3, 3), (20.0, 10.0, -15.0)),
    ((0.9, 2.5, -2.3), (3, 3, 3), (-18.0, -12.0, 22.0)),
)

# Colours (the texture's own; the mod reads them only as pixels).
SLIME = palette("#22230f", "#323418", "#454a1f", "#5a6227", "#707b30", "#88943d", "#a0ab52")   # brownish green
MUD = palette("#2a2111", "#3a2e17", "#4c3d1e", "#5e4c26", "#6f5b2f")                              # brown mottling
SPOT = palette("#1a1410", "#261c16", "#33231c", "#3f2b20")                                        # sickly dark spots
PUS = palette("#8f8a3a", "#b5ad4c", "#d4cc6a")                                                    # a pale pustule
GLOW = palette("#1d4a12", "#2f6b1e", "#4f9a2b", "#79c93a", "#a6ec5b", "#d4ff8a")                  # poison green
MOTTLE = 0.62                   # how much of it is brown mud (noise above this)
SPOT_RADIUS = 1.9               # units: a sickly spot's size...
PUS_RADIUS = 0.6                # ...and its pale middle
SPOTS = ((1, (1, 1)), (3, (2, 1)), (4, (0, -1)))   # (lump, (axis, side)): spots on these lumps' outer faces
GLOW_FAINT = 34.0               # alpha of the faint glow all over it...
GLOW_VEIN = 120.0               # ...and of the brighter veins of poison running through it
GLOW_PUS = 90.0                 # ...and of the spots' pale middles
VEIN_WIDTH = 0.022              # how wide the veins are (share of the noise's range)

SPIN_T = 2.4                    # seconds per tumble (it turns once round two axes)
PULSE = 0.06                    # how much each lump swells and shrinks (share of its size)
PULSES_PER_LOOP = 2             # whole pulses per loop (seamless)
STEP = 0.1

PREVIEW_SCALE = 20              # pixels per unit in the preview
PREVIEW_MARGIN = 0.12           # blocks of room round it in each view


def build():
    """The model."""
    m = geo.Model(ID)
    root = m.bone("root", (0.0, 0.0, 0.0))
    ball = m.bone("ball", (0.0, BALL_CENTRE_Y, 0.0), parent=root)
    for i, (mid, size, tilt) in enumerate(LUMPS):
        c = (mid[0], BALL_CENTRE_Y + mid[1], mid[2])
        b = m.bone(f"lump_{i}", c, parent=ball)
        b.add_c((0.0, 0.0, 0.0), size, "slime", rotation=tilt if any(tilt) else None, tag=f"lump_{i}")
    for i, c in enumerate(m.cubes()):
        c.index = i
    checks.fix_coplanar(m)
    m.pack_uv(TEXTURE_WIDTH)
    return m


def _spot_centres(m):
    """Where the sickly spots sit (bind pose, units): on the outer faces SPOTS names."""
    out = []
    cubes = m.cubes()
    for lump, (axis, side) in SPOTS:
        c = next(cb for cb in cubes if cb.tag == f"lump_{lump}")
        mid = np.array(c.center())
        p = mid.copy()
        p[axis] += side * c.size[axis] / 2.0
        out.append(p)
    return np.array(out)


def painter(m):
    spots = _spot_centres(m)

    def paint(tx):
        P, N = tx.P, tx.N
        cen = np.array([tx.cubes[i].center() for i in tx.cube_index])
        k = (3.0 + (noise.fbm(P, 2.5, 2, 401) - 0.5) * 2.2 + bevel(P, N, cen, 1.0) * 1.1
             + np.where(N[:, 1] > 0.5, 0.4, np.where(N[:, 1] < -0.5, -0.7, 0.0))
             + (noise.hash_float(np.floor(P[:, 0]), np.floor(P[:, 1]), np.floor(P[:, 2]), 403) - 0.5) * 0.7)
        col = pick(SLIME, k)
        mott = noise.fbm(noise.warp(P, 1.0, 5.0, 405), 3.0, 2, 406)
        col = np.where((mott > MOTTLE)[:, None], pick(MUD, k - 1.0 - (mott - MOTTLE) * 6.0), col)
        d = np.min(np.linalg.norm(P[:, None, :] - spots[None, :, :], axis=2), axis=1)
        col = np.where((d < SPOT_RADIUS)[:, None], pick(SPOT, k - 1.5 + d), col)
        col = np.where((d < PUS_RADIUS)[:, None], pick(PUS, 2.0 - d * 2.0), col)
        col = np.where((tx.edge < 0.5)[:, None], col * 0.78, col)
        # The glow: faint all over, brighter along veins of poison running through it (and round the spots).
        vn = noise.fbm(noise.warp(P, 1.5, 4.0, 407), 3.5, 2, 408)
        vein = (np.abs(vn - 0.5) < VEIN_WIDTH) & (d >= SPOT_RADIUS)
        pus = d < PUS_RADIUS
        gk = np.where(vein, 4.0, np.where(pus, 5.0, 2.0)) + (noise.fbm(P, 1.5, 1, 409) - 0.5) * 1.2
        galpha = np.where(vein, GLOW_VEIN, np.where(pus, GLOW_PUS, np.where(d < SPOT_RADIUS, 0.0, GLOW_FAINT)))
        glow = rgba(pick(GLOW, gk), galpha)
        return dict(base=rgba(col), glow=glow, statue=np.zeros((tx.n, 4)))

    return paint


def spin():
    """animation.plague_ball.spin: a slow tumble, each lump swelling and shrinking a little in turn (loops)."""
    a = Animation("spin", SPIN_T, True)
    n = int(round(SPIN_T / STEP))
    for i in range(n + 1):
        t = i * STEP
        u = t / SPIN_T
        p = Pose()
        p.rot("ball", 360.0 * u, 0.0, 360.0 * u)
        for j in range(len(LUMPS)):
            p.scale(f"lump_{j}", 1.0 + PULSE * math.sin(2 * math.pi * (PULSES_PER_LOOP * u + j / len(LUMPS))))
        a.add(t, p)
    a.bake(0.2, 0.04, 0.004)
    return a


def size_blocks(m):
    lo, hi = m.bounds(m.pose_matrices())
    return (hi - lo) / 16.0


def textures(m):
    base, glow, _statue = paint_model(m, {"slime": painter(m)})
    return dict(base=base, glow=glow, rage=None)


def write(m, tex, anim, assets):
    """The model, the animation and the two textures, each in its own plague_ball/ folder."""
    import json
    geo_dir = os.path.join(assets, "geo", "entity", ID)
    anim_dir = os.path.join(assets, "animations", "entity", ID)
    tex_dir = os.path.join(assets, "textures", "entity", ID)
    for d in (geo_dir, anim_dir, tex_dir):
        os.makedirs(d, exist_ok=True)
    with open(os.path.join(geo_dir, f"{ID}.geo.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(m.to_json(visible_bounds=(1.0, 1.0, 0.25)), f, separators=(",", ":"))
    with open(os.path.join(anim_dir, f"{ID}.animation.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(to_json({"spin": anim}, prefix=PREFIX), f, separators=(",", ":"))
    Image.fromarray(tex["base"], "RGBA").save(os.path.join(tex_dir, f"{ID}.png"))
    Image.fromarray(tex["glow"], "RGBA").save(os.path.join(tex_dir, f"{ID}_glow.png"))


def preview(m, tex, anim, out_path):
    """A few angles in daylight, at night (what glows), and the tumble."""
    from . import views as V
    lift = {"root": ((0.0, 0.0, 0.0), (0.0, 1.5, 0.0), (1.0, 1.0, 1.0))}    # off the preview's ground
    s = size_blocks(m)
    times = np.linspace(0.0, SPIN_T, 7)[:-1]
    spin_poses = []
    for t in times:
        pose = dict(anim.sample(float(t)))
        pose["root"] = lift["root"]
        spin_poses.append(pose)
    bounds = V.fit(m, spin_poses, 35.0, 18.0, margin=PREVIEW_MARGIN, figure=False, floor=False)
    ims = []
    for label, yaw, el in (("front", 0.0, 10.0), ("side", 90.0, 10.0), ("back", 180.0, 10.0), ("from above", 35.0, 55.0),
                           ("three-quarter", 35.0, 18.0)):
        b_ = V.fit(m, [lift], yaw, el, margin=PREVIEW_MARGIN, figure=False, floor=False)
        ims.append(V.label(V.draw(m, tex, pose=lift, yaw=yaw, elevation=el, scale=PREVIEW_SCALE, bounds=b_,
                                  figure=False, grid=False), label, 16))
    b_ = V.fit(m, [lift], 35.0, 18.0, margin=PREVIEW_MARGIN, figure=False, floor=False)
    ims.append(V.label(V.draw(m, tex, pose=lift, yaw=35.0, elevation=18.0, scale=PREVIEW_SCALE, bounds=b_,
                              figure=False, grid=False, world_light=V.NIGHT_LIGHT, background=V.NIGHT_BG),
                       "at night (its glow)", 16))
    frames = []
    for t, pose in zip(times, spin_poses):
        frames.append(V.label(V.draw(m, tex, pose=pose, yaw=35.0, elevation=18.0, scale=PREVIEW_SCALE // 2,
                                     bounds=bounds, figure=False, grid=False), f"{t:.1f} s", 14, V.DIM))
    V.page("The Rat King's poison ball", f"What his cast launches: six lumps of brownish-green poison stuck together, "
           f"{s[0]:.2f} x {s[1]:.2f} x {s[2]:.2f} blocks, faintly glowing, tumbling once every {SPIN_T:.1f} s "
           f"(animation.plague_ball.spin).",
           [("Views", V.grid(ims, len(ims))), ("The tumble (each lump swells and shrinks a little)",
                                                V.grid(frames, len(frames)))], out_path)


def build_all(assets, previews_dir, write_files=True):
    m = build()
    tex = textures(m)
    anim = spin()
    s = size_blocks(m)
    print(f"Poison ball: {len(m.cubes())} lumps, texture {m.tex_w}x{m.tex_h}, "
          f"{s[0]:.2f} wide x {s[1]:.2f} tall x {s[2]:.2f} deep (blocks)")
    if write_files:
        write(m, tex, anim, assets)
    preview(m, tex, anim, os.path.join(previews_dir, "plague_ball.png"))
    return m, tex, anim
