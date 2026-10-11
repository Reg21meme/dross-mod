"""
No. 5 - Forgebound (ornate).

Not grown from rubble but forged: the Fire Necromancer's war-idol. Carved obsidian armour edged in gold, glowing
runes cut into every plate, cut-stone flanks, a horned helm with a T-shaped visor, layered spiked pauldrons, chains
and shackles round its forearms and a brazier burning on its back. Its lava core is white-hot with the runes
branded into it, and a crown of fire.
"""
from designs import common as C
from kit import materials as M

NUMBER = 5
ID = "forgebound"
NAME = "Forgebound"
MOOD = "ornate"
BLURB = "The Fire Necromancer's war-idol: gold-edged obsidian armour, glowing runes, chains and a brazier."


def body():
    return C.Body(
        hip_y=22, hip_z=5, pelvis_pitch=8, torso_pitch=32, spine_base=8, torso_len=30,
        shoulder_x=21, shoulder_drop=7, shoulder_dz=0,
        upper_len=18, fore_len=17, hip_x=10, thigh_len=13, shin_len=12,
        neck_drop=4, neck_dz=1, head_look_down=4,
        fist_target=(22.0, -16.0), fist_heading=(0.1, -1.0), fist_contact=(0.0, -13.0, 0.0),
        foot_target=(12.0, 9.0), foot_heading=(0.1, -1.0), foot_contact=(0.0, -6.0, -2.0),
    )


def build(m, b):
    B = C.skeleton(m, b)
    pel, tor, head = B["pelvis"], B["torso"], B["head"]
    pp, tp, hp = pel.pivot, tor.pivot, head.pivot

    # ------------------------------------------------------------ core: white-hot, runes branded in
    pel.add((-11, -6, -8), (22, 13, 16), "lava")
    tor.add((-17, -3, -11), (34, 33, 22), "lava")
    tor.add((-13, 13, 6), (26, 18, 9), "lava")
    tor.add((-12, 0, -14), (24, 14, 4), "lava")
    head.add((-7, -3, -13), (14, 13, 14), "lava", tag="cranium")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 2, hp[2] - 1), (0, hp[1] - 1, hp[2] - 2))
    jp = jaw.pivot
    jaw.add((-8, -6, -15), (16, 6, 15), "lava", tag="jaw")
    jaw_in.add((-5, -4, -11), (10, 4, 9), "lava")
    for x in (-4, -1, 2):
        jaw_in.add((x, 0, -10.5), (2, 2, 2), "fang")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        arm.add((-6, -18, -6), (12, 23, 12), "lava")
        fore.add((-7.5, -17, -7.5), (15, 18, 15), "lava")
        fist.add((-9, -13, -8.5), (18, 14, 17), "lava")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        thigh.add((-6, -13, -6.5), (12, 17, 13), "lava")
        shin.add((-5.5, -12, -5), (11, 13, 10), "lava")
        foot.add((-6.5, -6, -10), (13, 6, 15), "lava")

    # ------------------------------------------------------------ shell: forged armour
    def tplate(name, x0, y0, z0, x1, y1, z1, mat="carved", rot=None):
        cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
        C.plate(m, tor, name, (tp[0] + cx, tp[1] + cy, tp[2] + cz), (x1 - x0, y1 - y0, z1 - z0), mat, rotation=rot)

    C.plate(m, pel, "shell_hips", (0, pp[1] + 1.5, pp[2] + 0.5), (25, 13, 19), "stone")
    C.plate(m, pel, "shell_belt", (0, pp[1] + 6.5, pp[2] + 0.5), (27, 3, 21), "gold")
    tplate("shell_cuirass", -15.5, 12.5, -15.5, 15.5, 31.5, -10.5)            # the breastplate
    tplate("shell_plackart", -12.5, -1.5, -17.5, 12.5, 12.5, -13.5)
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * 0.5, sx * 18.5))
        tplate(f"shell_back_{side}", lo, -2.5, 10.5, hi, 15.5, 15.5)
        flo, fhi = sorted((sx * 16.5, sx * 20.5))
        tplate(f"shell_flank_{side}", flo, 0, -10, fhi, 27, 9, mat="stone")
    tplate("shell_backplate", -15.5, 13.5, 13.5, 15.5, 33.5, 18.5)
    tplate("shell_gorget", -13, 29.5, -6.5, 13, 32.5, 11.5, mat="stone")
    # The brazier on its back: a gold-rimmed bowl with a fire of lava in it.
    tplate("shell_brazier", -7, 18, 18.5, 7, 28, 22.5, mat="gold")
    tplate("shell_brazier_fire", -5, 19, 22, 5, 27, 25, mat="brazier_fire")
    # Helm: a horned helm with a T-shaped visor, and an armoured jaw.
    C.plate(m, head, "shell_helm", (0, hp[1] + 4, hp[2] - 5.5), (17, 13, 17), "visor", tag="skull")
    C.plate(m, head, "shell_crest", (0, hp[1] + 11.5, hp[2] - 5), (3, 3, 15), "gold")
    for side, sx in C.SIDES:
        C.plate(m, head, f"shell_horn_{side}0", (sx * 9, hp[1] + 8, hp[2] - 4), (4, 4, 4), "obsidian_plain")
        C.plate(m, head, f"shell_horn_{side}1", (sx * 11.5, hp[1] + 10.5, hp[2] - 3), (3, 4, 3), "obsidian_plain",
                rotation=(0, 0, -sx * 30))
        C.plate(m, head, f"shell_horn_{side}2", (sx * 13, hp[1] + 14, hp[2] - 2), (2, 4, 2), "gold",
                rotation=(-15, 0, -sx * 15))
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4, jp[2] - 7), (18, 7, 17), "carved")
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        # Layered pauldrons with spikes.
        C.plate(m, arm, f"shell_pauldron_{side}", (ap[0] + sx * 1.5, ap[1] + 2, ap[2]), (19, 9, 19), "carved")
        C.plate(m, arm, f"shell_pauldron2_{side}", (ap[0] + sx * 3, ap[1] - 4, ap[2]), (17, 6, 17), "carved")
        for i, dz in enumerate((-5, 0, 5)):
            C.plate(m, arm, f"shell_spike_{side}{i}", (ap[0] + sx * 4, ap[1] + 9, ap[2] + dz), (2, 6 if i == 1 else 4, 2),
                    "gold", rotation=(0, 0, -sx * 20))
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 13, ap[2]), (15, 9, 15), "stone")
        C.plate(m, fore, f"shell_vambrace_{side}", (fp[0], fp[1] - 7.5, fp[2]), (20, 15, 20), "carved")
        for i, y in enumerate((-4.5, -9.5)):
            C.plate(m, fore, f"shell_chain_{side}{i}", (fp[0], fp[1] + y, fp[2]), (22, 2, 22), "chain")
        C.plate(m, fore, f"shell_shackle_{side}", (fp[0], fp[1] - 15.5, fp[2]), (18, 3, 18), "iron")
        C.plate(m, fore, f"shell_chain_end_{side}", (fp[0] + sx * 10, fp[1] - 19, fp[2] + 3), (2, 6, 2), "chain",
                rotation=(0, 0, sx * 10))
        C.plate(m, fist, f"shell_gauntlet_{side}", (kp[0], kp[1] - 3.5, kp[2] + 1.5), (21, 11, 16), "carved")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 6.5, kp[2] - 9.5), (21, 11, 4), "obsidian_plain")
        for i, x in enumerate((-7, -2.5, 2.5, 7)):
            C.plate(m, fist, f"shell_stud_{side}{i}", (kp[0] + x, kp[1] - 6, kp[2] - 12), (3, 3, 2), "gold")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_tasset_{side}", (tp2[0], tp2[1] - 4.5, tp2[2]), (14, 16, 15), "carved")
        C.plate(m, shin, f"shell_greave_{side}", (sp[0], sp[1] - 6, sp[2]), (13, 11, 12), "carved")
        C.plate(m, foot, f"shell_sabaton_{side}", (fp2[0], fp2[1] - 1.5, fp2[2] - 2.5), (15, 5, 16), "stone")

    # ------------------------------------------------------------ molten extras: a crown of fire
    for i, (x, h) in enumerate(((-5, 5), (-2.5, 7), (0, 9), (2.5, 7), (5, 5))):
        C.plate(m, head, f"molten_crown_{i}", (x, hp[1] + 10 + h / 2.0, hp[2] - 6), (2, h, 2), "molten", kind="molten")
    for i, (x, y, h) in enumerate(((0, 23, 12), (-4, 21, 8), (4, 25, 9))):
        C.plate(m, tor, f"molten_pyre_{i}", (x, tp[1] + y, tp[2] + 15 + h / 2.0), (3, 3, h), "molten", kind="molten")
    return B


def materials():
    return {
        "lava": M.lava(seed=51, crust=1.1, hot=0.45, edge_cool=1.0, hot_spots=[(0, 46, -10, 9)]),
        "molten": M.lava(seed=52, crust=1.3, hot=1.3, edge_cool=0.2),
        "brazier_fire": M.lava(seed=53, crust=1.3, hot=1.1, edge_cool=0.4),
        "carved": M.carved(seed=54, runes=0.5, cracks=0.18),
        "visor": M.carved(seed=55, runes=0.0),
        "obsidian_plain": M.carved(seed=56, runes=0.0, border=False),
        "stone": M.rock(seed=57, chunk=8.0, obsidian=0.0, cracks=0.22, fissures=0.0, gold_border=True),
        "gold": M.gold(seed=58),
        "chain": M.chain(seed=59),
        "iron": M.solid(M.IRON, idx=2.6, seed=60),
        "fang": M.fang(seed=61, palette_=M.GOLD),
    }


def overlays():
    return [M.face_overlay(
        # A T-shaped visor slit: a burning bar for the eyes and a line down to the jaw.
        eye_px=[(x, 1) for x in range(-6, 6)] + [(0, 0), (-1, 0), (0, -1), (-1, -1), (0, -2), (-1, -2), (0, -3), (-1, -3)],
        sockets=[(-0.5, 1.0, 14, 3), (-0.5, -1.5, 4, 6)],
        cracks=[],
        skull_tag="skull", core_tag="cranium"),
        M.rune_brand("lava", seed=62, share=0.22)]


ANIM = dict(
    weight=1.0,
    walk_cycle=1.75,
    stride=30.0,
    arm_lift=9.0,
    leg_lift=6.0,
    bob=1.2,
    sway=1.2,
    roll=2.5,
    idle_period=4.4,
    roar=1.1,
    plate_speed=1.0,
)
