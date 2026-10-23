"""
The Huntsman's trophy candidates (the user, October 23, 2026: "give me 10-15 options, like the trophy you get when you
kill the bosses"): like the Cinder Colossus's and the Rat King's, a 1x1x1 decorative block holding a miniature of the
boss in one pose, his real models and textures scaled down to fit the block. This is phase 1: previews only, for the
user to pick from (the game's trophies come after, built like boss/ratking/trophy).

Each candidate is a moment of their own animations (rimemobs/huntsman.py on foot, huntsman_rider.py in the saddle,
alpha_moose.py), the rider carried by the saddle as in the game (tools/rimefall/wildhunt.py). Every one is drawn inside
the outline of the block it fills: the figure turned on the block (the turn among TURNS that makes it biggest; a long
moose fits best along the block's diagonal) and scaled so its width and depth fill at most FIT_SIDE of it and its height
FIT_HEIGHT, its lowest point on the block's floor.

  python tools/rimefall/hunt_trophies.py

Writes boss-previews/rimefall/huntsman/trophies/candidates/: trophy_<nn>_<key>.png (two views each), candidates.png (all
of them, numbered and named) and index.html.
"""
import math
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import render  # noqa: E402
from rimekit import mobkit, view as V  # noqa: E402
from rimemobs import alpha_moose, huntsman, huntsman_rider  # noqa: E402

OUT = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "trophies", "candidates")
FIT_SIDE = 0.92           # the figure's width and depth fill at most this much of the block (as the other trophies)...
FIT_HEIGHT = 0.96         # ...and its height this much
TURNS = (0.0, 15.0, 30.0, 45.0, -15.0, -30.0, -45.0)   # the figure's turns on its block tried (degrees)
BLOCK_PIXELS = 330        # the block's side in the pictures
VIEWS = ((32.0, 14.0), (118.0, 10.0))   # (camera yaw from the figure's front, elevation): a candidate's own "views" first
EDGE = (120, 150, 176)    # the block's outline (behind the figure), and its floor
FLOOR = (44, 52, 64)
FRONT_EDGE_ALPHA = 0.28   # the outline's front edges drawn faintly over the figure

# The candidates: number, file key, name, what it shows, and the figure: "pair" (him in the saddle: his animation and
# moment, the moose's under him and its moment), "foot" (him on his own), "moose" (the Alpha Moose on its own) or
# "fallen" (both of them dead, side by side). "end": an animation's last frame.
TROPHIES = (
    dict(number=1, views=((40.0, 12.0), (95.0, 8.0)), key="wild_hunt", name="The Wild Hunt", fig="pair", rider=("ride_gallop", 0.35), moose=("run", 0.35),
         blurb="At full gallop, his spear up, the moose's antlers forward"),
    dict(number=2, views=((-55.0, 12.0), (-100.0, 8.0)), key="riding_down", name="Riding Down", fig="pair", rider=("ride_thrust", 0.62), moose=("run", 0.2),
         blurb="His spear driven down past the antlers at a gallop"),
    dict(number=3, views=((25.0, 12.0), (75.0, 10.0)), key="horn_of_the_hunt", name="Horn of the Hunt", fig="pair", rider=("ride_horn", 1.6), moose=("idle", 1.0),
         blurb="In the saddle, his horn at his lips"),
    dict(number=4, views=((60.0, 10.0), (-40.0, 14.0)), key="rain_of_arrows", name="Rain of Arrows", fig="pair", rider=("ride_rain", 0.95), moose=("idle", 0.5),
         blurb="His bow drawn high, three arrows on the string"),
    dict(number=5, views=((80.0, 10.0), (35.0, 14.0)), key="circling_shot", name="The Circling Shot", fig="pair", rider=("ride_shoot", 1.05), moose=("run", 0.6),
         blurb="Shooting out to his left at a gallop"),
    dict(number=6, views=((45.0, 18.0), (-50.0, 12.0)), key="the_bola", name="The Bola", fig="pair", rider=("ride_bola", 0.7), moose=("walk", 0.4),
         blurb="The bola whirled over his head"),
    dict(number=7, views=((90.0, 6.0), (35.0, 12.0)), key="the_rearing", name="The Rearing", fig="pair", rider=("ride_idle", 0.0), moose=("rear", 1.0),
         blurb="The Alpha Moose up on its hind legs, him still in the saddle"),
    dict(number=8, views=((-35.0, 10.0), (55.0, 10.0)), key="full_draw", name="Full Draw", fig="foot", foot=("aimed_shot", 1.5),
         blurb="On foot, his bow drawn to his chin"),
    dict(number=9, views=((35.0, 10.0), (100.0, 8.0)), key="leaping_strike", name="Leaping Strike", fig="foot", foot=("leap_strike", 0.85),
         blurb="In mid-leap, his spear over his shoulder"),
    dict(number=10, views=((-40.0, 10.0), (60.0, 10.0)), key="the_javelin", name="The Javelin", fig="foot", foot=("throw_spear", 0.45),
         blurb="His spear drawn back to throw"),
    dict(number=11, views=((35.0, 12.0), (-60.0, 10.0)), key="spear_sweep", name="Spear Sweep", fig="foot", foot=("spear_combo", 1.2),
         blurb="The combo's last blow, the spear swept round"),
    dict(number=12, views=((30.0, 10.0), (100.0, 10.0)), key="hunters_call", name="The Hunter's Call", fig="foot", foot=("horn_blow", 1.6),
         blurb="Standing, blowing his horn"),
    dict(number=13, views=((60.0, 8.0), (-30.0, 10.0)), key="alphas_bellow", name="Alpha's Bellow", fig="moose", moose=("bellow", 1.1),
         blurb="The Alpha Moose roaring, its neck stretched out"),
    dict(number=14, views=((70.0, 8.0), (20.0, 12.0)), key="antler_toss", name="Antler Toss", fig="moose", moose=("antler_toss", 0.6),
         blurb="Its antlers flung up as it tosses you"),
    dict(number=15, views=((40.0, 32.0), (-50.0, 26.0)), key="hunter_hunted", name="The Hunter Hunted", fig="fallen", moose=("death", "end"),
         foot=("death", "end"), blurb="Both of them fallen, side by side"),
)
FALLEN_APART = -40.0      # units: in "fallen", the Huntsman lies this far to the moose's right


def build():
    mm, mt = mobkit.build_model(alpha_moose, verbose=False)
    rm, rt = mobkit.build_model(huntsman_rider, verbose=False)
    fm, ft = mobkit.build_model(huntsman, verbose=False)
    ma = alpha_moose.animations(mobkit.MobRig(mm, alpha_moose))
    rrig = mobkit.MobRig(rm, huntsman_rider)
    wanted = {t["rider"][0] for t in TROPHIES if t["fig"] == "pair"}
    ra = {name: getattr(huntsman_rider, name)(rrig) for name in wanted}
    ra.update(huntsman_rider.carries(rrig, (mm, ma)))
    fa = huntsman.animations(mobkit.MobRig(fm, huntsman))
    return (mm, mt["alpha_moose"]), (rm, rt["huntsman_rider"]), (fm, ft["huntsman"]), ma, ra, fa


def _at(a, t):
    return float(a.length) if t == "end" else float(t % a.length if a.loop is True else min(t, a.length))


def layers(trophy, moose, rider, foot, ma, ra, fa):
    """The models to draw and their poses: [(model, textures)], [pose]."""
    fig = trophy["fig"]
    if fig == "pair":
        rname, rt = trophy["rider"]
        mname, mt = trophy["moose"]
        mp = ma[mname].sample(_at(ma[mname], mt))
        rp = dict(ra[rname].sample(_at(ra[rname], rt)))
        rp[huntsman_rider.CARRY] = ra[f"carry_{mname}"].sample(_at(ma[mname], mt))[huntsman_rider.CARRY]
        return [moose, rider], [mp, rp]
    if fig == "foot":
        name, t = trophy["foot"]
        return [foot], [fa[name].sample(_at(fa[name], t))]
    if fig == "moose":
        name, t = trophy["moose"]
        return [moose], [ma[name].sample(_at(ma[name], t))]
    mname, mt = trophy["moose"]
    fname, ftm = trophy["foot"]
    fp = dict(fa[fname].sample(_at(fa[fname], ftm)))
    rot, pos, scl = fp.get("root", ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
    fp["root"] = (rot, (pos[0] + FALLEN_APART, pos[1], pos[2]), scl)
    return [moose, foot], [ma[mname].sample(_at(ma[mname], mt)), fp]


def _corners(model, pose):
    """Every corner of the boxes that show (a hidden prop is scaled to nothing at its bone's pivot: left out)."""
    from kit import geo
    mats = model.pose_matrices(pose)
    pts = []
    for b in model.bones:
        m = mats[b.name]
        if abs(np.linalg.det(m[:3, :3])) < 1e-6:
            continue
        for c in b.cubes:
            cm = m @ c.cube_matrix()
            ox, oy, oz = c.origin
            sx, sy, sz = c.size
            i = c.inflate
            for dx in (-i, sx + i):
                for dy in (-i, sy + i):
                    for dz in (-i, sz + i):
                        q = cm @ np.append(geo.gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        pts.append(geo.bedrock(q[:3]))
    return np.array(pts)


def world_points(models, poses):
    """Every box corner of the figure that shows, in blocks (world axes, as the renderer's camera sees them)."""
    pts = np.concatenate([_corners(m[0], p) for m, p in zip(models, poses)])
    return np.stack([pts[:, 0] / 16.0, pts[:, 1] / 16.0, -pts[:, 2] / 16.0], axis=1)


def _turn(points, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    r = np.array([[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]])
    return points @ r.T


def fit(points):
    """The best turn and the block that holds the figure turned so: (turn, side in blocks, cube corners in world)."""
    best = None
    for turn in TURNS:
        q = _turn(points, turn)
        lo, hi = q.min(axis=0), q.max(axis=0)
        side = max((hi[0] - lo[0]) / FIT_SIDE, (hi[2] - lo[2]) / FIT_SIDE, (hi[1] - lo[1]) / FIT_HEIGHT)
        if best is None or side < best[1] - 1e-6:
            mid = (lo + hi) / 2.0
            corners = np.array([[mid[0] + dx * side / 2.0, lo[1] + dy * side, mid[2] + dz * side / 2.0]
                                for dy in (0, 1) for dx in (-1, 1) for dz in (-1, 1)])
            best = (turn, side, _turn(corners, -turn))
    return best


EDGES = ((0, 1), (0, 2), (1, 3), (2, 3), (4, 5), (4, 6), (5, 7), (6, 7), (0, 4), (1, 5), (2, 6), (3, 7))


def picture(models, poses, cube, side, yaw, el):
    """One view of the figure in its block's outline."""
    view = render.camera(yaw, el)
    v = cube @ view.T
    margin = side * 0.12
    bounds = (v[:, 0].min() - margin, v[:, 1].min() - margin, v[:, 0].max() + margin, v[:, 1].max() + margin)
    scale = BLOCK_PIXELS / (16.0 * side)
    im = V.draw(models[0][0], models[0][1], yaw=yaw, elevation=el, scale=scale, bounds=bounds,
                pose=poses if len(models) > 1 else poses[0], extra=models[1:], grid=False, figure=False)
    px = (v[:, 0] - bounds[0]) * scale * 16.0
    py = (v[:, 1] - bounds[1]) * scale * 16.0
    figure = (np.asarray(im).astype(int) != np.array(V.BG)).any(axis=2)
    behind = Image.new("RGB", im.size, V.BG)
    d = ImageDraw.Draw(behind)
    d.polygon([(px[i], py[i]) for i in (0, 1, 3, 2)], fill=FLOOR)
    for a, b in EDGES:
        d.line([(px[a], py[a]), (px[b], py[b])], fill=EDGE, width=2)
    out = np.where(figure[:, :, None], np.asarray(im), np.asarray(behind))
    # the edges nearest the camera, faint over the figure
    near = int(np.argmin(v[:, 2]))
    over = Image.fromarray(out.astype(np.uint8), "RGB")
    lines = Image.new("RGB", im.size, (0, 0, 0))
    mask = Image.new("L", im.size, 0)
    dl, dm = ImageDraw.Draw(lines), ImageDraw.Draw(mask)
    for a, b in EDGES:
        if near in (a, b):
            dl.line([(px[a], py[a]), (px[b], py[b])], fill=EDGE, width=2)
            dm.line([(px[a], py[a]), (px[b], py[b])], fill=int(255 * FRONT_EDGE_ALPHA), width=2)
    over.paste(lines, (0, 0), mask)
    return over


def card(trophy, ims):
    """The candidate's two views side by side, under its number, name and what it shows."""
    w = sum(i.width for i in ims) + 10 * (len(ims) - 1)
    h = max(i.height for i in ims)
    out = Image.new("RGB", (max(w, 520), h + 64), V.SHEET_BG)
    x = 0
    for im in ims:
        out.paste(im, (x, 64 + h - im.height))
        x += im.width + 10
    d = ImageDraw.Draw(out)
    d.text((8, 4), f"No. {trophy['number']}  {trophy['name']}", font=V.font(24), fill=V.TEXT)
    d.text((8, 36), trophy["blurb"], font=V.font(16, bold=False), fill=V.DIM)
    return out


def main():
    moose, rider, foot, ma, ra, fa = build()
    os.makedirs(OUT, exist_ok=True)
    cards = []
    rows = []
    for trophy in TROPHIES:
        models, poses = layers(trophy, moose, rider, foot, ma, ra, fa)
        turn, side, cube = fit(world_points(models, poses))
        ims = [picture(models, poses, cube, side, yaw, el) for yaw, el in trophy.get("views", VIEWS)]
        c = card(trophy, ims)
        name = f"trophy_{trophy['number']:02d}_{trophy['key']}.png"
        c.save(os.path.join(OUT, name))
        cards.append(c)
        rows.append((trophy, name, turn, side))
        print(f"  No. {trophy['number']:2d} {trophy['name']}: turned {turn:+.0f}, scaled to 1/{side:.1f}")
    per = 3
    parts = [(f"Nos. {k + 1}-{min(k + per, len(cards))}", V.grid(cards[k:k + per], per)) for k in range(0, len(cards), per)]
    V.page("The Huntsman's trophy candidates",
           "Each a 1x1x1 block holding a miniature of him (in the saddle, on foot, or his Alpha Moose) in one pose of "
           "their own animations, drawn in the outline of its block. Pick the ones you'd like (any number): right-click "
           "will turn a placed trophy through them, like the other bosses' trophies.", parts,
           os.path.join(OUT, "candidates.png"))
    with open(os.path.join(OUT, "index.html"), "w", encoding="utf-8") as f:
        f.write("<!doctype html><meta charset=utf-8><title>Huntsman trophies</title>"
                "<style>body{background:#12161c;color:#ecf2f8;font-family:Segoe UI,sans-serif;margin:24px}"
                "img{max-width:100%;display:block;margin:8px 0 28px}h2{color:#96d6ec;margin:0}</style>"
                "<h1>The Huntsman's trophy candidates</h1>")
        for trophy, name, turn, side in rows:
            f.write(f"<h2>No. {trophy['number']} {trophy['name']}</h2><div>{trophy['blurb']}</div>"
                    f"<img src='{name}' alt='{trophy['name']}'>")
    print(f"Wrote {len(rows)} candidates to {OUT}")


if __name__ == "__main__":
    main()
