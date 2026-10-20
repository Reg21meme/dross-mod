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
STRAIGHT = 0.985     # the bow arm's reach, of its full length: straight, a hair short of locked
NOCK_IN = 30.0       # while he nocks, the bow is held out in front of his chest: the aim turned this far to his right
NOCK_DOWN = 12.0     # and this far down (degrees)
FACE = -70.0         # how far his chest turns to face the target while he nocks (degrees: back to side-on as he draws)
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
                 volley=1, quiver=None, reach_turn=0.0, nock_down=NOCK_DOWN):
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
        return shoulder + hunt_ik.unit(d) * (H.UPPER_ARM + H.FOREARM) * h.k * STRAIGHT

    def _aim_dir(self, p, aim, mats=None):
        """Where the straight bow arm points at full draw: towards the spot that brings the drawn string to his chin."""
        h = self.h
        shoulder = h.at(p, "arm_l", mats=mats)
        anchor = h.point(p, "head", h.j["anchor"], mats)
        fist = hunt_ik.bow_fist(anchor, aim, self._bow_up(aim), 1.0, h.k)
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
            return 1.0 - smooth(u)
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
        put_end = self.away + PUT_AWAY * 0.5
        bow_out = False
        if t < self.start + GRAB * self.q:
            k = smooth((t - self.start) / (GRAB * self.q))
            h.arm(p, "l", _arc(rest_l, grip, k, 4.0), down=(0.0, -1.0, 0.0), key="bow", w_down=3.0 * k)
            if k > 0.9:
                props.append("bow_held")
        elif t < self.ready:
            # round in front of him, the arm straightening as it comes
            k = smooth((t - self.start - GRAB * self.q) / (BRING * self.q))
            nock = self._nock_dir(aim)
            up = self._bow_up(nock)
            fist = _lerp(grip, self._straight(p, nock, mats), k)
            h.arm(p, "l", fist, down=_nlerp(np.array([0.0, -1.0, 0.0]), -up, k), heading=nock, key="bow",
                  w_down=3.0 + 3.0 * k, w_head=2.0 * k)
            props.append("bow_held")
        elif t < self.away:
            props.append("bow_held")
            bow_out = True
        elif t < put_end:
            k = smooth((t - self.away) / (put_end - self.away))
            up = self._bow_up(aim)
            full = self._straight(p, self._aim_dir(p, aim, mats), mats)
            h.arm(p, "l", _arc(full, grip, k, 3.0), down=_nlerp(-up, np.array([0.0, -1.0, 0.0]), k), heading=aim,
                  key="bow", w_head=2.0 * (1.0 - k))
            if k < 0.92:
                props.append("bow_held")
        else:
            k = smooth((t - put_end) / (self.end - put_end))
            h.arm(p, "l", _lerp(grip, rest_l, k), down=(0.0, -1.0, 0.0), heading=(0.0, 0.0, -1.0), key="bow",
                  w_head=2.0 * k)
        # ---- the drawing arm (and the bow, when it's out)
        if bow_out:
            d, facing = self._bow_dir(p, aim, idx, phase, u, mats)
            fist = self._straight(p, d, mats)
            if phase in ("draw", "hold"):
                draw = H.DRAW * (smooth(u) if phase == "draw" else 1.0)
                h.bow(p, facing, draw, nocked=True, fist=fist, nock_prop=self.nock_prop)
                props.append(self.nock_prop)
                return props
            h.bow(p, facing, 0.0, nocked=False, draw_hand=False, fist=fist)
        else:
            h.strings(p, 0.0)
        up = self._bow_up(aim)
        if t >= self.away and idx == len(self.plan) - 1 and phase in ("after",):
            # back to the spear: the hand where it was after the last shot, easing back to its rest (the angles
            # blended down to nothing, so the last frame is his standing pose)
            k = smooth((t - self.away) / (self.end - self.away))
            h.arm(p, "r", self._after(p, aim, up), down=-up, heading=aim, key="draw")
            for b in ("arm_r", "forearm_r", "hand_r"):
                r = p.get3(b)[0]
                p.get3(b)[0] = [v * (1.0 - k) for v in r]
            return props
        mats = h.mats(p)
        quiver = self._quiver(p, mats)
        if phase == "wait":
            if idx > 0:
                h.arm(p, "r", self._after(p, aim, up), down=-up, heading=aim, key="draw")
            return props
        if phase == "to_quiver":
            start = rest_r if idx == 0 else self._after(p, aim, up)
            h.arm(p, "r", _arc(start, quiver, smooth(u), 3.0), down=(0.0, -1.0, 0.0), key="draw", w_down=3.0)
            if u > 0.85:
                props.append(self.hand_prop)
        elif phase == "to_string":
            mid, fwd = self._string_mid(p, mats)
            k = smooth(u)
            bup = self._bow_up(fwd)
            # (out in front of him on the way, so his arm doesn't cut across his chest)
            way = _arc(quiver, mid, k, 8.0) + fwd * 6.0 * h.k * math.sin(math.pi * k)
            h.arm(p, "r", way, down=_nlerp(np.array([0.0, -1.0, 0.0]), -bup, k), heading=fwd,
                  key="draw", w_down=3.0 + 3.0 * k, w_head=2.0 * k * k)
            props.append(self.hand_prop)
        elif phase in ("follow", "after"):
            fo = smooth(min(1.0, u * 1.5)) if phase == "follow" else 1.0
            anchor = h.point(p, "head", h.j["anchor"])
            h.arm(p, "r", _lerp(anchor, self._after(p, aim, up), fo), down=-up, heading=aim, key="draw")
        return props

    def _after(self, p, aim, up):
        """Where the drawing hand flies to after a release: back past his face and a little out."""
        h = self.h
        anchor = h.point(p, "head", h.j["anchor"])
        side = np.cross(aim, up)
        return anchor - aim * 7.0 * h.k - side * 2.5 * h.k
