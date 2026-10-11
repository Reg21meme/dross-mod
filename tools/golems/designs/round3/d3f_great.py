"""
No. 3F - Great Calderon (one great volcano, round 3).

The bigger volcano, lined up with the back: its foot covers the whole upper back (there are no back plates there,
the volcano IS its back), and the cone rises out of it leaning back with the body, up to a wide caldera full of lava.
Dark scorched stone like 3C, spikes pointing up off its shoulders, 3D's beard, and a few obsidian spikes down its
lower back like a tail ridge. Erupting, the caldera blows and lava sheets down its back and shoulders; as lava, an
even bigger volcano of molten rock erupts on its back.
"""
from designs import calderon as K
from designs import common as C
from kit import materials as M

NUMBER = 2
LABEL = "3F"
ID = "great"
NAME = "Great Calderon"
MOOD = "great volcano"
BLURB = "The bigger volcano: its foot is the whole upper back, its caldera full of lava. Dark stone, shoulder spikes."


def body():
    return K.body()


def build(m, b):
    B = C.skeleton(m, b)
    jaw, _jaw_in = K.core(m, B)
    # The lower back plates stop where the volcano's foot begins; there are no upper back plates.
    K.torso_shell(m, B, upper_back=False)
    K.head_shell(m, B, jaw)
    K.limb_shell(m, B)
    # The tail ridge: spikes down the lower back, raked further and further towards the tail.
    K.ridge(m, B, root_mat="spikeroot", spikes=[(0, 8.5, K.LOWER_BACK_Z, 8, 5, 30, 0), (0, 2.5, K.LOWER_BACK_Z, 6, 4, 38, 0)])
    # The great volcano: its foot sits on the lava hump, its cone leans back with the back.
    vol = C.volcano(m, B["torso"], K.T(B, 0, 24, K.HUMP_Z), direction=C.back_dir(-4, 0),
                    levels=[(40, 28, 6), (33, 23, 5), (27, 18, 5), (22, 14, 4), (19, 13, 4)], crater=(11, 7), sink=1,
                    molten_sink=4, rock="vrock", rim="obsidian", rim_spikes=4, tooth_mat="obsidian",
                    molten_levels=[(38, 30, 7), (32, 25, 6), (26, 20, 6), (20, 15, 6), (15, 11, 5)],
                    fountain=16, bombs=5)
    # Obsidian crags breaking out of the volcano's lowest slope, leaning out with it (the holder's frame: y is
    # along the cone, z down the spine).
    bx, by, bz = vol.holder.pivot
    for side, sx in C.SIDES:
        C.spike(m, vol.holder, f"shell_crag_{side}", (bx + sx * 18, by + 5, bz + 2), 7, 4,
                direction=(sx * 0.8, 1.0, 0.0), mat="obsidian", embed=2)
    K.molten_spikes(m, B, back=[(0, 6, 14, 7, 4, 30, 0)])
    return dict(volcanoes=[vol])


def materials():
    return {
        "lava": M.lava(seed=71, crust=1.1, hot=-0.6, edge_cool=1.0, plates=0.55, plate_cell=9.0),
        "molten": M.lava(seed=72, crust=1.2, hot=0.6, edge_cool=0.5, plates=0.25, plate_cell=6.0),
        "fountain": M.lava(seed=73, crust=1.3, hot=1.4, edge_cool=0.2),
        "crater_lava": M.crater_pool(seed=74),
        "rock": M.rock(seed=75, chunk=10.0, obsidian=0.3, cracks=0.15, fissures=0.12, fissure_cell=30.0, heat=-0.7,
                       cobble=M.COBBLE_DARK),
        "vrock": M.rock(seed=80, chunk=9.0, obsidian=0.5, cracks=0.22, fissures=0.18, fissure_cell=26.0, heat=-0.5,
                        cobble=M.COBBLE_DARK),
        "obsidian": M.rock(seed=76, chunk=7.0, obsidian=1.0, cracks=0.1, fissures=0.0, heat=-0.6),
        "spikeroot": M.rock(seed=81, chunk=3.5, obsidian=1.0, cracks=0.6, fissures=0.0, heat=-0.2, crack_width=0.6),
        "mask": M.rock(seed=77, chunk=6.0, obsidian=0.0, cracks=0.0, fissures=0.0, cobble=M.COBBLE_OLD),
        "basalt": M.basalt(seed=78, cracks=0.1),
        "fang": M.fang(seed=79),
    }


def overlays():
    return [M.face_overlay(
        # Two embers under the brow and the third eye, a crack of fire up the forehead (Old Calderon's face).
        eye_px=[(-5, 0), (4, 0), (-1, 4), (-1, 5), (-1, 6)],
        sockets=[(4.5, 0.5, 6, 3), (-4.5, 0.5, 6, 3)],
        cracks=[((-1, 7), (-2, 9), (-2, 11)), ((-1, 3), (0, 0), (-1, -4)), ((7, 1), (9, -2)), ((-7, 1), (-9, 3))],
        skull_tag="skull", core_tag="cranium")]


FLOWS = dict(reach=78.0, streams=0.6, seed=701)

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
