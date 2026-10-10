"""Writes a Grid as a Minecraft 1.20.1 structure template (.nbt) and checks it first."""
import mc
import nbt
from voxel import FRAME, STEP

DATA_VERSION = 3465  # Minecraft 1.20.1

SOIL_FOR_BUSH = {"sand", "red_sand", "suspicious_sand", "terracotta", "dirt", "grass_block", "podzol", "coarse_dirt",
                 "mycelium", "rooted_dirt", "moss_block", "mud", "muddy_mangrove_roots"}
GROUND_PLANTS = {"grass", "fern", "tall_grass", "large_fern", "poppy", "dandelion", "cornflower", "azure_bluet",
                 "oxeye_daisy", "allium", "blue_orchid", "lily_of_the_valley", "wither_rose", "sweet_berry_bush",
                 "torchflower", "pink_petals", "oak_sapling", "spruce_sapling", "dark_oak_sapling", "lilac",
                 "rose_bush", "peony", "sunflower"}
SOIL_FOR_GRASS = {"dirt", "grass_block", "podzol", "coarse_dirt", "mycelium", "rooted_dirt", "moss_block", "mud",
                  "muddy_mangrove_roots", "farmland"}


def find_frames(grid):
    """Every complete Dross frame with an all-air opening (the same rules as the mod's PortalCastle finder)."""
    frame = set()
    air = set()
    for x, yi, z, s in grid.blocks():
        b = mc.parse(s)[0]
        if s == mc.canon(FRAME):
            frame.add((x, yi, z))
        elif b in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air"):
            air.add((x, yi, z))
    found = []
    for (x, y, z) in air:
        for axis in ("x", "z"):
            ax = (1, 0, 0) if axis == "x" else (0, 0, 1)

            def rel(p, n):
                return (p[0] + ax[0] * n, p[1], p[2] + ax[2] * n)

            mn = (x, y, z)
            if (x, y - 1, z) not in frame or rel(mn, -1) not in frame:
                continue
            w = 0
            while w <= 21 and rel(mn, w) in air and (rel(mn, w)[0], y - 1, rel(mn, w)[2]) in frame:
                w += 1
            h = 0
            while h <= 21 and (x, y + h, z) in air:
                h += 1
            if not (2 <= w <= 21 and 3 <= h <= 21):
                continue
            ok = True
            for i in range(w):
                c = rel(mn, i)
                if (c[0], y - 1, c[2]) not in frame or (c[0], y + h, c[2]) not in frame:
                    ok = False
                    break
                for j in range(h):
                    if (c[0], y + j, c[2]) not in air:
                        ok = False
                        break
                if not ok:
                    break
            if ok:
                for j in range(h):
                    l, r = rel(mn, -1), rel(mn, w)
                    if (l[0], y + j, l[2]) not in frame or (r[0], y + j, r[2]) not in frame:
                        ok = False
                        break
            if ok:
                found.append((mn, axis, w, h))
    return found


def check(grid):
    """Returns (problems, warnings)."""
    problems, warnings = [], []
    frames = find_frames(grid)
    if len(frames) != 1:
        problems.append(f"needs exactly one complete empty frame, found {len(frames)}: {frames}")
    G = grid.G
    unsupported = []
    for x, yi, z, s in grid.blocks():
        y = yi - G
        k = mc.kind(s)
        b = mc.base(s)
        below = grid.get(x, y - 1, z)
        below_b = mc.base(below) if below else ("grass_block" if y - 1 <= 0 else "air")
        if b == "dead_bush" and below_b not in SOIL_FOR_BUSH:
            unsupported.append(f"dead_bush on {below_b} at {x},{y},{z}")
        elif b in GROUND_PLANTS and below_b not in SOIL_FOR_GRASS and below_b != b:
            unsupported.append(f"{b} on {below_b} at {x},{y},{z}")
        elif b in ("lantern", "soul_lantern"):
            hanging = mc.props_of(s).get("hanging") == "true"
            other = grid.get(x, y + 1, z) if hanging else below
            if other is None and not (not hanging and y - 1 <= 0):
                unsupported.append(f"{b} (hanging={hanging}) with nothing {'above' if hanging else 'below'} at {x},{y},{z}")
            elif other is not None and mc.is_air(other):
                unsupported.append(f"{b} (hanging={hanging}) with air {'above' if hanging else 'below'} at {x},{y},{z}")
        elif b == "vine":
            props = mc.props_of(s)
            ok = False
            for d in ("north", "south", "east", "west"):
                if props.get(d) == "true":
                    dx, dz = STEP[d]
                    if grid.opaque(x + dx, y, z + dz):
                        ok = True
            if props.get("up") == "true" and grid.opaque(x, y + 1, z):
                ok = True
            if not ok:
                above = grid.get(x, y + 1, z)
                if not (above and mc.base(above) == "vine"):
                    unsupported.append(f"vine with no support at {x},{y},{z}")
        elif k in ("cross",) and b.startswith("dead_") and "coral" in b:
            if below is None and y - 1 > 0 or (below is not None and mc.kind(below) != "full"):
                unsupported.append(f"{b} not on a full block at {x},{y},{z}")
        elif k == "water":
            for d in ("north", "south", "east", "west"):
                dx, dz = STEP[d]
                n = grid.get(x + dx, y, z + dz)
                if n is None and y > 0 or (n is not None and mc.is_air(n)):
                    warnings.append(f"water can flow out sideways at {x},{y},{z}")
                    break
            if below is None and y - 1 > 0 or (below is not None and mc.is_air(below)):
                warnings.append(f"water can fall at {x},{y},{z}")
        elif b in ("sand", "red_sand", "gravel") or b.endswith("concrete_powder"):
            if below is not None and (mc.is_air(below) or mc.kind(below) in ("cross", "water")) or (below is None and y - 1 > 0):
                unsupported.append(f"falling {b} over nothing at {x},{y},{z}")
    if unsupported:
        problems.append(f"{len(unsupported)} unsupported blocks, e.g. {unsupported[:8]}")
    return problems, warnings


def write(grid, path):
    """Writes the template; layer 0 is the template's bottom (the ground layer is layer grid.G)."""
    used = {}
    palette = nbt.List(nbt.COMPOUND)
    blocks = nbt.List(nbt.COMPOUND)
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
        blocks.append({"pos": nbt.List(nbt.INT, [nbt.Int(x), nbt.Int(yi), nbt.Int(z)]), "state": nbt.Int(idx)})
    root = {
        "DataVersion": nbt.Int(DATA_VERSION),
        "size": nbt.List(nbt.INT, [nbt.Int(grid.W), nbt.Int(grid.H), nbt.Int(grid.D)]),
        "palette": palette,
        "blocks": blocks,
        "entities": nbt.List(nbt.END),
    }
    nbt.write_file(path, root)
    return len(blocks), len(palette)
