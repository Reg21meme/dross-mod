"""
The hunter villagers' callings, side by side (rimemobs/hunter_folk.py: the Huntsman's model on foot with each calling's
gear; the game draws them at half his size, about 2 blocks tall): each in its own gear, from the front and from its
left, standing, then in its attack. Writes boss-previews/rimefall/hunters/folk.png and index.html.

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
from rimemobs import hunter_folk as F  # noqa: E402

OUT = os.path.join(ROOT, "boss-previews", "rimefall", "hunters")
# Each calling's attack and the moment of it shown on its card (its gear and moves: rimemobs/hunter_folk.CALLINGS).
MOVE = {"spearman": ("spear_combo", 0.38), "axeman": ("chop", 0.5), "pikeman": ("thrust_sweep", 0.4),
        "archer": ("quick_shot", 1.05), "hornblower": ("horn_blow", 1.6), "farmer": ("chop", 0.5),
        "butcher": ("chop", 0.5), "herder": ("chop", 0.38), "fisher": ("idle", 1.0), "tanner": ("chop", 0.5),
        "elder": ("chop", 0.38)}
ROLES = tuple((key, name, kind, shows) + MOVE[key] for key, name, kind, shows, _plays in F.CALLINGS)
hidden_for = F.hidden_for


def main():
    m, texs = mobkit.build_model(F, verbose=False)
    rig = mobkit.MobRig(m, F)
    anims = F.animations(rig)
    tex = texs["hunter_folk"]
    cards = []
    for key, name, kind, shows, move, at in ROLES:
        hid = hidden_for(shows)
        ims = []
        for yaw, el, anim, t in ((20.0, 8.0, "idle", 0.0), (100.0, 6.0, "idle", 0.0), (35.0, 10.0, move, at)):
            pose = anims[anim].sample(t)
            ims.append(V.draw(m, tex, yaw=yaw, elevation=el, scale=4.2, pose=pose, hidden=hid, grid=False, figure=False))
        h = max(i.height for i in ims)
        w = sum(i.width for i in ims) + 8 * (len(ims) - 1)
        card = Image.new("RGB", (max(w, 360), h + 56), V.SHEET_BG)
        x = 0
        for im in ims:
            card.paste(im, (x, 56 + h - im.height))
            x += im.width + 8
        d = ImageDraw.Draw(card)
        d.text((8, 4), name, font=V.font(24), fill=V.TEXT)
        d.text((8, 34), f"{kind}: front, from its left, and its {move.replace('_', ' ')}", font=V.font(15, bold=False),
               fill=V.DIM)
        cards.append(card)
        card.save(os.path.join(OUT, f"folk_{key}.png"))
    os.makedirs(OUT, exist_ok=True)
    V.page("The hunter villagers (the Huntsman's look at 2 blocks)",
           "Eleven callings in the Huntsman's own model, each with its own gear: five warriors, five workers (no antlers "
           "on their hoods) and the elder (his wolf's face pushed back off his own: scarred eyes, a grey beard, a sword). "
           "In the game they're half the Huntsman's size, about 2 blocks to the top of the hood.",
           [("Warriors", V.grid(cards[0:5], 3)), ("Workers", V.grid(cards[5:10], 3)), ("The elder", V.grid(cards[10:], 3))],
           os.path.join(OUT, "folk.png"))
    with open(os.path.join(OUT, "folk.html"), "w", encoding="utf-8") as f:
        f.write("<!doctype html><meta charset=utf-8><title>Hunter villagers</title>"
                "<style>body{background:#12161c;color:#ecf2f8;font-family:Segoe UI,sans-serif;margin:24px}"
                "img{max-width:100%;display:block;margin:8px 0 28px}h2{color:#96d6ec;margin:0}</style>"
                "<h1>The hunter villagers</h1>")
        for key, name, kind, _s, _m, _a in ROLES:
            f.write(f"<h2>{name}</h2><div>{kind}</div><img src='folk_{key}.png' alt='{name}'>")
    print(f"Wrote {len(ROLES)} callings to {OUT}")


if __name__ == "__main__":
    main()
