"""
Static check of every Dross block and item's files (no game needed).

Run from the project folder:    python tools/terrain/check_assets.py

For every block registered in registry/ModBlocks and every item in registry/ModItems (it reads their
`register("id", ...)` calls) it checks:
    blocks:  the blockstate; every model it uses, following parents (dross models in this project, minecraft ones in
             the game jar if it can be found); every texture those models use, including texture variables (#name)
             that an element uses but nothing defines; the English name (block.dross.<id>); a loot table, unless the
             block drops nothing on purpose (NO_LOOT below)
    items:   the item model (and its parents and textures; builtin/entity models are drawn by code and skipped) and
             the English name (block.dross.<id> for block items, item.dross.<id> otherwise)
    data:    every dross id named in a loot table, recipe or tag is a registered block/item (or tag)
It prints the problems and exits with code 1 if there are any (warnings alone don't fail it).
"""
import glob
import json
import os
import re
import sys
import zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "registry")
RES = os.path.join(ROOT, "src", "main", "resources")
ASSETS = os.path.join(RES, "assets")
DATA = os.path.join(RES, "data")
MOD = "dross"

# Blocks without an item on purpose (no item model or English name needed).
NO_ITEM = {
    "dross_portal": "the lit portal itself",
    "cinder_lava": "the Cinder Colossus's temporary lava",
    "cinder_fire": "the Cinder Colossus's temporary fire",
}
# Blocks that drop nothing on purpose (no loot table).
NO_LOOT = {
    "dross_portal_frame": "unbreakable, drops nothing",
    "dross_portal": "the portal itself",
    "cinder_lava": "temporary, drops nothing",
    "cinder_fire": "temporary, drops nothing",
}

errors = []
warnings = []


def error(msg):
    errors.append(msg)


def warn(msg):
    warnings.append(msg)


# ---------------------------------------------------------------------------------------------------------------------
# What's registered
# ---------------------------------------------------------------------------------------------------------------------


def registered():
    blocks_src = open(os.path.join(JAVA, "ModBlocks.java"), encoding="utf-8").read()
    items_src = open(os.path.join(JAVA, "ModItems.java"), encoding="utf-8").read()
    block_const = dict(re.findall(r"RegistryObject<Block>\s+(\w+)\s*=\s*BLOCKS\.register\(\s*\"([a-z0-9_]+)\"", blocks_src))
    blocks = list(block_const.values())
    items = {}
    for m in re.finditer(r"ITEMS\.register\(\s*\"([a-z0-9_]+)\"(.*?)\);\s*$", items_src, re.S | re.M):
        iid, body = m.group(1), m.group(2)
        ref = re.search(r"ModBlocks\.(\w+)\.get\(\)", body)
        items[iid] = block_const.get(ref.group(1)) if ref else None      # the block it places, if any
    return blocks, items


# ---------------------------------------------------------------------------------------------------------------------
# Vanilla files (models and textures) from the game jar, if it can be found
# ---------------------------------------------------------------------------------------------------------------------

_vanilla = None


def vanilla():
    global _vanilla
    if _vanilla is None:
        _vanilla = {}
        home = os.path.expanduser("~")
        for jar in glob.glob(os.path.join(home, ".gradle", "caches", "forge_gradle", "minecraft_repo", "versions",
                                          "1.20.1", "client-extra.jar")):
            try:
                z = zipfile.ZipFile(jar)
                _vanilla = {"zip": z, "names": set(z.namelist())}
                break
            except (OSError, zipfile.BadZipFile):
                continue
    return _vanilla


def split(ref, default_ns="minecraft"):
    ns, path = ref.split(":", 1) if ":" in ref else (default_ns, ref)
    return ns, path


def load_model(ref):
    """The model json for 'ns:path' (path like block/x), or None if missing; 'skip' for vanilla when the jar is unknown."""
    ns, path = split(ref)
    if ns == MOD:
        f = os.path.join(ASSETS, ns, "models", *path.split("/")) + ".json"
        if not os.path.exists(f):
            return None
        with open(f, encoding="utf-8") as fh:
            return json.load(fh)
    v = vanilla()
    if not v:
        return "skip"
    name = f"assets/{ns}/models/{path}.json"
    if name not in v["names"]:
        return None
    return json.loads(v["zip"].read(name))


def texture_exists(ref):
    ns, path = split(ref)
    if ns == MOD:
        return os.path.exists(os.path.join(ASSETS, ns, "textures", *path.split("/")) + ".png")
    v = vanilla()
    if not v:
        return True
    return f"assets/{ns}/textures/{path}.png" in v["names"]


used_textures = set()


def check_model(ref, owner, seen=None):
    """Follows a model's parents, merges its textures, and checks every texture it ends up using."""
    chain = []
    cur = ref
    while cur:
        if cur.startswith("builtin/") or cur.startswith("minecraft:builtin/"):
            break
        model = load_model(cur)
        if model is None:
            error(f"{owner}: model {cur} doesn't exist")
            return
        if model == "skip":
            break
        chain.append((cur, model))
        cur = model.get("parent")
        if len(chain) > 20:
            error(f"{owner}: model {ref} has a parent loop")
            return
    textures = {}
    for _, model in reversed(chain):              # parents first, children override
        textures.update(model.get("textures", {}))
    elements = None
    for _, model in chain:                        # the nearest model with elements wins
        if "elements" in model:
            elements = model["elements"]
            break

    def resolve(value, depth=0):
        while isinstance(value, str) and value.startswith("#") and depth < 10:
            value = textures.get(value[1:])
            depth += 1
        return value

    for key, value in textures.items():
        final = resolve(value)
        if final is None:
            continue                               # an unused variable left over from a parent: harmless
        if not texture_exists(final):
            error(f"{owner}: model {ref} uses texture {final} ({key}), which doesn't exist")
        else:
            used_textures.add(split(final)[0] + ":" + split(final)[1])
    for el in elements or []:
        for face, data in el.get("faces", {}).items():
            tex = data.get("texture", "")
            if tex.startswith("#") and resolve(tex) is None:
                error(f"{owner}: model {ref} has a {face} face using {tex}, which no model in its chain defines")


def blockstate_models(state):
    refs = []
    for variant in state.get("variants", {}).values():
        for v in (variant if isinstance(variant, list) else [variant]):
            refs.append(v["model"])
    for part in state.get("multipart", []):
        apply = part["apply"]
        for v in (apply if isinstance(apply, list) else [apply]):
            refs.append(v["model"])
    return sorted(set(refs))


# ---------------------------------------------------------------------------------------------------------------------
# Data files: every dross id they name must exist
# ---------------------------------------------------------------------------------------------------------------------


def check_data(blocks, items):
    item_ids = {f"{MOD}:{i}" for i in items}
    block_ids = {f"{MOD}:{b}" for b in blocks}

    def walk_names(obj, found):
        if isinstance(obj, dict):
            for k, v in obj.items():
                if k in ("name", "item") and isinstance(v, str):
                    found.append(v)
                elif k == "result" and isinstance(v, str):
                    found.append(v)
                else:
                    walk_names(v, found)
        elif isinstance(obj, list):
            for v in obj:
                walk_names(v, found)

    for f in glob.glob(os.path.join(DATA, MOD, "loot_tables", "blocks", "*.json")) + \
            glob.glob(os.path.join(DATA, MOD, "recipes", "*.json")):
        with open(f, encoding="utf-8") as fh:
            data = json.load(fh)
        names = []
        walk_names(data, names)
        for n in names:
            if n.startswith(MOD + ":") and n not in item_ids:
                error(f"{os.path.relpath(f, ROOT)} names {n}, which isn't a registered item")
        tags = re.findall(r'"tag":\s*"([a-z0-9_]+:[a-z0-9_/]+)"', json.dumps(data))
        for t in tags:
            ns, path = t.split(":", 1)
            if not os.path.exists(os.path.join(DATA, ns, "tags", "items", *path.split("/")) + ".json") and ns == MOD:
                error(f"{os.path.relpath(f, ROOT)} uses item tag #{t}, which doesn't exist")
    for kind, ids in (("blocks", block_ids), ("items", item_ids)):
        for f in glob.glob(os.path.join(DATA, "*", "tags", kind, "**", "*.json"), recursive=True):
            with open(f, encoding="utf-8") as fh:
                data = json.load(fh)
            for v in data.get("values", []):
                v = v["id"] if isinstance(v, dict) else v
                if v.startswith("#"):
                    ns, path = v[1:].split(":", 1)
                    if ns == MOD and not os.path.exists(os.path.join(DATA, ns, "tags", kind, *path.split("/")) + ".json"):
                        error(f"{os.path.relpath(f, ROOT)} includes tag {v}, which doesn't exist")
                elif v.startswith(MOD + ":") and v not in ids:
                    error(f"{os.path.relpath(f, ROOT)} names {v}, which isn't a registered {kind[:-1]}")


# ---------------------------------------------------------------------------------------------------------------------


def main():
    blocks, items = registered()
    with open(os.path.join(ASSETS, MOD, "lang", "en_us.json"), encoding="utf-8") as fh:
        lang = json.load(fh)
    if not vanilla():
        warn("the game jar wasn't found: minecraft: models and textures were not checked")

    for b in blocks:
        owner = f"block {b}"
        bs = os.path.join(ASSETS, MOD, "blockstates", b + ".json")
        if not os.path.exists(bs):
            error(f"{owner}: no blockstate (assets/dross/blockstates/{b}.json)")
        else:
            with open(bs, encoding="utf-8") as fh:
                for ref in blockstate_models(json.load(fh)):
                    check_model(ref, owner)
        key = f"block.{MOD}.{b}"
        if key not in lang:
            (warn if b in NO_ITEM else error)(f"{owner}: no English name ({key})" + (f" [{NO_ITEM[b]}]" if b in NO_ITEM else ""))
        if b not in NO_LOOT and not os.path.exists(os.path.join(DATA, MOD, "loot_tables", "blocks", b + ".json")):
            error(f"{owner}: no loot table (data/dross/loot_tables/blocks/{b}.json); add it to NO_LOOT if it drops nothing on purpose")
        if b not in NO_ITEM and b not in items:
            warn(f"{owner}: no item registered with the same id")

    for i, block in items.items():
        owner = f"item {i}"
        model = os.path.join(ASSETS, MOD, "models", "item", i + ".json")
        if not os.path.exists(model):
            error(f"{owner}: no item model (assets/dross/models/item/{i}.json)")
        else:
            check_model(f"{MOD}:item/{i}", owner)
        key = f"block.{MOD}.{block}" if block else f"item.{MOD}.{i}"
        if key not in lang:
            error(f"{owner}: no English name ({key})")

    check_data(blocks, items)

    # textures nobody uses (only a warning: entity textures etc. are used by code)
    for folder in ("block", "item"):
        for f in glob.glob(os.path.join(ASSETS, MOD, "textures", folder, "*.png")):
            ref = f"{MOD}:{folder}/{os.path.splitext(os.path.basename(f))[0]}"
            if ref not in used_textures and not f.endswith("_flow.png") and not f.endswith("_flow_base.png"):
                warn(f"texture {ref} isn't used by any block or item model (fine if code draws it)")

    print(f"check_assets: {len(blocks)} blocks, {len(items)} items checked")
    for w in warnings:
        print("  warning: " + w)
    for e in errors:
        print("  ERROR: " + e)
    if errors:
        print(f"check_assets: {len(errors)} problem(s)")
        sys.exit(1)
    print("check_assets: all good")


if __name__ == "__main__":
    main()
