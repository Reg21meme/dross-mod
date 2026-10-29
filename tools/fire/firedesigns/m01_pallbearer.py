"""No. M1 Pallbearer: a regular mob of the Cinder Wastes (the user's pick of the fire servants: his first look, 11A)."""
from firedesigns import f11_pallbearer as P

NUMBER = 1
ID = "mobpallbearer"
NAME = "Pallbearer"
BLURB = ("A regular mob of the Cinder Wastes (the user's pick, October 26, 2026: the fire servant candidate 11A, his "
         "first look, at his full 8.7 blocks): a gaunt giant of soot-black bone wrapped in grave-cloth, a bare skull "
         "with ember eyes under an undertaker's top hat, bent under a burning coffin chained to his back, censers "
         "smoking at its corners. He walks the ash roads of the Cinder Wastes.")
VARIANTS = (
    dict(letter="A", mood="burning coffin", look="back", note="A: his first look, as the user picked it."),
)
FREE = P.FREE


def build(s, v):
    P.build(s, v)


def materials(v):
    return P.materials(v)
