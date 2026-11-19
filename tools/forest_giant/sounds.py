"""
Makes the Silverwood Giant's sounds (QUEST_PLAN.md, "The Silverwood Giant"): its grumbles, its warning (a huff and a
stamp), its roar, hurt and death (its body crashing down folded into it), its footsteps, its blows (the smash, the
sweep, the stomp), the throw's heave, a rock torn out of the ground and the boulder's crash. Every one is synthesized
from scratch (nothing recorded, nothing from Minecraft): its voice with the Huntsman's voice synthesizer
(tools/huntsman/voicekit.py: a living voice, the folds' puffs through formant resonators), the rest with the Rat King's
synthesizer (tools/ratking/ratkit/synth.py).

Its voice (the user, November 9, 2026: "their voices need to be way deeper and monstrous and have a presence"): a
monster's, not a big man's. Its pitch 26-56 Hz (a man's is about 100); a mouth and throat twice a man's length (every
formant half a big man's: GIANT); two throats a little out of tune with each other (a choir of two reads as one huge
voice, the Emberdactyl's trick); its folds' every other puff weaker (period doubling: a growl an octave under it);
gravel, breath torn by its own puffs; the weight of its chest under it all (a tone on its pitch and a rumble); pushed
into grit; and thrown back by the forest's far hills (a valley's echoes and a long tail: `valley`, the Emberdactyl's).
Every voice event has 3 versions to pick from (the throw 3, the tear, sweep and smash 2), each also its own event.
(Until November 13, 2026 it had a `fall` sound for its own death animation; it dies the vanilla way now, so its body's
crash is in each death version, about 0.65 s in, as it tips over.)

Run from the project folder:

    python tools/forest_giant/sounds.py
    python tools/forest_giant/sounds.py --dry <folder>    renders them into <folder> only (WAV), with their numbers

It writes:
  src/main/resources/assets/dross/sounds/silverwood_giant/*.ogg   the sounds (mono Ogg Vorbis)
  src/main/resources/assets/dross/sounds.json      ADDS its own events (entity.silverwood_giant.<name>, and each
                                                   version on its own, <event>.v<n>); every other entry stays exactly
                                                   as it was; a key already there is never written twice (Minecraft
                                                   drops a sounds.json with a key twice, silencing every Dross sound)
  src/main/resources/assets/dross/lang/en_us.json  ADDS the subtitles (subtitles.dross.silverwood_giant.<name>); every
                                                   other key and the file's line endings stay as they were
  boss-previews/silverwood/mobs/forest_giant/sounds/index.html   a page that plays every version

Where each plays (for the game; the moments are in tools/forest_giant/moments.json): ambient now and then; warn at the
warning's flare and stamp at its stamp; roar when it turns on someone; hurt when hurt; death the moment it dies (its
cry, and its body hitting the ground as it tips over); step at each footfall of the walk and the run; smash, sweep and
stomp at their hits; throw at the throw's release; tear when it tears a rock out (G1's regrab, G2's and G3's tear);
boulder_hit where the thrown boulder lands. Its voice events carry an attenuation distance (Event.attenuation: how far
off they fade out, blocks; Minecraft's default is 16), so they carry further.

    python tools/forest_giant/sounds.py --dry <folder>    also prints each one's peak, its tail and where its energy lies
                                                          (under 100 Hz, under 250 Hz, 300-2000 Hz: what small speakers
                                                          play)

Needs numpy, scipy and soundfile.
"""
import hashlib
import json
import math
import os
import re
import sys
import zlib

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "ratking"))
sys.path.insert(0, os.path.join(ROOT, "tools", "huntsman"))

import numpy as np  # noqa: E402
import soundfile  # noqa: E402
from scipy import signal  # noqa: E402
from scipy.ndimage import maximum_filter1d, minimum_filter1d  # noqa: E402

from ratkit import synth as S  # noqa: E402

import voicekit as V  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
FOLDER = "silverwood_giant"
SOUND_DIR = os.path.join(ASSETS, "sounds", FOLDER)
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
PREVIEWS = os.path.join(ROOT, "boss-previews", "silverwood", "mobs", "forest_giant", "sounds")
HASHES = os.path.join(HERE, "sound_hashes.json")

GIANT = 0.5           # its mouth and throat: every formant this share of a big man's (twice as long: a monster's)
CHOIR = 14.0          # cents its second throat is out of tune with its first


# ---------------------------------------------------------------- pieces


def env(points, dur):
    return S.smooth_env(points, S.n_of(dur))


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def rms(x):
    return x / (np.sqrt(np.mean(x ** 2)) + 1e-9)


def shift(f0, cents):
    """A pitch (a number or (s, Hz) points) moved by `cents`."""
    k = 2.0 ** (cents / 1200.0)
    if isinstance(f0, (list, tuple)):
        return [(t, v * k) for t, v in f0]
    return f0 * k


def plus(v, d, hi):
    """A number or (s, value) points raised by `d`, never over `hi`."""
    if isinstance(v, (list, tuple)):
        return [(t, min(hi, x + d)) for t, x in v]
    return min(hi, v + d)


def puffs(f, sub=0.4, sharp=4.0):
    """A pulse on every period of the pitch curve `f` (Hz per sample), every other one weaker by `sub` (a growl's
    period doubling): what tears its breath into gravel."""
    ph = 2 * math.pi * np.cumsum(f) / S.SR
    p = (0.5 + 0.5 * np.cos(ph)) ** sharp
    return p * (1.0 - sub * (0.5 + 0.5 * np.cos(ph / 2.0)))


def voice(rng, dur, f0, vowels, amp, oq=0.42, rasp=0.35, breath=0.15, jitter=0.03, vib=(0.0, 0.0), hum=False,
          growl=0.5, chest=0.55, choir=0.6, drive=1.8, scale=GIANT):
    """
    Its voice, a monster's (see the top): voicekit's folds and mouth at a giant's size (`scale`), a second throat
    CHOIR cents off it (`choir`: how loud), both with every other puff weaker by `rasp` (a growl an octave under it),
    gravel (`growl`: breath torn by its puffs), pushed into grit (`drive`), and under it all the weight of its chest
    (`chest`: a tone on its pitch and a deep rumble, kept clean). f0 and every shape take (s, value) points like
    voicekit's; `amp` its loudness. Returns it at an RMS of 1.
    """
    n = S.n_of(dur)
    main = V.voice(dur, f0, vowels, rng, amp=None, oq=oq, rasp=rasp, breath=breath, jitter=jitter, shimmer=0.09,
                   scale=scale, wobble=0.05, vib=vib, hum=hum, hum_open=0.35)
    second = V.voice(dur, shift(f0, CHOIR), vowels, rng, amp=None, oq=plus(oq, 0.06, 0.72),
                     rasp=plus(rasp, 0.2, 0.85), breath=breath,
                     jitter=jitter * 1.3, shimmer=0.12, scale=scale * 0.93, wobble=0.06, vib=vib, hum=hum,
                     hum_open=0.35)
    f = V.track(f0, n, log=True)
    r = max(p[1] for p in rasp) if isinstance(rasp, list) else rasp
    grit = S.bandpass(S.noise(n, rng, "pink"), 140.0, 1700.0) * puffs(f, sub=min(0.9, r + 0.3))
    if hum:
        grit = S.lowpass(grit, 700.0)
    throat = rms(main) + choir * rms(second) + growl * rms(grit)
    throat = np.tanh(drive * throat / (np.max(np.abs(throat)) + 1e-9)) / math.tanh(drive)
    ph = 2 * math.pi * np.cumsum(f) / S.SR
    weight = rms(np.sin(ph) + 0.35 * np.sin(2 * ph)) + 0.5 * rms(S.lowpass(S.noise(n, rng, "brown"), 110.0))
    out = (rms(throat) + chest * rms(weight)) * V.envelope(amp, n)
    return rms(out)


def valley(x, rng, rt60=3.2, mix=0.42, predelay=0.07, echoes=((0.31, 0.26), (0.74, 0.14), (1.22, 0.06))):
    """
    Its voice thrown back by the forest's far hills (the Emberdactyl's valley, tools/fire/sounds.py): a few echoes off
    them, each later one quieter and duller, then after a gap (`predelay`) a long tail (`rt60` seconds to die away)
    whose highs die first. `mix`: how loud the tail is against the dry sound.
    """
    parts = [(0.0, x)]
    for k, (delay, gain) in enumerate(echoes):
        parts.append((delay, S.lowpass(x, 2600.0 / (1 + k) ** 0.6) * gain))
    dry = S.place(len(x) / S.SR, *parts)
    length = S.n_of(rt60 * 1.15)
    tt = np.arange(length) / S.SR
    ir = np.zeros(length)
    for lo, hi, k in ((20.0, 250.0, 1.0), (250.0, 1200.0, 0.8), (1200.0, 3500.0, 0.55), (3500.0, 12000.0, 0.3)):
        ir += S.bandpass(rng.standard_normal(length), lo, hi) * np.exp(-6.91 * tt / (rt60 * k))
    ir *= np.clip(tt / 0.06, 0.0, 1.0) ** 2
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = signal.fftconvolve(dry, ir)
    wet *= mix * np.sqrt(np.sum(dry ** 2) / (np.sum(wet ** 2) + 1e-12))
    pre = S.n_of(predelay)
    out = np.zeros(max(len(dry), pre + len(wet)))
    out[:len(dry)] += dry
    out[pre:pre + len(wet)] += wet
    return out


def loud(x, ceiling=0.55, release=0.08):
    """A limiter (the Emberdactyl's): its loudest instants turned down to `ceiling` of the peak, easing back over
    `release` seconds, so the whole sound comes up loud once it's scaled to its peak. It never clips."""
    a = np.abs(x) / (np.max(np.abs(x)) + 1e-12)
    r = S.n_of(release)
    g = np.minimum(1.0, ceiling / (maximum_filter1d(a, size=S.n_of(0.004) * 2 + 1) + 1e-12))
    g = minimum_filter1d(g, size=2 * r + 1)
    g = np.convolve(g, np.ones(r) / r, mode="same")
    return x * g


def hills(x, rng, rt60=3.2, mix=0.42, ceiling=0.55):
    """Its voice as it's heard: thrown back by the hills (valley), then brought up loud (loud)."""
    return loud(valley(x, rng, rt60, mix), ceiling)


def forest(x, mix=0.16):
    """In among the trees: a short, dark reverb."""
    return S.reverb(x, mix, room=0.8, damp=3000.0)


def rumble(rng, dur, level=1.0, lo=24.0, hi=90.0, attack=0.02, decay=None):
    """The ground shaking: deep brown noise."""
    n = S.n_of(dur)
    r = S.bandpass(S.noise(n, rng, "brown"), lo, hi)
    decay = decay or dur * 0.4
    t = np.arange(n) / S.SR
    return norm(r) * np.clip(t / attack, 0.0, 1.0) * np.exp(-t / decay) * level


def litter(rng, dur, level=1.0, rate=260.0):
    """The forest floor under its weight: dry leaves, moss, twigs."""
    leaves = S.grains(rng, dur, rate, grain=(0.003, 0.012), lo=900.0, hi=6000.0, level=1.0)
    out = norm(leaves) * env([(0.0, 1.0), (dur, 0.0)], dur) * 0.6
    for _ in range(int(2 + rng.integers(0, 3))):
        o = rng.uniform(0.0, dur * 0.5)
        out = S.place(dur, (0.0, out), (o, S.crack(rng, 0.05) * rng.uniform(0.1, 0.3)))[:len(out)]
    return out * level


def impact(rng, f0=52.0, f1=30.0, dur=0.8, level=1.0, click=0.25):
    """A huge weight coming down: a falling deep tone, an earthy knock in the middle (what small speakers play: the
    deep part alone was almost all under 150 Hz) and the ground answering."""
    body = norm(S.thud(rng, f0, f1, min(dur, 0.45), click))
    knock = norm(S.knock(rng, freqs=(f0 * 2.4, f0 * 4.1, f0 * 6.7), qs=(3.0, 4.0, 5.0), gains=(1.0, 0.7, 0.4),
                         dur=min(dur, 0.5), decay=0.08))
    earth = S.bandpass(S.noise(S.n_of(0.35), rng, "pink"), 180.0, 1400.0)
    earth = norm(earth) * np.exp(-np.arange(len(earth)) / S.SR / 0.06)
    return S.place(dur, (0.0, body * 0.8), (0.0, knock * 1.0), (0.0, earth * 0.8),
                   (0.0, rumble(rng, dur, 0.35, decay=dur * 0.35))) * level


def stones(rng, dur, level=1.0, rate=60.0):
    """Stones and earth tumbling: bigger, lower grains."""
    g = S.grains(rng, dur, rate, grain=(0.01, 0.04), lo=250.0, hi=2200.0, level=1.0)
    return norm(g) * env([(0.0, 0.0), (0.03, 1.0), (dur, 0.0)], dur) * level


def breath(rng, dur, vowel="ah", level=1.0, amp=None, chest=0.6):
    """A breath through its huge mouth (or, "mm", its nose), with the rush of air out of lungs the size of barrels under
    it (`chest`: a deep, rough rumble of air following the breath's loudness). Returns it at a peak of `level`."""
    n = S.n_of(dur)
    hiss = V.aspirate(dur, vowel, rng, amp=amp, scale=GIANT, lo=180.0)
    e = V.envelope(amp or [(0.0, 0.0), (dur * 0.5, 1.0), (dur, 0.0)], n)
    lungs = S.bandpass(S.noise(n, rng, "brown"), 35.0, 260.0) * e
    return norm(rms(hiss) + chest * rms(lungs)) * level


# ---------------------------------------------------------------- its voice


def ambient(rng, kind):
    if kind == 1:        # a deep grumble rolling round its chest, its mouth half shut
        d = 2.6
        v = voice(rng, d, [(0.0, 34.0), (0.7, 41.0), (1.6, 38.0), (d, 30.0)],
                  [(0.0, "mm"), (0.5, "uh"), (1.7, "oh"), (d, "mm")], oq=0.55, rasp=0.4, growl=0.45, chest=0.7,
                  amp=[(0.0, 0.0), (0.3, 0.85), (1.6, 1.0), (d, 0.0)], vib=(2.5, 0.03))
        return hills(v, rng, 2.8, 0.36)
    if kind == 2:        # a long, slow breath out of barrel-sized lungs, a growling groan in it
        d = 3.0
        air = breath(rng, d, [(0.0, "ah"), (d, "uh")], 1.0, amp=[(0.0, 0.0), (0.45, 1.0), (2.2, 0.7), (d, 0.0)],
                     chest=1.4)
        g = voice(rng, 2.0, [(0.0, 33.0), (0.8, 30.0), (2.0, 27.0)], [(0.0, "ah"), (2.0, "uh")], oq=0.62,
                  breath=0.45, rasp=0.55, growl=0.8, chest=0.85, amp=[(0.0, 0.0), (0.35, 0.8), (1.4, 0.6), (2.0, 0.0)])
        return hills(S.place(d, (0.0, rms(air) * 0.55), (0.4, g * 0.8)), rng, 2.8, 0.36)
    # a guttural "hrrrgh" through its teeth, then a huge snort
    g = voice(rng, 0.95, [(0.0, 38.0), (0.3, 46.0), (0.95, 33.0)], [(0.0, "uh"), (0.5, "aw"), (0.95, "mm")], oq=0.4,
              rasp=0.65, growl=1.0, drive=2.4, amp=[(0.0, 0.0), (0.07, 1.0), (0.6, 0.85), (0.95, 0.0)])
    snort = breath(rng, 0.45, "mm", 1.0, amp=[(0.0, 0.0), (0.03, 1.0), (0.12, 0.7), (0.45, 0.0)], chest=1.2)
    return hills(S.place(1.7, (0.0, g), (1.1, rms(snort) * 0.7)), rng, 2.8, 0.36)


def inhale(rng, dur, level=1.0):
    """A breath drawn in through a giant's nose and mouth: a rising rush of air, deep for a breath (lungs like
    barrels), its hiss rising as it fills."""
    n = S.n_of(dur)
    e = V.envelope([(0.0, 0.0), (dur * 0.8, 1.0), (dur, 0.0)], n)
    hiss = V.mouth(V.breath_noise(n, rng, 300.0), [(0.0, "oh"), (dur, "ah")], GIANT * 1.3)
    lungs = S.bandpass(S.noise(n, rng, "brown"), 40.0, 300.0)
    return norm((rms(hiss) + 0.7 * rms(lungs)) * e) * level


def warn(rng, kind):
    """Its warning: a breath drawn in, then blown out hard through its nose, a growl rolling under it (1, 2 with a
    second huff); or (3) a growl rising out of its throat, its lips curling, ending in a snort."""
    if kind == 3:
        d = 1.8
        g = voice(rng, d, [(0.0, 28.0), (0.9, 36.0), (1.5, 42.0), (d, 36.0)], [(0.0, "mm"), (0.7, "uh"), (1.4, "aw"),
                                                                              (d, "ah")],
                  oq=0.38, rasp=[(0.0, 0.5), (d, 0.75)], growl=1.1, drive=2.4, chest=0.6, vib=(7.0, 0.02),
                  amp=[(0.0, 0.0), (0.4, 0.45), (1.3, 0.9), (1.65, 1.0), (d, 0.0)])
        snort = breath(rng, 0.4, "mm", 1.0, amp=[(0.0, 0.0), (0.02, 1.0), (0.4, 0.0)], chest=1.2)
        return hills(S.place(2.4, (0.0, g), (1.75, rms(snort) * 0.6)), rng, 3.0, 0.4)
    draw = inhale(rng, 0.6)
    huff = breath(rng, 0.5, "mm", 1.0, amp=[(0.0, 0.0), (0.02, 1.0), (0.14, 0.7), (0.5, 0.0)], chest=1.3)
    huff2 = breath(rng, 0.42, "mm", 1.0, amp=[(0.0, 0.0), (0.02, 1.0), (0.42, 0.0)], chest=1.3)
    growl = voice(rng, 1.4, [(0.0, 31.0), (0.6, 34.0), (1.4, 28.0)], [(0.0, "mm"), (0.5, "uh"), (1.4, "mm")],
                  hum=True, oq=0.4, rasp=0.65, growl=1.2, jitter=0.05, drive=2.2, chest=0.8,
                  amp=[(0.0, 0.0), (0.12, 0.9), (1.0, 0.8), (1.4, 0.0)])
    parts = [(0.0, rms(draw) * 0.4), (0.6, rms(huff) * 1.0), (0.6, growl * 0.8)]
    if kind == 2:
        parts.append((1.05, rms(huff2) * 0.8))
    return hills(S.place(2.1, *parts), rng, 3.0, 0.4)


def stamp(rng, kind):
    """The warning's stamp: its foot slammed down on the forest floor."""
    return forest(S.place(1.1, (0.0, impact(rng, 60.0, 34.0, 0.9, 1.0, 0.2)), (0.0, litter(rng, 0.4, 0.5))), 0.1)


def roar(rng, kind):
    """Its angry roar as it turns on you."""
    if kind == 1:        # a huge "RAAAHR" rising out of a growl, held, shaking, falling into a growl
        d = 2.7
        v = voice(rng, d, [(0.0, 32.0), (0.3, 50.0), (1.5, 48.0), (2.1, 40.0), (d, 30.0)],
                  [(0.0, "uh"), (0.25, "ah"), (1.6, "aw"), (d, "uh")], oq=0.36,
                  rasp=[(0.0, 0.45), (1.2, 0.5), (d, 0.75)], growl=0.9, choir=0.75, drive=2.4, chest=0.6,
                  jitter=0.04, breath=0.22, amp=[(0.0, 0.0), (0.12, 1.0), (1.7, 0.9), (d, 0.0)], vib=(5.0, 0.03))
        return hills(v, rng, 3.8, 0.46, 0.5)
    if kind == 2:        # two barking roars, then the long one
        parts = []
        for i, o in enumerate((0.0, 0.55)):
            b = voice(rng, 0.45, [(0.0, 44.0 + 4 * i), (0.08, 52.0 + 4 * i), (0.45, 34.0)], [(0.0, "ah"), (0.45, "uh")],
                      oq=0.36, rasp=0.55, growl=0.9, drive=2.4, chest=0.85,
                      amp=[(0.0, 0.0), (0.03, 1.0), (0.3, 0.7), (0.45, 0.0)])
            parts.append((o, b * 0.85))
        d = 2.3
        v = voice(rng, d, [(0.0, 38.0), (0.25, 54.0), (1.3, 50.0), (d, 30.0)], [(0.0, "aw"), (0.3, "ah"), (d, "uh")],
                  oq=0.34, rasp=[(0.0, 0.45), (d, 0.75)], growl=0.9, choir=0.75, drive=2.4, chest=0.8, jitter=0.045,
                  breath=0.22,
                  amp=[(0.0, 0.0), (0.08, 1.0), (1.4, 0.85), (d, 0.0)], vib=(5.5, 0.03))
        parts.append((1.15, v))
        return hills(S.place(3.5, *parts), rng, 3.8, 0.46, 0.5)
    # a long rolling bellow, its throat trilling (a lion's purr the size of a cave), ending in a snarl
    d = 3.0
    v = voice(rng, d, [(0.0, 28.0), (0.4, 42.0), (2.0, 46.0), (d, 30.0)], [(0.0, "oh"), (0.4, "aw"), (1.6, "ah"),
                                                                         (d, "uh")],
              oq=0.38, rasp=[(0.0, 0.6), (d, 0.8)], growl=1.2, choir=0.7, drive=2.6, chest=0.7, jitter=0.05,
              amp=[(0.0, 0.0), (0.3, 0.8), (2.0, 1.0), (d, 0.0)], vib=(4.0, 0.025))
    n = len(v)
    trill = 1.0 - 0.35 * (0.5 + 0.5 * np.sin(2 * math.pi * np.cumsum(V.track([(0.0, 7.0), (d, 11.0)], n)) / S.SR))
    snarl = breath(rng, 0.7, "ah", 1.0, amp=[(0.0, 0.0), (0.05, 1.0), (0.7, 0.0)], chest=0.8)
    return hills(S.place(3.8, (0.0, v * trill), (2.7, rms(snarl) * 0.5)), rng, 3.8, 0.46, 0.5)


def hurt(rng, kind):
    if kind == 1:        # "UGH!": a deep grunt stopped dead, a growl after it
        v = voice(rng, 0.42, [(0.0, 48.0), (0.42, 34.0)], "uh", oq=0.38, rasp=0.5, growl=0.9, drive=2.4,
                  amp=[(0.0, 0.0), (0.012, 1.0), (0.3, 0.8), (0.36, 0.0), (0.42, 0.0)])
        g = voice(rng, 0.5, [(0.0, 31.0), (0.5, 27.0)], "mm", hum=True, rasp=0.7, growl=1.0,
                  amp=[(0.0, 0.0), (0.08, 0.6), (0.5, 0.0)])
        return hills(S.place(1.0, (0.0, v), (0.4, g * 0.55)), rng, 2.2, 0.36)
    if kind == 2:        # "HRRAH!": an angry, pained bark
        v = voice(rng, 0.55, [(0.0, 44.0), (0.07, 56.0), (0.55, 36.0)], [(0.0, "ae"), (0.12, "ah"), (0.55, "uh")],
                  oq=0.35, rasp=0.6, growl=1.0, drive=2.6, jitter=0.04, chest=0.85,
                  amp=[(0.0, 0.0), (0.012, 1.0), (0.38, 0.75), (0.55, 0.0)])
        return hills(v, rng, 2.2, 0.36)
    # "OOF": the wind knocked out of it, then a heavy breath back in through its teeth
    v = voice(rng, 0.36, [(0.0, 40.0), (0.36, 30.0)], [(0.0, "oo"), (0.36, "uh")], oq=0.6, breath=0.45, rasp=0.45,
              growl=0.7, amp=[(0.0, 0.0), (0.012, 1.0), (0.24, 0.6), (0.36, 0.0)])
    return hills(S.place(1.2, (0.0, rms(breath(rng, 0.1, "oo", chest=1.2)) * 0.6), (0.04, v),
                         (0.5, rms(inhale(rng, 0.5)) * 0.35)), rng, 2.2, 0.36)


def crash(rng):
    """Its body hitting the forest floor as it tips over: a great weight coming down, its arms and head after it, the
    ground shaking, leaves and twigs flying."""
    hit = impact(rng, 46.0, 24.0, 1.4, 1.0, 0.25)
    second = impact(rng, 54.0, 30.0, 0.7, 0.5, 0.15)
    return S.place(2.0, (0.0, hit), (0.0, litter(rng, 0.7, 0.6, 340.0)), (0.16, second),
                   (0.04, rumble(rng, 1.8, 0.6, decay=0.7)))


DEATH_CRASH = 0.65    # seconds into its death sound its body hits the ground (it tips over the vanilla way)


def death(rng, kind):
    """Its death (the moment it dies; the sound runs on after its body has gone): its cry, its body crashing down
    about 0.65 s in (DEATH_CRASH) as it tips over, and the end of its cry."""
    body = crash(rng)
    if kind == 1:        # a last great roar breaking into a groan, then its last breath rattling out of it
        d = 2.0
        v = voice(rng, d, [(0.0, 40.0), (0.15, 54.0), (0.6, 50.0), (1.3, 34.0), (d, 26.0)],
                  [(0.0, "uh"), (0.12, "ah"), (0.9, "aw"), (d, "oo")], oq=[(0.0, 0.36), (d, 0.65)],
                  rasp=[(0.0, 0.5), (0.8, 0.45), (d, 0.75)], breath=[(0.0, 0.18), (d, 0.5)], growl=0.9, choir=0.75,
                  drive=2.4, jitter=0.04, vib=(4.5, [(0.0, 0.01), (d, 0.05)]),
                  amp=[(0.0, 0.0), (0.06, 1.0), (0.8, 0.9), (1.5, 0.5), (d, 0.0)])
        fry = V.creak(0.7, "uh", rng, amp=[(0.0, 0.7), (0.7, 0.0)], f0=(30.0, 20.0))
        out = breath(rng, 1.4, [(0.0, "uh"), (1.4, "oo")], 1.0, amp=[(0.0, 0.0), (0.15, 1.0), (1.4, 0.0)], chest=1.0)
        mix = S.place(4.2, (0.0, v), (DEATH_CRASH, rms(body) * 1.1), (1.9, rms(fry) * 0.5), (2.5, rms(out) * 0.35))
        return hills(mix, rng, 4.2, 0.46, 0.5)
    if kind == 2:        # a long, falling wail, sinking away under the crash into a croak
        d = 2.9
        v = voice(rng, d, [(0.0, 46.0), (0.4, 56.0), (1.4, 44.0), (2.3, 30.0), (d, 24.0)],
                  [(0.0, "ah"), (0.6, "aw"), (1.8, "oh"), (d, "oo")], oq=[(0.0, 0.38), (d, 0.66)],
                  rasp=[(0.0, 0.4), (d, 0.8)], breath=[(0.0, 0.2), (d, 0.5)], growl=0.7, choir=0.8, drive=2.2,
                  jitter=0.04, vib=(3.5, [(0.0, 0.02), (d, 0.06)]),
                  amp=[(0.0, 0.0), (0.1, 1.0), (1.2, 0.8), (2.3, 0.4), (d, 0.0)])
        fry = V.creak(0.6, "oo", rng, amp=[(0.0, 0.6), (0.6, 0.0)], f0=(26.0, 19.0))
        mix = S.place(4.4, (0.0, v), (DEATH_CRASH, rms(body) * 1.1), (2.8, rms(fry) * 0.45))
        return hills(mix, rng, 4.2, 0.46, 0.5)
    # a choking roar cut off as it crashes down, then a long, deep groan and a last sigh
    r = voice(rng, 0.75, [(0.0, 42.0), (0.12, 52.0), (0.75, 44.0)], [(0.0, "uh"), (0.12, "ah"), (0.75, "aw")],
              oq=0.34, rasp=0.6, growl=1.1, drive=2.6, choir=0.75,
              amp=[(0.0, 0.0), (0.05, 1.0), (0.6, 0.95), (0.7, 0.0), (0.75, 0.0)])
    groan = voice(rng, 2.0, [(0.0, 30.0), (1.2, 27.0), (2.0, 23.0)], [(0.0, "mm"), (0.8, "uh"), (2.0, "mm")],
                  hum=True, oq=0.6, rasp=0.7, growl=0.9, chest=0.8, breath=0.4,
                  amp=[(0.0, 0.0), (0.3, 0.7), (1.4, 0.6), (2.0, 0.0)])
    sigh = breath(rng, 1.3, [(0.0, "ah"), (1.3, "uh")], 1.0, amp=[(0.0, 0.0), (0.2, 1.0), (1.3, 0.0)], chest=1.0)
    mix = S.place(4.6, (0.0, r), (DEATH_CRASH, rms(body) * 1.15), (1.0, groan * 0.7), (3.0, rms(sigh) * 0.35))
    return hills(mix, rng, 4.2, 0.46, 0.5)


# ---------------------------------------------------------------- its body


def step(rng, kind):
    """A heavy footfall on the forest floor."""
    f0 = (58.0, 64.0, 54.0)[kind - 1]
    thump = impact(rng, f0, f0 * 0.6, 0.45, 1.0, 0.12)
    return S.place(0.6, (0.0, thump), (0.005, litter(rng, 0.25, 0.75 + 0.1 * kind, 420.0)))


def smash(rng, kind):
    """The smash landing: a huge blow driven into the ground, the earth cracking, clods flying."""
    hit = impact(rng, 64.0, 30.0, 1.0, 1.0, 0.45)
    crack = S.crack(rng, 0.09) * 0.35
    parts = [(0.0, hit), (0.0, crack), (0.03, stones(rng, 0.6, 0.35)), (0.0, litter(rng, 0.4, 0.45))]
    if kind == 2:        # (its roar with the blow: a monster's, deep, torn into gravel, echoing off the hills)
        r = voice(rng, 0.7, [(0.0, 42.0), (0.06, 54.0), (0.7, 34.0)], [(0.0, "ah"), (0.7, "uh")], oq=0.34, rasp=0.6,
                  growl=1.0, drive=2.6, choir=0.75, amp=[(0.0, 0.0), (0.03, 1.0), (0.45, 0.8), (0.7, 0.0)])
        parts.append((0.0, norm(r) * 0.6))
        return hills(S.place(1.4, *parts), rng, 2.8, 0.36, 0.6)
    return forest(S.place(1.3, *parts), 0.14)


def sweep(rng, kind):
    """The sweep: a giant arm swung through the air, a deep grunt of effort behind it."""
    w = S.whoosh(rng, 0.6, 180.0 if kind == 1 else 140.0, 520.0 if kind == 1 else 420.0,
                 amp_pts=[(0.0, 0.0), (0.38, 1.0), (0.6, 0.0)], color="brown", width=0.8)
    grunt = voice(rng, 0.42, [(0.0, 46.0 if kind == 1 else 40.0), (0.42, 32.0)], [(0.0, "ah"), (0.42, "uh")],
                  oq=0.38, rasp=0.55, growl=0.9, drive=2.4, amp=[(0.0, 0.0), (0.03, 1.0), (0.28, 0.7), (0.42, 0.0)])
    return hills(S.place(1.0, (0.0, norm(w)), (0.16, norm(grunt) * 0.55)), rng, 2.0, 0.3, 0.6)


def stomp(rng, kind):
    """The stomp: its foot driven down with all its weight, a boom and the ground shaking round it."""
    boom = impact(rng, 44.0, 24.0, 1.6, 1.0, 0.3)
    return forest(S.place(2.2, (0.0, boom), (0.0, rumble(rng, 2.0, 0.8, attack=0.05, decay=0.8)),
                          (0.0, litter(rng, 0.6, 0.6, 400.0)), (0.08, stones(rng, 1.0, 0.3, 40.0))), 0.18)


def throw(rng, kind):
    """The throw: a straining heave ("HHNN-RAAH!") and the boulder rushing out of its hands (1; 2 a shorter heave;
    3 no strain, one deep grunting bark, "HUAH!"). The yell comes 0.48 s in, as the game's throw sound leads its
    release."""
    w = S.whoosh(rng, 0.7, 220.0, 120.0, amp_pts=[(0.0, 0.0), (0.12, 1.0), (0.7, 0.0)], color="brown", width=0.7)
    if kind == 3:
        bark = voice(rng, 0.55, [(0.0, 40.0), (0.06, 50.0), (0.55, 32.0)], [(0.0, "uh"), (0.08, "ah"), (0.55, "uh")],
                     oq=0.34, rasp=0.6, growl=1.1, drive=2.6, choir=0.75,
                     amp=[(0.0, 0.0), (0.02, 1.0), (0.3, 0.75), (0.55, 0.0)])
        draw = inhale(rng, 0.35)
        return hills(S.place(1.5, (0.1, rms(draw) * 0.35), (0.46, bark), (0.5, rms(w) * 0.7)), rng, 2.8, 0.36)
    strain = voice(rng, 0.55, [(0.0, 32.0), (0.55, 40.0)], "ng", hum=True, oq=0.32, rasp=0.6, growl=1.1,
                   amp=[(0.0, 0.0), (0.06, 0.7), (0.48, 1.0), (0.55, 0.0)])
    yell = voice(rng, 0.65, [(0.0, 44.0), (0.1, 54.0), (0.65, 36.0)], [(0.0, "ah"), (0.65, "uh")], oq=0.34,
                 rasp=0.55, growl=0.9, drive=2.4, choir=0.75, amp=[(0.0, 0.0), (0.02, 1.0), (0.4, 0.8), (0.65, 0.0)])
    parts = [(0.0, strain * 0.7), (0.48, yell), (0.52, rms(w) * 0.7)]
    if kind == 2:
        parts[0] = (0.2, strain[S.n_of(0.25):] * 0.55)
    return hills(S.place(1.6, *parts), rng, 2.8, 0.36)


def tear(rng, kind):
    """A rock torn out of the ground: earth ripping, roots snapping, stones grinding loose, and its strain (1 a deep,
    straining "nnnngh"; 2 a growl through its teeth)."""
    rip = S.bandpass(S.noise(S.n_of(0.6), rng, "brown"), 80.0, 900.0) * env([(0.0, 0.0), (0.1, 1.0), (0.45, 0.8),
                                                                              (0.6, 0.0)], 0.6)
    parts = [(0.0, norm(rip) * 0.8), (0.05, stones(rng, 0.6, 0.6, 80.0)), (0.0, litter(rng, 0.5, 0.4))]
    for i in range(4):
        parts.append((0.08 + i * 0.11 + rng.uniform(0.0, 0.04), S.crack(rng, 0.06) * rng.uniform(0.25, 0.5)))
    if kind == 1:
        strain = voice(rng, 0.8, [(0.0, 31.0), (0.8, 37.0)], "ng", hum=True, oq=0.32, rasp=0.6, growl=1.0,
                       amp=[(0.0, 0.0), (0.12, 0.8), (0.65, 1.0), (0.8, 0.0)])
    else:
        strain = voice(rng, 0.8, [(0.0, 30.0), (0.5, 38.0), (0.8, 33.0)], [(0.0, "mm"), (0.3, "uh"), (0.8, "uh")],
                       oq=0.36, rasp=0.75, growl=1.3, drive=2.6, amp=[(0.0, 0.0), (0.1, 0.8), (0.6, 1.0), (0.8, 0.0)])
    parts.append((0.0, norm(strain) * 0.6))
    return hills(S.place(1.2, *parts), rng, 2.4, 0.32, 0.6)


def boulder_hit(rng, kind):
    """The boulder landing: a great crash of rock, cracking, and stones and earth tumbling after."""
    hit = impact(rng, 70.0 if kind == 1 else 58.0, 34.0, 1.0, 1.0, 0.6)
    parts = [(0.0, hit), (0.0, S.crack(rng, 0.1) * 0.5), (0.02, stones(rng, 0.9, 0.55, 90.0)),
             (0.0, litter(rng, 0.5, 0.4))]
    if kind == 2:
        parts.append((0.22, impact(rng, 80.0, 46.0, 0.4, 0.35, 0.3)))   # (it bounces once)
        parts.append((0.25, stones(rng, 0.5, 0.3, 50.0)))
    return forest(S.place(1.6, *parts), 0.15)


# ---------------------------------------------------------------- the events


class Event:
    """One sound event, its versions (each its own file), its subtitle, and a line for the listening page. The game's
    event plays one of its versions at random; each version is also an event of its own, <event>.v<n>."""

    def __init__(self, name, maker, labels, subtitle, label, volume=1.0, attenuation=None):
        """attenuation: how far off it fades out to nothing (blocks; Minecraft's default, None, is 16), written into
        sounds.json as each version's "attenuation_distance" (the game also widens it by the volume it's played at,
        if over 1)."""
        self.name, self.maker, self.labels = name, maker, labels
        self.subtitle, self.label, self.volume = subtitle, label, volume
        self.attenuation = attenuation

    @property
    def event(self):
        return f"entity.silverwood_giant.{self.name}"

    @property
    def subtitle_key(self):
        return f"subtitles.dross.silverwood_giant.{self.name}"

    def files(self):
        if len(self.labels) == 1:
            return [(self.name, 1, self.labels[0])]
        return [(f"{self.name}_{i}", i, lab) for i, lab in enumerate(self.labels, start=1)]


EVENTS = [
    Event("ambient", ambient, ("a deep grumble rolling round its chest",
                               "a long, slow breath out of huge lungs, a growling groan in it",
                               "a guttural 'hrrrgh' through its teeth, then a huge snort"),
          "Giant grumbles", "Now and then", attenuation=24),
    Event("warn", warn, ("a breath drawn in, then a hard huff through its nose, a growl rolling under it",
                         "the same with a second huff", "a growl rising out of its throat, ending in a snort"),
          "Giant growls a warning", "The warning: its growl", attenuation=32),
    Event("stamp", stamp, ("its foot slammed down",), "Giant stamps", "The warning: its stamp"),
    Event("roar", roar, ("a huge 'RAAAHR' rising out of a growl, held, falling into a growl",
                         "two barking roars, then the long one",
                         "a long rolling bellow, its throat trilling, ending in a snarl"),
          "Giant roars", "When it turns on someone", attenuation=48),
    Event("hurt", hurt, ("'UGH!' and a growl after it", "'HRRAH!': an angry, pained bark",
                         "'OOF': the wind knocked out of it, a breath back in"),
          "Giant hurts", "Hurt", attenuation=24),
    Event("death", death, ("a last great roar breaking into a groan, its body crashing down, its last breath rattling "
                           "out", "a long falling wail sinking away under the crash into a croak",
                           "a choking roar cut off as it crashes down, a long deep groan, a last sigh"),
          "Giant dies", "Death (the moment it dies: its body hits the ground about 0.65 s in)", attenuation=40),
    Event("step", step, ("a heavy footfall", "a heavier one", "a softer one"), "Heavy footsteps", "Footsteps",
          volume=0.8),
    Event("smash", smash, ("a blow driven into the ground", "the same with a deep roar"), "Giant smashes",
          "The smash, as it lands"),
    Event("sweep", sweep, ("an arm swung through the air, a deep grunt", "a lower, heavier swing, a deeper grunt"),
          "Giant swings", "The sweep, as it swings"),
    Event("stomp", stomp, ("a boom and the ground shaking",), "Ground shakes", "The stomp, as it lands"),
    Event("throw", throw, ("a straining heave, a yell, the rush of the boulder", "a shorter heave",
                           "one deep grunting bark, 'HUAH!'"), "Giant heaves", "The throw, at its release",
          attenuation=32),
    Event("tear", tear, ("earth ripping, roots snapping, stones grinding, a deep straining 'nnnngh'",
                         "the same with a growl through its teeth"), "Earth tears",
          "A rock torn out of the ground", attenuation=24),
    Event("boulder_hit", boulder_hit, ("a crash of rock and stones tumbling", "the same, bouncing once"),
          "Boulder crashes", "The boulder landing"),
]


# ---------------------------------------------------------------- writing


_hashes = {}


def write(name, x):
    """A sound's file (not written again if its samples haven't changed: the encoder makes new bytes every time)."""
    path = os.path.join(SOUND_DIR, name + ".ogg")
    digest = hashlib.sha1(x.astype(np.float32).tobytes()).hexdigest()
    if os.path.exists(path) and _hashes.get(name) == digest:
        return
    soundfile.write(path, x.astype(np.float32), S.SR, format="OGG", subtype="VORBIS")
    _hashes[name] = digest


def _no_duplicates(pairs):
    seen = {}
    for k_, v in pairs:
        if k_ in seen:
            raise ValueError(f"duplicate key {k_}")
        seen[k_] = v
    return seen


def our_entries():
    """{event: entry} of every event this tool writes, in order."""
    out = {}
    for ev in EVENTS:
        files = ev.files()

        def line(name):
            d = {"name": f"dross:{FOLDER}/{name}"}
            if ev.volume != 1.0:
                d["volume"] = ev.volume
            if ev.attenuation:
                d["attenuation_distance"] = int(ev.attenuation)
            return d
        out[ev.event] = {"subtitle": ev.subtitle_key, "sounds": [line(n) for n, _i, _l in files]}
        if len(files) > 1:
            for name, i, _lab in files:
                out[f"{ev.event}.v{i}"] = {"subtitle": ev.subtitle_key, "sounds": [line(name)]}
    return out


def _detect_newline(text):
    for nl in ("\r\r\n", "\r\n", "\n"):
        if nl in text:
            return nl
    return "\n"


def write_sounds_json():
    """Adds this tool's events to sounds.json (replacing only its own, if it ran before), keeping every other entry
    exactly as it was, in its own line endings. Refuses to write a key twice."""
    with open(SOUNDS_JSON, encoding="utf-8", newline="") as f:
        text = f.read()
    nl = _detect_newline(text)
    data = json.loads(text, object_pairs_hook=_no_duplicates)
    ours = our_entries()
    mine = "entity.silverwood_giant."
    lines = text.split(nl)
    # drop our own old entries (a block from `  "entity.silverwood_giant...": {` to its closing brace)
    keep, skipping, depth = [], False, 0
    for ln in lines:
        stripped = ln.strip()
        if not skipping and depth == 0 and stripped.startswith('"' + mine):
            skipping = True
        if skipping:
            depth += ln.count("{") - ln.count("}")
            if depth <= 0 and ln.count("}") > 0:
                skipping, depth = False, 0
            continue
        keep.append(ln)
    body = nl.join(keep).rstrip()
    assert body.endswith("}"), "sounds.json doesn't end with }"
    body = body[:-1].rstrip().rstrip(",")
    blocks = []
    for key, entry in ours.items():
        snd = (", " + nl).join("      " + json.dumps(s) for s in entry["sounds"])
        blocks.append(f'  "{key}": {{{nl}    "subtitle": "{entry["subtitle"]}",{nl}    "sounds": [{nl}'
                      + (f",{nl}").join("      " + json.dumps(s) for s in entry["sounds"]) + f"{nl}    ]{nl}  }}")
    out = body + "," + nl + ("," + nl).join(blocks) + nl + "}" + nl
    parsed = json.loads(out, object_pairs_hook=_no_duplicates)
    old = {k_: v for k_, v in data.items() if not k_.startswith(mine)}
    assert all(parsed.get(k_) == v for k_, v in old.items()), "lost or changed an entry"
    assert all(parsed[k_] == v for k_, v in ours.items())
    with open(SOUNDS_JSON, "w", encoding="utf-8", newline="") as f:
        f.write(out)
    return len(ours)


def write_lang():
    """Adds the subtitles to en_us.json (replacing only its own keys), every other key and the file's line endings
    (\\r\\r\\n in this file) kept as they were. Read and written at once (another tool may be adding keys too)."""
    with open(LANG, encoding="utf-8", newline="") as f:
        text = f.read()
    nl = _detect_newline(text)
    data = json.loads(text, object_pairs_hook=_no_duplicates)
    mine = "subtitles.dross.silverwood_giant."
    want = {}
    for ev in EVENTS:
        want.setdefault(ev.subtitle_key, ev.subtitle)
    # (each line with its own ending: other tools and hands have left the file's endings mixed, \r\r\n and \r\n)
    bits = re.split(r"(\r*\n)", text)
    lines = [[bits[i], bits[i + 1] if i + 1 < len(bits) else ""] for i in range(0, len(bits), 2)]
    while lines and lines[-1][0].strip() == "":
        lines.pop()
    assert lines[-1][0].strip() == "}", "en_us.json doesn't end with }"
    close = lines[-1]
    kept = [ln for ln in lines[1:-1] if ln[0].strip() and not ln[0].strip().startswith('"' + mine)]
    for ln in kept:
        if not ln[0].rstrip().endswith(","):
            ln[0] = ln[0].rstrip() + ","
    ours = [f"  {json.dumps(k_)}: {json.dumps(v)}" for k_, v in want.items()]
    out = (lines[0][0] + lines[0][1] + "".join(c + e for c, e in kept)
           + ("," + nl).join(ours) + nl + close[0] + (close[1] or nl))
    parsed = json.loads(out, object_pairs_hook=_no_duplicates)
    old = {k_: v for k_, v in data.items() if not k_.startswith(mine)}
    assert all(parsed.get(k_) == v for k_, v in old.items()), "lost or changed a key"
    with open(LANG, "w", encoding="utf-8", newline="") as f:
        f.write(out)


def write_page(lengths):
    os.makedirs(PREVIEWS, exist_ok=True)
    src = "../../../../../src/main/resources/assets/dross/sounds/" + FOLDER
    out = ['<!doctype html><meta charset="utf-8"><title>Silverwood Giant sounds</title>',
           "<style>body{font-family:system-ui,sans-serif;background:#0c0f1c;color:#e6ecf8;margin:24px;max-width:1150px}"
           "h1{font-size:22px;color:#7fa8ff}.row{margin:8px 0;padding:8px 10px;background:#151b2e;border-radius:6px}"
           ".label{font-weight:600}.id{color:#8d98a6;font-size:12px;margin-bottom:4px}"
           ".v{display:inline-flex;flex-direction:column;margin:4px 14px 4px 0;font-size:13px;color:#c9d4df}"
           "audio{height:32px;margin-top:2px}code{color:#cfe0ff}</style>",
           "<h1>The Silverwood Giant's sounds</h1>",
           "<p>All original: made from scratch by <code>tools/forest_giant/sounds.py</code>. <b>New (November 13, "
           "2026): its voice remade way deeper and monstrous</b> (the user: \"their voices need to be way deeper and "
           "monstrous and have a presence\"): its pitch about 26-56 Hz (a man's is about 100), a mouth and throat "
           "twice a man's size, two throats a little out of tune with each other, a growl an octave under it, gravel "
           "in its throat, the weight of its chest under it all, and the forest's hills throwing it back (echoes and a "
           "long tail); its voice sounds carry further (their fade-out distance in brackets, blocks; Minecraft's "
           "default is 16). Every voice sound has three versions to pick from (the tear, the sweep and the smash two). "
           "There's no separate fall sound any more: it dies the vanilla way, and each death version has its body "
           "crashing down in it about 0.65 s in. The stamp, the steps, the stomp and the boulder's crash are as they "
           "were.</p>",
           "<p>Each player is one version; in the game an event plays one of its versions at random. Each version is "
           "also its own event (its id under it), so you can pick: tell me the numbers you like (\"roar 2, death 1 and "
           "3\").</p>"]
    for ev in EVENTS:
        reach = f" (heard to {ev.attenuation} blocks)" if ev.attenuation else ""
        many = len(ev.files()) > 1
        players = "".join(f'<span class="v"><b>{i}.</b> {lab} ({lengths.get(name, 0):.1f} s)<audio controls '
                          f'preload="none" src="{src}/{name}.ogg"></audio><span class="id">dross:{ev.event}'
                          f'{f".v{i}" if many else ""}</span></span>' for name, i, lab in ev.files())
        out.append(f'<div class="row"><div class="label">{ev.label}: "{ev.subtitle}"{reach}</div>'
                   f'<div class="id">dross:{ev.event} (any of them)</div>{players}</div>')
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")


def dry(folder):
    """Renders every sound into `folder` (WAV) and prints its length, loudness and where its energy lies; writes nothing
    in the project."""
    os.makedirs(folder, exist_ok=True)
    for ev in EVENTS:
        for name, i, _lab in ev.files():
            rng = np.random.default_rng(zlib.crc32(f"{FOLDER}/{name}".encode()))
            x = S.finish(ev.maker(rng, i))
            soundfile.write(os.path.join(folder, name + ".wav"), x.astype(np.float32), S.SR)
            spec = np.abs(np.fft.rfft(x)) ** 2
            f = np.fft.rfftfreq(len(x), 1.0 / S.SR)
            total = spec.sum()
            centroid = float((spec * f).sum() / total)
            u100 = float(spec[f < 100.0].sum() / total)
            u250 = float(spec[f < 250.0].sum() / total)
            mid = float(spec[(f >= 300.0) & (f <= 2000.0)].sum() / total)
            # its tail: from the last moment it's within 20 dB of its loudest (a 50 ms window) to its end
            k = S.n_of(0.05)
            level = np.sqrt(np.convolve(x ** 2, np.ones(k) / k, mode="same"))
            loud_at = np.where(level > 0.1 * level.max())[0]
            tail = (len(x) - loud_at[-1]) / S.SR if len(loud_at) else 0.0
            print(f"  {name:16s} {len(x) / S.SR:5.2f} s  peak {np.max(np.abs(x)):.2f}  rms {np.sqrt(np.mean(x ** 2)):.3f}"
                  f"  tail {tail:4.1f} s  centroid {centroid:5.0f} Hz  <100 {u100:4.0%}  <250 {u250:4.0%}"
                  f"  300-2000 {mid:4.0%}")


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--dry":
        dry(sys.argv[2])
        return
    os.makedirs(SOUND_DIR, exist_ok=True)
    if os.path.exists(HASHES):
        _hashes.update(json.load(open(HASHES, encoding="utf-8")))
    lengths = {}
    for ev in EVENTS:
        for name, i, _lab in ev.files():
            rng = np.random.default_rng(zlib.crc32(f"{FOLDER}/{name}".encode()))
            x = S.finish(ev.maker(rng, i))
            write(name, x)
            lengths[name] = len(x) / S.SR
            print(f"  {FOLDER}/{name}.ogg  {lengths[name]:.2f} s")
    names = {n for ev in EVENTS for n, _i, _l in ev.files()}
    for fn in os.listdir(SOUND_DIR):
        if fn.endswith(".ogg") and fn[:-4] not in names:
            os.remove(os.path.join(SOUND_DIR, fn))
            print(f"  removed stale {fn}")
    with open(HASHES, "w", encoding="utf-8", newline="\n") as f:
        json.dump({k_: v for k_, v in sorted(_hashes.items()) if k_ in names}, f, indent=1)
        f.write("\n")
    count = write_sounds_json()
    write_lang()
    write_page(lengths)
    print(f"{len(lengths)} sounds in {len(EVENTS)} events ({count} entries in sounds.json). Done.")


if __name__ == "__main__":
    main()
