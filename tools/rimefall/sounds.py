"""
Makes the Rimefall animals' sounds (CLAUDE.md step 14): the Rimehorn's, the moose's and the Pond Skater's. Every one is
synthesized from scratch with the Rat King's synthesizer (tools/ratking/ratkit/synth.py): nothing is recorded and
nothing comes from Minecraft (the user: "make sure it has custom sound effects ... original sounds if you can"). Most
events have a few versions; the game picks one at random each time, and the page below plays each version on its own,
so the user can say which to keep.

Run from the project folder:

    python tools/rimefall/sounds.py

It writes:
  src/main/resources/assets/dross/sounds/rimehorn/*.ogg, moose/*.ogg, pondskater/*.ogg    the sounds (mono Ogg Vorbis)
  src/main/resources/assets/dross/sounds.json        adds (or replaces) its own events; every other entry stays as it was
  src/main/resources/assets/dross/lang/en_us.json    adds the subtitles it needs (never changes other keys)
  src/main/java/com/reg21meme/dross/boss/rimefall/RimefallMobSounds.java   every event, for /dross showcase mob_sounds
  boss-previews/rimefall/mobs/sounds/index.html      a page that plays every version (open it in a browser)

Needs numpy, scipy and soundfile (`pip install soundfile`).
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
sys.path.insert(0, os.path.join(ROOT, "tools", "ratking"))

import numpy as np  # noqa: E402
import soundfile  # noqa: E402

from ratkit import synth as S  # noqa: E402

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
SOUND_DIR = os.path.join(ASSETS, "sounds")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "rimefall", "RimefallMobSounds.java")
PREVIEWS = os.path.join(ROOT, "boss-previews", "rimefall", "mobs", "sounds")


# ---------------------------------------------------------------- pieces


def env(points, dur):
    return S.smooth_env(points, S.n_of(dur))


def low_voice(rng, dur, f0a, f0b, vowel, vowel2=None, scale=0.62, rough=0.6, breath=0.3, rough_hz=32.0):
    """A big animal's voice: low, its mouth much bigger than a person's (the formants move down), growly."""
    return S.voice(dur, [(0.0, f0a), (dur, f0b)], [(0.0, vowel), (dur, vowel2 or vowel)], rng, scale=scale,
                   breath=breath, rough=rough, rough_hz=rough_hz,
                   amp_pts=[(0.0, 0.0), (min(0.06, dur * 0.2), 1.0), (dur * 0.6, 0.8), (dur, 0.0)])


def snort(rng, dur=0.32, lo=280.0, hi=1900.0, level=1.0):
    """Air blasted out through big nostrils: a band of noise with a sharp start and a flutter."""
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    x = S.bandpass(S.noise(n, rng, "pink"), lo, hi)
    flutter = 1.0 + 0.5 * np.sin(2 * math.pi * 31.0 * t)
    return x * flutter * env([(0.0, 0.0), (0.012, 1.0), (dur * 0.35, 0.6), (dur, 0.0)], dur) * level


def huff(rng, dur=0.22, level=0.7):
    n = S.n_of(dur)
    return S.bandpass(S.noise(n, rng), 600.0, 3500.0) * env([(0.0, 0.0), (0.03, 1.0), (dur, 0.0)], dur) * level


def crunch(rng, dur, rate=120.0, level=0.8, lo=1500.0, hi=7000.0):
    """Snow crunching: a burst of tiny grains."""
    return S.grains(rng, dur, rate, grain=(0.002, 0.008), lo=lo, hi=hi, level=level)


def hoof(rng, heavy=1.0):
    """A hoof coming down on packed snow: a thud and a short crunch."""
    pad = S.thud(rng, 70.0 * (1.4 - 0.4 * heavy), 40.0, 0.28, 0.25) * heavy
    return S.place(0.32, (0.0, pad), (0.005, crunch(rng, 0.09, 260.0, 0.45)))


def hoofbeats(rng, dur, count, heavy=1.0, grow=True):
    parts = []
    for i in range(count):
        at = i * dur / count + rng.uniform(-0.02, 0.02)
        lvl = (0.5 + 0.5 * i / max(1, count - 1)) if grow else 1.0
        parts.append((max(0.0, at), hoof(rng, heavy) * lvl))
    return S.place(dur + 0.35, *parts)


def chew(rng, dur, chomps):
    parts = []
    for i in range(chomps):
        at = i * dur / chomps
        parts.append((at, crunch(rng, 0.12, 300.0, 0.7, 900.0, 5000.0)))
        parts.append((at + 0.02, S.knock(rng, freqs=(180, 420, 900), qs=(5, 7, 9), gains=(0.4, 0.25, 0.1), dur=0.12, decay=0.03)))
    return S.place(dur + 0.2, *parts)


def tick(rng, lo=2500.0, level=0.6):
    return S.highpass(S.burst(0.02, rng, 0.002), lo) * level


def clicks(rng, dur, rate, lo=3000.0, level=0.6):
    """A run of hard little clicks (an insect's mouthparts and legs)."""
    parts, at = [], 0.0
    while at < dur:
        parts.append((at, tick(rng, lo + rng.uniform(0, 2500), level * rng.uniform(0.5, 1.0))))
        at += rng.exponential(1.0 / rate)
    return S.place(dur + 0.03, *parts)


def rasp(rng, dur, rate=34.0, lo=2200.0, hi=7500.0, level=0.8):
    """Stridulation: a leg drawn over a ridge, a dry buzzing rasp of tiny bursts."""
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    x = S.bandpass(S.noise(n, rng), lo, hi)
    gate = (0.5 + 0.5 * np.sin(2 * math.pi * rate * t)) ** 6
    return x * gate * env([(0.0, 0.0), (dur * 0.1, 1.0), (dur * 0.8, 0.9), (dur, 0.0)], dur) * level


def buzz(rng, dur, f0a, f0b, level=0.6):
    n = S.n_of(dur)
    f0 = S.curve([(0.0, f0a), (dur, f0b)], n, log=True)
    src = S.saw(f0, 0.8)
    src /= np.max(np.abs(src)) + 1e-9
    return S.bandpass(src, 400.0, 5000.0) * env([(0.0, 0.0), (0.02, 1.0), (dur * 0.7, 0.8), (dur, 0.0)], dur) * level


def swish(rng, dur, level=0.8):
    """A blade or a long leg sliding over ice: a bright hiss rising and falling, with a glassy shimmer."""
    hiss = S.whoosh(rng, dur, 2500.0, 6500.0, amp_pts=[(0.0, 0.0), (dur * 0.3, 1.0), (dur, 0.0)], color="white", width=0.5)
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    shimmer = np.sin(2 * math.pi * 5200.0 * t + 3.0 * np.sin(2 * math.pi * 37.0 * t)) * 0.12
    return (hiss + shimmer * env([(0.0, 0.0), (dur * 0.4, 1.0), (dur, 0.0)], dur)) * level


# ---------------------------------------------------------------- the Rimehorn
# The user (October 19): the Rimehorn and the moose "sound too similar". The Rimehorn is now all breath and chest: no
# calls at all, only deep rumbles, heavy wet chuffs through its nostrils (a rhino's), frost crackling in its breath and
# very deep footfalls. The moose (below) is the one with a voice.


def rumble(rng, dur, f=46.0, level=1.0, rough=0.9):
    """A deep chest rumble, a growl too low to be a call: a huge mouth's voice over a sub-bass swell."""
    v = low_voice(rng, dur, f * 1.08, f * 0.9, "oo", "uh", scale=0.45, rough=rough, breath=0.25, rough_hz=22.0)
    n = S.n_of(dur)
    sub = S.lowpass(S.noise(n, rng), 70.0)
    sub /= np.max(np.abs(sub)) + 1e-9
    swell = env([(0.0, 0.0), (min(0.15, dur * 0.25), 1.0), (dur * 0.7, 0.8), (dur, 0.0)], dur)
    return (v * 0.8 + sub * swell) * level


def chuff(rng, dur=0.26, level=1.0):
    """A heavy breath blown out through big nostrils: a low, wet whump of air (a rhino's chuff)."""
    n = S.n_of(dur)
    air = S.bandpass(S.noise(n, rng, "pink"), 140.0, 900.0)
    air /= np.max(np.abs(air)) + 1e-9
    whump = S.thud(rng, 62.0, 40.0, dur, 0.0)
    shape = env([(0.0, 0.0), (0.02, 1.0), (dur * 0.4, 0.5), (dur, 0.0)], dur)
    return S.place(dur + 0.05, (0.0, air * shape * level), (0.0, whump * 0.5 * level))


def frost(rng, dur, level=0.3):
    """Frost crackling in its breath and fur: a fizz of tiny icy grains."""
    return crunch(rng, dur, 420.0, level, 3500.0, 9500.0)


def heavy_hoof(rng, level=1.0):
    """A Rimehorn's foot coming down: a deep thud you feel, and the snow packing under it."""
    pad = S.thud(rng, 48.0, 28.0, 0.42, 0.12) * level
    return S.place(0.45, (0.0, pad), (0.008, crunch(rng, 0.12, 200.0, 0.4 * level, 600.0, 4000.0)))


def rh_ambient(rng, kind):
    if kind == 1:        # three chuffs, then a low rumble
        return S.place(1.7, (0.0, chuff(rng)), (0.36, chuff(rng, 0.24, 0.8)), (0.7, chuff(rng, 0.3, 0.95)),
                       (0.95, rumble(rng, 0.7, 44.0, 0.8)))
    if kind == 2:        # a long rumbling growl, breathed out at the end
        return S.place(1.9, (0.0, rumble(rng, 1.3, 48.0)), (1.25, chuff(rng, 0.5, 0.7)))
    if kind == 3:        # a big wet snort, frost crackling in its nostrils
        return S.place(1.0, (0.0, snort(rng, 0.42, 160.0, 1000.0, 1.0)), (0.0, chuff(rng, 0.3, 0.6)),
                       (0.06, frost(rng, 0.35)))
    return S.place(1.0, (0.0, low_voice(rng, 0.42, 58.0, 50.0, "mm", scale=0.45, rough=0.6, breath=0.3)),   # "hmmph"
                   (0.35, chuff(rng, 0.3, 0.8)))


def rh_hurt(rng, kind):
    roar = low_voice(rng, 0.55, 84.0 if kind == 1 else 76.0, 58.0, "aw", scale=0.48, rough=1.0, breath=0.45, rough_hz=26.0)
    return S.place(0.85, (0.0, roar), (0.45, chuff(rng, 0.3, 0.8)))


def rh_death(rng):
    roar = low_voice(rng, 1.5, 78.0, 40.0, "aw", "oo", scale=0.48, rough=1.0, breath=0.5, rough_hz=24.0)
    sigh = chuff(rng, 0.9, 0.7)
    return S.reverb(S.place(2.9, (0.0, roar), (1.4, sigh), (1.45, frost(rng, 0.6, 0.2)),
                            (2.1, S.thud(rng, 42.0, 26.0, 0.7, 0.3))), 0.1)


def rh_step(rng, kind):
    return heavy_hoof(rng, 1.0 if kind != 2 else 0.85)


def rh_eat(rng, kind):
    crunching = crunch(rng, 0.8, 160.0, 0.6, 500.0, 3500.0)
    return S.place(1.4, (0.0, crunching), (0.0, rumble(rng, 0.8, 50.0, 0.25, 0.4)), (0.95, chuff(rng, 0.3, 0.6)))


def rh_flick(rng, kind):
    grunt = low_voice(rng, 0.3, 60.0, 92.0 if kind == 1 else 84.0, "uh", scale=0.5, rough=1.0, breath=0.4)
    air = S.whoosh(rng, 0.35, 200.0, 1100.0, color="pink", width=0.7)
    return S.place(0.75, (0.0, chuff(rng, 0.2, 0.8)), (0.1, grunt), (0.18, air * 0.7))


def rh_rush(rng, kind):
    roar = low_voice(rng, 0.6, 68.0 if kind == 1 else 74.0, 90.0, "aw", scale=0.48, rough=1.0, breath=0.45, rough_hz=26.0)
    beats = S.place(1.0, *[(i * 0.14 + rng.uniform(-0.01, 0.01), heavy_hoof(rng, 0.6 + 0.4 * i / 6)) for i in range(7)])
    return S.place(1.6, (0.0, chuff(rng, 0.25)), (0.12, roar), (0.25, beats))


# ---------------------------------------------------------------- the moose
# A voice: tonal, nasal, wavering calls (a cow's long wail, a bull's croaky "oh-wah", bleating brays), lighter woody
# hoof clops and the hollow clack of its antlers. Nothing of the Rimehorn's chest rumble.


def nasal(rng, dur, f0_pts, vowel_pts, vib=5.5, vib_depth=0.03, rough=0.12, breath=0.18, scale=0.95):
    """A moose's nasal call: a pitched voice through a long nose, wavering."""
    return S.voice(dur, f0_pts, vowel_pts, rng, scale=scale, breath=breath, rough=rough, vib_rate=vib,
                   vib_pts=[(0.0, vib_depth * 0.3), (dur, vib_depth)],
                   amp_pts=[(0.0, 0.0), (min(0.12, dur * 0.2), 1.0), (dur * 0.75, 0.85), (dur, 0.0)])


def croak(rng, dur, f0a, f0b):
    """A bull moose's croak: a low voice broken into slow glottal pulses."""
    return S.voice(dur, [(0.0, f0a), (dur, f0b)], [(0.0, "oo"), (dur, "ah")], rng, scale=0.85, breath=0.1, rough=1.0,
                   rough_hz=16.0, amp_pts=[(0.0, 0.0), (0.03, 1.0), (dur * 0.6, 0.9), (dur, 0.0)])


def clop(rng, level=1.0):
    """A moose's hoof: a hollow, woody clop on the snow, light for its size."""
    knock = S.knock(rng, freqs=(320, 860, 1900), qs=(8, 11, 13), gains=(0.9, 0.45, 0.2), dur=0.18, decay=0.035)
    return S.place(0.25, (0.0, knock * level), (0.004, crunch(rng, 0.06, 300.0, 0.12 * level, 700.0, 3500.0)))


def mo_ambient(rng, kind):
    if kind == 1:        # a cow's long nasal wail: nnn-aaah-uh
        call = nasal(rng, 1.6, [(0.0, 220.0), (0.3, 262.0), (1.2, 240.0), (1.6, 168.0)],
                     [(0.0, "mm"), (0.25, "ah"), (1.1, "aw"), (1.6, "uh")])
        return S.reverb(call, 0.22)
    if kind == 2:        # a bull's croaky grunts: oh-wah, oh-wah
        return S.reverb(S.place(1.1, (0.0, croak(rng, 0.3, 120.0, 98.0)), (0.45, croak(rng, 0.34, 116.0, 92.0))), 0.12)
    if kind == 3:        # a short nasal honk: mm-wa
        return S.reverb(nasal(rng, 0.55, [(0.0, 180.0), (0.2, 215.0), (0.55, 172.0)], [(0.0, "mm"), (0.2, "ah"), (0.55, "uh")],
                              vib=7.0, vib_depth=0.02), 0.15)
    return S.place(0.7, (0.0, snort(rng, 0.2, 700.0, 3600.0, 0.55)), (0.32, tick(rng, 1800.0, 0.5)),   # a snuffle and a lip smack
                   (0.42, tick(rng, 2200.0, 0.35)))


def mo_hurt(rng, kind):
    bleat = nasal(rng, 0.5, [(0.0, 310.0 if kind == 1 else 285.0), (0.5, 200.0)], [(0.0, "eh"), (0.5, "ah")],
                  vib=9.0, vib_depth=0.05, rough=0.45, breath=0.25)
    return S.place(0.65, (0.0, bleat))


def mo_death(rng):
    moan = nasal(rng, 2.0, [(0.0, 270.0), (0.7, 225.0), (2.0, 110.0)], [(0.0, "ah"), (1.0, "aw"), (2.0, "oo")],
                 vib=5.0, vib_depth=0.05, rough=0.3, breath=0.35)
    return S.reverb(S.place(2.5, (0.0, moan), (2.0, S.thud(rng, 75.0, 45.0, 0.35, 0.3) * 0.7)), 0.18)


def mo_step(rng, kind):
    return clop(rng, 1.0 if kind == 1 else 0.85)


def mo_eat(rng, kind):
    parts = [(i * 0.26, tick(rng, 1500.0 + 300.0 * (i % 2), 0.45)) for i in range(4 if kind == 1 else 5)]   # lip smacks
    return S.place(1.3, (0.0, crunch(rng, 0.9, 90.0, 0.4, 600.0, 3000.0)), *parts)


def mo_charge(rng, kind):
    bellow = nasal(rng, 0.65, [(0.0, 150.0), (0.65, 235.0 if kind == 1 else 215.0)], [(0.0, "uh"), (0.65, "ah")],
                   vib=0.0, rough=0.7, breath=0.3, scale=0.9)
    beats = S.place(1.0, *[(i * 0.17 + rng.uniform(-0.01, 0.01), clop(rng, 0.55 + 0.45 * i / 5)) for i in range(6)])
    return S.place(1.5, (0.0, snort(rng, 0.22, 700.0, 3600.0, 0.6)), (0.15, bellow), (0.35, beats))


def mo_attack(rng, kind):
    grunt = croak(rng, 0.24, 130.0, 150.0)
    antler = S.knock(rng, freqs=(620, 1550, 3300), qs=(12, 16, 18), gains=(1.0, 0.6, 0.35), dur=0.3, decay=0.05)
    return S.place(0.8, (0.0, grunt), (0.2, antler), (0.21, S.thud(rng, 90.0, 55.0, 0.25, 0.3) * 0.6))


# ---------------------------------------------------------------- the Pond Skater


def ps_ambient(rng, kind):
    if kind == 1:
        return clicks(rng, 0.7, 28.0)                                        # a chitter of clicks
    if kind == 2:
        return S.place(0.9, (0.0, rasp(rng, 0.55)), (0.62, clicks(rng, 0.2, 40.0, level=0.4)))   # a dry rasp
    if kind == 3:
        return S.place(0.7, (0.0, buzz(rng, 0.18, 210.0, 190.0, 0.5)), (0.3, buzz(rng, 0.14, 230.0, 200.0, 0.4)))
    return S.place(0.8, (0.0, tick(rng, 2800.0, 0.8)), (0.12, tick(rng, 3200.0, 0.7)), (0.4, clicks(rng, 0.3, 45.0, level=0.5)))


def ps_hurt(rng, kind):
    squeal = S.chirp(0.22, [(0.0, 2600.0 if kind == 1 else 2300.0), (0.22, 1700.0)], rng,
                     harmonics=((1, 1.0), (2, 0.4), (3, 0.2)), vib=(40.0, 0.03))
    return S.place(0.45, (0.0, squeal * 0.7), (0.05, clicks(rng, 0.3, 55.0, level=0.5)))


def ps_death(rng):
    return S.place(1.3, (0.0, buzz(rng, 0.9, 260.0, 90.0, 0.6)), (0.0, rasp(rng, 0.9, 26.0, level=0.5)),
                   (0.85, S.crack(rng, 0.12) * 0.7), (0.9, clicks(rng, 0.35, 20.0, level=0.4)))


def ps_step(rng, kind):
    return S.place(0.12, (0.0, tick(rng, 2400.0, 0.45)), (0.04 + 0.02 * kind, tick(rng, 3000.0, 0.3)))


def ps_skate(rng, kind):
    return S.reverb(swish(rng, 0.55 if kind == 1 else 0.7), 0.1)


def ps_attack(rng, kind):
    hiss = S.bandpass(S.noise(S.n_of(0.25), rng), 3000.0, 9000.0) * env([(0.0, 0.0), (0.05, 1.0), (0.25, 0.0)], 0.25)
    stab = S.place(0.15, (0.0, S.crack(rng, 0.06)), (0.0, tick(rng, 1500.0, 0.9)))
    return S.place(0.6, (0.0, hiss * 0.5), (0.0, clicks(rng, 0.2, 60.0, level=0.4)), (0.22 + 0.04 * kind, stab))


# ---------------------------------------------------------------- the list


class Event:
    """One sound event: its id, the files it picks from at random, its subtitle, and where it shows in the showcase."""

    def __init__(self, event, folder, files, subtitle, group, label, volume=1.0):
        self.event, self.folder, self.files = event, folder, files
        self.subtitle, self.group, self.label, self.volume = subtitle, group, label, volume

    @property
    def subtitle_key(self):
        return "subtitles.dross." + self.event.split(".", 1)[1]


def versions(prefix, fn, count):
    return [(f"{prefix}_{i}", (lambda r, f=fn, k=i: f(r, k))) for i in range(1, count + 1)]


RH, MO, PS = "rimehorn", "moose", "pondskater"
EVENTS = [
    Event("entity.rimehorn.ambient", RH, versions("ambient", rh_ambient, 4), "Rimehorn snorts", "Rimehorn",
          "Chuffs and a rumble, a long growl, a frosty snort, a deep \"hmmph\" (4 versions)"),
    Event("entity.rimehorn.hurt", RH, versions("hurt", rh_hurt, 2), "Rimehorn bellows", "Rimehorn",
          "Hurt: a gruff roar and a chuff (2 versions)"),
    Event("entity.rimehorn.death", RH, [("death", rh_death)], "Rimehorn dies", "Rimehorn",
          "Death: a sinking roar, a last frosty breath, a heavy fall"),
    Event("entity.rimehorn.step", RH, versions("step", rh_step, 2), "Heavy footsteps", "Rimehorn",
          "Deep, heavy footfalls on packed snow", volume=0.6),
    Event("entity.rimehorn.eat", RH, versions("eat", rh_eat, 2), "Rimehorn grazes", "Rimehorn",
          "Crunching snow and a low rumble"),
    Event("entity.rimehorn.flick", RH, versions("flick", rh_flick, 2), "Rimehorn tosses its horn", "Rimehorn",
          "The horn flick: a chuff, a deep heave and the air (2 versions)"),
    Event("entity.rimehorn.rush", RH, versions("rush", rh_rush, 2), "Rimehorn charges", "Rimehorn",
          "The rush: a roar and thundering heavy feet (2 versions)"),
    Event("entity.moose.ambient", MO, versions("ambient", mo_ambient, 4), "Moose calls", "Moose",
          "A cow's long nasal wail, a bull's croaks, a nasal honk, a snuffle (4 versions)"),
    Event("entity.moose.hurt", MO, versions("hurt", mo_hurt, 2), "Moose brays", "Moose",
          "Hurt: a wavering bleat (2 versions)"),
    Event("entity.moose.death", MO, [("death", mo_death)], "Moose dies", "Moose", "Death: a long wavering moan"),
    Event("entity.moose.step", MO, versions("step", mo_step, 2), "Hooves clop", "Moose",
          "Light, woody hoof clops on snow", volume=0.5),
    Event("entity.moose.eat", MO, versions("eat", mo_eat, 2), "Moose grazes", "Moose", "Nibbling and lip smacks"),
    Event("entity.moose.charge", MO, versions("charge", mo_charge, 2), "Moose charges", "Moose",
          "The charge: a rising nasal bellow and clopping hooves (2 versions)"),
    Event("entity.moose.attack", MO, versions("attack", mo_attack, 2), "Moose rams", "Moose",
          "The ram: a croak and the hollow clack of its antlers (2 versions)"),
    Event("entity.pondskater.ambient", PS, versions("ambient", ps_ambient, 4), "Pond Skater chitters", "Pond Skater",
          "Chittering clicks, a dry rasp, a buzz, taps (4 versions)", volume=0.8),
    Event("entity.pondskater.hurt", PS, versions("hurt", ps_hurt, 2), "Pond Skater squeals", "Pond Skater",
          "Hurt (2 versions)"),
    Event("entity.pondskater.death", PS, [("death", ps_death)], "Pond Skater dies", "Pond Skater",
          "Death: a dying buzz and a crack"),
    Event("entity.pondskater.step", PS, versions("step", ps_step, 2), "Legs tap", "Pond Skater",
          "Its legs tapping the ground", volume=0.35),
    Event("entity.pondskater.skate", PS, versions("skate", ps_skate, 2), "Pond Skater glides", "Pond Skater",
          "Gliding over the ice (2 versions)", volume=0.6),
    Event("entity.pondskater.attack", PS, versions("attack", ps_attack, 2), "Pond Skater stabs", "Pond Skater",
          "The stab: a hiss and a sharp strike (2 versions)"),
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
    ours = ("entity.rimehorn.", "entity.moose.", "entity.pondskater.")
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
    json.loads(out)
    old = {k for k in data if not k.startswith(ours)}
    assert old <= set(json.loads(out)), "lost an entry"
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


def write_java():
    rows = [f'            new Sound({json.dumps(ev.group)}, {json.dumps(ev.event)}, {json.dumps(ev.label)}, {len(ev.files)})'
            for ev in EVENTS]
    src = f'''package com.reg21meme.dross.boss.rimefall;

import java.util.List;

/**
 * Every Rimefall animal sound (the Rimehorn's, the moose's and the Pond Skater's), written by
 * {{@code tools/rimefall/sounds.py}} (don't edit by hand: change the sounds there and run it again). All of them are
 * original, synthesized by that tool. {{@code /dross showcase mob_sounds rimefall}} lists them in chat with a button that
 * plays each one.
 */
public final class RimefallMobSounds
{{
    /**
     * One sound event.
     *
     * @param group    the animal it belongs to (the heading it's listed under)
     * @param event    its id ({{@code dross:<event>}})
     * @param label    what it is
     * @param versions how many versions it picks from at random
     */
    public record Sound(String group, String event, String label, int versions)
    {{
    }}

    public static final List<Sound> ALL = List.of(
{",\n".join(rows)});

    private RimefallMobSounds() {{}}
}}
'''
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)


def write_page():
    os.makedirs(PREVIEWS, exist_ok=True)
    groups = {}
    for ev in EVENTS:
        groups.setdefault(ev.group, []).append(ev)
    html = ['<!doctype html><meta charset="utf-8"><title>Rimefall animal sounds</title>',
            "<style>body{font-family:system-ui,sans-serif;background:#12161c;color:#ecf2f8;margin:24px;max-width:1100px}"
            "h1{font-size:22px}h2{font-size:17px;margin-top:28px;color:#96d6ec}"
            ".row{display:flex;flex-wrap:wrap;align-items:center;gap:10px;margin:6px 0;padding:6px 8px;background:#1e252e;border-radius:6px}"
            ".label{flex:1 1 280px}.id{color:#8d98a6;font-size:12px}audio{height:32px}</style>",
            "<h1>The Rimefall animals' sounds</h1>",
            "<p>All original: made from scratch by <code>tools/rimefall/sounds.py</code>. Each player is one version; "
            "in the game a sound with several versions plays one at random. "
            "<code>/dross showcase mob_sounds rimefall</code> lists them in the game.</p>"]
    for group, evs in groups.items():
        html.append(f"<h2>{group}</h2>")
        for ev in evs:
            players = "".join(f'<audio controls preload="none" src="../../../../src/main/resources/assets/dross/sounds/{ev.folder}/{name}.ogg"></audio>'
                              for name, _m in ev.files)
            html.append(f'<div class="row"><div class="label">{ev.label}<div class="id">dross:{ev.event}</div></div>{players}</div>')
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(html) + "\n")


def main():
    count = 0
    for ev in EVENTS:
        os.makedirs(os.path.join(SOUND_DIR, ev.folder), exist_ok=True)
        for name, maker in ev.files:
            x = render(ev, name, maker)
            count += 1
            print(f"  {ev.folder}/{name}.ogg  {len(x) / S.SR:.2f} s")
    for folder in (RH, MO, PS):
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
