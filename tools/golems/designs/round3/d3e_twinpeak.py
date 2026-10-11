"""
No. 3E - Twinpeak (twin volcanoes, round 3).

3D's twin volcanoes, now growing out of its shoulder blades: each cone leans back with its back and splays out a
little, its foot sunk into the stone, so nothing hangs off. Dark scorched stone like 3C, 3C's spikes pointing up off
its shoulders, 3D's beard of obsidian shards, and a short ridge of obsidian spikes down the spine below the peaks.
Erupting, both craters blow and lava runs down its shoulders and arms; as lava, both volcanoes erupt at once.
"""
from designs import calderon as K
from designs import common as C
from kit import materials as M

NUMBER = 1
LABEL = "3E"
ID = "twinpeak"
NAME = "Twinpeak"
MOOD = "twin volcanoes"
BLURB = "3D's twin volcanoes grown out of its shoulder blades, 3C's dark stone and shoulder spikes, 3D's beard."


def body():
    return K.body()


def build(m, b):
    B = C.skeleton(m, b)
    jaw, _jaw_in = K.core(m, B)
    K.torso_shell(m, B)
    K.head_shell(m, B, jaw)
    K.limb_shell(m, B)
    # A short ridge down the spine below the peaks, raked towards the tail.
    K.ridge(m, B, root_mat="spikeroot", spikes=[(0, 12, K.LOWER_BACK_Z, 9, 5, 20, 0), (0, 5, K.LOWER_BACK_Z, 8, 5, 28, 0),
                   (0, 0.5, K.LOWER_BACK_Z, 6, 4, 36, 0)])
    # Two volcanoes out of the shoulder blades, leaning back with the back and splayed out a little.
    vols = []
    for side, sx in C.SIDES:
        v = C.volcano(m, B["torso"], K.T(B, sx * 11, 25, K.UPPER_BACK_Z), direction=C.back_dir(-6, 14, sx),
                      levels=[(18, 16, 5), (15, 13, 5), (13, 11, 4), (12, 10, 4)], crater=(8, 6), sink=3,
                      molten_sink=7, prefix=f"volcano_{side}", rock="vrock", rim="obsidian", rim_spikes=3,
                      molten_levels=[(19, 20, 6), (16, 16, 6), (14, 13, 6), (11, 10, 5)], fountain=12, bombs=3)
        vols.append(v)
    K.molten_spikes(m, B, back=[(0, 10, K.HUMP_Z - 4, 8, 4, 25, 0), (0, 3, 14, 6, 4, 32, 0)])
    return dict(volcanoes=vols)


def materials():
    return {
        "lava": M.lava(seed=61, crust=1.1, hot=-0.6, edge_cool=1.0, plates=0.55, plate_cell=9.0),
        "molten": M.lava(seed=62, crust=1.2, hot=0.6, edge_cool=0.5, plates=0.25, plate_cell=6.0),
        "fountain": M.lava(seed=63, crust=1.3, hot=1.4, edge_cool=0.2),
        "crater_lava": M.crater_pool(seed=64),
        "rock": M.rock(seed=65, chunk=10.0, obsidian=0.3, cracks=0.15, fissures=0.12, fissure_cell=30.0, heat=-0.7,
                       cobble=M.COBBLE_DARK),
        "vrock": M.rock(seed=70, chunk=8.0, obsidian=0.55, cracks=0.2, fissures=0.0, heat=-0.5, cobble=M.COBBLE_DARK),
        "obsidian": M.rock(seed=66, chunk=7.0, obsidian=1.0, cracks=0.1, fissures=0.0, heat=-0.6),
        "spikeroot": M.rock(seed=71, chunk=3.5, obsidian=1.0, cracks=0.6, fissures=0.0, heat=-0.2, crack_width=0.6),
        "mask": M.rock(seed=67, chunk=6.0, obsidian=0.0, cracks=0.0, fissures=0.0, cobble=M.COBBLE_OLD),
        "basalt": M.basalt(seed=68, cracks=0.1),
        "fang": M.fang(seed=69),
    }


def overlays():
    return [M.face_overlay(
        eye_px=[(-5, 0), (-4, 0), (3, 0), (4, 0), (-1, 4), (-1, 5), (-1, 6)],
        sockets=[(4.0, 0.5, 6, 3), (-4.5, 0.5, 6, 3)],
        cracks=[((-1, 7), (-2, 10)), ((-1, 3), (0, 0), (-1, -4)), ((7, 1), (9, -2)), ((-7, 1), (-9, 3))],
        skull_tag="skull", core_tag="cranium")]


FLOWS = dict(reach=72.0, streams=0.55, seed=601)

ANIM = dict(
    weight=1.3,
    walk_cycle=2.25,
    stride=30.0,
    arm_lift=8.0,
    leg_lift=6.0,
    bob=2.0,
    sway=2.1,
    roll=3.0,
    idle_period=5.0,
    roar=0.85,
    plate_speed=1.0,
)
