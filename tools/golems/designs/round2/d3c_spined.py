"""
No. 3C - Spined Calderon (spikes).

Old Calderon grown spikes: two horns on its brow, a dragon-like ridge of great obsidian spikes from the back of its
head down its spine to a tall chimney of a volcano, spiked shoulders and rows of spikes along its forearms. Its
three eyes and beard stay. Erupting, the chimney floods and lava runs down between the spikes; in the core form a
ridge of molten spikes stands along its back and the chimney spews lava.
"""
from designs import common as C
from kit import materials as M

NUMBER = 3
LABEL = "3C"
ID = "spined"
NAME = "Spined Calderon"
MOOD = "spiked"
BLURB = "Horns, a ridge of great obsidian spikes down its spine, and a tall chimney of a volcano."


def body():
    return C.Body(
        hip_y=24, hip_z=6, pelvis_pitch=12, torso_pitch=38, spine_base=9, torso_len=35,
        shoulder_x=24, shoulder_drop=8, shoulder_dz=0,
        upper_len=18, fore_len=18, hip_x=13, thigh_len=13, shin_len=12,
        neck_drop=4, neck_dz=-11, head_look_down=4,
        fist_target=(25.0, -18.0), fist_heading=(0.12, -1.0), fist_contact=(0.0, -14.0, 0.0),
        foot_target=(14.0, 7.0), foot_heading=(0.15, -1.0), foot_contact=(0.0, -7.0, -2.0),
    )


def build(m, b):
    B = C.skeleton(m, b)
    pel, tor, head = B["pelvis"], B["torso"], B["head"]
    pp, tp, hp = pel.pivot, tor.pivot, head.pivot
    tilt = -(b.pelvis_pitch + b.torso_pitch)

    # ------------------------------------------------------------ core
    pel.add((-14, -7, -9), (28, 15, 18), "lava")
    tor.add((-21, -3, -14), (42, 39, 28), "lava")
    tor.add((-17, 15, 7), (34, 22, 11), "lava")
    tor.add((-15, 0, -17), (30, 16, 4), "lava")
    head.add((-9, -3, -16), (18, 14, 17), "lava", tag="cranium")
    head.add((-7, -8, -15), (2, 5, 2), "fang")
    head.add((5, -8, -15), (2, 5, 2), "fang")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 2, hp[2] - 1), (0, hp[1] - 1, hp[2] - 2))
    jp = jaw.pivot
    jaw.add((-9.5, -7, -18), (19, 7, 18), "lava", tag="jaw")
    jaw_in.add((-6, -5, -14), (12, 5, 12), "lava")
    for x in (-5, -1, 3):
        jaw_in.add((x, 0, -13.5), (2, 2, 2), "fang")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        arm.add((-8, -18, -8), (16, 24, 16), "lava")
        fore.add((-9.5, -18, -9.5), (19, 19, 19), "lava")
        fist.add((-11, -14, -10.5), (22, 15, 20), "lava")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        thigh.add((-8, -13, -8), (16, 17, 16), "lava")
        shin.add((-7, -12, -6.5), (14, 13, 13), "lava")
        foot.add((-8.5, -7, -12), (17, 7, 18), "lava")

    # ------------------------------------------------------------ shell: stone, a spine of spikes, a chimney
    def tplate(name, x0, y0, z0, x1, y1, z1, mat="rock", rot=None):
        cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
        C.plate(m, tor, name, (tp[0] + cx, tp[1] + cy, tp[2] + cz), (x1 - x0, y1 - y0, z1 - z0), mat, rotation=rot)

    C.plate(m, pel, "shell_hips", (0, pp[1] + 0.5, pp[2] + 0.5), (31, 17, 21), "rock")
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * 1.5, sx * 23.5))
        tplate(f"shell_back_{side}", lo, -2.5, 13.5, hi, 15.5, 18.5)
        tplate(f"shell_upper_{side}", sorted((sx * 1.5, sx * 19.5))[0], 15.5, 17.5, sorted((sx * 1.5, sx * 19.5))[1],
               36.5, 22.5)
        tplate(f"shell_chest_{side}", lo, 10.5, -18.5, hi, 27.5, -13.5)
        flo, fhi = sorted((sx * 20.5, sx * 24.5))
        tplate(f"shell_flank_{side}", flo, 0, -13, fhi, 33, 12)
    tplate("shell_belly", -16, -1.5, -21.5, 16, 15.5, -16.5, mat="obsidian")
    tplate("shell_traps", -16, 35.5, -4.5, 16, 38.5, 16.5)
    # The spine: a channel down the middle of the back, lined with great obsidian spikes.
    for i, (y, z, h, w) in enumerate(((0, 18.5, 12, 5), (8, 20.5, 16, 6), (17, 22.5, 20, 7), (26, 22.5, 16, 6))):
        C.spike(m, tor, f"shell_spine_{i}", (0, tp[1] + y, tp[2] + z), h, w, lean=(-50 + i * 6, 0, 0), mat="obsidian")
    # The chimney: a tall, narrow volcano at the top of the spine.
    vol = C.volcano(m, tor, (0, tp[1] + 30, tp[2] + 16), tilt,
                    levels=[(18, 16, 5), (15, 13, 6), (13, 11, 6), (12, 10, 5)], crater=(6, 4), rim_spikes=4,
                    rock="obsidian", rim="obsidian", grow=1.2, fountain=16, bombs=4)
    # Head: horns and the beard.
    C.plate(m, head, "shell_skull", (0, hp[1] + 4, hp[2] - 7), (21, 15, 19), "mask", tag="skull")
    C.plate(m, head, "shell_brow", (0, hp[1] + 8.5, hp[2] - 16), (23, 5, 5), "rock")
    for side, sx in C.SIDES:
        C.spike(m, head, f"shell_horn_{side}", (sx * 8, hp[1] + 10, hp[2] - 8), 15, 5, lean=(-35, 0, -sx * 30),
                mat="obsidian")
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4.5, jp[2] - 10), (21, 8, 17), "rock")
    for i, (x, length) in enumerate(((-6, 6), (-3, 9), (0, 11), (3, 8), (6, 5))):
        C.plate(m, jaw, f"shell_beard_{i}", (x, jp[1] - 9 - length / 2.0, jp[2] - 16), (2, length, 2), "obsidian")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        C.plate(m, arm, f"shell_pauldron_{side}", (ap[0] + sx * 1, ap[1] - 1, ap[2]), (21, 13, 21), "rock")
        for i, (dz, h, lean) in enumerate(((-6, 12, 20), (0, 17, 0), (6, 12, -20))):
            C.spike(m, arm, f"shell_shoulderspike_{side}{i}", (ap[0] + sx * 5, ap[1] + 5, ap[2] + dz), h, 4,
                    lean=(lean, 0, -sx * 25), mat="obsidian")
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 12, ap[2]), (19, 12, 19), "rock")
        C.plate(m, fore, f"shell_forearm_{side}", (fp[0], fp[1] - 8.5, fp[2]), (24, 15, 24), "rock")
        for i, dy in enumerate((-2, -8, -14)):
            C.spike(m, fore, f"shell_armspike_{side}{i}", (fp[0] + sx * 12, fp[1] + dy, fp[2] + 4), 9, 4,
                    lean=(0, 0, -sx * 70), mat="obsidian", steps=2)
        C.plate(m, fore, f"shell_cuff_{side}", (fp[0], fp[1] - 16.5, fp[2]), (22, 3, 22), "obsidian")
        C.plate(m, fist, f"shell_fist_{side}", (kp[0], kp[1] - 4, kp[2] + 1.5), (25, 12, 19), "rock")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 6.5, kp[2] - 12), (25, 12, 4), "obsidian")
        for i, x in enumerate((-8, -2.5, 3, 8.5)):
            C.spike(m, fist, f"shell_stud_{side}{i}", (kp[0] + x, kp[1] - 4, kp[2] - 14), 3, 3, lean=(-90, 0, 0),
                    mat="obsidian", steps=2)
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_thigh_{side}", (tp2[0], tp2[1] - 4, tp2[2]), (19, 16, 19), "rock")
        C.plate(m, shin, f"shell_shin_{side}", (sp[0], sp[1] - 5, sp[2]), (17, 11, 16), "rock")
        C.plate(m, foot, f"shell_foot_{side}", (fp2[0], fp2[1] - 2, fp2[2] - 3), (20, 6, 20), "rock")

    # ------------------------------------------------------------ molten extras: a ridge of lava spikes
    for i, (y, z, h, w) in enumerate(((2, 15, 10, 4), (10, 17, 13, 5), (19, 18, 15, 5))):
        C.spike(m, tor, f"molten_spine_{i}", (0, tp[1] + y, tp[2] + z), h, w, lean=(-48 + i * 6, 0, 0), mat="molten",
                kind="molten")
    for side, sx in C.SIDES:
        ap = B[f"arm_{side}"].pivot
        C.spike(m, B[f"arm_{side}"], f"molten_shoulder_{side}", (ap[0] + sx * 4, ap[1] + 5, ap[2]), 11, 4,
                lean=(0, 0, -sx * 25), mat="molten", kind="molten")
    return dict(volcanoes=[vol])


def materials():
    return {
        "lava": M.lava(seed=51, crust=1.1, hot=-0.5, edge_cool=1.0, plates=0.5, plate_cell=8.0),
        "molten": M.lava(seed=52, crust=1.2, hot=0.8, edge_cool=0.4),
        "fountain": M.lava(seed=53, crust=1.3, hot=1.4, edge_cool=0.2),
        "crater_lava": M.crater_pool(seed=54),
        "rock": M.rock(seed=55, chunk=10.0, obsidian=0.18, cracks=0.18, fissures=0.15, fissure_cell=30.0, heat=-0.6,
                       cobble=M.COBBLE_OLD),
        "obsidian": M.rock(seed=56, chunk=7.0, obsidian=1.0, cracks=0.12, fissures=0.0, heat=-0.6),
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
