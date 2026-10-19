"""Writes a village grid as a structure template (.nbt), with block entity data where a design set some (banner
patterns: grid.block_nbt[(x, y, z)] = {"id": "minecraft:banner", "Patterns": [...]})."""
import mc
import nbt

DATA_VERSION = 3465  # Minecraft 1.20.1

# Banner pattern codes (1.20.1) and dye numbers, for the hunters' banners.
DYE = {"white": 0, "orange": 1, "magenta": 2, "light_blue": 3, "yellow": 4, "lime": 5, "pink": 6, "gray": 7,
       "light_gray": 8, "cyan": 9, "purple": 10, "blue": 11, "brown": 12, "green": 13, "red": 14, "black": 15}


def banner(*patterns):
    """Block entity data for a banner: patterns as (code, dye name) pairs, e.g. ("sku", "white")."""
    items = nbt.List(nbt.COMPOUND, [{"Pattern": code, "Color": nbt.Int(DYE[colour])} for code, colour in patterns])
    return {"id": "minecraft:banner", "Patterns": items}


def write(grid, path):
    used = {}
    palette = nbt.List(nbt.COMPOUND)
    blocks = nbt.List(nbt.COMPOUND)
    extra = getattr(grid, "block_nbt", {})
    G = grid.G
    for x, yi, z, s in grid.blocks():
        idx = used.get(s)
        if idx is None:
            idx = len(palette)
            used[s] = idx
            name, props = mc.parse(s)
            entry = {"Name": name}
            if props:
                entry["Properties"] = {k: str(v) for k, v in props.items()}
            palette.append(entry)
        entry = {"pos": nbt.List(nbt.INT, [nbt.Int(x), nbt.Int(yi), nbt.Int(z)]), "state": nbt.Int(idx)}
        data = extra.get((x, yi - G, z))
        if data is not None:
            entry["nbt"] = data
        blocks.append(entry)
    root = {
        "DataVersion": nbt.Int(DATA_VERSION),
        "size": nbt.List(nbt.INT, [nbt.Int(grid.W), nbt.Int(grid.H), nbt.Int(grid.D)]),
        "palette": palette,
        "blocks": blocks,
        "entities": nbt.List(nbt.END),
    }
    nbt.write_file(path, root)
    return len(blocks), len(palette)
