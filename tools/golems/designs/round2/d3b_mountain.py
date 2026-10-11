"""
No. 3B - Mount Calderon (a living mountain).

Old Calderon as a mountain that stood up: its whole back is the volcano, a broad stepped mountain whose summit
crater rises past its head, with obsidian shards jutting from the slopes like outcrops and boulder foothills on its
shoulders. Its head peers out from under the mountain's front slope. When it erupts, lava runs down every side of
the mountain; in the core form the mountain is molten and erupts from the summit.
"""
from designs import common as C
from kit import materials as M

NUMBER = 2
LABEL = "3B"
ID = "mountain"
NAME = "Mount Calderon"
MOOD = "living mountain"
BLURB = "Its whole back is the volcano: a mountain with obsidian outcrops and a summit crater."


def body():
    return C.Body(
        hip_y=24, hip_z=6, pelvis_pitch=12, torso_pitch=40, spine_base=9, torso_len=34,
        shoulder_x=24, shoulder_drop=9, shoulder_dz=0,
        upper_len=18, fore_len=18, hip_x=13, thigh_len=13, shin_len=12,
        neck_drop=5, neck_dz=-11, head_look_down=4,
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
    tor.add((-22, -3, -14), (44, 38, 28), "lava")
    tor.add((-18, 14, 7), (36, 22, 11), "lava")
    tor.add((-15, 0, -17), (30, 16, 4), "lava")
    head.add((-9, -3, -16), (18, 14, 17), "lava", tag="cranium")
    head.add((-7, -8, -15), (2, 5, 2), "fang")
    head.add((5, -8, -15), (2, 5, 2), "fang")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 2, hp[2] - 1), (0, hp[1] - 1, hp[2] - 2))
    jp = jaw.pivot
    jaw.add((-9.5, -7, -18), (19, 7, 18), "lava", tag="jaw")
    jaw.add((-8, 0, -18.5), (3, 5, 3), "fang")                        # two craggy tusks
    jaw.add((5, 0, -18.5), (3, 5, 3), "fang")
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

    # ------------------------------------------------------------ shell: a mountain on its back
    def tplate(name, x0, y0, z0, x1, y1, z1, mat="rock", rot=None):
        cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
        C.plate(m, tor, name, (tp[0] + cx, tp[1] + cy, tp[2] + cz), (x1 - x0, y1 - y0, z1 - z0), mat, rotation=rot)

    C.plate(m, pel, "shell_hips", (0, pp[1] + 0.5, pp[2] + 0.5), (31, 17, 21), "rock")
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * 0.5, sx * 24.5))
        tplate(f"shell_chest_{side}", lo, 10.5, -18.5, hi, 26.5, -13.5)
        flo, fhi = sorted((sx * 21.5, sx * 25.5))
        tplate(f"shell_flank_{side}", flo, 0, -13, fhi, 32, 12)
    tplate("shell_belly", -16, -1.5, -21.5, 16, 15.5, -16.5, mat="obsidian")
    tplate("shell_back", -24, -2.5, 13.5, 24, 14.5, 18.5)
    tplate("shell_traps", -17, 34.5, -4.5, 17, 37.5, 15.5)
    # The mountain: wide at the base across the whole back, its summit crater above the head.
    vol = C.volcano(m, tor, (0, tp[1] + 14, tp[2] + 17), tilt,
                    levels=[(46, 30, 6), (40, 26, 6), (33, 22, 6), (26, 18, 5), (20, 15, 5), (16, 12, 4)],
                    crater=(8, 6), rim_spikes=4, rock="rock", rim="basalt", grow=1.15, fountain=14, bombs=5)
    # Obsidian outcrops on the slopes (on the volcano holder, which stands upright).
    hb = vol.holder.pivot
    for i, (x, y, z, h, lean) in enumerate(((-18, 4, -6, 14, (-20, 0, 38)), (19, 6, -4, 12, (-15, 0, -42)),
                                             (-13, 12, 6, 11, (25, 0, 32)), (15, 10, 7, 15, (30, 0, -28)),
                                             (0, 17, 9, 10, (42, 0, 0)), (-6, 20, -6, 9, (-35, 0, 18)))):
        C.spike(m, vol.holder, f"shell_outcrop_{i}", (hb[0] + x, hb[1] + y, hb[2] + z), h, 5, lean=lean, mat="obsidian")
    # Head: a craggy skull under the mountain's front slope.
    C.plate(m, head, "shell_skull", (0, hp[1] + 4, hp[2] - 7), (21, 15, 19), "mask", tag="skull")
    C.plate(m, head, "shell_brow", (0, hp[1] + 8.5, hp[2] - 16), (23, 5, 5), "rock")
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4.5, jp[2] - 10), (21, 8, 17), "rock")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        # Boulder foothills on the shoulders.
        C.plate(m, arm, f"shell_pauldron_{side}", (ap[0] + sx * 1, ap[1] - 1, ap[2]), (21, 13, 21), "rock")
        C.plate(m, arm, f"shell_boulder_{side}", (ap[0] + sx * 3, ap[1] + 7, ap[2] - 2), (12, 7, 12), "rock",
                rotation=(8, 15, -sx * 10))
        C.spike(m, arm, f"shell_shoulderspike_{side}", (ap[0] + sx * 6, ap[1] + 6, ap[2] + 4), 9, 4,
                lean=(15, 0, -sx * 35), mat="obsidian")
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 12, ap[2]), (19, 12, 19), "rock")
        C.plate(m, fore, f"shell_forearm_{side}", (fp[0], fp[1] - 8.5, fp[2]), (24, 15, 24), "rock")
        C.plate(m, fore, f"shell_cuff_{side}", (fp[0], fp[1] - 16.5, fp[2]), (22, 3, 22), "basalt")
        C.plate(m, fist, f"shell_fist_{side}", (kp[0], kp[1] - 4, kp[2] + 1.5), (25, 12, 19), "rock")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 6.5, kp[2] - 12), (25, 12, 4), "obsidian")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_thigh_{side}", (tp2[0], tp2[1] - 4, tp2[2]), (19, 16, 19), "rock")
        C.plate(m, shin, f"shell_shin_{side}", (sp[0], sp[1] - 5, sp[2]), (17, 11, 16), "rock")
        C.plate(m, foot, f"shell_foot_{side}", (fp2[0], fp2[1] - 2, fp2[2] - 3), (20, 6, 20), "rock")
    return dict(volcanoes=[vol])


def materials():
    return {
        "lava": M.lava(seed=41, crust=1.1, hot=-0.6, edge_cool=1.0, plates=0.55, plate_cell=10.0),
        "molten": M.lava(seed=42, crust=1.2, hot=0.4, edge_cool=0.6, plates=0.35, plate_cell=8.0),
        "fountain": M.lava(seed=43, crust=1.3, hot=1.4, edge_cool=0.2),
        "crater_lava": M.crater_pool(seed=44),
        "rock": M.rock(seed=45, chunk=11.0, obsidian=0.08, cracks=0.16, fissures=0.15, fissure_cell=32.0, heat=-0.6,
                       cobble=M.COBBLE_OLD),
        "obsidian": M.rock(seed=46, chunk=7.0, obsidian=1.0, cracks=0.1, fissures=0.0, heat=-0.6),
        "mask": M.rock(seed=47, chunk=6.0, obsidian=0.0, cracks=0.0, fissures=0.0, cobble=M.COBBLE_OLD),
        "basalt": M.basalt(seed=48, cracks=0.1),
        "fang": M.fang(seed=49),
    }


def overlays():
    return [M.face_overlay(
        # Two deep embers under a crag of a brow, and the third eye: a crack of fire up the forehead.
        eye_px=[(-5, 0), (-4, 0), (3, 0), (4, 0), (-1, 4), (-1, 5), (-1, 6)],
        sockets=[(4.0, 0.5, 6, 3), (-4.5, 0.5, 6, 3)],
        cracks=[((-1, 7), (-2, 10)), ((-1, 3), (0, 0), (-1, -4)), ((7, 1), (9, -3)), ((-8, 1), (-9, 4))],
        skull_tag="skull", core_tag="cranium")]


FLOWS = dict(reach=86.0, streams=0.6, seed=401)

ANIM = dict(
    weight=1.45,
    walk_cycle=2.4,
    stride=28.0,
    arm_lift=7.0,
    leg_lift=5.5,
    bob=2.1,
    sway=2.3,
    roll=2.6,
    idle_period=5.2,
    roar=0.8,
    plate_speed=0.9,
)
