"""
A fast, approximate sketch of the Dross terrain, for tuning worldgen.py's numbers without starting the game.

Run from the project folder (after worldgen.py):    python tools/terrain/terrain_sketch.py [--seeds 8] [--out DIR]

It reads the worldgen JSON that worldgen.py wrote (the noises, the density functions, the biome source) and works it out
with numpy, using a copy of Minecraft's noise maths (ImprovedNoise, PerlinNoise, NormalNoise, cubic splines). The random
numbers are numpy's, not Minecraft's, so a seed here is NOT the same world as that seed in the game; but the shapes and
the statistics (biome shares, region sizes, heights) are the same kind. The real check is the GameTest
dimension/TerrainSamplingGameTest and the previews of dimension/TerrainPreviewGameTest (terrain-previews/), which use
the game's own code.

Writes (into --out, default build/terrain-sketch/): sketch_biomes_<seed>.png, sketch_relief_<seed>.png, sketch_stats.txt.
Needs numpy, scipy and Pillow.
"""
import argparse
import json
import math
import os
import re
import sys
import zlib

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
WORLDGEN = os.path.join(DATA, "dross", "worldgen")
COLORS_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "DrossColors.java")
MOUNTAIN_MIN_AREA = 256 * 256     # a Gravecrags region at least this big (blocks^2) counts as a mountain range for its peak
BIOMES = ["silverwood_forest", "cinder_wastes", "blightwood", "rimefall", "gravecrags"]
PREVIEW_COLOR_NAMES = {"silverwood_forest": "PREVIEW_SILVERWOOD", "cinder_wastes": "PREVIEW_CINDER",
                       "blightwood": "PREVIEW_BLIGHTWOOD", "rimefall": "PREVIEW_RIMEFALL",
                       "gravecrags": "PREVIEW_GRAVECRAGS"}

# ---------------------------------------------------------------------------------------------------------------------
# Minecraft's noise, in numpy
# ---------------------------------------------------------------------------------------------------------------------
GRAD = np.array([[1, 1, 0], [-1, 1, 0], [1, -1, 0], [-1, -1, 0], [1, 0, 1], [-1, 0, 1], [1, 0, -1], [-1, 0, -1],
                 [0, 1, 1], [0, -1, 1], [0, 1, -1], [0, -1, -1], [1, 1, 0], [0, -1, 1], [-1, 1, 0], [0, -1, -1]],
                dtype=np.float64)


def smooth(t):
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0)


class ImprovedNoise:
    def __init__(self, rng):
        self.xo, self.yo, self.zo = rng.random(3) * 256.0
        self.p = rng.permutation(256).astype(np.int64)

    def noise(self, x, y, z):
        x = x + self.xo
        y = y + self.yo
        z = z + self.zo
        i = np.floor(x).astype(np.int64)
        j = np.floor(y).astype(np.int64)
        k = np.floor(z).astype(np.int64)
        fx, fy, fz = x - i, y - j, z - k
        p = self.p

        def P(v):
            return p[v & 255]

        a, b = P(i), P(i + 1)
        aa, ab, ba, bb = P(a + j), P(a + j + 1), P(b + j), P(b + j + 1)

        def g(h, dx, dy, dz):
            gr = GRAD[h & 15]
            return gr[..., 0] * dx + gr[..., 1] * dy + gr[..., 2] * dz

        d0 = g(P(aa + k), fx, fy, fz)
        d1 = g(P(ba + k), fx - 1, fy, fz)
        d2 = g(P(ab + k), fx, fy - 1, fz)
        d3 = g(P(bb + k), fx - 1, fy - 1, fz)
        d4 = g(P(aa + k + 1), fx, fy, fz - 1)
        d5 = g(P(ba + k + 1), fx - 1, fy, fz - 1)
        d6 = g(P(ab + k + 1), fx, fy - 1, fz - 1)
        d7 = g(P(bb + k + 1), fx - 1, fy - 1, fz - 1)
        u, v, w = smooth(fx), smooth(fy), smooth(fz)
        x00 = d0 + u * (d1 - d0)
        x10 = d2 + u * (d3 - d2)
        x01 = d4 + u * (d5 - d4)
        x11 = d6 + u * (d7 - d6)
        y0 = x00 + v * (x10 - x00)
        y1 = x01 + v * (x11 - x01)
        return y0 + w * (y1 - y0)


class PerlinNoise:
    def __init__(self, rng, first_octave, amplitudes):
        n = len(amplitudes)
        self.amplitudes = amplitudes
        self.levels = [ImprovedNoise(rng) if a != 0 else None for a in amplitudes]
        self.input_factor = 2.0 ** first_octave
        self.value_factor = 2.0 ** (n - 1) / (2.0 ** n - 1.0)

    def value(self, x, y, z):
        total = 0.0
        f, v = self.input_factor, self.value_factor
        for amp, level in zip(self.amplitudes, self.levels):
            if level is not None:
                total = total + amp * level.noise(x * f, y * f, z * f) * v
            f *= 2.0
            v /= 2.0
        return total


class NormalNoise:
    def __init__(self, rng, first_octave, amplitudes):
        self.first = PerlinNoise(rng, first_octave, amplitudes)
        self.second = PerlinNoise(rng, first_octave, amplitudes)
        nz = [i for i, a in enumerate(amplitudes) if a != 0]
        span = nz[-1] - nz[0]
        self.factor = (1.0 / 6.0) / (0.1 * (1.0 + 1.0 / (span + 1)))

    def value(self, x, y, z):
        k = 1.0181268882175227
        return (self.first.value(x, y, z) + self.second.value(x * k, y * k, z * k)) * self.factor


# ---------------------------------------------------------------------------------------------------------------------
# The density functions (2D only: everything the land shape and the climate need)
# ---------------------------------------------------------------------------------------------------------------------

def load(kind, ident):
    ns, path = ident.split(":", 1)
    with open(os.path.join(DATA, ns, "worldgen", kind, path + ".json"), encoding="utf-8") as f:
        return json.load(f)


class World:
    def __init__(self, seed):
        self.seed = seed
        self.noises = {}
        self.functions = {}

    def noise(self, ident):
        if ident not in self.noises:
            params = load("noise", ident)
            rng = np.random.default_rng(zlib.crc32(f"{self.seed}:{ident}".encode()))
            self.noises[ident] = NormalNoise(rng, params["firstOctave"], params["amplitudes"])
        return self.noises[ident]

    def evaluate(self, node, x, z, cache):
        if isinstance(node, (int, float)):
            return np.full(x.shape, float(node))
        if isinstance(node, str):
            if node in cache:
                return cache[node]
            if node == "minecraft:y":
                raise ValueError("3D function in a 2D sketch")
            value = self.evaluate(load("density_function", node), x, z, cache)
            cache[node] = value
            return value
        t = node["type"].split(":", 1)[1]
        e = lambda n: self.evaluate(n, x, z, cache)
        if t in ("flat_cache", "cache_2d", "cache_once", "interpolated", "cache_all_in_cell"):
            return e(node["argument"])
        if t == "add":
            return e(node["argument1"]) + e(node["argument2"])
        if t == "mul":
            return e(node["argument1"]) * e(node["argument2"])
        if t == "min":
            return np.minimum(e(node["argument1"]), e(node["argument2"]))
        if t == "max":
            return np.maximum(e(node["argument1"]), e(node["argument2"]))
        if t == "clamp":
            return np.clip(e(node["input"]), node["min"], node["max"])
        if t == "abs":
            return np.abs(e(node["argument"]))
        if t == "square":
            v = e(node["argument"])
            return v * v
        if t == "noise":
            n = self.noise(node["noise"])
            return n.value(x * node["xz_scale"], np.zeros_like(x), z * node["xz_scale"])
        if t == "shifted_noise":
            n = self.noise(node["noise"])
            return n.value(x * node["xz_scale"] + e(node["shift_x"]), e(node["shift_y"]),
                           z * node["xz_scale"] + e(node["shift_z"]))
        if t == "spline":
            return self.spline(node["spline"], x, z, cache)
        if t == "range_choice":
            v = e(node["input"])
            inside = (v >= node["min_inclusive"]) & (v < node["max_exclusive"])
            return np.where(inside, e(node["when_in_range"]), e(node["when_out_of_range"]))
        if t == "hub_falloff":
            dx = x - node.get("center_x", 0.0)
            dz = z - node.get("center_z", 0.0)
            s = np.clip((np.sqrt(dx * dx + dz * dz) - node["inner_radius"]) / (node["outer_radius"] - node["inner_radius"]), 0, 1)
            return 1.0 - s * s * (3.0 - 2.0 * s)
        if t == "constant":
            return np.full(x.shape, float(node["argument"]))
        raise ValueError("unsupported density function " + node["type"])

    def spline(self, spl, x, z, cache):
        if isinstance(spl, (int, float)):
            return np.full(x.shape, float(spl))
        c = self.evaluate(spl["coordinate"], x, z, cache).astype(np.float32).astype(np.float64)
        pts = spl["points"]
        locs = np.array([p["location"] for p in pts], dtype=np.float64)
        ders = np.array([p["derivative"] for p in pts], dtype=np.float64)
        vals = [self.spline(p["value"], x, z, cache) for p in pts]
        out = np.empty(x.shape)
        below = c < locs[0]
        out[below] = (vals[0] + ders[0] * (c - locs[0]))[below]
        above = c >= locs[-1]
        out[above] = (vals[-1] + ders[-1] * (c - locs[-1]))[above]
        for i in range(len(pts) - 1):
            m = (c >= locs[i]) & (c < locs[i + 1])
            if not m.any():
                continue
            l0, l1 = locs[i], locs[i + 1]
            t = (c[m] - l0) / (l1 - l0)
            v0, v1 = vals[i][m], vals[i + 1][m]
            a = ders[i] * (l1 - l0) - (v1 - v0)
            b = -ders[i + 1] * (l1 - l0) + (v1 - v0)
            out[m] = v0 + t * (v1 - v0) + t * (1 - t) * (a + t * (b - a))
        return out


def biome_boxes():
    with open(os.path.join(DATA, "dross", "dimension", "dross.json"), encoding="utf-8") as f:
        dim = json.load(f)

    def q(v):
        return int(np.float32(np.float32(v) * np.float32(10000.0)))

    boxes = []
    for entry in dim["generator"]["biome_source"]["biomes"]:
        p = entry["parameters"]

        def rng(k):
            v = p[k]
            return (q(v[0]), q(v[1])) if isinstance(v, list) else (q(v), q(v))
        name = entry["biome"].split(":")[1]
        if name in BIOMES:   # (the sewers' box is underground: the sketch only looks at the surface)
            boxes.append((name, {k: rng(k) for k in ("temperature", "humidity", "continentalness")}))
    return boxes


def classify(world, x, z):
    cache = {}
    climate = {"temperature": "dross:climate/heat", "humidity": "dross:climate/rot",
               "continentalness": "dross:climate/mountain"}
    vals = {k: np.trunc(world.evaluate(f, x, z, cache).astype(np.float32) * np.float32(10000.0)).astype(np.int64)
            for k, f in climate.items()}
    out = np.full(x.shape, -1, dtype=np.int8)
    for name, box in biome_boxes():
        m = np.ones(x.shape, dtype=bool)
        for k, (lo, hi) in box.items():
            m &= (vals[k] >= lo) & (vals[k] <= hi)
        out[(out < 0) & m] = BIOMES.index(name)
    return out, cache


# ---------------------------------------------------------------------------------------------------------------------
# Stats and pictures
# ---------------------------------------------------------------------------------------------------------------------

def preview_colors():
    src = open(COLORS_JAVA, encoding="utf-8").read()
    raw = dict(re.findall(r"public\s+static\s+final\s+int\s+(\w+)\s*=\s*([^;]+);", src))

    def val(n):
        t = raw[n].strip()
        return int(t, 16) if t.startswith("0x") else val(t)
    return [val(PREVIEW_COLOR_NAMES[b]) for b in BIOMES]


def region_sizes(biome_grid, cell):
    out = {}
    for i, name in enumerate(BIOMES):
        lab, n = ndimage.label(biome_grid == i)
        if n == 0:
            out[name] = None
            continue
        sizes = ndimage.sum(np.ones_like(lab), lab, index=np.arange(1, n + 1))
        edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
        keep = np.array([s for k, s in enumerate(sizes, start=1) if k not in edge])
        if keep.size == 0:
            out[name] = None
            continue
        d = 2.0 * np.sqrt(keep * cell * cell / math.pi)
        order = np.argsort(d)
        w = keep[order] / keep.sum()
        weighted_median = d[order][np.searchsorted(np.cumsum(w), 0.5)]
        out[name] = (len(keep), float(np.median(d)), float(weighted_median), float(d.max()))
    return out


def sketch(seed, out_dir, half=4096, step=8):
    world = World(seed)
    xs = np.arange(-half, half, step, dtype=np.float64)
    x, z = np.meshgrid(xs, xs)        # rows = z, columns = x
    biomes, cache = classify(world, x, z)
    height = world.evaluate("dross:terrain/height", x, z, cache)
    rock = cache.get("dross:terrain/rock_gate")
    stats = {"seed": seed}
    # Hub
    dist = np.sqrt(x * x + z * z)
    for i, name in enumerate(BIOMES):
        m = biomes == i
        stats["nearest_" + name] = float(dist[m].min()) if m.any() else None
    stats["all_silverwood_radius"] = float(dist[biomes != 0].min()) if (biomes != 0).any() else None
    stats["shares_whole"] = {b: float((biomes == i).mean()) for i, b in enumerate(BIOMES)}
    # Heights by biome (slope from neighbouring samples, in blocks per block)
    gy, gx = np.gradient(height, step)
    slope = np.hypot(gx, gy)
    stats["height"] = {}
    for i, b in enumerate(BIOMES):
        m = (biomes == i) & (dist > 300)
        if m.sum() > 50:
            h = height[m]
            stats["height"][b] = [float(np.percentile(h, q)) for q in (5, 50, 95)] + [float(np.percentile(slope[m], 50)),
                                                                                    float(np.percentile(slope[m], 95))]
    m = biomes == BIOMES.index("gravecrags")
    lab, n = ndimage.label(m)
    peaks = ndimage.maximum(height, lab, index=np.arange(1, n + 1)) if n else []
    sizes = ndimage.sum(np.ones_like(lab), lab, index=np.arange(1, n + 1)) if n else []
    big = [float(p) for p, s in zip(peaks, sizes) if s * step * step >= MOUNTAIN_MIN_AREA]
    stats["mountain_peaks"] = sorted(big)
    stats["height_min_max"] = (float(height.min()), float(height.max()))
    # Hub ground (finer)
    hx, hz = np.meshgrid(np.arange(-45, 70, 1.0), np.arange(-50, 55, 1.0))
    hh = world.evaluate("dross:terrain/height", hx, hz, {})
    stats["hub_ground"] = (float(hh.min()), float(hh.max()), float(hh.std()))
    # Pictures
    cols = np.array([[(c >> 16) & 255, (c >> 8) & 255, c & 255] for c in preview_colors()] + [[255, 0, 255]], dtype=np.float64)
    img = cols[np.where(biomes < 0, len(BIOMES), biomes)]
    Image.fromarray(img.astype(np.uint8)).save(os.path.join(out_dir, f"sketch_biomes_{seed}.png"))
    # Relief: hillshade (light from the north-west) x biome colour, contour every 16 blocks, bright where rocky
    shade = np.clip(0.75 + (-gx + gy) * 0.35, 0.25, 1.4)
    rel = img * shade[..., None] * (0.55 + 0.45 * np.clip((height - 30) / 200, 0, 1))[..., None]
    contour = (np.floor(height / 16) != np.floor(np.roll(height, 1, 0) / 16)) | (np.floor(height / 16) != np.floor(np.roll(height, 1, 1) / 16))
    rel[contour] *= 0.6
    rim = Image.fromarray(np.clip(rel, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(rim)
    c = half // step
    for r in (200, 450, 600, 850, 1500):
        d.ellipse([c - r / step, c - r / step, c + r / step, c + r / step], outline=(127, 168, 255))
    rim.save(os.path.join(out_dir, f"sketch_relief_{seed}.png"))
    # Region sizes from a far window (16-block cells)
    fx, fz = np.meshgrid(np.arange(24000 - 4096, 24000 + 4096, 16.0), np.arange(-24000 - 4096, -24000 + 4096, 16.0))
    far, _ = classify(world, fx, fz)
    stats["shares_far"] = {b: float((far == i).mean()) for i, b in enumerate(BIOMES)}
    stats["regions_far"] = region_sizes(far, 16)
    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--seeds", type=int, default=6)
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "terrain-sketch"))
    ap.add_argument("--half", type=int, default=4096)
    ap.add_argument("--step", type=int, default=8)
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    lines = []
    all_stats = []
    for seed in range(1, args.seeds + 1):
        s = sketch(seed, args.out, args.half, args.step)
        all_stats.append(s)
        lines.append(json.dumps(s))
        print(f"seed {seed}: silverwood radius {s['all_silverwood_radius']:.0f}, nearest "
              + ", ".join(f"{b.split('_')[0]} {s['nearest_' + b]:.0f}" if s['nearest_' + b] is not None else f"{b} none"
                          for b in BIOMES[1:])
              + f"; height {s['height_min_max'][0]:.0f}..{s['height_min_max'][1]:.0f}; peaks {[round(p) for p in s['mountain_peaks']]}"
              + f"; hub ground {s['hub_ground'][0]:.1f}..{s['hub_ground'][1]:.1f} std {s['hub_ground'][2]:.2f}")
    # Averages
    peaks = sorted(p for s in all_stats for p in s["mountain_peaks"])
    if peaks:
        print(f"mountain ranges: {len(peaks)}, peak median {np.median(peaks):.0f}, p10 {np.percentile(peaks, 10):.0f}, p90 {np.percentile(peaks, 90):.0f}")
    print("far shares (mean): " + ", ".join(f"{b} {np.mean([s['shares_far'][b] for s in all_stats]):.3f}" for b in BIOMES))
    for b in BIOMES:
        vals = [s["regions_far"][b] for s in all_stats if s["regions_far"][b]]
        if vals:
            print(f"regions {b}: count {np.mean([v[0] for v in vals]):.1f}, median D {np.mean([v[1] for v in vals]):.0f}, "
                  f"area-weighted median D {np.mean([v[2] for v in vals]):.0f}, largest {np.mean([v[3] for v in vals]):.0f}")
    for b in BIOMES:
        hs = [s["height"][b] for s in all_stats if b in s["height"]]
        if hs:
            m = np.mean(hs, axis=0)
            print(f"height {b}: p5 {m[0]:.0f} p50 {m[1]:.0f} p95 {m[2]:.0f}; slope p50 {m[3]:.2f} p95 {m[4]:.2f}")
    with open(os.path.join(args.out, "sketch_stats.txt"), "w") as f:
        f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
