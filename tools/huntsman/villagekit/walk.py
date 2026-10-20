"""Walking round a village before it's written: can a player get from the way in to every room (nothing in a doorway,
a passage or a lane, the user's "make sure chests don't block the way into other igloos"), and does everything face
the right way (a chest, a barrel, a smoker or a loom opens onto the room, not into a wall; a seat's back is behind
whoever sits on it)?

A player stands in a cell when it and the one over it are free (air, carpet, snow up to half a block, a plant, a
banner, an open way: doors and gates count as free, they open) and there's something to stand on under it (a block
of the village, or the world's ground). They walk to a neighbouring cell on the same level, one up (with room over
their head) or one or two down.
"""
from collections import deque

import mc
from voxel import STEP

WALK_THROUGH_THIN = ("_door", "_fence_gate", "_banner", "_button", "_pressure_plate", "_carpet", "_sign")
FRONT_OPENS = ("chest", "trapped_chest", "barrel", "smoker", "furnace", "blast_furnace", "loom", "lectern",
               "stonecutter", "grindstone")


def _free(g, x, y, z):
    if not g.inb(x, y, z):
        return y > 0
    s = g.get(x, y, z)
    if s is None:
        return y > 0
    k = mc.kind(s)
    b = mc.base(s)
    if k in ("air", "cross"):
        return True
    if k == "carpet":
        if b == "snow":
            return int(mc.props_of(s).get("layers", "1")) <= 4
        return True
    if k == "thin" and b.endswith(WALK_THROUGH_THIN):
        return True
    if b in ("frostbloom", "light"):
        return True
    return False


def _floor(g, x, y, z):
    """Something to stand on under (x, y, z)."""
    if not g.inb(x, y - 1, z):
        return False
    s = g.get(x, y - 1, z)
    if s is None:
        return y - 1 <= 0
    k = mc.kind(s)
    if k in ("air", "water", "lava", "cross"):
        return False
    if k == "carpet":
        return False  # a carpet is walked on in its own cell
    return True


def standable(g, x, y, z):
    return _free(g, x, y, z) and _free(g, x, y + 1, z) and (_floor(g, x, y, z) or _carpet_floor(g, x, y, z))


def _carpet_floor(g, x, y, z):
    s = g.get(x, y, z) if g.inb(x, y, z) else None
    return s is not None and mc.kind(s) == "carpet" and _floor(g, x, y, z)


def reach(g, start):
    """Every cell a player can stand in, starting from `start` (x, y, z): a set."""
    seen = set()
    if not standable(g, *start):
        return seen
    q = deque([start])
    seen.add(start)
    while q:
        x, y, z = q.popleft()
        for dx, dz in STEP.values():
            nx, nz = x + dx, z + dz
            for dy in (0, 1, -1, -2):
                ny = y + dy
                n = (nx, ny, nz)
                if n in seen:
                    continue
                if dy == 1 and not _free(g, x, y + 2, z):
                    continue
                if dy < 0 and not all(_free(g, nx, y + k, nz) for k in range(dy + 1, 2)):
                    continue
                if standable(g, nx, ny, nz):
                    seen.add(n)
                    q.append(n)
                    break
    return seen


def facing_problems(g, rooms=None):
    """Blocks facing the wrong way: something that opens (a chest, barrel, smoker...) with a block in front of it, and
    a seat (bottom stairs on a room's floor) with its back to the open room and its front against a wall."""
    out = []
    G = g.G
    for x, yi, z, s in g.blocks():
        b = mc.base(s)
        p = mc.props_of(s)
        y = yi - G
        if b in FRONT_OPENS:
            f = p.get("facing")
            if b == "barrel" and f in ("up", "down"):
                continue
            if b == "grindstone" and p.get("face") != "floor":
                continue
            if f not in STEP:
                continue
            dx, dz = STEP[f]
            if b == "barrel" or b.endswith("chest"):
                ok = _free(g, x + dx, y, z + dz) or _free(g, x + dx, y + 1, z + dz)
            else:
                ok = _free(g, x + dx, y, z + dz)
            if not ok:
                out.append(f"{b} at {x},{y},{z} faces {f} into a block")
        elif b.endswith("_stairs") and p.get("half") == "bottom" and rooms is not None and (x, y, z) in rooms:
            f = p.get("facing")
            dx, dz = STEP[f]
            back_free = _free(g, x + dx, y, z + dz)
            front_free = _free(g, x - dx, y, z - dz)
            if back_free and not front_free:
                out.append(f"seat at {x},{y},{z} has its back ({f}) to the room and its front against a wall")
    return out
