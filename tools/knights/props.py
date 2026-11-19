"""
The elemental knights' thrown props, as models of their own (the game draws them while they fly):

  wreck_anchor   T3A's anchor, out of his hand on its chain (boss/knight/KnightAnchor): the same anchor as on his
                 shoulder (knightdesigns/t03_wreck.py `anchor`), its ring at the back and its crown and flukes pointing
                 the way it flies (-z, the model's front), its middle at the model's origin.

Writes assets/dross/geo|animations|textures/entity/knightmob/<id>.* (an empty animation file: GeckoLib asks for one).
Run: python tools/knights/props.py
"""
import json
import os
import sys

sys.dont_write_bytecode = True

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
for sub in ("golems", "rimefall", "fire", "blight"):
    sys.path.insert(0, os.path.join(ROOT, "tools", sub))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

from rimekit import mobkit, uvpack  # noqa: E402
from knightdesigns import t03_wreck as T  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
MODEL_DIR = "knightmob"
HALF = 14.0    # the anchor's length either side of its middle (model units)


class Design:
    @staticmethod
    def build(s, variant):
        T.anchor(s, (0.0, 0.0, HALF), (0.0, 0.0, -HALF), (1.0, 0.0, 0.0))

    @staticmethod
    def materials(design):
        return T.materials(T.VARIANTS[0]) if getattr(T, "VARIANTS", None) else T.materials()


class Anchor:
    ID = "wreck_anchor"
    K = 1.0
    BONES = [("body", (0.0, 0.0, 0.0), None)]
    DESIGN = Design
    BUILD_VARIANT = None
    VARIANTS = [{"name": "wreck_anchor", "design": None}]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def main():
    mob = Anchor
    m, texs = mobkit.build_model(mob)
    pts = mobkit.V.corners(m) / 16.0
    span = float(np.abs(pts).max() * 2.0 + 1.0)
    write_json(os.path.join(ASSETS, "geo", "entity", MODEL_DIR, f"{mob.ID}.geo.json"),
               uvpack.to_json(m, (span, span, 0.0)))
    write_json(os.path.join(ASSETS, "animations", "entity", MODEL_DIR, f"{mob.ID}.animation.json"),
               {"format_version": "1.8.0", "animations": {}})
    mobkit.save_textures(texs["wreck_anchor"], os.path.join(ASSETS, "textures", "entity", MODEL_DIR), mob.ID)
    print("Done.")


if __name__ == "__main__":
    main()
