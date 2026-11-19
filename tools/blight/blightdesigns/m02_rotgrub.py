"""M2 Rotgrub: a fat maggot as big as a pig, glowing green inside, squirming through the rot."""
import math

import numpy as np

from blightkit import bmats as B

NUMBER = 2
ID = "rotgrub"
NAME = "Rotgrub"
MOOD = "fat maggot"
BLURB = ("A maggot as big as a pig, fat and pale and ringed with wrinkles, its insides glowing green through its skin, "
         "black mouth hooks working at its small blunt head, little stubby legs underneath. Rotgrubs squirm out of "
         "rotting logs and carcasses and chew whatever lies still; left alone long enough, one pupates into something "
         "with wings. About a block tall and 2 long.")

SEGMENTS = 8


def build(s):
    # The body: fat ringed segments, the front curling up a little to the head.
    for k in range(SEGMENTS):
        t = k / (SEGMENTS - 1)
        z = 14.0 - t * 26.0
        r = 6.0 + 2.5 * math.sin(math.pi * min(1.0, t * 1.15))
        y = r + (t ** 3) * 4.0
        L = 5.0 + (k % 2) * 0.6
        s.box((0.0, y, z), (2.0 * r, 2.0 * r * 0.92 * 0.62, L), "body", inflate=0.05 * (k % 3))
        s.box((0.0, y, z), (2.0 * r * 0.62, 2.0 * r * 0.92, L), "body", inflate=0.12 + 0.05 * (k % 3))
        if 0 < k < SEGMENTS - 1:
            s.box((0.0, y, z + L / 2.0), (2.0 * r * 0.8, 2.0 * r * 0.92 * 0.8, 1), "fold", inflate=0.3)
        if 1 <= k <= 5:
            for sign in (1.0, -1.0):
                s.seg((sign * r * 0.45, 3.5, z), (sign * r * 0.7, 0.0, z - 0.5), 2, 2, "leg")
    # The head: small and blunt, black mouth hooks, two dark eyespots.
    head = np.array([0.0, 11.0, -14.5])
    s.blob(head, (4.5, 4.0, 3.5), "body", slices=2)
    for sign in (1.0, -1.0):
        s.seg(head + np.array([sign * 1.2, -1.0, -2.5]), head + np.array([sign * 0.6, -3.0, -5.0]), 1, 1, "hook")
        s.seg(head + np.array([sign * 2.4, 1.5, -2.8]), head + np.array([sign * 2.4, 1.5, -3.4]), 1, 1, "spot",
              side=(1, 0, 0))
    s.seg(head + np.array([0.0, -1.5, -3.0]), head + np.array([0.0, -1.5, -3.8]), 2, 1, "mouth", side=(1, 0, 0))
    # Two breathing spiracles at its tail end.
    for sign in (1.0, -1.0):
        s.seg((sign * 2.0, 6.0, 16.4), (sign * 2.0, 6.0, 17.2), 2, 2, "spot", side=(1, 0, 0))


def materials():
    grub = B.palette("#6a6450", "#847e66", "#a09a80", "#b8b29a", "#ceC8b2", "#e2dccc")
    return {
        "body": B.spotted(grub, B.palette("#4a7a18", "#64a020", "#86c42e", "#aee05a"), seed=821, cell=3.0,
                          radius=0.38, density=0.45, mottle=1.0, glow_pal=B.TOXIC, glow_alpha=160.0, up_only=False),
        "fold": B.skin(B.palette("#4e4a3a", "#625c48", "#787058", "#8e8668"), seed=823),
        "leg": B.skin(grub, seed=825),
        "hook": B.solid(B.palette("#0a0806", "#14100c", "#1e1812", "#2a2218"), seed=827),
        "spot": B.solid(B.palette("#0a0806", "#14100c", "#1e1812"), seed=829),
        "mouth": B.mouth(),
    }
