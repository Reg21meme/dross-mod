"""
A small sound synthesizer for the Rat King's and the rats' sounds: every sound is made from scratch out of tones and
noise (nothing is recorded, nothing comes from Minecraft). Mono, 44.1 kHz, floats in -1..1.

The building blocks:
  tone / saw        oscillators following a pitch curve (Hz per sample), with harmonics
  noise             white, pink or brown noise
  bandpass ...      filters (scipy)
  voice             a little vocal tract: a buzzing source shaped into vowels by formant filters, with breath and growl
  chirp             a rat's squeak: a quick tonal sweep with a couple of harmonics
  knock / thud / clink / whoosh / crack   percussive pieces
  whistle           a steam train's chime whistle: pipes in a chord, a scoop up at the start, steam hiss
  reverb            a small Schroeder reverb (rooms, echoes)
"""
import math

import numpy as np
from scipy import signal

SR = 44100


def n_of(seconds):
    return max(1, int(round(seconds * SR)))


def silence(seconds):
    return np.zeros(n_of(seconds))


def curve(points, n, log=False):
    """A curve through (seconds, value) points, one value per sample (straight lines, or straight in log space)."""
    ts = np.array([p[0] for p in points], dtype=float) * SR
    vs = np.array([p[1] for p in points], dtype=float)
    x = np.arange(n)
    if log:
        return np.exp(np.interp(x, ts, np.log(np.maximum(vs, 1e-6))))
    return np.interp(x, ts, vs)


def smooth_env(points, n, ms=4.0):
    """An amplitude envelope through (seconds, level) points, lightly smoothed so it never clicks."""
    e = curve(points, n)
    k = max(1, int(SR * ms / 1000.0))
    if k > 1:
        e = np.convolve(e, np.ones(k) / k, mode="same")
    return e


def tone(freq, harmonics=((1, 1.0),), phase=0.0):
    """Sines on a pitch curve: harmonics as (multiple, amplitude); any above 0.45 x the sample rate is left out."""
    freq = np.asarray(freq, dtype=float)
    ph = 2.0 * math.pi * np.cumsum(freq) / SR + phase
    out = np.zeros_like(freq)
    for k, a in harmonics:
        out += a * np.sin(k * ph) * (k * freq < 0.45 * SR)
    return out


def saw(freq, tilt=1.0, nmax=48):
    """A buzzing, band-limited sawtooth-like source (harmonic k at 1 / k^tilt): the voice's raw sound."""
    return tone(freq, [(k, 1.0 / k ** tilt) for k in range(1, nmax + 1)])


def noise(n, rng, color="white"):
    w = rng.standard_normal(n)
    if color == "white":
        return w / 3.0
    if color == "pink":
        b, a = [0.049922035, -0.095993537, 0.050612699, -0.004408786], [1, -2.494956002, 2.017265875, -0.522189400]
        x = signal.lfilter(b, a, w)
        return x / (np.std(x) * 3.0 + 1e-9)
    if color == "brown":
        x = np.cumsum(w)
        x = highpass(x, 25.0)
        return x / (np.std(x) * 3.0 + 1e-9)
    raise ValueError(color)


def _sos(kind, f, order=2):
    return signal.butter(order, f, kind, fs=SR, output="sos")


def bandpass(x, lo, hi, order=2):
    lo = max(10.0, lo)
    hi = min(0.48 * SR, hi)
    return signal.sosfilt(_sos("bandpass", [lo, hi], order), x)


def lowpass(x, f, order=2):
    return signal.sosfilt(_sos("lowpass", min(f, 0.48 * SR), order), x)


def highpass(x, f, order=2):
    return signal.sosfilt(_sos("highpass", max(f, 10.0), order), x)


def resonator(x, f, q):
    """A ringing band (a peak filter): what makes a knock sound like wood or a pipe sound like a pipe."""
    b, a = signal.iirpeak(min(f, 0.45 * SR), q, fs=SR)
    return signal.lfilter(b, a, x)


def sweep_filter(x, centres, width=0.5, bands=None):
    """A band of noise (or anything) whose centre follows a curve (Hz per sample): crossfades fixed bands."""
    bands = bands or [150, 250, 400, 650, 1000, 1600, 2500, 4000, 6300, 10000]
    lc = np.log(np.maximum(centres, 20.0))
    out = np.zeros_like(x)
    lb = np.log(np.array(bands, dtype=float))
    for i, fb in enumerate(bands):
        filt = bandpass(x, fb * (1.0 - width * 0.6), fb * (1.0 + width))
        # Weight: a triangle round this band in log frequency.
        step = (lb[1] - lb[0]) if i == 0 else (lb[i] - lb[i - 1])
        w = np.clip(1.0 - np.abs(lc - lb[i]) / step, 0.0, 1.0)
        out += filt * w
    return out


# ---------------------------------------------------------------- voices


VOWELS = {
    # formant (Hz, bandwidth Hz, gain dB) for an adult human; scaled up for smaller creatures
    "ee": ((270, 60, 0), (2290, 100, -10), (3010, 120, -16)),
    "ih": ((390, 70, 0), (1990, 100, -9), (2550, 120, -15)),
    "eh": ((530, 70, 0), (1840, 100, -7), (2480, 120, -14)),
    "ah": ((730, 90, 0), (1090, 100, -4), (2440, 120, -13)),
    "aw": ((570, 80, 0), (840, 90, -5), (2410, 120, -17)),
    "oo": ((300, 60, 0), (870, 80, -10), (2240, 120, -20)),
    "uh": ((640, 80, 0), (1190, 100, -6), (2390, 120, -15)),
    "mm": ((250, 60, 0), (1000, 200, -22), (2200, 200, -28)),
}


def formant_filter(src, vowel, scale=1.0):
    out = np.zeros_like(src)
    for f, bw, g in VOWELS[vowel]:
        fc = f * scale
        q = fc / (bw * scale)
        out += resonator(src, fc, q) * 10 ** (g / 20.0)
    return out


def voice(dur, f0_pts, vowel_pts, rng, scale=1.35, breath=0.15, rough=0.0, rough_hz=45.0, jitter=0.012, tilt=1.0,
          amp_pts=None, vib_rate=0.0, vib_pts=None, direct=0.0):
    """
    A creature's voice. f0_pts: (s, Hz) pitch; vowel_pts: (s, vowel) the mouth shape (crossfading between them);
    scale: how much smaller than a person (formants move up); breath: hiss; rough: a growl (fast wobble in loudness);
    vib_rate / vib_pts: a vibrato (Hz) and how deep it is over time ((s, depth), 0.02 = 2 %); direct: how much of the
    raw source to mix in (high squeals: their pitch is above the mouth's resonances, so filtering alone would mute them).
    """
    n = n_of(dur)
    f0 = curve(f0_pts, n, log=True)
    wob = lowpass(rng.standard_normal(n), 12.0)
    wob /= (np.std(wob) + 1e-9)
    f0 = f0 * (1.0 + jitter * wob)
    if vib_rate > 0 and vib_pts:
        t = np.arange(n) / SR
        f0 = f0 * (1.0 + curve(vib_pts, n) * np.sin(2 * math.pi * vib_rate * t))
    src = saw(f0, tilt)
    src /= np.max(np.abs(src)) + 1e-9
    if rough > 0:
        am = lowpass(rng.standard_normal(n), rough_hz)
        am /= (np.max(np.abs(am)) + 1e-9)
        src *= 1.0 + rough * am
    br = highpass(noise(n, rng), 600.0)
    src = src + breath * br * 3.0
    names = []
    for _t, v in vowel_pts:
        if v not in names:
            names.append(v)
    filtered = {v: formant_filter(src, v, scale) for v in names}
    out = np.zeros(n)
    for v in names:
        w = curve([(t, 1.0 if vv == v else 0.0) for t, vv in vowel_pts], n)
        out += filtered[v] * w
    out /= np.sqrt(np.mean(out ** 2)) + 1e-9
    if direct > 0:
        d = lowpass(src, 7000.0)
        out += direct * d / (np.sqrt(np.mean(d ** 2)) + 1e-9)
    if amp_pts is not None:
        out *= smooth_env(amp_pts, n)
    return out


def chirp(dur, f_pts, rng, harmonics=((1, 1.0), (2, 0.3), (3, 0.1)), amp_pts=None, vib=(0.0, 0.0), noise_mix=0.04):
    """A squeak: a quick tonal sweep (f_pts: (s, Hz)), a little vibrato, a breath of noise."""
    n = n_of(dur)
    f = curve(f_pts, n, log=True)
    if vib[1] > 0:
        t = np.arange(n) / SR
        f = f * (1.0 + vib[1] * np.sin(2 * math.pi * vib[0] * t))
    out = tone(f, harmonics)
    out += noise_mix * bandpass(noise(n, rng), 2000.0, 9000.0) * 3.0
    amp = smooth_env(amp_pts or [(0.0, 0.0), (0.01, 1.0), (dur * 0.6, 0.8), (dur, 0.0)], n, ms=2.0)
    return out * amp


# ---------------------------------------------------------------- percussive pieces


def burst(seconds, rng, decay=0.004):
    n = n_of(seconds)
    t = np.arange(n) / SR
    return noise(n, rng) * np.exp(-t / decay)


def knock(rng, freqs=(280, 760, 1650), qs=(9, 12, 14), gains=(1.0, 0.6, 0.35), dur=0.35, decay=0.07):
    """A knock on wood (or a staff on stone): a sharp tap ringing at a few frequencies."""
    exc = burst(dur, rng, 0.0025)
    out = sum(resonator(exc, f, q) * g for f, q, g in zip(freqs, qs, gains))
    t = np.arange(len(out)) / SR
    return out * np.exp(-t / decay)


def thud(rng, f0=70.0, f1=42.0, dur=0.35, click=0.3):
    """Something heavy hitting the ground: a falling low tone and a dull knock of noise."""
    n = n_of(dur)
    t = np.arange(n) / SR
    f = f1 + (f0 - f1) * np.exp(-t / 0.05)
    body = tone(f, ((1, 1.0), (2, 0.25))) * np.exp(-t / (dur * 0.35))
    dull = lowpass(noise(n, rng, "brown"), 300.0) * np.exp(-t / 0.06) * 1.5
    tick = highpass(burst(dur, rng, 0.002), 1500.0) * click
    return body + dull + tick


def clink(rng, base=2100.0, ratios=(1.0, 1.58, 2.33, 3.11), dur=0.6, decay=0.12):
    """Metal (a gold crown landing): a few bright partials that aren't in tune with each other, dying away."""
    n = n_of(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for i, r in enumerate(ratios):
        out += np.sin(2 * math.pi * base * r * t + rng.random() * 6.28) * np.exp(-t / (decay / (1 + 0.5 * i))) / (1 + i)
    out += highpass(burst(dur, rng, 0.0015), 3000.0) * 0.4
    return out


def whoosh(rng, dur, f_from, f_to, amp_pts=None, color="pink", width=0.6):
    """Air rushing past: noise whose pitch sweeps from f_from to f_to."""
    n = n_of(dur)
    c = curve([(0.0, f_from), (dur, f_to)], n, log=True)
    out = sweep_filter(noise(n, rng, color), c, width)
    amp = smooth_env(amp_pts or [(0.0, 0.0), (dur * 0.45, 1.0), (dur, 0.0)], n, ms=6.0)
    return out * amp


def crack(rng, dur=0.12):
    """A whip's crack: a sharp snap of bright noise."""
    n = n_of(dur)
    t = np.arange(n) / SR
    snap = highpass(noise(n, rng), 1800.0) * np.exp(-t / 0.008)
    pop = np.sin(2 * math.pi * 900.0 * t) * np.exp(-t / 0.004)
    return snap * 1.4 + pop * 0.6


def grains(rng, dur, rate, grain=(0.004, 0.02), lo=400.0, hi=3000.0, level=1.0, jitter=1.0):
    """Lots of tiny noise grains (dirt, gravel, scratching): `rate` grains a second at random times."""
    n = n_of(dur)
    out = np.zeros(n)
    count = int(rate * dur)
    for _ in range(count):
        g = rng.uniform(*grain)
        gn = n_of(g)
        start = int(rng.uniform(0, max(1, n - gn)))
        f = math.exp(rng.uniform(math.log(lo), math.log(hi)))
        piece = bandpass(noise(gn, rng), f * 0.7, f * 1.4) * np.hanning(gn) * rng.uniform(0.3, 1.0) ** jitter
        out[start:start + gn] += piece
    return out * level


def bubbles(rng, dur, rate, lo=180.0, hi=700.0):
    """Bubbles popping (sickly, gurgling): little rising blips."""
    n = n_of(dur)
    out = np.zeros(n)
    for _ in range(int(rate * dur)):
        bd = rng.uniform(0.02, 0.06)
        bn = n_of(bd)
        start = int(rng.uniform(0, max(1, n - bn)))
        f0 = rng.uniform(lo, hi)
        t = np.arange(bn) / SR
        f = f0 * (1.0 + 1.6 * t / bd)
        out[start:start + bn] += np.sin(2 * math.pi * np.cumsum(f) / SR) * np.exp(-t / (bd * 0.4)) * rng.uniform(0.3, 1.0)
    return out


def whistle(rng, dur, chord, blasts, scoop=0.07, droop=0.035, steam=0.3, detune=0.0025, vib=(5.5, 0.0025),
            harmonics=((1, 1.0), (2, 0.18), (3, 0.32), (4, 0.06), (5, 0.12)), breath=0.12):
    """
    A steam whistle (a train's chime whistle): pipes sounding a chord together. Each blast (start, length) scoops up
    into pitch as the steam hits, wavers a little, and droops as the valve closes; steam hisses at the start and end.
    """
    n = n_of(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    gate = np.zeros(n)
    pitch = np.ones(n)
    hiss_env = np.zeros(n)
    for start, length in blasts:
        s, e = n_of(start), min(n, n_of(start + length))
        tt = (np.arange(e - s)) / SR
        a = np.clip(tt / 0.035, 0, 1) * np.clip((length - tt) / 0.11, 0, 1)
        gate[s:e] = np.maximum(gate[s:e], a)
        p = 1.0 - scoop * np.exp(-tt / 0.07) - droop * np.clip((tt - (length - 0.18)) / 0.18, 0, 1) ** 2
        pitch[s:e] = p
        h = np.exp(-tt / 0.05) + 0.6 * np.clip((tt - (length - 0.12)) / 0.12, 0, 1) * np.exp(-np.clip(tt - length, 0, None) / 0.08)
        hiss_env[s:e] = np.maximum(hiss_env[s:e], h)
        # The hiss trails off after the valve shuts.
        tail = min(n, e + n_of(0.35))
        tt2 = np.arange(tail - e) / SR
        hiss_env[e:tail] = np.maximum(hiss_env[e:tail], 0.45 * np.exp(-tt2 / 0.12))
    gate = np.convolve(gate, np.ones(64) / 64, mode="same")
    vibr = 1.0 + vib[1] * np.sin(2 * math.pi * vib[0] * t + rng.random() * 6.0)
    for i, f in enumerate(chord):
        d = 1.0 + detune * (i - (len(chord) - 1) / 2.0)
        fr = f * d * pitch * vibr
        pipe = tone(fr, harmonics, phase=rng.random() * 6.28)
        # Steam rushing through the pipe: noise tuned round its pitch.
        air = bandpass(noise(n, rng), f * 0.92, f * 1.08) * 4.0
        out += (pipe + breath * air) / len(chord) ** 0.6
    out *= gate
    hiss = highpass(noise(n, rng, "white"), 2500.0) * steam * hiss_env * 2.0
    return out + hiss


# ---------------------------------------------------------------- finishing


def reverb(x, mix=0.2, room=0.78, damp=4500.0):
    """A small Schroeder reverb: four combs and two all-passes. Pads the end so the tail isn't cut off."""
    pad = np.concatenate([x, np.zeros(n_of(0.6))])
    wet = np.zeros_like(pad)
    for d in (1557, 1617, 1491, 1422):
        a = np.zeros(d + 1)
        a[0], a[d] = 1.0, -room
        wet += signal.lfilter([1.0], a, pad)
    wet = lowpass(wet / 4.0, damp)
    for d in (225, 556):
        b = np.zeros(d + 1)
        a = np.zeros(d + 1)
        b[0], b[d] = -0.5, 1.0
        a[0], a[d] = 1.0, -0.5
        wet = signal.lfilter(b, a, wet)
    return pad * (1.0 - mix) + wet * mix


def place(dur, *parts):
    """Mixes (offset seconds, sound) pieces into one buffer `dur` long (longer if a piece runs past it)."""
    n = n_of(dur)
    end = max([n] + [n_of(o) + len(s) for o, s in parts])
    out = np.zeros(end)
    for o, s in parts:
        i = n_of(o)
        out[i:i + len(s)] += s
    return out


def finish(x, peak=0.89, fade_in=0.002, fade_out=0.02, trim=True):
    """Removes any DC, trims trailing silence, fades the ends and scales the loudest point to `peak`."""
    x = x - np.mean(x)
    x = highpass(x, 30.0)
    if trim:
        loud = np.where(np.abs(x) > 1e-3 * (np.max(np.abs(x)) + 1e-12))[0]
        if len(loud):
            x = x[:min(len(x), loud[-1] + n_of(0.02))]
    n = len(x)
    fi, fo = min(n // 4, n_of(fade_in)), min(n // 3, n_of(fade_out))
    if fi > 0:
        x[:fi] *= np.linspace(0.0, 1.0, fi)
    if fo > 0:
        x[-fo:] *= np.linspace(1.0, 0.0, fo)
    m = np.max(np.abs(x))
    if m > 0:
        x = x * (peak / m)
    return x
