"""
The Cinder Colossus's minions, summoned during the fight (about a block tall, built on the same skeleton as the
Colossus, so the same limb solver and painter work on them):

  stone_golem  - the Cracking stage's: a small hunched brute of scorched stone and obsidian on big fists, dim lava
                 cracks, two glowing eyes and a pair of obsidian spikes on its back.
  lava_golem   - the Lava Core stage's: the same little brute made of lava, a few plates of black crust, white-hot
                 eyes. It swells and blows up a few seconds after it rises (the mod scales it up; see LavaGolem).

Each is a design module of its own shape: ID, NAME, body(), build(m, b), materials(), overlays(), ANIM (the walk's
style). build.py builds them with the Colossus (python tools/golems/build.py --minions-only builds just these).
"""
from designs import common as C
from kit import materials as M

TEXTURE_WIDTH = 128


def body():
    return C.Body(hip_y=6.0, hip_z=2.0, pelvis_pitch=12.0, torso_pitch=36.0, spine_base=2.0, torso_len=8.0,
                  shoulder_x=5.5, shoulder_drop=2.0, shoulder_dz=0.0, upper_len=4.0, fore_len=4.6,
                  hip_x=2.5, thigh_len=3.5, shin_len=3.0, neck_drop=1.0, neck_dz=-2.5, head_look_down=4.0,
                  fist_target=(6.5, -4.5), fist_heading=(0.1, -1.0), fist_contact=(0.0, -3.5, 0.0),
                  foot_target=(3.0, 2.5), foot_heading=(0.12, -1.0), foot_contact=(0.0, -1.5, -0.5))


def frame(m, b, skin, fist_mat, crust=None, spikes="obsidian", spike_root=None):
    """
    The little brute's boxes, the same for both: hips, a chest with a hump, a head with a jaw, thick forearms and
    big fists, short legs, and two spikes out of its back. `skin` paints the body, `crust` (if any) the plates on
    its shoulders and back.
    """
    B = C.skeleton(m, b)
    head = B["head"]
    hp = head.pivot
    B["pelvis"].add((-3.5, -2.0, -2.5), (7, 4, 5), skin)
    tor = B["torso"]
    tor.add((-5.0, -1.0, -4.0), (10, 9, 7), skin)
    tor.add((-4.0, 3.0, 2.0), (8, 5, 3), crust or skin)                     # the hump of its back
    head.add((-3.0, -2.0, -5.5), (6, 5, 6), skin, tag="skull" if crust is None else "cranium")
    jaw, jaw_in = C.jaws(m, head, (0, hp[1] - 1.0, hp[2] - 0.5), (0, hp[1] - 0.5, hp[2] - 1.0))
    jaw.add((-3.0, -2.5, -5.5), (6, 2, 5), skin)
    jaw_in.add((-2.0, -1.5, -4.5), (4, 1, 3), "fang")
    for side, sx in C.SIDES:
        B[f"arm_{side}"].add((-1.5, -4.0, -1.5), (3, 5, 3), skin)
        B[f"forearm_{side}"].add((-2.0, -4.5, -2.0), (4, 5, 4), skin)
        B[f"fist_{side}"].add((-2.5, -3.5, -2.5), (5, 4, 5), fist_mat)
        B[f"thigh_{side}"].add((-1.5, -3.5, -1.5), (3, 4, 3), skin)
        B[f"shin_{side}"].add((-1.5, -3.0, -1.5), (3, 3, 3), skin)
        B[f"foot_{side}"].add((-1.5, -1.5, -3.0), (3, 2, 4), skin)
        if crust is not None:
            # Plates of cooled crust on its shoulders.
            B[f"arm_{side}"].add((-2.0, -1.0, -2.0), (4, 2, 4), crust)
    # Two spikes out of its back, raked towards its tail.
    tp = tor.pivot
    for i, (y, length, rake) in enumerate(((7.0, 4, 10.0), (3.5, 3, 30.0))):
        C.spike(m, tor, f"spike_{i}", (tp[0], tp[1] + y, tp[2] + 4.5), length, 2, mat=spikes, kind="core", steps=2,
                embed=1, direction=C.back_dir(rake), root_mat=spike_root)
    return B


class Stone:
    ID = "stone_golem"
    NAME = "Stone Golem"
    body = staticmethod(body)

    @staticmethod
    def build(m, b):
        frame(m, b, "rock", "knuckles", spike_root="spikeroot")
        return {}

    @staticmethod
    def materials():
        return {
            "rock": M.rock(seed=121, chunk=4.0, obsidian=0.3, cracks=0.1, fissures=0.0, heat=-0.7, scorch=0.4,
                           cobble=M.COBBLE_DARK, stone_cell=2.2, crack_width=0.45),
            "knuckles": M.rock(seed=122, chunk=3.0, obsidian=0.75, cracks=0.08, fissures=0.0, heat=-0.7, scorch=0.4,
                               stone_cell=2.0, crack_width=0.45),
            "obsidian": M.rock(seed=123, chunk=2.5, obsidian=1.0, cracks=0.1, fissures=0.0, heat=-0.6),
            "spikeroot": M.rock(seed=124, chunk=1.5, obsidian=1.0, cracks=0.6, fissures=0.0, heat=-0.2, crack_width=0.5),
            "fang": M.fang(seed=125),
        }

    @staticmethod
    def overlays():
        return [M.face_overlay(eye_px=[(-2, 0), (1, 0)], skull_tag="skull", core_tag="cranium")]

    ANIM = dict(walk_cycle=0.55, stride=14.0, arm_lift=3.0, leg_lift=2.2, bob=0.6, sway=0.5, roll=4.0, arm_bow=1.6,
                arm_reach=1.0, walk_crouch=0.4, idle_period=2.6, weight=0.35)


class Lava:
    ID = "lava_golem"
    NAME = "Lava Golem"
    body = staticmethod(body)

    @staticmethod
    def build(m, b):
        frame(m, b, "lava", "lava_fist", crust="crust", spikes="crust")
        return {}

    @staticmethod
    def materials():
        return {
            "lava": M.lava(seed=131, crust=1.1, hot=0.5, flow_cell=5.0, edge_cool=0.6, plates=0.2, plate_cell=3.0),
            "lava_fist": M.lava(seed=132, crust=1.05, hot=0.2, flow_cell=4.0, edge_cool=0.8, plates=0.35,
                                plate_cell=2.5),
            "crust": M.rock(seed=133, chunk=2.0, obsidian=0.9, cracks=0.45, fissures=0.0, heat=0.4, crack_width=0.5),
            "fang": M.fang(seed=134),
        }

    @staticmethod
    def overlays():
        return [M.face_overlay(eye_px=[(-3, 0), (-2, 0), (1, 0), (2, 0)], cracks=[((-1, -2), (0, -2))],
                               skull_tag="skull", core_tag="cranium")]

    ANIM = dict(walk_cycle=0.45, stride=13.0, arm_lift=3.2, leg_lift=2.4, bob=0.7, sway=0.6, roll=5.0, arm_bow=1.6,
                arm_reach=1.0, walk_crouch=0.4, idle_period=1.8, weight=0.3)


ALL = (Stone, Lava)
