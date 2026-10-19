"""No. 6 Water Bear: a giant tardigrade, plated with frost, that survives anything."""
import numpy as np

from rimekit import mats as M

NUMBER = 6
ID = "waterbear"
NAME = "Water Bear"
MOOD = "survived it all"
BLURB = ("A giant tardigrade, the tiny real animal that survives being frozen solid: a chubby, wrinkly barrel body "
         "with plates of frost on its back, eight stubby clawed legs and a round tube mouth full of needle teeth.")

SEGMENTS = (  # (centre z, centre y, rx, ry, rz)
    (-50, 40, 21, 21, 15),
    (-29, 44, 29, 31, 15),
    (-5, 46, 31, 33, 16),
    (19, 44, 29, 31, 15),
    (40, 39, 23, 24, 13),
)


def build(s):
    for z, y, rx, ry, rz in SEGMENTS:
        s.blob((0, y, z), (rx, ry, rz), "skin")
    # Frost armour: an arch of plates over each body segment, hugging its curve like a pill bug's.
    for z, y, rx, ry, rz in SEGMENTS[1:4]:
        for k in range(-3, 4):
            th = np.radians(k * 24.0)
            px, py = np.sin(th) * (rx + 1.0), np.cos(th) * (ry + 1.0)
            n = np.array([np.sin(th) / rx, np.cos(th) / ry, 0.0])
            arc = (rx + ry) / 2.0 * np.radians(24.0) * 1.15
            s.plate((px, y + py, z), n, (0, 0, 1), arc, rz * 1.55, 3, "plate")
    # The snout: a soft tube, a ring of lips round the mouth, two needle stylets poking out.
    s.blob((0, 35, -62), (13, 12, 10), "skin", axis=(0, 0.25, -1))
    s.ring((0, 33, -71), (0, -0.15, -1), 7, 3, 4, "lip", n=8)
    s.box((0, 33, -72), (8, 8, 2), "mouth")
    for x in (-1, 1):
        s.seg((1.5 * x, 33.5, -70), (1 * x, 32, -78), 1, 1, "teeth")
        s.box((9 * x, 48, -60.5), (2, 2, 1), "eye")
    # Four pairs of stubby legs, each foot with a fan of long claws.
    for z, out in ((-30, 1.0), (-5, 1.0), (19, 1.0), (42, 0.7)):
        for x in (-1, 1):
            top = (25 * x, 24, z)
            foot = (31 * x * out, 5, z + (6 if z > 30 else 0))
            s.taper([top, foot], [17, 13], "skin")
            s.blob(foot, (8, 4.5, 9), "skin")
            for k in range(5):
                dx = (k - 2) * 3.0
                fz = foot[2] - 7 if z < 30 else foot[2] + 7
                tip_z = fz - 9 if z < 30 else fz + 9
                s.cone((foot[0] + dx, 4, fz), (foot[0] + dx * 1.7, 0.6, tip_z), 2.4, "claw", steps=3)


def wrinkly(base):
    """Skin with the creases of a tardigrade's body rings (darker bands round the body)."""

    def paint(tx):
        out = base(tx)
        band = np.sin(tx.Pw[:, 2] * 0.62 + (M.noise.fbm(tx.Pw, 6.0, 2, 641) - 0.5) * 3.0)
        crease = band > 0.82
        out["color"] = np.where(crease[:, None], out["color"] * 0.72, out["color"])
        return out

    return paint


def materials():
    return {
        "skin": wrinkly(M.skin(M.PALE_SKIN, seed=601, mottle=1.2, frost_amount=0.2)),
        "lip": M.skin(M.STAR_FLESH, seed=603, mottle=0.6, frost_amount=0.0),
        "plate": M.glacier(M.GLACIER, seed=605, snow_amount=0.5, bands=0.6),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        "eye": M.chitin(M.BLACK_ICE, seed=607, gloss=2.0),
        "claw": M.claw(),
    }
