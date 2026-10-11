"""
No. 3A - Old Calderon (round 2: the volcano grows).

The original No. 3, grown: a mountain that learned to walk, now about five and a half blocks to the rim of the
volcano on its back. Stooped and broad, basalt on its shoulders, a beard of obsidian stalactites, three eyes; a row
of basalt spikes climbs its spine to a stepped volcano with a jagged basalt rim. Dormant, it's mostly rock with a
few dim red cracks and a smoking crater. Halfway through the fight the volcano erupts and lava pours down its back,
shoulders and arms. When the shell breaks, a bigger volcano of pure lava grows out of its back and keeps erupting.
"""
from designs import common as C
from kit import materials as M

NUMBER = 1
LABEL = "3A"
ID = "calderon"
NAME = "Old Calderon"
MOOD = "volcano back"
BLURB = "The original No. 3, grown: a stepped volcano on its hump, basalt spikes up its spine."


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

    # ------------------------------------------------------------ core (old, heavy lava)
    pel.add((-14, -7, -9), (28, 15, 18), "lava")
    tor.add((-22, -3, -14), (44, 39, 28), "lava")                 # chest
    tor.add((-18, 15, 7), (36, 22, 11), "lava")                   # upper back mass
    tor.add((-15, 0, -17), (30, 16, 4), "lava")                   # belly
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

    # ------------------------------------------------------------ shell: worn stone, basalt, spikes
    def tplate(name, x0, y0, z0, x1, y1, z1, mat="rock", rot=None):
        cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
        C.plate(m, tor, name, (tp[0] + cx, tp[1] + cy, tp[2] + cz), (x1 - x0, y1 - y0, z1 - z0), mat, rotation=rot)

    C.plate(m, pel, "shell_hips", (0, pp[1] + 0.5, pp[2] + 0.5), (31, 17, 21), "rock")
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * 0.5, sx * 24.5))
        tplate(f"shell_back_{side}", lo, -2.5, 13.5, hi, 15.5, 18.5)
        tplate(f"shell_chest_{side}", lo, 10.5, -18.5, hi, 27.5, -13.5)     # stops short of the chin
        flo, fhi = sorted((sx * 21.5, sx * 25.5))
        tplate(f"shell_flank_{side}", flo, 0, -13, fhi, 33, 12)
    tplate("shell_belly", -16, -1.5, -21.5, 16, 15.5, -16.5, mat="obsidian")
    tplate("shell_mantle", -19, 14.5, 17.5, 19, 37.5, 22.5)          # the mountain's shoulders
    tplate("shell_traps", -17, 35.5, -4.5, 17, 38.5, 16.5)
    # Basalt spikes climbing the spine to the volcano.
    for i, (y, h) in enumerate(((4, 7), (10, 9))):
        C.spike(m, tor, f"shell_spine_{i}", (0, tp[1] + y, tp[2] + 18.5), h, 4, lean=(-55, 0, 0), mat="basalt")
    # The volcano, standing upright on the hump.
    vol = C.volcano(m, tor, (0, tp[1] + 27, tp[2] + 20), tilt,
                    levels=[(28, 24, 6), (23, 19, 5), (18, 15, 5), (14, 12, 4)], crater=(8, 6), rim_spikes=4,
                    rock="rock", rim="basalt")
    # Head: a worn skull, a heavy brow, a crown of basalt and a beard of obsidian stalactites.
    C.plate(m, head, "shell_skull", (0, hp[1] + 4, hp[2] - 7), (21, 15, 19), "mask", tag="skull")
    C.plate(m, head, "shell_brow", (0, hp[1] + 8.5, hp[2] - 16), (23, 5, 5), "rock")
    for i, (x, h) in enumerate(((-6, 6), (0, 9), (6, 5))):
        C.plate(m, head, f"shell_crown_{i}", (x, hp[1] + 12.5 + h / 2.0, hp[2] - 8), (4, h, 4), "basalt")
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4.5, jp[2] - 10), (21, 8, 17), "rock")
    for i, (x, length) in enumerate(((-7, 5), (-4, 8), (-1, 10), (2, 9), (5, 7), (8, 4))):
        C.plate(m, jaw, f"shell_beard_{i}", (x, jp[1] - 9 - length / 2.0, jp[2] - 16), (2, length, 2), "obsidian")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        C.plate(m, arm, f"shell_pauldron_{side}", (ap[0] + sx * 1, ap[1] - 1, ap[2]), (21, 13, 21), "rock")
        cols = ((-5, 7), (1, 11), (6, 6), (-1, 5)) if side == "l" else ((-5, 6), (2, 9), (6, 5))
        for i, (dz, h) in enumerate(cols):
            C.plate(m, arm, f"shell_column_{side}{i}", (ap[0] + sx * (3 + (i % 2) * 4), ap[1] + 5.5 + h / 2.0, ap[2] + dz),
                    (5, h, 5), "basalt")
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 12, ap[2]), (19, 12, 19), "rock")
        C.plate(m, fore, f"shell_forearm_{side}", (fp[0], fp[1] - 8.5, fp[2]), (24, 15, 24), "rock")
        for i, dy in enumerate((-3, -9)):
            C.spike(m, fore, f"shell_armspike_{side}{i}", (fp[0] + sx * 12, fp[1] + dy, fp[2] + 3), 6, 3,
                    lean=(0, 0, -sx * 75), mat="obsidian", steps=2)
        C.plate(m, fore, f"shell_cuff_{side}", (fp[0], fp[1] - 16.5, fp[2]), (22, 3, 22), "basalt")
        C.plate(m, fist, f"shell_fist_{side}", (kp[0], kp[1] - 4, kp[2] + 1.5), (25, 12, 19), "rock")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 6.5, kp[2] - 12), (25, 12, 4), "obsidian")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_thigh_{side}", (tp2[0], tp2[1] - 4, tp2[2]), (19, 16, 19), "rock")
        C.plate(m, shin, f"shell_shin_{side}", (sp[0], sp[1] - 5, sp[2]), (17, 11, 16), "rock")
        C.plate(m, foot, f"shell_foot_{side}", (fp2[0], fp2[1] - 2, fp2[2] - 3), (20, 6, 20), "rock")

    # ------------------------------------------------------------ molten extras: lava spikes on the shoulders
    for side, sx in C.SIDES:
        ap = B[f"arm_{side}"].pivot
        for i, (dz, h) in enumerate(((-4, 8), (3, 11))):
            C.spike(m, B[f"arm_{side}"], f"molten_spike_{side}{i}", (ap[0] + sx * 4, ap[1] + 5, ap[2] + dz), h, 4,
                    lean=(0, 0, -sx * 20), mat="molten", kind="molten")
    return dict(volcanoes=[vol])


def materials():
    return {
        "lava": M.lava(seed=31, crust=1.1, hot=-0.7, edge_cool=1.0, plates=0.6, plate_cell=9.0),
        "molten": M.lava(seed=32, crust=1.2, hot=0.6, edge_cool=0.5, plates=0.25, plate_cell=6.0),
        "fountain": M.lava(seed=33, crust=1.3, hot=1.4, edge_cool=0.2),
        "crater_lava": M.crater_pool(seed=34),
        "rock": M.rock(seed=35, chunk=10.0, obsidian=0.1, cracks=0.18, fissures=0.15, fissure_cell=30.0, heat=-0.6,
                       cobble=M.COBBLE_OLD),
        "obsidian": M.rock(seed=36, chunk=7.0, obsidian=1.0, cracks=0.1, fissures=0.0, heat=-0.6),
        "mask": M.rock(seed=37, chunk=6.0, obsidian=0.0, cracks=0.0, fissures=0.0, cobble=M.COBBLE_OLD),
        "basalt": M.basalt(seed=38, cracks=0.1),
        "fang": M.fang(seed=39),
    }


def overlays():
    return [M.face_overlay(
        # Two embers deep in the dark under the brow, and a third eye: a vertical crack of fire in the forehead.
        eye_px=[(-5, 0), (4, 0), (-1, 4), (-1, 5), (-1, 6)],
        sockets=[(4.5, 0.5, 6, 3), (-4.5, 0.5, 6, 3)],
        cracks=[((-1, 7), (-2, 9), (-2, 11)), ((-1, 3), (0, 0), (-1, -4)),
                ((7, 1), (9, -2)), ((-7, 1), (-9, 3))],
        skull_tag="skull", core_tag="cranium")]


FLOWS = dict(reach=78.0, streams=0.55, seed=301)

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
