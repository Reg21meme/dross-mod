"""
Lists the face pairs that would still flicker (z-fighting) after the fixer, for one variant (not part of the build):

    python tools/rimefall/zcheck.py <label>
"""
import sys

import build  # noqa: E402  (sets up the paths)
from kit import geo  # noqa: E402
from rimekit import shapes, zfight  # noqa: E402


def main(argv):
    label = argv[0].upper()
    e = next(x for x in build.designs() if x.label == label)
    m = geo.Model("check")
    s = shapes.Shape(m)
    e.mod.build(s, e.v)
    zfight.fix(m)
    for a, b in zfight.clashes(m):
        print(f"{a.mat:>10} {tuple(round(v, 1) for v in a.center())} {a.size} rot {a.rotation}  <->  "
              f"{b.mat:>10} {tuple(round(v, 1) for v in b.center())} {b.size} rot {b.rotation}")


if __name__ == "__main__":
    main(sys.argv[1:])
