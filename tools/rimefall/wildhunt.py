"""
Previews of the Huntsman in the Alpha Moose's saddle (CLAUDE.md step 14): his riding model (rimemobs/huntsman_rider.py)
and the moose (rimemobs/alpha_moose.py) are two mobs, drawn together here the way the game puts them (he sits at the
moose's own position), each playing its own animation; he rides along with the saddle (his carry_<name> animation for
the moose's), except while he leaps off.

  python tools/rimefall/wildhunt.py          GIFs and a filmstrip page
  python tools/rimefall/wildhunt.py --quick  the filmstrip page only

Writes boss-previews/rimefall/mobs/huntsman_rider/wild_hunt_<rider animation>.gif, wild_hunt_moose_<moose animation>.gif
(him sitting still while the moose makes a move), wild_hunt_split.gif (the split: he leaps off as it rears) and
wild_hunt.png (run tools/rimefall/mobs.py first: it makes the models' files; this only draws).
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
from rimemobs import alpha_moose, huntsman, huntsman_rider  # noqa: E402

OUT = os.path.join(ROOT, "boss-previews", "rimefall", "mobs", "huntsman_rider")
# (the GIF's name, the rider's animation, the moose's under him, the moose's start in seconds)
PAIRS = (("ride_gallop", "ride_gallop", "run", 0.0), ("ride_idle", "ride_idle", "charge", 0.0),
         ("ride_thrust", "ride_thrust", "idle", 0.0), ("ride_shoot", "ride_shoot", "run", 0.0),
         ("ride_rain", "ride_rain", "idle", 0.0), ("ride_horn", "ride_horn", "idle", 0.0),
         ("ride_bola", "ride_bola", "walk", 0.0),
         ("split", "ride_leap", "rear", 0.0),     # (together, as in the game: his leap has the rear's saddle in it)
         ("moose_walk", "ride_idle", "walk", 0.0), ("moose_ram", "ride_idle", "ram", 0.0),
         ("moose_back_kick", "ride_idle", "back_kick", 0.0), ("moose_antler_toss", "ride_idle", "antler_toss", 0.0),
         ("moose_stomp", "ride_idle", "stomp", 0.0), ("moose_bellow", "ride_idle", "bellow", 0.0),
         ("moose_crack_stamp", "ride_idle", "crack_stamp", 0.0), ("moose_eat", "ride_idle", "eat", 0.0))
YAW, EL, SCALE = 55.0, 10.0, 2.4


def build():
    mm, mt = mobkit.build_model(alpha_moose, verbose=False)
    rm, rt = mobkit.build_model(huntsman_rider, verbose=False)
    fm, ft = mobkit.build_model(huntsman, verbose=False)
    ma = alpha_moose.animations(mobkit.MobRig(mm, alpha_moose))
    rrig = mobkit.MobRig(rm, huntsman_rider)
    carry = huntsman_rider.carries(rrig, (mm, ma))
    ra = {name: (huntsman_rider.ride_leap(rrig, carry["carry_rear"]) if name == "ride_leap"
                 else getattr(huntsman_rider, name)(rrig)) for name in {p[1] for p in PAIRS}}
    ra.update(carry)
    frig = mobkit.MobRig(fm, huntsman)
    fa = {"land": huntsman.land(frig), "idle": huntsman.idle(frig)}
    return (mm, mt["alpha_moose"]), (rm, rt["huntsman_rider"]), (fm, ft["huntsman"]), ma, ra, fa


def length(ra, ma, pair):
    _gif, rname, mname, delay = pair
    a_r, a_m = ra[rname], ma[mname]
    if rname == "ride_idle" and a_m.loop is not True:
        return a_m.length + 0.3                # (him sitting still: as long as the moose's move)
    return max(a_r.length, delay + (a_m.length if a_m.loop is not True else 0.0))


def frames(moose, rider, foot, ma, ra, fa, pair, step):
    _gif, rname, mname, delay = pair
    a_m = ma[mname]
    ts = np.arange(0.0, length(ra, ma, pair) + 1e-6, step)
    leap_end = ra["ride_leap"].length

    def moose_at(t):
        t = max(0.0, t - delay)
        return float(t % a_m.length) if a_m.loop is True else float(min(t, a_m.length))

    shots = []                               # (the other model drawn, the poses)
    for t in ts:
        mp = a_m.sample(moose_at(t))
        if rname == "ride_leap" and t > leap_end:
            # off the moose: the Huntsman on foot where the leap put him, out of his landing crouch
            tl = t - leap_end
            fp = dict(fa["land"].sample(tl) if tl <= fa["land"].length
                      else fa["idle"].sample(tl - fa["land"].length))
            rot, pos, scl = fp.get("root", ((0.0, 0.0, 0.0), (0.0, 0.0, 0.0), (1.0, 1.0, 1.0)))
            fp["root"] = (rot, (pos[0] + huntsman_rider.LEAP_OUT, pos[1], pos[2]), scl)
            shots.append((foot, [mp, fp]))
        else:
            shots.append((rider, [mp, _rider_pose(ra, mname, rname, moose_at(t), t)]))
    boxes = [V.window(moose[0], YAW, EL, pose=p[0]) for _o, p in shots]
    boxes += [V.window(o[0], YAW, EL, pose=p[1]) for o, p in shots]
    b = np.array(boxes)
    bounds = (b[:, 0].min(), b[:, 1].min(), b[:, 2].max(), b[:, 3].max())
    ims = [V.draw(moose[0], moose[1], yaw=YAW, elevation=EL, scale=SCALE, bounds=bounds, pose=p, extra=[o])
           for o, p in shots]
    return ts, ims


def _rider_pose(ra, mname, rname, tm, t):
    """His pose: his own animation, carried by the saddle (the moose's carry from its own start), except while he leaps
    off."""
    pose = dict(ra[rname].sample(float(t)))
    if rname != "ride_leap":
        pose[huntsman_rider.CARRY] = ra[f"carry_{mname}"].sample(tm)[huntsman_rider.CARRY]
    return pose


def main(argv):
    quick = "--quick" in argv
    moose, rider, foot, ma, ra, fa = build()
    os.makedirs(OUT, exist_ok=True)
    parts = []
    for old in os.listdir(OUT) if not quick else ():
        if old.startswith("wild_hunt_") and old.endswith(".gif"):
            os.remove(os.path.join(OUT, old))
    for pair in PAIRS:
        gif, rname, mname, delay = pair
        ts, ims = frames(moose, rider, foot, ma, ra, fa, pair, length(ra, ma, pair) / 8.0)
        strip = [V.label(im.resize((im.width // 2, im.height // 2)), f"{t:.2f} s", 13) for t, im in zip(ts, ims)]
        title = (f"the split: {rname} with the moose's {mname}" if gif == "split"
                 else f"{rname} (the moose: {mname})")
        parts.append((title, V.grid(strip[:8], 8)))
        if not quick:
            ts, ims = frames(moose, rider, foot, ma, ra, fa, pair, mobkit.GIF_STEP)
            ims[0].save(os.path.join(OUT, f"wild_hunt_{gif}.gif"), save_all=True, append_images=ims[1:],
                        duration=int(mobkit.GIF_STEP * 1000), loop=0, optimize=True)
        print(f"  {gif}: {rname} + {mname}")
    V.page("The Wild Hunt: the Huntsman riding his Alpha Moose",
           "His riding model and the moose are two mobs; in the game he sits at the moose's own position.", parts,
           os.path.join(OUT, "wild_hunt.png"))


if __name__ == "__main__":
    main(sys.argv[1:])
