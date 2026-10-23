"""
Makes the Huntsman's and the Alpha Moose's sounds (CLAUDE.md step 14, "Sounds"): his war cry, voice, spear, bow and
bola; the Alpha Moose's bellows, its call as it rears, its hooves, kick, ram, stomp, the cracking stamp and its ice
spikes. Every one is synthesized from scratch with the Rat King's synthesizer (tools/ratking/ratkit/synth.py): nothing
is recorded and nothing comes from Minecraft. Each event was made in two or three versions for the user to pick from
(the page below plays each version on its own); an event's `picks` are the versions the game plays (the user's picks
of October 23, 2026), the rest stay in the showcase. His horn isn't here: the user kept the goat horn
(boss/huntsman/HuntSoundCues).

Run from the project folder:

    python tools/huntsman/sounds.py

It writes:
  src/main/resources/assets/dross/sounds/huntsman/*.ogg, alpha_moose/*.ogg   the sounds (mono Ogg Vorbis)
  src/main/resources/assets/dross/sounds.json        adds (or replaces) its own events; every other entry stays as it was
  src/main/resources/assets/dross/lang/en_us.json    adds the subtitles it needs (never changes other keys)
  src/main/java/com/reg21meme/dross/boss/huntsman/HuntsmanSounds.java   every event, for /dross showcase boss_sounds huntsman
  boss-previews/rimefall/huntsman/sounds/index.html  a page that plays every version (open it in a browser)

Needs numpy, scipy and soundfile (`pip install soundfile`).
"""
import json
import math
import os
import sys
import zlib

sys.dont_write_bytecode = True
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools", "ratking"))

import numpy as np  # noqa: E402
import soundfile  # noqa: E402

from ratkit import synth as S  # noqa: E402

import voicekit as V  # noqa: E402  (tools/huntsman/voicekit.py: a living voice and a howl)

ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "dross")
SOUND_DIR = os.path.join(ASSETS, "sounds")
SOUNDS_JSON = os.path.join(ASSETS, "sounds.json")
LANG = os.path.join(ASSETS, "lang", "en_us.json")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "reg21meme", "dross", "boss", "huntsman", "HuntsmanSounds.java")
PREVIEWS = os.path.join(ROOT, "boss-previews", "rimefall", "huntsman", "sounds")


# ---------------------------------------------------------------- pieces


def env(points, dur):
    return S.smooth_env(points, S.n_of(dur))


def norm(x):
    """Scaled so its loudest point is 1 (the synth's knocks come out far quieter than its thuds)."""
    return x / (np.max(np.abs(x)) + 1e-9)


def echo(x, delay=0.38, gain=0.3, repeats=2, damp=2600.0):
    """The call coming back off the hills: a few softer, duller copies, further and further behind."""
    out = np.concatenate([x, np.zeros(S.n_of(delay * repeats + 0.1))])
    copy = x
    for k in range(1, repeats + 1):
        copy = S.lowpass(copy, damp)
        i = S.n_of(delay * k)
        out[i:i + len(copy)] += copy * gain ** k
    return out


def valley(x, mix=0.3):
    """Out on the open snow: a wide, cold reverb and an echo."""
    return S.reverb(echo(x), mix, room=0.84, damp=3800.0)


def crunch(rng, dur, rate=120.0, level=0.8, lo=1500.0, hi=7000.0):
    """Snow crunching: a burst of tiny grains."""
    return S.grains(rng, dur, rate, grain=(0.002, 0.008), lo=lo, hi=hi, level=level)


def frost(rng, dur, level=0.3):
    """Frost crackling: a fizz of tiny icy grains."""
    return crunch(rng, dur, 420.0, level, 3500.0, 9500.0)


def ring(rng, dur, freqs, decay, level=1.0):
    """Glassy partials dying away (ice): sines out of tune with each other, each with its own decay."""
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    out = np.zeros(n)
    for k, f in enumerate(freqs):
        out += np.sin(2 * math.pi * f * t + rng.random() * 6.0) * np.exp(-t / (decay * (1.0 - 0.12 * k))) / (1 + 0.5 * k)
    attack = np.clip(t / 0.002, 0.0, 1.0)
    return out * attack * level


def ice_crack(rng, level=1.0, size=1.0):
    """Ice splitting: a sharp snap, a glassy ring and a crackle running off."""
    snap = S.crack(rng, 0.05 + 0.03 * size)
    body = S.thud(rng, 120.0 / size, 70.0 / size, 0.25, 0.4)
    base = 1900.0 / size ** 0.5
    glass = ring(rng, 0.6, (base, base * 1.53, base * 2.31, base * 3.07), 0.12 * size, 0.35)
    return S.place(0.7, (0.0, snap), (0.0, body * 0.35), (0.004, glass), (0.02, frost(rng, 0.35, 0.35))) * level


# ---------------------------------------------------------------- the Huntsman's voice
# A big man (he stands 4 blocks to the top of his hood) speaking through a wolf's hood: a low voice (f0 about 85-110 Hz),
# a mouth a little bigger than a person's (formants scaled 0.86), breathy and rough; never words, only cries and grunts.


def man(rng, dur, f0_pts, vowel_pts, rough=0.35, breath=0.3, scale=0.86, rough_hz=30.0, amp_pts=None, vib=None):
    amp = amp_pts or [(0.0, 0.0), (min(0.05, dur * 0.2), 1.0), (dur * 0.7, 0.85), (dur, 0.0)]
    kw = {}
    if vib:
        kw = dict(vib_rate=vib[0], vib_pts=[(0.0, 0.0), (dur, vib[1])])
    return S.voice(dur, f0_pts, vowel_pts, rng, scale=scale, breath=breath, rough=rough, rough_hz=rough_hz,
                   amp_pts=amp, **kw)


def grunt(rng, dur, f0a, f0b, vowel="uh", rough=0.5, breath=0.45):
    return man(rng, dur, [(0.0, f0a), (dur, f0b)], [(0.0, vowel), (dur, "uh")], rough=rough, breath=breath,
               amp_pts=[(0.0, 0.0), (0.012, 1.0), (dur * 0.45, 0.7), (dur, 0.0)])


def breath_out(rng, dur=0.5, level=0.5, lo=500.0, hi=3000.0):
    """A breath let out through the hood: soft, low, with frost on it."""
    n = S.n_of(dur)
    air = S.bandpass(S.noise(n, rng, "pink"), lo, hi) * env([(0.0, 0.0), (dur * 0.25, 1.0), (dur, 0.0)], dur)
    return S.place(dur, (0.0, air * level), (dur * 0.3, frost(rng, dur * 0.5, 0.06)))


def sniff(rng, dur=0.09, level=0.6):
    n = S.n_of(dur)
    return S.bandpass(S.noise(n, rng), 1100.0, 6000.0) * env([(0.0, 0.0), (dur * 0.7, 1.0), (dur, 0.0)], dur) * level


def hu_ambient(rng, kind):
    if kind == 1:        # a slow breath through the hood, frost on it, and a low "hm"
        hm = man(rng, 0.4, [(0.0, 96.0), (0.4, 88.0)], [(0.0, "mm")], rough=0.15, breath=0.2)
        return S.place(1.5, (0.0, breath_out(rng, 0.7, 0.5)), (0.85, hm * 0.7))
    if kind == 2:        # tracking: sniff, sniff, sniff... like the wolf he wears, then a pleased growl
        parts = [(i * 0.17, sniff(rng, 0.08 + 0.02 * (i % 2))) for i in range(4)]
        growl = man(rng, 0.55, [(0.0, 82.0), (0.55, 76.0)], [(0.0, "uh"), (0.55, "oo")], rough=0.9, breath=0.35, rough_hz=24.0)
        return S.place(1.5, *parts, (0.82, growl * 0.6))
    # a low hunting hum, two notes
    a = man(rng, 0.5, [(0.0, 98.0), (0.5, 100.0)], [(0.0, "mm")], rough=0.1, breath=0.15, vib=(5.0, 0.015))
    b = man(rng, 0.7, [(0.0, 110.0), (0.7, 92.0)], [(0.0, "mm"), (0.7, "oo")], rough=0.1, breath=0.15, vib=(5.0, 0.02))
    return S.reverb(S.place(1.5, (0.0, a * 0.7), (0.55, b * 0.7)), 0.15)


def hu_man_howl(rng):
    """A man's wolf howl: low, sliding up, held, falling off (the user's first pick for his war cry)."""
    howl = man(rng, 1.8, [(0.0, 140.0), (0.35, 290.0), (1.2, 275.0), (1.8, 170.0)],
               [(0.0, "oo"), (0.4, "ah"), (1.3, "aw"), (1.8, "oo")], rough=0.25, breath=0.3, vib=(5.5, 0.03),
               amp_pts=[(0.0, 0.0), (0.2, 0.8), (0.5, 1.0), (1.3, 0.85), (1.8, 0.0)])
    return valley(howl, 0.25)


def hu_cry(rng, kind):
    """His war cry when he spots you (then his horn): a wolf's howl (the user, October 23, 2026: "for the war cry I was
    wishing more wolfiness"). Each is about 2.3 s, its howl over before his horn sounds (HuntsmanTuning.WAR_CRY_TICKS
    plus the horn's lead-in)."""
    if kind == 1:        # long and pure: up, held with a slow vibrato, the mouth opening and closing
        d = 2.3
        h = V.howl(d, [(0.0, 300.0), (0.35, 430.0), (1.5, 445.0), (1.95, 380.0), (d, 300.0)],
                   [(0.0, "oo"), (0.35, "oh"), (0.7, "ah"), (1.5, "ah"), (1.9, "oh"), (d, "oo")], rng,
                   amp=[(0.0, 0.0), (0.25, 0.75), (0.5, 1.0), (1.7, 0.9), (d, 0.0)], vib=(4.5, 0.008))
        return valley(h, 0.3)
    if kind == 2:        # low, then breaking up an octave, then down again with a quaver
        d = 2.3
        h = V.howl(d, [(0.0, 250.0), (0.38, 270.0), (0.43, 520.0), (1.35, 500.0), (1.43, 330.0), (d, 290.0)],
                   [(0.0, "oo"), (0.38, "oo"), (0.46, "ah"), (1.35, "oh"), (1.45, "oo"), (d, "oo")], rng,
                   amp=[(0.0, 0.0), (0.15, 0.6), (0.38, 0.7), (0.46, 1.0), (1.3, 0.9), (1.45, 0.75), (d, 0.0)],
                   vib=(5.5, [(0.0, 0.004), (1.4, 0.008), (d, 0.03)]))
        return valley(h, 0.3)
    if kind == 3:        # his howl, and the pack answering from far off
        main = V.howl(2.1, [(0.0, 290.0), (0.3, 410.0), (1.4, 425.0), (2.1, 320.0)],
                      [(0.0, "oo"), (0.35, "ah"), (1.5, "oh"), (2.1, "oo")], rng,
                      amp=[(0.0, 0.0), (0.25, 0.8), (0.5, 1.0), (1.6, 0.85), (2.1, 0.0)])
        one = V.howl(1.9, [(0.0, 380.0), (0.3, 515.0), (1.3, 505.0), (1.9, 420.0)],
                     [(0.0, "oo"), (0.35, "ah"), (1.4, "oh"), (1.9, "oo")], rng,
                     amp=[(0.0, 0.0), (0.3, 0.8), (1.2, 0.9), (1.9, 0.0)], vib=(5.2, 0.01))
        two = V.howl(1.7, [(0.0, 460.0), (0.3, 640.0), (1.2, 620.0), (1.7, 520.0)],
                     [(0.0, "oo"), (0.3, "oh"), (1.2, "ah"), (1.7, "oo")], rng,
                     amp=[(0.0, 0.0), (0.3, 0.8), (1.1, 0.9), (1.7, 0.0)], vib=(5.8, 0.012))
        far = lambda x: S.lowpass(x, 2400.0)  # noqa: E731
        return valley(S.place(3.0, (0.0, main), (0.55, far(one) * 0.4), (0.85, far(two) * 0.3)), 0.35)
    return hu_man_howl(rng)


# The user (October 23, 2026): his voice "just sounds like a moose", then, of the first human round, "I was hoping more
# human-ish": his grunts, hurt and death are made with voicekit (the folds' own puffs, wobbling like a real voice's,
# through a mouth that glides between a man's vowels), with a glottal stop, rasp, creak and breath where a person's
# have them.


def teeth_hiss(rng, dur=0.22, level=1.0):
    """A sharp breath in through clenched teeth."""
    n = S.n_of(dur)
    x = S.bandpass(S.noise(n, rng), 2800.0, 8000.0)
    return x * env([(0.0, 0.0), (dur * 0.8, 1.0), (dur, 0.0)], dur) * level


def hu_effort(rng, kind):
    """The grunt of a blow (the spear's thrusts, the leap): "hup!", "hah!", "hyah!"."""
    if kind == 1:        # "hup!": an h, a tight rising "uh", stopped dead
        v = V.voice(0.13, [(0.0, 145.0), (0.13, 165.0)], "uh", rng, oq=0.45, rasp=0.05,
                    amp=[(0.0, 0.0), (0.01, 1.0), (0.1, 0.85), (0.115, 0.0), (0.13, 0.0)])
        return V.place(0.3, (0.0, V.aspirate(0.05, "uh", rng) * 0.35), (0.04, v))
    if kind == 2:        # "hah!": a glottal stop, a falling "ah", the breath out after it
        v = V.voice(0.2, [(0.0, 172.0), (0.2, 132.0)], [(0.0, "ah"), (0.2, "uh")], rng, oq=0.48,
                    rasp=[(0.0, 0.2), (0.06, 0.0)], amp=[(0.0, 0.0), (0.006, 1.0), (0.14, 0.8), (0.2, 0.0)])
        out = V.aspirate(0.12, "uh", rng, amp=[(0.0, 0.0), (0.02, 1.0), (0.12, 0.0)])
        return V.place(0.4, (0.0, V.aspirate(0.03, "ah", rng) * 0.3), (0.025, v), (0.2, out * 0.3))
    # "hyah!": a battle yell, ee sliding open to ah
    v = V.voice(0.34, [(0.0, 165.0), (0.08, 210.0), (0.34, 150.0)], [(0.0, "ee"), (0.08, "ae"), (0.16, "ah"), (0.34, "ah")],
                rng, oq=0.42, rasp=0.12, jitter=0.015, amp=[(0.0, 0.0), (0.02, 0.8), (0.08, 1.0), (0.26, 0.8), (0.34, 0.0)])
    return V.place(0.45, (0.0, V.aspirate(0.04, "ee", rng) * 0.3), (0.035, v))


def hu_hurt(rng, kind):
    if kind == 1:        # "ugh!": a creak, a tight "uh" stopped dead, a breath out of his nose
        cr = V.creak(0.05, "uh", rng, amp=[(0.0, 0.0), (0.01, 0.6), (0.05, 0.6)], f0=(60.0, 90.0))
        v = V.voice(0.19, [(0.0, 165.0), (0.19, 118.0)], "uh", rng, oq=0.4, rasp=0.08,
                    amp=[(0.0, 0.6), (0.02, 1.0), (0.15, 0.85), (0.165, 0.0), (0.19, 0.0)])
        nose = V.aspirate(0.18, "mm", rng, amp=[(0.0, 0.0), (0.04, 1.0), (0.18, 0.0)])
        return V.place(0.55, (0.0, cr * 0.5), (0.045, v), (0.3, nose * 0.25))
    if kind == 2:        # "agh!", then a hiss of breath in through his teeth
        v = V.voice(0.3, [(0.0, 225.0), (0.05, 252.0), (0.3, 168.0)], [(0.0, "ae"), (0.1, "ah"), (0.3, "ah")], rng,
                    oq=0.38, rasp=0.22, jitter=0.018, amp=[(0.0, 0.0), (0.01, 1.0), (0.2, 0.8), (0.3, 0.0)])
        return V.place(0.75, (0.0, v), (0.42, teeth_hiss(rng) * 0.35))
    # "oof": the wind knocked out of him
    v = V.voice(0.17, [(0.0, 138.0), (0.17, 108.0)], [(0.0, "oo"), (0.17, "uh")], rng, oq=0.72, breath=0.35,
                puff_breath=0.6, amp=[(0.0, 0.0), (0.01, 1.0), (0.12, 0.6), (0.17, 0.0)])
    return V.place(0.4, (0.0, V.aspirate(0.05, "oo", rng) * 0.6), (0.03, v), (0.2, V.aspirate(0.15, "uh", rng) * 0.25))


def hu_death(rng, kind):
    if kind == 1:        # "aaaagh...": a cry falling away, breaking into a croak, his last breath
        d = 1.45
        v = V.voice(d, [(0.0, 205.0), (0.12, 228.0), (0.6, 180.0), (1.15, 110.0), (d, 85.0)],
                    [(0.0, "ae"), (0.15, "ah"), (0.9, "aw"), (d, "uh")], rng,
                    oq=[(0.0, 0.42), (d, 0.7)], rasp=[(0.0, 0.18), (0.5, 0.05), (d, 0.1)], breath=[(0.0, 0.05), (d, 0.4)],
                    vib=(5.0, [(0.0, 0.0), (0.5, 0.01), (d, 0.04)]),
                    amp=[(0.0, 0.0), (0.02, 1.0), (0.7, 0.85), (1.2, 0.5), (d, 0.0)])
        fry = V.creak(0.35, "uh", rng, amp=[(0.0, 0.5), (0.35, 0.0)])
        out = V.aspirate(0.9, [(0.0, "uh"), (0.9, "oo")], rng, amp=[(0.0, 0.0), (0.15, 1.0), (0.9, 0.0)])
        return S.reverb(V.place(2.8, (0.0, v), (d - 0.05, fry * 0.6), (1.6, out * 0.3)), 0.12)
    if kind == 2:        # "nngh... aaah": straining against it, then letting go
        strain = V.voice(0.45, [(0.0, 128.0), (0.45, 150.0)], "ng", rng, oq=0.34, rasp=0.12, hum=True,
                         amp=[(0.0, 0.0), (0.04, 0.8), (0.4, 1.0), (0.45, 0.0)])
        let = V.voice(1.1, [(0.0, 175.0), (0.4, 150.0), (1.1, 88.0)], [(0.0, "ah"), (0.6, "aw"), (1.1, "uh")], rng,
                      oq=[(0.0, 0.5), (1.1, 0.75)], breath=[(0.0, 0.08), (1.1, 0.45)], vib=(4.5, [(0.0, 0.0), (1.1, 0.03)]),
                      amp=[(0.0, 0.0), (0.03, 1.0), (0.7, 0.7), (1.1, 0.0)])
        fry = V.creak(0.3, "uh", rng, amp=[(0.0, 0.4), (0.3, 0.0)])
        out = V.aspirate(0.8, "uh", rng, amp=[(0.0, 0.0), (0.1, 1.0), (0.8, 0.0)])
        return S.reverb(V.place(2.8, (0.0, strain), (0.5, let), (1.55, fry * 0.5), (1.75, out * 0.3)), 0.12)
    # two gasps, each with a weak "ah", then a long sigh running into a croak
    parts, t = [], 0.0
    for i in range(2):
        parts.append((t, V.gasp(0.28, rng, 0.35)))
        ah = V.voice(0.3, [(0.0, 140.0 - 15 * i), (0.3, 108.0 - 10 * i)], [(0.0, "ah"), (0.3, "uh")], rng, oq=0.65,
                     breath=0.3, amp=[(0.0, 0.0), (0.02, 1.0), (0.3, 0.0)])
        parts.append((t + 0.3, ah * (0.8 - 0.2 * i)))
        t += 0.75
    sigh = V.voice(1.0, [(0.0, 100.0), (1.0, 72.0)], [(0.0, "ah"), (1.0, "uh")], rng, oq=0.75, breath=0.6, puff_breath=0.6,
                   amp=[(0.0, 0.0), (0.1, 0.5), (0.7, 0.3), (1.0, 0.0)])
    air = V.aspirate(1.1, [(0.0, "ah"), (1.1, "uh")], rng, amp=[(0.0, 0.0), (0.1, 1.0), (1.1, 0.0)])
    fry = V.creak(0.3, "uh", rng, amp=[(0.0, 0.35), (0.3, 0.0)])
    parts += [(t, sigh * 0.5), (t, air * 0.35), (t + 0.95, fry * 0.4)]
    return S.reverb(V.place(t + 1.5, *parts), 0.12)


def boot(rng, level=1.0):
    """His boot coming down in the snow: a soft thud and a deep crunch."""
    pad = S.thud(rng, 85.0, 55.0, 0.2, 0.15) * 0.3
    return S.place(0.3, (0.0, pad * level), (0.0, crunch(rng, 0.14, 380.0, 0.8 * level, 700.0, 5000.0)))


def hu_step(rng, kind):
    return boot(rng, 1.0 if kind == 1 else 0.85)


def hu_land(rng, kind):
    """Landing after his leap (off the moose, the leaping strike, the backstep): a grunt and a heavy crunch."""
    thump = S.thud(rng, 70.0, 42.0, 0.35, 0.3)
    return S.place(0.7, (0.0, grunt(rng, 0.14, 120.0, 105.0, "uh") * 0.6), (0.08, thump * 0.5), (0.08, crunch(rng, 0.3, 520.0, 0.8, 500.0, 5000.0)),
                   (0.12, frost(rng, 0.3, 0.2 if kind == 1 else 0.35)))


# ---------------------------------------------------------------- his weapons


def pole_whoosh(rng, dur, lo, hi, level=1.0):
    """A long spear swung through the air: a deep rush of air."""
    return S.whoosh(rng, dur, lo, hi, amp_pts=[(0.0, 0.0), (dur * 0.55, 1.0), (dur, 0.0)], color="pink", width=0.5) * level


def hu_spear_swing(rng, kind):
    if kind == 1:        # a thrust: a short, rising rush
        return S.place(0.45, (0.0, pole_whoosh(rng, 0.3, 300.0, 1600.0)), (0.0, grunt(rng, 0.14, 120.0, 130.0) * 0.5))
    # a wide sweep: a long, deep rush
    return S.place(0.7, (0.0, pole_whoosh(rng, 0.55, 220.0, 900.0)), (0.05, grunt(rng, 0.2, 125.0, 110.0, "ah") * 0.45))


def hu_spear_hit(rng, kind):
    """The moonsilver spike striking: a bright metallic stab, and frost bursting where it hits."""
    clang = S.clink(rng, 1700.0 if kind == 1 else 2200.0, dur=0.5, decay=0.09)
    hit = S.thud(rng, 110.0, 70.0, 0.2, 0.6)
    return S.place(0.8, (0.0, hit * 0.55), (0.0, clang * 0.9), (0.01, frost(rng, 0.45, 0.55)))


def hu_spear_throw(rng, kind):
    """The spear hurled: a heave and a long whistle of air as it flies; then the rope paying out."""
    heave = grunt(rng, 0.3, 105.0, 135.0, "ah", rough=0.8)
    fly = S.whoosh(rng, 0.9, 1800.0, 700.0, amp_pts=[(0.0, 0.0), (0.08, 1.0), (0.9, 0.0)], color="white", width=0.35)
    parts = [(0.0, heave * 0.6), (0.18, fly * 0.8)]
    if kind == 2:        # with the rope hissing out behind it
        parts.append((0.22, S.grains(rng, 0.7, 260.0, grain=(0.003, 0.01), lo=600.0, hi=3000.0, level=0.35)))
    return S.place(1.2, *parts)


def bow_creak(rng, dur, level=1.0):
    """The bow bending as he draws: wood creaking and the string stretching."""
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    parts = [(0.0, S.grains(rng, dur, 70.0, grain=(0.006, 0.02), lo=250.0, hi=1400.0, level=0.6))]
    f = S.curve([(0.0, 240.0), (dur, 330.0)], n)
    groan = np.sin(2 * math.pi * np.cumsum(f) / S.SR) * (0.5 + 0.5 * np.sin(2 * math.pi * 23.0 * t)) ** 3
    groan = S.bandpass(groan, 180.0, 1200.0) * env([(0.0, 0.0), (dur * 0.3, 0.7), (dur, 0.3)], dur)
    parts.append((0.0, groan * 0.5))
    return S.place(dur, *parts) * level


def twang(rng, f0=165.0, level=1.0):
    """The string let go: a deep twang, the bow's wood knocking, and a quick buzz."""
    n = S.n_of(0.5)
    t = np.arange(n) / S.SR
    f = f0 * (1.0 + 0.25 * np.exp(-t / 0.01))
    s = S.tone(f, ((1, 1.0), (2, 0.5), (3, 0.3), (4, 0.15))) * np.exp(-t / 0.09)
    knock = norm(S.knock(rng, freqs=(220, 560, 1300), qs=(7, 9, 11), gains=(0.8, 0.4, 0.2), dur=0.2, decay=0.03))
    return S.place(0.5, (0.0, norm(s) * 0.8), (0.0, knock * 0.5)) * level


def arrow_fly(rng, dur=0.45, level=0.7):
    return S.whoosh(rng, dur, 3200.0, 1500.0, amp_pts=[(0.0, 0.0), (0.03, 1.0), (dur, 0.0)], color="white", width=0.3) * level


def hu_bow_draw(rng, kind):
    """He draws: an arrow pulled from the quiver (a rattle of shafts, a whisper of fletching), then the bow creaks."""
    rattle = S.grains(rng, 0.2, 90.0, grain=(0.004, 0.012), lo=700.0, hi=3500.0, level=0.5)
    nock = norm(S.knock(rng, freqs=(900, 2100), qs=(9, 12), gains=(0.6, 0.3), dur=0.1, decay=0.02))
    return S.place(1.1, (0.0, rattle), (0.22, nock * 0.6), (0.3, bow_creak(rng, 0.75 if kind == 1 else 0.6)))


def hu_bow_shot(rng, kind):
    """The loose: the twang and the arrow away."""
    f0 = (150.0, 175.0, 128.0)[kind - 1]
    return S.place(0.8, (0.0, twang(rng, f0)), (0.02, arrow_fly(rng, 0.5)))


def hu_arrow_rain(rng, kind):
    """The arrow rain coming down round you: arrows whistling down, then thudding into the snow."""
    parts = []
    count = 14 if kind == 1 else 22
    for i in range(count):
        at = 0.15 + rng.uniform(0.0, 1.3)
        f = rng.uniform(2200.0, 4200.0)
        d = rng.uniform(0.25, 0.45)
        parts.append((at, S.whoosh(rng, d, f, f * 0.55, amp_pts=[(0.0, 0.0), (d * 0.8, 1.0), (d, 0.0)], color="white",
                                   width=0.25) * rng.uniform(0.3, 0.6)))
        parts.append((at + d - 0.01, S.place(0.2, (0.0, S.thud(rng, 140.0, 90.0, 0.1, 0.7) * 0.1),
                                             (0.0, crunch(rng, 0.08, 400.0, 0.3, 900.0, 5000.0)))))
    return S.place(2.0, *parts)


def hu_arrow_hit(rng, kind):
    """One of his arrows hitting you: a thwack and the frost spreading (it slows you)."""
    thwack = S.thud(rng, 160.0, 95.0, 0.12, 0.8)
    glass = ring(rng, 0.5, (2600.0, 3980.0, 6020.0), 0.08, 0.25)
    return S.place(0.8, (0.0, thwack * 0.45), (0.01, frost(rng, 0.6 if kind == 1 else 0.4, 0.7)), (0.03, glass * 1.6))


def hu_bola_whirl(rng, kind):
    """The bola whirled over his head: a pulsing whoom-whoom-whoom of the cords and their weights of ice."""
    dur = 1.0
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    rate = 3.2 if kind == 1 else 4.2
    rate_curve = rate * (1.0 + 0.35 * t / dur)
    pulse = (0.5 + 0.5 * np.sin(2 * math.pi * np.cumsum(rate_curve) / S.SR)) ** 2
    air = S.bandpass(S.noise(n, rng, "pink"), 250.0, 1800.0) * pulse
    air *= env([(0.0, 0.0), (0.2, 0.8), (dur * 0.8, 1.0), (dur, 0.6)], dur)
    return S.place(dur, (0.0, air))


def hu_bola_throw(rng, kind):
    """Let go: a whistle away, then the snare: the cords wrapping round, their weights of ice clacking together."""
    fly = S.whoosh(rng, 0.4, 1200.0, 700.0, amp_pts=[(0.0, 0.0), (0.05, 1.0), (0.4, 0.0)], color="pink", width=0.4)
    wrap = S.grains(rng, 0.25, 220.0, grain=(0.004, 0.01), lo=400.0, hi=2500.0, level=0.6)
    clack1 = ring(rng, 0.4, (1500.0, 2280.0, 3460.0), 0.07, 0.5)
    clack2 = ring(rng, 0.4, (1620.0, 2490.0, 3710.0), 0.06, 0.4)
    parts = [(0.0, fly * 0.7), (0.38, wrap), (0.5, clack1), (0.6, clack2)]
    if kind == 2:
        parts.append((0.42, S.thud(rng, 120.0, 80.0, 0.15, 0.5) * 0.5))
    return S.place(1.1, *parts)


# ---------------------------------------------------------------- the Alpha Moose
# The wild moose's voice (tools/rimefall/sounds.py: nasal, wavering calls, woody clops) made old, huge and wild: an
# octave down, rougher, longer, and the hooves heavier; nothing of the Rimehorn's chest rumble.


def am_call(rng, dur, f0_pts, vowel_pts, vib=5.0, vib_depth=0.03, rough=0.35, breath=0.25, scale=0.7, amp_pts=None):
    return S.voice(dur, f0_pts, vowel_pts, rng, scale=scale, breath=breath, rough=rough, rough_hz=20.0, vib_rate=vib,
                   vib_pts=[(0.0, vib_depth * 0.3), (dur, vib_depth)],
                   amp_pts=amp_pts or [(0.0, 0.0), (min(0.15, dur * 0.2), 1.0), (dur * 0.75, 0.85), (dur, 0.0)])


def am_croak(rng, dur, f0a, f0b, vowel="oo"):
    """A huge bull's croak: a low voice broken into slow glottal pulses."""
    return S.voice(dur, [(0.0, f0a), (dur, f0b)], [(0.0, vowel), (dur, "ah")], rng, scale=0.7, breath=0.12, rough=1.0,
                   rough_hz=12.0, amp_pts=[(0.0, 0.0), (0.03, 1.0), (dur * 0.6, 0.9), (dur, 0.0)])


def big_snort(rng, dur=0.35, level=1.0):
    n = S.n_of(dur)
    t = np.arange(n) / S.SR
    x = S.bandpass(S.noise(n, rng, "pink"), 220.0, 1800.0) * (1.0 + 0.5 * np.sin(2 * math.pi * 26.0 * t))
    return x * env([(0.0, 0.0), (0.015, 1.0), (dur * 0.35, 0.6), (dur, 0.0)], dur) * level


def big_hoof(rng, level=1.0):
    """The Alpha Moose's hoof: a deep woody clop and the snow packing under it."""
    knock = norm(S.knock(rng, freqs=(240, 640, 1500), qs=(7, 10, 12), gains=(0.8, 0.7, 0.4), dur=0.25, decay=0.05))
    pad = norm(S.thud(rng, 70.0, 45.0, 0.3, 0.2))
    return S.place(0.35, (0.0, knock * level), (0.0, pad * 0.35 * level), (0.005, crunch(rng, 0.1, 280.0, 0.5 * level, 600.0, 3500.0)))


def antler_clack(rng, level=1.0, low=1.0):
    return norm(S.knock(rng, freqs=(420 * low, 1050 * low, 2300 * low), qs=(11, 14, 16), gains=(1.0, 0.6, 0.35), dur=0.4,
                        decay=0.07)) * level


def am_ambient(rng, kind):
    if kind == 1:        # deep croaky grunts: oh-wah... oh-wah
        return S.reverb(S.place(1.4, (0.0, am_croak(rng, 0.38, 78.0, 62.0)), (0.6, am_croak(rng, 0.42, 74.0, 58.0))), 0.15)
    if kind == 2:        # a big wet snort and the frost in its breath
        return S.place(1.0, (0.0, big_snort(rng, 0.45)), (0.08, frost(rng, 0.4, 0.2)))
    # a low nasal moan, wavering
    moan = am_call(rng, 1.3, [(0.0, 120.0), (0.3, 138.0), (1.3, 96.0)], [(0.0, "mm"), (0.3, "aw"), (1.3, "uh")])
    return S.reverb(moan, 0.2)


def am_bellow(rng, kind):
    """The bellow (its roar in a cone; also its first call when he spots you)."""
    if kind == 1:        # a huge, long nasal bellow: a moose's call an octave down
        call = am_call(rng, 1.9, [(0.0, 105.0), (0.3, 135.0), (1.3, 128.0), (1.9, 78.0)],
                       [(0.0, "mm"), (0.25, "ah"), (1.2, "aw"), (1.9, "uh")], rough=0.5, breath=0.3)
        return valley(call, 0.28)
    if kind == 2:        # a rutting bull's grunting roar: croaks building into a roar
        parts = [(i * 0.3, am_croak(rng, 0.2, 80.0 + 4 * i, 70.0 + 4 * i) * (0.5 + 0.15 * i)) for i in range(3)]
        roar = am_call(rng, 1.0, [(0.0, 92.0), (0.3, 120.0), (1.0, 84.0)], [(0.0, "aw"), (1.0, "uh")], rough=0.95,
                       breath=0.4, vib=0.0)
        return valley(S.place(2.1, *parts, (0.95, roar)), 0.25)
    # a wild bugling call, high and screaming, then a grunt (an elk's bugle, on a moose)
    bugle = am_call(rng, 1.4, [(0.0, 150.0), (0.25, 420.0), (0.95, 460.0), (1.4, 220.0)],
                    [(0.0, "oo"), (0.3, "ee"), (1.0, "ih"), (1.4, "uh")], rough=0.2, breath=0.35, scale=0.85, vib_depth=0.02)
    tail = am_croak(rng, 0.25, 85.0, 70.0)
    return valley(S.place(1.9, (0.0, bugle), (1.45, tail * 0.7)), 0.28)


def am_rear(rng, kind):
    """Its call at the top of its rear (the split): a scream of a neigh, breaking."""
    if kind == 1:
        call = am_call(rng, 1.0, [(0.0, 180.0), (0.15, 330.0), (0.6, 300.0), (1.0, 140.0)],
                       [(0.0, "ee"), (0.4, "ah"), (1.0, "uh")], vib=9.0, vib_depth=0.06, rough=0.55, breath=0.4, scale=0.8)
        return valley(S.place(1.4, (0.0, big_snort(rng, 0.25, 0.6)), (0.15, call)), 0.25)
    if kind == 2:        # a deeper, rougher bray
        call = am_call(rng, 1.1, [(0.0, 140.0), (0.2, 240.0), (0.7, 215.0), (1.1, 100.0)],
                       [(0.0, "eh"), (0.4, "ah"), (1.1, "aw")], vib=8.0, vib_depth=0.07, rough=0.85, breath=0.45)
        return valley(call, 0.25)
    # three rising brays
    parts = []
    for i, (a, b) in enumerate(((170.0, 230.0), (190.0, 260.0), (210.0, 140.0))):
        d = 0.3 if i < 2 else 0.6
        parts.append((i * 0.34, am_call(rng, d, [(0.0, a), (d, b)], [(0.0, "eh"), (d, "ah")], vib=10.0, vib_depth=0.05,
                                        rough=0.6, breath=0.4, scale=0.8)))
    return valley(S.place(1.5, *parts), 0.25)


def am_gallop(rng, kind):
    """One stride of its gallop (four beats), heavy."""
    beats = ((0.0, 0.8), (0.11, 0.9), (0.3, 1.0), (0.4, 0.85)) if kind == 1 else ((0.0, 0.9), (0.15, 1.0), (0.33, 0.85), (0.45, 1.0))
    return S.place(0.75, *[(at + rng.uniform(-0.01, 0.01), big_hoof(rng, lvl)) for at, lvl in beats])


def am_step(rng, kind):
    return big_hoof(rng, 0.9 if kind == 1 else 0.75)


def am_kick(rng, kind):
    """The back kick: a snort, both hind legs lashing out (air), and the thump."""
    air = S.whoosh(rng, 0.25, 300.0, 1400.0, color="pink", width=0.5)
    thump = S.thud(rng, 95.0, 55.0, 0.3, 0.6)
    parts = [(0.0, big_snort(rng, 0.2, 0.7)), (0.2, air * 0.8), (0.42, thump * 0.5), (0.42, big_hoof(rng, 0.9))]
    if kind == 2:
        parts.append((0.42, crunch(rng, 0.2, 300.0, 0.5, 700.0, 4000.0)))
    return S.place(1.0, *parts)


def am_ram(rng, kind):
    """The ram and the antler toss: a croak and the hollow clack of its great antlers."""
    croak = am_croak(rng, 0.26, 82.0, 100.0)
    return S.place(0.9, (0.0, croak), (0.22, antler_clack(rng, 1.0, 0.8 if kind == 1 else 0.65)),
                   (0.23, S.thud(rng, 80.0, 50.0, 0.3, 0.3) * 0.4))


def am_stomp(rng, kind):
    """The stomp: a little rear, then both front hooves down hard, the ice under the snow cracking."""
    pad = S.thud(rng, 58.0, 34.0, 0.5, 0.4) * 0.45
    parts = [(0.0, big_snort(rng, 0.25, 0.6)), (0.35, pad), (0.35, big_hoof(rng, 1.0)), (0.37, big_hoof(rng, 0.9))]
    if kind == 2:
        parts.append((0.38, ice_crack(rng, 0.6, 1.3)))
    return S.place(1.1, *parts)


def am_crack_stamp(rng, kind):
    """The cracking stamp: three paws at the snow, the stamp, then cracks running away across the ice."""
    parts = []
    for i in range(3):
        parts.append((i * 0.32, crunch(rng, 0.18, 300.0, 0.6, 500.0, 4000.0)))
        parts.append((i * 0.32, big_hoof(rng, 0.5)))
    stamp = 1.1
    parts += [(stamp, S.thud(rng, 55.0, 32.0, 0.6, 0.5) * 0.45), (stamp, big_hoof(rng, 1.0))]
    cracks = 5 if kind == 1 else 8
    for i in range(cracks):
        parts.append((stamp + 0.05 + i * 0.09 + rng.uniform(0.0, 0.03), ice_crack(rng, 0.9 - 0.08 * i, 1.0 + 0.1 * (i % 3))))
    return S.place(2.4, *parts)


def am_ice_spikes(rng, kind):
    """The spikes of clear ice bursting up: small ones clattering up in a clump, then the big one with a crash."""
    parts = []
    for i in range(6 if kind == 1 else 9):
        at = rng.uniform(0.0, 0.3)
        base = rng.uniform(2200.0, 3600.0)
        parts.append((at, ring(rng, 0.45, (base, base * 1.47, base * 2.2), 0.06, rng.uniform(0.15, 0.3))))
        parts.append((at, S.crack(rng, 0.03) * 0.3))
    big = 0.35
    parts += [(big, S.thud(rng, 90.0, 50.0, 0.4, 0.8) * 0.5), (big, ice_crack(rng, 1.0, 1.8)),
              (big + 0.01, ring(rng, 0.8, (880.0, 1310.0, 1990.0, 2770.0), 0.14, 0.35)),
              (big + 0.02, frost(rng, 0.7, 0.5))]
    return S.reverb(S.place(1.9, *parts), 0.12)


def am_hurt(rng, kind):
    """A bray, deeper and rougher than a wild moose's."""
    return am_call(rng, 0.55, [(0.0, 190.0 if kind == 1 else 165.0), (0.55, 115.0)], [(0.0, "eh"), (0.55, "ah")],
                   vib=9.0, vib_depth=0.05, rough=0.7, breath=0.35, scale=0.78)


def am_death(rng, kind):
    if kind == 1:        # its last great bellow as it rears, then the crash onto its side
        call = am_call(rng, 2.0, [(0.0, 130.0), (0.4, 150.0), (2.0, 62.0)], [(0.0, "ah"), (1.0, "aw"), (2.0, "oo")],
                       vib=4.5, vib_depth=0.06, rough=0.6, breath=0.4)
        crash = S.place(0.8, (0.0, S.thud(rng, 50.0, 28.0, 0.8, 0.5)), (0.0, crunch(rng, 0.5, 260.0, 0.6, 400.0, 3500.0)))
        return valley(S.place(3.6, (0.0, call), (2.35, crash)), 0.2)
    # a long, breaking moan and a last snort
    moan = am_call(rng, 2.3, [(0.0, 160.0), (0.6, 125.0), (1.4, 105.0), (2.3, 55.0)], [(0.0, "aw"), (1.2, "uh"), (2.3, "oo")],
                   vib=6.0, vib_depth=0.08, rough=0.8, breath=0.45)
    return valley(S.place(3.4, (0.0, moan), (2.6, big_snort(rng, 0.5, 0.5))), 0.2)


# ---------------------------------------------------------------- the list


class Event:
    """
    One sound event: its id, the versions made (files), its subtitle, and where it shows in the showcase. `picks` are
    the versions the game plays (numbered from 1; it picks one of them at random each time); None means the user hasn't
    picked yet (all of them play). Every version also gets its own event, `<event>.v<n>`, for the showcase's buttons.
    """

    def __init__(self, event, folder, files, subtitle, group, label, volume=1.0, picks=None, pending=False):
        self.event, self.folder, self.files = event, folder, files
        self.subtitle, self.group, self.label, self.volume = subtitle, group, label, volume
        self.picks = tuple(picks) if picks else None
        self.pending = pending      # new versions made for the user to pick from (the old pick plays meanwhile)

    @property
    def subtitle_key(self):
        return "subtitles.dross." + self.event.split(".", 1)[1]

    @property
    def playing(self):
        """The versions the game plays (numbered from 1)."""
        return self.picks or tuple(range(1, len(self.files) + 1))


class Clip:
    """A piece of a picked version as an event of its own, so the game can play it at its own moment (the bola's throw
    and its snare happen apart: as one sound the snare would come before the bola got there)."""

    def __init__(self, event, folder, name, source, start, end, subtitle):
        self.event, self.folder, self.name, self.source = event, folder, name, source
        self.start, self.end, self.subtitle = start, end, subtitle

    @property
    def subtitle_key(self):
        return "subtitles.dross." + self.event.split(".", 1)[1]


def versions(prefix, fn, labels):
    """One file per version; labels describe each version in turn (the page and the showcase show them)."""
    return [(f"{prefix}_{i}", (lambda r, f=fn, k=i: f(r, k)), lab) for i, lab in enumerate(labels, start=1)]


HU, AM = "huntsman", "alpha_moose"
HUNTSMAN, MOOSE, GEAR = "The Huntsman: his voice", "The Alpha Moose", "The Huntsman: his weapons"
# The user's picks (October 23, 2026) are each event's `picks`. Not picked: his grunts, hurt and death (remade to sound
# human, waiting for a pick), and the sounds they skipped (footsteps, landing, the spear's hit and throw, the bow's
# draw), which play version 1 until they say otherwise. The spear's swing plays version 1 for a thrust and 2 for a
# sweep (HuntSoundCues).
EVENTS = [
    Event("entity.huntsman.cry", HU, versions("cry", hu_cry, ("a wolf's howl, long and pure",
                                                               "a wolf's howl breaking up an octave",
                                                               "his howl, the pack answering far off",
                                                               "the man's wolf howl (your pick before)")),
          "Huntsman howls", HUNTSMAN, "War cry, when he spots you", picks=(4,), pending=True),
    Event("entity.huntsman.ambient", HU, versions("ambient", hu_ambient, ("a slow frosty breath and a low hm",
                                                                           "sniffing out your trail, then a growl",
                                                                           "a low hunting hum")),
          "Huntsman breathes", HUNTSMAN, "Idle", picks=(1, 2, 3)),
    Event("entity.huntsman.effort", HU, versions("effort", hu_effort, ("hup!", "hah!", "hyah! (a battle yell)")),
          "Huntsman grunts", HUNTSMAN, "The grunt of a blow (spear thrusts, the leap)"),
    Event("entity.huntsman.hurt", HU, versions("hurt", hu_hurt, ("ugh! (a croak, cut off)",
                                                                  "agh! and a hiss through his teeth",
                                                                  "oof, winded")),
          "Huntsman hurts", HUNTSMAN, "Hurt"),
    Event("entity.huntsman.death", HU, versions("death", hu_death, ("aaaagh... falling into a croak, his last breath",
                                                                     "nngh... aaah, letting go",
                                                                     "two gasps, then a long sigh")),
          "Huntsman dies", HUNTSMAN, "Death"),
    Event("entity.huntsman.step", HU, versions("step", hu_step, ("a boot in the snow", "a softer one")),
          "Footsteps", HUNTSMAN, "Footsteps", volume=0.6, picks=(1,)),
    Event("entity.huntsman.land", HU, versions("land", hu_land, ("landing in the snow", "landing, more frost")),
          "Huntsman lands", HUNTSMAN, "Landing after a leap", picks=(1,)),
    Event("entity.huntsman.spear_swing", HU, versions("spear_swing", hu_spear_swing, ("a thrust", "a wide sweep")),
          "Spear swings", GEAR, "Spear: the swing (1 a thrust, 2 a sweep)", picks=(1, 2)),
    Event("entity.huntsman.spear_hit", HU, versions("spear_hit", hu_spear_hit, ("a deep stab", "a brighter one")),
          "Spear strikes", GEAR, "Spear: a hit, frost bursting", picks=(1,)),
    Event("entity.huntsman.spear_throw", HU, versions("spear_throw", hu_spear_throw, ("the throw",
                                                                                       "the throw, its rope paying out")),
          "Spear flies", GEAR, "Spear: the throw", picks=(1,)),
    Event("entity.huntsman.bow_draw", HU, versions("bow_draw", hu_bow_draw, ("an arrow from the quiver, a long creak",
                                                                              "a shorter draw")),
          "Bow creaks", GEAR, "Bow: the draw", picks=(1,)),
    Event("entity.huntsman.bow_shot", HU, versions("bow_shot", hu_bow_shot, ("a twang", "a higher twang",
                                                                              "a deep twang")),
          "Bow twangs", GEAR, "Bow: the shot", picks=(3,)),
    Event("entity.huntsman.arrow_rain", HU, versions("arrow_rain", hu_arrow_rain, ("arrows whistling down round you",
                                                                                    "a heavier storm")),
          "Arrows rain down", GEAR, "The arrow rain coming down", picks=(2,)),
    Event("entity.huntsman.arrow_hit", HU, versions("arrow_hit", hu_arrow_hit, ("a thwack and frost spreading",
                                                                                 "a shorter frost")),
          "Frost arrow hits", GEAR, "One of his arrows hitting you (the frost slows you)", picks=(1,)),
    Event("entity.huntsman.bola_whirl", HU, versions("bola_whirl", hu_bola_whirl, ("whirling", "whirling faster")),
          "Bola whirls", GEAR, "Bola: whirled overhead", picks=(1,)),
    Event("entity.huntsman.bola_throw", HU, versions("bola_throw", hu_bola_throw, ("thrown, wrapping, ice clacking",
                                                                                   "the same, with a thump")),
          "Bola snares", GEAR, "Bola: thrown and snaring you", picks=(2,)),
    Event("entity.alpha_moose.bellow", AM, versions("bellow", am_bellow, ("a huge, long nasal bellow",
                                                                           "a rutting bull's grunting roar",
                                                                           "a wild bugling call")),
          "Alpha Moose bellows", MOOSE, "The bellow (its roar)", picks=(3, 1)),
    Event("entity.alpha_moose.rear", AM, versions("rear", am_rear, ("a screaming neigh", "a deeper, rougher bray",
                                                                     "three rising brays")),
          "Alpha Moose rears", MOOSE, "Its call as it rears (the split)", picks=(1,)),
    Event("entity.alpha_moose.ambient", AM, versions("ambient", am_ambient, ("deep croaky grunts",
                                                                              "a big frosty snort",
                                                                              "a low wavering moan")),
          "Alpha Moose grunts", MOOSE, "Idle", picks=(1, 2)),
    Event("entity.alpha_moose.hurt", AM, versions("hurt", am_hurt, ("a rough bray", "a deeper one")),
          "Alpha Moose brays", MOOSE, "Hurt", picks=(1,)),
    Event("entity.alpha_moose.death", AM, versions("death", am_death, ("a last bellow and the crash",
                                                                        "a long breaking moan")),
          "Alpha Moose dies", MOOSE, "Death", picks=(2,)),
    Event("entity.alpha_moose.gallop", AM, versions("gallop", am_gallop, ("one stride of its gallop",
                                                                           "a slower, heavier stride")),
          "Hooves thunder", MOOSE, "Gallop", picks=(2,)),
    Event("entity.alpha_moose.step", AM, versions("step", am_step, ("a heavy hoof", "a lighter one")),
          "Hooves clop", MOOSE, "Footsteps", volume=0.6, picks=(1,)),
    Event("entity.alpha_moose.kick", AM, versions("kick", am_kick, ("the back kick", "the back kick, snow flying")),
          "Alpha Moose kicks", MOOSE, "The back kick", picks=(2,)),
    Event("entity.alpha_moose.ram", AM, versions("ram", am_ram, ("a croak and the antlers' clack",
                                                                  "a deeper clack")),
          "Alpha Moose rams", MOOSE, "The ram and the antler toss", picks=(2,)),
    Event("entity.alpha_moose.stomp", AM, versions("stomp", am_stomp, ("both front hooves down",
                                                                        "the same, the ice cracking")),
          "Alpha Moose stomps", MOOSE, "The stomp", picks=(2,)),
    Event("entity.alpha_moose.crack_stamp", AM, versions("crack_stamp", am_crack_stamp, ("three paws and the stamp, cracks running",
                                                                                         "more cracks")),
          "Ice cracks", MOOSE, "The cracking stamp", picks=(1,)),
    Event("entity.alpha_moose.ice_spikes", AM, versions("ice_spikes", am_ice_spikes, ("small spikes, then the big one",
                                                                                       "more small spikes")),
          "Ice spikes burst up", MOOSE, "The clear ice spikes bursting up", picks=(1,)),
]

# The picked bola sound (version 2) cut in two: its whoosh as he lets go, and the cords wrapping round you and their
# weights of ice clacking (from 0.33 s, just before the wrap) when it hits.
CLIPS = [
    Clip("entity.huntsman.bola_release", HU, "bola_release", "bola_throw_2", 0.0, 0.36, "Bola flies"),
    Clip("entity.huntsman.bola_snare", HU, "bola_snare", "bola_throw_2", 0.33, None, "Bola snares"),
]


# ---------------------------------------------------------------- writing it all


# A version that moved keeps the randomness it was made with (the user's old pick of a war cry, once cry_2).
SEED_AS = {"cry_4": "cry_2"}


def render(event, name, maker):
    rng = np.random.default_rng(zlib.crc32(f"{event.folder}/{SEED_AS.get(name, name)}".encode()))
    x = S.finish(maker(rng))
    write(event.folder, name, x)
    return x


def write(folder, name, x):
    path = os.path.join(SOUND_DIR, folder, name + ".ogg")
    soundfile.write(path, x.astype(np.float32), S.SR, format="OGG", subtype="VORBIS")


def cut(x, start, end):
    """A piece of a rendered sound (seconds), with short fades so it doesn't click."""
    piece = x[S.n_of(start):S.n_of(end) if end is not None else len(x)].copy()
    return S.finish(piece, fade_in=0.004, fade_out=0.03)


OURS = ("entity.huntsman.", "entity.alpha_moose.")
OUR_SUBTITLES = ("subtitles.dross.huntsman.", "subtitles.dross.alpha_moose.")


def sound_line(folder, name, volume):
    return f'      {{"name": "dross:{folder}/{name}"' + (f', "volume": {volume}' if volume != 1.0 else "") + "}"


def entry(event, subtitle_key, lines):
    return f'  "{event}": {{\n    "subtitle": "{subtitle_key}",\n    "sounds": [\n' + ",\n".join(lines) + "\n    ]\n  }"


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
    for ev in EVENTS:
        # the event the game plays: the picked versions (all of them until the user picks)
        entries.append(entry(ev.event, ev.subtitle_key,
                             [sound_line(ev.folder, ev.files[i - 1][0], ev.volume) for i in ev.playing]))
        # each version on its own too, for the showcase's buttons
        for i, (name, _m, _l) in enumerate(ev.files, start=1):
            entries.append(entry(f"{ev.event}.v{i}", ev.subtitle_key, [sound_line(ev.folder, name, ev.volume)]))
    for clip in CLIPS:
        entries.append(entry(clip.event, clip.subtitle_key, [sound_line(clip.folder, clip.name, 1.0)]))
    out = body + ",\n" + ",\n".join(entries) + "\n}\n"
    json.loads(out)
    old = {k for k in data if not k.startswith(OURS)}
    assert old <= set(json.loads(out)), "lost an entry"
    with open(SOUNDS_JSON, "w", encoding="utf-8", newline="\n") as f:
        f.write(out)


def write_lang():
    """Adds the subtitles; its own keys (subtitles.dross.huntsman.* / alpha_moose.*) are kept up to date, a stale one of
    them removed; every other key stays exactly as it was."""
    with open(LANG, encoding="utf-8") as f:
        text = f.read()
    data = json.loads(text)
    want = {}
    for ev in list(EVENTS) + list(CLIPS):
        want.setdefault(ev.subtitle_key, ev.subtitle)
    lines = text.rstrip().split("\n")
    assert lines[-1].strip() == "}"
    kept = []
    for line in lines[1:-1]:
        stripped = line.strip()
        if any(stripped.startswith('"' + o) for o in OUR_SUBTITLES):
            continue
        kept.append(line.rstrip().rstrip(","))
    kept += [f"  {json.dumps(k)}: {json.dumps(v)}" for k, v in want.items()]
    out = lines[0] + "\n" + ",\n".join(kept) + "\n}\n"
    parsed = json.loads(out)
    assert {k: v for k, v in data.items() if not k.startswith(OUR_SUBTITLES)}.items() <= parsed.items(), "lost a key"
    with open(LANG, "w", encoding="utf-8", newline="\n") as f:
        f.write(out)


def write_java():
    rows = []
    for ev in EVENTS:
        picks = ", ".join(json.dumps(f"{ev.event}.v{i}") for i in range(1, len(ev.files) + 1))
        labels = ", ".join(json.dumps(lab) for _n, _m, lab in ev.files)
        chosen = ", ".join(str(i) for i in ev.picks) if ev.picks else ""
        rows.append(f'            new Sound({json.dumps(ev.group)}, {json.dumps(ev.event)}, {json.dumps(ev.label)},\n'
                    f'                    List.of({picks}),\n'
                    f'                    List.of({labels}),\n'
                    f'                    List.of({chosen}))')
    src = f"""package com.reg21meme.dross.boss.huntsman;

import java.util.List;

/**
 * Every sound of the Huntsman and the Alpha Moose and all the versions that were made of each, written by
 * {{@code tools/huntsman/sounds.py}} (don't edit by hand: change the sounds there and run it again). All of them are
 * original, synthesized by that tool; an event plays the versions the user picked ({{@code picked}}; all of them while
 * nothing is picked). {{@link HuntSoundCues}} plays them in the fight; {{@code /dross showcase boss_sounds huntsman}} lists
 * them in chat with a button for each version.
 */
public final class HuntsmanSounds
{{
    /**
     * One sound event and its versions.
     *
     * @param group    the heading it's listed under
     * @param event    its id ({{@code dross:<event>}}; played, it picks one of the picked versions at random)
     * @param label    what it is
     * @param picks    each version on its own: its event id ({{@code <event>.v<n>}}), so a button can play just it
     * @param versions what each version is, in the same order
     * @param picked   the versions the user picked (numbered from 1), empty if they haven't yet
     */
    public record Sound(String group, String event, String label, List<String> picks, List<String> versions,
                        List<Integer> picked)
    {{
    }}

    public static final List<Sound> ALL = List.of(
{",\n".join(rows)});

    private HuntsmanSounds() {{}}
}}
"""
    with open(JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(src)


def write_page():
    os.makedirs(PREVIEWS, exist_ok=True)
    groups = {}
    for ev in EVENTS:
        groups.setdefault(ev.group, []).append(ev)
    html = ['<!doctype html><meta charset="utf-8"><title>The Huntsman\'s sounds</title>',
            "<style>body{font-family:system-ui,sans-serif;background:#12161c;color:#ecf2f8;margin:24px;max-width:1150px}"
            "h1{font-size:22px}h2{font-size:17px;margin-top:28px;color:#96d2f0}"
            ".row{margin:8px 0;padding:8px 10px;background:#1e252e;border-radius:6px}"
            ".label{font-weight:600}.id{color:#8d98a6;font-size:12px;margin-bottom:4px}"
            ".new{color:#ffd27a;font-weight:600}"
            ".v{display:inline-flex;flex-direction:column;margin:4px 14px 4px 0;padding:4px 6px;border-radius:5px;"
            "font-size:13px;color:#c9d4df}.v.picked{background:#23402e;color:#bff0c8}"
            "audio{height:32px;margin-top:2px}</style>",
            "<h1>The Huntsman's and the Alpha Moose's sounds</h1>",
            "<p>All original: made from scratch by <code>tools/huntsman/sounds.py</code>. Each player is one version "
            "(numbered as in the game). <b>Green</b> is the version you picked; a row marked <span class=\"new\">pick "
            "one</span> hasn't been picked yet (all its versions play in the game until then). His horn is the goat "
            "horn you kept. <code>/dross showcase boss_sounds huntsman</code> plays them in the game.</p>"]
    for group, evs in groups.items():
        html.append(f"<h2>{group}</h2>")
        for ev in evs:
            players = "".join(f'<span class="v{" picked" if ev.picks and i in ev.picks else ""}">{i}. {lab}'
                              f'<audio controls preload="none" '
                              f'src="../../../../src/main/resources/assets/dross/sounds/{ev.folder}/{name}.ogg"></audio></span>'
                              for i, (name, _m, lab) in enumerate(ev.files, start=1))
            note = ' <span class="new">pick one</span>' if not ev.picks or ev.pending else ""
            html.append(f'<div class="row"><div class="label">{ev.label}{note}</div><div class="id">dross:{ev.event}</div>'
                        f'{players}</div>')
    with open(os.path.join(PREVIEWS, "index.html"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(html) + "\n")


def main():
    count = 0
    rendered = {}
    for ev in EVENTS:
        os.makedirs(os.path.join(SOUND_DIR, ev.folder), exist_ok=True)
        for name, maker, _lab in ev.files:
            x = render(ev, name, maker)
            rendered[name] = x
            count += 1
            print(f"  {ev.folder}/{name}.ogg  {len(x) / S.SR:.2f} s")
    for clip in CLIPS:
        x = cut(rendered[clip.source], clip.start, clip.end)
        write(clip.folder, clip.name, x)
        print(f"  {clip.folder}/{clip.name}.ogg  {len(x) / S.SR:.2f} s (from {clip.source})")
    for folder in (HU, AM):
        names = {n for ev in EVENTS if ev.folder == folder for n, _m, _l in ev.files}
        names |= {c.name for c in CLIPS if c.folder == folder}
        for fn in os.listdir(os.path.join(SOUND_DIR, folder)):
            if fn.endswith(".ogg") and fn[:-4] not in names:
                os.remove(os.path.join(SOUND_DIR, folder, fn))
                print(f"  removed stale {folder}/{fn}")
    write_sounds_json()
    write_lang()
    write_java()
    write_page()
    print(f"{count} sounds in {len(EVENTS)} events and {len(CLIPS)} clips. Done.")


if __name__ == "__main__":
    main()
