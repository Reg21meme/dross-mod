"""
The parts every round-3 Calderon shares (the variations of No. 3 the user picked from round 2: 3C's dark stone and
upward shoulder spikes, 3D's beard, the round-2 head that no longer clips into the chest).

  body()                    the proportions: hunched 50 degrees, about five blocks to the top of its volcano
  core(m, B)                the lava body (always drawn; the whole golem in its core form)
  torso_shell(m, B, ...)    the stone over its hips, chest, flanks and back
  head_shell(m, B, ...)     the skull, brow, outer jaw and the beard of obsidian shards (horns if asked)
  limb_shell(m, B, ...)     pauldrons with spikes pointing up, armoured arms with spikes along them, fists with
                            obsidian knuckles and studs, legs
  ridge(m, B, ...)          spikes along the spine, their feet sunk into the back and pointing out of it
  molten_spikes(m, B, ...)  the core form's lava spikes (they grow out when the shell bursts)

Coordinates: `T(B, x, y, z)` turns a point given relative to the torso pivot (x across, y up the spine, z out of the
back; the bind pose is upright) into the absolute bind-pose point the helpers want. The back's surfaces, relative
to the torso pivot: the lower back plates at z = 18.5 (y -2.5 to 15.5), the upper back plates at z = 22.5
(y 15.5 to 36.5); the lava body's back at z = 14 (chest) and 18 (the hump, y 15 to 37).
"""
from designs import common as C

PAD_FOLLOW = 0.35        # how much of the arm's swing the shoulder pads (and the lava shoulder caps) follow
ARM_TOP = 2              # how far (units) the lava upper arm reaches above the shoulder joint
LOWER_BACK_Z = 18.5
UPPER_BACK_Z = 22.5
HUMP_Z = 18.0


def body(**kw):
    # The forearms are 2 units longer than round 2's, so the arms have a little slack standing (the elbows bent a
    # bit) and can reach forward, back and out in the walk and the other animations without locking straight.
    values = dict(
        hip_y=24, hip_z=6, pelvis_pitch=12, torso_pitch=38, spine_base=9, torso_len=35,
        shoulder_x=24, shoulder_drop=8, shoulder_dz=0,
        upper_len=18, fore_len=20, hip_x=13, thigh_len=13, shin_len=12,
        neck_drop=4, neck_dz=-11, head_look_down=4,
        fist_target=(25.0, -18.0), fist_heading=(0.12, -1.0), fist_contact=(0.0, -14.0, 0.0),
        foot_target=(14.0, 7.0), foot_heading=(0.15, -1.0), foot_contact=(0.0, -7.0, -2.0),
    )
    values.update(kw)
    return C.Body(**values)


def T(B, x, y, z):
    """A point relative to the torso pivot (x across, y up the spine, z out of the back), as absolute bind pose."""
    tp = B["torso"].pivot
    return (tp[0] + x, tp[1] + y, tp[2] + z)


def core(m, B):
    """The lava body. Returns the two jaw bones."""
    pel, tor, head = B["pelvis"], B["torso"], B["head"]
    hp = head.pivot
    pel.add((-14, -7, -9), (28, 15, 18), "lava")
    tor.add((-21, -3, -14), (42, 39, 28), "lava")                 # chest
    tor.add((-17, 15, 7), (34, 22, 11), "lava")                   # the hump of the upper back
    tor.add((-15, 0, -17), (30, 16, 4), "lava")                   # belly
    head.add((-9, -3, -16), (18, 14, 17), "lava", tag="cranium")
    head.add((-7, -8, -15), (2, 5, 2), "fang")
    head.add((5, -8, -15), (2, 5, 2), "fang")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 2, hp[2] - 1), (0, hp[1] - 1, hp[2] - 2))
    jaw.add((-9.5, -7, -18), (19, 7, 18), "lava", tag="jaw")
    jaw_in.add((-6, -5, -14), (12, 5, 12), "lava")
    for x in (-5, -1, 3):
        jaw_in.add((x, 0, -13.5), (2, 2, 2), "fang")
    for side, sx in C.SIDES:
        # The upper arm reaches only ARM_TOP above the shoulder joint: above that, the shoulder is a cap of lava on a
        # mount at the joint that the animations turn back against most of the arm's swing (anims.Rig.follow), so it
        # moves only PAD_FOLLOW of what the arm does and doesn't slide in and out of the body. The stone shoulder pad
        # (limb_shell) hangs on the same mount, round the cap.
        arm = B[f"arm_{side}"]
        ap = arm.pivot
        arm.add((-8, -18, -6), (16, 18 + ARM_TOP, 14), "lava")      # its front stays behind the shoulder pad's
        mount = m.bone(f"pauldron_mount_{side}", ap, parent=arm, kind="core")
        mount.follow = (arm.name, PAD_FOLLOW)
        mount.add_c((sx * 2, 1.75, 1), (18, 7, 16), "lava")
        B[f"pauldron_mount_{side}"] = mount
        B[f"forearm_{side}"].add((-9.5, -20, -9.5), (19, 21, 19), "lava")
        B[f"fist_{side}"].add((-11, -14, -10.5), (22, 15, 20), "lava")
        B[f"thigh_{side}"].add((-8, -13, -8), (16, 17, 16), "lava")
        B[f"shin_{side}"].add((-7, -12, -6.5), (14, 13, 13), "lava")
        B[f"foot_{side}"].add((-8.5, -7, -12), (17, 7, 18), "lava")
    return jaw, jaw_in


def tplate(m, B, name, x0, y0, z0, x1, y1, z1, mat="rock", rot=None, kind="shell"):
    """A plate on the torso from two corners relative to the torso pivot."""
    tp = B["torso"].pivot
    cx, cy, cz = (x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0
    return C.plate(m, B["torso"], name, (tp[0] + cx, tp[1] + cy, tp[2] + cz),
                   (abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)), mat, rotation=rot, kind=kind)


def torso_shell(m, B, upper_back=True, lower_back_top=15.5, back_mat="rock", spine_gap=0.5):
    """
    The stone over the hips and torso. upper_back=False leaves the upper back open for a volcano that grows out of
    the lava hump itself (its foot then covers it); lower_back_top is where the lower back plates stop.
    """
    pp = B["pelvis"].pivot
    C.plate(m, B["pelvis"], "shell_hips", (0, pp[1] + 0.5, pp[2] + 0.5), (31, 17, 21), "rock")
    for side, sx in C.SIDES:
        lo, hi = sorted((sx * spine_gap, sx * 23.5))
        tplate(m, B, f"shell_back_{side}", lo, -2.5, 13.5, hi, lower_back_top, LOWER_BACK_Z, back_mat)
        if upper_back:
            ulo, uhi = sorted((sx * spine_gap, sx * 19.5))
            tplate(m, B, f"shell_upper_{side}", ulo, 15.5, 17.5, uhi, 36.5, UPPER_BACK_Z, back_mat)
        tplate(m, B, f"shell_chest_{side}", lo, 10.5, -18.5, hi, 27.5, -13.5)      # stops short of the chin
        # Collar plates either side of the neck, above the chest plates, and plates on the top of the chest beside the
        # neck, so no lava shows at the front of the shoulders (they stay clear of the head and jaw).
        clo, chi = sorted((sx * 11.5, sx * 23.5))
        tplate(m, B, f"shell_collar_{side}", clo, 27.5, -18.5, chi, 35.5, -13.5)
        nlo, nhi = sorted((sx * 11.5, sx * 16.5))
        tplate(m, B, f"shell_nape_{side}", nlo, 35.5, -14.5, nhi, 38.5, -4.5)
        flo, fhi = sorted((sx * 20.5, sx * 24.5))
        tplate(m, B, f"shell_flank_{side}", flo, 0, -13, fhi, 33, 12)
    tplate(m, B, "shell_belly", -16, -1.5, -21.5, 16, 15.5, -16.5, mat="obsidian")
    tplate(m, B, "shell_traps", -16, 35.5, -4.5, 16, 38.5, 16.5)


def head_shell(m, B, jaw, beard=((-6, 5), (-3, 8), (0, 10), (3, 8), (6, 5)), horns=None, crown=None, horn_root=None):
    """
    The stone skull and brow, the outer jaw and a beard of obsidian shards hanging from it.
      horns: (length, width, lean_x, lean_z) of two horns on the brow, or None (horn_root paints their feet)
      crown: [(x, height)] basalt stubs on top of the skull, or None
    """
    head = B["head"]
    hp, jp = head.pivot, jaw.pivot
    C.plate(m, head, "shell_skull", (0, hp[1] + 4, hp[2] - 7), (21, 15, 19), "mask", tag="skull")
    C.plate(m, head, "shell_brow", (0, hp[1] + 8.5, hp[2] - 16), (23, 5, 5), "rock")
    if horns:
        length, width, lx, lz = horns
        for side, sx in C.SIDES:
            C.spike(m, head, f"shell_horn_{side}", (sx * 8, hp[1] + 10, hp[2] - 8), length, width,
                    lean=(lx, 0, -sx * lz), mat="obsidian", embed=2, root_mat=horn_root)
    for i, (x, h) in enumerate(crown or ()):
        C.plate(m, head, f"shell_crown_{i}", (x, hp[1] + 11 + h / 2.0, hp[2] - 8), (4, h, 4), "basalt").attach = True
    C.plate(m, jaw, "shell_jaw", (0, jp[1] - 4.5, jp[2] - 10), (21, 8, 17), "rock")
    for i, (x, length) in enumerate(beard):
        C.plate(m, jaw, f"shell_beard_{i}", (x, jp[1] - 8 - length / 2.0, jp[2] - 16), (2, length + 1, 2), "obsidian")


def limb_shell(m, B, shoulder_spikes=((-6, 12, 20), (0, 17, 0), (6, 12, -20)), shoulder_splay=25.0,
               arm_spikes=(-2, -8, -14), arm_spike_size=(6, 4), studs=True, spike_mat="obsidian"):
    """
    Pauldrons with spikes pointing up (3C's: [(z offset, length, fore/aft lean)], splayed out `shoulder_splay`
    degrees), armoured arms with spikes along the forearms' outer sides (pointing out, a little back up the arm),
    obsidian cuffs, fists with obsidian knuckles and studs, and stone legs.
    """
    for side, sx in C.SIDES:
        arm, fore, fist = B[f"arm_{side}"], B[f"forearm_{side}"], B[f"fist_{side}"]
        ap, fp, kp = arm.pivot, fore.pivot, fist.pivot
        # The shoulder pad and its spikes hang on the shoulder's mount (core()), so they move only PAD_FOLLOW of the
        # arm's swing, like armour strapped to the body. The pad sits a little back and out from the joint, so less of
        # it is buried in the body.
        mount = B[f"pauldron_mount_{side}"]
        C.plate(m, mount, f"shell_pauldron_{side}", (ap[0] + sx * 2, ap[1] - 1, ap[2] + 1.5), (20, 13, 18), "rock")
        for i, (dz, h, lean) in enumerate(shoulder_spikes):
            C.spike(m, mount, f"shell_shoulderspike_{side}{i}", (ap[0] + sx * 5, ap[1] + 5.5, ap[2] + dz), h, 4,
                    lean=(lean, 0, -sx * shoulder_splay), mat=spike_mat, embed=2)
        C.plate(m, arm, f"shell_upperarm_{side}", (ap[0], ap[1] - 12, ap[2]), (19, 12, 19), "rock")
        C.plate(m, fore, f"shell_forearm_{side}", (fp[0], fp[1] - 9.5, fp[2]), (24, 17, 24), "rock")
        length, width = arm_spike_size
        for i, dy in enumerate(arm_spikes):
            C.spike(m, fore, f"shell_armspike_{side}{i}", (fp[0] + sx * 12, fp[1] + dy, fp[2] + 3), length, width,
                    direction=(sx, 0.9, 0.4), mat=spike_mat, steps=2, embed=2)
        C.plate(m, fore, f"shell_cuff_{side}", (fp[0], fp[1] - 18.5, fp[2]), (22, 3, 22), "obsidian")
        C.plate(m, fist, f"shell_fist_{side}", (kp[0], kp[1] - 4, kp[2] + 1.5), (25, 12, 19), "rock")
        C.plate(m, fist, f"shell_knuckles_{side}", (kp[0], kp[1] - 6.5, kp[2] - 12), (25, 12, 4), "obsidian")
        if studs:
            for i, x in enumerate((-8, -2.5, 3, 8.5)):
                C.spike(m, fist, f"shell_stud_{side}{i}", (kp[0] + x, kp[1] - 4, kp[2] - 14), 3, 3,
                        direction=(0, 0, -1), mat="obsidian", steps=2, embed=1)
        thigh, shin, foot = B[f"thigh_{side}"], B[f"shin_{side}"], B[f"foot_{side}"]
        tp2, sp, fp2 = thigh.pivot, shin.pivot, foot.pivot
        C.plate(m, thigh, f"shell_thigh_{side}", (tp2[0], tp2[1] - 4, tp2[2]), (19, 16, 19), "rock")
        C.plate(m, shin, f"shell_shin_{side}", (sp[0], sp[1] - 5, sp[2]), (17, 11, 16), "rock")
        C.plate(m, foot, f"shell_foot_{side}", (fp2[0], fp2[1] - 2, fp2[2] - 3), (20, 6, 20), "rock")


def ridge(m, B, spikes, prefix="shell_spine", mat="obsidian", kind="shell", embed=2, root_mat=None, steps=None):
    """
    Spikes along the spine: [(x, y, z, length, width, rake, splay)] relative to the torso pivot (z = the surface they
    grow out of). Each points straight out of the back, raked `rake` degrees towards the tail and splayed `splay`
    degrees out to the side its x is on, its foot sunk `embed` units into the back. Spikes 12 or more long taper in
    four steps (unless `steps` says otherwise); `root_mat` paints their feet.
    """
    out = []
    for i, (x, y, z, length, width, rake, splay) in enumerate(spikes):
        side = 1 if x >= 0 else -1
        n = steps or (4 if length >= 12 else 3)
        out.append(C.spike(m, B["torso"], f"{prefix}_{i}", T(B, x, y, z), length, width,
                           direction=C.back_dir(rake, splay, side), mat=mat, kind=kind, embed=embed, steps=n,
                           root_mat=root_mat))
    return out


def molten_spikes(m, B, back=(), shoulder=((0, 11, 0),), shoulder_splay=25.0, shoulder_out=3.0, horns=None):
    """
    The core form's lava spikes: along the back (as ridge()), on the shoulders, pointing up ([(z offset, length,
    fore/aft lean)], `shoulder_out` units out from the joint, splayed `shoulder_splay` degrees), and horns on the head
    (like head_shell's: (length, width, lean_x, lean_z), or None).
    """
    ridge(m, B, back, prefix="molten_spine", mat="molten", kind="molten")
    if horns:
        head = B["head"]
        hp = head.pivot
        length, width, lx, lz = horns
        for side, sx in C.SIDES:
            # The lava head tops out at the neck pivot + 11.
            C.spike(m, head, f"molten_horn_{side}", (sx * 7, hp[1] + 10, hp[2] - 8), length, width,
                    lean=(lx, 0, -sx * lz), mat="molten", kind="molten", embed=2)
    for side, sx in C.SIDES:
        mount = B[f"pauldron_mount_{side}"]
        ap = mount.pivot
        for i, (dz, h, lean) in enumerate(shoulder):
            # On the lava shoulder cap (core()), which tops out at the joint + 5.25.
            C.spike(m, mount, f"molten_shoulder_{side}{i}", (ap[0] + sx * shoulder_out, ap[1] + 5.0, ap[2] + dz), h, 4,
                    lean=(lean, 0, -sx * shoulder_splay), mat="molten", kind="molten", embed=2)
