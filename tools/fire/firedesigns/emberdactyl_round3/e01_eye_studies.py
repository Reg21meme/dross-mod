"""
Archived (October 27, 2026): the Emberdactyl's ten eye studies, E1A1 to E1A10 (the user: "give me variations on his
eyes, it should be similar to Rodan"): its head and the end of its neck at the adult's size, each with its own eyes
(firekit/emberdactyl.py: EYE_STYLES, build_eye_study, both kept). The user's pick: "E1A2 is the best eyes, the
evil-looking ones" (the ember slit, now the adult's own eyes; its young got plain ones). Not built (build.py only reads
firedesigns/eNN_*.py); copy it back up a folder to show them again.
"""
from firekit import emberdactyl as E

NUMBER = 1
ID = "emberdactyl"
NAME = "Emberdactyl"
BLURB = "The Emberdactyl's eye studies (round 3): its head and neck at its real size, each with its own eyes."
VARIANTS = tuple(
    dict(letter=f"A{i}", mood=st["mood"], eye=key, study=True, density=E.STUDY_DENSITY,
         note=f"E1A{i}, eye study {i}: {st['text']} (its head and neck only, at the adult's size).")
    for i, (key, st) in enumerate(E.EYE_STYLES.items(), 1))
FREE = ()


def build(s, v):
    E.build_eye_study(s, v["eye"])


def materials(v):
    return E.materials(eye=v["eye"])
