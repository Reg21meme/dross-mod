"""No. 1 The Glutton: a wolverine the size of a car, low, broad and snarling, frost on its fur, claws of black ice."""
import numpy as np

from rimekit import mats as M

NUMBER = 1
ID = "glutton"
NAME = "The Glutton"
MOOD = "always hungry"
BLURB = "A wolverine the size of a car: low, broad and snarling, a pale band down its flanks, claws of black ice."


def build(s):
    # Body: deep shoulders, a belly, the back arching up over the hips.
    s.blob((0, 24, -12), (14, 13, 15), "fur")
    s.blob((0, 22, 1), (13, 11, 15), "fur")
    s.blob((0, 27, 13), (13, 13, 14), "fur")
    s.blob((0, 31, 4), (11, 8, 12), "fur")
    # Neck and head, held low.
    s.blob((0, 24, -25), (10, 10, 8), "fur", axis=(0, 0.6, -1))
    s.blob((0, 21, -34), (10, 9, 9), "fur")
    s.rbox((0, 19, -43), (11, 8, 9), "fur", c=2)
    s.box((0, 21, -48), (5, 3, 2), "nose")
    # Snarl: upper lip, open lower jaw, the dark mouth between, icy fangs.
    s.seg((0, 15.5, -37), (0, 11, -46), 9, 3, "fur", side=(1, 0, 0))
    s.box((0, 14.8, -43.5), (7, 2, 6), "mouth")
    for x in (-3.4, 3.4):
        s.cone((x, 16, -46.2), (x * 0.95, 12.6, -46.6), 1.6, "teeth", steps=2)
        s.cone((x * 0.85, 11.6, -45.2), (x * 0.85, 14.0, -45.6), 1.4, "teeth", steps=2)
    for x in (-1.5, 0.0, 1.5):
        s.box((x, 15.4, -46.8), (1, 1, 1), "teeth")
    # Ears and eyes.
    for x in (-6.5, 6.5):
        s.rbox((x, 28.5, -32), (4, 4, 2), "fur", c=1)
        s.box((x * 0.78, 23.2, -41.6), (2, 2, 1), "eye")
    # Legs: thick and short, big paws, long claws.
    for x in (-1, 1):
        s.taper([(10 * x, 23, -14), (12 * x, 12, -16), (13 * x, 4, -19)], [11, 9, 8], "fur")
        s.rbox((13 * x, 2.5, -21.5), (9, 5, 11), "fur", c=1)
        for dx in (-3.0, -1.0, 1.0, 3.0):
            s.cone((13 * x + dx, 3.0, -26.5), (13 * x + dx * 1.1, 0.6, -31.5), 1.6, "claw", steps=2)
        s.taper([(10 * x, 27, 16), (12 * x, 15, 21), (12 * x, 5, 24)], [12, 9, 7], "fur")
        s.rbox((12 * x, 2.5, 21.5), (9, 5, 12), "fur", c=1)
        for dx in (-2.5, 0.0, 2.5):
            s.cone((12 * x + dx, 2.5, 15.0), (12 * x + dx * 1.1, 0.6, 11.5), 1.4, "claw", steps=2)
    # A bushy tail.
    s.taper([(0, 31, 26), (0, 30, 35), (0, 25, 43), (0, 19, 47)], [9, 11, 10, 6], "fur")
    # Crusts of hoarfrost along the spine.
    for z, y, x in ((-15, 37.0, 2), (-5, 36.5, -3), (6, 40.5, 1), (15, 39.5, -2), (22, 37.5, 3)):
        s.box((x, y, z), (4, 2, 3), "rime", rot=(0, 25 * np.sign(z), 0))
        s.box((x * 0.5, y + 0.8, z + 1.5), (2, 2, 2), "rime", rot=(0, -20, 0))


def band(tx, col, k):
    """The wolverine's pale band: from the shoulders along each flank to the base of the tail."""
    side = np.abs(tx.Nw[:, 0]) > 0.35
    zz = tx.Pw[:, 2]
    centre = 25.0 + 0.12 * zz
    wob = (M.noise.fbm(tx.Pw, 3.0, 2, 77) - 0.5) * 3.0
    m = side & (np.abs(tx.Pw[:, 1] - centre + wob) < 2.6) & (zz > -16) & (zz < 32)
    return np.where(m[:, None], M.pick(M.WOLV_BAND, k + 0.4), col)


def materials():
    return {
        "fur": M.fur(M.WOLVERINE, seed=101, strand=3.4, frost_amount=0.32, pattern=band),
        "rime": M.snow(M.SNOW, seed=103),
        "nose": M.chitin(M.BLACK_ICE, seed=105, gloss=1.6),
        "mouth": M.mouth(),
        "teeth": M.teeth(),
        "claw": M.claw(),
        "eye": M.eye(M.GLOW, pupil=False),
    }
