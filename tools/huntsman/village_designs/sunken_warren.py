"""(Since October 20, 2026 the villages are built by warren.py, from the hand-designed igloo templates: it uses this
module's planning helpers, lining, floors, fires and decoration lists. This module's own build(), with its igloos
furnished by villagekit.interiors, is the version before that, kept for reference.)

The Sunken Warren: the hunters' village (the user's pick, October 19, 2026: "I like the sunken igloos and the warren
igloos, combine them into something"; then, after looking in-game: "I want the villages to be massive", 15 to 20
igloos, the Huntsman's 25 to 30; "make the village more interconnected as well, I like the ravines you have in
between"; "add life with more blocks and stuff"; a moose or two in a paddock, a Rimehorn herd in his).

Courtyards dug three blocks down into the snow, out of the wind (Sunken Hollow), each ringed by igloos sunk into its
rim with their doorways on its floor and a fire in its middle with a joint of meat roasting on a spit; sunken lanes
(the ravines) join the courtyards, in loops wherever two are near enough (each courtyard joins at least two others
where it can); and more igloos behind the rings, joined to them and to each other by covered snow passages (the
Warren), so the village is half a burrow. The first courtyard is the biggest: the meeting hall stands at its back, and
steps lead down into it from the south (the way in); the others have steps out too.

Every decoration (racks of meat, hides, sleds with their loads, woodpiles, barrels and crates, lean-tos, fishing
holes, totems, lantern posts, banners, skull and antler posts, bone piles, ice sculptures, Frostblooms, arches of
bone over the lanes...) is its own group, placed by the game with a 1 in 2 chance (villagekit.decor); the fires, the
igloos, the pens and their animals are always there.

    build(number, seed, igloos, huntsman=False) -> (grid, info, extras)

huntsman=True leaves room north of the courtyards for the Huntsman's lodge and stable (lodge.py), joined to the
nearest courtyard by a lane and steps up, and a herding corral for his Rimehorns: his village. A regular village
keeps a moose or two in a paddock.
"""
import math
import random
from itertools import combinations

import mc
from voxel import AIR, Mix

from villagekit import interiors, lighting, props
from villagekit.decor import Decor, YFrame
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village, facing_towards
from villagekit.writer import animal, banner

FLOOR = -2                     # where you stand in a courtyard, a lane or a sunken igloo (3 below the ground)
FLOOR_BLOCK = "snow_block"
# the dug spaces' walls: snow (until October 23, 2026 with patches of packed ice, which the user saw as "weird ice
# squares": "I don't like the ice squares")
LINING = Mix(("snow_block", 1))
# the floors: trodden snow, Hoarfrost and gravel (the user: no dirt, it "looks lame"; and no packed ice, as above)
LANE_FLOOR = Mix(("snow_block", 4), ("gravel", 2), ("dross:hoarfrost", 2), patch=0.4)
COURT_FLOOR = Mix(("snow_block", 6), ("dross:hoarfrost", 2), ("gravel", 1), patch=0.5)
PEN_FLOOR = Mix(("snow_block", 4), ("dross:hoarfrost", 3), ("gravel", 1), patch=0.5)

MAIN_R = 12.0                  # the first courtyard's radius
COURT_R = (9.0, 11.0)          # the others'
LANE_HALF = 1.6                # a lane is about 3 wide
COURT_GAP = 21.0               # between two courtyards' edges (room for their igloos and a lane)
LOOP_GAP = 36.0                # a loop lane joins two courtyards whose edges are at most this far apart
LOOP_ANGLE = 50.0              # degrees between two lanes leaving one courtyard, at least
RIM_OUT = 2.2                  # a ring igloo's middle this far outside its courtyard's edge
HALL_OUT = 3.0
IGLOO_R = (4.5, 5.0, 5.5)
HALL_R = 7.0
DOME_H = 1.25                  # a sunken igloo's height over its radius (it rises about 3-4 blocks over the ground)
PASSAGE_GAP = (3.0, 4.5)       # blocks of passage between an igloo's wall and the one it leads to
EXTRA_GAP = (1.5, 9.0)         # two igloos whose walls are this far apart may be joined too (the warren)
PASSAGE_R = 2.0
MAX_PASSAGES = 3               # covered passages out of one igloo, at most
OUTLIER_SHARE = 0.3            # about this share of the igloos sit behind the rings, joined by passages
STEPS_LEN = 4
MARGIN = 9                     # open ground round the village, for its decorations
NEAR_VILLAGE = 9.0             # the decorations on the snow stand within this of a courtyard's edge or an igloo
H = 22
G = 5

# the pens: a moose paddock in a regular village (1 or 2 grown moose: a cow with a calf near goes for players), a
# herding corral of Rimehorns in the Huntsman's (a bull, 4 or 5 cows, sometimes a calf). Outer sizes (fence to fence);
# fences hold them (1.5 high: they can't jump them); a Rimehorn is 3.4 wide, a moose 2.0.
PADDOCK = (15, 13)
CORRAL = (27, 25)
PEN_CLEAR = 3.0                # open ground between a pen and anything of the village
MOOSE_GROW = 0                 # (grown moose only)
RIMEHORN_CALF_AGE = -42000     # a calf: 35 minutes to grow up (RimefallMobTuning.RIMEHORN_GROW_TICKS)

KINDS = ("sleeping", "sleeping", "cook", "sleeping", "workshop", "trophy", "sleeping", "storage")

STYLES = (Style(rim_courses=0, bands=(3,), band="packed_ice"),
          Style(rim_courses=0, bands=(2, 4), band="packed_ice"),
          Style(rim_courses=0, bands=(), cap="packed_ice"))
HALL_STYLE = Style(rim_courses=0, bands=(3, 5), band="packed_ice", cap="blue_ice")
PASSAGE_STYLE = Style(rim_courses=0)

# the hunters' banners: a pale field, a brown border, a white skull or a cross of spears
CLOTHS = ((("bo", "brown"), ("sku", "white")), (("ts", "brown"), ("bs", "brown"), ("cr", "light_gray")),
          (("gra", "light_gray"), ("sku", "brown")), (("bts", "brown"), ("tts", "brown")))


def seg_dist(px, pz, a, b):
    (ax, az), (bx, bz) = a, b
    dx, dz = bx - ax, bz - az
    L = dx * dx + dz * dz
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((px - ax) * dx + (pz - az) * dz) / L))
    return math.hypot(px - (ax + t * dx), pz - (az + t * dz))


def seg_seg(a, b, c, d):
    if _crosses(a, b, c, d):
        return 0.0
    return min(seg_dist(*a, c, d), seg_dist(*b, c, d), seg_dist(*c, a, b), seg_dist(*d, a, b))


def _crosses(a, b, c, d):
    def side(p, q, r):
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
    d1, d2 = side(c, d, a), side(c, d, b)
    d3, d4 = side(a, b, c), side(a, b, d)
    return (d1 > 0) != (d2 > 0) and (d3 > 0) != (d4 > 0) and min(abs(d1), abs(d2), abs(d3), abs(d4)) > 1e-9


def rect_dist(rect, px, pz):
    x1, z1, x2, z2 = rect
    dx = max(x1 - px, 0.0, px - x2)
    dz = max(z1 - pz, 0.0, pz - z2)
    return math.hypot(dx, dz)


def rect_seg(rect, a, b):
    (ax, az), (bx, bz) = a, b
    n = int(math.hypot(bx - ax, bz - az)) + 2
    return min(rect_dist(rect, ax + (bx - ax) * i / n, az + (bz - az) * i / n) for i in range(n + 1))


# ---------------------------------------------------------------- the plan


class Court:
    def __init__(self, x, z, r, main=False):
        self.x, self.z, self.r, self.main = x, z, r, main
        self.steps = None


class Igloo:
    def __init__(self, x, z, r, court=None, parent=None, kind="sleeping", hall=False):
        self.x, self.z, self.r = x, z, r
        self.court = court
        self.parent = parent
        self.kind = kind
        self.hall = hall
        self.dome = None
        self.home = court
        self.links = 0


class Pen:
    def __init__(self, rect, gate, kind):
        self.rect = rect          # (x1, z1, x2, z2): the fence line, in plan coordinates
        self.gate = gate          # the side the gate is on: north, south, east or west
        self.kind = kind          # "paddock" (moose) or "corral" (Rimehorns)


class Plan:
    def __init__(self):
        self.courts = []
        self.lanes = []         # ((x1, z1), (x2, z2))
        self.loops = 0          # how many of the lanes close a loop
        self.igloos = []
        self.passages = []      # (igloo a, igloo b)
        self.steps = []         # (court, direction, (x1, z1), (x2, z2))
        self.lodge = None       # (x1, z1, x2, z2) kept for the Huntsman's lodge, and its lane
        self.pens = []


def _steps_band(court, direction):
    dx, dz = {"south": (0, 1), "north": (0, -1), "east": (1, 0), "west": (-1, 0)}[direction]
    a = (court.x + dx * (court.r - 1), court.z + dz * (court.r - 1))
    b = (court.x + dx * (court.r + STEPS_LEN + 1), court.z + dz * (court.r + STEPS_LEN + 1))
    return a, b


def courts_for(n):
    """How many courtyards a village of n igloos has (about four ring igloos each, a third of them behind)."""
    return 4 if n <= 16 else (5 if n <= 21 else (6 if n <= 27 else 7))


def plan(seed, n, huntsman=False):
    for attempt in range(600):
        rng = random.Random(seed * 7919 + attempt)
        p = _try_plan(rng, n, huntsman)
        if p is not None:
            return p, attempt
    raise SystemExit(f"no plan with {n} igloos for seed {seed}")


def _lane_ok(p, a, b, lane):
    """A lane between courtyards a and b: not through a third courtyard, not across another lane, and at a
    courtyard it shares with another lane, far enough round from it."""
    for c in p.courts:
        if c is a or c is b:
            continue
        if seg_dist(c.x, c.z, *lane) < c.r + 5:
            return False
    for other in p.lanes:
        shared = {(a.x, a.z), (b.x, b.z)} & {other[0], other[1]}
        if not shared:
            if seg_seg(lane[0], lane[1], other[0], other[1]) < 2 * LANE_HALF + 7:
                return False
            continue
        (sx, sz) = next(iter(shared))
        o_end = other[1] if other[0] == (sx, sz) else other[0]
        l_end = lane[1] if lane[0] == (sx, sz) else lane[0]
        a1 = math.atan2(o_end[1] - sz, o_end[0] - sx)
        a2 = math.atan2(l_end[1] - sz, l_end[0] - sx)
        diff = abs((a1 - a2 + math.pi) % (2 * math.pi) - math.pi)
        if math.degrees(diff) < LOOP_ANGLE:
            return False
    return True


def _try_plan(rng, n, huntsman):
    p = Plan()
    k = courts_for(n)
    p.courts.append(Court(0.0, 0.0, MAIN_R, main=True))
    joined = set()
    for _ in range(k - 1):
        best = None
        for _t in range(80):
            parent = rng.choice(p.courts)
            r = rng.uniform(*COURT_R)
            a = rng.uniform(-math.pi * 0.95, math.pi * 0.05) if not parent.main else rng.uniform(-math.pi, 0.0)
            d = parent.r + r + COURT_GAP + rng.uniform(0, 4)
            x, z = parent.x + math.cos(a) * d, parent.z + math.sin(a) * d
            if z > 4.0:
                continue  # the first courtyard stays the southernmost: the way in is there
            if any(math.hypot(x - c.x, z - c.z) < c.r + r + COURT_GAP - 0.5 for c in p.courts):
                continue
            c = Court(x, z, r)
            lane = ((parent.x, parent.z), (x, z))
            if not _lane_ok(p, parent, c, lane):
                continue
            # compact: close to the others
            score = sum(math.hypot(x - o.x, z - o.z) for o in p.courts) + rng.uniform(0, 10)
            if best is None or score < best[0]:
                best = (score, c, parent)
        if best is None:
            return None
        p.courts.append(best[1])
        p.lanes.append(((best[2].x, best[2].z), (best[1].x, best[1].z)))
        joined.add((id(best[2]), id(best[1])))
    # loops: the nearest pairs not yet joined, where a lane fits (the user's "more interconnected")
    pairs = []
    for a, b in combinations(p.courts, 2):
        if (id(a), id(b)) in joined or (id(b), id(a)) in joined:
            continue
        gap = math.hypot(a.x - b.x, a.z - b.z) - a.r - b.r
        if gap <= LOOP_GAP:
            pairs.append((gap, a, b))
    pairs.sort(key=lambda t: t[0])
    for gap, a, b in pairs:
        lane = ((a.x, a.z), (b.x, b.z))
        if _lane_ok(p, a, b, lane):
            p.lanes.append(lane)
            p.loops += 1
    if k >= 3 and p.loops == 0:
        return None
    cx = sum(c.x for c in p.courts) / len(p.courts)
    cz = sum(c.z for c in p.courts) / len(p.courts)
    # steps: south out of the first courtyard; out of each other one, the free way furthest from the middle
    p.steps.append((p.courts[0], "south") + _steps_band(p.courts[0], "south"))
    for c in p.courts[1:]:
        options = []
        for d, (dx, dz) in (("north", (0, -1)), ("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1))):
            band = _steps_band(c, d)
            if any(seg_seg(band[0], band[1], a, b) < LANE_HALF + 4 for a, b in p.lanes):
                continue
            if any(seg_dist(o.x, o.z, *band) < o.r + 3 for o in p.courts if o is not c):
                continue
            away = (c.x - cx) * dx + (c.z - cz) * dz
            options.append((away, d, band))
        if options:
            options.sort(reverse=True)
            _a, d, band = options[0]
            p.steps.append((c, d) + band)
    if huntsman:
        north = min(p.courts, key=lambda c: c.z - abs(c.x - cx) * 0.3)
        lw, ld = LODGE_SIZE
        z2 = min(c.z - c.r for c in p.courts) - 14
        x1 = north.x - lw / 2
        p.lodge = (x1, z2 - ld, x1 + lw, z2)
        lane = ((north.x, north.z), (north.x, z2 + 3.5))
        if any(seg_dist(o.x, o.z, *lane) < o.r + 3 for o in p.courts if o is not north):
            return None
        if any(seg_seg(lane[0], lane[1], a, b) < 2 * LANE_HALF + 7 for a, b in p.lanes
               if (north.x, north.z) not in (a, b)):
            return None
        p.lanes.append(lane)
        p.lodge_lane = lane
        p.lodge_court = north
    # the meeting hall at the back of the first courtyard (the way round from north that's free of lanes)
    hall = p.courts[0]
    placed_hall = None
    for off in (0, 25, -25, 50, -50, 75, -75):
        a = math.radians(180 + off)              # 180: north (angles from south, clockwise seen from above)
        ring = hall.r + HALL_OUT
        hx, hz = hall.x + math.sin(a) * ring, hall.z + math.cos(a) * ring
        if _fits(p, hx, hz, HALL_R, hall):
            placed_hall = Igloo(hx, hz, HALL_R, court=hall, kind="hall", hall=True)
            break
    if placed_hall is None:
        return None
    p.igloos.append(placed_hall)
    # the ring igloos
    for c in p.courts:
        start = rng.uniform(0, 360)
        step = rng.choice((2.0, 3.0))
        ang = 0.0
        while ang < 360:
            a = math.radians(start + ang)
            r = rng.choice(IGLOO_R)
            ring = c.r + RIM_OUT
            x, z = c.x + math.sin(a) * ring, c.z + math.cos(a) * ring
            if _fits(p, x, z, r, c):
                p.igloos.append(Igloo(x, z, r, court=c))
                ang += 25
            else:
                ang += step
    rim = len(p.igloos)
    want_out = max(n - rim, int(round(n * OUTLIER_SHARE)))
    # drop ring igloos (never the hall, at least two round each courtyard) to make room for the outliers
    drop = rim + want_out - n
    if drop > 0:
        ring_only = [i for i in p.igloos if not i.hall]
        rng.shuffle(ring_only)
        count = {id(c): sum(1 for i in ring_only if i.court is c) for c in p.courts}
        for i in ring_only:
            if drop <= 0:
                break
            if count[id(i.court)] <= 2:
                continue
            p.igloos.remove(i)
            count[id(i.court)] -= 1
            drop -= 1
    # the outliers, behind the ring igloos, joined by passages (and now and then one behind those)
    tries = 0
    while len(p.igloos) < n and tries < 900:
        tries += 1
        parent = rng.choice(p.igloos)
        if parent.hall and rng.random() < 0.6:
            continue
        if parent.links >= MAX_PASSAGES:
            continue
        c = parent.home
        a = math.atan2(parent.x - c.x, parent.z - c.z) + math.radians(rng.uniform(-40, 40))
        r = rng.choice(IGLOO_R)
        d = parent.r + r + rng.uniform(*PASSAGE_GAP)
        x, z = parent.x + math.sin(a) * d, parent.z + math.cos(a) * d
        if _fits(p, x, z, r, None, parent=parent):
            o = Igloo(x, z, r, parent=parent)
            o.home = c
            p.igloos.append(o)
            p.passages.append((parent, o))
            parent.links += 1
            o.links += 1
    if len(p.igloos) != n:
        return None
    # more passages: neighbours whose walls are near each other, where a straight passage fits (the warren)
    linked = {(id(a), id(b)) for a, b in p.passages} | {(id(b), id(a)) for a, b in p.passages}
    cands = []
    for a, b in combinations(p.igloos, 2):
        if (id(a), id(b)) in linked or a.hall or b.hall:
            continue
        gap = math.hypot(a.x - b.x, a.z - b.z) - a.r - b.r
        if EXTRA_GAP[0] <= gap <= EXTRA_GAP[1] and not (a.court is not None and a.court is b.court):
            cands.append((gap, a, b))
    cands.sort(key=lambda t: t[0])
    for gap, a, b in cands:
        if a.links >= MAX_PASSAGES or b.links >= MAX_PASSAGES:
            continue
        if _passage_ok(p, a, b):
            p.passages.append((a, b))
            a.links += 1
            b.links += 1
    # the pens
    if huntsman:
        pen = _place_pen(p, rng, CORRAL, "corral")
    else:
        pen = _place_pen(p, rng, PADDOCK, "paddock")
    if pen is None:
        return None
    p.pens.append(pen)
    # room kinds: the hall, a cookhouse by each courtyard, then the rest by turns
    kinds = list(KINDS)
    rng.shuffle(kinds)
    cooks = set()
    for i, ig in enumerate(p.igloos):
        if ig.hall:
            continue
        home = ig.home
        if home is not None and id(home) not in cooks and ig.court is not None:
            ig.kind = "cook"
            cooks.add(id(home))
        else:
            ig.kind = kinds[i % len(kinds)] if kinds[i % len(kinds)] != "cook" else "sleeping"
    return p


LODGE_SIZE = (55, 51)


def _passage_ok(p, a, b):
    """A straight covered passage from igloo a's wall to igloo b's: clear of the courtyards, the lanes, the steps,
    the other igloos and passages, and the lodge."""
    L = math.hypot(b.x - a.x, b.z - a.z) or 1.0
    ux, uz = (b.x - a.x) / L, (b.z - a.z) / L
    wa = (a.x + ux * a.r, a.z + uz * a.r)
    wb = (b.x - ux * b.r, b.z - uz * b.r)
    for o in p.igloos:
        if o is a or o is b:
            continue
        if seg_dist(o.x, o.z, wa, wb) < o.r + PASSAGE_R + 1.0:
            return False
    for c in p.courts:
        if seg_dist(c.x, c.z, wa, wb) < c.r + PASSAGE_R + 1.0:
            return False
    for l1, l2 in p.lanes:
        if seg_seg(wa, wb, l1, l2) < PASSAGE_R + LANE_HALF + 1.5:
            return False
    for _c, _d, s1, s2 in p.steps:
        dx, dz = s2[0] - s1[0], s2[1] - s1[1]
        Ls = math.hypot(dx, dz) or 1.0
        far = (s2[0] + dx / Ls * 9, s2[1] + dz / Ls * 9)
        if seg_seg(wa, wb, s1, far) < PASSAGE_R + 3.5:
            return False
    for q1, q2 in p.passages:
        if a in (q1, q2) or b in (q1, q2):
            continue
        if seg_seg(wa, wb, (q1.x, q1.z), (q2.x, q2.z)) < 2 * PASSAGE_R + 1.5:
            return False
    if p.lodge is not None and rect_seg(p.lodge, wa, wb) < PASSAGE_R + 3:
        return False
    return True


def _fits(p, x, z, r, court, parent=None):
    for o in p.igloos:
        if o is parent:
            continue
        if math.hypot(x - o.x, z - o.z) < r + o.r + 1.5:
            return False
    for c in p.courts:
        if c is court:
            continue
        if math.hypot(x - c.x, z - c.z) < c.r + r + 2.0:
            return False
    for a, b in p.lanes:
        if seg_dist(x, z, a, b) < r + LANE_HALF + 1.5:
            return False
    for _c, d, a, b in p.steps:
        # the steps, and the open snow in front of them (the way in stays clear)
        dx, dz = (b[0] - a[0]), (b[1] - a[1])
        L = math.hypot(dx, dz) or 1.0
        far = (b[0] + dx / L * 9, b[1] + dz / L * 9)
        if seg_dist(x, z, a, far) < r + 4.0:
            return False
    if p.lodge is not None:
        x1, z1, x2, z2 = p.lodge
        if x1 - r - 2 < x < x2 + r + 2 and z1 - r - 2 < z < z2 + r + 2:
            return False
    for pen in p.pens:
        if rect_dist(pen.rect, x, z) < r + PEN_CLEAR:
            return False
    if parent is not None:
        # the passage from the parent's wall out to the new igloo
        L = math.hypot(x - parent.x, z - parent.z) or 1.0
        wall = (parent.x + (x - parent.x) / L * parent.r, parent.z + (z - parent.z) / L * parent.r)
        seg = (wall, (x, z))
        for o in p.igloos:
            if o is parent:
                continue
            if seg_dist(o.x, o.z, *seg) < o.r + PASSAGE_R + 1.0:
                return False
        for c in p.courts:
            if seg_dist(c.x, c.z, *seg) < c.r + PASSAGE_R + 0.5:
                return False
        for a, b in p.lanes:
            if seg_seg(seg[0], seg[1], a, b) < PASSAGE_R + LANE_HALF + 1.0:
                return False
        for _c, _d, a, b in p.steps:
            if seg_seg(seg[0], seg[1], a, b) < PASSAGE_R + 3.0:
                return False
        for q1, q2 in p.passages:
            if seg_seg(seg[0], seg[1], (q1.x, q1.z), (q2.x, q2.z)) < 2 * PASSAGE_R + 1.0:
                return False
    return True


def _place_pen(p, rng, size, kind):
    """A pen of the given outer size on the open snow beside the village: clear of every igloo, courtyard, lane,
    passage, flight of steps (and the snow in front of it) and the lodge, never in front of the way in, as near the
    village as that allows. Its gate faces the nearest courtyard."""
    xs = [c.x - c.r for c in p.courts] + [c.x + c.r for c in p.courts] + [i.x for i in p.igloos]
    zs = [c.z - c.r for c in p.courts] + [c.z + c.r for c in p.courts] + [i.z for i in p.igloos]
    main = p.courts[0]
    best = None
    for _t in range(1500):
        w, d = size if rng.random() < 0.5 else (size[1], size[0])
        cx = rng.uniform(min(xs) - 30, max(xs) + 30)
        cz = rng.uniform(min(zs) - 30, max(zs) + 30)
        rect = (cx - w / 2.0, cz - d / 2.0, cx + w / 2.0, cz + d / 2.0)
        near = 1e9
        ok = True
        for i in p.igloos:
            dd = rect_dist(rect, i.x, i.z) - i.r
            if dd < PEN_CLEAR:
                ok = False
                break
            near = min(near, dd)
        if not ok:
            continue
        for c in p.courts:
            dd = rect_dist(rect, c.x, c.z) - c.r
            if dd < PEN_CLEAR + 2:
                ok = False
                break
            near = min(near, dd)
        if not ok:
            continue
        if any(rect_seg(rect, a, b) < LANE_HALF + PEN_CLEAR + 1 for a, b in p.lanes):
            continue
        if any(rect_seg(rect, (a.x, a.z), (b.x, b.z)) < PASSAGE_R + PEN_CLEAR for a, b in p.passages):
            continue
        bad = False
        for _c, _d, s1, s2 in p.steps:
            dx, dz = s2[0] - s1[0], s2[1] - s1[1]
            Ls = math.hypot(dx, dz) or 1.0
            far = (s2[0] + dx / Ls * 11, s2[1] + dz / Ls * 11)
            if rect_seg(rect, s1, far) < 5:
                bad = True
                break
        if bad:
            continue
        if p.lodge is not None:
            lx1, lz1, lx2, lz2 = p.lodge
            if rect[0] < lx2 + 4 and rect[2] > lx1 - 4 and rect[1] < lz2 + 4 and rect[3] > lz1 - 4:
                continue
        # never in front of the way in (south of the first courtyard, near its line)
        if rect[3] > main.z and rect[0] < main.x + main.r + 6 and rect[2] > main.x - main.r - 6:
            continue
        score = near + rng.uniform(0, 1.5)
        if best is None or score < best[0]:
            best = (score, rect)
    if best is None:
        return None
    rect = best[1]
    pcx, pcz = (rect[0] + rect[2]) / 2.0, (rect[1] + rect[3]) / 2.0
    c = min(p.courts, key=lambda c: math.hypot(c.x - pcx, c.z - pcz))
    gate = facing_towards(pcx, pcz, c.x, c.z)
    return Pen(rect, gate, kind)


# ---------------------------------------------------------------- building it


def build(number, seed, igloos, huntsman=False, name=None, lodge_builder=None):
    p, attempt = plan(seed, igloos, huntsman)
    # the grid: everything, a margin round it
    xs, zs = [], []
    for c in p.courts:
        xs += [c.x - c.r, c.x + c.r]
        zs += [c.z - c.r, c.z + c.r]
    for i in p.igloos:
        xs += [i.x - i.r - 1, i.x + i.r + 1]
        zs += [i.z - i.r - 1, i.z + i.r + 1]
    for _c, _d, a, b in p.steps:
        xs += [a[0], b[0]]
        zs += [a[1], b[1]]
    if p.lodge:
        xs += [p.lodge[0], p.lodge[2]]
        zs += [p.lodge[1], p.lodge[3]]
    for pen in p.pens:
        xs += [pen.rect[0] - 2, pen.rect[2] + 2]
        zs += [pen.rect[1] - 2, pen.rect[3] + 2]
    x0, x1 = min(xs) - MARGIN, max(xs) + MARGIN
    z0, z1 = min(zs) - MARGIN, max(zs) + MARGIN
    W = int(math.ceil(x1 - x0)) | 1
    D = int(math.ceil(z1 - z0)) | 1
    # the middle of the template is the village's middle (the way in's steps run south from the first courtyard)
    v = Village(W, D, H=H, G=G, seed=seed)
    g = v.g
    g.entities = []
    m = v.m

    def X(x):
        return int(round(x - x0))

    def Z(z):
        return int(round(z - z0))

    courts = [(X(c.x), Z(c.z), c.r, c) for c in p.courts]
    dug_floor = {}

    # -- the courtyards: dug 3 down, a ledge one block up round their edge
    for cx, cz, r, c in courts:
        for x in range(int(cx - r - 3), int(cx + r + 4)):
            for z in range(int(cz - r - 3), int(cz + r + 4)):
                if not (0 <= x < W and 0 <= z < D):
                    continue
                d = math.hypot(x - cx, z - cz)
                if d <= r:
                    for y in range(FLOOR, 1):
                        m.dig(x, y, z)
                    dug_floor[(x, z)] = FLOOR
                elif d <= r + 1.4 and dug_floor.get((x, z), 9) > FLOOR + 1:
                    for y in range(FLOOR + 1, 1):
                        m.dig(x, y, z)
                    dug_floor[(x, z)] = FLOOR + 1
    # -- the lanes
    for a, b in p.lanes:
        A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
        for x in range(min(A[0], B[0]) - 3, max(A[0], B[0]) + 4):
            for z in range(min(A[1], B[1]) - 3, max(A[1], B[1]) + 4):
                if 0 <= x < W and 0 <= z < D and seg_dist(x, z, A, B) <= LANE_HALF:
                    for y in range(FLOOR, 1):
                        m.dig(x, y, z)
                    dug_floor[(x, z)] = FLOOR
    # -- the lane to the Huntsman's lodge climbs out of its trench with three stairs at its north end
    lodge_box = None
    if p.lodge is not None:
        lx1, lz1 = X(p.lodge[0]), Z(p.lodge[1])
        lodge_box = (lx1, lz1, lx1 + LODGE_SIZE[0] - 1, lz1 + LODGE_SIZE[1] - 1)
        lane_x = lx1 + LODGE_SIZE[0] // 2
        ze = lodge_box[3] + 3                     # the top stair
        (ax_, az_), (bx_, bz_) = p.lodge_lane
        for x in range(lane_x - 1, lane_x + 2):
            for z in range(lodge_box[3] + 1, ze):
                for y in range(FLOOR, 1):
                    g.clear(x, y, z)
                    m.dug[x, y + G, z] = False
                dug_floor.pop((x, z), None)
            for k in range(3):
                z = ze + k
                top = -k
                for y in range(FLOOR, 1):
                    if y > top:
                        m.dig(x, y, z)
                g.set(x, top, z, "spruce_stairs[facing=north,half=bottom]", force=True)
                m.dug[x, top + G, z] = False
                dug_floor[(x, z)] = top + 1
        # the lane itself runs straight up to the stairs (the box's middle column)
        for z in range(ze + 3, Z(az_) + 1):
            for x in range(lane_x - 1, lane_x + 2):
                if (x, z) not in dug_floor:
                    for y in range(FLOOR, 1):
                        m.dig(x, y, z)
                    dug_floor[(x, z)] = FLOOR
    # -- the steps up (3 wide, a stair a block)
    steps_cells = []
    for c, d, a, b in p.steps:
        cx, cz = X(c.x), Z(c.z)
        f = Frame(g, cx, cz, d)
        edge = int(math.floor(c.r))
        for k in range(0, STEPS_LEN + 2):
            vv = edge - 1 + k
            top = FLOOR + k - 1          # the stair's height (FLOOR-1: the courtyard's floor; 1 and up: the ground)
            for u in (-1, 0, 1):
                x, z = f.xz(u, vv)
                if not (0 <= x < W and 0 <= z < D):
                    continue
                if top >= 1:
                    steps_cells.append((x, z))
                    continue
                for y in range(max(FLOOR, top + 1), 1):
                    m.dig(x, y, z)
                if top >= FLOOR:
                    g.set(x, top, z, f"spruce_stairs[facing={d},half=bottom]", force=True)
                    m.dug[x, top + G, z] = False
                    dug_floor[(x, z)] = top + 1
                else:
                    dug_floor.setdefault((x, z), FLOOR)
                steps_cells.append((x, z))
    # -- the igloos
    rng = random.Random(seed)
    for i in p.igloos:
        x, z = X(i.x), Z(i.z)
        style = HALL_STYLE if i.hall else rng.choice(STYLES)
        if i.court is not None:
            door = facing_towards(x, z, X(i.court.x), Z(i.court.z))
        else:
            door = facing_towards(x, z, X(i.parent.x), Z(i.parent.z))
        n_win = 3 if i.hall else 2
        wins = [(a, 0) for a in ((60, -60, 120, -120, 180)[:n_win + 1])]
        i.dome = m.dome(x, z, i.r, i.r * DOME_H, base=float(FLOOR), style=style, door=door, tunnel=None,
                        windows=wins, name="hall" if i.hall else "igloo")
    for a, b in p.passages:
        m.burrow(a.dome.cx, a.dome.cz, b.dome.cx, b.dome.cz, radius=PASSAGE_R, height=4, base=FLOOR,
                 style=PASSAGE_STYLE)
    m.build()
    m.reserved = {}
    # -- the doorways of the ring igloos: cut through the wall towards the courtyard's middle
    approach = []
    doorway_cells = []
    for i in p.igloos:
        if i.court is None:
            continue
        d = i.dome
        tx, tz = X(i.court.x), Z(i.court.z)
        cells, outside = _doorway(m, d, tx, tz, wide=i.hall)
        res = m.reserved.setdefault(id(d), set())
        y0 = int(math.floor(d.base + 0.5))
        for (x, z) in cells:
            doorway_cells.append((x, y0, z))
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    res.add((x + dx, z + dz))
        # also the cells in, towards the middle of the room
        L = math.hypot(tx - d.cx, tz - d.cz) or 1.0
        for k in range(0, int(d.r)):
            x = int(round(d.cx + (tx - d.cx) / L * k))
            z = int(round(d.cz + (tz - d.cz) / L * k))
            res.add((x, z))
        if outside:
            approach.append((outside, (tx, tz)))
    # passages: where each opens into an igloo stays clear (its mouth, near the igloo's wall; the walk check makes
    # sure nothing in the room is in the way to it)
    for b in getattr(m, "burrows", []):
        for d in m.domes:
            res = m.reserved.setdefault(id(d), set())
            for (x, y, z) in d.room:
                if b.dist(x, z) <= PASSAGE_R + 0.8 and math.hypot(x - d.cx, z - d.cz) >= d.r - 2.6:
                    res.add((x, z))
    # the dug spaces' walls lined with snow before anything stands against them (so nothing in a room, a barrel or a
    # chest, has the world's own ground behind it: the user saw dirt there, October 19)
    _line(g)
    # -- the rooms
    for i in p.igloos:
        kind = i.kind
        opts = {"beds": 3} if kind == "sleeping" else {}
        fn = {"sleeping": interiors.sleeping, "cook": interiors.cookhouse, "workshop": interiors.workshop,
              "trophy": interiors.trophy, "storage": interiors.storage, "hall": interiors.hall}[kind]
        fn(m, i.dome, **opts)
        v.igloos.append((i.dome, kind, opts))
    # -- the courtyards' and lanes' floors, and their walls lined with snow
    for (x, z), fy in dug_floor.items():
        s = g.get(x, fy - 1, z)
        if s is None:
            lane = not any(math.hypot(x - cx, z - cz) <= r + 1.5 for cx, cz, r, _c in courts)
            g.set(x, fy - 1, z, LANE_FLOOR if lane else COURT_FLOOR, force=True)
    _line(g)
    _settle_snow(g)
    # -- every courtyard's fire in its middle, a joint of meat roasting on its spit (always there)
    for cx, cz, r, c in courts:
        props.fire_pit(YFrame(Frame(g, cx, cz, "south"), FLOOR - 1), r=1, benches=True, spit=True, ground=False,
                       bench_r=3, bench="dross:frostbitten_log" if (cx + cz) % 2 else "stripped_spruce_log")
    # -- the Huntsman's lodge, built on its own and set into its place
    lodge_info = None
    if lodge_box is not None and lodge_builder is not None:
        lodge_info = lodge_builder(g, *lodge_box)
    # -- the pens and their animals
    pens = [_build_pen(g, pen, X, Z, random.Random(seed * 31 + k)) for k, pen in enumerate(p.pens)]
    # -- decorations
    dec = Decor(g)
    _entrance_pair(g, dec, p, X, Z, rng)
    for (x, z) in steps_cells:
        dec.keep_out(x, z, 1)
    for cx, cz, r, c in courts:
        dec.keep_out(cx, cz, 4)                     # the fire and its benches
    for pen in pens:
        x1, z1, x2, z2 = pen["box"]
        dec.keepout[max(0, x1 - 2):x2 + 3, max(0, z1 - 2):z2 + 3] = True
    # and the way up to each flight of steps stays open (8 blocks of snow in front of it, 5 wide)
    for c, d, _a, _b in p.steps:
        f = Frame(g, X(c.x), Z(c.z), d)
        edge = int(math.floor(c.r))
        for vv in range(edge + STEPS_LEN, edge + STEPS_LEN + 9):
            for u in range(-2, 3):
                x, z = f.xz(u, vv)
                if 0 <= x < W and 0 <= z < D:
                    dec.keepout[x, z] = True
    # the lanes stay clear where they're trenches and where they open into a courtyard (inside a courtyard, people
    # walk round things)
    for a, b in p.lanes:
        A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
        for x in range(max(0, min(A[0], B[0]) - 4), min(W, max(A[0], B[0]) + 5)):
            for z in range(max(0, min(A[1], B[1]) - 4), min(D, max(A[1], B[1]) + 5)):
                if seg_dist(x, z, A, B) <= LANE_HALF + 1.0 and \
                        all(math.hypot(x - cx, z - cz) > r - 3.5 for cx, cz, r, _c in courts):
                    dec.keepout[x, z] = True
    for (ox, oz), (tx, tz) in approach:
        L = math.hypot(tx - ox, tz - oz) or 1.0
        for k in range(0, 2):
            dec.keep_out(int(round(ox + (tx - ox) / L * k)), int(round(oz + (tz - oz) / L * k)), 1)
    if lodge_info is not None:
        dec.adopt(lodge_info["decor"])
        x1, z1, x2, z2 = lodge_info["box"]
        dec.keepout[max(0, x1 - 1):x2 + 2, max(0, z1 - 1):z2 + 2] = True
    _decorate(g, v, dec, courts, p, X, Z, rng)
    # the decorations that reach underground (a fishing hole) lined too
    _line(g)
    # snow drifts on open ground (never where a decoration may stand, nor in a pen: an animal on a drift could jump
    # its fence)
    blocked = dec.taken | dec.keepout

    def open_ground(x, z):
        return not blocked[x, z] and not v.footprint(x, z, 1) and (x, z) not in dug_floor
    props.drifts(g, 0, area=open_ground, salt=seed % 97)
    info = {"number": number, "id": name or f"sunken_warren_{number:02d}", "name": "Sunken Warren",
            "mood": f"{len(p.courts)} sunken courtyards, {igloos} igloos" + (", the Huntsman's lodge" if huntsman else ""),
            "igloos": igloos, "courts": len(p.courts), "huntsman": huntsman, "plan_attempt": attempt,
            "lanes": len(p.lanes), "loops": p.loops, "passages": len(p.passages)}
    c0, d0, _a, _b = p.steps[0]
    f = Frame(g, X(c0.x), Z(c0.z), d0)
    sx, sz = f.xz(0, int(math.floor(c0.r)) + STEPS_LEN + 2)
    targets = [i.dome for i in p.igloos] + (list(lodge_info["domes"]) if lodge_info else [])
    # the cells the rooms must be lit in: every room, passage and doorway of the igloos, the lodge and the barn
    floors = lighting.floors(g, m, doorway_cells)
    rooms = [[c for c in floors if (c[0], c[1], c[2]) in set(i.dome.room)] for i in p.igloos]
    if lodge_info is not None:
        lm = lodge_info["massing"]
        lodge_floors = lighting.floors(g, lm, lodge_info.get("floors", ()))
        floors |= lodge_floors
        rooms += [[c for c in lodge_floors if c in set(d.room)] for d in lodge_info["domes"]]
        rooms.append(sorted(set(lodge_info.get("floors", ())) & lodge_floors))
    keep_clear = set()
    for res in m.reserved.values():
        keep_clear |= res
    extras = {"decor": dec, "plan": p, "entrance": (sx, 1, sz), "targets": targets, "courts": courts,
              "lodge": lodge_info, "floors": floors, "rooms": rooms, "keep_clear": keep_clear, "pens": pens,
              "massing": m}
    return g, info, extras


def _doorway(m, d, tx, tz, wide=False):
    """Cuts a doorway two high through the dome's wall towards (tx, tz) (a 4-connected line of cells, so it can be
    walked through even on a slant; 3 wide for the hall), framed with packed ice on the outside. Returns the cells cut
    and the first cell outside it."""
    g = m.g
    Gg = g.G
    y0 = int(math.floor(d.base + 0.5))
    L = math.hypot(tx - d.cx, tz - d.cz) or 1.0
    ux, uz = (tx - d.cx) / L, (tz - d.cz) / L
    path = []
    x, z = d.cx, d.cz
    fx, fz = float(d.cx), float(d.cz)
    for _ in range(int(d.r) + 6):
        fx += ux * 0.5
        fz += uz * 0.5
        nx, nz = int(round(fx)), int(round(fz))
        if (nx, nz) == (x, z):
            continue
        if nx != x and nz != z:
            path.append((nx, z))          # step one way, then the other: face to face
        path.append((nx, nz))
        x, z = nx, nz
    cut = []
    outside = None
    seen_shell = False
    widths = (-1, 0, 1) if wide else (0,)
    px_, pz_ = -uz, ux                   # sideways
    for (x, z) in path:
        if not g.inb(x, y0, z):
            break
        is_shell = bool(m.shell[x, y0 + Gg, z]) or bool(m.shell[x, y0 + 1 + Gg, z])
        is_room = bool(m.inner[x, y0 + Gg, z])
        if is_room and not is_shell:
            if seen_shell:
                break
            continue
        if is_shell:
            seen_shell = True
            for w in widths:
                xx, zz = int(round(x + px_ * w)), int(round(z + pz_ * w))
                for y in (y0, y0 + 1):
                    if m.shell[xx, y + Gg, zz]:
                        g.set(xx, y, zz, AIR, force=True)
                        cut.append((xx, zz))
                if wide and m.shell[xx, y0 + 2 + Gg, zz]:
                    g.set(xx, y0 + 2, zz, AIR, force=True)
            continue
        if seen_shell:
            outside = (x, z)
            break
    # the frame: packed ice round the outer end of the cut
    if outside is not None and cut:
        top = y0 + (3 if wide else 2)
        for (x, z) in set(cut):
            s = g.get(x, top, z)
            if s is not None and mc.base(s) in ("snow_block",):
                g.set(x, top, z, "packed_ice", force=True)
        for (x, z) in set(cut):
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                xx, zz = x + dx, z + dz
                if (xx, zz) in cut:
                    continue
                for y in range(y0, top):
                    s = g.get(xx, y, zz)
                    if s is not None and mc.base(s) == "snow_block" and m.shell[xx, y + Gg, zz]:
                        g.set(xx, y, zz, "packed_ice", force=True)
    return sorted(set(cut)), outside


def _settle_snow(g):
    """Takes away snow layers left lying on ice or on anything without a full top (a doorway framed in packed ice
    after the dome was smoothed)."""
    for x, yi, z, st in list(g.blocks()):
        if mc.base(st) != "snow":
            continue
        below = g.pal[g.a[x, yi - 1, z]] if yi > 0 else None
        if below is None:
            continue
        if mc.base(below) in ("ice", "packed_ice", "blue_ice", "frosted_ice") or mc.kind(below) != "full":
            g.a[x, yi, z] = 0


def _open(state):
    """Something you could see past, or stand in: not a full opaque block (air, a lantern, a chest, a carpet, a bed,
    a slab, glass, clear ice, water...)."""
    return state is not None and mc.kind(state) != "full"


def _line(g):
    """Every dug space (courtyards, lanes, steps, sunken rooms and passages) gets walls and a floor of snow where it
    meets the raw ground, so the world's own dirt or stone never shows: every cell the template leaves unset (the
    world's) next to anything open at or under the ground (air, or anything that isn't a full opaque block: a
    lantern, a chest, a slab, a carpet, glass, water...) is lined."""
    Gg = g.G
    W, D = g.W, g.D
    opn = [_open(s) for s in g.pal]
    for x in range(W):
        for z in range(D):
            for y in range(-Gg + 1, 1):
                if not opn[g.a[x, y + Gg, z]]:
                    continue
                for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0), (0, 1, 0)):
                    xx, yy, zz = x + dx, y + dy, z + dz
                    if yy > 0:
                        continue
                    if g.inb(xx, yy, zz) and g.get(xx, yy, zz) is None:
                        g.set(xx, yy, zz, LINING if dy == 0 else FLOOR_BLOCK, force=True)


# ---------------------------------------------------------------- the pens


def _build_pen(g, pen, X, Z, rng):
    """The fence round a pen (spruce, with posts of Frostbitten Log or bone every few blocks, a gate facing the
    village), its floor of trodden snow, a water trough of packed ice, hay; and its animals."""
    x1, z1 = X(pen.rect[0]), Z(pen.rect[1])
    x2, z2 = X(pen.rect[2]), Z(pen.rect[3])
    corral = pen.kind == "corral"
    mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
    gate_cells = set()
    gates = (-1, 0, 1) if corral else (0,)
    for k in gates:
        gate_cells.add({"north": (mx + k, z1), "south": (mx + k, z2), "west": (x1, mz + k), "east": (x2, mz + k)}[pen.gate])
    gate_facing = {"north": "north", "south": "south", "west": "west", "east": "east"}[pen.gate]
    along_x = pen.gate in ("north", "south")
    post_every = 4
    for x, z in props.rect_cells(x1, z1, x2, z2):
        if (x, z) in gate_cells:
            g.set(x, 1, z, f"spruce_fence_gate[facing={gate_facing},in_wall=false,open=false,powered=false]",
                  force=True)
            continue
        corner = x in (x1, x2) and z in (z1, z2)
        post = corner or ((x - x1) % post_every == 0 and z in (z1, z2)) or ((z - z1) % post_every == 0 and x in (x1, x2))
        if post:
            mat = "bone_block[axis=y]" if corral and (x + z) % 8 < 4 else "dross:frostbitten_log[axis=y]"
            g.set(x, 1, z, mat, force=True)
            g.set(x, 2, z, "skeleton_skull[rotation=0]" if corral and corner else
                  ("spruce_fence" if not corral else mat), force=True)
        else:
            g.set(x, 1, z, "spruce_fence", force=True)
    # the posts either side of the gate stand taller, a lantern hung over the gate between them
    for k in (min(gates) - 1, max(gates) + 1):
        gx, gz = (mx + k, z1 if pen.gate == "north" else z2) if along_x else (x1 if pen.gate == "west" else x2, mz + k)
        for y in (1, 2, 3):
            g.set(gx, y, gz, "dross:frostbitten_log[axis=y]", force=True)
    # the floor: trodden snow, Hoarfrost, a little gravel (never dirt)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            g.set(x, 0, z, PEN_FLOOR, force=True)
    # hay in a corner (stacked), a trough of packed ice with water along the far side; both a block clear of the fence
    # (an animal that can get up on something next to a fence steps over it: a moose got out that way in a test)
    hx, hz = x1 + 2, z1 + 2
    hay = [(hx, hz), (hx + 1, hz), (hx, hz + 1)]
    if corral:
        hay += [(hx + 2, hz), (hx, hz + 2), (hx + 1, hz + 1)]
    for k, (x, z) in enumerate(hay):
        g.set(x, 1, z, "hay_block[axis={}]".format("x" if k % 2 else "z"), force=True)
    g.set(hx, 2, hz, "hay_block[axis=y]", force=True)
    if corral:
        g.set(hx + 1, 2, hz, "hay_block[axis=x]", force=True)
    # the trough: on the side away from the hay, inside the fence (a ring of packed ice round open water that never
    # freezes over: a waterlogged light block on top of the water, like the lakes' holes)
    tl = 7 if corral else 4
    tz_ = z2 - 3
    tx1 = x2 - 3 - tl
    trough = []
    for x in range(tx1, tx1 + tl + 2):
        for z in (tz_ - 1, tz_, tz_ + 1):
            inner = tx1 < x < tx1 + tl + 1 and z == tz_
            g.set(x, 0, z, "packed_ice", force=True)
            if inner:
                g.set(x, 1, z, props.OPEN_WATER, force=True)
                trough.append((x, z))
            else:
                g.set(x, 1, z, "packed_ice", force=True)
    # nothing an animal could climb on right inside the fence
    for x, z in props.rect_cells(x1 + 1, z1 + 1, x2 - 1, z2 - 1):
        st = g.get(x, 1, z)
        if st is not None and not mc.is_air(st) and mc.kind(st) not in ("carpet", "cross"):
            raise SystemExit(f"a {mc.base(st)} at {x}, {z} right inside a {pen.kind}'s fence: an animal could climb out "
                             f"over it")
    # the animals, spread out over the open part of the pen, facing every way
    taken = set(hay) | {(x, z) for x in range(tx1 - 1, tx1 + tl + 3) for z in (tz_ - 2, tz_ - 1, tz_, tz_ + 1, tz_ + 2)}
    gap = 2.6 if corral else 2.0                  # from the fence (half its width and a little)
    if corral:
        herd = [("dross:rimehorn", {"Male": True})]
        herd += [("dross:rimehorn", {"Male": False}) for _ in range(rng.choice((4, 5)))]
        if rng.random() < 0.6:
            herd.append(("dross:rimehorn", {"Male": rng.random() < 0.5, "Age": RIMEHORN_CALF_AGE}))
        apart = 4.2
    else:
        herd = [("dross:moose", {"Bull": rng.random() < 0.5, "Snowy": True})]
        if rng.random() < 0.6:
            herd.append(("dross:moose", {"Bull": rng.random() < 0.4, "Snowy": True}))
        apart = 3.4
    spots = []
    for _t in range(4000):
        if len(spots) == len(herd):
            break
        px = rng.uniform(x1 + gap, x2 + 1 - gap)
        pz = rng.uniform(z1 + gap, z2 + 1 - gap)
        if any(math.hypot(px - qx, pz - qz) < apart for qx, qz in spots):
            continue
        if any(abs(px - (tx + 0.5)) < gap and abs(pz - (tzz + 0.5)) < gap for tx, tzz in taken):
            continue
        spots.append((px, pz))
    if len(spots) < len(herd):
        raise SystemExit(f"no room for {len(herd)} animals in a {pen.kind} of {x2 - x1} x {z2 - z1}")
    animals = []
    for (kind, flags), (px, pz) in zip(herd, spots):
        e = animal(kind, px, 1.0, pz, yaw=round(rng.uniform(-180, 180), 1), **flags)
        g.entities.append(e)
        animals.append(e)
    return {"box": (x1, z1, x2, z2), "kind": pen.kind, "gate": pen.gate, "animals": animals, "trough": trough}


# ---------------------------------------------------------------- decorations


def _court_items(rng):
    """What may stand in a courtyard (each a 1 in 2 chance in the game)."""
    return [
        ("meat rack", lambda f: props.meat_rack(f, length=rng.choice((3, 4)))),
        ("drying rack", lambda f: props.drying_rack(f, length=4)),
        ("hide frame", lambda f: props.hide_frame(f, colour=rng.choice(("brown", "light_gray", "white")))),
        ("hide rack", lambda f: props.hide_rack(f)),
        ("hide line", lambda f: props.hide_line(f, length=rng.choice((4, 5)))),
        ("woodpile", lambda f: props.woodpile(f, length=3)),
        ("sled", lambda f: props.sled(f, cargo=rng.choice(("mixed", "logs", "meat", "hides", "ice")))),
        ("weapon rack", lambda f: props.weapon_rack(f, n=3)),
        ("spear stand", lambda f: props.spear_stand(f)),
        ("bone pile", lambda f: props.bone_pile(f, big=rng.random() < 0.5)),
        ("skull pile", lambda f: props.skull_pile(f)),
        ("butcher's block", lambda f: props.butcher_block(f)),
        ("banner pole", lambda f: props.banner_pole(f, rng.choice(("white", "light_gray")), height=4,
                                                    cloth=rng.choice(CLOTHS))),
        ("lean-to", lambda f: props.lean_to(f)),
        ("stew fire", lambda f: props.stew_fire(f)),
        ("smoking rack", lambda f: props.smoking_rack(f, length=3)),
        ("crates", lambda f: props.crate_stack(f, lantern=rng.random() < 0.4)),
        ("kayak", lambda f: props.kayak_rack(f)),
        ("snow blocks", lambda f: props.snow_block_stack(f)),
        ("ice blocks", lambda f: props.ice_block_stack(f)),
        ("anvil", lambda f: props.anvil_corner(f)),
        ("frostblooms", lambda f: props.frostbloom_patch(f, ground=False)),
        ("rimehorn skull", lambda f: props.rimehorn_skull(f)),
        ("ice sculpture", lambda f: props.ice_sculpture(f, kind=rng.choice(("rimehorn", "angel", "crystal")))),
    ]


def _ground_items(rng):
    """What may stand on the snow round the village."""
    return [
        ("inuksuk", lambda f: props.inuksuk(f, tall=rng.random() < 0.6)),
        ("antler post", lambda f: props.antler_post(f, height=3)),
        ("skull post", lambda f: props.skull_post(f, height=rng.choice((2, 3)))),
        ("totem", lambda f: props.totem(f, height=rng.choice((5, 6)))),
        ("meat rack", lambda f: props.meat_rack(f, length=4)),
        ("drying rack", lambda f: props.drying_rack(f, length=rng.choice((3, 4)))),
        ("hide frame", lambda f: props.hide_frame(f, colour=rng.choice(("brown", "light_gray")))),
        ("hide line", lambda f: props.hide_line(f, length=rng.choice((4, 5, 6)))),
        ("sled", lambda f: props.sled(f, cargo=rng.choice(("mixed", "logs", "meat", "hides", "ice", None)))),
        ("dog sleds", lambda f: props.dog_sleds(f)),
        ("woodpile", lambda f: props.woodpile(f, length=rng.choice((2, 3)))),
        ("bone pile", lambda f: props.bone_pile(f, big=True)),
        ("fishing hole", lambda f: props.fishing_hole(f)),
        ("ice lantern", lambda f: props.ice_lantern(f, height=2)),
        ("lantern post", lambda f: props.lantern_post(f, height=3, arm=rng.random() < 0.7)),
        ("lean-to", lambda f: props.lean_to(f)),
        ("spear stand", lambda f: props.spear_stand(f)),
        ("meat cache", lambda f: props.meat_cache(f)),
        ("crates", lambda f: props.crate_stack(f, lantern=rng.random() < 0.3)),
        ("ice sculpture", lambda f: props.ice_sculpture(f, kind=rng.choice(("rimehorn", "angel", "crystal")))),
        ("frostblooms", lambda f: props.frostbloom_patch(f)),
        ("kayak", lambda f: props.kayak_rack(f)),
        ("snow blocks", lambda f: props.snow_block_stack(f)),
        ("ice blocks", lambda f: props.ice_block_stack(f)),
        ("windbreak", lambda f: props.windbreak(f, length=rng.choice((4, 5, 6)))),
        ("smoking rack", lambda f: props.smoking_rack(f, length=rng.choice((3, 4)))),
        ("rimehorn skull", lambda f: props.rimehorn_skull(f)),
        ("banner pole", lambda f: props.banner_pole(f, rng.choice(("white", "light_gray")), height=4,
                                                    cloth=rng.choice(CLOTHS))),
        ("stew fire", lambda f: props.stew_fire(f)),
    ]


DIRS = ("north", "south", "east", "west")


def _decorate(g, v, dec, courts, p, X, Z, rng):
    # each courtyard: things round its fire, close together (the user: "add life with more blocks and stuff")
    for k, (cx, cz, r, c) in enumerate(courts):
        dy = FLOOR - 1
        want = int(r * 1.15) + 3
        placed = 0
        items = _court_items(rng)
        for _t in range(600):
            if placed >= want:
                break
            a = rng.uniform(0, 2 * math.pi)
            d = rng.uniform(4.6, r - 2.0)
            x, z = int(round(cx + math.sin(a) * d)), int(round(cz + math.cos(a) * d))
            front = facing_towards(x, z, cx, cz)
            name, fn = rng.choice(items)
            if dec.place(name, lambda s, x=x, z=z, front=front, fn=fn: fn(YFrame(Frame(s, x, z, front), dy))):
                placed += 1
    # over the lanes: great ribs of bone arching across, icicles hanging from them; lanterns along their rims
    for a, b in p.lanes:
        A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
        L = math.hypot(B[0] - A[0], B[1] - A[1])
        if L < 1:
            continue
        ux, uz = (B[0] - A[0]) / L, (B[1] - A[1]) / L
        front = facing_towards(0, 0, ux, uz)
        # where the lane runs as a trench (outside the courtyards)
        ts = [t for t in range(0, int(L) + 1)
              if all(math.hypot(A[0] + ux * t - cx, A[1] + uz * t - cz) > r + 4 for cx, cz, r, _c in courts)]
        if not ts:
            continue
        t0, t1 = ts[0], ts[-1]
        n_arch = max(1, int((t1 - t0) // 13))
        for i in range(n_arch):
            t = t0 + (t1 - t0) * (i + 0.5) / n_arch
            x, z = int(round(A[0] + ux * t)), int(round(A[1] + uz * t))
            for width in (5, 7, 9):
                if dec.place("bone arch", lambda s, x=x, z=z, w=width: props.lane_arch(Frame(s, x, z, front), width=w,
                                                                                    height=6), overhead=True):
                    break
        # lantern posts along both rims, every few blocks, turn about
        side = 1
        t = t0 + 3
        while t <= t1 - 2:
            px, pz = -uz * side, ux * side
            off = LANE_HALF + 2.6
            x, z = int(round(A[0] + ux * t + px * off)), int(round(A[1] + uz * t + pz * off))
            arm_front = facing_towards(0, 0, -px, -pz)
            dec.place("lane lantern", lambda s, x=x, z=z, fr=arm_front: props.lantern_post(Frame(s, x, z, fr), height=3,
                                                                                         arm=False), margin=1)
            side = -side
            t += 7
    # by the top of every flight of steps: a lantern post either side
    for c, d, _a, _b in p.steps:
        f = Frame(g, X(c.x), Z(c.z), d)
        top = int(math.floor(c.r)) + STEPS_LEN + 1
        for side in (-3, 3):
            x, z = f.xz(side, top)
            dec.place("steps lantern", lambda s, x=x, z=z: props.lantern_post(Frame(s, x, z, d), height=3, arm=False),
                      margin=0)
    # round the village on the snow
    n_out = int(len(p.igloos) * 3.2) + 12
    placed = 0
    items = _ground_items(rng)
    for _t in range(n_out * 40):
        if placed >= n_out:
            break
        x, z = rng.randrange(3, g.W - 3), rng.randrange(3, g.D - 3)
        if any(math.hypot(x - cx, z - cz) <= r + 2.5 for cx, cz, r, _c in courts):
            continue
        if v.footprint(x, z, 2):
            continue
        # near the village: within a few blocks of a courtyard's edge or an igloo
        near = min([math.hypot(x - cx, z - cz) - r for cx, cz, r, _c in courts] +
                   [math.hypot(x - i.dome.cx, z - i.dome.cz) - i.r for i in p.igloos])
        if near > NEAR_VILLAGE:
            continue
        name, fn = rng.choice(items)
        front = rng.choice(DIRS)
        if dec.place(name, lambda s, x=x, z=z, front=front, fn=fn: fn(Frame(s, x, z, front))):
            placed += 1


def _entrance_pair(g, dec, p, X, Z, rng):
    """The way in: a pair of totems or inuksuks either side of the top of the main steps, facing out."""
    c0, d0, _a, _b = p.steps[0]
    f = Frame(g, X(c0.x), Z(c0.z), d0)
    top = int(math.floor(c0.r)) + STEPS_LEN + 2
    pick = rng.random() < 0.5
    for side in (-3, 3):
        x, z = f.xz(side, top + 2)
        if pick:
            dec.place("totem", lambda s, x=x, z=z: props.totem(Frame(s, x, z, d0), height=6), margin=0)
        else:
            dec.place("inuksuk", lambda s, x=x, z=z: props.inuksuk(Frame(s, x, z, d0), tall=True), margin=0)
