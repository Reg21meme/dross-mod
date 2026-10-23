"""
The Wild Hunt as one model (the user, October 23, 2026: "make them one model, then separate the models after the
split"): the Alpha Moose with the Huntsman in its saddle, which the Alpha Moose wears while he rides it (stage 1 of his
fight), so the two are drawn from one place and one clock and can't drift apart on the screen. Built from the two
models tools/rimefall/mobs.py writes (alpha_moose and huntsman_rider): their bones side by side (the rider's renamed
r_<bone>: both have a root and a head), one texture with the rider's to the right of the moose's (each layer: the solid
parts, _glow, _ice, _iceglow), and every animation of both (the moose's under their own names, the rider's ride_* and
carry_* with his bones renamed). In the game three controllers play them on the moose: its own animation, the rider's,
and the saddle's motion under him (carry_<the moose's animation>).

  python tools/rimefall/wildhunt_model.py       (mobs.py runs it after building either of the two)

Writes src/main/resources/assets/dross/geo/entity/wild_hunt/wild_hunt.geo.json,
animations/entity/wild_hunt/wild_hunt.animation.json and textures/entity/wild_hunt/wild_hunt*.png.
"""
import copy
import json
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "dross")
MOOSE, RIDER, OUT = "alpha_moose", "huntsman_rider", "wild_hunt"
PREFIX = "r_"                      # the rider's bones in the combined model
LAYERS = ("", "_glow", "_ice", "_iceglow")


def path(kind, model, name):
    return os.path.join(ASSETS, kind, "entity", model, name)


def load(kind, model, ext):
    with open(path(kind, model, model + ext), encoding="utf-8") as f:
        return json.load(f)


def rider_bone(name):
    return PREFIX + name


def merge_geo(moose, rider, shift_u):
    gm = moose["minecraft:geometry"][0]
    gr = rider["minecraft:geometry"][0]
    out = copy.deepcopy(moose)
    g = out["minecraft:geometry"][0]
    dm, dr = gm["description"], gr["description"]
    g["description"]["identifier"] = "geometry.dross_" + OUT
    g["description"]["texture_width"] = dm["texture_width"] + dr["texture_width"]
    g["description"]["texture_height"] = max(dm["texture_height"], dr["texture_height"])
    for k in ("visible_bounds_width", "visible_bounds_height"):
        g["description"][k] = max(dm.get(k, 0), dr.get(k, 0))
    for b in gr["bones"]:
        b = copy.deepcopy(b)
        b["name"] = rider_bone(b["name"])
        if "parent" in b:
            b["parent"] = rider_bone(b["parent"])
        for c in b.get("cubes", []):
            uv = c.get("uv")
            if isinstance(uv, dict):
                for face in uv.values():
                    face["uv"][0] += shift_u
            elif isinstance(uv, list):
                uv[0] += shift_u
        g["bones"].append(b)
    return out


def merge_anims(moose, rider):
    out = {"format_version": moose.get("format_version", "1.8.0"), "animations": {}}
    for name, a in moose["animations"].items():
        out["animations"]["animation." + OUT + "." + name.split(".")[-1]] = a
    for name, a in rider["animations"].items():
        a = copy.deepcopy(a)
        a["bones"] = {rider_bone(b): v for b, v in a.get("bones", {}).items()}
        out["animations"]["animation." + OUT + "." + name.split(".")[-1]] = a
    return out


def merge_textures():
    for layer in LAYERS:
        ims = []
        for model in (MOOSE, RIDER):
            p = path("textures", model, model + layer + ".png")
            ims.append(Image.open(p).convert("RGBA") if os.path.exists(p) else None)
        size_m = Image.open(path("textures", MOOSE, MOOSE + ".png")).size
        size_r = Image.open(path("textures", RIDER, RIDER + ".png")).size
        im = Image.new("RGBA", (size_m[0] + size_r[0], max(size_m[1], size_r[1])), (0, 0, 0, 0))
        if ims[0] is not None:
            im.paste(ims[0], (0, 0))
        if ims[1] is not None:
            im.paste(ims[1], (size_m[0], 0))
        dest = path("textures", OUT, OUT + layer + ".png")
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        im.save(dest)


def write(kind, ext, data):
    dest = path(kind, OUT, OUT + ext)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=1)
        f.write("\n")


def main():
    moose_geo, rider_geo = load("geo", MOOSE, ".geo.json"), load("geo", RIDER, ".geo.json")
    shift = moose_geo["minecraft:geometry"][0]["description"]["texture_width"]
    write("geo", ".geo.json", merge_geo(moose_geo, rider_geo, shift))
    write("animations", ".animation.json", merge_anims(load("animations", MOOSE, ".animation.json"),
                                                       load("animations", RIDER, ".animation.json")))
    merge_textures()
    print(f"wrote the Wild Hunt's combined model ({OUT}): the moose's bones and the rider's as {PREFIX}<bone>")


if __name__ == "__main__":
    main()
