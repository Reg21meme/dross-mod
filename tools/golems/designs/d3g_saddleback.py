"""
No. 3G - Saddleback (great twin peaks): the user's pick from round 3.

The twin peaks of 3D grown as big as 3F's volcano: two great cones rise out of its back, their feet merging into one
saddle of rock across the spine, each leaning back with the body and splayed out over a shoulder blade. Big obsidian
spikes stand in the saddle between them and run down its lower back. 3H's horns on its brow, dark stone like 3C,
spikes up off its shoulders, 3D's beard. Erupting, both calderas blow out the way the cones lean; as lava, two
molten mountains erupt side by side, and the horns and spikes are lava too.
"""
from designs import calderon as K
from designs import common as C
from kit import materials as M

NUMBER = 1
LABEL = "3G"
ID = "saddleback"
NAME = "Saddleback"
MOOD = "big twin peaks"
BLURB = "Two great volcanoes out of its back, merged into one saddle of rock. Horns, big back spikes, a beard."


def body():
    return K.body()


def build(m, b):
    B = C.skeleton(m, b)
    jaw, _jaw_in = K.core(m, B)
    K.torso_shell(m, B, upper_back=False)
    K.head_shell(m, B, jaw, horns=(15, 5, -35, 30), horn_root="spikeroot")   # 3H's horns
    # Smaller shoulder spikes, so the peaks have room.
    K.limb_shell(m, B, shoulder_spikes=((-5, 9, 20), (1, 13, 0), (6, 9, -20)))
    vols = []
    for side, sx in C.SIDES:
        v = C.volcano(m, B["torso"], K.T(B, sx * 9, 24, K.HUMP_Z), direction=C.back_dir(-4, 13, sx),
                      levels=[(24, 26, 6), (21, 21, 5), (18, 17, 5), (15, 14, 4), (14, 12, 4)], crater=(8, 6), sink=1,
                      molten_sink=4, prefix=f"volcano_{side}", rock="vrock", rim="obsidian", rim_spikes=3,
                      molten_levels=[(23, 28, 7), (21, 23, 6), (18, 18, 6), (15, 14, 6), (11, 10, 4)],
                      fountain=13, bombs=3)
        vols.append(v)
    # Big spikes in the saddle between the peaks and down the lower back, raked further back the lower they are.
    K.ridge(m, B, root_mat="spikeroot", spikes=[(0, 31, K.HUMP_Z + 5, 10, 5, -10, 0), (0, 24, K.HUMP_Z + 5, 13, 6, 5, 0),
                   (0, 16, K.HUMP_Z + 4, 13, 6, 18, 0), (0, 8, K.LOWER_BACK_Z, 11, 5, 28, 0),
                   (0, 1.5, K.LOWER_BACK_Z, 8, 4, 38, 0)])
    K.molten_spikes(m, B, back=[(0, 8, 14, 10, 5, 26, 0), (0, 1, 14, 8, 4, 36, 0)], shoulder=((0, 10, 0),),
                    shoulder_splay=35.0, shoulder_out=6.0, horns=(12, 4, -35, 30))
    K.held_rock(m, B)   # the fight's throw: the rock it tears out of the ground
    return dict(volcanoes=vols)


def materials():
    return {
        "lava": M.lava(seed=81, crust=1.1, hot=-0.6, edge_cool=1.0, plates=0.55, plate_cell=9.0),
        "molten": M.lava(seed=82, crust=1.2, hot=0.6, edge_cool=0.5, plates=0.25, plate_cell=6.0),
        "fountain": M.lava(seed=83, crust=1.3, hot=1.4, edge_cool=0.2),
        "crater_lava": M.crater_pool(seed=84),
        "rock": M.rock(seed=85, chunk=10.0, obsidian=0.3, cracks=0.15, fissures=0.12, fissure_cell=30.0, heat=-0.7,
                       cobble=M.COBBLE_DARK),
        "vrock": M.rock(seed=90, chunk=9.0, obsidian=0.5, cracks=0.22, fissures=0.15, fissure_cell=24.0, heat=-0.5,
                        cobble=M.COBBLE_DARK),
        "obsidian": M.rock(seed=86, chunk=7.0, obsidian=1.0, cracks=0.1, fissures=0.0, heat=-0.6),
        "spikeroot": M.rock(seed=91, chunk=3.5, obsidian=1.0, cracks=0.6, fissures=0.0, heat=-0.2, crack_width=0.6),
        "mask": M.rock(seed=87, chunk=6.0, obsidian=0.0, cracks=0.0, fissures=0.0, cobble=M.COBBLE_OLD),
        "basalt": M.basalt(seed=88, cracks=0.1),
        "fang": M.fang(seed=89),
        "throwrock": M.rock(seed=92, chunk=5.0, obsidian=0.0, cracks=0.0, fissures=0.0, heat=-0.8, cobble=M.COBBLE,
                            stone_cell=3.2),
    }


def overlays():
    return [M.face_overlay(
        eye_px=[(-5, 0), (-4, 0), (3, 0), (4, 0), (-1, 4), (-1, 5), (-1, 6)],
        sockets=[(4.0, 0.5, 6, 3), (-4.5, 0.5, 6, 3)],
        cracks=[((-1, 7), (-2, 10)), ((-1, 3), (0, 0), (-1, -4)), ((7, 1), (9, -2)), ((-7, 1), (-9, 3))],
        skull_tag="skull", core_tag="cranium")]


FLOWS = dict(reach=76.0, streams=0.6, seed=801)

# The fight's Cracking stage (60% down to 35% of its health): its shell breaking up, lava glowing through. Plates
# knocked loose: (bone, push (units, along its parent's axes), tilt (degrees), how cracked the shell is when it comes
# loose: 0 = as soon as the stage starts, 1 = just before the shell bursts). See kit/cracks.py.
LOOSE = [
    ("shell_back_l", (0.8, 0.0, 1.6), (5.0, 0.0, -4.0), 0.0),
    ("shell_flank_r", (-1.4, 0.0, 0.0), (0.0, -4.0, -6.0), 0.05),
    ("shell_upperarm_l", (1.2, 0.0, 0.0), (0.0, 0.0, 7.0), 0.15),
    ("shell_chest_r", (-0.5, 0.0, -1.3), (-4.0, 0.0, -5.0), 0.25),
    ("shell_thigh_r", (-1.2, 0.0, 0.0), (0.0, 0.0, -6.0), 0.3),
    ("shell_traps", (0.0, 1.2, 0.6), (5.0, 0.0, 0.0), 0.4),
    ("shell_back_r", (-0.8, 0.0, 1.4), (4.0, 0.0, 5.0), 0.45),
    ("shell_pauldron_l", (0.8, 1.0, 0.0), (0.0, 0.0, 8.0), 0.5),
    ("shell_flank_l", (1.4, 0.0, 0.0), (0.0, 4.0, 7.0), 0.55),
    ("shell_thigh_l", (1.2, 0.0, 0.0), (0.0, 0.0, 6.0), 0.6),
    ("shell_chest_l", (0.5, 0.0, -1.3), (-5.0, 0.0, 4.0), 0.7),
    ("shell_upperarm_r", (-1.2, 0.0, 0.0), (0.0, 0.0, -7.0), 0.75),
    ("shell_pauldron_r", (-0.8, 1.0, 0.0), (0.0, 0.0, -8.0), 0.8),
    ("shell_hips", (0.0, 0.4, 1.0), (4.0, 0.0, 0.0), 0.85),
]
# Its bright new cracks (the _cracks texture, faded in with the cracking).
CRACKS = dict(seed=811, cell=11.0, width=0.8, share=0.55)

ANIM = dict(
    weight=1.35,
    walk_cycle=2.3,
    stride=30.0,
    arm_lift=8.0,
    leg_lift=6.0,
    bob=2.0,
    sway=2.2,
    roll=3.0,
    idle_period=5.0,
    roar=0.85,
    plate_speed=0.95,
)
