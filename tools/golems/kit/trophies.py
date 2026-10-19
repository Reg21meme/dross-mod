"""
The Cinder Colossus trophies: five 1x1x1 decorative blocks, each a miniature of the boss (its real model and
textures, scaled down to fit one block, no base) holding one pose in one look. The boss drops one when it dies.

    1  Volcano's Wrath  the volcano lob as the lava leaves the craters             erupted (lava over the shell)
    2  Molten Charge    mid-gallop on all fours, head first                        lava core
    3  The Roar         reared up roaring, both fists raised high (the slam's warning)  erupted
    4  Earthshaker      the ground slam as its fists hit                           cracking (plates loose, bright cracks)
    5  Dormant Shell    crouched in its shell, head bowed                          dormant, dim

It writes (all outside the cinder_colossus folders, so build.remove_stale_files never touches them):
  assets/dross/geo/block/colossus_trophy.geo.json                     the boss's model (its own copies, so the trophies'
  assets/dross/geo/item/colossus_trophy.geo.json                      bones never mix with the boss's in GeckoLib, nor
                                                                      the placed trophies' with the items')
  assets/dross/animations/block/colossus_trophy.animation.json        animation.colossus_trophy.design_<n>: each pose,
                                                                      held (hidden bones are scaled to 0; the root is
                                                                      moved so the figure stands in the middle of the
                                                                      block, its lowest point on the floor)
  assets/dross/textures/block/colossus_trophy/design_<n>_flow.png      the lava streams, hot (the bright pulses)
                                                .../design_<n>_flow_base.png  the same streams, cooler (between pulses)
  boss-previews/cinder_wastes/cinder_colossus/trophies.png                                          the five designs from four sides and at night
  src/main/java/com/reg21meme/dross/boss/trophy/TrophyModels.java     scale, outline, drip spots, craters (generated)

The streams are painted over the figure as it stands in its own pose (lava runs downhill from the craters), and each
texel's alpha says how far down the stream it is (1 - 0.25 r, r = 0 at the crater to 1 farthest), like the boss's
flow texture. The mod draws them with Minecraft's dissolve shader (TrophyLayer): the cool texture everywhere, then
bright bands that slide down the streams and loop, so the lava seems to keep flowing. The crater pools are fully
opaque in both, so they always glow.
"""
import json
import os

import numpy as np
from PIL import Image, ImageDraw

from . import anims, cracks, moves, noise, render
from . import materials as M
from .paint import TexelMap, _pool_shades, rest_world

PREFIX = "animation.colossus_trophy."
FIT_SIDE = 0.92     # the figure's width and depth fill at most this much of the block...
FIT_HEIGHT = 0.96   # ...and its height this much
REACH = 64.0        # units of travel (drop + 0.6 x sideways) the streams run from the craters
DRIPS = 6           # drip spots per design (the mod drips lava from one now and then)
HEAD_BONES = ("head", "jaw", "jaw_inner")
PREVIEW_BAND = (0.35, 0.53)   # the bright band shown in the preview pictures (r from, to)

# Each look: which bones show, how strongly the lava glows (the mod's glow layer, full-bright), the Cracking stage's
# cracks, and the streams: how much of the surface they cover, and how the cool (between-pulse) lava looks.
STYLES = {
    "dormant": dict(java="DORMANT", glow=0.4, cracks=0.0, streams=0.0, reach=0.75, base="crust"),
    "erupted": dict(java="ERUPTED", glow=0.75, cracks=0.0, streams=0.55, reach=1.0, base="warm"),
    "cracking": dict(java="CRACKING", glow=0.75, cracks=0.8, streams=0.45, reach=1.0, base="warm"),
    "core": dict(java="CORE", glow=0.85, cracks=0.0, streams=0.3, reach=1.0, base="core"),
}

# The designs: number, file key, name (the mod's names are lang keys; these label the previews), look, and the move
# and moment (seconds) the pose is taken from. "spurt": the lob's lava spurts out of the craters (volcano_lob).
TROPHIES = (
    dict(number=1, name="Volcano's Wrath", style="erupted", move="lob", at=1.15, spurt=True,
         blurb="The volcano lob: its back heaves and both craters spit lava"),
    dict(number=2, name="Molten Charge", style="core", move="charge", at=0.0,
         blurb="Mid-gallop on all fours, head first, as lava"),
    dict(number=3, name="The Roar", style="erupted", move="slam", at=1.0,
         blurb="Reared up and roaring, both fists raised high"),
    dict(number=4, name="Earthshaker", style="cracking", move="slam", at=1.52, crack=0.8,
         blurb="The ground slam as its fists hit, its shell cracking"),
    dict(number=5, name="Dormant Shell", style="dormant", move="death", at="end",
         blurb="Crouched in its shell, head bowed, the lava only seeping"),
)
MOVES = ("lob", "volcano_lob", "charge", "slam", "death")


def needed_animations(d, m, b, animations=None):
    """The animations the poses come from (taken from a full build, or built here: about half a minute)."""
    if animations and all(k in animations for k in MOVES):
        return animations
    from designs import common as C
    rig = anims.Rig(m, C.limbs(m, b), C.rest_targets(b), getattr(d, "ANIM", {}))
    mr = moves.move_rig(rig)
    return {"lob": moves.lob(mr), "volcano_lob": moves.volcano_lob(mr), "charge": moves.charge(mr),
            "slam": moves.slam(mr), "death": anims.death(rig)}


def visibility(m, trophy):
    """Which bones the design shows (the same rules as the boss: CinderColossus.showsBone)."""
    rocks = [bn.name for bn in m.bones if "_rock_" in bn.name or "_bomb_" in bn.name]
    if trophy["style"] == "core":
        return render.Visibility(shell=False, molten=True, vent=False, burst=False, hide=rocks)
    return render.Visibility(shell=True, molten=False, vent=False, burst=bool(trophy.get("spurt")), hide=rocks)


def raw_pose(d, trophy, A):
    """The pose of the move at the design's moment (with the lob's spurts and the cracking's loose plates)."""
    a = A[trophy["move"]]
    t = a.length if trophy["at"] == "end" else trophy["at"]
    pose = dict(a.sample(t))
    if trophy.get("spurt"):
        pose = anims.combine(pose, A["volcano_lob"].sample(t))
    if trophy.get("crack"):
        pose = cracks.add_pose(pose, cracks.loose_pose(getattr(d, "LOOSE", ()), trophy["crack"]))
    return pose


def _set(pose, bone, rot=None, pos=None, scl=None):
    r, p, s = pose.get(bone, ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
    pose[bone] = (tuple(rot) if rot is not None else tuple(r), tuple(pos) if pos is not None else tuple(p),
                  tuple(scl) if scl is not None else tuple(s))


def hide(m, pose, vis):
    """Scales the topmost hidden bones to 0 (their children go with them), so the pose itself hides them."""
    for bn in m.bones:
        if not vis(bn) and (bn.parent is None or vis(bn.parent)):
            _set(pose, bn.name, scl=(0.0, 0.0, 0.0))


def bounds(m, mats, vis):
    """Bedrock bounding box (min, max) of every box the design shows, posed."""
    from .geo import bedrock, gecko
    pts = []
    for bn in m.bones:
        if not bn.cubes or not vis(bn):
            continue
        mb = mats[bn.name]
        for c in bn.cubes:
            cm = mb @ c.cube_matrix()
            ox, oy, oz = c.origin
            w, h, dd = c.size
            for dx in (0, w):
                for dy in (0, h):
                    for dz in (0, dd):
                        q = cm @ np.append(gecko((ox + dx, oy + dy, oz + dz)), 1.0)
                        pts.append(bedrock(q[:3]))
    pts = np.array(pts)
    return pts.min(axis=0), pts.max(axis=0)


def to_block(p, scale):
    """A Bedrock model point of the placed figure as a point in its block (0-1), for a trophy facing north."""
    return (0.5 - p[0] / 16.0 * scale, p[1] / 16.0 * scale, 0.5 + p[2] / 16.0 * scale)


def sources(m, mats, info, style):
    """Where the lava comes from: the crater tops (the molten volcanoes' tops in the lava core look)."""
    out = []
    for v in info.get("volcanoes", []):
        top = np.array(m.point(mats, v.crater.name, v.crater_top), dtype=float)
        if style == "core":
            side = v.holder.name.split("_")[-1]
            best = None
            for bn in m.bones:
                if bn.name.startswith(f"molten_volcano_{side}_") and bn.cubes:
                    for c in bn.cubes:
                        cx, cy, cz = c.center()
                        q = np.array(m.point(mats, bn.name, (cx, c.origin[1] + c.size[1], cz)), dtype=float)
                        if best is None or q[1] > best[1]:
                            best = q
            if best is not None:
                top = best
        out.append(top)
    return out


def paint_streams(m, tm, mats, vis, srcs, style, seed):
    """
    The design's two flow textures (hot, cool) and its stream texels (for the drip spots): lava running downhill from
    the craters over the figure in its own pose. Alpha: 1 - 0.25 r (r = how far down the stream), the pools 255.
    """
    st = STYLES[style]
    H, W = tm.H, tm.W
    Pw, Nw = rest_world(m, tm, mats)
    stream_ids, pool_ids, head_ids = [], [], []
    for c in tm.cubes:
        if not vis(c.bone) or any(not vis(a) for a in c.bone.ancestors()):
            continue
        kinds = [a.kind for a in c.bone.ancestors()]
        names = [a.name for a in c.bone.ancestors()]
        if c.tag == "crater" or ("burst" in kinds and c.mat == "fountain"):
            pool_ids.append(c.index)
            continue
        if any(k in kinds for k in ("burst", "vent", "prop")):
            continue
        if style == "core":
            if "shell" in kinds or c.mat not in ("lava", "molten"):
                continue
        elif "shell" not in kinds or "molten" in kinds:
            continue
        stream_ids.append(c.index)
        if any(n in HEAD_BONES for n in names):
            head_ids.append(c.index)
    on_stream = np.isin(tm.cube, stream_ids)
    on_pool = np.isin(tm.cube, pool_ids)
    sel = np.nonzero(on_stream | on_pool)
    P, N = Pw[sel], Nw[sel]
    best = np.full(len(P), np.inf)
    for s in srcs:
        dh = np.hypot(P[:, 0] - s[0], P[:, 2] - s[2])
        drop = s[1] - P[:, 1]
        best = np.minimum(best, np.maximum(drop, 0.0) + 0.6 * dh + np.maximum(-drop, 0.0) * 2.5)
    r = np.clip(best / (REACH * st["reach"]), 0.0, 1.0)
    pool = on_pool[sel]
    head = np.isin(tm.cube[sel], head_ids)
    q = noise.warp(P * np.array([0.42, 0.06, 0.42]), 0.8, 3.0, seed)
    s1 = noise.fbm(q, 2.2, 2, seed + 1)
    thr = 0.56 + 0.14 * r - 0.2 * st["streams"]
    lava = (s1 > thr) & (r < 1.0)
    if style != "dormant":
        lava |= (Nw[sel][:, 1] > 0.6) & (r < 0.2)        # pools on surfaces facing up, right by the crater
        lava |= r < 0.06
    lava &= ~head | (s1 > thr + 0.12)                    # keep the face mostly clear
    fine = noise.fbm(noise.warp(P * np.array([0.9, 0.22, 0.9]), 0.6, 2.0, seed + 7), 1.4, 2, seed + 9)
    hot_idx = 4.4 + (s1 - 0.5) * 2.4 + (fine - 0.5) * 1.6 - r * 0.9
    if st["base"] == "crust":
        cool = M.pick(M.CRUST, 1.6 + (s1 - 0.5) * 2.0 + (fine - 0.5) * 1.6 + (1.0 - r) * 1.2)
        hot_idx = hot_idx - 1.2                            # dormant: a dull orange seep, not white-hot
    elif st["base"] == "core":
        cool = M.pick(M.LAVA, hot_idx - 1.4)
    else:
        cool = M.pick(M.LAVA, hot_idx - 2.0) * 0.85
    hot = M.pick(M.LAVA, hot_idx)
    pool_col = M.pick(M.LAVA, _pool_shades(m, tm, sel, pool, seed))
    hot = np.where(pool[:, None], pool_col, hot)
    cool = np.where(pool[:, None], pool_col, cool)
    alpha = np.where(pool, 255.0, np.clip(np.round(255.0 * (1.0 - 0.25 * r)), 191, 254))
    alpha = np.where(lava | pool, alpha, 0.0)
    out_hot = np.zeros((H, W, 4))
    out_cool = np.zeros((H, W, 4))
    out_hot[sel] = np.c_[hot, alpha]
    out_cool[sel] = np.c_[cool, alpha]
    stream = dict(P=P[lava & ~pool], N=N[lava & ~pool], r=r[lava & ~pool])
    return (np.clip(out_hot, 0, 255).astype(np.uint8), np.clip(out_cool, 0, 255).astype(np.uint8), stream,
            dict(sel=sel, r=r, shown=lava | pool))


def drip_spots(stream, lowest_room=3.0, count=DRIPS):
    """Where lava drips from: stream ends on faces looking down (or out), spread apart, nudged off the surface."""
    P, N, r = stream["P"], stream["N"], stream["r"]
    if len(P) == 0:
        return []
    ok = P[:, 1] > lowest_room
    for limit in (-0.3, 0.2, 1.1):
        cand = ok & (N[:, 1] < limit) & (r > 0.25)
        if cand.sum() >= count:
            break
    idx = np.nonzero(cand)[0]
    if len(idx) == 0:
        return []
    # Farthest-point sampling, starting from the stream's furthest-down texel.
    pts = P[idx]
    chosen = [int(np.argmax(r[idx] - P[idx, 1] * 0.01))]
    dist = np.linalg.norm(pts - pts[chosen[0]], axis=1)
    while len(chosen) < min(count, len(idx)):
        k = int(np.argmax(dist))
        if dist[k] < 2.0:
            break
        chosen.append(k)
        dist = np.minimum(dist, np.linalg.norm(pts - pts[k], axis=1))
    out = []
    for k in chosen:
        i = idx[k]
        out.append(P[i] + N[i] * 0.6 + np.array([0.0, -0.4, 0.0]))
    return out


def build_one(d, m, info, trophy, A, tm):
    """One design: its held pose, scale, outline, flow textures, drip spots and craters."""
    style = trophy["style"]
    vis = visibility(m, trophy)
    pose = raw_pose(d, trophy, A)
    hide(m, pose, vis)
    lo, hi = bounds(m, m.pose_matrices(pose), vis)
    r0 = pose.get("root", ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
    shift = (-(lo[0] + hi[0]) / 2.0, -lo[1], -(lo[2] + hi[2]) / 2.0)
    _set(pose, "root", pos=tuple(r0[1][i] + shift[i] for i in range(3)))
    mats = m.pose_matrices(pose)
    lo, hi = bounds(m, mats, vis)
    size = hi - lo
    scale = min(FIT_SIDE * 16.0 / size[0], FIT_SIDE * 16.0 / size[2], FIT_HEIGHT * 16.0 / size[1])
    srcs = sources(m, mats, info, style)
    hot, cool, stream, flow_info = paint_streams(m, tm, mats, vis, srcs, style, 900 + 17 * trophy["number"])
    drips = [to_block(p, scale) for p in drip_spots(stream)]
    crater_spots = [to_block(np.asarray(s), scale) for s in srcs]
    a = anims.Animation(f"design_{trophy['number']}", 0.05, True)
    p = anims.Pose()
    for bone, (rot, pos, scl) in pose.items():
        p[bone] = [list(rot), list(pos), list(scl)]
    a.add(0.0, p)
    a.add(0.05, p)
    a.bake(tol_rot=0.05, tol_pos=0.01, tol_scale=0.002)
    lo_b = to_block((hi[0], lo[1], lo[2]), scale)
    hi_b = to_block((lo[0], hi[1], hi[2]), scale)
    return dict(trophy=trophy, style=style, vis=vis, pose=pose, scale=scale, size=size * scale / 16.0,
                shape=(lo_b, hi_b), drips=drips, craters=crater_spots, hot=hot, cool=cool, flow_info=flow_info,
                animation=a)


def build(d, m, b, info, textures, out, animations=None):
    """
    Builds every trophy design and writes its files. out: dict of output folders (anim, geo, tex, preview, java).
    """
    A = needed_animations(d, m, b, animations)
    tm = TexelMap(m)
    built = []
    for trophy in TROPHIES:
        t = build_one(d, m, info, trophy, A, tm)
        s = t["size"]
        print(f"  trophy {trophy['number']}. {trophy['name']}: {s[0]:.2f} x {s[1]:.2f} x {s[2]:.2f} blocks "
              f"(scale {t['scale']:.3f}), {len(t['drips'])} drip spots")
        built.append(t)
    for key in ("anim", "geo", "tex", "preview"):
        os.makedirs(out[key], exist_ok=True)
    # Two copies of the model, one for the placed block and one for the item: GeckoLib shares a model's bones between
    # everything drawn with it, and draws an item twice in a frame without posing it again, so the block's and the
    # item's figures each get their own.
    for kind in ("block", "item"):
        folder = os.path.join(out["geo"], kind) if out.get("geo_split") else out["geo"]
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, f"colossus_trophy{'' if out.get('geo_split') else '_' + kind}.geo.json"), "w",
                  encoding="utf-8") as f:
            geo_json = m.to_json(visible_bounds=(1.5, 1.5, 0.5))
            geo_json["minecraft:geometry"][0]["description"]["identifier"] = "geometry.colossus_trophy_" + kind
            json.dump(geo_json, f, separators=(",", ":"))
    with open(os.path.join(out["anim"], "colossus_trophy.animation.json"), "w", encoding="utf-8") as f:
        json.dump(anims.to_json({t["animation"].name: t["animation"] for t in built}, prefix=PREFIX), f,
                  separators=(",", ":"))
    for t in built:
        n = t["trophy"]["number"]
        Image.fromarray(t["hot"], "RGBA").save(os.path.join(out["tex"], f"design_{n}_flow.png"))
        Image.fromarray(t["cool"], "RGBA").save(os.path.join(out["tex"], f"design_{n}_flow_base.png"))
    sheet(d, m, textures, built, out["preview"])
    if out.get("java"):
        write_java(out["java"], d, built)
    return built


# ---------------------------------------------------------------- previews


def preview_flow(t):
    """A still of the streams for the previews: the cool lava with one bright band part-way down."""
    hot, cool = t["hot"].astype(float), t["cool"].astype(float)
    a = cool[..., 3]
    r = (1.0 - a / 255.0) / 0.25
    band = (a > 0) & (a < 255) & (r >= PREVIEW_BAND[0]) & (r <= PREVIEW_BAND[1])
    out = cool.copy()
    out[band] = hot[band]
    out[..., 3] = np.where(a > 0, 255.0, 0.0)
    return np.clip(out, 0, 255).astype(np.uint8)


def shot(m, textures, t, yaw, elevation, scale, bounds_, light=1.0, background=None):
    st = STYLES[t["style"]]
    tex = dict(base=textures["base"], glow=textures["glow"], statue=textures["statue"], flow=preview_flow(t),
               cracks=textures.get("cracks"))
    from . import previews
    return render.render(m, tex, pose=t["pose"], visible=t["vis"], yaw=yaw, elevation=elevation, scale=scale,
                         bounds=bounds_, glow_strength=st["glow"], crack_strength=st["cracks"], flow_progress=1.0,
                         world_light=light, background=background or previews.BG, figure=False)


def sheet(d, m, textures, built, out_dir):
    """boss-previews/cinder_wastes/cinder_colossus/trophies.png: each design from four sides and at night."""
    from . import previews
    views = (("front", 25.0, 12.0, 1.0), ("side", 90.0, 8.0, 1.0), ("back", 205.0, 14.0, 1.0),
             ("at night", 35.0, 16.0, previews.NIGHT_LIGHT))
    rows = []
    for t in built:
        ims = []
        for label, yaw, el, light in views:
            view = render.camera(yaw, el)
            win = render.view_bounds(m, m.pose_matrices(t["pose"]), t["vis"], view, 0.5)
            cx, cy = (win[0] + win[2]) / 2.0, (win[1] + win[3]) / 2.0
            half = max(win[2] - win[0], win[3] - win[1]) / 2.0
            win = (cx - half, cy - half, cx + half, cy + half)
            sc = 230.0 / ((win[2] - win[0]) * 16.0)
            bg = (14, 13, 18) if light < 1.0 else previews.BG
            ims.append(previews._label(shot(m, textures, t, yaw, el, sc, win, light, bg), label, 15, previews.DIM))
        row = previews._grid(ims, len(ims), 6)
        tr = t["trophy"]
        cell = Image.new("RGB", (row.width, row.height + 62), previews.SHEET_BG)
        dr = ImageDraw.Draw(cell)
        title = f"{tr['number']}. {tr['name']}"
        dr.text((6, 2), title, font=previews.font(26), fill=previews.TEXT)
        tw = dr.textlength(title, font=previews.font(26))
        s = t["size"]
        dr.text((16 + tw, 10), f"({t['style'].replace('core', 'lava core')})", font=previews.font(17),
                fill=previews.ACCENT)
        dr.text((6, 36), f"{tr['blurb']}. {s[0]:.2f} x {s[1]:.2f} x {s[2]:.2f} blocks "
                         f"({t['scale'] * 100:.0f}% of the boss's size).", font=previews.font(15, bold=False),
                fill=previews.DIM)
        cell.paste(row, (0, 62))
        rows.append(cell)
    grid = previews._grid(rows, 1, 16)
    out = Image.new("RGB", (grid.width + 40, grid.height + 124), previews.SHEET_BG)
    dr = ImageDraw.Draw(out)
    dr.text((20, 14), "Cinder Colossus trophies (candidates)", font=previews.font(34), fill=previews.TEXT)
    dr.text((20, 54), "Each is one block: the boss's own model, scaled down, no base.", font=previews.font(16, bold=False),
            fill=previews.DIM)
    dr.text((20, 74), "In the game the lava streams keep flowing (bright bands slide down them; one is shown here) and "
                      "lava drips off now and then.", font=previews.font(16, bold=False), fill=previews.DIM)
    out.paste(grid, (20, 104))
    out.save(os.path.join(out_dir, "trophies.png"))


# ---------------------------------------------------------------- Java


JAVA = '''package com.reg21meme.dross.boss.trophy;

import java.util.List;

/**
 * The Cinder Colossus trophies' shapes, made from the boss's model: how much each design is scaled down, the box it
 * fills in its block, where lava drips off it and where its craters are.
 * <p>
 * GENERATED by {{@code tools/golems/build.py}} ({{@code kit/trophies.py}}): don't edit by hand, change the Python and
 * run the build again. Each design's pose is {{@code animation.colossus_trophy.design_<n>}} in
 * {{@code animations/block/colossus_trophy.animation.json}}, drawn on {{@code geo/block/colossus_trophy.geo.json}} (the
 * placed trophy) or {{@code geo/item/colossus_trophy.geo.json}} (the item), copies of the boss's model, with the boss's
 * textures, and its lava streams are
 * {{@code textures/block/colossus_trophy/design_<n>_flow.png}} (hot) and {{@code design_<n>_flow_base.png}} (cool).
 * <p>
 * Points are in the block (0 to 1) for a trophy facing north; the mod turns them for the other directions.
 */
public final class TrophyModels
{{
    /** The boss design the trophies are made from. */
    public static final String DESIGN_ID = "{design_id}";
    /** Each design's held pose: this plus its number. */
    public static final String ANIMATION_PREFIX = "{prefix}design_";

    /**
     * One trophy design.
     *
     * @param number  its number (1 to {count})
     * @param style   its look (which parts show, how its lava glows)
     * @param scale   how much the boss's model is scaled down
     * @param glow    how strongly its lava glows (the glow layer's strength, 0 to 1)
     * @param cracks  how strongly the Cracking stage's bright cracks show (0 to 1)
     * @param min     the box it fills in its block: lowest corner...
     * @param max     ...and highest corner
     * @param drips   where lava drips off it (lowest ends of its lava streams)
     * @param craters its volcanoes' craters
     */
    public record Model(int number, TrophyStyle style, float scale, float glow, float cracks, Spot min, Spot max,
                        List<Spot> drips, List<Spot> craters)
    {{
    }}

    /** A point in the block (0 to 1), for a trophy facing north. */
    public record Spot(float x, float y, float z)
    {{
    }}

    /** Every design, in order. */
    public static final List<Model> ALL = List.of(
{models});

    private TrophyModels()
    {{
    }}
}}
'''


def _f(v):
    return f"{round(float(v), 4) + 0.0:.4f}F"


def _spot(p):
    return f"new Spot({_f(p[0])}, {_f(p[1])}, {_f(p[2])})"


def write_java(path, d, built):
    rows = []
    for t in built:
        st = STYLES[t["style"]]
        lo, hi = t["shape"]
        drips = ", ".join(_spot(p) for p in t["drips"])
        craters = ", ".join(_spot(p) for p in t["craters"])
        rows.append(f"            // {t['trophy']['number']}. {t['trophy']['name']}\n"
                    f"            new Model({t['trophy']['number']}, TrophyStyle.{st['java']}, {_f(t['scale'])}, "
                    f"{_f(st['glow'])}, {_f(st['cracks'])},\n"
                    f"                    {_spot(lo)}, {_spot(hi)},\n"
                    f"                    List.of({drips}),\n"
                    f"                    List.of({craters}))")
    text = JAVA.format(design_id=d.ID, prefix=PREFIX, count=len(built), models=",\n".join(rows))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
