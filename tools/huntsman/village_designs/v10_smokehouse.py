"""No. 10 Smokehouse Camp: a working camp. Six igloos round a busy yard: rows of meat-drying racks, a stone
smokehouse with smokers inside and a smoking chimney, woodpiles, sleds lined up, a pen with hay, and an ice cellar
dug under the snow (a trapdoor and a ladder down to barrels kept cold in a room of packed ice)."""
from villagekit import props
from villagekit.igloo import Style
from villagekit.local import Frame
from villagekit.village import Village

INFO = {"number": 10, "id": "smokehouse_camp", "name": "Smokehouse Camp",
        "mood": "a working camp: drying racks, a stone smokehouse, a pen and an ice cellar"}


def smokehouse(g, x0, z0):
    """A cobblestone hut 7 x 5 (door on the south), a spruce gable roof, smokers along the back, a chimney with a
    campfire on top (its smoke rises)."""
    for x in range(x0, x0 + 7):
        for z in range(z0, z0 + 5):
            edge = x in (x0, x0 + 6) or z in (z0, z0 + 4)
            for y in range(1, 4):
                if edge:
                    g.set(x, y, z, "cobblestone" if (x + y + z) % 5 else "mossy_cobblestone")
                else:
                    g.set(x, y, z, "air")
            g.set(x, 0, z, "stone_bricks")
    # the roof: stairs from both long sides up to a ridge, planks under them and in the gable ends
    for k in range(4):
        y = 4 + k
        for z in range(z0 - 1, z0 + 6):
            if k < 3:
                g.set(x0 - 1 + k, y, z, "spruce_stairs[facing=east,half=bottom]")
                g.set(x0 + 7 - k, y, z, "spruce_stairs[facing=west,half=bottom]")
                if k > 0:
                    g.set(x0 - 1 + k, y - 1, z, "spruce_planks") if z0 <= z <= z0 + 4 else None
                    g.set(x0 + 7 - k, y - 1, z, "spruce_planks") if z0 <= z <= z0 + 4 else None
            else:
                g.set(x0 + 3, y - 1, z, "spruce_planks")
                g.set(x0 + 3, y, z, "spruce_slab[type=bottom]")
        for x in range(x0 + k, x0 + 7 - k):
            for z in (z0, z0 + 4):
                if g.get(x, y, z) is None or k == 0:
                    g.set(x, y, z, "spruce_planks")
    for x in range(x0 + 1, x0 + 6):
        g.set(x, 1, z0 + 1, "smoker[facing=south,lit=true]")
    g.set(x0 + 3, 1, z0 + 4, "spruce_door[facing=south,half=lower,hinge=left,open=false]")
    g.set(x0 + 3, 2, z0 + 4, "spruce_door[facing=south,half=upper,hinge=left,open=false]")
    g.set(x0 + 1, 2, z0 + 4, "air")
    g.set(x0 + 5, 2, z0 + 4, "air")
    for y in range(1, 10):
        g.set(x0 + 6, y, z0 + 2, "cobblestone")
    g.set(x0 + 6, 10, z0 + 2, "campfire[facing=north,lit=true,signal_fire=false]")
    g.set(x0 + 1, 1, z0 + 3, "barrel[facing=up,open=false]")
    g.set(x0 + 5, 1, z0 + 3, "barrel[facing=up,open=false]")
    g.set(x0 + 3, 3, z0 + 2, "chain[axis=y]")
    g.set(x0 + 3, 2, z0 + 2, "dried_kelp_block")


def cellar(g, x0, z0):
    """An ice cellar: a trapdoor in the snow, a ladder down 4 blocks into a 5 x 5 room of packed ice with barrels."""
    for x in range(x0 - 1, x0 + 6):
        for z in range(z0 - 1, z0 + 6):
            for y in range(-5, 0):
                edge = x in (x0 - 1, x0 + 5) or z in (z0 - 1, z0 + 5) or y == -5
                g.set(x, y, z, "packed_ice" if edge else "air")
    for x in range(x0, x0 + 5):
        g.set(x, -1, z0 - 1, "packed_ice")
    for x, z in ((x0, z0 + 4), (x0 + 1, z0 + 4), (x0 + 4, z0 + 4), (x0 + 4, z0 + 3), (x0 + 4, z0)):
        g.set(x, -4, z, "barrel[facing=up,open=false]")
    g.set(x0 + 2, -4, z0 + 4, "chest[facing=north,type=single]")
    g.set(x0, -4, z0 + 2, "soul_lantern[hanging=false]")
    for y in range(-4, 1):
        g.set(x0, y, z0, "ladder[facing=south]")
    g.set(x0, 0, z0 - 1, "packed_ice")
    g.set(x0, 1, z0, "spruce_trapdoor[facing=south,half=bottom,open=false]")
    g.set(x0, 0, z0, "ladder[facing=south]")
    for x in range(x0 - 1, x0 + 2):
        for z in range(z0 - 1, z0 + 2):
            if (x, z) != (x0, z0) and g.get(x, 0, z) is None:
                g.set(x, 0, z, "spruce_planks")


def pen(g, x1, z1, x2, z2):
    for x, z in props.rect_cells(x1, z1, x2, z2):
        g.set(x, 1, z, "spruce_fence")
    g.set((x1 + x2) // 2, 1, z2, "spruce_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
    for x, z in ((x1 + 1, z1 + 1), (x1 + 2, z1 + 1), (x1 + 1, z1 + 2)):
        g.set(x, 1, z, "hay_block[axis=y]")
    g.set(x1 + 1, 2, z1 + 1, "hay_block[axis=x]")
    g.set(x2 - 1, 1, z1 + 1, "water_cauldron[level=3]")
    g.set(x2 - 2, 1, z1 + 1, "water_cauldron[level=3]")


def build():
    v = Village(57, 55, H=18, G=6, seed=1010)
    g = v.g
    style = Style()
    plan = [(10, 10, "sleeping"), (28, 8, "cook"), (46, 10, "sleeping"), (8, 32, "workshop"), (48, 32, "storage"),
            (12, 48, "sleeping")]
    for x, z, kind in plan:
        v.igloo(x, z, 4.5, toward=(28, 28), kind=kind, style=style, **({"beds": 3} if kind == "sleeping" else {}))
    v.build_igloos()

    props.plaza(g, 28, 28, 10)
    for row, z in enumerate((21, 26, 31)):
        for x0 in (19, 31):
            props.drying_rack(Frame(g, x0, z, "south"), length=5, hang=("dried_kelp_block", "dried_kelp_block",
                                                                       "brown_wool" if row == 1 else None))
    smokehouse(g, 36, 41)
    cellar(g, 24, 40)
    pen(g, 39, 17, 46, 23)
    for i, z in enumerate((44, 47, 50)):
        props.sled(Frame(g, 26 + 0 * i, z, "east"), cargo=i != 1)
    props.woodpile(Frame(g, 18, 40, "south"), length=4, height=2)
    props.woodpile(Frame(g, 18, 41, "south"), length=4, height=1)
    props.fire_pit(Frame(g, 28, 36, "south"), r=1, benches=False, pot=True)
    g.set(23, 1, 34, "stripped_spruce_log[axis=y]")
    g.set(24, 1, 34, "grindstone[face=floor,facing=south]")
    props.path(g, [(28, 39), (28, 54)], width=3)
    props.banner_pole(Frame(g, 25, 52, "south"), "brown", height=4)
    props.banner_pole(Frame(g, 31, 52, "south"), "brown", height=4)
    props.drifts(g, 0, area=lambda x, z: not v.footprint(x, z, 1) and (x < 15 or x > 41 or z < 15), salt=10)
    return g, dict(INFO, igloos=len(v.igloos))
