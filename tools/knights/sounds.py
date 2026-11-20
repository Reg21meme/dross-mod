"""
Makes the ten elemental knights' sounds (CLAUDE.md step 18; the user, November 18, 2026: "create their own sounds for
the knights ... unique sounds for each element ... the speedy lightning dude's gonna be a little lighter sound, while
the anchor dude's gonna be a little heavier, more like fatter"; "just create your own sounds"). Every one is
synthesized from scratch with the Rat King's synthesizer (tools/ratking/ratkit/synth.py) and the knights' own voice
engine (tools/knights/knightvoice.py): nothing is recorded and nothing comes from Minecraft.

Their voices are no one's voice (the user, November 18, 2026, after hearing the first ones, made with the Huntsman's
voicekit: "too much like the Rimefall dudes"): no throat, no vowels, each made of his element in his sealed helm (an
ice corpse's breath rattling and its ice groaning, rot gurgling and wheezing, a furnace's bellows and growl, a hull
groaning under a broken radio's hum or an electric snarl, a cave's grinding groan and ringing crystal).
tools/knights/voicecheck.py measures how far they are from the Rimefall voices; the page shows it.

Each ELEMENT has its own character:
  ice      frozen breath, frost crackling, ice cracking and creaking, glassy rings
  poison   wet bubbling rasps, hisses, squelches, puffs of spores
  fire     crackling roars, embers, flame whooshes, booms
  thunder  electric hum, crackles, zaps, thunder
  earth    grinding stone, rumbles, gravel, crystal chimes
and each KNIGHT its own voice and weight by build (`BUILDS`): the Wreck Knight the heaviest and "fattest" (low, slow,
everything an octave down), the Cinder and Rockfall Knights heavy, the Thawed Soldiers, the Sporebound and the Geode
Knight middling, the Blight Warden, Flamehand and the Stormblade light and quick (the Stormblade the lightest: high,
short, bright). Their armoured footsteps follow it (a heavy clank and thud against a light patter).

Run from the project folder:

    python tools/knights/sounds.py

It writes:
  src/main/resources/assets/dross/sounds/knight/<knight>/*.ogg     the sounds (mono Ogg Vorbis)
  src/main/resources/assets/dross/sounds.json      adds (or replaces) its own events (entity.knight_*); every other entry
                                                   stays exactly as it was; a key twice is refused
  src/main/resources/assets/dross/lang/en_us.json  adds its subtitles (subtitles.dross.knight_*), keeping the file's own
                                                   line endings; every other key stays exactly as it was
  src/main/java/com/reg21meme/dross/boss/knight/KnightSounds.java   every event (generated: don't edit by hand)
  boss-previews/knights/sounds/index.html          a page that plays every sound, with how long, loud and low each is

Needs numpy, scipy and soundfile (`pip install soundfile`).
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
sys.path.insert(0, HERE)

import numpy as np  # noqa: E402
import soundfile  # noqa: E402

from ratkit import synth as S  # noqa: E402
import knightvoice as KV  # noqa: E402
import voicecheck as VC  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
SOUND_DIR = os.path.join(ASSETS, "sounds", "knight")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "knight", "KnightSounds.java")
PREVIEWS = os.path.join(ROOT, "boss-previews", "knights", "sounds")
HASHES = os.path.join(HERE, "sound_hashes.json")

SR = S.SR


# ---------------------------------------------------------------- the knights' builds


class Build:
    """
    How a knight sounds: `w` how heavy (0 light and quick .. 1 the heaviest), `f0` his voice's pitch (Hz), `scale` his
    mouth (below 1 bigger), `helm` his helm's hollow ring (ms: a longer one is a bigger, duller helm), `time` how long
    his moves take (above 1 slower), `rasp` how rough his voice is.
    """

    def __init__(self, key, name, element, w, f0, scale, helm, time, rasp):
        self.key, self.name, self.element = key, name, element
        self.w, self.f0, self.scale, self.helm, self.time, self.rasp = w, f0, scale, helm, time, rasp

    def low(self, f):
        """A frequency for this knight: an octave down for the heaviest, a little up for the lightest."""
        return f * (1.25 - 0.75 * self.w)


BUILDS = {b.key: b for b in (
    Build("thawed_axe", "Thawed Axeman", "ice", 0.6, 92.0, 0.9, 2.0, 1.0, 0.25),
    Build("thawed_spear", "Thawed Spearman", "ice", 0.55, 100.0, 0.92, 1.9, 0.95, 0.2),
    Build("blight_warden", "Blight Warden", "poison", 0.3, 120.0, 0.98, 1.4, 0.85, 0.55),
    Build("sporebound", "Sporebound", "poison", 0.55, 90.0, 0.9, 2.0, 1.0, 0.45),
    Build("cinder", "Cinder Knight", "fire", 0.85, 70.0, 0.83, 2.7, 1.15, 0.35),
    Build("flamehand", "Flamehand", "fire", 0.3, 128.0, 1.02, 1.3, 0.85, 0.2),
    Build("wreck", "Wreck Knight", "thunder", 1.0, 56.0, 0.76, 3.4, 1.3, 0.3),
    Build("stormblade", "Stormblade", "thunder", 0.08, 152.0, 1.1, 0.9, 0.7, 0.1),
    Build("geode", "Geode Knight", "earth", 0.65, 88.0, 0.88, 2.2, 1.05, 0.25),
    Build("rockfall", "Rockfall Knight", "earth", 0.9, 66.0, 0.8, 2.9, 1.2, 0.3),
)}


# ---------------------------------------------------------------- pieces


def env(points, dur):
    return S.smooth_env(points, S.n_of(dur))


def norm(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def fit(x, dur):
    """`x` cut or padded to `dur` seconds."""
    n = S.n_of(dur)
    if len(x) >= n:
        return x[:n]
    return np.concatenate([x, np.zeros(n - len(x))])


def wobble(rng, n, rate, depth):
    """A slow random wobble in loudness (1 +- depth)."""
    w = S.lowpass(rng.standard_normal(n), rate)
    return 1.0 + depth * w / (np.max(np.abs(w)) + 1e-9)


# ---- metal


def clank(rng, w, level=1.0):
    """Armour knocking: a bright little clink for a light knight, a deep heavy clank for a heavy one."""
    base = 2600.0 - 1900.0 * w
    metal = S.clink(rng, base, (1.0, 1.47, 2.09, 2.56, 3.3), 0.35 + 0.6 * w, 0.05 + 0.13 * w)
    body = S.thud(rng, 170.0 - 110.0 * w, 110.0 - 70.0 * w, 0.18 + 0.25 * w, 0.2)
    return S.place(0.0, (0.0, norm(metal) * 0.6), (0.0, norm(body) * (0.3 + 0.5 * w))) * level


def chain(rng, dur, rate, w, level=1.0):
    """A chain rattling: lots of little links knocking together."""
    parts = []
    for _ in range(int(rate * dur)):
        t = rng.uniform(0.0, dur)
        base = rng.uniform(1400.0, 3200.0) * (1.0 - 0.45 * w)
        parts.append((t, S.clink(rng, base, (1.0, 1.52, 2.4), 0.1, 0.025) * rng.uniform(0.3, 1.0)))
    return S.place(dur, *parts) * level


def whoosh(rng, b, dur, lo, hi, level=1.0, rise=0.45):
    """A weapon cutting the air: lower and longer for a heavy knight."""
    dur = dur * b.time
    return S.whoosh(rng, dur, b.low(lo), b.low(hi), [(0.0, 0.0), (dur * rise, 1.0), (dur, 0.0)]) * level


# ---- ice


def frost(rng, dur, level=0.3):
    return S.grains(rng, dur, 420.0, grain=(0.002, 0.008), lo=3500.0, hi=9500.0, level=level)


def crunch(rng, dur, rate=120.0, level=0.8, lo=1500.0, hi=7000.0):
    return S.grains(rng, dur, rate, grain=(0.002, 0.008), lo=lo, hi=hi, level=level)


def ring(rng, dur, freqs, decay, level=1.0):
    """Glassy partials dying away (ice): out of tune with each other, each with its own decay."""
    n = S.n_of(dur)
    t = np.arange(n) / SR
    out = np.zeros(n)
    for k, f in enumerate(freqs):
        out += np.sin(2 * math.pi * f * t + rng.random() * 6.0) * np.exp(-t / (decay * (1.0 - 0.12 * k))) / (1 + 0.5 * k)
    return out * np.clip(t / 0.002, 0.0, 1.0) * level


def ice_crack(rng, level=1.0, size=1.0):
    """Ice splitting: a sharp snap, a glassy ring and a crackle running off."""
    snap = S.crack(rng, 0.05 + 0.03 * size)
    body = S.thud(rng, 120.0 / size, 70.0 / size, 0.25, 0.4)
    base = 1900.0 / size ** 0.5
    glass = ring(rng, 0.6, (base, base * 1.53, base * 2.31, base * 3.07), 0.12 * size, 0.35)
    return S.place(0.7, (0.0, snap), (0.0, body * 0.35), (0.004, glass), (0.02, frost(rng, 0.35, 0.35))) * level


def creak(rng, dur, rate_pts, freqs, level=1.0):
    """Ice (or a fungus, or stone) creaking: a stick-slip train of tiny clicks rung through a few resonances."""
    n = S.n_of(dur)
    rate = S.curve(rate_pts, n)
    drift = S.lowpass(rng.standard_normal(n), 6.0)
    rate = rate * (1.0 + 0.25 * drift / (np.std(drift) * 3.0 + 1e-9))
    phase = np.cumsum(np.maximum(rate, 1.0)) / SR
    clicks = np.zeros(n)
    idx = np.where(np.diff(np.floor(phase)) > 0)[0]
    clicks[idx] = rng.uniform(0.4, 1.0, len(idx))
    exc = clicks + 0.05 * S.noise(n, rng)
    out = sum(S.resonator(exc, f, 12.0) / (1 + k) for k, f in enumerate(freqs))
    return norm(out) * level


# ---- poison


def hiss(rng, dur, lo=2500.0, hi=9000.0, pts=None, level=1.0):
    n = S.n_of(dur)
    x = S.bandpass(S.noise(n, rng), lo, hi)
    return norm(x) * env(pts or [(0.0, 0.0), (dur * 0.2, 1.0), (dur, 0.0)], dur) * level


def bubbles(rng, dur, rate, lo=180.0, hi=700.0, level=1.0):
    return norm(S.bubbles(rng, dur, rate, lo, hi)) * level


def squelch(rng, dur, f_pts, level=1.0):
    """Something wet and soft squeezed: low noise through a sliding, resonant mouth."""
    n = S.n_of(dur)
    src = S.lowpass(S.noise(n, rng, "pink"), 2500.0)
    out = S.sweep_filter(src, S.curve(f_pts, n, log=True), 0.35)
    out = norm(out) * env([(0.0, 0.0), (dur * 0.15, 1.0), (dur * 0.7, 0.8), (dur, 0.0)], dur)
    return S.place(dur, (0.0, out), (0.0, bubbles(rng, dur, 30.0, 120.0, 500.0, 0.4))) * level


def plop(rng, f=300.0, level=1.0):
    """One wet drop: a little falling blip."""
    d = 0.06
    n = S.n_of(d)
    t = np.arange(n) / SR
    fr = f * (1.0 + 1.8 * t / d)
    return np.sin(2 * math.pi * np.cumsum(fr) / SR) * np.exp(-t / 0.015) * level


# ---- fire


def crackle(rng, dur, rate, level=1.0, lo=900.0, hi=6500.0):
    """Embers popping: sharp little clicks at random, some big pops."""
    fine = S.grains(rng, dur, rate, grain=(0.0008, 0.003), lo=lo, hi=hi, jitter=2.5)
    pops = S.grains(rng, dur, rate * 0.12, grain=(0.003, 0.012), lo=300.0, hi=1500.0, jitter=1.5)
    return norm(fine + 0.8 * pops) * level


def roar(rng, dur, lo=70.0, hi=900.0, flicker=7.0, pts=None, level=1.0):
    """Fire roaring: low rushing noise with a flicker in it."""
    n = S.n_of(dur)
    x = S.bandpass(S.noise(n, rng, "brown"), lo, hi) * wobble(rng, n, flicker, 0.5)
    return norm(x) * env(pts or [(0.0, 0.0), (dur * 0.25, 1.0), (dur * 0.7, 0.8), (dur, 0.0)], dur) * level


def flame(rng, dur, lo, hi, level=1.0):
    """A burst of flame going past: a whoosh with a roar under it and embers in it."""
    return (S.whoosh(rng, dur, lo, hi) * 0.8 + roar(rng, dur, 60.0, lo * 1.5, 12.0, level=0.6)
            + crackle(rng, dur, 140.0, 0.25)) * level


# ---- thunder


def hum(rng, dur, f, level=1.0, bright=2400.0, pts=None):
    """An electric hum: a buzzing low note with a waver in it."""
    n = S.n_of(dur)
    t = np.arange(n) / SR
    fr = f * (1.0 + 0.004 * np.sin(2 * math.pi * 0.7 * t))
    x = S.lowpass(S.saw(fr, 0.8, 40), bright) * wobble(rng, n, 9.0, 0.25)
    return norm(x) * env(pts or [(0.0, 0.0), (dur * 0.2, 1.0), (dur * 0.8, 0.9), (dur, 0.0)], dur) * level


def fizz(rng, dur, f, level=1.0):
    """Electricity failing: a buzz torn about by noise, cutting out more and more as it dies away."""
    n = S.n_of(dur)
    x = KV.torn_buzz(rng, np.full(n, f) * S.curve([(0.0, 1.0), (dur, 0.55)], n), 0.45, 80.0, 1.0, 30)
    x = S.lowpass(x, 2600.0) * KV.dropouts(rng, n, 9.0, (0.01, 0.05), 0.0)
    return norm(x) * env([(0.0, 1.0), (dur, 0.0)], dur) * level


def sparks(rng, dur, rate, level=1.0, lo=2500.0, hi=12000.0):
    return norm(S.grains(rng, dur, rate, grain=(0.0004, 0.002), lo=lo, hi=hi, jitter=2.2)) * level


def zap(rng, dur, f_from, f_to, level=1.0):
    """A zap: a buzzing tone dropping (or rising) fast, torn up by crackles."""
    n = S.n_of(dur)
    f = S.curve([(0.0, f_from), (dur, f_to)], n, log=True)
    tone = S.saw(f, 1.0, 30)
    ripped = tone * (0.6 + 0.4 * np.sign(np.sin(2 * math.pi * np.cumsum(f * 0.5) / SR)))
    x = norm(ripped) * env([(0.0, 0.0), (0.004, 1.0), (dur * 0.5, 0.7), (dur, 0.0)], dur)
    return (x + sparks(rng, dur, 400.0, 0.5)) * level


def thunder(rng, dur, w, level=1.0):
    """A crack of lightning and its thunder rolling off: deeper and longer for a heavy knight."""
    crack = S.crack(rng, 0.12)
    n = S.n_of(dur)
    roll = S.lowpass(S.noise(n, rng, "brown"), 380.0 - 220.0 * w) * wobble(rng, n, 3.0, 0.6)
    roll = norm(roll) * env([(0.0, 0.0), (0.03, 1.0), (dur * 0.35, 0.6), (dur, 0.0)], dur)
    return S.place(dur, (0.0, norm(crack)), (0.01, roll * 0.9)) * level


# ---- earth


def grind(rng, dur, lo=150.0, hi=1400.0, rate=500.0, level=1.0, pts=None):
    """Stone grinding on stone: a dense grit of low grains over a rumble."""
    n = S.n_of(dur)
    grit = S.grains(rng, dur, rate, grain=(0.003, 0.014), lo=lo, hi=hi, jitter=1.0)
    rum = S.lowpass(S.noise(n, rng, "brown"), lo * 1.2) * wobble(rng, n, 4.0, 0.5)
    x = norm(grit) * 0.7 + norm(rum) * 0.5
    return x * env(pts or [(0.0, 0.0), (dur * 0.2, 1.0), (dur * 0.8, 0.9), (dur, 0.0)], dur) * level


def rumble(rng, dur, f=55.0, level=1.0, pts=None):
    n = S.n_of(dur)
    t = np.arange(n) / SR
    body = np.sin(2 * math.pi * np.cumsum(f * (1.0 + 0.05 * np.sin(2 * math.pi * 0.9 * t))) / SR)
    x = norm(S.lowpass(S.noise(n, rng, "brown"), f * 2.5)) * 0.8 + body * 0.5
    return x * wobble(rng, n, 3.0, 0.4) * env(pts or [(0.0, 0.0), (dur * 0.2, 1.0), (dur, 0.0)], dur) * level


def chime(rng, base, dur=0.9, decay=0.25, level=1.0):
    """A crystal ringing (amethyst): a bright bell-like partials."""
    return norm(S.clink(rng, base, (1.0, 2.0, 2.76, 3.9, 5.4), dur, decay)) * level


def chimes(rng, dur, count, lo, hi, level=1.0):
    parts = [(rng.uniform(0.0, dur * 0.75), chime(rng, math.exp(rng.uniform(math.log(lo), math.log(hi))),
                                                    0.8, rng.uniform(0.12, 0.3), rng.uniform(0.3, 1.0)))
             for _ in range(count)]
    return S.place(dur, *parts) * level


def stone_knock(rng, low=1.0, level=1.0):
    k = S.knock(rng, (180.0 * low, 520.0 * low, 1150.0 * low), (6, 8, 10), (1.0, 0.6, 0.3), 0.3, 0.05)
    return (norm(k) + fit(crunch(rng, 0.2, 200.0, 0.5, 400.0, 2500.0), 0.3)[:len(k)]) * level


def gravel(rng, dur, level=1.0):
    return norm(S.grains(rng, dur, 260.0, grain=(0.003, 0.012), lo=500.0, hi=4000.0, jitter=1.5)) * level


# ---- the knight's body


def boom(rng, b, dur=1.0, level=1.0):
    """A heavy blow landing: a falling low tone (lower for a heavier knight) and a dull knock."""
    return norm(S.thud(rng, b.low(85.0), b.low(48.0), dur, 0.35)) * level


# ---------------------------------------------------------------- each element's touch


def touch(rng, b, dur, level=1.0):
    """A short bit of the knight's element (on a step, a blow, a hurt): frost, a wet squelch, embers, a spark, grit."""
    e = b.element
    if e == "ice":
        return crunch(rng, dur, 160.0, 1.0, 1800.0, 8000.0) * level
    if e == "poison":
        return squelch(rng, dur, [(0.0, 900.0), (dur, 350.0)], level)
    if e == "fire":
        return crackle(rng, dur, 220.0, level)
    if e == "thunder":
        return sparks(rng, dur, 500.0 * (1.2 - b.w), level)
    return gravel(rng, dur, level)


def element_finale(rng, b, level=1.0):
    """The end of his death in his element: ice shattering, poison bubbling out, fire guttering, electricity fizzling
    out, stone crumbling."""
    e = b.element
    if e == "ice":
        return S.place(1.6, (0.0, ice_crack(rng, 1.0, 1.6)), (0.15, ice_crack(rng, 0.6, 1.0)),
                       (0.2, crunch(rng, 1.0, 220.0, 0.6, 2000.0, 9000.0) * env([(0.0, 1.0), (1.0, 0.0)], 1.0)),
                       (0.3, ring(rng, 1.2, (2400.0, 3700.0, 5600.0), 0.4, 0.3))) * level
    if e == "poison":
        return S.place(2.0, (0.0, bubbles(rng, 1.8, 45.0, 120.0, 500.0, 0.8) * env([(0.0, 1.0), (1.8, 0.0)], 1.8)),
                       (0.0, hiss(rng, 1.8, 1500.0, 7000.0, [(0.0, 0.0), (0.2, 1.0), (1.8, 0.0)], 0.5))) * level
    if e == "fire":
        return S.place(2.0, (0.0, roar(rng, 1.8, 50.0, 600.0, 5.0, [(0.0, 1.0), (1.8, 0.0)], 0.8)),
                       (0.0, crackle(rng, 1.8, 90.0, 0.5) * env([(0.0, 1.0), (1.8, 0.0)], 1.8)),
                       (0.6, hiss(rng, 1.2, 2500.0, 8000.0, [(0.0, 0.0), (0.2, 1.0), (1.2, 0.0)], 0.4))) * level
    if e == "thunder":
        return S.place(1.8, (0.0, zap(rng, 0.6, b.low(900.0), b.low(60.0), 0.9)),
                       (0.1, fizz(rng, 1.5, 45.0 + 280.0 * (1.0 - b.w), 0.7)),
                       (0.2, sparks(rng, 1.4, 120.0, 0.5) * env([(0.0, 1.0), (1.4, 0.0)], 1.4))) * level
    return S.place(2.0, (0.0, grind(rng, 1.6, 120.0, 1200.0, 500.0, 0.9, [(0.0, 1.0), (1.6, 0.0)])),
                   (0.1, gravel(rng, 1.4, 0.6) * env([(0.0, 1.0), (1.4, 0.0)], 1.4)),
                   (0.3, stone_knock(rng, 1.0 - 0.4 * b.w, 0.6)), (0.7, stone_knock(rng, 1.0 - 0.4 * b.w, 0.4))) * level


# ---------------------------------------------------------------- every knight: voice, steps


def k_step(rng, b, kind):
    """An armoured footstep: a heavy knight's thud and clank, a light one's quick patter."""
    w = b.w
    dur = 0.18 + 0.4 * w
    thud = S.thud(rng, b.low(150.0) * (2.0 - w), b.low(80.0) * (2.0 - w), dur, 0.3)
    metal = clank(rng, w, 0.35 - 0.1 * w)
    return S.place(dur, (0.0, norm(thud) * (0.2 + 0.8 * w)), (0.005, metal), (0.0, touch(rng, b, 0.12 + 0.1 * w, 0.35)))


def k_effort(rng, b, kind):
    """His strain as he starts a move: (1) a short bark of his element, (2) a bigger heave rising."""
    d = (0.26 if kind == 1 else 0.42) * (0.8 + 0.45 * b.w)
    if kind == 1:
        v = KV.voice(rng, b, d, [(0.0, 0.0), (0.015, 1.0), (d * 0.5, 0.75), (d, 0.0)], [(0.0, 0.75), (d, 0.45)], "bark")
        return S.place(d, (0.0, v), (0.0, touch(rng, b, d * 0.8, 0.12)))
    v = KV.voice(rng, b, d, [(0.0, 0.0), (d * 0.3, 0.8), (d * 0.6, 1.0), (d, 0.0)],
                 [(0.0, 0.35), (d * 0.6, 0.85), (d, 0.6)], "bark")
    return S.place(d, (0.0, v), (0.0, touch(rng, b, d, 0.18)))


def k_hurt(rng, b, kind):
    """Hurt: (1) a sharp elemental cry cut short, (2) a lower, longer groan; his armour knocked, a touch of his
    element."""
    d = (0.32 if kind == 1 else 0.48) * (0.8 + 0.45 * b.w)
    if kind == 1:
        v = KV.voice(rng, b, d, [(0.0, 0.0), (0.01, 1.0), (d * 0.4, 0.8), (d, 0.0)], [(0.0, 1.0), (d, 0.55)], "cry")
    else:
        v = KV.voice(rng, b, d, [(0.0, 0.0), (0.02, 1.0), (d * 0.55, 0.85), (d, 0.0)],
                     [(0.0, 0.7), (d * 0.3, 0.6), (d, 0.2)], "cry")
    return S.place(d + 0.3, (0.0, clank(rng, b.w, 0.55)), (0.01, v), (0.0, touch(rng, b, 0.25, 0.3)))


def k_death(rng, b, kind):
    """Death: a long elemental cry rising then falling away, a last rattle of breath, his armour going down in a clatter,
    then his element's end."""
    d = 1.35 * (0.85 + 0.35 * b.w)
    cry = KV.voice(rng, b, d, [(0.0, 0.0), (0.05, 1.0), (d * 0.55, 0.85), (d, 0.0)],
                   [(0.0, 0.85), (d * 0.2, 1.0), (d * 0.65, 0.5), (d, 0.1)], "death")
    last = KV.voice(rng, b, 0.7, [(0.0, 0.0), (0.1, 0.7), (0.7, 0.0)], [(0.0, 0.2), (0.7, 0.0)], "breath")
    fall = 0.25 + 0.15 * b.w
    clatter = [(d + 0.1 + fall * i * rng.uniform(0.6, 1.0), clank(rng, b.w, 0.7 / (1 + 0.5 * i))) for i in range(4)]
    return S.place(d + 2.4, (0.0, cry), (d * 0.85, last * 0.5), *clatter,
                   (d + 0.15, boom(rng, b, 0.8, 0.25 + 0.65 * b.w)), (d + 0.25, element_finale(rng, b, 0.8)))


def k_ambient(rng, b, kind):
    """Idle: (1) his breath through the helm in his element, two slow breaths; (2) a low elemental murmur, his armour
    shifting."""
    d = 1.8 * (0.85 + 0.3 * b.w)
    if kind == 1:
        h = d / 2
        breaths = [(i * h, KV.voice(rng, b, h, [(0.0, 0.0), (h * 0.35, 1.0), (h, 0.0)], [(0.0, 0.1), (h, 0.25)],
                                    "breath")) for i in range(2)]
        return S.place(d, *breaths)
    m = KV.voice(rng, b, d, [(0.0, 0.0), (d * 0.25, 1.0), (d * 0.7, 0.8), (d, 0.0)],
                 [(0.0, 0.2), (d * 0.5, 0.4), (d, 0.15)], "murmur")
    return S.place(d + 0.3, (0.0, m), (d * 0.3, clank(rng, b.w, 0.15)))


# ---------------------------------------------------------------- the moves
# Each plays at its move's moment in ElementalKnight: a wind-up at its start, a swing or a blow at its hit (the whoosh
# first, the blow right after it, so a blow played at the hit lands as the animation's), a release as something leaves
# its hand.

# ---- the Thawed Axeman (ice, middling)


def ta_swing(rng, b):
    """The axe of ice swung wide: a whoosh, frost trailing off its blade, a glassy ring."""
    w = whoosh(rng, b, 0.36, 500.0, 2200.0, 1.0, 0.6)
    return S.place(0.9, (0.0, w), (0.15, frost(rng, 0.5, 0.35)), (0.2, ring(rng, 0.6, (2100.0, 3200.0, 4900.0), 0.18, 0.25)))


def ta_smash_windup(rng, b):
    """Raising the axe for the smash: the ice on him creaking, a cold breath drawn in."""
    d = 1.0
    c = creak(rng, d, [(0.0, 25.0), (d, 55.0)], (b.low(260.0), b.low(610.0), 1450.0))
    c = c * env([(0.0, 0.0), (d * 0.7, 1.0), (d, 0.4)], d)
    return S.place(d, (0.0, c * 0.8), (0.1, KV.inhale(rng, b, 0.5, 0.35)), (0.3, frost(rng, 0.7, 0.3)))


def ta_smash(rng, b):
    """The axe brought down: a heavy thud and the ground's ice cracking open."""
    return S.place(1.3, (0.0, boom(rng, b, 0.8, 0.9)), (0.0, ice_crack(rng, 1.0, 1.7)), (0.02, clank(rng, b.w, 0.5)),
                   (0.05, crunch(rng, 0.8, 300.0, 0.5) * env([(0.0, 1.0), (0.8, 0.0)], 0.8)))


def ta_spike(rng, b):
    """A small ice spike bursting up: a snap and a quick glassy shink rising."""
    up = S.whoosh(rng, 0.2, 1200.0, 5000.0, [(0.0, 0.0), (0.05, 1.0), (0.2, 0.0)], color="white")
    return S.place(0.6, (0.0, ice_crack(rng, 0.8, 0.6)), (0.0, up * 0.5),
                   (0.02, ring(rng, 0.5, (2900.0, 4300.0, 6100.0), 0.1, 0.3)))


def ta_big_spike(rng, b):
    """The big spike at the line's end: a deep crack, ice grinding up out of the ground, a long ring."""
    up = crunch(rng, 0.35, 400.0, 1.0, 1200.0, 7000.0) * env([(0.0, 0.2), (0.3, 1.0), (0.35, 0.0)], 0.35)
    return S.place(1.4, (0.0, up * 0.7), (0.3, ice_crack(rng, 1.0, 2.0)), (0.3, boom(rng, b, 0.6, 0.6)),
                   (0.32, ring(rng, 1.1, (1300.0, 2050.0, 3100.0, 4400.0), 0.35, 0.45)))


# ---- the Thawed Spearman (ice, middling)


def ts_thrust(rng, b):
    """The spear driven forward: a short whoosh and its icy head's shink."""
    w = whoosh(rng, b, 0.22, 900.0, 3200.0, 1.0, 0.7)
    return S.place(0.6, (0.0, w), (0.14, ring(rng, 0.45, (3300.0, 4900.0), 0.08, 0.35)), (0.12, frost(rng, 0.3, 0.2)))


def ts_guard(rng, b):
    """The shield wall: his hide shield thumped down before him, frost hissing over it."""
    k = S.knock(rng, (b.low(200.0), b.low(560.0), 1250.0), (5, 7, 9), (1.0, 0.5, 0.25), 0.45, 0.08)
    return S.place(1.0, (0.0, norm(k)), (0.0, clank(rng, b.w, 0.3)),
                   (0.05, hiss(rng, 0.8, 3000.0, 9000.0, [(0.0, 0.0), (0.3, 1.0), (0.8, 0.0)], 0.35)),
                   (0.08, frost(rng, 0.7, 0.3)))


def ts_poke(rng, b):
    """A quick jab from behind the shield."""
    w = whoosh(rng, b, 0.13, 1300.0, 3800.0, 1.0, 0.6)
    return S.place(0.35, (0.0, w), (0.1, ring(rng, 0.25, (3600.0, 5400.0), 0.05, 0.3)))


def ts_block(rng, b):
    """A blow caught on his shield: a dull thump, ice ringing."""
    k = S.knock(rng, (b.low(230.0), b.low(620.0), 1400.0), (5, 7, 9), (1.0, 0.5, 0.25), 0.4, 0.06)
    return S.place(0.7, (0.0, norm(k)), (0.0, ring(rng, 0.6, (1800.0, 2750.0, 4100.0), 0.15, 0.3)),
                   (0.0, frost(rng, 0.4, 0.3)))


# ---- the Blight Warden (poison, light)


def bw_cast(rng, b):
    """His staff flicked: a quick whoosh, the ball of plague tearing off it with a wet glug, a hiss after it."""
    w = whoosh(rng, b, 0.25, 600.0, 2000.0, 0.8, 0.5)
    glug = S.place(0.3, *[(i * 0.05, plop(rng, 260.0 + 90.0 * i, 0.8)) for i in range(4)])
    return S.place(1.0, (0.0, w), (0.12, glug), (0.12, squelch(rng, 0.3, [(0.0, 400.0), (0.3, 1100.0)], 0.6)),
                   (0.2, hiss(rng, 0.7, 2500.0, 8000.0, [(0.0, 0.0), (0.1, 1.0), (0.7, 0.0)], 0.4)))


def bw_rain_call(rng, b):
    """Calling the blight rain: a wheezing, gurgling call rising and the poison bubbling up round him."""
    d = 1.25
    chant = KV.voice(rng, b, d, [(0.0, 0.0), (0.2, 0.7), (d * 0.8, 1.0), (d, 0.0)], [(0.0, 0.3), (d, 1.0)], "cry")
    bub = bubbles(rng, d, 60.0, 150.0, 650.0, 1.0) * env([(0.0, 0.0), (d, 1.0)], d)
    return S.place(d + 0.2, (0.0, chant * 0.8), (0.0, bub * 0.6),
                   (0.3, hiss(rng, d - 0.2, 1500.0, 6000.0, [(0.0, 0.0), (d - 0.3, 1.0), (d - 0.2, 0.0)], 0.4)))


def bw_rain(rng, b):
    """The blight rain falling for 4 s: a drizzle of thick drops plopping and sizzling, fading out."""
    d = 4.2
    drops = S.place(d, *[(rng.uniform(0.0, d - 0.1), plop(rng, rng.uniform(220.0, 900.0), rng.uniform(0.2, 1.0)))
                         for _ in range(int(55 * d))])
    patter = S.grains(rng, d, 300.0, grain=(0.002, 0.006), lo=900.0, hi=5000.0, jitter=1.6)
    shape = env([(0.0, 0.0), (0.4, 1.0), (d - 0.8, 0.9), (d, 0.0)], d)
    sizzle = hiss(rng, d, 3000.0, 9000.0, [(0.0, 0.0), (0.6, 1.0), (d - 0.6, 0.8), (d, 0.0)], 0.25)
    return (fit(norm(drops), d) * 0.7 + norm(patter) * 0.5) * shape + sizzle


# ---- the Sporebound (poison, middling)


def sb_reap(rng, b):
    """His rusted scythe swept round: a long low whoosh, a wet rasp off its blade, spores puffing."""
    w = whoosh(rng, b, 0.5, 300.0, 1500.0, 1.0, 0.55)
    return S.place(1.0, (0.0, w), (0.2, squelch(rng, 0.35, [(0.0, 700.0), (0.35, 300.0)], 0.4)),
                   (0.25, hiss(rng, 0.6, 1800.0, 6000.0, [(0.0, 0.0), (0.1, 1.0), (0.6, 0.0)], 0.3)))


def sb_spore_windup(rng, b):
    """The Blightcap on his head swelling: a fungal creak, a wet stretching, rising."""
    d = 1.05
    c = creak(rng, d, [(0.0, 18.0), (d, 45.0)], (b.low(180.0), b.low(450.0), 980.0)) * env([(0.0, 0.0), (d, 1.0)], d)
    sq = squelch(rng, d, [(0.0, 250.0), (d, 900.0)], 0.7)
    sq = fit(sq, d) * env([(0.0, 0.2), (d, 1.0)], d)
    return S.place(d, (0.0, c * 0.6), (0.0, sq))


def sb_spore_burst(rng, b):
    """The cap bursting: a deep wet POOF, then a long airy hiss of spores spreading."""
    n = S.n_of(0.5)
    t = np.arange(n) / SR
    poof = norm(S.lowpass(S.noise(n, rng, "pink"), 900.0)) * np.exp(-t / 0.12)
    return S.place(2.0, (0.0, poof), (0.0, boom(rng, b, 0.6, 0.7)), (0.02, bubbles(rng, 0.6, 50.0, 120.0, 500.0, 0.5)),
                   (0.05, hiss(rng, 1.8, 1200.0, 7000.0, [(0.0, 0.0), (0.15, 1.0), (1.8, 0.0)], 0.6)))


# ---- the Cinder Knight (fire, heavy)


def ci_swing(rng, b):
    """The fire hammer swung: a heavy low whoosh, fire roaring off its head, embers flying, a dull clank."""
    w = whoosh(rng, b, 0.5, 180.0, 900.0, 1.0, 0.6)
    return S.place(1.0, (0.0, w), (0.05, roar(rng, 0.6, 60.0, 700.0, 10.0, level=0.6)),
                   (0.3, crackle(rng, 0.6, 160.0, 0.4)), (0.3, clank(rng, b.w, 0.35)))


def ci_slam_windup(rng, b):
    """The hammer raised high: its fire roaring up, crackling harder."""
    d = 1.0
    r = roar(rng, d, 50.0, 800.0, 8.0, [(0.0, 0.0), (d * 0.85, 1.0), (d, 0.6)])
    c = crackle(rng, d, 200.0, 0.5) * env([(0.0, 0.1), (d, 1.0)], d)
    return S.place(d, (0.0, r), (0.0, c))


def ci_slam(rng, b):
    """The slam: a huge boom, a ring of fire bursting out, embers raining, a rumble rolling off."""
    return S.place(2.2, (0.0, boom(rng, b, 1.3, 1.0)), (0.0, S.crack(rng, 0.15) * 0.5),
                   (0.02, roar(rng, 1.6, 40.0, 900.0, 6.0, [(0.0, 1.0), (1.6, 0.0)], 0.9)),
                   (0.05, crackle(rng, 1.8, 200.0, 0.5) * env([(0.0, 1.0), (1.8, 0.0)], 1.8)),
                   (0.0, clank(rng, b.w, 0.4)), (0.1, rumble(rng, 2.0, b.low(55.0), 0.6, [(0.0, 1.0), (2.0, 0.0)])))


# ---- Flamehand (fire, light)


def fh_fireball(rng, b):
    """A fireball flung from his fist: a quick bright whoosh of flame, a crackle."""
    return S.place(0.7, (0.0, flame(rng, 0.45, 700.0, 2600.0)), (0.0, crackle(rng, 0.4, 260.0, 0.3)))


def fh_charge(rng, b):
    """The great fireball growing between his fists: fire roaring up higher and higher, crackling, for 1.9 s."""
    d = 1.85
    n = S.n_of(d)
    x = S.sweep_filter(S.noise(n, rng, "pink"), S.curve([(0.0, 200.0), (d, 1400.0)], n, log=True), 0.5)
    x = norm(x) * wobble(rng, n, 9.0, 0.4) * env([(0.0, 0.0), (d * 0.9, 1.0), (d, 0.7)], d)
    c = crackle(rng, d, 300.0, 0.6) * env([(0.0, 0.1), (d, 1.0)], d)
    return S.place(d, (0.0, x), (0.0, c), (0.0, roar(rng, d, 60.0, 500.0, 5.0, [(0.0, 0.0), (d, 1.0)], 0.5)))


def fh_great_fireball(rng, b):
    """The great fireball let go: a big deep WHOOMP of flame driving off."""
    return S.place(1.3, (0.0, flame(rng, 1.0, 250.0, 1400.0)), (0.0, boom(rng, b, 0.5, 0.6)),
                   (0.0, crackle(rng, 1.0, 200.0, 0.4) * env([(0.0, 1.0), (1.0, 0.0)], 1.0)))


def fh_blast(rng, b):
    """The great fireball bursting: a boom, a ball of fire roaring out, embers falling."""
    big = Build("blast", "", "fire", 0.9, 0.0, 1.0, 1.0, 1.0, 0.0)
    return S.place(2.0, (0.0, boom(rng, big, 1.2, 1.0)), (0.0, S.crack(rng, 0.12) * 0.6),
                   (0.01, roar(rng, 1.6, 50.0, 1200.0, 8.0, [(0.0, 1.0), (1.6, 0.0)], 1.0)),
                   (0.1, crackle(rng, 1.7, 160.0, 0.5) * env([(0.0, 1.0), (1.7, 0.0)], 1.7)))


def fh_fizzle(rng, b):
    """A fireball hitting the ground: a hiss and a few embers."""
    return S.place(0.6, (0.0, hiss(rng, 0.5, 2000.0, 8000.0, [(0.0, 1.0), (0.5, 0.0)])), (0.0, crackle(rng, 0.4, 90.0, 0.5)))


# ---- the Wreck Knight (thunder, the heaviest: everything low, slow and fat)


def wr_throw_windup(rng, b):
    """Swinging the anchor round on its chain before the throw: the chain rattling in two heavy swings, a low hum."""
    d = 0.75
    swings = [(i * 0.35, S.whoosh(rng, 0.35, b.low(160.0), b.low(500.0), [(0.0, 0.0), (0.2, 1.0), (0.35, 0.0)]) * 0.8)
              for i in range(2)]
    return S.place(d, *swings, (0.0, chain(rng, d, 40.0, b.w, 0.35)), (0.0, hum(rng, d, b.low(60.0), 0.5)),
                   (0.0, rumble(rng, d, b.low(50.0), 0.4)))


def wr_anchor_throw(rng, b):
    """The anchor hurled: a huge low whoosh, its chain running out after it, crackling."""
    w = whoosh(rng, b, 0.6, 120.0, 600.0, 1.0, 0.4)
    run = fit(chain(rng, 0.8, 70.0, b.w, 0.8), 0.8) * env([(0.0, 1.0), (0.8, 0.2)], 0.8)
    return S.place(1.2, (0.0, w), (0.05, run * 0.5), (0.0, sparks(rng, 0.6, 80.0, 0.2)),
                   (0.0, rumble(rng, 0.7, b.low(50.0), 0.6, [(0.0, 0.0), (0.2, 1.0), (0.7, 0.0)])))


def wr_anchor_hook(rng, b):
    """The anchor striking and hooking its foe: a deep clang, a thud, a zap."""
    return S.place(1.1, (0.0, clank(rng, b.w, 1.0)), (0.0, boom(rng, b, 0.6, 0.6)),
                   (0.01, zap(rng, 0.3, b.low(700.0), b.low(120.0), 0.5)), (0.05, chain(rng, 0.4, 50.0, b.w, 0.5)))


def wr_anchor_haul(rng, b):
    """The chain hauled in (every few ticks while he pulls): a ratcheting rattle and a strain."""
    d = 0.32
    ratchet = S.place(d, *[(i * 0.045, S.clink(rng, b.low(1300.0), (1.0, 1.6), 0.08, 0.02) * (1.0 - 0.1 * i))
                           for i in range(6)])
    c = creak(rng, d, [(0.0, 30.0), (d, 22.0)], (b.low(240.0), b.low(700.0)), 0.5)
    return S.place(d, (0.0, norm(ratchet)), (0.0, c))


def wr_anchor_clang(rng, b):
    """The anchor hitting a wall: a clang and a stony thud."""
    return S.place(0.9, (0.0, clank(rng, b.w, 0.9)), (0.0, stone_knock(rng, 0.6, 0.6)))


def wr_anchor_catch(rng, b):
    """The anchor back in his fist: the chain piling up, a heavy clunk."""
    return S.place(0.8, (0.0, chain(rng, 0.4, 60.0, b.w, 0.4)), (0.25, clank(rng, b.w, 0.8)), (0.25, boom(rng, b, 0.5, 0.5)))


def wr_slam_windup(rng, b):
    """The anchor raised over his head: lightning charging up in it, a rising electric hum, the chain rattling."""
    d = 1.0
    n = S.n_of(d)
    t = np.arange(n) / SR
    f = b.low(55.0) * (1.0 + 1.2 * (t / d) ** 2)
    x = norm(S.lowpass(S.saw(f, 0.8, 40), 2500.0)) * env([(0.0, 0.0), (d * 0.9, 1.0), (d, 0.8)], d)
    return S.place(d, (0.0, x), (0.0, sparks(rng, d, 160.0, 0.4) * env([(0.0, 0.0), (d, 1.0)], d)),
                   (0.0, chain(rng, 0.5, 30.0, b.w, 0.4)))


def wr_anchor_slam(rng, b):
    """The anchor slammed down with a bolt of lightning: a massive boom, a deep clang, thunder rolling away."""
    return S.place(2.6, (0.0, boom(rng, b, 1.4, 1.0)), (0.0, clank(rng, b.w, 0.8)), (0.0, thunder(rng, 2.4, b.w, 0.9)),
                   (0.02, zap(rng, 0.4, 1600.0, 90.0, 0.5)))


# ---- the Stormblade (thunder, the lightest: everything high, short and bright)


def st_slash(rng, b):
    """One of his two quick cuts: a thin high zing of a blade and lightning crackling along it."""
    w = whoosh(rng, b, 0.16, 1800.0, 6000.0, 1.0, 0.6)
    return S.place(0.45, (0.0, w), (0.06, zap(rng, 0.14, 4200.0, 2400.0, 0.5)), (0.04, sparks(rng, 0.25, 600.0, 0.4)),
                   (0.08, ring(rng, 0.3, (4100.0, 6200.0), 0.06, 0.25)))


def st_dash(rng, b):
    """The lightning dash: a zap tearing upward, a crack, a rushing whoosh."""
    return S.place(0.8, (0.0, zap(rng, 0.25, 500.0, 3800.0, 0.9)), (0.0, S.crack(rng, 0.1) * 0.7),
                   (0.05, S.whoosh(rng, 0.5, 900.0, 5000.0, [(0.0, 0.0), (0.1, 1.0), (0.5, 0.0)], color="white") * 0.7),
                   (0.0, sparks(rng, 0.6, 500.0, 0.4)))


def st_dash_crackle(rng, b):
    """Crackling as he runs (every few ticks of the dash)."""
    return S.place(0.2, (0.0, sparks(rng, 0.18, 900.0, 1.0)), (0.0, zap(rng, 0.1, 3000.0, 1500.0, 0.4)))


def st_trail(rng, b):
    """The lightning he leaves on the ground (2 s): a fizzing, buzzing crackle dying away."""
    d = 2.2
    n = S.n_of(d)
    t = np.arange(n) / SR
    buzz = norm(S.bandpass(S.saw(np.full(n, 180.0), 1.0, 40), 600.0, 6000.0)) * (0.5 + 0.5 * (np.sin(2 * math.pi * 11 * t) > 0.6))
    shape = env([(0.0, 0.0), (0.05, 1.0), (d * 0.6, 0.6), (d, 0.0)], d)
    return (buzz * 0.4 + sparks(rng, d, 350.0, 0.8)) * shape


# ---- the Geode Knight (earth, middling)


def ge_pick(rng, b):
    """His war pick swung: a whoosh, a hard stony strike and an amethyst ping."""
    w = whoosh(rng, b, 0.3, 400.0, 1800.0, 1.0, 0.7)
    return S.place(1.0, (0.0, w), (0.18, stone_knock(rng, 0.9, 0.8)), (0.19, chime(rng, 2600.0, 0.8, 0.22, 0.35)))


def ge_burst_windup(rng, b):
    """Before the crystal burst: stone grinding under him, crystals tinkling into life, rising."""
    d = 1.0
    g = grind(rng, d, 120.0, 1000.0, 400.0, 1.0, [(0.0, 0.0), (d, 1.0)])
    return S.place(d, (0.0, g * 0.8), (0.2, chimes(rng, 0.8, 8, 2200.0, 5200.0, 0.35)))


def ge_crystal_burst(rng, b):
    """The burst: a stony boom and a cascade of amethyst bursting up out of the ground."""
    return S.place(2.0, (0.0, boom(rng, b, 0.9, 0.9)), (0.0, gravel(rng, 0.8, 0.5) * env([(0.0, 1.0), (0.8, 0.0)], 0.8)),
                   (0.03, chimes(rng, 1.6, 22, 1500.0, 5200.0, 0.8)))


def ge_crystals(rng, b):
    """A ring of crystals bursting up: grinding cracks and their chimes."""
    return S.place(1.2, (0.0, grind(rng, 0.35, 200.0, 2000.0, 700.0, 0.7, [(0.0, 1.0), (0.35, 0.0)])),
                   (0.02, chimes(rng, 1.0, 12, 1800.0, 4800.0, 0.8)))


# ---- the Rockfall Knight (earth, heavy)


def rf_heave(rng, b):
    """Heaving the boulder up to throw: deep stone grinding and a rumble as it lifts."""
    d = 0.75
    return S.place(d, (0.0, grind(rng, d, 80.0, 700.0, 300.0, 1.0, [(0.0, 0.0), (d * 0.8, 1.0), (d, 0.6)])),
                   (0.0, rumble(rng, d, b.low(50.0), 0.5, [(0.0, 0.0), (d, 1.0)])))


def rf_throw(rng, b):
    """The boulder let go: a heavy low whoosh, grit spraying off it."""
    w = whoosh(rng, b, 0.55, 100.0, 500.0, 1.0, 0.35)
    return S.place(1.0, (0.0, w), (0.0, gravel(rng, 0.5, 0.5) * env([(0.0, 1.0), (0.5, 0.0)], 0.5)))


def rf_rockfall_windup(rng, b):
    """The boulder raised high before the rockfall: the ground grinding and rumbling, building."""
    d = 1.05
    return S.place(d, (0.0, grind(rng, d, 70.0, 600.0, 350.0, 1.0, [(0.0, 0.0), (d, 1.0)])),
                   (0.0, rumble(rng, d, b.low(45.0), 0.7, [(0.0, 0.0), (d, 1.0)])))


def rf_rockfall(rng, b):
    """The rockfall: the boulder brought down with a boom, a long rumble, gravel and rocks clattering down."""
    rocks = [(rng.uniform(0.3, 2.0), stone_knock(rng, rng.uniform(0.5, 0.9), rng.uniform(0.3, 0.7))) for _ in range(7)]
    return S.place(2.8, (0.0, boom(rng, b, 1.4, 1.0)), (0.0, S.crack(rng, 0.12) * 0.4),
                   (0.02, rumble(rng, 2.6, b.low(45.0), 0.8, [(0.0, 1.0), (2.6, 0.0)])),
                   (0.1, gravel(rng, 2.2, 0.5) * env([(0.0, 1.0), (2.2, 0.0)], 2.2)), *rocks)


# ---------------------------------------------------------------- the events


class Event:
    """One sound event, `entity.knight_<key>.<name>`: its files (each a version; played, the game picks one at random)."""

    def __init__(self, key, name, files, subtitle, label):
        self.key, self.name, self.files, self.subtitle, self.label = key, name, files, subtitle, label

    @property
    def event(self):
        return f"entity.knight_{self.key}.{self.name}"

    @property
    def subtitle_key(self):
        return f"subtitles.dross.knight_{self.key}.{self.name}"

    @property
    def folder(self):
        return f"knight/{self.key}"


COMMON = (
    # name, maker, versions, subtitle verb, label, what each version is
    ("ambient", k_ambient, 2, "breathes", "Idle", ("two slow breaths through the helm, in his element",
                                                  "a low murmur of his element, his armour shifting")),
    ("effort", k_effort, 2, "grunts", "The strain as a move starts", ("a short bark", "a bigger heave, rising")),
    ("hurt", k_hurt, 2, "hurts", "Hurt", ("a sharp cry cut short, his armour knocked",
                                          "a lower, longer groan, his armour knocked")),
    ("death", k_death, 1, "dies", "Death", ("a long cry falling away, a last breath, his armour clattering down, "
                                            "his element's end",)),
    ("step", k_step, 1, "clanks", "Footsteps", ("an armoured step",)),
)

# key: (name, maker, subtitle, label)
MOVES = {
    "thawed_axe": (("swing", ta_swing, "Thawed Axeman swings", "Axe swing"),
                   ("smash_windup", ta_smash_windup, "Ice creaks", "Smash: raising the axe"),
                   ("smash", ta_smash, "Thawed Axeman smashes the ground", "Smash: the blow"),
                   ("spike", ta_spike, "Ice spike bursts up", "Smash: each small ice spike"),
                   ("big_spike", ta_big_spike, "Great ice spike bursts up", "Smash: the big spike")),
    "thawed_spear": (("thrust", ts_thrust, "Thawed Spearman thrusts", "Spear thrust"),
                     ("guard", ts_guard, "Shield raised", "Shield wall: the shield up"),
                     ("poke", ts_poke, "Thawed Spearman jabs", "Shield wall: each jab"),
                     ("block", ts_block, "Shield blocks", "A blow caught on the shield")),
    "blight_warden": (("cast", bw_cast, "Blight Warden casts", "Plague ball thrown"),
                      ("rain_call", bw_rain_call, "Blight Warden chants", "Blight rain: the call"),
                      ("rain", bw_rain, "Poison rain falls", "Blight rain: the rain")),
    "sporebound": (("reap", sb_reap, "Sporebound reaps", "Scythe reap"),
                   ("spore_windup", sb_spore_windup, "Fungus swells", "Spore burst: the cap swelling"),
                   ("spore_burst", sb_spore_burst, "Spores burst", "Spore burst: the burst")),
    "cinder": (("swing", ci_swing, "Cinder Knight swings", "Hammer swing"),
               ("slam_windup", ci_slam_windup, "Fire roars", "Fire slam: the hammer raised"),
               ("slam", ci_slam, "Cinder Knight slams the ground", "Fire slam: the blow")),
    "flamehand": (("fireball", fh_fireball, "Flamehand hurls fire", "Fireball"),
                  ("charge", fh_charge, "Fire gathers", "Great fireball: growing in his hands"),
                  ("great_fireball", fh_great_fireball, "Great fireball flies", "Great fireball: let go"),
                  ("blast", fh_blast, "Great fireball explodes", "Great fireball: the burst"),
                  ("fizzle", fh_fizzle, "Fireball fizzles", "A fireball hitting the ground")),
    "wreck": (("throw_windup", wr_throw_windup, "Chain swings", "Anchor throw: swinging it round"),
              ("anchor_throw", wr_anchor_throw, "Anchor flies", "Anchor throw: let go"),
              ("anchor_hook", wr_anchor_hook, "Anchor hooks", "Anchor throw: it hooks its foe"),
              ("anchor_haul", wr_anchor_haul, "Chain hauls", "Anchor throw: hauling in"),
              ("anchor_clang", wr_anchor_clang, "Anchor clangs", "Anchor throw: it hits a wall"),
              ("anchor_catch", wr_anchor_catch, "Anchor caught", "Anchor throw: back in his hand"),
              ("slam_windup", wr_slam_windup, "Lightning charges", "Anchor slam: raised, charging"),
              ("anchor_slam", wr_anchor_slam, "Anchor slams down", "Anchor slam: the blow and the lightning")),
    "stormblade": (("slash", st_slash, "Stormblade slashes", "Each quick cut"),
                   ("dash", st_dash, "Stormblade dashes", "Lightning dash: the start"),
                   ("dash_crackle", st_dash_crackle, "Lightning crackles", "Lightning dash: crackling as he runs"),
                   ("trail", st_trail, "Lightning trail buzzes", "Lightning dash: the trail left behind")),
    "geode": (("pick", ge_pick, "Geode Knight strikes", "War pick"),
              ("burst_windup", ge_burst_windup, "Stone grinds", "Crystal burst: building"),
              ("crystal_burst", ge_crystal_burst, "Crystals burst up", "Crystal burst: the burst"),
              ("crystals", ge_crystals, "Crystals burst up", "Crystal burst: each ring of crystals")),
    "rockfall": (("heave", rf_heave, "Boulder heaved", "Boulder throw: lifting it"),
                 ("throw", rf_throw, "Boulder flies", "Boulder throw: let go"),
                 ("rockfall_windup", rf_rockfall_windup, "Ground rumbles", "Rockfall: the boulder raised"),
                 ("rockfall", rf_rockfall, "Rocks fall", "Rockfall: the blow and the falling rocks")),
}


def build_events():
    events = []
    for key, b in BUILDS.items():
        for name, maker, count, verb, label, what in COMMON:
            files = [(name if count == 1 else f"{name}_{i}", (lambda r, m=maker, k=i, bb=b: m(r, bb, k)), what[i - 1])
                     for i in range(1, count + 1)]
            events.append(Event(key, name, files, f"{b.name} {verb}", label))
        for name, maker, subtitle, label in MOVES[key]:
            events.append(Event(key, name, [(name, (lambda r, m=maker, bb=b: m(r, bb)), label)], subtitle, label))
    return events


EVENTS = build_events()


# ---------------------------------------------------------------- writing and measuring

_hashes = {}


def render(ev, name, maker):
    rng = np.random.default_rng(zlib.crc32(f"{ev.folder}/{name}".encode()))
    x = S.finish(maker(rng))
    path = os.path.join(SOUND_DIR, ev.key, name + ".ogg")
    key = f"{ev.folder}/{name}"
    digest = hashlib.sha1(x.astype(np.float32).tobytes()).hexdigest()
    if not (os.path.exists(path) and _hashes.get(key) == digest):
        soundfile.write(path, x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        _hashes[key] = digest
    return x


def measure(x):
    """How long (s), how loud (RMS, dB below full scale), its brightness (spectral centroid, Hz) and its share of energy
    under 200 Hz (how low and heavy it sounds)."""
    spec = np.abs(np.fft.rfft(x)) ** 2
    freqs = np.fft.rfftfreq(len(x), 1.0 / SR)
    total = np.sum(spec) + 1e-12
    rms = np.sqrt(np.mean(x ** 2)) + 1e-12
    out = {"seconds": len(x) / SR, "rms_db": 20.0 * math.log10(rms), "centroid": float(np.sum(freqs * spec) / total),
           "low": float(np.sum(spec[freqs < 200.0]) / total)}
    v = VC.measure_voice(x)
    out.update({k: v[k] for k in ("periodicity", "harmonic", "flatness", "vowel")})
    return out


def _no_duplicates(pairs):
    """For json.loads: a key twice is an error (Minecraft refuses a sounds.json with one, and every Dross sound goes
    silent)."""
    seen = {}
    for k, v in pairs:
        if k in seen:
            raise ValueError(f"duplicate key {k}")
        seen[k] = v
    return seen


OURS = "entity.knight_"
# His own body's sounds (not his weapons' and element's moves): what the heavy-against-light table compares.
BODY = ("ambient", "effort", "hurt", "death", "step")
# His voice (what must sound like no one's voice, least of all the Rimefall folk's): what the voice table compares.
VOICE = ("ambient", "effort", "hurt", "death")
OUR_SUBTITLES = "subtitles.dross.knight_"


def sound_line(folder, name):
    return f'      {{"name": "dross:{folder}/{name}"}}'


def entry(event, subtitle_key, lines):
    return f'  "{event}": {{\n    "subtitle": "{subtitle_key}",\n    "sounds": [\n' + ",\n".join(lines) + "\n    ]\n  }"


def write_sounds_json():
    """Adds (or replaces) this tool's events (entity.knight_*) in sounds.json, keeping every other entry as it was."""
    with open(SOUNDS_JSON, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text, object_pairs_hook=_no_duplicates)
    lines = text.rstrip().rstrip("}").rstrip().split("\n")
    keep, skipping, depth = [], False, 0
    for line in lines:
        stripped = line.strip()
        if not skipping and depth == 0 and stripped.startswith('"' + OURS):
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
        entries.append(entry(ev.event, ev.subtitle_key, [sound_line(ev.folder, n) for n, _m, _l in ev.files]))
        if len(ev.files) > 1:
            for i, (n, _m, _l) in enumerate(ev.files, start=1):
                entries.append(entry(f"{ev.event}.v{i}", ev.subtitle_key, [sound_line(ev.folder, n)]))
    out = body + ",\n" + ",\n".join(entries) + "\n}\n"
    parsed = json.loads(out, object_pairs_hook=_no_duplicates)
    old = {k for k in data if not k.startswith(OURS)}
    assert old <= set(parsed), "lost an entry"
    assert {k: v for k, v in data.items() if k in old}.items() <= parsed.items(), "changed an entry"
    with open(SOUNDS_JSON, "w", encoding="utf-8", newline="\n") as f:
        f.write(out)
    return len(parsed) - len(old)


def write_lang():
    """Adds the subtitles (its own keys kept up to date, a stale one removed); every other line stays byte for byte,
    with the file's own line endings."""
    with open(LANG, "rb") as f:
        raw = f.read()
    nl = b"\r\r\n" if b"\r\r\n" in raw else (b"\r\n" if b"\r\n" in raw else b"\n")
    data = json.loads(raw.decode("utf-8"), object_pairs_hook=_no_duplicates)
    lines = raw.rstrip().split(nl)
    assert lines[-1].strip() == b"}", "the lang file doesn't end in }"
    body = [ln for ln in lines[1:-1] if not ln.strip().startswith(b'"' + OUR_SUBTITLES.encode())]
    body = [ln.rstrip() for ln in body if ln.strip()]
    body[-1] = body[-1].rstrip(b",")
    want = {}
    for ev in EVENTS:
        want.setdefault(ev.subtitle_key, ev.subtitle)
    added = [f"  {json.dumps(k)}: {json.dumps(v)}".encode("utf-8") for k, v in want.items()]
    body = [ln if ln.endswith(b",") else ln + b"," for ln in body[:-1]] + [body[-1] + b","] + \
           [a + b"," for a in added[:-1]] + [added[-1]]
    out = lines[0] + nl + nl.join(body) + nl + b"}" + nl
    parsed = json.loads(out.decode("utf-8"), object_pairs_hook=_no_duplicates)
    assert {k: v for k, v in data.items() if not k.startswith(OUR_SUBTITLES)}.items() <= parsed.items(), "lost a key"
    with open(LANG, "wb") as f:
        f.write(out)
    return len(want)


def write_java():
    rows = []
    for ev in EVENTS:
        plays = [f"{ev.event}.v{i}" for i in range(1, len(ev.files) + 1)] if len(ev.files) > 1 else [ev.event]
        files = [f"sounds/{ev.folder}/{n}.ogg" for n, _m, _l in ev.files]
        rows.append(f'            new Sound({json.dumps(ev.key)}, {json.dumps(ev.event)}, {json.dumps(ev.label)},\n'
                    f'                    List.of({", ".join(json.dumps(p) for p in plays)}),\n'
                    f'                    List.of({", ".join(json.dumps(lab) for _n, _m, lab in ev.files)}),\n'
                    f'                    List.of({", ".join(json.dumps(f) for f in files)}))')
    joined = ",\n".join(rows)
    src = f"""package com.reg21meme.dross.boss.knight;

import java.util.List;

/**
 * Every sound of the ten elemental knights, written by {{@code tools/knights/sounds.py}} (generated: don't edit by hand;
 * change the sounds there and run it again). All of them are original, synthesized by that tool: each element its own
 * character (ice, poison, fire, thunder, earth), each knight his own weight and voice (the Wreck Knight the heaviest,
 * the Stormblade the lightest). {{@code ModSounds.KNIGHT_SOUNDS}} registers them, {{@link ElementalKnight}} plays them
 * ({{@link #event}}), {{@code /dross showcase boss_sounds knights}} lists them with a button for each version.
 */
public final class KnightSounds
{{
    /**
     * One sound event and its versions.
     *
     * @param knight   whose it is ({{@code KnightKind}}'s name in lower case: {{@code thawed_axe}}...)
     * @param event    its id ({{@code dross:<event>}}; played, it picks one of its versions at random)
     * @param label    what it is
     * @param plays    each version on its own (its event id: {{@code <event>.v<n>}}, or the event itself if it has one)
     * @param versions what each version is, in the same order
     * @param files    each version's file in the mod ({{@code assets/dross/...}})
     */
    public record Sound(String knight, String event, String label, List<String> plays, List<String> versions,
                        List<String> files)
    {{
    }}

    public static final List<Sound> ALL = List.of(
{joined});

    /** The id of one of a knight's sound events ({{@code entity.knight_<kind>.<name>}}). */
    public static String event(KnightKind kind, String name)
    {{
        return "entity." + kind.typeName() + "." + name;
    }}

    private KnightSounds() {{}}
}}
"""
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)


ELEMENT_COLOURS = {"ice": "#bfe4f2", "poison": "#96d648", "fire": "#ff8a3a", "thunder": "#7fa8ff", "earth": "#e8a032"}


def write_page(measures):
    os.makedirs(PREVIEWS, exist_ok=True)
    html = ['<!doctype html><meta charset="utf-8"><title>The knights\' sounds</title>',
            "<style>body{font-family:system-ui,sans-serif;background:#14151a;color:#ececf2;margin:24px;max-width:1200px}"
            "h1{font-size:22px}h2{font-size:17px;margin-top:28px}"
            ".row{margin:6px 0;padding:6px 10px;background:#1f2129;border-radius:6px}"
            ".label{font-weight:600}.id{color:#8d93a6;font-size:12px}"
            ".v{display:inline-flex;flex-direction:column;margin:4px 14px 4px 0;font-size:13px;color:#c9cfdc}"
            ".m{color:#9aa3b6;font-size:12px}audio{height:30px;margin-top:2px}"
            "table{border-collapse:collapse;font-size:13px}td,th{padding:3px 10px;border-bottom:1px solid #2a2d38;"
            "text-align:right}td:first-child,th:first-child{text-align:left}</style>",
            "<h1>The elemental knights' sounds</h1>",
            "<p>All original: made from scratch by <code>tools/knights/sounds.py</code> and its voice engine "
            "<code>knightvoice.py</code>. Each element has its own character, each knight his own weight: the heavy "
            "ones low (much of their sound under 200 Hz), the light ones high and short. Their voices are no one's "
            "voice: no throat, no vowels, each made of his element inside his sealed helm. "
            "<code>/dross showcase boss_sounds knights</code> plays them in the game.</p>",
            "<h2>No one's voice: how voice-like, against the Rimefall folk</h2>"
            "<p>Idle, grunts, hurts and death of each (<code>tools/knights/voicecheck.py</code>). <b>periodicity</b>: "
            "how strongly the sound repeats at a pitch a voice could have (white noise 0.11, a buzz 1.0); "
            "<b>harmonic</b>: how far one comb of harmonics stands out of its gaps (noise about 4 dB); "
            "<b>vowel band</b>: its share of energy at 250-2500 Hz, where vowels live. The Huntsman and the Frostfolk "
            "are voices (made with the Huntsman's voicekit); the knights must not be.</p><table>"
            "<tr><th></th><th>periodicity</th><th>harmonic (dB)</th><th>noisiness</th><th>vowel band</th>"
            "<th>brightness (Hz)</th></tr>",
            *[f"<tr><td>{folder} (voicekit)</td><td>{m['periodicity']:.2f}</td><td>{m['harmonic']:.1f}</td>"
              f"<td>{m['flatness']:.3f}</td><td>{m['vowel'] * 100:.0f}%</td><td>{m['centroid']:.0f}</td></tr>"
              for folder, m in VC.rimefall_measures().items()],
            *[f"<tr><td>{b.name}</td><td>{m['periodicity']:.2f}</td><td>{m['harmonic']:.1f}</td>"
              f"<td>{m['flatness']:.3f}</td><td>{m['vowel'] * 100:.0f}%</td><td>{m['centroid']:.0f}</td></tr>"
              for b, m in ((BUILDS[key], VC.mean_of([mm for (k, n), mm in measures.items()
                                                      if k == key and n.startswith(VOICE)]))
                           for key in BUILDS)],
            "</table>",
            "<h2>Heavy against light (his voice, breath, hurt, death and steps together)</h2><table>"
            "<tr><th>Knight</th><th>weight</th><th>voice</th><th>brightness (Hz)</th><th>energy under 200 Hz</th>"
            "<th>step (s)</th><th>hurt brightness (Hz)</th></tr>"]
    for key, b in sorted(BUILDS.items(), key=lambda kv: -kv[1].w):
        ms = [m for (k, n), m in measures.items() if k == key and n.startswith(BODY)]
        cen = np.mean([m["centroid"] for m in ms])
        low = np.mean([m["low"] for m in ms])
        step = measures[(key, "step")]["seconds"]
        hurt = measures[(key, "hurt_1")]["centroid"]
        html.append(f"<tr><td>{b.name}</td><td>{b.w:.2f}</td><td>{b.f0:.0f} Hz</td><td>{cen:.0f}</td>"
                    f"<td>{low * 100:.0f}%</td><td>{step:.2f}</td><td>{hurt:.0f}</td></tr>")
    html.append("</table>")
    for key, b in BUILDS.items():
        html.append(f'<h2 style="color:{ELEMENT_COLOURS[b.element]}">{b.name} ({b.element})</h2>')
        for ev in [e for e in EVENTS if e.key == key]:
            players = "".join(
                f'<span class="v">{lab}<span class="m">{measures[(key, n)]["seconds"]:.2f} s, '
                f'{measures[(key, n)]["centroid"]:.0f} Hz, {measures[(key, n)]["low"] * 100:.0f}% under 200 Hz, '
                f'periodicity {measures[(key, n)]["periodicity"]:.2f}</span>'
                f'<audio controls preload="none" src="../../../src/main/resources/assets/dross/sounds/{ev.folder}/{n}.ogg">'
                f'</audio></span>' for n, _m, lab in ev.files)
            html.append(f'<div class="row"><div class="label">{ev.label}</div><div class="id">dross:{ev.event}</div>'
                        f'{players}</div>')
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(html) + "\n")


def main():
    only = set(sys.argv[1].split(",")) if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else None
    if os.path.exists(HASHES):
        with open(HASHES, encoding="utf-8") as f:
            _hashes.update(json.load(f))
    measures = {}
    count = 0
    for ev in EVENTS:
        os.makedirs(os.path.join(SOUND_DIR, ev.key), exist_ok=True)
        for name, maker, _lab in ev.files:
            if only and ev.key not in only:
                path = os.path.join(SOUND_DIR, ev.key, name + ".ogg")
                x, _sr = soundfile.read(path)
            else:
                x = render(ev, name, maker)
            measures[(ev.key, name)] = measure(x)
            count += 1
    for key in BUILDS:
        names = {n + ".ogg" for ev in EVENTS if ev.key == key for n, _m, _l in ev.files}
        for fn in os.listdir(os.path.join(SOUND_DIR, key)):
            if fn.endswith(".ogg") and fn not in names:
                os.remove(os.path.join(SOUND_DIR, key, fn))
                print(f"  removed stale knight/{key}/{fn}")
    files = {f"{ev.folder}/{n}" for ev in EVENTS for n, _m, _l in ev.files}
    with open(HASHES, "w", encoding="utf-8", newline="\n") as f:
        json.dump({k: v for k, v in sorted(_hashes.items()) if k in files}, f, indent=1)
        f.write("\n")
    print(f"{'knight':<16}{'w':>5}{'brightness':>12}{'<200 Hz':>9}{'step s':>8}{'death s':>9}")
    for key, b in sorted(BUILDS.items(), key=lambda kv: -kv[1].w):
        ms = [m for (k, n), m in measures.items() if k == key and n.startswith(BODY)]
        print(f"{key:<16}{b.w:>5.2f}{np.mean([m['centroid'] for m in ms]):>12.0f}"
              f"{np.mean([m['low'] for m in ms]) * 100:>8.0f}%{measures[(key, 'step')]['seconds']:>8.2f}"
              f"{measures[(key, 'death')]['seconds']:>9.2f}")
    print()
    print("How voice-like (idle, grunts, hurts, death), against the Rimefall folk's voicekit voices:")
    print(VC.HEADER)
    for folder, m in VC.rimefall_measures().items():
        print(VC.row(folder, m))
    for key in BUILDS:
        print(VC.row(key, VC.mean_of([m for (k, n), m in measures.items() if k == key and n.startswith(VOICE)])))
    # a sound that could pass for a voice: periodic, a comb of harmonics and most of its energy where vowels are (an
    # ice ring or a thud is periodic too, but sits far above or below the vowels)
    loud = [(k, n, m) for (k, n), m in measures.items()
            if m["periodicity"] > 0.8 and m["harmonic"] > 15.0 and m["vowel"] > 0.6]
    print("Sounds that could pass for a voice (periodicity over 0.8, a comb of harmonics over 15 dB, 60% in the vowel "
          "band):",
          ", ".join(f"{k}/{n}" for k, n, _m in loud) or "none")
    if "--verbose" in sys.argv:
        for (key, name), m in measures.items():
            print(f"  {key}/{name}: {m['seconds']:.2f} s, {m['rms_db']:.1f} dB, {m['centroid']:.0f} Hz, "
                  f"{m['low'] * 100:.0f}% low")
    entries = write_sounds_json()
    subs = write_lang()
    write_java()
    write_page(measures)
    print(f"{count} sounds in {len(EVENTS)} events ({entries} sounds.json entries, {subs} subtitles). Done.")


if __name__ == "__main__":
    main()
