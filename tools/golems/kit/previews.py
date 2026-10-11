"""
Preview pictures for the golem designs (boss-previews/):

  NN_<id>_<dormant|erupted|core>_<front|side|back>.png
        dormant  - its rock shell at the start of the fight, the volcano only smoking
        erupted  - halfway through the first stage: the volcano has blown and lava has poured over the shell
        core     - the second stage: the shell broken off, a lava body with a bigger, erupting volcano
  NN_<id>_sheet.png   everything about one design on one page: the three looks from four sides, night shots, and
                      filmstrips of the eruption, the shell breaking, the live eruption and the death (cooling into
                      an obsidian statue)
  NN_<id>_fight.png   the fight: the Cracking stage's look (plates knocked loose, bright cracks) and a filmstrip of
                      every move (swipe, throw, slam, charge, lob, summon)
  all_golems.png      every design side by side
  minions.png         the stone golem and the lava golem it summons

The previews are drawn by kit/render.py with GeckoLib's own geometry maths, so they match the game.
A dark figure the size of a player stands to the left of each golem for scale.
"""
import os

from PIL import Image, ImageDraw, ImageFont

from . import anims, render

SCALE = 7                    # pixels per texel in the single views
BG = (46, 40, 38)
SHEET_BG = (24, 21, 20)
TEXT = (245, 236, 226)
ACCENT = (255, 163, 40)      # lava orange
DIM = (170, 156, 146)
# Timings shared with the mod's renderer (written into GolemDesigns.java):
FLOW_REVEAL = (1.4, 3.3)     # the eruption: the lava starts pouring over the shell, and has covered it
GLOW_FADE = anims.DEATH_GLOW_FADE       # the death: after its scream (anims.DEATH_SCREAM) the lava's glow fades out...
STATUE_FADE = anims.DEATH_STATUE_FADE   # ...and the obsidian statue fades in (both slowly)
DORMANT_GLOW = 0.45          # how strongly the lava glows while dormant (it brightens to 1 as it erupts)
NIGHT_LIGHT = 0.18           # world light at night (moonlight) for the night shots
ERUPTING_AT = 0.55           # a moment of the live-eruption loop to show in still pictures of the core form

PHASES = ("dormant", "erupted", "core")


def font(size, bold=True):
    names = ("segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf") if bold else ("segoeui.ttf", "arial.ttf", "DejaVuSans.ttf")
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default(size=size)


def visibility(phase, burst=False):
    if phase == "core":
        return render.Visibility(shell=False, molten=True, vent=True)
    return render.Visibility(shell=True, molten=False, vent=False, burst=burst)


def still(phase, animations):
    """The pose and flow amount a still picture of a phase uses."""
    pose = None
    if phase == "core" and "volcano_erupting" in animations:
        pose = animations["volcano_erupting"].sample(ERUPTING_AT)
    return pose, (1.0 if phase == "erupted" else 0.0)


def window(m, yaw, elevation, animations, margin=0.35):
    """One view window big enough for all three looks, so they line up."""
    view = render.camera(yaw, elevation)
    boxes = []
    for phase in PHASES:
        pose, _ = still(phase, animations)
        mats = m.pose_matrices(pose, yaw_deg=0.0)
        boxes.append(render.view_bounds(m, mats, visibility(phase), view, margin))
    return (min(b[0] for b in boxes) - 1.1, min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


def shot(m, textures, animations, phase, yaw, elevation, scale, bounds, world_light=1.0, background=BG):
    pose, flow = still(phase, animations)
    glow = DORMANT_GLOW if phase == "dormant" else 1.0
    return render.render(m, textures, pose=pose, visible=visibility(phase), yaw=yaw, elevation=elevation, scale=scale,
                         bounds=bounds, flow_progress=flow, world_light=world_light, background=background,
                         glow_strength=glow)


def single_views(d, m, textures, animations, out_dir):
    stem = f"{d.NUMBER:02d}_{d.ID}"
    for side, yaw in (("front", 0.0), ("side", 90.0), ("back", 180.0)):
        bounds = window(m, yaw, 10.0, animations)
        for phase in PHASES:
            shot(m, textures, animations, phase, yaw, 10.0, SCALE, bounds).save(
                os.path.join(out_dir, f"{stem}_{phase}_{side}.png"))


def _label(im, text, size=20, color=TEXT):
    ImageDraw.Draw(im).text((10, 6), text, font=font(size), fill=color)
    return im


def _grid(images, per_row, gap=10, bg=SHEET_BG):
    cw = max(i.width for i in images)
    ch = max(i.height for i in images)
    rows = (len(images) + per_row - 1) // per_row
    out = Image.new("RGB", (per_row * cw + (per_row - 1) * gap, rows * ch + (rows - 1) * gap), bg)
    for k, im in enumerate(images):
        out.paste(im, ((k % per_row) * (cw + gap), (k // per_row) * (ch + gap)))
    return out


def filmstrip(m, textures, frames, yaw, elevation, scale, bounds):
    """frames: [(label, pose, visibility, flow, glow, statue)]; a glow under 1 with no statue is the dormant dimming."""
    out = []
    for label, pose, vis, flow, glow, statue in frames:
        cooling = statue > 0 or (glow < 1.0 and flow == 0.0 and vis.molten)
        im = render.render(m, textures, pose=pose, visible=vis, yaw=yaw, elevation=elevation, scale=scale,
                           bounds=bounds, flow_progress=flow, flow_dim=glow if cooling else 1.0, glow_strength=glow,
                           statue_strength=statue, background=BG, figure=False)
        out.append(_label(im, label, 16, DIM))
    return out


def design_sheet(d, m, textures, animations, out_dir):
    stem = f"{d.NUMBER:02d}_{d.ID}"
    sc = 3
    views = (("front", 0.0, 10.0), ("side", 90.0, 10.0), ("back", 180.0, 10.0), ("three-quarter", 35.0, 18.0))
    rows = []
    for phase in PHASES:
        ims = []
        for name, yaw, el in views:
            ims.append(_label(shot(m, textures, animations, phase, yaw, el, sc, window(m, yaw, el, animations)),
                              f"{phase} - {name}", 17))
        rows.append(_grid(ims, 4))
    nb = window(m, 35.0, 18.0, animations)
    night = [_label(shot(m, textures, animations, phase, 35.0, 18.0, sc, nb, world_light=NIGHT_LIGHT,
                         background=(14, 13, 18)), f"{phase} at night", 17) for phase in PHASES]
    wide = (-5.0, -7.0, 5.0, 0.5)
    strips = []
    if "erupt" in animations:
        er, vb = animations["erupt"], animations["volcano_burst"]
        tb = er.meta["blast"]
        frames = []
        for t in (0.0, tb - 0.3, tb + 0.1, tb + 0.6, (FLOW_REVEAL[0] + FLOW_REVEAL[1]) / 2.0, er.length):
            fp = max(0.0, min(1.0, (t - FLOW_REVEAL[0]) / (FLOW_REVEAL[1] - FLOW_REVEAL[0])))
            frames.append((f"{t:.1f} s", anims.combine(er.sample(t), vb.sample(t)), visibility("dormant", burst=True),
                           fp, DORMANT_GLOW + (1.0 - DORMANT_GLOW) * fp, 0.0))
        strips.append(("The volcano erupts: it leans in, the volcano blows, lava pours over the shell",
                       filmstrip(m, textures, frames, 30.0, 14.0, 2, wide)))
    brk = animations["break_shell"]
    tb = brk.meta["burst"]
    frames = []
    for t in (tb - 0.45, tb + 0.15, tb + 0.55, tb + 1.0, brk.length):
        vis = render.Visibility(shell=True, molten=t >= brk.meta["molten_from"], vent=False)
        frames.append((f"{t:.1f} s", brk.sample(t), vis, 1.0, 1.0, 0.0))
    strips.append(("Stage two: the shell breaks off", filmstrip(m, textures, frames, 30.0, 14.0, 2, wide)))
    if "volcano_erupting" in animations:
        ve = animations["volcano_erupting"]
        frames = [(f"{t:.1f} s", anims.combine(animations["idle"].sample(t), ve.sample(t)), visibility("core"), 0.0, 1.0,
                   0.0) for t in (0.0, 0.3, 0.6, 0.9, 1.2)]
        strips.append(("The lava core's volcano erupts all the time", filmstrip(m, textures, frames, 30.0, 14.0, 2, wide)))
    dth = animations["death"]
    frames = []
    sc = anims.DEATH_SCREAM
    for t in (0.5, 1.05, 2.35, sc + 0.6, GLOW_FADE[0] + 1.2, STATUE_FADE[0] + 1.0, STATUE_FADE[0] + 2.4, dth.length):
        g = 1.0 - anims.ramp(t, *GLOW_FADE)
        s_ = anims.ramp(t, *STATUE_FADE)
        frames.append((f"{t:.1f} s", dth.sample(t), render.Visibility(shell=False, molten=True, vent=False), 0.0, g, s_))
    strips.append(("Death: cooling into an obsidian statue", filmstrip(m, textures, frames, 35.0, 14.0, 2,
                                                                         (-4.0, -6.2, 4.0, 0.4))))
    parts = [(None, rows[0]), (None, rows[1]), (None, rows[2]), ("In the dark", _grid(night, 3))]
    parts += [(title, _grid(fr, len(fr))) for title, fr in strips]
    width = max(p.width for _t, p in parts) + 40
    header = 100
    height = header + sum(p.height + (40 if t else 14) for t, p in parts) + 20
    sheet = Image.new("RGB", (width, height), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    title = f"{d.LABEL}. {d.NAME}"
    dr.text((20, 14), title, font=font(40), fill=TEXT)
    tw = dr.textlength(title, font=font(40))
    dr.text((30 + tw, 30), f"({d.MOOD})", font=font(24), fill=ACCENT)
    dr.text((20, 64), d.BLURB, font=font(18, bold=False), fill=DIM)
    y = header
    for t, p in parts:
        if t:
            dr.text((20, y + 4), t, font=font(22), fill=ACCENT)
            y += 34
        sheet.paste(p, (20, y))
        y += p.height + 14
    sheet.save(os.path.join(out_dir, f"{stem}_sheet.png"))


def design_previews(d, m, textures, animations, out_dir):
    single_views(d, m, textures, animations, out_dir)
    design_sheet(d, m, textures, animations, out_dir)
    if "swipe" in animations:
        fight_sheet(d, m, textures, animations, out_dir)


def cracking_shot(d, m, textures, yaw, elevation, scale, bounds, amount=1.0, pose=None, world_light=1.0,
                  background=BG, figure=True):
    """The fight's Cracking stage: the erupted shell with plates knocked loose and bright cracks (at `amount`)."""
    from . import cracks
    loose = cracks.loose_pose(getattr(d, "LOOSE", ()), amount)
    return render.render(m, textures, pose=cracks.add_pose(pose, loose), visible=visibility("dormant"), yaw=yaw,
                         elevation=elevation, scale=scale, bounds=bounds, flow_progress=1.0, crack_strength=amount,
                         world_light=world_light, background=background, figure=figure)


# The moves' preview frames (seconds into each), and which form they're shown in.
MOVE_FRAMES = (
    ("swipe", "Its melee hit: a backhand swipe", (0.0, 0.3, 0.45, 0.55, 0.6, 0.7, 0.95, 1.3), "dormant"),
    ("throw", "Throwing a rock: torn out of the ground, raised high, hurled", (0.35, 0.6, 0.85, 1.1, 1.25, 1.4, 1.6, 2.0),
     "dormant"),
    ("slam", "The ground slam: fists raised high (the warning), then smashed down", (0.0, 0.5, 0.75, 1.1, 1.36, 1.5, 1.8,
                                                                                    2.3), "dormant"),
    ("charge_start", "The charge's wind-up: head down, a fist raking the ground", (0.0, 0.3, 0.45, 0.6, 0.75, 1.0),
     "erupted"),
    ("charge", "The charge: a gallop on all fours, head first (loops)", (0.0, 0.1, 0.2, 0.3, 0.4, 0.5), "core"),
    ("lob", "The volcano lob: it hunches, its back heaves, the craters spit lava", (0.0, 0.5, 0.9, 1.05, 1.2, 1.5, 2.0),
     "erupted"),
    ("summon", "Summoning: a roar, then three pounds (a minion rises at each)", (0.35, 0.7, 0.95, 1.2, 1.45, 1.8, 2.05,
                                                                               2.5), "erupted"),
)


def fight_sheet(d, m, textures, animations, out_dir):
    """NN_<id>_fight.png: the Cracking stage's look and a filmstrip of every fight move."""
    stem = f"{d.NUMBER:02d}_{d.ID}"
    sc = 3
    parts = []
    views = (("front", 0.0, 10.0), ("side", 90.0, 10.0), ("back", 180.0, 10.0), ("three-quarter", 35.0, 18.0))
    row = [_label(cracking_shot(d, m, textures, yaw, el, sc, window(m, yaw, el, animations)), f"cracking - {name}", 17)
           for name, yaw, el in views]
    parts.append(("The Cracking stage (60% to 35% of its health): plates knocked loose, lava glowing through",
                  _grid(row, 4)))
    stages = []
    for amount in (0.0, 0.35, 0.7, 1.0):
        b = window(m, 35.0, 18.0, animations)
        im = cracking_shot(d, m, textures, 35.0, 18.0, sc, b, amount=amount, world_light=NIGHT_LIGHT,
                           background=(14, 13, 18))
        stages.append(_label(im, f"at night, {int(amount * 100)}% cracked", 17))
    parts.append(("It cracks a little more with every hit (at night)", _grid(stages, 4)))
    wide = (-5.0, -7.6, 5.0, 0.5)
    for name, title, times, form in MOVE_FRAMES:
        a = animations[name]
        frames = []
        for t in times:
            pose = a.sample(t)
            props = ()
            if name == "throw" and a.meta["grab"] <= t < a.meta["release"]:
                props = ("prop_magma",) if form == "core" else ("prop_rock",)
            if name == "lob" and "volcano_lob" in animations:
                pose = anims.combine(pose, animations["volcano_lob"].sample(t))
            if form == "core":
                if "volcano_erupting" in animations:
                    pose = anims.combine(pose, animations["volcano_erupting"].sample(t))
                vis = render.Visibility(shell=False, molten=True, vent=True, props=props)
                frames.append((f"{t:.2f} s", pose, vis, 0.0, 1.0, 0.0))
            else:
                burst = name == "lob"
                vis = render.Visibility(shell=True, molten=False, vent=False, burst=burst, props=props,
                                        hide=[b.name for b in m.bones if "_rock_" in b.name] if burst else ())
                flow = 1.0 if form == "erupted" else 0.0
                glow = 1.0 if form == "erupted" else DORMANT_GLOW
                frames.append((f"{t:.2f} s", pose, vis, flow, glow, 0.0))
        parts.append((title, _grid(filmstrip(m, textures, frames, 30.0, 14.0, 2, wide), len(frames))))
    width = max(p.width for _t, p in parts) + 40
    header = 100
    height = header + sum(p.height + 40 for _t, p in parts) + 20
    sheet = Image.new("RGB", (width, height), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((20, 14), f"{d.LABEL}. {d.NAME}: the fight", font=font(40), fill=TEXT)
    dr.text((20, 64), "The Cracking stage and its moves (the shell forms; the gallop as lava). The rock it throws "
                      "and the lava blobs it lobs are separate things in the game.", font=font(18, bold=False), fill=DIM)
    y = header
    for t, p in parts:
        dr.text((20, y + 4), t, font=font(22), fill=ACCENT)
        y += 34
        sheet.paste(p, (20, y))
        y += p.height + 6
    sheet.save(os.path.join(out_dir, f"{stem}_fight.png"))


def minion_sheet(built, out_dir):
    """minions.png: the stone golem and the lava golem from four sides, at night, and their animations."""
    parts = []
    for d, m, animations, textures in built:
        ims = []
        for name, yaw, el in (("front", 0.0, 10.0), ("side", 90.0, 8.0), ("back", 180.0, 10.0), ("three-quarter", 35.0, 18.0)):
            ims.append(_label(render.render(m, textures, visible=render.Visibility(shell=True), yaw=yaw, elevation=el,
                                            scale=9, bounds=(-1.9, -1.6, 1.2, 0.3)), name, 15, DIM))
        ims.append(_label(render.render(m, textures, visible=render.Visibility(shell=True), yaw=35.0, elevation=18.0,
                                        scale=9, bounds=(-1.9, -1.6, 1.2, 0.3), world_light=NIGHT_LIGHT,
                                        background=(14, 13, 18)), "at night", 15, DIM))
        parts.append((f"{d.NAME}", _grid(ims, 5)))
        frames = []
        for name, ts in (("walk", (0.0, 0.15, 0.3)), ("attack", (0.1, 0.2, 0.35)), ("spawn", (0.1, 0.35, 0.6, 0.9))):
            for t in ts:
                frames.append((f"{name} {t:.2f} s", animations[name].sample(t), render.Visibility(shell=True), 0.0, 1.0,
                               0.0))
        parts.append((f"{d.NAME}: walking, punching, climbing out of the ground",
                      _grid(filmstrip(m, textures, frames, 35.0, 14.0, 6, (-1.2, -1.5, 1.2, 1.1)), len(frames))))
    width = max(p.width for _t, p in parts) + 40
    header = 90
    height = header + sum(p.height + 40 for _t, p in parts) + 20
    sheet = Image.new("RGB", (width, height), SHEET_BG)
    dr = ImageDraw.Draw(sheet)
    dr.text((20, 14), "The Cinder Colossus's minions", font=font(36), fill=TEXT)
    dr.text((20, 58), "About a block tall (the dark figure is a player). Stone golems rise in the Cracking stage, lava "
                      "golems (which blow up) in the Lava Core stage.", font=font(17, bold=False), fill=DIM)
    y = header
    for t, p in parts:
        dr.text((20, y + 4), t, font=font(22), fill=ACCENT)
        y += 34
        sheet.paste(p, (20, y))
        y += p.height + 6
    sheet.save(os.path.join(out_dir, "minions.png"))


def overview(built, out_dir):
    """
    all_golems.png: each design dormant, erupted, cracking (the fight's middle stage), as lava and as a statue
    (three-quarter view), side by side.
    """
    cells = []
    for d, m, animations, textures, *_rest in built:
        b = window(m, 35.0, 18.0, animations)
        ims = [_label(shot(m, textures, animations, phase, 35.0, 18.0, 3, b), label, 16, DIM)
               for phase, label in (("dormant", "dormant"), ("erupted", "erupted"))]
        if getattr(d, "LOOSE", None):
            ims.append(_label(cracking_shot(d, m, textures, 35.0, 18.0, 3, b), "cracking (the fight)", 16, DIM))
        ims.append(_label(shot(m, textures, animations, "core", 35.0, 18.0, 3, b), "lava core", 16, DIM))
        dth = animations["death"]
        st = render.render(m, textures, pose=dth.sample(dth.length), visible=render.Visibility(False, True, vent=False),
                           yaw=35.0, elevation=18.0, scale=3, bounds=b, glow_strength=0.0, statue_strength=1.0,
                           background=BG)
        ims.append(_label(st, "statue", 16, DIM))
        row = _grid(ims, len(ims), 6)
        cell = Image.new("RGB", (row.width, row.height + 64), SHEET_BG)
        dr = ImageDraw.Draw(cell)
        title = f"{d.LABEL}. {d.NAME}"
        dr.text((8, 4), title, font=font(28), fill=TEXT)
        tw = dr.textlength(title, font=font(28))
        dr.text((18 + tw, 14), f"({d.MOOD})", font=font(18), fill=ACCENT)
        dr.text((8, 38), d.BLURB, font=font(15, bold=False), fill=DIM)
        cell.paste(row, (0, 64))
        cells.append(cell)
    grid = _grid(cells, 1, 18)
    out = Image.new("RGB", (grid.width + 40, grid.height + 90), SHEET_BG)
    dr = ImageDraw.Draw(out)
    dr.text((20, 16), "Cinder Colossus: the pick", font=font(36), fill=TEXT)
    dr.text((20, 58), "dormant / erupted / cracking (the fight's middle stage) / lava core / cooled obsidian statue "
                      "(the dark figure is a player, for scale)", font=font(17, bold=False), fill=DIM)
    out.paste(grid, (20, 90))
    out.save(os.path.join(out_dir, "all_golems.png"))
