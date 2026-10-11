"""
No. 4 - Rubblemaw (crude).

Slapped together from rubble by a necromancer in a hurry: lopsided and lumpy, boulders stuck on at odd angles with
lava oozing out between them. Its left fist is a colossal boulder, its right a smaller lump; its jaw hangs crooked
with one big tusk, and its eyes don't match (one round and staring, one a squint). It limps. Its lava core is
bubbling slag.
"""
import random

from designs import common as C
from kit import materials as M

NUMBER = 4
ID = "rubblemaw"
NAME = "Rubblemaw"
MOOD = "crude"
BLURB = "Rubble slapped together in a hurry: lopsided, one colossal fist, a crooked jaw, oozing lava."


def body():
    return C.Body(
        hip_y=21, hip_z=5, pelvis_pitch=10, torso_pitch=36, spine_base=8, torso_len=28,
        shoulder_x=20, shoulder_drop=7, shoulder_dz=0,
        upper_len=16, fore_len=17, hip_x=10, thigh_len=12, shin_len=11,
        neck_drop=6, neck_dz=1, head_look_down=8,
        fist_target=(23.0, -15.0), fist_heading=(0.15, -1.0), fist_contact=(0.0, -15.0, 0.0),
        foot_target=(12.0, 8.0), foot_heading=(0.12, -1.0), foot_contact=(0.0, -6.0, -2.0),
        side={"r": dict(upper_len=17, fore_len=19, fist_contact=(0.0, -11.0, 0.0), fist_target=(19.0, -13.0),
                        shoulder_drop=8)},
    )


def build(m, b):
    rng = random.Random(4004)
    B = C.skeleton(m, b)
    pel, tor, head = B["pelvis"], B["torso"], B["head"]
    pp, tp, hp = pel.pivot, tor.pivot, head.pivot

    # ------------------------------------------------------------ core: bubbling slag, lopsided
    pel.add((-11, -6, -8), (22, 13, 16), "lava")
    tor.add((-17, -3, -11), (34, 32, 22), "lava")
    tor.add((-6, 12, 5), (21, 19, 10), "lava")                    # the hump sits off to its left
    tor.add((-12, 0, -14), (23, 13, 4), "lava")
    head.add((-6.5, -3, -12), (13, 11, 13), "lava", tag="cranium")
    head.add((2, -7, -11), (2, 4, 2), "fang")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 2, hp[2] - 1), (0, hp[1] - 1, hp[2] - 2))
    jp = jaw.pivot
    jaw.rotation = (4.0, 9.0, -6.0)                                 # hangs crooked
    jaw.add((-8, -6, -15), (16, 6, 15), "lava", tag="jaw")
    jaw.add((-7.5, 0, -15.5), (3, 6, 3), "fang")                    # one big tusk...
    jaw.add((5, 0, -15), (2, 2, 2), "fang")                         # ...and a stub
    jaw_in.add((-5, -4, -10), (10, 4, 8), "lava")
    for x in (-3, 1):
        jaw_in.add((x, 0, -9.5), (2, 2, 2), "fang")
    sizes = {"l": dict(arm=(14, 21, 14), fore=(19, 18, 19), fist=(26, 16, 21)),
             "r": dict(arm=(10, 22, 10), fore=(12, 20, 12), fist=(15, 12, 13))}
    for side, sx in C.SIDES:
        s = sizes[side]
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        aw, ah, ad = s["arm"]
        arm.add((-aw / 2.0, -ah + 5, -ad / 2.0), s["arm"], "lava")
        fw, fh, fd = s["fore"]
        fore.add((-fw / 2.0, -fh + 1, -fd / 2.0), s["fore"], "lava")
        kw, kh, kd = s["fist"]
        contact = -b.get("fist_contact", side)[1]
        fist.add((-kw / 2.0, -contact, -kd / 2.0), s["fist"], "lava")
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        thigh.add((-6, -12, -6.5), (12, 16, 13), "lava")
        shin.add((-5.5, -11, -5), (11, 12, 10), "lava")
        foot.add((-6.5, -6, -10), (13, 6, 15), "lava")

    # ------------------------------------------------------------ shell: boulders stuck on any old how
    n = [0]

    def boulder(parent, centre, size, mat="rock", wobble=14.0):
        """A boulder on a bone, tilted a little at random; centre relative to the bone's pivot."""
        p = parent.pivot
        rot = (rng.uniform(-wobble, wobble), rng.uniform(-wobble, wobble), rng.uniform(-wobble, wobble))
        n[0] += 1
        C.plate(m, parent, f"shell_rubble_{n[0]}", (p[0] + centre[0], p[1] + centre[1], p[2] + centre[2]), size, mat,
                rotation=rot)

    boulder(pel, (0, 2, 1), (26, 14, 19), wobble=5)
    # Back: lumps of different sizes, the biggest over its left shoulder.
    boulder(tor, (-9, 4, 12), (16, 14, 7))
    boulder(tor, (8, 3, 12), (17, 12, 7))
    boulder(tor, (-7, 17, 14), (15, 13, 7))
    boulder(tor, (6, 22, 16), (22, 17, 8), wobble=10)
    boulder(tor, (0, 30, 6), (24, 6, 16), wobble=8)
    boulder(tor, (9, 13, -13), (16, 18, 5), mat="obsidian")
    boulder(tor, (-9, 17, -13), (14, 15, 5))
    boulder(tor, (0, 5, -15.5), (20, 11, 5), mat="obsidian", wobble=6)
    for side, sx in C.SIDES:
        boulder(tor, (sx * 18, 12, 0), (5, 22, 18), wobble=8)
    # Head: a lumpy skull with a rock jammed on top, and a crude casing on the crooked jaw.
    C.plate(m, head, "shell_skull", (0, hp[1] + 3, hp[2] - 5), (16, 12, 16), "mask", tag="skull")
    C.plate(m, head, "shell_lump", (-3, hp[1] + 10, hp[2] - 3), (9, 5, 9), "rock", rotation=(10, 20, -15))
    C.plate(m, head, "shell_brow", (2, hp[1] + 6.5, hp[2] - 12.5), (10, 4, 5), "rock", rotation=(0, 0, -12))
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4, jp[2] - 7), (18, 7, 17), "rock")
    for side, sx in C.SIDES:
        s = sizes[side]
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        aw, ah, ad = s["arm"]
        fw, fh, fd = s["fore"]
        kw, kh, kd = s["fist"]
        boulder(arm, (sx * 2, 1, 0), (aw + 6, 12, ad + 6))
        boulder(arm, (0, -ah / 2.0 - 2, 0), (aw + 3, ah // 2, ad + 3), wobble=6)
        boulder(fore, (0, -fh / 2.0 + 2, 0), (fw + 4, fh - 6, fd + 4), wobble=7)
        if side == "l":
            boulder(fore, (sx * 6, -6, 4), (6, 8, 8), mat="obsidian", wobble=20)   # a chunk bolted on
        contact = -b.get("fist_contact", side)[1]
        boulder(fist, (0, -contact / 2.0 + 2, 1.5), (kw + 3, kh - 4, kd - 2), wobble=6)
        boulder(fist, (0, -contact / 2.0 - 0.5, -kd / 2.0 - 1.5), (kw + 2, kh - 3, 4), mat="obsidian", wobble=4)
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        boulder(thigh, (0, -4, 0), (14, 14, 15), wobble=8)
        boulder(shin, (0, -5, 0.5), (13, 10, 12), wobble=8)
        boulder(foot, (0, -1.5, -2.5), (15, 5, 16), wobble=4)

    # ------------------------------------------------------------ molten extras: slag oozing and dripping
    for i, (x, y, z, h) in enumerate(((10, 6, 12, 5), (-12, 14, 11, 7), (14, 18, -12, 4))):
        C.plate(m, tor, f"molten_ooze_{i}", (x, tp[1] + y - h / 2.0, tp[2] + z), (3, h, 3), "molten", kind="molten")
    C.plate(m, jaw, "molten_drool", (-3, jp[1] - 9, jp[2] - 12), (2, 7, 2), "molten", kind="molten")
    return B


def materials():
    return {
        "lava": M.lava(seed=41, crust=1.1, hot=0.1, flow_cell=6.0, edge_cool=0.8, plates=0.4, plate_cell=4.5,
                       bubbles=0.22),
        "molten": M.lava(seed=42, crust=1.2, hot=0.9, edge_cool=0.3),
        "rock": M.rock(seed=43, chunk=11.0, obsidian=0.12, cracks=0.3, fissures=0.25, fissure_cell=30.0,
                       crack_width=0.6, stone_cell=5.5),
        "obsidian": M.rock(seed=44, chunk=8.0, obsidian=1.0, cracks=0.25, fissures=0.0),
        "mask": M.rock(seed=46, chunk=7.0, obsidian=0.0, cracks=0.0, fissures=0.0),
        "fang": M.fang(seed=45),
    }


def overlays():
    return [M.face_overlay(
        # Its right eye (the viewer's left) stares, round and wide; its left eye is a squint. Crude cracks.
        eye_px=[(-5, 1), (-4, 1), (-6, 0), (-3, 0), (-5, -1), (-4, -1), (3, 0), (4, 0), (5, 0)],
        socket_px=[(-5, 0), (-4, 0)],
        sockets=[(-4.5, 0.0, 6, 5), (4.0, 0.5, 5, 2)],
        cracks=[((-1, 4), (1, 1), (0, -2), (2, -5)), ((6, 2), (7, 5)), ((-7, -2), (-7, -5))],
        skull_tag="skull", core_tag="cranium")]


ANIM = dict(
    weight=1.1,
    walk_cycle=1.9,
    stride=28.0,
    arm_lift=8.0,
    leg_lift=6.0,
    bob=1.6,
    sway=2.4,
    roll=5.0,
    lopsided=1.0,
    idle_period=3.8,
    roar=1.0,
    plate_speed=1.2,
)
