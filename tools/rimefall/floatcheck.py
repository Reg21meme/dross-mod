"""
Lists the floating pieces of every candidate (or the ones named), without painting or drawing anything:

    python tools/rimefall/floatcheck.py [label ...]
"""
import sys

import build  # noqa: E402  (sets up the paths)
from kit import geo  # noqa: E402
from rimekit import connect, shapes  # noqa: E402


def main(argv):
    want = {a.upper() for a in argv}
    for e in build.designs():
        if want and e.label not in want:
            continue
        m = geo.Model("check")
        s = shapes.Shape(m)
        e.mod.build(s, e.v)
        found = connect.floating(m, getattr(e.mod, "FREE", ()))
        print(f"{e.label} {e.id}: {len(m.cubes())} boxes, {len(found)} floating group(s)")
        for mats, c, count in found:
            print(f"    {count} box(es) of {', '.join(mats)} near ({c[0]:.0f}, {c[1]:.0f}, {c[2]:.0f})")


if __name__ == "__main__":
    main(sys.argv[1:])
