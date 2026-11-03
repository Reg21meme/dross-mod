"""No. 3, Under the Fallen Stones, in five versions (the user, November 2, 2026: "no3 is the best camp", then November
3: "more versions of No. 3"). Every version keeps what No. 3 is: the survivors sheltering in a piece of the Shattered
Spire's broken crown beside its front steps, the Blind Oracle on a stone seat by a humming shard of a Dross frame, the
Ragged Seeker among his salvaged crates and tools, a blue soul fire in front. What changes is the shelter:

  3A  Under the Fallen Stones   the draft, worked up: a slab of the crown on four broken pillars, a windbreak of masonry
  3B  The Leaning Slab          a slab that fell tilted: its low end on the ground, its high end propped on pillars
  3C  The Broken Arch           a fallen piece of the gate's arch, the Oracle seated in it, the Seeker's lean-to beside it
  3D  The Leaning Stones        two slabs fallen against each other, a stone tent open to the road's end
  3E  The Ring of Pillars       a ring of broken pillars round a hollow, its back roofed by a slab, the fire in its middle

All west of the road (x 23-27), in front of the steps (z 50 on). Built by camps.py (its previews and templates).

The user's pick (November 3, 2026: "3A is best for the camps, let's implement that one"): 3A, built at the real hub
(camps.py's DESIGNS); 3B-3E stay here as a record (camps.py --round2 draws them again, into round2/). 3A also says where
the survivors live in it (`survivors`: their rest spots and the places they walk to, template x, y over the ground, z),
checked by survivor_check.
"""
import math

from campkit import (CROWN, FRAME, Mix, ROAD_X, RUIN_FLOOR, STEPS_Z, STONES, bed, floor_box, floor_disc, lantern,
                     mc, put, stairs)
from voxel import chain

BRICK = Mix(("polished_blackstone_bricks", 4), ("cracked_polished_blackstone_bricks", 2), patch=0.4)
CHISELED = "chiseled_polished_blackstone"
WALL = "polished_blackstone_brick_wall"
SUNK_FLOOR = Mix(("coarse_dirt", 3), ("blackstone", 2), ("gravel", 1), ("rooted_dirt", 1), patch=0.5)
LOG = "dross:stripped_silverwood_log"


def bslab(kind="bottom"):
    return f"polished_blackstone_brick_slab[type={kind}]"


def bstair(facing, half="bottom"):
    return stairs("polished_blackstone_brick_stairs", facing, half)


# ---------------------------------------------------------------- pieces every version uses

def pillar(g, x, z, h, y0=1, cap=True):
    """A broken pillar of the spire, `h` blocks from `y0`: cracked bricks, a chiseled capital if it still has one."""
    for y in range(y0, y0 + h):
        put(g, x, y, z, CHISELED if cap and y == y0 + h - 1 else BRICK)


def hang(g, x, z, roof_y, links=1, lamp="soul_lantern"):
    """A lantern hanging on `links` of chain from the full block at `roof_y`."""
    for i in range(1, links + 1):
        put(g, x, roof_y - i, z, chain())
    put(g, x, roof_y - links - 1, z, lantern(lamp, hanging=True))


def shard(g, x, z, h, y0=1):
    """A shard of a Dross frame standing on end (it hums and leaks blue dust in the game)."""
    for y in range(y0, y0 + h):
        put(g, x, y, z, FRAME)


def altar(g, x, z, n, y0=1):
    """A chiseled stone with lit white candles on it."""
    put(g, x, y0, z, CHISELED)
    put(g, x, y0 + 1, z, f"white_candle[candles={n},lit=true]")


def plant(g, x, z, p, y=1):
    """A Silverwood plant on a patch of Silvermoss (so it always has soil under it)."""
    put(g, x, y - 1, z, "dross:silvermoss")
    put(g, x, y, z, p)


def soul_fire(g, x, z, y=1):
    put(g, x, y, z, "dross:village_soul_fire[facing=south,lit=true]")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                put(g, x + dx, y, z + dz, "blackstone_slab[type=bottom]")


def bench(g, x1, z1, x2, z2, y=1):
    """A bench of stripped silverwood logs along x or along z."""
    axis = "x" if z1 == z2 else "z"
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            put(g, x, y, z, f"{LOG}[axis={axis}]")


def woodpile(g, x1, z1, x2, z2, h=2, axis="z"):
    for x in range(x1, x2 + 1):
        for z in range(z1, z2 + 1):
            for y in range(1, h + 1):
                put(g, x, y, z, f"dross:silverwood_log[axis={axis}]")


def masonry(g, cells, hmax, scale=3.0):
    """A windbreak of fallen masonry over `cells`, its height wandering with the noise, a wall's top on it."""
    for (x, z) in cells:
        h = 1 + int(hmax * g.noise3_at(scale, x, 1, z))
        for y in range(1, h + 1):
            put(g, x, y, z, WALL if y == h else CROWN)


def road_stones(g, x1, z1, z2):
    """Stepping stones from x1 over to the road."""
    for x in range(x1, ROAD_X[0]):
        for z in range(z1, z2 + 1):
            put(g, x, 0, z, STONES)


def road_lamp(g, z):
    """A soul lantern on a post of brick wall at the road's west edge."""
    x = ROAD_X[0] - 2
    put(g, x, 1, z, WALL)
    put(g, x, 2, z, WALL)
    put(g, x, 3, z, lantern("soul_lantern"))


def chest(g, x, z, facing, y=1):
    put(g, x, y, z, f"chest[facing={facing},type=single]")


def barrels(g, x, z, h=1, y=1):
    for yy in range(y, y + h):
        put(g, x, yy, z, "barrel[facing=up,open=false]")


def rubble(g, cells):
    """Loose pieces of the crown lying about: (x, z, state) at y 1."""
    for (x, z, s) in cells:
        put(g, x, 1, z, s)


# ---------------------------------------------------------------- 3A  Under the Fallen Stones (the draft, worked up)

def fallen_crown(g):
    """The draft: a slab of the crown on four broken pillars beside the steps, behind a windbreak of masonry; now with
    the Oracle's books and candles round him, a lectern for his tome, the Seeker's smithy and map table outside, a
    woodpile, a stew pot and the slab grown over with Silvermoss."""
    x1, z1, x2, z2 = 5, STEPS_Z + 1, 20, STEPS_Z + 14   # x 5-20, z 50-63
    floor_box(g, x1, z1, x2, z2, RUIN_FLOOR)
    road_stones(g, x2 - 1, z1 + 6, z1 + 8)
    for (x, z) in ((8, z1), (15, z1), (8, z1 + 4), (15, z1 + 4)):
        pillar(g, x, z, 4)
    for x in range(7, 17):                           # the slab, its edge broken
        for z in range(z1 - 1, z1 + 6):
            edge = x in (7, 16) or z in (z1 - 1, z1 + 5)
            if edge and g.chance(0.3):
                continue
            put(g, x, 5, z, bslab() if edge else CROWN)
    for (x, z) in ((10, z1 + 1), (13, z1 + 3), (11, z1 + 4)):   # Silvermoss on top, ferns in it
        put(g, x, 5, z, "dross:silvermoss")
        put(g, x, 6, z, "dross:glimmer_fern")
    hang(g, 9, z1 + 2, 5)
    hang(g, 14, z1 + 2, 5)
    # the Oracle: his seat facing the road, the frame shard behind him, books, candles, a lectern for his tome
    put(g, 10, 1, z1 + 2, bstair("west"))
    shard(g, 9, z1 + 2, 2)
    put(g, 9, 1, z1 + 3, FRAME)                       # a broken piece of it, fallen beside it
    for z in range(z1 + 1, z1 + 4):
        put(g, 11, 1, z, "blue_carpet")
        put(g, 12, 1, z, "light_blue_carpet" if z == z1 + 2 else "blue_carpet")
    put(g, 8, 1, z1 + 1, "bookshelf")
    put(g, 8, 2, z1 + 1, "bookshelf")
    put(g, 8, 1, z1 + 3, "bookshelf")
    altar(g, 10, z1, 4)
    altar(g, 10, z1 + 4, 2)
    put(g, 9, 1, z1 + 4, "lectern[facing=east]")
    # the Seeker: his bedroll, crates, a chest; his tools outside the east pillars
    bed(g, 13, z1 + 1, "north", "gray")
    barrels(g, 14, z1 + 1, 2)
    chest(g, 14, z1 + 3, "west")
    put(g, 17, 1, z1 + 1, "cartography_table")
    put(g, 17, 1, z1 + 2, "smithing_table")
    put(g, 17, 1, z1 + 3, "grindstone[face=floor,facing=east]")
    put(g, 16, 3, z1, "blue_wall_banner[facing=east]")
    # the blue fire in front, benches round it, a stew pot
    soul_fire(g, 12, z1 + 8)
    bench(g, 11, z1 + 11, 13, z1 + 11)
    bench(g, 9, z1 + 7, 9, z1 + 9)
    for z in range(z1 + 7, z1 + 10):
        put(g, 15, 1, z, bslab())
    put(g, 15, 1, z1 + 6, "cauldron")
    woodpile(g, 6, z1 + 5, 6, z1 + 8)
    masonry(g, [(x1, z) for z in range(z1, z2 + 1)], 2.5)
    masonry(g, [(x, z2) for x in range(x1 + 1, x1 + 9)], 1.6)
    rubble(g, [(18, z2 - 1, "blackstone"), (17, z2, "blackstone"), (19, z1 + 4, "blackstone"),
               (16, z2 - 2, bstair("north"))])
    road_lamp(g, z1 + 3)
    road_lamp(g, z1 + 10)
    plant(g, 7, z2 - 2, "dross:glimmer_fern")
    plant(g, 18, z1 + 10, "dross:wispbloom")
    plant(g, 7, z1 + 10, "dross:glimmer_fern")
    plant(g, 19, z1 + 7, "dross:glimmer_fern")
    # where the survivors live in it: the Oracle on the runner before his seat, facing the road as his seat does; the
    # Seeker in front of his chest by his bed, facing the fire; the places they walk to (weights: how often), the fire's
    # west and south sides most
    survivors = dict(mentor=(11, 1, z1 + 2, "east"), student=(13, 1, z1 + 3, "south"),
                     places=((10, 1, z1 + 8, 3), (12, 1, z1 + 10, 2), (11, 1, z1 + 4, 1), (16, 1, z1 + 2, 2),
                             (7, 1, z1 + 9, 1), (21, 1, z1 + 7, 1), (24, 1, z1 + 1, 1)))
    return dict(number=1, label="3A", id="fallen_stones_a", west_of_road=True, name="Under the Fallen Stones",
                survivors=survivors,
                blurb="The draft, worked up: a slab of the spire's broken crown on four broken pillars beside the "
                      "steps, behind a windbreak of fallen masonry, grown over with Silvermoss. The Oracle sits facing "
                      "the road by a humming shard of a Dross frame, among books and candles, a lectern for his tome; "
                      "the Seeker sleeps among his crates, his map table, smithing table and grindstone outside. A blue "
                      "soul fire with benches and a stew pot in front.",
                focus=(3, 46, 30, 66))


# ---------------------------------------------------------------- 3B  The Leaning Slab

LEAN = {6: 1, 7: 1, 8: 2, 9: 2, 10: 3, 11: 3, 12: 4, 13: 4}   # the slab's height over each x (5 past 13)


def leaning_slab(g):
    """A slab of the crown that fell tilted: its west end lies on the ground, its east end is propped 5 high on two
    pillars (a third broke under it), so it makes a low cave that opens to the road. The Oracle sits under its high
    end facing out; the Seeker's bed and crates are crammed under its low end."""
    x1, z1, x2, z2 = 4, STEPS_Z + 1, 21, STEPS_Z + 13   # x 4-21, z 50-62
    floor_box(g, x1, z1, x2, z2, RUIN_FLOOR)
    zs, zn = z1 + 1, z1 + 8                             # the slab: x 6-17, z 51-58
    for x in range(6, 18):
        h = LEAN.get(x, 5)
        for z in range(zs, zn + 1):
            if (x in (6, 17) or z in (zs, zn)) and g.chance(0.22):
                continue
            put(g, x, h, z, CROWN)
            if x < 17 and LEAN.get(x + 1, 5) > h:
                put(g, x, h + 1, z, bstair("east"))
    pillar(g, 17, zs, 4)
    pillar(g, 17, zn, 4)
    pillar(g, 17, zs + 3, 2, cap=False)                # the one that broke
    rubble(g, [(19, zs + 4, CHISELED), (18, zs + 5, bstair("south")), (20, zs + 2, "blackstone")])
    for (x, z) in ((6, zs + 2), (7, zs + 3), (6, zs + 5), (9, zs + 4)):   # Silvermoss on its low end, ferns in it
        put(g, x, LEAN[x], z, "dross:silvermoss")
        put(g, x, LEAN[x] + 1, z, "dross:glimmer_fern")
    hang(g, 15, zs + 1, 5)
    hang(g, 15, zn - 1, 5)
    # the Oracle under the high end, facing the road
    put(g, 14, 1, zs + 3, bstair("west"))
    shard(g, 13, zs + 3, 2)
    for z in range(zs + 2, zs + 5):
        put(g, 15, 1, z, "blue_carpet")
    put(g, 16, 1, zs + 3, "blue_carpet")
    altar(g, 13, zs + 1, 3)
    altar(g, 13, zs + 5, 2)
    put(g, 14, 1, zs + 1, "lectern[facing=east]")
    put(g, 12, 1, zs + 2, "bookshelf")
    put(g, 12, 1, zs + 4, "bookshelf")
    put(g, 18, 3, zs, "blue_wall_banner[facing=east]")
    # the Seeker under the low end: his bed, his crates, a chest, more crates crammed in at the back
    bed(g, 11, zs + 1, "north", "gray")
    barrels(g, 10, zs + 4, 2)
    chest(g, 11, zs + 5, "east")
    barrels(g, 9, zs + 6)
    barrels(g, 8, zs + 6)
    barrels(g, 8, zs + 1)
    put(g, 16, 1, zn - 1, "cartography_table")
    put(g, 16, 1, zs + 1, "smithing_table")
    # the fire at the cave's mouth
    soul_fire(g, 20, zs + 4)
    bench(g, 19, zn, 21, zn)
    bench(g, 19, zs, 21, zs)
    put(g, 18, 1, zn - 1, "cauldron")
    woodpile(g, 13, z2 - 2, 15, z2 - 2, axis="x")
    masonry(g, [(x, z2) for x in range(x1, x1 + 7)], 1.4)
    rubble(g, [(5, z1, "blackstone"), (5, zn + 1, CROWN), (12, zn + 2, bslab()), (18, z2 - 1, "blackstone")])
    road_lamp(g, z1)
    road_lamp(g, z2 - 2)
    plant(g, 5, zs + 4, "dross:glimmer_fern")
    plant(g, 19, z2 - 1, "dross:wispbloom")
    plant(g, 8, z2 - 1, "dross:glimmer_fern")
    return dict(number=2, label="3B", id="fallen_stones_b", west_of_road=True, name="The Leaning Slab",
                blurb="A slab of the crown fell tilted beside the steps: its far end lies on the ground grown over "
                      "with Silvermoss, its near end is propped five high on two pillars (a third broke under it), "
                      "making a low cave that opens to the road. The Oracle sits under the high end facing out, the "
                      "frame shard and his books behind him; the Seeker's bed and crates are crammed under the low "
                      "end. The blue fire burns at the cave's mouth.",
                focus=(3, 46, 30, 66))


# ---------------------------------------------------------------- 3C  The Broken Arch

def broken_arch(g):
    """A piece of the spire's gate arch that fell whole and stands beside the steps facing the way in. The Oracle sits
    in its opening under a keystone that is a shard of a Dross frame; the Seeker has built a lean-to of silverwood
    against its west pier."""
    x1, z1, x2, z2 = 3, STEPS_Z + 1, 21, STEPS_Z + 16   # x 3-21, z 50-65
    floor_box(g, x1, z1, x2, z2, RUIN_FLOOR)
    road_stones(g, 19, z1 + 10, z1 + 12)
    za, zb = z1 + 5, z1 + 6                             # the arch: z 55-56
    for z in (za, zb):
        for x in list(range(7, 10)) + list(range(15, 18)):   # its piers
            for y in range(1, 7):
                put(g, x, y, z, BRICK)
        put(g, 10, 4, z, bstair("west", "top"))
        put(g, 14, 4, z, bstair("east", "top"))
        put(g, 10, 5, z, BRICK)
        put(g, 11, 5, z, bstair("west", "top"))
        put(g, 13, 5, z, bstair("east", "top"))
        put(g, 14, 5, z, BRICK)
        for x in (10, 11, 13, 14):
            put(g, x, 6, z, BRICK)
        put(g, 12, 6, z, FRAME)                         # the keystone: a shard of a Dross frame
        for x in range(7, 18):                          # the top course, broken at its ends
            if x in (7, 17) and g.chance(0.5):
                continue
            put(g, x, 7, z, CROWN)
    put(g, 8, 8, za, CROWN)
    put(g, 16, 8, zb, CROWN)
    put(g, 9, 8, zb, WALL)
    put(g, 12, 8, za, "blue_banner[rotation=0]")
    put(g, 12, 5, za, lantern("soul_lantern", hanging=True))
    # the Oracle seated in the opening, facing out, the shard and his books behind him
    put(g, 12, 1, zb, bstair("north"))
    shard(g, 12, za - 1, 2)
    for (x, z) in ((12, zb + 1), (12, zb + 2), (11, zb + 1), (13, zb + 1)):
        put(g, x, 1, z, "blue_carpet")
    altar(g, 10, zb, 3)
    altar(g, 14, zb, 2)
    put(g, 11, 1, za - 1, "lectern[facing=south]")
    put(g, 13, 1, za - 1, "bookshelf")
    put(g, 14, 1, za - 1, "bookshelf")
    put(g, 13, 2, za - 1, "bookshelf")
    # the Seeker's lean-to against the west pier: a silverwood roof sloping down to the west, planks on its north
    lz1, lz2 = za - 2, zb + 2                           # z 53-58
    for z in range(lz1, lz2 + 1):
        put(g, 6, 5, z, stairs("spruce_stairs", "east"))
        put(g, 5, 4, z, stairs("spruce_stairs", "east"))
        put(g, 4, 3, z, stairs("spruce_stairs", "east"))
    for z in (lz1, lz2):
        for y in range(1, 5):
            put(g, 6, y, z, "dross:silverwood_log[axis=y]")
        for y in range(1, 3):
            put(g, 4, y, z, "dross:silverwood_log[axis=y]")
    for y in range(1, 4):
        put(g, 5, y, lz1, "dross:silverwood_planks")
    bed(g, 5, za, "north", "gray")
    barrels(g, 5, zb + 1, 2)
    put(g, 5, 3, zb + 1, lantern("lantern"))
    chest(g, 4, zb + 1, "east")
    put(g, 6, 1, z1 + 10, "cartography_table")
    put(g, 7, 1, z1 + 9, "smithing_table")
    put(g, 5, 1, z1 + 10, "barrel[facing=up,open=false]")
    # the fire before the arch
    soul_fire(g, 12, z1 + 11)
    bench(g, 11, z1 + 14, 13, z1 + 14)
    bench(g, 9, z1 + 10, 9, z1 + 12)
    bench(g, 15, z1 + 10, 15, z1 + 12)
    put(g, 16, 1, z1 + 9, "cauldron")
    woodpile(g, 18, z1 + 2, 19, z1 + 3)
    # pieces of the arch that broke off
    rubble(g, [(16, z1 + 13, CROWN), (17, z1 + 14, bstair("west")), (18, z1 + 8, bstair("north")),
               (4, z1 + 12, CROWN), (5, z1 + 13, "blackstone"), (17, z1 + 12, WALL)])
    masonry(g, [(x, z2) for x in range(x1, x1 + 7)], 1.5)
    road_lamp(g, z1 + 5)
    road_lamp(g, z1 + 12)
    plant(g, 10, z2 - 1, "dross:glimmer_fern")
    plant(g, 16, z2 - 1, "dross:wispbloom")
    plant(g, 4, z1 + 9, "dross:glimmer_fern")
    plant(g, 19, z1 + 6, "dross:glimmer_fern")
    return dict(number=3, label="3C", id="fallen_stones_c", west_of_road=True, name="The Broken Arch",
                blurb="A piece of the spire's gate arch fell whole and stands beside the steps facing the way in, a "
                      "blue banner on its top. The Oracle sits in its opening under a keystone that is a shard of a "
                      "Dross frame, a soul lantern hanging from it, his books and lectern behind him; the Seeker has "
                      "built a lean-to of silverwood against its west pier for his bed and crates. The blue fire "
                      "burns before the arch.",
                focus=(1, 46, 30, 68))


# ---------------------------------------------------------------- 3D  The Leaning Stones

def leaning_stones(g):
    """Two slabs of the crown fell against each other and stand like a tent of stone along the steps, its back shut
    with masonry, its front open to the south. The Oracle sits at its back by the frame shard; the Seeker sleeps along
    its east side."""
    x1, z1, x2, z2 = 4, STEPS_Z + 1, 21, STEPS_Z + 16   # x 4-21, z 50-65
    floor_box(g, x1, z1, x2, z2, RUIN_FLOOR)
    road_stones(g, 19, z1 + 12, z1 + 14)
    zs, zn = z1 + 1, z1 + 9                             # the tent: z 51-59, its ridge over x 12
    for z in range(zs, zn + 1):
        end = z in (zs, zn)
        put(g, 12, 6, z, CROWN)
        put(g, 12, 5, z, CROWN)
        for k in range(1, 6):
            if end and k >= 3 and g.chance(0.35):
                continue                                # its broken ends
            for x, up, under in ((12 - k, "east", "west"), (12 + k, "west", "east")):
                put(g, x, 6 - k, z, bstair(up))
                if 5 - k >= 1:
                    put(g, x, 5 - k, z, bstair(under, "top"))
    for x in range(9, 16):                              # its back, shut with masonry under the slopes
        top = 5 - abs(x - 12)
        for y in range(1, top + 1):
            put(g, x, y, z1, CROWN)
    put(g, 12, 7, zn, "blue_banner[rotation=0]")
    hang(g, 12, zs + 4, 5, links=0)
    hang(g, 12, zn - 1, 5, links=0)
    # the Oracle at the back, the frame shard behind him, candles either side, a runner down the middle
    put(g, 12, 1, zs + 1, bstair("north"))
    shard(g, 12, zs, 3)
    altar(g, 11, zs, 3)
    altar(g, 13, zs, 2)
    put(g, 10, 1, zs + 1, "lectern[facing=east]")
    for z in range(zs + 2, zn + 1):
        put(g, 12, 1, z, "blue_carpet")
    put(g, 11, 1, zs + 2, "light_blue_carpet")
    put(g, 13, 1, zs + 2, "light_blue_carpet")
    # the Seeker along the east side, his books and tools along the west
    bed(g, 14, zs + 4, "north", "gray")
    barrels(g, 14, zs + 6, 2)
    chest(g, 13, zs + 7, "west")
    put(g, 10, 1, zs + 4, "bookshelf")
    put(g, 10, 2, zs + 4, "bookshelf")
    put(g, 10, 1, zs + 6, "smithing_table")
    put(g, 15, 1, z1 + 11, "cartography_table")
    put(g, 16, 1, z1 + 11, "grindstone[face=floor,facing=south]")
    # the fire before its open end
    soul_fire(g, 12, z1 + 13)
    bench(g, 9, z1 + 12, 9, z1 + 14)
    for z in range(z1 + 12, z1 + 15):
        put(g, 15, 1, z, bslab())
    put(g, 14, 1, z1 + 15, "cauldron")
    # a pillar still standing, another toppled over, the woodpile
    pillar(g, 18, zs + 1, 5)
    for x in range(16, 21):
        put(g, x, 1, zn, CHISELED if x == 20 else BRICK)
    pillar(g, 6, z1 + 11, 2, cap=False)
    woodpile(g, 5, z1 + 13, 6, z1 + 14)
    rubble(g, [(6, zs + 3, CROWN), (17, z1 + 14, "blackstone"), (19, z1 + 4, bstair("west"))])
    road_lamp(g, zs + 3)
    road_lamp(g, z1 + 11)
    plant(g, 8, z1 + 14, "dross:glimmer_fern")
    plant(g, 17, z1 + 12, "dross:wispbloom")
    plant(g, 5, zs + 6, "dross:glimmer_fern")
    plant(g, 19, zs + 6, "dross:glimmer_fern")
    return dict(number=4, label="3D", id="fallen_stones_d", west_of_road=True, name="The Leaning Stones",
                blurb="Two slabs of the crown fell against each other and stand like a tent of stone beside the steps, "
                      "its back shut with masonry, its front open to the south, a blue banner on its ridge. The Oracle "
                      "sits at its back by the frame shard between his candles, a blue runner down to the door; the "
                      "Seeker sleeps along its east side, his books and smithing table along the west. One pillar "
                      "still stands, another lies toppled; the blue fire burns before the open end.",
                focus=(3, 46, 30, 68))


# ---------------------------------------------------------------- 3E  The Ring of Pillars

RING_CENTRE = (12, 58)
RING_PILLARS = ((35, 2, False), (85, 3, False), (135, 1, False), (180, 3, False), (225, 4, True), (270, 4, True),
                (315, 4, True))   # (degrees from east towards south, height, holds the roof)


def ring_of_pillars(g):
    """A ring of broken pillars round a hollow worn a block down into the ground, the back of the ring roofed by a slab
    of the crown on its three tallest pillars, steps down into it from the road. The fire burns in the middle; the
    Oracle sits under the roof facing it."""
    cx, cz = RING_CENTRE
    floor_disc(g, cx, cz, 7.8, RUIN_FLOOR)
    hollow = [(x, z) for x in range(cx - 6, cx + 7) for z in range(cz - 6, cz + 7)
              if (x - cx) ** 2 + (z - cz) ** 2 <= 5.2 ** 2]
    for (x, z) in hollow:
        put(g, x, 0, z, "air")
        put(g, x, -1, z, SUNK_FLOOR)
    for z in range(cz - 1, cz + 2):                     # steps up out of it towards the road
        put(g, cx + 5, 0, z, bstair("east"))
    road_stones(g, cx + 6, cz - 1, cz + 1)
    tops = []
    for deg, h, holds in RING_PILLARS:
        a = math.radians(deg)
        x, z = round(cx + 6.5 * math.cos(a)), round(cz + 6.5 * math.sin(a))
        pillar(g, x, z, h, cap=holds)
        tops.append((x, z, h, holds))
    for x in range(cx - 6, cx + 7):                      # the roof over the back, its front edge broken
        for z in range(cz - 7, cz - 2):
            if (x - cx) ** 2 + (z - cz) ** 2 > 7.8 ** 2:
                continue
            edge = z == cz - 3 or (x - cx) ** 2 + (z - cz) ** 2 > 6.8 ** 2
            if edge and g.chance(0.3):
                continue
            put(g, x, 5, z, bslab() if edge else CROWN)
    hang(g, cx - 3, cz - 4, 5)
    hang(g, cx + 3, cz - 4, 5)
    for (x, z, h, holds) in tops:                        # lanterns on the broken pillars at the front
        if not holds and h >= 2:
            put(g, x, h + 1, z, lantern("soul_lantern"))
    # the Oracle under the roof, facing the fire, the frame shard behind him
    put(g, cx, 0, cz - 4, bstair("north"))
    shard(g, cx, cz - 5, 3, y0=0)
    put(g, cx, 3, cz - 5, CHISELED)                     # the shard's broken top
    put(g, cx, 3, cz - 6, "blue_wall_banner[facing=south]")
    altar(g, cx - 2, cz - 4, 3, y0=0)
    altar(g, cx + 2, cz - 4, 2, y0=0)
    for (x, z) in ((cx, cz - 3), (cx - 1, cz - 3), (cx + 1, cz - 3)):
        put(g, x, 0, z, "blue_carpet")
    put(g, cx + 3, 0, cz - 2, "lectern[facing=west]")
    # the Seeker on the west side of the hollow
    bed(g, cx - 4, cz + 1, "north", "gray")
    barrels(g, cx - 3, cz + 4, 2, y=0)
    chest(g, cx - 4, cz - 1, "east", y=0)
    put(g, cx + 2, 0, cz + 4, "smithing_table")
    put(g, cx - 2, 1, cz + 6, "cartography_table")
    # the fire in the middle, benches round it
    soul_fire(g, cx, cz, y=0)
    bench(g, cx - 1, cz + 3, cx + 1, cz + 3, y=0)
    bench(g, cx - 3, cz - 1, cx - 3, cz + 1, y=0)
    for z in range(cz - 1, cz + 2):
        put(g, cx + 3, 0, z, bslab())
    woodpile(g, cx - 7, cz + 2, cx - 7, cz + 3)
    rubble(g, [(cx + 7, cz + 5, CHISELED), (cx - 8, cz - 3, CROWN), (cx + 5, cz + 7, bstair("west"))])
    road_lamp(g, cz - 4)
    road_lamp(g, cz + 4)
    plant(g, cx + 6, cz + 3, "dross:wispbloom")
    plant(g, cx - 3, cz + 7, "dross:glimmer_fern")
    plant(g, cx + 4, cz - 6, "dross:glimmer_fern")
    plant(g, cx - 6, cz + 4, "dross:glimmer_fern")
    return dict(number=5, label="3E", id="fallen_stones_e", west_of_road=True, name="The Ring of Pillars",
                blurb="A ring of the spire's broken pillars round a hollow worn a block down into the ground, the back "
                      "of the ring roofed by a slab of the crown on its three tallest pillars, steps down into it from "
                      "the road, soul lanterns on the broken ones. The blue fire burns in the middle; the Oracle sits "
                      "under the roof facing it, the frame shard and his banner behind him; the Seeker sleeps on the "
                      "west side among his crates.",
                focus=(1, 46, 30, 68))


VERSIONS = (fallen_crown, leaning_slab, broken_arch, leaning_stones, ring_of_pillars)
PICK = fallen_crown     # the user's pick (November 3, 2026), the camp at the real hub

# ---------------------------------------------------------------- checks (run by camps.py on every build)

SOIL = {"dirt", "coarse_dirt", "rooted_dirt", "podzol", "grass_block", "moss_block", "silvermoss"}
CENTRE_PROPS = {"white_candle", "lantern", "soul_lantern"}


def _full(s):
    return s is not None and not mc.is_air(s) and mc.kind(s) == "full"


def _holds_up(s):
    """A candle or a standing lantern can stand on it: its top's middle is solid (a full block, a wall's or a fence's
    post, the top of an upside-down stair or a top slab)."""
    if s is None or mc.is_air(s):
        return False
    b, props = mc.base(s), mc.props_of(s)
    return (_full(s) or b.endswith("_wall") or (b.endswith("_fence") and not b.endswith("_gate"))
            or (b.endswith("_stairs") and props.get("half") == "top") or (b.endswith("_slab") and props.get("type") != "bottom"))


def _holds_from(s):
    """A lantern can hang from it: its bottom's middle is solid (a full block, a chain, a wall's or a fence's post, the
    bottom of an upright stair or a bottom slab)."""
    if s is None or mc.is_air(s):
        return False
    b, props = mc.base(s), mc.props_of(s)
    return (_full(s) or b == "chain" or b.endswith("_wall") or (b.endswith("_fence") and not b.endswith("_gate"))
            or (b.endswith("_stairs") and props.get("half") != "top") or (b.endswith("_slab") and props.get("type") != "top"))


def _passable(s):
    """Something a survivor can stand in: air, or a carpet (it stands on top of it)."""
    return s is None or mc.is_air(s) or mc.base(s).endswith("_carpet")


def _floor(s):
    """Solid enough to stand on: its top's middle is solid (a full block, an upside-down stair, a top slab, a wall)."""
    return _holds_up(s)


def survivor_check(g, info):
    """The survivors' rest spots and places in a camp (`survivors`), as the game will use them: each inside the camp's
    box, free to stand in (air, or a carpet, at its feet; air over it), on a floor, nothing burning in it; the two rest
    spots different cells."""
    problems = []
    spots = info.get("survivors")
    if not spots:
        return problems
    x1, z1, x2, z2 = info["focus"]
    cells = [("the Oracle's rest spot", spots["mentor"][:3]), ("the Seeker's rest spot", spots["student"][:3])]
    cells += [(f"place {i + 1}", p[:3]) for i, p in enumerate(spots["places"])]
    for what, (x, y, z) in cells:
        where = f"{what} at {x},{y},{z}"
        feet, head, below = g.get(x, y, z), g.get(x, y + 1, z), g.get(x, y - 1, z)
        if not (x1 <= x <= x2 and z1 <= z <= z2):
            problems.append(f"{where}: outside the camp's box")
        if not _passable(feet) or not (head is None or mc.is_air(head)):
            problems.append(f"{where}: no room to stand ({feet}, {head} over it)")
        if not _floor(below):                   # (on a carpet too: it lies on the block under it)
            problems.append(f"{where}: nothing to stand on ({below})")
        for s in (feet, head, below):
            if s is not None and any(k in mc.base(s) for k in ("fire", "campfire", "magma", "lava")):
                problems.append(f"{where}: something burning ({s})")
    if spots["mentor"][:3] == spots["student"][:3]:
        problems.append("the two rest spots are the same cell")
    return problems


def check(g, base, info):
    """What the shrine kit's own check doesn't look at, for a camp: every bed whole, every chest free to open, candles
    and standing lanterns on a full block or a wall, hanging lanterns under a full block or a chain, plants on soil,
    carpets on something, the spire's steps left as they were; for a camp beside the road (`west_of_road`), the road
    free and nothing east of it."""
    import numpy as np
    problems = []
    a = np.array(g.pal, dtype=object)[g.a]
    b0 = np.array(base.pal, dtype=object)[base.a]
    G = g.G

    def ground_or(s, y):
        """Unset under the ground layer is the world's own ground."""
        return s is None and y <= 0

    for x, yi, z in np.argwhere(a != b0):
        x, yi, z = int(x), int(yi), int(z)
        y = yi - G
        s = a[x, yi, z] or "minecraft:air"
        if mc.is_air(s):
            continue
        b = mc.base(s)
        props = mc.props_of(s)
        where = f"{b} at {x},{y},{z}"
        if info.get("west_of_road") and x >= ROAD_X[0] and y >= 1 and (x <= ROAD_X[1] or z > STEPS_Z):
            problems.append(f"{where}: on the road or east of it")
        if z <= STEPS_Z and y <= 2 and x >= 20:
            problems.append(f"{where}: on the spire's steps")
        below, above = g.get(x, y - 1, z), g.get(x, y + 1, z)
        if b.endswith("_bed"):
            dx, dz = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[props["facing"]]
            sign = 1 if props["part"] == "foot" else -1
            other = g.get(x + sign * dx, y, z + sign * dz)
            if other is None or mc.base(other) != b or mc.props_of(other).get("part") == props["part"]:
                problems.append(f"{where}: half a bed")
        if b == "chest" and above is not None and mc.is_opaque(above):
            problems.append(f"{where}: it can't open (a block over it)")
        if b in CENTRE_PROPS and props.get("hanging") != "true":
            if not (_holds_up(below) or ground_or(below, y - 1)):
                problems.append(f"{where}: nothing it can stand on ({below})")
        if props.get("hanging") == "true" and not _holds_from(above):
            problems.append(f"{where}: nothing it can hang from ({above})")
        if b == "chain" and not _holds_from(above):
            problems.append(f"{where}: a chain hanging from nothing ({above})")
        if mc.kind(s) == "cross" and (below is None or mc.base(below) not in SOIL):
            problems.append(f"{where}: a plant on {below}")
        if b.endswith("_carpet") and not ((below is not None and not mc.is_air(below)) or ground_or(below, y - 1)):
            problems.append(f"{where}: carpet over nothing")
    return problems
