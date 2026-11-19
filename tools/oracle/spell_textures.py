"""
The Blind Oracle's spell: the two rune circles drawn on the ground round him while he breaks a barrier
(quest/client/OracleSpellEffects), and a preview (no game needed).

Run from the project folder:    python tools/oracle/spell_textures.py

The circles are white with a soft glow; the game tints them with DrossColors (ORACLE_SPELL_*) and draws them glowing,
the big one turning one way and the small one the other. Their runes are the land wards' own (tools/terrain/
ward_particles.py, RUNES), so the spell and the barriers it breaks share one script.

Writes:
    src/main/resources/assets/dross/textures/effect/oracle_spell_circle.png   the big circle (512 x 512)
    src/main/resources/assets/dross/textures/effect/oracle_spell_ring.png     the small inner ring (256 x 256)
    quest-previews/oracle_spell_circles.png                                   both, tinted, on a night ground

Needs Pillow and numpy.
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "terrain"))
import ward_particles  # noqa: E402  (its RUNES; nothing runs on import)

OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "textures", "effect")
PREVIEW = os.path.join(ROOT, "quest-previews", "oracle_spell_circles.png")

SUPER = 2            # drawn at twice the size, then shrunk (smooth lines)
GLOW_BLUR = 5        # the soft glow round every line (pixels at the final size)
GLOW_SHARE = 0.55    # how bright the glow is next to the line

# ---- the big circle (radii as a share of half its width)
CIRCLE_SIZE = 512
OUTER_RINGS = (0.975, 0.945)        # two thin rings at its rim
RUNE_BAND = (0.80, 0.93)            # the band of runes inside them
RUNE_COUNT = 18
BAND_INNER_RING = 0.785
STAR_POINTS = 7                     # a seven-pointed star (every third point joined)
STAR_RADIUS = 0.77
MIDDLE_RING = 0.47
TICKS = 56                          # little ticks just outside the middle ring
TICK_LENGTH = 0.035
HUB_RING = 0.13

# ---- the small ring
RING_SIZE = 256
RING_OUTER = 0.96
RING_INNER = 0.70
RING_RUNES = 10
RING_SPOKES = 20


def runes_band(draw, size, inner, outer, count, width, offset=0.0):
    """Runes stood round a band, each turned so its top points outwards."""
    half = size / 2
    rune_px = (outer - inner) * half
    scale = rune_px / ward_particles.RUNE_SIZE
    for i in range(count):
        name, strokes = ward_particles.RUNES[i % len(ward_particles.RUNES)]
        a = 2 * math.pi * (i + offset) / count
        mid = (inner + outer) / 2 * half
        cx, cy = half + math.cos(a) * mid, half + math.sin(a) * mid
        rot = a + math.pi / 2    # the rune's "up" points away from the middle
        for path in strokes:
            pts = []
            for (x, y) in path:
                lx = (x + 0.5 - ward_particles.RUNE_SIZE / 2) * scale
                ly = (y + 0.5 - ward_particles.RUNE_SIZE / 2) * scale
                rx = lx * math.cos(rot) - ly * math.sin(rot)
                ry = lx * math.sin(rot) + ly * math.cos(rot)
                pts.append((cx + rx, cy + ry))
            if len(pts) == 1 or pts[0] == pts[-1] and len(pts) == 2:
                draw.ellipse([pts[0][0] - width, pts[0][1] - width, pts[0][0] + width, pts[0][1] + width], fill=255)
            else:
                draw.line(pts, fill=255, width=width, joint="curve")


def ring(draw, size, r, width):
    half = size / 2
    rr = r * half
    draw.ellipse([half - rr, half - rr, half + rr, half + rr], outline=255, width=width)


def finish(mask, size):
    """The lines shrunk to size, a soft glow added round them: (alpha 0-1)."""
    line = np.asarray(mask.resize((size, size), Image.LANCZOS), dtype=float) / 255.0
    glow = np.asarray(Image.fromarray((line * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(GLOW_BLUR)),
                      dtype=float) / 255.0
    return np.clip(np.maximum(line, glow * GLOW_SHARE * 2.2), 0, 1)


def circle():
    s = CIRCLE_SIZE * SUPER
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    w = 3 * SUPER
    for r in OUTER_RINGS:
        ring(d, s, r, w)
    ring(d, s, BAND_INNER_RING, w)
    runes_band(d, s, RUNE_BAND[0], RUNE_BAND[1], RUNE_COUNT, 3 * SUPER)
    half = s / 2
    pts = [(half + math.cos(2 * math.pi * i / STAR_POINTS - math.pi / 2) * STAR_RADIUS * half,
            half + math.sin(2 * math.pi * i / STAR_POINTS - math.pi / 2) * STAR_RADIUS * half) for i in range(STAR_POINTS)]
    order = [pts[(i * 3) % STAR_POINTS] for i in range(STAR_POINTS + 1)]
    d.line(order, fill=255, width=w, joint="curve")
    for p in pts:
        rr = 0.03 * half
        d.ellipse([p[0] - rr, p[1] - rr, p[0] + rr, p[1] + rr], outline=255, width=w)
    ring(d, s, MIDDLE_RING, w)
    for i in range(TICKS):
        a = 2 * math.pi * i / TICKS
        r0, r1 = MIDDLE_RING * half, (MIDDLE_RING + TICK_LENGTH * (2 if i % 4 == 0 else 1)) * half
        d.line([(half + math.cos(a) * r0, half + math.sin(a) * r0), (half + math.cos(a) * r1, half + math.sin(a) * r1)],
               fill=255, width=2 * SUPER)
    ring(d, s, HUB_RING, w)
    ring(d, s, HUB_RING * 0.55, 2 * SUPER)
    return finish(m, CIRCLE_SIZE)


def small_ring():
    s = RING_SIZE * SUPER
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    w = 3 * SUPER
    ring(d, s, RING_OUTER, w)
    ring(d, s, RING_INNER, w)
    runes_band(d, s, RING_INNER + 0.04, RING_OUTER - 0.04, RING_RUNES, 3 * SUPER, 0.5)
    half = s / 2
    for i in range(RING_SPOKES):
        a = 2 * math.pi * i / RING_SPOKES
        r0, r1 = 0.25 * half, (RING_INNER - 0.04) * half
        if i % 2 == 0:
            d.line([(half + math.cos(a) * r0, half + math.sin(a) * r0), (half + math.cos(a) * r1, half + math.sin(a) * r1)],
                   fill=255, width=2 * SUPER)
    ring(d, s, 0.25, w)
    return finish(m, RING_SIZE)


def save(alpha, path):
    rgba = np.zeros(alpha.shape + (4,), dtype=np.uint8)
    rgba[..., 0:3] = 255
    rgba[..., 3] = (alpha * 255).round().astype(np.uint8)
    Image.fromarray(rgba, "RGBA").save(path)


def preview(big, small):
    size = CIRCLE_SIZE
    bg = np.zeros((size, size, 3))
    bg[:] = (14, 18, 34)
    glow = np.array((127, 168, 255), dtype=float)
    core = np.array((46, 107, 255), dtype=float)
    img = bg + big[..., None] * glow
    pad = (size - int(size * 0.62)) // 2
    sm = np.asarray(Image.fromarray((small * 255).astype(np.uint8)).resize((size - 2 * pad, size - 2 * pad)), dtype=float) / 255
    img[pad:size - pad, pad:size - pad] += sm[..., None] * core
    os.makedirs(os.path.dirname(PREVIEW), exist_ok=True)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(PREVIEW)


def main():
    os.makedirs(OUT, exist_ok=True)
    big = circle()
    small = small_ring()
    save(big, os.path.join(OUT, "oracle_spell_circle.png"))
    save(small, os.path.join(OUT, "oracle_spell_ring.png"))
    preview(big, small)
    print("wrote the spell's two circles and", os.path.relpath(PREVIEW, ROOT))


if __name__ == "__main__":
    main()
