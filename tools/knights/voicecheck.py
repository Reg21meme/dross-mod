"""
Measures how voice-like a sound is, to keep the elemental knights' voices clear of the Rimefall folk's (the Huntsman's
and the Frostfolk's, made with tools/huntsman/voicekit.py: a man's voice). The user, November 18, 2026, after hearing
the knights' first sounds: "their sounds [are] too much like the Rimefall dudes".

For each sound (its loud frames only: within 30 dB of its loudest):
  periodicity   how strongly it repeats itself at a pitch a voice could have (60-500 Hz): the normalized
                autocorrelation's highest peak, frame by frame. A held vowel is about 0.8-0.95; noise, grit and
                inharmonic metal are low. The main "is this a voice" number.
  harmonic      how far one harmonic series stands out (dB): the best fundamental of 60-400 Hz, its first 12
                harmonics' energy against the energy half way between them, frame by frame. A voice's comb of
                harmonics stands 15-30 dB out of its gaps; noise and inharmonic ringing only a few dB.
  flatness      how noise-like its spectrum is (geometric over arithmetic mean of the power, 100-8000 Hz; 1 white noise).
  vowel band    its share of energy between 250 and 2500 Hz, where a voice's first two formants (its vowels) are.
  centroid      its brightness (Hz).
  low           its share of energy under 200 Hz (heavy against light).

For a feel of the scale: white noise measures periodicity 0.11 and harmonic 3.8 dB (the floor: the best of 90
fundamentals always finds a few dB by chance), a buzzing sawtooth 1.0 and 18.5 dB, the Huntsman's and the Frostfolk's
voices 0.93-0.97 and 18-27 dB.

    python tools/knights/voicecheck.py            the table for the knights' voices and the Rimefall voices

sounds.py imports `measure_voice` for its preview page.
"""
import math
import os
import sys

import numpy as np

SR = 44100


def _frames(x, size, hop):
    n = 1 + max(0, (len(x) - size) // hop)
    idx = np.arange(size)[None, :] + hop * np.arange(n)[:, None]
    return x[idx]


def measure_voice(x, sr=SR):
    """The numbers above for one sound (mono float array)."""
    x = np.asarray(x, dtype=np.float64)
    if x.ndim > 1:
        x = x.mean(axis=1)
    size, hop = int(0.04 * sr), int(0.02 * sr)
    if len(x) < size:
        x = np.concatenate([x, np.zeros(size - len(x))])
    fr = _frames(x, size, hop)
    energy = np.sum(fr ** 2, axis=1)
    loud = energy > np.max(energy) * 10 ** (-30 / 10)
    fr, energy = fr[loud], energy[loud]
    fr = fr - fr.mean(axis=1, keepdims=True)
    win = np.hanning(size)
    nfft = 4096
    spec = np.abs(np.fft.rfft(fr * win, nfft, axis=1)) ** 2
    # periodicity: the autocorrelation's highest peak past its first zero (so a smooth, low rumble, which correlates
    # with itself at short lags without repeating, doesn't count), at a lag a voice's pitch could have
    ac = np.fft.irfft(spec, axis=1)[:, :size]
    taper = np.correlate(win, win, "full")[size - 1:] / np.sum(win ** 2)
    lo_lag, hi_lag = int(sr / 500), int(sr / 60)
    per = []
    for a in ac:
        r = a / (a[0] + 1e-12) / np.maximum(taper, 0.2)
        neg = np.nonzero(r[1:] < 0)[0]
        start = max(lo_lag, (neg[0] + 1) if len(neg) else hi_lag)
        per.append(float(np.clip(np.max(r[start:hi_lag]), 0.0, 1.0)) if start < hi_lag else 0.0)
    # harmonic contrast: for the best fundamental (60-400 Hz), its first 12 harmonics' energy against the energy half
    # way between them (dB): a voice's comb of harmonics stands far out of the gaps; noise and inharmonic metal don't
    df = sr / nfft
    f0s = np.exp(np.linspace(math.log(60), math.log(400), 90))
    ks = np.arange(1, 13)
    peaks = np.round(np.outer(f0s, ks) / df).astype(int)
    below = np.round(np.outer(f0s, ks - 0.5) / df).astype(int)
    above = np.round(np.outer(f0s, ks + 0.5) / df).astype(int)
    off = np.array([-1, 0, 1])
    top = spec.shape[1] - 1
    pk = spec[:, (peaks[:, :, None] + off).clip(0, top)].sum(axis=(2, 3))
    # the gaps either side of each harmonic, so a spectrum falling steeply (a low rumble) doesn't count as a comb
    tr = 0.5 * (spec[:, (below[:, :, None] + off).clip(0, top)].sum(axis=(2, 3))
                + spec[:, (above[:, :, None] + off).clip(0, top)].sum(axis=(2, 3)))
    harm = np.max(10 * np.log10((pk + 1e-20) / (tr + 1e-20)), axis=1)
    freqs = np.fft.rfftfreq(nfft, 1.0 / sr)
    band = (freqs >= 100) & (freqs <= 8000)
    s = spec[:, band] + 1e-20
    flat = np.exp(np.mean(np.log(s), axis=1)) / np.mean(s, axis=1)
    wts = energy / np.sum(energy)
    whole = np.abs(np.fft.rfft(x)) ** 2
    fq = np.fft.rfftfreq(len(x), 1.0 / sr)
    total = np.sum(whole) + 1e-12
    return {
        "periodicity": float(np.sum(wts * np.array(per))),
        "harmonic": float(np.sum(wts * harm)),
        "flatness": float(np.sum(wts * flat)),
        "vowel": float(np.sum(whole[(fq >= 250) & (fq <= 2500)]) / total),
        "centroid": float(np.sum(fq * whole) / total),
        "low": float(np.sum(whole[fq < 200]) / total),
    }


ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SOUNDS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross", "sounds")
# The Rimefall folk's voices (voicekit): what the knights must not sound like.
RIMEFALL = {
    "huntsman": ("ambient_1", "ambient_2", "effort_1", "effort_2", "hurt_1", "hurt_2", "hurt_3", "death_1", "death_2",
                 "cry_1"),
    "frostfolk": ("ambient_1", "ambient_2", "hurt_1", "hurt_2", "death_1", "talk_1", "cry_1"),
    "frostfolk_elder": ("ambient_1", "hurt_1", "death_1", "talk_1"),
}
KNIGHT_VOICE = ("ambient_1", "ambient_2", "effort_1", "effort_2", "hurt_1", "hurt_2", "death")
KEYS = ("periodicity", "harmonic", "flatness", "vowel", "centroid", "low")


def load(path):
    import soundfile
    x, sr = soundfile.read(path)
    assert sr == SR
    return x


def mean_of(ms):
    return {k: float(np.mean([m[k] for m in ms])) for k in KEYS}


def rimefall_measures():
    """Each Rimefall folder's voices, averaged."""
    return {folder: mean_of([measure_voice(load(os.path.join(SOUNDS, folder, n + ".ogg"))) for n in names])
            for folder, names in RIMEFALL.items()}


def knight_measures(keys):
    return {k: mean_of([measure_voice(load(os.path.join(SOUNDS, "knight", k, n + ".ogg"))) for n in KNIGHT_VOICE])
            for k in keys}


HEADER = f"{'':<18}{'period.':>8}{'harmonic':>11}{'flatness':>9}{'vowel':>9}{'centroid':>9}{'<200':>8}"


def row(name, m):
    return (f"{name:<18}{m['periodicity']:>8.2f}{m['harmonic']:>8.1f} dB{m['flatness']:>9.3f}{m['vowel'] * 100:>8.0f}%"
            f"{m['centroid']:>9.0f}{m['low'] * 100:>7.0f}%")


def main():
    keys = sorted(os.listdir(os.path.join(SOUNDS, "knight")))
    print(HEADER)
    for f, m in rimefall_measures().items():
        print(row(f, m))
    km = knight_measures(keys)
    for k, m in km.items():
        print(row(k, m))
    print(row("knights (mean)", mean_of(list(km.values()))))
    if "--each" in sys.argv:
        for k in keys:
            for n in KNIGHT_VOICE:
                print(row(f"{k}/{n}"[:18], measure_voice(load(os.path.join(SOUNDS, "knight", k, n + ".ogg")))))


if __name__ == "__main__":
    main()
