"""
Makes the Rat King's and his rats' sounds: every one is synthesized from scratch (ratkit/synth.py), nothing is
recorded and nothing comes from Minecraft (the user's request, October 14, 2026). Several versions of each, so the
game can vary them, and several versions of each future attack sound, for the user to pick from.

Run from the project folder:

    python tools/ratking/sounds.py

It writes:
  src/main/resources/assets/dross/sounds/rat_king/*.ogg, rat/*.ogg, enraged_rat/*.ogg     the sounds (mono Ogg Vorbis)
  src/main/resources/assets/dross/sounds.json        adds (or replaces) its own events; the Colossus's stay as they are
  src/main/resources/assets/dross/lang/en_us.json    adds the subtitles it needs (never changes other keys)
  src/main/java/com/reg21meme/dross/boss/ratking/RatKingSounds.java   every event, for /dross showcase boss_sounds
  boss-previews/rat_king/sounds/index.html           a page that plays every sound (open it in a browser)
  boss-previews/rat_king/sounds/spectrograms.png     what each sound looks like (pitch over time)

Needs numpy, scipy, Pillow and soundfile (`pip install soundfile`: writes the .ogg files).
"""
import json
import math
import os
import re
import sys
import zlib

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
import soundfile  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from scipy import signal  # noqa: E402

from ratkit import synth as S  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
SOUND_DIR = os.path.join(ASSETS, "sounds")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "ratking", "RatKingSounds.java")
PREVIEWS = os.path.join(ROOT, "boss-previews", "rat_king", "sounds")

# Which train whistle his rage plays (1, 2 or 3; all three are in the sound showcase). The user picked 1, 'choo-choo-chooo'.
RAGE_WHISTLE = 1


# ---------------------------------------------------------------- small helpers


def env(points, dur):
    return S.smooth_env(points, S.n_of(dur))


def syll(rng, dur, f0a, f0b, vowel, vowel2=None, breath=0.3, scale=1.3, rough=0.0, rough_hz=45.0, attack=0.008,
         tilt=1.0):
    """One syllable of his voice ("heh", "hee", "mm" ...)."""
    return S.voice(dur, [(0.0, f0a), (dur, f0b)], [(0.0, vowel), (dur, vowel2 or vowel)], rng, scale=scale,
                   breath=breath, rough=rough, rough_hz=rough_hz, tilt=tilt,
                   amp_pts=[(0.0, 0.0), (attack, 1.0), (dur * 0.55, 0.75), (dur, 0.0)])


def sniff(rng, dur=0.07):
    n = S.n_of(dur)
    return S.bandpass(S.noise(n, rng), 1200.0, 6500.0) * env([(0.0, 0.0), (dur * 0.7, 1.0), (dur, 0.0)], dur)


def tick(rng, lo=2500.0, level=0.6):
    """A tiny hard click (teeth, claws on stone)."""
    return S.highpass(S.burst(0.02, rng, 0.002), lo) * level


def squeak(rng, dur, f_pts, level=1.0, vib=(0.0, 0.0), harm=((1, 1.0), (2, 0.3), (3, 0.1))):
    return S.chirp(dur, f_pts, rng, harmonics=harm, vib=vib) * level


def swarm(rng, dur, count, lo=2400.0, hi=4600.0, grow=True):
    """A crowd of rats squeaking (and scurrying), getting louder."""
    parts = []
    for _ in range(count):
        at = rng.uniform(0.0, dur - 0.1)
        f = rng.uniform(lo, hi)
        d = rng.uniform(0.04, 0.1)
        lvl = rng.uniform(0.25, 0.7) * ((0.35 + 0.65 * at / dur) if grow else 1.0)
        parts.append((at, squeak(rng, d, [(0.0, f), (d * 0.4, f * rng.uniform(1.05, 1.25)), (d, f * 0.95)], lvl)))
    scurry = S.grains(rng, dur, 140, grain=(0.002, 0.006), lo=1500.0, hi=6000.0, level=0.5)
    scurry *= np.linspace(0.3, 1.0, len(scurry)) if grow else 1.0
    return S.place(dur, (0.0, scurry), *parts)


def fm_bell(rng, dur, fc, ratio=1.41, index=3.0, decay=0.5, trem=(9.0, 0.3)):
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    mod = np.sin(2 * math.pi * fc * ratio * t) * index * np.exp(-t / (decay * 0.5))
    out = np.sin(2 * math.pi * fc * t + mod) * np.exp(-t / decay)
    return out * (1.0 + trem[1] * np.sin(2 * math.pi * trem[0] * t))


def growl(rng, dur, f0, vowel="uh", rough=0.8, rough_hz=35.0, scale=1.1, breath=0.35, f_end=None):
    return S.voice(dur, [(0.0, f0), (dur, f_end or f0 * 0.9)], [(0.0, vowel)], rng, scale=scale, breath=breath,
                   rough=rough, rough_hz=rough_hz, amp_pts=[(0.0, 0.0), (0.04, 1.0), (dur * 0.7, 0.8), (dur, 0.0)])


def hiss(rng, dur, rattle=25.0, lo=2500.0, hi=9000.0):
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    x = S.bandpass(S.noise(n, rng), lo, hi)
    am = 1.0 + 0.45 * np.sin(2 * math.pi * rattle * t) * (0.5 + 0.5 * np.sin(2 * math.pi * 1.3 * t))
    return x * am * env([(0.0, 0.0), (dur * 0.25, 1.0), (dur * 0.8, 0.85), (dur, 0.0)], dur)


# ---------------------------------------------------------------- the Rat King: voice


def king_snicker(rng):
    """Heh-heh-heh-hee: a sly snicker through his teeth."""
    parts, t = [], 0.0
    for d, a, b in ((0.085, 340.0, 310.0), (0.08, 335.0, 300.0), (0.085, 345.0, 305.0)):
        parts.append((t, syll(rng, d, a, b, "eh", breath=0.45)))
        t += d + 0.055
    parts.append((t + 0.02, syll(rng, 0.2, 430.0, 560.0, "ih", "ee", breath=0.35)))
    return S.reverb(S.place(t + 0.3, *parts), 0.08)


def king_sniff_squeak(rng):
    """Sniff, sniff... a curious squeak and a little 'hm'."""
    sq = squeak(rng, 0.24, [(0.0, 1250.0), (0.08, 1900.0), (0.24, 1550.0)], 0.6, vib=(13.0, 0.018))
    hm = syll(rng, 0.16, 210.0, 240.0, "mm", breath=0.15)
    return S.place(0.85, (0.0, sniff(rng) * 0.8), (0.12, sniff(rng, 0.06) * 0.7), (0.27, sq), (0.6, hm * 0.8))


def king_mutter(rng):
    """Mm-hm-hmm, scheming to himself, then his teeth chatter."""
    parts = [(0.0, syll(rng, 0.14, 200.0, 230.0, "mm", breath=0.1)), (0.18, syll(rng, 0.12, 240.0, 210.0, "mm", breath=0.1)),
             (0.34, syll(rng, 0.22, 225.0, 185.0, "mm", "uh", breath=0.15))]
    for i in range(7):
        parts.append((0.72 + i * 0.055, tick(rng, level=0.5 + 0.3 * rng.random())))
    return S.place(1.2, *parts)


def king_cackle(rng):
    """Kheh-kheh-kheh, falling, and a squeak of glee."""
    parts, t = [], 0.0
    for i in range(5):
        f = 520.0 - i * 25.0
        parts.append((t, syll(rng, 0.075, f, f - 40.0, "ah", breath=0.4, rough=0.35)))
        t += 0.11
    parts.append((t, squeak(rng, 0.3, [(0.0, 1500.0), (0.1, 2300.0), (0.3, 1800.0)], 0.5, vib=(9.0, 0.03))))
    return S.reverb(S.place(t + 0.4, *parts), 0.1)


def king_hurt(rng, k=1.0, kind=0):
    """A yelp: 'eek!', 'ow!' or a double yip."""
    amp = [(0.0, 0.0), (0.01, 1.0), (0.12, 0.8), (0.3, 0.0)]
    if kind == 0:
        v = S.voice(0.3, [(0.0, 650 * k), (0.04, 1050 * k), (0.3, 780 * k)], [(0.0, "ee"), (0.3, "ih")], rng, breath=0.25,
                    amp_pts=amp, direct=0.4)
    elif kind == 1:
        v = S.voice(0.32, [(0.0, 900 * k), (0.05, 1000 * k), (0.32, 520 * k)], [(0.0, "ah"), (0.32, "oo")], rng,
                    breath=0.25, amp_pts=[(0.0, 0.0), (0.01, 1.0), (0.15, 0.8), (0.32, 0.0)], direct=0.3)
    else:
        a = S.voice(0.12, [(0.0, 800 * k), (0.12, 1100 * k)], [(0.0, "ee")], rng, breath=0.3,
                    amp_pts=[(0.0, 0.0), (0.008, 1.0), (0.12, 0.0)], direct=0.5)
        v = S.place(0.3, (0.0, a), (0.14, a * 0.8))
    sq = squeak(rng, 0.2, [(0.0, 2400 * k), (0.05, 3200 * k), (0.2, 2600 * k)], 0.35)
    return S.place(0.4, (0.0, v), (0.01, sq))


def king_death(rng):
    """The silly death: a long squeal sliding down, wobbling more and more, then 'pfft'."""
    v = S.voice(1.35, [(0.0, 900.0), (0.08, 1450.0), (0.35, 1250.0), (1.1, 380.0), (1.35, 300.0)],
                [(0.0, "ee"), (0.5, "ah"), (1.35, "oo")], rng, breath=0.3, vib_rate=7.5,
                vib_pts=[(0.0, 0.0), (0.3, 0.01), (1.3, 0.05)], amp_pts=[(0.0, 0.0), (0.02, 1.0), (1.0, 0.8), (1.35, 0.0)],
                direct=0.6)
    pfft = S.lowpass(S.noise(S.n_of(0.14), rng), 2200.0) * env([(0.0, 0.0), (0.01, 1.0), (0.14, 0.0)], 0.14)
    return S.place(1.6, (0.0, v), (1.36, pfft * 0.6))


def king_startle(rng, kind=0):
    """Jolted awake: 'eep!' (or 'huh?!') after a little gasp."""
    gasp = S.bandpass(S.noise(S.n_of(0.18), rng), 800.0, 5000.0) * env([(0.0, 0.0), (0.12, 0.6), (0.18, 0.0)], 0.18)
    if kind == 0:
        v = S.voice(0.2, [(0.0, 700.0), (0.05, 1500.0), (0.2, 1300.0)], [(0.0, "ee")], rng, breath=0.3,
                    amp_pts=[(0.0, 0.0), (0.012, 1.0), (0.1, 0.7), (0.2, 0.0)], direct=0.5)
    else:
        v = S.voice(0.26, [(0.0, 380.0), (0.26, 650.0)], [(0.0, "uh"), (0.26, "ah")], rng, breath=0.35,
                    amp_pts=[(0.0, 0.0), (0.02, 1.0), (0.18, 0.8), (0.26, 0.0)])
    return S.place(0.5, (0.0, gasp * 0.5), (0.1, v))


def king_yawn(rng):
    """A big yawn, a smack of the lips and a happy little squeak."""
    v = S.voice(1.25, [(0.0, 430.0), (0.3, 540.0), (0.85, 300.0), (1.25, 230.0)], [(0.0, "ah"), (0.6, "aw"), (1.25, "oo")],
                rng, breath=0.45, amp_pts=[(0.0, 0.0), (0.15, 0.7), (0.5, 1.0), (1.0, 0.6), (1.25, 0.0)])
    smack = S.lowpass(S.burst(0.03, rng, 0.003), 1800.0)
    sq = squeak(rng, 0.12, [(0.0, 1600.0), (0.12, 2000.0)], 0.25)
    return S.place(1.8, (0.0, v), (1.33, smack * 0.6), (1.4, smack * 0.4), (1.5, sq))


def king_snore(rng):
    """One snore (his sleep loop plays one every 4 seconds): a rattling breath in, a little whistle out."""
    n_in = S.n_of(1.5)
    t = np.arange(n_in) / S.SR
    flutter = np.abs(np.sin(2 * math.pi * 26.0 * t)) ** 1.5
    rasp = S.lowpass(S.noise(n_in, rng, "pink"), 900.0) * flutter
    buzz = S.voice(1.5, [(0.0, 80.0), (1.5, 95.0)], [(0.0, "aw")], rng, scale=0.9, breath=0.6, rough=0.5, rough_hz=26.0)
    inhale = (rasp * 0.8 + buzz * 0.5) * env([(0.0, 0.0), (0.9, 0.8), (1.35, 1.0), (1.5, 0.0)], 1.5)
    wh = S.chirp(1.0, [(0.0, 1500.0), (1.0, 1050.0)], rng, harmonics=((1, 1.0), (2, 0.06)),
                 amp_pts=[(0.0, 0.0), (0.15, 0.5), (0.7, 0.45), (1.0, 0.0)], noise_mix=0.12)
    breath = S.bandpass(S.noise(S.n_of(1.0), rng), 400.0, 2500.0) * env([(0.0, 0.0), (0.2, 0.3), (1.0, 0.0)], 1.0)
    return S.place(3.3, (0.1, inhale), (2.0, wh * 0.6 + breath * 0.55))


def king_screech(rng):
    """The rage's screech: a raw, rasping rat scream."""
    v = S.voice(0.8, [(0.0, 850.0), (0.1, 1250.0), (0.5, 1150.0), (0.8, 950.0)], [(0.0, "ah"), (0.35, "ee"), (0.8, "ee")],
                rng, breath=0.55, rough=0.6, rough_hz=70.0, tilt=0.75,
                amp_pts=[(0.0, 0.0), (0.03, 1.0), (0.6, 0.85), (0.8, 0.0)], direct=0.3)
    v = np.tanh(v / (np.max(np.abs(v)) + 1e-9) * 2.5)
    sq = squeak(rng, 0.7, [(0.0, 2800.0), (0.2, 3300.0), (0.7, 2900.0)], 0.25, vib=(11.0, 0.03))
    return S.reverb(S.place(0.9, (0.0, v), (0.02, sq)), 0.12)


# ---------------------------------------------------------------- the Rat King: moves


def king_thud(rng):
    """Flat on his back like a plank: a thud, a puff of dust and a silly wobbling 'boing'."""
    th = S.thud(rng, 85.0, 45.0, 0.45, 0.35)
    n = S.n_of(0.6)
    t = np.arange(n) / S.SR
    f = 230.0 * (1.0 + 0.12 * np.sin(2 * math.pi * 13.0 * t) * np.exp(-t / 0.25))
    boing = S.tone(f, ((1, 1.0), (2, 0.2))) * np.exp(-t / 0.18) * 0.35
    dust = S.lowpass(S.grains(rng, 0.3, 120, lo=300.0, hi=1500.0), 2500.0) * 0.3
    return S.place(0.8, (0.0, th), (0.02, boing), (0.0, dust))


def staff_clatter(rng):
    """His staff clattering onto the ground and bouncing."""
    return S.place(0.5, (0.0, S.knock(rng, (420, 1100, 2300), decay=0.05)), (0.09, S.knock(rng, (450, 1150, 2400), decay=0.04) * 0.6),
                   (0.15, S.knock(rng, (430, 1120, 2350), decay=0.03) * 0.35))


def crown_clink(rng):
    """His crown landing on his belly: clink, clink-clink."""
    return S.place(0.8, (0.0, S.clink(rng, 2300.0)), (0.13, S.clink(rng, 2350.0) * 0.45), (0.22, S.clink(rng, 2320.0) * 0.2))


def staff_grab(rng):
    """Picking his staff up: a soft tap of wood."""
    pat = S.lowpass(S.burst(0.05, rng, 0.006), 900.0)
    return S.place(0.3, (0.0, S.knock(rng, (360, 950, 2100), decay=0.045) * 0.8), (0.0, pat * 0.5))


def king_step(rng, k=1.0):
    """A soft, padded footstep with a tick of claws."""
    pad = S.thud(rng, 120.0 * k, 80.0 * k, 0.18, 0.08) * 0.7
    return S.place(0.25, (0.0, pad), (0.02, tick(rng, 3000.0, 0.25)), (0.05, tick(rng, 3500.0, 0.18)))


def king_dig(rng, phase=0.0):
    """Clawing at the dirt: four scrapes a second, pebbles ticking, a low rumble of earth."""
    dur = 0.85
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    scr = S.grains(rng, dur, 220, grain=(0.003, 0.012), lo=500.0, hi=3500.0)
    rhythm = 0.35 + 0.65 * np.abs(np.sin(2 * math.pi * 4.0 * t + phase)) ** 2
    pebbles = S.grains(rng, dur, 25, grain=(0.002, 0.004), lo=3000.0, hi=7000.0) * 0.6
    rumble = S.lowpass(S.noise(n, rng, "brown"), 250.0) * 0.5
    return (scr * rhythm + pebbles + rumble) * env([(0.0, 0.0), (0.05, 1.0), (dur - 0.1, 0.9), (dur, 0.0)], dur)


def king_dive(rng):
    """Diving head first into the ground: a whoosh, then a soft plunge of earth."""
    wh = S.whoosh(rng, 0.4, 2400.0, 600.0)
    n = S.n_of(0.6)
    plunge = S.lowpass(S.noise(n, rng, "brown"), 700.0) * env([(0.0, 0.0), (0.03, 1.0), (0.6, 0.0)], 0.6)
    dirt = S.grains(rng, 0.6, 90, lo=400.0, hi=2500.0) * env([(0.0, 1.0), (0.6, 0.0)], 0.6)
    return S.place(1.1, (0.0, wh * 0.7), (0.32, plunge), (0.35, S.thud(rng, 75.0, 50.0, 0.3, 0.1) * 0.6), (0.32, dirt * 0.5))


def ground_rumble(rng):
    """The mound trembling before he pops up."""
    n = S.n_of(1.1)
    t = np.arange(n) / S.SR
    r = S.lowpass(S.noise(n, rng, "brown"), 160.0) * (1.0 + 0.6 * np.sin(2 * math.pi * 11.0 * t))
    g = S.grains(rng, 1.1, 30, lo=300.0, hi=1200.0) * 0.4
    return (r + g) * env([(0.0, 0.0), (0.3, 1.0), (0.8, 1.0), (1.1, 0.0)], 1.1)


def king_pop(rng, kind=0):
    """Popping up out of the ground: a cartoon pop, a spray of dirt and a 'ta-da' squeak."""
    n = S.n_of(0.06)
    t = np.arange(n) / S.SR
    if kind == 0:
        f = 500.0 + 1100.0 * np.exp(-t / 0.008)
        tada = squeak(rng, 0.22, [(0.0, 900.0), (0.22, 1500.0)], 0.45, vib=(10.0, 0.02))
    else:
        f = 300.0 + 700.0 * np.exp(-t / 0.012)
        tada = squeak(rng, 0.35, [(0.0, 900.0), (0.25, 2000.0), (0.35, 1900.0)], 0.4, vib=(8.0, 0.03))
    pop = S.tone(f) * np.exp(-t / 0.012)
    click = S.highpass(S.burst(0.02, rng, 0.0015), 1500.0) * 0.5
    spray = S.grains(rng, 0.45, 180, lo=300.0, hi=2500.0) * env([(0.0, 1.0), (0.45, 0.0)], 0.45)
    return S.place(0.8, (0.0, pop), (0.0, click), (0.01, spray * 0.6), (0.22, tada))


def king_stomp(rng, k=1.0):
    """A tantrum stomp: a heavy foot slammed down, a little dust."""
    th = S.thud(rng, 65.0 * k, 38.0 * k, 0.32, 0.45)
    dust = S.lowpass(S.grains(rng, 0.2, 100, lo=300.0, hi=1800.0), 2500.0) * 0.25
    return S.place(0.4, (0.0, th), (0.0, dust))


def staff_slam(rng):
    """The rage's end: his staff slammed on the ground, the orb ringing out green."""
    kn = S.knock(rng, (240, 650, 1500), (8, 10, 12), (1.0, 0.7, 0.4), dur=0.5, decay=0.1)
    th = S.thud(rng, 62.0, 40.0, 0.4, 0.3) * 0.8
    bell = fm_bell(rng, 1.2, 1180.0) * 0.35
    return S.reverb(S.place(1.3, (0.0, kn), (0.0, th), (0.02, bell)), 0.18)


def rage_whistle_1(rng):
    """'Choo-choo-chooooo': two short toots and a long blast of a three-chime train whistle, with his steam puffs."""
    w = S.whistle(rng, 2.3, (370.0, 466.2, 554.4), [(0.0, 0.3), (0.4, 0.3), (0.8, 1.2)])
    return S.reverb(w, 0.22, room=0.82)


def rage_whistle_2(rng):
    """One long, wailing blast of a five-chime steam whistle."""
    w = S.whistle(rng, 2.2, (554.4, 659.3, 830.6, 987.8, 1108.7), [(0.0, 1.75)], scoop=0.11, droop=0.07, steam=0.45,
                  breath=0.18)
    return S.reverb(w, 0.25, room=0.84)


def rage_whistle_3(rng):
    """'Too-wheet, too-wheet!': a shrill little toy-train whistle."""
    a = S.whistle(rng, 0.26, (880.0, 1108.7), [(0.0, 0.2)], scoop=0.05, steam=0.15)
    b = S.whistle(rng, 0.32, (987.8, 1244.5), [(0.0, 0.24)], scoop=0.03, steam=0.15)
    return S.reverb(S.place(1.4, (0.0, a), (0.24, b), (0.62, a), (0.86, b)), 0.15)


def scurry_rush(rng, dur=0.75):
    """Claws pattering fast on the ground (twelve steps a second, getting louder) and the air rushing past."""
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    pads = S.grains(rng, dur, 260, grain=(0.002, 0.008), lo=700.0, hi=5000.0)
    gait = 0.3 + 0.7 * np.abs(np.sin(2 * math.pi * 6.0 * t)) ** 3          # two feet: 12 footfalls a second
    rush = S.whoosh(rng, dur, 500.0, 2600.0, amp_pts=[(0.0, 0.0), (dur * 0.6, 1.0), (dur, 0.0)])
    grow = np.linspace(0.45, 1.0, n)
    return (pads * gait * grow * 0.7 + rush * 0.55) * env([(0.0, 0.0), (0.03, 1.0), (dur - 0.08, 0.9), (dur, 0.0)], dur)


def king_charge(rng, kind=1):
    """The spear charge: a war-cry squeak ('yee-YAH!', kind 1) or a rising squeal (kind 2) as he dashes, his claws
    scurrying and the air rushing past."""
    if kind == 1:
        cry = S.voice(0.5, [(0.0, 720.0), (0.12, 1050.0), (0.22, 980.0), (0.32, 1300.0), (0.5, 1150.0)],
                      [(0.0, "ee"), (0.2, "ee"), (0.28, "ah"), (0.5, "ah")], rng, breath=0.35, rough=0.25, rough_hz=60.0,
                      amp_pts=[(0.0, 0.0), (0.02, 0.8), (0.2, 0.6), (0.28, 1.0), (0.42, 0.85), (0.5, 0.0)], direct=0.45)
        sq = squeak(rng, 0.2, [(0.0, 2400.0), (0.2, 3100.0)], 0.3)
        return S.reverb(S.place(0.95, (0.0, cry), (0.27, sq), (0.12, scurry_rush(rng, 0.8))), 0.1)
    squeal = S.voice(0.55, [(0.0, 900.0), (0.25, 1500.0), (0.55, 1650.0)], [(0.0, "ee"), (0.55, "ee")], rng, breath=0.3,
                     rough=0.2, rough_hz=70.0, vib_rate=10.0, vib_pts=[(0.0, 0.0), (0.55, 0.03)],
                     amp_pts=[(0.0, 0.0), (0.03, 0.9), (0.4, 1.0), (0.55, 0.0)], direct=0.5)
    sq = squeak(rng, 0.25, [(0.0, 2600.0), (0.12, 3400.0), (0.25, 3200.0)], 0.25, vib=(12.0, 0.03))
    return S.reverb(S.place(0.95, (0.0, squeal), (0.15, sq), (0.08, scurry_rush(rng, 0.85))), 0.1)


# ---------------------------------------------------------------- the Rat King: attacks (versions to pick from later)


def attack_swipe(rng, kind):
    if kind == 1:
        wh = S.whoosh(rng, 0.3, 900.0, 3500.0)
        scrape = S.highpass(S.noise(S.n_of(0.08), rng), 4000.0) * env([(0.0, 0.0), (0.01, 1.0), (0.08, 0.0)], 0.08)
        grunt = syll(rng, 0.12, 300.0, 260.0, "ah", breath=0.5)
        return S.place(0.5, (0.0, grunt * 0.6), (0.05, wh), (0.24, scrape * 0.7))
    if kind == 2:
        a = S.whoosh(rng, 0.2, 1200.0, 3800.0)
        b = S.whoosh(rng, 0.2, 1000.0, 3200.0)
        sc = S.highpass(S.noise(S.n_of(0.06), rng), 4500.0) * env([(0.0, 0.0), (0.01, 1.0), (0.06, 0.0)], 0.06)
        return S.place(0.6, (0.0, a), (0.15, sc * 0.6), (0.2, b), (0.36, sc * 0.5))
    wh = S.whoosh(rng, 0.42, 400.0, 1600.0, color="brown")
    snarl = growl(rng, 0.35, 190.0, rough=0.7)
    return S.place(0.6, (0.0, snarl * 0.5), (0.08, wh))


def attack_bite(rng, kind):
    clack = S.bandpass(S.burst(0.03, rng, 0.003), 2500.0, 6000.0)
    crunch = S.grains(rng, 0.15, 160, lo=800.0, hi=4000.0)
    if kind == 1:
        return S.place(0.6, (0.0, growl(rng, 0.25, 220.0, rough=0.7) * 0.6), (0.25, clack), (0.28, clack * 0.6), (0.3, crunch * 0.6))
    if kind == 2:
        sq = squeak(rng, 0.1, [(0.0, 1500.0), (0.1, 2200.0)], 0.4)
        return S.place(0.4, (0.0, sq), (0.1, clack), (0.12, crunch * 0.5))
    parts = [(0.0, growl(rng, 0.5, 200.0, rough=0.6) * 0.4)]
    for i in range(3):
        parts += [(0.08 + 0.13 * i, clack * (1.0 - 0.2 * i)), (0.1 + 0.13 * i, crunch * 0.4)]
    return S.place(0.7, *parts)


def attack_staff(rng, kind):
    thwack = S.knock(rng, (230, 620, 1400), (8, 10, 12), (1.0, 0.7, 0.4), dur=0.5, decay=0.09)
    if kind == 1:
        wh = S.whoosh(rng, 0.35, 300.0, 1200.0)
        return S.place(0.9, (0.0, wh), (0.33, thwack), (0.35, fm_bell(rng, 0.5, 1400.0, decay=0.2) * 0.2))
    if kind == 2:
        wh = S.whoosh(rng, 0.45, 250.0, 900.0, amp_pts=[(0.0, 0.0), (0.4, 1.0), (0.45, 0.0)])
        return S.reverb(S.place(1.2, (0.0, wh), (0.44, thwack), (0.44, S.thud(rng, 60.0, 38.0, 0.4, 0.4)),
                                (0.46, fm_bell(rng, 0.9, 1180.0) * 0.3)), 0.15)
    a = S.whoosh(rng, 0.2, 500.0, 1500.0)
    b = S.whoosh(rng, 0.2, 1500.0, 500.0)
    return S.place(0.8, (0.0, a), (0.2, b), (0.4, thwack))


def attack_orb(rng, kind):
    if kind == 1:
        # A plague bolt: charges up, bubbling, then zaps.
        n = S.n_of(0.6)
        t = np.arange(n) / S.SR
        f = S.curve([(0.0, 300.0), (0.6, 900.0)], n, log=True)
        charge = np.sin(2 * math.pi * np.cumsum(f) / S.SR + 2.0 * np.sin(2 * math.pi * f * 1.5 * t / 1.0)) * (t / 0.6)
        bub = S.bubbles(rng, 0.6, 40, 300.0, 900.0) * 0.4
        n2 = S.n_of(0.4)
        t2 = np.arange(n2) / S.SR
        f2 = S.curve([(0.0, 2000.0), (0.4, 200.0)], n2, log=True)
        zap = np.sin(2 * math.pi * np.cumsum(f2) / S.SR) * np.exp(-t2 / 0.15)
        zap += S.highpass(S.noise(n2, rng), 1500.0) * np.exp(-t2 / 0.05)
        return S.place(1.1, (0.0, charge * 0.5 + bub), (0.6, zap))
    if kind == 2:
        # A sickly green cloud: a warbling, out-of-tune hum, bubbles and a hiss.
        n = S.n_of(1.3)
        t = np.arange(n) / S.SR
        hum = sum(np.sin(2 * math.pi * f * t + 3.0 * np.sin(2 * math.pi * 0.7 * t)) for f in (220.0, 233.0, 247.0)) / 3.0
        hum *= env([(0.0, 0.0), (0.3, 1.0), (1.0, 0.8), (1.3, 0.0)], 1.3)
        return hum * 0.6 + S.bubbles(rng, 1.3, 30) * 0.35 + hiss(rng, 1.3, 9.0, 1500.0, 6000.0) * 0.25
    # A green flash: a swell, then a bright, shimmering ring.
    n = S.n_of(0.5)
    swell = S.bandpass(S.noise(n, rng), 800.0, 5000.0) * np.linspace(0.0, 1.0, n) ** 2
    ring = sum(fm_bell(rng, 1.0, f, ratio=2.0, index=1.5, decay=0.4) for f in (1046.5, 1318.5, 1568.0)) / 3.0
    return S.reverb(S.place(1.5, (0.0, swell * 0.6), (0.5, ring), (0.5, S.burst(0.02, rng, 0.002) * 0.5)), 0.25)


def attack_summon(rng, kind):
    if kind == 1:
        # A two-finger whistle, then his rats come squeaking.
        a = S.chirp(0.22, [(0.0, 1800.0), (0.22, 2300.0)], rng, harmonics=((1, 1.0),), noise_mix=0.08)
        b = S.chirp(0.4, [(0.0, 2400.0), (0.15, 2900.0), (0.4, 2700.0)], rng, harmonics=((1, 1.0),), noise_mix=0.08)
        return S.place(2.0, (0.0, a * 0.7), (0.28, b * 0.7), (0.6, swarm(rng, 1.3, 45)))
    if kind == 2:
        # His own squeaky call, three times, rising.
        calls = [(0.25 * i, S.voice(0.18, [(0.0, 1000.0 + 150 * i), (0.18, 1250.0 + 150 * i)], [(0.0, "ee")], rng,
                                    breath=0.3, amp_pts=[(0.0, 0.0), (0.02, 1.0), (0.18, 0.0)], direct=0.5)) for i in range(3)]
        return S.place(2.0, *calls, (0.75, swarm(rng, 1.2, 40)))
    taps = [(0.2 * i, S.knock(rng, (280, 760, 1650), decay=0.06) * 0.8) for i in range(3)]
    rumble = S.lowpass(S.noise(S.n_of(1.3), rng, "brown"), 200.0) * np.linspace(0.0, 1.0, S.n_of(1.3)) * 0.6
    return S.place(2.0, *taps, (0.6, rumble), (0.6, swarm(rng, 1.3, 35, lo=2000.0, hi=4000.0)))


def attack_screech(rng, kind):
    if kind == 1:
        n = S.n_of(1.3)
        t = np.arange(n) / S.SR
        v = S.voice(1.3, [(0.0, 1100.0), (0.3, 1400.0), (1.3, 1250.0)], [(0.0, "ah"), (0.4, "ee")], rng, breath=0.5,
                    rough=0.5, rough_hz=80.0, tilt=0.7, amp_pts=[(0.0, 0.0), (0.05, 1.0), (1.0, 0.9), (1.3, 0.0)])
        v = v * (0.6 + 0.4 * np.sin(2 * math.pi * 230.0 * t))     # ring-modulated: unnatural, sonic
        return S.reverb(np.tanh(v / (np.max(np.abs(v)) + 1e-9) * 2.0), 0.3, room=0.85)
    v = S.voice(1.2, [(0.0, 600.0), (0.9, 1600.0), (1.2, 1500.0)], [(0.0, "aw"), (0.6, "ee")], rng, breath=0.45,
                rough=0.4, rough_hz=60.0, vib_rate=9.0, vib_pts=[(0.0, 0.01), (1.2, 0.06)],
                amp_pts=[(0.0, 0.0), (0.1, 0.8), (0.9, 1.0), (1.2, 0.0)])
    return S.reverb(np.tanh(v / (np.max(np.abs(v)) + 1e-9) * 2.2), 0.25)


def attack_tail_whip(rng, kind):
    if kind == 1:
        return S.place(0.5, (0.0, S.whoosh(rng, 0.25, 400.0, 2500.0) * 0.8), (0.24, S.crack(rng)))
    return S.place(0.7, (0.0, S.whoosh(rng, 0.2, 500.0, 2800.0) * 0.7), (0.19, S.crack(rng)), (0.3, S.whoosh(rng, 0.18, 600.0, 3000.0) * 0.6),
                   (0.47, S.crack(rng) * 0.8))


def attack_plague_breath(rng, kind):
    if kind == 1:
        exhale = S.voice(1.6, [(0.0, 140.0), (1.6, 120.0)], [(0.0, "ah"), (1.6, "uh")], rng, scale=1.0, breath=2.0,
                         amp_pts=[(0.0, 0.0), (0.2, 0.6), (1.3, 0.5), (1.6, 0.0)]) * 0.4
        return S.place(1.7, (0.0, hiss(rng, 1.6, 7.0, 1500.0, 7000.0)), (0.0, exhale), (0.1, S.bubbles(rng, 1.4, 25) * 0.3))
    parts = []
    for i in range(3):
        parts.append((0.3 * i, hiss(rng, 0.28, 14.0, 2000.0, 9000.0) * (1.0 - 0.15 * i)))
    return S.place(1.3, *parts, (0.0, S.bubbles(rng, 1.2, 35, 250.0, 800.0) * 0.35))


# ---------------------------------------------------------------- the rats


def rat_ambient(rng, kind):
    if kind == 1:
        return squeak(rng, 0.08, [(0.0, 3200.0), (0.03, 3800.0), (0.08, 3400.0)])
    if kind == 2:
        a = squeak(rng, 0.06, [(0.0, 3600.0), (0.06, 4200.0)])
        return S.place(0.2, (0.0, a), (0.11, a * 0.8))
    if kind == 3:
        return S.place(0.45, (0.0, sniff(rng, 0.04) * 0.6), (0.07, sniff(rng, 0.04) * 0.5), (0.14, sniff(rng, 0.04) * 0.5),
                       (0.25, squeak(rng, 0.07, [(0.0, 3000.0), (0.07, 3600.0)], 0.8)))
    parts = []
    for i in range(5):
        f = rng.uniform(2800.0, 4200.0)
        parts.append((0.05 * i, squeak(rng, 0.025, [(0.0, f), (0.025, f * 1.1)], 0.6 + 0.4 * rng.random())))
    return S.place(0.35, *parts)


def rat_hurt(rng, kind):
    if kind == 1:
        return squeak(rng, 0.15, [(0.0, 3500.0), (0.04, 5200.0), (0.15, 4400.0)], harm=((1, 1.0), (2, 0.4), (3, 0.15)))
    return squeak(rng, 0.14, [(0.0, 4200.0), (0.14, 3000.0)], harm=((1, 1.0), (2, 0.35)))


def rat_death(rng):
    sq = squeak(rng, 0.45, [(0.0, 4800.0), (0.45, 1800.0)], vib=(14.0, 0.03))
    return S.place(0.6, (0.0, sq), (0.42, S.thud(rng, 160.0, 110.0, 0.12, 0.05) * 0.3))


def rat_step(rng, kind):
    parts = [(0.02 * i + rng.uniform(0, 0.01), tick(rng, 3000.0 + 1000 * kind, 0.3 + 0.2 * rng.random())) for i in range(3)]
    return S.place(0.12, *parts)


def rat_attack(rng, kind):
    clack = S.bandpass(S.burst(0.02, rng, 0.002), 3000.0, 8000.0)
    if kind == 1:
        return S.place(0.3, (0.0, hiss(rng, 0.15, 30.0) * 0.5), (0.14, clack))
    return S.place(0.3, (0.0, squeak(rng, 0.06, [(0.0, 3000.0), (0.06, 3800.0)], 0.6)), (0.06, clack), (0.09, clack * 0.6))


def ravager_ambient(rng, kind):
    if kind == 1:
        g = growl(rng, 0.6, 140.0, "uh", rough=0.8, rough_hz=35.0)
        chatter = S.place(0.4, *[(0.05 * i, tick(rng, 2500.0, 0.5)) for i in range(7)])
        return S.place(1.1, (0.0, g), (0.65, chatter))
    if kind == 2:
        return S.place(0.8, (0.0, hiss(rng, 0.5)), (0.05, growl(rng, 0.45, 120.0, "aw", rough=0.9) * 0.5))
    v = S.voice(0.5, [(0.0, 700.0), (0.2, 900.0), (0.5, 650.0)], [(0.0, "ah"), (0.5, "ih")], rng, breath=0.5, rough=0.6,
                rough_hz=55.0, amp_pts=[(0.0, 0.0), (0.03, 1.0), (0.5, 0.0)])
    return S.place(1.0, (0.0, v), (0.4, S.bubbles(rng, 0.5, 30, 200.0, 600.0) * 0.4))


def ravager_hurt(rng, kind):
    f = (700.0, 1100.0, 900.0) if kind == 1 else (850.0, 1300.0, 1000.0)
    v = S.voice(0.32, [(0.0, f[0]), (0.06, f[1]), (0.32, f[2])], [(0.0, "ah"), (0.32, "ee")], rng, breath=0.45, rough=0.6,
                rough_hz=60.0, tilt=0.8, amp_pts=[(0.0, 0.0), (0.01, 1.0), (0.2, 0.8), (0.32, 0.0)])
    return S.place(0.4, (0.0, v), (0.01, squeak(rng, 0.2, [(0.0, 2500.0), (0.2, 2900.0)], 0.3)))


def ravager_death(rng):
    v = S.voice(1.0, [(0.0, 1000.0), (0.15, 1200.0), (1.0, 300.0)], [(0.0, "ee"), (0.4, "ah"), (1.0, "uh")], rng, breath=0.5,
                rough=0.7, rough_hz=40.0, amp_pts=[(0.0, 0.0), (0.02, 1.0), (0.7, 0.7), (1.0, 0.0)])
    return S.place(1.4, (0.0, v), (0.5, S.bubbles(rng, 0.8, 45, 150.0, 500.0) * 0.5),
                   (1.0, S.thud(rng, 110.0, 70.0, 0.2, 0.1) * 0.4))


def ravager_hiss(rng, kind):
    return hiss(rng, 0.8 if kind == 1 else 0.6, rattle=22.0 if kind == 1 else 34.0)


def ravager_step(rng, kind):
    pad = S.thud(rng, 150.0 + 20 * kind, 100.0, 0.12, 0.06) * 0.6
    return S.place(0.18, (0.0, pad), (0.01, tick(rng, 2800.0, 0.35)), (0.04, tick(rng, 3200.0, 0.25)))


def ravager_attack(rng, kind):
    snap = S.bandpass(S.burst(0.03, rng, 0.003), 2000.0, 6000.0)
    if kind == 1:
        return S.place(0.6, (0.0, growl(rng, 0.3, 160.0, rough=0.9) * 0.7), (0.28, snap))
    if kind == 2:
        return S.place(0.6, (0.0, S.whoosh(rng, 0.2, 600.0, 2000.0) * 0.6), (0.0, hiss(rng, 0.2, 30.0) * 0.4), (0.2, snap))
    return S.place(0.6, (0.0, growl(rng, 0.2, 180.0, rough=0.8) * 0.5), (0.18, snap), (0.33, snap * 0.8))


# ---------------------------------------------------------------- the list


class Event:
    """One sound event: its id (sound name), the files it picks from, its subtitle, and where it shows in the
    showcase. `used`: the mod plays it now (the rest wait for the fights)."""

    def __init__(self, event, folder, files, subtitle, group, label, volume=1.0, used=False):
        self.event, self.folder, self.files = event, folder, files
        self.subtitle, self.group, self.label, self.volume, self.used = subtitle, group, label, volume, used

    @property
    def subtitle_key(self):
        base = self.event.split(".", 1)[1]                       # rat_king.ambient ...
        base = re.sub(r"_\d+$", "", base)                         # one subtitle for every version
        return "subtitles.dross." + base


K, R_, E = "rat_king", "rat", "enraged_rat"
EVENTS = [
    # The Rat King's voice and moves (played by the showcase's animations now).
    Event("entity.rat_king.ambient", K, [("ambient_1", king_snicker), ("ambient_2", king_sniff_squeak),
                                         ("ambient_3", king_mutter), ("ambient_4", king_cackle)],
          "Rat King snickers", "Rat King: voice", "Sly snickers, sniffs, muttering and a cackle (4 versions)", used=True),
    Event("entity.rat_king.hurt", K, [("hurt_1", lambda r: king_hurt(r, 1.0, 0)), ("hurt_2", lambda r: king_hurt(r, 1.0, 1)),
                                      ("hurt_3", lambda r: king_hurt(r, 1.05, 2))],
          "Rat King yelps", "Rat King: voice", "Hurt (3 versions)", used=True),
    Event("entity.rat_king.death", K, [("death", king_death)], "Rat King squeals", "Rat King: voice",
          "The silly death's long squeal", used=True),
    Event("entity.rat_king.startle", K, [("startle_1", lambda r: king_startle(r, 0)), ("startle_2", lambda r: king_startle(r, 1))],
          "Rat King startles", "Rat King: voice", "Jolted awake: 'eep!' and 'huh?!'", used=True),
    Event("entity.rat_king.yawn", K, [("yawn", king_yawn)], "Rat King yawns", "Rat King: voice", "A big yawn", used=True),
    Event("entity.rat_king.snore", K, [("snore", king_snore)], "Rat King snores", "Rat King: voice",
          "A snore with a little whistle (every 4 seconds while he sleeps)", volume=0.7, used=True),
    Event("entity.rat_king.screech", K, [("screech", king_screech)], "Rat King screeches", "Rat King: rage",
          "The rage's screech", used=True),
    Event("entity.rat_king.rage_whistle_1", K, [("rage_whistle_1", rage_whistle_1)], "Rat King whistles like a train",
          "Rat King: rage", "Train whistle 1: 'choo-choo-chooo' (with his steam puffs)", used=True),
    Event("entity.rat_king.rage_whistle_2", K, [("rage_whistle_2", rage_whistle_2)], "Rat King whistles like a train",
          "Rat King: rage", "Train whistle 2: one long wailing blast", used=True),
    Event("entity.rat_king.rage_whistle_3", K, [("rage_whistle_3", rage_whistle_3)], "Rat King whistles like a train",
          "Rat King: rage", "Train whistle 3: a shrill toy train", used=True),
    Event("entity.rat_king.stomp", K, [("stomp_1", lambda r: king_stomp(r, 1.0)), ("stomp_2", lambda r: king_stomp(r, 0.9))],
          "Rat King stomps", "Rat King: rage", "Tantrum stomps", used=True),
    Event("entity.rat_king.staff_slam", K, [("staff_slam", staff_slam)], "Staff slams down", "Rat King: rage",
          "The staff slam and the orb ringing", used=True),
    Event("entity.rat_king.step", K, [("step_1", lambda r: king_step(r, 1.0)), ("step_2", lambda r: king_step(r, 0.9)),
                                      ("step_3", lambda r: king_step(r, 1.1))],
          "Footsteps", "Rat King: moves", "Soft padded footsteps", volume=0.5, used=True),
    Event("entity.rat_king.dig", K, [("dig_1", lambda r: king_dig(r, 0.0)), ("dig_2", lambda r: king_dig(r, 1.3))],
          "Rat King digs", "Rat King: moves", "Clawing at the dirt", used=True),
    Event("entity.rat_king.dive", K, [("dive", king_dive)], "Rat King dives underground", "Rat King: moves",
          "Diving head first into the ground", used=True),
    Event("entity.rat_king.rumble", K, [("rumble", ground_rumble)], "Ground rumbles", "Rat King: moves",
          "The mound trembling before he pops up", used=True),
    Event("entity.rat_king.pop", K, [("pop_1", lambda r: king_pop(r, 0)), ("pop_2", lambda r: king_pop(r, 1))],
          "Rat King pops up", "Rat King: moves", "Popping up: pop, dirt and a 'ta-da'", used=True),
    Event("entity.rat_king.staff_grab", K, [("staff_grab", staff_grab)], "Staff taps", "Rat King: moves",
          "Picking up his staff", used=True),
    Event("entity.rat_king.thud", K, [("thud", king_thud)], "Rat King flops over", "Rat King: moves",
          "Falling flat on his back (with a silly boing)", used=True),
    Event("entity.rat_king.staff_clatter", K, [("staff_clatter", staff_clatter)], "Staff clatters", "Rat King: moves",
          "His staff clattering down", used=True),
    Event("entity.rat_king.crown_clink", K, [("crown_clink", crown_clink)], "Crown clinks", "Rat King: moves",
          "His crown landing on his belly", used=True),
    Event("entity.rat_king.charge", K, [("charge_1", lambda r: king_charge(r, 1)), ("charge_2", lambda r: king_charge(r, 2))],
          "Rat King charges", "Rat King: moves",
          "The spear charge: a war-cry squeak or a rising squeal, scurrying claws, the air rushing (2 versions)", used=True),
]
ATTACKS = [
    ("swipe", attack_swipe, 3, "Rat King swipes", "Claw swipe"),
    ("bite", attack_bite, 3, "Rat King bites", "Bite"),
    ("staff", attack_staff, 3, "Staff strikes", "Staff strike"),
    ("orb", attack_orb, 3, "Plague orb crackles", "Plague orb spell"),
    ("summon", attack_summon, 3, "Rat King calls his rats", "Summoning his rats"),
    ("screech", attack_screech, 2, "Rat King shrieks", "Sonic screech"),
    ("tail_whip", attack_tail_whip, 2, "Tail cracks", "Tail whip"),
    ("plague_breath", attack_plague_breath, 2, "Plague breath hisses", "Plague breath"),
]
for kind, fn, count, sub, label in ATTACKS:
    for i in range(1, count + 1):
        EVENTS.append(Event(f"entity.rat_king.attack.{kind}_{i}", K, [(f"attack_{kind}_{i}", (lambda r, f=fn, k=i: f(r, k)))],
                            sub, "Rat King: attacks (for the fight; pick your favourites)", f"{label}, version {i}"))
EVENTS += [
    # The regular rats (the Plague Rat) and the enraged rats (the Plague Ravager).
    Event("entity.rat.ambient", R_, [(f"ambient_{i}", (lambda r, k=i: rat_ambient(r, k))) for i in range(1, 5)],
          "Rat squeaks", "Plague Rat", "Squeaks, sniffs and chittering (4 versions)", volume=0.6, used=True),
    Event("entity.rat.hurt", R_, [(f"hurt_{i}", (lambda r, k=i: rat_hurt(r, k))) for i in range(1, 3)], "Rat squeals",
          "Plague Rat", "Hurt (2 versions)", volume=0.7, used=True),
    Event("entity.rat.death", R_, [("death", rat_death)], "Rat dies", "Plague Rat", "Death", volume=0.7, used=True),
    Event("entity.rat.step", R_, [(f"step_{i}", (lambda r, k=i: rat_step(r, k))) for i in range(1, 3)], "Footsteps",
          "Plague Rat", "Tiny pattering feet", volume=0.25, used=True),
    Event("entity.rat.attack", R_, [(f"attack_{i}", (lambda r, k=i: rat_attack(r, k))) for i in range(1, 3)], "Rat bites",
          "Plague Rat", "Bite (2 versions, for the fight)", volume=0.7),
    Event("entity.enraged_rat.ambient", E, [(f"ambient_{i}", (lambda r, k=i: ravager_ambient(r, k))) for i in range(1, 4)],
          "Plague Ravager growls", "Plague Ravager", "Growls, hissing snarls and a sick gurgle (3 versions)", used=True),
    Event("entity.enraged_rat.hurt", E, [(f"hurt_{i}", (lambda r, k=i: ravager_hurt(r, k))) for i in range(1, 3)],
          "Plague Ravager screeches", "Plague Ravager", "Hurt (2 versions)", used=True),
    Event("entity.enraged_rat.death", E, [("death", ravager_death)], "Plague Ravager dies", "Plague Ravager", "Death",
          used=True),
    Event("entity.enraged_rat.hiss", E, [(f"hiss_{i}", (lambda r, k=i: ravager_hiss(r, k))) for i in range(1, 3)],
          "Plague Ravager hisses", "Plague Ravager", "Hiss (2 versions, for the fight)"),
    Event("entity.enraged_rat.step", E, [(f"step_{i}", (lambda r, k=i: ravager_step(r, k))) for i in range(1, 3)],
          "Footsteps", "Plague Ravager", "Heavier pattering feet", volume=0.35, used=True),
    Event("entity.enraged_rat.attack", E, [(f"attack_{i}", (lambda r, k=i: ravager_attack(r, k))) for i in range(1, 4)],
          "Plague Ravager bites", "Plague Ravager", "Snarling bites (3 versions, for the fight)"),
]


# ---------------------------------------------------------------- writing it all


def render(event, name, maker):
    rng = np.random.default_rng(zlib.crc32(f"{event.folder}/{name}".encode()))
    x = S.finish(maker(rng))
    path = os.path.join(SOUND_DIR, event.folder, name + ".ogg")
    soundfile.write(path, x.astype(np.float32), S.SR, format="OGG", subtype="VORBIS")
    return x


def write_sounds_json():
    """Adds (or replaces) this tool's events in sounds.json, keeping every other entry exactly as it was."""
    with open(SOUNDS_JSON, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    ours = ("entity.rat_king.", "entity.rat.", "entity.enraged_rat.")
    # Drop our old entries (the file's own formatting is kept for everything else).
    lines = text.rstrip().rstrip("}").rstrip().split("\n")
    keep, skipping, depth = [], False, 0
    for line in lines:
        stripped = line.strip()
        if not skipping and depth == 0 and stripped.startswith('"') and any(stripped.startswith('"' + o) for o in ours):
            skipping = True
        if skipping:
            depth += line.count("{") - line.count("}")
            if depth <= 0 and line.count("}") > 0:
                skipping, depth = False, 0
            continue
        keep.append(line)
    body = "\n".join(keep).rstrip().rstrip(",")
    entries = []
    for ev in EVENTS:
        sounds = ",\n".join(f'      {{"name": "dross:{ev.folder}/{name}"' + (f', "volume": {ev.volume}' if ev.volume != 1.0 else "") + "}"
                            for name, _m in ev.files)
        entries.append(f'  "{ev.event}": {{\n    "subtitle": "{ev.subtitle_key}",\n    "sounds": [\n{sounds}\n    ]\n  }}')
    out = body + ",\n" + ",\n".join(entries) + "\n}\n"
    json.loads(out)  # still valid JSON
    old = {k for k in data if not k.startswith(ours)}
    assert old <= set(json.loads(out)), "lost an entry"
    with open(SOUNDS_JSON, "w", encoding="utf-8", newline="\n") as f:
        f.write(out)


def write_lang():
    """Adds the subtitles to en_us.json (only keys that aren't there yet; nothing else changes)."""
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


def write_java():
    rows = []
    for ev in EVENTS:
        rows.append(f'            new Sound("{ev.group}", "{ev.event}", "{ev.label.replace(chr(34), chr(39))}", {len(ev.files)}, {str(ev.used).lower()})')
    src = f'''package com.reg21meme.dross.boss.ratking;

import java.util.List;

/**
 * Every Rat King and rat sound, written by {{@code tools/ratking/sounds.py}} (don't edit by hand: change the sounds there
 * and run it again). All of them are original, synthesized by that tool. {{@code /dross showcase boss_sounds ratking}}
 * lists them in chat with a button that plays each one.
 */
public final class RatKingSounds
{{
    /**
     * One sound event.
     *
     * @param group    the heading it's listed under
     * @param event    its id ({{@code dross:<event>}})
     * @param label    what it is
     * @param versions how many versions it picks from at random
     * @param used     true if the mod plays it now; the rest wait for the fights
     */
    public record Sound(String group, String event, String label, int versions, boolean used)
    {{
    }}

    /** Which train whistle his rage plays (1, 2 or 3; all three are in the showcase). */
    public static final int RAGE_WHISTLE = {RAGE_WHISTLE};

    public static final List<Sound> ALL = List.of(
{",\n".join(rows)});

    private RatKingSounds() {{}}
}}
'''
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)


def write_page():
    os.makedirs(PREVIEWS, exist_ok=True)
    groups = {}
    for ev in EVENTS:
        groups.setdefault(ev.group, []).append(ev)
    html = ['<!doctype html><meta charset="utf-8"><title>Rat King sounds</title>',
            "<style>body{font-family:system-ui,sans-serif;background:#1c1d1b;color:#e6e6e0;margin:24px;max-width:1100px}"
            "h1{font-size:22px}h2{font-size:17px;margin-top:28px;color:#a8c457}"
            ".row{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin:6px 0;padding:6px 8px;background:#262824;border-radius:6px}"
            ".label{flex:1 1 280px}.id{color:#8d8d86;font-size:12px}audio{height:32px}</style>",
            "<h1>The Rat King's and his rats' sounds</h1>",
            "<p>All original: made from scratch by <code>tools/ratking/sounds.py</code>. In the game, "
            "<code>/dross showcase boss_sounds ratking</code> lists them with play buttons.</p>"]
    for group, evs in groups.items():
        html.append(f"<h2>{group}</h2>")
        for ev in evs:
            players = "".join(f'<audio controls preload="none" src="../../../src/main/resources/assets/dross/sounds/{ev.folder}/{name}.ogg"></audio>'
                              for name, _m in ev.files)
            html.append(f'<div class="row"><div class="label">{ev.label}<div class="id">dross:{ev.event}</div></div>{players}</div>')
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(html) + "\n")


def spectrogram(x, w=300, h=110):
    f, t, Z = signal.spectrogram(x, S.SR, nperseg=512, noverlap=384)
    keep = f <= 8000.0
    Z = 10.0 * np.log10(Z[keep] + 1e-10)
    Z = np.clip((Z - (Z.max() - 70.0)) / 70.0, 0.0, 1.0)
    im = Image.fromarray((Z[::-1] * 255).astype(np.uint8), "L").resize((w, h))
    return Image.merge("RGB", (im, im.point(lambda v: int(v * 0.85)), im.point(lambda v: int(v * 0.4))))


def write_sheet(rendered, path):
    cols, cw, ch = 4, 300, 150
    rows = math.ceil(len(rendered) / cols)
    sheet = Image.new("RGB", (cols * cw, rows * ch), (24, 24, 22))
    d = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    for i, (label, x) in enumerate(rendered):
        cx, cy = (i % cols) * cw, (i // cols) * ch
        sheet.paste(spectrogram(x, cw - 8, ch - 34), (cx + 4, cy + 26))
        d.text((cx + 6, cy + 5), f"{label}  {len(x) / S.SR:.2f}s", fill=(230, 230, 220), font=font)
    sheet.save(path)


def main():
    rendered = []
    for ev in EVENTS:
        os.makedirs(os.path.join(SOUND_DIR, ev.folder), exist_ok=True)
        for name, maker in ev.files:
            x = render(ev, name, maker)
            rendered.append((f"{ev.folder}/{name}", x))
            print(f"  {ev.folder}/{name}.ogg  {len(x) / S.SR:.2f} s")
    # Old files of ours that no event uses any more.
    for folder in (K, R_, E):
        names = {n for ev in EVENTS if ev.folder == folder for n, _m in ev.files}
        for fn in os.listdir(os.path.join(SOUND_DIR, folder)):
            if fn.endswith(".ogg") and fn[:-4] not in names:
                os.remove(os.path.join(SOUND_DIR, folder, fn))
                print(f"  removed stale {folder}/{fn}")
    write_sounds_json()
    write_lang()
    write_java()
    write_page()
    write_sheet(rendered, os.path.join(PREVIEWS, "spectrograms.png"))
    print(f"{len(rendered)} sounds in {len(EVENTS)} events. Done.")


if __name__ == "__main__":
    main()
