"""Local coordinates that turn with a door, so furniture and props are written once and face any way."""
from voxel import CCW, CW, OPP, STEP

# A standing banner's or sign's "rotation" (0-15) that faces each way.
ROT16 = {"south": 0, "west": 4, "north": 8, "east": 12}
# A standing skull's or head's: the game counts these from the other side (SkullBlock places a skull with the
# player's own yaw, BannerBlock with it turned round), so the same number faces the opposite way. Every skull was
# backwards until October 20, 2026 (the user: "the skulls ... are facing [the wrong] way").
SKULL_ROT16 = {"north": 0, "east": 4, "south": 8, "west": 12}


class Frame:
    """Local coordinates round (ox, oz): u to the right, v to the front (towards `front`, a compass direction), y up.

    Block states may name directions relative to the frame, filled in when placed: {front}, {back}, {left}, {right}
    (compass directions), {fb} and {lr} (the log axis along front-back and left-right), and {rot_front},
    {rot_back}, {rot_left}, {rot_right} (a standing banner's or sign's rotation facing that way), {skull_front},
    {skull_back}, {skull_left}, {skull_right} (a standing skull's)."""

    def __init__(self, grid, ox, oz, front):
        self.g = grid
        self.ox, self.oz = ox, oz
        self.front = front
        self.fx, self.fz = STEP[front]
        right = CW[front]
        self.rx, self.rz = STEP[right]
        self.dirs = {"front": front, "back": OPP[front], "right": right, "left": CCW[front]}
        along = "z" if self.fx == 0 else "x"
        self.names = dict(self.dirs)
        self.names["fb"] = along
        self.names["lr"] = "x" if along == "z" else "z"
        for k, d in self.dirs.items():
            self.names["rot_" + k] = str(ROT16[d])
            self.names["skull_" + k] = str(SKULL_ROT16[d])

    def xz(self, u, v):
        return self.ox + u * self.rx + v * self.fx, self.oz + u * self.rz + v * self.fz

    def d(self, name):
        """A frame direction ('front', 'left'...) as a compass direction."""
        return self.dirs[name]

    def state(self, s):
        if isinstance(s, str) and "{" in s:
            return s.format(**self.names)
        return s

    def set(self, u, y, v, mat, force=False):
        x, z = self.xz(u, v)
        self.g.set(x, y, z, self.state(mat), force=force)

    def get(self, u, y, v):
        x, z = self.xz(u, v)
        return self.g.get(x, y, z)

    def box(self, u1, y1, v1, u2, y2, v2, mat):
        for u in range(min(u1, u2), max(u1, u2) + 1):
            for y in range(min(y1, y2), max(y1, y2) + 1):
                for v in range(min(v1, v2), max(v1, v2) + 1):
                    self.set(u, y, v, mat)

    def sub(self, u, v, front=None):
        """A frame whose origin is at (u, v) of this one, facing `front` (a frame direction name; default the same)."""
        x, z = self.xz(u, v)
        return Frame(self.g, x, z, self.dirs[front] if front else self.front)
