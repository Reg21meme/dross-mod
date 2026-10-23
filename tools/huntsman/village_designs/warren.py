"""The Sunken Warren built from the hand-designed igloos (step 2 of the plan of October 20, 2026: "create a general
template for each house ... then copy and paste them"; keep how the igloos connect: "I like that part, don't change
that part").

The layout is the Sunken Warren's (sunken_warren.py): courtyards dug three blocks down into the snow, joined by sunken
lanes in loops, steps up out of them, the first courtyard the biggest with the way in at its south, a fire with a
joint of meat on its spit in every courtyard. What changed is what stands round them: every igloo is one of the
hand-designed templates (igloo_designs, villagekit.stamp), set in exactly as designed, turned so its doorway faces its
courtyard and sunk so its doorway opens onto the courtyard's floor.

  * ring igloos stand round the courtyards (their doorways onto the floor);
  * lane igloos stand along the lanes (their doorways onto the lane);
  * the warren's igloos stand behind the rings, each joined by a covered snow passage from its doorway to a wall of
    the igloo in front of it, where that wall has clear floor inside (villagekit.stamp's spots: no bed, chest or lamp
    is ever moved or blocked); neighbours whose walls face each other are joined too, where a passage fits;
  * the Snow Angel's Shrine stands alone on the snow by the village, joined to nothing (the user: "they should not be
    connected to any other igloo to show their importance"), a path of lanterns to it; the Rimeberry Patches stand
    on the snow too;
  * the Huntsman's village has his lodge and the Alpha Moose's barn at its north, on the snow, his yard before them,
    a lane and steps up to it.

Which igloos a village gets is the spawn table the user approved (October 20): a regular village always has a
meeting hall (the Round Hall 60%, the Long Hall 40%), a Cookhouse and a Storehouse, the rest rolled (homes 55, work
22, food 23), the shrine in 1 village of 3, 1-2 Rimeberry Patches; the Huntsman's both halls, 2 Barracks, a Cookhouse,
a Storehouse, the shrine, 2 Patches, the rest rolled. A ring of lights stands round every village (always there), and
the pens' fences are two high (nothing gets out over them) with nothing to climb within two blocks of them.

    build(number, seed, igloos, huntsman=False, shrine=False, name=None) -> (grid, info, extras)
"""
import math
import random
from itertools import combinations

import mc
from voxel import AIR, Mix

import sunken_warren as sw
from villagekit import lighting, props, stamp, walk
from villagekit.decor import Decor, YFrame
from villagekit.local import Frame
from villagekit.village import Village, facing_towards
from villagekit.writer import animal

FLOOR = sw.FLOOR                 # where you stand on a courtyard's floor (3 under the ground)
SINK = FLOOR - 1                 # a sunken template's floor layer goes here (its layer 0)
LANE_HALF = sw.LANE_HALF
COURT_GAP = 24.0                 # between two courtyards' edges (the templates are a little bigger than the old igloos)
LOOP_GAP = 38.0
RIM_OUT = 2.2                    # a ring igloo's first dome this far outside its courtyard's edge
HALL_OUT = 3.0
RING_STEP = 2.5                  # degrees round a courtyard between tries
DISC_GAP = 1.5                   # between two igloos' domes
OWN_COURT_OVERLAP = 3.0          # a ring igloo's other domes may reach this far into its own courtyard
PASSAGE_R = 2.0                  # a covered passage's walls this far from its middle line (3 wide inside)
PASSAGE_IN = 1.0                 # its room this far
PASSAGE_LEN = (3, 6)             # blocks of passage between a parent's wall and the outlier's doorway
PASSAGE_JOG = 2                  # the outlier may sit this far to the side of the passage's straight line
EXTRA_LEN = (2.0, 9.0)           # two facing walls this far apart may be joined too (the warren)
MAX_LINKS = 3                    # passages into one igloo, at most
OUTLIER_SHARE = 0.3              # about this share of the igloos sit behind the rings
APPROACH_MAX = 8                 # a doorway's dug way to its courtyard or lane, at most this long
MARGIN = 13                      # open ground round the village (its ring of lights stands in it)
H = 24
G = 5

# (October 23, 2026: 9 deeper for his trophy corridor, the barn moved down with his door so his yard stays as it was)
LODGE_SIZE = (64, 46)            # the Huntsman's lodge, the barn and his yard
LODGE_DOOR_X = 17                # the lodge's doorway this far from the box's west edge (the lane arrives under it)
LODGE_AT = (17, 9)               # the lodge's anchor in its box
BARN_AT = (51, 20)               # the barn's

# the pens: fences two high (an animal standing on anything can't jump them), nothing to climb within two blocks
PADDOCK = (15, 13)
CORRAL = (27, 25)
PEN_CLEAR = 3.0
PEN_KEEP = 2                     # nothing an animal could stand on within this many blocks inside the fence
RIMEHORN_CALF_AGE = sw.RIMEHORN_CALF_AGE

SURFACE_CLEAR = 4.0              # the shrine and the patches: this much open snow round them
SHRINE_APART = 14.0              # the shrine stands about this far from the rest of the village (alone)
# The lamps inside a village (the user, October 22, 2026: "we need some lighting inside the villages, not just the
# outside ... big poles and stuff"): always there, like the ring of lights. A courtyard gets a tall pole every
# COURT_LAMP_EVERY blocks round it, COURT_LAMP_IN in from its rim; a lane a pole on its bank every LANE_LAMP_EVERY,
# by turns on either side, its arm reaching out over the lane.
COURT_LAMP_EVERY = 7.0
COURT_LAMP_IN = 1.8
LANE_LAMP_EVERY = 9.0
LANE_LAMP_OUT = 1.6 + 1.6         # (from the lane's middle line: just past its edge, on the bank)
# A staircase's landing (the user, October 22, 2026: the top of the stairs didn't meet the ground): LANDING_LONG rows
# past its top step, LANDING_HALF either side of its middle, snow at the ground with solid snow under it LANDING_DEEP
# down, and nothing over it or over its top steps CLEAR_OVER up (a snow layer or a plant of the world's left there).
LANDING_LONG = 2
LANDING_HALF = 2
LANDING_DEEP = 3
CLEAR_OVER = 2
LANDING_SOFT = ("snow_block", "dross:hoarfrost", "gravel", "snow")     # what a landing may replace at the ground
ARCH_CLEAR = 5                    # a lane's arch stands at least this far (blocks) from any stair or landing
RING_OUT = 7.0                   # the ring of lights this far outside everything
RING_EVERY = 11.0                # a light every this many blocks round it

# ---------------------------------------------------------------- the spawn table (the user's, October 20, 2026)

HALLS = (("round_hall", 60), ("long_hall", 40))
HOMES = (("family_igloo", 14), ("trappers_hut", 9), ("twin_hunters", 9), ("scouts_igloo", 8),
         ("apprentice_igloo", 8), ("elders_igloo", 7))
WORK = (("workshop_igloo", 7), ("barracks_igloo", 6), ("tannery_igloo", 5), ("storage_igloo", 4))
FOOD = (("root_house", 9), ("lichen_house", 7), ("fishing_hut", 7))
ONLY_ONE = ("elders_igloo", "barracks_igloo")
PATCHES = (1, 2)


def choose(rng, n, huntsman, shrine, beds_of):
    """The igloos a village gets: [design id] (n of them), and how many Rimeberry Patches; at least one bed for every
    two igloos (a roll that comes up short is rolled again)."""
    for _attempt in range(200):
        if huntsman:
            fixed = ["round_hall", "long_hall", "barracks_igloo", "barracks_igloo", "cook_igloo", "storage_igloo"]
        else:
            fixed = [_weighted(rng, HALLS), "cook_igloo", "storage_igloo"]
        out = list(fixed)
        table = list(HOMES) + list(WORK) + list(FOOD)
        while len(out) < n:
            pick = _weighted(rng, table)
            if pick in ONLY_ONE and (pick in out):
                continue
            out.append(pick)
        beds = sum(beds_of[d] for d in out)
        if beds >= (n + 1) // 2:
            patches = 2 if huntsman else rng.choice(PATCHES)
            return out, patches
    raise SystemExit(f"no roll of {n} igloos with enough beds")


def _weighted(rng, table):
    total = sum(w for _d, w in table)
    r = rng.uniform(0, total)
    for d, w in table:
        r -= w
        if r <= 0:
            return d
    return table[-1][0]


# ---------------------------------------------------------------- the pieces (built once)

_PIECES = {}


def pieces():
    """Every design built once, as a stamp.Piece by id."""
    if not _PIECES:
        import igloos as igloo_tool
        from igloo_designs import plan
        import villages
        for d in igloo_tool.designs():
            built = plan.build(d) if isinstance(d, plan.Igloo) else plan.build_custom(d)
            villages.tidy(built.g)
            if built.problems:
                raise SystemExit(f"{d.id} has problems of its own: {built.problems[:3]}")
            _PIECES[d.id] = stamp.Piece(built)
    return _PIECES


def beds_of(piece):
    return sum(1 for c in piece.cells if mc.base(c[3]).endswith("_bed") and "part=head" in c[3])


_COLS = {}


def turned_columns(piece, k):
    key = (piece.id, k)
    if key not in _COLS:
        _COLS[key] = [stamp.turn_xz(dx, dz, k) for dx, dz in piece.columns]
    return _COLS[key]


# ---------------------------------------------------------------- the plan


class Item:
    """One template in the plan: where (its anchor, whole blocks), its turn, what it is to the village (ring, lane,
    warren, surface), its courtyard or the igloo it's joined to."""

    def __init__(self, piece, x, z, k, role, court=None, parent=None):
        self.piece, self.x, self.z, self.k, self.role = piece, int(x), int(z), k, role
        self.court, self.parent = court, parent
        self.placed = stamp.Placed(piece, self.x, self.z, k, 0 if role == "surface" else SINK)
        self.discs = self.placed.discs()
        self.used = set()                     # its passage spots already taken (their outer cells)
        self.links = 0
        self.lane = None

    @property
    def id(self):
        return self.piece.id

    def columns(self):
        return [(self.x + a, self.z + b) for a, b in turned_columns(self.piece, self.k)]

    def free_spots(self):
        return [s for s in self.placed.spots() if s.outer not in self.used]


class Plan(sw.Plan):
    def __init__(self):
        super().__init__()
        self.items = []
        self.links = []           # (from item, its entrance or spot outer (x, z), to item, its spot or None)
        self.occupied = {}        # (x, z) -> item
        self.surface = []


def _cardinal(dx, dz):
    return facing_towards(0, 0, dx, dz)


def _layout(rng, n, huntsman):
    """The courtyards, lanes, loops, steps and the lodge's box (sunken_warren's planner, with more room)."""
    p = Plan()
    k = sw.courts_for(n)
    p.courts.append(sw.Court(0.0, 0.0, sw.MAIN_R, main=True))
    joined = set()
    for _ in range(k - 1):
        best = None
        for _t in range(80):
            parent = rng.choice(p.courts)
            r = rng.uniform(*sw.COURT_R)
            a = rng.uniform(-math.pi * 0.95, math.pi * 0.05) if not parent.main else rng.uniform(-math.pi, 0.0)
            d = parent.r + r + COURT_GAP + rng.uniform(0, 4)
            x, z = parent.x + math.cos(a) * d, parent.z + math.sin(a) * d
            if z > 4.0:
                continue
            if any(math.hypot(x - c.x, z - c.z) < c.r + r + COURT_GAP - 0.5 for c in p.courts):
                continue
            c = sw.Court(x, z, r)
            lane = ((parent.x, parent.z), (x, z))
            if not sw._lane_ok(p, parent, c, lane):
                continue
            score = sum(math.hypot(x - o.x, z - o.z) for o in p.courts) + rng.uniform(0, 10)
            if best is None or score < best[0]:
                best = (score, c, parent)
        if best is None:
            return None
        p.courts.append(best[1])
        p.lanes.append(((best[2].x, best[2].z), (best[1].x, best[1].z)))
        joined.add((id(best[2]), id(best[1])))
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
        if sw._lane_ok(p, a, b, lane):
            p.lanes.append(lane)
            p.loops += 1
    if k >= 3 and p.loops == 0:
        return None
    cx = sum(c.x for c in p.courts) / len(p.courts)
    cz = sum(c.z for c in p.courts) / len(p.courts)
    p.steps.append((p.courts[0], "south") + sw._steps_band(p.courts[0], "south"))
    for c in p.courts[1:]:
        options = []
        for d, (dx, dz) in (("north", (0, -1)), ("east", (1, 0)), ("west", (-1, 0)), ("south", (0, 1))):
            band = sw._steps_band(c, d)
            if any(sw.seg_seg(band[0], band[1], a, b) < LANE_HALF + 4 for a, b in p.lanes):
                continue
            if any(sw.seg_dist(o.x, o.z, *band) < o.r + 3 for o in p.courts if o is not c):
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
        x1 = north.x - LODGE_DOOR_X
        p.lodge = (x1, z2 - ld, x1 + lw, z2)
        lane = ((north.x, north.z), (north.x, z2 + 3.5))
        if any(sw.seg_dist(o.x, o.z, *lane) < o.r + 3 for o in p.courts if o is not north):
            return None
        if any(sw.seg_seg(lane[0], lane[1], a, b) < 2 * LANE_HALF + 7 for a, b in p.lanes
               if (north.x, north.z) not in (a, b)):
            return None
        p.lanes.append(lane)
        p.lodge_lane = lane
        p.lodge_court = north
        # (its courtyard's steps can't run into the lodge's lane: up to the ground and straight down into the lane, the
        # user's "the top of the stairs doesn't meet the ground"; the lane and the lodge's stairs are its way up)
        p.steps = [st for st in p.steps
                   if not (st[0] is north and sw.seg_seg(st[2], st[3], lane[0], lane[1]) < LANE_HALF + 4)]
    return p


def _fits(p, item, ignore=()):
    """Room for an item: its domes clear of the other igloos, the courtyards (but its own), the lanes (its own lane
    only as near as its doorway), the steps and the way up them, the lodge, the pens, the surface pieces and the
    passages; and no column of it on or next to another template's."""
    for idx, (x, z, r) in enumerate(item.discs):
        for o in p.items:
            if o in ignore:
                continue
            for ox, oz, orr in o.discs:
                if math.hypot(x - ox, z - oz) < r + orr + DISC_GAP:
                    return False
        for c in p.courts:
            d = math.hypot(x - c.x, z - c.z)
            if c is item.court:
                if idx > 0 and d < c.r + r - OWN_COURT_OVERLAP:
                    return False
                continue
            if d < c.r + r + 2.0:
                return False
        for a, b in p.lanes:
            near = LANE_HALF + 0.5 if (a, b) == item.lane else LANE_HALF + 1.5
            if sw.seg_dist(x, z, a, b) < r + near:
                return False
        for _c, d, a, b in p.steps:
            dx, dz = (b[0] - a[0]), (b[1] - a[1])
            L = math.hypot(dx, dz) or 1.0
            far = (b[0] + dx / L * 9, b[1] + dz / L * 9)
            if sw.seg_dist(x, z, a, far) < r + 4.0:
                return False
        if p.lodge is not None:
            x1, z1, x2, z2 = p.lodge
            if x1 - r - 2 < x < x2 + r + 2 and z1 - r - 2 < z < z2 + r + 2:
                return False
        for pen in p.pens:
            if sw.rect_dist(pen.rect, x, z) < r + PEN_CLEAR:
                return False
        for s in p.surface:
            if sw.rect_dist(s.rect, x, z) < r + SURFACE_CLEAR:
                return False
        for (a, b) in _passage_lines(p):
            if sw.seg_dist(x, z, a, b) < r + PASSAGE_R + 1.0:
                return False
    occ = p.occupied
    for x, z in item.columns():
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            o = occ.get((x + dx, z + dz))
            if o is not None and o is not item and o not in ignore:
                return False
    return True


def _passage_lines(p):
    return [(a, b) for _f, a, _t, b in p.links if b is not None]


def _add(p, item):
    p.items.append(item)
    for c in item.columns():
        p.occupied[c] = item


def _ring(p, rng, court, ids, want, angles=None):
    """Igloos round a courtyard, their doorways onto its floor: ids is the queue of designs (the first that fits at
    each angle is taken). Returns the items placed."""
    placed = []
    start = rng.uniform(0, 360)
    ang = 0.0
    while ang < 360 and ids and len(placed) < want:
        a = math.radians(start + ang)
        took = False
        for k_try, did in enumerate(list(ids)[:6]):
            piece = pieces()[did]
            r0 = piece.domes[0][2]
            ring = court.r + (HALL_OUT if piece.kind == "hall" else RIM_OUT)
            x, z = court.x + math.sin(a) * (ring + r0 - 5.0), court.z + math.cos(a) * (ring + r0 - 5.0)
            door = facing_towards(x, z, court.x, court.z)
            k = stamp.turns_for(door)
            item = Item(piece, round(x), round(z), k, "ring", court=court)
            ex, ez = item.placed.entrance
            if math.hypot(ex - court.x, ez - court.z) > court.r + 1.0:
                continue                      # its doorway must open onto (or right by) the courtyard's floor
            if _fits(p, item):
                _add(p, item)
                placed.append(item)
                ids.remove(did)
                took = True
                break
        ang += 12.0 if took else RING_STEP
    return placed


def _hall(p, court, did, prefer):
    """A meeting hall at the back of a courtyard (the way round from `prefer`, an angle, that's free)."""
    piece = pieces()[did]
    for off in (0, 20, -20, 40, -40, 60, -60, 80, -80, 100, -100):
        a = math.radians(prefer + off)
        ring = court.r + HALL_OUT + piece.domes[0][2] - 6.0
        x, z = court.x + math.sin(a) * ring, court.z + math.cos(a) * ring
        door = facing_towards(x, z, court.x, court.z)
        item = Item(piece, round(x), round(z), stamp.turns_for(door), "ring", court=court)
        ex, ez = item.placed.entrance
        if math.hypot(ex - court.x, ez - court.z) > court.r + 1.0:
            continue
        if _fits(p, item):
            _add(p, item)
            return item
    return None


def _outlier(p, rng, did, tries=300):
    """A warren igloo behind the others: a free spot on another igloo's wall facing out, then this one a few blocks out
    from it, its doorway facing back, a covered passage between."""
    piece = pieces()[did]
    hosts = [i for i in p.items if i.role in ("ring", "lane", "warren") and i.links < MAX_LINKS]
    for _t in range(tries):
        if not hosts:
            return None
        host = rng.choice(hosts)
        spots = host.free_spots()
        if not spots:
            continue
        spot = rng.choice(spots)
        # out from its courtyard (or away from its own host), never back into the village's middle
        base = host.court or (host.parent.court if host.parent else None)
        sx, sz = stamp.STEPS[spot.side]
        if base is not None and (spot.outer[0] - base.x) * sx + (spot.outer[1] - base.z) * sz <= 0:
            continue
        L = rng.randint(*PASSAGE_LEN)
        jog = rng.randint(-PASSAGE_JOG, PASSAGE_JOG)
        tx, tz = -sz, sx
        ex = spot.outer[0] + sx * L + tx * jog
        ez = spot.outer[1] + sz * L + tz * jog
        door = stamp.turn_dir(spot.side, 2)
        k = stamp.turns_for(door)
        ox, oz = stamp.turn_xz(*piece.entrance, k)
        item = Item(piece, ex - ox, ez - oz, k, "warren", parent=host)
        if not _fits(p, item):
            continue
        if not _link_clear(p, (ex, ez), spot.outer, (host, item)):
            continue
        _add(p, item)
        host.used.add(spot.outer)
        host.links += 1
        item.links += 1
        p.links.append((item, (ex, ez), host, spot.outer))
        p.link_spots = getattr(p, "link_spots", {})
        p.link_spots[(id(host), spot.outer)] = spot
        return item
    return None


def _lane_igloo(p, rng, did, tries=200):
    """An igloo along a lane, its doorway onto the lane's floor."""
    piece = pieces()[did]
    lanes = [ln for ln in p.lanes if ln != getattr(p, "lodge_lane", None)]
    for _t in range(tries):
        a, b = rng.choice(lanes)
        L = math.hypot(b[0] - a[0], b[1] - a[1])
        t = rng.uniform(0.25, 0.75)
        px, pz = a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t
        if any(math.hypot(px - c.x, pz - c.z) < c.r + 6 for c in p.courts):
            continue
        ux, uz = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        side = rng.choice((-1, 1))
        nx, nz = -uz * side, ux * side
        door = _cardinal(-nx, -nz)
        k = stamp.turns_for(door)
        ox, oz = stamp.turn_xz(*piece.entrance, k)
        # its entrance just outside the lane's edge
        ex, ez = px + nx * (LANE_HALF + 1.5), pz + nz * (LANE_HALF + 1.5)
        item = Item(piece, round(ex - ox), round(ez - oz), k, "lane")
        item.lane = (a, b)
        eX, eZ = item.placed.entrance
        if sw.seg_dist(eX, eZ, a, b) > LANE_HALF + APPROACH_MAX - 2:
            continue
        if _fits(p, item):
            _add(p, item)
            return item
    return None


def _link_clear(p, a, b, own):
    """A covered passage from a to b: clear of the courtyards, the lanes, the steps, the other igloos (but its two
    ends'), the other passages, the lodge, the pens and the surface pieces."""
    for o in p.items:
        if o in own:
            continue
        for ox, oz, r in o.discs:
            if sw.seg_dist(ox, oz, a, b) < r + PASSAGE_R + 1.0:
                return False
    for c in p.courts:
        if sw.seg_dist(c.x, c.z, a, b) < c.r + PASSAGE_R + 1.0:
            return False
    for l1, l2 in p.lanes:
        if sw.seg_seg(a, b, l1, l2) < PASSAGE_R + LANE_HALF + 1.5:
            return False
    for _c, _d, s1, s2 in p.steps:
        dx, dz = s2[0] - s1[0], s2[1] - s1[1]
        Ls = math.hypot(dx, dz) or 1.0
        far = (s2[0] + dx / Ls * 9, s2[1] + dz / Ls * 9)
        if sw.seg_seg(a, b, s1, far) < PASSAGE_R + 3.5:
            return False
    for q1, q2 in _passage_lines(p):
        if sw.seg_seg(a, b, q1, q2) < 2 * PASSAGE_R + 1.5:
            return False
    if p.lodge is not None and sw.rect_seg(p.lodge, a, b) < PASSAGE_R + 3:
        return False
    for pen in p.pens:
        if sw.rect_seg(pen.rect, a, b) < PASSAGE_R + PEN_CLEAR:
            return False
    for s in p.surface:
        if sw.rect_seg(s.rect, a, b) < PASSAGE_R + SURFACE_CLEAR:
            return False
    # nothing of another template under its way
    n = int(math.hypot(b[0] - a[0], b[1] - a[1]) * 2) + 2
    for i in range(n + 1):
        x = a[0] + (b[0] - a[0]) * i / n
        z = a[1] + (b[1] - a[1]) * i / n
        for dx in (-2, -1, 0, 1, 2):
            for dz in (-2, -1, 0, 1, 2):
                if math.hypot(dx, dz) > PASSAGE_R + 0.5:
                    continue
                o = p.occupied.get((int(round(x + dx)), int(round(z + dz))))
                if o is not None and o not in own:
                    return False
    return True


def _extra_links(p):
    """More passages: two igloos whose free walls face each other a few blocks apart (the warren)."""
    added = 0
    items = [i for i in p.items if i.role != "surface"]
    for a, b in combinations(items, 2):
        if a.links >= MAX_LINKS or b.links >= MAX_LINKS:
            continue
        if a.court is not None and a.court is b.court:
            continue
        if any((f is a and t is b) or (f is b and t is a) for f, _x, t, _y in p.links):
            continue
        best = None
        for sa in a.free_spots():
            for sb in b.free_spots():
                if stamp.turn_dir(sa.side, 2) != sb.side:
                    continue
                d = math.hypot(sa.outer[0] - sb.outer[0], sa.outer[1] - sb.outer[1])
                if not EXTRA_LEN[0] <= d <= EXTRA_LEN[1]:
                    continue
                vx, vz = sb.outer[0] - sa.outer[0], sb.outer[1] - sa.outer[1]
                ax, az = stamp.STEPS[sa.side]
                if (vx * ax + vz * az) < 0.7 * d:
                    continue
                if best is None or d < best[0]:
                    best = (d, sa, sb)
        if best is None:
            continue
        _d, sa, sb = best
        if not _link_clear(p, sa.outer, sb.outer, (a, b)):
            continue
        a.used.add(sa.outer)
        b.used.add(sb.outer)
        a.links += 1
        b.links += 1
        p.links.append((a, sa.outer, b, sb.outer))
        p.link_spots = getattr(p, "link_spots", {})
        p.link_spots[(id(a), sa.outer)] = sa
        p.link_spots[(id(b), sb.outer)] = sb
        added += 1
    return added


class Surface:
    def __init__(self, item, rect, path):
        self.item, self.rect, self.path = item, rect, path


def _steps_tops(p):
    out = []
    for c, d, _a, _b in p.steps:
        dx, dz = stamp.STEPS[d]
        top = c.r + sw.STEPS_LEN + 2
        out.append((c.x + dx * top, c.z + dz * top))
    return out


def _place_surface(p, rng, did, far=False, tries=2500):
    """The shrine or a Rimeberry Patch on the open snow beside the village, its doorway facing the nearest flight of
    steps' top, a straight path between them over the snow (never across a courtyard or a lane: they're dug)."""
    piece = pieces()[did]
    xs = [c.x for c in p.courts] + [i.x for i in p.items]
    zs = [c.z for c in p.courts] + [i.z for i in p.items]
    tops = _steps_tops(p)
    best = None
    for _t in range(tries):
        x = rng.uniform(min(xs) - 40, max(xs) + 40)
        z = rng.uniform(min(zs) - 40, max(zs) + 40)
        top = min(tops, key=lambda t: math.hypot(t[0] - x, t[1] - z))
        door = facing_towards(x, z, top[0], top[1])
        k = stamp.turns_for(door)
        item = Item(piece, round(x), round(z), k, "surface")
        bx1, bz1, bx2, bz2 = piece.turned_box(k)
        rect = (item.x + bx1, item.z + bz1, item.x + bx2, item.z + bz2)
        ex, ez = item.placed.entrance
        path = ((ex, ez), top)
        if not _surface_ok(p, rect, path, item):
            continue
        near = min(sw.rect_dist(rect, dx, dz) - r for o in p.items for dx, dz, r in o.discs)
        near = min([near] + [sw.rect_dist(rect, c.x, c.z) - c.r for c in p.courts])
        score = ((abs(near - SHRINE_APART) if far else near) + math.hypot(ex - top[0], ez - top[1]) * 0.3
                 + rng.uniform(0, 3))
        if best is None or score < best[0]:
            best = (score, item, rect, path)
    if best is None:
        return None
    _s, item, rect, path = best
    _add(p, item)
    s = Surface(item, rect, path)
    p.surface.append(s)
    return s


def _surface_ok(p, rect, path, item):
    for o in p.items:
        for ox, oz, r in o.discs:
            if sw.rect_dist(rect, ox, oz) < r + SURFACE_CLEAR:
                return False
            if sw.seg_dist(ox, oz, *path) < r + 2.0:
                return False
    for c in p.courts:
        if sw.rect_dist(rect, c.x, c.z) < c.r + SURFACE_CLEAR + 2:
            return False
        if sw.seg_dist(c.x, c.z, *path) < c.r + 1.5:
            return False
    for a, b in p.lanes:
        if sw.rect_seg(rect, a, b) < LANE_HALF + SURFACE_CLEAR:
            return False
        if sw.seg_seg(path[0], path[1], a, b) < LANE_HALF + 1.5:
            return False
    for _c, _d, s1, s2 in p.steps:
        dx, dz = s2[0] - s1[0], s2[1] - s1[1]
        Ls = math.hypot(dx, dz) or 1.0
        far = (s2[0] + dx / Ls * 9, s2[1] + dz / Ls * 9)
        if sw.rect_seg(rect, s1, far) < 5:
            return False
    for q1, q2 in _passage_lines(p):
        if sw.rect_seg(rect, q1, q2) < PASSAGE_R + SURFACE_CLEAR:
            return False
        if sw.seg_seg(path[0], path[1], q1, q2) < PASSAGE_R + 1:
            return False
    if p.lodge is not None:
        lx1, lz1, lx2, lz2 = p.lodge
        if rect[0] < lx2 + 6 and rect[2] > lx1 - 6 and rect[1] < lz2 + 6 and rect[3] > lz1 - 6:
            return False
        if sw.rect_seg(p.lodge, *path) < 2:
            return False
    for pen in p.pens:
        if _rects_near(pen.rect, rect, SURFACE_CLEAR) or sw.rect_seg(pen.rect, *path) < 2:
            return False
    for s in p.surface:
        if _rects_near(s.rect, rect, SURFACE_CLEAR) or sw.seg_seg(s.path[0], s.path[1], *path) < 2 or \
                sw.rect_seg(s.rect, *path) < 2 or sw.rect_seg(rect, *s.path) < 2:
            return False
    # never in front of the way in
    main = p.courts[0]
    if rect[3] > main.z and rect[0] < main.x + main.r + 8 and rect[2] > main.x - main.r - 8:
        return False
    for x, z in item.columns():
        for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            if (x + dx, z + dz) in p.occupied:
                return False
    return True


def _rects_near(a, b, gap):
    return a[0] - gap < b[2] and b[0] - gap < a[2] and a[1] - gap < b[3] and b[1] - gap < a[3]


def _place_pen(p, rng, size, kind):
    """sunken_warren's pen placement, round the templates."""
    xs = [c.x - c.r for c in p.courts] + [c.x + c.r for c in p.courts] + [i.x for i in p.items]
    zs = [c.z - c.r for c in p.courts] + [c.z + c.r for c in p.courts] + [i.z for i in p.items]
    main = p.courts[0]
    best = None
    for _t in range(2000):
        w, d = size if rng.random() < 0.5 else (size[1], size[0])
        cx = rng.uniform(min(xs) - 30, max(xs) + 30)
        cz = rng.uniform(min(zs) - 30, max(zs) + 30)
        rect = (cx - w / 2.0, cz - d / 2.0, cx + w / 2.0, cz + d / 2.0)
        near = 1e9
        ok = True
        for i in p.items:
            for ox, oz, r in i.discs:
                dd = sw.rect_dist(rect, ox, oz) - r
                if dd < PEN_CLEAR:
                    ok = False
                    break
                near = min(near, dd)
            if not ok:
                break
        if not ok:
            continue
        for c in p.courts:
            dd = sw.rect_dist(rect, c.x, c.z) - c.r
            if dd < PEN_CLEAR + 2:
                ok = False
                break
            near = min(near, dd)
        if not ok:
            continue
        if any(sw.rect_seg(rect, a, b) < LANE_HALF + PEN_CLEAR + 1 for a, b in p.lanes):
            continue
        if any(sw.rect_seg(rect, a, b) < PASSAGE_R + PEN_CLEAR for a, b in _passage_lines(p)):
            continue
        bad = False
        for _c, _d, s1, s2 in p.steps:
            dx, dz = s2[0] - s1[0], s2[1] - s1[1]
            Ls = math.hypot(dx, dz) or 1.0
            far = (s2[0] + dx / Ls * 11, s2[1] + dz / Ls * 11)
            if sw.rect_seg(rect, s1, far) < 5:
                bad = True
                break
        if bad:
            continue
        if p.lodge is not None:
            lx1, lz1, lx2, lz2 = p.lodge
            if rect[0] < lx2 + 4 and rect[2] > lx1 - 4 and rect[1] < lz2 + 4 and rect[3] > lz1 - 4:
                continue
        if any(_rects_near(s.rect, rect, SURFACE_CLEAR) or sw.rect_seg(rect, *s.path) < 3 for s in p.surface):
            continue
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
    return sw.Pen(rect, facing_towards(pcx, pcz, c.x, c.z), kind)


def plan(seed, n, huntsman=False, shrine=False):
    ps = pieces()
    beds = {d: beds_of(pc) for d, pc in ps.items()}
    for attempt in range(800):
        rng = random.Random(seed * 7919 + attempt)
        ids, patches = choose(random.Random(seed * 31 + attempt // 50), n, huntsman, shrine or huntsman, beds)
        p = _try_plan(rng, ids, patches, huntsman, shrine or huntsman, n)
        if p is not None:
            p.ids = ids
            return p, attempt
    raise SystemExit(f"no plan with {n} igloos for seed {seed}")


def _try_plan(rng, ids, patches, huntsman, shrine, n):
    p = _layout(rng, n, huntsman)
    if p is None:
        return None
    queue = list(ids)
    # the halls at the backs of their courtyards
    first = _hall(p, p.courts[0], queue[0], 180)
    if first is None:
        return None
    queue.remove(first.id)
    if huntsman:
        other = [c for c in p.courts[1:] if c is not getattr(p, "lodge_court", None)] or p.courts[1:]
        c2 = max(other, key=lambda c: math.hypot(c.x, c.z))
        away = math.degrees(math.atan2(c2.x - p.courts[0].x, c2.z - p.courts[0].z))
        second = _hall(p, c2, "long_hall", away)
        if second is None:
            return None
        queue.remove("long_hall")
    # the rest: the big ones first round the rings, about a third behind them, then along the lanes
    queue.sort(key=lambda d: -len(pieces()[d].columns) + rng.uniform(-60, 60))
    want_out = int(round(n * OUTLIER_SHARE))
    ring_want = max(0, len(queue) - want_out)
    courts = list(p.courts)
    rng.shuffle(courts)
    per = max(2, int(math.ceil(ring_want / len(courts))) + 1)
    for c in courts:
        if ring_want <= 0:
            break
        got = _ring(p, rng, c, queue, min(per, ring_want))
        ring_want -= len(got)
    rng.shuffle(queue)
    for did in list(queue):
        if _outlier(p, rng, did) is not None:
            queue.remove(did)
    for did in list(queue):
        if _lane_igloo(p, rng, did) is not None:
            queue.remove(did)
    for c in courts:
        if not queue:
            break
        _ring(p, rng, c, queue, len(queue))
    if queue:
        return None
    _extra_links(p)
    # the pens, then on the snow: the shrine (alone, a little way out), the patches
    pen = _place_pen(p, rng, CORRAL if huntsman else PADDOCK, "corral" if huntsman else "paddock")
    if pen is None:
        return None
    p.pens.append(pen)
    if shrine and _place_surface(p, rng, "snow_angel_shrine", far=True) is None:
        return None
    for _k in range(patches):
        if _place_surface(p, rng, "rimeberry_patch") is None:
            return None
    return p


# ---------------------------------------------------------------- building it


class Target:
    """A room to walk to (for the checks): its cells, its name, where it is."""

    def __init__(self, room, name, cx, cz, r=6):
        self.room, self.name, self.cx, self.cz, self.r = room, name, cx, cz, r
        self.base = float(SINK + 1)


def build(number, seed, igloos, huntsman=False, shrine=False, name=None):
    p, attempt = plan(seed, igloos, huntsman, shrine)
    xs, zs = [], []
    for c in p.courts:
        xs += [c.x - c.r, c.x + c.r]
        zs += [c.z - c.r, c.z + c.r]
    for i in p.items:
        bx1, bz1, bx2, bz2 = i.piece.turned_box(i.k)
        xs += [i.x + bx1, i.x + bx2]
        zs += [i.z + bz1, i.z + bz2]
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
    v = Village(W, D, H=H, G=G, seed=seed)
    g = v.g
    g.entities = []
    m = v.m
    ox, oz = int(math.floor(x0)), int(math.floor(z0))

    def X(x):
        return int(round(x - ox))

    def Z(z):
        return int(round(z - oz))

    courts = [(X(c.x), Z(c.z), c.r, c) for c in p.courts]
    dug_floor = {}
    # -- the courtyards, the lanes, the lodge's lane and stairs, the steps (sunken_warren's)
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
    for a, b in p.lanes:
        A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
        for x in range(min(A[0], B[0]) - 3, max(A[0], B[0]) + 4):
            for z in range(min(A[1], B[1]) - 3, max(A[1], B[1]) + 4):
                if 0 <= x < W and 0 <= z < D and sw.seg_dist(x, z, A, B) <= LANE_HALF:
                    for y in range(FLOOR, 1):
                        m.dig(x, y, z)
                    dug_floor[(x, z)] = FLOOR
    stair_cells = set()               # every cell of a staircase and its landing (x, z)
    landings = []                     # the landings' cells (x, z), for the check that the stairs meet the ground
    lodge_box = None
    if p.lodge is not None:
        lx1, lz1 = X(p.lodge[0]), Z(p.lodge[1])
        lodge_box = (lx1, lz1, lx1 + LODGE_SIZE[0] - 1, lz1 + LODGE_SIZE[1] - 1)
        lane_x = lx1 + LODGE_DOOR_X
        ze = lodge_box[3] + 3
        (_ax, az_), _b = p.lodge_lane
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
        # its landing, at the top of the stairs, north of the top step
        for z in range(ze - LANDING_LONG, ze + 3):
            for x in range(lane_x - LANDING_HALF, lane_x + LANDING_HALF + 1):
                if z < ze:
                    _landing(g, x, z)
                    landings.append((x, z))
                _clear_over(g, x, z)
                stair_cells.add((x, z))
        for z in range(ze + 3, Z(az_) + 1):
            for x in range(lane_x - 1, lane_x + 2):
                if (x, z) not in dug_floor:
                    for y in range(FLOOR, 1):
                        m.dig(x, y, z)
                    dug_floor[(x, z)] = FLOOR
    steps_cells = []
    for c, d, a, b in p.steps:
        cx, cz = X(c.x), Z(c.z)
        f = Frame(g, cx, cz, d)
        edge = int(math.floor(c.r))
        for k in range(0, sw.STEPS_LEN + 2):
            vv = edge - 1 + k
            top = FLOOR + k - 1
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
    # -- a landing at the top of every courtyard's steps: past its top step (at the ground), LANDING_LONG rows of snow
    # on solid snow, nothing left over it or over the top steps
    for c, d, _a, _b in p.steps:
        f = Frame(g, X(c.x), Z(c.z), d)
        top_v = int(math.floor(c.r)) - FLOOR              # (the top step: the one at the ground, y 0)
        for vv in range(top_v - 1, top_v + LANDING_LONG + 1):
            for u in range(-LANDING_HALF, LANDING_HALF + 1):
                x, z = f.xz(u, vv)
                if not (0 <= x < W and 0 <= z < D):
                    continue
                if vv > top_v:
                    _landing(g, x, z)
                    landings.append((x, z))
                _clear_over(g, x, z)
                stair_cells.add((x, z))
    stair_cells |= set(steps_cells)
    # -- the templates, block for block (shifted into the grid)
    owner = {}
    placed = []
    for it in p.items:
        it.placed = stamp.Placed(it.piece, X(it.x), Z(it.z), it.k, it.placed.dy)
        if it.role == "surface" and lodge_box is not None and False:
            pass
        stamp.stamp(g, it.placed, owner)
        placed.append(it.placed)
    lodge_items = []
    if lodge_box is not None:
        lx1, lz1 = lodge_box[0], lodge_box[1]
        for did, (ax, az) in (("huntsman_lodge", LODGE_AT), ("alpha_moose_barn", BARN_AT)):
            pl = stamp.Placed(pieces()[did], lx1 + ax, lz1 + az, 0, 0)
            stamp.stamp(g, pl, owner)
            lodge_items.append(pl)
    owned_cols = {(x, z) for (x, y, z) in owner}
    # -- each sunken doorway's way out to its courtyard or lane, dug where it isn't already
    approach_cells = []
    for it in p.items:
        if it.role not in ("ring", "lane"):
            continue
        ex, ez = it.placed.entrance
        if it.role == "ring":
            tx, tz = X(it.court.x), Z(it.court.z)
        else:
            (a, b) = it.lane
            A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
            tx, tz = _nearest_on(ex, ez, A, B)
        if not _approach(m, dug_floor, owned_cols, (ex, ez), (tx, tz), W, D, approach_cells):
            raise SystemExit(f"{it.id}'s doorway at {ex},{ez} has no way out to its "
                             f"{'courtyard' if it.role == 'ring' else 'lane'}")
    # -- the covered passages
    passage_floor = []
    cut_cells = []
    for frm, a, to, b in p.links:
        A = (a[0] - ox, a[1] - oz)
        B = (b[0] - ox, b[1] - oz)
        passage_floor += _carve_passage(g, m, A, B, owner)
        spot = p.link_spots[(id(to), b)]
        cut_cells += _cut(g, spot, ox, oz, owner)
        if frm.role != "warren" or a != frm.placed.entrance:
            fspot = p.link_spots.get((id(frm), a))
            if fspot is not None:
                cut_cells += _cut(g, fspot, ox, oz, owner)
    # the dug spaces lined with snow (the world's ground never shows), then the floors
    sw._line(g)
    for (x, z), fy in dug_floor.items():
        s = g.get(x, fy - 1, z)
        if s is None:
            lane = not any(math.hypot(x - cx, z - cz) <= r + 1.5 for cx, cz, r, _c in courts)
            g.set(x, fy - 1, z, sw.LANE_FLOOR if lane else sw.COURT_FLOOR, force=True)
    sw._line(g)
    buried = stamp.bury_windows(g, placed)
    sw._settle_snow(g)
    for cx, cz, r, c in courts:
        props.fire_pit(YFrame(Frame(g, cx, cz, "south"), FLOOR - 1), r=1, benches=True, spit=True, ground=False,
                       bench_r=3, bench="dross:frostbitten_log" if (cx + cz) % 2 else "stripped_spruce_log")
    # -- the pens (fences two high) and their animals
    pens = [_build_pen(g, pen, X, Z, random.Random(seed * 31 + k)) for k, pen in enumerate(p.pens)]
    # -- the paths over the snow to the shrine and the patches, lanterns along the shrine's
    rng = random.Random(seed)
    path_cells = []
    for s in p.surface:
        (ex, ez), top = s.path
        a = (ex - ox, ez - oz)
        b = (top[0] - ox, top[1] - oz)
        path_cells += _surface_path(g, a, b)
    # -- decorations (each a 1 in 2 chance) and the ring of lights (always there)
    dec = Decor(g)
    blocked = _occupied_mask(g, owned_cols, passage_floor)
    for (x, z) in steps_cells:
        dec.keep_out(x, z, 1)
    for cx, cz, r, c in courts:
        dec.keep_out(cx, cz, 4)
    for pen in pens:
        x1, z1, x2, z2 = pen["box"]
        dec.keepout[max(0, x1 - 2):x2 + 3, max(0, z1 - 2):z2 + 3] = True
    for c, d, _a, _b in p.steps:
        f = Frame(g, X(c.x), Z(c.z), d)
        edge = int(math.floor(c.r))
        for vv in range(edge + sw.STEPS_LEN, edge + sw.STEPS_LEN + 9):
            for u in range(-2, 3):
                x, z = f.xz(u, vv)
                if 0 <= x < W and 0 <= z < D:
                    dec.keepout[x, z] = True
    for a, b in p.lanes:
        A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
        for x in range(max(0, min(A[0], B[0]) - 4), min(W, max(A[0], B[0]) + 5)):
            for z in range(max(0, min(A[1], B[1]) - 4), min(D, max(A[1], B[1]) + 5)):
                if sw.seg_dist(x, z, A, B) <= LANE_HALF + 1.0 and \
                        all(math.hypot(x - cx, z - cz) > r - 3.5 for cx, cz, r, _c in courts):
                    dec.keepout[x, z] = True
    for (x, z) in approach_cells + path_cells:
        dec.keep_out(x, z, 1)
    for (x, y, z) in passage_floor:
        dec.keep_out(x, z, 2)
    for pl in placed:
        ex, ez = pl.entrance
        dec.keep_out(ex, ez, 2)
    for c in cut_cells:
        dec.keep_out(c[0], c[2], 2)
    lodge_info = None
    if lodge_box is not None:
        lodge_info = _lodge_yard(g, dec, lodge_box, lodge_items, rng)
    for (x, z) in stair_cells:
        dec.keep_out(x, z, 0)
    ring_lights = _light_ring(g, dec, p, X, Z, blocked, rng)
    inside_lights = _light_inside(g, dec, p, X, Z, courts, blocked,
                                  (lane_x, ze) if lodge_box is not None else None)
    _shrine_lanterns(g, dec, p, X, Z, ox, oz)
    sw._entrance_pair(g, dec, p, X, Z, rng)
    _decorate(g, dec, courts, p, X, Z, rng, blocked, stair_cells)
    sw._line(g)
    blocked2 = dec.taken | dec.keepout

    def open_ground(x, z):
        return not blocked2[x, z] and not blocked[x, z] and (x, z) not in dug_floor
    props.drifts(g, 0, area=open_ground, salt=seed % 97)
    # -- what the checks need
    c0, d0, _a, _b = p.steps[0]
    f = Frame(g, X(c0.x), Z(c0.z), d0)
    sx, sz = f.xz(0, int(math.floor(c0.r)) + sw.STEPS_LEN + 2)
    targets = []
    rooms = []
    for it in p.items:
        cells = it.placed.rooms
        cx, cz = it.placed.ax, it.placed.az
        targets.append(Target(cells, it.id, cx, cz, it.piece.domes[0][2]))
        rooms.append([c for c in cells if c[1] == 1 + it.placed.dy])
        if it.role == "surface":
            targets[-1].base = 1.0
    for pl in lodge_items:
        targets.append(Target(pl.rooms, pl.id, pl.ax, pl.az, pl.piece.domes[0][2]))
        targets[-1].base = 1.0
        rooms.append([c for c in pl.rooms if c[1] == 1])
    # the cells the village itself lights (its lanterns go only in the passages and the dug ways: the templates are lit
    # as designed) and every floor the checks look at
    # (the doorways' dug ways are open to the sky, like the lanes and courtyards: not rooms)
    light_cells = set(passage_floor) | set(cut_cells)
    # (a room's floor counts where a player could stand, two high, as the igloos' own check counts it)
    rooms = [[c for c in room if walk.standable(g, *c)] for room in rooms]
    floors = set(light_cells)
    for room in rooms:
        floors |= set(room)
    floors = lighting.floors(g, None, floors)
    light_cells = lighting.floors(g, None, light_cells)
    rooms = [[c for c in room if c in floors] for room in rooms]
    rooms.append(sorted(set(passage_floor) & floors))
    rooms = [room for room in rooms if room]
    keep_clear = set()
    for pl in placed + lodge_items:
        ex, ez = pl.entrance
        keep_clear |= {(ex + a, ez + b) for a in (-1, 0, 1) for b in (-1, 0, 1)}
    for c in cut_cells:
        keep_clear |= {(c[0] + a, c[2] + b) for a in (-1, 0, 1) for b in (-1, 0, 1)}
    kinds = {}
    for it in p.items:
        kinds[it.id] = kinds.get(it.id, 0) + 1
    info = {"number": number, "id": name or f"sunken_warren_{number:02d}", "name": "Sunken Warren",
            "mood": f"{len(p.courts)} sunken courtyards, {igloos} igloos" + (", the shrine" if (shrine or huntsman) else "")
                    + (", the Huntsman's lodge" if huntsman else ""),
            "igloos": igloos, "courts": len(p.courts), "huntsman": huntsman, "plan_attempt": attempt,
            "lanes": len(p.lanes), "loops": p.loops, "passages": len(p.links), "kinds": kinds,
            "roles": {r: sum(1 for i in p.items if i.role == r) for r in ("ring", "lane", "warren", "surface")},
            "buried_windows": buried, "ring_lights": ring_lights, "inside_lights": inside_lights}
    # (the courtyards', lanes' and steps' floors, where one stands in them: their lamps light them, the checks look)
    open_floors = lighting.floors(g, None, {(x, y, z) for (x, z), y in dug_floor.items()})
    extras = {"decor": dec, "plan": p, "entrance": (sx, 1, sz), "targets": targets, "courts": courts,
              "open_floors": open_floors, "landings": landings,
              "lodge": lodge_info, "light_cells": light_cells, "floors": floors, "rooms": rooms,
              "keep_clear": keep_clear, "pens": pens, "massing": None, "placed": placed + lodge_items}
    return g, info, extras


def _approach(m, dug_floor, owned_cols, start, target, W, D, out):
    """A doorway's dug way out: from the cell in front of it straight towards `target` (its courtyard's middle, or
    the nearest point of its lane), 3 wide, until it meets a dug floor. Returns False if it never does."""
    sx, sz = start
    tx, tz = target
    L = math.hypot(tx - sx, tz - sz)
    if L < 0.5:
        return True
    ux, uz = (tx - sx) / L, (tz - sz) / L
    steps = int(min(L, APPROACH_MAX + 4) * 2) + 1
    mine = set()
    for i in range(steps + 1):
        fx, fz = sx + ux * i * 0.5, sz + uz * i * 0.5
        x, z = int(round(fx)), int(round(fz))
        if i > 0 and (x, z) not in mine and dug_floor.get((x, z)) is not None:
            return True
        for ax in (-1, 0, 1):
            for az in (-1, 0, 1):
                xx, zz = x + ax, z + az
                if math.hypot(xx - fx, zz - fz) > 1.05:
                    continue
                if (xx, zz) in owned_cols or not (0 <= xx < W and 0 <= zz < D):
                    continue
                if (xx, zz) not in dug_floor:
                    for y in range(FLOOR, 1):
                        m.dig(xx, y, zz)
                    dug_floor[(xx, zz)] = FLOOR
                    mine.add((xx, zz))
                    out.append((xx, zz))
    return False


def _nearest_on(x, z, a, b):
    (ax, az), (bx, bz) = a, b
    dx, dz = bx - ax, bz - az
    L = dx * dx + dz * dz
    t = 0.0 if L == 0 else max(0.0, min(1.0, ((x - ax) * dx + (z - az) * dz) / L))
    return ax + t * dx, az + t * dz


def _carve_passage(g, m, A, B, owner):
    """A covered passage from A to B (grid columns): its room 3 wide and 3 tall on the courtyards' level, a floor of
    snow, walls and a roof of snow where the ground isn't (the roof stands a block over the snow). Never touches a
    template's own blocks. Returns its floor cells."""
    floor = []
    (ax, az), (bx, bz) = A, B
    r = int(math.ceil(PASSAGE_R)) + 1
    for x in range(int(min(ax, bx)) - r, int(max(ax, bx)) + r + 1):
        for z in range(int(min(az, bz)) - r, int(max(az, bz)) + r + 1):
            if not g.inb(x, 0, z):
                continue
            d = sw.seg_dist(x, z, A, B)
            if d > PASSAGE_R:
                continue
            inside = d <= PASSAGE_IN
            for y in range(FLOOR - 1, 2):
                if (x, y, z) in owner:
                    continue
                cur = g.get(x, y, z)
                if inside and FLOOR <= y <= 0:
                    g.set(x, y, z, AIR, force=True)
                    m.dug[x, y + g.G, z] = True
                elif inside and y == FLOOR - 1:
                    if cur is None or mc.is_air(cur):
                        g.set(x, y, z, "snow_block", force=True)
                elif cur is None or (y >= 1 and mc.is_air(cur)):
                    if y == 1 or y <= 0:
                        g.set(x, y, z, "snow_block", force=True)
            if inside and (x, FLOOR, z) not in owner:
                floor.append((x, FLOOR, z))
    return floor


def _cut(g, spot, ox, oz, owner):
    """A passage's way into a template's room: its wall cells cut, two high, a floor under them."""
    out = []
    for (cx, cz) in spot.cut:
        x, z = cx - ox, cz - oz
        for y in (FLOOR, FLOOR + 1):
            g.set(x, y, z, AIR, force=True)
        s = g.get(x, FLOOR - 1, z)
        if s is None or mc.is_air(s) or mc.kind(s) != "full":
            g.set(x, FLOOR - 1, z, "snow_block", force=True)
        out.append((x, FLOOR, z))
    return out


def _surface_path(g, a, b):
    """A path of trodden snow over the ground from a to b, 3 wide."""
    cells = []
    n = int(math.hypot(b[0] - a[0], b[1] - a[1]) * 2) + 2
    for i in range(n + 1):
        x = a[0] + (b[0] - a[0]) * i / n
        z = a[1] + (b[1] - a[1]) * i / n
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                xx, zz = int(round(x + dx)), int(round(z + dz))
                if g.inb(xx, 0, zz) and g.get(xx, 0, zz) is None and g.get(xx, 1, zz) is None:
                    g.set(xx, 0, zz, props.PATH)
                    cells.append((xx, zz))
    return cells


def _occupied_mask(g, owned_cols, passage_floor):
    import numpy as np
    mask = np.zeros((g.W, g.D), bool)
    for (x, z) in owned_cols:
        if 0 <= x < g.W and 0 <= z < g.D:
            mask[max(0, x - 2):x + 3, max(0, z - 2):z + 3] = True
    for (x, _y, z) in passage_floor:
        mask[max(0, x - 3):x + 4, max(0, z - 3):z + 4] = True
    return mask


# ---------------------------------------------------------------- the pens


def _build_pen(g, pen, X, Z, rng):
    """A pen's fence, two high all round (spruce rails, posts of Frostbitten Log or bone, skulls on the corral's
    corners), a gate facing the village between taller posts; a floor of trodden snow; hay and a water trough at
    least PEN_KEEP + 1 blocks in from the fence (an animal on anything near a fence could jump it: they got out in the
    user's look, October 20); its animals (penned: they never go for anyone)."""
    x1, z1 = X(pen.rect[0]), Z(pen.rect[1])
    x2, z2 = X(pen.rect[2]), Z(pen.rect[3])
    corral = pen.kind == "corral"
    mx, mz = (x1 + x2) // 2, (z1 + z2) // 2
    gates = (-1, 0, 1) if corral else (0,)
    gate_cells = set()
    for k in gates:
        gate_cells.add({"north": (mx + k, z1), "south": (mx + k, z2), "west": (x1, mz + k), "east": (x2, mz + k)}[pen.gate])
    along_x = pen.gate in ("north", "south")
    post_every = 4
    for x, z in props.rect_cells(x1, z1, x2, z2):
        if (x, z) in gate_cells:
            g.set(x, 1, z, f"spruce_fence_gate[facing={pen.gate},in_wall=false,open=false,powered=false]", force=True)
            continue
        corner = x in (x1, x2) and z in (z1, z2)
        post = corner or ((x - x1) % post_every == 0 and z in (z1, z2)) or ((z - z1) % post_every == 0 and x in (x1, x2))
        if post:
            mat = "bone_block[axis=y]" if corral and (x + z) % 8 < 4 else "dross:frostbitten_log[axis=y]"
            g.set(x, 1, z, mat, force=True)
            g.set(x, 2, z, mat, force=True)
            if corral and corner:
                out = "north" if z == z1 else "south"
                g.set(x, 3, z, f"dross:rimehorn_skull[facing={out},wall=false]", force=True)
        else:
            g.set(x, 1, z, "spruce_fence", force=True)
            g.set(x, 2, z, "spruce_fence", force=True)
    for k in (min(gates) - 1, max(gates) + 1):
        gx, gz = (mx + k, z1 if pen.gate == "north" else z2) if along_x else (x1 if pen.gate == "west" else x2, mz + k)
        for y in (1, 2, 3):
            g.set(gx, y, gz, "dross:frostbitten_log[axis=y]", force=True)
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            g.set(x, 0, z, sw.PEN_FLOOR, force=True)
    keep = PEN_KEEP + 1
    hx, hz = x1 + keep, z1 + keep
    hay = [(hx, hz), (hx + 1, hz), (hx, hz + 1)]
    if corral:
        hay += [(hx + 2, hz), (hx, hz + 2), (hx + 1, hz + 1)]
    for k, (x, z) in enumerate(hay):
        g.set(x, 1, z, "hay_block[axis={}]".format("x" if k % 2 else "z"), force=True)
    g.set(hx, 2, hz, "hay_block[axis=y]", force=True)
    tl = 7 if corral else 4
    tz_ = z2 - keep - 1
    tx1 = x2 - keep - 1 - tl
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
    for x in range(x1 + 1, x2):
        for z in range(z1 + 1, z2):
            if min(x - x1, x2 - x, z - z1, z2 - z) > PEN_KEEP:
                continue
            st = g.get(x, 1, z)
            if st is not None and not mc.is_air(st) and mc.kind(st) not in ("carpet", "cross"):
                raise SystemExit(f"a {mc.base(st)} at {x}, {z} within {PEN_KEEP} of a {pen.kind}'s fence: an animal "
                                 f"could climb out over it")
    taken = set(hay) | {(x, z) for x in range(tx1 - 1, tx1 + tl + 3) for z in range(tz_ - 2, tz_ + 3)}
    gap = 2.6 if corral else 2.0
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
        e = animal(kind, px, 1.0, pz, yaw=round(rng.uniform(-180, 180), 1), Penned=True, **flags)
        g.entities.append(e)
        animals.append(e)
    return {"box": (x1, z1, x2, z2), "kind": pen.kind, "gate": pen.gate, "animals": animals, "trough": trough}


# ---------------------------------------------------------------- the Huntsman's yard


def _lodge_yard(g, dec, box, lodge_items, rng):
    """His yard before the lodge and the barn: a path from the top of the lane's stairs to his door between skull
    posts, under a bone arch; a path on to the barn's door; the roasting fire, racks of meat, the butcher's block,
    bones, skulls, hides, a woodpile, the weapon rack, a totem, a sled (each a 1 in 2 chance)."""
    x1, z1, x2, z2 = box
    lodge, barn = lodge_items
    lx, lz = lodge.entrance
    bx, bz = barn.entrance
    lane_x = x1 + LODGE_DOOR_X
    turn_z = lz + 5
    cells = []
    cells += _surface_path(g, (lane_x, z2), (lx, lz))
    cells += _surface_path(g, (lx, turn_z), (bx, turn_z))
    cells += _surface_path(g, (bx, turn_z), (bx, bz))
    for (x, z) in cells:
        dec.keep_out(x, z, 1)
    dec.keep_out(lx, lz, 2)
    dec.keep_out(bx, bz, 2)
    dec.place("bone arch", lambda s: props.bone_arch(Frame(s, lane_x, z2 - 4, "south"), width=5, height=6), overhead=True)
    yard = []
    for zz in range(lz + 3, z2 - 6, 4):
        for side in (-3, 3):
            # (his trophies: moose and Rimehorn skulls by turns down his path)
            yard.append(("skull post", lambda s, zz=zz, side=side: props.skull_post(
                Frame(s, lane_x + side, zz, "east" if side < 0 else "west"), height=2,
                skull="moose_skull" if (zz // 4) % 2 == 0 else "rimehorn_skull")))
    yard += [
        ("roasting fire", lambda s: props.fire_pit(Frame(s, x1 + 31, z2 - 6, "south"), r=1, benches=True, spit=True,
                                                   bench_r=3)),
        ("meat rack", lambda s: props.meat_rack(Frame(s, x1 + 4, z2 - 10, "east"), length=4)),
        ("meat rack", lambda s: props.meat_rack(Frame(s, x1 + 4, z2 - 4, "east"), length=4)),
        ("butcher's block", lambda s: props.butcher_block(Frame(s, x1 + 10, z2 - 3, "north"))),
        ("bone pile", lambda s: props.bone_pile(Frame(s, x1 + 9, z2 - 9, "south"), big=True)),
        ("skull pile", lambda s: props.skull_pile(Frame(s, x1 + 24, z2 - 11, "south"))),
        ("hide rack", lambda s: props.hide_rack(Frame(s, x1 + 40, z2 - 4, "south"), colours=("gray", "brown"))),
        ("hide frame", lambda s: props.hide_frame(Frame(s, x1 + 47, z2 - 4, "south"), colour="light_gray")),
        ("woodpile", lambda s: props.woodpile(Frame(s, x1 + 56, z2 - 4, "north"), length=3)),
        ("weapon rack", lambda s: props.weapon_rack(Frame(s, x1 + 24, z2 - 3, "north"), n=4)),
        ("totem", lambda s: props.totem(Frame(s, x1 + 2, z2 - 15, "south"), height=6)),
        ("sled", lambda s: props.sled(Frame(s, x1 + 52, z2 - 9, "west"))),
        ("antler post", lambda s: props.antler_post(Frame(s, lane_x - 6, z2 - 2, "south"), height=3)),
        ("meat rack", lambda s: props.meat_rack(Frame(s, x1 + 58, z2 - 9, "south"), length=4)),
        ("bone pile", lambda s: props.bone_pile(Frame(s, x1 + 36, z2 - 10, "south"), big=False)),
    ]
    for name, draw in yard:
        dec.place(name, draw)
    dec.keepout[max(0, x1 - 1):x2 + 2, max(0, z1 - 1):z2 + 2] = True
    # where he and the Alpha Moose wait at home (the user, October 23, 2026: "make sure the hunter spawns in his
    # village"): his yard, on the path out from his door to where it turns to the barn
    return {"box": box, "domes": [], "placed": lodge_items, "home": (lx, turn_z - 1)}


# ---------------------------------------------------------------- the ring of lights and the shrine's path

RING_LIGHTS = ("antler_lamp", "stone_brazier", "snow_lantern", "bone_lamp", "lantern_arch", "ice_pillar", "skull_lamp")


def _light_ring(g, dec, p, X, Z, blocked, rng):
    """A ring of lights round the whole village, RING_OUT outside everything, one every RING_EVERY blocks (always
    there: they're the village's own, not decorations): one of the seven light designs (the user's "a perimeter of
    lights around the base, but you give different light designs"), two taking turns, picked for the village."""
    from igloo_designs import decor_new
    draws = {d[0]: d[3] for d in decor_new.LIGHTS}
    a, b = rng.sample(RING_LIGHTS, 2)
    pts = []
    for c in p.courts:
        for k in range(16):
            t = k / 16 * 2 * math.pi
            pts.append((X(c.x) + math.cos(t) * (c.r + RING_OUT), Z(c.z) + math.sin(t) * (c.r + RING_OUT)))
    for it in p.items:
        for x, z, r in it.discs:
            for k in range(12):
                t = k / 12 * 2 * math.pi
                pts.append((X(x) + math.cos(t) * (r + RING_OUT), Z(z) + math.sin(t) * (r + RING_OUT)))
    rects = [pen.rect for pen in p.pens] + [s.rect for s in p.surface]
    if p.lodge is not None:
        rects.append(p.lodge)
    for x1, z1, x2, z2 in rects:
        for x, z in ((x1, z1), (x1, z2), (x2, z1), (x2, z2)):
            sx = -1 if x == x1 else 1
            sz = -1 if z == z1 else 1
            pts.append((X(x) + sx * RING_OUT, Z(z) + sz * RING_OUT))
    hull = _hull(pts)
    ring = Decor(g)
    ring.keepout = dec.keepout
    ring.taken = dec.taken
    placed = 0
    per = 0
    for (ax, az), (bx, bz) in zip(hull, hull[1:] + hull[:1]):
        L = math.hypot(bx - ax, bz - az)
        n = max(1, int(L // RING_EVERY))
        for i in range(n):
            t = (i + 0.5) / n
            x, z = int(round(ax + (bx - ax) * t)), int(round(az + (bz - az) * t))
            if not (3 <= x < g.W - 3 and 3 <= z < g.D - 3) or blocked[x, z]:
                continue
            # facing out, away from the village's middle
            cx, cz = g.W / 2, g.D / 2
            front = facing_towards(cx, cz, x, z)
            draw = draws[a if per % 2 == 0 else b]
            if ring.place("ring light", lambda s, x=x, z=z, front=front, draw=draw: draw(Frame(s, x, z, front)), margin=1):
                placed += 1
                per += 1
    return placed


def _hull(pts):
    pts = sorted(set((round(x, 2), round(z, 2)) for x, z in pts))
    if len(pts) <= 2:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for q in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], q) <= 0:
            lower.pop()
        lower.append(q)
    for q in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], q) <= 0:
            upper.pop()
        upper.append(q)
    return lower[:-1] + upper[:-1]


def _shrine_lanterns(g, dec, p, X, Z, ox, oz):
    """Lantern posts either side of the path to the shrine, every 6 blocks (the village's own: always there)."""
    ring = Decor(g)
    ring.keepout = dec.keepout
    ring.taken = dec.taken
    for s in p.surface:
        if s.item.id != "snow_angel_shrine":
            continue
        (ex, ez), top = s.path
        a = (ex - ox, ez - oz)
        b = (top[0] - ox, top[1] - oz)
        L = math.hypot(b[0] - a[0], b[1] - a[1]) or 1.0
        ux, uz = (b[0] - a[0]) / L, (b[1] - a[1]) / L
        t = 3.0
        while t < L - 2:
            for side in (-1, 1):
                x = int(round(a[0] + ux * t - uz * side * 3))
                z = int(round(a[1] + uz * t + ux * side * 3))
                front = facing_towards(0, 0, uz * side, -ux * side)
                ring.place("shrine lantern", lambda q, x=x, z=z, fr=front: props.lantern_post(Frame(q, x, z, fr),
                                                                                           height=3, arm=False),
                           margin=0)
            t += 6.0


# ---------------------------------------------------------------- decorations


def _ground_items(rng):
    """sunken_warren's, with windbreaks four times as often (the user: "for the windbreaks I kind of like it, make
    sure to include those a lot")."""
    items = sw._ground_items(rng)
    for _k in range(3):
        items.append(("windbreak", lambda f: props.windbreak(f, length=rng.choice((4, 5, 6)))))
    return items


def _landing(g, x, z):
    """A cell of a staircase's landing: snow at the ground (y 0) and solid snow under it, LANDING_DEEP down, where
    nothing else of the village is (it only replaces snow, Hoarfrost or gravel at the ground)."""
    for y in range(-LANDING_DEEP + 1, 1):
        cur = g.get(x, y, z)
        if cur is None or (y == 0 and mc.base(cur) in LANDING_SOFT):
            g.set(x, y, z, "snow_block", force=True)


def _clear_over(g, x, z):
    """Nothing over a step or a landing cell, CLEAR_OVER up from the ground (where the village has nothing there)."""
    for y in range(1, CLEAR_OVER + 1):
        if g.get(x, y, z) is None:
            g.set(x, y, z, AIR, force=True)


def _light_inside(g, dec, p, X, Z, courts, blocked, lodge_stairs=None):
    """The lamps inside the village (always there: the village's own, not decorations): tall poles round each
    courtyard near its rim, facing its middle, and poles along each lane on its banks by turns, their arms over it.
    Returns how many."""
    lamps = Decor(g)
    lamps.keepout = dec.keepout
    lamps.taken = dec.taken
    placed = 0
    for cx, cz, r, _c in courts:
        n = max(4, int(round(2 * math.pi * r / COURT_LAMP_EVERY)))
        d = r - COURT_LAMP_IN
        for i in range(n):
            for nudge in (0.0, 0.2, -0.2, 0.4, -0.4, 0.6, -0.6):
                a = 2 * math.pi * (i + 0.5 + nudge) / n
                x, z = int(round(cx + math.sin(a) * d)), int(round(cz + math.cos(a) * d))
                front = facing_towards(x, z, cx, cz)
                if lamps.place("court lamp", lambda s, x=x, z=z, front=front: props.court_lamp(
                        YFrame(Frame(s, x, z, front), FLOOR - 1)), margin=1):
                    placed += 1
                    break
    for a, b in p.lanes:
        A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
        L = math.hypot(B[0] - A[0], B[1] - A[1])
        if L < 1:
            continue
        ux, uz = (B[0] - A[0]) / L, (B[1] - A[1]) / L
        side = 1
        t = LANE_LAMP_EVERY / 2.0
        while t < L:
            mx, mz = A[0] + ux * t, A[1] + uz * t
            if all(math.hypot(mx - cx, mz - cz) > r + 1.0 for cx, cz, r, _c in courts):
                for sd in (side, -side):
                    px, pz = -uz * sd, ux * sd
                    x, z = int(round(mx + px * LANE_LAMP_OUT)), int(round(mz + pz * LANE_LAMP_OUT))
                    if not (0 <= x < g.W and 0 <= z < g.D) or blocked[x, z]:
                        continue
                    front = facing_towards(x, z, mx, mz)
                    if lamps.place("lane lamp", lambda s, x=x, z=z, front=front: props.lane_lamp(Frame(s, x, z, front)),
                                   margin=1, overhead=True):
                        placed += 1
                        break
            side = -side
            t += LANE_LAMP_EVERY
    # a lantern post either side of every staircase's landing
    posts = []
    for c, d, _a, _b in p.steps:
        f = Frame(g, X(c.x), Z(c.z), d)
        top_v = int(math.floor(c.r)) - FLOOR
        posts += [(f.xz(u, top_v + 1), d) for u in (-LANDING_HALF - 1, LANDING_HALF + 1)]
    if lodge_stairs is not None:
        lane_x, ze = lodge_stairs
        posts += [((lane_x + u, ze - 1), "north") for u in (-LANDING_HALF - 1, LANDING_HALF + 1)]
    for (x, z), d in posts:
        if 0 <= x < g.W and 0 <= z < g.D and lamps.place(
                "steps lamp", lambda s, x=x, z=z, d=d: props.lantern_post(Frame(s, x, z, d), height=3, arm=False),
                margin=0):
            placed += 1
    return placed


def _decorate(g, dec, courts, p, X, Z, rng, blocked, stair_cells=()):
    for k, (cx, cz, r, c) in enumerate(courts):
        dy = FLOOR - 1
        want = int(r * 1.15) + 3
        placed = 0
        items = sw._court_items(rng)
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
    for a, b in p.lanes:
        A, B = (X(a[0]), Z(a[1])), (X(b[0]), Z(b[1]))
        L = math.hypot(B[0] - A[0], B[1] - A[1])
        if L < 1:
            continue
        ux, uz = (B[0] - A[0]) / L, (B[1] - A[1]) / L
        front = facing_towards(0, 0, ux, uz)
        ts = [t for t in range(0, int(L) + 1)
              if all(math.hypot(A[0] + ux * t - cx, A[1] + uz * t - cz) > r + 4 for cx, cz, r, _c in courts)]
        if not ts:
            continue
        t0, t1 = ts[0], ts[-1]
        n_arch = max(1, int((t1 - t0) // 13))
        for i in range(n_arch):
            t = t0 + (t1 - t0) * (i + 0.5) / n_arch
            x, z = int(round(A[0] + ux * t)), int(round(A[1] + uz * t))
            if any(abs(x - sx) <= ARCH_CLEAR and abs(z - sz) <= ARCH_CLEAR for sx, sz in stair_cells):
                continue              # (never over or by a staircase: the user saw one glitch the stairs' top)
            for width in (5, 7, 9):
                if dec.place("bone arch", lambda s, x=x, z=z, w=width: props.lane_arch(Frame(s, x, z, front), width=w,
                                                                                    height=6), overhead=True):
                    break
        side = 1
        t = t0 + 3
        while t <= t1 - 2:
            px, pz = -uz * side, ux * side
            off = LANE_HALF + 2.6
            x, z = int(round(A[0] + ux * t + px * off)), int(round(A[1] + uz * t + pz * off))
            arm_front = facing_towards(0, 0, -px, -pz)
            if 0 <= x < g.W and 0 <= z < g.D and not blocked[x, z]:
                dec.place("lane lantern", lambda s, x=x, z=z, fr=arm_front: props.lantern_post(Frame(s, x, z, fr),
                                                                                             height=3, arm=False),
                          margin=1)
            side = -side
            t += 7
    for c, d, _a, _b in p.steps:
        f = Frame(g, X(c.x), Z(c.z), d)
        top = int(math.floor(c.r)) + sw.STEPS_LEN + 1
        for side in (-3, 3):
            x, z = f.xz(side, top)
            dec.place("steps lantern", lambda s, x=x, z=z: props.lantern_post(Frame(s, x, z, d), height=3, arm=False),
                      margin=0)
    n_out = int(len(p.items) * 3.2) + 12
    placed = 0
    items = _ground_items(rng)
    for _t in range(n_out * 40):
        if placed >= n_out:
            break
        x, z = rng.randrange(3, g.W - 3), rng.randrange(3, g.D - 3)
        if any(math.hypot(x - cx, z - cz) <= r + 2.5 for cx, cz, r, _c in courts):
            continue
        if blocked[x, z]:
            continue
        near = min([math.hypot(x - cx, z - cz) - r for cx, cz, r, _c in courts] +
                   [math.hypot(x - X(dx), z - Z(dz)) - r for it in p.items for dx, dz, r in it.discs])
        if near > sw.NEAR_VILLAGE + 2:
            continue
        name, fn = rng.choice(items)
        front = rng.choice(sw.DIRS)
        if dec.place(name, lambda s, x=x, z=z, front=front, fn=fn: fn(Frame(s, x, z, front))):
            placed += 1
