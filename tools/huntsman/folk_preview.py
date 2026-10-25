"""
The Frostfolk's callings, side by side (rimemobs/frostfolk.py: a pillager's build, each calling its own hood, clothes,
cape and tools, and its own texture): each from the front, from its left and from behind, standing, then in its own
move. Writes boss-previews/rimefall/frostfolk/frostfolk.png, a card for each calling and index.html.

  python tools/huntsman/folk_preview.py
"""
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, os.path.join(ROOT, "tools", "rimefall"))

from PIL import Image, ImageDraw  # noqa: E402

from rimekit import mobkit  # noqa: E402
from rimekit import view as V  # noqa: E402
from rimemobs import frostfolk as F  # noqa: E402

OUT = os.path.join(ROOT, "boss-previews", "rimefall", "frostfolk")
# Each calling's move and the moment of it shown on its card (its gear and moves: rimemobs/frostfolk.CALLINGS).
MOVE = {"spearman": ("spear_combo", 0.4), "axeman": ("chop", 0.38), "pikeman": ("thrust_sweep", 0.4),
        "archer": ("quick_shot", 1.05), "hornblower": ("horn_blow", 1.6), "farmer": ("chop", 0.5),
        "butcher": ("chop", 0.5), "herder": ("talk", 0.8), "fisher": ("talk", 0.8), "tanner": ("chop", 0.38),
        "elder": ("chop", 0.45)}
# (each calling's own version of an idle, talk or chop: the way it carries its tool, F.STYLE)
ROLES = tuple((key, name, kind, shows, F.anim_for(key, MOVE[key][0]), MOVE[key][1])
              for key, name, kind, shows, _plays in F.CALLINGS)
SCALE = 9.0
VIEWS = ((20.0, 8.0), (95.0, 6.0), (200.0, 8.0))


def main():
    m, texs = mobkit.build_model(F, verbose=False)
    rig = mobkit.MobRig(m, F)
    anims = F.animations(rig)
    os.makedirs(OUT, exist_ok=True)
    cards = []
    for key, name, kind, shows, move, at in ROLES:
        hid = F.hidden_for(shows)
        tex = texs[key]
        ims = []
        for yaw, el in VIEWS:
            ims.append(V.draw(m, tex, yaw=yaw, elevation=el, scale=SCALE, pose=anims[F.anim_for(key, "idle")].sample(0.0), hidden=hid,
                              grid=False, figure=False))
        ims.append(V.draw(m, tex, yaw=35.0, elevation=10.0, scale=SCALE, pose=anims[move].sample(at), hidden=hid,
                          grid=False, figure=False))
        h = max(i.height for i in ims)
        w = sum(i.width for i in ims) + 10 * (len(ims) - 1)
        card = Image.new("RGB", (max(w, 420), h + 60), V.SHEET_BG)
        x = 0
        for im in ims:
            card.paste(im, (x, 60 + h - im.height))
            x += im.width + 10
        d = ImageDraw.Draw(card)
        d.text((8, 4), f"Frostfolk {name}" if key != "elder" else "The Frostfolk Elder", font=V.font(24), fill=V.TEXT)
        d.text((8, 36), f"{kind}: front, from its left, from behind, and its {MOVE[key][0].replace('_', ' ')}",
               font=V.font(15, bold=False), fill=V.DIM)
        cards.append(card)
        card.save(os.path.join(OUT, f"frostfolk_{key}.png"))
    V.page("The Frostfolk",
           "Rimefall's villagers: a pillager's build, no face under the hood but darkness and glowing blue eyes, blue "
           "hoods in a style of their own for each calling, gloves, simple clothes, and their tools held in their fists: "
           "five warriors, five workers and the elder (a white scar across his right eye, a white beard, a sword on "
           "his back, drawn to strike).",
           [("Warriors", V.grid(cards[0:5], 2)), ("Workers", V.grid(cards[5:10], 2)), ("The elder", V.grid(cards[10:], 2))],
           os.path.join(OUT, "frostfolk.png"))
    with open(os.path.join(OUT, "index.html"), "w", encoding="utf-8") as f:
        f.write("<!doctype html><meta charset=utf-8><title>The Frostfolk</title>"
                "<style>body{background:#12161c;color:#ecf2f8;font-family:Segoe UI,sans-serif;margin:24px}"
                "img{max-width:100%;display:block;margin:8px 0 28px}h2{color:#96d6ec;margin:0}</style>"
                "<h1>The Frostfolk</h1>")
        for key, name, kind, _s, _m, _a in ROLES:
            f.write(f"<h2>{name}</h2><div>{kind}</div><img src='frostfolk_{key}.png' alt='{name}'>")
    print(f"Wrote {len(ROLES)} callings to {OUT}")


if __name__ == "__main__":
    main()
