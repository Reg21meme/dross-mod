"""
Makes the fire mobs' sounds (October 27, 2026): the Emberdactyl's and its young's, the Pallbearer's and the Cinder
Hermit's. Every one is synthesized from scratch with the Rat King's synthesizer (tools/ratking/ratkit/synth.py) and the
Huntsman's voice tools (tools/huntsman/voicekit.py): nothing is recorded and nothing comes from Minecraft or a film.

The Emberdactyl's screech: the user asked for its cry to be like a certain giant pterosaur's from the films ("for his
screech I want to copy Rodan"), then, after hearing the first versions, for every sound of it to be deeper, "a
kaiju-level noise". That recording is the film studio's, so this is an original sound in the same spirit: a huge,
piercing shriek with a fast trill in it and a metallic, ringing edge, sung by a choir of voices a little out of tune
with each other, over a roaring throat two and three octaves down and a sub-bass weight, echoing across a valley.
The user kept those three for its young (October 27) and asked for the grown one's to be much deeper ("that level of
depth"): its own three are about half their pitch over a far heavier throat, growl and sub-bass (`ed_screech_deep`).
Every other Emberdactyl sound is as big; its young's are higher and shorter. The user's picks of that round: its hurt,
talons, wingbeats, dive, gust and rattles kept as they are and played in turn; the Pallbearer's roar and death in three
versions each to pick from (the showcase plays version 1 meanwhile).

Run from the project folder:

    python tools/fire/sounds.py
    python tools/fire/sounds.py --only emberdactyl,pallbearer/roar   (only those folders' sounds, or those sounds;
                                                                     the rest as they are)

A sound whose samples haven't changed isn't written again (each file keeps a fingerprint of its samples in its Ogg
comment: the encoder makes different bytes every run). Files from before the fingerprints are written once more.

It writes:
  src/main/resources/assets/dross/sounds/emberdactyl/*.ogg, emberyoung/, pallbearer/, hermit/   (mono Ogg Vorbis)
  src/main/resources/assets/dross/sounds.json        adds (or replaces) its own events; every other entry stays as it was
  src/main/resources/assets/dross/lang/en_us.json    adds the subtitles it needs (never changes other keys)
  src/main/java/com/reg21meme/dross/boss/fire/FireSounds.java   every event, and which sound each animation makes when
                                                                 (the showcases read it)
  boss-previews/cinder_wastes/sounds/index.html      a page that plays every version (open it in a browser)

The moments the sounds play at come from the animations (tools/fire/firemobs/anims.json, written by mobs.py: run that
first; the Emberdactyl's death sound lines its crash up with the animation's). Needs numpy, scipy and soundfile.
"""
import hashlib
import json
import math
import os
import sys
import zlib

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "ratking"))
sys.path.append(os.path.join(ROOT, "tools", "huntsman"))

import numpy as np  # noqa: E402
import soundfile  # noqa: E402
from scipy import signal  # noqa: E402
from scipy.ndimage import maximum_filter1d, minimum_filter1d  # noqa: E402

from ratkit import synth as S  # noqa: E402
import voicekit as V  # noqa: E402  (the Huntsman's voice tools: its glottal puffs, gliding formants, breaths)

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
SOUND_DIR = os.path.join(ASSETS, "sounds")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "fire", "FireSounds.java")
PREVIEWS = os.path.join(ROOT, "boss-previews", "cinder_wastes", "sounds")
ANIMS = os.path.join(HERE, "firemobs", "anims.json")


def env(points, dur):
    return S.smooth_env(points, S.n_of(dur))


# ---------------------------------------------------------------- kaiju pieces (the Emberdactyl and its young)
#
# The user, after hearing the first versions in the game (October 27): "I want the scream to be Rodan-like ... make all
# the noises deeper, it should sound like a Rodan-esque noise, a kaiju-level noise". So every Emberdactyl sound is made
# for something the size of a hill: a deep body under every cry (a roaring throat two and three octaves under the
# shriek, and a sub-bass weight), several voices a little out of tune with each other (a choir of voices reads as one
# enormous one), grit, and the echo of a valley. Its young is the same animal, smaller: higher and shorter, but still big.


def norm(x):
    """Scaled to an RMS of 1 (so layers mix by their weights, whatever made them)."""
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-12)


def fade_end(x, seconds=0.06):
    """Fades a sound's last `seconds` out (a long thud cut off while it still rings would click)."""
    k = min(len(x), S.n_of(seconds))
    x = x.copy()
    x[-k:] *= np.linspace(1.0, 0.0, k)
    return x


def boom(rng, f0, f1, dur, click=0.3):
    """Something huge hitting the ground (the synth's thud, faded out at its end)."""
    return fade_end(S.thud(rng, f0, f1, dur, click), dur * 0.3)


def limit(x, ceiling=0.5, release=0.06):
    """
    A limiter: turns the loudest instants (a thud's first moment) down to `ceiling` of the peak, easing back over
    `release` seconds, so they don't decide how loud the whole sound can be. It turns the level down; it never clips.
    """
    a = np.abs(x) / (np.max(np.abs(x)) + 1e-12)
    r = S.n_of(release)
    g = np.minimum(1.0, ceiling / (maximum_filter1d(a, size=S.n_of(0.004) * 2 + 1) + 1e-12))
    g = minimum_filter1d(g, size=2 * r + 1)
    g = np.convolve(g, np.ones(r) / r, mode="same")
    return x * g


def wobble(n, rate, rng, square=0.0, wander=0.06):
    """
    A warble's shape: an oscillator from -1 to 1 at `rate` Hz (a number or (s, Hz) points), its rate wandering a little
    (`wander`); `square` rounds it towards a square wave (0 a sine; 1-3 a trill flipping between two notes). Returns the
    shape and its phase (so a second shape can ride the same beat).
    """
    r = V.track(rate, n, log=True)
    w = S.lowpass(rng.standard_normal(n), 3.0)
    r = r * (1.0 + wander * w / (np.std(w) + 1e-9))
    phase = 2 * math.pi * np.cumsum(r) / S.SR + rng.random() * 6.28
    m = np.sin(phase)
    if square > 0:
        m = np.tanh(square * m) / math.tanh(square)
    return m, phase


def throat(f, rng, oq=0.45, jitter=0.02, shimmer=0.12, rasp=0.3):
    """
    A huge throat's buzz on a pitch curve `f` (Hz per sample): one Rosenberg puff of air per period (voicekit's folds,
    following any curve), each a little off in length and loudness; `rasp` makes every other puff weaker, which drops a
    growl an octave under it (the period doubles). Returns what the lips radiate.
    """
    n = len(f)
    flow = np.zeros(n + 8192)
    t, k = 0.0, 0
    while t < n:
        i = int(t)
        period = max(S.SR / f[i] * (1.0 + jitter * rng.standard_normal()), 12.0)
        a = max(0.0, 1.0 + shimmer * rng.standard_normal())
        if k % 2:
            a *= 1.0 - rasp
        te = period * oq
        tp = te * 0.7
        tn = max(te - tp, 1.0)
        length = int(period) + 1
        s = np.arange(length, dtype=float) - (t - i)
        g = np.zeros(length)
        up = (s >= 0) & (s < tp)
        g[up] = 0.5 * (1.0 - np.cos(math.pi * s[up] / tp))
        down = (s >= tp) & (s < te)
        g[down] = np.cos(0.5 * math.pi * (s[down] - tp) / tn)
        flow[i:i + length] += a * g
        t += period
        k += 1
    return np.diff(flow[:n], prepend=0.0)


def lead_env(e, lead):
    """An envelope moved `lead` seconds earlier (the throat's roar starts a moment before the shriek), faded in."""
    k = S.n_of(lead)
    if k <= 1:
        return e
    out = np.concatenate([e[k:], np.full(k, e[-1])])
    fade = min(len(out), S.n_of(0.04))
    out[:fade] *= np.linspace(0.0, 1.0, fade)
    return out


def kaiju_cry(rng, dur, f0, amp, vowels, size=1.0, warble=(16.0, 0.04, 0.3), square=0.8, flutter=(31.0, 0.14),
              metal=0.55, ring=0.71, body=0.9, deep=0.75, sub=0.7, rasp=0.35, drive=1.8, voices=5, spread=16.0,
              stutter=None, lead=0.12, body_gain=1.0, octaves=(2, 3, 4), beak=None, pipe=1530.0, growl=0.0,
              tilt=0.85, metal_band=(1200.0, 8000.0), top=11000.0):
    """
    A kaiju's cry (Rodan's kind: a piercing, warbling, metallic shriek, here with a mountain under it). f0: the shriek's
    pitch ((s, Hz) points); amp: its loudness ((s, level) points); vowels: the beak's shape ((s, vowel) points). Every
    layer rides the one pitch curve, so they fuse into one voice:
      - the shriek: `voices` bright buzzing voices `spread` cents apart (a choir of them sounds enormous) through the
        beak's vowel, with the trill: `warble` = (rate Hz, pitch depth, loudness depth; each a number or (s, value)
        points), rounded towards a flip between two notes by `square`, and a fast `flutter` (Hz, depth) in its loudness;
      - the metal: the shriek ring-modulated at `ring` x its pitch (clanging partials that follow it) and rung through a
        metal pipe (a comb filter), mixed in by `metal`;
      - the body: a roaring throat two octaves down (`body`) and three octaves down (`deep`), its every other puff weaker
        by `rasp` (a growl an octave further under), through a throat far bigger than a man's; it starts `lead` seconds
        before the shriek; `body_gain` ((s, gain) points) swells or fades it over the cry;
      - the weight: a sub-bass four octaves down and a chest rumble (`sub`), swelling in a little after the cry starts;
      - the breath: a rush of air through it all.
    `stutter` = (depth (s, depth) points, rate Hz or points): the cry chopped into fast bursts. `size` < 1 makes it
    smaller (the young): less body and weight, smaller throat and beak. `drive`: how hard it's pushed into grit (a
    tanh saturation: intended). For a bigger animal: `octaves` (how many octaves under the shriek the throat, the low
    throat and the sub-bass sit), `beak` (its vowels' scale: lower is a bigger mouth), `pipe` (the metal's ring, Hz) and
    `growl` (a gravel of breath riding the throat's puffs: a roar's rasp), `tilt` (how fast the shriek's overtones
    fall away: higher is darker), `metal_band` (where the metal rings, Hz) and `top` (nothing above it, Hz). Returns it
    before any echo.
    """
    n = S.n_of(dur)
    sz = size
    f = V.track(f0, n, log=True)
    drift = S.lowpass(rng.standard_normal(n), 6.0)
    f = f * (1.0 + 0.008 * drift / (np.std(drift) + 1e-9))
    rate, fm_depth, am_depth = warble
    m, phase = wobble(n, rate, rng, square)
    m2 = np.sin(phase + 0.5)
    if square > 0:
        m2 = np.tanh(square * m2) / math.tanh(square)
    fm = 1.0 + V.track(fm_depth, n) * m
    am = 1.0 - V.track(am_depth, n) * 0.5 * (1.0 - m2)          # loudest just after the upper note
    fl, _ph = wobble(n, flutter[0], rng, 0.0, 0.1)
    am = am * (1.0 - flutter[1] * 0.5 * (1.0 - fl))
    gate_hi = gate_lo = gate_out = 1.0
    if stutter is not None:
        depth = V.track(stutter[0], n)
        gm, _ph = wobble(n, stutter[1], rng, 3.0, 0.04)
        chop = 0.5 * (1.0 - gm)                                  # 0 in a burst, 1 between
        gate_hi = 1.0 - depth * chop
        gate_lo = 1.0 - 0.7 * depth * chop
        gate_out = 1.0 - 0.8 * depth * np.convolve(chop, np.ones(S.n_of(0.004)) / S.n_of(0.004), mode="same")

    # the shriek: a choir of voices, a little out of tune with each other
    shriek = np.zeros(n)
    for i in range(voices):
        c = spread * sz ** 0.5 * (i - (voices - 1) / 2.0) / max(1.0, (voices - 1) / 2.0) + rng.uniform(-3.0, 3.0)
        wander = S.lowpass(rng.standard_normal(n), 4.0)
        fi = f * fm * 2 ** (c / 1200.0) * (1.0 + 0.004 * wander / (np.std(wander) + 1e-9))
        shriek += S.saw(fi, tilt=tilt, nmax=24) * rng.uniform(0.8, 1.0)
    beak = V.mouth(shriek, vowels, beak or 1.05 / sz ** 0.35, wobble=0.03, rng=rng)
    shriek = S.highpass(0.65 * norm(beak) + 0.35 * norm(S.highpass(shriek, 900.0)), 320.0)

    # the metal: clanging partials that follow the pitch, and a pipe that rings at its own
    carrier = np.sin(2 * math.pi * np.cumsum(f * fm * ring) / S.SR)
    clang = S.bandpass(shriek * carrier, *metal_band)
    d = int(round(S.SR / (pipe / sz ** 0.3)))
    a = np.zeros(d + 1)
    a[0], a[d] = 1.0, -0.78
    pipe = signal.lfilter([1.0], a, S.bandpass(shriek, 900.0, 9000.0))
    metal_layer = norm(clang) + 0.8 * norm(pipe)

    # the body: a roaring throat two and three octaves down
    bv = [(0.0, "ah"), (dur, "aw")]                             # the throat's own vowel, open
    big = 0.62 / sz ** 0.45
    roar = throat(f * np.sqrt(fm) / 2.0 ** octaves[0], rng, oq=0.42, jitter=0.025, shimmer=0.15, rasp=rasp)
    roar = V.mouth(roar, bv, big, wobble=0.04, rng=rng)
    roar = np.tanh(2.2 * roar / (np.max(np.abs(roar)) + 1e-9))
    low = throat(f * fm ** 0.3 / 2.0 ** octaves[1], rng, oq=0.5, jitter=0.04, shimmer=0.2, rasp=min(0.8, rasp + 0.25))
    low = V.mouth(low, bv, big * 0.9, wobble=0.04, rng=rng)
    low = np.tanh(1.8 * low / (np.max(np.abs(low)) + 1e-9))

    # the weight: a sub-bass four octaves down, and its chest rumbling
    fs = np.maximum(f * fm ** 0.2 / 2.0 ** octaves[2], 34.0)
    ph = 2 * math.pi * np.cumsum(fs) / S.SR
    rumble = S.lowpass(S.noise(n, rng, "brown"), 120.0)
    weight = norm(np.sin(ph) + 0.35 * np.sin(2 * ph)) + 0.6 * norm(rumble)
    air = S.bandpass(S.noise(n, rng, "pink"), 700.0, 6500.0)

    e = S.smooth_env(amp, n, ms=8.0)
    eb = lead_env(e, lead) * V.track(body_gain, n)
    es = np.convolve(lead_env(e, lead * 0.5), np.ones(S.n_of(0.08)) / S.n_of(0.08), mode="same")
    hi = (norm(shriek) + metal * norm(metal_layer)) * am * gate_hi * e
    lo = (body * norm(roar) * (0.5 + 0.5 * am) + deep * norm(low)) * gate_lo * eb * sz
    if growl > 0:                                                # breath torn by the throat's puffs: gravel
        puffs = S.lowpass(np.abs(roar), 90.0)
        grit = S.bandpass(S.noise(n, rng, "pink"), 120.0, 1800.0) * puffs / (np.max(puffs) + 1e-9)
        lo = lo + growl * norm(grit) * gate_lo * eb * sz
    out = hi + lo + sub * sz ** 1.5 * norm(weight) * es * gate_lo + 0.12 * norm(air) * e * am
    out /= np.max(np.abs(out)) + 1e-9
    out = np.tanh(drive * out) / math.tanh(drive) * gate_out
    return S.lowpass(out, top)


def valley(x, rng, rt60=3.0, mix=0.5, predelay=0.09, echoes=((0.38, 0.3), (0.82, 0.16), (1.31, 0.07))):
    """
    The cry thrown back by a valley's walls: a few distinct echoes off its cliffs (each later one quieter and duller),
    then, after a gap (`predelay`: the far walls), a long diffuse tail (`rt60` seconds to die away) whose highs die
    first. `mix`: how loud the tail is against the cry.
    """
    parts = [(0.0, x)]
    for k, (delay, gain) in enumerate(echoes):
        parts.append((delay, S.lowpass(x, 3200.0 / (1 + k) ** 0.6) * gain))
    dry = S.place(len(x) / S.SR, *parts)
    length = S.n_of(rt60 * 1.15)
    tt = np.arange(length) / S.SR
    ir = np.zeros(length)
    for lo, hi, k in ((20.0, 250.0, 1.0), (250.0, 1200.0, 0.85), (1200.0, 3500.0, 0.6), (3500.0, 12000.0, 0.35)):
        ir += S.bandpass(rng.standard_normal(length), lo, hi) * np.exp(-6.91 * tt / (rt60 * k))
    ir *= np.clip(tt / 0.06, 0.0, 1.0) ** 2                    # the walls are far: the tail builds up
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = signal.fftconvolve(dry, ir)
    wet *= mix * np.sqrt(np.sum(dry ** 2) / (np.sum(wet ** 2) + 1e-12))
    pre = S.n_of(predelay)
    out = np.zeros(max(len(dry), pre + len(wet)))
    out[:len(dry)] += dry
    out[pre:pre + len(wet)] += wet
    return out


def membrane(rng, size=1.0):
    """A wing's membrane snapping taut: a burst of air rung through a sail's low resonances (a flump you feel)."""
    exc = S.burst(0.5, rng, 0.03 * size ** 0.5)
    out = (S.resonator(exc, 95.0 / size, 2.5) + 0.7 * S.resonator(exc, 180.0 / size, 3.0)
           + 0.4 * S.resonator(exc, 420.0 / size, 4.0))
    t = np.arange(len(out)) / S.SR
    return norm(S.lowpass(out, 600.0 / size) * np.exp(-t / (0.12 * size ** 0.5)))


def whump(rng, size=1.0, dur=0.9):
    """The air shoved by a wing the size of a sail: a deep falling push and a mass of low air."""
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    f = (26.0 + 26.0 * np.exp(-t / 0.12)) / size ** 0.5
    push = S.tone(f, ((1, 1.0), (2, 0.3))) * np.clip(t / 0.06, 0.0, 1.0) * np.exp(-t / (0.25 * size ** 0.5))
    mass = S.lowpass(S.noise(n, rng, "brown"), 220.0 / size) * env([(0.0, 0.0), (0.12, 1.0), (dur, 0.0)], dur)
    return norm(push) + 0.8 * norm(mass)


# How high its shriek is: the first versions' pitches times this (a fourth lower: the user asked for deeper). Still a
# shriek (about 700-1000 Hz at its height): the trill and the metal keep it piercing, the body under it makes it huge.
SHRIEK = 0.78


def ed_screech(rng, kind, pitch=1.0, length=1.0, size=1.0, barks=1):
    p, L = pitch * SHRIEK, length
    if kind == 1:          # a long warbling shriek: up out of a growl, held and trilling, falling away into a roar
        d = 3.3 * L
        x = kaiju_cry(rng, d, [(0.0, 430 * p), (0.08 * L, 520 * p), (0.34 * L, 1120 * p), (0.9 * L, 1180 * p),
                               (1.6 * L, 1260 * p), (2.2 * L, 1150 * p), (2.7 * L, 820 * p), (d, 440 * p)],
                      [(0.0, 0.0), (0.06 * L, 0.55), (0.3 * L, 1.0), (2.2 * L, 0.9), (2.8 * L, 0.6), (d, 0.0)],
                      [(0.0, "ah"), (0.3 * L, "ee"), (1.4 * L, "eh"), (2.6 * L, "ah"), (d, "aw")], size=size,
                      warble=([(0.0, 9.0), (0.4 * L, 16.0), (2.0 * L, 17.0), (d, 9.0)], 0.045, 0.35), square=0.9,
                      body_gain=[(0.0, 1.0), (2.2 * L, 0.9), (d, 1.3)])
    elif kind == 2:        # a harsh, deep bark ("KRAAK!"), then the long scream
        b = 0.5 * L
        bark = kaiju_cry(rng, b, [(0.0, 330 * p), (0.08 * L, 640 * p), (0.25 * L, 600 * p), (b, 360 * p)],
                         [(0.0, 0.0), (0.02, 1.0), (0.3 * L, 0.8), (b, 0.0)], [(0.0, "ah"), (b, "aw")], size=size,
                         warble=(24.0, 0.03, 0.45), square=1.5, rasp=0.6, body=1.1, deep=0.8, drive=2.6, metal=0.4,
                         lead=0.03)
        s = 2.5 * L
        scream = kaiju_cry(rng, s, [(0.0, 900 * p), (0.12 * L, 1340 * p), (0.6 * L, 1300 * p), (1.6 * L, 1080 * p),
                                    (2.1 * L, 820 * p), (s, 560 * p)],
                           [(0.0, 0.0), (0.05, 1.0), (1.7 * L, 0.85), (s, 0.0)],
                           [(0.0, "ee"), (1.0 * L, "eh"), (s, "ah")], size=size,
                           warble=([(0.0, 20.0), (1.2 * L, 15.0), (s, 11.0)], 0.05, 0.38), square=1.0, metal=0.65,
                           lead=0.05)
        # (`barks` 2: the very same bark twice, then the scream: the young's, the user's of October 27, "squawk,
        # squawk, then the main scream")
        gap = 0.62 * L * (barks - 1)
        x = S.place(gap + 0.68 * L + s, (0.0, bark), *([(gap, bark)] if barks > 1 else []), (gap + 0.68 * L, scream))
    else:                  # a stuttering trill that breaks into a long, deep, roaring scream
        d = 3.2 * L
        x = kaiju_cry(rng, d, [(0.0, 980 * p), (0.25 * L, 1240 * p), (1.1 * L, 1300 * p), (1.35 * L, 1180 * p),
                               (2.2 * L, 760 * p), (2.8 * L, 520 * p), (d, 400 * p)],
                      [(0.0, 0.0), (0.04 * L, 0.9), (1.1 * L, 0.95), (1.3 * L, 1.0), (2.6 * L, 0.75), (d, 0.0)],
                      [(0.0, "ee"), (1.1 * L, "ih"), (1.4 * L, "ah"), (d, "aw")], size=size,
                      warble=([(0.0, 13.0), (1.1 * L, 13.0), (1.4 * L, 8.0), (d, 6.0)],
                              [(0.0, 0.08), (1.1 * L, 0.08), (1.4 * L, 0.025), (d, 0.02)], 0.3), square=2.0,
                      stutter=([(0.0, 0.85), (1.05 * L, 0.85), (1.35 * L, 0.0), (d, 0.0)], [(0.0, 12.0), (1.1 * L, 14.0)]),
                      rasp=0.45, drive=2.0, lead=0.04,
                      body_gain=[(0.0, 0.55), (1.15 * L, 0.6), (1.5 * L, 1.25), (d, 1.3)])
    return limit(valley(x, rng, rt60=3.0 * size ** 0.5, mix=0.5, echoes=((0.38, 0.3), (0.82, 0.16), (1.31, 0.07))), 0.8)


# The grown Emberdactyl's own screech (October 27, round 2; the user: "for the bigger kaiju it needs to be deeper ...
# that level of depth"): the first three became its young's. About half their pitch (the shriek tops out near 500 Hz,
# where a huge bird of prey's scream would sit slowed right down), a mouth a third bigger, the throat one octave and two
# under it (not two and three), the sub-bass three, a gravel growl in the throat, a lower metal ring, a bigger valley.
DEEP = dict(octaves=(1, 2, 3), beak=0.8, pipe=980.0, body=1.2, deep=1.3, sub=1.5, growl=0.9, drive=2.2, tilt=1.15,
            metal_band=(700.0, 5000.0), top=8000.0)


def ed_screech_deep(rng, kind):
    if kind == 1:          # the long call: up out of a growl, held and trilling, sinking back into a roar
        d = 3.8
        x = kaiju_cry(rng, d, [(0.0, 170), (0.12, 220), (0.45, 470), (1.0, 500), (1.8, 520), (2.5, 480), (3.1, 330),
                               (d, 190)],
                      [(0.0, 0.0), (0.08, 0.6), (0.4, 1.0), (2.5, 0.9), (3.2, 0.6), (d, 0.0)],
                      [(0.0, "aw"), (0.4, "ah"), (1.5, "eh"), (2.9, "ah"), (d, "oh")],
                      warble=([(0.0, 8.0), (0.5, 13.0), (2.4, 14.0), (d, 7.0)], 0.05, 0.38), square=1.0, rasp=0.5,
                      lead=0.2, body_gain=[(0.0, 1.2), (2.4, 1.0), (d, 1.5)], **DEEP)
    elif kind == 2:        # two harsh barks ("KRRAK! KRRAK!"), then the long scream
        barks = [kaiju_cry(rng, 0.45, [(0.0, f0 * 0.5), (0.08, f0), (0.25, f0 * 0.9), (0.45, f0 * 0.65)],
                           [(0.0, 0.0), (0.02, 1.0), (0.28, 0.8), (0.45, 0.0)], [(0.0, "ah"), (0.45, "aw")],
                           warble=(20.0, 0.03, 0.5), square=1.5, rasp=0.65, lead=0.03,
                           **dict(DEEP, body=1.4, growl=1.1, drive=2.8)) for f0 in (330, 310)]
        s = 2.6
        scream = kaiju_cry(rng, s, [(0.0, 420), (0.15, 540), (0.8, 520), (1.7, 430), (2.2, 300), (s, 200)],
                           [(0.0, 0.0), (0.05, 1.0), (1.8, 0.85), (s, 0.0)], [(0.0, "eh"), (1.0, "ah"), (s, "aw")],
                           warble=([(0.0, 16.0), (1.3, 12.0), (s, 9.0)], 0.055, 0.4), square=1.0, rasp=0.5,
                           metal=0.65, lead=0.08, **DEEP)
        x = S.place(1.25 + s, (0.0, barks[0]), (0.6, barks[1]), (1.25, scream))
    else:                  # a rattling throat-trill that breaks into a long, falling, growling roar
        d = 3.7
        x = kaiju_cry(rng, d, [(0.0, 400), (0.3, 480), (1.2, 510), (1.5, 460), (2.4, 300), (3.0, 210), (d, 150)],
                      [(0.0, 0.0), (0.04, 0.9), (1.2, 0.95), (1.4, 1.0), (2.8, 0.75), (d, 0.0)],
                      [(0.0, "ih"), (1.2, "eh"), (1.5, "ah"), (d, "oh")],
                      warble=([(0.0, 10.0), (1.2, 11.0), (1.5, 7.0), (d, 5.0)],
                              [(0.0, 0.07), (1.2, 0.07), (1.5, 0.025), (d, 0.02)], 0.3), square=2.0,
                      stutter=([(0.0, 0.8), (1.15, 0.8), (1.45, 0.0), (d, 0.0)], [(0.0, 9.0), (1.2, 11.0)]),
                      rasp=0.6, lead=0.04, body_gain=[(0.0, 0.7), (1.2, 0.8), (1.6, 1.4), (d, 1.6)],
                      **dict(DEEP, growl=1.0))
    return limit(valley(x, rng, rt60=3.5, mix=0.55, echoes=((0.45, 0.3), (0.95, 0.16), (1.5, 0.07))), 0.8)


def ed_hurt(rng, kind, pitch=1.0, size=1.0):
    p = pitch * SHRIEK
    d = (0.85 if kind == 1 else 1.05) * size ** 0.3
    f = [(0.0, 620 * p), (0.1, 1050 * p), (0.35 * d, 980 * p), (d, 480 * p)] if kind == 1 else \
        [(0.0, 520 * p), (0.18, 900 * p), (d, 380 * p)]
    x = kaiju_cry(rng, d, f, [(0.0, 0.0), (0.03, 1.0), (d * 0.6, 0.85), (d, 0.0)], [(0.0, "eh"), (d, "ah")],
                  size=size, warble=(21.0, 0.035, 0.4), square=1.2, rasp=0.45, drive=2.2, lead=0.04)
    return limit(valley(x, rng, rt60=2.0 * size ** 0.5, mix=0.42, echoes=((0.36, 0.2), (0.78, 0.08))), 0.8)


DEATH_CRY = 0.2        # where the death animation's cry and crash are (anims.json's moments; read below if it's there)
DEATH_CRASH = 2.6


def death_crash_at():
    """How far into the death sound the body hits the ground: the animation's crash, counted from its cry."""
    try:
        with open(ANIMS, encoding="utf-8") as f:
            cues = json.load(f)["emberdactyl"]["cues"]["death"]
        return cues["crash"] - cues["cry"]
    except (OSError, KeyError, ValueError):
        return DEATH_CRASH - DEATH_CRY


def ed_death(rng, kind):
    c = death_crash_at()                                     # (2.4 s: it screams, falls and hits the ground)
    d = c - 0.05
    # (its cry in the deep voice of its own screech, DEEP: the user, October 27, "make sure his death pitch is as deep
    # as" its screech; it was its young's pitch)
    cry = kaiju_cry(rng, d, [(0.0, 440), (0.35, 520), (1.2, 420), (c - 0.6, 250), (d, 130)],
                    [(0.0, 0.0), (0.06, 1.0), (1.3, 0.85), (c - 0.4, 0.55), (d, 0.0)],
                    [(0.0, "eh"), (1.0, "ah"), (d, "oh")],
                    warble=([(0.0, 13.0), (1.2, 10.0), (d, 5.0)], [(0.0, 0.05), (d, 0.08)], 0.4), square=1.0,
                    rasp=0.55, lead=0.15, body_gain=[(0.0, 1.2), (d, 1.6)], **DEEP)
    hit = boom(rng, 46.0, 22.0, 1.8, 0.35)
    slam = S.lowpass(S.burst(0.6, rng, 0.08), 260.0)
    debris = S.grains(rng, 1.6, 110.0, grain=(0.004, 0.025), lo=150.0, hi=1500.0, level=0.55)
    rocks = [(c + rng.uniform(0.05, 1.1), norm(S.knock(rng, freqs=(rng.uniform(140, 200), rng.uniform(400, 560), 1100.0),
                                                        qs=(6, 7, 8), gains=(1.0, 0.6, 0.3), dur=0.3, decay=0.06))
              * rng.uniform(0.1, 0.25)) for _ in range(7)]
    rumble = S.lowpass(S.noise(S.n_of(2.4), rng, "brown"), 70.0) * env([(0.0, 1.0), (2.4, 0.0)], 2.4)
    last = V.mouth(throat(V.track([(0.0, 34.0), (1.3, 25.0)], S.n_of(1.3), log=True), rng, oq=0.3, jitter=0.2,
                          shimmer=0.3, rasp=0.6), [(0.0, "aw"), (1.3, "oh")], 0.55)
    last = norm(last) * env([(0.0, 0.0), (0.1, 1.0), (0.9, 0.6), (1.3, 0.0)], 1.3)
    sigh = V.aspirate(1.4, [(0.0, "ah"), (1.4, "oh")], rng, scale=0.5, lo=150.0)
    x = S.place(c + 3.4, (0.0, norm(cry) * 1.0), (c, norm(hit) * 1.1), (c, norm(slam) * 0.6),
                (c + 0.05, membrane(rng, 1.4) * 0.6), (c + 0.22, membrane(rng, 1.4) * 0.45), (c + 0.02, norm(debris) * 0.25),
                (c, norm(rumble) * 0.8), (c + 0.75, last * 0.55), (c + 1.95, norm(sigh) * 0.25), *rocks)
    return limit(valley(x, rng, rt60=2.8, mix=0.45, echoes=((0.4, 0.22), (0.85, 0.1))), 0.45)


def ed_ambient(rng, kind):
    if kind == 1:          # a deep, throaty rattle and its obsidian beak clacking shut
        d = 1.6
        n = S.n_of(d)
        r = throat(V.track([(0.0, 50.0), (0.5, 58.0), (d, 40.0)], n, log=True), rng, oq=0.3, jitter=0.12,
                   shimmer=0.35, rasp=0.55)
        r = V.mouth(r, [(0.0, "aw"), (d, "oh")], 0.55, wobble=0.05, rng=rng)
        g = throat(V.track([(0.0, 100.0), (0.5, 116.0), (d, 80.0)], n, log=True), rng, oq=0.4, jitter=0.05,
                   shimmer=0.2, rasp=0.4)
        g = V.mouth(g, [(0.0, "aw"), (d, "oh")], 0.6, wobble=0.04, rng=rng)
        trem, _ph = wobble(n, 9.0, rng, 1.5)
        pulses = S.lowpass(np.abs(r), 60.0)
        pulses /= np.max(pulses) + 1e-9
        hiss = S.bandpass(S.noise(n, rng), 500.0, 2400.0) * pulses
        f = V.track([(0.0, 50.0), (0.5, 58.0), (d, 40.0)], n, log=True)
        chest = (np.sin(2 * math.pi * np.cumsum(f) / S.SR) * (0.4 + 0.6 * pulses)
                 + 0.25 * norm(S.lowpass(S.noise(n, rng, "brown"), 110.0)))
        voice = ((norm(r) + 0.8 * norm(g) + 0.25 * norm(hiss) + 0.9 * norm(chest)) * (0.75 + 0.25 * trem)
                 * env([(0.0, 0.0), (0.12, 1.0), (1.2, 0.8), (d, 0.0)], d))
        clack = S.knock(rng, freqs=(380, 1050, 2400), qs=(14, 18, 22), gains=(1.0, 0.6, 0.3), dur=0.4, decay=0.08)
        ring = S.clink(rng, base=760.0, ratios=(1.0, 1.71, 2.43), dur=0.5, decay=0.12)
        x = S.place(2.0, (0.0, voice), (1.45, norm(clack) * 0.9), (1.45, norm(ring) * 0.25))
        return limit(valley(x, rng, rt60=1.6, mix=0.3, echoes=((0.4, 0.12),)), 0.4)
    if kind == 2:          # a short squawk, in its deep voice (the user, October 27: "make rattle 2 deeper")
        d = 0.9
        x = kaiju_cry(rng, d, [(0.0, 200), (0.12, 360), (0.5, 330), (d, 170)],
                      [(0.0, 0.0), (0.05, 1.0), (0.55, 0.8), (d, 0.0)], [(0.0, "ah"), (d, "aw")],
                      warble=(15.0, 0.035, 0.3), square=1.0, rasp=0.55, lead=0.08, **DEEP)
        return limit(valley(x, rng, rt60=2.0, mix=0.4, echoes=((0.4, 0.2), (0.85, 0.08))), 0.8)
    # a slow furnace breath, embers hissing off its hide, its beak clicking
    d = 2.0
    n = S.n_of(d)
    e = env([(0.0, 0.0), (0.7, 1.0), (1.6, 0.6), (d, 0.0)], d)
    breath = V.aspirate(d, [(0.0, "oh"), (d, "ah")], rng, scale=0.5, lo=120.0) * e
    furnace = S.lowpass(S.noise(n, rng, "brown"), 140.0) * e
    hiss = S.grains(rng, 1.8, 150.0, grain=(0.002, 0.01), lo=2500.0, hi=8000.0, level=1.0)
    pops = S.grains(rng, 1.8, 25.0, grain=(0.002, 0.006), lo=1000.0, hi=4000.0, level=1.0)
    clicks = [(1.5 + 0.2 * i, norm(S.knock(rng, freqs=(520, 1400, 3000), qs=(14, 16, 18), gains=(1.0, 0.5, 0.25),
                                            dur=0.2, decay=0.04)) * 0.6) for i in range(2)]
    x = S.place(d + 0.3, (0.0, norm(breath) * 0.9), (0.0, norm(furnace) * 0.8), (0.1, norm(hiss) * 0.12),
                (0.1, norm(pops) * 0.12), *clicks)
    return limit(valley(x, rng, rt60=1.4, mix=0.25, echoes=()), 0.6)


def ed_flap(rng, kind, size=1.0):
    """One beat of its great wings: the air shoved down in a deep whump, the membrane snapping taut, its trailing edge
    fluttering, the air rushing after."""
    k = size
    d = 1.25 * k ** 0.6
    n_fl = S.n_of(0.4 * k ** 0.5)
    tr, _ph = wobble(n_fl, 22.0 / k ** 0.5, rng, 1.2)
    flutter = (S.bandpass(S.noise(n_fl, rng), 70.0 / k, 350.0 / k) * (0.5 + 0.5 * tr)
               * env([(0.0, 1.0), (0.4 * k ** 0.5, 0.0)], 0.4 * k ** 0.5))
    rush = S.whoosh(rng, d * 0.95, 160.0 / k, 45.0 / k, amp_pts=[(0.0, 0.0), (d * 0.3, 1.0), (d * 0.95, 0.0)],
                    color="brown", width=0.9)
    x = S.place(d + 0.2, (d * 0.18, whump(rng, k * 1.15, 0.9 * k ** 0.5) * 1.2), (d * 0.3, membrane(rng, k * 1.6) * 0.55),
                (d * 0.32, norm(flutter) * (0.22 if kind == 1 else 0.14)), (0.0, norm(rush) * 0.6))
    return limit(valley(x, rng, rt60=1.2 * k ** 0.5, mix=0.3, echoes=()), 0.4)


def ed_gust(rng, kind):
    """The wing gust that sends out fire tornadoes: both wings slamming forward, a huge whoosh, and a roar of flame."""
    d = 2.4
    n = S.n_of(d)
    wind = S.whoosh(rng, d, 70.0, 480.0 if kind == 1 else 360.0,
                    amp_pts=[(0.0, 0.0), (0.3, 1.0), (0.9, 0.6), (d, 0.0)], color="brown", width=0.9)
    turb = S.lowpass(rng.standard_normal(n), 9.0)
    turb = 1.0 + 0.6 * turb / (np.max(np.abs(turb)) + 1e-9)
    flicker = S.lowpass(rng.standard_normal(n), 18.0)
    flicker = 1.0 + 0.5 * flicker / (np.max(np.abs(flicker)) + 1e-9)
    fe = env([(0.0, 0.0), (0.25, 0.0), (0.42, 1.0), (1.7 if kind == 1 else 1.95, 0.85), (d, 0.0)], d)
    flame = (norm(S.lowpass(S.noise(n, rng, "brown"), 420.0)) * turb
             + 0.4 * norm(S.bandpass(S.noise(n, rng, "pink"), 80.0, 600.0)) * flicker) * fe
    t = np.arange(S.n_of(0.5)) / S.SR
    ignite = (S.tone(40.0 + 100.0 * np.exp(-t / 0.08)) * np.exp(-t / 0.15)
              + 0.6 * norm(S.lowpass(S.burst(0.5, rng, 0.06), 600.0)))
    crackle = S.grains(rng, 1.9, 90.0, grain=(0.002, 0.008), lo=900.0, hi=5000.0, level=1.0)
    x = S.place(d + 0.3, (0.0, whump(rng, 1.0, 0.9) * 1.1), (0.04, membrane(rng, 1.4) * 0.6), (0.0, norm(wind) * 0.9),
                (0.0, norm(flame) * 0.9), (0.22, norm(ignite) * 0.8), (0.3, norm(crackle) * 0.06))
    return limit(valley(x, rng, rt60=2.2, mix=0.4, echoes=((0.45, 0.18),)), 0.6)


DIVE_PULL = 1.45       # the dive's flare: its animation pulls out 1.55 s in, the sound starts 0.1 s in


def ed_dive(rng, kind):
    """Diving: the air roaring and buffeting past it, a deep howl rising, then its wings flaring open."""
    d = 2.1
    n = S.n_of(d)
    P = DIVE_PULL
    wind = S.whoosh(rng, d, 110.0, 900.0 if kind == 1 else 700.0,
                    amp_pts=[(0.0, 0.0), (1.1, 0.8), (P, 1.0), (P + 0.15, 0.5), (d, 0.0)], width=0.6)
    gusts = S.lowpass(rng.standard_normal(n), 11.0)
    gusts = 0.5 + 0.5 * np.tanh(2.0 * gusts / (np.std(gusts) + 1e-9))
    buffet = (S.lowpass(S.noise(n, rng, "brown"), 260.0) * gusts
              * env([(0.0, 0.0), (1.0, 0.7), (P, 1.0), (P + 0.2, 0.3), (d, 0.0)], d))
    top = 520.0 if kind == 1 else 420.0
    ha = [(0.0, 0.0), (0.9, 0.35), (P, 0.55), (P + 0.1, 0.2), (d, 0.0)]
    howl = (S.chirp(d, [(0.0, 140.0), (P, top), (d, top * 0.6)], rng, harmonics=((1, 1.0), (2, 0.35), (3, 0.15)),
                    amp_pts=ha, vib=(6.0, 0.02))
            + 0.5 * S.chirp(d, [(0.0, 210.0), (P, top * 1.5), (d, top * 0.9)], rng, harmonics=((1, 1.0), (2, 0.2)),
                            amp_pts=ha, vib=(6.5, 0.02)))
    x = S.place(d + 0.4, (0.0, norm(wind) * 1.0), (0.0, norm(buffet) * 0.8), (0.0, norm(howl) * 0.8),
                (P - 0.05, whump(rng, 1.0, 0.8) * 1.0), (P, membrane(rng) * 0.9))
    return limit(valley(x, rng, rt60=1.8, mix=0.3, echoes=()), 0.45)


SNAP_HIT = 0.15        # the beak shuts this far in (its animation's hit, 0.5 s, less the cue's 0.35 s)


def ed_snap(rng, kind):
    """Its obsidian beak snapping shut: a hard, glassy clack ringing, and the weight of the jaw behind it."""
    rush = S.whoosh(rng, 0.2, 140.0, 520.0, amp_pts=[(0.0, 0.0), (0.16, 1.0), (0.2, 0.0)], color="brown")
    clack = S.knock(rng, freqs=(310, 860, 1850, 3500) if kind == 1 else (260, 720, 1600, 3100), qs=(22, 30, 36, 40),
                    gains=(1.0, 0.8, 0.5, 0.3), dur=0.7, decay=0.2)
    ring = S.clink(rng, base=720.0 if kind == 1 else 610.0, ratios=(1.0, 1.73, 2.41, 3.37), dur=0.8, decay=0.25)
    jaw = boom(rng, 85.0, 38.0, 0.6, 0.25)
    x = S.place(0.9, (0.0, norm(rush) * 0.35), (SNAP_HIT, norm(clack) * 1.0), (SNAP_HIT, norm(ring) * 0.6),
                (SNAP_HIT, norm(jaw) * 0.8), (SNAP_HIT, norm(S.crack(rng, 0.08)) * 0.3))
    return limit(valley(x, rng, rt60=1.4, mix=0.3, echoes=((0.32, 0.15),)), 0.3)


def ed_grab(rng, kind):
    """Its talons closing: a sweep of air, the talons crunching shut (rock and bone cracking), the weight behind them, a
    heavy scrape and the claws' clicks. Version 2: a deeper crunch of more pieces, a heavier weight."""
    k, pieces = (1.0, 5) if kind == 1 else (0.8, 7)       # (version 1 exactly as the user liked it)
    sweep = S.whoosh(rng, 0.12, 200.0, 600.0, amp_pts=[(0.0, 0.0), (0.1, 1.0), (0.12, 0.0)])
    crunch = S.place(0.6, *[(0.03 * i + rng.uniform(0.0, 0.012),
                             norm(S.knock(rng, freqs=(rng.uniform(160, 260) * k, rng.uniform(450, 700) * k,
                                                      rng.uniform(1100, 1600) * k, rng.uniform(2400, 3200) * k),
                                          qs=(5, 7, 9, 11), gains=(1.0, 0.7, 0.45, 0.25), dur=0.4, decay=0.06)) * 0.8 ** i)
                            for i in range(pieces)])
    weight = boom(rng, 70.0 * k, 34.0 * k, 0.7, 0.2)
    scrape = (S.grains(rng, 0.55, 300.0, grain=(0.002, 0.01), lo=500.0, hi=3500.0, level=1.0)
              * env([(0.0, 1.0), (0.55, 0.0)], 0.55))
    clicks = [(0.04 + 0.025 * i, norm(S.knock(rng, freqs=(1400, 3300), qs=(12, 14), gains=(1.0, 0.4), dur=0.06,
                                               decay=0.012)) * 0.25) for i in range(3)]
    x = S.place(1.0, (0.0, norm(sweep) * 0.3), (0.04, norm(crunch) * 1.0), (0.04, norm(weight) * 0.7),
                (0.05, norm(scrape) * 0.6), (0.05, norm(S.crack(rng, 0.08)) * 0.25), *clicks)
    return limit(valley(x, rng, rt60=1.2, mix=0.25, echoes=()), 0.3)


def ed_step(rng, kind):
    """A taloned foot the size of a cart coming down on rock: a deep weight, the ground shaking, claws and gravel."""
    pad = boom(rng, 52.0, 25.0, 0.9, 0.12)
    rumble = S.lowpass(S.noise(S.n_of(0.9), rng, "brown"), 90.0) * env([(0.0, 0.0), (0.03, 1.0), (0.9, 0.0)], 0.9)
    claws = [(0.025 * i * (1 + i), norm(S.knock(rng, freqs=(rng.uniform(1300, 1800), rng.uniform(3000, 3800)),
                                                    qs=(10, 12), gains=(1.0, 0.5), dur=0.06, decay=0.015)) * 0.18)
             for i in range(3)]
    gravel = S.grains(rng, 0.35, 160.0, grain=(0.002, 0.01), lo=350.0, hi=2500.0, level=1.0)
    x = S.place(1.0, (0.0, norm(pad) * 1.3), (0.0, norm(rumble) * 0.7), (0.02, norm(gravel) * 0.15), *claws)
    return limit(valley(x, rng, rt60=1.0, mix=0.2, echoes=()), 0.6)


def rocks(rng, dur, count, lo=0.8, level=1.0):
    """Chunks of rock falling and landing over `dur` seconds: knocks and thuds, the big ones first."""
    parts = []
    for i in range(count):
        size = rng.uniform(0.4, 1.0) * (1.0 - 0.5 * i / max(1, count))
        knock = S.knock(rng, freqs=(rng.uniform(120, 220) * lo, rng.uniform(380, 620) * lo, rng.uniform(1000, 1700)),
                        qs=(5, 7, 9), gains=(1.0, 0.6, 0.3), dur=0.35, decay=0.05 + 0.05 * size)
        parts.append((rng.uniform(0.0, dur) * (0.4 + 0.6 * i / count), norm(knock) * size * level))
    return S.place(dur + 0.4, *parts)


def ed_break(rng, kind):
    """Its stone body cracking open and breaking apart: a huge crack, stone splitting, slabs falling and thudding."""
    split = S.grains(rng, 1.4, 220.0, grain=(0.002, 0.012), lo=500.0, hi=4000.0, level=1.0)
    split *= env([(0.0, 0.2), (0.9, 1.0), (1.4, 0.0)], 1.4)
    crack = S.place(1.2, (0.0, norm(S.crack(rng, 0.12))), (0.0, 1.2 * norm(boom(rng, 70.0, 30.0, 1.2, 0.5))))
    slabs = [(1.0 + 0.25 * i + rng.uniform(0.0, 0.1), norm(boom(rng, 55.0 - 6 * i, 26.0, 0.9, 0.3)) * (1.0 - 0.15 * i))
             for i in range(4 if kind == 1 else 3)]
    x = S.place(4.0, (0.0, norm(split) * 0.5), (1.0, crack * 1.0), (1.05, norm(rocks(rng, 1.8, 18)) * 0.7), *slabs,
                (1.2, norm(S.lowpass(S.noise(S.n_of(2.2), rng, "brown"), 80.0) * env([(0.0, 1.0), (2.2, 0.0)], 2.2)) * 0.5))
    return limit(valley(x, rng, rt60=2.4, mix=0.4, echoes=((0.42, 0.18),)), 0.4)


def ey_harden(rng, kind):
    """The young hardening into obsidian: a long cooling hiss, the crackle and ticks of stone setting, a glassy ring."""
    d = 3.4
    n = S.n_of(d)
    hiss = S.highpass(S.noise(n, rng), 2200.0) * env([(0.0, 0.0), (0.15, 1.0), (1.4, 0.7), (d, 0.0)], d)
    steam = S.bandpass(S.noise(n, rng, "pink"), 500.0, 2500.0) * env([(0.0, 0.0), (0.3, 1.0), (d, 0.0)], d)
    ticks = S.place(d, *[(rng.uniform(0.2, d - 0.3), norm(S.knock(rng, freqs=(rng.uniform(2200, 4200),
                                                                                    rng.uniform(5000, 7000)),
                                                                          qs=(30, 30), gains=(1.0, 0.5), dur=0.08,
                                                                          decay=0.015)) * rng.uniform(0.2, 0.6))
                         for _ in range(40)])
    settle = norm(S.knock(rng, freqs=(180, 520, 1300), qs=(6, 8, 10), gains=(1.0, 0.6, 0.3), dur=0.6, decay=0.1))
    glass = norm(S.clink(rng, base=1900.0, ratios=(1.0, 1.62, 2.71), dur=1.2, decay=0.35))
    x = S.place(d + 0.6, (0.0, norm(hiss) * 0.5), (0.0, norm(steam) * 0.6), (0.0, norm(ticks) * 0.5),
                (d - 0.9, settle * 0.7), (d - 0.5, glass * 0.35))
    return limit(valley(x, rng, rt60=1.6, mix=0.3, echoes=()), 0.5)


# ---------------------------------------------------------------- the Pallbearer


def rattle(rng, dur, rate=60.0, level=0.6):
    """Bones knocking against each other."""
    parts, at = [], 0.0
    while at < dur:
        parts.append((at, S.knock(rng, freqs=(rng.uniform(700, 1100), rng.uniform(1800, 2600)), qs=(10, 12),
                                  gains=(1.0, 0.5), dur=0.08, decay=0.015) * rng.uniform(0.4, 1.0) * level))
        at += rng.exponential(1.0 / rate)
    return S.place(dur + 0.1, *parts)


def chains(rng, dur, level=0.5):
    parts, at = [], 0.0
    while at < dur:
        parts.append((at, S.clink(rng, base=rng.uniform(1500, 2600), dur=0.25, decay=0.05) * rng.uniform(0.3, 1.0)))
        at += rng.exponential(0.09)
    return S.place(dur + 0.3, *parts) * level


def groan(rng, dur, f0a, f0b, vowel="oo", rough=0.8):
    return S.voice(dur, [(0.0, f0a), (dur, f0b)], [(0.0, vowel), (dur, "aw")], rng, scale=0.6, rough=rough,
                   rough_hz=24.0, breath=0.4, amp_pts=[(0.0, 0.0), (0.15, 1.0), (dur * 0.7, 0.7), (dur, 0.0)])


def pb_ambient(rng, kind):
    if kind == 1:
        return S.place(2.0, (0.0, groan(rng, 1.8, 72.0, 58.0)), (0.2, rattle(rng, 1.2, 25.0, 0.4)))
    return S.place(1.6, (0.0, chains(rng, 1.2, 0.6)), (0.3, groan(rng, 1.1, 64.0, 70.0, "aw", 0.6) * 0.7))


def pb_drum(rng, kind):
    """The procession's funeral drum: three slow, deep beats, echoing."""
    beats = [(i * 0.85, S.place(0.9, (0.0, S.thud(rng, 85.0, 55.0, 0.9, 0.25)), (0.0, S.lowpass(S.burst(0.6, rng, 0.02), 400.0) * 0.6)))
             for i in range(3)]
    return S.reverb(S.place(3.0, *beats), mix=0.35, room=0.84)


def jaw_creak(rng, dur, rate=((0.0, 14.0), (1.0, 40.0)), level=1.0):
    """A jaw creaking on its hinge: stick-slip clicks, speeding up (`rate` (s, Hz) points), rung through bone."""
    n = S.n_of(dur)
    r = V.track([(t * dur, v) for t, v in rate], n)
    imp, at = np.zeros(n), 0.0
    while at < n:
        imp[int(at)] = rng.uniform(0.5, 1.0)
        at += S.SR / r[int(at)] * rng.uniform(0.8, 1.25)
    out = sum(S.resonator(imp, f, q) * g for f, q, g in ((380, 14, 1.0), (870, 18, 0.6), (1900, 22, 0.3)))
    return norm(out) * env([(0.0, 0.0), (0.05, 1.0), (dur * 0.8, 1.0), (dur, 0.0)], dur) * level


def hollow(x, f=170.0, feedback=0.55):
    """Rung through a hollow skull and ribcage: a tube's resonance (a comb filter) at `f` and its overtones."""
    d = int(round(S.SR / f))
    a = np.zeros(d + 1)
    a[0], a[d] = 1.0, -feedback
    return signal.lfilter([1.0], a, x)


def skull_voice(rng, dur, f0, vowels, rasp=0.5, breath=0.15, scale=0.6, amp=None, chest=0.0, ring=170.0):
    """A voice from an empty skull: low, rasping folds through a huge mouth, rung hollow (at `ring` Hz); `chest`: its
    deepest note, felt in the chest (the folds' own puffs carry little of it)."""
    v = norm(hollow(V.voice(dur, f0, vowels, rng, amp=amp, oq=0.4, jitter=0.03, shimmer=0.18, rasp=rasp, breath=breath,
                            scale=scale, wobble=0.05), ring))
    if chest > 0:
        n = len(v)
        ph = 2 * math.pi * np.cumsum(V.track(f0, n, log=True)) / S.SR
        under = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.4 * norm(S.lowpass(S.noise(n, rng, "brown"), 90.0))
        v = v + chest * norm(under) * (V.envelope(amp, n) if amp is not None else 1.0)
    return v


def pb_roar(rng, kind):
    """Its roar, evil and skeletal: a hollow throat, a creaking jaw, rattling bones (3 versions for the user)."""
    crypt = dict(rt60=2.4, mix=0.45, echoes=((0.15, 0.25), (0.33, 0.12)))      # a stone crypt's close walls
    if kind == 1:          # the crypt roar: the jaw creaks open, a deep hollow roar with a ghost a tritone over it
        a = [(0.0, 0.0), (0.15, 1.0), (1.8, 0.85), (2.4, 0.0)]
        roar = skull_voice(rng, 2.4, [(0.0, 45), (0.4, 62), (1.4, 58), (2.4, 40)], [(0.0, "oh"), (0.6, "ah"), (2.4, "aw")],
                           amp=a, chest=0.9, ring=110.0)
        ghost = skull_voice(rng, 2.4, [(0.0, 64), (0.4, 88), (1.4, 82), (2.4, 57)], [(0.0, "oo"), (2.4, "oh")],
                            rasp=0.2, breath=0.4, amp=a, ring=110.0)
        x = S.place(3.4, (0.0, jaw_creak(rng, 0.55) * 0.5), (0.4, roar), (0.4, ghost * 0.4),
                    (0.5, norm(rattle(rng, 2.2, 40.0)) * 0.25), (2.9, norm(S.knock(rng, freqs=(260, 700, 1500))) * 0.6))
    elif kind == 2:        # the death rattle: a gargling, rattling growl rising to a howl, teeth chattering, a hiss
        a = [(0.0, 0.0), (0.1, 0.9), (1.3, 1.0), (2.3, 0.6), (2.6, 0.0)]
        roar = skull_voice(rng, 2.6, [(0.0, 50), (0.6, 78), (1.3, 120), (2.0, 75), (2.6, 45)],
                           [(0.0, "aw"), (1.0, "ah"), (1.4, "uh"), (2.6, "aw")], rasp=0.75, amp=a, chest=0.8, ring=120.0)
        chatter = rattle(rng, 2.0, 22.0, 1.0)
        x = S.place(3.6, (0.0, jaw_creak(rng, 0.35, ((0.0, 25.0), (1.0, 60.0))) * 0.4), (0.25, roar),
                    (0.4, norm(chatter) * 0.3), (2.7, norm(V.aspirate(0.8, "ee", rng, scale=0.6)) * 0.3))
    else:                  # the demon choir: three hollow voices out of tune with each other, a whisper, the jaw shutting
        a = [(0.0, 0.0), (0.3, 1.0), (2.2, 0.8), (2.8, 0.0)]
        roar = sum(skull_voice(rng, 2.8, [(0.0, 40 * r), (0.8, 50 * r), (2.0, 46 * r), (2.8, 35 * r)], [(0.0, v), (2.8, "aw")],
                               amp=a, chest=0.9 if r == 1.0 else 0.0, ring=100.0) * g
                   for r, v, g in ((1.0, "oh", 1.0), (1.06, "ah", 0.6), (1.414, "oo", 0.45)))
        whisper = V.aspirate(2.6, [(0.0, "ee"), (1.3, "ah"), (2.6, "ee")], rng, scale=0.9, lo=900.0)
        x = S.place(3.8, (0.2, norm(roar)), (0.3, norm(whisper) * 0.25), (0.4, norm(rattle(rng, 2.4, 30.0)) * 0.22),
                    (3.0, jaw_creak(rng, 0.5, ((0.0, 40.0), (1.0, 12.0))) * 0.45))
    return limit(valley(x, rng, **crypt), 0.6)


def pb_hurt(rng, kind):
    return S.place(0.9, (0.0, groan(rng, 0.6, 95.0 + 10 * kind, 70.0, "ah", 0.9)), (0.0, rattle(rng, 0.4, 70.0, 0.7)))


def moment(mob, anim, name, default):
    """A named moment of one of the mobs' animations (anims.json, written by mobs.py), or `default` seconds."""
    try:
        with open(ANIMS, encoding="utf-8") as f:
            return json.load(f)[mob]["cues"][anim][name]
    except (OSError, KeyError, ValueError):
        return default


def censers(rng, dur, count, level=1.0):
    """His censer bells clanging as they swing and fall: small bells, out of tune with each other."""
    return S.place(dur + 1.5, *[(rng.uniform(0.0, dur), bell(rng, rng.choice((640.0, 668.0, 701.0, 745.0)), 1.4,
                                                              rng.uniform(0.4, 1.0) * level)) for _ in range(count)])


def toll(rng, base=140.0, dur=4.5):
    """A last toll of a great bell, its partner a quarter-tone flat beating against it."""
    return bell(rng, base, dur, 1.0) + bell(rng, base * 0.971, dur, 0.45)


def pb_death(rng, kind):
    """An evil Halloween death: a falling moan or wail, his bones clattering down, his censer bells out of tune, a last
    toll (3 versions for the user). The collapse lands at the animation's fall."""
    fall = moment("pallbearer", "death", "fall", 1.9)
    down = S.place(1.6, (0.0, norm(boom(rng, 50.0, 28.0, 1.2, 0.3)) * 1.0), (0.0, norm(rattle(rng, 1.2, 70.0)) * 0.6),
                   (0.05, norm(rocks(rng, 0.9, 10, lo=1.3)) * 0.4))
    if kind == 1:          # the falling wail: a ghostly voice falling away, the censers clanging, the last toll
        wail = V.howl(fall + 0.6, [(0.0, 340.0), (0.6, 360.0), (fall, 120.0), (fall + 0.6, 85.0)],
                      [(0.0, "oo"), (fall * 0.5, "ah"), (fall + 0.6, "oo")], rng, vib=(5.0, 0.03), purity=0.45, scale=0.8)
        trem, _ph = wobble(len(wail), 7.0, rng)
        x = S.place(fall + 5.0, (0.0, norm(wail) * (0.75 + 0.25 * trem)), (0.1, norm(censers(rng, fall, 6)) * 0.35),
                    (fall, down), (fall + 0.9, norm(toll(rng)) * 0.7))
    elif kind == 2:        # the bells' dirge: three tolls, each a quarter-tone flatter, over a deep moan; the last rings on
        moan = skull_voice(rng, fall + 0.4, [(0.0, 62.0), (fall + 0.4, 44.0)], [(0.0, "oo"), (fall + 0.4, "oh")],
                           rasp=0.3, breath=0.3, amp=[(0.0, 0.0), (0.3, 1.0), (fall, 0.8), (fall + 0.4, 0.0)])
        dirge = [(i * fall / 3.0, norm(bell(rng, 175.0 * 0.971 ** i, 2.0, 1.0)) * 0.45) for i in range(3)]
        x = S.place(fall + 5.0, (0.0, moan * 0.8), *dirge, (fall, down), (fall, norm(censers(rng, 0.6, 5)) * 0.4),
                    (fall + 1.0, norm(toll(rng, 150.0)) * 0.7))
    else:                  # the dying laugh: an evil cackle slowing and sinking, his censer bells jangling with it,
        # the collapse and the last toll, then the bells withering away, each fainter, slower and flatter, and a slow
        # creak as the last of them dies (the user's pick, October 28: "the dying laugh ... include the bells with that,
        # and the creaking at the end as the bells wither away")
        laugh, at = [], 0.0
        for i in range(6):
            f0 = 150.0 * 0.9 ** i
            syl = skull_voice(rng, 0.2, [(0.0, f0 * 1.1), (0.2, f0 * 0.9)], [(0.0, "ah"), (0.2, "uh")], rasp=0.45,
                              amp=[(0.0, 0.0), (0.02, 1.0), (0.12, 0.7), (0.2, 0.0)])
            laugh.append((at, syl * (1.0 - 0.06 * i)))
            laugh.append((at, norm(V.aspirate(0.05, "ah", rng)) * 0.3))
            at += 0.2 + 0.035 * i
        sigh = skull_voice(rng, 0.9, [(0.0, 70.0), (0.9, 45.0)], [(0.0, "ah"), (0.9, "oh")], rasp=0.6,
                           amp=[(0.0, 0.0), (0.1, 0.8), (0.9, 0.0)])
        wither = [(fall + w, norm(bell(rng, b, 1.6)) * lv) for w, b, lv in
                  ((0.7, 668.0, 0.5), (1.3, 652.0, 0.38), (2.1, 636.0, 0.27), (3.0, 618.0, 0.18), (4.0, 600.0, 0.1))]
        creak = jaw_creak(rng, 1.6, ((0.0, 28.0), (1.0, 7.0)), 0.45)
        x = S.place(fall + 6.4, *laugh, (at, sigh * 0.7), (0.1, norm(censers(rng, fall, 7)) * 0.35), (fall, down),
                    (fall, norm(censers(rng, 0.8, 6)) * 0.4), (fall + 0.8, norm(toll(rng, 130.0)) * 0.7), *wither,
                    (fall + 3.6, creak))
    return limit(valley(x, rng, rt60=2.6, mix=0.45, echoes=((0.18, 0.2), (0.4, 0.1))), 0.55)


def pb_step(rng, kind):
    """A heavy foot coming down in the ash: a deep thud, the bones of his foot knocking, ash crunching (much louder:
    the user, October 28: "make his foot, like his walking, much louder")."""
    x = S.place(0.6, (0.0, norm(S.thud(rng, 95.0, 60.0, 0.4, 0.3)) * 1.0),
                (0.0, norm(boom(rng, 48.0, 30.0, 0.4, 0.25)) * 0.7),
                (0.004, norm(S.knock(rng, freqs=(240, 620, 1300), qs=(8, 10, 12), gains=(1.0, 0.6, 0.3),
                                     dur=0.18, decay=0.05)) * 0.8),
                (0.01, norm(S.grains(rng, 0.25, 160.0, grain=(0.003, 0.012), lo=500.0, hi=3500.0, level=1.0)) * 0.6),
                (0.03, norm(rattle(rng, 0.25, 90.0)) * 0.35))
    return limit(x, 0.3)        # (its peaks squeezed down, so the whole step comes up loud and punchy)


def pb_swing(rng, kind):
    return S.place(0.9, (0.0, S.whoosh(rng, 0.6, 180.0, 700.0)), (0.1, chains(rng, 0.5, 0.4)))


def pb_slam(rng, kind):
    """The coffin's weight brought down: a huge thud and a burst of crackling embers."""
    return S.place(1.6, (0.0, S.thud(rng, 60.0, 32.0, 1.0, 0.4) * 1.7),
                   (0.02, S.grains(rng, 1.0, 140.0, grain=(0.002, 0.008), lo=1500.0, hi=7000.0, level=0.7)),
                   (0.0, chains(rng, 0.5, 0.5)))


# ---------------------------------------------------------------- the Cinder Hermit


def mandibles(rng, dur, rate=40.0, level=0.5):
    parts, at = [], 0.0
    while at < dur:
        parts.append((at, S.highpass(S.burst(0.02, rng, 0.0015), rng.uniform(2500, 5000)) * rng.uniform(0.4, 1.0) * level))
        at += rng.exponential(1.0 / rate)
    return S.place(dur + 0.05, *parts)


def bell(rng, base=310.0, dur=2.6, level=1.0):
    """A bronze bell struck: its hum, its strike note, partials out of tune with each other, a long ring."""
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    out = np.zeros(n)
    for r, a, dec in ((0.5, 0.6, 1.6), (1.0, 1.0, 1.1), (1.19, 0.5, 0.8), (1.5, 0.45, 0.7), (2.0, 0.35, 0.5),
                      (2.51, 0.25, 0.35), (3.0, 0.18, 0.25)):
        out += a * np.sin(2 * math.pi * base * r * t + rng.random() * 6.28) * np.exp(-t / dec)
    out *= np.clip((dur - t) / 0.6, 0.0, 1.0)           # (its ring fades out, so it never stops with a click)
    strike = S.highpass(S.burst(0.05, rng, 0.003), 2000.0) * 0.5
    return S.place(dur, (0.0, out), (0.0, strike)) * level


def hm_ambient(rng, kind):
    if kind == 1:
        return mandibles(rng, 1.0, 35.0, 0.6)
    return S.place(1.0, (0.0, mandibles(rng, 0.5, 50.0, 0.4)), (0.2, S.bubbles(rng, 0.7, 14.0, 300.0, 900.0) * 0.5))


def hm_clang(rng, kind):
    return S.reverb(bell(rng), mix=0.3, room=0.82)


def hm_snap(rng, kind):
    """The magma claw snapping shut: a rocky crack and a heavy knock."""
    rock = S.knock(rng, freqs=(220, 600, 1400), qs=(6, 8, 10), gains=(1.0, 0.6, 0.3), dur=0.35, decay=0.06)
    return S.place(0.6, (0.0, S.crack(rng, 0.1) * (0.8 if kind == 1 else 0.5)), (0.0, rock * 1.2),
                   (0.0, S.grains(rng, 0.2, 200.0, grain=(0.002, 0.006), lo=800.0, hi=4000.0, level=0.4)))


def hm_hurt(rng, kind):
    sq = S.chirp(0.35, [(0.0, 1800), (0.1, 2600), (0.35, 1400)], rng, amp_pts=[(0.0, 0.0), (0.02, 1.0), (0.35, 0.0)])
    return S.place(0.6, (0.0, sq * 0.6), (0.0, mandibles(rng, 0.4, 80.0, 0.6)))


def hm_death(rng, kind):
    clicks = mandibles(rng, 1.2, 30.0, 0.5)
    fade = clicks * np.linspace(1.0, 0.1, len(clicks))
    thunk = S.knock(rng, freqs=(160, 420, 900), qs=(5, 6, 8), gains=(1.0, 0.5, 0.2), dur=0.5, decay=0.1)
    return S.place(1.8, (0.0, fade), (1.1, thunk * 1.2), (1.1, bell(rng, 290.0, 1.4, 0.15)))


def hm_step(rng, kind):
    taps = [(i * 0.07, S.knock(rng, freqs=(1300, 3100), qs=(12, 14), gains=(1.0, 0.4), dur=0.06, decay=0.012)
             * rng.uniform(0.4, 0.9)) for i in range(3)]
    return S.place(0.3, *taps)


def hm_hide(rng, kind):
    scrape = S.grains(rng, 0.5, 220.0, grain=(0.002, 0.008), lo=600.0, hi=3500.0, level=0.6)
    thunk = S.knock(rng, freqs=(140, 380, 820), qs=(5, 6, 8), gains=(1.0, 0.5, 0.2), dur=0.5, decay=0.09)
    return S.place(1.2, (0.0, scrape), (0.6, thunk * 1.3), (0.6, bell(rng, 300.0, 1.0, 0.12)))


def hm_emerge(rng, kind):
    """Climbing out of its shell: a scrape against the inside of the shell, its legs tapping out, a hollow knock as
    the shell rocks, its mandibles clicking."""
    scrape = S.grains(rng, 0.7, 200.0, grain=(0.002, 0.008), lo=600.0, hi=3500.0, level=0.6)
    taps = [(0.3 + 0.09 * i, S.knock(rng, freqs=(1300, 3100), qs=(12, 14), gains=(1.0, 0.4), dur=0.06, decay=0.012)
             * rng.uniform(0.4, 0.9)) for i in range(6)]
    rock = S.knock(rng, freqs=(150, 400, 860), qs=(5, 6, 8), gains=(1.0, 0.5, 0.2), dur=0.5, decay=0.09)
    return S.place(1.4, (0.0, scrape), *taps, (0.75, rock * 1.1), (0.75, bell(rng, 300.0, 0.9, 0.1)),
                   (0.95, mandibles(rng, 0.4, 45.0, 0.5)))


# ---------------------------------------------------------------- the list


class Event:
    """
    One sound event: its id, the files it picks from at random, its subtitle, and where it shows in the showcase. An
    event with several versions also gets one event per version, `<event>.v<n>` (in sounds.json), so the showcase can
    play them in turn (`pick` 0, the user's request of October 27) or always one of them (`pick` n: the one in use until
    the user picks). `about`: a few words on each version (the showcase's and the page's). `seeds`: a file whose sound
    must stay exactly as it was under another name (its randomness seeded by that old "folder/name").
    """

    def __init__(self, event, folder, files, subtitle, group, label, volume=1.0, pick=0, about=None, seeds=None):
        self.event, self.folder, self.files = event, folder, files
        self.subtitle, self.group, self.label, self.volume = subtitle, group, label, volume
        self.pick, self.about, self.seeds = pick, about or [""] * len(files), seeds or {}
        assert len(self.about) == len(files), event

    @property
    def subtitle_key(self):
        return "subtitles.dross." + self.event.split(".", 1)[1]


def versions(prefix, fn, count, **kw):
    return [(f"{prefix}_{i}", (lambda r, f=fn, k=i: f(r, k, **kw))) for i in range(1, count + 1)]


ED, EY, PB, HM = "emberdactyl", "emberyoung", "pallbearer", "hermit"
EVENTS = [
    Event("entity.emberdactyl.screech", ED, versions("screech", ed_screech_deep, 3), "Emberdactyl screeches",
          "Emberdactyl", "Its screech, a kaiju's, an octave deeper than its young's (the user's pick: 1)", pick=1,
          about=["the long call: up out of a growl, held and trilling, sinking into a roar",
                 "two harsh barks, then the long scream",
                 "a rattling throat-trill breaking into a long, falling, growling roar"]),
    Event("entity.emberdactyl.ambient", ED, versions("ambient", ed_ambient, 3), "Emberdactyl rattles", "Emberdactyl",
          "A deep throaty rattle, a short deep squawk, a furnace breath with embers hissing and its beak clicking "
          "(3 versions)"),
    Event("entity.emberdactyl.flap", ED, versions("flap", ed_flap, 2), "Great wings beat", "Emberdactyl",
          "One beat of its wings: a deep whump of air, the membrane snapping taut (2 versions)"),
    Event("entity.emberdactyl.gust", ED, versions("gust", ed_gust, 2), "A gust of fire", "Emberdactyl",
          "The wing gust that sends out its fire tornadoes: a whump, a huge whoosh, a roar of flame (2 versions)"),
    Event("entity.emberdactyl.dive", ED, versions("dive", ed_dive, 2), "Emberdactyl dives", "Emberdactyl",
          "The dive: the air roaring and buffeting past it, a deep howl rising, its wings flaring open (2 versions)"),
    Event("entity.emberdactyl.snap", ED, versions("snap", ed_snap, 2), "Beak snaps", "Emberdactyl",
          "Its obsidian beak snapping shut: a glassy clack and the jaw's weight (2 versions)"),
    Event("entity.emberdactyl.grab", ED, versions("grab", ed_grab, 2), "Talons close", "Emberdactyl",
          "Its talons closing on you: a crunch, a heavy scrape (2 versions, in turn)",
          about=["the talon crunch the user liked", "a deeper crunch of more pieces, a heavier weight"],
          seeds={"grab_1": "emberdactyl/grab"}),
    Event("entity.emberdactyl.hurt", ED, versions("hurt", ed_hurt, 2), "Emberdactyl shrieks", "Emberdactyl",
          "Hurt: a short harsh shriek over a roar (2 versions)"),
    Event("entity.emberdactyl.death", ED, [("death", lambda r: ed_death(r, 1))], "Emberdactyl dies", "Emberdactyl",
          "Death: a last falling scream, the crash, a death rattle and its last breath"),
    Event("entity.emberdactyl.step", ED, [("step", lambda r: ed_step(r, 1))], "Talons on rock", "Emberdactyl",
          "A taloned foot the size of a cart on rock", volume=0.7),
    Event("entity.emberdactyl.break", ED, versions("break", ed_break, 2), "Stone breaks apart", "Emberdactyl",
          "Its body cracking open and breaking apart (its death; 2 versions, in turn)",
          about=["four slabs falling", "three slabs falling"]),
    Event("entity.emberyoung.screech", EY, [("screech", lambda r: ed_screech(r, 2, barks=2))],
          "Young Emberdactyl screeches", "Young Reborn",
          "Its screech: a harsh squawk, the same squawk again, then the long scream (the user's, October 27)",
          seeds={"screech": "emberdactyl/screech_2"}),
    Event("entity.emberyoung.hurt", EY, versions("hurt", ed_hurt, 2, pitch=1.25, size=0.62), "Young Emberdactyl shrieks",
          "Young Reborn", "Hurt (2 versions)"),
    Event("entity.emberyoung.flap", EY, versions("flap", ed_flap, 2, size=0.62), "Wings beat", "Young Reborn",
          "One quick beat of its wings (2 versions)"),
    Event("entity.emberyoung.harden", EY, [("harden", lambda r: ey_harden(r, 1))], "Obsidian hardens", "Young Reborn",
          "Hardening into obsidian as it dies: a cooling hiss, crackle and ticks, a glassy ring"),
    Event("entity.pallbearer.ambient", PB, versions("ambient", pb_ambient, 2), "Pallbearer groans", "Pallbearer",
          "A rattling groan, chains and a moan (2 versions)"),
    Event("entity.pallbearer.drum", PB, [("drum", lambda r: pb_drum(r, 1))], "A funeral drum", "Pallbearer",
          "The procession's funeral drum: three slow, deep beats"),
    Event("entity.pallbearer.roar", PB, versions("roar", pb_roar, 3), "Pallbearer cries out", "Pallbearer",
          "Its roar, evil and skeletal (the user's pick: 3, the demon choir)", pick=3,
          about=["the crypt roar: the jaw creaks open, a deep hollow roar with a ghost a tritone over it",
                 "the death rattle: a gargling growl rising to a howl, teeth chattering, a hiss",
                 "the demon choir: three hollow voices out of tune, a whisper, the jaw creaking shut"]),
    Event("entity.pallbearer.swing", PB, [("swing", lambda r: pb_swing(r, 1))], "Pallbearer swings", "Pallbearer",
          "Its backhand swing"),
    Event("entity.pallbearer.slam", PB, [("slam", lambda r: pb_slam(r, 1))], "Coffin slams down", "Pallbearer",
          "The coffin's weight slammed down, embers bursting"),
    Event("entity.pallbearer.hurt", PB, versions("hurt", pb_hurt, 2), "Pallbearer hurts", "Pallbearer",
          "Hurt: a groan and rattling bones (2 versions)"),
    Event("entity.pallbearer.death", PB, versions("death", pb_death, 3), "Pallbearer falls", "Pallbearer",
          "Death, an evil Halloween one (the user's pick: 3, the dying laugh, with the bells withering away)", pick=3,
          about=["the falling wail: a ghostly wail sinking away, his censers clanging, the last toll",
                 "the bells' dirge: three tolls each a quarter-tone flatter over a deep moan, the last ringing on",
                 "the dying laugh: an evil cackle with his bells, the collapse, the toll, the bells withering away and"
                 " a last creak"]),
    Event("entity.pallbearer.step", PB, [("step", lambda r: pb_step(r, 1))], "Heavy footsteps", "Pallbearer",
          "A heavy foot in the ash", volume=1.0),
    Event("entity.hermit.ambient", HM, versions("ambient", hm_ambient, 2), "Cinder Hermit clicks", "Cinder Hermit",
          "Clicking mandibles, a bubbling (2 versions)", volume=0.8),
    Event("entity.hermit.clang", HM, [("clang", lambda r: hm_clang(r, 1))], "A bell clangs", "Cinder Hermit",
          "Its bronze bell struck (the stunning clang)"),
    Event("entity.hermit.snap", HM, versions("snap", hm_snap, 2), "Claw snaps", "Cinder Hermit",
          "Its magma claw snapping shut (2 versions)"),
    Event("entity.hermit.hide", HM, [("hide", lambda r: hm_hide(r, 1))], "Cinder Hermit hides", "Cinder Hermit",
          "Pulling into its shell: a scrape and a hollow thunk"),
    Event("entity.hermit.emerge", HM, [("emerge", lambda r: hm_emerge(r, 1))], "Cinder Hermit climbs out",
          "Cinder Hermit", "Climbing out of its shell: a scrape, its legs tapping, the shell rocking"),
    Event("entity.hermit.hurt", HM, [("hurt", lambda r: hm_hurt(r, 1))], "Cinder Hermit squeals", "Cinder Hermit",
          "Hurt: a chittering squeal"),
    Event("entity.hermit.death", HM, [("death", lambda r: hm_death(r, 1))], "Cinder Hermit dies", "Cinder Hermit",
          "Death: its clicks fading, its shell thunking down"),
    Event("entity.hermit.step", HM, [("step", lambda r: hm_step(r, 1))], "Legs tap", "Cinder Hermit",
          "Its legs tapping the ground", volume=0.5),
]

# Which sound each animation makes and when (seconds into it, or the name of a moment its animation names: mobs.py
# writes them to anims.json; or (a moment, the seconds to use if it has none, and how far before it to start)). A loop's
# cues play every time round. An animation mobs.py hasn't made yet is left out (with a note) until it has.
CUES = {
    # (the hover is two beats long since October 30: a flap on each)
    ("emberdactyl", "hover"): [(0.3, "entity.emberdactyl.flap"), (2.5, "entity.emberdactyl.flap")],
    ("emberdactyl", "fly"): [(0.4, "entity.emberdactyl.flap")],
    ("emberdactyl", "dive"): [(0.1, "entity.emberdactyl.dive")],
    ("emberdactyl", "screech"): [("scream", "entity.emberdactyl.screech")],
    ("emberdactyl", "gust"): [("gust", "entity.emberdactyl.gust")],
    ("emberdactyl", "grab"): [("grab", "entity.emberdactyl.grab")],
    ("emberdactyl", "snap"): [(0.35, "entity.emberdactyl.snap")],
    # its peck in the air (November 1): a flap each peck, the beak's clack landing on the strike ("hit")
    ("emberdactyl", "air_snap"): [(0.12, "entity.emberdactyl.flap"),
                                  (("hit", 0.5, SNAP_HIT), "entity.emberdactyl.snap")],
    ("emberdactyl", "walk"): [(0.0, "entity.emberdactyl.step"), (1.0, "entity.emberdactyl.step")],
    # its death (the user's pick of three, October 28: it breaks apart): the scream and fall, then (its big crack 1 s
    # into the sound) the stone breaking
    ("emberdactyl", "death"): [("cry", "entity.emberdactyl.death"), (("break", 3.4, 1.0), "entity.emberdactyl.break")],
    ("emberyoung", "hover"): [(0.2, "entity.emberyoung.flap"), (1.575, "entity.emberyoung.flap")],
    ("emberyoung", "fly"): [(0.25, "entity.emberyoung.flap")],
    ("emberyoung", "dive"): [(0.05, "entity.emberdactyl.dive")],
    ("emberyoung", "screech"): [("scream", "entity.emberyoung.screech")],
    ("emberyoung", "gust"): [("gust", "entity.emberdactyl.gust")],
    ("emberyoung", "grab"): [("grab", "entity.emberdactyl.grab")],
    ("emberyoung", "snap"): [(0.2, "entity.emberdactyl.snap")],
    ("emberyoung", "air_snap"): [(0.11, "entity.emberyoung.flap"),
                                 (("hit", 0.3125, SNAP_HIT), "entity.emberdactyl.snap")],
    ("emberyoung", "death"): [(("cry", 0.125, 0.0), "entity.emberyoung.hurt"),
                              (("harden", 1.0, 0.0), "entity.emberyoung.harden")],
    ("pallbearer", "idle"): [],
    ("pallbearer", "walk"): [(0.0, "entity.pallbearer.step"), (1.3, "entity.pallbearer.step"),
                             (0.0, "entity.pallbearer.drum")],
    ("pallbearer", "swipe"): [(0.6, "entity.pallbearer.swing")],
    ("pallbearer", "slam"): [("hit", "entity.pallbearer.slam")],
    ("pallbearer", "roar"): [("roar", "entity.pallbearer.roar")],
    ("pallbearer", "death"): [(0.0, "entity.pallbearer.death")],
    ("hermit", "walk"): [(0.0, "entity.hermit.step"), (0.5, "entity.hermit.step")],
    ("hermit", "snap"): [("hit", "entity.hermit.snap")],
    ("hermit", "pinch"): [("hit", "entity.hermit.snap"), ("hit2", "entity.hermit.snap")],
    ("hermit", "hide"): [(0.1, "entity.hermit.hide")],
    ("hermit", "peek"): [(0.2, "entity.hermit.ambient")],
    ("hermit", "come_out"): [(("out", 0.0, 0.0), "entity.hermit.emerge")],
    ("hermit", "death"): [(0.0, "entity.hermit.death")],
}
OURS = ("entity.emberdactyl.", "entity.emberyoung.", "entity.pallbearer.", "entity.hermit.")


# ---------------------------------------------------------------- writing it all


def make(event, name, maker):
    """The sound's samples (the same every run: its randomness is seeded by its name)."""
    seed = event.seeds.get(name, f"{event.folder}/{name}")
    rng = np.random.default_rng(zlib.crc32(seed.encode()))
    return S.finish(maker(rng)).astype(np.float32)


def render(event, name, maker):
    """Makes the sound and writes it, unless the file there already holds exactly these samples (its Ogg comment keeps
    their fingerprint). Returns the samples and whether the file was written."""
    x = make(event, name, maker)
    path = os.path.join(SOUND_DIR, event.folder, name + ".ogg")
    tag = "dross-sound sha1:" + hashlib.sha1(x.tobytes()).hexdigest()
    if os.path.exists(path):
        with soundfile.SoundFile(path) as f:
            if f.comment == tag:
                return x, False
    write_ogg(path, x, tag)
    return x, True


def write_ogg(path, x, comment):
    """
    Writes mono Ogg Vorbis. The encoder can push a loud, dense sound's peaks a few percent past where they were; if the
    decoded file reaches 0.97, the sound is turned down a little and written again, so it never clips when it plays.
    """
    for _ in range(4):
        with soundfile.SoundFile(path, "w", S.SR, 1, format="OGG", subtype="VORBIS") as f:
            f.comment = comment
            f.write(x)
        peak = float(np.max(np.abs(soundfile.read(path, dtype="float32")[0])))
        if peak <= 0.97:
            return
        x = x * (0.93 / peak)


def write_sounds_json():
    """Adds (or replaces) this tool's events in sounds.json, keeping every other entry exactly as it was."""
    with open(SOUNDS_JSON, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    lines = text.rstrip().rstrip("}").rstrip().split("\n")
    keep, skipping, depth = [], False, 0
    for line in lines:
        stripped = line.strip()
        if not skipping and depth == 0 and any(stripped.startswith('"' + o) for o in OURS):
            skipping = True
        if skipping:
            depth += line.count("{") - line.count("}")
            if depth <= 0 and line.count("}") > 0:
                skipping, depth = False, 0
            continue
        keep.append(line)
    body = "\n".join(keep).rstrip().rstrip(",")
    entries = []

    def entry(key, ev, names):
        sounds = ",\n".join(f'      {{"name": "dross:{ev.folder}/{name}"' + (f', "volume": {ev.volume}' if ev.volume != 1.0
                                                                            else "") + "}" for name in names)
        return f'  "{key}": {{\n    "subtitle": "{ev.subtitle_key}",\n    "sounds": [\n{sounds}\n    ]\n  }}'

    for ev in EVENTS:
        names = [name for name, _m in ev.files]
        entries.append(entry(ev.event, ev, names))
        if len(names) > 1:                          # each version on its own too, to play them in turn
            entries.extend(entry(f"{ev.event}.v{i}", ev, [name]) for i, name in enumerate(names, 1))
    out = body + ",\n" + ",\n".join(entries) + "\n}\n"
    parsed = json.loads(out)
    old = {k for k in data if not k.startswith(OURS)}
    assert old <= set(parsed), "lost an entry"
    # (a key written twice makes Minecraft drop the whole file: every Dross sound would go quiet)
    keys = [line.strip().split('"')[1] for line in out.split("\n") if line.startswith('  "')]
    assert len(keys) == len(set(keys)), "a key twice in sounds.json"
    with open(SOUNDS_JSON, "w", encoding="utf-8", newline="\n") as f:
        f.write(out)


def write_lang():
    with open(LANG, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    new = {}
    for ev in EVENTS:
        if ev.subtitle_key not in data and ev.subtitle_key not in new:
            new[ev.subtitle_key] = ev.subtitle
    if not new:
        return
    body = text.rstrip()
    assert body.endswith("}")
    body = body[:-1].rstrip()
    add = ",\n".join(f"  {json.dumps(k)}: {json.dumps(v)}" for k, v in new.items())
    out = body + ",\n" + add + "\n}\n"
    json.loads(out)
    with open(LANG, "w", encoding="utf-8", newline="\n") as f:
        f.write(out)


def cue_rows():
    anims = json.load(open(ANIMS, encoding="utf-8")) if os.path.exists(ANIMS) else {}
    rows = []
    for (mob, anim), cues in CUES.items():
        moments = anims.get(mob, {}).get("cues", {}).get(anim, {})
        known = {n for n, _l, _p in anims.get(mob, {}).get("anims", [])}
        if mob in anims and anim not in known:
            print(f"  (CUES: {mob} has no animation {anim} yet: its sounds are left out until mobs.py makes it)")
            continue
        for when, event in cues:
            if isinstance(when, tuple):                  # (a moment, or this many seconds if it has none; how early)
                when = max(0.0, moments.get(when[0], when[1]) - when[2])
            elif isinstance(when, str):
                if when not in moments:
                    print(f"  (CUES: {mob} {anim} has no moment {when}: that sound is left out; run mobs.py first)")
                    continue
                when = moments[when]
            if not any(ev.event == event for ev in EVENTS):
                raise SystemExit(f"CUES: no event {event}")
            rows.append(f'            new Cue({json.dumps(mob)}, {json.dumps(anim)}, {int(round(when * 20))}, '
                        f'{json.dumps(event)})')
    return rows


def write_java():
    rows = [f'            new Sound({json.dumps(ev.group)}, {json.dumps(ev.event)}, {json.dumps(ev.label)}, {len(ev.files)}, '
            f'{ev.pick}, List.of({", ".join(json.dumps(a) for a in ev.about)}))' for ev in EVENTS]
    src = f'''package com.reg21meme.dross.boss.fire;

import java.util.List;

/**
 * Every fire mob sound (the Emberdactyl's and its young's, the Pallbearer's, the Cinder Hermit's), written by
 * {{@code tools/fire/sounds.py}} (don't edit by hand: change the sounds there and run it again). All of them are
 * original, synthesized by that tool. {{@code /dross showcase boss_sounds emberdactyl}} and
 * {{@code /dross showcase mob_sounds cinder_wastes}} list them in chat with a button that plays each one; the showcase
 * models play their animations' {{@link #CUES}}.
 */
public final class FireSounds
{{
    /**
     * One sound event.
     *
     * @param group    the mob it belongs to (the heading it's listed under)
     * @param event    its id ({{@code dross:<event>}})
     * @param label    what it is
     * @param versions how many versions it picks from at random (each also its own event, {{@code <event>.v<n>}},
     *                 when there are several)
     * @param pick     which version the showcase models play: 0 each in turn (1, 2, 3, 1...), n always version n
     * @param about    a few words on each version (may be empty)
     */
    public record Sound(String group, String event, String label, int versions, int pick, List<String> about)
    {{
        /** The event of version {{@code n}} (1 up), or the event itself when it has only one. */
        public String version(int n)
        {{
            return this.versions > 1 ? this.event + ".v" + n : this.event;
        }}

        /** The sound event {{@code event}} is, or null. */
        public static Sound of(String event)
        {{
            return ALL.stream().filter(s -> s.event().equals(event)).findFirst().orElse(null);
        }}
    }}

    /** A sound one of a mob's animations makes, {{@code tick}} ticks into it (every time round, for a loop). */
    public record Cue(String mob, String anim, int tick, String event)
    {{
    }}

    public static final List<Sound> ALL = List.of(
{",\n".join(rows)});

    public static final List<Cue> CUES = List.of(
{",\n".join(cue_rows())});

    private FireSounds() {{}}
}}
'''
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)


def write_page():
    os.makedirs(PREVIEWS, exist_ok=True)
    groups = {}
    for ev in EVENTS:
        groups.setdefault(ev.group, []).append(ev)
    html = ['<!doctype html><meta charset="utf-8"><title>Fire mob sounds</title>',
            "<style>body{font-family:system-ui,sans-serif;background:#1a1310;color:#f4ece4;margin:24px;max-width:1100px}"
            "h1{font-size:22px}h2{font-size:17px;margin-top:28px;color:#ffa046}"
            ".row{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin:6px 0;padding:6px 8px;"
            "background:#2a201b;border-radius:6px}.label{flex:1 1 280px}.id{color:#a8988a;font-size:12px}"
            "audio{height:32px}.v{display:flex;flex-direction:column;align-items:flex-start;max-width:330px}</style>",
            "<h1>The fire mobs' sounds</h1>",
            "<p>All original: made from scratch by <code>tools/fire/sounds.py</code>. Each player is one version, numbered; "
            "the showcase models play a sound's versions in turn, or the one marked *. The Emberdactyl's screech is in the spirit of a "
            "giant film pterosaur's cry (piercing, trilling, metallic, echoing across a valley) with a kaiju's "
            "roaring throat and weight under it, made from nothing. In the game: "
            "<code>/dross showcase boss_sounds emberdactyl</code> and <code>/dross showcase mob_sounds cinder_wastes"
            "</code>.</p>"]
    for group, evs in groups.items():
        html.append(f"<h2>{group}</h2>")
        for ev in evs:
            players = "".join(
                f'<div class="v"><b>{i if len(ev.files) > 1 else ""}</b>{"*" if ev.pick == i else ""} '
                f'<audio controls preload="none" src="../../../src/main/resources/assets/dross/sounds/'
                f'{ev.folder}/{name}.ogg"></audio><div class="id">{ev.about[i - 1]}</div></div>'
                for i, (name, _m) in enumerate(ev.files, 1))
            how = ("" if len(ev.files) < 2 else " (the showcase plays version %d)" % ev.pick if ev.pick
                   else " (the showcase plays them in turn)")
            html.append(f'<div class="row"><div class="label">{ev.label}<div class="id">dross:{ev.event}{how}</div>'
                        f'</div>{players}</div>')
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(html) + "\n")


def main():
    only = None                                      # folders, or "folder/name" beginnings (pallbearer/roar)
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1].split(",")
        unknown = [o for o in only if o.split("/")[0] not in (ED, EY, PB, HM)]
        if unknown:
            raise SystemExit(f"--only: no folder {unknown} (the folders: {ED}, {EY}, {PB}, {HM})")
    folders = []
    count = 0
    for ev in EVENTS:
        os.makedirs(os.path.join(SOUND_DIR, ev.folder), exist_ok=True)
        for name, maker in ev.files:
            key = f"{ev.folder}/{name}"
            if only is not None and not any(key == o or key.startswith(o + "_") or ev.folder == o for o in only):
                continue
            if ev.folder not in folders:
                folders.append(ev.folder)
            x, wrote = render(ev, name, maker)
            count += 1
            print(f"  {key}.ogg  {len(x) / S.SR:.2f} s" + ("" if wrote else "  (unchanged)"))
    for folder in folders:
        names = {n for ev in EVENTS if ev.folder == folder for n, _m in ev.files}
        for fn in os.listdir(os.path.join(SOUND_DIR, folder)):
            if fn.endswith(".ogg") and fn[:-4] not in names:
                os.remove(os.path.join(SOUND_DIR, folder, fn))
                print(f"  removed stale {folder}/{fn}")
    write_sounds_json()
    write_lang()
    write_java()
    write_page()
    print(f"{count} sounds in {len(EVENTS)} events. Done.")


if __name__ == "__main__":
    main()
