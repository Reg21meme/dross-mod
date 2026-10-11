"""
No. 3 - Old Calderon (ancient).

A mountain that learned to walk, older than the Nether's fortresses: stooped and broad, its stone worn and broken
open in places, cracks cooled to a deep red. A ring of basalt columns on its back holds a small volcano's crater,
lava simmering inside. Basalt crowns its shoulders and its head, and a beard of obsidian stalactites hangs from
its jaw. Three eyes: two embers deep under the brow and a crack of fire in its forehead. In the core form the
crater bursts into a fountain of lava.
"""
import math

from designs import common as C
from kit import materials as M

NUMBER = 3
ID = "calderon"
NAME = "Old Calderon"
MOOD = "ancient"
BLURB = "A mountain that learned to walk: a volcano's crater on its back, a beard of obsidian stalactites."


def body():
    return C.Body(
        hip_y=20, hip_z=5, pelvis_pitch=13, torso_pitch=40, spine_base=8, torso_len=29,
        shoulder_x=22, shoulder_drop=7, shoulder_dz=0,
        upper_len=15, fore_len=15, hip_x=11, thigh_len=11, shin_len=10,
        neck_drop=7, neck_dz=0, head_look_down=4,
        fist_target=(23.0, -14.0), fist_heading=(0.12, -1.0), fist_contact=(0.0, -12.0, 0.0),
        foot_target=(13.0, 8.0), foot_heading=(0.15, -1.0), foot_contact=(0.0, -6.0, -2.0),
    )


def build(m, b):
    B = C.skeleton(m, b)
    pel, tor, head = B["pelvis"], B["torso"], B["head"]
    pp, tp, hp = pel.pivot, tor.pivot, head.pivot

    # ------------------------------------------------------------ core (old, heavy lava)
    pel.add((-12, -6, -8), (24, 13, 16), "lava")
    tor.add((-19, -3, -12), (38, 33, 24), "lava")                 # chest
    tor.add((-15, 12, 6), (30, 19, 10), "lava")                   # upper back mass
    tor.add((-13, 0, -15), (26, 14, 4), "lava")                   # belly
    tor.add((-5, 17, 15), (10, 10, 3), "molten_core", tag="crater")  # the crater's lava pool (always shows)
    head.add((-7.5, -3, -13), (15, 12, 14), "lava", tag="cranium")
    head.add((-6, -7, -12), (2, 4, 2), "fang")
    head.add((4, -7, -12), (2, 4, 2), "fang")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 2, hp[2] - 1), (0, hp[1] - 1, hp[2] - 2))
    jp = jaw.pivot
    jaw.add((-8, -6, -15), (16, 6, 15), "lava", tag="jaw")
    jaw_in.add((-5, -4, -11), (10, 4, 9), "lava")
    for x in (-4, -1, 2):
        jaw_in.add((x, 0, -10.5), (2, 2, 2), "fang")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        arm.add((-6.5, -15, -6.5), (13, 20, 13), "lava")
        fore.add((-8, -15, -8), (16, 16, 16), "lava")
        fist.add((-9.5, -12, -8.5), (19, 13, 17), "lava")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        thigh.add((-6.5, -11, -6.5), (13, 15, 13), "lava")
        shin.add((-6, -10, -5.5), (12, 11, 11), "lava")
        foot.add((-7, -6, -10), (14, 6, 15), "lava")

    # ------------------------------------------------------------ shell: worn stone, basalt, a volcano
    def tplate(name, x0, y0, z0, x1, y1, z1, mat="rock", rot=None, tag=None):
        cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
        C.plate(m, tor, name, (tp[0] + cx, tp[1] + cy, tp[2] + cz), (x1 - x0, y1 - y0, z1 - z0), mat,
                rotation=rot, tag=tag)

    C.plate(m, pel, "shell_hips", (0, pp[1] + 0.5, pp[2] + 0.5), (27, 15, 19), "rock")
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * 0.5, sx * 20.5))
        tplate(f"shell_back_{side}", lo, -2.5, 11.5, hi, 13.5, 16.5, mat="broken")
        tplate(f"shell_chest_{side}", lo, 14.5, -15.5, hi, 30.5, -11.5, mat="broken")
        flo, fhi = sorted((sx * 18.5, sx * 22.5))
        tplate(f"shell_flank_{side}", flo, 0, -11, fhi, 27, 10)
    tplate("shell_belly", -14, -1.5, -18.5, 14, 13.5, -14.5, mat="obsidian")
    tplate("shell_traps", -14, 29.5, -2.5, 14, 32.5, 11.5)
    # The crater: a ring of basalt columns round the lava pool on its back.
    for i in range(9):
        a = 2.0 * math.pi * (i + 0.3) / 9.0
        cx, cy = 7.5 * math.cos(a), 22.0 + 7.5 * math.sin(a)
        h = (8, 12, 9, 14, 7, 11, 6, 13, 9)[i]
        tplate(f"shell_column_{i}", cx - 2, cy - 2, 15.5, cx + 2, cy + 2, 15.5 + h, mat="basalt")
    tplate("shell_mantle", -16, 10, 15.5, 16, 16, 19.5)          # the mountain's shoulders below the crater
    # Head: a worn skull, a heavy brow, a broken crown and a beard of obsidian stalactites.
    C.plate(m, head, "shell_skull", (0, hp[1] + 3.5, hp[2] - 5.5), (18, 13, 17), "mask", tag="skull")
    C.plate(m, head, "shell_brow", (0, hp[1] + 7.5, hp[2] - 13.5), (20, 5, 5), "rock")
    for i, (x, h) in enumerate(((-5, 5), (0, 7), (5, 4))):
        C.plate(m, head, f"shell_crown_{i}", (x, hp[1] + 11 + h / 2.0, hp[2] - 6), (3, h, 3), "basalt")
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4, jp[2] - 7), (18, 7, 17), "rock")
    for i, (x, length) in enumerate(((-6, 6), (-3.5, 10), (-1, 13), (1.5, 11), (4, 8), (6.5, 4))):
        C.plate(m, jaw, f"shell_beard_{i}", (x, jp[1] - 7.5 - length / 2.0, jp[2] - 13), (2, length, 2), "obsidian")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        C.plate(m, arm, f"shell_pauldron_{side}", (ap[0] + sx * 1.5, ap[1] - 1, ap[2]), (19, 11, 19), "rock")
        # Basalt columns on the shoulder; the right one is broken (fewer, shorter, one leaning).
        cols = ((-4, 6), (1, 9), (5, 5), (-1, 4)) if side == "l" else ((-4, 4), (2, 6))
        for i, (dz, h) in enumerate(cols):
            lean = (0, 0, 0) if not (side == "r" and i == 1) else (0, 0, 25)
            C.plate(m, arm, f"shell_column_{side}{i}", (ap[0] + sx * (3 + (i % 2) * 3), ap[1] + 4 + h / 2.0, ap[2] + dz),
                    (4, h, 4), "basalt", rotation=lean)
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 10, ap[2]), (15, 10, 15), "broken")
        C.plate(m, fore, f"shell_forearm_{side}", (fp[0], fp[1] - 7, fp[2]), (20, 13, 20), "rock")
        C.plate(m, fore, f"shell_cuff_{side}", (fp[0], fp[1] - 13.5, fp[2]), (18, 3, 18), "basalt")
        C.plate(m, fist, f"shell_fist_{side}", (kp[0], kp[1] - 3.5, kp[2] + 1.5), (22, 10, 16), "rock")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 5.5, kp[2] - 9.5), (22, 10, 4), "obsidian")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_thigh_{side}", (tp2[0], tp2[1] - 3.5, tp2[2]), (15, 14, 15), "rock")
        C.plate(m, shin, f"shell_shin_{side}", (sp[0], sp[1] - 4.5, sp[2]), (14, 10, 13), "rock")
        C.plate(m, foot, f"shell_foot_{side}", (fp2[0], fp2[1] - 1.5, fp2[2] - 2.5), (16, 5, 16), "rock")

    # ------------------------------------------------------------ molten extras: the crater erupts
    for i, (x, y, h) in enumerate(((0, 22, 14), (-3, 20, 9), (3, 24, 10), (1, 19, 6), (-2, 25, 7))):
        C.plate(m, tor, f"molten_plume_{i}", (x, tp[1] + y, tp[2] + 17 + h / 2.0), (3 if i else 4, 3 if i else 4, h),
                "molten", kind="molten")
    return B


def materials():
    return {
        "lava": M.lava(seed=31, crust=1.1, hot=-0.7, edge_cool=1.0, plates=0.6, plate_cell=8.0),
        "molten": M.lava(seed=32, crust=1.2, hot=1.2, edge_cool=0.3),
        "molten_core": M.lava(seed=33, crust=1.2, hot=0.8, edge_cool=0.5),
        "rock": M.rock(seed=34, chunk=9.5, obsidian=0.1, cracks=0.3, fissures=0.35, fissure_cell=28.0, heat=-0.4,
                       cobble=M.COBBLE_OLD),
        "broken": M.rock(seed=35, chunk=8.5, obsidian=0.1, cracks=0.3, fissures=0.3, fissure_cell=28.0, heat=-0.4,
                         cobble=M.COBBLE_OLD, holes=0.05),
        "obsidian": M.rock(seed=36, chunk=7.0, obsidian=1.0, cracks=0.15, fissures=0.0, heat=-0.4),
        "mask": M.rock(seed=37, chunk=6.0, obsidian=0.0, cracks=0.0, fissures=0.0, cobble=M.COBBLE_OLD),
        "basalt": M.basalt(seed=38, cracks=0.2),
        "fang": M.fang(seed=39),
    }


def overlays():
    return [M.face_overlay(
        # Two embers deep in the dark under the brow, and a third eye: a vertical crack of fire in the forehead.
        eye_px=[(-4, 0), (3, 0), (-1, 3), (-1, 4), (-1, 5)],
        sockets=[(3.5, 0.5, 5, 3), (-3.5, 0.5, 5, 3)],
        cracks=[((-1, 6), (-2, 8), (-2, 10)), ((-1, 2), (0, -1), (-1, -4)),
                ((6, 1), (8, -2)), ((-6, 1), (-8, 3))],
        skull_tag="skull", core_tag="cranium")]


ANIM = dict(
    weight=1.35,
    walk_cycle=2.2,
    stride=26.0,
    arm_lift=7.0,
    leg_lift=5.0,
    bob=1.8,
    sway=2.0,
    roll=3.0,
    idle_period=5.0,
    roar=0.85,
    plate_speed=0.9,
)
