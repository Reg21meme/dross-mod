"""Tiny numpy-only image helpers (no scipy needed)."""
import numpy as np


def gaussian_blur(a, sigma):
    if sigma <= 0:
        return a.copy()
    r = int(max(1, round(sigma * 3)))
    xs = np.arange(-r, r + 1)
    k = np.exp(-(xs ** 2) / (2 * sigma * sigma))
    k /= k.sum()
    out = a.astype(float)
    # blur along each axis with zero padding (mass that spreads past the edge is lost)
    for axis in range(out.ndim):
        pad = [(0, 0)] * out.ndim
        pad[axis] = (r, r)
        p = np.pad(out, pad)
        acc = np.zeros_like(out)
        for i, w in enumerate(k):
            sl = [slice(None)] * out.ndim
            sl[axis] = slice(i, i + out.shape[axis])
            acc += w * p[tuple(sl)]
        out = acc
    return out
