"""
No. 1 - Slagback (hulking).

The archetype: a walking landslide. A mountain of a back, a small head sunk low in front of it, and fists
like boulders bound in obsidian knuckle-plates. Cobblestone chunks with obsidian seams, wide lava cracks.
The lava core underneath is dense and heavy; in the core form a crest of molten slag rises along its spine.
"""
from designs import common as C
from kit import materials as M

NUMBER = 1
ID = "slagback"
NAME = "Slagback"
MOOD = "hulking"
BLURB = "A walking landslide: a mountain of a back, boulder fists in obsidian knuckle-plates."


def body():
    return C.Body(
        hip_y=21, hip_z=5, pelvis_pitch=10, torso_pitch=35, spine_base=8, torso_len=30,
        shoulder_x=21, shoulder_drop=7, shoulder_dz=0,
        upper_len=17, fore_len=16, hip_x=10, thigh_len=12, shin_len=11,
        neck_drop=5, neck_dz=1, head_look_down=8,
        fist_target=(22.0, -15.0), fist_heading=(0.1, -1.0), fist_contact=(0.0, -13.0, 0.0),
        foot_target=(12.0, 9.0), foot_heading=(0.12, -1.0), foot_contact=(0.0, -6.0, -2.0),
    )


def build(m, b):
    B = C.skeleton(m, b)
    pel, tor, head = B["pelvis"], B["torso"], B["head"]
    pp, tp, hp = pel.pivot, tor.pivot, head.pivot

    # ------------------------------------------------------------ core (lava body)
    pel.add((-11, -6, -8), (22, 14, 16), "lava")
    tor.add((-17, -3, -11), (34, 33, 22), "lava")                 # chest
    tor.add((-13, 13, 6), (26, 18, 9), "lava")                    # upper back mass (the hump)
    tor.add((-12, 0, -14), (24, 15, 4), "lava")                   # belly
    # Head: cranium, two upper fangs (hidden behind the jaw until it opens), and the two jaws.
    head.add((-7, -3, -13), (14, 13, 14), "lava", tag="cranium")
    head.add((-6, -7, -12), (2, 4, 2), "fang")
    head.add((4, -7, -12), (2, 4, 2), "fang")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 2, hp[2] - 1), (0, hp[1] - 1, hp[2] - 2))
    jp, jip = jaw.pivot, jaw_in.pivot
    jaw.add((-8, -6, -15), (16, 6, 16), "lava", tag="jaw")
    jaw.add((-7, 0, -15.5), (2, 3, 2), "fang")                    # lower tusks, in front of the face
    jaw.add((5, 0, -15.5), (2, 3, 2), "fang")
    jaw_in.add((-5, -4, -10), (10, 4, 9), "lava")
    for x in (-4, -1, 2):
        jaw_in.add((x, 0, -9.5), (2, 2, 2), "fang")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        arm.add((-6, -17, -6), (12, 22, 12), "lava")
        fore.add((-7.5, -16, -7.5), (15, 17, 15), "lava")
        fist.add((-9, -13, -9), (18, 14, 17), "lava")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        thigh.add((-6, -13, -6.5), (12, 17, 13), "lava")
        shin.add((-5.5, -11, -5), (11, 12, 10), "lava")
        foot.add((-6.5, -6, -10), (13, 6, 15), "lava")

    # ------------------------------------------------------------ shell (rock armour)
    def tplate(name, x0, y0, z0, x1, y1, z1, mat="rock", tag=None):
        """A torso plate from corners relative to the torso pivot."""
        cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
        C.plate(m, tor, name, (tp[0] + cx, tp[1] + cy, tp[2] + cz), (x1 - x0, y1 - y0, z1 - z0), mat, tag=tag)

    C.plate(m, pel, "shell_hips", (0, pp[1] + 1.5, pp[2] + 0.5), (25, 15, 19), "rock")
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * 0.5, sx * 18.5))
        tplate(f"shell_back_{side}", lo, -2, 10, hi, 15, 15)          # lower back
        tplate(f"shell_chest_{side}", lo, 14, -14.5, hi, 31, -10.5)    # pecs (a lava seam down the middle)
        flo, fhi = sorted((sx * 16.5, sx * 20.5))
        tplate(f"shell_flank_{side}", flo, 0, -10, fhi, 27, 9)
    tplate("shell_hump", -15.5, 12, 13.5, 15.5, 33, 21.5)            # the mountain
    tplate("shell_traps", -12, 29.5, -1.5, 12, 32.5, 13.5)          # behind the head: lava glows round the neck
    tplate("shell_belly", -13, -1.5, -17.5, 13, 14.5, -13.5, mat="obsidian")
    # Head: a stone skull with a heavy brow, and a casing over the outer jaw (a lava seam for a mouth).
    C.plate(m, head, "shell_skull", (0, hp[1] + 4, hp[2] - 5.5), (17, 13, 17), "mask", tag="skull")
    C.plate(m, head, "shell_brow", (0, hp[1] + 7.5, hp[2] - 13.5), (19, 4, 5), "rock")
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4, jp[2] - 7.5), (18, 7, 18), "rock")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        C.plate(m, arm, f"shell_pauldron_{side}", (ap[0] + sx * 1.5, ap[1] + 1, ap[2]), (19, 15, 19), "rock")
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 11.5, ap[2]), (15, 11, 15), "rock")
        C.plate(m, fore, f"shell_forearm_{side}", (fp[0], fp[1] - 7, fp[2]), (20, 14, 20), "rock")
        C.plate(m, fore, f"shell_cuff_{side}", (fp[0], fp[1] - 14, fp[2]), (18, 3, 18), "obsidian")
        C.plate(m, fist, f"shell_fist_{side}", (kp[0], kp[1] - 3.5, kp[2] + 1.5), (21, 11, 16), "rock")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 6.5, kp[2] - 9.5), (21, 11, 4), "obsidian")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_thigh_{side}", (tp2[0], tp2[1] - 4.5, tp2[2]), (14, 16, 15), "rock")
        C.plate(m, shin, f"shell_knee_{side}", (sp[0], sp[1] - 1.5, sp[2] - 5.5), (12, 7, 3), "rock")
        C.plate(m, shin, f"shell_shin_{side}", (sp[0], sp[1] - 6.5, sp[2] + 0.5), (13, 9, 13), "rock")
        C.plate(m, foot, f"shell_foot_{side}", (fp2[0], fp2[1] - 1.5, fp2[2] - 2.5), (15, 5, 16), "rock")

    # ------------------------------------------------------------ molten extras (core form only)
    for i, (y, h) in enumerate(((14, 7), (20, 10), (26, 7))):
        C.plate(m, tor, f"molten_crest_{i}", (0, tp[1] + y, tp[2] + 15 + h / 2.0 - 1), (3, 5, h), "molten",
                kind="molten")
    C.plate(m, jaw, "molten_drip_l", (5, jp[1] - 8, jp[2] - 12), (2, 4, 2), "molten", kind="molten")
    C.plate(m, jaw, "molten_drip_r", (-4, jp[1] - 9, jp[2] - 9), (2, 6, 2), "molten", kind="molten")
    return B


def materials():
    return {
        "lava": M.lava(seed=11, crust=0.86, hot_spots=[(0, 46, -12, 8)]),
        "molten": M.lava(seed=12, crust=1.1, hot=0.6, edge_cool=0.5),
        "rock": M.rock(seed=13, chunk=9.0, obsidian=0.14, cracks=0.28, fissures=0.4, fissure_cell=26.0),
        "obsidian": M.rock(seed=14, chunk=7.0, obsidian=1.0, cracks=0.18, fissures=0.0),
        "mask": M.rock(seed=16, chunk=6.0, obsidian=1.0, cracks=0.0, fissures=0.0),
        "fang": M.fang(seed=15),
        "eye": M.eye(),
    }


def overlays():
    return [M.face_overlay(
        # Two burning slits, slanted down towards the nose, deep in the shadow under the brow.
        eye_px=[(2, 0), (3, 0), (4, 0), (5, 1), (-3, 0), (-4, 0), (-5, 0), (-6, 1)],
        sockets=[(4.0, 0.5, 7, 3), (-4.0, 0.5, 7, 3)],
        cracks=[((0, 2), (0, -1), (-1, -3), (0, -6)),
                ((5, 2), (7, 4), (8, 6)),
                ((-6, 2), (-7, -1), (-8, -4))],
        skull_tag="skull", core_tag="cranium")]


ANIM = dict(
    weight=1.0,          # heavier = slower, deeper bob
    walk_cycle=1.8,      # seconds per full cycle (two steps per side)
    stride=30.0,         # units the body travels per cycle
)
