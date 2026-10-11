"""
No. 2 - Emberstride (lean).

A lanky ember ape: a tall, narrow hunch, gibbon-long arms that plant its fists far out in front, and a ridge of
obsidian spikes down its spine. Its shell is thin and segmented, so the lava glows between the plates. Its face
is all slanted eye-slits and a long crocodile jaw. In the core form the spikes become a crest of flame.
"""
from designs import common as C
from kit import materials as M

NUMBER = 2
ID = "emberstride"
NAME = "Emberstride"
MOOD = "lean"
BLURB = "A lanky ember ape: long gibbon arms, a spined back, lava glowing between thin plates."


def body():
    return C.Body(
        hip_y=25, hip_z=6, pelvis_pitch=6, torso_pitch=34, spine_base=8, torso_len=31,
        shoulder_x=17, shoulder_drop=6, shoulder_dz=1,
        upper_len=22, fore_len=23, hip_x=8, thigh_len=15, shin_len=14,
        neck_drop=4, neck_dz=1, head_look_down=6,
        fist_target=(22.0, -18.0), fist_heading=(0.15, -1.0), fist_contact=(0.0, -11.0, 0.0),
        foot_target=(10.0, 9.0), foot_heading=(0.1, -1.0), foot_contact=(0.0, -5.0, -2.0),
    )


def build(m, b):
    B = C.skeleton(m, b)
    pel, tor, head = B["pelvis"], B["torso"], B["head"]
    pp, tp, hp = pel.pivot, tor.pivot, head.pivot

    # ------------------------------------------------------------ core (lava body): lean and long
    pel.add((-9, -6, -7), (18, 13, 14), "lava")
    tor.add((-13, -3, -9), (26, 33, 18), "lava")                  # chest
    tor.add((-10, 12, 5), (20, 19, 7), "lava")                    # upper back
    tor.add((-9, 1, -11), (18, 13, 3), "lava")                    # belly
    head.add((-6, -2, -13), (12, 10, 14), "lava", tag="cranium")
    head.add((-5, -6, -14), (2, 4, 2), "fang")
    head.add((3, -6, -14), (2, 4, 2), "fang")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 1, hp[2]), (0, hp[1] - 0.5, hp[2] - 1))
    jp = jaw.pivot
    jaw.add((-6, -5, -18), (12, 5, 18), "lava", tag="jaw")      # long crocodile jaw
    for x in (-5.5, 3.5):
        jaw.add((x, 0, -17.5), (2, 3, 2), "fang")
    jaw_in.add((-4, -3.5, -14), (8, 3, 12), "lava")
    for z in (-13.5, -10.5):
        for x in (-3, 1):
            jaw_in.add((x, -0.5, z), (2, 2, 2), "fang")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        arm.add((-5, -22, -5), (10, 26, 10), "lava")
        fore.add((-6, -23, -6), (12, 24, 12), "lava")
        fist.add((-7.5, -11, -7.5), (15, 12, 14), "lava")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        thigh.add((-5, -15, -5.5), (10, 18, 11), "lava")
        shin.add((-4.5, -14, -4.5), (9, 15, 9), "lava")
        foot.add((-5.5, -5, -11), (11, 5, 15), "lava")

    # ------------------------------------------------------------ shell: thin, segmented plates
    def tplate(name, x0, y0, z0, x1, y1, z1, mat="rock", rot=None, tag=None):
        cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
        C.plate(m, tor, name, (tp[0] + cx, tp[1] + cy, tp[2] + cz), (x1 - x0, y1 - y0, z1 - z0), mat,
                rotation=rot, tag=tag)

    C.plate(m, pel, "shell_hips", (0, pp[1] + 0.5, pp[2] + 1), (20, 11, 15), "rock")
    # Back: three bands across the spine, with lava between them.
    for i, (y0, y1) in enumerate(((-2, 8), (9, 19), (20, 31))):
        tplate(f"shell_back_{i}", -13.5, y0, 9.5, 13.5, y1, 12.5)
    tplate("shell_upperback", -11, 13, 11.5, 11, 30, 14.5)
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * 0.5, sx * 14.5))
        tplate(f"shell_chest_{side}", lo, 15, -12.5, hi, 30, -9.5)
        flo, fhi = sorted((sx * 12.5, sx * 15.5))
        tplate(f"shell_rib_{side}a", flo, 2, -7, fhi, 12, 7)
        tplate(f"shell_rib_{side}b", flo, 14, -7, fhi, 26, 7)
    tplate("shell_belly", -9.5, 0, -14, 9.5, 13, -11, mat="obsidian")
    # The spine: obsidian spikes raking back along the ridge.
    for i, (y, h, lean) in enumerate(((4, 6, 20), (10, 10, 22), (17, 14, 24), (24, 12, 20))):
        tplate(f"shell_spike_{i}", -1.5, y, 14, 1.5, y + 3, 14 + h, mat="obsidian", rot=(-lean, 0, 0))
    # Head: a narrow stone skull and a casing on the long jaw.
    C.plate(m, head, "shell_skull", (0, hp[1] + 3.5, hp[2] - 5.5), (14, 12, 17), "mask", tag="skull")
    C.plate(m, head, "shell_brow", (0, hp[1] + 6.5, hp[2] - 13), (15, 3, 4), "rock")
    C.plate(m, head, "shell_horn_l", (5.5, hp[1] + 10, hp[2] - 2), (2, 5, 2), "obsidian", rotation=(-35, 0, -15))
    C.plate(m, head, "shell_horn_r", (-5.5, hp[1] + 10, hp[2] - 2), (2, 5, 2), "obsidian", rotation=(-35, 0, 15))
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 3, jp[2] - 9), (14, 6, 19), "rock")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        C.plate(m, arm, f"shell_pauldron_{side}", (ap[0] + sx * 1.5, ap[1] + 1.5, ap[2]), (15, 9, 15), "rock")
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 13, ap[2]), (12, 14, 12), "rock")
        for i, (y0, y1) in enumerate(((-11, -2), (-22, -12))):
            C.plate(m, fore, f"shell_bracer_{side}{i}", (fp[0], fp[1] + (y0 + y1) / 2.0, fp[2]), (14, y1 - y0, 14),
                    "obsidian" if i == 1 else "rock")
        C.plate(m, fist, f"shell_fist_{side}", (kp[0], kp[1] - 3.5, kp[2] + 1.5), (17, 9, 13), "rock")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 5, kp[2] - 8.5), (17, 9, 3), "obsidian")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_thigh_{side}", (tp2[0], tp2[1] - 6.5, tp2[2]), (12, 13, 13), "rock")
        C.plate(m, shin, f"shell_knee_{side}", (sp[0], sp[1] - 1, sp[2] - 5.5), (10, 6, 3), "obsidian")
        C.plate(m, shin, f"shell_shin_{side}", (sp[0], sp[1] - 8.5, sp[2] + 0.5), (11, 9, 11), "rock")
        C.plate(m, foot, f"shell_foot_{side}", (fp2[0], fp2[1] - 1, fp2[2] - 3.5), (13, 4, 14), "rock")

    # ------------------------------------------------------------ molten extras: a crest of flame
    for i, (y, h, lean) in enumerate(((3, 9, 45), (10, 12, 40), (17, 14, 33), (24, 12, 28), (30, 8, 22))):
        C.plate(m, tor, f"molten_flame_{i}", (0, tp[1] + y + 1.5, tp[2] + 11 + h / 2.0), (2, 3, h), "molten",
                rotation=(-lean, 0, 0), kind="molten")
    return B


def materials():
    return {
        "lava": M.lava(seed=21, crust=1.1, hot=0.75, edge_cool=1.0),
        "molten": M.lava(seed=22, crust=1.2, hot=1.0, edge_cool=0.4),
        "rock": M.rock(seed=23, chunk=7.5, obsidian=0.72, cracks=0.36, fissures=0.35, fissure_cell=22.0),
        "obsidian": M.rock(seed=24, chunk=6.0, obsidian=1.0, cracks=0.22, fissures=0.0),
        "mask": M.rock(seed=26, chunk=5.0, obsidian=1.0, cracks=0.0, fissures=0.0),
        "fang": M.fang(seed=25),
    }


def overlays():
    return [M.face_overlay(
        # Narrow slits slanting hard towards the snout, with cracks running from them like war paint.
        eye_px=[(2, 1), (3, 1), (4, 2), (5, 2), (-3, 1), (-4, 1), (-5, 2), (-6, 2)],
        sockets=[(4.0, 1.5, 6, 3), (-4.0, 1.5, 6, 3)],
        cracks=[((3, 0), (3, -3), (4, -5)), ((-4, 0), (-4, -3), (-5, -5)),
                ((5, 3), (6, 5)), ((-6, 3), (-7, 5))],
        skull_tag="skull", core_tag="cranium")]


ANIM = dict(
    weight=0.8,
    walk_cycle=1.45,
    stride=38.0,
    arm_lift=12.0,
    leg_lift=8.0,
    bob=1.8,
    sway=1.2,
    roll=4.5,
    idle_period=3.6,
    roar=1.15,
    plate_speed=1.1,
)
