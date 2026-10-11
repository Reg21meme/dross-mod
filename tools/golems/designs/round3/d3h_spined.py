"""
No. 3H - Spined Calderon (spikes, round 3).

3C fixed: its great obsidian spikes now grow out of its back instead of standing straight up. A ridge of them runs
down its spine, raked further back the lower they are, with a row of smaller ones fanning out over each shoulder
blade, their feet sunk into the stone. At the top of the spine a chimney of a volcano leans back with its back.
Horns, 3C's spikes pointing up off its shoulders, spikes along its forearms, a beard; the darkest stone of the four.
"""
from designs import calderon as K
from designs import common as C
from kit import materials as M

NUMBER = 4
LABEL = "3H"
ID = "spined"
NAME = "Spined Calderon"
MOOD = "spiked"
BLURB = "3C fixed: its ridge of great obsidian spikes grows out of its back, and its chimney leans back with it."


def body():
    return K.body()


def build(m, b):
    B = C.skeleton(m, b)
    jaw, _jaw_in = K.core(m, B)
    K.torso_shell(m, B)
    K.head_shell(m, B, jaw, horns=(15, 5, -35, 30))
    K.limb_shell(m, B, arm_spikes=(-2, -7, -12), arm_spike_size=(7, 4))
    # The ridge down the spine: great spikes, raked further back the lower they are.
    K.ridge(m, B, [(0, 18, K.UPPER_BACK_Z, 17, 7, 10, 0), (0, 10, K.LOWER_BACK_Z, 18, 7, 18, 0),
                   (0, 3, K.LOWER_BACK_Z, 13, 6, 28, 0), (0, -1, K.LOWER_BACK_Z, 8, 4, 40, 0)], root_mat="spikeroot")
    # Smaller spikes fanning out over the shoulder blades and down the flanks of the back.
    side_rows = []
    for _side, sx in C.SIDES:
        side_rows += [(sx * 12, 24, K.UPPER_BACK_Z, 10, 4, 8, 35), (sx * 13, 17, K.UPPER_BACK_Z, 9, 4, 16, 40),
                      (sx * 14, 9, K.LOWER_BACK_Z, 8, 4, 26, 45)]
    K.ridge(m, B, side_rows, prefix="shell_backspike", root_mat="spikeroot")
    # The chimney at the top of the spine, leaning back with it (a little less than the back does).
    vol = C.volcano(m, B["torso"], K.T(B, 0, 29, K.UPPER_BACK_Z), direction=C.back_dir(-12, 0),
                    levels=[(16, 14, 5), (14, 12, 6), (12, 11, 6), (12, 10, 5)], crater=(8, 6), sink=3, molten_sink=7,
                    rock="obsidian", rim="obsidian", rim_spikes=4, grow=1.2, fountain=16, bombs=4)
    K.molten_spikes(m, B, back=[(0, 10, 14, 10, 4, 22, 0), (0, 3, 14, 8, 4, 32, 0)],
                    shoulder=((-4, 8, 15), (2, 11, -10)))
    return dict(volcanoes=[vol])


def materials():
    return {
        "lava": M.lava(seed=51, crust=1.1, hot=-0.5, edge_cool=1.0, plates=0.5, plate_cell=8.0),
        "molten": M.lava(seed=52, crust=1.2, hot=0.8, edge_cool=0.4),
        "fountain": M.lava(seed=53, crust=1.3, hot=1.4, edge_cool=0.2),
        "crater_lava": M.crater_pool(seed=54),
        "rock": M.rock(seed=55, chunk=10.0, obsidian=0.38, cracks=0.14, fissures=0.12, fissure_cell=30.0, heat=-0.7,
                       cobble=M.COBBLE_DARK),
        "obsidian": M.rock(seed=56, chunk=7.0, obsidian=1.0, cracks=0.12, fissures=0.0, heat=-0.6),
        "spikeroot": M.rock(seed=60, chunk=3.5, obsidian=1.0, cracks=0.6, fissures=0.0, heat=-0.2, crack_width=0.6),
        "mask": M.rock(seed=57, chunk=6.0, obsidian=0.0, cracks=0.0, fissures=0.0, cobble=M.COBBLE_OLD),
        "basalt": M.basalt(seed=58, cracks=0.1),
        "fang": M.fang(seed=59),
    }


def overlays():
    return [M.face_overlay(
        eye_px=[(-5, 0), (-4, 1), (3, 1), (4, 0), (-1, 4), (-1, 5), (-1, 6)],
        sockets=[(4.0, 0.5, 6, 3), (-4.5, 0.5, 6, 3)],
        cracks=[((-1, 7), (-1, 9), (-2, 11)), ((-1, 3), (0, 0), (-1, -4)), ((6, 2), (9, 4)), ((-7, 2), (-9, 5))],
        skull_tag="skull", core_tag="cranium")]


FLOWS = dict(reach=76.0, streams=0.5, seed=501)

ANIM = dict(
    weight=1.3,
    walk_cycle=2.2,
    stride=30.0,
    arm_lift=8.0,
    leg_lift=6.0,
    bob=2.0,
    sway=2.0,
    roll=3.0,
    idle_period=4.8,
    roar=0.9,
    plate_speed=1.0,
)
