"""
The Emberdactyl's lava looks side by side (the user, October 31, 2026: "I am considering less lava spots on the adult
version on the body; the wings are fine, but on the body and horns less lava; give me a showcase with those
versions"): E1A (the one in the game) and E1B-E1D (firedesigns/e01_emberdactyl.py, firekit/emberdactyl.py BODY_LAVA),
drawn from the game's own files (tools/fire/mobs.py makes them: geo/ and textures/entity/firemob/emberdactyl<a-d>),
each view a row with the four looks across it, so they're easy to compare: the whole animal by day and at night, its
head, horns and half moon close up, and its body, neck, legs and tail with its wings left off, by day and at night.
Under each look, the share of its body's and its wings' texels that glow.

  python tools/fire/lava_looks.py     writes boss-previews/cinder_wastes/emberdactyl/lava_looks/lava_looks.png

Run tools/fire/mobs.py emberdactyl first (after changing a look). Needs numpy and Pillow.
"""
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
from PIL import Image, ImageDraw  # noqa: E402

from kit import render  # noqa: E402
from kit.previews import font  # noqa: E402
from rimekit import view as V  # noqa: E402

from firedesigns import e01_emberdactyl as DESIGN  # noqa: E402
from shapes import is_middle  # noqa: E402
from trophies import FIRE_GEO, load_geo, load_textures, points_of  # noqa: E402

OUT = os.path.join(ROOT, "boss-previews", "cinder_wastes", "emberdactyl", "lava_looks")
ACCENT = (255, 160, 70)
DAY_BG = (40, 32, 30)
CELL = 440                      # pixels: each picture's widest side
WINGS = ("arm_l", "arm_r")      # hidden for the body's close-ups (with everything on them: the whole wing)
HEAD = ("head", "jaw", "bill", "horn_l", "horn_r", "crest", "lid_l", "lid_r")


def window_of(pts, yaw, el, margin):
    """The view window (blocks) round these points (Bedrock units), as rimekit.view.window works it out."""
    view = render.camera(yaw, el)
    w = np.stack([pts[:, 0] / 16.0, pts[:, 1] / 16.0, -pts[:, 2] / 16.0], axis=1)
    v = w @ view.T
    return (v[:, 0].min() - margin, v[:, 1].min() - margin, v[:, 0].max() + margin, v[:, 1].max() + margin)


def picture(m, tex, yaw, el, bounds, night=False, hidden=()):
    scale = CELL / (16.0 * max(bounds[2] - bounds[0], bounds[3] - bounds[1]))
    return V.draw(m, tex, yaw=yaw, elevation=el, scale=scale, bounds=bounds, grid=False, figure=False,
                  background=V.NIGHT_BG if night else DAY_BG, world_light=V.NIGHT_LIGHT if night else 1.0,
                  hidden=hidden)


def glowing_share(m, tex, middle):
    """The share of its body's (`middle`) or its wings' texels that glow."""
    glow = tex["glow"][:, :, 3] > 25
    lit = total = 0
    for b in m.bones:
        if is_middle(b.name) != middle:
            continue
        for c in b.cubes:
            for u, v, w, h in c.face_uv.values():
                u0, v0 = int(min(u, u + w)), int(min(v, v + h))
                u1, v1 = int(max(u, u + w)), int(max(v, v + h))
                if u1 > u0 and v1 > v0:
                    region = glow[v0:v1, u0:u1]
                    lit += int(region.sum())
                    total += region.size
    return lit / max(total, 1)


def main():
    looks = []
    for v in DESIGN.VARIANTS:
        model_id = f"{DESIGN.ID}{v['letter'].lower()}"
        m = load_geo(os.path.join(FIRE_GEO, f"{model_id}.geo.json"), f"lava_{model_id}")
        looks.append((f"E1{v['letter']}", v["mood"], m, load_textures(model_id)))
    m0 = looks[0][2]
    # The views: (title, yaw, elevation, its window, night, hidden).
    whole = V.window(m0, 35.0, 14.0, figure=False, ground=False)
    head_pts = points_of(m0, None, HEAD)
    head = window_of(head_pts, 62.0, 12.0, 0.6)
    body = V.window(m0, 90.0, 6.0, figure=False, ground=False, hidden=WINGS)
    back = V.window(m0, -150.0, 30.0, figure=False, ground=False, hidden=WINGS)
    views = [("The whole animal, three-quarter, by day", 35.0, 14.0, whole, False, ()),
             ("At night: what glows", 35.0, 14.0, whole, True, ()),
             ("Its head, horns and half moon, by day", 62.0, 12.0, head, False, ()),
             ("Its head at night", 62.0, 12.0, head, True, ()),
             ("Its body, neck, legs and tail (wings left off), from its left, by day", 90.0, 6.0, body, False, WINGS),
             ("The same at night", 90.0, 6.0, body, True, WINGS),
             ("Its back from behind and above (wings left off), by day", -150.0, 30.0, back, False, WINGS)]
    parts = []
    for title, yaw, el, bounds, night, hidden in views:
        ims = [V.label(picture(m, tex, yaw, el, bounds, night, hidden), f"{label}: {mood}", 17)
               for label, mood, m, tex in looks]
        parts.append((title, V.grid(ims, len(ims))))
    # How much of each glows.
    cards = []
    for label, mood, m, tex in looks:
        body_share, wing_share = glowing_share(m, tex, True), glowing_share(m, tex, False)
        print(f"  {label} ({mood}): {body_share * 100:.1f}% of its body's texels glow, {wing_share * 100:.1f}% of its "
              f"wings'")
        im = Image.new("RGB", (CELL, 64), V.SHEET_BG)
        d = ImageDraw.Draw(im)
        d.text((8, 6), f"{label}: {mood}", font=font(17), fill=V.TEXT)
        d.text((8, 32), f"body {body_share * 100:.1f}% glowing, wings {wing_share * 100:.1f}%",
               font=font(15, bold=False), fill=V.DIM)
        cards.append(im)
    parts.insert(0, ("How much of it glows (its texture's texels)", V.grid(cards, len(cards))))
    os.makedirs(OUT, exist_ok=True)
    V.page("The Emberdactyl: less lava on its body",
           "E1A is the one in the game; E1B, E1C and E1D keep its wings as they are and have less and less lava on "
           "its body, neck, head, legs, tail, horns and half moon (the user, October 31, 2026: \"less lava spots on "
           "the adult version on the body; the wings are fine, but on the body and horns less lava\"). In the game: "
           "/dross showcase boss_designs emberdactyl (the four side by side, A on the left).", parts,
           os.path.join(OUT, "lava_looks.png"), accent=ACCENT)
    print(f"Wrote {os.path.relpath(os.path.join(OUT, 'lava_looks.png'), ROOT)}")


if __name__ == "__main__":
    main()
