"""What's inside an igloo: bedrolls and beds, a fire or a lamp, racks, chests, trophies.

Each furnisher works in the dome's own frame (v towards the door, u to its right) and only puts things where the room
is (Massing.is_room), on the floor or against the wall, so it fits any size of dome and never breaks the shell.
"""
import math

import mc
from voxel import AIR

from .local import Frame

FURS = ("white_carpet", "light_gray_carpet", "brown_carpet", "gray_carpet")
BED_COLOURS = ("white", "light_gray", "brown", "gray")


def _free(m, x, y, z):
    s = m.g.get(x, y, z)
    return m.is_room(x, y, z) and (s is None or mc.is_air(s))


class Room:
    """A dome's room, for furnishing."""

    def __init__(self, m, d):
        self.m = m
        self.g = m.g
        self.d = d
        self.f = Frame(m.g, d.cx, d.cz, d.door or "south")
        self.floor = {}
        for x, y, z in d.room:
            if (x, z) not in self.floor or y < self.floor[(x, z)]:
                self.floor[(x, z)] = y
        self.y0 = min(self.floor.values()) if self.floor else 1
        self.used = set()

    def local(self, x, z):
        f = self.f
        dx, dz = x - f.ox, z - f.oz
        return dx * f.rx + dz * f.rz, dx * f.fx + dz * f.fz

    def cell(self, u, v):
        """(x, y, z) of the floor cell at (u, v), or None if that isn't room."""
        x, z = self.f.xz(u, v)
        y = self.floor.get((x, z))
        if y is None or y != self.y0:
            return None
        return x, y, z

    def free(self, u, v, dy=0):
        c = self.cell(u, v)
        if c is None or (c[0], c[2]) in self.used:
            return False
        return _free(self.m, c[0], c[1] + dy, c[2])

    def put(self, u, v, state, dy=0, mark=True):
        c = self.cell(u, v)
        if c is None:
            return False
        x, y, z = c
        if not _free(self.m, x, y + dy, z) and dy == 0 and (x, z) in self.used:
            return False
        self.g.set(x, y + dy, z, self.f.state(state), force=True)
        if mark:
            self.used.add((x, z))
        return True

    def wall_cells(self):
        """Floor cells against the wall (a neighbour isn't room), with the direction towards the wall, sorted by angle
        from the door (0 = by the door, 180 = the back)."""
        out = []
        for (x, z), y in self.floor.items():
            if y != self.y0:
                continue
            for name, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0))):
                if not self.m.is_room(x + dx, y, z + dz):
                    u, v = self.local(x, z)
                    ang = math.degrees(math.atan2(u, v)) % 360
                    out.append((ang, x, z, name))
                    break
        out.sort()
        return out

    def by_door(self, u, v):
        """True for the cells in the doorway's path (keep them clear)."""
        return abs(u) <= 0 and v >= 0

    # ---------------------------------------------------------------- pieces
    def rug(self, r=None, mats=FURS, centre=(0, 0)):
        r = (self.d.r - 2.2) if r is None else r
        cu, cv = centre
        for (x, z), y in self.floor.items():
            u, v = self.local(x, z)
            if y == self.y0 and math.hypot(u - cu, v - cv) <= r and _free(self.m, x, y, z):
                mat = mats[0] if math.hypot(u - cu, v - cv) <= r * 0.55 else mats[1 % len(mats)]
                self.g.set(x, y, z, mat, force=True)

    def bed(self, u, v, toward, colour="white"):
        """A bed whose head is at (u, v) and foot one step away from `toward` (a frame direction the head points)."""
        f = self.f
        du, dv = {"front": (0, 1), "back": (0, -1), "left": (-1, 0), "right": (1, 0)}[toward]
        if not (self.free(u, v) and self.free(u - du, v - dv)):
            return False
        d = f.d(toward)
        self.put(u, v, f"{colour}_bed[facing={d},part=head,occupied=false]")
        self.put(u - du, v - dv, f"{colour}_bed[facing={d},part=foot,occupied=false]")
        return True

    def bedroll(self, u, v, toward, fur="brown_carpet", pillow="white_carpet"):
        du, dv = {"front": (0, 1), "back": (0, -1), "left": (-1, 0), "right": (1, 0)}[toward]
        if not (self.free(u, v) and self.free(u - du, v - dv)):
            return False
        self.put(u, v, pillow)
        self.put(u - du, v - dv, fur)
        return True

    def against_back(self, items, spread=1.0):
        """Puts single blocks along the wall, from the back round both sides (alternating), skipping the doorway."""
        cells = [c for c in self.wall_cells() if 50 < c[0] < 310]
        cells.sort(key=lambda c: abs(180 - c[0]))
        i = 0
        placed = []
        for ang, x, z, wall in cells:
            if i >= len(items):
                break
            if (x, z) in self.used:
                continue
            u, v = self.local(x, z)
            state = items[i]
            if state is None:
                i += 1
                continue
            facing_in = {"north": "south", "south": "north", "east": "west", "west": "east"}[wall]
            from .local import ROT16
            s = state.replace("{in}", facing_in).replace("{wall}", wall).replace("{rot_in}", str(ROT16[facing_in]))
            if self.put(u, v, s):
                placed.append((x, z))
                i += 1
        return placed

    def lamp(self, kind="soul_lantern", hang=True):
        """A lantern hanging on a chain from the top of the dome (or standing in the middle)."""
        x, z = self.d.cx, self.d.cz
        ys = sorted(y for (xx, y, zz) in self.d.room if xx == x and zz == z)
        if not ys:
            return
        here = self.g.get(x, self.y0, z)
        if here is not None and "campfire" in here:
            return  # the fire lights it, and its smoke hole is overhead
        top = ys[-1]
        if hang and top - self.y0 >= 3:
            self.g.set(x, top, z, "chain[axis=y]", force=True)
            self.g.set(x, top - 1, z, f"{kind}[hanging=true]", force=True)
        else:
            self.g.set(x, self.y0, z, f"{kind}[hanging=false]", force=True)
            self.used.add((x, z))

    def fire(self, kind="campfire", stones="cobblestone_slab[type=bottom]", u=0, v=0):
        c = self.cell(u, v)
        if c is None:
            return
        x, y, z = c
        lit = "true"
        self.g.set(x, y, z, f"{kind}[facing=north,lit={lit},signal_fire=false]", force=True)
        self.used.add((x, z))
        # a smoke hole straight above it, through the whole shell
        ys = sorted(yy for (xx, yy, zz) in self.d.room if xx == x and zz == z)
        if ys:
            top = ys[-1] + 1
            for yy in range(top, top + 4):
                s = self.g.get(x, yy, z)
                if s is None or mc.is_air(s):
                    break
                self.g.set(x, yy, z, AIR, force=True)


# ---------------------------------------------------------------------------- whole rooms

def sleeping(m, d, beds=2, colour="white", fire=None, extras=("chest[facing={in},type=single]",
                                                             "barrel[facing=up,open=false]")):
    """Beds or bedrolls round the back wall, a fur rug, a lamp, a chest."""
    r = Room(m, d)
    back = -int(d.r - 1.5)
    rows = [(-1, back), (1, back), (-2, back + 1), (2, back + 1), (0, back)]
    n = 0
    for i, (u, v) in enumerate(rows):
        if n >= beds:
            break
        side = "back"
        if i % 2 == 0:
            ok = r.bed(u, v, side, colour) if n % 2 == 0 else r.bedroll(u, v, side)
        else:
            ok = r.bedroll(u, v, side) if n % 2 == 0 else r.bed(u, v, side, colour)
        if not ok:
            ok = r.bedroll(u, v, side)
        if ok:
            n += 1
    if fire:
        r.fire(fire)
    r.rug()
    r.against_back(list(extras))
    r.lamp("lantern" if not fire else "soul_lantern", hang=not fire)
    return r


def cookhouse(m, d):
    """A fire in the middle under its smoke hole, a stew pot, a smoker, barrels of meat."""
    r = Room(m, d)
    r.fire("campfire")
    r.put(1, 0, "cauldron") if r.free(1, 0) else None
    r.against_back(["smoker[facing={in},lit=false]", "barrel[facing=up,open=false]", "barrel[facing=up,open=false]",
                    "brown_carpet", "spruce_stairs[facing={wall},half=bottom]", "spruce_stairs[facing={wall},half=bottom]"])
    return r


def workshop(m, d):
    """A hunter's workshop: fletching table, grindstone, spears (lightning rods) leaning on the wall, a chest."""
    r = Room(m, d)
    r.against_back(["fletching_table", "grindstone[face=floor,facing={in}]", "crafting_table",
                    "lightning_rod[facing=up]", "lightning_rod[facing=up]", "chest[facing={in},type=single]",
                    "loom[facing={in}]", "barrel[facing=up,open=false]"])
    r.rug(mats=("brown_carpet", "light_gray_carpet"))
    r.lamp("lantern")
    return r


def trophy(m, d):
    """Skulls on the wall, furs, a bed of hides, a lamp."""
    r = Room(m, d)
    r.bed(0, -int(d.r - 1.5), "back", "gray") or r.bedroll(0, -int(d.r - 1.5), "back")
    r.rug(mats=("white_carpet", "brown_carpet"))
    placed = r.against_back(["skeleton_skull[rotation={rot_in}]", "barrel[facing=up,open=false]",
                             "skeleton_skull[rotation={rot_in}]", "chest[facing={in},type=single]"])
    r.lamp("soul_lantern")
    return r


def storage(m, d):
    r = Room(m, d)
    r.against_back(["barrel[facing=up,open=false]", "barrel[facing=up,open=false]", "chest[facing={in},type=single]",
                    "hay_block[axis=y]", "barrel[facing=up,open=false]", "chest[facing={in},type=single]",
                    "hay_block[axis=y]", "barrel[facing=up,open=false]"])
    r.lamp("soul_lantern")
    return r


def hall(m, d, benches=True):
    """A meeting hall: a big fire in the middle, log benches round it, banners on the wall."""
    r = Room(m, d)
    r.fire("campfire")
    ring = d.r * 0.45
    for k in range(10):
        a = 2 * math.pi * k / 10
        u, v = int(round(math.sin(a) * ring)), int(round(math.cos(a) * ring))
        if v >= ring - 0.5 and abs(u) <= 1:
            continue
        if r.free(u, v):
            ax = "{lr}" if abs(math.cos(a)) > abs(math.sin(a)) else "{fb}"
            r.put(u, v, f"stripped_spruce_log[axis={ax}]")
    r.against_back(["chest[facing={in},type=single]", "barrel[facing=up,open=false]",
                    "skeleton_skull[rotation={rot_in}]", "barrel[facing=up,open=false]",
                    "lantern[hanging=false]", "lantern[hanging=false]"])
    return r
