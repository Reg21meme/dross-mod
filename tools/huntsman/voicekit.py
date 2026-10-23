"""
A voice synthesizer for people (the Huntsman; later his villagers) and for howling animals, built to sound more alive
than the Rat King's synthesizer (whose sawtooth through fixed resonators sounded, the user found, "like a moose"):

- the source is the vocal folds' own puffs of air, made period by period (Rosenberg pulses: a rise, a quick fall, a
  closed rest), each period a little off in length and loudness (jitter, shimmer), with breath in each puff;
- the mouth is a cascade of five formant resonators (Klatt's way) whose frequencies glide from vowel to vowel as the
  sound goes on, set for a big man's vowels (Peterson and Barney's men, a little lowered);
- what makes a voice someone's: an "h" breathed through the coming vowel, a glottal stop, creak (vocal fry) as a voice
  dies, rasp (every other puff weaker: a yell's roughness), a hum's closed mouth.

Every parameter that changes over a sound takes (seconds, value) points; a plain number stays put.
"""
import math

import numpy as np
from scipy import signal

SR = 44100

# Formants (Hz) and their bandwidths (Hz): F1-F5. Men's averages (Peterson & Barney 1952; F4/F5 typical).
VOWELS = {
    "ah": ((730, 1090, 2440, 3300, 3750), (90, 110, 150, 220, 260)),
    "aw": ((570, 840, 2410, 3300, 3750), (80, 100, 150, 220, 260)),
    "uh": ((640, 1190, 2390, 3300, 3750), (80, 110, 150, 220, 260)),
    "oh": ((480, 800, 2400, 3300, 3750), (70, 90, 150, 220, 260)),
    "oo": ((300, 870, 2240, 3300, 3750), (60, 90, 140, 220, 260)),
    "eh": ((530, 1840, 2480, 3300, 3750), (70, 110, 150, 220, 260)),
    "ae": ((660, 1720, 2410, 3300, 3750), (80, 120, 150, 220, 260)),
    "ee": ((270, 2290, 3010, 3500, 3900), (60, 120, 170, 220, 260)),
    "ih": ((390, 1990, 2550, 3300, 3750), (70, 120, 160, 220, 260)),
    "mm": ((250, 1000, 2200, 3300, 3750), (60, 300, 400, 500, 500)),    # a hum: the mouth shut
    "ng": ((270, 1500, 2500, 3300, 3750), (70, 250, 350, 450, 500)),    # the back of the tongue up: "nngh"
}

# A big man's mouth is a little longer than the average man's: every formant this much lower.
BIG_MAN = 0.94


def n_of(seconds):
    return max(1, int(round(seconds * SR)))


def track(value, n, log=False):
    """A per-sample curve from a number or (seconds, value) points (straight lines; log: straight in log space)."""
    if not isinstance(value, (list, tuple)):
        return np.full(n, float(value))
    ts = np.array([p[0] for p in value], dtype=float) * SR
    vs = np.array([p[1] for p in value], dtype=float)
    x = np.arange(n)
    if log:
        return np.exp(np.interp(x, ts, np.log(np.maximum(vs, 1e-6))))
    return np.interp(x, ts, vs)


def envelope(points, n, ms=6.0):
    """An amplitude curve through (seconds, level) points, lightly smoothed so it never clicks."""
    e = track(points, n)
    k = max(1, int(SR * ms / 1000.0))
    return np.convolve(e, np.ones(k) / k, mode="same")


# ---------------------------------------------------------------- the source


def folds(dur, f0, rng, oq=0.6, rise=0.7, jitter=0.012, shimmer=0.05, rasp=0.0, drift=0.015, vib=(0.0, 0.0)):
    """
    The vocal folds: one Rosenberg pulse of air per period. f0 the pitch (Hz), oq how much of each period they're open
    (0.35 pressed and bright, a shout; 0.6 a normal voice; 0.8 soft and breathy), rise how much of the open part is the
    rise, jitter and shimmer each period's random wobble in length and loudness, rasp how much weaker every other puff
    is (a yell's roughness; creak uses a lot), drift a slow wander in pitch, vib a vibrato (Hz, depth).
    Returns the glottal flow (its puffs) and its derivative (what the lips radiate).
    """
    n = n_of(dur)
    f0t = track(f0, n, log=True)
    oqt, riset, jt, st, rt = (track(v, n) for v in (oq, rise, jitter, shimmer, rasp))
    wander = signal.lfilter([1.0], [1.0, -0.9995], rng.standard_normal(n))
    wander = wander / (np.max(np.abs(wander)) + 1e-9) * drift
    t_axis = np.arange(n) / SR
    vrate, vdepth = vib
    vdepth_t = track(vdepth, n)
    f0t = f0t * (1.0 + wander) * (1.0 + vdepth_t * np.sin(2 * math.pi * vrate * t_axis))
    flow = np.zeros(n + 4096)
    t, k = 0.0, 0
    while t < n:
        i = int(t)
        period = SR / f0t[i] * (1.0 + jt[i] * rng.standard_normal())
        period = max(period, 12.0)
        amp = max(0.0, 1.0 + st[i] * rng.standard_normal())
        if k % 2 == 1:
            amp *= 1.0 - rt[i]
        te = period * oqt[i]
        tp = te * riset[i]
        tn = max(te - tp, 1.0)
        length = int(period) + 1
        s = np.arange(length, dtype=float) - (t - i)
        g = np.zeros(length)
        up = (s >= 0) & (s < tp)
        g[up] = 0.5 * (1.0 - np.cos(math.pi * s[up] / tp))
        down = (s >= tp) & (s < te)
        g[down] = np.cos(0.5 * math.pi * (s[down] - tp) / tn)
        flow[i:i + length] += amp * g
        t += period
        k += 1
    flow = flow[:n]
    lips = np.diff(flow, prepend=0.0)
    return flow, lips


def breath_noise(n, rng, lo=400.0):
    """Turbulence: white noise above `lo` (the breath, the "h", the hiss)."""
    b, a = signal.butter(2, lo / (SR / 2), btype="high")
    return signal.lfilter(b, a, rng.standard_normal(n))


# ---------------------------------------------------------------- the mouth


def mouth(x, vowels, scale=BIG_MAN, block=64, wobble=0.0, rng=None):
    """
    Five formant resonators in a cascade (each with unity gain at 0 Hz, so their peaks come out as a real voice's do),
    their frequencies gliding through `vowels` ((seconds, vowel) points), recomputed every `block` samples. wobble: a
    slow random drift of every formant (a living mouth never holds still).
    """
    n = len(x)
    if isinstance(vowels, str):
        vowels = [(0.0, vowels)]
    starts = np.arange(0, n, block)
    centres = (starts + block / 2.0) / SR
    times = [p[0] for p in vowels]
    out = x.astype(float)
    for k in range(5):
        fs = np.array([VOWELS[v][0][k] for _t, v in vowels], dtype=float) * scale
        bs = np.array([VOWELS[v][1][k] for _t, v in vowels], dtype=float)
        F = np.interp(centres, times, fs)
        B = np.interp(centres, times, bs)
        if wobble > 0 and rng is not None and k < 3:
            w = signal.lfilter([1.0], [1.0, -0.98], rng.standard_normal(len(starts)))
            F = F * (1.0 + wobble * w / (np.max(np.abs(w)) + 1e-9))
        y = np.zeros(n)
        zi = np.zeros(2)
        for j, s0 in enumerate(starts):
            f = min(F[j], 0.45 * SR)
            r = math.exp(-math.pi * B[j] / SR)
            c = 2.0 * r * math.cos(2.0 * math.pi * f / SR)
            a = [1.0, -c, r * r]
            gain = 1.0 - c + r * r
            seg, zi = signal.lfilter([gain], a, out[s0:s0 + block], zi=zi)
            y[s0:s0 + block] = seg
        out = y
    return out


# ---------------------------------------------------------------- a voice


def voice(dur, f0, vowels, rng, amp=None, oq=0.6, rise=0.7, jitter=0.012, shimmer=0.05, rasp=0.0, breath=0.08,
          puff_breath=0.25, drift=0.015, vib=(0.0, 0.0), scale=BIG_MAN, wobble=0.03, hum=False, hum_open=0.0):
    """
    A voiced sound: the folds' puffs (see folds) through the mouth (see mouth). breath: steady breath noise; puff_breath:
    breath in each puff (a voice's natural airiness); amp: the loudness over time ((s, level) points). hum: the mouth
    closed (most of the sound out through the nose: darker); hum_open: how much of the brighter sound is kept in a hum.
    """
    n = n_of(dur)
    flow, lips = folds(dur, f0, rng, oq, rise, jitter, shimmer, rasp, drift, vib)
    lips = lips / (np.max(np.abs(lips)) + 1e-9)
    noise = breath_noise(n, rng)
    noise = noise / (np.max(np.abs(noise)) + 1e-9)
    puffed = noise * (flow / (np.max(flow) + 1e-9))
    src = lips + track(puff_breath, n) * puffed * 0.5 + track(breath, n) * noise * 0.5
    out = mouth(src, vowels, scale, wobble=wobble, rng=rng)
    if hum:
        b, a = signal.butter(2, 900.0 / (SR / 2))
        out = signal.lfilter(b, a, out) * 2.0 * (1.0 - hum_open) + out * hum_open
    out = out / (np.sqrt(np.mean(out ** 2)) + 1e-9)
    if amp is not None:
        out = out * envelope(amp, n)
    return out


def aspirate(dur, vowel, rng, amp=None, scale=BIG_MAN, lo=500.0):
    """An "h" (or a breath, a sigh): breath only, shaped by the mouth of `vowel` (a vowel, or (s, vowel) points)."""
    n = n_of(dur)
    out = mouth(breath_noise(n, rng, lo), vowel, scale)
    out = out / (np.max(np.abs(out)) + 1e-9)
    return out * envelope(amp or [(0.0, 0.0), (dur * 0.5, 1.0), (dur, 0.0)], n, ms=4.0)


def gasp(dur, rng, level=1.0, vowel="ah"):
    """A breath drawn in hard: breath rising in loudness and pitch through an open mouth (an in-breath's hiss is higher
    than an out-breath's)."""
    n = n_of(dur)
    noise = breath_noise(n, rng, 900.0)
    out = mouth(noise, [(0.0, vowel), (dur, "eh")], 1.05)
    out = out / (np.max(np.abs(out)) + 1e-9)
    return out * envelope([(0.0, 0.0), (dur * 0.75, 1.0), (dur, 0.0)], n, ms=4.0) * level


def creak(dur, vowel, rng, amp=None, f0=(45.0, 38.0)):
    """Vocal fry: the voice run down to slow, uneven pops (a dying voice, a groan's end)."""
    return voice(dur, [(0.0, f0[0]), (dur, f0[1])], vowel, rng, amp=amp, oq=0.3, rise=0.8, jitter=0.18, shimmer=0.3,
                 rasp=0.5, breath=0.02, puff_breath=0.1, drift=0.0)


def place(dur, *parts):
    """Mixes (offset seconds, sound) pieces into one buffer `dur` long (longer if a piece runs past it)."""
    n = n_of(dur)
    end = max([n] + [n_of(o) + len(s) for o, s in parts])
    out = np.zeros(end)
    for o, s in parts:
        i = n_of(o) if o > 0 else 0
        out[i:i + len(s)] += s
    return out


# ---------------------------------------------------------------- a howl


def howl(dur, f0, vowels, rng, amp=None, vib=(5.0, 0.008), purity=0.6, scale=1.0, breath=0.04, bright=1800.0):
    """
    A wolf's howl: a smooth, soft voice (folds open most of each period, hardly any jitter), its pitch gliding, a slow
    vibrato, the mouth opening and closing ("oo-ah-oo"), its overtones rolled off above `bright` Hz; `purity` of a
    plain sine on its pitch mixed in (a howl is close to a pure tone: its first pictures had overtones up to 4 kHz).
    """
    n = n_of(dur)
    v = voice(dur, f0, vowels, rng, amp=None, oq=0.78, rise=0.6, jitter=0.003, shimmer=0.02, breath=breath,
              puff_breath=0.12, drift=0.0, vib=vib, scale=scale, wobble=0.02)   # (no drift: it stays on the sine)
    b, a = signal.butter(2, bright / (SR / 2))
    v = signal.lfilter(b, a, v)
    v = v / (np.sqrt(np.mean(v ** 2)) + 1e-9)
    f0t = track(f0, n, log=True)
    t = np.arange(n) / SR
    f0t = f0t * (1.0 + track(vib[1], n) * np.sin(2 * math.pi * vib[0] * t))
    phase = 2 * math.pi * np.cumsum(f0t) / SR
    pure = np.sin(phase) + 0.25 * np.sin(2 * phase)
    pure = pure / (np.sqrt(np.mean(pure ** 2)) + 1e-9)
    out = (1.0 - purity) * v + purity * pure
    if amp is not None:
        out = out * envelope(amp, n, ms=12.0)
    return out
