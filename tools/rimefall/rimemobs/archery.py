"""
How the Huntsman shoots, the same on foot and in the saddle (the user's notes of October 19: first "the bow looks
unnatural ... I also want to see a physical arrow"; then "the arm holding the bow needs to be all the way out, then
his other arm grabs the arrow and shoots ... the arm holding the bow should be straight; same with the arrow rain:
straight bow-holding arm, then he grabs 3 arrows at a time to shoot"):

  he lets go of his spear (it stays standing where it was: in the snow beside him, or in its cup on the saddle),
  reaches over his left shoulder for the bow on his back and brings it round in front of him, his bow arm straight;
  for each shot he turns to face the target, the bow held out straight in front of his chest, and his right hand takes
  an arrow (three for a volley) from the quiver (at his hip; in the saddle, the one on the saddle behind his right
  leg) and nocks it; then he turns side-on, swinging the bow out to the aim (the arm still straight) as he draws the
  string to his chin (the string's two halves following his fingers, the arrow sliding back on the bow), holds, and
  looses: the string snaps back, the arrow is gone (the game fires a real one from there) and his hand flies back past
  his face; then the next shot; at the end the bow goes back over his shoulder and his hand back to the spear.

(A straight bow arm pointed at the target is out of his drawing hand's reach, for him as for any archer: so he nocks
with the bow in front of him, facing the target, and turns side-on as he draws.)

Archer(...).frame(p, t) poses both arms (and turns his chest) for time t (the body's stance is the animation's own)
and returns the props to show; `shots` are the release times, everything between is worked out.
"""
import math

import numpy as np

from kit.anims import smooth
from rimedesigns import d06_huntsman as H

from . import hunt_ik

GRAB = 0.28          # seconds to reach over the shoulder for the bow
BRING = 0.3          # to bring it round in front of him
TO_QUIVER = 0.2      # the right hand down to the quiver
TO_STRING = 0.24     # the arrow up to the string
DRAW_TIME = 0.32     # drawing to the chin
FOLLOW = 0.14        # the hand flying back after the release
PUT_AWAY = 0.5       # the bow back over the shoulder (the first part), the hands back to rest and the spear
BACK_GRIP = np.array([18.0, 162.0, 15.0])    # where the bow on his back is grabbed (riding frame): its upper limb
# The bow arm's reach, of its full length (the user, October 20: "fully straight in the rain, and nearly straight in
# the shot"; at 0.985 the elbow was still bent about 20 degrees: near full stretch the reach hardly changes with it)
STRAIGHT = 0.996     # the shots: the elbow about 10 degrees off straight
STRAIGHT_RAIN = 0.9995   # the arrow rain: straight (a few degrees off)
NOCK_IN = 48.0       # while he nocks, the bow is held out in front of his chest: the aim turned this far to his right
                     # (at 30 the string was out of his drawing hand's reach, and his forearm went into his chest)
NOCK_DOWN = 12.0     # and this far down (degrees)
FACE = -70.0         # how far his chest turns to face the target while he nocks (degrees: back to side-on as he draws)
DRAWN_FACE = 0.45    # of that, how much it keeps at full draw (an open stance: fully side-on his drawing arm had no
                     # room beside his hood)
# where his drawing elbow is bent towards after a release and on the way back to his spear (his chest's directions:
# out to his right, back (negative: forward) and down): side-on, "out to his right" runs along his arm, and alone it
# slid his elbow up into his hood, so mostly forward of his chest and down
AFTER_ELBOW = dict(out=0.5, back=-0.6, down=0.5)
# and his bow arm's elbow, reaching over his left shoulder for the bow on his back: out and up
REACH_BACK_ELBOW = dict(out=0.8, back=0.1, down=-0.6)
FAN = 3.0            # a volley's three arrows fan out this far from each other (degrees)
FAN_GAP = 3.0        # and sit this far apart on the string (design units: their fletchings clear of each other)


def _lerp(a, b, t):
    return np.asarray(a, dtype=float) * (1.0 - t) + np.asarray(b, dtype=float) * t


def _nlerp(a, b, t):
    v = _lerp(a, b, t)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else np.asarray(b, dtype=float)


def _arc(a, b, t, lift):
    """From a to b, lifted `lift` units in the middle (a hand's path doesn't run in a straight line)."""
    v = _lerp(a, b, t)
    v[1] += lift * math.sin(math.pi * t)
    return v


def volley_arrows(volley):
    """The arrows of one loosing: (offset along the bow's up, fan angle in degrees) for each."""
    if volley == 3:
        return [(-FAN_GAP, -FAN), (0.0, 0.0), (FAN_GAP, FAN)]
    return [(0.0, 0.0)]


class Archer:
    """One shooting animation: `shots` the release times, `aim(t)` the aim (a unit Bedrock direction), `start` when
    he lets go of the spear and reaches for the bow, `end` the animation's length (by then the bow is back on his back
    and the spear in his hand), `hold` how long each shot is held at full draw, `quick` scales the hand's moves;
    `face` how far his chest turns to the target to nock (degrees), `volley` arrows a loosing (1 or 3), `quiver(p,
    mats)` where his hand takes arrows from (default: the quiver at his hip), `reach_turn` how far his chest turns to
    his right as he reaches for it (degrees: the saddle's quiver is behind his right leg), `nock_down` how far down the
    bow is held while he nocks (degrees; negative: up, clear of the moose's neck in front of the saddle)."""

    def __init__(self, rig, hands, shots, aim, start=0.0, end=None, hold=0.12, quick=1.0, drop=0.0, face=FACE,
                 volley=1, quiver=None, reach_turn=0.0, nock_down=NOCK_DOWN, straight=None):
        self.rig = rig
        self.h = hands
        self.shots = list(shots)
        self.aim = aim
        self.start = start
        self.hold = hold
        self.q = quick
        self.face = face
        self.volley = volley
        self.quiver = quiver
        self.reach_turn = reach_turn
        self.nock_down = nock_down
        self.straight = straight if straight is not None else (STRAIGHT_RAIN if volley == 3 else STRAIGHT)
        self.drop = np.array([0.0, drop, 0.0])
        self.ready = start + (GRAB + BRING) * quick
        self.end = end if end is not None else self.shots[-1] + FOLLOW + PUT_AWAY + 0.1
        self.away = self.end - PUT_AWAY - 0.05
        self.plan = []
        prev = start + 0.05
        for r in self.shots:
            full = r - hold
            nock = full - DRAW_TIME * quick
            at_q = nock - TO_STRING * quick
            go_q = max(prev, at_q - TO_QUIVER * quick)
            if go_q > at_q - 0.06:
                raise ValueError(f"the shot at {r} s comes too soon after the last")
            self.plan.append((go_q, at_q, nock, full, r))
            prev = r + FOLLOW
        if self.plan[0][1] < self.ready:
            raise ValueError(f"the first arrow comes up to the string ({self.plan[0][1]:.2f} s) before the bow is out "
                             f"({self.ready:.2f} s)")
        self.hand_prop = "arrow_hand3" if volley == 3 else "arrow_hand"
        self.nock_prop = "arrow_nock3" if volley == 3 else "arrow_nock"

    # -------------------------------------------------------------- where things are
    @staticmethod
    def _bow_up(aim):
        up = np.array([0.0, 1.0, 0.0])
        up_p = up - aim * np.dot(up, aim)
        return up_p / np.linalg.norm(up_p)

    def _grip(self, p, mats):
        return self.h.m.point(mats, "chest", (BACK_GRIP + self.drop) * self.h.k)

    def _straight(self, p, d, mats=None):
        """The bow fist at the end of a straight arm pointing along d."""
        h = self.h
        shoulder = h.at(p, "arm_l", mats=mats)
        return shoulder + hunt_ik.unit(d) * (H.UPPER_ARM + H.FOREARM) * h.k * self.straight

    def _aim_dir(self, p, aim, mats=None):
        """Where the straight bow arm points at full draw: towards the spot out along the aim from his chin where a
        full draw brings the string back to it, or, when his straight arm doesn't reach that far, the spot on the aim
        through his chin it does reach (he then draws a little less: so the arrow still runs past his jaw; until
        October 20 the bow fell short off that line and the string came back into his face)."""
        h = self.h
        shoulder = h.at(p, "arm_l", mats=mats)
        anchor = h.point(p, "head", h.j["anchor"], mats)
        full = (H.BOW_BEND + H.DRAW) * h.k
        reach = (H.UPPER_ARM + H.FOREARM) * h.k * self.straight
        a = hunt_ik.unit(aim)
        rel = anchor - shoulder
        b = float(np.dot(a, rel))
        c = float(np.dot(rel, rel)) - reach * reach
        disc = b * b - c
        s = -b + math.sqrt(disc) if disc >= 0.0 else full
        fist = anchor + a * min(full, max(s, 0.3 * full))
        return hunt_ik.unit(fist - shoulder)

    def _nock_dir(self, aim):
        """Where the straight bow arm points while he nocks: forward of him (the aim, levelled), turned in to his right
        and a little down, so the bow is out in front of his chest."""
        up = np.array([0.0, 1.0, 0.0])
        flat = np.array([aim[0], 0.0, aim[2]])
        flat = flat / np.linalg.norm(flat) if np.linalg.norm(flat) > 1e-6 else np.array([0.0, 0.0, -1.0])
        right = np.cross(up, flat)
        a, b = math.radians(NOCK_IN), math.radians(self.nock_down)
        d = flat * math.cos(a) + right * math.sin(a)
        return hunt_ik.unit(d * math.cos(b) - up * math.sin(b))

    def _in_reach(self, p, target, mats=None, side="l"):
        """The target pulled in towards his shoulder if it's past a straight arm's reach (the bow's ways out and back
        bulge out from a straight arm's end)."""
        h = self.h
        shoulder = h.at(p, f"arm_{side}", mats=mats)
        v = np.asarray(target, dtype=float) - shoulder
        reach = (H.UPPER_ARM + H.FOREARM) * h.k * self.straight
        d = float(np.linalg.norm(v))
        return shoulder + v * (reach / d) if d > reach else np.asarray(target, dtype=float)

    def _quiver(self, p, mats):
        if self.quiver is not None:
            return self.quiver(p, mats)
        return self.h.point(p, "quiver_hip", self.h.j["quiver_top"], mats)

    def _string_mid(self, p, mats=None):
        h = self.h
        mats = mats or h.mats(p)
        mid = h.m.point(mats, "bow_held", h.j["hand_l"] + np.array([0.0, 0.0, H.BOW_BEND * h.k]))
        return mid, h.direction(p, "bow_held", (0.0, 0.0, -1.0), mats)

    def _shot(self, t):
        """(index, phase, u): which shot the right hand is busy with, what it's doing and how far along (0..1)."""
        for i, (go_q, at_q, nock, full, r) in enumerate(self.plan):
            nxt = self.plan[i + 1][0] if i + 1 < len(self.plan) else 1e9
            if t < go_q:
                return i, "wait", 0.0
            if t < at_q:
                return i, "to_quiver", (t - go_q) / (at_q - go_q)
            if t < nock:
                return i, "to_string", (t - at_q) / (nock - at_q)
            if t < full:
                return i, "draw", (t - nock) / (full - nock)
            if t < r:
                return i, "hold", (t - full) / max(r - full, 1e-6)
            if t < r + FOLLOW:
                return i, "follow", (t - r) / FOLLOW
            if t < nxt:
                return i, "after", 0.0
        return len(self.plan) - 1, "after", 0.0

    def _facing(self, t, idx, phase, u):
        """How far he's turned to face the target (1) or stands side-on to shoot (0): he takes the arrow from the
        quiver as he stands, turns to the target as he brings it to the bow, and side-on again as he draws."""
        if t < self.ready or t >= self.away:
            return 0.0
        if phase == "to_string":
            return smooth(u)
        if phase == "draw":
            return 1.0 - (1.0 - DRAWN_FACE) * smooth(u)
        if phase == "hold":
            return DRAWN_FACE
        if phase == "follow":
            return DRAWN_FACE * (1.0 - smooth(u))
        return 0.0

    def _bow_dir(self, p, aim, idx, phase, u, mats):
        """(where the straight bow arm points, which way the bow faces) for this moment while the bow is out."""
        nock = self._nock_dir(aim)
        full = self._aim_dir(p, aim, mats)
        if phase in ("wait", "to_string") or (phase == "to_quiver" and idx == 0):
            return nock, nock
        if phase == "to_quiver":
            k = smooth(u)
            return _nlerp(full, nock, k), _nlerp(aim, nock, k)
        if phase == "draw":
            k = smooth(u)
            return _nlerp(nock, full, k), _nlerp(nock, aim, k)
        return full, aim

    # -------------------------------------------------------------- a frame
    def frame(self, p, t):
        """Poses both arms (and the bow's string and arrows) for time t; returns the props to show."""
        h = self.h
        props = []
        if t < self.start or t >= self.end:
            return props
        props.append("spear_free")
        aim = hunt_ik.unit(self.aim(t))
        idx, phase, u = self._shot(t)
        f = self._facing(t, idx, phase, u)
        if phase == "to_quiver" and self.reach_turn and self.ready <= t < self.away:
            f -= self.reach_turn / self.face * math.sin(math.pi * u)     # (turned the other way: towards the quiver)
        if abs(f) > 1e-4:
            # his chest turns to the target to nock, his head staying on it
            p.rot("chest", 0.0, self.face * f, 0.0)
            p.rot("head", 0.0, -self.face * f, 0.0)
        mats = h.mats(p)
        rest_l = h.point(p, "forearm_l", h.j["hand_l"], mats)
        rest_r = h.point(p, "forearm_r", h.j["hand_r"], mats)
        grip = self._grip(p, mats)
        # ---- the bow arm
        left = h.direction(p, "chest", (1.0, 0.0, 0.0), mats)
        put_end = self.away + PUT_AWAY * 0.5
        bow_out = False
        if t < self.start + GRAB * self.q:
            k = smooth((t - self.start) / (GRAB * self.q))
            target = _arc(rest_l, grip, k, 9.0) + left * 4.0 * h.k * math.sin(math.pi * k)   # (up over his pauldron)
            h.arm(p, "l", target, down=(0.0, -1.0, 0.0), key="bow", w_down=3.0 * k,
                  elbow=h.elbow_out(p, "l", target, **REACH_BACK_ELBOW, mats=mats))
            if k > 0.9:
                props.append("bow_held")
        elif t < self.ready:
            # round in front of him, the arm straightening as it comes
            k = smooth((t - self.start - GRAB * self.q) / (BRING * self.q))
            nock = self._nock_dir(aim)
            up = self._bow_up(nock)
            fist = self._in_reach(p, _lerp(grip, self._straight(p, nock, mats), k)
                                  + left * 4.0 * h.k * math.sin(math.pi * k), mats)
            h.arm(p, "l", fist, down=_nlerp(np.array([0.0, -1.0, 0.0]), -up, k), heading=nock, key="bow",
                  w_down=3.0 + 3.0 * k, w_head=2.0 * k,
                  elbow=h.elbow_out(p, "l", fist, **REACH_BACK_ELBOW, mats=mats) if k < 0.7 else None)
            props.append("bow_held")
        elif t < self.away:
            props.append("bow_held")
            bow_out = True
        elif t < put_end:
            k = smooth((t - self.away) / (put_end - self.away))
            up = self._bow_up(aim)
            full = self._straight(p, self._aim_dir(p, aim, mats), mats)
            target = self._in_reach(p, _arc(full, grip, k, 9.0) + left * 4.0 * h.k * math.sin(math.pi * k), mats)
            h.arm(p, "l", target, down=_nlerp(-up, np.array([0.0, -1.0, 0.0]), k), heading=aim,
                  key="bow", w_head=2.0 * (1.0 - k),
                  elbow=h.elbow_out(p, "l", target, **REACH_BACK_ELBOW, mats=mats) if k > 0.3 else None)
            if k < 0.92:
                props.append("bow_held")
        else:
            k = smooth((t - put_end) / (self.end - put_end))
            target = _lerp(grip, rest_l, k) + left * 5.0 * h.k * math.sin(math.pi * k)     # (down outside his arm)
            h.arm(p, "l", target, down=(0.0, -1.0, 0.0), heading=(0.0, 0.0, -1.0), key="bow",
                  w_head=2.0 * k, elbow=h.elbow_out(p, "l", target, out=0.9, back=0.1, down=0.3, mats=mats))
        # ---- the drawing arm (and the bow, when it's out)
        if bow_out:
            d, facing = self._bow_dir(p, aim, idx, phase, u, mats)
            fist = self._straight(p, d, mats)
            if phase in ("draw", "hold"):
                draw = H.DRAW * (smooth(u) if phase == "draw" else 1.0)
                h.bow(p, facing, draw, nocked=True, fist=fist, nock_prop=self.nock_prop,
                      stop=h.point(p, "head", h.j["anchor"]))
                props.append(self.nock_prop)
                return props
            h.bow(p, facing, 0.0, nocked=False, draw_hand=False, fist=fist)
        else:
            h.strings(p, 0.0)
        up = self._bow_up(aim)
        if t >= self.away and idx == len(self.plan) - 1 and phase in ("after",):
            # back to the spear: the hand from where it was after the last shot round his right side to its rest
            # (the IK, his elbow out), and the last of it the angles eased down to nothing, so the last frame is his
            # standing pose (until October 20 the angles alone were eased down, and his arm swept through his chest)
            k = smooth((t - self.away) / (self.end - self.away))
            right = h.direction(p, "chest", (-1.0, 0.0, 0.0))
            front = h.direction(p, "chest", (0.0, 0.0, -1.0))
            target = _lerp(self._after(p, aim, up), rest_r, k) + (right + front) * 4.0 * h.k * math.sin(math.pi * k)
            h.arm(p, "r", target, down=_nlerp(-up, np.array([0.0, -1.0, 0.0]), k), heading=_nlerp(aim, front, k),
                  key="draw", elbow=h.elbow_out(p, "r", target, **AFTER_ELBOW))
            w = smooth((k - 0.55) / 0.45)
            for b in ("arm_r", "forearm_r", "hand_r"):
                r = p.get3(b)[0]
                p.get3(b)[0] = [v * (1.0 - w) for v in r]
            return props
        mats = h.mats(p)
        quiver = self._quiver(p, mats)
        if phase == "wait":
            if idx > 0:
                target = self._after(p, aim, up)
                h.arm(p, "r", target, down=-up, heading=aim, key="draw",
                      elbow=h.elbow_out(p, "r", target, **AFTER_ELBOW))
            return props
        if phase == "to_quiver":
            start = rest_r if idx == 0 else self._after(p, aim, up)
            # (down in front of his chest and out to his right on the way, round it to the quiver at his hip)
            front = h.direction(p, "chest", (0.0, 0.0, -1.0), mats)
            right = h.direction(p, "chest", (-1.0, 0.0, 0.0), mats)
            target = _arc(start, quiver, smooth(u), 3.0) + (front * 14.0 + right * 4.0) * h.k * math.sin(math.pi * smooth(u))
            h.arm(p, "r", target, down=(0.0, -1.0, 0.0), key="draw", w_down=3.0,
                  elbow=h.elbow_out(p, "r", target, out=0.9, back=0.0, down=0.4, mats=mats))
            if u > 0.85:
                props.append(self.hand_prop)
        elif phase == "to_string":
            mid, fwd = self._string_mid(p, mats)
            k = smooth(u)
            bup = self._bow_up(fwd)
            # (out in front of him on the way, so his arm doesn't cut across his chest: forward of the bow's way and
            # of his chest's front, which side-on (the arrow rain) are not the same)
            front = h.direction(p, "chest", (0.0, 0.0, -1.0), mats)
            way = _arc(quiver, mid, k, 8.0) + (fwd * 10.0 + front * 14.0) * h.k * math.sin(math.pi * k)
            h.arm(p, "r", way, down=_nlerp(np.array([0.0, -1.0, 0.0]), -bup, k), heading=fwd,
                  key="draw", w_down=2.0 + 1.0 * k, w_head=1.0 * k * k,
                  elbow=h.elbow_out(p, "r", way, out=0.6, back=-0.3, down=0.6, mats=mats))
            props.append(self.hand_prop)
        elif phase in ("follow", "after"):
            fo = smooth(min(1.0, u * 1.5)) if phase == "follow" else 1.0
            anchor = h.point(p, "head", h.j["anchor"])
            target = _lerp(anchor, self._after(p, aim, up), fo)
            h.arm(p, "r", target, down=-up, heading=aim, key="draw", elbow=h.elbow_out(p, "r", target, **AFTER_ELBOW))
        return props

    def _after(self, p, aim, up):
        """Where the drawing hand flies to after a release: back past his face and a little out."""
        h = self.h
        anchor = h.point(p, "head", h.j["anchor"])
        side = np.cross(aim, up)
        return anchor - aim * 7.0 * h.k - side * 2.5 * h.k
