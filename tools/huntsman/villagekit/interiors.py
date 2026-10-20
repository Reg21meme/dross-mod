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
        # cells kept clear for the ways in and through (doorways, passages joining other igloos): nothing goes there
        self.used = set(getattr(m, "reserved", {}).get(id(d), ()))

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
        dirs = (("north", (0, -1)), ("south", (0, 1)), ("east", (1, 0)), ("west", (-1, 0)))
        for (x, z), y in self.floor.items():
            if y != self.y0:
                continue
            # the wall it stands against: one with open room on the far side, so whatever stands here faces into
            # the room (a cell between two walls, or in a corner with no room in front, isn't one)
            best = None
            for name, (dx, dz) in dirs:
                if not self.m.is_room(x + dx, y, z + dz) and self.m.is_room(x - dx, y, z - dz)                         and _free(self.m, x - dx, y, z - dz):
                    # of the walls it could stand against, the one most behind it as seen from the middle
                    out_x, out_z = x - self.d.cx, z - self.d.cz
                    score = dx * out_x + dz * out_z
                    if best is None or score > best[0]:
                        best = (score, name)
            if best is not None:
                u, v = self.local(x, z)
                ang = math.degrees(math.atan2(u, v)) % 360
                out.append((ang, x, z, best[1]))
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

    def _pair_free(self, x, z, facing):
        """The head at (x, z) and the foot one step back from `facing` (compass): both free floor cells?"""
        from voxel import STEP
        dx, dz = STEP[facing]
        cells = ((x, z), (x - dx, z - dz))
        for cx, cz in cells:
            y = self.floor.get((cx, cz))
            if y is None or y != self.y0 or (cx, cz) in self.used or not _free(self.m, cx, y, cz):
                return None
        return cells

    def bed_against(self, x, z, wall, colour="white", roll=False):
        """A bed (or with `roll` a bedroll) with its head on the floor cell (x, z) against the wall that way (compass)
        and its foot out into the room."""
        cells = self._pair_free(x, z, wall)
        if cells is None:
            return False
        (hx, hz), (fx, fz) = cells
        if roll:
            self.g.set(hx, self.y0, hz, "white_carpet", force=True)
            self.g.set(fx, self.y0, fz, "brown_carpet", force=True)
        else:
            self.g.set(hx, self.y0, hz, f"{colour}_bed[facing={wall},part=head,occupied=false]", force=True)
            self.g.set(fx, self.y0, fz, f"{colour}_bed[facing={wall},part=foot,occupied=false]", force=True)
        self.used.update(cells)
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
                if "{in}" in state:
                    # what it opens onto stays clear
                    dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[facing_in]
                    self.used.add((x + dx, z + dz))
        return placed

    def tops(self):
        """{(x, z): the highest room cell over the floor in that column (the ceiling is just over it)}."""
        out = {}
        for x, y, z in self.d.room:
            if (x, z) in self.floor and y >= self.floor[(x, z)]:
                out[(x, z)] = max(out.get((x, z), y), y)
        return out

    def lamp(self, kind="lantern", hang=True, at=3):
        """A lantern hanging on a chain from the ceiling `at` blocks over the floor, a little off the middle (the
        skylight is over the middle: a lantern just under it would melt it), at the back of the room; or standing in
        the middle where nothing can hang."""
        here = self.g.get(self.d.cx, self.y0, self.d.cz)
        if here is not None and "campfire" in here:
            return  # the fire lights it, and its smoke hole is overhead
        tops = self.tops()
        best = None
        for (x, z), top in tops.items():
            if self.floor[(x, z)] != self.y0 or top - self.y0 < at:
                continue
            d = math.hypot(x - self.d.cx, z - self.d.cz)
            if not 1.4 <= d <= 2.6:
                continue
            above = self.g.get(x, top + 1, z)
            if above is None or mc.kind(above) != "full":
                continue
            u, v = self.local(x, z)
            score = (-v, top, -abs(d - 2.0))       # towards the back, under the highest ceiling
            if best is None or score > best[0]:
                best = (score, x, z, top)
        if hang and best is not None:
            _s, x, z, top = best
            for y in range(self.y0 + at + 1, top + 1):
                self.g.set(x, y, z, "chain[axis=y]", force=True)
            self.g.set(x, self.y0 + at, z, f"{kind}[hanging=true]", force=True)
            return
        x, z = self.d.cx, self.d.cz
        self.g.set(x, self.y0, z, f"{kind}[hanging=false]", force=True)
        self.used.add((x, z))

    def hang(self, state, n=3, ring=2.0, at=3):
        """Things hung from the ceiling on chains round the middle (joints of meat round a fire, bundles of dried
        meat): n of them about `ring` blocks out, `at` blocks over the floor."""
        tops = self.tops()
        spots = []
        for (x, z), top in tops.items():
            if self.floor[(x, z)] != self.y0 or top - self.y0 < at + 1:
                continue
            d = math.hypot(x - self.d.cx, z - self.d.cz)
            if abs(d - ring) > 0.6:
                continue
            above = self.g.get(x, top + 1, z)
            if above is None or mc.kind(above) != "full":
                continue
            if self.g.get(x, self.y0 + at, z) not in (None, AIR):
                continue
            spots.append((math.atan2(x - self.d.cx, z - self.d.cz), x, z, top))
        spots.sort()
        if not spots:
            return 0
        step = max(1, len(spots) // n)
        done = 0
        for ang, x, z, top in spots[::step][:n]:
            for y in range(self.y0 + at + 1, top + 1):
                self.g.set(x, y, z, "chain[axis=y]", force=True)
            self.g.set(x, self.y0 + at, z, self.f.state(state), force=True)
            done += 1
        return done

    def on_top(self, states, every=1):
        """Puts things on top of what stands along the wall (candles on the barrels, a second barrel on a barrel):
        states is {what's there (its block id): what goes on it}; every: each how many."""
        k = 0
        reserved = getattr(self.m, "reserved", {}).get(id(self.d), ())
        for (x, z), y in sorted(self.floor.items()):
            if y != self.y0:
                continue
            s = self.g.get(x, y, z)
            if s is None:
                continue
            b = mc.base(s)
            if b not in states:
                continue
            if not _free(self.m, x, y + 1, z) or (x, z) in reserved:
                continue
            k += 1
            if k % every:
                continue
            from .local import ROT16
            dx, dz = self.d.cx - x, self.d.cz - z
            inward = ("east" if dx > 0 else "west") if abs(dx) > abs(dz) else ("south" if dz > 0 else "north")
            state = states[b].replace("{rot_in}", str(ROT16[inward]))
            self.g.set(x, y + 1, z, self.f.state(state), force=True)

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
                                                             "barrel[facing=up,open=false]",
                                                             "white_wool", "barrel[facing=up,open=false]")):
    """Beds and a bedroll against the walls (from the back round, the head to the wall: two beds, then a bedroll), a
    fur rug, a lamp, a chest, barrels with candles on them, a bundle of furs."""
    r = Room(m, d)
    n = 0
    spots = sorted(r.wall_cells(), key=lambda c: abs(180.0 - c[0]))
    for _ang, x, z, wall in spots:
        if n >= beds:
            break
        if r.bed_against(x, z, wall, colour, roll=(n % 3 == 2)):
            n += 1
    if fire:
        r.fire(fire)
    r.rug()
    r.against_back(list(extras))
    r.on_top({"barrel": "candle[candles=3,lit=true]"})
    r.lamp("lantern" if not fire else "soul_lantern", hang=not fire)
    return r


def cookhouse(m, d):
    """A fire in the middle under its smoke hole, joints of meat hung round it to smoke, a stew pot, a smoker,
    barrels of meat, a butcher's stump."""
    r = Room(m, d)
    r.fire("campfire")
    r.put(1, 0, "cauldron") if r.free(1, 0) else None
    r.against_back(["smoker[facing={in},lit=false]", "barrel[facing=up,open=false]", "barrel[facing=up,open=false]",
                    "brown_carpet", "spruce_stairs[facing={wall},half=bottom]", "spruce_stairs[facing={wall},half=bottom]",
                    "stripped_spruce_log[axis=y]", "dross:meat_block[axis=y]", "dried_kelp_block"])
    r.on_top({"barrel": "dross:meat_block[axis=y]", "stripped_spruce_log": "spruce_pressure_plate"})
    r.hang("dross:meat_block[axis=y]", n=3, ring=2.2)
    return r


def workshop(m, d):
    """A hunter's workshop: fletching table, grindstone, an anvil, spears (lightning rods) leaning on the wall, a
    target, a chest."""
    r = Room(m, d)
    r.against_back(["fletching_table", "grindstone[face=floor,facing={in}]", "crafting_table",
                    "lightning_rod[facing=up]", "lightning_rod[facing=up]", "chest[facing={in},type=single]",
                    "loom[facing={in}]", "barrel[facing=up,open=false]", "chipped_anvil[facing={wall}]", "target",
                    "barrel[facing=up,open=false]"])
    r.on_top({"barrel": "candle[candles=2,lit=true]"})
    r.rug(mats=("brown_carpet", "light_gray_carpet"))
    r.lamp("lantern")
    return r


def trophy(m, d):
    """Skulls on posts round the wall, furs, a bed of hides, a lamp."""
    r = Room(m, d)
    r.bed(0, -int(d.r - 1.5), "back", "gray") or r.bedroll(0, -int(d.r - 1.5), "back")
    r.rug(mats=("white_carpet", "brown_carpet"))
    r.against_back(["stripped_spruce_log[axis=y]", "barrel[facing=up,open=false]", "stripped_spruce_log[axis=y]",
                    "chest[facing={in},type=single]", "stripped_spruce_log[axis=y]", "bone_block[axis=y]"])
    r.on_top({"stripped_spruce_log": "skeleton_skull[rotation={rot_in}]",
              "bone_block": "skeleton_skull[rotation={rot_in}]", "barrel": "candle[candles=3,lit=true]"})
    r.lamp("soul_lantern")
    return r


def storage(m, d):
    """The village's store: barrels (some stacked two high), chests, hay, joints of meat and bundles of dried meat,
    blocks of packed ice to keep it cold."""
    r = Room(m, d)
    r.against_back(["barrel[facing=up,open=false]", "barrel[facing=up,open=false]", "chest[facing={in},type=single]",
                    "hay_block[axis=y]", "dross:meat_block[axis=y]", "barrel[facing=up,open=false]",
                    "chest[facing={in},type=single]", "hay_block[axis=y]", "packed_ice", "dried_kelp_block",
                    "barrel[facing=up,open=false]", "dross:meat_block[axis=y]"])
    r.on_top({"barrel": "barrel[facing=up,open=false]", "hay_block": "dross:meat_block[axis=x]"}, every=2)
    r.lamp("soul_lantern")
    return r


def hall(m, d, benches=True):
    """A meeting hall: a big fire in the middle, log benches round it, banners on the wall, chests and barrels with
    candles, joints of meat hung from the roof."""
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
                    "white_banner[rotation={rot_in}]", "white_banner[rotation={rot_in}]",
                    "lantern[hanging=false]", "lantern[hanging=false]", "barrel[facing=up,open=false]",
                    "dross:roasted_meat_block[axis=y]"])
    r.on_top({"barrel": "candle[candles=4,lit=true]"})
    r.hang("dross:meat_block[axis=y]", n=2, ring=d.r * 0.7)
    return r
