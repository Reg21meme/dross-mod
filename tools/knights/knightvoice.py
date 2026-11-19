"""
The elemental knights' voices: what an ancient knight sealed in his armour sounds like when he breathes, strains, is
hurt and dies (tools/knights/sounds.py uses it for every knight's idle, grunt, hurt and death).

The user, November 18, 2026, after hearing the knights' first voices (made with the Huntsman's voicekit, a man's
voice): "their sounds [are] too much like the Rimefall dudes, especially use original audios." So nothing here is a
voice in the human sense: no vocal folds puffing at a pitch, no mouth shaping vowels. Each element makes its sound out
of its own stuff, every piece synthesized from scratch (noise, impulses, resonances; nothing recorded, nothing from
Minecraft), and every voice rings in the knight's sealed helm (a bell's inharmonic partials, not a voice's harmonics):

  ice      a frozen corpse: breath rattling through ice, the ice on him groaning and creaking, glassy whistles through
           its cracks, frost crackling, a long cold exhale
  poison   rot and fungus: gurgling and bubbling, a wheeze whistling through clogged lungs, wet clicks, a choked hiss
  fire     a furnace: roaring bellows breath, a fluttering growl of flame, crackle and hiss
  thunder  a broken storm: the Wreck Knight a ship's hull groaning under a drowned electric hum through a broken radio;
           the Stormblade a quick electric snarl of ring-modulated buzz, zaps and sparks
  earth    a cave: stone grinding, a deep rumbling groan with the cave's echo; the Geode Knight's crystals ringing as he
           strains (a rubbed crystal's squeal), the Rockfall Knight a boulder's rumble and rattling gravel

Pitch-like sounds are kept irregular (impulse trains whose every gap is jittered, buzzes whose pitch is torn about by
noise, rings at partials out of tune with each other), so none of them settles into the steady comb of harmonics a
throat makes. tools/knights/voicecheck.py measures it.

A voice is made with `voice(rng, b, dur, amp_pts, lift_pts, kind)`: `amp_pts` its loudness over time, `lift_pts` how
strained it is (0 calm .. 1 a scream: higher, faster, harsher), `kind` "breath", "murmur", "bark", "cry" or "death".
"""
import math

import numpy as np
from scipy import signal

from ratkit import synth as S

SR = S.SR


def env(points, dur):
    return S.smooth_env(points, S.n_of(dur))


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def lowwob(rng, n, rate, depth):
    """A slow random wobble (1 +- depth)."""
    w = S.lowpass(rng.standard_normal(n), rate)
    return 1.0 + depth * w / (np.max(np.abs(w)) + 1e-9)


# ---------------------------------------------------------------- building blocks (no throat, no vowels)


def impulses(rng, rate, jitter=0.45, lo=0.35):
    """Clicks at an irregular rate (`rate` per sample, per second): every gap 1/rate times (1 +- jitter), so a train of
    them rattles or creaks but never turns into a pitch."""
    n = len(rate)
    out = np.zeros(n)
    t = rng.uniform(0.0, 1.0 / max(rate[0], 1.0))
    while True:
        i = int(t * SR)
        if i >= n:
            break
        out[i] = rng.uniform(lo, 1.0)
        t += (1.0 / max(rate[i], 1.0)) * (1.0 + jitter * rng.uniform(-1.0, 1.0))
    return out


def pulse_env(imp, decay):
    """Each click a little burst dying away over `decay` seconds."""
    return signal.lfilter([1.0], [1.0, -math.exp(-1.0 / (decay * SR))], imp)


def rattle(rng, rate, lo, hi, decay=0.006, jitter=0.45, color="white"):
    """A rattle with no pitch: short bursts of noise (lo-hi Hz) at an irregular rate."""
    n = len(rate)
    e = pulse_env(impulses(rng, rate, jitter), decay)
    return norm(S.bandpass(S.noise(n, rng, color), lo, hi) * e)


def creak(rng, rate, freqs, q, gains=None, jitter=0.35, hiss=0.04):
    """Stick-slip creaking (ice, a hull, stone), rung through a few resonances out of tune with each other."""
    n = len(rate)
    exc = impulses(rng, rate, jitter) + hiss * S.noise(n, rng)
    gains = gains or [1.0 / (1 + k) for k in range(len(freqs))]
    return norm(sum(S.resonator(exc, f, q) * g for f, g in zip(freqs, gains)))


def jumping(rng, n, lo, hi, step=(0.02, 0.07), ms=6.0):
    """A centre frequency jumping about at random (log lo..hi), a step every 20-70 ms: a gurgle's bubbles, a growl's
    flutter."""
    out = np.zeros(n)
    i = 0
    while i < n:
        k = S.n_of(rng.uniform(*step))
        out[i:i + k] = math.exp(rng.uniform(math.log(lo), math.log(hi)))
        i += k
    k = max(1, int(SR * ms / 1000.0))
    return np.convolve(out, np.ones(k) / k, mode="same")


def drive(x, k):
    """Overdriven: squashed with tanh (k above 1 harsher)."""
    return np.tanh(k * norm(x)) / math.tanh(k)


def ringmod(x, rng, f, drift=0.08, rate=3.0):
    """Ring-modulated by a carrier that wanders: every partial split into two that belong to no harmonic series."""
    n = len(x)
    fr = f * lowwob(rng, n, rate, drift)
    return x * np.sin(2 * math.pi * np.cumsum(fr) / SR + rng.random() * 6.28)


def torn_buzz(rng, f, tear=0.25, tear_rate=70.0, tilt=0.9, nmax=36):
    """An electric buzz whose pitch is torn about by noise (`tear` of it, changing `tear_rate` times a second), so it
    snarls and fizzes instead of singing."""
    n = len(f)
    w = S.lowpass(rng.standard_normal(n), tear_rate)
    fr = f * (1.0 + tear * w / (np.std(w) * 2.5 + 1e-9))
    return S.saw(np.maximum(fr, 15.0), tilt, nmax)


def crush(x, bits=5, hold=5):
    """A broken radio: fewer samples and fewer levels."""
    y = np.repeat(norm(x)[::hold], hold)[:len(x)]
    q = 2 ** (bits - 1)
    return np.round(y * q) / q


def dropouts(rng, n, rate=6.0, length=(0.01, 0.04), floor=0.08):
    """A signal cutting out now and then (a broken radio): `rate` gaps a second."""
    g = np.ones(n)
    for _ in range(int(rate * n / SR)):
        i = int(rng.uniform(0, n))
        g[i:i + S.n_of(rng.uniform(*length))] = floor
    k = S.n_of(0.002)
    return np.convolve(g, np.ones(k) / k, mode="same")


def grit(rng, n, rate, lo, hi, grain=(0.002, 0.009)):
    """Grains of noise at random (frost, embers, gravel), `rate` per second along a curve."""
    dur = n / SR
    total = int(np.mean(rate) * dur) + 1
    out = np.zeros(n)
    cum = np.cumsum(rate)
    cum = cum / cum[-1]
    for _ in range(total):
        i = int(np.searchsorted(cum, rng.random()))
        gn = S.n_of(rng.uniform(*grain))
        f = math.exp(rng.uniform(math.log(lo), math.log(hi)))
        piece = S.bandpass(S.noise(gn, rng), f * 0.7, f * 1.4) * np.hanning(gn) * rng.uniform(0.2, 1.0)
        out[i:i + gn] += piece[:len(out[i:i + gn])]
    return norm(out)


def air(rng, n, lo, hi, color="pink"):
    return norm(S.bandpass(S.noise(n, rng, color), lo, hi))


def helm(x, b, ring=0.35, hollow=0.25, rng=None):
    """Inside a sealed helm: a bell-like ring of partials out of tune with each other (lower in a heavier knight's
    bigger helm), and a little hollow space round it (a tiny room, its echoes far longer than any voice's pitch)."""
    base = 2400.0 / b.helm
    parts = (1.0, 1.59, 2.14, 2.83, 3.71, 4.62)
    rung = sum(S.resonator(x, base * r, 35.0) / (1.0 + 0.6 * k) for k, r in enumerate(parts))
    y = norm(x) + ring * norm(rung)
    return S.reverb(y, mix=hollow, room=0.55 + 0.25 * b.w, damp=3500.0 - 1800.0 * b.w)


# ---------------------------------------------------------------- each element's voice
# Each takes the knight, the length, how loud over time (`amp`, per sample), how strained (`lift`, per sample, 0..1)
# and the kind of sound, and returns his voice before the helm.


def _up(b):
    """How high this knight's voice sits: 1.25 the lightest .. 0.5 the heaviest."""
    return b.low(1.0)


def ice_voice(rng, b, n, amp, lift, kind):
    up = _up(b)
    spear = b.key == "thawed_spear"
    # breath rattling through the ice in his chest
    rat = rattle(rng, (14.0 + 26.0 * lift) * (0.8 + 0.3 * up), 420.0 * up, 3600.0 * up, 0.007, 0.5, "pink")
    # the ice on him groaning: slow stick-slip through deep, out-of-tune resonances (the axeman deeper, slower)
    g0 = (150.0 if not spear else 230.0) * up
    groan = creak(rng, (9.0 + 38.0 * lift) * (0.7 + 0.4 * up), (g0, g0 * 2.37, g0 * 3.91, g0 * 6.2), 6.0)
    # glassy whistles through its cracks (two, beating; the spearman's sing out more)
    cen = (1700.0 + 2400.0 * lift) * (1.15 if spear else 0.85)
    src = S.noise(n, rng)
    whistle = S.sweep_filter(src, cen, 0.08) + 0.7 * S.sweep_filter(S.noise(n, rng), cen * 1.37, 0.08)
    whistle = norm(whistle) * lowwob(rng, n, 7.0, 0.6)
    frost = grit(rng, n, 120.0 + 500.0 * lift, 2500.0, 9500.0)
    exhale = air(rng, n, 650.0 * up, 4800.0)
    w = {"breath": (0.25, 0.25, 0.15, 0.25, 1.0), "murmur": (0.45, 0.9, 0.25, 0.25, 0.4),
         "bark": (0.8, 0.8, 0.35, 0.35, 0.3), "cry": (0.7, 0.8, 0.75 if spear else 0.5, 0.45, 0.3),
         "death": (0.7, 0.9, 0.6 if spear else 0.4, 0.5, 0.45)}[kind]
    if not spear:      # the axeman: a bigger, deeper body of ice, its groan the most of him
        groan = groan * 1.35
    out = w[0] * rat + w[1] * groan + w[2] * whistle * (0.4 + 0.6 * lift) + w[3] * frost + w[4] * exhale
    return out * amp


def poison_voice(rng, b, n, amp, lift, kind):
    up = _up(b)
    warden = b.key == "blight_warden"
    # the gurgle: thick noise through a mouth of rot whose centre jumps about like bubbles bursting
    lo, hi = (260.0, 1300.0) if warden else (110.0, 620.0)
    cen = jumping(rng, n, lo, hi, (0.02, 0.06) if warden else (0.035, 0.09))
    gurgle = norm(S.sweep_filter(S.noise(n, rng, "pink"), cen * (0.8 + 0.5 * lift), 0.35))
    gurgle *= pulse_env(impulses(rng, (16.0 + 22.0 * lift) * (0.7 + 0.4 * up), 0.55, 0.2), 0.02) * 0.7 + 0.3
    bub = S.bubbles(rng, n / SR, (20.0 + 70.0 * float(np.max(lift))) * (1.3 if warden else 0.8), lo * 0.8, hi * 0.9)
    bub = norm(S.place(n / SR, (0.0, bub))[:n]) * (0.3 + 0.7 * lift)
    # a wheeze whistling through clogged lungs (the Warden's thin and high, the Sporebound's a low rasp)
    wz_c = (2600.0 if warden else 1300.0) * (0.75 + 0.6 * lift)
    wheeze = norm(S.sweep_filter(S.noise(n, rng), wz_c, 0.18 if warden else 0.45)) * lowwob(rng, n, 12.0, 0.7)
    clicks = grit(rng, n, 25.0 + 90.0 * lift, 1800.0 if warden else 900.0, 6500.0 if warden else 3200.0,
                  (0.0008, 0.003))
    # the Sporebound's spores puffing out of him: soft low thumps of air
    puffs = norm(S.lowpass(S.noise(n, rng, "brown"), 500.0) * pulse_env(impulses(rng, np.full(n, 3.5), 0.6, 0.3), 0.06))
    hiss = air(rng, n, 3000.0, 9000.0, "white")
    w = {"breath": (0.35, 0.35, 0.6, 0.2, 0.0), "murmur": (0.8, 0.6, 0.35, 0.3, 0.0),
         "bark": (0.9, 0.35, 0.4, 0.35, 0.3), "cry": (0.8, 0.45, 0.8, 0.4, 0.45), "death": (0.9, 0.7, 0.55, 0.4, 0.5)}[kind]
    out = w[0] * gurgle + w[1] * bub + w[2] * wheeze + w[3] * clicks + w[4] * hiss * lift ** 1.5
    if not warden:
        out = out + (0.55 if kind in ("breath", "murmur") else 0.3) * puffs
    return out * amp


def fire_voice(rng, b, n, amp, lift, kind):
    up = _up(b)
    cinder = b.key == "cinder"
    # bellows breath: a furnace's roar, rushing in strokes, flickering
    lo, hi = (45.0 * up, 1100.0 * up) if cinder else (220.0, 2800.0)
    bellows = norm(S.bandpass(S.noise(n, rng, "brown" if cinder else "pink"), lo, hi))
    bellows = bellows + lift * air(rng, n, hi, hi * 3.0)      # strained, its roar opens up brighter
    bellows *= lowwob(rng, n, 9.0, 0.45)
    # the growl: flame fluttering at an irregular rate in the furnace's throat (low and heavy for the Cinder Knight)
    flutter = (18.0 + 30.0 * lift) * (0.75 + 0.35 * up)
    growl = rattle(rng, flutter, 55.0 * up if cinder else 180.0, 650.0 * up if cinder else 1900.0,
                   0.018 if cinder else 0.01, 0.5, "pink")
    # an iron furnace's ring under it (the Cinder Knight's armour is the furnace)
    if cinder:
        growl = growl + 0.35 * norm(sum(S.resonator(growl, f, 9.0) for f in (97.0, 163.0, 251.0, 389.0)))
    crackle = grit(rng, n, 80.0 + 380.0 * lift, 900.0, 7000.0, (0.0008, 0.003))
    hiss = air(rng, n, 2500.0, 10000.0, "white")
    # Flamehand flares: whooshes of flame rising out of his fists
    flare = norm(S.sweep_filter(S.noise(n, rng, "pink"), 500.0 + 3800.0 * lift, 0.6))
    w = {"breath": (0.9, 0.2, 0.35, 0.1, 0.1), "murmur": (0.6, 0.7, 0.4, 0.1, 0.1),
         "bark": (0.7, 0.8, 0.5, 0.3, 0.45), "cry": (0.55, 0.85, 0.6, 0.55, 0.6),
         "death": (0.6, 0.9, 0.7, 0.45, 0.4)}[kind]
    out = w[0] * bellows + w[1] * growl + w[2] * crackle + w[3] * hiss * lift
    if not cinder:
        out = out + w[4] * flare + 0.25 * hiss * lift
    return out * amp


def thunder_voice(rng, b, n, amp, lift, kind):
    t = np.arange(n) / SR
    if b.key == "wreck":
        # a ship's hull groaning: slow stick-slip through deep, out-of-tune iron resonances
        hull_f = (41.0, 67.0, 103.0, 151.0, 233.0, 347.0)
        hull = creak(rng, 5.0 + 22.0 * lift, [f * (1.0 + 0.25 * float(np.mean(lift))) for f in hull_f], 10.0,
                     gains=(1.0, 0.9, 0.7, 0.5, 0.35, 0.25), jitter=0.4, hiss=0.08)
        scrape = norm(S.sweep_filter(S.noise(n, rng, "pink"), 180.0 + 500.0 * lift, 0.25)) * lowwob(rng, n, 2.0, 0.6)
        # a drowned electric hum through a broken radio
        hum = torn_buzz(rng, (38.0 + 30.0 * lift) * (1.0 + 0.02 * np.sin(2 * math.pi * 0.6 * t)), 0.22, 30.0, 1.0, 30)
        hum = ringmod(S.lowpass(hum, 1400.0), rng, 61.3, 0.15)
        radio = crush(S.bandpass(hum, 120.0, 2200.0), 4, 9) * dropouts(rng, n, 5.0)
        sparks = grit(rng, n, 30.0 + 160.0 * lift, 2500.0, 11000.0, (0.0004, 0.002))
        # the deep water round the hull: a slow, heavy swell under it all (what makes him the "fattest")
        swell = norm(S.lowpass(S.noise(n, rng, "brown"), 75.0)) * lowwob(rng, n, 1.5, 0.5)
        w = {"breath": (0.7, 0.5, 0.4, 0.15), "murmur": (0.9, 0.4, 0.6, 0.2), "bark": (1.0, 0.5, 0.5, 0.35),
             "cry": (1.0, 0.6, 0.65, 0.45), "death": (1.0, 0.7, 0.6, 0.45)}[kind]
        out = (w[0] * hull + w[1] * scrape + w[2] * drive(radio, 2.0) * (0.5 + 0.5 * lift) + w[3] * sparks
               + 0.9 * swell)
        return out * amp
    # the Stormblade: a quick electric snarl, a buzz torn about and ring-modulated, sparks and zaps
    f = (110.0 + 240.0 * lift)
    buzz = torn_buzz(rng, f, 0.5, 110.0, 0.8, 40)
    buzz = ringmod(buzz, rng, 733.0 + 900.0 * lift, 0.18, 9.0)
    buzz = drive(S.bandpass(buzz, 700.0, 7500.0), 3.0)
    gate = pulse_env(impulses(rng, 28.0 + 40.0 * lift, 0.5, 0.3), 0.012)
    snarl = buzz * (0.35 + 0.65 * norm(gate)) * dropouts(rng, n, 8.0, (0.005, 0.02))
    sparks = grit(rng, n, 120.0 + 700.0 * lift, 3000.0, 14000.0, (0.0003, 0.0015))
    static = air(rng, n, 1500.0, 9000.0, "white") * lowwob(rng, n, 20.0, 0.8)
    w = {"breath": (0.2, 0.4, 0.6), "murmur": (0.45, 0.4, 0.4), "bark": (0.9, 0.5, 0.3),
         "cry": (0.9, 0.6, 0.35), "death": (0.85, 0.6, 0.45)}[kind]
    return (w[0] * snarl + w[1] * sparks + w[2] * static) * amp


def earth_voice(rng, b, n, amp, lift, kind):
    up = _up(b)
    geode = b.key == "geode"
    # stone grinding in his joints
    grind = grit(rng, n, 160.0 + 600.0 * lift, 130.0 * up, 1500.0 * up, (0.003, 0.014))
    # a cave's deep groan: stone slipping on stone, rung through thick, damped resonances
    s0 = (70.0 if not geode else 105.0) * (0.8 + 0.4 * float(np.mean(lift)))
    groan = creak(rng, (6.0 + 22.0 * lift) * (0.7 + 0.4 * up), (s0, s0 * 1.83, s0 * 2.94, s0 * 4.4), 4.0, jitter=0.45,
                  hiss=0.1)
    rumble = norm(S.lowpass(S.noise(n, rng, "brown"), 130.0 * up + 60.0)) * lowwob(rng, n, 3.0, 0.5)
    gravel = grit(rng, n, 40.0 + 250.0 * lift, 500.0, 4000.0, (0.003, 0.012))
    # the Geode Knight's crystals squealing as he strains: a rubbed crystal (fast stick-slip through glassy rings)
    xtal = creak(rng, 50.0 + 140.0 * lift, (1730.0, 2690.0, 4130.0, 5870.0), 45.0, jitter=0.3, hiss=0.02)
    w = {"breath": (0.4, 0.35, 0.8, 0.2, 0.3), "murmur": (0.5, 0.8, 0.7, 0.25, 0.45),
         "bark": (0.7, 0.8, 0.5, 0.4, 0.6), "cry": (0.6, 0.85, 0.5, 0.5, 0.9), "death": (0.7, 0.9, 0.7, 0.6, 0.7)}[kind]
    if geode:      # a lighter body of stone than the Rockfall Knight's: less rumble, his crystals ringing through
        out = w[0] * grind + 0.7 * w[1] * groan + 0.35 * w[2] * rumble + w[3] * gravel
        out = out + w[4] * xtal * (0.4 + 0.6 * lift)
        return out * amp
    # the Rockfall Knight: a boulder's body, its rumble swelling as he strains
    out = w[0] * grind + w[1] * groan + w[2] * rumble + w[3] * gravel + 0.25 * rumble * lift
    return out * amp


VOICES = {"ice": ice_voice, "poison": poison_voice, "fire": fire_voice, "thunder": thunder_voice, "earth": earth_voice}


def voice(rng, b, dur, amp_pts, lift_pts, kind):
    """This knight's voice for `dur` seconds, in his helm."""
    n = S.n_of(dur)
    amp = S.smooth_env(amp_pts, n)
    lift = np.clip(S.curve(lift_pts, n), 0.0, 1.0)
    raw = VOICES[b.element](rng, b, n, amp, lift, kind)
    return norm(helm(raw, b, rng=rng))[:n + S.n_of(0.6)]


def inhale(rng, b, dur, level=1.0):
    """Air drawn in through his helm before a big blow, in his element (it rises)."""
    return voice(rng, b, dur, [(0.0, 0.0), (dur * 0.8, 1.0), (dur, 0.0)], [(0.0, 0.1), (dur, 0.5)], "breath") * level
