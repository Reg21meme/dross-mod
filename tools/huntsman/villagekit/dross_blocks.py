"""Teaches the shrine kit (tools/shrines/mc.py) the Dross blocks the villages use: their states for the checks and
their models and textures (from the mod's own assets folder) for the previews."""
import json
import os

import mc

BLOCKS = {
    "dross:hoarfrost": {"properties": {}, "default": {}},
    "dross:frostbitten_log": {"properties": {"axis": ["x", "y", "z"]}, "default": {"axis": "y"}},
    "dross:icicle": {"properties": {}, "default": {}},
    "dross:frostbloom": {"properties": {}, "default": {}},
}
mc.MOD_BLOCKS.update(BLOCKS)
mc.CROSS_PLANTS.update({"frostbloom", "icicle"})

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
