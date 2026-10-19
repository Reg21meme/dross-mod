"""
Previews of the Huntsman in the Alpha Moose's saddle (CLAUDE.md step 14): his riding model (rimemobs/huntsman_rider.py)
and the moose (rimemobs/alpha_moose.py) are two mobs, drawn together here the way the game puts them (he sits at the
moose's own position), each playing its own animation.

  python tools/rimefall/wildhunt.py          GIFs and a filmstrip page
  python tools/rimefall/wildhunt.py --quick  the filmstrip page only

Writes boss-previews/rimefall/mobs/huntsman_rider/wild_hunt_<rider animation>.gif and wild_hunt.png (run
tools/rimefall/mobs.py first: it makes the models' files; this only draws).
"""
import os
import sys

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "golems"))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402

from rimekit import mobkit, view as V  # noqa: E402
from rimemobs import alpha_moose, huntsman_rider  # noqa: E402

OUT = os.path.join(ROOT, "boss-previews", "rimefall", "mobs", "huntsman_rider")
# (the rider's animation, the moose's under him)
PAIRS = (("ride_gallop", "run"), ("ride_idle", "charge"), ("ride_thrust", "idle"), ("ride_shoot", "idle"),
         ("ride_rain", "idle"), ("ride_horn", "idle"), ("ride_bola", "walk"), ("ride_leap", "idle"))
YAW, EL, SCALE = 55.0, 10.0, 2.4


def build():
    mm, mt = mobkit.build_model(alpha_moose, verbose=False)
    rm, rt = mobkit.build_model(huntsman_rider, verbose=False)
    ma = alpha_moose.animations(mobkit.MobRig(mm, alpha_moose))
    ra = huntsman_rider.animations(mobkit.MobRig(rm, huntsman_rider))
    return (mm, mt["alpha_moose"]), (rm, rt["huntsman_rider"]), ma, ra


def frames(moose, rider, ma, ra, pair, step):
    rname, mname = pair
    a_r, a_m = ra[rname], ma[mname]
    length = a_r.length
    ts = np.arange(0.0, length + 1e-6, step)
    poses = [[a_m.sample(float(t % a_m.length) if a_m.loop is True else float(min(t, a_m.length))),
              a_r.sample(float(t))] for t in ts]
    boxes = [V.window(moose[0], YAW, EL, pose=p[0]) for p in poses]
    boxes += [V.window(rider[0], YAW, EL, pose=p[1]) for p in poses]
    b = np.array(boxes)
    bounds = (b[:, 0].min(), b[:, 1].min(), b[:, 2].max(), b[:, 3].max())
    ims = [V.draw(moose[0], moose[1], yaw=YAW, elevation=EL, scale=SCALE, bounds=bounds, pose=p, extra=[rider])
           for p in poses]
    return ts, ims


def main(argv):
    quick = "--quick" in argv
    moose, rider, ma, ra = build()
    os.makedirs(OUT, exist_ok=True)
    parts = []
    for pair in PAIRS:
        ts, ims = frames(moose, rider, ma, ra, pair, ra[pair[0]].length / 8.0)
        strip = [V.label(im.resize((im.width // 2, im.height // 2)), f"{t:.2f} s", 13) for t, im in zip(ts, ims)]
        parts.append((f"{pair[0]} (the moose: {pair[1]})", V.grid(strip[:8], 8)))
        if not quick:
            ts, ims = frames(moose, rider, ma, ra, pair, mobkit.GIF_STEP)
            ims[0].save(os.path.join(OUT, f"wild_hunt_{pair[0]}.gif"), save_all=True, append_images=ims[1:],
                        duration=int(mobkit.GIF_STEP * 1000), loop=0, optimize=True)
        print(f"  {pair[0]} + {pair[1]}")
    V.page("The Wild Hunt: the Huntsman riding his Alpha Moose",
           "His riding model and the moose are two mobs; in the game he sits at the moose's own position.", parts,
           os.path.join(OUT, "wild_hunt.png"))


if __name__ == "__main__":
    main(sys.argv[1:])
