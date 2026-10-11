"""
The golem skeleton every design shares, and helpers for the parts every design builds.

Bones (core = the lava body; every design adds its own boxes to them):

    root
     └ pelvis                 hips; tilts the whole upper body
        ├ torso               chest and back (pitched forward: the hunch)
        │  ├ head             cranium; the face is on its front (-z)
        │  │  ├ jaw           the outer, bottom jaw (hinged at the back of the head)
        │  │  └ jaw_inner     the inner jaw, behind and inside the outer one
        │  ├ arm_l / arm_r    upper arm (shoulder joint)
        │  │  └ forearm_*     (elbow)
        │  │     └ fist_*     (wrist); the knuckles stand on the ground
        ├ thigh_l / thigh_r   (hip joint)
        │  └ shin_*           (knee)
        │     └ foot_*        (ankle)

Shell plates are extra bones of kind "shell" hung on these (see plate()); they're drawn in the shell form
and fly off in the shell-breaking animation. Bones of kind "molten" are lava extras drawn only in the
core form. The volcano (see volcano()) adds two more kinds: "vent" (the lava fountain and bombs of the
core form's live eruption, animated by their own controller) and "burst" (the blast of the eruption,
drawn only while it erupts).

All numbers are Bedrock model units (16 = 1 block). The golem's front is -z, its left side +x.
"""
import numpy as np

from kit import geo, ik

SIDES = (("l", 1), ("r", -1))


class Body:
    """Proportions. Every design passes its own values; these defaults are a hulking brute."""

    def __init__(self, **kw):
        # Hips
        self.hip_y = 22.0            # height of the hip pivot (bind pose)
        self.hip_z = 8.0             # hips sit behind the middle
        self.pelvis_pitch = 18.0     # forward tilt of the hips (degrees)
        self.spine_base = 8.0        # torso pivot this far above the hip pivot (bind pose)
        # Torso (bind pose: upright)
        self.torso_len = 30.0        # length along the spine
        self.torso_pitch = 34.0      # forward tilt of the torso on top of the hips
        # Shoulders: joint position on the torso (bind pose, relative to the top of the torso and its pivot)
        self.shoulder_x = 21.0
        self.shoulder_drop = 6.0
        self.shoulder_dz = -2.0
        # Arms (lengths along the bone)
        self.upper_len = 20.0
        self.fore_len = 20.0
        # Legs
        self.hip_x = 11.0
        self.thigh_len = 13.0
        self.shin_len = 12.0
        # Head: neck pivot (bind pose, relative to the top of the torso and the torso pivot)
        self.neck_drop = 4.0
        self.neck_dz = -9.0
        self.head_pitch = None       # None = look level (cancels the body's tilt)
        self.head_look_down = 6.0    # ...then tip the face down a little
        # Rest-pose targets for the contact points (Bedrock): fists and feet on the ground
        self.fist_target = (24.0, -18.0)   # (x, z) of the left fist's knuckle contact; the right one mirrors
        self.foot_target = (12.0, 14.0)
        self.fist_heading = (0.12, -1.0)    # which way the knuckles point (x, z)
        self.foot_heading = (0.08, -1.0)
        # Contact points (bind pose, relative to the end bone's pivot), filled in by the design
        self.fist_contact = (0.0, -12.0, 0.0)
        self.foot_contact = (0.0, -6.0, -3.0)
        # Per-side overrides for lopsided designs: {"r": {"fist_target": (...), "upper_len": ...}}
        self.side = {}
        self.__dict__.update(kw)

    def get(self, name, side):
        return self.side.get(side, {}).get(name, getattr(self, name))


def skeleton(m, b):
    """Creates the core bones (no boxes yet) and returns them by name."""
    bones = {}
    root = m.bone("root", (0, 0, 0))
    pelvis = m.bone("pelvis", (0, b.hip_y, b.hip_z), (b.pelvis_pitch, 0, 0), root)
    torso_pivot = (0, b.hip_y + b.spine_base, b.hip_z)
    torso = m.bone("torso", torso_pivot, (b.torso_pitch, 0, 0), pelvis)
    top = torso_pivot[1] + b.torso_len
    head_pitch = b.head_pitch if b.head_pitch is not None else -(b.pelvis_pitch + b.torso_pitch) + b.head_look_down
    head = m.bone("head", (0, top - b.neck_drop, b.hip_z + b.neck_dz), (head_pitch, 0, 0), torso)
    bones.update(root=root, pelvis=pelvis, torso=torso, head=head)
    for side, sx in SIDES:
        sxp = b.get("shoulder_x", side)
        up = b.get("upper_len", side)
        fo = b.get("fore_len", side)
        sy = top - b.get("shoulder_drop", side)
        sz = b.hip_z + b.get("shoulder_dz", side)
        arm = m.bone(f"arm_{side}", (sx * sxp, sy, sz), parent=torso)
        fore = m.bone(f"forearm_{side}", (sx * sxp, sy - up, sz), parent=arm)
        fist = m.bone(f"fist_{side}", (sx * sxp, sy - up - fo, sz), parent=fore)
        hx = b.get("hip_x", side)
        th = b.get("thigh_len", side)
        sh = b.get("shin_len", side)
        thigh = m.bone(f"thigh_{side}", (sx * hx, b.hip_y, b.hip_z), parent=pelvis)
        shin = m.bone(f"shin_{side}", (sx * hx, b.hip_y - th, b.hip_z), parent=thigh)
        foot = m.bone(f"foot_{side}", (sx * hx, b.hip_y - th - sh, b.hip_z), parent=shin)
        bones.update({f"arm_{side}": arm, f"forearm_{side}": fore, f"fist_{side}": fist,
                      f"thigh_{side}": thigh, f"shin_{side}": shin, f"foot_{side}": foot})
    return bones


def jaws(m, head, hinge, inner_hinge):
    """The two jaw bones, hinged at the back of the head (absolute bind-pose pivots)."""
    jaw = m.bone("jaw", hinge, parent=head)
    jaw_inner = m.bone("jaw_inner", inner_hinge, parent=head)
    return jaw, jaw_inner


# ---------------------------------------------------------------- limbs for the solver


def arm_limb(m, b, side):
    sx = 1 if side == "l" else -1
    # Shoulder: x swings the arm (negative = hand forward), z swings it out (negative on the left side);
    # the elbow bends one way only (forearm can't fold backwards past straight) and is held firmly to its own plane
    # (its side-swing z strongly prefers 0, so a crouching arm bends at the elbow instead of twisting); the wrist
    # keeps the knuckles flat.
    return ik.Limb([
        ik.Joint(f"arm_{side}", (0, 1, 2), lo=(-160, -45, -50), hi=(60, 45, 50), prefer=(None, 0, -8 * sx), weight=0.03),
        ik.Joint(f"forearm_{side}", (0, 2), lo=(-110, -25), hi=(8, 25), prefer=(None, 0), weight=0.2),
        ik.Joint(f"fist_{side}", (0, 1, 2), lo=(-120, -50, -60), hi=(120, 50, 60), prefer=(None, 0, 0), weight=0.01),
    ], f"fist_{side}", contact=_contact(m, f"fist_{side}", b.get("fist_contact", side)))


def leg_limb(m, b, side):
    return ik.Limb([
        ik.Joint(f"thigh_{side}", (0, 1, 2), lo=(-110, -35, -40), hi=(40, 35, 40), prefer=(None, 0, 0), weight=0.03),
        ik.Joint(f"shin_{side}", (0,), lo=(-5,), hi=(140,)),
        ik.Joint(f"foot_{side}", (0, 1, 2), lo=(-120, -40, -40), hi=(80, 40, 40), prefer=(None, 0, 0), weight=0.01),
    ], f"foot_{side}", contact=_contact(m, f"foot_{side}", b.get("foot_contact", side)))


def _contact(m, bone, rel):
    p = m[bone].pivot
    return (p[0] + rel[0], p[1] + rel[1], p[2] + rel[2])


def limbs(m, b):
    out = {}
    for side, _sx in SIDES:
        out[f"arm_{side}"] = arm_limb(m, b, side)
        out[f"leg_{side}"] = leg_limb(m, b, side)
    return out


def rest_targets(b):
    """Contact targets of the rest pose: {limb name: (target point, heading)}."""
    out = {}
    for side, sx in SIDES:
        fx, fz = b.get("fist_target", side)
        hx, hz = b.get("fist_heading", side)
        out[f"arm_{side}"] = ((sx * fx, 0.0, fz), (sx * hx, 0.0, hz))
        lx, lz = b.get("foot_target", side)
        hx, hz = b.get("foot_heading", side)
        out[f"leg_{side}"] = ((sx * lx, 0.0, lz), (sx * hx, 0.0, hz))
    return out


def solve_rest(m, b, verbose=True):
    """Bends the limbs so the knuckles and feet stand flat on their targets, and bakes that into the bones."""
    lims = limbs(m, b)
    pose = {}
    for name, (target, heading) in rest_targets(b).items():
        limb = lims[name]
        # Start from a sensible bend so the solver finds the natural elbow/knee direction.
        if name.startswith("arm"):
            side = name[-1]
            sx = 1 if side == "l" else -1
            tilt = b.pelvis_pitch + b.torso_pitch
            pose[f"arm_{side}"] = ((-tilt - 15.0, 0.0, -8.0 * sx), (0, 0, 0), (1, 1, 1))
            pose[f"forearm_{side}"] = ((-15.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
            pose[f"fist_{side}"] = ((tilt, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
        else:
            side = name[-1]
            pose[f"thigh_{side}"] = ((-50.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
            pose[f"shin_{side}"] = ((70.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
            pose[f"foot_{side}"] = ((-20.0, 0.0, 0.0), (0, 0, 0), (1, 1, 1))
        pose, err, _x = ik.solve(m, pose, limb, target, heading)
        if verbose and err > 0.25:
            print(f"  warning: {m.identifier} rest {name} misses its target by {err:.2f} units")
    ik.bake_rest(m, pose, lims.values())
    return lims


# ---------------------------------------------------------------- shell plates and details


def plate(m, parent, name, center, size, mat, rotation=None, kind="shell", tag=None):
    """
    A separate bone (pivot at its centre, so it tumbles nicely when it flies off) holding one box.
    center is absolute bind-pose Bedrock; rotation is the box's own rotation (degrees) about its centre.
    """
    bone = m.bone(name, center, parent=parent, kind=kind)
    bone.add_c((0, 0, 0), size, mat, rotation=rotation, tag=tag)
    return bone


def abs_box(bone, x0, y0, z0, x1, y1, z1, mat, **kw):
    """A box from two opposite corners (absolute bind-pose)."""
    return bone.add_abs((min(x0, x1), min(y0, y1), min(z0, z1)),
                        (abs(x1 - x0), abs(y1 - y0), abs(z1 - z0)), mat, **kw)


def mirror_x(fn):
    """Calls fn(side, sx) for the left (+x) and right (-x) sides."""
    for side, sx in SIDES:
        fn(side, sx)


def measure(m, kinds=("core", "shell")):
    """Size of the posed model in blocks (width x, height y, depth z) and its Bedrock bounds."""
    mats = m.pose_matrices()
    lo, hi = m.bounds(mats, kinds)
    return (hi - lo) / 16.0, lo, hi


# ---------------------------------------------------------------- the volcano and spikes


class Volcano:
    """What volcano() built: its bones and the crater's top centre (bind pose, absolute Bedrock)."""

    def __init__(self, holder, crater, crater_top):
        self.holder = holder
        self.crater = crater          # the marker bone at the crater (the mod spawns its particles there)
        self.crater_top = crater_top  # absolute bind-pose point at the top of the crater (carried by `crater`)


def upright(m, bone_name):
    """
    Bedrock rotation that cancels a bone's rest-pose orientation: a child bone with it has its +y pointing straight
    up (world) in the rest pose. Used for the eruption's fountain and blast, so lava shoots up and falls down even
    though the volcano itself leans with the back.
    """
    mats = m.pose_matrices()
    return tuple(round(v, 4) for v in geo.euler_bedrock(mats[bone_name][:3, :3].T))


def volcano(m, parent, base, direction=(0.0, 0.0, 1.0), levels=(), crater=(6, 4), prefix="volcano", sink=2,
            molten_sink=None, rock="rock", rim="basalt", pool="crater_lava", lava="molten", grow=1.25,
            molten_levels=None, fountain=12, bombs=4, rim_spikes=0, burst=True, tag="volcano", twist=0.0,
            tooth_mat=None, jet="axis"):
    """
    A stepped volcano cone growing out of `parent` (the torso), lined up with the body instead of standing upright:
      base:      where its axis comes out of the body's surface (absolute bind pose)
      direction: its axis, a Bedrock vector in the parent's bind frame: (0, 0, 1) points straight out of the back,
                 (0.3, 0, 1) splays it out to the golem's left; the levels' depth runs down the spine
      levels:    [(width, depth, height)] of the rock cone, bottom first; the last one is the rim round the crater
      crater:    (width, depth) of the opening at the top
      sink:      the lowest level starts this far inside the body, so the cone grows out of it with no gap
      molten_sink: the same for the core form's lava cone (it stands on the lava body under the shell)
    Rock levels and rim are shell plates (they fly off when the shell breaks). In the crater, a lava pool that always
    glows (the mod draws it with the poured-lava layer, which shows the pool even while dormant). For the core form a
    bigger lava cone ("molten", `grow` times the size, or `molten_levels`) with a fountain and lava bombs ("vent"
    bones) for the live eruption, and a blast column and rocks ("burst" bones) for the eruption itself. They hang on
    frames (`*_spout`, `*_blast`) that point along the cone (jet="axis": the lava shoots out the way the volcano
    leans) or straight up (jet="upright"); the bombs and rocks still fall with real gravity (see anims.py). A second
    marker, `*_crater_aim`, sits on the cone's axis above the crater: the mod aims the eruption's particles from the
    crater towards it.
    """
    holder = m.bone(prefix, base, rotation=geo.aim(direction, twist), parent=parent, kind="core")
    holder.is_volcano = True
    bx, by, bz = base
    y = by - sink
    for i, (w, d, h) in enumerate(levels[:-1]):
        plate(m, holder, f"shell_{prefix}_{i}", (bx, y + h / 2.0, bz), (w, h, d), rock, tag=tag).attach = True
        y += h
    w, d, h = levels[-1]
    cw, cd = crater
    side = (w - cw) / 2.0
    # The rim: four walls round the opening.
    plate(m, holder, f"shell_{prefix}_rim_l", (bx + cw / 2.0 + side / 2.0, y + h / 2.0, bz), (side, h, d), rim,
          tag=tag).attach = True
    plate(m, holder, f"shell_{prefix}_rim_r", (bx - cw / 2.0 - side / 2.0, y + h / 2.0, bz), (side, h, d), rim,
          tag=tag).attach = True
    front = (d - cd) / 2.0
    plate(m, holder, f"shell_{prefix}_rim_f", (bx, y + h / 2.0, bz - cd / 2.0 - front / 2.0), (cw, h, front), rim, tag=tag)
    plate(m, holder, f"shell_{prefix}_rim_b", (bx, y + h / 2.0, bz + cd / 2.0 + front / 2.0), (cw, h, front), rim, tag=tag)
    top = y + h
    for k in range(rim_spikes):
        # Jagged teeth on the rim, leaning outwards, their roots in the rim.
        sx = (-1, 1, 1, -1)[k % 4]
        sz = (-1, -1, 1, 1)[k % 4]
        plate(m, holder, f"shell_{prefix}_tooth_{k}", (bx + sx * (w / 2.0 - 1.5), top + 1.0, bz + sz * (d / 2.0 - 1.5)),
              (2, 4, 2), tooth_mat or rim, rotation=(sz * -14, 0, sx * 14), tag=tag).attach = True
    # The lava pool, just under the rim, tucked under the walls so no gap shows.
    pool_box = holder.add_abs((bx - (cw + 1) / 2.0, top - 3.5, bz - (cd + 1) / 2.0), (cw + 1, 2, cd + 1), pool)
    pool_box.tag = "crater"
    # The marker the mod tracks for particles: in the middle of the opening, at the rim's top.
    crater_bone = m.bone(f"{prefix}_crater", (bx, top, bz), parent=holder, kind="core")
    # Core form: a bigger cone of lava (its own levels, or the rock cone's grown).
    y = by - (molten_sink if molten_sink is not None else sink + 2)
    mlev = molten_levels or [(w_ * grow, d_ * grow, h_ * grow) for (w_, d_, h_) in levels]
    for i, (w_, d_, h_) in enumerate(mlev):
        gw, gd, gh = int(round(w_)), int(round(d_)), int(round(h_))
        mp = plate(m, holder, f"molten_{prefix}_{i}", (bx, y + gh / 2.0, bz), (gw, gh, gd), lava, kind="molten")
        mp.attach = i == 0
        y += gh
    mtop = y
    # Which way the lava shoots: out along the cone (its own lean), or straight up.
    jet_rot = upright(m, prefix) if jet == "upright" else (0.0, 0.0, 0.0)
    m.bone(f"{prefix}_crater_aim", (bx, top + 8, bz), parent=holder, kind="core")
    # The live eruption: a fountain and lava bombs (animated by the volcano controller).
    if fountain or bombs:
        spout = m.bone(f"{prefix}_spout", (bx, mtop, bz), rotation=jet_rot, parent=holder, kind="core")
        if fountain:
            # Its foot starts inside the cone's top, so no gap shows under it.
            sunk = 3 if jet == "upright" else 2
            plate(m, spout, f"vent_{prefix}_fountain", (bx, mtop - sunk + fountain / 2.0, bz),
                  (max(3, cw - 1), fountain, max(3, cd - 1)), "fountain", kind="vent")
        for k in range(bombs):
            plate(m, spout, f"vent_{prefix}_bomb_{k}", (bx, mtop + 2, bz), (4, 4, 4), "fountain", kind="vent")
    # The eruption's blast: a column of lava and rocks thrown out of the crater.
    if burst:
        blast = m.bone(f"{prefix}_blast", (bx, top, bz), rotation=jet_rot, parent=holder, kind="core")
        plate(m, blast, f"burst_{prefix}_column", (bx, top - 3 + 6, bz), (cw + 1, 12, cd + 1), "fountain", kind="burst")
        for k in range(4):
            plate(m, blast, f"burst_{prefix}_rock_{k}", (bx, top + 1, bz), (3, 3, 3), rock, kind="burst")
    return Volcano(holder, crater_bone, (bx, top, bz))


def spike(m, parent, name, base, height, width, lean=(0.0, 0.0, 0.0), mat="obsidian", kind="shell", steps=3,
          embed=0, direction=None, twist=0.0, root_mat=None):
    """
    A tapering spike: `steps` stacked boxes from `width` at the base to 1 at the tip, `height` long from `base`.
    It points along `direction` (a Bedrock vector in the parent's bind frame) when given, else it leans by `lean`
    (Bedrock degrees about its base) from straight up. `embed` (whole units) sinks its first box that far into what
    it grows out of, so its foot sits flush at any angle; `root_mat` paints that first box differently (glowing
    cracks where it breaks out of the stone). One bone, so it flies off in one piece.
    """
    rot = geo.aim(direction, twist) if direction is not None else (tuple(lean) if any(lean) else None)
    bone = m.bone(name, base, parent=parent, kind=kind)
    bone.attach = True
    y = 0.0
    hs = [int(round(height / steps))] * steps
    hs[-1] = max(1, int(round(height - sum(hs[:-1]))))
    for i in range(steps):
        w = max(1, int(round(width - (width - 1) * i / max(1, steps - 1))))
        e = int(embed) if i == 0 else 0
        box_mat = root_mat if (i == 0 and root_mat) else mat
        bone.add((-w / 2.0, y - e, -w / 2.0), (w, hs[i] + e, w), box_mat, rotation=rot, pivot=base)
        y += hs[i]
    return bone


def back_dir(rake=0.0, splay=0.0, side=1):
    """
    A direction straight out of the golem's back (torso +z), raked `rake` degrees down the spine towards its tail
    and splayed `splay` degrees out to one side (side 1 = its left, -1 = its right). For spikes and volcanoes.
    """
    r, s_ = np.radians(rake), np.radians(splay)
    d = np.array([side * np.sin(s_) * np.cos(r), -np.sin(r), np.cos(s_) * np.cos(r)])
    return tuple(float(v) for v in d / np.linalg.norm(d))
