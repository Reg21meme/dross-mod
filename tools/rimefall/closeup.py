"""
Close-up previews for checking a candidate's details (not part of the build): one variant from a few angles, zoomed.

    python tools/rimefall/closeup.py <out.png> <label> [yaw elevation scale] ...

For example `closeup.py head.png 7B 300 10 9 330 25 9` draws No. 7B twice, from yaw 300 and 330, 9 pixels a unit.
"""
import sys

import build  # noqa: E402  (sets up the paths)
from rimekit import view as V  # noqa: E402


def main(argv):
    out, label = argv[0], argv[1].upper()
    nums = [float(a) for a in argv[2:]] or [30.0, 12.0, 6.0]
    entry = next(e for e in build.designs() if e.label == label)
    b = build.build_model(entry, verbose=False)
    m, tex, size, placed, bv = b
    extra = [bv] if bv else []
    ims = []
    for k in range(0, len(nums), 3):
        yaw, el, sc = nums[k:k + 3]
        ims.append(V.label(V.draw(m, tex, yaw=yaw, elevation=el, scale=sc, extra=extra, figure=False),
                           f"yaw {yaw:.0f}", 16))
    V.grid(ims, min(3, len(ims))).save(out)


if __name__ == "__main__":
    main(sys.argv[1:])
