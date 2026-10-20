"""Teaches the shrine kit (tools/shrines/mc.py) the Dross blocks the villages use: their states for the checks and
their models and textures (from the mod's own assets folder) for the previews."""
import json
import os
from functools import lru_cache

import mc

BLOCKS = {
    "dross:hoarfrost": {"properties": {}, "default": {}},
    "dross:frostbitten_log": {"properties": {"axis": ["x", "y", "z"]}, "default": {"axis": "y"}},
    "dross:icicle": {"properties": {}, "default": {}},
    "dross:frostbloom": {"properties": {}, "default": {}},
    # the hunters' meat (boss/huntsman/village/MeatBlocks): pillars like a log, the bone along their axis
    "dross:meat_block": {"properties": {"axis": ["x", "y", "z"]}, "default": {"axis": "y"}},
    "dross:roasted_meat_block": {"properties": {"axis": ["x", "y", "z"]}, "default": {"axis": "y"}},
    # Rimefall's crops (boss/huntsman/village/HunterCrops) and their crate and basket
    "dross:frostroots": {"properties": {"age": [str(a) for a in range(8)]}, "default": {"age": "0"}},
    "dross:snow_lichen": {"properties": {"age": [str(a) for a in range(8)]}, "default": {"age": "0"}},
    "dross:rimeberry_bush": {"properties": {"age": ["0", "1", "2", "3"], "half": ["lower", "upper"]},
                             "default": {"age": "0", "half": "lower"}},
    "dross:frostroot_crate": {"properties": {}, "default": {}},
    "dross:rimeberry_basket": {"properties": {}, "default": {}},
}
mc.MOD_BLOCKS.update(BLOCKS)
mc.CROSS_PLANTS.update({"frostbloom", "icicle", "frostroots", "snow_lichen", "rimeberry_bush"})

# pillars the shrine kit doesn't know lie down like logs in the previews (bark on top, the end facing its axis)
PILLARS = {"meat_block", "roasted_meat_block"}

_jar_json = mc._read_json


def _read_json(path):
    """Minecraft's files from the game jar, the mod's from src/main/resources/assets."""
    if path.startswith("assets/dross/"):
        file = os.path.join(mc.MOD_ASSETS, *path.split("/")[1:])
        if not os.path.exists(file):
            return None
        with open(file, encoding="utf-8") as f:
            return json.load(f)
    return _jar_json(path)


mc._read_json = _read_json

_faces = mc.faces


@lru_cache(maxsize=None)
def _pillar_faces(state):
    f = _faces(state)
    if mc.base(state) in PILLARS and "axis" not in f:
        axis = mc.props_of(state).get("axis", "y")
        if axis != "y":
            # the horizontal model's up face is the cut end: lying down, the side shows on top
            return {"top": f["side"], "side": f["side"], "bottom": f["side"], "end": f["top"], "axis": axis}
    return f


mc.faces = _pillar_faces
